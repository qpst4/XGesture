package com.slideindex.app.launcher

import android.content.Intent
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureActionType
import com.slideindex.app.gesture.LaunchWindowMode
import com.slideindex.app.gesture.SlotPickerKind
import com.slideindex.app.gesture.sanitizeForSlotPicker
import com.slideindex.app.gesture.normalized

enum class QuickLauncherItemType(val id: Int) {
    APP(0),
    SHORTCUT(1),
    WIDGET(2),
    FOLDER(3),
    ACTION(4),
    ;

    companion object {
        fun fromId(id: Int): QuickLauncherItemType =
            entries.firstOrNull { it.id == id } ?: APP
    }
}

/**
 * 应用条目 / 启动动作的目标：主实例（user 0）或某个分身空间。
 *
 * 编码只用于 payload 字符串：主实例保持裸包名（旧数据完全兼容），
 * 分身追加 `\u0002<userId>`；包名本身不可能包含控制字符，解析无歧义。
 */
data class AppTarget(
    val packageName: String,
    val cloneUserId: Int = 0,
) {
    val isClone: Boolean get() = cloneUserId > 0

    companion object {
        private const val CLONE_SEP = "\u0002"

        fun encode(packageName: String, cloneUserId: Int): String =
            if (cloneUserId > 0) "$packageName$CLONE_SEP$cloneUserId" else packageName

        fun parse(payload: String): AppTarget {
            if (payload.isEmpty()) return AppTarget(payload)
            val sep = payload.lastIndexOf(CLONE_SEP)
            if (sep <= 0 || sep >= payload.lastIndex) return AppTarget(payload)
            val userId = payload.substring(sep + 1).toIntOrNull()
            if (userId == null || userId <= 0) return AppTarget(payload)
            return AppTarget(payload.substring(0, sep), userId)
        }
    }
}

data class QuickLauncherItem(
    val type: QuickLauncherItemType,
    val payload: String,
    val label: String = "",
) {
    val isFolder: Boolean get() = type == QuickLauncherItemType.FOLDER

    /** 仅 APP 条目有意义；其他类型返回 null。 */
    val appTarget: AppTarget?
        get() = if (type == QuickLauncherItemType.APP) AppTarget.parse(payload) else null

    fun folderItems(): List<QuickLauncherItem> =
        if (type == QuickLauncherItemType.FOLDER) QuickLauncherFolderCodec.decodeFolderPayload(payload) else emptyList()

    fun withFolderItems(items: List<QuickLauncherItem>): QuickLauncherItem =
        if (type == QuickLauncherItemType.FOLDER) copy(payload = QuickLauncherFolderCodec.encodeFolderPayload(items)) else this

    fun withFolderLabel(newLabel: String): QuickLauncherItem =
        copy(label = newLabel)

    companion object {
        fun app(packageName: String, label: String = "", cloneUserId: Int = 0) =
            QuickLauncherItem(
                QuickLauncherItemType.APP,
                AppTarget.encode(packageName, cloneUserId),
                label,
            )

        fun folder(label: String = "", items: List<QuickLauncherItem> = emptyList()) =
            QuickLauncherItem(
                QuickLauncherItemType.FOLDER,
                QuickLauncherFolderCodec.encodeFolderPayload(items),
                label,
            )

        fun shortcut(componentFlat: String, label: String = "") =
            QuickLauncherItem(QuickLauncherItemType.SHORTCUT, componentFlat, label)

        fun intentShortcut(intentUri: String, label: String = "", hostPackage: String? = null) =
            QuickLauncherItem(
                QuickLauncherItemType.SHORTCUT,
                "${QuickLauncherItemCodec.INTENT_PAYLOAD_PREFIX}${
                    QuickLauncherItemCodec.encodeIntentPayloadBody(intentUri, hostPackage)
                }",
                label,
            )

        fun intentShortcuts(intentUris: List<String>, label: String = "", hostPackage: String? = null) =
            QuickLauncherItem(
                QuickLauncherItemType.SHORTCUT,
                "${QuickLauncherItemCodec.INTENT_LIST_PAYLOAD_PREFIX}${
                    intentUris.joinToString(QuickLauncherItemCodec.INTENT_LIST_SEP) {
                        QuickLauncherItemCodec.encodeIntentPayloadBody(it, hostPackage)
                    }
                }",
                label,
            )

        fun dynamicShortcut(packageName: String, shortcutId: String, label: String = "") =
            QuickLauncherItem(
                QuickLauncherItemType.SHORTCUT,
                "$packageName${QuickLauncherItemCodec.SHORTCUT_PAYLOAD_SEP}$shortcutId",
                label,
            )

        fun action(action: GestureAction, label: String = "") =
            QuickLauncherItem(
                QuickLauncherItemType.ACTION,
                QuickLauncherItemCodec.encodeActionPayload(
                    action.sanitizeForSlotPicker(SlotPickerKind.OverlayTap),
                ),
                label,
            )

        fun widget(appWidgetId: Int, label: String = "") =
            QuickLauncherItem(QuickLauncherItemType.WIDGET, appWidgetId.toString(), label)
    }
}

