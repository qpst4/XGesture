package com.slideindex.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Shell 面板的环境状态点。
 *
 * 圆点表达的是“当前有几个特权通道可用”，不是只表达 Shizuku：
 * - 绿/主题色：Root 与 Shizuku 都可用
 * - 黄：只有一个可用，或状态部分未知
 * - 红：两个都不可用
 * - 灰：正在探测
 */
internal enum class ShellEnvironmentStatus {
    CHECKING,
    READY,
    PARTIAL,
    UNAVAILABLE,
}

internal fun shellEnvironmentStatus(
    checking: Boolean,
    rootAvailable: Boolean?,
    shizukuGranted: Boolean,
): ShellEnvironmentStatus = when {
    checking || rootAvailable == null -> ShellEnvironmentStatus.CHECKING
    rootAvailable && shizukuGranted -> ShellEnvironmentStatus.READY
    !rootAvailable && !shizukuGranted -> ShellEnvironmentStatus.UNAVAILABLE
    else -> ShellEnvironmentStatus.PARTIAL
}

@Composable
internal fun ShellEnvironmentStatus.color(): Color = when (this) {
    ShellEnvironmentStatus.CHECKING -> MaterialTheme.colorScheme.outline
    ShellEnvironmentStatus.READY -> MaterialTheme.colorScheme.primary
    ShellEnvironmentStatus.PARTIAL -> Color(0xFFFFC107)
    ShellEnvironmentStatus.UNAVAILABLE -> MaterialTheme.colorScheme.error
}
