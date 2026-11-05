package com.slideindex.app.overlay.layout

import android.graphics.RectF
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.overlay.PanelSide.LEFT
import com.slideindex.app.overlay.PanelSide.RIGHT
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class QuickLauncherPanelLayoutEngineTest {

    @Test
    fun gridLayoutInfo_clampsColumnsAndRows() {
        val info = QuickLauncherPanelLayoutEngine.gridLayoutInfo(
            columnsPerPage = 10,
            rowsPerPage = 20,
            cellWidth = 100f,
            gridPadding = 8f,
        )
        assertEquals(6, info.panelColumns)
        assertEquals(9, info.rows)
        assertEquals(6 * 100f + 16f, info.panelWidth, 0.01f)
    }

    @Test
    fun contentHeight_includesHeaderAndPadding() {
        val height = QuickLauncherPanelLayoutEngine.contentHeight(
            rows = 4,
            cellHeight = 120f,
            gridPadding = 8f,
            headerHeight = 48f,
        )
        assertEquals(4 * 120f + 16f + 48f, height, 0.01f)
    }

    @Test
    fun cellHeight_keepsLabeledHeightWhileLabelsVisible() {
        assertEquals(
            64f,
            QuickLauncherPanelLayoutEngine.cellHeight(
                showLabels = true,
                cellWidth = 56f,
                labeledCellHeight = 64f,
            ),
            0.001f,
        )
    }

    /** 关掉名称后行高 = 列宽，纵向图标间隙因此与横向相同。 */
    @Test
    fun cellHeight_withoutLabels_matchesCellWidth() {
        assertEquals(
            56f,
            QuickLauncherPanelLayoutEngine.cellHeight(
                showLabels = false,
                cellWidth = 56f,
                labeledCellHeight = 64f,
            ),
            0.001f,
        )
    }

    @Test
    fun anchoredPanelRect_placesPanelToRightOfLeftTrigger() {
        val host = fakeHost(
            side = LEFT,
            trigger = RectF(0f, 400f, 24f, 800f),
        )
        val rect = QuickLauncherPanelLayoutEngine.anchoredPanelRect(
            host = host,
            panelWidth = 300f,
            contentHeight = 400f,
            anchorLocalY = 600f,
        )
        assertEquals(24f + host.dp(8f), rect.left, 0.01f)
        assertEquals(400f, rect.height(), 0.01f)
        assertEquals(600f - 200f, rect.top, 0.01f)
    }

    @Test
    fun anchoredPanelRect_placesPanelToLeftOfRightTrigger() {
        val host = fakeHost(
            side = RIGHT,
            viewWidth = 1080,
            trigger = RectF(1056f, 400f, 1080f, 800f),
        )
        val rect = QuickLauncherPanelLayoutEngine.anchoredPanelRect(
            host = host,
            panelWidth = 300f,
            contentHeight = 400f,
            anchorLocalY = 600f,
        )
        assertEquals(1056f - host.dp(8f) - 300f, rect.left, 0.01f)
    }

    @Test
    fun offsetForToolbar_shiftsRightPanelAwayFromToolbarReserve() {
        val host = fakeHost(
            side = RIGHT,
            viewWidth = 400,
            trigger = RectF(376f, 0f, 400f, 200f),
        )
        val base = RectF(20f, 100f, 320f, 500f)
        val adjusted = QuickLauncherPanelLayoutEngine.offsetForToolbar(host, base, reserveWidth = 120f)
        assertEquals(84f, adjusted.left, 0.01f)
        assertEquals(384f, adjusted.right, 0.01f)
    }

    /**
     * 该侧一个触钮都没有时触发带是**零高**合成带（见 app 侧 `PanelTriggerBand`）：
     * 面板贴触发侧那条边、锚点落在触发高度而不是屏幕顶端。
     */
    @Test
    fun zeroHeightTriggerBand_keepsPanelOnTriggerSideAtFingerHeight() {
        val leftHost = fakeHost(side = LEFT, trigger = RectF(0f, 600f, 24f, 600f))
        val left = QuickLauncherPanelLayoutEngine.anchoredPanelRect(
            host = leftHost,
            panelWidth = 200f,
            contentHeight = 300f,
            anchorLocalY = 600f,
        )
        assertEquals(24f + leftHost.dp(8f), left.left, 0.01f)
        assertEquals(600f - 150f, left.top, 0.01f)

        val rightHost = fakeHost(side = RIGHT, trigger = RectF(1056f, 600f, 1080f, 600f))
        val right = QuickLauncherPanelLayoutEngine.anchoredPanelRect(
            host = rightHost,
            panelWidth = 200f,
            contentHeight = 300f,
            anchorLocalY = 600f,
        )
        assertEquals(1056f - rightHost.dp(8f) - 200f, right.left, 0.01f)
        assertEquals(600f - 150f, right.top, 0.01f)
    }

    /**
     * 外部触发（全局触控 / 角轮盘 / 悬浮球）不把面板拉回触钮条的纵向范围：
     * 同一个锚点，`clampAnchorToTrigger=false` 时面板跟着触发高度走。
     */
    @Test
    fun anchoredPanelRect_withoutClamp_followsAnchorOutsideTriggerBand() {
        val host = fakeHost(
            side = RIGHT,
            viewWidth = 1080,
            viewHeight = 1920,
            trigger = RectF(1056f, 1380f, 1080f, 1920f),
        )
        val clamped = QuickLauncherPanelLayoutEngine.anchoredPanelRect(
            host = host,
            panelWidth = 300f,
            contentHeight = 400f,
            anchorLocalY = 300f,
        )
        assertEquals(1380f - 200f, clamped.top, 0.01f)

        val free = QuickLauncherPanelLayoutEngine.anchoredPanelRect(
            host = host,
            panelWidth = 300f,
            contentHeight = 400f,
            anchorLocalY = 300f,
            clampAnchorToTrigger = false,
        )
        assertEquals(300f - 200f, free.top, 0.01f)
        // 横向仍贴触发侧，不受影响。
        assertEquals(1056f - host.dp(8f) - 300f, free.left, 0.01f)
    }

    private fun fakeHost(
        side: PanelSide,
        viewWidth: Int = 1080,
        viewHeight: Int = 1920,
        trigger: RectF = RectF(0f, 400f, 24f, 800f),
    ): OverlayPanelLayoutHost = object : OverlayPanelLayoutHost {
        override fun side(): PanelSide = side
        override fun activeTriggerZoneRect(): RectF = trigger
        override fun viewWidth(): Int = viewWidth
        override fun viewHeight(): Int = viewHeight
        override fun dp(value: Float): Float = value
    }
}
