package com.slideindex.app.settings

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 悬浮球「拖动悬浮球取词」开关。
 *
 * 默认开 = 历史行为（拖动即进入取词：红十字光标 + 悬停预览 + 松手弹面板）；
 * 只有用户显式关掉才退化成"拖动只跟手"，因此升级不会改变任何人的观感。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class FloatBallDragPickTest {

    private val context get() = RuntimeEnvironment.getApplication()

    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() {
        repository = testSettingsRepository(context)
    }

    @Test
    fun freshInstall_keepsDragPickOn() {
        val settings = SettingsSnapshotReader.read(emptyPreferences(), context)

        assertTrue(settings.floatBallDragPickEnabled)
    }

    @Test
    fun storedOff_isReadBack() {
        val prefs = mutablePreferencesOf(
            SettingsPreferenceKeys.FLOAT_BALL_DRAG_PICK_ENABLED to false,
        )

        assertFalse(SettingsSnapshotReader.read(prefs, context).floatBallDragPickEnabled)
    }

    @Test
    fun repositoryWrite_roundTrips() = runBlocking {
        val write = repository.setFloatBallDragPickEnabled(false)
        assertTrue("写入拖动取词开关失败: $write", write.isSuccess)

        val settings = withTimeout(5_000) {
            repository.settings.first { !it.floatBallDragPickEnabled }
        }

        assertFalse(settings.floatBallDragPickEnabled)
    }
}
