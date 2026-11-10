package com.slideindex.app.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import kotlin.math.roundToInt

private val CloneRingColor = Color(0xB3000000)
private val CloneDotColor = Color(0xFFFFC107)

/** 分身黄点半径占比（与 `CloneBadge` 保持一致）。 */
private const val CLONE_DOT_RADIUS_RATIO = 0.11f

/**
 * 绘制期分身角标（Compose）：与 `CloneBadge.draw` 同一位置规则。
 *
 * 面板预览必须在图标**画完之后**叠这个，不要把黄点烘进图标位图——
 * 圆形图标会把方角上的黄点裁掉（角落轮盘 / 圆环 / 蜂窝都踩过）。
 */
@Composable
fun CloneBadgeOverlay(
    iconSize: Dp,
    circular: Boolean,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
) {
    val densityScale = LocalDensity.current.density
    Box(modifier = modifier.size(iconSize)) {
        Canvas(Modifier.fillMaxSize()) {
            val iconDiameter = size.minDimension
            if (iconDiameter <= 1f || alpha <= 0f) return@Canvas
            drawCloneBadge(
                iconCenterX = size.width / 2f,
                iconCenterY = size.height / 2f,
                iconSize = iconDiameter,
                circular = circular,
                alpha = alpha,
                density = densityScale,
            )
        }
    }
}

fun DrawScope.drawCloneBadge(
    iconCenterX: Float,
    iconCenterY: Float,
    iconSize: Float,
    circular: Boolean,
    alpha: Float = 1f,
    density: Float = 1f,
) {
    val r = iconSize * CLONE_DOT_RADIUS_RATIO
    val (cx, cy) = if (circular) {
        val distance = iconSize * 0.5f - r * 1.35f
        val offset = distance / 1.41421356f
        (iconCenterX + offset) to (iconCenterY - offset)
    } else {
        val half = iconSize / 2f
        (iconCenterX + half - r * 1.3f) to (iconCenterY - half + r * 1.3f)
    }
    val resolvedAlpha = (255f * alpha).coerceIn(0f, 255f).roundToInt()
    val ring = CloneRingColor.copy(alpha = resolvedAlpha / 255f)
    val dot = CloneDotColor.copy(alpha = resolvedAlpha / 255f)
    val center = Offset(cx, cy)
    drawCircle(color = ring, radius = r, center = center)
    drawCircle(color = dot, radius = r * 0.78f, center = center)
}
