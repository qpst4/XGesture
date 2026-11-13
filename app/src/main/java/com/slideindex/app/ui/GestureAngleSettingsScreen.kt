package com.slideindex.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import com.slideindex.app.ui.HomeLeadingIcons
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slideindex.app.R
import com.slideindex.app.gesture.GESTURE_ANGLE_BASE
import com.slideindex.app.gesture.GestureAngle
import com.slideindex.app.gesture.GestureAnglePoint
import com.slideindex.app.gesture.GestureAngles
import com.slideindex.app.gesture.SwipeDirection
import com.slideindex.app.gesture.forSide
import com.slideindex.app.gesture.withSide
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.ui.settings.components.SettingsCardScope
import com.slideindex.app.ui.miuix.MiuixHintText
import com.slideindex.app.ui.miuix.MiuixTopBarIconButton
import kotlinx.coroutines.launch
import kotlin.math.atan
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Reset
import top.yukonga.miuix.kmp.icon.MiuixIcons

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GestureAngleSettingsScreen(
    angles: GestureAngles,
    livePreviewEnabled: Boolean,
    onBack: () -> Unit,
    onSave: suspend (GestureAngles) -> Boolean,
    onPreviewStart: (GestureAngles) -> Unit = {},
    onPreviewAnglesChange: (GestureAngles) -> Unit = {},
    onPreviewStop: () -> Unit = {}
) {
    var draft by remember { mutableStateOf(angles) }
    var selectedSide by remember { mutableStateOf(PanelSide.LEFT) }
    var saving by remember { mutableStateOf(false) }
    val saveScope = rememberCoroutineScope()

    DisposableEffect(livePreviewEnabled) {
        if (livePreviewEnabled) {
            onPreviewStart(draft)
        }
        onDispose {
            if (livePreviewEnabled) {
                onPreviewStop()
            }
        }
    }
    LaunchedEffect(draft, livePreviewEnabled) {
        if (livePreviewEnabled) {
            onPreviewAnglesChange(draft)
        }
    }

    SettingsScreenScaffold(
        title = stringResource(R.string.gesture_angle_title),
        pageHint = stringResource(R.string.gesture_angle_entry_desc),
        onBack = onBack,
        scrollContent = false,
        actions = {
            MiuixTopBarIconButton(
                onClick = {
                    draft = draft.withSide(
                        selectedSide,
                        when (selectedSide) {
                            PanelSide.BOTTOM -> GestureAngle.DEFAULT_BOTTOM
                            PanelSide.TOP -> GestureAngle.DEFAULT_TOP
                            else -> GestureAngle.DEFAULT_LEFT
                        }
                    )
                }
            ) {
                // 这枚按钮是「重置为默认角度」（contentDescription = gesture_angle_reset），
                // 不是"历史"，所以用 Reset 而不是 Recent（Material 原来的 History 也是错的）。
                Icon(MiuixIcons.Medium.Reset, contentDescription = stringResource(R.string.gesture_angle_reset))
            }
            MiuixTopBarIconButton(
                enabled = !saving,
                onClick = {
                    saveScope.launch {
                        saving = true
                        try {
                            if (onSave(draft)) {
                                onBack()
                            }
                        } finally {
                            saving = false
                        }
                    }
                }
            ) {
                Icon(MiuixIcons.Medium.Ok, contentDescription = stringResource(R.string.gesture_angle_save))
            }
        }
    ) {
        item(key = "gesture-angle-body") {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                ) {
                    listOf(
                        PanelSide.LEFT to stringResource(R.string.gesture_angle_side_left),
                        PanelSide.RIGHT to stringResource(R.string.gesture_angle_side_right),
                        PanelSide.BOTTOM to stringResource(R.string.gesture_angle_side_bottom),
                        PanelSide.TOP to stringResource(R.string.gesture_angle_side_top)
                    ).forEachIndexed { index, (side, label) ->
                        SegmentedButton(
                            selected = selectedSide == side,
                            onClick = { selectedSide = side },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = 4)
                        ) {
                            Text(label)
                        }
                    }
                }
                if (livePreviewEnabled) {
                    MiuixHintText(stringResource(R.string.gesture_angle_live_preview_hint))
                }
                GestureAngleDiagram(
                    side = selectedSide,
                    angle = draft.forSide(selectedSide),
                    onAngleChange = { updated ->
                        draft = draft.withSide(selectedSide, updated)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            when (selectedSide) {
                                PanelSide.BOTTOM -> Modifier.navigationBarsPadding()
                                PanelSide.TOP -> Modifier.padding(top = 8.dp)
                                else -> Modifier
                            }
                        )
                )
            }
        }
    }
}

@Composable
fun SettingsCardScope.GestureAngleEntryCard(
    enabled: Boolean,
    outlinedLeadingIcons: Boolean = false,
    onClick: () -> Unit
) {
    SettingNavigationRow(
        icon = { label ->
            Icon(HomeLeadingIcons.gestureAngle(outlinedLeadingIcons), contentDescription = label)
        },
        title = stringResource(R.string.gesture_angle_entry_title),
        subtitle = stringResource(R.string.gesture_angle_entry_desc),
        enabled = enabled,
        onClick = onClick
    )
}

