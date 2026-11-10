package com.slideindex.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SwipeLeft
import androidx.compose.material.icons.filled.SwipeRight
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.shake.ShakeGestureType
import com.slideindex.app.shake.ShakeSensitivityScale
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingsCardScope
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.SettingsSliderRow
import com.slideindex.app.ui.settings.components.settingsCardScopeItem

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShakeActionSetSettingsScreen(
    title: String,
    subtitle: String,
    actions: Map<ShakeGestureType, GestureAction>,
    appSettings: AppSettings? = null,
    onBack: () -> Unit,
    onOpenActionPick: (ShakeGestureType) -> Unit,
) {
    SettingsScreenScaffold(
        title = title,
        pageHint = subtitle,
        onBack = onBack,
    ) {
        groupedCardItems(
            keyPrefix = "shake-action-set",
            items = buildList {
                ShakeGestureType.entries.forEach { type ->
                    add(
                        settingsCardScopeItem("shake-action-${type.name}") {
                            ShakeGestureActionRow(
                                type = type,
                                action = actions[type] ?: GestureAction.None,
                                activityShortcuts = appSettings?.activityShortcuts.orEmpty(),
                                shellCommands = appSettings?.shellCommands.orEmpty(),
                                onClick = { onOpenActionPick(type) },
                            )
                        },
                    )
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShakeIndependentSensitivityScreen(
    globalSensitivity: Float,
    perDirectionSensitivity: Map<ShakeGestureType, Float>,
    onBack: () -> Unit,
    onSensitivityChange: (ShakeGestureType, Float) -> Unit,
) {
    val sensitivityHint = stringResource(R.string.shake_gestures_sensitivity_hint)
    val pageHint = buildString {
        append(stringResource(R.string.shake_gestures_independent_sensitivity_desc))
        append('\n')
        append(sensitivityHint)
    }

    SettingsScreenScaffold(
        title = stringResource(R.string.shake_gestures_independent_sensitivity),
        pageHint = pageHint,
        onBack = onBack,
    ) {
        groupedCardItems(
            keyPrefix = "shake-sensitivity",
            items = buildList {
                ShakeGestureType.entries.forEach { type ->
                    add(
                        settingsCardScopeItem("shake-sensitivity-${type.name}") {
                            SettingsSliderRow(
                                title = shakeGestureLabel(type),
                                value = perDirectionSensitivity[type] ?: globalSensitivity,
                                valueRange = ShakeSensitivityScale.UI_MIN..ShakeSensitivityScale.UI_MAX,
                                enabled = true,
                                label = String.format(
                                    java.util.Locale.US,
                                    "%.0f",
                                    perDirectionSensitivity[type] ?: globalSensitivity,
                                ),
                                formatLabel = { String.format(java.util.Locale.US, "%.0f", it) },
                                startLabel = stringResource(R.string.shake_gestures_sensitivity_hard),
                                endLabel = stringResource(R.string.shake_gestures_sensitivity_easy),
                                keyPoints = ShakeSensitivityScale.UI_KEY_POINTS,
                                onValueChange = { onSensitivityChange(type, it) },
                            )
                        },
                    )
                }
            },
        )
    }
}

@Composable
internal fun SettingsCardScope.ShakeGestureActionRow(
    type: ShakeGestureType,
    action: GestureAction,
    enabled: Boolean = true,
    activityShortcuts: List<com.slideindex.app.activity.ActivityShortcut> = emptyList(),
    shellCommands: List<com.slideindex.app.shell.ShellCommand> = emptyList(),
    onClick: () -> Unit,
) {
    SettingNavigationRow(
        icon = { label ->
            ShakeGestureColoredIcon(
                icon = shakeGestureIcon(type),
                background = shakeGestureIconTint(type),
                contentDescription = label,
            )
        },
        title = shakeGestureLabel(type),
        subtitle = gestureActionSettingSubtitle(action),
        enabled = enabled,
        onClick = onClick,
        trailingContent = {
            GestureActionSettingTrailing(
                action = action,
                enabled = enabled,
                activityShortcuts = activityShortcuts,
                shellCommands = shellCommands,
                onClick = onClick,
            )
        },
    )
}

@Composable
fun ShakeGestureColoredIcon(
    icon: ImageVector,
    background: Color,
    contentColor: Color = Color.White,
    contentDescription: String,
) {
    Surface(
        modifier = Modifier.size(40.dp),
        shape = MaterialTheme.shapes.small,
        color = background,
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = contentColor,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
fun shakeGestureLabel(type: ShakeGestureType): String = when (type) {
    ShakeGestureType.LEFT_FLIP -> stringResource(R.string.shake_gesture_left_flip)
    ShakeGestureType.RIGHT_FLIP -> stringResource(R.string.shake_gesture_right_flip)
    ShakeGestureType.FORWARD_FLIP -> stringResource(R.string.shake_gesture_forward_flip)
    ShakeGestureType.BACKWARD_FLIP -> stringResource(R.string.shake_gesture_backward_flip)
    ShakeGestureType.LEFT_FLICK -> stringResource(R.string.shake_gesture_left_flick)
    ShakeGestureType.RIGHT_FLICK -> stringResource(R.string.shake_gesture_right_flick)
}

fun shakeGestureIcon(type: ShakeGestureType): ImageVector = when (type) {
    ShakeGestureType.LEFT_FLIP -> Icons.AutoMirrored.Filled.RotateLeft
    ShakeGestureType.RIGHT_FLIP -> Icons.AutoMirrored.Filled.RotateRight
    ShakeGestureType.FORWARD_FLIP -> Icons.Default.ScreenRotation
    ShakeGestureType.BACKWARD_FLIP -> Icons.Default.ScreenRotation
    ShakeGestureType.LEFT_FLICK -> Icons.Default.SwipeLeft
    ShakeGestureType.RIGHT_FLICK -> Icons.Default.SwipeRight
}

@Composable
fun shakeGestureIconTint(type: ShakeGestureType): Color = when (type) {
    ShakeGestureType.LEFT_FLIP,
    ShakeGestureType.RIGHT_FLIP,
    -> Color(0xFF42A5F5)
    ShakeGestureType.FORWARD_FLIP,
    ShakeGestureType.BACKWARD_FLIP,
    -> Color(0xFFFF9800)
    ShakeGestureType.LEFT_FLICK,
    ShakeGestureType.RIGHT_FLICK,
    -> Color(0xFFAB47BC)
}
