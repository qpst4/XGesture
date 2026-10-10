package com.slideindex.app.clipboardfloat

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.slideindex.app.overlay.ImeBoundsDetector
import com.slideindex.app.overlay.ImeDiagnostics
import com.slideindex.app.overlay.ImeSignalStabilizer
import com.slideindex.app.service.ClipboardFloatLifecycle
import com.slideindex.app.service.ClipboardFloatService
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.SettingsRepository
import kotlin.math.abs
import kotlinx.coroutines.flow.first

/**
 * 剪贴板浮窗的"跟随键盘"协调器。
 *
 * ⚠️ 两条硬约束（都是真机踩出来的）：
 *
 * 1. **绝不能对单次采样直接做显示/隐藏**：`ClipboardFloatService` 的显示/隐藏不是免费的
 *    （`startService` → 新建 Compose 窗口 + 新 Surface；隐藏 → `removeView` + `stopSelf()`），
 *    而它自己增删窗口又会引来新的窗口事件 —— 单次采样直接动作会形成自激振荡（真机实测：6 秒
 *    18 次 SHOW/HIDE，顺带 13 次服务销毁重建 + 一次 94MB GC）。见 [ImeSignalStabilizer]。
 * 2. **提交必须能"到点自己发生"**：采样只在窗口事件里发生，而键盘收起后系统不再产生任何窗口事件。
 *    只靠"下一次采样"确认候选会让最后一跳永久卡住（真机实测：按返回收起键盘后我们一条 HIDE
 *    都没发，胶囊一直挂着；点它一下才立刻消失）。所以这里按 [ImeSignalStabilizer.nextWakeAtMs]
 *    定时回来自查。
 */
object ClipboardFloatImeCoordinator {
    /** 顶缘抖动容差，与 `KeyboardTriggerBoundsAdjuster` 同一口径，避免键盘动画期间刷 intent。 */
    private const val IME_TOP_DEBOUNCE_PX = 8

    @Volatile
    private var enabled: Boolean = false

    @Volatile
    private var showChip: Boolean = true

    @Volatile
    private var blockedPackages: Set<String> = emptySet()

    /** IME 可见性状态机；只有它提交的翻转才会真正动浮窗。 */
    private val stabilizer = ImeSignalStabilizer()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val tickRunnable = Runnable { onTick() }

    /** 上次下发给浮窗的顶缘，用于 8px 去抖。 */
    @Volatile
    private var lastDispatchedImeTop: Int? = null

    /** 最近一次采样到的顶缘；[onTick] 提交"可见"时用它（采样与提交之间最多差 confirmMs）。 */
    @Volatile
    private var lastSampledImeTop: Int? = null

    @Volatile
    private var tickContext: Context? = null

    fun applySettings(settings: AppSettings) {
        enabled = settings.clipboardFloatEnabled
        showChip = settings.clipboardFloatShowChip
        blockedPackages = settings.clipboardFloatBlockedPackages
    }

    fun onWindowsChanged(serviceContext: Context) {
        if (!enabled) {
            cancelTick()
            hideIfShown(serviceContext)
            return
        }
        val service = SlideIndexAccessibilityService.accessibilityInstance()
            ?: return
        if (isForegroundBlocked(service)) {
            cancelTick()
            hideIfShown(serviceContext)
            return
        }
        val probe = ClipboardFloatImeDetector.detectImeProbe(service)
        lastSampledImeTop = probe.bounds?.top
        val now = SystemClock.uptimeMillis()
        val committed = stabilizer.onSample(sampleVisible = probe.bounds != null, nowMs = now)
        if (ImeDiagnostics.enabled) {
            ImeDiagnostics.log("chip", diagLine(service, probe, committed, now), now)
        }
        if (committed) {
            applyCommitted(serviceContext, visible = probe.bounds != null)
        } else if (probe.bounds != null && stabilizer.committedVisible) {
            // 已确认可见：只把新的顶缘转发出去（键盘变高 / 表情面板）。
            dispatchImeTop(serviceContext, probe.bounds.top)
        }
        scheduleTick(serviceContext)
    }

