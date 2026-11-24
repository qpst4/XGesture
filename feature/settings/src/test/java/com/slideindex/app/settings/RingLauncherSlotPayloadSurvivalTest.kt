package com.slideindex.app.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 排查「升级后圆环槽位被清空」：确认搬运是否会因为槽位载荷解码失败而写入空集合。
 *
 * 真实设备上槽位里有带自定义图标的条目（载荷里嵌 emoji / 路径文本），
 * 这些必须能原样往返，否则 [FvRingLauncherSettings.writeSlotsAxis] 会把空集合写回磁盘。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class RingLauncherSlotPayloadSurvivalTest {

    private val context get() = RuntimeEnvironment.getApplication()

    private val realisticSlots = mapOf(
        0 to QuickLauncherItem(type = QuickLauncherItemType.APP, payload = "com.fooview.android.fooview", label = "fooView"),
        1 to QuickLauncherItem(type = QuickLauncherItemType.APP, payload = "com.fooview.android.intent", label = "意图"),
        3 to QuickLauncherItem(type = QuickLauncherItemType.APP, payload = "com.tencent.mm", label = "微信"),
        5 to QuickLauncherItem(type = QuickLauncherItemType.APP, payload = "com.tencent.mm.plugin.card.ui.v4", label = "卡包"),
        7 to QuickLauncherItem(type = QuickLauncherItemType.APP, payload = "com.some.app", label = "🎱Billiards"),
    )

    @Test
    fun slotCodec_roundTripsRealisticSlotsIncludingEmojiLabel() {
        val encoded = FvRingLauncherSlotCodec.encodeAll(realisticSlots)
        assertEquals("每个槽位都应编码成功", realisticSlots.size, encoded.size)

        val decoded = FvRingLauncherSlotCodec.decodeAll(encoded)
        assertEquals("解码后槽位数量必须一致", realisticSlots.size, decoded.size)
        for ((index, item) in realisticSlots) {
            assertEquals("槽位 $index 的条目必须原样还原", item, decoded[index])
        }
    }

    @Test
    fun writeSlotsAxis_preservesDecodedSlots() = runBlocking {
        val prefs = mutablePreferencesOf()
        FvRingLauncherSettings.writeSlotsAxis(prefs, FvRingLauncherAxis.VERTICAL, FvRingLauncherSettings(slots = realisticSlots))

        val reread = FvRingLauncherSettings.fromPreferences(prefs, FvRingLauncherAxis.VERTICAL)
        assertEquals("写盘再读回必须不丢槽位", realisticSlots.size, reread.slots.size)
    }

    @Test
    fun selectingNonePersistsWithoutDisablingUntouchedSlots() = runBlocking {
        val editor = SettingsPreferencesEditor(context)
        val mutator = OverlaySettingsMutator(editor)
        editor.edit { prefs ->
            prefs.remove(SettingsPreferenceKeys.FV_RING_LAUNCHER_SLOTS)
            prefs.remove(SettingsPreferenceKeys.FV_RING_LAUNCHER_HORIZONTAL_SLOTS)
            prefs[SettingsPreferenceKeys.FV_RING_LAUNCHER_LINK_SLOT_AXES] = true
        }.getOrThrow()
        val none = QuickLauncherItem(QuickLauncherItemType.APP, "", "")
        mutator.setFvRingLauncherSlot(FvRingLauncherAxis.VERTICAL, 0, none).getOrThrow()

        for (axis in FvRingLauncherAxis.entries) {
            val reloaded = FvRingLauncherSettings.fromPreferences(editor.readRawPreferences(), axis)
            assertEquals(none, reloaded.itemAt(0))
            assertTrue(reloaded.isExplicitlyEmpty(0))
            assertFalse(reloaded.isExplicitlyEmpty(1))
            assertNull(reloaded.itemAt(1))
        }

        mutator.setFvRingLauncherSlot(FvRingLauncherAxis.VERTICAL, 0, realisticSlots.getValue(0)).getOrThrow()
        val replaced = FvRingLauncherSettings.fromPreferences(editor.readRawPreferences(), FvRingLauncherAxis.VERTICAL)
        assertFalse(replaced.isExplicitlyEmpty(0))
        assertEquals(realisticSlots[0], replaced.itemAt(0))
    }

    @Test
    fun panelReferenceMigration_doesNotWipeDecodableSlots() = runBlocking {
        val editor = SettingsPreferencesEditor(context)
        val mutator = OverlaySettingsMutator(editor)

        // 清场：保证两个标记与两侧槽位都从干净状态开始。
        editor.edit { prefs ->
            prefs.remove(SettingsPreferenceKeys.FV_RING_LAUNCHER_SLOTS)
            prefs.remove(SettingsPreferenceKeys.FV_RING_LAUNCHER_PANEL_REFERENCE_MIGRATED)
            prefs[SettingsPreferenceKeys.FV_RING_LAUNCHER_CIRCLE_COUNT] = 2
        }.getOrThrow()
        editor.edit { prefs ->
            FvRingLauncherSettings.writeSlotsAxis(prefs, FvRingLauncherAxis.VERTICAL, FvRingLauncherSettings(slots = realisticSlots))
        }.getOrThrow()

        // 跑那两条一次性迁移（它们都可能写回槽位）。
        mutator.migrateFvRingLauncherQuickLauncherPanelsOnce().getOrThrow()
        mutator.migrateFvRingLauncherPreferenceKeysOnce().getOrThrow()

        val after = FvRingLauncherSettings.fromPreferences(editor.readRawPreferences(), FvRingLauncherAxis.VERTICAL)
        assertTrue(
            "迁移后槽位不能变空（实际 ${after.slots.size} 个）",
            after.slots.size == realisticSlots.size,
        )
    }
}
