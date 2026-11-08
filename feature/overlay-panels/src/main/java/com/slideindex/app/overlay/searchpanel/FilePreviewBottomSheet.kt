package com.slideindex.app.overlay.searchpanel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.slideindex.app.overlay.panels.R
import com.slideindex.app.search.files.DeviceFileEntry
import com.slideindex.app.search.files.FileTypeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val PreviewCornerRadius = 12.dp
private val SheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
private const val MIN_SCALE = 1f
private const val MAX_SCALE = 8f
private const val PREVIEW_ANIM_MS = 280

/** In-tree overlay sheet for search panel (overlay window has no Activity token for Dialog). */
@Composable
fun FilePreviewBottomSheet(
    deviceFile: DeviceFileEntry,
    visibleState: MutableTransitionState<Boolean>,
    onDismissRequest: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetInteraction = remember { MutableInteractionSource() }
    val fadeSpec = tween<Float>(PREVIEW_ANIM_MS, easing = FastOutSlowInEasing)
    val slideSpec = tween<IntOffset>(PREVIEW_ANIM_MS, easing = FastOutSlowInEasing)

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visibleState = visibleState,
            enter = fadeIn(fadeSpec),
            exit = fadeOut(fadeSpec),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismissRequest,
                    ),
            )
        }
        AnimatedVisibility(
            visibleState = visibleState,
            enter = fadeIn(fadeSpec) + slideInVertically(slideSpec) { it },
            exit = fadeOut(fadeSpec) + slideOutVertically(slideSpec) { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .clickable(
                        interactionSource = sheetInteraction,
                        indication = null,
                        onClick = {},
                    ),
                shape = SheetShape,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
            ) {
                FilePreviewSheetContent(
                    deviceFile = deviceFile,
                    onOpen = onOpen,
                    onShare = onShare,
                )
            }
        }
    }
}

@Composable
private fun FilePreviewSheetContent(
    deviceFile: DeviceFileEntry,
    onOpen: () -> Unit,
    onShare: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(),
    ) {
        Text(
            text = deviceFile.displayName,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(PreviewCornerRadius)),
        ) {
            when {
                FileTypeUtils.isPdf(deviceFile) -> PdfPreview(deviceFile)
                FileTypeUtils.isImage(deviceFile) -> ImagePreview(deviceFile)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onShare,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Share,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 4.dp),
                )
                Text(stringResource(R.string.search_panel_file_preview_share))
            }
            Button(
                onClick = onOpen,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 4.dp),
                )
                Text(stringResource(R.string.search_panel_file_preview_open))
            }
        }
    }
}

@Composable
private fun ImagePreview(deviceFile: DeviceFileEntry) {
    val context = LocalContext.current
    var bitmap by remember(deviceFile.uri) { mutableStateOf<Bitmap?>(null) }
    var loading by remember(deviceFile.uri) { mutableStateOf(true) }

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(deviceFile.uri) {
        bitmap = withContext(Dispatchers.IO) { loadScaledBitmap(context, deviceFile) }
        loading = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                    scale = newScale
                    if (newScale > MIN_SCALE) {
                        offsetX += pan.x
                        offsetY += pan.y
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            loading -> CircularProgressIndicator()
            bitmap != null -> Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = deviceFile.displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                    },
            )
            else -> Text(
                text = stringResource(
                    R.string.search_panel_file_preview_unable_to_open,
                    deviceFile.displayName,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PdfPreview(deviceFile: DeviceFileEntry) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var pages by remember(deviceFile.uri) { mutableStateOf<List<Bitmap>>(emptyList()) }
    var loading by remember(deviceFile.uri) { mutableStateOf(true) }

    val scaleState = remember { mutableFloatStateOf(1f) }
    val offsetXState = remember { mutableFloatStateOf(0f) }
    val offsetYState = remember { mutableFloatStateOf(0f) }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                if (scaleState.floatValue > MIN_SCALE) available else Offset.Zero
        }
    }

    val pageWidthPx = with(density) { 360.dp.roundToPx() }

    LaunchedEffect(deviceFile.uri) {
        pages = withContext(Dispatchers.IO) { renderPdfPages(context, deviceFile, pageWidthPx) }
        loading = false
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        when {
            loading -> CircularProgressIndicator()
            pages.isNotEmpty() -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .nestedScroll(nestedScrollConnection)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scaleState.floatValue * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                            scaleState.floatValue = newScale
                            if (newScale > MIN_SCALE) {
                                offsetXState.floatValue += pan.x
                                offsetYState.floatValue += pan.y
                            } else {
                                offsetXState.floatValue = 0f
                                offsetYState.floatValue = 0f
                            }
                        }
                    },
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scaleState.floatValue
                            scaleY = scaleState.floatValue
                            translationX = offsetXState.floatValue
                            translationY = offsetYState.floatValue
                        },
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(8.dp),
                ) {
                    itemsIndexed(pages) { _, page ->
                        Image(
                            bitmap = page.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White),
                        )
                    }
                }
            }
            else -> Text(
                text = stringResource(
                    R.string.search_panel_file_preview_unable_to_open,
                    deviceFile.displayName,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun loadScaledBitmap(context: Context, deviceFile: DeviceFileEntry): Bitmap? {
    return try {
        val targetWidth = 1080
        val sampleSize = context.contentResolver.openInputStream(deviceFile.uri)?.use { stream ->
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(stream, null, opts)
            maxOf(1, opts.outWidth / targetWidth)
        } ?: 1
        context.contentResolver.openInputStream(deviceFile.uri)?.use { stream ->
            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            BitmapFactory.decodeStream(stream, null, opts)
        }
    } catch (_: Exception) {
        null
    }
}

private fun renderPdfPages(context: Context, deviceFile: DeviceFileEntry, pageWidthPx: Int): List<Bitmap> {
    val pages = mutableListOf<Bitmap>()
    var pfd: ParcelFileDescriptor? = null
    var renderer: PdfRenderer? = null
    try {
        pfd = context.contentResolver.openFileDescriptor(deviceFile.uri, "r") ?: return emptyList()
        renderer = PdfRenderer(pfd)
        for (i in 0 until renderer.pageCount) {
            renderer.openPage(i).use { page ->
                val scale = pageWidthPx.toFloat() / page.width
                val pageHeightPx = (page.height * scale).toInt()
                val bitmap = createBitmap(pageWidthPx, pageHeightPx)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                val matrix = Matrix().apply { setScale(scale, scale) }
                page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                pages.add(bitmap)
            }
        }
    } catch (_: Exception) {
        // return whatever pages rendered so far
    } finally {
        renderer?.close()
        pfd?.close()
    }
    return pages
}
