package com.slideindex.app.overlay.quickwheel

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.graphics.get

/**
 * HUD 长按进度环的**图标轮廓**。
 *
 * 目标：环贴着图标本身走，而不是永远套一个圆 —— squircle / 圆角方 / 圆 / 方形图标
 * 各自的四角留白差别很大，固定圆环套在圆角方图标上会显得"四角离得太远"。
 *
 * 形状来源：直接**探测图标位图的 alpha 通道**（不查系统 Drawable 的 outline —— 那个对
 * 非自适应图标返回空、对图库位图无效，而且要多一条同步加载链路）。做法是沿四个角的
 * 内对角线二分查找"透明 → 不透明"的深度 `t`，再由圆角矩形的几何关系
 * `t = r · (√2 − 1) / (边长 · √2)` 反解出圆角半径 `r ≈ t / 0.2929`：
 * - `r ≈ 0`         → 方形；
 * - `r ≈ 0.22~0.28` → squircle / 圆角方（本机启动器图标）；
 * - `r ≥ 0.42`      → 直接按椭圆绘制（圆形图标精确贴合）。
 *
 * 能被识别的：应用图标 / 快捷方式图标 / 图库图片（都是位图）。
 * 识别不了的两类——**矢量 / 文字图标**（没有位图）、以及"图标没填满位图"
 * （对角中点都是透明的，探测无意义）——一律退回圆角矩形（[FALLBACK_RADIUS_RATIO]）。
 *
 * 代价：首次探测约 4 × (1 + [PROBE_STEPS]) 次像素读取；调用方用 `remember` 缓存结果，
 * 图标不变就不会重算。
 */
object HudIconSilhouette {

    /** 归一化圆角半径 ≥ 该值 → 当成椭圆（圆形图标）。 */
    private const val OVAL_RADIUS_RATIO = 0.42f

    /** 圆角矩形里"对角内缩深度 / 圆角半径"的比值（1 − 1/√2）。 */
    private const val CORNER_INSET_TO_RADIUS = 0.2929f

    /** 探测失败时假设的圆角比例（按主流 squircle 估）。 */
    private const val FALLBACK_RADIUS_RATIO = 0.26f

    /** 二分次数：0.5 / 2^8 ≈ 0.002 的精度已远超需要。 */
    private const val PROBE_STEPS = 8

    /** 判定"不透明"的 alpha 阈值（抗边缘抗锯齿）。 */
    private const val ALPHA_THRESHOLD = 8

    /** 四个角指向内部的对角方向。 */
    private val CORNER_DIRECTIONS = listOf(
        1 to 1,
        -1 to 1,
        1 to -1,
        -1 to -1,
    )

    /**
     * 生成轮廓路径。[bounds] 是轮廓外接正方形（调用方已把外扩量算进去）。
     *
     * 起点固定在**正上方中点**、顺时针，与原来的圆环进度方向保持一致。
     */
    fun outlineFor(bitmap: ImageBitmap?, bounds: Rect): Path {
        val ratio = bitmap?.let(::probeRadiusRatio) ?: FALLBACK_RADIUS_RATIO
        return if (ratio >= OVAL_RADIUS_RATIO) {
            // 圆形 / 椭圆：addArc 从 -90°（正上方）顺时针一圈。
            Path().apply { addArc(bounds, -90f, 360f) }
        } else {
            roundedRectPath(bounds, minOf(bounds.width, bounds.height) * ratio)
        }
    }

    /** 圆角矩形路径：从正上方中点起，顺时针走一圈（与椭圆分支起点一致）。 */
    private fun roundedRectPath(bounds: Rect, radius: Float): Path {
        val r = radius.coerceIn(0f, minOf(bounds.width, bounds.height) / 2f)
        val left = bounds.left
        val top = bounds.top
        val right = bounds.right
        val bottom = bounds.bottom
        val topCenterX = (left + right) / 2f
        return Path().apply {
            moveTo(topCenterX, top)
            lineTo(right - r, top)
            if (r > 0f) arcTo(Rect(right - 2f * r, top, right, top + 2f * r), -90f, 90f, false)
            lineTo(right, bottom - r)
            if (r > 0f) arcTo(Rect(right - 2f * r, bottom - 2f * r, right, bottom), 0f, 90f, false)
            lineTo(left + r, bottom)
            if (r > 0f) arcTo(Rect(left, bottom - 2f * r, left + 2f * r, bottom), 90f, 90f, false)
            lineTo(left, top + r)
            if (r > 0f) arcTo(Rect(left, top, left + 2f * r, top + 2f * r), 180f, 90f, false)
            lineTo(topCenterX, top)
            close()
        }
    }

    /**
     * 探测归一化圆角半径（0f = 方形，0.5f = 圆）。
     *
     * 取四个角结果的**中位数**：图标内部图案可能干扰单个角的探测，中位数比平均值稳。
     */
    private fun probeRadiusRatio(bitmap: ImageBitmap): Float? {
        val pixels = runCatching { bitmap.asAndroidBitmap() }.getOrNull() ?: return null
        val size = minOf(pixels.width, pixels.height)
        if (size < 8) return null
        val ratios = CORNER_DIRECTIONS.mapNotNull { (dirX, dirY) ->
            probeCornerRatio(pixels, size, dirX, dirY)
        }
        if (ratios.isEmpty()) return null
        return ratios.sorted()[ratios.size / 2]
    }

    /** 单个角：二分查找"透明 → 不透明"的深度，换算成归一化圆角半径。 */
    private fun probeCornerRatio(pixels: Bitmap, size: Int, dirX: Int, dirY: Int): Float? {
        val originX = if (dirX > 0) 0 else size - 1
        val originY = if (dirY > 0) 0 else size - 1
        // 先确认对角中点不透明；否则说明图标没填满位图，探测没有意义。
        if (!isOpaque(pixels, size, originX, originY, dirX, dirY, 0.5f)) return null
        var transparent = 0f
        var opaque = 0.5f
        repeat(PROBE_STEPS) {
            val mid = (transparent + opaque) / 2f
            if (isOpaque(pixels, size, originX, originY, dirX, dirY, mid)) {
                opaque = mid
            } else {
                transparent = mid
            }
        }
        return (opaque / CORNER_INSET_TO_RADIUS).coerceIn(0f, 0.5f)
    }

    /** 沿对角方向取 [t]（以边长为单位）处的像素是否不透明。 */
    private fun isOpaque(
        pixels: Bitmap,
        size: Int,
        originX: Int,
        originY: Int,
        dirX: Int,
        dirY: Int,
        t: Float,
    ): Boolean {
        val x = (originX + dirX * size * t).toInt().coerceIn(0, pixels.width - 1)
        val y = (originY + dirY * size * t).toInt().coerceIn(0, pixels.height - 1)
        return (pixels[x, y] ushr 24) > ALPHA_THRESHOLD
    }
}
