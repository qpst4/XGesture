package com.slideindex.app.util

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.slideindex.app.privilege.PrivilegeGateway
import kotlin.math.abs
import kotlin.math.roundToInt

object VolumeControlHelper {
    enum class Stream {
        MEDIA,
        RING,
        NOTIFICATION,
        ALARM,
    }

    fun hasAccess(context: Context): Boolean =
        PermissionHelper.hasNotificationPolicyAccess(context.applicationContext)

    fun readRingerMode(context: Context): Int {
        val manager = audioManager(context) ?: return AudioManager.RINGER_MODE_NORMAL
        return manager.ringerMode
    }

    fun cycleRingerMode(context: Context): Int? {
        if (!hasAccess(context)) return null
        val manager = audioManager(context) ?: return null
        val nextMode = when (manager.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
            AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
            else -> AudioManager.RINGER_MODE_NORMAL
        }
        return runCatching {
            manager.ringerMode = nextMode
            nextMode
        }.getOrElse { error ->
            Log.w(TAG, "cycle ringer mode failed", error)
            null
        }
    }

    fun readInterruptionFilter(context: Context): Int {
        if (!hasAccess(context)) return NotificationManager.INTERRUPTION_FILTER_ALL
        val manager = notificationManager(context) ?: return NotificationManager.INTERRUPTION_FILTER_ALL
        return manager.currentInterruptionFilter
    }

    fun isDndEnabled(context: Context): Boolean = isDndFilter(readInterruptionFilter(context))

    fun isDndFilter(filter: Int): Boolean = when (filter) {
        NotificationManager.INTERRUPTION_FILTER_ALL,
        NotificationManager.INTERRUPTION_FILTER_UNKNOWN,
        -> false
        else -> true
    }

    /**
     * Toggles system Do Not Disturb.
     *
     * Uses `cmd notification set_dnd` when privileged shell access is available (Flyme/OEM
     * often ignores [NotificationManager.setInterruptionFilter]), otherwise falls back to the API.
     *
     * @return the current interruption filter after the attempt when the enabled state changed,
     *         or null when access is missing, the call failed, or the system state did not change.
     */
    fun toggleDnd(context: Context): Int? {
        if (!hasAccess(context)) return null
        val appContext = context.applicationContext
        val beforeEnabled = isDndEnabled(appContext)
        val targetFilter = if (beforeEnabled) {
            NotificationManager.INTERRUPTION_FILTER_ALL
        } else {
            NotificationManager.INTERRUPTION_FILTER_PRIORITY
        }
        val after = setInterruptionFilter(appContext, targetFilter) ?: return null
        val afterEnabled = isDndFilter(after)
        if (beforeEnabled == afterEnabled) {
            Log.w(TAG, "toggle dnd had no effect after=$after")
            return null
        }
        Log.i(TAG, "toggle dnd ok after=$after")
        return after
    }

    /**
     * Applies the requested interruption filter via shell when available, otherwise via API.
     *
     * @return the filter read back after the attempt when it matches the request or DND state,
     *         or null when access is missing or the call had no effect.
     */
    fun setInterruptionFilter(context: Context, filter: Int): Int? {
        if (!hasAccess(context)) return null
        val appContext = context.applicationContext
        if (TaskManagerUtil.hasPermission()) {
            setInterruptionFilterViaShell(appContext, filter)?.let { return it }
        }
        return setInterruptionFilterViaNotificationManager(appContext, filter)
    }

    fun ensureDndEnabled(context: Context): Boolean {
        if (isDndEnabled(context)) return true
        return setInterruptionFilter(
            context,
            NotificationManager.INTERRUPTION_FILTER_PRIORITY,
        ) != null
    }

