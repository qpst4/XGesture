package com.slideindex.app.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.zIndex
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.launcher.QuickLauncherDragTiming
import com.slideindex.app.launcher.QuickLauncherGridLogic
import com.slideindex.app.launcher.dissolveFolder
import com.slideindex.app.launcher.renameFolder
import com.slideindex.app.launcher.withFolderChildren
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import com.slideindex.app.launcher.QuickLauncherGridLogic.moveIndex
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.quicklauncher.QuickLauncherDeleteButtonLayer
import com.slideindex.app.ui.quicklauncher.QuickLauncherEditorToolbar
import com.slideindex.app.ui.quicklauncher.QuickLauncherFolderHeader
import com.slideindex.app.ui.quicklauncher.QuickLauncherGridCell
import com.slideindex.app.ui.quicklauncher.QuickLauncherPageGrid
import com.slideindex.app.ui.quicklauncher.QuickLauncherPageSwitcher
import com.slideindex.app.ui.quicklauncher.quickLauncherGridLabel
import com.slideindex.app.ui.miuix.MiuixConfirmDialog
import com.slideindex.app.ui.miuix.MiuixFormDialog
import com.slideindex.app.ui.miuix.MiuixLabeledTextField
import com.slideindex.app.util.QuickLauncherIconResolver
import kotlin.math.roundToInt

import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.slideindex.app.launcher.mergeIntoFolder

private const val PAGE_EDGE_RESISTANCE = 0.35f
private const val PAGE_COMMIT_FRACTION = 0.22f
private const val PAGE_EDGE_AUTO_PAGE_CELL_FRACTION = 0.12f
private const val PAGE_AUTO_TURN_COOLDOWN_MS = 400L
private const val HOVER_DEADZONE_DP = 14f

/** 图标并发解析数：串行在 OEM 主题图标设备上要等一分钟，太多又会把 CPU 占满影响交互。 */
private const val ICON_LOAD_CONCURRENCY = 4

