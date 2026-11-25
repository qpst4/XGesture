package com.slideindex.app.search

import android.content.Context
import android.net.Uri
import com.slideindex.app.link.LinkGoRuleConverter
import com.slideindex.app.settings.SearchEngineConfig
import com.slideindex.app.settings.SearchEngineStore
import com.slideindex.app.settings.SearchEngineType
import com.slideindex.app.settings.SearchGroupDefaults
import com.slideindex.app.settings.SearchGroupKind
import com.slideindex.app.settings.SearchIconType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream

data class SearchEngineImportResult(
    val importedCount: Int,
    val skippedCount: Int,
    val sourceLabel: String,
    val mergedEngines: List<SearchEngineConfig>
)

object SearchEngineImporter {
    private val json = Json { ignoreUnknownKeys = true }

    fun importFromUri(
        context: Context,
        uri: Uri,
        existing: List<SearchEngineConfig>,
        replaceExisting: Boolean
    ): Result<SearchEngineImportResult> = runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("cannot_read_file")
        val name = uri.lastPathSegment.orEmpty().lowercase()
        when {
            isZipArchive(name, bytes) -> importZipBytes(context, bytes, existing, replaceExisting)
            else -> importJsonBytes(context, bytes.decodeToString(), existing, replaceExisting)
        }
    }

    /**
     * 粘贴 JSON 导入：本应用信封、一墨/LinkGo `search_engines`、Gevo 导出格式都走这里。
     */
    fun importFromJsonText(
        context: Context,
        raw: String,
        existing: List<SearchEngineConfig>,
        replaceExisting: Boolean,
    ): Result<SearchEngineImportResult> = runCatching {
        val imported = parseJsonText(raw)
        finalizeImport(context, existing, replaceExisting, imported, emptyMap(), sourceLabel(raw))
    }

    /**
     * 只解析、不落盘；供导入预览 BottomSheet 使用。
     *
     * 组的 groupMemberIds 仍指向同一份 JSON 里的成员 id，合并阶段再统一 remap。
     */
    fun parseJsonText(raw: String): List<SearchEngineConfig> {
        if (raw.isBlank()) error("empty_json")
        if (raw.length > MAX_JSON_TEXT_LENGTH) error("json_too_large")
        val native = SearchEngineShareCodec.decode(raw)
        return when {
            native != null -> native
            looksLikeLinkGoJson(raw) -> parseLinkGoSearchEngines(raw)
            else -> parseGevoExportJson(raw)
        }
    }

    /** 导入已解析、已勾选的引擎列表，组的成员 id 会在合并阶段 remap。 */
    fun importParsed(
        context: Context,
        imported: List<SearchEngineConfig>,
        existing: List<SearchEngineConfig>,
        replaceExisting: Boolean,
    ): Result<SearchEngineImportResult> = runCatching {
        finalizeImport(context, existing, replaceExisting, imported, emptyMap(), "JSON")
    }

    private const val MAX_JSON_TEXT_LENGTH = 1_000_000

    private fun sourceLabel(raw: String): String = when {
        raw.contains("\"search_engines\"") -> "LinkGo"
        raw.contains("\"entries\"") -> "GestureEVO"
        else -> "JSON"
    }

    private fun looksLikeLinkGoJson(raw: String): Boolean =
        raw.contains("\"search_engines\"")

    /**
     * 一墨/LinkGo 的组格式：
     *
     * {"search_engines":[{"id":0,"type":1,"name":"#",
     *   "configJson":"{\"members\":[1,2,3],\"intervalMillis\":800}"}]}
     *
     * 组里的 members 是数字 id，必须和同一份 JSON 里的普通引擎 id 对上；
     * 只有组、没有成员时返回空列表，调用方应提示缺少成员，不能造空组。
     */
    fun parseLinkGoSearchEngines(raw: String): List<SearchEngineConfig> {
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
            ?: return emptyList()
        val array = root["search_engines"]?.jsonArray ?: return emptyList()
        val idMap = linkedMapOf<String, String>()
        val result = mutableListOf<SearchEngineConfig>()
        array.filterNot { it.jsonObject.isLinkGoGroup() }.forEachIndexed { index, item ->
            val obj = item.jsonObject
            val oldId = obj.stringValue("id") ?: return@forEachIndexed
            val mapped = mapLinkGoEngine(obj, index) ?: return@forEachIndexed
            idMap[oldId] = mapped.id
            result += mapped
        }
        array.filter { it.jsonObject.isLinkGoGroup() }.forEachIndexed { index, item ->
            val obj = item.jsonObject
            val config = parseLinkGoGroupConfig(obj.stringValue("configJson")) ?: return@forEachIndexed
            val mappedMembers = config.members.mapNotNull { idMap[it.toString()] }.distinct()
            if (mappedMembers.size != config.members.distinct().size || mappedMembers.isEmpty()) {
                return@forEachIndexed
            }
            result += SearchEngineConfig(
                id = UUID.randomUUID().toString(),
                name = obj.stringValue("name").orEmpty().ifBlank { "未命名搜索组" },
                engineType = SearchEngineType.SEARCH_GROUP,
                groupKind = SearchGroupKind.TEXT,
                groupMemberIds = mappedMembers,
                groupIntervalMillis = config.intervalMillis.coerceIn(
                    SearchGroupDefaults.MIN_INTERVAL_MILLIS,
                    SearchGroupDefaults.MAX_INTERVAL_MILLIS,
                ),
                showInPickPanel = obj.booleanValue("enabled") ?: true,
                sortOrder = obj.stringValue("sortOrder")?.toIntOrNull() ?: (100 + index),
            )
        }
        return result.sortedBy { it.sortOrder }
            .mapIndexed { index, engine -> engine.copy(sortOrder = index) }
    }

    private fun JsonObject.isLinkGoGroup(): Boolean =
        stringValue("type")?.toIntOrNull() == 1 ||
            parseLinkGoGroupConfig(stringValue("configJson")) != null

    private data class LinkGoGroupConfig(
        val members: List<Long>,
        val intervalMillis: Int,
    )

    private fun parseLinkGoGroupConfig(raw: String?): LinkGoGroupConfig? {
        if (raw.isNullOrBlank()) return null
        val obj = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null
        val members = obj["members"]?.jsonArray?.mapNotNull {
            it.jsonPrimitive.content.toLongOrNull()
        } ?: return null
        if (members.isEmpty()) return null
        val interval = obj["intervalMillis"]?.jsonPrimitive?.content?.toIntOrNull()
            ?: SearchGroupDefaults.DEFAULT_INTERVAL_MILLIS
        return LinkGoGroupConfig(members = members, intervalMillis = interval)
    }

    private data class YimoEngineConfig(
        val autoEnter: Boolean = true,
        val className: String = "",
        val link: String = "",
        val target: String = "",
    )

    private fun parseYimoConfig(raw: String?): YimoEngineConfig? {
        if (raw.isNullOrBlank()) return null
        val obj = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null
        return YimoEngineConfig(
            autoEnter = obj.booleanValue("autoEnter") ?: true,
            className = obj.stringValue("className").orEmpty(),
            link = obj.stringValue("link").orEmpty(),
            target = obj.stringValue("target").orEmpty(),
        )
    }

    private fun mapLinkGoEngine(obj: JsonObject, index: Int): SearchEngineConfig? {
        val name = obj.stringValue("name").orEmpty()
        val url = obj.stringValue("urlTemplate").orEmpty()
        val pkg = obj.stringValue("packageName").orEmpty()
        val topClassName = obj.stringValue("className").orEmpty()
        val externUrl = obj.stringValue("externJumpLink").orEmpty()
        val type = obj.stringValue("type")?.toIntOrNull() ?: 0
        val scheme = obj.stringValue("scheme")?.toIntOrNull() ?: 0
        val cfg = parseYimoConfig(obj.stringValue("configJson"))
        val targetPair = cfg?.target?.takeIf { it.isNotBlank() }?.let(LinkGoRuleConverter::splitTarget)
        val classPair = (cfg?.className?.takeIf { it.isNotBlank() } ?: topClassName.takeIf { it.isNotBlank() })
            ?.let(LinkGoRuleConverter::splitTarget)
        val engineType = when {
            type == 2 && scheme == 1 && targetPair?.first?.isNotBlank() == true ->
                SearchEngineType.SHARE_IMAGE_TO_APP
            scheme == 1 && targetPair?.first?.isNotBlank() == true ->
                SearchEngineType.SHARE_TO_APP
            scheme == 2 && classPair?.first?.isNotBlank() == true ->
                SearchEngineType.JUMP_TO_ACTIVITY
            scheme == 3 && !cfg?.link.isNullOrBlank() ->
                SearchEngineType.EXTERN_JUMP_LINK
            url.isNotBlank() -> SearchEngineType.DIRECT_LINK
            externUrl.isNotBlank() -> SearchEngineType.EXTERN_JUMP_LINK
            classPair?.first?.isNotBlank() == true -> SearchEngineType.JUMP_TO_ACTIVITY
            else -> return null
        }
        val targetPackage = when (engineType) {
            SearchEngineType.SHARE_TO_APP,
            SearchEngineType.SHARE_IMAGE_TO_APP,
            -> targetPair?.first.orEmpty()
            SearchEngineType.JUMP_TO_ACTIVITY -> classPair?.first.orEmpty()
            else -> pkg
        }
        val targetActivity = when (engineType) {
            SearchEngineType.SHARE_TO_APP,
            SearchEngineType.SHARE_IMAGE_TO_APP,
            -> targetPair?.second.orEmpty()
            SearchEngineType.JUMP_TO_ACTIVITY -> classPair?.second.orEmpty()
            else -> ""
        }
        if (engineType in setOf(
                SearchEngineType.SHARE_TO_APP,
                SearchEngineType.SHARE_IMAGE_TO_APP,
                SearchEngineType.JUMP_TO_ACTIVITY,
            ) && (targetPackage.isBlank() || targetActivity.isBlank())
        ) {
            return null
        }
        val externLink = when {
            engineType == SearchEngineType.EXTERN_JUMP_LINK -> cfg?.link?.takeIf { it.isNotBlank() }
                ?: url.takeIf { it.isNotBlank() }
            else -> externUrl.takeIf { it.isNotBlank() }
        }
        return SearchEngineConfig(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "未命名引擎" },
            engineType = engineType,
            searchLink = url.takeIf { engineType == SearchEngineType.DIRECT_LINK && it.isNotBlank() },
            externJumpLink = externLink,
            externJumpPackage = pkg.takeIf {
                engineType == SearchEngineType.EXTERN_JUMP_LINK && it.isNotBlank()
            },
            targetPackage = targetPackage.takeIf { it.isNotBlank() },
            targetActivity = targetActivity.takeIf { it.isNotBlank() },
            autoInputEnter = cfg?.autoEnter ?: true,
            showInPickPanel = obj.booleanValue("enabled") ?: true,
            sortOrder = obj.stringValue("sortOrder")?.toIntOrNull() ?: index,
        )
    }

    private fun importZipBytes(
        context: Context,
        bytes: ByteArray,
        existing: List<SearchEngineConfig>,
        replaceExisting: Boolean
    ): SearchEngineImportResult {
        val entries = readZipEntries(bytes)
        val gevoJson = entries["settings/search.json"]
        if (gevoJson != null) {
            val imported = parseGevoSearchJson(gevoJson.decodeToString(), entries)
            return finalizeImport(context, existing, replaceExisting, imported, entries, "GestureEVO")
        }
        val sevoJson = entries["info.json"]
        if (sevoJson != null) {
            val imported = parseSevoSearchJson(sevoJson.decodeToString(), entries)
            return finalizeImport(context, existing, replaceExisting, imported, entries, "SearchEVO")
        }
        error("unsupported_backup")
    }

    private fun importJsonBytes(
        context: Context,
        raw: String,
        existing: List<SearchEngineConfig>,
        replaceExisting: Boolean
    ): SearchEngineImportResult {
        val imported = parseJsonText(raw)
        return finalizeImport(context, existing, replaceExisting, imported, emptyMap(), sourceLabel(raw))
    }

    private fun finalizeImport(
        context: Context,
        existing: List<SearchEngineConfig>,
        replaceExisting: Boolean,
        imported: List<SearchEngineConfig>,
        zipEntries: Map<String, ByteArray>,
        sourceLabel: String
    ): SearchEngineImportResult {
        if (imported.isEmpty()) error("no_search_engines")
        copyIconsFromZip(context, zipEntries, imported)
        val outcome = mergeImportedEngines(existing, imported, replaceExisting)
        return SearchEngineImportResult(
            importedCount = outcome.added,
            skippedCount = outcome.skipped,
            sourceLabel = sourceLabel,
            mergedEngines = outcome.merged
        )
    }

    private data class MergeOutcome(
        val merged: List<SearchEngineConfig>,
        val added: Int,
        val skipped: Int,
    )

    /**
     * 组导入不能直接走 [SearchEngineStore.mergeEngines]：后者按名称跳过重复项后
     * 会重新生成 id，导致组的 groupMemberIds 指向被丢掉的新 id。
     * 这里先导入普通引擎并记录“导入 id -> 最终 id”，再解析组。
     */
    private fun mergeImportedEngines(
        existing: List<SearchEngineConfig>,
        imported: List<SearchEngineConfig>,
        replaceExisting: Boolean,
    ): MergeOutcome {
        val base = if (replaceExisting) emptyList() else existing
        val merged = base.toMutableList()
        val usedNames = base.map { it.name.trim().lowercase() }.toMutableSet()
        val byName = base.associateBy { it.name.trim().lowercase() }.toMutableMap()
        val idMap = mutableMapOf<String, String>()
        var order = (base.maxOfOrNull { it.sortOrder } ?: -1) + 1
        var added = 0

        imported.filterNot { it.engineType == SearchEngineType.SEARCH_GROUP }
            .forEach { engine ->
                val key = engine.name.trim().lowercase()
                if (key.isBlank()) return@forEach
                val existingSame = byName[key]
                if (existingSame != null) {
                    idMap[engine.id] = existingSame.id
                    return@forEach
                }
                if (key in usedNames) {
                    merged.firstOrNull { it.name.trim().equals(engine.name.trim(), true) }
                        ?.let { idMap[engine.id] = it.id }
                    return@forEach
                }
                val copy = engine.copy(
                    id = UUID.randomUUID().toString(),
                    sortOrder = order++,
                )
                merged += copy
                usedNames += key
                byName[key] = copy
                idMap[engine.id] = copy.id
                added++
            }

        imported.filter { it.engineType == SearchEngineType.SEARCH_GROUP }
            .forEach { group ->
                val memberIds = group.groupMemberIds.mapNotNull { idMap[it] }.distinct()
                if (memberIds.isEmpty() ||
                    memberIds.size != group.groupMemberIds.distinct().size
                ) {
                    return@forEach
                }
                val key = group.name.trim().lowercase()
                if (key.isBlank() || key in usedNames) {
                    byName[key]?.let { idMap[group.id] = it.id }
                    return@forEach
                }
                val copy = group.copy(
                    id = UUID.randomUUID().toString(),
                    groupMemberIds = memberIds,
                    sortOrder = order++,
                )
                merged += copy
                usedNames += key
                byName[key] = copy
                idMap[group.id] = copy.id
                added++
            }

        return MergeOutcome(
            merged = merged.sortedBy { it.sortOrder }
                .mapIndexed { index, engine -> engine.copy(sortOrder = index) },
            added = added,
            skipped = (imported.size - added).coerceAtLeast(0),
        )
    }

    fun parseGevoSearchJson(
        settingsJson: String,
        zipEntries: Map<String, ByteArray>
    ): List<SearchEngineConfig> {
        val root = json.parseToJsonElement(settingsJson).jsonObject
        val entries = root["entries"]?.jsonArray ?: return emptyList()
        val listEntry = entries.firstOrNull { entry ->
            entry.jsonObject["key"]?.jsonPrimitive?.content == "key_search_engine_list"
        } ?: return emptyList()
        val listJson = listEntry.jsonObject["value"]?.jsonPrimitive?.content.orEmpty()
        if (listJson.isBlank()) return emptyList()
        return parseGevoEngineArray(listJson, zipEntries)
    }

    fun parseGevoExportJson(raw: String): List<SearchEngineConfig> {
        val element = json.parseToJsonElement(raw)
        return when (element) {
            is JsonArray -> parseGevoEngineArray(raw, emptyMap())
            is JsonObject -> {
                if (element["entries"] != null) {
                    parseGevoSearchJson(raw, emptyMap())
                } else {
                    listOf(mapGevoEngine(element, emptyMap()))
                }
            }
            else -> emptyList()
        }
    }

    private fun parseGevoEngineArray(
        listJson: String,
        zipEntries: Map<String, ByteArray>
    ): List<SearchEngineConfig> {
        val array = json.parseToJsonElement(listJson).jsonArray
        return array.mapIndexed { index, item ->
            mapGevoEngine(item.jsonObject, zipEntries).copy(sortOrder = index)
        }
    }

    private fun mapGevoEngine(
        obj: JsonObject,
        zipEntries: Map<String, ByteArray>
    ): SearchEngineConfig {
        val engineType = runCatching {
            SearchEngineType.valueOf(obj.stringValue("engineType") ?: SearchEngineType.DIRECT_LINK.name)
        }.getOrDefault(SearchEngineType.DIRECT_LINK)
        val iconType = runCatching {
            SearchIconType.valueOf(obj.stringValue("iconType") ?: SearchIconType.OTHER.name)
        }.getOrDefault(SearchIconType.OTHER)
        val iconRef = obj.stringValue("icon")
        val iconFileName = iconRef?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        val hasZipIcon = iconFileName != null && zipEntries.keys.any { it.endsWith(iconFileName) }
        return SearchEngineConfig(
            id = obj.stringValue("tag") ?: UUID.randomUUID().toString(),
            name = obj.stringValue("name").orEmpty(),
            engineType = engineType,
            iconType = if (hasZipIcon) SearchIconType.URI else iconType,
            iconPath = if (hasZipIcon) "search_icons/$iconFileName" else null,
            textIcon = obj.stringValue("textIcon"),
            searchLink = obj.stringValue("searchLink"),
            externJumpLink = obj.stringValue("externJumpLink"),
            externJumpPackage = obj.stringValue("externJumpPackage"),
            targetPackage = obj.stringValue("targetPackage"),
            targetActivity = obj.stringValue("targetActivity"),
            autoInputEnter = obj.booleanValue("autoInputEnter") ?: true,
            showInPickPanel = obj.booleanValue("showInSearchEngineList") ?: true
        )
    }

    fun parseSevoSearchJson(
        infoJson: String,
        zipEntries: Map<String, ByteArray>
    ): List<SearchEngineConfig> {
        val searchArray = extractSevoSearchArray(infoJson) ?: error("sevo_search_parse_failed")
        return searchArray.mapIndexed { index, item ->
            mapSevoEngine(item.jsonObject, zipEntries).copy(sortOrder = index)
        }
    }

    private fun mapSevoEngine(
        obj: JsonObject,
        zipEntries: Map<String, ByteArray>
    ): SearchEngineConfig {
        val url = obj.stringValue("url").orEmpty()
        val externUrl = obj.stringValue("externUrl").orEmpty()
        val pkg = obj.stringValue("pkgName").orEmpty()
        val targetClass = obj.stringValue("targetClass").orEmpty()
        val iconName = obj.stringValue("icon").orEmpty()
        val hasIcon = iconName.isNotBlank() && zipEntries.containsKey("files/icon/$iconName")
        val engineType = when {
            url.isNotBlank() -> SearchEngineType.DIRECT_LINK
            externUrl.isNotBlank() -> SearchEngineType.EXTERN_JUMP_LINK
            pkg.isNotBlank() -> SearchEngineType.JUMP_TO_ACTIVITY
            else -> SearchEngineType.DIRECT_LINK
        }
        return SearchEngineConfig(
            id = UUID.randomUUID().toString(),
            name = obj.stringValue("name").orEmpty(),
            engineType = engineType,
            iconType = if (hasIcon) SearchIconType.URI else SearchIconType.OTHER,
            iconPath = if (hasIcon) "search_icons/$iconName" else null,
            searchLink = url.takeIf { it.isNotBlank() },
            externJumpLink = externUrl.takeIf { it.isNotBlank() },
            targetPackage = pkg.takeIf { it.isNotBlank() },
            targetActivity = targetClass.takeIf { it.isNotBlank() },
            autoInputEnter = true,
            showInPickPanel = true
        )
    }

    private fun extractSevoSearchArray(infoJson: String): JsonArray? {
        runCatching {
            val root = json.parseToJsonElement(infoJson).jsonObject
            return root["search"]?.jsonArray
        }
        val marker = "\"search\":"
        val start = infoJson.indexOf(marker)
        if (start < 0) return null
        val arrayStart = infoJson.indexOf('[', start)
        if (arrayStart < 0) return null
        var depth = 0
        for (i in arrayStart until infoJson.length) {
            when (infoJson[i]) {
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) {
                        val slice = infoJson.substring(arrayStart, i + 1)
                        return json.parseToJsonElement(slice).jsonArray
                    }
                }
            }
        }
        return null
    }

    private fun copyIconsFromZip(
        context: Context,
        zipEntries: Map<String, ByteArray>,
        engines: List<SearchEngineConfig>
    ) {
        val iconDir = File(context.filesDir, "search_icons").apply { mkdirs() }
        engines.forEach { engine ->
            val relative = engine.iconPath ?: return@forEach
            val fileName = relative.substringAfterLast('/')
            val zipKey = zipEntries.keys.firstOrNull { it.endsWith(fileName) } ?: return@forEach
            val bytes = zipEntries[zipKey] ?: return@forEach
            File(iconDir, fileName).writeBytes(bytes)
        }
    }

    private fun readZipEntries(bytes: ByteArray): Map<String, ByteArray> {
        val result = linkedMapOf<String, ByteArray>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) {
                    result[entry.name.replace('\\', '/')] = zip.readBytes()
                }
                zip.closeEntry()
            }
        }
        return result
    }

    private fun isZipArchive(name: String, bytes: ByteArray): Boolean =
        name.endsWith(".zip") || name.endsWith(".evobak") || isZip(bytes)

    private fun isZip(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()

    private fun JsonObject.stringValue(key: String): String? =
        this[key]?.jsonPrimitive?.content

    private fun JsonObject.booleanValue(key: String): Boolean? =
        this[key]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
}
