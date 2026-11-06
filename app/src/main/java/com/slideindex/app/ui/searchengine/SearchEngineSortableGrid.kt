package com.slideindex.app.ui.searchengine

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.slideindex.app.launcher.QuickLauncherGridLogic
import com.slideindex.app.launcher.QuickLauncherGridLogic.moveIndex
import com.slideindex.app.overlay.pickresult.SearchEngineIcon
import com.slideindex.app.settings.SearchEngineConfig
import com.slideindex.app.settings.SearchEngineIconShape
import kotlin.math.roundToInt

private const val PAGE_EDGE_RESISTANCE = 0.35f
private const val PAGE_COMMIT_FRACTION = 0.22f
private const val PAGE_EDGE_AUTO_PAGE_CELL_FRACTION = 0.12f
private const val PAGE_AUTO_TURN_COOLDOWN_MS = 850L

private fun searchIconSizeForColumns(columns: Int): Dp = when {
    columns >= 7 -> 32.dp
    columns >= 6 -> 36.dp
    else -> 40.dp
}

private fun searchGridContentHeight(rows: Int, showLabels: Boolean, columns: Int): Dp {
    val rowCount = rows.coerceIn(1, 4)
    val iconSize = searchIconSizeForColumns(columns)
    val labelHeight = if (showLabels) 18.dp else 0.dp
    val itemHeight = iconSize + labelHeight + 6.dp
    val rowGap = 10.dp * (rowCount - 1).coerceAtLeast(0)
    return itemHeight * rowCount + rowGap
}

