package com.slideindex.app.shortcut.port

import android.content.Intent
import com.slideindex.app.util.ShortcutKind

/** UI metadata wrapper around a [ShortCutItem], with the full launch Intent. */
data class ShortcutPortEntry(
    val item: ShortCutItem,
    val kinds: Set<ShortcutKind>,
    val intent: Intent,
)
