package com.slideindex.app.shake

import com.slideindex.app.gesture.GestureAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShakeActionResolverTest {

    @Test
    fun resolveShakeAction_usesBasicActionByDefault() {
        val shake = ShakeGestureSettings()
        assertEquals(
            GestureAction.Back,
            resolveShakeAction(
                shake,
                ShakeGestureType.LEFT_FLIP,
                "com.example.app",
                lockScreenActive = false,
                screenOn = true,
            ),
        )
    }

    @Test
    fun resolveShakeAction_usesLockScreenAction() {
        val shake = ShakeGestureSettings(
            lockScreenActions = mapOf(ShakeGestureType.LEFT_FLIP to GestureAction.Home),
        )
        assertEquals(
            GestureAction.Home,
            resolveShakeAction(
                shake,
                ShakeGestureType.LEFT_FLIP,
                "com.example.app",
                lockScreenActive = true,
                screenOn = true,
            ),
        )
    }

    @Test
    fun resolveShakeAction_lockScreenNoneDoesNotFallThrough() {
        val shake = ShakeGestureSettings(
            lockScreenActions = mapOf(ShakeGestureType.LEFT_FLIP to GestureAction.None),
            independentAppShakeEnabled = true,
            perAppActions = mapOf(
                "com.example.app" to mapOf(ShakeGestureType.LEFT_FLIP to GestureAction.Recents),
            ),
        )
        assertEquals(
            GestureAction.None,
            resolveShakeAction(
                shake,
                ShakeGestureType.LEFT_FLIP,
                "com.example.app",
                lockScreenActive = true,
                screenOn = true,
            ),
        )
    }

    @Test
    fun resolveShakeAction_usesLockScreenActionWhenScreenOff() {
        val shake = ShakeGestureSettings(
            lockScreenActions = mapOf(ShakeGestureType.LEFT_FLIP to GestureAction.Flashlight),
        )
        assertEquals(
            GestureAction.Flashlight,
            resolveShakeAction(
                shake,
                ShakeGestureType.LEFT_FLIP,
                foregroundPackage = null,
                lockScreenActive = true,
                screenOn = false,
            ),
        )
    }

    @Test
    fun resolveShakeAction_screenOffDoesNotFallBackToBasicAction() {
        val shake = ShakeGestureSettings(
            basicActions = mapOf(ShakeGestureType.FORWARD_FLIP to GestureAction.Flashlight),
        )
        assertEquals(
            GestureAction.None,
            resolveShakeAction(
                shake,
                ShakeGestureType.FORWARD_FLIP,
                foregroundPackage = null,
                lockScreenActive = true,
                screenOn = false,
            ),
        )
    }

    @Test
    fun resolveShakeAction_screenOffExplicitNoneStaysNone() {
        val shake = ShakeGestureSettings(
            basicActions = mapOf(ShakeGestureType.FORWARD_FLIP to GestureAction.Flashlight),
            lockScreenActions = mapOf(ShakeGestureType.FORWARD_FLIP to GestureAction.None),
        )
        assertEquals(
            GestureAction.None,
            resolveShakeAction(
                shake,
                ShakeGestureType.FORWARD_FLIP,
                foregroundPackage = null,
                lockScreenActive = true,
                screenOn = false,
            ),
        )
    }

    @Test
    fun resolveShakeAction_usesPerAppActionWhenIndependentEnabled() {
        val shake = ShakeGestureSettings(
            independentAppShakeEnabled = true,
            perAppActions = mapOf(
                "com.example.app" to mapOf(ShakeGestureType.RIGHT_FLIP to GestureAction.Home),
            ),
        )
        assertEquals(
            GestureAction.Home,
            resolveShakeAction(
                shake,
                ShakeGestureType.RIGHT_FLIP,
                "com.example.app",
                lockScreenActive = false,
                screenOn = true,
            ),
        )
    }

    @Test
    fun resolveShakeAction_perAppNoneDoesNotFallThrough() {
        val shake = ShakeGestureSettings(
            independentAppShakeEnabled = true,
            perAppActions = mapOf(
                "com.example.app" to mapOf(ShakeGestureType.LEFT_FLIP to GestureAction.None),
            ),
        )
        assertEquals(
            GestureAction.None,
            resolveShakeAction(
                shake,
                ShakeGestureType.LEFT_FLIP,
                "com.example.app",
                lockScreenActive = false,
                screenOn = true,
            ),
        )
    }

    @Test
    fun resolveShakeAction_perAppMissingTypeInheritsBasic() {
        val shake = ShakeGestureSettings(
            independentAppShakeEnabled = true,
            basicActions = mapOf(ShakeGestureType.LEFT_FLIP to GestureAction.Home),
            perAppActions = mapOf(
                "com.example.app" to mapOf(ShakeGestureType.RIGHT_FLIP to GestureAction.Recents),
            ),
        )
        assertEquals(
            GestureAction.Home,
            resolveShakeAction(
                shake,
                ShakeGestureType.LEFT_FLIP,
                "com.example.app",
                lockScreenActive = false,
                screenOn = true,
            ),
        )
    }

    @Test
    fun resolveShakeAction_ignoresPerAppWhenForegroundMissing() {
        val shake = ShakeGestureSettings(
            independentAppShakeEnabled = true,
            perAppActions = mapOf(
                "com.example.app" to mapOf(ShakeGestureType.RIGHT_FLIP to GestureAction.Home),
            ),
        )
        assertEquals(
            GestureAction.Recents,
            resolveShakeAction(
                shake,
                ShakeGestureType.RIGHT_FLIP,
                foregroundPackage = null,
                lockScreenActive = false,
                screenOn = true,
            ),
        )
    }

    @Test
    fun resolveShakeAction_ignoresPerAppOnLockScreen() {
        val shake = ShakeGestureSettings(
            independentAppShakeEnabled = true,
            perAppActions = mapOf(
                "com.example.app" to mapOf(ShakeGestureType.LEFT_FLIP to GestureAction.Home),
            ),
            lockScreenActions = mapOf(ShakeGestureType.LEFT_FLIP to GestureAction.Flashlight),
        )
        assertEquals(
            GestureAction.Flashlight,
            resolveShakeAction(
                shake,
                ShakeGestureType.LEFT_FLIP,
                "com.example.app",
                lockScreenActive = true,
                screenOn = true,
            ),
        )
    }

    @Test
    fun workMode_allowsExpectedStates() {
        assertTrue(ShakeWorkModes.ALL.allows(screenOn = true, keyguardLocked = false))
        assertTrue(ShakeWorkModes.ALL.allows(screenOn = true, keyguardLocked = true))
        assertTrue(ShakeWorkModes.ALL.allows(screenOn = false, keyguardLocked = true))

        assertTrue(ShakeWorkModes.DEFAULT.allows(screenOn = true, keyguardLocked = false))
        assertFalse(ShakeWorkModes.DEFAULT.allows(screenOn = true, keyguardLocked = true))
        assertFalse(ShakeWorkModes.DEFAULT.allows(screenOn = false, keyguardLocked = false))

        assertTrue(setOf(ShakeWorkMode.LOCKED_SCREEN_ON).allows(screenOn = true, keyguardLocked = true))
        assertFalse(setOf(ShakeWorkMode.LOCKED_SCREEN_ON).allows(screenOn = true, keyguardLocked = false))
        assertFalse(setOf(ShakeWorkMode.LOCKED_SCREEN_ON).allows(screenOn = false, keyguardLocked = true))

        assertTrue(setOf(ShakeWorkMode.SCREEN_OFF).allows(screenOn = false, keyguardLocked = true))
        assertTrue(setOf(ShakeWorkMode.SCREEN_OFF).allows(screenOn = false, keyguardLocked = false))
        assertFalse(setOf(ShakeWorkMode.SCREEN_OFF).allows(screenOn = true, keyguardLocked = true))

        // 锁屏亮屏 + 解锁：亮屏即生效，熄屏不生效。
        val screenOnModes = setOf(ShakeWorkMode.UNLOCKED, ShakeWorkMode.LOCKED_SCREEN_ON)
        assertTrue(screenOnModes.allows(screenOn = true, keyguardLocked = false))
        assertTrue(screenOnModes.allows(screenOn = true, keyguardLocked = true))
        assertFalse(screenOnModes.allows(screenOn = false, keyguardLocked = true))
        assertFalse(screenOnModes.includesScreenOff)
        assertTrue(setOf(ShakeWorkMode.SCREEN_OFF).includesScreenOff)
    }

    @Test
    fun workModes_encodeDecodeRoundTrip() {
        assertEquals(ShakeWorkModes.ALL, ShakeWorkModes.decode(ShakeWorkModes.encode(ShakeWorkModes.ALL)))
        val mixed = setOf(ShakeWorkMode.UNLOCKED, ShakeWorkMode.SCREEN_OFF)
        assertEquals(mixed, ShakeWorkModes.decode(ShakeWorkModes.encode(mixed)))
        // 空掩码回落到默认的“解锁”。
        assertEquals(ShakeWorkModes.DEFAULT, ShakeWorkModes.decode(0))
    }

    @Test
    fun workModes_legacySingleSelectionMigrates() {
        assertEquals(setOf(ShakeWorkMode.UNLOCKED), ShakeWorkModes.fromLegacyId(0))
        assertEquals(setOf(ShakeWorkMode.SCREEN_OFF), ShakeWorkModes.fromLegacyId(1))
        assertEquals(ShakeWorkModes.ALL, ShakeWorkModes.fromLegacyId(2))
        assertEquals(setOf(ShakeWorkMode.LOCKED_SCREEN_ON), ShakeWorkModes.fromLegacyId(3))
        // 未知 id 按旧版默认值处理。
        assertEquals(ShakeWorkModes.DEFAULT, ShakeWorkModes.fromLegacyId(99))
    }
}
