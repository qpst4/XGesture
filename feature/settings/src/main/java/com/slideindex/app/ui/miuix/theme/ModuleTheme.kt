package com.slideindex.app.ui.miuix.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor as Material3LocalContentColor
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.slideindex.app.settings.AppColorSpec
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.toThemeSpec
import com.slideindex.app.settings.AppThemeMode
import com.slideindex.app.settings.DarkBackgroundStyle
import com.slideindex.app.settings.OverlaySettings
import com.slideindex.app.settings.ThemePaletteStyle
import com.slideindex.app.settings.TopAppBarBlurStyle
import com.slideindex.app.settings.UiDensityScaleLimits
import com.slideindex.app.ui.miuix.LocalTopAppBarBlurStyle
import com.slideindex.app.ui.theme.LocalAppDarkTheme
import com.slideindex.app.ui.theme.LocalPlatformDensity
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
/**
 * Miuix + Material 3 Expressive 双主题，对齐 WeKit [ModuleTheme]。
 * 配色由 [AppSettings] 驱动：customColor 关用 Miuix 默认蓝，开则 Monet + MaterialKolor。
 */
@Composable
fun ModuleTheme(
    settings: AppSettings,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val themeMode = AppThemeMode.fromId(settings.themeModeId)
    val darkTheme = themeMode.resolveIsDark(isSystemInDarkTheme())
    val paletteStyle = ThemePaletteStyle.fromId(settings.themePaletteStyleId)
    val colorSpec = AppColorSpec.fromId(settings.themeColorSpecId).let { spec ->
        if (paletteStyle.supportsMiuixSpec2025()) spec else AppColorSpec.SPEC_2021
    }

    val controller = if (!settings.customColorEnabled) {
        ThemeController(
            colorSchemeMode = if (darkTheme) ColorSchemeMode.Dark else ColorSchemeMode.Light,
            darkColors = darkColorsFor(DarkBackgroundStyle.fromId(settings.darkBackgroundStyleId)),
            isDark = darkTheme,
        )
    } else {
        ThemeController(
            colorSchemeMode = if (darkTheme) ColorSchemeMode.MonetDark else ColorSchemeMode.MonetLight,
            keyColor = Color(MiuixSeedResolver.customSeed(context, settings.toThemeSpec(darkTheme))),
            colorSpec = colorSpec.toMiuix(),
            paletteStyle = paletteStyle.toMiuix(),
            isDark = darkTheme,
        )
    }

    val materialScheme = resolveAppMaterialColorScheme(context, settings.toThemeSpec(darkTheme))

    val currentDensity = LocalDensity.current
    val densityScale = UiDensityScaleLimits.normalize(settings.uiDensityScale)
    val appDensity = remember(currentDensity, densityScale) {
        Density(
            density = currentDensity.density * densityScale,
            fontScale = currentDensity.fontScale,
        )
    }

    MiuixTheme(controller = controller) {
        CompositionLocalProvider(
            LocalPlatformDensity provides currentDensity,
            LocalDensity provides appDensity,
            LocalContentColor provides MiuixTheme.colorScheme.onBackground,
            LocalAppDarkTheme provides darkTheme,
            LocalTopAppBarBlurStyle provides TopAppBarBlurStyle.fromId(settings.topAppBarBlurStyleId),
        ) {
            MaterialExpressiveTheme(
                colorScheme = materialScheme,
                motionScheme = MotionScheme.expressive(),
            ) {
                CompositionLocalProvider(
                    Material3LocalContentColor provides materialScheme.onBackground,
                ) {
                    content()
                }
            }
        }
    }
}

/** 无 [AppSettings] 时用于独立 Activity 等场景的简化入口。 */
@Composable
fun ModuleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    customColor: Boolean = false,
    seedColorArgb: Int = 0xFF6750A4.toInt(),
    dynamicWallpaper: Boolean = false,
    paletteStyle: ThemePaletteStyle = ThemePaletteStyle.TONAL_SPOT,
    colorSpec: AppColorSpec = AppColorSpec.SPEC_2025,
    content: @Composable () -> Unit,
) {
    ModuleTheme(
        settings = AppSettings(
            themeModeId = if (darkTheme) AppThemeMode.DARK.id else AppThemeMode.LIGHT.id,
            customColorEnabled = customColor,
            themeColorArgb = seedColorArgb,
            dynamicColorEnabled = dynamicWallpaper,
            themePaletteStyleId = paletteStyle.id,
            themeColorSpecId = colorSpec.id,
        ),
        content = content,
    )
}

/** 从 [OverlaySettings] 提取 ModuleTheme 所需字段。 */
fun OverlaySettings.toModuleThemeSettings(): AppSettings = AppSettings(
    themeColorArgb = themeColorArgb,
    dynamicColorEnabled = dynamicColorEnabled,
    themePaletteStyleId = themePaletteStyleId,
    themeModeId = themeModeId,
    customColorEnabled = customColorEnabled,
    darkBackgroundStyleId = darkBackgroundStyleId,
    themeColorSpecId = themeColorSpecId,
    uiDensityScale = uiDensityScale,
    topAppBarBlurStyleId = topAppBarBlurStyleId,
)

