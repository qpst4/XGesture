package com.slideindex.app.macro.record

import android.app.Application
import com.slideindex.app.macro.Macro
import com.slideindex.app.macro.MacroStep
import com.slideindex.app.macro.MacroStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 录制器的行为契约（`docs/macro-port-plan.md` §6）。
 *
 * 这里只喂 `onDown/onMove/onUp`，不碰任何 View —— 录制逻辑刻意做成纯逻辑，
 * 就是为了让这些阈值边界（24px 抽稀 / 40px 点击判定 / 80ms 等待）能这样直接钉住。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class)
class MacroRecorderTest {

    private val context get() = RuntimeEnvironment.getApplication()

    /** 事件时间基准；初值等价于"录制开始"的时刻。 */
    private var clock = 0L

    private fun recorder(
        screen: ScreenSize = ScreenSize(1000, 1000),
        targetPackage: String = TARGET_PACKAGE,
        name: String = MACRO_NAME,
        macroId: String? = null,
    ) = MacroRecorder(
        context = context,
        targetPackage = targetPackage,
        macroName = name,
        macroId = macroId,
        clock = { clock },
        screenSize = { screen },
    )

    // ── 采样抽稀 ──────────────────────────────────────────────────────────

    @Test
    fun `a move closer than 24px is not sampled`() {
        clock = 1_000L
        val recorder = recorder()
        recorder.onDown(100f, 100f, 1_000L)
        recorder.onMove(110f, 100f, 1_010L) // 差 10px：不记
        recorder.onUp(400f, 100f, 1_100L)

        val swipe = recorder.steps().single() as MacroStep.Swipe
        assertEquals(2, swipe.points.size)
        assertEquals(0.1f, swipe.points[0].x, 1e-4f)
        assertEquals(0.4f, swipe.points[1].x, 1e-4f)
    }

    @Test
    fun `a move farther than 24px from the last sample is kept`() {
        clock = 1_000L
        val recorder = recorder()
        recorder.onDown(100f, 100f, 1_000L)
        recorder.onMove(110f, 100f, 1_010L) // 差 10px：不记
        recorder.onMove(134f, 100f, 1_020L) // 距"上一次采样点"(100)34px：记
        recorder.onUp(400f, 100f, 1_100L)

        val swipe = recorder.steps().single() as MacroStep.Swipe
        assertEquals(3, swipe.points.size)
        assertEquals(0.134f, swipe.points[1].x, 1e-4f)
    }

    @Test
    fun `the 24px boundary is inclusive`() {
        clock = 1_000L
        val recorder = recorder()
        recorder.onDown(100f, 100f, 1_000L)
        recorder.onMove(124f, 100f, 1_010L) // 正好 24px：记
        recorder.onUp(400f, 100f, 1_100L)

        val swipe = recorder.steps().single() as MacroStep.Swipe
        assertEquals(3, swipe.points.size)
    }

    @Test
    fun `down clears the sample table of the previous gesture`() {
        clock = 1_000L
        val recorder = recorder()
        recorder.onDown(100f, 100f, 1_000L)
        recorder.onUp(300f, 100f, 1_100L)

        // 第二次按下离上次抬起点只有 5px；采样表若没清空，抬起点就会被 24px 规则吃掉
        recorder.onDown(305f, 100f, 1_200L)
        recorder.onUp(405f, 100f, 1_300L)

        val second = recorder.steps().last() as MacroStep.Swipe
        assertEquals(2, second.points.size)
        assertEquals(0.305f, second.points[0].x, 1e-4f)
        assertEquals(0.405f, second.points[1].x, 1e-4f)
    }

    @Test
    fun `up is sampled even when closer than 24px`() {
        clock = 1_000L
        val recorder = recorder()
        recorder.onDown(100f, 100f, 1_000L)
        recorder.onMove(300f, 100f, 1_050L)
        recorder.onUp(310f, 100f, 1_100L) // 差 10px，但抬起强制采样

        val swipe = recorder.steps().single() as MacroStep.Swipe
        assertEquals(3, swipe.points.size)
        assertEquals(0.31f, swipe.points.last().x, 1e-4f)
    }

