package com.slideindex.app.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.overlay.ringlauncher.RingLauncherOverlayWindow
import com.slideindex.app.overlay.layout.FvIconShape
import com.slideindex.app.overlay.layout.PanelIconShape
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FvRingLauncherAxis
import com.slideindex.app.settings.FvRingLauncherAxisMergeDirection
import com.slideindex.app.settings.FvRingLauncherSettings
import com.slideindex.app.settings.fvRingLauncherFor
import com.slideindex.app.ui.miuix.MiuixConfirmDialog
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingLinkRow
import com.slideindex.app.ui.settings.components.SettingSwitchRow
import com.slideindex.app.ui.SettingDropdownRow
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.SettingsSliderRow
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import com.slideindex.app.util.PermissionHelper
import kotlin.math.roundToInt

private enum class RingLauncherSyncDialogTarget {
    APPEARANCE,
    SLOTS,
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RingLauncherSettingsScreen(
    appSettings: AppSettings,
    onBack: () -> Unit,
    onSettingsChange: (FvRingLauncherAxis, FvRingLauncherSettings) -> Unit,
    onLinkAppearanceAxesChange: (Boolean, FvRingLauncherAxis, FvRingLauncherAxisMergeDirection?) -> Unit,
    onLinkSlotAxesChange: (Boolean, FvRingLauncherAxis, FvRingLauncherAxisMergeDirection?) -> Unit,
) {
    val context = LocalContext.current
    var selectedAxis by remember { mutableStateOf(FvRingLauncherAxis.HORIZONTAL) }
    var pendingSyncTarget by remember { mutableStateOf<RingLauncherSyncDialogTarget?>(null) }

    val currentAxisSettings = appSettings.fvRingLauncherFor(selectedAxis)
    val linkAppearanceAxes = appSettings.fvRingLauncherLinkAppearanceAxes
    val linkSlotAxes = appSettings.fvRingLauncherLinkSlotAxes

    fun updateCurrentAxisSettings(transform: (FvRingLauncherSettings) -> FvRingLauncherSettings) {
        val next = transform(currentAxisSettings)
        onSettingsChange(selectedAxis, next)
    }

    val launchSectionTitle = stringResource(R.string.fv_ring_launcher_open_overlay)
    val syncSectionTitle = stringResource(R.string.fv_ring_launcher_link_merge_title)
    val appearanceSectionTitle = stringResource(R.string.fv_ring_launcher_appearance_title)

    SettingsScreenScaffold(
        title = stringResource(R.string.fv_ring_launcher_entry_title),
        pageHint = stringResource(R.string.fv_ring_launcher_entry_desc),
        onBack = onBack,
    ) {
        // 顶部切轴 SegmentedButton
        item(key = "axis-selector") {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                val axes = listOf(
                    FvRingLauncherAxis.HORIZONTAL to stringResource(R.string.fv_ring_launcher_axis_horizontal),
                    FvRingLauncherAxis.VERTICAL to stringResource(R.string.fv_ring_launcher_axis_vertical),
                )
                axes.forEachIndexed { index, (axis, label) ->
                    SegmentedButton(
                        selected = selectedAxis == axis,
                        onClick = { selectedAxis = axis },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = axes.size),
                    ) {
                        Text(label)
                    }
                }
            }
        }

