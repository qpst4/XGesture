package com.slideindex.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.RotateLeft
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slideindex.app.R
import com.slideindex.app.gesture.multitouch.MultiPointerGesture
import com.slideindex.app.gesture.multitouch.MultiTouchGestureEntry
import com.slideindex.app.multitouch.MultiTouchGestureSettings
import com.slideindex.app.multitouch.MultiTouchStatus
import com.slideindex.app.multitouch.encodePathStrokes
import com.slideindex.app.multitouch.multiTouchPresetKinds
import com.slideindex.app.ui.miuix.MiuixArrowRow
import com.slideindex.app.ui.miuix.MiuixArrowRow
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.MiuixNavigationRow
import com.slideindex.app.ui.settings.components.SettingSwitchRow
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.StatusRow
import com.slideindex.app.ui.settings.components.StatusTone
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazyHint
import com.slideindex.app.ui.settings.components.settingsLazyTipCard
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.window.WindowDialog

private val PreviewColors = listOf(
    Color(0xFF1E88E5),
    Color(0xFF43A047),
    Color(0xFFE53935),
    Color(0xFFFB8C00),
    Color(0xFF8E24AA),
)

/**
 * 多指手势设置页（Shizuku 观察模式）。
 *
 * 手势条目 = 类型 + 接触点数 + 动作；“记录手势”只是其中一种类型。
 * 列表项显示手势预览；点条目打开编辑弹窗（预览 + 动作 + 删除）。
 */
