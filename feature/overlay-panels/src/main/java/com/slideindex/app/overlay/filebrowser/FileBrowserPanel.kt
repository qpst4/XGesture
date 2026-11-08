package com.slideindex.app.overlay.filebrowser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.slideindex.app.R
import com.slideindex.app.overlay.LocalFrostedGlassBackdrop
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.ocr.OcrDependencyAccess
import com.slideindex.app.ocr.OcrEngines
import com.slideindex.app.ocr.OcrRecognizeResult
import com.slideindex.app.overlay.searchpanel.SearchPanelOverlayWindow
import com.slideindex.app.overlay.searchpanel.SearchPanelSessionState
import com.slideindex.app.stash.StashCoordinator
import com.slideindex.app.ui.miuix.MiuixSearchField
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import com.slideindex.app.ui.theme.LocalAppDarkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BreadcrumbBar
import top.yukonga.miuix.kmp.basic.BreadcrumbBarDefaults
import top.yukonga.miuix.kmp.basic.BreadcrumbItem
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 文件浏览器面板内容（浮窗内）。
 *
 * 纯 UI：目录读取/权限判断全部在 [FileBrowserController] 里；这里只负责列表、面包屑、
 * 搜索与详情，并通过回调把“授权/关闭”交给宿主 [FileBrowserOverlayWindow]。
 */
