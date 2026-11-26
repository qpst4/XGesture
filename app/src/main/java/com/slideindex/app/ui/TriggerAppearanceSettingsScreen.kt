package com.slideindex.app.ui

import top.yukonga.miuix.kmp.basic.SmallTitle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import com.slideindex.app.ui.settings.components.settingsGroupedRowBackground
import top.yukonga.miuix.kmp.basic.BasicComponent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.gesture.SwipePathRecognizer
import com.slideindex.app.gesture.TriggerHandle
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.GestureHintStyle
import com.slideindex.app.settings.maxTriggerEdgeWidthDp
import com.slideindex.app.settings.primaryTriggerHandle
import com.slideindex.app.settings.triggerCollectionEntries
import com.slideindex.app.settings.triggerHandle
import com.slideindex.app.settings.triggerHandleEdgeWidthDp
import com.slideindex.app.settings.gestureHintStyle
import com.slideindex.app.ui.animationstyle.AnimationStylePreview
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingsCardRow
import com.slideindex.app.ui.settings.components.SettingsCardScope
import com.slideindex.app.ui.settings.components.LazySettingsItem
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazyHint
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import com.slideindex.app.util.SystemBackGestureConflictHelper
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TriggerAppearanceSettingsScreen(
    side: PanelSide,
    handleId: String,
    settings: AppSettings,
    layoutPreviewVisible: Boolean,
    serviceEnabled: Boolean,
    onBack: () -> Unit,
    onShortSwipeDistanceChange: (Float) -> Unit,
    onLongSwipeDistanceChange: (Float) -> Unit,
    onEdgeWidthChange: (Float) -> Unit,
    onTriggerVerticalRangeChange: (String, Float, Float) -> Unit,
    onAlignHandlesChange: (Boolean) -> Unit,
    onInterceptBackChange: (Boolean) -> Unit,
    onLimitInterceptLengthChange: (Boolean) -> Unit,
    onApplyBackGestureRecommendation: () -> Unit = {},
    onPreviewStart: () -> Unit = {},
    onPreviewStop: () -> Unit = {},
    onLayoutPreviewStart: () -> Unit,
    onLayoutPreviewStop: () -> Unit = {},
    onEdgeWidthPreviewChange: (Float) -> Unit = {},
    onTriggerVerticalRangePreviewChange: (Float, Float) -> Unit = { _, _ -> },
    onSwipeDistancePreviewStart: () -> Unit,
    onSwipeDistancePreviewStop: () -> Unit,
    onShortSwipeDistancePreviewChange: (Float) -> Unit = {},
    onLongSwipeDistancePreviewChange: (Float) -> Unit = {},
    onTriggerLayoutPreviewStop: () -> Unit = {},
    /**
     * 非 null = 正在编辑「按应用分档」里的某个档（issue #26 v2）。
     *
     * 本页里 [AppSettings.interceptSystemBackGesture] / [AppSettings.limitMaxInterceptLength]
     * 与"系统返回冲突"提示都**不在档快照里**（系统级、按全局生效，见 `GestureProfileSnapshot`
     * 的注释），档内一律隐藏并注明「全局设置，档内不适用」，绝不能在档里改到全局。
     */
    profileId: String? = null,
) {
    val profileScoped = profileId != null
    val pairIndex = settings.triggerCollectionEntries().indexOfFirst { it.handleId == handleId }.let {
        if (it >= 0) it + 1 else 1
    }
    val pairCount = settings.triggerCollectionEntries().size
    val selectedHandle = settings.triggerHandle(side, handleId)
        ?: settings.primaryTriggerHandle(side)
    val handleWidth = settings.triggerHandleEdgeWidthDp(side, handleId)
    val useFocusedPreview = (side.isHorizontalEdge && !profileScoped) || selectedHandle.passthrough
    val previewEnabled = serviceEnabled
    val showNativePreview = previewEnabled && !useFocusedPreview
    val showFocusedPreview = previewEnabled && useFocusedPreview
    val showSwipeDistanceHints = previewEnabled && !selectedHandle.passthrough
    var previewWidth by remember(side, handleId, handleWidth) { mutableStateOf<Float?>(null) }
    var previewSpan by remember(side, handleId, selectedHandle.topFraction, selectedHandle.bottomFraction) {
        mutableStateOf<ClosedFloatingPointRange<Float>?>(null)
    }
    var swipeDistances by remember(side, handleId) { mutableStateOf<Pair<Float, Float>?>(null) }
    val startSwipePreview = {
        if (showFocusedPreview) {
            swipeDistances = selectedHandle.shortSwipeDistanceDp to selectedHandle.longSwipeDistanceDp
        } else {
            onSwipeDistancePreviewStart()
        }
    }
    val stopSwipePreview = {
        swipeDistances = null
        if (showNativePreview) {
            onTriggerLayoutPreviewStop()
            onSwipeDistancePreviewStop()
        }
    }
    val maxHandleWidth = side.maxTriggerEdgeWidthDp()
    val pairSuffix = if (pairCount > 1) " · $pairIndex" else ""
    val context = LocalContext.current
    val showBackGestureConflict = !profileScoped && side == PanelSide.LEFT &&
        SystemBackGestureConflictHelper.hasPotentialConflict(settings, context)

    TriggerHandlePreviewLifecycle(
        enabled = previewEnabled,
        side = side,
        handleId = handleId,
        onPreviewStart = { _, _ -> onPreviewStart() },
        onPreviewStop = onPreviewStop,
    )

    val previewHint = stringResource(R.string.side_gestures_preview_hint)
    val handleSectionTitle = stringResource(R.string.side_gestures_handle_section)

    if (layoutPreviewVisible) {
        val focusedSettings = settings.takeIf { showFocusedPreview }
        FocusedSideTriggerLayoutPreview(focusedSettings, side, handleId, previewWidth, previewSpan, swipeDistances)
    }
    SettingsScreenScaffold(
        title = stringResource(R.string.trigger_appearance_title),
        subtitle = stringResource(R.string.trigger_appearance_desc) + pairSuffix,
        onBack = onBack,
    ) {
        if (showSwipeDistanceHints) {
            settingsLazyHint(key = "trigger-appearance-preview-hint", text = previewHint)
        }

        if (showBackGestureConflict) {
            LazySettingsItem(key = "back-gesture-conflict") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.system_back_conflict_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = stringResource(R.string.system_back_conflict_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    androidx.compose.material3.Button(
                        onClick = onApplyBackGestureRecommendation,
                        enabled = serviceEnabled,
                    ) {
                        Text(stringResource(R.string.system_back_conflict_apply))
                    }
                }
            }
        }

        settingsLazySmallTitle(key = "trigger-handle-section", title = handleSectionTitle)
        groupedCardItems(
            keyPrefix = "trigger-handle-settings",
            items = buildList {
                add(
                    settingsCardScopeItem("handle-width") {
                        SettingsSliderRow(
                            title = stringResource(R.string.handle_width),
                            value = handleWidth,
                            valueRange = TriggerHandle.MIN_EDGE_WIDTH_DP..maxHandleWidth,
                            enabled = serviceEnabled,
                            label = "${handleWidth.roundToInt()} dp",
                            startLabel = stringResource(R.string.handle_width_small),
                            endLabel = stringResource(R.string.handle_width_large),
                            triggersLayoutPreview = true,
                            onLayoutPreviewStart = { if (showNativePreview) onLayoutPreviewStart() },
                            onLayoutPreviewStop = {
                                previewWidth = null
                                if (showNativePreview) {
                                    onTriggerLayoutPreviewStop()
                                    onLayoutPreviewStop()
                                }
                            },
                            onLayoutPreviewValueChange = { width ->
                                if (selectedHandle.passthrough) previewWidth = width
                                if (showNativePreview) onEdgeWidthPreviewChange(width)
                            },
                            onValueChange = onEdgeWidthChange,
                        )
                    },
                )
                add(
                    settingsCardScopeItem("handle-span") {
                        SettingsRangeSliderRow(
                            title = if (side.isVerticalEdge) {
                                stringResource(R.string.handle_span_horizontal)
                            } else {
                                stringResource(R.string.handle_length)
                            },
                            values = selectedHandle.topFraction..selectedHandle.bottomFraction,
                            valueRange = 0f..1f,
                            startLabel = if (side.isVerticalEdge) {
                                stringResource(R.string.handle_span_horizontal_start)
                            } else {
                                stringResource(R.string.handle_length_small)
                            },
                            endLabel = if (side.isVerticalEdge) {
                                stringResource(R.string.handle_span_horizontal_end)
                            } else {
                                stringResource(R.string.handle_length_large)
                            },
                            enabled = serviceEnabled,
                            triggersLayoutPreview = true,
                            onLayoutPreviewStart = { if (showNativePreview) onLayoutPreviewStart() },
                            onLayoutPreviewStop = {
                                previewSpan = null
                                if (showNativePreview) {
                                    onTriggerLayoutPreviewStop()
                                    onLayoutPreviewStop()
                                }
                            },
                            onLayoutPreviewValueChange = { range ->
                                if (selectedHandle.passthrough) previewSpan = range
                                if (showNativePreview) {
                                    onTriggerVerticalRangePreviewChange(range.start, range.endInclusive)
                                }
                            },
                            onValueChange = { range ->
                                onTriggerVerticalRangeChange(handleId, range.start, range.endInclusive)
                            },
                        )
                    },
                )
                if (!selectedHandle.passthrough) add(
                    settingsCardScopeItem("short-swipe-distance") {
                        SettingsSliderRow(
                            title = stringResource(R.string.short_swipe_distance),
                            value = selectedHandle.shortSwipeDistanceDp,
                            valueRange = SwipePathRecognizer.SHORT_DISTANCE_MIN_DP..
                                SwipePathRecognizer.SHORT_DISTANCE_MAX_DP,
                            enabled = serviceEnabled,
                            label = "",
                            formatLabel = { "${it.roundToInt()} dp" },
                            startLabel = stringResource(R.string.swipe_distance_small),
                            endLabel = stringResource(R.string.swipe_distance_large),
                            triggersLayoutPreview = showSwipeDistanceHints,
                            onLayoutPreviewStart = startSwipePreview,
                            onLayoutPreviewStop = stopSwipePreview,
                            onLayoutPreviewValueChange = { distance ->
                                if (showNativePreview) onShortSwipeDistancePreviewChange(distance)
                                else swipeDistances = distance to selectedHandle.longSwipeDistanceDp
                            },
                            onValueChange = onShortSwipeDistanceChange,
                        )
                    },
                )
                if (!selectedHandle.passthrough) add(
                    settingsCardScopeItem("long-swipe-distance") {
                        SettingsSliderRow(
                            title = stringResource(R.string.long_swipe_distance),
                            value = selectedHandle.longSwipeDistanceDp,
                            valueRange = (selectedHandle.shortSwipeDistanceDp + 16f)
                                .coerceAtLeast(SwipePathRecognizer.LONG_DISTANCE_MIN_DP)..
                                SwipePathRecognizer.LONG_DISTANCE_MAX_DP,
                            enabled = serviceEnabled,
                            label = "",
                            formatLabel = { "${it.roundToInt()} dp" },
                            startLabel = stringResource(R.string.swipe_distance_small),
                            endLabel = stringResource(R.string.swipe_distance_large),
                            triggersLayoutPreview = showSwipeDistanceHints,
                            onLayoutPreviewStart = startSwipePreview,
                            onLayoutPreviewStop = stopSwipePreview,
                            onLayoutPreviewValueChange = { distance ->
                                if (showNativePreview) onLongSwipeDistancePreviewChange(distance)
                                else swipeDistances = selectedHandle.shortSwipeDistanceDp to distance
                            },
                            onValueChange = onLongSwipeDistanceChange,
                        )
                    },
                )
                if (side.isHorizontalEdge) {
                    add(
                        settingsCardScopeItem("align-handles") {
                            SettingSwitchRow(
                                title = stringResource(R.string.align_handles),
                                subtitle = stringResource(R.string.align_handles_desc),
                                checked = selectedHandle.alignOppositeSide,
                                enabled = serviceEnabled,
                                onCheckedChange = onAlignHandlesChange,
                            )
                        },
                    )
                    if (profileScoped) {
                        // 系统返回拦截是 LSPosed 输入层的事，按全局生效且不进档快照：档内只提示、不给改。
                        add(
                            settingsCardScopeItem("global-only-back-gesture") {
                                SettingSwitchRow(
                                    title = stringResource(R.string.intercept_system_back),
                                    subtitle = stringResource(R.string.gesture_app_profiles_global_only_hint),
                                    checked = settings.interceptSystemBackGesture,
                                    enabled = false,
                                    onCheckedChange = {},
                                )
                            },
                        )
                        add(
                            settingsCardScopeItem("global-only-limit-intercept-length") {
                                SettingSwitchRow(
                                    title = stringResource(R.string.limit_intercept_length),
                                    subtitle = stringResource(R.string.gesture_app_profiles_global_only_hint),
                                    checked = settings.limitMaxInterceptLength,
                                    enabled = false,
                                    onCheckedChange = {},
                                )
                            },
                        )
                    } else {
                        add(
                            settingsCardScopeItem("intercept-back") {
                                SettingSwitchRow(
                                    title = stringResource(R.string.intercept_system_back),
                                    subtitle = stringResource(R.string.intercept_system_back_desc),
                                    checked = settings.interceptSystemBackGesture,
                                    enabled = serviceEnabled,
                                    onCheckedChange = onInterceptBackChange,
                                )
                            },
                        )
                        add(
                            settingsCardScopeItem("limit-intercept-length") {
                                SettingSwitchRow(
                                    title = stringResource(R.string.limit_intercept_length),
                                    subtitle = stringResource(R.string.limit_intercept_length_desc),
                                    checked = settings.limitMaxInterceptLength,
                                    enabled = serviceEnabled && settings.interceptSystemBackGesture,
                                    onCheckedChange = onLimitInterceptLengthChange,
                                )
                            },
                        )
                    }
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsCardScope.GestureHintStyleRow(
    style: GestureHintStyle,
    selected: Boolean,
    enabled: Boolean,
    settings: AppSettings,
    onClick: () -> Unit,
) {
    val title = when (style) {
        GestureHintStyle.WAVE -> stringResource(R.string.gesture_hint_style_wave)
        GestureHintStyle.CAPSULE -> stringResource(R.string.gesture_hint_style_capsule)
        GestureHintStyle.BUBBLE -> stringResource(R.string.gesture_hint_style_bubble)
        GestureHintStyle.ANDROID -> stringResource(R.string.gesture_hint_style_android)
    }
    val subtitle = when (style) {
        GestureHintStyle.WAVE -> stringResource(R.string.gesture_hint_style_wave_desc)
        GestureHintStyle.CAPSULE -> stringResource(R.string.gesture_hint_style_capsule_desc)
        GestureHintStyle.BUBBLE -> stringResource(R.string.gesture_hint_style_bubble_desc)
        GestureHintStyle.ANDROID -> stringResource(R.string.gesture_hint_style_android_desc)
    }
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    }
    SettingsCardRow(key = style) { position ->
        BasicComponent(
            modifier = Modifier.settingsGroupedRowBackground(position.index, position.count),
            title = title,
            summary = subtitle,
            enabled = enabled,
            onClick = onClick,
            startAction = {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, borderColor, RoundedCornerShape(10.dp)),
                ) {
                    AnimationStylePreview(
                        style = style, modifier = Modifier.fillMaxSize(),
                    )
                }
            },
            endActions = {
                RadioButton(
                    selected = selected,
                    onClick = null,
                )
            },
        )
    }
}

@Composable
internal fun gestureHintStyleLabel(style: GestureHintStyle): String = when (style) {
    GestureHintStyle.WAVE -> stringResource(R.string.gesture_hint_style_wave)
    GestureHintStyle.CAPSULE -> stringResource(R.string.gesture_hint_style_capsule)
    GestureHintStyle.BUBBLE -> stringResource(R.string.gesture_hint_style_bubble)
    GestureHintStyle.ANDROID -> stringResource(R.string.gesture_hint_style_android)
}

@Composable
internal fun triggerAppearanceSummary(settings: AppSettings, side: PanelSide, handleId: String): String {
    val width = settings.triggerHandleEdgeWidthDp(side, handleId).roundToInt()
    return stringResource(R.string.trigger_appearance_summary, width)
}
