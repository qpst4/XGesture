package com.slideindex.app.settings

import com.slideindex.app.gesture.GestureAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CornerRadialMenuCodecTest {

    @Test
    fun layerMapping_matchesFiveLayerLayout() {
        assertEquals(35, CornerRadialMenuCodec.SLOT_COUNT)
        assertEquals(listOf(3, 5, 7, 9, 11), CornerRadialMenuCodec.LAYER_SLOT_COUNTS.toList())
        val starts = listOf(0, 3, 8, 15, 24)
        starts.forEachIndexed { layer, start ->
            assertEquals(start, CornerRadialMenuCodec.layerStartIndex(layer))
            assertEquals(CornerRadialMenuCodec.LAYER_SLOT_COUNTS[layer], CornerRadialMenuCodec.slotCountInLayer(layer))
            assertEquals(layer, CornerRadialMenuCodec.layerOf(start))
            assertEquals(0, CornerRadialMenuCodec.layerLocalIndex(start))
        }
    }

    @Test
    fun layerMapping_coversEverySlotAndRejectsOutOfRange() {
        val expectedLayers = buildList {
            CornerRadialMenuCodec.LAYER_SLOT_COUNTS.forEachIndexed { layer, count ->
                repeat(count) { add(layer) }
            }
        }
        assertEquals(CornerRadialMenuCodec.SLOT_COUNT, expectedLayers.size)
        expectedLayers.forEachIndexed { index, layer ->
            assertEquals(layer, CornerRadialMenuCodec.layerOf(index))
            assertEquals(index - CornerRadialMenuCodec.layerStartIndex(layer), CornerRadialMenuCodec.layerLocalIndex(index))
        }
        assertEquals(-1, CornerRadialMenuCodec.layerOf(-1))
        assertEquals(-1, CornerRadialMenuCodec.layerOf(CornerRadialMenuCodec.SLOT_COUNT))
        assertEquals(-1, CornerRadialMenuCodec.layerLocalIndex(CornerRadialMenuCodec.SLOT_COUNT))
    }

    @Test
    fun lastSlotIndexInLayerCount_matchesEachLayerEnd() {
        assertEquals(2, CornerRadialMenuCodec.lastSlotIndexInLayerCount(1))
        assertEquals(7, CornerRadialMenuCodec.lastSlotIndexInLayerCount(2))
        assertEquals(14, CornerRadialMenuCodec.lastSlotIndexInLayerCount(3))
        assertEquals(23, CornerRadialMenuCodec.lastSlotIndexInLayerCount(4))
        assertEquals(34, CornerRadialMenuCodec.lastSlotIndexInLayerCount(5))
        assertEquals(34, CornerRadialMenuCodec.lastSlotIndexInLayerCount(9))
        assertEquals(2, CornerRadialMenuCodec.lastSlotIndexInLayerCount(0))
    }

    /** 老存档只有 15 条（索引 0–14），解码后必须原样落位、新层为空。 */
    @Test
    fun decode_keepsLegacyFifteenSlotBindings() {
        val defaults = CornerRadialMenuCodec.defaultLeftSlots()
        val legacyArchive = CornerRadialMenuCodec.encode(defaults)
            .filter { it.substringBefore('\u001D').toInt() < 15 }
            .toSet()
        assertEquals(15, legacyArchive.size)

        val decoded = CornerRadialMenuCodec.decode(legacyArchive, defaults)
        assertEquals(CornerRadialMenuCodec.SLOT_COUNT, decoded.size)
        assertEquals(
            legacyArchive,
            CornerRadialMenuCodec.encode(decoded)
                .filter { it.substringBefore('\u001D').toInt() < 15 }
                .toSet(),
        )
        assertTrue(decoded.drop(15).all { it is GestureAction.None })
    }

    @Test
    fun decode_emptyFallsBackToDefaultsAndPadsToFullRange() {
        val defaults = CornerRadialMenuCodec.defaultRightSlots()
        val decoded = CornerRadialMenuCodec.decode(emptySet(), defaults)
        assertEquals(CornerRadialMenuCodec.SLOT_COUNT, decoded.size)
        defaults.forEachIndexed { index, action -> assertEquals(action, decoded[index]) }
        assertTrue(decoded.drop(defaults.size).all { it is GestureAction.None })
    }

    @Test
    fun encodeRoundTrip_keepsExtraLayerBindings() {
        val defaults = CornerRadialMenuCodec.defaultLeftSlots()
        val slots = CornerRadialMenuCodec.normalizeSlots(defaults).toMutableList()
        slots[15] = GestureAction.Screenshot
        slots[24] = GestureAction.Back
        slots[34] = GestureAction.Home

        val decoded = CornerRadialMenuCodec.decode(CornerRadialMenuCodec.encode(slots), defaults)
        assertEquals(GestureAction.Screenshot, decoded[15])
        assertEquals(GestureAction.Back, decoded[24])
        assertEquals(GestureAction.Home, decoded[34])
    }

    @Test
    fun normalizeSlots_padsShortListToFiveLayers() {
        val normalized = CornerRadialMenuCodec.normalizeSlots(listOf(GestureAction.Back))
        assertEquals(CornerRadialMenuCodec.SLOT_COUNT, normalized.size)
        assertEquals(GestureAction.Back, normalized[0])
        assertTrue(normalized.drop(1).all { it is GestureAction.None })
    }

    @Test
    fun customCounts_mapSlotsToLayers() {
        val counts = CornerRadialMenuCodec.normalizeLayerSlotCounts(listOf(5, 6, 7, 8, 9))
        assertEquals(35, counts.sum())
        assertEquals(0, CornerRadialMenuCodec.layerOf(0, counts))
        assertEquals(0, CornerRadialMenuCodec.layerOf(4, counts))
        assertEquals(0, CornerRadialMenuCodec.layerOf(5, counts) - 1)
        assertEquals(1, CornerRadialMenuCodec.layerOf(5, counts))
        assertEquals(4, CornerRadialMenuCodec.lastSlotIndexInLayerCount(1, counts))
        assertEquals(34, CornerRadialMenuCodec.lastSlotIndexInLayerCount(5, counts))
    }

    @Test
    fun customCounts_areNormalizedTo35Slots() {
        assertEquals(listOf(3, 5, 7, 9, 11), CornerRadialMenuCodec.normalizeLayerSlotCounts(emptyList()))
        assertEquals(35, CornerRadialMenuCodec.normalizeLayerSlotCounts(listOf(99, 99, 99, 99, 99)).sum())
        assertEquals(35, CornerRadialMenuCodec.normalizeLayerSlotCounts(listOf(0, 0, 0, 0, 0)).sum())
    }
}
