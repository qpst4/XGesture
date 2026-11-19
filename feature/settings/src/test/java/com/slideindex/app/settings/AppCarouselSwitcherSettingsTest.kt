package com.slideindex.app.settings

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.preferencesOf
import org.junit.Assert.assertEquals
import org.junit.Test

class AppCarouselSwitcherSettingsTest {

    @Test
    fun defaults_preserveLegacyBehavior() {
        val defaults = AppCarouselSwitcherSettings()
        // 老用户升级后行为不能变：列表仍是混合来源，「关闭」仍只强停。
        assertEquals(AppCarouselListSource.RECENT_APPS, defaults.listSource)
        assertEquals(TaskSwitcherCloseMode.FORCE_STOP, defaults.closeMode)
        assertEquals(true, defaults.cancelDistanceUpDp > 0)
        assertEquals(true, defaults.cancelDistanceDownDp > 0)
    }

    @Test
    fun fromPreferences_emptyUsesLegacyDefaults() {
        val settings = AppCarouselSwitcherSettings.fromPreferences(preferencesOf())
        assertEquals(AppCarouselListSource.RECENT_APPS, settings.listSource)
        assertEquals(TaskSwitcherCloseMode.FORCE_STOP, settings.closeMode)
    }

    @Test
    fun writeThenRead_roundTripsNewFields() {
        val written = AppCarouselSwitcherSettings(
            listSource = AppCarouselListSource.BACKGROUND_TASKS,
            closeMode = TaskSwitcherCloseMode.BOTH,
        )
        val prefs = mutablePreferencesOf()
        AppCarouselSwitcherSettings.writeToPreferences(written, prefs)

        val read = AppCarouselSwitcherSettings.fromPreferences(prefs)
        assertEquals(AppCarouselListSource.BACKGROUND_TASKS, read.listSource)
        assertEquals(TaskSwitcherCloseMode.BOTH, read.closeMode)
    }

    @Test
    fun fromPreferences_unknownIdsFallBackToDefaults() {
        val prefs = preferencesOf(
            SettingsPreferenceKeys.APP_CAROUSEL_LIST_SOURCE to 99,
            SettingsPreferenceKeys.APP_CAROUSEL_CLOSE_MODE to -3,
        )
        val settings = AppCarouselSwitcherSettings.fromPreferences(prefs)
        assertEquals(AppCarouselListSource.RECENT_APPS, settings.listSource)
        assertEquals(TaskSwitcherCloseMode.FORCE_STOP, settings.closeMode)
    }

    @Test
    fun writeToPreferences_clampsCancelDistances() {
        val prefs = mutablePreferencesOf()
        AppCarouselSwitcherSettings.writeToPreferences(
            AppCarouselSwitcherSettings(cancelDistanceUpDp = 5, cancelDistanceDownDp = 9_999),
            prefs,
        )
        assertEquals(
            AppCarouselSwitcherSettings.MIN_CANCEL_DISTANCE_DP,
            prefs[SettingsPreferenceKeys.APP_CAROUSEL_CANCEL_DISTANCE_UP_DP],
        )
        assertEquals(
            AppCarouselSwitcherSettings.MAX_CANCEL_DISTANCE_DP,
            prefs[SettingsPreferenceKeys.APP_CAROUSEL_CANCEL_DISTANCE_DOWN_DP],
        )
    }
}
