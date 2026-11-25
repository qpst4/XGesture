package com.slideindex.app.screencontrol

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.slideindex.app.R
import com.slideindex.app.overlay.OverlayGlassForegroundDrawable
import com.slideindex.app.overlay.OverlayMaterialSpec
import com.slideindex.app.overlay.OverlayPanelTheme
import com.slideindex.app.overlay.setupBackgroundBlur
import kotlin.math.hypot
import kotlin.math.roundToInt

internal enum class ScreenControlDirection { UP, DOWN, LEFT, RIGHT }

internal enum class ScreenControlNav { BACK, RECENTS, HOME }

/**
 * 「屏幕控制」悬浮面板。
 *
 * 视觉：半透明磨砂底 + 细描边 + 浮动按键；触控板区域只做很淡的点阵和中心圆环，
 * 不再是一整块灰色矩形。
 *
 * 交互：三点拖动条 → 触控板 → 方向键（2×2）→ 导航键（1×4）→ 底部上滑关闭条。
 * 右下角圆弧负责缩放；「挂起」后整个窗口缩成可拖动的 72dp 圆球。
 */
internal class ScreenControlPanelView(
    context: Context,
    private val listener: Listener,
    private val blurEnabled: Boolean = false,
) : FrameLayout(context) {

    interface Listener {
        fun onPointerMove(dx: Float, dy: Float)
        fun onTap()
        fun onDoubleTap()
        fun onLongPressStart()
        fun onLongPressMove(dx: Float, dy: Float)
        fun onLongPressEnd()
        fun onDirection(direction: ScreenControlDirection, longPress: Boolean)
        fun onDirectionEnd()
        fun onNav(action: ScreenControlNav)
        fun onMinimize()
        fun onRestore()
        fun onClose()
        fun onMovePanel(dx: Float, dy: Float)
        fun onPanelMoveFinished()
        fun onResizeStart()
        fun onResize(dx: Float, dy: Float)
        fun onResizeEnd()
        fun onBallMove(dx: Float, dy: Float)
        fun onBallMoveFinished()
        fun onDismissDrag(dy: Float)
        fun onDismissRelease(dismiss: Boolean)
    }

    companion object {
        /** 挂起后缩成的圆球直径（dp）：窗口尺寸与球底模糊圆角都从这里取。 */
        const val BALL_SIZE_DP = 48f
    }

    private val density = resources.displayMetrics.density
    private val themeColors = OverlayPanelTheme.colors(context)

    private val content: LinearLayout
    private val header: FrameLayout
    private val pad: PadView
    private val resizeHandle: ResizeHandleView
    private val ball: BallView
    private var minimized = false

    init {
        background = if (blurEnabled) null else panelBackground()
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                if (minimized) {
                    outline.setOval(0, 0, view.width, view.height)
                } else {
                    outline.setRoundRect(0, 0, view.width, view.height, 18f * density)
                }
            }
        }
        // 不用 elevation 阴影：这是悬浮窗根视图，AOSP 上本来就画不出窗口阴影；
        // 但部分 ROM 会把它当窗口高度，给整个悬浮窗套一层很大的系统阴影（用户反馈“面板下方阴影很大”）。
        // 投影改由外层 [ScreenControlShadowHost] 用画布自绘，这里显式保持零海拔。
        elevation = 0f

        content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10f), dp(5f), dp(10f), dp(0f))
        }
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        header = FrameLayout(context).apply {
            setOnTouchListener(HeaderDragListener())
        }
        header.addView(
            DotsPill(context),
            LayoutParams(dp(140f), dp(14f)).apply { gravity = Gravity.CENTER },
        )
        content.addView(header, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(16f)))

        pad = PadView(context, object : PadListener {
            override fun onMove(dx: Float, dy: Float) = listener.onPointerMove(dx, dy)
            override fun onTap() = listener.onTap()
            override fun onDoubleTap() = listener.onDoubleTap()
            override fun onLongPressStart() = listener.onLongPressStart()
            override fun onLongPressMove(dx: Float, dy: Float) = listener.onLongPressMove(dx, dy)
            override fun onLongPressEnd() = listener.onLongPressEnd()
        }).apply {
            background = padBackground()
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, 12f * density)
                }
            }
        }
        content.addView(
            pad,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                topMargin = dp(6f)
                bottomMargin = dp(8f)
            },
        )

        content.addView(
            directionRow(
                ScreenControlDirection.UP,
                ScreenControlDirection.DOWN,
                ScreenControlDirection.LEFT,
                ScreenControlDirection.RIGHT,
            ),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(37f)),
        )
        content.addView(
            navRow(),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(37f)).apply {
                topMargin = dp(4f)
            },
        )
        content.addView(
            BottomBarView(
                context = context,
                onDragProgress = { dy -> listener.onDismissDrag(dy) },
                onDismiss = { listener.onDismissRelease(true) },
                onCancel = { listener.onDismissRelease(false) },
            ),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(18f)).apply {
                topMargin = dp(0f)
            },
        )

        resizeHandle = ResizeHandleView(context, object : ResizeListener {
            override fun onStart() = listener.onResizeStart()
            override fun onResize(dx: Float, dy: Float) = listener.onResize(dx, dy)
            override fun onEnd() = listener.onResizeEnd()
        })
        addView(
            resizeHandle,
            LayoutParams(dp(24f), dp(24f)).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                setMargins(0, 0, 0, 0)
            },
        )

        ball = BallView(
            context = context,
            listener = object : BallListener {
                override fun onMove(dx: Float, dy: Float) = listener.onBallMove(dx, dy)
                override fun onMoveFinished() = listener.onBallMoveFinished()
                override fun onRestore() = listener.onRestore()
            },
            blurEnabled = blurEnabled,
        ).apply {
            visibility = GONE
        }
        addView(ball, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        applyPanelBackground()
    }

    private fun applyPanelBackground() {
        val spec = OverlayMaterialSpec.fromContext(context)
        val isDark = OverlayPanelTheme.isDarkTheme(context)
        if (minimized) {
            if (!blurEnabled) {
                background = ballBackground()
                foreground = null
                return
            }
            background = null
            // 球直径固定 [BALL_SIZE_DP]，圆角取其一半。这里不能用 width / 2：
            // setMinimized() 调用本方法时窗口还没缩，width 仍是挂起前的面板宽度，
            // 交给 BackgroundBlurDrawable 的圆角会远大于球径，部分 ROM 会把模糊区域画歪。
            val rad = BALL_SIZE_DP / 2f * density
            setupBackgroundBlur(
                view = this,
                cornerRadiusPx = rad,
                blurRadiusPx = spec.blurRadiusPx(density),
                tintColor = spec.tintColor(isDark),
            )
            foreground = OverlayGlassForegroundDrawable(
                spec = spec,
                isDark = isDark,
                cornerRadiusPx = rad,
                density = density,
            )
            post {
                val bg = background
                if (bg == null || !bg.javaClass.name.contains("BackgroundBlurDrawable")) {
                    background = ballBackground()
                }
            }
            return
        }

        if (!blurEnabled) {
            background = panelBackground()
            foreground = null
            return
        }
        background = null
        val corner = spec.cornerRadiusPx(density)
        setupBackgroundBlur(
            view = this,
            cornerRadiusPx = corner,
            blurRadiusPx = spec.blurRadiusPx(density),
            tintColor = spec.tintColor(isDark),
        )
        foreground = OverlayGlassForegroundDrawable(
            spec = spec,
            isDark = isDark,
            cornerRadiusPx = corner,
            density = density,
        )
        post {
            val bg = background
            if (bg == null || !bg.javaClass.name.contains("BackgroundBlurDrawable")) {
                background = panelBackground()
            }
        }
    }

    fun setMinimized(minimized: Boolean) {
        if (this.minimized == minimized) return
        this.minimized = minimized
        if (minimized) {
            content.visibility = GONE
            resizeHandle.visibility = GONE
            ball.visibility = VISIBLE
            applyPanelBackground()
        } else {
            content.visibility = VISIBLE
            resizeHandle.visibility = VISIBLE
            ball.visibility = GONE
            applyPanelBackground()
        }
        invalidateOutline()
        requestLayout()
        invalidate()
    }

    private fun directionRow(vararg directions: ScreenControlDirection): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        directions.forEach { direction ->
            val iconType = when (direction) {
                ScreenControlDirection.UP -> HairlineIconType.UP
                ScreenControlDirection.DOWN -> HairlineIconType.DOWN
                ScreenControlDirection.LEFT -> HairlineIconType.LEFT
                ScreenControlDirection.RIGHT -> HairlineIconType.RIGHT
            }
            val key = HairlineKeyIconView(context, iconType).apply {
                background = rippleKey(selected = false)
                setOnTouchListener(
                    RepeatKeyTouchListener(
                        onShort = { listener.onDirection(direction, false) },
                        onLongStart = { listener.onDirection(direction, true) },
                        onLongEnd = { listener.onDirectionEnd() },
                    ),
                )
            }
            val slot = FrameLayout(context).apply {
                addView(
                    key,
                    FrameLayout.LayoutParams(dp(37f), dp(37f)).apply {
                        gravity = Gravity.CENTER
                    },
                )
            }
            row.addView(
                slot,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f),
            )
        }
        return row
    }

    private fun navRow(): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val items = listOf(
            HairlineIconType.BACK to ScreenControlNav.BACK,
            HairlineIconType.RECENTS to ScreenControlNav.RECENTS,
            HairlineIconType.HOME to ScreenControlNav.HOME,
        )
        items.forEach { (iconType, nav) ->
            val key = HairlineKeyIconView(context, iconType).apply {
                background = rippleKey(selected = false)
                setOnClickListener {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    listener.onNav(nav)
                }
            }
            val slot = FrameLayout(context).apply {
                addView(
                    key,
                    FrameLayout.LayoutParams(dp(37f), dp(37f)).apply {
                        gravity = Gravity.CENTER
                    },
                )
            }
            row.addView(
                slot,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f),
            )
        }
        val minimize = HairlineKeyIconView(context, HairlineIconType.MINIMIZE).apply {
            background = rippleKey(selected = false, accent = false)
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                listener.onMinimize()
            }
        }
        val minimizeSlot = FrameLayout(context).apply {
            addView(
                minimize,
                FrameLayout.LayoutParams(dp(37f), dp(37f)).apply {
                    gravity = Gravity.CENTER
                },
            )
        }
        row.addView(
            minimizeSlot,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f),
        )
        return row
    }

    private val panelBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.85f * density
        color = Color.argb(135, 255, 255, 255)
    }
    // 外层极微深色轮廓，在纯白背景下形成立体边界，防止面板边缘被吞噬
    private val panelOuterShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        color = Color.argb(32, 0, 0, 0)
    }
    private val panelBorderRect = RectF()

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (!minimized && width > 0 && height > 0) {
            // 1. 最外层微暗灰轮廓（0.5dp 偏移），保证纯白底色下轮廓清晰立体
            val outerInset = panelOuterShadowPaint.strokeWidth / 2f
            panelBorderRect.set(outerInset, outerInset, width - outerInset, height - outerInset)
            canvas.drawRoundRect(panelBorderRect, 18f * density, 18f * density, panelOuterShadowPaint)

            // 2. 内沿高光白反光边
            val inset = panelBorderPaint.strokeWidth / 2f + 0.5f * density
            panelBorderRect.set(inset, inset, width - inset, height - inset)
            canvas.drawRoundRect(panelBorderRect, 17.5f * density, 17.5f * density, panelBorderPaint)
        }
    }

    private fun panelBackground(): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 18f * density
        setColor(Color.argb(if (blurEnabled) 165 else 248, 245, 247, 252))
        setStroke((1f * density).roundToInt(), Color.argb(35, 0, 0, 0))
    }

    private fun ballBackground(): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(Color.argb(if (blurEnabled) 130 else 220, 235, 240, 248))
        setStroke((0.9f * density).roundToInt(), Color.argb(110, 255, 255, 255))
    }

    private fun padBackground(): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 12f * density
        // 纯净高亮浅白，在毛玻璃上稍微带一点点微阶梯对比度
        setColor(Color.argb(if (blurEnabled) 60 else 185, 255, 255, 255))
        // 关键：0.85dp 双层对比感细腻边框——用极轻的半透明灰边（14% 暗灰），在纯白背景下立刻把触控区勾勒出来
        setStroke((0.85f * density).roundToInt(), Color.argb(38, 0, 0, 0))
    }

    private fun rippleKey(selected: Boolean = false, accent: Boolean = false): RippleDrawable {
        val content = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 8f * density
            setColor(
                when {
                    accent -> themeColors.accent
                    selected -> withAlpha(themeColors.accentSoft, 220)
                    blurEnabled -> Color.argb(70, 255, 255, 255)
                    else -> withAlpha(themeColors.cardBackground, 180)
                }
            )
            setStroke(
                (0.85f * density).roundToInt(),
                when {
                    accent || selected -> themeColors.accent
                    // 微暗灰轮廓+白光混合（22% 暗灰），让按键在白底上边缘清晰可辨，不再费眼
                    else -> Color.argb(32, 0, 0, 0)
                }
            )
        }
        return RippleDrawable(
            ColorStateList.valueOf(withAlpha(themeColors.accent, 48)),
            content,
            null,
        )
    }

    private enum class HairlineIconType {
        UP, DOWN, LEFT, RIGHT, BACK, RECENTS, HOME, MINIMIZE
    }

    private class HairlineKeyIconView(
        context: Context,
        private val type: HairlineIconType,
    ) : View(context) {
        private val density = resources.displayMetrics.density
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 1.35f * density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val iconPath = android.graphics.Path()
        private val arcRect = RectF()

        init {
            setWillNotDraw(false)
            isClickable = true
            isFocusable = true
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            val h = measuredHeight
            val w = measuredWidth
            // 取高和宽的合理值，若行高已确定（比如34dp），按键维持 1:1 正方形
            val size = if (h > 0) h else w
            setMeasuredDimension(size, size)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            iconPath.reset()

            when (type) {
                HairlineIconType.UP -> {
                    iconPath.moveTo(cx - 5.5f * density, cy + 2.2f * density)
                    iconPath.lineTo(cx, cy - 3.2f * density)
                    iconPath.lineTo(cx + 5.5f * density, cy + 2.2f * density)
                    canvas.drawPath(iconPath, strokePaint)
                }
                HairlineIconType.DOWN -> {
                    iconPath.moveTo(cx - 5.5f * density, cy - 2.2f * density)
                    iconPath.lineTo(cx, cy + 3.2f * density)
                    iconPath.lineTo(cx + 5.5f * density, cy - 2.2f * density)
                    canvas.drawPath(iconPath, strokePaint)
                }
                HairlineIconType.LEFT -> {
                    iconPath.moveTo(cx + 2.2f * density, cy - 5.5f * density)
                    iconPath.lineTo(cx - 3.2f * density, cy)
                    iconPath.lineTo(cx + 2.2f * density, cy + 5.5f * density)
                    canvas.drawPath(iconPath, strokePaint)
                }
                HairlineIconType.RIGHT -> {
                    iconPath.moveTo(cx - 2.2f * density, cy - 5.5f * density)
                    iconPath.lineTo(cx + 3.2f * density, cy)
                    iconPath.lineTo(cx - 2.2f * density, cy + 5.5f * density)
                    canvas.drawPath(iconPath, strokePaint)
                }
                HairlineIconType.BACK -> {
                    // 1. 箭头尖端
                    iconPath.moveTo(cx - 2.5f * density, cy - 4.5f * density)
                    iconPath.lineTo(cx - 6.2f * density, cy - 1f * density)
                    iconPath.lineTo(cx - 2.5f * density, cy + 2.5f * density)

                    // 2. 水平向右线段
                    iconPath.moveTo(cx - 6.2f * density, cy - 1f * density)
                    iconPath.lineTo(cx + 1.5f * density, cy - 1f * density)

                    // 3. 右下圆角弯钩（无缝平滑，绝无交叉）
                    val corner = 4.2f * density
                    arcRect.set(
                        cx + 1.5f * density - corner,
                        cy - 1f * density,
                        cx + 1.5f * density + corner,
                        cy - 1f * density + 2f * corner,
                    )
                    iconPath.arcTo(arcRect, 270f, 90f, false)
                    iconPath.lineTo(cx + 1.5f * density + corner, cy + 4.8f * density)
                    canvas.drawPath(iconPath, strokePaint)
                }
                HairlineIconType.RECENTS -> {
                    // ViewCarousel：标准任务切换器（纯单线全镂空，中央主卡片 + 左右对称侧卡片，无任何白块）
                    val centerW = 6.8f * density
                    val centerH = 10.5f * density
                    val centerR = 1.6f * density

                    // 1. 中央主任务卡片（纯单线圆角矩形）
                    arcRect.set(
                        cx - centerW / 2f,
                        cy - centerH / 2f,
                        cx + centerW / 2f,
                        cy + centerH / 2f,
                    )
                    canvas.drawRoundRect(arcRect, centerR, centerR, strokePaint)

                    // 2. 左右两侧对称的侧翼任务卡片（高度略收敛，上下右/左各一折线，全镂空）
                    val sideGap = 2.0f * density
                    val sideW = 2.4f * density
                    val sideTop = cy - 3.8f * density
                    val sideBottom = cy + 3.8f * density

                    // 左侧翼
                    val leftEdge = cx - centerW / 2f - sideGap
                    iconPath.moveTo(leftEdge, sideTop)
                    iconPath.lineTo(leftEdge - sideW, sideTop)
                    iconPath.lineTo(leftEdge - sideW, sideBottom)
                    iconPath.lineTo(leftEdge, sideBottom)
                    canvas.drawPath(iconPath, strokePaint)

                    // 右侧翼
                    iconPath.reset()
                    val rightEdge = cx + centerW / 2f + sideGap
                    iconPath.moveTo(rightEdge, sideTop)
                    iconPath.lineTo(rightEdge + sideW, sideTop)
                    iconPath.lineTo(rightEdge + sideW, sideBottom)
                    iconPath.lineTo(rightEdge, sideBottom)
                    canvas.drawPath(iconPath, strokePaint)
                }
                HairlineIconType.HOME -> {
                    iconPath.moveTo(cx - 6.2f * density, cy - 1f * density)
                    iconPath.lineTo(cx, cy - 6f * density)
                    iconPath.lineTo(cx + 6.2f * density, cy - 1f * density)
                    canvas.drawPath(iconPath, strokePaint)

                    iconPath.reset()
                    iconPath.moveTo(cx - 4.5f * density, cy - 1f * density)
                    iconPath.lineTo(cx - 4.5f * density, cy + 5.5f * density)
                    iconPath.lineTo(cx + 4.5f * density, cy + 5.5f * density)
                    iconPath.lineTo(cx + 4.5f * density, cy - 1f * density)
                    canvas.drawPath(iconPath, strokePaint)
                }
                HairlineIconType.MINIMIZE -> {
                    val techBlue = Color.parseColor("#0A84FF")
                    strokePaint.color = techBlue
                    fillPaint.color = techBlue
                    canvas.drawCircle(cx, cy, 6f * density, strokePaint)
                    canvas.drawCircle(cx, cy, 2.2f * density, fillPaint)
                }
            }
        }
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    private fun dp(value: Float): Int = (value * density).roundToInt()

    private inner class HeaderDragListener : OnTouchListener {
        private var lastRawX = 0f
        private var lastRawY = 0f
        private var dragging = false

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                    dragging = true
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (!dragging) return true
                    val dx = event.rawX - lastRawX
                    val dy = event.rawY - lastRawY
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                    if (dx != 0f || dy != 0f) listener.onMovePanel(dx, dy)
                    return true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) listener.onPanelMoveFinished()
                    dragging = false
                    return true
                }
            }
            return false
        }
    }

    private class RepeatKeyTouchListener(
        private val onShort: () -> Unit,
        private val onLongStart: () -> Unit,
        private val onLongEnd: () -> Unit,
    ) : OnTouchListener {
        private val handler = Handler(Looper.getMainLooper())
        private var longFired = false

        private val longRunnable = Runnable {
            longFired = true
            onLongStart()
        }

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    longFired = false
                    view.isPressed = true
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    handler.postDelayed(longRunnable, 360L)
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longRunnable)
                    view.isPressed = false
                    if (longFired) onLongEnd() else onShort()
                    return true
                }

                MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longRunnable)
                    view.isPressed = false
                    if (longFired) onLongEnd()
                    return true
                }
            }
            return false
        }
    }

    private interface PadListener {
        fun onMove(dx: Float, dy: Float)
        fun onTap()
        fun onDoubleTap()
        fun onLongPressStart()
        fun onLongPressMove(dx: Float, dy: Float)
        fun onLongPressEnd()
    }

    private class PadView(
        context: Context,
        private val listener: PadListener,
    ) : View(context) {

        private val density = resources.displayMetrics.density
        private val handler = Handler(Looper.getMainLooper())
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

        private var downX = 0f
        private var downY = 0f
        private var lastX = 0f
        private var lastY = 0f
        private var moved = false
        private var longPressFired = false
        private var lastTapAt = 0L

        private val longPressRunnable = Runnable {
            longPressFired = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            listener.onLongPressStart()
        }

        init {
            setWillNotDraw(false)
            isClickable = true
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    lastX = event.x
                    lastY = event.y
                    moved = false
                    longPressFired = false
                    handler.postDelayed(longPressRunnable, 380L)
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    lastX = event.x
                    lastY = event.y
                    if (!moved && hypot(event.x - downX, event.y - downY) > touchSlop) {
                        moved = true
                        if (!longPressFired) handler.removeCallbacks(longPressRunnable)
                    }
                    if (longPressFired) {
                        listener.onLongPressMove(dx, dy)
                    } else {
                        listener.onMove(dx, dy)
                    }
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPressRunnable)
                    if (longPressFired) {
                        listener.onLongPressEnd()
                    } else if (!moved) {
                        val now = SystemClock.uptimeMillis()
                        if (now - lastTapAt <= 300L) {
                            lastTapAt = 0L
                            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            listener.onDoubleTap()
                        } else {
                            lastTapAt = now
                            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            listener.onTap()
                        }
                    }
                    return true
                }

                MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPressRunnable)
                    if (longPressFired) listener.onLongPressEnd()
                    return true
                }
            }
            return false
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            // 一墨的触控区是纯白/同色，不画点阵、圆环或提示文字。
        }
    }

    private class BottomBarView(
        context: Context,
        private val onDragProgress: (Float) -> Unit,
        private val onDismiss: () -> Unit,
        private val onCancel: () -> Unit,
    ) : View(context) {

        private var startRawY = 0f
        private var armed = false
        private val density = resources.displayMetrics.density
        private val themeColors = OverlayPanelTheme.colors(context)
        private val threshold = 42f * density

        init {
            setWillNotDraw(false)
            isClickable = true
            contentDescription = "Close"
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startRawY = event.rawY
                    armed = false
                    isPressed = true
                    onDragProgress(0f)
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dy = (startRawY - event.rawY).coerceAtLeast(0f)
                    val nowArmed = dy >= threshold
                    if (nowArmed && !armed) {
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                    armed = nowArmed
                    onDragProgress(dy)
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    isPressed = false
                    val dy = startRawY - event.rawY
                    if (dy >= threshold) onDismiss() else onCancel()
                    armed = false
                    return true
                }

                MotionEvent.ACTION_CANCEL -> {
                    isPressed = false
                    armed = false
                    onCancel()
                    return true
                }
            }
            return false
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = withAlpha(themeColors.textPrimary, if (armed) 220 else 165)
                style = Paint.Style.FILL
            }
            val barWidth = (64f * density).coerceAtMost(width * 0.7f)
            val barHeight = 3.5f * density
            val left = (width - barWidth) / 2f
            val top = (height - barHeight - 6f * density).coerceAtLeast(0f)
            canvas.drawRoundRect(
                RectF(left, top, left + barWidth, top + barHeight),
                barHeight / 2f,
                barHeight / 2f,
                paint,
            )
        }

        private fun withAlpha(color: Int, alpha: Int): Int =
            (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)
    }

    private interface ResizeListener {
        fun onStart()
        fun onResize(dx: Float, dy: Float)
        fun onEnd()
    }

    private class ResizeHandleView(
        context: Context,
        private val listener: ResizeListener,
    ) : View(context) {

        private var startRawX = 0f
        private var startRawY = 0f
        private var resizing = false

        init {
            setWillNotDraw(false)
            isClickable = true
            contentDescription = "Resize"
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startRawX = event.rawX
                    startRawY = event.rawY
                    resizing = true
                    isPressed = true
                    listener.onStart()
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (!resizing) return true
                    listener.onResize(event.rawX - startRawX, event.rawY - startRawY)
                    return true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isPressed = false
                    if (resizing) listener.onEnd()
                    resizing = false
                    return true
                }
            }
            return false
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val density = resources.displayMetrics.density
            val themeColors = OverlayPanelTheme.colors(context)
            val lineWidth = 2.2f * density
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = withAlpha(themeColors.accent, 220)
                style = Paint.Style.STROKE
                strokeWidth = lineWidth
                strokeCap = Paint.Cap.ROUND
            }
            val cornerR = 18f * density
            val cx = width - cornerR
            val cy = height - cornerR
            val r = cornerR - lineWidth / 2f - 0.85f * density
            canvas.drawArc(
                RectF(cx - r, cy - r, cx + r, cy + r),
                0f,
                90f,
                false,
                paint,
            )
        }

        private fun withAlpha(color: Int, alpha: Int): Int =
            (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)
    }

    private class DotsPill(context: Context) : View(context) {
        private val density = resources.displayMetrics.density

        init {
            setWillNotDraw(false)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(225, 28, 30, 36)
                style = Paint.Style.FILL
            }
            val radius = 1.8f * density
            val gap = 5.5f * density
            val cx = width / 2f
            val cy = height / 2f
            canvas.drawCircle(cx - gap, cy, radius, dot)
            canvas.drawCircle(cx, cy, radius, dot)
            canvas.drawCircle(cx + gap, cy, radius, dot)
        }
    }

    private interface BallListener {
        fun onMove(dx: Float, dy: Float)
        fun onMoveFinished()
        fun onRestore()
    }

    private class BallView(
        context: Context,
        private val listener: BallListener,
        private val blurEnabled: Boolean = false,
    ) : View(context) {

        private val density = resources.displayMetrics.density
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        private val techBlue = Color.parseColor("#0A84FF")

        private var downRawX = 0f
        private var downRawY = 0f
        private var lastRawX = 0f
        private var lastRawY = 0f
        private var dragging = false

        init {
            setWillNotDraw(false)
            isClickable = true
            contentDescription = "Restore touchpad"
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
            // 缩小球同样不要 elevation：球体背景是半透明的，阴影会透进球体内部；
            // 个别 ROM/GPU 的阴影内边界还会按粗多边形绘制，球心会出现白色八边形色块。
            // 球的投影由外层 [ScreenControlShadowHost] 的椭圆自绘负责。
            elevation = 0f
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                    dragging = false
                    isPressed = true
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val totalX = event.rawX - downRawX
                    val totalY = event.rawY - downRawY
                    if (!dragging && hypot(totalX, totalY) > touchSlop) {
                        dragging = true
                    }
                    if (dragging) {
                        listener.onMove(event.rawX - lastRawX, event.rawY - lastRawY)
                        lastRawX = event.rawX
                        lastRawY = event.rawY
                    }
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    isPressed = false
                    if (dragging) {
                        listener.onMoveFinished()
                    } else {
                        performClick()
                        listener.onRestore()
                    }
                    dragging = false
                    return true
                }

                MotionEvent.ACTION_CANCEL -> {
                    isPressed = false
                    if (dragging) listener.onMoveFinished()
                    dragging = false
                    return true
                }
            }
            return false
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val radius = minOf(width, height) / 2f - 1f * density

            // 外圈柔白高光微描边
            val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(85, 255, 255, 255)
                style = Paint.Style.STROKE
                strokeWidth = 0.9f * density
            }
            canvas.drawCircle(cx, cy, radius, border)

            // 科技蓝同心圆图标（外细环 + 居中实心小点）
            val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = techBlue
                style = Paint.Style.STROKE
                strokeWidth = 1.5f * density
            }
            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = techBlue
                style = Paint.Style.FILL
            }
            canvas.drawCircle(cx, cy, 7.5f * density, ringPaint)
            canvas.drawCircle(cx, cy, 2.5f * density, dotPaint)
        }

        private fun withAlpha(color: Int, alpha: Int): Int =
            (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)
    }
}
