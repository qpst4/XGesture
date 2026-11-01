package com.slideindex.app.overlay.animation

/*
 * Portions derived from SideGesture (https://github.com/aaronzzx/gulugulu)
 * Licensed under Apache-2.0. Modified for com.slideindex.app.
 */

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.slideindex.app.overlay.animation.GestureAnimationTriggerDirection.Center
import com.slideindex.app.overlay.animation.GestureAnimationTriggerDirection.Center2
import com.slideindex.app.overlay.animation.GestureAnimationTriggerDirection.Click
import com.slideindex.app.overlay.animation.GestureAnimationTriggerDirection.Down
import com.slideindex.app.overlay.animation.GestureAnimationTriggerDirection.Down2
import com.slideindex.app.overlay.animation.GestureAnimationTriggerDirection.Up
import com.slideindex.app.overlay.animation.GestureAnimationTriggerDirection.Up2
import com.slideindex.app.gesture.GestureAnimationProgress
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.gesture.SwipeDirection
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.settings.AnimationStyle
import com.slideindex.app.settings.BubbleStyle
import com.slideindex.app.settings.CapsuleStyle
import com.slideindex.app.settings.WaveStyle
import com.slideindex.app.settings.animationIconInitialRotation
import com.slideindex.app.settings.painterIcon
import com.slideindex.app.ui.ThinActionIcons
import com.slideindex.app.ui.gestureTriggerIconImageVector
import com.slideindex.app.ui.gestureTriggerIconRotationZ
import kotlin.math.abs

// B：按下瞬间的最小可见进度（px）。原来进度完全由位移驱动，
// 手指只动几像素时画出来的形状小到看不见，观感像"还没出现"。
private const val MIN_VISIBLE_PROGRESS_PX = 16f

@Composable
private fun rememberCornerPainters(): (PanelSide, GestureTriggerType, Painter) -> Painter {
    val cornerUpPainter = rememberVectorPainter(ThinActionIcons.CornerArrowUpRight)
    val cornerDownPainter = rememberVectorPainter(ThinActionIcons.CornerArrowDownRight)
    val doubleCornerUpPainter = rememberVectorPainter(ThinActionIcons.DoubleCornerArrowUpRight)
    val doubleCornerDownPainter = rememberVectorPainter(ThinActionIcons.DoubleCornerArrowDownRight)

    return remember(cornerUpPainter, cornerDownPainter, doubleCornerUpPainter, doubleCornerDownPainter) {
        { side, trigger, fallback ->
            val vector = gestureTriggerIconImageVector(side, trigger)
            when (vector) {
                ThinActionIcons.CornerArrowUpRight -> cornerUpPainter
                ThinActionIcons.CornerArrowDownRight -> cornerDownPainter
                ThinActionIcons.DoubleCornerArrowUpRight -> doubleCornerUpPainter
                ThinActionIcons.DoubleCornerArrowDownRight -> doubleCornerDownPainter
                else -> fallback
            }
        }
    }
}

@Composable
fun GestureAnimation(
    animationStyle: AnimationStyle,
    animationState: GestureAnimationState,
    modifier: Modifier = Modifier,
) {
    when (animationStyle) {
        is WaveStyle -> WaveGestureAnimation(
            modifier = modifier,
            animationStyle = animationStyle,
            animationState = animationState,
        )
        is CapsuleStyle -> CapsuleGestureAnimation(
            modifier = modifier,
            animationStyle = animationStyle,
            animationState = animationState,
        )
        is BubbleStyle -> BubbleGestureAnimation(
            modifier = modifier,
            animationStyle = animationStyle,
            animationState = animationState,
        )
    }
}