    private fun setInterruptionFilterViaShell(context: Context, filter: Int): Int? {
        val beforeEnabled = isDndEnabled(context)
        val targetEnabled = isDndFilter(filter)
        val mode = shellModeForInterruptionFilter(filter)
        val result = TaskManagerUtil.runShellCommandLine(
            command = "cmd notification set_dnd $mode",
            useRoot = PrivilegeGateway.isRootMode(),
        )
        if (!result.success) {
            Log.w(
                TAG,
                "set dnd shell failed: mode=$mode filter=$filter exit=${result.exitCode} output=${result.output}",
            )
            return null
        }
        return readInterruptionFilterIfChanged(context, beforeEnabled, "shell:$mode")
            ?: readInterruptionFilter(context).takeIf { isDndFilter(it) == targetEnabled }
    }

    private fun setInterruptionFilterViaNotificationManager(context: Context, filter: Int): Int? {
        val manager = notificationManager(context) ?: return null
        return runCatching {
            val beforeEnabled = isDndEnabled(context)
            manager.setInterruptionFilter(filter)
            readInterruptionFilterIfChanged(context, beforeEnabled, "api:$filter")
                ?: readInterruptionFilter(context).takeIf { it == filter }
        }.getOrElse { error ->
            Log.w(TAG, "set dnd api failed filter=$filter", error)
            null
        }
    }

    private fun shellModeForInterruptionFilter(filter: Int): String = when (filter) {
        NotificationManager.INTERRUPTION_FILTER_ALL -> "off"
        NotificationManager.INTERRUPTION_FILTER_ALARMS -> "alarms"
        NotificationManager.INTERRUPTION_FILTER_NONE -> "silent"
        NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "priority"
        else -> if (isDndFilter(filter)) "priority" else "off"
    }

    private fun readInterruptionFilterIfChanged(
        context: Context,
        beforeEnabled: Boolean,
        via: String,
    ): Int? {
        val after = readInterruptionFilter(context)
        val afterEnabled = isDndFilter(after)
        if (beforeEnabled == afterEnabled) {
            Log.w(TAG, "toggle dnd had no effect via $via after=$after")
            return null
        }
        Log.i(TAG, "toggle dnd ok via $via after=$after")
        return after
    }

    fun readFraction(context: Context, stream: Stream): Float {
        val manager = audioManager(context) ?: return 0f
        val audioStream = toAudioStream(stream)
        val min = streamMin(manager, audioStream)
        val max = streamMax(manager, audioStream, min)
        return (manager.getStreamVolume(audioStream).coerceIn(min, max) - min).toFloat() / (max - min)
    }

    fun setFraction(context: Context, stream: Stream, fraction: Float) {
        if (stream.requiresPolicyAccess() && !hasAccess(context)) return
        val manager = audioManager(context) ?: return
        val audioStream = toAudioStream(stream)
        val min = streamMin(manager, audioStream)
        val max = streamMax(manager, audioStream, min)
        val level = (fraction.coerceIn(0f, 1f) * (max - min)).roundToInt() + min
        applyStreamLevel(manager, audioStream, level, min, max)
    }