object QuickLauncherFolderCodec {
    private const val FOLDER_ITEM_SEP = "\u001A"
    private const val FOLDER_FIELD_SEP = "\u0019"

    fun encodeFolderPayload(items: List<QuickLauncherItem>): String {
        if (items.isEmpty()) return ""
        val validItems = items.filter { it.type != QuickLauncherItemType.FOLDER }
        if (validItems.isEmpty()) return ""
        return validItems.joinToString(FOLDER_ITEM_SEP) { item ->
            "${item.type.id}$FOLDER_FIELD_SEP${item.payload}$FOLDER_FIELD_SEP${item.label}"
        }
    }

    fun decodeFolderPayload(payload: String): List<QuickLauncherItem> {
        if (payload.isBlank()) return emptyList()
        return payload.split(FOLDER_ITEM_SEP).mapNotNull { raw ->
            if (raw.isBlank()) return@mapNotNull null
            val firstSep = raw.indexOf(FOLDER_FIELD_SEP)
            if (firstSep <= 0) return@mapNotNull null
            val lastSep = raw.lastIndexOf(FOLDER_FIELD_SEP)
            if (lastSep <= firstSep) return@mapNotNull null
            val typeId = raw.substring(0, firstSep).toIntOrNull() ?: return@mapNotNull null
            if (typeId == QuickLauncherItemType.FOLDER.id) return@mapNotNull null
            val type = QuickLauncherItemType.fromId(typeId)
            val itemPayload = raw.substring(firstSep + 1, lastSep)
            val label = raw.substring(lastSep + 1)
            QuickLauncherItem(type, itemPayload, label)
        }
    }
}

object QuickLauncherItemCodec {
    private const val SEP = "\u001E"
    const val SHORTCUT_PAYLOAD_SEP = "\u001C"
    const val INTENT_PAYLOAD_PREFIX = "i:"
    const val INTENT_LIST_PAYLOAD_PREFIX = "is:"
    const val INTENT_LIST_SEP = "\u001F"

    /** 「启动应用」动作正文里包名与启动形态 id 的分隔符。 */
    const val LAUNCH_WINDOW_MODE_SEP = "\u001D"

    fun encode(item: QuickLauncherItem): String =
        listOf(item.type.id, item.payload, item.label).joinToString(SEP)

    fun decode(raw: String): QuickLauncherItem? {
        val firstSep = raw.indexOf(SEP)
        if (firstSep <= 0) return null
        val typeId = raw.substring(0, firstSep).toIntOrNull() ?: return null
        val type = QuickLauncherItemType.fromId(typeId)
        val lastSep = raw.lastIndexOf(SEP)
        if (lastSep <= firstSep) return null
        val payload = raw.substring(firstSep + 1, lastSep)
        val label = raw.substring(lastSep + 1)
        return QuickLauncherItem(type, payload, label)
    }

    fun encodeActionPayload(action: GestureAction): String =
        "${action.type.id}$SHORTCUT_PAYLOAD_SEP${encodeActionBody(action)}"

    fun parseActionPayload(payload: String): GestureAction? {
        val index = payload.indexOf(SHORTCUT_PAYLOAD_SEP)
        if (index < 0) return null
        val typeId = payload.substring(0, index).toIntOrNull() ?: return null
        val body = payload.substring(index + 1)
        val type = GestureActionType.fromId(typeId)
        return decodeActionBody(type, body)?.normalized()
    }

