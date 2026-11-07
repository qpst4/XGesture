package com.slideindex.app.overlay.volumepanel

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.slideindex.app.ui.theme.LocalAppDarkTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class ExpandPanelFrostedStyle(
    val tintColor: Int,
    val borderColor: Color,
    val innerScrimAlpha: Float,
    val fallbackBackground: Color,
)

@Composable
fun expandPanelFrostedStyle(): ExpandPanelFrostedStyle {
    val isDark = LocalAppDarkTheme.current
    return ExpandPanelFrostedStyle(
        tintColor = if (isDark) 0x88343438.toInt() else 0x78F5F5F7.toInt(),
        borderColor = if (isDark) {
            Color.White.copy(alpha = 0.14f)
        } else {
            Color.Black.copy(alpha = 0.06f)
        },
        innerScrimAlpha = if (isDark) 0.20f else 0f,
        fallbackBackground = MiuixTheme.colorScheme.surfaceContainer.copy(
            alpha = if (isDark) 0.94f else 0.92f,
        ),
    )
}

@Composable
fun expandPanelSecondaryTextColor(): Color {
    val isDark = LocalAppDarkTheme.current
    return if (isDark) {
        MiuixTheme.colorScheme.onSurface.copy(alpha = 0.90f)
    } else {
        MiuixTheme.colorScheme.onSurface.copy(alpha = 0.74f)
    }
}

@Composable
fun expandPanelSliderTrackColor(): Color {
    val isDark = LocalAppDarkTheme.current
    return MiuixTheme.colorScheme.outline.copy(alpha = if (isDark) 0.48f else 0.34f)
}
