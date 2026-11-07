package com.slideindex.app.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import kotlin.math.max
import kotlin.math.roundToInt

private val ShortcutBadgeBackground = Color(0xFF4976F2)

/** 与 [ShortcutBadgeRenderer] 一致的快捷方式角标（Compose 编辑页预览）。 */
@Composable
fun ShortcutBadgeOverlay(
    iconSize: Dp,
    modifier: Modifier = Modifier,
    alpha: Float = 1f
) {
    val densityScale = LocalDensity.current.density
    Box(modifier = modifier.size(iconSize)) {
        Canvas(Modifier.fillMaxSize()) {
            val iconDiameter = size.minDimension
            if (iconDiameter <= 1f || alpha <= 0f) return@Canvas
            drawShortcutBadge(
                iconCenterX = size.width / 2f,
                iconCenterY = size.height / 2f,
                iconDiameter = iconDiameter,
                alpha = alpha,
                density = densityScale
            )
        }
    }
}

fun DrawScope.drawShortcutBadge(
    iconCenterX: Float,
    iconCenterY: Float,
    iconDiameter: Float,
    alpha: Float,
    density: Float
) {
    val badgeDiameter = max(9f * density, iconDiameter * 0.27f)
    val radius = badgeDiameter / 2f
    val centerX = iconCenterX + iconDiameter * 0.34f
    val centerY = iconCenterY + iconDiameter * 0.34f
    val resolvedAlpha = (255f * alpha).coerceIn(0f, 255f).roundToInt()
    val borderColor = Color.White.copy(alpha = resolvedAlpha / 255f)
    val backgroundColor = ShortcutBadgeBackground.copy(alpha = resolvedAlpha / 255f)
    val glyphColor = Color.White.copy(alpha = resolvedAlpha / 255f)

    drawCircle(
        color = borderColor,
        radius = radius + 1.5f * density,
        center = androidx.compose.ui.geometry.Offset(centerX, centerY)
    )
    drawCircle(
        color = backgroundColor,
        radius = radius,
        center = androidx.compose.ui.geometry.Offset(centerX, centerY)
    )

    val lightning = Path().apply {
        moveTo(centerX + radius * 0.02f, centerY - radius * 0.62f)
        lineTo(centerX - radius * 0.45f, centerY + radius * 0.05f)
        lineTo(centerX - radius * 0.10f, centerY + radius * 0.05f)
        lineTo(centerX - radius * 0.27f, centerY + radius * 0.62f)
        lineTo(centerX + radius * 0.47f, centerY - radius * 0.16f)
        lineTo(centerX + radius * 0.12f, centerY - radius * 0.16f)
        close()
    }
    drawPath(lightning, glyphColor)
}
