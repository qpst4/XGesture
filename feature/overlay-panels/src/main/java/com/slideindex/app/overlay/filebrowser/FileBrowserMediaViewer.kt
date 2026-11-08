package com.slideindex.app.overlay.filebrowser

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.util.LruCache
import android.util.Size
import android.widget.ImageView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.slideindex.app.overlay.panels.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val mediaViewerBitmapCache = object : LruCache<String, Bitmap>(
    (Runtime.getRuntime().maxMemory() / 1024L / 6L).toInt().coerceAtLeast(8 * 1024),
) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
}

/**
 * 相册式媒体查看器：
 * - 单图模式：横向 Pager + 底部缩略图条 + 动作栏；
 * - 连看模式：竖向 LazyColumn 浏览当前列表；
 * - 点击缩略图定位，分享/详情在顶栏，编辑/钉图/取词/搜索在底栏。
 */
@Composable
fun FileBrowserMediaViewer(
    items: List<FileBrowserMediaItem>,
    initialItem: FileBrowserMediaItem,
    onClose: () -> Unit,
    onShare: (FileBrowserMediaItem) -> Unit,
    onEdit: (Bitmap) -> Unit,
    onPin: (Bitmap) -> Unit,
    onOcr: (Bitmap) -> Unit,
    onSearch: (Bitmap) -> Unit,
    onDelete: (FileBrowserMediaItem) -> Unit,
) {
    if (items.isEmpty()) return
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenMaxEdge = with(density) {
        maxOf(configuration.screenWidthDp, configuration.screenHeightDp).dp.roundToPx()
    }
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    val initialIndex = items.indexOfFirst { it.id == initialItem.id }.takeIf { it >= 0 } ?: 0
    val pagerState = rememberPagerState(initialPage = initialIndex) { items.size }
    val stripState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var continuous by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf<FileBrowserMediaItem?>(null) }
    var deleteTarget by remember { mutableStateOf<FileBrowserMediaItem?>(null) }
    var currentBitmap by remember { mutableStateOf<Pair<Int, Bitmap>?>(null) }
    var zoomed by remember { mutableStateOf(false) }
    var resetToken by remember { mutableIntStateOf(0) }
    val currentIndex = pagerState.currentPage.coerceIn(items.indices)
    val current = items[currentIndex]
    val actionBitmap = currentBitmap?.takeIf { it.first == currentIndex }?.second

    LaunchedEffect(currentIndex) {
        zoomed = false
        stripState.animateScrollToItem(currentIndex)
    }
    LaunchedEffect(current.uri, screenMaxEdge) {
        // 预取前后各一张，切换时尽量命中缓存。
        val neighbors = listOfNotNull(
            items.getOrNull(currentIndex - 1),
            items.getOrNull(currentIndex + 1),
        )
        withContext(Dispatchers.IO) {
            neighbors.forEach { item ->
                loadMediaBitmap(context, item, screenMaxEdge)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (continuous) {
            Column(modifier = Modifier.fillMaxSize()) {
                MediaViewerTopBar(
                    title = "${currentIndex + 1}/${items.size}",
                    subtitle = current.name,
                    onBack = onClose,
                    onShare = { onShare(current) },
                    onDetails = { details = current },
                    onDelete = { deleteTarget = current },
                )
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 8.dp),
                ) {
                    itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                        ContinuousMediaImage(
                            item = item,
                            targetWidthPx = screenWidthPx,
                            onClick = {
                                continuous = false
                                scope.launch { pagerState.scrollToPage(index) }
                            },
                        )
                    }
                }
                MediaViewerActionRow(
                    enabled = false,
                    continuous = true,
                    onEdit = {},
                    onPin = {},
                    onOcr = {},
                    onSearch = {},
                    onFit = {},
                    onToggleContinuous = { continuous = false },
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                MediaViewerTopBar(
                    title = "${currentIndex + 1}/${items.size}",
                    subtitle = current.name,
                    onBack = onClose,
                    onShare = { onShare(current) },
                    onDetails = { details = current },
                    onDelete = { deleteTarget = current },
                )
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    beyondViewportPageCount = 1,
                    userScrollEnabled = !zoomed,
                ) { page ->
                    val item = items[page]
                    MediaPagerPage(
                        item = item,
                        pageIndex = page,
                        isCurrent = page == currentIndex,
                        maxEdge = screenMaxEdge,
                        resetToken = if (page == currentIndex) resetToken else 0,
                        onLoaded = { index, bitmap ->
                            currentBitmap = index to bitmap
                        },
                        onZoomChanged = { value ->
                            if (page == currentIndex) zoomed = value
                        },
                        onOpenExternally = { onShare(item) },
                    )
                }
                MediaFilmstrip(
                    items = items,
                    selectedIndex = currentIndex,
                    stripState = stripState,
                    onSelect = { index ->
                        scope.launch { pagerState.animateScrollToPage(index) }
                    },
                )
                MediaViewerActionRow(
                    enabled = true,
                    continuous = false,
                    onEdit = { actionBitmap?.let(onEdit) },
                    onPin = { actionBitmap?.let(onPin) },
                    onOcr = { actionBitmap?.let(onOcr) },
                    onSearch = { actionBitmap?.let(onSearch) },
                    onFit = {
                        zoomed = false
                        resetToken++
                    },
                    onToggleContinuous = { continuous = true },
                )
            }
        }
        details?.let { item ->
            MediaDetailsDialog(item = item, onDismiss = { details = null })
        }
        deleteTarget?.let { item ->
            MediaDeleteConfirmDialog(
                item = item,
                onDismiss = { deleteTarget = null },
                onConfirm = {
                    deleteTarget = null
                    onDelete(item)
                    onClose()
                },
            )
        }
    }
}