@Composable
internal fun FileBrowserPanel(
    visible: Boolean,
    controller: FileBrowserController,
    onDismiss: () -> Unit,
    onRequestAllFiles: () -> Unit,
    onRequestShizuku: () -> Unit,
    onRequestMediaPermission: () -> Unit,
) {
    val state = controller.state
    val mediaCategory = state.mediaCategory
    val internalRoot = controller.internalRoot
    val filtered = remember(state.entries, state.query, state.showHidden, state.sort) {
        FileBrowserSort.apply(
            entries = state.entries,
            order = state.sort,
            showHidden = state.showHidden,
            query = state.query,
        )
    }
    val mediaImages = remember(state.mediaItems) {
        state.mediaItems.filter { it.mimeType?.startsWith("image/") == true }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(listState) {
        controller.bindScrollProvider {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }
    }
    var quickBarVisible by remember { mutableStateOf(true) }
    LaunchedEffect(state.activeTabId) {
        quickBarVisible = true
    }
    LaunchedEffect(listState, state.activeTabId) {
        var last = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { current ->
            when {
                current.first == 0 && current.second < 12 -> quickBarVisible = true
                current.first > last.first ||
                    (current.first == last.first && current.second > last.second + 18) ->
                    quickBarVisible = false
                current.first < last.first ||
                    (current.first == last.first && current.second < last.second - 18) ->
                    quickBarVisible = true
            }
            last = current
        }
    }
    DisposableEffect(state.activeTabId, state.path) {
        val path = state.path
        onDispose {
            controller.saveScrollAt(
                path,
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
            )
        }
    }
    if (mediaCategory == null) {
        LaunchedEffect(state.activeTabId, state.path) {
            val path = state.path
            snapshotFlow {
                state.loading to listState.layoutInfo.totalItemsCount
            }.first { (loading, total) -> !loading && total > 0 }
            withFrameNanos { }
            val (index, offset) = controller.scrollForActiveTab()
            Log.d("FileBrowserScroll", "restore path=$path target=$index,$offset total=${listState.layoutInfo.totalItemsCount}")
            runCatching { listState.scrollToItem(index, offset) }
            withFrameNanos { }
            if (listState.firstVisibleItemIndex != index) {
                runCatching { listState.scrollToItem(index, offset) }
            }
            snapshotFlow {
                if (state.loading || state.path != path) null else {
                    listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                }
            }.filterNotNull().collect { (index, offset) ->
                controller.saveScroll(index, offset)
            }
        }
    }
    val levelBytes = remember(state.entries) {
        state.entries.asSequence()
            .filter { it.kind != FileEntryKind.DIRECTORY }
            .sumOf { it.sizeBytes }
    }
    val context = LocalContext.current
    val openWithTitle = stringResource(R.string.file_browser_open_with)
    val fileImageItems = remember(filtered, context) {
        filtered.mapNotNull { entry ->
            if (FileKind.from(entry.name, entry.kind) != FileKind.IMAGE) return@mapNotNull null
            val file = java.io.File(entry.path)
            if (!file.isFile || !file.canRead()) return@mapNotNull null
            val uri = runCatching {
                androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file,
                )
            }.getOrNull() ?: return@mapNotNull null
            FileBrowserMediaItem(
                id = entry.path.hashCode().toLong(),
                uri = uri,
                name = entry.name,
                mimeType = android.webkit.MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(file.extension.lowercase())
                    ?: "image/*",
                sizeBytes = file.length(),
                modifiedEpochSeconds = file.lastModified() / 1000L,
                relativePath = entry.path,
            )
        }
    }
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    var copied by remember { mutableStateOf(false) }
    var showPathDialog by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var showFavorites by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var searchExpanded by remember { mutableStateOf(false) }
    var imagePreview by remember { mutableStateOf<FileBrowserEntry?>(null) }
    var swipeAnchorPath by remember(state.activeTabId, state.path) {
        mutableStateOf<String?>(null)
    }
    LaunchedEffect(state.selectionMode) {
        if (!state.selectionMode) swipeAnchorPath = null
    }
    var selectionBarHeightPx by remember { mutableStateOf(0) }
    val selectionInset = if (state.selectionMode) {
        with(density) { selectionBarHeightPx.toDp() }
    } else {
        0.dp
    }
    LaunchedEffect(state.selectionMode, selectionBarHeightPx, filtered) {
        if (state.selectionMode && selectionBarHeightPx > 0) {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            if (lastVisible != null && lastVisible.index >= filtered.lastIndex - 1) {
                runCatching { listState.scrollBy(selectionBarHeightPx.toFloat()) }
            }
        }
    }
    var exportBusy by remember { mutableStateOf(false) }
    var exportFailed by remember { mutableStateOf(false) }
    var nameTarget by remember { mutableStateOf<FileBrowserNameTarget?>(null) }
    var deleteTarget by remember { mutableStateOf<FileBrowserEntry?>(null) }
    var operationError by remember { mutableStateOf(false) }
    var showBatchDelete by remember { mutableStateOf(false) }
    var ocrText by remember { mutableStateOf<String?>(null) }
    val actionScope = rememberCoroutineScope()

    LaunchedEffect(operationError) {
        if (operationError) {
            delay(1_800L)
            operationError = false
        }
    }

    LaunchedEffect(state.details?.path) {
        exportBusy = false
        exportFailed = false
    }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1_600L)
            copied = false
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(150)) + scaleIn(
            initialScale = 0.96f,
            animationSpec = tween(180),
        ),
        exit = fadeOut(tween(150)) + scaleOut(
            targetScale = 0.98f,
            animationSpec = tween(150),
        ),
        modifier = Modifier.fillMaxSize(),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.42f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
            val cardWidth = minOf(maxWidth * 0.94f, 560.dp)
            val cardHeight = minOf(maxHeight * 0.90f, 820.dp)
            FileBrowserCard(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(cardWidth)
                    .height(cardHeight),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        FileBrowserHeader(
                            access = state.access,
                            onRefresh = controller::refresh,
                            onSearchClick = { searchExpanded = !searchExpanded },
                            onMenuClick = { showMenu = true },
                            onDismiss = onDismiss,
                            showMenuButton = mediaCategory == null,
                        )
                        FileBrowserTabBar(
                            tabs = state.tabs,
                            activeTabId = state.activeTabId,
                            onSelect = controller::selectTab,
                            onClose = controller::closeTab,
                            onNew = controller::newTab,
                        )
                        if (mediaCategory == null) {
                            FileBrowserBreadcrumb(
                                path = state.path,
                                internalRoot = internalRoot,
                                onNavigate = { target ->
                                    focusManager.clearFocus()
                                    controller.navigateTo(target)
                                },
                                onLongClick = { copyToClipboard(context, state.path) },
                            )
                        }
                        AnimatedVisibility(
                            visible = searchExpanded || state.query.isNotEmpty(),
                            enter = expandVertically(tween(160)) + fadeIn(tween(120)),
                            exit = shrinkVertically(tween(140)) + fadeOut(tween(100)),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                MiuixSearchField(
                                    query = state.query,
                                    onQueryChange = controller::setQuery,
                                    modifier = Modifier.weight(1f),
                                    hintResId = R.string.file_browser_search_hint,
                                )
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            searchExpanded = false
                                            controller.setQuery("")
                                            focusManager.clearFocus()
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        AnimatedVisibility(visible = quickBarVisible && mediaCategory == null) {
                            FileBrowserQuickChips(
                                internalRoot = internalRoot,
                                onNavigate = { target ->
                                    focusManager.clearFocus()
                                    controller.navigateTo(target)
                                },
                            )
                        }
                        if (state.loading && state.entries.isNotEmpty() && mediaCategory == null) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                            )
                        }
                        if (mediaCategory == null) {
                            FileBrowserLimitedBanner(
                                state = state,
                                internalRoot = internalRoot,
                                onRequestAllFiles = onRequestAllFiles,
                                onRequestShizuku = onRequestShizuku,
                            )
                        }
                        if (mediaCategory != null) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                            ) {
                                FileBrowserMediaGrid(
                                    category = mediaCategory,
                                    items = state.mediaItems,
                                    query = state.query,
                                    loading = state.mediaLoading,
                                    hasMore = state.mediaHasMore,
                                    totalCount = state.mediaTotalCount,
                                    totalBytes = state.mediaTotalBytes,
                                    error = state.error,
                                    mediaPermissionAvailable =
                                        mediaCategory != FileBrowserMediaCategory.DOCUMENTS,
                                    selectedIds = state.mediaSelectedIds,
                                    onLoadMore = controller::loadMoreMedia,
                                    onOpen = { item ->
                                        if (state.mediaSelectedIds.isNotEmpty()) {
                                            controller.toggleMediaSelection(item)
                                        } else if (item.mimeType?.startsWith("image/") == true) {
                                            controller.showMediaPreview(item)
                                        } else {
                                            openMediaExternally(context, item)
                                        }
                                    },
                                    onLongClick = { item -> controller.toggleMediaSelection(item) },
                                    onRequestMediaPermission = onRequestMediaPermission,
                                    onRequestAllFiles = onRequestAllFiles,
                                    onRetry = controller::refresh,
                                )
                            }
                            if (state.mediaSelectedIds.isNotEmpty()) {
                                FileBrowserMediaSelectionBar(
                                    count = state.mediaSelectedIds.size,
                                    onShare = {
                                        val selected = state.mediaItems.filter {
                                            it.id in state.mediaSelectedIds
                                        }
                                        shareMediaItems(context, selected)
                                        controller.clearMediaSelection()
                                    },
                                    onClose = controller::clearMediaSelection,
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                            ) {
                                FileBrowserListContent(
                                    state = state,
                                    filtered = filtered,
                                    internalRoot = internalRoot,
                                    listState = listState,
                                    onEntryClick = { entry ->
                                        focusManager.clearFocus()
                                        if (state.selectionMode || state.selectedPaths.isNotEmpty()) {
                                            controller.toggleSelection(entry.path)
                                        } else {
                                            when (entry.kind) {
                                                FileEntryKind.DIRECTORY,
                                                FileEntryKind.SYMLINK,
                                                -> controller.navigateTo(entry.path)

                                                FileEntryKind.FILE,
                                                FileEntryKind.OTHER,
                                                -> {
                                                    if (
                                                        FileKind.from(entry.name, entry.kind) ==
                                                        FileKind.IMAGE
                                                    ) {
                                                        imagePreview = entry
                                                    } else {
                                                        val opened = FileBrowserOpen.open(
                                                            context = context,
                                                            path = entry.path,
                                                            chooserTitle = openWithTitle,
                                                        )
                                                        if (!opened) {
                                                            controller.showDetails(entry)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    onEntryLongClick = { entry ->
                                        focusManager.clearFocus()
                                        controller.enterSelection(entry)
                                        swipeAnchorPath = entry.path
                                    },
                                    onSwipeSelect = { entry ->
                                        focusManager.clearFocus()
                                        val ordered = filtered.map { it.path }
                                        val range = FileBrowserSelection.rangeBetween(
                                            orderedPaths = ordered,
                                            anchorPath = swipeAnchorPath,
                                            targetPath = entry.path,
                                        )
                                        if (range != null) {
                                            controller.selectRange(range)
                                        } else {
                                            controller.selectRange(listOf(entry.path))
                                            swipeAnchorPath = entry.path
                                        }
                                    },
                                    selectionMode = state.selectionMode,
                                    selectedPaths = state.selectedPaths,
                                    access = state.access,
                                    onRetry = controller::refresh,
                                    onRequestAllFiles = onRequestAllFiles,
                                    onRequestShizuku = onRequestShizuku,
                                    bottomInset = selectionInset,
                                )
                            }
                        }
                        if (mediaCategory == null) {
                        if (state.selectionMode) {
                            FileBrowserSelectionBar(
                                count = state.selectedPaths.size,
                                onCopy = { controller.copySelection(cut = false) },
                                onMove = { controller.copySelection(cut = true) },
                                onShare = {
                                    val paths = state.selectedPaths.toList()
                                    if (paths.isNotEmpty()) {
                                        actionScope.launch {
                                            shareFileBrowserPaths(context, paths, state.access)
                                        }
                                    }
                                },
                                onDelete = { showBatchDelete = true },
                                onDetails = if (state.selectedPaths.size == 1) {
                                    {
                                        state.entries
                                            .firstOrNull { it.path in state.selectedPaths }
                                            ?.let(controller::showDetails)
                                    }
                                } else {
                                    null
                                },
                                onCancel = controller::exitSelection,
                                modifier = Modifier.onSizeChanged {
                                    selectionBarHeightPx = it.height
                                },
                            )
                        }
                        if (!state.selectionMode && state.clipboard != null) {
                            FileBrowserClipboardBar(
                                clipboard = state.clipboard,
                                onPaste = controller::paste,
                                onCancel = controller::clearClipboard,
                            )
                        }
                        if (state.operationRunning) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                LinearProgressIndicator(
                                    progress = { state.operationProgress },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(3.dp),
                                )
                                TextButton(onClick = controller::cancelOperation) {
                                    Text(stringResource(R.string.file_browser_cancel))
                                }
                            }
                        }
                        if (state.operationMessage != null) {
                            Text(
                                text = state.operationMessage,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                        if (state.lastTrash.isNotEmpty() && !state.operationRunning) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f))
                                    .padding(horizontal = 12.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.file_browser_trash_count,
                                        state.lastTrash.size,
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = controller::undoDelete) {
                                    Text(stringResource(R.string.file_browser_undo))
                                }
                                TextButton(onClick = controller::clearTrashUndo) {
                                    Text(stringResource(R.string.file_browser_cancel))
                                }
                            }
                        }
                        if (state.error == null && (filtered.isNotEmpty() || state.truncated)) {
                            FileBrowserFooter(
                                count = filtered.size,
                                sizeBytes = levelBytes,
                                directorySizeBytes = state.directorySizeBytes,
                                directorySizeLoading = state.directorySizeLoading,
                                directorySizeComputed = state.directorySizeComputed,
                                truncated = state.truncated,
                                onComputeSize = controller::computeDirectorySize,
                            )
                        }
                        }
                    }

                    if (copied) {
                        Text(
                            text = stringResource(R.string.file_browser_copied),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 52.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }

                    val details = state.details
                    if (details != null) {
                        val canDirectOpen = FileBrowserOpen.canOpen(details.path)
                        val canExport = !canDirectOpen &&
                            state.access?.mode == AccessMode.ROOT
                        val openWithLabel = when {
                            exportBusy -> stringResource(R.string.file_browser_exporting)
                            exportFailed -> stringResource(R.string.file_browser_export_failed)
                            canDirectOpen -> stringResource(R.string.file_browser_open_with)
                            canExport -> stringResource(R.string.file_browser_export_open)
                            else -> null
                        }
                        FileBrowserDetailsOverlay(
                            entry = details,
                            onCopy = {
                                copyToClipboard(context, details.path)
                                copied = true
                            },
                            onOpenWith = when {
                                canDirectOpen -> {
                                    {
                                        FileBrowserOpen.open(
                                            context = context,
                                            path = details.path,
                                            chooserTitle = openWithTitle,
                                        )
                                    }
                                }
                                canExport -> {
                                    {
                                        if (!exportBusy) {
                                            actionScope.launch {
                                                exportBusy = true
                                                exportFailed = false
                                                val exported = FileBrowserExport.exportToCache(
                                                    context = context,
                                                    sourcePath = details.path,
                                                )
                                                exportBusy = false
                                                if (exported != null) {
                                                    FileBrowserOpen.openFile(
                                                        context = context,
                                                        file = exported,
                                                        chooserTitle = openWithTitle,
                                                    )
                                                } else {
                                                    exportFailed = true
                                                }
                                            }
                                        }
                                    }
                                }
                                else -> null
                            },
                            onOpenImage = if (
                                FileKind.from(details.name, details.kind) == FileKind.IMAGE
                            ) {
                                { imagePreview = details }
                            } else {
                                null
                            },
                            openWithLabel = openWithLabel,
                            openWithEnabled = !exportBusy,
                            onRename = { nameTarget = FileBrowserNameTarget.Rename(details) },
                            onDelete = { deleteTarget = details },
                            onClose = controller::hideDetails,
                        )
                    }
                    state.mediaPreview?.let { preview ->
                        FileBrowserMediaViewer(
                            items = mediaImages,
                            initialItem = preview,
                            onClose = controller::hideMediaPreview,
                            onShare = { item -> shareMediaItems(context, listOf(item)) },
                            onEdit = { bitmap ->
                                val editorPath = com.slideindex.app.imageeditor.ImageEditorLaunchCache
                                    .put(context, bitmap)
                                com.slideindex.app.imageeditor.SlideIndexImageEditorActivity
                                    .launch(context, editorPath)
                            },
                            onPin = { bitmap ->
                                StashCoordinator.pinImageToScreen(context, bitmap)
                            },
                            onOcr = { bitmap ->
                                actionScope.launch {
                                    val text = recognizeImageText(context, bitmap)
                                    if (text.isNullOrBlank()) {
                                        Toast.makeText(
                                            context,
                                            R.string.file_browser_media_ocr_empty,
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    } else {
                                        ocrText = text
                                    }
                                }
                            },
                            onSearch = { bitmap ->
                                openImageSearch(context, bitmap)
                            },
                            onDelete = { item ->
                                FileBrowserMediaDeleteActivity.launch(context, item.uri) { success ->
                                    if (success) controller.refresh()
                                }
                            },
                        )
                    }
                    ocrText?.let { text ->
                        FileBrowserDialog(
                            title = stringResource(R.string.file_browser_media_ocr),
                            onDismiss = { ocrText = null },
                            confirmLabel = stringResource(R.string.file_browser_close),
                            onConfirm = { ocrText = null },
                        ) {
                            SelectionContainer {
                                Text(text = text, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    if (showPathDialog) {
                        FileBrowserPathDialog(
                            initialPath = state.path,
                            onDismiss = { showPathDialog = false },
                            onNavigate = { target ->
                                showPathDialog = false
                                focusManager.clearFocus()
                                controller.navigateTo(target)
                            },
                        )
                    }
                    if (showSearchDialog) {
                        FileBrowserSearchDialog(
                            onDismiss = { showSearchDialog = false },
                            onSearch = { query ->
                                showSearchDialog = false
                                focusManager.clearFocus()
                                controller.searchRecursive(query)
                            },
                        )
                    }
                    state.searchResults?.let { results ->
                        FileBrowserSearchOverlay(
                            query = state.searchQuery.orEmpty(),
                            results = results,
                            loading = state.searchLoading,
                            onOpen = controller::openSearchResult,
                            onClose = controller::clearSearch,
                        )
                    }
                    if (showFavorites) {
                        FileBrowserFavoritesOverlay(
                            currentPath = state.path,
                            onNavigate = { path ->
                                showFavorites = false
                                controller.navigateTo(path)
                            },
                            onClose = { showFavorites = false },
                        )
                    }
                    if (showMenu && mediaCategory == null) {
                        FileBrowserMenuOverlay(
                            showHidden = state.showHidden,
                            sort = state.sort,
                            pinned = FileBrowserPreferences.isPinned(state.path),
                            onSort = {
                                controller.setSort(it)
                                showMenu = false
                            },
                            onToggleHidden = {
                                controller.setShowHidden(!state.showHidden)
                                showMenu = false
                            },
                            onCopyPath = {
                                copyToClipboard(context, state.path)
                                copied = true
                                showMenu = false
                            },
                            onJumpPath = {
                                showMenu = false
                                showPathDialog = true
                            },
                            onNewFolder = {
                                showMenu = false
                                nameTarget = FileBrowserNameTarget.Create(state.path)
                            },
                            onRecursiveSearch = {
                                showMenu = false
                                showSearchDialog = true
                            },
                            onFavorites = {
                                showMenu = false
                                showFavorites = true
                            },
                            onAddToHome = {
                                showMenu = false
                                if (!FileBrowserLauncherHelper.requestPinShortcut(context)) {
                                    FileBrowserLauncherHelper.showPinFailedToast(context)
                                }
                            },
                            onTogglePin = {
                                FileBrowserPreferences.togglePin(state.path)
                                showMenu = false
                            },
                            onDismiss = { showMenu = false },
                        )
                    }
                    nameTarget?.let { target ->
                        val access = state.access
                        FileBrowserNameDialog(
                            initialName = when (target) {
                                is FileBrowserNameTarget.Create -> ""
                                is FileBrowserNameTarget.Rename -> target.entry.name
                            },
                            title = when (target) {
                                is FileBrowserNameTarget.Create ->
                                    stringResource(R.string.file_browser_new_folder)
                                is FileBrowserNameTarget.Rename ->
                                    stringResource(R.string.file_browser_rename)
                            },
                            confirmLabel = when (target) {
                                is FileBrowserNameTarget.Create ->
                                    stringResource(R.string.file_browser_create)
                                is FileBrowserNameTarget.Rename ->
                                    stringResource(R.string.file_browser_rename)
                            },
                            onDismiss = { nameTarget = null },
                            onConfirm = { name ->
                                nameTarget = null
                                if (access != null) {
                                    actionScope.launch {
                                        val ok = when (target) {
                                            is FileBrowserNameTarget.Create ->
                                                FileBrowserWrite.createDirectory(
                                                    contextPath = target.parent,
                                                    name = name,
                                                    access = access,
                                                )
                                            is FileBrowserNameTarget.Rename ->
                                                FileBrowserWrite.rename(
                                                    path = target.entry.path,
                                                    newName = name,
                                                    access = access,
                                                )
                                        }
                                        if (ok) {
                                            controller.hideDetails()
                                            controller.refresh()
                                        } else {
                                            operationError = true
                                        }
                                    }
                                }
                            },
                        )
                    }
                    deleteTarget?.let { target ->
                        FileBrowserDialog(
                            title = stringResource(R.string.file_browser_delete),
                            onDismiss = { deleteTarget = null },
                            confirmLabel = stringResource(R.string.file_browser_delete),
                            confirmColor = MaterialTheme.colorScheme.error,
                            onConfirm = {
                                deleteTarget = null
                                controller.deleteEntry(target)
                            },
                        ) {
                            Text(
                                stringResource(
                                    R.string.file_browser_delete_message,
                                    target.name,
                                ),
                            )
                        }
                    }
                    if (showBatchDelete) {
                        FileBrowserDialog(
                            title = stringResource(R.string.file_browser_delete),
                            onDismiss = { showBatchDelete = false },
                            confirmLabel = stringResource(R.string.file_browser_delete),
                            confirmColor = MaterialTheme.colorScheme.error,
                            onConfirm = {
                                showBatchDelete = false
                                controller.deleteSelected()
                            },
                        ) {
                            Text(
                                stringResource(
                                    R.string.file_browser_delete_selected_message,
                                    state.selectedPaths.size,
                                ),
                            )
                        }
                    }
                    if (state.pendingPaste != null) {
                        FileBrowserConflictDialog(
                            count = state.pendingConflicts,
                            onRename = { controller.resolvePaste(FileConflictPolicy.RENAME) },
                            onOverwrite = { controller.resolvePaste(FileConflictPolicy.OVERWRITE) },
                            onSkip = { controller.resolvePaste(FileConflictPolicy.SKIP) },
                            onCancel = controller::cancelPaste,
                        )
                    }
                    imagePreview?.let { entry ->
                        val viewerItem = fileImageItems.firstOrNull {
                            it.relativePath == entry.path
                        }
                        if (viewerItem != null) {
                            FileBrowserMediaViewer(
                                items = fileImageItems,
                                initialItem = viewerItem,
                                onClose = { imagePreview = null },
                                onShare = { item -> shareMediaItems(context, listOf(item)) },
                                onEdit = { bitmap ->
                                    val editorPath = com.slideindex.app.imageeditor.ImageEditorLaunchCache
                                        .put(context, bitmap)
                                    com.slideindex.app.imageeditor.SlideIndexImageEditorActivity
                                        .launch(context, editorPath)
                                },
                                onPin = { bitmap ->
                                    StashCoordinator.pinImageToScreen(context, bitmap)
                                },
                                onOcr = { bitmap ->
                                    actionScope.launch {
                                        val text = recognizeImageText(context, bitmap)
                                        if (text.isNullOrBlank()) {
                                            Toast.makeText(
                                                context,
                                                R.string.file_browser_media_ocr_empty,
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        } else {
                                            ocrText = text
                                        }
                                    }
                                },
                                onSearch = { bitmap ->
                                    openImageSearch(context, bitmap)
                                },
                                onDelete = { item ->
                                    val target = state.entries.firstOrNull {
                                        it.path == item.relativePath
                                    }
                                    if (target != null) controller.deleteEntry(target)
                                },
                            )
                        } else {
                            val images = filtered.filter {
                                FileKind.from(it.name, it.kind) == FileKind.IMAGE
                            }
                            val imageIndex = images.indexOfFirst { it.path == entry.path }
                            FileBrowserImageViewer(
                                entry = entry,
                                access = state.access,
                                onPrevious = if (imageIndex > 0) {
                                    { imagePreview = images[imageIndex - 1] }
                                } else {
                                    null
                                },
                                onNext = if (imageIndex >= 0 && imageIndex < images.lastIndex) {
                                    { imagePreview = images[imageIndex + 1] }
                                } else {
                                    null
                                },
                                onDetails = {
                                    imagePreview = null
                                    controller.showDetails(entry)
                                },
                                onEdit = { bitmap ->
                                    val editorPath = com.slideindex.app.imageeditor.ImageEditorLaunchCache
                                        .put(context, bitmap)
                                    com.slideindex.app.imageeditor.SlideIndexImageEditorActivity
                                        .launch(context, editorPath)
                                },
                                onClose = { imagePreview = null },
                            )
                        }
                    }
                    if (operationError) {
                        Text(
                            text = stringResource(R.string.file_browser_operation_failed),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 52.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FileBrowserMenuOverlay(
    showHidden: Boolean,
    sort: FileSortOrder,
    pinned: Boolean,
    onSort: (FileSortOrder) -> Unit,
    onToggleHidden: () -> Unit,
    onCopyPath: () -> Unit,
    onJumpPath: () -> Unit,
    onNewFolder: () -> Unit,
    onRecursiveSearch: () -> Unit,
    onFavorites: () -> Unit,
    onAddToHome: () -> Unit,
    onTogglePin: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            shape = RoundedCornerShape(22.dp),
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
            ) {
                Text(
                    text = stringResource(R.string.file_browser_menu),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.file_browser_sort_name) + " / " +
                        stringResource(R.string.file_browser_sort_time) + " / " +
                        stringResource(R.string.file_browser_sort_size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                )
                listOf(
                    Triple(R.string.file_browser_sort_name, FileSortOrder.NAME_ASC, FileSortOrder.NAME_DESC),
                    Triple(R.string.file_browser_sort_time, FileSortOrder.TIME_ASC, FileSortOrder.TIME_DESC),
                    Triple(R.string.file_browser_sort_size, FileSortOrder.SIZE_ASC, FileSortOrder.SIZE_DESC),
                ).forEach { (labelRes, asc, desc) ->
                    val active = sort == asc || sort == desc
                    val next = when {
                        !active -> if (labelRes == R.string.file_browser_sort_name) asc else desc
                        sort == asc -> desc
                        else -> asc
                    }
                    TextButton(
                        onClick = { onSort(next) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(labelRes) +
                                if (active) {
                                    if (sort == asc) " ↑" else " ↓"
                                } else {
                                    ""
                                },
                            color = if (active) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
                HorizontalDivider()
                TextButton(onClick = onToggleHidden, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.file_browser_show_hidden) +
                            if (showHidden) " ✓" else "",
                    )
                }
                TextButton(onClick = onCopyPath, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.file_browser_copy_current_path))
                }
                TextButton(onClick = onJumpPath, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.file_browser_jump_path))
                }
                TextButton(onClick = onNewFolder, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.file_browser_new_folder))
                }
                TextButton(onClick = onRecursiveSearch, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.file_browser_recursive_search))
                }
                TextButton(onClick = onFavorites, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.file_browser_favorites))
                }
                TextButton(onClick = onAddToHome, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.file_browser_add_to_home))
                }
                TextButton(onClick = onTogglePin, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(
                            if (pinned) {
                                R.string.file_browser_unpin_current
                            } else {
                                R.string.file_browser_pin_current
                            },
                        ),
                    )
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.file_browser_cancel))
                }
            }
        }
    }
}

