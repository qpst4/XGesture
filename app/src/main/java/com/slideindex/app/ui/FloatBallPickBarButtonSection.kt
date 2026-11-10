package com.slideindex.app.ui

import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.slideindex.app.R
import com.slideindex.app.settings.PickResultBarButtons
import com.slideindex.app.settings.PickResultImageBarButton
import com.slideindex.app.settings.PickResultTextBarButton
import kotlin.math.roundToInt

/** 一颗底栏按钮在设置页里的展示信息。id 与 settings 模块的枚举一一对应。 */
private data class BarButtonSpec(
    val id: String,
    val labelRes: Int,
    val descRes: Int,
    val icon: ImageVector,
)

private val textBarSpecs: List<BarButtonSpec> = listOf(
    BarButtonSpec(
        PickResultTextBarButton.COPY.id,
        R.string.float_ball_pick_bar_copy,
        R.string.float_ball_pick_bar_copy_desc,
        Icons.Outlined.ContentCopy,
    ),
    BarButtonSpec(
        PickResultTextBarButton.OPEN_LINK.id,
        R.string.float_ball_pick_bar_open_link,
        R.string.float_ball_pick_bar_open_link_desc,
        Icons.AutoMirrored.Outlined.OpenInNew,
    ),
    BarButtonSpec(
        PickResultTextBarButton.SEARCH.id,
        R.string.float_ball_pick_bar_search,
        R.string.float_ball_pick_bar_search_desc,
        Icons.Outlined.Search,
    ),
    BarButtonSpec(
        PickResultTextBarButton.TRANSLATE.id,
        R.string.float_ball_pick_bar_translate,
        R.string.float_ball_pick_bar_translate_desc,
        Icons.Outlined.Translate,
    ),
    BarButtonSpec(
        PickResultTextBarButton.SHARE.id,
        R.string.float_ball_pick_bar_share,
        R.string.float_ball_pick_bar_share_desc,
        Icons.Outlined.Share,
    ),
    BarButtonSpec(
        PickResultTextBarButton.PIN.id,
        R.string.float_ball_pick_bar_pin,
        R.string.float_ball_pick_bar_pin_desc,
        Icons.Outlined.PushPin,
    ),
    BarButtonSpec(
        PickResultTextBarButton.STASH.id,
        R.string.float_ball_pick_bar_stash,
        R.string.float_ball_pick_bar_stash_desc,
        Icons.Outlined.Archive,
    ),
    BarButtonSpec(
        PickResultTextBarButton.QR.id,
        R.string.float_ball_pick_bar_qr,
        R.string.float_ball_pick_bar_qr_desc,
        Icons.Outlined.QrCode,
    ),
    BarButtonSpec(
        PickResultTextBarButton.PROCESS_TEXT.id,
        R.string.float_ball_pick_bar_process_text,
        R.string.float_ball_pick_bar_process_text_desc,
        Icons.Outlined.Apps,
    ),
)

private val imageBarSpecs: List<BarButtonSpec> = listOf(
    BarButtonSpec(
        PickResultImageBarButton.IMAGE_SEARCH.id,
        R.string.float_ball_pick_bar_image_search,
        R.string.float_ball_pick_bar_image_search_desc,
        Icons.Outlined.ImageSearch,
    ),
    BarButtonSpec(
        PickResultImageBarButton.SAVE.id,
        R.string.float_ball_pick_bar_save,
        R.string.float_ball_pick_bar_save_desc,
        Icons.Outlined.Save,
    ),
    BarButtonSpec(
        PickResultImageBarButton.SHARE.id,
        R.string.float_ball_pick_bar_share,
        R.string.float_ball_pick_bar_share_image_desc,
        Icons.Outlined.Share,
    ),
    BarButtonSpec(
        PickResultImageBarButton.PIN.id,
        R.string.float_ball_pick_bar_pin,
        R.string.float_ball_pick_bar_pin_image_desc,
        Icons.Outlined.PushPin,
    ),
    BarButtonSpec(
        PickResultImageBarButton.STASH.id,
        R.string.float_ball_pick_bar_stash,
        R.string.float_ball_pick_bar_stash_image_desc,
        Icons.Outlined.Archive,
    ),
)

private enum class PickResultBarTab(val labelRes: Int) {
    IMAGE(R.string.float_ball_pick_bar_image_title),
    TEXT(R.string.float_ball_pick_bar_text_title),
}

