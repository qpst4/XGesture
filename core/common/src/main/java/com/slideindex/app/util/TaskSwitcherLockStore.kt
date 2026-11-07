package com.slideindex.app.util

import android.content.Context
import androidx.core.content.edit

/** Persists task-switcher lock state locally (skip on close-all / single dismiss). */
object TaskSwitcherLockStore {

    private const val PREFS_NAME = "task_switcher_locks"
    private const val KEY_LOCKED = "locked_packages"

    fun lockedPackages(context: Context): Set<String> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_LOCKED, emptySet())?.toSet() ?: emptySet()
    }

    fun isLocked(context: Context, packageName: String): Boolean =
        packageName.isNotBlank() && packageName in lockedPackages(context)

    fun setLocked(context: Context, packageName: String, locked: Boolean) {
        if (packageName.isBlank()) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_LOCKED, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (locked) current.add(packageName) else current.remove(packageName)
        prefs.edit { putStringSet(KEY_LOCKED, HashSet(current)) }
    }
}
