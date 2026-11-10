package com.slideindex.app.ui

import android.os.SystemClock
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
import com.slideindex.app.ui.settings.components.LazySettingsItem
import com.slideindex.app.ui.settings.components.SettingsLazyScreenScaffold
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

/**
 * 取词面板底栏按钮：按择定的「图标条」样式（点选查看说明、再点切换显示、长按拖动排序）。
 *
 * 顺序与显隐落盘；顺序只对「纯图标模式」生效，经典样式的位置仍由另外两项设置决定。
 */
@Composable
fun FloatBallPickBarButtonSettingsScreen(
    textOrder: List<String>,
    textHidden: Set<String>,
    imageOrder: List<String>,
    imageHidden: Set<String>,
    onTextOrderChange: (List<String>) -> Unit,
    onTextHiddenChange: (Set<String>) -> Unit,
    onImageOrderChange: (List<String>) -> Unit,
    onImageHiddenChange: (Set<String>) -> Unit,
    onRestoreDefaults: () -> Unit,
    onBack: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    val isText = tab == 1
    val entries = remember(tab, textOrder, imageOrder) {
        val ids = if (tab == 1) {
            PickResultBarButtons.normalizeText(textOrder).map { it.id }
        } else {
            PickResultBarButtons.normalizeImage(imageOrder).map { it.id }
        }
        val specs = if (tab == 1) textBarSpecs else imageBarSpecs
        ids.mapNotNull { id -> specs.firstOrNull { it.id == id } }
    }
    val hidden = if (isText) textHidden else imageHidden
    var selectedId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(tab) { selectedId = null }

    SettingsLazyScreenScaffold(
        title = stringResource(R.string.float_ball_pick_bar_buttons),
        pageHint = stringResource(R.string.float_ball_pick_bar_hint),
        onBack = onBack,
    ) {
        LazySettingsItem(key = "pick-bar-tabs") {
            BarTabRow(
                textSelected = isText,
                onSelect = { text -> tab = if (text) 1 else 0 },
            )
        }
        LazySettingsItem(key = "pick-bar-strip") {
            BarButtonStrip(
                entries = entries,
                hidden = hidden,
                selectedId = selectedId,
                onSelectToggle = { id ->
                    if (selectedId != id) {
                        selectedId = id
                    } else {
                        val next = if (id in hidden) hidden - id else hidden + id
                        if (isText) onTextHiddenChange(next) else onImageHiddenChange(next)
                    }
                },
                onReorder = { ids ->
                    if (isText) onTextOrderChange(ids) else onImageOrderChange(ids)
                },
            )
        }
        LazySettingsItem(key = "pick-bar-description") {
            BarButtonDescription(
                selected = entries.firstOrNull { it.id == selectedId },
                hint = stringResource(R.string.float_ball_pick_bar_select_hint),
                empty = stringResource(R.string.float_ball_pick_bar_select_empty),
            )
        }
        LazySettingsItem(key = "pick-bar-restore") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
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
}

@Composable
private fun BarTabRow(
    textSelected: Boolean,
    onSelect: (Boolean) -> Unit,
) {
    val container = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    val active = MaterialTheme.colorScheme.surface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(container)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BarTabItem(
            label = stringResource(R.string.float_ball_pick_bar_image_title),
            selected = !textSelected,
            activeColor = active,
            modifier = Modifier.weight(1f),
            onClick = { onSelect(false) },
        )
        BarTabItem(
            label = stringResource(R.string.float_ball_pick_bar_text_title),
            selected = textSelected,
            activeColor = active,
            modifier = Modifier.weight(1f),
            onClick = { onSelect(true) },
        )
    }
}

@Composable
private fun BarTabItem(
    label: String,
    selected: Boolean,
    activeColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(19.dp))
            .background(if (selected) activeColor else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/**
 * 图标条：点选查看说明；再点同一颗切换显示/隐藏；长按后拖动调整顺序。
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
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var suppressTapUntil by remember { mutableLongStateOf(0L) }

    val itemStridePx = with(LocalDensity.current) { (BarItemSize + BarItemSpacing).toPx() }
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(BarItemSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        localIds.forEach { id ->
            val entry = entries.firstOrNull { it.id == id } ?: return@forEach
            val isDragging = draggingId == id
            Box(
                modifier = Modifier
                    .zIndex(if (isDragging) 1f else 0f)
                    .offset { IntOffset(if (isDragging) dragOffsetX.roundToInt() else 0, 0) }
                    .pointerInput(id) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val longPress = awaitLongPressOrCancellation(down.id)
                                ?: return@awaitEachGesture
                            val startIndex = localIds.indexOf(id)
                            if (startIndex < 0) return@awaitEachGesture

                            draggingId = id
                            dragOffsetX = 0f
                            // 长按一成立就封住点击：up 由谁先消费不做保证。
                            suppressTapUntil = Long.MAX_VALUE
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                            var targetIndex = startIndex
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == longPress.id }
                                    if (change == null || !change.pressed) break
                                    dragOffsetX += change.positionChange().x
                                    targetIndex = (startIndex + (dragOffsetX / itemStridePx).roundToInt())
                                        .coerceIn(0, localIds.lastIndex)
                                    change.consume()
                                }
                            } finally {
                                val finishedIds = if (targetIndex != startIndex) {
                                    localIds.toMutableList().apply {
                                        val moved = removeAt(startIndex)
                                        add(targetIndex.coerceIn(0, size), moved)
                                    }
                                } else {
                                    localIds
                                }
                                localIds = finishedIds
                                draggingId = null
                                dragOffsetX = 0f
                                suppressTapUntil = SystemClock.uptimeMillis() + 250L
                                onReorder(finishedIds)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        }
                    }
                    .clickable {
                        if (SystemClock.uptimeMillis() >= suppressTapUntil) onSelectToggle(id)
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
    Box(
        modifier = Modifier
            .size(BarItemSize)
            .zIndex(if (dragging) 1f else 0f)
            .clip(shape)
            .background(container)
            .then(
                if (selected) {
                    Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, shape)
                } else {
                    Modifier
                }
            )
            .then(if (dragging) Modifier.alpha(0.9f) else Modifier),
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
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
