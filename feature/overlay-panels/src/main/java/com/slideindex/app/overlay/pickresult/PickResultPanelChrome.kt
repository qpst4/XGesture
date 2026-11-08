package com.slideindex.app.overlay.pickresult
import com.slideindex.app.ui.theme.LocalAppDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.ui.draw.rotate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import android.os.SystemClock
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slideindex.app.overlay.panels.R
import com.slideindex.app.overlay.overlayIsLandscape
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.util.HapticHelper
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.filled.MoreVert

val PickResultPanelMaxWidth = 400.dp
const val PickResultMaxVisibleTextLines = 7
private const val PickResultLandscapeMaxVisibleTextLines = 5
private const val PickResultPortraitMinTextBodyLines = 6
private const val PickResultLandscapeMinTextBodyLines = 4

/**
 * 搜索按钮长按直搜的配置。
 *
 * 长按搜索按钮会在按钮上方弹出横向滑选条，选择本次搜索的窗口形态（全屏 / 小窗），
 * 松手即按选中项搜索；[initialFullscreen] 是上一次使用的形态，作为初始选中项。
 */
data class PickResultSearchQuickLaunch(
    /** 小窗是否可选；未开启小窗时滑选条只剩「全屏」，此时长按直接搜索。 */
    val offerFreeWindow: Boolean,
    val initialFullscreen: Boolean,
    val onConfirm: (fullscreen: Boolean) -> Unit,
)

private val PickResultQuickLaunchItemWidth = 58.dp
private val PickResultQuickLaunchItemSpacing = 6.dp
private val PickResultQuickLaunchRowPadding = 8.dp
private val PickResultQuickLaunchGapAboveButton = 10.dp
private val PickResultQuickLaunchYCancel = 60.dp
private val PickResultQuickLaunchScreenMargin = 8.dp

/** 长按滑选条的可选项：开启小窗时两项（全屏 / 小窗），否则只剩全屏。 */
fun pickResultSearchQuickLaunchOptions(offerFreeWindow: Boolean): List<Boolean> =
    if (offerFreeWindow) listOf(true, false) else listOf(true)

/** 滑选条初始选中项：上次使用的形态；该形态不可用时落到首项。 */
fun pickResultSearchQuickLaunchStartIndex(
    options: List<Boolean>,
    initialFullscreen: Boolean,
): Int = options.indexOf(initialFullscreen).coerceAtLeast(0)

@Composable

fun pickResultMaxVisibleTextLines(): Int =
if (overlayIsLandscape()) PickResultLandscapeMaxVisibleTextLines else PickResultMaxVisibleTextLines

@Composable

fun pickResultMinTextBodyLines(): Int =
if (overlayIsLandscape()) PickResultLandscapeMinTextBodyLines else PickResultPortraitMinTextBodyLines
/** 分词 chip 正文行高比例，与 [PickResultWordTapBody] 一致。*/
private const val PickResultWordTapLineHeightRatio = 20f / 15f
/** chip 上下 padding 合计（各 4.dp）。*/
private const val PickResultChipVerticalPaddingDp = 8f
/** 分词 chip 行间距，?[PickResultWordTapBody] 一致。*/
private const val PickResultFlowRowLineSpacingDp = 4f
/** 点词正文区底?padding，避免末?chip 贴边被裁切。*/

val PickResultWordTapBottomContentPadding = 4.dp
/** 文本区高度上限：屏幕高度比例，避免大屏占满卡片。*/
private const val PickResultTextHeightScreenFractionCap = 0.50f
/**
* 取词正文区最大高度：按字号估算可?[PickResultMaxVisibleTextLines] 行分?chip?
* SELECT / EDIT 模式共用同一上限以保持一致体验。
*/

@Composable

fun pickResultWindowHeightDp(fraction: Float): Dp {
val density = LocalDensity.current
val containerHeight = with(density) {
LocalWindowInfo.current.containerSize.height.toDp()
}
return containerHeight * fraction
}

@Composable

fun pickResultMaxTextHeight(textSizeSp: Float): Dp {
val density = LocalDensity.current
val lineHeightDp = with(density) {
(textSizeSp * PickResultWordTapLineHeightRatio).sp.toDp()
}
val rowHeightDp = lineHeightDp + PickResultChipVerticalPaddingDp.dp
val lineSpacingDp = PickResultFlowRowLineSpacingDp.dp
val visibleLines = pickResultMaxVisibleTextLines()
val contentHeight = rowHeightDp * visibleLines +
lineSpacingDp * (visibleLines - 1) +
PickResultWordTapBottomContentPadding
val screenCap = pickResultWindowHeightDp(PickResultTextHeightScreenFractionCap)
return minOf(contentHeight, screenCap)
}
/** 面板为正文区分配的高度：7 行内?+ 正文区上?padding。*/

@Composable

fun pickResultTextBodyAllocatedHeight(textSizeSp: Float): Dp =
pickResultMaxTextHeight(textSizeSp) + PickResultTextBodyVerticalPadding

@Composable

fun pickResultMinTextBodyAllocatedHeight(textSizeSp: Float, lines: Int = 6): Dp {
val density = LocalDensity.current
val lineHeightDp = with(density) {
(textSizeSp * PickResultWordTapLineHeightRatio).sp.toDp()
}
val rowHeightDp = lineHeightDp + PickResultChipVerticalPaddingDp.dp
val lineSpacingDp = PickResultFlowRowLineSpacingDp.dp
val contentHeight = rowHeightDp * lines +
lineSpacingDp * (lines - 1).coerceAtLeast(0) +
PickResultWordTapBottomContentPadding
return contentHeight + PickResultTextBodyVerticalPadding
}
/** 翻译面板等独立区块标题行（含上下 padding）。*/
val PickResultTextSectionHeaderReservedHeight = 46.dp

/** 分 Tab 样式顶部药丸切换栏高度（含上下 padding 与边距）。*/
val PickResultTabHeaderReservedHeight = 46.dp

/** 取词面板：文本标题 + 来源切换 + 编辑工具栏合并行。*/
val PickResultTextSectionToolbarReservedHeight = 40.dp
/** 仅编辑工具栏行（翻译面板等无合并标题时使用）。*/

val PickResultTextToolbarReservedHeight = 38.dp
/** 底部操作栏（分享 / 复制 / 翻译等）。*/

