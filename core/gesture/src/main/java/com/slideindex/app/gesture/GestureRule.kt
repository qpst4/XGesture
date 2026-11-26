package com.slideindex.app.gesture

import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.overlay.PanelSide

data class GestureRule(
    val id: String,
    val side: PanelSide,
    val trigger: GestureTriggerType,
    val action: GestureAction,
    val priority: Int = 0,
    val enabled: Boolean = true,
    val triggerMode: GestureTriggerMode = GestureTriggerMode.DEFAULT,
    val handleId: String = TriggerHandle.DEFAULT_ID,
) {
    companion object {
        fun slot(
            side: PanelSide,
            trigger: GestureTriggerType,
            action: GestureAction,
            triggerMode: GestureTriggerMode = GestureTriggerMode.DEFAULT,
            handleId: String = TriggerHandle.DEFAULT_ID,
        ): GestureRule = GestureRule(
            id = slotId(side, trigger, handleId),
            side = side,
            trigger = trigger,
            action = action,
            priority = 0,
            enabled = true,
            triggerMode = triggerMode,
            handleId = handleId,
        )

        fun slotId(
            side: PanelSide,
            trigger: GestureTriggerType,
            handleId: String = TriggerHandle.DEFAULT_ID,
        ): String = "slot-${side.name.lowercase()}-$handleId-${trigger.id}"

        fun legacySlotId(side: PanelSide, trigger: GestureTriggerType): String =
            "slot-${side.name.lowercase()}-${trigger.id}"

        fun newId(): String = java.util.UUID.randomUUID().toString()
    }
}

object GestureRuleCodec {
    private const val SEP = "\u001F"
    private const val SEP_CHAR = '\u001F'

    fun encode(rule: GestureRule): String {
        return listOf(
            rule.id,
            rule.side.ordinal.toString(),
            rule.handleId,
            rule.trigger.id.toString(),
            rule.action.type.id.toString(),
            QuickLauncherItemCodec.encodeActionBody(rule.action),
            rule.priority.toString(),
            if (rule.enabled) "1" else "0",
            rule.triggerMode.id.toString(),
        ).joinToString(SEP)
    }

    fun decode(raw: String): GestureRule? {
        val sepCount = raw.count { it == SEP_CHAR }
        // 新格式 9 段。动作正文里可能自带分隔符（例如组合动作的子动作分隔符），
        // 所以不能按 split 的段数判断：按「前 5 段 + 正文整段 + 后 3 段」锚定。
        if (sepCount >= 8) {
            anchored(raw, headFields = 5, tailFields = 3)?.let { parts ->
                decodeNewAnchored(parts)?.let { return it }
            }
        }
        val parts = raw.split(SEP)
        return when (parts.size) {
            9 -> decodeNew(parts)
            8 -> decodeLegacy(parts)
            7 -> decodeLegacy(parts, hasTriggerMode = false)
            else -> null
        }
    }

    /**
     * 把 [raw] 拆成「[headFields] 段 + 中间正文 + [tailFields] 段」。
     *
     * 中间正文可以包含任意分隔符：前端取前 [headFields] 个分隔符，后端取最后
     * [tailFields] 个分隔符，中间整段原样保留。
     */
    private fun anchored(raw: String, headFields: Int, tailFields: Int): List<String>? {
        val positions = ArrayList<Int>()
        raw.forEachIndexed { index, ch -> if (ch == SEP_CHAR) positions.add(index) }
        if (positions.size < headFields + tailFields) return null
        val headEnd = positions[headFields - 1]
        val tailStart = positions[positions.size - tailFields]
        if (tailStart <= headEnd) return null
        val body = raw.substring(headEnd + 1, tailStart)
        val result = ArrayList<String>(headFields + 1 + tailFields)
        var start = 0
        for (index in 0 until headFields) {
            val end = positions[index]
            result.add(raw.substring(start, end))
            start = end + 1
        }
        result.add(body)
        start = tailStart + 1
        for (index in 1..tailFields) {
            val end = if (index == tailFields) raw.length else positions[positions.size - tailFields + index]
            result.add(raw.substring(start, end))
            start = end + 1
        }
        return result
    }

    private fun decodeNewAnchored(parts: List<String>): GestureRule? {
        val side = PanelSide.entries.getOrNull(parts[1].toIntOrNull() ?: return null) ?: return null
        val handleId = parts[2]
        val trigger = GestureTriggerType.fromId(parts[3].toIntOrNull() ?: return null) ?: return null
        val actionType = GestureActionType.fromId(parts[4].toIntOrNull() ?: return null)
        val priority = parts[6].toIntOrNull() ?: return null
        if (parts[7] != "0" && parts[7] != "1") return null
        val triggerMode = GestureTriggerMode.fromId(parts[8].toIntOrNull() ?: return null)
            ?: return null
        return GestureRule(
            id = parts[0],
            side = side,
            trigger = trigger,
            action = QuickLauncherItemCodec.decodeActionBody(actionType, parts[5])
                ?: GestureAction.None,
            priority = priority,
            enabled = parts[7] == "1",
            triggerMode = triggerMode,
            handleId = handleId,
        )
    }

    private fun decodeNew(parts: List<String>): GestureRule? {
        val side = PanelSide.entries.getOrNull(parts[1].toIntOrNull() ?: return null) ?: return null
        val handleId = parts[2]
        val trigger = GestureTriggerType.fromId(parts[3].toIntOrNull() ?: return null) ?: return null
        val actionType = GestureActionType.fromId(parts[4].toIntOrNull() ?: return null)
        val triggerMode = GestureTriggerMode.fromId(parts[8].toIntOrNull() ?: return null)
        return GestureRule(
            id = parts[0],
            side = side,
            trigger = trigger,
            action = QuickLauncherItemCodec.decodeActionBody(actionType, parts[5]) ?: GestureAction.None,
            priority = parts[6].toIntOrNull() ?: 0,
            enabled = parts[7] == "1",
            triggerMode = triggerMode,
            handleId = handleId,
        )
    }

    private fun decodeLegacy(parts: List<String>, hasTriggerMode: Boolean = true): GestureRule? {
        val side = PanelSide.entries.getOrNull(parts[1].toIntOrNull() ?: return null) ?: return null
        val trigger = GestureTriggerType.fromId(parts[2].toIntOrNull() ?: return null) ?: return null
        val actionType = GestureActionType.fromId(parts[3].toIntOrNull() ?: return null)
        val triggerMode = if (hasTriggerMode) {
            GestureTriggerMode.fromId(parts[7].toIntOrNull() ?: return null)
        } else {
            GestureTriggerMode.DEFAULT
        }
        return GestureRule(
            id = parts[0],
            side = side,
            trigger = trigger,
            action = QuickLauncherItemCodec.decodeActionBody(actionType, parts[4]) ?: GestureAction.None,
            priority = parts[5].toIntOrNull() ?: 0,
            enabled = parts[6] == "1",
            triggerMode = triggerMode,
            handleId = TriggerHandle.DEFAULT_ID,
        )
    }

    fun encodeAll(rules: List<GestureRule>): Set<String> = rules.map { encode(it) }.toSet()

    fun decodeAll(raw: Set<String>): List<GestureRule> =
        raw.mapNotNull { decode(it) }
            .map { rule -> rule.copy(action = rule.action.normalized()) }
            .sortedByDescending { it.priority }
}
