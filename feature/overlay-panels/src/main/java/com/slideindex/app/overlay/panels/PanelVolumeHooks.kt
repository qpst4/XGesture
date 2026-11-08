package com.slideindex.app.overlay.panels

/**
 * 音量面板需要的系统读写（`:app` 的 `VolumeControlHelper` / `BrightnessControlHelper`）。
 *
 * 面板不再直接依赖 `:app` 的静态 helper。
 */
object PanelVolumeHooks {

    /** 读当前亮度（0..1）。 */
    @Volatile
    var readBrightness: (() -> Float)? = null

    /** 应用亮度（0..1）。 */
    @Volatile
    var applyBrightness: ((Float) -> Unit)? = null

    /** 设某条音量流的比例：`stream` = `VolumeControlHelper.Stream` 序号。 */
    @Volatile
    var setVolume: ((Int, Float) -> Unit)? = null
}
