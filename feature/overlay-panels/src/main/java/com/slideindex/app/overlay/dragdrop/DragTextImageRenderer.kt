package com.slideindex.app.overlay.dragdrop

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint

/**
 * 将长文本在后台离线排版并绘制生成高清纯白底长图 Bitmap。
 */
object DragTextImageRenderer {
    private const val DEFAULT_WIDTH = 560
    private const val PADDING = 32
    private const val MAX_TEXT_LENGTH = 50_000
    private const val MAX_BITMAP_HEIGHT = 8192

    fun render(text: String): Bitmap? {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_TEXT_LENGTH) return null
        val paint = TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(17, 24, 39)
            textSize = 36f
        }
        val layoutWidth = (DEFAULT_WIDTH - PADDING * 2).coerceAtLeast(100)
        val layout = StaticLayout.Builder.obtain(trimmed, 0, trimmed.length, paint, layoutWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(true)
            .setLineSpacing(6f, 1f)
            .build()
        val height = (layout.height + PADDING * 2).coerceIn(1, MAX_BITMAP_HEIGHT)
        val bitmap = runCatching {
            Bitmap.createBitmap(DEFAULT_WIDTH, height, Bitmap.Config.ARGB_8888)
        }.getOrNull() ?: return null

        Canvas(bitmap).apply {
            drawColor(Color.WHITE)
            translate(PADDING.toFloat(), PADDING.toFloat())
            layout.draw(this)
        }
        return bitmap
    }
}
