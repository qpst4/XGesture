package com.slideindex.app.link

import android.graphics.Rect
import kotlin.math.hypot

/**
 * 链接在屏幕中的可见几何区域。
 */
data class LinkRegion(
    val url: String,
    val rects: List<Rect>,
    val isPrecise: Boolean,
    val groupId: Int,
    val textOffset: Int,
    val sourcePackage: String,
    val sourceText: String,
)

/** 包含多行换行的总外接矩形。 */
fun LinkRegion.frameBounds(): Rect =
    Rect(rects.first()).apply { rects.drop(1).forEach(::union) }

data class RadarBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    fun distance(x: Float, y: Float): Float =
        hypot(maxOf(left - x, 0f, x - right), maxOf(top - y, 0f, y - bottom))

    fun overlaps(other: RadarBox): Boolean =
        left < other.right && right > other.left && top < other.bottom && bottom > other.top
}

fun Rect.toRadarBox(offsetX: Int = 0, offsetY: Int = 0): RadarBox =
    RadarBox(
        (left - offsetX).toFloat(),
        (top - offsetY).toFloat(),
        (right - offsetX).toFloat(),
        (bottom - offsetY).toFloat(),
    )

data class RadarHitArea(
    val index: Int,
    val group: Int,
    val precise: Boolean,
    val boxes: List<RadarBox>,
)

/** 指针/取词十字光标命中探测算法（对齐 GestureEVO LinkRadarTargeting）。 */
fun findRadarTargets(
    areas: List<RadarHitArea>,
    x: Float,
    y: Float,
    radius: Float,
    previous: Int? = null,
    hysteresis: Float = 0f,
): List<Int> {
    val nearby = areas.mapNotNull { area ->
        area.boxes.minOfOrNull { it.distance(x, y) }?.takeIf { it <= radius }?.let { area to it }
    }
    val precise = nearby.filter { it.first.precise }
    val candidates = (precise.ifEmpty { nearby }).sortedBy { it.second }
    val closest = candidates.firstOrNull() ?: return emptyList()
    val primary = candidates.firstOrNull {
        it.first.index == previous && it.second - closest.second < hysteresis
    }?.first ?: closest.first
    return if (primary.precise) {
        precise.filter { (area, _) ->
            area.index == primary.index ||
                area.boxes.any { box -> primary.boxes.any(box::overlaps) }
        }.map { it.first.index }
    } else {
        areas.filter { !it.precise && it.group == primary.group }.map { it.index }
    }
}

fun enclosingRadarBox(boxes: List<RadarBox>): RadarBox? =
    boxes.takeIf { it.isNotEmpty() }?.let {
        RadarBox(
            it.minOf { box -> box.left },
            it.minOf { box -> box.top },
            it.maxOf { box -> box.right },
            it.maxOf { box -> box.bottom },
        )
    }

/**
 * 屏幕内容链接雷达常驻胶囊四向防碰撞摆放算法（对齐 GestureEVO LinkRadarActionLayout）。
 */
fun placeRadarActions(
    anchors: Map<Int, RadarBox>,
    obstacles: Map<Int, List<RadarBox>>,
    viewport: RadarBox,
    actionWidth: Float,
    actionHeight: Float,
    gap: Float,
): Map<Int, RadarBox> {
    if (actionWidth <= 0 || actionHeight <= 0 ||
        actionWidth > viewport.right - viewport.left ||
        actionHeight > viewport.bottom - viewport.top
    ) {
        return emptyMap()
    }
    val placed = linkedMapOf<Int, RadarBox>()
    for ((key, anchor) in anchors) {
        if (!anchor.overlaps(viewport)) continue
        val centerX = (anchor.left + anchor.right - actionWidth) / 2f
        val centerY = (anchor.top + anchor.bottom - actionHeight) / 2f
        val candidates = listOf(
            centerX to anchor.top - gap - actionHeight,
            centerX to anchor.bottom + gap,
            anchor.right + gap to centerY,
            anchor.left - gap - actionWidth to centerY,
        )
        val candidateBoxes = candidates.map { (x, y) ->
            val left = x.coerceIn(viewport.left, viewport.right - actionWidth)
            val top = y.coerceIn(viewport.top, viewport.bottom - actionHeight)
            RadarBox(left, top, left + actionWidth, top + actionHeight)
        }
        val bounds = candidateBoxes.firstOrNull { candidate ->
            obstacles.all { (group, boxes) -> group == key || boxes.none(candidate::overlaps) } &&
                placed.values.none(candidate::overlaps)
        } ?: candidateBoxes.firstOrNull { candidate ->
            placed.values.none(candidate::overlaps)
        } ?: candidateBoxes.firstOrNull()
        if (bounds != null) {
            placed[key] = bounds
        }
    }
    return placed
}
