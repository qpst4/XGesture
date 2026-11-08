package com.slideindex.app.overlay.quickmenu

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.slideindex.app.quickmenu.StopwatchLogic

/**
 * 「快捷菜单 → 番茄钟」。
 *
 * ## 移植来源与"照搬了什么"
 *
 * 行为规格照着 QuickDash（https://github.com/balajitechlabs/quickdash，GPL-3.0）的
 * `features/pomodoro/presentation/QuickPomodoroScreen.kt`：
 * 三个档位（25 分钟专注 / 5 分钟短休 / 15 分钟长休，可手动点选）、专注与休息**自动接续**、
 * 播放/暂停/重置、进度条、会话计数。原实现只有 FOCUS / BREAK 两种状态，长休也是**手动**的档位、
 * 从不自动进入；本实现保留了这套手动档位，并按"每 [PomodoroLogic.FOCUSES_PER_LONG_BREAK] 个专注
 * 自动进入长休"这一经典规则接续（比原实现更完整，规则写在纯逻辑里、有单测）。
 *
 * ## 与参考实现的两点刻意不同
 *
 * 1. **不照它把状态留在 Composable 里**。QuickDash 把计时留在 `remember` + 一个 `CountDownTimer`，
 *    并在 `onDispose` 里 `cancel()` —— 也就是说它的计时**离开页面就停**。那在本面板里更差：
 *    "返回工具列表"就会销毁这一页的组合。所以照**本仓库既有的正确做法**（见 [QuickMenuTimerRuntime]
 *    的注释）：状态机与滴答放进与组合无关的进程级单例 [QuickMenuPomodoroRuntime]，页面只做只读订阅。
 *    于是切到别的工具页、返回列表、面板隐藏再唤出，计时都照常走，回来时按**真实经过的时间**补算
 *    （跨过好几段也能一次推完）。
 * 2. **没有到点提醒**（闹钟 / 通知）。这一点与 QuickDash 一致 —— 它的番茄钟同样没有任何提醒
 *    （已核实）。计时到点只在**进程还活着**时自动接续下一段；进程被杀期间不会响铃、也不会自己往下走。
 * 3. **进程被杀后不保留**：状态只在内存里（`QuickMenuPomodoroRuntime`），重启后从「25:00 专注」
 *    重新开始 —— 这一点与 QuickDash 相同（它连"离开页面"都保不住）。
 * 4. 原实现的两条时长档位语义保持原样：长休是一个**可手动点选**的档位；自动接续时按
 *    [PomodoroLogic.FOCUSES_PER_LONG_BREAK] 规则进入长休。
 *
 * 显示上只用两个已有字符串（[com.slideindex.app.overlay.panels.R.string.quick_menu_pomodoro_title] /
 * `_desc`，它们已经在工具列表里用了），按钮一律图标，不再新增文案：当前处于哪一段用图标区分
 * （专注 = 沙漏，休息 = 咖啡杯），会话计数写成 `×已完成的专注数 / 每轮专注数`。
 */

/**
 * 番茄钟的纯逻辑（状态机 + 时长推进），可脱离 Android 直接单测。
 *
 * 与 [com.slideindex.app.quickmenu.CountdownLogic] 同一套设计：**"当前时间"一律作为参数传入**，
 * 状态自己不读时钟。因此"到点自动接续""离开页面很久之后回来补算""暂停冻结剩余量"这些边界都能
 * 在纯 JVM 测试里直接断言，UI 侧只负责每帧调 [remainingAt]。
 */
object PomodoroLogic {

    /** 专注时长（分钟）——与参考实现一致。 */
    const val FOCUS_MINUTES = 25

    /** 短休时长（分钟）——与参考实现一致。 */
    const val SHORT_BREAK_MINUTES = 5

    /** 长休时长（分钟）——与参考实现一致（原实现里它是手动档位）。 */
    const val LONG_BREAK_MINUTES = 15

    /** 每完成几个专注进入一次长休（经典番茄规则；原实现没有这条自动规则）。 */
    const val FOCUSES_PER_LONG_BREAK = 4

