package com.slideindex.app.overlay.holographic

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewParent
import androidx.core.view.ViewCompat
import com.slideindex.app.overlay.panels.R
import com.slideindex.app.settings.HolographicLauncherSettings
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

class Ball3DView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    companion object {
        /** px/s → rad/帧，再经 density 归一化 */
        private const val FLING_VELOCITY_SCALE = 2.5e-4f
        private const val FLING_MIN_VELOCITY_PX_PER_S = 900f
        private const val FLING_MAX_RAD_PER_FRAME = 0.85f
        private const val FLING_DECAY = 0.96f
        private const val FLING_STOP_THRESHOLD = 0.005f

        /** 球面布点上限：再多就挤成一团、看不出也点不准。 */
        const val MAX_BALL_APPS = 60
    }

    private data class AppNode(
        val app: HolographicLauncherApp,
        val originalX: Float,
        val originalY: Float,
        val originalZ: Float,
    ) {
        var currentX = 0f
        var currentY = 0f
        var currentZ = 0f
        var screenX = 0f
        var screenY = 0f
        var scale = 1f
        var alpha = 255
    }

    private val nodes = mutableListOf<AppNode>()
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val smallHintPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var rotX = 0.3f
    private var rotY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var sensitivity = HolographicLauncherSettings.DEFAULT_ROTATION_SENSITIVITY
    private var isDragging = false
    private var lastMoveX = 0f
    private var lastMoveY = 0f
    private var downTime = 0L
    private var velocityTracker: VelocityTracker? = null
    private val handler = Handler(Looper.getMainLooper())
    private var flingRunnable: Runnable? = null

    private var lastHapticRotX = 0f
    private var lastHapticRotY = 0f
    private var hapticLevel = HolographicLauncherSettings.DEFAULT_HAPTIC_LEVEL
    private val hapticThreshold = 0.12f

    private val iconSize: Float
    private val textSize: Float
    private val touchSlop: Int

    var onAppClick: ((HolographicLauncherApp) -> Unit)? = null
    var onTouchAction: (() -> Unit)? = null
    var onBlankClick: (() -> Unit)? = null
    var onRotationStateChange: ((Boolean) -> Unit)? = null

    init {
        setBackgroundColor(Color.TRANSPARENT)
        val dm = resources.displayMetrics
        iconSize = dm.density * 48f
        textSize = dm.density * 11f
        touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        textPaint.color = Color.WHITE
        textPaint.textSize = textSize
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.setShadowLayer(3f, 0f, 1f, Color.BLACK)
        shadowPaint.color = Color.argb(100, 0, 0, 0)
        hintPaint.color = Color.WHITE
        hintPaint.textSize = dm.density * 18f
        hintPaint.textAlign = Paint.Align.CENTER
        hintPaint.setShadowLayer(4f, 0f, 2f, Color.BLACK)
        smallHintPaint.color = Color.WHITE
        smallHintPaint.textAlign = Paint.Align.CENTER
        smallHintPaint.setShadowLayer(4f, 0f, 2f, Color.BLACK)
    }

    fun setApps(apps: List<HolographicLauncherApp>) {
        nodes.clear()
        if (apps.isEmpty()) {
            invalidate()
            return
        }
        // 不再 shuffled(Random)：球面位置必须与传入顺序一一对应，否则每次打开看到的
        // 应用都不一样（真机反馈「有些应用永远找不到」）。截断交给调用方决定。
        val take = apps.take(MAX_BALL_APPS)
        val size = take.size
        val goldenAngle = (3.0 - sqrt(5.0)) * Math.PI
        take.forEachIndexed { index, app ->
            val t = index.toDouble()
            val y = 1.0 - (t / (size - 1).coerceAtLeast(1)) * 2.0
            val radiusAtY = sqrt(1.0 - y * y)
            val theta = goldenAngle * t
            val x = cos(theta) * radiusAtY
            val z = sin(theta) * radiusAtY
            nodes += AppNode(app, x.toFloat(), y.toFloat(), z.toFloat())
        }
        invalidate()
    }

    fun applySettings(settings: HolographicLauncherSettings) {
        sensitivity = settings.rotationSensitivity.coerceIn(
            HolographicLauncherSettings.MIN_ROTATION_SENSITIVITY,
            HolographicLauncherSettings.MAX_ROTATION_SENSITIVITY,
        )
        hapticLevel = settings.hapticLevel.coerceIn(0, 3)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(cx, cy) * 0.98f
        if (nodes.isEmpty()) {
            canvas.drawText(context.getString(R.string.holographic_no_apps_loaded), cx, cy - 30f, hintPaint)
            smallHintPaint.textSize = resources.displayMetrics.density * 14f
            canvas.drawText(context.getString(R.string.holographic_check_settings_hint), cx, cy + 30f, smallHintPaint)
            return
        }
        val drawableNodes = mutableListOf<AppNode>()
        for (node in nodes) {
            val cosY = cos(rotY.toDouble()).toFloat()
            val sinY = sin(rotY.toDouble()).toFloat()
            val x1 = node.originalX * cosY - node.originalZ * sinY
            val z1 = node.originalX * sinY + node.originalZ * cosY
            val cosX = cos(rotX.toDouble()).toFloat()
            val sinX = sin(rotX.toDouble()).toFloat()
            val y1 = node.originalY * cosX - z1 * sinX
            val z2 = node.originalY * sinX + z1 * cosX
            node.currentX = x1
            node.currentY = y1
            node.currentZ = z2
            val scaleFactor = (2.8f / (2.8f - z2)) * 0.75f
            node.screenX = x1 * radius * scaleFactor + cx
            node.screenY = y1 * radius * scaleFactor + cy
            node.scale = scaleFactor.coerceIn(0.5f, 1.3f)
            if (z2 > -0.92f) {
                node.alpha = ((z2 + 1f) / 1.05f * 255f).toInt().coerceIn(20, 255)
                drawableNodes += node
            }
        }
        drawableNodes.sortBy { it.currentZ }
        for (node in drawableNodes) {
            drawNode(canvas, node)
        }
    }

    private fun drawNode(canvas: Canvas, node: AppNode) {
        val icon = node.app.icon ?: return
        val size = (iconSize * node.scale).toInt()
        val half = size / 2f
        val left = (node.screenX - half).toInt()
        val top = (node.screenY - half).toInt()
        canvas.drawOval(
            left - 6f,
            top - 6f,
            left + size + 6f,
            top + size + 6f,
            shadowPaint,
        )
        icon.setBounds(left, top, left + size, top + size)
        val savedAlpha = icon.alpha
        icon.alpha = node.alpha
        icon.draw(canvas)
        icon.alpha = savedAlpha
        if (node.scale <= 0.55f || node.alpha <= 100) return
        textPaint.alpha = node.alpha
        val label = node.app.label.take(4)
        canvas.drawText(label, node.screenX, node.screenY + half + textSize + 6f, textPaint)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                isDragging = false
                lastMoveX = event.x
                lastMoveY = event.y
                downTime = System.currentTimeMillis()
                val ballRadius = minOf(width / 2f, height / 2f) * 0.95f
                val dist = hypot(event.x - width / 2f, event.y - height / 2f)
                lastHapticRotX = rotX
                lastHapticRotY = rotY
                if (dist > ballRadius) {
                    onBlankClick?.invoke()
                    return true
                }
                velocityTracker = VelocityTracker.obtain()
                velocityTracker?.addMovement(event)
                stopFling()
                onTouchAction?.invoke()
                onRotationStateChange?.invoke(true)
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                val dx = event.x - lastTouchX
                val dy = event.y - lastTouchY
                if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                    isDragging = true
                }
                if (isDragging) {
                    val density = resources.displayMetrics.density
                    rotY += dx * sensitivity / density
                    rotX += dy * sensitivity / density
                    maybeHapticOnRotate()
                    lastTouchX = event.x
                    lastTouchY = event.y
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                velocityTracker?.addMovement(event)
                velocityTracker?.computeCurrentVelocity(1000)
                if (!isDragging) {
                    handleClick(event.x, event.y)
                    performClick()
                    onRotationStateChange?.invoke(false)
                } else {
                    val vx = velocityTracker?.xVelocity ?: 0f
                    val vy = velocityTracker?.yVelocity ?: 0f
                    val elapsed = System.currentTimeMillis() - downTime
                    val movedX = abs(event.x - lastMoveX)
                    val movedY = abs(event.y - lastMoveY)
                    val fast = abs(vx) > FLING_MIN_VELOCITY_PX_PER_S || abs(vy) > FLING_MIN_VELOCITY_PX_PER_S
                    val far = movedX > touchSlop * 3f || movedY > touchSlop * 3f
                    if (fast && far && elapsed < 500) {
                        startFling(vx, vy)
                    } else {
                        onRotationStateChange?.invoke(false)
                    }
                }
                velocityTracker?.recycle()
                velocityTracker = null
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun maybeHapticOnRotate() {
        if (hapticLevel <= 0) return
        val delta = hypot(rotX - lastHapticRotX, rotY - lastHapticRotY)
        if (delta <= hapticThreshold) return
        val feedback = when (hapticLevel) {
            1 -> android.view.HapticFeedbackConstants.CLOCK_TICK
            2 -> android.view.HapticFeedbackConstants.CONTEXT_CLICK
            3 -> android.view.HapticFeedbackConstants.CONFIRM
            else -> -1
        }
        if (feedback != -1) {
            performHapticFeedback(feedback)
        }
        runCatching {
            val vibrator = context.getSystemService(Vibrator::class.java)
            if (vibrator?.hasVibrator() == true) {
                val amplitude = when (hapticLevel) {
                    1 -> 80
                    2 -> 160
                    3 -> 255
                    else -> 128
                }
                vibrator.vibrate(VibrationEffect.createOneShot(12L, amplitude))
            }
        }
        lastHapticRotX = rotX
        lastHapticRotY = rotY
    }

    private fun handleClick(x: Float, y: Float) {
        var best: AppNode? = null
        var bestDist = Float.MAX_VALUE
        for (node in nodes) {
            if (node.currentZ <= -0.6f) continue
            val dx = x - node.screenX
            val dy = y - node.screenY
            val dist = hypot(dx, dy)
            val hitRadius = iconSize * node.scale * 0.6f
            if (dist < hitRadius && dist < bestDist) {
                best = node
                bestDist = dist
            }
        }
        best?.let { onAppClick?.invoke(it.app) }
    }

    private fun startFling(vxPxPerS: Float, vyPxPerS: Float) {
        val density = resources.displayMetrics.density
        onRotationStateChange?.invoke(true)
        var velocityX = (vxPxPerS / density * FLING_VELOCITY_SCALE)
            .coerceIn(-FLING_MAX_RAD_PER_FRAME, FLING_MAX_RAD_PER_FRAME)
        var velocityY = (vyPxPerS / density * FLING_VELOCITY_SCALE)
            .coerceIn(-FLING_MAX_RAD_PER_FRAME, FLING_MAX_RAD_PER_FRAME)
        val runnable = object : Runnable {
            override fun run() {
                if (abs(velocityX) < FLING_STOP_THRESHOLD && abs(velocityY) < FLING_STOP_THRESHOLD) {
                    onRotationStateChange?.invoke(false)
                    flingRunnable = null
                    return
                }
                rotY += velocityX
                rotX += velocityY
                velocityX *= FLING_DECAY
                velocityY *= FLING_DECAY
                invalidate()
                if (abs(velocityX) > FLING_STOP_THRESHOLD || abs(velocityY) > FLING_STOP_THRESHOLD) {
                    handler.postDelayed(this, 16L)
                } else {
                    onRotationStateChange?.invoke(false)
                    flingRunnable = null
                }
            }
        }
        flingRunnable = runnable
        handler.post(runnable)
    }

    private fun stopFling() {
        flingRunnable?.let { handler.removeCallbacks(it) }
        flingRunnable = null
        onRotationStateChange?.invoke(false)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopFling()
    }
}
