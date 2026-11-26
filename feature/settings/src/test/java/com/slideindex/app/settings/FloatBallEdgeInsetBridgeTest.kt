package com.slideindex.app.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 悬浮球「距边缘距离」必须能穿过 OverlaySettings → toMinimalAppSettings 这条桥。
 *
 * 之前 OverlaySettings 没这个字段，外观页读到的是默认值，滑条回显和真实位置不一致。
 */
class FloatBallEdgeInsetBridgeTest {

    @Test
    fun edgeInsetSurvivesOverlayRoundTrip() {
        val app = AppSettings(
            floatBallEdgeInsetDp = -33,
            // 显式给 freeWindowModeId：纯 JVM 测试没有 Build.MANUFACTURER，
            // 默认参数会走 detectDefault() 抛 NPE。
            freeWindow = FreeWindowSettings(freeWindowModeId = FreeWindowMode.STANDARD.id),
        )
        val overlay = OverlaySettings.from(app)
        assertEquals(-33, overlay.floatBallEdgeInsetDp)
        assertEquals(-33, overlay.toMinimalAppSettings().floatBallEdgeInsetDp)
    }
}
