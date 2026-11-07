package com.slideindex.app.overlay.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.slideindex.app.overlay.panels.R
import com.slideindex.app.stash.StashTag
import com.slideindex.app.stash.StashTagEdits
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.icon.MiuixIcons

/**
 * 8 个预设标签色（§0.16.4 待办 2「新增（名字 + 8 个预设色）」）。
 *
 * 前 5 个就是 `StashMetaRepository.DEFAULT_TAGS` 的配色 —— 默认标签改色时能"改回原样"，
 * 后 3 个是补的：默认 5 色全是偏暖/偏冷的中饱和色，用户在粉紫之间换色时会觉得没得选。
 */
val HistoryTagPalette: List<Long> = listOf(
    0xFF5B8DF6,
    0xFFF0A93B,
    0xFF57B87A,
    0xFFC77DF0,
    0xFFE86E8C,
    0xFF4BC0C8,
    0xFF8A93A8,
    0xFFB0873C,
)

/** 标签行高。**定死**，长按拖拽的落点就是按它算的（见 [HistoryTagDragState]）。 */
private val HistoryTagRowHeight = 44.dp

/** 列表最多显示几行（超了自己滚）。行高固定，所以列表高度也是算得出来的。 */
private const val HistoryTagVisibleRows = 6

/**
 * 拖拽排序的本地状态（§0.16.5 / §0.16.6）。
 *
 * 为什么用一个 holder 而不是 `var x by remember { mutableStateOf(...) }`：
 * 拖拽回调挂在 `pointerInput` 上，它**只在 key 变化时重建**，捕获普通 local var / 委托属性时
 * 可能一直读的是旧实例；拿一个 `remember` 出来的稳定对象装状态就没有这个坑。
 *
 * 拖拽策略是**本地实时换位**（越过半行就把本地顺序换一格、手指反着补回半行），
 * 落下时把最终下标交给 `StashMetaRepository.moveTag` —— 那边的语义（移除后插入到第 N 位）
 * 与这里每一步做的操作完全一致，所以"看着落在哪"就是"落盘落在哪"。
 */
private class HistoryTagDragState(initial: List<StashTag>) {
    val ordered: MutableState<List<StashTag>> = mutableStateOf(initial)
    val index: MutableState<Int?> = mutableStateOf(null)
    val offsetY: MutableState<Float> = mutableStateOf(0f)

    /**
     * 拖拽开始那一刻的顺序。
     *
     * 为什么不在落下时跟 `tags`（宿主传进来的列表）比：`pointerInput` 的 lambda 只在 key 变化时重建，
     * 里面捕获的 `tags` 可能是**旧组合**的那一份；拿 holder 里的快照比就没有这个坑。
     */
    val original: MutableState<List<StashTag>> = mutableStateOf(initial)

    fun reset() {
        index.value = null
        offsetY.value = 0f
    }
}

/**
 * 标签管理浮窗（§0.16.4 待办 2）。
 *
 * 壳子与输入条 / 编辑条**同一套**（96% 宽、最大 720dp、圆角 28、投影 18、内边距 26·26·26·22、
 * 顶部信息行 + 内容 + 底部整行主按钮）—— 用户在面板里看到的所有"居中浮窗"必须是同一个东西。
 *
 * 内容：标签列表（色点 + 名字 + 改名 / 改色 / 删除，**长按可拖拽排序**）+ 新增（名字 + 8 个预设色）。
 * **「待办」是关键字**（完成态 / `isTodo` / 把手 `pendingTodoCount` 全靠它）：改名与删除禁用并给提示，
 * 改色与移动照旧可用（颜色与顺序都不参与关键字判定）。
 */