val PickResultTextActionBarReservedHeight = 48.dp
/** 文本区内：工具栏与正文之间的垂直间距。*/

val PickResultTextToolbarBodySpacing = 4.dp
/** 文本区内：正文与操作栏之间的垂直间距（与操作栏下方分割区视觉平衡）。*/

val PickResultTextBodyActionBarSpacing = 4.dp
/** 工具栏、正文、操作栏间距合计。*/

val PickResultTextSectionInnerSpacing =
PickResultTextToolbarBodySpacing + PickResultTextBodyActionBarSpacing
/** 文本操作栏顶部留白（正文与操作栏之间）。*/

val PickResultTextActionBarTopPadding = PickResultTextBodyActionBarSpacing
/** 操作栏底部留白（与面板底边距同步插值；搜索区展开时为 0）。*/

val PickResultTextActionBarBottomPaddingWhenAlone = 4.dp
/** 正文区顶?padding（底部不留白，避免操作栏上方空隙偏大）。*/

val PickResultTextBodyTopPadding = 4.dp
/** 正文区上?padding 合计（与 [PickResultTextBody] paddedModifier 一致）。*/

val PickResultTextBodyVerticalPadding = 28.dp

/**
 * 非分词（长按选中）模式正文区的**高度下限**。
 *
 * 为什么需要它：这个模式的正文区原本是 `.heightIn(min = effectiveMaxHeight, max = effectiveMaxHeight)`
 * —— 把"屏幕剩余空间"当成内容高度**写死**，于是取两三个字也把面板撑到接近满屏（用户反馈）。
 * 现已改为按真实文字高度收缩，但纯按内容收缩会让短文本的面板缩成一条、选中手柄与工具条很局促，
 * 所以给一个地板高度。240dp 是用户真机试出来的观感取值。
 *
 * 注意它只是**下限**：文字多到超过可用空间时仍以可用空间为准（内部滚动）。
 */
val PickResultTextBodyMinVisibleHeight = 240.dp

fun pickResultTextSectionChromeReservedHeight(): Dp =
PickResultTextSectionToolbarReservedHeight +
PickResultTextActionBarReservedHeight +
PickResultTextSectionInnerSpacing
/** 编辑工具?+ 操作?+ 其间距（不含合并标题行）。*/

fun pickResultInteractiveTextChromeReservedHeight(): Dp =
PickResultTextToolbarReservedHeight +
PickResultTextActionBarReservedHeight +
PickResultTextSectionInnerSpacing

val PickResultPanelCardCorner = 28.dp

val PickResultPanelCardShape = RoundedCornerShape(PickResultPanelCardCorner)

val PickResultBottomPanelShape = RoundedCornerShape(
    topStart = PickResultPanelCardCorner,
    topEnd = PickResultPanelCardCorner,
    bottomStart = 0.dp,
    bottomEnd = 0.dp,
)

val PickResultPanelCardElevation = 16.dp

@Composable
fun Modifier.pickResultPanelCard(): Modifier {
    val isDark = LocalAppDarkTheme.current
    return this
        .shadow(
            elevation = PickResultPanelCardElevation,
            shape = PickResultPanelCardShape,
            clip = false,
        )
        .clip(PickResultPanelCardShape)
        .border(
            width = 0.5.dp,
            color = if (isDark) androidx.compose.ui.graphics.Color(0x28FFFFFF) else androidx.compose.ui.graphics.Color(0x18000000),
            shape = PickResultPanelCardShape,
        )
}

@Composable
fun Modifier.pickResultBottomPanelCard(suppressShadow: Boolean = false): Modifier {
    val isDark = LocalAppDarkTheme.current
    return this
        .then(
            if (!suppressShadow) {
                Modifier.shadow(
                    elevation = PickResultPanelCardElevation,
                    shape = PickResultBottomPanelShape,
                    clip = false,
                )
            } else {
                Modifier
            },
        )
        .clip(PickResultBottomPanelShape)
        .border(
            width = 0.5.dp,
            color = if (isDark) androidx.compose.ui.graphics.Color(0x28FFFFFF) else androidx.compose.ui.graphics.Color(0x18000000),
            shape = PickResultBottomPanelShape,
        )
}

