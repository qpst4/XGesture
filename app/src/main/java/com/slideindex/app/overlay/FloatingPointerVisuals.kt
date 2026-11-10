package com.slideindex.app.overlay

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatingPointerDesign
import com.slideindex.app.settings.FloatingPointerTrailType
import kotlin.math.max
import kotlin.math.roundToInt

internal data class FloatingPointerTrailPoint(
    val x: Float,
    val y: Float,
    val timeMs: Long
)

/** QC `t51` / `b60` samples shared by recorder, replay, and trail drawing. */
internal data class GestureRecorderTrailPoint(
    val x: Int,
    val y: Int,
    val durationMs: Long
)

internal fun DrawScope.drawFloatingPointerAreaPreview(
    layout: FloatingPointerBounds.AreaPreviewLayout,
    settings: AppSettings,
    screenWidth: Float,
    screenHeight: Float
) {
    // 全部描边都用"深色底描边 + 亮色面描边"两层：
    // 单层半透明白线在浅色页面上几乎看不见（旧实现在白底上就是一层淡白雾）。
    drawRoundRect(
        color = Color.White.copy(alpha = 0.06f),
        topLeft = Offset.Zero,
        size = Size(screenWidth, screenHeight),
        cornerRadius = CornerRadius(0f, 0f)
    )
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.35f),
        topLeft = Offset.Zero,
        size = Size(screenWidth, screenHeight),
        cornerRadius = CornerRadius(0f, 0f),
        style = Stroke(width = 4f)
    )
    drawRoundRect(
        color = Color.White.copy(alpha = 0.35f),
        topLeft = Offset.Zero,
        size = Size(screenWidth, screenHeight),
        cornerRadius = CornerRadius(0f, 0f),
        style = Stroke(width = 1.5f)
    )

    val area = layout.travelRect
    if (area.width > 0f && area.height > 0f) {
        val dash = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
        drawRoundRect(
            color = Color.White.copy(alpha = 0.20f),
            topLeft = area.topLeft,
            size = area.size,
            cornerRadius = CornerRadius(8f, 8f)
        )
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.45f),
            topLeft = area.topLeft,
            size = area.size,
            cornerRadius = CornerRadius(8f, 8f),
            style = Stroke(width = 4f, pathEffect = dash)
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.92f),
            topLeft = area.topLeft,
            size = area.size,
            cornerRadius = CornerRadius(8f, 8f),
            style = Stroke(width = 1.8f, pathEffect = dash)
        )
    }
    val onScreen = layout.travelRectOnScreen
    if (onScreen.width > 0f && onScreen.height > 0f) {
        drawRoundRect(
            color = Color.White.copy(alpha = 0.38f),
            topLeft = onScreen.topLeft,
            size = onScreen.size,
            cornerRadius = CornerRadius(8f, 8f)
        )
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.40f),
            topLeft = onScreen.topLeft,
            size = onScreen.size,
            cornerRadius = CornerRadius(8f, 8f),
            style = Stroke(width = 4.5f)
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 1f),
            topLeft = onScreen.topLeft,
            size = onScreen.size,
            cornerRadius = CornerRadius(8f, 8f),
            style = Stroke(width = 2.2f)
        )
    }

    val pointerRadius = (settings.floatingPointerPointerDiameterPx / 2f)
        .coerceAtMost(screenWidth * 0.05f)
    drawCircle(
        color = Color.White.copy(alpha = 0.35f),
        radius = pointerRadius,
        center = layout.pointerPosition,
        style = Stroke(width = 2.5f)
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.92f),
        radius = 5f,
        center = layout.pointerPosition
    )

    drawQcJoystickDisc(
        center = layout.joystickCenter,
        radiusPx = layout.joystickRadiusPx,
        innerColor = Color(settings.floatingPointerJoystickInnerColorArgb),
        outerColor = Color(settings.floatingPointerJoystickOuterColorArgb),
        gradientRadiusFraction = settings.floatingPointerJoystickGradientRadiusFraction,
        pressed = false
    )

    drawCircle(
        color = Color(0xFFFF5252),
        radius = 9f,
        center = layout.trigger
    )
    drawCircle(
        color = Color.White,
        radius = 4f,
        center = layout.trigger
    )
}