@Composable
private fun FileBrowserTabBar(
    tabs: List<FileBrowserTab>,
    activeTabId: Long,
    onSelect: (Long) -> Unit,
    onClose: (Long) -> Unit,
    onNew: () -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(tabs, key = { it.id }) { tab ->
            val active = tab.id == activeTabId
            Row(
                modifier = Modifier
                    .heightIn(min = 28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                        else Color.Transparent,
                    )
                    .padding(start = 9.dp, end = if (tabs.size > 1) 2.dp else 9.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = tabTitle(tab.path),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .widthIn(max = 130.dp)
                        .clickable { onSelect(tab.id) },
                )
                if (tabs.size > 1) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onClose(tab.id) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .clickable(onClick = onNew),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun tabTitle(path: String): String {
    FileBrowserMediaCategory.forPath(path)?.let { category ->
        return stringResource(category.labelRes)
    }
    return when (path) {
        "/" -> "/"
        "/storage/emulated/0" -> stringResource(R.string.file_browser_crumb_internal)
        else -> path.trimEnd('/').substringAfterLast('/').ifEmpty { path }
    }
}

private fun openImageSearch(context: Context, bitmap: Bitmap) {
    // 文件浏览器本身也是 overlay；先收起它，再把图片交给搜索面板的图片模式。
    SearchPanelSessionState.pushImage(bitmap)
    FileBrowserOverlayWindow.dismiss()
    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
        { SearchPanelOverlayWindow.show(context) },
        240L,
    )
}

private suspend fun recognizeImageText(
    context: Context,
    bitmap: Bitmap,
): String? {
    val settings = OverlayDependencyAccess.overlayDependencies(context)
        ?.settingsRepository
        ?.readSnapshot()
    val configured = settings?.floatBallOcrModelId.orEmpty()
    val catalog = OcrDependencyAccess.catalogProvider(context) ?: return null
    val repository = OcrDependencyAccess.modelRepository(context) ?: return null
    val modelId = if (configured.isNotBlank()) {
        configured.takeIf {
            catalog.findModel(it)?.engine == OcrEngines.PPOCR && repository.isInstalled(it)
        }
    } else {
        null
    } ?: repository.installedModelIds().firstOrNull { id ->
        catalog.findModel(id)?.engine == OcrEngines.PPOCR
    } ?: run {
        Toast.makeText(
            context,
            R.string.share_image_ocr_model_required,
            Toast.LENGTH_SHORT,
        ).show()
        return null
    }
    val inference = OcrDependencyAccess.inferenceService(context) ?: return null
    return withContext(Dispatchers.Default) {
        when (val result = inference.recognizeBitmap(modelId, bitmap)) {
            is OcrRecognizeResult.Success -> result.text
            is OcrRecognizeResult.Failure -> null
        }
    }
}

private fun openMediaExternally(context: Context, item: FileBrowserMediaItem) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(item.uri, item.mimeType ?: "*/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val chooser = Intent.createChooser(
        intent,
        context.getString(R.string.file_browser_open_with),
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(intent)
    }.onFailure {
        runCatching { context.startActivity(chooser) }
    }
}

private fun shareMediaItems(context: Context, items: List<FileBrowserMediaItem>) {
    if (items.isEmpty()) return
    val single = items.size == 1
    val intent = if (single) {
        Intent(Intent.ACTION_SEND).apply {
            type = items.first().mimeType ?: "*/*"
            putExtra(Intent.EXTRA_STREAM, items.first().uri)
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(
                Intent.EXTRA_STREAM,
                ArrayList(items.map { it.uri }),
            )
        }
    }.apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val chooser = Intent.createChooser(
        intent,
        context.getString(R.string.file_browser_share),
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(chooser) }
}

@Composable
private fun FileBrowserMediaSelectionBar(
    count: Int,
    onShare: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f))
            .padding(start = 16.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.file_browser_media_selected, count),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onShare) {
            Text(stringResource(R.string.file_browser_share))
        }
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.file_browser_close),
            )
        }
    }
}

