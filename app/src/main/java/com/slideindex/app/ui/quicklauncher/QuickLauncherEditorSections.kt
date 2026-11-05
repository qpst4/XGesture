package com.slideindex.app.ui.quicklauncher

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Shortcut
import androidx.compose.runtime.Composable
import com.slideindex.app.overlay.TaskSwitcherMenuItem
import com.slideindex.app.ui.Md3PickerIconLeading
import com.slideindex.app.ui.Md3PickerListRow
import com.slideindex.app.ui.PickerTrailingMode
import com.slideindex.app.ui.shortcutKindChip

internal enum class QuickLauncherEditorAddTab { ACTIONS, APPS, SHORTCUTS }

@Composable
internal fun ShortcutCatalogRow(
    shortcut: TaskSwitcherMenuItem,
    segmentIndex: Int,
    segmentCount: Int,
    added: Boolean,
    onToggle: () -> Unit,
) {
    Md3PickerListRow(
        segmentIndex = segmentIndex,
        segmentCount = segmentCount,
        title = shortcut.label,
        subtitle = shortcut.targetComponent?.takeIf { it.isNotBlank() },
        selected = added,
        onClick = onToggle,
        leadingContent = {
            Md3PickerIconLeading(
                icon = Icons.AutoMirrored.Outlined.Shortcut,
                selected = added,
            )
        },
        trailingMode = PickerTrailingMode.Toggle,
        onTrailingClick = onToggle,
        trailingChip = shortcutKindChip(shortcut.kind),
    )
}
