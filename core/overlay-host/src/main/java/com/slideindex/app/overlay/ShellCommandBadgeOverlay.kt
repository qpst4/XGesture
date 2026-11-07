package com.slideindex.app.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

/** 与 [ShellCommandBadgeRenderer] 一致的 Shell 命令角标（Compose 编辑页预览）。 */
@Composable
fun ShellCommandBadgeOverlay(
    iconSize: Dp,
    modifier: Modifier = Modifier,
    alpha: Float = 1f
) {
    val densityScale = LocalDensity.current.density
    Box(modifier = modifier.size(iconSize)) {
        Canvas(Modifier.fillMaxSize()) {
            val iconDiameter = size.minDimension
            if (iconDiameter <= 1f || alpha <= 0f) return@Canvas
            drawShellCommandBadge(
                iconCenterX = size.width / 2f,
                iconCenterY = size.height / 2f,
                iconDiameter = iconDiameter,
                alpha = alpha,
                density = densityScale
            )
        }
    }
}

fun DrawScope.drawShellCommandBadge(
    iconCenterX: Float,
    iconCenterY: Float,
    iconDiameter: Float,
    alpha: Float,
    density: Float
) {
    ShellCommandBadgeRenderer.draw(
        canvas = drawContext.canvas.nativeCanvas,
        iconCenterX = iconCenterX,
        iconCenterY = iconCenterY,
        iconDiameter = iconDiameter,
        alpha = alpha,
        density = density
    )
}
