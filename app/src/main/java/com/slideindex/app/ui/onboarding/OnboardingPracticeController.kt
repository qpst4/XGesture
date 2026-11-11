package com.slideindex.app.ui.onboarding

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.slideindex.app.R
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GesturePerformedEvents
import com.slideindex.app.gesture.GestureTriggerMode
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.gesture.SideGestureDefaults
import com.slideindex.app.gesture.TriggerHandle
import com.slideindex.app.overlay.FloatBallPickEvents
import com.slideindex.app.overlay.FloatingPointerOverlayWindow
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.settings.SettingsRepository
import com.slideindex.app.settings.slotAction
import com.slideindex.app.util.PermissionHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** 一张练习卡的状态。 */
enum class PracticeTaskState {
    /** 还没配置（没绑动作 / 没开悬浮球）。 */
    PENDING,

    /** 已配置好，可以演示或上手试。 */
    READY,

    /** 正在演示，或正在等用户自己做一次。 */
    RUNNING,

    /** 用户自己做成了。 */
    DONE,
}

/**
 * 绑定优先级：**长斜下滑**（选定的手感：从边缘斜着往内下方拖）→ 长内滑 → 上滑 → 下滑。
 *
 * 注意斜下滑这一格出厂本来绑着「任务切换器」，所以覆盖它时要把被顶掉的动作搬到空位上，
 * 见 [planFloatingPointerBinding] 的 `relocateTo`。
 */
internal val FLOATING_POINTER_TRIGGER_CANDIDATES = listOf(
    GestureTriggerType.LONG_SWIPE_DOWN_RIGHT,
    GestureTriggerType.LONG_SWIPE_IN,
    GestureTriggerType.SHORT_SWIPE_UP,
    GestureTriggerType.SHORT_SWIPE_DOWN,
)

/** 被顶掉的出厂动作往哪儿搬：先选同一方向的短版，再退到其他空位。 */
internal val DISPLACED_ACTION_RELOCATION_CANDIDATES = listOf(
    GestureTriggerType.SHORT_SWIPE_DOWN_RIGHT,
    GestureTriggerType.SHORT_SWIPE_UP_RIGHT,
    GestureTriggerType.SHORT_SWIPE_IN_HOVER,
)

/** 该边出厂默认的槽位动作表：用来区分"用户没改过"和"用户自己配的"。 */
internal fun factoryActionsFor(side: PanelSide): Map<GestureTriggerType, GestureAction> =
    SideGestureDefaults.rulesFor(side)
        .filter { it.handleId == TriggerHandle.DEFAULT_ID }
        .associate { it.trigger to it.action }

internal enum class FloatingPointerBindOutcome {
    /** 这个触钮上已经绑着悬浮指针了，直接用。 */
    ALREADY_BOUND,

    /** 找到了一个可以安全覆盖的槽位（空位，或仍是出厂默认值的位）。 */
    BIND_NEW,

    /** 候选槽位都被用户自己改过了，不能动。 */
    NO_SLOT,
}

internal data class FloatingPointerBindPlan(
    val outcome: FloatingPointerBindOutcome,
    val trigger: GestureTriggerType? = null,
    /** 这次绑定会顶掉的出厂默认动作；非空时要把它搬到 [relocateTo]，别让用户白丢一个功能。 */
    val replacesAction: GestureAction? = null,
    /** 被顶掉动作的落点；null 表示没有空位可搬。 */
    val relocateTo: GestureTriggerType? = null,
)

/**
 * 选一个槽位来绑「悬浮指针」。
 *
 * "可以安全覆盖"的定义：**空位**，或**仍然是出厂默认值**（说明用户从没改过它）。
 * 用户自己改过的槽位一律不碰——引导只是建议配置，不能拿用户的手势当草稿纸。
 *
 * 抽成纯函数是为了让这两条约束（不覆盖用户手势、顶掉出厂动作要搬家）能被单测钉住。
 */
