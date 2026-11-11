package com.slideindex.app.overlay.ringlauncher

import com.slideindex.app.overlay.overlayHostContext

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.slideindex.app.data.AppInfo
import com.slideindex.app.data.AppRepository
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.overlay.ScreenOffDismissReceiver
import com.slideindex.app.overlay.OverlayComposeDialogHost
import com.slideindex.app.overlay.OverlayDisplayMetrics
import com.slideindex.app.overlay.HoneycombIconLoader
import com.slideindex.app.overlay.HoneycombTargetResolver
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.overlay.layout.FvRingLauncherSide
import com.slideindex.app.overlay.layout.FvCircleLayoutEngine
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatBallSide
import com.slideindex.app.settings.FvRingLauncherAxis
import com.slideindex.app.settings.FvRingLauncherAxisMergeDirection
import com.slideindex.app.settings.FvRingLauncherSettings
import com.slideindex.app.settings.fvRingLauncherFor
import com.slideindex.app.settings.toAxis
import com.slideindex.app.util.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.slideindex.app.ui.miuix.theme.ModuleTheme

@SuppressLint("StaticFieldLeak")
object RingLauncherOverlayWindow {
    private const val TAG = "RingLauncherOverlay"
    /** FV CircleAppContainer 贴边 inset：n5.q.a(20) */
    private const val FV_EDGE_INSET_DP = 20f
    private val mainHandler = Handler(Looper.getMainLooper())
    private val settingsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var controller: RingLauncherOverlayController? = null
    private var slotConfigDialogHost: OverlayComposeDialogHost? = null
    private var lastSettings: AppSettings = AppSettings()
    private var appContext: Context? = null
    private val screenOffDismissReceiver = ScreenOffDismissReceiver { dismiss() }
    private var externalTracking = false
    private var persistAfterPin = false
    private var activeSide: FvRingLauncherSide? = null
    private var editModeActive = false

    val isShowing: Boolean get() = controller?.isVisible() == true

    fun currentAxis(): FvRingLauncherAxis = activeSide?.toAxis() ?: FvRingLauncherAxis.VERTICAL

