package com.slideindex.app.overlay.pickresult

import android.graphics.Bitmap
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.slideindex.app.R
import com.slideindex.app.barcode.BarcodeScanResult
import com.slideindex.app.overlay.FloatBallOverlay
import com.slideindex.app.overlay.FloatBallPickResultPanel
import com.slideindex.app.overlay.LocalFrostedGlassBackdrop
import com.slideindex.app.overlay.PickResultTextSource
import com.slideindex.app.overlay.overlayBottomPanelMaxHeightFraction
import com.slideindex.app.overlay.overlayBottomPanelMaxWidth
import com.slideindex.app.overlay.overlayBottomPanelWidth
import com.slideindex.app.overlay.overlayContainerHeightDp
import com.slideindex.app.overlay.overlayContainerWidthDp
import com.slideindex.app.overlay.overlayIsLandscape
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.SearchEngineType
import com.slideindex.app.settings.launchPolicyLongPressEligible
import com.slideindex.app.ui.theme.LocalAppDarkTheme
import com.slideindex.app.ui.theme.OverlayAwareModuleTheme
import kotlin.math.abs
import kotlin.math.roundToInt

internal val PANEL_MIN_IMAGE_HEIGHT = 48.dp
internal val PANEL_VERTICAL_PADDING = 12.dp
internal val PANEL_ACTION_BAR_BOTTOM_GAP = 20.dp
internal val TEXT_IMAGE_DIVIDER_HEIGHT = 10.dp
internal const val LANDSCAPE_DUAL_COLUMN_TEXT_WEIGHT = 0.58f
internal const val LANDSCAPE_DUAL_COLUMN_AUX_WEIGHT = 0.42f
internal const val AUXILIARY_COLLAPSE_ANIMATION_MS = 280
internal const val AUXILIARY_COLLAPSE_DRAG_THRESHOLD = 0.35f
internal const val EDIT_MODE_ANIMATION_MS = 280

internal fun pickPanelSlideAnimationSpec(durationMs: Int): AnimationSpec<Dp> =
    if (durationMs <= 0) {
        snap()
    } else {
        tween(durationMillis = durationMs, easing = FastOutSlowInEasing)
    }

@Stable
internal class AuxiliaryCollapseController(
    initialCollapseProgress: Float = 0f,
    initialSearchCollapseProgress: Float = 0f
) {
    private var totalCollapsiblePx by mutableFloatStateOf(1f)
    private var totalSearchCollapsiblePx by mutableFloatStateOf(1f)

    var collapseProgress by mutableFloatStateOf(initialCollapseProgress)
        private set
    var searchCollapseProgress by mutableFloatStateOf(initialSearchCollapseProgress)
        private set
    var isDragging by mutableStateOf(false)
        private set
    var isSearchDragging by mutableStateOf(false)
        private set

    fun updateTotalCollapsiblePx(px: Float) {
        totalCollapsiblePx = px.coerceAtLeast(1f)
    }

    fun updateTotalSearchCollapsiblePx(px: Float) {
        totalSearchCollapsiblePx = px.coerceAtLeast(1f)
    }

    fun applyDrag(deltaPx: Float) {
        isDragging = true
        isSearchDragging = false
        collapseProgress = (collapseProgress + deltaPx / totalCollapsiblePx).coerceIn(0f, 1f)
    }

    fun applySearchDrag(deltaPx: Float) {
        isDragging = true
        isSearchDragging = true
        searchCollapseProgress =
            (searchCollapseProgress + deltaPx / totalSearchCollapsiblePx).coerceIn(0f, 1f)
    }

    fun endDrag(onSettled: (expanded: Boolean) -> Unit) {
        isDragging = false
        isSearchDragging = false
        val target = if (collapseProgress > AUXILIARY_COLLAPSE_DRAG_THRESHOLD) 1f else 0f
        collapseProgress = target
        onSettled(target < 0.5f)
    }

    fun endSearchDrag(onSettled: (expanded: Boolean) -> Unit) {
        isDragging = false
        isSearchDragging = false
        val target = if (searchCollapseProgress > AUXILIARY_COLLAPSE_DRAG_THRESHOLD) 1f else 0f
        searchCollapseProgress = target
        onSettled(target < 0.5f)
    }

    fun setExpanded(expanded: Boolean) {
        if (isDragging) return
        val target = if (expanded) 0f else 1f
        if (abs(collapseProgress - target) < 0.001f) return
        collapseProgress = target
    }

    fun setSearchExpanded(expanded: Boolean) {
        if (isDragging) return
        val target = if (expanded) 0f else 1f
        if (abs(searchCollapseProgress - target) < 0.001f) return
        searchCollapseProgress = target
    }
}

internal data class PickResultCollapseHeights(
    val imageSectionHeight: Dp,
    val textImageDividerHeight: Dp,
    val searchDividerHeight: Dp,
    val searchGridHeight: Dp,
    val textBodyHeight: Dp,
    val fillTextSpace: Boolean
)