@Composable
fun SearchEngineSortableGrid(
    engines: List<SearchEngineConfig>,
    columns: Int,
    rows: Int,
    showLabels: Boolean,
    onOrderChange: (List<SearchEngineConfig>) -> Unit,
    modifier: Modifier = Modifier,
    iconShape: SearchEngineIconShape = SearchEngineIconShape.DEFAULT,
) {
    if (engines.isEmpty()) return

    val columnCount = columns.coerceIn(3, 8)
    val rowCount = rows.coerceIn(1, 4)
    val pageSize = QuickLauncherGridLogic.pageSize(columnCount, rowCount)
    val pageCount = QuickLauncherGridLogic.pageCount(engines.size, pageSize)
    val gridHeight = searchGridContentHeight(rowCount, showLabels, columnCount)
    val iconSize = searchIconSizeForColumns(columnCount)

    var currentPage by remember { mutableIntStateOf(0) }
    var dragFromGlobal by remember { mutableIntStateOf(-1) }
    var dragSlotGlobal by remember { mutableIntStateOf(-1) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var dragStartInGrid by remember { mutableStateOf(Offset.Zero) }
    var pageSwipeOffsetPx by remember { mutableFloatStateOf(0f) }
    var lastAutoPageTurnMs by remember { mutableLongStateOf(0L) }
    var dragEdgePageZone by remember { mutableIntStateOf(0) }
    var dragEdgeAutoPageSeeded by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val gridGapPx = with(density) { 10.dp.toPx() }
    val haptic = LocalHapticFeedback.current
    val enginesState = rememberUpdatedState(engines)
    val currentPageState = rememberUpdatedState(currentPage)
    val pageSizeState = rememberUpdatedState(pageSize)

    LaunchedEffect(pageCount) {
        currentPage = currentPage.coerceIn(0, pageCount - 1)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (pageCount > 1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        if (currentPage > 0) {
                            currentPage -= 1
                            pageSwipeOffsetPx = 0f
                        }
                    },
                    enabled = currentPage > 0,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = null,
                    )
                }
                Text(
                    text = "${currentPage + 1} / $pageCount",
                    style = MaterialTheme.typography.labelLarge,
                )
                IconButton(
                    onClick = {
                        if (currentPage < pageCount - 1) {
                            currentPage += 1
                            pageSwipeOffsetPx = 0f
                        }
                    },
                    enabled = currentPage < pageCount - 1,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(gridHeight)
                .clip(RoundedCornerShape(12.dp))
                .clipToBounds(),
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val pageWidthPx = with(density) { maxWidth.toPx().coerceAtLeast(1f) }
                val pageStartIndex = currentPage * pageSize
                val cellWidthPx = ((pageWidthPx - gridGapPx * (columnCount - 1)) / columnCount)
                    .coerceAtLeast(1f)
                val labelHeight = if (showLabels) 14.dp else 0.dp
                val cellHeightPx = with(density) {
                    (iconSize + labelHeight + 4.dp).toPx()
                }
                val stepX = cellWidthPx + gridGapPx
                val stepY = cellHeightPx + gridGapPx

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
                    val lastColStart = (columnCount - 1).coerceAtLeast(0) * stepX
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
                    if (pageCount <= 1 || dragFromGlobal < 0) return
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
                        .fillMaxSize()
                        .pointerInput(currentPage, pageCount, dragFromGlobal, pageWidthPx) {
                            if (dragFromGlobal >= 0 || pageCount <= 1) return@pointerInput
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
                        SearchEngineSortableGridPage(
                            engines = engines,
                            pageStart = pageStartIndex,
                            columns = columnCount,
                            rows = rowCount,
                            pageSize = pageSize,
                            showLabels = showLabels,
                            iconSize = iconSize,
                            iconShape = iconShape,
                            dragFromGlobal = dragFromGlobal,
                            dragSlotGlobal = dragSlotGlobal,
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .zIndex(1f)
                        .pointerInput(
                            columnCount,
                            rowCount,
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
                                        columns = columnCount,
                                        rows = rowCount,
                                        pageSize = pageSize,
                                        cellWidthPx = cellWidthPx,
                                        cellHeightPx = cellHeightPx,
                                        gapPx = gridGapPx,
                                    )
                                    val globalIndex = pageStart + localIndex
                                    if (globalIndex in enginesState.value.indices) {
                                        dragFromGlobal = globalIndex
                                        dragSlotGlobal = globalIndex
                                        dragOffsetX = 0f
                                        dragOffsetY = 0f
                                        lastAutoPageTurnMs = 0L
                                        resetDragEdgeAutoPage()
                                        haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    if (dragFromGlobal < 0) return@detectDragGesturesAfterLongPress
                                    change.consume()
                                    dragOffsetX += dragAmount.x
                                    dragOffsetY += dragAmount.y
                                    val pointerX = dragStartInGrid.x + dragOffsetX
                                    val pointerY = dragStartInGrid.y + dragOffsetY
                                    tryAutoPageTurn(pointerX)
                                    val activePageStart = currentPageState.value * pageSizeState.value
                                    val localSlot = QuickLauncherGridLogic.localSlotAt(
                                        x = pointerX,
                                        y = pointerY,
                                        columns = columnCount,
                                        rows = rowCount,
                                        pageSize = pageSize,
                                        cellWidthPx = cellWidthPx,
                                        cellHeightPx = cellHeightPx,
                                        gapPx = gridGapPx,
                                    )
                                    dragSlotGlobal = QuickLauncherGridLogic.dragSlotGlobal(
                                        pageStart = activePageStart,
                                        localSlot = localSlot,
                                        pageSize = pageSize,
                                    )
                                },
                                onDragEnd = {
                                    if (dragFromGlobal >= 0 && dragSlotGlobal >= 0) {
                                        val currentEngines = enginesState.value
                                        val insertIndex = QuickLauncherGridLogic.dragInsertIndex(
                                            dragSlotGlobal = dragSlotGlobal,
                                            itemCount = currentEngines.size,
                                        )
                                        if (dragFromGlobal != insertIndex) {
                                            onOrderChange(
                                                currentEngines.moveIndex(dragFromGlobal, insertIndex),
                                            )
                                        }
                                    }
                                    dragFromGlobal = -1
                                    dragSlotGlobal = -1
                                    dragOffsetX = 0f
                                    dragOffsetY = 0f
                                    resetDragEdgeAutoPage()
                                    haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                },
                                onDragCancel = {
                                    dragFromGlobal = -1
                                    dragSlotGlobal = -1
                                    dragOffsetX = 0f
                                    dragOffsetY = 0f
                                    resetDragEdgeAutoPage()
                                },
                            )
                        },
                )

                if (dragFromGlobal >= 0) {
                    val draggedEngine = engines.getOrNull(dragFromGlobal)
                    if (draggedEngine != null) {
                        val pointerX = dragStartInGrid.x + dragOffsetX
                        val pointerY = dragStartInGrid.y + dragOffsetY
                        val onCurrentPage = dragFromGlobal in pageStartIndex until (pageStartIndex + pageSize)
                        val floaterX = if (onCurrentPage) {
                            val localFrom = dragFromGlobal - pageStartIndex
                            val col = localFrom % columnCount
                            col * stepX + dragOffsetX
                        } else {
                            pointerX - cellWidthPx / 2f
                        }
                        val floaterY = if (onCurrentPage) {
                            val localFrom = dragFromGlobal - pageStartIndex
                            val row = localFrom / columnCount
                            row * stepY + dragOffsetY
                        } else {
                            pointerY - cellHeightPx / 2f
                        }
                        SearchEngineSortableGridCell(
                            engine = draggedEngine,
                            showLabel = showLabels,
                            iconSize = iconSize,
                            iconShape = iconShape,
                            modifier = Modifier
                                .zIndex(2f)
                                .offset {
                                    IntOffset(
                                        floaterX.roundToInt(),
                                        floaterY.roundToInt(),
                                    )
                                }
                                .width(with(density) { cellWidthPx.toDp() })
                                .graphicsLayer {
                                    scaleX = 1.08f
                                    scaleY = 1.08f
                                    alpha = 0.88f
                                    shadowElevation = 16f
                                },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchEngineSortableGridPage(
    engines: List<SearchEngineConfig>,
    pageStart: Int,
    columns: Int,
    rows: Int,
    pageSize: Int,
    showLabels: Boolean,
    iconSize: Dp,
    iconShape: SearchEngineIconShape,
    dragFromGlobal: Int,
    dragSlotGlobal: Int,
) {
    val displayMapping = remember(engines.size, dragFromGlobal, dragSlotGlobal, pageStart, pageSize) {
        QuickLauncherGridLogic.displayMappingForPage(
            itemCount = engines.size,
            dragFrom = dragFromGlobal,
            dragSlotGlobal = dragSlotGlobal,
            pageStart = pageStart,
            pageSize = pageSize,
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        for (row in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                for (col in 0 until columns) {
                    val cellIndex = row * columns + col
                    if (cellIndex >= pageSize) continue
                    Box(modifier = Modifier.weight(1f)) {
                        val originalIndex = displayMapping.getOrNull(cellIndex)
                        val engine = originalIndex?.let { engines.getOrNull(it) }
                        if (engine != null) {
                            SearchEngineSortableGridCell(
                                engine = engine,
                                showLabel = showLabels,
                                iconSize = iconSize,
                                iconShape = iconShape,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchEngineSortableGridCell(
    engine: SearchEngineConfig,
    showLabel: Boolean,
    iconSize: Dp,
    iconShape: SearchEngineIconShape = SearchEngineIconShape.DEFAULT,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SearchEngineIcon(
            engine = engine,
            modifier = Modifier.size(iconSize),
            iconShape = iconShape,
        )
        if (showLabel) {
            Text(
                text = engine.name,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            )
        }
    }
}
