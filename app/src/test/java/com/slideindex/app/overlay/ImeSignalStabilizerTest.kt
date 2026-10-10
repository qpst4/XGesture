package com.slideindex.app.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ImeSignalStabilizer] 的决策矩阵。
 *
 * 关键回归有两条：
 * 1. **抖动的采样（我们自己增删浮窗引起的那种）永远不提交**——这是"胶囊每秒闪 3 次"的直接成因；
 * 2. **没有后续事件时也必须能提交**——采样只在窗口事件里发生，而键盘收起后系统不再产生窗口事件；
 *    只靠"下一次采样"确认候选会让最后一跳永久卡住（真机：胶囊一直挂着，点它一下才消失）。
 */
class ImeSignalStabilizerTest {

    @Test
    fun flutter_neverCommits() {
        val stabilizer = ImeSignalStabilizer()
        // 每 200ms 翻转一次，持续 4 秒：模拟自激振荡的采样序列。
        var now = 0L
        var sample = true
        repeat(20) {
            assertFalse(stabilizer.onSample(sampleVisible = sample, nowMs = now))
            now += 200
            sample = !sample
        }
        assertFalse(stabilizer.committedVisible)
    }

    @Test
    fun stableSample_commitsOnlyAfterConfirmWindow() {
        val stabilizer = ImeSignalStabilizer()
        assertFalse(stabilizer.onSample(sampleVisible = true, nowMs = 0))
        assertFalse(stabilizer.onSample(sampleVisible = true, nowMs = 199))
        assertTrue(stabilizer.onSample(sampleVisible = true, nowMs = 200))
        assertTrue(stabilizer.committedVisible)
    }

    @Test
    fun pendingMustBeConsecutive() {
        val stabilizer = ImeSignalStabilizer()
        assertFalse(stabilizer.onSample(sampleVisible = true, nowMs = 0))
        // 中间冒出一个与已提交状态一致的采样 → 候选被清空，计时重来。
        assertFalse(stabilizer.onSample(sampleVisible = false, nowMs = 100))
        assertFalse(stabilizer.onSample(sampleVisible = true, nowMs = 200))
        assertFalse(stabilizer.onSample(sampleVisible = true, nowMs = 399))
        assertTrue(stabilizer.onSample(sampleVisible = true, nowMs = 400))
        assertTrue(stabilizer.committedVisible)
    }

    /**
     * 提交后进入静默期：自己那一下窗口增删引来的"看不见了"要等静默期结束才可能提交
     * （候选在静默期内就记账，但提交被推迟到静默期结束）。
     */
    @Test
    fun echoRightAfterCommit_isIgnored() {
        val stabilizer = ImeSignalStabilizer()
        assertFalse(stabilizer.onSample(sampleVisible = true, nowMs = 0))
        assertTrue(stabilizer.onSample(sampleVisible = true, nowMs = 200))
        assertFalse(stabilizer.onSample(sampleVisible = false, nowMs = 300))
        assertFalse(stabilizer.onSample(sampleVisible = false, nowMs = 500))
        assertFalse(stabilizer.onSample(sampleVisible = false, nowMs = 599))
        assertTrue(stabilizer.committedVisible)
        // 静默期一过、候选也早已满确认窗口 → 立刻提交隐藏。
        assertTrue(stabilizer.onSample(sampleVisible = false, nowMs = 600))
        assertFalse(stabilizer.committedVisible)
    }

    /**
     * 回归（2026-10-10 真机，短信输入框）：短按返回收起键盘后，系统**不再产生任何窗口事件**。
     * 当时实现只靠"下一次采样"确认候选，于是提交永远等不到 —— `dumpsys input_method` 已经是
     * `mInputShown=false`、无障碍窗口表也空了，我们却一条 HIDE 都没发，胶囊一直挂着；
     * 点它一下（点击带来窗口事件）才立刻消失。
     */
    @Test
    fun tick_commitsWithoutFurtherEvents() {
        val stabilizer = ImeSignalStabilizer()
        assertFalse(stabilizer.onSample(sampleVisible = true, nowMs = 0))
        assertTrue(stabilizer.onSample(sampleVisible = true, nowMs = 200))

        // 键盘收起只带来这一次采样，之后再无事件。
        assertFalse(stabilizer.onSample(sampleVisible = false, nowMs = 800))
        assertEquals(1000L, stabilizer.nextWakeAtMs()!!)
        assertFalse(stabilizer.tick(nowMs = 999))
        assertTrue(stabilizer.committedVisible) // 还没到点：保持原状
        assertTrue(stabilizer.tick(nowMs = 1000))
        assertFalse(stabilizer.committedVisible)
    }

    /** 没有待提交的候选时不能给出"到点时刻"，否则调用方会排一个空转的定时任务。 */
    @Test
    fun nextWakeAtMs_isNullWhenNothingPending() {
        val stabilizer = ImeSignalStabilizer()
        assertNull(stabilizer.nextWakeAtMs())
        // 与已提交状态一致的采样不产生候选。
        assertFalse(stabilizer.onSample(sampleVisible = false, nowMs = 0))
        assertNull(stabilizer.nextWakeAtMs())
        assertFalse(stabilizer.onSample(sampleVisible = true, nowMs = 10))
        assertEquals(210L, stabilizer.nextWakeAtMs()!!)
        assertTrue(stabilizer.onSample(sampleVisible = true, nowMs = 210))
        assertNull(stabilizer.nextWakeAtMs())
    }

    @Test
    fun reset_realignsStateWithoutCommitting() {
        val stabilizer = ImeSignalStabilizer()
        assertFalse(stabilizer.onSample(sampleVisible = true, nowMs = 0))
        assertTrue(stabilizer.onSample(sampleVisible = true, nowMs = 200))
        stabilizer.reset(visible = false, nowMs = 300)
        assertFalse(stabilizer.committedVisible)
        // reset 不进入静默期：下一次真实可见应当照常走"候选 + 确认"。
        assertFalse(stabilizer.onSample(sampleVisible = true, nowMs = 300))
        assertTrue(stabilizer.onSample(sampleVisible = true, nowMs = 500))
        assertTrue(stabilizer.committedVisible)
    }
}
