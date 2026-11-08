package com.slideindex.app.search.shortcuts

import com.slideindex.app.util.PinyinHelper

/** 快捷方式匹配：快捷方式名优先，其次所属 App 名；同时支持拼音/首字母。 */
object ShortcutSearchMatcher {
    fun search(
        entries: List<ShortcutSearchEntry>,
        query: String,
        limit: Int,
    ): List<ShortcutSearchEntry> {
        val normalized = query.trim().lowercase()
        if (normalized.isEmpty() || limit <= 0) return emptyList()
        val pinyinQuery = PinyinHelper.sortKey(query)
        return entries.asSequence()
            .mapNotNull { entry ->
                score(entry, normalized, pinyinQuery)?.let { entry to it }
            }
            .sortedWith(
                compareByDescending<Pair<ShortcutSearchEntry, Int>> { it.second }
                    .thenBy { PinyinHelper.sortKey(it.first.label) }
                    .thenBy { PinyinHelper.sortKey(it.first.appLabel) },
            )
            .map { it.first }
            .distinctBy { it.dedupeKey }
            .take(limit)
            .toList()
    }

    internal fun score(
        entry: ShortcutSearchEntry,
        normalizedQuery: String,
        pinyinQuery: String,
    ): Int? {
        val label = entry.label.trim()
        if (label.isEmpty()) return null
        var best = textScore(
            lowerText = label.lowercase(),
            pinyinText = PinyinHelper.sortKey(label),
            initialText = PinyinHelper.initialKey(label),
            normalizedQuery = normalizedQuery,
            pinyinQuery = pinyinQuery,
            boost = 100,
        )
        val appLabel = entry.appLabel.trim()
        if (appLabel.isNotEmpty()) {
            best = maxOf(
                best,
                textScore(
                    lowerText = appLabel.lowercase(),
                    pinyinText = PinyinHelper.sortKey(appLabel),
                    initialText = PinyinHelper.initialKey(appLabel),
                    normalizedQuery = normalizedQuery,
                    pinyinQuery = pinyinQuery,
                    boost = 60,
                ),
            )
        }
        return best.takeIf { it > 0 }
    }

    private fun textScore(
        lowerText: String,
        pinyinText: String,
        initialText: String,
        normalizedQuery: String,
        pinyinQuery: String,
        boost: Int,
    ): Int {
        if (lowerText == normalizedQuery || pinyinText == pinyinQuery) return boost + 40
        if (lowerText.startsWith(normalizedQuery) || pinyinText.startsWith(pinyinQuery)) return boost + 30
        if (initialText.startsWith(normalizedQuery)) return boost + 20
        if (lowerText.contains(normalizedQuery) || pinyinText.contains(pinyinQuery)) return boost + 10
        return 0
    }
}
