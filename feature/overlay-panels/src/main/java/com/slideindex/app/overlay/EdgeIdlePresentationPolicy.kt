package com.slideindex.app.overlay

import android.os.Build
import android.util.Log

/**
 * 空闲态要不要让全屏 presentation 宿主常驻。
 *
 * presentation 是整屏（100%）的无障碍浮窗，空闲时是 `NOT_TOUCHABLE` + alpha=1 的透明窗。
 * Oplus（ColorOS / OxygenOS 15+）的反诈规则会把这种形态当成「黑屏浮窗」：开启无障碍
 * 约 2s 后由 `com.oplus.safecenter:accessibility` 强制摘掉服务（issue #42）。
 *
 * 命中这类 ROM 时退回「按需挂载」：空闲不挂窗，触钮按下时再补挂
 * （见 [SideOverlayWindowManager]）。代价是手势动画首帧要现建 Compose 组合，
 * 比常驻组合慢 20~30ms —— 换「服务不被系统杀掉」，值得。
 */
object EdgeIdlePresentationPolicy {

    /** true = 保持原来的「配了触钮就空闲常驻」；false = 空闲摘窗、按下再挂。 */
    val keepPresentationWhileIdle: Boolean = false

    /**
     * 是否 Oplus 的反诈规则区。参数留口子便于单测，默认取当前设备。
     *
     * 只圈 Android 15+：更老的 ColorOS 上没有这条「黑屏浮窗」判定，没必要牺牲动画手感。
     */
    internal fun isOplusAntiFraudRom(
        manufacturer: String = Build.MANUFACTURER,
        brand: String = Build.BRAND,
        sdkInt: Int = Build.VERSION.SDK_INT,
    ): Boolean {
        if (sdkInt < Build.VERSION_CODES.VANILLA_ICE_CREAM) return false
        val identity = "$manufacturer $brand".lowercase()
        return identity.contains("oneplus") ||
            identity.contains("oppo") ||
            identity.contains("oplus") ||
            identity.contains("realme")
    }
}
