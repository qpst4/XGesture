package com.slideindex.app.overlay

/*
 * Portions derived from SideGesture (https://github.com/aaronzzx/gulugulu)
 * Licensed under Apache-2.0. Modified for com.slideindex.app.
 */

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import com.slideindex.app.di.AppDependencies
import com.slideindex.app.diagnostic.EdgeDiag
import com.slideindex.app.gesture.GestureAnglesPreviewStore
import com.slideindex.app.monitoring.OverlayPerformanceMonitorBinding
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.GestureProfileCatalog
import com.slideindex.app.settings.withActiveGestureProfile
import android.util.Log
import com.slideindex.app.overlay.corner.CornerGestureHost
import com.slideindex.app.overlay.dragdrop.DragDropFlowController
import com.slideindex.app.service.AccessibilityRecoverNotifier
import com.slideindex.app.service.AccessibilityRecoverOutcome
import com.slideindex.app.service.OverlayService
import com.slideindex.app.service.OverlayServiceLifecycle
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.util.OverlaySnoozeController
import com.slideindex.app.util.PermissionHelper
import com.slideindex.app.util.TaskManagerUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Hosts edge gesture overlays on the accessibility service process (SideGesture-style).
 */
class EdgeOverlayHost(
    private val context: Context,
    private val scope: CoroutineScope,
    private val deps: AppDependencies
) {
    private var overlayManager: OverlayManager? = null
    private var floatBallController: FloatBallController? = null
    private var cornerGestureHost: CornerGestureHost? = null
    private var dragDropFlowController: DragDropFlowController? = null
    private var settingsJob: Job? = null
    private var appsJob: Job? = null
    /** 冰箱冻结/解冻后强制重算索引列表的订阅。 */
    private var freezerRefreshJob: Job? = null
    private var previewActive = false
    private var previewContent: LayoutPreviewContent = LayoutPreviewContent.TRIGGER_ONLY
    private var previewFocus: LayoutPreviewFocus? = null
    /** 当前已生效的「按应用分档」档 id：换档才重排，同一个档反复回调不做事。 */
    private var appliedProfileId: String? = null
    private val profileSettleHandler = Handler(Looper.getMainLooper())
    private var displayRotationMonitor: OverlayDisplayRotationMonitor? = null
    private var triggerHealJob: Job? = null
    private var linkProbeJob: Job? = null

    /**
     * 是否已经用“真实设置快照”跑过一次 apply。
     *
     * 真机故障：进程/服务重建后，设置回流的第一帧可能早于宿主就绪而被丢掉，之后设置没有变化
     * 就不会再有回流 —— `OverlayManager.currentSettings` 一直停在默认值（`serviceEnabled=false`），
     * 表现是**触钮永远不挂、面板专用宿主建不出来（快速启动器点了没反应）**，
     * 只有用户手动改一次设置才恢复。这里用“主动补一次”替代等回流。
     */
    private var settingsApplied = false

    /** 最近一次真正应用出去的 serviceEnabled（用于识别“只应用过默认值”的假健康）。 */
    private var appliedServiceEnabled = false
    /** 宿主被显式 stop 后不再自动重建；租约兜底拆卸不属于此列。 */
    private var stopRequested = false

    fun start() {
        stopRequested = false
        AccessibilityLink.noteAlive("host.start")
        AccessibilityLink.attachRecovery(::onAccessibilityLinkBroken)
        Log.d("XGStartup", "host.start cachedServiceEnabled=" +
            deps.settingsRepository.readSnapshot().serviceEnabled)
        startCollectorsAndLease()
        ensureStarted()
        Log.d("XGStartup", "host.start done overlayManager=${overlayManager != null}")
    }

    /**
     * 幂等创建窗口宿主。
     *
     * 租约兜底或"服务未连接"拆掉窗口后，下一次设置回流 / 预览入口会调到这里重建，
     * 避免出现"窗口被拆掉但没有任何路径重挂"的半死状态（表现为触钮、悬浮球、
     * 边角轮盘全部失灵，只能靠用户手动开关或重启恢复）。
     */
    private fun ensureStarted() {
        if (stopRequested || overlayManager != null) return
        if (!PermissionHelper.isAccessibilityServiceEnabled(context)) return
        OverlayPerformanceMonitorBinding.onOverlayShown(
            deps.settingsRepository.readSnapshot(),
            context
        )
        overlayManager = OverlayManager(
            context = context,
            appRepository = deps.appRepository,
            scope = scope,
            onShellCommandsPersist = { commands ->
                scope.launch { deps.settingsRepository.setShellCommands(commands) }
            },
            onQuickLauncherPanelItemsPersist = { panelId, items ->
                scope.launch { deps.settingsRepository.updateQuickLauncherPanelItems(panelId, items) }
            }
        )
        scope.launch(Dispatchers.Default) {
            deps.appRepository.loadApps()
        }
        // 启动期只做被动探测：不因为"预热"就把 Shizuku binder 抢过来。
        // 取 binder 会顺带拉起主进程，并让 :overlay 依赖主进程里的 provider ——
        // 覆盖安装后主进程启动慢被判死时，:overlay 会被系统连带杀掉（悬浮球消失）。
        if (TaskManagerUtil.peekPrivilegedAccess()) {
            TaskManagerUtil.warmUpPrivilegedBackend()
            // 最近任务查询的「探测」只做一次（十几种候选各跑一遍挑出最小集合）。放到启动后
            // 空闲时做，用户第一次打开任务切换器就走稳定路径；这里只预热查询方案，不缓存任务。
            TaskManagerUtil.warmRecentTasksAsync()
        }
        floatBallController = FloatBallController(context, scope, deps.settingsRepository)
        val cornerExecutor = com.slideindex.app.gesture.ActionExecutor(
            context = context,
            appRepository = deps.appRepository,
            onShellCommandsPersist = { commands ->
                scope.launch { deps.settingsRepository.setShellCommands(commands) }
            },
        )
        val cornerRunner = com.slideindex.app.overlay.panels.GestureActionRunner {
            action, settings, armed, ax, ay, side ->
            cornerExecutor.execute(
                action = action,
                settings = settings,
                longPressArmed = armed,
                anchorRawX = ax,
                anchorRawY = ay,
                panelSide = side,
            )
        }
        cornerGestureHost = CornerGestureHost(context, scope, deps.appRepository, cornerRunner).also { it.start() }
        dragDropFlowController = DragDropFlowController(context, scope, deps.settingsRepository).also { it.start() }
        displayRotationMonitor = OverlayDisplayRotationMonitor(context) {
            overlayManager?.relayoutTriggersForDisplayRotation()
            refreshOverlaySuppression()
        }.also { it.start() }
        OverlaySnoozeController.onStateChanged = {
            refreshOverlaySuppression()
        }
        // 宿主刚建好就立刻用当前快照跑一次：不能只等设置回流（首帧可能已被丢掉，
        // 见 [settingsApplied] 的说明），否则触钮/面板宿主会一直缺席到用户改一次设置。
        if (!settingsApplied) {
            runCatching { applySettingsToHosts(deps.settingsRepository.readSnapshot()) }
                .onFailure { Log.e(TAG, "initial applySettings after host start failed", it) }
        }
    }

    /** 设置 → 各宿主的唯一应用口径（设置回流与宿主首次创建共用）。 */
    private fun applySettingsToHosts(settings: AppSettings) {
        // 「按应用分档」：把前台命中的那一档贴到全局之上（没命中就是原样）。
        appliedProfileId = activeGestureProfileId(settings)
        val effectiveSettings = settings
            .withActiveGestureProfile(OverlayService.foregroundPackage)
            .withGestureAnglesPreview()
            .withOverlayLayoutPreview()
        floatBallController?.apply(effectiveSettings)
        updatePerformanceMonitor(effectiveSettings.debugPerformanceMonitorEnabled)
        overlayManager?.applySettings(effectiveSettings)
        settingsApplied = true
        appliedServiceEnabled = settings.serviceEnabled
        OverlayStatePort.noteSettingsApplied()
        if (KeyboardTriggerImeState.imeVisible) {
            FloatBallOverlay.onKeyboardImeChanged()
        }
        if (previewActive) {
            overlayManager?.setPreviewMode(true, previewContent, previewFocus)
        }
    }

    /** 一次性资源：设置/应用订阅与失联租约。窗口宿主拆掉后这些订阅仍需存活，用于触发重建。 */
    private fun startCollectorsAndLease() {
        if (triggerHealJob == null) {
            // 周期自愈：触钮"设置里开着、屏幕上没有"时自己挂回来。
            // 只发生在事件丢失/半重建之后，正常路径下是一次很便宜的判断。
            triggerHealJob = scope.launch {
                val startedAtMs = android.os.SystemClock.elapsedRealtime()
                while (isActive) {
                    // 启动期用更密的节奏：设置在进程冷启动后 1~10s 才加载完是常态，
                    // 12s 一次的节奏会让触钮比悬浮球晚十几秒才出现（真机实测 9~11s）。
                    // 启动窗口内 1.5s 一次，加载完立刻补 apply；之后恢复 12s 的日常节奏。
                    val warmingUp = android.os.SystemClock.elapsedRealtime() - startedAtMs <
                        STARTUP_WARMUP_MS
                    delay(if (warmingUp) STARTUP_HEAL_INTERVAL_MS else TRIGGER_HEAL_INTERVAL_MS)
                    if (stopRequested) return@launch
                    if (!PermissionHelper.isAccessibilityServiceEnabled(context)) continue
                    ensureStarted()
                    // 兜底：只应用过默认设置（全 false）时，用当前快照补一次。
                    // 覆盖“宿主创建时设置还没加载完、之后又没有任何设置变化”的启动窗口。
                    val snapshot = deps.settingsRepository.readSnapshot()
                    if (!settingsApplied || (snapshot.serviceEnabled && !appliedServiceEnabled)) {
                        runCatching { applySettingsToHosts(snapshot) }
                            .onFailure { Log.e(TAG, "heal fallback applySettings failed", it) }
                    }
                    runCatching { overlayManager?.healTriggerAttachments() }
                    // 悬浮球没有触钮那条“窗口没了就重挂”的路径：这里按服务代次再 apply 一次，
                    // 命中代次变化时它会自己 dismiss + 重建（幂等，正常路径下只是一次设置比对）。
                    runCatching { floatBallController?.apply(deps.settingsRepository.readSnapshot()) }
                }
            }
        }
        if (linkProbeJob == null) {
            // 进程内探活：系统摘连接**不会**回调 onUnbind，只能自己定期问一次。
            // `Service.windows` 是少数几个“连接没了就返回空列表”的只读接口，代价是一次 binder 调用。
            // 连续两次为空才判掉线，避免锁屏/窗口过渡的瞬间误伤。
            linkProbeJob = scope.launch {
                var consecutiveFailures = 0
                while (isActive) {
                    delay(LINK_PROBE_INTERVAL_MS)
                    if (stopRequested) return@launch
                    val service = SlideIndexAccessibilityService.accessibilityInstance() ?: continue
                    val windowsAlive = withContext(Dispatchers.Default) {
                        runCatching { service.windows?.isNotEmpty() == true }.getOrDefault(false)
                    }
                    if (windowsAlive) {
                        consecutiveFailures = 0
                        AccessibilityLink.noteAlive("probe")
                    } else {
                        consecutiveFailures++
                        if (consecutiveFailures >= LINK_PROBE_FAILURES_TO_BROKEN) {
                            AccessibilityLink.noteBroken(
                                "probe",
                                "windows empty for ${consecutiveFailures * LINK_PROBE_INTERVAL_MS / 1000}s",
                            )
                        }
                    }
                }
            }
        }
        if (appsJob == null) {
            appsJob = scope.launch {
                deps.appRepository.apps.collectLatest { apps ->
                    overlayManager?.syncApps(apps)
                }
            }
        }
        if (freezerRefreshJob == null) {
            freezerRefreshJob = scope.launch {
                OverlayRefreshBus.freezerChanged.collectLatest {
                    // 冻结/解冻不一定会让可启动列表立刻变化：这里强制重算索引里的冻结项。
                    overlayManager?.reloadApps()
                    overlayManager?.refreshIndexApps()
                }
            }
        }
        if (settingsJob == null) {
            settingsJob = scope.launch {
                combine(
                    deps.settingsRepository.gestureSettings,
                    deps.settingsRepository.overlaySettings
                ) { _, _ ->
                    deps.settingsRepository.readSnapshot()
                }.collectLatest { settings ->
                    Log.d("XGStartup", "settings emission serviceEnabled=" +
                        settings.serviceEnabled + " manager=" + (overlayManager != null))
                    if (!PermissionHelper.isAccessibilityServiceEnabled(context)) {
                        // 服务不可用：拆掉窗口，但保留订阅，恢复后由 ensureStarted() 重建。
                        teardownOverlayWindows()
                        return@collectLatest
                    }
                    ensureStarted()
                    applySettingsToHosts(settings)
                }
            }
        }
        // 租约兜底：无障碍服务被系统解绑/回收时，即使 stop() 没走到，
        // 也会由 OverlayHostLease 把这里的窗口摘掉，避免全屏直触窗留下吞触摸。
        OverlayHostLease.register(
            key = HOST_LEASE_KEY,
            // 同进程内直接看“链路是否可用”，而不是“实例还在不在”：
            // 系统摘连接时实例不会消失，只看实例就会把半死状态当成健康，永远不拆窗。
            isOwnerAlive = { SlideIndexAccessibilityService.isConnected() },
            teardown = ::teardownOverlayWindows,
        )
    }

    fun stop() {
        stopRequested = true
        AccessibilityLink.detachRecovery()
        triggerHealJob?.cancel()
        triggerHealJob = null
        linkProbeJob?.cancel()
        linkProbeJob = null
        OverlaySnoozeController.onStateChanged = null
        OverlaySnoozeController.cancel()
        OverlayPerformanceMonitorBinding.onOverlayHidden(context)
        settingsJob?.cancel()
        settingsJob = null
        appsJob?.cancel()
        appsJob = null
        freezerRefreshJob?.cancel()
        freezerRefreshJob = null
        teardownOverlayWindows()
        OverlayCompose.clearWindowContextCache()
        // 放在最后：万一上面的拆卸半途失败，租约超时还能兜底把残留窗口再摘一次。
        OverlayHostLease.unregister(HOST_LEASE_KEY)
    }

    /**
     * 无障碍链路失效时的唯一恢复入口（由 [AccessibilityLink] 调用，已做 60s 节流）。
     *
     * 顺序刻意是“先拆、再重绑、最后提示”：
     * 1. 拆窗：当前所有浮层都建在这一代连接上，连接没了它们要么是僵尸窗口、要么再也挂不上；
     * 2. 重绑：有 WRITE_SECURE_SETTINGS（Shizuku / adb）时改写系统条目强制重绑；
     * 3. 提示：重绑失败就发一条可点击的通知 —— ColorOS 上这是唯一 100% 可靠的出路。
     */
    private fun onAccessibilityLinkBroken(reason: String) {
        scope.launch {
            Log.e(TAG, "accessibility link broken: $reason → teardown + rebind")
            runCatching { teardownOverlayWindows() }
                .onFailure { Log.e(TAG, "teardown after link broken failed", it) }
            val settings = deps.settingsRepository.readSnapshot()
            val outcome = runCatching {
                OverlayServiceLifecycle.recoverAccessibilityBindingWithRetries(context, settings)
            }.getOrNull()
            if (outcome == AccessibilityRecoverOutcome.Connected) {
                AccessibilityLink.noteAlive("recover")
                AccessibilityLink.noteRecoveryResult("成功")
            } else {
                Log.w(TAG, "accessibility recovery failed (outcome=$outcome), notifying user")
                AccessibilityLink.noteRecoveryResult("失败（$outcome，已发通知）")
                runCatching { AccessibilityRecoverNotifier.notifyOffline(context) }
            }
        }
    }

    /**
     * 幂等：把本宿主创建的所有浮层窗口摘掉。
     *
     * 正常拆卸由 [stop] 调用；服务失联时由 [OverlayHostLease] 兜底调用，
     * 所以每一步都必须自己吞异常，不能因为某一步失败就漏掉后面的窗口。
     */
    private fun teardownOverlayWindows() {
        EdgeDiag.logStack(
            "host",
            "teardownOverlayWindows：拆掉整个浮层宿主（面板/触钮全没，且在重挂前不会再出现）"
        )
        runCatching { floatBallController?.stop() }
        floatBallController = null
        runCatching { cornerGestureHost?.stop() }
        cornerGestureHost = null
        runCatching { dragDropFlowController?.stop() }
        dragDropFlowController = null
        runCatching { displayRotationMonitor?.stop() }
        displayRotationMonitor = null
        runCatching { overlayManager?.destroy() }
        overlayManager = null
        OverlayService.foregroundPackage = null
        appliedProfileId = null
        settingsApplied = false
        appliedServiceEnabled = false
        profileSettleHandler.removeCallbacks(leaveProfileRunnable)
        previewActive = false
    }

    /** 输入层接管转发的触摸事件入口（system_server 模块 → app 现有手势引擎）。 */
    fun handleForwardedTouch(side: PanelSide, event: MotionEvent): Boolean =
        overlayManager?.handleForwardedTouch(side, event) ?: false

    fun cancelForwardedTouch(side: PanelSide) {
        overlayManager?.cancelForwardedTouch(side)
    }

    fun isForwardingCapable(side: PanelSide): Boolean =
        overlayManager?.isForwardingCapable(side) == true

    fun recoverTriggerInteraction(forceReAddChrome: Boolean = false) {
        overlayManager?.recoverTriggerInteraction(forceReAddChrome)
    }

    /** 硬复位取消所有侧边交互：熄屏/锁屏等硬边界与失联兜底使用。 */
    fun forceRecoverInteractionState() {
        overlayManager?.forceRecoverInteractionState()
    }

    fun onKeyboardImeChanged(visibilityChanged: Boolean = false) {
        overlayManager?.onKeyboardImeChanged(visibilityChanged)
    }

    fun onConfigurationChanged() {
        floatBallController?.onConfigurationChanged()
        RegionalPickOverlay.onConfigurationChanged()
        overlayManager?.relayoutTriggersForConfigurationChange()
        cornerGestureHost?.onConfigurationChanged()
        refreshOverlaySuppression()
    }

    fun reloadApps() {
        overlayManager?.reloadApps()
    }

    fun setPreviewMode(
        enabled: Boolean,
        content: LayoutPreviewContent = LayoutPreviewContent.TRIGGER_ONLY,
        focus: LayoutPreviewFocus? = null
    ) {
        previewActive = enabled
        previewContent = content
        previewFocus = if (enabled) focus else null
        overlayManager?.setPreviewMode(enabled, content, previewFocus)
    }

    fun setGestureAnglesPreview(angles: com.slideindex.app.gesture.GestureAngles?) {
        GestureAnglesPreviewStore.current = angles
        val settings = deps.settingsRepository.readSnapshot()
            .withActiveGestureProfile(OverlayService.foregroundPackage)
            .withGestureAnglesPreview()
            .withOverlayLayoutPreview()
        overlayManager?.applySettings(settings)
    }

    /** Immediate overlay response for QS tile toggles; does not wait for DataStore propagation. */
    fun applyServiceEnabledImmediate(enabled: Boolean) {
        if (overlayManager == null) {
            Log.w(TAG, "applyServiceEnabledImmediate: overlayManager null, enabled=$enabled")
            return
        }
        Log.i(TAG, "applyServiceEnabledImmediate: enabled=$enabled")
        val settings = deps.settingsRepository.readSnapshot()
            .withActiveGestureProfile(OverlayService.foregroundPackage)
            .copy(serviceEnabled = enabled)
            .withGestureAnglesPreview()
            .withOverlayLayoutPreview()
        floatBallController?.apply(settings)
        updatePerformanceMonitor(settings.debugPerformanceMonitorEnabled)
        overlayManager?.applySettings(settings)
        cornerGestureHost?.applySettings(settings)
    }

    fun setCornerZonePreviewActive(active: Boolean) {
        ensureStarted()
        cornerGestureHost?.setZonePreviewActive(active)
    }

    fun applyCornerZonePreviewDimensions(
        verticalEdgeWidthDp: Float,
        verticalEdgeHeightDp: Float,
        horizontalEdgeWidthDp: Float,
        horizontalEdgeHeightDp: Float
    ) {
        ensureStarted()
        cornerGestureHost?.applyZonePreviewDimensions(
            verticalEdgeWidthDp,
            verticalEdgeHeightDp,
            horizontalEdgeWidthDp,
            horizontalEdgeHeightDp
        )
    }

    fun updateForegroundPackage(packageName: String?) {
        onForegroundPackageChanged(packageName)
    }

    private fun onForegroundPackageChanged(packageName: String?) {
        if (packageName.isNullOrBlank()) return
        OverlayService.foregroundPackage = packageName
        overlayManager?.updateForegroundPackage(packageName)
        refreshOverlaySuppression()
        // 前台换 App = 可能换档：命中档立刻生效，离开档迟滞一下（见 syncActiveGestureProfile）。
        syncActiveGestureProfile()
    }

    /** 当前前台命中的档 id（没命中/总开关关 = null）。 */
    private fun activeGestureProfileId(settings: AppSettings = deps.settingsRepository.readSnapshot()): String? =
        GestureProfileCatalog.profileIdOfPackage(
            profiles = settings.gestureProfiles,
            enabled = settings.independentAppGestureEnabled,
            foregroundPackage = OverlayService.foregroundPackage,
        )

    /**
     * 前台 App 变化后同步「按应用分档」。
     *
     * - **进入**某个档：立刻生效（用户确实切到了那个 App，晚一点用户就会看到触钮没变）；
     * - **离开**档：迟滞 [PROFILE_SETTLE_MS] 再落回全局 —— 拉通知栏、弹键盘、Activity 过渡态的
     *   一瞬间前台包名会闪成 SystemUI/输入法，不迟滞的话触钮会跟着来回重排（肉眼可见的跳）。
     */
    private fun syncActiveGestureProfile() {
        val target = activeGestureProfileId()
        if (target == appliedProfileId) {
            profileSettleHandler.removeCallbacks(leaveProfileRunnable)
            return
        }
        profileSettleHandler.removeCallbacks(leaveProfileRunnable)
        if (target != null) {
            appliedProfileId = target
            applyEffectiveGestureSettings()
        } else {
            profileSettleHandler.postDelayed(leaveProfileRunnable, PROFILE_SETTLE_MS)
        }
    }

    /** 离开档的迟滞到点：真的落回全局。 */
    private val leaveProfileRunnable = Runnable {
        if (appliedProfileId == null) return@Runnable
        appliedProfileId = null
        applyEffectiveGestureSettings()
    }

    /** 把"全局 + 当前档"那一份重新下发给浮层（触钮几何/外观/动作与横屏那套都会跟着走）。 */
    private fun applyEffectiveGestureSettings() {
        if (!PermissionHelper.isAccessibilityServiceEnabled(context)) return
        ensureStarted()
        val settings = deps.settingsRepository.readSnapshot()
            .withActiveGestureProfile(OverlayService.foregroundPackage)
            .withGestureAnglesPreview()
            .withOverlayLayoutPreview()
        floatBallController?.apply(settings)
        overlayManager?.applySettings(settings)
        cornerGestureHost?.applySettings(settings)
    }

    fun refreshOverlaySuppression() {
        val settings = deps.settingsRepository.readSnapshot()
            .withActiveGestureProfile(OverlayService.foregroundPackage)
            .withGestureAnglesPreview()
            .withOverlayLayoutPreview()
        cornerGestureHost?.refreshSuppression()
        if (!FloatBallPickResultPanel.isShowing) {
            floatBallController?.apply(settings)
        }
        overlayManager?.onEnvironmentChanged()
        // 环境（前台应用/熄屏/桌面）变化是触钮最容易被收掉的时机：顺手检查有没有该挂没挂的边。
        overlayManager?.healTriggerAttachments()
    }

    fun recoverOverlaysIfIdle() {
        overlayManager?.recoverOverlaysIfIdle()
    }

    fun refreshTriggerVisibility() {
        overlayManager?.onEnvironmentChanged()
    }

    fun refreshTriggerVisuals() {
        overlayManager?.refreshTriggerVisuals()
    }

    fun bringEdgeChromeAbovePanels(forceReAdd: Boolean = true, sides: Set<PanelSide>? = null) {
        overlayManager?.bringEdgeChromeAbovePanels(forceReAdd, sides)
    }

    fun edgePresentationNeedsChromeRaise(): Boolean =
        overlayManager?.edgePresentationNeedsChromeRaise() == true

    fun notifyEdgeChromeBelowPanel() {
        overlayManager?.notifyEdgeChromeBelowPanel()
    }

    fun suspendAllEdgeOverlays() {
        overlayManager?.suspendAllEdgeOverlays()
    }

    fun resumeAllEdgeOverlays() {
        overlayManager?.resumeAllEdgeOverlays()
    }

    fun suppressCaptureVisuals() {
        overlayManager?.suppressCaptureVisuals()
    }

    fun resumeCaptureVisuals() {
        overlayManager?.resumeCaptureVisuals()
    }

    fun suspendEdgeCapturesForPassthrough() {
        overlayManager?.suspendEdgeCapturesForPassthrough()
    }

    fun resumeEdgeCapturesAfterPassthrough() {
        overlayManager?.resumeEdgeCapturesAfterPassthrough()
    }

    fun dispatchExternalGestureAction(
        action: com.slideindex.app.gesture.GestureAction,
        anchorRawY: Float,
        panelSide: com.slideindex.app.overlay.PanelSide? = null
    ): Boolean =
        overlayManager?.dispatchExternalGestureAction(action, anchorRawY, panelSide) == true

    private fun updatePerformanceMonitor(enabled: Boolean) {
        OverlayPerformanceMonitorBinding.syncUserPreference(enabled, context)
    }

    fun applyOverlayLayoutPreviewSettings() {
        if (!PermissionHelper.isAccessibilityServiceEnabled(context)) return
        ensureStarted()
        val settings = deps.settingsRepository.readSnapshot()
            .withActiveGestureProfile(OverlayService.foregroundPackage)
            .withGestureAnglesPreview()
            .withOverlayLayoutPreview()
        overlayManager?.applySettings(settings)
        overlayManager?.refreshTriggerVisuals()
    }

    fun previewIndexHeightFraction(fraction: Float) {
        OverlayLayoutPreviewStore.previewIndexHeightFraction(fraction)
        applyOverlayLayoutPreviewSettings()
    }

    fun clearIndexHeightPreview() {
        OverlayLayoutPreviewStore.clearIndexSliderPreview()
        applyOverlayLayoutPreviewSettings()
    }

    /**
     * 松手提交索引高度预览：把临时值转成"待确认"，等设置落盘回流追上再撤，
     * 避免松手瞬间先按旧值重画一次。
     */
    fun commitIndexHeightPreview() {
        OverlayLayoutPreviewStore.commitIndexHeightPreview()
        applyOverlayLayoutPreviewSettings()
    }

    fun previewIndexVerticalPositionFraction(fraction: Float) {
        OverlayLayoutPreviewStore.previewIndexVerticalPositionFraction(fraction)
        applyOverlayLayoutPreviewSettings()
    }

    fun clearIndexVerticalPositionPreview() {
        OverlayLayoutPreviewStore.clearIndexSliderPreview()
        applyOverlayLayoutPreviewSettings()
    }

    /** 松手提交索引位置预览：语义同 [commitIndexHeightPreview]。 */
    fun commitIndexVerticalPositionPreview() {
        OverlayLayoutPreviewStore.commitIndexVerticalPositionPreview()
        applyOverlayLayoutPreviewSettings()
    }

    /** 索引页滑条松手（长度/位置共用）：只提交最近拖过的那一个。 */
    fun commitIndexSliderPreviews() {
        OverlayLayoutPreviewStore.commitIndexSliderPreviews()
        applyOverlayLayoutPreviewSettings()
    }

    fun mergeTriggerHandleLayoutPreview(
        side: PanelSide,
        handleId: String,
        edgeWidthDp: Float? = null,
        topFraction: Float? = null,
        bottomFraction: Float? = null,
        shortSwipeDistanceDp: Float? = null,
        longSwipeDistanceDp: Float? = null,
        design: com.slideindex.app.gesture.TriggerHandleDesign? = null
    ) {
        OverlayLayoutPreviewStore.mergeTriggerHandlePreview(
            side = side,
            handleId = handleId,
            edgeWidthDp = edgeWidthDp,
            topFraction = topFraction,
            bottomFraction = bottomFraction,
            shortSwipeDistanceDp = shortSwipeDistanceDp,
            longSwipeDistanceDp = longSwipeDistanceDp,
            design = design
        )
        applyOverlayLayoutPreviewSettings()
    }

    fun clearTriggerHandleLayoutPreview() {
        OverlayLayoutPreviewStore.clearTriggerHandlePreview()
        applyOverlayLayoutPreviewSettings()
    }

    /** 松手提交触钮预览：同 [commitIndexHeightPreview]。 */
    fun commitTriggerHandleLayoutPreview() {
        OverlayLayoutPreviewStore.commitTriggerHandlePreview()
        applyOverlayLayoutPreviewSettings()
    }

    fun clearOverlayLayoutPreview() {
        OverlayLayoutPreviewStore.clear()
        applyOverlayLayoutPreviewSettings()
    }

    private fun AppSettings.withGestureAnglesPreview(): AppSettings {
        val preview = GestureAnglesPreviewStore.current ?: return this
        return copy(edgeTrigger = edgeTrigger.copy(gestureAngles = preview))
    }

    private companion object {
        const val TAG = "EdgeOverlayHost"
        const val HOST_LEASE_KEY = "edge-overlay-host"

        /** 触钮自愈巡检间隔：足够快（用户几乎察觉不到）又足够便宜。 */
        const val TRIGGER_HEAL_INTERVAL_MS = 12_000L

        /** 启动期自愈的加速窗口：这段时间内用 [STARTUP_HEAL_INTERVAL_MS] 的节奏。 */
        const val STARTUP_WARMUP_MS = 20_000L

        /** 启动期自愈间隔：只用来尽快补上“设置加载完但还没应用”的那一帧。 */
        const val STARTUP_HEAL_INTERVAL_MS = 1_500L

        /** 无障碍链路探活间隔（连续两次失败才判掉线，见 [LINK_PROBE_FAILURES_TO_BROKEN]）。 */
        const val LINK_PROBE_INTERVAL_MS = 15_000L

        /** 探活连续失败几次才判掉线：一次空窗口可能只是锁屏/窗口过渡的瞬态。 */
        const val LINK_PROBE_FAILURES_TO_BROKEN = 2

        /** 离开「按应用分档」的迟滞：拉通知栏/弹键盘/过渡态的前台抖动短于此值时不动触钮。 */
        const val PROFILE_SETTLE_MS = 400L
    }
}