@Composable
private fun FileBrowserHeader(
    access: FileBrowserAccessInfo?,
    onRefresh: () -> Unit,
    onSearchClick: () -> Unit,
    onMenuClick: () -> Unit,
    onDismiss: () -> Unit,
    showMenuButton: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.file_browser_title),
                style = MiuixTheme.textStyles.title4,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(modifier = Modifier.width(8.dp))
            FileBrowserAccessBadge(access)
        }
        IconButton(onClick = onSearchClick) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = stringResource(R.string.file_browser_search_hint),
            )
        }
        IconButton(onClick = onRefresh) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = stringResource(R.string.file_browser_refresh),
            )
        }
        if (showMenuButton) {
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.file_browser_menu),
                )
            }
        }
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.file_browser_close),
            )
        }
    }
}

@Composable
private fun SortMenuItem(
    labelRes: Int,
    order: FileSortOrder,
    selected: FileSortOrder,
    ascending: Boolean,
    onSelect: (FileSortOrder) -> Unit,
) {
    DropdownMenuItem(
        text = {
            Text(
                text = stringResource(labelRes) + if (ascending) " ↑" else " ↓",
            )
        },
        onClick = { onSelect(order) },
        trailingIcon = {
            if (selected == order) {
                Icon(Icons.Filled.Check, contentDescription = null)
            }
        },
    )
}

