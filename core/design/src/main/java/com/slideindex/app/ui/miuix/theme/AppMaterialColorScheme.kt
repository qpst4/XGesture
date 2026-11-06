package com.slideindex.app.ui.miuix.theme

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.dynamiccolor.ColorSpec
import com.slideindex.app.design.AppThemeSpec
import com.slideindex.app.settings.DarkBackgroundStyle
import top.yukonga.miuix.kmp.theme.darkColorScheme as miuixDarkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme as miuixLightColorScheme

/**
 * 与 [ModuleTheme] 一致的 Material3 [ColorScheme]，供 Compose 与 View 主题共用。
 *
 * 入参是纯 [AppThemeSpec]：settings → spec 的映射在 `:app` 的 `AppThemeSpecAdapter` 里，
 * 这个模块不依赖 `:feature:settings`。
 */
fun resolveAppMaterialColorScheme(
    context: Context,
    spec: AppThemeSpec,
): ColorScheme =
    if (spec.customColorEnabled) {
        MiuixSeedResolver.materialScheme(
            MiuixSeedResolver.customSeed(context, spec),
            spec,
        )
    } else {
        defaultMiuixMaterialScheme(spec.dark, DarkBackgroundStyle.fromId(spec.darkBackgroundStyleId))
    }

/** 明显更热（拖影 / 提示窗会反复问），配色生成不便宜，所以按输入做一层单值缓存。 */
@Volatile
private var accentCacheKey: AppThemeSpec? = null

@Volatile
private var accentCacheArgb: Int = 0

/**
 * 浮层 chrome（悬浮球 / 边缘线条 / 拖影 / 手势提示）要的**主题强调色**（ARGB）。
 *
 * 种子选择与 [resolveAppMaterialColorScheme] 完全一致：开了自定义色 → 用户选的颜色
 * （开了动态取色时是壁纸强调色）；没开 → Miuix 默认主色。
 */
fun resolveAppAccentArgb(context: Context, spec: AppThemeSpec): Int {
    if (spec == accentCacheKey) return accentCacheArgb
    val argb = if (spec.customColorEnabled) {
        MiuixSeedResolver.customSeed(context, spec)
    } else {
        resolveAppMaterialColorScheme(context, spec).primary.toArgb()
    }
    accentCacheKey = spec
    accentCacheArgb = argb
    return argb
}

fun defaultMiuixMaterialScheme(
    darkTheme: Boolean,
    darkBackgroundStyle: DarkBackgroundStyle,
): ColorScheme {
    val miuixColors = if (darkTheme) darkColorsFor(darkBackgroundStyle) else miuixLightColorScheme()
    val dialogSurface = miuixColors.surfaceContainer

    return dynamicColorScheme(
        seedColor = miuixColors.primary,
        isDark = darkTheme,
        style = PaletteStyle.TonalSpot,
        specVersion = ColorSpec.SpecVersion.SPEC_2021,
    ).copy(
        primary = miuixColors.primary,
        onPrimary = miuixColors.onPrimary,
        primaryContainer = miuixColors.primaryContainer,
        onPrimaryContainer = miuixColors.onPrimaryContainer,
        error = miuixColors.error,
        onError = miuixColors.onError,
        errorContainer = miuixColors.errorContainer,
        onErrorContainer = miuixColors.onErrorContainer,
        background = miuixColors.background,
        onBackground = miuixColors.onBackground,
        surface = dialogSurface,
        onSurface = miuixColors.onSurfaceContainer,
        surfaceVariant = miuixColors.surfaceVariant,
        onSurfaceVariant = miuixColors.onSurfaceVariantSummary,
        surfaceTint = dialogSurface,
        outline = miuixColors.outline,
        outlineVariant = miuixColors.dividerLine,
        surfaceBright = dialogSurface,
        surfaceContainerLowest = dialogSurface,
        surfaceContainerLow = dialogSurface,
        surfaceContainer = dialogSurface,
        surfaceContainerHigh = miuixColors.surfaceContainerHigh,
        surfaceContainerHighest = miuixColors.surfaceContainerHighest,
        surfaceDim = miuixColors.surface,
    )
}

fun darkColorsFor(style: DarkBackgroundStyle) = miuixDarkColorScheme().copy(
    background = when (style) {
        DarkBackgroundStyle.QUIET_BLUE -> Color(0xFF101820)
        DarkBackgroundStyle.DEEP_BLACK -> Color(0xFF090D12)
        DarkBackgroundStyle.AMOLED_BLACK -> Color.Black
    },
    surface = when (style) {
        DarkBackgroundStyle.QUIET_BLUE -> Color(0xFF17232D)
        DarkBackgroundStyle.DEEP_BLACK -> Color(0xFF11161C)
        DarkBackgroundStyle.AMOLED_BLACK -> Color(0xFF080808)
    },
    surfaceContainer = when (style) {
        DarkBackgroundStyle.QUIET_BLUE -> Color(0xFF1C2B36)
        DarkBackgroundStyle.DEEP_BLACK -> Color(0xFF171D24)
        DarkBackgroundStyle.AMOLED_BLACK -> Color(0xFF101010)
    },
    surfaceContainerHigh = when (style) {
        DarkBackgroundStyle.QUIET_BLUE -> Color(0xFF243744)
        DarkBackgroundStyle.DEEP_BLACK -> Color(0xFF202832)
        DarkBackgroundStyle.AMOLED_BLACK -> Color(0xFF181818)
    },
    surfaceContainerHighest = when (style) {
        DarkBackgroundStyle.QUIET_BLUE -> Color(0xFF2C4352)
        DarkBackgroundStyle.DEEP_BLACK -> Color(0xFF29333E)
        DarkBackgroundStyle.AMOLED_BLACK -> Color(0xFF202020)
    },
)