    @Test
    fun `forced sampling still requires at least one pixel`() {
        clock = 1_000L
        val recorder = recorder()
        recorder.onDown(100f, 100f, 1_000L)
        recorder.onMove(300f, 100f, 1_050L)
        recorder.onUp(300.5f, 100f, 1_100L) // 距上次采样点 0.5px：不记

        val swipe = recorder.steps().single() as MacroStep.Swipe
        assertEquals(2, swipe.points.size)
        // 终点仍然体现在 endX 上（轨迹点里没有它）
        assertEquals(0.3005f, swipe.endX, 1e-4f)
    }

    @Test
    fun `sample points are not capped`() {
        clock = 1_000L
        val recorder = recorder(screen = ScreenSize(4000, 4000))
        recorder.onDown(100f, 100f, 1_000L)
        repeat(60) { index ->
            recorder.onMove(100f + 30f * (index + 1), 100f, 1_010L + index)
        }
        recorder.onUp(100f + 30f * 61f, 100f, 2_000L)

        val swipe = recorder.steps().single() as MacroStep.Swipe
        assertEquals(62, swipe.points.size)
    }

    // ── 点击 / 滑动判定 ────────────────────────────────────────────────────

    @Test
    fun `40px travel is a tap and 41px is a swipe`() {
        clock = 1_000L
        val tapRecorder = recorder()
        tapRecorder.onDown(100f, 100f, 1_000L)
        tapRecorder.onUp(140f, 100f, 1_100L)
        val tapStep = tapRecorder.steps().single()
        assertTrue(tapStep is MacroStep.Tap)
        val tap = tapStep as MacroStep.Tap
        assertEquals(0.1f, tap.x, 1e-4f)
        assertEquals(0.1f, tap.y, 1e-4f)

        clock = 1_000L
        val swipeRecorder = recorder()
        swipeRecorder.onDown(100f, 100f, 1_000L)
        swipeRecorder.onUp(141f, 100f, 1_100L)
        assertTrue(swipeRecorder.steps().single() is MacroStep.Swipe)
    }

    @Test
    fun `tap uses the down point and clamps its duration`() {
        clock = 1_000L
        val short = recorder()
        short.onDown(100f, 200f, 1_000L)
        short.onUp(100f, 200f, 1_005L) // 按压 5ms → 收进 30ms
        assertEquals(30L, (short.steps().single() as MacroStep.Tap).durationMs)

        clock = 1_000L
        val long = recorder()
        long.onDown(100f, 200f, 1_000L)
        long.onUp(100f, 200f, 5_000L) // 按压 4000ms → 收进 2000ms
        assertEquals(2_000L, (long.steps().single() as MacroStep.Tap).durationMs)
    }

    @Test
    fun `swipe clamps its duration`() {
        clock = 1_000L
        val short = recorder()
        short.onDown(100f, 100f, 1_000L)
        short.onUp(300f, 100f, 900L) // 负的按压时长 → 收进 50ms
        assertEquals(50L, (short.steps().single() as MacroStep.Swipe).durationMs)

        clock = 1_000L
        val long = recorder()
        long.onDown(100f, 100f, 1_000L)
        long.onUp(300f, 100f, 9_000L) // 按压 8000ms → 收进 5000ms
        assertEquals(5_000L, (long.steps().single() as MacroStep.Swipe).durationMs)
    }

    // ── 等待 ──────────────────────────────────────────────────────────────

    @Test
    fun `a gap over 80ms inserts a wait before the step`() {
        clock = 1_000L
        val recorder = recorder()
        recorder.onDown(100f, 100f, 1_000L)
        recorder.onUp(100f, 100f, 1_050L)
        recorder.onDown(100f, 100f, 1_131L) // 间隔 81ms
        recorder.onUp(100f, 100f, 1_181L)

        val steps = recorder.steps()
        assertEquals(3, steps.size)
        assertEquals(MacroStep.Wait(81L), steps[1])
        assertTrue(steps[2] is MacroStep.Tap)
    }

