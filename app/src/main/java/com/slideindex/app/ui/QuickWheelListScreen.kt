package com.slideindex.app.ui

import sh.calvin.reorderable.rememberReorderableLazyListState
import sh.calvin.reorderable.ReorderableItem
import com.slideindex.app.ui.miuix.CardSegment
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.slideindex.app.ui.miuix.MiuixLabeledTextField
import com.slideindex.app.ui.miuix.MiuixFormDialog
import com.slideindex.app.R
import com.slideindex.app.overlay.layout.QuickWheelLayoutEngine
import com.slideindex.app.overlay.layout.QuickWheelShape
import com.slideindex.app.settings.QuickWheel
import com.slideindex.app.ui.miuix.MiuixSettingsScreenScaffold
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.settingsCardScopeItem
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
    var manageTarget by remember { mutableStateOf<QuickWheel?>(null) }
    var renameTarget by remember { mutableStateOf<QuickWheel?>(null) }
    var renameText by remember { mutableStateOf("") }

    fun moveWheel(target: QuickWheel, delta: Int) {
        val index = wheels.indexOfFirst { it.id == target.id }
        val destination = index + delta
        if (index < 0 || destination !in wheels.indices) return
        val mutable = wheels.toMutableList()
        val item = mutable.removeAt(index)
        mutable.add(destination, item)
        onUpdateWheels(mutable)
    }

    /** 复制轮盘：除 id / 名称 / 排序外**全部照搬**（容器与二级、两级样式、扇区、长按时间、背景参数）。 */
    fun duplicateWheel(source: QuickWheel, copyName: String) {
        if (wheels.size >= QuickWheel.MAX_WHEELS) return
        onUpdateWheels(
            wheels + source.copy(
                id = UUID.randomUUID().toString(),
                name = copyName,
                order = wheels.size,
            ),
        )
    }

    // 长按整行拖动排序：需要外部持有 listState 才能算出 from/to。
    val wheelListState = rememberLazyListState()
    val wheelReorderState = rememberReorderableLazyListState(lazyListState = wheelListState) { from, to ->
        val fromId = (from.key as? String)?.removePrefix("quick-wheel-")
        val toId = (to.key as? String)?.removePrefix("quick-wheel-")
        if (fromId != null && toId != null) {
            val fromIndex = wheels.indexOfFirst { it.id == fromId }
            val toIndex = wheels.indexOfFirst { it.id == toId }
            if (fromIndex >= 0 && toIndex >= 0 && fromIndex != toIndex) {
                val mutable = wheels.toMutableList()
                val moved = mutable.removeAt(fromIndex)
                mutable.add(toIndex, moved)
                onUpdateWheels(mutable)
            }
        }
    }

    MiuixSettingsScreenScaffold(
        title = stringResource(R.string.quick_wheel_title),
        listState = wheelListState,
        subtitle = stringResource(R.string.quick_wheel_list_subtitle),
        onBack = onBack,
        floatingActionButton = {
            if (wheels.size < QuickWheel.MAX_WHEELS) {
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
        if (wheels.isEmpty()) {
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

        wheels.forEachIndexed { index, wheel ->
            val itemKey = "quick-wheel-${wheel.id}"
            item(key = itemKey) {
                ReorderableItem(state = wheelReorderState, key = itemKey) { _ ->
                    val haptic = LocalHapticFeedback.current
                    CardSegment(
                        isFirst = index == 0,
                        isLast = index == wheels.lastIndex,
                        modifier = Modifier.longPressDraggableHandle(
                            onDragStarted = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                        ),
                    ) {
                        QuickWheelRow(
                            wheel = wheel,
                            onClick = { onOpenWheel(wheel.id) },
                        )
                    }
                }
            }
        }
    }

    manageTarget?.let { target ->
        val currentIndex = wheels.indexOfFirst { it.id == target.id }
        // 用 Dialog 而不是 AlertDialog：AlertDialog 会给按钮区预留一块空白（本菜单已去掉「取消」，
        // 底下多出来的那一截就是它留下的），这里自己画卡片，底部不再多一截。
        Dialog(
            onDismissRequest = { manageTarget = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            // 复制项的默认名称：源名称 + "副本"（源名称为空时用兜底名）。
            val copyName = stringResource(
                R.string.quick_wheel_duplicate_name,
                target.name.ifBlank { stringResource(R.string.quick_wheel_label_fallback) },
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // 点卡片以外的空白处收起：本弹窗铺满整屏，Dialog 自带的"点外部关闭"用不上。
                    // Main 阶段 + requireUnconsumed：按在卡片 / 选项上会被子控件消费，不会误关。
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitFirstDown(
                                    requireUnconsumed = true,
                                    pass = PointerEventPass.Main,
                                )
                                manageTarget = null
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier.width(300.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 6.dp,
                    shadowElevation = 6.dp,
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        QuickWheelManageAction(
                            label = stringResource(R.string.quick_wheel_rename),
                            onClick = {
                                renameText = target.name
                                renameTarget = target
                                manageTarget = null
                            },
                        )
                        QuickWheelManageAction(
                            label = stringResource(R.string.quick_wheel_duplicate),
                            enabled = wheels.size < QuickWheel.MAX_WHEELS,
                            onClick = {
                                duplicateWheel(target, copyName)
                                manageTarget = null
                            },
                        )
                        QuickWheelManageAction(
                            label = stringResource(R.string.quick_wheel_move_up),
                            enabled = currentIndex > 0,
                            onClick = {
                                moveWheel(target, -1)
                                manageTarget = null
                            },
                        )
                        QuickWheelManageAction(
                            label = stringResource(R.string.quick_wheel_move_down),
                            enabled = currentIndex in 0 until wheels.lastIndex,
                            onClick = {
                                moveWheel(target, 1)
                                manageTarget = null
                            },
                        )
                        QuickWheelManageAction(
                            label = stringResource(R.string.quick_wheel_delete),
                            destructive = true,
                            onClick = {
                                onUpdateWheels(wheels.filterNot { it.id == target.id })
                                manageTarget = null
                            },
                        )
                    }
                }
            }
        }
    }

    // show 绑状态、对话框常驻组合，关闭时才有退出动画。
    val renamingWheel = renameTarget
    MiuixFormDialog(
        show = renamingWheel != null,
        onDismissRequest = { renameTarget = null },
        title = stringResource(R.string.quick_wheel_rename),
        confirmText = stringResource(R.string.quick_wheel_done),
        confirmEnabled = renameText.isNotBlank(),
        onConfirm = {
            renamingWheel?.let { target ->
                onUpdateWheels(
                    wheels.map { if (it.id == target.id) it.copy(name = renameText) else it },
                )
            }
            renameTarget = null
        },
        dismissText = stringResource(R.string.cancel),
        dismissOnConfirm = false,
    ) {
        MiuixLabeledTextField(
            value = renameText,
            onValueChange = { renameText = it },
            label = stringResource(R.string.quick_wheel_name_hint),
            singleLine = true,
            useLabelAsPlaceholder = false,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickWheelRow(
    wheel: QuickWheel,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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

/** 长按管理菜单的一行：整行横向任何位置都可点，文字居中（不再只有文字本身能点）。 */
@Composable
private fun QuickWheelManageAction(
    label: String,
    enabled: Boolean = true,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                destructive -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface
            },
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
