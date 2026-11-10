package com.slideindex.app.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import kotlin.math.max
import kotlin.math.roundToInt

private val MacroBadgeBackground = Color(0xFF7C5CFF)

/** 与 [MacroBadgeRenderer] 一致的「运行宏」角标（Compose 编辑页 / 面板预览）。 */
@Composable
fun MacroBadgeOverlay(
    iconSize: Dp,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
) {
    val densityScale = LocalDensity.current.density
    Box(modifier = modifier.size(iconSize)) {
        Canvas(Modifier.fillMaxSize()) {
            val iconDiameter = size.minDimension
            if (iconDiameter <= 1f || alpha <= 0f) return@Canvas
            drawMacroBadge(
                iconCenterX = size.width / 2f,
                iconCenterY = size.height / 2f,
                iconDiameter = iconDiameter,
                alpha = alpha,
                density = densityScale,
            )
        }
    }
}

fun DrawScope.drawMacroBadge(
    iconCenterX: Float,
    iconCenterY: Float,
    iconDiameter: Float,
    alpha: Float,
    density: Float,
) {
    val badgeDiameter = max(9f * density, iconDiameter * 0.27f)
    val radius = badgeDiameter / 2f
    val centerX = iconCenterX + iconDiameter * 0.34f
    val centerY = iconCenterY + iconDiameter * 0.34f
    val resolvedAlpha = (255f * alpha).coerceIn(0f, 255f).roundToInt()
    val borderColor = Color.White.copy(alpha = resolvedAlpha / 255f)
    val backgroundColor = MacroBadgeBackground.copy(alpha = resolvedAlpha / 255f)
    val glyphColor = Color.White.copy(alpha = resolvedAlpha / 255f)
    val center = Offset(centerX, centerY)

    drawCircle(color = borderColor, radius = radius + 1.5f * density, center = center)
    drawCircle(color = backgroundColor, radius = radius, center = center)

    val play = Path().apply {
        moveTo(centerX - radius * 0.26f, centerY - radius * 0.40f)
        lineTo(centerX + radius * 0.42f, centerY)
        lineTo(centerX - radius * 0.26f, centerY + radius * 0.40f)
        close()
    }
    drawPath(play, glyphColor)
}
