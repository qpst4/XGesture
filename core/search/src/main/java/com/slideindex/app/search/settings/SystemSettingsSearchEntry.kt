package com.slideindex.app.search.settings

data class SystemSettingsSearchEntry(
    val title: String,
    val screenTitle: String?,
    val keywords: String?,
    val packageName: String,
    val className: String?,
    val action: String?,
    val key: String?,
) {
    val subtitle: String?
        get() = screenTitle?.takeIf { it.isNotBlank() && !it.equals(title, ignoreCase = true) }

    val dedupeKey: String
        get() = listOf(
            title.trim().lowercase(),
            packageName,
            className.orEmpty(),
            action.orEmpty(),
            key.orEmpty(),
        ).joinToString("|")
}
