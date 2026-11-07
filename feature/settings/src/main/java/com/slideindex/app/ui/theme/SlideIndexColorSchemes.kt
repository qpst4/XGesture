package com.slideindex.app.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.google.android.material.color.utilities.DynamicColor
import com.google.android.material.color.utilities.DynamicScheme
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.MaterialDynamicColors
import com.google.android.material.color.utilities.SchemeContent
import com.google.android.material.color.utilities.SchemeExpressive
import com.google.android.material.color.utilities.SchemeFidelity
import com.google.android.material.color.utilities.SchemeFruitSalad
import com.google.android.material.color.utilities.SchemeMonochrome
import com.google.android.material.color.utilities.SchemeNeutral
import com.google.android.material.color.utilities.SchemeRainbow
import com.google.android.material.color.utilities.SchemeTonalSpot
import com.google.android.material.color.utilities.SchemeVibrant
import com.slideindex.app.settings.ThemePaletteStyle

private val materialDynamicColors = MaterialDynamicColors()

fun seedDynamicScheme(
    seedArgb: Int,
    darkTheme: Boolean,
    style: ThemePaletteStyle,
    contrastLevel: Double = 0.0,
): DynamicScheme {
    val hct = Hct.fromInt(seedArgb)
    return when (style) {
        ThemePaletteStyle.TONAL_SPOT -> SchemeTonalSpot(hct, darkTheme, contrastLevel)
        ThemePaletteStyle.VIBRANT -> SchemeVibrant(hct, darkTheme, contrastLevel)
        ThemePaletteStyle.EXPRESSIVE -> SchemeExpressive(hct, darkTheme, contrastLevel)
        ThemePaletteStyle.MONOCHROME -> SchemeMonochrome(hct, darkTheme, contrastLevel)
        ThemePaletteStyle.NEUTRAL -> SchemeNeutral(hct, darkTheme, contrastLevel)
        ThemePaletteStyle.FIDELITY -> SchemeFidelity(hct, darkTheme, contrastLevel)
        ThemePaletteStyle.CONTENT -> SchemeContent(hct, darkTheme, contrastLevel)
        ThemePaletteStyle.RAINBOW -> SchemeRainbow(hct, darkTheme, contrastLevel)
        ThemePaletteStyle.FRUIT_SALAD -> SchemeFruitSalad(hct, darkTheme, contrastLevel)
    }
}

fun colorSchemeFromSeed(
    seedColor: Color,
    darkTheme: Boolean,
    paletteStyle: ThemePaletteStyle,
): ColorScheme {
    val dynamicScheme = seedDynamicScheme(seedColor.toArgb(), darkTheme, paletteStyle)
    return dynamicScheme.toComposeColorScheme()
}

fun slideIndexColorScheme(
    context: Context,
    seedColor: Color,
    dynamicColor: Boolean,
    paletteStyle: ThemePaletteStyle,
    darkTheme: Boolean,
): ColorScheme {
    if (dynamicColor) {
        val wallpaperScheme = if (darkTheme) {
            dynamicDarkColorScheme(context)
        } else {
            dynamicLightColorScheme(context)
        }
        if (paletteStyle == ThemePaletteStyle.TONAL_SPOT) {
            return wallpaperScheme
        }
        val seedArgb = wallpaperScheme.primary.toArgb()
        return colorSchemeFromSeed(Color(seedArgb), darkTheme, paletteStyle)
    }
    return colorSchemeFromSeed(seedColor, darkTheme, paletteStyle)
}

@Composable
fun rememberSlideIndexColorScheme(
    seedColor: Color,
    dynamicColor: Boolean,
    paletteStyle: ThemePaletteStyle,
    darkTheme: Boolean = isSystemInDarkTheme(),
): ColorScheme {
    val context = LocalContext.current
    return remember(seedColor, dynamicColor, paletteStyle, darkTheme) {
        slideIndexColorScheme(context, seedColor, dynamicColor, paletteStyle, darkTheme)
    }
}

private fun DynamicScheme.toComposeColorScheme(): ColorScheme = ColorScheme(
    primary = roleColor { primary() },
    onPrimary = roleColor { onPrimary() },
    primaryContainer = roleColor { primaryContainer() },
    onPrimaryContainer = roleColor { onPrimaryContainer() },
    inversePrimary = roleColor { inversePrimary() },
    secondary = roleColor { secondary() },
    onSecondary = roleColor { onSecondary() },
    secondaryContainer = roleColor { secondaryContainer() },
    onSecondaryContainer = roleColor { onSecondaryContainer() },
    tertiary = roleColor { tertiary() },
    onTertiary = roleColor { onTertiary() },
    tertiaryContainer = roleColor { tertiaryContainer() },
    onTertiaryContainer = roleColor { onTertiaryContainer() },
    primaryFixed = roleColor { primaryFixed() },
    onPrimaryFixed = roleColor { onPrimaryFixed() },
    primaryFixedDim = roleColor { primaryFixedDim() },
    onPrimaryFixedVariant = roleColor { onPrimaryFixedVariant() },
    secondaryFixed = roleColor { secondaryFixed() },
    onSecondaryFixed = roleColor { onSecondaryFixed() },
    secondaryFixedDim = roleColor { secondaryFixedDim() },
    onSecondaryFixedVariant = roleColor { onSecondaryFixedVariant() },
    tertiaryFixed = roleColor { tertiaryFixed() },
    onTertiaryFixed = roleColor { onTertiaryFixed() },
    tertiaryFixedDim = roleColor { tertiaryFixedDim() },
    onTertiaryFixedVariant = roleColor { onTertiaryFixedVariant() },
    background = roleColor { background() },
    onBackground = roleColor { onBackground() },
    surface = roleColor { surface() },
    onSurface = roleColor { onSurface() },
    surfaceVariant = roleColor { surfaceVariant() },
    onSurfaceVariant = roleColor { onSurfaceVariant() },
    surfaceTint = roleColor { primary() },
    inverseSurface = roleColor { inverseSurface() },
    inverseOnSurface = roleColor { inverseOnSurface() },
    error = roleColor { error() },
    onError = roleColor { onError() },
    errorContainer = roleColor { errorContainer() },
    onErrorContainer = roleColor { onErrorContainer() },
    outline = roleColor { outline() },
    outlineVariant = roleColor { outlineVariant() },
    scrim = roleColor { scrim() },
    surfaceBright = roleColor { surfaceBright() },
    surfaceDim = roleColor { surfaceDim() },
    surfaceContainer = roleColor { surfaceContainer() },
    surfaceContainerHigh = roleColor { surfaceContainerHigh() },
    surfaceContainerHighest = roleColor { surfaceContainerHighest() },
    surfaceContainerLow = roleColor { surfaceContainerLow() },
    surfaceContainerLowest = roleColor { surfaceContainerLowest() },
)

private fun DynamicScheme.roleColor(selector: MaterialDynamicColors.() -> DynamicColor): Color =
    Color(selector(materialDynamicColors).getArgb(this))