@Composable
fun FloatingPointerJoystickPreview(
    settings: AppSettings,
    modifier: Modifier = Modifier,
    pressed: Boolean = false
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
    ) {
        val radius = settings.floatingPointerJoystickDiameterPx / 2f
        drawQcJoystickDisc(
            center = Offset(size.width / 2f, size.height / 2f),
            radiusPx = radius.coerceAtMost(size.minDimension / 2f - 8f),
            innerColor = Color(settings.floatingPointerJoystickInnerColorArgb),
            outerColor = Color(settings.floatingPointerJoystickOuterColorArgb),
            gradientRadiusFraction = settings.floatingPointerJoystickGradientRadiusFraction,
            pressed = pressed
        )
    }
}

@Composable
fun FloatingPointerRingPreview(
    settings: AppSettings,
    modifier: Modifier = Modifier
) = FloatingPointerDesignPreview(settings = settings, modifier = modifier)

@Composable
fun FloatingPointerDesignPreview(
    settings: AppSettings,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val design = FloatingPointerDesign.fromId(settings.floatingPointerDesignId)
    val bitmap = rememberFloatingPointerDesignBitmap(
        context = context,
        design = design,
        sizePx = settings.floatingPointerPointerDiameterPx.roundToInt().coerceAtLeast(1)
    )
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp)
    ) {
        val diameter = settings.floatingPointerPointerDiameterPx.coerceAtMost(size.minDimension - 16f)
        drawFloatingPointer(
            center = Offset(size.width / 2f, size.height / 2f),
            settings = settings,
            design = design,
            bitmap = bitmap,
            sizePx = diameter
        )
    }
}

@Composable
fun rememberFloatingPointerDesignBitmap(
    context: Context,
    design: FloatingPointerDesign,
    sizePx: Int
): ImageBitmap? = remember(design.id, sizePx) {
    if (!design.isBitmap) return@remember null
    renderFloatingPointerDesignBitmap(context, design, sizePx)
}

internal fun renderFloatingPointerDesignBitmap(
    context: Context,
    design: FloatingPointerDesign,
    sizePx: Int
): ImageBitmap? {
    if (!design.isBitmap) return null
    val drawable = ContextCompat.getDrawable(context, design.drawableResId) ?: return null
    val intrinsicW = drawable.intrinsicWidth.coerceAtLeast(1)
    val intrinsicH = drawable.intrinsicHeight.coerceAtLeast(1)
    val aspect = intrinsicW.toFloat() / intrinsicH
    val (widthPx, heightPx) = floatingPointerBitmapDimensions(aspect, sizePx.toFloat())
    val width = widthPx.roundToInt().coerceAtLeast(1)
    val height = heightPx.roundToInt().coerceAtLeast(1)
    val bitmap = createBitmap(width, height)
    val canvas = android.graphics.Canvas(bitmap)
    drawable.setBounds(0, 0, width, height)
    drawable.draw(canvas)
    return bitmap.asImageBitmap()
}

internal fun floatingPointerBitmapDimensions(
    aspectRatio: Float,
    sizePx: Float
): Pair<Float, Float> = if (aspectRatio > 1f) {
    sizePx to sizePx / aspectRatio
} else {
    sizePx * aspectRatio to sizePx
}

