package com.slideindex.app.ui

import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.overlay.layout.QuickWheelEditorChromePlacement
import com.slideindex.app.overlay.layout.QuickWheelLayout
import com.slideindex.app.overlay.layout.QuickWheelLayoutEngine
import com.slideindex.app.overlay.quickwheel.QuickWheelAddContainer
import com.slideindex.app.settings.QuickWheel
import com.slideindex.app.settings.QuickWheelCodec
import com.slideindex.app.settings.withGapInserted
import com.slideindex.app.settings.withPrimaryMoved
import com.slideindex.app.settings.withPrimaryRemovedAt
import com.slideindex.app.settings.withSecondaryMoved
import com.slideindex.app.settings.withSecondaryPlaceholderAppended
import com.slideindex.app.settings.withSecondaryRemovedAt
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

private const val DROP_NONE = -100
private const val DROP_TRASH = -101

private val PillBackground = Color(0xCC202020)
private val PillDanger = Color(0xFFE53935)
private val BadgeBackground = Color(0xE63478F6)

/** 进入二级设置后，其它一级容器的淡化程度。 */
private const val PRIMARY_DIM_ALPHA = 0.3f

/**
 * 一级容器长按后，拖动位移超过该距离（dp）即视为"用户在处理一级容器"，
 * 此时应退出二级显示（长按只是弹二级用；一旦拖动就是排序 / 拖到删除键）。
 */
private const val DRAG_EXIT_SECONDARY_THRESHOLD_DP = 8f

/** 拖动重排时，其它容器"依次让位"的位移时长（ms）。 */
private const val DISPLACE_ANIM_MS = 160

/**
 * 编辑层顶部提示条 / 底部药丸条的占位高度（dp）。
 *
 * 矩形一级从锚点单向下铺：容器一多，末尾的「+」会滑到屏幕底部被药丸条盖住或直接出屏
 * （真机反馈的"切矩形后加号消失、点空白占位没反应"）。布局用它把整组网格收进可视区。
 */
private const val EDITOR_TOP_INSET_DP = 56f
private const val EDITOR_BOTTOM_INSET_DP = 88f

/**
 * 编辑层挂件（提示条 / 药丸组）的动态定位参数。
 *
 * 挂件横向仍居中，纵向按「当前层级容器」的实际占用计算（见 QuickWheelEditorChromePlacement），
 * 保证轮盘靠边 / 靠顶呼出时不会被盖住。
 */
private const val CHROME_GAP_DP = 12f
private const val CHROME_EDGE_MARGIN_DP = 8f

/** 提示条找不到空档、只能压在容器上时的不透明度（提示文字，不影响操作）。 */
private const val CHROME_HINT_OVERLAP_ALPHA = 0.5f

/** 首帧还没测量到实际尺寸时的兜底值（dp）。 */
private const val CHROME_PILL_FALLBACK_WIDTH_DP = 180f
private const val CHROME_PILL_FALLBACK_HEIGHT_DP = 48f
private const val CHROME_HINT_FALLBACK_HEIGHT_DP = 44f

private enum class WheelLevel { PRIMARY, SECONDARY }

/**
 * 「轮盘功能设置」悬浮编辑层（覆盖在轮盘配置页之上，与参考实现一致）。
 *
 * 一级设置：
 * - 轮盘末尾**始终只有一个**「+」容器：点它 → 进入空白容器编辑页，保存后轮盘多一个容器，「+」顺延；
 * - 点按容器 → 编辑该容器；长按容器**满「长按时间」** → 原地进入**该容器的二级设置**
 *   （判定阈值 = 轮盘自身的 `longPressMs`，与浮层真实呼出时用的是同一套参数）；
 * - 长按时间**还没到就先移动**（超过系统 touchSlop）→ 视为"按下即拖动"：**不展开二级也不变暗**，
 *   直接进入排序 / 拖到「删除」药丸松手删除（直接点击「删除」无效）；
 * - 拖动过程中按落点实时重排：其它容器**依次让位**（带位移动画），松手才真正写回；
 *   若二级已经展开，拖动位移超过 8dp 会立刻收起二级（拖动本身与二级无关）。
 *
 * 二级设置：
 * - 只有被长按的那个一级容器保持原样，**其它一级容器淡化**，以提示当前在谁的二级里；
 * - 点按二级容器 → 编辑它；底部「+ 空白占位」作用于当前层级；
 * - 点这个容器与其二级容器**之外的任意位置**（含其它一级容器、圆心、空白）→ 直接退出二级设置。
 */
