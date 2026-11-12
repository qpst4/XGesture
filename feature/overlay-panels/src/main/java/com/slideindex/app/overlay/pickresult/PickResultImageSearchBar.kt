package com.slideindex.app.overlay.pickresult

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import android.os.SystemClock
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.slideindex.app.overlay.panels.R
import com.slideindex.app.overlay.ScreenshotLayoutMeta
import com.slideindex.app.overlay.share.ShareDefaultBadgeSlot
import com.slideindex.app.overlay.share.ShareDefaultTarget
import com.slideindex.app.overlay.share.ShareSheetKind
import com.slideindex.app.overlay.share.rememberShareDefaultTarget
import com.slideindex.app.overlay.overlayContainerHeightDp
import com.slideindex.app.overlay.overlayIsLandscape
import com.slideindex.app.overlay.resolvePinImageDisplaySizePx
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.PickResultImageToolbarPosition
import com.slideindex.app.settings.SearchEngineConfig
import com.slideindex.app.settings.SearchEngineStore
import com.slideindex.app.ui.theme.LocalAppDarkTheme
import com.slideindex.app.util.HapticHelper
import kotlin.math.roundToInt

private val ImageSearchBarHeight = 36.dp
private val ImageSectionItemSpacing = 4.dp
private val ImageSearchBarBottomPadding = 0.dp

/** 图片区水平内容宽度（面板全宽减去左右 padding）。 */
@Composable
fun pickResultImageContentWidth(horizontalPadding: Dp = 40.dp): Dp {
    val density = LocalDensity.current
    val containerWidth = with(density) {
        LocalWindowInfo.current.containerSize.width.toDp()
    }
    return (containerWidth - horizontalPadding).coerceAtLeast(0.dp)
}

/**
 * 图片区尺寸。
 *
 * [width]/[height] 是图片区视口；[naturalHeight] 默认与 [height] 相同。只有「宽度优先」方案
 * （高度超出视口时不缩宽度、改由图片自身滚动）才会让 [naturalHeight] 大于 [height]；
 * 当前取词面板走的是「图片让位给搜索区」，两者相等。
 */
data class PickResultImageDisplaySize(
    val width: Dp,
    val height: Dp,
    val naturalHeight: Dp = height,
)

/**
 * 图片预览高度上限的**硬**天花板。
 *
 * 竖屏**不能**再等于屏宽：屏宽只是"按内容宽撑开后多高还算合理"的旧经验值，一旦图片撑开后
 * 高于它，[applyPickResultImageDisplayCaps] 就会反过来把**宽度**也一起缩掉，竖长截图因此被压窄
 * （真机反馈：截取较高范围的区域后图片被压着看不清）。这里统一放宽到容器高度的 80%，
 * 剩下的真实约束交给面板自身的 0.85/0.92/0.70 高度上限与「可负担高度」预算。
 */
@Composable
fun pickResultImageMaxHeightDp(): Dp {
    val containerHeight = overlayContainerHeightDp()
    if (!overlayIsLandscape()) return containerHeight * PORTRAIT_IMAGE_MAX_HEIGHT_FRACTION
    val widthDp = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.width.toDp()
    }
    return minOf(widthDp, containerHeight * LANDSCAPE_IMAGE_MAX_HEIGHT_FRACTION)
}

/** 竖屏：图片高度最多占容器高度的 80%（面板上限 0.85，留出分页头与图片区自身 chrome）。 */
private const val PORTRAIT_IMAGE_MAX_HEIGHT_FRACTION = 0.80f

/** 横屏：不再抢垂直空间，维持旧值 45%。 */
private const val LANDSCAPE_IMAGE_MAX_HEIGHT_FRACTION = 0.45f

private fun applyPickResultImageDisplayCaps(
    baseWidth: Dp,
    baseHeight: Dp,
    contentWidth: Dp,
    maxHeight: Dp,
    maxWidth: Dp,
    /** true：高度超出 [maxHeight] 时**不缩宽度**，只把高度夹到视口（超出部分靠滚动查看）。 */
    keepWidthOnOverflowHeight: Boolean = false,
): PickResultImageDisplaySize {
    if (baseWidth <= 0.dp || baseHeight <= 0.dp) {
        return PickResultImageDisplaySize(contentWidth, maxHeight)
    }
    // 宽度上限永远生效：图片再高也不能横向超出屏幕。
    val widthCap = minOf(contentWidth, maxWidth)
    if (baseWidth <= widthCap && baseHeight <= maxHeight) {
        return PickResultImageDisplaySize(baseWidth, baseHeight)
    }
    val aspect = baseHeight / baseWidth
    val heightAtFullWidth = widthCap * aspect
    if (heightAtFullWidth <= maxHeight) {
        return PickResultImageDisplaySize(widthCap, heightAtFullWidth)
    }
    if (keepWidthOnOverflowHeight) {
        // 宽度优先：宽度给到上限，高度夹进视口；图片本体尺寸由调用方另行计算，
        // 这里返回的 height 只表示「视口里看得见多少」。
        return PickResultImageDisplaySize(widthCap, maxHeight)
    }
    val widthAtMaxHeight = maxHeight / aspect
    return PickResultImageDisplaySize(widthAtMaxHeight, maxHeight)
}

