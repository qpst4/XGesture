package com.slideindex.app.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.view.View
import com.slideindex.app.data.AppInfo
import com.slideindex.app.data.AppRepository
import com.slideindex.app.gesture.ActionExecutor
import com.slideindex.app.gesture.GestureSession
import com.slideindex.app.gesture.GestureZoneLayout
import com.slideindex.app.gesture.PanelGridSession
import com.slideindex.app.gesture.SlideAlongRailSession
import com.slideindex.app.gesture.SwipePathRecognizer
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.util.HapticHelper

internal class EdgeGestureOverlayHosts(
    private val view: EdgeGestureOverlayView,
    private val side: PanelSide,
    private val appRepository: AppRepository,
    private val zoneLayout: GestureZoneLayout,
    private val indexSession: SlideAlongRailSession,
    private val panelGridSession: PanelGridSession,
    private val actionExecutor: ActionExecutor,
    private val pathRecognizer: SwipePathRecognizer,
    private val gestureSession: GestureSession,
    private val panelEnterAnimator: OverlayPanelEnterAnimator,
    private val panelContentRect: RectF,
    private val settingsProvider: () -> AppSettings,
    private val appsProvider: () -> List<AppInfo>,
    private val dpFn: (Float) -> Float,
    private val spFn: (Float) -> Float,
    private val runAfterLayoutFn: (() -> Unit) -> Unit,
    private val activeTriggerZoneRectFn: () -> RectF,
    private val clearEdgeCaptureTouchActiveFn: () -> Unit,
    private val onInitiatingEdgeGestureReleasedFn: () -> Unit,
    private val notifyPresentationTouchRequirementChangedFn: () -> Unit,
    private val notifyOverlayLayoutIfNeededFn: () -> Unit,
    private val onAdjustPanelDismissFn: () -> Unit,
    private val onSessionStartFn: () -> Unit,
    private val onShellCommandsPersistFn: (List<ShellCommand>) -> Unit,
    private val onQuickLauncherPanelItemsPersistFn: (String, List<QuickLauncherItem>) -> Unit,
    private val onShellPanelFocusChangeFn: (Boolean) -> Unit,
    private val onOverlayWindowSuspendFn: () -> Unit,
    private val onOverlayWindowResumeFn: () -> Unit,
    private val onShellPanelAuxiliaryPrepareFn: () -> Unit,
    private val onShellPanelAuxiliaryDismissFn: () -> Unit,
    private val iconForFn: (AppInfo) -> Bitmap
) : ShellPanelOverlayController.Host,
    QuickLauncherOverlayController.Host,
    IndexPanelRenderer.Host,
    AdjustPanelOverlayController.Host,
    TaskSwitcherOverlayController.Host {

    var indexPanelContentRectProvider: (() -> RectF)? = null

    override val context: Context get() = view.context

    override fun settings(): AppSettings = settingsProvider()

    override fun side(): PanelSide = side

    override fun apps(): List<AppInfo> = appsProvider()

    override fun gestureSession(): GestureSession = gestureSession

    override fun zoneLayout(): GestureZoneLayout = zoneLayout

    override fun pathRecognizer(): SwipePathRecognizer = pathRecognizer

    override fun actionExecutor(): ActionExecutor = actionExecutor
    override fun actionHost(): com.slideindex.app.overlay.panels.PanelActionHost =
        com.slideindex.app.overlay.AdjustActionHostAdapter(actionExecutor)
    override fun isAdjustMode(): Boolean = gestureSession.isAdjustMode()
    override fun currentAdjustMode(): com.slideindex.app.overlay.panels.PanelAdjustMode? =
        gestureSession.adjustModeOrNull()?.toPanelMode()
    override fun adjustAnchorRawY(): Float? = gestureSession.adjustAnchorRawY()
    override fun panelMode(): com.slideindex.app.overlay.OverlayPanelMode = gestureSession.panelMode()
    override fun endGestureSession() = gestureSession.endSession()

    override fun panelGridSession(): PanelGridSession = panelGridSession

    override fun panelEnterProgress(): Float = panelEnterAnimator.progress

    override fun panelEnterAdjustedX(localX: Float, panel: RectF): Float =
        panelEnterAnimator.adjustedX(localX, panel)

    override fun panelEnterOffsetX(panel: RectF): Float = panelEnterAnimator.enterOffsetX(panel)

    override fun panelContentRect(): RectF = panelContentRect

    override fun drawWithPanelEnterAnimation(
        canvas: Canvas,
        contentRect: RectF,
        drawContent: () -> Unit
    ) = panelEnterAnimator.drawWithAnimation(canvas, contentRect, drawContent)

    override fun activeTriggerZoneRect(): RectF = activeTriggerZoneRectFn()

    // 缩窗（钉住面板）期间窗口尺寸 != 整屏，面板几何必须继续按整屏坐标算，否则会越算越小。
    override fun viewWidth(): Int = view.logicalWidthPx()

    override fun viewHeight(): Int = view.logicalHeightPx()

    override fun dp(value: Float): Float = dpFn(value)

    override fun sp(value: Float): Float = spFn(value)

    override fun density(): Float = view.resources.displayMetrics.density

    override fun screenWidthPx(): Int = OverlayScreenMetrics.sizePx(view.context).first

    override fun screenHeightPx(): Int = OverlayScreenMetrics.sizePx(view.context).second

    // 缩窗（钉住面板）期间窗口原点不是 (0,0)，而面板 / 触钮都按整屏坐标算 → 统一按逻辑原点换算。
    override fun viewLocationOnScreen(): IntArray = IntArray(2).also { view.locationOnScreenLogical(it) }

    override fun invalidate() = view.invalidate()

    @Suppress("DEPRECATION")
    override fun invalidatePanel(rect: RectF) {
        // 缩窗（钉住面板）时脏矩形还是整屏坐标，和窗口本地坐标对不上 → 整窗重画即可（窗口本身很小）。
        if (view.isPresentationWindowShrunk()) {
            view.invalidate()
            return
        }
        val target = if (rect.isEmpty) indexPanelContentRectProvider?.invoke() ?: rect else rect
        if (target.isEmpty) {
            view.invalidate()
            return
        }
        val pad = dp(4f).toInt()
        view.invalidate(
            (target.left - pad).toInt().coerceAtLeast(0),
            (target.top - pad).toInt().coerceAtLeast(0),
            (target.right + pad).toInt().coerceAtMost(view.width.coerceAtLeast(1)),
            (target.bottom + pad).toInt().coerceAtMost(view.height.coerceAtLeast(1))
        )
    }

    @Suppress("DEPRECATION")
    override fun invalidatePartial(left: Int, top: Int, right: Int, bottom: Int) {
        // ⚠️ 缩窗（钉住面板）时这些脏矩形还是**整屏坐标**，而 view 的局部坐标原点已经变成窗口原点。
        // 直接传进去会与 view 边界求交 → 要么只重绘窗口右下角一小块、要么交集为空**什么都不重绘**，
        // 于是面板内容停在旧帧上（真机表现：点一下面板外，面板像跳到左上角，随后某次整窗重绘才回位）。
        // 窗口本身很小，缩窗期间一律整窗重绘。
        if (view.isPresentationWindowShrunk()) {
            view.invalidate()
            return
        }
        view.invalidate(left, top, right, bottom)
    }

    override fun post(action: () -> Unit) {
        view.post(action)
    }

    override fun postDelayed(runnable: Runnable, delayMs: Long) {
        view.postDelayed(runnable, delayMs)
    }

    override fun removeCallbacks(runnable: Runnable) {
        view.removeCallbacks(runnable)
    }

    override fun overlayView(): android.view.View? = view

    override fun requestFocus() {
        view.requestFocus()
    }

    override fun runAfterLayout(block: () -> Unit) = runAfterLayoutFn(block)

    override fun anchorLocalY(rawY: Float): Float {
        val loc = IntArray(2)
        view.getLocationOnScreen(loc)
        return rawY - loc[1]
    }

    override fun indexSession(): SlideAlongRailSession = indexSession

    override fun iconFor(app: AppInfo): Bitmap = iconForFn(app)

    override fun appRepository(): AppRepository = appRepository

    override fun hapticTick() = HapticHelper.appTick(view, settings())

    override fun hapticLongThreshold() = HapticHelper.longThreshold(view, settings())

    override fun hapticConfirm() = HapticHelper.confirmLaunch(view, settings())

    override fun hapticConfirmLaunch() = HapticHelper.confirmLaunch(view, settings())

    override fun startPanelExitAnimation(onEnd: () -> Unit) =
        panelEnterAnimator.startExit(gestureSession.panelMode(), onEnd)

    override fun notifyPresentationTouchRequirementChanged() =
        notifyPresentationTouchRequirementChangedFn()

    override fun onQuickLauncherPanelItemsPersist(panelId: String, items: List<QuickLauncherItem>) =
        onQuickLauncherPanelItemsPersistFn(panelId, items)

    override fun onOverlayWindowSuspend() = onOverlayWindowSuspendFn()

    override fun onOverlayWindowResume() = onOverlayWindowResumeFn()

    override fun onAdjustPanelDismiss() = onAdjustPanelDismissFn()

    override fun onSessionStart() = onSessionStartFn()

    override fun notifyOverlayLayoutIfNeeded() = notifyOverlayLayoutIfNeededFn()

    override fun onShellCommandsPersist(commands: List<ShellCommand>) =
        onShellCommandsPersistFn(commands)

    override fun onShellPanelFocusChange(needsFocus: Boolean) =
        onShellPanelFocusChangeFn(needsFocus)

    override fun onShellPanelAuxiliaryPrepare() = onShellPanelAuxiliaryPrepareFn()

    override fun onShellPanelAuxiliaryDismiss() = onShellPanelAuxiliaryDismissFn()

    override fun clearEdgeCaptureTouchActive() = clearEdgeCaptureTouchActiveFn()

    override fun onInitiatingEdgeGestureReleased() = onInitiatingEdgeGestureReleasedFn()

    override fun setPanelPresentationFocus(needsFocus: Boolean) =
        onShellPanelFocusChangeFn(needsFocus)
}
