package com.slideindex.app.macro.record

import android.content.Context
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.WindowManager
import com.slideindex.app.macro.Macro
import com.slideindex.app.macro.MacroStep
import com.slideindex.app.macro.MacroStore
import kotlin.math.hypot
import kotlin.math.max

/**
 * 录制阈值。
 *
 * 这些数字**是行为契约的一部分**（见 `docs/macro-port-plan.md` §6），改动前先确认执行端也同步，
 * 否则"录的时候判成点击、放的时候按滑动走"这种事会很难查。
 */
object MacroRecordThresholds {

    /** 普通采样抽稀：与「上一次采样点」的距离 ≥ 该值才记一点。 */
    const val SAMPLE_MIN_DISTANCE_PX = 24f

    /** 按下 / 抬起是**强制采样**，但仍要求与上一点距离 ≥ 该值。 */
    const val FORCED_MIN_DISTANCE_PX = 1f

    /** 位移 ≤ 该值判为点击，否则判为滑动。 */
    const val TAP_MAX_TRAVEL_PX = 40f

    /** 与「上次输入结束」的间隔**严格大于**该值才补一条等待。 */
    const val WAIT_MIN_GAP_MS = 80L

    /** 间隔上限（超过就按上限记，别把用户发呆的一分钟录进去）。 */
    const val WAIT_MAX_GAP_MS = 10_000L

    /** 按压时长的收窄区间。 */
    const val PRESS_MIN_MS = 1L
    const val PRESS_MAX_MS = 5_000L

    /** 点击 / 滑动落库的时长区间。 */
    const val TAP_MIN_MS = 30L
    const val TAP_MAX_MS = 2_000L
    const val SWIPE_MIN_MS = 50L
    const val SWIPE_MAX_MS = 5_000L

    /** 保存时丢弃的等待上限：≤ 该值的等待没有意义。 */
    const val DROPPED_WAIT_MAX_MS = 80L
}

/** 屏幕真实分辨率，比例坐标的换算基准（执行端用同一套，所以能对得上）。 */
data class ScreenSize(val widthPx: Int, val heightPx: Int) {

    companion object {

        /**
         * 用 `display.getRealMetrics()` 取真实分辨率。
         *
         * 之所以不用 `resources.displayMetrics`：那套在多窗口/分屏下可能是"应用可用区域"，
         * 而执行端注入手势用的是**真实屏幕**坐标，两边基准必须一致。
         */
        fun real(context: Context): ScreenSize {
            val metrics = DisplayMetrics()
            val manager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            val display = manager?.defaultDisplay
            if (display != null) {
                @Suppress("DEPRECATION")
                display.getRealMetrics(metrics)
            } else {
                metrics.setTo(context.resources.displayMetrics)
            }
            return ScreenSize(metrics.widthPixels, metrics.heightPixels)
        }
    }
}

/** 保存前清洗：丢掉所有 ≤ [MacroRecordThresholds.DROPPED_WAIT_MAX_MS] 的等待。 */
internal fun stepsForSave(steps: List<MacroStep>): List<MacroStep> =
    stepsForSave(steps, dropTap = null)

/**
 * 保存前清洗（带"丢弃指定点击"）。
 *
 * [dropTap] 用来剔除落在**录制控制条**上的点击：捕获层一旦被重新装回、压到控制条上面，
 * 用户点"暂停/停止/保存"就都会被录成一步（真正的修法见 `MacroRecordOverlay.bringToFront`，
 * 这里只是兜底）。丢点击时把它**前面那条等待**一起丢：那是"从上一个动作挪到按钮"的时间，
 * 留着会让宏尾巴白等几秒。
 */
internal fun stepsForSave(
    steps: List<MacroStep>,
    dropTap: ((MacroStep.Tap) -> Boolean)?,
): List<MacroStep> {
    val kept = if (dropTap == null) {
        steps
    } else {
        val result = ArrayList<MacroStep>(steps.size)
        for (step in steps) {
            if (step is MacroStep.Tap && dropTap(step)) {
                if (result.isNotEmpty() && result.last() is MacroStep.Wait) {
                    result.removeAt(result.lastIndex)
                }
                continue
            }
            result += step
        }
        result
    }
    return kept.filterNot { step ->
        step is MacroStep.Wait && step.durationMs <= MacroRecordThresholds.DROPPED_WAIT_MAX_MS
    }
}

