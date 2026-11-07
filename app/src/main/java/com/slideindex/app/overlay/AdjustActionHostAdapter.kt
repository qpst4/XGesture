package com.slideindex.app.overlay

import android.content.Context
import com.slideindex.app.gesture.ActionExecutor
import com.slideindex.app.overlay.panels.PanelActionHost
import com.slideindex.app.overlay.panels.PanelAdjustMode
import com.slideindex.app.overlay.panels.PanelVolumeStream
import com.slideindex.app.util.BrightnessControlHelper
import com.slideindex.app.util.VolumeControlHelper

/**
 * 把 `:app` 的 [ActionExecutor] 适配成面板模块的 [PanelActionHost]。
 *
 * 只做枚举映射与转发，不含逻辑——音量/亮度/勿扰的真实实现仍在 `:app`。
 */
internal class AdjustActionHostAdapter(
    private val executor: ActionExecutor,
) : PanelActionHost {

    override fun readVolumeFraction(stream: PanelVolumeStream): Float =
        executor.readVolumeFraction(stream.toAppStream())

    override fun setVolumeFraction(stream: PanelVolumeStream, fraction: Float) {
        executor.setVolumeFraction(stream.toAppStream(), fraction)
    }

    override fun adjustFraction(): Float = executor.adjustFraction()

    override fun readCurrentAdjustFraction(mode: PanelAdjustMode): Float =
        executor.readCurrentAdjustFraction(mode.toAppMode())

    override fun readRingerMode(): Int = executor.readRingerMode()

    override fun cycleRingerMode(): Int? = executor.cycleRingerMode()

    override fun readInterruptionFilter(): Int = executor.readInterruptionFilter()

    override fun toggleDnd(): Int? = executor.toggleDnd()

    override fun readAutoBrightnessEnabled(): Boolean = executor.readAutoBrightnessEnabled()

    override fun toggleAutoBrightness(): Boolean? = executor.toggleAutoBrightness()

    override fun readDarkModeEnabled(): Boolean = executor.readDarkModeEnabled()

    override fun toggleDarkMode(): Boolean? = executor.toggleDarkMode()

    override fun clearBrightnessPreview() = executor.clearBrightnessPreview()

    override fun beginContinuousAdjust(mode: PanelAdjustMode, rawY: Float): Boolean =
        executor.beginContinuousAdjust(mode.toAppMode(), rawY)

    override fun updateContinuousAdjust(mode: PanelAdjustMode, rawY: Float) =
        executor.updateContinuousAdjust(mode.toAppMode(), rawY)

    override fun endContinuousAdjust() = executor.endContinuousAdjust()

    override fun applyAdjustOnce(mode: PanelAdjustMode, anchorRawY: Float) {
        executor.applyAdjustOnce(mode.toAppMode(), anchorRawY, anchorRawY)
    }

    override fun describeVolumeScale(stream: PanelVolumeStream): String {
        val context = overlayHostContext() ?: return ""
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            ?: return ""
        return VolumeControlHelper.describeScale(manager, stream.toAppStream().ordinal)
    }

    override fun hasVolumeAccess(context: Context): Boolean =
        VolumeControlHelper.hasAccess(context)

    override fun hasBrightnessAccess(context: Context): Boolean =
        BrightnessControlHelper.hasAccess(context)

    override fun hasDarkModeAccess(context: Context): Boolean =
        BrightnessControlHelper.hasDarkModeAccess(context)

    override fun isDndFilter(interruptionFilter: Int): Boolean =
        VolumeControlHelper.isDndFilter(interruptionFilter)

    override fun readBrightnessFraction(context: Context): Float =
        BrightnessControlHelper.readBrightnessFractionAccurate(context)

    override fun describeBrightnessScale(context: Context): String =
        BrightnessControlHelper.describeScale(context)
}

private fun PanelVolumeStream.toAppStream(): VolumeControlHelper.Stream = when (this) {
    PanelVolumeStream.MEDIA -> VolumeControlHelper.Stream.MEDIA
    PanelVolumeStream.RING -> VolumeControlHelper.Stream.RING
    PanelVolumeStream.NOTIFICATION -> VolumeControlHelper.Stream.NOTIFICATION
    PanelVolumeStream.ALARM -> VolumeControlHelper.Stream.ALARM
}
