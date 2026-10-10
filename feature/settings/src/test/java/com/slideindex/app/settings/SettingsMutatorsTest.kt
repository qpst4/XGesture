package com.slideindex.app.settings

import android.content.Context
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureRule
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.gesture.TriggerHandle
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.otp.OtpKeywords
import com.slideindex.app.shake.ShakeGestureType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class SettingsMutatorsTest {
    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() {
        repository = testSettingsRepository(RuntimeEnvironment.getApplication())
    }

    private suspend fun awaitSettings(predicate: (AppSettings) -> Boolean = { true }): AppSettings =
        withTimeout(5_000) {
            repository.settings.first(predicate)
        }

    @Test
    fun otpKeywordsRegex_blankFallsBackToDefault() = runBlocking {
        repository.setOtpKeywordsRegex("")

        assertEquals(OtpKeywords.DEFAULT_KEYWORDS_REGEX, awaitSettings { it.otpKeywordsRegex == OtpKeywords.DEFAULT_KEYWORDS_REGEX }.otpKeywordsRegex)
    }

    @Test
    fun otpAutoInputDelayMs_clampsToRange() = runBlocking {
        repository.setOtpAutoInputDelayMs(-100)
        assertEquals(0, awaitSettings { it.otpAutoInputDelayMs == 0 }.otpAutoInputDelayMs)

        repository.setOtpAutoInputDelayMs(9_999)
        assertEquals(5_000, awaitSettings { it.otpAutoInputDelayMs == 5_000 }.otpAutoInputDelayMs)
    }

    @Test
    fun otpAutoInputIntervalMs_clampsToRange() = runBlocking {
        repository.setOtpAutoInputIntervalMs(-1)
        assertEquals(0, awaitSettings { it.otpAutoInputIntervalMs == 0 }.otpAutoInputIntervalMs)

        repository.setOtpAutoInputIntervalMs(1_000)
        assertEquals(500, awaitSettings { it.otpAutoInputIntervalMs == 500 }.otpAutoInputIntervalMs)
    }

    @Test
    fun otpOfficialRuleEnabled_togglesDisabledSet() = runBlocking {
        repository.setOtpOfficialRuleEnabled("rule-a", enabled = false)
        assertTrue(awaitSettings { it.otpDisabledOfficialRuleIds.contains("rule-a") }.otpDisabledOfficialRuleIds.contains("rule-a"))

        repository.setOtpOfficialRuleEnabled("rule-a", enabled = true)
        assertFalse(awaitSettings { !it.otpDisabledOfficialRuleIds.contains("rule-a") }.otpDisabledOfficialRuleIds.contains("rule-a"))
    }

    @Test
    fun triggerEdgeWidthDp_clampsToZeroToThirtySix() = runBlocking {
        repository.setTriggerEdgeWidthDp(PanelSide.LEFT, TriggerHandle.DEFAULT_ID, -2f)
        val left = awaitSettings {
            it.leftTriggerHandles.firstOrNull { handle -> handle.id == TriggerHandle.DEFAULT_ID }?.edgeWidthDp == 0f
        }.leftTriggerHandles.first { it.id == TriggerHandle.DEFAULT_ID }
        assertEquals(0f, left.edgeWidthDp)

        repository.setTriggerEdgeWidthDp(PanelSide.RIGHT, TriggerHandle.DEFAULT_ID, 99f)
        val right = awaitSettings {
            it.rightTriggerHandles.firstOrNull { handle -> handle.id == TriggerHandle.DEFAULT_ID }?.edgeWidthDp == 36f
        }.rightTriggerHandles.first { it.id == TriggerHandle.DEFAULT_ID }
        assertEquals(36f, right.edgeWidthDp)
    }

    @Test
    fun triggerEdgeWidthDp_mirrorsOppositeSideWhenAligned() = runBlocking {
        repository.setTriggerEdgeWidthDp(PanelSide.LEFT, TriggerHandle.DEFAULT_ID, 24f)

        val snapshot = awaitSettings {
            val left = it.leftTriggerHandles.firstOrNull { handle -> handle.id == TriggerHandle.DEFAULT_ID }
            val right = it.rightTriggerHandles.firstOrNull { handle -> handle.id == TriggerHandle.DEFAULT_ID }
            left?.edgeWidthDp == 24f && right?.edgeWidthDp == 24f
        }
        assertEquals(24f, snapshot.leftTriggerHandles.first { it.id == TriggerHandle.DEFAULT_ID }.edgeWidthDp)
        assertEquals(24f, snapshot.rightTriggerHandles.first { it.id == TriggerHandle.DEFAULT_ID }.edgeWidthDp)
    }

    @Test
    fun addTriggerHandlePair_persistsFactorySlotsForNewGroup() = runBlocking {
        // 新触钮的出厂动作必须真正落盘：只进内存的话重启后又会退回"借用 default 组"。
        val handleCountBefore = awaitSettings().leftTriggerHandles.size

        repository.addTriggerHandlePair()

        val snapshot = awaitSettings { it.leftTriggerHandles.size == handleCountBefore + 1 }
        val newId = snapshot.leftTriggerHandles.last().id
        assertTrue(snapshot.gestureRules.any { it.handleId == newId && it.side == PanelSide.LEFT })
        assertEquals(
            GestureAction.Back,
            snapshot.slotAction(PanelSide.LEFT, GestureTriggerType.SHORT_SWIPE_IN, newId),
        )
    }

    @Test
    fun triggerVerticalRange_swapsInvertedBoundsAndMirrorsOppositeSide() = runBlocking {
        repository.setTriggerVerticalRange(
            side = PanelSide.LEFT,
            handleId = TriggerHandle.DEFAULT_ID,
            topFraction = 0.70f,
            bottomFraction = 0.20f,
        )

        val snapshot = awaitSettings {
            val left = it.leftTriggerHandles.firstOrNull { handle -> handle.id == TriggerHandle.DEFAULT_ID }
            left != null && kotlin.math.abs(left.topFraction - 0.20f) < 0.001f
        }
        val left = snapshot.leftTriggerHandles.first { it.id == TriggerHandle.DEFAULT_ID }
        val right = snapshot.rightTriggerHandles.first { it.id == TriggerHandle.DEFAULT_ID }

        assertEquals(0.20f, left.topFraction, 0.001f)
        assertEquals(0.50f, left.heightFraction, 0.001f)
        assertEquals(left.topFraction, right.topFraction, 0.001f)
        assertEquals(left.heightFraction, right.heightFraction, 0.001f)
    }

    @Test
    fun shakeDirectionSensitivity_clampsBetweenOneAndTen() = runBlocking {
        repository.setShakeDirectionSensitivity(ShakeGestureType.LEFT_FLIP, 0f)
        assertEquals(
            1f,
            awaitSettings {
                it.shakeGestureSettings.perDirectionSensitivity[ShakeGestureType.LEFT_FLIP] == 1f
            }.shakeGestureSettings.perDirectionSensitivity.getValue(ShakeGestureType.LEFT_FLIP),
        )

        repository.setShakeDirectionSensitivity(ShakeGestureType.LEFT_FLIP, 99f)
        assertEquals(
            20f,
            awaitSettings {
                it.shakeGestureSettings.perDirectionSensitivity[ShakeGestureType.LEFT_FLIP] == 20f
            }.shakeGestureSettings.perDirectionSensitivity.getValue(ShakeGestureType.LEFT_FLIP),
        )
    }

    @Test
    fun perAppShakeConfig_addAndRemoveRoundTrip() = runBlocking {
        repository.addPerAppShakeConfig("com.example.app")
        assertTrue("com.example.app" in awaitSettings { "com.example.app" in it.shakeGestureSettings.perAppActions }.shakeGestureSettings.perAppActions)

        repository.removePerAppShakeConfig("com.example.app")
        assertFalse("com.example.app" in awaitSettings { "com.example.app" !in it.shakeGestureSettings.perAppActions }.shakeGestureSettings.perAppActions)
    }

    @Test
    fun addFloatingPointerEdgeBarSlot_appendsZones() = runBlocking {
        val initial = awaitSettings().floatingPointerEdgeActionsConfig.top.layoutSlots().size
        repeat(3) {
            repository.addFloatingPointerEdgeBarSlot(FloatingPointerEdgeSide.TOP)
        }
        val updated = awaitSettings {
            it.floatingPointerEdgeActionsConfig.top.layoutSlots().size == initial + 3
        }.floatingPointerEdgeActionsConfig.top.layoutSlots().size
        assertEquals(initial + 3, updated)
    }

    @Test
    fun readSnapshot_reflectsLatestFlowEmission() = runBlocking {
        repository.setServiceEnabled(true)
        awaitSettings { it.serviceEnabled }
        repository.setShakeGesturesEnabled(true)
        awaitSettings { it.shakeGestureSettings.enabled }

        // readSnapshot() 读的是后台 IO 收集器维护的缓存，可能比 flow 的第一个发射晚一拍；
        // 这里等它**有界地**追上（真追不上才失败），避免机器忙时把时序抖动当成断言失败。
        val snapshot = withTimeout(5_000) {
            var current = repository.readSnapshot()
            while (!current.serviceEnabled || !current.shakeGestureSettings.enabled) {
                delay(10)
                current = repository.readSnapshot()
            }
            current
        }
        assertTrue(snapshot.serviceEnabled)
        assertTrue(snapshot.shakeGestureSettings.enabled)
    }

    @Test
    fun withRepairedOppositeGestureSlotsIfNeeded_mirrorsFromSideWithMoreCustomRules() {
        val handleId = TriggerHandle.DEFAULT_ID
        val trigger = GestureTriggerType.SHORT_SWIPE_DOWN_RIGHT
        val desynced = AppSettings(
            edgeTrigger = EdgeTriggerSettings(
                leftTriggerHandles = listOf(TriggerHandle.default().copy(alignOppositeGestures = true)),
                rightTriggerHandles = listOf(TriggerHandle.default().copy(alignOppositeGestures = true)),
            ),
            launcher = LauncherSettings(
                gestureRules = listOf(
                    GestureRule(
                        id = GestureRule.slotId(PanelSide.LEFT, trigger, handleId),
                        side = PanelSide.LEFT,
                        trigger = trigger,
                        action = GestureAction.TaskSwitcher,
                        handleId = handleId,
                    ),
                    GestureRule(
                        id = GestureRule.slotId(PanelSide.RIGHT, trigger, handleId),
                        side = PanelSide.RIGHT,
                        trigger = trigger,
                        action = GestureAction.QuickLauncher("panel-1"),
                        handleId = handleId,
                    ),
                ),
            ),
        )

        val repaired = desynced.withRepairedOppositeGestureSlotsIfNeeded()

        assertEquals(
            GestureAction.TaskSwitcher,
            repaired.actionFor(PanelSide.RIGHT, trigger, handleId),
        )
        assertEquals(
            GestureAction.TaskSwitcher,
            repaired.actionFor(PanelSide.LEFT, trigger, handleId),
        )
    }

    @Test
    fun hasOppositeGestureSlotDesync_detectsAlignedHandleMismatch() {
        val handleId = TriggerHandle.DEFAULT_ID
        val trigger = GestureTriggerType.SHORT_SWIPE_DOWN_RIGHT
        val desynced = AppSettings(
            edgeTrigger = EdgeTriggerSettings(
                leftTriggerHandles = listOf(TriggerHandle.default().copy(alignOppositeGestures = true)),
                rightTriggerHandles = listOf(TriggerHandle.default().copy(alignOppositeGestures = true)),
            ),
            launcher = LauncherSettings(
                gestureRules = listOf(
                    GestureRule(
                        id = GestureRule.slotId(PanelSide.LEFT, trigger, handleId),
                        side = PanelSide.LEFT,
                        trigger = trigger,
                        action = GestureAction.TaskSwitcher,
                        handleId = handleId,
                    ),
                    GestureRule(
                        id = GestureRule.slotId(PanelSide.RIGHT, trigger, handleId),
                        side = PanelSide.RIGHT,
                        trigger = trigger,
                        action = GestureAction.QuickLauncher("panel-1"),
                        handleId = handleId,
                    ),
                ),
            ),
        )

        assertTrue(desynced.hasOppositeGestureSlotDesync())
        assertFalse(desynced.withRepairedOppositeGestureSlotsIfNeeded().hasOppositeGestureSlotDesync())
    }
    @Test
    fun removeFreezerApp_tracksBootstrapExclusion_andAddClearsIt() = runBlocking {
        repository.addFreezerApp("com.example.app")
        repository.removeFreezerApp("com.example.app")

        val afterRemove = awaitSettings {
            it.freezerAppPackages.isEmpty() &&
                it.freezerBootstrapExcludedPackages.contains("com.example.app")
        }
        assertTrue(afterRemove.freezerBootstrapExcludedPackages.contains("com.example.app"))

        repository.addFreezerApp("com.example.app")
        val afterAdd = awaitSettings {
            it.freezerAppPackages.contains("com.example.app") &&
                !it.freezerBootstrapExcludedPackages.contains("com.example.app")
        }
        assertTrue(afterAdd.freezerAppPackages.contains("com.example.app"))
        assertFalse(afterAdd.freezerBootstrapExcludedPackages.contains("com.example.app"))
    }

    @Test
    fun setFreezerWorkMode_persistsPauseAndFreeze() = runBlocking {
        repository.setFreezerWorkMode(FreezerWorkMode.PAUSE.id)
        val paused = awaitSettings { it.freezerWorkMode.isPause }
        assertEquals(FreezerWorkMode.PAUSE, paused.freezerWorkMode)

        repository.setFreezerWorkMode(FreezerWorkMode.FREEZE.id)
        val frozen = awaitSettings { !it.freezerWorkMode.isPause }
        assertEquals(FreezerWorkMode.FREEZE, frozen.freezerWorkMode)
    }

    @Test
    fun setFreezerAppIntent_persistsAndClearsPerPackage() = runBlocking {
        repository.addFreezerApp("com.example.app")
        repository.setFreezerAppIntent("com.example.app", FreezerAppIntent.PAUSE)
        repository.setFreezerAppIntent("com.example.other", FreezerAppIntent.FROZEN)

        val recorded = awaitSettings { it.freezerAppIntents.size == 2 }
        assertEquals(FreezerAppIntent.PAUSE, recorded.freezerAppIntents["com.example.app"])
        assertEquals(FreezerAppIntent.FROZEN, recorded.freezerAppIntents["com.example.other"])

        repository.clearFreezerAppIntents(setOf("com.example.app"))
        val cleared = awaitSettings { it.freezerAppIntents.size == 1 }
        assertEquals(null, cleared.freezerAppIntents["com.example.app"])
    }

    @Test
    fun removeFreezerApp_dropsItsIntent() = runBlocking {
        repository.addFreezerApp("com.example.app")
        repository.setFreezerAppIntent("com.example.app", FreezerAppIntent.PAUSE)
        awaitSettings { it.freezerAppIntents.containsKey("com.example.app") }

        repository.removeFreezerApp("com.example.app")
        val afterRemove = awaitSettings { !it.freezerAppIntents.containsKey("com.example.app") }
        assertFalse(afterRemove.freezerAppIntents.containsKey("com.example.app"))
    }
}

private val testSettingsLock = Any()

internal fun testSettingsRepository(context: Context): SettingsRepository = synchronized(testSettingsLock) {
    val editor = SettingsPreferencesEditor(context)
    return SettingsRepository(
        context = context,
        editor = editor,
        backupManager = SettingsBackupManager(context, editor, NoOpSettingsBackupCloudConfigPort),
        edge = EdgeSettingsMutator(editor, context),
        overlay = OverlaySettingsMutator(editor),
        shake = ShakeSettingsMutator(editor),
        message = MessageSettingsMutator(editor),
        otp = OtpSettingsMutator(editor),
    )
}