@Composable
private fun CapsuleGestureAnimation(
    animationStyle: CapsuleStyle,
    animationState: GestureAnimationState,
    modifier: Modifier = Modifier,
) {
    val button = animationState.button ?: return
    val shortIcon = animationStyle.painterIcon(isLong = false)
    val longIcon = animationStyle.painterIcon(isLong = true)
    val returnIcon = rememberVectorPainter(ThinActionIcons.SwipeReturn)
    val cornerPainterResolver = rememberCornerPainters()
    @Suppress("UNUSED_VARIABLE")
    val redrawFrame = animationState.redrawTick

    Canvas(modifier = modifier) {
        val originXAnimVal = animationState.originXAnimVal
        val originYAnimVal = animationState.originYAnimVal
        val fingerXAnimVal = animationState.fingerXAnimVal
        val fingerYAnimVal = animationState.fingerYAnimVal
        if (originXAnimVal.isNaN() ||
            originYAnimVal.isNaN() ||
            fingerXAnimVal.isNaN() ||
            fingerYAnimVal.isNaN()
        ) {
            return@Canvas
        }

        // 按时间入场：启动后的 GESTURE_ENTRY_RAMP_MS 内把进度从 0 拉到当前值，
        // 让图形"长出来"而不是第一帧就满长（按距离的入场只需十几像素，早就被走完了）。
        val progress = GestureAnimationProgress.progress(
            position = button.position,
            originX = originXAnimVal,
            originY = originYAnimVal,
            fingerX = fingerXAnimVal,
            fingerY = fingerYAnimVal,
            swipeDirection = animationState.swipeDirection,
        ).coerceAtLeast(MIN_VISIBLE_PROGRESS_PX) * animationState.entryRamp
        if (progress <= 1f) {
            return@Canvas
        }

        val yShift = animationState.displayYOffset(button.position)
        val horizontalAlongEdge = GestureAnimationProgress.isHorizontalAlongEdge(
            button.position,
            animationState.swipeDirection,
        )

        val thickness = animationStyle.thickness.toFloat().coerceAtLeast(1f)
        val strokeWidth = animationStyle.strokeWidth.toFloat()
        val maxLength = animationStyle.maxLength.toFloat().coerceAtLeast(thickness)
        val length = progress.coerceAtMost(maxLength).coerceAtLeast(thickness)
        val entryDistance = (thickness + strokeWidth * 2f).coerceAtLeast(1f)
        val entryProgress = (progress / entryDistance).coerceIn(0f, 1f)
        val centerShiftRatio = (progress / maxLength).coerceIn(0f, 1f) * 0.2f
        val topLeft: Offset
        val rectSize: Size
        if (horizontalAlongEdge) {
            val centerY = when (button.position) {
                GestureAnimationPosition.Bottom -> yShift
                GestureAnimationPosition.Top -> size.height + yShift
                else -> 0f
            }
            val slideRight = animationState.swipeDirection == SwipeDirection.DOWN ||
                animationState.swipeDirection == SwipeDirection.DOWN_RIGHT
            val animatedLength = lerpFloat(0f, length, entryProgress)
            val anchorX = originXAnimVal +
                (fingerXAnimVal - originXAnimVal) * centerShiftRatio
            val startX = if (slideRight) anchorX else anchorX - animatedLength
            topLeft = Offset(startX, centerY - thickness / 2f)
            rectSize = Size(animatedLength, thickness)
        } else {
            val centerX = when (button.position) {
                GestureAnimationPosition.Left, GestureAnimationPosition.Right -> 0f
                GestureAnimationPosition.Bottom, GestureAnimationPosition.Top ->
                    (originXAnimVal + (fingerXAnimVal - originXAnimVal) * centerShiftRatio)
                        .safeCoerceIn(thickness / 2f, size.width - thickness / 2f)
            }
            val centerY = when (button.position) {
                GestureAnimationPosition.Left, GestureAnimationPosition.Right ->
                    (originYAnimVal + (fingerYAnimVal - originYAnimVal) * centerShiftRatio + yShift)
                        .safeCoerceIn(thickness / 2f, size.height - thickness / 2f)
                GestureAnimationPosition.Bottom -> yShift
                GestureAnimationPosition.Top -> size.height + yShift
            }
            val leftStart = lerpFloat(start = -length - strokeWidth, stop = 0f, fraction = entryProgress)
            val rightStart = lerpFloat(
                start = size.width + strokeWidth,
                stop = size.width - length,
                fraction = entryProgress,
            )
            val bottomStart = lerpFloat(
                start = size.height + strokeWidth,
                stop = size.height - length,
                fraction = entryProgress,
            ) + yShift
            val topStart = lerpFloat(
                start = -length - strokeWidth,
                stop = 0f,
                fraction = entryProgress,
            ) + yShift
            topLeft = when (button.position) {
                GestureAnimationPosition.Left -> Offset(leftStart, centerY - thickness / 2f)
                GestureAnimationPosition.Right -> Offset(rightStart, centerY - thickness / 2f)
                GestureAnimationPosition.Bottom -> Offset(centerX - thickness / 2f, bottomStart)
                GestureAnimationPosition.Top -> Offset(centerX - thickness / 2f, topStart)
            }
            rectSize = when (button.position) {
                GestureAnimationPosition.Left, GestureAnimationPosition.Right -> Size(length, thickness)
                GestureAnimationPosition.Bottom, GestureAnimationPosition.Top -> Size(thickness, length)
            }
        }
        val radiusCap = minOf(rectSize.width, rectSize.height) / 2f
        val cornerRadius = animationStyle.cornerRadius.toFloat().safeCoerceIn(0f, radiusCap)
        val canTriggered = animationState.canDistanceTriggered(button, isLongSlide = false)
        val isLongSlide = animationState.canDistanceTriggered(button, isLongSlide = true) ||
            animationState.triggerDirection.isLong ||
            animationState.currentTrigger?.isLongDistance == true
        val activeAlpha = if (canTriggered) 1f else 0.35f

        drawRoundRect(
            color = Color(animationStyle.backgroundColor),
            topLeft = topLeft,
            size = rectSize,
            cornerRadius = CornerRadius(cornerRadius, cornerRadius),
        )
        if (animationStyle.strokeWidth > 0) {
            drawRoundRect(
                color = Color(animationStyle.strokeColor),
                topLeft = topLeft,
                size = rectSize,
                cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                style = Stroke(animationStyle.strokeWidth.toFloat()),
            )
        }

        val side = button.position.toPanelSide()
        val trigger = animationState.currentTrigger
        val isCorner = trigger?.isCornerSwipe == true
        val isReturn = trigger?.isReturnSwipe == true
        val baseIcon = if (isLongSlide) longIcon else shortIcon
        val activeIcon = when {
            trigger != null && isCorner -> cornerPainterResolver(side, trigger, baseIcon)
            isReturn -> returnIcon
            else -> baseIcon
        }
        val degree = gestureAnimationIconRotationDegrees(
            position = button.position,
            trigger = trigger,
            triggerDirection = animationState.triggerDirection,
            swipeDirection = animationState.swipeDirection,
        )
        val iconSize = minOf(rectSize.width, rectSize.height) * animationStyle.iconScale
        val rectCenter = Offset(
            x = topLeft.x + rectSize.width / 2f,
            y = topLeft.y + rectSize.height / 2f,
        )
        rotate(degree, pivot = rectCenter) {
            translate(left = rectCenter.x - iconSize / 2f, top = rectCenter.y - iconSize / 2f) {
                activeIcon.run {
                    draw(
                        size = Size(iconSize, iconSize),
                        colorFilter = ColorFilter.tint(Color(animationStyle.iconColor)),
                        alpha = activeAlpha,
                    )
                }
            }
        }
    }
}