@Composable
private fun FileBrowserAccessBadge(access: FileBrowserAccessInfo?) {
    val mode = access?.mode
    val textRes = when (mode) {
        null -> R.string.file_browser_access_detecting
        AccessMode.ROOT -> R.string.file_browser_access_root
        AccessMode.SHIZUKU -> R.string.file_browser_access_shizuku
        AccessMode.ALL_FILES -> R.string.file_browser_access_all_files
        AccessMode.LIMITED -> R.string.file_browser_access_limited
    }
    val color = when (mode) {
        AccessMode.ROOT, AccessMode.SHIZUKU -> MaterialTheme.colorScheme.primary
        AccessMode.ALL_FILES -> MaterialTheme.colorScheme.tertiary
        AccessMode.LIMITED -> MaterialTheme.colorScheme.error
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        color = color,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

@Composable
private fun FileBrowserBreadcrumb(
    path: String,
    internalRoot: String,
    onNavigate: (String) -> Unit,
    onLongClick: () -> Unit,
) {
    val rootLabel = stringResource(R.string.file_browser_crumb_root)
    val internalLabel = stringResource(R.string.file_browser_crumb_internal)
    val normalizedInternal = remember(internalRoot) {
        FileBrowserPaths.normalize(internalRoot, internalRoot)
    }
    val items = remember(path, internalRoot, rootLabel, internalLabel) {
        FileBrowserPaths.crumbs(path, internalRoot).map { crumb ->
            val label = when (crumb.path) {
                "/" -> rootLabel
                normalizedInternal -> internalLabel
                else -> crumb.label
            }
            BreadcrumbItem(path = crumb.path, text = label)
        }
    }
    val scrollState = rememberScrollState()

    BreadcrumbBar(
        items = items,
        onItemClick = { index ->
            if (index != items.lastIndex) {
                onNavigate(items[index].path)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = onLongClick,
            ),
        highlightIndex = items.lastIndex,
        colors = BreadcrumbBarDefaults.breadcrumbBarColors(
            color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.62f),
            highlightColor = MiuixTheme.colorScheme.primary,
            backgroundColor = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.08f),
            highlightBackgroundColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.16f),
        ),
        itemMaxWidth = 120.dp,
        scrollState = scrollState,
    )
}

