package com.slideindex.app.gesture

enum class TriggerDesignKind(val id: Int) {
    HIDE(0),
    CONFIGURABLE_RECTANGLE(1),
    CUSTOM_IMAGE(2),
    ;

    companion object {
        fun fromId(id: Int): TriggerDesignKind =
            entries.firstOrNull { it.id == id } ?: HIDE
    }
}

enum class TriggerCornerMode(val id: Int) {
    ALL(0),
    OUTER(1),
    ;

    companion object {
        fun fromId(id: Int): TriggerCornerMode =
            entries.firstOrNull { it.id == id } ?: ALL
    }
}

enum class TriggerDesignPreset {
    BAR,
    LINE,
    ROUNDED_RECT,
    HALO,
    LINE_AND_HALO,
}

data class TriggerHandleDesign(
    val kind: TriggerDesignKind = TriggerDesignKind.HIDE,
    val sizeDp: Float = 0f,
    val marginDp: Float = 0f,
    val backgroundColor: Int = DEFAULT_BACKGROUND_COLOR,
    val cornerRadiusDp: Float = 0f,
    val cornerMode: TriggerCornerMode = TriggerCornerMode.ALL,
    val borderSizeDp: Float = 0f,
    val borderColor: Int = DEFAULT_BORDER_COLOR,
    val haloSizeDp: Float = 0f,
    val haloColor: Int = DEFAULT_HALO_COLOR,
    val customImageUri: String? = null,
) {
    val isVisible: Boolean
        get() = kind != TriggerDesignKind.HIDE &&
            (kind == TriggerDesignKind.CUSTOM_IMAGE && !customImageUri.isNullOrBlank() ||
                kind == TriggerDesignKind.CONFIGURABLE_RECTANGLE &&
                (sizeDp > 0f || haloSizeDp > 0f || borderSizeDp > 0f))

    companion object {
        const val DEFAULT_BACKGROUND_COLOR = 0xA6FD746C.toInt()
        const val DEFAULT_BORDER_COLOR = 0xA6FD746C.toInt()
        const val DEFAULT_HALO_COLOR = 0xCCFD746C.toInt()
    }
}

data class TriggerRectangleSettingsVisibility(
    val body: Boolean,
    val border: Boolean,
    val halo: Boolean,
) {
    val hasAny: Boolean get() = body || border || halo
}

private val BODY_CAPABLE_PRESETS = setOf(
    TriggerDesignPreset.BAR,
    TriggerDesignPreset.LINE,
    TriggerDesignPreset.ROUNDED_RECT,
    TriggerDesignPreset.LINE_AND_HALO,
)

private val HALO_CAPABLE_PRESETS = setOf(
    TriggerDesignPreset.HALO,
    TriggerDesignPreset.LINE_AND_HALO,
)

fun TriggerHandleDesign.rectangleSettingsVisibility(
    activePreset: TriggerDesignPreset?,
): TriggerRectangleSettingsVisibility {
    if (kind != TriggerDesignKind.CONFIGURABLE_RECTANGLE) {
        return TriggerRectangleSettingsVisibility(body = false, border = false, halo = false)
    }
    val preset = activePreset ?: TriggerDesignPresets.detectPreset(this)
    val bodyCapable = preset in BODY_CAPABLE_PRESETS
    val haloCapable = preset in HALO_CAPABLE_PRESETS
    val showBody = sizeDp > 0f || bodyCapable
    val showBorder = sizeDp > 0f
    val showHalo = haloSizeDp > 0f || sizeDp == 0f || haloCapable
    return TriggerRectangleSettingsVisibility(
        body = showBody,
        border = showBorder,
        halo = showHalo,
    )
}

fun TriggerDesignPresets.detectPreset(design: TriggerHandleDesign): TriggerDesignPreset? {
    if (design.kind != TriggerDesignKind.CONFIGURABLE_RECTANGLE) return null
    return TriggerDesignPreset.entries.firstOrNull { preset ->
        design.matchesPresetStructure(TriggerDesignPresets.apply(preset))
    }
}

private fun TriggerHandleDesign.matchesPresetStructure(base: TriggerHandleDesign): Boolean =
    sizeDp == base.sizeDp &&
        haloSizeDp == base.haloSizeDp &&
        cornerRadiusDp == base.cornerRadiusDp &&
        cornerMode == base.cornerMode

