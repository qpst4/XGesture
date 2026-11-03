package com.slideindex.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.multitouch.MultiPointerBinding
import com.slideindex.app.gesture.multitouch.MultiPointerGesture
import com.slideindex.app.gesture.multitouch.PathGestureTemplate
import com.slideindex.app.gesture.multitouch.PathPoint
import com.slideindex.app.multitouch.MultiTouchGestureSettings
import com.slideindex.app.multitouch.MultiTouchStatus
import com.slideindex.app.multitouch.multiTouchSelectableActions
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.MiuixNavigationRow
import com.slideindex.app.ui.settings.components.SettingSwitchRow
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.StatusRow
import com.slideindex.app.ui.settings.components.StatusTone
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazyHint
import kotlin.math.hypot

/**
 * 多指手势设置页（Shizuku 观察模式）。
 *
 * 注意：这是**只读观察**——触发手势时应用仍会收到同一手势（例如三指捏合时页面也会缩放）。
 * 页面底部明确提示这一点，避免被当成系统级拦截。
 */
@Composable
fun MultiTouchGestureSettingsScreen(
    settings: MultiTouchGestureSettings,
    status: MultiTouchStatus,
    onBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onBindingChange: (MultiPointerBinding, GestureAction) -> Unit,
    onRetry: () -> Unit,
    onAddPathGesture: (PathGestureTemplate) -> Unit = {},
    onRemovePathGesture: (String) -> Unit = {},
) {
    var pickerBinding by remember { mutableStateOf<MultiPointerBinding?>(null) }
    var showRecorder by remember { mutableStateOf(false) }
    var pendingPath by remember { mutableStateOf<Pair<List<PathPoint>, Int>?>(null) }
    var deleteTarget by remember { mutableStateOf<PathGestureTemplate?>(null) }

    SettingsScreenScaffold(title = "多指手势", onBack = onBack) {
        groupedCardItems(
            keyPrefix = "multitouch",
            items = buildList {
                add(
                    settingsCardScopeItem("multitouch-enabled") {
                        SettingSwitchRow(
                            title = "启用多指手势",
                            subtitle = "通过 Shizuku 读取触摸屏，三/四指手势触发动作",
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
                MultiTouchGestureSettings.configurableBindings.forEach { binding ->
                    add(
                        settingsCardScopeItem("multitouch-${binding.storageKey}") {
                            MiuixNavigationRow(
                                title = multiTouchBindingTitle(binding),
                                summary = gestureActionLabel(
                                    settings.bindings[binding] ?: GestureAction.None
                                ),
                                enabled = settings.enabled,
                                onClick = { pickerBinding = binding },
                            )
                        }
                    )
                }
                add(
                    settingsCardScopeItem("multitouch-path-recorder") {
                        MiuixNavigationRow(
                            title = "录制路径手势",
                            summary = "三指及以上画一笔，松手后选择动作",
                            enabled = settings.enabled,
                            onClick = { showRecorder = true },
                        )
                    }
                )
                settings.pathGestures.forEach { template ->
                    add(
                        settingsCardScopeItem("multitouch-path-${template.id}") {
                            MiuixNavigationRow(
                                title = template.name,
                                summary = "${template.fingerCount} 指 · " +
                                    gestureActionLabel(template.action),
                                enabled = settings.enabled,
                                onClick = { deleteTarget = template },
                            )
                        }
                    )
                }
            }
        )
        settingsLazyHint(
            key = "multitouch-hint",
            text = "观察模式不会拦截事件：触发手势时应用仍会收到同一手势。" +
                "Shizuku 重启后需要重新授权/启动；四指手势在游戏里可能误触。",
        )
    }

    val binding = pickerBinding
    if (binding != null) {
        AlertDialog(
            onDismissRequest = { pickerBinding = null },
            title = { Text("选择动作 · ${multiTouchBindingTitle(binding)}") },
            text = { ActionChoiceList { action -> onBindingChange(binding, action) } },
            confirmButton = {
                TextButton(onClick = { pickerBinding = null }) { Text("取消") }
            },
        )
    }

    if (showRecorder) {
        PathGestureRecorderDialog(
            onDismiss = { showRecorder = false },
            onFinish = { points, fingers ->
                showRecorder = false
                pendingPath = points to fingers
            },
        )
    }

    val pending = pendingPath
    if (pending != null) {
        AlertDialog(
            onDismissRequest = { pendingPath = null },
            title = { Text("选择动作 · ${pending.second} 指路径") },
            text = {
                ActionChoiceList { action ->
                    onAddPathGesture(
                        PathGestureTemplate(
                            id = "path_${System.currentTimeMillis()}",
                            name = "手势 ${settings.pathGestures.size + 1}",
                            fingerCount = pending.second,
                            points = pending.first,
                            action = action,
                        )
                    )
                    pendingPath = null
                }
            },
            confirmButton = {
                TextButton(onClick = { pendingPath = null }) { Text("取消") }
            },
        )
    }

    val deleting = deleteTarget
    if (deleting != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除手势") },
            text = { Text("确定删除「${deleting.name}」？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemovePathGesture(deleting.id)
                        deleteTarget = null
                    },
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun ActionChoiceList(onPick: (GestureAction) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        multiTouchSelectableActions.forEach { action ->
            TextButton(
                onClick = { onPick(action) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
            ) {
                Text(gestureActionLabel(action))
            }
        }
    }
}

/**
 * 路径手势录制对话框：画板内按屏幕高度归一化，与实时识别使用同一坐标系。
 */
@Composable
private fun PathGestureRecorderDialog(
    onDismiss: () -> Unit,
    onFinish: (List<PathPoint>, Int) -> Unit,
) {
    var points by remember { mutableStateOf<List<PathPoint>>(emptyList()) }
    var fingers by remember { mutableStateOf(3) }
    var drawing by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("三指及以上画一笔", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .pointerInput(Unit) {
                            val boardWidth = size.width.toFloat().coerceAtLeast(1f)
                            val boardHeight = size.height.toFloat().coerceAtLeast(1f)
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val active = event.changes.filter { it.pressed }
                                    if (active.isNotEmpty()) {
                                        fingers = active.size
                                        if (!drawing) {
                                            drawing = true
                                            points = emptyList()
                                        }
                                        // 统一按画板高度归一化，和实时识别一致。
                                        val x = active.map { it.position.x }.average().toFloat()
                                        val y = active.map { it.position.y }.average().toFloat()
                                        val next = PathPoint(x / boardHeight, y / boardHeight)
                                        val last = points.lastOrNull()
                                        if (last == null || hypot(
                                                next.x - last.x,
                                                next.y - last.y,
                                            ) >= 0.006f
                                        ) {
                                            if (points.size < 128) points = points + next
                                        }
                                    } else if (drawing) {
                                        drawing = false
                                        if (points.size >= 2) {
                                            onFinish(points, fingers)
                                        }
                                    }
                                }
                            }
                        }
                ) {
                    PathCanvas(points = points)
                }
                Spacer(modifier = Modifier.height(12.dp))
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    TextButton(
                        onClick = {
                            if (points.size >= 2) onFinish(points, fingers)
                        },
                        enabled = points.size >= 2,
                    ) { Text("完成") }
                }
            }
        }
    }
}