    @Test
    fun `a gap of exactly 80ms inserts no wait`() {
        clock = 1_000L
        val recorder = recorder()
        recorder.onDown(100f, 100f, 1_000L)
        recorder.onUp(100f, 100f, 1_050L)
        recorder.onDown(100f, 100f, 1_130L) // 间隔正好 80ms
        recorder.onUp(100f, 100f, 1_180L)

        val steps = recorder.steps()
        assertEquals(2, steps.size)
        assertTrue(steps.all { it is MacroStep.Tap })
    }

    @Test
    fun `a huge gap is clamped to ten seconds`() {
        clock = 0L
        val recorder = recorder()
        recorder.onDown(100f, 100f, 60_000L)
        recorder.onUp(100f, 100f, 60_050L)

        assertEquals(MacroStep.Wait(10_000L), recorder.steps().first())
    }

    @Test
    fun `the first gesture does not carry a wait`() {
        clock = 5_000L
        val recorder = recorder()
        recorder.onDown(100f, 100f, 5_000L)
        recorder.onUp(100f, 100f, 5_050L)

        assertEquals(1, recorder.steps().size)
        assertTrue(recorder.steps().single() is MacroStep.Tap)
    }

    // ── 取消 / 暂停 ────────────────────────────────────────────────────────

    @Test
    fun `cancel drops the current gesture`() {
        clock = 1_000L
        val recorder = recorder()
        recorder.onDown(100f, 100f, 1_000L)
        recorder.onMove(300f, 100f, 1_050L)
        recorder.onCancel()
        recorder.onUp(300f, 100f, 1_100L) // 已被取消：抬起不再结算
        assertTrue(recorder.steps().isEmpty())

        recorder.onDown(100f, 100f, 1_200L)
        recorder.onUp(100f, 100f, 1_250L)
        val steps = recorder.steps()
        assertEquals(2, steps.size)
        assertEquals(MacroStep.Wait(200L), steps[0])
        assertTrue(steps[1] is MacroStep.Tap)
    }

    @Test
    fun `events during pause are dropped and resume resets the last input time`() {
        clock = 1_000L
        val recorder = recorder()
        recorder.onDown(100f, 100f, 1_000L)
        recorder.onUp(100f, 100f, 1_050L)
        assertEquals(1, recorder.steps().size)

        recorder.pause()
        assertTrue(recorder.isPaused)
        recorder.onDown(200f, 200f, 2_000L)
        recorder.onMove(300f, 300f, 2_010L)
        recorder.onUp(300f, 300f, 2_100L)
        assertEquals(1, recorder.steps().size)

        recorder.resume(5_000L)
        assertFalse(recorder.isPaused)
        recorder.onDown(100f, 100f, 5_100L)
        recorder.onUp(100f, 100f, 5_150L)

        val steps = recorder.steps()
        assertEquals(3, steps.size)
        // 恢复后的间隔是 100ms，而不是暂停的那 4 秒
        assertEquals(MacroStep.Wait(100L), steps[1])
        assertTrue(steps[2] is MacroStep.Tap)
    }

    @Test
    fun `pause discards the gesture in flight`() {
        clock = 1_000L
        val recorder = recorder()
        recorder.onDown(100f, 100f, 1_000L)
        recorder.onMove(300f, 100f, 1_050L)
        recorder.pause()
        recorder.onUp(300f, 100f, 1_100L)
        assertTrue(recorder.steps().isEmpty())
    }

    // ── 比例换算 ──────────────────────────────────────────────────────────

    @Test
    fun `coordinates become fractions and are clamped`() {
        clock = 1_000L
        val recorder = recorder(screen = ScreenSize(1000, 2000))
        recorder.onDown(-50f, 3_000f, 1_000L) // 越界：x<0 / y>高
        recorder.onUp(250f, 3_000f, 1_100L) // 位移 300px → 滑动

        val swipe = recorder.steps().single() as MacroStep.Swipe
        assertEquals(0f, swipe.startX, 1e-6f)
        assertEquals(1f, swipe.startY, 1e-6f)
        assertEquals(0.25f, swipe.endX, 1e-6f)
        assertEquals(1f, swipe.endY, 1e-6f)
    }

