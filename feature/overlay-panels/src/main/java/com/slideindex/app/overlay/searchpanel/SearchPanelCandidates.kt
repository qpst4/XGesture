package com.slideindex.app.overlay.searchpanel

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slideindex.app.overlay.panels.R
import com.slideindex.app.overlay.pickresult.PickResultUrl
import com.slideindex.app.search.contacts.ContactSearchEntry
import com.slideindex.app.search.files.DeviceFileEntry
import com.slideindex.app.search.settings.SystemSettingsSearchEntry

@Composable
fun SearchPanelContactCandidates(
    contacts: List<ContactSearchEntry>,
    onLaunchContact: (ContactSearchEntry, longPressTriggered: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    longPressEnabled: Boolean = false,
) {
    if (contacts.isEmpty()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        contacts.forEach { contact ->
            val label = "${contact.name} · ${contact.formattedPhone}"
            SearchPanelCandidateChip(
                label = label,
                leading = {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                longPressEnabled = longPressEnabled,
                onClick = { onLaunchContact(contact, false) },
                onLongClick = { onLaunchContact(contact, true) },
            )
        }
    }
}

@Composable
fun SearchPanelContactPermissionPrompt(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchPanelCandidateChip(
            label = stringResource(R.string.search_panel_contact_permission_prompt),
            leading = {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            longPressEnabled = false,
            onClick = onRequestPermission,
            onLongClick = {},
        )
    }
}

@Composable
fun SearchPanelFileCandidates(
    files: List<DeviceFileEntry>,
    onOpenFile: (DeviceFileEntry, longPressTriggered: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    longPressEnabled: Boolean = false,
) {
    if (files.isEmpty()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        files.forEach { file ->
            val label = file.relativePath?.takeIf { it.isNotBlank() }?.let { path ->
                "${file.displayName} · ${path.trimEnd('/')}"
            } ?: file.displayName
            SearchPanelCandidateChip(
                label = label,
                leading = {
                    Icon(
                        imageVector = if (file.isDirectory) {
                            Icons.Default.Folder
                        } else {
                            Icons.AutoMirrored.Filled.InsertDriveFile
                        },
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                longPressEnabled = longPressEnabled,
                onClick = { onOpenFile(file, false) },
                onLongClick = { onOpenFile(file, true) },
            )
        }
    }
}

@Composable
fun SearchPanelFilePermissionPrompt(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchPanelCandidateChip(
            label = stringResource(R.string.search_panel_file_permission_prompt),
            leading = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            longPressEnabled = false,
            onClick = onRequestPermission,
            onLongClick = {},
        )
    }
}

@Composable
fun SearchPanelLinkCandidates(
    urls: List<String>,
    onOpenUrl: (url: String, longPressTriggered: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    longPressEnabled: Boolean = false,
) {
    if (urls.isEmpty()) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val hosts = remember(urls) { PickResultUrl.linkDisplayLabels(urls) }
        urls.forEachIndexed { index, url ->
            val host = hosts[index]
            val label = stringResource(R.string.search_panel_open_link_host, host)
            SearchPanelCandidateChip(
                label = label,
                leading = {
                    Icon(
                        imageVector = Icons.Outlined.Link,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                longPressEnabled = longPressEnabled,
                onClick = { onOpenUrl(url, false) },
                onLongClick = { onOpenUrl(url, true) },
            )
        }
    }
}

@Composable
fun SearchPanelSettingsCandidates(
    entries: List<SystemSettingsSearchEntry>,
    onLaunchEntry: (SystemSettingsSearchEntry, longPressTriggered: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    longPressEnabled: Boolean = false,
) {
    if (entries.isEmpty()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        entries.forEach { entry ->
            val label = entry.subtitle?.let { "${entry.title} · $it" } ?: entry.title
            SearchPanelCandidateChip(
                label = label,
                leading = {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                longPressEnabled = longPressEnabled,
                onClick = { onLaunchEntry(entry, false) },
                onLongClick = { onLaunchEntry(entry, true) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchPanelCandidateChip(
    label: String,
    leading: @Composable () -> Unit,
    longPressEnabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val clickModifier = if (longPressEnabled) {
        Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick,
        )
    } else {
        Modifier.combinedClickable(onClick = onClick)
    }
    Row(
        modifier = Modifier
            .then(clickModifier)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
