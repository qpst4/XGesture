package com.slideindex.app.xposed.takeover

import com.slideindex.app.gesture.TriggerHandle
import com.slideindex.app.gesture.TriggerHandleCodec
import com.slideindex.app.xposed.bridge.ModuleHookBridgeContract.GROUP_SIDES
import com.slideindex.app.xposed.bridge.ModuleHookBridgeContract.SIDE_LEFT
import com.slideindex.app.xposed.bridge.ModuleHookHandle
import com.slideindex.app.xposed.bridge.ModuleHookSide
import com.slideindex.app.xposed.bridge.ModuleHookSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReservedEdgeGeometryTest {
  @Test
  fun manualRegionUsesItsOwnBoundsEvenWithoutOtherHandles() {
    assertEquals(
      listOf(0.2f to 0.8f),
      ReservedEdgeGeometry.intervals(0.2f, 0.8f, emptyList()),
    )
  }

  @Test
  fun ordinaryHandlesTakePriorityInsideAnOverlappingRegion() {
    assertEquals(
      listOf(0.1f to 0.25f, 0.6f to 0.9f),
      ReservedEdgeGeometry.intervals(0.1f, 0.9f, listOf(0.4f to 0.6f, 0.25f to 0.5f)),
    )
    assertTrue(ReservedEdgeGeometry.intervals(0.1f, 0.4f, listOf(0f to 0.5f)).isEmpty())
  }

  @Test
  fun zeroLengthAndInvalidRegionsHaveNoEffect() {
    assertTrue(ReservedEdgeGeometry.intervals(0.4f, 0.4f, emptyList()).isEmpty())
    assertTrue(ReservedEdgeGeometry.intervals(Float.NaN, 0.8f, emptyList()).isEmpty())
  }

  @Test
  fun handleModeRoundTripsAndOldHandlesRemainOrdinary() {
    val handle = TriggerHandle.default().copy(passthrough = true)
    assertTrue(TriggerHandleCodec.decode(TriggerHandleCodec.encode(handle))!!.passthrough)
    assertFalse(TriggerHandleCodec.decode("old\u001E0.3\u001E0.4\u001E1")!!.passthrough)
  }

  @Test
  fun manualPassthroughRegionsDoNotBecomeGestureTakeoverTargets() {
    val side = ModuleHookSide(
      SIDE_LEFT,
      widthDp = 20f,
      handles = listOf(ModuleHookHandle(0.3f, 0.2f, 20f)),
      reservedHandles = listOf(ModuleHookHandle(0.05f, 0.15f, 20f)),
    )
    val snapshot = ModuleHookSnapshot(takeoverGroups = GROUP_SIDES, sides = listOf(side))
    assertTrue(TakeoverGeometry.isInsideSidesRegion(snapshot, 5f, 350f, 400, 1000))
    assertFalse(TakeoverGeometry.isInsideSidesRegion(snapshot, 5f, 100f, 400, 1000))
  }
}
