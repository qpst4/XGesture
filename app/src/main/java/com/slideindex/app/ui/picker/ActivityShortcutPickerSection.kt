package com.slideindex.app.ui.picker

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.automirrored.filled.Shortcut
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import com.slideindex.app.activity.ActivityShortcut
import com.slideindex.app.activity.ActivityShortcutKind
import com.slideindex.app.activity.subtitleDetail
import com.slideindex.app.activity.toLaunchShortcut
import com.slideindex.app.activity.toQuickLauncherItem
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.ui.Md3PickerIconLeading
import com.slideindex.app.ui.Md3PickerManagedShortcutLeading
import com.slideindex.app.ui.Md3PickerListRow
import com.slideindex.app.ui.Md3PickerSectionHeader
import com.slideindex.app.ui.PickerTrailingMode

fun LazyListScope.activityShortcutPickerRadioSection(
    activityShortcuts: List<ActivityShortcut>,
    current: GestureAction,
    onSelect: (GestureAction) -> Unit,
    onBrowse: () -> Unit,
    showWhenSearchEmptyOnly: Boolean = true,
    searchQuery: String = "",
) {
    if (showWhenSearchEmptyOnly && searchQuery.isNotBlank() && activityShortcuts.isEmpty()) return
    item(key = "activity-shortcuts-header") {
        Md3PickerSectionHeader(stringResource(R.string.activity_shortcut_picker_section))
    }
    val segmentCount = activityShortcuts.size + 1
    items(
        count = activityShortcuts.size,
        key = { activityShortcuts[it].id },
    ) { index ->
        val shortcut = activityShortcuts[index]
        val action = shortcut.toLaunchShortcut()
        val selected = current is GestureAction.LaunchShortcut && current.payloadKey == action.payloadKey
        Md3PickerListRow(
            segmentIndex = index,
            segmentCount = segmentCount,
            title = shortcut.label,
            subtitle = shortcut.subtitleDetail(),
            selected = selected,
            onClick = { onSelect(action) },
            leadingContent = {
                Md3PickerManagedShortcutLeading(shortcut = shortcut, selected = selected)
            },
            trailingMode = PickerTrailingMode.Radio,
        )
    }
    item(key = "activity-shortcuts-browse") {
        Md3PickerListRow(
            segmentIndex = activityShortcuts.size,
            segmentCount = segmentCount,
            title = stringResource(R.string.activity_shortcut_browse),
            subtitle = stringResource(R.string.activity_shortcut_browse_hint),
            selected = false,
            onClick = onBrowse,
            leadingContent = {
                Md3PickerIconLeading(
                    icon = Icons.AutoMirrored.Filled.Shortcut,
                    selected = false,
                )
            },
            trailingMode = PickerTrailingMode.Icon,
            trailingIcon = Icons.AutoMirrored.Filled.Shortcut,
            trailingIconDescription = stringResource(R.string.activity_shortcut_browse),
        )
    }
}

fun LazyListScope.activityShortcutPickerToggleSection(
    activityShortcuts: List<ActivityShortcut>,
    configuredShortcutKeys: Set<String>,
    onToggle: (QuickLauncherItem, Boolean) -> Unit,
    onBrowse: () -> Unit,
    showWhenSearchEmptyOnly: Boolean = true,
    searchQuery: String = "",
    singleSelect: Boolean = false,
) {
    if (showWhenSearchEmptyOnly && searchQuery.isNotBlank() && activityShortcuts.isEmpty()) return
    item(key = "activity-shortcuts-header") {
        Md3PickerSectionHeader(stringResource(R.string.activity_shortcut_picker_section))
    }
    val segmentCount = activityShortcuts.size + 1
    items(
        count = activityShortcuts.size,
        key = { activityShortcuts[it].id },
    ) { index ->
        val shortcut = activityShortcuts[index]
        val item = shortcut.toQuickLauncherItem()
        val key = QuickLauncherItemCodec.shortcutItemKey(item).orEmpty()
        val added = key.isNotBlank() && key in configuredShortcutKeys
        Md3PickerListRow(
            segmentIndex = index,
            segmentCount = segmentCount,
            title = shortcut.label,
            subtitle = shortcut.subtitleDetail(),
            selected = added,
            onClick = { onToggle(item, if (singleSelect) false else added) },
            leadingContent = {
                Md3PickerManagedShortcutLeading(shortcut = shortcut, selected = added)
            },
            trailingMode = if (singleSelect) PickerTrailingMode.Radio else PickerTrailingMode.Toggle,
            onTrailingClick = if (singleSelect) null else { { onToggle(item, added) } },
        )
    }
    item(key = "activity-shortcuts-browse") {
        Md3PickerListRow(
            segmentIndex = activityShortcuts.size,
            segmentCount = segmentCount,
            title = stringResource(R.string.activity_shortcut_browse),
            subtitle = stringResource(R.string.activity_shortcut_browse_hint),
            selected = false,
            onClick = onBrowse,
            leadingContent = {
                Md3PickerIconLeading(
                    icon = Icons.AutoMirrored.Filled.Shortcut,
                    selected = false,
                )
            },
            trailingMode = PickerTrailingMode.Icon,
            trailingIcon = Icons.AutoMirrored.Filled.Shortcut,
            trailingIconDescription = stringResource(R.string.activity_shortcut_browse),
        )
    }
}
