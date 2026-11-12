package com.slideindex.app.gesture

/**
 * 「打开小组件面板」动作（[GestureAction.WidgetPopupOverlay]）里的**页引用**维护。
 *
 * 动作只存 `WidgetPanelPage.id`，页被删除后如果不改写引用，运行期会静默回落到第一页 ——
 * 用户删一页，别的绑定跟着换目标，和「快速启动器面板」当年踩的是同一个坑
 * （见 `QuickLauncherPanelMutator.normalizeQuickLauncherAction` 的注释）。
 *
 * 设置层里同一个动作有两种落库形态，[remap] 都要覆盖：
 * - 动作负载：`<动作 id>\u001C<页 id>`（快速启动器条目、圆环槽位、悬浮球手势等）
 * - 手势规则相邻字段：`<动作 id>\u001F<页 id>`（`GestureRuleCodec` 的字段分隔符）
 *
 * 页 id 是十进制 Long，`"1"` 是 `"12"` 的前缀，所以匹配时必须校验「id 之后不再是数字」。
 */
object WidgetPanelActionRefs {

    private const val ACTION_PAYLOAD_SEP = '\u001C'
    private const val RULE_FIELD_SEP = '\u001F'

    /**
     * 把空/失效的页引用钉到 [fallbackPageId]。
     *
     * 写入时用：绑定那一刻就把具体 id 落库，之后增删页不会漂移。
     * 非「打开小组件面板」动作原样返回。
     */
    fun normalize(
        action: GestureAction,
        validPageIds: Set<String>,
        fallbackPageId: String,
    ): GestureAction {
        if (action !is GestureAction.WidgetPopupOverlay) return action
        if (action.panelId.isNotBlank() && action.panelId in validPageIds) return action
        if (fallbackPageId.isBlank()) return action
        return action.copy(panelId = fallbackPageId)
    }

    /**
     * 把已编码内容里指向 [removedPageId] 的「打开小组件面板」动作改写到 [fallbackPageId]。
     *
     * 与 `QuickLauncherItemCodec.remapPanelReferences` 同构：只做精确匹配，
     * 命中后紧跟的字符不能仍然是页 id 字符（数字）。
     */
    fun remap(raw: String, removedPageId: String, fallbackPageId: String): String {
        if (raw.isEmpty() || removedPageId.isEmpty() || removedPageId == fallbackPageId) return raw
        if (removedPageId !in raw) return raw
        var result = raw
        for (separator in SEPARATORS) {
            result = replaceReference(result, separator, removedPageId, fallbackPageId)
        }
        return result
    }

    fun remap(values: Set<String>, removedPageId: String, fallbackPageId: String): Set<String> {
        if (values.isEmpty()) return values
        if (removedPageId.isEmpty() || removedPageId == fallbackPageId) return values
        if (values.none { removedPageId in it }) return values
        return values.mapTo(LinkedHashSet(values.size)) {
            remap(it, removedPageId, fallbackPageId)
        }
    }

    private val SEPARATORS = listOf(ACTION_PAYLOAD_SEP, RULE_FIELD_SEP)

    private fun replaceReference(
        raw: String,
        separator: Char,
        removedPageId: String,
        fallbackPageId: String,
    ): String {
        val replacement = "${GestureActionType.WIDGET_POPUP_OVERLAY.id}$separator$fallbackPageId"
        val needle = "${GestureActionType.WIDGET_POPUP_OVERLAY.id}$separator$removedPageId"
        var index = raw.indexOf(needle)
        if (index < 0) return raw
        val builder = StringBuilder(raw.length + replacement.length)
        var cursor = 0
        while (index >= 0) {
            val end = index + needle.length
            builder.append(raw, cursor, index)
            if (end >= raw.length || !isPageIdChar(raw[end])) {
                builder.append(replacement)
            } else {
                // 命中的是「同前缀但更长」的页 id（1 vs 12），原样保留。
                builder.append(raw, index, end)
            }
            cursor = end
            index = raw.indexOf(needle, end)
        }
        builder.append(raw, cursor, raw.length)
        return builder.toString()
    }

    private fun isPageIdChar(char: Char): Boolean = char.isDigit()
}
