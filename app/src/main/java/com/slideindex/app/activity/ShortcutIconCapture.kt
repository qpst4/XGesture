package com.slideindex.app.activity

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.core.content.IntentCompat
import androidx.core.graphics.drawable.toBitmap
import java.io.ByteArrayOutputStream

/**
 * 在**导入快捷方式的那一刻**把自带图标存下来（反馈 #7 / #390）。
 *
 * 为什么不能等到渲染时再读：应用内直达只持久化 `intent` 的 URI，
 * `Intent.parseUri(URI_INTENT_SCHEME)` 不会带上 Parcelable 的图标 extra，
 * 所以渲染阶段再读 `EXTRA_SHORTCUT_ICON` 永远是空。只有在还拿着原始
 * `Intent` / `ShortcutInfo` 的这一刻把图标落成文件，才拿得到第三方
 * 「创建快捷方式」类应用自定义的图标。
 *
 * 返回可直接写进 [ActivityShortcut.iconPath] 的路径；拿不到图标时返回 null。
 */
object ShortcutIconCapture {

    fun captureFromIntent(context: Context, intent: Intent?): String? {
        if (intent == null) return null
        val bitmap = runCatching {
            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_SHORTCUT_ICON, Bitmap::class.java)
        }.getOrNull()
        if (bitmap != null) {
            return saveBitmap(context, bitmap)
        }
        val iconResource = runCatching {
            IntentCompat.getParcelableExtra(
                intent,
                Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                Intent.ShortcutIconResource::class.java,
            )
        }.getOrNull() ?: return null
        val drawable = runCatching {
            val res = context.packageManager.getResourcesForApplication(iconResource.packageName)
            val id = res.getIdentifier(iconResource.resourceName, null, iconResource.packageName)
            if (id == 0) return@runCatching null
            @Suppress("DEPRECATION")
            res.getDrawable(id, context.theme)
        }.getOrNull() ?: return null
        return saveDrawable(context, drawable)
    }

    private fun saveDrawable(context: Context, drawable: Drawable): String? = runCatching {
        val bitmap = drawable.toBitmap(width = 144, height = 144)
        saveBitmap(context, bitmap)
    }.getOrNull()

    private fun saveBitmap(context: Context, bitmap: Bitmap): String? = runCatching {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        ShortcutIconStorage.saveIconFromBytes(context, stream.toByteArray())
    }.getOrNull()
}
