package com.slideindex.app.overlay.history

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.SettingsFlowHolder
import com.slideindex.app.util.HapticHelper

/**
 * 闪念的触觉反馈（长按 = LONG_PRESS、存下 / 复制 / 删除 / 完成 = CONFIRM）。
 *
 * 把手与面板**都要用**；`:app` 与 `:feature:overlay-panels` 共用这一份实现。
 *
 * 走既有的 [HapticHelper]：它统一尊重用户的**触觉开关与强度**设置，并自带
 * `performHapticFeedback` 失败时的 `Vibrator` 兜底。
 */
class HistoryHaptics(
    private val view: View,
    private val settings: AppSettings,
) {
    /** 确认类动作：存下 / 复制 / 删除 / 完成 / 星标。 */
    fun confirm() {
        HapticHelper.actionConfirm(view, settings)
    }

    /** 长按类动作（把手长按 = 速记入口）。 */
    fun longPress() {
        HapticHelper.longThreshold(view, settings)
    }

    /** 轻一点的选择类反馈（切换标签 chip / 菜单项）。 */
    fun tick() {
        HapticHelper.actionTick(view, settings)
    }
}

@Composable
fun rememberHistoryHaptics(): HistoryHaptics {
    val view = LocalView.current
    val context = LocalContext.current.applicationContext
    var settings by remember { mutableStateOf(AppSettings()) }
    LaunchedEffect(context) {
        SettingsFlowHolder.settingsFlow(context)?.collect { settings = it }
    }
    return remember(view, settings) { HistoryHaptics(view, settings) }
}
