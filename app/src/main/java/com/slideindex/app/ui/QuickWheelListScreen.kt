package com.slideindex.app.ui

import sh.calvin.reorderable.rememberReorderableLazyListState
import sh.calvin.reorderable.ReorderableItem
import com.slideindex.app.ui.miuix.CardSegment
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.overlay.layout.QuickWheelLayoutEngine
import com.slideindex.app.overlay.layout.QuickWheelShape
import com.slideindex.app.settings.QuickWheel
import com.slideindex.app.ui.miuix.MiuixGlassDropdownMenu
import com.slideindex.app.ui.miuix.MiuixNavigationBackHandler
import com.slideindex.app.ui.miuix.MiuixSettingsScreenScaffold
import com.slideindex.app.ui.miuix.MiuixTopBarIconButton
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Sort
import java.util.UUID

/**
 * 「快捷轮盘」列表页。
 *
 * 对应参考设计页面 1：大标题 + 「轮盘列表（长按管理）」副标题、右上排序、右下 FAB `+`、条目长按管理。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuickWheelListScreen(
    wheels: List<QuickWheel>,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onOpenWheel: (String) -> Unit,
    onUpdateWheels: (List<QuickWheel>) -> Unit,
) {
    // 长按卡片：弹锚在手指位置的玻璃下拉菜单（编辑 / 复制 / 删除）。
    var menuTarget by remember { mutableStateOf<QuickWheel?>(null) }
    var lastMenuTarget by remember { mutableStateOf<QuickWheel?>(null) }
    var menuAnchor by remember { mutableStateOf<Rect?>(null) }
    // 列表编辑态：顶栏 sort 键进入，进入后才能拖动排序；正常态长按弹管理菜单。
    var editing by rememberSaveable { mutableStateOf(false) }

    // 拖拽排序：拖动期间只改本地顺序（dragOrder），松手才写一次设置。
    // 以前 onMove 里直接 onUpdateWheels(...)：写盘是异步的，列表要等回灌才刷新，
    // 于是每拖过一格卡一下、回灌时整块跳一下。pendingOrder 负责在写盘期间顶住回灌。
    var dragOrder by remember { mutableStateOf<List<QuickWheel>?>(null) }
    var pendingOrder by remember { mutableStateOf<List<QuickWheel>?>(null) }
    val displayWheels = dragOrder ?: pendingOrder ?: wheels

    LaunchedEffect(wheels) {
        val pending = pendingOrder ?: return@LaunchedEffect
        if (wheels.map { it.id } == pending.map { it.id }) {
            pendingOrder = null
        }
    }

    // 编辑态返回先退出编辑，而不是直接退页面。
    MiuixNavigationBackHandler(enabled = editing) { editing = false }

    /** 复制轮盘：除 id / 名称 / 排序外**全部照搬**（容器与二级、两级样式、扇区、长按时间、背景参数）。 */
    fun duplicateWheel(source: QuickWheel, copyName: String) {
        if (displayWheels.size >= QuickWheel.MAX_WHEELS) return
        onUpdateWheels(
            displayWheels + source.copy(
                id = UUID.randomUUID().toString(),
                name = copyName,
                order = displayWheels.size,
            ),
        )
    }

    fun commitDragOrder() {
        val order = dragOrder ?: return
        dragOrder = null
        // 拖了但没跨过任何一格（顺序没变）就不写盘。
        if (order.map { it.id } == wheels.map { it.id }) return
        pendingOrder = order
        onUpdateWheels(order)
    }

    // 长按整行拖动排序：需要外部持有 listState 才能算出 from/to。
    val wheelListState = rememberLazyListState()
    val wheelReorderState = rememberReorderableLazyListState(lazyListState = wheelListState) { from, to ->
        val fromId = (from.key as? String)?.removePrefix("quick-wheel-")
        val toId = (to.key as? String)?.removePrefix("quick-wheel-")
        val current = dragOrder ?: return@rememberReorderableLazyListState
        if (fromId != null && toId != null) {
            val fromIndex = current.indexOfFirst { it.id == fromId }
            val toIndex = current.indexOfFirst { it.id == toId }
            if (fromIndex >= 0 && toIndex >= 0 && fromIndex != toIndex) {
                dragOrder = current.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
            }
        }
    }

    MiuixSettingsScreenScaffold(
        title = stringResource(R.string.quick_wheel_title),
        listState = wheelListState,
        subtitle = stringResource(R.string.quick_wheel_list_subtitle),
        onBack = onBack,
        actions = {
            MiuixTopBarIconButton(
                onClick = { editing = !editing },
                // 0/1 个轮盘没有排序意义；编辑态下始终可点（用来退出）。
                enabled = editing || displayWheels.size > 1,
            ) {
                Icon(
                    imageVector = if (editing) MiuixIcons.Medium.Ok else MiuixIcons.Medium.Sort,
                    contentDescription = stringResource(
                        if (editing) R.string.quick_wheel_done else R.string.quick_wheel_sort,
                    ),
                )
            }
        },
        floatingActionButton = {
            if (!editing && displayWheels.size < QuickWheel.MAX_WHEELS) {
                FloatingActionButton(
                    onClick = onCreate,
                    modifier = Modifier.padding(20.dp),
                    shape = RoundedCornerShape(18.dp),
                    containerColor = QuickWheelAccent,
                    contentColor = Color.White,
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.quick_wheel_create),
                    )
                }
            }
        },
    ) {
        if (displayWheels.isEmpty()) {
            item(key = "quick-wheel-empty") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.quick_wheel_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.quick_wheel_empty_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return@MiuixSettingsScreenScaffold
        }

        displayWheels.forEachIndexed { index, wheel ->
            val itemKey = "quick-wheel-${wheel.id}"
            item(key = itemKey) {
                ReorderableItem(state = wheelReorderState, key = itemKey) { _ ->
                    val haptic = LocalHapticFeedback.current
                    CardSegment(
                        isFirst = index == 0,
                        isLast = index == displayWheels.lastIndex,
                        // 编辑态：整张卡就是拖拽把手，按下即可拖（不用再长按一次）。
                        modifier = if (editing) {
                            Modifier.draggableHandle(
                                onDragStarted = {
                                    // 整个拖动期间只动这份副本，松手才落盘。
                                    dragOrder = displayWheels.toList()
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDragStopped = {
                                    commitDragOrder()
                                    haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                },
                            )
                        } else {
                            Modifier
                        },
                    ) {
                        QuickWheelRow(
                            wheel = wheel,
                            enabled = !editing,
                            onClick = { onOpenWheel(wheel.id) },
                            onLongClick = { anchor ->
                                menuAnchor = anchor
                                menuTarget = wheel
                            },
                        )
                    }
                }
            }
        }
    }

    // 长按菜单：锚在手指按下位置的 miuix 玻璃下拉。保留最后一次目标/锚点，
    // 关闭时先播完退场动画再移除（否则会瞬间消失）。
    val shownMenuTarget = menuTarget ?: lastMenuTarget
    if (menuTarget != null) lastMenuTarget = menuTarget
    val shownMenuAnchor = menuAnchor
    if (shownMenuTarget != null && shownMenuAnchor != null) {
        val target = shownMenuTarget
        val copyName = stringResource(
            R.string.quick_wheel_duplicate_name,
            target.name.ifBlank { stringResource(R.string.quick_wheel_label_fallback) },
        )
        MiuixGlassDropdownMenu(
            expanded = menuTarget != null,
            onDismissRequest = { menuTarget = null },
            anchorBounds = shownMenuAnchor,
            items = listOf(
                DropdownItem(
                    text = stringResource(R.string.quick_wheel_edit),
                    onClick = { onOpenWheel(target.id) },
                ),
                DropdownItem(
                    text = stringResource(R.string.quick_wheel_duplicate),
                    enabled = displayWheels.size < QuickWheel.MAX_WHEELS,
                    onClick = { duplicateWheel(target, copyName) },
                ),
                DropdownItem(
                    text = stringResource(R.string.quick_wheel_delete),
                    onClick = { onUpdateWheels(displayWheels.filterNot { it.id == target.id }) },
                ),
            ),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickWheelRow(
    wheel: QuickWheel,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: (anchor: Rect) -> Unit,
) {
    var rowBounds by remember { mutableStateOf(Rect.Zero) }
    var pressPosition by remember { mutableStateOf(Offset.Zero) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { rowBounds = it.boundsInWindow() }
            // 只被动记录按下点（不消费事件），点击/长按照旧交给 combinedClickable。
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press) {
                            event.changes.firstOrNull()?.let { pressPosition = it.position }
                        }
                    }
                }
            }
            // 正常态：单击进配置，长按弹管理菜单；编辑态整张卡交给拖拽，点击直接禁用。
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = {
                    val point = rowBounds.topLeft + pressPosition
                    onLongClick(Rect(point.x, point.y, point.x, point.y))
                },
                onLongClickLabel = stringResource(R.string.quick_wheel_manage),
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = wheel.name.ifBlank { stringResource(R.string.quick_wheel_label_fallback) },
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = quickWheelSummary(wheel),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal val QuickWheelAccent = Color(0xFF3478F6)

/** 「整圆 360° · 2环 · 6个动作 + 1个留白」 */
@Composable
internal fun quickWheelSummary(wheel: QuickWheel): String {
    // 摘要与预览口径一致：按用户实际选的扇区算跨度与环数（不再做归一化）。
    val sectorMask = QuickWheelLayoutEngine.normalizeSectorMask(wheel.sectorMask)
    val shapeText = when (wheel.primaryShape) {
        QuickWheelShape.CIRCLE -> stringResource(
            R.string.quick_wheel_sector_span,
            QuickWheelLayoutEngine.sectorSpanDeg(sectorMask),
        )

        QuickWheelShape.RECT -> stringResource(R.string.quick_wheel_shape_rect)
    }
    // 统计与环数都基于真实槽位（含用户主动留出的空位）。
    val slots = wheel.slots
    val rings = QuickWheelLayoutEngine.estimateRingCount(
        slotCount = slots.size,
        shape = wheel.primaryShape,
        style = wheel.primaryStyle,
        availableWidthPx = 1080f,
        sectorMask = sectorMask,
    )
    return stringResource(
        R.string.quick_wheel_summary,
        shapeText,
        rings,
        slots.count { it.isConfigured },
        slots.count { !it.isConfigured },
    )
}