private data class QuickTarget(
    val labelRes: Int,
    val path: String,
    val icon: ImageVector,
)

@Composable
private fun FileBrowserQuickChips(
    internalRoot: String,
    onNavigate: (String) -> Unit,
) {
    val paths = FileBrowserPreferences.quickShortcuts(internalRoot)
    if (paths.isEmpty()) return
    val normalizedRoot = FileBrowserPaths.normalize(internalRoot, internalRoot)
    val scrollState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val reorderState = rememberReorderableLazyListState(scrollState) { from, to ->
        FileBrowserPreferences.moveShortcut(from.index, to.index, internalRoot)
    }

    LazyRow(
        state = scrollState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(paths, key = { it }) { path ->
            ReorderableItem(
                state = reorderState,
                key = path,
            ) { _ ->
                val label = when (path) {
                    "/" -> stringResource(R.string.file_browser_quick_root)
                    normalizedRoot -> stringResource(R.string.file_browser_quick_internal)
                    "$normalizedRoot/Android/data" ->
                        stringResource(R.string.file_browser_quick_android_data)
                    "$normalizedRoot/Download" ->
                        stringResource(R.string.file_browser_quick_download)
                    "$normalizedRoot/Android/obb" ->
                        stringResource(R.string.file_browser_quick_obb)
                    "/data/data" -> stringResource(R.string.file_browser_quick_app_data)
                    else -> tabTitle(path)
                }
                val icon = when (path) {
                    "/" -> Icons.Outlined.Storage
                    normalizedRoot -> Icons.Outlined.PhoneAndroid
                    "$normalizedRoot/Android/data" -> Icons.Outlined.Android
                    "$normalizedRoot/Download" -> Icons.Outlined.Download
                    "/data/data" -> Icons.Outlined.Apps
                    else -> Icons.Outlined.Folder
                }
                Row(
                    modifier = Modifier
                        .longPressDraggableHandle(
                            onDragStarted = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                        )
                        .clip(RoundedCornerShape(13.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f))
                        .clickable { onNavigate(path) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun FileBrowserFavoritesOverlay(
    currentPath: String,
    onNavigate: (String) -> Unit,
    onClose: () -> Unit,
) {
    val internalRoot = FileBrowserStorage.internalStoragePath()
    var favorites by remember {
        mutableStateOf(FileBrowserPreferences.quickShortcuts(internalRoot))
    }
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val list = favorites.toMutableList()
        if (from.index in list.indices && to.index in list.indices) {
            val item = list.removeAt(from.index)
            list.add(to.index, item)
            favorites = list
            FileBrowserPreferences.moveShortcut(from.index, to.index, internalRoot)
        }
    }

    fun refresh() {
        favorites = FileBrowserPreferences.quickShortcuts(internalRoot)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            shape = RoundedCornerShape(22.dp),
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.file_browser_favorites),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onClose, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = null)
                    }
                }
                if (favorites.isEmpty()) {
                    Text(
                        text = stringResource(R.string.file_browser_favorites_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 460.dp),
                        contentPadding = PaddingValues(bottom = 4.dp),
                    ) {
                        items(favorites, key = { it }) { path ->
                            ReorderableItem(
                                state = reorderState,
                                key = path,
                            ) { _ ->
                                FileBrowserFavoriteRow(
                                    path = path,
                                    modifier = Modifier.longPressDraggableHandle(
                                        onDragStarted = {
                                            haptic.performHapticFeedback(
                                                HapticFeedbackType.LongPress,
                                            )
                                        },
                                    ),
                                    onClick = { onNavigate(path) },
                                    onDelete = {
                                        FileBrowserPreferences.removeShortcut(path)
                                        refresh()
                                    },
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                TextButton(
                    onClick = {
                        FileBrowserPreferences.togglePin(currentPath)
                        refresh()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            if (FileBrowserPreferences.isPinned(currentPath)) {
                                R.string.file_browser_favorite_remove
                            } else {
                                R.string.file_browser_favorite_add
                            },
                        ),
                    )
                }
                if (FileBrowserPreferences.hiddenSystemShortcuts().isNotEmpty()) {
                    TextButton(
                        onClick = {
                            FileBrowserPreferences.resetHiddenShortcuts()
                            refresh()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.file_browser_restore_shortcuts))
                    }
                }
            }
        }
    }
}

@Composable
private fun FileBrowserFavoriteRow(
    path: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tabTitle(path),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = path,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(7.dp))
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FileBrowserConflictDialog(
    count: Int,
    onRename: () -> Unit,
    onOverwrite: () -> Unit,
    onSkip: () -> Unit,
    onCancel: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onCancel,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            shape = RoundedCornerShape(22.dp),
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.file_browser_conflict_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.file_browser_conflict_message, count),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(14.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onRename, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.file_browser_conflict_rename))
                    }
                    TextButton(onClick = onOverwrite, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.file_browser_conflict_overwrite),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.file_browser_conflict_skip))
                    }
                    TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.file_browser_cancel))
                    }
                }
            }
        }
    }
}

