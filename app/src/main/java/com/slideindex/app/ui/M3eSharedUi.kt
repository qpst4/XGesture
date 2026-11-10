@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.slideindex.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.activity.ActivityShortcut
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.shell.ShellCommand
import com.slideindex.app.ui.miuix.CardItem
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.MiuixIcons

data class PendingPermissionItem(
    val title: String,
    val description: String,
    val grantLabel: String,
    val onGrant: () -> Unit,
)

@Composable
fun SettingIconContainer(
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    contentDescription: String? = null,
    content: @Composable () -> Unit,
) {
    val shape = if (emphasized) {
        MaterialShapes.Cookie9Sided.toShape()
    } else {
        MaterialTheme.shapes.small
    }
    Surface(
        modifier = modifier
            .size(40.dp)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            ),
        shape = shape,
        color = if (emphasized) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@Composable
fun LoadingContent(
    modifier: Modifier = Modifier,
    message: String? = null,
) {
    Column(
        modifier = modifier.padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LoadingIndicator()
        if (!message.isNullOrBlank()) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun SettingsAppBarTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLargeEmphasized,
    )
}

@Composable
fun PendingPermissionsCard(
    items: List<PendingPermissionItem>,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    PendingPermissionsCardContent(items = items, modifier = modifier)
}

@Composable
fun PendingPermissionsCardContent(
    items: List<PendingPermissionItem>,
    modifier: Modifier = Modifier,
) {
    top.yukonga.miuix.kmp.basic.Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(
            color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.errorContainer,
            contentColor = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 12.dp),
            ) {
                top.yukonga.miuix.kmp.basic.Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.error,
                    modifier = Modifier.size(22.dp),
                )
                top.yukonga.miuix.kmp.basic.Text(
                    text = stringResource(R.string.permissions_pending_title),
                    style = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.title4,
                    color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.error,
                )
            }
            items.forEachIndexed { index, item ->
                if (index > 0) {
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        top.yukonga.miuix.kmp.basic.Text(
                            text = item.title,
                            style = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.title4,
                            color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onErrorContainer,
                        )
                        top.yukonga.miuix.kmp.basic.Text(
                            text = item.description,
                            style = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.body2,
                            color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    top.yukonga.miuix.kmp.basic.TextButton(
                        text = item.grantLabel,
                        onClick = item.onGrant,
                        colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        }
    }
}

@Composable
fun GestureActionSettingTrailing(
    action: GestureAction,
    enabled: Boolean = true,
    showSettings: Boolean = false,
    onSettingsClick: (() -> Unit)? = null,
    activityShortcuts: List<ActivityShortcut> = emptyList(),
    shellCommands: List<ShellCommand> = emptyList(),
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        GestureActionTargetIcon(
            action = action,
            activityShortcuts = activityShortcuts,
            shellCommands = shellCommands,
            size = 20.dp,
            contentDescription = gestureActionLabel(action),
        )
        if (showSettings) {
            IconButton(
                onClick = { onSettingsClick?.invoke() ?: onClick() },
                enabled = enabled,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = MiuixIcons.Medium.Settings,
                    contentDescription = stringResource(R.string.float_ball_gesture_action_settings),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = stringResource(R.string.cd_navigate_forward),
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun SettingsRadioPickerScreen(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    keyPrefix: String = "settings-radio-picker",
    items: List<CardItem>,
) {
    SettingsScreenScaffold(
        title = title,
        subtitle = subtitle,
        onBack = onBack,
    ) {
        groupedCardItems(
            keyPrefix = keyPrefix,
            selectableGroup = true,
            items = items,
        )
    }
}

@Composable
fun SettingsFormScreen(
    title: String,
    pageHint: String? = null,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    SettingsScreenScaffold(
        title = title,
        pageHint = pageHint,
        onBack = onBack,
    ) {
        item(key = "settings-form-content") {
            Column {
                content()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.cancel))
                    }
                    Button(
                        onClick = onConfirm,
                        enabled = confirmEnabled,
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        Text(stringResource(R.string.confirm))
                    }
                }
            }
        }
    }
}

@Composable
fun AnimatedFullScreenOverlay(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInHorizontally { fullWidth -> fullWidth / 5 },
        exit = fadeOut() + slideOutHorizontally { fullWidth -> fullWidth / 5 },
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            content()
        }
    }
}

@Composable
fun SettingNavigationIcon(
    icon: ImageVector,
    contentDescription: String? = null,
) {
    SettingIconContainer {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}