    fun suspendForSlotIconEditor() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { suspendForSlotIconEditor() }
            return
        }
        slotConfigDialogHost?.dismiss()
        slotConfigDialogHost = null
        controller?.suspendForExternalActivity()
        // 圆环让位给外部编辑页（自定义图标等）时，悬浮球要还回来：
        // 球本来只在**圆环展示期间**才需要收起（避免挡住圆环与它的触摸），
        // 编辑页是普通页面，球消失反而是 bug（真机反馈：一进自定义图标页球就没了）。
        com.slideindex.app.overlay.panels.PanelFloatBallHooks.restoreChromeAfterRingLauncher?.invoke()
    }

    fun resumeAfterSlotIconEditor() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { resumeAfterSlotIconEditor() }
            return
        }
        controller?.resumeAfterExternalActivity()
        refreshFromSettings()
        // 圆环回到屏幕上了 → 按原设计重新收起悬浮球；若圆环没能恢复（比如已被释放），则把球还回来，
        // 避免出现"圆环没了、球也没了"的悬挂状态。
        if (isShowing) {
            com.slideindex.app.overlay.panels.PanelFloatBallHooks.hideChromeForRingLauncher?.invoke()
        } else {
            com.slideindex.app.overlay.panels.PanelFloatBallHooks.restoreChromeAfterRingLauncher?.invoke()
        }
    }

    /**
     * 外部编辑页已经离开前台，但圆环仍卡在"为它让位"的挂起态时自愈。
     *
     * 进编辑页是主进程的 trampoline，返回时的 [resumeAfterSlotIconEditor] 调用落在主进程，
     * 而本 object 的状态在 `:overlay` —— 那个调用等于空操作（跨进程静态），
     * 于是圆环一直 GONE、悬浮球一直是隐藏态。这里由 `:overlay` 侧在前台变化时兜底恢复。
     */
    fun selfHealAfterExternalActivity() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { selfHealAfterExternalActivity() }
            return
        }
        val current = controller ?: return
        if (!current.isSuspendedForExternalActivity()) return
        resumeAfterSlotIconEditor()
    }

    fun openSlotPicker(slotIndex: Int) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { openSlotPicker(slotIndex) }
            return
        }
        val hostContext = overlayHostContext() ?: return
        val settings = lastSettings
        val axis = currentAxis()
        val currentItem = settings.fvRingLauncherFor(axis).itemAt(slotIndex)
        val dialogHost = slotConfigDialogHost ?: OverlayComposeDialogHost(
            context = hostContext,
            applyTheme = { content ->
                ModuleTheme(settings = lastSettings) {
                    // 槽位面板复用快捷启动器的「添加」区，里面的可组合会读 AppRepository。
                    // 浮层合成默认没有 LocalAppDependencies，必须在这里补上，
                    // 否则点开槽位就抛 "AppDependencies not provided" 闪退。
                    val provider =
                        com.slideindex.app.overlay.panels.PanelRuntimeHooks.appDependenciesComposition
                    if (provider != null) provider.provide(content) else content()
                }
            },
        ).also { slotConfigDialogHost = it }

        // 圆环槽位的应用列表要和动作选择 / 索引一致：带分身条目、带冻结 / 暂停状态。
        // enrich 会查 LauncherApps / shell，放 IO 线程，算完再回主线程弹窗。
        settingsScope.launch {
            val apps = com.slideindex.app.overlay.panels.PanelRuntimeHooks.pickerApps
                ?.invoke(hostContext, null, settings.freezerAppPackages)
                .orEmpty()
            mainHandler.post {
                val sheet = com.slideindex.app.overlay.panels.PanelRuntimeHooks.ringSlotConfigSheetContent
                if (sheet == null) {
                    dialogHost.dismiss()
                    return@post
                }
                dialogHost.show(
                    onBackPressed = {
                        dialogHost.dismiss()
                        true
                    },
                ) {
                    sheet.content(
                        slotIndex = slotIndex,
                        currentItem = currentItem,
                        settings = settings,
                        apps = apps,
                        onDismiss = { dialogHost.dismiss() },
                        onSelectItem = { selectedItem ->
                            val itemToSet = selectedItem
                                ?: QuickLauncherItem(QuickLauncherItemType.APP, "", "")
                            com.slideindex.app.overlay.panels.PanelRuntimeHooks.persistRingLauncherSlot
                                ?.invoke(axis, slotIndex, itemToSet)
                            settingsScope.launch { refreshFromSettings() }
                        },
                        onOpenCustomIconEditor = {
                            suspendForSlotIconEditor()
                            val intent =
                                com.slideindex.app.overlay.panels.PanelRuntimeHooks.ringSlotIconEditorIntent
                                    ?.invoke(hostContext, axis, slotIndex)
                            if (intent != null) {
                                runCatching { hostContext.startActivity(intent) }
                            }
                        },
                        launchCreateShortcut = { createHost, onResult ->
                            com.slideindex.app.overlay.panels.PanelRuntimeHooks.createShortcutLauncher
                                ?.invoke(
                                    hostContext,
                                    createHost,
                                    { RingLauncherOverlayWindow.dismiss() },
                                ) { created ->
                                    onResult(created)
                                }
                        },
                    )
                }
            }
        }
    }

    fun show(
        context: Context,
        settings: AppSettings,
        anchorRawX: Float,
        anchorRawY: Float,
        externalTracking: Boolean,
        onLaunch: (QuickLauncherItem, Boolean) -> Unit,
        edgePanelSide: PanelSide? = null,
        initialHighlightSlot: Int = -1,
        autoSelectOriginX: Float? = null,
        autoSelectOriginY: Float? = null,
    ): Boolean {
        lastSettings = settings
        if (Looper.myLooper() != Looper.getMainLooper()) {
            var result = false
            val latch = java.util.concurrent.CountDownLatch(1)
            mainHandler.post {
                result = show(
                    context,
                    settings,
                    anchorRawX,
                    anchorRawY,
                    externalTracking,
                    onLaunch,
                    edgePanelSide,
                    initialHighlightSlot,
                    autoSelectOriginX,
                    autoSelectOriginY,
                )
                latch.countDown()
            }
            runCatching { latch.await(500, java.util.concurrent.TimeUnit.MILLISECONDS) }
            return result
        }

        if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
            Log.w(TAG, "show: accessibility service not enabled")
            return false
        }

        val hostContext = overlayHostContext()
            ?: run {
                Log.w(TAG, "show: accessibility service not connected")
                return false
            }
        val appRepository = com.slideindex.app.overlay.panels.PanelRuntimeHooks.appRepository?.invoke(hostContext)
        val apps = appRepository?.getCachedApps().orEmpty()
        val appsByPackage = apps.associateBy { it.packageName }

        val wm = hostContext.getSystemService(android.view.WindowManager::class.java)
        // 一次算清 density / 宽 / 高：三者必须来自**同一帧** WindowMetrics。
        // 分两次读的话，旋转瞬间 bounds 可能已经换了一帧，宽度和高度来自不同方向，
        // resolveFvSide() 就会把左右上下判错（表现为圆环贴错边）。
        //
        // 密度允许带提示（悬浮球按自己的视图反推的那个），但宽度绝不允许从外面传进来：
        // 1.64.x 的故障就是兜底常量把假密度（1f）和假宽度（1080f）一起带了进来，
        // 圆环按 1dp=1px 画，在 2.75 密度的机器上只剩 36%，还贴着屏幕左边。
        val layoutMetrics = OverlayDisplayMetrics.resolve(
            context = hostContext,
            windowManager = wm,
            densityHint = com.slideindex.app.overlay.panels.PanelFloatBallHooks.layoutDensityHint
                ?.invoke(settings),
        )
        val density = layoutMetrics.density
        val screenWidth = layoutMetrics.widthPixels.toFloat().coerceAtLeast(1f)
        val screenHeight = layoutMetrics.heightPixels.toFloat().coerceAtLeast(1f)
        val side = resolveFvSide(anchorRawX, anchorRawY, screenWidth, screenHeight, edgePanelSide)
        activeSide = side
        val fvSettings = settings.fvRingLauncherFor(side)
        // 编辑模式只在「真的没东西可显示」时才进入，判定见 shouldStartRingLauncherInEditMode：
        // 槽位全空时必须还有可用应用来填充，否则会关掉 autoFill 又进编辑模式，圆环变成空的。
        val startingInEdit = shouldStartRingLauncherInEditMode(
            configuredSlotCount = fvSettings.configuredCount(),
            autoFillCandidateCount = appsByPackage.size,
        )
        editModeActive = startingInEdit
        val targets = resolveTargets(
            hostContext,
            fvSettings,
            appsByPackage,
            appRepository,
            settings,
            autoFill = !startingInEdit,
        )
        hydrateTargetIcons(targets, appRepository)

        // 只在该槽位真的有内容时才自动选中，否则松手会"什么都不发生"（看起来像卡住）。
        val resolvedInitialHighlight =
            if (initialHighlightSlot >= 0 && targets.getOrNull(initialHighlightSlot) != null) {
                initialHighlightSlot
            } else {
                -1
            }

        val edgeInsetPx = FV_EDGE_INSET_DP * density
        val anchorX = when (side) {
            FvRingLauncherSide.LEFT -> edgeInsetPx
            FvRingLauncherSide.RIGHT -> screenWidth - edgeInsetPx
            FvRingLauncherSide.BOTTOM, FvRingLauncherSide.TOP -> screenWidth / 2f
        }
        val anchorY = when (side) {
            FvRingLauncherSide.LEFT, FvRingLauncherSide.RIGHT -> anchorRawY
            FvRingLauncherSide.BOTTOM -> screenHeight - edgeInsetPx
            FvRingLauncherSide.TOP -> edgeInsetPx
        }

        val overlayController = controller ?: RingLauncherOverlayController(hostContext, mainHandler).also {
            controller = it
        }
        this.externalTracking = externalTracking
        persistAfterPin = !externalTracking
        // 「长按自动选中首槽」的判定基准：圆环弹出那一刻手指的位置。
        // 缺省退回锚点（线条侧 / 边缘手势的锚点本来就是按下点）。
        val resolvedAutoSelectOriginX = autoSelectOriginX ?: anchorRawX
        val resolvedAutoSelectOriginY = autoSelectOriginY ?: anchorRawY

        val launchCallback = onLaunch
        val resolvedItems = targets.filterNotNull().map { it.item }
        if (appRepository != null && resolvedItems.isNotEmpty()) {
            val iconSizePx = (FvCircleLayoutEngine.ICON_SIZE_DP * density).toInt()
            settingsScope.launch {
                HoneycombIconLoader.warmAppIcons(appRepository, resolvedItems, iconSizePx)
            }
            com.slideindex.app.overlay.panels.PanelRuntimeHooks.refreshRecentTasks
            ?.invoke(appRepository) { recentEntries ->
                if (recentEntries.isNotEmpty()) {
                    refreshFromSettings()
                }
            }
        }

        com.slideindex.app.overlay.panels.PanelFloatBallHooks.hideChromeForRingLauncher?.invoke()

        val shown = overlayController.show(
            settings = settings,
            fvSettings = fvSettings,
            fvLinkAppearanceAxes = settings.fvRingLauncherLinkAppearanceAxes,
            fvLinkSlotAxes = settings.fvRingLauncherLinkSlotAxes,
            targets = targets,
            appsByPackage = appsByPackage,
            appRepository = appRepository,
            side = side,
            anchorX = anchorX,
            anchorY = anchorY,
            externalTracking = externalTracking,
            layoutDensity = density,
            screenWidth = screenWidth,
            initialHighlightSlot = resolvedInitialHighlight,
            autoSelectOriginX = resolvedAutoSelectOriginX,
            autoSelectOriginY = resolvedAutoSelectOriginY,
            listener = object : RingLauncherOverlayController.Listener {
                override fun onLaunch(target: com.slideindex.app.overlay.HoneycombRuntimeTarget, longPressArmed: Boolean) {
                    screenOffDismissReceiver.unregister()
                    releaseOverlayState()
                    launchCallback(target.item, longPressArmed)
                }

                override fun onClosed() {
                    // dismissPanel 已 clearSessionState；此处为会话真正结束，须恢复悬浮球（勿因 persistAfterPin 跳过）。
                    screenOffDismissReceiver.unregister()
                    releaseOverlayState()
                }

                override fun onCircleCountChange(circleCount: Int) {
                    val repository = com.slideindex.app.overlay.panels.PanelRuntimeHooks.settingsRepository
                        ?.invoke(hostContext) ?: return
                    val axis = side.toAxis()
                    settingsScope.launch {
                        repository.setFvRingLauncherCircleCount(axis, circleCount)
                        val refreshedSettings = repository.settings.first()
                        val refreshedFv = refreshedSettings.fvRingLauncherFor(side)
                        val refreshedTargets = resolveSessionTargets(
                            hostContext,
                            refreshedFv,
                            appsByPackage,
                            appRepository,
                            refreshedSettings,
                        )
                        mainHandler.post {
                            if (controller === overlayController && overlayController.isVisible()) {
                                overlayController.refreshTargets(
                                    refreshedFv,
                                    refreshedTargets,
                                    appsByPackage,
                                    appRepository,
                                )
                            }
                        }
                    }
                }

                override fun onSettingsChange(settings: FvRingLauncherSettings) {
                    val repository = com.slideindex.app.overlay.panels.PanelRuntimeHooks.settingsRepository
                        ?.invoke(hostContext) ?: return
                    val axis = side.toAxis()
                    settingsScope.launch {
                        repository.setFvRingLauncherSettings(axis, settings)
                        val refreshedSettings = repository.settings.first()
                        val refreshedFv = refreshedSettings.fvRingLauncherFor(side)
                        val refreshedTargets = resolveSessionTargets(
                            hostContext,
                            refreshedFv,
                            appsByPackage,
                            appRepository,
                            refreshedSettings,
                        )
                        mainHandler.post {
                            if (controller === overlayController && overlayController.isVisible()) {
                                overlayController.refreshTargets(
                                    refreshedFv,
                                    refreshedTargets,
                                    appsByPackage,
                                    appRepository,
                                )
                            }
                        }
                    }
                }

                override fun onLinkAppearanceAxesChange(
                    enabled: Boolean,
                    mergeDirection: FvRingLauncherAxisMergeDirection?,
                ) {
                    val repository = com.slideindex.app.overlay.panels.PanelRuntimeHooks.settingsRepository
                        ?.invoke(hostContext) ?: return
                    val axis = side.toAxis()
                    settingsScope.launch {
                        repository.setFvRingLauncherLinkAppearanceAxes(enabled, axis, mergeDirection)
                        val refreshedSettings = repository.settings.first()
                        lastSettings = refreshedSettings
                        val refreshedFv = refreshedSettings.fvRingLauncherFor(side)
                        val refreshedTargets = resolveSessionTargets(
                            hostContext,
                            refreshedFv,
                            appsByPackage,
                            appRepository,
                            refreshedSettings,
                        )
                        mainHandler.post {
                            if (controller === overlayController && overlayController.isVisible()) {
                                overlayController.refreshSession(
                                    fvSettings = refreshedFv,
                                    fvLinkAppearanceAxes = refreshedSettings.fvRingLauncherLinkAppearanceAxes,
                                    fvLinkSlotAxes = refreshedSettings.fvRingLauncherLinkSlotAxes,
                                    targets = refreshedTargets,
                                    appsByPackage = appsByPackage,
                                )
                            }
                        }
                    }
                }

                override fun onLinkSlotAxesChange(
                    enabled: Boolean,
                    mergeDirection: FvRingLauncherAxisMergeDirection?,
                ) {
                    val repository = com.slideindex.app.overlay.panels.PanelRuntimeHooks.settingsRepository
                        ?.invoke(hostContext) ?: return
                    val axis = side.toAxis()
                    settingsScope.launch {
                        repository.setFvRingLauncherLinkSlotAxes(enabled, axis, mergeDirection)
                        val refreshedSettings = repository.settings.first()
                        lastSettings = refreshedSettings
                        val refreshedFv = refreshedSettings.fvRingLauncherFor(side)
                        val refreshedTargets = resolveSessionTargets(
                            hostContext,
                            refreshedFv,
                            appsByPackage,
                            appRepository,
                            refreshedSettings,
                        )
                        mainHandler.post {
                            if (controller === overlayController && overlayController.isVisible()) {
                                overlayController.refreshSession(
                                    fvSettings = refreshedFv,
                                    fvLinkAppearanceAxes = refreshedSettings.fvRingLauncherLinkAppearanceAxes,
                                    fvLinkSlotAxes = refreshedSettings.fvRingLauncherLinkSlotAxes,
                                    targets = refreshedTargets,
                                    appsByPackage = appsByPackage,
                                )
                            }
                        }
                    }
                }

                override fun onEditModeChanged(editMode: Boolean) {
                    editModeActive = editMode
                    refreshTargetsForEditMode()
                }

                override fun onSlotsSwapped(fromSlot: Int, toSlot: Int) {
                    val repository = com.slideindex.app.overlay.panels.PanelRuntimeHooks.settingsRepository
                        ?.invoke(hostContext) ?: return
                    val axis = side.toAxis()
                    settingsScope.launch {
                        repository.swapFvRingLauncherSlots(axis, fromSlot, toSlot)
                    }
                }
            },
        )
        if (!shown) {
            Log.w(TAG, "show: overlayController.show returned false")
            com.slideindex.app.overlay.panels.PanelFloatBallHooks.restoreChromeAfterRingLauncher?.invoke()
            return false
        }

        controller = overlayController
        appContext = hostContext.applicationContext
        screenOffDismissReceiver.register(hostContext)
        if (externalTracking) {
            // 用**按下点**而不是锚点做开环后的第一次交互：半隐藏停靠 / 自定义位置时
            // 球心离贴边锚点可能已超过自动选中阈值，用球心会一开环就把自动选中作废。
            overlayController.externalMove(resolvedAutoSelectOriginX, resolvedAutoSelectOriginY)
        } else {
            overlayController.pinForLeaveOpen()
            com.slideindex.app.overlay.panels.PanelFloatBallHooks.suppressTouchHostsForActiveLauncherOverlay?.invoke()
        }
        if (appRepository != null && targets.any { it?.icon == null }) {
            HoneycombIconLoader.loadMissingIconsAsync(
                context = hostContext,
                targets = targets.filterNotNull(),
                appsByPackage = appsByPackage,
                appRepository = appRepository,
                activityShortcuts = settings.activityShortcuts,
                shellCommands = settings.shellCommands,
                onIconsReady = {
                    if (controller === overlayController && overlayController.isVisible()) {
                        val refreshedTargets = resolveSessionTargets(
                            hostContext,
                            fvSettings,
                            appsByPackage,
                            appRepository,
                            settings,
                        )
                        overlayController.refreshTargets(fvSettings, refreshedTargets, appsByPackage, appRepository)
                    }
                },
            )
        }
        return true
    }

    fun updatePointer(rawX: Float, rawY: Float) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { updatePointer(rawX, rawY) }
            return
        }
        controller?.externalMove(rawX, rawY)
    }

    fun confirmSelection(
        rawX: Float,
        rawY: Float,
        actionExecutor: Any,
        settings: AppSettings,
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { confirmSelection(rawX, rawY, actionExecutor, settings) }
            return
        }
        updatePointer(rawX, rawY)
        if (externalTracking) {
            externalTracking = false
        }
        controller?.externalUp(rawX, rawY, cancelled = false)
        val overlayController = controller
        if (overlayController != null && overlayController.isVisible() && overlayController.isPinned()) {
            persistAfterPin = true
        }
        if (overlayController?.isVisible() == true) {
            com.slideindex.app.overlay.panels.PanelFloatBallHooks.suppressTouchHostsForActiveLauncherOverlay?.invoke()
        }
    }

    fun refreshFromSettings() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { refreshFromSettings() }
            return
        }
        val overlayController = controller ?: return
        if (!overlayController.isVisible()) return
        val hostContext = overlayHostContext() ?: return
        val appRepository = com.slideindex.app.overlay.panels.PanelRuntimeHooks.appRepository?.invoke(hostContext) ?: return
        val apps = appRepository.getCachedApps()
        val appsByPackage = apps.associateBy { it.packageName }
        settingsScope.launch {
            val settings = com.slideindex.app.settings.SettingsFlowHolder.settingsFlow(hostContext)
                ?.first() ?: lastSettings
            lastSettings = settings
            val side = activeSide ?: return@launch
            val fvSettings = settings.fvRingLauncherFor(side)
            val targets = resolveSessionTargets(
                hostContext,
                fvSettings,
                appsByPackage,
                appRepository,
                settings,
            )
            mainHandler.post {
                if (controller === overlayController && overlayController.isVisible()) {
                    overlayController.refreshTargets(fvSettings, targets, appsByPackage, appRepository)
                }
            }
        }
    }

    fun onGestureSessionEnd() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { onGestureSessionEnd() }
            return
        }
        if (persistAfterPin || controller?.isPinned() == true) {
            return
        }
        dismiss()
    }

    fun dismiss() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismiss() }
            return
        }
        slotConfigDialogHost?.dismiss()
        slotConfigDialogHost = null
        controller?.removeNow()
        screenOffDismissReceiver.unregister()
        releaseOverlayState()
    }

    private fun resolveSessionTargets(
        context: Context,
        fvSettings: FvRingLauncherSettings,
        appsByPackage: Map<String, AppInfo>,
        appRepository: AppRepository?,
        settings: AppSettings,
    ): List<com.slideindex.app.overlay.HoneycombRuntimeTarget?> {
        val targets = resolveTargets(
            context = context,
            fvSettings = fvSettings,
            appsByPackage = appsByPackage,
            appRepository = appRepository,
            settings = settings,
            autoFill = !editModeActive,
        )
        hydrateTargetIcons(targets, appRepository)
        return targets
    }

    private fun refreshTargetsForEditMode() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { refreshTargetsForEditMode() }
            return
        }
        val overlayController = controller ?: return
        if (!overlayController.isVisible()) return
        val hostContext = overlayHostContext() ?: return
        val appRepository = com.slideindex.app.overlay.panels.PanelRuntimeHooks.appRepository?.invoke(hostContext) ?: return
        val appsByPackage = appRepository.getCachedApps().associateBy { it.packageName }
        settingsScope.launch {
            val settings = com.slideindex.app.settings.SettingsFlowHolder.settingsFlow(hostContext)
                ?.first() ?: lastSettings
            lastSettings = settings
            val side = activeSide ?: return@launch
            val fvSettings = settings.fvRingLauncherFor(side)
            val refreshedTargets = resolveSessionTargets(
                hostContext,
                fvSettings,
                appsByPackage,
                appRepository,
                settings,
            )
            mainHandler.post {
                if (controller === overlayController && overlayController.isVisible()) {
                    overlayController.refreshTargets(fvSettings, refreshedTargets, appsByPackage, appRepository)
                }
            }
        }
    }

    private fun resolveTargets(
        context: Context,
        fvSettings: FvRingLauncherSettings,
        appsByPackage: Map<String, AppInfo>,
        appRepository: AppRepository?,
        settings: AppSettings,
        autoFill: Boolean = true,
    ): List<com.slideindex.app.overlay.HoneycombRuntimeTarget?> {
        val slotCount = fvSettings.slotCount()
        val explicitSlots = mutableMapOf<Int, com.slideindex.app.overlay.HoneycombRuntimeTarget>()
        val usedPackages = mutableSetOf<String>()

        for (i in 0 until slotCount) {
            val item = fvSettings.itemAt(i)?.takeIf { it.payload.isNotBlank() }
            if (item != null) {
                val target = HoneycombTargetResolver.resolve(
                    context,
                    listOf(item),
                    appsByPackage,
                    appRepository,
                    settings.activityShortcuts,
                    settings.shellCommands,
                ).firstOrNull()
                if (target != null) {
                    explicitSlots[i] = target
                    if (item.type == QuickLauncherItemType.APP) {
                        usedPackages.add(item.appTarget?.packageName ?: item.payload)
                    }
                }
            }
        }

        val autoFillQueue = ArrayDeque<AppInfo>()
        if (appRepository != null) {
            val cachedRecents = (com.slideindex.app.overlay.panels.PanelRuntimeHooks.cachedRecentTasks
                ?.invoke().orEmpty()).mapNotNull { entry ->
                    runCatching {
                        entry.javaClass.getMethod("getApp").invoke(entry) as? com.slideindex.app.data.AppInfo
                    }.getOrNull()
                }
                // 任务切换器现在会显示本应用；圆环的「最近应用」自动填充仍然不要把自己塞进去。
                .filter { it.packageName != context.packageName }
            for (app in cachedRecents) {
                val pkg = app.packageName
                if (pkg.isNotBlank() &&
                    pkg in appsByPackage &&
                    pkg !in usedPackages &&
                    !isSystemInternalPackage(pkg)
                ) {
                    autoFillQueue.add(app)
                    usedPackages.add(pkg)
                }
            }
        }
        for (app in appsByPackage.values) {
            val pkg = app.packageName
            if (pkg.isNotBlank() &&
                pkg !in usedPackages &&
                !isSystemInternalPackage(pkg)
            ) {
                autoFillQueue.add(app)
                usedPackages.add(pkg)
            }
        }

        return List(slotCount) { index ->
            explicitSlots[index] ?: run {
                if (!autoFill) return@run null
                val autoApp = autoFillQueue.removeFirstOrNull() ?: return@run null
                val item = QuickLauncherItem(
                    type = QuickLauncherItemType.APP,
                    payload = autoApp.packageName,
                    label = autoApp.label,
                )
                HoneycombTargetResolver.resolve(
                    context,
                    listOf(item),
                    appsByPackage,
                    appRepository,
                    settings.activityShortcuts,
                    settings.shellCommands,
                ).firstOrNull()
            }
        }
    }

    private fun isSystemInternalPackage(pkg: String): Boolean {
        if (pkg.isBlank()) return true
        val lower = pkg.lowercase()
        return lower == "android" ||
            lower.startsWith("com.android.internal") ||
            lower.contains("intentresolver") ||
            lower == "com.android.settings.intelligence" ||
            lower == "com.android.permissioncontroller"
    }

    private fun releaseOverlayState() {
        com.slideindex.app.overlay.panels.PanelFloatBallHooks.restoreChromeAfterRingLauncher?.invoke()
        controller = null
        appContext = null
        externalTracking = false
        persistAfterPin = false
        activeSide = null
        editModeActive = false
    }

    private fun resolveFvSide(
        anchorRawX: Float,
        anchorRawY: Float,
        screenWidth: Float,
        screenHeight: Float,
        edgePanelSide: PanelSide? = null,
    ): FvRingLauncherSide {
        when (edgePanelSide) {
            PanelSide.BOTTOM -> return FvRingLauncherSide.BOTTOM
            PanelSide.TOP -> return FvRingLauncherSide.TOP
            PanelSide.LEFT -> return FvRingLauncherSide.LEFT
            PanelSide.RIGHT -> return FvRingLauncherSide.RIGHT
            null -> Unit
        }
        val bottomThreshold = screenHeight * 0.15f
        if (anchorRawY >= screenHeight - bottomThreshold) {
            return FvRingLauncherSide.BOTTOM
        }
        val topThreshold = screenHeight * 0.15f
        if (anchorRawY <= topThreshold) {
            return FvRingLauncherSide.TOP
        }
        return if (anchorRawX > screenWidth / 2f) {
            FvRingLauncherSide.RIGHT
        } else {
            FvRingLauncherSide.LEFT
        }
    }

    private fun hydrateTargetIcons(
        targets: List<com.slideindex.app.overlay.HoneycombRuntimeTarget?>,
        appRepository: AppRepository?,
    ) {
        if (appRepository == null) return
        targets.filterNotNull().forEach { target ->
            if (target.icon != null ||
                target.item.type != QuickLauncherItemType.APP ||
                target.item.payload.isBlank()
            ) {
                return@forEach
            }
            target.icon = appRepository.peekLaunchIconDrawable(
                target.item.appTarget?.packageName ?: target.item.payload
            )
        }
    }
}