@Composable
private fun GestureAngleDiagram(
    side: PanelSide,
    angle: GestureAngle,
    onAngleChange: (GestureAngle) -> Unit,
    modifier: Modifier = Modifier
) {
    val degrees = remember(angle) { List(angle.ps.size) { angle.getDegree(it) } }
    val arcDegrees = remember(angle) { angle.getArcDegrees() }
    val primary = MaterialTheme.colorScheme.primary
    val labelStyle = MaterialTheme.typography.labelLarge.copy(
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp
    )
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val handleRadius = if (side == PanelSide.BOTTOM) 15.dp else 20.dp
    val lineWidth = if (side == PanelSide.BOTTOM) 4.5.dp else 6.dp
    val labelRadiusOffset = 40.dp
    var circleRadius by remember { mutableFloatStateOf(0f) }
    var circleCenter by remember { mutableStateOf(Offset.Zero) }
    var viewBounds by remember { mutableStateOf(Rect.Zero) }
    var activePoint by remember { mutableIntStateOf(-1) }
    val latestAngle by rememberUpdatedState(angle)
    val latestOnAngleChange by rememberUpdatedState(onAngleChange)
    val latestSide by rememberUpdatedState(side)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(360.dp)
            .pointerInput(side, handleRadius) {
                val handleRadiusPx = with(density) { handleRadius.toPx() }
                var dragOffset = Offset.Zero
                var draggingPoint = -1
                detectDragGestures(
                    onDragStart = { offset ->
                        dragOffset = offset
                        draggingPoint = latestAngle.ps.indexOfFirst { p ->
                            val index = latestAngle.ps.indexOf(p)
                            val degree = latestAngle.getDegree(index)
                            val handleOffset = calcDragHandleOffset(
                                side = latestSide,
                                circleCenter = circleCenter,
                                circleRadius = circleRadius,
                                degree = degree
                            )
                            val bounds = Rect(
                                center = handleOffset,
                                radius = handleRadiusPx
                            )
                            bounds.contains(offset)
                        }
                        activePoint = draggingPoint
                    },
                    onDrag = { change, dragAmount ->
                        if (draggingPoint < 0) return@detectDragGestures
                        dragOffset += dragAmount
                        if (!viewBounds.contains(dragOffset)) return@detectDragGestures
                        val opposite = when (latestSide) {
                            PanelSide.LEFT -> dragOffset.x
                            PanelSide.RIGHT -> circleCenter.x - dragOffset.x
                            PanelSide.BOTTOM -> circleCenter.y - dragOffset.y
                            PanelSide.TOP -> dragOffset.y - circleCenter.y
                        }
                        val neighbor = when (latestSide) {
                            PanelSide.LEFT, PanelSide.RIGHT -> circleCenter.y - dragOffset.y
                            PanelSide.BOTTOM -> circleCenter.x - dragOffset.x
                            PanelSide.TOP -> dragOffset.x - circleCenter.x
                        }
                        if (neighbor == 0f) return@detectDragGestures
                        val tanVal = opposite / neighbor
                        val radians = atan(tanVal.toDouble())
                        var newDegree = Math.toDegrees(radians).toFloat()
                        if (newDegree < 0f) {
                            newDegree = 90f + (newDegree + 90f)
                        }
                        val minGapP = run {
                            val sinVal = handleRadiusPx / circleRadius.coerceAtLeast(1f)
                            Math.toDegrees(sin(sinVal.toDouble())) / GESTURE_ANGLE_BASE
                        }
                        val point = GestureAnglePoint.entries[draggingPoint]
                        latestOnAngleChange(
                            latestAngle.copyPoint(
                                field = point,
                                newP = (newDegree / GESTURE_ANGLE_BASE).coerceIn(0f, 1f),
                                minGapP = minGapP.toFloat()
                            )
                        )
                    },
                    onDragEnd = {
                        dragOffset = Offset.Zero
                        draggingPoint = -1
                        activePoint = -1
                    },
                    onDragCancel = {
                        dragOffset = Offset.Zero
                        draggingPoint = -1
                        activePoint = -1
                    }
                )
            }
    ) {
        val radius = when (side) {
            PanelSide.LEFT, PanelSide.RIGHT -> size.minDimension / 2f
            PanelSide.BOTTOM, PanelSide.TOP -> size.minDimension / 4f
        }
        val center = when (side) {
            PanelSide.LEFT -> Offset(0f, size.height / 2f)
            PanelSide.RIGHT -> Offset(size.width, size.height / 2f)
            PanelSide.BOTTOM -> Offset(size.width / 2f, size.height)
            PanelSide.TOP -> Offset(size.width / 2f, 0f)
        }
        circleRadius = radius
        circleCenter = center
        viewBounds = Rect(Offset.Zero, size)
        val lineWidthPx = lineWidth.toPx()
        val handleRadiusPx = handleRadius.toPx()
        val labelOffsetPx = labelRadiusOffset.toPx()

        clipRect {
            drawCircle(
                color = primary,
                radius = radius,
                center = center,
                alpha = 0.12f
            )
            drawCircle(
                color = primary,
                radius = radius,
                center = center,
                alpha = 0.35f,
                style = Stroke(width = 2.dp.toPx())
            )
        }

        degrees.forEachIndexed { index, degree ->
            val handleOffset = calcDragHandleOffset(side, center, radius, degree)
            val isActive = index == activePoint
            drawLine(
                color = primary.copy(alpha = if (isActive) 1f else 0.7f),
                start = center,
                end = handleOffset,
                strokeWidth = if (isActive) lineWidthPx * 1.2f else lineWidthPx
            )
            drawCircle(
                color = primary,
                radius = if (isActive) handleRadiusPx * 1.1f else handleRadiusPx,
                center = handleOffset
            )
        }

        drawCircle(
            color = primary,
            radius = lineWidthPx,
            center = center
        )

        val labels = SwipeDirection.ordered()
        arcDegrees.forEachIndexed { index, arcDegree ->
            val boundaryDegree = degrees.getOrNull(index) ?: GESTURE_ANGLE_BASE
            val labelAnchor = calcDragHandleOffset(
                side = side,
                circleCenter = center,
                circleRadius = radius + labelOffsetPx,
                degree = boundaryDegree - (arcDegree / 2f)
            )
            drawDirectionLabel(
                direction = labels[index],
                degrees = arcDegree,
                anchor = labelAnchor,
                textMeasurer = textMeasurer,
                style = labelStyle,
                color = primary,
                side = side,
                canvasWidth = size.width,
                canvasHeight = size.height
            )
        }
    }
}