@Composable
private fun MediaPagerPage(
    item: FileBrowserMediaItem,
    pageIndex: Int,
    isCurrent: Boolean,
    maxEdge: Int,
    resetToken: Int,
    onLoaded: (Int, Bitmap) -> Unit,
    onZoomChanged: (Boolean) -> Unit,
    onOpenExternally: () -> Unit,
) {
    val context = LocalContext.current
    var bitmap by remember(item.uri) { mutableStateOf<Bitmap?>(null) }
    var animated by remember(item.uri) { mutableStateOf<AnimatedImageDrawable?>(null) }
    var failed by remember(item.uri) { mutableStateOf(false) }
    var showLoading by remember(item.uri) { mutableStateOf(false) }
    var scale by remember(item.uri) { mutableFloatStateOf(1f) }
    var offset by remember(item.uri) { mutableStateOf(Offset.Zero) }
    LaunchedEffect(item.uri, maxEdge) {
        failed = false
        showLoading = false
        animated = null
        bitmap = null
        delay(160)
        showLoading = true
        if (isGif(item)) {
            animated = withContext(Dispatchers.IO) {
                loadAnimatedGif(context, item)
            }
        }
        bitmap = withContext(Dispatchers.IO) {
            loadMediaBitmap(context, item, maxEdge)
        }
        showLoading = false
        failed = bitmap == null && animated == null
    }
    DisposableEffect(animated) {
        animated?.start()
        onDispose {
            animated?.stop()
        }
    }
    LaunchedEffect(bitmap, isCurrent, pageIndex) {
        val loaded = bitmap
        if (isCurrent && loaded != null) onLoaded(pageIndex, loaded)
    }
    LaunchedEffect(resetToken) {
        scale = 1f
        offset = Offset.Zero
        onZoomChanged(false)
    }
    val imageModifier = Modifier
        .fillMaxSize()
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            translationX = offset.x
            translationY = offset.y
        }
        .pointerInput(item.uri, resetToken) {
            awaitEachGesture {
                val first = awaitFirstDown(requireUnconsumed = false)
                var lastPosition = first.position
                var pinchStarted = false
                while (true) {
                    val event = awaitPointerEvent()
                    val pressed = event.changes.filter { it.pressed }
                    if (pressed.isEmpty()) break
                    if (pressed.size >= 2) {
                        pinchStarted = true
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        if (zoom != 1f || pan != Offset.Zero) {
                            scale = (scale * zoom).coerceIn(0.5f, 6f)
                            offset += pan
                            onZoomChanged(scale > 1.01f)
                            event.changes.forEach { it.consume() }
                        }
                    } else if (pinchStarted && scale > 1.01f) {
                        val change = pressed.first()
                        offset += change.position - lastPosition
                        change.consume()
                    }
                    lastPosition = pressed.first().position
                }
            }
        }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.file_browser_media_image_failed),
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onOpenExternally) {
                    Text(stringResource(R.string.file_browser_share))
                }
            }
            animated != null -> AndroidView(
                factory = { ctx ->
                    ImageView(ctx).apply {
                        scaleType = ImageView.ScaleType.FIT_CENTER
                    }
                },
                update = { view ->
                    view.setImageDrawable(animated)
                },
                modifier = imageModifier,
            )
            bitmap == null && showLoading -> CircularProgressIndicator(modifier = Modifier.size(28.dp))
            bitmap == null -> Box(modifier = Modifier.fillMaxSize())
            else -> Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = item.name,
                contentScale = ContentScale.Fit,
                modifier = imageModifier,
            )
        }
    }
}