@Composable
fun QuickLauncherGridEditor(
    settings: AppSettings,
    items: List<QuickLauncherItem>,
    appsByPackage: Map<String, AppInfo>,
    onItemsChange: (List<QuickLauncherItem>) -> Unit,
    onAdd: (folderIndex: Int) -> Unit,
    onInteractionActiveChange: (Boolean) -> Unit = {},
    showPageSwitcher: Boolean = true,
    gridColumnsOverride: Int? = null,
    gridRowsOverride: Int? = null,
) {
    var editMode by remember { mutableStateOf(false) }
    /** 正在编辑的文件夹在根列表中的索引；-1 表示面板根层级。 */
    var openFolderIndex by rememberSaveable { mutableStateOf(-1) }
    var renameDialogOpen by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var dissolveDialogOpen by remember { mutableStateOf(false) }
    var dragFromGlobal by remember { mutableIntStateOf(-1) }
    var dragSlotGlobal by remember { mutableIntStateOf(-1) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var dragStartInGrid by remember { mutableStateOf(Offset.Zero) }
    var hoverSlotGlobal by remember { mutableIntStateOf(-1) }
    var hoverAnchorPointer by remember { mutableStateOf(Offset.Zero) }
    var mergeTargetGlobal by remember { mutableIntStateOf(-1) }
    var currentPage by remember { mutableIntStateOf(0) }
    var pageSwipeOffsetPx by remember { mutableFloatStateOf(0f) }
    var lastAutoPageTurnMs by remember { mutableLongStateOf(0L) }
    var dragEdgePageZone by remember { mutableIntStateOf(0) }
    var dragEdgeAutoPageSeeded by remember { mutableStateOf(false) }
    // 索引可能因为面板被改写而失效（解散文件夹、删除文件夹、切换面板），失效时回落到面板根。
    val activeFolderIndex = openFolderIndex.takeIf { it in items.indices && items[it].isFolder } ?: -1
    val levelItems = if (activeFolderIndex >= 0) items[activeFolderIndex].folderItems() else items
    val columns = (gridColumnsOverride ?: settings.quickLauncherColumnsPerPage).coerceIn(
        2,
        com.slideindex.app.overlay.layout.QuickLauncherPanelLayoutEngine.MAX_COLUMNS,
    )
    val rows = (gridRowsOverride ?: settings.quickLauncherRowsPerPage).coerceIn(
        2,
        com.slideindex.app.overlay.layout.QuickLauncherPanelLayoutEngine.MAX_ROWS,
    )
    val iconSizeDp = settings.quickLauncherDisplay.iconSizeDp
    val iconShape = settings.quickLauncherDisplay.iconShape
    val showLabels = settings.quickLauncherDisplay.showLabels
    val pageSize = QuickLauncherGridLogic.pageSize(columns, rows)
    val pageCount = QuickLauncherGridLogic.pageCount(levelItems.size, pageSize)
    val density = LocalDensity.current
    val gridGapPx = with(density) { 8.dp.toPx() }
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val actionIconTintArgb = MaterialTheme.colorScheme.onSurface.toArgb()
    val rootItemsState = rememberUpdatedState(items)

    /** 把当前层级的改动写回面板：根层级直接写，文件夹层级回写到所属文件夹。 */
    fun writeLevelItems(newLevelItems: List<QuickLauncherItem>) {
        val index = activeFolderIndex
        if (index < 0) {
            onItemsChange(newLevelItems)
            return
        }
        val root = rootItemsState.value
        if (index !in root.indices || !root[index].isFolder) return
        onItemsChange(root.withFolderChildren(index, newLevelItems))
    }

    fun resetDragState() {
        dragFromGlobal = -1
        dragSlotGlobal = -1
        hoverSlotGlobal = -1
        mergeTargetGlobal = -1
        dragOffsetX = 0f
        dragOffsetY = 0f
        pageSwipeOffsetPx = 0f
    }

    fun enterFolder(index: Int) {
        // 文件夹只有一层，已经在文件夹内时不再下钻。
        if (activeFolderIndex >= 0) return
        if (index !in items.indices || !items[index].isFolder) return
        renameDialogOpen = false
        dissolveDialogOpen = false
        openFolderIndex = index
        currentPage = 0
        resetDragState()
        haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
    }

    fun exitFolder() {
        if (openFolderIndex < 0) return
        renameDialogOpen = false
        dissolveDialogOpen = false
        openFolderIndex = -1
        currentPage = 0
        resetDragState()
    }

    var iconBitmapCache by remember { mutableStateOf<Map<Int, android.graphics.Bitmap?>>(emptyMap()) }
    LaunchedEffect(
        levelItems,
        appsByPackage,
        actionIconTintArgb,
        settings.activityShortcuts,
        settings.shellCommands,
        // 图标库里改完应用图标要重新取图，否则编辑页一直显示改动前的缓存
        com.slideindex.app.ui.GlobalIconStore.revision,
    ) {
        iconBitmapCache = withContext(Dispatchers.IO) {
            // 受限并发：串行解析在 OEM 主题图标设备上要等一分钟，全并发又会把 CPU 打满。
            val permits = Semaphore(ICON_LOAD_CONCURRENCY)
            coroutineScope {
                levelItems.mapIndexed { index, item ->
                    async {
                        permits.withPermit {
                            index to QuickLauncherIconResolver.iconBitmap(
                                item = item,
                                appsByPackage = appsByPackage,
                                context = context,
                                actionIconTintArgb = actionIconTintArgb,
                                activityShortcuts = settings.activityShortcuts,
                                shellCommands = settings.shellCommands,
                            )
                        }
                    }
                }.awaitAll().toMap()
            }
        }
    }
    val itemsState = rememberUpdatedState(levelItems)

    LaunchedEffect(pageCount, columns, rows) {
        currentPage = currentPage.coerceIn(0, pageCount - 1)
    }

    LaunchedEffect(openFolderIndex, items) {
        if (openFolderIndex >= 0 && (openFolderIndex !in items.indices || !items[openFolderIndex].isFolder)) {
            exitFolder()
        }
    }

    LaunchedEffect(editMode, dragFromGlobal) {
        onInteractionActiveChange(editMode || dragFromGlobal >= 0)
    }

    LaunchedEffect(hoverSlotGlobal, hoverAnchorPointer, dragFromGlobal) {
        if (dragFromGlobal < 0 || hoverSlotGlobal < 0 || hoverSlotGlobal == dragFromGlobal) {
            mergeTargetGlobal = -1
            return@LaunchedEffect
        }
        // 文件夹不能再嵌套文件夹，文件夹内部禁用拖拽合并。
        if (activeFolderIndex >= 0) {
            mergeTargetGlobal = -1
            return@LaunchedEffect
        }
        val currentItems = itemsState.value
        val draggedItem = currentItems.getOrNull(dragFromGlobal)
        val targetItem = currentItems.getOrNull(hoverSlotGlobal)
        if (draggedItem == null || draggedItem.isFolder || targetItem == null) {
            mergeTargetGlobal = -1
            return@LaunchedEffect
        }
        delay(QuickLauncherDragTiming.FOLDER_MERGE_DWELL_MS)
        mergeTargetGlobal = hoverSlotGlobal
        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
            if (activeFolderIndex >= 0) {
                QuickLauncherFolderHeader(
                    title = quickLauncherGridLabel(context, items[activeFolderIndex], appsByPackage),
                    onBack = { exitFolder() },
                    onRename = {
                        renameText = items[activeFolderIndex].label
                        renameDialogOpen = true
                    },
                    onDissolve = { dissolveDialogOpen = true },
                )
                if (levelItems.isEmpty()) {
                    Text(
                        text = stringResource(R.string.quick_launcher_folder_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
                    )
                }
            }
            if (showPageSwitcher) {
                QuickLauncherPageSwitcher(
                    currentPage = currentPage,
                    pageCount = pageCount,
                    onPrevious = {
                        if (currentPage > 0) {
                            currentPage -= 1
                            pageSwipeOffsetPx = 0f
                        }
                    },
                    onNext = {
                        if (currentPage < pageCount - 1) {
                            currentPage += 1
                            pageSwipeOffsetPx = 0f
                        }
                    },
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clipToBounds(),
                ) {
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val pageWidthPx = with(density) { maxWidth.toPx().coerceAtLeast(1f) }
                        val pageStartIndex = currentPage * pageSize
                        val cellWidthPx = ((pageWidthPx - gridGapPx * (columns - 1)) / columns)
                            .coerceAtLeast(1f)
                        val cellHeightPx = (cellWidthPx * 0.75f).coerceIn(
                            with(density) { 76.dp.toPx() },
                            with(density) { 86.dp.toPx() },
                        )
                        val stepX = cellWidthPx + gridGapPx
                        val stepY = cellHeightPx + gridGapPx
                        val gridTotalHeightPx = cellHeightPx * rows + gridGapPx * (rows - 1)
                        val gridHeightDp = with(density) { gridTotalHeightPx.toDp() }
                        val cellHeightDp = with(density) { cellHeightPx.toDp() }
                        val currentPageState = rememberUpdatedState(currentPage)
                        val itemsState = rememberUpdatedState(levelItems)
                        val pageSizeState = rememberUpdatedState(pageSize)

                        val hoverDeadzonePx = with(density) { HOVER_DEADZONE_DP.dp.toPx() }

                        fun finishPageSwipe() {
                            val threshold = pageWidthPx * PAGE_COMMIT_FRACTION
                            val offset = pageSwipeOffsetPx
                            val delta = when {
                                offset <= -threshold && currentPage < pageCount - 1 -> 1
                                offset >= threshold && currentPage > 0 -> -1
                                else -> 0
                            }
                            if (delta != 0) {
                                currentPage += delta
                            }
                            pageSwipeOffsetPx = 0f
                        }

                        fun dragEdgeZone(pointerX: Float): Int {
                            val edgeInset = cellWidthPx * PAGE_EDGE_AUTO_PAGE_CELL_FRACTION
                            val lastColStart = (columns - 1).coerceAtLeast(0) * stepX
                            val rightEdgeStart = lastColStart + cellWidthPx - edgeInset
                            return when {
                                pointerX <= edgeInset -> -1
                                pointerX >= rightEdgeStart -> 1
                                else -> 0
                            }
                        }

                        fun resetDragEdgeAutoPage() {
                            dragEdgeAutoPageSeeded = false
                            dragEdgePageZone = 0
                        }

                        fun tryAutoPageTurn(pointerX: Float) {
                            if (!showPageSwitcher || pageCount <= 1 || dragFromGlobal < 0) return
                            val zone = dragEdgeZone(pointerX)
                            if (!dragEdgeAutoPageSeeded) {
                                dragEdgeAutoPageSeeded = true
                                dragEdgePageZone = zone
                                return
                            }
                            val prevZone = dragEdgePageZone
                            dragEdgePageZone = zone
                            if (zone == 0 || zone == prevZone) return
                            val now = System.currentTimeMillis()
                            if (now - lastAutoPageTurnMs < PAGE_AUTO_TURN_COOLDOWN_MS) return
                            val page = currentPageState.value
                            val delta = when (zone) {
                                -1 -> if (page > 0) -1 else 0
                                1 -> if (page < pageCount - 1) 1 else 0
                                else -> 0
                            }
                            if (delta == 0) return
                            currentPage = page + delta
                            lastAutoPageTurnMs = now
                            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(gridHeightDp)
                                .pointerInput(currentPage, pageCount, editMode, dragFromGlobal, pageWidthPx, showPageSwitcher) {
                                    if (!showPageSwitcher || editMode || dragFromGlobal >= 0 || pageCount <= 1) {
                                        return@pointerInput
                                    }
                                    detectHorizontalDragGestures(
                                        onDragEnd = { finishPageSwipe() },
                                        onDragCancel = { pageSwipeOffsetPx = 0f },
                                        onHorizontalDrag = { change, dragAmount ->
                                            change.consume()
                                            val nextOffset = pageSwipeOffsetPx + dragAmount
                                            pageSwipeOffsetPx = when {
                                                currentPage == 0 && nextOffset > 0f ->
                                                    pageSwipeOffsetPx + dragAmount * PAGE_EDGE_RESISTANCE
                                                currentPage >= pageCount - 1 && nextOffset < 0f ->
                                                    pageSwipeOffsetPx + dragAmount * PAGE_EDGE_RESISTANCE
                                                else -> nextOffset
                                            }.coerceIn(-pageWidthPx, pageWidthPx)
                                        },
                                    )
                                },
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { translationX = pageSwipeOffsetPx },
                            ) {
                                QuickLauncherPageGrid(
                                    pageStart = pageStartIndex,
                                    columns = columns,
                                    rows = rows,
                                    pageSize = pageSize,
                                    items = levelItems,
                                    appsByPackage = appsByPackage,
                                    iconBitmapCache = iconBitmapCache,
                                    actionIconTintArgb = actionIconTintArgb,
                                    editMode = editMode,
                                    dragFromGlobal = dragFromGlobal,
                                    dragSlotGlobal = dragSlotGlobal,
                                    mergeTargetGlobal = mergeTargetGlobal,
                                    iconSizeDp = iconSizeDp,
                                    iconShape = iconShape,
                                    showLabels = showLabels,
                                    cellHeightDp = cellHeightDp,
                                    shellCommands = settings.shellCommands,
                                    onEnterFolder = if (!editMode && activeFolderIndex < 0) {
                                        { index -> enterFolder(index) }
                                    } else {
                                        null
                                    },
                                )
                            }

                            if (pageSwipeOffsetPx < 0f && currentPage < pageCount - 1) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            translationX = pageWidthPx + pageSwipeOffsetPx
                                        },
                                ) {
                                    QuickLauncherPageGrid(
                                        pageStart = (currentPage + 1) * pageSize,
                                        columns = columns,
                                        rows = rows,
                                        pageSize = pageSize,
                                        items = levelItems,
                                        appsByPackage = appsByPackage,
                                        iconBitmapCache = iconBitmapCache,
                                        actionIconTintArgb = actionIconTintArgb,
                                        editMode = false,
                                        dragFromGlobal = -1,
                                        dragSlotGlobal = -1,
                                        mergeTargetGlobal = -1,
                                        iconSizeDp = iconSizeDp,
                                        iconShape = iconShape,
                                        showLabels = showLabels,
                                        cellHeightDp = cellHeightDp,
                                        shellCommands = settings.shellCommands,
                                    )
                                }
                            }

                            if (pageSwipeOffsetPx > 0f && currentPage > 0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            translationX = pageSwipeOffsetPx - pageWidthPx
                                        },
                                ) {
                                    QuickLauncherPageGrid(
                                        pageStart = (currentPage - 1) * pageSize,
                                        columns = columns,
                                        rows = rows,
                                        pageSize = pageSize,
                                        items = levelItems,
                                        appsByPackage = appsByPackage,
                                        iconBitmapCache = iconBitmapCache,
                                        actionIconTintArgb = actionIconTintArgb,
                                        editMode = false,
                                        dragFromGlobal = -1,
                                        dragSlotGlobal = -1,
                                        mergeTargetGlobal = -1,
                                        iconSizeDp = iconSizeDp,
                                        iconShape = iconShape,
                                        showLabels = showLabels,
                                        cellHeightDp = cellHeightDp,
                                        shellCommands = settings.shellCommands,
                                    )
                                }
                            }

                            if (editMode) {
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .zIndex(1f)
                                        .pointerInput(
                                            editMode,
                                            columns,
                                            rows,
                                            pageSize,
                                            pageCount,
                                            cellWidthPx,
                                            cellHeightPx,
                                            gridGapPx,
                                            pageWidthPx,
                                        ) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = { start ->
                                                    dragStartInGrid = start
                                                    val page = currentPageState.value
                                                    val pageStart = page * pageSizeState.value
                                                    val localIndex = QuickLauncherGridLogic.localSlotAt(
                                                        x = start.x,
                                                        y = start.y,
                                                        columns = columns,
                                                        rows = rows,
                                                        pageSize = pageSize,
                                                        cellWidthPx = cellWidthPx,
                                                        cellHeightPx = cellHeightPx,
                                                        gapPx = gridGapPx,
                                                    )
                                                    val globalIndex = pageStart + localIndex
                                                    if (globalIndex in itemsState.value.indices) {
                                                        dragFromGlobal = globalIndex
                                                        dragSlotGlobal = globalIndex
                                                        dragOffsetX = 0f
                                                        dragOffsetY = 0f
                                                        lastAutoPageTurnMs = 0L
                                                        resetDragEdgeAutoPage()
                                                        hoverSlotGlobal = globalIndex
                                                        hoverAnchorPointer = start
                                                        mergeTargetGlobal = -1
                                                        haptic.performHapticFeedback(
                                                            HapticFeedbackType.GestureThresholdActivate,
                                                        )
                                                    }
                                                },
                                                onDrag = { change, dragAmount ->
                                                    if (dragFromGlobal < 0) {
                                                        return@detectDragGesturesAfterLongPress
                                                    }
                                                    change.consume()
                                                    dragOffsetX += dragAmount.x
                                                    dragOffsetY += dragAmount.y
                                                    val pointerX = dragStartInGrid.x + dragOffsetX
                                                    val pointerY = dragStartInGrid.y + dragOffsetY
                                                    tryAutoPageTurn(pointerX)
                                                    val activePageStart =
                                                        currentPageState.value * pageSizeState.value
                                                    val localSlot = QuickLauncherGridLogic.localSlotAt(
                                                        x = pointerX,
                                                        y = pointerY,
                                                        columns = columns,
                                                        rows = rows,
                                                        pageSize = pageSize,
                                                        cellWidthPx = cellWidthPx,
                                                        cellHeightPx = cellHeightPx,
                                                        gapPx = gridGapPx,
                                                    )
                                                    val slotGlobal = QuickLauncherGridLogic.dragSlotGlobal(
                                                        pageStart = activePageStart,
                                                        localSlot = localSlot,
                                                        pageSize = pageSize,
                                                    )
                                                    dragSlotGlobal = slotGlobal
                                                    val currentPointer = Offset(pointerX, pointerY)
                                                    val moveDist = (currentPointer - hoverAnchorPointer).getDistance()
                                                    if (moveDist > hoverDeadzonePx || slotGlobal != hoverSlotGlobal) {
                                                        hoverAnchorPointer = currentPointer
                                                        hoverSlotGlobal = slotGlobal
                                                        if (mergeTargetGlobal >= 0) {
                                                            mergeTargetGlobal = -1
                                                        }
                                                    }
                                                },
                                                onDragEnd = {
                                                    if (mergeTargetGlobal >= 0 && dragFromGlobal >= 0 && mergeTargetGlobal != dragFromGlobal) {
                                                        val currentItems = itemsState.value
                                                        val newItems = currentItems.mergeIntoFolder(
                                                            from = dragFromGlobal,
                                                            target = mergeTargetGlobal,
                                                        )
                                                        if (newItems != currentItems) {
                                                            writeLevelItems(newItems)
                                                        }
                                                        haptic.performHapticFeedback(
                                                            HapticFeedbackType.GestureEnd,
                                                        )
                                                    } else if (dragFromGlobal >= 0 && dragSlotGlobal >= 0) {
                                                        val currentItems = itemsState.value
                                                        val insertIndex =
                                                            QuickLauncherGridLogic.dragInsertIndex(
                                                                dragSlotGlobal = dragSlotGlobal,
                                                                itemCount = currentItems.size,
                                                            )
                                                        if (dragFromGlobal != insertIndex) {
                                                            writeLevelItems(
                                                                currentItems.moveIndex(
                                                                    dragFromGlobal,
                                                                    insertIndex,
                                                                ),
                                                            )
                                                        }
                                                        haptic.performHapticFeedback(
                                                            HapticFeedbackType.GestureEnd,
                                                        )
                                                    }
                                                    dragFromGlobal = -1
                                                    dragSlotGlobal = -1
                                                    hoverSlotGlobal = -1
                                                    mergeTargetGlobal = -1
                                                    dragOffsetX = 0f
                                                    dragOffsetY = 0f
                                                    resetDragEdgeAutoPage()
                                                },
                                                onDragCancel = {
                                                    dragFromGlobal = -1
                                                    dragSlotGlobal = -1
                                                    hoverSlotGlobal = -1
                                                    mergeTargetGlobal = -1
                                                    dragOffsetX = 0f
                                                    dragOffsetY = 0f
                                                    resetDragEdgeAutoPage()
                                                },
                                            )
                                        },
                                )
                                QuickLauncherDeleteButtonLayer(
                                    columns = columns,
                                    pageSize = pageSize,
                                    pageStartIndex = pageStartIndex,
                                    items = levelItems,
                                    dragFromGlobal = dragFromGlobal,
                                    dragSlotGlobal = dragSlotGlobal,
                                    stepX = stepX,
                                    stepY = stepY,
                                    cellWidthPx = cellWidthPx,
                                    density = density,
                                    zIndex = if (dragFromGlobal >= 0) 0.5f else 3f,
                                    onRemoveAt = { globalIndex ->
                                        writeLevelItems(
                                            itemsState.value.filterIndexed { i, _ -> i != globalIndex },
                                        )
                                    },
                                )
                            }

                            if (editMode && dragFromGlobal >= 0) {
                                val draggedItem = levelItems.getOrNull(dragFromGlobal)
                                if (draggedItem != null) {
                                    val pointerX = dragStartInGrid.x + dragOffsetX
                                    val pointerY = dragStartInGrid.y + dragOffsetY
                                    val onCurrentPage = dragFromGlobal in pageStartIndex until (pageStartIndex + pageSize)
                                    val floaterX = if (onCurrentPage) {
                                        val localFrom = dragFromGlobal - pageStartIndex
                                        val col = localFrom % columns
                                        col * stepX + dragOffsetX
                                    } else {
                                        pointerX - cellWidthPx / 2f
                                    }
                                    val floaterY = if (onCurrentPage) {
                                        val localFrom = dragFromGlobal - pageStartIndex
                                        val row = localFrom / columns
                                        row * stepY + dragOffsetY
                                    } else {
                                        pointerY - cellHeightPx / 2f
                                    }
                                    val isMerging = mergeTargetGlobal >= 0
                                    QuickLauncherGridCell(
                                        modifier = Modifier
                                            .zIndex(2f)
                                            .offset {
                                                IntOffset(
                                                    floaterX.roundToInt(),
                                                    floaterY.roundToInt(),
                                                )
                                            }
                                            .width(with(density) { cellWidthPx.toDp() })
                                            .height(cellHeightDp)
                                            .graphicsLayer {
                                                if (isMerging) {
                                                    scaleX = 0.85f
                                                    scaleY = 0.85f
                                                    alpha = 0.88f
                                                }
                                            },
                                        item = draggedItem,
                                        appsByPackage = appsByPackage,
                                        iconBitmap = iconBitmapCache[dragFromGlobal],
                                        actionIconTintArgb = actionIconTintArgb,
                                        showEditBadge = false,
                                        iconSizeDp = iconSizeDp,
                                        iconShape = iconShape,
                                        showLabels = showLabels,
                                        shellCommands = settings.shellCommands,
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                QuickLauncherEditorToolbar(
                    editMode = editMode,
                    onAdd = { onAdd(activeFolderIndex) },
                    onToggleEdit = {
                        editMode = !editMode
                        resetDragState()
                    },
                )
            }

            if (activeFolderIndex >= 0) {
                MiuixFormDialog(
                    show = renameDialogOpen,
                    onDismissRequest = { renameDialogOpen = false },
                    title = stringResource(R.string.quick_launcher_folder_rename),
                    confirmText = stringResource(R.string.shell_panel_save),
                    confirmEnabled = renameText.isNotBlank(),
                    onConfirm = {
                        onItemsChange(
                            rootItemsState.value.renameFolder(activeFolderIndex, renameText.trim()),
                        )
                    },
                ) {
                    MiuixLabeledTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        label = stringResource(R.string.quick_launcher_folder_name),
                    )
                }

                MiuixConfirmDialog(
                    show = dissolveDialogOpen,
                    onDismissRequest = { dissolveDialogOpen = false },
                    title = stringResource(R.string.quick_launcher_folder_dissolve),
                    message = stringResource(R.string.quick_launcher_folder_dissolve_message),
                    confirmText = stringResource(R.string.quick_launcher_folder_dissolve),
                    onConfirm = {
                        onItemsChange(rootItemsState.value.dissolveFolder(activeFolderIndex))
                        exitFolder()
                    },
                )
            }
        }
    }
