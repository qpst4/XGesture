package com.slideindex.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatingPointerEdgeBar
import com.slideindex.app.settings.FloatingPointerEdgeSide
import com.slideindex.app.ui.animationstyle.AnimationStyleColorPickerDialog
import com.slideindex.app.ui.animationstyle.AnimationStyleColorRow
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import com.slideindex.app.ui.settings.components.settingsLazyTipCard
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FloatingPointerEdgeActionsSettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onThresholdChange: (Float) -> Unit,
    onPreviewSensitivityChange: (Int) -> Unit,
    onPreviewGlowSizeChange: (Int) -> Unit,
    onPreviewShowIconChange: (Boolean) -> Unit,
    onVisualColorChange: (Int) -> Unit,
    onOpenSideSettings: (FloatingPointerEdgeSide) -> Unit,
    onResetDefaults: () -> Unit
) {
    var showColorPicker by remember { mutableStateOf(false) }

    if (showColorPicker) {
        AnimationStyleColorPickerDialog(
            initialColor = settings.floatingPointerEdgeVisualColorArgb,
            onDismissRequest = { showColorPicker = false },
            onColorPicked = {
                onVisualColorChange(it)
                showColorPicker = false
            }
        )
    }

    val generalSectionTitle = stringResource(R.string.floating_pointer_edge_section_general)
    val sidesSectionTitle = stringResource(R.string.floating_pointer_edge_section_sides)
    val edgeSummary = stringResource(R.string.floating_pointer_edge_settings_summary)

    Box(modifier = Modifier.fillMaxSize()) {
        SettingsScreenScaffold(
            title = stringResource(R.string.floating_pointer_edge_settings_title),
            onBack = onBack
        ) {
            settingsLazyTipCard(
                key = "fp-edge-summary",
                text = edgeSummary,
            )
            settingsLazySmallTitle(
                key = "edge-general-section",
                title = generalSectionTitle
            )
            groupedCardItems(
                keyPrefix = "fp-edge-general",
                items = buildList {
                    add(
                        settingsCardScopeItem("threshold") {
                            SettingsSliderRow(
                                title = stringResource(R.string.floating_pointer_edge_threshold),
                                value = settings.floatingPointerEdgeThresholdDp,
                                valueRange = 5f..160f,
                                steps = 30,
                                enabled = true,
                                label = stringResource(
                                    R.string.floating_pointer_edge_threshold_value,
                                    settings.floatingPointerEdgeThresholdDp.roundToInt()
                                ),
                                onValueChange = onThresholdChange
                            )
                        }
                    )
                    add(
                        settingsCardScopeItem("preview-sensitivity") {
                            SettingsSliderRow(
                                title = stringResource(R.string.floating_pointer_edge_preview_sensitivity),
                                value = settings.floatingPointerEdgePreviewSensitivity.toFloat(),
                                valueRange = 0f..5f,
                                steps = 4,
                                enabled = true,
                                label = edgePreviewSensitivityLabel(settings.floatingPointerEdgePreviewSensitivity),
                                onValueChange = { onPreviewSensitivityChange(it.roundToInt()) }
                            )
                        }
                    )
                    add(
                        settingsCardScopeItem("preview-glow") {
                            SettingsSliderRow(
                                title = stringResource(R.string.floating_pointer_edge_preview_glow_size),
                                value = settings.floatingPointerEdgePreviewGlowSize.toFloat(),
                                valueRange = 0f..7f,
                                steps = 6,
                                enabled = true,
                                label = edgePreviewGlowLabel(settings.floatingPointerEdgePreviewGlowSize),
                                onValueChange = { onPreviewGlowSizeChange(it.roundToInt()) }
                            )
                        }
                    )
                    add(
                        settingsCardScopeItem("preview-icon") {
                            SettingSwitchRow(
                                title = stringResource(R.string.floating_pointer_edge_preview_show_icon),
                                subtitle = stringResource(R.string.floating_pointer_edge_preview_show_icon_desc),
                                checked = settings.floatingPointerEdgePreviewShowIcon,
                                enabled = true,
                                onCheckedChange = onPreviewShowIconChange
                            )
                        }
                    )
                    add(
                        settingsCardScopeItem("visual-color") {
                            AnimationStyleColorRow(
                                title = stringResource(R.string.floating_pointer_edge_visual_color),
                                color = settings.floatingPointerEdgeVisualColorArgb,
                                onClick = { showColorPicker = true }
                            )
                        }
                    )
                }
            )

            settingsLazySmallTitle(
                key = "edge-sides-section",
                title = sidesSectionTitle
            )
            groupedCardItems(
                keyPrefix = "fp-edge-sides",
                items = buildList {
                    FloatingPointerEdgeSide.entries.forEach { side ->
                        val bar = settings.floatingPointerEdgeActionsConfig.bar(side)
                        add(
                            settingsCardScopeItem("side-${side.name}") {
                                SettingNavigationRow(
                                    icon = { label -> Icon(edgeSideIcon(side), contentDescription = label) },
                                    title = edgeSideTitle(side),
                                    subtitle = edgeSideSummary(bar),
                                    onClick = { onOpenSideSettings(side) }
                                )
                            }
                        )
                    }
                }
            )
            groupedCardItems(
                keyPrefix = "fp-edge-reset",
                items = buildList {
                    add(
                        settingsCardScopeItem("reset") {
                            SettingNavigationRow(
                                icon = { label -> SymbolsIcon("Restore", contentDescription = label) },
                                title = stringResource(R.string.floating_pointer_edge_reset_defaults),
                                subtitle = stringResource(R.string.floating_pointer_edge_reset_defaults_desc),
                                onClick = onResetDefaults
                            )
                        }
                    )
                }
            )
        }
    }
}

