package com.slideindex.app.ui

import top.yukonga.miuix.kmp.basic.SmallTitle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.launcher.cloneTargetUserId
import com.slideindex.app.overlay.CloneAppsIndex
import com.slideindex.app.overlay.FloatingPointerRadialMenuPreview
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatingPointerRadialMenuCodec
import com.slideindex.app.ui.animationstyle.AnimationStyleColorPickerDialog
import com.slideindex.app.ui.animationstyle.AnimationStyleColorRow
import com.slideindex.app.ui.miuix.MiuixScaffoldTabRowBottomContent
import com.slideindex.app.ui.miuix.MiuixTabRowWithContour
import com.slideindex.app.ui.settings.components.settingsCardItems
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffold
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.MiuixIcons

private enum class RadialMenuTab { Settings, Functions, Design }

private enum class RadialColorTarget {
    Outer,
    Inner,
    Divider,
    Icon,
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FloatingPointerRadialMenuSettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onAlwaysVisibleChange: (Boolean) -> Unit,
    onLongPressMsChange: (Int) -> Unit,
    onOpenLongPressActionPick: () -> Unit,
    onOpenSlotActionPick: (Int) -> Unit,
    onOpenShellCommand: (Int, String) -> Unit,
    onOpenSwipeConfig: (Int) -> Unit,
    onSlotActionChange: (Int, GestureAction) -> Unit,
    onOuterDiameterChange: (Float) -> Unit,
    onInnerDiameterChange: (Float) -> Unit,
    onOuterColorChange: (Int) -> Unit,
    onInnerColorChange: (Int) -> Unit,
    onDividerThicknessChange: (Float) -> Unit,
    onDividerColorChange: (Int) -> Unit,
    onIconSizeFractionChange: (Float) -> Unit,
    onIconColorChange: (Int) -> Unit,
    onResetDesignDefaults: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(RadialMenuTab.Settings) }
    var colorTarget by remember { mutableStateOf<RadialColorTarget?>(null) }
    var pickerInitialColor by remember { mutableIntStateOf(0) }
    var radialDesignPreviewDragging by remember { mutableStateOf(false) }
    var previewOuterDiameterPx by remember {
        mutableFloatStateOf(settings.floatingPointerRadialOuterDiameterPx)
    }
    var previewInnerDiameterPx by remember {
        mutableFloatStateOf(settings.floatingPointerRadialInnerDiameterPx)
    }
    var previewDividerThicknessPx by remember {
        mutableFloatStateOf(settings.floatingPointerRadialDividerThicknessPx)
    }
    var previewIconSizeFraction by remember {
        mutableFloatStateOf(settings.floatingPointerRadialIconSizeFraction)
    }

    LaunchedEffect(
        settings.floatingPointerRadialOuterDiameterPx,
        settings.floatingPointerRadialInnerDiameterPx,
        settings.floatingPointerRadialDividerThicknessPx,
        settings.floatingPointerRadialIconSizeFraction
    ) {
        if (!radialDesignPreviewDragging) {
            previewOuterDiameterPx = settings.floatingPointerRadialOuterDiameterPx
            previewInnerDiameterPx = settings.floatingPointerRadialInnerDiameterPx
            previewDividerThicknessPx = settings.floatingPointerRadialDividerThicknessPx
            previewIconSizeFraction = settings.floatingPointerRadialIconSizeFraction
        }
    }

    val previewSettings = settings.copy(
        floatingPointer = settings.floatingPointer.copy(
            floatingPointerRadialOuterDiameterPx = previewOuterDiameterPx,
            floatingPointerRadialInnerDiameterPx = previewInnerDiameterPx,
            floatingPointerRadialDividerThicknessPx = previewDividerThicknessPx,
            floatingPointerRadialIconSizeFraction = previewIconSizeFraction
        )
    )

    if (colorTarget != null) {
        AnimationStyleColorPickerDialog(
            initialColor = pickerInitialColor,
            onDismissRequest = { colorTarget = null },
            onColorPicked = { color ->
                when (colorTarget) {
                    RadialColorTarget.Outer -> onOuterColorChange(color)
                    RadialColorTarget.Inner -> onInnerColorChange(color)
                    RadialColorTarget.Divider -> onDividerColorChange(color)
                    RadialColorTarget.Icon -> onIconColorChange(color)
                    null -> Unit
                }
                colorTarget = null
            }
        )
    }

    val functionsSectionTitle = stringResource(R.string.floating_pointer_radial_functions_section)
    val resetDesignTitle = stringResource(R.string.floating_pointer_radial_reset_design)
    val previewSectionTitle = stringResource(R.string.floating_pointer_preview_section)

    val settingsTabCard = settingsCardItems(settings) {
        SettingSwitchRow(
            title = stringResource(R.string.floating_pointer_radial_always_visible),
            subtitle = stringResource(R.string.floating_pointer_radial_always_visible_desc),
            icon = { label ->
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = label
                )
            },
            checked = settings.floatingPointerRadialAlwaysVisible,
            enabled = true,
            onCheckedChange = onAlwaysVisibleChange
        )
        val longPressAction = settings.floatingPointerJoystickLongPressAction
        SettingNavigationRow(
            icon = { label ->
                GestureSlotActionIcon(
                    action = longPressAction,
                    settings = settings,
                    contentDescription = label,
                )
            },
            title = stringResource(R.string.floating_pointer_joystick_long_press_action),
            subtitle = radialSlotActionSubtitle(longPressAction),
            onClick = onOpenLongPressActionPick
        )
        SettingsSliderRow(
            title = stringResource(R.string.floating_pointer_radial_long_press_ms),
            value = settings.floatingPointerRadialLongPressMs.toFloat(),
            valueRange = 200f..2000f,
            steps = 17,
            enabled = true,
            label = stringResource(
                R.string.floating_pointer_radial_long_press_ms_value,
                settings.floatingPointerRadialLongPressMs
            ),
            onValueChange = { onLongPressMsChange(it.roundToInt()) }
        )
    }

    val functionsTabCard = settingsCardItems(settings.floatingPointerRadialSlotActions) {
        repeat(FloatingPointerRadialMenuCodec.SLOT_COUNT) { index ->
            val action = settings.floatingPointerRadialSlotActions.getOrElse(index) {
                GestureAction.None
            }
            SettingNavigationRow(
                icon = { label ->
                    GestureSlotActionIcon(
                        action = action,
                        settings = settings,
                        contentDescription = label,
                    )
                },
                title = radialSlotDirectionLabel(index),
                subtitle = radialSlotActionSubtitle(action),
                onClick = { onOpenSlotActionPick(index) },
                trailingContent = when (action) {
                    is GestureAction.SimulatePointerSwipe,
                    is GestureAction.ExecuteShellCommand -> {
                        {
                            IconButton(
                                onClick = {
                                    when (val current = action) {
                                        is GestureAction.SimulatePointerSwipe ->
                                            onOpenSwipeConfig(index)
                                        is GestureAction.ExecuteShellCommand ->
                                            onOpenShellCommand(index, current.command)
                                    }
                                }
                            ) {
                                Icon(
                                    MiuixIcons.Medium.Settings,
                                    contentDescription = stringResource(
                                        R.string.cd_radial_menu_settings
                                    ),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    else -> null
                }
            )
        }
    }

    val designTabCard = settingsCardItems(
        settings,
        radialDesignPreviewDragging,
        previewOuterDiameterPx,
        previewInnerDiameterPx,
        previewDividerThicknessPx,
        previewIconSizeFraction
    ) {
        SettingsSliderRow(
            title = stringResource(R.string.floating_pointer_radial_outer_size),
            value = settings.floatingPointerRadialOuterDiameterPx,
            valueRange = 240f..720f,
            steps = 23,
            enabled = true,
            label = stringResource(
                R.string.floating_pointer_size_px_value,
                settings.floatingPointerRadialOuterDiameterPx.roundToInt()
            ),
            triggersLayoutPreview = true,
            commitOnFinish = true,
            onLayoutPreviewStart = { radialDesignPreviewDragging = true },
            onLayoutPreviewStop = { radialDesignPreviewDragging = false },
            onLayoutPreviewValueChange = { previewOuterDiameterPx = it },
            onValueChange = onOuterDiameterChange
        )
        AnimationStyleColorRow(
            title = stringResource(R.string.floating_pointer_radial_outer_color),
            color = settings.floatingPointerRadialOuterColorArgb,
            enabled = true,
            onClick = {
                pickerInitialColor = settings.floatingPointerRadialOuterColorArgb
                colorTarget = RadialColorTarget.Outer
            }
        )
        SettingsSliderRow(
            title = stringResource(R.string.floating_pointer_radial_inner_size),
            value = settings.floatingPointerRadialInnerDiameterPx,
            valueRange = 80f..480f,
            steps = 19,
            enabled = true,
            label = stringResource(
                R.string.floating_pointer_size_px_value,
                settings.floatingPointerRadialInnerDiameterPx.roundToInt()
            ),
            triggersLayoutPreview = true,
            commitOnFinish = true,
            onLayoutPreviewStart = { radialDesignPreviewDragging = true },
            onLayoutPreviewStop = { radialDesignPreviewDragging = false },
            onLayoutPreviewValueChange = { previewInnerDiameterPx = it },
            onValueChange = onInnerDiameterChange
        )
        AnimationStyleColorRow(
            title = stringResource(R.string.floating_pointer_radial_inner_color),
            color = settings.floatingPointerRadialInnerColorArgb,
            enabled = true,
            onClick = {
                pickerInitialColor = settings.floatingPointerRadialInnerColorArgb
                colorTarget = RadialColorTarget.Inner
            }
        )
        SettingsSliderRow(
            title = stringResource(R.string.floating_pointer_radial_divider_thickness),
            value = settings.floatingPointerRadialDividerThicknessPx,
            valueRange = 1f..12f,
            steps = 10,
            enabled = true,
            label = stringResource(
                R.string.floating_pointer_size_px_value,
                settings.floatingPointerRadialDividerThicknessPx.roundToInt()
            ),
            triggersLayoutPreview = true,
            commitOnFinish = true,
            onLayoutPreviewStart = { radialDesignPreviewDragging = true },
            onLayoutPreviewStop = { radialDesignPreviewDragging = false },
            onLayoutPreviewValueChange = { previewDividerThicknessPx = it },
            onValueChange = onDividerThicknessChange
        )
        AnimationStyleColorRow(
            title = stringResource(R.string.floating_pointer_radial_divider_color),
            color = settings.floatingPointerRadialDividerColorArgb,
            enabled = true,
            onClick = {
                pickerInitialColor = settings.floatingPointerRadialDividerColorArgb
                colorTarget = RadialColorTarget.Divider
            }
        )
        SettingsSliderRow(
            title = stringResource(R.string.floating_pointer_radial_icon_size),
            value = settings.floatingPointerRadialIconSizeFraction,
            valueRange = 0.2f..0.9f,
            steps = 13,
            enabled = true,
            label = stringResource(
                R.string.floating_pointer_percent_value,
                (settings.floatingPointerRadialIconSizeFraction * 100).roundToInt()
            ),
            triggersLayoutPreview = true,
            commitOnFinish = true,
            onLayoutPreviewStart = { radialDesignPreviewDragging = true },
            onLayoutPreviewStop = { radialDesignPreviewDragging = false },
            onLayoutPreviewValueChange = { previewIconSizeFraction = it },
            onValueChange = onIconSizeFractionChange
        )
        AnimationStyleColorRow(
            title = stringResource(R.string.floating_pointer_radial_icon_color),
            color = settings.floatingPointerRadialIconColorArgb,
            enabled = true,
            onClick = {
                pickerInitialColor = settings.floatingPointerRadialIconColorArgb
                colorTarget = RadialColorTarget.Icon
            }
        )
    }

    val designResetCard = settingsCardItems {
        SettingLinkRow(
            title = resetDesignTitle,
            onClick = onResetDesignDefaults
        )
    }

    SettingsLazyScreenScaffold(
        title = stringResource(R.string.floating_pointer_radial_settings_title),
        onBack = onBack,
        modifier = Modifier.fillMaxSize(),
        bottomContent = {
            MiuixScaffoldTabRowBottomContent {
                MiuixTabRowWithContour(
                    tabs = RadialMenuTab.entries.map { tab ->
                        stringResource(
                            when (tab) {
                                RadialMenuTab.Settings -> R.string.floating_pointer_radial_tab_settings
                                RadialMenuTab.Functions -> R.string.floating_pointer_radial_tab_functions
                                RadialMenuTab.Design -> R.string.floating_pointer_radial_tab_design
                            }
                        )
                    },
                    selectedTabIndex = selectedTab.ordinal,
                    onTabSelected = { selectedTab = RadialMenuTab.entries[it] }
                )
            }
        }
    ) {
        item(key = "radial-body") {
        Column(modifier = Modifier.fillMaxWidth()) {
            when (selectedTab) {
                RadialMenuTab.Settings -> {
                    settingsTabCard.RenderRows()
                }

                RadialMenuTab.Functions -> {
                    SmallTitle(
                        functionsSectionTitle,
                        modifier = Modifier
                            .fillMaxWidth()
                            
                    )
                    functionsTabCard.RenderRows()
                }

                RadialMenuTab.Design -> {
                    designTabCard.RenderRows()
                    designResetCard.RenderRows()
                }
            }

            SmallTitle(
                previewSectionTitle,
                modifier = Modifier
                    .fillMaxWidth()
                    
            )
            Surface(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 4.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                FloatingPointerRadialMenuPreview(
                    settings = previewSettings,
                    slots = settings.floatingPointerRadialSlotActions,
                    highlightedSlot = 2
                )
            }
        }
        }
    }
}

@Composable
private fun radialSlotDirectionLabel(index: Int): String = when (index) {
    0 -> stringResource(R.string.floating_pointer_radial_slot_top)
    1 -> stringResource(R.string.floating_pointer_radial_slot_top_right)
    2 -> stringResource(R.string.floating_pointer_radial_slot_right)
    3 -> stringResource(R.string.floating_pointer_radial_slot_bottom_right)
    4 -> stringResource(R.string.floating_pointer_radial_slot_bottom)
    5 -> stringResource(R.string.floating_pointer_radial_slot_bottom_left)
    6 -> stringResource(R.string.floating_pointer_radial_slot_left)
    7 -> stringResource(R.string.floating_pointer_radial_slot_top_left)
    else -> stringResource(R.string.floating_pointer_radial_slot_top)
}

@Composable
private fun radialSlotActionSubtitle(action: GestureAction): String {
    val context = LocalContext.current
    val cloneUserId = action.cloneTargetUserId(context)
    val base = if (cloneUserId > 0) {
        CloneAppsIndex.labelForUser(context, cloneUserId, gestureActionLabel(action))
    } else {
        gestureActionLabel(action)
    }
    return if (action is GestureAction.SimulatePointerSwipe) {
        stringResource(R.string.pointer_swipe_action_summary, base)
    } else if (action is GestureAction.ExecuteShellCommand && action.command.isNotBlank()) {
        stringResource(
            R.string.gesture_action_execute_shell_command_named,
            gestureExecuteShellCommandPreview(action.command)
        )
    } else {
        base
    }
}
