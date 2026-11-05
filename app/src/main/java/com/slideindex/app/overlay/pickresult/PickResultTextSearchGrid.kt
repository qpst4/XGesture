package com.slideindex.app.overlay.pickresult

import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.BoxWithConstraints
import com.slideindex.app.overlay.overlayIsLandscape
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.slideindex.app.settings.SearchEngineConfig
import com.slideindex.app.settings.SearchEngineStore
import com.slideindex.app.settings.SearchIconType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import android.util.LruCache
import android.content.Context

private val searchEngineBitmapCache = LruCache<String, android.graphics.Bitmap>(48)

internal fun searchEngineIconCacheKey(engine: SearchEngineConfig): String =
    "${engine.id}|${engine.iconType}|${engine.iconPath.orEmpty()}|${engine.textIcon.orEmpty()}|" +
        "${engine.iconSourcePackage.orEmpty()}|${com.slideindex.app.ui.GlobalIconStore.revision}"

internal fun invalidateSearchEngineIconCache(engineId: String) {
    val prefix = "$engineId|"
    searchEngineBitmapCache.snapshot().keys.filter { it.startsWith(prefix) }.forEach { key ->
        searchEngineBitmapCache.remove(key)
    }
}

internal fun preloadPickResultSearchEngineIcons(
    context: Context,
    engines: List<SearchEngineConfig>,
) {
    engines.forEach { engine ->
        val cacheKey = searchEngineIconCacheKey(engine)
        if (searchEngineBitmapCache.get(cacheKey) != null) return@forEach
        resolveSearchEngineBitmap(context, engine)?.let { bitmap ->
            searchEngineBitmapCache.put(cacheKey, bitmap)
        }
    }
}

private fun cachedSearchEngineBitmap(cacheKey: String): android.graphics.Bitmap? =
    searchEngineBitmapCache.get(cacheKey)

private val SearchIconSizeDefault = 40.dp

/** 文本区与搜索引擎网格之间、分隔线下方的额外间距（已归一化为对称 12dp）。 */
internal val PickResultTextSearchGridTopSpacing = 0.dp

internal fun searchGridContentHeight(rows: Int, showLabels: Boolean, columns: Int): Dp {
    val rowCount = rows.coerceIn(1, 4)
    val iconSize = searchIconSizeForColumns(columns)
    val labelHeight = if (showLabels) 18.dp else 0.dp
    val itemHeight = iconSize + labelHeight + 4.dp
    val rowGap = 10.dp * (rowCount - 1).coerceAtLeast(0)
    return itemHeight * rowCount + rowGap
}

internal fun pickResultSearchGridReservedHeight(rows: Int, showLabels: Boolean, columns: Int = 5): Dp {
    val padding = 4.dp // 4.dp top, 0.dp bottom
    val divider = 5.dp // 2.dp padding * 2 + 1.dp
    val gaps = 16.dp // 2 gaps of 8.dp each (from spacedBy)
    return padding + searchGridContentHeight(rows, showLabels, columns) + divider + gaps +
        PickResultTextSearchGridTopSpacing
}

private fun searchIconSizeForColumns(columns: Int): Dp = when {
    columns >= 7 -> 32.dp
    columns >= 6 -> 36.dp
    else -> SearchIconSizeDefault
}