/**
 * 取词面板底栏的搜索按钮。
 *
 * - 单击：切换搜索网格展开/收起；
 * - 长按：按钮上方弹出横向滑选条，滑动选择本次搜索的窗口形态（全屏 / 小窗），松手执行。
 *
 * [quickLaunch] 为 null 时（未设置取词默认搜索引擎）长按不响应。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PickResultSearchActionButton(
    enabled: Boolean,
    selected: Boolean,
    tint: Color,
    background: Color,
    buttonSize: Dp,
    cornerRadius: Dp,
    iconSize: Dp,
    quickLaunch: PickResultSearchQuickLaunch?,
    onClick: () -> Unit,
) {
    val options = remember(quickLaunch) {
        quickLaunch?.let { pickResultSearchQuickLaunchOptions(it.offerFreeWindow) }.orEmpty()
    }
    val quickLaunchEnabled = quickLaunch != null && enabled
    var anchorCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var isSelecting by remember { mutableStateOf(false) }
    var hoveredIndex by remember { mutableIntStateOf(-1) }
    var rowAnchorLeftInWindow by remember { mutableFloatStateOf(0f) }
    var dragStartFingerY by remember { mutableFloatStateOf(0f) }
    // 滑选抬手后短暂抑制点击：`clickable` 与长按手势的派发先后不做保证，避免同一次抬手被当成单击。
    var suppressClickUntilMs by remember { mutableLongStateOf(0L) }

    val density = LocalDensity.current
    val appContext = LocalContext.current.applicationContext
    val view = LocalView.current
    var appSettings by remember { mutableStateOf(AppSettings()) }
    LaunchedEffect(appContext) {
        com.slideindex.app.settings.SettingsFlowHolder.settingsFlow(appContext)
            ?.collect { appSettings = it }
    }
    val currentAppSettings = rememberUpdatedState(appSettings)
    val currentView = rememberUpdatedState(view)

    LaunchedEffect(quickLaunchEnabled) {
        if (!quickLaunchEnabled) {
            isSelecting = false
            hoveredIndex = -1
        }
    }

    val itemWidthPx = with(density) { PickResultQuickLaunchItemWidth.toPx() }
    val itemSpacingPx = with(density) { PickResultQuickLaunchItemSpacing.toPx() }
    val rowPaddingPx = with(density) { PickResultQuickLaunchRowPadding.toPx() }
    val totalItemStridePx = itemWidthPx + itemSpacingPx
    val yCancelThresholdPx = with(density) { PickResultQuickLaunchYCancel.toPx() }
    val screenMarginPx = with(density) { PickResultQuickLaunchScreenMargin.toPx() }
    val popupOffsetAboveButtonPx = with(density) {
        (buttonSize + PickResultQuickLaunchGapAboveButton).toPx()
    }
    val screenWidthPx = LocalWindowInfo.current.containerSize.width.toFloat()

    fun quickLaunchRowWidthPx(): Float {
        val itemCount = options.size
        return rowPaddingPx * 2 + itemCount * itemWidthPx +
            (itemCount - 1).coerceAtLeast(0) * itemSpacingPx
    }

    fun clampRowAnchorLeft(left: Float): Float {
        val rowWidth = quickLaunchRowWidthPx()
        return left.coerceIn(
            screenMarginPx,
            (screenWidthPx - rowWidth - screenMarginPx).coerceAtLeast(screenMarginPx),
        )
    }

    fun indexAtFingerX(fingerX: Float): Int {
        val localX = fingerX - rowAnchorLeftInWindow - rowPaddingPx
        return (localX / totalItemStridePx).toInt().coerceIn(0, options.lastIndex)
    }

    fun anchorRowUnderFinger(fingerX: Float, underIndex: Int) {
        val desiredLeft = fingerX - rowPaddingPx - underIndex * totalItemStridePx - itemWidthPx / 2f
        rowAnchorLeftInWindow = clampRowAnchorLeft(desiredLeft)
    }

    Box(modifier = Modifier.onGloballyPositioned { anchorCoordinates = it }) {
        Box(
            modifier = Modifier
                .size(buttonSize)
                .clip(RoundedCornerShape(cornerRadius))
                .background(background)
                .then(
                    if (quickLaunchEnabled) {
                        Modifier.pointerInput(options, quickLaunch) {
                            val launch = quickLaunch
                            // 自己接管长按手势：`detectDragGesturesAfterLongPress` 在抬手时要求
                            // up 未被消费，而同一节点的 clickable 是内层、会先拿走这次 up，
                            // 于是"长按后原地松手"被它判成 cancel（什么都不做），必须滑动一下才触发。
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                if (options.isEmpty()) return@awaitEachGesture
                                // 长按成立前抬手 / 移动过大 → null，这次仍按普通单击处理
                                val longPress = awaitLongPressOrCancellation(down.id)
                                    ?: return@awaitEachGesture
                                val coords = anchorCoordinates ?: return@awaitEachGesture

                                val startIndex = pickResultSearchQuickLaunchStartIndex(
                                    options,
                                    launch.initialFullscreen,
                                )
                                val finger = coords.localToWindow(longPress.position)
                                dragStartFingerY = finger.y
                                anchorRowUnderFinger(finger.x, startIndex)
                                hoveredIndex = startIndex
                                isSelecting = true
                                // 长按一成立就封住点击：up 由谁先消费不做保证，这里两头都堵住。
                                suppressClickUntilMs = Long.MAX_VALUE
                                HapticHelper.appTick(currentView.value, currentAppSettings.value)

                                var confirmedIndex = startIndex
                                var released = false
                                try {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == longPress.id }
                                        if (change == null || !change.pressed) {
                                            // 抬手即确认：这里**不看 isConsumed**（旧实现看的正是它）
                                            released = event.changes.none { it.pressed }
                                            if (released) change?.consume()
                                            break
                                        }
                                        if (change.positionChanged()) {
                                            change.consume()
                                            val current = coords.localToWindow(change.position)
                                            if (abs(current.y - dragStartFingerY) > yCancelThresholdPx) {
                                                if (hoveredIndex != -1) {
                                                    hoveredIndex = -1
                                                    HapticHelper.appTick(currentView.value, currentAppSettings.value)
                                                }
                                            } else {
                                                val index = indexAtFingerX(current.x)
                                                if (index != hoveredIndex) {
                                                    hoveredIndex = index
                                                    HapticHelper.appTick(currentView.value, currentAppSettings.value)
                                                }
                                            }
                                            confirmedIndex = hoveredIndex
                                        }
                                    }
                                } finally {
                                    isSelecting = false
                                    hoveredIndex = -1
                                    suppressClickUntilMs = SystemClock.uptimeMillis() + 250L
                                }
                                if (released && confirmedIndex in options.indices) {
                                    launch.onConfirm(options[confirmedIndex])
                                }
                            }
                        }
                    } else {
                        Modifier
                    }
                )
                .clickable(enabled = enabled) {
                    if (!isSelecting && SystemClock.uptimeMillis() >= suppressClickUntilMs) onClick()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (selected) Icons.Filled.Search else Icons.Outlined.Search,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(iconSize),
            )
        }

        if (isSelecting && quickLaunch != null) {
            val coords = anchorCoordinates
            if (coords != null && coords.isAttached) {
                val anchorTopLeft = coords.boundsInWindow().topLeft
                // 滑选条强制 Ltr：底栏「复制按钮在右」是靠 Rtl 反转图标顺序实现的
                // （见 CompactIconToolbar），而这里的位置与命中都按窗口 x 从左往右算。
                // Popup 的方向取自调用点，Rtl 下会把 TopStart 锚到按钮右上角并反向扣除
                // offset.x，内容 Row 也会从右往左摆 —— 表现为「右滑选中态却往左走」。
                // 与 PickResultDropdownMenuInLtr 同一套处理。
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Popup(
                        alignment = Alignment.TopStart,
                        offset = IntOffset(
                            (rowAnchorLeftInWindow - anchorTopLeft.x).roundToInt(),
                            (-popupOffsetAboveButtonPx).roundToInt(),
                        ),
                    ) {
                        PickResultSearchQuickLaunchRow(
                            options = options,
                            hoveredIndex = hoveredIndex,
                        )
                    }
                }
            }
        }
    }
}

/** 长按直搜的窗口形态滑选条：选中项放大并高亮，未滑到任何项时松手取消。 */
@Composable
private fun PickResultSearchQuickLaunchRow(
    options: List<Boolean>,
    hoveredIndex: Int,
) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = Modifier
            .shadow(12.dp, shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, shape)
            .border(
                width = 0.8.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                shape = shape,
            )
            .padding(horizontal = PickResultQuickLaunchRowPadding, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PickResultQuickLaunchItemSpacing),
    ) {
        options.forEachIndexed { index, fullscreen ->
            val hovered = index == hoveredIndex
            val itemScale by animateFloatAsState(
                targetValue = if (hovered) 1.08f else 1f,
                label = "pickSearchQuickLaunchScale",
            )
            Column(
                modifier = Modifier
                    .width(PickResultQuickLaunchItemWidth)
                    .graphicsLayer {
                        scaleX = itemScale
                        scaleY = itemScale
                    }
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (hovered) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                    )
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                val contentColor = if (hovered) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                Icon(
                    imageVector = if (fullscreen) {
                        Icons.Outlined.Fullscreen
                    } else {
                        Icons.Outlined.PictureInPictureAlt
                    },
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(
                        if (fullscreen) {
                            R.string.search_panel_overlay_fullscreen
                        } else {
                            R.string.search_panel_app_quick_action_free_window
                        },
                    ),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 12.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    ),
                    color = contentColor,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun PickResultSectionHeader(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    collapsible: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (collapsible) {
                    Modifier.clickable(onClick = onToggle)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
        )
        if (collapsible) {
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable

fun PickResultTextActionBar(
    enabled: Boolean,
    translateEnabled: Boolean = true,
    translateSelected: Boolean = false,
    showSearch: Boolean = true,
    searchSelected: Boolean = false,
    showOpenLink: Boolean = false,
    openLinkChooserExpanded: Boolean = false,
    openLinkChoices: List<String> = emptyList(),
    onSearch: () -> Unit = {},
    searchQuickLaunch: PickResultSearchQuickLaunch? = null,
    onOpenLink: () -> Unit = {},
    onOpenLinkChoice: (String) -> Unit = {},
    onDismissOpenLinkChooser: () -> Unit = {},
    onShare: () -> Unit,
    /** 长按分享键 = 把当前这段文字起成系统拖拽；null 时保持普通分享键。 */
    onStartDrag: (() -> Unit)? = null,
    copyDismissEnabled: Boolean = false,
    onCopy: () -> Unit,
    onCopyKeepOpen: () -> Unit = onCopy,
    onTranslate: () -> Unit,
    onPinToScreen: (() -> Unit)? = null,
    onStash: (() -> Unit)? = null,
    /** 生成二维码；null 时菜单里不显示这一项。 */
    onGenerateQr: (() -> Unit)? = null,
    /** 用其他应用处理（二级页列出 PROCESS_TEXT 入口）；null 时菜单里不显示这一项。 */
    onProcessText: ((PickResultProcessTextTarget) -> Unit)? = null,
    onTrimSpaces: (() -> Unit)? = null,
    hasImageContent: Boolean = false,
    onImageSearch: (() -> Unit)? = null,
    onSaveScreenshot: (() -> Unit)? = null,
    onShareScreenshot: (() -> Unit)? = null,
    bottomPadding: Dp = PickResultTextActionBarBottomPaddingWhenAlone,
    lightweightDrag: Boolean = false,
    compactEmbedded: Boolean = true,
    copyButtonPosition: com.slideindex.app.settings.PickResultCopyButtonPosition =
        com.slideindex.app.settings.PickResultCopyButtonPosition.LEFT,
) {
    var justCopied by remember { mutableStateOf(false) }
    var moreMenuExpanded by remember { mutableStateOf(false) }
    // 「用其他应用处理」的二级页：点进去才查一次 PROCESS_TEXT 列表（图标解码放在 IO）。
    var processTextMenuExpanded by remember { mutableStateOf(false) }
    var processTextTargets by remember { mutableStateOf<List<PickResultProcessTextTarget>?>(null) }
    val processTextContext = LocalContext.current
    LaunchedEffect(processTextMenuExpanded) {
        if (processTextMenuExpanded && processTextTargets == null) {
            processTextTargets = withContext(Dispatchers.IO) {
                runCatching {
                    PickResultProcessText.listTargets(processTextContext.applicationContext)
                }.getOrDefault(emptyList())
            }
        }
    }

    LaunchedEffect(justCopied) {
        if (justCopied) {
            delay(750L)
            justCopied = false
        }
    }
    val handleCopyAction: () -> Unit = {
        justCopied = true
        onCopy()
    }
    val handleCopyKeepOpenAction: () -> Unit = {
        justCopied = true
        onCopyKeepOpen()
    }

    val isDark = LocalAppDarkTheme.current

    if (compactEmbedded) {
        val copyOnRight =
            copyButtonPosition == com.slideindex.app.settings.PickResultCopyButtonPosition.RIGHT

        @Composable
        fun CompactPrimaryChip() {
            if (!enabled && hasImageContent && onImageSearch != null) {
                Row(
                    modifier = Modifier
                        .height(30.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onImageSearch)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ImageSearch,
                        contentDescription = null,
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.pick_result_action_image_search),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                        color = androidx.compose.ui.graphics.Color.White
                    )
                }
            } else {
                val successColor = if (isDark) androidx.compose.ui.graphics.Color(0xFF2ED573) else androidx.compose.ui.graphics.Color(0xFF22B14C)
                val baseCopyBg = if (enabled) {
                    if (justCopied) successColor else MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                }
                val animatedCopyBg by animateColorAsState(
                    targetValue = baseCopyBg,
                    label = "compactCopyBg"
                )
                val copyScale by animateFloatAsState(
                    targetValue = if (justCopied) 1.05f else 1.0f,
                    label = "compactCopyScale"
                )
                val copyTint = if (enabled) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.5f)

                Row(
                    modifier = Modifier
                        .height(40.dp)
                        .graphicsLayer {
                            scaleX = copyScale
                            scaleY = copyScale
                        }
                        .clip(RoundedCornerShape(20.dp))
                        .background(animatedCopyBg)
                        .then(
                            if (copyDismissEnabled) {
                                Modifier.combinedClickable(
                                    enabled = enabled,
                                    onClick = handleCopyAction,
                                    onLongClick = handleCopyKeepOpenAction,
                                    )
                            } else {
                                Modifier.clickable(
                                    enabled = enabled,
                                    onClick = handleCopyKeepOpenAction,
                                )
                            }
                        )
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (justCopied) Icons.Filled.Check else Icons.Outlined.ContentCopy,
                        contentDescription = null,
                        tint = copyTint,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(if (justCopied) R.string.pick_result_copied else R.string.pick_result_copy_selection),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 14.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                        ),
                        color = copyTint
                    )
                }
            }
        }

        @Composable
        fun CompactIconToolbar() {
            CompositionLocalProvider(
                LocalLayoutDirection provides if (copyOnRight) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 2.1 打开链接（位于搜索左侧，有链接时外显）
                if (showOpenLink) {
                    Box {
                        IconButton(
                            onClick = onOpenLink,
                            enabled = enabled,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(21.dp))
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                                contentDescription = stringResource(R.string.pick_result_open_link),
                                tint = if (enabled) (if (isDark) androidx.compose.ui.graphics.Color(0xFFE0E0E6) else androidx.compose.ui.graphics.Color(0xFF333333)) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        if (openLinkChoices.isNotEmpty()) {
                            PickResultDropdownMenuInLtr(
                                expanded = openLinkChooserExpanded,
                                onDismissRequest = onDismissOpenLinkChooser,
                                modifier = Modifier.widthIn(max = 240.dp),
                            ) {
                                openLinkChoices.forEachIndexed { index, url ->
                                    if (index > 0) {
                                        Box(
                                            modifier = Modifier
                                                .padding(horizontal = 12.dp)
                                                .fillMaxWidth()
                                                .height(0.5.dp)
                                                .background(
                                            pickResultDropdownMenuDividerColor(isDark)
                                        )
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = url,
                                                maxLines = 3,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontSize = 12.sp,
                                                    lineHeight = 16.sp,
                                                    color = if (isDark) androidx.compose.ui.graphics.Color(0xFFECECED)
                                                        else androidx.compose.ui.graphics.Color(0xFF1F1F1F),
                                                ),
                                            )
                                        },
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                            horizontal = 12.dp,
                                            vertical = 6.dp,
                                        ),
                                        onClick = {
                                            onDismissOpenLinkChooser()
                                            onOpenLinkChoice(url)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // 2.2 搜索（正对图片底栏的以图搜图，无厚重蓝色底衬）
                if (showSearch) {
                    val searchTint = when {
                        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        searchSelected -> MaterialTheme.colorScheme.primary
                        else -> if (isDark) androidx.compose.ui.graphics.Color(0xFFE0E0E6) else androidx.compose.ui.graphics.Color(0xFF333333)
                    }
                    PickResultSearchActionButton(
                        enabled = enabled,
                        selected = searchSelected,
                        tint = searchTint,
                        background = androidx.compose.ui.graphics.Color.Transparent,
                        buttonSize = 42.dp,
                        cornerRadius = 21.dp,
                        iconSize = 22.dp,
                        quickLaunch = searchQuickLaunch,
                        onClick = onSearch,
                    )
                }

                // 2.3 翻译（正对图片底栏的保存，无厚重蓝色底衬）
                val translateTint = when {
                    !enabled || !translateEnabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    translateSelected -> MaterialTheme.colorScheme.primary
                    else -> if (isDark) androidx.compose.ui.graphics.Color(0xFFE0E0E6) else androidx.compose.ui.graphics.Color(0xFF333333)
                }
                IconButton(
                    onClick = onTranslate,
                    enabled = enabled && translateEnabled,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(21.dp))
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Translate,
                        contentDescription = null,
                        tint = translateTint,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // 2.3 分享（直接外显，正对图片底栏的分享）。长按 = 拖拽出口：
                // 面板里内容的长按已经被「分词 / 复制图片」占住，拖拽只能借工具键起手。
                // 用 `combinedClickable` 而不是 IconButton：IconButton 只有 click，
                // 长按会被它内部的 clickable 吃掉，起不了拖。
                val shareTint = if (isDark) {
                    androidx.compose.ui.graphics.Color(0xFFE0E0E6)
                } else {
                    androidx.compose.ui.graphics.Color(0xFF333333)
                }
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(21.dp))
                        .combinedClickable(
                            enabled = enabled,
                            onClick = onShare,
                            onLongClick = onStartDrag,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = stringResource(R.string.pick_result_share),
                        tint = if (enabled) shareTint else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                        modifier = Modifier.size(22.dp)
                    )
                }

                // 2.4 更多菜单（正对图片底栏的更多）
                Box {
                    IconButton(
                        onClick = { moreMenuExpanded = true },
                        enabled = enabled,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(21.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.pick_result_action_more),
                            tint = if (enabled) (if (isDark) androidx.compose.ui.graphics.Color(0xFFE0E0E6) else androidx.compose.ui.graphics.Color(0xFF333333)) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    PickResultDropdownMenuInLtr(
                        expanded = moreMenuExpanded,
                        onDismissRequest = {
                            moreMenuExpanded = false
                            processTextMenuExpanded = false
                        }
                    ) {
                        if (processTextMenuExpanded) {
                            PickResultProcessTextTargetList(
                                targets = processTextTargets.orEmpty(),
                                loading = processTextTargets == null,
                                onBack = { processTextMenuExpanded = false },
                                onSelect = { target ->
                                    moreMenuExpanded = false
                                    processTextMenuExpanded = false
                                    onProcessText?.invoke(target)
                                },
                            )
                        } else {
                            onPinToScreen?.let { pinAction ->
                                PickResultActionMenuItem(
                                    label = stringResource(R.string.pick_result_pin),
                                    icon = Icons.Outlined.PushPin,
                                    onClick = {
                                        moreMenuExpanded = false
                                        pinAction()
                                    },
                                )
                            }
                            onStash?.let { stashAction ->
                                PickResultActionMenuItem(
                                    label = stringResource(R.string.pick_result_stash),
                                    icon = Icons.Outlined.Archive,
                                    onClick = {
                                        moreMenuExpanded = false
                                        stashAction()
                                    },
                                )
                            }
                            onGenerateQr?.let { generateQr ->
                                PickResultActionMenuItem(
                                    label = stringResource(R.string.pick_result_qr),
                                    icon = Icons.Outlined.QrCode,
                                    onClick = {
                                        moreMenuExpanded = false
                                        generateQr()
                                    },
                                )
                            }
                            onProcessText?.let {
                                PickResultActionMenuItem(
                                    label = stringResource(R.string.pick_result_process_text),
                                    icon = Icons.Outlined.Apps,
                                    onClick = { processTextMenuExpanded = true },
                                )
                            }
                        }
                    }
                }
            }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = 4.dp,
                    bottom = bottomPadding,
                    start = 2.dp,
                    end = 2.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (copyOnRight) {
                CompactIconToolbar()
                CompactPrimaryChip()
            } else {
                CompactPrimaryChip()
                CompactIconToolbar()
            }
        }
        return
    }

    val pillBg = if (isDark) androidx.compose.ui.graphics.Color(0x3BFFFFFF) else androidx.compose.ui.graphics.Color(0x1E000000)
    val pillBorder = if (isDark) androidx.compose.ui.graphics.Color(0x28FFFFFF) else androidx.compose.ui.graphics.Color(0x18000000)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                top = PickResultTextActionBarTopPadding,
                bottom = bottomPadding,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Floating Action Pill
        Row(
            modifier = Modifier
                .shadow(
                    elevation = if (!lightweightDrag) 8.dp else 0.dp,
                    shape = RoundedCornerShape(24.dp),
                    spotColor = if (isDark) androidx.compose.ui.graphics.Color(0x66000000) else androidx.compose.ui.graphics.Color(0x33000000),
                    ambientColor = androidx.compose.ui.graphics.Color(0x22000000),
                )
                .clip(RoundedCornerShape(24.dp))
                .background(pillBg)
                .border(0.6.dp, pillBorder, RoundedCornerShape(24.dp))
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (!enabled && hasImageContent && onImageSearch != null) {
                // 当没有文本但有图片时，主胶囊优雅变身为【以图搜图】
                Row(
                    modifier = Modifier
                        .height(38.dp)
                        .shadow(
                            elevation = 4.dp,
                            shape = RoundedCornerShape(19.dp),
                            spotColor = MaterialTheme.colorScheme.primary,
                        )
                        .clip(RoundedCornerShape(19.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onImageSearch)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ImageSearch,
                        contentDescription = null,
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.pick_result_action_image_search),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                        color = androidx.compose.ui.graphics.Color.White
                    )
                }
            } else {
                // 1. Primary Action: Copy Button with Morphing Animation
                val successColor = if (isDark) androidx.compose.ui.graphics.Color(0xFF2ED573) else androidx.compose.ui.graphics.Color(0xFF22B14C)
                val baseCopyBg = if (enabled) {
                    if (justCopied) successColor else MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                }
                val animatedCopyBg by animateColorAsState(
                    targetValue = baseCopyBg,
                    label = "floatingCopyBg"
                )
                val copyScale by animateFloatAsState(
                    targetValue = if (justCopied) 1.05f else 1.0f,
                    label = "floatingCopyScale"
                )
                val copyTint = if (enabled) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.5f)

                Row(
                    modifier = Modifier
                        .height(38.dp)
                        .graphicsLayer {
                            scaleX = copyScale
                            scaleY = copyScale
                        }
                        .shadow(
                            elevation = if (enabled) 4.dp else 0.dp,
                            shape = RoundedCornerShape(19.dp),
                            spotColor = if (justCopied) successColor else MaterialTheme.colorScheme.primary,
                        )
                        .clip(RoundedCornerShape(19.dp))
                        .background(animatedCopyBg)
                        .then(
                            if (copyDismissEnabled) {
                                Modifier.combinedClickable(
                                    enabled = enabled,
                                    onClick = handleCopyAction,
                                    onLongClick = handleCopyKeepOpenAction,
                                )
                            } else {
                                Modifier.clickable(enabled = enabled, onClick = handleCopyKeepOpenAction)
                            }
                        )
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (justCopied) Icons.Filled.Check else Icons.Outlined.ContentCopy,
                        contentDescription = null,
                        tint = copyTint,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(if (justCopied) R.string.pick_result_copied else R.string.pick_result_copy_selection),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                        color = copyTint
                    )
                }
            }

            // 1.1 Image Search Action (若有图且有文时展示独立的以图搜图按钮)
            if (enabled && hasImageContent && onImageSearch != null) {
                IconButton(
                    onClick = onImageSearch,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(19.dp))
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ImageSearch,
                        contentDescription = stringResource(R.string.pick_result_action_image_search),
                        tint = if (isDark) androidx.compose.ui.graphics.Color(0xFFE0E0E6) else androidx.compose.ui.graphics.Color(0xFF333333),
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            // 2. Search Action
            if (showSearch) {
                val searchBg = when {
                    !enabled -> androidx.compose.ui.graphics.Color.Transparent
                    searchSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    else -> androidx.compose.ui.graphics.Color.Transparent
                }
                val searchTint = when {
                    !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    searchSelected -> MaterialTheme.colorScheme.primary
                    else -> if (isDark) androidx.compose.ui.graphics.Color(0xFFE0E0E6) else androidx.compose.ui.graphics.Color(0xFF333333)
                }
                PickResultSearchActionButton(
                    enabled = enabled,
                    selected = searchSelected,
                    tint = searchTint,
                    background = searchBg,
                    buttonSize = 38.dp,
                    cornerRadius = 19.dp,
                    iconSize = 19.dp,
                    quickLaunch = searchQuickLaunch,
                    onClick = onSearch,
                )
            }

            // 3. Translate Action
            val translateBg = when {
                !enabled || !translateEnabled -> androidx.compose.ui.graphics.Color.Transparent
                translateSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                else -> androidx.compose.ui.graphics.Color.Transparent
            }
            val translateTint = when {
                !enabled || !translateEnabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                translateSelected -> MaterialTheme.colorScheme.primary
                else -> if (isDark) androidx.compose.ui.graphics.Color(0xFFE0E0E6) else androidx.compose.ui.graphics.Color(0xFF333333)
            }
            IconButton(
                onClick = onTranslate,
                enabled = enabled && translateEnabled,
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(19.dp))
                    .background(translateBg)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Translate,
                    contentDescription = null,
                    tint = translateTint,
                    modifier = Modifier.size(19.dp)
                )
            }

            // Small Divider
            Box(
                modifier = Modifier
                    .size(width = 1.dp, height = 16.dp)
                    .background(if (isDark) androidx.compose.ui.graphics.Color(0x33FFFFFF) else androidx.compose.ui.graphics.Color(0x24000000))
            )

            // 4. More Actions (DropdownMenu)
            Box {
                IconButton(
                    onClick = { moreMenuExpanded = true },
                    enabled = enabled,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(19.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.pick_result_action_more),
                        tint = if (enabled) (if (isDark) androidx.compose.ui.graphics.Color(0xFFE0E0E6) else androidx.compose.ui.graphics.Color(0xFF333333)) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                        modifier = Modifier.size(19.dp)
                    )
                }

                PickResultDropdownMenuInLtr(
                    expanded = moreMenuExpanded,
                    onDismissRequest = { moreMenuExpanded = false }
                ) {
                    PickResultActionMenuItem(
                        label = stringResource(R.string.pick_result_share),
                        icon = Icons.Outlined.Share,
                        onClick = {
                            moreMenuExpanded = false
                            onShare()
                        },
                    )
                    if (hasImageContent && onSaveScreenshot != null) {
                        PickResultActionMenuItem(
                            label = stringResource(R.string.pick_result_action_save_image),
                            icon = Icons.Outlined.Save,
                            onClick = {
                                moreMenuExpanded = false
                                onSaveScreenshot()
                            },
                        )
                    }
                    if (hasImageContent && onShareScreenshot != null) {
                        PickResultActionMenuItem(
                            label = stringResource(R.string.pick_result_action_share_image),
                            icon = Icons.Outlined.Image,
                            onClick = {
                                moreMenuExpanded = false
                                onShareScreenshot()
                            },
                        )
                    }
                    onPinToScreen?.let { pinAction ->
                        PickResultActionMenuItem(
                            label = stringResource(R.string.pick_result_pin),
                            icon = Icons.Outlined.PushPin,
                            onClick = {
                                moreMenuExpanded = false
                                pinAction()
                            },
                        )
                    }
                    onStash?.let { stashAction ->
                        PickResultActionMenuItem(
                            label = stringResource(R.string.pick_result_stash),
                            icon = Icons.Outlined.Archive,
                            onClick = {
                                moreMenuExpanded = false
                                stashAction()
                            },
                        )
                    }
                    if (onTrimSpaces != null) {
                        PickResultActionMenuItem(
                            label = stringResource(R.string.float_ball_action_trim_spaces),
                            icon = Icons.Outlined.UnfoldLess,
                            iconModifier = Modifier.rotate(90f),
                            onClick = {
                                moreMenuExpanded = false
                                onTrimSpaces()
                            },
                        )
                    }
                    if (showOpenLink) {
                        PickResultActionMenuItem(
                            label = stringResource(R.string.pick_result_open_link),
                            icon = Icons.AutoMirrored.Outlined.OpenInNew,
                            onClick = {
                                moreMenuExpanded = false
                                onOpenLink()
                            },
                        )
                    }
                }
            }
        }
    }
}

