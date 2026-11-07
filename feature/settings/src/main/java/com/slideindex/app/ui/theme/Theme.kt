package com.slideindex.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.AppThemeMode
import com.slideindex.app.settings.ThemePaletteStyle

/** App 解析后的深色状态（尊重 [AppThemeMode]，非仅系统）。 */
val LocalAppDarkTheme = staticCompositionLocalOf { false }

@Composable
fun SlideIndexTheme(
    seedColor: Color = Purple40,
    dynamicColor: Boolean = false,
    paletteStyle: ThemePaletteStyle = ThemePaletteStyle.TONAL_SPOT,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = rememberSlideIndexColorScheme(
        seedColor = seedColor,
        dynamicColor = dynamicColor,
        paletteStyle = paletteStyle,
        darkTheme = darkTheme,
    )

    CompositionLocalProvider(LocalAppDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = ExpressiveShapes,
            typography = SlideIndexTypography,
            content = content,
        )
    }
}

/** 从 [AppSettings] 解析明暗与种子色，供浮层 / Trampoline 与主界面一致。 */
@Composable
fun SlideIndexTheme(
    settings: AppSettings,
    content: @Composable () -> Unit,
) {
    val darkTheme = AppThemeMode.fromId(settings.themeModeId).resolveIsDark(isSystemInDarkTheme())
    SlideIndexTheme(
        seedColor = Color(settings.themeColorArgb),
        dynamicColor = settings.dynamicColorEnabled,
        paletteStyle = ThemePaletteStyle.fromId(settings.themePaletteStyleId),
        darkTheme = darkTheme,
        content = content,
    )
}