@Composable
private fun ContinuousMediaImage(
    item: FileBrowserMediaItem,
    targetWidthPx: Int,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    var bitmap by remember(item.uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(item.uri, targetWidthPx) {
        bitmap = withContext(Dispatchers.IO) {
            loadMediaBitmap(context, item, targetWidthPx)
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val loaded = bitmap
        if (loaded != null) {
            Image(
                bitmap = loaded.asImageBitmap(),
                contentDescription = item.name,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun MediaFilmstrip(
    items: List<FileBrowserMediaItem>,
    selectedIndex: Int,
    stripState: androidx.compose.foundation.lazy.LazyListState,
    onSelect: (Int) -> Unit,
) {
    LazyRow(
        state = stripState,
        modifier = Modifier.fillMaxWidth().height(76.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.10f))
                    .then(
                        if (selected) {
                            Modifier.border(
                                width = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(6.dp),
                            )
                        } else {
                            Modifier
                        },
                    )
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                MediaThumbnail(item = item)
            }
        }
    }
}

@Composable
private fun MediaThumbnail(item: FileBrowserMediaItem) {
    val context = LocalContext.current
    var bitmap by remember(item.uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(item.uri) {
        bitmap = FileBrowserMediaThumbnailLoader.load(context, item.uri)
    }
    val loaded = bitmap
    if (loaded != null) {
        Image(
            bitmap = loaded.asImageBitmap(),
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        Icon(
            imageVector = Icons.Filled.Info,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun MediaViewerTopBar(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onDetails: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.66f))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.file_browser_close),
                tint = Color.White,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.68f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onShare) {
            Icon(
                imageVector = Icons.Filled.Share,
                contentDescription = stringResource(R.string.file_browser_share),
                tint = Color.White,
            )
        }
        IconButton(onClick = onDetails) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = stringResource(R.string.file_browser_media_details),
                tint = Color.White,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.file_browser_delete),
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun MediaViewerActionRow(
    enabled: Boolean,
    continuous: Boolean,
    onEdit: () -> Unit,
    onPin: () -> Unit,
    onOcr: () -> Unit,
    onSearch: () -> Unit,
    onFit: () -> Unit,
    onToggleContinuous: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.72f))
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ViewerAction(Icons.Filled.Edit, R.string.file_browser_media_edit, enabled, onEdit)
        ViewerAction(Icons.Filled.PushPin, R.string.file_browser_media_pin, enabled, onPin)
        ViewerAction(Icons.Filled.TextSnippet, R.string.file_browser_media_ocr, enabled, onOcr)
        ViewerAction(Icons.Filled.Search, R.string.file_browser_media_search, enabled, onSearch)
        ViewerAction(Icons.Filled.FitScreen, R.string.file_browser_media_fit, enabled, onFit)
        ViewerAction(
            if (continuous) Icons.Filled.CropSquare else Icons.Filled.ViewAgenda,
            if (continuous) R.string.file_browser_media_single
            else R.string.file_browser_media_continuous,
            true,
            onToggleContinuous,
        )
    }
}

@Composable
private fun ViewerAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    labelRes: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(52.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = stringResource(labelRes),
            tint = if (enabled) Color.White else Color.White.copy(alpha = 0.32f),
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = stringResource(labelRes),
            color = if (enabled) Color.White.copy(alpha = 0.86f)
            else Color.White.copy(alpha = 0.32f),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

@Composable
private fun MediaDetailsDialog(
    item: FileBrowserMediaItem,
    onDismiss: () -> Unit,
) {
    MediaDialogShell(onDismiss = onDismiss) {
        Text(
            text = stringResource(R.string.file_browser_media_details),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "${stringResource(R.string.file_browser_media_details_name)}：${item.name}",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = "${stringResource(R.string.file_browser_media_details_path)}：${item.relativePath.orEmpty()}",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = "${stringResource(R.string.file_browser_media_details_mime)}：${item.mimeType.orEmpty()}",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = "${stringResource(R.string.file_browser_media_details_size)}：${FileBrowserFormat.size(item.sizeBytes)}",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onDismiss) {
            Text(stringResource(R.string.file_browser_close))
        }
    }
}

@Composable
private fun MediaDeleteConfirmDialog(
    item: FileBrowserMediaItem,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    MediaDialogShell(onDismiss = onDismiss) {
        Text(
            text = stringResource(R.string.file_browser_delete),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.file_browser_cancel))
            }
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.file_browser_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun MediaDialogShell(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.48f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
            ) {
                content()
            }
        }
    }
}

private fun isGif(item: FileBrowserMediaItem): Boolean =
    item.mimeType.equals("image/gif", ignoreCase = true) ||
        item.name.endsWith(".gif", ignoreCase = true)

private fun loadAnimatedGif(
    context: android.content.Context,
    item: FileBrowserMediaItem,
): AnimatedImageDrawable? = runCatching {
    val source = ImageDecoder.createSource(context.contentResolver, item.uri)
    (ImageDecoder.decodeDrawable(source) as? AnimatedImageDrawable)?.apply {
        repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
    }
}.getOrNull()

private suspend fun loadMediaBitmap(
    context: android.content.Context,
    item: FileBrowserMediaItem,
    maxEdge: Int,
): Bitmap? = withContext(Dispatchers.IO) {
    val edge = maxEdge.coerceAtLeast(320)
    val key = "${item.uri}@$edge"
    mediaViewerBitmapCache.get(key)?.let { return@withContext it }
    val resolver = context.contentResolver
    val decoded = runCatching {
        val source = ImageDecoder.createSource(resolver, item.uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > edge) {
                val scale = edge.toFloat() / longest
                decoder.setTargetSize(
                    (info.size.width * scale).toInt().coerceAtLeast(1),
                    (info.size.height * scale).toInt().coerceAtLeast(1),
                )
            }
        }
    }.getOrNull() ?: runCatching {
        resolver.loadThumbnail(item.uri, Size(edge, edge), null)
    }.getOrNull()
    if (decoded != null) mediaViewerBitmapCache.put(key, decoded)
    decoded
}
