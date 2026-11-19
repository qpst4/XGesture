package com.slideindex.app.physicalkey

/**
 * 驱动级硬件实体按键手势枚举。
 */
enum class PhysicalKeyGesture {
    POWER_LONG_PRESS,
    POWER_DOUBLE_PRESS,
    VOLUME_UP_LONG_PRESS,
    VOLUME_UP_DOUBLE_PRESS,
    VOLUME_DOWN_LONG_PRESS,
    VOLUME_DOWN_DOUBLE_PRESS,
    VOLUME_BOTH_PRESS,
    POWER_SINGLE_PRESS,
    VOLUME_UP_SINGLE_PRESS,
    VOLUME_DOWN_SINGLE_PRESS,
    ;

    companion object {
        private val VALUES = entries.toTypedArray()

        fun fromOrdinal(ordinal: Int): PhysicalKeyGesture? =
            VALUES.getOrNull(ordinal)
    }
}
