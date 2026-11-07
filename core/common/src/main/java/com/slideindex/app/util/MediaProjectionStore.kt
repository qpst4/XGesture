package com.slideindex.app.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.core.content.edit

/** Persists MediaProjection consent so capture can resume without re-prompting (incl. ADB grant). */
object MediaProjectionStore {
    private const val PREFS = "media_projection_store"
    private const val KEY_RESULT_CODE = "result_code"
    private const val KEY_RESULT_DATA = "result_data"

    fun save(context: Context, resultCode: Int, data: Intent) {
        if (resultCode != Activity.RESULT_OK) return
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit {
                putInt(KEY_RESULT_CODE, resultCode)
                putString(KEY_RESULT_DATA, data.toUri(Intent.URI_INTENT_SCHEME))
            }
    }

    fun read(context: Context): StoredProjection? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val resultCode = prefs.getInt(KEY_RESULT_CODE, Activity.RESULT_CANCELED)
        val uri = prefs.getString(KEY_RESULT_DATA, null) ?: return null
        if (resultCode != Activity.RESULT_OK) return null
        return try {
            val data = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)
            StoredProjection(resultCode, data)
        } catch (_: Exception) {
            null
        }
    }

    fun clear(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit { clear() }
    }

    data class StoredProjection(val resultCode: Int, val data: Intent)
}
