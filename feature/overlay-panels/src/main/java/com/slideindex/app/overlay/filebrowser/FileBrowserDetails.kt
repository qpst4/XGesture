package com.slideindex.app.overlay.filebrowser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slideindex.app.overlay.panels.R

@Composable
fun FileBrowserDetailsOverlay(
    entry: FileBrowserEntry,
    onCopy: () -> Unit,
    onOpenWith: (() -> Unit)?,
    openWithLabel: String?,
    openWithEnabled: Boolean,
    onOpenImage: (() -> Unit)?,
    onRename: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onClose: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val detailsMaxHeight = maxHeight * 0.94f
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose,
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = detailsMaxHeight)
                    .shadow(18.dp, RoundedCornerShape(24.dp), clip = false),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.file_browser_details_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onClose) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.file_browser_close),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    DetailRow(
                        label = stringResource(R.string.file_browser_detail_name),
                        value = entry.name,
                    )
                    DetailRow(
                        label = stringResource(R.string.file_browser_detail_path),
                        value = entry.path,
                        selectable = false,
                        onClick = onCopy,
                    )
                    DetailRow(
                        label = stringResource(R.string.file_browser_detail_size),
                        value = if (entry.kind == FileEntryKind.DIRECTORY) {
                            "—"
                        } else {
                            FileBrowserFormat.size(entry.sizeBytes)
                        },
                    )
                    DetailRow(
                        label = stringResource(R.string.file_browser_detail_modified),
                        value = FileBrowserFormat.date(entry.modifiedEpochSeconds),
                    )
                    DetailRow(
                        label = stringResource(R.string.file_browser_detail_type),
                        value = typeLabel(entry),
                    )
                    DetailRow(
                        label = stringResource(R.string.file_browser_detail_permissions),
                        value = entry.permissions ?: "—",
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    if (onOpenImage != null || onOpenWith != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (onOpenImage != null) {
                                Button(
                                    onClick = onOpenImage,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(stringResource(R.string.file_browser_image_open))
                                }
                            }
                            if (onOpenWith != null) {
                                OutlinedButton(
                                    onClick = onOpenWith,
                                    enabled = openWithEnabled,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(
                                        text = openWithLabel
                                            ?: stringResource(R.string.file_browser_open_with),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (onRename != null) {
                            OutlinedButton(
                                onClick = onRename,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.file_browser_rename))
                            }
                        }
                        if (onDelete != null) {
                            TextButton(
                                onClick = onDelete,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = stringResource(R.string.file_browser_delete),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun typeLabel(entry: FileBrowserEntry): String = when (entry.kind) {
    FileEntryKind.DIRECTORY -> stringResource(R.string.file_browser_kind_folder)
    FileEntryKind.SYMLINK -> stringResource(R.string.file_browser_kind_link)
    FileEntryKind.FILE, FileEntryKind.OTHER -> stringResource(R.string.file_browser_kind_file)
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    selectable: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp),
        )
        if (selectable) {
            SelectionContainer(modifier = Modifier.weight(1f)) {
                DetailValue(value)
            }
        } else {
            Box(modifier = Modifier.weight(1f)) {
                DetailValue(value)
            }
        }
    }
}

@Composable
private fun DetailValue(value: String) {
    Text(
        text = value,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 4,
        overflow = TextOverflow.Ellipsis,
    )
}
