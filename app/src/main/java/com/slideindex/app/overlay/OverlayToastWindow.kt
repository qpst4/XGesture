package com.slideindex.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slideindex.app.overlay.history.HistoryDurations
import com.slideindex.app.overlay.history.HistoryEasing
import com.slideindex.app.overlay.history.HistoryFontSizes
import com.slideindex.app.overlay.history.HistoryRadii
import com.slideindex.app.overlay.history.HistorySpacing
import com.slideindex.app.overlay.history.historyTheme
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.ui.theme.OverlayAwareModuleTheme
import top.yukonga.miuix.kmp.basic.Text

/**
 * 覆盖层提示条：用独立悬浮窗显示一句提示。
 *
 * 为什么需要它：本应用的很多面板是**全屏浮窗**（冰箱面板就是 `MATCH_PARENT` 的
 * `TYPE_APPLICATION_OVERLAY`），在面板里调 `Toast` 会被面板自己盖住，用户看不到。
 *
 * 窗口挂点（**踩过两次坑，别改回去**）：必须挂到**无障碍服务那层**
 * （[SlideIndexAccessibilityService.overlayHostContext]，`TYPE_ACCESSIBILITY_OVERLAY`，`mBaseLayer` 311000），
 * 和悬浮球/触钮/侧栏同层。挂在应用层（`TYPE_APPLICATION_OVERLAY`）时，无论 `addView` 顺序如何
 * 都会被面板盖住 —— 真机 `dumpsys window` 已确认两层的层级差。
 *
 * 另外**不要**用 `PermissionHelper.canDrawOverlays()` 当闸门：Flyme 上它可能在应用确实有权限时
 * 返回 false，于是窗口压根不建、悄悄退回系统 Toast（现象与「提示看不见」一模一样）。
 */
object OverlayToastWindow {
    private const val TAG = "OverlayToastWindow"

    /** 自动消失时长：比系统 Toast 的 SHORT 稍长，够读完一句话。 */
    private const val AUTO_HIDE_MS = 3_200L
    private const val BOTTOM_MARGIN_DP = 96

    private val mainHandler = Handler(Looper.getMainLooper())
    private val messageState: MutableState<String?> = mutableStateOf(null)
    private val visibleState: MutableState<Boolean> = mutableStateOf(false)

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var owner: OverlayComposeOwner? = null
    private var hideToken = 0

    fun show(context: Context, message: String) {
        if (message.isBlank()) return
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { show(context, message) }
            return
        }
        Log.i(TAG, "show: $message")
        if (!attach(context)) {
            // 没有悬浮窗权限时退回系统 Toast：能显示就显示，不能显示也不静默。
            runCatching { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
            return
        }
        messageState.value = message
        visibleState.value = true
        val token = ++hideToken
        mainHandler.postDelayed({
            if (token == hideToken) hide()
        }, AUTO_HIDE_MS)
    }

    fun hide() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { hide() }
            return
        }
        ++hideToken
        visibleState.value = false
        mainHandler.postDelayed({
            if (visibleState.value) return@postDelayed
            detach()
        }, 240)
    }

    private fun attach(context: Context): Boolean {
        if (composeView != null) return true
        val appContext = context.applicationContext
        // ⚠️ 关键：窗口要挂在**无障碍服务那层**（`TYPE_ACCESSIBILITY_OVERLAY`，mBaseLayer 311000），
        // 项目自己的悬浮球/触钮/侧栏都在那一层。冰箱面板是应用层的 `TYPE_APPLICATION_OVERLAY`
        // （mBaseLayer 明显更低），挂在应用层就会被面板盖住 —— 这正是前两版「提示看不见」的原因。
        // 拿不到无障碍宿主时才退回应用层，那时至少还有系统 Toast 兜底。
        val a11yHost = SlideIndexAccessibilityService.overlayHostContext()
        val host = a11yHost ?: appContext
        val wm = host.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: run {
            Log.w(TAG, "attach: no WindowManager")
            return false
        }
        val overlayContext = OverlayCompose.themedContext(host)
        val dialogOwner = OverlayComposeOwner()
        val view = OverlayCompose.createComposeView(overlayContext, dialogOwner).apply {
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setContent {
                OverlayAwareModuleTheme {
                    OverlayToastContent(visibleState = visibleState, messageState = messageState)
                }
            }
        }
        val density = appContext.resources.displayMetrics.density
        val windowType = if (a11yHost != null) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            y = (BOTTOM_MARGIN_DP * density).toInt()
            // 显式写 1.0：部分 ROM（实测 ColorOS）会把「2038 + NOT_TOUCHABLE + alpha>0.8」的窗口
            // 自动降到 0.8，显式声明可以少一次意外。
            alpha = 1.0f
            OverlayWindowTypes.ensureNoBrightnessOverride(this)
        }
        // ⚠️ 不要用 `PermissionHelper.canDrawOverlays()` 当闸门：魅族 Flyme 上它可能在应用确实
        // 有悬浮窗权限时仍返回 false，于是窗口压根不建、直接退回系统 Toast —— 那又会被面板挡住，
        // 现象和「提示看不见」一模一样（本模块的第一版就是这么踩进去的）。这里直接尝试 addView，
        // 真没权限它会抛异常，那时再回退。
        val added = runCatching {
            wm.addView(view, params)
            true
        }.getOrElse { error ->
            Log.w(TAG, "attach: addView failed (${error.javaClass.simpleName}: ${error.message})")
            OverlayCompose.disposeComposeView(view)
            dialogOwner.destroy()
            false
        }
        if (!added) return false
        Log.i(TAG, "attach: toast window added, type=$windowType host=${a11yHost != null}")
        windowManager = wm
        composeView = view
        owner = dialogOwner
        return true
    }

    private fun detach() {
        val view = composeView ?: return
        val wm = windowManager
        val dialogOwner = owner
        composeView = null
        owner = null
        windowManager = null
        if (wm != null) {
            runCatching { wm.removeView(view) }
        }
        OverlayCompose.teardownOverlayCompose(view, dialogOwner)
    }
}

@Composable
private fun OverlayToastContent(
    visibleState: MutableState<Boolean>,
    messageState: MutableState<String?>,
) {
    // 视觉规格对齐项目自己的提示条（`overlay/history/HistoryToast` + `HistoryPanelTokens`）：
    // 磨砂纵向渐变填充 + 1dp 玻璃描边 + 14dp 圆角 + 18dp 投影、字号取 body（13.5sp）。
    // 这里刻意写成本地常量而不是引用那几个 internal token：本组件是全局提示，不该依赖
    // 历史面板的内部 token（它们会随那个功能的改版一起动）。
    val theme = historyTheme()
    val enter = fadeIn(tween(HistoryDurations.d2)) +
        slideInVertically(tween(HistoryDurations.d3, easing = HistoryEasing.out)) { it / 3 }
    val exit = fadeOut(tween(HistoryDurations.d2)) +
        slideOutVertically(tween(HistoryDurations.d3)) { it / 3 }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HistorySpacing.s6),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = visibleState.value,
            enter = enter,
            exit = exit,
        ) {
            Text(
                text = messageState.value.orEmpty(),
                color = theme.text,
                fontSize = HistoryFontSizes.body,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .widthIn(max = 340.dp)
                    .shadow(18.dp, RoundedCornerShape(HistoryRadii.md))
                    .clip(RoundedCornerShape(HistoryRadii.md))
                    .background(theme.glassFill)
                    .border(1.dp, theme.glassBorder, RoundedCornerShape(HistoryRadii.md))
                    .padding(horizontal = HistorySpacing.s5, vertical = HistorySpacing.s3),
            )
        }
    }
}