/**
 * 取词面板底栏按钮的「图标条」设置段：直接嵌在面板布局设置页里（不跳子页）。
 *
 * 交互照竞品：图片/文字分段 tab → 一排工具图标 → 点选显示当前动作与说明 →
 * 再点同一颗切换显隐 → 长按拖动排序（实时换位 + 让位动画 + 换位震动）→ 恢复默认。
 */
@Composable
fun PickResultBarButtonSection(
    textOrder: List<String>,
    textHidden: Set<String>,
    imageOrder: List<String>,
    imageHidden: Set<String>,
    onTextOrderChange: (List<String>) -> Unit,
    onTextHiddenChange: (Set<String>) -> Unit,
    onImageOrderChange: (List<String>) -> Unit,
    onImageHiddenChange: (Set<String>) -> Unit,
    onRestoreDefaults: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by remember { mutableStateOf(PickResultBarTab.IMAGE) }
    val isText = tab == PickResultBarTab.TEXT

    // 乐观本地状态：点击先立即改本地，再异步落盘；settings 回流后用 LaunchedEffect 对齐。
    var localTextOrder by remember { mutableStateOf(textOrder) }
    var localTextHidden by remember { mutableStateOf(textHidden) }
    var localImageOrder by remember { mutableStateOf(imageOrder) }
    var localImageHidden by remember { mutableStateOf(imageHidden) }
    LaunchedEffect(textOrder) { localTextOrder = textOrder }
    LaunchedEffect(textHidden) { localTextHidden = textHidden }
    LaunchedEffect(imageOrder) { localImageOrder = imageOrder }
    LaunchedEffect(imageHidden) { localImageHidden = imageHidden }

    val order = if (isText) localTextOrder else localImageOrder
    val hidden = if (isText) localTextHidden else localImageHidden
    val entries = remember(tab, order) {
        val ids = if (isText) {
            PickResultBarButtons.normalizeText(order).map { it.id }
        } else {
            PickResultBarButtons.normalizeImage(order).map { it.id }
        }
        val specs = if (isText) textBarSpecs else imageBarSpecs
        ids.mapNotNull { id -> specs.firstOrNull { it.id == id } }
    }

    var selectedId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(tab) { selectedId = null }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SegmentedControl(
            items = PickResultBarTab.entries,
            selected = tab,
            labelRes = { it.labelRes },
            onSelect = { tab = it },
        )
        BarButtonStrip(
            entries = entries,
            hidden = hidden,
            selectedId = selectedId,
            onSelectToggle = { id ->
                if (selectedId != id) {
                    selectedId = id
                } else {
                    val next = if (id in hidden) hidden - id else hidden + id
                    if (isText) {
                        localTextHidden = next
                        onTextHiddenChange(next)
                    } else {
                        localImageHidden = next
                        onImageHiddenChange(next)
                    }
                }
            },
            onReorder = { ids ->
                if (isText) {
                    localTextOrder = ids
                    onTextOrderChange(ids)
                } else {
                    localImageOrder = ids
                    onImageOrderChange(ids)
                }
            },
        )
        BarButtonDescription(
            selected = entries.firstOrNull { it.id == selectedId },
            hint = stringResource(R.string.float_ball_pick_bar_select_hint),
            empty = stringResource(R.string.float_ball_pick_bar_select_empty),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onRestoreDefaults) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.float_ball_pick_bar_restore_default),
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}

/**
 * 横向图标条：点选切换说明、再点切显隐；长按后拖动实时换位。
 *
 * 手势自己接管长按（`awaitLongPressOrCancellation`）：`detectDragGesturesAfterLongPress`
 * 在抬手时要求 up 未被消费，而同一节点的 `clickable` 会先拿走 up，原地松手会被判 cancel。
 */
