package com.slideindex.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.overlay.FloatingPointerDesignPreview
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatingPointerDesign
import com.slideindex.app.settings.FloatingPointerTrailType
import com.slideindex.app.ui.animationstyle.AnimationStyleColorPickerDialog
import com.slideindex.app.ui.animationstyle.AnimationStyleColorRow
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingExpandableSwitchRow
import com.slideindex.app.ui.settings.components.SettingSpinnerRow
import com.slideindex.app.ui.settings.components.SettingsCardRow
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsGroupedRowBackground
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.DropdownItem
import kotlin.math.roundToInt

private enum class PointerColorTarget {
    Ring,
    Fill,
    Dot,
    Trail,
    Ripple,
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FloatingPointerPointerSettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onPointerDiameterChange: (Float) -> Unit,
    onRingThicknessChange: (Float) -> Unit,
    onDotDiameterChange: (Float) -> Unit,
    onRingColorChange: (Int) -> Unit,
    onFillColorChange: (Int) -> Unit,
    onDotColorChange: (Int) -> Unit,
    onClickVisualFeedbackChange: (Boolean) -> Unit,
    onClickHapticChange: (Boolean) -> Unit,
    onRippleColorChange: (Int) -> Unit,
    onRippleSizeChange: (Float) -> Unit,
    onRippleDurationChange: (Int) -> Unit,
    onTrailTypeChange: (FloatingPointerTrailType) -> Unit,
    onTrailDurationChange: (Int) -> Unit,
    onTrailColorChange: (Int) -> Unit,
    onHideWhenReleasedChange: (Boolean) -> Unit,
    onPointerDesignChange: (FloatingPointerDesign) -> Unit,
    onResetVisualDefaults: () -> Unit
) {
    var colorTarget by remember { mutableStateOf<PointerColorTarget?>(null) }
    var pickerInitialColor by remember { mutableIntStateOf(0) }
    var pointerSizeDragging by remember { mutableStateOf(false) }
    var previewPointerDiameterPx by remember {
        mutableFloatStateOf(settings.floatingPointerPointerDiameterPx)
    }
    var previewRingThicknessPx by remember {
        mutableFloatStateOf(settings.floatingPointerRingThicknessPx)
    }
    var previewDotDiameterPx by remember {
        mutableFloatStateOf(settings.floatingPointerDotDiameterPx)
    }
    val density = LocalDensity.current.density
    val resources = LocalResources.current
    val formatPxDpLabel = remember(density, resources) {
        { px: Float ->
            resources.getString(
                R.string.floating_pointer_size_px_dp_value,
                px.roundToInt(),
                px / density
            )
        }
    }
    val selectedDesign = FloatingPointerDesign.fromId(settings.floatingPointerDesignId)
    val trailType = FloatingPointerTrailType.fromId(settings.floatingPointerTrailTypeId)

    LaunchedEffect(
        settings.floatingPointerPointerDiameterPx,
        settings.floatingPointerRingThicknessPx,
        settings.floatingPointerDotDiameterPx
    ) {
        if (!pointerSizeDragging) {
            previewPointerDiameterPx = settings.floatingPointerPointerDiameterPx
            previewRingThicknessPx = settings.floatingPointerRingThicknessPx
            previewDotDiameterPx = settings.floatingPointerDotDiameterPx
        }
    }

    val previewSettings = settings.copy(
        floatingPointer = settings.floatingPointer.copy(
            floatingPointerPointerDiameterPx = previewPointerDiameterPx,
            floatingPointerRingThicknessPx = previewRingThicknessPx,
            floatingPointerDotDiameterPx = previewDotDiameterPx
        )
    )

    @Composable
    fun pxDpLabel(px: Float): String = formatPxDpLabel(px)

    if (colorTarget != null) {
        AnimationStyleColorPickerDialog(
            initialColor = pickerInitialColor,
            onDismissRequest = { colorTarget = null },
            onColorPicked = { color ->
                when (colorTarget) {
                    PointerColorTarget.Ring -> onRingColorChange(color)
                    PointerColorTarget.Fill -> onFillColorChange(color)
                    PointerColorTarget.Dot -> onDotColorChange(color)
                    PointerColorTarget.Trail -> onTrailColorChange(color)
                    PointerColorTarget.Ripple -> onRippleColorChange(color)
                    null -> Unit
                }
                colorTarget = null
            }
        )
    }

    val designEntries = FloatingPointerDesign.entries
    val selectedDesignIndex = designEntries.indexOf(selectedDesign).coerceAtLeast(0)

    val previewSectionTitle = stringResource(R.string.floating_pointer_preview_section)
    val designSectionTitle = stringResource(R.string.floating_pointer_design_section)
    val pointerSectionTitle = stringResource(R.string.floating_pointer_settings_section_pointer)
    val visualFeedbackSectionTitle = stringResource(R.string.floating_pointer_visual_feedback_section)
    val trailSectionTitle = stringResource(R.string.floating_pointer_trail_section)
    val otherSectionTitle = stringResource(R.string.floating_pointer_settings_section_other)

    SettingsScreenScaffold(
        title = stringResource(R.string.floating_pointer_pointer_settings_title),
        onBack = onBack
    ) {
        settingsLazySmallTitle(key = "fp-preview-section", title = previewSectionTitle)
        item(key = "floating-pointer-preview") {
            Surface(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 4.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                FloatingPointerDesignPreview(settings = previewSettings)
            }
        }

        settingsLazySmallTitle(key = "fp-design-section", title = designSectionTitle)
        groupedCardItems(
            keyPrefix = "fp-pointer-design",
            items = buildList {
                add(
                    settingsCardScopeItem("design-spinner") {
                        SettingSpinnerRow(
                            title = stringResource(R.string.floating_pointer_design_section),
                            subtitle = stringResource(selectedDesign.labelResId),
                            dialogButtonText = stringResource(R.string.cancel),
                            items = designEntries.map { design ->
                                DropdownItem(
                                    text = stringResource(design.labelResId),
                                    icon = { iconModifier ->
                                        PointerDesignThumbnail(
                                            design = design,
                                            settings = settings,
                                            selected = design == selectedDesign,
                                            modifier = iconModifier
                                        )
                                    }
                                )
                            },
                            selectedIndex = selectedDesignIndex,
                            onSelectedIndexChange = { index -> onPointerDesignChange(designEntries[index]) }
                        )
                    }
                )
            }
        )

        settingsLazySmallTitle(key = "fp-pointer-section", title = pointerSectionTitle)
        groupedCardItems(
            keyPrefix = "fp-pointer-size",
            items = buildList {
                add(
                    settingsCardScopeItem("pointer-size") {
                        SettingsSliderRow(
                            title = stringResource(R.string.floating_pointer_pointer_size),
                            value = settings.floatingPointerPointerDiameterPx,
                            valueRange = 48f..120f,
                            steps = 17,
                            enabled = true,
                            label = stringResource(
                                R.string.floating_pointer_size_px_value,
                                settings.floatingPointerPointerDiameterPx.roundToInt()
                            ),
                            triggersLayoutPreview = true,
                            onLayoutPreviewStart = { pointerSizeDragging = true },
                            onLayoutPreviewStop = { pointerSizeDragging = false },
                            onLayoutPreviewValueChange = { previewPointerDiameterPx = it },
                            onValueChange = onPointerDiameterChange
                        )
                    }
                )
                if (selectedDesign.isRing) {
                    add(
                        settingsCardScopeItem("ring-thickness") {
                            SettingsSliderRow(
                                title = stringResource(R.string.floating_pointer_ring_thickness),
                                value = settings.floatingPointerRingThicknessPx,
                                valueRange = 4f..24f,
                                steps = 19,
                                enabled = true,
                                label = pxDpLabel(settings.floatingPointerRingThicknessPx),
                                formatLabel = formatPxDpLabel,
                                triggersLayoutPreview = true,
                                onLayoutPreviewStart = { pointerSizeDragging = true },
                                onLayoutPreviewStop = { pointerSizeDragging = false },
                                onLayoutPreviewValueChange = { previewRingThicknessPx = it },
                                onValueChange = onRingThicknessChange
                            )
                        }
                    )
                    add(
                        settingsCardScopeItem("dot-diameter") {
                            SettingsSliderRow(
                                title = stringResource(R.string.floating_pointer_dot_diameter),
                                value = settings.floatingPointerDotDiameterPx,
                                valueRange = 2f..24f,
                                steps = 21,
                                enabled = true,
                                label = pxDpLabel(settings.floatingPointerDotDiameterPx),
                                formatLabel = formatPxDpLabel,
                                triggersLayoutPreview = true,
                                onLayoutPreviewStart = { pointerSizeDragging = true },
                                onLayoutPreviewStop = { pointerSizeDragging = false },
                                onLayoutPreviewValueChange = { previewDotDiameterPx = it },
                                onValueChange = onDotDiameterChange
                            )
                        }
                    )
                    add(
                        settingsCardScopeItem("ring-color") {
                            AnimationStyleColorRow(
                                title = stringResource(R.string.floating_pointer_ring_color),
                                color = settings.floatingPointerRingColorArgb,
                                enabled = true,
                                onClick = {
                                    pickerInitialColor = settings.floatingPointerRingColorArgb
                                    colorTarget = PointerColorTarget.Ring
                                }
                            )
                        }
                    )
                    add(
                        settingsCardScopeItem("fill-color") {
                            AnimationStyleColorRow(
                                title = stringResource(R.string.floating_pointer_fill_color),
                                subtitle = stringResource(R.string.floating_pointer_fill_color_desc),
                                color = settings.floatingPointerFillColorArgb,
                                enabled = true,
                                onClick = {
                                    pickerInitialColor = settings.floatingPointerFillColorArgb
                                    colorTarget = PointerColorTarget.Fill
                                }
                            )
                        }
                    )
                    add(
                        settingsCardScopeItem("dot-color") {
                            AnimationStyleColorRow(
                                title = stringResource(R.string.floating_pointer_dot_color),
                                color = settings.floatingPointerDotColorArgb,
                                enabled = true,
                                onClick = {
                                    pickerInitialColor = settings.floatingPointerDotColorArgb
                                    colorTarget = PointerColorTarget.Dot
                                }
                            )
                        }
                    )
                }
            }
        )

        settingsLazySmallTitle(key = "fp-visual-feedback-section", title = visualFeedbackSectionTitle)
        groupedCardItems(
            keyPrefix = "fp-visual-feedback",
            items = buildList {
                add(
                    settingsCardScopeItem("click-haptic") {
                        SettingSwitchRow(
                            title = stringResource(R.string.floating_pointer_click_haptic),
                            subtitle = stringResource(R.string.floating_pointer_click_haptic_desc),
                            checked = settings.floatingPointerClickHapticEnabled,
                            enabled = true,
                            onCheckedChange = onClickHapticChange
                        )
                    }
                )
                add(
                    settingsCardScopeItem("click-visual-feedback") {
                        SettingExpandableSwitchRow(
                            title = stringResource(R.string.floating_pointer_click_visual_feedback),
                            subtitle = stringResource(R.string.floating_pointer_click_visual_feedback_desc),
                            checked = settings.floatingPointerClickVisualFeedbackEnabled,
                            enabled = true,
                            onCheckedChange = onClickVisualFeedbackChange
                        ) {
                            SettingsSliderRow(
                                title = stringResource(R.string.floating_pointer_ripple_size),
                                value = settings.floatingPointerRippleSizeDp,
                                valueRange = 40f..200f,
                                steps = 15,
                                enabled = true,
                                label = stringResource(
                                    R.string.floating_pointer_ripple_size_value,
                                    settings.floatingPointerRippleSizeDp
                                ),
                                onValueChange = onRippleSizeChange
                            )
                            SettingsSliderRow(
                                title = stringResource(R.string.floating_pointer_ripple_duration),
                                value = settings.floatingPointerRippleDurationMs.toFloat(),
                                valueRange = 100f..1500f,
                                steps = 55,
                                enabled = true,
                                label = stringResource(
                                    R.string.floating_pointer_ripple_duration_value,
                                    settings.floatingPointerRippleDurationMs
                                ),
                                onValueChange = { onRippleDurationChange(it.roundToInt()) }
                            )
                            AnimationStyleColorRow(
                                title = stringResource(R.string.floating_pointer_ripple_color),
                                subtitle = stringResource(R.string.floating_pointer_ripple_color_desc),
                                color = settings.floatingPointerRippleColorArgb,
                                enabled = true,
                                onClick = {
                                    pickerInitialColor = settings.floatingPointerRippleColorArgb
                                    colorTarget = PointerColorTarget.Ripple
                                }
                            )
                        }
                    }
                )
            }
        )

        settingsLazySmallTitle(key = "fp-trail-section", title = trailSectionTitle)
        groupedCardItems(
            keyPrefix = "fp-trail-type",
            items = buildList {
                add(
                    settingsCardScopeItem("trail-type") {
                        SettingsCardRow(key = "trail-type-row") { position ->
                            Column(
                                modifier = Modifier
                                    .settingsGroupedRowBackground(position.index, position.count)
                                    .padding(16.dp),
                            ) {
                                SegmentedControl(
                                    items = FloatingPointerTrailType.entries,
                                    selected = trailType,
                                    labelRes = { type ->
                                        when (type) {
                                            FloatingPointerTrailType.OFF ->
                                                R.string.floating_pointer_trail_off

                                            FloatingPointerTrailType.SIMPLE ->
                                                R.string.floating_pointer_trail_simple

                                            FloatingPointerTrailType.HIGH_DETAIL ->
                                                R.string.floating_pointer_trail_high_detail
                                        }
                                    },
                                    onSelect = onTrailTypeChange,
                                    containerColor = MiuixTheme.colorScheme.surface,
                                    indicatorColor = MiuixTheme.colorScheme.surfaceContainer,
                                )
                            }
                        }
                    },
                )
                if (trailType != FloatingPointerTrailType.OFF) {
                    add(
                        settingsCardScopeItem("trail-duration") {
                            SettingsSliderRow(
                                title = stringResource(R.string.floating_pointer_trail_duration),
                                value = settings.floatingPointerTrailDurationMs.toFloat(),
                                valueRange = 50f..500f,
                                steps = 8,
                                enabled = true,
                                label = stringResource(
                                    R.string.floating_pointer_trail_duration_value,
                                    settings.floatingPointerTrailDurationMs
                                ),
                                onValueChange = { onTrailDurationChange(it.roundToInt()) }
                            )
                        }
                    )
                    add(
                        settingsCardScopeItem("trail-color") {
                            AnimationStyleColorRow(
                                title = stringResource(R.string.floating_pointer_trail_color),
                                color = settings.floatingPointerTrailColorArgb,
                                enabled = true,
                                onClick = {
                                    pickerInitialColor = settings.floatingPointerTrailColorArgb
                                    colorTarget = PointerColorTarget.Trail
                                }
                            )
                        }
                    )
                }
            },
        )

        settingsLazySmallTitle(key = "fp-other-section", title = otherSectionTitle)
        groupedCardItems(
            keyPrefix = "fp-pointer-other",
            items = buildList {
                add(
                    settingsCardScopeItem("hide-on-release") {
                        SettingSwitchRow(
                            title = stringResource(R.string.floating_pointer_hide_on_release),
                            subtitle = stringResource(R.string.floating_pointer_hide_on_release_desc),
                            checked = settings.floatingPointerHideWhenJoystickReleased,
                            enabled = true,
                            onCheckedChange = onHideWhenReleasedChange
                        )
                    }
                )
            }
        )
        groupedCardItems(
            keyPrefix = "fp-pointer-reset",
            items = buildList {
                add(
                    settingsCardScopeItem("reset-visual") {
                        SettingLinkRow(
                            title = stringResource(R.string.floating_pointer_reset_visual),
                            onClick = onResetVisualDefaults
                        )
                    }
                )
            }
        )
    }
}