    /**
     * 补算时最多连续推进多少段。
     *
     * 兜底用：面板挂了一整夜再回来，剩余时间可能已经是时长的几十倍。数到上限就"认了"这一轮
     * 从头开始（[State.remainingMs] = 0），而不是拿一个巨大的溢出量去做除法算轮数。
     */
    const val MAX_CATCH_UP_PHASES = 60

    /** 番茄钟的三段。 */
    enum class Phase {
        FOCUS,
        SHORT_BREAK,
        LONG_BREAK,
        ;

        /** 该段的默认时长（毫秒）。 */
        fun durationMs(): Long = minutes() * 60_000L

        /** 该段的默认时长（分钟）。 */
        fun minutes(): Int = when (this) {
            FOCUS -> FOCUS_MINUTES
            SHORT_BREAK -> SHORT_BREAK_MINUTES
            LONG_BREAK -> LONG_BREAK_MINUTES
        }
    }

    /**
     * @param phase 当前处于哪一段。
     * @param durationMs 本段设定的总时长。
     * @param remainingMs 剩余量（可正可负：为负表示"已经超时这么多"，是补算用的真实信息）。
     * @param durationOverrideMs 手动点选档位时覆盖的时长（`阶段 → 时长`），自动接续时沿用同一档位的覆盖值。
     * @param focusCount 已完成的专注段数（跨轮累计，用于"每 N 个专注进一次长休"与界面计数）。
     * @param endsAtMs 本段的**结束时刻**（与 [State.remainingAt] 的 `nowMs` 同一时间基准，
     *   运行时用的是 `SystemClock.elapsedRealtime()`）；暂停时为 null。
     * @param running 是否在跑。
     */
    data class State(
        val phase: Phase = Phase.FOCUS,
        val durationMs: Long = Phase.FOCUS.durationMs(),
        val remainingMs: Long = Phase.FOCUS.durationMs(),
        val durationOverrideMs: Map<Phase, Long> = emptyMap(),
        val focusCount: Int = 0,
        val endsAtMs: Long? = null,
        val running: Boolean = false,
    ) {
        /**
         * 剩余量**不夹到 0 以下**：为负时它的绝对值就是"超时了多久"，[advanceTo] 的溢出补算依赖它。
         *
         * 早先这里写了 `coerceAtLeast(0L)`，看起来"更安全"，实际把溢出量抹成了 0 —— 于是同步补算
         * 永远减不掉任何时间（`+3 分钟`的偏移会被丢掉，单测 `remainingAt_keepsOverdueAsNegative`
         * 盯着这一点）。界面要的是 `max(0, remainingAt)`，那个 0 由显示层夹。
         */
        fun remainingAt(nowMs: Long): Long {
            val endsAt = endsAtMs
            return if (running && endsAt != null) endsAt - nowMs else remainingMs
        }

        /**
         * 本段在墙钟上会结束于哪一刻（纪元毫秒）。
         *
         * 暂停时用"现在 + 快照剩余量"，好让**两个状态之间可以比先后**
         * （[PomodoroLogic.adoptIfLater] 与落盘记录的合并都靠它）。
         */
        fun endsAtEpochMs(nowMs: Long): Long = if (running && endsAtMs != null) endsAtMs else nowMs + remainingMs
    }

    /**
     * 一段**真正走完**的记录（跳过不算）；历史就按它写。
     *
     * @param endedAtEpochMs 该段结束的墙钟时刻 —— 历史要用它，而不是"发现它的那一刻"，
     *   否则进程被杀一夜之后再看历史，几条记录会挤在同一秒上。
     */
    data class Completion(
        val phase: Phase,
        val durationMs: Long,
        val endedAtEpochMs: Long,
    )

    /**
     * 推进的结果。
     *
     * @param completed 这一次推进中真正走完的所有段（按时间先后）。可能不止一段：面板很久没打开、
     *   回来时一次补算要跨过好几段，它们都要能按真实结束时刻回报出来。
     */
    data class AdvanceResult(
        val state: State,
        val completed: List<Completion> = emptyList(),
    )

    /** 初始状态：一段还没开始的专注。 */
    fun initial(): State = State()

