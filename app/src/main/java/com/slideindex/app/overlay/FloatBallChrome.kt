package com.slideindex.app.overlay

import android.view.View
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import android.content.res.Configuration
import androidx.compose.ui.viewinterop.AndroidView
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatBallPositionMode
import com.slideindex.app.settings.FloatBallSide
import com.slideindex.app.settings.FloatBallStyleType
import com.slideindex.app.ui.miuix.theme.rememberAppAccentColor
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Display 层线条与条带预览（球体与准星由 [FloatBallDisplayHost] 原生定位）。 */
@Composable
internal fun FloatBallLineChrome(
    sceneState: FloatBallSceneState,
    dragActiveSideOverrideState: MutableState<FloatBallSide?>
) {
    val settings by sceneState.settingsState
    val stripPreviewActive by sceneState.stripZonePreview
    val screenLayoutGeneration by sceneState.screenLayoutGeneration
    val lineVisible by sceneState.lineVisible
    val chromeVisible by sceneState.chromeVisible
    val dragActiveSideOverride by dragActiveSideOverrideState

    if (!chromeVisible) return

    val context = LocalContext.current
    val density = LocalDensity.current
    val resources = LocalResources.current
    val metrics = resources.displayMetrics
    val (screenWidthPx, screenHeightPx) = remember(screenLayoutGeneration) {
        FloatBallScreenMetrics.sizePx(context)
    }
    val activeSide = sceneState.resolvedActiveSide(settings, dragActiveSideOverride)
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isCustom = settings.floatBallPositionMode == FloatBallPositionMode.CUSTOM
    val lineColor = rememberAppAccentColor(settings)
        .copy(alpha = settings.floatBallLineOpacity.coerceIn(0f, 1f))

    Box(modifier = Modifier.fillMaxSize()) {
        if (lineVisible && FloatBallLayout.shouldShowLine(settings)) {
            val inactiveSide = FloatBallSide.opposite(activeSide)
            val lineRect = sceneState.lineHitRect(
                settings,
                metrics,
                inactiveSide,
                screenWidthPx,
                screenHeightPx,
                isLandscape,
            )
            Box(
                modifier = Modifier
                    .offset { IntOffset(lineRect.left, lineRect.top) }
                    .width(with(density) { lineRect.width().toDp() })
                    .height(with(density) { lineRect.height().toDp() })
            ) {
                if (stripPreviewActive) {
                    FloatBallStripZonePreviewLayer(
                        settings = settings,
                        side = inactiveSide,
                        lineColor = lineColor,
                        showEdgeLine = true
                    )
                } else {
                    FloatBallEdgeLineVisual(
                        side = inactiveSide,
                        lineColor = lineColor
                    )
                }
            }
        }

        if (stripPreviewActive && !isCustom) {
            if (FloatBallLayout.isBothLines(settings)) {
                // 两侧都是线：球侧预览也按线条触发区展示。
                val activeLineRect = sceneState.lineHitRect(
                    settings,
                    metrics,
                    activeSide,
                    screenWidthPx,
                    screenHeightPx,
                    isLandscape,
                )
                Box(
                    modifier = Modifier
                        .offset { IntOffset(activeLineRect.left, activeLineRect.top) }
                        .width(with(density) { activeLineRect.width().toDp() })
                        .height(with(density) { activeLineRect.height().toDp() })
                ) {
                    FloatBallStripZonePreviewLayer(
                        settings = settings,
                        side = activeSide,
                        lineColor = lineColor,
                        showEdgeLine = true
                    )
                }
            } else {
                val ballCenter = sceneState.dockBallCenter(
                    settings,
                    metrics,
                    activeSide,
                    screenWidthPx,
                    screenHeightPx
                )
                val ballSize = FloatBallLayout.ballSize(settings, metrics.density)
                val ballWidthDp = with(density) { ballSize.widthPx.toDp() }
                val ballHeightDp = with(density) { ballSize.heightPx.toDp() }
                val (ballLeft, ballTop) = sceneState.ballWindowTopLeft(
                    settings,
                    metrics,
                    activeSide,
                    ballCenter,
                    screenHeightPx
                )
                Box(
                    modifier = Modifier
                        .offset { IntOffset(ballLeft, ballTop) }
                        .width(ballWidthDp)
                        .height(ballHeightDp),
                    contentAlignment = when (activeSide) {
                        FloatBallSide.LEFT -> Alignment.CenterStart
                        FloatBallSide.RIGHT -> Alignment.CenterEnd
                    }
                ) {
                    FloatBallStripZonePreviewLayer(
                        settings = settings,
                        side = activeSide,
                        lineColor = lineColor,
                        showEdgeLine = false
                    )
                }
            }
        }
    }
}