internal fun planFloatingPointerBinding(
    actionOf: (GestureTriggerType) -> GestureAction,
    factoryActionOf: (GestureTriggerType) -> GestureAction,
): FloatingPointerBindPlan {
    val alreadyBound = FLOATING_POINTER_TRIGGER_CANDIDATES
        .firstOrNull { actionOf(it) == GestureAction.FloatingPointer }
    if (alreadyBound != null) {
        return FloatingPointerBindPlan(FloatingPointerBindOutcome.ALREADY_BOUND, alreadyBound)
    }

    val target = FLOATING_POINTER_TRIGGER_CANDIDATES.firstOrNull { trigger ->
        val current = actionOf(trigger)
        current == GestureAction.None || current == factoryActionOf(trigger)
    } ?: return FloatingPointerBindPlan(FloatingPointerBindOutcome.NO_SLOT)

    val displaced = actionOf(target).takeIf { it != GestureAction.None }
    val relocateTo = displaced?.let {
        DISPLACED_ACTION_RELOCATION_CANDIDATES.firstOrNull { candidate ->
            candidate != target &&
                actionOf(candidate) == GestureAction.None &&
                factoryActionOf(candidate) == GestureAction.None
        }
    }
    return FloatingPointerBindPlan(
        outcome = FloatingPointerBindOutcome.BIND_NEW,
        trigger = target,
        replacesAction = displaced,
        relocateTo = relocateTo,
    )
}

/**
 * 新手引导第 4 步「上手两件事」的状态机。
 *
 * 两件事都刻意只用**既有设置**推导状态（触钮槽位动作、悬浮球开关），不新增任何持久化字段：
 * 「已绑好/已开启」直接读快照，「我刚才试成功了」只活在本次会话里。
 *
 * 演示不做手势注入：注入的合成触摸能不能被我们自己的边缘触钮窗口吃到，取决于窗口层级与系统
 * 策略，不适合作为"演示"的可靠底座。这里改成**直接调用动作本身**（与 `ActionExecutor` 的
 * `GestureAction.FloatingPointer` 分支同一入口），屏幕上真的会浮出指针，演示结果是真实的；
 * 方向示意则画在卡片里。用户侧的"自己做一次"才是真正的识别链路。
 */
