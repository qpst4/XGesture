package com.slideindex.app.overlay.screenshot

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.slideindex.app.overlay.panels.R
import kotlin.math.abs
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Pin
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.icon.MiuixIcons

@Composable
fun SmartScreenshotEditor(
    bitmap: Bitmap,
    state: SmartScreenshotState,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onPin: () -> Unit,
    onWordSegmentation: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface
    var interaction by remember { mutableStateOf<SelectionInteraction?>(null) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val density = LocalDensity.current
        val layoutDirection = LocalLayoutDirection.current
        val cutoutInsets = WindowInsets.displayCutout
        val edgeMarginPx = with(density) { 12.dp.toPx() }
        val toolbarGapPx = with(density) { 14.dp.toPx() }
        val containerSize = remember(maxWidth, maxHeight, density) {
            IntSize(
                width = with(density) { maxWidth.roundToPx() },
                height = with(density) { maxHeight.roundToPx() }
            )
        }
        val safeLeftPx = with(density) {
            cutoutInsets.getLeft(density, layoutDirection).toFloat() + edgeMarginPx
        }
        val safeTopPx = with(density) {
            cutoutInsets.getTop(density).toFloat() + edgeMarginPx
        }
        val safeRightPx = with(density) {
            cutoutInsets.getRight(density, layoutDirection).toFloat() + edgeMarginPx
        }
        val safeBottomPx = with(density) {
            cutoutInsets.getBottom(density).toFloat() + edgeMarginPx
        }
        var topToolbarSize by remember { mutableStateOf(IntSize.Zero) }
        var bottomToolbarSize by remember { mutableStateOf(IntSize.Zero) }
        val selectionRect = state.selectionRect
        val topToolbarReady = topToolbarSize != IntSize.Zero
        val bottomToolbarReady = bottomToolbarSize != IntSize.Zero
        val toolbarVisible = interaction == null
        val topToolbarEnabled = toolbarVisible && topToolbarReady
        val bottomToolbarEnabled = toolbarVisible && bottomToolbarReady
        val topToolbarOffset = calculateToolbarOffset(
            rect = selectionRect,
            toolbarSize = topToolbarSize,
            containerSize = containerSize,
            safeLeftPx = safeLeftPx,
            safeTopPx = safeTopPx,
            safeRightPx = safeRightPx,
            safeBottomPx = safeBottomPx,
            gapPx = toolbarGapPx,
            placeAbove = true
        )
        val bottomToolbarOffset = calculateToolbarOffset(
            rect = selectionRect,
            toolbarSize = bottomToolbarSize,
            containerSize = containerSize,
            safeLeftPx = safeLeftPx,
            safeTopPx = safeTopPx,
            safeRightPx = safeRightPx,
            safeBottomPx = safeBottomPx,
            gapPx = toolbarGapPx,
            placeAbove = false
        )
        val topToolbarBounds = toolbarBounds(topToolbarOffset, topToolbarSize)
        val bottomToolbarBounds = toolbarBounds(bottomToolbarOffset, bottomToolbarSize)
        val latestTopToolbarBounds = rememberUpdatedState(topToolbarBounds)
        val latestBottomToolbarBounds = rememberUpdatedState(bottomToolbarBounds)
        val dimTapSlopPx = with(density) { 8.dp.toPx() }

        Canvas(modifier = Modifier.fillMaxSize()) {
            drawImage(imageBitmap)

            val rect = state.selectionRect
            val frameStroke = 3.dp.toPx()
            val fullPath = Path().apply { addRect(Rect(Offset.Zero, size)) }
            val selectedPath = Path().apply {
                when (state.shape) {
                    ScreenshotShape.Rectangle -> addRect(rect)
                    ScreenshotShape.Oval -> addOval(rect)
                }
            }
            drawPath(
                path = Path.combine(PathOperation.Difference, fullPath, selectedPath),
                color = Color.Black.copy(alpha = 0.58f)
            )
            val ovalFramePathEffect = PathEffect.dashPathEffect(
                intervals = floatArrayOf(12.dp.toPx(), 8.dp.toPx())
            )

            when (state.shape) {
                ScreenshotShape.Rectangle -> drawRect(
                    color = primaryColor,
                    topLeft = rect.topLeft,
                    size = rect.size,
                    style = Stroke(width = frameStroke)
                )
                ScreenshotShape.Oval -> drawOval(
                    color = primaryColor,
                    topLeft = rect.topLeft,
                    size = rect.size,
                    style = Stroke(
                        width = frameStroke,
                        pathEffect = ovalFramePathEffect
                    )
                )
            }

            if (state.shape == ScreenshotShape.Oval) {
                val topLeft = rect.topLeft
                val topRight = Offset(rect.right, rect.top)
                val bottomLeft = Offset(rect.left, rect.bottom)
                val bottomRight = rect.bottomRight
                drawLine(
                    color = primaryColor,
                    start = topLeft,
                    end = topRight,
                    strokeWidth = frameStroke
                )
                drawLine(
                    color = primaryColor,
                    start = topRight,
                    end = bottomRight,
                    strokeWidth = frameStroke
                )
                drawLine(
                    color = primaryColor,
                    start = bottomRight,
                    end = bottomLeft,
                    strokeWidth = frameStroke
                )
                drawLine(
                    color = primaryColor,
                    start = bottomLeft,
                    end = topLeft,
                    strokeWidth = frameStroke
                )
            }

            val handleRadius = 8.dp.toPx()
            val handleStroke = 2.dp.toPx()
            listOf(
                rect.topLeft,
                Offset(rect.right, rect.top),
                Offset(rect.left, rect.bottom),
                rect.bottomRight
            ).forEach { handle ->
                drawCircle(primaryColor, handleRadius, handle)
                drawCircle(
                    color = surfaceColor,
                    radius = handleRadius,
                    center = handle,
                    style = Stroke(width = handleStroke)
                )
            }

            val center = rect.center
            val lineLength = 14.dp.toPx()
            val lineStroke = 2.dp.toPx()
            drawLine(
                color = primaryColor,
                start = Offset(center.x - lineLength, center.y),
                end = Offset(center.x + lineLength, center.y),
                strokeWidth = lineStroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = primaryColor,
                start = Offset(center.x, center.y - lineLength),
                end = Offset(center.x, center.y + lineLength),
                strokeWidth = lineStroke,
                cap = StrokeCap.Round
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(pass = PointerEventPass.Main)
                        interaction = resolveInteraction(
                            position = down.position,
                            rect = state.selectionRect,
                            handleRadius = 36.dp.toPx(),
                            shape = state.shape
                        )
                        val isDimTapCandidate = interaction == null &&
                                !containsSelection(state.selectionRect, state.shape, down.position) &&
                                !latestTopToolbarBounds.value.containsPoint(down.position) &&
                                !latestBottomToolbarBounds.value.containsPoint(down.position)
                        if (interaction != null || isDimTapCandidate) {
                            down.consume()
                        }
                        var last = down.position
                        var moved = false
                        var pointerCountMax = 1
                        var event = awaitPointerEvent(pass = PointerEventPass.Main)
                        while (event.changes.any { it.pressed && !it.changedToUpIgnoreConsumed() }) {
                            pointerCountMax = maxOf(pointerCountMax, event.changes.size)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.first()
                            val current = change.position
                            val delta = current - last
                            if (abs(current.x - down.position.x) > dimTapSlopPx ||
                                abs(current.y - down.position.y) > dimTapSlopPx
                            ) {
                                moved = true
                            }
                            last = current
                            val currentRect = state.selectionRect
                            val nextRect = when (val mode = interaction) {
                                SelectionInteraction.Move -> currentRect.translate(delta.x, delta.y)
                                is SelectionInteraction.Resize -> mode.resize(
                                    rect = currentRect,
                                    delta = delta,
                                    forceSquareCrop = state.forceSquareCrop,
                                    imageBounds = state.imageBounds,
                                    minSelectionSize = state.minSelectionSize
                                )
                                null -> currentRect
                            }
                            if (interaction != null) {
                                state.updateSelection(nextRect)
                                event.changes.forEach { if (it.pressed) it.consume() }
                            } else if (isDimTapCandidate) {
                                event.changes.forEach { if (it.pressed) it.consume() }
                            }
                            event = awaitPointerEvent(pass = PointerEventPass.Main)
                        }
                        if (isDimTapCandidate && pointerCountMax == 1 && !moved) {
                            onCancel()
                        }
                        interaction = null
                    }
                }
        )

        ToolbarBubble(
            modifier = Modifier
                .zIndex(1f)
                .offset {
                    IntOffset(
                        x = topToolbarOffset.x.roundToInt(),
                        y = topToolbarOffset.y.roundToInt()
                    )
                }
                .onSizeChanged { topToolbarSize = it }
                .alpha(if (topToolbarEnabled) 1f else 0f),
            surfaceColor = surfaceColor
        ) {
            ToolIconButton(
                enabled = topToolbarEnabled,
                selected = state.shape == ScreenshotShape.Rectangle,
                contentDescription = "Rectangle",
                onClick = {
                    state.updateShape(ScreenshotShape.Rectangle)
                }
            ) {
                Icon(Icons.Default.CropSquare, contentDescription = null)
            }
            ToolIconButton(
                enabled = topToolbarEnabled,
                selected = state.shape == ScreenshotShape.Oval,
                contentDescription = "Oval",
                onClick = {
                    state.updateShape(ScreenshotShape.Oval)
                }
            ) {
                Icon(Icons.Outlined.Circle, contentDescription = null)
            }
            ToolbarDivider()
            ToolIconButton(
                enabled = topToolbarEnabled,
                selected = state.forceSquareCrop,
                contentDescription = "1:1",
                onClick = {
                    state.updateForceSquareCrop(!state.forceSquareCrop)
                }
            ) {
                Text(
                    text = "1:1",
                    fontWeight = FontWeight.Bold
                )
            }
            ToolbarDivider()
            ToolIconButton(
                enabled = topToolbarEnabled,
                contentDescription = stringResource(R.string.float_ball_action_edit),
                onClick = onEdit
            ) {
                Icon(MiuixIcons.Medium.Edit, contentDescription = null)
            }
        }

        ToolbarBubble(
            modifier = Modifier
                .zIndex(1f)
                .offset {
                    IntOffset(
                        x = bottomToolbarOffset.x.roundToInt(),
                        y = bottomToolbarOffset.y.roundToInt()
                    )
                }
                .onSizeChanged { bottomToolbarSize = it }
                .alpha(if (bottomToolbarEnabled) 1f else 0f),
            surfaceColor = surfaceColor
        ) {
            ToolIconButton(
                enabled = bottomToolbarEnabled,
                contentDescription = "Save",
                onClick = onSave
            ) {
                Icon(Icons.Outlined.Save, contentDescription = null)
            }
            ToolIconButton(
                enabled = bottomToolbarEnabled,
                contentDescription = "Copy",
                onClick = onCopy
            ) {
                Icon(MiuixIcons.Medium.Copy, contentDescription = null)
            }
            ToolIconButton(
                enabled = bottomToolbarEnabled,
                contentDescription = "Share",
                onClick = onShare
            ) {
                Icon(MiuixIcons.Medium.Share, contentDescription = null)
            }
            ToolIconButton(
                enabled = bottomToolbarEnabled,
                contentDescription = "Pin",
                onClick = onPin
            ) {
                Icon(MiuixIcons.Medium.Pin, contentDescription = null)
            }
            ToolIconButton(
                enabled = bottomToolbarEnabled,
                contentDescription = stringResource(R.string.universal_copy_pick),
                onClick = onWordSegmentation
            ) {
                Icon(Icons.Outlined.TextFields, contentDescription = null)
            }
        }

    }
}

