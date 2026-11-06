package com.slideindex.app.settings

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 悬浮球「闲置隐身」默认值：开关默认开、闲置不透明度 0.38、闲置判定 3 秒。
 *
 * 默认值在 2fbbc4322（2026-10-31「闲置隐身与闲置停动图默认开启」）被显式改成"开"，
 * 本测试随之对齐。该提交只改了 `?: default`，没有补一次性键迁移，所以**从未写过该键的
 * 存量设备升级后也会变成"闲置 3 秒淡到 38%"**——2026-11-06 维护者确认接受这个行为变化，
 * 不再补迁移（原先"存量用户行为必须完全不变"的约束作废）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class FloatBallIdleHiddenTest {

    private val context get() = RuntimeEnvironment.getApplication()

    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() {
        repository = testSettingsRepository(context)
    }

    @Test
    fun freshInstall_idleHideEnabledByDefault() {
        val settings = SettingsSnapshotReader.read(emptyPreferences(), context)

        assertTrue(settings.floatBallHideWhenIdle)
        assertEquals(0.38f, settings.floatBallIdleOpacity, 0.0001f)
        assertEquals(3000, settings.floatBallIdleDelayMs)
    }

    @Test
    fun explicitValues_arePreserved() {
        val prefs = mutablePreferencesOf(
            SettingsPreferenceKeys.FLOAT_BALL_HIDE_WHEN_IDLE to true,
            SettingsPreferenceKeys.FLOAT_BALL_IDLE_OPACITY to 0.2f,
            SettingsPreferenceKeys.FLOAT_BALL_IDLE_DELAY_MS to 1500,
        )

        val settings = SettingsSnapshotReader.read(prefs, context)

        assertTrue(settings.floatBallHideWhenIdle)
        assertEquals(0.2f, settings.floatBallIdleOpacity, 0.0001f)
        assertEquals(1500, settings.floatBallIdleDelayMs)
    }

    @Test
    fun outOfRangeValues_areClamped() {
        val prefs = mutablePreferencesOf(
            SettingsPreferenceKeys.FLOAT_BALL_IDLE_OPACITY to 3f,
            SettingsPreferenceKeys.FLOAT_BALL_IDLE_DELAY_MS to 99_000,
        )

        val settings = SettingsSnapshotReader.read(prefs, context)

        assertEquals(1f, settings.floatBallIdleOpacity, 0.0001f)
        assertEquals(10_000, settings.floatBallIdleDelayMs)
    }

    @Test
    fun repositoryWrites_roundTrip() = runBlocking {
        assertTrue(repository.setFloatBallHideWhenIdle(true).isSuccess)
        assertTrue(repository.setFloatBallIdleOpacity(0.3f).isSuccess)
        assertTrue(repository.setFloatBallIdleDelayMs(2500).isSuccess)

        val settings = withTimeout(5_000) {
            repository.settings.first { it.floatBallHideWhenIdle }
        }

        assertTrue(settings.floatBallHideWhenIdle)
        assertEquals(0.3f, settings.floatBallIdleOpacity, 0.0001f)
        assertEquals(2500, settings.floatBallIdleDelayMs)
    }
}