@Composable
private fun PathCanvas(points: List<PathPoint>) {
    val strokeColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.fillMaxSize()) {
        if (points.size < 2) return@Canvas
        val path = Path()
        for ((index, point) in points.withIndex()) {
            // 画板坐标反算：x/y 都按高度归一化。
            val x = point.x * size.height
            val y = point.y * size.height
            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        drawPath(
            path = path,
            color = strokeColor,
            style = Stroke(width = 6.dp.toPx()),
        )
    }
}

/** 状态行说明：设备 / 模式 / 特权通道 / 冲突，全部收在这一行里。 */
private fun multiTouchStatusDetail(status: MultiTouchStatus): String {
    val base = when {
        !status.enabled -> "开关未打开"
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

private fun multiTouchBindingTitle(binding: MultiPointerBinding): String {
    val prefix = when (binding.fingerCount) {
        3 -> "三指"
        4 -> "四指"
        else -> "${binding.fingerCount} 指"
    }
    val gesture = when (binding.gesture) {
        MultiPointerGesture.PINCH -> "捏合"
        MultiPointerGesture.SPREAD -> "张开"
        MultiPointerGesture.SWIPE_UP -> "上滑"
        MultiPointerGesture.SWIPE_DOWN -> "下滑"
        MultiPointerGesture.SWIPE_LEFT -> "左滑"
        MultiPointerGesture.SWIPE_RIGHT -> "右滑"
        MultiPointerGesture.ROTATE_CLOCKWISE -> "顺时针旋转"
        MultiPointerGesture.ROTATE_COUNTER_CLOCKWISE -> "逆时针旋转"
    }
    return prefix + gesture
}
