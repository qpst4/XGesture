package com.slideindex.app.overlay.panels

/**
 * 面板要的"系统动作"面：音量 / 亮度 / 勿扰 / 深色模式 / 连续调节。
 *
 * 这些能力在 `:app` 的 `ActionExecutor` 里（背后是特权、Shizuku、AudioManager 等），
 * 面板模块不能依赖它，所以抽成窄接口由调用方实现。
 */
interface PanelActionHost {

    fun readVolumeFraction(stream: PanelVolumeStream): Float

    fun setVolumeFraction(stream: PanelVolumeStream, fraction: Float)

    /** 当前被"按住调节"的那个流（媒体音量）。 */
    fun adjustFraction(): Float

    fun readCurrentAdjustFraction(mode: PanelAdjustMode): Float

    fun readRingerMode(): Int

    fun cycleRingerMode(): Int?

    fun readInterruptionFilter(): Int

    fun toggleDnd(): Int?

    fun readAutoBrightnessEnabled(): Boolean

    fun toggleAutoBrightness(): Boolean?

    fun readDarkModeEnabled(): Boolean

    fun toggleDarkMode(): Boolean?

    fun clearBrightnessPreview()

    fun beginContinuousAdjust(mode: PanelAdjustMode, rawY: Float): Boolean

    fun updateContinuousAdjust(mode: PanelAdjustMode, rawY: Float)

    fun endContinuousAdjust()

    fun applyAdjustOnce(mode: PanelAdjustMode, anchorRawY: Float)
}

/** 音量流（对齐 `:app` 的 `VolumeControlHelper.Stream`）。 */
enum class PanelVolumeStream {
    MEDIA,
    RING,
    NOTIFICATION,
    ALARM,
}

/** 连续调节的目标（对齐 `:app` 的 `ContinuousAdjustController.Mode`）。 */
enum class PanelAdjustMode {
    VOLUME,
    BRIGHTNESS,
}

/** 系统 UI 深色模式的 Settings key（原 `BrightnessControlHelper.UI_NIGHT_MODE_KEY`）。 */
const val UI_NIGHT_MODE_KEY: String = "ui_night_mode"
