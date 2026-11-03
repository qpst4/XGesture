package com.slideindex.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.util.TypedValue
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import com.slideindex.app.data.AppInfo
import com.slideindex.app.data.AppRepository
import com.slideindex.app.diagnostic.EdgeDiag
import com.slideindex.app.gesture.ActionExecutor
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.CollapsedWindowBounds
import com.slideindex.app.gesture.GestureSession
import com.slideindex.app.gesture.GestureZoneLayout
import com.slideindex.app.gesture.IndexSessionHost
import com.slideindex.app.gesture.PanelGridSession
import com.slideindex.app.gesture.SlideAlongRailSession
import com.slideindex.app.gesture.SwipePathRecognizer
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.triggerHandles
import com.slideindex.app.util.ContinuousAdjustController
import com.slideindex.app.util.GestureActionIconBitmap
import com.slideindex.app.util.HapticHelper
import com.slideindex.app.util.OverlayBrightnessControl

/** 会话/触钮取词"按住才成立"状态最长允许无任何输入事件的时间，超过即判定 UP/CANCEL 丢失。 */
private const val INTERACTION_IDLE_TIMEOUT_MS = 10_000L

/**
 * 外部触发面板时，合成触发带的横向厚度（dp）。
 *
 * 只决定"面板离屏幕边多远"（与触钮条的边宽同量级），不参与别的判定；
 * 纵向高度恒为 0 = 锚点锁定在触发点那一行。
 */
private const val PANEL_TRIGGER_BAND_DP = 24f

/**
 * 边缘手势 Overlay 编排层：触摸分发、会话生命周期与各面板 Controller 协调。
 */
