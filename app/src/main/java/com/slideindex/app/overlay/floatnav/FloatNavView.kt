package com.slideindex.app.overlay.floatnav

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.LinearLayout
import com.slideindex.app.R
import com.slideindex.app.overlay.OverlayBlurGate
import com.slideindex.app.overlay.OverlayGlassForegroundDrawable
import com.slideindex.app.overlay.OverlayMaterialSpec
import com.slideindex.app.overlay.OverlayPanelTheme
import com.slideindex.app.overlay.setupBackgroundBlur
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.AppThemeMode
import com.slideindex.app.util.HapticHelper
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 浮动导航条视图。
 *
 * 视觉上是一块和项目其他悬浮面板同材质的胶囊：系统局部模糊 + 全局不透明度 + 圆角 +
 * 高光/描边；模糊不可用时退化为实色底 + 细描边。支持横向/竖向两种排列：
 * 把柄在展开方向的起点，其余按键朝屏幕中心排列；右侧/下侧方向整体镜像。
 */
@SuppressLint("ViewConstructor", "RtlHardcoded")
internal class FloatNavView(
    context: Context,
    private var appSettings: AppSettings,
    form: FloatNavForm,
    expandForward: Boolean,
    private val listener: Listener,
) : LinearLayout(context) {

    interface Listener {
        fun onKey(key: FloatNavKey)
        fun onClose()
        fun onGripClick()
        fun onGripLongPress()
        fun onDragStart()
        fun onDragBy(dx: Float, dy: Float)
        fun onDragEnd()
    }

    private val density = resources.displayMetrics.density
    private var themeColors = OverlayPanelTheme.colors(resolveIsDark())
    private val keyViews = LinkedHashMap<FloatNavKey, FloatNavKeyView>()
    private var cornerRadiusPx = 0f
    private var hovered = FloatNavKey.NONE
    private var collapsed = false
    private var animating = false
    private var reordering = false
    private var currentForm = form
    private var expandForwardDirection = expandForward
    private val reorderRunnable = Runnable { applyChildOrder() }
    private val longPressHandler = Handler(Looper.getMainLooper())

    val isCollapsed: Boolean get() = collapsed
    val form: FloatNavForm get() = currentForm

    init {
        orientation = if (currentForm == FloatNavForm.HORIZONTAL) HORIZONTAL else VERTICAL
        // 这是物理侧边导航条，阿拉伯语等 RTL 语言下也不能把左右顺序翻过来。
        layoutDirection = LAYOUT_DIRECTION_LTR
        applyGravity()
        setPadding(dp(6), dp(6), dp(6), dp(6))
        minimumWidth = dp(56)
        minimumHeight = dp(56)
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val radius = cornerRadiusPx.coerceAtLeast(0f)
                outline.setRoundRect(0, 0, view.width, view.height, radius)
            }
        }

        // 展开方向朝屏幕中心：轴起点是 GRIP，其余按键沿水平/垂直方向排列。
        orderKeys().forEach { key ->
            val keyView = FloatNavKeyView(context, key, themeColors).apply {
                contentDescription = descriptionFor(key)
                if (key == FloatNavKey.GRIP) {
                    isClickable = true
                    isFocusable = true
                    setOnTouchListener(GripTouchListener())
                    setOnClickListener { listener.onGripClick() }
                } else {
                    setOnClickListener {
                        HapticHelper.confirmLaunch(this, appSettings)
                        if (key == FloatNavKey.CLOSE) listener.onClose() else listener.onKey(key)
                    }
                }
            }
            addView(keyView, layoutParamsFor(key))
            keyViews[key] = keyView
        }
    }

    /** 重新套用“悬浮面板材质”，在 attach / 尺寸变化时调用。 */
    fun applyMaterial() {
        if (width <= 0 || height <= 0) return
        val spec = OverlayMaterialSpec.fromContext(context)
        val isDark = resolveIsDark()
        val corner = min(spec.cornerRadiusPx(density), min(width, height) / 2f).coerceAtLeast(0f)
        cornerRadiusPx = corner

        val wm = context.getSystemService(WindowManager::class.java)
        val blur = spec.blurEnabled && OverlayBlurGate.canBlur(wm, true)
        if (blur) {
            background = null
            foreground = null
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
            // `setupBackgroundBlur` 在窗口刚挂上 / 软件渲染 / 系统不支持时会拿不到 drawable，
            // 与 ScreenControlPanelView 同一口径：退化为实色，不能留一块半透明 tint。
            post {
                if (background?.javaClass?.name?.contains("BackgroundBlurDrawable") != true) {
                    background = solidBackground(spec, isDark, corner)
                    foreground = null
                }
            }
        } else {
            background = solidBackground(spec, isDark, corner)
            foreground = null
        }
        invalidateOutline()
        invalidate()
    }

    fun keyAt(rawX: Float, rawY: Float): FloatNavKey {
        if (keyViews.isEmpty()) return FloatNavKey.NONE
        val location = IntArray(2)
        getLocationOnScreen(location)
        val localX = rawX - location[0]
        val localY = rawY - location[1]
        val bounds = keyViews.mapNotNull { (key, view) ->
            if (view.visibility != VISIBLE) return@mapNotNull null
            FloatNavKeyBounds(
                key = key,
                left = view.left.toFloat(),
                top = view.top.toFloat(),
                right = view.right.toFloat(),
                bottom = view.bottom.toFloat(),
            )
        }
        return FloatNavGeometry.hitTest(bounds, localX, localY, slopPx = dp(4).toFloat())
    }

    fun setHoveredKey(key: FloatNavKey) {
        if (key == hovered) return
        val previous = hovered
        hovered = key
        keyViews.forEach { (k, view) ->
            view.setHighlighted(k == key && k != FloatNavKey.GRIP)
        }
        if (key != FloatNavKey.NONE && key != FloatNavKey.GRIP && previous != key) {
            HapticHelper.appTick(this, appSettings)
        }
    }

    fun clearHighlight() {
        setHoveredKey(FloatNavKey.NONE)
    }

    /** 宽度动画期间不要每帧重挂模糊背景，结束时统一 apply 一次。 */
    fun setAnimating(value: Boolean) {
        animating = value
    }

    fun collapsedWidthPx(): Int = dp(56)

    fun collapsedHeightPx(): Int = dp(56)

    /** 在极少数需要预测量“展开后尺寸”的场景使用（如收起态长按切形态）。 */
    fun measureExpandedSizePx(): Pair<Int, Int> {
        val probe = FloatNavView(context, appSettings, currentForm, expandForwardDirection, listener)
        val spec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        probe.measure(spec, spec)
        return probe.measuredWidth.coerceAtLeast(1) to probe.measuredHeight.coerceAtLeast(1)
    }

    /** 设置回流时实时刷新主题，不能继续用构造时的浅色 colors 快照。 */
    fun updateSettings(settings: AppSettings) {
        appSettings = settings
        applyTheme()
    }

    fun applyTheme() {
        themeColors = OverlayPanelTheme.colors(resolveIsDark())
        keyViews.values.forEach { it.setColors(themeColors) }
        applyMaterial()
    }

    private fun resolveIsDark(): Boolean {
        val systemDark =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        return AppThemeMode.fromId(appSettings.themeModeId).resolveIsDark(systemDark)
    }

    fun toggleCollapsed() {
        setCollapsed(!collapsed)
    }

    /** 动态换展开方向：不重建 View，只把现有子 View 按新顺序挂回去。 */
    fun setExpandForward(value: Boolean) {
        if (expandForwardDirection == value) return
        expandForwardDirection = value
        applyGravity()
        clearHighlight()
        // 这个方法可能在把柄自己的 onTouch 里被调用；同步 removeAllViews 会重入
        // cancelTouchTarget，先 post 到下一帧再重排。
        if (isAttachedToWindow) {
            removeCallbacks(reorderRunnable)
            post(reorderRunnable)
        } else {
            applyChildOrder()
        }
    }

    /** 切换横/竖形态；收起态时窗口尺寸仍是 56×56，只更新内部布局。 */
    fun setForm(form: FloatNavForm, expandForward: Boolean) {
        val formChanged = currentForm != form
        currentForm = form
        if (formChanged) {
            orientation = if (form == FloatNavForm.HORIZONTAL) HORIZONTAL else VERTICAL
            keyViews.forEach { (key, view) ->
                view.layoutParams = layoutParamsFor(key)
            }
        }
        setExpandForward(expandForward)
        applyGravity()
        requestLayout()
        invalidate()
    }

    private fun applyGravity() {
        gravity = if (currentForm == FloatNavForm.HORIZONTAL) {
            Gravity.CENTER_VERTICAL or if (expandForwardDirection) Gravity.LEFT else Gravity.RIGHT
        } else {
            Gravity.CENTER_HORIZONTAL or if (expandForwardDirection) Gravity.TOP else Gravity.BOTTOM
        }
    }

    private fun applyChildOrder() {
        if (reordering || keyViews.isEmpty()) return
        reordering = true
        try {
            val params = keyViews.mapValues { it.value.layoutParams }
            removeAllViews()
            orderKeys().forEach { key ->
                val view = keyViews[key] ?: return@forEach
                addView(view, params[key] ?: generateDefaultLayoutParams())
            }
            requestLayout()
            invalidate()
        } finally {
            reordering = false
        }
    }

    private fun orderKeys(): List<FloatNavKey> = if (expandForwardDirection) {
        listOf(
            FloatNavKey.GRIP,
            FloatNavKey.BACK,
            FloatNavKey.HOME,
            FloatNavKey.RECENTS,
            FloatNavKey.CLOSE,
        )
    } else {
        listOf(
            FloatNavKey.CLOSE,
            FloatNavKey.RECENTS,
            FloatNavKey.HOME,
            FloatNavKey.BACK,
            FloatNavKey.GRIP,
        )
    }

    /**
     * 收起时只留把柄，并把它变成 56×56dp 的圆角方块；展开时恢复完整按钮条。
     */
    fun setCollapsed(value: Boolean) {
        if (collapsed == value) return
        collapsed = value
        clearHighlight()
        keyViews.forEach { (key, view) ->
            val params = view.layoutParams as? LayoutParams ?: return@forEach
            if (key == FloatNavKey.GRIP) {
                applyKeySize(key, params)
                view.layoutParams = params
                view.contentDescription = context.getString(
                    if (value) R.string.float_nav_expand else R.string.float_nav_drag,
                )
            } else {
                view.visibility = if (value) GONE else VISIBLE
            }
        }
        requestLayout()
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post { applyMaterial() }
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(reorderRunnable)
        longPressHandler.removeCallbacksAndMessages(null)
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (!animating) applyMaterial()
    }

    private fun solidBackground(
        spec: OverlayMaterialSpec,
        isDark: Boolean,
        corner: Float,
    ): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = corner
        setColor(spec.solidFallbackColor(isDark))
        val border = spec.innerBorderColor(isDark)
        if (Color.alpha(border) > 0) {
            setStroke(
                maxOf(1, (0.75f * density).roundToInt()),
                border,
            )
        }
    }

    private fun layoutParamsFor(key: FloatNavKey): LayoutParams =
        if (currentForm == FloatNavForm.HORIZONTAL) {
            LayoutParams(widthFor(key), dp(44)).apply {
                gravity = Gravity.CENTER_VERTICAL
                marginStart = if (collapsed && key == FloatNavKey.GRIP) 0 else dp(2)
                marginEnd = if (collapsed && key == FloatNavKey.GRIP) 0 else dp(2)
            }
        } else {
            LayoutParams(dp(44), heightFor(key)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = if (collapsed && key == FloatNavKey.GRIP) 0 else dp(2)
                bottomMargin = if (collapsed && key == FloatNavKey.GRIP) 0 else dp(2)
            }
        }

    private fun applyKeySize(key: FloatNavKey, params: LayoutParams) {
        if (currentForm == FloatNavForm.HORIZONTAL) {
            params.width = widthFor(key)
            params.height = dp(44)
            params.marginStart = if (collapsed && key == FloatNavKey.GRIP) 0 else dp(2)
            params.marginEnd = if (collapsed && key == FloatNavKey.GRIP) 0 else dp(2)
        } else {
            params.width = dp(44)
            params.height = heightFor(key)
            params.topMargin = if (collapsed && key == FloatNavKey.GRIP) 0 else dp(2)
            params.bottomMargin = if (collapsed && key == FloatNavKey.GRIP) 0 else dp(2)
        }
    }

    private fun widthFor(key: FloatNavKey): Int = when (key) {
        FloatNavKey.GRIP -> if (collapsed) dp(44) else dp(32)
        FloatNavKey.CLOSE -> dp(40)
        else -> dp(48)
    }

    private fun heightFor(key: FloatNavKey): Int = when (key) {
        FloatNavKey.GRIP -> if (collapsed) dp(44) else dp(32)
        FloatNavKey.CLOSE -> dp(40)
        else -> dp(48)
    }

    private fun descriptionFor(key: FloatNavKey): String = when (key) {
        FloatNavKey.GRIP -> context.getString(R.string.float_nav_drag)
        FloatNavKey.BACK -> context.getString(R.string.gesture_action_back)
        FloatNavKey.HOME -> context.getString(R.string.gesture_action_home)
        FloatNavKey.RECENTS -> context.getString(R.string.gesture_action_recents)
        FloatNavKey.CLOSE -> context.getString(R.string.float_nav_close)
        FloatNavKey.NONE -> ""
    }

    private fun dp(value: Int): Int = (value * density).roundToInt().coerceAtLeast(0)

    private inner class GripTouchListener : OnTouchListener {
        private val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
        private var downRawX = 0f
        private var downRawY = 0f
        private var lastRawX = 0f
        private var lastRawY = 0f
        private var moved = false
        private var longPressed = false
        private val longPressRunnable = Runnable {
            if (!moved && !longPressed) {
                longPressed = true
                listener.onGripLongPress()
            }
        }

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                    moved = false
                    longPressed = false
                    longPressHandler.removeCallbacks(longPressRunnable)
                    longPressHandler.postDelayed(
                        longPressRunnable,
                        ViewConfiguration.getLongPressTimeout().toLong(),
                    )
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (longPressed) return true
                    if (!moved) {
                        val totalDx = event.rawX - downRawX
                        val totalDy = event.rawY - downRawY
                        if (hypot(totalDx, totalDy) >= slop) {
                            longPressHandler.removeCallbacks(longPressRunnable)
                            moved = true
                            listener.onDragStart()
                            listener.onDragBy(totalDx, totalDy)
                            lastRawX = event.rawX
                            lastRawY = event.rawY
                        }
                    } else {
                        listener.onDragBy(event.rawX - lastRawX, event.rawY - lastRawY)
                        lastRawX = event.rawX
                        lastRawY = event.rawY
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    longPressHandler.removeCallbacks(longPressRunnable)
                    if (!longPressed) {
                        if (moved) {
                            listener.onDragEnd()
                        } else {
                            // 没有超过 slop：按点击处理，展开 / 收起浮条。
                            v.performClick()
                        }
                    }
                    moved = false
                    longPressed = false
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    longPressHandler.removeCallbacks(longPressRunnable)
                    if (moved && !reordering && !longPressed) listener.onDragEnd()
                    moved = false
                    longPressed = false
                    return true
                }
            }
            return false
        }
    }
}