@Composable
private fun BarButtonStrip(
    entries: List<BarButtonSpec>,
    hidden: Set<String>,
    selectedId: String?,
    onSelectToggle: (String) -> Unit,
    onReorder: (List<String>) -> Unit,
) {
    var localIds by remember(entries) { mutableStateOf(entries.map { it.id }) }
    LaunchedEffect(entries) { localIds = entries.map { it.id } }

    var dragId by remember { mutableStateOf<String?>(null) }
    var dragStartIndex by remember { mutableIntStateOf(-1) }
    var dragTargetIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragSnapshot by remember { mutableStateOf<List<String>?>(null) }
    var suppressTapUntil by remember { mutableLongStateOf(0L) }

    val itemStridePx = with(LocalDensity.current) { (BarItemSize + BarItemSpacing).toPx() }
    val haptic = LocalHapticFeedback.current
    val scrollState = rememberScrollState()
    val displayIds = dragSnapshot ?: localIds

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(BarItemSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        displayIds.forEachIndexed { index, id ->
            val entry = entries.firstOrNull { it.id == id } ?: return@forEachIndexed
            val isDragging = dragId == id
            val displacement by animateFloatAsState(
                targetValue = displacementForIndex(
                    index = index,
                    fromIndex = dragStartIndex,
                    toIndex = dragTargetIndex,
                    itemStridePx = itemStridePx,
                ),
                label = "barItemDisplacement",
            )
            val itemOffsetX = if (isDragging) dragOffsetX else displacement

            Box(
                modifier = Modifier
                    .zIndex(if (isDragging) 1f else 0f)
                    .offset { IntOffset(itemOffsetX.roundToInt(), 0) }
                    .pointerInput(id) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val longPress = awaitLongPressOrCancellation(down.id)
                                ?: return@awaitEachGesture
                            val startIndex = localIds.indexOf(id)
                            if (startIndex < 0) return@awaitEachGesture

                            val snapshot = localIds
                            dragId = id
                            dragStartIndex = startIndex
                            dragTargetIndex = startIndex
                            dragOffsetX = 0f
                            dragSnapshot = snapshot
                            // 长按一成立就封住点击：up 由谁先消费不做保证。
                            suppressTapUntil = Long.MAX_VALUE
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                            var target = startIndex
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == longPress.id }
                                    if (change == null || !change.pressed) break
                                    dragOffsetX += change.positionChange().x
                                    val newTarget = (startIndex + (dragOffsetX / itemStridePx).roundToInt())
                                        .coerceIn(0, snapshot.lastIndex)
                                    if (newTarget != target) {
                                        target = newTarget
                                        dragTargetIndex = target
                                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    }
                                    change.consume()
                                }
                            } finally {
                                val finished = if (target != startIndex) {
                                    snapshot.toMutableList().apply {
                                        add(target.coerceIn(0, size), removeAt(startIndex))
                                    }
                                } else {
                                    snapshot
                                }
                                localIds = finished
                                dragId = null
                                dragStartIndex = -1
                                dragTargetIndex = -1
                                dragOffsetX = 0f
                                dragSnapshot = null
                                suppressTapUntil = SystemClock.uptimeMillis() + 250L
                                if (target != startIndex) onReorder(finished)
                                haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                            }
                        }
                    }
                    .clickable {
                        if (SystemClock.uptimeMillis() >= suppressTapUntil) {
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            onSelectToggle(id)
                        }
                    },
            ) {
                BarButtonCell(
                    spec = entry,
                    visible = id !in hidden,
                    selected = id == selectedId,
                    dragging = isDragging,
                )
            }
        }
    }
}

private fun displacementForIndex(
    index: Int,
    fromIndex: Int,
    toIndex: Int,
    itemStridePx: Float,
): Float = when {
    fromIndex < 0 || toIndex < 0 || fromIndex == toIndex -> 0f
    index == fromIndex -> 0f
    fromIndex < toIndex && index in (fromIndex + 1)..toIndex -> -itemStridePx
    fromIndex > toIndex && index in toIndex until fromIndex -> itemStridePx
    else -> 0f
}

@Composable
private fun BarButtonCell(
    spec: BarButtonSpec,
    visible: Boolean,
    selected: Boolean,
    dragging: Boolean,
) {
    val shape = RoundedCornerShape(14.dp)
    val container = when {
        selected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    }
    val tint = when {
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        visible -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val dragScale by animateFloatAsState(
        targetValue = if (dragging) 1.08f else 1f,
        label = "barItemDragScale",
    )
    Box(
        modifier = Modifier
            .size(BarItemSize)
            .scale(dragScale)
            .alpha(if (dragging) 0.95f else 1f)
            .clip(shape)
            .background(container)
            .then(
                if (selected) {
                    Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, shape)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = spec.icon,
            contentDescription = stringResource(spec.labelRes),
            tint = tint,
            modifier = Modifier
                .size(22.dp)
                .alpha(if (visible) 1f else 0.38f),
        )
    }
}

@Composable
private fun BarButtonDescription(
    selected: BarButtonSpec?,
    hint: String,
    empty: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = if (selected != null) {
                stringResource(
                    R.string.float_ball_pick_bar_current_action,
                    stringResource(selected.labelRes),
                )
            } else {
                empty
            },
            style = MaterialTheme.typography.titleMedium,
            color = if (selected != null) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Text(
            text = selected?.let { stringResource(it.descRes) } ?: hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        )
    }
}

private val BarItemSize = 46.dp
private val BarItemSpacing = 10.dp
