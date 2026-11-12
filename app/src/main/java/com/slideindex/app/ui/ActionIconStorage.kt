package com.slideindex.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.util.ImageDecodeGuard
import java.io.File
import java.io.FileOutputStream

/**
 * 动作自定义图标的**图片存储**。
 *
 * 为什么落盘成私有文件、而不是直接存相册 Uri：
 * 相册返回的 Uri 只有临时读权限，重启/清理后就失效（图标会变成空白）。
 * 这里在选图时立刻解码、缩放到 192px 并写进 `filesDir/action_icons/`，
 * 之后只读本地文件，与相册权限彻底解耦。
 *
 * 与 [ActionDisplayNameStore] 共用同一套动作身份键，所以"改动作内容 → 名字和图标一起失效"。
 */
object ActionIconStorage {

    private const val DIR_NAME = "action_icons"
    private const val MAX_SIZE_PX = 192

    private fun dir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { if (!exists()) mkdirs() }

    private fun fileNameOf(context: Context, action: GestureAction): String =
        ActionDisplayNameStore.actionKey(action).hashCode().toUInt().toString(16) + ".png"

    /**
     * 从相册 Uri 存图标。
     *
     * @return 存好的本地文件路径（存进 [ActionDisplayNameStore.setIcon]）；失败返回 null。
     */
    fun saveFromUri(context: Context, action: GestureAction, uri: Uri): String? {
        val bitmap = ImageDecodeGuard.decodeUri(
            context = context,
            uri = uri,
            maxSidePx = MAX_SIZE_PX,
        ) ?: return null
        val scaled = scaleDown(bitmap)
        val target = File(dir(context), fileNameOf(context, action))
        val ok = runCatching {
            FileOutputStream(target).use { out ->
                scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }.isSuccess
        if (scaled !== bitmap) scaled.recycle()
        if (bitmap !== scaled) bitmap.recycle()
        return if (ok) target.absolutePath else null
    }

    /** 读回自定义图标；没设过 / 文件丢了返回 null（调用方回退到动作自带图标）。 */
    fun load(context: Context, action: GestureAction, iconKey: String?): Bitmap? {
        val path = iconKey?.takeIf { it.isNotBlank() } ?: return null
        if (!File(path).exists()) return null
        return ImageDecodeGuard.decodeFile(path, MAX_SIZE_PX)
    }

    /** 删掉这个动作的自定义图标文件（恢复默认时调用）。 */
    fun delete(context: Context, action: GestureAction) {
        runCatching { File(dir(context), fileNameOf(context, action)).delete() }
    }

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        if (maxSide <= MAX_SIZE_PX) return bitmap
        val ratio = MAX_SIZE_PX.toFloat() / maxSide
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }

    /** 全局图标：按任意 key（如 `global:action:OPEN_INDEX`）存图片。 */
    fun saveFromUriForGlobalKey(context: Context, key: String, uri: android.net.Uri): String? {
        val bitmap = ImageDecodeGuard.decodeUri(
            context = context,
            uri = uri,
            maxSidePx = MAX_SIZE_PX,
        ) ?: return null
        val scaled = scaleDown(bitmap)
        val target = File(dir(context), globalFileName(key))
        val ok = runCatching {
            FileOutputStream(target).use { out ->
                scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }.isSuccess
        if (scaled !== bitmap) scaled.recycle()
        if (bitmap !== scaled) bitmap.recycle()
        return if (ok) target.absolutePath else null
    }

    /** 全局图标：按任意 key 删除图片文件。 */
    fun deleteForGlobalKey(context: Context, key: String) {
        runCatching { File(dir(context), globalFileName(key)).delete() }
    }

    private fun globalFileName(key: String): String =
        key.hashCode().toUInt().toString(16) + ".png"

    /** 全局图标：把 Bitmap（如应用图标）按任意 key 落盘。 */
    fun saveBitmapForGlobalKey(context: Context, key: String, bitmap: Bitmap): String? {
        val target = File(dir(context), globalFileName(key))
        val ok = runCatching {
            FileOutputStream(target).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }.isSuccess
        return if (ok) target.absolutePath else null
    }
}
