package com.slideindex.app.ui

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.CornerGestureSettings
import com.slideindex.app.settings.CornerRadialMenuCodec
import com.slideindex.app.ui.miuix.CardItem
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingSwitchRow
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.SettingsSliderRow
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazyTipCard
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CornerGestureSlotsSettingsScreen(
    settings: AppSettings,
    serviceEnabled: Boolean,
    onBack: () -> Unit,
    onUnifiedSlotsChange: (Boolean) -> Unit,
    onLayerCountChange: (Int) -> Unit,
    /** 每圈槽位数量（5 个数，各自独立、总数可变）。 */
    onLayerSlotCountsChange: (List<Int>) -> Unit = {},
    onOpenInnerZoneActionPick: () -> Unit,
    onOpenLeftSlotActionPick: (Int) -> Unit,
    onOpenRightSlotActionPick: (Int) -> Unit
) {
    val corner = settings.cornerGestureSettings
    val slotsEnabled = serviceEnabled && corner.enabled
    val slotsEnabledLeft = slotsEnabled && corner.leftEnabled
    val slotsEnabledRight = slotsEnabled && corner.rightEnabled

    val slotsSectionTitle = stringResource(R.string.corner_gesture_slots_section)
    val launchPolicyHint = stringResource(R.string.corner_gesture_launch_policy_hint)
    val leftSlotsSectionTitle = stringResource(R.string.corner_gesture_left_slots_section)
    val rightSlotsSectionTitle = stringResource(R.string.corner_gesture_right_slots_section)

    // 只展示已启用的层；层数由卡片里的档位决定。
    val layerCount = corner.enabledLayerCount
    val layerTitles = (0 until layerCount).map {
        stringResource(R.string.corner_gesture_layer_title, it + 1)
    }
    val unifiedLayerItems = (0 until layerCount).map {
        cornerLayerCardItems(
            it,
            corner.leftSlots,
            corner.normalizedLayerSlotCounts,
            settings,
            slotsEnabled,
            onOpenLeftSlotActionPick,
        )
    }
    val leftLayerItems = (0 until layerCount).map {
        cornerLayerCardItems(
            it,
            corner.leftSlots,
            corner.normalizedLayerSlotCounts,
            settings,
            slotsEnabledLeft,
            onOpenLeftSlotActionPick,
        )
    }
    val rightLayerItems = (0 until layerCount).map {
        cornerLayerCardItems(
            it,
            corner.rightSlots,
            corner.normalizedLayerSlotCounts,
            settings,
            slotsEnabledRight,
            onOpenRightSlotActionPick,
        )
    }

    SettingsScreenScaffold(
        title = stringResource(R.string.corner_gesture_slots_section),
        subtitle = stringResource(R.string.corner_gesture_slots_entry_desc),
        onBack = onBack
    ) {
        settingsLazySmallTitle(
            key = "corner-slots-section",
            title = slotsSectionTitle
        )
        settingsLazyTipCard(key = "corner-launch-policy-hint",
            text = launchPolicyHint
        )
        groupedCardItems(
            keyPrefix = "corner-unified-slots",
            items = buildList {
                add(
                    settingsCardScopeItem("unified-slots") {
                        SettingSwitchRow(
                            title = stringResource(R.string.corner_gesture_unified_slots),
                            subtitle = stringResource(R.string.corner_gesture_unified_slots_desc),
                            checked = corner.unifiedSlots,
                            enabled = serviceEnabled && corner.enabled,
                            onCheckedChange = onUnifiedSlotsChange
                        )
                    }
                )
                add(
                    settingsCardScopeItem("wheel-layer-count") {
                        SettingsSliderRow(
                            title = stringResource(R.string.corner_gesture_layer_count),
                            value = layerCount.toFloat(),
                            valueRange = CornerGestureSettings.LAYER_COUNT_RANGE.first.toFloat()..
                                CornerGestureSettings.LAYER_COUNT_RANGE.last.toFloat(),
                            steps = CornerGestureSettings.LAYER_COUNT_RANGE.count() - 2,
                            enabled = serviceEnabled && corner.enabled,
                            label = stringResource(R.string.corner_gesture_layer_count_value, layerCount),
                            snapValue = { it.roundToInt().toFloat() },
                            onValueChange = { onLayerCountChange(it.roundToInt()) }
                        )
                    }
                )
            }
        )
        val layerCounts = corner.normalizedLayerSlotCounts
        groupedCardItems(
            keyPrefix = "corner-layer-slot-counts",
            items = layerCounts.indices.map { layer ->
                settingsCardScopeItem("layer-slot-count-$layer") {
                    SettingsSliderRow(
                        title = stringResource(
                            R.string.corner_gesture_layer_slot_count,
                            layer + 1,
                        ),
                        value = layerCounts[layer].toFloat(),
                        valueRange = 1f..CornerRadialMenuCodec.MAX_LAYER_SLOT_COUNT.toFloat(),
                        steps = CornerRadialMenuCodec.MAX_LAYER_SLOT_COUNT - 2,
                        enabled = serviceEnabled && corner.enabled,
                        label = stringResource(
                            R.string.corner_gesture_layer_slot_count_value,
                            layerCounts[layer],
                            layerCounts.sum(),
                        ),
                        onValueChange = { value ->
                            val updated = layerCounts.toMutableList()
                            updated[layer] = value.roundToInt()
                            onLayerSlotCountsChange(
                                CornerRadialMenuCodec.normalizeLayerSlotCounts(updated),
                            )
                        },
                    )
                }
            },
        )
        groupedCardItems(
            keyPrefix = "corner-inner-zone",
            items = buildList {
                add(
                    settingsCardScopeItem("inner-zone-action") {
                        SettingNavigationRow(
                            icon = { label ->
                                GestureSlotActionIcon(
                                    action = corner.innerZoneAction,
                                    settings = settings,
                                    contentDescription = label,
                                )
                            },
                            title = stringResource(R.string.corner_gesture_inner_zone_action),
                            subtitle = gestureActionLabel(corner.innerZoneAction),
                            enabled = serviceEnabled && corner.enabled,
                            onClick = onOpenInnerZoneActionPick
                        )
                    }
                )
            }
        )

        if (corner.unifiedSlots) {
            emitCornerLayerSlots(
                keyPrefix = "corner-unified",
                layerTitles = layerTitles,
                layerItems = unifiedLayerItems
            )
        } else {
            settingsLazySmallTitle(
                key = "corner-left-slots-section",
                title = leftSlotsSectionTitle
            )
            emitCornerLayerSlots(
                keyPrefix = "corner-left",
                layerTitles = layerTitles,
                layerItems = leftLayerItems
            )
            settingsLazySmallTitle(
                key = "corner-right-slots-section",
                title = rightSlotsSectionTitle
            )
            emitCornerLayerSlots(
                keyPrefix = "corner-right",
                layerTitles = layerTitles,
                layerItems = rightLayerItems
            )
        }
    }
}

