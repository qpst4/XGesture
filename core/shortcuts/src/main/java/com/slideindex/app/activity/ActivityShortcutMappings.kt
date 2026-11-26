package com.slideindex.app.activity

import android.content.Intent
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureShortcutPayload
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.overlay.TaskSwitcherMenuItem
import com.slideindex.app.util.ShortcutKind

fun ActivityShortcut.toLaunchShortcut(): GestureAction.LaunchShortcut = when (kind) {
    ActivityShortcutKind.COMPONENT ->
        GestureAction.LaunchShortcut.component(componentFlat(), label)
    ActivityShortcutKind.DYNAMIC ->
        GestureAction.LaunchShortcut.dynamic(packageName, shortcutId, label)
    ActivityShortcutKind.INTENT -> {
        val hostPackage = packageName.takeIf { it.isNotBlank() }
        if (intentUris.size <= 1) {
            GestureAction.LaunchShortcut.intent(
                intentUris.firstOrNull().orEmpty(),
                label,
                hostPackage
            )
        } else {
            GestureAction.LaunchShortcut.intents(intentUris, label, hostPackage)
        }
    }
}

fun ActivityShortcut.toQuickLauncherItem(): QuickLauncherItem {
    if (ActivityShortcutFileSupport.isFileShortcut(this)) {
        val config = ActivityShortcutFileSupport.configFrom(this)
        if (config != null) {
            val intent = Intent().apply {
                setClassName(
                    "com.slideindex.app",
                    "com.slideindex.app.activity.ShortcutFileLaunchActivity",
                )
                putExtra("extra_file_config", ActivityShortcutFileSupport.encodeUri(config))
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return QuickLauncherItem.intentShortcut(
                intentUri = intent.toUri(Intent.URI_INTENT_SCHEME),
                label = label,
            )
        }
    }
    if (ActivityShortcutImageSupport.isImageShortcut(this)) {
        val config = ActivityShortcutImageSupport.configFrom(this)
        if (config != null) {
            val intent = Intent().apply {
                setClassName(
                    "com.slideindex.app",
                    "com.slideindex.app.activity.ShortcutImagePreviewActivity",
                )
                putExtra("extra_image_uri", config.uri)
                putExtra("extra_open_in_dialog", config.openInDialog)
                putExtra("extra_click_to_dismiss", config.clickToDismiss)
                putExtra("extra_orientation", config.orientation)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return QuickLauncherItem.intentShortcut(
                intentUri = intent.toUri(Intent.URI_INTENT_SCHEME),
                label = label,
            )
        }
    }
    return when (kind) {
        ActivityShortcutKind.COMPONENT ->
            QuickLauncherItem.shortcut(componentFlat(), label)
        ActivityShortcutKind.DYNAMIC ->
            QuickLauncherItem.dynamicShortcut(packageName, shortcutId, label)
        ActivityShortcutKind.INTENT -> {
            if (intentUris.size <= 1) {
                QuickLauncherItem.intentShortcut(
                    intentUri = intentUris.firstOrNull().orEmpty(),
                    label = label,
                    hostPackage = packageName
                )
            } else {
                QuickLauncherItem.intentShortcuts(
                    intentUris = intentUris,
                    label = label,
                    hostPackage = packageName
                )
            }
        }
    }
}

fun ActivityShortcut.subtitleDetail(): String = when {
    ActivityShortcutImageSupport.isImageShortcut(this) -> {
        ActivityShortcutImageSupport.configFrom(this)?.uri.orEmpty()
    }
    ActivityShortcutFileSupport.isFileShortcut(this) -> {
        val config = ActivityShortcutFileSupport.configFrom(this)
        if (config != null) {
            if (config.targetPackage.isNotBlank()) {
                "${config.targetPackage} · ${config.fileUri}"
            } else {
                config.fileUri
            }
        } else {
            intentUris.firstOrNull().orEmpty()
        }
    }
    isAppShortcut -> shortcutId
    kind == ActivityShortcutKind.COMPONENT -> activityClassName
    kind == ActivityShortcutKind.DYNAMIC -> shortcutId
    else -> intentUris.firstOrNull().orEmpty()
}

/**
 * 把「应用快捷方式」列表里的条目转成目录条目。
 *
 * 关键：动态/固定快捷方式要保留 [TaskSwitcherMenuItem.shortcutId]；
 * 静态（XML）快捷方式的 id 是 `包名/类名(intents)` 拼出来的伪 id，不能当系统 ID 用，只保留 intent。
 */
fun TaskSwitcherMenuItem.toActivityShortcut(
    packageName: String,
    label: String? = null,
): ActivityShortcut {
    val uris = intentUris.orEmpty()
        .filter { it.isNotBlank() }
        .map { enrichIntentUriWithPackage(it, packageName) }
    val keepShortcutId = (kind == ShortcutKind.DYNAMIC || kind == ShortcutKind.PINNED) &&
        !shortcutId.isNullOrBlank()
    return ActivityShortcut(
        label = label?.takeIf { it.isNotBlank() } ?: this.label,
        packageName = packageName,
        shortcutId = if (keepShortcutId) shortcutId.orEmpty() else "",
        intentUris = uris,
    )
}

private fun enrichIntentUriWithPackage(intentUri: String, packageName: String): String =
    runCatching {
        val intent = Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME)
        if (intent.`package`.isNullOrBlank() && intent.component == null) {
            intent.setPackage(packageName)
        }
        intent.toUri(Intent.URI_INTENT_SCHEME)
    }.getOrDefault(intentUri)


private fun normalizeIntentUriForMatch(raw: String?): String? {
    val uri = raw?.takeIf { it.isNotBlank() } ?: return null
    return runCatching {
        android.content.Intent.parseUri(uri, android.content.Intent.URI_INTENT_SCHEME)
            .toUri(android.content.Intent.URI_INTENT_SCHEME)
    }.getOrNull()
}

/** intent URI 的 extras 编码顺序/转义差异不影响匹配：先各自规范化再比。 */
private fun List<ActivityShortcut>.findByIntentEquivalence(rawIntentUri: String?): ActivityShortcut? {
    val target = normalizeIntentUriForMatch(rawIntentUri) ?: return null
    return firstOrNull { shortcut ->
        shortcut.intentUris.any { normalizeIntentUriForMatch(it) == target }
    }
}

private fun parseIntentForMatch(raw: String?): android.content.Intent? {
    val uri = raw?.takeIf { it.isNotBlank() } ?: return null
    return runCatching {
        android.content.Intent.parseUri(uri, android.content.Intent.URI_INTENT_SCHEME)
    }.getOrNull()
}

private fun isOwnPreviewIntent(intent: android.content.Intent?): Boolean {
    val component = intent?.component ?: return false
    if (component.packageName != "com.slideindex.app") return false
    val name = component.className.orEmpty()
    return name.contains("ShortcutImagePreviewActivity") ||
        name.contains("ShortcutFileLaunchActivity")
}

private fun List<ActivityShortcut>.findByFilterEquals(
    rawIntentUri: String?,
): ActivityShortcut? {
    val target = parseIntentForMatch(rawIntentUri) ?: return null
    if (isOwnPreviewIntent(target)) return null
    return firstOrNull { shortcut ->
        shortcut.intentUris.any { raw ->
            parseIntentForMatch(raw)?.filterEquals(target) == true
        }
    }
}

private fun List<ActivityShortcut>.findByPreviewPayload(
    rawIntentUri: String?,
): ActivityShortcut? {
    val intent = parseIntentForMatch(rawIntentUri) ?: return null
    val imageUri = intent.getStringExtra("extra_image_uri")
    val fileConfig = intent.getStringExtra("extra_file_config")
    if (imageUri.isNullOrBlank() && fileConfig.isNullOrBlank()) return null
    return firstOrNull { shortcut ->
        val image = com.slideindex.app.activity.ActivityShortcutImageSupport
            .configFrom(shortcut)?.uri
        (!imageUri.isNullOrBlank() && image == imageUri) ||
            (!fileConfig.isNullOrBlank() &&
                com.slideindex.app.activity.ActivityShortcutFileSupport
                    .isFileShortcut(shortcut))
    }
}

fun List<ActivityShortcut>.findForLaunchShortcut(payloadKey: String): ActivityShortcut? {
    val decoded = GestureShortcutPayload.decode(payloadKey) ?: return null
    val exact = when (decoded) {
        is GestureShortcutPayload.Decoded.Dynamic ->
            findByIdentityKey(
                QuickLauncherItemCodec.shortcutKey(decoded.packageName, decoded.shortcutId)
            )
        is GestureShortcutPayload.Decoded.Component ->
            findByIdentityKey(decoded.componentFlat)
        is GestureShortcutPayload.Decoded.IntentShortcut ->
            findByIdentityKey("intent:${decoded.intentUri}")
        is GestureShortcutPayload.Decoded.IntentsShortcut ->
            findByIdentityKey(
                if (decoded.intentUris.size == 1) {
                    "intent:${decoded.intentUris[0]}"
                } else {
                    "intents:${decoded.intentUris.joinToString(QuickLauncherItemCodec.INTENT_LIST_SEP)}"
                }
            )
    }
    if (exact != null) return exact
    val rawIntent = when (decoded) {
        is GestureShortcutPayload.Decoded.IntentShortcut -> decoded.intentUri
        is GestureShortcutPayload.Decoded.IntentsShortcut -> decoded.intentUris.singleOrNull()
        else -> null
    }
    return findByIntentEquivalence(rawIntent)
        ?: findByFilterEquals(rawIntent)
        ?: findByPreviewPayload(rawIntent)
}

fun List<ActivityShortcut>.findForQuickLauncherItem(item: QuickLauncherItem): ActivityShortcut? {
    if (item.type != QuickLauncherItemType.SHORTCUT) return null
    val key = QuickLauncherItemCodec.shortcutItemKey(item)
    key?.let { findByIdentityKey(it) }?.let { return it }
    val rawIntent = QuickLauncherItemCodec.parseIntentPayload(item.payload)
        ?: QuickLauncherItemCodec.parseIntentListPayload(item.payload)?.singleOrNull()
    return findByIntentEquivalence(rawIntent)
        ?: findByFilterEquals(rawIntent)
        ?: findByPreviewPayload(rawIntent)
}

/** 按 intent URI（规范化后）在目录里找快捷方式；图片/文件直达的 extras 编码差异也能命中。 */

fun List<ActivityShortcut>.findByIntentUri(rawIntentUri: String?): ActivityShortcut? =
    findByIntentEquivalence(rawIntentUri)

fun activityShortcutFromQuickLauncherItem(item: QuickLauncherItem): ActivityShortcut? {
    if (item.type != QuickLauncherItemType.SHORTCUT) return null
    QuickLauncherItemCodec.parseIntentListPayload(item.payload)?.let { uris ->
        val pkg = QuickLauncherItemCodec.resolveHostPackageName(item.payload).orEmpty()
        return ActivityShortcut.intent(label = item.label, packageName = pkg, intentUris = uris)
    }
    QuickLauncherItemCodec.parseIntentPayload(item.payload)?.let { uri ->
        val pkg = QuickLauncherItemCodec.resolveHostPackageName(item.payload).orEmpty()
        return ActivityShortcut.intent(label = item.label, packageName = pkg, intentUris = listOf(uri))
    }
    QuickLauncherItemCodec.parseShortcutPayload(item.payload)?.let { (pkg, id) ->
        return ActivityShortcut.dynamic(label = item.label, packageName = pkg, shortcutId = id)
    }
    val payload = item.payload
    if (payload.startsWith("c:")) {
        val flat = payload.removePrefix("c:").substringBefore('\u001D')
        val pkg = flat.substringBefore('/')
        val cls = flat.substringAfter('/', missingDelimiterValue = "")
        if (pkg.isBlank() || cls.isBlank()) return null
        return ActivityShortcut.component(label = item.label, packageName = pkg, activityClassName = cls)
    }
    if ('/' in payload) {
        val pkg = payload.substringBefore('/')
        val cls = payload.substringAfter('/')
        if (pkg.isBlank() || cls.isBlank()) return null
        return ActivityShortcut.component(
            label = item.label.ifBlank { cls },
            packageName = pkg,
            activityClassName = cls
        )
    }
    return null
}
