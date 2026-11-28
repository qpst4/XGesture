package com.slideindex.app.settings

/** 取词面板在屏幕上的落位方式（与面板内容样式 [PickResultPanelStyle] 正交）。 */
enum class PickResultPanelPlacement(val storageKey: String) {
    /** 贴屏幕底部滑入（默认）。 */
    BOTTOM_DOCKED("bottom_docked"),
    /** 屏幕中间的卡片。 */
    CENTER("center"),

    /**
     * 自定义：面板**底边距屏幕底部**的距离可调（默认 0 = 等同贴底）。
     *
     * 间距调大时整块面板上浮；面板自身高度会按「屏幕可用高度 − 间距」自适应，
     * 保证顶边永远不超出屏幕顶部。
     */
    CUSTOM("custom"),
    ;

    companion object {
        fun fromStorageKey(key: String?): PickResultPanelPlacement =
            when (key) {
                "center" -> CENTER
                "custom" -> CUSTOM
                else -> BOTTOM_DOCKED
            }
    }
}
