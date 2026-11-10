@file:Suppress("UNUSED_EXPRESSION")

package com.slideindex.app.overlay

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatingPointerDesign
import com.slideindex.app.ui.theme.OverlayAwareModuleTheme
import com.slideindex.app.util.HapticHelper
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull


@Composable
internal fun FloatingPointerDisplay(
    session: FloatingPointerSession,
    settings: AppSettings,
    visible: Boolean
) {
    OverlayAwareModuleTheme(settings = settings) {
        val presence by animateFloatAsState(
            targetValue = if (visible) 1f else 0f,
            animationSpec = tween(
                durationMillis = FLOATING_POINTER_PRESENCE_ANIMATION_MS.toInt(),
                easing = FastOutSlowInEasing
            ),
            label = "floatingPointerPresence"
        )
        if (presence <= 0.001f && !visible) return@OverlayAwareModuleTheme

        val pointerX by session.pointerX
        val pointerY by session.pointerY
        val joystickX by session.joystickCenterX
        val joystickY by session.joystickCenterY
        val joystickActive by session.joystickActive
        val pointerVisible by session.pointerVisible
        val radialMenuActive by session.radialMenuActive
        val radialMenuIdle by session.radialMenuIdle
        val gestureCaptureActive = session.gestureCaptureActive
        val gestureReplayActive by session.gestureReplayActive
        val gestureRecordingActive by session.gestureRecordingActive
        val realtimeGestureActive by session.realtimeGestureActive
        val gestureTrailRetreatActive by session.gestureTrailRetreatActive
        val gestureRecorderTrailRevision = session.gestureRecorderTrailRevision.intValue
        val pointerRestoreGeneration = session.pointerRestoreGeneration.intValue
        val radialHighlightedSlot by session.radialHighlightedSlot
        val rippleGeneration by session.rippleGeneration
        val continuedEdgeSessionActive by session.continuedEdgeSessionActive
        val screenshotCaptureInProgress by session.screenshotCaptureInProgress
        val suppressRadialForReleaseClick =
            settings.floatingPointerReleaseClickAndDismiss && continuedEdgeSessionActive
        val radialMenuAlwaysShown = settings.floatingPointerRadialAlwaysVisible &&
            !session.awaitingPlacement &&
            !suppressRadialForReleaseClick &&
            !screenshotCaptureInProgress
        val radialMenuTargetProgress = when {
            radialMenuActive -> 1f
            radialMenuIdle -> 1f
            radialMenuAlwaysShown -> 1f
            else -> 0f
        }
        val radialMenuProgress by animateFloatAsState(
            targetValue = radialMenuTargetProgress,
            animationSpec = tween(
                durationMillis = FLOATING_POINTER_RADIAL_MENU_ANIMATION_MS.toInt(),
                easing = FastOutSlowInEasing
            ),
            label = "radialMenuProgress"
        )
        val edgePreviewTarget = if (session.edgePreviewVisible.value) 1f else 0f
        val edgePreviewAlpha by animateFloatAsState(
            targetValue = edgePreviewTarget,
            animationSpec = tween(
                durationMillis = FLOATING_POINTER_EDGE_PREVIEW_ANIMATION_MS.toInt(),
                easing = if (edgePreviewTarget > 0f) FastOutSlowInEasing else LinearOutSlowInEasing
            ),
            label = "edgePreviewAlpha"
        )
        val suppressPointerForGestureAftermath =
            gestureRecordingActive ||
                gestureReplayActive ||
                (gestureTrailRetreatActive && !session.isGestureRecorderTrailRetreatConsumed())
        val showPointer = !suppressPointerForGestureAftermath && (
            gestureCaptureActive ||
                ((!settings.floatingPointerHideWhenJoystickReleased || pointerVisible) && !session.awaitingPlacement)
            )
        val pointerDesign = FloatingPointerDesign.fromId(settings.floatingPointerDesignId)
        val context = LocalContext.current
        val pointerBitmap = rememberFloatingPointerDesignBitmap(
            context = context,
            design = pointerDesign,
            sizePx = settings.floatingPointerPointerDiameterPx.roundToInt().coerceAtLeast(1)
        )
        val presenceScale = 0.72f + 0.28f * presence
        val rippleProgress = remember { Animatable(0f) }
        val pointerSizeScale = remember { Animatable(if (showPointer) 1f else 0f) }
        val pointerDrawAlpha = remember { Animatable(if (showPointer) 1f else 0f) }
        val pointerClickAnim = remember { Animatable(0f) }
        val gestureRecorderProgress = remember { Animatable(0f) }
        var animationTick by remember { mutableLongStateOf(0L) }

        LaunchedEffect(gestureRecordingActive, realtimeGestureActive, gestureReplayActive) {
            if (gestureRecordingActive || realtimeGestureActive) {
                gestureRecorderProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = GESTURE_RECORDER_POINTER_ANIMATION_MS,
                        easing = FastOutLinearInEasing
                    )
                )
            } else if (gestureReplayActive) {
                gestureRecorderProgress.snapTo(0f)
            } else {
                gestureRecorderProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = GESTURE_RECORDER_POINTER_ANIMATION_MS,
                        easing = LinearOutSlowInEasing
                    )
                )
            }
        }

        LaunchedEffect(showPointer, pointerRestoreGeneration) {
            if (showPointer) {
                if (pointerRestoreGeneration > 0) {
                    pointerDrawAlpha.snapTo(1f)
                    pointerSizeScale.snapTo(1f)
                } else {
                    launch {
                        pointerDrawAlpha.snapTo(0f)
                        pointerDrawAlpha.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = 400,
                                easing = FastOutSlowInEasing
                            )
                        )
                    }
                    launch {
                        pointerSizeScale.snapTo(0f)
                        pointerSizeScale.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = 0.42f,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                    }
                }
            } else {
                launch {
                    pointerDrawAlpha.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(
                            durationMillis = 300,
                            easing = LinearOutSlowInEasing
                        )
                    )
                }
                launch {
                    pointerSizeScale.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(
                            durationMillis = 500,
                            easing = FastOutSlowInEasing
                        )
                    )
                }
            }
        }

        val pointerClickGeneration = session.pointerClickGeneration.intValue
        LaunchedEffect(pointerClickGeneration, settings.floatingPointerRippleDurationMs) {
            if (pointerClickGeneration <= 0) return@LaunchedEffect
            val generation = pointerClickGeneration
            pointerClickAnim.snapTo(0f)
            pointerClickAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = floatingPointerClickAnimDurationMs(settings, session.density),
                    easing = QcPointerClickEasing
                )
            )
            if (session.pointerClickGeneration.intValue == generation) {
                pointerClickAnim.snapTo(0f)
            }
        }

        LaunchedEffect(rippleGeneration, settings.floatingPointerRippleDurationMs) {
            if (rippleGeneration <= 0) return@LaunchedEffect
            val generation = rippleGeneration
            rippleProgress.snapTo(0f)
            try {
                rippleProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = settings.floatingPointerRippleDurationMs,
                        easing = FastOutSlowInEasing
                    )
                )
            } finally {
                if (session.rippleGeneration.intValue == generation) {
                    session.rippleActive.value = false
                    rippleProgress.snapTo(0f)
                }
            }
        }

        LaunchedEffect(Unit) {
            while (true) {
                // 空闲时挂起等状态变化；最多 IDLE_RECHECK_MS 兜底重查一次，
                // 免得"只有时间衰减、没有状态写入"的收尾（轨迹过期、指针回填）被漏掉。
                withTimeoutOrNull(IDLE_RECHECK_MS) {
                    snapshotFlow { session.needsFrameTicker() }.first { it }
                }
                var keepTicking = true
                while (keepTicking) {
                    withFrameNanos { frameTime ->
                        val now = System.currentTimeMillis()
                        val recordingActive = session.gestureRecordingActive.value
                        val replayActive = session.gestureReplayActive.value
                        val retreatActive = session.gestureTrailRetreatActive.value
                        session.clearRippleIfExpired(now, settings.floatingPointerRippleDurationMs.toLong())
                        session.pruneExpiredTrailPoints(now)
                        session.clearInjectedSwipePreviewIfExpired(now)
                        session.finishGestureTrailRetreatIfConsumed(settings)
                        if (!replayActive && !recordingActive && !retreatActive) {
                            session.completeGestureAftermathIfReady(settings)
                        }
                        keepTicking = session.needsFrameTicker(now) ||
                            rippleProgress.isRunning ||
                            pointerSizeScale.isRunning ||
                            pointerDrawAlpha.isRunning ||
                            pointerClickAnim.isRunning ||
                            gestureRecorderProgress.isRunning
                        if (keepTicking) {
                            animationTick = frameTime
                        }
                    }
                }
            }
        }

        val trailPointCount = session.trailPoints.size
        val injectedSwipePreview by session.injectedSwipePreview
        val hoverSelectChrome by session.hoverSelectChrome
        val hoverSelectAsPlus = hoverSelectChrome.paused || hoverSelectChrome.regionalActive
        Box(modifier = Modifier.fillMaxSize()) {
            if (hoverSelectChrome.visible ||
                hoverSelectChrome.hintMode != FloatBallCursorPreviewView.HintMode.HIDDEN ||
                hoverSelectChrome.paused ||
                hoverSelectChrome.regionalActive
            ) {
                AndroidView(
                    factory = { ctx -> FloatBallCursorPreviewView(ctx) },
                    update = { view ->
                        view.setChromeState(
                            visible = hoverSelectChrome.visible,
                            paused = hoverSelectChrome.paused,
                            selectionStart = if (hoverSelectChrome.hasSelectionStart) {
                                Offset(
                                    hoverSelectChrome.selectionStartX,
                                    hoverSelectChrome.selectionStartY
                                )
                            } else {
                                null
                            },
                            selectionPreviewBounds = hoverSelectChrome.previewBoundsOrNull(),
                            pickAnchor = Offset(
                                hoverSelectChrome.pickAnchorX,
                                hoverSelectChrome.pickAnchorY
                            ),
                            regionalDragActive = hoverSelectChrome.regionalActive,
                            crossVisible = hoverSelectChrome.paused || hoverSelectChrome.regionalActive,
                            crossAlpha = 1f,
                            crossPaused = hoverSelectChrome.paused,
                            crossArmDp = settings.floatBallPickCrossArmDp,
                            hintMode = hoverSelectChrome.hintMode
                        )
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                    val anchorX = if (!session.awaitingPlacement) joystickX else pointerX
                    val anchorY = if (!session.awaitingPlacement) joystickY else pointerY
                    val width = size.width.coerceAtLeast(1f)
                    val height = size.height.coerceAtLeast(1f)
                    transformOrigin = TransformOrigin(
                        pivotFractionX = (anchorX / width).coerceIn(0f, 1f),
                        pivotFractionY = (anchorY / height).coerceIn(0f, 1f)
                    )
                    scaleX = presenceScale
                    scaleY = presenceScale
                    alpha = presence
                }
        ) {
            animationTick
            trailPointCount
            Canvas(Modifier.fillMaxSize()) {
                val now = session.effectiveTrailNowMs(System.currentTimeMillis())
                if (edgePreviewAlpha > 0.001f && session.edgeActionSegments.isNotEmpty()) {
                    FloatingPointerEdgeActionsRenderer.draw(
                        scope = this,
                        settings = settings,
                        segments = session.edgeActionSegments,
                        previewAlpha = edgePreviewAlpha,
                        density = density
                    )
                }
                if (session.trailPoints.size >= 2 &&
                    !gestureRecordingActive &&
                    !gestureTrailRetreatActive &&
                    !gestureReplayActive &&
                    !session.shouldDrawGestureRecorderTrail(now)
                ) {
                    val lifespanMs = session.trailLifespanOverrideMs
                        ?: settings.floatingPointerTrailDurationMs.coerceAtLeast(50).toLong()
                    drawFloatingPointerTrail(
                        trailPoints = session.trailPoints,
                        settings = settings,
                        lifespanMs = lifespanMs,
                        nowMs = now,
                        trailColorArgb = settings.floatingPointerTrailColorArgb
                    )
                }
                if (settings.floatingPointerClickVisualFeedbackEnabled && rippleProgress.value > 0.001f) {
                    val rippleSizePx = settings.floatingPointerRippleSizeDp * density
                    drawFloatingPointerRipple(
                        center = Offset(session.rippleCenterX.floatValue, session.rippleCenterY.floatValue),
                        progress = rippleProgress.value,
                        rippleColor = Color(settings.floatingPointerRippleColorArgb),
                        rippleSizePx = rippleSizePx
                    )
                }
                val recorderProgress = gestureRecorderProgress.value
                when {
                    hoverSelectAsPlus -> {
                        // FloatBallCursorPreviewView draws the pick plus; hide ring/design.
                    }
                    gestureRecordingActive || realtimeGestureActive || recorderProgress > 0.001f -> {
                        // QC recording pointer stays visible while the shared trail grows.
                        drawQcGestureRecorderPointer(
                            center = Offset(pointerX, pointerY),
                            settings = settings,
                            recorderColor = Color(DefaultGestureRecorderColorArgb),
                            recorderProgress = recorderProgress,
                            ringColor = Color(settings.floatingPointerRingColorArgb),
                            fillColor = Color(settings.floatingPointerFillColorArgb),
                            dotColor = Color(settings.floatingPointerDotColorArgb),
                            visibilityAlpha = 1f,
                            sizeScale = 1f
                        )
                    }
                    showPointer || pointerDrawAlpha.value > 0.001f -> {
                        drawFloatingPointer(
                            center = Offset(pointerX, pointerY),
                            settings = settings,
                            design = pointerDesign,
                            bitmap = pointerBitmap,
                            visibilityAlpha = pointerDrawAlpha.value,
                            sizeScale = pointerSizeScale.value,
                            clickProgress = if (settings.floatingPointerClickVisualFeedbackEnabled) {
                                pointerClickAnim.value
                            } else {
                                0f
                            }
                        )
                    }
                }
                if (!session.awaitingPlacement || joystickActive || radialMenuActive || radialMenuIdle || gestureCaptureActive) {
                    drawQcJoystickDisc(
                        center = Offset(joystickX, joystickY),
                        radiusPx = session.joystickRadiusPx(),
                        innerColor = Color(settings.floatingPointerJoystickInnerColorArgb),
                        outerColor = Color(settings.floatingPointerJoystickOuterColorArgb),
                        gradientRadiusFraction = settings.floatingPointerJoystickGradientRadiusFraction,
                        pressed = joystickActive && !radialMenuActive
                    )
                }
                if (radialMenuProgress > 0.01f) {
                    val radialCenter = if (radialMenuActive || radialMenuIdle) {
                        Offset(session.radialMenuCenterX, session.radialMenuCenterY)
                    } else {
                        Offset(joystickX, joystickY)
                    }
                    val highlightedSlot = if (radialMenuActive) radialHighlightedSlot else -1
                    val radialScale = 0.68f + 0.32f * radialMenuProgress
                    withTransform({
                        translate(radialCenter.x, radialCenter.y)
                        scale(radialScale, radialScale, pivot = Offset.Zero)
                        translate(-radialCenter.x, -radialCenter.y)
                    }) {
                        drawFloatingPointerRadialMenu(
                            context = context,
                            center = radialCenter,
                            settings = settings,
                            slots = settings.floatingPointerRadialSlotActions,
                            highlightedSlot = highlightedSlot,
                            visibilityProgress = radialMenuProgress
                        )
                    }
                }
            }
            }
            injectedSwipePreview
            animationTick
            Canvas(Modifier.fillMaxSize()) {
                val preview = injectedSwipePreview
                if (preview != null) {
                    val drawNow = System.currentTimeMillis()
                    drawInjectedSwipePreview(
                        preview = preview,
                        nowMs = drawNow,
                        colorArgb = settings.floatingPointerTrailColorArgb,
                        strokeWidthPx = settings.floatingPointerDotDiameterPx
                    )
                }
            }
            gestureRecorderTrailRevision
            animationTick
            Canvas(Modifier.fillMaxSize()) {
                val drawNow = System.currentTimeMillis()
                animationTick
                gestureRecorderTrailRevision
                val recorderTrailPoints = session.gestureRecorderTrailPointsForDraw(drawNow)
                if (recorderTrailPoints.size >= 2) {
                    drawGestureRecorderTrail(
                        trailPoints = recorderTrailPoints,
                        color = Color(DefaultGestureRecorderColorArgb),
                        strokeWidthPx = settings.floatingPointerDotDiameterPx
                    )
                }
            }
        }
    }
}