        // 快捷交互卡片：调起悬浮窗
        settingsLazySmallTitle(
            key = "section-launch",
            title = launchSectionTitle,
        )
        groupedCardItems(
            keyPrefix = "app-switcher-launch",
            items = buildList {
                add(
                    settingsCardScopeItem("open-overlay") {
                        SettingLinkRow(
                            title = stringResource(R.string.fv_ring_launcher_open_overlay),
                            subtitle = stringResource(R.string.fv_ring_launcher_open_overlay_desc),
                            onClick = {
                                if (!PermissionHelper.isAccessibilityServiceEnabledForOverlays(context)) {
                                    Toast.makeText(
                                        context,
                                        R.string.fv_ring_launcher_accessibility_required,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                } else {
                                    val displayMetrics = context.resources.displayMetrics
                                    val anchorX = if (selectedAxis == FvRingLauncherAxis.VERTICAL) {
                                        displayMetrics.widthPixels / 2f
                                    } else {
                                        0f
                                    }
                                    val anchorY = displayMetrics.heightPixels / 2f
                                    RingLauncherOverlayWindow.show(
                                        context = context,
                                        settings = appSettings,
                                        anchorRawX = anchorX,
                                        anchorRawY = anchorY,
                                        externalTracking = false,
                                        onLaunch = { _, _ -> },
                                    )
                                }
                            },
                        )
                    },
                )
            },
        )

        // 轴联动同步开关
        settingsLazySmallTitle(
            key = "section-sync",
            title = syncSectionTitle,
        )
        groupedCardItems(
            keyPrefix = "app-switcher-sync",
            items = buildList {
                add(
                    settingsCardScopeItem("sync-appearance") {
                        SettingSwitchRow(
                            title = stringResource(R.string.fv_ring_launcher_link_appearance_axes_title),
                            subtitle = stringResource(R.string.fv_ring_launcher_link_appearance_axes_desc),
                            checked = linkAppearanceAxes,
                            enabled = true,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    pendingSyncTarget = RingLauncherSyncDialogTarget.APPEARANCE
                                } else {
                                    onLinkAppearanceAxesChange(false, selectedAxis, null)
                                }
                            },
                        )
                    },
                )
                add(
                    settingsCardScopeItem("sync-slots") {
                        SettingSwitchRow(
                            title = stringResource(R.string.fv_ring_launcher_link_slot_axes_title),
                            subtitle = stringResource(R.string.fv_ring_launcher_link_slot_axes_desc),
                            checked = linkSlotAxes,
                            enabled = true,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    pendingSyncTarget = RingLauncherSyncDialogTarget.SLOTS
                                } else {
                                    onLinkSlotAxesChange(false, selectedAxis, null)
                                }
                            },
                        )
                    },
                )
            },
        )

        // 外观与几何尺寸
        settingsLazySmallTitle(
            key = "section-appearance",
            title = appearanceSectionTitle,
        )
        groupedCardItems(
            keyPrefix = "app-switcher-appearance",
            items = buildList {
                // 显示圈数
                add(
                    settingsCardScopeItem("circle-count") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.fv_ring_launcher_circle_count_title),
                                fontWeight = FontWeight.Medium,
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                val circleLabels = listOf(
                                    stringResource(R.string.fv_ring_launcher_circle_1),
                                    stringResource(R.string.fv_ring_launcher_circle_2),
                                    stringResource(R.string.fv_ring_launcher_circle_3),
                                    stringResource(R.string.fv_ring_launcher_circle_4),
                                )
                                circleLabels.forEachIndexed { index, label ->
                                    val count = index + 1
                                    val isSelected = currentAxisSettings.circleCount == count
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { updateCurrentAxisSettings { it.copy(circleCount = count) } },
                                        label = {
                                            Text(
                                                text = label,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            )
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    },
                )

                // 图标形状
                add(
                    settingsCardScopeItem("icon-shape") {
                        val shapes = listOf(
                            PanelIconShape.FOLLOW_GLOBAL to stringResource(R.string.icon_shape_follow_global),
                            PanelIconShape.SQUIRCLE to stringResource(R.string.fv_icon_shape_squircle),
                            PanelIconShape.CIRCLE to stringResource(R.string.fv_icon_shape_circle),
                            PanelIconShape.ROUNDED_RECT to stringResource(R.string.fv_icon_shape_rounded_rect),
                            PanelIconShape.ORIGINAL to stringResource(R.string.fv_icon_shape_original),
                        )
                        SettingDropdownRow(
                            title = stringResource(R.string.fv_ring_launcher_icon_shape_title),
                            items = shapes.map { it.second },
                            selectedIndex = shapes.indexOfFirst { it.first == currentAxisSettings.iconShape }.coerceAtLeast(0),
                            onSelectedIndexChange = { index ->
                                val selected = shapes[index].first
                                updateCurrentAxisSettings { it.copy(iconShape = selected) }
                            },
                        )
                    },
                )

                // 图标大小
                add(
                    settingsCardScopeItem("icon-size") {
                        SettingsSliderRow(
                            title = stringResource(R.string.fv_ring_launcher_icon_size_title),
                            value = currentAxisSettings.iconSizeDp,
                            valueRange = FvRingLauncherSettings.MIN_ICON_SIZE_DP..FvRingLauncherSettings.MAX_ICON_SIZE_DP,
                            steps = ((FvRingLauncherSettings.MAX_ICON_SIZE_DP - FvRingLauncherSettings.MIN_ICON_SIZE_DP) / 2f).toInt() - 1,
                            enabled = true,
                            label = "${currentAxisSettings.iconSizeDp.toInt()} dp",
                            onValueChange = {
                                val stepped = (it / 2f).roundToInt() * 2f
                                updateCurrentAxisSettings { s -> s.copy(iconSizeDp = stepped) }
                            },
                        )
                    },
                )

                // 内圈起始半径
                add(
                    settingsCardScopeItem("base-radius") {
                        SettingsSliderRow(
                            title = stringResource(R.string.fv_ring_launcher_base_radius_title),
                            value = currentAxisSettings.baseRadiusDp,
                            valueRange = FvRingLauncherSettings.MIN_BASE_RADIUS_DP..FvRingLauncherSettings.MAX_BASE_RADIUS_DP,
                            steps = ((FvRingLauncherSettings.MAX_BASE_RADIUS_DP - FvRingLauncherSettings.MIN_BASE_RADIUS_DP) / 2f).toInt() - 1,
                            enabled = true,
                            label = "${currentAxisSettings.baseRadiusDp.toInt()} dp",
                            onValueChange = {
                                val stepped = (it / 2f).roundToInt() * 2f
                                updateCurrentAxisSettings { s -> s.copy(baseRadiusDp = stepped) }
                            },
                        )
                    },
                )

                // 同心圆环间距
                add(
                    settingsCardScopeItem("layer-gap") {
                        SettingsSliderRow(
                            title = stringResource(R.string.fv_ring_launcher_layer_gap_title),
                            value = currentAxisSettings.layerGapDp,
                            valueRange = FvRingLauncherSettings.MIN_LAYER_GAP_DP..FvRingLauncherSettings.MAX_LAYER_GAP_DP,
                            steps = ((FvRingLauncherSettings.MAX_LAYER_GAP_DP - FvRingLauncherSettings.MIN_LAYER_GAP_DP) / 2f).toInt() - 1,
                            enabled = true,
                            label = "${currentAxisSettings.layerGapDp.toInt()} dp",
                            onValueChange = {
                                val stepped = (it / 2f).roundToInt() * 2f
                                updateCurrentAxisSettings { s -> s.copy(layerGapDp = stepped) }
                            },
                        )
                    },
                )

                // 扇区边距
                add(
                    settingsCardScopeItem("end-margin") {
                        SettingsSliderRow(
                            title = stringResource(R.string.fv_ring_launcher_end_margin_title),
                            value = currentAxisSettings.endMarginDeg,
                            valueRange = FvRingLauncherSettings.MIN_END_MARGIN_DEG..FvRingLauncherSettings.MAX_END_MARGIN_DEG,
                            steps = ((FvRingLauncherSettings.MAX_END_MARGIN_DEG - FvRingLauncherSettings.MIN_END_MARGIN_DEG) / 2f).toInt() - 1,
                            enabled = true,
                            label = "${currentAxisSettings.endMarginDeg.toInt()}°",
                            onValueChange = {
                                val stepped = (it / 2f).roundToInt() * 2f
                                updateCurrentAxisSettings { s -> s.copy(endMarginDeg = stepped) }
                            },
                        )
                    },
                )

                // 显示操作工具栏
                add(
                    settingsCardScopeItem("show-toolbar") {
                        SettingSwitchRow(
                            title = stringResource(R.string.fv_ring_launcher_show_toolbar_title),
                            subtitle = stringResource(R.string.fv_ring_launcher_show_toolbar_desc),
                            checked = currentAxisSettings.showToolbar,
                            enabled = true,
                            onCheckedChange = { checked ->
                                updateCurrentAxisSettings { it.copy(showToolbar = checked) }
                            },
                        )
                    },
                )

                // 恢复默认外观
                add(
                    settingsCardScopeItem("reset-defaults") {
                        SettingLinkRow(
                            title = stringResource(R.string.fv_ring_launcher_reset_defaults),
                            onClick = {
                                updateCurrentAxisSettings { s ->
                                    FvRingLauncherSettings(
                                        circleCount = s.circleCount,
                                        slots = s.slots,
                                    )
                                }
                            },
                        )
                    },
                )
            },
        )
    }

    // 同步确认对话框
    pendingSyncTarget?.let { target ->
        val mergeKindLabel = stringResource(
            when (target) {
                RingLauncherSyncDialogTarget.APPEARANCE -> R.string.fv_ring_launcher_link_merge_appearance_kind
                RingLauncherSyncDialogTarget.SLOTS -> R.string.fv_ring_launcher_link_merge_slot_kind
            },
        )
        val currentAxisLabel = stringResource(
            if (selectedAxis == FvRingLauncherAxis.VERTICAL) {
                R.string.fv_ring_launcher_axis_vertical
            } else {
                R.string.fv_ring_launcher_axis_horizontal
            },
        )
        val otherAxisLabel = stringResource(
            if (selectedAxis == FvRingLauncherAxis.VERTICAL) {
                R.string.fv_ring_launcher_axis_horizontal
            } else {
                R.string.fv_ring_launcher_axis_vertical
            },
        )

        MiuixConfirmDialog(
            show = true,
            title = stringResource(R.string.fv_ring_launcher_link_merge_title),
            message = stringResource(
                R.string.fv_ring_launcher_link_merge_message,
                mergeKindLabel,
                currentAxisLabel,
                otherAxisLabel,
            ),
            confirmText = stringResource(R.string.fv_ring_launcher_link_merge_use_current),
            onConfirm = {
                when (target) {
                    RingLauncherSyncDialogTarget.APPEARANCE -> {
                        onLinkAppearanceAxesChange(true, selectedAxis, FvRingLauncherAxisMergeDirection.USE_CURRENT_AXIS)
                    }
                    RingLauncherSyncDialogTarget.SLOTS -> {
                        onLinkSlotAxesChange(true, selectedAxis, FvRingLauncherAxisMergeDirection.USE_CURRENT_AXIS)
                    }
                }
                pendingSyncTarget = null
            },
            secondaryConfirmText = stringResource(R.string.fv_ring_launcher_link_merge_use_other),
            onSecondaryConfirm = {
                when (target) {
                    RingLauncherSyncDialogTarget.APPEARANCE -> {
                        onLinkAppearanceAxesChange(true, selectedAxis, FvRingLauncherAxisMergeDirection.USE_OTHER_AXIS)
                    }
                    RingLauncherSyncDialogTarget.SLOTS -> {
                        onLinkSlotAxesChange(true, selectedAxis, FvRingLauncherAxisMergeDirection.USE_OTHER_AXIS)
                    }
                }
                pendingSyncTarget = null
            },
            onDismissRequest = { pendingSyncTarget = null },
        )
    }
}
