package com.slideindex.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.PickPanelSlideAnimationDefaults
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.LazySettingsItem
import com.slideindex.app.ui.settings.components.SettingExpandableSwitchRow
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FloatBallPickPanelLayoutBehaviorSettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onPickPanelStyleChange: (com.slideindex.app.settings.PickResultPanelStyle) -> Unit,
    onPickPanelPlacementChange: (com.slideindex.app.settings.PickResultPanelPlacement) -> Unit,
    onPickSearchGridDefaultStateChange: (com.slideindex.app.settings.PickResultSearchGridDefaultState) -> Unit,
    onPickTextModeDefaultChange: (com.slideindex.app.settings.PickResultTextModeDefault) -> Unit,
    onPickWordTapGranularityChange: (com.slideindex.app.settings.PickResultWordTapGranularity) -> Unit,
    onPickTextFirstPanelChange: (Boolean) -> Unit,
    onPickAutoSelectAllChange: (Boolean) -> Unit,
    onPickCopyDismissPanelChange: (Boolean) -> Unit,
    onPickCopyButtonPositionChange: (com.slideindex.app.settings.PickResultCopyButtonPosition) -> Unit,
    onPickImageToolbarPositionChange: (com.slideindex.app.settings.PickResultImageToolbarPosition) -> Unit,
    onPickIconBarEnabledChange: (Boolean) -> Unit,
    onPickBarTextOrderChange: (List<String>) -> Unit,
    onPickBarTextHiddenChange: (Set<String>) -> Unit,
    onPickBarImageOrderChange: (List<String>) -> Unit,
    onPickBarImageHiddenChange: (Set<String>) -> Unit,
    onResetPickBarButtons: () -> Unit,
    onPickTextSizeChange: (Float) -> Unit,
    onPickHapticEnabledChange: (Boolean) -> Unit,
    onPickPanelEnterAnimationMsChange: (Int) -> Unit,
    onPickPanelExitAnimationMsChange: (Int) -> Unit,
) {
    val appearanceSectionTitle = stringResource(R.string.pick_settings_section_panel_appearance)
    val contentSearchSectionTitle = stringResource(R.string.pick_settings_section_panel_content_search)
    val selectCopySectionTitle = stringResource(R.string.pick_settings_section_panel_select_copy)
    val feedbackSectionTitle = stringResource(R.string.pick_settings_section_panel_feedback)
    val panelAnimationsTitle = stringResource(R.string.pick_settings_panel_animations)
    val barButtonsTitle = stringResource(R.string.float_ball_pick_bar_buttons)
    val panelAnimExpanded = remember { mutableStateOf(false) }

    val integratedLabel = stringResource(R.string.float_ball_pick_panel_style_integrated)
    val tabPagedLabel = stringResource(R.string.float_ball_pick_panel_style_tab_paged)
    val panelStyleOptions = remember(integratedLabel, tabPagedLabel) {
        listOf(
            com.slideindex.app.settings.PickResultPanelStyle.TAB_PAGED to tabPagedLabel,
            com.slideindex.app.settings.PickResultPanelStyle.INTEGRATED_BOTTOM_BAR to integratedLabel,
        )
    }
    val selectedPanelStyleIndex = remember(settings.floatBallPickPanelStyle, panelStyleOptions) {
        panelStyleOptions.indexOfFirst { it.first == settings.floatBallPickPanelStyle }.coerceAtLeast(0)
    }
    val panelStyleItems = remember(panelStyleOptions) {
        panelStyleOptions.map { DropdownItem(text = it.second) }
    }

    val placementBottomLabel = stringResource(R.string.float_ball_pick_panel_placement_bottom)
    val placementCenterLabel = stringResource(R.string.float_ball_pick_panel_placement_center)
    val placementOptions = remember(placementBottomLabel, placementCenterLabel) {
        listOf(
            com.slideindex.app.settings.PickResultPanelPlacement.BOTTOM_DOCKED to placementBottomLabel,
            com.slideindex.app.settings.PickResultPanelPlacement.CENTER to placementCenterLabel,
        )
    }
    val selectedPlacementIndex = remember(settings.floatBallPickPanelPlacement, placementOptions) {
        placementOptions.indexOfFirst { it.first == settings.floatBallPickPanelPlacement }.coerceAtLeast(0)
    }
    val placementItems = remember(placementOptions) {
        placementOptions.map { DropdownItem(text = it.second) }
    }

    val searchGridStateRememberLabel = stringResource(R.string.float_ball_pick_search_grid_state_remember)
    val searchGridStateExpandedLabel = stringResource(R.string.float_ball_pick_search_grid_state_expanded)
    val searchGridStateCollapsedLabel = stringResource(R.string.float_ball_pick_search_grid_state_collapsed)
    val searchGridStateOptions = remember(
        searchGridStateRememberLabel,
        searchGridStateExpandedLabel,
        searchGridStateCollapsedLabel,
    ) {
        listOf(
            com.slideindex.app.settings.PickResultSearchGridDefaultState.REMEMBER_LAST to searchGridStateRememberLabel,
            com.slideindex.app.settings.PickResultSearchGridDefaultState.ALWAYS_EXPANDED to searchGridStateExpandedLabel,
            com.slideindex.app.settings.PickResultSearchGridDefaultState.ALWAYS_COLLAPSED to searchGridStateCollapsedLabel,
        )
    }
    val selectedSearchGridStateIndex = remember(settings.floatBallPickSearchGridDefaultState, searchGridStateOptions) {
        searchGridStateOptions.indexOfFirst { it.first == settings.floatBallPickSearchGridDefaultState }.coerceAtLeast(0)
    }
    val searchGridStateItems = remember(searchGridStateOptions) {
        searchGridStateOptions.map { DropdownItem(text = it.second) }
    }

    val textModeRememberLabel = stringResource(R.string.float_ball_pick_text_mode_state_remember)
    val textModeAlwaysOnLabel = stringResource(R.string.float_ball_pick_text_mode_state_always_on)
    val textModeAlwaysOffLabel = stringResource(R.string.float_ball_pick_text_mode_state_always_off)
    val textModeOptions = remember(
        textModeRememberLabel,
        textModeAlwaysOnLabel,
        textModeAlwaysOffLabel,
    ) {
        listOf(
            com.slideindex.app.settings.PickResultTextModeDefault.REMEMBER_LAST to textModeRememberLabel,
            com.slideindex.app.settings.PickResultTextModeDefault.ALWAYS_ON to textModeAlwaysOnLabel,
            com.slideindex.app.settings.PickResultTextModeDefault.ALWAYS_OFF to textModeAlwaysOffLabel,
        )
    }
    val selectedTextModeIndex = remember(settings.floatBallPickTextModeDefault, textModeOptions) {
        textModeOptions.indexOfFirst { it.first == settings.floatBallPickTextModeDefault }.coerceAtLeast(0)
    }
    val textModeItems = remember(textModeOptions) {
        textModeOptions.map { DropdownItem(text = it.second) }
    }

    val granularityWordLabel = stringResource(R.string.float_ball_pick_word_tap_granularity_word)
    val granularityCharacterLabel = stringResource(R.string.float_ball_pick_word_tap_granularity_character)
    val granularityOptions = remember(granularityWordLabel, granularityCharacterLabel) {
        listOf(
            com.slideindex.app.settings.PickResultWordTapGranularity.WORD to granularityWordLabel,
            com.slideindex.app.settings.PickResultWordTapGranularity.CHARACTER to granularityCharacterLabel,
        )
    }
    val selectedGranularityIndex = remember(
        settings.floatBallPickWordTapGranularity,
        granularityOptions,
    ) {
        granularityOptions.indexOfFirst { it.first == settings.floatBallPickWordTapGranularity }
            .coerceAtLeast(0)
    }
    val granularityItems = remember(granularityOptions) {
        granularityOptions.map { DropdownItem(text = it.second) }
    }

    val copyButtonPositionLeftLabel = stringResource(R.string.float_ball_pick_copy_button_position_left)
    val copyButtonPositionRightLabel = stringResource(R.string.float_ball_pick_copy_button_position_right)
    val copyButtonPositionOptions = remember(copyButtonPositionLeftLabel, copyButtonPositionRightLabel) {
        listOf(
            com.slideindex.app.settings.PickResultCopyButtonPosition.LEFT to copyButtonPositionLeftLabel,
            com.slideindex.app.settings.PickResultCopyButtonPosition.RIGHT to copyButtonPositionRightLabel,
        )
    }
    val selectedCopyButtonPositionIndex = remember(settings.floatBallPickCopyButtonPosition, copyButtonPositionOptions) {
        copyButtonPositionOptions.indexOfFirst { it.first == settings.floatBallPickCopyButtonPosition }.coerceAtLeast(0)
    }
    val copyButtonPositionItems = remember(copyButtonPositionOptions) {
        copyButtonPositionOptions.map { DropdownItem(text = it.second) }
    }

    val imageToolbarPositionLeftLabel = stringResource(R.string.float_ball_pick_image_toolbar_position_left)
    val imageToolbarPositionRightLabel = stringResource(R.string.float_ball_pick_image_toolbar_position_right)
    val imageToolbarPositionOptions = remember(imageToolbarPositionLeftLabel, imageToolbarPositionRightLabel) {
        listOf(
            com.slideindex.app.settings.PickResultImageToolbarPosition.LEFT to imageToolbarPositionLeftLabel,
            com.slideindex.app.settings.PickResultImageToolbarPosition.RIGHT to imageToolbarPositionRightLabel,
        )
    }
    val selectedImageToolbarPositionIndex = remember(settings.floatBallPickImageToolbarPosition, imageToolbarPositionOptions) {
        imageToolbarPositionOptions.indexOfFirst { it.first == settings.floatBallPickImageToolbarPosition }.coerceAtLeast(0)
    }
    val imageToolbarPositionItems = remember(imageToolbarPositionOptions) {
        imageToolbarPositionOptions.map { DropdownItem(text = it.second) }
    }

    SettingsScreenScaffold(
        title = stringResource(R.string.pick_settings_panel_layout_behavior_title),
        onBack = onBack,
    ) {
        settingsLazySmallTitle(
            key = "panel-appearance-section",
            title = appearanceSectionTitle,
        )
        // 底栏样式：经典 / 纯图标二选一，代替原来的「开关 + 置灰折叠组」。
        LazySettingsItem(key = "pick-bar-style") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.defaultColors(
                    color = MiuixTheme.colorScheme.surfaceContainer,
                    contentColor = MiuixTheme.colorScheme.onSurfaceContainer,
                ),
                insideMargin = PaddingValues(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.float_ball_pick_bar_style_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SegmentedControl(
                        items = listOf(false, true),
                        selected = settings.floatBallPickIconBarEnabled,
                        labelRes = { iconOnly ->
                            if (iconOnly) {
                                R.string.float_ball_pick_bar_style_icon
                            } else {
                                R.string.float_ball_pick_bar_style_classic
                            }
                        },
                        onSelect = onPickIconBarEnabledChange,
                        containerColor = MiuixTheme.colorScheme.surface,
                        indicatorColor = MiuixTheme.colorScheme.surfaceContainer,
                    )
                    Text(
                        text = stringResource(R.string.float_ball_pick_bar_style_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    )
                }
            }
        }
        groupedCardItems(
            keyPrefix = "fb-pick-panel-appearance",
            items = buildList {
                add(
                    settingsCardScopeItem("panel-style") {
                        SettingSpinnerRow(
                            title = stringResource(R.string.float_ball_pick_panel_style),
                            subtitle = panelStyleOptions.getOrNull(selectedPanelStyleIndex)?.second.orEmpty(),
                            dialogButtonText = stringResource(R.string.cancel),
                            items = panelStyleItems,
                            selectedIndex = selectedPanelStyleIndex,
                            enabled = true,
                            onSelectedIndexChange = { index ->
                                val selected = panelStyleOptions.getOrNull(index)?.first ?: return@SettingSpinnerRow
                                onPickPanelStyleChange(selected)
                            },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("panel-placement") {
                        SettingSpinnerRow(
                            title = stringResource(R.string.float_ball_pick_panel_placement),
                            subtitle = placementOptions.getOrNull(selectedPlacementIndex)?.second.orEmpty(),
                            dialogButtonText = stringResource(R.string.cancel),
                            items = placementItems,
                            selectedIndex = selectedPlacementIndex,
                            enabled = true,
                            onSelectedIndexChange = { index ->
                                val selected = placementOptions.getOrNull(index)?.first
                                    ?: return@SettingSpinnerRow
                                onPickPanelPlacementChange(selected)
                            },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("text-size") {
                        SettingsSliderRow(
                            title = stringResource(R.string.float_ball_pick_text_size),
                            value = settings.floatBallPickTextSizeSp,
                            valueRange = 12f..22f,
                            steps = 9,
                            enabled = true,
                            label = stringResource(R.string.float_ball_text_size_value, settings.floatBallPickTextSizeSp),
                            onValueChange = onPickTextSizeChange,
                        )
                    },
                )
                // 经典样式的两个位置项只在经典模式下出现；纯图标模式的内容在下方独立卡片里。
                if (!settings.floatBallPickIconBarEnabled) {
                    add(
                        settingsCardScopeItem("copy-button-position") {
                            SettingSpinnerRow(
                                title = stringResource(R.string.float_ball_pick_copy_button_position),
                                subtitle = copyButtonPositionOptions.getOrNull(selectedCopyButtonPositionIndex)?.second.orEmpty(),
                                dialogButtonText = stringResource(R.string.cancel),
                                items = copyButtonPositionItems,
                                selectedIndex = selectedCopyButtonPositionIndex,
                                enabled = true,
                                onSelectedIndexChange = { index ->
                                    val selected = copyButtonPositionOptions.getOrNull(index)?.first
                                        ?: return@SettingSpinnerRow
                                    onPickCopyButtonPositionChange(selected)
                                },
                            )
                        },
                    )
                    add(
                        settingsCardScopeItem("image-toolbar-position") {
                            SettingSpinnerRow(
                                title = stringResource(R.string.float_ball_pick_image_toolbar_position),
                                subtitle = imageToolbarPositionOptions.getOrNull(selectedImageToolbarPositionIndex)?.second.orEmpty(),
                                dialogButtonText = stringResource(R.string.cancel),
                                items = imageToolbarPositionItems,
                                selectedIndex = selectedImageToolbarPositionIndex,
                                enabled = true,
                                onSelectedIndexChange = { index ->
                                    val selected = imageToolbarPositionOptions.getOrNull(index)?.first
                                        ?: return@SettingSpinnerRow
                                    onPickImageToolbarPositionChange(selected)
                                },
                            )
                        },
                    )
                }
            },
        )
        // 底栏按钮卡片：Lazy item 常驻，不要用 if 增删（列表项增删没有过渡）；
        // 只靠 AnimatedVisibility 做展开/收起 + 淡入淡出。
        LazySettingsItem(key = "pick-bar-buttons-section") {
            AnimatedVisibility(
                visible = settings.floatBallPickIconBarEnabled,
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.defaultColors(
                        color = MiuixTheme.colorScheme.surfaceContainer,
                        contentColor = MiuixTheme.colorScheme.onSurfaceContainer,
                    ),
                    insideMargin = PaddingValues(16.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = barButtonsTitle,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        PickResultBarButtonSection(
                            textOrder = settings.floatBallPickTextBarOrder,
                            textHidden = settings.floatBallPickTextBarHidden,
                            imageOrder = settings.floatBallPickImageBarOrder,
                            imageHidden = settings.floatBallPickImageBarHidden,
                            onTextOrderChange = onPickBarTextOrderChange,
                            onTextHiddenChange = onPickBarTextHiddenChange,
                            onImageOrderChange = onPickBarImageOrderChange,
                            onImageHiddenChange = onPickBarImageHiddenChange,
                            onRestoreDefaults = onResetPickBarButtons,
                        )
                    }
                }
            }
        }
        settingsLazySmallTitle(
            key = "panel-content-search-section",
            title = contentSearchSectionTitle,
        )
        groupedCardItems(
            keyPrefix = "fb-pick-panel-content-search",
            items = buildList {
                if (settings.floatBallPickPanelStyle ==
                    com.slideindex.app.settings.PickResultPanelStyle.INTEGRATED_BOTTOM_BAR
                ) {
                    add(
                        settingsCardScopeItem("text-first-panel") {
                            SettingSwitchRow(
                                title = stringResource(R.string.float_ball_pick_text_first_panel),
                                subtitle = stringResource(R.string.float_ball_pick_text_first_panel_desc),
                                checked = settings.floatBallPickTextFirstPanel,
                                enabled = true,
                                onCheckedChange = onPickTextFirstPanelChange,
                            )
                        },
                    )
                }
                add(
                    settingsCardScopeItem("search-grid-default-state") {
                        SettingSpinnerRow(
                            title = stringResource(R.string.float_ball_pick_search_grid_state_title),
                            subtitle = searchGridStateOptions.getOrNull(selectedSearchGridStateIndex)?.second.orEmpty(),
                            dialogButtonText = stringResource(R.string.cancel),
                            items = searchGridStateItems,
                            selectedIndex = selectedSearchGridStateIndex,
                            enabled = true,
                            onSelectedIndexChange = { index ->
                                val selected = searchGridStateOptions.getOrNull(index)?.first
                                    ?: return@SettingSpinnerRow
                                onPickSearchGridDefaultStateChange(selected)
                            },
                        )
                    },
                )
            },
        )
        settingsLazySmallTitle(
            key = "panel-select-copy-section",
            title = selectCopySectionTitle,
        )
        groupedCardItems(
            keyPrefix = "fb-pick-panel-select-copy",
            items = buildList {
                add(
                    settingsCardScopeItem("text-mode-default-state") {
                        SettingSpinnerRow(
                            title = stringResource(R.string.float_ball_pick_text_mode_state_title),
                            subtitle = textModeOptions.getOrNull(selectedTextModeIndex)?.second.orEmpty(),
                            dialogButtonText = stringResource(R.string.cancel),
                            items = textModeItems,
                            selectedIndex = selectedTextModeIndex,
                            enabled = true,
                            onSelectedIndexChange = { index ->
                                val selected = textModeOptions.getOrNull(index)?.first
                                    ?: return@SettingSpinnerRow
                                onPickTextModeDefaultChange(selected)
                            },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("word-tap-granularity") {
                        SettingSpinnerRow(
                            title = stringResource(R.string.float_ball_pick_word_tap_granularity_title),
                            subtitle = granularityOptions.getOrNull(selectedGranularityIndex)?.second.orEmpty(),
                            dialogButtonText = stringResource(R.string.cancel),
                            items = granularityItems,
                            selectedIndex = selectedGranularityIndex,
                            enabled = true,
                            onSelectedIndexChange = { index ->
                                val selected = granularityOptions.getOrNull(index)?.first
                                    ?: return@SettingSpinnerRow
                                onPickWordTapGranularityChange(selected)
                            },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("auto-select-all") {
                        SettingSwitchRow(
                            title = stringResource(R.string.float_ball_pick_auto_select_all),
                            subtitle = stringResource(R.string.float_ball_pick_auto_select_all_desc),
                            checked = settings.floatBallPickAutoSelectAll,
                            enabled = true,
                            onCheckedChange = onPickAutoSelectAllChange,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("copy-dismiss-panel") {
                        SettingSwitchRow(
                            title = stringResource(R.string.float_ball_pick_copy_dismiss_panel),
                            subtitle = stringResource(R.string.float_ball_pick_copy_dismiss_panel_desc),
                            checked = settings.floatBallPickCopyDismissPanel,
                            enabled = true,
                            onCheckedChange = onPickCopyDismissPanelChange,
                        )
                    },
                )
            },
        )
        settingsLazySmallTitle(
            key = "panel-feedback-section",
            title = feedbackSectionTitle,
        )
        groupedCardItems(
            keyPrefix = "fb-pick-panel-feedback",
            items = buildList {
                add(
                    settingsCardScopeItem("pick-haptic-enabled") {
                        SettingSwitchRow(
                            title = stringResource(R.string.float_ball_pick_haptic_enabled),
                            subtitle = stringResource(R.string.float_ball_pick_haptic_enabled_desc),
                            checked = settings.floatBallPickHapticEnabled,
                            enabled = true,
                            onCheckedChange = onPickHapticEnabledChange,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("panel-animations") {
                        SettingExpandableSwitchRow(
                            title = panelAnimationsTitle,
                            checked = panelAnimExpanded.value,
                            enabled = true,
                            onCheckedChange = { panelAnimExpanded.value = it },
                        ) {
                            SettingsSliderRow(
                                title = stringResource(R.string.float_ball_pick_panel_enter_animation),
                                value = settings.floatBallPickPanelEnterAnimationMs.toFloat(),
                                valueRange = PickPanelSlideAnimationDefaults.MIN_MS.toFloat()
                                    ..PickPanelSlideAnimationDefaults.MAX_MS.toFloat(),
                                steps = (PickPanelSlideAnimationDefaults.MAX_MS - PickPanelSlideAnimationDefaults.MIN_MS) / 10,
                                enabled = true,
                                label = stringResource(
                                    R.string.float_ball_pick_panel_animation_ms_value,
                                    settings.floatBallPickPanelEnterAnimationMs,
                                ),
                                onValueChange = { onPickPanelEnterAnimationMsChange(it.roundToInt()) },
                            )
                            SettingsSliderRow(
                                title = stringResource(R.string.float_ball_pick_panel_exit_animation),
                                value = settings.floatBallPickPanelExitAnimationMs.toFloat(),
                                valueRange = PickPanelSlideAnimationDefaults.MIN_MS.toFloat()
                                    ..PickPanelSlideAnimationDefaults.MAX_MS.toFloat(),
                                steps = (PickPanelSlideAnimationDefaults.MAX_MS - PickPanelSlideAnimationDefaults.MIN_MS) / 10,
                                enabled = true,
                                label = stringResource(
                                    R.string.float_ball_pick_panel_animation_ms_value,
                                    settings.floatBallPickPanelExitAnimationMs,
                                ),
                                onValueChange = { onPickPanelExitAnimationMsChange(it.roundToInt()) },
                            )
                        }
                    },
                )
            },
        )
    }
}
