package com.slideindex.app.overlay.filebrowser

import android.content.ClipData
import android.graphics.Bitmap
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.view.drawToBitmap
import com.slideindex.app.R
import com.slideindex.app.clipboard.ClipboardDragShareFallback
import com.slideindex.app.overlay.dragdrop.DragFlowSource
import com.slideindex.app.overlay.history.HistoryDragPreview
import com.slideindex.app.overlay.history.HistoryEntryDragHelper
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.withTimeoutOrNull

@Composable
internal fun FileBrowserListContent(
    state: FileBrowserUiState,
    filtered: List<FileBrowserEntry>,
    internalRoot: String,
    listState: LazyListState,
    onEntryClick: (FileBrowserEntry) -> Unit,
    onEntryLongClick: (FileBrowserEntry) -> Unit,
    onSwipeSelect: (FileBrowserEntry) -> Unit = {},
    selectionMode: Boolean = false,
    selectedPaths: Set<String> = emptySet(),
    access: FileBrowserAccessInfo? = null,
    onRetry: () -> Unit,
    onRequestAllFiles: () -> Unit,
    onRequestShizuku: () -> Unit,
    bottomInset: Dp = 0.dp,
) {
    val error = state.error
    when {
        error != null -> FileBrowserErrorState(
            error = error,
            state = state,
            internalRoot = internalRoot,
            onRetry = onRetry,
            onRequestAllFiles = onRequestAllFiles,
            onRequestShizuku = onRequestShizuku,
        )

        state.loading && state.entries.isEmpty() -> FileBrowserLoadingState()

        filtered.isEmpty() -> FileBrowserEmptyState(
            searching = state.query.isNotBlank() || state.entries.isNotEmpty(),
        )

        else -> {
            val haptic = LocalHapticFeedback.current
            val latestFiltered = rememberUpdatedState(filtered)
            val latestOnSwipeSelect = rememberUpdatedState(onSwipeSelect)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .swipeSelect(
                        listState = listState,
                        entries = latestFiltered,
                        onSelect = latestOnSwipeSelect,
                        haptic = haptic,
                    ),
                contentPadding = PaddingValues(bottom = 8.dp + bottomInset),
            ) {
                itemsIndexed(
                    items = filtered,
                    key = { _, entry -> entry.path },
                ) { index, entry ->
                    FileBrowserEntryRow(
                        entry = entry,
                        showDivider = index < filtered.lastIndex,
                        selected = entry.path in selectedPaths,
                        selectionMode = selectionMode,
                        access = access,
                        onClick = { onEntryClick(entry) },
                        onLongClick = { onEntryLongClick(entry) },
                        dragPaths = if (selectionMode && entry.path in selectedPaths) {
                            selectedPaths.toList()
                        } else {
                            listOf(entry.path)
                        },
                    )
                }
            }
        }
    }
}

/**
 * MT 式「滑动勾选」：在行上左/右滑即勾选当前行，继续滑到别的行则把两行之间（含两端）
 * 全部并入选择。只做并集，取消勾选仍靠点击。
 *
 * 手指按下后即使滑出本行，pointerInput 仍会收到事件；这里用 [LazyListState] 的可见项偏移
 * 把手指 Y 映射回行下标。纵向滚动 / 长按拖拽先认领手势时（change.isConsumed）直接放弃，
 * 避免抢走滚动。
 */