/** 横屏时按可用宽度自动增加列数；竖屏保持用户设置。 */
internal fun effectiveSearchGridColumns(
    configuredColumns: Int,
    availableWidth: Dp,
    isLandscape: Boolean,
): Int {
    val base = configuredColumns.coerceIn(3, 8)
    if (!isLandscape) return base
    val minCellWidth = 44.dp
    val horizontalPadding = 24.dp
    val autoFit = ((availableWidth - horizontalPadding) / minCellWidth).toInt()
    return maxOf(base, autoFit).coerceIn(3, 8)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PickResultTextSearchGrid(
    engines: List<SearchEngineConfig>,
    query: String,
    columns: Int,
    rows: Int,
    showLabels: Boolean,
    onEngineClick: (SearchEngineConfig, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    longPressEnabled: Boolean = false,
    showPageIndicator: Boolean = false,
    pageIndicatorTopPadding: Dp = 8.dp,
    pageIndicatorBottomPadding: Dp = 6.dp,
) {
    if (engines.isEmpty()) return
    val isLandscape = overlayIsLandscape()
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val columnCount = effectiveSearchGridColumns(columns, maxWidth, isLandscape)
        val rowCount = rows.coerceIn(1, 4)
        val pageSize = columnCount * rowCount
        val pages = remember(engines, pageSize) {
            engines.chunked(pageSize)
        }
        val iconSize = searchIconSizeForColumns(columnCount)
        val pagerState = rememberPagerState(pageCount = { pages.size })
        val gridHeight = searchGridContentHeight(rowCount, showLabels, columnCount)
        val coroutineScope = rememberCoroutineScope()
        var horizontalDragAccumulator by remember { mutableFloatStateOf(0f) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 0.dp, bottom = 0.dp),
        ) {
            VerticalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(gridHeight)
                    .draggable(
                        orientation = Orientation.Horizontal,
                        state = rememberDraggableState { delta ->
                            horizontalDragAccumulator += delta
                            if (pages.size > 1) {
                                pagerState.dispatchRawDelta(-delta)
                            }
                        },
                        onDragStopped = { velocity ->
                            val pageCount = pages.size
                            if (pageCount > 1) {
                                val currentPage = pagerState.currentPage
                                val targetPage = when {
                                    velocity < -600f && currentPage < pageCount - 1 -> currentPage + 1
                                    velocity > 600f && currentPage > 0 -> currentPage - 1
                                    horizontalDragAccumulator < -80f && currentPage < pageCount - 1 -> currentPage + 1
                                    horizontalDragAccumulator > 80f && currentPage > 0 -> currentPage - 1
                                    pagerState.currentPageOffsetFraction > 0.2f && currentPage < pageCount - 1 -> currentPage + 1
                                    pagerState.currentPageOffsetFraction < -0.2f && currentPage > 0 -> currentPage - 1
                                    else -> currentPage
                                }
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(targetPage)
                                }
                            }
                            horizontalDragAccumulator = 0f
                        }
                    ),
            ) { pageIndex ->
                PickResultSearchEngineGridPage(
                    engines = pages[pageIndex],
                    query = query,
                    columnCount = columnCount,
                    showLabels = showLabels,
                    iconSize = iconSize,
                    longPressEnabled = longPressEnabled,
                    onEngineClick = onEngineClick,
                )
            }
            if (showPageIndicator && pages.size > 1) {
                PickResultSearchGridPageIndicator(
                    pageCount = pages.size,
                    currentPage = pagerState.currentPage,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = pageIndicatorTopPadding, bottom = pageIndicatorBottomPadding),
                )
            }
        }
    }
}

@Composable
private fun PickResultSearchGridPageIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val selected = index == currentPage
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .then(
                        if (selected) {
                            Modifier
                                .width(16.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(50))
                        } else {
                            Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                        },
                    )
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        },
                    ),
            )
        }
    }
}

