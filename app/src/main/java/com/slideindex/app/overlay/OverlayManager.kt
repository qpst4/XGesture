package com.slideindex.app.overlay

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.util.Log
import com.slideindex.app.data.AppRepository
import com.slideindex.app.diagnostic.EdgeDiag
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.runtimeTriggerHandles
import com.slideindex.app.settings.triggerHandles
import com.slideindex.app.overlay.compositor.OverlayCompositor
import com.slideindex.app.overlay.compositor.OverlayScene
import com.slideindex.app.overlay.compositor.OverlaySceneController
import com.slideindex.app.util.TaskManagerUtil
import com.slideindex.app.util.TriggerVisibility
import com.slideindex.app.service.OverlayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** 输入层接管能力掩码位。 */
private fun PanelSide.forwardingBit(): Int = when (this) {
    PanelSide.LEFT -> 1 shl 0
    PanelSide.RIGHT -> 1 shl 1
    PanelSide.BOTTOM -> 1 shl 2
    PanelSide.TOP -> 1 shl 3
}

class OverlayManager(
    private val context: Context,
    private val appRepository: AppRepository,
    private val scope: CoroutineScope,
    private val onShellCommandsPersist: (List<com.slideindex.app.shell.ShellCommand>) -> Unit = {},
    private val onQuickLauncherPanelItemsPersist: (String, List<QuickLauncherItem>) -> Unit = { _, _ -> }
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var leftController: SideOverlayController? = null
    private var rightController: SideOverlayController? = null
    private var bottomController: SideOverlayController? = null
    private var topController: SideOverlayController? = null
    private var currentSettings: AppSettings = AppSettings()
    private var previewMode = false
    private var previewContent: LayoutPreviewContent = LayoutPreviewContent.TRIGGER_ONLY
    private var previewFocus: LayoutPreviewFocus? = null
    private var foregroundPackage: String? = null
    private var triggersSuppressed = false
    private var triggersShown = false

    /**
     * 可由 system_server 模块安全读取（volatile）的“该边可处理输入层转发触摸”位掩码。
     * 只在主线程更新，binder 线程只读。
     */
    @Volatile
    private var forwardingSideMask: Int = 0
    private val mainHandler = Handler(Looper.getMainLooper())
    private var refreshVisibilityPending = false

    /** 自愈巡检日志节流（前后台切换时这条路径可能每秒被调多次）。 */
    private var lastHealLogAtMs = 0L

    /** 启动期触钮补挂：是否已排了一次延迟重试、还剩几次额度。 */
    private var triggerAttachRetryPending = false
    private var triggerAttachRetriesLeft = 0

    fun applySettings(settings: AppSettings) {
        Log.d("XGStartup", "applySettings serviceEnabled=" + settings.serviceEnabled +
            " triggersShown=" + triggersShown + " suppressed=" + triggersSuppressed)
        currentSettings = settings
        if (!settings.serviceEnabled) {
            Log.d("XGStartup", "applySettings SKIP: serviceEnabled=false")
            clearAllOverlayBrightness()
            leftController?.destroy()
            rightController?.destroy()
            bottomController?.destroy()
            topController?.destroy()
            leftController = null
            rightController = null
            bottomController = null
            topController = null
            triggersShown = false
            triggersSuppressed = false
            forwardingSideMask = 0
            return
        }

        val metrics = OverlayScreenMetrics.snapshot(context)
        syncControllers(settings, metrics)
        if (settings.serviceEnabled) {
            clearAllOverlayBrightness()
        }
        val suppressRuntimeVisuals = previewMode && previewFocus != null
        leftController?.setRuntimeVisualsSuppressed(suppressRuntimeVisuals)
        rightController?.setRuntimeVisualsSuppressed(suppressRuntimeVisuals)
        bottomController?.setRuntimeVisualsSuppressed(suppressRuntimeVisuals)
        topController?.setRuntimeVisualsSuppressed(suppressRuntimeVisuals)
        recoverOverlaysIfIdle()
        ensureSideEdgesForHandles(settings, metrics)
        refreshTriggerVisibility()
        if (KeyboardTriggerImeState.imeVisible) {
            onKeyboardImeChanged()
        }
    }

    private fun ensureSideEdgesForHandles(
        settings: AppSettings,
        metrics: ScreenMetricsSnapshot = OverlayScreenMetrics.snapshot(context)
    ) {
        if (!settings.serviceEnabled || triggersSuppressed) {
            Log.d("XGStartup", "ensureSideEdges SKIP serviceEnabled=" + settings.serviceEnabled +
                " suppressed=" + triggersSuppressed)
            return
        }
        Log.d("XGStartup", "ensureSideEdges attach left=" +
            settings.runtimeTriggerHandles(PanelSide.LEFT, metrics.isLandscape).size +
            " right=" + settings.runtimeTriggerHandles(PanelSide.RIGHT, metrics.isLandscape).size +
            " bottom=" + settings.runtimeTriggerHandles(PanelSide.BOTTOM, metrics.isLandscape).size +
            " top=" + settings.runtimeTriggerHandles(PanelSide.TOP, metrics.isLandscape).size)
        ensureSideEdge(PanelSide.LEFT, leftController, settings, metrics)
        ensureSideEdge(PanelSide.RIGHT, rightController, settings, metrics)
        ensureSideEdge(PanelSide.BOTTOM, bottomController, settings, metrics)
        ensureSideEdge(PanelSide.TOP, topController, settings, metrics)
    }

    private fun ensureSideEdge(
        side: PanelSide,
        controller: SideOverlayController?,
        settings: AppSettings,
        metrics: ScreenMetricsSnapshot
    ) {
        if (controller == null) return
        val hasHandles = settings.runtimeTriggerHandles(side, metrics.isLandscape).isNotEmpty()
        if (!hasHandles) {
            // 没有触钮的侧平时不留画布；只有"面板专用宿主正被面板使用"时才保留
            //（角轮盘 / 悬浮球在这种侧弹出快速启动器、任务切换器就靠它）。
            //
            // ⚠️ 隐藏之前必须先把 controller 的 settings 刷新一遍：旋转 / 改配置后这一侧
            // 会被 hideEdge 反复收掉，而 hideEdge 不碰 settings。缓存停在**旧方向**的触钮列表时，
            // 外部触发（全局触控 / 角轮盘 / 悬浮球）会误判「这侧有触钮、画布应该自己回来」，
            // 于是拒绝挂面板专用画布 —— 表现是「这一侧怎么触发都打不开面板，重启应用才好」。
            controller.refreshSettingsCache(settings, metrics)
            if (controller.holdsPanelHostInUse()) return
            controller.hideEdge()
            return
        }
        if (triggersShown) {
            if (!controller.isEdgeInitialized()) {
                controller.showEdge(metrics)
            } else {
                controller.updateSettings(settings, metrics)
            }
        }
    }

    fun recoverOverlaysIfIdle() {
        if (!currentSettings.serviceEnabled) return
        leftController?.forceCollapseIfIdle()
        rightController?.forceCollapseIfIdle()
        bottomController?.forceCollapseIfIdle()
        topController?.forceCollapseIfIdle()
    }

    /**
     * 自愈：设置要求有触钮、当前也没被抑制，但某一边一个窗口都没挂上时，重新挂。
     *
     * 为什么需要它：触钮的挂载只发生在 `refreshTriggerVisibilityNow()` 里
     * `triggersShown` 由 false 变 true 的那一次；一旦那次被跳过（trampoline 守卫、
     * 进程被 lmk 杀掉后的半重建、事件丢失），`triggersShown` 会一直停在某个不一致的值上，
     * 于是悬浮球/角轮盘都出来了、触钮却永远不出现，只能靠用户手动开关恢复。
     *
     * 由周期巡检、环境变化与预览入口调用；只有真的重挂了才返回 true。
     */
    /** 自愈巡检的调试日志：同一条路径高频调用，统一节流，避免刷屏淹没真正的问题。 */
    private fun logHealThrottled(message: String) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastHealLogAtMs < HEAL_LOG_THROTTLE_MS) return
        lastHealLogAtMs = now
        Log.d("XGStartup", message)
    }

    fun healTriggerAttachments(
        metrics: ScreenMetricsSnapshot = OverlayScreenMetrics.snapshot(context)
    ): Boolean {
        if (!currentSettings.serviceEnabled) {
            OverlayStatePort.noteHealTick(skipped = true)
            logHealThrottled("heal SKIP serviceEnabled=false")
            return false
        }
        if (OverlayTrampolineGuard.blocksOverlayPresentationTouch()) {
            OverlayStatePort.noteHealTick(skipped = true)
            logHealThrottled("heal SKIP trampolineGuard")
            return false
        }
        if (shouldSuppressTrigger()) {
            OverlayStatePort.noteHealTick(skipped = true)
            logHealThrottled("heal SKIP suppressed")
            return false
        }

        val missing = missingTriggerSides(metrics)
        if (missing.isEmpty()) {
            OverlayStatePort.noteHealTick(skipped = false)
            logHealThrottled("heal nothing-missing")
            return false
        }

        Log.w(TAG, "触钮缺失，自愈重挂: $missing " +
            "(serviceEnabled=${currentSettings.serviceEnabled}, " +
            "triggersShown=$triggersShown, " +
            "triggersSuppressed=$triggersSuppressed, " +
            "foreground=${OverlayService.foregroundPackage})")
        triggersSuppressed = false
        triggersShown = true
        ensureSideEdgesForHandles(currentSettings, metrics)
        updateForwardingCapability()
        refreshTriggerVisibility()
        return true
    }

    /**
     * 硬复位所有侧边交互状态（是否 idle 都不管）。
     *
     * 熄屏/锁屏等硬边界调用：正常收尾依赖 UP/CANCEL，丢事件后
     * 全屏直触的 presentation 会一直吞触摸，必须无条件收回。
     */
    fun forceRecoverInteractionState() {
        leftController?.forceResetInteraction()
        rightController?.forceResetInteraction()
        bottomController?.forceResetInteraction()
        topController?.forceResetInteraction()
    }

    /** 路由输入层接管转发的触摸事件；返回 false 表示当前没有可处理该边的 overlay。 */
    fun handleForwardedTouch(side: PanelSide, event: android.view.MotionEvent): Boolean {
        val controller = controllerFor(side) ?: return false
        return controller.handleForwardedTouch(event)
    }

    fun cancelForwardedTouch(side: PanelSide) {
        controllerFor(side)?.cancelForwardedTouch()
    }

    private fun controllerFor(side: PanelSide): SideOverlayController? = when (side) {
        PanelSide.LEFT -> leftController
        PanelSide.RIGHT -> rightController
        PanelSide.BOTTOM -> bottomController
        PanelSide.TOP -> topController
    }

    fun updateForegroundPackage(packageName: String?) {
        if (foregroundPackage == packageName) return
        foregroundPackage = packageName
        scheduleRefreshTriggerVisibility()
        // 钉住面板的「仅在本应用」靠它决定自动隐藏 / 恢复；面板细节留在各侧宿主里，这里只做转发。
        leftController?.onForegroundPackageChanged(packageName)
        rightController?.onForegroundPackageChanged(packageName)
        bottomController?.onForegroundPackageChanged(packageName)
        topController?.onForegroundPackageChanged(packageName)
    }

    fun setPreviewMode(
        enabled: Boolean,
        content: LayoutPreviewContent = LayoutPreviewContent.TRIGGER_ONLY,
        focus: LayoutPreviewFocus? = null
    ) {
        previewMode = enabled
        previewContent = content
        previewFocus = if (enabled) focus else null
        val suppressRuntimeVisuals = enabled && focus != null
        leftController?.setRuntimeVisualsSuppressed(suppressRuntimeVisuals)
        rightController?.setRuntimeVisualsSuppressed(suppressRuntimeVisuals)
        bottomController?.setRuntimeVisualsSuppressed(suppressRuntimeVisuals)
        topController?.setRuntimeVisualsSuppressed(suppressRuntimeVisuals)
        applyPreviewToControllers()
        refreshTriggerVisibility()
    }

    private fun syncControllers(
        settings: AppSettings,
        metrics: ScreenMetricsSnapshot = OverlayScreenMetrics.snapshot(context)
    ) {
        if (leftController == null) {
            leftController = SideOverlayController(
                context = context,
                side = PanelSide.LEFT,
                windowManager = windowManager,
                appRepository = appRepository,
                scope = scope,
                clickPassthroughHandler = ::performClickPassthrough,
                onShellCommandsPersist = onShellCommandsPersist,
                onQuickLauncherPanelItemsPersist = onQuickLauncherPanelItemsPersist,
                onComposeOverlayDialogStateChanged = ::onComposeOverlayDialogStateChanged
            )
        }
        leftController?.updateSettings(settings, metrics)

        if (rightController == null) {
            rightController = SideOverlayController(
                context = context,
                side = PanelSide.RIGHT,
                windowManager = windowManager,
                appRepository = appRepository,
                scope = scope,
                clickPassthroughHandler = ::performClickPassthrough,
                onShellCommandsPersist = onShellCommandsPersist,
                onQuickLauncherPanelItemsPersist = onQuickLauncherPanelItemsPersist,
                onComposeOverlayDialogStateChanged = ::onComposeOverlayDialogStateChanged
            )
        }
        rightController?.updateSettings(settings, metrics)

        if (bottomController == null) {
            bottomController = SideOverlayController(
                context = context,
                side = PanelSide.BOTTOM,
                windowManager = windowManager,
                appRepository = appRepository,
                scope = scope,
                clickPassthroughHandler = ::performClickPassthrough,
                onShellCommandsPersist = onShellCommandsPersist,
                onQuickLauncherPanelItemsPersist = onQuickLauncherPanelItemsPersist,
                onComposeOverlayDialogStateChanged = ::onComposeOverlayDialogStateChanged
            )
        }
        bottomController?.updateSettings(settings, metrics)

        if (topController == null) {
            topController = SideOverlayController(
                context = context,
                side = PanelSide.TOP,
                windowManager = windowManager,
                appRepository = appRepository,
                scope = scope,
                clickPassthroughHandler = ::performClickPassthrough,
                onShellCommandsPersist = onShellCommandsPersist,
                onQuickLauncherPanelItemsPersist = onQuickLauncherPanelItemsPersist,
                onComposeOverlayDialogStateChanged = ::onComposeOverlayDialogStateChanged
            )
        }
        topController?.updateSettings(settings, metrics)

        applyPreviewToControllers()
    }

    private fun refreshTriggerVisibility() {
        refreshTriggerVisibilityNow()
    }

    private fun scheduleRefreshTriggerVisibility() {
        if (refreshVisibilityPending) return
        refreshVisibilityPending = true
        mainHandler.postDelayed({
            refreshVisibilityPending = false
            refreshTriggerVisibilityNow()
        }, REFRESH_VISIBILITY_DEBOUNCE_MS)
    }

    private fun refreshTriggerVisibilityNow() {
        if (!currentSettings.serviceEnabled) {
            forwardingSideMask = 0
            return
        }
        if (OverlayTrampolineGuard.blocksOverlayPresentationTouch()) {
            // 这里跳过挂载时若触钮还没挂上，必须留痕：否则"设置开着、屏幕上没有"
            // 会一直查不出是谁跳过的（历史故障就是这么来的）。
            if (!triggersShown) {
                Log.w(TAG, "触钮挂载被 trampoline 守卫跳过（triggersShown=false），等待自愈或环境变化重试")
            }
            forwardingSideMask = 0
            return
        }

        val suppress = shouldSuppressTrigger()
        if (suppress) {
            if (!triggersSuppressed) {
                leftController?.hideEdge()
                rightController?.hideEdge()
                bottomController?.hideEdge()
                topController?.hideEdge()
                triggersSuppressed = true
                triggersShown = false
            }
            // 被抑制期间不做补挂重试：等解除抑制时再走“首次显示”分支。
            triggerAttachRetriesLeft = 0
            forwardingSideMask = 0
            return
        }

        triggersSuppressed = false
        if (!triggersShown) {
            clearAllOverlayBrightness()
            TaskManagerUtil.ensureServiceBound()
            triggersShown = true
            // 每次“重新显示”给几次短重试额度：首次挂载可能被窗口过渡/环境抖动打断，
            // 不能只等 12 秒一次的自愈巡检（用户体感就是触钮晚好几秒才出现）。
            triggerAttachRetriesLeft = TRIGGER_ATTACH_RETRY_ATTEMPTS
            ensureSideEdgesForHandles(currentSettings)
        } else if (triggerAttachRetriesLeft > 0 && missingTriggerSides().isNotEmpty()) {
            // 上一轮已经把 triggersShown 置了 true，但窗口并没有真的建出来（attach 失败/被守卫打断）。
            ensureSideEdgesForHandles(currentSettings)
        }
        updateForwardingCapability()
        scheduleTriggerAttachRetryIfNeeded()
    }

    /** 配置了触钮、但窗口没真的挂上的边。触钮自愈与启动重试共用同一判据。 */
    private fun missingTriggerSides(
        metrics: ScreenMetricsSnapshot = OverlayScreenMetrics.snapshot(context)
    ): List<PanelSide> = listOf(
        PanelSide.LEFT to leftController,
        PanelSide.RIGHT to rightController,
        PanelSide.BOTTOM to bottomController,
        PanelSide.TOP to topController,
    ).filter { (side, controller) ->
        controller != null &&
            currentSettings.runtimeTriggerHandles(side, metrics.isLandscape).isNotEmpty() &&
            !controller.hasTriggerAttachments()
    }.map { it.first }

    /**
     * 启动期补挂：触钮“该显示但没挂上”时，立刻在几百毫秒后重试，而不是等 12 秒自愈。
     *
     * 只在“首次显示 / 状态刚翻成已显示”时给有限次数，避免和 attach 失败形成死循环。
     */
    private fun scheduleTriggerAttachRetryIfNeeded() {
        if (triggerAttachRetryPending || triggerAttachRetriesLeft <= 0) return
        if (missingTriggerSides().isEmpty()) return
        triggerAttachRetryPending = true
        triggerAttachRetriesLeft--
        Log.w(TAG, "触钮未挂齐，${TRIGGER_ATTACH_RETRY_MS}ms 后补挂（剩余 $triggerAttachRetriesLeft 次）")
        mainHandler.postDelayed({
            triggerAttachRetryPending = false
            refreshTriggerVisibilityNow()
        }, TRIGGER_ATTACH_RETRY_MS)
    }

    /** 输入层接管是否可用：服务开启、触钮未被隐藏、且该边 overlay 已就绪。 */
    fun isForwardingCapable(side: PanelSide): Boolean =
        (forwardingSideMask and side.forwardingBit()) != 0

    private fun updateForwardingCapability() {
        var mask = 0
        if (currentSettings.serviceEnabled && !triggersSuppressed) {
            if (leftController?.isEdgeInitialized() == true) {
                mask = mask or PanelSide.LEFT.forwardingBit()
            }
            if (rightController?.isEdgeInitialized() == true) {
                mask = mask or PanelSide.RIGHT.forwardingBit()
            }
            if (bottomController?.isEdgeInitialized() == true) {
                mask = mask or PanelSide.BOTTOM.forwardingBit()
            }
            if (topController?.isEdgeInitialized() == true) {
                mask = mask or PanelSide.TOP.forwardingBit()
            }
        }
        forwardingSideMask = mask
    }

    fun onEnvironmentChanged() {
        scheduleRefreshTriggerVisibility()
    }

    fun relayoutTriggersForConfigurationChange() {
        if (!currentSettings.serviceEnabled) return
        if (shouldSuppressTrigger()) {
            refreshTriggerVisibilityNow()
            return
        }
        recoverTriggerInteraction(forceReAddChrome = false)
    }

    fun onKeyboardImeChanged(visibilityChanged: Boolean = false) {
        if (!currentSettings.serviceEnabled) return
        if (shouldSuppressTrigger()) return
        // IME is inserted above chrome after a content panel raised triggers; re-add while it is visible.
        if (KeyboardTriggerImeState.imeVisible &&
            OverlaySceneController.scene is OverlayScene.ContentPanelVisible
        ) {
            // 真机修复：原来这里是 bringChromeAbovePanelsForce() + forceReAdd = true，
            // 其实现是**故意 remove + add** 整批边缘 chrome 与悬浮球，才能压到面板之上 ——
            // 但这会让触钮与悬浮球被拆掉重建（实测一次呼出：8 个窗口被移除、9 个新增，
            // 4 秒内 6 次属性变化、浮层窗口 ID 反复更换 → 肉眼"闪好几次"）。
            // 且该分支的条件 OverlayScene.ContentPanelVisible 是单例状态、会残留，故"有时闪有时不闪"。
            // 改为非强制：已挂上时走 updateViewLayout。
            FloatBallOverlay.bringChromeAbovePanels()
            bringEdgeChromeAbovePanels(forceReAdd = false)
        }
        leftController?.windowManager?.syncCaptureWindowLayout()
        rightController?.windowManager?.syncCaptureWindowLayout()
        bottomController?.windowManager?.syncCaptureWindowLayout()
        topController?.windowManager?.syncCaptureWindowLayout()
    }

    fun relayoutTriggersForDisplayRotation() {
        relayoutTriggersForConfigurationChange()
    }

    fun recoverTriggerInteraction(forceReAddChrome: Boolean = false) {
        if (!currentSettings.serviceEnabled) return
        recoverOverlaysIfIdle()
        resumeEdgeCapturesAfterPassthrough()
        val metrics = OverlayScreenMetrics.snapshot(context)
        // 先自愈"该有却没挂上"的边，再做 layout/chrome 收尾。
        healTriggerAttachments(metrics)
        ensureSideEdgesForHandles(currentSettings, metrics)
        bringEdgeChromeAbovePanels(forceReAdd = forceReAddChrome)
    }

    fun refreshTriggerVisuals() {
        if (!currentSettings.serviceEnabled) return
        val suppressRuntimeVisuals = previewMode && previewFocus != null
        leftController?.setRuntimeVisualsSuppressed(suppressRuntimeVisuals)
        rightController?.setRuntimeVisualsSuppressed(suppressRuntimeVisuals)
        bottomController?.setRuntimeVisualsSuppressed(suppressRuntimeVisuals)
        topController?.setRuntimeVisualsSuppressed(suppressRuntimeVisuals)
        leftController?.refreshTriggerVisualWindows()
        rightController?.refreshTriggerVisualWindows()
        bottomController?.refreshTriggerVisualWindows()
        topController?.refreshTriggerVisualWindows()
    }

    fun bringEdgeChromeAbovePanels(forceReAdd: Boolean = true, sides: Set<PanelSide>? = null) {
        if (!currentSettings.serviceEnabled) return
        val target = sides ?: PanelSide.entries.toSet()
        if (PanelSide.LEFT in target) leftController?.bringEdgeWindowsAbovePanels(forceReAdd)
        if (PanelSide.RIGHT in target) rightController?.bringEdgeWindowsAbovePanels(forceReAdd)
        if (PanelSide.BOTTOM in target) bottomController?.bringEdgeWindowsAbovePanels(forceReAdd)
        if (PanelSide.TOP in target) topController?.bringEdgeWindowsAbovePanels(forceReAdd)
    }

    fun edgePresentationNeedsChromeRaise(): Boolean {
        if (!currentSettings.serviceEnabled) return false
        return sequenceOf(leftController, rightController, bottomController, topController)
            .filterNotNull()
            .any { it.edgePresentationNeedsChromeRaise() }
    }

    /** 任一侧的音量/亮度条是否正在显示（悬浮球要让出触摸）。 */
    fun edgePresentationHasAdjustPanel(): Boolean {
        if (!currentSettings.serviceEnabled) return false
        return sequenceOf(leftController, rightController, bottomController, topController)
            .filterNotNull()
            .any { it.hasAdjustPanel() }
    }

    fun notifyEdgeChromeBelowPanel() {
        if (!currentSettings.serviceEnabled) return
        leftController?.markChromeBelowPanel()
        rightController?.markChromeBelowPanel()
        bottomController?.markChromeBelowPanel()
        topController?.markChromeBelowPanel()
    }

    private fun shouldSuppressTrigger(): Boolean {
        if (previewMode) return false
        return TriggerVisibility.shouldSuppress(
            settings = currentSettings,
            context = context,
            foregroundPackage = foregroundPackage
        )
    }

    private fun applyPreviewToControllers() {
        if (!currentSettings.serviceEnabled) return
        val content = previewContent
        val focus = previewFocus
        if (focus?.showPairedGroup == true && focus.side.isHorizontalEdge) {
            leftController?.setPreviewMode(
                enabled = previewMode,
                content = content,
                focus = focus.copy(side = PanelSide.LEFT)
            )
            rightController?.setPreviewMode(
                enabled = previewMode,
                content = content,
                focus = focus.copy(side = PanelSide.RIGHT)
            )
            return
        }
        leftController?.setPreviewMode(
            enabled = previewMode && (focus == null || focus.side == PanelSide.LEFT),
            content = content,
            focus = focus?.takeIf { it.side == PanelSide.LEFT }
        )
        rightController?.setPreviewMode(
            enabled = previewMode && (focus == null || focus.side == PanelSide.RIGHT),
            content = content,
            focus = focus?.takeIf { it.side == PanelSide.RIGHT }
        )
        bottomController?.setPreviewMode(
            enabled = previewMode && (focus == null || focus.side == PanelSide.BOTTOM),
            content = content,
            focus = focus?.takeIf { it.side == PanelSide.BOTTOM }
        )
        topController?.setPreviewMode(
            enabled = previewMode && (focus == null || focus.side == PanelSide.TOP),
            content = content,
            focus = focus?.takeIf { it.side == PanelSide.TOP }
        )
    }

    fun reloadApps() {
        scope.launch {
            appRepository.refreshApps()
        }
    }

    fun syncApps(apps: List<com.slideindex.app.data.AppInfo>) {
        leftController?.syncApps(apps)
        rightController?.syncApps(apps)
        bottomController?.syncApps(apps)
        topController?.syncApps(apps)
    }

    /** 强制重算各侧的索引列表（冰箱冻结/解冻后用，冻结项不靠可启动列表驱动）。 */
    fun refreshIndexApps() {
        leftController?.refreshIndexApps()
        rightController?.refreshIndexApps()
        bottomController?.refreshIndexApps()
        topController?.refreshIndexApps()
    }

    fun suspendAllEdgeOverlays() {
        leftController?.suspendEdgeOverlay()
        rightController?.suspendEdgeOverlay()
        bottomController?.suspendEdgeOverlay()
        topController?.suspendEdgeOverlay()
    }

    fun resumeAllEdgeOverlays() {
        if (OverlayTrampolineGuard.blocksOverlayResume()) return
        leftController?.resumeEdgeOverlay()
        rightController?.resumeEdgeOverlay()
        bottomController?.resumeEdgeOverlay()
        topController?.resumeEdgeOverlay()
    }

    /**
     * 截图前临时隐藏边缘触钮（只隐藏绘制，不摘窗口），截完必须用 [resumeCaptureVisuals] 恢复。
     * 与 [suspendAllEdgeOverlays] 的区别：那条路会把触钮窗一起摘掉，恢复依赖 trampoline 等状态，
     * 截图这种"几十毫秒"的短窗口用不起。
     */
    fun suppressCaptureVisuals() {
        leftController?.setCaptureVisualsSuppressed(true)
        rightController?.setCaptureVisualsSuppressed(true)
        bottomController?.setCaptureVisualsSuppressed(true)
        topController?.setCaptureVisualsSuppressed(true)
    }

    fun resumeCaptureVisuals() {
        leftController?.setCaptureVisualsSuppressed(false)
        rightController?.setCaptureVisualsSuppressed(false)
        bottomController?.setCaptureVisualsSuppressed(false)
        topController?.setCaptureVisualsSuppressed(false)
    }

    fun suspendEdgeCapturesForPassthrough() {
        leftController?.suspendCaptureForPassthrough()
        rightController?.suspendCaptureForPassthrough()
        bottomController?.suspendCaptureForPassthrough()
        topController?.suspendCaptureForPassthrough()
    }

    fun resumeEdgeCapturesAfterPassthrough() {
        leftController?.resumeCaptureAfterPassthrough()
        rightController?.resumeCaptureAfterPassthrough()
        bottomController?.resumeCaptureAfterPassthrough()
        topController?.resumeCaptureAfterPassthrough()
    }

    /**
     * 外部触发的边缘面板（快速启动器 / 任务切换器 / 索引 / 音量亮度条）。
     *
     * [panelSide] 是**用户触发的那一侧**。面板必须画在那一侧的浮层画布上，而画布平时只跟着触钮走：
     * 该侧**没有任何触钮**时它没有画布（[ensureSideEdge] → `hideEdge`）。这正是"一个触钮都不开时
     * 轮盘 / 悬浮球触发的面板不弹"的原因，所以这里在派发前先按需挂一扇面板专用宿主。
     *
     * [panelSide] 为 null 的入口（宏 / 全局快捷键等没有明确触发侧）沿用"谁的画布在就用谁"。
     */
    fun dispatchExternalGestureAction(
        action: com.slideindex.app.gesture.GestureAction,
        anchorRawY: Float,
        panelSide: PanelSide? = null
    ): Boolean {
        if (!currentSettings.serviceEnabled) return false
        refreshTriggerVisibility()
        val controller = resolvePanelHostController(panelSide) ?: return false
        if (!controller.prepareExternalGestureDispatch()) {
            EdgeDiag.log("panel", "外部触发面板失败：prepareExternalGestureDispatch 被拦 side=${panelSide}")
            return false
        }
        val view = controller.overlayPresentation ?: return false
        return view.dispatchExternalAction(action, anchorRawY)
    }

    private fun resolvePanelHostController(panelSide: PanelSide?): SideOverlayController? {
        if (panelSide == null) {
            return PANEL_HOST_FALLBACK_ORDER.asSequence()
                .mapNotNull { controllerFor(it) }
                .firstOrNull { it.overlayPresentation != null }
                ?: run {
                    EdgeDiag.log("panel", "外部触发面板失败：四侧都没有浮层画布（无触发侧）")
                    null
                }
        }
        val controller = controllerFor(panelSide)
        if (controller == null) {
            EdgeDiag.log("panel", "外部触发面板失败：该侧没有控制器 side=$panelSide")
            return null
        }
        if (controller.overlayPresentation != null) return controller
        // 该侧可能「设置改了 / 旋转后一直被隐藏」：controller 缓存的方向或触钮列表会过期。
        // 先按当前设置刷一遍缓存，再判断要不要挂宿主，否则会拿着旧方向的触钮列表拒绝挂载。
        val metrics = OverlayScreenMetrics.snapshot(context)
        controller.refreshSettingsCache(currentSettings, metrics)
        // 诊断：把「挂之前 / 挂之后」的窗口状态打出来。这一句是定位「同一侧反复挂不上」的唯一线索
        // （presentation / detached / suspended / captures / handles 五个量各自对应一种卡死原因）。
        val stateBefore = controller.panelHostDebugState()
        if (controller.ensurePanelHostForExternalPanel(metrics) &&
            controller.overlayPresentation != null
        ) {
            EdgeDiag.log(
                "panel",
                "外部触发面板：$panelSide 侧没有触钮 → 已按需挂面板专用宿主（before: $stateBefore）",
            )
            return controller
        }
        EdgeDiag.log(
            "panel",
            "外部触发面板失败：$panelSide 侧无触钮且面板专用宿主挂不上" +
                "（before: $stateBefore / after: ${controller.panelHostDebugState()}）",
        )
        return null
    }

    private fun onComposeOverlayDialogStateChanged() {
        if (OverlayTrampolineGuard.blocksOverlayPresentationTouch()) {
            suspendAllEdgeOverlays()
            return
        }
        val dialogOpen =
            leftController?.overlayPresentation?.presentationShouldPassthroughTouches() == true ||
                rightController?.overlayPresentation?.presentationShouldPassthroughTouches() == true ||
                bottomController?.overlayPresentation?.presentationShouldPassthroughTouches() == true ||
                topController?.overlayPresentation?.presentationShouldPassthroughTouches() == true
        if (dialogOpen) {
            leftController?.suspendCapturesForComposeDialog()
            rightController?.suspendCapturesForComposeDialog()
            bottomController?.suspendCapturesForComposeDialog()
            topController?.suspendCapturesForComposeDialog()
        } else {
            leftController?.resumeCapturesAfterComposeDialog()
            rightController?.resumeCapturesAfterComposeDialog()
            bottomController?.resumeCapturesAfterComposeDialog()
            topController?.resumeCapturesAfterComposeDialog()
        }
    }

    fun destroy() {
        mainHandler.removeCallbacksAndMessages(null)
        refreshVisibilityPending = false
        leftController?.destroy()
        rightController?.destroy()
        bottomController?.destroy()
        topController?.destroy()
        leftController = null
        rightController = null
        bottomController = null
        topController = null
        triggersShown = false
        triggersSuppressed = false
        forwardingSideMask = 0
    }

    private fun clearAllOverlayBrightness() {
        OverlayCompositor.clearBrightnessPreview()
        OverlayCompositor.detach()
        leftController?.clearOverlayWindowBrightness()
        rightController?.clearOverlayWindowBrightness()
        bottomController?.clearOverlayWindowBrightness()
        topController?.clearOverlayWindowBrightness()
    }

    private companion object {
        private const val REFRESH_VISIBILITY_DEBOUNCE_MS = 150L

        /** 自愈巡检日志的最小间隔：够看清趋势，又不会把 logcat 刷满。 */
        private const val HEAL_LOG_THROTTLE_MS = 5_000L

        /** 启动期触钮补挂：首次没挂上时多久重试一次、最多补几次。 */
        private const val TRIGGER_ATTACH_RETRY_MS = 300L
        private const val TRIGGER_ATTACH_RETRY_ATTEMPTS = 2
        private const val TAG = "OverlayManager"

        /** 没有明确触发侧时，按这个顺序找"有画布的那一侧"。 */
        private val PANEL_HOST_FALLBACK_ORDER = listOf(
            PanelSide.LEFT,
            PanelSide.RIGHT,
            PanelSide.BOTTOM,
            PanelSide.TOP,
        )
    }

    private fun performClickPassthrough(rawX: Float, rawY: Float, onComplete: () -> Unit) {
        // 旧实现在这里用「最近 1500ms 有过模块转发」整段跳过注入——接管区里的单击必然由模块转发，
        // 于是每次单击都被跳过（只有振动、点击不生效）。现在只拦**我们自己注入的回声**
        // （见 ModuleForwardedTouchGate），用户的新点击一律放行。
        EdgeDiag.log(
            "passthrough",
            "放行请求 at=($rawX,$rawY) injectionPending=${ModuleForwardedTouchGate.isInjectionPending()} " +
                "echo=${ModuleForwardedTouchGate.isInjectedEcho(rawX, rawY)}"
        )
        OverlayPassthrough.run(
            hideTriggers = ::suspendEdgeCapturesForPassthrough,
            showTriggers = ::resumeEdgeCapturesAfterPassthrough,
            rawX = rawX,
            rawY = rawY,
            onComplete = onComplete
        )
    }
}
