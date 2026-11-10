package com.slideindex.app.settings

import android.content.Context
import com.slideindex.app.shell.ShellCommand
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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class SettingsBackupCodecTest {
    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() {
        runBlocking {
            val context = RuntimeEnvironment.getApplication()
            repository = testSettingsRepository(context)
            clearTestSettings(context)
            repository.setServiceEnabled(true)
            repository.setLeftEdgeEnabled(false)
            repository.setOnboardingCompleted(true)
        }
    }

    /**
     * `readSnapshot()` 读的是 `cacheScope` 里异步更新的缓存，写完立刻读会拿到旧值，
     * 因此这里等 flow 真正发射出期望的快照。
     */
    private suspend fun awaitSettings(predicate: (AppSettings) -> Boolean = { true }): AppSettings =
        withTimeout(5_000) { repository.settings.first(predicate) }

    @Test
    fun exportImport_roundTrip_restoresSettingsWithoutOnboardingFlag() = runBlocking {
        val outStream = ByteArrayOutputStream()
        repository.exportSettings("1.2.0", null, outStream).getOrThrow()

        repository.setServiceEnabled(false)
        repository.setLeftEdgeEnabled(true)
        repository.setOnboardingCompleted(false)

        val inStream = ByteArrayInputStream(outStream.toByteArray())
        val importedCount = repository.importSettings(inStream).getOrThrow().preferencesImported

        assertTrue(importedCount > 0)
        assertTrue(awaitSettings { it.serviceEnabled }.serviceEnabled)
        assertFalse(awaitSettings { !it.leftEdgeEnabled }.leftEdgeEnabled)
        assertFalse(awaitSettings { !it.onboardingCompleted }.onboardingCompleted)
    }

    @Test
    fun exportImport_roundTrip_preservesLongPreferences() = runBlocking {
        repository.setFaceDownHoldDurationMs(1_200L)
        repository.setFaceDownCooldownMs(4_000L)

        val outStream = ByteArrayOutputStream()
        repository.exportSettings("1.2.0", null, outStream).getOrThrow()

        repository.setFaceDownHoldDurationMs(800L)
        repository.setFaceDownCooldownMs(2_000L)

        val inStream = ByteArrayInputStream(outStream.toByteArray())
        repository.importSettings(inStream).getOrThrow()

        val restored = awaitSettings { it.faceDownGestureSettings.holdDurationMs == 1_200L }
        assertEquals(1_200L, restored.faceDownGestureSettings.holdDurationMs)
        assertEquals(4_000L, awaitSettings {
            it.faceDownGestureSettings.cooldownMs == 4_000L
        }.faceDownGestureSettings.cooldownMs)
    }

    @Test
    fun exportImport_includesSearchIconsDirectory() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val iconDir = File(context.filesDir, "search_icons").apply { mkdirs() }
        File(iconDir, "custom-test.png").writeBytes(byteArrayOf(1, 2, 3))

        val outStream = ByteArrayOutputStream()
        repository.exportSettings("1.2.0", null, outStream).getOrThrow()

        iconDir.deleteRecursively()

        val inStream = ByteArrayInputStream(outStream.toByteArray())
        repository.importSettings(inStream).getOrThrow()

        val restored = File(iconDir, "custom-test.png")
        assertTrue(restored.exists())
        assertEquals(3, restored.readBytes().size)
    }

    @Test
    fun exportImport_includesShellAndShortcutIconDirectories() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val shellDir = File(context.filesDir, "shell_icons").apply { mkdirs() }
        val shortcutDir = File(context.filesDir, "shortcut_icons").apply { mkdirs() }
        File(shellDir, "custom-shell.png").writeBytes(byteArrayOf(4, 5, 6))
        File(shortcutDir, "custom-shortcut.png").writeBytes(byteArrayOf(7, 8, 9))

        val outStream = ByteArrayOutputStream()
        repository.exportSettings("1.2.0", null, outStream).getOrThrow()

        shellDir.deleteRecursively()
        shortcutDir.deleteRecursively()

        val inStream = ByteArrayInputStream(outStream.toByteArray())
        repository.importSettings(inStream).getOrThrow()

        assertTrue(File(shellDir, "custom-shell.png").exists())
        assertTrue(File(shortcutDir, "custom-shortcut.png").exists())
    }

    @Test
    fun exportImport_preservesSearchPanelHistoryJson() = runBlocking {
        val historyJson = """[{"query":"test query","createdAtEpochMs":1234567890}]"""
        val sections = SensitiveBackupSections(searchPanelHistoryJson = historyJson)

        val outStream = ByteArrayOutputStream()
        repository.exportSettings("1.2.0", sections, outStream).getOrThrow()

        val inStream = ByteArrayInputStream(outStream.toByteArray())
        val result = repository.importSettings(inStream).getOrThrow()

        assertEquals(historyJson, result.sensitive.searchPanelHistoryJson)
    }

    @Test
    fun importSettings_rejectsUnsupportedFormat() = runBlocking {
        val invalid = """{"formatVersion":99,"exportedAtEpochMs":1,"appVersionName":"1.0","preferences":[]}"""
        val outStream = ByteArrayOutputStream()
        ZipOutputStream(outStream).use { zos ->
            zos.putNextEntry(ZipEntry("settings.json"))
            zos.write(invalid.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        val inStream = ByteArrayInputStream(outStream.toByteArray())

        val result = repository.importSettings(inStream)

        assertTrue(result.isFailure)
    }

    @Test
    fun importSettings_acceptsLegacySearchEngineIconsPath() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val outStream = ByteArrayOutputStream()
        ZipOutputStream(outStream).use { zos ->
            zos.putNextEntry(ZipEntry("settings.json"))
            zos.write(
                """
                {
                  "formatVersion": 2,
                  "exportedAtEpochMs": 1,
                  "appVersionName": "1.0",
                  "preferences": [
                    {"key":"service_enabled","type":"boolean","value":"true"}
                  ]
                }
                """.trimIndent().toByteArray(Charsets.UTF_8),
            )
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("search_engine_icons/legacy.png"))
            zos.write(byteArrayOf(9, 8, 7))
            zos.closeEntry()
        }

        val inStream = ByteArrayInputStream(outStream.toByteArray())
        repository.importSettings(inStream).getOrThrow()

        val restored = File(context.filesDir, "search_icons/legacy.png")
        assertTrue(restored.exists())
        assertEquals(3, restored.readBytes().size)
    }

    /**
     * 选择性导入的回归网：只导入 Shell 模块，未选中的模块必须保持导入前的本地值。
     * 这直接对应「清数据后只恢复健康模块，别把出 bug 的模块带回来」这条诉求。
     */
    @Test
    fun importWithModules_onlyTouchesSelectedModules() = runBlocking {
        repository.setShellCommands(listOf(ShellCommand(label = "backup", command = "echo backup")))
        repository.setLeftEdgeEnabled(true)
        awaitSettings { it.shellCommands.singleOrNull()?.label == "backup" && it.leftEdgeEnabled }

        val outStream = ByteArrayOutputStream()
        repository.exportSettings("1.2.0", null, outStream).getOrThrow()

        repository.setShellCommands(listOf(ShellCommand(label = "local", command = "echo local")))
        repository.setLeftEdgeEnabled(false)
        awaitSettings { it.shellCommands.singleOrNull()?.label == "local" && !it.leftEdgeEnabled }

        val result = repository.importSettings(
            ByteArrayInputStream(outStream.toByteArray()),
            modules = setOf(SettingsModule.SHELL),
        ).getOrThrow()

        assertEquals(1, result.preferencesImported)
        val restored = awaitSettings { it.shellCommands.singleOrNull()?.label == "backup" }
        assertEquals("echo backup", restored.shellCommands.single().command)
        // 未选中的「边缘手势」模块保持本地值 false，不能被备份里的 true 覆盖。
        assertFalse(restored.leftEdgeEnabled)
    }

    @Test
    fun exportImport_roundTrip_preservesExternalStores() = runBlocking {
        val external = mapOf(
            SettingsBackupExternalStore.MACRO.id to """{"macros_v2_json":"[]"}""",
            SettingsBackupExternalStore.STASH_META.id to """{"version":1}""",
        )
        val outStream = ByteArrayOutputStream()
        repository.exportSettings(
            "1.2.0",
            SensitiveBackupSections(externalStores = external),
            outStream,
        ).getOrThrow()

        val result = repository.importSettings(
            ByteArrayInputStream(outStream.toByteArray()),
        ).getOrThrow()

        assertEquals(external, result.sensitive.externalStores)
    }
}

private suspend fun clearTestSettings(context: Context) {
    val editor = SettingsPreferencesEditor(context)
    editor.edit { prefs ->
        prefs.asMap().keys.toList().forEach { key -> prefs.remove(key) }
    }
}
