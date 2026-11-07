package com.slideindex.app.overlay.dragdrop

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 环形流转栏的纯几何计算：手指落在哪、命中第几个目标。
 *
 * 单独抽出来是为了能直接单测——这里一旦算错，表现就是“拖到微信却发给了 QQ”，
 * 而且是随机、偶发的那种，靠肉眼在浮层里调不出来。
 *
 * 角度约定：0° 指向正右方，顺时针为正（与屏幕坐标的 atan2 一致）；
 * 第 0 个目标固定在正上方（-90°），其余按 [angleFor] 顺时针排开。
 *
 * 命中范围只认环带：环带以内（圆心那块空的）和以外都不算瞄准。缺了这条，
 * 窗口是个方块、环带是个圆，方块四角会按角度算给“最近的 App”，看着就是
 * “拖到圆外还亮着某个应用”。
 */
object RingFlowGeometry {

    /** 第 [index] 个目标在轮盘上的角度（度）。 */
    fun angleFor(index: Int, count: Int): Float {
        if (count <= 0) return 0f
        return -90f + 360f * (index % count) / count
    }

    /** 第 [index] 个目标相对圆心的像素偏移，返回 (dx, dy)。 */
    fun offsetFor(index: Int, count: Int, radius: Float): Pair<Float, Float> {
        val radians = Math.toRadians(angleFor(index, count).toDouble())
        return (cos(radians).toFloat() * radius) to (sin(radians).toFloat() * radius)
    }

    /**
     * 手指落在 ([x], [y]) 时命中的目标下标。
     *
     * [innerRadiusPx] / [outerRadiusPx] 是环带的内外沿（像素），落在环带外一律返回 null。
     * 环带内按“角度最近”判定，不是按图标矩形：一个扇区里的任何位置都算数，
     * 手指不必精确压在 48dp 图标上。
     */
    fun targetIndexAt(
        x: Float,
        y: Float,
        centerX: Float,
        centerY: Float,
        count: Int,
        innerRadiusPx: Float,
        outerRadiusPx: Float,
    ): Int? {
        if (count <= 0 || outerRadiusPx <= innerRadiusPx) return null
        val dx = x - centerX
        val dy = y - centerY
        val distance = hypot(dx, dy)
        if (distance < innerRadiusPx || distance > outerRadiusPx) return null

        val step = 360f / count
        val degrees = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat() + 90f
        val normalized = ((degrees % 360f) + 360f) % 360f
        return (normalized / step).roundToInt() % count
    }
}
