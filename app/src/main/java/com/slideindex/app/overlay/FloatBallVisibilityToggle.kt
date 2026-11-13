package com.slideindex.app.overlay

import android.content.Context
import android.widget.Toast
import com.slideindex.app.R
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.service.OverlayService
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.util.OverlaySnoozeController
import com.slideindex.app.util.OverlaySuppression
import com.slideindex.app.util.OverlaySuppressionScope
import com.slideindex.app.util.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 「切换悬浮球」动作的执行体：把 `FLOAT_BALL_ENABLED` 取反。
 *
 * 只管写设置，不碰窗口：显隐和设置页那个开关走的是**同一条**链路
 * （`EdgeOverlayHost` 的设置流 → `FloatBallController.apply`）。
 * 这样就不必在这里再判断"按应用禁用 / 锁屏 / 暂停悬浮层"，
 * 也不会和设置流抢着挂/摘窗口。
 *
 * 为什么不用 `readSnapshot()`：它读的是异步维护的缓存，写完立刻读会拿到旧值，
 * 连点两次就可能"翻了又翻回去"。这里与「切换晃动手势」一致走 `settings.first()`，
 * 再套一层 [Mutex] 防止两次快速触发读到同一个旧值。
 */
object FloatBallVisibilityToggle {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    /** 浮层进程内（手势 / 槽位 / 桌面长按菜单）用这个：自己取 [SettingsRepository]。 */
    fun toggle(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            val repository = OverlayDependencyAccess.overlayDependencies(appContext)?.settingsRepository
                ?: return@launch
            toggle(appContext, repository)
        }
    }

    /** 显式显示 / 隐藏（幂等目标态）。 */
    fun set(context: Context, visible: Boolean) {
        val appContext = context.applicationContext
        scope.launch {
            val repository = OverlayDependencyAccess.overlayDependencies(appContext)?.settingsRepository
                ?: return@launch
            apply(appContext, repository, visible)
        }
    }

    /** 已经拿到 [SettingsRepository] 的调用方（外部调用的 trampoline）用这个。 */
    suspend fun toggle(context: Context, repository: SettingsRepository) {
        val appContext = context.applicationContext
        val messageRes = mutex.withLock {
            val current = repository.settings.first()
            val enabled = !current.floatBallEnabled
            repository.setFloatBallEnabled(enabled)
            resolveToast(appContext, current, enabled)
        }
        withContext(Dispatchers.Main) {
            Toast.makeText(appContext, messageRes, Toast.LENGTH_SHORT).show()
        }
    }

    /** 写入目标态并给出「开/关之后看不看得见」的解释。 */
    private suspend fun apply(context: Context, repository: SettingsRepository, visible: Boolean) {
        val appContext = context.applicationContext
        val messageRes = mutex.withLock {
            val current = repository.settings.first()
            if (current.floatBallEnabled != visible) {
                repository.setFloatBallEnabled(visible)
            }
            resolveToast(appContext, current, visible)
        }
        withContext(Dispatchers.Main) {
            Toast.makeText(appContext, messageRes, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 开关本身永远成功（只是写设置），但「开了却看不到」有好几种原因，
     * 必须当场说出来，否则用户会当成动作没生效。
     */
    private fun resolveToast(context: Context, current: AppSettings, enabled: Boolean): Int = when {
        !enabled -> R.string.gesture_action_toggle_float_ball_hidden
        !current.serviceEnabled -> R.string.gesture_action_toggle_float_ball_service_off
        !PermissionHelper.isAccessibilityServiceEnabled(context) -> R.string.float_ball_permission_required
        OverlaySnoozeController.isActive() -> R.string.gesture_action_toggle_float_ball_snoozed
        OverlaySuppression.shouldSuppress(
            settings = current,
            context = context,
            foregroundPackage = OverlayService.foregroundPackage,
            scope = OverlaySuppressionScope.FLOAT_BALL,
        ) -> R.string.gesture_action_toggle_float_ball_suppressed
        else -> R.string.gesture_action_toggle_float_ball_shown
    }
}
