package com.slideindex.app.util

enum class ShortcutScanPhase {
    SYSTEM_XML,
    DUMPSYS,
    PACKAGES,
    APPS,
    FINALIZING,
}

data class ShortcutScanProgress(
    val phase: ShortcutScanPhase,
    val current: Int = 0,
    val total: Int = 0,
    val detail: String? = null,
) {
    val fraction: Float?
        get() = if (total > 0) current.toFloat() / total.toFloat() else null
}
