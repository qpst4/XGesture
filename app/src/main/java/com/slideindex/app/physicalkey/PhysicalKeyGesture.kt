package com.slideindex.app.physicalkey

/**
 * 驱动级硬件实体按键手势枚举。
 */
enum class PhysicalKeyGesture(
    val title: String,
    val description: String,
) {
    POWER_LONG_PRESS(
        title = "电源键长按",
        description = "按住电源键达到设定时长（默认 500ms）触发",
    ),
    POWER_DOUBLE_PRESS(
        title = "电源键双击",
        description = "快速连续两次按下电源键触发",
    ),
    VOLUME_UP_LONG_PRESS(
        title = "音量加长按",
        description = "按住音量增加键达到设定时长触发",
    ),
    VOLUME_UP_DOUBLE_PRESS(
        title = "音量加双击",
        description = "快速连续两次按下音量增加键触发",
    ),
    VOLUME_DOWN_LONG_PRESS(
        title = "音量减长按",
        description = "按住音量减少键达到设定时长触发",
    ),
    VOLUME_DOWN_DOUBLE_PRESS(
        title = "音量减双击",
        description = "快速连续两次按下音量减少键触发",
    ),
    VOLUME_BOTH_PRESS(
        title = "音量双键并击",
        description = "同时按下音量加和音量减键触发",
    );

    companion object {
        private val VALUES = entries.toTypedArray()

        fun fromOrdinal(ordinal: Int): PhysicalKeyGesture? =
            VALUES.getOrNull(ordinal)
    }
}