internal fun computePickResultCollapseHeights(
    panelInnerHeight: Dp,
    normalLayoutFactor: Float,
    hasImageContent: Boolean,
    minImageSectionHeight: Dp,
    maxImageSectionHeight: Dp,
    textImageDividerBaseHeight: Dp,
    searchDividerBaseHeight: Dp,
    expandedSearchGridContentHeight: Dp,
    idealTextBodyHeight: Dp,
    minTextBodyHeight: Dp,
    actionBarBottomPadding: Dp = 0.dp,
    imageExpansionFraction: Float,
    searchExpansionFraction: Float,
    landscapeDualColumn: Boolean = false,
    showTabBar: Boolean = false,
): PickResultCollapseHeights {
    val layoutFactor = normalLayoutFactor.coerceIn(0f, 1f)
    val imageExpansion = imageExpansionFraction.coerceIn(0f, 1f)
    val searchExpansion = searchExpansionFraction.coerceIn(0f, 1f)

    val imageSectionHeight = if (hasImageContent) {
        lerp(minImageSectionHeight, maxImageSectionHeight, imageExpansion) * layoutFactor
    } else {
        0.dp
    }
    val textImageDividerHeight = if (landscapeDualColumn) {
        0.dp
    } else {
        textImageDividerBaseHeight * imageExpansion * layoutFactor
    }
    val searchDividerHeight = searchDividerBaseHeight * searchExpansion * layoutFactor
    val searchGridHeight = expandedSearchGridContentHeight * searchExpansion * layoutFactor

    val textToolbarReserved =
        PickResultTextSectionToolbarReservedHeight + PickResultTextToolbarBodySpacing
    val actionBarReserved =
        PickResultTextActionBarReservedHeight +
            PickResultTextActionBarTopPadding +
            actionBarBottomPadding
    val tabHeaderReserved = if (showTabBar) PickResultTabHeaderReservedHeight else 0.dp

    val rawTextBodyHeight = if (landscapeDualColumn) {
        (panelInnerHeight - textToolbarReserved - actionBarReserved)
            .coerceAtLeast(minTextBodyHeight)
    } else if (showTabBar) {
        (
            panelInnerHeight -
                tabHeaderReserved -
                searchDividerHeight -
                searchGridHeight -
                textToolbarReserved -
                actionBarReserved
        ).coerceAtLeast(minTextBodyHeight)
    } else {
        (
            panelInnerHeight -
                tabHeaderReserved -
                imageSectionHeight -
                textImageDividerHeight -
                searchDividerHeight -
                searchGridHeight -
                textToolbarReserved -
                actionBarReserved
        ).coerceAtLeast(minTextBodyHeight)
    }

    // 文字区最大高度按屏幕剩余空间自适应展开，当图片区收起时动态借调释放的空间
    val compactTextBodyHeight = rawTextBodyHeight.coerceAtLeast(0.dp)
    val fillTextSpace = false
    val textBodyHeight = compactTextBodyHeight

    return PickResultCollapseHeights(
        imageSectionHeight = imageSectionHeight.coerceAtLeast(0.dp),
        textImageDividerHeight = textImageDividerHeight.coerceAtLeast(0.dp),
        searchDividerHeight = searchDividerHeight.coerceAtLeast(0.dp),
        searchGridHeight = searchGridHeight.coerceAtLeast(0.dp),
        textBodyHeight = textBodyHeight,
        fillTextSpace = fillTextSpace
    )
}

/** 计算完全展开态高度（供面板外层 wrapContent 动画与滑动入场使用）。*/
internal fun computePickResultExpandedPanelOuterHeight(
    panelContentHeight: Dp,
    hasSearchGrid: Boolean,
    hasImageContent: Boolean,
    minImageSectionHeight: Dp,
    maxImageSectionHeight: Dp,
    textImageDividerBaseHeight: Dp,
    searchGridSectionPrefixHeight: Dp,
    expandedSearchGridContentHeight: Dp,
    idealTextBodyHeight: Dp,
    minTextBodyHeight: Dp,
    actionBarBottomPadding: Dp = 0.dp,
    landscapeDualColumn: Boolean = false,
    showTabBar: Boolean = false,
): Dp {
    val expandedBottomPadding = if (hasSearchGrid) 0.dp else PANEL_ACTION_BAR_BOTTOM_GAP
    val panelInnerHeight = panelContentHeight - PANEL_VERTICAL_PADDING - expandedBottomPadding
    val heights = computePickResultCollapseHeights(
        panelInnerHeight = panelInnerHeight,
        normalLayoutFactor = 1f,
        hasImageContent = hasImageContent,
        minImageSectionHeight = minImageSectionHeight,
        maxImageSectionHeight = maxImageSectionHeight,
        textImageDividerBaseHeight = textImageDividerBaseHeight,
        searchDividerBaseHeight = searchGridSectionPrefixHeight,
        expandedSearchGridContentHeight = expandedSearchGridContentHeight,
        idealTextBodyHeight = idealTextBodyHeight,
        minTextBodyHeight = minTextBodyHeight,
        actionBarBottomPadding = actionBarBottomPadding,
        imageExpansionFraction = 1f,
        searchExpansionFraction = 1f,
        landscapeDualColumn = landscapeDualColumn,
        showTabBar = showTabBar,
    )
    val textToolbarReserved =
        PickResultTextSectionToolbarReservedHeight + PickResultTextToolbarBodySpacing
    val actionBarReserved =
        PickResultTextActionBarReservedHeight +
            PickResultTextActionBarTopPadding +
            actionBarBottomPadding
    val tabHeaderReserved = if (showTabBar) PickResultTabHeaderReservedHeight else 0.dp

    if (landscapeDualColumn) {
        val textColumnHeight = textToolbarReserved + heights.textBodyHeight + actionBarReserved
        val rightColumnHeight = heights.imageSectionHeight +
            heights.searchDividerHeight +
            heights.searchGridHeight
        return PANEL_VERTICAL_PADDING + expandedBottomPadding +
            maxOf(textColumnHeight, rightColumnHeight)
    }

    if (showTabBar) {
        val textPageHeight = textToolbarReserved +
            heights.textBodyHeight +
            actionBarReserved +
            heights.searchDividerHeight +
            heights.searchGridHeight
        val imagePageHeight = maxOf(heights.imageSectionHeight, 200.dp)
        return PANEL_VERTICAL_PADDING + expandedBottomPadding +
            tabHeaderReserved +
            maxOf(textPageHeight, imagePageHeight)
    }

    return PANEL_VERTICAL_PADDING + expandedBottomPadding +
        tabHeaderReserved +
        heights.imageSectionHeight +
        heights.textImageDividerHeight +
        heights.searchDividerHeight +
        heights.searchGridHeight +
        textToolbarReserved +
        heights.textBodyHeight +
        actionBarReserved
}