@Composable
private fun ToolbarBubble(
    modifier: Modifier = Modifier,
    surfaceColor: Color,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = surfaceColor.copy(alpha = 0.92f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            content()
        }
    }
}

@Composable
private fun ToolIconButton(
    modifier: Modifier = Modifier,
    contentDescription: String,
    enabled: Boolean = true,
    selected: Boolean = false,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val colors = if (selected) {
        IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = colorScheme.primary,
            contentColor = colorScheme.onPrimary
        )
    } else {
        IconButtonDefaults.filledTonalIconButtonColors()
    }
    FilledTonalIconButton(
        modifier = modifier.semantics {
            this.contentDescription = contentDescription
        },
        enabled = enabled,
        onClick = onClick,
        colors = colors
    ) {
        Box(contentAlignment = Alignment.Center) {
            icon()
        }
    }
}

@Composable
private fun ToolbarDivider(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(24.dp)
            .width(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.9f))
    )
}

private fun calculateToolbarOffset(
    rect: Rect,
    toolbarSize: IntSize,
    containerSize: IntSize,
    safeLeftPx: Float,
    safeTopPx: Float,
    safeRightPx: Float,
    safeBottomPx: Float,
    gapPx: Float,
    placeAbove: Boolean
): Offset {
    val toolbarWidth = toolbarSize.width.toFloat()
    val toolbarHeight = toolbarSize.height.toFloat()
    val maxX = (containerSize.width - safeRightPx - toolbarWidth).coerceAtLeast(safeLeftPx)
    val maxY = (containerSize.height - safeBottomPx - toolbarHeight).coerceAtLeast(safeTopPx)
    val x = (rect.center.x - toolbarWidth / 2f).coerceIn(safeLeftPx, maxX)
    val preferredY = if (placeAbove) {
        rect.top - toolbarHeight - gapPx
    } else {
        rect.bottom + gapPx
    }
    val y = preferredY.coerceIn(safeTopPx, maxY)
    return Offset(x, y)
}