    /**
     * 动作正文编码。除「启动应用」外与 `action.payload` 一致；
     * 「启动应用」按 `包名[\u0002userId][\u001D启动形态]` 编码：克隆 id 在前、启动形态在后，
     * 旧数据两种后缀都没有，按「主实例 + 跟随全局」解析。
     *
     * 自带动作类型字段的编码器（如 [com.slideindex.app.gesture.GestureRuleCodec]）应直接复用本函数，
     * 不要另写一份正文编码，否则「启动应用」的启动形态会在该存储路径上静默丢失。
     */
    fun encodeActionBody(action: GestureAction): String {
        if (action !is GestureAction.LaunchApp) return action.payload
        val body = AppTarget.encode(action.packageName, action.cloneUserId)
        val mode = action.windowMode
        if (mode == LaunchWindowMode.FOLLOW_GLOBAL) return body
        return "$body$LAUNCH_WINDOW_MODE_SEP${mode.id}"
    }

    /** [encodeActionBody] 的逆操作；无后缀或后缀畸形时按主实例 / [LaunchWindowMode.FOLLOW_GLOBAL] 解析。 */
    fun decodeActionBody(type: GestureActionType, body: String): GestureAction? {
        if (type != GestureActionType.LAUNCH_APP) return GestureAction.from(type, body)
        var payload = body
        var mode = LaunchWindowMode.FOLLOW_GLOBAL
        val modeIndex = payload.lastIndexOf(LAUNCH_WINDOW_MODE_SEP)
        if (modeIndex > 0) {
            val modeId = payload.substring(modeIndex + 1).toIntOrNull()
            val parsedMode = modeId?.let(LaunchWindowMode::fromId)
            if (parsedMode != null && parsedMode.id == modeId) {
                mode = parsedMode
            }
            // 与旧版一致：后缀畸形也剥掉，只按剩余正文解析（否则包名里会混进启动形态）。
            payload = payload.substring(0, modeIndex)
        }
        val target = AppTarget.parse(payload)
        return GestureAction.LaunchApp(target.packageName, mode, target.cloneUserId)
    }

    fun sanitizeOverlayTapItem(item: QuickLauncherItem): QuickLauncherItem = when (item.type) {
        QuickLauncherItemType.ACTION -> {
            val action = parseActionPayload(item.payload)?.sanitizeForSlotPicker(SlotPickerKind.OverlayTap)
                ?: return item
            item.copy(payload = encodeActionPayload(action))
        }
        QuickLauncherItemType.FOLDER -> item.withFolderItems(
            item.folderItems().map { sanitizeOverlayTapItem(it) },
        )
        else -> item
    }

    fun sanitizeOverlayTapItems(items: List<QuickLauncherItem>): List<QuickLauncherItem> =
        items.map { sanitizeOverlayTapItem(it) }

    fun actionKey(action: GestureAction): String = encodeActionPayload(action)

    /**
     * 把已编码内容里指向 [removedPanelId] 的「打开快速启动器」动作改写到 [fallbackPanelId]。
     *
     * 设置层里同一个动作有两种落库形态，都要覆盖：
     * - 动作负载：`<动作 id>\u001C<面板 id>`（快速启动器条目、伸展面板、悬浮球手势等）
     * - 手势规则相邻字段：`<动作 id>\u001F<面板 id>`（GestureRuleCodec 的字段分隔符）
     *
     * 只做精确匹配：命中后面板 id 之后出现的字符不能仍属于 id 字符，
     * 避免把「同前缀但更长」的面板 id 误伤。
     */
    fun remapPanelReferences(raw: String, removedPanelId: String, fallbackPanelId: String): String {
        if (raw.isEmpty() || removedPanelId.isEmpty() || removedPanelId == fallbackPanelId) return raw
        if (removedPanelId !in raw) return raw
        var result = raw
        for (separator in PANEL_REFERENCE_SEPARATORS) {
            result = replacePanelReference(result, separator, removedPanelId, fallbackPanelId)
        }
        return result
    }

    fun remapPanelReferences(
        values: Set<String>,
        removedPanelId: String,
        fallbackPanelId: String,
    ): Set<String> {
        if (values.isEmpty()) return values
        if (removedPanelId.isEmpty() || removedPanelId == fallbackPanelId) return values
        if (values.none { removedPanelId in it }) return values
        return values.mapTo(LinkedHashSet(values.size)) {
            remapPanelReferences(it, removedPanelId, fallbackPanelId)
        }
    }

    private val PANEL_REFERENCE_SEPARATORS = listOf(SHORTCUT_PAYLOAD_SEP, LIST_SEP)

