package com.slideindex.app.overlay.corner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.overlay.layout.FvIconShape
import com.slideindex.app.overlay.panels.PanelRuntimeHooks
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 边角轮盘的槽位图标必须和快速启动器 / 圆环启动器一样，先看「图标库」里的全局覆盖。
 *
 * 回归点：corner 整组迁出 `:feature:overlay-panels`（1d9dc8c3）时删掉了
 * [CornerSlotIconBitmap.get] 顶部的覆盖查询（面板模块看不到 `:app` 的 `ActionIconBitmap`），
 * 于是动作自定义图标在轮盘里又变回默认矢量图标；快速启动器 / 圆环启动器走
 * `QuickLauncherIconResolver`，那边是查覆盖的，所以只有轮盘不对。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class CornerSlotIconOverrideTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    private val overrideBitmap: Bitmap =
        Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }

    private val legacyBitmap: Bitmap =
        Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }

    @After
    fun tearDown() {
        PanelRuntimeHooks.globalIconKey = null
        PanelRuntimeHooks.actionIconBitmapFull = null
        PanelRuntimeHooks.managedShortcutBitmap = null
    }

    /** 覆盖必须排在「快捷方式自身图标」分支之前：图标库是按动作类型设的，快捷方式也是动作类型。 */
    @Test
    fun actionTypeOverride_winsBeforeShortcutIconBranch() {
        PanelRuntimeHooks.globalIconKey = { token -> if (token == "LAUNCH_SHORTCUT") "l:back" else null }
        PanelRuntimeHooks.actionIconBitmapFull = { _, _, _, _, _, _, _ -> overrideBitmap }
        PanelRuntimeHooks.managedShortcutBitmap = { _, _, _, _ -> legacyBitmap }

        val out = CornerSlotIconBitmap.get(
            context = context,
            action = GestureAction.LaunchShortcut.component("com.example.host/.MainActivity"),
            sizePx = 24,
            tintArgb = Color.WHITE,
        )

        assertSame("设了动作类型覆盖后，轮盘不能再显示快捷方式原图", overrideBitmap, out)
    }

    /** 没设覆盖时仍走原分支（快捷方式自身图标），不能因为加了覆盖查询就改变默认行为。 */
    @Test
    fun noOverride_keepsShortcutIconBranch() {
        PanelRuntimeHooks.globalIconKey = { null }
        PanelRuntimeHooks.actionIconBitmapFull = { _, _, _, _, _, _, _ -> overrideBitmap }
        PanelRuntimeHooks.managedShortcutBitmap = { _, _, _, _ -> legacyBitmap }

        val out = CornerSlotIconBitmap.get(
            context = context,
            action = GestureAction.LaunchShortcut.component("com.example.host/.MainActivity"),
            sizePx = 24,
            tintArgb = Color.WHITE,
        )

        assertSame(legacyBitmap, out)
    }

    /** 渲染参数要原样透传：尺寸 / 着色 / 描边 / 底板（plateFallback=false 的功能环是裸图标）/ 形状。 */
    @Test
    fun overrideRender_receivesSizeTintAndPlateFlag() {
        var capturedSize = -1
        var capturedTint = -1
        var capturedOutlined = false
        var capturedWithPlate = true
        var capturedShape = -1
        PanelRuntimeHooks.globalIconKey = { "t:替" }
        PanelRuntimeHooks.actionIconBitmapFull = { _, _, size, tint, outlined, withPlate, shape ->
            capturedSize = size
            capturedTint = tint
            capturedOutlined = outlined
            capturedWithPlate = withPlate
            capturedShape = shape
            overrideBitmap
        }

        CornerSlotIconBitmap.get(
            context = context,
            action = GestureAction.OpenIndex,
            sizePx = 40,
            tintArgb = Color.GREEN,
            shape = FvIconShape.SQUIRCLE,
            plateFallback = false,
        )

        assertEquals(40, capturedSize)
        assertEquals(Color.GREEN, capturedTint)
        assertEquals(true, capturedOutlined)
        assertEquals(false, capturedWithPlate)
        assertEquals(2, capturedShape) // SQUIRCLE 在 GestureActionPlateShape 里是第 3 个
    }

    /** 轮盘是每帧重绘，覆盖位图必须命中缓存，否则字体图标每帧都要重新排版。 */
    @Test
    fun overrideBitmap_cachedAcrossFrames() {
        var renderCalls = 0
        PanelRuntimeHooks.globalIconKey = { "t:替" }
        PanelRuntimeHooks.actionIconBitmapFull = { _, _, _, _, _, _, _ ->
            renderCalls++
            overrideBitmap
        }

        repeat(3) {
            CornerSlotIconBitmap.get(
                context = context,
                action = GestureAction.OpenIndex,
                sizePx = 24,
                tintArgb = Color.WHITE,
            )
        }

        assertEquals("同一份覆盖图标只该渲染一次", 1, renderCalls)
    }
}