/**
 * 单个按键。图标用细线自绘，避免依赖字体/emoji；高亮用主题 accentSoft 底。
 */
@SuppressLint("ViewConstructor")
private class FloatNavKeyView(
    context: Context,
    private val key: FloatNavKey,
    private var colors: OverlayPanelTheme.Colors,
) : View(context) {

    private val density = resources.displayMetrics.density
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val path = Path()
    private val highlightRect = RectF()
    private val gripColumns = intArrayOf(-1, 1)
    private val gripRows = intArrayOf(-1, 0, 1)

    var highlighted = false
        private set

    init {
        isClickable = key != FloatNavKey.GRIP
        isFocusable = key != FloatNavKey.GRIP
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun setHighlighted(value: Boolean) {
        if (highlighted == value) return
        highlighted = value
        invalidate()
    }

    fun setColors(value: OverlayPanelTheme.Colors) {
        if (colors == value) return
        colors = value
        invalidate()
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = 12f * density

        if (highlighted) {
            fillPaint.color = colors.accentSoft
            highlightRect.set(2f * density, 2f * density, width - 2f * density, height - 2f * density)
            canvas.drawRoundRect(highlightRect, radius, radius, fillPaint)
        }
        if (isPressed && !highlighted) {
            fillPaint.color = colors.rowHighlight
            highlightRect.set(2f * density, 2f * density, width - 2f * density, height - 2f * density)
            canvas.drawRoundRect(highlightRect, radius, radius, fillPaint)
        }

        val iconColor = when (key) {
            FloatNavKey.CLOSE -> colors.textSecondary
            FloatNavKey.GRIP -> colors.grip
            else -> if (highlighted) colors.accent else colors.textPrimary
        }
        strokePaint.color = iconColor
        fillPaint.color = iconColor
        val s = min(width, height) * 0.24f
        when (key) {
            FloatNavKey.BACK -> {
                path.reset()
                path.moveTo(cx + s * 0.45f, cy - s)
                path.lineTo(cx - s * 0.55f, cy)
                path.lineTo(cx + s * 0.45f, cy + s)
                canvas.drawPath(path, strokePaint)
            }
            FloatNavKey.HOME -> canvas.drawCircle(cx, cy, s * 0.82f, strokePaint)
            FloatNavKey.RECENTS -> {
                val inset = s * 0.78f
                highlightRect.set(cx - inset, cy - inset, cx + inset, cy + inset)
                canvas.drawRoundRect(highlightRect, 2.5f * density, 2.5f * density, strokePaint)
            }
            FloatNavKey.CLOSE -> {
                val d = s * 0.7f
                canvas.drawLine(cx - d, cy - d, cx + d, cy + d, strokePaint)
                canvas.drawLine(cx + d, cy - d, cx - d, cy + d, strokePaint)
            }
            FloatNavKey.GRIP -> {
                // 2 列 × 3 行的拖动点，和 oneHandX 的把手语义一致，但用自绘避免 emoji 字体差异。
                for (row in gripRows) {
                    for (column in gripColumns) {
                        canvas.drawCircle(
                            cx + column * 3.2f * density,
                            cy + row * 4.2f * density,
                            1.25f * density,
                            fillPaint,
                        )
                    }
                }
            }
            FloatNavKey.NONE -> Unit
        }
    }
}
