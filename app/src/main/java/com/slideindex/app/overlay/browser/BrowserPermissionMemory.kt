package com.slideindex.app.overlay.browser

import android.content.Context

/**
 * 站点级权限记忆：把用户对某个域名（相机/麦克风）的选择记下来，
 * 下次同一站点直接复用，不再每次弹窗。
 */
internal object BrowserPermissionMemory {

    private const val PREFS = "browser_permissions"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(context: Context, host: String, kind: String): Boolean? {
        if (host.isBlank()) return null
        val raw = prefs(context).getString("$host|$kind", null) ?: return null
        return raw == "1"
    }

    fun set(context: Context, host: String, kind: String, granted: Boolean) {
        if (host.isBlank()) return
        prefs(context).edit().putString("$host|$kind", if (granted) "1" else "0").apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
