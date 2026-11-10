package com.slideindex.app.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.gesture.SelectedHintMetrics
import com.slideindex.app.overlay.corner.CornerWheelLayout
import com.slideindex.app.overlay.layout.FvIconShape
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.CornerGestureSettings
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingExpandableSwitchRow
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazyTipCard
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.DropdownItem

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CornerGestureInteractionScreen(
    settings: AppSettings,
    serviceEnabled: Boolean,
    onBack: () -> Unit,
    onVerticalEdgeWidthChange: (Float) -> Unit,
    onVerticalEdgeHeightChange: (Float) -> Unit,
    onHorizontalEdgeWidthChange: (Float) -> Unit,
    onHorizontalEdgeHeightChange: (Float) -> Unit,
    onZonePreviewStart: () -> Unit = {},
    onZonePreviewStop: () -> Unit = {},
    onZonePreviewDimensionsChange: (
        verticalEdgeWidthDp: Float,
        verticalEdgeHeightDp: Float,
        horizontalEdgeWidthDp: Float,
        horizontalEdgeHeightDp: Float
    ) -> Unit = { _, _, _, _ -> },
    onTriggerSlopChange: (Float) -> Unit,
    onHideInLandscapeChange: (Boolean) -> Unit,
    onLandscapePreventFalseTouchChange: (Boolean) -> Unit,
    onOverrideSystemNavChange: (Boolean) -> Unit,
    onInnerDiameterChange: (Float) -> Unit,
    onRingSpacingChange: (Float) -> Unit,
    onBubbleSizeChange: (Float) -> Unit,
    onSlotIconShapeChange: (FvIconShape) -> Unit,
    onCancelOutsideWheelChange: (Boolean) -> Unit,
    onProgressiveLayersChange: (Boolean) -> Unit,
    onSlotHapticChange: (Boolean) -> Unit = {},
    onShowSelectedNameChange: (Boolean) -> Unit,
    onShowEditButtonChange: (Boolean) -> Unit = {},
    onSelectedHintIconSizeChange: (Int) -> Unit,
    onBackgroundStyleChange: (Int) -> Unit,
    onBlurDpChange: (Int) -> Unit,
    onDimPercentChange: (Int) -> Unit
) {
    val corner = settings.cornerGestureSettings

    // 下拉切换背景时立刻驱动条件滑条，避免等 DataStore 回流。
    var localBackgroundStyle by remember(corner.backgroundStyle) {
        mutableIntStateOf(corner.backgroundStyle)
    }
    var localBlurDp by remember(corner.blurDp) { mutableIntStateOf(corner.blurDp) }
    var localDimPercent by remember(corner.dimPercent) { mutableIntStateOf(corner.dimPercent) }

    val zoneControlsEnabled = serviceEnabled && corner.enabled

    var previewVerticalWidthDp by remember { mutableFloatStateOf(corner.verticalEdgeWidthDp) }
    var previewVerticalHeightDp by remember { mutableFloatStateOf(corner.verticalEdgeHeightDp) }
    var previewHorizontalWidthDp by remember { mutableFloatStateOf(corner.horizontalEdgeWidthDp) }
    var previewHorizontalHeightDp by remember { mutableFloatStateOf(corner.horizontalEdgeHeightDp) }

    LaunchedEffect(
        corner.verticalEdgeWidthDp,
        corner.verticalEdgeHeightDp,
        corner.horizontalEdgeWidthDp,
        corner.horizontalEdgeHeightDp
    ) {
        previewVerticalWidthDp = corner.verticalEdgeWidthDp
        previewVerticalHeightDp = corner.verticalEdgeHeightDp
        previewHorizontalWidthDp = corner.horizontalEdgeWidthDp
        previewHorizontalHeightDp = corner.horizontalEdgeHeightDp
    }

    fun pushZonePreview() {
        onZonePreviewDimensionsChange(
            previewVerticalWidthDp,
            previewVerticalHeightDp,
            previewHorizontalWidthDp,
            previewHorizontalHeightDp
        )
    }

    DisposableEffect(Unit) {
        onDispose { onZonePreviewStop() }
    }

    val triggerSectionTitle = stringResource(R.string.corner_gesture_trigger_section)
    val zonePreviewHint = stringResource(R.string.corner_gesture_zone_preview_hint)
    val behaviorSectionTitle = stringResource(R.string.corner_gesture_behavior_section)
    val appearanceSectionTitle = stringResource(R.string.corner_gesture_appearance_section)

    SettingsScreenScaffold(
        title = stringResource(R.string.corner_gesture_interaction_appearance_title),
        subtitle = stringResource(R.string.corner_gesture_interaction_appearance_desc),
        onBack = onBack
    ) {
        settingsLazySmallTitle(
            key = "corner-trigger-section",
            title = triggerSectionTitle
        )
        settingsLazyTipCard(key = "corner-zone-preview-hint",
            text = zonePreviewHint
        )
        groupedCardItems(
            keyPrefix = "corner-trigger",
            items = buildList {
                add(
                    settingsCardScopeItem("vertical-edge-height") {
                        SettingsSliderRow(
                            title = stringResource(R.string.corner_gesture_vertical_edge_height),
                            value = corner.verticalEdgeHeightDp,
                            valueRange = 0f..200f,
                            steps = 31,
                            enabled = zoneControlsEnabled,
                            label = "",
                            formatLabel = { "${it.roundToInt()} dp" },
                            triggersLayoutPreview = true,
                            onLayoutPreviewStart = onZonePreviewStart,
                            onLayoutPreviewStop = onZonePreviewStop,
                            onLayoutPreviewValueChange = { value ->
                                previewVerticalHeightDp = value
                                pushZonePreview()
                            },
                            onValueChange = onVerticalEdgeHeightChange
                        )
                    }
                )
                add(
                    settingsCardScopeItem("vertical-edge-width") {
                        SettingsSliderRow(
                            title = stringResource(R.string.corner_gesture_vertical_edge_width),
                            value = corner.verticalEdgeWidthDp,
                            valueRange = 0f..120f,
                            steps = 21,
                            enabled = zoneControlsEnabled,
                            label = "",
                            formatLabel = { "${it.roundToInt()} dp" },
                            triggersLayoutPreview = true,
                            onLayoutPreviewStart = onZonePreviewStart,
                            onLayoutPreviewStop = onZonePreviewStop,
                            onLayoutPreviewValueChange = { value ->
                                previewVerticalWidthDp = value
                                pushZonePreview()
                            },
                            onValueChange = onVerticalEdgeWidthChange
                        )
                    }
                )
                add(
                    settingsCardScopeItem("horizontal-edge-height") {
                        SettingsSliderRow(
                            title = stringResource(R.string.corner_gesture_horizontal_edge_height),
                            value = corner.horizontalEdgeHeightDp,
                            valueRange = 0f..160f,
                            steps = 23,
                            enabled = zoneControlsEnabled,
                            label = "",
                            formatLabel = { "${it.roundToInt()} dp" },
                            triggersLayoutPreview = true,
                            onLayoutPreviewStart = onZonePreviewStart,
                            onLayoutPreviewStop = onZonePreviewStop,
                            onLayoutPreviewValueChange = { value ->
                                previewHorizontalHeightDp = value
                                pushZonePreview()
                            },
                            onValueChange = onHorizontalEdgeHeightChange
                        )
                    }
                )
                add(
                    settingsCardScopeItem("horizontal-edge-width") {
                        SettingsSliderRow(
                            title = stringResource(R.string.corner_gesture_horizontal_edge_width),
                            value = corner.horizontalEdgeWidthDp,
                            valueRange = 0f..160f,
                            steps = 31,
                            enabled = zoneControlsEnabled,
                            label = "",
                            formatLabel = { "${it.roundToInt()} dp" },
                            triggersLayoutPreview = true,
                            onLayoutPreviewStart = onZonePreviewStart,
                            onLayoutPreviewStop = onZonePreviewStop,
                            onLayoutPreviewValueChange = { value ->
                                previewHorizontalWidthDp = value
                                pushZonePreview()
                            },
                            onValueChange = onHorizontalEdgeWidthChange
                        )
                    }
                )
                add(
                    settingsCardScopeItem("trigger-slop") {
                        SettingsSliderRow(
                            title = stringResource(R.string.corner_gesture_trigger_slop),
                            value = corner.triggerSlopDp,
                            valueRange = 24f..96f,
                            steps = 17,
                            enabled = serviceEnabled && corner.enabled,
                            label = stringResource(
                                R.string.corner_gesture_zone_dp_value,
                                corner.triggerSlopDp.roundToInt()
                            ),
                            onValueChange = onTriggerSlopChange
                        )
                    }
                )
                add(
                    settingsCardScopeItem("inner-diameter") {
                        SettingsSliderRow(
                            title = stringResource(R.string.corner_gesture_inner_diameter),
                            value = corner.innerDiameterDp,
                            valueRange = CornerGestureSettings.INNER_DIAMETER_RANGE_DP,
                            // 固定 5dp 一档；量程不再跟着别的设置变。
                            steps = (
                                (CornerGestureSettings.INNER_DIAMETER_RANGE_DP.endInclusive -
                                    CornerGestureSettings.INNER_DIAMETER_RANGE_DP.start) / 5f
                                ).roundToInt() - 1,
                            enabled = serviceEnabled && corner.enabled,
                            label = stringResource(
                                R.string.corner_gesture_zone_dp_value,
                                corner.innerDiameterDp.roundToInt()
                            ),
                            onValueChange = onInnerDiameterChange
                        )
                    }
                )
                add(
                    settingsCardScopeItem("ring-spacing") {
                        // 下限 = 气泡直径：小于它相邻两环必然重叠。
                        val spacingMinDp = (corner.bubbleSizeDp * 2f)
                            .coerceAtMost(CornerGestureSettings.MAX_RING_SPACING_DP)
                        SettingsSliderRow(
                            title = stringResource(R.string.corner_gesture_ring_spacing),
                            value = corner.ringSpacingDp.coerceIn(
                                spacingMinDp,
                                CornerGestureSettings.MAX_RING_SPACING_DP,
                            ),
                            valueRange = spacingMinDp..CornerGestureSettings.MAX_RING_SPACING_DP,
                            // 1dp 一档：层间距是精调项，5dp 档对不齐 58dp 这类值。
                            steps = (CornerGestureSettings.MAX_RING_SPACING_DP - spacingMinDp).roundToInt().coerceAtLeast(1) - 1,
                            enabled = serviceEnabled && corner.enabled,
                            // 顺带把推导出来的轮盘外沿显示出来，尺寸不再靠猜。
                            label = stringResource(
                                R.string.corner_gesture_ring_spacing_value,
                                corner.ringSpacingDp.roundToInt(),
                                CornerWheelLayout.wheelOuterEdgeDp(corner).roundToInt(),
                            ),
                            onValueChange = onRingSpacingChange
                        )
                    }
                )
                add(
                    settingsCardScopeItem("bubble-size") {
                        SettingsSliderRow(
                            title = stringResource(R.string.corner_gesture_bubble_size),
                            value = corner.bubbleSizeDp,
                            valueRange = 12f..28f,
                            steps = 15,
                            enabled = serviceEnabled && corner.enabled,
                            label = stringResource(
                                R.string.corner_gesture_zone_dp_value,
                                corner.bubbleSizeDp.roundToInt()
                            ),
                            onValueChange = onBubbleSizeChange
                        )
                    }
                )
            }
        )
        settingsLazySmallTitle(
            key = "corner-behavior-section",
            title = behaviorSectionTitle
        )
        groupedCardItems(
            keyPrefix = "corner-behavior",
            items = buildList {
                add(
                    settingsCardScopeItem("hide-landscape") {
                        SettingSwitchRow(
                            title = stringResource(R.string.corner_gesture_hide_landscape),
                            subtitle = stringResource(R.string.corner_gesture_hide_landscape_desc),
                            checked = corner.hideInLandscape,
                            enabled = serviceEnabled && corner.enabled,
                            onCheckedChange = onHideInLandscapeChange
                        )
                    }
                )
                add(
                    settingsCardScopeItem("landscape-prevent-false-touch") {
                        SettingSwitchRow(
                            title = stringResource(R.string.corner_gesture_landscape_prevent_false_touch),
                            subtitle = stringResource(R.string.corner_gesture_landscape_prevent_false_touch_desc),
                            checked = corner.landscapePreventFalseTouch,
                            enabled = serviceEnabled && corner.enabled,
                            onCheckedChange = onLandscapePreventFalseTouchChange
                        )
                    }
                )
                add(
                    settingsCardScopeItem("override-system-nav") {
                        SettingSwitchRow(
                            title = stringResource(R.string.corner_gesture_override_system_nav),
                            subtitle = stringResource(R.string.corner_gesture_override_system_nav_desc),
                            checked = corner.overrideSystemNav,
                            enabled = serviceEnabled && corner.enabled,
                            onCheckedChange = onOverrideSystemNavChange
                        )
                    }
                )
                add(
                    settingsCardScopeItem("cancel-outside-wheel") {
                        SettingSwitchRow(
                            title = stringResource(R.string.corner_gesture_cancel_outside_wheel),
                            subtitle = stringResource(R.string.corner_gesture_cancel_outside_wheel_desc),
                            checked = corner.cancelOutsideWheel,
                            enabled = serviceEnabled && corner.enabled,
                            onCheckedChange = onCancelOutsideWheelChange
                        )
                    }
                )
            }
        )
        settingsLazySmallTitle(
            key = "corner-appearance-section",
            title = appearanceSectionTitle
        )
        groupedCardItems(
            keyPrefix = "corner-appearance",
            items = buildList {
                add(
                    settingsCardScopeItem("slot-icon-shape") {
                        val shapes = remember {
                            listOf(
                                FvIconShape.ROUNDED_RECT to R.string.fv_icon_shape_rounded_rect,
                                FvIconShape.CIRCLE to R.string.fv_icon_shape_circle,
                                FvIconShape.SQUIRCLE to R.string.fv_icon_shape_squircle,
                                FvIconShape.SQUARE to R.string.fv_icon_shape_square,
                            )
                        }
                        SettingSpinnerRow(
                            title = stringResource(R.string.corner_gesture_slot_icon_shape),
                            subtitle = stringResource(
                                shapes.firstOrNull { it.first == corner.slotIconShape }?.second
                                    ?: R.string.fv_icon_shape_circle,
                            ),
                            dialogButtonText = stringResource(R.string.cancel),
                            items = shapes.map { DropdownItem(text = stringResource(it.second)) },
                            selectedIndex = shapes.indexOfFirst { it.first == corner.slotIconShape }
                                .coerceAtLeast(0),
                            enabled = serviceEnabled && corner.enabled,
                            onSelectedIndexChange = { index ->
                                shapes.getOrNull(index)?.first?.let(onSlotIconShapeChange)
                            },
                        )
                    }
                )
                add(
                    settingsCardScopeItem("progressive-layers") {
                        SettingSwitchRow(
                            title = stringResource(R.string.corner_gesture_progressive_layers),
                            subtitle = stringResource(R.string.corner_gesture_progressive_layers_desc),
                            checked = corner.progressiveLayers,
                            enabled = serviceEnabled && corner.enabled,
                            onCheckedChange = onProgressiveLayersChange
                        )
                    }
                )
                add(
                    settingsCardScopeItem("show-selected-name") {
                        SettingExpandableSwitchRow(
                            title = stringResource(R.string.corner_gesture_show_selected_name),
                            subtitle = stringResource(R.string.corner_gesture_show_selected_name_desc),
                            checked = corner.showSelectedName,
                            enabled = serviceEnabled && corner.enabled,
                            onCheckedChange = onShowSelectedNameChange,
                            expandedContent = {
                                SettingsSliderRow(
                                    title = stringResource(R.string.corner_gesture_selected_hint_icon_size),
                                    value = corner.selectedHintIconSizeDp.toFloat(),
                                    valueRange = SelectedHintMetrics.MIN_ICON_SIZE_DP.toFloat()..
                                        SelectedHintMetrics.MAX_ICON_SIZE_DP.toFloat(),
                                    steps = SelectedHintMetrics.MAX_ICON_SIZE_DP - SelectedHintMetrics.MIN_ICON_SIZE_DP - 1,
                                    enabled = serviceEnabled && corner.enabled && corner.showSelectedName,
                                    label = stringResource(
                                        R.string.selected_hint_icon_size_value,
                                        corner.selectedHintIconSizeDp
                                    ),
                                    onValueChange = { onSelectedHintIconSizeChange(it.roundToInt()) }
                                )
                            }
                        )
                    }
                )
                add(
                    settingsCardScopeItem("show-edit-button") {
                        SettingSwitchRow(
                            title = stringResource(R.string.corner_gesture_show_edit_button),
                            subtitle = stringResource(R.string.corner_gesture_show_edit_button_desc),
                            checked = corner.showEditButton,
                            enabled = serviceEnabled && corner.enabled,
                            onCheckedChange = onShowEditButtonChange,
                        )
                    }
                )
                val backgroundStyles = listOf(
                    CornerGestureSettings.BACKGROUND_NONE,
                    CornerGestureSettings.BACKGROUND_BLUR,
                    CornerGestureSettings.BACKGROUND_BLACK
                )
                add(
                    settingsCardScopeItem("background-style") {
                        SettingDropdownRow(
                            title = stringResource(R.string.corner_gesture_background_style),
                            items = listOf(
                                stringResource(R.string.corner_gesture_background_none),
                                stringResource(R.string.honeycomb_background_blur),
                                stringResource(R.string.honeycomb_background_black)
                            ),
                            selectedIndex = backgroundStyles.indexOf(localBackgroundStyle).coerceAtLeast(0),
                            enabled = serviceEnabled && corner.enabled,
                            onSelectedIndexChange = { index ->
                                val style = backgroundStyles[index]
                                localBackgroundStyle = style
                                onBackgroundStyleChange(style)
                            }
                        )
                    }
                )
                // 始终注册行，避免 Lazy 条件增减导致切背景后滑条不出现。
                add(
                    settingsCardScopeItem("blur-strength") {
                        SettingsSliderRow(
                            title = stringResource(R.string.honeycomb_blur_strength),
                            value = localBlurDp.toFloat(),
                            valueRange = CornerGestureSettings.MIN_BLUR_DP.toFloat()..
                                CornerGestureSettings.MAX_BLUR_DP.toFloat(),
                            steps = 16,
                            enabled = serviceEnabled && corner.enabled
                                && localBackgroundStyle == CornerGestureSettings.BACKGROUND_BLUR,
                            label = stringResource(R.string.corner_gesture_zone_dp_value, localBlurDp),
                            onValueChange = {
                                val v = it.roundToInt()
                                localBlurDp = v
                                onBlurDpChange(v)
                            }
                        )
                    }
                )
                add(
                    settingsCardScopeItem("dim-percent") {
                        SettingsSliderRow(
                            title = stringResource(R.string.honeycomb_dim_percent),
                            value = localDimPercent.toFloat(),
                            valueRange = CornerGestureSettings.MIN_DIM_PERCENT.toFloat()..
                                CornerGestureSettings.MAX_DIM_PERCENT.toFloat(),
                            steps = 12,
                            enabled = serviceEnabled && corner.enabled
                                && localBackgroundStyle != CornerGestureSettings.BACKGROUND_NONE,
                            label = stringResource(R.string.floating_pointer_percent_value, localDimPercent),
                            onValueChange = {
                                val v = it.roundToInt()
                                localDimPercent = v
                                onDimPercentChange(v)
                            }
                        )
                    }
                )
            },
        )
    }
}
