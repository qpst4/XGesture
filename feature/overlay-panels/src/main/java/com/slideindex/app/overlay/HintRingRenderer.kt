package com.slideindex.app.overlay

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.util.LruCache
import kotlin.math.min

/**
 * 提示卡片里那个**图标外圈的进度线**（长按倒计时）。
 *
 * 只负责画线，不管卡片本身：调用方把自己卡片里图标的位图与外接正方形传进来即可。
 * 位置/颜色/笔宽全部对齐快捷轮盘的 `QuickWheelActionHud`（底环 #33FFFFFF、进度 #FF64B5F6、3dp），
 * 形状沿用 `HudIconSilhouette` 的思路——沿位图 alpha 探测圆角半径，方形 / squircle / 圆形各自贴合。
 *
 * @param progress 0..1；**< 0 表示不画**（没有在按住任何东西时）
 *
 * 另外提供 [drawPipBadge]：长按到时间且目标是"小窗"时，在图标右下角画一个画中画角标。
 */
object HintRingRenderer {

    /** 底环（与 HudRingTrack 一致）。 */
    const val TRACK_COLOR = 0x33FFFFFF

    /** 进度段（与 HudRingFill 一致）。 */
    const val FILL_COLOR = 0xFF64B5F6.toInt()

    /** 笔宽 dp（与 HudRingStroke 一致）。 */
    const val STROKE_DP = 3f

    /** 归一化圆角半径 ≥ 该值 → 按椭圆画。 */
    private const val OVAL_RADIUS_RATIO = 0.42f

    /** 圆角矩形：对角内缩深度 / 圆角半径。 */
    private const val CORNER_INSET_TO_RADIUS = 0.2929f

    /** 探测失败时的圆角比例。 */
    private const val FALLBACK_RADIUS_RATIO = 0.26f

    private const val PROBE_STEPS = 8
    private const val ALPHA_THRESHOLD = 8

    private val radiusCache = LruCache<Int, Float>(64)

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val measure = PathMeasure()
    private val segment = Path()
    private val outline = Path()

    /**
     * 长按到时间后，在**图标右下角**画一个"小窗（画中画）"角标。
     *
     * 只用 Canvas 画（外框 + 内部小矩形），不依赖任何位图资源。
     *
     * @param iconBounds 卡片里图标的矩形（与进度环用的是同一个）
     */
    fun drawPipBadge(canvas: Canvas, iconBounds: RectF, density: Float) {
        val side = min(iconBounds.width(), iconBounds.height())
        if (side <= 0f) return
        val badgeSize = (side * 0.34f).coerceAtLeast(10f * density)
        val margin = 1f * density
        val right = iconBounds.right + margin
        val bottom = iconBounds.bottom + margin
        val left = right - badgeSize
        val top = bottom - badgeSize
        val radius = badgeSize * 0.22f

        // 底：实心圆角方，保证在浅色图标上也看得见
        badgePaint.style = Paint.Style.FILL
        badgePaint.color = BADGE_FILL
        canvas.drawRoundRect(left, top, right, bottom, radius, radius, badgePaint)

        // 外框：画中画的"屏幕"
        badgePaint.style = Paint.Style.STROKE
        badgePaint.strokeWidth = badgeSize * 0.09f
        badgePaint.color = BADGE_GLYPH
        val pad = badgeSize * 0.2f
        canvas.drawRoundRect(
            left + pad,
            top + pad,
            right - pad,
            bottom - pad,
            radius * 0.4f,
            radius * 0.4f,
            badgePaint,
        )

        // 内部小矩形：画中画的"小窗"
        badgePaint.style = Paint.Style.FILL
        val innerSize = badgeSize * 0.3f
        canvas.drawRoundRect(
            right - pad - innerSize,
            bottom - pad - innerSize,
            right - pad,
            bottom - pad,
            innerSize * 0.25f,
            innerSize * 0.25f,
            badgePaint,
        )
    }

    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private const val BADGE_FILL = 0xFFFFFFFF.toInt()
    private const val BADGE_GLYPH = 0xFF111827.toInt()