@Composable
internal fun PickResultAuxiliaryImageBlock(
    sectionHeight: Dp,
    alphaFactor: Float,
    sectionExpanded: Boolean,
    screenshot: Bitmap?,
    panelImages: List<Bitmap>,
    currentImageIndex: Int,
    panelImageDisplaySize: PickResultImageDisplaySize,
    searchEngines: List<com.slideindex.app.settings.SearchEngineConfig>,
    onSaveScreenshot: () -> Unit,
    onSaveScreenshotLongClick: (() -> Unit)? = null,
    onShareScreenshot: () -> Unit,
    onImageSearch: () -> Unit,
    onImageShareEngineClick: (com.slideindex.app.settings.SearchEngineConfig) -> Unit,
    onPinImageToScreen: () -> Unit,
    onStashImage: () -> Unit,
    onImageClick: () -> Unit,
    onCopyImage: (() -> Unit)? = null,
    onImageIndexChange: (Int) -> Unit,
    onSectionExpandedChange: (Boolean) -> Unit,
    showImageSearchBar: Boolean = true,
    imageToolbarPosition: com.slideindex.app.settings.PickResultImageToolbarPosition =
        com.slideindex.app.settings.PickResultImageToolbarPosition.LEFT,
    collapseDragActive: Boolean = false,
    auxiliaryDragEnabled: Boolean = false,
    onDragEnd: () -> Unit = {},
    applyDrag: (Float) -> Unit = {}
) {
    if (sectionHeight <= 0.dp) return

    PickResultImageSection(
        screenshot = screenshot,
        panelImages = panelImages,
        currentImageIndex = currentImageIndex,
        imageDisplaySize = panelImageDisplaySize,
        searchEngines = searchEngines,
        showImageSearchBar = showImageSearchBar,
        imageToolbarPosition = imageToolbarPosition,
        modifier = Modifier
            .fillMaxWidth()
            .height(sectionHeight)
            .graphicsLayer {
                alpha = alphaFactor
                if (collapseDragActive) {
                    clip = true
                }
            }
            .clipToBounds()
            .then(
                if (auxiliaryDragEnabled) {
                    Modifier.pickResultLinkedVerticalDrag(
                        onDragDelta = { dragAmount -> applyDrag(-dragAmount) },
                        onDragEnd = onDragEnd
                    )
                } else {
                    Modifier
                }
            ),
        onSave = onSaveScreenshot,
        onSaveLongClick = onSaveScreenshotLongClick,
        onShare = onShareScreenshot,
        onImageSearch = onImageSearch,
        onShareEngineClick = onImageShareEngineClick,
        onPinToScreen = onPinImageToScreen,
        onStash = onStashImage,
        onImageClick = onImageClick,
        onCopyImage = onCopyImage,
        onImageIndexChange = onImageIndexChange,
        sectionExpanded = sectionExpanded,
        onSectionExpandedChange = onSectionExpandedChange
    )
}

@Composable
internal fun PickResultTextImageDividerBlock(
    dividerHeight: Dp,
    alphaFactor: Float,
    hasAuxiliaryCollapse: Boolean,
    onDragEnd: () -> Unit,
    applyDrag: (Float) -> Unit
) {
    if (dividerHeight <= 0.dp) return

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(dividerHeight)
            .clipToBounds()
            .then(
                if (hasAuxiliaryCollapse) {
                    Modifier.pointerInput(onDragEnd, applyDrag) {
                        detectVerticalDragGestures(
                            onDragEnd = onDragEnd,
                            onDragCancel = onDragEnd
                        ) { _, dragAmount ->
                            applyDrag(-dragAmount)
                        }
                    }
                } else {
                    Modifier
                }
            )
    )
}

@Composable
internal fun PickResultAuxiliarySearchBlock(
    searchDividerHeight: Dp,
    searchGridHeight: Dp,
    alphaFactor: Float,
    panelSearchEngines: List<com.slideindex.app.settings.SearchEngineConfig>,
    activeText: String,
    searchEngineGridColumns: Int,
    searchEngineGridRows: Int,
    searchEngineShowLabels: Boolean,
    appSettings: AppSettings,
    onSearchEngineClick: (com.slideindex.app.settings.SearchEngineConfig, Boolean) -> Unit,
    onDragEnd: () -> Unit,
    applyDrag: (Float) -> Unit,
    collapseDragActive: Boolean = false,
    auxiliaryDragEnabled: Boolean = false
) {
    if (searchDividerHeight > 0.dp) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(searchDividerHeight)
                .clipToBounds()
                .pointerInput(onDragEnd, applyDrag) {
                    detectVerticalDragGestures(
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragEnd
                    ) { _, dragAmount ->
                        applyDrag(dragAmount)
                    }
                },
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    if (searchGridHeight <= 0.dp) return

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(searchGridHeight)
            .graphicsLayer {
                alpha = alphaFactor
                if (collapseDragActive) {
                    clip = true
                }
            }
            .clipToBounds()
            .then(
                if (auxiliaryDragEnabled) {
                    Modifier.pickResultLinkedVerticalDrag(
                        onDragDelta = applyDrag,
                        onDragEnd = onDragEnd
                    )
                } else {
                    Modifier
                }
            )
    ) {
        PickResultTextSearchGrid(
            engines = panelSearchEngines,
            query = activeText,
            columns = searchEngineGridColumns,
            rows = searchEngineGridRows,
            showLabels = searchEngineShowLabels,
            longPressEnabled = appSettings.launchPolicyLongPressEligible(),
            onEngineClick = onSearchEngineClick
        )
    }
}

