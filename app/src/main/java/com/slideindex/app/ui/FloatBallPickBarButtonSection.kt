package com.slideindex.app.ui

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.slideindex.app.R
import com.slideindex.app.settings.PickResultBarButtons
import com.slideindex.app.settings.PickResultImageBarButton
import com.slideindex.app.settings.PickResultTextBarButton
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
        PickResultTextBarButton.SPEAK.id,
        R.string.float_ball_pick_bar_speak,
        R.string.float_ball_pick_bar_speak_desc,
        Icons.Outlined.VolumeUp,
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
 * 再点同一颗切换显隐 → 长按拖动排序（LazyRow + animateItem 实时换位）→ 恢复默认。
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
    val haptic = LocalHapticFeedback.current
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
            // 卡片内：容器用页面灰，选中 pill 用卡片白，层次和竞品一致。
            containerColor = MiuixTheme.colorScheme.surface,
            indicatorColor = MiuixTheme.colorScheme.surfaceContainer,
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
            empty = stringResource(R.string.float_ball_pick_bar_select_empty),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.float_ball_pick_bar_select_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // 自绘按钮：TextButton 即使去掉最小交互高度，仍带 ButtonDefaults.MinHeight(40dp)，
            // 会把这一行撑高；Row + clickable 的高度就等于图标/文字高度。
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        onRestoreDefaults()
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.float_ball_pick_bar_restore_default),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * 横向图标条：点选切换说明、再点切显隐；长按后拖动实时换位。
 *
 * 拖动排序用 LazyRow 的 `animateItem()` 做让位动画：越过邻居时立即把本项移动到目标位、
 * 同时把跟手 offset 补一个槽位，视觉上手指下的图标不动；松手只需把 offset 平滑归零，
 * 顺序已经是最终态，不再"松手重排 → 卡一下再到位"。
 */
@Composable
private fun BarButtonStrip(
    entries: List<BarButtonSpec>,
    hidden: Set<String>,
    selectedId: String?,
    onSelectToggle: (String) -> Unit,
    onReorder: (List<String>) -> Unit,
) {
    var workingIds by remember(entries) { mutableStateOf(entries.map { it.id }) }
    LaunchedEffect(entries) { workingIds = entries.map { it.id } }

    var dragId by remember { mutableStateOf<String?>(null) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var settleJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var suppressTapUntil by remember { mutableLongStateOf(0L) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val itemStridePx = with(LocalDensity.current) { (BarItemSize + BarItemSpacing).toPx() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MiuixTheme.colorScheme.surface)
            .padding(4.dp),
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(BarItemSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(items = workingIds, key = { it }) { id ->
                val entry = entries.firstOrNull { it.id == id } ?: return@items
                val isDragging = dragId == id
                Box(
                    modifier = Modifier
                        // 被拖项跟手，不参与 LazyRow 的换位动画；其他项靠 animateItem 平滑让位。
                        .then(if (isDragging) Modifier else Modifier.animateItem())
                        .zIndex(if (isDragging) 1f else 0f)
                        .offset {
                            IntOffset(if (isDragging) dragOffsetX.roundToInt() else 0, 0)
                        }
                        .pointerInput(id) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val longPress = awaitLongPressOrCancellation(down.id)
                                    ?: return@awaitEachGesture
                                var currentIndex = workingIds.indexOf(id)
                                if (currentIndex < 0) return@awaitEachGesture

                                settleJob?.cancel()
                                settleJob = null
                                dragId = id
                                dragOffsetX = 0f
                                // 长按一成立就封住点击：up 由谁先消费不做保证。
                                suppressTapUntil = Long.MAX_VALUE
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                                try {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == longPress.id }
                                        if (change == null || !change.pressed) break
                                        val delta = change.positionChange().x
                                        if (delta != 0f) {
                                            dragOffsetX += delta
                                            val target = (
                                                currentIndex + (dragOffsetX / itemStridePx).roundToInt()
                                                ).coerceIn(0, workingIds.lastIndex)
                                            if (target != currentIndex) {
                                                workingIds = workingIds.toMutableList().apply {
                                                    add(target, removeAt(currentIndex))
                                                }
                                                // 位置已交换：把跟手位移补回一个槽位，手指下的图标保持不动。
                                                dragOffsetX -= (target - currentIndex) * itemStridePx
                                                currentIndex = target
                                                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                            }
                                            change.consume()
                                        }
                                    }
                                } finally {
                                    suppressTapUntil = SystemClock.uptimeMillis() + 250L
                                    // 顺序已是最终态：只把跟手 offset 平滑归零，不再重排。
                                    // 受限手势作用域里不能直接 await 普通 suspend 函数，交给普通协程。
                                    val startOffset = dragOffsetX
                                    settleJob = scope.launch {
                                        val settle = Animatable(startOffset)
                                        settle.animateTo(0f, tween(140)) { dragOffsetX = value }
                                        dragId = null
                                        onReorder(workingIds)
                                        haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                    }
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
}

@Composable
private fun BarButtonCell(
    spec: BarButtonSpec,
    visible: Boolean,
    selected: Boolean,
    dragging: Boolean,
) {
    val shape = RoundedCornerShape(12.dp)
    // 整条图标栏已有一层灰底容器：未选中项透明，选中项用 surface 抬成一颗白色 pill（与竞品一致）。
    val container = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent
    val tint = when {
        selected -> MaterialTheme.colorScheme.primary
        visible -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val dragScale by animateFloatAsState(
        targetValue = if (dragging) 1.1f else 1f,
        label = "barItemDragScale",
    )
    Box(
        modifier = Modifier
            .size(BarItemSize)
            .scale(dragScale)
            .alpha(if (dragging) 0.95f else 1f)
            .clip(shape)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = spec.icon,
            contentDescription = stringResource(spec.labelRes),
            tint = tint,
            modifier = Modifier
                .size(20.dp)
                .alpha(if (visible) 1f else 0.38f),
        )
    }
}

@Composable
private fun BarButtonDescription(
    selected: BarButtonSpec?,
    empty: String,
) {
    val label = if (selected != null) {
        stringResource(
            R.string.float_ball_pick_bar_current_action,
            stringResource(selected.labelRes),
        )
    } else {
        empty
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = if (selected != null) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        if (selected != null) {
            Text(
                text = stringResource(selected.descRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val BarItemSize = 42.dp
private val BarItemSpacing = 3.dp
