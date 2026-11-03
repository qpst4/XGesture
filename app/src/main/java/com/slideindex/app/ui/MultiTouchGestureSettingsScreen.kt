package com.slideindex.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.multitouch.MultiPointerGesture
import com.slideindex.app.gesture.multitouch.MultiTouchGestureEntry
import com.slideindex.app.gesture.multitouch.displayName
import com.slideindex.app.multitouch.MultiTouchGestureSettings
import com.slideindex.app.multitouch.MultiTouchStatus
import com.slideindex.app.multitouch.encodePathStrokes
import com.slideindex.app.multitouch.multiTouchPresetKinds
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.MiuixNavigationRow
import com.slideindex.app.ui.settings.components.SettingSwitchRow
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.StatusRow
import com.slideindex.app.ui.settings.components.StatusTone
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazyHint

/**
 * 多指手势设置页（Shizuku 观察模式）。
 *
 * 手势条目 = 类型 + 接触点数 + 动作；“记录手势”只是其中一种类型。
 */
@Composable
fun MultiTouchGestureSettingsScreen(
    settings: MultiTouchGestureSettings,
    status: MultiTouchStatus,
    onBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onRetry: () -> Unit,
    onOpenActionPick: (MultiPointerGesture, Int, String) -> Unit = { _, _, _ -> },
    onOpenRecorder: () -> Unit = {},
    onRemoveEntry: (String) -> Unit = {},
) {
    var showKindPicker by remember { mutableStateOf(false) }
    var pendingKind by remember { mutableStateOf<MultiPointerGesture?>(null) }
    var entryMenu by remember { mutableStateOf<MultiTouchGestureEntry?>(null) }

    SettingsScreenScaffold(title = "多指手势", onBack = onBack) {
        groupedCardItems(
            keyPrefix = "multitouch",
            items = buildList {
                add(
                    settingsCardScopeItem("multitouch-enabled") {
                        SettingSwitchRow(
                            title = "启用多指手势",
                            subtitle = "通过 Shizuku 读取触摸屏，按手势条目触发动作",
                            checked = settings.enabled,
                            enabled = true,
                            onCheckedChange = onEnabledChange,
                        )
                    }
                )
                add(
                    settingsCardScopeItem("multitouch-status") {
                        val conflict = status.conflictDevices
                        val tone = when {
                            !status.enabled -> StatusTone.Neutral
                            conflict != null -> StatusTone.Bad
                            status.running -> StatusTone.Good
                            else -> StatusTone.Bad
                        }
                        val pill = when {
                            !status.enabled -> "未启用"
                            conflict != null -> "有冲突"
                            status.running -> "观察中"
                            else -> "未运行"
                        }
                        StatusRow(
                            title = "运行状态",
                            pill = pill,
                            tone = tone,
                            detail = multiTouchStatusDetail(status),
                            segmentKey = "multitouch-status",
                            onClick = onRetry,
                        )
                    }
                )
                add(
                    settingsCardScopeItem("multitouch-add") {
                        MiuixNavigationRow(
                            title = "添加手势",
                            summary = "记录手势，或选择预设类型",
                            enabled = settings.enabled,
                            onClick = { showKindPicker = true },
                        )
                    }
                )
                settings.entries.forEach { entry ->
                    add(
                        settingsCardScopeItem("multitouch-entry-${entry.id}") {
                            MiuixNavigationRow(
                                title = "${entry.fingerCount} 指 · ${entry.kind.displayName()}",
                                summary = gestureActionLabel(entry.action),
                                enabled = settings.enabled,
                                onClick = { entryMenu = entry },
                            )
                        }
                    )
                }
            }
        )
        settingsLazyHint(
            key = "multitouch-hint",
            text = "观察模式不会拦截事件：触发手势时应用仍会收到同一手势。" +
                "Shizuku 重启后需要重新授权/启动；低指数量手势容易和应用自身手势冲突。",
        )
    }

    if (showKindPicker) {
        GestureKindPickerDialog(
            onDismiss = { showKindPicker = false },
            onPick = { kind ->
                showKindPicker = false
                if (kind == MultiPointerGesture.PATH) {
                    onOpenRecorder()
                } else {
                    pendingKind = kind
                }
            },
        )
    }

    val kind = pendingKind
    if (kind != null) {
        FingerCountPickerDialog(
            kind = kind,
            onDismiss = { pendingKind = null },
            onPick = { fingers ->
                pendingKind = null
                onOpenActionPick(kind, fingers, "")
            },
        )
    }

    val menuEntry = entryMenu
    if (menuEntry != null) {
        AlertDialog(
            onDismissRequest = { entryMenu = null },
            title = { Text("${menuEntry.fingerCount} 指 · ${menuEntry.kind.displayName()}") },
            text = { Text(gestureActionLabel(menuEntry.action)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        entryMenu = null
                        onOpenActionPick(
                            menuEntry.kind,
                            menuEntry.fingerCount,
                            encodePathStrokes(menuEntry.pathStrokes),
                        )
                    },
                ) { Text("修改动作") }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            entryMenu = null
                            onRemoveEntry(menuEntry.id)
                        },
                    ) { Text("删除") }
                    TextButton(onClick = { entryMenu = null }) { Text("取消") }
                }
            },
        )
    }
}

/** 选择手势：记录手势 + 预设类型（对齐常见多指手势工具的列表）。 */
@Composable
private fun GestureKindPickerDialog(
    onDismiss: () -> Unit,
    onPick: (MultiPointerGesture) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(vertical = 12.dp)) {
                Text(
                    "选择手势",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(460.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    PickerRow(
                        label = "✏️ 记录手势",
                        onClick = { onPick(MultiPointerGesture.PATH) },
                    )
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                    multiTouchPresetKinds.forEach { kind ->
                        PickerRow(
                            label = kind.displayName(),
                            onClick = { onPick(kind) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerRow(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
    )
}

/** 接触点：1–5。 */
@Composable
private fun FingerCountPickerDialog(
    kind: MultiPointerGesture,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("接触点", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "${kind.displayName()}：选择手势触摸点的数量",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (fingers in 1..5) {
                        FingerCell(
                            fingers = fingers,
                            modifier = Modifier.weight(1f),
                            onClick = { onPick(fingers) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                ) { Text("取消") }
            }
        }
    }
}

@Composable
private fun FingerCell(fingers: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
    ) {
        Text(
            text = fingers.toString(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
        )
    }
}

/** 状态行说明：设备 / 模式 / 特权通道 / 冲突，全部收在这一行里。 */
private fun multiTouchStatusDetail(status: MultiTouchStatus): String {
    val base = when {
        !status.enabled -> "开关未打开"
        status.detail == "recording" -> "录制中（识别已暂停）"
        !status.shizukuAvailable -> "Shizuku 未运行或未授权，点击重试"
        status.running -> {
            val device = status.detail.removePrefix("watching:").ifBlank { "未知设备" }
            "设备 $device · 观察模式（只读，不拦截） · Shizuku 已授权"
        }
        status.detail == "service-unavailable" -> "Shizuku 服务启动失败，点击重试"
        status.detail == "device-unavailable" -> "没有可读的触摸设备，点击重试"
        else -> "未运行（${status.detail.ifBlank { "unknown" }}），点击重试"
    }
    val conflict = status.conflictDevices ?: return base
    val name = if (conflict.length > 30) conflict.take(30) + "…" else conflict
    return base + "\n⚠ 冲突：$name（同时开启会双触发；对方独占触摸屏时本功能收不到事件）"
}