/**
 * 计算**图片区视口**的展示尺寸。
 *
 * 顺序是「宽度是主语、高度是结果」：宽 = min(图片原宽, 内容宽, 屏幕宽)，高 = 宽 × 宽高比。
 * [keepWidthOnOverflowHeight] 为 true 时（取词面板用滚动方案），高度超出视口**不再反推缩小宽度**，
 * 只把高度夹到视口；图片本体的真实高度 = 视口宽度 × 宽高比，由调用方用 [pickResultImageNaturalHeight] 取。
 */
fun pickResultImageDisplaySize(
    bitmap: Bitmap,
    contentWidth: Dp,
    maxHeight: Dp,
    density: Density,
    screenRect: Rect?,
    layoutMeta: ScreenshotLayoutMeta?,
    screenWidthPx: Int,
    screenHeightPx: Int,
    keepWidthOnOverflowHeight: Boolean = false,
): PickResultImageDisplaySize {
    val hasScreenRect = screenRect != null && !screenRect.isEmpty
    val (baseWidth, baseHeight) = if (hasScreenRect) {
        val (widthPx, heightPx) = resolvePinImageDisplaySizePx(
            bitmap = bitmap,
            screenRect = screenRect,
            layoutMeta = layoutMeta,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
        )
        with(density) { widthPx.toDp() to heightPx.toDp() }
    } else {
        with(density) { bitmap.width.toDp() to bitmap.height.toDp() }
    }
    val screenMaxWidth = with(density) { screenWidthPx.toDp() }
    val screenMaxHeight = with(density) { screenHeightPx.toDp() }
    return if (hasScreenRect) {
        applyPickResultImageDisplayCaps(
            baseWidth = baseWidth,
            baseHeight = baseHeight,
            contentWidth = minOf(screenMaxWidth, contentWidth),
            maxHeight = minOf(screenMaxHeight, maxHeight),
            maxWidth = screenMaxWidth,
            keepWidthOnOverflowHeight = keepWidthOnOverflowHeight,
        )
    } else {
        applyPickResultImageDisplayCaps(
            baseWidth = baseWidth,
            baseHeight = baseHeight,
            contentWidth = contentWidth,
            maxHeight = maxHeight,
            maxWidth = screenMaxWidth,
            keepWidthOnOverflowHeight = keepWidthOnOverflowHeight,
        )
    }
}

/**
 * 图片本体在「宽度优先」下的真实高度 = 展示宽度 × 宽高比；拿不到原图时返回 null。
 *
 * 图片区视口高度由 [pickResultImageDisplaySize] 给出，图片本体比它高多少，就由
 * `PickResultImageSection` 的 verticalScroll 承担多少。
 */
fun pickResultImageNaturalHeight(
    displayWidth: Dp,
    bitmap: Bitmap,
    screenRect: Rect?,
    layoutMeta: ScreenshotLayoutMeta?,
    density: Density,
    screenWidthPx: Int,
    screenHeightPx: Int,
): Dp? {
    if (displayWidth <= 0.dp) return null
    val hasScreenRect = screenRect != null && !screenRect.isEmpty
    val (baseWidth, baseHeight) = if (hasScreenRect) {
        val (widthPx, heightPx) = resolvePinImageDisplaySizePx(
            bitmap = bitmap,
            screenRect = screenRect,
            layoutMeta = layoutMeta,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
        )
        with(density) { widthPx.toDp() to heightPx.toDp() }
    } else {
        with(density) { bitmap.width.toDp() to bitmap.height.toDp() }
    }
    if (baseWidth <= 0.dp || baseHeight <= 0.dp) return null
    return displayWidth * (baseHeight / baseWidth)
}

fun pickResultImageSectionReservedHeight(
    imageMaxHeight: Dp,
    isImageVisible: Boolean,
    includeSearchBar: Boolean = true
): Dp {
    val header = 12.dp
    if (!isImageVisible) return header
    val gaps = 6.dp
    val searchBar = if (includeSearchBar) ImageSectionItemSpacing + ImageSearchBarHeight else 0.dp
    return header + gaps + imageMaxHeight + searchBar
}

