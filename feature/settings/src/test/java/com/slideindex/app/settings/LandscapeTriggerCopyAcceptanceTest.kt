package com.slideindex.app.settings

import android.content.Context
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureRule
import com.slideindex.app.gesture.GestureRuleCodec
import com.slideindex.app.gesture.GestureTriggerMode
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.gesture.TriggerDesignKind
import com.slideindex.app.gesture.TriggerDesignPreset
import com.slideindex.app.gesture.TriggerHandle
import com.slideindex.app.gesture.TriggerHandleDesign
import com.slideindex.app.gesture.TriggerRectanglePresetState
import com.slideindex.app.overlay.PanelSide
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 验收“复制竖屏设置”：全量覆盖触钮配置，其他页面的配置保持独立。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class LandscapeTriggerCopyAcceptanceTest {
    private lateinit var context: Context
    private lateinit var editor: SettingsPreferencesEditor
    private lateinit var edge: EdgeSettingsMutator

    @Before
    fun setUp() = runBlocking {
        context = RuntimeEnvironment.getApplication()
        editor = SettingsPreferencesEditor(context)
        edge = EdgeSettingsMutator(editor, context)
        editor.edit { it.clear() }.getOrThrow()
    }

    @After
    fun tearDown() = runBlocking {
        editor.edit { it.clear() }.getOrThrow()
    }

    @Test
    fun copy_replacesAllFourSides_includingAppearanceDisabledStateAndGestureRules() = runBlocking {
        seed(profile())
        val before = read()
        edge.copyPortraitToLandscape().getOrThrow()
        assertCopiedProfile(before, SettingsPreferencesEditor(context).settings.first())
    }

    @Test
    fun firstCopy_persistsHandlesAndDefaultTriggerModes_withoutPriorInitialization() = runBlocking {
        seed(profile().let {
            it.copy(edgeTrigger = it.edgeTrigger.copy(landscapeTriggersInitialized = false))
        })
        val before = read()
        edge.copyPortraitToLandscape().getOrThrow()
        assertCopiedProfile(before, read())
    }

    @Test
    fun firstLandscapeVisit_copiesPortraitConfiguration_withoutChangingPositionFractions() = runBlocking {
        seed(profile().let {
            it.copy(edgeTrigger = it.edgeTrigger.copy(
                landscapeTriggersInitialized = false,
                leftTriggerHandlesLandscape = emptyList(), rightTriggerHandlesLandscape = emptyList(),
                bottomTriggerHandlesLandscape = emptyList(), topTriggerHandlesLandscape = emptyList(),
            ))
        })
        val before = read()
        edge.ensureLandscapeTriggerHandlesInitialized().getOrThrow()
        assertCopiedProfile(before, read())
    }

    @Test
    fun copy_clearsLandscapeSides_whenPortraitHasNoHandlesThere() = runBlocking {
        seed(profile().let {
            it.copy(edgeTrigger = it.edgeTrigger.copy(
                leftTriggerHandles = emptyList(), rightTriggerHandles = emptyList(),
                bottomTriggerHandles = emptyList(), topTriggerHandles = emptyList(),
            ))
        })
        edge.copyPortraitToLandscape().getOrThrow()
        edge.ensureLandscapeTriggerHandlesInitialized().getOrThrow()
        val saved = read()
        for (side in PanelSide.entries) {
            assertTrue(saved.storedLandscapeTriggerHandles(side).isEmpty())
            assertTrue(saved.forLandscapeEditing().allTriggerHandles(side).isEmpty())
            assertTrue(saved.runtimeAllTriggerHandles(side, isLandscape = true).isEmpty())
        }
    }

    @Test
    fun copy_preservesPositionFractions_andFullWidthTopHandle_afterReenteringSettings() = runBlocking {
        val initial = profile().let {
            it.copy(edgeTrigger = it.edgeTrigger.copy(
                leftTriggerHandles = listOf(
                    handle("overlapping-a", 0.1f), handle("overlapping-b", 0.2f),
                ),
            ))
        }
        seed(initial)
        val before = read()
        edge.copyPortraitToLandscape().getOrThrow()
        edge.ensureLandscapeTriggerHandlesInitialized().getOrThrow()
        val reopened = read()
        for (side in PanelSide.entries) {
            assertEquals(before.allTriggerHandles(side), reopened.storedLandscapeTriggerHandles(side))
        }
        assertEquals(1f, reopened.topTriggerHandlesLandscape.single().heightFraction)
    }

    @Test
    fun copy_leavesKeyboardAvoidanceAndOtherPagesUnchanged() = runBlocking {
        seed(profile())
        val before = read()
        edge.copyPortraitToLandscape().getOrThrow()
        val after = read()
        assertEquals(before, after.copy(edgeTrigger = before.edgeTrigger))
        assertEquals(before.keyboardTriggerBehaviorPortrait, after.keyboardTriggerBehaviorPortrait)
        assertEquals(before.keyboardTriggerBehaviorLandscape, after.keyboardTriggerBehaviorLandscape)
        assertEquals(before.keyboardTriggerNarrowPercentPortrait, after.keyboardTriggerNarrowPercentPortrait)
        assertEquals(before.keyboardTriggerNarrowPercentLandscape, after.keyboardTriggerNarrowPercentLandscape)
        assertEquals(before.leftTriggerHandles, after.leftTriggerHandles)
        assertEquals(before.gestureRules, after.gestureRules)
    }

    @Test
    fun copy_keepsLegacyPortraitInheritedWidths_whileMigratingLandscapeStorage() = runBlocking {
        seed(profile())
        editor.edit { it.remove(SettingsPreferenceKeys.TRIGGER_EDGE_WIDTH_NULLABLE_MIGRATED) }.getOrThrow()
        val before = read()
        assertEquals(null, before.leftTriggerHandles.first().edgeWidthDp)
        edge.copyPortraitToLandscape().getOrThrow()
        assertCopiedProfile(before, read())
    }

    @Test
    fun firstLandscapeVisit_keepsLegacyPortraitInheritedWidths() = runBlocking {
        seed(profile().let {
            it.copy(edgeTrigger = it.edgeTrigger.copy(
                landscapeTriggersInitialized = false,
                leftTriggerHandlesLandscape = emptyList(), rightTriggerHandlesLandscape = emptyList(),
                bottomTriggerHandlesLandscape = emptyList(), topTriggerHandlesLandscape = emptyList(),
            ))
        })
        editor.edit { it.remove(SettingsPreferenceKeys.TRIGGER_EDGE_WIDTH_NULLABLE_MIGRATED) }.getOrThrow()
        val before = read()
        edge.ensureLandscapeTriggerHandlesInitialized().getOrThrow()
        assertCopiedProfile(before, read())
    }

    @Test
    fun repeatedCopy_usesLatestPortraitProfile_andRemovesLandscapeOnlyHandlesAndRules() = runBlocking {
        seed(profile())
        edge.copyPortraitToLandscape().getOrThrow()
        edge.addBottomTriggerHandle(landscape = true).getOrThrow()
        edge.removeTriggerHandle(PanelSide.LEFT, "portrait-left-b", landscape = false).getOrThrow()
        val before = read()
        edge.copyPortraitToLandscape().getOrThrow()
        assertCopiedProfile(before, read())
    }

    @Test
    fun copiedProfiles_remainIndependent_whenLandscapeHandleAndActionAreEdited() = runBlocking {
        seed(profile())
        val portrait = read()
        edge.copyPortraitToLandscape().getOrThrow()
        edge.removeTriggerHandle(PanelSide.LEFT, "portrait-left-b", landscape = true).getOrThrow()
        edge.setSlotConfig(
            PanelSide.TOP, GestureTriggerType.SHORT_SWIPE_IN, GestureAction.Screenshot,
            GestureTriggerMode.IMMEDIATE, landscape = true,
        ).getOrThrow()
        val edited = read()
        assertEquals(portrait.leftTriggerHandles, edited.leftTriggerHandles)
        assertEquals(portrait.gestureRules, edited.gestureRules)
        assertEquals(listOf("portrait-left-a"), edited.leftTriggerHandlesLandscape.map { it.id })
        assertTrue(edited.gestureRulesLandscape.none { it.handleId == "portrait-left-b" })
        assertEquals(GestureAction.Screenshot, edited.forLandscapeEditing().actionFor(
            PanelSide.TOP, GestureTriggerType.SHORT_SWIPE_IN,
        ))
    }

    @Test
    fun copyWithinAppProfile_usesItsOwnPortraitSettings_andPreservesGlobalAndOtherProfiles() = runBlocking {
        seed(AppSettings())
        val profiles = GestureProfileSettingsMutator(editor)
        val selected = GestureProfile(
            id = "selected", name = "游戏", packageNames = setOf("com.example.game"),
            snapshot = profile().gestureSnapshot(),
        )
        val other = GestureProfile(id = "other", name = "其他")
        profiles.upsertGestureProfile(selected).getOrThrow()
        profiles.upsertGestureProfile(other).getOrThrow()
        val before = read()
        val working = before.materializeGestureProfile(selected.id)
        profiles.setGestureProfileSnapshot(
            selected.id, working.withLandscapeCopiedFromPortrait().extractGestureProfileSnapshot(),
        ).getOrThrow()

        val saved = SettingsPreferencesEditor(context).settings.first()
        assertCopiedProfile(working, saved.materializeGestureProfile(selected.id))
        assertEquals(before.copy(gestureProfiles = saved.gestureProfiles), saved)
        assertEquals(other, saved.gestureProfileById(other.id))
        assertEquals(selected.packageNames, saved.gestureProfileById(selected.id)?.packageNames)
        assertEquals(selected.name, saved.gestureProfileById(selected.id)?.name)
    }

    private fun assertCopiedProfile(source: AppSettings, saved: AppSettings) {
        assertTrue(saved.landscapeTriggersInitialized)
        for (side in PanelSide.entries) {
            assertEquals("$side 的全部触钮配置应覆盖", source.allTriggerHandles(side), saved.storedLandscapeTriggerHandles(side))
            assertEquals(source.defaultTriggerModeFor(side), saved.runtimeDefaultTriggerMode(side, isLandscape = true))
        }
        assertEquals(source.gestureRules, saved.gestureRulesLandscape)
        assertEquals(source.launcher, saved.launcher)
        assertEquals(source.leftTriggerHandles, saved.leftTriggerHandles)
        assertEquals(source.rightTriggerHandles, saved.rightTriggerHandles)
        assertEquals(source.bottomTriggerHandles, saved.bottomTriggerHandles)
        assertEquals(source.topTriggerHandles, saved.topTriggerHandles)
    }

    private fun handle(id: String, top: Float): TriggerHandle {
        val design = TriggerHandleDesign(
            kind = TriggerDesignKind.CONFIGURABLE_RECTANGLE, sizeDp = 5f,
            marginDp = 2f, backgroundColor = 0xFF123456.toInt(),
            cornerRadiusDp = 3f, borderSizeDp = 1f, borderColor = 0xFF654321.toInt(),
            haloSizeDp = 7f, haloColor = 0xFFABCDEF.toInt(),
        )
        return TriggerHandle(
            id, topFraction = top, heightFraction = 0.31f,
            enabled = !id.endsWith("-a"), alignOppositeSide = false,
            alignOppositeDesign = false, alignOppositeGestures = false,
            shortSwipeDistanceDp = 45f, longSwipeDistanceDp = 135f,
            edgeWidthDp = 0f, design = design,
            rectanglePresetState = TriggerRectanglePresetState(
                activePreset = TriggerDesignPreset.BAR,
                savedDesigns = mapOf(TriggerDesignPreset.BAR to design),
            ),
        )
    }

    private fun profile(): AppSettings {
        val rules = listOf(
            GestureRule.slot(PanelSide.LEFT, GestureTriggerType.SHORT_SWIPE_IN,
                GestureAction.Screenshot, GestureTriggerMode.IMMEDIATE, "portrait-left-b"),
            GestureRule.slot(PanelSide.RIGHT, GestureTriggerType.SHORT_SWIPE_IN,
                GestureAction.None, GestureTriggerMode.ON_RELEASE, "portrait-right"),
            GestureRule.slot(PanelSide.BOTTOM, GestureTriggerType.SHORT_SWIPE_IN,
                GestureAction.Home, GestureTriggerMode.ON_RELEASE, "portrait-bottom").copy(enabled = false),
            GestureRule.slot(PanelSide.TOP, GestureTriggerType.SHORT_SWIPE_IN,
                GestureAction.Back, GestureTriggerMode.DEFAULT),
        )
        val extra = listOf(TriggerHandle("landscape-only", 0.4f, 0.2f))
        return AppSettings(
            edgeTrigger = EdgeTriggerSettings(
                leftTriggerHandles = listOf(handle("portrait-left-a", 0.03f), handle("portrait-left-b", 0.65f)),
                rightTriggerHandles = listOf(handle("portrait-right", 0.54f)),
                bottomTriggerHandles = listOf(handle("portrait-bottom", 0.02f).copy(edgeWidthDp = null)),
                topTriggerHandles = listOf(TriggerHandle.topDefault().copy(enabled = true)),
                leftTriggerHandlesLandscape = extra, rightTriggerHandlesLandscape = extra,
                bottomTriggerHandlesLandscape = extra, topTriggerHandlesLandscape = extra,
                landscapeTriggersInitialized = true,
                gestureRulesLandscape = listOf(GestureRule.slot(
                    PanelSide.LEFT, GestureTriggerType.SHORT_SWIPE_IN,
                    GestureAction.Home, handleId = "landscape-only",
                )),
                leftDefaultTriggerMode = GestureTriggerMode.IMMEDIATE,
                rightDefaultTriggerMode = GestureTriggerMode.CONTINUOUS,
                bottomDefaultTriggerMode = GestureTriggerMode.ON_RELEASE,
                topDefaultTriggerMode = GestureTriggerMode.IMMEDIATE,
                keyboardTriggerBehaviorPortrait = KeyboardTriggerBehavior.MOVE_UP,
                keyboardTriggerBehaviorLandscape = KeyboardTriggerBehavior.NARROW,
                keyboardTriggerNarrowPercentPortrait = 23,
                keyboardTriggerNarrowPercentLandscape = 67,
            ),
            launcher = LauncherSettings(gestureRules = rules),
        )
    }

    private suspend fun seed(settings: AppSettings) {
        editor.edit { prefs ->
            SettingsTriggerStore.writeTriggerHandles(prefs, settings)
            SettingsTriggerStore.writeLandscapeSettings(prefs, settings)
            prefs[SettingsPreferenceKeys.GESTURE_RULES] = GestureRuleCodec.encodeAll(settings.gestureRules)
            prefs[SettingsPreferenceKeys.LEFT_DEFAULT_TRIGGER_MODE] = settings.leftDefaultTriggerMode.id
            prefs[SettingsPreferenceKeys.RIGHT_DEFAULT_TRIGGER_MODE] = settings.rightDefaultTriggerMode.id
            prefs[SettingsPreferenceKeys.BOTTOM_DEFAULT_TRIGGER_MODE] = settings.bottomDefaultTriggerMode.id
            prefs[SettingsPreferenceKeys.TOP_DEFAULT_TRIGGER_MODE] = settings.topDefaultTriggerMode.id
            prefs[SettingsPreferenceKeys.KEYBOARD_TRIGGER_BEHAVIOR_PORTRAIT] = settings.keyboardTriggerBehaviorPortrait.storageKey
            prefs[SettingsPreferenceKeys.KEYBOARD_TRIGGER_BEHAVIOR_LANDSCAPE] = settings.keyboardTriggerBehaviorLandscape.storageKey
            prefs[SettingsPreferenceKeys.KEYBOARD_TRIGGER_NARROW_PERCENT_PORTRAIT] = settings.keyboardTriggerNarrowPercentPortrait
            prefs[SettingsPreferenceKeys.KEYBOARD_TRIGGER_NARROW_PERCENT_LANDSCAPE] = settings.keyboardTriggerNarrowPercentLandscape
            prefs[SettingsPreferenceKeys.HAPTIC_ENABLED] = false
        }.getOrThrow()
    }

    private suspend fun read(): AppSettings = editor.settings.first()
}