    /**
     * 到点自查（见类注释第 2 条）。
     *
     * 若 [ImeSignalStabilizer.tick] 没提交，只可能是"还没到点"或"还在静默期"，
     * 此时 [ImeSignalStabilizer.nextWakeAtMs] 一定给出更晚的时刻，所以重新排一次不会变成忙循环。
     */
    private fun onTick() {
        val ctx = tickContext ?: return
        val now = SystemClock.uptimeMillis()
        val committed = stabilizer.tick(now)
        if (ImeDiagnostics.enabled) {
            ImeDiagnostics.log(
                "chip-tick",
                "committed=$committed committedVisible=${stabilizer.committedVisible}",
                now,
            )
        }
        if (committed) applyCommitted(ctx, visible = stabilizer.committedVisible)
        scheduleTick(ctx)
    }

    private fun scheduleTick(serviceContext: Context) {
        tickContext = serviceContext.applicationContext
        mainHandler.removeCallbacks(tickRunnable)
        val wakeAt = stabilizer.nextWakeAtMs() ?: return
        val delay = (wakeAt - SystemClock.uptimeMillis()).coerceAtLeast(0L)
        mainHandler.postDelayed(tickRunnable, delay)
    }

    private fun cancelTick() {
        mainHandler.removeCallbacks(tickRunnable)
        tickContext = null
    }

    private fun applyCommitted(serviceContext: Context, visible: Boolean) {
        if (visible) {
            val top = lastSampledImeTop ?: return
            lastDispatchedImeTop = top
            ClipboardFloatLifecycle.showForIme(
                context = serviceContext,
                imeTop = top,
                showChip = showChip,
            )
        } else {
            lastDispatchedImeTop = null
            ClipboardFloatLifecycle.hide(serviceContext)
        }
    }

    private fun dispatchImeTop(serviceContext: Context, top: Int) {
        val previous = lastDispatchedImeTop
        if (previous != null && abs(top - previous) <= IME_TOP_DEBOUNCE_PX) return
        lastDispatchedImeTop = top
        ClipboardFloatService.updateImeTop(serviceContext, top)
    }

    private fun hideIfShown(serviceContext: Context) {
        if (!stabilizer.committedVisible) return
        stabilizer.reset(visible = false, nowMs = SystemClock.uptimeMillis())
        lastDispatchedImeTop = null
        ClipboardFloatLifecycle.hide(serviceContext)
    }

    suspend fun syncFromSettings(context: Context, settingsRepository: SettingsRepository) {
        applySettings(settingsRepository.settings.first())
        val service = SlideIndexAccessibilityService.accessibilityInstance()
        if (!enabled || (service != null && isForegroundBlocked(service))) {
            cancelTick()
            stabilizer.reset(visible = false, nowMs = SystemClock.uptimeMillis())
            lastDispatchedImeTop = null
            ClipboardFloatLifecycle.hide(context)
        }
    }

    private fun isForegroundBlocked(service: SlideIndexAccessibilityService): Boolean {
        val foregroundPackage = ClipboardFloatForegroundResolver.resolveHostPackage(service)
            ?: return false
        return foregroundPackage in blockedPackages
    }

    /** 诊断行：把"我们的两个读数 + 决定 + 焦点节点"放进同一行，便于和 dumpsys 对齐。 */
    private fun diagLine(
        service: SlideIndexAccessibilityService,
        probe: ImeBoundsDetector.ImeProbe,
        committed: Boolean,
        nowMs: Long,
    ): String = buildString {
        append("src=").append(probe.source)
        append(" bounds=").append(probe.bounds?.let { "top=${it.top} h=${it.height()}" } ?: "null")
        append(" rawInset=").append(probe.rawImeInsetBottom)
        append(" appWin=").append(probe.applicationWindowPresent)
        append(" sample=").append(probe.bounds != null)
        append(" committedVisible=").append(stabilizer.committedVisible)
        append(" flipped=").append(committed)
        append(" nextWake=").append(stabilizer.nextWakeAtMs()?.minus(nowMs) ?: "-")
        append(' ').append(ImeDiagnostics.focusedNodeDescription(service))
        append(' ').append(ImeDiagnostics.acceptingTextProbe(service))
    }
}