@Composable
fun PickResultImageSearchBar(
    engines: List<SearchEngineConfig>,
    onShareEngineClick: (SearchEngineConfig) -> Unit,
    onShare: () -> Unit,
    /** 点按分享键的直发入口（有默认目标时用）：返回 false 回退到 [onShare]（弹面板）。 */
    onShareToDefault: (() -> Boolean)? = null,
    onImageSearch: () -> Unit,
    onSave: () -> Unit,
    /** 独立「查看图片」按钮：打开面板内的全屏查看器；null 时不显示。 */
    onViewImage: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    onSaveLongClick: (() -> Unit)? = null,
    /** 长按分享键 = 把当前这张图起成系统拖拽；null 时不挂。 */
    onStartImageDrag: (() -> Unit)? = null,
    onPinToScreen: (() -> Unit)? = null,
    onStash: (() -> Unit)? = null,
    overlayMode: Boolean = false,
    compactEmbedded: Boolean = false,
    imageToolbarPosition: PickResultImageToolbarPosition = PickResultImageToolbarPosition.LEFT,
    /** 纯图标模式：不再画分享引擎主芯片，整排同规格小图标；按钮顺序/显隐由设置决定。 */
    iconBarEnabled: Boolean = false,
    iconBarOrder: List<String> = emptyList(),
    iconBarHidden: Set<String> = emptySet(),
) {
    val shareEngines = SearchEngineStore.imageSharePanelEngines(engines)
    val isDark = LocalAppDarkTheme.current
    // 有默认目标才画角标、才把长按从"拖拽"改成"弹面板"；没设默认时交互保持原样。
    // 调用方没提供直发回调时也保持原样：默认分享只属于取词面板。
    val shareDefaultTarget = rememberShareDefaultTarget(ShareSheetKind.IMAGE)
        ?.takeIf { onShareToDefault != null }
    val handleShareClick: () -> Unit = {
        if (shareDefaultTarget == null || onShareToDefault == null || !onShareToDefault()) {
            onShare()
        }
    }
    val handleShareLongClick: (() -> Unit)? =
        if (shareDefaultTarget != null) onShare else onStartImageDrag

    if (compactEmbedded) {
        if (iconBarEnabled) {
            PickResultImageIconBar(
                order = iconBarOrder,
                hidden = iconBarHidden,
                engines = shareEngines,
                onShareEngineClick = onShareEngineClick,
                onImageSearch = onImageSearch,
                onShare = onShare,
                onShareClick = handleShareClick,
                onShareLongClick = handleShareLongClick,
                shareDefault = shareDefaultTarget,
                onSave = onSave,
                onViewImage = onViewImage,
                onSaveLongClick = onSaveLongClick,
                onStartImageDrag = onStartImageDrag,
                onPinToScreen = onPinToScreen,
                onStash = onStash,
            )
            return
        }
        val toolbarOnRight = imageToolbarPosition == PickResultImageToolbarPosition.RIGHT

        @Composable
        fun ShareEngineSlot() {
            if (shareEngines.isNotEmpty()) {
                ImageShareEngineChip(
                    engines = shareEngines,
                    onShareEngineClick = onShareEngineClick,
                    compact = true,
                )
            } else {
                Spacer(Modifier.width(1.dp))
            }
        }

        @Composable
        fun ImageActionIcons() {
            CompositionLocalProvider(
                LocalLayoutDirection provides if (toolbarOnRight) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                PickResultImageSearchActions(
                    onShare = onShare,
                    onShareClick = handleShareClick,
                    onShareLongClick = handleShareLongClick,
                    shareDefault = shareDefaultTarget,
                    onImageSearch = onImageSearch,
                    onSave = onSave,
                    onViewImage = onViewImage,
                    onSaveLongClick = onSaveLongClick,
                    onStartImageDrag = onStartImageDrag,
                    onPinToScreen = onPinToScreen,
                    onStash = onStash,
                    overlayMode = false,
                    compact = true,
                )
            }
        }

        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (toolbarOnRight) {
                // 「右侧」= 工具图标组在右、分享芯片在左。
                ShareEngineSlot()
                ImageActionIcons()
            } else {
                ImageActionIcons()
                ShareEngineSlot()
            }
        }
        return
    }

    val pillBg = if (overlayMode) {
        Color.Black.copy(alpha = 0.62f)
    } else if (isDark) {
        Color(0x3BFFFFFF)
    } else {
        Color(0x1E000000)
    }
    val pillBorder = if (overlayMode) {
        Color.White.copy(alpha = 0.22f)
    } else if (isDark) {
        Color(0x28FFFFFF)
    } else {
        Color(0x18000000)
    }

    Box(
        modifier = modifier
            .then(
                if (overlayMode) {
                    Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                } else {
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .shadow(
                    elevation = if (overlayMode) 6.dp else 8.dp,
                    shape = RoundedCornerShape(24.dp),
                    spotColor = if (overlayMode || isDark) Color(0x66000000) else Color(0x33000000),
                    ambientColor = Color(0x22000000),
                )
                .clip(RoundedCornerShape(24.dp))
                .background(pillBg)
                .border(0.6.dp, pillBorder, RoundedCornerShape(24.dp))
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (shareEngines.isNotEmpty()) {
                ImageShareEngineChip(
                    engines = shareEngines,
                    onShareEngineClick = onShareEngineClick,
                )
            }

            PickResultImageSearchActions(
                onShare = onShare,
                onShareClick = handleShareClick,
                onShareLongClick = handleShareLongClick,
                shareDefault = shareDefaultTarget,
                onImageSearch = onImageSearch,
                onSave = onSave,
                onViewImage = onViewImage,
                onSaveLongClick = onSaveLongClick,
                onStartImageDrag = onStartImageDrag,
                onPinToScreen = onPinToScreen,
                onStash = onStash,
                overlayMode = overlayMode,
            )
        }
    }
}