@Composable
fun QuickWheelEditorOverlay(
    wheel: QuickWheel,
    onChange: (QuickWheel) -> Unit,
    onOpenSlot: (String) -> Unit,
    onClose: () -> Unit,
    /**
     * 锚点（屏幕坐标）；null = 用 [QuickWheelLayoutEngine.resolveAnchor] 居中。
     *
     * 配置页会传 `quickWheelPreviewAnchor` 的结果，让编辑层里轮盘的**形状与位置**都和
     * 「轮盘预览 / 调参实时预览」完全一致——例如默认左半圆时，圆心同样落在屏幕右边缘。
     */
    anchorOverride: Pair<Float, Float>? = null,
    modifier: Modifier = Modifier,
) {
    var expandedPrimary by remember(wheel.id) { mutableIntStateOf(-1) }
    var dragLevel by remember { mutableStateOf(WheelLevel.PRIMARY) }
    var draggingIndex by remember { mutableIntStateOf(DROP_NONE) }
    // 拖动项当前的"手指中心"（用绝对坐标，避免长按弹/收二级导致基准槽位变化时抖动）。
    var dragFinger by remember { mutableStateOf(Offset.Zero) }
    // 开始拖动时的手指中心（只用于判断"是否真的拖动了"）。
    var dragStartCenter by remember { mutableStateOf(Offset.Zero) }
    var dropTarget by remember { mutableIntStateOf(DROP_NONE) }
    // 拖动中的「实时插入位置」（重排预览用；DROP_NONE 表示不在拖动）。
    var liveTarget by remember { mutableIntStateOf(DROP_NONE) }
    // 每次提交重排后自增：让各容器的位移动画状态归零（此刻基准槽位已变，避免旧位移叠加）。
    var layoutGeneration by remember { mutableIntStateOf(0) }
    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
    var trashBounds by remember { mutableStateOf(Rect.Zero) }

    // 与「轮盘预览」浮层、配置页常驻预览共用同一套屏幕尺寸与锚点，避免三处位置/尺寸对不上。
    val context = LocalContext.current
    val metrics = context.resources.displayMetrics
    val density = if (metrics.density > 0f) metrics.density else 1f
    val screenWidthPx = metrics.widthPixels.toFloat()
    val screenHeightPx = metrics.heightPixels.toFloat()
    // 编辑层贴在页面最上层、不经过设置 Scaffold，挂件的安全区要自己避开状态栏 / 挖孔 / 导航栏。
    val composeDensity = LocalDensity.current
    val chromeTopPadding = WindowInsets.systemBars
        .union(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Top)
        .asPaddingValues()
    val chromeBottomPadding = WindowInsets.systemBars
        .only(WindowInsetsSides.Bottom)
        .asPaddingValues()
    val systemTopInsetPx = with(composeDensity) {
        chromeTopPadding.calculateTopPadding().toPx()
    }
    val systemBottomInsetPx = with(composeDensity) {
        chromeBottomPadding.calculateBottomPadding().toPx()
    }
    // 挂件的实测尺寸：首帧还没测量到，用常量兜底，测量后自动重算位置。
    var pillsSize by remember { mutableStateOf(IntSize.Zero) }
    var hintSize by remember { mutableStateOf(IntSize.Zero) }
    // 拖动多远算"真的在拖"（超过它就从二级显示里退出）。
    val dragExitSecondaryPx = DRAG_EXIT_SECONDARY_THRESHOLD_DP * density
    // 顶部提示条 / 底部药丸条占的高度：矩形网格的「+」必须留在它们之间（见常量注释）。
    val editorTopBandPx = EDITOR_TOP_INSET_DP * density
    val editorBottomBandPx = EDITOR_BOTTOM_INSET_DP * density

    val anchor = remember(
        wheel.primaryStyle,
        density,
        screenWidthPx,
        screenHeightPx,
        anchorOverride,
    ) {
        anchorOverride ?: QuickWheelLayoutEngine.resolveAnchor(
            primaryStyle = wheel.primaryStyle,
            density = density,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
        )
    }

    val editingSecondary = expandedPrimary >= 0
    // ⚠️ pointerInput 的协程不会因 expandedPrimary 变化而重启，回调闭包必须读这个"实时值"，
    // 否则会一直用创建时的旧值（曾导致"拖动时不退出二级"）。
    val editingSecondaryState = rememberUpdatedState(editingSecondary)
    val visibleSlots = wheel.slots
    val secondarySlots = visibleSlots.getOrNull(expandedPrimary)?.subSlots.orEmpty()

    // 编辑层永远在末尾多留一个位置给「+」（当前层级各一个）。
    val layout: QuickWheelLayout = remember(
        wheel,
        expandedPrimary,
        anchor,
        density,
        screenWidthPx,
        screenHeightPx,
        overlayOrigin,
        editorTopBandPx,
        editorBottomBandPx,
    ) {
        buildQuickWheelLayout(
            wheel = wheel,
            expandedPrimaryIndex = expandedPrimary,
            anchorX = anchor.first - overlayOrigin.x,
            anchorY = anchor.second - overlayOrigin.y,
            density = density,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            // 编辑层坐标是"本地坐标"（锚点已扣掉 overlayOrigin），二级求解必须用同一坐标系。
            screenOriginX = overlayOrigin.x,
            screenOriginY = overlayOrigin.y,
            // 让出顶部提示条 / 底部药丸条：矩形网格整体收进可视带，末尾「+」不会被盖住 / 出屏。
            topInsetPx = editorTopBandPx,
            bottomInsetPx = editorBottomBandPx,
            // ⚠️ 一级的追加位**始终**要有（进入二级设置时也不能去掉），但与真实槽位的数量无关：
            // 渲染器按真实槽位取数据，取不到追加位索引就跳过，它由下面的 QuickWheelAddContainer
            // 单独画成「+」，位置必须始终稳定。
            // ⚠️ 「+」是**追加位**（appendAddSlot），不再计入真实槽位数：
            // 旧写法把它算进 count，而"环未放满时整体居中"（centerOffset）会让所有真实容器
            // 平移半个步距，于是编辑层的布局和「轮盘预览 / 真实呼出」对不上。
            // 现在真实容器位置逐个一致，「+」只是顺着末环 / 末行多摆一个。
            appendPrimaryAddSlot = true,
            appendSecondaryAddSlot = editingSecondary,
        )
    }
    // 长按会改变 layout；若把它放进 pointerInput 的 key 会重启手势协程并中断拖拽，
    // 因此这里用 rememberUpdatedState 读取最新布局，pointerInput 只按容器身份取 key。
    val layoutState = rememberUpdatedState(layout)

    /** 一级 / 二级布局里真实容器的数量（不含末尾的「+」占位）。 */
    val primaryRealCount = visibleSlots.size
    val secondaryRealCount = secondarySlots.size
    // ⚠️ pointerInput 的长驻协程不会因重组而重启，闭包必须读这些"实时值"，
    // 否则会一直用创建时的旧数据（多步编辑会互相覆盖）。
    val primaryRealCountState = rememberUpdatedState(primaryRealCount)
    val secondaryRealCountState = rememberUpdatedState(secondaryRealCount)
    val wheelState = rememberUpdatedState(wheel)

    fun centerOfSlot(level: WheelLevel, index: Int): Offset? {
        val slots = when (level) {
            WheelLevel.PRIMARY -> layoutState.value.primarySlots
            WheelLevel.SECONDARY -> layoutState.value.secondarySlots
        }
        val placed = slots.firstOrNull { it.index == index } ?: return null
        return Offset(placed.centerX, placed.centerY)
    }

    fun realCountOf(level: WheelLevel): Int = when (level) {
        WheelLevel.PRIMARY -> primaryRealCountState.value
        WheelLevel.SECONDARY -> secondaryRealCountState.value
    }

    /**
     * 拖动重排后，第 [index] 个容器落到的新槽位序号。
     *
     * 与 [withPrimaryMoved] / [withSecondaryMoved]（removeAt(from) 后 add(to)）的语义一致。
     */
    fun newIndexOf(level: WheelLevel, index: Int): Int {
        if (dragLevel != level) return index
        val from = draggingIndex
        val to = liveTarget
        if (from < 0 || to == DROP_NONE || from == to) return index
        return when {
            index == from -> to
            from < to && index in (from + 1)..to -> index - 1
            from > to && index in to until from -> index + 1
            else -> index
        }
    }

    /** 第 [index] 个容器为"让位"需要移动的位移（相对它的基准槽位）；不需要动则为 Zero。 */
    fun displacementOf(level: WheelLevel, index: Int): Offset {
        if (index !in 0 until realCountOf(level)) return Offset.Zero
        val target = newIndexOf(level, index)
        if (target == index) return Offset.Zero
        val from = centerOfSlot(level, index) ?: return Offset.Zero
        val to = centerOfSlot(level, target) ?: return Offset.Zero
        return Offset(to.x - from.x, to.y - from.y)
    }

    /** 手指落点应把拖动项插到第几个位置（0..realCount-1）。 */
    fun computeLiveInsertIndex(level: WheelLevel, from: Int, fingerX: Float, fingerY: Float): Int {
        val count = realCountOf(level)
        if (count <= 1) return from
        val others = (0 until count)
            .filter { it != from }
            .mapNotNull { index -> centerOfSlot(level, index)?.let { index to it } }
        if (others.isEmpty()) return from
        val (nearestIndex, nearestCenter) = others.minByOrNull { (_, center) ->
            hypot(center.x - fingerX, center.y - fingerY)
        } ?: return from
        val k = others.indexOfFirst { it.first == nearestIndex }.coerceAtLeast(0)
        // 以"最近的容器 → 它的下一个容器"方向为前进方向，判断落点在它之前还是之后。
        val reference = others.getOrNull(k + 1)?.second ?: others.getOrNull(k - 1)?.second
        val forward = if (reference != null) {
            Offset(reference.x - nearestCenter.x, reference.y - nearestCenter.y)
        } else {
            Offset.Zero
        }
        val toNearest = Offset(fingerX - nearestCenter.x, fingerY - nearestCenter.y)
        val after = if (forward.x * forward.x + forward.y * forward.y > 0f) {
            (toNearest.x * forward.x + toNearest.y * forward.y) > 0f
        } else {
            true
        }
        return (k + if (after) 1 else 0).coerceIn(0, count - 1)
    }

    /** 退出二级设置（如果不在二级里就退出整个编辑层）。 */
    fun exitSecondaryOrClose() {
        if (editingSecondary) expandedPrimary = -1 else onClose()
    }

    fun resetDrag() {
        draggingIndex = DROP_NONE
        dragFinger = Offset.Zero
        dragStartCenter = Offset.Zero
        dropTarget = DROP_NONE
        liveTarget = DROP_NONE
    }

    /** 拖拽落点：优先判定「删除」药丸，其次最近的同级容器。 */
    fun computeDropTarget(level: WheelLevel, from: Int): Int {
        val slots = when (level) {
            WheelLevel.PRIMARY -> layoutState.value.primarySlots
            WheelLevel.SECONDARY -> layoutState.value.secondarySlots
        }
        if (slots.none { it.index == from }) return DROP_NONE
        if (trashBounds.contains(overlayOrigin + dragFinger)) return DROP_TRASH

        var best = DROP_NONE
        var bestDistance = Float.MAX_VALUE
        slots.forEach { other ->
            if (other.index == from) return@forEach
            val distance = hypot(dragFinger.x - other.centerX, dragFinger.y - other.centerY)
            if (distance < other.sizePx && distance < bestDistance) {
                bestDistance = distance
                best = other.index
            }
        }
        return best
    }

    fun commitDrop(level: WheelLevel, from: Int, to: Int) {
        if (from == DROP_NONE || to == DROP_NONE || from == to) return
        val current = wheelState.value
        val updated = when (level) {
            WheelLevel.PRIMARY -> if (to == DROP_TRASH) {
                current.withPrimaryRemovedAt(from)
            } else {
                current.withPrimaryMoved(from, to)
            }

            WheelLevel.SECONDARY -> if (expandedPrimary < 0) {
                current
            } else if (to == DROP_TRASH) {
                current.withSecondaryRemovedAt(expandedPrimary, from)
            } else {
                current.withSecondaryMoved(expandedPrimary, from, to)
            }
        }
        if (updated != current) {
            onChange(updated)
            // 基准槽位即将变化：把动画状态整体归零，避免旧位移与新基准叠加（否则会跳一下）。
            layoutGeneration++
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // 编辑期间吞掉所有指针事件，避免误触下层设置项。
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { overlayOrigin = it.positionInWindow() },
        ) {
            // 这个容器、它的二级容器**之外**的任何位置：二级里 = 退出二级；一级里 = 关闭编辑层。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { exitSecondaryOrClose() },
            )

            key(layoutGeneration) {
                QuickWheelWheelRenderer(
                    layout = layout,
                    wheel = wheel,
                    // 配置里把用户留出的空位显示为"空白阴影"（真实呼出时是全透明的）。
                    showPlaceholders = true,
                    // 有二级容器的一级容器：右上角红点。
                    showSecondaryDots = true,
                    centerModifier = Modifier
                        .alpha(if (editingSecondary) PRIMARY_DIM_ALPHA else 1f)
                        .clickable {
                            if (editingSecondary) {
                                expandedPrimary = -1
                            } else {
                                onOpenSlot(QuickWheelCodec.PATH_CENTER)
                            }
                        },
                    // 只施加「拖拽位移」：容器的基础摆位由 PlacedContainer 内部完成。
                    // 拖动重排时，其它容器按"实时插入位置"依次让位（带位移动画）；拖动项本身跟手。
                    primaryModifier = { placed, _ ->
                        val isSecondaryParent = placed.index == expandedPrimary
                        val isDragged = dragLevel == WheelLevel.PRIMARY &&
                            draggingIndex == placed.index
                        val displace = displacementOf(WheelLevel.PRIMARY, placed.index)
                        val animatedDisplace by animateIntOffsetAsState(
                            targetValue = IntOffset(displace.x.roundToInt(), displace.y.roundToInt()),
                            animationSpec = tween(durationMillis = DISPLACE_ANIM_MS),
                            label = "primaryDisplace",
                        )
                        // 拖动项按"绝对手指位置 − 当前基准槽位"来偏移：即使长按弹/收二级改了基准，
                        // 拖动项也不会跳。
                        val baseCenter = centerOfSlot(WheelLevel.PRIMARY, placed.index)
                        val dragDelta = if (isDragged && baseCenter != null) {
                            IntOffset(
                                (dragFinger.x - baseCenter.x).roundToInt(),
                                (dragFinger.y - baseCenter.y).roundToInt(),
                            )
                        } else {
                            IntOffset.Zero
                        }
                        Modifier
                            .alpha(
                                if (editingSecondary && !isSecondaryParent) PRIMARY_DIM_ALPHA else 1f,
                            )
                            .offset {
                                // ⚠️ 按层级判断，否则拖二级容器时同级序号的一级容器会被一起拖动。
                                if (isDragged) dragDelta else animatedDisplace
                            }
                            // 长按阈值 = 轮盘自身的「长按时间」，与浮层真实呼出时同一套判定。
                            .pointerInput(placed.index, wheel.longPressMs) {
                                detectDragOrLongPress(
                                    longPressMs = wheel.longPressMs,
                                    onDragStart = { longPressed ->
                                        // 二级设置里一级容器不参与拖拽，避免误拖。
                                        if (!editingSecondaryState.value) {
                                            dragLevel = WheelLevel.PRIMARY
                                            draggingIndex = placed.index
                                            val base = centerOfSlot(WheelLevel.PRIMARY, placed.index)
                                                ?: Offset.Zero
                                            dragStartCenter = base
                                            dragFinger = base
                                            dropTarget = DROP_NONE
                                            liveTarget = placed.index
                                            // 长按满「长按时间」→ 立刻展开它的二级；
                                            // "到点前就先移动"（longPressed=false）只排序、不展开。
                                            if (longPressed) expandedPrimary = placed.index
                                        } else {
                                            draggingIndex = DROP_NONE
                                            // 已在二级设置里：长按其它一级容器 = 切换它的二级。
                                            if (longPressed) expandedPrimary = placed.index
                                        }
                                    },
                                    onDrag = { change, delta ->
                                        change.consume()
                                        if (dragLevel == WheelLevel.PRIMARY &&
                                            draggingIndex == placed.index
                                        ) {
                                            dragFinger += delta
                                            // 位移超过阈值 = 真的在拖动（排序 / 拖到删除键）：
                                            // 已展开的二级立刻收起——整个过程与二级无关。
                                            val travel = dragFinger - dragStartCenter
                                            if (travel.getDistance() > dragExitSecondaryPx &&
                                                editingSecondaryState.value
                                            ) {
                                                expandedPrimary = -1
                                            }
                                            val drop = computeDropTarget(WheelLevel.PRIMARY, placed.index)
                                            dropTarget = drop
                                            liveTarget = if (drop == DROP_TRASH) {
                                                placed.index
                                            } else {
                                                computeLiveInsertIndex(
                                                    WheelLevel.PRIMARY,
                                                    placed.index,
                                                    dragFinger.x,
                                                    dragFinger.y,
                                                )
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        if (dragLevel == WheelLevel.PRIMARY &&
                                            draggingIndex == placed.index
                                        ) {
                                            val to = if (dropTarget == DROP_TRASH) {
                                                DROP_TRASH
                                            } else {
                                                liveTarget.takeIf { it != DROP_NONE } ?: draggingIndex
                                            }
                                            commitDrop(WheelLevel.PRIMARY, draggingIndex, to)
                                        }
                                        resetDrag()
                                    },
                                    onDragCancel = { resetDrag() },
                                )
                            }
                            .clickable {
                                when {
                                    // 一级设置：点容器进入它的编辑页。
                                    !editingSecondary ->
                                        onOpenSlot(QuickWheelCodec.primaryPath(placed.index))

                                    // 二级设置：点该容器本身不跳转（它只是父容器）。
                                    isSecondaryParent -> Unit

                                    // 二级设置：点其它一级容器 = 退出二级设置。
                                    else -> expandedPrimary = -1
                                }
                            }
                    },
                    secondaryModifier = { placed, _ ->
                        val isDragged = dragLevel == WheelLevel.SECONDARY &&
                            draggingIndex == placed.index
                        val displace = displacementOf(WheelLevel.SECONDARY, placed.index)
                        val animatedDisplace by animateIntOffsetAsState(
                            targetValue = IntOffset(displace.x.roundToInt(), displace.y.roundToInt()),
                            animationSpec = tween(durationMillis = DISPLACE_ANIM_MS),
                            label = "secondaryDisplace",
                        )
                        val baseCenter = centerOfSlot(WheelLevel.SECONDARY, placed.index)
                        val dragDelta = if (isDragged && baseCenter != null) {
                            IntOffset(
                                (dragFinger.x - baseCenter.x).roundToInt(),
                                (dragFinger.y - baseCenter.y).roundToInt(),
                            )
                        } else {
                            IntOffset.Zero
                        }
                        Modifier
                            .offset {
                                if (isDragged) dragDelta else animatedDisplace
                            }
                            // 二级容器同样按轮盘的「长按时间」判定长按（二级没有更深一层，长按只是排序入口）。
                            .pointerInput(placed.index, expandedPrimary, wheel.longPressMs) {
                                detectDragOrLongPress(
                                    longPressMs = wheel.longPressMs,
                                    onDragStart = { _ ->
                                        if (expandedPrimary >= 0) {
                                            dragLevel = WheelLevel.SECONDARY
                                            draggingIndex = placed.index
                                            val base = centerOfSlot(WheelLevel.SECONDARY, placed.index)
                                                ?: Offset.Zero
                                            dragStartCenter = base
                                            dragFinger = base
                                            dropTarget = DROP_NONE
                                            liveTarget = placed.index
                                        } else {
                                            draggingIndex = DROP_NONE
                                        }
                                    },
                                    onDrag = { change, delta ->
                                        change.consume()
                                        if (dragLevel == WheelLevel.SECONDARY &&
                                            draggingIndex == placed.index
                                        ) {
                                            dragFinger += delta
                                            val drop = computeDropTarget(WheelLevel.SECONDARY, placed.index)
                                            dropTarget = drop
                                            liveTarget = if (drop == DROP_TRASH) {
                                                placed.index
                                            } else {
                                                computeLiveInsertIndex(
                                                    WheelLevel.SECONDARY,
                                                    placed.index,
                                                    dragFinger.x,
                                                    dragFinger.y,
                                                )
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        if (expandedPrimary >= 0 &&
                                            dragLevel == WheelLevel.SECONDARY &&
                                            draggingIndex == placed.index
                                        ) {
                                            val to = if (dropTarget == DROP_TRASH) {
                                                DROP_TRASH
                                            } else {
                                                liveTarget.takeIf { it != DROP_NONE } ?: draggingIndex
                                            }
                                            commitDrop(WheelLevel.SECONDARY, draggingIndex, to)
                                        }
                                        resetDrag()
                                    },
                                    onDragCancel = { resetDrag() },
                                )
                            }
                            .clickable {
                                if (expandedPrimary >= 0) {
                                    onOpenSlot(
                                        QuickWheelCodec.secondaryPath(expandedPrimary, placed.index),
                                    )
                                }
                            }
                    },
                    highlightPrimaryIndex = if (dragLevel == WheelLevel.PRIMARY) dropTarget else -1,
                    highlightSecondaryIndex = if (dragLevel == WheelLevel.SECONDARY) dropTarget else -1,
                )
            }

            // 当前层级末尾唯一的「+」：位置就是布局多预留的那一个槽位。
            val addIndex = if (editingSecondary) secondarySlots.size else visibleSlots.size
            val addPlaced = if (editingSecondary) {
                layout.secondarySlots.firstOrNull { it.index == addIndex }
            } else {
                layout.primarySlots.firstOrNull { it.index == addIndex }
            }
            // 兜底：布局算出的位置不在可视区（容器特别多、整组比屏幕还高）时，固定在底部药丸条
            // 上方。「+」是唯一的新增入口，不能因为越界就彻底看不见 / 点不到。
            val visibleWidthPx = (screenWidthPx - overlayOrigin.x).coerceAtLeast(1f)
            val visibleHeightPx = (screenHeightPx - overlayOrigin.y).coerceAtLeast(1f)
            val addStyle = if (editingSecondary) wheel.secondaryStyle else wheel.primaryStyle
            val addSizePx = addPlaced?.sizePx ?: (addStyle.containerSizeDp * density)
            val addCornerPx = addPlaced?.cornerPx ?: (addStyle.containerCornerDp * density)
            val addCenter = addPlaced?.let { placed ->
                val fits = placed.centerX - addSizePx / 2f >= 0f &&
                    placed.centerX + addSizePx / 2f <= visibleWidthPx &&
                    placed.centerY - addSizePx / 2f >= 0f &&
                    placed.centerY + addSizePx / 2f <= visibleHeightPx - editorBottomBandPx
                if (fits) Offset(placed.centerX, placed.centerY) else null
            } ?: Offset(
                x = visibleWidthPx / 2f,
                y = (visibleHeightPx - editorBottomBandPx - addSizePx / 2f)
                    .coerceAtLeast(addSizePx / 2f),
            )
            QuickWheelAddContainer(
                sizePx = addSizePx,
                cornerPx = addCornerPx,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (addCenter.x - addSizePx / 2f).roundToInt(),
                            (addCenter.y - addSizePx / 2f).roundToInt(),
                        )
                    }
                    .clickable {
                        onOpenSlot(
                            if (editingSecondary) {
                                QuickWheelCodec.secondaryPath(expandedPrimary, addIndex)
                            } else {
                                QuickWheelCodec.primaryPath(addIndex)
                            },
                        )
                    },
            )

        }

        // 挂件只按**一级容器**避让：展开二级时位置保持不变（二级容器较小，
        // 一级的空档通常仍然适用；保持不动可以避免进出二级时药丸跳来跳去）。
        val chromeSlots = layout.primarySlots
        val chromeVisibleWidthPx = (screenWidthPx - overlayOrigin.x).coerceAtLeast(1f)
        val chromeVisibleHeightPx = (screenHeightPx - overlayOrigin.y).coerceAtLeast(1f)
        val pillsHeightPx = pillsSize.height.toFloat().takeIf { it > 0f }
            ?: (CHROME_PILL_FALLBACK_HEIGHT_DP * density)
        val pillsWidthPx = pillsSize.width.toFloat().takeIf { it > 0f }
            ?: (CHROME_PILL_FALLBACK_WIDTH_DP * density)
        val hintHeightPx = hintSize.height.toFloat().takeIf { it > 0f }
            ?: (CHROME_HINT_FALLBACK_HEIGHT_DP * density)
        val chromePlacement = remember(
            chromeSlots,
            chromeVisibleWidthPx,
            chromeVisibleHeightPx,
            pillsWidthPx,
            pillsHeightPx,
            hintHeightPx,
            systemTopInsetPx,
            systemBottomInsetPx,
        ) {
            QuickWheelEditorChromePlacement.place(
                slots = chromeSlots,
                horizontalCenterPx = chromeVisibleWidthPx / 2f,
                pillsWidthPx = pillsWidthPx,
                pillsHeightPx = pillsHeightPx,
                hintWidthPx = chromeVisibleWidthPx,
                hintHeightPx = hintHeightPx,
                safeTopPx = systemTopInsetPx + CHROME_EDGE_MARGIN_DP * density,
                safeBottomPx = (chromeVisibleHeightPx - systemBottomInsetPx -
                    CHROME_EDGE_MARGIN_DP * density)
                    .coerceAtLeast(systemTopInsetPx + pillsHeightPx),
                gapPx = CHROME_GAP_DP * density,
                wheelCenterY = layout.centerY,
            )
        }

        QuickWheelHintPill(
            text = stringResource(
                if (editingSecondary) {
                    R.string.quick_wheel_hint_secondary
                } else {
                    R.string.quick_wheel_hint_canvas
                },
            ),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, chromePlacement.hintTop.roundToInt()) }
                .onSizeChanged { hintSize = it }
                // 找不到空档时允许压在容器上：半透明表示"这是提示、不挡操作"。
                .alpha(if (chromePlacement.hintOverlapping) CHROME_HINT_OVERLAP_ALPHA else 1f),
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, chromePlacement.pillsTop.roundToInt()) }
                .onSizeChanged { pillsSize = it },
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QuickWheelOverlayPill(
                text = stringResource(R.string.quick_wheel_add_placeholder),
                onClick = {
                    onChange(
                        if (editingSecondary) {
                            wheel.withSecondaryPlaceholderAppended(expandedPrimary)
                        } else {
                            wheel.withGapInserted()
                        },
                    )
                },
            )
            QuickWheelOverlayPill(
                text = stringResource(R.string.quick_wheel_delete_container),
                active = dropTarget == DROP_TRASH,
                deleteIcon = dropTarget == DROP_TRASH,
                modifier = Modifier.onGloballyPositioned { trashBounds = it.boundsInWindow() },
                // 只作为拖放目标：直接点击不做任何事。
                onClick = {},
            )
        }
    }
}

