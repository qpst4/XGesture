package com.slideindex.app.ui

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slideindex.app.R
import com.slideindex.app.external.AppLinks
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.KeyEventPresets
import com.slideindex.app.ui.miuix.MiuixLabeledTextField
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.SettingNavigationRow
import com.slideindex.app.ui.settings.components.SettingSwitchRow
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 动作详情页：说明 + 测试（内容型动作填参数）+ 相关设置。
 *
 * 测试参数只存在本地，不写 settings、不写绑定；特殊入口动作（指针/角轮/单击/晃动）
 * 由动作中心禁用测试并给入口设置链接。
 */
@Composable
fun ActionDetailScreen(
    action: GestureAction,
    entry: BuiltInActionEntry,
    onBack: () -> Unit,
    onOpenSettings: (() -> Unit)?,
    onTest: ((GestureAction) -> Unit)?,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val label = gestureActionLabel(action)
    val description = gestureActionDescription(action)
    val permissionHint = gestureActionPermissionHint(action, context)
    val specialHint = entry.special?.let { stringResource(it.hintRes) }
    val infoTitle = stringResource(R.string.action_center_info)
    val testTitle = stringResource(R.string.action_center_test)
    val settingsTitle = stringResource(R.string.action_center_settings)
    val deeplinkTitle = stringResource(R.string.action_center_deeplink)
    val deeplink = AppLinks.actionUriString(action.type.name)

    var url by rememberSaveable(action.type) { mutableStateOf("") }
    var linkLabel by rememberSaveable(action.type) { mutableStateOf("") }
    var command by rememberSaveable(action.type) { mutableStateOf("") }
    var keyCodeText by rememberSaveable(action.type) { mutableStateOf("82") }
    var isLongPress by rememberSaveable(action.type) { mutableStateOf(false) }
    var remindMinutes by rememberSaveable(action.type) { mutableIntStateOf(5) }

    val keyCode = keyCodeText.toIntOrNull()
    val builtAction: GestureAction? = when (entry.testKind) {
        ActionTestKind.OPEN_LINK -> {
            val trimmed = url.trim()
            if (trimmed.isBlank()) null else GestureAction.OpenLink(trimmed, linkLabel.trim())
        }
        ActionTestKind.SHELL -> {
            if (command.isBlank()) null else GestureAction.ExecuteShellCommand(command)
        }
        ActionTestKind.KEY -> {
            keyCode?.takeIf { it > 0 }?.let { code ->
                GestureAction.SimulateKeyEvent(
                    keyCode = code,
                    keyName = KeyEventPresets.getDisplayName(context, code),
                    isLongPress = isLongPress,
                )
            }
        }
        ActionTestKind.REMIND -> when (remindMinutes) {
            1 -> GestureAction.Remind1m
            3 -> GestureAction.Remind3m
            5 -> GestureAction.Remind5m
            10 -> GestureAction.Remind10m
            15 -> GestureAction.Remind15m
            else -> GestureAction.Remind
        }
        ActionTestKind.DIRECT -> entry.sample
        ActionTestKind.DISABLED -> null
    }
    val testEnabled = onTest != null &&
        entry.testKind != ActionTestKind.DISABLED &&
        builtAction != null
    val disabledReason = when {
        entry.testKind == ActionTestKind.DISABLED ->
            stringResource(entry.testDisabledReasonRes ?: R.string.action_center_test_unavailable)
        onTest == null -> stringResource(R.string.action_center_test_unavailable)
        else -> null
    }

    SettingsLazyScreenScaffold(
        title = label,
        onBack = onBack,
    ) {
        settingsLazySmallTitle(
            key = "action-detail-info",
            title = infoTitle,
        )
        groupedCardItems(
            keyPrefix = "action-detail-info",
            items = listOf(
                settingsCardScopeItem("info") {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (!description.isNullOrBlank()) {
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        if (specialHint != null) {
                            Text(
                                text = specialHint,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MiuixTheme.colorScheme.primary,
                            )
                        }
                        if (permissionHint != null) {
                            Text(
                                text = permissionHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                },
            ),
        )

        settingsLazySmallTitle(
            key = "action-detail-deeplink",
            title = deeplinkTitle,
        )
        groupedCardItems(
            keyPrefix = "action-detail-deeplink",
            items = listOf(
                settingsCardScopeItem("deeplink") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = deeplink,
                            style = MaterialTheme.typography.bodySmall,
                            color = MiuixTheme.colorScheme.onSurfaceContainer,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(deeplink))
                                Toast.makeText(
                                    context,
                                    R.string.action_center_deeplink_copied,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = deeplinkTitle,
                            )
                        }
                    }
                },
            ),
        )

        settingsLazySmallTitle(
            key = "action-detail-test",
            title = testTitle,
        )
        groupedCardItems(
            keyPrefix = "action-detail-test",
            items = buildList {
                when (entry.testKind) {
                    ActionTestKind.OPEN_LINK -> add(
                        settingsCardScopeItem("open-link-form") {
                            ActionFormCard(
                                note = stringResource(R.string.action_center_test_note),
                            ) {
                                MiuixLabeledTextField(
                                    value = url,
                                    onValueChange = { url = it },
                                    label = stringResource(R.string.gesture_open_link_url_field),
                                    singleLine = false,
                                    minLines = 2,
                                    maxLines = 4,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                MiuixLabeledTextField(
                                    value = linkLabel,
                                    onValueChange = { linkLabel = it },
                                    label = stringResource(R.string.gesture_open_link_label_field),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        },
                    )
                    ActionTestKind.SHELL -> add(
                        settingsCardScopeItem("shell-form") {
                            ActionFormCard(
                                note = stringResource(R.string.action_center_test_note),
                            ) {
                                MiuixLabeledTextField(
                                    value = command,
                                    onValueChange = { command = it },
                                    label = stringResource(R.string.action_center_command),
                                    singleLine = false,
                                    minLines = 2,
                                    maxLines = 5,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        },
                    )
                    ActionTestKind.KEY -> add(
                        settingsCardScopeItem("key-form") {
                            ActionFormCard(
                                note = stringResource(R.string.action_center_test_note),
                            ) {
                                MiuixLabeledTextField(
                                    value = keyCodeText,
                                    onValueChange = { input ->
                                        keyCodeText = input.filter { it.isDigit() }
                                    },
                                    label = stringResource(R.string.key_event_code_label),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        },
                    )
                    ActionTestKind.REMIND -> add(
                        settingsCardScopeItem("remind-form") {
                            ActionFormCard(
                                note = stringResource(R.string.action_center_test_note),
                            ) {
                                Text(
                                    text = stringResource(R.string.action_center_remind_duration),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MiuixTheme.colorScheme.onSurfaceContainer,
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    listOf(1, 3, 5, 10, 15).forEach { minutes ->
                                        RemindChip(
                                            label = stringResource(
                                                R.string.action_center_remind_minutes,
                                                minutes,
                                            ),
                                            selected = remindMinutes == minutes,
                                            onClick = { remindMinutes = minutes },
                                        )
                                    }
                                    RemindChip(
                                        label = stringResource(R.string.action_center_remind_custom),
                                        selected = remindMinutes == 0,
                                        onClick = { remindMinutes = 0 },
                                    )
                                }
                            }
                        },
                    )
                    else -> Unit
                }

                if (entry.testKind == ActionTestKind.KEY) {
                    add(
                        settingsCardScopeItem("key-long-press") {
                            SettingSwitchRow(
                                title = stringResource(R.string.key_event_long_press_title),
                                checked = isLongPress,
                                enabled = true,
                                onCheckedChange = { isLongPress = it },
                            )
                        },
                    )
                }

                add(
                    settingsCardScopeItem("test-action") {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (disabledReason != null) {
                                Text(
                                    text = disabledReason,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                            Button(
                                onClick = { builtAction?.let { onTest?.invoke(it) } },
                                enabled = testEnabled,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.action_center_test))
                            }
                        }
                    },
                )
            },
        )

        onOpenSettings?.let { openSettings ->
            settingsLazySmallTitle(
                key = "action-detail-settings",
                title = settingsTitle,
            )
            groupedCardItems(
                keyPrefix = "action-detail-settings",
                items = listOf(
                    settingsCardScopeItem("settings") {
                        SettingNavigationRow(
                            icon = { contentDescription ->
                                Icon(
                                    imageVector = gestureActionTypeOutlinedIcon(entry.type),
                                    contentDescription = contentDescription,
                                )
                            },
                            title = stringResource(R.string.action_center_open_settings),
                            subtitle = stringResource(R.string.action_center_settings_desc),
                            onClick = openSettings,
                        )
                    },
                ),
            )
        }
    }
}

@Composable
private fun ActionFormCard(
    note: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        content()
        Text(
            text = note,
            style = MaterialTheme.typography.bodySmall,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun RemindChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.surfaceContainer
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            color = if (selected) {
                MiuixTheme.colorScheme.onPrimary
            } else {
                MiuixTheme.colorScheme.onSurfaceContainer
            },
            fontSize = 13.sp,
            maxLines = 1,
        )
    }
}

@get:StringRes
private val ActionSpecialMark.hintRes: Int
    get() = when (this) {
        ActionSpecialMark.POINTER -> R.string.action_center_special_pointer
        ActionSpecialMark.CORNER -> R.string.action_center_special_corner
        ActionSpecialMark.SINGLE_TAP -> R.string.action_center_special_single_tap
        ActionSpecialMark.SHAKE -> R.string.action_center_special_shake
    }
