package com.slideindex.app.ui.miuix.theme

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.dynamiccolor.ColorSpec
import com.slideindex.app.design.AppThemeSpec
import com.slideindex.app.settings.AppColorSpec
import com.slideindex.app.settings.ThemePaletteStyle
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle as MiuixPaletteStyle

object MiuixSeedResolver {
    @SuppressLint("NewApi")
    private fun wallpaperAccent(context: Context, dark: Boolean): Int? {
        val scheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        return scheme.primary.toArgb()
    }

    fun customSeed(context: Context, spec: AppThemeSpec): Int =
        if (spec.dynamicColorEnabled) {
            wallpaperAccent(context, spec.dark) ?: spec.themeColorArgb
        } else {
            spec.themeColorArgb
        }

    fun materialScheme(seed: Int, spec: AppThemeSpec): ColorScheme =
        dynamicColorScheme(
            seedColor = Color(seed),
            isDark = spec.dark,
            style = spec.paletteStyle().toMaterialKolor(),
            specVersion = spec.effectiveColorSpec().toMaterialKolor(),
        )
}

private fun AppThemeSpec.paletteStyle(): ThemePaletteStyle =
    ThemePaletteStyle.fromId(paletteStyleId)

private fun AppThemeSpec.effectiveColorSpec(): AppColorSpec {
    val spec = AppColorSpec.fromId(colorSpecId)
    val palette = ThemePaletteStyle.fromId(paletteStyleId)
    return if (palette.supportsMiuixSpec2025()) spec else AppColorSpec.SPEC_2021
}

fun ThemePaletteStyle.toMaterialKolor(): PaletteStyle = when (this) {
    ThemePaletteStyle.TONAL_SPOT -> PaletteStyle.TonalSpot
    ThemePaletteStyle.VIBRANT -> PaletteStyle.Vibrant
    ThemePaletteStyle.EXPRESSIVE -> PaletteStyle.Expressive
    ThemePaletteStyle.MONOCHROME -> PaletteStyle.Monochrome
    ThemePaletteStyle.NEUTRAL -> PaletteStyle.Neutral
    ThemePaletteStyle.FIDELITY -> PaletteStyle.Fidelity
    ThemePaletteStyle.CONTENT -> PaletteStyle.Content
    ThemePaletteStyle.RAINBOW -> PaletteStyle.Rainbow
    ThemePaletteStyle.FRUIT_SALAD -> PaletteStyle.FruitSalad
}

fun ThemePaletteStyle.toMiuix(): MiuixPaletteStyle = when (this) {
    ThemePaletteStyle.TONAL_SPOT -> MiuixPaletteStyle.TonalSpot
    ThemePaletteStyle.VIBRANT -> MiuixPaletteStyle.Vibrant
    ThemePaletteStyle.EXPRESSIVE -> MiuixPaletteStyle.Expressive
    ThemePaletteStyle.MONOCHROME -> MiuixPaletteStyle.Monochrome
    ThemePaletteStyle.NEUTRAL -> MiuixPaletteStyle.Neutral
    ThemePaletteStyle.FIDELITY -> MiuixPaletteStyle.Fidelity
    ThemePaletteStyle.CONTENT -> MiuixPaletteStyle.Content
    ThemePaletteStyle.RAINBOW -> MiuixPaletteStyle.Rainbow
    ThemePaletteStyle.FRUIT_SALAD -> MiuixPaletteStyle.FruitSalad
}

fun AppColorSpec.toMaterialKolor(): ColorSpec.SpecVersion = when (this) {
    AppColorSpec.SPEC_2021 -> ColorSpec.SpecVersion.SPEC_2021
    AppColorSpec.SPEC_2025 -> ColorSpec.SpecVersion.SPEC_2025
}

fun AppColorSpec.toMiuix(): ThemeColorSpec = when (this) {
    AppColorSpec.SPEC_2021 -> ThemeColorSpec.Spec2021
    AppColorSpec.SPEC_2025 -> ThemeColorSpec.Spec2025
}

fun ThemePaletteStyle.supportsMiuixSpec2025(): Boolean =
    when (this) {
        ThemePaletteStyle.TONAL_SPOT,
        ThemePaletteStyle.NEUTRAL,
        ThemePaletteStyle.VIBRANT,
        ThemePaletteStyle.EXPRESSIVE,
        ThemePaletteStyle.MONOCHROME,
        ThemePaletteStyle.FIDELITY,
        ThemePaletteStyle.CONTENT,
        ThemePaletteStyle.RAINBOW,
        -> true
        ThemePaletteStyle.FRUIT_SALAD -> false
    }
