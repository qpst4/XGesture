package com.slideindex.app.overlay

import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import com.slideindex.app.data.AppRepository
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.triggerHandles
import com.slideindex.app.settings.withRuntimeLandscapeSettings
import com.slideindex.app.util.TriggerVisibility
import com.slideindex.app.overlay.animation.GestureAnimationOverlayRegistry
import com.slideindex.app.overlay.backpanel.BackPanelOverlayRegistry
import com.slideindex.app.overlay.compositor.OverlayCompositor
import com.slideindex.app.util.TaskManagerUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Dual-window edge overlay per side:
 * - [EdgeTouchCaptureView]: fixed edge strip, always attached (touch capture)
 * - [EdgeGestureOverlayView]: full-screen presentation, attached only while drawing / panels
 *
 * Idle state keeps **capture strips only** so the screen center is never blocked.
 */
class SideOverlayController(
    internal val context: android.content.Context,
    val side: PanelSide,
    windowManager: WindowManager,
    private val appRepository: AppRepository,
    private val scope: CoroutineScope,
    private val clickPassthroughHandler: ((Float, Float, () -> Unit) -> Unit)? = null,
    private val onShellCommandsPersist: (List<ShellCommand>) -> Unit = {},
    private val onQuickLauncherPanelItemsPersist: (String, List<QuickLauncherItem>) -> Unit = { _, _ -> },
    private val onComposeOverlayDialogStateChanged: () -> Unit = {}
) {
    internal val androidWindowManager = windowManager
    internal var settings: AppSettings = AppSettings()
    internal var screenWidthPx: Int = 0
    internal var screenHeightPx: Int = 0
    internal var previewMode = false
    private var previewContent: LayoutPreviewContent = LayoutPreviewContent.TRIGGER_ONLY
    private var previewFocus: LayoutPreviewFocus? = null
    private var lastIsLandscape: Boolean? = null

    internal fun isLandscapeLayout(): Boolean = lastIsLandscape == true

    internal val overlayContext = OverlayCompose.themedContext(context)
    internal val windowManager = SideOverlayWindowManager(this)
    internal val renderer = SideOverlayRenderer(this)

    internal val overlayPresentation: EdgeGestureOverlayView?
        get() = windowManager.presentationView

    /**
     * 面板专用宿主：该侧**没有触钮**时，为了承载面板而按需挂的一扇全屏画布。
     *
     * 面板（快速启动器 / 任务切换器 / 索引 / 音量亮度条）不是独立窗口，只能画在
     * [EdgeGestureOverlayView] 这块画布上；而画布平时跟着触钮走（没触钮 → [hideEdge]）。
     * 角轮盘 / 悬浮球在没有触钮的侧触发面板时，[ensurePanelHostForExternalPanel] 会把它置为 true，
     * 于是那几道"空触钮就拆窗"的守卫放行；面板收起后由 [OverlayManager.ensureSideEdge] 收回。
     *
     * 它只影响"能不能有画布"：捕获窗与触钮绘制仍然按触钮数量走（没触钮就是 0 个捕获窗、什么都不画）。
     */
    private var panelOnlyHostRequested = false

    private var loadJob: Job? = null

    /**
     * 索引里"冰箱成员"那一份（含冻结/挂起状态）。
     *
     * `appRepository.apps` 只含可启动应用，`syncApps` 用它覆盖列表时会把冻结项冲掉；
     * 这里留一份用于合并，保证冻结项在索引里稳定存在。
     */
    @Volatile
    private var indexAppsExtra: List<com.slideindex.app.data.AppInfo> = emptyList()

    /**
     * 索引里"分身"那一份（`cloneUserId > 0`）。
     *
     * 与 [indexAppsExtra] 分开存：分身和主实例**同包名**，按包名去重会把它当重复项丢掉；
     * `syncApps` 收到的是 `appRepository.apps`（只含主实例），必须把这份并回去，
     * 否则装/卸任意应用后分身条目会从索引里消失，直到下一次全量重算。
     */
    @Volatile
    private var cloneAppsExtra: List<com.slideindex.app.data.AppInfo> = emptyList()

    internal val density get() = context.resources.displayMetrics.density

    private fun effectiveSettings(
        source: AppSettings = settings,
        metrics: ScreenMetricsSnapshot = OverlayScreenMetrics.snapshot(context)
    ): AppSettings = source.withRuntimeLandscapeSettings(metrics.isLandscape)

    /**
     * 绘制用的权威屏幕尺寸：与触摸捕获窗同一个来源（服务上下文的真实屏幕，而不是浮层 WindowContext
     * 的窗口边界），保证「看得见的触钮」和「划得到的触钮」用的是同一把尺子。
     */
    internal fun authoritativeScreenSizePx(): Pair<Int, Int> {
        if (screenWidthPx > 0 && screenHeightPx > 0) return screenWidthPx to screenHeightPx
        val metrics = OverlayScreenMetrics.snapshot(context)
        return metrics.widthPx to metrics.heightPx
    }

    internal fun shouldShowRuntimeVisuals(): Boolean =
        !runtimeVisualsSuppressed && !captureVisualsSuppressed && !previewMode

    internal fun syncRuntimeVisuals() {
        if (shouldShowRuntimeVisuals()) {
            renderer.syncTriggerVisualWindows()
        } else {
            renderer.detachAllTriggerVisualWindows()
        }
    }

    /**
     * 此刻允许存在 presentation 吗。
     *
     * 常规是"这一侧有触钮"；没有触钮时只有面板专用宿主（见 [panelOnlyHostRequested]）才放行，
     * 否则这扇全屏窗没有任何用途，应该立刻收掉。
     */
    private fun canHoldPresentation(): Boolean =
        settings.triggerHandles(side).isNotEmpty() || panelOnlyHostRequested

    /** 面板专用宿主是否"真的在用"：请求过、且画布还在，并正在显示面板/手势。 */
    fun holdsPanelHostInUse(): Boolean =
        panelOnlyHostRequested &&
            overlayPresentation?.keepsOverlayExpanded() == true

    /**
     * 该侧没有触钮时，按需挂一扇只承载面板的画布。
     *
     * @return true 表示画布已就绪，调用方可以接着 [prepareExternalGestureDispatch]。
     */
    fun ensurePanelHostForExternalPanel(metrics: ScreenMetricsSnapshot? = null): Boolean {
        if (settings.triggerHandles(side).isNotEmpty()) return isEdgeInitialized()
        panelOnlyHostRequested = true
        if (overlayPresentation == null) {
            showEdge(metrics ?: OverlayScreenMetrics.snapshot(context))
        }
        return overlayPresentation != null
    }

    /**
     * 只刷新缓存的设置，不碰窗口。
     *
     * 用于「这一侧当前被隐藏」的场景（没触钮 / 横屏隐藏 / 排除应用）：窗口由 [hideEdge] 收掉，
     * 但 `settings` 必须跟着刷新。否则旋转或改配置后，controller 会一直拿着**旧方向**的触钮列表
     * ——外部触发（全局触控 / 角轮盘 / 悬浮球）会误判「这侧有触钮、画布应该自己回来」，
     * 于是拒绝挂面板专用画布，表现就是「这一侧怎么触发都打不开面板」。
     *
     * 真机案例：横屏时 left/right 各 2 个触钮，转回竖屏后这些触钮是关的；隐藏侧没有刷新缓存，
     * `ensurePanelHostForExternalPanel` 看到 2 个触钮直接 `return isEdgeInitialized()`（false），
     * 面板永远挂不上，直到应用重启。
     */
    fun refreshSettingsCache(newSettings: AppSettings, metrics: ScreenMetricsSnapshot) {
        settings = effectiveSettings(newSettings, metrics)
        lastIsLandscape = metrics.isLandscape
        screenWidthPx = metrics.widthPx
        screenHeightPx = metrics.heightPx
    }

    /**
     * 「面板专用宿主」挂不上时的现场，只给 [OverlayManager] 的诊断日志用。
     *
     * 几个量分别对应一种卡死原因：
     * - `presentation`：画布对象在不在（不在说明视图被收掉了）；
     * - `detached`：该侧是否被 suspend 过且没恢复（`showEdge` 会在 suspended 时直接返回）；
     * - `suspended`：上面两项 + trampoline 守卫的合并判断；
     * - `captures`：残留的捕获窗数量（非空但 presentation=false 时 showEdge 会只走"补挂"分支）；
     * - `handles`：controller 眼里该侧还有几个触钮（非 0 会让 ensure 直接返回 false）；
     * - `landscape`：缓存里的设置是按哪个方向算的（和屏幕方向不一致就是缓存过期）。
     */
    internal fun panelHostDebugState(): String =
        "presentation=${overlayPresentation != null} " +
            "detached=${windowManager.edgeOverlayDetached} " +
            "suspended=${windowManager.overlayLayoutSuspended()} " +
            "captures=${windowManager.touchCaptureWindows.size} " +
            "handles=${settings.triggerHandles(side).size} " +
            "landscape=${lastIsLandscape} " +
            "panelOnly=$panelOnlyHostRequested"

    fun updateSettings(newSettings: AppSettings, metrics: ScreenMetricsSnapshot? = null) {
        val resolvedMetrics = metrics ?: OverlayScreenMetrics.snapshot(context)
        val effective = effectiveSettings(newSettings, resolvedMetrics)
        val hiddenChanged = effective.hiddenAppPackages != settings.hiddenAppPackages
        settings = effective
        if (!canHoldPresentation()) {
            hideEdge()
            return
        }
        val isLandscape = resolvedMetrics.isLandscape
        lastIsLandscape = isLandscape

        if (!isEdgeInitialized()) {
            showEdge(resolvedMetrics)
            return
        }

        screenWidthPx = resolvedMetrics.widthPx
        screenHeightPx = resolvedMetrics.heightPx
        windowManager.presentationView?.applySettings(settings, resolvedMetrics.widthPx)
        if (windowManager.presentationView != null) {
            preloadApps(force = hiddenChanged)
        }
        windowManager.syncCaptureWindowLayout()
        windowManager.presentationContainer?.let { container ->
            attachHintOverlays(container)
        }
        if (windowManager.edgeOverlayDetached) return
        syncRuntimeVisuals()
        if (previewMode) {
            val presentation = windowManager.presentationView
            if (presentation != null && presentation.isPreviewMode()) {
                windowManager.ensurePresentationAttached()
                presentation.setPreviewMode(true, previewContent, previewFocus)
                renderer.applyPreviewPresentationWindow()
                presentation.invalidate()
            } else {
                windowManager.detachPresentationUnlessRequired()
            }
        } else {
            windowManager.detachPresentationUnlessRequired()
        }
    }

    fun updateSettings(newSettings: AppSettings, screenWidth: Int) {
        updateSettings(newSettings, null)
    }

    fun isEdgeInitialized(): Boolean = windowManager.presentationView != null

    /**
     * 触钮是否"真的挂上了"。
     *
     * presentation 存在只说明宿主在，还要有触摸捕获窗才算能用——两者不齐时
     * 用户看到的就是"设置里开着、屏幕上没有"，需要自愈重挂。
     */
    fun hasTriggerAttachments(): Boolean =
        windowManager.presentationView != null && windowManager.touchCaptureWindows.isNotEmpty()

    /** 输入层接管转发的触摸事件；窗口未就绪时返回 false（模块会据此放行）。 */
    fun handleForwardedTouch(event: android.view.MotionEvent): Boolean {
        val view = windowManager.presentationView ?: return false
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            return false
        }
        return view.handleForwardedTouch(event)
    }

    fun cancelForwardedTouch() {
        windowManager.presentationView?.cancelForwardedTouch()
    }

    fun forceCollapseIfIdle() {
        val view = windowManager.presentationView ?: return
        // leave-open 面板抬手后 active=false，但 panelMode 仍非 NONE，不能当 idle 清掉。
        if (view.isSessionActive() || view.keepsOverlayExpanded() || previewMode) return
        view.forceRecoverInteractionState()
        windowManager.detachPresentationUnlessRequired()
    }

    /**
     * 硬复位：无视 idle 判定，强制结束本侧交互会话并收回全屏直触。
     *
     * 用于熄屏/锁屏这类硬边界——正常收尾依赖 UP/CANCEL，一旦丢事件，
     * presentation 会一直保持可触摸并吞掉整屏触摸。
     */
    fun forceResetInteraction() {
        val view = windowManager.presentationView ?: return
        view.forceRecoverInteractionState()
        windowManager.syncPresentationTouchState()
        windowManager.syncCaptureWindowLayout()
        windowManager.detachPresentationUnlessRequired()
    }

    internal var runtimeVisualsSuppressed = false
        private set

    /**
     * 截图期的临时压制（只隐藏触钮的绘制，不动窗口）。
     *
     * 和 [runtimeVisualsSuppressed] 分开记：那个是应用级显隐（预览、隐藏应用等），
     * 截图恢复时只能清自己这一位，否则会把别人压制的触钮误显出来。
     */
    private var captureVisualsSuppressed = false

    fun setCaptureVisualsSuppressed(suppressed: Boolean) {
        if (captureVisualsSuppressed == suppressed) return
        captureVisualsSuppressed = suppressed
        syncRuntimeVisuals()
    }

    fun setRuntimeVisualsSuppressed(suppressed: Boolean) {
        runtimeVisualsSuppressed = suppressed
        if (suppressed) {
            renderer.detachAllTriggerVisualWindows()
        } else if (!previewMode) {
            syncRuntimeVisuals()
        }
    }

    /**
     * 前台应用变化（由 [OverlayManager] 转发）。
     *
     * 目前只有「钉住 + 仅在本应用」的快速启动器面板关心：切走自动隐藏、切回归属应用原样恢复。
     */
    fun onForegroundPackageChanged(packageName: String?) {
        windowManager.presentationView?.onForegroundPackageChanged(packageName)
    }

    fun setPreviewMode(
        enabled: Boolean,
        content: LayoutPreviewContent = LayoutPreviewContent.TRIGGER_ONLY,
        focus: LayoutPreviewFocus? = null
    ) {
        previewMode = enabled
        previewContent = content
        previewFocus = focus
        syncRuntimeVisuals()
        if (enabled && windowManager.presentationView == null && windowManager.touchCaptureWindows.isNotEmpty()) {
            windowManager.ensurePresentationAttached()
        }
        val view = windowManager.presentationView
        if (view != null) {
            if (enabled && content == LayoutPreviewContent.INDEX_ONLY) {
                val cached = appRepository.getCachedApps()
                    .filter { com.slideindex.app.launcher.AppTarget.encode(it.packageName, it.cloneUserId) !in settings.hiddenAppPackages }
                if (cached.isNotEmpty()) {
                    view.setApps(cached)
                }
            }
            view.setPreviewMode(enabled, content, focus)
            if (enabled) {
                windowManager.ensurePresentationAttached()
                renderer.applyPreviewPresentationWindow()
            } else if (!runtimeVisualsSuppressed) {
                windowManager.detachPresentationUnlessRequired()
            }
        } else if (enabled) {
            windowManager.ensurePresentationAttached()
            windowManager.presentationView?.let { presentation ->
                if (content == LayoutPreviewContent.INDEX_ONLY) {
                    val cached = appRepository.getCachedApps()
                        .filter { com.slideindex.app.launcher.AppTarget.encode(it.packageName, it.cloneUserId) !in settings.hiddenAppPackages }
                    if (cached.isNotEmpty()) {
                        presentation.setApps(cached)
                    }
                }
                presentation.setPreviewMode(enabled, content, focus)
                renderer.applyPreviewPresentationWindow()
            }
        }
    }

    fun showEdge(metrics: ScreenMetricsSnapshot? = null) {
        val resolvedMetrics = metrics ?: OverlayScreenMetrics.snapshot(context)
        val isLandscape = resolvedMetrics.isLandscape
        lastIsLandscape = isLandscape

        screenWidthPx = resolvedMetrics.widthPx
        screenHeightPx = resolvedMetrics.heightPx
        if (!canHoldPresentation()) {
            hideEdge()
            return
        }
        if (windowManager.overlayLayoutSuspended()) {
            windowManager.presentationView?.let { presentation ->
                windowManager.syncCaptureWindows(presentation, forceLayout = true)
                syncRuntimeVisuals()
            }
            return
        }
        windowManager.clearOverlayWindowBrightness()
        val existingPresentation = windowManager.presentationView
        if (existingPresentation != null) {
            if (!canHoldPresentation()) {
                hideEdge()
                return
            }
            if (windowManager.touchCaptureWindows.isEmpty()) {
                windowManager.reattachCaptureWindows()
            }
            existingPresentation.applySettings(settings, screenWidthPx)
            windowManager.presentationContainer?.let { container ->
                attachHintOverlays(container)
            }
            windowManager.syncCaptureWindows(existingPresentation)
            windowManager.detachPresentationUnlessRequired()
            syncRuntimeVisuals()
            if (previewMode) {
                existingPresentation.setPreviewMode(true, previewContent, previewFocus)
                windowManager.ensurePresentationAttached()
                renderer.applyPreviewPresentationWindow()
                existingPresentation.invalidate()
            }
            return
        }
        if (windowManager.touchCaptureWindows.isNotEmpty()) {
            if (previewMode) {
                windowManager.ensurePresentationAttached()
                windowManager.presentationView?.setPreviewMode(true, previewContent, previewFocus)
                renderer.applyPreviewPresentationWindow()
            }
            windowManager.presentationView?.let { presentation ->
                presentation.applySettings(settings, screenWidthPx)
                windowManager.presentationContainer?.let { container ->
                    attachHintOverlays(container)
                }
                windowManager.syncCaptureWindows(presentation)
                windowManager.detachPresentationUnlessRequired()
            }
            syncRuntimeVisuals()
            if (previewMode) {
                windowManager.presentationView?.invalidate()
            }
            return
        }

        val container = FrameLayout(overlayContext)
        val presentation = EdgeGestureOverlayView(
            context = overlayContext,
            side = side,
            appRepository = appRepository,
            onSessionStartCallback = {
                windowManager.presentationView?.setPreviewMode(false)
                windowManager.ensurePresentationAttached()
                windowManager.syncPresentationTouchState()
                windowManager.syncCaptureWindowLayout()
                if (FloatBallOverlay.isShowing &&
                    windowManager.presentationView?.needsChromeRaisedAbovePresentation() == true
                ) {
                    FloatBallOverlay.notifyPanelAttachedAboveChrome(edgeSide = side)
                }
            },
            onSessionEndCallback = {
                windowManager.clearOverlayWindowBrightness()
                if (windowManager.presentationView?.keepsOverlayExpanded() != true &&
                    windowManager.presentationView?.isSessionActive() != true
                ) {
                    if (previewMode) {
                        windowManager.presentationView?.setPreviewMode(true, previewContent, previewFocus)
                        windowManager.ensurePresentationAttached()
                        renderer.applyPreviewPresentationWindow()
                    } else {
                        windowManager.presentationView?.forceRecoverInteractionState()
                        windowManager.detachPresentationUnlessRequired()
                    }
                } else {
                    windowManager.syncPresentationTouchState()
                }
                if (!EdgeContinuedOverlayHandoff.active) {
                    windowManager.syncCaptureWindowLayout()
                }
            },
            onGestureTrackingStartCallback = {
                if (previewMode) {
                    windowManager.presentationView?.setPreviewMode(false)
                }
                windowManager.syncPresentationTouchState()
                windowManager.presentationContainer?.let { container ->
                    attachHintOverlays(container)
                }
                TaskManagerUtil.ensureServiceBound()
            },
            onAdjustPanelLayoutCallback = { _ ->
                windowManager.presentationView?.setPreviewMode(false)
                windowManager.presentationView?.applyAdjustPanelOverlayLayout()
                windowManager.ensurePresentationAttached()
                windowManager.syncPresentationTouchState()
                if (FloatBallOverlay.isShowing) {
                    FloatBallOverlay.notifyPanelAttachedAboveChrome(edgeSide = side)
                }
            },
            onAdjustPanelDismissCallback = {
                windowManager.clearOverlayWindowBrightness()
                if (previewMode) {
                    windowManager.presentationView?.setPreviewMode(true, previewContent, previewFocus)
                    windowManager.ensurePresentationAttached()
                    renderer.applyPreviewPresentationWindow()
                } else {
                    windowManager.syncPresentationTouchState()
                    windowManager.detachPresentationUnlessRequired()
                }
                // 条已收起（状态先清空、这个回调后到）：把球的触摸收回来。
                if (FloatBallOverlay.isShowing) {
                    FloatBallOverlay.syncTouchForPanelState()
                }
            },
            onClickPassthroughCallback = { rawX, rawY, onComplete ->
                val handler = clickPassthroughHandler
                if (handler != null) {
                    handler(rawX, rawY, onComplete)
                } else {
                    onComplete()
                }
            },
            onShellCommandsPersist = onShellCommandsPersist,
            onQuickLauncherPanelItemsPersist = onQuickLauncherPanelItemsPersist,
            onShellPanelFocusChange = { focusable -> windowManager.setPresentationFocusable(focusable) },
            onOverlayWindowSuspend = { suspendEdgeOverlay() },
            onOverlayWindowResume = { resumeEdgeOverlay() },
            onOverlayPresentationSuspend = { suspendPresentationForShellPanelActivity() },
            onOverlayPresentationResume = { resumePresentationIfNeeded() },
            onShellPanelAuxiliaryPrepare = { suspendEdgeOverlay() },
            onShellPanelAuxiliaryDismiss = { resumeEdgeOverlay() },
            screenSizeProvider = { authoritativeScreenSizePx() },
            overlayBrightness = null
        ).also { view ->
            view.onPresentationTouchRequirementChanged = {
                if (!windowManager.edgeOverlayDetached && !windowManager.overlayLayoutSuspended()) {
                    if (view.needsPresentationDirectTouch() && !view.presentationShouldPassthroughTouches()) {
                        windowManager.ensurePresentationAttached()
                    }
                    windowManager.syncPresentationTouchState()
                    windowManager.syncCaptureWindowLayout()
                    if (view.presentationShouldPassthroughTouches()) {
                        view.syncOverlayDialogZOrder()
                    }
                    windowManager.detachPresentationUnlessRequired()
                }
                // 音量条出现/收起时球要让出/收回触摸（手势进行中由松手那一拍同步）。
                if (FloatBallOverlay.isShowing) {
                    FloatBallOverlay.syncTouchForPanelState()
                }
                onComposeOverlayDialogStateChanged()
            }
        }

        container.addView(
            presentation,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        attachHintOverlays(container)

        val params = windowManager.createPresentationLayoutParams().apply {
            OverlayWindowTypes.ensureNoBrightnessOverride(this)
        }
        OverlayWindowTypes.applyFullScreen(params)
        presentation.applySettings(settings, screenWidthPx)
        presentation.applyExpandedOverlayLayout()

        runCatching {
            windowManager.attachCaptureWindows(presentation)
            windowManager.presentationView = presentation
            windowManager.presentationContainer = container
            windowManager.presentationParams = params
            windowManager.detachPresentationUnlessRequired()
            TaskManagerUtil.ensureServiceBound()
            preloadApps()
            if (previewMode) {
                presentation.setPreviewMode(true, previewContent, previewFocus)
                windowManager.ensurePresentationAttached()
                renderer.applyPreviewPresentationWindow()
            }
        }.onFailure {
            Log.e(TAG, "Failed to show overlay", it)
            GestureAnimationOverlayRegistry.controller(side).detach()
            BackPanelOverlayRegistry.controller(side).detach()
            windowManager.detachAllCaptureWindows()
            windowManager.presentationView = null
            windowManager.presentationContainer = null
            windowManager.presentationParams = null
            windowManager.presentationAttached = false
        }
    }

    fun clearOverlayWindowBrightness() {
        windowManager.clearOverlayWindowBrightness()
    }

    fun hideEdge() {
        windowManager.clearOverlayWindowBrightness()
        windowManager.detachPresentationWindow()
        windowManager.detachAllCaptureWindows()
        GestureAnimationOverlayRegistry.controller(side).detach()
        BackPanelOverlayRegistry.controller(side).detach()
        OverlayCompositor.detach()
        windowManager.presentationView = null
        windowManager.presentationContainer = null
        windowManager.presentationParams = null
        windowManager.presentationAttached = false
        windowManager.edgeOverlayDetached = false
        // 面板专用宿主也一并释放：下次要用再按需挂。
        panelOnlyHostRequested = false
    }

    fun suspendCaptureForPassthrough() {
        windowManager.suspendCaptureTouchForPassthrough()
    }

    fun resumeCaptureAfterPassthrough() {
        windowManager.resumeCaptureTouchAfterPassthrough()
    }

    fun suspendEdgeOverlay() {
        setRuntimeVisualsSuppressed(true)
        windowManager.suspendEdgeOverlay()
    }

    /** Detach presentation only so a shell-panel Activity stays visible while edge capture remains. */
    fun suspendPresentationForShellPanelActivity() {
        windowManager.clearOverlayWindowBrightness()
        windowManager.detachPresentationWindow()
    }

    fun resumePresentationIfNeeded() {
        if (windowManager.edgeOverlayDetached || windowManager.overlayLayoutSuspended()) return
        val view = windowManager.presentationView ?: return
        if (previewMode || view.isSessionActive() || view.keepsOverlayExpanded()) {
            windowManager.ensurePresentationAttached()
        } else {
            windowManager.detachPresentationUnlessRequired()
        }
        windowManager.syncPresentationTouchState()
    }

    fun resumeEdgeOverlay() {
        if (OverlayTrampolineGuard.blocksOverlayResume()) return
        windowManager.resumeEdgeOverlay()
        setRuntimeVisualsSuppressed(false)
    }

    fun suspendCapturesForComposeDialog() {
        if (windowManager.edgeOverlayDetached || windowManager.overlayLayoutSuspended()) return
        windowManager.detachTouchCaptureViewsOnly()
        windowManager.presentationView?.syncOverlayDialogZOrder()
    }

    fun resumeCapturesAfterComposeDialog() {
        if (windowManager.edgeOverlayDetached || windowManager.overlayLayoutSuspended()) return
        windowManager.syncCaptureWindowLayout()
        syncRuntimeVisuals()
    }

    fun reloadApps() {
        scope.launch {
            appRepository.refreshApps()
        }
    }

    fun syncApps(apps: List<com.slideindex.app.data.AppInfo>) {
        if (windowManager.edgeOverlayDetached || windowManager.overlayLayoutSuspended()) return
        // 可启动列表不含被冻结的应用；把索引里缓存的那部分并回来，避免冻结项被整份刷掉。
        val merged = mergeIndexExtras(apps)
        val filtered = merged.filter { com.slideindex.app.launcher.AppTarget.encode(it.packageName, it.cloneUserId) !in settings.hiddenAppPackages }
        windowManager.presentationView?.setApps(filtered)
    }

    /** 冰箱冻结/解冻后强制重算索引（冻结项不由可启动列表驱动）。 */
    fun refreshIndexApps() {
        preloadApps(force = true)
    }

    private fun mergeIndexExtras(apps: List<com.slideindex.app.data.AppInfo>): List<com.slideindex.app.data.AppInfo> {
        if (indexAppsExtra.isEmpty() && cloneAppsExtra.isEmpty()) return apps
        val present = apps.mapTo(HashSet()) { it.indexIdentityKey() }
        // 两个来源可能都含同一个分身（冰箱目标 + `enrich`），先按身份去重再并。
        val extras = (indexAppsExtra + cloneAppsExtra)
            .distinctByIdentity()
            .filter { it.indexIdentityKey() !in present }
        if (extras.isEmpty()) return apps
        return (apps + extras).sortedBy { it.pinyinKey }
    }

    /** 索引里的唯一身份：分身与主实例同包名，必须带上 userId 才能区分。 */
    private fun com.slideindex.app.data.AppInfo.indexIdentityKey(): Pair<String, Int> =
        packageName to cloneUserId

    private fun List<com.slideindex.app.data.AppInfo>.distinctByIdentity():
        List<com.slideindex.app.data.AppInfo> = distinctBy { it.indexIdentityKey() }

    fun refreshTriggerVisualWindows() {
        resyncCaptureInteraction()
    }

    fun resyncCaptureInteraction() {
        if (settings.triggerHandles(side).isEmpty()) return
        if (windowManager.edgeOverlayDetached || windowManager.overlayLayoutSuspended()) return
        resumeCaptureAfterPassthrough()
        windowManager.syncCaptureWindowLayout()
    }

    fun bringEdgeWindowsAbovePanels(forceReAdd: Boolean = true) {
        if (windowManager.edgeOverlayDetached ||
            windowManager.overlayLayoutSuspended()
        ) return
        windowManager.bringEdgeWindowsToFront(forceReAdd)
        windowManager.syncCaptureWindowLayout()
    }

    fun edgePresentationNeedsChromeRaise(): Boolean = windowManager.needsChromeRaisedAbovePresentation()

    /**
     * 音量/亮度条是否正在这一侧的 presentation 上显示**且还要接触摸**。
     *
     * 退场动画期间条已经立刻让出触摸（见 `AdjustPanelOverlayController.dismissAdjustPanel`），
     * 这里同步返回 false，球的触摸也就同一拍收回来。窗口摘掉/挂起时同样返回 false，
     * 保证悬浮球不会因为一个残留状态一直处于不可点。
     */
    fun hasAdjustPanel(): Boolean {
        if (windowManager.edgeOverlayDetached || windowManager.overlayLayoutSuspended()) return false
        val view = overlayPresentation ?: return false
        return view.hasAdjustPanel() && !view.isAdjustPanelDismissing()
    }

    fun markChromeBelowPanel() {
        windowManager.markChromeBelowPanel()
    }

    /**
     * Attaches the full-screen presentation window before shake / other external panel triggers.
     * Idle edge overlays keep only capture strips; without this, panel commands run on an
     * in-memory [EdgeGestureOverlayView] that is not on screen.
     */
    fun prepareExternalGestureDispatch(): Boolean {
        if (OverlayTrampolineGuard.blocksOverlayResume()) return false
        if (windowManager.edgeOverlayDetached) {
            resumeEdgeOverlay()
        }
        val view = windowManager.presentationView ?: return false
        view.setPreviewMode(false)
        view.applyExpandedOverlayLayout()
        windowManager.ensurePresentationAttached(forceWhenIdle = true)
        windowManager.syncPresentationTouchState()
        windowManager.syncCaptureWindowLayout()
        return true
    }

    fun destroy() {
        loadJob?.cancel()
        hideEdge()
    }

    private fun preloadApps(force: Boolean = false) {
        if (!force) {
            val cached = mergeIndexExtras(appRepository.getCachedApps())
                .filter { com.slideindex.app.launcher.AppTarget.encode(it.packageName, it.cloneUserId) !in settings.hiddenAppPackages }
            if (cached.isNotEmpty()) {
                windowManager.presentationView?.setApps(cached)
            }
        }
        loadJob?.cancel()
        loadJob = scope.launch {
            // 应用索引要把冰箱里被冻结/挂起的应用也列出来（冻结的查不到 launcher intent）。
            val base = appRepository.loadIndexApps(
                freezerPackages = settings.freezerAppPackages,
                hiddenPackages = settings.hiddenAppPackages,
            )
            // 再补上"分身"条目（其他 user 下安装的同一批包，同图标 + 名字后缀）。
            // 枚举可能走特权 shell / LauncherApps（可能阻塞几十毫秒），放到 IO 线程，别卡主线程。
            val apps = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                CloneAppsIndex.enrich(base) { label, kind ->
                    context.getString(CloneAppsIndex.labelRes(kind), label)
                }
            }.distinctByIdentity()
            // 记住"冰箱成员"和"分身"各一份，供 syncApps 合并，避免被可启动列表覆盖。
            indexAppsExtra = apps.filter { !it.isClone && it.packageName in settings.freezerAppPackages }
            cloneAppsExtra = apps.filter { it.isClone }
            if (windowManager.edgeOverlayDetached || windowManager.overlayLayoutSuspended()) return@launch
            windowManager.presentationView?.setApps(apps)
        }
    }

    /** 当前手势动画 overlay 所属的无障碍服务代次；变化时必须丢掉旧 Compose 组合再重挂。 */
    private var hintOverlayGeneration = -1

    private fun attachHintOverlays(container: FrameLayout) {
        val generation = com.slideindex.app.service.SlideIndexAccessibilityService
            .overlayHostGeneration()
        if (hintOverlayGeneration != generation) {
            // 系统摘掉无障碍连接时会把它那代窗口整片删掉，但动画的 Compose 组合可能还活着：
            // 表现就是 onDrag/snapTo 日志照打、屏幕上什么都不画。代次一变先 detach，
            // 下一行 attach 会拿新容器 + 新 context 重建组合。
            runCatching { GestureAnimationOverlayRegistry.controller(side).detach() }
            runCatching { BackPanelOverlayRegistry.controller(side).detach() }
            hintOverlayGeneration = generation
        }
        GestureAnimationOverlayRegistry.controller(side).attach(container, overlayContext)
        GestureAnimationOverlayRegistry.controller(side).applySettings(settings)
        val back = BackPanelOverlayRegistry.controller(side)
        back.attach(container, overlayContext)
        back.applySettings(settings)
        back.onSettled = { windowManager.detachPresentationUnlessRequired() }
    }

    companion object {
        private const val TAG = "SideOverlayController"
    }
}