    @Test
    fun `a degenerate screen size still divides by one`() {
        clock = 1_000L
        val recorder = recorder(screen = ScreenSize(0, 0))
        recorder.onDown(5f, 5f, 1_000L)
        recorder.onUp(5f, 5f, 1_100L)

        val tap = recorder.steps().single() as MacroStep.Tap
        assertEquals(1f, tap.x, 1e-6f)
        assertEquals(1f, tap.y, 1e-6f)
    }

    // ── 保存 ──────────────────────────────────────────────────────────────

    @Test
    fun `waits no longer than 80ms are dropped before saving`() {
        val kept = stepsForSave(
            listOf(
                MacroStep.Wait(0L),
                MacroStep.Wait(80L),
                MacroStep.Wait(81L),
                MacroStep.Tap(x = 0.5f, y = 0.5f),
            ),
        )
        assertEquals(listOf(MacroStep.Wait(81L), MacroStep.Tap(x = 0.5f, y = 0.5f)), kept)
    }

    @Test
    fun `finish returns null when nothing was recorded`() {
        clock = 1_000L
        assertNull(recorder().finish())
    }

    @Test
    fun `finish writes the macro to the store`() {
        clock = 1_000L
        val recorder = recorder(macroId = FIXED_ID)
        recorder.onDown(100f, 200f, 1_000L)
        recorder.onUp(100f, 200f, 1_100L)

        val macro = recorder.finish()
        assertNotNull(macro)
        assertEquals(FIXED_ID, macro!!.id)
        assertEquals(MACRO_NAME, macro.name)
        assertEquals(TARGET_PACKAGE, macro.targetPackage)
        assertEquals(Macro.CURRENT_SCHEMA_VERSION, macro.schemaVersion)
        assertEquals(2, macro.schemaVersion)
        assertEquals(macro.createdAtMs, macro.updatedAtMs)
        assertTrue(macro.createdAtMs > 0L)
        assertEquals(1, macro.actionCount)

        val stored = MacroStore.byId(context, FIXED_ID)
        assertNotNull(stored)
        val storedTap = stored!!.steps.filterIsInstance<MacroStep.Tap>().single()
        assertEquals(0.1f, storedTap.x, 1e-6f)
        assertEquals(0.2f, storedTap.y, 1e-6f)
    }

    @Test
    fun `finish mints a new id when none was given`() {
        clock = 1_000L
        val recorder = recorder()
        recorder.onDown(100f, 100f, 1_000L)
        recorder.onUp(100f, 100f, 1_100L)

        val first = recorder.finish()
        assertNotNull(first)
        assertFalse(first!!.id.isBlank())
        assertNotNull(MacroStore.byId(context, first.id))
    }

    // ── 控制条点击剔除（#6 的兜底）────────────────────────────────────────

    @Test
    fun `dropTap removes every tap that lands on the bar and its preceding wait`() {
        val kept = stepsForSave(
            steps = listOf(
                MacroStep.Tap(x = 0.2f, y = 0.3f),
                MacroStep.Wait(1_500L),
                MacroStep.Tap(x = 0.9f, y = 0.1f), // 控制条上的"保存"
            ),
            dropTap = { tap -> tap.x > 0.8f },
        )
        assertEquals(listOf(MacroStep.Tap(x = 0.2f, y = 0.3f)), kept)
    }

    @Test
    fun `dropTap removes bar taps in the middle too, not just the last one`() {
        val kept = stepsForSave(
            steps = listOf(
                MacroStep.Tap(x = 0.2f, y = 0.3f),
                MacroStep.Wait(900L),
                MacroStep.Tap(x = 0.9f, y = 0.1f), // 暂停
                MacroStep.Wait(2_000L),
                MacroStep.Tap(x = 0.2f, y = 0.3f),
            ),
            dropTap = { tap -> tap.x > 0.8f },
        )
        assertEquals(
            listOf(
                MacroStep.Tap(x = 0.2f, y = 0.3f),
                MacroStep.Wait(2_000L),
                MacroStep.Tap(x = 0.2f, y = 0.3f),
            ),
            kept,
        )
    }

    private companion object {
        const val TARGET_PACKAGE = "com.example.target"
        const val MACRO_NAME = "测试宏"
        const val FIXED_ID = "fixed-id"
    }
}
