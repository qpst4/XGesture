package com.slideindex.app.shortcut.port

import com.slideindex.app.util.ShortcutKind

/**
 * UI metadata wrapper around a [ShortCutItem]. Execution still uses only
 * [ShortCutItem.toIntent] and the root/Shizuku shells.
 */
data class ShortcutPortEntry(
    val item: ShortCutItem,
    val kinds: Set<ShortcutKind>,
)