/** 取词面板统一下拉容器（与打开链接菜单一致）。 */
fun pickResultDropdownMenuDividerColor(isDark: Boolean): Color =
    if (isDark) Color(0x24FFFFFF) else Color(0x14000000)

/** 二级页：系统文本菜单里那些第三方 App 入口（`ACTION_PROCESS_TEXT`）。 */
@Composable
private fun PickResultProcessTextTargetList(
    targets: List<PickResultProcessTextTarget>,
    loading: Boolean,
    onBack: () -> Unit,
    onSelect: (PickResultProcessTextTarget) -> Unit,
) {
    val isDark = LocalAppDarkTheme.current
    val textColor = if (isDark) Color(0xFFECECED) else Color(0xFF1F1F1F)
    val iconTint = if (isDark) Color(0xFFE0E0E6) else Color(0xFF333333)
    DropdownMenuItem(
        text = {
            Text(
                text = stringResource(R.string.pick_result_process_text),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, color = textColor),
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = iconTint,
            )
        },
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        onClick = onBack,
    )
    Box(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .height(0.5.dp)
            .background(pickResultDropdownMenuDividerColor(isDark)),
    )
    when {
        loading -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        }

        targets.isEmpty() -> Text(
            text = stringResource(R.string.pick_result_process_text_empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
        )

        else -> targets.forEach { target ->
            PickResultProcessTextMenuItem(target = target, onClick = { onSelect(target) })
        }
    }
}

