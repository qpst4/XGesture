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
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.util.HapticHelper

/**
 * 闪念的触觉反馈（计划 P4「触觉」：长按 = LONG_PRESS、存下 / 复制 / 删除 / 完成 = CONFIRM）。
 *
 * 把手与面板**都要用**，所以放在 history 包里而不是面板里。
 *
 * 走既有的 [HapticHelper]：它统一尊重用户的**触觉开关与强度**设置，并自带
 * `performHapticFeedback` 失败时的 `Vibrator` 兜底。它需要 `(View, AppSettings)`，
 * settings 用 overlay 里通行的 `OverlayDependencyAccess` 订阅
 * （同 `PickResultInteractiveText` / `PickResultPanelChrome`）。
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
        OverlayDependencyAccess.overlayDependencies(context)
            ?.settingsRepository
            ?.settings
            ?.collect { settings = it }
    }
    return remember(view, settings) { HistoryHaptics(view, settings) }
}
