package com.slideindex.app.settings

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 引擎图标形状的落盘/读取/投影链路。
 *
 * 面板读的是 [SettingsSnapshotReader]，设置页读的是 OverlaySettings 再投影回 AppSettings；
 * 两条链路都必须带上这个字段，漏一条就会出现"设置页回弹 / 面板不生效"。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class SearchEngineIconShapeSnapshotTest {

    private val context get() = RuntimeEnvironment.getApplication()

    @Test
    fun freshInstall_defaultsToHistoricalShape() {
        val settings = SettingsSnapshotReader.read(emptyPreferences(), context)
        assertEquals(SearchEngineIconShape.DEFAULT, settings.searchEngineIconShape)
    }

    @Test
    fun storedShape_isReadBack() {
        val prefs = mutablePreferencesOf(
            SettingsPreferenceKeys.SEARCH_ENGINE_ICON_SHAPE to SearchEngineIconShape.CIRCLE.name,
        )
        val settings = SettingsSnapshotReader.read(prefs, context)
        assertEquals(SearchEngineIconShape.CIRCLE, settings.searchEngineIconShape)
    }

    @Test
    fun unknownStoredShape_fallsBackToDefault() {
        val prefs = mutablePreferencesOf(
            SettingsPreferenceKeys.SEARCH_ENGINE_ICON_SHAPE to "LEGACY_ROUNDED",
        )
        val settings = SettingsSnapshotReader.read(prefs, context)
        assertEquals(SearchEngineIconShape.DEFAULT, settings.searchEngineIconShape)
    }

    @Test
    fun overlayRoundTrip_keepsIconShape() {
        val app = AppSettings(
            searchPanel = SearchPanelSettings(searchEngineIconShape = SearchEngineIconShape.SQUIRCLE),
        )
        val overlay = OverlaySettings.from(app)
        assertEquals(SearchEngineIconShape.SQUIRCLE, overlay.searchEngineIconShape)
        assertEquals(
            SearchEngineIconShape.SQUIRCLE,
            overlay.toMinimalAppSettings().searchEngineIconShape,
        )
    }
}