@Composable
private fun PickResultProcessTextMenuItem(
    target: PickResultProcessTextTarget,
    onClick: () -> Unit,
) {
    val isDark = LocalAppDarkTheme.current
    val textColor = if (isDark) Color(0xFFECECED) else Color(0xFF1F1F1F)
    val iconTint = if (isDark) Color(0xFFE0E0E6) else Color(0xFF333333)
    val iconBitmap = target.icon
    DropdownMenuItem(
        text = {
            Text(
                text = target.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, color = textColor),
            )
        },
        leadingIcon = {
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap,
                    contentDescription = null,
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(5.dp)),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Apps,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = iconTint,
                )
            }
        },
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        onClick = onClick,
    )
}

@Composable

fun PickResultToolbarIcon(
icon: ImageVector,
enabled: Boolean,
onClick: () -> Unit,
selected: Boolean = false,
) {
IconButton(
onClick = onClick,
enabled = enabled,
modifier = Modifier.size(40.dp),
) {
Icon(
imageVector = icon,
contentDescription = null,
modifier = Modifier.size(22.dp),
tint = when {
!enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
selected -> MaterialTheme.colorScheme.primary
else -> MaterialTheme.colorScheme.onSurface
},
)
}
}
/** 使用屏幕空间 dragAmount 驱动折叠，避免拖动时行随布局移动导致 local Y 失真。*/

