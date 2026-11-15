package com.slideindex.app.overlay

import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatBallPositionMode
import com.slideindex.app.settings.FloatBallSettings
import com.slideindex.app.settings.FloatBallSide
import com.slideindex.app.settings.FreeWindowMode
import com.slideindex.app.settings.FreeWindowSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 锁住「悬浮球 / 双贴边线条的接管矩形指纹必须随位置变化」这条行为。
 *
 * 回归背景（真机必现）：接管矩形是按下发那一刻的球位、停靠侧、球径、线条开关算出来的。
 * 一旦配置签名里漏了这些字段，球线换位 / 拖动后签名不变 → `distinctUntilChanged` 吞掉发射
 * → 不重新下发 → 模块仍按旧侧矩形命中，而球的新位置通常正好落在该侧两条触钮手柄之间的
 * 竖直空隙里（没有任何矩形覆盖）→ 模块直接放行 → 只剩系统返回手势。
 *
 * 这条链要真的用到 `android.graphics.Rect` 与 `DisplayMetrics`（纯 JVM 的 android.jar 桩里
 * `Rect` 构造是空实现、坐标恒为 0），所以用 Robolectric；SDK 固定 34 是因为本机只缓存了
 * API 11–14 的 android-all（不固定会去下载 targetSdk 那份）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TakeoverExtraRectsFingerprintTest {

    private val screenWidthPx = 1080
    private val screenHeightPx = 2340
    private val density = 2.75f

    private fun fingerprint(settings: AppSettings): String =
        TakeoverExtraRects.fingerprint(settings, screenWidthPx, screenHeightPx, density, false)

    /** 双边模式：球在一侧、线条在另一侧——"换位"就是改 [FloatBallSettings.floatBallActiveSide]。 */
    private fun bothEdges(side: FloatBallSide = FloatBallSide.LEFT): AppSettings =
        AppSettings(
            floatBall = FloatBallSettings(
                floatBallEnabled = true,
                floatBallPositionMode = FloatBallPositionMode.BOTH_EDGES,
                floatBallActiveSide = side,
            ),
            // 显式给出，避免默认值走 FreeWindowMode.detectDefault() 读 Build.MANUFACTURER。
            freeWindow = FreeWindowSettings(freeWindowModeId = FreeWindowMode.STANDARD.id),
        )

    private fun AppSettings.withBall(block: FloatBallSettings.() -> FloatBallSettings): AppSettings =
        copy(floatBall = floatBall.block())

    @Test
    fun swappingActiveSide_changesFingerprint() {
        val left = fingerprint(bothEdges(FloatBallSide.LEFT))
        val right = fingerprint(bothEdges(FloatBallSide.RIGHT))
        assertTrue("双边模式下两侧都应有接管矩形", left.isNotEmpty() && right.isNotEmpty())
        assertNotEquals("球线换位后指纹必须变化，否则不会重新下发", left, right)
    }

    @Test
    fun movingBallAlongEdge_changesFingerprint() {
        val base = bothEdges()
        val moved = base.withBall { copy(floatBallPositionYFraction = floatBallPositionYFraction + 0.2f) }
        assertNotEquals(fingerprint(base), fingerprint(moved))
    }

    @Test
    fun resizingBall_changesFingerprint() {
        val base = bothEdges()
        val bigger = base.withBall { copy(floatBallSizeDp = floatBallSizeDp + 12f) }
        assertNotEquals(fingerprint(base), fingerprint(bigger))
    }

    @Test
    fun hidingBallDeeperIntoEdge_changesFingerprint() {
        val base = bothEdges()
        // 「往里/往外挪」现在由距离控制：负值 = 往屏幕外挪（旧版 50% 露出）。
        val shallower = base.copy(floatBallEdgeInsetDp = -24)
        assertNotEquals(fingerprint(base), fingerprint(shallower))
    }

    @Test
    fun resizingLine_changesFingerprint() {
        val base = bothEdges()
        val longer = base.withBall { copy(floatBallLineHeightFraction = floatBallLineHeightFraction + 0.1f) }
        assertNotEquals(fingerprint(base), fingerprint(longer))
    }

    @Test
    fun changingLineWidthDp_changesFingerprint() {
        val base = bothEdges()
        val wider = base.withBall { copy(floatBallLineWidthDp = floatBallLineWidthDp + 24f) }
        assertNotEquals(fingerprint(base), fingerprint(wider))
    }

    @Test
    fun leavingBothEdgesMode_changesFingerprint() {
        val base = bothEdges()
        val single = base.withBall { copy(floatBallPositionMode = FloatBallPositionMode.LEFT) }
        assertNotEquals("离开双边模式后线条矩形应消失", fingerprint(base), fingerprint(single))
    }

    @Test
    fun disabledBall_hasNoBallRect() {
        val disabled = bothEdges().withBall { copy(floatBallEnabled = false) }
        assertFalse("悬浮球关闭时不该再有 target=16 的接管矩形", fingerprint(disabled).contains("target=16"))
    }

    @Test
    fun sameSettings_produceSameFingerprint() {
        val base = bothEdges()
        assertEquals(fingerprint(base), fingerprint(base))
    }
}