    /**
     * 手动点选档位：设定到该阶段（不自动开始）。
     *
     * [durationMs] 给了就覆盖该阶段的默认时长（这样"选中的是 25:00 专注"与"选中的是 15:00 长休"在
     * 界面上都能一眼对上）。
     */
    fun select(state: State, phase: Phase, durationMs: Long = phase.durationMs()): State {
        val safe = durationMs.coerceAtLeast(0L)
        return state.copy(
            phase = phase,
            durationMs = safe,
            remainingMs = safe,
            durationOverrideMs = state.durationOverrideMs + (phase to safe),
            endsAtMs = null,
            running = false,
        )
    }

    /**
     * 开始或继续；已经到点（快照剩余量 ≤ 0，含"超时"的负值）时按本段时长从头开始。
     *
     * 到点后按播放的语义对齐参考实现：**重来本段**，而不是立刻又"到点"。
     *
     * 不变量：暂停时快照 [State.remainingMs] 就是（可能为负的）真实剩余量 —— [pause]、
     * [advanceTo]、[skip]、[select]、[reset] 都维护它，所以这里只判快照值即可。
     */
    fun start(state: State, nowMs: Long): State {
        if (state.running) return state
        return if (state.remainingMs <= 0L) {
            val duration = durationFor(state, state.phase)
            state.copy(
                durationMs = duration,
                remainingMs = duration,
                running = true,
                endsAtMs = nowMs + duration,
            )
        } else {
            state.copy(running = true, endsAtMs = nowMs + state.remainingMs)
        }
    }

    /** 暂停并冻结剩余量；未运行时保持原状。 */
    fun pause(state: State, nowMs: Long): State =
        if (!state.running) {
            state
        } else {
            state.copy(remainingMs = state.remainingAt(nowMs), running = false, endsAtMs = null)
        }

    /** 复位到当前这一段刚设好的时长（不自动开始），已完成的专注计数**保留**。 */
    fun reset(state: State): State {
        val duration = durationFor(state, state.phase)
        return state.copy(
            durationMs = duration,
            remainingMs = duration,
            running = false,
            endsAtMs = null,
        )
    }

    /**
     * 跳过当前这一段，直接进入下一段（不自动开始）。
     *
     * 跳过的专注**不计入**会话数：只有真的走完一段专注才算数。
     */
    fun skip(state: State): State {
        // 用当前的计数决定下一段（不 +1）：跳过的专注不算完成，所以也不该触发长休。
        val next = nextPhase(state.phase, state.focusCount)
        val duration = durationFor(state, next)
        return state.copy(
            phase = next,
            durationMs = duration,
            remainingMs = duration,
            running = false,
            endsAtMs = null,
        )
    }

