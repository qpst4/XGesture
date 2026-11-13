package com.slideindex.app.ui.miuix

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.settings.AppColorSpec
import com.slideindex.app.settings.AppThemeMode
import com.slideindex.app.settings.BottomNavBlurDefaults
import com.slideindex.app.settings.BottomNavMode
import com.slideindex.app.settings.BottomNavStyle
import com.slideindex.app.settings.DarkBackgroundStyle
import com.slideindex.app.settings.ThemePaletteStyle
import com.slideindex.app.settings.TopAppBarBlurStyle
import com.slideindex.app.settings.UiDensityScaleLimits
import com.slideindex.app.ui.HomeLeadingIcons
import com.slideindex.app.ui.miuix.theme.supportsMiuixSpec2025
import com.slideindex.app.ui.miuix.MiuixDropdownPreference
import com.slideindex.app.ui.settings.components.settingsCardItem
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported

@Composable
fun themeAppearanceSettingsCardItems(
    outlinedPreferenceIcons: Boolean = false,
    themeModeId: Int,
    customColorEnabled: Boolean,
    darkBackgroundStyleId: Int,
    dynamicColorEnabled: Boolean,
    themeColorArgb: Int,
    paletteStyleId: Int,
    themeColorSpecId: Int,
    bottomNavStyleId: Int,
    bottomNavModeId: Int,
    bottomNavGlassEnabled: Boolean,
    bottomNavBlurRadiusDp: Float,
    topAppBarBlurStyleId: Int,
    uiDensityScale: Float,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onCustomColorChange: (Boolean) -> Unit,
    onDarkBackgroundStyleChange: (DarkBackgroundStyle) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onThemeColorChange: (Int) -> Unit,
    onPaletteStyleChange: (ThemePaletteStyle) -> Unit,
    onThemeColorSpecChange: (AppColorSpec) -> Unit,
    onBottomNavStyleChange: (BottomNavStyle) -> Unit,
    onBottomNavModeChange: (BottomNavMode) -> Unit,
    onBottomNavGlassEnabledChange: (Boolean) -> Unit,
    onBottomNavBlurRadiusChange: (Float) -> Unit,
    onTopAppBarBlurStyleChange: (TopAppBarBlurStyle) -> Unit,
    onUiDensityScaleChange: (Float) -> Unit,
    onBottomNavBlurPreviewChange: (Float) -> Unit = {},
    onBottomNavBlurPreviewStop: () -> Unit = {},
): List<CardItem> {
    val themeMode = AppThemeMode.fromId(themeModeId)
    val darkBackgroundStyle = DarkBackgroundStyle.fromId(darkBackgroundStyleId)
    val paletteStyle = ThemePaletteStyle.fromId(paletteStyleId)
    val colorSpec = AppColorSpec.fromId(themeColorSpecId)
    val bottomNavStyle = BottomNavStyle.fromId(bottomNavStyleId)
    val bottomNavMode = BottomNavMode.fromId(bottomNavModeId)
    val topAppBarBlurStyle = TopAppBarBlurStyle.fromId(topAppBarBlurStyleId)
    val spec2025Supported = paletteStyle.supportsMiuixSpec2025()
    val effectiveColorSpec = if (spec2025Supported) colorSpec else AppColorSpec.SPEC_2021
    @android.annotation.SuppressLint("LocalContextResourcesRead")
    val resources = LocalContext.current.resources
    var showSeedColorPicker by remember { mutableStateOf(false) }

    MiuixThemeSeedColorDialog(
        show = showSeedColorPicker,
        initialColorArgb = themeColorArgb,
        onDismiss = { showSeedColorPicker = false },
        onConfirm = onThemeColorChange,
    )

    return buildList {
        add(
            settingsCardItem("theme-mode") {
                MiuixDropdownPreference(
                    title = stringResource(R.string.theme_mode),
                    items = AppThemeMode.entries.map { stringResource(it.labelRes()) },
                    selectedIndex = AppThemeMode.entries.indexOf(themeMode).coerceAtLeast(0),
                    onSelectedIndexChange = { onThemeModeChange(AppThemeMode.entries[it]) },
                )
            },
        )
        add(
            settingsCardItem("theme-custom-color") {
                MiuixExpandableSwitchRow(
                    title = stringResource(R.string.theme_custom_color),
                    summary = stringResource(R.string.theme_custom_color_desc),
                    checked = customColorEnabled,
                    onCheckedChange = onCustomColorChange,
                ) {
                    MiuixSwitchRow(
                        title = stringResource(R.string.dynamic_color),
                        summary = stringResource(R.string.dynamic_color_desc),
                        checked = dynamicColorEnabled,
                        onCheckedChange = onDynamicColorChange,
                    )
                    AnimatedVisibility(visible = !dynamicColorEnabled) {
                        BasicComponent(
                            title = stringResource(R.string.theme_seed_color),
                            summary = stringResource(R.string.theme_seed_color_desc),
                            startAction = {
                                ThemePrefIcon(HomeLeadingIcons.themeSeedColor(outlinedPreferenceIcons))
                            },
                            onClick = { showSeedColorPicker = true },
                            endActions = {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color(themeColorArgb)),
                                )
                            },
                        )
                    }
                    val paletteEntries = ThemePaletteStyle.entries
                    MiuixDropdownPreference(
                        title = stringResource(R.string.theme_palette_style),
                        summary = stringResource(R.string.theme_palette_style_desc),
                        items = paletteEntries.map { stringResource(it.labelRes()) },
                        selectedIndex = paletteEntries.indexOf(paletteStyle).coerceAtLeast(0),
                        startAction = { ThemePrefIcon(HomeLeadingIcons.themePalette(outlinedPreferenceIcons)) },
                        onSelectedIndexChange = { index ->
                            onPaletteStyleChange(paletteEntries[index])
                        },
                    )
                    val specEntries = if (spec2025Supported) {
                        AppColorSpec.entries
                    } else {
                        listOf(AppColorSpec.SPEC_2021)
                    }
                    MiuixDropdownPreference(
                        title = stringResource(R.string.theme_color_spec),
                        summary = if (!spec2025Supported) {
                            stringResource(R.string.theme_color_spec_2025_unsupported)
                        } else {
                            null
                        },
                        items = specEntries.map { stringResource(it.labelRes()) },
                        selectedIndex = specEntries.indexOf(effectiveColorSpec).coerceAtLeast(0),
                        enabled = spec2025Supported,
                        startAction = { ThemePrefIcon(HomeLeadingIcons.themeColorSpec(outlinedPreferenceIcons)) },
                        onSelectedIndexChange = { index -> onThemeColorSpecChange(specEntries[index]) },
                    )
                }
            },
        )
        val darkBackgroundEntries = DarkBackgroundStyle.entries
        add(
            settingsCardItem("theme-dark-background") {
                MiuixDropdownPreference(
                    title = stringResource(R.string.theme_dark_background),
                    summary = stringResource(darkBackgroundStyle.labelRes()),
                    items = darkBackgroundEntries.map { stringResource(it.labelRes()) },
                    selectedIndex = darkBackgroundEntries.indexOf(darkBackgroundStyle).coerceAtLeast(0),
                    enabled = !customColorEnabled,
                    onSelectedIndexChange = { index ->
                        onDarkBackgroundStyleChange(darkBackgroundEntries[index])
                    },
                )
            },
        )
        if (isRuntimeShaderSupported()) {
            val topAppBarBlurStyleEntries = TopAppBarBlurStyle.entries
            add(
                settingsCardItem("top-app-bar-blur-style") {
                    MiuixDropdownPreference(
                        title = stringResource(R.string.top_app_bar_blur_style),
                        summary = stringResource(R.string.top_app_bar_blur_style_desc),
                        items = topAppBarBlurStyleEntries.map { stringResource(it.labelRes()) },
                        selectedIndex = topAppBarBlurStyleEntries.indexOf(topAppBarBlurStyle).coerceAtLeast(0),
                        onSelectedIndexChange = { index ->
                            onTopAppBarBlurStyleChange(topAppBarBlurStyleEntries[index])
                        },
                    )
                },
            )
        }
        val bottomNavStyleEntries = BottomNavStyle.entries
        add(
            settingsCardItem("bottom-nav-style") {
                MiuixDropdownPreference(
                    title = stringResource(R.string.bottom_nav_style),
                    items = bottomNavStyleEntries.map { stringResource(it.labelRes()) },
                    selectedIndex = bottomNavStyleEntries.indexOf(bottomNavStyle).coerceAtLeast(0),
                    onSelectedIndexChange = { index -> onBottomNavStyleChange(bottomNavStyleEntries[index]) },
                )
            },
        )
        val bottomNavModeEntries = BottomNavMode.entries
        add(
            settingsCardItem("bottom-nav-mode") {
                MiuixDropdownPreference(
                    title = stringResource(R.string.bottom_nav_mode),
                    items = bottomNavModeEntries.map { stringResource(it.labelRes()) },
                    selectedIndex = bottomNavModeEntries.indexOf(bottomNavMode).coerceAtLeast(0),
                    onSelectedIndexChange = { index -> onBottomNavModeChange(bottomNavModeEntries[index]) },
                )
            },
        )
        add(
            settingsCardItem("bottom-nav-glass") {
                MiuixExpandableSwitchRow(
                    title = stringResource(R.string.bottom_nav_glass_enabled),
                    summary = stringResource(R.string.bottom_nav_glass_enabled_desc),
                    checked = bottomNavGlassEnabled,
                    onCheckedChange = onBottomNavGlassEnabledChange,
                ) {
                    MiuixSliderRow(
                        title = stringResource(R.string.bottom_nav_blur_radius),
                        value = bottomNavBlurRadiusDp,
                        valueRange = BottomNavBlurDefaults.MIN_RADIUS_DP..BottomNavBlurDefaults.MAX_RADIUS_DP,
                        steps = (BottomNavBlurDefaults.MAX_RADIUS_DP - BottomNavBlurDefaults.MIN_RADIUS_DP).roundToInt(),
                        enabled = bottomNavGlassEnabled,
                        formatLabel = { "${it.roundToInt()} dp" },
                        commitOnFinish = true,
                        triggersLayoutPreview = true,
                        onLayoutPreviewValueChange = onBottomNavBlurPreviewChange,
                        onLayoutPreviewStop = onBottomNavBlurPreviewStop,
                        onValueChange = onBottomNavBlurRadiusChange,
                    )
                }
            },
        )
        add(
            settingsCardItem("theme-ui-density-scale") {
                MiuixSliderRow(
                    title = stringResource(R.string.theme_ui_density_scale),
                    summary = stringResource(R.string.theme_ui_density_scale_desc),
                    value = UiDensityScaleLimits.scaleToPercent(uiDensityScale),
                    valueRange = UiDensityScaleLimits.MIN_PERCENT.toFloat()..UiDensityScaleLimits.MAX_PERCENT.toFloat(),
                    keyPoints = UiDensityScaleLimits.KEY_POINT_PERCENTS,
                    commitOnFinish = true,
                    formatLabel = { percent ->
                        resources.getString(
                            R.string.theme_ui_density_scale_value,
                            percent.roundToInt(),
                        )
                    },
                    onValueChange = { percent ->
                        onUiDensityScaleChange(UiDensityScaleLimits.percentToScale(percent))
                    },
                )
            },
        )
    }
}

@Composable
private fun ThemePrefIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}