@Composable
private fun FileBrowserSelectionBar(
    count: Int,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onDetails: (() -> Unit)?,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        IconButton(onClick = onCopy, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Outlined.ContentCopy,
                contentDescription = stringResource(R.string.file_browser_copy),
                modifier = Modifier.size(20.dp),
            )
        }
        IconButton(onClick = onMove, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Outlined.DriveFileMove,
                contentDescription = stringResource(R.string.file_browser_move),
                modifier = Modifier.size(20.dp),
            )
        }
        IconButton(onClick = onShare, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Outlined.Share,
                contentDescription = stringResource(R.string.file_browser_share),
                modifier = Modifier.size(20.dp),
            )
        }
        if (onDetails != null) {
            IconButton(onClick = onDetails, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = stringResource(R.string.file_browser_selection_details),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.file_browser_delete),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
        }
        Box(modifier = Modifier.weight(1f))
        IconButton(onClick = onCancel, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = stringResource(R.string.file_browser_cancel),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun FileBrowserClipboardBar(
    clipboard: FileClipboard,
    onPaste: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
            .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(
                if (clipboard.mode == FileTransferMode.MOVE) {
                    R.string.file_browser_clipboard_move
                } else {
                    R.string.file_browser_clipboard_copy
                },
                clipboard.paths.size,
            ),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onPaste) {
            Text(stringResource(R.string.file_browser_paste_here))
        }
        TextButton(onClick = onCancel) {
            Text(stringResource(R.string.file_browser_cancel))
        }
    }
}

