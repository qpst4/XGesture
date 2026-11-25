package com.slideindex.app.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.overlay.layout.FvRingLauncherSide
import com.slideindex.app.overlay.layout.FvCircleLayoutEngine
import com.slideindex.app.overlay.layout.FvIconShape
import com.slideindex.app.overlay.layout.PanelIconShape

enum class FvRingLauncherAxis {
    VERTICAL,
    HORIZONTAL,
}

enum class FvRingLauncherAxisMergeDirection {
    /** 采用另一轴已有配置，同步到两轴。 */
    USE_OTHER_AXIS,
    /** 用当前触发轴的配置覆盖另一轴，并同步到两轴。 */
    USE_CURRENT_AXIS,
}

data class FvRingLauncherLinkFlags(
    val linkAppearanceAxes: Boolean = DEFAULT_LINK_APPEARANCE_AXES,
    val linkSlotAxes: Boolean = DEFAULT_LINK_SLOT_AXES,
) {
    companion object {
        const val DEFAULT_LINK_APPEARANCE_AXES = false
        const val DEFAULT_LINK_SLOT_AXES = true
    }
}

fun FvRingLauncherAxis.other(): FvRingLauncherAxis = when (this) {
    FvRingLauncherAxis.VERTICAL -> FvRingLauncherAxis.HORIZONTAL
    FvRingLauncherAxis.HORIZONTAL -> FvRingLauncherAxis.VERTICAL
}

fun FvRingLauncherSide.toAxis(): FvRingLauncherAxis = when (this) {
    FvRingLauncherSide.TOP, FvRingLauncherSide.BOTTOM -> FvRingLauncherAxis.VERTICAL
    FvRingLauncherSide.LEFT, FvRingLauncherSide.RIGHT -> FvRingLauncherAxis.HORIZONTAL
}

fun LauncherSettings.fvRingLauncherFor(side: FvRingLauncherSide): FvRingLauncherSettings =
    fvRingLauncherFor(side.toAxis())

fun LauncherSettings.fvRingLauncherFor(axis: FvRingLauncherAxis): FvRingLauncherSettings {
    val vertical = fvRingLauncherVertical
    val horizontal = fvRingLauncherHorizontal
    val own = when (axis) {
        FvRingLauncherAxis.VERTICAL -> vertical
        FvRingLauncherAxis.HORIZONTAL -> horizontal
    }
    if (!fvRingLauncherLinkAppearanceAxes && !fvRingLauncherLinkSlotAxes) {
        return own
    }
    val appearanceSource = if (fvRingLauncherLinkAppearanceAxes) vertical else own
    val slotSource = if (fvRingLauncherLinkSlotAxes) vertical else own
    return own.withAppearanceFrom(appearanceSource).withSlotsFrom(slotSource)
}

fun AppSettings.fvRingLauncherFor(side: FvRingLauncherSide): FvRingLauncherSettings =
    launcher.fvRingLauncherFor(side)

fun AppSettings.fvRingLauncherFor(axis: FvRingLauncherAxis): FvRingLauncherSettings =
    launcher.fvRingLauncherFor(axis)