@Composable
internal fun FloatBallEdgeLineVisual(
    side: FloatBallSide,
    lineColor: Color
) {
    val density = LocalDensity.current
    val lineWidth = with(density) { 4.dp }
    val outerAlignment = when (side) {
        FloatBallSide.LEFT -> Alignment.CenterStart
        FloatBallSide.RIGHT -> Alignment.CenterEnd
    }
    val roundedEdge = when (side) {
        FloatBallSide.LEFT -> RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp)
        FloatBallSide.RIGHT -> RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp)
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = outerAlignment
    ) {
        Box(
            modifier = Modifier
                .width(lineWidth)
                .fillMaxHeight()
                .clip(roundedEdge)
                .background(lineColor)
        )
    }
}

/** ???????????????????????? display ????*/
@Composable
internal fun FloatBallIdleBallChrome(
    sceneState: FloatBallSceneState,
    dragActiveSideOverrideState: MutableState<FloatBallSide?>
) {
    val settings by sceneState.settingsState
    val styleVisualGeneration by sceneState.styleVisualGeneration
    val ballDragging by sceneState.ballDragging
    val dragActiveSideOverride by dragActiveSideOverrideState
    if (ballDragging) return
    val activeSide = sceneState.resolvedActiveSide(settings, dragActiveSideOverride)
    val density = LocalDensity.current
    val metrics = LocalResources.current.displayMetrics
    if (FloatBallLayout.isBothLines(settings)) {
        // 「两侧都是线」：球侧空闲时同样呈现为边缘线，球只在拖动时浮现。
        FloatBallEdgeLineVisual(
            side = activeSide,
            lineColor = rememberAppAccentColor(settings)
                .copy(alpha = settings.floatBallLineOpacity.coerceIn(0f, 1f))
        )
        return
    }
    val ballSize = FloatBallLayout.ballSize(settings, metrics.density)
    val ballWidthDp = with(density) { ballSize.widthPx.toDp() }
    val ballHeightDp = with(density) { ballSize.heightPx.toDp() }
    val dockAlignment = when (settings.floatBallPositionMode) {
        FloatBallPositionMode.CUSTOM -> Alignment.Center
        else -> when (activeSide) {
            FloatBallSide.LEFT -> Alignment.CenterStart
            FloatBallSide.RIGHT -> Alignment.CenterEnd
        }
    }
    // 闲置隐身：窗内这份球自己跑 alpha 动画（Display 层那份由 FloatBallOverlay 的 ValueAnimator 驱动）。
    val idleHidden by sceneState.ballIdleHidden
    val ballIdle by sceneState.ballIdle
    val idleOpacity by sceneState.ballIdleOpacity
    val ballOpacity = settings.floatBallOpacity.coerceIn(0f, 1f)
    val targetBallAlpha = if (idleHidden) idleOpacity.coerceIn(0f, 1f) else ballOpacity
    val animatedBallAlpha by animateFloatAsState(
        targetValue = targetBallAlpha,
        animationSpec = tween(durationMillis = if (targetBallAlpha < ballOpacity) 200 else 120)
    )
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = dockAlignment) {
        AndroidView(
            modifier = Modifier
                .width(ballWidthDp)
                .height(ballHeightDp),
            factory = { ctx ->
                FloatBallIconView(ctx)
            },
            update = { view ->
                view.bind(
                    settings = settings,
                    activeSide = activeSide,
                    styleGeneration = styleVisualGeneration
                )
                view.setDragging(false)
                view.setAlphaOverride(animatedBallAlpha)
                view.setGifFrozen(ballIdle && settings.floatBallIdleFreezeGif)
            }
        )
    }
}

/** ????????????????????????*/
@Composable
internal fun FloatBallIdleLineChrome(sceneState: FloatBallSceneState) {
    val settings by sceneState.settingsState
    val inactiveSide = FloatBallSide.opposite(FloatBallLayout.resolvedActiveSide(settings))
    val lineColor = rememberAppAccentColor(settings)
        .copy(alpha = settings.floatBallLineOpacity.coerceIn(0f, 1f))
    FloatBallEdgeLineVisual(side = inactiveSide, lineColor = lineColor)
}