@Composable
private fun BubbleGestureAnimation(
    animationStyle: BubbleStyle,
    animationState: GestureAnimationState,
    modifier: Modifier = Modifier,
) {
    val button = animationState.button ?: return
    val shortIcon = animationStyle.painterIcon(isLong = false)
    val longIcon = animationStyle.painterIcon(isLong = true)
    val returnIcon = rememberVectorPainter(ThinActionIcons.SwipeReturn)
    val cornerPainterResolver = rememberCornerPainters()
    @Suppress("UNUSED_VARIABLE")
    val redrawFrame = animationState.redrawTick

    Canvas(modifier = modifier) {
        val originXAnimVal = animationState.originXAnimVal
        val originYAnimVal = animationState.originYAnimVal
        val fingerXAnimVal = animationState.fingerXAnimVal
        val fingerYAnimVal = animationState.fingerYAnimVal
        if (originXAnimVal.isNaN() ||
            originYAnimVal.isNaN() ||
            fingerXAnimVal.isNaN() ||
            fingerYAnimVal.isNaN()
        ) {
            return@Canvas
        }

        // 按时间入场：启动后的 GESTURE_ENTRY_RAMP_MS 内把进度从 0 拉到当前值，
        // 让图形"长出来"而不是第一帧就满长（按距离的入场只需十几像素，早就被走完了）。
        val progress = GestureAnimationProgress.progress(
            position = button.position,
            originX = originXAnimVal,
            originY = originYAnimVal,
            fingerX = fingerXAnimVal,
            fingerY = fingerYAnimVal,
            swipeDirection = animationState.swipeDirection,
        ).coerceAtLeast(MIN_VISIBLE_PROGRESS_PX) * animationState.entryRamp
        if (progress <= 1f) {
            return@Canvas
        }

        val yShift = animationState.displayYOffset(button.position)
        val horizontalAlongEdge = GestureAnimationProgress.isHorizontalAlongEdge(
            button.position,
            animationState.swipeDirection,
        )

        val diameter = animationStyle.diameter.toFloat().coerceAtLeast(1f)
        val radius = diameter / 2f
        val strokeWidth = animationStyle.strokeWidth.toFloat()
        val offset = progress.coerceAtMost(animationStyle.maxOffset.toFloat().coerceAtLeast(radius))
        val centerShiftRatio = (progress / animationStyle.maxOffset.toFloat().coerceAtLeast(1f))
            .coerceIn(0f, 1f) * 0.18f
        val centerX = when (button.position) {
            GestureAnimationPosition.Left -> -radius + offset
            GestureAnimationPosition.Right -> size.width + radius - offset
            GestureAnimationPosition.Bottom, GestureAnimationPosition.Top ->
                if (horizontalAlongEdge) {
                    val slideRight = animationState.swipeDirection == SwipeDirection.DOWN ||
                        animationState.swipeDirection == SwipeDirection.DOWN_RIGHT
                    val anchorX = originXAnimVal +
                        (fingerXAnimVal - originXAnimVal) * centerShiftRatio
                    if (slideRight) {
                        (anchorX + offset).safeCoerceIn(radius, size.width - radius)
                    } else {
                        (anchorX - offset).safeCoerceIn(radius, size.width - radius)
                    }
                } else {
                    (originXAnimVal + (fingerXAnimVal - originXAnimVal) * centerShiftRatio)
                        .safeCoerceIn(radius, size.width - radius)
                }
        }
        val centerY = when (button.position) {
            GestureAnimationPosition.Left, GestureAnimationPosition.Right ->
                (originYAnimVal + (fingerYAnimVal - originYAnimVal) * centerShiftRatio + yShift)
                    .safeCoerceIn(radius, size.height - radius)
            GestureAnimationPosition.Bottom ->
                if (horizontalAlongEdge) {
                    size.height + radius + yShift
                } else {
                    size.height + radius - offset + yShift
                }
            GestureAnimationPosition.Top ->
                if (horizontalAlongEdge) {
                    -radius + yShift
                } else {
                    -radius + offset + yShift
                }
        }
        val canTriggered = animationState.canDistanceTriggered(button, isLongSlide = false)
        val isLongSlide = animationState.canDistanceTriggered(button, isLongSlide = true) ||
            animationState.triggerDirection.isLong ||
            animationState.currentTrigger?.isLongDistance == true
        val activeAlpha = if (canTriggered) 1f else 0.35f

        drawCircle(
            color = Color(animationStyle.backgroundColor),
            radius = radius,
            center = Offset(centerX, centerY),
        )
        if (animationStyle.strokeWidth > 0) {
            drawCircle(
                color = Color(animationStyle.strokeColor),
                radius = radius - strokeWidth / 2f,
                center = Offset(centerX, centerY),
                style = Stroke(width = strokeWidth),
            )
        }

        val side = button.position.toPanelSide()
        val trigger = animationState.currentTrigger
        val isCorner = trigger?.isCornerSwipe == true
        val isReturn = trigger?.isReturnSwipe == true
        val baseIcon = if (isLongSlide) longIcon else shortIcon
        val activeIcon = when {
            trigger != null && isCorner -> cornerPainterResolver(side, trigger, baseIcon)
            isReturn -> returnIcon
            else -> baseIcon
        }
        val degree = gestureAnimationIconRotationDegrees(
            position = button.position,
            trigger = trigger,
            triggerDirection = animationState.triggerDirection,
            swipeDirection = animationState.swipeDirection,
        )
        val iconSize = diameter * animationStyle.iconScale
        val bubbleCenter = Offset(centerX, centerY)
        rotate(degree, pivot = bubbleCenter) {
            translate(
                left = centerX - radius + radius - iconSize / 2f,
                top = centerY - radius + radius - iconSize / 2f,
            ) {
                activeIcon.run {
                    draw(
                        size = Size(iconSize, iconSize),
                        colorFilter = ColorFilter.tint(Color(animationStyle.iconColor)),
                        alpha = activeAlpha,
                    )
                }
            }
        }
    }
}

