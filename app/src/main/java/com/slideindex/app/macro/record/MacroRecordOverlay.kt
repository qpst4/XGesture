package com.slideindex.app.macro.record

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.slideindex.app.R
import com.slideindex.app.macro.Macro
import com.slideindex.app.macro.MacroStep
import com.slideindex.app.overlay.OverlayWindowTypes
import kotlin.math.abs
import kotlin.math.roundToInt

/** 浮动条配色（`docs/macro-port-plan.md` §6 的固定值）。 */
private object BarColors {
    // 注意：这里**必须写满 8 位 ARGB**。写成 6 位（如 0xF5F7FB）在 Kotlin 里是 0x00F5F7FB，
    // alpha=0 → 全透明：文字会"有布局但看不见"（真机踩过：浮动条上只有按钮底色、一个字都没有）。
    val cardFill = 0xF015171D.toInt()
    val cardStroke = 0xFF2E323D.toInt()
    val dot = 0xFFFF7A73.toInt()
    val secondaryText = 0xFF8D929E.toInt()
    val pauseFill = 0xFF242733.toInt()
    val primaryText = 0xFFF5F7FB.toInt()
    val stopFill = 0x1FFF7A73
    val stopStroke = 0x55FF7A73
    val stopText = 0xFFFF7A73.toInt()
    val onAccentDark = 0xFF06131D.toInt()
    val onAccentLight = 0xFFF5F7FB.toInt()
}

/**
 * 录制浮动条：窗口 + 布局 + 两个状态。
 *
 * 视觉数值照 `docs/macro-port-plan.md` §6 抄（宽 196dp、圆角 20dp、按钮高 32dp…），
 * 文案走 `strings_macro_record.xml`。
 *
 * 三件事要留意：
 * 1. **窗口类型固定 [WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY]**：
 *    所以 `context` 必须是**无障碍服务**（或它的浮层宿主）上下文，用 Application 上下文 `addView` 会抛异常。
 * 2. 不可聚焦、非模态：`FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL` —— 条外的触摸照常落到下面的 App。
 * 3. 可拖动：按住第一行（含右侧拖动手柄）拖动，直接改 `layoutParams.x/y` 再 `updateViewLayout`。
 *
 * @param recorder 被这块条控制的录制器（按钮直接驱动它）。
 * @param accentColor 强调色实心（"保存"按钮的填充色，也是文字颜色自适应的依据）。
 * @param onSaved 保存成功后的回调（界面用它切回编辑页）。
 * @param onDiscarded 用户点"放弃"后的回调（不落盘）。
 */
