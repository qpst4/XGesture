package com.slideindex.app.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.slideindex.app.di.AppDependencies
import com.slideindex.app.overlay.HistoryFloatHandleGestureExclusion
import com.slideindex.app.overlay.OverlayCompose
import com.slideindex.app.overlay.OverlayComposeOwner
import com.slideindex.app.overlay.OverlayWindowTypes
import com.slideindex.app.overlay.StashPanelInitialTab
import com.slideindex.app.overlay.history.HistoryFloatContent
import com.slideindex.app.overlay.history.HistoryHandleCaptureVisibility
import com.slideindex.app.overlay.history.HistoryNoteSlotWindow
import com.slideindex.app.overlay.history.HistoryPanelTab
import com.slideindex.app.overlay.history.HistorySavePeekWindow
import com.slideindex.app.overlay.history.HistorySaveSignal
import com.slideindex.app.overlay.history.StashPanelTabMemory
import com.slideindex.app.overlay.history.StashReminderPendingState
import com.slideindex.app.settings.HistoryFloatHandlePosition
import com.slideindex.app.settings.HistoryFloatHandleWidth
import com.slideindex.app.settings.StashPanelHandleOpenTab
import com.slideindex.app.stash.StashAccess
import com.slideindex.app.stash.StashCoordinator
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HistoryFloatService : Service() {
    @Inject lateinit var deps: AppDependencies

    private lateinit var windowManager: WindowManager
    private lateinit var mainParams: LayoutParams
    private var composeView: ComposeView? = null
    private var composeOwner: OverlayComposeOwner? = null
    private var handleVisible by mutableStateOf(true)
    private var handleWidth by mutableIntStateOf(HistoryFloatHandleWidth.DEFAULT_DP)
    private var lockLoc = true
    private var landscapeEnabled = false
    private var positionY = 0
    private var viewAdded = false
    private var hiddenForFullscreen = false
    private var hiddenForLandscape = false
    private var hiddenForScreenOff = false
    /** 有待办未完成 → 把手整条变色（不出数字、不加宽）。 */
    private var handleAlert by mutableStateOf(false)
    /** 存下后的内容预览（懒创建：没人存东西就不建窗）。 */
    private var peekWindow: HistorySavePeekWindow? = null
    /** 长按把手的就地输入槽（懒创建）。 */
    private var slotWindow: HistoryNoteSlotWindow? = null
    /**
     * 「提醒流光」自愈轮询的退避状态（见 [refreshHandlePendingGlow]）。单位 ms。
     */
    private var pendingGlowBackoffMs = 0L
    private var pendingGlowRefreshedAtMs = 0L
    /**
     * 是否**至少算过一次** [StashReminderPendingState]。
     * 用它保证"进程起来后必然算一次"这件事不被退避/可见性 gate 吃掉（见 [refreshHandlePendingGlow]）。
     */
    private var hasEverComputed = false
    /**
     * 过期提醒「有界对账」的重入闸门（见 [reconcileExpiredRemindersIfNeeded]）。
     *
     * `clearExpiredReminders()` 是 suspend（要拿跨进程文件锁 + 写盘 + 取消闹钟），可能跨好几拍
     * 500ms tick 才回来；而 [refreshHandlePendingGlow] 到点就会再起一个协程。没有这个闸门的话，
     * 几拍会同时排队去写同一份 meta 文件 —— 纯属浪费，而且它们算出来的结果完全一样。
     *
     * 用 [AtomicBoolean] 而不是 `@Volatile var`：读取与置位必须是一个原子动作
     * （先读 false 再各自置 true 的写法挡不住两拍同时进来），`compareAndSet` 才是真正的"抢位"。
     */
    private val reconcilingExpiredReminders = AtomicBoolean(false)
    /** [HistorySaveSignal] 的普通回调（Service 里没有组合上下文）。 */
    private val saveListener: (String) -> Unit = { text ->
        // 把手自己都被藏起来时（全屏/横屏/息屏/截图）不要凭空冒出一个预览。
        if (!hiddenForFullscreen && !hiddenForLandscape && !hiddenForScreenOff &&
            !HistoryHandleCaptureVisibility.isSuppressed
        ) {
            ensurePeekWindow().show(text, handleCenterY())
        }
    }
    private val visibleDisplayFrame = Rect()
    private val mainHandler = Handler(Looper.getMainLooper())
    /** 息屏时把手没必要留在屏上（对照 ClipboardFloatService 的做法）。 */
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                hiddenForScreenOff = true
                applyFloatVisibility()
            } else if (intent?.action == Intent.ACTION_SCREEN_ON) {
                hiddenForScreenOff = false
                applyFloatVisibility()
            }
        }
    }
    private val fullscreenCheckRunnable = object : Runnable {
        override fun run() {
            updateFullscreenVisibility()
            refreshHandleAlert()
            // 顺带把"提醒流光"的开关也算一遍（§0.16.x：进程重启后把手不亮）。
            // 复用这个已在跑的 500ms tick，不额外起协程；真正的 refresh 由内部节流到 30s 一次。
            refreshHandlePendingGlow()
            mainHandler.postDelayed(this, FULLSCREEN_CHECK_INTERVAL_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        mainParams = LayoutParams()
        val overlayContext = OverlayCompose.themedContext(this)
        val owner = OverlayComposeOwner()
        composeOwner = owner
        composeView = OverlayCompose.createComposeView(overlayContext, owner).apply {
            setContent {
                HistoryFloatContent(
                    handleVisible = handleVisible,
                    handleAlert = handleAlert,
                    onOpenPanel = { openPanelFromHandle() },
                    onMoveHandle = { moveHandle(it) },
                    onMoveHandleEnd = { persistHandlePosition() },
                    // 跟手拉出：横向拖过阈值 → 面板窗从屏幕外开始跟着手指走（见 HistoryPanelReveal）。
                    onRevealStart = {
                        StashCoordinator.beginHandleReveal(this@HistoryFloatService, resolveHandleOpenTab())
                    },
                    onRevealEnd = { commit -> StashCoordinator.endHandleReveal(commit) },
                    // 长按 = 就地记一条（设计稿 `.slot`），不再只是"打开面板"。
                    onQuickNote = { showNoteSlot() },
                )
            }
        }
        HistorySaveSignal.addListener(saveListener)
        // 截图期临时隐藏把手：截图链路与本服务之间没有引用，靠这个进程级对象传一个"重新算可见性"的动作。
        HistoryHandleCaptureVisibility.setApplier { applyFloatVisibility() }
        runCatching {
            ContextCompat.registerReceiver(
                this,
                screenOffReceiver,
                IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_OFF)
                    addAction(Intent.ACTION_SCREEN_ON)
                },
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_LOCK_POSITION -> {
                lockLoc = intent.getBooleanExtra(EXTRA_LOCK_POSITION, lockLoc)
                return START_STICKY
            }
            ACTION_SET_HANDLE_WIDTH -> {
                handleWidth = intent.getIntExtra(
                    EXTRA_HANDLE_WIDTH_DP,
                    HistoryFloatHandleWidth.DEFAULT_DP
                )
                return START_STICKY
            }
            ACTION_SET_LANDSCAPE_ENABLED -> {
                landscapeEnabled = intent.getBooleanExtra(EXTRA_LANDSCAPE_ENABLED, landscapeEnabled)
                updateLandscapeVisibility()
                return START_STICKY
            }
        }
        handleWidth = intent?.getIntExtra(EXTRA_HANDLE_WIDTH_DP, handleWidth) ?: handleWidth
        lockLoc = intent?.getBooleanExtra(EXTRA_LOCK_POSITION, lockLoc) ?: lockLoc
        landscapeEnabled = intent?.getBooleanExtra(EXTRA_LANDSCAPE_ENABLED, landscapeEnabled) ?: landscapeEnabled
        showFloatWindow()
        return START_STICKY
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(fullscreenCheckRunnable)
        // 窗马上要摘了：清空截图钩子并复位状态，避免下次启动带着陈旧的隐藏态。
        HistoryHandleCaptureVisibility.detachApplier()
        runCatching { unregisterReceiver(screenOffReceiver) }
        HistorySaveSignal.removeListener(saveListener)
        slotWindow?.destroy()
        slotWindow = null
        peekWindow?.destroy()
        peekWindow = null
        if (viewAdded) {
            composeView?.let { runCatching { windowManager.removeView(it) } }
            viewAdded = false
        }
        OverlayCompose.teardownOverlayCompose(composeView, composeOwner)
        composeOwner = null
        composeView = null
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (viewAdded) {
            applyHandlePosition()
            composeView?.let { windowManager.updateViewLayout(it, mainParams) }
        }
        updateLandscapeVisibility()
        updateFullscreenVisibility()
    }

    private fun showFloatWindow() {
        val view = composeView ?: return
        if (!Settings.canDrawOverlays(this) || viewAdded) {
            return
        }

        mainParams.type = OverlayWindowTypes.overlayWindowType(this)
        mainParams.format = PixelFormat.RGBA_8888
        mainParams.width = LayoutParams.WRAP_CONTENT
        mainParams.height = LayoutParams.WRAP_CONTENT
        mainParams.flags = BASE_WINDOW_FLAGS
        mainParams.gravity = Gravity.END or Gravity.TOP
        OverlayWindowTypes.ensureNoBrightnessOverride(mainParams)
        applyHandlePosition()
        windowManager.addView(view, mainParams)
        viewAdded = true
        // 把手落在右侧 48dp 系统返回手势区里，必须为**自己这块足迹**申请排除区，
        // 否则用户想从把手位置返回时会滑不动（其上下方的返回手势照常可用）。
        HistoryFloatHandleGestureExclusion.attach(view)
        view.post {
            if (!viewAdded) return@post
            applyHandlePosition()
            runCatching { windowManager.updateViewLayout(view, mainParams) }
        }
        updateLandscapeVisibility()
        updateFullscreenVisibility()
        mainHandler.removeCallbacks(fullscreenCheckRunnable)
        mainHandler.post(fullscreenCheckRunnable)
    }

    private fun isLandscape(): Boolean =
        resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private fun storedHandleY(): Int {
        val snapshot = deps.settingsRepository.readSnapshot()
        return if (isLandscape()) {
            snapshot.clipboardHistoryFloatHandleYLandscape
        } else {
            snapshot.clipboardHistoryFloatHandleYPortrait
        }
    }

    private fun estimateHandleHeightPx(): Int {
        val measured = composeView?.height?.takeIf { it > 0 }
        if (measured != null) return measured
        return (96f * resources.displayMetrics.density).roundToInt()
    }

    private fun screenHeightPx(): Int = resources.displayMetrics.heightPixels

    private fun applyHandlePosition() {
        positionY = HistoryFloatHandlePosition.resolveY(
            storedY = storedHandleY(),
            screenHeightPx = screenHeightPx(),
            handleHeightPx = estimateHandleHeightPx()
        )
        mainParams.x = 0
        mainParams.y = positionY
    }

    private fun moveHandle(dy: Float) {
        val view = composeView ?: return
        if (lockLoc || !viewAdded) {
            return
        }
        positionY = HistoryFloatHandlePosition.clampY(
            y = positionY + dy.roundToInt(),
            screenHeightPx = screenHeightPx(),
            handleHeightPx = estimateHandleHeightPx()
        )
        mainParams.x = 0
        mainParams.y = positionY
        windowManager.updateViewLayout(view, mainParams)
    }

    private fun persistHandlePosition() {
        if (lockLoc || !viewAdded) {
            return
        }
        val landscape = isLandscape()
        val y = positionY
        deps.applicationScope.launch {
            deps.settingsRepository.setClipboardHistoryFloatHandleY(y, landscape)
        }
    }

    /**
     * 「从指示条打开面板先显示哪一页」—— 点击（含双击）与横向拉出**共用这一份判定**。
     *
     * 设置见 `StashPanelHandleOpenTab`：默认 CLIPBOARD = 加这条设置之前的历史行为（点一下开剪贴板）；
     * "记住上次页签"读进程级的 [StashPanelTabMemory]（面板每次换页都会写它）。
     */
    private fun resolveHandleOpenTab(): StashPanelInitialTab =
        when (deps.settingsRepository.readSnapshot().stashPanelHandleOpenTab) {
            StashPanelHandleOpenTab.STASH -> StashPanelInitialTab.Stash
            StashPanelHandleOpenTab.CLIPBOARD -> StashPanelInitialTab.Clipboard
            StashPanelHandleOpenTab.REMEMBER_LAST -> when (StashPanelTabMemory.tab) {
                HistoryPanelTab.Stash -> StashPanelInitialTab.Stash
                HistoryPanelTab.Clipboard -> StashPanelInitialTab.Clipboard
            }
        }

    private fun openPanelFromHandle() {
        val context = applicationContext
        when (resolveHandleOpenTab()) {
            StashPanelInitialTab.Stash -> StashCoordinator.openStashPanel(context)
            StashPanelInitialTab.Clipboard -> StashCoordinator.openClipboardPanel(context)
        }
    }

    /** 把手纵向中心（px）：peek 与输入槽都贴着它对齐。 */
    private fun handleCenterY(): Int = positionY + estimateHandleHeightPx() / 2

    /** 长按把手：弹出就地输入槽（懒创建窗口）。 */
    private fun showNoteSlot() {
        val window = slotWindow ?: HistoryNoteSlotWindow(this, windowManager).also {
            slotWindow = it
        }
        window.show(handleCenterY())
    }

    private fun ensurePeekWindow(): HistorySavePeekWindow =
        peekWindow ?: HistorySavePeekWindow(this, windowManager).also { peekWindow = it }

    /**
     * 有待办未完成 → 把手变色。
     *
     * 数据来自 [StashAccess.metaRepository]（标签绑定 + 完成态）。这里跟随既有的
     * 500ms 轮询顺手刷新，而不是订阅 Flow —— 因为元数据仓库可能比本 Service 晚初始化，
     * 轮询天然容忍顺序问题，代价也只是一次内存 Map 遍历。
     */
    private fun refreshHandleAlert() {
        val alert = StashAccess.metaRepository?.pendingTodoCount()?.let { it > 0 } ?: false
        if (alert != handleAlert) {
            handleAlert = alert
        }
    }

    /**
     * 让"提醒流光"的开关在**进程刚起来**时也能亮起来（§0.16.x 实测"通知还在、条不亮"）。
     *
     * 为什么必须有这一步：[StashReminderPendingState.hasPending] 是**纯内存**的
     * （初值 false），而它的 `refresh(context)` 原先只在三处被调：提醒通知发出/稍后（receiver）、
     * 通知里的按钮 trampoline、以及**面板可见时**（`HistoryPanelScreen` 的 LaunchedEffect + 2s 轮询）。
     * 于是**杀掉进程 / 重装 App / 系统重启之后**：通知明明还挂在通知栏里、数据层也还有"已过点未完成"
     * 的提醒，但这个 Boolean 没人去算，把手就永远不亮 —— 直到用户去打开一次面板。
     * 用户复现的正是这条路径（装完新包就把面板关着看条）。
     *
     * 为什么 30s 轮询 + 退避重试：
     * - 这不是 UI 驱动，而是"自愈"：通知被 ROM 静默清掉、或数据层晚于本服务挂上（冷启动时序）
     *   都能靠下一拍纠正，用户最多等 30s，可接受；
     * - `refresh` 本身很轻（一次 `getActiveNotifications` + 一次偏好读 + 一遍提醒表），30s 一次无所谓；
     * - **退避**是为了"状态不翻转时不要每 30s 白跑一趟"：连续没有变化就翻倍到 240s 封顶；
     *   一旦结果翻转（点亮/熄灭）立刻回到 30s，保证关键变化跟得紧。
     * - 这条自愈轮询挂在**已有的** 500ms [fullscreenCheckRunnable] 上，不额外起协程/计时器。
     *
     * ⚠️ **"首次必然计算一次"不依赖任何 gate**（用户特别要求）：
     * 用一个 [hasEverComputed] 标志，只要还没算过，就跳过"退避间隔"判断无条件算一次；
     * 算完（不管结果）才置位。这样即使 `viewAdded` 在头几拍还是 false、或者 [pendingGlowBackoffMs]
     * 在别处被推进过，都不会出现"永远没算过"的情况 —— 而没算过时 `hasPending` 永远是初值 false。
     * 唯一保留的前置条件是"把手真的可能被看见"（窗口已上屏且没被全屏/横屏/息屏藏起来），
     * 那是为了不在没人看的窗口期白算；但它不会**消费**首次计算的机会（标志只在真算过之后置位）。
     *
     * ⚠️ 每一拍照样是"先对账、再算流光"两步（[reconcileExpiredRemindersIfNeeded]）：
     * 对账是治"用户从不打开面板 + 通知权限被关 → 那条过期 `reminders` 没人收尾 → 光永久亮"的，
     * 详见那个方法的注释。对账同样受这里的可见性 gate 保护 —— 把手看不见时流光本来就不显示，
     * 不需要为了收敛去写盘。
     */
    private fun refreshHandlePendingGlow() {
        val firstTime = !hasEverComputed
        // 把手看不见的时候不算：没上屏，或正被全屏/横屏/息屏藏着
        // （那三种状态下 `applyFloatVisibility` 只是把窗口 alpha 归零，`viewAdded` 仍是 true）。
        // ⚠️ 注意这里**不置位** `hasEverComputed`：首次计算的机会要留到把手真能看见的那一刻。
        if (!viewAdded || hiddenForFullscreen || hiddenForLandscape || hiddenForScreenOff) return
        val now = SystemClock.elapsedRealtime()
        // 首次无条件算；之后才按退避间隔节流。
        if (!firstTime && now - pendingGlowRefreshedAtMs < pendingGlowBackoffMs) return
        hasEverComputed = true
        pendingGlowRefreshedAtMs = now
        // 放到 Default 线程算：`refresh` 里有 `getActiveNotifications` 和偏好读取，
        // 虽然很轻，但这里是 500ms 的服务 tick（主线程），不该把 IO/系统调用压在主线程上。
        // 它内部只写一个 Compose state（`hasPending`），从后台线程写是安全的。
        deps.applicationScope.launch(Dispatchers.Default) {
            // 先对账，再算流光：
            // 对账会把"过点超过宽限期"的 `reminders` 条目搬进 `firedAt`（并 cancel 掉它的闹钟），
            // 于是兜底判据改由 `recentlyFired`（自带 30 分钟时限）接手 → 光在宽限期后自然灭。
            // 这是治本的收敛入口（面板那条 LaunchedEffect 在"从不打开面板"时永远不跑）。
            reconcileExpiredRemindersIfNeeded()
            val changed = StashReminderPendingState.refresh(applicationContext)
            pendingGlowBackoffMs = if (changed) {
                PENDING_GLOW_MIN_INTERVAL_MS
            } else {
                (maxOf(pendingGlowBackoffMs, PENDING_GLOW_MIN_INTERVAL_MS) * 2)
                    .coerceAtMost(PENDING_GLOW_MAX_INTERVAL_MS)
            }
        }
    }

    /**
     * 「有界对账」：把**过点超过宽限期**的 `reminders` 收尾掉（搬进 `firedAt`），让流光能自然熄灭。
     *
     * 治的是什么：[StashReminderPendingState] 的兜底判据里，`reminders[id] <= now` 这一半
     * **不能**加时限（它专门覆盖"通知权限被关 / 通知发不出去"的机器 —— `StashReminderReceiver`
     * 在 `areNotificationsEnabled() == false` 时直接 return，那些用户**永远没有通知可看**）；
     * 而收尾它的 `clearExpiredReminders()` 原先**只在面板可见/数据变化时**才被调，
     * 于是"用户从不打开面板 + 通知权限被关"时，那条过期项永远躺在 `reminders` 里 → 把手永久亮。
     * 这里补上服务侧那条入口，整条链是：
     * **`reminders` 过期项 →（面板可见时 / 本服务的 30s 轮询）对账搬进 `firedAt` → 宽限期后自然灭**。
     *
     * 为什么不是"给 `overdue` 也加时限"：那等于把上面那批用户的流光彻底关掉
     * （见 [StashReminderPendingState] 的 §「过期 `reminders` 的收敛链」）。收敛只能靠"把过期项搬走"。
     *
     * 代价控制（三层，从最便宜到最贵）：
     * 1. **零写盘探针** [StashReminderPendingState.hasStaleExpiredReminders]：只读内存里的
     *    `StateFlow` 快照，没有"过点超过宽限期"的条目就立刻返回 —— 绝大多数轮询走的就是这条；
     * 2. **重入闸门** [reconcilingExpiredReminders]：上一拍的对账还没回来就直接放弃这一拍，绝不叠写；
     * 3. 只有前两步都放行，才真的去 `clearExpiredReminders()`（suspend：文件锁 + 写盘 + 取消闹钟）。
     *    它的收敛是**有保证**的：每条过期项要么被删掉（同时记 `firedAt`），要么被 snooze override
     *    推成未来时间 —— 两种结果都会让探针在下一拍返回 false，同一条不会被反复对账。
     *
     * ⚠️ 这里**只**调 `clearExpiredReminders`，不调 `mergeSnoozeOverrides`（面板那边要按顺序先并再清，
     * 是因为它紧接着要用合并后的表 `rescheduleAll`）。本方法只做收尾，而 `clearExpiredReminders`
     * 内部自己会读 snooze override 并按规则 2 顺延，所以顺序问题在这里不存在。
     *
     * 失败不吵：对账失败只记一条 warn（下一拍 30s 后自然重试），不弹任何 UI —— 它是自愈，不是用户操作。
     */
    private suspend fun reconcileExpiredRemindersIfNeeded() {
        // ① 便宜判断：只扫一遍内存快照，没有该搬走的就直接返回（零写盘）。
        if (!StashReminderPendingState.hasStaleExpiredReminders()) return
        // ② 抢位：抢不到说明上一拍还没写完，这一拍直接放弃。
        if (!reconcilingExpiredReminders.compareAndSet(false, true)) return
        try {
            // 数据层晚于本服务挂上时拿不到 → 静默跳过，下一次轮询再试（`deps.stashRepository`
            // 是 `StashMetaRepository` 的构造依赖，它的 `init` 会把自己挂到 `StashAccess`）。
            val repo = StashAccess.metaRepository ?: return
            runCatching { repo.clearExpiredReminders() }
                .onFailure { Log.w(TAG, "过期提醒对账失败（下一拍重试）", it) }
        } finally {
            reconcilingExpiredReminders.set(false)
        }
    }

    private fun updateFullscreenVisibility() {
        if (!viewAdded) {
            return
        }
        val isFullscreen = isSystemFullscreen()
        if (hiddenForFullscreen == isFullscreen) {
            return
        }
        hiddenForFullscreen = isFullscreen
        applyFloatVisibility()
    }

    private fun updateLandscapeVisibility() {
        if (!viewAdded) {
            return
        }
        val isLandscape = isLandscape()
        val shouldHide = isLandscape && !landscapeEnabled
        if (hiddenForLandscape == shouldHide) {
            return
        }
        hiddenForLandscape = shouldHide
        applyFloatVisibility()
    }

    private fun applyFloatVisibility() {
        val view = composeView ?: return
        val hidden = hiddenForFullscreen || hiddenForLandscape || hiddenForScreenOff ||
            // 截图期（取词 / 屏内搜索）也要藏：不藏就会被 takeScreenshot 拍进全屏截图。
            HistoryHandleCaptureVisibility.isSuppressed
        val expectedFlags = if (hidden) {
            BASE_WINDOW_FLAGS or LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            BASE_WINDOW_FLAGS
        }
        if (viewAdded && mainParams.flags != expectedFlags) {
            mainParams.flags = expectedFlags
            windowManager.updateViewLayout(view, mainParams)
        }
        view.alpha = if (hidden) 0f else 1f
        view.visibility = View.VISIBLE
        // 把手藏起来时，贴在它旁边的两个小窗（预览 / 输入槽）也要收掉。
        if (hidden) {
            peekWindow?.hide()
            slotWindow?.hide()
        }
    }

    private fun isSystemFullscreen(): Boolean {
        val view = composeView ?: return false
        view.getWindowVisibleDisplayFrame(visibleDisplayFrame)
        val statusBarHeight = getStatusBarHeight()
        if (statusBarHeight <= 0) {
            return false
        }
        return visibleDisplayFrame.top <= statusBarHeight / 2
    }

    private fun getStatusBarHeight(): Int {
        if (!viewAdded) return 0
        val view = composeView ?: return 0
        return ViewCompat.getRootWindowInsets(view)
            ?.getInsets(WindowInsetsCompat.Type.statusBars())
            ?.top ?: 0
    }

    companion object {
        private const val TAG = "HistoryFloatService"

        const val ACTION_LOCK_POSITION = "com.slideindex.app.history_float.LOCK_POSITION"
        const val ACTION_SET_HANDLE_WIDTH = "com.slideindex.app.history_float.SET_HANDLE_WIDTH"
        const val ACTION_SET_LANDSCAPE_ENABLED = "com.slideindex.app.history_float.SET_LANDSCAPE_ENABLED"
        const val EXTRA_HANDLE_WIDTH_DP = "handle_width_dp"
        const val EXTRA_LOCK_POSITION = "lock_position"
        const val EXTRA_LANDSCAPE_ENABLED = "landscape_enabled"

        private const val BASE_WINDOW_FLAGS =
            LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                LayoutParams.FLAG_NOT_FOCUSABLE or
                LayoutParams.FLAG_NOT_TOUCH_MODAL or
                LayoutParams.FLAG_HARDWARE_ACCELERATED
        private const val FULLSCREEN_CHECK_INTERVAL_MS = 500L

        /** 「提醒流光」自愈轮询的间隔：有变化时 30s 一次，长期无变化就退避到 240s。 */
        private const val PENDING_GLOW_MIN_INTERVAL_MS = 30_000L
        private const val PENDING_GLOW_MAX_INTERVAL_MS = 240_000L
    }
}
