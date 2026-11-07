package com.slideindex.app.overlay

import com.slideindex.app.overlay.panels.PanelAdjustMode
import com.slideindex.app.util.ContinuousAdjustController

/** `:app` 的连续调节模式 → 面板模块的枚举。 */
internal fun ContinuousAdjustController.Mode.toPanelMode(): PanelAdjustMode = when (this) {
    ContinuousAdjustController.Mode.VOLUME -> PanelAdjustMode.VOLUME
    ContinuousAdjustController.Mode.BRIGHTNESS -> PanelAdjustMode.BRIGHTNESS
}

/** 反向映射。 */
internal fun PanelAdjustMode.toAppMode(): ContinuousAdjustController.Mode = when (this) {
    PanelAdjustMode.VOLUME -> ContinuousAdjustController.Mode.VOLUME
    PanelAdjustMode.BRIGHTNESS -> ContinuousAdjustController.Mode.BRIGHTNESS
}
