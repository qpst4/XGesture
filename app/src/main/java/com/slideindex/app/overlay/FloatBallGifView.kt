@file:Suppress("DEPRECATION")

package com.slideindex.app.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Movie
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.PowerManager
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withClip
import kotlin.math.roundToInt

/**
 * 悬浮球 GIF 的绘制面（普通 View，对齐 FV 的做法）。
 *
 * 之前这里是 `SurfaceView` + `lockCanvas` 软件绘制；但球改成**独立小球窗**后，
 * SurfaceView 的画面（独立图层）会被压在窗口下面，GIF 直接看不见。
 * 现在全部走普通 View 的 `onDraw`：能自动吃到窗口合成、alpha、圆角裁剪。
 *
 * 三种内容来源：
 * - [setAnimatedDrawable]：`AnimatedImageDrawable`（ImageDecoder 解出来的动图，自己播放）
 * - [showCachedFrame]：预解码好的 Bitmap（小 GIF 全帧缓存）
 * - [showStreamingFrame]：`Movie` 逐帧（大 GIF 兜底）
 */
internal class FloatBallGifView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    companion object {
        /** 低于这个不透明度就当作“看不见”，停止出帧。 */
        private const val HIDDEN_ALPHA = 0.01f
    }

    private val circlePath = Path()
    private val circleRect = RectF()
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private var cachedBitmap: Bitmap? = null
    private var streamingMovie: Movie? = null
    private var streamingElapsedMs = 0
    private var streamingOutW = 0
    private var streamingOutH = 0
    private var streamingMode = false

    /** ImageDecoder 解出来的动图；非空时优先于其它两种来源。 */
    private var animatedDrawable: Drawable? = null

    private var frameAlpha = 1f

    /** 「闲置时停止动图」冻结状态；动图自己播，必须显式 stop/start。 */
    private var animatedFrozen = false

    /** 熄屏时也停帧（旧 tick 路径靠 canAnimateFrame 判断，新动图路径要单独处理）。 */
    private var screenOn = true

    private val screenReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            screenOn = intent?.action != android.content.Intent.ACTION_SCREEN_OFF
            applyAnimatedRunningState()
        }
    }

    init {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        screenOn = powerManager?.isInteractive != false
        runCatching {
            val filter = android.content.IntentFilter().apply {
                addAction(android.content.Intent.ACTION_SCREEN_ON)
                addAction(android.content.Intent.ACTION_SCREEN_OFF)
            }
            context.registerReceiver(screenReceiver, filter)
        }
    }

    fun setFrameAlpha(alpha: Float) {
        val clamped = alpha.coerceIn(0f, 1f)
        if (frameAlpha == clamped) return
        frameAlpha = clamped
        applyAnimatedRunningState()
        invalidate()
    }

    /** 「闲置时停止动图」：冻结/恢复动图（AnimatedImageDrawable 自己播，只能显式停）。 */
    fun setAnimatedFrozen(frozen: Boolean) {
        if (animatedFrozen == frozen) return
        animatedFrozen = frozen
        applyAnimatedRunningState()
    }

    /** 只有“未冻结 且 可见”时才让动图跑；隐藏到不可见时停帧省电。 */
    private fun applyAnimatedRunningState() {
        val animatable = animatedDrawable as? android.graphics.drawable.Animatable ?: return
        val shouldRun = !animatedFrozen && frameAlpha > HIDDEN_ALPHA && isShown && screenOn
        if (shouldRun) animatable.start() else animatable.stop()
    }

    /** 用系统动图（AnimatedImageDrawable）绘制；会自动播放。 */
    fun setAnimatedDrawable(drawable: Drawable?) {
        val previous = animatedDrawable
        animatedDrawable = drawable
        if (previous !== drawable) {
            (previous as? android.graphics.drawable.Animatable)?.stop()
        }
        if (drawable != null) {
            cachedBitmap = null
            streamingMovie = null
            streamingMode = false
            drawable.setBounds(0, 0, width.coerceAtLeast(1), height.coerceAtLeast(1))
            applyAnimatedRunningState()
        }
        invalidate()
        previous?.setCallback(null)
        if (drawable != null) drawable.callback = this
    }

    fun showCachedFrame(bitmap: Bitmap) {
        animatedDrawable?.let {
            (it as? android.graphics.drawable.Animatable)?.stop()
            it.callback = null
        }
        animatedDrawable = null
        streamingMode = false
        streamingMovie = null
        cachedBitmap = bitmap
        invalidate()
    }

    fun showStreamingFrame(movie: Movie, elapsedMs: Int, outW: Int, outH: Int) {
        animatedDrawable?.let {
            (it as? android.graphics.drawable.Animatable)?.stop()
            it.callback = null
        }
        animatedDrawable = null
        streamingMode = true
        cachedBitmap = null
        streamingMovie = movie
        streamingElapsedMs = elapsedMs
        streamingOutW = outW
        streamingOutH = outH
        invalidate()
    }

    fun clearFrame() {
        animatedDrawable?.let {
            (it as? android.graphics.drawable.Animatable)?.stop()
            it.callback = null
        }
        animatedDrawable = null
        streamingMode = false
        streamingMovie = null
        cachedBitmap = null
        invalidate()
    }

    /** Current visible frame for drag shell — never mutates internal state. */
    fun snapshotCurrentFrame(): Bitmap? {
        val outW = width.coerceAtLeast(1)
        val outH = height.coerceAtLeast(1)
        if (outW <= 0 || outH <= 0) return null
        val drawable = animatedDrawable
        if (drawable != null) {
            val bitmap = createBitmap(outW, outH)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, outW, outH)
            drawable.draw(canvas)
            return bitmap
        }
        if (!streamingMode) {
            return cachedBitmap
                ?.takeIf { !it.isRecycled }
                ?.copy(Bitmap.Config.ARGB_8888, false)
        }
        val movie = streamingMovie ?: return null
        val durationMs = movie.duration().takeIf { it > 0 } ?: 1_000
        val bitmap = createBitmap(
            streamingOutW.coerceAtLeast(1),
            streamingOutH.coerceAtLeast(1),
        )
        val canvas = Canvas(bitmap)
        movie.setTime(streamingElapsedMs % durationMs)
        val scaleX = streamingOutW.toFloat() / movie.width().coerceAtLeast(1)
        val scaleY = streamingOutH.toFloat() / movie.height().coerceAtLeast(1)
        canvas.scale(scaleX, scaleY)
        movie.draw(canvas, 0f, 0f)
        return bitmap
    }

    /** 普通 View 由播放器控制重绘节奏，这里仅为兼容旧调用点。 */
    fun setAnimating(@Suppress("UNUSED_PARAMETER") animating: Boolean) = Unit

    /** 球不可见（窗口隐藏 / 从窗口树摘掉）、熄屏、或**完全透明**时，逐帧重绘没有意义。 */
    fun canAnimateFrame(): Boolean {
        if (frameAlpha <= HIDDEN_ALPHA) return false
        if (!isShown || windowVisibility != VISIBLE) return false
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isInteractive != false
    }

    fun isFrameInvisible(): Boolean = frameAlpha <= HIDDEN_ALPHA

    /** 诊断用：当前画面上到底有没有内容（日志排查用）。 */
    fun debugState(): String {
        val frame = cachedBitmap
        val frameDesc = when {
            frame == null -> "null"
            frame.isRecycled -> "recycled"
            else -> "${frame.width}x${frame.height}"
        }
        return "alpha=${"%.2f".format(java.util.Locale.US, frameAlpha)} " +
            "anim=${animatedDrawable != null} stream=$streamingMode frame=$frameDesc " +
            "shown=$isShown winVis=$windowVisibility"
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        animatedDrawable?.setBounds(0, 0, w.coerceAtLeast(1), h.coerceAtLeast(1))
    }

    override fun onDraw(canvas: Canvas) {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return
        circleRect.set(0f, 0f, w.toFloat(), h.toFloat())
        circlePath.reset()
        circlePath.addOval(circleRect, Path.Direction.CW)

        val alphaInt = (frameAlpha * 255f).roundToInt().coerceIn(0, 255)
        if (alphaInt == 0) return
        val layer = if (alphaInt < 255) {
            canvas.saveLayerAlpha(0f, 0f, w.toFloat(), h.toFloat(), alphaInt)
        } else {
            -1
        }
        canvas.withClip(circlePath) {
            val drawable = animatedDrawable
            if (drawable != null) {
                drawable.setBounds(0, 0, w, h)
                drawable.draw(this)
                return@withClip
            }
            if (streamingMode) {
                val movie = streamingMovie ?: return@withClip
                val durationMs = movie.duration().takeIf { it > 0 } ?: 1_000
                movie.setTime(streamingElapsedMs % durationMs)
                val scaleX = streamingOutW.toFloat() / movie.width().coerceAtLeast(1)
                val scaleY = streamingOutH.toFloat() / movie.height().coerceAtLeast(1)
                this.scale(scaleX, scaleY)
                movie.draw(this, 0f, 0f)
                return@withClip
            }
            val bitmap = cachedBitmap ?: return@withClip
            if (bitmap.isRecycled) return@withClip
            drawBitmap(bitmap, null, android.graphics.Rect(0, 0, w, h), bitmapPaint)
        }
        if (layer >= 0) canvas.restoreToCount(layer)
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        applyAnimatedRunningState()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        applyAnimatedRunningState()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        applyAnimatedRunningState()
    }

    override fun onDetachedFromWindow() {
        animatedDrawable?.let {
            (it as? android.graphics.drawable.Animatable)?.stop()
            it.callback = null
        }
        runCatching { context.unregisterReceiver(screenReceiver) }
        super.onDetachedFromWindow()
    }
}