class MacroRecordOverlay(
    private val hostContext: Context,
    val recorder: MacroRecorder,
    private val accentColor: Int = DEFAULT_ACCENT_COLOR,
    private val onSaved: (Macro) -> Unit = {},
    private val onDiscarded: () -> Unit = {},
    /** 控制条被拖到新位置后回调（捕获层要跟着重算"洞"）。 */
    private val onMoved: (() -> Unit)? = null,
) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val density = hostContext.resources.displayMetrics.density

    private var windowManager: WindowManager? = null
    private var rootView: FrameLayout? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var countView: TextView? = null
    private var leftButton: TextView? = null
    private var middleButton: TextView? = null
    private var rightButton: TextView? = null

    /** 确认态（"放弃这段录制？"）。 */
    private var confirming = false

    val isShowing: Boolean get() = rootView != null

    /** 显示浮动条；重复调用是幂等的。 */
    fun show() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { show() }
            return
        }
        if (rootView != null) return

        val manager = hostContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        var params = buildLayoutParams()
        val content = buildContentView()

        windowManager = manager
        rootView = content
        layoutParams = params
        var added = runCatching { manager.addView(content, params) }
        if (added.isFailure) {
            // 应用层悬浮窗不可用（没授权/被 ROM 拒）→ 回退无障碍浮层，别让录制条直接消失。
            Log.w(TAG, "应用层悬浮窗不可用，回退无障碍浮层", added.exceptionOrNull())
            params.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            layoutParams = params
            added = runCatching { manager.addView(content, params) }
        }
        if (added.isFailure) {
            Log.w(TAG, "无法添加录制浮动条", added.exceptionOrNull())
            windowManager = null
            rootView = null
            layoutParams = null
            return
        }

        recorder.onStepsChanged = { actions, waits -> onStepsChanged(actions, waits) }
        // 条的高度会因为"已录 N 步"计数文本而变化：一变就通知捕获层重算洞，
        // 否则新增的那一截会露在捕获窗底下，从那里起手拖动就会被录成手势。
        content.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            Log.i(TAG, "bar layout ${view.width}x${view.height}")
            onMoved?.invoke()
        }
        refresh()
    }

    /**
     * 控制条当前在屏幕上的矩形（捕获层据此"挖洞"）。
     *
     * 用窗口参数 `x/y + 视图宽高` 和 `getLocationOnScreen` **取并集**：
     * - 参数是即时的，拖动过程中不会慢一帧；
     * - 屏幕坐标含系统 inset，避免状态栏/挖孔导致整体偏移。
     * 并集只会让洞略大（少录一条边），但绝不会把控制条露在捕获窗底下。
     */
    internal fun barRectOnScreen(): Rect? {
        val view = rootView ?: return null
        if (!view.isAttachedToWindow) return null
        if (view.width <= 0 || view.height <= 0) return null
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val params = layoutParams
        val left = minOf(location[0], params?.x ?: location[0])
        val top = minOf(location[1], params?.y ?: location[1])
        val right = maxOf(location[0] + view.width, (params?.x ?: location[0]) + view.width)
        val bottom = maxOf(location[1] + view.height, (params?.y ?: location[1]) + view.height)
        return Rect(left, top, right, bottom)
    }

    /** 控制条完成布局后执行（捕获层必须等它有了真实尺寸再"挖洞"）。 */
    internal fun runAfterLayout(block: () -> Unit) {
        val view = rootView
        if (view == null) {
            mainHandler.post(block)
            return
        }
        if (view.isLaidOut) {
            block()
        } else {
            view.post { if (rootView != null) block() }
        }
    }

    /** 这一步点击是否落在控制条上（兜底：即使层级又乱了，也别把按钮点击存进宏）。 */
    private fun isTapOnBar(tap: MacroStep.Tap): Boolean {
        val rect = barRectOnScreen() ?: return false
        val screen = ScreenSize.real(hostContext)
        val x = (tap.x * screen.widthPx).roundToInt()
        val y = (tap.y * screen.heightPx).roundToInt()
        return rect.contains(x, y)
    }

    /** 收起浮动条（不落盘）。 */
    fun dismiss() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismiss() }
            return
        }
        recorder.onStepsChanged = null
        val view = rootView ?: return
        rootView = null
        runCatching { windowManager?.removeView(view) }
        windowManager = null
        layoutParams = null
        confirming = false
        countView = null
        leftButton = null
        middleButton = null
        rightButton = null
    }

    // ── 布局 ──────────────────────────────────────────────────────────────

    private fun buildLayoutParams(): WindowManager.LayoutParams {
        val metrics = hostContext.resources.displayMetrics
        val barWidth = dp(196f)
        return WindowManager.LayoutParams().apply {
            width = barWidth
            height = WindowManager.LayoutParams.WRAP_CONTENT
            // 优先应用层悬浮窗（见 MacroRecordingTouchCapture 里的说明：Flyme 给无障碍浮层叠 80% alpha，
            // 且应用层悬浮窗才让"后台启动目标 App"免被系统拦）。
            type = OverlayWindowTypes.fullscreenLauncherWindowType(hostContext)
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.TOP or Gravity.START
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            buttonBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            // 右边缘留 12dp、距顶 48dp
            x = (metrics.widthPixels - barWidth - dp(12f)).coerceAtLeast(0)
            y = dp(48f)
        }
    }

    private fun buildContentView(): FrameLayout {
        val root = FrameLayout(hostContext)

        val card = LinearLayout(hostContext).apply {
            orientation = LinearLayout.VERTICAL
            background = pill(BarColors.cardFill, BarColors.cardStroke, cornerDp = 20f)
            elevation = dp(10f).toFloat()
            setPadding(dp(10f), dp(9f), dp(10f), dp(10f))
        }
        root.addView(
            card,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        val header = buildHeaderRow()
        card.addView(header)
        attachDrag(header)

        val count = TextView(hostContext).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(6f) }
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(BarColors.secondaryText)
        }
        countView = count
        card.addView(count)

        val buttonRow = LinearLayout(hostContext).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(8f) }
        }
        val left = createButton { onLeftButton() }
        val middle = createButton { onMiddleButton() }
        val right = createButton { onRightButton() }
        leftButton = left
        middleButton = middle
        rightButton = right
        buttonRow.addView(left, buttonParams(isFirst = true))
        buttonRow.addView(middle, buttonParams(isFirst = false))
        buttonRow.addView(right, buttonParams(isFirst = false))
        card.addView(buttonRow)

        return root
    }

    /** 第一行：7dp 红点 + "宏" + "录制中" + 拖动手柄。 */
    private fun buildHeaderRow(): LinearLayout {
        val header = LinearLayout(hostContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        header.addView(
            View(hostContext).apply {
                layoutParams = LinearLayout.LayoutParams(dp(7f), dp(7f))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(BarColors.dot)
                }
            },
        )

        header.addView(
            TextView(hostContext).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { marginStart = dp(6f) }
                text = hostContext.getString(R.string.macro_record_badge)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(textColorOn(accentColor))
            },
        )

        header.addView(
            TextView(hostContext).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { marginStart = dp(6f) }
                text = hostContext.getString(R.string.macro_record_running)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(BarColors.secondaryText)
            },
        )

        header.addView(
            TextView(hostContext).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                text = DRAG_HANDLE
                contentDescription = hostContext.getString(R.string.macro_record_drag_handle)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(BarColors.secondaryText)
            },
        )

        return header
    }

    private fun createButton(onClick: () -> Unit): TextView = TextView(hostContext).apply {
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(6f), 0, dp(6f), dp(1f))
        isClickable = true
        setOnClickListener { onClick() }
    }

    private fun buttonParams(isFirst: Boolean): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, dp(32f), 1f).apply {
            if (!isFirst) marginStart = dp(5f)
        }

    // ── 两态 ──────────────────────────────────────────────────────────────

    private fun refresh() {
        val count = countView ?: return
        val left = leftButton ?: return
        val middle = middleButton ?: return
        val right = rightButton ?: return

        if (confirming) {
            count.text = hostContext.getString(R.string.macro_record_confirm_discard)
            applyStopStyle(left, R.string.macro_record_discard)
            applyQuietStyle(middle, R.string.macro_record_keep)
            right.visibility = View.GONE
            return
        }

        count.text = countsText(recorder.actionCount, recorder.waitCount)
        applyQuietStyle(
            left,
            if (recorder.isPaused) R.string.macro_record_resume else R.string.macro_record_pause,
        )
        applyStopStyle(middle, R.string.macro_record_stop)
        applyAccentStyle(right, R.string.macro_record_save)
        right.visibility = View.VISIBLE
    }

    private fun countsText(actions: Int, waits: Int): String = if (waits > 0) {
        hostContext.getString(R.string.macro_record_counts_with_waits, actions, waits)
    } else {
        hostContext.getString(R.string.macro_record_counts, actions)
    }

    /** 暂停 / 保留配色：填充 `#242733`、1dp 描边 `#2E323D`、文字 `#F5F7FB`。 */
    private fun applyQuietStyle(button: TextView, textRes: Int) {
        button.setText(textRes)
        button.background = pill(BarColors.pauseFill, BarColors.cardStroke, cornerDp = 14f)
        button.setTextColor(BarColors.primaryText)
    }

    /** 停止 / 放弃配色：填充 `#1FFF7A73`、1dp 描边 `#55FF7A73`、文字 `#FF7A73`。 */
    private fun applyStopStyle(button: TextView, textRes: Int) {
        button.setText(textRes)
        button.background = pill(BarColors.stopFill, BarColors.stopStroke, cornerDp = 14f)
        button.setTextColor(BarColors.stopText)
    }

    /** 保存配色：强调色实心、无描边、文字按强调色亮度自适应。 */
    private fun applyAccentStyle(button: TextView, textRes: Int) {
        button.setText(textRes)
        button.background = pill(accentColor, strokeColor = null, cornerDp = 14f)
        button.setTextColor(textColorOn(accentColor))
    }

    // ── 交互 ──────────────────────────────────────────────────────────────

    private fun onLeftButton() {
        if (confirming) {
            discard()
            return
        }
        if (recorder.isPaused) recorder.resume() else recorder.pause()
        refresh()
    }

    private fun onMiddleButton() {
        if (confirming) {
            confirming = false
            refresh()
            return
        }
        confirming = true
        refresh()
    }

    private fun onRightButton() {
        if (confirming) return
        val macro = recorder.finish(dropTap = ::isTapOnBar)
        if (macro == null) {
            // 一步都没录到：留在原地，让用户继续录
            Toast.makeText(hostContext, R.string.macro_record_nothing, Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(
            hostContext,
            hostContext.getString(R.string.macro_record_saved, macro.name),
            Toast.LENGTH_SHORT,
        ).show()
        dismiss()
        onSaved(macro)
    }

    private fun discard() {
        dismiss()
        onDiscarded()
    }

    private fun onStepsChanged(actions: Int, waits: Int) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { onStepsChanged(actions, waits) }
            return
        }
        if (confirming) return
        countView?.text = countsText(actions, waits)
    }

    /** 按住第一行（含拖动手柄）拖动整条：直接改 `layoutParams.x/y` 再 `updateViewLayout`。 */
    private fun attachDrag(handleRow: View) {
        val touchSlop = ViewConfiguration.get(hostContext).scaledTouchSlop
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var dragging = false

        handleRow.setOnTouchListener { _, event ->
            val params = layoutParams ?: return@setOnTouchListener false
            val manager = windowManager ?: return@setOnTouchListener false
            val root = rootView ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    dragging = false
                    // 按下时先让捕获层按当前几何重算一次洞，避免"洞慢一帧"把这次拖动录进去。
                    onMoved?.invoke()
                    // 必须吃掉 DOWN：ViewGroup 在"没有任何子 View 消费按下"时，后续 MOVE 不再派发给子 View，
                    // 拖动手柄就永远收不到 MOVE。
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchX
                    val dy = event.rawY - touchY
                    if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        dragging = true
                    }
                    if (!dragging) return@setOnTouchListener false
                    val metrics = hostContext.resources.displayMetrics
                    val maxX = (metrics.widthPixels - root.width).coerceAtLeast(0)
                    val maxY = (metrics.heightPixels - root.height).coerceAtLeast(0)
                    params.x = (startX + dx).roundToInt().coerceIn(0, maxX)
                    params.y = (startY + dy).roundToInt().coerceIn(0, maxY)
                    runCatching { manager.updateViewLayout(root, params) }
                        .onSuccess { onMoved?.invoke() }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dragging = false
                    // 松手后再同步一次，防止下一次按下时洞还停在拖动中的旧位置。
                    onMoved?.invoke()
                    true
                }

                else -> false
            }
        }
    }

    // ── 工具 ──────────────────────────────────────────────────────────────

    private fun dp(value: Float): Int = (value * density).roundToInt()

    private fun pill(fill: Int, strokeColor: Int?, cornerDp: Float): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadius = dp(cornerDp).toFloat()
            if (strokeColor != null) setStroke(dp(1f), strokeColor)
        }

    /** `(0.299R + 0.587G + 0.114B) / 255 > 0.62` → 深色文字，否则浅色文字。 */
    private fun textColorOn(color: Int): Int {
        val luminance = (
            0.299 * Color.red(color) +
                0.587 * Color.green(color) +
                0.114 * Color.blue(color)
            ) / 255.0
        return if (luminance > 0.62) BarColors.onAccentDark else BarColors.onAccentLight
    }

    companion object {

        private const val TAG = "MacroRecordOverlay"

        /** 拖动手柄字形。 */
        const val DRAG_HANDLE = "⠿"

        /** 调用方没给强调色时的兜底（与仓库默认强调色一致的深蓝）。 */
        val DEFAULT_ACCENT_COLOR: Int = 0xFF4A57D8.toInt()
    }
}
