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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 覆盖层提示条：用独立悬浮窗显示一句提示。
 *
 * 为什么需要它：本应用的很多面板是**全屏浮窗**（冰箱面板就是 `MATCH_PARENT` 的
 * `TYPE_APPLICATION_OVERLAY`），在面板里调 `Toast` 会被面板自己盖住，用户看不到。
 *
 * 窗口挂点（**踩过两次坑，别改回去**）：必须挂到**无障碍服务那层**
 * （`OverlayHostRuntime.accessibilityHostContext`，`TYPE_ACCESSIBILITY_OVERLAY`，`mBaseLayer` 311000），
 * 和悬浮球/触钮/侧栏同层。挂在应用层（`TYPE_APPLICATION_OVERLAY`）时，无论 `addView` 顺序如何
 * 都会被面板盖住 —— 真机 `dumpsys window` 已确认两层的层级差。
 *
 * 另外**不要**用 `PermissionHelper.canDrawOverlays()` 当闸门：Flyme 上它可能在应用确实有权限时
 * 返回 false，于是窗口压根不建、悄悄退回系统 Toast（现象与「提示看不见」一模一样）。
 */
object OverlayToastWindow {
    private const val TAG = "OverlayToastWindow"

    /** 自动消失时长：对齐取词面板当年那条（`FloatBallPickResultContent` 的 2000ms）。 */
    private const val AUTO_HIDE_MS = 2_000L
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
        }, 260)
    }

    private fun attach(context: Context): Boolean {
        if (composeView != null) return true
        val appContext = context.applicationContext
        // ⚠️ 关键：窗口要挂在**无障碍服务那层**（`TYPE_ACCESSIBILITY_OVERLAY`，mBaseLayer 311000），
        // 项目自己的悬浮球/触钮/侧栏都在那一层。冰箱面板是应用层的 `TYPE_APPLICATION_OVERLAY`
        // （mBaseLayer 明显更低），挂在应用层就会被面板盖住 —— 这正是前两版「提示看不见」的原因。
        // 拿不到无障碍宿主时才退回应用层，那时至少还有系统 Toast 兜底。
        val a11yHost = OverlayHostRuntime.accessibilityHostContext?.invoke()
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
                val content: @Composable () -> Unit = {
                    OverlayToastContent(visibleState = visibleState, messageState = messageState)
                }
                val wrapper = OverlayHostRuntime.themeWrapper
                if (wrapper != null) wrapper.wrap(content) else content()
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
    // 样式取自取词面板当年那套「面板内提示」（`git show 3f114f6d^` 的
    // `FloatBallPickResultContent.kt`）：Material3 `inverseSurface` 深底 + Check 图标 +
    // 24dp 圆角 + 6dp tonal / 8dp 阴影，底部居中、24dp 下边距，
    // `fadeIn() + slideInVertically { it / 2 }` 进出。
    //
    // 为什么改成它：冰箱面板那条玻璃底提示（照闪念面板 toast 抄的 glassFill）对比度太弱，
    // 反馈偏淡；当年取词那条深底 + 勾的观感更醒目，是用户点名要的样式。
    //
    // ⚠️ 刻意**不引用** `overlay/history` 那几个 internal token：本组件是全局提示，
    // 不该绑死闪念面板的内部设计（那边改版会连累这里）。
    val enter = fadeIn()
    val exit = fadeOut()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = visibleState.value,
            enter = enter + slideInVertically { it / 2 },
            exit = exit + slideOutVertically { it / 2 },
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = messageState.value.orEmpty(),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 280.dp),
                    )
                }
            }
        }
    }
}
