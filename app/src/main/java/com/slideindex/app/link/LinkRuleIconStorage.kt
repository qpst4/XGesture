package com.slideindex.app.link

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.graphics.scale
import com.slideindex.app.util.ImageDecodeGuard
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/**
 * 链接分发规则的自定义头像本地存储管理器。
 */
object LinkRuleIconStorage {
    private const val DIR = "link_rule_icons"

    fun saveIconFromUri(context: Context, uri: Uri): String? {
        val bitmap = ImageDecodeGuard.decodeUri(
            context = context,
            uri = uri,
            maxSidePx = 192,
        ) ?: return null
        val bytes = runCatching {
            ByteArrayOutputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                stream.toByteArray()
            }
        }.getOrNull()
        if (!bitmap.isRecycled) bitmap.recycle()
        return bytes?.let { saveIconFromBytes(context, it) }
    }

    fun saveIconFromBytes(context: Context, bytes: ByteArray): String? {
        if (bytes.isEmpty()) return null
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val fileName = "custom-${UUID.randomUUID()}.png"
        File(dir, fileName).writeBytes(bytes)
        return "$DIR/$fileName"
    }

    fun loadBitmap(context: Context, iconPath: String?, sizePx: Int = 192): Bitmap? {
        val relative = iconPath?.takeIf { it.isNotBlank() } ?: return null
        val file = if (relative.startsWith("/")) File(relative) else File(context.filesDir, relative)
        if (!file.isFile) return null
        return runCatching {
            ImageDecodeGuard.decodeFile(file.absolutePath, sizePx)
                ?.let { src ->
                    if (src.width == sizePx && src.height == sizePx) {
                        src
                    } else {
                        src.scale(sizePx, sizePx).also {
                            if (it !== src) src.recycle()
                        }
                    }
                }
        }.getOrNull()
    }

    fun deleteIconIfOwned(context: Context, iconPath: String?) {
        val relative = iconPath?.takeIf { it.startsWith("$DIR/") } ?: return
        val file = File(context.filesDir, relative)
        if (file.exists()) {
            runCatching { file.delete() }
        }
    }
}
