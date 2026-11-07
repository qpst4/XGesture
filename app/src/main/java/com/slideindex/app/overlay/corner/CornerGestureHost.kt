package com.slideindex.app.overlay.corner

import android.content.Context
import com.slideindex.app.data.AppRepository
import com.slideindex.app.overlay.panels.PanelRuntimeHooks
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.SettingsFlowHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CornerGestureHost(
    private val context: Context,
    private val scope: CoroutineScope,
    private val appRepository: AppRepository,
    private val gestureRunner: com.slideindex.app.overlay.panels.GestureActionRunner,
) {
    private var controller: CornerGestureController? = null

    companion object {
        @Volatile
        private var active: CornerGestureHost? = null

        /** 供输入层接管（system_server 模块）拿到当前宿主；未启动时为 null。 */
        fun instanceOrNull(): CornerGestureHost? = active

        /**
         * 该角落此刻是否能处理输入层转发来的触摸。
         *
         * 由 binder 线程调用，只读控制器维护的 volatile 命中快照。
         */
        fun canAcceptForwardedTouchAt(anchor: CornerAnchor, x: Float, y: Float): Boolean =
            active?.controller?.canAcceptForwardedTouchAt(anchor, x, y) == true

        fun resumeAfterSlotPicker() {
            val host = active ?: return
            host.controller?.resumeAfterSlotPicker()
            PanelRuntimeHooks.settingsSnapshot?.invoke(host.context)?.let { settings ->
                host.controller?.applySettings(settings)
            }
        }

        /** 外部编辑页已离开前台但轮盘仍卡在挂起态时自愈（由 `:overlay` 侧前台变化时调用）。 */
        fun selfHealAfterExternalActivity() {
            val host = active ?: return
            val controller = host.controller ?: return
            if (!controller.isSuspendedForExternalActivity()) return
            host.controller?.resumeAfterSlotPicker()
            PanelRuntimeHooks.settingsSnapshot?.invoke(host.context)?.let { settings ->
                host.controller?.applySettings(settings)
            }
        }
    }

    /** 输入层转发来的触摸：直接喂给既有会话流程（与窗口触摸同一条路径）。 */
    fun handleForwardedTouch(anchor: CornerAnchor, event: android.view.MotionEvent) {
        controller?.handleForwardedTouch(anchor, event)
    }

    /** 输入层会话结束：取消该角落进行中的轮盘会话。 */
    fun cancelForwardedTouch(anchor: CornerAnchor) {
        controller?.cancelForwardedTouch(anchor)
    }

    fun start() {
        active = this
        if (controller != null) return
        controller = CornerGestureController(
            context = context,
            appRepository = appRepository,
            scope = scope,
            onShellCommandsPersist = { commands ->
                PanelRuntimeHooks.persistShellCommands?.invoke(commands)
            },
            gestureRunner = gestureRunner,
        )
        val settingsFlow = SettingsFlowHolder.settingsFlow(context) ?: return
        scope.launch {
            settingsFlow.collectLatest { settings ->
                controller?.applySettings(settings)
            }
        }
    }

    fun refreshSuppression() {
        controller?.refreshSuppression()
    }

    fun stop() {
        if (active === this) {
            active = null
        }
        controller?.destroy()
        controller = null
    }

    fun onConfigurationChanged() {
        controller?.onConfigurationChanged()
    }

    fun setZonePreviewActive(active: Boolean) {
        controller?.setZonePreviewActive(active)
    }

    fun applyZonePreviewDimensions(
        verticalEdgeWidthDp: Float,
        verticalEdgeHeightDp: Float,
        horizontalEdgeWidthDp: Float,
        horizontalEdgeHeightDp: Float,
    ) {
        controller?.applyZonePreviewDimensions(
            verticalEdgeWidthDp,
            verticalEdgeHeightDp,
            horizontalEdgeWidthDp,
            horizontalEdgeHeightDp,
        )
    }

    fun applySettings(settings: AppSettings) {
        controller?.applySettings(settings)
    }
}
