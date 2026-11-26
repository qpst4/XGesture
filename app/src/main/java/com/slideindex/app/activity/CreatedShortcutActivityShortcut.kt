package com.slideindex.app.activity

import com.slideindex.app.util.AppShortcutLoader

/**
 * `ACTION_CREATE_SHORTCUT` 回传结果 -> 应用内直达条目（带图标路径的由调用方补）。
 *
 * 优先用 intent URI（宿主自己拼好的启动意图，最保真）；没有 URI 时退回组件名。
 * 两个都没有就返回 null（调用方提示失败）。
 */
fun AppShortcutLoader.CreatedShortcut.toActivityShortcutOrNull(): ActivityShortcut? {
    val uri = intentUri?.takeIf { it.isNotBlank() }
    if (uri != null) {
        return ActivityShortcut.intent(
            label = label.ifBlank { hostPackageName },
            packageName = hostPackageName,
            intentUris = listOf(uri),
        )
    }
    val flat = componentFlat?.takeIf { it.isNotBlank() } ?: return null
    val pkg = flat.substringBefore('/')
    val cls = flat.substringAfter('/', missingDelimiterValue = "")
    if (pkg.isBlank() || cls.isBlank()) return null
    return ActivityShortcut.component(
        label = label.ifBlank { cls },
        packageName = pkg,
        activityClassName = cls,
    )
}