@Composable
private fun QuickWheelOverlayPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    deleteIcon: Boolean = false,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier
            .clip(CircleShape)
            .background(if (active) PillDanger else PillBackground),
    ) {
        if (deleteIcon) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(text = text, color = Color.White)
    }
}

/** 编辑层的长按判定结果。 */
private sealed interface WheelPressOutcome {
    /** 按住不动满「长按时间」：长按成立。 */
    object LongPress : WheelPressOutcome

    /**
     * 到点前就移动超过 touchSlop："按下即拖动"，不展开二级。
     *
     * @param change 触发判定的那个事件（补齐位移时要重放给拖动回调）；
     * @param travel 按下后已经移动的位移（拖动起点要补上，否则拖动项会落后手指）。
     */
    data class MovedEarly(val change: PointerInputChange, val travel: Offset) : WheelPressOutcome

    /** 到点前就抬手：不算长按（轻点仍交给外层 clickable）。 */
    object Lifted : WheelPressOutcome
}

/**
 * 长按 + 拖动手势。
 *
 * 与 `detectDragGesturesAfterLongPress` 的唯一区别：**长按阈值改用轮盘自身的「长按时间」**
 * （`QuickWheel.longPressMs`），与浮层真实呼出时的判定（`QuickWheelOverlayContent` 的悬停计时）
 * 保持同一套参数。判定规则：
 *
 * - 按住不动满 [longPressMs] → [onDragStart] 传 `true`（长按成立）；
 * - 到点前就移动超过 touchSlop → [onDragStart] 传 `false`（"按下即拖动"，不展开二级）；
 * - 到点前就抬手 → 什么都不做，轻点由外层 clickable 处理。
 *
 * 长按成立 / 转拖动时会**消费按下事件**，避免外层 clickable 把同一次触控也当成点击。
 */