internal fun DrawScope.drawFloatingPointer(
    center: Offset,
    settings: AppSettings,
    design: FloatingPointerDesign = FloatingPointerDesign.fromId(settings.floatingPointerDesignId),
    bitmap: ImageBitmap? = null,
    sizePx: Float = settings.floatingPointerPointerDiameterPx,
    visibilityAlpha: Float = 1f,
    sizeScale: Float = 1f,
    clickProgress: Float = 0f
) {
    if (design.isRing) {
        drawQcRingPointer(
            center = center,
            diameterPx = sizePx,
            ringThicknessPx = settings.floatingPointerRingThicknessPx,
            dotDiameterPx = settings.floatingPointerDotDiameterPx,
            ringColor = Color(settings.floatingPointerRingColorArgb),
            fillColor = Color(settings.floatingPointerFillColorArgb),
            dotColor = Color(settings.floatingPointerDotColorArgb),
            visibilityAlpha = visibilityAlpha,
            sizeScale = sizeScale,
            clickProgress = clickProgress
        )
        return
    }
    val image = bitmap ?: return
    val aspect = image.width.toFloat() / image.height.coerceAtLeast(1)
    val (widthPx, heightPx) = floatingPointerBitmapDimensions(aspect, sizePx * sizeScale)
    drawQcBitmapPointer(
        image = image,
        center = center,
        widthPx = widthPx,
        heightPx = heightPx,
        tipXFraction = design.tipXFraction,
        tipYFraction = design.tipYFraction,
        visibilityAlpha = visibilityAlpha
    )
    if (clickProgress > 0.001f) {
        drawQcPointerClickRing(
            center = center,
            outerRadius = max(widthPx, heightPx) / 2f,
            dotDiameterPx = settings.floatingPointerDotDiameterPx,
            ringColor = Color(settings.floatingPointerRingColorArgb),
            highlightColor = Color(settings.floatingPointerDotColorArgb),
            visibilityAlpha = visibilityAlpha,
            sizeScale = sizeScale,
            clickProgress = clickProgress
        )
    }
}

fun DrawScope.drawQcBitmapPointer(
    image: ImageBitmap,
    center: Offset,
    widthPx: Float,
    heightPx: Float,
    tipXFraction: Float,
    tipYFraction: Float,
    visibilityAlpha: Float = 1f
) {
    if (visibilityAlpha <= 0.001f) return
    val topLeft = Offset(
        center.x - widthPx * tipXFraction,
        center.y - heightPx * tipYFraction
    )
    drawImage(
        image = image,
        dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
        dstSize = IntSize(
            widthPx.roundToInt().coerceAtLeast(1),
            heightPx.roundToInt().coerceAtLeast(1)
        ),
        alpha = visibilityAlpha.coerceIn(0f, 1f)
    )
}

@Composable
fun FloatingPointerRadialMenuPreview(
    settings: AppSettings,
    slots: List<com.slideindex.app.gesture.GestureAction>,
    modifier: Modifier = Modifier,
    highlightedSlot: Int = -1
) {
    val context = LocalContext.current
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
    ) {
        val maxOuter = settings.floatingPointerRadialOuterDiameterPx / 2f
        val scale = (size.minDimension / 2f - 12f) / maxOuter.coerceAtLeast(1f)
        val outer = settings.floatingPointerRadialOuterDiameterPx * scale
        val inner = settings.floatingPointerRadialInnerDiameterPx * scale
        val previewSettings = settings.copy(
            floatingPointer = settings.floatingPointer.copy(
                floatingPointerRadialOuterDiameterPx = outer,
                floatingPointerRadialInnerDiameterPx = inner,
                floatingPointerRadialDividerThicknessPx = settings.floatingPointerRadialDividerThicknessPx * scale
            )
        )
        drawQcJoystickDisc(
            center = Offset(size.width / 2f, size.height / 2f),
            radiusPx = (settings.floatingPointerJoystickDiameterPx / 2f * scale)
                .coerceAtMost(inner / 2f - 4f),
            innerColor = Color(settings.floatingPointerJoystickInnerColorArgb),
            outerColor = Color(settings.floatingPointerJoystickOuterColorArgb),
            gradientRadiusFraction = settings.floatingPointerJoystickGradientRadiusFraction,
            pressed = true
        )
        drawFloatingPointerRadialMenu(
            context = context,
            center = Offset(size.width / 2f, size.height / 2f),
            settings = previewSettings,
            slots = slots,
            highlightedSlot = highlightedSlot
        )
    }
}

