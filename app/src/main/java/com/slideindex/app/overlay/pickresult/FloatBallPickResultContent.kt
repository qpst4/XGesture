package com.slideindex.app.overlay.pickresult

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.di.OverlayDependencyAccess
import kotlinx.coroutines.launch
import com.slideindex.app.barcode.BarcodeScanResult
import com.slideindex.app.overlay.FloatBallImageSearchPanel
import com.slideindex.app.overlay.FloatBallPickResultPanel
import com.slideindex.app.overlay.PickResultContentOrigin
import com.slideindex.app.overlay.PickResultTextSource
import com.slideindex.app.overlay.ScreenshotLayoutMeta
import com.slideindex.app.overlay.overlayPickPanelMaxHeightFraction
import com.slideindex.app.overlay.overlayPickPanelMaxWidth
import com.slideindex.app.overlay.overlayPickPanelWidth
import com.slideindex.app.overlay.overlayContainerHeightDp
import com.slideindex.app.overlay.overlayContainerWidthDp
import com.slideindex.app.overlay.overlayIsLandscape
import com.slideindex.app.overlay.rememberOverlayImeBottomHeight
import com.slideindex.app.overlay.pickresult.detectPickResultDismissOutsidePanelTap
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.SearchEngineStore
import com.slideindex.app.ui.theme.OverlayAwareModuleTheme

