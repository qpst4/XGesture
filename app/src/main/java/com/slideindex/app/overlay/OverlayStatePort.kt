package com.slideindex.app.overlay

import android.content.Context
import android.util.Log
import com.slideindex.app.notification.ActiveNotificationSnapshot
import com.slideindex.app.service.OverlayService
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.util.TriggerEnvironmentState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * overlay 状态与命令入口（单进程实现）。
 *
 * 历史上这里是跨进程端口：`:overlay` 广播权威状态、主进程维护镜像、主进程再通过广播下发命令。
 * 回单进程后无障碍/浮层/UI 都在同一进程，"权威值"就是本进程的静态状态，
 * 因此这里全部改成**直接读、直接调**，不再有广播、镜像、保鲜期和"服务未连接"式的静默丢弃。
 *
 * 保留同名 API，是为了让其它调用点（设置页/ViewModel/服务）一行都不用改。
 */
object OverlayStatePort {
    private const val TAG = "OverlayStatePort"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    const val COMMAND_SYNC_SCREENSHOT_MONITORING = "sync_screenshot_monitoring"
    const val COMMAND_OTP_AUTOFILL = "otp_autofill"
    const val COMMAND_SYNC_CLIPBOARD_MONITORING = "sync_clipboard_monitoring"
    const val COMMAND_PUBLISH_ACTIVE_NOTIFICATIONS = "publish_active_notifications"
    const val COMMAND_RECOVER_ACCESSIBILITY = "recover_accessibility"
    const val COMMAND_RESUME_CORNER_OVERLAY = "resume_corner_overlay"
    const val COMMAND_RESUME_RING_LAUNCHER_OVERLAY = "resume_ring_launcher_overlay"

    // ── 运行时计数（只给诊断报告用，不参与任何行为判断）─────────────────────────
    private val settingsApplyCount = java.util.concurrent.atomic.AtomicInteger(0)
    private val healTickCount = java.util.concurrent.atomic.AtomicInteger(0)
    private val healSkipCount = java.util.concurrent.atomic.AtomicInteger(0)

    @Volatile
    private var lastSettingsApplyAtMs = 0L

    fun noteSettingsApplied() {
        settingsApplyCount.incrementAndGet()
        lastSettingsApplyAtMs = android.os.SystemClock.elapsedRealtime()
    }

    /** 触钮自愈巡检计数；[skipped] = 这次因为设置/抑制原因直接跳过。 */
    fun noteHealTick(skipped: Boolean) {
        healTickCount.incrementAndGet()
        if (skipped) healSkipCount.incrementAndGet()
    }

    fun settingsApplyCount(): Int = settingsApplyCount.get()

    fun healTickCount(): Int = healTickCount.get()

    fun healSkipCount(): Int = healSkipCount.get()

    fun lastSettingsApplyAtMs(): Long = lastSettingsApplyAtMs

    private val _activeNotificationSnapshots =
        MutableStateFlow<List<ActiveNotificationSnapshot>?>(null)
    val activeNotificationSnapshots: StateFlow<List<ActiveNotificationSnapshot>?> =
        _activeNotificationSnapshots.asStateFlow()

    fun mirroredActiveNotificationSnapshots(): List<ActiveNotificationSnapshot>? =
        _activeNotificationSnapshots.value

    /** 通知栏实时快照：单进程直接落本地 flow。 */
    fun publishActiveNotifications(context: Context, snapshots: List<ActiveNotificationSnapshot>) {
        _activeNotificationSnapshots.value = snapshots
    }

    /** 刷新请求：直接让监听服务重发一次快照。 */
    fun requestActiveNotificationsPublish(context: Context) {
        runCatching {
            com.slideindex.app.service.MediaNotificationListener
                .publishActiveNotificationsSnapshot(context.applicationContext)
        }.onFailure { Log.w(TAG, "publish active notifications failed", it) }
    }

    fun isServiceConnected(): Boolean = SlideIndexAccessibilityService.isConnected()

    /** 单进程里本进程就是权威，永远可信。 */
    fun hasServiceState(): Boolean = true

