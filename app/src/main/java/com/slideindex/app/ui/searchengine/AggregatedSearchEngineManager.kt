package com.slideindex.app.ui.searchengine

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.ui.res.stringResource
import com.slideindex.app.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import com.slideindex.app.overlay.pickresult.SearchEngineIcon
import com.slideindex.app.settings.SearchEngineConfig
import com.slideindex.app.settings.SearchEngineIconShape
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

/**
 * 聚合搜索管理工作台（桌面级交互与严格 MIUIX 规范）：
 * 1. 【根层手势永不断裂】：所有拖拽由根容器统一调度，跨页换位时 Composable 节点销毁绝不中断手势；
 * 2. 【多页实时平滑流转】：跨页拖拽时每页永远保持整齐满格，绝不留下空白孤立格子；
 * 3. 【首帧无闪现连续弹簧】：使用连续物理插值公式 (animX - targetX)，从数学根源彻底根绝首帧闪跳；
 * 4. 【双向平滑归位】：拖出后移回原位即可精准复原，绝不卡死；
 * 5. 【松手零延迟落盘】：拖拽过程中已就位，松手瞬间无重排卡顿；
 * 6. 【整页拖拽迟滞防抖】：15% 迟滞死区判定，彻底消除临界点震颤。
 */
@Composable
fun AggregatedSearchEngineManager(
    engines: List<SearchEngineConfig>,
    gridColumns: Int,
    gridRows: Int,
    showLabels: Boolean,
    onUpdateEngines: (List<SearchEngineConfig>) -> Unit,
    onAddEngine: () -> Unit,
    onEditEngine: (String) -> Unit,
    onDeleteEngine: (String) -> Unit,
    iconShape: SearchEngineIconShape = SearchEngineIconShape.DEFAULT,
    modifier: Modifier = Modifier,
    onDraggingStateChange: (Boolean) -> Unit = {},
    /**
     * 拖拽松手时回调，带上手指的窗口坐标；返回 true 表示这次拖拽被调用方消费
     * （例如丢进了「流转栏」卡片），本地列表不改动、图标回原位。
     */
    onDragFinishedWindow: (SearchEngineConfig, Offset?) -> Boolean = { _, _ -> false },
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    val columns = gridColumns.coerceIn(3, 8)
    val rows = gridRows.coerceIn(1, 4)
    val pageSize = columns * rows

    // 本地响应式数据源
    val workingEngines = remember { mutableStateListOf<SearchEngineConfig>() }
    LaunchedEffect(engines) {
        val partitioned = engines.filter { it.showInPickPanel } + engines.filter { !it.showInPickPanel }
        if (workingEngines != partitioned) {
            workingEngines.clear()
            workingEngines.addAll(partitioned)
        }
    }

    // --- 图标拖拽状态（采用项目成熟的 Preview 预览槽位架构，拖拽中绝不修改实体列表） ---
    var draggedEngine by remember { mutableStateOf<SearchEngineConfig?>(null) }
    var dragTargetIsHidden by remember { mutableStateOf(false) }
    var dragTargetSlot by remember { mutableIntStateOf(0) }
    var dragFingerLocalPos by remember { mutableStateOf(Offset.Zero) }

    /** 手指在窗口坐标系里的位置，用于判定是否落在「流转栏」卡片上。 */
    var lastFingerWindow by remember { mutableStateOf<Offset?>(null) }

    // --- 整页拖拽状态 ---
    var dragStartPageIndex by remember { mutableIntStateOf(-1) }
    var pageTotalDragY by remember { mutableFloatStateOf(0f) }
    var pageHoverTargetIndex by remember { mutableIntStateOf(-1) }
    var singleCardHeightPx by remember { mutableFloatStateOf(0f) }

    val iconSizeDp = calculateIconSize(columns)
    val iconSizePx = with(density) { iconSizeDp.toPx() }
    val horizontalSpacingDp = 6.dp
    val verticalSpacingDp = 10.dp
    val horizontalSpacingPx = with(density) { horizontalSpacingDp.toPx() }
    val verticalSpacingPx = with(density) { verticalSpacingDp.toPx() }

    // 动态派生预览序列（列表不实时修改，明确区分页面与隐藏池）
    val previewEngines by remember(workingEngines, draggedEngine, dragTargetIsHidden, dragTargetSlot) {
        derivedStateOf {
            val dragged = draggedEngine
            if (dragged == null) {
                workingEngines.toList()
            } else {
                val visible = workingEngines.filter { it.showInPickPanel && it.id != dragged.id }.toMutableList()
                val hidden = workingEngines.filter { !it.showInPickPanel && it.id != dragged.id }.toMutableList()

                if (dragTargetIsHidden) {
                    val updatedItem = dragged.copy(showInPickPanel = false)
                    val insertIdx = dragTargetSlot.coerceIn(0, hidden.size)
                    hidden.add(insertIdx, updatedItem)
                } else {
                    val updatedItem = dragged.copy(showInPickPanel = true)
                    val insertIdx = dragTargetSlot.coerceIn(0, visible.size)
                    visible.add(insertIdx, updatedItem)
                }

                visible + hidden
            }
        }
    }

    val visibleEngines = previewEngines.filter { it.showInPickPanel }
    val hiddenEngines = previewEngines.filter { !it.showInPickPanel }

    val pageCount = maxOf(1, (workingEngines.count { it.showInPickPanel } + pageSize - 1) / pageSize)
    val pages = remember(visibleEngines, pageSize, pageCount) {
        (0 until pageCount).map { pageIdx ->
            val start = pageIdx * pageSize
            val end = (start + pageSize).coerceAtMost(visibleEngines.size)
            if (start < visibleEngines.size) {
                visibleEngines.subList(start, end)
            } else {
                emptyList()
            }
        }
    }

    // 根容器全局定位
    var rootCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    // 槽位与网格 Bounds 记录表
    val itemSlotBounds = remember { HashMap<String, Rect>() }
    val pageHeaderBounds = remember { HashMap<Int, Rect>() }
    val pageCardBounds = remember { HashMap<Int, Rect>() }
    val pageGridBounds = remember { HashMap<Int, Rect>() }
    var hiddenCardBounds by remember { mutableStateOf(Rect.Zero) }
    var hiddenGridBounds by remember { mutableStateOf(Rect.Zero) }

    // 快捷管理弹窗
    var actionTargetEngine by remember { mutableStateOf<SearchEngineConfig?>(null) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { rootCoordinates = it }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { downOffset ->
                        val hitEngine = workingEngines.firstOrNull { itemSlotBounds[it.id]?.contains(downOffset) == true }
                        if (hitEngine != null) {
                            draggedEngine = hitEngine
                            dragTargetIsHidden = !hitEngine.showInPickPanel
                            dragTargetSlot = if (hitEngine.showInPickPanel) {
                                workingEngines.filter { it.showInPickPanel }.indexOfFirst { it.id == hitEngine.id }.coerceAtLeast(0)
                            } else {
                                workingEngines.filter { !it.showInPickPanel }.indexOfFirst { it.id == hitEngine.id }.coerceAtLeast(0)
                            }
                            dragFingerLocalPos = downOffset
                            lastFingerWindow = rootCoordinates?.localToWindow(downOffset)
                            onDraggingStateChange(true)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            return@detectDragGesturesAfterLongPress
                        }
                        val hitPage = pageHeaderBounds.entries.find { it.value.contains(downOffset) }?.key
                        if (hitPage != null) {
                            dragStartPageIndex = hitPage
                            pageHoverTargetIndex = hitPage
                            pageTotalDragY = 0f
                            onDraggingStateChange(true)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            return@detectDragGesturesAfterLongPress
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val dragged = draggedEngine
                        if (dragged != null) {
                            dragFingerLocalPos += dragAmount
                            val fingerPos = dragFingerLocalPos
                            lastFingerWindow = rootCoordinates?.localToWindow(fingerPos)

                            val isOverHidden = hiddenCardBounds != Rect.Zero &&
                                fingerPos.y >= (hiddenCardBounds.top - 16f) &&
                                fingerPos.y <= (hiddenCardBounds.bottom + 16f) &&
                                fingerPos.x >= (hiddenCardBounds.left - 20f) &&
                                fingerPos.x <= (hiddenCardBounds.right + 20f)

                            val safeCols = columns.coerceAtLeast(1)
                            val (newIsHidden, newSlot) = if (isOverHidden) {
                                // 位于已隐藏卡片内部
                                val grid = hiddenGridBounds.takeIf { it != Rect.Zero } ?: hiddenCardBounds
                                val gridWidth = grid.width.coerceAtLeast(1f)
                                val gridLeft = grid.left
                                val gridTop = grid.top
                                val cellWidth = ((gridWidth - (safeCols - 1) * horizontalSpacingPx) / safeCols).coerceAtLeast(1f)
                                val cellHeight = cellWidth * 1.25f
                                val stepX = cellWidth + horizontalSpacingPx
                                val stepY = cellHeight + verticalSpacingPx

                                val col = (0 until safeCols).minByOrNull { c ->
                                    kotlin.math.abs(fingerPos.x - (gridLeft + c * stepX + cellWidth / 2f))
                                } ?: 0
                                val row = ((fingerPos.y - gridTop) / stepY).toInt().coerceAtLeast(0)
                                val localSlot = row * safeCols + col
                                val otherHiddenCount = workingEngines.count { !it.showInPickPanel && it.id != dragged.id }
                                true to localSlot.coerceIn(0, otherHiddenCount)
                            } else {
                                // 位于可见页卡片内部
                                val targetPageIndex = if (pages.size <= 1) {
                                    0
                                } else {
                                    pageCardBounds.entries.minByOrNull { (_, rect) ->
                                        if (fingerPos.y in rect.top..rect.bottom) 0f
                                        else if (fingerPos.y < rect.top) rect.top - fingerPos.y
                                        else fingerPos.y - rect.bottom
                                    }?.key ?: 0
                                }

                                val grid = pageGridBounds[targetPageIndex] ?: pageCardBounds[targetPageIndex] ?: Rect.Zero
                                val gridWidth = grid.width.coerceAtLeast(1f)
                                val gridLeft = grid.left
                                val gridTop = grid.top
                                val cellWidth = ((gridWidth - (safeCols - 1) * horizontalSpacingPx) / safeCols).coerceAtLeast(1f)
                                val cellHeight = cellWidth * 1.25f
                                val stepX = cellWidth + horizontalSpacingPx
                                val stepY = cellHeight + verticalSpacingPx

                                val col = (0 until safeCols).minByOrNull { c ->
                                    kotlin.math.abs(fingerPos.x - (gridLeft + c * stepX + cellWidth / 2f))
                                } ?: 0
                                val row = (0 until rows).minByOrNull { r ->
                                    kotlin.math.abs(fingerPos.y - (gridTop + r * stepY + cellHeight / 2f))
                                } ?: 0

                                val localSlot = row * safeCols + col
                                val otherVisibleCount = workingEngines.count { it.showInPickPanel && it.id != dragged.id }
                                false to (targetPageIndex * pageSize + localSlot).coerceIn(0, otherVisibleCount)
                            }

                            if (newIsHidden != dragTargetIsHidden || newSlot != dragTargetSlot) {
                                dragTargetIsHidden = newIsHidden
                                dragTargetSlot = newSlot
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        } else if (dragStartPageIndex >= 0) {
                            pageTotalDragY += dragAmount.y
                            val h = if (singleCardHeightPx > 50f) singleCardHeightPx + with(density) { 12.dp.toPx() } else with(density) { 200.dp.toPx() }
                            val from = dragStartPageIndex
                            val currentTarget = pageHoverTargetIndex
                            val floatTarget = from + (pageTotalDragY / h)
                            val safeFloat = floatTarget.coerceIn(0f, (pages.size - 1).toFloat())
                            
                            val newTarget = when {
                                safeFloat > currentTarget + 0.55f -> (currentTarget + 1).coerceAtMost(pages.size - 1)
                                safeFloat < currentTarget - 0.55f -> (currentTarget - 1).coerceAtLeast(0)
                                else -> currentTarget
                            }
                            if (newTarget != pageHoverTargetIndex) {
                                pageHoverTargetIndex = newTarget
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    },
                    onDragEnd = {
                        val dragged = draggedEngine
                        // 「拖到流转栏卡片上松手」由调用方判定：返回 true 表示这一拖已被别处消费，
                        // 本地列表一个字都不改，图标自然回到原位。
                        val consumedByCaller = dragged != null &&
                            onDragFinishedWindow(dragged, lastFingerWindow)
                        if (dragged != null && !consumedByCaller) {
                            val finalEngines = previewEngines.mapIndexed { idx, it -> it.copy(sortOrder = idx) }
                            workingEngines.clear()
                            workingEngines.addAll(finalEngines)
                            onUpdateEngines(finalEngines)
                            haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                        }
                        draggedEngine = null
                        dragTargetIsHidden = false
                        dragTargetSlot = 0
                        lastFingerWindow = null
                        onDraggingStateChange(false)

                        if (dragStartPageIndex >= 0) {
                            val from = dragStartPageIndex
                            val to = pageHoverTargetIndex
                            if (from >= 0 && to >= 0 && from != to) {
                                movePageInWorkingList(workingEngines, from, to, pageSize)
                                onUpdateEngines(workingEngines.mapIndexed { idx, itm -> itm.copy(sortOrder = idx) })
                            }
                            dragStartPageIndex = -1
                            pageHoverTargetIndex = -1
                            pageTotalDragY = 0f
                            onDraggingStateChange(false)
                        }
                    },
                    onDragCancel = {
                        draggedEngine = null
                        dragTargetIsHidden = false
                        dragTargetSlot = 0
                        lastFingerWindow = null
                        dragStartPageIndex = -1
                        pageHoverTargetIndex = -1
                        pageTotalDragY = 0f
                        onDraggingStateChange(false)
                    },
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 1. 多页平铺卡片（每一页为独立 MiuixCard，外圈 12dp 标准边距）
            if (pages.isEmpty()) {
                MiuixCard(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        MiuixText(
                            text = stringResource(R.string.search_engine_empty_enabled),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                pages.forEachIndexed { pageIndex, pageEngines ->
                    val isBeingDragged = dragStartPageIndex == pageIndex

                    // 计算非拖拽页的弹簧避让位移动画
                    val cardHeight = if (singleCardHeightPx > 50f) singleCardHeightPx + with(density) { 12.dp.toPx() } else with(density) { 200.dp.toPx() }
                    val targetPageOffset = when {
                        isBeingDragged -> pageTotalDragY
                        dragStartPageIndex >= 0 -> {
                            val from = dragStartPageIndex
                            val to = pageHoverTargetIndex
                            if (from < to && pageIndex in (from + 1)..to) {
                                -cardHeight
                            } else if (from > to && pageIndex in to until from) {
                                cardHeight
                            } else {
                                0f
                            }
                        }
                        else -> 0f
                    }

                    val animatedPageOffset by animateIntOffsetAsState(
                        targetValue = if (isBeingDragged) {
                            IntOffset(0, pageTotalDragY.roundToInt())
                        } else {
                            IntOffset(0, targetPageOffset.roundToInt())
                        },
                        animationSpec = if (isBeingDragged) {
                            spring(dampingRatio = 1f, stiffness = 10000f)
                        } else {
                            spring(dampingRatio = 0.85f, stiffness = 400f)
                        },
                        label = "page_reorder_offset_$pageIndex",
                    )

                    MiuixCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(if (isBeingDragged) 10f else 1f)
                            .offset { animatedPageOffset }
                            .scale(if (isBeingDragged) 1.02f else 1f)
                            .shadow(if (isBeingDragged) 12.dp else 0.dp, shape = RoundedCornerShape(18.dp))
                            .onGloballyPositioned { coords ->
                                rootCoordinates?.let { root ->
                                    if (coords.isAttached) {
                                        val bounds = root.localBoundingBoxOf(coords)
                                        pageCardBounds[pageIndex] = bounds
                                        if (bounds.height > 50f && dragStartPageIndex < 0) {
                                            singleCardHeightPx = bounds.height
                                        }
                                    }
                                }
                            }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            // 标头行：长按标头拖拽整页，带 15% 迟滞死区与避让动效
                            PageHeaderRow(
                                pageIndex = pageIndex,
                                totalPages = pages.size,
                                engineCount = pageEngines.size,
                                pageSize = pageSize,
                                onPositioned = { coords ->
                                    rootCoordinates?.let { root ->
                                        if (coords.isAttached) {
                                            pageHeaderBounds[pageIndex] = root.localBoundingBoxOf(coords)
                                        }
                                    }
                                },
                            )

                            // 该页网格列表（桌面级弹簧平滑避让）
                            DesktopReorderableGrid(
                                engines = pageEngines,
                                columns = columns,
                                showLabels = showLabels,
                                iconSize = iconSizeDp,
                                iconShape = iconShape,
                                draggedEngineId = draggedEngine?.id,
                                rootCoordinates = rootCoordinates,
                                itemSlotBounds = itemSlotBounds,
                                fixedRows = rows,
                                onGridPositioned = { rect ->
                                    pageGridBounds[pageIndex] = rect
                                },
                                onEngineClick = { actionTargetEngine = it },
                            )
                        }
                    }
                }
            }

            // 2. 已隐藏卡片池
            MiuixCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coords ->
                        rootCoordinates?.let { root ->
                            if (coords.isAttached) {
                                hiddenCardBounds = root.localBoundingBoxOf(coords)
                            }
                        }
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MiuixText(
                            text = stringResource(R.string.search_engine_hidden_header),
                            style = MiuixTheme.textStyles.title4,
                            fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        MiuixText(
                            text = "${hiddenEngines.size}",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }

                    if (hiddenEngines.isEmpty()) {
                        MiuixText(
                            text = stringResource(R.string.search_engine_hidden_hint),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.6f),
                            modifier = Modifier.padding(vertical = 10.dp),
                        )
                    } else {
                        DesktopReorderableGrid(
                            engines = hiddenEngines,
                            columns = columns,
                            showLabels = showLabels,
                            iconSize = iconSizeDp,
                            iconShape = iconShape,
                            draggedEngineId = draggedEngine?.id,
                            rootCoordinates = rootCoordinates,
                            itemSlotBounds = itemSlotBounds,
                            fixedRows = null,
                            onGridPositioned = { rect ->
                                hiddenGridBounds = rect
                            },
                            onEngineClick = { actionTargetEngine = it },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        // 5. 手指正下方跟随浮层（中心绝对对准触点，带光晕与阴影）
        draggedEngine?.let { engine ->
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (dragFingerLocalPos.x - iconSizePx / 2f).roundToInt(),
                            y = (dragFingerLocalPos.y - iconSizePx / 2f).roundToInt(),
                        )
                    }
                    .size(iconSizeDp)
                    .zIndex(999f)
                    .scale(1.15f)
                    .shadow(12.dp, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                SearchEngineIcon(
                    engine = engine,
                    modifier = Modifier.size(iconSizeDp),
                    iconShape = iconShape,
                )
            }
        }

        // 6. 单项快捷操作弹窗
        actionTargetEngine?.let { target ->
            MiuixEngineActionDialog(
                engine = target,
                onDismiss = { actionTargetEngine = null },
                onToggleVisibility = {
                    val updated = workingEngines.map {
                        if (it.id == target.id) it.copy(showInPickPanel = !it.showInPickPanel) else it
                    }
                    val partitioned = updated.filter { it.showInPickPanel } + updated.filter { !it.showInPickPanel }
                    val sorted = partitioned.mapIndexed { idx, itm -> itm.copy(sortOrder = idx) }
                    workingEngines.clear()
                    workingEngines.addAll(sorted)
                    onUpdateEngines(sorted)
                    actionTargetEngine = null
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                },
                onEdit = {
                    actionTargetEngine = null
                    onEditEngine(target.id)
                },
                onDelete = {
                    actionTargetEngine = null
                    onDeleteEngine(target.id)
                },
            )
        }
    }
}

/** 页标头行：展示页码并向根容器汇报 Bounds */
@Composable
private fun PageHeaderRow(
    pageIndex: Int,
    totalPages: Int,
    engineCount: Int,
    pageSize: Int,
    onPositioned: (LayoutCoordinates) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .onGloballyPositioned { coords -> onPositioned(coords) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MiuixText(
                text = stringResource(R.string.search_engine_page_header, pageIndex + 1),
                style = MiuixTheme.textStyles.title4,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
            )
            MiuixText(
                text = "$engineCount/$pageSize",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

/** 桌面级平滑网格 */
@Composable
private fun DesktopReorderableGrid(
    engines: List<SearchEngineConfig>,
    columns: Int,
    showLabels: Boolean,
    iconSize: Dp,
    iconShape: SearchEngineIconShape,
    draggedEngineId: String?,
    rootCoordinates: LayoutCoordinates?,
    itemSlotBounds: MutableMap<String, Rect>,
    onEngineClick: (SearchEngineConfig) -> Unit,
    modifier: Modifier = Modifier,
    fixedRows: Int? = null,
    onGridPositioned: ((Rect) -> Unit)? = null,
) {
    val safeCols = columns.coerceAtLeast(1)
    val horizontalSpacingDp = 6.dp
    val verticalSpacingDp = 10.dp
    val density = LocalDensity.current
    val horizontalSpacingPx = with(density) { horizontalSpacingDp.toPx() }
    val verticalSpacingPx = with(density) { verticalSpacingDp.toPx() }

    EngineCustomLayout(
        columns = safeCols,
        horizontalSpacing = horizontalSpacingDp,
        verticalSpacing = verticalSpacingDp,
        fixedRows = fixedRows,
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                rootCoordinates?.let { root ->
                    if (coords.isAttached) {
                        onGridPositioned?.invoke(root.localBoundingBoxOf(coords))
                    }
                }
            },
    ) { cellWidthPx, cellHeightPx ->
        for ((index, engine) in engines.withIndex()) {
            val engineId = engine.id
            val isBeingDragged = engineId == draggedEngineId
            val col = index % safeCols
            val row = index / safeCols

            key(engineId) {
                DesktopGridSlotItem(
                    engine = engine,
                    slotCol = col,
                    slotRow = row,
                    cellWidthPx = cellWidthPx,
                    cellHeightPx = cellHeightPx,
                    horizontalSpacingPx = horizontalSpacingPx,
                    verticalSpacingPx = verticalSpacingPx,
                    showLabel = showLabels,
                    iconSize = iconSize,
                    iconShape = iconShape,
                    isBeingDragged = isBeingDragged,
                    onClick = { onEngineClick(engine) },
                    onSlotPositioned = { coords ->
                        rootCoordinates?.let { root ->
                            if (coords.isAttached) {
                                val rect = root.localBoundingBoxOf(coords)
                                itemSlotBounds[engineId] = rect
                            }
                        }
                    },
                )
            }
        }
    }
}

/**
 * 独立的网格单元项容器：持有纯数学物理位置差值动画，首帧绝无闪烁！
 */
@Composable
private fun DesktopGridSlotItem(
    engine: SearchEngineConfig,
    slotCol: Int,
    slotRow: Int,
    cellWidthPx: Float,
    cellHeightPx: Float,
    horizontalSpacingPx: Float,
    verticalSpacingPx: Float,
    showLabel: Boolean,
    iconSize: Dp,
    iconShape: SearchEngineIconShape,
    isBeingDragged: Boolean,
    onClick: () -> Unit,
    onSlotPositioned: (LayoutCoordinates) -> Unit,
    modifier: Modifier = Modifier,
) {
    val targetX = slotCol * (cellWidthPx + horizontalSpacingPx)
    val targetY = slotRow * (cellHeightPx + verticalSpacingPx)

    val animX by animateFloatAsState(
        targetValue = targetX,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 420f),
        label = "slot_x_${engine.id}",
    )
    val animY by animateFloatAsState(
        targetValue = targetY,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 420f),
        label = "slot_y_${engine.id}",
    )

    val alpha = if (isBeingDragged) 0f else 1f

    Box(
        modifier = modifier
            .onGloballyPositioned { coords ->
                onSlotPositioned(coords)
            }
            .offset {
                IntOffset(
                    (animX - targetX).roundToInt(),
                    (animY - targetY).roundToInt(),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(vertical = 4.dp, horizontal = 2.dp)
                .alpha(alpha),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SearchEngineIcon(
                engine = engine,
                modifier = Modifier.size(iconSize),
                iconShape = iconShape,
            )

            if (showLabel) {
                MiuixText(
                    text = engine.name,
                    style = MiuixTheme.textStyles.body2,
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    color = MiuixTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** 扁平网格布局（纯数学尺寸推导，不产生二次 Measure 状态刷新闪烁） */
@Composable
private fun EngineCustomLayout(
    columns: Int,
    horizontalSpacing: Dp,
    verticalSpacing: Dp,
    modifier: Modifier = Modifier,
    fixedRows: Int? = null,
    content: @Composable (cellWidthPx: Float, cellHeightPx: Float) -> Unit,
) {
    BoxWithConstraints(modifier = modifier) {
        val safeColumns = columns.coerceAtLeast(1)
        val density = LocalDensity.current
        val horizontalSpacingPx = with(density) { horizontalSpacing.toPx() }
        val verticalSpacingPx = with(density) { verticalSpacing.toPx() }
        
        val maxWidthPx = constraints.maxWidth.toFloat()
        val totalHorizontalSpacingPx = horizontalSpacingPx * (safeColumns - 1)
        val cellWidthPx = ((maxWidthPx - totalHorizontalSpacingPx) / safeColumns).coerceAtLeast(0f)
        val cellHeightPx = cellWidthPx * 1.25f

        Layout(
            content = { content(cellWidthPx, cellHeightPx) },
        ) { measurables, constraints ->
            if (measurables.isEmpty() && fixedRows == null) {
                return@Layout layout(constraints.minWidth, constraints.minHeight) {}
            }
            val cellWidthInt = cellWidthPx.roundToInt()
            val childConstraints = constraints.copy(minWidth = cellWidthInt, maxWidth = cellWidthInt)
            val placeables = measurables.map { it.measure(childConstraints) }

            val actualRowCount = if (measurables.isEmpty()) 0 else (placeables.size + safeColumns - 1) / safeColumns
            val rowCount = fixedRows ?: actualRowCount

            val rowHeights = IntArray(rowCount) { cellHeightPx.roundToInt() }
            placeables.forEachIndexed { index, placeable ->
                val r = index / safeColumns
                if (r < rowCount) {
                    rowHeights[r] = maxOf(rowHeights[r], placeable.height)
                }
            }

            var totalHeight = 0
            val rowY = IntArray(rowCount) { 0 }
            val vSpacingInt = verticalSpacingPx.roundToInt()
            val hSpacingInt = horizontalSpacingPx.roundToInt()

            for (r in 0 until rowCount) {
                rowY[r] = totalHeight
                totalHeight += rowHeights[r]
                if (r < rowCount - 1) totalHeight += vSpacingInt
            }

            layout(constraints.maxWidth, totalHeight.coerceIn(constraints.minHeight, constraints.maxHeight)) {
                placeables.forEachIndexed { index, placeable ->
                    val r = index / safeColumns
                    val c = index % safeColumns
                    val x = c * (cellWidthInt + hSpacingInt)
                    val y = if (r < rowCount) rowY[r] else 0
                    placeable.placeRelative(x, y)
                }
            }
        }
    }
}

/** 移动整页逻辑（安全无缝移动） */
private fun movePageInWorkingList(
    list: MutableList<SearchEngineConfig>,
    fromPage: Int,
    toPage: Int,
    pageSize: Int,
) {
    val visible = list.filter { it.showInPickPanel }
    val hidden = list.filter { !it.showInPickPanel }
    val pages = visible.chunked(pageSize).map { it.toMutableList() }.toMutableList()

    if (fromPage in pages.indices && toPage in pages.indices && fromPage != toPage) {
        val moved = pages.removeAt(fromPage)
        pages.add(toPage, moved)

        val flattened = pages.flatten()
        list.clear()
        list.addAll(flattened + hidden)
    }
}

/** 图标尺寸计算 */
private fun calculateIconSize(columns: Int): Dp = when {
    columns >= 8 -> 32.dp
    columns >= 7 -> 34.dp
    columns >= 6 -> 36.dp
    columns >= 5 -> 38.dp
    else -> 40.dp
}

/** 单个引擎弹窗操作 */
@Composable
private fun MiuixEngineActionDialog(
    engine: SearchEngineConfig,
    onDismiss: () -> Unit,
    onToggleVisibility: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        MiuixCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SearchEngineIcon(engine = engine, modifier = Modifier.size(36.dp))
                    Column {
                        MiuixText(
                            text = engine.name,
                            style = MiuixTheme.textStyles.title4,
                            fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        MiuixText(
                            text = if (engine.showInPickPanel) stringResource(R.string.search_engine_status_visible) else stringResource(R.string.search_engine_status_hidden),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ArrowPreference(
                        title = if (engine.showInPickPanel) stringResource(R.string.search_engine_action_hide) else stringResource(R.string.search_engine_action_restore),
                        summary = if (engine.showInPickPanel) stringResource(R.string.search_engine_action_hide_summary) else stringResource(R.string.search_engine_action_restore_summary),
                        onClick = onToggleVisibility,
                    )
                    ArrowPreference(
                        title = stringResource(R.string.search_engine_action_edit),
                        summary = stringResource(R.string.search_engine_action_edit_summary),
                        onClick = onEdit,
                    )
                    ArrowPreference(
                        title = stringResource(R.string.search_engine_action_delete),
                        summary = stringResource(R.string.search_engine_action_delete_summary),
                        onClick = onDelete,
                    )
                }
            }
        }
    }
}

/** 计算两触点/中心点间的欧氏距离 */
private fun distance(a: Offset, b: Offset): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return kotlin.math.sqrt(dx * dx + dy * dy)
}

