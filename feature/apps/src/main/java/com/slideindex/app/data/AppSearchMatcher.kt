package com.slideindex.app.data

import com.slideindex.app.util.PinyinHelper

object AppSearchMatcher {
    fun search(
        apps: List<AppInfo>,
        query: String,
        limit: Int = Int.MAX_VALUE,
        /** 应用别名（反馈 #375）：非空时既能按标签搜到，也能按别名搜到。 */
        aliasOf: (AppInfo) -> String? = { null },
    ): List<AppInfo> {
        val normalized = query.trim().lowercase()
        if (normalized.isEmpty()) return apps
        if (limit <= 0) return emptyList()
        val pinyinQuery = PinyinHelper.sortKey(query)
        return apps
            .mapNotNull { app ->
                val alias = aliasOf(app)?.takeIf { it.isNotBlank() }
                score(app, normalized, pinyinQuery, alias)?.let { app to it }
            }
            .sortedWith(
                compareByDescending<Pair<AppInfo, Int>> { it.second }
                    .thenBy { matchIndex(it.first, normalized, pinyinQuery, aliasOf(it.first)) }
                    .thenBy { it.first.label.length }
                    .thenBy { it.first.pinyinKey },
            )
            .map { it.first }
            .take(limit)
    }

    internal fun score(
        app: AppInfo,
        normalizedQuery: String,
        pinyinQuery: String,
        alias: String? = null,
    ): Int? {
        val labelLower = app.label.trim().lowercase()
        if (labelLower.isEmpty()) return null

        val labelPinyin = app.pinyinKey
        val labelInitial = PinyinHelper.initialKey(app.label)
        var best = textScore(
            lowerText = labelLower,
            pinyinText = labelPinyin,
            initialText = labelInitial,
            normalizedQuery = normalizedQuery,
            pinyinQuery = pinyinQuery,
            titleBoost = 100,
        )
        if (!alias.isNullOrBlank()) {
            // 别名比标签更像"用户自己起的名字"，命中时给更高权重。
            best = maxOf(
                best,
                textScore(
                    lowerText = alias.trim().lowercase(),
                    pinyinText = PinyinHelper.sortKey(alias),
                    initialText = PinyinHelper.initialKey(alias),
                    normalizedQuery = normalizedQuery,
                    pinyinQuery = pinyinQuery,
                    titleBoost = 130,
                ),
            )
        }
        if (app.packageName.lowercase().contains(normalizedQuery)) {
            best = maxOf(best, 20)
        }
        return best.takeIf { it > 0 }
    }

    private fun textScore(
        lowerText: String,
        pinyinText: String,
        initialText: String,
        normalizedQuery: String,
        pinyinQuery: String,
        titleBoost: Int,
    ): Int {
        if (lowerText == normalizedQuery || pinyinText == pinyinQuery || initialText == normalizedQuery) return titleBoost + 40
        if (lowerText.startsWith(normalizedQuery) || pinyinText.startsWith(pinyinQuery) || initialText.startsWith(normalizedQuery)) {
            return titleBoost + 30
        }
        if (lowerText.contains(normalizedQuery) || pinyinText.contains(pinyinQuery) || initialText.contains(normalizedQuery)) {
            return titleBoost + 10
        }
        return 0
    }

    private fun matchIndex(
        app: AppInfo,
        normalizedQuery: String,
        pinyinQuery: String,
        alias: String? = null,
    ): Int {
        val labelLower = app.label.lowercase()
        val labelIndex = labelLower.indexOf(normalizedQuery)
        if (labelIndex >= 0) return labelIndex
        if (!alias.isNullOrBlank()) {
            val aliasIndex = alias.trim().lowercase().indexOf(normalizedQuery)
            if (aliasIndex >= 0) return aliasIndex
            val aliasPinyinIndex = PinyinHelper.sortKey(alias).indexOf(pinyinQuery)
            if (aliasPinyinIndex >= 0) return aliasPinyinIndex + 500
        }
        val pinyinIndex = app.pinyinKey.indexOf(pinyinQuery)
        if (pinyinIndex >= 0) return pinyinIndex + 1_000
        val initialIndex = PinyinHelper.initialKey(app.label).indexOf(normalizedQuery)
        if (initialIndex >= 0) return initialIndex + 5_000
        val packageIndex = app.packageName.lowercase().indexOf(normalizedQuery)
        if (packageIndex >= 0) return packageIndex + 10_000
        return Int.MAX_VALUE
    }
}