fun Modifier.pickResultLinkedVerticalDrag(
onDragDelta: (dragAmount: Float) -> Unit,
onDragEnd: () -> Unit,
): Modifier = composed {
val onDragDeltaState = rememberUpdatedState(onDragDelta)
val onDragEndState = rememberUpdatedState(onDragEnd)
this.then(
Modifier.pointerInput(Unit) {
detectPickResultLinkedVerticalDragGestures(
onDragDelta = { onDragDeltaState.value(it) },
onDragEnd = { onDragEndState.value() },
)
},
)
}
/**
* 垂直拖动优先于子?clickable：未超过 slop 时不消费事件，短按仍可点击；
* 判定为垂直拖动后再消费并上报增量。
*/
suspend fun androidx.compose.ui.input.pointer.PointerInputScope
.detectPickResultLinkedVerticalDragGestures(
onDragDelta: (dragAmount: Float) -> Unit,
onDragEnd: () -> Unit,
) {
awaitEachGesture {
val down = awaitFirstDown(requireUnconsumed = false)
val pointerId = down.id
val touchSlop = viewConfiguration.touchSlop
var dragging = false
var totalX = 0f
var totalY = 0f
while (true) {
val event = awaitPointerEvent()
val change = event.changes.firstOrNull { it.id == pointerId } ?: break
if (!change.pressed) {
if (dragging) {
onDragEnd()
}
break
}
val delta = change.positionChange()
if (!dragging) {
if (delta != Offset.Zero) {
totalX += delta.x
totalY += delta.y
}
if (abs(totalY) > touchSlop && abs(totalY) > abs(totalX)) {
dragging = true
}
} else if (delta.y != 0f) {
change.consume()
onDragDelta(delta.y)
}
}
}
}
/**
* 仅当按下与抬起都在面板外、且移动未超?slop 时关闭面板；
* 按下在面板内时本次手势不触发关闭（避免上滑滑出面板后?dismiss）。
*/
suspend fun androidx.compose.ui.input.pointer.PointerInputScope
.detectPickResultDismissOutsidePanelTap(
panelBoundsInRoot: () -> Rect,
onDismiss: () -> Unit,
) {
val touchSlop = viewConfiguration.touchSlop
awaitEachGesture {
val down = awaitFirstDown(requireUnconsumed = false)
val bounds = panelBoundsInRoot()
if (bounds.width <= 0f || bounds.height <= 0f) return@awaitEachGesture
if (bounds.contains(down.position)) return@awaitEachGesture
val pointerId = down.id
var totalMove = Offset.Zero
while (true) {
val event = awaitPointerEvent()
val change = event.changes.firstOrNull { it.id == pointerId } ?: break
if (!change.pressed) {
if (!bounds.contains(change.position) && totalMove.getDistance() <= touchSlop) {
onDismiss()
}
break
}
val delta = change.positionChange()
if (delta != Offset.Zero) {
totalMove += delta
}
}
}
}