    private fun replacePanelReference(
        raw: String,
        separator: String,
        removedPanelId: String,
        fallbackPanelId: String,
    ): String {
        val replacement = "${GestureActionType.QUICK_LAUNCHER.id}$separator$fallbackPanelId"
        val needle = "${GestureActionType.QUICK_LAUNCHER.id}$separator$removedPanelId"
        var index = raw.indexOf(needle)
        if (index < 0) return raw
        val builder = StringBuilder(raw.length + replacement.length)
        var cursor = 0
        while (index >= 0) {
            val end = index + needle.length
            builder.append(raw, cursor, index)
            if (end >= raw.length || !isPanelIdChar(raw[end])) {
                builder.append(replacement)
            } else {
                // 命中的是「同前缀但更长」的面板 id，原样保留。
                builder.append(raw, index, end)
            }
            cursor = end
            index = raw.indexOf(needle, end)
        }
        builder.append(raw, cursor, raw.length)
        return builder.toString()
    }

    private fun isPanelIdChar(char: Char): Boolean =
        char.isLetterOrDigit() || char == '-' || char == '_'

    fun parseIntentPayload(payload: String): String? {
        if (!payload.startsWith(INTENT_PAYLOAD_PREFIX)) return null
        val body = payload.removePrefix(INTENT_PAYLOAD_PREFIX).takeIf { it.isNotBlank() } ?: return null
        return extractIntentUriFromBody(body)
    }

    fun parseIntentListPayload(payload: String): List<String>? {
        if (!payload.startsWith(INTENT_LIST_PAYLOAD_PREFIX)) return null
        return payload.removePrefix(INTENT_LIST_PAYLOAD_PREFIX)
            .split(INTENT_LIST_SEP)
            .mapNotNull { part -> part.takeIf { it.isNotBlank() }?.let { extractIntentUriFromBody(it) } }
            .takeIf { it.isNotEmpty() }
    }

    fun encodeIntentPayloadBody(intentUri: String, hostPackage: String? = null): String {
        if (hostPackage.isNullOrBlank()) return intentUri
        return "$hostPackage$SHORTCUT_PAYLOAD_SEP$intentUri"
    }

    private fun extractIntentUriFromBody(body: String): String {
        val sep = body.indexOf(SHORTCUT_PAYLOAD_SEP)
        if (sep <= 0) return body
        val prefix = body.substring(0, sep)
        if (prefix.contains('.') && !prefix.startsWith("#")) {
            return body.substring(sep + 1)
        }
        return body
    }

    private fun parseIntentHostPackage(payload: String): String? {
        if (!payload.startsWith(INTENT_PAYLOAD_PREFIX)) return null
        return parseIntentHostPackageBody(payload.removePrefix(INTENT_PAYLOAD_PREFIX))
    }

    private fun parseIntentHostPackageBody(body: String): String? {
        val sep = body.indexOf(SHORTCUT_PAYLOAD_SEP)
        if (sep <= 0) return null
        val prefix = body.substring(0, sep)
        return prefix.takeIf { it.contains('.') && !it.startsWith("#") }
    }

    fun shortcutItemKey(item: QuickLauncherItem): String? {
        if (item.type != QuickLauncherItemType.SHORTCUT) return null
        parseIntentListPayload(item.payload)?.let { uris ->
            return "intents:${uris.joinToString(INTENT_LIST_SEP)}"
        }
        parseIntentPayload(item.payload)?.let { return "intent:$it" }
        parseShortcutPayload(item.payload)?.let { (pkg, id) ->
            return shortcutKey(pkg, id)
        }
        return item.payload.takeIf { it.isNotBlank() }
    }

    fun shortcutToggleKey(packageName: String, shortcutId: String, intentUris: List<String>? = null): String {
        intentUris?.let { uris ->
            return if (uris.size == 1) "intent:${uris[0]}" else "intents:${uris.joinToString(INTENT_LIST_SEP)}"
        }
        return shortcutKey(packageName, shortcutId)
    }

    fun parseShortcutPayload(payload: String): Pair<String, String>? {
        val index = payload.indexOf(SHORTCUT_PAYLOAD_SEP)
        if (index <= 0 || index >= payload.lastIndex) return null
        val packageName = payload.substring(0, index)
        val shortcutId = payload.substring(index + 1)
        if (packageName.isBlank() || shortcutId.isBlank()) return null
        return packageName to shortcutId
    }

