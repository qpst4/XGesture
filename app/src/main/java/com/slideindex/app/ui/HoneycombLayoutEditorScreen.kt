package com.slideindex.app.ui

import top.yukonga.miuix.kmp.basic.SmallTitle
/*
 * Portions derived from FanFreeform / Hyper手势 (https://github.com/oxohang/FanFreeform)
 * Licensed under GPL-3.0. Modified for com.slideindex.app.
 */

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.data.AppInfo
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.showsMacroBadge
import com.slideindex.app.launcher.showsShellCommandBadge
import com.slideindex.app.launcher.showsShortcutBadge
import com.slideindex.app.overlay.HoneycombGeometry
import com.slideindex.app.overlay.drawCloneBadge
import com.slideindex.app.overlay.drawMacroBadge
import com.slideindex.app.overlay.drawShellCommandBadge
import com.slideindex.app.overlay.drawShortcutBadge
import com.slideindex.app.settings.HoneycombDisplaySettings
import com.slideindex.app.ui.compose.rememberAppRepository
import com.slideindex.app.ui.settings.components.LazySettingsItem
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.util.QuickLauncherIconResolver
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlin.coroutines.coroutineContext

private const val HoneycombLayoutLongPressDelayMs = 360L

private val PreviewBackground = Color(0xFF15161B)
private val IconPlateColor = Color(0xFF272930)
private val SlotStrokeColor = Color(0x66FFFFFF)
private val TargetStrokeColor = Color(0xFF7783FF)
private val PreviewHintColor = Color(0xB3FFFFFF)
private val DeleteZoneIdleColor = Color(0x44FFFFFF)
private val DeleteZoneActiveColor = Color(0xCCE53935)
private val DeleteZoneIconIdleColor = Color(0xB3FFFFFF)
private val DeleteZoneIconActiveColor = Color.White

private data class HoneycombPreviewLayout(
    val width: Float,
    val height: Float,
    val center: Offset,
    val scale: Float,
    val iconRadius: Float,
    val screenSlots: List<Offset>,
    val pitchPx: Float,
)