@Composable
fun HistoryTagManagerModal(
    open: Boolean,
    tags: List<StashTag>,
    imeBottom: Dp,
    haptics: HistoryHaptics,
    onDismiss: () -> Unit,
    onAdd: (name: String, colorArgb: Long) -> Unit,
    onRename: (oldName: String, newName: String) -> Unit,
    onSetColor: (name: String, colorArgb: Long) -> Unit,
    onDelete: (name: String) -> Unit,
    onMove: (name: String, targetIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = historyTheme()
    // 每次打开都从干净状态开始（关掉时把草稿丢掉，免得下次打开看见上次没提交的名字）。
    var newName by remember(open) { mutableStateOf("") }
    var newColor by remember(open) { mutableStateOf(HistoryTagPalette.first()) }
    var renaming by remember(open) { mutableStateOf<String?>(null) }
    var renameText by remember(open) { mutableStateOf("") }
    /** 正在改色的标签：色板统一画在**列表下方**（不在行内），这样每一行都是固定行高、拖拽落点才算得准。 */
    var colorPicking by remember(open) { mutableStateOf<String?>(null) }

    val drag = remember { HistoryTagDragState(tags) }
    // 外部列表变了（改名 / 删除 / 新增 / 拖拽落盘后）就跟着同步 —— **拖拽进行中不打断**。
    // ⚠️ key 只放 `tags`：把 `drag.index` 也当 key 的话，落下那一刻 index 归 null 会立刻用**旧的**
    // `tags` 覆盖本地顺序，列表会先闪回旧顺序再跳到新顺序。
    LaunchedEffect(tags) {
        if (drag.index.value == null) drag.ordered.value = tags
    }
    val rowHeightPx = with(LocalDensity.current) { HistoryTagRowHeight.toPx() }
    val listState = rememberLazyListState()

    /** 拖拽：越过半行就换一格，换完把手指的位移反着补回来（行才跟着手指走）。 */
    fun dragBy(deltaY: Float) {
        val from = drag.index.value ?: return
        var index = from
        var offset = drag.offsetY.value + deltaY
        val half = rowHeightPx / 2f
        while (offset > half && index < drag.ordered.value.lastIndex) {
            drag.ordered.value = drag.ordered.value.toMutableList().apply {
                add(index + 1, removeAt(index))
            }
            index += 1
            offset -= rowHeightPx
        }
        while (offset < -half && index > 0) {
            drag.ordered.value = drag.ordered.value.toMutableList().apply {
                add(index - 1, removeAt(index))
            }
            index -= 1
            offset += rowHeightPx
        }
        // 到头了就**夹住**：列表是定高（≤6 行就是内容高），再往下/上拖只会让这一行被 LazyColumn
        // 裁掉（"拖到最上边那一行突然消失"）。夹在半行以内，最多露出去 22dp。
        if (index == 0 && offset < -half) offset = -half
        if (index == drag.ordered.value.lastIndex && offset > half) offset = half
        drag.index.value = index
        drag.offsetY.value = offset
    }

    /** 落下：和拖之前的位置不一样才写盘（`moveTag` 自己也会把"没动"当 no-op）。 */
    fun endDrag() {
        val from = drag.index.value
        val name = drag.ordered.value.getOrNull(from ?: -1)?.name
        val originalIndex = drag.original.value.indexOfFirst { it.name == name }
        drag.reset()
        if (from != null && name != null && originalIndex >= 0 && originalIndex != from) {
            onMove(name, from)
        }
    }

    AnimatedVisibility(
        visible = open,
        enter = fadeIn() + scaleIn(initialScale = 0.94f),
        exit = fadeOut() + scaleOut(targetScale = 0.96f),
    ) {
        val shape = RoundedCornerShape(28.dp)
        Column(
            modifier = modifier
                .padding(bottom = imeBottom)
                .shadow(18.dp, shape)
                .clip(shape)
                // 卡片本体吃点击：不然点在卡片空白处会穿到"点空白关闭"的遮罩上（§0.16.9）。
                .historyConsumeTaps()
                .background(theme.glassSolid)
                .border(1.dp, theme.glassBorder, shape)
                .padding(start = 26.dp, end = 26.dp, top = 26.dp, bottom = 22.dp),
        ) {
            // ---- 顶部信息行：标题 + 关闭 ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.stash_tag_manage_title),
                    style = TextStyle(fontSize = HistoryFontSizes.body, fontWeight = FontWeight.SemiBold),
                    color = theme.text,
                    modifier = Modifier.weight(1f),
                )
                HistoryHeaderCircleButton(
                    icon = Icons.Default.Close,
                    contentDescription = stringResource(R.string.panel_close),
                    onClick = onDismiss,
                )
            }

            // ---- 已有标签（长按拖拽排序；行高固定，列表高度 = 行高 × 行数）----
            val ordered = drag.ordered.value
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .height(HistoryTagRowHeight * ordered.size.coerceAtMost(HistoryTagVisibleRows)),
            ) {
                itemsIndexed(ordered, key = { _, tag -> tag.name }) { index, tag ->
                    val dragging = drag.index.value == index
                    TagRow(
                        tag = tag,
                        renaming = renaming == tag.name,
                        renameText = renameText,
                        onRenameTextChange = { renameText = it },
                        onRenameStart = {
                            colorPicking = null
                            renaming = tag.name
                            renameText = tag.name
                        },
                        onRenameCancel = { renaming = null },
                        onRenameConfirm = {
                            val next = renameText.trim()
                            renaming = null
                            if (next.isNotEmpty() && next != tag.name) onRename(tag.name, next)
                        },
                        onColorToggle = {
                            renaming = null
                            colorPicking = if (colorPicking == tag.name) null else tag.name
                        },
                        onDelete = { onDelete(tag.name) },
                        renameable = !StashTagEdits.isProtected(tag.name),
                        removable = !StashTagEdits.isProtected(tag.name),
                        modifier = Modifier
                            .height(HistoryTagRowHeight)
                            .zIndex(if (dragging) 1f else 0f)
                            .graphicsLayer {
                                if (dragging) {
                                    translationY = drag.offsetY.value
                                    scaleX = 1.03f
                                    scaleY = 1.03f
                                    shadowElevation = 10.dp.toPx()
                                }
                            }
                            .pointerInput(tag.name) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        val at = drag.ordered.value.indexOfFirst { it.name == tag.name }
                                        if (at >= 0 && drag.ordered.value.size > 1) {
                                            // 行高必须一致，所以拖拽一开始就把"改色色板/改名框"收掉。
                                            colorPicking = null
                                            renaming = null
                                            drag.original.value = drag.ordered.value
                                            drag.index.value = at
                                            drag.offsetY.value = 0f
                                            haptics.tick()
                                        }
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragBy(amount.y)
                                    },
                                    onDragEnd = { endDrag() },
                                    onDragCancel = { drag.reset() },
                                )
                            },
                    )
                }
            }

            // 「待办」是关键字标签：只读规则写成列表下方的一行脚注（不再塞在行内，免得行高不齐）。
            if (ordered.any { StashTagEdits.isProtected(it.name) }) {
                Text(
                    text = stringResource(
                        R.string.stash_tag_protected_hint,
                        stashTagLabel(StashTagEdits.PROTECTED_TAG_NAME),
                    ),
                    style = TextStyle(fontSize = HistoryFontSizes.tiny),
                    color = theme.sub,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            // 改色色板（列表下方，标题写明改的是哪一枚）
            colorPicking?.let { name ->
                Text(
                    text = stringResource(R.string.stash_tag_color_picking, stashTagLabel(name)),
                    style = TextStyle(fontSize = HistoryFontSizes.meta),
                    color = theme.sub,
                    modifier = Modifier.padding(top = 10.dp),
                )
                HistoryTagPaletteRow(
                    selected = ordered.firstOrNull { it.name == name }?.colorArgb,
                    onPick = { color ->
                        colorPicking = null
                        onSetColor(name, color)
                    },
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            // ---- 新增 ----
            Text(
                text = stringResource(R.string.stash_tag_add),
                style = TextStyle(fontSize = HistoryFontSizes.meta),
                color = theme.sub,
                modifier = Modifier.padding(top = 14.dp),
            )
            TagNameField(
                value = newName,
                hint = stringResource(R.string.stash_tag_name_hint),
                onValueChange = { newName = it },
                onSubmit = {
                    val trimmed = newName.trim()
                    if (trimmed.isNotEmpty()) {
                        onAdd(trimmed, newColor)
                        newName = ""
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            HistoryTagPaletteRow(
                selected = newColor,
                onPick = { newColor = it },
                modifier = Modifier.padding(top = 10.dp),
            )
            HistoryEditActionButton(
                label = stringResource(R.string.stash_tag_add),
                icon = Icons.Default.Check,
                onClick = {
                    val trimmed = newName.trim()
                    if (trimmed.isNotEmpty()) {
                        onAdd(trimmed, newColor)
                        newName = ""
                    }
                },
                primary = true,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
        }
    }
}

/** 一行标签：色点 + 名字（或改名输入框）+ 改名 / 改色 / 删除。行高由调用方定死。 */
@Composable
private fun TagRow(
    tag: StashTag,
    renaming: Boolean,
    renameText: String,
    onRenameTextChange: (String) -> Unit,
    onRenameStart: () -> Unit,
    onRenameCancel: () -> Unit,
    onRenameConfirm: () -> Unit,
    onColorToggle: () -> Unit,
    onDelete: () -> Unit,
    renameable: Boolean,
    removable: Boolean,
    modifier: Modifier = Modifier,
) {
    val theme = historyTheme()
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(Color(tag.colorArgb)),
        )
        if (renaming) {
            TagNameField(
                value = renameText,
                hint = stringResource(R.string.stash_tag_name_hint),
                onValueChange = onRenameTextChange,
                onSubmit = onRenameConfirm,
                modifier = Modifier.weight(1f),
            )
            TagIconButton(
                icon = MiuixIcons.Medium.Ok,
                contentDescription = stringResource(R.string.stash_tag_rename),
                enabled = renameText.isNotBlank(),
                onClick = onRenameConfirm,
            )
            TagIconButton(
                icon = MiuixIcons.Medium.Close,
                contentDescription = stringResource(R.string.panel_close),
                enabled = true,
                onClick = onRenameCancel,
            )
        } else {
            Text(
                text = stashTagLabel(tag.name),
                style = TextStyle(fontSize = HistoryFontSizes.body),
                color = theme.text,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            TagIconButton(
                icon = MiuixIcons.Medium.Edit,
                contentDescription = stringResource(R.string.stash_tag_rename),
                enabled = renameable,
                onClick = onRenameStart,
            )
            TagIconButton(
                icon = MiuixIcons.Medium.Theme,
                contentDescription = stringResource(R.string.stash_tag_color),
                // 改色对「待办」也开放：颜色不参与关键字判定。
                enabled = true,
                onClick = onColorToggle,
            )
            TagIconButton(
                icon = MiuixIcons.Medium.Delete,
                contentDescription = stringResource(R.string.stash_tag_delete),
                enabled = removable,
                danger = true,
                onClick = onDelete,
            )
        }
    }
}

/** 8 个预设色的选色行；选中那枚加一圈 accent 描边。 */
@Composable
private fun HistoryTagPaletteRow(
    selected: Long?,
    onPick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = historyTheme()
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HistoryTagPalette.forEach { color ->
            val isSelected = color == selected
            Box(
                modifier = Modifier
                    .size(if (isSelected) 24.dp else 20.dp)
                    .clip(CircleShape)
                    .background(Color(color))
                    .then(
                        if (isSelected) {
                            Modifier.border(2.dp, theme.accentSolid, CircleShape)
                        } else {
                            Modifier.border(1.dp, theme.chipBorder, CircleShape)
                        },
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onPick(color) },
            )
        }
    }
}

/** 药丸形单行输入框（与编辑条里的"追加"框同一套观感）。 */
@Suppress("DEPRECATION") // state 版 BasicTextField 重载在这个 foundation 版本里不存在，见输入条的同类注释
@Composable
private fun TagNameField(
    value: String,
    hint: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = historyTheme()
    val shape = RoundedCornerShape(HistoryRadii.pill)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .height(38.dp)
            .clip(shape)
            .background(theme.appElev)
            .border(1.dp, theme.cardBorder, shape)
            .padding(horizontal = 14.dp),
        textStyle = TextStyle(fontSize = HistoryFontSizes.body, color = theme.text),
        cursorBrush = SolidColor(theme.accentSolid),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        text = hint,
                        style = TextStyle(fontSize = HistoryFontSizes.body),
                        color = theme.sub,
                        maxLines = 1,
                    )
                }
                innerTextField()
            }
        },
    )
}

/** 行内小图标按钮（34dp 命中区）。禁用时只是变淡，不做任何事。 */
@Composable
private fun TagIconButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    val theme = historyTheme()
    val tint = when {
        !enabled -> theme.sub.copy(alpha = 0.38f)
        danger -> theme.danger
        else -> theme.text.copy(alpha = 0.86f)
    }
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
    }
}