private fun Modifier.swipeSelect(
    listState: LazyListState,
    entries: State<List<FileBrowserEntry>>,
    onSelect: State<(FileBrowserEntry) -> Unit>,
    haptic: HapticFeedback,
): Modifier = pointerInput(listState) {
    val touchSlop = viewConfiguration.touchSlop
    val longPressTimeout = viewConfiguration.longPressTimeoutMillis
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var active = false
        var lastIndex = -1
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            val change = event.changes.firstOrNull { it.id == down.id }
                ?: return@awaitEachGesture
            if (!change.pressed) return@awaitEachGesture
            if (!active) {
                // 滚动或长按已经消费了这次手势：不抢。
                if (change.isConsumed) return@awaitEachGesture
                // 长按时间窗之后才动的手，属于长按拖拽（把文件拖出去），不是滑选。
                if (change.uptimeMillis - down.uptimeMillis > longPressTimeout) {
                    return@awaitEachGesture
                }
                val dx = abs(change.position.x - down.position.x)
                val dy = abs(change.position.y - down.position.y)
                if (dy > touchSlop && dy >= dx) return@awaitEachGesture
                if (dx <= touchSlop) continue
                active = true
                lastIndex = listState.indexAtPosition(change.position.y)
                    ?: return@awaitEachGesture
                entries.value.getOrNull(lastIndex)?.let {
                    onSelect.value(it)
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
            change.consume()
            val index = listState.indexAtPosition(change.position.y) ?: continue
            if (index != lastIndex) {
                lastIndex = index
                entries.value.getOrNull(index)?.let {
                    onSelect.value(it)
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
        }
    }
}

/**
 * 手指 Y（列表容器坐标）→ 可见行下标。
 *
 * `LazyListItemInfo.offset` 不含 contentPadding（Compose 到 place 阶段才加 visualOffset），
 * 所以先加 `viewportStartOffset` 换到同一套坐标里；本列表只有底部 padding，通常等价于 y。
 */
private fun LazyListState.indexAtPosition(y: Float): Int? {
    val info = layoutInfo
    val adjusted = y + info.viewportStartOffset
    return info.visibleItemsInfo
        .firstOrNull { adjusted >= it.offset && adjusted < it.offset + it.size }
        ?.index
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileBrowserEntryRow(
    entry: FileBrowserEntry,
    showDivider: Boolean,
    selected: Boolean,
    selectionMode: Boolean,
    access: FileBrowserAccessInfo?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    dragPaths: List<String>,
) {
    val fileKind = FileKind.from(entry.name, entry.kind)
    val isGif = fileKind == FileKind.IMAGE && entry.name.endsWith(".gif", ignoreCase = true)
    val isFolder = entry.kind == FileEntryKind.DIRECTORY
    val context = LocalContext.current
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    var rowBounds by remember { mutableStateOf(Rect.Zero) }
    val dragClip = remember(dragPaths) {
        val uris = dragPaths.mapNotNull { path ->
            runCatching {
                val file = File(path)
                if (file.isFile && file.canRead()) {
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file,
                    )
                } else {
                    null
                }
            }.getOrNull()
        }
        if (uris.isEmpty()) {
            ClipData.newPlainText(entry.name, entry.path)
        } else {
            ClipData.newUri(context.contentResolver, entry.name, uris.first()).apply {
                uris.drop(1).forEach { uri -> addItem(ClipData.Item(uri)) }
            }
        }
    }
    val latestOnClick by rememberUpdatedState(onClick)
    val latestOnLongClick by rememberUpdatedState(onLongClick)
    val latestDragPaths by rememberUpdatedState(dragPaths)
    val latestDragClip by rememberUpdatedState(dragClip)
    val tint = if (isFolder) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    var thumbnail by remember(entry.path, entry.modifiedEpochSeconds) {
        mutableStateOf<Bitmap?>(null)
    }
    LaunchedEffect(entry.path, entry.modifiedEpochSeconds, fileKind) {
        if (fileKind == FileKind.IMAGE ||
            fileKind == FileKind.VIDEO ||
            fileKind == FileKind.APK ||
            fileKind == FileKind.PDF
        ) {
            thumbnail = FileBrowserThumbnailLoader.load(
                context = context,
                path = entry.path,
                modifiedEpochSeconds = entry.modifiedEpochSeconds,
                kind = fileKind,
                access = access,
            )
        }
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { rowBounds = it.boundsInRoot() }
                .background(
                    if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    } else {
                        Color.Transparent
                    },
                )
                .pointerInput(entry.path) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var tap = false
                        var scrolled = false
                        val timedOut = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val change = event.changes.firstOrNull { it.id == down.id }
                                    ?: return@withTimeoutOrNull false
                                if (!change.pressed) {
                                    tap = true
                                    return@withTimeoutOrNull false
                                }
                                val moved = (change.position - down.position).getDistance()
                                if (moved > viewConfiguration.touchSlop || change.isConsumed) {
                                    scrolled = true
                                    return@withTimeoutOrNull false
                                }
                            }
                        } == null
                        if (!timedOut) {
                            if (tap && !scrolled) latestOnClick()
                            return@awaitEachGesture
                        }
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val rowSnapshot = runCatching {
                            val full = view.drawToBitmap()
                            val left = rowBounds.left.toInt().coerceIn(0, full.width - 1)
                            val top = rowBounds.top.toInt().coerceIn(0, full.height - 1)
                            val width = rowBounds.width.toInt()
                                .coerceIn(1, full.width - left)
                            val height = rowBounds.height.toInt()
                                .coerceIn(1, full.height - top)
                            Bitmap.createBitmap(full, left, top, width, height)
                        }.getOrNull()
                        val lockedPaths = latestDragPaths
                        val lockedClip = latestDragClip
                        latestOnLongClick()
                        // 菜单出现后不松手，继续按住到第二个阈值才进入拖拽。
                        val released = withTimeoutOrNull(420L) {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                    ?: return@withTimeoutOrNull true
                                if (!change.pressed) return@withTimeoutOrNull true
                                change.consume()
                            }
                            @Suppress("UNREACHABLE_CODE")
                            true
                        }
                        if (released != null) return@awaitEachGesture
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (rowSnapshot != null) {
                            // 恢复 = 把文件浏览器浮窗放回来；关掉 = 关掉整个浏览器。
                            val dragFlowSource = DragFlowSource(
                                onRestore = { FileBrowserOverlayWindow.resumeFromFileDrag() },
                                onDiscard = { FileBrowserOverlayWindow.dismiss() },
                            )
                            val started = HistoryEntryDragHelper.startDrag(
                                view = view,
                                clipData = lockedClip,
                                preview = HistoryDragPreview(),
                                dragShadow = FileBrowserDragShadow(
                                    source = rowSnapshot,
                                    touchX = down.position.x,
                                    touchY = down.position.y,
                                    count = lockedPaths.size,
                                ),
                                onDragStart = { FileBrowserOverlayWindow.suspendForFileDrag() },
                                onDragEnd = { FileBrowserOverlayWindow.resumeFromFileDrag() },
                                onDropRejected = {
                                    if (ClipboardDragShareFallback.hasShareableContent(lockedClip) &&
                                        !ClipboardDragShareFallback.shareToForegroundHost(context, lockedClip)
                                    ) {
                                        Toast.makeText(
                                            context,
                                            R.string.history_drag_unsupported,
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                },
                                dragFlowSource = dragFlowSource,
                            )
                            if (!started &&
                                !ClipboardDragShareFallback.shareToForegroundHost(context, lockedClip)
                            ) {
                                Toast.makeText(
                                    context,
                                    R.string.history_drag_unsupported,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                            Log.d(
                                "FileBrowserDrag",
                                "start ${entry.path} started=$started count=${lockedPaths.size} paths=$lockedPaths",
                            )
                        }
                    }
                }
                .heightIn(min = 60.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                val bitmap = thumbnail
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = iconFor(fileKind),
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(22.dp),
                    )
                }
                if (isGif) {
                    Text(
                        text = "GIF",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(2.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f))
                            .padding(horizontal = 3.dp, vertical = 1.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitleFor(entry),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (selectionMode) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    },
                    modifier = Modifier.size(22.dp),
                )
            } else if (entry.kind == FileEntryKind.DIRECTORY || entry.kind == FileEntryKind.SYMLINK) {
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 68.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
            )
        }
    }
}

