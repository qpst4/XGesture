package com.slideindex.app.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.slideindex.app.overlay.pickresult.PickResultFlatPrefs
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.settings.OverlayBlurMode
import com.slideindex.app.ui.miuix.MiuixListSettingsCard
import com.slideindex.app.ui.settings.components.SettingDropdownRow
import com.slideindex.app.ui.settings.components.SettingSwitchNavigationRow
import com.slideindex.app.ui.settings.components.SettingSwitchRow
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.SettingsSliderRow
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.SmallTitle

/**
 * 悬浮窗材质二级设置页。
 *
 * 聚合管理：
 * 1. 全屏背景与遮罩（管辖轮盘、圆环、蜂窝、全息等全屏呼出物背后的虚化与暗化）；
 * 2. 悬浮面板材质（管辖悬浮卡片本身的毛玻璃厚度、不透明度、圆角及高级光影独立微调入口）；
 * 3. 悬浮面板模式（管辖搜索页、扩展面板、分词浮层的独立三态特例覆盖）。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OverlayMaterialSettingsScreen(
    onBack: () -> Unit,
    // 全屏背景与遮罩
    backdropBlurEnabled: Boolean,
    onBackdropBlurEnabledChange: (Boolean) -> Unit,
    backdropBlurRadiusDp: Int,
    onBackdropBlurRadiusChange: (Int) -> Unit,
    backdropDimPercent: Int,
    onBackdropDimPercentChange: (Int) -> Unit,
    // 悬浮面板材质
    overlayBlurEnabled: Boolean,
    onOverlayBlurEnabledChange: (Boolean) -> Unit,
    overlayBlurRadiusDp: Int,
    onOverlayBlurRadiusChange: (Int) -> Unit,
    overlayOpacityPercent: Int,
    onOverlayOpacityPercentChange: (Int) -> Unit,
    overlayCornerRadiusDp: Int,
    onOverlayCornerRadiusChange: (Int) -> Unit,
    overlayAdvancedShimmerEnabled: Boolean,
    onOverlayAdvancedShimmerEnabledChange: (Boolean) -> Unit,
    onOpenAdvancedShimmerSettings: () -> Unit,
    // 悬浮面板模式
    searchPanelBlurModeId: Int,
    onSearchPanelBlurModeChange: (OverlayBlurMode) -> Unit,
    expandPanelBlurModeId: Int,
    onExpandPanelBlurModeChange: (OverlayBlurMode) -> Unit,
    pickResultBlurModeId: Int,
    onPickResultBlurModeChange: (OverlayBlurMode) -> Unit,
    widgetPanelBlurModeId: Int,
    onWidgetPanelBlurModeChange: (OverlayBlurMode) -> Unit,
) {
    // 顶层提取所有文本资源，不在 buildList 内部触发 @Composable
    val pageTitle = stringResource(R.string.overlay_material_settings_title)
    val pageHint = stringResource(R.string.overlay_material_settings_desc)
    val context = LocalContext.current
    // 取词面板平铺（反馈 #401）：独立偏好，改完立即生效。
    var pickResultFlat by remember { mutableStateOf(PickResultFlatPrefs.isEnabled(context)) }
    val pickResultFlatTitle = stringResource(R.string.pick_result_flat_title)
    val pickResultFlatDesc = stringResource(R.string.pick_result_flat_desc)

    val backdropSectionTitle = stringResource(R.string.settings_section_backdrop_visual)
    val backdropBlurTitle = stringResource(R.string.backdrop_blur_enabled)
    val backdropBlurDesc = stringResource(R.string.backdrop_blur_enabled_desc)
    val backdropBlurRadiusTitle = stringResource(R.string.backdrop_blur_radius_title)
    val backdropDimTitle = stringResource(R.string.backdrop_dim_title)

    val materialSectionTitle = stringResource(R.string.settings_section_overlay_material)
    val overlayBlurTitle = stringResource(R.string.overlay_blur_enabled)
    val overlayBlurDesc = stringResource(R.string.overlay_blur_enabled_desc)
    val overlayBlurRadiusTitle = stringResource(R.string.overlay_blur_radius_title)
    val overlayOpacityTitle = stringResource(R.string.overlay_opacity_title)
    val overlayCornerRadiusTitle = stringResource(R.string.overlay_corner_radius_title)
    val overlayAdvancedShimmerToggle = stringResource(R.string.overlay_advanced_shimmer_toggle)
    val overlayAdvancedShimmerDesc = stringResource(R.string.overlay_advanced_shimmer_desc)

    val panelBlurModeSectionTitle = stringResource(R.string.settings_section_panel_blur_mode)
    val searchPanelBlurModeTitle = stringResource(R.string.search_panel_blur_mode_title)
    val searchPanelBlurModeDesc = stringResource(R.string.search_panel_blur_mode_desc)
    val expandPanelBlurModeTitle = stringResource(R.string.expand_panel_blur_mode_title)
    val expandPanelBlurModeDesc = stringResource(R.string.expand_panel_blur_mode_desc)
    val pickResultBlurModeTitle = stringResource(R.string.pick_result_blur_mode_title)
    val pickResultBlurModeDesc = stringResource(R.string.pick_result_blur_mode_desc)
    val widgetPanelBlurModeTitle = stringResource(R.string.widget_panel_blur_mode_title)
    val widgetPanelBlurModeDesc = stringResource(R.string.widget_panel_blur_mode_desc)
    val blurModeLabels = listOf(
        stringResource(R.string.overlay_blur_mode_disabled),
        stringResource(R.string.overlay_blur_mode_all),
        stringResource(R.string.overlay_blur_mode_backdrop_only),
        stringResource(R.string.overlay_blur_mode_panel_only),
    )

    SettingsScreenScaffold(
        title = pageTitle,
        pageHint = pageHint,
        onBack = onBack,
    ) {
        // 卡片 1：全屏背景与遮罩
        item(key = "backdrop_visual_section") {
            SmallTitle(
                text = backdropSectionTitle,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        MiuixListSettingsCard(
            keyPrefix = "overlay-material-backdrop-visual",
            items = buildList {
                add(
                    settingsCardScopeItem("backdrop-blur") {
                        SettingSwitchRow(
                            title = backdropBlurTitle,
                            subtitle = backdropBlurDesc,
                            checked = backdropBlurEnabled,
                            enabled = true,
                            onCheckedChange = onBackdropBlurEnabledChange,
                        )
                    }
                )
                if (backdropBlurEnabled) {
                    add(
                        settingsCardScopeItem("backdrop-blur-radius") {
                            SettingsSliderRow(
                                title = backdropBlurRadiusTitle,
                                value = backdropBlurRadiusDp.toFloat(),
                                valueRange = 0f..100f,
                                enabled = true,
                                label = "${backdropBlurRadiusDp}dp",
                                formatLabel = { "${it.roundToInt()}dp" },
                                onValueChange = { onBackdropBlurRadiusChange(it.roundToInt()) },
                            )
                        }
                    )
                }
                add(
                    settingsCardScopeItem("backdrop-dim") {
                        SettingsSliderRow(
                            title = backdropDimTitle,
                            value = backdropDimPercent.toFloat(),
                            valueRange = 0f..100f,
                            enabled = true,
                            label = "${backdropDimPercent}%",
                            formatLabel = { "${it.roundToInt()}%" },
                            onValueChange = { onBackdropDimPercentChange(it.roundToInt()) },
                        )
                    }
                )
            },
        )

        // 卡片 2：悬浮面板材质
        item(key = "overlay_material_section") {
            SmallTitle(
                text = materialSectionTitle,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        MiuixListSettingsCard(
            keyPrefix = "overlay-material-panel-visual",
            items = buildList {
                add(
                    settingsCardScopeItem("overlay-blur") {
                        SettingSwitchRow(
                            title = overlayBlurTitle,
                            subtitle = overlayBlurDesc,
                            checked = overlayBlurEnabled,
                            enabled = true,
                            onCheckedChange = onOverlayBlurEnabledChange,
                        )
                    }
                )
                if (overlayBlurEnabled) {
                    add(
                        settingsCardScopeItem("overlay-blur-radius") {
                            SettingsSliderRow(
                                title = overlayBlurRadiusTitle,
                                value = overlayBlurRadiusDp.toFloat(),
                                valueRange = 0f..100f,
                                enabled = true,
                                label = "${overlayBlurRadiusDp}dp",
                                formatLabel = { "${it.roundToInt()}dp" },
                                onValueChange = { onOverlayBlurRadiusChange(it.roundToInt()) },
                            )
                        }
                    )
                    add(
                        settingsCardScopeItem("overlay-opacity") {
                            SettingsSliderRow(
                                title = overlayOpacityTitle,
                                value = overlayOpacityPercent.toFloat(),
                                valueRange = 0f..100f,
                                enabled = true,
                                label = "${overlayOpacityPercent}%",
                                formatLabel = { "${it.roundToInt()}%" },
                                onValueChange = { onOverlayOpacityPercentChange(it.roundToInt()) },
                            )
                        }
                    )
                    // 高级光影微调：手势动画同款 SettingSwitchNavigationRow
                    add(
                        settingsCardScopeItem("overlay-advanced-shimmer") {
                            SettingSwitchNavigationRow(
                                title = overlayAdvancedShimmerToggle,
                                subtitle = overlayAdvancedShimmerDesc,
                                checked = overlayAdvancedShimmerEnabled,
                                enabled = true,
                                onCheckedChange = onOverlayAdvancedShimmerEnabledChange,
                                onNavigate = onOpenAdvancedShimmerSettings,
                            )
                        }
                    )
                }
                add(
                    settingsCardScopeItem("overlay-corner-radius") {
                        SettingsSliderRow(
                            title = overlayCornerRadiusTitle,
                            value = overlayCornerRadiusDp.toFloat(),
                            valueRange = 12f..28f,
                            enabled = true,
                            label = "${overlayCornerRadiusDp}dp",
                            formatLabel = { "${it.roundToInt()}dp" },
                            onValueChange = { onOverlayCornerRadiusChange(it.roundToInt()) },
                        )
                    }
                )
            },
        )

        // 卡片 3：悬浮面板模式
        item(key = "panel_blur_mode_section") {
            SmallTitle(
                text = panelBlurModeSectionTitle,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        MiuixListSettingsCard(
            keyPrefix = "overlay-material-panel-blur-mode",
            items = buildList {
                val blurModeOptions = listOf(
                    OverlayBlurMode.DISABLED,
                    OverlayBlurMode.ALL,
                    OverlayBlurMode.BACKDROP_ONLY,
                    OverlayBlurMode.PANEL_ONLY,
                )
                // 1. 搜索页背景模糊
                val currentSearchMode = OverlayBlurMode.fromId(searchPanelBlurModeId)
                val selectedSearchIndex = blurModeOptions.indexOf(currentSearchMode).coerceAtLeast(0)
                add(
                    settingsCardScopeItem("search-panel-blur-mode") {
                        SettingDropdownRow(
                            title = searchPanelBlurModeTitle,
                            subtitle = searchPanelBlurModeDesc,
                            items = blurModeLabels,
                            selectedIndex = selectedSearchIndex,
                            onSelectedIndexChange = { index ->
                                onSearchPanelBlurModeChange(blurModeOptions[index])
                            },
                        )
                    }
                )

                // 2. 扩展面板模糊
                val currentExpandMode = OverlayBlurMode.fromId(expandPanelBlurModeId)
                val selectedExpandIndex = blurModeOptions.indexOf(currentExpandMode).coerceAtLeast(0)
                add(
                    settingsCardScopeItem("expand-panel-blur-mode") {
                        SettingDropdownRow(
                            title = expandPanelBlurModeTitle,
                            subtitle = expandPanelBlurModeDesc,
                            items = blurModeLabels,
                            selectedIndex = selectedExpandIndex,
                            onSelectedIndexChange = { index ->
                                onExpandPanelBlurModeChange(blurModeOptions[index])
                            },
                        )
                    }
                )

                // 3. 分词浮层模糊
                val currentPickMode = OverlayBlurMode.fromId(pickResultBlurModeId)
                val selectedPickIndex = blurModeOptions.indexOf(currentPickMode).coerceAtLeast(0)
                add(
                    settingsCardScopeItem("pick-result-blur-mode") {
                        SettingDropdownRow(
                            title = pickResultBlurModeTitle,
                            subtitle = pickResultBlurModeDesc,
                            items = blurModeLabels,
                            selectedIndex = selectedPickIndex,
                            onSelectedIndexChange = { index ->
                                onPickResultBlurModeChange(blurModeOptions[index])
                            },
                        )
                    }
                )
                // 取词面板平铺（反馈 #401）：去掉文字区/图片区的卡片底板与模糊，只留细线分隔。
                add(
                    settingsCardScopeItem("pick-result-flat") {
                        SettingSwitchRow(
                            title = pickResultFlatTitle,
                            subtitle = pickResultFlatDesc,
                            checked = pickResultFlat,
                            enabled = true,
                            onCheckedChange = { value ->
                                pickResultFlat = value
                                PickResultFlatPrefs.setEnabled(context, value)
                            },
                        )
                    }
                )

                // 4. 小组件面板模糊
                val currentWidgetMode = OverlayBlurMode.fromId(widgetPanelBlurModeId)
                val selectedWidgetIndex = blurModeOptions.indexOf(currentWidgetMode).coerceAtLeast(0)
                add(
                    settingsCardScopeItem("widget-panel-blur-mode") {
                        SettingDropdownRow(
                            title = widgetPanelBlurModeTitle,
                            subtitle = widgetPanelBlurModeDesc,
                            items = blurModeLabels,
                            selectedIndex = selectedWidgetIndex,
                            onSelectedIndexChange = { index ->
                                onWidgetPanelBlurModeChange(blurModeOptions[index])
                            },
                        )
                    }
                )
            },
        )
    }
}