fun DrawScope.drawQcJoystickDisc(
    center: Offset,
    radiusPx: Float,
    innerColor: Color,
    outerColor: Color,
    gradientRadiusFraction: Float,
    pressed: Boolean
) {
    val alphaScale = if (pressed) 1f else 0.94f
    val gradientStop = gradientRadiusFraction.coerceIn(0.5f, 1f)
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to innerColor.copy(alpha = innerColor.alpha * alphaScale),
                gradientStop to outerColor.copy(alpha = outerColor.alpha * alphaScale),
                1f to outerColor.copy(alpha = outerColor.alpha * alphaScale * 0.85f)
            ),
            center = center,
            radius = radiusPx
        ),
        radius = radiusPx,
        center = center
    )
}

fun DrawScope.drawQcRingPointer(
    center: Offset,
    diameterPx: Float,
    ringThicknessPx: Float,
    dotDiameterPx: Float,
    ringColor: Color,
    fillColor: Color,
    dotColor: Color,
    visibilityAlpha: Float = 1f,
    sizeScale: Float = 1f,
    clickProgress: Float = 0f
) {
    if (visibilityAlpha <= 0.001f || sizeScale <= 0.001f) return
    val alpha = visibilityAlpha.coerceIn(0f, 1f)
    val click = clickProgress.coerceIn(0f, 1f)

    val outerRadius = (diameterPx / 2f) * sizeScale
    val dotRadius = (dotDiameterPx / 2f) * sizeScale
    val innerRingRadius = (outerRadius - ringThicknessPx * sizeScale).coerceAtLeast(dotRadius)

    if (innerRingRadius > dotRadius) {
        drawAnnulus(
            center = center,
            outerRadius = innerRingRadius,
            innerRadius = dotRadius,
            color = fillColor.copy(alpha = fillColor.alpha * alpha)
        )
    }

    if (outerRadius > innerRingRadius) {
        drawAnnulus(
            center = center,
            outerRadius = outerRadius,
            innerRadius = innerRingRadius,
            color = ringColor.copy(alpha = ringColor.alpha * alpha)
        )
    }

    // QC keeps the base fill layers visible; thin outline strokes are omitted during click.
    if (click <= 0.001f) {
        drawCircle(
            color = ringColor.copy(alpha = ringColor.alpha * 0.9f * alpha),
            radius = outerRadius,
            center = center,
            style = Stroke(width = 1.4f * sizeScale)
        )
        drawCircle(
            color = ringColor.copy(alpha = ringColor.alpha * 0.75f * alpha),
            radius = innerRingRadius,
            center = center,
            style = Stroke(width = 1.1f * sizeScale)
        )
        drawCircle(
            color = dotColor.copy(alpha = dotColor.alpha * alpha),
            radius = dotRadius,
            center = center
        )
    }

    drawQcPointerClickRing(
        center = center,
        outerRadius = outerRadius,
        dotDiameterPx = dotDiameterPx,
        ringColor = ringColor,
        highlightColor = dotColor,
        visibilityAlpha = alpha,
        sizeScale = sizeScale,
        clickProgress = click
    )
}

/**
 * QC [CursorDesignQuickCursorDrawable.n]: outer fill + ring stroke shrink away while the center
 * becomes a small gesture-recorder dot ([recorderColor]).
 */
fun DrawScope.drawQcGestureRecorderPointer(
    center: Offset,
    settings: AppSettings,
    recorderColor: Color,
    recorderProgress: Float,
    ringColor: Color,
    fillColor: Color,
    dotColor: Color,
    visibilityAlpha: Float = 1f,
    sizeScale: Float = 1f
) {
    val alpha = visibilityAlpha.coerceIn(0f, 1f)
    if (alpha <= 0.001f || sizeScale <= 0.001f) return

    val enterProgress = recorderProgress.coerceIn(0f, 1f)
    // QC inverts the accelerate color animator: rings are full at start, gone at end.
    val shrink = 1f - enterProgress
    val m = sizeScale
    val p = settings.floatingPointerPointerDiameterPx / 2f
    val u = settings.floatingPointerDotDiameterPx / 2f
    val v = u / 2f

    val fillRadius = m * shrink * ((p - v) - 1f)
    if (fillRadius > 0.5f) {
        drawCircle(
            color = fillColor.copy(alpha = fillColor.alpha * alpha),
            radius = fillRadius,
            center = center
        )
    }

    val ringRadius = m * shrink * (p - v)
    if (ringRadius > 0.5f) {
        drawCircle(
            color = ringColor.copy(alpha = ringColor.alpha * alpha),
            radius = ringRadius,
            center = center,
            style = Stroke(width = (m * u).coerceAtLeast(1f))
        )
    }

    val centerRadius = (((1f - shrink) * 0.6f) + 1f) * u * m
    val centerDotColor = lerp(dotColor, recorderColor, enterProgress)
    drawCircle(
        color = centerDotColor.copy(alpha = centerDotColor.alpha * alpha),
        radius = centerRadius.coerceAtLeast(1f),
        center = center
    )
}

