@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.shake.FaceDownGestureSettings
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SETTINGS_SLIDER_PERCENT_KEY_POINTS_100
import com.slideindex.app.ui.settings.components.SettingExpandableSwitchRow
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingSwitchRow
import com.slideindex.app.ui.settings.components.SettingsCardScope
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.SettingsSliderRow
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazyHint
import kotlin.math.roundToInt

@Composable
fun FaceDownSettingsScreen(
    faceDownSettings: FaceDownGestureSettings,
    appSettings: com.slideindex.app.settings.AppSettings? = null,
    onBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onHoldDurationChange: (Long) -> Unit,
    onCooldownChange: (Long) -> Unit,
    onRequireProximityChange: (Boolean) -> Unit,
    onDisableInLandscapeChange: (Boolean) -> Unit,
    onVibrationFeedbackChange: (Boolean) -> Unit,
    onAudioFeedbackChange: (Boolean) -> Unit,
    onAudioFeedbackVolumeChange: (Int) -> Unit,
    onOpenActionPick: () -> Unit
) {
    val resources = LocalResources.current
    val formatHoldDuration: (Float) -> String = remember(resources) {
        { seconds -> resources.getString(R.string.face_down_gestures_hold_duration_value, seconds) }
    }
    val formatCooldown: (Float) -> String = remember(resources) {
        { seconds -> resources.getString(R.string.face_down_gestures_cooldown_value, seconds) }
    }
    val formatAudioVolume: (Float) -> String = remember(resources) {
        { percent -> resources.getString(R.string.face_down_gestures_audio_feedback_volume_value, percent.toInt()) }
    }
    val blacklistHint = stringResource(R.string.face_down_gestures_blacklist_hint)

    SettingsScreenScaffold(
        title = stringResource(R.string.face_down_gestures_title),
        onBack = onBack
    ) {
        groupedCardItems(
            keyPrefix = "face-down",
            items = buildList {
                add(
                    settingsCardScopeItem("face-down-enabled") {
                        SettingSwitchRow(
                            title = stringResource(R.string.face_down_gestures_title),
                            subtitle = stringResource(R.string.face_down_gestures_subtitle),
                            icon = { label ->
                                FaceDownColoredSettingIcon(
                                    icon = Icons.Default.PhoneAndroid,
                                    background = Color(0xFF78909C),
                                    contentDescription = label
                                )
                            },
                            checked = faceDownSettings.enabled,
                            enabled = true,
                            onCheckedChange = onEnabledChange
                        )
                    }
                )
                add(
                    settingsCardScopeItem("face-down-action") {
                        FaceDownActionRow(
                            action = faceDownSettings.action,
                            enabled = faceDownSettings.enabled,
                            activityShortcuts = appSettings?.activityShortcuts.orEmpty(),
                            shellCommands = appSettings?.shellCommands.orEmpty(),
                            onClick = onOpenActionPick
                        )
                    }
                )
                add(
                    settingsCardScopeItem("face-down-hold-duration") {
                        SettingsSliderRow(
                            title = stringResource(R.string.face_down_gestures_hold_duration),
                            value = faceDownSettings.holdDurationMs / 1000f,
                            valueRange = FaceDownGestureSettings.HOLD_DURATION_MIN_SECONDS..
                                FaceDownGestureSettings.HOLD_DURATION_MAX_SECONDS,
                            enabled = faceDownSettings.enabled,
                            label = formatHoldDuration(faceDownSettings.holdDurationMs / 1000f),
                            formatLabel = formatHoldDuration,
                            keyPoints = FaceDownGestureSettings.HOLD_DURATION_KEY_POINTS_SECONDS,
                            onValueChange = { seconds ->
                                onHoldDurationChange((seconds * 1000f).toLong())
                            }
                        )
                    }
                )
                add(
                    settingsCardScopeItem("face-down-cooldown") {
                        SettingsSliderRow(
                            title = stringResource(R.string.face_down_gestures_cooldown),
                            value = faceDownSettings.cooldownMs / 1000f,
                            valueRange = FaceDownGestureSettings.COOLDOWN_MIN_SECONDS..
                                FaceDownGestureSettings.COOLDOWN_MAX_SECONDS,
                            enabled = faceDownSettings.enabled,
                            label = formatCooldown(faceDownSettings.cooldownMs / 1000f),
                            formatLabel = formatCooldown,
                            keyPoints = FaceDownGestureSettings.COOLDOWN_KEY_POINTS_SECONDS,
                            onValueChange = { seconds ->
                                onCooldownChange((seconds * 1000f).toLong())
                            }
                        )
                    }
                )
                add(
                    settingsCardScopeItem("face-down-require-proximity") {
                        SettingSwitchRow(
                            title = stringResource(R.string.face_down_gestures_require_proximity),
                            subtitle = stringResource(R.string.face_down_gestures_require_proximity_desc),
                            checked = faceDownSettings.requireProximity,
                            enabled = faceDownSettings.enabled,
                            onCheckedChange = onRequireProximityChange
                        )
                    }
                )
                add(
                    settingsCardScopeItem("face-down-disable-landscape") {
                        SettingSwitchRow(
                            title = stringResource(R.string.face_down_gestures_disable_landscape),
                            checked = faceDownSettings.disableInLandscape,
                            enabled = faceDownSettings.enabled,
                            onCheckedChange = onDisableInLandscapeChange
                        )
                    }
                )
                add(
                    settingsCardScopeItem("face-down-vibration") {
                        SettingSwitchRow(
                            title = stringResource(R.string.face_down_gestures_vibration_feedback),
                            checked = faceDownSettings.vibrationFeedbackEnabled,
                            enabled = faceDownSettings.enabled,
                            onCheckedChange = onVibrationFeedbackChange
                        )
                    }
                )
                add(
                    settingsCardScopeItem("face-down-audio") {
                        SettingExpandableSwitchRow(
                            title = stringResource(R.string.face_down_gestures_audio_feedback),
                            subtitle = stringResource(R.string.face_down_gestures_audio_feedback_desc),
                            checked = faceDownSettings.audioFeedbackEnabled,
                            enabled = faceDownSettings.enabled,
                            onCheckedChange = onAudioFeedbackChange
                        ) {
                            SettingsSliderRow(
                                title = stringResource(R.string.face_down_gestures_audio_feedback_volume),
                                value = faceDownSettings.audioFeedbackVolume.toFloat(),
                                valueRange = 0f..100f,
                                enabled = faceDownSettings.enabled && faceDownSettings.audioFeedbackEnabled,
                                label = formatAudioVolume(faceDownSettings.audioFeedbackVolume.toFloat()),
                                formatLabel = formatAudioVolume,
                                keyPoints = SETTINGS_SLIDER_PERCENT_KEY_POINTS_100,
                                onValueChange = { volume ->
                                    onAudioFeedbackVolumeChange(volume.roundToInt())
                                }
                            )
                        }
                    }
                )
            }
        )
        settingsLazyHint(key = "face-down-blacklist-hint", text = blacklistHint)
    }
}

@Composable
private fun SettingsCardScope.FaceDownActionRow(
    action: GestureAction,
    enabled: Boolean,
    activityShortcuts: List<com.slideindex.app.activity.ActivityShortcut> = emptyList(),
    shellCommands: List<com.slideindex.app.shell.ShellCommand> = emptyList(),
    onClick: () -> Unit
) {
    SettingNavigationRow(
        icon = { label ->
            FaceDownColoredSettingIcon(
                icon = Icons.Default.Lock,
                background = Color(0xFF5C6BC0),
                contentDescription = label
            )
        },
        title = stringResource(R.string.face_down_gestures_action),
        subtitle = gestureActionSettingSubtitle(action),
        enabled = enabled,
        onClick = onClick,
        trailingContent = {
            GestureActionSettingTrailing(
                action = action,
                enabled = enabled,
                activityShortcuts = activityShortcuts,
                shellCommands = shellCommands,
                onClick = onClick
            )
        }
    )
}

@Composable
private fun FaceDownColoredSettingIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    background: Color,
    contentColor: Color = Color.White,
    contentDescription: String
) {
    Surface(
        modifier = Modifier.size(40.dp),
        shape = MaterialTheme.shapes.small,
        color = background
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