private sealed interface SelectionInteraction {
    data object Move : SelectionInteraction

    class Resize(
        private val horizontal: HorizontalHandle,
        private val vertical: VerticalHandle
    ) : SelectionInteraction {
        fun resize(
            rect: Rect,
            delta: Offset,
            forceSquareCrop: Boolean,
            imageBounds: Rect,
            minSelectionSize: Float
        ): Rect {
            if (forceSquareCrop) {
                return resizeSquare(
                    rect = rect,
                    delta = delta,
                    imageBounds = imageBounds,
                    minSelectionSize = minSelectionSize
                )
            }
            val left = if (horizontal == HorizontalHandle.Left) rect.left + delta.x else rect.left
            val right = if (horizontal == HorizontalHandle.Right) rect.right + delta.x else rect.right
            val top = if (vertical == VerticalHandle.Top) rect.top + delta.y else rect.top
            val bottom = if (vertical == VerticalHandle.Bottom) rect.bottom + delta.y else rect.bottom
            return Rect(left, top, right, bottom)
        }

        private fun resizeSquare(
            rect: Rect,
            delta: Offset,
            imageBounds: Rect,
            minSelectionSize: Float
        ): Rect {
            val anchor = when {
                horizontal == HorizontalHandle.Left && vertical == VerticalHandle.Top -> rect.bottomRight
                horizontal == HorizontalHandle.Right && vertical == VerticalHandle.Top -> Offset(rect.left, rect.bottom)
                horizontal == HorizontalHandle.Left && vertical == VerticalHandle.Bottom -> Offset(rect.right, rect.top)
                else -> rect.topLeft
            }
            val active = when {
                horizontal == HorizontalHandle.Left && vertical == VerticalHandle.Top -> rect.topLeft + delta
                horizontal == HorizontalHandle.Right && vertical == VerticalHandle.Top -> Offset(rect.right, rect.top) + delta
                horizontal == HorizontalHandle.Left && vertical == VerticalHandle.Bottom -> Offset(rect.left, rect.bottom) + delta
                else -> rect.bottomRight + delta
            }
            val horizontalDistance = when (horizontal) {
                HorizontalHandle.Left -> (anchor.x - active.x).coerceAtLeast(0f)
                HorizontalHandle.Right -> (active.x - anchor.x).coerceAtLeast(0f)
            }
            val verticalDistance = when (vertical) {
                VerticalHandle.Top -> (anchor.y - active.y).coerceAtLeast(0f)
                VerticalHandle.Bottom -> (active.y - anchor.y).coerceAtLeast(0f)
            }
            val desiredSide = maxOf(horizontalDistance, verticalDistance)
            val maxHorizontalSide = when (horizontal) {
                HorizontalHandle.Left -> anchor.x - imageBounds.left
                HorizontalHandle.Right -> imageBounds.right - anchor.x
            }
            val maxVerticalSide = when (vertical) {
                VerticalHandle.Top -> anchor.y - imageBounds.top
                VerticalHandle.Bottom -> imageBounds.bottom - anchor.y
            }
            val maxSide = minOf(maxHorizontalSide, maxVerticalSide)
            val side = desiredSide.coerceIn(
                minimumValue = minSelectionSize,
                maximumValue = maxSide.coerceAtLeast(minSelectionSize)
            )
            val left = when (horizontal) {
                HorizontalHandle.Left -> anchor.x - side
                HorizontalHandle.Right -> anchor.x
            }
            val right = when (horizontal) {
                HorizontalHandle.Left -> anchor.x
                HorizontalHandle.Right -> anchor.x + side
            }
            val top = when (vertical) {
                VerticalHandle.Top -> anchor.y - side
                VerticalHandle.Bottom -> anchor.y
            }
            val bottom = when (vertical) {
                VerticalHandle.Top -> anchor.y
                VerticalHandle.Bottom -> anchor.y + side
            }
            return Rect(left, top, right, bottom)
        }
    }
}

