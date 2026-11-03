package com.slideindex.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.multitouch.MultiPointerBinding
import com.slideindex.app.gesture.multitouch.MultiPointerGesture
import com.slideindex.app.gesture.multitouch.PathGestureTemplate
import com.slideindex.app.multitouch.MultiTouchGestureSettings
import com.slideindex.app.multitouch.MultiTouchStatus
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
 * 注意：这是**只读观察**——触发手势时应用仍会收到同一手势（例如三指捏合时页面也会缩放）。
 */
@Composable
fun MultiTouchGestureSettingsScreen(
    settings: MultiTouchGestureSettings,
    status: MultiTouchStatus,
    onBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onRetry: () -> Unit,
    onPathPriorityChange: (Boolean) -> Unit = {},
    onOpenActionPick: (MultiPointerBinding) -> Unit = {},
    onOpenRecorder: () -> Unit = {},
    onRemovePathGesture: (String) -> Unit = {},
) {
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
                add(
                    settingsCardScopeItem("multitouch-path-priority") {
                        SettingSwitchRow(
                            title = "路径手势优先",
                            subtitle = "开启后 ≥2 指只做路径匹配，不再触发捏合/旋转/滑动",
                            checked = settings.pathPriority,
                            enabled = settings.enabled,
                            onCheckedChange = onPathPriorityChange,
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
                                onClick = { onOpenActionPick(binding) },
                            )
                        }
                    )
                }
                add(
                    settingsCardScopeItem("multitouch-path-recorder") {
                        MiuixNavigationRow(
                            title = "录制路径手势",
                            summary = "一条轨迹代表一根手指，逐条画 2–5 条",
                            enabled = settings.enabled,
                            onClick = onOpenRecorder,
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
