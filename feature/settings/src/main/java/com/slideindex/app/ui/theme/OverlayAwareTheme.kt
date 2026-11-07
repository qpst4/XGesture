package com.slideindex.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.SettingsFlowHolder
import com.slideindex.app.ui.miuix.MiuixOverlayComposeLocals
import com.slideindex.app.ui.miuix.theme.ModuleTheme

/**
 * 浮层 / 无参数主题入口：从 [SettingsFlowHolder]（`:app` 注入）读取完整主题设置，走 [ModuleTheme]，
 * 与主界面一致（含 [com.slideindex.app.settings.AppThemeMode]）。
 */
@Composable
fun OverlayAwareModuleTheme(
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(AppSettings()) }
    LaunchedEffect(Unit) {
        val flow = SettingsFlowHolder.settingsFlow(context) ?: return@LaunchedEffect
        flow.collect { settings = it }
    }
    OverlayAwareModuleTheme(settings = settings, content = content)
}

/**
 * 已经有 [AppSettings] 的调用方用这个。
 *
 * 好处是**省掉一次异步取设置**：上面的无参重载第一帧只能拿到 `AppSettings()` 的默认值，
 * 于是面板会先按默认主题画一帧再跳成用户主题（快速菜单 / 快捷工具 / 悬浮指针这类
 * "一打开就整块可见"的浮层会闪一下）。手里已经有 settings 就别走那条路。
 */
@Composable
fun OverlayAwareModuleTheme(
    settings: AppSettings,
    content: @Composable () -> Unit,
) {
    MiuixOverlayComposeLocals {
        ModuleTheme(settings = settings, content = content)
    }
}
