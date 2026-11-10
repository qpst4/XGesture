@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatBallSettings
import com.slideindex.app.settings.FloatBallStyleType
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingRadioRow
import com.slideindex.app.ui.settings.components.SettingSpinnerRow
import com.slideindex.app.ui.settings.components.SettingSwitchRow
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloatBallStyleSettingsScreen(
    settings: AppSettings,
    enabled: Boolean,
    onBack: () -> Unit,
    onStyleTypeChange: (FloatBallStyleType) -> Unit,
    onCustomImageUriChange: (String) -> Unit,
    onCustomImageNoCropChange: (Boolean) -> Unit,
    onSlideshowUrisChange: (List<String>) -> Unit,
    onSlideshowIntervalChange: (Int) -> Unit,
    onGifUriChange: (String) -> Unit
) {
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { onCustomImageUriChange(it.toString()) }
    }
    val slideshowPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(50)
    ) { uris ->
        if (uris.isNotEmpty()) {
            onSlideshowUrisChange(uris.map { it.toString() })
        }
    }
    val gifPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { onGifUriChange(it.toString()) }
    }

    val intervalOptions = FloatBallSettings.SLIDESHOW_INTERVAL_OPTIONS_SECONDS
    val selectedIntervalIndex = intervalOptions.indexOf(
        FloatBallSettings.normalizeSlideshowIntervalSeconds(
            settings.floatBallSlideshowIntervalSeconds,
        ),
    ).coerceAtLeast(0)
    val intervalItems = intervalOptions.map { seconds ->
        DropdownItem(text = slideshowIntervalLabel(seconds))
    }
    val intervalText = slideshowIntervalLabel(settings.floatBallSlideshowIntervalSeconds)

    SettingsScreenScaffold(
        title = stringResource(R.string.float_ball_style_picker_title),
        subtitle = stringResource(R.string.float_ball_style_picker_summary),
        onBack = onBack
    ) {
        groupedCardItems(
            keyPrefix = "fb-style-type",
            selectableGroup = true,
            items = buildList {
                FloatBallStyleType.entries.forEach { style ->
                    add(
                        settingsCardScopeItem("style-${style.name}") {
                            SettingRadioRow(
                                title = floatBallStyleLabel(style),
                                selected = settings.floatBallStyleType == style,
                                enabled = enabled,
                                onClick = { if (enabled) onStyleTypeChange(style) }
                            )
                        }
                    )
                }
            }
        )

        when (settings.floatBallStyleType) {
            FloatBallStyleType.CUSTOM_IMAGE -> {
                groupedCardItems(
                    keyPrefix = "fb-style-custom-image",
                    items = listOf(
                        settingsCardScopeItem("custom-image") {
                            SettingNavigationRow(
                                icon = { label ->
                                    Icon(
                                        Icons.Outlined.Image,
                                        contentDescription = label,
                                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                },
                                title = stringResource(R.string.float_ball_style_pick_image),
                                subtitle = if (settings.floatBallCustomImageUri.isNotBlank()) {
                                    stringResource(R.string.float_ball_style_image_selected)
                                } else {
                                    stringResource(R.string.float_ball_style_custom_image_hint)
                                },
                                enabled = enabled,
                                onClick = { imagePicker.launch("image/*") },
                            )
                        },
                        settingsCardScopeItem("custom-image-no-crop") {
                            SettingSwitchRow(
                                title = stringResource(R.string.float_ball_style_custom_image_no_crop),
                                subtitle = stringResource(R.string.float_ball_style_custom_image_no_crop_hint),
                                checked = settings.floatBallCustomImageNoCrop,
                                enabled = enabled,
                                onCheckedChange = onCustomImageNoCropChange,
                            )
                        },
                    ),
                )
            }

            FloatBallStyleType.SLIDESHOW -> {
                groupedCardItems(
                    keyPrefix = "fb-style-slideshow",
                    items = listOf(
                        settingsCardScopeItem("slideshow-images") {
                            SettingNavigationRow(
                                icon = { label ->
                                    Icon(
                                        Icons.Outlined.Image,
                                        contentDescription = label,
                                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                },
                                title = stringResource(R.string.float_ball_style_pick_slideshow),
                                subtitle = pluralStringResource(
                                    R.plurals.float_ball_style_slideshow_hint,
                                    settings.floatBallSlideshowUris.size,
                                    settings.floatBallSlideshowUris.size,
                                    intervalText,
                                ),
                                enabled = enabled,
                                onClick = {
                                    slideshowPicker.launch(
                                        PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly,
                                        ),
                                    )
                                },
                            )
                        },
                        settingsCardScopeItem("slideshow-interval") {
                            SettingSpinnerRow(
                                title = stringResource(R.string.float_ball_style_slideshow_interval),
                                dialogButtonText = stringResource(
                                    R.string.float_ball_style_slideshow_interval_confirm,
                                ),
                                items = intervalItems,
                                selectedIndex = selectedIntervalIndex,
                                enabled = enabled,
                                icon = { label ->
                                    Icon(
                                        Icons.Outlined.Timer,
                                        contentDescription = label,
                                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                },
                                onSelectedIndexChange = { index ->
                                    intervalOptions.getOrNull(index)?.let(onSlideshowIntervalChange)
                                },
                            )
                        },
                    ),
                )
            }

            FloatBallStyleType.GIF -> {
                groupedCardItems(
                    keyPrefix = "fb-style-gif",
                    items = listOf(
                        settingsCardScopeItem("gif") {
                            SettingNavigationRow(
                                icon = { label ->
                                    Icon(
                                        Icons.Outlined.Image,
                                        contentDescription = label,
                                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                },
                                title = stringResource(R.string.float_ball_style_pick_gif),
                                subtitle = if (settings.floatBallGifUri.isBlank()) {
                                    stringResource(R.string.float_ball_style_gif_hint)
                                } else {
                                    stringResource(R.string.float_ball_style_gif)
                                },
                                enabled = enabled,
                                onClick = { gifPicker.launch("image/*") },
                            )
                        },
                    ),
                )
            }

            else -> Unit
        }
    }
}

@Composable
private fun slideshowIntervalLabel(seconds: Int): String {
    val value = FloatBallSettings.normalizeSlideshowIntervalSeconds(seconds)
    return when {
        value % 86_400 == 0 ->
            stringResource(R.string.float_ball_style_slideshow_interval_days, value / 86_400)
        value % 3_600 == 0 ->
            stringResource(R.string.float_ball_style_slideshow_interval_hours, value / 3_600)
        value % 60 == 0 ->
            stringResource(R.string.float_ball_style_slideshow_interval_minutes, value / 60)
        else ->
            stringResource(R.string.float_ball_style_slideshow_interval_seconds, value)
    }
}

@Composable
fun floatBallStyleLabel(style: FloatBallStyleType): String = when (style) {
    FloatBallStyleType.DEFAULT -> stringResource(R.string.float_ball_style_default)
    FloatBallStyleType.ANIMATED_PLANE -> stringResource(R.string.float_ball_style_animated_plane)
    FloatBallStyleType.ANIMATED_PULSE -> stringResource(R.string.float_ball_style_animated_pulse)
    FloatBallStyleType.ANIMATED_ORBIT -> stringResource(R.string.float_ball_style_animated_orbit)
    FloatBallStyleType.CUSTOM_IMAGE -> stringResource(R.string.float_ball_style_custom_image)
    FloatBallStyleType.SLIDESHOW -> stringResource(R.string.float_ball_style_slideshow)
    FloatBallStyleType.GIF -> stringResource(R.string.float_ball_style_gif)
}
