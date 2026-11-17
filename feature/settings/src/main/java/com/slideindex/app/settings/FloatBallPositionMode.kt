package com.slideindex.app.settings

enum class FloatBallPositionMode(val storageKey: String) {
    LEFT("left"),
    RIGHT("right"),
    /** 一边球、对边一条线。 */
    BOTH_EDGES("both_edges"),
    /** 两侧都显示同规格的线：球只在拖动时从按下的那条线浮现。 */
    BOTH_LINES("both_lines"),
    CUSTOM("custom"),
    ;

    companion object {
        val selectable: List<FloatBallPositionMode> = listOf(LEFT, RIGHT, BOTH_EDGES, BOTH_LINES)

        fun fromStorageKey(key: String?): FloatBallPositionMode =
            when (val mode = entries.firstOrNull { it.storageKey == key }) {
                null, CUSTOM -> RIGHT
                else -> mode
            }
    }
}

enum class FloatBallSide(val storageKey: String) {
    LEFT("left"),
    RIGHT("right"),
    ;

    companion object {
        fun fromStorageKey(key: String?): FloatBallSide =
            entries.firstOrNull { it.storageKey == key } ?: RIGHT

        fun opposite(side: FloatBallSide): FloatBallSide = when (side) {
            LEFT -> RIGHT
            RIGHT -> LEFT
        }
    }
}