/** QC click feedback: shrinking stroke ring overlay; base pointer colors stay unchanged underneath. */
fun DrawScope.drawQcPointerClickRing(
    center: Offset,
    outerRadius: Float,
    dotDiameterPx: Float,
    ringColor: Color,
    highlightColor: Color,
    visibilityAlpha: Float = 1f,
    sizeScale: Float = 1f,
    clickProgress: Float = 0f
) {
    val click = clickProgress.coerceIn(0f, 1f)
    if (click <= 0.001f) return
    val alpha = visibilityAlpha.coerceIn(0f, 1f)
    val dotRadius = (dotDiameterPx / 2f) * sizeScale
    // QC animates clickCircleSize from outer radius to dot diameter, drawn at (size - dotRadius).
    val clickRingRadius = (outerRadius - dotRadius) * (1f - click) + dotRadius * click
    val strokeColor = lerp(ringColor, highlightColor, click)
    drawCircle(
        color = strokeColor.copy(alpha = strokeColor.alpha * alpha),
        radius = clickRingRadius,
        center = center,
        style = Stroke(width = dotDiameterPx * sizeScale)
    )
}

private fun DrawScope.drawAnnulus(
    center: Offset,
    outerRadius: Float,
    innerRadius: Float,
    color: Color
) {
    val path = Path().apply {
        fillType = PathFillType.EvenOdd
        addOval(
            Rect(
                center.x - outerRadius,
                center.y - outerRadius,
                center.x + outerRadius,
                center.y + outerRadius
            )
        )
        addOval(
            Rect(
                center.x - innerRadius,
                center.y - innerRadius,
                center.x + innerRadius,
                center.y + innerRadius
            )
        )
    }
    drawPath(path, color)
}

internal fun DrawScope.drawGestureRecorderTrail(
    trailPoints: List<GestureRecorderTrailPoint>,
    color: Color,
    strokeWidthPx: Float
) {
    // QC `b60.draw`: always draw the full remaining shared list (no time clip).
    if (trailPoints.size < 2) return
    val strokeWidth = strokeWidthPx.coerceAtLeast(1f)
    val path = Path()
    var first = true
    for (point in trailPoints) {
        if (first) {
            path.moveTo(point.x.toFloat(), point.y.toFloat())
            first = false
        } else {
            path.lineTo(point.x.toFloat(), point.y.toFloat())
        }
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = androidx.compose.ui.graphics.StrokeJoin.Round
        )
    )
}

internal fun DrawScope.drawFloatingPointerTrail(
    trailPoints: List<FloatingPointerTrailPoint>,
    settings: AppSettings,
    lifespanMs: Long,
    nowMs: Long,
    trailColorArgb: Int = settings.floatingPointerTrailColorArgb,
    sequentialRetreat: Boolean = false,
    retreatProgress: Float = 0f,
    forceDraw: Boolean = false
) {
    val trailType = FloatingPointerTrailType.fromId(settings.floatingPointerTrailTypeId)
    if (trailPoints.size < 2) return
    if (!forceDraw && trailType == FloatingPointerTrailType.OFF) return

    val baseColor = Color(trailColorArgb)
    val strokeBasePx = settings.floatingPointerDotDiameterPx.coerceAtLeast(1f)

    if (sequentialRetreat) {
        drawGestureTrailRetreatSuffix(
            trailPoints = trailPoints,
            progress = retreatProgress,
            color = baseColor,
            strokeBasePx = strokeBasePx
        )
        return
    }

    val visible = trailPoints.filter { nowMs - it.timeMs <= lifespanMs }
    if (visible.size < 2) return

    for (index in 1 until visible.size) {
        val prev = visible[index - 1]
        val next = visible[index]
        val age = (nowMs - next.timeMs).toFloat() / lifespanMs
        if (age >= 1f) continue
        drawQcTrailSegment(
            from = Offset(prev.x, prev.y),
            to = Offset(next.x, next.y),
            ageFraction = age,
            color = baseColor,
            strokeBasePx = strokeBasePx
        )
    }
}