    /**
     * 把时间推进到 [nowMs]，并**回报真正走完的那些段**（核心行为）。
     *
     * - **溢出会带到下一段**：离开页面 3 分钟才回来，短休已经跑掉 3 分钟，而不是重新 5 分钟。
     * - **一次推进可以跨过很多段**：循环一直算到"当前这一段还没到点"为止（面板很久没打开再回来
     *   就是这种情形），跨过的每一段都会出现在 [AdvanceResult.completed] 里。
     * - **兜底**：连续接续超过 [MAX_CATCH_UP_PHASES] 段时不再逐段推，直接让当前这一段从头开始。
     *   即便在这一支里，也要保证"这一段是没到点的"——否则会留下一个"运行中却已经超时"的状态
     *   （单测 `advanceTo_neverReturnsAnExpiredRunningState` 盯着这一点）。
     * - 每走完一段**专注**就把累计计数 +1，并据此决定下一段是短休还是长休。
     */
    fun advanceCompleted(state: State, nowMs: Long): AdvanceResult {
        if (!state.running) return AdvanceResult(state)
        var current = state
        var phases = 0
        val completed = mutableListOf<Completion>()
        while (true) {
            // ⚠️ 判据是 `<= 0`（到点那一刻也算走完），不是 `< 0`：否则"刚好走到 0"的那一下不会接续，
            // 状态会卡在 00:00 上不动。
            val overdue = -current.remainingAt(nowMs)
            if (overdue < 0L) return AdvanceResult(current, completed)
            if (phases >= MAX_CATCH_UP_PHASES) {
                // 挂太久：不猜轮数，直接让当前这一段从头开始（快照值与结束时刻必须一起写对）。
                val cold = durationFor(current, current.phase)
                return AdvanceResult(
                    state = current.copy(
                        durationMs = cold,
                        remainingMs = cold,
                        endsAtMs = nowMs + cold,
                    ),
                    completed = completed,
                )
            }
            // ⚠️ 先把"刚走完的那一段"存下来再覆写 current：`current.copy(phase = next)` 之后
            // `current.phase` 就是**下一段**了，用它去判断"刚走完的是不是专注"会永远判错
            // （表现：专注数一直不涨，单测 `thirdPhase_keepsCountingFocuses` 盯着这一点）。
            val finished = current.phase
            // 这一段的结束时刻 = 现在往回退"超时了多久"，即它真正该结束的那一刻。
            completed += Completion(
                phase = finished,
                durationMs = current.durationMs,
                endedAtEpochMs = nowMs - overdue,
            )
            // 只有真的走完一段**专注**才把计数 +1（跳过走的是 skip，不经过这里）。
            val count = if (finished == Phase.FOCUS) current.focusCount + 1 else current.focusCount
            val next = nextPhase(finished, count)
            val nextDuration = durationFor(current, next)
            // ⚠️ 恰好停在边界（overdue == 0）时，下一段应当是**完整的**：这一个瞬间还没走进下一段。
            // 若照旧写 `nextDuration - overdue`，下一段的剩余量就是 0、循环会立刻把它也判成"走完"，
            // 于是整段整段被吞掉（"跑了 25 分钟回来却是 25:00 专注"，单测
            // `advanceTo_exactlyOnBoundary_startsTheNextPhaseInFull` 盯着这一点）。
            val nextRemaining = if (overdue == 0L) nextDuration else nextDuration - overdue
            current = current.copy(
                phase = next,
                durationMs = nextDuration,
                remainingMs = nextRemaining,
                // 结束时刻由剩余量推出，两者始终一致（暂停时不会露出一个和读数不同的值）。
                // 恰好停在边界时剩余量是完整的正数；真超时时它可以是负数，下一轮循环会继续推。
                endsAtMs = nowMs + nextRemaining,
                focusCount = count,
            )
            phases++
        }
    }

    /** [advanceCompleted] 的简化版：只要状态，不要"走完了哪些段"。 */
    fun advanceTo(state: State, nowMs: Long): State = advanceCompleted(state, nowMs).state

    /** 进度（0f~1f），供进度条使用；时长设为 0 时返回 0。 */
    fun progress(state: State, nowMs: Long): Float {
        if (state.durationMs <= 0L) return 0f
        // 夹到 0：剩余量可以为负（超时），进度条只画到满格为止。
        val remaining = state.remainingAt(nowMs).coerceAtLeast(0L)
        return (1f - remaining.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
    }

    /**
     * 若 [candidate] 在墙钟上比 [current] 更靠后，就采用它（否则原样返回）。
     *
     * 用途：界面手里的状态与"补算算出来的状态"可能不是同一份，谁更靠后用 [State.endsAtEpochMs]
     * 比较（例如打开面板时的幂等补算、或用户在补算之前刚点了开始）。纯函数，可直接单测。
     */
    fun adoptIfLater(current: State, candidate: State, nowMs: Long): State =
        if (candidate.endsAtEpochMs(nowMs) > current.endsAtEpochMs(nowMs)) candidate else current

    /**
     * 下一段是什么（纯函数，与"什么时候"无关）。
     *
     * - 专注结束：累计数达到 [FOCUSES_PER_LONG_BREAK] 的整数倍就进长休，否则短休。
     * - 休息结束：回到专注。
     *
     * ⚠️ 必须排除 0：初始状态第一次"跳过专注"时计数还是 0，若按 `0 % 4 == 0` 判定就会直接进长休
     * （单测 `skip_fromFreshFocus_goesToShortBreak` 盯着这一点）。
     *
     * @param focusCountAfter 若刚走完的是专注段，这里传**加过 1 的**计数。
     */
    private fun nextPhase(finished: Phase, focusCountAfter: Int): Phase = when (finished) {
        Phase.FOCUS -> if (focusCountAfter > 0 && focusCountAfter % FOCUSES_PER_LONG_BREAK == 0) {
            Phase.LONG_BREAK
        } else {
            Phase.SHORT_BREAK
        }

        Phase.SHORT_BREAK, Phase.LONG_BREAK -> Phase.FOCUS
    }

    /** [phase] 当前的生效时长：手动覆盖优先，否则用默认值。 */
    private fun durationFor(state: State, phase: Phase): Long =
        state.durationOverrideMs[phase] ?: phase.durationMs()
}

/**
 * 番茄钟的运行时状态（与 Compose 组合无关的进程级单例）。
 *
 * 起因见文件顶部：本面板"返回工具列表"会销毁工具页的组合，状态若留在 `remember` 里就会跟着消失
 * （QuickDash 原实现正是如此，离开页面即停）。因此状态机与滴答都提到这里，Compose 侧只做只读订阅；
 * 滴答只在确实有计时在跑时存在，停下来就移除，不会常驻一个循环。
 */
object QuickMenuPomodoroRuntime {

