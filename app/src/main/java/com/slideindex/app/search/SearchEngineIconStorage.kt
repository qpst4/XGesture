package com.slideindex.app.search

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.graphics.drawable.toBitmap
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

object SearchEngineIconStorage {
    fun saveIconFromUri(context: Context, uri: Uri): String? {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        return saveIconFromBytes(context, bytes)
    }

    fun saveIconFromAsset(context: Context, assetRelativePath: String): String? {
        val bytes = runCatching {
            context.assets.open(assetRelativePath).use { it.readBytes() }
        }.getOrNull() ?: return null
        return saveIconFromBytes(context, bytes)
    }

    fun saveIconFromPackage(context: Context, packageName: String): String? {
        if (packageName.isBlank()) return null
        // 全局图标覆盖优先（扩展 → 图标库）：落快照时用的应该是用户当前看到的那张图
        val drawable = com.slideindex.app.ui.AppIconOverride.drawable(context, packageName, 128)
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
        val dir = File(context.filesDir, "search_icons").apply { mkdirs() }
        val fileName = "custom-${UUID.randomUUID()}.png"
        File(dir, fileName).writeBytes(bytes)
        return "search_icons/$fileName"
    }

    fun deleteIconIfOwned(context: Context, iconPath: String?) {
        val relative = iconPath?.takeIf { it.startsWith("search_icons/") } ?: return
        val file = File(context.filesDir, relative)
        if (file.exists()) {
            runCatching { file.delete() }
        }
    }
}