@Composable
private fun cornerLayerCardItems(
    layer: Int,
    slots: List<GestureAction>,
    layerSlotCounts: List<Int>,
    settings: AppSettings,
    enabled: Boolean,
    onOpenSlotActionPick: (Int) -> Unit,
): List<CardItem> {
    val start = CornerRadialMenuCodec.layerStartIndex(layer, layerSlotCounts)
    val count = CornerRadialMenuCodec.slotCountInLayer(layer, layerSlotCounts)
    return buildList {
        repeat(count) { offset ->
            val index = start + offset
            val action = slots.getOrElse(index) { GestureAction.None }
            add(
                settingsCardScopeItem("corner-slot-$index") {
                    SettingNavigationRow(
                        icon = { label ->
                            GestureSlotActionIcon(
                                action = action,
                                settings = settings,
                                contentDescription = label,
                            )
                        },
                        title = stringResource(R.string.corner_gesture_slot_title, index + 1),
                        subtitle = if (action is GestureAction.None) {
                            stringResource(R.string.corner_gesture_slot_empty)
                        } else {
                            gestureActionLabel(action)
                        },
                        enabled = enabled,
                        onClick = { onOpenSlotActionPick(index) }
                    )
                }
            )
        }
    }
}

private fun LazyListScope.emitCornerLayerSlots(
    keyPrefix: String,
    layerTitles: List<String>,
    layerItems: List<List<CardItem>>
) {
    layerItems.forEachIndexed { index, items ->
        settingsLazySmallTitle(
            key = "$keyPrefix-layer-$index-title",
            title = layerTitles.getOrElse(index) { "" },
        )
        groupedCardItems("$keyPrefix-layer-$index", items)
    }
}