    fun resolveHostPackageName(
        payload: String,
        fallbackPackageForIntentUri: (String) -> String? = { null },
    ): String? {
        if (payload.startsWith(INTENT_PAYLOAD_PREFIX)) {
            parseIntentHostPackage(payload)?.let { return it }
            parseIntentPayload(payload)?.let { uri ->
                packageNameFromIntentUri(uri)?.let { return it }
                fallbackPackageForIntentUri(uri)?.let { return it }
            }
            return null
        }
        if (payload.startsWith(INTENT_LIST_PAYLOAD_PREFIX)) {
            val body = payload.removePrefix(INTENT_LIST_PAYLOAD_PREFIX)
            body.split(INTENT_LIST_SEP).firstOrNull { it.isNotBlank() }?.let { part ->
                parseIntentHostPackageBody(part)?.let { return it }
                extractIntentUriFromBody(part).let { uri ->
                    packageNameFromIntentUri(uri)?.let { return it }
                    fallbackPackageForIntentUri(uri)?.let { return it }
                }
            }
            return null
        }
        parseShortcutPayload(payload)?.first?.let { return it }
        if (payload.startsWith("c:")) {
            val componentFlat = payload.removePrefix("c:").substringBefore('\u001D')
            return packageNameFromComponentFlat(componentFlat)
        }
        if ('/' in payload) {
            return packageNameFromComponentFlat(payload.substringBefore('/'))
        }
        return null
    }

    private fun packageNameFromComponentFlat(componentFlat: String): String? =
        componentFlat.trim().takeIf { it.isNotBlank() }

    private fun packageNameFromIntentUri(uri: String): String? = runCatching {
        val intent = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)
        intent.`package`?.takeIf { it.isNotBlank() }
            ?: intent.component?.packageName?.takeIf { it.isNotBlank() }
    }.getOrNull() ?: packageNameFromIntentUriText(uri)

    private fun packageNameFromIntentUriText(uri: String): String? {
        Regex("""(?:^|;)package=([^;]+)""").find(uri)?.groupValues?.get(1)?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        Regex("""(?:^|;)component=([^;/]+)""").find(uri)?.groupValues?.get(1)?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        return null
    }

    fun shortcutKey(packageName: String, shortcutId: String): String =
        "$packageName$SHORTCUT_PAYLOAD_SEP$shortcutId"

    private const val LIST_SEP = "\u001F"

    fun encodeAll(items: List<QuickLauncherItem>): Set<String> =
        if (items.isEmpty()) emptySet() else setOf(items.joinToString(LIST_SEP) { encode(it) })

    fun decodeAll(raw: Set<String>): List<QuickLauncherItem> {
        if (raw.isEmpty()) return emptyList()
        return if (raw.size == 1) {
            decodeConcatenatedItems(raw.first())
        } else {
            raw.mapNotNull { decode(it) }
        }
    }

    /**
     * 多项 blob 用 [LIST_SEP] 拼接；项内 payload（如带显示名的 OPEN_LINK）也可能含 \\u001F，
     * 仅在「下一项」边界（[LIST_SEP] + typeId + [SEP]）处切开。
     */
    private fun decodeConcatenatedItems(blob: String): List<QuickLauncherItem> {
        if (blob.isBlank()) return emptyList()
        val splitIndices = mutableListOf<Int>()
        var searchFrom = 0
        while (searchFrom < blob.length) {
            val sepIndex = blob.indexOf(LIST_SEP, searchFrom)
            if (sepIndex < 0) break
            if (isEncodedItemBoundary(blob, sepIndex)) {
                splitIndices.add(sepIndex)
            }
            searchFrom = sepIndex + LIST_SEP.length
        }
        if (splitIndices.isEmpty()) {
            return listOfNotNull(decode(blob))
        }
        val items = mutableListOf<QuickLauncherItem>()
        var start = 0
        for (splitAt in splitIndices) {
            decode(blob.substring(start, splitAt))?.let { items.add(it) }
            start = splitAt + LIST_SEP.length
        }
        decode(blob.substring(start))?.let { items.add(it) }
        return items
    }

    private fun isEncodedItemBoundary(blob: String, listSepIndex: Int): Boolean {
        val afterListSep = listSepIndex + LIST_SEP.length
        if (afterListSep >= blob.length) return false
        val typeChar = blob[afterListSep]
        if (typeChar !in '0'..'9') return false
        val typeId = typeChar.code - '0'.code
        if (QuickLauncherItemType.entries.none { it.id == typeId }) return false
        val fieldSepIndex = afterListSep + 1
        return fieldSepIndex < blob.length && blob[fieldSepIndex] == SEP[0]
    }
}

