package com.slideindex.app.xposed.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class ModuleHookSnapshotTest {
  @Test
  fun jsonRoundTrip_restoresManualReservedRegions() {
    val sideId = ModuleHookBridgeContract.SIDE_LEFT
    val region = ModuleHookHandle(topFraction = 0.2f, heightFraction = 0.3f, widthDp = 18f)
    val side = ModuleHookSide(sideId, widthDp = 20f, reservedHandles = listOf(region))
    val snapshot = ModuleHookSnapshot(sides = listOf(side))
    val restored = ModuleHookSnapshot.parse(snapshot.toJson())!!.side(sideId)!!
    assertEquals(listOf(region), restored.reservedHandles)
    assertTrue(restored.handles.isEmpty())
    assertTrue(ModuleHookSnapshot.parse("{\"back_reserved_areas\":15}")!!.sides.isEmpty())
  }

  @Test
  fun jsonRoundTrip_preservesGroupsAndGeometry() {
    val snapshot = ModuleHookSnapshot(
      takeoverGroups = ModuleHookBridgeContract.GROUP_SIDES or ModuleHookBridgeContract.GROUP_BOTTOM,
      interceptSystemBackGesture = true,
      navigationMode = 2,
      density = 2.75f,
      sides = listOf(
        ModuleHookSide(
          sideId = ModuleHookBridgeContract.SIDE_BOTTOM,
          widthDp = 20f,
          handles = listOf(ModuleHookHandle(topFraction = 0.1f, heightFraction = 0.4f, widthDp = 24f)),
        ),
      ),
      updatedAtMs = 1234L,
    )

    val parsed = ModuleHookSnapshot.parse(snapshot.toJson())!!

    assertEquals(snapshot.takeoverGroups, parsed.takeoverGroups)
    assertTrue(parsed.hasGroup(ModuleHookBridgeContract.GROUP_SIDES))
    assertTrue(parsed.hasGroup(ModuleHookBridgeContract.GROUP_BOTTOM))
    assertFalse(parsed.hasGroup(ModuleHookBridgeContract.GROUP_TOP))
    assertTrue(parsed.interceptSystemBackGesture)
    assertEquals(2, parsed.navigationMode)
    assertEquals(2.75f, parsed.density, 0.001f)
    assertEquals(1, parsed.sides.size)
    assertEquals(0.1f, parsed.sides[0].handles[0].topFraction, 0.001f)
    assertEquals(0.5f, parsed.sides[0].handles[0].bottomFraction, 0.001f)
  }

  @Test
  fun parse_rejectsGarbageAndBlank() {
    assertNull(ModuleHookSnapshot.parse(null))
    assertNull(ModuleHookSnapshot.parse(""))
    assertNull(ModuleHookSnapshot.parse("{not json"))
  }

  @Test
  fun parse_defaultsToDisabledWhenKeysMissing() {
    val parsed = ModuleHookSnapshot.parse("{}")!!
    assertEquals(0, parsed.takeoverGroups)
    assertTrue(parsed.sides.isEmpty())
    assertTrue(parsed.extraRects.isEmpty())
    assertEquals(1f, parsed.density, 0.001f)
  }

  @Test
  fun jsonRoundTrip_preservesExtraRects() {
    val snapshot = ModuleHookSnapshot(
      takeoverGroups = ModuleHookBridgeContract.GROUP_BOTTOM,
      extraRects = listOf(
        ModuleHookExtraRect(
          target = ModuleHookBridgeContract.TARGET_CORNER_LEFT,
          groups = ModuleHookBridgeContract.GROUP_BOTTOM,
          leftFraction = 0f,
          topFraction = 0.9f,
          rightFraction = 0.1f,
          bottomFraction = 1f,
          strip = ModuleHookBridgeContract.CORNER_STRIP_HORIZONTAL,
        ),
        ModuleHookExtraRect(
          target = ModuleHookBridgeContract.TARGET_CORNER_RIGHT,
          groups = ModuleHookBridgeContract.GROUP_BOTTOM,
          leftFraction = 0.9f,
          topFraction = 0.9f,
          rightFraction = 1f,
          bottomFraction = 1f,
        ),
      ),
    )

    val parsed = ModuleHookSnapshot.parse(snapshot.toJson())!!

    assertEquals(2, parsed.extraRects.size)
    val left = parsed.extraRects[0]
    assertEquals(ModuleHookBridgeContract.TARGET_CORNER_LEFT, left.target)
    assertEquals(ModuleHookBridgeContract.GROUP_BOTTOM, left.groups)
    assertEquals(0.9f, left.topFraction, 0.001f)
    assertEquals(ModuleHookBridgeContract.CORNER_STRIP_HORIZONTAL, left.strip)
    assertEquals(ModuleHookBridgeContract.CORNER_STRIP_VERTICAL, parsed.extraRects[1].strip)
  }

  /** 旧模块读到 v2 快照时忽略未知键，行为与今天一致（反向兼容）。 */
  @Test
  fun parse_v1SnapshotWithoutExtraRectsStillWorks() {
    val parsed = ModuleHookSnapshot.parse(
      """{"version":1,"groups":2,"nav_mode":2,"density":2.0,"sides":[]}""",
    )!!

    assertEquals(ModuleHookBridgeContract.GROUP_SIDES, parsed.takeoverGroups)
    assertTrue(parsed.extraRects.isEmpty())
  }
}
