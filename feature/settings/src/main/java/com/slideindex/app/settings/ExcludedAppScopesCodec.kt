package com.slideindex.app.settings

object ExcludedAppScopesCodec {
    private const val ENTRY_SEPARATOR = '|'
    private const val FLAG_SEPARATOR = ','

    fun encode(map: Map<String, ExcludedAppScopes>): Set<String> =
        map.map { (packageName, scopes) -> encodeEntry(packageName, scopes) }.toSet()

    fun decode(encoded: Set<String>?): Map<String, ExcludedAppScopes> {
        if (encoded.isNullOrEmpty()) return emptyMap()
        return buildMap {
            encoded.forEach { entry ->
                decodeEntry(entry)?.let { (packageName, scopes) ->
                    put(packageName, scopes)
                }
            }
        }
    }

    fun encodeEntry(packageName: String, scopes: ExcludedAppScopes): String =
        buildString {
            append(packageName)
            append(ENTRY_SEPARATOR)
            append(if (scopes.suppressTriggers) '1' else '0')
            append(FLAG_SEPARATOR)
            append(if (scopes.suppressCornerWheel) '1' else '0')
            append(FLAG_SEPARATOR)
            append(if (scopes.suppressFloatBall) '1' else '0')
            append(FLAG_SEPARATOR)
            append(if (scopes.suppressMultiTouch) '1' else '0')
        }

    private fun decodeEntry(entry: String): Pair<String, ExcludedAppScopes>? {
        val separatorIndex = entry.indexOf(ENTRY_SEPARATOR)
        if (separatorIndex <= 0 || separatorIndex >= entry.lastIndex) return null
        val packageName = entry.substring(0, separatorIndex)
        val flags = entry.substring(separatorIndex + 1).split(FLAG_SEPARATOR)
        if (flags.size < 3) return null
        return packageName to ExcludedAppScopes(
            suppressTriggers = flags[0] == "1",
            suppressCornerWheel = flags[1] == "1",
            suppressFloatBall = flags[2] == "1",
            suppressMultiTouch = flags.getOrNull(3)?.let { it == "1" } ?: true,
        )
    }
}