@Composable
private fun rememberLastUsedImageShareEngine(
    engines: List<SearchEngineConfig>,
): Pair<SearchEngineConfig?, (SearchEngineConfig) -> Unit> {
    val context = LocalContext.current.applicationContext
    var lastUsedEngineId by remember {
        mutableStateOf(
            context.getSharedPreferences(PickResultImageSharePrefs.PREFS_NAME, android.content.Context.MODE_PRIVATE)
                .getString(PickResultImageSharePrefs.KEY_LAST_USED_ENGINE_ID, null),
        )
    }
    // 图片分享引擎可以为空：默认引擎只有网页搜索，用户也没配「分享到 App」时就是空列表。
    // 这里必须容忍空列表，否则纯图标底栏在组合阶段会因 first() 抛 NoSuchElementException。
    val displayEngine = remember(engines, lastUsedEngineId) {
        engines.find { it.id == lastUsedEngineId } ?: engines.firstOrNull()
    }
    val rememberEngine: (SearchEngineConfig) -> Unit = { engine ->
        PickResultImageSharePrefs.rememberLastUsedEngine(context, engine)
        lastUsedEngineId = engine.id
    }
    return displayEngine to rememberEngine
}

@Composable
private fun ImageShareEngineChip(
    engines: List<SearchEngineConfig>,
    onShareEngineClick: (SearchEngineConfig) -> Unit,
    compact: Boolean = false,
) {
    val (displayEngineOrNull, rememberEngine) = rememberLastUsedImageShareEngine(engines)
    // 调用方只在有引擎时才挂这个芯片；这里再兜一层，避免空列表从别的入口进来。
    val displayEngine = displayEngineOrNull ?: return
    var menuExpanded by remember { mutableStateOf(false) }
    val showEnginePicker = engines.size > 1
    val shareLabel = stringResource(R.string.pick_result_image_share_to_app)

    fun shareWith(engine: SearchEngineConfig) {
        rememberEngine(engine)
        onShareEngineClick(engine)
    }

    var anchorCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var isDragging by remember { mutableStateOf(false) }
    var hoveredIndex by remember { mutableIntStateOf(-1) }
    var rowAnchorLeftInWindow by remember { mutableFloatStateOf(0f) }
    var dragStartFingerY by remember { mutableFloatStateOf(0f) }

    val quickSwitchItemWidth = 40.dp
    val quickSwitchItemSpacing = 6.dp
    val quickSwitchRowPaddingStart = 10.dp
    val quickSwitchRowPaddingEnd = 10.dp
    val quickSwitchRowVerticalPadding = 6.dp
    val quickSwitchPopupGapAboveChip = 8.dp
    val quickSwitchYCancelThreshold = 60.dp
    val quickSwitchScreenMargin = 8.dp

    val density = LocalDensity.current
    val itemWidthPx = with(density) { quickSwitchItemWidth.toPx() }
    val itemSpacingPx = with(density) { quickSwitchItemSpacing.toPx() }
    val rowPaddingStartPx = with(density) { quickSwitchRowPaddingStart.toPx() }
    val rowPaddingEndPx = with(density) { quickSwitchRowPaddingEnd.toPx() }
    val popupOffsetAboveChipPx = with(density) {
        (quickSwitchItemWidth + quickSwitchRowVerticalPadding * 2 + quickSwitchPopupGapAboveChip).toPx()
    }
    val yCancelThresholdPx = with(density) { quickSwitchYCancelThreshold.toPx() }
    val screenMarginPx = with(density) { quickSwitchScreenMargin.toPx() }
    val totalItemStridePx = itemWidthPx + itemSpacingPx
    val screenWidthPx = LocalWindowInfo.current.containerSize.width.toFloat()

    val view = LocalView.current
    val context = LocalContext.current.applicationContext
    var appSettings by remember { mutableStateOf(AppSettings()) }
    LaunchedEffect(context) {
        com.slideindex.app.settings.SettingsFlowHolder.settingsFlow(context)
            ?.collect { appSettings = it }
    }

    val quickSwitchEnabled = engines.size > 1

    fun quickSwitchRowWidthPx(): Float {
        val itemCount = engines.size
        return rowPaddingStartPx + rowPaddingEndPx +
            itemCount * itemWidthPx +
            (itemCount - 1).coerceAtLeast(0) * itemSpacingPx
    }

    fun clampRowAnchorLeft(left: Float): Float {
        val rowWidth = quickSwitchRowWidthPx()
        return left.coerceIn(screenMarginPx, (screenWidthPx - rowWidth - screenMarginPx).coerceAtLeast(screenMarginPx))
    }

    fun indexAtFingerX(fingerX: Float): Int {
        val localX = fingerX - rowAnchorLeftInWindow - rowPaddingStartPx
        return (localX / totalItemStridePx)
            .toInt()
            .coerceIn(0, engines.lastIndex)
    }

    fun anchorRowUnderFinger(fingerX: Float, underIndex: Int) {
        val desiredLeft = fingerX - rowPaddingStartPx - underIndex * totalItemStridePx - itemWidthPx / 2f
        rowAnchorLeftInWindow = clampRowAnchorLeft(desiredLeft)
    }

    val chipHeight = 40.dp
    val chipCorner = 20.dp

    Box(
        modifier = Modifier.onGloballyPositioned { anchorCoordinates = it }
    ) {
        Row(
            modifier = Modifier
                .height(chipHeight)
                .then(
                    if (compact) {
                        Modifier
                    } else {
                        Modifier.shadow(
                            elevation = 4.dp,
                            shape = RoundedCornerShape(chipCorner),
                            spotColor = MaterialTheme.colorScheme.primary,
                        )
                    }
                )
                .clip(RoundedCornerShape(chipCorner))
                .background(MaterialTheme.colorScheme.primary)
                .then(
                    if (quickSwitchEnabled) {
                        Modifier.pointerInput(engines, displayEngine, anchorCoordinates) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { startOffset ->
                                    val coords = anchorCoordinates ?: return@detectDragGesturesAfterLongPress
                                    val finger = coords.localToWindow(startOffset)
                                    dragStartFingerY = finger.y
                                    val startIndex = engines.indexOf(displayEngine).coerceAtLeast(0)
                                    anchorRowUnderFinger(finger.x, startIndex)
                                    hoveredIndex = indexAtFingerX(finger.x)
                                    isDragging = true
                                    HapticHelper.appTick(view, appSettings)
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val coords = anchorCoordinates ?: return@detectDragGesturesAfterLongPress
                                    val finger = coords.localToWindow(change.position)
                                    if (kotlin.math.abs(finger.y - dragStartFingerY) > yCancelThresholdPx) {
                                        if (hoveredIndex != -1) {
                                            hoveredIndex = -1
                                            HapticHelper.appTick(view, appSettings)
                                        }
                                    } else {
                                        val index = indexAtFingerX(finger.x)
                                        if (index != hoveredIndex) {
                                            hoveredIndex = index
                                            HapticHelper.appTick(view, appSettings)
                                        }
                                    }
                                },
                                onDragEnd = {
                                    if (isDragging && hoveredIndex in engines.indices) {
                                        shareWith(engines[hoveredIndex])
                                    }
                                    isDragging = false
                                    hoveredIndex = -1
                                },
                                onDragCancel = {
                                    isDragging = false
                                    hoveredIndex = -1
                                },
                            )
                        }
                    } else {
                        Modifier
                    }
                )
                .clickable {
                    if (!isDragging) {
                        shareWith(displayEngine)
                    }
                }
                .padding(
                    start = 14.dp,
                    end = if (showEnginePicker) 6.dp else 14.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SearchEngineIcon(
                engine = displayEngine,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = displayEngine.name,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 14.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                ),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (showEnginePicker) {
                Box(
                    modifier = Modifier
                        .size(if (compact) 20.dp else 24.dp)
                        .clip(RoundedCornerShape(if (compact) 10.dp else 12.dp))
                        .clickable { menuExpanded = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.pick_result_image_search_pick_engine),
                        modifier = Modifier.size(if (compact) 14.dp else 16.dp),
                        tint = Color.White.copy(alpha = 0.85f)
                    )
                }
            }
        }

        if (showEnginePicker) {
            val isDark = LocalAppDarkTheme.current
            val textColor = if (isDark) Color(0xFFECECED) else Color(0xFF1F1F1F)
            PickResultDropdownMenuInLtr(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                engines.forEach { engine ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = engine.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 14.sp,
                                    color = textColor,
                                ),
                            )
                        },
                        leadingIcon = {
                            SearchEngineIcon(
                                engine = engine,
                                modifier = Modifier.size(22.dp),
                            )
                        },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 12.dp,
                            vertical = 8.dp,
                        ),
                        onClick = {
                            menuExpanded = false
                            shareWith(engine)
                        },
                    )
                }
            }
        }

        if (isDragging && quickSwitchEnabled) {
            val coords = anchorCoordinates
            if (coords != null && coords.isAttached) {
                val anchorTopLeft = coords.boundsInWindow().topLeft
                val popupOffsetX = (rowAnchorLeftInWindow - anchorTopLeft.x).roundToInt()
                val popupOffsetY = -popupOffsetAboveChipPx.roundToInt()
                Popup(
                    alignment = Alignment.TopStart,
                    offset = IntOffset(popupOffsetX, popupOffsetY),
                ) {
                    ImageShareEngineQuickSwitchRow(
                        engines = engines,
                        hoveredIndex = hoveredIndex,
                    )
                }
            }
        }
    }
}