private fun calcDragHandleOffset(
    side: PanelSide,
    circleCenter: Offset,
    circleRadius: Float,
    degree: Float
): Offset {
    val transformedDegree = if (degree > 90f) {
        GESTURE_ANGLE_BASE - degree
    } else {
        degree
    }
    val radians = Math.toRadians(transformedDegree.toDouble())
    val opposite = circleRadius * sin(radians)
    val neighbor = sqrt(circleRadius.pow(2) - opposite.pow(2))
    val x = when (side) {
        PanelSide.LEFT -> circleCenter.x + opposite.toFloat()
        PanelSide.RIGHT -> circleCenter.x - opposite.toFloat()
        PanelSide.BOTTOM -> if (degree > 90f) {
            circleCenter.x + neighbor.toFloat()
        } else {
            circleCenter.x - neighbor.toFloat()
        }
        PanelSide.TOP -> if (degree > 90f) {
            circleCenter.x - neighbor.toFloat()
        } else {
            circleCenter.x + neighbor.toFloat()
        }
    }
    val y = when (side) {
        PanelSide.LEFT, PanelSide.RIGHT -> if (degree > 90f) {
            circleCenter.y + neighbor.toFloat()
        } else {
            circleCenter.y - neighbor.toFloat()
        }
        PanelSide.BOTTOM -> circleCenter.y - opposite.toFloat()
        PanelSide.TOP -> circleCenter.y + opposite.toFloat()
    }
    return Offset(x, y)
}

private fun DrawScope.drawDirectionLabel(
    direction: SwipeDirection,
    degrees: Float,
    anchor: Offset,
    textMeasurer: TextMeasurer,
    style: TextStyle,
    color: androidx.compose.ui.graphics.Color,
    side: PanelSide,
    canvasWidth: Float,
    canvasHeight: Float
) {
    val symbol = when (direction) {
        SwipeDirection.UP -> "↑"
        SwipeDirection.UP_RIGHT -> "↗"
        SwipeDirection.IN -> when (side) {
            PanelSide.BOTTOM -> "↑"
            PanelSide.TOP -> "↓"
            PanelSide.LEFT -> "→"
            PanelSide.RIGHT -> "←"
        }
        SwipeDirection.DOWN_RIGHT -> "↘"
        SwipeDirection.DOWN -> "↓"
    }
    val label = "$symbol ${degrees.roundToInt()}"
    val layout = textMeasurer.measure(label, style)
    val x = when (side) {
        PanelSide.LEFT, PanelSide.BOTTOM, PanelSide.TOP -> anchor.x - layout.size.width / 2f
        PanelSide.RIGHT -> anchor.x - layout.size.width
    }.coerceIn(0f, maxOf(0f, canvasWidth - layout.size.width))
    val y = when (side) {
        PanelSide.LEFT, PanelSide.RIGHT -> anchor.y - layout.size.height / 2f
        PanelSide.BOTTOM -> anchor.y - layout.size.height
        PanelSide.TOP -> anchor.y
    }.coerceIn(0f, maxOf(0f, canvasHeight - layout.size.height))
    drawText(
        textLayoutResult = layout,
        topLeft = Offset(x, y),
        color = color
    )
}