@SuppressLint("ViewConstructor") // Programmatically created overlay; not inflated from XML
class EdgeGestureOverlayView(
    context: Context,
    private val side: PanelSide,
    private val appRepository: AppRepository,
    /** 权威屏幕尺寸（与触摸捕获窗同一来源）；见 [EdgeGestureLayoutCoordinator.screenSizeProvider]。 */
    private val screenSizeProvider: () -> Pair<Int, Int>? = { null },
    private val onSessionStartCallback: () -> Unit,
    private val onSessionEndCallback: () -> Unit,
    private val onGestureTrackingStartCallback: () -> Unit = {},
    private val onAdjustPanelLayoutCallback: (Float) -> Unit = {},
    private val onAdjustPanelDismissCallback: () -> Unit = {},
    private val onClickPassthroughCallback: (Float, Float, () -> Unit) -> Unit = { _, _, onComplete -> onComplete() },
    private val onShellCommandsPersist: (List<ShellCommand>) -> Unit = {},
    private val onQuickLauncherPanelItemsPersist: (String, List<QuickLauncherItem>) -> Unit = { _, _ -> },
    private val onShellPanelFocusChange: (Boolean) -> Unit = {},
    private val onOverlayWindowSuspend: () -> Unit = {},
    private val onOverlayWindowResume: () -> Unit = {},
    private val onOverlayPresentationSuspend: () -> Unit = {},
    private val onOverlayPresentationResume: () -> Unit = {},
    private val onShellPanelAuxiliaryPrepare: () -> Unit = {},
    private val onShellPanelAuxiliaryDismiss: () -> Unit = {},
    overlayBrightness: OverlayBrightnessControl? = null
) : View(context), IndexSessionHost {

    private val gestureCallbacks = GestureSessionCallbackBridge()

    private var settings = AppSettings()
    private var apps: List<AppInfo> = emptyList()
    private var previewMode = false
    private var previewContent: LayoutPreviewContent = LayoutPreviewContent.TRIGGER_ONLY
    private var previewFocus: LayoutPreviewFocus? = null

    private val zoneLayout = GestureZoneLayout(side)
    private val indexSession = SlideAlongRailSession(side, zoneLayout, this)
    private val panelGridSession = PanelGridSession()
    private val actionExecutor = ActionExecutor(
        context = context,
        appRepository = appRepository,
        clickPassthroughHandler = onClickPassthroughCallback,
        overlayBrightness = overlayBrightness,
        side = side,
        onShellCommandsPersist = onShellCommandsPersist
    )
    private val pathRecognizer = SwipePathRecognizer(side, resources.displayMetrics.density)
    private val panelContentRect = RectF()
    private val panelEnterAnimator = OverlayPanelEnterAnimator(side, ::dp) { invalidate() }
    private var edgeCaptureTouchActive = false

    /**
     * 本次外部触发（角轮盘 / 悬浮球 / 双贴边线条）的本地锚点高度。
     *
     * 该侧**没有任何触钮**时没有真实触发带（空心矩形），面板布局引擎会把锚点夹成 0，
     * 面板就贴到屏幕最上沿了；有了它才能合成一条"贴本侧、纵向=触发点"的触发带。
     */
    private var externalPanelAnchorLocalY: Float? = null

    private val iconSizePx: Float get() = dp(44f)

    private val gestureSession = GestureSession(
        side = side,
        zoneLayout = zoneLayout,
        indexSession = indexSession,
        pathRecognizer = pathRecognizer,
        actionExecutor = actionExecutor,
        callbacks = gestureCallbacks
    )

    private val gestureAnimationCoordinator = GestureAnimationCoordinator(
        side = side,
        gestureSessionProvider = { gestureSession },
        pathRecognizerProvider = { pathRecognizer },
        settingsProvider = { settings },
        post = { action -> post(action) }
    )

    private val overlayHosts = EdgeGestureOverlayHosts(
        view = this,
        side = side,
        appRepository = appRepository,
        zoneLayout = zoneLayout,
        indexSession = indexSession,
        panelGridSession = panelGridSession,
        actionExecutor = actionExecutor,
        pathRecognizer = pathRecognizer,
        gestureSession = gestureSession,
        panelEnterAnimator = panelEnterAnimator,
        panelContentRect = panelContentRect,
        settingsProvider = { settings },
        appsProvider = { apps },
        iconForFn = { app -> iconFor(app) },
        dpFn = ::dp,
        spFn = ::sp,
        runAfterLayoutFn = ::runAfterLayout,
        activeTriggerZoneRectFn = ::activeTriggerZoneRect,
        clearEdgeCaptureTouchActiveFn = { edgeCaptureTouchActive = false },
        onInitiatingEdgeGestureReleasedFn = {
            edgeCaptureTouchActive = false
            gestureAnimationCoordinator.onTouchCanceled()
            notifyPresentationTouchRequirementChanged()
        },
        notifyPresentationTouchRequirementChangedFn = ::notifyPresentationTouchRequirementChanged,
        notifyOverlayLayoutIfNeededFn = ::notifyOverlayLayoutIfNeeded,
        onAdjustPanelDismissFn = onAdjustPanelDismissCallback,
        onSessionStartFn = onSessionStartCallback,
        onShellCommandsPersistFn = onShellCommandsPersist,
        onQuickLauncherPanelItemsPersistFn = onQuickLauncherPanelItemsPersist,
        onShellPanelFocusChangeFn = onShellPanelFocusChange,
        onOverlayWindowSuspendFn = onOverlayWindowSuspend,
        onOverlayWindowResumeFn = onOverlayWindowResume,
        onShellPanelAuxiliaryPrepareFn = onShellPanelAuxiliaryPrepare,
        onShellPanelAuxiliaryDismissFn = onShellPanelAuxiliaryDismiss
    )

    private val shellCoordinator: ShellPanelOverlayController = ShellPanelOverlayController(overlayHosts)
    private val quickLauncherController: QuickLauncherOverlayController = QuickLauncherOverlayController(overlayHosts)

    /** 前台应用变化：钉住 +「仅本应用」时据此自动隐藏 / 恢复面板（恢复走 OverlayManager 那条路）。 */
    fun onForegroundPackageChanged(packageName: String?) {
        quickLauncherController.onForegroundPackageChanged(packageName)
    }
    private val indexPanelRenderer: IndexPanelRenderer = IndexPanelRenderer(overlayHosts).also {
        overlayHosts.indexPanelContentRectProvider = { it.indexPanelContentRect() }
    }
    private val adjustPanelController: AdjustPanelOverlayController = AdjustPanelOverlayController(overlayHosts)
    private val taskSwitcherController: TaskSwitcherOverlayController = TaskSwitcherOverlayController(overlayHosts)

    private var overlayAccessibilityDelegate: OverlayAccessibilityDelegate? = null
    private var lastAccessibilityFingerprint: Int = 0

    private val layoutCoordinator = EdgeGestureLayoutCoordinator(
        context = context,
        resources = resources,
        zoneLayout = zoneLayout,
        gestureSession = gestureSession,
        adjustPanelController = adjustPanelController,
        quickLauncherController = quickLauncherController,
        shellCoordinator = shellCoordinator,
        settingsProvider = { settings },
        previewModeProvider = { previewMode },
        viewSizeProvider = { width to height },
        onSessionEnd = onSessionEndCallback,
        screenSizeProvider = screenSizeProvider,
    )

    private val sessionCoordinator = EdgeGestureSessionCoordinator(
        view = this,
        gestureSession = gestureSession,
        panelGridSession = panelGridSession,
        panelEnterAnimator = panelEnterAnimator,
        adjustPanelController = adjustPanelController,
        taskSwitcherController = taskSwitcherController,
        quickLauncherController = quickLauncherController,
        shellCoordinator = shellCoordinator,
        gestureAnimationCoordinator = gestureAnimationCoordinator,
        layoutCoordinator = layoutCoordinator,
        actionExecutor = actionExecutor,
        settingsProvider = { settings },
        runAfterLayout = ::runAfterLayout,
        onSessionStartCallback = onSessionStartCallback,
        onAdjustPanelLayoutCallback = onAdjustPanelLayoutCallback,
        notifyPresentationTouchRequirementChanged = ::notifyPresentationTouchRequirementChanged,
        requestInvalidate = ::invalidate,
        indexPanelContentRect = { indexPanelRenderer.indexPanelContentRect() },
        onIndexSessionStart = ::warmIndexLaunchIcons,
        notifyAccessibilityStructure = ::notifyOverlayAccessibilityStructureIfNeeded
    )

    init {
        gestureCallbacks.delegate = sessionCoordinator
    }

    private val panelRenderer = EdgeGesturePanelRenderer(
        side = side,
        gestureSession = gestureSession,
        indexSession = indexSession,
        adjustPanelController = adjustPanelController,
        indexPanelRenderer = indexPanelRenderer,
        quickLauncherController = quickLauncherController,
        taskSwitcherController = taskSwitcherController,
        shellCoordinator = shellCoordinator,
        panelEnterAnimator = panelEnterAnimator,
        panelContentRect = panelContentRect,
        zoneLayout = zoneLayout,
        settingsProvider = { settings },
        previewModeProvider = { previewMode },
        previewContentProvider = { previewContent },
        previewFocusProvider = { previewFocus },
        densityProvider = { resources.displayMetrics.density },
        dpFn = ::dp,
        syncZoneLayout = { layoutCoordinator.syncZoneLayout() }
    )

    private val touchDispatcher = EdgeGestureTouchDispatcher(
        gestureSession = gestureSession,
        adjustPanelController = adjustPanelController,
        quickLauncherController = quickLauncherController,
        shellCoordinator = shellCoordinator,
        taskSwitcherController = taskSwitcherController,
        indexPanelRenderer = indexPanelRenderer,
        gestureAnimationCoordinator = gestureAnimationCoordinator,
        rawToLocal = ::rawToLocal,
        forEachGesturePoint = ::forEachGesturePoint,
        onGestureTrackingStart = onGestureTrackingStartCallback,
        onSyncZoneLayout = { layoutCoordinator.syncZoneLayout() },
        onForceRecoverInteractionState = ::forceRecoverInteractionState,
        edgeCaptureTouchActive = { edgeCaptureTouchActive },
        setEdgeCaptureTouchActive = { edgeCaptureTouchActive = it },
        composeOverlayDialogShowing = layoutCoordinator::composeOverlayDialogShowing
    )

    /**
     * "按住才成立"的手势兜底：会话/触钮取词超过 [INTERACTION_IDLE_TIMEOUT_MS]
     * 收不到任何输入事件，说明 UP/CANCEL 丢了，直接强制复位。
     * 不复位的话全屏直触的 presentation 会一直吞掉整屏触摸。
     */
    private val interactionWatchdog: StuckGestureWatchdog by lazy(LazyThreadSafetyMode.NONE) {
        StuckGestureWatchdog(
            timeoutMs = INTERACTION_IDLE_TIMEOUT_MS,
            isHolding = { edgeCaptureTouchActive || gestureSession.isActive() },
            onStall = { forceRecoverInteractionState() }
        )
    }

    init {
        isClickable = true
        isFocusableInTouchMode = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        overlayAccessibilityDelegate = installEdgeGestureOverlayAccessibility(this) {
            buildOverlayAccessibilitySnapshot()
        }
        setOnKeyListener { _, keyCode, event ->
            if (keyCode != KeyEvent.KEYCODE_BACK || event.action != KeyEvent.ACTION_UP) {
                return@setOnKeyListener false
            }
            when {
                shellCoordinator.handleBackPress() -> true
                quickLauncherController.handleBackPress() -> true
                taskSwitcherController.handleBackPress() -> true
                else -> false
            }
        }
    }

    fun gestureAnimationCoordinator(): GestureAnimationCoordinator = gestureAnimationCoordinator

    fun applySettings(newSettings: AppSettings, screenWidth: Int) {
        settings = newSettings
        shellCoordinator.syncSettings(newSettings)
        quickLauncherController.syncSettings(newSettings)
        indexPanelRenderer.syncSettings(newSettings)
        gestureSession.applySettings(newSettings)
        quickLauncherController.invalidateDerivedCaches()
        gestureAnimationCoordinator.applySettings(newSettings)
        layoutCoordinator.syncZoneLayout()
        invalidate()
    }

    /**
     * 面板布局引擎读的"触发带"。
     *
     * 常规就是本侧触钮的区间。**本侧没有任何触钮**时它是空矩形，而两个面板布局引擎都会把锚点
     * `coerceIn(trigger.top, trigger.bottom)` = 夹成 0 → 面板贴到屏幕最上沿；这时改用按本次
     * 触发点合成的窄带（贴本侧、纵向=触发那一行）。
     */
    private fun activeTriggerZoneRect(): RectF {
        val base = layoutCoordinator.activeTriggerZoneRect()
        if (!base.isEmpty) return base
        val anchorLocalY = externalPanelAnchorLocalY ?: return base
        val viewWidth = if (width > 0) width else resources.displayMetrics.widthPixels
        val viewHeight = if (height > 0) height else resources.displayMetrics.heightPixels
        return PanelTriggerBand.synthetic(
            side = side,
            anchorLocalY = anchorLocalY,
            viewWidthPx = viewWidth,
            viewHeightPx = viewHeight,
            thicknessPx = dp(PANEL_TRIGGER_BAND_DP),
        )
    }

    private fun notifyOverlayLayoutIfNeeded() = layoutCoordinator.notifyOverlayLayoutIfNeeded()

    /**
     * 外部触发打开面板（边滑触钮之外的入口：角轮盘 / 悬浮球 / 双贴边线条 / 圆环条目）。
     *
     * 本侧可能**完全没触钮**（画布由 `OverlayManager` 按需挂成"面板专用宿主"），此时没有真实
     * 触发带，必须记下触发高度让 [activeTriggerZoneRect] 合成一条，否则面板会跑到屏幕最上面。
     */
    fun dispatchExternalAction(action: GestureAction, anchorRawY: Float): Boolean {
        layoutCoordinator.applyExpandedOverlayLayout()
        runAfterLayout {
            val loc = IntArray(2)
            locationOnScreenLogical(loc)
            val screenHeight = resources.displayMetrics.heightPixels.toFloat().coerceAtLeast(1f)
            val viewHeight = logicalHeightPx().toFloat().coerceAtLeast(1f)
            val localY = (anchorRawY - loc[1]).coerceIn(0f, viewHeight)
            val screenWidth = resources.displayMetrics.widthPixels.toFloat().coerceAtLeast(1f)
            val localX = logicalWidthPx() / 2f
            val anchorRawX = loc[0] + localX
            externalPanelAnchorLocalY = localY
            when (action) {
                is GestureAction.TaskSwitcher -> taskSwitcherController.setExternalAnchor(anchorRawY)
                is GestureAction.QuickLauncher -> quickLauncherController.setAnchorRawY(anchorRawY)
                else -> Unit
            }
            gestureSession.openDiscretePanel(action, localX, localY, anchorRawX, anchorRawY)
            notifyPresentationTouchRequirementChanged()
            invalidate()
        }
        return true
    }

    fun isSessionActive(): Boolean = gestureSession.isActive()

    fun panelMode(): OverlayPanelMode = gestureSession.panelMode()

    fun applyCollapsedTriggerLayout(bounds: CollapsedWindowBounds) {
        layoutCoordinator.applyExpandedOverlayLayout()
    }

    fun applyExpandedOverlayLayout() = layoutCoordinator.applyExpandedOverlayLayout()

    fun needsPresentationDirectTouch(): Boolean = layoutCoordinator.needsPresentationDirectTouch()

    fun presentationShouldPassthroughTouches(): Boolean =
        layoutCoordinator.presentationShouldPassthroughTouches()

    fun syncOverlayDialogZOrder() {
        quickLauncherController.syncOverlayDialogZOrder()
    }

    var onPresentationTouchRequirementChanged: (() -> Unit)? = null

    private fun notifyPresentationTouchRequirementChanged() {
        // 面板与手势都结束了：清掉外部触发的锚点（驻留面板仍在场时 panelMode != NONE，不会误清）。
        if (gestureSession.panelMode() == OverlayPanelMode.NONE && !gestureSession.isActive()) {
            externalPanelAnchorLocalY = null
        }
        // 交互态变化时同步看门狗：按住才成立的状态要续租，退出交互态要撤销。
        interactionWatchdog.onStateChanged()
        onPresentationTouchRequirementChanged?.invoke()
    }

    fun handleOverlayTouch(event: MotionEvent): Boolean {
        val handled = touchDispatcher.handleTouch(event)
        EdgeDiag.log(
            "touch",
            "handleOverlayTouch action=${MotionEvent.actionToString(event.actionMasked)} " +
                "handled=$handled panelMode=${gestureSession.panelMode()} " +
                "active=${gestureSession.isActive()} raw=(${event.rawX},${event.rawY})"
        )
        interactionWatchdog.onInput()
        return handled
    }

    /**
     * 处理由 LSPosed 模块在输入层接管并转发过来的触摸事件（屏幕原始坐标）。
     *
     * 复用与窗口触摸完全相同的分发路径，保证面板跟手、Pie、连续调节、子手势行为一致。
     */
    fun handleForwardedTouch(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            layoutCoordinator.applyExpandedOverlayLayout()
        }
        return touchDispatcher.handleTouch(event).also { interactionWatchdog.onInput() }
    }

    /** 模块侧判定会话需要提前结束（多指、屏幕关闭等）时调用。 */
    fun cancelForwardedTouch() {
        EdgeDiag.logStack(
            "module",
            "cancelForwardedTouch（LSPosed 接管会话结束/模块侧取消）→ 会 forceReset 并摘掉刚开的面板"
        )
        edgeCaptureTouchActive = false
        forceRecoverInteractionState()
    }

    fun handleCaptureStripTouch(event: MotionEvent, triggerIndex: Int): Boolean {
        val handle = settings.triggerHandles(side).getOrNull(triggerIndex) ?: return false
        val (localX, localY) = rawToLocal(event.rawX, event.rawY)
        EdgeDiag.log(
            "touch",
            "handleCaptureStripTouch action=${MotionEvent.actionToString(event.actionMasked)} " +
                "strip=$triggerIndex handle=${handle.id} panelMode=${gestureSession.panelMode()} " +
                "active=${gestureSession.isActive()} raw=(${event.rawX},${event.rawY})"
        )
        val handled = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                layoutCoordinator.syncZoneLayout()
                if (!touchDispatcher.beginCaptureStripTouch(
                        handleId = handle.id,
                        rawX = event.rawX,
                        rawY = event.rawY,
                        localX = localX,
                        localY = localY
                    )
                ) {
                    return false
                }
                edgeCaptureTouchActive = true
                true
            }
            else -> touchDispatcher.handleTouch(event)
        }
        interactionWatchdog.onInput()
        return handled
    }

    @SuppressLint("ClickableViewAccessibility") // Overlay gesture surface; not a clickable control
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (layoutCoordinator.composeOverlayDialogShowing()) {
            logDroppedTouch(event, "composeDialogShowing")
            return false
        }
        if (!needsPresentationDirectTouch()) {
            logDroppedTouch(event, "notDirectTouch")
            return false
        }
        return handleOverlayTouch(event)
    }

    /**
     * 全屏窗收到了触摸却没人处理 —— 这一拍就是用户眼里的「点哪都没反应」。
     *
     * 只在 DOWN / UP 打，避免 MOVE 刷屏；坐标、当前状态一起打出来，能直接对上"哪一扇窗吃掉了它"。
     */
    private fun logDroppedTouch(event: MotionEvent, reason: String) {
        val action = event.actionMasked
        if (action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_UP) return
        EdgeDiag.log(
            "touchDrop",
            "$reason action=${MotionEvent.actionToString(action)} raw=(${event.rawX},${event.rawY}) " +
                "panelMode=${gestureSession.panelMode()} adjust=${hasAdjustPanel()} " +
                "dismissing=${adjustPanelController.isDismissing()} session=${gestureSession.isActive()}"
        )
    }

    fun applyGestureTrackingLayout(bounds: CollapsedWindowBounds) =
        layoutCoordinator.applyGestureTrackingLayout(bounds)

    fun applyAdjustPanelOverlayLayout() = layoutCoordinator.applyAdjustPanelOverlayLayout()

    fun hasAdjustPanel(): Boolean = adjustPanelController.hasAdjustPanel()

    /** True when presentation shows real panel chrome, not mere finger-down gesture tracking. */
    fun needsChromeRaisedAbovePresentation(): Boolean =
        panelMode() != OverlayPanelMode.NONE ||
            hasAdjustPanel() ||
            (panelMode() == OverlayPanelMode.SHELL_COMMANDS && shellCoordinator.hasActiveUi())

    fun keepsOverlayExpanded(): Boolean = layoutCoordinator.keepsOverlayExpanded()

    fun forceRecoverInteractionState() {
        EdgeDiag.logStack(
            "recover",
            "forceRecoverInteractionState before: panelMode=${gestureSession.panelMode()} " +
                "active=${gestureSession.isActive()} adjust=${adjustPanelController.hasAdjustPanel()}"
        )
        if (adjustPanelController.isDismissing()) return
        // Activity 版 Shell 面板不随 edge recover 关闭（触钮点按收回会走到这里）。
        shellCoordinator.clearShellContinuousPick()
        gestureAnimationCoordinator.hide()
        edgeCaptureTouchActive = false
        adjustPanelController.forceRecover()
        gestureSession.forceReset(notifySessionEnd = false)
        interactionWatchdog.cancel()
        notifyPresentationTouchRequirementChanged()
        layoutCoordinator.syncZoneLayout()
        invalidate()
    }

    fun isPreviewMode(): Boolean = previewMode

    fun setPreviewMode(
        enabled: Boolean,
        content: LayoutPreviewContent = LayoutPreviewContent.TRIGGER_ONLY,
        focus: LayoutPreviewFocus? = null
    ) {
        previewMode = enabled
        previewContent = content
        previewFocus = focus
        layoutCoordinator.syncZoneLayout()
        invalidate()
    }

    fun setApps(newApps: List<AppInfo>) {
        if (appsContentEqual(apps, newApps)) return
        apps = newApps
        indexSession.setApps(newApps)
        quickLauncherController.setApps(newApps)
        warmIndexLaunchIcons()
        invalidate()
    }

    private fun appsContentEqual(old: List<AppInfo>, new: List<AppInfo>): Boolean {
        if (old.size != new.size) return false
        return old.indices.all { i ->
            val a = old[i]
            val b = new[i]
            a.packageName == b.packageName && a.label == b.label && a.letter == b.letter &&
                a.cloneUserId == b.cloneUserId &&
                a.isFrozen == b.isFrozen && a.isPaused == b.isPaused
        }
    }

    fun warmIndexLaunchIcons() {
        val size = iconSizePx.toInt().coerceAtLeast(1)
        appRepository.warmLaunchIconBitmapsAsync(apps.map { it.packageName }, size)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // 窗口 resize 已经发生：把挂起的几何（含画布平移量）应用到这一帧，
        // 平移与窗口尺寸因此永远同步（否则会先闪到窗口左上角）。
        pendingWindowGeometry?.let { geometry ->
            pendingWindowGeometry = null
            applyPresentationWindowGeometry(geometry.originX, geometry.originY, geometry.shrunk)
        }
        // 窗口被缩成面板矩形时，view 的尺寸不再等于整屏；绘制 / 命中仍按整屏坐标系算，
        // 所以只在"全屏"这一档记住逻辑屏幕尺寸（见 [logicalWidthPx]）。
        if (!presentationWindowShrunk && w > 0 && h > 0) {
            val logicalChanged = logicalScreenWidthPx != w || logicalScreenHeightPx != h
            logicalScreenWidthPx = w
            logicalScreenHeightPx = h
            // 只有「屏幕真的变了」（旋转 / 分屏 / 分辨率切换）才需要作废钉住冻结；
            // 钉住缩窗自己改窗口尺寸不算，否则每次缩窗都会把冻结打掉、面板跟着跳。
            if (logicalChanged) {
                quickLauncherController.onScreenSizeChanged()
            }
        }
        quickLauncherController.onSizeChanged()
        layoutCoordinator.syncZoneLayout()
        adjustPanelController.onSizeChanged()
    }

    /**
     * presentation 窗口是否已被缩成「面板矩形」。
     *
     * 钉住面板要在保持可见的同时把面板外的触摸让给下面的应用 —— 实现方式就是把窗口缩到面板那一块。
     * 缩了之后 [width]/[height] 是面板尺寸，而面板绘制 / 命中全部沿用整屏坐标，
     * 因此这里额外维护一份逻辑屏幕尺寸 + 绘制时的画布平移。
     */
    private var presentationWindowShrunk = false
    private var presentationWindowOriginX = 0
    private var presentationWindowOriginY = 0
    private var logicalScreenWidthPx = 0
    private var logicalScreenHeightPx = 0

    /**
     * 待应用的窗口几何。
     *
     * ⚠️ 窗口 resize 是**异步**的（[WindowManager.updateViewLayout] 提交后要等系统合成），
     * 而画布平移是同步的。如果先改平移再等窗口缩小，中间那一帧就是「新平移 + 旧窗口尺寸」：
     * 面板会被画到窗口左上角（真机表现：面板先闪到左上角，窗口缩完再回到原位）。
     * 所以几何先挂起，等 [onSizeChanged] 报出目标尺寸再一起生效。
     */
    private var pendingWindowGeometry: WindowGeometry? = null

    private data class WindowGeometry(
        val originX: Int,
        val originY: Int,
        val shrunk: Boolean,
    )

    /**
     * 由 [SideOverlayWindowManager] 下发窗口几何（缩窗 / 全屏）。
     *
     * [widthPx]/[heightPx] 是**这次下发的窗口目标尺寸**：与 view 当前尺寸不一致时说明 resize 还没发生，
     * 几何先挂起，等 [onSizeChanged] 再应用（保证平移量与窗口尺寸始终同一帧）。
     */
    fun setPresentationWindowGeometry(
        originX: Int,
        originY: Int,
        widthPx: Int,
        heightPx: Int,
        shrunk: Boolean,
    ) {
        if (widthPx != width || heightPx != height) {
            pendingWindowGeometry = WindowGeometry(originX, originY, shrunk)
            return
        }
        applyPresentationWindowGeometry(originX, originY, shrunk)
    }

    private fun applyPresentationWindowGeometry(originX: Int, originY: Int, shrunk: Boolean) {
        if (presentationWindowOriginX == originX &&
            presentationWindowOriginY == originY &&
            presentationWindowShrunk == shrunk
        ) {
            return
        }
        presentationWindowOriginX = originX
        presentationWindowOriginY = originY
        presentationWindowShrunk = shrunk
        com.slideindex.app.diagnostic.EdgeDiag.log(
            "qlPin",
            "窗口几何生效 origin=($originX,$originY) shrunk=$shrunk view=${width}x${height} " +
                "logical=${logicalWidthPx()}x${logicalHeightPx()}",
        )
        invalidate()
    }

    /**
     * 当前是否处于缩窗态。
     *
     * ⚠️ 按**真实尺寸**判断，不看 `presentationWindowShrunk` 标志：窗口 resize 是 WMS 异步应用的，
     * 标志由我们的回调更新，两者之间存在中间态；用标志去决定"脏矩形要不要换算坐标"会在中间态
     * 算错（与 [onDraw] 保持同一判据，绘制与局部重绘才不会错位）。
     */
    fun isPresentationWindowShrunk(): Boolean =
        width < logicalWidthPx() || height < logicalHeightPx()

    /** 逻辑屏幕宽 / 高：缩窗期间仍按整屏算，面板几何才不会跟着窗口一起缩。 */
    fun logicalWidthPx(): Int = if (logicalScreenWidthPx > 0) logicalScreenWidthPx else width

    fun logicalHeightPx(): Int = if (logicalScreenHeightPx > 0) logicalScreenHeightPx else height

    /**
     * 钉住面板的窗口矩形（屏幕坐标）；不用缩窗时返回 null。
     *
     * 只在小手已经抬起、面板还停在屏上的「钉住 idle」态返回，其余时刻一律全屏 ——
     * 呼出动画、手势跟手、面板收起动画都还在整屏坐标系里跑。
     */
    fun pinnedQuickLauncherWindowRect(): RectF? =
        quickLauncherController.pinnedWindowRect(logicalWidthPx().toFloat(), logicalHeightPx().toFloat())

    /** 图标库里改了应用图标：索引 / 任务切换器是 Canvas 绘制，必须主动重绘。 */
    private val iconChangeListener: () -> Unit = { invalidate() }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        com.slideindex.app.ui.GlobalIconStore.addIconChangeListener(iconChangeListener)
    }

    override fun onDetachedFromWindow() {
        com.slideindex.app.ui.GlobalIconStore.removeIconChangeListener(iconChangeListener)
        gestureAnimationCoordinator.hide()
        adjustPanelController.onDetachedFromWindow()
        GestureActionIconBitmap.clear()
        super.onDetachedFromWindow()
    }

    fun showAdjustPanel(
        mode: ContinuousAdjustController.Mode,
        fraction: Float,
        anchorRawY: Float,
        @Suppress("UNUSED_PARAMETER") deferWindowLayout: Boolean = false
    ) {
        onAdjustPanelLayoutCallback(anchorRawY)
        adjustPanelController.showAdjustPanel(mode, fraction, anchorRawY)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 绘制窗口**永远全屏**（包括钉住态），坐标系就是屏幕坐标系，不需要任何平移。
        // 钉住态的面板外触摸穿透由独立的触摸层窗口实现，窗口几何不变 → 不存在
        // "窗口位置/尺寸与画布平移不同步"的闪跳帧。
        panelRenderer.draw(canvas, width, height)
    }

    /**
     * 钉住面板触摸层转发进来的触摸事件（该层只有面板那么大）。
     *
     * 事件来自另一个窗口，`rawX/rawY` 仍是屏幕坐标，与绘制层的屏幕坐标系一致。
     */
    fun dispatchPinnedTouch(event: MotionEvent): Boolean = handleForwardedTouch(event)

    private fun buildOverlayAccessibilitySnapshot(): OverlayAccessibilitySnapshot =
        EdgeGestureOverlayAccessibilityCollector.collect(
            context = context,
            side = side,
            panelMode = gestureSession.panelMode(),
            zoneLayout = zoneLayout,
            indexSession = indexSession,
            panelGridSession = panelGridSession,
            quickLauncherController = quickLauncherController,
            taskSwitcherController = taskSwitcherController,
            shellPanelController = shellCoordinator.shellCommandPanelController(),
            appsByPackage = quickLauncherController.quickLauncherAppsByPackage
        )

    private fun notifyOverlayAccessibilityIfChanged() {
        val fingerprint = buildOverlayAccessibilitySnapshot().nodes.hashCode() +
            gestureSession.panelMode().ordinal * 31
        if (fingerprint != lastAccessibilityFingerprint) {
            lastAccessibilityFingerprint = fingerprint
            overlayAccessibilityDelegate?.notifyStructureChanged()
        }
    }

    fun notifyOverlayAccessibilityStructureIfNeeded() {
        if (!isAttachedToWindow) return
        post { notifyOverlayAccessibilityIfChanged() }
    }

    override fun invalidate() {
        super.invalidate()
        if (!isAttachedToWindow) return
        if (gestureSession.isActive() ||
            (width > 0 && height > 0 && !edgeCaptureTouchActive)
        ) {
            notifyOverlayAccessibilityStructureIfNeeded()
        }
    }

    override fun hapticLetterTick() = sessionCoordinator.hapticLetterTick()
    override fun hapticAppTick() = sessionCoordinator.hapticAppTick()
    override fun hapticConfirmLaunch() = sessionCoordinator.hapticConfirmLaunch()
    override fun scheduleDelayed(runnable: Runnable, delayMs: Long) =
        sessionCoordinator.scheduleDelayed(runnable, delayMs)
    override fun cancelDelayed(runnable: Runnable) = sessionCoordinator.cancelDelayed(runnable)
    override fun requestInvalidate() {
        if (gestureSession.panelMode() == OverlayPanelMode.INDEX) {
            invalidateIndexPanel()
        } else {
            sessionCoordinator.requestInvalidateThrottled()
        }
    }

    @Suppress("DEPRECATION")
    private fun invalidateIndexPanel() {
        val rect = indexPanelRenderer.indexPanelContentRect()
        val pad = dp(4f).toInt()
        invalidate(
            (rect.left - pad).toInt().coerceAtLeast(0),
            (rect.top - pad).toInt().coerceAtLeast(0),
            (rect.right + pad).toInt().coerceAtMost(width.coerceAtLeast(1)),
            (rect.bottom + pad).toInt().coerceAtMost(height.coerceAtLeast(1))
        )
        notifyOverlayAccessibilityStructureIfNeeded()
    }

    private fun runAfterLayout(block: () -> Unit) {
        if (isAttachedToWindow && width > 0 && height > 0) {
            block()
            return
        }
        val observer = viewTreeObserver
        if (!observer.isAlive) {
            post { runAfterLayout(block) }
            return
        }
        observer.addOnGlobalLayoutListener(
            object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    if (width <= 0 || height <= 0) return
                    viewTreeObserver.removeOnGlobalLayoutListener(this)
                    block()
                }
            }
        )
        requestLayout()
    }

    private fun forEachGesturePoint(
        event: MotionEvent,
        localX: Float,
        localY: Float,
        includeHistory: Boolean,
        block: (rawX: Float, rawY: Float, localX: Float, localY: Float) -> Unit
    ) {
        if (includeHistory) {
            val rawOffsetX = event.rawX - event.x
            val rawOffsetY = event.rawY - event.y
            for (i in 0 until event.historySize) {
                val rawX = event.getHistoricalX(i) + rawOffsetX
                val rawY = event.getHistoricalY(i) + rawOffsetY
                val (lx, ly) = rawToLocal(rawX, rawY)
                block(rawX, rawY, lx, ly)
            }
        }
        block(event.rawX, event.rawY, localX, localY)
    }

    /**
     * 「逻辑原点」= 整屏左上角在 view 坐标系里的位置。
     *
     * 缩窗期间真实窗口origin 在 (x0,y0)，但面板矩形 / 触钮区域 / 命中判定全部沿用**整屏坐标**，
     * 所以触摸与定位换算必须按逻辑原点（0,0）算：否则触摸（窗口本地坐标）与面板矩形差一个窗口原点，
     * 表现为「点面板上某处却命中别处」，同时锚点会漂、面板自己挪位置。
     */
    internal fun locationOnScreenLogical(out: IntArray) {
        if (presentationWindowShrunk) {
            out[0] = 0
            out[1] = 0
        } else {
            getLocationOnScreen(out)
        }
    }

    private fun rawToLocal(rawX: Float, rawY: Float): Pair<Float, Float> {
        val loc = IntArray(2)
        locationOnScreenLogical(loc)
        return rawX - loc[0] to rawY - loc[1]
    }

    /**
     * 索引面板 / 任务切换器绘制路径取图标：只读缓存，未命中返回透明占位并提交后台加载。
     *
     * 这里原来直接调 `launchIconBitmap`（缓存未命中会同步打 PackageManager）——
     * 在魅族上那一条会走主题图标逐像素重建，主线程直接卡到 ANR。
     */
    private fun iconFor(app: AppInfo): Bitmap {
        val size = iconSizePx.toInt().coerceAtLeast(1)
        // 全局图标覆盖优先（扩展 → 图标库 → 应用图标）
        com.slideindex.app.ui.AppIconOverride.bitmap(context, app.packageName, size)?.let { return it }
        appRepository.peekLaunchIconBitmap(app.packageName, size)?.let { return it }
        appRepository.requestLaunchIconBitmapAsync(app.packageName, size) {
            if (panelMode() != OverlayPanelMode.NONE) invalidate()
        }
        return transparentIcon(size)
    }

    private val transparentIcons = mutableMapOf<Int, Bitmap>()

    /** 图标还没解析出来时的占位：透明（与 AppLaunchIconCache 的 ColorDrawable(0) 观感一致）。 */
    private fun transparentIcon(size: Int): Bitmap =
        transparentIcons.getOrPut(size) { Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888) }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun sp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)
}