private fun DrawScope.drawGestureTrailRetreatSuffix(
    trailPoints: List<FloatingPointerTrailPoint>,
    progress: Float,
    color: Color,
    strokeBasePx: Float
) {
    if (trailPoints.size < 2 || progress >= 1f) return
    val firstTime = trailPoints.first().timeMs
    val lastTime = trailPoints.last().timeMs
    val span = (lastTime - firstTime).coerceAtLeast(1L)
    val cutoffTime = firstTime + (span * progress).toLong()
    var startIndex = 0
    while (startIndex < trailPoints.lastIndex &&
        trailPoints[startIndex + 1].timeMs <= cutoffTime
    ) {
        startIndex++
    }
    val drawFrom = startIndex.coerceAtMost(trailPoints.lastIndex - 1)
    for (index in drawFrom + 1 until trailPoints.size) {
        val prev = trailPoints[index - 1]
        val next = trailPoints[index]
        drawQcTrailSegment(
            from = Offset(prev.x, prev.y),
            to = Offset(next.x, next.y),
            ageFraction = 0f,
            color = color,
            strokeBasePx = strokeBasePx
        )
    }
}

internal fun DrawScope.drawQcTrailSegment(
    from: Offset,
    to: Offset,
    ageFraction: Float,
    color: Color,
    strokeBasePx: Float
) {
    val fade = (1f - ageFraction.coerceIn(0f, 1f))
    if (fade <= 0.001f) return
    val strokeWidth = strokeBasePx * fade
    if (strokeWidth < 0.5f) return
    val segmentColor = color.copy(alpha = color.alpha * fade)
    val capRadius = strokeWidth / 2f
    drawCircle(
        color = segmentColor,
        radius = capRadius,
        center = from
    )
    drawLine(
        color = segmentColor,
        start = from,
        end = to,
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
}

internal fun DrawScope.drawFloatingPointerRipple(
    center: Offset,
    progress: Float,
    rippleColor: Color,
    rippleSizePx: Float
) {
    val fraction = progress.coerceIn(0f, 1f)
    if (fraction <= 0.001f) return

    val animatedRadius = rippleSizePx * fraction
    val drawRadius = (animatedRadius / 2f).coerceAtLeast(1f)
    val baseAlpha = rippleColor.alpha.coerceIn(0f, 1f)
    val fadeAlpha = (1f - fraction) * baseAlpha
    if (fadeAlpha <= 0.001f) return

    val innerAlpha = fadeAlpha / 1.3f
    val outerAlpha = fadeAlpha

    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to rippleColor.copy(alpha = innerAlpha),
                1f to rippleColor.copy(alpha = outerAlpha)
            ),
            center = center,
            radius = drawRadius
        ),
        radius = drawRadius,
        center = center
    )
}

internal const val FLOATING_POINTER_RIPPLE_DURATION_MS = 500L

/** Quick Cursor click ring shrink duration derived from cursor/ripple sizes. */
internal fun floatingPointerClickAnimDurationMs(settings: AppSettings, density: Float): Int {
    val rippleSizePx = settings.floatingPointerRippleSizeDp * density
    val cursorSizePx = settings.floatingPointerPointerDiameterPx
    val ratio = (cursorSizePx * 1.3f) / rippleSizePx.coerceAtLeast(1f)
    return kotlin.math.min(
        (ratio * settings.floatingPointerRippleDurationMs).toInt(),
        settings.floatingPointerRippleDurationMs
    ).coerceAtLeast(80)
}
