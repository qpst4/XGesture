package com.slideindex.app.overlay.searchpanel

import com.slideindex.app.settings.SearchEngineConfig
import com.slideindex.app.settings.SearchPanelSectionAliasSettings
import java.util.Locale

enum class SearchPanelResultSection {
    ALL,
    APPS,
    SHORTCUTS,
    CONTACTS,
    FILES,
    SETTINGS,
    CLIPBOARD,
}

sealed class SearchPanelAliasMatch {
    data class Engine(
        val queryWithoutAlias: String,
        val engine: SearchEngineConfig,
    ) : SearchPanelAliasMatch()

    data class Section(
        val queryWithoutAlias: String,
        val section: SearchPanelResultSection,
    ) : SearchPanelAliasMatch()
}

/**
 * Prefix alias matching (quick-search style): first token + separating space.
 */
object SearchPanelAliasResolver {
    fun detectPrefixAlias(
        query: String,
        engines: List<SearchEngineConfig>,
        sectionAliases: SearchPanelSectionAliasSettings = SearchPanelSectionAliasSettings(),
    ): SearchPanelAliasMatch? {
        val trimmedStart = query.trimStart()
        if (trimmedStart.isEmpty()) return null
        val separatorIndex = trimmedStart.indexOfFirst { it.isWhitespace() }
        if (separatorIndex <= 0) return null

        val prefix = trimmedStart.substring(0, separatorIndex).lowercase(Locale.getDefault())
        val remainder = trimmedStart.substring(separatorIndex).trimStart()

        sectionAliases.toAliasLookup()[prefix]?.let { sectionId ->
            val section = when (sectionId) {
                SearchPanelSectionAliasSettings.SECTION_APPS -> SearchPanelResultSection.APPS
                SearchPanelSectionAliasSettings.SECTION_SHORTCUTS -> SearchPanelResultSection.SHORTCUTS
                SearchPanelSectionAliasSettings.SECTION_CONTACTS -> SearchPanelResultSection.CONTACTS
                SearchPanelSectionAliasSettings.SECTION_FILES -> SearchPanelResultSection.FILES
                SearchPanelSectionAliasSettings.SECTION_SETTINGS -> SearchPanelResultSection.SETTINGS
                SearchPanelSectionAliasSettings.SECTION_CLIPBOARD -> SearchPanelResultSection.CLIPBOARD
                else -> null
            }
            if (section != null) {
                return SearchPanelAliasMatch.Section(remainder, section)
            }
        }

        val engineAliases = buildEngineAliasMap(engines, sectionAliases)
        engineAliases[prefix]?.let { engine ->
            return SearchPanelAliasMatch.Engine(remainder, engine)
        }
        return null
    }

    fun normalizeAliasCode(raw: String): String =
        SearchPanelSectionAliasSettings.normalize(raw)

    fun isValidAliasCode(code: String): Boolean {
        if (code.isEmpty()) return true
        if (code.length > 16) return false
        return code.none { it.isWhitespace() }
    }

    fun findAliasConflict(
        code: String,
        engines: List<SearchEngineConfig>,
        excludeEngineId: String?,
        sectionAliases: SearchPanelSectionAliasSettings = SearchPanelSectionAliasSettings(),
    ): String? {
        val normalized = normalizeAliasCode(code)
        if (normalized.isEmpty()) return null
        if (sectionAliases.toAliasLookup().containsKey(normalized)) return normalized
        val conflict = engines.firstOrNull {
            it.id != excludeEngineId &&
                normalizeAliasCode(it.aliasCode.orEmpty()) == normalized
        }
        return if (conflict != null) normalized else null
    }

    private fun buildEngineAliasMap(
        engines: List<SearchEngineConfig>,
        sectionAliases: SearchPanelSectionAliasSettings,
    ): Map<String, SearchEngineConfig> {
        val reserved = sectionAliases.toAliasLookup()
        val map = LinkedHashMap<String, SearchEngineConfig>()
        engines.forEach { engine ->
            val code = normalizeAliasCode(engine.aliasCode.orEmpty())
            if (code.isNotEmpty() && !map.containsKey(code) && !reserved.containsKey(code)) {
                map[code] = engine
            }
        }
        return map
    }
}
