package com.slideindex.app.overlay

import androidx.compose.ui.geometry.Offset
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatBallSide

/**
 * Continuous pick-point offset relative to the float-ball center (FV-style).
 * Default above the ball; smoothly transitions below only inside a band at the screen bottom edge.
 */
object FloatBallPickAnchor {
  private const val MIN_SCREEN_MARGIN_DP = 4f
  private const val BOTTOM_EDGE_INSET_DP = 8f
  private const val HORIZONTAL_OFFSET_FRACTION = 0.35f

  fun pickPointForBallCenter(
    settings: AppSettings,
    ballCenterX: Float,
    ballCenterY: Float,
    ballHeightPx: Float,
    screenWidth: Float,
    screenHeight: Float,
    density: Float,
    dockSide: FloatBallSide
  ): Offset {
    val gapPx = settings.floatBallPickOffsetDp.coerceIn(4f, 48f) * density
    val offsetY = verticalOffsetPx(
      settings = settings,
      ballCenterY = ballCenterY,
      ballHeightPx = ballHeightPx,
      screenHeight = screenHeight,
      density = density,
    )
    val offsetX = horizontalOffsetPx(gapPx, dockSide)

    return clampToScreen(
      x = ballCenterX + offsetX,
      y = ballCenterY + offsetY,
      screenWidth = screenWidth,
      screenHeight = screenHeight,
      density = density,
      dockSide = dockSide
    )
  }

  /**
   * 取词十字相对球心的**垂直**偏移（px）：常态在球上方，进入底部过渡带后平滑翻到下方。
   *
   * 单独抽出来是为了让拖动过程中能**逐帧重算**（`FloatBallDragSession` 用它的变化量做补偿），
   * 从而让「贴底过渡区」真正在拖动中生效，而不是只在按下那一刻算一次。
   */
  fun verticalOffsetPx(
    settings: AppSettings,
    ballCenterY: Float,
    ballHeightPx: Float,
    screenHeight: Float,
    density: Float
  ): Float {
    val gapPx = settings.floatBallPickOffsetDp.coerceIn(4f, 48f) * density
    // 取词十字在球的**上方/下方**，所以用球高而不是球宽：不处理模式下球可能很宽但高度不变。
    val aboveOffsetY = -(ballHeightPx / 2f + gapPx)
    val belowOffsetY = ballHeightPx / 2f + gapPx

    val ballBottom = ballCenterY + ballHeightPx / 2f
    val blend = bottomFlipBlend(
      ballBottomY = ballBottom,
      screenHeight = screenHeight,
      transitionBandPx = bottomTransitionBandPx(settings, screenHeight),
      edgeInsetPx = BOTTOM_EDGE_INSET_DP * density
    )
    return aboveOffsetY + (belowOffsetY - aboveOffsetY) * blend
  }

  fun pickPointForFinger(
    fingerX: Float,
    ballCenterY: Float,
    ballHeightPx: Float,
    settings: AppSettings,
    screenWidth: Float,
    screenHeight: Float,
    density: Float,
    dockSide: FloatBallSide
  ): Offset {
    val ballPick = pickPointForBallCenter(
      settings = settings,
      ballCenterX = fingerX,
      ballCenterY = ballCenterY,
      ballHeightPx = ballHeightPx,
      screenWidth = screenWidth,
      screenHeight = screenHeight,
      density = density,
      dockSide = dockSide
    )
    return clampToScreen(
      x = fingerX,
      y = ballPick.y,
      screenWidth = screenWidth,
      screenHeight = screenHeight,
      density = density,
      dockSide = dockSide
    )
  }

  fun horizontalOffsetPx(gapPx: Float, dockSide: FloatBallSide): Float =
    when (dockSide) {
      FloatBallSide.LEFT -> -gapPx * HORIZONTAL_OFFSET_FRACTION
      FloatBallSide.RIGHT -> gapPx * HORIZONTAL_OFFSET_FRACTION
    }

  /** Screen X for pick anchored to the physical left/right edge (matches [clampToScreen]). */
  fun edgePickX(dockSide: FloatBallSide, screenWidth: Float): Float =
    when (dockSide) {
      FloatBallSide.LEFT -> 0f
      FloatBallSide.RIGHT -> screenWidth
    }

  /** Pick edge follows ball center; near screen center keeps [fallbackDockSide]. */
  fun dockSideForBallCenter(
    ballCenterX: Float,
    screenWidth: Float,
    fallbackDockSide: FloatBallSide
  ): FloatBallSide = when {
    ballCenterX < screenWidth * 0.45f -> FloatBallSide.LEFT
    ballCenterX > screenWidth * 0.55f -> FloatBallSide.RIGHT
    else -> fallbackDockSide
  }

  /** Pick above/below [ballCenterY] with X snapped to the dock edge. */
  fun pickPointAtEdge(
    settings: AppSettings,
    ballCenterY: Float,
    ballHeightPx: Float,
    screenWidth: Float,
    screenHeight: Float,
    density: Float,
    dockSide: FloatBallSide
  ): Offset = pickPointForBallCenter(
    settings = settings,
    ballCenterX = edgePickX(dockSide, screenWidth),
    ballCenterY = ballCenterY,
    ballHeightPx = ballHeightPx,
    screenWidth = screenWidth,
    screenHeight = screenHeight,
    density = density,
    dockSide = dockSide
  )

  /**
   * 0 = pick above ball, 1 = pick below ball.
   * Transition only occurs while [ballBottomY] moves through the bottom edge band.
   */
  internal fun bottomFlipBlend(
    ballBottomY: Float,
    screenHeight: Float,
    transitionBandPx: Float,
    edgeInsetPx: Float
  ): Float {
    val bandBottomY = screenHeight - edgeInsetPx
    val bandTopY = (bandBottomY - transitionBandPx).coerceAtLeast(0f)
    val linearT = when {
      ballBottomY <= bandTopY -> 0f
      ballBottomY >= bandBottomY -> 1f
      else -> (ballBottomY - bandTopY) / (bandBottomY - bandTopY)
    }
    return smoothstep(linearT)
  }

  internal fun bottomTransitionBandPx(settings: AppSettings, screenHeight: Float): Float =
    screenHeight * settings.floatBallPickBottomTransitionFraction.coerceIn(0.05f, 0.22f)

  fun clampToScreen(
    x: Float,
    y: Float,
    screenWidth: Float,
    screenHeight: Float,
    density: Float,
    dockSide: FloatBallSide? = null
  ): Offset {
    val margin = MIN_SCREEN_MARGIN_DP * density
    val minX = if (dockSide == FloatBallSide.LEFT) 0f else margin
    val maxX = if (dockSide == FloatBallSide.RIGHT) screenWidth else screenWidth - margin
    return Offset(
      x = x.coerceIn(minX, maxX),
      y = y.coerceIn(margin, screenHeight - margin)
    )
  }

  private fun smoothstep(value: Float): Float {
    val t = value.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
  }
}
