package com.slideindex.app.screencapture

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.overlay.OverlayBlurGate
import com.slideindex.app.overlay.OverlayGlassForegroundDrawable
import com.slideindex.app.overlay.OverlayMaterialSpec
import com.slideindex.app.overlay.OverlayPanelTheme
import com.slideindex.app.overlay.OverlayWindowTypes
import com.slideindex.app.overlay.setupBackgroundBlur
import com.slideindex.app.service.SlideIndexAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 录制悬浮 dock：暂停/继续、停止、进入/退出「全局绘制」。
 *
 * 设计约束（与悬浮球那次全屏窗泄漏的教训对齐）：
 * - 只占一块 132×44dp 的小窗，不铺全屏；
 * - 静止时没有动画时钟，不产生帧；只有状态变化（暂停/绘制）才 invalidate 一次；
 * - 拖动时只更新窗口位置，不改尺寸。
 */
internal class RecordingDock(
    private val appContext: Context,
    private val onTogglePause: () -> Unit,
    private val onStop: () -> Unit,
    private val onToggleDraw: () -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    private var windowManager: WindowManager? = null
    private var dockView: DockView? = null
    private var params: WindowManager.LayoutParams? = null

    private var paused = false
    private var drawActive = false
    private var blurScope: CoroutineScope? = null
    private var densityScale = 1f

    // 拖动期间的基准
    private var dragDownRawX = 0f
    private var dragDownRawY = 0f
    private var dragBaseX = 0
    private var dragBaseY = 0

    fun show() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { show() }
            return
        }
        if (dockView != null) {
            dockView?.visibility = View.VISIBLE
            return
        }
        val host = SlideIndexAccessibilityService.overlayHostContext() ?: appContext
        val wm = host.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: run {
            Log.w(TAG, "show: no WindowManager")
            return
        }
        val density = host.resources.displayMetrics.density
        val width = dp(WIDTH_DP.toFloat(), density)
        val height = dp(HEIGHT_DP.toFloat(), density)
        val screenWidth = host.resources.displayMetrics.widthPixels
        val screenHeight = host.resources.displayMetrics.heightPixels

        val view = DockView(host, density)
        densityScale = density
        val lp = WindowManager.LayoutParams(
            width,
            height,
            OverlayWindowTypes.overlayWindowType(host),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = ((screenWidth - width) / 2).coerceAtLeast(0)
            y = (screenHeight - height - dp(140f, density)).coerceAtLeast(0)
        }
        OverlayWindowTypes.ensureNoBrightnessOverride(lp)

        val added = runCatching { wm.addView(view, lp) }.isSuccess
        if (!added) {
            Log.w(TAG, "show: addView failed (no overlay permission?)")
            return
        }
        windowManager = wm
        dockView = view
        params = lp
        applyBlurForCurrentSetting()
        startBlurWatch()
    }

    fun hide() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { hide() }
            return
        }
        val view = dockView ?: return
        blurScope?.cancel()
        blurScope = null
        windowManager?.let { wm -> runCatching { wm.removeView(view) } }
        dockView = null
        params = null
        windowManager = null
    }

    /**
     * 跟随「悬浮窗模糊」总开关；开关关闭或系统不支持跨窗模糊时，dock 保持现在的实色底。
     * 项目约定（见 [OverlayBlurGate]）：不允许降级成半透明 tint，只能实色。
     */
    private fun startBlurWatch() {
        blurScope?.cancel()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        blurScope = scope
        val settingsFlow = OverlayDependencyAccess.overlayDependencies(appContext)
            ?.settingsRepository
            ?.settings
        if (settingsFlow == null) {
            applyBlur(userEnabled = true)
            return
        }
        scope.launch {
            settingsFlow
                .map { it.overlayBlurEnabled }
                .distinctUntilChanged()
                .collect { enabled -> applyBlur(enabled) }
        }
    }

    /** 用当前设置快照决定首次是否启用模糊（窗口刚挂上时 Flow 可能还没发出首帧）。 */
    private fun applyBlurForCurrentSetting() {
        val userEnabled = OverlayDependencyAccess.overlayDependencies(appContext)
            ?.settingsRepository
            ?.readSnapshot()
            ?.overlayBlurEnabled
            ?: true
        applyBlur(userEnabled)
    }

    /**
     * 模糊走项目里已经验证可用的那条路：`ViewRootImpl.createBackgroundBlurDrawable()`
     * 生成的 `BackgroundBlurDrawable` 作为 view 背景（见 [setupBackgroundBlur]），
     * **不是** `WindowManager.LayoutParams` 的跨窗模糊——后者在 Flyme 上参数能写进去、
     * 但 SurfaceFlinger 不给 overlay 窗口糊，表现就是"只有半透明、没有模糊"。
     */
    private fun applyBlur(userEnabled: Boolean) {
        val wm = windowManager ?: return
        val view = dockView ?: return
        val canBlur = OverlayBlurGate.canBlur(wm, userEnabled)
        // 日/夜两条路都要用：模糊底与玻璃描边读 isDark，模糊不可用时的实色底和图标也得跟着换，
        // 否则日间会停在「浅色磨砂底 + 白图标」这种看不清的组合上。
        val spec = OverlayMaterialSpec.fromContext(appContext)
        val isDark = OverlayPanelTheme.isDarkTheme(appContext)
        view.setBlurred(canBlur)
        view.applyTheme(spec, isDark)
        if (canBlur) {
            val corner = dp(HEIGHT_DP.toFloat(), densityScale) / 2f
            setupBackgroundBlur(
                view = view,
                cornerRadiusPx = corner,
                blurRadiusPx = spec.blurRadiusPx(densityScale),
                tintColor = spec.tintColor(isDark),
            )
            view.foreground = OverlayGlassForegroundDrawable(
                spec = spec,
                isDark = isDark,
                cornerRadiusPx = corner,
                density = densityScale,
            )
            // 模糊区域要靠一次 surface 事务才会被合成器采纳；首帧画完可能再没有下一帧，
            // 这里主动补一帧（照抄项目里 LocalFrostedGlassBackdrop 的做法）。
            view.postInvalidateOnAnimation()
        } else {
            view.background = null
            view.foreground = null
        }
    }

    fun setPaused(value: Boolean) {
        if (paused == value) return
        paused = value
        dockView?.invalidate()
    }

    fun setDrawActive(value: Boolean) {
        if (drawActive == value) return
        drawActive = value
        dockView?.invalidate()
    }

    fun setVisible(visible: Boolean) {
        dockView?.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun moveDockTo(x: Int, y: Int) {
        val view = dockView ?: return
        val wm = windowManager ?: return
        val lp = params ?: return
        lp.x = x
        lp.y = y
        runCatching { wm.updateViewLayout(view, lp) }
    }

    private fun beginDrag(rawX: Float, rawY: Float) {
        val lp = params ?: return
        dragDownRawX = rawX
        dragDownRawY = rawY
        dragBaseX = lp.x
        dragBaseY = lp.y
    }

    private fun dragTo(rawX: Float, rawY: Float) {
        val view = dockView ?: return
        val lp = params ?: return
        val screenWidth = view.resources.displayMetrics.widthPixels
        val screenHeight = view.resources.displayMetrics.heightPixels
        val x = (dragBaseX + (rawX - dragDownRawX)).roundToInt()
            .coerceIn(-lp.width / 4, screenWidth - lp.width * 3 / 4)
        val y = (dragBaseY + (rawY - dragDownRawY)).roundToInt()
            .coerceIn(0, (screenHeight - lp.height).coerceAtLeast(0))
        moveDockTo(x, y)
    }

    /**
     * dock 本体：圆角底 + 三个图标（暂停/继续、停止、绘制）。
     * 只在 [paused] / [drawActive] 变化时 invalidate，没有逐帧动画。
     */
    private inner class DockView(context: Context, private val density: Float) : View(context) {
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        /**
         * 日/夜只决定这一层的取色：材质规范里的实色兜底、描边、图标与三个语义色。
         * 模糊能用时底色由 `BackgroundBlurDrawable` 按同一份 [OverlayMaterialSpec] 画，这里只负责前景。
         */
        private var isDark = OverlayPanelTheme.isDarkTheme(context)
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f * density
        }
        private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = ICON_COLOR_DARK
        }
        private val iconStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = ICON_COLOR_DARK
        }
        private val rect = RectF()
        private val path = Path()

        private var downX = 0f
        private var downY = 0f
        private var downIndex = -1
        private var dragging = false
        private var blurred = false

        init {
            applyTheme(OverlayMaterialSpec.fromContext(appContext), isDark)
        }

        /** 按当前材质规范与日/夜刷新实色底与描边；图标颜色在 [onDraw] 里按 [isDark] 现取。 */
        fun applyTheme(spec: OverlayMaterialSpec, dark: Boolean) {
            isDark = dark
            bgPaint.color = spec.solidFallbackColor(dark)
            borderPaint.color = spec.innerBorderColor(dark)
            invalidate()
        }

        /** 模糊生效时背景交给 `BackgroundBlurDrawable` 画，这里只留描边和图标。 */
        fun setBlurred(value: Boolean) {
            if (blurred == value) return
            blurred = value
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val radius = h / 2f
            rect.set(0f, 0f, w, h)
            if (!blurred) {
                canvas.drawRoundRect(rect, radius, radius, bgPaint)
                // 模糊时描边由 OverlayGlassForegroundDrawable 按同一份规范画，别叠两遍。
                canvas.drawRoundRect(rect, radius, radius, borderPaint)
            }

            val slot = w / 3f
            val idleColor = if (isDark) ICON_COLOR_DARK else ICON_COLOR_LIGHT

            // 1) 暂停 / 继续
            setIconColor(if (paused) pausedColor() else idleColor)
            val c1x = slot / 2f
            if (paused) drawPlay(canvas, c1x, h / 2f) else drawPause(canvas, c1x, h / 2f)

            // 2) 停止
            setIconColor(stopColor())
            drawStop(canvas, slot * 1.5f, h / 2f)

            // 3) 绘制
            setIconColor(if (drawActive) activeColor() else idleColor)
            drawPen(canvas, slot * 2.5f, h / 2f)
        }

        private fun stopColor(): Int = if (isDark) STOP_COLOR_DARK else STOP_COLOR_LIGHT

        private fun pausedColor(): Int = if (isDark) PAUSED_COLOR_DARK else PAUSED_COLOR_LIGHT

        private fun activeColor(): Int = if (isDark) ACTIVE_COLOR_DARK else ACTIVE_COLOR_LIGHT

        private fun setIconColor(color: Int) {
            iconPaint.color = color
            iconStrokePaint.color = color
        }

        private fun drawPause(canvas: Canvas, cx: Float, cy: Float) {
            val barW = 3.2f * density
            val barH = 13f * density
            val gap = 2.4f * density
            rect.set(cx - gap - barW, cy - barH / 2f, cx - gap, cy + barH / 2f)
            canvas.drawRoundRect(rect, barW / 2f, barW / 2f, iconPaint)
            rect.set(cx + gap, cy - barH / 2f, cx + gap + barW, cy + barH / 2f)
            canvas.drawRoundRect(rect, barW / 2f, barW / 2f, iconPaint)
        }

        private fun drawPlay(canvas: Canvas, cx: Float, cy: Float) {
            path.reset()
            val size = 7f * density
            path.moveTo(cx - size * 0.6f, cy - size)
            path.lineTo(cx + size, cy)
            path.lineTo(cx - size * 0.6f, cy + size)
            path.close()
            canvas.drawPath(path, iconPaint)
        }

        private fun drawStop(canvas: Canvas, cx: Float, cy: Float) {
            val half = 6f * density
            rect.set(cx - half, cy - half, cx + half, cy + half)
            val r = 2.2f * density
            canvas.drawRoundRect(rect, r, r, iconPaint)
        }

        private fun drawPen(canvas: Canvas, cx: Float, cy: Float) {
            iconStrokePaint.strokeWidth = 4f * density
            canvas.save()
            canvas.rotate(-45f, cx, cy)
            // 笔杆
            rect.set(cx - 2.4f * density, cy - 9.5f * density, cx + 2.4f * density, cy + 3.2f * density)
            canvas.drawRoundRect(rect, 1.6f * density, 1.6f * density, iconStrokePaint)
            // 笔尖
            path.reset()
            path.moveTo(cx - 2.4f * density, cy + 3.2f * density)
            path.lineTo(cx + 2.4f * density, cy + 3.2f * density)
            path.lineTo(cx, cy + 8.8f * density)
            path.close()
            canvas.drawPath(path, iconPaint)
            canvas.restore()
        }

        private fun iconIndexAt(x: Float): Int {
            val slot = width / 3f
            return (x / slot).toInt().coerceIn(0, 2)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    downIndex = iconIndexAt(event.x)
                    dragging = false
                    beginDrag(event.rawX, event.rawY)
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (!dragging &&
                        (abs(event.rawX - downX) > touchSlop || abs(event.rawY - downY) > touchSlop)
                    ) {
                        dragging = true
                    }
                    if (dragging) dragTo(event.rawX, event.rawY)
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    if (!dragging) {
                        val index = iconIndexAt(event.x)
                        if (index == downIndex) {
                            when (index) {
                                0 -> onTogglePause()
                                1 -> onStop()
                                2 -> onToggleDraw()
                            }
                        }
                    }
                    dragging = false
                    return true
                }

                MotionEvent.ACTION_CANCEL -> {
                    dragging = false
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        override fun onConfigurationChanged(newConfig: Configuration) {
            super.onConfigurationChanged(newConfig)
            // 录屏可能持续几分钟，中途切日夜要连模糊 tint / 玻璃描边一起换：
            // 只 invalidate 会留下"新图标配旧底色"。
            applyBlurForCurrentSetting()
        }
    }

    private fun dp(value: Float, density: Float): Int = (value * density).roundToInt()

    private companion object {
        private const val TAG = "RecordingDock"
        private const val WIDTH_DP = 132
        private const val HEIGHT_DP = 44
        private const val BLUR_RADIUS_DP = 30
        private const val MAX_BLUR_PX = 80

        // 实色兜底与描边不再写死：跟模糊路径一样走材质规范（见 applyTheme）。
        private const val BLUR_TINT = 0x331C1C1E
        private val ICON_COLOR_DARK = Color.WHITE

        /** 日间图标基色：材质规范 light 主题的正文色（#1E1E1E，90% 不透明）。 */
        private val ICON_COLOR_LIGHT = Color.argb(0xE6, 0x1E, 0x1E, 0x1E)

        private val STOP_COLOR_DARK = Color.parseColor("#FFEF5350")
        private val STOP_COLOR_LIGHT = Color.parseColor("#FFD32F2F")
        private val PAUSED_COLOR_DARK = Color.parseColor("#FFFFB300")
        private val PAUSED_COLOR_LIGHT = Color.parseColor("#FFE65100")
        private val ACTIVE_COLOR_DARK = Color.parseColor("#FF66BB6A")
        private val ACTIVE_COLOR_LIGHT = Color.parseColor("#FF388E3C")
    }
}