@Composable
internal fun FloatBallStripZonePreviewLayer(
    settings: AppSettings,
    side: FloatBallSide,
    lineColor: Color,
    showEdgeLine: Boolean
) {
    val density = LocalDensity.current
    val ballSize = FloatBallLayout.ballSize(settings, density.density)
    val previewWidthPx = if (showEdgeLine) {
        FloatBallLayout.lineTriggerWidthPx(settings, density.density)
    } else {
        ballSize.widthPx
    }
    val previewWidth = with(density) { previewWidthPx.toDp() }
    val outerAlignment = when (side) {
        FloatBallSide.LEFT -> Alignment.CenterStart
        FloatBallSide.RIGHT -> Alignment.CenterEnd
    }
    val roundedEdge = when (side) {
        FloatBallSide.LEFT -> RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp)
        FloatBallSide.RIGHT -> RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp)
    }
    val lineWidth = with(density) { 4.dp }
    val previewColor = lineColor.copy(alpha = (lineColor.alpha * 0.28f).coerceIn(0.08f, 0.45f))
    val lineAlignment = when (side) {
        FloatBallSide.LEFT -> Alignment.CenterStart
        FloatBallSide.RIGHT -> Alignment.CenterEnd
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = outerAlignment
    ) {
        Box(
            modifier = Modifier
                .width(previewWidth)
                .fillMaxHeight()
                .clip(roundedEdge)
                .background(previewColor),
            contentAlignment = lineAlignment
        ) {
            if (showEdgeLine) {
                Box(
                    modifier = Modifier
                        .width(lineWidth)
                        .fillMaxHeight()
                        .clip(roundedEdge)
                        .background(lineColor)
                )
            }
        }
    }
}

@Composable
internal fun FloatBallStyledVisual(
    sizeDp: androidx.compose.ui.unit.Dp,
    ballColor: Color,
    settings: AppSettings,
    isDragging: Boolean
) {
    when (settings.floatBallStyleType) {
        FloatBallStyleType.DEFAULT -> FloatBallDefaultVisual.Content(sizeDp = sizeDp, ballColor = ballColor)
        FloatBallStyleType.ANIMATED_PLANE,
        FloatBallStyleType.ANIMATED_PULSE,
        FloatBallStyleType.ANIMATED_ORBIT,
        -> FloatBallBuiltinAnimVisual(
            sizeDp = sizeDp,
            opacity = settings.floatBallOpacity,
            styleType = settings.floatBallStyleType,
            isDragging = isDragging
        )
        FloatBallStyleType.CUSTOM_IMAGE -> {
            // 「不处理」时窗口不是正方形：这里也必须按同一份几何给尺寸，否则预览/兜底图会被拉回方形。
            val density = LocalDensity.current
            val ballSize = FloatBallLayout.ballSize(settings, density.density)
            FloatBallUriVisual(
                widthDp = with(density) { ballSize.widthPx.toDp() },
                heightDp = with(density) { ballSize.heightPx.toDp() },
                opacity = settings.floatBallOpacity,
                uri = settings.floatBallCustomImageUri,
                noCrop = FloatBallLayout.usesOriginalImageGeometry(settings)
            )
        }
        FloatBallStyleType.SLIDESHOW -> FloatBallSlideshowVisual(
            sizeDp = sizeDp,
            opacity = settings.floatBallOpacity,
            uris = settings.floatBallSlideshowUris
        )
        FloatBallStyleType.GIF -> FloatBallGifVisual(
            sizeDp = sizeDp,
            opacity = settings.floatBallOpacity,
            ballColor = ballColor,
            uri = settings.floatBallGifUri,
            isDragging = isDragging
        )
    }
}

@Composable
private fun FloatBallBuiltinAnimVisual(
    sizeDp: androidx.compose.ui.unit.Dp,
    opacity: Float,
    styleType: FloatBallStyleType,
    isDragging: Boolean
) {
    val alpha = opacity.coerceIn(0f, 1f)
    if (!FloatBallBuiltinAnimCatalog.isBuiltinAnimated(styleType)) return

    key(styleType) {
        Box(modifier = Modifier.size(sizeDp)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    FloatBallBuiltinAnimView(ctx).apply {
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        this.alpha = alpha
                        setStyle(styleType)
                    }
                },
                update = { animView ->
                    animView.alpha = alpha
                    animView.setStyle(styleType)
                    animView.setPaused(isDragging)
                }
            )
        }
    }
}

