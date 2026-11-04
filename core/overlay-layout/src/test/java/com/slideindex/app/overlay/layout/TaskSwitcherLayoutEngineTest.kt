package com.slideindex.app.overlay.layout

import android.graphics.RectF
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.overlay.PanelSide.LEFT
import com.slideindex.app.overlay.PanelSide.RIGHT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class TaskSwitcherLayoutEngineTest {

    @Test
    fun compute_placesPanelToRightOfLeftTrigger() {
        val host = fakeHost(
            side = LEFT,
            trigger = RectF(0f, 400f, 24f, 800f),
        )
        val rows = listOf(
            TaskSwitcherRowEntry("com.example.a", 1),
            TaskSwitcherRowEntry("com.example.b", 2),
        )
        val (layout, _) = TaskSwitcherLayoutEngine.compute(
            host = host,
            rows = rows,
            scrollOffset = 0f,
            anchorLocalY = 600f,
        )
        assertEquals(24f + host.dp(10f), layout.panelRect.left, 0.01f)
        assertEquals(2, layout.rows.size)
        assertEquals("com.example.a", layout.rows[0].entry.packageName)
    }

    @Test
    fun compute_placesPanelToLeftOfRightTrigger() {
        val host = fakeHost(
            side = RIGHT,
            viewWidth = 1080,
            trigger = RectF(1056f, 400f, 1080f, 800f),
        )
        val (layout, _) = TaskSwitcherLayoutEngine.compute(
            host = host,
            rows = listOf(TaskSwitcherRowEntry("com.example.a")),
            scrollOffset = 0f,
            anchorLocalY = 600f,
        )
        assertEquals(1056f - host.dp(10f) - layout.panelRect.width(), layout.panelRect.left, 0.01f)
    }

    @Test
    fun compute_clampsScrollOffset() {
        val host = fakeHost(
            side = LEFT,
            viewHeight = 1920,
            trigger = RectF(0f, 0f, 24f, 400f),
        )
        val rowHeight = host.dp(42f)
        val rows = (1..20).map { TaskSwitcherRowEntry("com.example.$it", it) }
        val (layout, offset) = TaskSwitcherLayoutEngine.compute(
            host = host,
            rows = rows,
            scrollOffset = 9999f,
            anchorLocalY = 200f,
        )
        val peek = TaskSwitcherLayoutEngine.scrollPeekHeight(host, rowHeight)
        val expectedVisible = rowHeight * TaskSwitcherLayoutEngine.MAX_VISIBLE_ROWS + peek
        assertEquals(expectedVisible, layout.listRect.height(), 0.01f)
        assertEquals(peek, layout.listScrollPeekHeight, 0.01f)
        assertEquals(20 * rowHeight - expectedVisible, layout.maxScrollOffset, 0.01f)
        assertEquals(layout.maxScrollOffset, offset, 0.01f)
    }

    @Test
    fun compute_atMostNineRows_noScrollPeek() {
        val host = fakeHost(side = LEFT)
        val rowHeight = host.dp(42f)
        val rows = (1..9).map { TaskSwitcherRowEntry("com.example.$it", it) }
        val (layout, _) = TaskSwitcherLayoutEngine.compute(
            host = host,
            rows = rows,
            scrollOffset = 0f,
            anchorLocalY = 600f,
        )
        assertEquals(0f, layout.listScrollPeekHeight, 0.01f)
        assertEquals(9 * rowHeight, layout.listRect.height(), 0.01f)
        assertEquals(0f, layout.maxScrollOffset, 0.01f)
    }

    /**
     * 该侧一个触钮都没有时，`activeTriggerZoneRect()` 返回的是**零高**合成带
     *（见 app 侧 `PanelTriggerBand`：拿本次触发点合成"贴本侧那条边"的窄带）。
     *
     * 锁两条契约：面板贴触发侧那条边、锚点落在触发高度而不是被夹到屏幕顶端。
     */
    @Test
    fun zeroHeightTriggerBand_keepsPanelOnTriggerSideAtFingerHeight() {
        val rows = listOf(
            TaskSwitcherRowEntry("com.example.a", 1),
            TaskSwitcherRowEntry("com.example.b", 2),
        )
        val leftHost = fakeHost(side = LEFT, trigger = RectF(0f, 600f, 24f, 600f))
        val (leftLayout, _) = TaskSwitcherLayoutEngine.compute(
            host = leftHost,
            rows = rows,
            scrollOffset = 0f,
            anchorLocalY = 600f,
        )
        assertEquals(24f + leftHost.dp(10f), leftLayout.panelRect.left, 0.01f)
        // 首行中心对准触发高度：600 - rowHeight/2（42 / 2）。
        assertEquals(579f, leftLayout.panelRect.top, 0.01f)

        val rightHost = fakeHost(side = RIGHT, trigger = RectF(1056f, 600f, 1080f, 600f))
        val (rightLayout, _) = TaskSwitcherLayoutEngine.compute(
            host = rightHost,
            rows = listOf(TaskSwitcherRowEntry("com.example.a", 1)),
            scrollOffset = 0f,
            anchorLocalY = 600f,
        )
        assertEquals(
            1056f - rightHost.dp(10f) - rightHost.dp(226f),
            rightLayout.panelRect.left,
            0.01f,
        )
        assertEquals(579f, rightLayout.panelRect.top, 0.01f)
    }

    /**
     * 底部触钮是横条，`trigger.right` 只是横条右端、不是屏幕边：面板必须以触钮中心水平居中，
     * 不能再沿用「贴触钮右缘」的公式（否则右缘会跑到屏幕外，只剩一条边可见）。
     */
    @Test
    fun compute_bottomTrigger_centersPanelHorizontally() {
        val host = fakeHost(
            side = PanelSide.BOTTOM,
            trigger = RectF(54f, 1900f, 1026f, 1920f),
        )
        val (layout, _) = TaskSwitcherLayoutEngine.compute(
            host = host,
            rows = listOf(TaskSwitcherRowEntry("com.example.a", 1)),
            scrollOffset = 0f,
            anchorLocalY = 1910f,
        )
        assertEquals(540f, layout.panelRect.centerX(), 0.01f)
        assertTrue(layout.panelRect.left >= 16f)
        assertTrue(layout.panelRect.right <= 1080f - 16f)
    }

    /** 底部触钮靠右：居中后仍会越界时夹回屏内，而不是画到屏幕外。 */
    @Test
    fun compute_bottomTriggerNearRightEdge_staysInsideScreen() {
        val host = fakeHost(
            side = PanelSide.BOTTOM,
            trigger = RectF(918f, 1900f, 1026f, 1920f),
        )
        val (layout, _) = TaskSwitcherLayoutEngine.compute(
            host = host,
            rows = listOf(TaskSwitcherRowEntry("com.example.a", 1)),
            scrollOffset = 0f,
            anchorLocalY = 1910f,
        )
        assertEquals(1080f - 16f, layout.panelRect.right, 0.01f)
        assertTrue(layout.panelRect.left >= 16f)
    }

    /** 顶部触钮同理：横条没有「内侧边」，按触钮中心居中落位。 */
    @Test
    fun compute_topTrigger_centersPanelHorizontally() {
        val host = fakeHost(
            side = PanelSide.TOP,
            trigger = RectF(200f, 0f, 400f, 20f),
        )
        val (layout, _) = TaskSwitcherLayoutEngine.compute(
            host = host,
            rows = listOf(TaskSwitcherRowEntry("com.example.a", 1)),
            scrollOffset = 0f,
            anchorLocalY = 10f,
        )
        assertEquals(300f, layout.panelRect.centerX(), 0.01f)
        assertTrue(layout.panelRect.left >= 16f)
        assertTrue(layout.panelRect.right <= 1080f - 16f)
    }

    /** 窄屏：面板整体收窄到「屏宽 - 左右边距」，不能出现负宽或越界。 */
    @Test
    fun compute_narrowScreen_shrinksPanelToFit() {
        val host = fakeHost(
            side = PanelSide.BOTTOM,
            viewWidth = 240,
            trigger = RectF(0f, 1900f, 240f, 1920f),
        )
        val (layout, _) = TaskSwitcherLayoutEngine.compute(
            host = host,
            rows = listOf(TaskSwitcherRowEntry("com.example.a", 1)),
            scrollOffset = 0f,
            anchorLocalY = 1910f,
        )
        assertEquals(240f - 32f, layout.panelRect.width(), 0.01f)
        assertEquals(16f, layout.panelRect.left, 0.01f)
        assertEquals(240f - 16f, layout.panelRect.right, 0.01f)
    }

    /** 左侧触钮在极窄屏同样要被夹回屏内（侧边贴边展开的常规行为不变）。 */
    @Test
    fun compute_leftTriggerOnNarrowScreen_clampsPanelInsideScreen() {
        val host = fakeHost(
            side = LEFT,
            viewWidth = 200,
            trigger = RectF(0f, 400f, 24f, 800f),
        )
        val (layout, _) = TaskSwitcherLayoutEngine.compute(
            host = host,
            rows = listOf(TaskSwitcherRowEntry("com.example.a", 1)),
            scrollOffset = 0f,
            anchorLocalY = 600f,
        )
        assertEquals(16f, layout.panelRect.left, 0.01f)
        assertEquals(200f - 16f, layout.panelRect.right, 0.01f)
    }

    /** 底部触钮带手指坐标时，面板中心对准触发点，而不是锁死在屏幕中间。 */
    @Test
    fun compute_bottomTrigger_usesAnchorXAsPanelCenter() {
        val host = fakeHost(
            side = PanelSide.BOTTOM,
            trigger = RectF(54f, 1900f, 1026f, 1920f),
        )
        val (layout, _) = TaskSwitcherLayoutEngine.compute(
            host = host,
            rows = listOf(TaskSwitcherRowEntry("com.example.a", 1)),
            scrollOffset = 0f,
            anchorLocalY = 1910f,
            anchorLocalX = 200f,
        )
        assertEquals(200f, layout.panelRect.centerX(), 0.01f)
    }

    /** 触发点贴左边缘：面板被夹到左边距，但整体仍在屏内。 */
    @Test
    fun compute_bottomTriggerAnchorNearLeftEdge_clampsToMargin() {
        val host = fakeHost(
            side = PanelSide.BOTTOM,
            trigger = RectF(0f, 1900f, 1080f, 1920f),
        )
        val (layout, _) = TaskSwitcherLayoutEngine.compute(
            host = host,
            rows = listOf(TaskSwitcherRowEntry("com.example.a", 1)),
            scrollOffset = 0f,
            anchorLocalY = 1910f,
            anchorLocalX = 20f,
        )
        assertEquals(16f, layout.panelRect.left, 0.01f)
        assertTrue(layout.panelRect.right <= 1080f - 16f)
    }

    /** 触发点贴右边缘：同样夹回屏内。 */
    @Test
    fun compute_bottomTriggerAnchorNearRightEdge_clampsToMargin() {
        val host = fakeHost(
            side = PanelSide.BOTTOM,
            trigger = RectF(0f, 1900f, 1080f, 1920f),
        )
        val (layout, _) = TaskSwitcherLayoutEngine.compute(
            host = host,
            rows = listOf(TaskSwitcherRowEntry("com.example.a", 1)),
            scrollOffset = 0f,
            anchorLocalY = 1910f,
            anchorLocalX = 1060f,
        )
        assertEquals(1080f - 16f, layout.panelRect.right, 0.01f)
        assertTrue(layout.panelRect.left >= 16f)
    }

    /** 左右侧触钮仍贴边展开，忽略横坐标锚点（避免改变既有手感）。 */
    @Test
    fun compute_anchorX_ignoredOnLeftSide() {
        val host = fakeHost(
            side = LEFT,
            trigger = RectF(0f, 400f, 24f, 800f),
        )
        val (layout, _) = TaskSwitcherLayoutEngine.compute(
            host = host,
            rows = listOf(TaskSwitcherRowEntry("com.example.a", 1)),
            scrollOffset = 0f,
            anchorLocalY = 600f,
            anchorLocalX = 900f,
        )
        assertEquals(24f + host.dp(10f), layout.panelRect.left, 0.01f)
    }

    /** 对照：空触发带（没触钮又没有合成带）会把面板夹到顶端 —— 这正是合成带要解决的问题。 */
    @Test
    fun emptyTriggerBand_pinsPanelToTop() {
        val host = fakeHost(side = LEFT, trigger = RectF())
        val (layout, _) = TaskSwitcherLayoutEngine.compute(
            host = host,
            rows = listOf(TaskSwitcherRowEntry("com.example.a", 1)),
            scrollOffset = 0f,
            anchorLocalY = 600f,
        )
        assertEquals(host.dp(16f), layout.panelRect.top, 0.01f)
    }

    private fun fakeHost(
        side: PanelSide,
        viewWidth: Int = 1080,
        viewHeight: Int = 1920,
        trigger: RectF = RectF(0f, 400f, 24f, 800f),
    ): TaskSwitcherLayoutHost = object : TaskSwitcherLayoutHost {
        override fun side(): PanelSide = side
        override fun activeTriggerZoneRect(): RectF = trigger
        override fun viewWidth(): Int = viewWidth
        override fun viewHeight(): Int = viewHeight
        override fun dp(value: Float): Float = value
    }
}
