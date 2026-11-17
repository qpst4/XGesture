package com.slideindex.app.activity

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

enum class ImageSourceType {
    LOCAL,
    FILE,
    URL,
}

data class ImageShortcutConfig(
    val uri: String,
    val sourceType: ImageSourceType = ImageSourceType.LOCAL,
    val openInDialog: Boolean = true,
    val clickToDismiss: Boolean = true,
    val orientation: Int = 0, // 0: 默认, 1: 竖屏, 2: 横屏
)

object ActivityShortcutImageSupport {
    const val HOST_PACKAGE = "com.slideindex.app.image"
    private const val URI_PREFIX = "cebianimage:"

    fun isImageShortcut(shortcut: ActivityShortcut): Boolean =
        shortcut.packageName == HOST_PACKAGE || shortcut.intentUris.any { isImageUri(it) }

    fun isImageUri(uri: String): Boolean = uri.startsWith(URI_PREFIX)

    fun encodeUri(config: ImageShortcutConfig): String {
        val encodedUri = URLEncoder.encode(config.uri, StandardCharsets.UTF_8.name())
        return "${URI_PREFIX}uri=$encodedUri&sourceType=${config.sourceType.name}&openInDialog=${config.openInDialog}&clickToDismiss=${config.clickToDismiss}&orientation=${config.orientation}"
    }

    fun decodeConfig(uri: String): ImageShortcutConfig? {
        if (!isImageUri(uri)) return null
        val query = uri.removePrefix(URI_PREFIX)
        val params = query.split('&').associate { param ->
            val idx = param.indexOf('=')
            if (idx != -1) {
                param.substring(0, idx) to param.substring(idx + 1)
            } else {
                param to ""
            }
        }
        val encodedUri = params["uri"] ?: return null
        val rawUri = runCatching { URLDecoder.decode(encodedUri, StandardCharsets.UTF_8.name()) }.getOrDefault(encodedUri)
        val sourceType = runCatching { ImageSourceType.valueOf(params["sourceType"].orEmpty()) }.getOrDefault(ImageSourceType.LOCAL)
        val openInDialog = params["openInDialog"]?.toBooleanStrictOrNull() ?: true
        val clickToDismiss = params["clickToDismiss"]?.toBooleanStrictOrNull() ?: true
        val orientation = params["orientation"]?.toIntOrNull() ?: 0
        return ImageShortcutConfig(
            uri = rawUri,
            sourceType = sourceType,
            openInDialog = openInDialog,
            clickToDismiss = clickToDismiss,
            orientation = orientation,
        )
    }

    fun configFrom(shortcut: ActivityShortcut): ImageShortcutConfig? {
        val uri = shortcut.intentUris.firstOrNull { isImageUri(it) } ?: return null
        return decodeConfig(uri)
    }

    fun toShortcut(
        label: String,
        config: ImageShortcutConfig,
        iconPath: String? = null,
        id: String = UUID.randomUUID().toString(),
    ): ActivityShortcut = ActivityShortcut(
        id = id,
        label = label,
        packageName = HOST_PACKAGE,
        activityClassName = "",
        shortcutId = id,
        intentUris = listOf(encodeUri(config)),
        iconPath = iconPath,
    )
}