@Composable
private fun subtitleFor(entry: FileBrowserEntry): String = when (entry.kind) {
    FileEntryKind.DIRECTORY -> stringResource(R.string.file_browser_kind_folder)
    FileEntryKind.SYMLINK -> stringResource(R.string.file_browser_kind_link)
    FileEntryKind.FILE, FileEntryKind.OTHER ->
        "${FileBrowserFormat.size(entry.sizeBytes)} · " +
            FileBrowserFormat.date(entry.modifiedEpochSeconds)
}

@Composable
private fun FileBrowserEmptyState(searching: Boolean) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = if (searching) Icons.Outlined.SearchOff else Icons.Outlined.FolderOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.size(44.dp),
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(
                    if (searching) {
                        R.string.file_browser_empty_search
                    } else {
                        R.string.file_browser_empty_dir
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun FileBrowserLoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(modifier = Modifier.size(30.dp), strokeWidth = 2.5.dp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.file_browser_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FileBrowserErrorState(
    error: FileBrowserError,
    state: FileBrowserUiState,
    internalRoot: String,
    onRetry: () -> Unit,
    onRequestAllFiles: () -> Unit,
    onRequestShizuku: () -> Unit,
) {
    val access = state.access
    val isAndroidData = FileBrowserPaths.isAndroidDataOrObb(state.path, internalRoot)
    val isExternal = FileBrowserPaths.isExternal(state.path, internalRoot)
    val isRootOnly = FileBrowserPaths.isRootOnly(state.path)
    val canOfferAllFiles = access?.allFiles != true && isExternal && !isAndroidData
    val canOfferShizuku = access?.rootMode == false &&
        access.privileged == false &&
        !isRootOnly

    val titleRes: Int
    val messageRes: Int?
    when (error) {
        FileBrowserError.PermissionDenied -> {
            titleRes = R.string.file_browser_error_permission
            messageRes = R.string.file_browser_error_permission_hint
        }
        FileBrowserError.NeedsRoot -> {
            titleRes = R.string.file_browser_error_needs_root
            messageRes = R.string.file_browser_error_needs_root_hint
        }
        FileBrowserError.PrivilegeUnavailable -> {
            titleRes = R.string.file_browser_error_privilege
            messageRes = when {
                access?.rootMode == true -> R.string.file_browser_error_privilege_root_hint
                isRootOnly -> R.string.file_browser_error_needs_root_hint
                else -> R.string.file_browser_error_privilege_shizuku_hint
            }
        }
        FileBrowserError.NotFound -> {
            titleRes = R.string.file_browser_error_not_found
            messageRes = null
        }
        FileBrowserError.NotDirectory -> {
            titleRes = R.string.file_browser_error_not_dir
            messageRes = null
        }
        FileBrowserError.Timeout -> {
            titleRes = R.string.file_browser_error_timeout
            messageRes = null
        }
        is FileBrowserError.Unknown -> {
            titleRes = R.string.file_browser_error_unknown
            messageRes = null
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Outlined.FolderOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                modifier = Modifier.size(44.dp),
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            if (messageRes != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(messageRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            if (error is FileBrowserError.Unknown && !error.detail.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = error.detail.take(160),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            if (canOfferAllFiles) {
                Button(
                    onClick = onRequestAllFiles,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.file_browser_grant_all_files))
                }
            }
            if (canOfferShizuku &&
                (error == FileBrowserError.PrivilegeUnavailable ||
                    isAndroidData ||
                    state.path == "/")
            ) {
                if (canOfferAllFiles) Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onRequestShizuku,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.file_browser_grant_shizuku))
                }
            }
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.file_browser_retry))
            }
        }
    }
}