@Composable
private fun FileBrowserFooter(
    count: Int,
    sizeBytes: Long,
    directorySizeBytes: Long?,
    directorySizeLoading: Boolean,
    directorySizeComputed: Boolean,
    truncated: Boolean,
    onComputeSize: () -> Unit,
) {
    HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val footerText = when {
            directorySizeLoading -> stringResource(
                R.string.file_browser_footer_count_size,
                count,
                stringResource(R.string.file_browser_size_calculating),
            )
            directorySizeComputed && directorySizeBytes != null -> stringResource(
                R.string.file_browser_footer_count_directory,
                count,
                FileBrowserFormat.size(directorySizeBytes),
            )
            directorySizeComputed -> stringResource(
                R.string.file_browser_footer_count_size,
                count,
                stringResource(R.string.file_browser_size_unavailable),
            )
            else -> stringResource(
                R.string.file_browser_footer_count_size,
                count,
                FileBrowserFormat.size(sizeBytes),
            )
        }
        Text(
            text = footerText,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (!directorySizeLoading && !directorySizeComputed) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .clickable(onClick = onComputeSize)
                    .padding(horizontal = 8.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.file_browser_compute_size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (truncated) {
            Text(
                text = stringResource(
                    R.string.file_browser_truncated,
                    FileBrowserShell.MAX_ENTRIES,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FileBrowserCard(
    modifier: Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val isDark = LocalAppDarkTheme.current
    val corner = 28.dp
    val shape = RoundedCornerShape(corner)
    val fallback = MaterialTheme.colorScheme.surface.copy(
        alpha = if (isDark) 0.88f else 0.82f,
    )
    val borderColor = if (isDark) Color(0x28FFFFFF) else Color(0x18000000)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val cornerPx = with(density) { corner.toPx() }
    val blurRadiusPx = with(density) { 57.dp.toPx().toInt() }

    Box(
        modifier = modifier
            .shadow(20.dp, shape, clip = false)
            .clip(shape)
            .border(0.5.dp, borderColor, shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(fallback),
        )
        LocalFrostedGlassBackdrop(
            modifier = Modifier.matchParentSize(),
            cornerRadiusPx = cornerPx,
            blurRadiusPx = blurRadiusPx,
            tintColor = if (isDark) 0x661C1C1E else 0x66F5F5F7,
            solidFallbackColor = fallback.toArgb(),
        )
        Box(modifier = Modifier.fillMaxSize(), content = content)
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    runCatching {
        manager.setPrimaryClip(ClipData.newPlainText("path", text))
    }
}

@Composable
private fun FileBrowserDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    confirmColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            shape = RoundedCornerShape(22.dp),
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(10.dp))
                content()
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.file_browser_cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = onConfirm) {
                        Text(text = confirmLabel, color = confirmColor)
                    }
                }
            }
        }
    }
}

@Composable
private fun FileBrowserNameDialog(
    initialName: String,
    title: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(initialName) { mutableStateOf(initialName) }
    FileBrowserDialog(
        title = title,
        onDismiss = onDismiss,
        confirmLabel = confirmLabel,
        onConfirm = {
            val name = value.trim()
            if (name.isNotEmpty()) onConfirm(name)
        },
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            singleLine = true,
            label = { Text(stringResource(R.string.file_browser_name_hint)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun FileBrowserSearchDialog(
    onDismiss: () -> Unit,
    onSearch: (String) -> Unit,
) {
    var value by remember { mutableStateOf("") }
    FileBrowserDialog(
        title = stringResource(R.string.file_browser_recursive_search),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.file_browser_go),
        onConfirm = {
            val query = value.trim()
            if (query.isNotEmpty()) onSearch(query)
        },
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            singleLine = true,
            label = { Text(stringResource(R.string.file_browser_search_hint_recursive)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private sealed interface FileBrowserNameTarget {
    data class Create(val parent: String) : FileBrowserNameTarget
    data class Rename(val entry: FileBrowserEntry) : FileBrowserNameTarget
}

@Composable
private fun FileBrowserPathDialog(
    initialPath: String,
    onDismiss: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    var value by remember(initialPath) { mutableStateOf(initialPath) }
    FileBrowserDialog(
        title = stringResource(R.string.file_browser_jump_path),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.file_browser_go),
        onConfirm = {
            val target = value.trimEnd('/').ifEmpty { "/" }
            if (value.isNotEmpty()) onNavigate(target)
        },
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            singleLine = true,
            label = { Text(stringResource(R.string.file_browser_jump_hint)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
