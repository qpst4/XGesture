package com.slideindex.app.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.view.MotionEvent
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.diagnostic.EdgeDiag
import com.slideindex.app.gesture.ActionExecutor
import com.slideindex.app.gesture.FloatBallOverlayAnchorPolicy
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureSession
import com.slideindex.app.gesture.GestureZoneLayout
import com.slideindex.app.gesture.PanelGridSession
import com.slideindex.app.gesture.SwipePathRecognizer
import com.slideindex.app.overlay.layout.GridLayoutInfo
import com.slideindex.app.overlay.layout.OverlayPanelLayoutHost
import com.slideindex.app.overlay.layout.QuickLauncherPanelLayoutEngine
import com.slideindex.app.overlay.layout.resolveLayoutSide
import com.slideindex.app.overlay.layout.visualColumn
import com.slideindex.app.launcher.QuickLauncherGridLogic
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.launcher.QuickLauncherPanel
import com.slideindex.app.launcher.QuickLauncherPanelDefaults
import com.slideindex.app.service.CreateShortcutTrampoline
import com.slideindex.app.service.QuickLauncherAddTrampoline
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.effectiveLongPressDurationMs
import com.slideindex.app.settings.resolvedLaunchPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

internal class QuickLauncherOverlayController(
    internal val host: Host
) {
    init {
        com.slideindex.app.ui.IconPlateModeState.addPlateModeListener {
            invalidateQuickLauncherDerivedCaches()
            OverlayIconLoader.clear()
            host.invalidate()
        }
        // 图标库里改了某个应用的图标：面板格子是 Canvas 绘制，没人观察 Compose 的 revision，
        // 必须主动清缓存 + 重绘，否则要退出再进才看得到。
        com.slideindex.app.ui.GlobalIconStore.addIconChangeListener {
            invalidateQuickLauncherDerivedCaches()
            OverlayIconLoader.clear()
            host.invalidate()
        }
    }

    private val motionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    internal val quickLauncherPageSnapMotion = OverlayFloatSpringMotion(motionScope)
    interface Host : OverlayPanelLayoutHost {
        val context: Context
        fun settings(): AppSettings
        override fun side(): PanelSide
        fun apps(): List<AppInfo>
        fun gestureSession(): GestureSession
        fun zoneLayout(): GestureZoneLayout
        fun pathRecognizer(): SwipePathRecognizer
        fun actionExecutor(): ActionExecutor
        fun panelGridSession(): PanelGridSession
        fun panelEnterProgress(): Float
        fun panelEnterAdjustedX(localX: Float, panel: RectF): Float
        fun panelEnterOffsetX(panel: RectF): Float
        fun panelContentRect(): RectF
        fun drawWithPanelEnterAnimation(canvas: Canvas, contentRect: RectF, drawContent: () -> Unit)
        override fun activeTriggerZoneRect(): RectF
        override fun viewWidth(): Int
        override fun viewHeight(): Int
        override fun dp(value: Float): Float
        fun sp(value: Float): Float
        fun viewLocationOnScreen(): IntArray
        fun overlayView(): android.view.View? = null
        fun invalidate()
        fun invalidatePartial(left: Int, top: Int, right: Int, bottom: Int)
        fun post(action: () -> Unit)
        fun postDelayed(runnable: Runnable, delayMs: Long)
        fun removeCallbacks(runnable: Runnable)
        fun hapticTick()
        fun hapticLongThreshold()
        fun hapticConfirmLaunch()
        fun startPanelExitAnimation(onEnd: () -> Unit)
        fun notifyPresentationTouchRequirementChanged()
        fun onQuickLauncherPanelItemsPersist(panelId: String, items: List<QuickLauncherItem>)
        fun onOverlayWindowSuspend()
        fun onOverlayWindowResume()
        fun clearEdgeCaptureTouchActive()
        fun onInitiatingEdgeGestureReleased()
        fun setPanelPresentationFocus(needsFocus: Boolean)
    }

    private val renderer = QuickLauncherRenderer(this)
    private val touchHandler = QuickLauncherTouchHandler(this)
    internal val folderHandler = QuickLauncherFolderHandler(this)

    internal val quickLauncherOverlayDialogHost = OverlayComposeDialogHost(
        context = host.context,
        themeSettings = { host.settings() }
    )
    internal val quickLauncherPanelController = QuickLauncherPanelController(
        object : QuickLauncherPanelController.Host {
            override val context: Context get() = host.context
            override fun settings(): AppSettings = host.settings()
            override fun activeQuickLauncherPanel(): QuickLauncherPanel = this@QuickLauncherOverlayController.activeQuickLauncherPanel()
            override fun side(): PanelSide = host.side()
            override fun apps(): List<AppInfo> = host.apps()
            override fun isPanelReady(): Boolean = host.panelEnterProgress() >= 1f
            override fun isAddDialogShowing(): Boolean =
                QuickLauncherAddTrampoline.isActive() || quickLauncherOverlayDialogHost.isShowing
            override fun dp(value: Float): Float = host.dp(value)
            override fun sp(value: Float): Float = host.sp(value)
            override fun invalidate() = host.invalidate()
            override fun hapticTick() = host.hapticTick()
            override fun showAddDialog(
                configuredAppPackages: Set<String>,
                configuredShortcutKeys: Set<String>,
                configuredActionKeys: Set<String>,
                onAdd: (QuickLauncherItem) -> Unit,
                onRemove: (QuickLauncherItem) -> Unit
            ) {
                val appDeps = dagger.hilt.android.EntryPointAccessors.fromApplication(
                    host.context.applicationContext,
                    com.slideindex.app.di.AppGraphEntryPoint::class.java
                ).dependencies()

                quickLauncherOverlayDialogHost.show(
                    onBackPressed = {
                        quickLauncherOverlayDialogHost.dismiss()
                        true
                    },
                    onDismiss = {
                        host.notifyPresentationTouchRequirementChanged()
                        host.invalidate()
                    }
                ) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        com.slideindex.app.ui.compose.LocalAppDependencies provides appDeps
                    ) {
                        com.slideindex.app.ui.QuickLauncherAddOverlaySheet(
                            panelSide = host.side(),
                            apps = host.apps(),
                            configuredAppPackages = configuredAppPackages,
                            configuredShortcutKeys = configuredShortcutKeys,
                            configuredActionKeys = configuredActionKeys,
                            activityShortcuts = host.settings().activityShortcuts,
                            shellCommands = host.settings().shellCommands,
                            onDismiss = {
                                quickLauncherOverlayDialogHost.dismiss()
                                host.notifyPresentationTouchRequirementChanged()
                            },
                            onAdd = onAdd,
                            onRemove = onRemove,
                            launchCreateShortcut = { createHost, onResult ->
                                CreateShortcutTrampoline.launch(
                                    context = host.context,
                                    host = createHost,
                                    onPrepare = {
                                        quickLauncherOverlayDialogHost.dismiss()
                                        dismissFromBack()
                                    },
                                    onResult = onResult
                                )
                            }
                        )
                    }
                }
                host.notifyPresentationTouchRequirementChanged()
            }
            override fun onPersist(items: List<QuickLauncherItem>) {
                invalidateQuickLauncherDerivedCaches()
                val panelId = host.gestureSession().quickLauncherPanelId()
                    .ifBlank { activeQuickLauncherPanel().id }
                host.onQuickLauncherPanelItemsPersist(panelId, items)
            }
            override fun isQuickLauncherVisible(): Boolean =
                host.gestureSession().panelMode() == OverlayPanelMode.QUICK_LAUNCHER
            override fun quickLauncherPageSize(): Int =
                this@QuickLauncherOverlayController.quickLauncherPageSize()
            override fun onEditDragMove(touchX: Float, localY: Float, panelRect: RectF) {
                touchHandler.applyEditDragAutoPage(touchX, panelRect)
            }
            override fun onEditDragBegan() {
                quickLauncherEdgeAutoPageSeeded = false
                quickLauncherEdgePageZone = 0
            }
            override fun resolveEditDragTargetGlobal(
                touchX: Float,
                localY: Float,
                panelRect: RectF
            ): Int = quickLauncherGlobalIndexAt(touchX, localY, panelRect)
            override fun postDelayed(runnable: Runnable, delayMs: Long) =
                host.postDelayed(runnable, delayMs)
            override fun removeCallbacks(runnable: Runnable) =
                host.removeCallbacks(runnable)
            override fun switchToNextPanel() =
                this@QuickLauncherOverlayController.switchToNextPanel()
            override fun hasMultiplePanels(): Boolean =
                this@QuickLauncherOverlayController.hasMultiplePanels()
            override fun currentPanelName(): String =
                this@QuickLauncherOverlayController.currentPanelName()
            override fun onFolderOpen(globalIndex: Int) {
                openFolder(globalIndex)
            }
            override fun onFolderItemsUpdated(globalIndex: Int, newChildren: List<QuickLauncherItem>) {
                if (folderOpen && folderGlobalIndex == globalIndex) {
                    updateActiveFolderChildren(newChildren)
                }
            }
        }
    )

    init {
        // 钉住态没有返回键兜底，工具栏「×」是唯一看得见的退出方式。
        quickLauncherPanelController.onCloseRequested = { requestClosePinned() }
    }

    internal var quickLauncherAnchorRawY: Float? = null
    /** 锚点来自外部触发（悬浮球 / 圆环入口等），不是边滑手势现场算出来的。 */
    internal var quickLauncherExternalAnchor: Boolean = false
    internal var quickLauncherFrozenAnchorLocalY: Float? = null
    internal var quickLauncherContinuousHapticIndex = -1
    internal var quickLauncherPressIndex = -1
    internal var quickLauncherPressDownTime = 0L
    internal var quickLauncherLongPressArmed = false
    internal var quickLauncherLongPressIndex = -1
    internal var quickLauncherLongPressRunnable: Runnable? = null
    internal var quickLauncherPageIndex = 0
    internal var quickLauncherPageCount = 1
    internal var quickLauncherPageSwipeStartX = 0f
    internal var quickLauncherPageSwipeStartY = 0f
    internal var quickLauncherPageSwipeTracking = false
    internal var quickLauncherPageSwipeLocked = false
    internal var quickLauncherPageChangedThisGesture = false
    internal var quickLauncherPageDragOffset = 0f
    internal var quickLauncherLaunchEndDeferMs = 0L
    internal var quickLauncherExiting = false
    internal var quickLauncherOpeningGestureActive = false
    internal var quickLauncherToolbarTouchActive = false
    /** 「长按面板空白处进入编辑态」的按下时刻；<= 0 表示当前没有按住空白区。 */
    internal var quickLauncherBlankPressDownTime = 0L
    internal var quickLauncherBlankLongPressRunnable: Runnable? = null
    /** 本次手势已经被「长按空白进编辑」消费：松手时不能再当成"点空白关闭面板"。 */
    internal var quickLauncherBlankLongPressConsumed = false
    /** -1 = outer edge, 0 = middle, 1 = inner edge; used for continuous edge auto-page. */
    internal var quickLauncherEdgePageZone = 0
    internal var quickLauncherEdgeAutoPageSeeded = false
    internal var quickLauncherAppsByPackage: Map<String, AppInfo> = emptyMap()
    /** 应用列表换了要换图标 key（图标本身缓存在 [OverlayIconLoader]，跨会话保留）。 */
    internal var quickLauncherAppsRevision: Int = 0
        private set
    internal val quickLauncherLabelCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    internal var quickLauncherCachedPages: List<List<QuickLauncherItem>>? = null
    internal var quickLauncherCachedPagesKey: Int = 0
    internal var quickLauncherLayoutPanelWidth: Float = 0f

    // Folder Sub-Panel State
    internal var folderOpen: Boolean = false
    internal var folderGestureActive: Boolean = false
    internal var folderGlobalIndex: Int = -1
    internal var folderItem: QuickLauncherItem? = null
    internal var folderSubPanelItems: List<QuickLauncherItem> = emptyList()
    internal var folderHighlightLocalIndex: Int = -1
    internal val folderRect = RectF()
    /** Global child index in [folderSubPanelItems] to cell bounds on the current folder page. */
    internal val folderCellBounds = mutableListOf<Pair<Int, RectF>>()
    internal val folderCloseButtonBounds = RectF()
    internal val folderAddButtonBounds = RectF()
    internal var folderHoverRunnable: Runnable? = null
    internal var folderPressLocalIndex: Int = -1
    internal var folderPressDownTime: Long = 0L
    internal var folderLongPressArmed: Boolean = false
    internal var folderLongPressRunnable: Runnable? = null

    fun openFolder(globalIndex: Int) {
        val items = quickLauncherRootItems()
        val item = items.getOrNull(globalIndex) ?: return
        if (item.type != QuickLauncherItemType.FOLDER) return
        folderGlobalIndex = globalIndex
        folderItem = item
        folderSubPanelItems = item.folderItems()
        folderOpen = true
        folderHighlightLocalIndex = -1
        folderHandler.reset()
        cancelFolderHover()
        cancelFolderLongPress()
        quickLauncherLongPressRunnable?.let { host.removeCallbacks(it) }
        quickLauncherLongPressRunnable = null
        quickLauncherLongPressIndex = -1
        quickLauncherLongPressArmed = false
        host.hapticTick()
        host.invalidate()
    }

    fun closeFolder() {
        if (!folderOpen) return
        folderOpen = false
        folderGlobalIndex = -1
        folderItem = null
        folderSubPanelItems = emptyList()
        folderHighlightLocalIndex = -1
        folderCellBounds.clear()
        folderCloseButtonBounds.setEmpty()
        folderAddButtonBounds.setEmpty()
        folderHandler.reset()
        cancelFolderHover()
        cancelFolderLongPress()
        host.invalidate()
    }

    fun cancelFolderHover() {
        folderHoverRunnable?.let { host.removeCallbacks(it) }
        folderHoverRunnable = null
    }

    fun cancelFolderLongPress() {
        folderLongPressRunnable?.let { host.removeCallbacks(it) }
        folderLongPressRunnable = null
        folderLongPressArmed = false
        folderPressLocalIndex = -1
        folderPressDownTime = 0L
    }

    /**
     * 非编辑态在**面板空白处**按下：起一个长按计时，到点就进编辑态。
     *
     * 空白处原本没有任何长按语义（短按松手是"关闭面板"，见 [QuickLauncherTouchHandler]），
     * 所以这里不会和「长按图标 = 启动形态切换」「长按文件夹 = 展开」「编辑态长按 = 拖拽排序」
     * 三处既有长按打架。时长沿用设置里的 `effectiveLongPressDurationMs()`，与其它长按一致。
     */
    fun scheduleBlankLongPress(eventTime: Long) {
        cancelBlankLongPress()
        if (quickLauncherPanelController.editMode) return
        val holdMs = host.settings().effectiveLongPressDurationMs().toLong()
        quickLauncherBlankPressDownTime = eventTime
        val runnable = Runnable {
            if (quickLauncherBlankPressDownTime <= 0L) return@Runnable
            if (quickLauncherPanelController.editMode) return@Runnable
            quickLauncherBlankLongPressConsumed = true
            quickLauncherBlankPressDownTime = 0L
            quickLauncherPanelController.setEditMode(true)
            host.hapticConfirmLaunch()
            host.invalidate()
        }
        quickLauncherBlankLongPressRunnable = runnable
        host.postDelayed(runnable, holdMs)
    }

    fun cancelBlankLongPress() {
        quickLauncherBlankLongPressRunnable?.let { host.removeCallbacks(it) }
        quickLauncherBlankLongPressRunnable = null
        quickLauncherBlankPressDownTime = 0L
    }

    /** 本次手势是否已被「长按空白进编辑」消费（松手时不能再当成点空白关闭面板）。 */
    fun consumeBlankLongPress(): Boolean {
        val consumed = quickLauncherBlankLongPressConsumed
        quickLauncherBlankLongPressConsumed = false
        cancelBlankLongPress()
        return consumed
    }

    fun scheduleFolderLongPress(localIndex: Int, eventTime: Long) {
        cancelFolderLongPress()
        val item = folderSubPanelItems.getOrNull(localIndex) ?: return
        if (item.type == QuickLauncherItemType.ACTION || item.type == QuickLauncherItemType.FOLDER) return
        if (!host.settings().freeWindowEnabled || !host.settings().resolvedLaunchPolicy().usesLongPress()) return
        folderPressLocalIndex = localIndex
        folderPressDownTime = eventTime
        val runnable = Runnable {
            if (folderOpen && folderHighlightLocalIndex == localIndex) {
                folderLongPressArmed = true
                host.hapticLongThreshold()
                host.invalidate()
            }
        }
        folderLongPressRunnable = runnable
        host.postDelayed(runnable, host.settings().effectiveLongPressDurationMs().toLong())
    }

    fun isFolderLongPressTriggered(event: MotionEvent): Boolean {
        if (folderLongPressArmed) return true
        val item = folderSubPanelItems.getOrNull(folderHighlightLocalIndex) ?: return false
        if (item.type == QuickLauncherItemType.ACTION || item.type == QuickLauncherItemType.FOLDER) return false
        if (!host.settings().freeWindowEnabled || !host.settings().resolvedLaunchPolicy().usesLongPress()) return false
        if (folderPressLocalIndex < 0 || folderPressLocalIndex != folderHighlightLocalIndex) return false
        return event.eventTime - folderPressDownTime >= host.settings().effectiveLongPressDurationMs()
    }

    fun updateActiveFolderChildren(newChildren: List<QuickLauncherItem>) {
        val globalIdx = folderGlobalIndex
        if (globalIdx < 0) return
        quickLauncherPanelController.updateFolderItems(globalIdx, newChildren)
        folderItem = quickLauncherRootItems().getOrNull(globalIdx)
        folderSubPanelItems = newChildren
        folderHandler.layout?.let { layout ->
            folderHandler.syncPagination(newChildren.size, layout.pageSize)
        }
        invalidateQuickLauncherDerivedCaches()
        host.invalidate()
    }

    /**
     * 行高：显示名称时保留 64dp 的标签空间；关掉名称后收到与列宽一致，
     * 让图标的纵向间隙等于横向间隙（见 [QuickLauncherPanelLayoutEngine.cellHeight]）。
     */
    internal val quickLauncherCellHeight get() = QuickLauncherPanelLayoutEngine.cellHeight(
        showLabels = host.settings().quickLauncherDisplay.showLabels,
        cellWidth = quickLauncherCellWidth,
        labeledCellHeight = host.dp(64f),
    )
    internal val quickLauncherCellWidth get() = host.dp(56f)
    internal val quickLauncherGridPadding get() = host.dp(8f)
    internal val quickLauncherHeaderHeight get() = 0f

    fun handleTouch(event: MotionEvent, localX: Float, localY: Float): Boolean =
        touchHandler.handleTouch(event, localX, localY)

    fun draw(canvas: Canvas, drawToolbar: Boolean = true) {
        // 工具栏要跟着「钉住」状态多画一个「×」（钉住态返回键不再负责关闭，得给一个可见出口）。
        quickLauncherPanelController.pinned = host.gestureSession().quickLauncherPinned()
        renderer.draw(canvas, drawToolbar)
    }

    /** 当前面板是否处于钉住态（下发给工具栏 / 触控判断）。 */
    fun isPinned(): Boolean = host.gestureSession().quickLauncherPinned()

    /**
     * 钉住面板的窗口矩形（屏幕坐标）；不该缩窗时返回 null。
     *
     * 只有「钉住 + 手指已抬起 + 面板还停在屏上」这一档才缩窗：缩窗后窗口不再盖住整屏，
     * 面板外的触摸才会落到下面的应用上 —— 这就是「钉住之后还能操作屏幕」的实现方式。
     * 呼出动画 / 手势跟手 / 收起动画 / 面板内弹窗 / 编辑拖动分页都仍走全屏窗，避免坐标系跨界。
     */
    fun pinnedWindowRect(screenWidthPx: Float, screenHeightPx: Float): RectF? {
        val session = host.gestureSession()
        if (!session.quickLauncherPinnedIdle()) return null
        if (quickLauncherExiting) return null
        if (quickLauncherOverlayDialogHost.isShowing) return null
        if (quickLauncherPanelController.editMode) return null
        if (screenWidthPx <= 0f || screenHeightPx <= 0f) return null
        // ⚠️ 进入钉住空闲的第一时间把面板落位**整块矩形**钉死。
        //
        // 只钉锚点不够：落位算法还有「对齐触发带 / 以锚点居中」两条分支（由 activeTriggerZoneRect
        // 与外部触发锚点决定），而这两样在呼出之后会变 —— 于是同一锚点算出来的矩形会整体平移
        // （真机日志：呼出时 (348,918)，重算后 (332,829)，宽高完全一致）。
        // 钉住期间一律用冻结的那一块，点面板外 / 切应用 / 设置刷新都不会再让面板跳。
        if (pinnedIdlePanelRect == null) {
            pinnedIdleAnchorLocalY = quickLauncherAnchorLocalY()
            val frozenRect = computeQuickLauncherPanelRect()
            pinnedIdlePanelRect = frozenRect
            EdgeDiag.log(
                "qlPin",
                "钉住落位锁定 anchorLocalY=$pinnedIdleAnchorLocalY panel=$frozenRect",
            )
        }
        val panelRect = pinnedIdlePanelRect ?: return null
        if (panelRect.isEmpty) return null
        val content = quickLauncherPanelController.combinedContentRect(panelRect)
        if (content.isEmpty) return null
        val margin = host.dp(PINNED_WINDOW_MARGIN_DP)
        val left = (content.left - margin).coerceIn(0f, screenWidthPx - 1f)
        val top = (content.top - margin).coerceIn(0f, screenHeightPx - 1f)
        val right = (content.right + margin).coerceIn(left + 1f, screenWidthPx)
        val bottom = (content.bottom + margin).coerceIn(top + 1f, screenHeightPx)
        val rect = RectF(left, top, right, bottom)
        if (rect != lastPinnedWindowRect) {
            lastPinnedWindowRect = rect
            EdgeDiag.log("qlPin", "钉住窗口 rect=$rect panel=$panelRect")
        }
        return rect
    }

    /** 最近一次下发的钉住窗口矩形（只用于日志去重）。 */
    private var lastPinnedWindowRect: RectF? = null

    /**
     * 钉住空闲期间**冻结的面板矩形**。
     *
     * 落位算法有两条分支（对齐触发带 / 以锚点居中），呼出之后触发带与外部锚点都会变，
     * 同一锚点也能算出平移过的矩形 —— 钉住期间一律用这一块，面板才「纹丝不动」。
     */
    private var pinnedIdlePanelRect: RectF? = null

    /**
     * 钉住空闲期间的「冻结锚点」。
     *
     * 面板一进入钉住空闲就锁定当时的落位，此后窗口怎么重算都不再重新求解 —— 见 [pinnedWindowRect]。
     */
    private var pinnedIdleAnchorLocalY: Float? = null

    private companion object {
        /** 缩窗时在面板外围留一点余量，避免面板边缘的抗锯齿 / 阴影被窗口边界裁掉。 */
        const val PINNED_WINDOW_MARGIN_DP = 6f

        /** 钉住期间的前台包名轮询间隔（只读一个静态字段，开销可忽略）。 */
        const val FOREGROUND_POLL_INTERVAL_MS = 400L

        /** 判定「切走应用」需要的连续命中次数（键盘 / 系统窗口的瞬时前台会被过滤掉）。 */
        const val FOREGROUND_CONFIRM_HITS = 2
    }

    // ── 钉住 +「仅在本应用」：切走自动隐藏、切回原样恢复 ─────────────────────────

    /** 钉住那一刻的前台包名（「仅在本应用」的归属）。 */
    private var pinnedOwnerPackage: String = ""

    /** 恢复面板用的主线程 Handler（视图 detach 后 View.post 不执行，见 restorePinnedAfterAppSwitch）。 */
    private val restoreHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /** 因为切走而暂时隐藏：记住它是谁、在哪里，等归属应用回来再原样打开。 */
    private var hiddenByAppSwitch: Boolean = false
    private var hiddenPanelId: String = ""
    private var hiddenAnchorRawY: Float = 0f

    /**
     * 前台包名轮询（钉住 +「仅本应用」的唯一可靠触发源）。
     *
     * 事件链（`OverlayManager.updateForegroundPackage` → 各侧 controller）在真机上不一定被调到：
     * 日志里只有「钉住归属」、一次「切走应用」都没有 —— 于是面板切走也不隐藏。
     * 这里在钉住期间直接比对那个本来就常驻的 `OverlayService.foregroundPackage`：
     * 400ms 一次、只读一个静态字段，开销可忽略，且不依赖任何回调链。
     */
    private var lastPolledForegroundPackage: String = ""
    private var foregroundPollScheduled = false

    /** 「切走应用」的连续确认：同一个非归属包名要连续命中这么多次才认。 */
    private var pendingForegroundPackage: String = ""
    private var pendingForegroundHits: Int = 0

    private val foregroundPollTick = Runnable {
        foregroundPollScheduled = false
        val pkg = com.slideindex.app.service.OverlayService.foregroundPackage.orEmpty()
        if (pkg.isNotBlank() && pkg != lastPolledForegroundPackage) {
            lastPolledForegroundPackage = pkg
            EdgeDiag.log("qlPin", "轮询到前台变化 → $pkg")
            onForegroundPackageChanged(pkg)
        }
        scheduleForegroundPolling()
    }

    private fun shouldPollForeground(): Boolean {
        val session = host.gestureSession()
        return hiddenByAppSwitch ||
            (session.quickLauncherPinned() && session.quickLauncherPinCurrentApp())
    }

    /** 幂等：需要时保证有一个在途的轮询。 */
    private fun scheduleForegroundPolling() {
        host.removeCallbacks(foregroundPollTick)
        foregroundPollScheduled = false
        if (!shouldPollForeground()) return
        host.postDelayed(foregroundPollTick, FOREGROUND_POLL_INTERVAL_MS)
        foregroundPollScheduled = true
    }

    /**
     * 前台应用变化：只有「钉住 + 仅本应用」才关心。
     *
     * 隐藏/恢复都要走**本侧**（面板当初就画在这一侧）；恢复用
     * [SlideIndexAccessibilityService.dispatchExternalGestureAction]，它会按需挂面板专用宿主 ——
     * 该侧没有触钮时也能把面板画出来（直接调本侧 view 的方式在无触钮侧会静默失败）。
     */
    fun onForegroundPackageChanged(packageName: String?) {
        val pkg = packageName.orEmpty()
        if (pkg.isBlank()) return
        val session = host.gestureSession()
        val panelVisible = session.panelMode() == OverlayPanelMode.QUICK_LAUNCHER
        if (hiddenByAppSwitch) {
            // 隐藏期间会话已经没了，钉住标记也随之清空 → 这里只看归属包名决定要不要恢复。
            if (pkg == pinnedOwnerPackage) {
                resetForegroundConfirm()
                restorePinnedAfterAppSwitch()
            }
            return
        }
        if (!session.quickLauncherPinned() || !session.quickLauncherPinCurrentApp()) return
        if (pinnedOwnerPackage.isEmpty()) return
        if (pkg == pinnedOwnerPackage) {
            resetForegroundConfirm()
            return
        }
        if (!panelVisible) return
        // ⚠️ 弹出键盘时前台包名会闪成输入法 / 系统界面包名（真机日志：com.meizu.assistant 之类）。
        // 键盘是叠加窗口，宿主 App 其实没变 —— 这时候绝不能当成"切走"，否则一打字面板就消失。
        if (isPinnedForegroundNoise(pkg)) return
        // 再要求"同一个包名连续命中两次"（≈0.8s）才认，避免一闪而过的系统窗口把面板收掉。
        if (pkg == pendingForegroundPackage) {
            pendingForegroundHits++
        } else {
            pendingForegroundPackage = pkg
            pendingForegroundHits = 1
        }
        if (pendingForegroundHits < FOREGROUND_CONFIRM_HITS) return
        resetForegroundConfirm()
        hidePinnedForAppSwitch(session.quickLauncherPanelId())
    }

    private fun resetForegroundConfirm() {
        pendingForegroundPackage = ""
        pendingForegroundHits = 0
    }

    /**
     * 这些包名不算"用户切走了 App"：输入法、系统界面、助手的瞬时前台。
     *
     * 它们都是**叠加在宿主 App 之上**的窗口，宿主仍然在下面；把它们当成切走，
     * 会让"在原本的 App 里点一下输入框"直接把钉住面板收掉。
     */
    private fun isPinnedForegroundNoise(pkg: String): Boolean {
        if (pkg == "com.android.systemui") return true
        // 键盘可见时，前台包名的抖动一律不算切走（宿主没变）。
        if (KeyboardTriggerImeState.imeTopOrNull() != null) return true
        return false
    }

    private fun hidePinnedForAppSwitch(panelId: String) {
        if (panelId.isBlank()) return
        hiddenByAppSwitch = true
        hiddenPanelId = panelId
        hiddenAnchorRawY = rememberRestoreAnchorRawY()
        EdgeDiag.log("qlPin", "切走应用 → 暂时隐藏钉住面板 panelId=$panelId")
        requestClosePinned()
    }

    /**
     * 记下"回来时按哪个高度恢复"。
     *
     * 优先用外部触发锚点（悬浮球位置）；没有就用面板中心 —— 绝不能用 0，
     * 否则恢复出来的面板会贴到屏幕最上面。
     */
    private fun rememberRestoreAnchorRawY(): Float {
        quickLauncherAnchorRawY?.takeIf { it > 0f }?.let { return it }
        val rect = quickLauncherPanelRect()
        return if (rect.isEmpty) 0f else rect.centerY()
    }

    /**
     * 钉住面板里的条目**打开了别的 App** → 面板要收起，但必须留住「回来就恢复」的信息。
     *
     * 与「切走应用」是同一件事的两种触发：用户注意力离开了，面板该让位；回到归属应用时再原样摆回来。
     * 必须在 [GestureSession.endSession] 之前调用（它会清归属 / 恢复信息）。
     */
    fun recordPinnedHiddenByLaunch() {
        val session = host.gestureSession()
        if (!session.quickLauncherPinned() || !session.quickLauncherPinCurrentApp()) return
        val panelId = session.quickLauncherPanelId()
        if (panelId.isBlank()) return
        if (pinnedOwnerPackage.isBlank()) {
            pinnedOwnerPackage =
                com.slideindex.app.service.OverlayService.foregroundPackage.orEmpty()
        }
        hiddenByAppSwitch = true
        hiddenPanelId = panelId
        hiddenAnchorRawY = rememberRestoreAnchorRawY()
        EdgeDiag.log(
            "qlPin",
            "面板里启动了别的 App → 记下待恢复 panelId=$panelId owner=$pinnedOwnerPackage " +
                "anchorY=$hiddenAnchorRawY",
        )
    }

    private fun restorePinnedAfterAppSwitch() {
        val panelId = hiddenPanelId
        val anchorRawY = hiddenAnchorRawY
        hiddenByAppSwitch = false
        hiddenPanelId = ""
        if (panelId.isBlank()) return
        val side = host.side()
        EdgeDiag.log("qlPin", "回到归属应用 → 恢复钉住面板 panelId=$panelId side=$side")
        // ⚠️ 不能用 host.post（= View.post）：面板隐藏后视图通常已 detach，
        // detached view 的 runnable 会挂在队列里等 attach，永远不执行（真机表现：恢复毫无反应、
        // 连失败日志都没有）。这里直接用主线程 Handler。
        restoreHandler.post {
            // ⚠️ 必须走 OverlayManager 那条「外部触发」路径：它会在该侧没有触钮时按需挂一扇
            // 面板专用宿主。直接调本侧 view 的 dispatchExternalAction 在无触钮侧会静默失败
            //（真机日志：外部触发面板失败：LEFT 侧无触钮且面板专用宿主挂不上）。
            val restored = com.slideindex.app.service.SlideIndexAccessibilityService
                .dispatchExternalGestureAction(
                    action = GestureAction.QuickLauncher(
                        panelId = panelId,
                        pinned = true,
                        pinCurrentApp = true,
                    ),
                    anchorRawY = anchorRawY,
                    panelSide = side,
                )
            if (!restored) {
                EdgeDiag.log("qlPin", "恢复失败（该侧挂不上画布），放弃本次恢复 side=$side")
            }
        }
    }

    /** 记录钉住归属（只在「钉住 + 仅本应用」且第一次钉住时抓一次）。 */
    private fun capturePinnedOwnerIfNeeded() {
        val session = host.gestureSession()
        if (!session.quickLauncherPinned() || !session.quickLauncherPinCurrentApp()) return
        if (pinnedOwnerPackage.isNotBlank()) return
        pinnedOwnerPackage = com.slideindex.app.service.OverlayService.foregroundPackage.orEmpty()
        EdgeDiag.log("qlPin", "钉住归属 = $pinnedOwnerPackage")
    }

    private val pinnedScreenOffReceiver = ScreenOffDismissReceiver { requestClosePinned() }

    private fun syncPinnedScreenOffReceiver() {
        val pinned = host.gestureSession().quickLauncherPinned()
        if (pinned) {
            pinnedScreenOffReceiver.register(host.context)
        } else {
            pinnedScreenOffReceiver.unregister()
        }
    }

    /**
     * 显式关闭面板（工具栏「×」/ 锁屏 / 切走应用等）。
     *
     * 与「点面板外」无关：钉住态下点面板外不关，只有这里和「再划一次同一手势」才关。
     */
    fun requestClosePinned() = dismissFromBack()

    fun panelRect(): RectF = quickLauncherPanelRect()

    fun enterContentRect(): RectF {
        val panelRect = quickLauncherPanelRect()
        return quickLauncherPanelController.combinedContentRect(panelRect)
    }

    fun isExiting(): Boolean = quickLauncherExiting

    fun isOverlayDialogShowing(): Boolean =
        QuickLauncherAddTrampoline.isActive() || quickLauncherOverlayDialogHost.isShowing

    fun isComposeOverlayDialogShowing(): Boolean = quickLauncherOverlayDialogHost.isShowing

    fun syncOverlayDialogZOrder() {
        if (quickLauncherOverlayDialogHost.isShowing) {
            quickLauncherOverlayDialogHost.bringToFront()
        }
    }

    fun syncSettings(settings: AppSettings) {
        quickLauncherPanelController.syncSettings(settings)
        renderer.syncSettings(settings)
        invalidateQuickLauncherDerivedCaches()
    }

    fun setApps(apps: List<AppInfo>) {
        rebuildQuickLauncherAppsByPackage(apps)
        quickLauncherAppsRevision++
        invalidateQuickLauncherDerivedCaches()
    }

    fun invalidateDerivedCaches() {
        invalidateQuickLauncherDerivedCaches()
    }

    fun onSizeChanged() {
        // ⚠️ 这里**不能**清钉住冻结：钉住缩窗就是我们自己改窗口尺寸触发的，
        // 清掉 → 下一次重算换个落位 → 再缩窗再清 → 真机表现就是「面板一跳一跳」。
        quickLauncherLayoutPanelWidth = 0f
    }

    /**
     * 真正的**屏幕**尺寸变化（旋转 / 分屏 / 分辨率切换）。
     *
     * 只有这种变化才该作废钉住冻结；窗口因钉住而缩小/恢复全屏不算。
     */
    fun onScreenSizeChanged() {
        pinnedIdlePanelRect = null
        pinnedIdleAnchorLocalY = null
        pinnedLayoutSnapshot = null
        lastPinnedWindowRect = null
        lastLoggedPanelRect = null
    }

    /**
     * 进场动画结束 / 落位已定 → 冻结钉住面板的位置。
     *
     * 冻结发生在**呼出完成那一刻**（而不是第一次缩窗时），这样从"看得见"到"钉住"之间
     * 位置也不会变一下。
     */
    fun freezePinnedLayout() {
        if (!host.gestureSession().quickLauncherPinned()) return
        if (pinnedIdlePanelRect != null) return
        // 锚点优先用「呼出过程中记下的快照」：它和用户看到的落位是同一套输入。
        pinnedIdleAnchorLocalY = pinnedLayoutSnapshot?.anchorLocalY ?: quickLauncherAnchorLocalY()
        pinnedIdlePanelRect = computeQuickLauncherPanelRect()
        lastPinnedWindowRect = null
        EdgeDiag.log(
            "qlPin",
            "冻结落位 anchorLocalY=$pinnedIdleAnchorLocalY panel=$pinnedIdlePanelRect " +
                "bandSnapshot=${pinnedLayoutSnapshot?.trigger} active=${host.gestureSession().isActive()}",
        )
    }

    fun setAnchorRawY(rawY: Float?) {
        quickLauncherAnchorRawY = rawY
        quickLauncherExternalAnchor = rawY != null
    }

    fun onSessionStart() {
        quickLauncherPageIndex = 0
        quickLauncherPageCount = 1
        quickLauncherPageSwipeTracking = false
        quickLauncherPageSwipeLocked = false
        quickLauncherPageChangedThisGesture = false
        quickLauncherPageSnapMotion.cancel()
        quickLauncherPageDragOffset = 0f
        quickLauncherExiting = false
        quickLauncherOpeningGestureActive = true
        quickLauncherEdgePageZone = 0
        quickLauncherEdgeAutoPageSeeded = false
        cancelBlankLongPress()
        quickLauncherPanelController.ensureDefaultsPersisted(host.settings())
        renderer.prewarm(quickLauncherPrewarmOrder())
        quickLauncherAnchorRawY = quickLauncherAnchorRawY
            ?.takeIf { it > 0f }
            ?: host.pathRecognizer().lastRawY().takeIf { it > 0f }
            ?: host.pathRecognizer().gestureStartRawY().takeIf { it > 0f }
    }

    /**
     * 后台取图顺序：当前页 → 下一页 → 上一页 → 其余。
     * 用户翻到第二页时那一页的图标已经是「正在解析/已就绪」，而不是刚开始排队。
     */
    private fun quickLauncherPrewarmOrder(): List<QuickLauncherItem> {
        val pages = quickLauncherPages()
        if (pages.size <= 1) return pages.flatten()
        val current = quickLauncherPageIndex.coerceIn(0, pages.size - 1)
        val ordered = buildList {
            add(pages[current])
            pages.getOrNull(current + 1)?.let { add(it) }
            pages.getOrNull(current - 1)?.let { add(it) }
            pages.forEachIndexed { index, page ->
                if (index != current && index != current + 1 && index != current - 1) add(page)
            }
        }
        return ordered.flatten()
    }

    fun onLayoutReady() {
        if (!host.gestureSession().quickLauncherContinuousPickActive()) {
            quickLauncherFrozenAnchorLocalY = resolveQuickLauncherAnchorLocalY()
        }
    }

    fun onSessionEnd() {
        // 面板真的收起了：锁屏监听撤掉。归属包名 / 恢复信息只在「不是因切走而隐藏」时才清 ——
        // 因切走隐藏的那一次必须留着它，归属应用回来才能原样恢复。
        pinnedScreenOffReceiver.unregister()
        // 因切走而隐藏的那一次要继续盯前台包名，等归属应用回来把面板恢复回去。
        scheduleForegroundPolling()
        // 钉住落位锁定 / 窗口矩形记录随会话一起作废：下一次呼出要按新的触发位置重新落位。
        pinnedIdleAnchorLocalY = null
        pinnedIdlePanelRect = null
        pinnedLayoutSnapshot = null
        lastPinnedWindowRect = null
        lastLoggedPanelRect = null
        if (!hiddenByAppSwitch) {
            hiddenPanelId = ""
            pinnedOwnerPackage = ""
        }
        quickLauncherAnchorRawY = null
        quickLauncherExternalAnchor = false
        quickLauncherFrozenAnchorLocalY = null
        quickLauncherContinuousHapticIndex = -1
        quickLauncherPressIndex = -1
        quickLauncherPressDownTime = 0L
        quickLauncherPageIndex = 0
        quickLauncherPageCount = 1
        quickLauncherPageSwipeTracking = false
        quickLauncherPageSwipeLocked = false
        quickLauncherPageChangedThisGesture = false
        quickLauncherPageSnapMotion.cancel()
        quickLauncherPageDragOffset = 0f
        quickLauncherLaunchEndDeferMs = 0L
        quickLauncherOpeningGestureActive = false
        quickLauncherEdgePageZone = 0
        quickLauncherEdgeAutoPageSeeded = false
        quickLauncherExiting = false
        quickLauncherLongPressRunnable?.let { host.removeCallbacks(it) }
        quickLauncherLongPressRunnable = null
        quickLauncherLongPressIndex = -1
        quickLauncherLongPressArmed = false
        cancelBlankLongPress()
        closeFolder()
        quickLauncherPanelController.reset()
        quickLauncherOverlayDialogHost.dismiss()
        activePanelId = null
        host.setPanelPresentationFocus(false)
        invalidateQuickLauncherDerivedCaches()
    }

    fun onPanelEnterAnimationEnded() {
        // 面板落位已经定了：钉住的面板从这一刻起冻结落位，之后点面板外 / 切应用 / 窗口重算
        // 都不再重算位置（重算会走另一条落位分支，panel 会整体平移）。
        freezePinnedLayout()
        host.notifyPresentationTouchRequirementChanged()
        if (!host.gestureSession().isMoveTimeActionLocked() ||
            host.gestureSession().quickLauncherContinuousPickActive()
        ) {
            quickLauncherOpeningGestureActive = false
        }
        // leave-open 面板需要可聚焦，否则 Back 会落到下层 Activity（尤其 XGesture 自身）。
        // ⚠️ 钉住态例外：钉住的目的是「一边用面板一边在下面的 App 里打字 / 点按」，
        // 一旦抢焦点，输入法就跟到浮层上，下面的 App 再也收不到字。
        capturePinnedOwnerIfNeeded()
        syncPinnedScreenOffReceiver()
        // 钉住 +「仅本应用」：从这里开始盯前台包名（事件链不可靠，见 scheduleForegroundPolling）。
        if (shouldPollForeground()) {
            lastPolledForegroundPackage =
                com.slideindex.app.service.OverlayService.foregroundPackage.orEmpty()
        }
        scheduleForegroundPolling()
        if (!host.gestureSession().quickLauncherPinned() &&
            !host.gestureSession().quickLauncherContinuousPickActive() &&
            !host.gestureSession().isMoveTimeActionLocked()
        ) {
            host.setPanelPresentationFocus(true)
        }
    }

    fun handleBackPress(): Boolean {
        if (host.gestureSession().panelMode() != OverlayPanelMode.QUICK_LAUNCHER) return false
        if (quickLauncherOverlayDialogHost.isShowing) {
            quickLauncherOverlayDialogHost.dismiss()
            return true
        }
        if (QuickLauncherAddTrampoline.isActive()) return false
        if (folderOpen) {
            closeFolder()
            return true
        }
        if (quickLauncherPanelController.editMode) {
            quickLauncherPanelController.setEditMode(false)
            host.invalidate()
            return true
        }
        dismissFromBack()
        return true
    }

    private fun dismissFromBack() {
        if (quickLauncherExiting) return
        if (host.gestureSession().panelMode() != OverlayPanelMode.QUICK_LAUNCHER) {
            host.gestureSession().endSession()
            return
        }
        quickLauncherExiting = true
        host.setPanelPresentationFocus(false)
        host.notifyPresentationTouchRequirementChanged()
        host.startPanelExitAnimation {
            quickLauncherExiting = false
            host.gestureSession().endSession()
        }
    }

    private var activePanelId: String? = null

    internal fun activeQuickLauncherPanel(): QuickLauncherPanel {
        val panelId = host.gestureSession().quickLauncherPanelId()
        if (panelId != activePanelId) {
            activePanelId = panelId
            invalidateQuickLauncherDerivedCaches()
            quickLauncherPageIndex = 0
            quickLauncherPanelController.onActivePanelChanged()
        }
        return QuickLauncherPanelDefaults.resolvePanel(host.settings().quickLauncherPanels, panelId)
    }

    fun hasMultiplePanels(): Boolean =
        QuickLauncherPanelDefaults.effectivePanels(host.settings().quickLauncherPanels).size > 1

    fun currentPanelName(): String =
        activeQuickLauncherPanel().name.ifBlank {
            val panels = QuickLauncherPanelDefaults.effectivePanels(host.settings().quickLauncherPanels)
            val idx = panels.indexOfFirst { it.id == activeQuickLauncherPanel().id }
            if (idx >= 0) {
                host.context.getString(R.string.quick_launcher_panel_named, idx + 1)
            } else {
                host.context.getString(R.string.gesture_action_quick_launcher)
            }
        }

    fun switchToNextPanel() {
        val panels = QuickLauncherPanelDefaults.effectivePanels(host.settings().quickLauncherPanels)
        if (panels.size <= 1) return
        val currentId = activeQuickLauncherPanel().id
        val currentIndex = panels.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
        val nextPanel = panels[(currentIndex + 1) % panels.size]
        host.gestureSession().setQuickLauncherPanelId(nextPanel.id)
        activePanelId = nextPanel.id
        closeFolder()
        invalidateQuickLauncherDerivedCaches()
        quickLauncherPageIndex = 0
        quickLauncherPanelController.onActivePanelChanged()
        host.hapticTick()
        host.invalidate()
    }

    internal fun quickLauncherColumnsPerPage(): Int =
        activeQuickLauncherPanel().columnsPerPage.coerceIn(2, QuickLauncherPanelLayoutEngine.MAX_COLUMNS)

    internal fun quickLauncherRowsPerPage(): Int =
        activeQuickLauncherPanel().rowsPerPage.coerceIn(2, QuickLauncherPanelLayoutEngine.MAX_ROWS)

    internal fun quickLauncherPageSize(): Int =
        quickLauncherColumnsPerPage() * quickLauncherRowsPerPage()

    internal fun quickLauncherRootItems(): List<QuickLauncherItem> =
        quickLauncherPanelController.displayItems(host.settings(), activeQuickLauncherPanel())

    internal fun quickLauncherItemCacheKey(item: QuickLauncherItem): String =
        "${item.type.id}\u0000${item.payload}"

    internal fun rebuildQuickLauncherAppsByPackage(apps: List<AppInfo> = host.apps()) {
        // 同一个包可能同时有主实例和分身（cloneUserId > 0），而快速启动器条目的 payload
        // 只有包名、启动的也是当前用户的主实例：这里必须优先保留主实例，
        // 否则主实例的图标/名字会被分身的「· 分身」覆盖。
        quickLauncherAppsByPackage = apps
            .groupBy { it.packageName }
            .mapValues { (_, candidates) ->
                candidates.firstOrNull { it.cloneUserId == 0 } ?: candidates.first()
            }
    }

    internal fun invalidateQuickLauncherDerivedCaches() {
        // 图标缓存在 OverlayIconLoader 里按 key 保留（key 含图标形状 / 尺寸 / 应用列表版本），
        // 这里只清便宜的派生数据；否则每次开关面板都要重新打一遍 PackageManager。
        quickLauncherLabelCache.clear()
        quickLauncherCachedPages = null
        quickLauncherCachedPagesKey = 0
        quickLauncherLayoutPanelWidth = 0f
    }

    internal fun quickLauncherPages(): List<List<QuickLauncherItem>> {
        val panel = activeQuickLauncherPanel()
        val root = quickLauncherRootItems()
        val pageSize = quickLauncherPageSize()
        val columns = quickLauncherColumnsPerPage()
        val rows = quickLauncherRowsPerPage()
        val key = QuickLauncherGridLogic.pagesCacheKey(panel.id, root.size, root.hashCode(), pageSize, columns, rows)
        quickLauncherCachedPages?.let { cached ->
            if (key == quickLauncherCachedPagesKey) return cached
        }
        val pages = if (root.isEmpty()) {
            listOf(emptyList())
        } else {
            root.chunked(pageSize)
        }
        quickLauncherPageCount = pages.size
        quickLauncherPageIndex = quickLauncherPageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        quickLauncherCachedPages = pages
        quickLauncherCachedPagesKey = key
        return pages
    }

    internal fun quickLauncherPagination(): Triple<Int, Int, Int> {
        val pages = quickLauncherPages()
        val pageSize = quickLauncherPageSize()
        val pageCount = pages.size
        val pageStart = quickLauncherPageIndex * pageSize
        return Triple(pageStart, pageSize, pageCount)
    }

    internal fun quickLauncherItemsForPage(pageIndex: Int): List<QuickLauncherItem> {
        val pages = quickLauncherPages()
        val clampedPage = pageIndex.coerceIn(0, pages.size - 1)
        val pageStart = clampedPage * quickLauncherPageSize()
        if (clampedPage == quickLauncherPageIndex) {
            quickLauncherPanelController.setItemPageOffset(pageStart)
        }
        return pages.getOrElse(clampedPage) { emptyList() }
    }

    internal fun quickLauncherPanelWidthForPaging(): Float {
        if (quickLauncherLayoutPanelWidth > 0f) return quickLauncherLayoutPanelWidth
        return quickLauncherPanelRect().width().coerceAtLeast(1f).also {
            quickLauncherLayoutPanelWidth = it
        }
    }

    internal fun invalidateQuickLauncherPanel() {
        if (host.gestureSession().panelMode() != OverlayPanelMode.QUICK_LAUNCHER) {
            host.invalidate()
            return
        }
        val panelRect = quickLauncherPanelRect()
        if (panelRect.isEmpty) {
            host.invalidate()
            return
        }
        val dirty = quickLauncherPanelController.combinedContentRect(panelRect)
        val offsetX = host.panelEnterOffsetX(dirty)
        val pad = host.dp(2f).toInt()
        host.invalidatePartial(
            (dirty.left + offsetX).toInt() - pad,
            dirty.top.toInt() - pad,
            (dirty.right + offsetX).toInt() + pad,
            dirty.bottom.toInt() + pad
        )
    }

    internal fun quickLauncherPagingActiveForHitTest(): Boolean =
        quickLauncherPageSwipeLocked ||
            quickLauncherPageSnapMotion.isRunning ||
            kotlin.math.abs(quickLauncherPageDragOffset) > host.dp(0.5f)

    internal fun quickLauncherGlobalIndexAt(touchX: Float, localY: Float, panelRect: RectF): Int {
        val pageSize = quickLauncherPageSize().coerceAtLeast(1)
        val panelWidth = panelRect.width().coerceAtLeast(1f)
        val offset = quickLauncherPageDragOffset
        val pagingActive = quickLauncherPagingActiveForHitTest()

        val pageIdx: Int
        val xInPage: Float
        if (pagingActive && quickLauncherPageCount > 1) {
            val relativeX = touchX - panelRect.left - offset
            pageIdx = (relativeX / panelWidth).toInt().coerceIn(0, quickLauncherPageCount - 1)
            xInPage = panelRect.left + relativeX - pageIdx * panelWidth
        } else {
            pageIdx = quickLauncherPageIndex.coerceIn(0, quickLauncherPageCount - 1)
            xInPage = touchX
        }

        val pageStart = pageIdx * pageSize
        val localIndex = quickLauncherLocalCellIndexAt(
            xInPage = xInPage,
            localY = localY,
            panelRect = panelRect,
            maxSlotIndex = pageSize - 1
        )
        return QuickLauncherGridLogic.dragSlotGlobal(pageStart, localIndex, pageSize)
    }

    internal fun quickLauncherLocalCellIndexAt(
        xInPage: Float,
        localY: Float,
        panelRect: RectF,
        maxSlotIndex: Int
    ): Int {
        if (maxSlotIndex < 0) return 0
        val columns = quickLauncherColumnsPerPage()
        val rows = quickLauncherRowsPerPage()
        val visualCol = ((xInPage - panelRect.left - quickLauncherGridPadding) / quickLauncherCellWidth)
            .toInt()
            .coerceIn(0, columns - 1)
        val row = ((localY - panelRect.top - quickLauncherHeaderHeight - quickLauncherGridPadding) /
            quickLauncherCellHeight)
            .toInt()
            .coerceIn(0, rows - 1)
        val colInRow = when (quickLauncherLayoutSide()) {
            com.slideindex.app.overlay.PanelSide.RIGHT -> columns - 1 - visualCol
            else -> visualCol
        }
        return (row * columns + colInRow).coerceIn(0, maxSlotIndex)
    }

    /**
     * 快速启动器内容的「排版侧」。
     *
     * 受设置项 `QuickLauncherDisplaySettings.mirrorOnRightSide` 控制：关掉镜像后，右侧面板
     * 也按 LEFT 的 LTR 语义排版（格子从左往右、翻页往左 == 下一页）。
     *
     * ⚠️ 渲染与命中必须**都**走这里。只改其中一边会让「看到的位置」和「点到的格子」错位。
     * 物理侧（面板落位、进入动画、触钮与面板之间的接近区）**不要**走这里，那些始终看 [Host.side]。
     */
    internal fun quickLauncherLayoutSide(): PanelSide =
        resolveLayoutSide(host.side(), host.settings().quickLauncherDisplay.mirrorOnRightSide)

    internal fun quickLauncherPanelRect(): RectF {
        // 钉住空闲：整块矩形已冻结（见 pinnedIdlePanelRect），不再让落位算法重算。
        pinnedIdlePanelRect?.let { return RectF(it) }
        return computeQuickLauncherPanelRect()
    }

    /** 真正跑一次落位算法（不含钉住冻结）。 */
    private fun computeQuickLauncherPanelRect(): RectF {
        quickLauncherPagination()
        val session = host.gestureSession()
        val pinned = session.quickLauncherPinned()
        // 呼出 / 跟手阶段：把这一帧真正用于布局的「触发带 + 锚点」记下来。
        //
        // 这是「面板自己跳」的根因所在：落位公式的横向位置与纵向夹取都由触发带决定，而
        // activeTriggerZoneRect() 在手势进行中返回「当前触钮」的矩形、松手后返回「该侧所有
        // 触钮的并集」——两者不是同一个矩形（真机实测横向差 16px、纵向差 89px）。
        // 钉住面板必须一直用呼出时那一个，否则松手后任何一次重算都会整体平移。
        if (pinned && session.isActive()) {
            pinnedLayoutSnapshot = PinnedLayoutSnapshot(
                trigger = RectF(host.activeTriggerZoneRect()),
                anchorLocalY = quickLauncherAnchorLocalY(),
            )
        }
        val snapshot = if (pinned) pinnedLayoutSnapshot else null
        val anchorLocalY = snapshot?.anchorLocalY ?: quickLauncherAnchorLocalY()
        val rect = QuickLauncherPanelLayoutEngine.panelRect(
            host = host,
            columnsPerPage = quickLauncherColumnsPerPage(),
            rowsPerPage = quickLauncherRowsPerPage(),
            cellWidth = quickLauncherCellWidth,
            cellHeight = quickLauncherCellHeight,
            gridPadding = quickLauncherGridPadding,
            headerHeight = quickLauncherHeaderHeight,
            anchorLocalY = anchorLocalY,
            toolbarReserveWidth = quickLauncherPanelController.contentReserveWidth(host.settings()),
            triggerRectOverride = snapshot?.trigger,
            // 外部触发（全局触控 / 角轮盘 / 悬浮球）：不把面板拉回触钮条的纵向范围，
            // 手指在哪个高度呼出就出现在哪个高度；触钮自己触发的路径保持贴条行为。
            clampAnchorToTrigger = !quickLauncherExternalAnchor,
        )
        // 真机排查「面板自己挪位置」：面板矩形一变就记一笔，附上触发带与状态。
        if (rect != lastLoggedPanelRect) {
            lastLoggedPanelRect = rect
            EdgeDiag.log(
                "qlPin",
                "面板矩形 rect=$rect anchorLocalY=$anchorLocalY " +
                    "active=${session.isActive()} pinned=$pinned " +
                    "bandNow=${host.activeTriggerZoneRect()} " +
                    "bandSnapshot=${pinnedLayoutSnapshot?.trigger} " +
                    "view=${host.viewWidth()}x${host.viewHeight()}",
            )
        }
        return rect
    }

    /** 钉住落位快照：呼出过程中记下的触发带与锚点；钉住空闲只认它。 */
    private data class PinnedLayoutSnapshot(
        val trigger: RectF,
        val anchorLocalY: Float,
    )

    private var pinnedLayoutSnapshot: PinnedLayoutSnapshot? = null

    /** 上次记录过的面板矩形（只用于日志去重）。 */
    private var lastLoggedPanelRect: RectF? = null

    private fun resolveQuickLauncherAnchorLocalY(): Float {
        val rawY = quickLauncherAnchorRawY ?: host.pathRecognizer().gestureStartRawY()
        val loc = host.viewLocationOnScreen()
        val anchorY = rawY - loc[1]
        // 外部触发（全局触控 / 角轮盘 / 悬浮球）的锚点不再夹进触钮区间：
        // 手指可能停在触钮范围之外，夹进去会让面板离开触发位置（表现为"最高只到屏幕中间"）。
        // 触钮自己触发的路径（quickLauncherExternalAnchor=false）保持贴条行为。
        val trigger = host.activeTriggerZoneRect()
        return anchorY.coerceIn(
            FloatBallOverlayAnchorPolicy.anchorRange(
                anchorsAtTouchDown = quickLauncherExternalAnchor,
                viewHeightPx = host.viewHeight().toFloat(),
                marginPx = host.dp(16f),
                triggerTopPx = trigger.top,
                triggerBottomPx = trigger.bottom
            )
        )
    }

    private fun quickLauncherAnchorLocalY(): Float =
        // 钉住空闲锁定的落位优先：此后不再跟随手势现场（那个现场已经被会话收尾清掉了）。
        pinnedIdleAnchorLocalY ?: quickLauncherFrozenAnchorLocalY ?: resolveQuickLauncherAnchorLocalY()

    private fun quickLauncherGridLayoutInfo(): GridLayoutInfo =
        QuickLauncherPanelLayoutEngine.gridLayoutInfo(
            columnsPerPage = quickLauncherColumnsPerPage(),
            rowsPerPage = quickLauncherRowsPerPage(),
            cellWidth = quickLauncherCellWidth,
            gridPadding = quickLauncherGridPadding
        )

    private fun quickLauncherPanelContentHeight(rows: Int): Float =
        QuickLauncherPanelLayoutEngine.contentHeight(
            rows,
            quickLauncherCellHeight,
            quickLauncherGridPadding,
            quickLauncherHeaderHeight
        )

    private fun anchoredQuickLauncherPanelRect(panelWidth: Float, rows: Int): RectF =
        QuickLauncherPanelLayoutEngine.anchoredPanelRect(
            host = host,
            panelWidth = panelWidth,
            contentHeight = quickLauncherPanelContentHeight(rows),
            anchorLocalY = quickLauncherAnchorLocalY(),
            clampAnchorToTrigger = !quickLauncherExternalAnchor,
        )

    private fun offsetQuickLauncherPanelForToolbar(panelRect: RectF): RectF =
        QuickLauncherPanelLayoutEngine.offsetForToolbar(
            host = host,
            panelRect = panelRect,
            reserveWidth = quickLauncherPanelController.contentReserveWidth(host.settings())
        )

    internal fun collectToolbarAccessibilityNodes(
        context: android.content.Context,
        panelRect: RectF
    ): List<OverlayVirtualNode> =
        quickLauncherPanelController.collectAccessibilityNodes(context, panelRect)
}