    /**
     * @param icon 卡片里那张图标位图（用于探测轮廓；null 时按圆角矩形画）
     * @param bounds 轮廓外接正方形（调用方已把外扩量与笔宽算进去）
     * @param progress 0..1；**< 0 不画**
     */
    fun draw(canvas: Canvas, icon: Bitmap?, bounds: RectF, progress: Float, density: Float) {
        if (progress < 0f) return
        val value = progress.coerceIn(0f, 1f)
        buildOutline(icon, bounds)
        val stroke = STROKE_DP * density
        trackPaint.strokeWidth = stroke
        trackPaint.color = TRACK_COLOR
        canvas.drawPath(outline, trackPaint)
        if (value <= 0f) return
        measure.setPath(outline, true)
        val total = measure.length
        if (total <= 0f) return
        segment.reset()
        // 从正上方中点起、顺时针取一段（与 HUD 的进度方向一致）
        measure.getSegment(0f, total * value, segment, true)
        fillPaint.strokeWidth = stroke
        fillPaint.color = FILL_COLOR
        canvas.drawPath(segment, fillPaint)
    }

    private fun buildOutline(icon: Bitmap?, bounds: RectF) {
        val ratio = icon?.let(::probeRadiusRatio) ?: FALLBACK_RADIUS_RATIO
        outline.reset()
        if (ratio >= OVAL_RADIUS_RATIO) {
            outline.addArc(bounds, -90f, 360f)
            return
        }
        val r = (min(bounds.width(), bounds.height()) * ratio)
            .coerceIn(0f, min(bounds.width(), bounds.height()) / 2f)
        val left = bounds.left
        val top = bounds.top
        val right = bounds.right
        val bottom = bounds.bottom
        val topCenterX = (left + right) / 2f
        outline.moveTo(topCenterX, top)
        outline.lineTo(right - r, top)
        if (r > 0f) outline.arcTo(RectF(right - 2f * r, top, right, top + 2f * r), -90f, 90f, false)
        outline.lineTo(right, bottom - r)
        if (r > 0f) outline.arcTo(RectF(right - 2f * r, bottom - 2f * r, right, bottom), 0f, 90f, false)
        outline.lineTo(left + r, bottom)
        if (r > 0f) outline.arcTo(RectF(left, bottom - 2f * r, left + 2f * r, bottom), 90f, 90f, false)
        outline.lineTo(left, top + r)
        if (r > 0f) outline.arcTo(RectF(left, top, left + 2f * r, top + 2f * r), 180f, 90f, false)
        outline.lineTo(topCenterX, top)
        outline.close()
    }

    /** 探测归一化圆角半径（0 = 方形，0.5 = 圆），取四角中位数；同一张位图只算一次。 */
    private fun probeRadiusRatio(bitmap: Bitmap): Float {
        val key = System.identityHashCode(bitmap)
        radiusCache.get(key)?.let { return it }
        val ratio = runCatching { computeRadiusRatio(bitmap) }.getOrNull() ?: FALLBACK_RADIUS_RATIO
        radiusCache.put(key, ratio)
        return ratio
    }

    private fun computeRadiusRatio(bitmap: Bitmap): Float? {
        val size = min(bitmap.width, bitmap.height)
        if (size < 8) return null
        val ratios = ArrayList<Float>(4)
        for (dirX in intArrayOf(1, -1)) {
            for (dirY in intArrayOf(1, -1)) {
                probeCornerRatio(bitmap, size, dirX, dirY)?.let(ratios::add)
            }
        }
        if (ratios.isEmpty()) return null
        ratios.sort()
        return ratios[ratios.size / 2]
    }

    private fun probeCornerRatio(bitmap: Bitmap, size: Int, dirX: Int, dirY: Int): Float? {
        val originX = if (dirX > 0) 0 else size - 1
        val originY = if (dirY > 0) 0 else size - 1
        // 对角中点不透明才说明图标填满了位图，探测才有意义
        if (!isOpaque(bitmap, size, originX, originY, dirX, dirY, 0.5f)) return null
        var transparent = 0f
        var opaque = 0.5f
        repeat(PROBE_STEPS) {
            val mid = (transparent + opaque) / 2f
            if (isOpaque(bitmap, size, originX, originY, dirX, dirY, mid)) {
                opaque = mid
            } else {
                transparent = mid
            }
        }
        return (opaque / CORNER_INSET_TO_RADIUS).coerceIn(0f, 0.5f)
    }

    private fun isOpaque(
        bitmap: Bitmap,
        size: Int,
        originX: Int,
        originY: Int,
        dirX: Int,
        dirY: Int,
        t: Float,
    ): Boolean {
        val x = (originX + dirX * size * t).toInt().coerceIn(0, bitmap.width - 1)
        val y = (originY + dirY * size * t).toInt().coerceIn(0, bitmap.height - 1)
        return (bitmap.getPixel(x, y) ushr 24) > ALPHA_THRESHOLD
    }
}