@Composable
fun MultiTouchGestureSettingsScreen(
    settings: MultiTouchGestureSettings,
    status: MultiTouchStatus,
    onBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onRetry: () -> Unit,
    onOpenActionPick: (MultiPointerGesture, Int, String, String) -> Unit = { _, _, _, _ -> },
    onOpenRecorder: () -> Unit = {},
    onOpenPathEditor: (MultiTouchGestureEntry) -> Unit = {},
    onRemoveEntry: (String) -> Unit = {},
) {
    var showKindPicker by remember { mutableStateOf(false) }
    var pendingKind by remember { mutableStateOf<MultiPointerGesture?>(null) }
    var editingEntry by remember { mutableStateOf<MultiTouchGestureEntry?>(null) }

    // SettingsScreenScaffold 的 content 是 LazyListScope 扩展，不是 @Composable 上下文，
    // 所以标题/说明/底部提示的文案要先在这里取出来。
    val screenTitle = stringResource(R.string.multitouch_title)
    val tipText = stringResource(R.string.multitouch_tip)
    val bottomHint = stringResource(R.string.multitouch_hint)

    SettingsScreenScaffold(title = screenTitle, onBack = onBack) {
        settingsLazyTipCard(
            key = "multitouch-tip",
            text = tipText,
        )
        groupedCardItems(
            keyPrefix = "multitouch",
            items = buildList {
                add(
                    settingsCardScopeItem("multitouch-enabled") {
                        SettingSwitchRow(
                            title = stringResource(R.string.multitouch_enable_title),
                            subtitle = stringResource(R.string.multitouch_enable_summary),
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
                            !status.enabled -> stringResource(R.string.multitouch_status_disabled)
                            conflict != null ->
                                stringResource(R.string.multitouch_status_conflict_pill)

                            status.running -> stringResource(R.string.multitouch_status_running)
                            else -> stringResource(R.string.multitouch_status_stopped)
                        }
                        StatusRow(
                            title = stringResource(R.string.multitouch_status_title),
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
                            title = stringResource(R.string.multitouch_add_title),
                            summary = stringResource(R.string.multitouch_add_summary),
                            enabled = settings.enabled,
                            onClick = { showKindPicker = true },
                        )
                    }
                )
                settings.entries.forEach { entry ->
                    add(
                        settingsCardScopeItem("multitouch-entry-${entry.id}") {
                            EntryRow(
                                entry = entry,
                                enabled = settings.enabled,
                                onClick = { editingEntry = entry },
                            )
                        }
                    )
                }
            }
        )
        settingsLazyHint(
            key = "multitouch-hint",
            text = bottomHint,
        )
    }

    if (showKindPicker) {
        WindowDialog(
            show = true,
            title = stringResource(R.string.multitouch_pick_kind_title),
            onDismissRequest = { showKindPicker = false },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                MiuixArrowRow(
                    title = stringResource(R.string.multitouch_kind_path),
                    summary = stringResource(R.string.multitouch_draw_summary),
                    onClick = {
                        showKindPicker = false
                        onOpenRecorder()
                    },
                )
                multiTouchPresetKinds.forEach { kind ->
                    MiuixArrowRow(
                        title = kind.displayName(),
                        onClick = {
                            showKindPicker = false
                            pendingKind = kind
                        },
                    )
                }
            }
        }
    }

    val kind = pendingKind
    if (kind != null) {
        WindowDialog(
            show = true,
            title = stringResource(R.string.multitouch_finger_count_title),
            summary = stringResource(
                R.string.multitouch_finger_count_summary,
                kind.displayName(),
            ),
            onDismissRequest = { pendingKind = null },
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (fingers in 1..5) {
                        // 捏合/张开/旋转是几何手势：质心与半径在多指下才有意义，单指必然触发不了。
                        val geometry = kind in GEOMETRY_GESTURES
                        FingerCell(
                            fingers = fingers,
                            modifier = Modifier.weight(1f),
                            enabled = !geometry || fingers >= 2,
                            onClick = {
                                pendingKind = null
                                onOpenActionPick(kind, fingers, "", "")
                            },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = { pendingKind = null },
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }

    val editing = editingEntry
    if (editing != null) {
        WindowDialog(
            show = true,
            title = stringResource(R.string.multitouch_edit_title),
            onDismissRequest = { editingEntry = null },
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(enabled = editing.isPath) {
                            editingEntry = null
                            onOpenPathEditor(editing)
                        },
                ) {
                    GesturePreview(entry = editing, modifier = Modifier.fillMaxWidth().height(140.dp))
                }
                if (editing.isPath) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.multitouch_edit_rerecord_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                MiuixArrowRow(
                    title = stringResource(
                        R.string.multitouch_entry_label,
                        editing.fingerCount,
                        editing.kind.displayName(),
                    ),
                    summary = gestureActionLabel(editing.action),
                    onClick = {
                        editingEntry = null
                        onOpenActionPick(
                            editing.kind,
                            editing.fingerCount,
                            encodePathStrokes(editing.pathStrokes),
                            editing.id,
                        )
                    },
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(
                        text = stringResource(R.string.multitouch_delete),
                        onClick = {
                            editingEntry = null
                            onRemoveEntry(editing.id)
                        },
                        modifier = Modifier.weight(1f),
                        minHeight = 44.dp,
                    )
                    TextButton(
                        text = stringResource(R.string.multitouch_done),
                        onClick = { editingEntry = null },
                        modifier = Modifier.weight(1f),
                        minHeight = 44.dp,
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        }
    }
}

@Composable
private fun EntryRow(
    entry: MultiTouchGestureEntry,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GesturePreview(entry = entry, modifier = Modifier.size(44.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(
                    R.string.multitouch_entry_label,
                    entry.fingerCount,
                    entry.kind.displayName(),
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = gestureActionLabel(entry.action),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 手势预览：记录手势画轨迹，预设类型画方向符号。 */
@Composable
private fun GesturePreview(entry: MultiTouchGestureEntry, modifier: Modifier) {
    if (entry.pathStrokes.isNotEmpty()) {
        Canvas(modifier = modifier) {
            val height = size.height
            entry.pathStrokes.forEachIndexed { index, stroke ->
                if (stroke.points.size < 2) return@forEachIndexed
                val path = Path()
                stroke.points.forEachIndexed { pointIndex, point ->
                    val x = point.x * height
                    val y = point.y * height
                    if (pointIndex == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path = path,
                    color = PreviewColors[index % PreviewColors.size],
                    style = Stroke(width = 3.dp.toPx()),
                )
            }
        }
    } else {
        val iconRes = gestureIconRes(entry.kind)
        val icon = gestureIcon(entry.kind)
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            when {
                iconRes != null -> Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                icon != null -> Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                else -> Text(
                    text = gestureSymbol(entry.kind),
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** 点按/捏合/张开用 Material Symbols 图标（用户提供）。 */
private fun gestureIconRes(kind: MultiPointerGesture): Int? = when (kind) {
    MultiPointerGesture.PINCH -> R.drawable.ic_gesture_pinch
    MultiPointerGesture.SPREAD -> R.drawable.ic_gesture_spread
    MultiPointerGesture.TAP,
    MultiPointerGesture.TOUCH,
    -> R.drawable.ic_gesture_touch
    MultiPointerGesture.DOUBLE_TAP -> R.drawable.ic_gesture_double_tap
    MultiPointerGesture.TRIPLE_TAP -> R.drawable.ic_gesture_triple_tap
    else -> null
}

/** 其余预设类型用 Compose 图标（旋转/长按/记录手势）。 */
private fun gestureIcon(kind: MultiPointerGesture): ImageVector? = when (kind) {
    MultiPointerGesture.ROTATE_CLOCKWISE -> Icons.Outlined.RotateRight
    MultiPointerGesture.ROTATE_COUNTER_CLOCKWISE -> Icons.Outlined.RotateLeft
    MultiPointerGesture.LONG_PRESS -> Icons.Outlined.Timer
    MultiPointerGesture.PATH -> Icons.Outlined.Draw
    else -> null
}

private fun gestureSymbol(kind: MultiPointerGesture): String = when (kind) {
    MultiPointerGesture.SWIPE_LEFT -> "←"
    MultiPointerGesture.SWIPE_RIGHT -> "→"
    MultiPointerGesture.SWIPE_UP -> "↑"
    MultiPointerGesture.SWIPE_DOWN -> "↓"
    MultiPointerGesture.SWIPE_UP_LEFT -> "↖"
    MultiPointerGesture.SWIPE_UP_RIGHT -> "↗"
    MultiPointerGesture.SWIPE_DOWN_LEFT -> "↙"
    MultiPointerGesture.SWIPE_DOWN_RIGHT -> "↘"
    else -> ""
}

@Composable
private fun FingerCell(
    fingers: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (enabled) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                },
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = fingers.toString(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
        )
    }
}

/** 需要多指才有意义的几何手势（单指时质心/半径失效）。 */
private val GEOMETRY_GESTURES = setOf(
    MultiPointerGesture.PINCH,
    MultiPointerGesture.SPREAD,
    MultiPointerGesture.ROTATE_CLOCKWISE,
    MultiPointerGesture.ROTATE_COUNTER_CLOCKWISE,
)

/** 状态行说明：设备 / 模式 / 特权通道 / 冲突，全部收在这一行里。 */
@Composable
private fun multiTouchStatusDetail(status: MultiTouchStatus): String {
    val base = when {
        !status.enabled -> stringResource(R.string.multitouch_detail_disabled)
        status.detail == "recording" -> stringResource(R.string.multitouch_detail_recording)
        status.running -> {
            val channel = when (status.channel) {
                "root" -> stringResource(R.string.multitouch_channel_root)
                "shizuku" -> stringResource(R.string.multitouch_channel_shizuku)
                else -> stringResource(R.string.multitouch_channel_observer)
            }
            val unknownDevice = stringResource(R.string.multitouch_device_unknown)
            val device = status.device.ifBlank { unknownDevice }
            stringResource(R.string.multitouch_detail_running, device, channel)
        }
        status.detail == "root-unavailable" ->
            stringResource(R.string.multitouch_detail_root_unavailable)

        status.detail == "root-start-failed" ->
            stringResource(R.string.multitouch_detail_root_start_failed)

        !status.shizukuAvailable ->
            stringResource(R.string.multitouch_detail_shizuku_unavailable)

        status.detail == "service-unavailable" ->
            stringResource(R.string.multitouch_detail_service_unavailable)

        status.detail == "device-unavailable" ->
            stringResource(R.string.multitouch_detail_device_unavailable)

        else -> stringResource(
            R.string.multitouch_detail_not_running,
            status.detail.ifBlank { "unknown" },
        )
    }
    val conflict = status.conflictDevices ?: return base
    val name = if (conflict.length > 30) conflict.take(30) + "…" else conflict
    return base + stringResource(R.string.multitouch_detail_conflict, name)
}