private enum class HorizontalHandle { Left, Right }
private enum class VerticalHandle { Top, Bottom }

private fun resolveInteraction(
    position: Offset,
    rect: Rect,
    handleRadius: Float,
    shape: ScreenshotShape
): SelectionInteraction? {
    val handles = listOf(
        Triple(rect.topLeft, HorizontalHandle.Left, VerticalHandle.Top),
        Triple(Offset(rect.right, rect.top), HorizontalHandle.Right, VerticalHandle.Top),
        Triple(Offset(rect.left, rect.bottom), HorizontalHandle.Left, VerticalHandle.Bottom),
        Triple(rect.bottomRight, HorizontalHandle.Right, VerticalHandle.Bottom)
    )
    val hitHandle = handles.firstOrNull { (point, _, _) ->
        abs(point.x - position.x) <= handleRadius && abs(point.y - position.y) <= handleRadius
    }
    if (hitHandle != null) {
        return SelectionInteraction.Resize(hitHandle.second, hitHandle.third)
    }
    if (containsSelection(rect, shape, position)) {
        return SelectionInteraction.Move
    }
    return null
}

private fun containsSelection(
    rect: Rect,
    shape: ScreenshotShape,
    position: Offset
): Boolean {
    return when (shape) {
        ScreenshotShape.Rectangle -> rect.contains(position)
        ScreenshotShape.Oval -> rect.contains(position) && pointInsideOval(rect, position)
    }
}

private fun pointInsideOval(
    rect: Rect,
    position: Offset
): Boolean {
    val radiusX = rect.width / 2f
    val radiusY = rect.height / 2f
    if (radiusX <= 0f || radiusY <= 0f) {
        return false
    }
    val center = rect.center
    val normalizedX = (position.x - center.x) / radiusX
    val normalizedY = (position.y - center.y) / radiusY
    return normalizedX * normalizedX + normalizedY * normalizedY <= 1f
}

private fun toolbarBounds(
    offset: Offset,
    size: IntSize
): Rect? {
    if (size == IntSize.Zero) {
        return null
    }
    return Rect(offset, Size(size.width.toFloat(), size.height.toFloat()))
}

private fun Rect?.containsPoint(point: Offset): Boolean {
    return this?.contains(point) == true
}
