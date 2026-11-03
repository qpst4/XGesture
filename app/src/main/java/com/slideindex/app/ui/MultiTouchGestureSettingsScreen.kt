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
import com.slideindex.app.multitouch.MultiTouchGestureSettings
import com.slideindex.app.multitouch.MultiTouchStatus
import com.slideindex.app.multitouch.multiTouchSelectableActions
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.MiuixNavigationRow
import com.slideindex.app.ui.settings.components.SettingSwitchRow
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
import com.slideindex.app.ui.settings.components.settingsLazyHint

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
) {
    var pickerBinding by remember { mutableStateOf<MultiPointerBinding?>(null) }

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
                        MiuixNavigationRow(
                            title = "运行状态",
                            summary = multiTouchStatusText(status),
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
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    multiTouchSelectableActions.forEach { action ->
                        TextButton(
                            onClick = {
                                onBindingChange(binding, action)
                                pickerBinding = null
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                        ) {
                            Text(gestureActionLabel(action))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pickerBinding = null }) { Text("取消") }
            },
        )
    }
}

private fun multiTouchStatusText(status: MultiTouchStatus): String = when {
    !status.enabled -> "未启用"
    !status.shizukuAvailable -> "Shizuku 未运行或未授权（点击重试）"
    status.running -> "观察中（点击重试）"
    status.detail == "service-unavailable" -> "Shizuku 服务启动失败（点击重试）"
    status.detail == "device-unavailable" -> "没有可读的触摸设备（点击重试）"
    status.detail.startsWith("watching") -> "观察中：${status.detail.removePrefix("watching:")}"
    else -> "未运行：${status.detail.ifBlank { "unknown" }}（点击重试）"
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
