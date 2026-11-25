package com.slideindex.app.settings

/**
 * 局部卡片类浮层的模糊模式。
 *
 * - [DISABLED] (0)：关闭（纯实色卡片，且背景桌面不虚化）；
 * - [BACKDROP_ONLY] (1)：仅背景（卡片保持纯实色底板，整屏背景高斯虚化）；
 * - [PANEL_ONLY] (2)：仅面板（卡片局部透光毛玻璃，整屏背景不虚化）；
 * - [ALL] (3)：全部开启（卡片局部毛玻璃 + 整屏背景高斯虚化，质感最通透）。
 */
enum class OverlayBlurMode(val id: Int) {
    DISABLED(0),
    BACKDROP_ONLY(1),
    PANEL_ONLY(2),
    ALL(3);

    val isPanelBlurEnabled: Boolean
        get() = this == PANEL_ONLY || this == ALL

    val isBackdropBlurEnabled: Boolean
        get() = this == BACKDROP_ONLY || this == ALL

    companion object {
        fun fromId(id: Int): OverlayBlurMode = entries.firstOrNull { it.id == id } ?: BACKDROP_ONLY
    }
}