@Composable
internal fun PickResultPanelTextSlot(
    useExpandedLayout: Boolean,
    compactBodyMaxHeight: Dp,
    text: String,
    textMode: PickResultTextMode,
    textSource: PickResultTextSource,
    textSizeSp: Float,
    ocrAvailable: Boolean,
    a11yAvailable: Boolean,
    ocrLoading: Boolean,
    forceOcrChip: Boolean = false,
    barcodeResults: List<BarcodeScanResult>,
    showingTranslation: Boolean,
    translateLoading: Boolean,
    showBackgroundOcrAction: Boolean,
    auxiliaryDragEnabled: Boolean,
    activeText: String,
    onTextModeChange: (PickResultTextMode) -> Unit,
    onTextChange: (String) -> Unit,
    onBackgroundOcr: () -> Unit,
    onTextSourceChange: (PickResultTextSource) -> Unit,
    onActiveTextChange: (String) -> Unit,
    onShareText: (String) -> Unit,
    onCopy: (String, keepPanelOpen: Boolean) -> Unit,
    onTranslate: (String) -> Unit,
    onRemoveSpaces: (String, Boolean) -> Unit,
    onZoomText: (Boolean) -> Unit,
    onToolbarDragDelta: (Float) -> Unit,
    onActionBarDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onSearchDragEnd: () -> Unit = onDragEnd,
    onPinTextToScreen: (String) -> Unit,
    onStashText: (String) -> Unit,
    actionBarBottomPadding: Dp,
    actionBarDragActive: Boolean = false,
    autoSelectAll: Boolean = false,
    showSearch: Boolean = false,
    searchSelected: Boolean = false,
    searchQuickLaunch: PickResultSearchQuickLaunch? = null,
    onToggleSearchGrid: () -> Unit = {},
    hasImageContent: Boolean = false,
    onImageSearch: (() -> Unit)? = null,
    onSaveScreenshot: (() -> Unit)? = null,
    onShareScreenshot: (() -> Unit)? = null,
    onTextScrollableChange: ((Boolean) -> Unit)? = null,
) {
    val isDark = com.slideindex.app.ui.theme.LocalAppDarkTheme.current
    val cardBg = if (isDark) Color(0x10FFFFFF) else Color(0xB2FFFFFF)
    val cardBorder = if (isDark) Color(0x24FFFFFF) else Color(0x12000000)

    androidx.compose.material3.Surface(
        modifier = if (useExpandedLayout) {
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        } else {
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        },
        shape = RoundedCornerShape(16.dp),
        color = cardBg,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, cardBorder)
    ) {
        Box(
            modifier = if (useExpandedLayout) {
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            } else {
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            }
        ) {
            PickResultInteractiveTextSection(
                text = text,
                textMode = textMode,
                onTextModeChange = onTextModeChange,
                onTextChange = onTextChange,
                modifier = if (useExpandedLayout) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier.fillMaxWidth()
                },
                textSizeSp = textSizeSp,
                textSource = textSource,
                ocrAvailable = ocrAvailable,
                forceOcrChip = forceOcrChip,
                a11yAvailable = a11yAvailable,
                ocrLoading = ocrLoading,
                barcodeResults = barcodeResults,
                showingTranslation = showingTranslation,
                translateLoading = translateLoading,
                showBackgroundOcrAction = showBackgroundOcrAction,
                onBackgroundOcr = onBackgroundOcr,
                onTextSourceChange = onTextSourceChange,
                pinActionBarOutside = true,
                expandTextBlock = useExpandedLayout,
                auxiliaryDragEnabled = auxiliaryDragEnabled,
                bodyMaxHeight = if (useExpandedLayout) null else compactBodyMaxHeight,
                showSearch = showSearch,
                searchSelected = searchSelected,
                searchQuickLaunch = searchQuickLaunch,
                onSearch = { onToggleSearchGrid() },
                onActiveTextChange = onActiveTextChange,
                onShare = onShareText,
                onCopy = onCopy,
                onTranslate = onTranslate,
                onRemoveSpaces = onRemoveSpaces,
                onZoomText = onZoomText,
                onToolbarDragDelta = onToolbarDragDelta,
                onActionBarDragDelta = onActionBarDragDelta,
                onDragEnd = onDragEnd,
                onSearchDragEnd = onSearchDragEnd,
                onPinToScreen = { onPinTextToScreen(activeText) },
                onStash = { onStashText(activeText) },
                actionBarBottomPadding = actionBarBottomPadding,
                actionBarDragActive = actionBarDragActive,
                autoSelectAll = autoSelectAll,
                hasImageContent = hasImageContent,
                onImageSearch = onImageSearch,
                onSaveScreenshot = onSaveScreenshot,
                onShareScreenshot = onShareScreenshot,
                onTextScrollableChange = onTextScrollableChange,
            )
        }
    }
}

/**
 * ????????????????????state ????[content]??????????????
 * ?????? [onPanelBoundsInRoot] ???????????????????
 * [content] ??[freezeCollapseAnimation] ??????????true?????? snap ?? spring??
 */
@Composable
internal fun PickResultPanelSlideHost(
    panelRevealed: Boolean,
    panelSlideDistance: Dp,
    panelEnterAnimationMs: Int,
    panelExitAnimationMs: Int,
    onPanelBoundsInRoot: (ComposeRect) -> Unit,
    centered: Boolean = false,
    content: @Composable (freezeCollapseAnimation: Boolean) -> Unit
) {
    val density = LocalDensity.current
    var hiddenSlideDistance by remember { mutableStateOf(panelSlideDistance) }
    if (!panelRevealed) {
        hiddenSlideDistance = panelSlideDistance
    }
    val slideAnimationMs = if (panelRevealed) {
        panelEnterAnimationMs
    } else {
        panelExitAnimationMs
    }
    val panelSlideOffset by animateDpAsState(
        targetValue = if (panelRevealed) 0.dp else hiddenSlideDistance,
        animationSpec = pickPanelSlideAnimationSpec(slideAnimationMs),
        label = "pickPanelSlide"
    )
    val isPanelSlideAnimating = panelSlideOffset > 0.5.dp
    val cornerPx = with(density) { PickResultPanelCardCorner.toPx() }
    val blurRadiusPx = (57f * density.density).roundToInt()
    val isDark = LocalAppDarkTheme.current
    val frostedTint = if (isDark) 0x661C1C1E.toInt() else 0x66F5F5F7.toInt()

    Box(
        modifier = Modifier
            .graphicsLayer {
                translationY = with(density) { panelSlideOffset.toPx() }
                compositingStrategy = if (isPanelSlideAnimating) {
                    CompositingStrategy.Offscreen
                } else {
                    CompositingStrategy.Auto
                }
            }
            .then(
                if (centered) {
                    Modifier.pickResultPanelCard()
                } else {
                    Modifier.pickResultBottomPanelCard(suppressShadow = isPanelSlideAnimating)
                }
            )
            .onGloballyPositioned { coords ->
                if (!isPanelSlideAnimating) {
                    onPanelBoundsInRoot(coords.boundsInRoot())
                }
            }
    ) {
        LocalFrostedGlassBackdrop(
            modifier = Modifier.matchParentSize(),
            cornerRadiusPx = cornerPx,
            blurRadiusPx = blurRadiusPx,
            tintColor = frostedTint,
            enabled = true
        )
        content(isPanelSlideAnimating)
    }
}

