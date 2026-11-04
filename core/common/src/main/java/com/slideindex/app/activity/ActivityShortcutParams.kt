package com.slideindex.app.activity

/**
 * 「应用内直达」参数编辑的纯函数：文本规范化、组件拆分、按类型重建条目、合并重复项。
 *
 * 这里刻意不碰 Android API，单测直接跑（见 `ActivityShortcutParamsTest`）；
 * 包/类是否存在、快捷方式是否还发布，属于编辑器里的“警告”，不在这里做。
 */
object ActivityShortcutParams {

    /**
     * [ActivityShortcutCodec] 用这些字符做字段/列表分隔。
     * 用户从 dumpsys、日志或网页里粘贴时可能带进来，混进 label/URI 会把整份列表读串。
     */
    private const val FORBIDDEN_CHARS = "\u001C\u001D\u001E\u001F"

    fun stripControlChars(value: String): String = value.filterNot { it in FORBIDDEN_CHARS }

    /**
     * 把 `com.foo/com.foo.Bar`（dumpsys 里常见的组件写法）拆成包名与类名。
     * 不是这个形状就返回 null，调用方按普通包名处理。
     */
    fun splitComponent(input: String): Pair<String, String>? {
        val text = stripControlChars(input).trim()
        val slash = text.indexOf('/')
        if (slash <= 0 || slash >= text.lastIndex) return null
        val packageName = text.substring(0, slash).trim()
        val className = text.substring(slash + 1).trim()
        if (packageName.isEmpty() || className.isEmpty()) return null
        if (className.contains('/')) return null
        if (packageName.any { it.isWhitespace() } || className.any { it.isWhitespace() }) return null
        return packageName to className
    }

    /** `.Bar` 这种相对类名补成完整类名；其余原样返回。 */
    fun normalizeClassName(packageName: String, className: String): String {
        val trimmed = stripControlChars(className).trim()
        if (!trimmed.startsWith(".")) return trimmed
        return packageName.trim() + trimmed
    }

    /** Intent URI 文本框：一行一条，忽略空行与重复。 */
    fun parseIntentUris(raw: String): List<String> =
        raw.lineSequence()
            .map { stripControlChars(it).trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()

    /**
     * 按条目原本的类型重建：只写当前类型的字段，其余清空，避免 `kind` 漂移
     * （`kind` 是从字段推断的，残留字段会让 Activity 条目变成 Intent 条目）。
     *
     * [isShell] 只对 INTENT 有意义：Shell 条目的宿主包名固定，命令写回 `cebianshell:` URI，
     * 并保留原 `shortcutId`（图标解析要用它回溯命令来源）。
     */
    fun build(
        base: ActivityShortcut,
        label: String,
        packageName: String,
        activityClassName: String,
        shortcutId: String,
        shellCommand: String,
        intentUris: List<String>,
        isShell: Boolean,
        isAppShortcut: Boolean = base.isAppShortcut,
    ): ActivityShortcut {
        val cleanLabel = stripControlChars(label).trim()
        val cleanPackage = stripControlChars(packageName).trim()
        val cleanShortcutId = stripControlChars(shortcutId).trim()
        return when (base.kind) {
            ActivityShortcutKind.COMPONENT -> base.copy(
                label = cleanLabel,
                packageName = cleanPackage,
                activityClassName = normalizeClassName(cleanPackage, activityClassName),
                shortcutId = "",
                intentUris = emptyList(),
            )

            ActivityShortcutKind.DYNAMIC -> base.copy(
                label = cleanLabel,
                packageName = cleanPackage,
                activityClassName = "",
                shortcutId = cleanShortcutId,
                intentUris = emptyList(),
            )

            ActivityShortcutKind.INTENT -> {
                when {
                    isShell -> base.copy(
                        label = cleanLabel,
                        packageName = ActivityShortcutShellSupport.HOST_PACKAGE,
                        activityClassName = "",
                        intentUris = listOf(
                            ActivityShortcutShellSupport.encodeShellUri(
                                stripControlChars(shellCommand).trim(),
                            ),
                        ),
                    )

                    isAppShortcut -> base.copy(
                        label = cleanLabel,
                        packageName = cleanPackage,
                        activityClassName = "",
                        shortcutId = cleanShortcutId,
                        // 改包名/改 ID 后旧 intent URI 已不对应当前快捷方式，清掉避免误导。
                        intentUris = if (cleanShortcutId == base.shortcutId && cleanPackage == base.packageName) {
                            base.intentUris
                        } else {
                            emptyList()
                        },
                    )

                    else -> base.copy(
                        label = cleanLabel,
                        packageName = cleanPackage,
                        activityClassName = "",
                        shortcutId = "",
                        intentUris = intentUris
                            .map { stripControlChars(it).trim() }
                            .filter { it.isNotEmpty() }
                            .distinct(),
                    )
                }
            }
        }
    }

    /** 合并重复目标：保留正在编辑的名称/图标，缺图标时继承被删条目的图标。 */
    fun merge(kept: ActivityShortcut, dropped: ActivityShortcut): ActivityShortcut =
        kept.copy(iconPath = kept.iconPath ?: dropped.iconPath)

    /** 命中同一 `identityKey` 的其它条目；没有则 null。 */
    fun duplicateOf(
        shortcuts: List<ActivityShortcut>,
        candidate: ActivityShortcut,
    ): ActivityShortcut? = shortcuts.firstOrNull {
        it.id != candidate.id && it.identityKey() == candidate.identityKey()
    }

    fun replace(
        shortcuts: List<ActivityShortcut>,
        updated: ActivityShortcut,
    ): List<ActivityShortcut> = shortcuts.map { if (it.id == updated.id) updated else it }
}