class OnboardingPracticeController(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope,
    /**
     * 把"真实触钮"显示到屏幕上/收起来。
     *
     * 接到 `OverlayServiceController.startLayoutPreview(TRIGGER_ONLY)`：
     * 引导里光靠文字说"从边缘滑"没用，用户根本不知道触钮在哪一段——先把真的触钮亮出来。
     */
    private val onPreviewTriggerBar: (Boolean) -> Unit = {},
) {
    // —— 环境 ——
    var serviceEnabled by mutableStateOf(false)
        private set

    /**
     * 无障碍服务"当下是否连着"。
     *
     * 注意：这个值在本机实测会**抖动**——App 自己的无障碍自愈（`recoverAccessibilityBinding`，
     * 由 Shizuku 起的 shell 进程执行、那里看不到服务实例）会周期性把开关关掉再打开，
     * 于是这里每十几秒就 false→true 一次。所以它只用来显示提示，
     * **不要**拿它当按钮的可点条件，否则用户会在"我明明开着"的情况下点不动。
     */
    var serviceConnected by mutableStateOf(false)
        private set

    // —— 卡 1：触钮 → 悬浮指针 ——
    var pointerState by mutableStateOf(PracticeTaskState.PENDING)
        private set
    var pointerSide by mutableStateOf(PanelSide.RIGHT)
        private set
    var pointerTrigger by mutableStateOf(GestureTriggerType.SHORT_SWIPE_IN)
        private set
    var pointerBusy by mutableStateOf(false)
        private set
    private var pointerHandleId: String = TriggerHandle.DEFAULT_ID

    /** 触钮在屏幕上的实际纵向范围（占屏高比例），引导里的"往这里按"标记按它对齐。 */
    var triggerTopFraction by mutableStateOf(0.30f)
        private set
    var triggerHeightFraction by mutableStateOf(0.38f)
        private set

    // —— 卡 2：悬浮球取词 ——
    var ballState by mutableStateOf(PracticeTaskState.PENDING)
        private set
    var ballBusy by mutableStateOf(false)
        private set
    var pickedText by mutableStateOf<String?>(null)
        private set

    /** 一次性提示（字符串资源），由 UI 消费后清空。 */
    var hintRes by mutableStateOf<Int?>(null)
        private set

    /** 这次绑定顶掉、并成功搬到别处的出厂动作（UI 用来告诉用户"它去哪儿了"）。 */
    var relocatedAction by mutableStateOf<GestureAction?>(null)
        private set
    var relocatedTrigger by mutableStateOf<GestureTriggerType?>(null)
        private set

    // —— 卡 3：出厂就有的手势（短滑返回 / 下滑应用索引）——
    // 这两根不用配置、装上就是可用的，所以初始状态直接是"已就绪"。
    var indexState by mutableStateOf(PracticeTaskState.READY)
        private set
    var indexBusy by mutableStateOf(false)
        private set

    /** 屏幕上是否正亮着"按住这里"的标记（点按钮亮一会儿，或等待手势期间常亮）。 */
    var indexHintActive by mutableStateOf(false)
        private set

    private var pointerTestJob: Job? = null
    private var pickTestJob: Job? = null
    private var indexTestJob: Job? = null
    private var indexHintJob: Job? = null
    private var gestureWatcherJob: Job? = null
    private var gestureRulesRepaired = false

    val ready: Boolean
        get() = pointerState == PracticeTaskState.DONE && ballState == PracticeTaskState.DONE

    /** 环境侧的"能用吗"：授权或实例任一为真就算能用。 */
    private val serviceAvailable: Boolean
        get() = serviceEnabled || serviceConnected

    fun consumeHint() {
        hintRes = null
    }

    /** 进入引导：把真实触钮亮到屏幕上，用户才知道该按哪儿。 */
    fun beginSession() {
        onPreviewTriggerBar(true)
        repairGestureRulesOnce()
        startGestureWatcher()
        ensureMasterSwitchOn()
    }

    /**
     * 确保「手势总开关」是开的。
     *
     * 全新安装时它是**默认关**的（`AppSettings.serviceEnabled = false`）：触钮条不接管手势、
     * 拖也拖不出东西。之前只有点「绑定到触钮」才会顺手把它打开，于是用户走到引导页
     * 会以为"手势/按钮坏了"（实测反馈：点了绑定之后才一切正常）。这里在进入引导时就打开。
     */
    private fun ensureMasterSwitchOn() {
        if (settingsRepository.readSnapshot().serviceEnabled) return
        scope.launch {
            if (attempt("serviceEnabled/guide") { settingsRepository.setServiceEnabled(true) }) {
                Log.i(TAG, "guide: master switch turned on")
            }
        }
    }

    /**
     * 整个引导期间一直在听"用户做成了哪个手势"。
     *
     * 之前只有点了「试一下」才开始监听，于是"先做手势、再点按钮"的用户永远等不到成功
     * （实测反馈：我明明滑出来了，卡片却没变）。出厂手势本来就随时可用，判定就不该有时序要求。
     */
    private fun startGestureWatcher() {
        if (gestureWatcherJob != null) return
        gestureWatcherJob = scope.launch {
            GesturePerformedEvents.performed.collect { action ->
                when (action) {
                    is GestureAction.OpenIndex -> if (indexState != PracticeTaskState.DONE) {
                        Log.i(TAG, "gesture watcher: app index opened → card done")
                        indexState = PracticeTaskState.DONE
                        indexHintActive = false
                        indexHintJob?.cancel()
                        hintRes = null
                    }

                    else -> Unit
                }
            }
        }
    }

    /** 离开引导：收起触钮预览，别把演示用的指针留在屏幕上。 */
    fun endSession() {
        cancelPendingTests()
        gestureWatcherJob?.cancel()
        gestureWatcherJob = null
        onPreviewTriggerBar(false)
    }

    /**
     * 设置写入统一从这里走。
     *
     * `SettingsPreferencesEditor.edit` 是 `runCatching` 包起来的：写失败会被吞成一个
     * `Result.failure` 而不是抛出来。早期版本直接忽略了返回值，于是"写失败"表现为
     * "界面显示已绑定、一刷新又变回未配置"，排查时毫无线索——所以这里必须把失败显式化。
     */
    private suspend fun attempt(label: String, write: suspend () -> Any?): Boolean {
        val outcome = try {
            write()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "settings write threw: $label", e)
            hintRes = R.string.onboarding_practice_failed
            return false
        }
        if (outcome is Result<*> && outcome.isFailure) {
            Log.e(TAG, "settings write failed: $label", outcome.exceptionOrNull())
            hintRes = R.string.onboarding_practice_failed
            return false
        }
        return true
    }

    /** 每帧/定时刷新：设置可能被别处改动，服务连接状态也会变。 */
    fun refresh() {
        serviceEnabled = PermissionHelper.isAccessibilityServiceEnabled(context)
        serviceConnected = SlideIndexAccessibilityService.isConnected()
        val settings = settingsRepository.readSnapshot()

        // 卡 1：先看右侧（默认侧），没开再退到左侧 —— 与下面的绑定策略保持一致。
        val side = when {
            settings.rightEdgeEnabled -> PanelSide.RIGHT
            settings.leftEdgeEnabled -> PanelSide.LEFT
            else -> PanelSide.RIGHT
        }
        val handles = if (side == PanelSide.RIGHT) settings.rightTriggerHandles else settings.leftTriggerHandles
        pointerHandleId = handles.firstOrNull()?.id ?: TriggerHandle.DEFAULT_ID
        // 标记要落在真实触钮上：读该侧的手柄几何（没有手柄时退回该侧的默认位置）。
        handles.firstOrNull { it.id == pointerHandleId }?.let { handle ->
            triggerTopFraction = handle.topFraction
            triggerHeightFraction = handle.heightFraction
        } ?: run {
            triggerTopFraction = if (side == PanelSide.RIGHT) {
                settings.rightTriggerTopFraction
            } else {
                settings.leftTriggerTopFraction
            }
            triggerHeightFraction = if (side == PanelSide.RIGHT) {
                settings.rightTriggerHeightFraction
            } else {
                settings.leftTriggerHeightFraction
            }
        }
        val factory = factoryActionsFor(side)
        val plan = planFloatingPointerBinding(
            actionOf = { settings.slotAction(side, it, pointerHandleId) },
            factoryActionOf = { factory[it] ?: GestureAction.None },
        )
        pointerSide = side
        // 未绑定时也要显示"准备绑到哪一根"，否则会一直显示初值（短距离内滑），与实际目标不符。
        plan.trigger?.let { pointerTrigger = it }
        if (plan.outcome == FloatingPointerBindOutcome.ALREADY_BOUND && plan.trigger != null) {
            pointerTrigger = plan.trigger
            if (pointerState == PracticeTaskState.PENDING) pointerState = PracticeTaskState.READY
        } else if (pointerState == PracticeTaskState.PENDING || pointerState == PracticeTaskState.READY) {
            pointerState = PracticeTaskState.PENDING
        }

        // 卡 2：悬浮球开关（总开关关着时球其实不会挂出来，所以两个都要求）。
        if (settings.floatBallEnabled && settings.serviceEnabled) {
            if (ballState == PracticeTaskState.PENDING) ballState = PracticeTaskState.READY
        } else if (ballState == PracticeTaskState.PENDING || ballState == PracticeTaskState.READY) {
            ballState = PracticeTaskState.PENDING
        }
    }

    // —— 卡 1 动作 ——

    /**
     * 把「出厂默认 + 用户已有」的完整规则表补落一次盘（幂等）。
     *
     * 为什么必须做：槽位写入只回写"当次读到的规则"，而 `SettingsSnapshotReader.readGestureRules`
     * 在该键非空时**不再合并出厂默认**。于是"第一次写某个槽位"会顺手抹掉所有还没落盘过的出厂手势
     * （短滑返回、下滑应用索引、上滑快捷菜单…）。先补齐这张表，这些默认手势才会被保住；
     * 对已经被抹掉过的机器，这一步也顺手把它们恢复回来。
     */
    private suspend fun materializeGestureRules(settings: com.slideindex.app.settings.AppSettings): Boolean {
        // 用户已有的规则优先（含"用户主动设成无"的记录），缺的槽位用出厂默认补上。
        val merged = (settings.gestureRules + SideGestureDefaults.defaultRules()).distinctBy { it.id }
        if (merged.isEmpty()) return true
        return attempt("materializeGestureRules") { settingsRepository.setGestureRules(merged) }
    }

    /** 打开引导时修一次规则表（承接历史版本被抹掉的出厂手势）。 */
    fun repairGestureRulesOnce() {
        if (gestureRulesRepaired) return
        gestureRulesRepaired = true
        scope.launch {
            try {
                materializeGestureRules(settingsRepository.readSnapshot())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "repair gesture rules failed", e)
            }
        }
    }

    /** 把「悬浮指针」绑到边缘触钮的一个空槽位上；已有绑定则直接沿用。 */
    fun bindPointer() {
        if (pointerBusy) return
        pointerBusy = true
        scope.launch {
            try {
                val settings = settingsRepository.readSnapshot()
                // 手势总开关默认是关的：不打开的话触钮条根本不会挂出来，"绑好了却拖不动"。
                if (!settings.serviceEnabled &&
                    !attempt("serviceEnabled") { settingsRepository.setServiceEnabled(true) }
                ) {
                    return@launch
                }
                val side = when {
                    settings.rightEdgeEnabled -> PanelSide.RIGHT
                    settings.leftEdgeEnabled -> PanelSide.LEFT
                    else -> {
                        // 两条边都关了：默认开右侧，否则绑了也没有入口。
                        if (!attempt("rightEdgeEnabled") { settingsRepository.setRightEdgeEnabled(true) }) {
                            return@launch
                        }
                        PanelSide.RIGHT
                    }
                }
                val handles = if (side == PanelSide.RIGHT) settings.rightTriggerHandles else settings.leftTriggerHandles
                val handleId = handles.firstOrNull()?.id ?: TriggerHandle.DEFAULT_ID
                if (handles.firstOrNull { it.id == handleId }?.enabled == false &&
                    !attempt("triggerHandleEnabled") {
                        settingsRepository.setTriggerHandleEnabled(side, handleId, true)
                    }
                ) {
                    return@launch
                }

                val factory = factoryActionsFor(side)
                val plan = planFloatingPointerBinding(
                    actionOf = { settings.slotAction(side, it, handleId) },
                    factoryActionOf = { factory[it] ?: GestureAction.None },
                )
                val target = plan.trigger
                if (plan.outcome == FloatingPointerBindOutcome.NO_SLOT || target == null) {
                    // 用户自己改过的槽位一律不碰，只给提示。
                    hintRes = R.string.onboarding_practice_pointer_slots_full
                    return@launch
                }
                relocatedAction = null
                relocatedTrigger = null
                if (plan.outcome == FloatingPointerBindOutcome.BIND_NEW) {
                    if (!materializeGestureRules(settings)) return@launch
                    if (!attempt("slotConfig/floatingPointer") {
                            settingsRepository.setSlotConfig(
                                side = side,
                                trigger = target,
                                action = GestureAction.FloatingPointer,
                                triggerMode = GestureTriggerMode.CONTINUOUS,
                                handleId = handleId,
                            )
                        }
                    ) {
                        return@launch
                    }
                    // 顶掉了出厂动作就把它搬到空位，别让用户白丢一个功能（例如斜下滑的任务切换器）。
                    val displaced = plan.replacesAction
                    if (displaced != null) {
                        val relocateTo = plan.relocateTo
                        if (relocateTo != null) {
                            if (!attempt("slotConfig/relocate") {
                                    settingsRepository.setSlotConfig(
                                        side = side,
                                        trigger = relocateTo,
                                        action = displaced,
                                        triggerMode = GestureTriggerMode.DEFAULT,
                                        handleId = handleId,
                                    )
                                }
                            ) {
                                return@launch
                            }
                            relocatedAction = displaced
                            relocatedTrigger = relocateTo
                        } else {
                            hintRes = R.string.onboarding_practice_pointer_relocate_failed
                        }
                    }
                }
                pointerSide = side
                pointerTrigger = target
                pointerHandleId = handleId
                pointerState = PracticeTaskState.READY
                hintRes = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "bind floating pointer failed", e)
                hintRes = R.string.onboarding_practice_failed
            } finally {
                pointerBusy = false
            }
        }
    }

    /** 演示：真的把指针浮到屏幕上（与真实动作同一入口），停留一会儿再收起。 */
    fun demoPointer() {
        if (pointerBusy) return
        if (!serviceAvailable) {
            hintRes = R.string.onboarding_practice_service_reconnecting
            return
        }
        onPreviewTriggerBar(true)
        pointerBusy = true
        pointerState = PracticeTaskState.RUNNING
        scope.launch {
            try {
                val metrics = context.resources.displayMetrics
                val anchorX = if (pointerSide == PanelSide.LEFT) 2f else metrics.widthPixels - 2f
                val anchorY = metrics.heightPixels * 0.5f
                FloatingPointerOverlayWindow.show(
                    context = context,
                    settings = settingsRepository.readSnapshot(),
                    anchorRawX = anchorX,
                    anchorRawY = anchorY,
                    continueTouch = false,
                )
                delay(DEMO_VISIBLE_MS)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "demo floating pointer failed", e)
            } finally {
                FloatingPointerOverlayWindow.dismiss()
                delay(DEMO_SETTLE_MS)
                pointerBusy = false
                pointerState = PracticeTaskState.READY
            }
        }
    }

    /** 等用户自己从边缘把指针拖出来一次。 */
    fun awaitPointerTest() {
        pointerTestJob?.cancel()
        if (!serviceAvailable) {
            hintRes = R.string.onboarding_practice_service_reconnecting
            return
        }
        // 等待期间必须能看见触钮在哪
        onPreviewTriggerBar(true)
        pointerState = PracticeTaskState.RUNNING
        pointerBusy = true
        // 先确保屏上没有上一轮遗留的指针，否则会立刻误判成"成功了"。
        FloatingPointerOverlayWindow.dismiss()
        pointerTestJob = scope.launch {
            try {
                delay(POINTER_TEST_SETTLE_MS)
                val seen = withTimeoutOrNull(POINTER_TEST_TIMEOUT_MS) {
                    var visible = FloatingPointerOverlayWindow.isVisible
                    while (!visible) {
                        delay(POLL_INTERVAL_MS)
                        visible = FloatingPointerOverlayWindow.isVisible
                    }
                    true
                } ?: false
                if (seen) {
                    pointerState = PracticeTaskState.DONE
                    hintRes = null
                    Log.i(TAG, "awaitPointerTest: pointer shown")
                } else {
                    Log.i(TAG, "awaitPointerTest: timed out after ${POINTER_TEST_TIMEOUT_MS}ms")
                    pointerState = PracticeTaskState.READY
                    hintRes = R.string.onboarding_practice_timeout
                }
            } finally {
                FloatingPointerOverlayWindow.dismiss()
                pointerBusy = false
            }
        }
    }

    // —— 卡 2 动作 ——

    fun enableFloatBall() {
        if (ballBusy) return
        ballBusy = true
        scope.launch {
            try {
                // 总开关 + 悬浮球开关：两者都开，球才会真的挂到屏幕上（见 FloatBallController.apply）。
                if (!attempt("serviceEnabled") { settingsRepository.setServiceEnabled(true) }) return@launch
                if (!attempt("floatBallEnabled") { settingsRepository.setFloatBallEnabled(true) }) return@launch
                ballState = PracticeTaskState.READY
                hintRes = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "enable float ball failed", e)
                hintRes = R.string.onboarding_practice_failed
            } finally {
                ballBusy = false
            }
        }
    }

    /** 等用户把悬浮球拖到文字上取到一次词。 */
    fun awaitPickTest() {
        pickTestJob?.cancel()
        ballState = PracticeTaskState.RUNNING
        ballBusy = true
        pickedText = null
        pickTestJob = scope.launch {
            try {
                val result = withTimeoutOrNull(PICK_TEST_TIMEOUT_MS) {
                    FloatBallPickEvents.results.first { !it.text.isNullOrBlank() }
                }
                if (result != null) {
                    pickedText = result.text?.trim()?.let { text ->
                        if (text.length > MAX_PICKED_PREVIEW) {
                            text.take(MAX_PICKED_PREVIEW).trimEnd() + "…"
                        } else {
                            text
                        }
                    }
                    ballState = PracticeTaskState.DONE
                    hintRes = null
                    Log.i(TAG, "awaitPickTest: picked '${pickedText}'")
                } else {
                    Log.i(TAG, "awaitPickTest: timed out after ${PICK_TEST_TIMEOUT_MS}ms")
                    ballState = PracticeTaskState.READY
                    hintRes = R.string.onboarding_practice_timeout
                }
            } catch (e: CancellationException) {
                throw e
            } finally {
                ballBusy = false
            }
        }
    }

    /** 取消"正在等你操作"的等待，回到已配置状态（用户点取消/跳过时用）。 */
    fun cancelPendingTests() {
        pointerTestJob?.cancel()
        pickTestJob?.cancel()
        indexTestJob?.cancel()
        indexHintJob?.cancel()
        pointerTestJob = null
        pickTestJob = null
        indexTestJob = null
        indexHintJob = null
        pointerBusy = false
        ballBusy = false
        indexBusy = false
        indexHintActive = false
        if (pointerState == PracticeTaskState.RUNNING) pointerState = PracticeTaskState.READY
        if (ballState == PracticeTaskState.RUNNING) ballState = PracticeTaskState.READY
        FloatingPointerOverlayWindow.dismiss()
    }

    /**
     * 在屏幕上把触钮标出来。
     *
     * 成功判定不依赖这个按钮：常驻的 [startGestureWatcher] 一直在听，
     * 用户任何时候滑一次都会打勾（实测反馈过"我先滑了、再点按钮，结果不算"）。
     */
    fun showIndexHint() {
        if (!serviceAvailable) {
            hintRes = R.string.onboarding_practice_service_reconnecting
            return
        }
        onPreviewTriggerBar(true)
        indexHintJob?.cancel()
        indexHintActive = true
        indexHintJob = scope.launch {
            delay(INDEX_HINT_DURATION_MS)
            indexHintActive = false
        }
    }

    /** 离开引导时收尾：别把演示用的指针留在屏幕上。 */
    fun release() {
        endSession()
    }

    private companion object {
        const val TAG = "OnboardingPractice"

        const val DEMO_VISIBLE_MS = 1_600L
        const val DEMO_SETTLE_MS = 400L
        const val POINTER_TEST_SETTLE_MS = 600L

        /**
         * 等待用户做手势的上限。
         *
         * 别设太短：用户要读完文案、看清屏幕上的"按住这里"标记、再动手，中间还可能试错一次，
         * 20s 实测会在"用户已经开始动手"的时候提前超时（表现为"我明明成功了，卡片却没变"）。
         */
        const val POINTER_TEST_TIMEOUT_MS = 45_000L
        const val PICK_TEST_TIMEOUT_MS = 45_000L
        /** 点「在屏幕上标出触钮」后，标记亮多久。 */
        const val INDEX_HINT_DURATION_MS = 20_000L
        const val POLL_INTERVAL_MS = 250L
        const val MAX_PICKED_PREVIEW = 120
    }
}