@Composable
internal fun PickResultCollapsePanelColumn(
    controller: AuxiliaryCollapseController,
    panelContentHeight: Dp,
    overlayImeBottom: Dp,
    pickPanelAlpha: Float,
    imageSearchVisible: Boolean,
    dismissInteraction: MutableInteractionSource,
    cardInteraction: MutableInteractionSource,
    onDismiss: () -> Unit,
    isEditMode: Boolean,
    hasImageContent: Boolean,
    hasSearchGrid: Boolean,
    hasAuxiliaryCollapse: Boolean,
    showTextSection: Boolean,
    minImageSectionHeight: Dp,
    maxImageSectionHeight: Dp,
    textImageDividerBaseHeight: Dp,
    searchGridSectionPrefixHeight: Dp,
    expandedSearchGridContentHeight: Dp,
    idealTextBodyHeight: Dp,
    minTextBodyHeight: Dp,
    screenshot: Bitmap?,
    panelImages: List<Bitmap>,
    currentImageIndex: Int,
    panelImageDisplaySize: PickResultImageDisplaySize,
    searchEngines: List<com.slideindex.app.settings.SearchEngineConfig>,
    onSaveScreenshot: () -> Unit,
    onSaveScreenshotLongClick: (() -> Unit)? = null,
    onShareScreenshot: () -> Unit,
    onImageSearch: () -> Unit,
    onImageShareEngineClick: (com.slideindex.app.settings.SearchEngineConfig) -> Unit,
    onPinImageToScreen: () -> Unit,
    onStashImage: () -> Unit,
    onImageClick: () -> Unit,
    onCopyImage: (() -> Unit)? = null,
    onImageIndexChange: (Int) -> Unit,
    onImageSectionExpandedChange: (Boolean) -> Unit,
    onDragEnd: () -> Unit,
    onSearchDragEnd: () -> Unit = onDragEnd,
    applyDrag: (Float) -> Unit,
    applySearchDrag: (Float) -> Unit = applyDrag,
    panelSearchEngines: List<com.slideindex.app.settings.SearchEngineConfig>,
    activeText: String,
    searchEngineGridColumns: Int,
    searchEngineGridRows: Int,
    searchEngineShowLabels: Boolean,
    appSettings: AppSettings,
    onSearchEngineClick: (com.slideindex.app.settings.SearchEngineConfig, Boolean) -> Unit,
    text: String?,
    textMode: PickResultTextMode,
    textSource: PickResultTextSource,
    textSizeSp: Float,
    ocrAvailable: Boolean,
    a11yAvailable: Boolean,
    ocrLoading: Boolean,
    forceOcrChip: Boolean = false,
    isShareImageOcr: Boolean,
    barcodeResults: List<BarcodeScanResult>,
    showingTranslation: Boolean,
    translateLoading: Boolean,
    onBackgroundOcr: () -> Unit,
    onTextSourceChange: (PickResultTextSource) -> Unit,
    onActiveTextChange: (String) -> Unit,
    onTextModeChange: (PickResultTextMode) -> Unit,
    onTextChange: (String) -> Unit,
    onShareText: (String) -> Unit,
    onCopy: (String, keepPanelOpen: Boolean) -> Unit,
    onTranslate: (String) -> Unit,
    onRemoveSpaces: (String, Boolean) -> Unit,
    onZoomText: (Boolean) -> Unit,
    onPinTextToScreen: (String) -> Unit,
    onStashText: (String) -> Unit,
    textFirstPanelEnabled: Boolean = false,
    freezeCollapseAnimation: Boolean = false,
    landscapeDualColumn: Boolean = false,
    searchQuickLaunch: PickResultSearchQuickLaunch? = null,
    onToggleSearchGrid: () -> Unit = {}
) {
    val panelStyle = appSettings.floatBallPickPanelStyle
    val isTabPaged = panelStyle == com.slideindex.app.settings.PickResultPanelStyle.TAB_PAGED && !landscapeDualColumn
    val isIntegratedBottomBar = panelStyle == com.slideindex.app.settings.PickResultPanelStyle.INTEGRATED_BOTTOM_BAR
    val showTabBar = isTabPaged && hasImageContent && showTextSection

    val hasMeaningfulText = !text.isNullOrBlank()
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = if (hasMeaningfulText) 0 else 1,
        pageCount = { 2 }
    )
    val density = LocalDensity.current
    var textTabPageHeightPx by remember { mutableIntStateOf(0) }
    var imageTabPageHeightPx by remember { mutableIntStateOf(0) }
    val tabPagerProgress = (pagerState.currentPage + pagerState.currentPageOffsetFraction).coerceIn(0f, 1f)
    val measuredTabPagerHeightPx = when {
        textTabPageHeightPx <= 0 && imageTabPageHeightPx <= 0 -> 0
        textTabPageHeightPx <= 0 -> imageTabPageHeightPx
        imageTabPageHeightPx <= 0 -> textTabPageHeightPx
        else -> (textTabPageHeightPx + (imageTabPageHeightPx - textTabPageHeightPx) * tabPagerProgress).roundToInt()
    }

    val editModeProgress by animateFloatAsState(
        targetValue = if (isEditMode) 1f else 0f,
        animationSpec = tween(
            durationMillis = EDIT_MODE_ANIMATION_MS,
            easing = FastOutSlowInEasing
        ),
        label = "editMode"
    )
    val normalLayoutFactor = 1f - editModeProgress
    val snapCollapseAnimation = controller.isDragging || freezeCollapseAnimation

    val animatedCollapseProgress by animateFloatAsState(
        targetValue = controller.collapseProgress,
        animationSpec = if (snapCollapseAnimation) {
            snap()
        } else {
            spring(
                dampingRatio = 0.92f,
                stiffness = 380f
            )
        },
        label = "collapse"
    )
    val expansionFraction = if (snapCollapseAnimation) {
        1f - controller.collapseProgress
    } else {
        1f - animatedCollapseProgress
    }

    val animatedSearchCollapseProgress by animateFloatAsState(
        targetValue = controller.searchCollapseProgress,
        animationSpec = if (snapCollapseAnimation) {
            snap()
        } else {
            spring(
                dampingRatio = 0.92f,
                stiffness = 380f
            )
        },
        label = "searchCollapse"
    )
    val decoupleSearchFromImage = hasSearchGrid
    val searchExpansionFraction = if (decoupleSearchFromImage) {
        if (snapCollapseAnimation) {
            1f - controller.searchCollapseProgress
        } else {
            1f - animatedSearchCollapseProgress
        }
    } else {
        0f
    }
    val imageExpansionFraction = expansionFraction

    val searchPresence = if (hasSearchGrid) {
        (searchExpansionFraction * normalLayoutFactor).coerceIn(0f, 1f)
    } else {
        0f
    }
    val targetActionBarBottomInset = lerp(
        PickResultTextActionBarBottomPaddingWhenAlone,
        0.dp,
        searchPresence
    ).coerceAtLeast(0.dp)
    var frozenActionBarBottomInset by remember { mutableStateOf<Dp?>(null) }
    SideEffect {
        if (controller.isSearchDragging) {
            if (frozenActionBarBottomInset == null) {
                frozenActionBarBottomInset = targetActionBarBottomInset
            }
        } else {
            frozenActionBarBottomInset = null
        }
    }
    val insetAnimationTarget = if (controller.isSearchDragging && frozenActionBarBottomInset != null) {
        frozenActionBarBottomInset!!
    } else {
        targetActionBarBottomInset
    }
    val animatedActionBarBottomInset by animateDpAsState(
        targetValue = insetAnimationTarget,
        animationSpec = if (controller.isSearchDragging || freezeCollapseAnimation) {
            snap()
        } else {
            spring(
                dampingRatio = 0.92f,
                stiffness = 380f
            )
        },
        label = "actionBarBottomInset"
    )
    // spring ?????????padding ????
    val actionBarBottomInset = animatedActionBarBottomInset.coerceAtLeast(0.dp)
    val searchCollapseDragActive = controller.isSearchDragging
    val imageCollapseDragActive = controller.isDragging && !controller.isSearchDragging

    val stablePanelHeight = remember(
        panelContentHeight,
        hasSearchGrid,
        hasImageContent,
        minImageSectionHeight,
        maxImageSectionHeight,
        textImageDividerBaseHeight,
        searchGridSectionPrefixHeight,
        expandedSearchGridContentHeight,
        idealTextBodyHeight,
        minTextBodyHeight,
        landscapeDualColumn,
        showTabBar
    ) {
        if (!hasAuxiliaryCollapse) {
            null
        } else {
            computePickResultExpandedPanelOuterHeight(
                panelContentHeight = panelContentHeight,
                hasSearchGrid = hasSearchGrid,
                hasImageContent = hasImageContent,
                minImageSectionHeight = minImageSectionHeight,
                maxImageSectionHeight = maxImageSectionHeight,
                textImageDividerBaseHeight = textImageDividerBaseHeight,
                searchGridSectionPrefixHeight = searchGridSectionPrefixHeight,
                expandedSearchGridContentHeight = expandedSearchGridContentHeight,
                idealTextBodyHeight = idealTextBodyHeight,
                minTextBodyHeight = minTextBodyHeight,
                landscapeDualColumn = landscapeDualColumn,
                showTabBar = showTabBar
            )
        }
    }

    val useWeightedTextLayout = isEditMode

    // 键盘让位只能让一次。居中落位时外层 [FloatBallPickResultContent] 已经用 `padding(bottom = ime)`
    // 把整块卡片抬到键盘之上（卡片底边 = 键盘顶边），内层再让一次就等于在卡片里白留一整个键盘高度：
    // 真机实测 530dp 的卡片里有 320dp 是空白，正文被挤到最小、动作栏顶到正文下面而不是贴卡片底边。
    // 贴底落位没有外层让位，这一层必须保留。
    val innerImeBottom =
        if (appSettings.floatBallPickPanelPlacement ==
            com.slideindex.app.settings.PickResultPanelPlacement.CENTER
        ) {
            0.dp
        } else {
            overlayImeBottom
        }

    val fixedPanelHeight = when {
        isEditMode -> panelContentHeight + innerImeBottom
        else -> null
    }

    val effectivePanelHeight = fixedPanelHeight ?: panelContentHeight
    val panelInnerHeight = effectivePanelHeight - PANEL_VERTICAL_PADDING * 2

    val collapseHeights = computePickResultCollapseHeights(
        panelInnerHeight = panelInnerHeight,
        normalLayoutFactor = normalLayoutFactor,
        hasImageContent = hasImageContent,
        minImageSectionHeight = minImageSectionHeight,
        maxImageSectionHeight = maxImageSectionHeight,
        textImageDividerBaseHeight = textImageDividerBaseHeight,
        searchDividerBaseHeight = searchGridSectionPrefixHeight,
        expandedSearchGridContentHeight = expandedSearchGridContentHeight,
        idealTextBodyHeight = idealTextBodyHeight,
        minTextBodyHeight = minTextBodyHeight,
        actionBarBottomPadding = actionBarBottomInset,
        imageExpansionFraction = imageExpansionFraction,
        searchExpansionFraction = searchExpansionFraction,
        landscapeDualColumn = landscapeDualColumn,
        showTabBar = showTabBar
    )

    val applyDragState = rememberUpdatedState(applyDrag)
    val applySearchDragState = rememberUpdatedState(applySearchDrag)
    val wrappedApplyDrag: (Float) -> Unit = remember {
        { delta -> applyDragState.value(delta) }
    }
    val wrappedApplySearchDrag: (Float) -> Unit = remember {
        { delta -> applySearchDragState.value(delta) }
    }
    val maxSearchSectionHeight = searchGridSectionPrefixHeight + expandedSearchGridContentHeight
    val totalImageCollapsiblePx = remember(
        maxImageSectionHeight,
        minImageSectionHeight,
        density
    ) {
        with(density) {
            (maxImageSectionHeight - minImageSectionHeight).toPx().coerceAtLeast(1f)
        }
    }
    val totalSearchCollapsiblePx = remember(
        maxSearchSectionHeight,
        density
    ) {
        with(density) {
            maxSearchSectionHeight.toPx().coerceAtLeast(1f)
        }
    }
    val totalCollapsiblePx = totalImageCollapsiblePx
    val toolbarLinkedRangePx = remember(
        hasImageContent,
        minImageSectionHeight,
        maxImageSectionHeight,
        textImageDividerBaseHeight,
        density
    ) {
        with(density) {
            if (hasImageContent) {
                (maxImageSectionHeight - minImageSectionHeight + textImageDividerBaseHeight).toPx()
            } else {
                1f
            }.coerceAtLeast(1f)
        }
    }
    val onToolbarDragDelta = remember(
        controller,
        totalCollapsiblePx,
        toolbarLinkedRangePx,
        wrappedApplyDrag
    ) {
        val scale = totalCollapsiblePx / toolbarLinkedRangePx
        { dragAmount: Float -> wrappedApplyDrag(-dragAmount * scale) }
    }
    val onActionBarDragDelta = remember(
        controller,
        totalCollapsiblePx,
        toolbarLinkedRangePx,
        wrappedApplyDrag
    ) {
        val scale = totalCollapsiblePx / toolbarLinkedRangePx
        { dragAmount: Float -> wrappedApplyDrag(dragAmount * scale) }
    }
    val onDragEndState = rememberUpdatedState(onDragEnd)
    val onSearchDragEndState = rememberUpdatedState(onSearchDragEnd)
    val onTextDragEnd = remember {
        { onDragEndState.value() }
    }
    val onActionBarDragEnd = remember {
        { onSearchDragEndState.value() }
    }

    val imageSectionExpanded = imageExpansionFraction > 0.5f
    val auxiliaryDragEnabled = hasAuxiliaryCollapse && !isEditMode

    var isTextScrollable by remember { mutableStateOf(false) }
    val isTextScrollableState = rememberUpdatedState(isTextScrollable)

    val onImageSectionExpandedChangeState = rememberUpdatedState(onImageSectionExpandedChange)
    val textNestedScrollConnection = remember(
        auxiliaryDragEnabled,
        hasImageContent,
        landscapeDualColumn,
        showTabBar,
        totalImageCollapsiblePx,
        controller
    ) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: NestedScrollSource): androidx.compose.ui.geometry.Offset {
                // 手指在文字区向上滑动查看下文时（available.y < 0），优先收起图片区以扩大文字区视口
                // 注意：分 Tab 模式下图片区已独立在另一页，文字区天然拥有最大自适应视口，不触发折叠拦截；
                // 仅当单列垂直模式且文本区有真实溢出内容（存在滚动条）时才允许折叠
                if (auxiliaryDragEnabled && hasImageContent && !landscapeDualColumn && !showTabBar &&
                    isTextScrollableState.value &&
                    available.y < 0f && controller.collapseProgress < 1f
                ) {
                    val remainingPx = (1f - controller.collapseProgress) * totalImageCollapsiblePx
                    val consumePx = minOf(-available.y, remainingPx)
                    if (consumePx > 0f) {
                        controller.applyDrag(consumePx)
                        return androidx.compose.ui.geometry.Offset(0f, -consumePx)
                    }
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }

            override fun onPostScroll(
                consumed: androidx.compose.ui.geometry.Offset,
                available: androidx.compose.ui.geometry.Offset,
                source: NestedScrollSource
            ): androidx.compose.ui.geometry.Offset {
                // 手指在文字区向下滑动且文字已处于顶部时（available.y > 0），拉下展开图片区（分 Tab 模式除外）
                if (auxiliaryDragEnabled && hasImageContent && !landscapeDualColumn && !showTabBar &&
                    available.y > 0f && controller.collapseProgress > 0f
                ) {
                    val remainingPx = controller.collapseProgress * totalImageCollapsiblePx
                    val consumePx = minOf(available.y, remainingPx)
                    if (consumePx > 0f) {
                        controller.applyDrag(-consumePx)
                        return androidx.compose.ui.geometry.Offset(0f, consumePx)
                    }
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (auxiliaryDragEnabled && hasImageContent && !landscapeDualColumn && !showTabBar && controller.isDragging) {
                    controller.endDrag { expanded ->
                        onImageSectionExpandedChangeState.value(expanded)
                    }
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (auxiliaryDragEnabled && hasImageContent && !landscapeDualColumn && !showTabBar && controller.isDragging) {
                    controller.endDrag { expanded ->
                        onImageSectionExpandedChangeState.value(expanded)
                    }
                }
                return Velocity.Zero
            }
        }
    }

    val renderTextSlot: @Composable () -> Unit = {
        PickResultPanelTextSlot(
            useExpandedLayout = useWeightedTextLayout,
            compactBodyMaxHeight = if (useWeightedTextLayout) {
                minTextBodyHeight
            } else {
                collapseHeights.textBodyHeight
            },
            text = text.orEmpty(),
            textMode = textMode,
            textSource = textSource,
            textSizeSp = textSizeSp,
            ocrAvailable = ocrAvailable,
            forceOcrChip = forceOcrChip,
            a11yAvailable = a11yAvailable,
            ocrLoading = ocrLoading,
            barcodeResults = barcodeResults,
            showingTranslation = showingTranslation,
            translateLoading = translateLoading,
            showBackgroundOcrAction = isShareImageOcr && ocrLoading,
            auxiliaryDragEnabled = auxiliaryDragEnabled,
            activeText = activeText,
            onTextModeChange = onTextModeChange,
            onTextChange = onTextChange,
            onBackgroundOcr = onBackgroundOcr,
            onTextSourceChange = onTextSourceChange,
            onActiveTextChange = onActiveTextChange,
            onShareText = onShareText,
            onCopy = onCopy,
            onTranslate = onTranslate,
            onRemoveSpaces = onRemoveSpaces,
            onZoomText = onZoomText,
            onToolbarDragDelta = onToolbarDragDelta,
            onActionBarDragDelta = onActionBarDragDelta,
            onDragEnd = onTextDragEnd,
            onSearchDragEnd = onActionBarDragEnd,
            onPinTextToScreen = onPinTextToScreen,
            onStashText = onStashText,
            actionBarBottomPadding = actionBarBottomInset,
            actionBarDragActive = searchCollapseDragActive,
            autoSelectAll = appSettings.floatBallPickAutoSelectAll,
            showSearch = hasSearchGrid,
            searchSelected = searchExpansionFraction > 0.5f,
            searchQuickLaunch = searchQuickLaunch,
            onToggleSearchGrid = onToggleSearchGrid,
            hasImageContent = false,
            onImageSearch = null,
            onSaveScreenshot = null,
            onShareScreenshot = null,
            onTextScrollableChange = { isTextScrollable = it },
        )
    }

    val renderImageBlock: @Composable () -> Unit = {
        val displayHeight = if (showTabBar) {
            maxImageSectionHeight.coerceAtLeast(200.dp)
        } else {
            collapseHeights.imageSectionHeight
        }
        PickResultAuxiliaryImageBlock(
            sectionHeight = displayHeight,
            alphaFactor = normalLayoutFactor,
            sectionExpanded = imageSectionExpanded,
            screenshot = screenshot,
            panelImages = panelImages,
            currentImageIndex = currentImageIndex,
            panelImageDisplaySize = panelImageDisplaySize,
            searchEngines = searchEngines,
            onSaveScreenshot = onSaveScreenshot,
            onSaveScreenshotLongClick = onSaveScreenshotLongClick,
            onShareScreenshot = onShareScreenshot,
            onImageSearch = onImageSearch,
            onImageShareEngineClick = onImageShareEngineClick,
            onPinImageToScreen = onPinImageToScreen,
            onStashImage = onStashImage,
            onImageClick = onImageClick,
            onCopyImage = onCopyImage,
            onImageIndexChange = onImageIndexChange,
            onSectionExpandedChange = onImageSectionExpandedChange,
            showImageSearchBar = true,
            imageToolbarPosition = appSettings.floatBallPickImageToolbarPosition,
            collapseDragActive = imageCollapseDragActive,
            auxiliaryDragEnabled = auxiliaryDragEnabled,
            onDragEnd = onDragEnd,
            applyDrag = wrappedApplyDrag
        )
    }

    val renderSearchBlock: @Composable () -> Unit = {
        if (hasSearchGrid) {
            PickResultAuxiliarySearchBlock(
                searchDividerHeight = collapseHeights.searchDividerHeight,
                searchGridHeight = collapseHeights.searchGridHeight,
                alphaFactor = normalLayoutFactor,
                panelSearchEngines = panelSearchEngines,
                activeText = activeText,
                searchEngineGridColumns = searchEngineGridColumns,
                searchEngineGridRows = searchEngineGridRows,
                searchEngineShowLabels = searchEngineShowLabels,
                appSettings = appSettings,
                onSearchEngineClick = onSearchEngineClick,
                onDragEnd = onSearchDragEnd,
                applyDrag = wrappedApplySearchDrag,
                collapseDragActive = searchCollapseDragActive,
                auxiliaryDragEnabled = false
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = innerImeBottom)
            .then(
                if (fixedPanelHeight != null) {
                    Modifier.height(fixedPanelHeight)
                } else {
                    Modifier
                        .wrapContentHeight()
                        .heightIn(
                            min = if (landscapeDualColumn) 0.dp else minOf(190.dp, panelContentHeight),
                            max = panelContentHeight
                        )
                }
            )
            .graphicsLayer { alpha = pickPanelAlpha }
            .then(
                if (imageSearchVisible) {
                    Modifier.clickable(
                        interactionSource = cardInteraction,
                        indication = null,
                        onClick = onDismiss
                    )
                } else {
                    Modifier
                }
            )
            .padding(top = PANEL_VERTICAL_PADDING, bottom = PANEL_VERTICAL_PADDING),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        if (landscapeDualColumn) {
            Row(
                modifier = if (useWeightedTextLayout) {
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                } else {
                    Modifier.fillMaxWidth()
                }
            ) {
                if (showTextSection) {
                    Box(
                        modifier = Modifier
                            .weight(LANDSCAPE_DUAL_COLUMN_TEXT_WEIGHT)
                            .fillMaxHeight()
                    ) {
                        renderTextSlot()
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(LANDSCAPE_DUAL_COLUMN_AUX_WEIGHT)
                        .fillMaxHeight()
                ) {
                    renderImageBlock()
                    renderSearchBlock()
                }
            }
        } else {
            if (showTabBar) {
                PickResultSegmentedTabHeader(
                    selectedTab = pagerState.currentPage,
                    pagerProgress = tabPagerProgress,
                    onTabSelected = { page ->
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(
                                page = page,
                                animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f)
                            )
                        }
                    }
                )
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (useWeightedTextLayout) {
                                Modifier.weight(1f)
                            } else if (measuredTabPagerHeightPx > 0) {
                                Modifier
                                    .height(with(density) { measuredTabPagerHeightPx.toDp() })
                                    .clipToBounds()
                            } else {
                                Modifier
                            }
                        ),
                    beyondViewportPageCount = 1,
                    verticalAlignment = Alignment.Top
                ) { page ->
                    if (page == 0) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (useWeightedTextLayout) {
                                        Modifier
                                    } else {
                                        Modifier
                                            .wrapContentHeight(align = Alignment.Top, unbounded = true)
                                            .onSizeChanged { size ->
                                                if (size.height > 0 && textTabPageHeightPx != size.height) {
                                                    textTabPageHeightPx = size.height
                                                }
                                            }
                                    }
                                )
                        ) {
                            Box(
                                modifier = (if (useWeightedTextLayout) {
                                    Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                } else {
                                    Modifier.fillMaxWidth()
                                }).nestedScroll(textNestedScrollConnection)
                            ) {
                                renderTextSlot()
                            }
                            renderSearchBlock()
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (useWeightedTextLayout) {
                                        Modifier.weight(1f, fill = false)
                                    } else {
                                        Modifier
                                            .wrapContentHeight(align = Alignment.Top, unbounded = true)
                                            .onSizeChanged { size ->
                                                if (size.height > 0 && imageTabPageHeightPx != size.height) {
                                                    imageTabPageHeightPx = size.height
                                                }
                                            }
                                    }
                                )
                        ) {
                            renderImageBlock()
                        }
                    }
                }
            } else {
                renderImageBlock()
                if (showTextSection) {
                    PickResultTextImageDividerBlock(
                        dividerHeight = collapseHeights.textImageDividerHeight,
                        alphaFactor = normalLayoutFactor,
                        hasAuxiliaryCollapse = auxiliaryDragEnabled,
                        onDragEnd = onDragEnd,
                        applyDrag = wrappedApplyDrag
                    )
                    Box(
                        modifier = (if (useWeightedTextLayout) {
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        } else {
                            Modifier.fillMaxWidth()
                        }).nestedScroll(textNestedScrollConnection)
                    ) {
                        renderTextSlot()
                    }
                }
                renderSearchBlock()
            }
        }
    }
}