/**
 * 逐帧回调是否还有活要干：只覆盖"没有状态事件驱动、靠时间衰减"的内容（轨迹 / 涟漪 / 手势收尾）。
 * 这些数据必须由帧回调清理并重绘，否则会一直留在屏幕上。
 *
 * Compose 动画（[androidx.compose.animation.core.Animatable]、`animateFloatAsState` 等）
 * 由自身状态失效驱动重绘，不在这里判断。原判据把 `presence < 0.999f`、
 * `radialMenuProgress < 0.999f` 当作"动画中"，但静止时这两个值恰好是 0，条件恒真，
 * 于是空闲的 overlay 也每帧写 `animationTick` → 永久重绘（真机实测：应用退到后台、
 * 无人操作时 40s 仍渲染 1200 帧，约 30fps，主线程 + RenderThread + SurfaceFlinger 全程忙）。
 */
private fun FloatingPointerSession.needsFrameTicker(
    nowMs: Long = System.currentTimeMillis()
): Boolean =
    trailPoints.isNotEmpty() ||
        hasActiveTrail(nowMs) ||
        hasActiveGestureRecorderTrail(nowMs) ||
        hasActiveInjectedSwipePreview(nowMs) ||
        hasPendingGestureAftermath ||
        rippleActive.value ||
        gestureRecordingActive.value ||
        realtimeGestureActive.value ||
        gestureReplayActive.value ||
        gestureTrailRetreatActive.value

/** 空闲时兜底重查周期：只做一次"有没有活要干"的判断，不写状态、不重绘。 */
private const val IDLE_RECHECK_MS = 500L
