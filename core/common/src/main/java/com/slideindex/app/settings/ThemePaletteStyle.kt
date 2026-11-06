package com.slideindex.app.settings

/** Material 3 动态/种子色配色风格（对应 Monet scheme 变体）。 */
enum class ThemePaletteStyle(val id: Int) {
    TONAL_SPOT(0),
    VIBRANT(1),
    EXPRESSIVE(2),
    MONOCHROME(3),
    NEUTRAL(4),
    FIDELITY(5),
    CONTENT(6),
    RAINBOW(7),
    FRUIT_SALAD(8),
    ;

    companion object {
        fun fromId(id: Int): ThemePaletteStyle =
            entries.firstOrNull { it.id == id } ?: TONAL_SPOT
    }
}