@Composable
fun HoneycombLauncherItemsSection(
    items: List<QuickLauncherItem>,
    display: HoneycombDisplaySettings,
    appsByPackage: Map<String, AppInfo>,
    onItemsChange: (List<QuickLauncherItem>) -> Unit,
    onAdd: () -> Unit,
    onInteractionActiveChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    nestedScrollEnabled: Boolean = true,
    descriptionResId: Int = R.string.honeycomb_launcher_editor_desc,
    showSectionHeader: Boolean = true,
    activityShortcuts: List<com.slideindex.app.activity.ActivityShortcut> = emptyList(),
    shellCommands: List<com.slideindex.app.shell.ShellCommand> = emptyList(),
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    var editing by remember { mutableStateOf(false) }
    var dragIndex by remember { mutableIntStateOf(-1) }
    var targetIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var deleteZoneActive by remember { mutableStateOf(false) }
    val previewHeight = 360.dp
    val touchSlop = remember(context) {
        android.view.ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    }
    var iconBitmaps by remember { mutableStateOf<List<ImageBitmap?>>(emptyList()) }
    LaunchedEffect(items, appsByPackage, activityShortcuts, shellCommands) {
        iconBitmaps = withContext(Dispatchers.IO) {
            items.map { item ->
                QuickLauncherIconResolver.iconBitmap(
                    item,
                    appsByPackage,
                    128,
                    context,
                    activityShortcuts = activityShortcuts,
                    shellCommands = shellCommands,
                )?.asImageBitmap()
            }
        }
    }
    val scrollState = rememberScrollState()
    val interactionActive = editing || dragIndex >= 0

    LaunchedEffect(interactionActive) {
        onInteractionActiveChange(interactionActive)
    }

    Column(
        modifier = modifier
            .then(
                if (nestedScrollEnabled) {
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState, enabled = !interactionActive)
                } else {
                    Modifier.fillMaxWidth()
                },
            )
            .padding(
                horizontal = if (showSectionHeader) 20.dp else 12.dp,
                vertical = if (showSectionHeader) 12.dp else 8.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (showSectionHeader) {
            Text(
                text = stringResource(descriptionResId),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SmallTitle(
                text = stringResource(R.string.honeycomb_layout_editor_title),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.honeycomb_layout_editor_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(previewHeight)
                        .clip(RoundedCornerShape(16.dp))
                        .background(PreviewBackground),
                ) {
                    val layout = remember(
                        maxWidth,
                        maxHeight,
                        items.size,
                        display.iconSizeDp,
                        display.spacingDp,
                        density.density,
                    ) {
                        computePreviewLayout(
                            width = with(density) { maxWidth.toPx() },
                            height = with(density) { maxHeight.toPx() },
                            iconSizeDp = display.iconSizeDp,
                            spacingDp = display.spacingDp,
                            count = items.size.coerceAtLeast(1),
                            density = density.density,
                        )
                    }
                    HoneycombLayoutPreview(
                        items = items,
                        iconBitmaps = iconBitmaps,
                        shellCommands = shellCommands,
                        editing = editing,
                        dragIndex = dragIndex,
                        targetIndex = targetIndex,
                        dragOffset = dragOffset,
                        deleteZoneActive = deleteZoneActive,
                        previewLayout = layout,
                        touchSlop = touchSlop,
                        onDragStateChange = { nextDragIndex, nextTargetIndex, nextOffset, nextDeleteZoneActive ->
                            dragIndex = nextDragIndex
                            targetIndex = nextTargetIndex
                            dragOffset = nextOffset
                            deleteZoneActive = nextDeleteZoneActive
                        },
                        onItemsChange = onItemsChange,
                        onLongPressHaptic = {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        },
                        onSlotTickHaptic = {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        },
                        onDeleteHaptic = {
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        },
                    )
                    if (items.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.honeycomb_layout_editor_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = PreviewHintColor,
                            )
                        }
                    }
                }
                HoneycombEditorActionsRow(
                    editMode = editing,
                    onAdd = onAdd,
                    onToggleEdit = {
                        val leavingEdit = editing
                        editing = !editing
                        if (leavingEdit) {
                            dragIndex = -1
                            targetIndex = -1
                            deleteZoneActive = false
                            // 点「完成」时强制再提交一次当前列表，避免仅本地预览未落盘�?
                            onItemsChange(items)
                        }
                    },
                )
            }
        }
    }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HoneycombLayoutEditorScreen(
    items: List<QuickLauncherItem>,
    display: HoneycombDisplaySettings,
    onBack: () -> Unit,
    onItemsChange: (List<QuickLauncherItem>) -> Unit,
) {
    val appRepository = rememberAppRepository()
    var allApps by remember { mutableStateOf(appRepository.getCachedApps()) }
    val appsByPackage = remember(allApps) { allApps.associateBy { it.packageName } }

    LaunchedEffect(Unit) {
        allApps = appRepository.loadApps(force = false)
    }

    SettingsScreenScaffold(
        title = stringResource(R.string.honeycomb_layout_editor_title),
        pageHint = stringResource(R.string.honeycomb_layout_editor_hint),
        onBack = onBack,
        scrollContent = false, modifier = Modifier.fillMaxSize(),
    ) {
        LazySettingsItem(key = "honeycomb-layout-editor-body", fillParentMaxSize = true) {
        HoneycombLauncherItemsSection(
            modifier = Modifier.fillMaxSize(),
            items = items,
            display = display,
            appsByPackage = appsByPackage,
            onItemsChange = onItemsChange,
            onAdd = {},
            onInteractionActiveChange = {},
            showSectionHeader = false,
        )
        }
    }
}

