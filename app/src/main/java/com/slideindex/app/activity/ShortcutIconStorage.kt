package com.slideindex.app.activity

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.scale
import android.net.Uri
import com.slideindex.app.ui.AppIconOverride
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

object ShortcutIconStorage {
    private const val DIR = "shortcut_icons"

    fun saveIconFromUri(context: Context, uri: Uri): String? {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        return saveIconFromBytes(context, bytes)
    }

    /** 用应用图标做自定义图标；优先走全局图标覆盖，和列表里的应用图标保持一致。 */
    fun saveIconFromPackage(context: Context, packageName: String): String? {
        if (packageName.isBlank()) return null
        val drawable = AppIconOverride.drawable(context, packageName, 128)
            ?: runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
            ?: return null
        val bytes = ByteArrayOutputStream().use { stream ->
            val bitmap = drawable.toBitmap(128, 128)
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) return null
            stream.toByteArray()
        }
        return saveIconFromBytes(context, bytes)
    }

    fun saveIconFromBytes(context: Context, bytes: ByteArray): String? {
        if (bytes.isEmpty()) return null
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val fileName = "custom-${UUID.randomUUID()}.png"
        File(dir, fileName).writeBytes(bytes)
        return "$DIR/$fileName"
    }

    fun loadBitmap(context: Context, iconPath: String?, size: Int = 128): Bitmap? {
        val relative = iconPath?.takeIf { it.startsWith("$DIR/") } ?: return null
        val file = File(context.filesDir, relative)
        if (!file.isFile) return null
        return runCatching {
            android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                ?.let { src ->
                    if (src.width == size && src.height == size) {
                        src
                    } else {
                        src.scale(size, size).also {
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