/** FV CircleAppContainer 风格圆环启动器设置（圈数、外观尺寸、半径与槽位）。 */
data class FvRingLauncherSettings(
    val circleCount: Int = DEFAULT_CIRCLE_COUNT,
    val iconSizeDp: Float = DEFAULT_ICON_SIZE_DP,
    val iconShape: PanelIconShape = PanelIconShape.FOLLOW_GLOBAL,
    val baseRadiusDp: Float = DEFAULT_BASE_RADIUS_DP,
    val layerGapDp: Float = DEFAULT_LAYER_GAP_DP,
    val endMarginDeg: Float = DEFAULT_END_MARGIN_DEG,
    val showToolbar: Boolean = true,
    val slots: Map<Int, QuickLauncherItem> = emptyMap(),
    val slotIconOverrides: Map<Int, FvRingLauncherSlotIconOverride> = emptyMap(),
) {
    fun slotCount(): Int = slotCountForCircleCount(circleCount)

    fun itemAt(index: Int): QuickLauncherItem? = slots[index]

    fun iconOverrideAt(index: Int): FvRingLauncherSlotIconOverride? = slotIconOverrides[index]

    fun configuredCount(): Int = slots.values.count { it.payload.isNotBlank() }

    fun withAppearanceFrom(source: FvRingLauncherSettings): FvRingLauncherSettings = copy(
        circleCount = source.circleCount,
        iconSizeDp = source.iconSizeDp,
        iconShape = source.iconShape,
        baseRadiusDp = source.baseRadiusDp,
        layerGapDp = source.layerGapDp,
        endMarginDeg = source.endMarginDeg,
        showToolbar = source.showToolbar,
    )

    fun withSlotsFrom(source: FvRingLauncherSettings): FvRingLauncherSettings =
        copy(slots = source.slots, slotIconOverrides = source.slotIconOverrides)

    companion object {
        const val MIN_CIRCLE_COUNT = 1
        const val MAX_CIRCLE_COUNT = 4
        const val DEFAULT_CIRCLE_COUNT = 2
        const val MAX_SLOTS = 38

        const val MIN_ICON_SIZE_DP = 24f
        const val MAX_ICON_SIZE_DP = 56f
        const val DEFAULT_ICON_SIZE_DP = FvCircleLayoutEngine.ICON_SIZE_DP

        const val MIN_BASE_RADIUS_DP = 60f
        const val MAX_BASE_RADIUS_DP = 130f
        const val DEFAULT_BASE_RADIUS_DP = FvCircleLayoutEngine.DEFAULT_BASE_RADIUS_DP

        const val MIN_LAYER_GAP_DP = 35f
        const val MAX_LAYER_GAP_DP = 75f
        const val DEFAULT_LAYER_GAP_DP = FvCircleLayoutEngine.DEFAULT_LAYER_GAP_DP

        const val MIN_END_MARGIN_DEG = 0f
        const val MAX_END_MARGIN_DEG = 30f
        const val DEFAULT_END_MARGIN_DEG = FvCircleLayoutEngine.DEFAULT_END_MARGIN_DEG

        fun slotCountForCircleCount(circleCount: Int): Int = when (circleCount.coerceIn(MIN_CIRCLE_COUNT, MAX_CIRCLE_COUNT)) {
            1 -> 5
            2 -> 13
            3 -> 24
            else -> MAX_SLOTS
        }

        /** 旧版单套配置读取；等价于垂直轴。 */
        fun fromPreferences(prefs: Preferences): FvRingLauncherSettings =
            fromPreferences(prefs, FvRingLauncherAxis.VERTICAL)

        fun fromPreferences(prefs: Preferences, axis: FvRingLauncherAxis): FvRingLauncherSettings {
            val vertical = readAxis(prefs, FvRingLauncherAxis.VERTICAL)
            if (axis == FvRingLauncherAxis.VERTICAL) {
                return vertical
            }
            if (!hasHorizontalOverrides(prefs)) {
                return vertical
            }
            return readAxis(prefs, FvRingLauncherAxis.HORIZONTAL)
        }

        fun linkFlagsFromPreferences(prefs: Preferences): FvRingLauncherLinkFlags {
            val appearance = prefs[SettingsPreferenceKeys.FV_RING_LAUNCHER_LINK_APPEARANCE_AXES]
            val slots = prefs[SettingsPreferenceKeys.FV_RING_LAUNCHER_LINK_SLOT_AXES]
            if (appearance != null || slots != null) {
                return FvRingLauncherLinkFlags(
                    linkAppearanceAxes = appearance ?: FvRingLauncherLinkFlags.DEFAULT_LINK_APPEARANCE_AXES,
                    linkSlotAxes = slots ?: FvRingLauncherLinkFlags.DEFAULT_LINK_SLOT_AXES,
                )
            }
            val legacyLinked = prefs[SettingsPreferenceKeys.FV_RING_LAUNCHER_LINK_AXES] ?: true
            return if (legacyLinked) {
                FvRingLauncherLinkFlags(linkAppearanceAxes = true, linkSlotAxes = true)
            } else {
                FvRingLauncherLinkFlags(
                    linkAppearanceAxes = false,
                    linkSlotAxes = true,
                )
            }
        }

        internal fun writeAppearanceAxis(
            prefs: MutablePreferences,
            axis: FvRingLauncherAxis,
            appearance: FvRingLauncherSettings,
        ) {
            val current = readAxis(prefs, axis)
            writeAxis(prefs, axis, current.withAppearanceFrom(appearance))
        }

        internal fun writeSlotsAxis(
            prefs: MutablePreferences,
            axis: FvRingLauncherAxis,
            slotsSource: FvRingLauncherSettings,
        ) {
            val current = readAxis(prefs, axis)
            writeAxis(prefs, axis, current.withSlotsFrom(slotsSource))
        }

        internal fun writeAxis(
            prefs: MutablePreferences,
            axis: FvRingLauncherAxis,
            settings: FvRingLauncherSettings,
        ) {
            val keys = keysFor(axis)
            prefs[keys.circleCount] =
                settings.circleCount.coerceIn(MIN_CIRCLE_COUNT, MAX_CIRCLE_COUNT)
            prefs[keys.iconSizeDp] =
                settings.iconSizeDp.coerceIn(MIN_ICON_SIZE_DP, MAX_ICON_SIZE_DP)
            prefs[keys.iconShape] = settings.iconShape.name
            prefs[keys.baseRadiusDp] =
                settings.baseRadiusDp.coerceIn(MIN_BASE_RADIUS_DP, MAX_BASE_RADIUS_DP)
            prefs[keys.layerGapDp] =
                settings.layerGapDp.coerceIn(MIN_LAYER_GAP_DP, MAX_LAYER_GAP_DP)
            prefs[keys.endMarginDeg] =
                settings.endMarginDeg.coerceIn(MIN_END_MARGIN_DEG, MAX_END_MARGIN_DEG)
            prefs[keys.showToolbar] = settings.showToolbar
            prefs[keys.slots] = FvRingLauncherSlotCodec.encodeAll(settings.slots)
            prefs[keys.slotIconOverrides] = FvRingLauncherSlotIconOverrideCodec.encodeAll(settings.slotIconOverrides)
        }

        private fun readAxis(prefs: Preferences, axis: FvRingLauncherAxis): FvRingLauncherSettings {
            val keys = keysFor(axis)
            val circleCount = prefs[keys.circleCount] ?: DEFAULT_CIRCLE_COUNT
            val iconSizeDp = prefs[keys.iconSizeDp] ?: DEFAULT_ICON_SIZE_DP
            val iconShape = PanelIconShape.fromName(prefs[keys.iconShape])
            val baseRadiusDp = prefs[keys.baseRadiusDp] ?: DEFAULT_BASE_RADIUS_DP
            val layerGapDp = prefs[keys.layerGapDp] ?: DEFAULT_LAYER_GAP_DP
            val endMarginDeg = prefs[keys.endMarginDeg] ?: DEFAULT_END_MARGIN_DEG
            val showToolbar = prefs[keys.showToolbar] ?: true
            val slots = FvRingLauncherSlotCodec.decodeAll(prefs[keys.slots] ?: emptySet())
            val slotIconOverrides =
                FvRingLauncherSlotIconOverrideCodec.decodeAll(prefs[keys.slotIconOverrides] ?: emptySet())
            return FvRingLauncherSettings(
                circleCount = circleCount.coerceIn(MIN_CIRCLE_COUNT, MAX_CIRCLE_COUNT),
                iconSizeDp = iconSizeDp.coerceIn(MIN_ICON_SIZE_DP, MAX_ICON_SIZE_DP),
                iconShape = iconShape,
                baseRadiusDp = baseRadiusDp.coerceIn(MIN_BASE_RADIUS_DP, MAX_BASE_RADIUS_DP),
                layerGapDp = layerGapDp.coerceIn(MIN_LAYER_GAP_DP, MAX_LAYER_GAP_DP),
                endMarginDeg = endMarginDeg.coerceIn(MIN_END_MARGIN_DEG, MAX_END_MARGIN_DEG),
                showToolbar = showToolbar,
                slots = slots,
                slotIconOverrides = slotIconOverrides,
            )
        }

        private fun hasHorizontalOverrides(prefs: Preferences): Boolean {
            if (prefs[SettingsPreferenceKeys.FV_RING_LAUNCHER_HORIZONTAL_CIRCLE_COUNT] != null) return true
            if (prefs[SettingsPreferenceKeys.FV_RING_LAUNCHER_HORIZONTAL_ICON_SIZE_DP] != null) return true
            if (prefs[SettingsPreferenceKeys.FV_RING_LAUNCHER_HORIZONTAL_ICON_SHAPE] != null) return true
            if (prefs[SettingsPreferenceKeys.FV_RING_LAUNCHER_HORIZONTAL_BASE_RADIUS_DP] != null) return true
            if (prefs[SettingsPreferenceKeys.FV_RING_LAUNCHER_HORIZONTAL_LAYER_GAP_DP] != null) return true
            if (prefs[SettingsPreferenceKeys.FV_RING_LAUNCHER_HORIZONTAL_END_MARGIN_DEG] != null) return true
            if (prefs[SettingsPreferenceKeys.FV_RING_LAUNCHER_HORIZONTAL_SHOW_TOOLBAR] != null) return true
            return prefs[SettingsPreferenceKeys.FV_RING_LAUNCHER_HORIZONTAL_SLOTS]?.isNotEmpty() == true ||
                prefs[SettingsPreferenceKeys.FV_RING_LAUNCHER_HORIZONTAL_SLOT_ICON_OVERRIDES]?.isNotEmpty() == true
        }

        private data class AxisKeys(
            val circleCount: Preferences.Key<Int>,
            val iconSizeDp: Preferences.Key<Float>,
            val iconShape: Preferences.Key<String>,
            val baseRadiusDp: Preferences.Key<Float>,
            val layerGapDp: Preferences.Key<Float>,
            val endMarginDeg: Preferences.Key<Float>,
            val showToolbar: Preferences.Key<Boolean>,
            val slots: Preferences.Key<Set<String>>,
            val slotIconOverrides: Preferences.Key<Set<String>>,
        )

        private fun keysFor(axis: FvRingLauncherAxis): AxisKeys = when (axis) {
            FvRingLauncherAxis.VERTICAL -> AxisKeys(
                circleCount = SettingsPreferenceKeys.FV_RING_LAUNCHER_CIRCLE_COUNT,
                iconSizeDp = SettingsPreferenceKeys.FV_RING_LAUNCHER_ICON_SIZE_DP,
                iconShape = SettingsPreferenceKeys.FV_RING_LAUNCHER_ICON_SHAPE,
                baseRadiusDp = SettingsPreferenceKeys.FV_RING_LAUNCHER_BASE_RADIUS_DP,
                layerGapDp = SettingsPreferenceKeys.FV_RING_LAUNCHER_LAYER_GAP_DP,
                endMarginDeg = SettingsPreferenceKeys.FV_RING_LAUNCHER_END_MARGIN_DEG,
                showToolbar = SettingsPreferenceKeys.FV_RING_LAUNCHER_SHOW_TOOLBAR,
                slots = SettingsPreferenceKeys.FV_RING_LAUNCHER_SLOTS,
                slotIconOverrides = SettingsPreferenceKeys.FV_RING_LAUNCHER_SLOT_ICON_OVERRIDES,
            )
            FvRingLauncherAxis.HORIZONTAL -> AxisKeys(
                circleCount = SettingsPreferenceKeys.FV_RING_LAUNCHER_HORIZONTAL_CIRCLE_COUNT,
                iconSizeDp = SettingsPreferenceKeys.FV_RING_LAUNCHER_HORIZONTAL_ICON_SIZE_DP,
                iconShape = SettingsPreferenceKeys.FV_RING_LAUNCHER_HORIZONTAL_ICON_SHAPE,
                baseRadiusDp = SettingsPreferenceKeys.FV_RING_LAUNCHER_HORIZONTAL_BASE_RADIUS_DP,
                layerGapDp = SettingsPreferenceKeys.FV_RING_LAUNCHER_HORIZONTAL_LAYER_GAP_DP,
                endMarginDeg = SettingsPreferenceKeys.FV_RING_LAUNCHER_HORIZONTAL_END_MARGIN_DEG,
                showToolbar = SettingsPreferenceKeys.FV_RING_LAUNCHER_HORIZONTAL_SHOW_TOOLBAR,
                slots = SettingsPreferenceKeys.FV_RING_LAUNCHER_HORIZONTAL_SLOTS,
                slotIconOverrides = SettingsPreferenceKeys.FV_RING_LAUNCHER_HORIZONTAL_SLOT_ICON_OVERRIDES,
            )
        }
    }
}