/**
 * 纯图标模式下的「搜图」键：
 * - 单击：把当前图片交给搜索面板的图片模式（由上层 [onImageSearch] 落地）；
 * - 长按后不松手左右滑动：快速选择已配置的分享引擎，松手即分享（原分享主芯片的长按行为）。
 *
 * 手势必须自己接管长按：`detectDragGesturesAfterLongPress` 在抬手时要求 up 未被消费，
 * 而同一节点的 `clickable` 是内层、会先拿走这次 up，于是「长按后原地松手」会被判成 cancel。
 * 与底栏搜索键（[PickResultSearchActionButton]）用同一套写法。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImageSearchIconButton(
    engines: List<SearchEngineConfig>,
    onImageSearch: () -> Unit,
    onShareEngineClick: (SearchEngineConfig) -> Unit,
) {
    val (displayEngine, rememberEngine) = rememberLastUsedImageShareEngine(engines)
    var anchorCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var isDragging by remember { mutableStateOf(false) }
    var hoveredIndex by remember { mutableIntStateOf(-1) }
    var rowAnchorLeftInWindow by remember { mutableFloatStateOf(0f) }
    var dragStartFingerY by remember { mutableFloatStateOf(0f) }
    // 滑选抬手后短暂抑制点击：`clickable` 与长按手势的派发先后不做保证。
    var suppressClickUntilMs by remember { mutableLongStateOf(0L) }

    val isDark = LocalAppDarkTheme.current
    val iconTint = if (isDark) Color(0xFFE0E0E6) else Color(0xFF333333)

    val quickSwitchItemWidth = 40.dp
    val quickSwitchItemSpacing = 6.dp
    val quickSwitchRowPaddingStart = 10.dp
    val quickSwitchRowPaddingEnd = 10.dp
    val quickSwitchRowVerticalPadding = 6.dp
    val quickSwitchPopupGapAboveChip = 8.dp
    val quickSwitchYCancelThreshold = 60.dp
    val quickSwitchScreenMargin = 8.dp

    val density = LocalDensity.current
    val itemWidthPx = with(density) { quickSwitchItemWidth.toPx() }
    val itemSpacingPx = with(density) { quickSwitchItemSpacing.toPx() }
    val rowPaddingStartPx = with(density) { quickSwitchRowPaddingStart.toPx() }
    val rowPaddingEndPx = with(density) { quickSwitchRowPaddingEnd.toPx() }
    val popupOffsetAboveChipPx = with(density) {
        (quickSwitchItemWidth + quickSwitchRowVerticalPadding * 2 + quickSwitchPopupGapAboveChip).toPx()
    }
    val yCancelThresholdPx = with(density) { quickSwitchYCancelThreshold.toPx() }
    val screenMarginPx = with(density) { quickSwitchScreenMargin.toPx() }
    val totalItemStridePx = itemWidthPx + itemSpacingPx
    val screenWidthPx = LocalWindowInfo.current.containerSize.width.toFloat()

    val view = LocalView.current
    val context = LocalContext.current.applicationContext
    var appSettings by remember { mutableStateOf(AppSettings()) }
    LaunchedEffect(context) {
        com.slideindex.app.settings.SettingsFlowHolder.settingsFlow(context)
            ?.collect { appSettings = it }
    }

    val quickSwitchEnabled = engines.size > 1

    fun quickSwitchRowWidthPx(): Float {
        val itemCount = engines.size
        return rowPaddingStartPx + rowPaddingEndPx +
            itemCount * itemWidthPx +
            (itemCount - 1).coerceAtLeast(0) * itemSpacingPx
    }

    fun clampRowAnchorLeft(left: Float): Float {
        val rowWidth = quickSwitchRowWidthPx()
        return left.coerceIn(
            screenMarginPx,
            (screenWidthPx - rowWidth - screenMarginPx).coerceAtLeast(screenMarginPx),
        )
    }

    fun indexAtFingerX(fingerX: Float): Int {
        val localX = fingerX - rowAnchorLeftInWindow - rowPaddingStartPx
        return (localX / totalItemStridePx).toInt().coerceIn(0, engines.lastIndex)
    }

    fun anchorRowUnderFinger(fingerX: Float, underIndex: Int) {
        val desiredLeft = fingerX - rowPaddingStartPx - underIndex * totalItemStridePx - itemWidthPx / 2f
        rowAnchorLeftInWindow = clampRowAnchorLeft(desiredLeft)
    }

    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .onGloballyPositioned { anchorCoordinates = it }
            .then(
                if (quickSwitchEnabled) {
                    Modifier.pointerInput(engines, displayEngine) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            // 没有分享引擎时 displayEngine 为 null：整段长按滑选直接退出，单击仍可搜图。
                            val currentEngine = displayEngine ?: return@awaitEachGesture
                            // 长按成立前抬手 / 移动过大 → null，这次仍按普通单击处理
                            val longPress = awaitLongPressOrCancellation(down.id)
                                ?: return@awaitEachGesture
                            val coords = anchorCoordinates ?: return@awaitEachGesture

                            val startIndex = engines.indexOf(currentEngine).coerceAtLeast(0)
                            val finger = coords.localToWindow(longPress.position)
                            dragStartFingerY = finger.y
                            anchorRowUnderFinger(finger.x, startIndex)
                            hoveredIndex = indexAtFingerX(finger.x)
                            isDragging = true
                            // 长按一成立就封住点击：up 由谁先消费不做保证，这里两头都堵住。
                            suppressClickUntilMs = Long.MAX_VALUE
                            HapticHelper.appTick(view, appSettings)

                            var confirmedIndex = startIndex
                            var released = false
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == longPress.id }
                                    if (change == null || !change.pressed) {
                                        // 抬手即确认：这里**不看 isConsumed**（旧实现看的正是它）。
                                        released = event.changes.none { it.pressed }
                                        if (released) change?.consume()
                                        break
                                    }
                                    if (change.positionChanged()) {
                                        change.consume()
                                        val current = coords.localToWindow(change.position)
                                        if (kotlin.math.abs(current.y - dragStartFingerY) > yCancelThresholdPx) {
                                            if (hoveredIndex != -1) {
                                                hoveredIndex = -1
                                                HapticHelper.appTick(view, appSettings)
                                            }
                                        } else {
                                            val index = indexAtFingerX(current.x)
                                            if (index != hoveredIndex) {
                                                hoveredIndex = index
                                                HapticHelper.appTick(view, appSettings)
                                            }
                                        }
                                        confirmedIndex = hoveredIndex
                                    }
                                }
                            } finally {
                                isDragging = false
                                hoveredIndex = -1
                                suppressClickUntilMs = SystemClock.uptimeMillis() + 250L
                            }

                            if (released && confirmedIndex in engines.indices) {
                                val engine = engines[confirmedIndex]
                                rememberEngine(engine)
                                onShareEngineClick(engine)
                            }
                        }
                    }
                } else {
                    Modifier
                }
            )
            .clickable {
                if (!isDragging && SystemClock.uptimeMillis() >= suppressClickUntilMs) onImageSearch()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.ImageSearch,
            contentDescription = stringResource(R.string.pick_result_action_image_search),
            tint = iconTint,
            modifier = Modifier.size(22.dp),
        )
    }

    if (isDragging) {
        val coords = anchorCoordinates
        if (coords != null && coords.isAttached) {
            val anchorTopLeft = coords.boundsInWindow().topLeft
            // 与分享主芯片一致：滑选条强制 Ltr，位置与命中都按窗口 x 从左往右算。
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Popup(
                    alignment = Alignment.TopStart,
                    offset = IntOffset(
                        (rowAnchorLeftInWindow - anchorTopLeft.x).roundToInt(),
                        (-popupOffsetAboveChipPx).roundToInt(),
                    ),
                ) {
                    ImageShareEngineQuickSwitchRow(
                        engines = engines,
                        hoveredIndex = hoveredIndex,
                    )
                }
            }
        }
    }
}

@Composable
private fun PickResultImageIconBar(
    order: List<String>,
    hidden: Set<String>,
    engines: List<SearchEngineConfig>,
    onShareEngineClick: (SearchEngineConfig) -> Unit,
    onImageSearch: () -> Unit,
    onShare: () -> Unit,
    onShareClick: (() -> Unit)? = null,
    onShareLongClick: (() -> Unit)? = null,
    shareDefault: ShareDefaultTarget? = null,
    onSave: () -> Unit,
    /** 独立「查看图片」按钮：打开面板内的全屏查看器；null 时不显示。 */
    onViewImage: (() -> Unit)? = null,
    onSaveLongClick: (() -> Unit)? = null,
    onStartImageDrag: (() -> Unit)? = null,
    onPinToScreen: (() -> Unit)? = null,
    onStash: (() -> Unit)? = null,
) {
    val buttons = remember(order) {
        com.slideindex.app.settings.PickResultBarButtons.normalizeImage(order)
    }
    val hiddenIds = remember(hidden) {
        com.slideindex.app.settings.PickResultBarButtons.sanitizeHiddenImage(hidden)
    }

    val visible = buttons.filter { button ->
        button.id !in hiddenIds && when (button) {
            com.slideindex.app.settings.PickResultImageBarButton.IMAGE_SEARCH -> true
            com.slideindex.app.settings.PickResultImageBarButton.SAVE -> true
            com.slideindex.app.settings.PickResultImageBarButton.SHARE -> true
            com.slideindex.app.settings.PickResultImageBarButton.PIN -> onPinToScreen != null
            com.slideindex.app.settings.PickResultImageBarButton.STASH -> onStash != null
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
    ) {
        // 「查看图片」不参与按钮排序/隐藏：它是图片区独立的查看入口，始终显示在最左侧。
        if (onViewImage != null) {
            PickResultActionIcon(
                icon = PickResultViewImageIcon,
                enabled = true,
                onClick = onViewImage,
                contentDescription = stringResource(R.string.pick_result_action_view_image),
            )
        }
        visible.forEach { button ->
            when (button) {
                com.slideindex.app.settings.PickResultImageBarButton.IMAGE_SEARCH ->
                    ImageSearchIconButton(
                        engines = engines,
                        onImageSearch = onImageSearch,
                        onShareEngineClick = onShareEngineClick,
                    )

                com.slideindex.app.settings.PickResultImageBarButton.SAVE ->
                    PickResultActionIcon(
                        icon = Icons.Outlined.Save,
                        enabled = true,
                        onClick = onSave,
                        onLongClick = onSaveLongClick,
                        contentDescription = stringResource(R.string.pick_result_action_save_image),
                    )

                com.slideindex.app.settings.PickResultImageBarButton.SHARE ->
                    PickResultActionIcon(
                        icon = Icons.Outlined.Share,
                        enabled = true,
                        onClick = onShareClick ?: onShare,
                        onLongClick = onShareLongClick ?: onStartImageDrag,
                        contentDescription = stringResource(R.string.pick_result_action_share_image),
                        badge = shareDefault,
                    )

                com.slideindex.app.settings.PickResultImageBarButton.PIN ->
                    PickResultActionIcon(
                        icon = Icons.Outlined.PushPin,
                        enabled = true,
                        onClick = { onPinToScreen?.invoke() },
                        contentDescription = stringResource(R.string.pick_result_pin),
                    )

                com.slideindex.app.settings.PickResultImageBarButton.STASH ->
                    PickResultActionIcon(
                        icon = Icons.Outlined.Archive,
                        enabled = true,
                        onClick = { onStash?.invoke() },
                        contentDescription = stringResource(R.string.pick_result_stash),
                    )
            }
        }
    }
}

@Composable
private fun ImageShareEngineQuickSwitchRow(
    engines: List<SearchEngineConfig>,
    hoveredIndex: Int,
) {
    Row(
        modifier = Modifier
            .shadow(12.dp, RoundedCornerShape(24.dp))
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(24.dp),
            )
            .border(
                width = 0.8.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(24.dp),
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        engines.forEachIndexed { index, engine ->
            val isHovered = index == hoveredIndex
            val scale by animateFloatAsState(
                targetValue = if (isHovered) 1.22f else 1f,
                label = "engineQuickSwitchScale",
            )
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .scale(scale)
                    .background(
                        color = if (isHovered) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            Color.Transparent
                        },
                        shape = RoundedCornerShape(12.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                SearchEngineIcon(
                    engine = engine,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

@Composable
private fun PickResultImageSearchActions(
    onShare: () -> Unit,
    onShareClick: (() -> Unit)? = null,
    onShareLongClick: (() -> Unit)? = null,
    shareDefault: ShareDefaultTarget? = null,
    onImageSearch: () -> Unit,
    onSave: () -> Unit,
    /** 独立「查看图片」按钮：打开面板内的全屏查看器；null 时不显示。 */
    onViewImage: (() -> Unit)? = null,
    onSaveLongClick: (() -> Unit)? = null,
    onStartImageDrag: (() -> Unit)? = null,
    onPinToScreen: (() -> Unit)? = null,
    onStash: (() -> Unit)? = null,
    overlayMode: Boolean = false,
    compact: Boolean = false,
) {
    var moreMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.height(if (compact) 32.dp else 38.dp),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 0. 独立入口：全屏查看当前图片（不占「更多」菜单、也不参与图标栏排序）。
        if (onViewImage != null) {
            PickResultActionIcon(
                icon = PickResultViewImageIcon,
                enabled = true,
                onClick = onViewImage,
                contentDescription = stringResource(R.string.pick_result_action_view_image),
                overlayMode = overlayMode,
                compact = compact,
            )
        }

        // 1. Direct Action: Image Search (以图搜图)
        PickResultActionIcon(
            icon = Icons.Outlined.ImageSearch,
            enabled = true,
            onClick = onImageSearch,
            contentDescription = stringResource(R.string.pick_result_action_image_search),
            overlayMode = overlayMode,
            compact = compact,
        )

        // 2. Direct Action: Save (保存图片)
        PickResultActionIcon(
            icon = Icons.Outlined.Save,
            enabled = true,
            onClick = onSave,
            onLongClick = onSaveLongClick,
            contentDescription = stringResource(R.string.pick_result_action_save_image),
            overlayMode = overlayMode,
            compact = compact,
        )

        // 3. Direct Action: Share (分享图片，直接外显)；长按 = 把这张图拖到别的 App。
        //    设了默认目标时：点按直发、长按改为弹分享面板（与 FV 一致）。
        PickResultActionIcon(
            icon = Icons.Outlined.Share,
            enabled = true,
            onClick = onShareClick ?: onShare,
            onLongClick = onShareLongClick ?: onStartImageDrag,
            contentDescription = stringResource(R.string.pick_result_action_share_image),
            overlayMode = overlayMode,
            compact = compact,
            badge = shareDefault,
        )

        // 4. More Action: Dropdown Menu (钉选 / 暂存)
        val hasMoreActions = onPinToScreen != null || onStash != null
        if (hasMoreActions) {
            Box {
                PickResultActionIcon(
                    icon = Icons.Filled.MoreVert,
                    enabled = true,
                    onClick = { moreMenuExpanded = true },
                    contentDescription = stringResource(R.string.pick_result_action_more),
                    overlayMode = overlayMode,
                    compact = compact,
                )

                PickResultDropdownMenuInLtr(
                    expanded = moreMenuExpanded,
                    onDismissRequest = { moreMenuExpanded = false }
                ) {
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
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PickResultActionIcon(
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    overlayMode: Boolean = false,
    compact: Boolean = false,
    badge: ShareDefaultTarget? = null,
) {
    val isDark = LocalAppDarkTheme.current
    val iconTint = if (overlayMode) Color(0xFFF2F2F7) else if (isDark) Color(0xFFE0E0E6) else Color(0xFF333333)
    val buttonSize = 42.dp
    val iconSize = 22.dp

    Box(
        modifier = modifier
            .size(buttonSize)
            .clip(RoundedCornerShape(buttonSize / 2))
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(iconSize)) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                tint = if (enabled) iconTint else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            )
            ShareDefaultBadgeSlot(badge)
        }
    }
}
