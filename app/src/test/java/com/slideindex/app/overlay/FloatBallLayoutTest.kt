package com.slideindex.app.overlay

import android.util.DisplayMetrics
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.EdgeTriggerSettings
import com.slideindex.app.settings.FloatBallPositionMode
import com.slideindex.app.settings.FloatBallSettings
import com.slideindex.app.settings.FloatBallSide
import com.slideindex.app.settings.FloatBallStyleType
import com.slideindex.app.settings.KeyboardTriggerBehavior
import com.slideindex.app.settings.FreeWindowMode
import com.slideindex.app.settings.FreeWindowSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlin.math.max
import kotlin.math.roundToInt
import org.junit.Test

class FloatBallLayoutTest {
  private val metrics = DisplayMetrics().apply {
    density = 3f
    widthPixels = 1080
    heightPixels = 2400
  }

  private fun testSettings(
    floatBall: FloatBallSettings,
    edgeTrigger: EdgeTriggerSettings = EdgeTriggerSettings(),
  ) = AppSettings(
    freeWindow = FreeWindowSettings(freeWindowModeId = FreeWindowMode.STANDARD.id),
    floatBall = floatBall,
    edgeTrigger = edgeTrigger,
  )

  @Test
  fun strip_window_origin_left_places_ball_center_at_target() {
    val settings = testSettings(
      FloatBallSettings(
        floatBallPositionMode = FloatBallPositionMode.LEFT,
        floatBallSizeDp = 48f,
      ),
    )
    val ballSizePx = FloatBallLayout.ballSize(settings, metrics.density).heightPx
    val centerX = ballSizePx / 2f
    val centerY = 1200f

    val (windowX, windowY) = FloatBallLayout.stripWindowOriginForBallCenter(
      settings = settings,
      metrics = metrics,
      activeSide = FloatBallSide.LEFT,
      ballCenterX = centerX,
      ballCenterY = centerY,
    )

    assertEquals(centerX, windowX + ballSizePx / 2f, 0.5f)
    assertEquals(centerY, windowY + ballSizePx / 2f, 0.5f)
  }

  @Test
  fun strip_window_origin_right_places_ball_center_at_target() {
    val settings = testSettings(
      FloatBallSettings(
        floatBallPositionMode = FloatBallPositionMode.RIGHT,
        floatBallSizeDp = 48f,
      ),
    )
    val ballSizePx = FloatBallLayout.ballSize(settings, metrics.density).heightPx
    val centerX = metrics.widthPixels - ballSizePx / 2f
    val centerY = 900f

    val (windowX, windowY) = FloatBallLayout.stripWindowOriginForBallCenter(
      settings = settings,
      metrics = metrics,
      activeSide = FloatBallSide.RIGHT,
      ballCenterX = centerX,
      ballCenterY = centerY,
    )

    assertEquals(centerX, windowX + ballSizePx / 2f, 0.5f)
    assertEquals(centerY, windowY + ballSizePx / 2f, 0.5f)
  }

  @Test
  fun line_strip_matches_trigger_width_dp_and_height_fraction() {
    val settings = testSettings(
      FloatBallSettings(
        floatBallPositionMode = FloatBallPositionMode.BOTH_EDGES,
        floatBallLineWidthDp = 40f,
        floatBallLineHeightFraction = 0.20f,
        floatBallPositionYFraction = 0.5f,
      ),
    )
    val width = FloatBallLayout.lineTriggerWidthPx(settings, metrics.density)
    val height = FloatBallLayout.lineTriggerHeightPx(settings, metrics.heightPixels, metrics.density)
    val expectedWidth = (40f * metrics.density).roundToInt()
    val expectedHeight = max(
      (metrics.heightPixels * 0.20f).roundToInt(),
      (48f * metrics.density).roundToInt(),
    )

    assertEquals(expectedWidth, width)
    assertEquals(expectedHeight, height)
  }

  @Test
  fun line_strip_width_dp_is_clamped_to_settings_range() {
    val tooNarrow = testSettings(
      FloatBallSettings(
        floatBallPositionMode = FloatBallPositionMode.BOTH_EDGES,
        floatBallLineWidthDp = -5f,
      ),
    )
    val zero = testSettings(
      FloatBallSettings(
        floatBallPositionMode = FloatBallPositionMode.BOTH_EDGES,
        floatBallLineWidthDp = 0f,
      ),
    )
    val tooWide = testSettings(
      FloatBallSettings(
        floatBallPositionMode = FloatBallPositionMode.BOTH_EDGES,
        floatBallLineWidthDp = 500f,
      ),
    )

    // 下限 0（只显示线、不接收触摸），上限 96dp；任何越界值都不该把触发带拉成整块屏。
    assertEquals(0, FloatBallLayout.lineTriggerWidthPx(tooNarrow, metrics.density))
    assertEquals(0, FloatBallLayout.lineTriggerWidthPx(zero, metrics.density))
    assertEquals((96f * metrics.density).roundToInt(), FloatBallLayout.lineTriggerWidthPx(tooWide, metrics.density))
  }

  @Test
  fun line_strip_width_is_narrower_than_ball_window() {
    val settings = testSettings(
      FloatBallSettings(
        floatBallPositionMode = FloatBallPositionMode.RIGHT,
        floatBallSizeDp = 64f,
        floatBallLineWidthDp = 24f,
      ),
    )
    val ballSizePx = FloatBallLayout.ballSize(settings, metrics.density).heightPx
    val stripWidth = FloatBallLayout.lineTriggerWidthPx(settings, metrics.density)

    assertTrue(stripWidth < ballSizePx)
  }