    /**
     * 滴答间隔。
     *
     * 200ms 而不是 32ms（秒表用 32ms 是因为它显示到百分秒）：番茄钟只显示到**秒**，200ms 足够
     * 让翻秒看起来是准时的，又比逐帧省得多。
     */
    private const val TICK_MS = 200L

    private val mainHandler = Handler(Looper.getMainLooper())

    private var state = PomodoroLogic.initial()
    private var ticker: Runnable? = null

    /** 供 Compose 订阅的只读状态。 */
    var current by mutableStateOf(state)
        private set

    /** 实时时钟快照；运行中由滴答更新，暂停时不再变化。 */
    var nowMs by mutableLongStateOf(SystemClock.elapsedRealtime())
        private set

    /**
     * 面板每次打开时调用：按**真实经过的时间**补算一次。
     *
     * 这一页的组合在"返回工具列表"时会被销毁，但状态与滴答都在本对象里，所以离开期间计时照走；
     * 回来时这一步把期间跨过的段（可能好几段）一次推完，界面读数与"已完成专注数"都是对的。
     * 幂等：没跨过段时 `adopt` 什么也不改，所以重复调用是安全的。
     */
    fun resume() {
        nowMs = SystemClock.elapsedRealtime()
        adopt(PomodoroLogic.advanceTo(state, nowMs))
        refreshTicker()
    }

    fun toggle() {
        if (current.running) pause() else start()
    }

    /** 开始 / 继续。 */
    fun start() {
        apply(PomodoroLogic.start(state, SystemClock.elapsedRealtime()))
    }

    fun pause() {
        apply(PomodoroLogic.pause(state, SystemClock.elapsedRealtime()))
    }

    /** 跳过当前段：直接进入下一段，不自动开始（跳过的专注不计入会话数）。 */
    fun skip() {
        apply(PomodoroLogic.skip(state))
    }

    /** 手动点选档位（专注 / 短休 / 长休）：设定到该段并暂停，等用户按播放。 */
    fun select(phase: PomodoroLogic.Phase, durationMs: Long = phase.durationMs()) {
        apply(PomodoroLogic.select(state, phase, durationMs))
    }

    /** 复位到初始的一段专注（同时清掉已完成的会话计数）。 */
    fun reset() {
        apply(PomodoroLogic.initial())
    }

    /**
     * 落一次状态：写进内存态、发布给 Compose、刷新滴答。
     *
     * 本页**不落盘**（用户已决定番茄钟不做提醒、也不需要跨进程存活），所以状态只在内存里；
     * 进程被系统回收后就从「25:00 专注」重新开始 —— 与 QuickDash 相同（它连"离开页面"都保不住）。
     */
    private fun apply(next: PomodoroLogic.State) {
        state = next
        current = next
        nowMs = SystemClock.elapsedRealtime()
        refreshTicker()
    }

    /** 与 [apply] 同义，但用于"已经有状态对象"的场合（打开面板时的补算）。 */
    private fun adopt(next: PomodoroLogic.State) {
        if (next == state) return
        state = next
        current = next
        nowMs = SystemClock.elapsedRealtime()
    }