@Composable

fun pickResultTranslateErrorLabel(code: String): String = when (code) {
"mlkit_model_not_installed" -> stringResource(R.string.float_ball_translate_error_model_missing)
"translate_engine_not_installed" -> stringResource(R.string.float_ball_translate_error_engine_missing)
"wifi_required" -> stringResource(R.string.float_ball_translate_error_wifi_required)
"unsupported_language" -> stringResource(R.string.float_ball_translate_error_unsupported_language)
"translate_unavailable" -> stringResource(R.string.float_ball_translate_error_unavailable)
"network_error", "http_403", "http_429", "http_500" ->
stringResource(R.string.float_ball_translate_error_network)
else -> code
}

@Composable
fun PickResultSegmentedTabHeader(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    pagerProgress: Float = selectedTab.toFloat(),
) {
    val isDark = LocalAppDarkTheme.current
    val containerBg = if (isDark) {
        androidx.compose.ui.graphics.Color(0xFF232429)
    } else {
        androidx.compose.ui.graphics.Color(0xFFEAEBED)
    }
    val activeBg = if (isDark) {
        androidx.compose.ui.graphics.Color(0xFF383A40)
    } else {
        androidx.compose.ui.graphics.Color(0xFFFFFFFF)
    }
    val density = LocalDensity.current
    var tab0WidthPx by remember { mutableIntStateOf(0) }
    var tab1WidthPx by remember { mutableIntStateOf(0) }
    var tabHeightPx by remember { mutableIntStateOf(0) }
    val progress = pagerProgress.coerceIn(0f, 1f)
    val tabSpacingPx = with(density) { 4.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .background(containerBg, CircleShape)
                .border(
                    width = 0.5.dp,
                    color = if (isDark) androidx.compose.ui.graphics.Color(0x28FFFFFF) else androidx.compose.ui.graphics.Color(0x18000000),
                    shape = CircleShape
                )
                .padding(3.dp)
        ) {
            if (tab0WidthPx > 0 && tabHeightPx > 0) {
                val thumbWidthPx = tab0WidthPx + (tab1WidthPx - tab0WidthPx) * progress
                val thumbOffsetPx = (tab0WidthPx + tabSpacingPx) * progress
                Box(
                    modifier = Modifier
                        .offset { IntOffset(thumbOffsetPx.roundToInt(), 0) }
                        .width(with(density) { thumbWidthPx.toDp() })
                        .height(with(density) { tabHeightPx.toDp() })
                        .shadow(elevation = 2.dp, shape = CircleShape, clip = false)
                        .background(activeBg, CircleShape)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                PickResultSegmentedTabItem(
                    selected = selectedTab == 0,
                    icon = Icons.Outlined.TextFields,
                    label = stringResource(R.string.float_ball_pick_panel_tab_text),
                    onClick = { onTabSelected(0) },
                    modifier = Modifier.onSizeChanged {
                        if (tab0WidthPx != it.width) tab0WidthPx = it.width
                        if (tabHeightPx != it.height) tabHeightPx = it.height
                    }
                )
                PickResultSegmentedTabItem(
                    selected = selectedTab == 1,
                    icon = Icons.Outlined.Image,
                    label = stringResource(R.string.float_ball_pick_panel_tab_image),
                    onClick = { onTabSelected(1) },
                    modifier = Modifier.onSizeChanged {
                        if (tab1WidthPx != it.width) tab1WidthPx = it.width
                        if (tabHeightPx != it.height) tabHeightPx = it.height
                    }
                )
            }
        }
    }
}

@Composable
private fun PickResultSegmentedTabItem(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val targetContentColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
    }
    val contentColor by animateColorAsState(
        targetValue = targetContentColor,
        label = "tabContentColor"
    )

    Row(
        modifier = modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal
            ),
            color = contentColor
        )
    }
}
