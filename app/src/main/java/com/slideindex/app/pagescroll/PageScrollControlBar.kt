package com.slideindex.app.pagescroll

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.slideindex.app.R
import com.slideindex.app.overlay.OverlayWindowTypes
import kotlin.math.abs
import kotlin.math.max

/**
 * 「页面滚动」的浮动控制条。
 *
 * 外观：竖向胶囊，分层光影（半透明底 + 顶部 1px 内高光 + 1dp 描边 + 柔投影），
 * 统一 1.75dp 线宽的描边图标，暂停键是唯一的大圆钮，速度用**竖向 7 格刻度梯**（下慢上快）。
 *
 * 交互：整条可上下拖（位置记住）；速度梯可点可拖；加速/减速键支持长按连调；
 * 闲置 4 秒淡到设置里的「闲置不透明度」（默认 55%，0 即完全看不见）；手指落在条上期间回报宿主暂停滚动。
 */
internal class PageScrollControlBar(
    private val context: Context,
    private val settingsProvider: () -> PageScrollSettings,
    private val listener: Listener,
) {

    interface Listener {
        fun onReverseDirection()
        fun onSpeedDown()
        fun onSpeedUp()
        fun onTogglePause()
        fun onClose()
        fun onUserTouchingControls(touching: Boolean)
    }

    private val density = context.resources.displayMetrics.density
    private val windowManager: WindowManager? =
        context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val night: Boolean
        get() = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    private var container: BarContainer? = null
    private var params: WindowManager.LayoutParams? = null

    private var directionIcon: ImageView? = null
    private var pauseIcon: ImageView? = null
    private var pauseButton: FrameLayout? = null
    /** 暂停键的圆底填充（只建一次，刷新时原地改色，保住按下的 ripple）。 */
    private var pauseFill: GradientDrawable? = null
    /** 当前已画出来的暂停态，供点击时做"立即切一次"的乐观更新。 */
    private var visualPaused = false
    private var fasterIcon: ImageView? = null
    private var slowerIcon: ImageView? = null
    private var closeIcon: ImageView? = null
    private var levelBadge: TextView? = null

    private var repeatTask: Runnable? = null
    private var dimTask: Runnable? = null

    var isShowing: Boolean = false
        private set

    fun show(side: Side, anchorRawY: Float): Boolean {
        if (isShowing) return true
        val manager = windowManager ?: return false
        val settings = settingsProvider()

        val iconColor = iconColor()
        val accent = accentColor()

        val bar = BarContainer(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            setPadding(dp(5), dp(7), dp(5), dp(7))
            background = buildBarBackground()
            elevation = 12f * density
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(28).toFloat())
                }
            }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            onTouchStateChanged = { touching ->
                listener.onUserTouchingControls(touching)
                if (touching) wakeUp() else scheduleDim()
            }
            owner = this@PageScrollControlBar
        }

        directionIcon = iconButton(R.drawable.ic_page_scroll_direction, iconColor) {
            listener.onReverseDirection()
        }.also(bar::addView)

        // 暂停：整个条里唯一的大圆钮（M3 的"主操作"用 active indicator 填充）
        pauseButton = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(56), dp(56)).apply { topMargin = dp(8) }
            // 背景只建一次：刷新时原地改填充色，别重建 RippleDrawable —— 重建会把按下的
            // ripple 立刻抹掉，用户看到的就是"点了没反应、过一会才亮"。
            val fill = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(activeIndicatorColor())
            }
            pauseFill = fill
            background = RippleDrawable(
                ColorStateList.valueOf(rippleColor()),
                fill,
                GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) },
            )
            isClickable = true
            isFocusable = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            setOnClickListener {
                pressFeedback(this)
                // 先本地立刻切选中态，再通知滚动器；refresh 回来时值相同，等于幂等对齐。
                applyPausedVisual(!visualPaused)
                listener.onTogglePause()
            }
        }
        pauseIcon = ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(dp(24), dp(24)).apply { gravity = Gravity.CENTER }
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageResource(R.drawable.ic_page_scroll_pause)
            imageTintList = ColorStateList.valueOf(iconColor)
        }
        pauseButton!!.addView(pauseIcon)

        // 档位徽标：贴在暂停键右下角，不占额外高度
        levelBadge = TextView(context).apply {
            layoutParams = FrameLayout.LayoutParams(dp(18), dp(18)).apply {
                gravity = Gravity.END or Gravity.BOTTOM
                rightMargin = dp(1)
                bottomMargin = dp(1)
            }
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, BADGE_TEXT_SP)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(accent)
            }
            setTextColor(if (night) NIGHT_SURFACE else DAY_SURFACE)
            text = settings.speedLevel.toString()
        }
        pauseButton!!.addView(levelBadge)
        bar.addView(pauseButton)

        fasterIcon = iconButton(R.drawable.ic_page_scroll_plus, iconColor) { listener.onSpeedUp() }
            .also { view -> view.setOnLongClickListener { startRepeat(view) { listener.onSpeedUp() } } }
            .also(bar::addView)
        slowerIcon = iconButton(R.drawable.ic_page_scroll_minus, iconColor) { listener.onSpeedDown() }
            .also { view -> view.setOnLongClickListener { startRepeat(view) { listener.onSpeedDown() } } }
            .also(bar::addView)
        closeIcon = iconButton(R.drawable.ic_page_scroll_close, dimmed(iconColor, 0.72f)) {
            listener.onClose()
        }.also(bar::addView)

        val layoutParams = buildLayoutParams(side, anchorRawY)
        bar.layoutParams = layoutParams

        val added = runCatching { manager.addView(bar, layoutParams) }
        if (added.isFailure) {
            Log.w(TAG, "控制条无法挂载", added.exceptionOrNull())
            return false
        }
        container = bar
        params = layoutParams
        isShowing = true
        playAppearMorph(bar)
        refresh(settings, paused = false, directionDown = true)
        scheduleDim()
        return true
    }

    fun dismiss() {
        val bar = container ?: return
        container = null
        params = null
        isShowing = false
        directionIcon = null
        pauseIcon = null
        pauseButton = null
        pauseFill = null
        visualPaused = false
        fasterIcon = null
        slowerIcon = null
        closeIcon = null
        levelBadge = null
        stopRepeat()
        dimTask?.let { handler.removeCallbacks(it) }
        dimTask = null
        runCatching { windowManager?.removeView(bar) }
    }

    /** 刷新方向、暂停态、档位与可用状态。 */
    fun refresh(settings: PageScrollSettings, paused: Boolean, directionDown: Boolean) {
        val iconColor = iconColor()
        directionIcon?.apply {
            setImageResource(R.drawable.ic_page_scroll_direction)
            rotation = if (directionDown) 0f else 180f
            contentDescription = context.getString(R.string.page_scroll_reverse_direction)
        }
        applyPausedVisual(paused)
        fasterIcon?.apply {
            contentDescription = context.getString(R.string.page_scroll_faster)
            isEnabled = settings.speedLevel < PageScrollSettings.MAX_SPEED_LEVEL
            alpha = if (isEnabled) 1f else DISABLED_ALPHA
        }
        slowerIcon?.apply {
            contentDescription = context.getString(R.string.page_scroll_slower)
            isEnabled = settings.speedLevel > PageScrollSettings.MIN_SPEED_LEVEL
            alpha = if (isEnabled) 1f else DISABLED_ALPHA
        }
        closeIcon?.contentDescription = context.getString(R.string.page_scroll_close)
        levelBadge?.apply {
            text = settings.speedLevel.toString()
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(accentColor())
            }
            setTextColor(if (night) NIGHT_SURFACE else DAY_SURFACE)
            contentDescription = context.getString(
                R.string.page_scroll_speed_value,
                settings.speedLevel,
                PageScrollSettings.MAX_SPEED_LEVEL,
            )
        }
    }

    /**
     * 只更新暂停键的视觉（图标、着色、圆底填充），不碰窗口/滚动器。
     *
     * 点击时先调它做乐观更新，[refresh] 再调一次做对齐；由于只改已存在 drawable 的颜色、
     * 不重建 RippleDrawable，按下的 ripple 不会被抹掉，选中态也在同一帧出现。
     */
    private fun applyPausedVisual(paused: Boolean) {
        visualPaused = paused
        val accent = accentColor()
        pauseIcon?.apply {
            setImageResource(
                if (paused) R.drawable.ic_page_scroll_play else R.drawable.ic_page_scroll_pause,
            )
            imageTintList = ColorStateList.valueOf(if (paused) accent else iconColor())
            contentDescription = context.getString(
                if (paused) R.string.page_scroll_resume else R.string.page_scroll_pause,
            )
        }
        val fill = pauseFill ?: return
        if (paused) {
            fill.setColor(pausedIndicatorColor())
        } else {
            fill.setColor(activeIndicatorColor())
        }
        pauseButton?.invalidate()
    }

    /** 暂停态圆底：强调色压到约 25% 不透明度（原实现是 `and 0x22FFFFFF`，实际只剩 13%）。 */
    private fun pausedIndicatorColor(): Int {
        val accent = accentColor()
        return (accent and 0x00FFFFFF) or (0x40 shl 24)
    }

    internal fun moveTo(newY: Int) {        val bar = container ?: return
        val layoutParams = params ?: return
        layoutParams.y = newY
        runCatching { windowManager?.updateViewLayout(bar, layoutParams) }
    }

    /** 拖动结束：记住位置（按可移动范围的比例）。 */
    internal fun rememberPosition() {
        val layoutParams = params ?: return
        val screenHeight = context.resources.displayMetrics.heightPixels
        val maxY = max(dp(1), screenHeight - currentBarHeight() - dp(BOTTOM_MARGIN_DP))
        val minY = dp(TOP_MARGIN_DP)
        val ratio = if (maxY - minY <= 0) {
            0f
        } else {
            ((layoutParams.y - minY).toFloat() / (maxY - minY)).coerceIn(0f, 1f)
        }
        PageScrollPrefs.saveBarYRatio(context, ratio)
    }

    internal fun clampY(candidate: Int, barHeight: Int): Int {
        val screenHeight = context.resources.displayMetrics.heightPixels
        val minY = dp(TOP_MARGIN_DP)
        val maxY = max(minY, screenHeight - barHeight - dp(BOTTOM_MARGIN_DP))
        return candidate.coerceIn(minY, maxY)
    }

    private fun currentBarHeight(): Int {
        val height = container?.height ?: 0
        return if (height > 0) height else dp(ESTIMATED_BAR_HEIGHT_DP)
    }

    // ---- 长按连调 / 闲置淡出 ----

    private fun startRepeat(view: View, action: () -> Unit): Boolean {
        stopRepeat()
        val task = object : Runnable {
            override fun run() {
                action()
                handler.postDelayed(this, REPEAT_MS)
            }
        }
        repeatTask = task
        handler.postDelayed(task, REPEAT_START_DELAY_MS)
        view.setOnTouchListener { v, event ->
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                stopRepeat()
                v.performClick()
                v.setOnTouchListener(null)
            }
            false
        }
        return true
    }

    private fun stopRepeat() {
        repeatTask?.let { handler.removeCallbacks(it) }
        repeatTask = null
    }

    private fun scheduleDim() {
        dimTask?.let { handler.removeCallbacks(it) }
        val task = Runnable {
            val alpha = settingsProvider().controlBarIdleAlpha.coerceIn(
                PageScrollSettings.MIN_CONTROL_BAR_IDLE_ALPHA,
                PageScrollSettings.MAX_CONTROL_BAR_IDLE_ALPHA,
            )
            container?.animate()?.alpha(alpha)?.setDuration(DIM_ANIM_MS)?.start()
        }
        dimTask = task
        handler.postDelayed(task, DIM_DELAY_MS)
    }

    private fun wakeUp() {
        dimTask?.let { handler.removeCallbacks(it) }
        dimTask = null
        // 直接恢复满不透明：220ms 的淡入会让刚点亮的高亮"迟到"，看起来像点了没反应。
        container?.animate()?.cancel()
        container?.alpha = 1f
    }

    // ---- 视图构造 ----

    private fun iconButton(resId: Int, tint: Int, onClick: () -> Unit): ImageView =
        ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(BUTTON_DP), dp(BUTTON_DP))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setImageResource(resId)
            imageTintList = ColorStateList.valueOf(tint)
            isClickable = true
            isFocusable = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            background = RippleDrawable(
                ColorStateList.valueOf(rippleColor()),
                null,
                GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) },
            )
            setOnClickListener {
                pressFeedback(this)
                onClick()
            }
        }

    private fun pressFeedback(view: View) {
        view.animate().scaleX(0.94f).scaleY(0.94f).setDuration(80L)
            .withEndAction { view.animate().scaleX(1f).scaleY(1f).setDuration(140L).start() }
            .start()
    }

    /** M3 语言：tonal 平面 + 软阴影，不用渐变和描边。 */
    private fun buildBarBackground(): GradientDrawable {
        val surface = if (night) NIGHT_SURFACE else DAY_SURFACE
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = BAR_RADIUS_DP * density
            setColor(surface)
        }
    }

    /** 出现时的"形变"：从接近圆角胶囊收拢成工具条（M3E 的容器形变）。 */
    private fun playAppearMorph(view: View) {
        val drawable = view.background as? GradientDrawable
        val from = dp(BAR_RADIUS_MORPH_FROM_DP).toFloat()
        val to = BAR_RADIUS_DP * density
        view.alpha = 0f
        view.scaleX = 0.90f
        view.scaleY = 0.90f
        view.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(160L).start()
        if (drawable == null) return
        android.animation.ValueAnimator.ofFloat(from, to).apply {
            duration = 220L
            addUpdateListener { animator ->
                drawable.cornerRadius = animator.animatedValue as Float
                view.invalidateOutline()
            }
            start()
        }
    }

    private fun iconColor(): Int = if (night) NIGHT_ICON else DAY_ICON

    /** 强调色固定跟随应用（不再提供开关）。 */
    private fun accentColor(): Int = if (night) NIGHT_ACCENT else DAY_ACCENT

    private fun activeIndicatorColor(): Int =
        if (night) NIGHT_ACTIVE_CONTAINER else DAY_ACTIVE_CONTAINER

    private fun rippleColor(): Int = if (night) 0x33FFFFFF else 0x1F000000

    private fun buildLayoutParams(side: Side, anchorRawY: Float): WindowManager.LayoutParams {
        val metrics = context.resources.displayMetrics
        val barHeight = dp(ESTIMATED_BAR_HEIGHT_DP)
        val edgeMargin = dp(EDGE_MARGIN_DP)
        val remembered = PageScrollPrefs.barYRatio(context)
        val minY = dp(TOP_MARGIN_DP)
        val maxY = max(minY, metrics.heightPixels - barHeight - dp(BOTTOM_MARGIN_DP))
        val y = if (remembered >= 0f) {
            (minY + (maxY - minY) * remembered).toInt()
        } else {
            clampY((anchorRawY - barHeight / 2f).toInt(), barHeight)
        }
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            OverlayWindowTypes.fullscreenLauncherWindowType(context),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = if (side == Side.LEFT) Gravity.LEFT or Gravity.TOP else Gravity.RIGHT or Gravity.TOP
            this.x = edgeMargin
            this.y = y
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            buttonBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
    }

    private fun dimmed(color: Int, fraction: Float): Int {
        val alpha = (((color ushr 24) and 0xFF) * fraction).toInt().coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (alpha shl 24)
    }

    private fun dp(value: Int): Int = max(1, (value * density).toInt())

    enum class Side { LEFT, RIGHT }

    /**
     * 控制条容器：既要感知"手指是否按在条上"（子控件也会吃到事件），又要支持整条拖动，
     * 所以覆写 `dispatchTouchEvent` 做交互回报、覆写拦截/触摸做拖动。
     */
    private class BarContainer(context: Context) : LinearLayout(context) {

        var onTouchStateChanged: ((Boolean) -> Unit)? = null
        var owner: PageScrollControlBar? = null

        private var downRawY = 0f
        private var startY = 0
        private var dragging = false
        private val slop = ViewConfiguration.get(context).scaledTouchSlop

        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) onTouchStateChanged?.invoke(true)
            val handled = super.dispatchTouchEvent(event)
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                onTouchStateChanged?.invoke(false)
                if (dragging) {
                    dragging = false
                    owner?.rememberPosition()
                }
            }
            return handled
        }

        override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawY = event.rawY
                    startY = (layoutParams as? WindowManager.LayoutParams)?.y ?: 0
                    dragging = false
                }

                MotionEvent.ACTION_MOVE -> {
                    if (abs(event.rawY - downRawY) > slop) {
                        dragging = true
                        return true
                    }
                }
            }
            return super.onInterceptTouchEvent(event)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                downRawY = event.rawY
                startY = (layoutParams as? WindowManager.LayoutParams)?.y ?: 0
                return true
            }
            if (event.actionMasked == MotionEvent.ACTION_MOVE && dragging) {
                val host = owner ?: return true
                val barHeight = if (height > 0) height else host.dp(ESTIMATED_BAR_HEIGHT_DP)
                host.moveTo(host.clampY(startY + (event.rawY - downRawY).toInt(), barHeight))
                return true
            }
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                dragging = false
                owner?.rememberPosition()
                return true
            }
            return super.onTouchEvent(event)
        }
    }

    private companion object {
        const val TAG = "PageScrollBar"

        const val BUTTON_DP = 44
        /** 方向 + 暂停(56) + 加 + 减 + 关闭 + 内边距 ≈ 257dp（无把手、无速度梯）。 */
        const val ESTIMATED_BAR_HEIGHT_DP = 257
        const val BADGE_TEXT_SP = 10f
        const val BAR_RADIUS_DP = 32
        const val BAR_RADIUS_MORPH_FROM_DP = 52
        const val EDGE_MARGIN_DP = 8
        const val TOP_MARGIN_DP = 48
        const val BOTTOM_MARGIN_DP = 48

        const val DIM_DELAY_MS = 4000L
        const val DIM_ANIM_MS = 220L
        const val DISABLED_ALPHA = 0.35f
        const val REPEAT_START_DELAY_MS = 420L
        const val REPEAT_MS = 160L

        const val NIGHT_SURFACE = 0xFF1D2024.toInt()
        const val DAY_SURFACE = 0xFFEFF1F4.toInt()
        const val NIGHT_ICON = 0xFFC3C7CF.toInt()
        const val DAY_ICON = 0xFF44474E.toInt()
        const val NIGHT_ACCENT = 0xFFA8C7FA.toInt()
        const val DAY_ACCENT = 0xFF0B57D0.toInt()
        const val NIGHT_ACTIVE_CONTAINER = 0xFF2B3A55.toInt()
        const val DAY_ACTIVE_CONTAINER = 0xFFD7E3FF.toInt()
        const val NIGHT_OUTLINE_VARIANT = 0xFF43474E.toInt()
        const val DAY_OUTLINE_VARIANT = 0xFFC4C6CF.toInt()
        const val ACCENT_FALLBACK = 0xFF8AB4FF.toInt()
    }
}