    /**
     * 只在主线程调用（滴答走主线程 Looper）：快照状态的写入因此天然在主线程上。
     *
     * 到点后的自动接续都在这里完成 —— 面板不可见时滴答仍在跑，所以"切到别的工具页、面板藏着、
     * App 在后台但进程还活着"都能正确接续。
     */
    private val tick = object : Runnable {
        override fun run() {
            ticker = null
            val now = SystemClock.elapsedRealtime()
            nowMs = now
            val before = state
            val advanced = PomodoroLogic.advanceTo(before, now)
            if (advanced != before) {
                state = advanced
                current = advanced
            }
            if (state.running) {
                mainHandler.postDelayed(this, TICK_MS)
                ticker = this
            }
        }
    }

    private fun refreshTicker() {
        if (!state.running) {
            ticker?.let(mainHandler::removeCallbacks)
            ticker = null
            return
        }
        if (ticker != null) return
        ticker = tick
        mainHandler.post(tick)
    }
}

@Composable
fun QuickMenuPomodoroScreen() {
    // 进入这一页时按真实经过的时间补算一次（离开期间跨过的段都在这一步推完）。
    // 用 LaunchedEffect(Unit)：本页每次被组合都会走一次，resume() 自己是幂等的。
    LaunchedEffect(Unit) { QuickMenuPomodoroRuntime.resume() }

    // 计时状态与滴答都在 QuickMenuPomodoroRuntime 里：离开这一页（返回列表、切到别的工具）也不会
    // 让番茄钟停下，回来时读数已经按真实经过的时间补算过。
    val state = QuickMenuPomodoroRuntime.current
    val nowMs = QuickMenuPomodoroRuntime.nowMs
    // 夹到 0：剩余量在"已经超时"时是负的（那是补算要用的真实信息），读数只显示到 00:00 为止。
    val remaining = state.remainingAt(nowMs).coerceAtLeast(0L)
    val progress = PomodoroLogic.progress(state, nowMs)
    val isBreak = state.phase != PomodoroLogic.Phase.FOCUS

    ToolScaffold {
        Text(
            text = StopwatchLogic.format(remaining),
            style = MaterialTheme.typography.displayMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            ),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary),
            )
        }

        // 当前段用图标区分（专注 = 沙漏，休息 = 咖啡杯），配"已完成专注数 / 每轮专注数"的计数；
        // 两者都不需要文案。
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isBreak) Icons.Filled.Coffee else Icons.Filled.HourglassBottom,
                contentDescription = null,
                tint = if (isBreak) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            Text(
                text = "×${state.focusCount}/${PomodoroLogic.FOCUSES_PER_LONG_BREAK}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        // 三档：25 分钟专注 / 5 分钟短休 / 15 分钟长休。按钮上直接写时长，不新增文案也不会误会
        // （选中态自己会变色，另外给一个同段的图标）。已经是这一段时再点即"重来"。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PomodoroLogic.Phase.entries.forEach { phase ->
                val active = state.phase == phase
                FilterChip(
                    selected = active,
                    onClick = {
                        // 已经是这一段就重来（原实现的点选语义：点即复位到该档位）。
                        if (active) {
                            QuickMenuPomodoroRuntime.reset()
                        } else {
                            QuickMenuPomodoroRuntime.select(phase)
                        }
                    },
                    leadingIcon = if (active) {
                        {
                            Icon(
                                imageVector = if (phase == PomodoroLogic.Phase.FOCUS) {
                                    Icons.Filled.HourglassBottom
                                } else {
                                    Icons.Filled.Coffee
                                },
                                contentDescription = null,
                            )
                        }
                    } else {
                        null
                    },
                    label = { Text(StopwatchLogic.format(phase.durationMs())) },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = { QuickMenuPomodoroRuntime.toggle() }) {
                Icon(
                    imageVector = if (state.running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = null,
                )
            }
            IconButton(onClick = { QuickMenuPomodoroRuntime.skip() }) {
                Icon(imageVector = Icons.Filled.SkipNext, contentDescription = null)
            }
            IconButton(onClick = { QuickMenuPomodoroRuntime.reset() }) {
                Icon(imageVector = Icons.Filled.RestartAlt, contentDescription = null)
            }
        }
    }
}