private suspend fun PointerInputScope.detectDragOrLongPress(
    longPressMs: Int,
    onDragStart: (longPressed: Boolean) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val outcome = awaitWheelPressOutcome(
            down = down,
            touchSlop = viewConfiguration.touchSlop,
            timeoutMs = longPressMs.toLong(),
        )
        if (outcome is WheelPressOutcome.MovedEarly) {
            onDragStart(false)
            // 判定为拖动之前已经移动的那段位移要先补上，否则拖动项会一直落后手指。
            onDrag(outcome.change, outcome.travel)
        } else if (outcome == WheelPressOutcome.LongPress) {
            onDragStart(true)
        } else {
            return@awaitEachGesture // 轻点：交给 clickable
        }
        awaitWheelDrag(down.id, onDrag, onDragEnd, onDragCancel)
    }
}

/**
 * 等待「长按时间」：期间抬手 = 轻点，位移超过 [touchSlop] = 拖动。
 *
 * 计时用 [withTimeout]——在指针作用域里超时只会中断本次等待（抛
 * [PointerEventTimeoutCancellationException] / [TimeoutCancellationException]），
 * 不会打断整个手势协程，因此超时后仍可继续跟踪拖动。
 */
private suspend fun AwaitPointerEventScope.awaitWheelPressOutcome(
    down: PointerInputChange,
    touchSlop: Float,
    timeoutMs: Long,
): WheelPressOutcome {
    var latest = down
    val outcome = try {
        withTimeout<WheelPressOutcome>(timeoutMs) {
            var event = awaitPointerEvent()
            while (true) {
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null || !change.pressed) break
                latest = change
                if ((change.position - down.position).getDistance() > touchSlop) {
                    return@withTimeout WheelPressOutcome.MovedEarly(
                        change = change,
                        travel = change.position - down.position,
                    )
                }
                event = awaitPointerEvent()
            }
            WheelPressOutcome.Lifted
        }
    } catch (_: PointerEventTimeoutCancellationException) {
        WheelPressOutcome.LongPress
    } catch (_: TimeoutCancellationException) {
        WheelPressOutcome.LongPress
    }
    // 长按成立 / 转拖动：消费按下事件，避免外层 clickable 也把这次触控当成点击。
    if (outcome != WheelPressOutcome.Lifted) latest.consume()
    return outcome
}

/** 跟踪拖动直到抬手（[onDragEnd]）或指针丢失（[onDragCancel]）。 */
private suspend fun AwaitPointerEventScope.awaitWheelDrag(
    pointerId: PointerId,
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == pointerId }
        if (change == null) {
            onDragCancel()
            return
        }
        if (!change.pressed) {
            onDragEnd()
            return
        }
        onDrag(change, change.positionChange())
    }
}
