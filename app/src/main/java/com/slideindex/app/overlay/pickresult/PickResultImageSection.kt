package com.slideindex.app.overlay.pickresult

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.settings.PickResultImageToolbarPosition
import com.slideindex.app.settings.SearchEngineConfig

@Composable
internal fun PickResultImageSection(
    screenshot: Bitmap?,
    panelImages: List<Bitmap>,
    currentImageIndex: Int,
    imageDisplaySize: PickResultImageDisplaySize,
    searchEngines: List<com.slideindex.app.settings.SearchEngineConfig>,
    showImageSearchBar: Boolean = true,
    imageToolbarPosition: PickResultImageToolbarPosition = PickResultImageToolbarPosition.LEFT,
    modifier: Modifier = Modifier,
    onSave: () -> Unit,
    onSaveLongClick: (() -> Unit)? = null,
    onShare: () -> Unit,
    onImageSearch: () -> Unit,
    onShareEngineClick: (com.slideindex.app.settings.SearchEngineConfig) -> Unit,
    onPinToScreen: () -> Unit,
    onStash: () -> Unit,
    onImageClick: () -> Unit,
    onCopyImage: (() -> Unit)? = null,
    onImageIndexChange: (Int) -> Unit,
    sectionExpanded: Boolean,
    onSectionExpandedChange: (Boolean) -> Unit
) {
    val images = panelImages.ifEmpty { listOfNotNull(screenshot) }
    Box(modifier = modifier) {
        if (images.isNotEmpty()) {
            PickResultImageSectionGallery(
                modifier = Modifier.fillMaxSize(),
                images = images,
                currentImageIndex = currentImageIndex,
                imageDisplaySize = imageDisplaySize,
                searchEngines = searchEngines,
                showImageSearchBar = showImageSearchBar,
                imageToolbarPosition = imageToolbarPosition,
                onSave = onSave,
                onSaveLongClick = onSaveLongClick,
                onShare = onShare,
                onImageSearch = onImageSearch,
                onShareEngineClick = onShareEngineClick,
                onPinToScreen = onPinToScreen,
                onStash = onStash,
                onImageClick = onImageClick,
                onCopyImage = onCopyImage,
                onImageIndexChange = onImageIndexChange,
                sectionExpanded = sectionExpanded,
                onSectionExpandedChange = onSectionExpandedChange
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PickResultImageSectionGallery(
    images: List<Bitmap>,
    currentImageIndex: Int,
    imageDisplaySize: PickResultImageDisplaySize,
    searchEngines: List<com.slideindex.app.settings.SearchEngineConfig>,
    showImageSearchBar: Boolean = true,
    imageToolbarPosition: PickResultImageToolbarPosition = PickResultImageToolbarPosition.LEFT,
    onSave: () -> Unit,
    onSaveLongClick: (() -> Unit)? = null,
    onShare: () -> Unit,
    onImageSearch: () -> Unit,
    onShareEngineClick: (com.slideindex.app.settings.SearchEngineConfig) -> Unit,
    onPinToScreen: () -> Unit,
    onStash: () -> Unit,
    onImageClick: () -> Unit,
    onCopyImage: (() -> Unit)? = null,
    onImageIndexChange: (Int) -> Unit,
    sectionExpanded: Boolean,
    onSectionExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(
        initialPage = currentImageIndex.coerceIn(0, images.lastIndex),
        pageCount = { images.size }
    )
    LaunchedEffect(currentImageIndex) {
        if (pagerState.currentPage != currentImageIndex) {
            pagerState.scrollToPage(currentImageIndex.coerceIn(0, images.lastIndex))
        }
    }
    LaunchedEffect(pagerState.settledPage) {
        if (pagerState.settledPage != currentImageIndex) {
            onImageIndexChange(pagerState.settledPage)
        }
    }
    val isDark = com.slideindex.app.ui.theme.LocalAppDarkTheme.current
    val cardBg = if (isDark) Color(0x10FFFFFF) else Color(0xB2FFFFFF)
    val cardBorder = if (isDark) Color(0x24FFFFFF) else Color(0x12000000)

    androidx.compose.material3.Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        color = cardBg,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 6.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (images.size == 1) {
                    val image = images.first()
                    Image(
                        bitmap = image.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .width(imageDisplaySize.width)
                            .height(imageDisplaySize.height)
                            .clip(RoundedCornerShape(8.dp))
                            .combinedClickable(
                                onClick = onImageClick,
                                onLongClick = onCopyImage,
                            ),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 0
                    ) { page ->
                        val image = images[page]
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = image.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .width(imageDisplaySize.width)
                                    .height(imageDisplaySize.height)
                                    .clip(RoundedCornerShape(8.dp))
                                    .combinedClickable(
                                        onClick = onImageClick,
                                        onLongClick = onCopyImage,
                                    ),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                    Text(
                        text = "${pagerState.currentPage + 1}/${images.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .background(
                                color = Color.Black.copy(alpha = 0.55f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            if (images.size > 1) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(images.size, key = { it }) { index ->
                        val selected = index == pagerState.currentPage
                        val thumb = images[index]
                        Image(
                            bitmap = thumb.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                    },
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable { onImageIndexChange(index) },
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
            if (showImageSearchBar) {
                PickResultImageSearchBar(
                    engines = searchEngines,
                    onShareEngineClick = onShareEngineClick,
                    onShare = onShare,
                    onImageSearch = onImageSearch,
                    onSave = onSave,
                    onSaveLongClick = onSaveLongClick,
                    onPinToScreen = onPinToScreen,
                    onStash = onStash,
                    compactEmbedded = true,
                    imageToolbarPosition = imageToolbarPosition,
                )
            }
        }
    }
}
