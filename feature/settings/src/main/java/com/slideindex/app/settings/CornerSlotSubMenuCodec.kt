package com.slideindex.app.settings

import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.launcher.QuickLauncherItemCodec

object CornerSlotSubMenuCodec {
    private const val ENTRY_SEP = "\u001D"
    private const val ITEM_SEP = "\u001B"

    fun defaultSlotSubMenus(): List<CornerSlotSubMenuConfig> =
        List(CornerRadialMenuCodec.MAX_SLOT_COUNT) { CornerSlotSubMenuConfig() }

    fun decode(encoded: Set<String>, defaults: List<CornerSlotSubMenuConfig>): List<CornerSlotSubMenuConfig> {
        if (encoded.isEmpty()) return normalizeSlotSubMenus(defaults)
        val byIndex = encoded.mapNotNull { entry -> decodeEntry(entry) }.toMap()
        val base = normalizeSlotSubMenus(defaults)
        return List(CornerRadialMenuCodec.MAX_SLOT_COUNT) { index ->
            byIndex[index] ?: base.getOrElse(index) { CornerSlotSubMenuConfig() }
        }
    }

    fun encode(slots: List<CornerSlotSubMenuConfig>): Set<String> =
        normalizeSlotSubMenus(slots).mapIndexedNotNull { index, config ->
            if (!config.enabled && config.items.isEmpty()) return@mapIndexedNotNull null
            encodeEntry(index, config)
        }.toSet()

    fun normalizeSlotSubMenus(slots: List<CornerSlotSubMenuConfig>): List<CornerSlotSubMenuConfig> =
        List(CornerRadialMenuCodec.MAX_SLOT_COUNT) { index ->
            slots.getOrElse(index) { CornerSlotSubMenuConfig() }
        }

    private fun decodeEntry(entry: String): Pair<Int, CornerSlotSubMenuConfig>? {
        val parts = entry.split(ENTRY_SEP, limit = 4)
        if (parts.size < 3) return null
        val index = parts[0].toIntOrNull() ?: return null
        val enabled = parts[1] == "1"
        val count = parts[2].toIntOrNull() ?: return null
        val itemsBlob = parts.getOrElse(3) { "" }
        val payloads = when {
            itemsBlob.isBlank() -> emptyList()
            ITEM_SEP in itemsBlob -> itemsBlob.split(ITEM_SEP)
            else -> decodeLegacyPayloads(parts.drop(3), count)
        }
        val items = payloads.mapNotNull { payload ->
            // 反馈 #385：二级菜单现在可以放任意动作（应用 / 内置动作 / 快捷方式），
            // 老数据里的快捷方式同样是 GestureAction，统一按动作解析。
            QuickLauncherItemCodec.parseActionPayload(payload)
        }
        return index to CornerSlotSubMenuConfig(enabled = enabled, items = items)
    }

    /** 旧版用 [ENTRY_SEP] 拼接多条 payload，与快捷方式 label 分隔符冲突时会拆碎 payload。 */
    private fun decodeLegacyPayloads(parts: List<String>, count: Int): List<String> {
        if (parts.isEmpty()) return emptyList()
        if (parts.size == count) return parts
        if (parts.size < count) return emptyList()
        return buildList {
            var index = 0
            while (index < parts.size && size < count) {
                var payload = parts[index]
                index++
                while (
                    index < parts.size &&
                    QuickLauncherItemCodec.parseActionPayload(payload) == null
                ) {
                    payload += ENTRY_SEP + parts[index]
                    index++
                }
                add(payload)
            }
        }
    }

    private fun encodeEntry(index: Int, config: CornerSlotSubMenuConfig): String {
        val payloads = config.items.map { QuickLauncherItemCodec.encodeActionPayload(it) }
        return buildString {
            append(index)
            append(ENTRY_SEP)
            append(if (config.enabled) "1" else "0")
            append(ENTRY_SEP)
            append(payloads.size)
            if (payloads.isNotEmpty()) {
                append(ENTRY_SEP)
                append(payloads.joinToString(ITEM_SEP))
            }
        }
    }
}
