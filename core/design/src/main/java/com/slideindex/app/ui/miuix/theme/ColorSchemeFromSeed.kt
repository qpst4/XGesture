package com.slideindex.app.ui.miuix.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.dynamiccolor.DynamicScheme
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.SchemeContent
import com.materialkolor.scheme.SchemeExpressive
import com.materialkolor.scheme.SchemeFidelity
import com.materialkolor.scheme.SchemeFruitSalad
import com.materialkolor.scheme.SchemeMonochrome
import com.materialkolor.scheme.SchemeNeutral
import com.materialkolor.scheme.SchemeRainbow
import com.materialkolor.scheme.SchemeTonalSpot
import com.materialkolor.scheme.SchemeVibrant
import com.slideindex.app.settings.AppColorSpec
import com.slideindex.app.settings.ThemePaletteStyle

/**
 * 直接用 `material-color-utilities` 从种子色生成 Material3 [ColorScheme]。
 *
 * 这一层以前由 `com.materialkolor:material-kolor` 的 `dynamicColorScheme()` 提供；但该库
 * 最新版仍停在 5.0.1（按 utilities 5.0.1 编译，`DynamicScheme` 在 `com.materialkolor.scheme`），
 * 而 miuix 0.9.5 把 utilities 顶到 6.0.0（`DynamicScheme` 搬到 `com.materialkolor.dynamiccolor`），
 * 结果是运行时 `ClassNotFoundException: com.materialkolor.scheme.DynamicScheme$Platform`。
 *
 * 这里只保留我们真正用到的能力：种子 → [DynamicScheme] → Material3 角色，语义与
 * material-kolor 的 TonalSpot/Fidelity/… + SpecVersion 组合一致。
 */
internal fun colorSchemeFromSeed(
    seedArgb: Int,
    dark: Boolean,
    paletteStyle: ThemePaletteStyle,
    colorSpec: AppColorSpec,
): ColorScheme = dynamicSchemeFromSeed(seedArgb, dark, paletteStyle, colorSpec).toMaterialColorScheme()

private val PhonePlatform: DynamicScheme.Platform = DynamicScheme.Platform.PHONE

/** HCT 中性色对比度为 0 与 material-kolor 的默认值保持一致。 */
private const val DefaultContrastLevel: Double = 0.0

private fun dynamicSchemeFromSeed(
    seedArgb: Int,
    dark: Boolean,
    paletteStyle: ThemePaletteStyle,
    colorSpec: AppColorSpec,
): DynamicScheme {
    val source = Hct.fromInt(seedArgb)
    val spec = colorSpec.toMaterialKolor()
    return when (paletteStyle) {
        ThemePaletteStyle.TONAL_SPOT -> SchemeTonalSpot(source, dark, DefaultContrastLevel, spec, PhonePlatform)
        ThemePaletteStyle.NEUTRAL -> SchemeNeutral(source, dark, DefaultContrastLevel, spec, PhonePlatform)
        ThemePaletteStyle.VIBRANT -> SchemeVibrant(source, dark, DefaultContrastLevel, spec, PhonePlatform)
        ThemePaletteStyle.EXPRESSIVE -> SchemeExpressive(source, dark, DefaultContrastLevel, spec, PhonePlatform)
        ThemePaletteStyle.MONOCHROME -> SchemeMonochrome(source, dark, DefaultContrastLevel, spec, PhonePlatform)
        ThemePaletteStyle.FIDELITY -> SchemeFidelity(source, dark, DefaultContrastLevel, spec, PhonePlatform)
        ThemePaletteStyle.CONTENT -> SchemeContent(source, dark, DefaultContrastLevel, spec, PhonePlatform)
        ThemePaletteStyle.RAINBOW -> SchemeRainbow(source, dark, DefaultContrastLevel, spec, PhonePlatform)
        ThemePaletteStyle.FRUIT_SALAD -> SchemeFruitSalad(source, dark, DefaultContrastLevel, spec, PhonePlatform)
    }
}

private fun DynamicScheme.toMaterialColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = Color(primary),
        onPrimary = Color(onPrimary),
        primaryContainer = Color(primaryContainer),
        onPrimaryContainer = Color(onPrimaryContainer),
        inversePrimary = Color(inversePrimary),
        secondary = Color(secondary),
        onSecondary = Color(onSecondary),
        secondaryContainer = Color(secondaryContainer),
        onSecondaryContainer = Color(onSecondaryContainer),
        tertiary = Color(tertiary),
        onTertiary = Color(onTertiary),
        tertiaryContainer = Color(tertiaryContainer),
        onTertiaryContainer = Color(onTertiaryContainer),
        background = Color(background),
        onBackground = Color(onBackground),
        surface = Color(surface),
        onSurface = Color(onSurface),
        surfaceVariant = Color(surfaceVariant),
        onSurfaceVariant = Color(onSurfaceVariant),
        surfaceTint = Color(surfaceTint),
        surfaceBright = Color(surfaceBright),
        surfaceDim = Color(surfaceDim),
        surfaceContainer = Color(surfaceContainer),
        surfaceContainerHigh = Color(surfaceContainerHigh),
        surfaceContainerHighest = Color(surfaceContainerHighest),
        surfaceContainerLow = Color(surfaceContainerLow),
        surfaceContainerLowest = Color(surfaceContainerLowest),
        inverseSurface = Color(inverseSurface),
        inverseOnSurface = Color(inverseOnSurface),
        error = Color(error),
        onError = Color(onError),
        errorContainer = Color(errorContainer),
        onErrorContainer = Color(onErrorContainer),
        outline = Color(outline),
        outlineVariant = Color(outlineVariant),
        scrim = Color(scrim),
        primaryFixed = Color(primaryFixed),
        primaryFixedDim = Color(primaryFixedDim),
        onPrimaryFixed = Color(onPrimaryFixed),
        onPrimaryFixedVariant = Color(onPrimaryFixedVariant),
        secondaryFixed = Color(secondaryFixed),
        secondaryFixedDim = Color(secondaryFixedDim),
        onSecondaryFixed = Color(onSecondaryFixed),
        onSecondaryFixedVariant = Color(onSecondaryFixedVariant),
        tertiaryFixed = Color(tertiaryFixed),
        tertiaryFixedDim = Color(tertiaryFixedDim),
        onTertiaryFixed = Color(onTertiaryFixed),
        onTertiaryFixedVariant = Color(onTertiaryFixedVariant),
    )
}