    /**
     * 分层写入音量（**一次调用最多推进一个档位预算内的步数，且绝不反向**）：
     *
     * 1. 先用 [AudioManager.setStreamVolume] 做绝对设置 —— 正常 ROM 一次到位；
     * 2. 立即回读校验：ColorOS 等 ROM 对非前台应用会**静默忽略**绝对设置（不抛异常、值不变），
     *    因此是否生效只能靠回读判断，不能靠 try/catch；被拒后 [ABSOLUTE_REJECT_BACKOFF_MS]
     *    内不再重试，避免每条 MOVE 白打一次 binder；
     * 3. 未生效则改用相对步进 [AudioManager.adjustStreamVolume]：
     *    - 目标先**吸附到档位网格**（本机媒体音量 160 级 / 每档 10 级，69 这种值永远不可达）；
     *    - 每档走完立即回读，**测出真实步长**并缓存，后续不再靠猜；
     *    - 单调推进：已经越过目标就停，绝不在同一次调用里反向（旧实现在 60↔70 之间来回跳，
     *      一条 MOVE 能打十几次 adjust，把主线程和系统音量条一起打爆）；
     *    - 有总预算 [STEP_BUDGET_MS]，保证一次调用是一帧内的事。
     *
     * @return 实际生效的音量级数
     */
    fun applyStreamLevel(manager: AudioManager, stream: Int, target: Int, min: Int, max: Int): Int {
        val startedAt = SystemClock.uptimeMillis()
        val want = target.coerceIn(min, max)
        var actual = manager.getStreamVolume(stream).coerceIn(min, max)
        if (actual == want) return actual

        var absoluteTried = 0
        var absoluteOk = 0
        var adjusts = 0

        // ① 绝对设置（被拒后短暂退避，别每条事件都试）
        val now = SystemClock.uptimeMillis()
        if (now >= (absoluteRejectedUntilMs[stream] ?: 0L)) {
            absoluteTried = 1
            try {
                manager.setStreamVolume(stream, want, 0)
            } catch (error: Exception) {
                Log.w(TAG, "setStreamVolume(stream=$stream, want=$want) threw", error)
            }
            actual = manager.getStreamVolume(stream).coerceIn(min, max)
            if (actual == want) {
                absoluteOk = 1
                absoluteRejectedUntilMs.remove(stream)
                VolumeStepStats.record(1, 1, 1, 0, want, actual, SystemClock.uptimeMillis() - startedAt)
                return actual
            }
            absoluteRejectedUntilMs[stream] = now + ABSOLUTE_REJECT_BACKOFF_MS
            Log.i(TAG, "absolute set rejected stream=$stream want=$want actual=$actual; stepping")
        }

        // ② 单调步进到「吸附后的目标」
        val deadline = SystemClock.uptimeMillis() + STEP_BUDGET_MS
        while (adjusts < MAX_STEPS_PER_CALL && SystemClock.uptimeMillis() <= deadline) {
            val step = stepCache[stream] ?: 1
            val snapped = snapToGrid(want, min, max, step)
            if (actual == snapped) break
            val raising = snapped > actual
            try {
                manager.adjustStreamVolume(
                    stream,
                    if (raising) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                    0,
                )
            } catch (error: Exception) {
                Log.w(TAG, "adjustStreamVolume step=$adjusts failed stream=$stream", error)
                break
            }
            val next = manager.getStreamVolume(stream).coerceIn(min, max)
            if (next == actual) break // 顶/底，走不动了
            val measured = abs(next - actual)
            if (measured > 0) stepCache[stream] = measured
            adjusts++
            actual = next
            // 越过目标就停：宁可差半档，也不许反向震荡
            if ((raising && actual >= want) || (!raising && actual <= want)) break
        }

        VolumeStepStats.record(
            calls = 1,
            absoluteTried = absoluteTried,
            absoluteOk = absoluteOk,
            adjusts = adjusts,
            want = want,
            actual = actual,
            costMs = SystemClock.uptimeMillis() - startedAt,
        )
        return actual
    }

    /** 把目标吸附到档位网格：步长为 1 时等于不做处理。 */
    private fun snapToGrid(want: Int, min: Int, max: Int, step: Int): Int {
        if (step <= 1) return want
        val offset = ((want - min).toFloat() / step).roundToInt() * step + min
        return offset.coerceIn(min, max)
    }

    /** 诊断用：当前缓存的量程 / 步长 / 绝对设置退避状态。 */
    fun describeScale(manager: AudioManager, stream: Int): String {
        val min = streamMin(manager, stream)
        val max = streamMax(manager, stream, min)
        val step = stepCache[stream]
        val backoff = absoluteRejectedUntilMs[stream] ?: 0L
        val backoffLeft = (backoff - SystemClock.uptimeMillis()).coerceAtLeast(0L)
        return "stream=$stream range=$min..$max step=${step ?: "未知"} " +
            "absoluteRejectedLeft=${backoffLeft}ms"
    }