  @Test
  fun fully_visible_right_ball_is_flush_with_screen_edge() {
    val settings = testSettings(
      FloatBallSettings(
        floatBallPositionMode = FloatBallPositionMode.RIGHT,
        floatBallSizeDp = 48f,
        floatBallVisibleFraction = 1f,
      ),
    )
    val ballSizePx = FloatBallLayout.ballSize(settings, metrics.density).heightPx
    val (left, _) = FloatBallLayout.ballTopLeft(settings, metrics, FloatBallSide.RIGHT)

    assertEquals(metrics.widthPixels - ballSizePx, left)
  }

  @Test
  fun half_visible_right_ball_extends_past_right_edge() {
    val settings = testSettings(
      FloatBallSettings(
        floatBallPositionMode = FloatBallPositionMode.RIGHT,
        floatBallSizeDp = 64f,
        floatBallVisibleFraction = 0.5f,
      ),
    )
    val ballSizePx = FloatBallLayout.ballSize(settings, metrics.density).heightPx
    val (left, _) = FloatBallLayout.ballTopLeft(settings, metrics, FloatBallSide.RIGHT)
    val expectedLeft = (metrics.widthPixels - ballSizePx * 0.5f).roundToInt()

    assertEquals(expectedLeft, left)
    assertTrue(left + ballSizePx > metrics.widthPixels)
  }

  @Test
  fun keyboardNarrow_onePercent_doesNotClampToHalfVisible() {
    KeyboardTriggerImeState.update(visible = true, top = 1800)
    try {
      val settings = testSettings(
        floatBall = FloatBallSettings(floatBallVisibleFraction = 1f),
        edgeTrigger = EdgeTriggerSettings(
          keyboardTriggerBehaviorPortrait = KeyboardTriggerBehavior.NARROW,
          keyboardTriggerNarrowPercentPortrait = 1,
        ),
      )
      val fraction = FloatBallLayout.keyboardAdjustedVisibleFraction(settings, isLandscape = false)
      assertEquals(0.01f, fraction, 0.0001f)
    } finally {
      KeyboardTriggerImeState.update(visible = false, top = null)
    }
  }

  @Test
  fun half_visible_left_ball_extends_past_left_edge() {
    val settings = testSettings(
      FloatBallSettings(
        floatBallPositionMode = FloatBallPositionMode.LEFT,
        floatBallSizeDp = 64f,
        floatBallVisibleFraction = 0.5f,
      ),
    )
    val ballSizePx = FloatBallLayout.ballSize(settings, metrics.density).heightPx
    val (left, _) = FloatBallLayout.ballTopLeft(settings, metrics, FloatBallSide.LEFT)
    val expectedLeft = (-ballSizePx * 0.5f).roundToInt()

    assertEquals(expectedLeft, left)
    assertTrue(left < 0)
  }

  // —— 自定义图片「不处理」：高度 = 滑块值，宽度 = 高度 × 原图比例，命中区跟着变 ——

  private fun noCropSettings(aspect: Float, sizeDp: Float = 48f) = testSettings(
    FloatBallSettings(
      floatBallPositionMode = FloatBallPositionMode.RIGHT,
      floatBallVisibleFraction = 1f,
      floatBallStyleType = FloatBallStyleType.CUSTOM_IMAGE,
      floatBallCustomImageUri = "file:///float_ball_custom.png",
      floatBallCustomImageNoCrop = true,
      floatBallCustomImageAspect = aspect,
      floatBallSizeDp = sizeDp,
    ),
  )

  @Test
  fun noCrop_keeps_aspect_and_uses_slider_as_height() {
    val settings = noCropSettings(aspect = 2f)
    val size = FloatBallLayout.ballSize(settings, metrics.density)

    assertEquals(144, size.heightPx) // 48dp × 3
    assertEquals(288, size.widthPx) // 高度 × 2
  }

  @Test
  fun noCrop_docked_ball_is_flush_with_edge_and_centered_by_height() {
    val settings = noCropSettings(aspect = 2f)
    val (left, top) = FloatBallLayout.ballTopLeft(settings, metrics, FloatBallSide.RIGHT)
    val size = FloatBallLayout.ballSize(settings, metrics.density)

    assertEquals(metrics.widthPixels - size.widthPx, left)
    // 竖直仍按高度居中：0.55 × 2400 - 144/2
    assertEquals(1248, top)
  }

  @Test
  fun noCrop_without_aspect_falls_back_to_square_window() {
    val settings = noCropSettings(aspect = 0f)
    val size = FloatBallLayout.ballSize(settings, metrics.density)

    assertEquals(144, size.heightPx)
    assertEquals(144, size.widthPx)
  }

  @Test
  fun noCrop_switch_off_or_other_style_keeps_square_ball() {
    val switchOff = noCropSettings(aspect = 2f).let {
      it.copy(floatBall = it.floatBall.copy(floatBallCustomImageNoCrop = false))
    }
    val otherStyle = noCropSettings(aspect = 2f).let {
      it.copy(floatBall = it.floatBall.copy(floatBallStyleType = FloatBallStyleType.SLIDESHOW))
    }

    assertEquals(144, FloatBallLayout.ballSize(switchOff, metrics.density).widthPx)
    assertEquals(144, FloatBallLayout.ballSize(otherStyle, metrics.density).widthPx)
  }
}
