package com.slideindex.app.settings

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 引擎列表的原子「读 → 改 → 写」。
 *
 * 回归背景：图片分享的批量添加页用的是 Activity 级 ViewModel，且页面不订阅 `settings`；
 * `settings.value` 是 `stateIn(WhileSubscribed)` 的快照，没有订阅者时永远停在旧值。
 * 旧实现拿它当基线拼新列表，每次添加都会把上一次新增的引擎整表覆盖掉（用户看到「只能添加一个」）。
 * 现在基线由 DataStore 事务内部提供，连续两次写入必须都保住。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class SettingsRepositorySearchEngineUpdateTest {
    private lateinit var context: Context
    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() = runBlocking {
        context = RuntimeEnvironment.getApplication()
        clearSettings()
        repository = testSettingsRepository(context)
    }

    @Test
    fun `two consecutive updates keep both engines`() = runBlocking {
        repository.updateSearchEngines { current -> current + engine("added-1") }.getOrThrow()
        repository.updateSearchEngines { current -> current + engine("added-2") }.getOrThrow()

        val engines = repository.readFreshSnapshot().searchEngines
        assertTrue(engines.any { it.name == "added-1" })
        assertTrue(engines.any { it.name == "added-2" })
        assertEquals(engines.size, engines.map { it.id }.distinct().size)
    }

    @Test
    fun `first update starts from the default catalog`() = runBlocking {
        val defaultIds = SearchEngineCatalog.defaultEngines(context).map { it.id }

        repository.updateSearchEngines { current -> current + engine("added") }.getOrThrow()

        val engineIds = repository.readFreshSnapshot().searchEngines.map { it.id }
        assertTrue(engineIds.containsAll(defaultIds))
        assertTrue(engineIds.contains("added"))
    }

    private suspend fun clearSettings() {
        SettingsPreferencesEditor(context).edit { prefs ->
            prefs.asMap().keys.toList().forEach { key -> prefs.remove(key) }
        }
    }

    private fun engine(id: String) = SearchEngineConfig(
        id = id,
        name = id,
        engineType = SearchEngineType.DIRECT_LINK,
    )
}
