package com.slideindex.app.shell

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import java.io.File

object ShellCommandIconResolver {
    private const val ICON_CORNER_FRACTION = 0.24f

    fun resolveBitmap(context: Context, command: ShellCommand, sizePx: Int): Bitmap? {
        val safeSize = sizePx.coerceAtLeast(1)
        return when (command.iconType) {
            ShellCommandIconType.URI -> loadUriBitmap(context, command.iconPath, safeSize)
            ShellCommandIconType.TEXT -> renderTextBitmap(command.textIcon, command.label, safeSize)
            ShellCommandIconType.OTHER -> null
        }
    }

    fun loadUriBitmap(context: Context, iconPath: String?, sizePx: Int): Bitmap? {
        val relative = iconPath?.takeIf { it.isNotBlank() } ?: return null
        val file = File(context.filesDir, relative)
        if (!file.exists()) return null
        val decoded = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull() ?: return null
        if (decoded.width == sizePx && decoded.height == sizePx) return decoded
        return decoded.scale(sizePx, sizePx)
    }

    fun renderTextBitmap(textIcon: String?, fallbackLabel: String, sizePx: Int): Bitmap {
        val text = textIcon?.take(2)?.ifBlank { null }
            ?: fallbackLabel.trim().take(1).ifBlank { "?" }
        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)
        val corner = sizePx * ICON_CORNER_FRACTION
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF5C6BC0.toInt()
        }
        canvas.drawRoundRect(RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat()), corner, corner, bgPaint)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            textSize = sizePx * if (text.length > 1) 0.34f else 0.42f
        }
        canvas.drawText(
            text,
            sizePx / 2f,
            sizePx / 2f - (textPaint.descent() + textPaint.ascent()) / 2f,
            textPaint
        )
        return bitmap
    }

    fun findForCommandLine(
        commandLine: String,
        shellCommands: List<ShellCommand>
    ): ShellCommand? =
        shellCommands.firstOrNull { it.command.trim() == commandLine.trim() && it.hasCustomIcon() }
}
