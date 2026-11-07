package com.slideindex.app.overlay.history

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slideindex.app.overlay.panels.R
import com.slideindex.app.overlay.OverlayCompose
import com.slideindex.app.overlay.OverlayComposeOwner
import com.slideindex.app.overlay.OverlayWindowTypes
import com.slideindex.app.ui.theme.OverlayAwareModuleTheme
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 存下后的内容预览 peek（设计稿 `.peek`）：「只闪一下的事件，不是常驻物」。
 *
 * - 独立小窗，必须 `FLAG_NOT_TOUCHABLE`（否则会在把手上方挡点击）；
 * - 位置贴着把手的**内侧**（设计稿 `right:58px` = 48dp 命中区 + 10dp 间距；
 *   触摸区收窄到 [HANDLE_TOUCH_WIDTH_DP] 后按新宽度算），纵向与把手居中；
 * - 把手可以贴左/右缘（[setGravityEnd]），peek 与滑入方向一起镜像；
 * - 1.2 秒后自己消失（设计稿 `showPeek` 里的 `setTimeout(…, 1200)`）。
 *
 * 事件源是 [HistorySaveSignal]（由 `HistoryFloatService` 订阅）。
 */
class HistorySavePeekWindow(
    private val context: Context,
    private val windowManager: WindowManager,
) {
    private var composeView: ComposeView? = null
    private var owner: OverlayComposeOwner? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private val displayText = mutableStateOf<String?>(null)
    private val visible = mutableStateOf(false)

    /** true = 把手贴右缘（peek 在其左侧、从右滑入）；false = 镜像。 */
    private val gravityEnd = mutableStateOf(true)
    private var measuredSize = IntSize.Zero
    private var anchorCenterY = 0
    private val mainHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hide() }

    /** 把手换边时调它：位置与滑入方向都跟着镜像（不做"值没变就返回"，旋屏后屏宽变了要重算）。 */
    fun setGravityEnd(end: Boolean) {
        gravityEnd.value = end
        applyPosition()
    }

    /** [handleCenterY] = 把手在屏幕上的纵向中心（px）。 */
    fun show(savedText: String, handleCenterY: Int) {
        anchorCenterY = handleCenterY
        val preview = savedText.trim().let { text ->
            if (text.length > PEEK_MAX_CHARS) text.take(PEEK_MAX_CHARS) + "…" else text
        }
        if (preview.isBlank()) return
        if (!ensureWindow()) return
        displayText.value = preview
        composeView?.visibility = View.VISIBLE
        visible.value = true
        applyPosition()
        // 连续存下：重新计时（设计稿 `clearTimeout` 后再 setTimeout）。
        mainHandler.removeCallbacks(hideRunnable)
        mainHandler.postDelayed(hideRunnable, PEEK_DURATION_MS)
    }

    fun hide() {
        mainHandler.removeCallbacks(hideRunnable)
        visible.value = false
        mainHandler.postDelayed({
            if (!visible.value) {
                displayText.value = null
                composeView?.visibility = View.INVISIBLE
            }
        }, PEEK_ANIM_MS)
    }

    fun destroy() {
        mainHandler.removeCallbacks(hideRunnable)
        val view = composeView
        val params = layoutParams
        val previousOwner = owner
        composeView = null
        layoutParams = null
        owner = null
        if (view != null && params != null) {
            runCatching { windowManager.removeView(view) }
        }
        OverlayCompose.teardownOverlayCompose(view, previousOwner)
    }

    private fun ensureWindow(): Boolean {
        if (composeView != null) return true
        val overlayContext = OverlayCompose.themedContext(context)
        val composeOwner = OverlayComposeOwner()
        val view = OverlayCompose.createComposeView(overlayContext, composeOwner).apply {
            visibility = View.INVISIBLE
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setContent {
                OverlayAwareModuleTheme {
                    HistorySavePeekContent(
                        visibleState = visible,
                        displayTextState = displayText,
                        gravityEndState = gravityEnd,
                        onSized = { width, height ->
                            measuredSize = IntSize(width, height)
                            applyPosition()
                        },
                    )
                }
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            OverlayWindowTypes.overlayWindowType(context),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        val added = runCatching { windowManager.addView(view, params) }
            .onFailure { Log.e(TAG, "addView failed", it) }
            .isSuccess
        if (!added) {
            OverlayCompose.clearViewTreeOwners(view)
            composeOwner.destroy()
            return false
        }
        composeView = view
        layoutParams = params
        owner = composeOwner
        return true
    }

    /** 贴把手**内侧**（设计稿 `right:58px`），纵向压在屏幕 44% 处。 */
    private fun applyPosition() {
        val params = layoutParams ?: return
        val view = composeView ?: return
        if (measuredSize.width <= 0 || measuredSize.height <= 0) {
            view.requestLayout()
            return
        }
        val screenWidthPx = context.resources.displayMetrics.widthPixels
        val density = context.resources.displayMetrics.density
        // 把手的屏幕足迹 = 触摸区宽度 + 一点间距（触摸区已经收到"竖条真实宽度"）。
        val handleFootprintPx = ((HANDLE_TOUCH_WIDTH_DP + PEEK_GAP_DP) * density).toInt()
        params.x = if (gravityEnd.value) {
            (screenWidthPx - handleFootprintPx - measuredSize.width).coerceAtLeast(0)
        } else {
            handleFootprintPx
        }
        // 设计稿 `.peek { top: 44% }`：纵向压在屏幕 44% 处（把手线附近），不是与把手精确居中。
        val topAt44 = (context.resources.displayMetrics.heightPixels * 0.44f).toInt()
        params.y = (topAt44 - measuredSize.height / 2)
            .coerceAtLeast(0)
            .coerceAtMost((context.resources.displayMetrics.heightPixels - measuredSize.height).coerceAtLeast(0))
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private companion object {
        const val TAG = "HistorySavePeek"
        const val PEEK_DURATION_MS = 1_200L
        const val PEEK_ANIM_MS = 220L
        const val PEEK_MAX_CHARS = 18
        const val PEEK_GAP_DP = 10
    }
}

@Composable
private fun HistorySavePeekContent(
    visibleState: MutableState<Boolean>,
    displayTextState: MutableState<String?>,
    gravityEndState: MutableState<Boolean>,
    onSized: (Int, Int) -> Unit,
) {
    val visible by visibleState
    val text by displayTextState
    val density = LocalDensity.current
    val slidePx = with(density) { 8.dp.roundToPx() }
    // 滑入/滑出方向跟着贴边侧走：贴右缘从右边滑进来，贴左缘从左滑进来。
    val offsetX = if (gravityEndState.value) slidePx else -slidePx
    AnimatedVisibility(
        visible = visible,
        // 注：fade 的 spec 是 Float、slide 的是 IntOffset，不能共用一个变量。
        enter = fadeIn(tween(durationMillis = 200, easing = FastOutSlowInEasing)) +
            slideInHorizontally(
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                initialOffsetX = { offsetX },
            ),
        exit = fadeOut(tween(durationMillis = 200, easing = FastOutSlowInEasing)) +
            slideOutHorizontally(
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                targetOffsetX = { offsetX },
            ),
        modifier = Modifier.onSizeChanged { onSized(it.width, it.height) },
    ) {
        text?.let { HistorySavePeekCard(text = it) }
    }
}

@Composable
private fun HistorySavePeekCard(text: String) {
    val theme = historyTheme()
    // 设计稿 `.peek { max-width:250px; padding:9px 13px; border-radius: var(--r-md)=14;
    //                 font-size: var(--f-meta); line-height: 1.5 }` + `.peek .k { font-size:9.5px; color: var(--sub) }`
    val shape = RoundedCornerShape(HistoryRadii.md)
    Column(
        modifier = Modifier
            .widthIn(max = 250.dp)
            .shadow(10.dp, shape)
            .clip(shape)
            .background(theme.glassFill)
            .border(width = 1.dp, color = theme.glassBorder, shape = shape)
            .padding(horizontal = 13.dp, vertical = 9.dp),
    ) {
        Text(
            text = stringResource(R.string.stash_peek_saved),
            style = androidx.compose.ui.text.TextStyle(
                fontSize = 9.5.sp,
                letterSpacing = 0.4.sp,
            ),
            color = theme.sub,
        )
        Text(
            text = text,
            style = androidx.compose.ui.text.TextStyle(
                fontSize = HistoryFontSizes.meta,
                lineHeight = 17.sp,
            ),
            color = theme.text,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}