@Composable
internal fun FloatBallPickResultContent(
    panelShowToken: Int,
    panelRevealed: Boolean,
    panelNotification: String?,
    onShowInPanelMessage: (String?) -> Unit,
    text: String?,
    screenshot: Bitmap?,
    panelImages: List<Bitmap>,
    currentImageIndex: Int,
    contentOrigin: PickResultContentOrigin,
    activeText: String,
    textMode: PickResultTextMode,
    textSource: PickResultTextSource,
    ocrAvailable: Boolean,
    a11yAvailable: Boolean,
    ocrLoading: Boolean,
    isShareImageOcr: Boolean,
    barcodeResults: List<BarcodeScanResult>,
    showingTranslation: Boolean,
    translateLoading: Boolean,
    onBackgroundOcr: () -> Unit,
    imageSearchPickPanelTransparency: Float,
    textSizeSp: Float,
    searchEngines: List<com.slideindex.app.settings.SearchEngineConfig>,
    searchEngineGridColumns: Int,
    searchEngineGridRows: Int,
    searchEngineShowLabels: Boolean,
    appSettings: AppSettings,
    onTextSourceChange: (PickResultTextSource) -> Unit,
    onActiveTextChange: (String) -> Unit,
    onTextModeChange: (PickResultTextMode) -> Unit,
    onDismiss: () -> Unit,
    onTextChange: (String) -> Unit,
    onCopy: (String, keepPanelOpen: Boolean) -> Unit,
    onShareText: (String) -> Unit,
    onTranslate: (String) -> Unit,
    onRemoveSpaces: (String, removeAll: Boolean) -> Unit,
    onSaveScreenshot: () -> Unit,
    onSaveScreenshotLongClick: (() -> Unit)? = null,
    onShareScreenshot: () -> Unit,
    onImageShareEngineClick: (com.slideindex.app.settings.SearchEngineConfig) -> Unit,
    onImageSearch: () -> Unit,
    onSearchEngineClick: (com.slideindex.app.settings.SearchEngineConfig, Boolean) -> Unit,
    onSearchQuickLaunch: (fullscreen: Boolean) -> Unit = {},
    onPinTextToScreen: (String) -> Unit,
    onStashText: (String) -> Unit,
    onPinImageToScreen: () -> Unit,
    onStashImage: () -> Unit,
    onImageClick: () -> Unit,
    onCopyImage: (() -> Unit)? = null,
    onImageIndexChange: (Int) -> Unit,
    screenRect: Rect?,
    layoutMeta: ScreenshotLayoutMeta?
) {
    val context = LocalContext.current
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    val settingsRepository = remember(context) {
        OverlayDependencyAccess.overlayDependencies(context)?.settingsRepository
    }

    LaunchedEffect(panelNotification) {
        if (panelNotification != null) {
            kotlinx.coroutines.delay(2000L)
            onShowInPanelMessage(null)
        }
    }

    val hasTextSection = ocrLoading || !text.isNullOrBlank() || screenshot != null ||
        panelImages.isNotEmpty() || ocrAvailable || barcodeResults.isNotEmpty() ||
        contentOrigin == PickResultContentOrigin.STASH_CLIPBOARD
    val isEditMode = textMode == PickResultTextMode.EDIT
    val showTextSection = hasTextSection || isEditMode
    val hasImageContent = panelImages.isNotEmpty() || screenshot != null
    val textFirstPanelEnabled = appSettings.floatBallPickTextFirstPanel
    // 屏幕取词入口（悬浮球点词/拖选、区域截图、指针悬停、触钮、通用复制）左上角保留一颗 OCR 来源芯片：
    // 识别开关没开或模型没装时置灰，而不是消失。分享图片 OCR 与剪贴板/收纳入口不适用，
    // 它们各自按可用性只显示对应那一颗。
    val forceOcrSourceChip =
        contentOrigin == PickResultContentOrigin.SCREEN_PICK && !isShareImageOcr &&
            !ocrAvailable && !ocrLoading
    val reserveImageSectionPlaceholder = textFirstPanelEnabled &&
        contentOrigin == PickResultContentOrigin.SCREEN_PICK &&
        !hasImageContent
    val showImageSection = hasImageContent || reserveImageSectionPlaceholder
    val imageSearchVisible by FloatBallImageSearchPanel.panelVisible
    val pickPanelAlphaTarget = if (imageSearchVisible) {
        1f - imageSearchPickPanelTransparency.coerceIn(0f, 1f)
    } else {
        1f
    }
    val pickPanelAlpha by animateFloatAsState(
        targetValue = pickPanelAlphaTarget,
        animationSpec = tween(
            durationMillis = appSettings.floatBallPickPanelEnterAnimationMs.coerceIn(64, 400)
        ),
        label = "pickPanelAlphaForImageSearch"
    )

    val density = LocalDensity.current
    val displayMetrics = LocalContext.current.applicationContext.resources.displayMetrics
    val landscapeDualColumn = overlayIsLandscape() &&
        showImageSection &&
        showTextSection &&
        hasImageContent &&
        !isEditMode
    val maxPanelHeight = overlayContainerHeightDp() *
        overlayPickPanelMaxHeightFraction(appSettings.floatBallPickPanelPlacement)
    val centeredPanel = appSettings.floatBallPickPanelPlacement ==
        com.slideindex.app.settings.PickResultPanelPlacement.CENTER
    val panelMaxImageHeight = pickResultImageMaxHeightDp()
    val panelLayoutWidth = overlayPickPanelMaxWidth(appSettings.floatBallPickPanelPlacement)
        ?: overlayContainerWidthDp()
    val imageContentWidth = if (landscapeDualColumn) {
        (panelLayoutWidth * LANDSCAPE_DUAL_COLUMN_AUX_WEIGHT - 40.dp).coerceAtLeast(80.dp)
    } else {
        // 居中样式两侧有留白，图片区按面板实际宽度收窄，避免超出卡片被裁切。
        val extraEdgePadding = if (centeredPanel) {
            (overlayContainerWidthDp() - panelLayoutWidth).coerceAtLeast(0.dp)
        } else {
            0.dp
        }
        pickResultImageContentWidth(horizontalPadding = 40.dp + extraEdgePadding)
    }

    val dismissInteraction = remember { MutableInteractionSource() }
    val cardInteraction = remember { MutableInteractionSource() }
    val panelSearchEngines = remember(searchEngines) {
        SearchEngineStore.textPickPanelEngines(searchEngines)
    }
    val hasSearchGrid = showTextSection && panelSearchEngines.isNotEmpty()
    val effectiveSearchGridColumns = if (overlayIsLandscape()) {
        val gridWidth = if (landscapeDualColumn) {
            panelLayoutWidth * LANDSCAPE_DUAL_COLUMN_AUX_WEIGHT
        } else {
            panelLayoutWidth
        }
        effectiveSearchGridColumns(searchEngineGridColumns, gridWidth, isLandscape = true)
    } else {
        searchEngineGridColumns
    }
    val searchGridReservedHeight = if (hasSearchGrid) {
        pickResultSearchGridReservedHeight(
            searchEngineGridRows,
            searchEngineShowLabels,
            effectiveSearchGridColumns
        )
    } else {
        0.dp
    }
    val textImageDividerBaseHeight = if (showImageSection && showTextSection) {
        TEXT_IMAGE_DIVIDER_HEIGHT
    } else {
        0.dp
    }
    val textSectionChromeHeight = if (showTextSection) {
        pickResultTextSectionChromeReservedHeight()
    } else {
        0.dp
    }
    val idealTextBodyHeight = if (showTextSection) {
        pickResultTextBodyAllocatedHeight(textSizeSp)
    } else {
        0.dp
    }
    val minTextBodyHeight = if (showTextSection) {
        pickResultMinTextBodyAllocatedHeight(
            textSizeSp = textSizeSp,
            lines = pickResultMinTextBodyLines()
        )
    } else {
        0.dp
    }
    val panelVerticalPadding = PANEL_VERTICAL_PADDING * 2
    val imageSectionFixedChrome = if (showImageSection) {
        pickResultImageSectionReservedHeight(0.dp, true) // Always use true for stable max height calculation
    } else {
        0.dp
    }
    val reservedForTextAndChrome = if (landscapeDualColumn) {
        panelVerticalPadding + searchGridReservedHeight + imageSectionFixedChrome
    } else {
        panelVerticalPadding +
            (if (showTextSection) {
                textSectionChromeHeight + textImageDividerBaseHeight + minTextBodyHeight
            } else {
                0.dp
            }) +
            searchGridReservedHeight
    }
    val affordableImageMaxHeight = when {
        !hasImageContent -> panelMaxImageHeight
        landscapeDualColumn -> {
            (maxPanelHeight - panelVerticalPadding - searchGridReservedHeight - imageSectionFixedChrome)
                .coerceAtLeast(PANEL_MIN_IMAGE_HEIGHT)
        }
        else -> {
            (maxPanelHeight - reservedForTextAndChrome - imageSectionFixedChrome)
                .coerceAtLeast(PANEL_MIN_IMAGE_HEIGHT)
        }
    }
    val effectivePanelMaxImageHeight = minOf(panelMaxImageHeight, affordableImageMaxHeight)
    val panelImageDisplaySize = screenshot?.let { bitmap ->
        pickResultImageDisplaySize(
            bitmap = bitmap,
            contentWidth = imageContentWidth,
            maxHeight = effectivePanelMaxImageHeight,
            density = density,
            screenRect = screenRect,
            layoutMeta = layoutMeta,
            screenWidthPx = displayMetrics.widthPixels,
            screenHeightPx = displayMetrics.heightPixels
        )
    } ?: PickResultImageDisplaySize(0.dp, 0.dp)

    val isIntegratedBottomBar = appSettings.floatBallPickPanelStyle == com.slideindex.app.settings.PickResultPanelStyle.INTEGRATED_BOTTOM_BAR
    val collapsedImageSectionHeight = 0.dp
    val minImageSectionHeight = 0.dp
    val maxImageSectionHeight = when {
        hasImageContent -> pickResultImageSectionReservedHeight(
            imageMaxHeight = panelImageDisplaySize.height,
            isImageVisible = true,
            includeSearchBar = true
        )
        reserveImageSectionPlaceholder -> pickResultImageSectionReservedHeight(
            imageMaxHeight = 0.dp,
            isImageVisible = false,
            includeSearchBar = true
        )
        else -> 0.dp
    }
    val searchGridSectionPrefixHeight = if (showTextSection || showImageSection) {
        12.dp + 1.dp + 12.dp
    } else {
        0.dp
    }
    val expandedSearchGridContentHeight = if (hasSearchGrid) {
        searchGridContentHeight(
            searchEngineGridRows,
            searchEngineShowLabels,
            effectiveSearchGridColumns
        )
    } else {
        0.dp
    }
    val maxSearchSectionHeight = if (hasSearchGrid) {
        searchGridSectionPrefixHeight + expandedSearchGridContentHeight
    } else {
        0.dp
    }
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

    val initialSearchGridExpanded = when (appSettings.floatBallPickSearchGridDefaultState) {
        com.slideindex.app.settings.PickResultSearchGridDefaultState.ALWAYS_EXPANDED -> true
        com.slideindex.app.settings.PickResultSearchGridDefaultState.ALWAYS_COLLAPSED -> false
        com.slideindex.app.settings.PickResultSearchGridDefaultState.REMEMBER_LAST -> appSettings.floatBallPickSearchGridLastExpanded
    }
    val isImageVisible = remember(panelShowToken, textFirstPanelEnabled) {
        mutableStateOf(!textFirstPanelEnabled)
    }
    val isSearchGridVisible = remember(panelShowToken) {
        mutableStateOf(initialSearchGridExpanded)
    }
    val scopedCollapseController = remember(panelShowToken, textFirstPanelEnabled) {
        AuxiliaryCollapseController(
            initialCollapseProgress = if (textFirstPanelEnabled) 1f else 0f,
            initialSearchCollapseProgress = if (initialSearchGridExpanded) 0f else 1f
        )
    }
    SideEffect {
        scopedCollapseController.updateTotalCollapsiblePx(totalImageCollapsiblePx)
        scopedCollapseController.updateTotalSearchCollapsiblePx(totalSearchCollapsiblePx)
    }

    LaunchedEffect(isImageVisible.value, hasImageContent) {
        if (!hasImageContent || scopedCollapseController.isDragging) return@LaunchedEffect
        scopedCollapseController.setExpanded(isImageVisible.value)
    }

    LaunchedEffect(isSearchGridVisible.value, hasSearchGrid) {
        if (!hasSearchGrid || scopedCollapseController.isDragging) return@LaunchedEffect
        scopedCollapseController.setSearchExpanded(isSearchGridVisible.value)
    }

    fun applyAuxiliaryDrag(deltaPx: Float) {
        scopedCollapseController.applyDrag(deltaPx)
    }

    fun applySearchAuxiliaryDrag(deltaPx: Float) {
        scopedCollapseController.applySearchDrag(deltaPx)
    }

    fun endImageAuxiliaryDrag() {
        scopedCollapseController.endDrag { expanded ->
            isImageVisible.value = expanded
        }
    }

    fun endSearchAuxiliaryDrag() {
        scopedCollapseController.endSearchDrag { expanded ->
            isSearchGridVisible.value = expanded
        }
    }

    val overlayImeBottom = rememberOverlayImeBottomHeight()
    val hasAuxiliaryCollapse = showImageSection || hasSearchGrid
    // 高度上限按「整屏可用高度」算，键盘让位交给下游各自让一次：
    // 居中落位由本函数外层的 `padding(bottom = ime)` 让，贴底落位由 PickResultPanelLayout 内层让。
    // 真机实测（MEIZU 21，居中落位）：键盘弹出时 containerSize 不缩（一直 851dp），ime 读到 320dp；
    // 若在这里先减一次 ime，卡片只能长到 275dp，反而填不满键盘上方那块空间。
    val panelContentHeight = maxPanelHeight

    val isTabPaged = appSettings.floatBallPickPanelStyle == com.slideindex.app.settings.PickResultPanelStyle.TAB_PAGED && !landscapeDualColumn
    val showTabBar = isTabPaged && hasImageContent && showTextSection

    val panelSlideDistance = remember(
        panelContentHeight,
        hasSearchGrid,
        showImageSection,
        minImageSectionHeight,
        maxImageSectionHeight,
        textImageDividerBaseHeight,
        searchGridSectionPrefixHeight,
        expandedSearchGridContentHeight,
        idealTextBodyHeight,
        minTextBodyHeight,
        showTextSection,
        maxPanelHeight,
        landscapeDualColumn,
        showTabBar
    ) {
        if (showTextSection || showImageSection) {
            computePickResultExpandedPanelOuterHeight(
                panelContentHeight = panelContentHeight,
                hasSearchGrid = hasSearchGrid,
                hasImageContent = showImageSection,
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
        } else {
            maxPanelHeight * 0.35f
        }
    }
    LaunchedEffect(isEditMode) {
        if (!isEditMode) {
            FloatBallPickResultPanel.requestPanelFocus()
        }
    }

    // 搜索按钮长按直搜：默认引擎未设置时长按不响应，窗口形态记住上次选择。
    // 默认引擎允许是已移入隐藏池的引擎（隐藏只表示不在网格占位）。
    val pickDefaultSearchEngine = remember(
        searchEngines,
        appSettings.floatBallPickDefaultSearchEngineId,
    ) {
        SearchEngineStore.findTextEngineById(
            searchEngines,
            appSettings.floatBallPickDefaultSearchEngineId,
        )
    }
    var lastSearchLaunchFullscreen by remember(panelShowToken) {
        mutableStateOf(PickResultSearchLaunchPrefs.resolveLastFullscreen(context))
    }
    val searchQuickLaunch = if (hasSearchGrid && pickDefaultSearchEngine != null) {
        PickResultSearchQuickLaunch(
            offerFreeWindow = appSettings.freeWindowEnabled,
            initialFullscreen = lastSearchLaunchFullscreen,
            onConfirm = { fullscreen ->
                lastSearchLaunchFullscreen = fullscreen
                PickResultSearchLaunchPrefs.rememberLastFullscreen(context, fullscreen)
                onSearchQuickLaunch(fullscreen)
            },
        )
    } else {
        null
    }

    var panelBoundsInRoot by remember { mutableStateOf(ComposeRect.Zero) }
    val panelBoundsState = rememberUpdatedState(panelBoundsInRoot)

    OverlayAwareModuleTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectPickResultDismissOutsidePanelTap(
                        panelBoundsInRoot = { panelBoundsState.value },
                        onDismiss = onDismiss
                    )
                },
            contentAlignment = if (centeredPanel) Alignment.Center else Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .overlayPickPanelWidth(appSettings.floatBallPickPanelPlacement)
                    .then(
                        // 居中样式：把居中山区域抬到输入法之上，避免被键盘遮住。
                        if (centeredPanel) Modifier.padding(bottom = overlayImeBottom) else Modifier
                    )
            ) {
            PickResultPanelSlideHost(
                panelRevealed = panelRevealed,
                panelSlideDistance = panelSlideDistance,
                panelEnterAnimationMs = appSettings.floatBallPickPanelEnterAnimationMs,
                panelExitAnimationMs = appSettings.floatBallPickPanelExitAnimationMs,
                centered = centeredPanel,
                onPanelBoundsInRoot = { panelBoundsInRoot = it }
            ) { freezeCollapseAnimation ->
                PickResultCollapsePanelColumn(
                controller = scopedCollapseController,
                panelContentHeight = panelContentHeight,
                overlayImeBottom = overlayImeBottom,
                pickPanelAlpha = pickPanelAlpha,
                imageSearchVisible = imageSearchVisible,
                dismissInteraction = dismissInteraction,
                cardInteraction = cardInteraction,
                onDismiss = onDismiss,
                isEditMode = isEditMode,
                hasImageContent = showImageSection,
                hasSearchGrid = hasSearchGrid,
                hasAuxiliaryCollapse = hasAuxiliaryCollapse,
                showTextSection = showTextSection,
                minImageSectionHeight = minImageSectionHeight,
                maxImageSectionHeight = maxImageSectionHeight,
                textImageDividerBaseHeight = textImageDividerBaseHeight,
                searchGridSectionPrefixHeight = searchGridSectionPrefixHeight,
                expandedSearchGridContentHeight = expandedSearchGridContentHeight,
                idealTextBodyHeight = idealTextBodyHeight,
                minTextBodyHeight = minTextBodyHeight,
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
                onImageSectionExpandedChange = { expanded ->
                    isImageVisible.value = expanded
                    scopedCollapseController.setExpanded(expanded)
                },
                onDragEnd = ::endImageAuxiliaryDrag,
                onSearchDragEnd = ::endSearchAuxiliaryDrag,
                applyDrag = ::applyAuxiliaryDrag,
                applySearchDrag = ::applySearchAuxiliaryDrag,
                panelSearchEngines = panelSearchEngines,
                activeText = activeText,
                searchEngineGridColumns = effectiveSearchGridColumns,
                searchEngineGridRows = searchEngineGridRows,
                searchEngineShowLabels = searchEngineShowLabels,
                appSettings = appSettings,
                onSearchEngineClick = onSearchEngineClick,
                text = text,
                textMode = textMode,
                textSource = textSource,
                textSizeSp = textSizeSp,
                ocrAvailable = ocrAvailable,
                forceOcrChip = forceOcrSourceChip,
                a11yAvailable = a11yAvailable,
                ocrLoading = ocrLoading,
                isShareImageOcr = isShareImageOcr,
                barcodeResults = barcodeResults,
                showingTranslation = showingTranslation,
                translateLoading = translateLoading,
                onBackgroundOcr = onBackgroundOcr,
                onTextSourceChange = onTextSourceChange,
                onActiveTextChange = onActiveTextChange,
                onTextModeChange = onTextModeChange,
                onTextChange = onTextChange,
                onShareText = onShareText,
                onCopy = onCopy,
                onTranslate = onTranslate,
                onRemoveSpaces = onRemoveSpaces,
                onZoomText = { expanded ->
                    val visible = !expanded
                    isImageVisible.value = visible
                    if (!textFirstPanelEnabled) {
                        isSearchGridVisible.value = visible
                    }
                },
                onPinTextToScreen = onPinTextToScreen,
                onStashText = onStashText,
                textFirstPanelEnabled = textFirstPanelEnabled,
                freezeCollapseAnimation = freezeCollapseAnimation,
                landscapeDualColumn = landscapeDualColumn,
                searchQuickLaunch = searchQuickLaunch,
                onToggleSearchGrid = {
                    val next = !isSearchGridVisible.value
                    isSearchGridVisible.value = next
                    scopedCollapseController.setSearchExpanded(next)
                    coroutineScope.launch {
                        settingsRepository?.setFloatBallPickSearchGridLastExpanded(next)
                    }
                }
            )
            }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = panelNotification != null,
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { it / 2 },
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { it / 2 },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
            ) {
                androidx.compose.material3.Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        androidx.compose.material3.Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = panelNotification ?: "",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}
