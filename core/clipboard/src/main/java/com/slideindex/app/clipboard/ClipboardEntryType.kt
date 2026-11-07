package com.slideindex.app.clipboard

import kotlinx.serialization.Serializable

@Serializable
enum class ClipboardEntryType {
    TEXT,
    URI,
    INTENT,
    HTML,
}
