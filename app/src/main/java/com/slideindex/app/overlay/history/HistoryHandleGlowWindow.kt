package com.slideindex.app.overlay.history

import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import com.slideindex.app.overlay.OverlayCompose
import com.slideindex.app.overlay.OverlayComposeOwner
import com.slideindex.app.overlay.OverlayWindowTypes
import com.slideindex.app.ui.theme.OverlayAwareModuleTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 把手的「提醒流光」窗：**只画光、且不可触摸**。
 *
 * ## 为什么要单独一个窗
 *
 * 用户反馈"调最窄也误触、点一下就呼出"，根因是把手窗的触摸区写死 48dp 方块（见
 * [HistoryFloatContent] 的 KDoc）。把触摸区收到"竖条真实宽度"（12dp）之后，
 * 原本**往条内侧铺满那 48dp** 的辉光就没有地方画了 —— 会被窗外裁掉。
 *
 * 于是按用户选定的方案拆成两个窗：
 * - **触摸窗**（`HistoryFloatService` 的把手窗）：12–14dp 宽，画条本体，吃所有手势；
 * - **本窗**：48dp 见方、`FLAG_NOT_TOUCHABLE`，画"过曝主带 + 拖尾 + 辉光"。
 *
 * 两个窗的外缘都对齐屏幕外缘、顶边同一 y，且绘制几何共用同一套常量
 * （条宽/条高/间距/圆角），所以条与光落在**同一个屏幕位置**，观感与拆窗之前一致。
 *
 * ## 代价（写明白）
 *
 * 1. 多一个悬浮窗（只在"有未处理提醒"时可见，其余时候 `View.GONE` 且不建动画时钟）；
 * 2. 部分 ROM（实测 Oplus/ColorOS）会把「type=2038 + `FLAG_NOT_TOUCHABLE` + `alpha > 0.8`」
 *    的窗强行压到 `alpha = 0.8`（系统原话见 `QuickWheelOverlayWindow` 的注释）。
 *    这里被压的只有"光"，条本体在**可触摸**的触摸窗里、不受影响 —— 这也是本方案
 *    优于"整窗都不可触摸"的地方。
 */
class HistoryHandleGlowWindow(
    private val context: Context,
    private val windowManager: WindowManager,
) {
    private var composeView: ComposeView? = null
    private var composeOwner: OverlayComposeOwner? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    /** 该不该有光。由把手侧算好后上报（见 `HistoryFloatContent.onGlowActiveChange`）。 */
    private val glowActive = mutableStateOf(false)

    /** true = 贴右缘（光往左漏）；false = 贴左缘（镜像）。 */
    private val gravityEnd = mutableStateOf(true)

    private var topPx = 0
    private var added = false

    /** 只是想显示/隐藏时用它：没光就不建窗（懒创建），也就没有任何动画时钟。 */
    fun setGlowActive(active: Boolean) {
        if (glowActive.value == active) return
        glowActive.value = active
        if (active) {
            if (!ensureWindow()) return
            composeView?.visibility = View.VISIBLE
        } else {
            composeView?.visibility = View.GONE
        }
    }

    /** 跟把手一起换边。**不做"值没变就返回"**：旋屏后屏宽变了，x 必须重算。 */
    fun setGravityEnd(end: Boolean) {
        gravityEnd.value = end
        applyX()
    }

    /** 跟把手一起动（把手窗改 `params.y` 时同步调它）。 */
    fun moveTo(top: Int) {
        if (topPx == top) return
        topPx = top
        val params = layoutParams ?: return
        params.y = top
        composeView?.let { runCatching { windowManager.updateViewLayout(it, params) } }
    }

    /** 把手被藏起来（全屏/横屏/息屏/截图）时也把光收掉 —— 由 Service 统一调 [setGlowActive]。 */
    fun destroy() {
        val view = composeView
        composeView = null
        layoutParams = null
        glowActive.value = false
        if (view != null) {
            runCatching { windowManager.removeView(view) }
        }
        OverlayCompose.teardownOverlayCompose(view, composeOwner)
        composeOwner = null
        added = false
    }

    private fun ensureWindow(): Boolean {
        if (added) return true
        val boxPx = boxPx()
        val overlayContext = OverlayCompose.themedContext(context)
        val owner = OverlayComposeOwner()
        val view = OverlayCompose.createComposeView(overlayContext, owner).apply {
            visibility = View.GONE
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setContent { GlowLayer() }
        }
        val params = WindowManager.LayoutParams(
            boxPx,
            boxPx,
            OverlayWindowTypes.overlayWindowType(context),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                // ⚠️ 光必须放行触摸：它铺满整个 48dp 见方，一点点都不能挡到底下的 App / 把手触摸窗。
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            y = topPx
        }
        val ok = runCatching { windowManager.addView(view, params) }
            .onFailure { Log.e(TAG, "addView failed", it) }
            .isSuccess
        if (!ok) {
            OverlayCompose.clearViewTreeOwners(view)
            owner.destroy()
            return false
        }
        composeView = view
        composeOwner = owner
        layoutParams = params
        added = true
        applyX()
        return true
    }

    /** 窗外缘对齐屏幕外缘：贴右缘时 `x = 屏宽 − 窗宽`，贴左缘时 `x = 0`。 */
    private fun applyX() {
        val params = layoutParams ?: return
        val view = composeView ?: return
        params.x = if (gravityEnd.value) {
            (context.resources.displayMetrics.widthPixels - boxPx()).coerceAtLeast(0)
        } else {
            0
        }
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun boxPx(): Int =
        (HANDLE_BOX_DP * context.resources.displayMetrics.density).toInt()

    @Composable
    private fun GlowLayer() {
        // 唯一的动画时钟：`glowActive == false` 时连它都不进组合（= 没提醒就一帧不跑）。
        val phase = if (glowActive.value) HistoryHandleGlowSweep() else null
        val observed = phase?.let { rememberGlowObservedPhase(it) }
        // 光只在"没被按住"时可见，而那时的条宽恒为静止值 —— 所以这里用固定宽度，
        // 不必像触摸窗那样跟着 `active` 做 9→11dp 的动画。
        val barWidth = remember { mutableStateOf(HANDLE_BAR_WIDTH_DP.dp) }
        OverlayAwareModuleTheme {
            val accent = MiuixTheme.colorScheme.primary
            Box(
                modifier = Modifier
                    .size(HANDLE_BOX_DP.dp)
                    .historyHandlePaint(
                        // 条本体不在这一层画（drawBar = false），这几个颜色只是为了满足签名。
                        barColor = accent,
                        barFillAlpha = 1f,
                        barAlpha = remember { mutableStateOf(0.72f) },
                        washColor = accent.copy(alpha = 0f),
                        borderColor = accent.copy(alpha = 0f),
                        accent = accent,
                        // 掺白是条本体的事，本层不画条。
                        glowActive = false,
                        barWidth = barWidth,
                        glowObserved = observed,
                        drawBar = false,
                        drawGlow = true,
                        gravityEnd = gravityEnd.value,
                    ),
            )
        }
    }

    private companion object {
        const val TAG = "HistoryHandleGlow"
    }
}
