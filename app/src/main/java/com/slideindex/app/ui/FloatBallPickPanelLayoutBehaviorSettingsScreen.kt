package com.slideindex.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.PickPanelSlideAnimationDefaults
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingsCardRow
import com.slideindex.app.ui.settings.components.SettingsCardScope
import com.slideindex.app.ui.settings.components.SettingExpandableRow
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsGroupedRowBackground
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Text as MiuixText
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
    onPickMergeLinksChange: (Boolean) -> Unit,
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
    val selectCopySectionTitle = stringResource(R.string.pick_settings_section_panel_select_copy)
    val feedbackSectionTitle = stringResource(R.string.pick_settings_section_panel_feedback)
    val panelAnimationsTitle = stringResource(R.string.pick_settings_panel_animations)
    val barButtonsTitle = stringResource(R.string.float_ball_pick_bar_buttons)
    val panelAnimExpanded = remember { mutableStateOf(false) }

    // 本地乐观状态：点选后立刻生效，不等 DataStore 写盘 + settings flow 回流。
    var localIconBarEnabled by remember { mutableStateOf(settings.floatBallPickIconBarEnabled) }
    var localPanelStyle by remember { mutableStateOf(settings.floatBallPickPanelStyle) }
    var localPanelPlacement by remember { mutableStateOf(settings.floatBallPickPanelPlacement) }
    var localGranularity by remember { mutableStateOf(settings.floatBallPickWordTapGranularity) }
    LaunchedEffect(settings.floatBallPickWordTapGranularity) {
        localGranularity = settings.floatBallPickWordTapGranularity
    }
    LaunchedEffect(settings.floatBallPickIconBarEnabled) {
        localIconBarEnabled = settings.floatBallPickIconBarEnabled
    }
    LaunchedEffect(settings.floatBallPickPanelStyle) {
        localPanelStyle = settings.floatBallPickPanelStyle
    }
    LaunchedEffect(settings.floatBallPickPanelPlacement) {
        localPanelPlacement = settings.floatBallPickPanelPlacement
    }
    var localCopyPosition by remember { mutableStateOf(settings.floatBallPickCopyButtonPosition) }
    var localImageToolbarPosition by remember { mutableStateOf(settings.floatBallPickImageToolbarPosition) }
    LaunchedEffect(settings.floatBallPickCopyButtonPosition) {
        localCopyPosition = settings.floatBallPickCopyButtonPosition
    }
    LaunchedEffect(settings.floatBallPickImageToolbarPosition) {
        localImageToolbarPosition = settings.floatBallPickImageToolbarPosition
    }

    var localSearchGridState by remember { mutableStateOf(settings.floatBallPickSearchGridDefaultState) }
    var localTextModeDefault by remember { mutableStateOf(settings.floatBallPickTextModeDefault) }
    LaunchedEffect(settings.floatBallPickSearchGridDefaultState) {
        localSearchGridState = settings.floatBallPickSearchGridDefaultState
    }
    LaunchedEffect(settings.floatBallPickTextModeDefault) {
        localTextModeDefault = settings.floatBallPickTextModeDefault
    }

    SettingsScreenScaffold(
        title = stringResource(R.string.pick_settings_panel_layout_behavior_title),
        onBack = onBack,
    ) {
        settingsLazySmallTitle(
            key = "panel-appearance-section",
            title = appearanceSectionTitle,
        )
        groupedCardItems(
            keyPrefix = "fb-pick-panel-appearance",
            items = buildList {
                add(
                    settingsCardScopeItem("panel-style") {
                        SettingSegmentedRow(
                            title = stringResource(R.string.float_ball_pick_panel_style),
                            items = listOf(
                                com.slideindex.app.settings.PickResultPanelStyle.TAB_PAGED,
                                com.slideindex.app.settings.PickResultPanelStyle.INTEGRATED_BOTTOM_BAR,
                            ),
                            selected = localPanelStyle,
                            labelRes = { style ->
                                when (style) {
                                    com.slideindex.app.settings.PickResultPanelStyle.TAB_PAGED ->
                                        R.string.float_ball_pick_panel_style_tab_paged

                                    com.slideindex.app.settings.PickResultPanelStyle.INTEGRATED_BOTTOM_BAR ->
                                        R.string.float_ball_pick_panel_style_integrated
                                }
                            },
                            onSelect = { style ->
                                localPanelStyle = style
                                onPickPanelStyleChange(style)
                            },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("panel-placement") {
                        SettingSegmentedRow(
                            title = stringResource(R.string.float_ball_pick_panel_placement),
                            items = listOf(
                                com.slideindex.app.settings.PickResultPanelPlacement.BOTTOM_DOCKED,
                                com.slideindex.app.settings.PickResultPanelPlacement.CENTER,
                            ),
                            selected = localPanelPlacement,
                            labelRes = { placement ->
                                when (placement) {
                                    com.slideindex.app.settings.PickResultPanelPlacement.BOTTOM_DOCKED ->
                                        R.string.float_ball_pick_panel_placement_bottom

                                    com.slideindex.app.settings.PickResultPanelPlacement.CENTER ->
                                        R.string.float_ball_pick_panel_placement_center
                                }
                            },
                            onSelect = { placement ->
                                localPanelPlacement = placement
                                onPickPanelPlacementChange(placement)
                            },
                        )
                    },
                )
                // 底栏样式放卡片最底部：样式行 + 对应配置（经典位置 / 纯图标工具条）。
                add(
                    settingsCardScopeItem("pick-bar-style") {
                        SettingsCardRow(key = "pick-bar-style-row") { position ->
                            Column(
                                modifier = Modifier.settingsGroupedRowBackground(position.index, position.count),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    MiuixText(
                                        text = stringResource(R.string.float_ball_pick_bar_style_title),
                                        style = MiuixTheme.textStyles.headline1,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    SegmentedControl(
                                        items = listOf(false, true),
                                        selected = localIconBarEnabled,
                                        labelRes = { iconOnly ->
                                            if (iconOnly) {
                                                R.string.float_ball_pick_bar_style_icon
                                            } else {
                                                R.string.float_ball_pick_bar_style_classic
                                            }
                                        },
                                        onSelect = { iconOnly ->
                                            localIconBarEnabled = iconOnly
                                            onPickIconBarEnabledChange(iconOnly)
                                        },
                                        containerColor = MiuixTheme.colorScheme.surface,
                                        indicatorColor = MiuixTheme.colorScheme.surfaceContainer,
                                    )
                                    MiuixText(
                                        text = stringResource(R.string.float_ball_pick_bar_style_desc),
                                        style = MiuixTheme.textStyles.body2,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                            }
                        }
                    },
                )
                // 经典位置：常驻 item，靠 AnimatedVisibility 收起/展开（与图标条互补，切换时高度连续变化）。
                add(
                    settingsCardScopeItem("classic-bar-positions") {
                        SettingsCardRow(key = "classic-bar-positions-row") { position ->
                            Column(
                                modifier = Modifier.settingsGroupedRowBackground(position.index, position.count),
                            ) {
                                AnimatedVisibility(
                                    visible = !localIconBarEnabled,
                                    enter = expandVertically(
                                        animationSpec = tween(220),
                                        expandFrom = Alignment.Top,
                                    ) + fadeIn(tween(220)),
                                    exit = shrinkVertically(
                                        animationSpec = tween(220),
                                        shrinkTowards = Alignment.Top,
                                    ) + fadeOut(tween(220)),
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        SegmentedSettingBlock(
                                            title = stringResource(R.string.float_ball_pick_copy_button_position),
                                            items = listOf(
                                                com.slideindex.app.settings.PickResultCopyButtonPosition.LEFT,
                                                com.slideindex.app.settings.PickResultCopyButtonPosition.RIGHT,
                                            ),
                                            selected = localCopyPosition,
                                            labelRes = { position ->
                                                when (position) {
                                                    com.slideindex.app.settings.PickResultCopyButtonPosition.LEFT ->
                                                        R.string.float_ball_pick_copy_button_position_left

                                                    com.slideindex.app.settings.PickResultCopyButtonPosition.RIGHT ->
                                                        R.string.float_ball_pick_copy_button_position_right
                                                }
                                            },
                                            onSelect = { position ->
                                                localCopyPosition = position
                                                onPickCopyButtonPositionChange(position)
                                            },
                                        )
                                        SegmentedSettingBlock(
                                            title = stringResource(R.string.float_ball_pick_image_toolbar_position),
                                            items = listOf(
                                                com.slideindex.app.settings.PickResultImageToolbarPosition.LEFT,
                                                com.slideindex.app.settings.PickResultImageToolbarPosition.RIGHT,
                                            ),
                                            selected = localImageToolbarPosition,
                                            labelRes = { position ->
                                                when (position) {
                                                    com.slideindex.app.settings.PickResultImageToolbarPosition.LEFT ->
                                                        R.string.float_ball_pick_image_toolbar_position_left

                                                    com.slideindex.app.settings.PickResultImageToolbarPosition.RIGHT ->
                                                        R.string.float_ball_pick_image_toolbar_position_right
                                                }
                                            },
                                            onSelect = { position ->
                                                localImageToolbarPosition = position
                                                onPickImageToolbarPositionChange(position)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    },
                )
                // 图标条行常驻（不随模式增删），靠 AnimatedVisibility 做展开/收起，切换模式才有动画。
                add(
                    settingsCardScopeItem("pick-bar-buttons") {
                        SettingsCardRow(key = "pick-bar-buttons-row") { position ->
                            Column(
                                modifier = Modifier.settingsGroupedRowBackground(position.index, position.count),
                            ) {
                                AnimatedVisibility(
                                    visible = localIconBarEnabled,
                                    enter = expandVertically(
                                        animationSpec = tween(220),
                                        expandFrom = Alignment.Top,
                                    ) + fadeIn(tween(220)),
                                    exit = shrinkVertically(
                                        animationSpec = tween(220),
                                        shrinkTowards = Alignment.Top,
                                    ) + fadeOut(tween(220)),
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        MiuixText(
                                            text = barButtonsTitle,
                                            style = MiuixTheme.textStyles.headline1,
                                            fontWeight = FontWeight.Medium,
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
                    },
                )
                // 字号放整张卡最后一行：末行有真实高度，卡片底部圆角才正常。
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
                        SettingSegmentedRow(
                            title = stringResource(R.string.float_ball_pick_text_mode_state_title),
                            items = listOf(
                                com.slideindex.app.settings.PickResultTextModeDefault.REMEMBER_LAST,
                                com.slideindex.app.settings.PickResultTextModeDefault.ALWAYS_ON,
                                com.slideindex.app.settings.PickResultTextModeDefault.ALWAYS_OFF,
                            ),
                            selected = localTextModeDefault,
                            labelRes = { mode ->
                                when (mode) {
                                    com.slideindex.app.settings.PickResultTextModeDefault.REMEMBER_LAST ->
                                        R.string.float_ball_pick_text_mode_state_remember

                                    com.slideindex.app.settings.PickResultTextModeDefault.ALWAYS_ON ->
                                        R.string.float_ball_pick_text_mode_state_always_on

                                    com.slideindex.app.settings.PickResultTextModeDefault.ALWAYS_OFF ->
                                        R.string.float_ball_pick_text_mode_state_always_off
                                }
                            },
                            onSelect = { mode ->
                                localTextModeDefault = mode
                                onPickTextModeDefaultChange(mode)
                            },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("word-tap-granularity") {
                        SettingSegmentedRow(
                            title = stringResource(R.string.float_ball_pick_word_tap_granularity_title),
                            items = listOf(
                                com.slideindex.app.settings.PickResultWordTapGranularity.WORD,
                                com.slideindex.app.settings.PickResultWordTapGranularity.CHARACTER,
                            ),
                            selected = localGranularity,
                            labelRes = { granularity ->
                                when (granularity) {
                                    com.slideindex.app.settings.PickResultWordTapGranularity.WORD ->
                                        R.string.float_ball_pick_word_tap_granularity_word

                                    com.slideindex.app.settings.PickResultWordTapGranularity.CHARACTER ->
                                        R.string.float_ball_pick_word_tap_granularity_character
                                }
                            },
                            onSelect = { granularity ->
                                localGranularity = granularity
                                onPickWordTapGranularityChange(granularity)
                            },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("merge-links") {
                        SettingSwitchRow(
                            title = stringResource(R.string.float_ball_pick_merge_links),
                            subtitle = stringResource(R.string.float_ball_pick_merge_links_desc),
                            checked = settings.floatBallPickMergeLinks,
                            // 只有「分词」会生成词块；按字模式不画 chip，这一项不生效。
                            enabled = localGranularity !=
                                com.slideindex.app.settings.PickResultWordTapGranularity.CHARACTER,
                            onCheckedChange = onPickMergeLinksChange,
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
                if (localPanelStyle ==
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
                        SettingSegmentedRow(
                            title = stringResource(R.string.float_ball_pick_search_grid_state_title),
                            items = listOf(
                                com.slideindex.app.settings.PickResultSearchGridDefaultState.REMEMBER_LAST,
                                com.slideindex.app.settings.PickResultSearchGridDefaultState.ALWAYS_EXPANDED,
                                com.slideindex.app.settings.PickResultSearchGridDefaultState.ALWAYS_COLLAPSED,
                            ),
                            selected = localSearchGridState,
                            labelRes = { state ->
                                when (state) {
                                    com.slideindex.app.settings.PickResultSearchGridDefaultState.REMEMBER_LAST ->
                                        R.string.float_ball_pick_search_grid_state_remember

                                    com.slideindex.app.settings.PickResultSearchGridDefaultState.ALWAYS_EXPANDED ->
                                        R.string.float_ball_pick_search_grid_state_expanded

                                    com.slideindex.app.settings.PickResultSearchGridDefaultState.ALWAYS_COLLAPSED ->
                                        R.string.float_ball_pick_search_grid_state_collapsed
                                }
                            },
                            onSelect = { state ->
                                localSearchGridState = state
                                onPickSearchGridDefaultStateChange(state)
                            },
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
                        SettingExpandableRow(
                            title = panelAnimationsTitle,
                            expanded = panelAnimExpanded.value,
                            onExpandedChange = { panelAnimExpanded.value = it },
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

/** 卡片行内的「标题 + 一整行 SegmentedControl」块（不单独占用分段，供 AnimatedVisibility 包住）。 */
@Composable
private fun <T> SegmentedSettingBlock(
    title: String,
    items: List<T>,
    selected: T,
    labelRes: (T) -> Int,
    onSelect: (T) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MiuixText(
            text = title,
            style = MiuixTheme.textStyles.headline1,
            fontWeight = FontWeight.Medium,
        )
        SegmentedControl(
            items = items,
            selected = selected,
            labelRes = labelRes,
            onSelect = onSelect,
            containerColor = MiuixTheme.colorScheme.surface,
            indicatorColor = MiuixTheme.colorScheme.surfaceContainer,
        )
    }
}

/** 设置行：标题 + 副标题 + 一整行 SegmentedControl（替代只有两项的下拉）。 */
@Composable
private fun <T> SettingsCardScope.SettingSegmentedRow(
    title: String,
    subtitle: String? = null,
    items: List<T>,
    selected: T,
    labelRes: (T) -> Int,
    onSelect: (T) -> Unit,
) {
    SettingsCardRow(key = title) { position ->
        Column(
            modifier = Modifier
                .settingsGroupedRowBackground(position.index, position.count)
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MiuixText(
                text = title,
                style = MiuixTheme.textStyles.headline1,
                fontWeight = FontWeight.Medium,
            )
            subtitle?.let {
                MiuixText(
                    text = it,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            SegmentedControl(
                items = items,
                selected = selected,
                labelRes = labelRes,
                onSelect = onSelect,
                containerColor = MiuixTheme.colorScheme.surface,
                indicatorColor = MiuixTheme.colorScheme.surfaceContainer,
            )
        }
    }
}
