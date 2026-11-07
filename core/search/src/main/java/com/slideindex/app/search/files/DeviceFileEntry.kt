package com.slideindex.app.search.files

/**
 * Portions derived from Quick Search (https://github.com/teja2495/quick-search)
 * Licensed under MIT. Modified for com.slideindex.app.
 */

import android.net.Uri

data class DeviceFileEntry(
    val uri: Uri,
    val displayName: String,
    val mimeType: String?,
    val lastModified: Long,
    val isDirectory: Boolean,
    val relativePath: String? = null,
)
