package com.slideindex.app.overlay.quickmenu

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.slideindex.app.quickmenu.CountdownLogic
import com.slideindex.app.quickmenu.StopwatchLogic

/**
 * 秒表与倒计时的运行时状态（与 Compose 组合无关的单例）。
 *
 * ## 为什么不能把状态留在 Composable 里
 *
 * 面板里从某个工具页返回列表时，那一页的 Composable 会被销毁：`remember` 的状态与
 * `LaunchedEffect` 里的计时协程一起消失。用户看到的现象就是本轮 review 里的第 6 条——
 * **离开倒计时页计时就停了、到点也不会记进历史**；秒表同理（切走再回来读数归零）。
 *
 * 因此计时状态与"滴答"都提到这里，Compose 侧只做只读订阅。滴答只在**确实有计时在跑**时存在，
 * 停下来就移除，不会常驻一个 200ms 的循环。
 *
 * 到点写历史发生在滴答里，与"当前正显示哪一页"无关——所以把倒计时挂在那儿、回列表里挑别的
 * 工具，到点照样会记一条。
 */
object QuickMenuTimerRuntime {

    /**
     * 滴答间隔。
     *
     * 用 32ms（≈30fps）而不是 100ms：界面显示到**百分秒**，100ms 一跳意味着每次进位都是 10，
     * 最后一位永远只可能是 0 —— 用户看到的就是"最后一位数字长时间不动"（真机反馈）。
     * 30fps 下百分秒会连续滚动，且比逐帧 16ms 省一半开销。
     */
    private const val TICK_MS = 32L

    private val mainHandler = Handler(Looper.getMainLooper())

    /** 用于写历史；面板打开时由宿主注入，不依赖某个页面是否还在组合中。 */
    private var appContext: Context? = null

    private var stopwatch = StopwatchLogic.State()
    private var countdown = CountdownLogic.presetMinutes(CountdownLogic.PRESET_MINUTES.first())
    private var countdownExpiryRecorded = false
    private var ticker: Runnable? = null

    /** 供 Compose 订阅的只读状态。 */
    var stopwatchState by mutableStateOf(stopwatch)
        private set

    var countdownState by mutableStateOf(countdown)
        private set

    /** 实时时钟快照；运行中由滴答更新，暂停时不再变化。 */
    var nowMs by mutableLongStateOf(SystemClock.elapsedRealtime())
        private set

    private val isRunning: Boolean get() = stopwatch.running || countdown.running

    fun attach(context: Context) {
        appContext = context.applicationContext
    }

    // ---------------------------------------------------------------- 秒表

    fun startStopwatch() = updateStopwatch { StopwatchLogic.start(it, SystemClock.elapsedRealtime()) }

    fun pauseStopwatch() = updateStopwatch { StopwatchLogic.pause(it, SystemClock.elapsedRealtime()) }

    fun lapStopwatch() = updateStopwatch { StopwatchLogic.lap(it, SystemClock.elapsedRealtime()) }

    /**
     * 结束并复位。有有效计时就写一条历史——**这是秒表唯一写历史的入口**，
     * 所以界面上要能引导用户"先暂停再结束"（运行中那颗按钮的语义是计次）。
     */
    fun finishStopwatch() {
        val now = SystemClock.elapsedRealtime()
        val current = stopwatch
        val elapsed = StopwatchLogic.elapsedAt(current, now)
        if (elapsed > 0L) {
            recordHistory(QuickMenuHistoryEntry.Kind.STOPWATCH, elapsed, current.laps)
        }
        stopwatch = StopwatchLogic.reset()
        stopwatchState = stopwatch
        nowMs = now
        refreshTicker()
    }

    private fun updateStopwatch(transform: (StopwatchLogic.State) -> StopwatchLogic.State) {
        stopwatch = transform(stopwatch)
        stopwatchState = stopwatch
        nowMs = SystemClock.elapsedRealtime()
        refreshTicker()
    }

    // ---------------------------------------------------------------- 倒计时

    fun setCountdownMinutes(minutes: Int) {
        countdown = CountdownLogic.presetMinutes(minutes)
        countdownState = countdown
        countdownExpiryRecorded = false
        nowMs = SystemClock.elapsedRealtime()
        // 换了时长，之前按旧时长排的到点闹钟必须撤掉，否则会按旧时刻响。
        appContext?.let { QuickMenuTimerAlarm.cancel(it) }
        refreshTicker()
    }

    fun startCountdown() {
        val now = SystemClock.elapsedRealtime()
        countdown = CountdownLogic.start(countdown, now)
        countdownState = countdown
        countdownExpiryRecorded = false
        nowMs = now
        // 按**剩余**时长排（不是总时长）：暂停 2:30 后继续，就该 2:30 之后响。
        appContext?.let {
            QuickMenuTimerAlarm.schedule(it, countdown.remainingMs, countdown.durationMs)
        }
        refreshTicker()
    }

    fun pauseCountdown() {
        val now = SystemClock.elapsedRealtime()
        countdown = CountdownLogic.pause(countdown, now)
        countdownState = countdown
        nowMs = now
        // 暂停必须撤闹钟 —— 这正是用户报的那个 bug：以前撤不掉，暂停后到点照样响。
        appContext?.let { QuickMenuTimerAlarm.cancel(it) }
        refreshTicker()
    }

    fun resetCountdown() {
        countdown = CountdownLogic.reset(countdown)
        countdownState = countdown
        countdownExpiryRecorded = false
        nowMs = SystemClock.elapsedRealtime()
        appContext?.let { QuickMenuTimerAlarm.cancel(it) }
        refreshTicker()
    }

    // ---------------------------------------------------------------- 滴答

    private val tick = object : Runnable {
        override fun run() {
            ticker = null
            val now = SystemClock.elapsedRealtime()
            nowMs = now

            if (countdown.running && !countdownExpiryRecorded && CountdownLogic.isExpired(countdown, now)) {
                countdownExpiryRecorded = true
                // 通知与历史都由到点闹钟的接收器负责（它是唯一写入口，面板开着/关着/进程被杀都一致）。
                // 这里只把界面停到 00:00，闹钟不撤 —— 它就是负责响的那一下。
                countdown = CountdownLogic.pause(countdown, now)
                countdownState = countdown
            }

            if (isRunning) {
                mainHandler.postDelayed(this, TICK_MS)
                ticker = this
            }
        }
    }

    private fun refreshTicker() {
        if (!isRunning) {
            ticker?.let(mainHandler::removeCallbacks)
            ticker = null
            return
        }
        if (ticker != null) return
        ticker = tick
        mainHandler.post(tick)
    }

    private fun recordHistory(
        kind: QuickMenuHistoryEntry.Kind,
        totalMs: Long,
        laps: List<Long>,
    ) {
        val context = appContext ?: return
        QuickMenuWork.launch {
            QuickMenuHistoryStore.add(
                context,
                QuickMenuHistoryEntry(
                    kind = kind,
                    totalMs = totalMs,
                    lapsMs = laps,
                    atEpochMs = System.currentTimeMillis(),
                ),
            )
        }
    }
}
