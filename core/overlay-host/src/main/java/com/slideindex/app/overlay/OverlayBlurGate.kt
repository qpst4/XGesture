package com.slideindex.app.overlay

import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 悬浮窗模糊的**唯一决策点**：用户总开关 × 系统跨窗模糊能力。
 *
 * 覆盖范围只有两条路：
 * - A：面板内 backdrop（`LocalFrostedGlassBackdrop` / `LocalFrostedGlassDrawable` / 小组件面板的反射实现）；
 * - B：窗口级跨窗模糊（`FLAG_BLUR_BEHIND` + `setBlurBehindRadius`）。
 *
 * **不含**壁纸/截图模糊——那是"把壁纸抠出来自己糊"，与跨窗模糊是两套语义。
 *
 * 调用点只表达意图（我要多强、单项开关是否允许），能不能糊一律问这里；关闭或系统不支持时
 * 由各入口降级为实色底，**不允许**塌成一块半透明 tint。
 *
 * 读设置的 Compose 版本（`rememberOverlayBlurEnabled`）留在 `:app`：它要依赖 DI 门面，
 * 而这一层只做纯决策。
 */
object OverlayBlurGate {

    /** 纯决策：用户总开关关掉、或系统不支持跨窗模糊，都不许糊。 */
    fun isBlurAllowed(userEnabled: Boolean, systemBlurEnabled: Boolean): Boolean =
        userEnabled && systemBlurEnabled

    /** 系统此刻是否支持跨窗模糊（部分 ROM / 省电模式会返回 false）。 */
    fun isSystemBlurEnabled(windowManager: WindowManager?): Boolean =
        runCatching { windowManager?.isCrossWindowBlurEnabled == true }.getOrDefault(false)

    fun canBlur(windowManager: WindowManager?, userEnabled: Boolean): Boolean =
        isBlurAllowed(userEnabled, isSystemBlurEnabled(windowManager))

    /**
     * 不许糊时把半径归零，供"半径 > 0 即开模糊"的既有调用点直接使用。
     */
    fun effectiveBlurRadiusDp(userEnabled: Boolean, radiusDp: Int): Int =
        if (userEnabled) radiusDp else 0
}

/**
 * 读取全局模糊开关（Compose 侧）。
 *
 * 设置还没就绪（宿主拿不到依赖、或首帧尚未 collect 到）时按默认「开启」处理，
 * 与设置项默认值一致，避免启动瞬间闪一下实色面板。
 *
 * 数据来自 [OverlayHostRuntime.overlayBlurEnabledFlow]：`:app` 在启动时把设置仓库的流接进来，
 * 模块自己不依赖 DI / DataStore。
 */
@Composable
fun rememberOverlayBlurEnabled(): Boolean {
    val context = LocalContext.current
    val flow: Flow<Boolean>? = remember(context) {
        OverlayHostRuntime.overlayBlurEnabledFlow?.invoke(context)
    }
    if (flow == null) return true
    val enabled by remember(flow) { flow.distinctUntilChanged() }.collectAsState(initial = true)
    return enabled
}