@Composable
private fun PickResultSearchEngineGridPage(
    engines: List<SearchEngineConfig>,
    query: String,
    columnCount: Int,
    showLabels: Boolean,
    iconSize: Dp,
    longPressEnabled: Boolean,
    onEngineClick: (SearchEngineConfig, Boolean) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        engines.chunked(columnCount).forEach { rowEngines ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                rowEngines.forEach { engine ->
                    PickResultSearchEngineItem(
                        engine = engine,
                        enabled = query.isNotBlank(),
                        showLabel = showLabels,
                        iconSize = iconSize,
                        longPressEnabled = longPressEnabled,
                        onClick = { longPressTriggered ->
                            onEngineClick(engine, longPressTriggered)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(columnCount - rowEngines.size) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PickResultSearchEngineItem(
    engine: SearchEngineConfig,
    enabled: Boolean,
    showLabel: Boolean,
    iconSize: Dp,
    onClick: (longPressTriggered: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    longPressEnabled: Boolean = false,
) {
    val clickModifier = if (longPressEnabled && enabled) {
        Modifier.combinedClickable(
            onClick = { onClick(false) },
            onLongClick = { onClick(true) },
        )
    } else {
        Modifier.clickable(enabled = enabled) { onClick(false) }
    }
    Column(
        modifier = modifier.then(clickModifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SearchEngineIcon(
            engine = engine,
            modifier = Modifier.size(iconSize),
            alpha = if (enabled) 1f else 0.45f,
        )
        if (showLabel) {
            Text(
                text = engine.name,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.85f else 0.45f),
            )
        }
    }
}

private const val SearchEngineIconCornerFraction = 0.24f

@Composable
fun SearchEngineIcon(
    engine: SearchEngineConfig,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    fallbackContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val context = LocalContext.current
    val cacheKey = searchEngineIconCacheKey(engine)
    var bitmap by remember(cacheKey) {
        mutableStateOf(cachedSearchEngineBitmap(cacheKey))
    }

    LaunchedEffect(cacheKey) {
        bitmap = withContext(Dispatchers.IO) {
            resolveSearchEngineBitmap(context, engine)
        }?.also { loaded ->
            searchEngineBitmapCache.put(cacheKey, loaded)
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val shape = RoundedCornerShape(maxWidth * SearchEngineIconCornerFraction)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(backgroundColor.copy(alpha = alpha * 0.6f)),
            contentAlignment = Alignment.Center,
        ) {
            if (engine.iconType == SearchIconType.TEXT) {
                Text(
                    text = engine.textIcon?.take(2).orEmpty().ifBlank { engine.name.take(1) },
                    style = MaterialTheme.typography.titleMedium,
                    color = fallbackContentColor.copy(alpha = alpha),
                )
            } else {
                val image = bitmap
                if (image != null) {
                    Image(
                        bitmap = image.asImageBitmap(),
                        contentDescription = engine.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(shape),
                        contentScale = ContentScale.Crop,
                        alpha = alpha,
                    )
                } else {
                    Text(
                        text = engine.name.take(1),
                        style = MaterialTheme.typography.titleMedium,
                        color = fallbackContentColor.copy(alpha = alpha),
                    )
                }
            }
        }
    }
}

internal fun loadPickResultSearchEngineBitmap(
    context: Context,
    engine: SearchEngineConfig,
): android.graphics.Bitmap? {
    val cacheKey = searchEngineIconCacheKey(engine)
    cachedSearchEngineBitmap(cacheKey)?.let { return it }
    return resolveSearchEngineBitmap(context, engine)?.also { bitmap ->
        searchEngineBitmapCache.put(cacheKey, bitmap)
    }
}

internal fun resolveSearchEngineBitmap(
    context: Context,
    engine: SearchEngineConfig,
): android.graphics.Bitmap? {
    if (engine.iconType == SearchIconType.TEXT) return null
    // 「用应用图标」：实时取该应用的当前图标，图标库改了要跟着变；
    // 取不到（应用被卸载 / 换设备没装）再回退到下面 iconPath 的快照。
    engine.iconSourcePackage?.takeIf { it.isNotBlank() }?.let { sourcePackage ->
        appIconBitmap(context, sourcePackage)?.let { return it }
    }
    if (engine.iconType == SearchIconType.URI) {
        val iconPath = engine.iconPath?.takeIf { it.isNotBlank() }
        if (iconPath != null) {
            val file = File(context.filesDir, iconPath)
            if (file.isFile) {
                runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()?.let { return it }
            }
        }
    }
    val pkg = engine.targetPackage?.takeIf { it.isNotBlank() }
        ?: engine.externJumpPackage?.takeIf { it.isNotBlank() }
    if (pkg != null) {
        return appIconBitmap(context, pkg)
    }
    return null
}

/** 应用图标：先查全局图标覆盖（扩展 → 图标库），再退回系统图标。 */
private fun appIconBitmap(context: Context, packageName: String): android.graphics.Bitmap? {
    com.slideindex.app.ui.AppIconOverride.bitmap(context, packageName, 128)?.let { return it }
    return runCatching {
        context.packageManager.getApplicationIcon(packageName).toBitmap(128, 128)
    }.getOrNull()
}
