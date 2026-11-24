/*
 * 移植自 GestureEVO（https://github.com/LumiaGG/GestureEVO，GPL-3.0-only）
 *   源文件：drop/DragContentPreview.kt
 * 已按本项目的包结构、设置体系与流转栏集成层改写（部分逻辑为本项目新增）；
 * 版权与许可声明见 THIRD_PARTY_NOTICES.md，文件级映射见 docs/ATTRIBUTIONS.md。
 */

package com.slideindex.app.dragpress

import android.content.ClipData
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 拖拽中悬浮在手指上方的小气泡缩略窗（展示被撕下的文字摘要或图片缩略图），跟随手指移动。
 */
internal class DragContentPreview(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val density = context.resources.displayMetrics.density

    private fun dp(dp: Float): Int = (dp * density).toInt()

    private fun isNightMode(): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    private fun resolveSurfaceContainerHighColor(): Int {
        val night = isNightMode()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val resId = if (night) android.R.color.system_neutral1_800 else android.R.color.system_neutral1_100
            val dynamicBase = runCatching { ContextCompat.getColor(context, resId) }.getOrNull()
            if (dynamicBase != null) {
                return if (night) {
                    ColorUtils.setAlphaComponent(dynamicBase, 242)
                } else {
                    ColorUtils.setAlphaComponent(ColorUtils.blendARGB(dynamicBase, Color.WHITE, 0.35f), 246)
                }
            }
        }
        return if (night) Color.argb(242, 43, 41, 48) else Color.argb(246, 243, 237, 247)
    }

    private fun resolveOnSurfaceColor(): Int {
        val night = isNightMode()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val resId = if (night) android.R.color.system_neutral1_100 else android.R.color.system_neutral1_900
            val dynamicText = runCatching { ContextCompat.getColor(context, resId) }.getOrNull()
            if (dynamicText != null) return dynamicText
        }
        return if (night) Color.rgb(230, 225, 229) else Color.rgb(28, 27, 31)
    }

    private fun resolveGlassEdgeStrokeColor(): Int {
        val night = isNightMode()
        return if (night) Color.argb(40, 255, 255, 255) else Color.argb(32, 0, 0, 0)
    }

    private fun applyThemedCardBackground() {
        card.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 12f * density
            setColor(resolveSurfaceContainerHighColor())
            setStroke((1f * density).toInt().coerceAtLeast(1), resolveGlassEdgeStrokeColor())
        }
    }

    private val card = FrameLayout(context).apply {
        elevation = 4f * density
        clipToOutline = true
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    private val image = ImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
    }

    private val params = WindowManager.LayoutParams().apply {
        type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        format = PixelFormat.TRANSLUCENT
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        gravity = Gravity.TOP or Gravity.START
        title = "SlideIndex:DragContentPreview"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            fitInsetsTypes = 0
        }
    }

    private var active = false
    private var job: Job? = null
    private var bitmap: Bitmap? = null
    private var generation = 0

    suspend fun show(content: ClipData, x: Float, y: Float, screen: Rect): Boolean {
        resetContent()
        if (content.itemCount == 0 || screen.isEmpty) {
            clear()
            return false
        }
        val item = content.getItemAt(0)
        val text = item.text?.takeIf { it.isNotBlank() }
        val uri = item.uri
        if (text == null && uri == null) {
            clear()
            return false
        }
        active = true
        val request = generation
        val margin = dp(16f)
        val maxWidth = minOf(dp(220f), (screen.width() - margin).coerceAtLeast(1))
        val maxHeight = (screen.height() - margin).coerceAtLeast(1)

        card.removeAllViews()
        applyThemedCardBackground()
        if (text != null) {
            val label = TextView(context).apply {
                this.text = DragContentPreviewPolicy.summary(text)
                setTextColor(resolveOnSurfaceColor())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                includeFontPadding = false
                maxLines = 3
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12f), dp(8f), dp(12f), dp(8f))
            }
            card.addView(label, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            card.measure(
                View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST)
            )
            params.width = card.measuredWidth.coerceIn(1, maxWidth)
            params.height = card.measuredHeight.coerceIn(1, maxHeight)
        } else {
            params.width = dp(64f).coerceAtMost(maxWidth)
            params.height = dp(64f).coerceAtMost(maxHeight)
            image.setImageResource(android.R.drawable.ic_menu_gallery)
            card.addView(image, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }

        move(x, y, screen)

        val accepted = if (card.isAttachedToWindow) {
            runCatching { windowManager.updateViewLayout(card, params) }
            true
        } else {
            runCatching {
                windowManager.addView(card, params)
                true
            }.getOrDefault(false)
        }

        if (!accepted) {
            clear()
            return false
        }

        if (text == null && uri != null) {
            job = scope.launch(Dispatchers.Main) {
                var decoded: Bitmap? = null
                try {
                    withContext(Dispatchers.IO) {
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                        if (options.outWidth <= 0 || options.outHeight <= 0) return@withContext
                        options.inJustDecodeBounds = false
                        val limit = dp(128f).coerceAtLeast(1)
                        while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize.coerceAtLeast(1) > limit) {
                            options.inSampleSize = options.inSampleSize.coerceAtLeast(1) * 2
                        }
                        decoded = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                    }
                    if (active && request == generation && decoded != null) {
                        val bmp = decoded!!
                        val maxThumbSide = dp(128f).toFloat().coerceAtLeast(1f)
                        val scale = minOf(
                            maxThumbSide / bmp.width.coerceAtLeast(1).toFloat(),
                            maxThumbSide / bmp.height.coerceAtLeast(1).toFloat(),
                            1f,
                        )
                        val pad = dp(10f)
                        params.width = ((bmp.width * scale).toInt() + pad * 2).coerceIn(dp(64f), maxWidth)
                        params.height = ((bmp.height * scale).toInt() + pad * 2).coerceIn(dp(64f), maxHeight)
                        image.setPadding(pad, pad, pad, pad)
                        image.setImageBitmap(bmp)
                        bitmap = bmp
                        if (card.isAttachedToWindow) {
                            runCatching { windowManager.updateViewLayout(card, params) }
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.w("DragContentPreview", "Preview thumbnail failed", error)
                }
            }
        }
        return true
    }

    fun move(x: Float, y: Float, screen: Rect) {
        if (!active) return
        val safe = Rect(screen).apply { inset(dp(8f), dp(8f)) }
        val point = DragContentPreviewPolicy.position(
            x,
            y,
            params.width,
            params.height,
            DragContentPreviewPolicy.Box(safe.left, safe.top, safe.right, safe.bottom),
            dp(20f)
        )
        params.x = point.x
        params.y = point.y
        if (card.isAttachedToWindow) {
            runCatching { windowManager.updateViewLayout(card, params) }
        }
    }

    fun clear() {
        resetContent()
        if (card.isAttachedToWindow) {
            runCatching { windowManager.removeView(card) }
        }
    }

    private fun resetContent() {
        generation++
        active = false
        job?.cancel()
        job = null
        image.setImageDrawable(null)
        card.removeAllViews()
        bitmap?.recycle()
        bitmap = null
    }
}