    private fun streamMin(manager: AudioManager, audioStream: Int): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            manager.getStreamMinVolume(audioStream)
        } else {
            0
        }

    private fun streamMax(manager: AudioManager, audioStream: Int, min: Int): Int =
        manager.getStreamMaxVolume(audioStream).coerceAtLeast(min + 1)

    fun toAudioStream(stream: Stream): Int = when (stream) {
        Stream.MEDIA -> AudioManager.STREAM_MUSIC
        Stream.RING -> AudioManager.STREAM_RING
        Stream.NOTIFICATION -> AudioManager.STREAM_NOTIFICATION
        Stream.ALARM -> AudioManager.STREAM_ALARM
    }

    private fun Stream.requiresPolicyAccess(): Boolean =
        this == Stream.RING || this == Stream.NOTIFICATION

    private fun audioManager(context: Context): AudioManager? =
        context.applicationContext.getSystemService(AudioManager::class.java)

    private fun notificationManager(context: Context): NotificationManager? =
        context.applicationContext.getSystemService(NotificationManager::class.java)

    private const val TAG = "VolumeControlHelper"

    /**
     * 单次调用最多走几档。
     *
     * 拖动时每档的位移通常只差 1 档，实际只会走 1 步；点按滑条要跳很远时才用到上限，
     * 由 [STEP_BUDGET_MS] 兜住总耗时（不再按「级差」当步数 —— 那是旧实现的根因）。
     */
    private const val MAX_STEPS_PER_CALL = 16

    /** 单次调用（一条 MOVE）的时间预算：一帧内必须返回。 */
    private const val STEP_BUDGET_MS = 6L

    /** 绝对设置被 ROM 拒绝后的退避时间：这段时间内直接走步进，不白打 binder。 */
    private const val ABSOLUTE_REJECT_BACKOFF_MS = 5_000L

    /** 实测到的每档级数：stream -> step（首次步进时学到）。 */
    private val stepCache = HashMap<Int, Int>()

    /** 绝对设置最近一次被拒的时刻（stream -> 可重试时间点）。 */
    private val absoluteRejectedUntilMs = HashMap<Int, Long>()
}

/**
 * 音量步进的滚动统计。
 *
 * 逐事件打日志会把日志冲爆（旧实现在 8.4 秒里写了 2000+ 行），所以这里只累加，由
 * [com.slideindex.app.util.ContinuousAdjustController] 每 500ms / 每次手势结束时吐**一行**汇总。
 */
object VolumeStepStats {
    private var calls = 0
    private var absoluteTried = 0
    private var absoluteOk = 0
    private var adjusts = 0
    private var totalCostMs = 0L
    private var maxCostMs = 0L
    private var lastWant = -1
    private var lastActual = -1

    @Synchronized
    fun record(
        calls: Int,
        absoluteTried: Int,
        absoluteOk: Int,
        adjusts: Int,
        want: Int,
        actual: Int,
        costMs: Long,
    ) {
        this.calls += calls
        this.absoluteTried += absoluteTried
        this.absoluteOk += absoluteOk
        this.adjusts += adjusts
        this.totalCostMs += costMs
        if (costMs > maxCostMs) maxCostMs = costMs
        lastWant = want
        lastActual = actual
    }

    /** 取走并清空：没有记录过就返回 null，避免刷空行。 */
    @Synchronized
    fun take(): String? {
        if (calls == 0) return null
        val avg = if (calls == 0) 0.0 else totalCostMs.toDouble() / calls
        val line = "calls=$calls absoluteTry=$absoluteTried absoluteOk=$absoluteOk " +
            "adjusts=$adjusts avg=${"%.1f".format(avg)}ms max=${maxCostMs}ms " +
            "lastWant=$lastWant lastActual=$lastActual"
        calls = 0
        absoluteTried = 0
        absoluteOk = 0
        adjusts = 0
        totalCostMs = 0L
        maxCostMs = 0L
        return line
    }
}

