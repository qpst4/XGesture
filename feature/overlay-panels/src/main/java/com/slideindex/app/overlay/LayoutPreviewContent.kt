package com.slideindex.app.overlay

enum class LayoutPreviewContent {
    TRIGGER_ONLY,
    INDEX_ONLY,
}

data class LayoutPreviewFocus(
    val side: PanelSide,
    val handleId: String,
    val showSwipeDistances: Boolean = false,
    /** 设计页预览：左右同组（同 handleId）触钮均显示设计。 */
    val showPairedGroup: Boolean = false
)