/**
 * 录制器：把用户的真实触摸转成 [MacroStep]。
 *
 * 这一层是**纯逻辑**，不依赖任何 View —— 无障碍捕获层只要按顺序投喂
 * [onDown] / [onMove] / [onUp] / [onCancel] 即可（坐标是屏幕像素，时间是 uptime 毫秒，
 * 与 `MotionEvent.getRawX/getRawY`、`MotionEvent.getEventTime` 一致）。
 *
 * 约定：
 * - 所有回调都在**同一个线程**上调用（实际是主线程），内部状态不做加锁。
 * - 手势记完后不在这里回放；回放由执行端（`MacroEngine`）负责，录制端只管"记准"。
 *
 * @param macroId 预生成的宏 id（编辑页已经先建了 id 时传入）；为 null 时保存时现取 [MacroStore.newId]。
 * @param clock 当前时间（与事件时间同一基准），用于"录制开始"与"暂停恢复"。
 * @param screenSize 分辨率来源，默认读真实分辨率；单测注入固定值。
 */
class MacroRecorder(
    context: Context,
    private val targetPackage: String,
    private val macroName: String,
    private val macroId: String? = null,
    private val clock: () -> Long = { SystemClock.uptimeMillis() },
    private val screenSize: () -> ScreenSize = { ScreenSize.real(context) },
) {

    private val appContext = context.applicationContext

    private val recorded = mutableListOf<MacroStep>()

    /**
     * 「上次输入结束时间」。
     *
     * 初值取录制开始的时刻：录制开始本身就等价于一次"恢复"，否则第一次点击会带出一条
     * 长达 [MacroRecordThresholds.WAIT_MAX_GAP_MS] 的等待（时间基准从 0 开始）。
     */
    private var lastInputAtMs = clock()

    private var paused = false
    private var pressing = false
    private var downX = 0f
    private var downY = 0f
    private var downTimeMs = 0L

    /** 本次手势的采样表（原始像素，x/y 平行存放，避免每点一个对象）。 */
    private val sampleXs = ArrayList<Float>()
    private val sampleYs = ArrayList<Float>()

    /** 步骤数变化回调：(动作数, 等待数)。用于浮动条刷新「动作 N · 等待 M」。 */
    var onStepsChanged: ((actions: Int, waits: Int) -> Unit)? = null

    val isPaused: Boolean get() = paused

    /** 会产生手势的步数（不含等待）。 */
    val actionCount: Int get() = recorded.count { it !is MacroStep.Wait }

    /** 等待步数。 */
    val waitCount: Int get() = recorded.count { it is MacroStep.Wait }

    /** 已录步骤的快照。 */
    fun steps(): List<MacroStep> = recorded.toList()

    /**
     * 追加一步系统按键（反馈 #4）：返回 / 主屏 / 最近任务。
     *
     * 工具条上的按钮不是触摸事件，所以这里手动补一段"距上次输入的等待"，
     * 回放节奏才和用户实际操作的间隔一致（与 onUp 里的补等待同一口径）。
     */
    fun addSystemKey(key: MacroStep.SystemKey) {
        if (paused) return
        val now = clock()
        val gapMs = (now - lastInputAtMs).coerceIn(0L, MacroRecordThresholds.WAIT_MAX_GAP_MS)
        if (gapMs > MacroRecordThresholds.WAIT_MIN_GAP_MS) {
            recorded.add(MacroStep.Wait(gapMs))
        }
        recorded.add(MacroStep.Key(key))
        lastInputAtMs = now
        android.util.Log.i("MacroRecorder", "system key step added: $key, steps=${recorded.size}")
        notifyStepsChanged()
    }

    /** 按下：记录按下点与时间，并清空本次手势的采样表（按下点本身写进采样表）。 */
    fun onDown(x: Float, y: Float, eventTimeMs: Long) {
        if (paused) return
        pressing = true
        downX = x
        downY = y
        downTimeMs = eventTimeMs
        sampleXs.clear()
        sampleYs.clear()
        sampleXs.add(x)
        sampleYs.add(y)
    }

    /** 移动：按抽稀规则采样（不设点数上限）。 */
    fun onMove(x: Float, y: Float, eventTimeMs: Long) {
        if (paused || !pressing) return
        sample(x, y, forced = false)
    }

    /**
     * 抬起：结算本次手势。
     *
     * 顺序是「先补等待、再记点击/滑动」，所以等待永远排在本次动作**之前**。
     */
    fun onUp(x: Float, y: Float, eventTimeMs: Long) {
        if (!pressing) return
        pressing = false
        if (paused) {
            clearSamples()
            return
        }

        val pressDurationMs = (eventTimeMs - downTimeMs).coerceIn(
            MacroRecordThresholds.PRESS_MIN_MS,
            MacroRecordThresholds.PRESS_MAX_MS,
        )
        val gapMs = (downTimeMs - lastInputAtMs).coerceIn(0L, MacroRecordThresholds.WAIT_MAX_GAP_MS)
        // 抬起是强制采样
        sample(x, y, forced = true)

        if (gapMs > MacroRecordThresholds.WAIT_MIN_GAP_MS) {
            recorded.add(MacroStep.Wait(gapMs))
        }

        val screen = screenSize()
        val widthPx = max(1, screen.widthPx).toFloat()
        val heightPx = max(1, screen.heightPx).toFloat()
        fun fractionX(value: Float): Float = (value / widthPx).coerceIn(0f, 1f)
        fun fractionY(value: Float): Float = (value / heightPx).coerceIn(0f, 1f)

        val travelPx = hypot(x - downX, y - downY)
        if (travelPx <= MacroRecordThresholds.TAP_MAX_TRAVEL_PX) {
            recorded.add(
                MacroStep.Tap(
                    x = fractionX(downX),
                    y = fractionY(downY),
                    durationMs = pressDurationMs.coerceIn(
                        MacroRecordThresholds.TAP_MIN_MS,
                        MacroRecordThresholds.TAP_MAX_MS,
                    ),
                ),
            )
        } else {
            val points = sampleXs.indices.map { index ->
                MacroStep.Swipe.PathPoint(
                    x = fractionX(sampleXs[index]),
                    y = fractionY(sampleYs[index]),
                )
            }
            recorded.add(
                MacroStep.Swipe(
                    startX = fractionX(downX),
                    startY = fractionY(downY),
                    endX = fractionX(x),
                    endY = fractionY(y),
                    durationMs = pressDurationMs.coerceIn(
                        MacroRecordThresholds.SWIPE_MIN_MS,
                        MacroRecordThresholds.SWIPE_MAX_MS,
                    ),
                    points = points,
                ),
            )
        }

        lastInputAtMs = eventTimeMs
        notifyStepsChanged()
    }

    /** 取消：丢弃当前手势的采样与状态，不产生任何步骤。 */
    fun onCancel() {
        pressing = false
        clearSamples()
    }

    /** 暂停：丢弃当前手势；暂停期间 [onDown] / [onMove] / [onUp] 一律不记录。 */
    fun pause() {
        paused = true
        pressing = false
        clearSamples()
    }

    /**
     * 从暂停恢复。
     *
     * 必须把「上次输入结束时间」重置为当前时间：暂停期间用户可能去干了别的，
     * 否则恢复后第一下的间隔会变成一条巨大的等待。
     */
    fun resume(nowMs: Long = clock()) {
        paused = false
        lastInputAtMs = nowMs
    }

    /**
     * 保存：丢弃所有 ≤ [MacroRecordThresholds.DROPPED_WAIT_MAX_MS] 的等待。
     *
     * @param dropTap 可选：返回 true 的点击会被丢弃（用于剔除落在录制控制条上的点击）。
     * @return 落盘后的宏；过滤后一步不剩时返回 null（调用方提示"还没录到步骤"）。
     */
    fun finish(dropTap: ((MacroStep.Tap) -> Boolean)? = null): Macro? {
        val steps = stepsForSave(recorded, dropTap)
        if (steps.isEmpty()) return null
        val now = System.currentTimeMillis()
        val id = macroId ?: MacroStore.newId()
        // 编辑已有宏时保留原始创建时间（否则"录一段再保存"会把它变成本次保存的时间）。
        val createdAtMs = MacroStore.byId(appContext, id)?.createdAtMs ?: now
        val macro = Macro(
            id = id,
            name = macroName,
            targetPackage = targetPackage,
            createdAtMs = createdAtMs,
            updatedAtMs = now,
            schemaVersion = Macro.CURRENT_SCHEMA_VERSION,
            steps = steps,
        )
        MacroStore.upsert(appContext, macro)
        return macro
    }

    /** 抽稀采样：普通点要 ≥ 24px，强制点（按下/抬起）只要 ≥ 1px。 */
    private fun sample(x: Float, y: Float, forced: Boolean) {
        if (sampleXs.isNotEmpty()) {
            val minDistance = if (forced) {
                MacroRecordThresholds.FORCED_MIN_DISTANCE_PX
            } else {
                MacroRecordThresholds.SAMPLE_MIN_DISTANCE_PX
            }
            val distance = hypot(x - sampleXs.last(), y - sampleYs.last())
            if (distance < minDistance) return
        }
        sampleXs.add(x)
        sampleYs.add(y)
    }

    private fun clearSamples() {
        sampleXs.clear()
        sampleYs.clear()
    }

    private fun notifyStepsChanged() {
        onStepsChanged?.invoke(actionCount, waitCount)
    }
}
