package com.slideindex.app.overlay.searchpanel

import android.content.Context

/** Syncs pick/search jump queries into the search panel input session + history. */
object SearchPanelQueryBridge {
    fun rememberQuery(context: Context, query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        SearchPanelSessionState.lastTextQuery = trimmed
        com.slideindex.app.overlay.panels.PanelRuntimeHooks.recordSearchHistory?.invoke(context, trimmed)
    }
}
