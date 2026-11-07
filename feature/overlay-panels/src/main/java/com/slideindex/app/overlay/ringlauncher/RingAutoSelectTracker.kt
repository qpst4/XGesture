package com.slideindex.app.overlay.ringlauncher

/**
 * 「长按自动选中首槽」的"手指动过没有"判定。
 *
 * 基准点是**圆环弹出那一刻手指所在的位置**，而不是圆环锚点：
 *  - 悬浮球触摸区是 48dp 见方，左右贴边时圆环锚点却固定在距屏幕边缘 20dp 处，
 *    按下点到锚点天然带一段偏置（球心 24dp vs 20dp，按在球边更大）。以锚点为基准时，
 *    这段偏置会先吃掉容差，用户必须把手指按在球心附近才保得住自动选中
 *    （真机反馈：球侧只有正中心生效、稍偏就"看着选中但松手不启动"；线条侧因为基准
 *    本来就是按下点，所以一直正常）。
 *  - 线条侧 / 边缘手势的锚点本来就等于按下点，因此本次修复对它们行为不变。
 *
 * 只记录"从基准点走开了多远"：超过 [slopPx] 就永久作废（语义仍是原先的
 * 「手指动过即作废」），之后一切以手指指向为准，空白 / 空隙一律不选（松手即取消）。
 */
class RingAutoSelectTracker(
    private val originX: Float,
    private val originY: Float,
    private val slopPx: Float,
) {
    var moved: Boolean = false
        private set

    fun update(x: Float, y: Float) {
        if (moved) return
        val dx = x - originX
        val dy = y - originY
        if (dx * dx + dy * dy > slopPx * slopPx) moved = true
    }
}
