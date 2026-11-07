package com.slideindex.app.util

/*
 * Portions derived from SideGesture (https://github.com/aaronzzx/gulugulu)
 * Licensed under Apache-2.0. Modified for com.slideindex.app.
 */

data class LauncherShortcutInfo(
    val packageName: String,
    val className: String,
    val label: String,
    val shortcuts: List<Entry> = emptyList(),
) {
    val qualifiedName: String get() = "$packageName/$className"

    data class Entry(
        val packageName: String,
        val className: String,
        val intents: List<String>,
        val label: String,
        val iconRes: Int = 0,
    ) {
        val qualifiedName: String get() = "$packageName/$className"

        val qualifiedNameWithIntents: String get() = "$packageName/$className(${intents.joinToString()})"
    }
}
