package com.slideindex.app.overlay

import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatBallSettings
import com.slideindex.app.settings.FloatBallSide
import com.slideindex.app.settings.FreeWindowMode
import com.slideindex.app.settings.FreeWindowSettings
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatBallDragSessionTest {
  private val screenHeight = 2400f
  private val screenWidth = 1080f
  private val ballSize = FloatBallSize(144, 144)
  private val density = 3f
  private val marginPx = 24

  private fun testSettings(
    floatBallPickBottomTransitionFraction: Float = 0.22f,
    floatBallPointerSpeedFraction: Float = 0.35f,
    floatBallPointerSpeedVerticalFraction: Float = 0.35f,
    floatBallPointerSlopDp: Float = 8f,
    floatBallPickAccelZoneFraction: Float = 0.5f,
  ): AppSettings = AppSettings(
    freeWindow = FreeWindowSettings(freeWindowModeId = FreeWindowMode.STANDARD.id),
    floatBall = FloatBallSettings(
      floatBallPickBottomTransitionFraction = floatBallPickBottomTransitionFraction,
      floatBallPointerSpeedFraction = floatBallPointerSpeedFraction,
      floatBallPointerSpeedVerticalFraction = floatBallPointerSpeedVerticalFraction,
      floatBallPointerSlopDp = floatBallPointerSlopDp,
      floatBallPickAccelZoneFraction = floatBallPickAccelZoneFraction,
    ),
  )

  /** 光标锚点相对屏幕外的偏移（20dp × density）。 */
  private val anchorOutsidePx = FloatingPointerBounds.PICK_ANCHOR_OUTSIDE_DP * density

  @Test
  fun pointer_mode_keeps_pick_above_ball_at_mid_screen() {
    val session = FloatBallDragSession()
    val settings = testSettings(floatBallPickBottomTransitionFraction = 0.05f)
    val centerY = screenHeight * 0.55f

    session.armAtTouch(
      settings = settings,
      touchDownX = 500f,
      touchDownY = centerY,
      fingerX = 500f,
      fingerY = centerY,
      ballCenterX = 500f,
      ballCenterY = centerY,
      ballSize = ballSize,
      screenWidth = screenWidth,
      screenHeight = screenHeight,
      density = density,
      pickDockSide = FloatBallSide.RIGHT,
    )
    session.onFingerMove(0f, 40f)

    val pick = session.computePick(
      settings = settings,
      ballSize = ballSize,
      screenWidth = screenWidth,
      screenHeight = screenHeight,
      density = density,
      marginPx = marginPx,
    )
    assertTrue(pick.y < centerY)
  }

  @Test
  fun boost_zone_is_twice_the_finger_travel_inside() {
    val travel = 1000f
    val zone = 400f
    val moved = FloatingPointerBounds.pickCursorTravelPx(
      delta = 200f,
      travelPx = travel,
      screenPx = 1080f,
      zonePx = zone,
    )
    assertEquals(400f, moved, 0.01f)
  }

  @Test
  fun boost_zone_switches_to_user_gain_after_the_zone() {
    val travel = 1000f
    val screen = 1080f
    val zone = 400f
    val delta = 700f
    val expected = 2f * zone + (delta - zone) * (screen / travel)
    val moved = FloatingPointerBounds.pickCursorTravelPx(
      delta = delta,
      travelPx = travel,
      screenPx = screen,
      zonePx = zone,
    )
    assertEquals(expected, moved, 0.01f)
  }

  @Test
  fun boost_zone_is_continuous_at_its_boundary() {
    val travel = 1000f
    val screen = 1080f
    val zone = 400f
    val before = FloatingPointerBounds.pickCursorTravelPx(zone - 0.5f, travel, screen, zone)
    val after = FloatingPointerBounds.pickCursorTravelPx(zone + 0.5f, travel, screen, zone)
    // 交界处只换"速度"，不换"位置"：两侧差值应约等于 1px 的手指位移，而不是一次跳变。
    assertTrue(abs(after - before) < 2f)
  }

  @Test
  fun zero_zone_falls_back_to_legacy_linear_mapping() {
    val travel = 1000f
    val screen = 1080f
    val delta = 300f
    val moved = FloatingPointerBounds.pickCursorTravelPx(delta, travel, screen, zonePx = 0f)
    assertEquals(delta * (screen / travel), moved, 0.01f)
  }

  @Test
  fun lowest_speed_reaches_the_opposite_edge_thanks_to_the_boost_zone() {
    val travelPx = 0.95f * screenWidth
    val zonePx = FloatingPointerBounds.pickAccelZonePx(0.5f, travelPx)
    val thumbReach = 0.6f * screenWidth

    val movedWithZone = FloatingPointerBounds.pickCursorTravelPx(
      delta = -thumbReach,
      travelPx = travelPx,
      screenPx = screenWidth,
      zonePx = zonePx,
    )
    val movedWithoutZone = FloatingPointerBounds.pickCursorTravelPx(
      delta = -thumbReach,
      travelPx = travelPx,
      screenPx = screenWidth,
      zonePx = 0f,
    )
    // 0% 档 + 加速区：半屏多的手指行程就够跨完整个屏幕；关掉加速区则不够（这就是原来的投诉）。
    assertTrue(abs(movedWithZone) >= screenWidth + anchorOutsidePx)
    assertTrue(abs(movedWithoutZone) < screenWidth)
  }

  @Test
  fun right_docked_pick_anchor_starts_outside_the_right_edge() {
    val session = FloatBallDragSession()
    val settings = testSettings()
    val centerY = screenHeight * 0.55f
    val fingerX = screenWidth - 36f
    val ballCenterX = screenWidth - ballSize.widthPx / 2f

    session.armAtTouch(
      settings = settings,
      touchDownX = fingerX,
      touchDownY = centerY,
      fingerX = fingerX,
      fingerY = centerY,
      ballCenterX = ballCenterX,
      ballCenterY = centerY,
      ballSize = ballSize,
      screenWidth = screenWidth,
      screenHeight = screenHeight,
      density = density,
      pickDockSide = FloatBallSide.RIGHT,
    )

    val pick = session.computePick(
      settings = settings,
      ballSize = ballSize,
      screenWidth = screenWidth,
      screenHeight = screenHeight,
      density = density,
      marginPx = marginPx,
    )
    // 起点在屏幕外：红十字第一帧不可见，靠拖进来出场（避免"贴边出现然后跳"）。
    assertEquals(screenWidth + anchorOutsidePx, pick.x, 0.5f)
  }

  /** 纵向倍率拉到最大时，红十字也不许掉到手指下面（这是「贴底过渡区」的核心语义）。 */
  @Test
  fun cross_never_drops_below_the_finger_even_with_high_vertical_gain() {
    val session = FloatBallDragSession()
    val settings = testSettings(floatBallPointerSpeedVerticalFraction = 0.75f)
    val startY = screenHeight * 0.55f
    session.armAtTouch(
      settings = settings,
      touchDownX = 500f,
      touchDownY = startY,
      fingerX = 500f,
      fingerY = startY,
      ballCenterX = 500f,
      ballCenterY = startY,
      ballSize = ballSize,
      screenWidth = screenWidth,
      screenHeight = screenHeight,
      density = density,
      pickDockSide = FloatBallSide.RIGHT,
    )
    val dragDy = 400f
    session.onFingerMove(0f, dragDy)
    val pick = session.computePick(
      settings = settings,
      ballSize = ballSize,
      screenWidth = screenWidth,
      screenHeight = screenHeight,
      density = density,
      marginPx = marginPx,
    )
    val fingerY = startY + dragDy
    val marginPx = FloatingPointerBounds.PICK_FINGER_MARGIN_DP * density
    // 手指还在屏幕中部（没进底部过渡带）⇒ 红十字被压在手指上方。
    assertTrue(pick.y <= fingerY - marginPx + 0.5f)
  }

  /** 手指进到底部过渡带后，红十字才允许翻到手指下方。 */
  @Test
  fun cross_may_drop_below_the_finger_inside_the_bottom_band() {
    val session = FloatBallDragSession()
    val settings = testSettings(floatBallPointerSpeedVerticalFraction = 0.75f)
    val startY = screenHeight * 0.55f
    session.armAtTouch(
      settings = settings,
      touchDownX = 500f,
      touchDownY = startY,
      fingerX = 500f,
      fingerY = startY,
      ballCenterX = 500f,
      ballCenterY = startY,
      ballSize = ballSize,
      screenWidth = screenWidth,
      screenHeight = screenHeight,
      density = density,
      pickDockSide = FloatBallSide.RIGHT,
    )
    // 让手指落到屏高 83% 处（22% 带子的上沿之内）
    val dragDy = screenHeight * 0.83f - startY
    session.onFingerMove(0f, dragDy)
    val pick = session.computePick(
      settings = settings,
      ballSize = ballSize,
      screenWidth = screenWidth,
      screenHeight = screenHeight,
      density = density,
      marginPx = marginPx,
    )
    val fingerY = startY + dragDy
    val marginPx = FloatingPointerBounds.PICK_FINGER_MARGIN_DP * density
    assertTrue(pick.y > fingerY - marginPx)
  }

  /** 贴底过渡区 = 0（关闭）：不做上限，红十字可以随便跑到手指下方（旧手感）。 */
  @Test
  fun bottom_transition_disabled_lets_cross_go_below_the_finger() {
    val session = FloatBallDragSession()
    val settings = testSettings(
      floatBallPointerSpeedVerticalFraction = 0.75f,
      floatBallPickBottomTransitionFraction = 0f,
    )
    val startY = screenHeight * 0.55f
    session.armAtTouch(
      settings = settings,
      touchDownX = 500f,
      touchDownY = startY,
      fingerX = 500f,
      fingerY = startY,
      ballCenterX = 500f,
      ballCenterY = startY,
      ballSize = ballSize,
      screenWidth = screenWidth,
      screenHeight = screenHeight,
      density = density,
      pickDockSide = FloatBallSide.RIGHT,
    )
    val dragDy = 200f
    session.onFingerMove(0f, dragDy)
    val pick = session.computePick(
      settings = settings,
      ballSize = ballSize,
      screenWidth = screenWidth,
      screenHeight = screenHeight,
      density = density,
      marginPx = marginPx,
    )
    // 手指还在屏幕中部，但 5 倍速下十字已经跑到手指下方。
    assertTrue(pick.y > startY + dragDy)
  }

  /** 进入过渡区后一次性解除上限：位置连续、速度立刻回到用户设置的倍率。 */
  @Test
  fun release_in_bottom_band_restores_user_gain_without_jump() {
    val session = FloatBallDragSession()
    val settings = testSettings(floatBallPointerSpeedVerticalFraction = 0.75f)
    val startY = screenHeight * 0.55f
    session.armAtTouch(
      settings = settings,
      touchDownX = 500f,
      touchDownY = startY,
      fingerX = 500f,
      fingerY = startY,
      ballCenterX = 500f,
      ballCenterY = startY,
      ballSize = ballSize,
      screenWidth = screenWidth,
      screenHeight = screenHeight,
      density = density,
      pickDockSide = FloatBallSide.RIGHT,
    )

    fun pickY(): Float = session.computePick(
      settings = settings,
      ballSize = ballSize,
      screenWidth = screenWidth,
      screenHeight = screenHeight,
      density = density,
      marginPx = marginPx,
    ).y

    val band = 0.22f * screenHeight
    val bandTop = screenHeight - band
    val marginPx = FloatingPointerBounds.PICK_FINGER_MARGIN_DP * density

    // 拖到带子上沿下方 1px：这一帧触发"解除上限"，位置必须等于原来的上限（连续，不跳）。
    session.onFingerMove(0f, bandTop + 1f - startY)
    val yAtRelease = pickY()
    val floorAtRelease = FloatingPointerBounds.pickCursorFloorY(
      fingerY = bandTop + 1f,
      screenHeight = screenHeight,
      bandFraction = 0.22f,
      marginPx = marginPx,
    )
    assertEquals(floorAtRelease, yAtRelease, 1f)

    // 再往下拖 50px：解除之后应按用户倍率（0.75 → 5×）继续，而不是被限制线拖着。
    session.onFingerMove(0f, 50f)
    val gain = 1f / FloatingPointerBounds.travelFractionFromSpeed(0.75f)
    assertEquals(50f * gain, pickY() - yAtRelease, 1.5f)
  }

  /** 往上拖不设上限：纵向倍率全额生效。 */
  @Test
  fun cross_keeps_full_gain_when_dragging_upwards() {
    val session = FloatBallDragSession()
    val settings = testSettings(floatBallPointerSpeedVerticalFraction = 0.75f)
    val startY = screenHeight * 0.55f
    session.armAtTouch(
      settings = settings,
      touchDownX = 500f,
      touchDownY = startY,
      fingerX = 500f,
      fingerY = startY,
      ballCenterX = 500f,
      ballCenterY = startY,
      ballSize = ballSize,
      screenWidth = screenWidth,
      screenHeight = screenHeight,
      density = density,
      pickDockSide = FloatBallSide.RIGHT,
    )
    val yAtArm = session.computePick(
      settings = settings,
      ballSize = ballSize,
      screenWidth = screenWidth,
      screenHeight = screenHeight,
      density = density,
      marginPx = marginPx,
    ).y
    // 只走 150px：5 倍速下十字上移 750px，仍停在屏幕内（不触发夹取），才能验证上限没生效。
    val dragDy = -150f
    session.onFingerMove(0f, dragDy)
    val yAfter = session.computePick(
      settings = settings,
      ballSize = ballSize,
      screenWidth = screenWidth,
      screenHeight = screenHeight,
      density = density,
      marginPx = marginPx,
    ).y

    val travel = (FloatingPointerBounds.SENSITIVITY_MIN + FloatingPointerBounds.SENSITIVITY_MAX -
      settings.floatBallPointerSpeedVerticalFraction) * screenHeight
    val movedByFinger = FloatingPointerBounds.pickCursorTravelPx(
      delta = dragDy,
      travelPx = travel,
      screenPx = screenHeight,
      zonePx = 0f,
    )
    assertEquals(movedByFinger, yAfter - yAtArm, 1f)
  }

  @Test
  fun line_drag_pick_snaps_to_ball_side_not_opposite_edge() {
    val session = FloatBallDragSession()
    val settings = testSettings()
    val centerY = screenHeight * 0.55f

    session.armAtTouch(
      settings = settings,
      touchDownX = 36f,
      touchDownY = centerY,
      fingerX = 36f,
      fingerY = centerY,
      ballCenterX = 80f,
      ballCenterY = centerY,
      ballSize = ballSize,
      screenWidth = screenWidth,
      screenHeight = screenHeight,
      density = density,
      pickDockSide = FloatBallSide.LEFT,
    )

    val pick = session.computePick(
      settings = settings,
      ballSize = ballSize,
      screenWidth = screenWidth,
      screenHeight = screenHeight,
      density = density,
      marginPx = marginPx,
    )
    assertEquals(-anchorOutsidePx, pick.x, 0.5f)
  }

  @Test
  fun drag_maintains_dock_side_anchor_without_jumping_to_opposite_edge() {
    val session = FloatBallDragSession()
    val settings = testSettings(floatBallPointerSlopDp = 32f)
    val centerY = screenHeight * 0.55f

    session.armAtTouch(
      settings = settings,
      touchDownX = screenWidth - 36f,
      touchDownY = centerY,
      fingerX = screenWidth - 36f,
      fingerY = centerY,
      ballCenterX = screenWidth - ballSize.widthPx / 2f,
      ballCenterY = centerY,
      ballSize = ballSize,
      screenWidth = screenWidth,
      screenHeight = screenHeight,
      density = density,
      pickDockSide = FloatBallSide.RIGHT,
    )
    session.updateFingerPosition(screenWidth - 36f - 50f, centerY)

    val pick = session.computePick(
      settings = settings,
      ballSize = ballSize,
      screenWidth = screenWidth,
      screenHeight = screenHeight,
      density = density,
      marginPx = marginPx,
    )
    // Pointer smoothly moves left from right edge (1080) towards left, without flipping dock anchor to 0
    assertTrue(pick.x < screenWidth)
    assertTrue(pick.x > 800f)
  }

  @Test
  fun lag_jump_absolute_finger_position_aligns_ball_with_finger() {
    val session = FloatBallDragSession()
    val settings = testSettings()
    val centerY = screenHeight * 0.55f
    val touchDownX = screenWidth - 36f
    val ballCenterX = screenWidth - ballSize.widthPx / 2f

    // 1. Arm at initial touch down
    session.armAtTouch(
      settings = settings,
      touchDownX = touchDownX,
      touchDownY = centerY,
      fingerX = touchDownX,
      fingerY = centerY,
      ballCenterX = ballCenterX,
      ballCenterY = centerY,
      ballSize = ballSize,
      screenWidth = screenWidth,
      screenHeight = screenHeight,
      density = density,
      pickDockSide = FloatBallSide.RIGHT,
    )

    // 2. Under heavy load, first move jumps 200px to the left in one single event
    val jumpedFingerX = touchDownX - 200f
    session.updateFingerPosition(jumpedFingerX, centerY)

    val center = session.ballCenter()
    val expectedCenterX = jumpedFingerX + (ballCenterX - touchDownX)
    assertEquals(expectedCenterX, center.x, 0.001f)
    assertEquals(centerY, center.y, 0.001f)
  }

  @Test
  fun joystick_offset_uses_touch_down_not_slop_finger_position() {
    val session = FloatBallDragSession()
    val settings = testSettings()
    val ballCenterX = 500f
    val ballCenterY = screenHeight * 0.55f
    val touchDownX = ballCenterX
    val touchDownY = ballCenterY
    val slopFingerX = touchDownX + 24f
    val slopFingerY = touchDownY + 24f

    session.armAtTouch(
      settings = settings,
      touchDownX = touchDownX,
      touchDownY = touchDownY,
      fingerX = slopFingerX,
      fingerY = slopFingerY,
      ballCenterX = ballCenterX,
      ballCenterY = ballCenterY,
      ballSize = ballSize,
      screenWidth = screenWidth,
      screenHeight = screenHeight,
      density = density,
      pickDockSide = FloatBallSide.RIGHT,
    )

    val center = session.ballCenter()
    assertEquals(slopFingerX, center.x, 0.5f)
    assertEquals(slopFingerY, center.y, 0.5f)
  }
}