@Composable
private fun WaveGestureAnimation(
    animationStyle: WaveStyle,
    animationState: GestureAnimationState,
    modifier: Modifier = Modifier,
) {
    val button = animationState.button ?: return
    val shortIcon = animationStyle.painterIcon(isLong = false)
    val longIcon = animationStyle.painterIcon(isLong = true)
    val returnIcon = rememberVectorPainter(ThinActionIcons.SwipeReturn)
    val cornerPainterResolver = rememberCornerPainters()
    val bezierPath = remember { Path() }
    val density = LocalDensity.current
    @Suppress("UNUSED_VARIABLE")
    val redrawFrame = animationState.redrawTick
    val bezierOffset = when (button.position) {
        GestureAnimationPosition.Left, GestureAnimationPosition.Right ->
            if (animationStyle.safeBounds) with(density) { 70.dp.toPx() } else 0f
        GestureAnimationPosition.Bottom, GestureAnimationPosition.Top -> 0f
    }
    val bezierSpacing = if (animationStyle.safeBounds) with(density) { 40.dp.toPx() } else 0f
    val bezierMaxWidth = animationStyle.width.toFloat()
    val bezierLengthHalf = bezierMaxWidth * animationStyle.bezierLengthHalfRatio
    val bezierTransformOffsetCoerce = if (animationStyle.transformEnabled) bezierLengthHalf / 2f else 0f

    Canvas(modifier = modifier) {
        val triggerDirection = animationState.triggerDirection
        val originXAnimVal = animationState.originXAnimVal
        val originYAnimVal = animationState.originYAnimVal
        val fingerXAnimVal = animationState.fingerXAnimVal
        val fingerYAnimVal = animationState.fingerYAnimVal
        if (originXAnimVal.isNaN() ||
            originYAnimVal.isNaN() ||
            fingerXAnimVal.isNaN() ||
            fingerYAnimVal.isNaN()
        ) {
            return@Canvas
        }
        // 沿边（平行于所贴那条边）的位移：左右取 Y 差、上下取 X 差。
        // originX/YAnimVal 与 fingerX/YAnimVal 都是绝对屏幕坐标，可以直接相减。
        val alongEdgeOffset = when (button.position) {
            GestureAnimationPosition.Left, GestureAnimationPosition.Right -> fingerYAnimVal - originYAnimVal
            GestureAnimationPosition.Bottom, GestureAnimationPosition.Top -> fingerXAnimVal - originXAnimVal
        }
        // 向内（垂直于边）的位移：上游 SideGestureState 里 fingerXAnimVal / fingerYAnimVal
        // 是「相对按下点的位移」（onDragStart 里 snapTo(0f)/snapTo(sticky) 之后累加每帧 delta），
        // 和左右方向一直当作深度用的 fingerXAnimVal 同义。所以上下方向的深度直接取它，
        // 不能再减 originY：顶部会被削掉「按下点到顶边」的那一截，底部直接变成大负值整块不可见。
        val inwardOffset = when (button.position) {
            GestureAnimationPosition.Left -> fingerXAnimVal
            GestureAnimationPosition.Right -> -fingerXAnimVal
            GestureAnimationPosition.Top -> fingerYAnimVal
            GestureAnimationPosition.Bottom -> -fingerYAnimVal
        }
        when (button.position) {
            GestureAnimationPosition.Left -> if (fingerXAnimVal < 0f) return@Canvas
            GestureAnimationPosition.Right -> if (fingerXAnimVal > 0f) return@Canvas
            GestureAnimationPosition.Top, GestureAnimationPosition.Bottom ->
                if (inwardOffset <= 1f && abs(alongEdgeOffset) <= 1f) return@Canvas
        }

        // 相对手指的视觉偏移只对左右方向成立：上下方向必须贴屏幕上下边，
        // 一旦叠加这个偏移（顶部实测 198px），整条波浪就离开边缘浮在屏幕里。
        val yShift = when (button.position) {
            GestureAnimationPosition.Left, GestureAnimationPosition.Right ->
                animationState.displayYOffset(button.position)
            GestureAnimationPosition.Bottom, GestureAnimationPosition.Top -> 0f
        }
        val originYDisplay = originYAnimVal + yShift

        // 沿边形变偏移：沿用上游的符号约定（左右 originY - fingerY，上下 originX - fingerX）。
        val transformOffset = when (button.position) {
            GestureAnimationPosition.Left, GestureAnimationPosition.Right -> originYAnimVal - fingerYAnimVal
            GestureAnimationPosition.Bottom, GestureAnimationPosition.Top -> originXAnimVal - fingerXAnimVal
        }.safeCoerceIn(-bezierTransformOffsetCoerce, bezierTransformOffsetCoerce)

        val safeOrigin = when (button.position) {
            GestureAnimationPosition.Left, GestureAnimationPosition.Right -> originYDisplay - bezierOffset
            GestureAnimationPosition.Bottom, GestureAnimationPosition.Top -> originXAnimVal - bezierOffset
        }.safeCoerceIn(
            minimumValue = when (animationStyle.safeBounds) {
                true -> bezierLengthHalf + bezierSpacing
                else -> 0f
            },
            maximumValue = when (button.position) {
                GestureAnimationPosition.Left, GestureAnimationPosition.Right ->
                    when (animationStyle.safeBounds) {
                        true -> size.height - bezierLengthHalf - bezierSpacing
                        else -> size.height
                    }
                GestureAnimationPosition.Bottom, GestureAnimationPosition.Top ->
                    when (animationStyle.safeBounds) {
                        true -> size.width - bezierLengthHalf - bezierSpacing
                        else -> size.width
                    }
            },
        )

        bezierPath.also { path ->
            path.reset()
            val moveToX = when (button.position) {
                GestureAnimationPosition.Left -> 0f
                GestureAnimationPosition.Right -> size.width
                GestureAnimationPosition.Bottom, GestureAnimationPosition.Top -> safeOrigin - bezierLengthHalf
            }
            val moveToY = when (button.position) {
                GestureAnimationPosition.Left, GestureAnimationPosition.Right -> safeOrigin - bezierLengthHalf
                GestureAnimationPosition.Bottom -> size.height
                GestureAnimationPosition.Top -> 0f
            }
            path.moveTo(moveToX, moveToY)

            val factor = 1.dp.toPx()
            when (button.position) {
                GestureAnimationPosition.Left, GestureAnimationPosition.Right -> {
                    val safeFingerX = when (button.position) {
                        GestureAnimationPosition.Left -> fingerXAnimVal.coerceAtMost(bezierMaxWidth)
                        else -> (size.width + fingerXAnimVal).coerceAtLeast(size.width - bezierMaxWidth)
                    }
                    var safeFingerY = safeOrigin - bezierLengthHalf / 2.5f - transformOffset
                    path.cubicTo(
                        x1 = when (button.position) {
                            GestureAnimationPosition.Left -> -factor
                            else -> size.width + factor
                        },
                        y1 = safeFingerY,
                        x2 = safeFingerX,
                        y2 = safeFingerY,
                        x3 = safeFingerX,
                        y3 = safeOrigin - transformOffset,
                    )
                    safeFingerY = safeOrigin + bezierLengthHalf / 2.5f - transformOffset
                    path.cubicTo(
                        x1 = safeFingerX,
                        y1 = safeFingerY,
                        x2 = when (button.position) {
                            GestureAnimationPosition.Left -> 0f
                            else -> size.width
                        },
                        y2 = safeFingerY,
                        x3 = when (button.position) {
                            GestureAnimationPosition.Left -> -factor
                            else -> size.width + factor
                        },
                        y3 = safeOrigin + bezierLengthHalf,
                    )
                }
                GestureAnimationPosition.Top, GestureAnimationPosition.Bottom -> {
                    // 与上游一致：顶部从 y=0、底部从 y=size.height 贴边起笔，凸起朝屏幕内。
                    val isTop = button.position == GestureAnimationPosition.Top
                    // 深度 = 贴边方向上的相对位移，夹到 maxWidth。
                    val depth = inwardOffset.coerceIn(0f, bezierMaxWidth)
                    val edgeY = if (isTop) 0f else size.height
                    val outerY = if (isTop) -factor else size.height + factor
                    val bulgeY = if (isTop) depth else size.height - depth
                    val firstControlX = safeOrigin - bezierLengthHalf / 2.5f - transformOffset
                    val secondControlX = safeOrigin + bezierLengthHalf / 2.5f - transformOffset
                    path.cubicTo(
                        x1 = firstControlX,
                        y1 = outerY,
                        x2 = firstControlX,
                        y2 = bulgeY,
                        x3 = safeOrigin - transformOffset,
                        y3 = bulgeY,
                    )
                    path.cubicTo(
                        x1 = secondControlX,
                        y1 = bulgeY,
                        x2 = secondControlX,
                        y2 = edgeY,
                        x3 = safeOrigin + bezierLengthHalf,
                        y3 = outerY,
                    )
                }
            }

            if (animationStyle.strokeWidth > 0) {
                val offset2 = when (button.position) {
                    GestureAnimationPosition.Left -> Offset(-animationStyle.strokeWidth.toFloat(), 0f)
                    GestureAnimationPosition.Right -> Offset(animationStyle.strokeWidth.toFloat(), 0f)
                    GestureAnimationPosition.Bottom -> Offset(0f, animationStyle.strokeWidth.toFloat())
                    GestureAnimationPosition.Top -> Offset(0f, -animationStyle.strokeWidth.toFloat())
                }
                path.translate(offset2)
            }
        }

        drawPath(path = bezierPath, color = Color(animationStyle.backgroundColor))
        if (animationStyle.strokeWidth > 0) {
            drawPath(
                path = bezierPath,
                color = Color(animationStyle.strokeColor),
                style = Stroke(animationStyle.strokeWidth.toFloat()),
            )
        }

        val bezierBounds = when (button.position) {
            GestureAnimationPosition.Left, GestureAnimationPosition.Right ->
                bezierPath.getBounds().translate(Offset(0f, -transformOffset))
            GestureAnimationPosition.Bottom, GestureAnimationPosition.Top ->
                bezierPath.getBounds().translate(Offset(-transformOffset, 0f))
        }
        val side = button.position.toPanelSide()
        val trigger = animationState.currentTrigger
        val isCorner = trigger?.isCornerSwipe == true
        val isReturn = trigger?.isReturnSwipe == true
        val canTriggered = animationState.canDistanceTriggered(button, isLongSlide = false)
        val isLongSlide = animationState.canDistanceTriggered(button, isLongSlide = true) ||
            animationState.triggerDirection.isLong ||
            animationState.currentTrigger?.isLongDistance == true
        val baseIcon = if (isLongSlide) longIcon else shortIcon
        val activeIcon = when {
            trigger != null && isCorner -> cornerPainterResolver(side, trigger, baseIcon)
            isReturn -> returnIcon
            else -> baseIcon
        }
        val degree = gestureAnimationIconRotationDegrees(
            position = button.position,
            trigger = trigger,
            triggerDirection = triggerDirection,
            swipeDirection = animationState.swipeDirection,
        )
        rotate(degree, pivot = bezierBounds.center) {
            val radius = when (button.position) {
                GestureAnimationPosition.Left, GestureAnimationPosition.Right ->
                    bezierBounds.width * animationStyle.iconScale
                GestureAnimationPosition.Bottom, GestureAnimationPosition.Top ->
                    bezierBounds.height * animationStyle.iconScale
            }
            // 图标画在形状自身包围盒的中心（四个方向共用一套算法）。
            // 原来 Top 用的是 -paddingVert，直接把图标画到屏幕上边以外去了。
            translate(
                left = bezierBounds.left + (bezierBounds.width - radius) / 2f,
                top = bezierBounds.top + (bezierBounds.height - radius) / 2f,
            ) {
                activeIcon.run {
                    draw(
                        size = Size(radius, radius),
                        colorFilter = ColorFilter.tint(Color(animationStyle.iconColor)),
                        alpha = if (canTriggered) 1f else 0.35f,
                    )
                }
            }
        }
    }
}

