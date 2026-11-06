package com.slideindex.app.overlay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.slideindex.app.di.OverlayDependencyAccess
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * 读取全局模糊开关。
 *
 * 设置还没就绪（overlay 宿主拿不到依赖、或首帧尚未 collect 到）时按默认「开启」处理，
 * 与设置项默认值一致，避免启动瞬间闪一下实色面板。
 *
 * 纯决策部分在 `:core:overlay-host` 的 [OverlayBlurGate]；这里只剩"从 DI 读设置"这一层。
 */
@Composable
internal fun rememberOverlayBlurEnabled(): Boolean {
    val context = LocalContext.current
    val settingsFlow = remember(context) {
        OverlayDependencyAccess.overlayDependencies(context)?.settingsRepository?.settings
    } ?: return true
    val enabled by remember(settingsFlow) {
        settingsFlow.map { it.overlayBlurEnabled }.distinctUntilChanged()
    }.collectAsState(initial = true)
    return enabled
}