/** 是否在图标右下角显示快捷方式角标（蜂窝启动器、快速启动器共用）。 */
fun QuickLauncherItem.showsShortcutBadge(): Boolean =
    type == QuickLauncherItemType.SHORTCUT ||
        (type == QuickLauncherItemType.ACTION && payload.startsWith("action:"))

/**
 * 是否在图标右下角显示「运行宏」角标。
 *
 * 宏格子显示的是目标 App 的图标（见 `MacroTargetIconResolver`），
 * 不额外标一下就和「打开这个 App」长得一样；快捷方式 / Shell 命令各有自己的角标，
 * 宏属于同一类"有额外行为"的条目，所以用第三枚角标区分。
 */
fun QuickLauncherItem.showsMacroBadge(): Boolean {
    if (type != QuickLauncherItemType.ACTION) return false
    return QuickLauncherItemCodec.parseActionPayload(payload) is GestureAction.RunMacro
}

/**
 * 将索引 [from] 处的图标合并到索引 [target] 处的图标/文件夹中。
 * 若 [from] 本身是文件夹，则拒绝合并以防止嵌套。
 */
fun List<QuickLauncherItem>.mergeIntoFolder(
    from: Int,
    target: Int,
    defaultFolderLabel: String = "",
): List<QuickLauncherItem> {
    if (from == target || from !in indices || target !in indices) return this
    val fromItem = this[from]
    val targetItem = this[target]
    if (fromItem.isFolder) return this

    val mergedFolder = if (targetItem.isFolder) {
        targetItem.withFolderItems(targetItem.folderItems() + fromItem)
    } else {
        QuickLauncherItem.folder(
            label = defaultFolderLabel,
            items = listOf(targetItem, fromItem),
        )
    }

    return mapIndexedNotNull { index, item ->
        when (index) {
            from -> null
            target -> mergedFolder
            else -> item
        }
    }
}

/**
 * 用 [children] 替换索引 [index] 处文件夹的子项。
 * [index] 越界或不是文件夹时原样返回，避免调用方拿着过期索引改到别的条目上。
 */
fun List<QuickLauncherItem>.withFolderChildren(
    index: Int,
    children: List<QuickLauncherItem>,
): List<QuickLauncherItem> {
    if (index !in indices) return this
    val folder = this[index]
    if (!folder.isFolder) return this
    return toMutableList().also { it[index] = folder.withFolderItems(children) }
}

/** 重命名索引 [index] 处的文件夹；名称留空表示沿用默认文案。 */
fun List<QuickLauncherItem>.renameFolder(index: Int, name: String): List<QuickLauncherItem> {
    if (index !in indices) return this
    val folder = this[index]
    if (!folder.isFolder) return this
    return toMutableList().also { it[index] = folder.withFolderLabel(name) }
}

/** 解散索引 [index] 处的文件夹：子项按原顺序摊回根列表，空文件夹则直接移除。 */
fun List<QuickLauncherItem>.dissolveFolder(index: Int): List<QuickLauncherItem> {
    if (index !in indices) return this
    val folder = this[index]
    if (!folder.isFolder) return this
    return toMutableList().also { list ->
        list.removeAt(index)
        list.addAll(index, folder.folderItems())
    }
}

/**
 * 取 [folderIndex] 处文件夹的子项；索引越界或不是文件夹时返回自身（面板根列表）。
 * 添加流程用它把「已添加」状态与写入目标一起切到目标文件夹。
 */
fun List<QuickLauncherItem>.resolveFolderItems(folderIndex: Int): List<QuickLauncherItem> =
    if (folderIndex in indices && this[folderIndex].isFolder) this[folderIndex].folderItems() else this

/**
 * 把 [newItems] 写回 [folderIndex] 处文件夹；[folderIndex] 为负表示写面板根列表。
 * 索引失效（文件夹已被解散/删除）时原样返回，避免把子项写到根列表上。
 */
fun List<QuickLauncherItem>.withItemsAtFolder(
    folderIndex: Int,
    newItems: List<QuickLauncherItem>,
): List<QuickLauncherItem> {
    if (folderIndex < 0) return newItems
    if (folderIndex !in indices || !this[folderIndex].isFolder) return this
    return withFolderChildren(folderIndex, newItems)
}
