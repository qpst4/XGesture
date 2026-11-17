package com.slideindex.app.activity

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

data class FileShortcutConfig(
    val fileUri: String,
    val mimeType: String = "*/*",
    val isShareMode: Boolean = false,
    val isTextMode: Boolean = false,
    val targetPackage: String = "",
    val targetActivity: String = "",
)

object ActivityShortcutFileSupport {
    const val HOST_PACKAGE = "com.slideindex.app.file"
    private const val URI_PREFIX = "cebianfile:"

    fun isFileShortcut(shortcut: ActivityShortcut): Boolean =
        shortcut.packageName == HOST_PACKAGE || shortcut.intentUris.any { isFileUri(it) }

    fun isFileUri(uri: String): Boolean = uri.startsWith(URI_PREFIX)

    fun encodeUri(config: FileShortcutConfig): String {
        val encodedUri = URLEncoder.encode(config.fileUri, StandardCharsets.UTF_8.name())
        val encodedMime = URLEncoder.encode(config.mimeType, StandardCharsets.UTF_8.name())
        val pkg = URLEncoder.encode(config.targetPackage, StandardCharsets.UTF_8.name())
        val act = URLEncoder.encode(config.targetActivity, StandardCharsets.UTF_8.name())
        return "${URI_PREFIX}fileUri=$encodedUri&mimeType=$encodedMime&isShareMode=${config.isShareMode}&isTextMode=${config.isTextMode}&targetPackage=$pkg&targetActivity=$act"
    }

    fun decodeConfig(uri: String): FileShortcutConfig? {
        if (!isFileUri(uri)) return null
        val query = uri.removePrefix(URI_PREFIX)
        val params = query.split('&').associate { param ->
            val idx = param.indexOf('=')
            if (idx != -1) {
                param.substring(0, idx) to param.substring(idx + 1)
            } else {
                param to ""
            }
        }
        val encodedUri = params["fileUri"] ?: return null
        val rawUri = runCatching { URLDecoder.decode(encodedUri, StandardCharsets.UTF_8.name()) }.getOrDefault(encodedUri)
        val rawMime = runCatching { URLDecoder.decode(params["mimeType"].orEmpty(), StandardCharsets.UTF_8.name()) }
            .getOrDefault("*/*")
            .ifBlank { "*/*" }
        val isShareMode = params["isShareMode"]?.toBooleanStrictOrNull() ?: false
        val isTextMode = params["isTextMode"]?.toBooleanStrictOrNull() ?: false
        val targetPackage = runCatching { URLDecoder.decode(params["targetPackage"].orEmpty(), StandardCharsets.UTF_8.name()) }.getOrDefault("")
        val targetActivity = runCatching { URLDecoder.decode(params["targetActivity"].orEmpty(), StandardCharsets.UTF_8.name()) }.getOrDefault("")
        return FileShortcutConfig(
            fileUri = rawUri,
            mimeType = rawMime,
            isShareMode = isShareMode,
            isTextMode = isTextMode,
            targetPackage = targetPackage,
            targetActivity = targetActivity,
        )
    }

    fun configFrom(shortcut: ActivityShortcut): FileShortcutConfig? {
        val uri = shortcut.intentUris.firstOrNull { isFileUri(it) } ?: return null
        return decodeConfig(uri)
    }

    fun toShortcut(
        label: String,
        config: FileShortcutConfig,
        iconPath: String? = null,
        id: String = UUID.randomUUID().toString(),
    ): ActivityShortcut {
        val effectivePackage = config.targetPackage.ifBlank { HOST_PACKAGE }
        return ActivityShortcut(
            id = id,
            label = label,
            packageName = effectivePackage,
            activityClassName = config.targetActivity,
            shortcutId = id,
            intentUris = listOf(encodeUri(config)),
            iconPath = iconPath,
        )
    }
}
