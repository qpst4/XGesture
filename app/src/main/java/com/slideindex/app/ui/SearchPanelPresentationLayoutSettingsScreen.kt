@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slideindex.app.R
import com.slideindex.app.overlay.SystemWallpaperBlurHelper
import com.slideindex.app.overlay.WallpaperPermissionLauncher
import com.slideindex.app.overlay.pickresult.SearchEngineIcon
import com.slideindex.app.overlay.pickresult.searchIconSizeForColumns
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.SearchEngineConfig
import com.slideindex.app.settings.SearchEngineIconShape
import com.slideindex.app.settings.SearchEngineStore
import com.slideindex.app.settings.SearchPanelAppDisplayStyle
import com.slideindex.app.settings.SearchPanelBackgroundStyle
import com.slideindex.app.settings.SearchPanelBarPosition
import com.slideindex.app.settings.SearchPanelEnterAction
import com.slideindex.app.settings.SearchPanelInputBehavior
import com.slideindex.app.settings.SearchPanelListOrder
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingDropdownRow
import com.slideindex.app.ui.settings.components.SettingSwitchRow
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.SettingsSliderRow
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import kotlin.math.roundToInt

@Composable
fun SearchPanelPresentationLayoutSettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onSetSearchPanelBarPosition: (SearchPanelBarPosition) -> Unit,
    onSetSearchPanelListOrder: (SearchPanelListOrder) -> Unit,
    onSetSearchPanelAppDisplayStyle: (SearchPanelAppDisplayStyle) -> Unit,
    onSetSearchEngineGridColumns: (Int) -> Unit,
    onSetSearchEngineGridRows: (Int) -> Unit,
    onSetSearchEngineShowLabels: (Boolean) -> Unit,
    onSetSearchEngineIconShape: (SearchEngineIconShape) -> Unit,
    onSetSearchPanelBackgroundStyle: (Int) -> Unit,
    onSetSearchPanelBlurRadiusDp: (Int) -> Unit,
    onSetSearchPanelDimPercent: (Int) -> Unit,
    onSetSearchPanelInputBehavior: (SearchPanelInputBehavior) -> Unit,
    onSetSearchPanelEnterAction: (SearchPanelEnterAction) -> Unit,
) {
    val context = LocalContext.current
    val barPositions = SearchPanelBarPosition.entries
    val listOrders = SearchPanelListOrder.entries
    val appDisplayStyles = SearchPanelAppDisplayStyle.entries
    val iconShapes = listOf(
        SearchEngineIconShape.DEFAULT,
        SearchEngineIconShape.CIRCLE,
        SearchEngineIconShape.SQUIRCLE,
        SearchEngineIconShape.SQUARE,
    )
    val enginePreview = remember(settings.searchEngines) {
        SearchEngineStore.textPickPanelEngines(settings.searchEngines)
    }
    val inputBehaviorEntries = SearchPanelInputBehavior.entries
    val enterActionEntries = SearchPanelEnterAction.entries
    val backgroundStyles = listOf(
        SearchPanelBackgroundStyle.BLUR,
        SearchPanelBackgroundStyle.WALLPAPER_BLUR,
        SearchPanelBackgroundStyle.BLACK,
    )
    val backgroundSectionTitle = stringResource(R.string.honeycomb_display_section_background)
    val engineSectionTitle = stringResource(R.string.search_panel_settings_section_engines)
    val behaviorSectionTitle = stringResource(R.string.search_panel_settings_section_behavior)

    // 默认引擎已挪到「搜索面板 → 搜索引擎」区，这里只在回车键动作会用到引擎时提示当前引擎。
    val enterActionEngineName = if (settings.searchPanelEnterAction == SearchPanelEnterAction.SEARCH_ENGINE) {
        val resolved = SearchEngineStore.findTextEngineById(
            settings.searchEngines,
            settings.searchPanelDefaultEngineId,
        ) ?: SearchEngineStore.textPickPanelEngines(settings.searchEngines).firstOrNull()
        resolved?.name
    } else {
        null
    }

    fun ensureWallpaperPermission() {
        SystemWallpaperBlurHelper.requestWallpaperPermission(context)
    }

    SettingsScreenScaffold(
        title = stringResource(R.string.search_panel_settings_section_layout),
        pageHint = stringResource(R.string.search_panel_presentation_layout_entry_desc),
        onBack = onBack,
    ) {
        groupedCardItems(
            keyPrefix = "search_panel_layout",
            items = buildList {
                add(
                    settingsCardScopeItem("bar-position") {
                        SettingDropdownRow(
                            title = stringResource(R.string.search_panel_bar_position_title),
                            items = barPositions.map { searchPanelBarPositionLabel(it) },
                            selectedIndex = barPositions.indexOf(settings.searchPanelBarPosition).coerceAtLeast(0),
                            onSelectedIndexChange = { onSetSearchPanelBarPosition(barPositions[it]) },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("list-order") {
                        SettingDropdownRow(
                            title = stringResource(R.string.search_panel_list_order_title),
                            items = listOrders.map { searchPanelListOrderLabel(it) },
                            selectedIndex = listOrders.indexOf(settings.searchPanelListOrder).coerceAtLeast(0),
                            onSelectedIndexChange = { onSetSearchPanelListOrder(listOrders[it]) },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("app-display-style") {
                        SettingDropdownRow(
                            title = stringResource(R.string.search_panel_app_display_style_title),
                            items = appDisplayStyles.map { searchPanelAppDisplayStyleLabel(it) },
                            selectedIndex = appDisplayStyles.indexOf(settings.searchPanelAppDisplayStyle).coerceAtLeast(0),
                            onSelectedIndexChange = { onSetSearchPanelAppDisplayStyle(appDisplayStyles[it]) },
                        )
                    },
                )
            },
        )

        settingsLazySmallTitle(
            key = "engine_section",
            title = engineSectionTitle,
        )
        if (enginePreview.isNotEmpty()) {
            item(key = "search_panel_engine_preview") {
                Surface(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 4.dp),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    SearchEngineDockPreview(
                        engines = enginePreview,
                        columns = settings.searchEngineGridColumns,
                        rows = settings.searchEngineGridRows,
                        showLabels = settings.searchEngineShowLabels,
                        iconShape = settings.searchEngineIconShape,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }
        }
        groupedCardItems(
            keyPrefix = "search_panel_engine",
            items = buildList {
                add(
                    settingsCardScopeItem("icon-shape") {
                        SettingDropdownRow(
                            title = stringResource(R.string.fv_ring_launcher_icon_shape_title),
                            subtitle = stringResource(R.string.search_panel_engine_layout_shared_hint),
                            items = iconShapes.map { searchEngineIconShapeLabel(it) },
                            selectedIndex = iconShapes.indexOf(settings.searchEngineIconShape).coerceAtLeast(0),
                            onSelectedIndexChange = { onSetSearchEngineIconShape(iconShapes[it]) },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("grid-rows") {
                        SettingsSliderRow(
                            title = stringResource(R.string.search_engine_grid_rows),
                            value = settings.searchEngineGridRows.toFloat(),
                            valueRange = 1f..4f,
                            steps = 2,
                            enabled = true,
                            label = stringResource(
                                R.string.quick_launcher_grid_rows_label,
                                settings.searchEngineGridRows,
                            ),
                            onValueChange = { onSetSearchEngineGridRows(it.roundToInt()) },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("grid-columns") {
                        SettingsSliderRow(
                            title = stringResource(R.string.search_engine_grid_columns),
                            value = settings.searchEngineGridColumns.toFloat(),
                            valueRange = 3f..8f,
                            steps = 4,
                            enabled = true,
                            label = stringResource(
                                R.string.quick_launcher_grid_columns_label,
                                settings.searchEngineGridColumns,
                            ),
                            onValueChange = { onSetSearchEngineGridColumns(it.roundToInt()) },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("show-labels") {
                        SettingSwitchRow(
                            title = stringResource(R.string.search_engine_show_labels),
                            subtitle = stringResource(R.string.search_engine_show_labels_desc),
                            checked = settings.searchEngineShowLabels,
                            enabled = true,
                            onCheckedChange = onSetSearchEngineShowLabels,
                        )
                    },
                )
            },
        )

        settingsLazySmallTitle(
            key = "background_section",
            title = backgroundSectionTitle,
        )
        groupedCardItems(
            keyPrefix = "search_panel_appearance",
            items = buildList {
                add(
                    settingsCardScopeItem("background-style") {
                        SettingDropdownRow(
                            title = stringResource(R.string.honeycomb_display_section_background),
                            items = listOf(
                                stringResource(R.string.honeycomb_background_blur),
                                stringResource(R.string.honeycomb_background_wallpaper_blur),
                                stringResource(R.string.honeycomb_background_black),
                            ),
                            selectedIndex = backgroundStyles.indexOf(settings.searchPanelBackgroundStyle).coerceAtLeast(0),
                            onSelectedIndexChange = {
                                val style = backgroundStyles[it]
                                onSetSearchPanelBackgroundStyle(style)
                                if (style == SearchPanelBackgroundStyle.WALLPAPER_BLUR &&
                                    !SystemWallpaperBlurHelper.hasWallpaperAccessPermission(context)
                                ) {
                                    ensureWallpaperPermission()
                                }
                            },
                        )
                    },
                )
                if (settings.searchPanelBackgroundStyle == SearchPanelBackgroundStyle.BLUR ||
                    settings.searchPanelBackgroundStyle == SearchPanelBackgroundStyle.WALLPAPER_BLUR
                ) {
                    add(
                        settingsCardScopeItem("blur-strength") {
                            SettingsSliderRow(
                                title = stringResource(R.string.honeycomb_blur_strength),
                                value = settings.searchPanelBlurRadiusDp.toFloat(),
                                valueRange = AppSettings.SEARCH_PANEL_BLUR_RADIUS_MIN_DP.toFloat()..
                                    AppSettings.SEARCH_PANEL_BLUR_RADIUS_MAX_DP.toFloat(),
                                steps = 16,
                                enabled = true,
                                label = stringResource(
                                    R.string.corner_gesture_zone_dp_value,
                                    settings.searchPanelBlurRadiusDp,
                                ),
                                onValueChange = { onSetSearchPanelBlurRadiusDp(it.roundToInt()) },
                            )
                        },
                    )
                }
                add(
                    settingsCardScopeItem("dim-percent") {
                        SettingsSliderRow(
                            title = stringResource(R.string.honeycomb_dim_percent),
                            value = settings.searchPanelDimPercent.toFloat(),
                            valueRange = AppSettings.SEARCH_PANEL_DIM_MIN_PERCENT.toFloat()..
                                AppSettings.SEARCH_PANEL_DIM_MAX_PERCENT.toFloat(),
                            steps = 12,
                            enabled = true,
                            label = stringResource(
                                R.string.floating_pointer_percent_value,
                                settings.searchPanelDimPercent,
                            ),
                            onValueChange = { onSetSearchPanelDimPercent(it.roundToInt()) },
                        )
                    },
                )
            },
        )

        settingsLazySmallTitle(
            key = "behavior_section",
            title = behaviorSectionTitle,
        )
        groupedCardItems(
            keyPrefix = "search_panel_behavior",
            items = buildList {
                add(
                    settingsCardScopeItem("input-behavior") {
                        SettingDropdownRow(
                            title = stringResource(R.string.search_panel_input_behavior_title),
                            items = inputBehaviorEntries.map { searchPanelInputBehaviorLabel(it) },
                            selectedIndex = inputBehaviorEntries.indexOf(settings.searchPanelInputBehavior)
                                .coerceAtLeast(0),
                            onSelectedIndexChange = { onSetSearchPanelInputBehavior(inputBehaviorEntries[it]) },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("enter-action") {
                        SettingDropdownRow(
                            title = stringResource(R.string.search_panel_enter_action_title),
                            subtitle = enterActionEngineName?.let {
                                stringResource(R.string.search_panel_enter_action_engine_subtitle, it)
                            },
                            items = enterActionEntries.map { searchPanelEnterActionLabel(it) },
                            selectedIndex = enterActionEntries.indexOf(settings.searchPanelEnterAction)
                                .coerceAtLeast(0),
                            onSelectedIndexChange = { onSetSearchPanelEnterAction(enterActionEntries[it]) },
                        )
                    },
                )
            },
        )
    }
}

@Composable
private fun searchEngineIconShapeLabel(shape: SearchEngineIconShape): String = when (shape) {
    SearchEngineIconShape.DEFAULT -> stringResource(R.string.quick_launcher_icon_shape_default)
    SearchEngineIconShape.CIRCLE -> stringResource(R.string.fv_icon_shape_circle)
    SearchEngineIconShape.SQUIRCLE -> stringResource(R.string.fv_icon_shape_squircle)
    SearchEngineIconShape.SQUARE -> stringResource(R.string.fv_icon_shape_square)
}

/**
 * 引擎区只读预览：直接按 dock 的图标尺寸/标签/形状口径画一页，
 * 让"行数/列数/显示名称/图标形状"改完立刻有反馈。
 */
@Composable
private fun SearchEngineDockPreview(
    engines: List<SearchEngineConfig>,
    columns: Int,
    rows: Int,
    showLabels: Boolean,
    iconShape: SearchEngineIconShape,
    modifier: Modifier = Modifier,
) {
    if (engines.isEmpty()) return
    val columnCount = columns.coerceIn(3, 8)
    val rowCount = rows.coerceIn(1, 4)
    val iconSize = searchIconSizeForColumns(columnCount)
    val page = engines.take(columnCount * rowCount)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        page.chunked(columnCount).forEach { rowEngines ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                rowEngines.forEach { engine ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        SearchEngineIcon(
                            engine = engine,
                            modifier = Modifier.size(iconSize),
                            iconShape = iconShape,
                        )
                        if (showLabels) {
                            Text(
                                text = engine.name,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            )
                        }
                    }
                }
                repeat(columnCount - rowEngines.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
