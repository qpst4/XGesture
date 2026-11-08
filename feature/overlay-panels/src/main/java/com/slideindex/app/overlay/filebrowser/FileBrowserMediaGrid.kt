package com.slideindex.app.overlay.filebrowser

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slideindex.app.overlay.panels.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * FV 式媒体网格：平铺、按日期分组、滚动分页、只读。
 *
 * 这是媒体模式，不是文件列表；点击交给调用方（图片预览或系统打开）。
 */
@Composable
fun FileBrowserMediaGrid(
    category: FileBrowserMediaCategory,
    items: List<FileBrowserMediaItem>,
    query: String,
    loading: Boolean,
    hasMore: Boolean,
    totalCount: Int?,
    totalBytes: Long?,
    error: FileBrowserError?,
    mediaPermissionAvailable: Boolean,
    selectedIds: Set<Long>,
    onLoadMore: () -> Unit,
    onOpen: (FileBrowserMediaItem) -> Unit,
    onLongClick: (FileBrowserMediaItem) -> Unit,
    onRequestMediaPermission: () -> Unit,
    onRequestAllFiles: () -> Unit,
    onRetry: () -> Unit,
) {
    val visible = remember(items, query) {
        val needle = query.trim()
        if (needle.isEmpty()) {
            items
        } else {
            items.filter { it.name.contains(needle, ignoreCase = true) }
        }
    }
    val gridState = rememberLazyGridState()
    val sections = remember(visible) {
        val zone = ZoneId.systemDefault()
        visible.groupBy { item ->
            Instant.ofEpochSecond(item.modifiedEpochSeconds)
                .atZone(zone)
                .toLocalDate()
        }
    }

    LaunchedEffect(gridState, visible.size, hasMore, loading) {
        snapshotFlow {
            gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        }
            .distinctUntilChanged()
            .collect { last ->
                if (hasMore &&
                    !loading &&
                    last >= gridState.layoutInfo.totalItemsCount - 8
                ) {
                    onLoadMore()
                }
            }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(category.labelRes),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.weight(1f))
            val count = if (query.isNotBlank()) visible.size else totalCount ?: visible.size
            val size = totalBytes?.let(FileBrowserFormat::size)
            Text(
                text = if (size == null) {
                    stringResource(R.string.file_browser_item_count, count)
                } else {
                    stringResource(R.string.file_browser_media_count_size, count, size)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        when {
            error != null -> FileBrowserMediaError(
                error = error,
                mediaPermissionAvailable = mediaPermissionAvailable,
                onRequestMediaPermission = onRequestMediaPermission,
                onRequestAllFiles = onRequestAllFiles,
                onRetry = onRetry,
            )

            loading && visible.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(30.dp), strokeWidth = 2.5.dp)
            }

            visible.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.file_browser_media_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(104.dp),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    bottom = 12.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                sections.forEach { (date, sectionItems) ->
                    item(
                        key = "date-$date",
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        Text(
                            text = sectionTitle(date),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
                        )
                    }
                    items(sectionItems, key = { "media-${it.id}" }) { item ->
                        FileBrowserMediaTile(
                            item = item,
                            selected = item.id in selectedIds,
                            showName = category != FileBrowserMediaCategory.PICTURES,
                            onClick = { onOpen(item) },
                            onLongClick = { onLongClick(item) },
                        )
                    }
                }
                if (loading || hasMore) {
                    item(
                        key = "loading-more",
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                TextButton(onClick = onLoadMore) {
                                    Text(stringResource(R.string.file_browser_media_more))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileBrowserMediaTile(
    item: FileBrowserMediaItem,
    selected: Boolean,
    showName: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var bitmap by remember(item.uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(item.uri) {
        bitmap = FileBrowserMediaThumbnailLoader.load(
            context = context,
            uri = item.uri,
        )
    }
    val isVideo = item.mimeType?.startsWith("video/") == true
    val isGif = item.mimeType.equals("image/gif", ignoreCase = true) ||
        item.name.endsWith(".gif", ignoreCase = true)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(9.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
        ) {
            val current = bitmap
            if (current != null) {
                Image(
                    bitmap = current.asImageBitmap(),
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = mediaIcon(item.mimeType),
                    contentDescription = item.name,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
            }
            if (isVideo) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.82f))
                        .padding(3.dp),
                )
            }
            if (isGif) {
                Text(
                    text = "GIF",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.86f))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
                )
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(3.dp),
                )
            }
        }
        if (showName) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun FileBrowserMediaError(
    error: FileBrowserError,
    mediaPermissionAvailable: Boolean,
    onRequestMediaPermission: () -> Unit,
    onRequestAllFiles: () -> Unit,
    onRetry: () -> Unit,
) {
    val permission = error == FileBrowserError.PermissionDenied
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Outlined.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                modifier = Modifier.size(42.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(
                    if (permission) {
                        R.string.file_browser_media_permission_title
                    } else {
                        R.string.file_browser_error_unknown
                    },
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            if (permission) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.file_browser_media_permission_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (permission && mediaPermissionAvailable) {
                Button(onClick = onRequestMediaPermission, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.file_browser_media_grant))
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
            if (permission) {
                OutlinedButton(onClick = onRequestAllFiles, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.file_browser_grant_all_files))
                }
            }
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.file_browser_retry))
            }
        }
    }
}

@Composable
private fun sectionTitle(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> stringResource(R.string.file_browser_media_today)
        today.minusDays(1) -> stringResource(R.string.file_browser_media_yesterday)
        else -> date.toString()
    }
}

private fun mediaIcon(mime: String?): androidx.compose.ui.graphics.vector.ImageVector = when {
    mime == null -> Icons.AutoMirrored.Outlined.InsertDriveFile
    mime.startsWith("image/") -> Icons.Outlined.Image
    mime.startsWith("video/") -> Icons.Outlined.Movie
    mime.startsWith("audio/") -> Icons.Outlined.MusicNote
    mime.startsWith("text/") || mime.contains("document") -> Icons.Outlined.Description
    else -> Icons.AutoMirrored.Outlined.InsertDriveFile
}
