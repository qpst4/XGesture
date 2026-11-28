package com.slideindex.app.ui.quicklauncher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.launcher.QuickLauncherPanel
import com.slideindex.app.launcher.QuickLauncherPanelDefaults
import com.slideindex.app.ui.ActionIconView
import com.slideindex.app.ui.GlobalIconStore
import com.slideindex.app.ui.SymbolsIcon
import com.slideindex.app.ui.miuix.CardSegment
import com.slideindex.app.ui.miuix.LocalMiuixScreenBackdrop
import com.slideindex.app.ui.miuix.MiuixConfirmDialog
import com.slideindex.app.ui.miuix.MiuixGlassDropdownMenu
import com.slideindex.app.ui.miuix.MiuixNavigationBackHandler
import com.slideindex.app.ui.miuix.MiuixSettingsFabInset
import com.slideindex.app.ui.miuix.MiuixTopBarIconButton
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.glass.GlassFloatingActionButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.material.GlassShape
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 快速启动器列表：多面板选择/新建/复制/排序/删除。
 *
 * 和快捷轮盘列表同一套交互：顶栏排序键进编辑态后整卡拖动排序；正常态长按弹
 * 手指锚定的玻璃菜单（编辑 / 复制 / 删除）；行内不再放三点菜单。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuickLauncherListScreen(
    panels: List<QuickLauncherPanel>,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onOpen: (String) -> Unit,
    onReorder: (List<QuickLauncherPanel>) -> Unit,
    onDuplicate: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var deleteTarget by remember { mutableStateOf<QuickLauncherPanel?>(null) }
    // 列表编辑态：顶栏 sort 键进入，进入后才能拖动排序；正常态长按弹管理菜单。
    var editing by rememberSaveable { mutableStateOf(false) }

    // 长按卡片：弹锚在手指位置的玻璃下拉菜单（编辑 / 复制 / 删除）。
    var menuTarget by remember { mutableStateOf<QuickLauncherPanel?>(null) }
    var lastMenuTarget by remember { mutableStateOf<QuickLauncherPanel?>(null) }
    var menuAnchor by remember { mutableStateOf<Rect?>(null) }

    // 拖动期间只改本地顺序，松手才写一次设置；pendingOrder 在写盘回灌期间顶住列表。
    var dragOrder by remember { mutableStateOf<List<QuickLauncherPanel>?>(null) }
    var pendingOrder by remember { mutableStateOf<List<QuickLauncherPanel>?>(null) }
    val displayPanels = dragOrder ?: pendingOrder ?: panels

    LaunchedEffect(panels) {
        val pending = pendingOrder ?: return@LaunchedEffect
        if (panels.map { it.id } == pending.map { it.id }) {
            pendingOrder = null
        }
    }

    // 编辑态返回先退出编辑，而不是直接退页面。
    MiuixNavigationBackHandler(enabled = editing) { editing = false }

    fun commitDragOrder() {
        val order = dragOrder ?: return
        dragOrder = null
        // 拖了但没跨过任何一格（顺序没变）就不写盘。
        if (order.map { it.id } == panels.map { it.id }) return
        pendingOrder = order
        onReorder(order)
    }

    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState = listState) { from, to ->
        val fromId = (from.key as? String)?.removePrefix("quick-launcher-panel-")
        val toId = (to.key as? String)?.removePrefix("quick-launcher-panel-")
        val current = dragOrder ?: return@rememberReorderableLazyListState
        if (fromId != null && toId != null) {
            val fromIndex = current.indexOfFirst { it.id == fromId }
            val toIndex = current.indexOfFirst { it.id == toId }
            if (fromIndex >= 0 && toIndex >= 0 && fromIndex != toIndex) {
                dragOrder = current.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
            }
        }
    }

    // FAB 与底部预留同时开/关：编辑态或已到上限时藏按钮，尾部也不该再留空白。
    val showFab = !editing && displayPanels.size < QuickLauncherPanelDefaults.MAX_PANELS

    SettingsScreenScaffold(
        title = stringResource(R.string.quick_launcher_editor_title),
        subtitle = stringResource(R.string.quick_launcher_list_subtitle),
        onBack = onBack,
        listState = listState,
        actions = {
            MiuixTopBarIconButton(
                onClick = { editing = !editing },
                // 0/1 块面板没有排序意义；编辑态下始终可点（用来退出）。
                enabled = editing || displayPanels.size > 1,
            ) {
                Icon(
                    imageVector = if (editing) MiuixIcons.Medium.Ok else MiuixIcons.Medium.Sort,
                    contentDescription = stringResource(
                        if (editing) R.string.quick_launcher_panel_done else R.string.quick_launcher_panel_sort,
                    ),
                    // 排序态用主色，跟顶栏普通动作键区分开。
                    tint = if (editing) MiuixTheme.colorScheme.primary else LocalContentColor.current,
                )
            }
        },
        floatingActionButton = {
            if (showFab) {
                GlassFloatingActionButton(
                    onClick = onCreate,
                    // 列表级背板（FAB 在录制范围外，不会自采样）；模糊不可用时自动退成实心主色圆。
                    backdrop = LocalMiuixScreenBackdrop.current,
                    // 保持 Material3 FAB 时代的圆角方块观感（16dp），只换玻璃材质。
                    shape = remember { GlassShape(16.dp) },
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.quick_launcher_panel_add),
                        tint = MiuixTheme.colorScheme.onPrimary,
                    )
                }
            }
        },
        // Scaffold 不算 FAB 高度，列表尾部要自己留出按钮的位置。
        fabInset = if (showFab) MiuixSettingsFabInset else 0.dp,
    ) {
        displayPanels.forEachIndexed { index, panel ->
            val itemKey = "quick-launcher-panel-${panel.id}"
            item(key = itemKey) {
                ReorderableItem(state = reorderState, key = itemKey) { _ ->
                    val haptic = LocalHapticFeedback.current
                    CardSegment(
                        isFirst = index == 0,
                        isLast = index == displayPanels.lastIndex,
                        // 编辑态：整张卡就是拖拽把手，按下即可拖（不用再长按一次）。
                        modifier = if (editing) {
                            Modifier.draggableHandle(
                                onDragStarted = {
                                    dragOrder = displayPanels.toList()
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
                        QuickLauncherPanelRow(
                            panel = panel,
                            displayName = panel.name.ifBlank {
                                stringResource(R.string.quick_launcher_panel_default_name, index + 1)
                            },
                            enabled = !editing,
                            onClick = { onOpen(panel.id) },
                            onLongClick = { anchor ->
                                menuAnchor = anchor
                                menuTarget = panel
                            },
                        )
                    }
                }
            }
        }
    }

    // 长按菜单：锚在手指按下位置的 miuix 玻璃下拉。保留最后一次目标/锚点，
    // 关闭时先播完退场动画再移除。
    val shownMenuTarget = menuTarget ?: lastMenuTarget
    if (menuTarget != null) lastMenuTarget = menuTarget
    val shownMenuAnchor = menuAnchor
    if (shownMenuTarget != null && shownMenuAnchor != null) {
        val target = shownMenuTarget
        MiuixGlassDropdownMenu(
            expanded = menuTarget != null,
            onDismissRequest = { menuTarget = null },
            anchorBounds = shownMenuAnchor,
            items = listOf(
                DropdownItem(
                    text = stringResource(R.string.quick_launcher_panel_edit),
                    onClick = { onOpen(target.id) },
                ),
                DropdownItem(
                    text = stringResource(R.string.quick_launcher_panel_duplicate),
                    enabled = displayPanels.size < QuickLauncherPanelDefaults.MAX_PANELS,
                    onClick = { onDuplicate(target.id) },
                ),
                DropdownItem(
                    text = stringResource(R.string.quick_launcher_panel_delete),
                    enabled = displayPanels.size > 1,
                    onClick = { deleteTarget = target },
                ),
            ),
        )
    }

    // 删除确认：保留最后一次目标，关闭时先播完退场动画，标题/文案不会先变空。
    var lastDeleteTarget by remember { mutableStateOf<QuickLauncherPanel?>(null) }
    if (deleteTarget != null) lastDeleteTarget = deleteTarget
    val deleteShownTarget = deleteTarget ?: lastDeleteTarget
    if (deleteShownTarget != null) {
        MiuixConfirmDialog(
            show = deleteTarget != null,
            onDismissRequest = { deleteTarget = null },
            title = stringResource(R.string.quick_launcher_panel_delete),
            message = stringResource(
                R.string.quick_launcher_panel_delete_confirm_named,
                deleteShownTarget.name.ifBlank {
                    stringResource(R.string.quick_launcher_panel_default_name, 1)
                },
            ),
            confirmText = stringResource(R.string.composite_library_delete),
            onConfirm = { onDelete(deleteShownTarget.id) },
            dismissText = stringResource(android.R.string.cancel),
        )
    }
}

/**
 * 面板行：单击进编辑器，长按弹管理菜单；[enabled] 为 false（编辑态）时整张卡交给拖拽。
 *
 * 长按位置要按在手指上：这里被动记录按下点（Initial 阶段、不消费事件），再换算成窗口坐标。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickLauncherPanelRow(
    panel: QuickLauncherPanel,
    displayName: String,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: (anchor: Rect) -> Unit,
) {
    var rowBounds by remember { mutableStateOf(Rect.Zero) }
    var pressPosition by remember { mutableStateOf(Offset.Zero) }
    val context = LocalContext.current
    // revision 进 key：编辑页里改完面板自定义图标，回到列表要立刻换图（prefs 不是 Compose 状态）。
    val panelIconKey = remember(panel.id, GlobalIconStore.revision) {
        GlobalIconStore.panelIcon(context, panel.id)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { rowBounds = it.boundsInWindow() }
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
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = {
                    val point = rowBounds.topLeft + pressPosition
                    onLongClick(Rect(point.x, point.y, point.x, point.y))
                },
                onLongClickLabel = stringResource(R.string.quick_launcher_panel_manage),
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (panelIconKey != null) {
            ActionIconView(
                action = GestureAction.QuickLauncher(panel.id),
                iconKey = panelIconKey,
                sizeDp = 22,
                showBackground = false,
            )
        } else {
            SymbolsIcon(
                "Apps",
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    R.string.quick_launcher_panel_item_count,
                    panel.items.size,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