object TriggerDesignPresets {
    fun apply(preset: TriggerDesignPreset): TriggerHandleDesign = when (preset) {
        TriggerDesignPreset.BAR -> TriggerHandleDesign(
            kind = TriggerDesignKind.CONFIGURABLE_RECTANGLE,
            sizeDp = 6f,
            marginDp = 0f,
            cornerRadiusDp = 3f,
            cornerMode = TriggerCornerMode.ALL,
            haloSizeDp = 0f,
        )
        TriggerDesignPreset.LINE -> TriggerHandleDesign(
            kind = TriggerDesignKind.CONFIGURABLE_RECTANGLE,
            sizeDp = 1f,
            marginDp = 0f,
            cornerRadiusDp = 0f,
            cornerMode = TriggerCornerMode.ALL,
            haloSizeDp = 0f,
        )
        TriggerDesignPreset.ROUNDED_RECT -> TriggerHandleDesign(
            kind = TriggerDesignKind.CONFIGURABLE_RECTANGLE,
            sizeDp = 10f,
            marginDp = 0f,
            cornerRadiusDp = 5f,
            cornerMode = TriggerCornerMode.OUTER,
            haloSizeDp = 0f,
        )
        TriggerDesignPreset.HALO -> TriggerHandleDesign(
            kind = TriggerDesignKind.CONFIGURABLE_RECTANGLE,
            sizeDp = 0f,
            marginDp = 0f,
            cornerRadiusDp = 0f,
            cornerMode = TriggerCornerMode.ALL,
            haloSizeDp = 10f,
        )
        TriggerDesignPreset.LINE_AND_HALO -> TriggerHandleDesign(
            kind = TriggerDesignKind.CONFIGURABLE_RECTANGLE,
            sizeDp = 1f,
            marginDp = 0f,
            cornerRadiusDp = 0f,
            cornerMode = TriggerCornerMode.ALL,
            haloSizeDp = 10f,
        )
    }
}

object TriggerHandleDesignCodec {
    private const val FIELD_SEP = "\u001F"

    fun encode(design: TriggerHandleDesign): String = listOf(
        design.kind.id.toString(),
        design.sizeDp.toString(),
        design.marginDp.toString(),
        design.backgroundColor.toString(),
        design.cornerRadiusDp.toString(),
        design.cornerMode.id.toString(),
        design.borderSizeDp.toString(),
        design.borderColor.toString(),
        design.haloSizeDp.toString(),
        design.haloColor.toString(),
        design.customImageUri.orEmpty(),
    ).joinToString(FIELD_SEP)

    fun decode(raw: String?): TriggerHandleDesign {
        if (raw.isNullOrBlank()) return TriggerHandleDesign()
        val parts = raw.split(FIELD_SEP)
        if (parts.size < 10) return TriggerHandleDesign()
        val decodedKind = TriggerDesignKind.fromId(parts[0].toIntOrNull() ?: 0)
        return TriggerHandleDesign(
            // 历史数据可能已经存成 CUSTOM_IMAGE（当时下拉框可选，但没有选图/渲染实现）。
            // 读回时归一成 HIDE，避免用户升级后条继续不可见且下拉框里找不到这个值。
            kind = if (decodedKind == TriggerDesignKind.CUSTOM_IMAGE) {
                TriggerDesignKind.HIDE
            } else {
                decodedKind
            },
            sizeDp = parts[1].toFloatOrNull() ?: 0f,
            marginDp = parts[2].toFloatOrNull() ?: 0f,
            backgroundColor = parts[3].toIntOrNull() ?: TriggerHandleDesign.DEFAULT_BACKGROUND_COLOR,
            cornerRadiusDp = parts[4].toFloatOrNull() ?: 0f,
            cornerMode = TriggerCornerMode.fromId(parts[5].toIntOrNull() ?: 0),
            borderSizeDp = parts[6].toFloatOrNull() ?: 0f,
            borderColor = parts[7].toIntOrNull() ?: TriggerHandleDesign.DEFAULT_BORDER_COLOR,
            haloSizeDp = parts[8].toFloatOrNull() ?: 0f,
            haloColor = parts[9].toIntOrNull() ?: TriggerHandleDesign.DEFAULT_HALO_COLOR,
            customImageUri = parts.getOrNull(10)?.takeIf { it.isNotBlank() },
        )
    }
}

fun TriggerHandleDesign.coerceInLimits(): TriggerHandleDesign = copy(
    sizeDp = sizeDp.coerceIn(0f, 48f),
    marginDp = 0f,
    cornerRadiusDp = cornerRadiusDp.coerceIn(0f, 32f),
    borderSizeDp = borderSizeDp.coerceIn(0f, 8f),
    haloSizeDp = haloSizeDp.coerceIn(0f, 48f),
)