fun MutableMap<Int, QuickLauncherItem>.moveFvRingLauncherSlot(
    fromIndex: Int,
    toIndex: Int,
): Boolean {
    if (fromIndex == toIndex) return false
    val fromItem = this[fromIndex] ?: return false
    val toItem = this[toIndex]
    if (toItem != null) {
        this[fromIndex] = toItem
        this[toIndex] = fromItem
    } else {
        remove(fromIndex)
        this[toIndex] = fromItem
    }
    return true
}

fun MutableMap<Int, FvRingLauncherSlotIconOverride>.moveFvRingLauncherSlotIconOverride(
    fromIndex: Int,
    toIndex: Int,
): Boolean {
    if (fromIndex == toIndex) return false
    val fromOverride = this[fromIndex] ?: return false
    val toOverride = this[toIndex]
    if (toOverride != null) {
        this[fromIndex] = toOverride
        this[toIndex] = fromOverride
    } else {
        remove(fromIndex)
        this[toIndex] = fromOverride
    }
    return true
}

object FvRingLauncherSlotCodec {
    private const val INDEX_SEP = "\u001D"

    fun encode(index: Int, item: QuickLauncherItem): String =
        "$index$INDEX_SEP${QuickLauncherItemCodec.encode(item)}"

    fun decode(raw: String): Pair<Int, QuickLauncherItem>? {
        val sep = raw.indexOf(INDEX_SEP)
        if (sep <= 0) return null
        val index = raw.substring(0, sep).toIntOrNull() ?: return null
        val item = QuickLauncherItemCodec.decode(raw.substring(sep + 1)) ?: return null
        return index to item
    }

    fun encodeAll(slots: Map<Int, QuickLauncherItem>): Set<String> =
        slots.filterValues { it.payload.isNotBlank() }
            .map { (index, item) -> encode(index, item) }
            .toSet()

    fun decodeAll(raw: Set<String>): Map<Int, QuickLauncherItem> =
        raw.mapNotNull { decode(it) }
            .filter { (_, item) -> item.payload.isNotBlank() }
            .toMap()
}
