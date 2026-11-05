package com.slideindex.app.shake

import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.LaunchWindowMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 菜单 JSON 编解码。
 *
 * 需要 Robolectric：`org.json` 在本地 JVM 单测里是 android.jar 的桩，
 * 不加 Robolectric 会在 `JSONObject()` 上抛 “not mocked”。
 */
@RunWith(RobolectricTestRunner::class)
class ShakeMenuCodecTest {

    @Test
    fun roundTripKeepsNameAndActions() {
        val menu = ShakeMenu(
            id = "menu-1",
            name = "hhh",
            actions = mapOf(
                ShakeGestureType.LEFT_FLIP to GestureAction.Home,
                ShakeGestureType.RIGHT_FLIP to GestureAction.LaunchApp("com.example.app"),
            ),
        )
        val decoded = ShakeMenuCodec.decode(ShakeMenuCodec.encode(listOf(menu)))
        assertEquals(1, decoded.size)
        assertEquals("menu-1", decoded[0].id)
        assertEquals("hhh", decoded[0].name)
        assertEquals(GestureAction.Home, decoded[0].actions[ShakeGestureType.LEFT_FLIP])
        assertEquals(
            GestureAction.LaunchApp("com.example.app"),
            decoded[0].actions[ShakeGestureType.RIGHT_FLIP],
        )
    }

    @Test
    fun roundTripKeepsLaunchWindowMode() {
        val menu = ShakeMenu(
            id = "menu-2",
            name = "窗口",
            actions = mapOf(
                ShakeGestureType.LEFT_FLICK to GestureAction.LaunchApp(
                    packageName = "com.example.app",
                    windowMode = LaunchWindowMode.ALWAYS_FREE_WINDOW,
                ),
            ),
        )
        val decoded = ShakeMenuCodec.decode(ShakeMenuCodec.encode(listOf(menu))).single()
        val action = decoded.actions[ShakeGestureType.LEFT_FLICK] as GestureAction.LaunchApp
        assertEquals(LaunchWindowMode.ALWAYS_FREE_WINDOW, action.windowMode)
    }

    @Test
    fun encodeSkipsNoneActions() {
        val menu = ShakeMenu(
            id = "menu-3",
            actions = mapOf(
                ShakeGestureType.LEFT_FLIP to GestureAction.Home,
                ShakeGestureType.RIGHT_FLIP to GestureAction.None,
            ),
        )
        val decoded = ShakeMenuCodec.decode(ShakeMenuCodec.encode(listOf(menu))).single()
        assertEquals(setOf(ShakeGestureType.LEFT_FLIP), decoded.actions.keys)
    }

    @Test
    fun decodeBrokenJsonReturnsEmptyList() {
        assertEquals(emptyList<ShakeMenu>(), ShakeMenuCodec.decode("{not json"))
        assertEquals(emptyList<ShakeMenu>(), ShakeMenuCodec.decode(""))
        assertEquals(emptyList<ShakeMenu>(), ShakeMenuCodec.decode(null))
    }

    @Test
    fun decodeDropsEntriesWithoutId() {
        val raw = """
            [
              {"name":"缺 id","actions":[]},
              {"id":"ok","name":"正常","actions":[]}
            ]
        """.trimIndent()
        val decoded = ShakeMenuCodec.decode(raw)
        assertEquals(listOf("ok"), decoded.map { it.id })
    }

    @Test
    fun encodeRespectsMaxMenus() {
        val menus = (0 until ShakeMenuLimits.MAX_MENUS + 3).map { index ->
            ShakeMenu(id = "m-$index", name = "菜单 $index")
        }
        val decoded = ShakeMenuCodec.decode(ShakeMenuCodec.encode(menus))
        assertEquals(ShakeMenuLimits.MAX_MENUS, decoded.size)
    }

    @Test
    fun remapReferenceRewritesOnlyMatchingMenu() {
        val action = GestureAction.OpenShakeMenu("old")
        assertEquals(
            GestureAction.OpenShakeMenu("new"),
            action.remapShakeMenuReference("old", "new"),
        )
        assertEquals(
            GestureAction.None,
            action.remapShakeMenuReference("old", null),
        )
        assertEquals(
            GestureAction.Home,
            GestureAction.Home.remapShakeMenuReference("old", "new"),
        )
        assertTrue(
            GestureAction.OpenShakeMenu("other").remapShakeMenuReference("old", "new") ==
                GestureAction.OpenShakeMenu("other"),
        )
    }
}