@Composable
private fun FloatBallUriVisual(
    widthDp: androidx.compose.ui.unit.Dp,
    heightDp: androidx.compose.ui.unit.Dp,
    opacity: Float,
    uri: String,
    noCrop: Boolean = false
) {
    val context = LocalContext.current
    val bitmap = remember(uri) { FloatBallImageLoader.loadBitmap(context, uri) }
    // 圆形裁剪只在"圆球样式"下生效；「不处理」要的是原图原样（方角、不裁边）。
    val shape = if (noCrop) RectangleShape else CircleShape
    val contentScale = if (noCrop) ContentScale.Fit else ContentScale.Crop
    val alpha = opacity.coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .width(widthDp)
            .height(heightDp)
            .clip(shape),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        this.shape = shape
                        clip = true
                    },
                contentScale = contentScale,
                alpha = alpha
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Gray.copy(alpha = alpha * 0.5f))
            )
        }
    }
}

@Composable
private fun FloatBallSlideshowVisual(
    sizeDp: androidx.compose.ui.unit.Dp,
    opacity: Float,
    uris: List<String>
) {
    if (uris.isEmpty()) {
        FloatBallUriVisual(widthDp = sizeDp, heightDp = sizeDp, opacity = opacity, uri = "")
        return
    }
    var index by remember(uris) { mutableIntStateOf(0) }
    LaunchedEffect(uris) {
        while (true) {
            delay(3000L)
            index = (index + 1) % uris.size
        }
    }
    FloatBallUriVisual(widthDp = sizeDp, heightDp = sizeDp, opacity = opacity, uri = uris[index])
}

@Composable
private fun FloatBallGifVisual(
    sizeDp: androidx.compose.ui.unit.Dp,
    opacity: Float,
    ballColor: Color,
    uri: String,
    isDragging: Boolean
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val decodePx = with(density) { 72.dp.roundToPx().coerceAtLeast(1) }
    val alpha = opacity.coerceIn(0f, 1f)
    val readable = uri.isNotBlank() && FloatBallStyleAssetStore.canRead(context, uri)
    if (uri.isBlank()) {
        FloatBallUriVisual(widthDp = sizeDp, heightDp = sizeDp, opacity = opacity, uri = uri)
        return
    }
    if (!readable) {
        FloatBallDefaultVisual.Content(sizeDp = sizeDp, ballColor = ballColor.copy(alpha = alpha))
        return
    }

    val player = remember { FloatBallGifPlayer() }
    var sequence by remember(uri) { mutableStateOf<FloatBallGifFrameDecoder.Sequence?>(null) }
    var decodeFailed by remember(uri) { mutableStateOf(false) }
    val gifHandle = remember(uri) { mutableStateOf<FloatBallGifSequenceCache.Handle?>(null) }

    LaunchedEffect(uri) {
        decodeFailed = false
        // 和球窗口、拖拽快照共享同一份解码结果，避免每个预览都预解码整只 GIF。
        val handle = withContext(Dispatchers.IO) {
            FloatBallGifSequenceCache.acquire(context, uri, decodePx)
        }
        gifHandle.value = handle
        sequence = handle?.sequence
        if (handle != null) {
            FloatBallGifDragSnapshot.update(uri, decodePx, handle.sequence)
        } else {
            decodeFailed = true
        }
    }

    LaunchedEffect(sequence) {
        player.setSequence(sequence)
    }

    LaunchedEffect(sequence, isDragging) {
        if (sequence == null) return@LaunchedEffect
        player.setPaused(isDragging)
        if (!isDragging) {
            player.start()
        }
    }

    DisposableEffect(player, uri) {
        onDispose {
            FloatBallGifDragSnapshot.clear()
            player.release()
            gifHandle.value?.close()
            gifHandle.value = null
        }
    }

    if (decodeFailed && sequence == null) {
        FloatBallDefaultVisual.Content(sizeDp = sizeDp, ballColor = ballColor.copy(alpha = alpha))
        return
    }

    Box(
        modifier = Modifier.size(sizeDp)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                FloatBallGifView(ctx).apply {
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setFrameAlpha(alpha)
                    player.attach(this)
                }
            },
            update = { gifView ->
                gifView.setFrameAlpha(alpha)
            }
        )
    }
}