@Composable
internal fun edgeSideTitle(side: FloatingPointerEdgeSide): String = when (side) {
    FloatingPointerEdgeSide.TOP -> stringResource(R.string.floating_pointer_edge_side_top)
    FloatingPointerEdgeSide.LEFT -> stringResource(R.string.floating_pointer_edge_side_left)
    FloatingPointerEdgeSide.RIGHT -> stringResource(R.string.floating_pointer_edge_side_right)
    FloatingPointerEdgeSide.BOTTOM -> stringResource(R.string.floating_pointer_edge_side_bottom)
}

internal fun edgeSideIcon(side: FloatingPointerEdgeSide): ImageVector = when (side) {
    FloatingPointerEdgeSide.TOP -> Icons.Default.KeyboardArrowUp
    FloatingPointerEdgeSide.LEFT -> Icons.AutoMirrored.Filled.KeyboardArrowLeft
    FloatingPointerEdgeSide.RIGHT -> Icons.AutoMirrored.Filled.KeyboardArrowRight
    FloatingPointerEdgeSide.BOTTOM -> Icons.Default.KeyboardArrowDown
}

@Composable
internal fun edgeSideSummary(bar: FloatingPointerEdgeBar): String =
    edgeSideSummary(bar.layoutSlots().size, bar.enabled)

@Composable
internal fun edgeSideSummary(zoneCount: Int, enabled: Boolean): String {
    val status = if (enabled) {
        stringResource(R.string.floating_pointer_edge_side_summary_enabled)
    } else {
        stringResource(R.string.floating_pointer_edge_side_summary_disabled)
    }
    return pluralStringResource(
        R.plurals.floating_pointer_edge_side_summary,
        zoneCount,
        zoneCount,
        status
    )
}

@Composable
internal fun edgePreviewSensitivityLabel(value: Int): String = when (value) {
    0 -> stringResource(R.string.floating_pointer_edge_preview_off)
    1 -> stringResource(R.string.floating_pointer_edge_preview_low)
    5 -> stringResource(R.string.floating_pointer_edge_preview_high)
    else -> stringResource(R.string.floating_pointer_edge_preview_medium)
}

@Composable
internal fun edgePreviewGlowLabel(value: Int): String = when (value) {
    0 -> stringResource(R.string.floating_pointer_edge_preview_off)
    1 -> stringResource(R.string.floating_pointer_edge_preview_low)
    7 -> stringResource(R.string.floating_pointer_edge_preview_high)
    else -> stringResource(R.string.floating_pointer_edge_preview_medium)
}
