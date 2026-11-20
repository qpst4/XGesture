package com.slideindex.app.util

import android.app.Application
import android.content.Context
import android.os.SystemClock
import android.util.TypedValue
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FreeWindowMode
import com.slideindex.app.settings.FreeWindowSettings
import com.slideindex.app.settings.ignoresLaunchBounds
import com.slideindex.app.settings.usesSunshineBroadcast
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBuild

/**
 * 小窗 launch bounds 的三条契约：固定百分比、底部安全边距、跟随触发位置。
 *
 * 这里刻意只断言**可独立解释的性质**（贴边距、不越界、居中），不照抄实现里的算式，
 * 免得测试退化成"实现怎么写就怎么对"。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class FreeWindowLauncherBoundsTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private val widthPx: Int get() = context.resources.displayMetrics.widthPixels
    private val heightPx: Int get() = context.resources.displayMetrics.heightPixels

    /** 20mm 换算成像素；安全边距取 `max(状态栏, 20mm)`，所以它一定是下界。 */
    private val safeBottomLowerBoundPx: Int
        get() = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_MM,
            20f,
            context.resources.displayMetrics,
        ).toInt()

    @After
    fun clearAnchor() {
        FreeWindowTriggerAnchor.clear()
    }

    private fun settings(
        mode: FreeWindowMode = FreeWindowMode.STANDARD,
        widthDp: Int = 230,
        heightDp: Int = 380,
        leftDp: Int = 70,
        topDp: Int = 250,
        edgeMarginDp: Int = 70,
        followTrigger: Boolean = false,
    ): AppSettings = AppSettings(
        freeWindow = FreeWindowSettings(
            freeWindowEnabled = true,
            freeWindowModeId = mode.id,
            freeWindowWidthDp = widthDp,
            freeWindowHeightDp = heightDp,
            freeWindowLeftDp = leftDp,
            freeWindowTopDp = topDp,
            freeWindowEdgeMarginDp = edgeMarginDp,
            freeWindowFollowTriggerSide = followTrigger,
        ),
    )

    /** 默认配置放得下：结果就是配置的物理 dp 尺寸（转换为 px）。 */
    @Test
    fun defaultLayoutIsUntouchedBySafeMargin() {
        val density = context.resources.displayMetrics.density
        val bounds = FreeWindowLauncher.launchBounds(context, settings())

        assertEquals((70 * density).toInt(), bounds.left)
        // 顶部为 statusBarHeight + topDp*density
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBar = if (id > 0) context.resources.getDimensionPixelSize(id) else 0
        assertEquals(statusBar + (250 * density).toInt(), bounds.top)
        assertEquals((230 * density).toInt(), bounds.width())
        assertEquals((380 * density).toInt(), bounds.height())
    }

    /** 配置高到会压住底部手势条 / 输入法区：高度与位置都被压回安全区之内。 */
    @Test
    fun tallLayoutIsClampedAboveBottomSafeMargin() {
        val bounds = FreeWindowLauncher.launchBounds(
            context,
            settings(widthDp = 300, heightDp = 800, topDp = 200),
        )

        assertTrue(
            "bottom=${bounds.bottom} 应不超过 屏幕高-安全边距(${heightPx - safeBottomLowerBoundPx})",
            bounds.bottom <= heightPx - safeBottomLowerBoundPx,
        )
        assertTrue(bounds.height() <= heightPx - safeBottomLowerBoundPx)
    }

    /** 横屏同样受安全边距约束（横屏高度更矮，安全区占比更大）。 */
    @Test
    @Config(sdk = [33], application = Application::class, qualifiers = "w891dp-h411dp-land-xxhdpi")
    fun landscapeLayoutRespectsSafeMargin() {
        val bounds = FreeWindowLauncher.launchBounds(
            context,
            settings(heightDp = 400, topDp = 100),
        )

        assertTrue(
            "bottom=${bounds.bottom} 应不超过 屏幕高-安全边距(${heightPx - safeBottomLowerBoundPx})",
            bounds.bottom <= heightPx - safeBottomLowerBoundPx,
        )
    }

    /** 跟随右触发：窗口贴右，右侧留白 = 配置的贴边距 edgeMargin。 */
    @Test
    fun followTriggerOnRightEdgeDocksRight() {
        val density = context.resources.displayMetrics.density
        val edgeMarginDp = 50
        val base = settings(
            widthDp = 200,
            heightDp = 300,
            leftDp = 70,
            edgeMarginDp = edgeMarginDp,
            followTrigger = true,
        )
        val withoutFollow = FreeWindowLauncher.launchBounds(context, settings(
            widthDp = 200,
            heightDp = 300,
            leftDp = 70,
            edgeMarginDp = edgeMarginDp,
            followTrigger = false,
        ))

        FreeWindowTriggerAnchor.record(PanelSide.RIGHT, heightPx / 2f)
        val bounds = FreeWindowLauncher.launchBounds(context, base)

        val expectedRightMargin = (edgeMarginDp * density).toInt()
        assertEquals(expectedRightMargin, widthPx - bounds.right)
        assertNotEquals(withoutFollow.left, bounds.left)
        assertEquals(withoutFollow.width(), bounds.width())
    }

    /** 跟随左触发：贴左。 */
    @Test
    fun followTriggerOnLeftEdgeDocksLeft() {
        val density = context.resources.displayMetrics.density
        val edgeMarginDp = 60
        val config = settings(
            widthDp = 200,
            heightDp = 300,
            leftDp = 60,
            edgeMarginDp = edgeMarginDp,
            followTrigger = true,
        )

        FreeWindowTriggerAnchor.record(PanelSide.LEFT, heightPx / 2f)
        val bounds = FreeWindowLauncher.launchBounds(context, config)

        val expectedLeftMargin = (edgeMarginDp * density).toInt()
        assertEquals(expectedLeftMargin, bounds.left)
    }

    /** 竖直方向以触发高度居中。 */
    @Test
    fun followTriggerCentersOnTriggerHeight() {
        val config = settings(
            widthDp = 200,
            heightDp = 200,
            followTrigger = true,
        )
        val triggerY = heightPx / 2f

        FreeWindowTriggerAnchor.record(PanelSide.LEFT, triggerY)
        val bounds = FreeWindowLauncher.launchBounds(context, config)

        assertEquals((triggerY - bounds.height() / 2f).toInt(), bounds.top)
    }

    /** 关闭跟随开关时，锚点完全不参与计算。 */
    @Test
    fun followDisabledIgnoresAnchor() {
        FreeWindowTriggerAnchor.record(PanelSide.RIGHT, heightPx / 2f)

        val bounds = FreeWindowLauncher.launchBounds(context, settings(followTrigger = false))
        val density = context.resources.displayMetrics.density

        assertEquals((70 * density).toInt(), bounds.left)
    }

    /** 过期锚点按"没有锚点"处理：手势开面板、很久之后再启动不会拿旧方位摆窗口。 */
    @Test
    fun staleAnchorIsIgnored() {
        FreeWindowTriggerAnchor.record(PanelSide.RIGHT, heightPx / 2f)

        assertNull(
            FreeWindowTriggerAnchor.peek(
                nowMs = SystemClock.uptimeMillis() + FreeWindowTriggerAnchor.MAX_AGE_MS + 1,
            ),
        )
        assertTrue(FreeWindowTriggerAnchor.peek() != null)
    }

    /** 米窗自己决定窗口几何：尺寸 / 位置 / 跟随对它无效，设置页据此置灰。 */
    @Test
    fun sunshineModeIgnoresLaunchBounds() {
        assertTrue(FreeWindowMode.SUNSHINE.usesSunshineBroadcast())
        assertTrue(FreeWindowMode.SUNSHINE.ignoresLaunchBounds())
        assertFalse(FreeWindowMode.STANDARD.ignoresLaunchBounds())
    }

    /**
     * 交付上限：只有「标准模式 + 小米」才有二次缩放，上限=补偿系数；
     * 其它模式/机型上限是整屏。设置页与编辑器都按这个值限制拖拽。
     */
    @Test
    fun deliverableLimitFractionMatchesCompensation() {
        assertEquals(
            0.7f,
            FreeWindowLauncher.deliverableLimitFraction(
                FreeWindowMode.STANDARD,
                isLandscape = false,
                isXiaomi = true,
            ),
            0.0001f,
        )
        assertEquals(
            0.555f,
            FreeWindowLauncher.deliverableLimitFraction(
                FreeWindowMode.STANDARD,
                isLandscape = true,
                isXiaomi = true,
            ),
            0.0001f,
        )
        assertEquals(
            1f,
            FreeWindowLauncher.deliverableLimitFraction(
                FreeWindowMode.STANDARD,
                isLandscape = false,
                isXiaomi = false,
            ),
            0.0001f,
        )
        // 非「标准」模式不做补偿，即便机型是小米（与 resolveScaleCompensation 一致）。
        assertEquals(
            1f,
            FreeWindowLauncher.deliverableLimitFraction(
                FreeWindowMode.COLOROS,
                isLandscape = false,
                isXiaomi = true,
            ),
            0.0001f,
        )
    }

    /**
     * 小米 MIUI 13+ / HyperOS：竖屏补偿 1.42f，且以 left / top 为原点锚点。
     */
    @Test
    fun xiaomiUsesMiuiCompensationAndTopLeftAnchor() {
        ShadowBuild.setManufacturer("Xiaomi")
        ShadowBuild.setBrand("Xiaomi")

        val density = context.resources.displayMetrics.density
        val config = settings(widthDp = 200, heightDp = 300, leftDp = 50, topDp = 100)
        val bounds = FreeWindowLauncher.launchBounds(context, config)

        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBar = if (id > 0) context.resources.getDimensionPixelSize(id) else 0

        // 锚点为 Top-Left：left 与 top 不缩放，直接作为起始边界
        assertEquals((50 * density).toInt(), bounds.left)
        assertEquals(statusBar + (100 * density).toInt(), bounds.top)

        // 宽高按 1.42f 补偿放大
        val expectedW = ((200 * density) * 1.42f).toInt()
        val expectedH = ((300 * density) * 1.42f).toInt()
        assertEquals(expectedW, bounds.width())
        assertEquals(expectedH, bounds.height())
    }
}
