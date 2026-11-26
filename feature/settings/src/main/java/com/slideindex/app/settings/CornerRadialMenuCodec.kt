package com.slideindex.app.settings

import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.SlotPickerKind
import com.slideindex.app.gesture.sanitizeForSlotPicker
import com.slideindex.app.launcher.QuickLauncherItemCodec

object CornerRadialMenuCodec {
    /**
     * 五层槽位：内 3 / 5 / 7 / 9 / 11，共 35。
     * 前三层始终启用；第 4、5 层由设置开关决定（见 [CornerGestureSettings.enabledLayerCount]）。
     */
    const val SLOT_COUNT = 35
    const val LEGACY_SLOT_COUNT = 8

    /** 始终启用的层数，第 4 层起需要用户显式开启。 */
    const val BASE_LAYER_COUNT = 3

    /** 默认每圈数量：内 3 / 5 / 7 / 9 / 11。 */
    val DEFAULT_LAYER_SLOT_COUNTS = intArrayOf(3, 5, 7, 9, 11)

    /** 兼容旧代码的默认值引用。 */
    val LAYER_SLOT_COUNTS = DEFAULT_LAYER_SLOT_COUNTS

    private const val SEP = "\u001D"

    fun layerCount(): Int = DEFAULT_LAYER_SLOT_COUNTS.size

    /**
     * 把用户配置的每圈数量收敛到合法状态：
     * 固定 5 圈、每圈至少 1、总数必须等于 [SLOT_COUNT]（多退少补，从外圈开始调）。
     */
    fun normalizeLayerSlotCounts(raw: List<Int>): List<Int> {
        val size = DEFAULT_LAYER_SLOT_COUNTS.size
        val result = MutableList(size) { index ->
            (raw.getOrNull(index) ?: DEFAULT_LAYER_SLOT_COUNTS[index]).coerceIn(1, SLOT_COUNT)
        }
        var diff = SLOT_COUNT - result.sum()
        var index = size - 1
        while (diff != 0 && index >= 0) {
            if (diff > 0) {
                val capacity = SLOT_COUNT - result[index]
                if (capacity <= 0) {
                    index--
                    continue
                }
                val add = minOf(diff, capacity)
                result[index] += add
                diff -= add
            } else {
                val capacity = result[index] - 1
                if (capacity <= 0) {
                    index--
                    continue
                }
                val remove = minOf(-diff, capacity)
                result[index] -= remove
                diff += remove
            }
        }
        return result
    }

    fun layerStartIndex(layer: Int, counts: List<Int> = DEFAULT_LAYER_SLOT_COUNTS.toList()): Int {
        if (layer <= 0) return 0
        var start = 0
        for (i in 0 until minOf(layer, counts.size)) start += counts[i]
        return start
    }

    fun slotCountInLayer(layer: Int, counts: List<Int> = DEFAULT_LAYER_SLOT_COUNTS.toList()): Int =
        counts.getOrElse(layer) { 0 }

    fun layerOf(globalIndex: Int, counts: List<Int> = DEFAULT_LAYER_SLOT_COUNTS.toList()): Int {
        if (globalIndex !in 0 until SLOT_COUNT) return -1
        var start = 0
        counts.forEachIndexed { layer, count ->
            if (globalIndex < start + count) return layer
            start += count
        }
        return counts.lastIndex
    }

    fun layerLocalIndex(
        globalIndex: Int,
        counts: List<Int> = DEFAULT_LAYER_SLOT_COUNTS.toList(),
    ): Int {
        val layer = layerOf(globalIndex, counts)
        return if (layer < 0) -1 else globalIndex - layerStartIndex(layer, counts)
    }

    /** [layerCount] 层（含）以内的最后一个槽位下标。 */
    fun lastSlotIndexInLayerCount(
        layerCount: Int,
        counts: List<Int> = DEFAULT_LAYER_SLOT_COUNTS.toList(),
    ): Int {
        val layer = (layerCount - 1).coerceIn(0, counts.size - 1)
        return layerStartIndex(layer, counts) + slotCountInLayer(layer, counts) - 1
    }

    fun defaultLeftSlots(): List<GestureAction> = listOf(
        // layer 1 — 3
        GestureAction.OpenIndex,
        GestureAction.QuickLauncher(),
        GestureAction.Screenshot,
        // layer 2 — 5
        GestureAction.FreeWindowCurrentApp,
        GestureAction.Back,
        GestureAction.Home,
        GestureAction.Recents,
        GestureAction.LaunchAssistant,
        // layer 3 — 7
        GestureAction.OpenQuickSettings,
        GestureAction.TaskSwitcher,
        GestureAction.None,
        GestureAction.None,
        GestureAction.None,
        GestureAction.None,
        GestureAction.None,
    )

    fun defaultRightSlots(): List<GestureAction> = listOf(
        GestureAction.FreeWindowCurrentApp,
        GestureAction.QuickLauncher(),
        GestureAction.OpenIndex,
        GestureAction.Screenshot,
        GestureAction.Back,
        GestureAction.Home,
        GestureAction.Recents,
        GestureAction.LaunchAssistant,
        GestureAction.OpenQuickSettings,
        GestureAction.TaskSwitcher,
        GestureAction.None,
        GestureAction.None,
        GestureAction.None,
        GestureAction.None,
        GestureAction.None,
    )

    fun decode(encoded: Set<String>, defaults: List<GestureAction>): List<GestureAction> {
        if (encoded.isEmpty()) return normalizeSlots(defaults)
        val byIndex = encoded.mapNotNull { entry ->
            val sep = entry.indexOf(SEP)
            if (sep <= 0) return@mapNotNull null
            val index = entry.substring(0, sep).toIntOrNull() ?: return@mapNotNull null
            val payload = entry.substring(sep + 1)
            val action = QuickLauncherItemCodec.parseActionPayload(payload) ?: return@mapNotNull null
            index to sanitizeSlotAction(action)
        }.toMap()
        val base = normalizeSlots(defaults)
        return List(SLOT_COUNT) { index ->
            byIndex[index] ?: base.getOrElse(index) { GestureAction.None }
        }
    }

    fun encode(slots: List<GestureAction>): Set<String> =
        normalizeSlots(slots).mapIndexed { index, action ->
            "$index$SEP${QuickLauncherItemCodec.encodeActionPayload(sanitizeSlotAction(action))}"
        }.toSet()

    fun normalizeSlots(slots: List<GestureAction>): List<GestureAction> =
        List(SLOT_COUNT) { index -> slots.getOrElse(index) { GestureAction.None } }

    fun layerTitleResLayer(layer: Int): Int = layer + 1

    private fun sanitizeSlotAction(action: GestureAction): GestureAction =
        action.sanitizeForSlotPicker(SlotPickerKind.CornerWheel)
}
