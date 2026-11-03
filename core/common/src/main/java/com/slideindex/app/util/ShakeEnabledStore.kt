package com.slideindex.app.util

import android.content.Context

/**
 * Synchronous mirror of [com.slideindex.app.settings.AppSettings.shakeGestureSettings.enabled]
 * for the XShake QS tile.
 *
 * SystemUI can bind the tile service while the process is cold, and DataStore's first read can
 * take seconds. The tile needs a first-frame answer and a synchronous toggle baseline, so this
 * mirror is written with [android.content.SharedPreferences.Editor.commit] on every change.
 * [com.slideindex.app.settings.SettingsRepository] keeps it fresh from the settings flow.
 */
object ShakeEnabledStore {
    private const val PREFS_NAME = "shake_enabled_mirror"
    private const val KEY_SHAKE_ENABLED = "shake_enabled"

    @Volatile
    private var memoryCache: Boolean? = null

    fun read(context: Context): Boolean {
        memoryCache?.let { return it }
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_SHAKE_ENABLED)) {
            return false
        }
        return prefs.getBoolean(KEY_SHAKE_ENABLED, false).also { memoryCache = it }
    }

    fun hasPersistedValue(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .contains(KEY_SHAKE_ENABLED)

    fun write(context: Context, enabled: Boolean) {
        memoryCache = enabled
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SHAKE_ENABLED, enabled)
            .commit()
    }
}