    /**
     * 链路是否“新鲜可用”。
     *
     * ⚠️ 不能再恒 true：`OverlayGuard` 用 `isServiceConnected() && isServiceStateFresh()`
     * 做第一道判定，恒 true 会让守护在任何半死状态下都直接返回 Healthy，
     * 后面的“改设置强制重绑 / 发通知”永远走不到（ColorOS 掉线不自愈的直接原因）。
     */
    fun isServiceStateFresh(
        @Suppress("UNUSED_PARAMETER") nowMs: Long = 0L,
        @Suppress("UNUSED_PARAMETER") maxAgeMs: Long = 0L,
    ): Boolean = AccessibilityLink.isUsable()

    /** 链路不可用时返回 null，让等待方立刻超时而不是空等。 */
    fun millisSinceServiceState(
        @Suppress("UNUSED_PARAMETER") nowMs: Long = 0L,
    ): Long? = if (AccessibilityLink.isUsable()) 0L else null

    fun foregroundPackage(): String? =
        OverlayService.foregroundPackage ?: SlideIndexAccessibilityService.currentForegroundPackage()

    fun isLockScreenActive(): Boolean = TriggerEnvironmentState.lockScreenActive

    /** 兼容旧调用点：单进程无需广播。 */
    fun publish(@Suppress("UNUSED_PARAMETER") context: Context, @Suppress("UNUSED_PARAMETER") reason: String) = Unit

    /** 命令入口：直接执行，失败只记日志，不再有"指令被丢弃"。 */
    fun sendCommand(context: Context, command: String) {
        val appContext = context.applicationContext
        when (command) {
            COMMAND_SYNC_SCREENSHOT_MONITORING ->
                runCatching { SlideIndexAccessibilityService.accessibilityInstance()?.syncScreenshotMonitoring() }
                    .onFailure { Log.w(TAG, "sync screenshot monitoring failed", it) }

            COMMAND_SYNC_CLIPBOARD_MONITORING ->
                runCatching {
                    com.slideindex.app.clipboard.ClipboardAccess.repository
                        ?.syncClipboardMonitoringFromSettings()
                }.onFailure { Log.w(TAG, "sync clipboard monitoring failed", it) }

            COMMAND_PUBLISH_ACTIVE_NOTIFICATIONS -> requestActiveNotificationsPublish(appContext)

            COMMAND_RESUME_CORNER_OVERLAY ->
                runCatching { com.slideindex.app.overlay.corner.CornerGestureHost.resumeAfterSlotPicker() }
                    .onFailure { Log.w(TAG, "resume corner overlay failed", it) }

            COMMAND_RESUME_RING_LAUNCHER_OVERLAY ->
                runCatching {
                    com.slideindex.app.overlay.ringlauncher.RingLauncherOverlayWindow
                        .resumeAfterSlotIconEditor()
                }.onFailure { Log.w(TAG, "resume ring launcher overlay failed", it) }

            COMMAND_RECOVER_ACCESSIBILITY -> scope.launch {
                val deps = runCatching {
                    dagger.hilt.android.EntryPointAccessors.fromApplication(
                        appContext,
                        com.slideindex.app.di.AppGraphEntryPoint::class.java,
                    ).dependencies()
                }.getOrNull() ?: return@launch
                runCatching {
                    com.slideindex.app.service.OverlayServiceLifecycle
                        .recoverAccessibilityBinding(appContext, deps.settingsRepository.readSnapshot())
                }.onFailure { Log.w(TAG, "recover accessibility failed", it) }
            }
        }
    }

    /** 验证码注入：单进程直接调编排（原先要跨进程转给 :overlay）。 */
    fun sendOtpAutoFill(context: Context, code: String, recordId: String?) {
        val appContext = context.applicationContext
        scope.launch {
            val deps = runCatching {
                dagger.hilt.android.EntryPointAccessors.fromApplication(
                    appContext,
                    com.slideindex.app.di.AppGraphEntryPoint::class.java,
                ).dependencies()
            }.getOrNull() ?: return@launch
            runCatching {
                com.slideindex.app.otp.OtpAutoInputOrchestrator.requestAutoFill(
                    context = appContext,
                    code = code,
                    settings = deps.settingsRepository.readSnapshot(),
                    recordId = recordId,
                )
            }.onFailure { Log.w(TAG, "otp autofill failed", it) }
        }
    }
}
