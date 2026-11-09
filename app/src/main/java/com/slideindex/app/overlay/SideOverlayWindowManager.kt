package com.slideindex.app.overlay

import android.graphics.Color
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import com.slideindex.app.diagnostic.EdgeDiag
import com.slideindex.app.gesture.CollapsedWindowBounds
import com.slideindex.app.gesture.GestureZoneLayout
import com.slideindex.app.gesture.KeyboardTriggerBoundsAdjuster
import com.slideindex.app.gesture.TriggerHandleDesign
import com.slideindex.app.overlay.backpanel.BackPanelOverlayRegistry
import com.slideindex.app.overlay.compositor.OverlaySceneController
import com.slideindex.app.settings.keyboardTriggerBehavior
import com.slideindex.app.settings.keyboardTriggerNarrowScale
import com.slideindex.app.settings.triggerHandles
import com.slideindex.app.util.OverlayBrightnessControl

internal class SideOverlayWindowManager(
    private val ctrl: SideOverlayController
) {
    private val context get() = ctrl.context
    private val side get() = ctrl.side
    private val windowManager get() = ctrl.androidWindowManager
    private val overlayContext get() = ctrl.overlayContext
    private val renderer get() = ctrl.renderer

    internal var presentationView: EdgeGestureOverlayView? = null
    internal var presentationContainer: FrameLayout? = null
    internal var presentationParams: WindowManager.LayoutParams? = null
    internal var presentationAttached = false
    internal val touchCaptureWindows = mutableListOf<CaptureWindow>()
    /** Legacy list; exclusion is now [EdgeTouchCaptureView] exclusionRects, not separate WM windows. */
    internal val exclusionWindows = mutableListOf<CaptureWindow>()
    internal var edgeOverlayDetached = false
    private var capturePassthroughSuspended = false
    private val trackedWmViews = mutableListOf<View>()

    internal fun trackOverlayView(view: View) {
        if (view !in trackedWmViews) {
            trackedWmViews += view
        }
    }

    internal fun untrackOverlayView(view: View) {
        trackedWmViews.remove(view)
    }

    private fun addOverlayView(view: View, params: WindowManager.LayoutParams) {
        OverlayWindowTypes.ensureNoBrightnessOverride(params)
        OverlayWindowGuard.addView(
            windowManager,
            view,
            params,
            "side-${side}-${view.javaClass.simpleName}",
        )
        com.slideindex.app.perf.OverlayDrawProbe.attach(
            view,
            "edge-${view.javaClass.simpleName}@${System.identityHashCode(view) % 1000}",
        )
        trackOverlayView(view)
    }

    private fun removeOverlayView(view: View) {
        untrackOverlayView(view)
        runCatching { windowManager.removeView(view) }
    }

    internal fun purgeAllTrackedOverlayViews() {
        trackedWmViews.toList().forEach { view ->
            // ⚠️ presentation 容器**不能**在这里顺手删：它的生命周期由 `presentationAttached` /
            // [detachPresentationWindow] 管理。而"按需挂的面板专用宿主"（该侧没有触钮时用来承载
            // 快速启动器/任务切换器的全屏窗）也登记在 trackedWmViews 里，被这里静默删掉的表现就是
            // "挂窗日志全正常、屏幕上一个像素都没有"——真机查了很久就是这个。
            if (view === presentationContainer) return@forEach
            runCatching { windowManager.removeView(view) }
            trackedWmViews.remove(view)
        }
    }

    internal fun suspendCaptureTouchForPassthrough() {
        capturePassthroughSuspended = true
        detachPresentationWindow()
        detachTouchCaptureViewsOnly()
        detachExclusionViewsOnly()
    }

    internal fun resumeCaptureTouchAfterPassthrough() {
        if (!capturePassthroughSuspended) return
        capturePassthroughSuspended = false
        if (overlayLayoutSuspended() || edgeOverlayDetached) return
        if (presentationView?.presentationShouldPassthroughTouches() == true) return
        val presentation = presentationView ?: return
        reattachCaptureWindows()
        syncCaptureWindows(presentation, forceLayout = true, applyToWindowManager = true)
        syncPresentationTouchState()
    }

    internal var overlayBrightnessFraction: Float? = null

    internal val overlayBrightness = OverlayBrightnessControl { fraction ->
        applyOverlayWindowBrightness(fraction)
    }

    fun presentationRoot(): View? = presentationContainer

    private fun onPresentationNewlyAttached() {
        presentationAttached = true
    }

    fun needsChromeRaisedAbovePresentation(): Boolean {
        if (overlayLayoutSuspended() || !presentationAttached) return false
        val view = presentationView ?: return false
        return view.needsChromeRaisedAbovePresentation()
    }

    fun ensurePresentationAttached(forceWhenIdle: Boolean = false) {
        if (overlayLayoutSuspended()) {
            EdgeDiag.log(
                "window",
                "ensurePresentationAttached 跳过：overlayLayoutSuspended=true " +
                    "(edgeOverlayDetached=$edgeOverlayDetached " +
                    "trampolineGuard=${OverlayTrampolineGuard.blocksOverlayPresentationTouch()})" +
                    " panelMode=${presentationView?.panelMode()}"
            )
            return
        }
        val root = presentationRoot() ?: return
        val content = presentationView ?: return
        val params = presentationParams ?: return
        if (!forceWhenIdle &&
            !ctrl.previewMode &&
            !content.isSessionActive() &&
            !content.needsPresentationDirectTouch()
        ) {
            EdgeDiag.log(
                "window",
                "ensurePresentationAttached 跳过：非会话且不需要直触 panelMode=${content.panelMode()}"
            )
            return
        }
        applyPresentationWindowLayout(content, params)
        clearPresentationBrightnessOverride(params)
        applyPresentationTouchFlags(content, params)
        if (!presentationAttached) {
            EdgeDiag.log(
                "window",
                "presentation 窗口 addView（panelMode=${content.panelMode()} " +
                    "session=${content.isSessionActive()}）"
            )
            runCatching { addOverlayView(root, params) }
                .onSuccess { onPresentationNewlyAttached() }
                .onFailure { Log.e(TAG, "Failed to attach presentation overlay", it) }
        } else {
            EdgeDiag.log(
                "window",
                "presentation 窗口 updateViewLayout（panelMode=${content.panelMode()} " +
                    "session=${content.isSessionActive()}）"
            )
            runCatching { windowManager.updateViewLayout(root, params) }
                .onFailure { Log.e(TAG, "Failed to sync presentation overlay", it) }
        }
    }

    fun detachPresentationWindow() {
        // 触摸层必须跟着绘制层一起摘：否则面板没了，那块「看不见却吃触摸」的窗口会留在屏幕上。
        pinnedTouchSlot?.let { slot ->
            removeOverlayView(slot.view)
            pinnedTouchSlot = null
        }
        if (!presentationAttached) return
        EdgeDiag.logStack(
            "window",
            "detachPresentationWindow：摘掉全屏 presentation（panelMode=${presentationView?.panelMode()} " +
                "session=${presentationView?.isSessionActive()}）"
        )
        presentationParams?.let { clearPresentationBrightnessOverride(it) }
        presentationRoot()?.let { removeOverlayView(it) }
        presentationAttached = false
    }

    fun detachPresentationUnlessRequired() {
        if (overlayLayoutSuspended()) return
        // 【常驻组合】该侧配了触钮时，空闲也不摘 presentation：
        // 一旦摘掉，手势提示动画的 Compose 组合就没了，下次手势又要现搭（实测 DOWN 之后
        // 18~32ms 才 BIND，动画因此在手指滑出一段后才出现）。
        // 空闲态本来就已是 passthrough（NOT_TOUCHABLE）标志，不会挡住下面的应用。
        //
        // Oplus 15+ 例外：整屏透明窗会命中「黑屏浮窗」反诈规则，开启无障碍约 2s 后被系统
        // 强摘（issue #42）。这类 ROM 上退回按需挂载，见 [EdgeIdlePresentationPolicy]。
        if (ctrl.settings.triggerHandles(side).isNotEmpty() &&
            EdgeIdlePresentationPolicy.keepPresentationWhileIdle
        ) {
            return
        }
        val view = presentationView ?: return
        if (ctrl.previewMode || view.isSessionActive() || view.needsPresentationDirectTouch() ||
            view.keepsOverlayExpanded() ||
            BackPanelOverlayRegistry.isAnyShowing()
        ) {
            return
        }
        EdgeDiag.log(
            "window",
            "detachPresentationUnlessRequired 判定为 idle → 摘窗 " +
                "(panelMode=${view.panelMode()} session=${view.isSessionActive()} " +
                "directTouch=${view.needsPresentationDirectTouch()} keepsExpanded=${view.keepsOverlayExpanded()})"
        )
        detachPresentationWindow()
    }



    fun syncCaptureWindowLayout() {
        val presentation = presentationView ?: return
        // 只在还没挂上时补挂：ensurePresentationAttached() 对已挂窗口会走 updateViewLayout，
        // 而本方法调用频繁，无条件调用会变成持续重排（实测空闲主线程 ~35% 空转、零绘制）。
        if (!presentationAttached &&
            ctrl.settings.triggerHandles(side).isNotEmpty() &&
            EdgeIdlePresentationPolicy.keepPresentationWhileIdle
        ) {
            ensurePresentationAttached(forceWhenIdle = true)
        }
        if (ctrl.settings.triggerHandles(side).isEmpty()) {
            // 该侧没有触钮：只收触钮捕获窗与系统手势排除区。
            // ⚠️ 必须**逐类收**，不能走 [detachAllCaptureWindows]：它会
            // `purgeAllTrackedOverlayViews()`，把"面板专用宿主"（该侧没触钮时按需挂的全屏 presentation）
            // 一起静默摘掉。而 `prepareExternalGestureDispatch()` 刚挂完宿主就会调到这里 ——
            // 现象就是"面板日志、窗口状态全部正常，屏幕上什么都没有"。
            detachTouchCaptureWindows()
            renderer.detachAllTriggerVisualWindows()
            detachAllExclusionWindows()
            return
        }
        syncCaptureWindows(
            presentation = presentation,
            forceLayout = true,
            applyToWindowManager = !edgeOverlayDetached
        )
        ctrl.syncRuntimeVisuals()
    }

    private fun captureTouchHandler(
        presentation: EdgeGestureOverlayView,
        triggerIndex: Int
    ): (android.view.MotionEvent) -> Boolean = { event ->
        if (ctrl.settings.triggerHandles(side).isEmpty()) {
            false
        } else {
            // 空闲不挂窗的 ROM（[EdgeIdlePresentationPolicy]）：按下这一拍补挂 presentation，
            // 否则手势动画的 Compose 宿主不在窗口里，等于这条手势没有提示动画。
            if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN &&
                !presentationAttached && !overlayLayoutSuspended()
            ) {
                ensurePresentationAttached(forceWhenIdle = true)
            }
            presentation.handleCaptureStripTouch(event, triggerIndex)
        }
    }

    fun syncPresentationTouchState() {
        if (overlayLayoutSuspended()) return
        val content = presentationView ?: return
        val root = presentationRoot() ?: return
        val params = presentationParams ?: return
        if (content.presentationShouldPassthroughTouches() &&
            content.panelMode() == OverlayPanelMode.NONE
        ) {
            if (content.needsPresentationDirectTouch()) {
                if (!presentationAttached) {
                    ensurePresentationAttached()
                } else {
                    applyPresentationWindowLayout(content, params)
                    applyPresentationPassthroughFlags(params)
                    clearPresentationBrightnessOverride(params)
                    runCatching { windowManager.updateViewLayout(root, params) }
                        .onFailure { Log.e(TAG, "Failed to sync presentation passthrough", it) }
                }
            } else if (presentationAttached) {
                detachPresentationWindow()
            }
            syncCaptureWindows(content)
            content.syncOverlayDialogZOrder()
            return
        }
        val edgeOnlyGestureTracking = content.isSessionActive() &&
            content.panelMode() == OverlayPanelMode.NONE &&
            !content.needsPresentationDirectTouch()
        if (edgeOnlyGestureTracking) {
            // 全屏 NOT_TOUCHABLE presentation：承载手势动画 ComposeView（动画挂在 container 上）。
            applyPresentationWindowLayout(content, params)
            applyPresentationPassthroughFlags(params)
            if (!presentationAttached) {
                runCatching { addOverlayView(root, params) }
                    .onSuccess { onPresentationNewlyAttached() }
                    .onFailure { Log.e(TAG, "Failed to attach presentation for edge gesture", it) }
            } else {
                runCatching { windowManager.updateViewLayout(root, params) }
                    .onFailure { Log.e(TAG, "Failed to sync presentation for edge gesture", it) }
            }
            return
        }
        if (!presentationAttached) {
            if (ctrl.previewMode && content.panelMode() == OverlayPanelMode.NONE && !content.hasAdjustPanel()) {
                ensurePresentationAttached()
                if (presentationAttached) {
                    renderer.applyPreviewPresentationWindow()
                }
                return
            }
            if (!content.needsPresentationDirectTouch()) return
            ensurePresentationAttached()
            if (!presentationAttached) return
        }
        if (ctrl.previewMode && content.panelMode() == OverlayPanelMode.NONE && !content.hasAdjustPanel()) {
            renderer.applyPreviewPresentationWindow()
            return
        }
        applyPresentationWindowLayout(content, params)
        applyPresentationTouchFlags(content, params)
        clearPresentationBrightnessOverride(params)
        runCatching { windowManager.updateViewLayout(root, params) }
            .onFailure { Log.e(TAG, "Failed to sync presentation touch state", it) }
    }

    fun clearOverlayWindowBrightness() {
        overlayBrightnessFraction = null
        presentationParams?.let { OverlayWindowTypes.ensureNoBrightnessOverride(it) }
        touchCaptureWindows.forEach { slot ->
            OverlayWindowTypes.ensureNoBrightnessOverride(slot.params)
        }
    }

    fun suspendEdgeOverlay() {
        if (edgeOverlayDetached) return
        clearOverlayWindowBrightness()
        detachPresentationWindow()
        touchCaptureWindows.forEach { slot ->
            removeOverlayView(slot.view)
        }
        edgeOverlayDetached = true
    }

    fun resumeEdgeOverlay() {
        if (!edgeOverlayDetached) return
        if (OverlayTrampolineGuard.blocksOverlayResume()) return
        presentationView?.let { syncCaptureWindows(it, forceLayout = true, applyToWindowManager = true) }
        if (touchCaptureWindows.isEmpty()) {
            edgeOverlayDetached = false
            return
        }
        touchCaptureWindows.forEach { slot ->
            runCatching { addOverlayView(slot.view, slot.params) }
                .onFailure { Log.e(TAG, "Failed to resume capture overlay", it) }
        }
        renderer.syncTriggerVisualWindows()
        if (ctrl.previewMode || presentationView?.keepsOverlayExpanded() == true) {
            ensurePresentationAttached()
        }
        syncPresentationTouchState()
        edgeOverlayDetached = false
    }

    fun detachTouchCaptureWindows() {
        detachTouchCaptureViewsOnly()
        touchCaptureWindows.clear()
    }

    fun detachTouchCaptureViewsOnly() {
        touchCaptureWindows.forEach { slot ->
            removeOverlayView(slot.view)
        }
        touchCaptureWindows.clear()
        // 兜底：同步路径曾用「只清表不动 WM」的方式收尾，留下过一批无人管理、却仍然可触摸的触钮窗。
        // 这类残窗会吞掉点击穿透注入的那一拍（穿透失效），并再次触发同一个放行动作（自持抽搐）。
        trackedWmViews.toList()
            .filterIsInstance<EdgeTouchCaptureView>()
            .forEach { removeOverlayView(it) }
    }

    fun reattachCaptureWindows() {
        if (overlayLayoutSuspended()) return
        touchCaptureWindows.forEach { slot ->
            runCatching { addOverlayView(slot.view, slot.params) }
                .onFailure { Log.e(TAG, "Failed to reattach capture overlay", it) }
        }
        renderer.syncTriggerVisualWindows()
    }

    fun detachAllCaptureWindows() {
        detachTouchCaptureWindows()
        renderer.detachAllTriggerVisualWindows()
        detachAllExclusionWindows()
        purgeAllTrackedOverlayViews()
    }

    fun detachAllExclusionWindows() {
        detachExclusionViewsOnly()
        exclusionWindows.clear()
    }

    fun detachExclusionViewsOnly() {
        exclusionWindows.forEach { slot ->
            removeOverlayView(slot.view)
        }
        exclusionWindows.clear()
    }

    fun attachCaptureWindows(presentation: EdgeGestureOverlayView) {
        if (overlayLayoutSuspended()) return
        val handles = ctrl.settings.triggerHandles(side)
        val passthrough = shouldPassthroughCaptureTouches()
        computeCaptureWindowBounds().forEachIndexed { index, bounds ->
            val params = createCaptureLayoutParams()
            applyCaptureLayout(params, bounds)
            if (passthrough || bounds.isZeroTouchStrip(side)) {
                applyPresentationPassthroughFlags(params)
            } else {
                applyCaptureTouchFlags(params)
            }
            val touchHandler = captureTouchHandler(presentation, index)
            val capture = EdgeTouchCaptureView(overlayContext, side, index, touchHandler)
            applyCaptureChrome(capture, handles.getOrNull(index)?.design)
            runCatching { addOverlayView(capture, params) }
                .onSuccess { touchCaptureWindows += CaptureWindow(capture, params) }
                .onFailure { Log.e(TAG, "Failed to add capture window", it) }
        }
    }

    fun attachExclusionWindows() {
        detachAllExclusionWindows()
    }

    fun syncCaptureWindows(
        presentation: EdgeGestureOverlayView,
        forceLayout: Boolean = false,
        applyToWindowManager: Boolean = true
    ) {
        if (!forceLayout && overlayLayoutSuspended()) return
        if (presentation.presentationShouldPassthroughTouches()) {
            // applyToWindowManager=false 表示调用方明确要求不动 WindowManager，此时追踪表也必须保持原样：
            // 清表会让窗口变成「没人管却还挂着」的幽灵窗（吞掉点击穿透），
            // 而摘窗又会让后续 reattachCaptureWindows() 无从回贴（触钮消失）。
            if (applyToWindowManager) {
                detachTouchCaptureViewsOnly()
            }
            exclusionWindows.clear()
            presentation.syncOverlayDialogZOrder()
            return
        }
        syncTouchCaptureWindows(presentation, applyToWindowManager)
    }

    fun setPresentationFocusable(focusable: Boolean) {
        if (!presentationAttached) return
        val view = presentationView ?: return
        val root = presentationRoot() ?: return
        val params = presentationParams ?: return
        if (focusable) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            view.clearFocus()
        }
        runCatching { windowManager.updateViewLayout(root, params) }
            .onFailure { Log.e(TAG, "Failed to update presentation focus", it) }
        if (focusable) {
            view.isFocusableInTouchMode = true
            view.requestFocus()
        }
    }

    fun createPresentationLayoutParams(): WindowManager.LayoutParams =
        OverlayWindowTypes.createPresentationParams(context)

    private fun syncTouchCaptureWindows(
        presentation: EdgeGestureOverlayView,
        applyToWindowManager: Boolean = true
    ) {
        val bounds = computeCaptureWindowBounds()
        val handles = ctrl.settings.triggerHandles(side)
        val passthrough = capturePassthroughSuspended || presentation.presentationShouldPassthroughTouches()
        while (touchCaptureWindows.size > bounds.size) {
            val slot = touchCaptureWindows.removeAt(touchCaptureWindows.lastIndex)
            removeOverlayView(slot.view)
        }
        bounds.forEachIndexed { index, bound ->
            val touchHandler = captureTouchHandler(presentation, index)
            val stripPassthrough = passthrough || bound.isZeroTouchStrip(side)
            if (index >= touchCaptureWindows.size) {
                if (!applyToWindowManager) return@forEachIndexed
                val params = createCaptureLayoutParams()
                applyCaptureLayout(params, bound)
                if (stripPassthrough) {
                    applyPresentationPassthroughFlags(params)
                } else {
                    applyCaptureTouchFlags(params)
                }
                val capture = EdgeTouchCaptureView(overlayContext, side, index, touchHandler)
                applyCaptureChrome(capture, handles.getOrNull(index)?.design)
                runCatching { addOverlayView(capture, params) }
                    .onSuccess { touchCaptureWindows += CaptureWindow(capture, params) }
                    .onFailure { Log.e(TAG, "Failed to add capture window", it) }
            } else {
                val slot = touchCaptureWindows[index]
                applyCaptureLayout(slot.params, bound)
                if (stripPassthrough) {
                    applyPresentationPassthroughFlags(slot.params)
                } else {
                    applyCaptureTouchFlags(slot.params)
                }
                (slot.view as? EdgeTouchCaptureView)?.let { capture ->
                    applyCaptureChrome(capture, handles.getOrNull(index)?.design)
                }
                if (applyToWindowManager) {
                    runCatching { windowManager.updateViewLayout(slot.view, slot.params) }
                        .onFailure { Log.e(TAG, "Failed to sync capture window layout", it) }
                }
            }
        }
    }

    private fun applyCaptureChrome(capture: EdgeTouchCaptureView, design: TriggerHandleDesign?) {
        capture.applyVisual(
            design = design ?: TriggerHandleDesign(),
            visible = ctrl.shouldShowRuntimeVisuals()
        )
        capture.setExcludeSystemGestures(
            !side.isVerticalEdge && ctrl.settings.interceptSystemBackGesture
        )
    }

    private fun applyOverlayWindowBrightness(fraction: Float?) {
        if (fraction == null) {
            clearOverlayWindowBrightness()
        }
    }

    private fun flushOverlayWindowBrightness() = Unit

    private fun clearPresentationBrightnessOverride(params: WindowManager.LayoutParams) {
        OverlayWindowTypes.ensureNoBrightnessOverride(params)
    }

    private fun computeCaptureWindowBounds(): List<CollapsedWindowBounds> {
        val raw = GestureZoneLayout.computeTouchCaptureWindowBounds(
            settings = ctrl.settings,
            side = side,
            screenWidthPx = ctrl.screenWidthPx,
            screenHeightPx = ctrl.screenHeightPx,
            density = ctrl.density,
        )
        val isLandscape = ctrl.isLandscapeLayout()
        return KeyboardTriggerBoundsAdjuster.adjustCollapsedBounds(
            bounds = raw,
            side = side,
            behavior = ctrl.settings.keyboardTriggerBehavior(isLandscape),
            imeTop = KeyboardTriggerImeState.imeTopOrNull(),
            density = ctrl.density,
            narrowScale = ctrl.settings.keyboardTriggerNarrowScale(isLandscape),
        )
    }

    private var lastPresentationTouchMode: String? = null

    private fun applyPresentationTouchFlags(
        view: EdgeGestureOverlayView,
        params: WindowManager.LayoutParams
    ) {
        val mode = resolvePresentationTouchMode(view)
        // 只在**状态变化**时打日志：这函数每次布局都会被调，逐次打会刷屏。
        if (mode != lastPresentationTouchMode) {
            lastPresentationTouchMode = mode
            EdgeDiag.log(
                "window",
                "presentation 触摸模式 → $mode " +
                    "(panelMode=${view.panelMode()} " +
                    "adjust=${view.hasAdjustPanel()} directTouch=${view.needsPresentationDirectTouch()} " +
                    "passthrough=${view.presentationShouldPassthroughTouches()} pinned=${view.pinnedQuickLauncherWindowRect() != null})"
            )
        }
        when (mode) {
            "interactive" -> applyPresentationInteractiveFlags(params)
            else -> applyPresentationPassthroughFlags(params)
        }
    }

    private fun resolvePresentationTouchMode(view: EdgeGestureOverlayView): String {
        // 钉住态：绘制窗口一律放行触摸（面板外直接落到下面的应用），
        // 面板内的触摸由 [syncPinnedTouchWindow] 那层小窗口接住并转发。
        if (view.pinnedQuickLauncherWindowRect() != null) return "passthrough(pinned)"
        if (!view.needsPresentationDirectTouch()) return "passthrough(idle)"
        val panelOpen = view.panelMode() != OverlayPanelMode.NONE
        if (!panelOpen && view.presentationShouldPassthroughTouches()) {
            return "passthrough(dialog)"
        }
        return "interactive"
    }

    /**
     * 下发 presentation 窗口的几何。
     *
     * 默认全屏；**钉住面板空闲态**（快速启动器面板钉住、手指已抬起、面板还停在屏上）缩到面板矩形 ——
     * 只有窗口不再盖住整屏，面板外的触摸才会落到下面的应用上，这就是「钉住还能操作屏幕」的实现。
     */
    private var pinnedWindowApplied = false

    /** 钉住面板的触摸层窗口（绘制窗口全屏不动，触摸只在这一块生效）。 */
    private var pinnedTouchSlot: CaptureWindow? = null

    /**
     * 下发 presentation 窗口的几何。
     *
     * ⚠️ **窗口几何永远是全屏**，钉住也不例外。
     *
     * 之前为了「面板外触摸穿透」把窗口缩到面板矩形，结果真机上每次缩放都会让面板闪到屏幕
     * 左上角再回来：面板走的是硬件合成层，窗口位置/尺寸一变，HWC 会短暂用「新位置 + 旧内容」
     * 合成一帧，画布怎么补都补不平（改平移量、改判据、用实时位置，全都试过）。
     * 现在穿透改由独立的「钉住触摸层」小窗口实现（见 [syncPinnedTouchWindow]），
     * 绘制窗口不再发生任何几何变化，这类闪跳从根上消失。
     */
    private fun applyPresentationWindowLayout(
        content: EdgeGestureOverlayView,
        params: WindowManager.LayoutParams
    ) {
        val pinned = content.pinnedQuickLauncherWindowRect() != null
        if (pinned != pinnedWindowApplied) {
            pinnedWindowApplied = pinned
            EdgeDiag.log("window", "presentation 钉住态=${pinned}（窗口保持全屏）")
        }
        content.setPresentationWindowGeometry(
            originX = 0,
            originY = 0,
            widthPx = content.logicalWidthPx(),
            heightPx = content.logicalHeightPx(),
            shrunk = false,
        )
        OverlayWindowTypes.applyFullScreen(params)
        OverlayWindowTypes.ensureNoBrightnessOverride(params)
        syncPinnedTouchWindow(content)
    }

    /**
     * 钉住面板的**触摸层**窗口。
     *
     * 绘制窗口全屏且 `FLAG_NOT_TOUCHABLE`（面板外触摸直接落到下面的应用）；这一层只有面板那么大、
     * 可触摸，收到的事件原样转发给绘制层（[EdgeGestureOverlayView.dispatchPinnedTouch]）。
     * 两层分工后：绘制窗口零几何变化（不闪），触摸仍精确限定在面板矩形内。
     */
    private fun syncPinnedTouchWindow(content: EdgeGestureOverlayView) {
        val rect = content.pinnedQuickLauncherWindowRect()
        val existing = pinnedTouchSlot
        if (rect == null) {
            if (existing != null) {
                removeOverlayView(existing.view)
                pinnedTouchSlot = null
                EdgeDiag.log("window", "钉住触摸层：摘除")
            }
            return
        }
        val bounds = CollapsedWindowBounds(
            widthPx = rect.width().toInt().coerceAtLeast(1),
            heightPx = rect.height().toInt().coerceAtLeast(1),
            xPx = rect.left.toInt(),
            yPx = rect.top.toInt(),
        )
        if (existing == null) {
            val params = createCaptureLayoutParams()
            applyPinnedTouchLayout(params, bounds)
            applyCaptureTouchFlags(params)
            val view = View(overlayContext).apply {
                setBackgroundColor(Color.TRANSPARENT)
                setOnTouchListener { _, event ->
                    content.dispatchPinnedTouch(event)
                    true
                }
            }
            runCatching { addOverlayView(view, params) }
                .onSuccess {
                    pinnedTouchSlot = CaptureWindow(view, params)
                    EdgeDiag.log("window", "钉住触摸层：挂载 rect=$rect")
                }
                .onFailure { Log.e(TAG, "Failed to add pinned touch window", it) }
            return
        }
        applyPinnedTouchLayout(existing.params, bounds)
        runCatching { windowManager.updateViewLayout(existing.view, existing.params) }
            .onFailure { Log.e(TAG, "Failed to update pinned touch window", it) }
    }

    /**
     * 钉住触摸层的布局：**必须用 TOP|START + 绝对屏幕坐标**。
     *
     * ⚠️ 不能复用 [applyCaptureLayout]：那个函数带 `gravity = windowGravity()`（右侧面板是
     * `TOP|END`），而 [CollapsedWindowBounds.xPx] 是"距屏幕左边"。两者一混，窗口就被摆到
     * 右边缘往里数 x 的位置，一块透明但可触摸的窗口落在屏幕中间/右侧，把那一片的点击滑动全吞掉
     *（真机表现：面板所在那一整条水平范围都点不动）。
     */
    private fun applyPinnedTouchLayout(
        params: WindowManager.LayoutParams,
        bounds: CollapsedWindowBounds
    ) {
        params.width = bounds.widthPx
        params.height = bounds.heightPx
        params.gravity = Gravity.TOP or Gravity.START
        params.x = bounds.xPx
        params.y = bounds.yPx
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        params.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }

    private fun applyCaptureLayout(
        params: WindowManager.LayoutParams,
        bounds: CollapsedWindowBounds
    ) {
        params.width = bounds.widthPx
        params.height = bounds.heightPx
        params.x = bounds.xPx
        params.y = bounds.yPx
        params.gravity = windowGravity()
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        params.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }

    private fun windowGravity(): Int = when (side) {
        PanelSide.LEFT -> Gravity.TOP or Gravity.START
        PanelSide.RIGHT -> Gravity.TOP or Gravity.END
        PanelSide.BOTTOM, PanelSide.TOP -> Gravity.TOP or Gravity.START
    }

    private fun createCaptureLayoutParams(): WindowManager.LayoutParams =
        OverlayWindowTypes.createCaptureParams(context, "XG:side-${side.name.lowercase()}")

    private fun applyCaptureTouchFlags(params: WindowManager.LayoutParams) {
        OverlayWindowTypes.applyCaptureTouchFlags(params)
    }

    private fun applyPresentationPassthroughFlags(params: WindowManager.LayoutParams) {
        OverlayWindowTypes.applyPresentationPassthroughFlags(params)
    }

    private fun applyPresentationInteractiveFlags(params: WindowManager.LayoutParams) {
        OverlayWindowTypes.applyPresentationInteractiveFlags(params)
    }

    fun markChromeBelowPanel() {
        chromeZOrderFront = false
        renderer.markChromeBelowPanel()
    }

    fun bringEdgeWindowsToFront(forceReAdd: Boolean = !chromeZOrderFront) {
        if (edgeOverlayDetached || overlayLayoutSuspended()) return
        if (OverlaySceneController.isEdgeGestureActive()) return
        touchCaptureWindows.forEach { slot ->
            bringWindowToFront(slot.view, slot.params, forceReAdd)
        }
        restoreCaptureTouchFlagsOnAllWindows()
        chromeZOrderFront = true
    }

    /** Re-apply touch flags after z-order remove/add; stale NOT_TOUCHABLE leaves chrome visible but dead. */
    private fun restoreCaptureTouchFlagsOnAllWindows() {
        if (touchCaptureWindows.isEmpty()) return
        val passthrough = shouldPassthroughCaptureTouches()
        touchCaptureWindows.forEach { slot ->
            if (passthrough) {
                applyPresentationPassthroughFlags(slot.params)
            } else {
                applyCaptureTouchFlags(slot.params)
            }
            if (slot.view.isAttachedToWindow) {
                runCatching { windowManager.updateViewLayout(slot.view, slot.params) }
                    .onFailure { Log.e(TAG, "Failed to restore capture touch flags", it) }
            }
        }
    }

    private fun shouldPassthroughCaptureTouches(): Boolean {
        if (capturePassthroughSuspended) return true
        val presentation = presentationView ?: return false
        return presentation.presentationShouldPassthroughTouches()
    }

    private var chromeZOrderFront = true

    private fun bringWindowToFront(
        view: View,
        params: WindowManager.LayoutParams,
        forceReAdd: Boolean
    ) {
        OverlayWindowTypes.ensureNoBrightnessOverride(params)
        if (!view.isAttachedToWindow) {
            if (!forceReAdd) return
            // 真机日志：这里直接 addView 会抛 "View ... has already been added to the window manager"
            // —— 窗口过渡瞬间 isAttachedToWindow 可能为 false，但 WM 里其实还留着这份 view。
            // 一抛异常，这批边缘/悬浮球窗口就被拆掉重建，用户看到它们"闪一下"。
            // 因此先尝试 updateViewLayout（已添加时生效且不动 z 序），失败再 addView。
            runCatching {
                windowManager.updateViewLayout(view, params)
                view.requestLayout()
                view.invalidate()
            }.recoverCatching {
                windowManager.addView(view, params)
                view.requestLayout()
                view.invalidate()
            }.onFailure { Log.e(TAG, "Failed to attach edge window", it) }
            return
        }
        val effectiveForceReAdd = forceReAdd && !OverlayDisplayRotationGate.suppressCaptureWindowReAdd
        if (effectiveForceReAdd) {
            // updateViewLayout 不会改变 z-order；与悬浮球一致，remove+add 才能压到取词/搜图面板之上。
            val startedAt = android.os.SystemClock.elapsedRealtime()
            runCatching {
                windowManager.removeView(view)
                windowManager.addView(view, params)
                view.requestLayout()
                view.invalidate()
            }.onFailure { Log.e(TAG, "Failed to forceReAdd edge window", it) }
            EdgeDiag.log(
                "zorder",
                "触钮窗口 remove+add: ${params.title} cost=${android.os.SystemClock.elapsedRealtime() - startedAt}ms"
            )
            return
        }
        runCatching {
            windowManager.updateViewLayout(view, params)
            view.requestLayout()
            view.invalidate()
        }.onFailure { Log.e(TAG, "Failed to update edge window layout", it) }
    }

    internal fun overlayLayoutSuspended(): Boolean =
        edgeOverlayDetached || OverlayTrampolineGuard.blocksOverlayPresentationTouch()

    internal data class CaptureWindow(
        val view: View,
        var params: WindowManager.LayoutParams
    )

    companion object {
        private const val TAG = "SideOverlayController"
    }
}

private fun CollapsedWindowBounds.isZeroTouchStrip(side: PanelSide): Boolean =
    widthPx <= 0 || heightPx <= 0
