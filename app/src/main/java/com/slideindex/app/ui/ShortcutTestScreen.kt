package com.slideindex.app.ui

import androidx.compose.runtime.Composable
import com.slideindex.app.activity.ActivityShortcut

@Composable
fun ShortcutTestScreen(
    shortcuts: List<ActivityShortcut> = emptyList(),
    onSaveShortcuts: (List<ActivityShortcut>) -> Unit = {},
    onBack: () -> Unit,
) {
    ShortcutPortPage(
        savedShortcuts = shortcuts,
        onSaveShortcuts = onSaveShortcuts,
        onBack = onBack,
    )
}

