package com.slideindex.app.overlay.pickresult

import android.content.Context
import androidx.core.content.edit
import com.slideindex.app.settings.SearchEngineConfig

object PickResultImageSharePrefs {
    const val PREFS_NAME = "pick_result_prefs"
    const val KEY_LAST_USED_ENGINE_ID = "last_used_image_engine"

    fun rememberLastUsedEngine(context: Context, engine: SearchEngineConfig) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putString(KEY_LAST_USED_ENGINE_ID, engine.id) }
    }

    fun resolveDisplayEngine(
        engines: List<SearchEngineConfig>,
        context: Context,
    ): SearchEngineConfig? {
        if (engines.isEmpty()) return null
        val lastId = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_USED_ENGINE_ID, null)
        return engines.find { it.id == lastId } ?: engines.first()
    }
}