/**
 * 只读访问受限时的轻量横幅。真正失败时由错误卡给动作，这里只在“列出来了但不完整”时提示。
 */
@Composable
internal fun FileBrowserLimitedBanner(
    state: FileBrowserUiState,
    internalRoot: String,
    onRequestAllFiles: () -> Unit,
    onRequestShizuku: () -> Unit,
) {
    if (!state.limited) return
    val access = state.access
    val isAndroidData = FileBrowserPaths.isAndroidDataOrObb(state.path, internalRoot)
    val canOfferAllFiles = access?.allFiles != true && !isAndroidData
    val canOfferShizuku = access?.rootMode == false && access.privileged == false

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.file_browser_banner_limited),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (canOfferAllFiles) {
            TextButton(onClick = onRequestAllFiles) {
                Text(stringResource(R.string.file_browser_grant_all_files))
            }
        } else if (canOfferShizuku) {
            TextButton(onClick = onRequestShizuku) {
                Text(stringResource(R.string.file_browser_grant_shizuku))
            }
        }
    }
}

private fun iconFor(kind: FileKind): ImageVector = when (kind) {
    FileKind.FOLDER -> Icons.Outlined.Folder
    FileKind.SYMLINK -> Icons.Outlined.Link
    FileKind.IMAGE -> Icons.Outlined.Image
    FileKind.VIDEO -> Icons.Outlined.Movie
    FileKind.AUDIO -> Icons.Outlined.MusicNote
    FileKind.APK -> Icons.Outlined.Android
    FileKind.ARCHIVE -> Icons.Outlined.FolderZip
    FileKind.PDF -> Icons.Outlined.PictureAsPdf
    FileKind.TEXT -> Icons.Outlined.Description
    FileKind.OTHER -> Icons.AutoMirrored.Outlined.InsertDriveFile
}
