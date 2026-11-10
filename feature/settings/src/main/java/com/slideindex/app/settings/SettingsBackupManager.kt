package com.slideindex.app.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val editor: SettingsPreferencesEditor,
    private val cloudConfigPort: SettingsBackupCloudConfigPort,
) {
    /**
     * 导出备份。
     *
     * @param modules `null` = 全量（旧行为）；非 null = 只导出这些模块的键/目录/分区。
     *   注意：**部分包不要交给旧版本导入**——旧版本的导入会先清空全部偏好，会把未导出模块一起清掉。
     */
    suspend fun exportToZip(
        appVersionName: String,
        sensitive: SensitiveBackupSections? = null,
        outputStream: OutputStream,
        modules: Set<SettingsModule>? = null,
    ): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            materializeSearchEngineIconsForExport()
            val preferences = editor.readRawPreferences()
            val filteredSections = sensitive?.forModules(modules)
            val json = SettingsBackupCodec.encode(preferences, appVersionName, filteredSections, modules)

            ZipOutputStream(outputStream).use { zos ->
                zos.putNextEntry(ZipEntry("settings.json"))
                zos.write(json.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                if (modules == null || SettingsModuleRegistry.CLOUD_CONFIG_MODULE in modules) {
                    val cloudConfigJson = cloudConfigPort.exportRawJson()
                    if (cloudConfigJson.isNotBlank()) {
                        zos.putNextEntry(ZipEntry(SettingsBackupPaths.VLM_OCR_CONFIG_JSON))
                        zos.write(cloudConfigJson.toByteArray(Charsets.UTF_8))
                        zos.closeEntry()
                    }
                }

                val dirsToBackup = SettingsBackupPaths.dirsForExport(
                    includeSensitiveDirectories = sensitive?.includeDirectories == true,
                ).filter { dir ->
                    modules == null ||
                        SettingsModuleRegistry.moduleOfDirectory(dir)?.let { it in modules } == true
                }
                for (dirName in dirsToBackup) {
                    val dir = File(context.filesDir, dirName)
                    if (dir.exists() && dir.isDirectory) {
                        dir.walkTopDown().forEach { file ->
                            if (file.isFile) {
                                val relativePath = file.relativeTo(context.filesDir).path
                                val entryPath = relativePath.replace(File.separatorChar, '/')
                                zos.putNextEntry(ZipEntry(entryPath))
                                file.inputStream().use { input ->
                                    input.copyTo(zos)
                                }
                                zos.closeEntry()
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 导入备份。
     *
     * @param modules `null` = 全量（旧行为：先清空所有偏好再写回）；
     *   非 null = **只恢复这些模块**——只删这些模块的旧键、只写备份里属于这些模块的键，
     *   其它模块的当前数据原样保留。
     */
    suspend fun importFromZip(
        inputStream: InputStream,
        replaceExisting: Boolean = true,
        modules: Set<SettingsModule>? = null,
    ): Result<SettingsBackupImportResult> = runCatching {
        withContext(Dispatchers.IO) {
            var document: SettingsBackupDocument? = null
            var cloudConfigJson: String? = null
            val importedDirs = mutableSetOf<String>()
            val clearedDirs = mutableSetOf<String>()
            val cloudConfigSelected =
                modules == null || SettingsModuleRegistry.CLOUD_CONFIG_MODULE in modules

            ZipInputStream(inputStream).use { zis ->
                var entry: ZipEntry?
                while (zis.nextEntry.also { entry = it } != null) {
                    val currentEntry = entry ?: continue
                    if (currentEntry.isDirectory) {
                        zis.closeEntry()
                        continue
                    }

                    val name = currentEntry.name
                    when (name) {
                        "settings.json" -> {
                            val json = zis.readBytes().toString(Charsets.UTF_8)
                            val decoded = SettingsBackupCodec.decode(json)
                            SettingsBackupCodec.validate(decoded)
                            document = decoded
                        }
                        SettingsBackupPaths.VLM_OCR_CONFIG_JSON -> {
                            if (cloudConfigSelected) {
                                cloudConfigJson = zis.readBytes().toString(Charsets.UTF_8)
                            }
                        }
                        else -> if (SettingsBackupPaths.isBackupPath(name)) {
                            if (name.contains("..")) {
                                zis.closeEntry()
                                continue
                            }

                            val normalizedName = SettingsBackupPaths.normalizeEntryPath(name)
                            val topLevelDir = SettingsBackupPaths.topLevelDir(normalizedName)
                            val dirModule = topLevelDir?.let { SettingsModuleRegistry.moduleOfDirectory(it) }
                            val dirSelected = modules == null ||
                                (dirModule != null && dirModule in modules)
                            if (!dirSelected) {
                                zis.closeEntry()
                                continue
                            }
                            if (replaceExisting && topLevelDir != null && topLevelDir !in clearedDirs) {
                                File(context.filesDir, topLevelDir).deleteRecursively()
                                clearedDirs += topLevelDir
                            }

                            val targetFile = File(context.filesDir, normalizedName)
                            targetFile.parentFile?.mkdirs()
                            targetFile.outputStream().use { out ->
                                zis.copyTo(out)
                            }
                            topLevelDir?.let { importedDirs += it }
                        }
                    }
                    zis.closeEntry()
                }
            }

            val finalDocument = requireNotNull(document) { "settings.json not found in backup" }
            val filteredPreferences = finalDocument.preferences.filter { entry ->
                modules == null || SettingsModuleRegistry.moduleOf(entry.key) in modules
            }
            val filteredDocument = finalDocument.copy(preferences = filteredPreferences)

            editor.edit { prefs ->
                if (replaceExisting) {
                    prefs.asMap().keys
                        .filter { key ->
                            key.name != SettingsPreferenceKeys.ONBOARDING_COMPLETED.name &&
                                (modules == null || SettingsModuleRegistry.moduleOf(key.name) in modules)
                        }
                        .forEach { key -> prefs.remove(key) }
                }
                SettingsBackupCodec.apply(filteredDocument, prefs)
            }

            cloudConfigJson?.let { raw ->
                cloudConfigPort.importRawJson(raw, replaceExisting = true)
            }

            SettingsBackupImportResult(
                preferencesImported = filteredPreferences.size,
                sensitive = filteredDocument.toOptionalSections().forModules(modules),
                importedClipboardDirectory = "clipboard" in importedDirs,
                importedShareImageOcrHistoryDirectory = "share_image_ocr_history" in importedDirs,
                importedCloudLlmConfig = !cloudConfigJson.isNullOrBlank(),
            )
        }
    }

    /**
     * 只把选中的模块恢复默认值（删掉这些模块的键）。不动文件目录，也不动其它模块。
     *
     * 比「清空整个 App 数据」温和得多，正是「某个功能出 bug、需要清数据」的常用出口。
     * `service_enabled` / `privilege_mode` / 引导标记属于运行前提，任何模块重置都不会碰。
     */
    suspend fun resetModules(modules: Set<SettingsModule>): Result<Int> = runCatching {
        withContext(Dispatchers.IO) {
            require(modules.isNotEmpty()) { "no modules selected" }
            var removed = 0
            editor.edit { prefs ->
                prefs.asMap().keys
                    .filter { key ->
                        key.name !in RESET_PROTECTED_KEYS &&
                            SettingsModuleRegistry.moduleOf(key.name) in modules
                    }
                    .forEach { key ->
                        prefs.remove(key)
                        removed++
                    }
            }
            removed
        }
    }

    suspend fun previewZipImport(inputStream: InputStream): Result<SettingsBackupPreview> = runCatching {
        withContext(Dispatchers.IO) {
            var document: SettingsBackupDocument? = null
            var hasCloudLlmConfig = false
            val importedDirs = mutableSetOf<String>()

            ZipInputStream(inputStream).use { zis ->
                var entry: ZipEntry?
                while (zis.nextEntry.also { entry = it } != null) {
                    val currentEntry = entry ?: continue
                    if (currentEntry.isDirectory) {
                        zis.closeEntry()
                        continue
                    }

                    val name = currentEntry.name
                    when (name) {
                        "settings.json" -> {
                            val json = zis.readBytes().toString(Charsets.UTF_8)
                            val decoded = SettingsBackupCodec.decode(json)
                            SettingsBackupCodec.validate(decoded)
                            document = decoded
                        }
                        SettingsBackupPaths.VLM_OCR_CONFIG_JSON -> hasCloudLlmConfig = true
                        else -> SettingsBackupPaths.topLevelDir(name)?.let { importedDirs += it }
                    }
                    zis.closeEntry()
                }
            }

            val finalDocument = requireNotNull(document) { "settings.json not found in backup" }
            val currentPrefs = editor.readRawPreferences()
            val importDiff = computeSettingsBackupImportDiff(currentPrefs, finalDocument)
            val domains = finalDocument.preferences.map { mapPreferenceKeyToDomain(it.key) }.toSet()
            SettingsBackupPreview(
                formatVersion = finalDocument.formatVersion,
                exportedAtEpochMs = finalDocument.exportedAtEpochMs,
                appVersionName = finalDocument.appVersionName,
                totalPreferencesCount = finalDocument.preferences.size,
                domains = domains,
                moduleSummaries = moduleSummariesOf(finalDocument, importedDirs, hasCloudLlmConfig),
                hasOtpRecords = !finalDocument.otpRecordsJson.isNullOrBlank(),
                hasNotificationHistory = !finalDocument.notificationHistoryJson.isNullOrBlank(),
                hasNotificationFilterRules = !finalDocument.notificationFilterRulesJson.isNullOrBlank(),
                hasNotificationFilterPreferences = !finalDocument.notificationFilterPreferencesJson.isNullOrBlank(),
                hasOtpAutoFillStats = !finalDocument.otpAutoFillStatsJson.isNullOrBlank(),
                hasShellOutputHistory = !finalDocument.shellOutputHistoryJson.isNullOrBlank(),
                hasSearchPanelHistory = !finalDocument.searchPanelHistoryJson.isNullOrBlank(),
                hasClipboardDirectory = "clipboard" in importedDirs,
                hasShareImageOcrHistoryDirectory = "share_image_ocr_history" in importedDirs,
                hasCloudLlmConfig = hasCloudLlmConfig,
                importDiff = importDiff,
            )
        }
    }

    private fun moduleSummariesOf(
        document: SettingsBackupDocument,
        importedDirs: Set<String>,
        hasCloudLlmConfig: Boolean,
    ): Map<SettingsModule, SettingsBackupModuleSummary> {
        val summaries = mutableMapOf<SettingsModule, SettingsBackupModuleSummary>()
        fun put(
            module: SettingsModule,
            settingsCount: Int = 0,
            history: Boolean = false,
            files: Boolean = false,
        ) {
            val old = summaries[module] ?: SettingsBackupModuleSummary()
            summaries[module] = old.copy(
                settingsCount = old.settingsCount + settingsCount,
                hasHistory = old.hasHistory || history,
                hasFiles = old.hasFiles || files,
            )
        }
        document.preferences
            .groupingBy { SettingsModuleRegistry.moduleOf(it.key) }
            .eachCount()
            .forEach { (module, count) -> put(module, settingsCount = count) }
        // 只有历史/文件、没有偏好键的模块也要出现在列表里，否则用户没法单独恢复它。
        importedDirs.mapNotNull { SettingsModuleRegistry.moduleOfDirectory(it) }
            .forEach { module -> put(module, files = true) }
        if (!document.otpRecordsJson.isNullOrBlank() || !document.otpAutoFillStatsJson.isNullOrBlank()) {
            put(SettingsModule.OTP, history = true)
        }
        if (!document.notificationHistoryJson.isNullOrBlank() ||
            !document.notificationFilterRulesJson.isNullOrBlank() ||
            !document.notificationFilterPreferencesJson.isNullOrBlank()
        ) {
            put(SettingsModule.MESSAGE, history = true)
        }
        if (!document.shellOutputHistoryJson.isNullOrBlank()) {
            put(SettingsModule.SHELL, history = true)
        }
        if (!document.searchPanelHistoryJson.isNullOrBlank()) {
            put(SettingsModule.SEARCH, history = true)
        }
        if (hasCloudLlmConfig) {
            put(SettingsModuleRegistry.CLOUD_CONFIG_MODULE, files = true)
        }
        return summaries
    }

    private suspend fun materializeSearchEngineIconsForExport() {
        val prefs = editor.readRawPreferences()
        val initialized = prefs[SettingsPreferenceKeys.SEARCH_ENGINES_INITIALIZED] ?: false
        if (!initialized) return
        val raw = prefs[SettingsPreferenceKeys.SEARCH_ENGINES_JSON]
        val before = SearchEngineStore.decode(context, raw)
        val after = SearchEngineIconMaterializer.materialize(context, before)
        if (!SearchEngineIconMaterializer.needsPersist(before, after)) return
        editor.edit {
            it[SettingsPreferenceKeys.SEARCH_ENGINES_JSON] = SearchEngineStore.encode(after)
        }
    }

    private fun SettingsBackupDocument.toOptionalSections(): SensitiveBackupSections =
        SensitiveBackupSections(
            otpRecordsJson = otpRecordsJson,
            notificationHistoryJson = notificationHistoryJson,
            notificationFilterRulesJson = notificationFilterRulesJson,
            notificationFilterPreferencesJson = notificationFilterPreferencesJson,
            otpAutoFillStatsJson = otpAutoFillStatsJson,
            shellOutputHistoryJson = shellOutputHistoryJson,
            searchPanelHistoryJson = searchPanelHistoryJson,
            multiTouchGestureJson = multiTouchGestureJson,
        )

    /** 只保留选中模块的分区；`modules == null` 时原样返回。 */
    private fun SensitiveBackupSections.forModules(
        modules: Set<SettingsModule>?,
    ): SensitiveBackupSections {
        if (modules == null) return this
        fun keep(module: SettingsModule) = module in modules
        return copy(
            otpRecordsJson = otpRecordsJson.takeIf { keep(SettingsModule.OTP) },
            notificationHistoryJson = notificationHistoryJson.takeIf { keep(SettingsModule.MESSAGE) },
            notificationFilterRulesJson = notificationFilterRulesJson.takeIf { keep(SettingsModule.MESSAGE) },
            notificationFilterPreferencesJson = notificationFilterPreferencesJson.takeIf { keep(SettingsModule.MESSAGE) },
            otpAutoFillStatsJson = otpAutoFillStatsJson.takeIf { keep(SettingsModule.OTP) },
            shellOutputHistoryJson = shellOutputHistoryJson.takeIf { keep(SettingsModule.SHELL) },
            searchPanelHistoryJson = searchPanelHistoryJson.takeIf { keep(SettingsModule.SEARCH) },
            multiTouchGestureJson = multiTouchGestureJson.takeIf { keep(SettingsModule.EDGE_GESTURE) },
        )
    }

    private companion object {
        /** 任何模块重置都不能碰的运行前提键。 */
        val RESET_PROTECTED_KEYS = setOf(
            SettingsPreferenceKeys.ONBOARDING_COMPLETED.name,
            SettingsPreferenceKeys.SERVICE_ENABLED.name,
            SettingsPreferenceKeys.PRIVILEGE_MODE.name,
        )
    }
}
