package com.slideindex.app.overlay.panels

/**
 * 扩展面板可调的音量流。
 *
 * 以前这里跨模块传的是 `VolumeControlHelper.Stream` 的 ordinal，调用方按面板从左到右
 * 数成 0..3，结果闹钟（0）落到了媒体流、媒体（3）落到了闹钟流。改成具名枚举，
 * 宿主侧显式映射到系统流，声明顺序不再影响行为。
 */
enum class PanelAudioStream {
    MEDIA,
    RING,
    NOTIFICATION,
    ALARM,
}

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

    /** 设某条音量流的比例。 */
    @Volatile
    var setVolume: ((PanelAudioStream, Float) -> Unit)? = null
}
