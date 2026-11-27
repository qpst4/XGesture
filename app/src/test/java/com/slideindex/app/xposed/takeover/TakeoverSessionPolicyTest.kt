package com.slideindex.app.xposed.takeover

import com.slideindex.app.xposed.bridge.ModuleHookBridgeContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TakeoverSessionPolicyTest {
  private val rect = TakeoverRect(
    sideId = ModuleHookBridgeContract.SIDE_BOTTOM,
    left = 0f,
    top = 1900f,
    right = 1000f,
    bottom = 2000f,
  )

  @Test
  fun downInsideRegion_startsSwallowingAndForwards() {
    val policy = TakeoverSessionPolicy()

    val decision = policy.onEvent(
      actionMasked = ACTION_DOWN,
      pointerCount = 1,
      x = 500f,
      y = 1950f,
      eventTimeMs = 1_000L,
      injected = false,
      bridgeAvailable = true,
      rects = listOf(rect),
    )

    assertTrue(decision.swallow)
    assertTrue(decision.forwardToApp)
    assertTrue(decision.isSessionStart)
    assertEquals(ModuleHookBridgeContract.SIDE_BOTTOM, decision.sideId)
    assertTrue(policy.hasActiveSession)
  }

  @Test
  fun downOutsideRegion_passesThrough() {
    val policy = TakeoverSessionPolicy()

    val decision = policy.onEvent(
      actionMasked = ACTION_DOWN,
      pointerCount = 1,
      x = 500f,
      y = 800f,
      eventTimeMs = 1_000L,
      injected = false,
      bridgeAvailable = true,
      rects = listOf(rect),
    )

    assertFalse(decision.swallow)
    assertFalse(policy.hasActiveSession)
  }

  @Test
  fun injectedOrBridgeMissingOrMultiTouchDown_passesThrough() {
    val policy = TakeoverSessionPolicy()

    val injected = policy.onEvent(ACTION_DOWN, 1, 500f, 1950f, 1_000L, true, true, listOf(rect))
    val noBridge = policy.onEvent(ACTION_DOWN, 1, 500f, 1950f, 1_000L, false, false, listOf(rect))
    val multiTouch = policy.onEvent(ACTION_DOWN, 2, 500f, 1950f, 1_000L, false, true, listOf(rect))

    assertFalse(injected.swallow)
    assertFalse(noBridge.swallow)
    assertFalse(multiTouch.swallow)
  }

  @Test
  fun moveAndUp_keepSwallowingAndEndSession() {
    val policy = TakeoverSessionPolicy()
    policy.onEvent(ACTION_DOWN, 1, 500f, 1950f, 1_000L, false, true, listOf(rect))

    val move = policy.onEvent(ACTION_MOVE, 1, 500f, 1900f, 1_020L, false, true, listOf(rect))
    val up = policy.onEvent(ACTION_UP, 1, 500f, 1850f, 1_060L, false, true, listOf(rect))

    assertTrue(move.swallow)
    assertTrue(move.forwardToApp)
    assertTrue(up.swallow)
    assertTrue(up.notifyAppEnd)
    assertEquals(TakeoverSessionPolicy.REASON_UP, up.endReason)
    assertFalse(policy.hasActiveSession)
  }

  @Test
  fun secondFinger_detachesAppButKeepsSwallowingUntilUp() {
    val policy = TakeoverSessionPolicy()
    policy.onEvent(ACTION_DOWN, 1, 500f, 1950f, 1_000L, false, true, listOf(rect))

    val pointerDown = policy.onEvent(ACTION_POINTER_DOWN, 2, 600f, 1950f, 1_010L, false, true, listOf(rect))
    val moveWhileDetached = policy.onEvent(ACTION_MOVE, 2, 620f, 1940f, 1_020L, false, true, listOf(rect))
    val up = policy.onEvent(ACTION_UP, 2, 620f, 1940f, 1_040L, false, true, listOf(rect))

    assertTrue(pointerDown.swallow)
    assertFalse(pointerDown.forwardToApp)
    assertTrue(pointerDown.notifyAppEnd)
    assertEquals(TakeoverSessionPolicy.REASON_MULTI_TOUCH, pointerDown.endReason)
    assertTrue(moveWhileDetached.swallow)
    assertFalse(moveWhileDetached.forwardToApp)
    assertTrue(up.swallow)
    assertFalse(policy.hasActiveSession)
  }

  @Test
  fun regionalPick_continuesAfterTheSecondFingerLifts() {
    val policy = TakeoverSessionPolicy()
    policy.onEvent(ACTION_DOWN, 1, 500f, 1950f, 1_000L, false, true, listOf(rect))
    val secondDown = policy.onEvent(ACTION_POINTER_DOWN, 2, 600f, 1950f, 1_010L, false, true, listOf(rect), keepRegionalPick = true)
    val bothMove = policy.onEvent(ACTION_MOVE, 2, 600f, 1950f, 1_020L, false, true, listOf(rect))
    val secondUp = policy.onEvent(ACTION_POINTER_UP, 2, 600f, 1950f, 1_030L, false, true, listOf(rect))
    val primaryMove = policy.onEvent(ACTION_MOVE, 1, 600f, 1950f, 1_040L, false, true, listOf(rect))

    listOf(secondDown, bothMove, secondUp).forEach { decision ->
      assertTrue(decision.swallow)
      assertFalse(decision.forwardToApp)
      assertFalse(decision.notifyAppEnd)
    }
    assertTrue(primaryMove.forwardToApp)
  }

  @Test
  fun regionalPick_abortsWhenThePrimaryFingerLiftsAndSwallowsTheTail() {
    val policy = TakeoverSessionPolicy()
    policy.onEvent(ACTION_DOWN, 1, 500f, 1950f, 1_000L, false, true, listOf(rect))
    policy.onEvent(ACTION_POINTER_DOWN, 2, 600f, 1950f, 1_010L, false, true, listOf(rect), keepRegionalPick = true)
    val primaryUp = policy.onEvent(ACTION_POINTER_UP, 2, 600f, 1950f, 1_020L, false, true, listOf(rect), primaryPointerLifted = true)
    val tail = policy.onEvent(ACTION_UP, 1, 600f, 1950f, 1_030L, false, true, listOf(rect))

    assertTrue(primaryUp.notifyAppEnd)
    assertEquals(TakeoverSessionPolicy.REASON_MULTI_TOUCH, primaryUp.endReason)
    assertTrue(tail.swallow)
    assertFalse(tail.forwardToApp)
  }

  @Test
  fun staleSession_passesThroughAndResets() {
    val policy = TakeoverSessionPolicy(sessionTimeoutMs = 500L)
    policy.onEvent(ACTION_DOWN, 1, 500f, 1950f, 1_000L, false, true, listOf(rect))

    val stale = policy.onEvent(ACTION_MOVE, 1, 500f, 1940f, 2_000L, false, true, listOf(rect))

    assertFalse(stale.swallow)
    assertFalse(policy.hasActiveSession)
  }

  @Test
  fun bridgeLossDuringSession_endsSessionAndPasses() {
    val policy = TakeoverSessionPolicy()
    policy.onEvent(ACTION_DOWN, 1, 500f, 1950f, 1_000L, false, true, listOf(rect))

    val decision = policy.onEvent(ACTION_MOVE, 1, 500f, 1940f, 1_020L, false, false, listOf(rect))

    assertFalse(decision.swallow)
    assertFalse(policy.hasActiveSession)
  }

  private companion object {
    const val ACTION_DOWN = 0
    const val ACTION_UP = 1
    const val ACTION_MOVE = 2
    const val ACTION_POINTER_DOWN = 5
    const val ACTION_POINTER_UP = 6
  }
}