@Composable
private fun HoneycombEditorActionsRow(
    editMode: Boolean,
    onAdd: () -> Unit,
    onToggleEdit: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HoneycombActionButton(onClick = onAdd) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.quick_launcher_add),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            HoneycombActionButton(onClick = onToggleEdit, selected = editMode) {
                Text(
                    text = if (editMode) {
                        stringResource(R.string.honeycomb_layout_editor_done)
                    } else {
                        stringResource(R.string.honeycomb_layout_editor_edit)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun HoneycombActionButton(
    onClick: () -> Unit,
    selected: Boolean = false,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun HoneycombLayoutPreview(
    items: List<QuickLauncherItem>,
    iconBitmaps: List<ImageBitmap?>,
    shellCommands: List<com.slideindex.app.shell.ShellCommand>,
    editing: Boolean,
    dragIndex: Int,
    targetIndex: Int,
    dragOffset: Offset,
    deleteZoneActive: Boolean,
    previewLayout: HoneycombPreviewLayout,
    touchSlop: Float,
    onDragStateChange: (dragIndex: Int, targetIndex: Int, dragOffset: Offset, deleteZoneActive: Boolean) -> Unit,
    onItemsChange: (List<QuickLauncherItem>) -> Unit,
    onLongPressHaptic: () -> Unit,
    onSlotTickHaptic: () -> Unit,
    onDeleteHaptic: () -> Unit,
) {
    val density = LocalDensity.current
    val densityValue = density.density
    val itemsState = rememberUpdatedState(items)
    val onItemsChangeState = rememberUpdatedState(onItemsChange)
    val deleteZoneSize = 52.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(editing, items.size, previewLayout, touchSlop) {
                if (!editing || itemsState.value.isEmpty()) return@pointerInput
                val gestureJobScope = CoroutineScope(coroutineContext)
                detectHoneycombLayoutDrag(
                    layout = previewLayout,
                    touchSlop = touchSlop,
                    density = densityValue,
                    gestureJobScope = gestureJobScope,
                    onDragStateChange = onDragStateChange,
                    onSwap = { from, to ->
                        val snapshot = itemsState.value
                        if (from !in snapshot.indices || to !in snapshot.indices || from == to) return@detectHoneycombLayoutDrag
                        val updated = snapshot.toMutableList()
                        val temp = updated[from]
                        updated[from] = updated[to]
                        updated[to] = temp
                        onItemsChangeState.value(updated)
                    },
                    onDelete = { index ->
                        val snapshot = itemsState.value
                        if (index !in snapshot.indices) return@detectHoneycombLayoutDrag
                        onDeleteHaptic()
                        onItemsChangeState.value(snapshot.filterIndexed { itemIndex, _ -> itemIndex != index })
                    },
                    onLongPressHaptic = onLongPressHaptic,
                    onSlotTickHaptic = onSlotTickHaptic,
                    onDeleteZoneTickHaptic = onSlotTickHaptic,
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val layout = previewLayout

            val slotStroke = Stroke(width = 1.5f * densityValue)
            val targetStroke = Stroke(width = 3f * densityValue)
            val itemCount = items.size

            layout.screenSlots.forEachIndexed { index, slot ->
                if (index >= itemCount) return@forEachIndexed

                if (editing) {
                    drawCircle(
                        color = SlotStrokeColor,
                        radius = layout.iconRadius + 4f * densityValue,
                        center = slot,
                        style = slotStroke,
                    )
                }
                if (index == dragIndex) return@forEachIndexed
                val bitmap = iconBitmaps.getOrNull(index)
                drawHoneycombIcon(
                    bitmap = bitmap,
                    center = slot,
                    radius = layout.iconRadius,
                    densityValue = densityValue,
                    showShortcutBadge = items.getOrNull(index)?.showsShortcutBadge() == true,
                    showShellCommandBadge = items.getOrNull(index)
                        ?.let { !it.showsShortcutBadge() && it.showsShellCommandBadge(shellCommands) } == true,
                    showMacroBadge = items.getOrNull(index)?.showsMacroBadge() == true,
                    isClone = items.getOrNull(index)?.appTarget?.isClone == true,
                )
            }

            if (itemCount > 0 && targetIndex in layout.screenSlots.indices && dragIndex >= 0 && !deleteZoneActive) {
                val target = layout.screenSlots[targetIndex]
                drawCircle(
                    color = TargetStrokeColor,
                    radius = layout.iconRadius + 7f * densityValue,
                    center = target,
                    style = targetStroke,
                )
            }

            if (dragIndex in items.indices) {
                val bitmap = iconBitmaps.getOrNull(dragIndex)
                drawHoneycombIcon(
                    bitmap = bitmap,
                    center = dragOffset,
                    radius = layout.iconRadius * 1.12f,
                    alpha = 0.92f,
                    densityValue = densityValue,
                    showShortcutBadge = items[dragIndex].showsShortcutBadge(),
                    showShellCommandBadge = !items[dragIndex].showsShortcutBadge() &&
                        items[dragIndex].showsShellCommandBadge(shellCommands),
                    showMacroBadge = items[dragIndex].showsMacroBadge(),
                    isClone = items[dragIndex].appTarget?.isClone == true,
                )
            }
        }

        if (dragIndex >= 0) {
            val zoneBackground = if (deleteZoneActive) DeleteZoneActiveColor else DeleteZoneIdleColor
            val iconTint = if (deleteZoneActive) DeleteZoneIconActiveColor else DeleteZoneIconIdleColor
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
                    .size(deleteZoneSize)
                    .clip(CircleShape)
                    .background(zoneBackground),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.honeycomb_layout_editor_delete_zone),
                    tint = iconTint,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

private fun computePreviewLayout(
    width: Float,
    height: Float,
    iconSizeDp: Int,
    spacingDp: Int,
    count: Int,
    density: Float,
): HoneycombPreviewLayout {
    val pitchPx = HoneycombGeometry.pitchPx(iconSizeDp.toFloat(), spacingDp.toFloat(), density)
    val slots = HoneycombGeometry.layoutSlots(count, pitchPx)
    val centerX = width * 0.5f
    val centerY = height * 0.5f
    var maxX = 0f
    var maxY = 0f
    for (slot in slots) {
        maxX = max(maxX, abs(slot.x))
        maxY = max(maxY, abs(slot.y))
    }
    val rawRadius = iconSizeDp * density * 0.5f
    val fitX = (width - 28f * density) / max(1f, maxX * 2f + rawRadius * 2f)
    val fitY = (height - 28f * density) / max(1f, maxY * 2f + rawRadius * 2f)
    val scale = min(1f, max(0.12f, min(fitX, fitY)))
    val iconRadius = rawRadius * scale
    val screenSlots = slots.map { slot ->
        Offset(centerX + slot.x * scale, centerY + slot.y * scale)
    }
    return HoneycombPreviewLayout(
        width = width,
        height = height,
        center = Offset(centerX, centerY),
        scale = scale,
        iconRadius = iconRadius,
        screenSlots = screenSlots,
        pitchPx = pitchPx,
    )
}

private fun nearestSlotAt(
    position: Offset,
    layout: HoneycombPreviewLayout,
    dragging: Boolean,
): Int {
    val radius = if (dragging) {
        max(layout.iconRadius * 1.45f, layout.pitchPx * layout.scale * 0.58f)
    } else {
        layout.iconRadius * 1.25f
    }
    val screenSlots = layout.screenSlots.map { slot ->
        HoneycombGeometry.LayoutSlot(slot.x, slot.y)
    }
    return HoneycombGeometry.nearestLayoutSlot(screenSlots, position.x, position.y, radius)
}

private fun deleteZoneBounds(width: Float, height: Float, density: Float): Rect {
    val margin = 16f * density
    val size = 52f * density
    return Rect(
        left = margin,
        top = height - margin - size,
        right = margin + size,
        bottom = height - margin,
    )
}

private fun isInDeleteZone(position: Offset, width: Float, height: Float, density: Float): Boolean =
    deleteZoneBounds(width, height, density).contains(position)

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectHoneycombLayoutDrag(
    layout: HoneycombPreviewLayout,
    touchSlop: Float,
    density: Float,
    gestureJobScope: CoroutineScope,
    onDragStateChange: (dragIndex: Int, targetIndex: Int, dragOffset: Offset, deleteZoneActive: Boolean) -> Unit,
    onSwap: (from: Int, to: Int) -> Unit,
    onDelete: (index: Int) -> Unit,
    onLongPressHaptic: () -> Unit,
    onSlotTickHaptic: () -> Unit,
    onDeleteZoneTickHaptic: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val downIndex = nearestSlotAt(down.position, layout, dragging = false)
        if (downIndex < 0) return@awaitEachGesture

        val downPosition = down.position
        var dragIndex = -1
        var targetIndex = -1
        var dragOffset = downPosition
        var deleteZoneActive = false

        val longPressReady = gestureJobScope.async {
            delay(HoneycombLayoutLongPressDelayMs)
            true
        }

        try {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break

                if (!change.pressed) {
                    if (dragIndex >= 0) {
                        if (isInDeleteZone(change.position, layout.width, layout.height, density)) {
                            onDelete(dragIndex)
                        } else if (targetIndex >= 0 && dragIndex != targetIndex) {
                            onSwap(dragIndex, targetIndex)
                        }
                    }
                    break
                }

                dragOffset = change.position

                if (dragIndex < 0) {
                    if ((change.position - downPosition).getDistance() > touchSlop) {
                        longPressReady.cancel()
                        break
                    }
                    if (longPressReady.isCompleted) {
                        dragIndex = downIndex
                        targetIndex = downIndex
                        onLongPressHaptic()
                        onDragStateChange(dragIndex, targetIndex, dragOffset, false)
                    }
                } else {
                    change.consume()
                    val overDelete = isInDeleteZone(change.position, layout.width, layout.height, density)
                    if (overDelete != deleteZoneActive) {
                        deleteZoneActive = overDelete
                        if (overDelete) onDeleteZoneTickHaptic()
                    }
                    if (!overDelete) {
                        val next = nearestSlotAt(change.position, layout, dragging = true)
                        if (next != targetIndex) {
                            targetIndex = next
                            if (next >= 0) onSlotTickHaptic()
                        }
                    }
                    onDragStateChange(dragIndex, targetIndex, dragOffset, deleteZoneActive)
                }
            }
        } finally {
            longPressReady.cancel()
            onDragStateChange(-1, -1, Offset.Zero, false)
        }
    }
}

private fun DrawScope.drawHoneycombIcon(
    bitmap: ImageBitmap?,
    center: Offset,
    radius: Float,
    densityValue: Float,
    alpha: Float = 1f,
    showShortcutBadge: Boolean = false,
    showShellCommandBadge: Boolean = false,
    showMacroBadge: Boolean = false,
    isClone: Boolean = false,
) {
    drawCircle(color = IconPlateColor, radius = radius, center = center, alpha = alpha)
    if (bitmap != null) {
        val inset = radius * 0.10f
        val iconSize = ((radius - inset) * 2f).toInt().coerceAtLeast(1)
        val topLeft = Offset(center.x - radius + inset, center.y - radius + inset)
        drawImage(
            image = bitmap,
            dstOffset = IntOffset(topLeft.x.toInt(), topLeft.y.toInt()),
            dstSize = IntSize(iconSize, iconSize),
            alpha = alpha,
        )
    }
    if (showShortcutBadge) {
        drawShortcutBadge(
            iconCenterX = center.x,
            iconCenterY = center.y,
            iconDiameter = radius * 2f,
            alpha = alpha,
            density = densityValue,
        )
    } else if (showShellCommandBadge) {
        drawShellCommandBadge(
            iconCenterX = center.x,
            iconCenterY = center.y,
            iconDiameter = radius * 2f,
            alpha = alpha,
            density = densityValue,
        )
    } else if (showMacroBadge) {
        drawMacroBadge(
            iconCenterX = center.x,
            iconCenterY = center.y,
            iconDiameter = radius * 2f,
            alpha = alpha,
            density = densityValue,
        )
    }
    if (isClone) {
        // 蜂窝图标是圆形：黄点必须走 45° 圆内安全位
        drawCloneBadge(
            iconCenterX = center.x,
            iconCenterY = center.y,
            iconSize = radius * 2f,
            circular = true,
            alpha = alpha,
            density = densityValue,
        )
    }
}
