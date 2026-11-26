package com.slideindex.app.activity

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log

/**
 * 快捷方式图标的"快照仓库"。
 *
 * 第三方 App 用 ACTION_CREATE_SHORTCUT 回传的图标只在那一次结果 Intent 里，
 * 之后靠 URI 重新解析是拿不到的（`EXTRA_SHORTCUT_ICON` 不会跟着 URI 走）。
 * 所以导入时就把图标落盘，按**身份键**存起来，渲染时优先查这里，查不到再退回宿主图标。
 *
 * 身份键与 `ActivityShortcutMappings.findByIdentityKey` / `QuickLauncherItemCodec.shortcutItemKey`
 * 保持同一套格式：
 * - `intent:<uri>`；
 * - `intents:<uri1>\u001D<uri2>`；
 * - 组件型直接是 `pkg/cls`；
 * - 动态型是 `QuickLauncherItemCodec.shortcutKey(pkg, id)` 的结果。
 */
object ShortcutIconSnapshotStore {

    private const val TAG = "ShortcutIconSnapshot"
    private const val PREFS = "shortcut_icon_snapshot"

    fun identityKeyForIntent(uri: String): String = "intent:$uri"

    fun save(context: Context, identityKey: String, iconPath: String) {
        if (identityKey.isBlank() || iconPath.isBlank()) return
        prefs(context).edit().putString(identityKey, iconPath).apply()
        Log.i(TAG, "snapshot saved key=$identityKey path=$iconPath")
    }

    fun iconPath(context: Context, identityKey: String?): String? {
        if (identityKey.isNullOrBlank()) return null
        return prefs(context).getString(identityKey, null)?.takeIf { it.isNotBlank() }
    }

    fun clear(context: Context, identityKey: String) {
        prefs(context).edit().remove(identityKey).apply()
    }

    fun drawable(context: Context, identityKey: String?, sizePx: Int = 128): Drawable? {
        val path = iconPath(context, identityKey) ?: return null
        // 落盘用的是相对 filesDir 的路径（shortcut_icons/xxx.png），
        // 必须走 ShortcutIconStorage 自己的解析入口，不能直接 File(path)。
        val bitmap = ShortcutIconStorage.loadBitmap(context, path, sizePx)
        if (bitmap == null) {
            Log.w(TAG, "snapshot file missing key=$identityKey path=$path")
            return null
        }
        Log.i(TAG, "snapshot hit key=$identityKey size=${bitmap.width}x${bitmap.height}")
        return BitmapDrawable(context.resources, bitmap)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