private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction

private fun gestureAnimationIconRotationDegrees(
    position: GestureAnimationPosition,
    trigger: GestureTriggerType?,
    triggerDirection: GestureAnimationTriggerDirection,
    swipeDirection: SwipeDirection?,
): Float {
    if (trigger != null) {
        return gestureTriggerIconRotationZ(position.toPanelSide(), trigger)
    }
    return animationIconInitialRotation(position) +
        triggerRotationOffset(triggerDirection, position, swipeDirection)
}

private fun triggerRotationOffset(
    triggerDirection: GestureAnimationTriggerDirection,
    position: GestureAnimationPosition,
    swipeDirection: SwipeDirection?,
): Float {
    if (position == GestureAnimationPosition.Top || position == GestureAnimationPosition.Bottom) {
        // Top 基准 90°（向内），Bottom 基准 270°（向内）；水平偏移需按边分别取反。
        val leftOffset = if (position == GestureAnimationPosition.Top) 90f else -90f
        val rightOffset = if (position == GestureAnimationPosition.Top) -90f else 90f
        val leftDiagOffset = if (position == GestureAnimationPosition.Top) 45f else -45f
        val rightDiagOffset = if (position == GestureAnimationPosition.Top) -45f else 45f
        return when (swipeDirection) {
            SwipeDirection.UP -> leftOffset
            SwipeDirection.DOWN -> rightOffset
            SwipeDirection.UP_RIGHT -> leftDiagOffset
            SwipeDirection.DOWN_RIGHT -> rightDiagOffset
            SwipeDirection.IN, null -> when (triggerDirection) {
                Up, Up2 -> leftOffset
                Down, Down2 -> rightOffset
                else -> 0f
            }
        }
    }
    return when (position) {
        GestureAnimationPosition.Left -> when (swipeDirection) {
            SwipeDirection.UP -> -90f
            SwipeDirection.DOWN -> 90f
            SwipeDirection.UP_RIGHT -> -45f
            SwipeDirection.DOWN_RIGHT -> 45f
            SwipeDirection.IN -> 0f
            null -> leftRightTriggerRotationFallback(triggerDirection, position)
        }
        GestureAnimationPosition.Right -> when (swipeDirection) {
            SwipeDirection.UP -> 90f
            SwipeDirection.DOWN -> -90f
            SwipeDirection.UP_RIGHT -> 45f
            SwipeDirection.DOWN_RIGHT -> -45f
            SwipeDirection.IN -> 0f
            null -> leftRightTriggerRotationFallback(triggerDirection, position)
        }
    }
}

private fun leftRightTriggerRotationFallback(
    triggerDirection: GestureAnimationTriggerDirection,
    position: GestureAnimationPosition,
): Float = when (triggerDirection) {
    Up -> when (position) {
        GestureAnimationPosition.Left -> -45f
        GestureAnimationPosition.Right -> 45f
        else -> 0f
    }
    Center, Center2, Click -> 0f
    Down -> when (position) {
        GestureAnimationPosition.Left -> 45f
        GestureAnimationPosition.Right -> -45f
        else -> 0f
    }
    Up2 -> when (position) {
        GestureAnimationPosition.Left -> -90f
        GestureAnimationPosition.Right -> 90f
        else -> 0f
    }
    Down2 -> when (position) {
        GestureAnimationPosition.Left -> 90f
        GestureAnimationPosition.Right -> -90f
        else -> 0f
    }
}

private fun Float.safeCoerceIn(minimumValue: Float, maximumValue: Float): Float = when {
    isNaN() -> 0f
    minimumValue <= maximumValue -> coerceIn(minimumValue, maximumValue)
    else -> (minimumValue + maximumValue) / 2f
}
