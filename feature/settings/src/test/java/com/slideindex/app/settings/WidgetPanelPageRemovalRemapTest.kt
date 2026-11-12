package com.slideindex.app.settings

import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.GestureTriggerMode
import com.slideindex.app.gesture.GestureTriggerType
import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemCodec
import com.slideindex.app.launcher.QuickLauncherPanel
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.widget.WidgetPanelPage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 删除小组件面板的某一页时，指向它的槽位/动作要跟着改写到存活页。
 *
 * 覆盖三条真实存储路径：圆环槽位（FV 槽位）、侧边手势槽位（手势规则）、其它启动面板里的条目。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class WidgetPanelPageRemovalRemapTest {
    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() {
        repository = testSettingsRepository(RuntimeEnvironment.getApplication())
    }

    @Test
    fun removingPage_remapsRingSlotGestureSlotAndPanelItem() = runBlocking {
        val keptPageId = 1L
        val removedPageId = 2L
        val quickLauncherPanelId = "test-ql-panel"

        assertTrue(
            repository.setQuickLauncherPanels(
                listOf(QuickLauncherPanel(id = quickLauncherPanelId, name = "QL")),
            ).isSuccess,
        )
        val initialWrite = repository.setWidgetPanelPages(
            listOf(
                WidgetPanelPage(id = keptPageId, name = "Keep"),
                WidgetPanelPage(id = removedPageId, name = "Remove"),
            ),
        )
        assertTrue("写入面板失败: $initialWrite", initialWrite.isSuccess)

        val widgetPanelItem = QuickLauncherItem.action(
            GestureAction.WidgetPopupOverlay(removedPageId.toString()),
            "小组件面板",
        )
        assertTrue(
            "写入圆环槽位失败",
            repository.setFvRingLauncherSlot(FvRingLauncherAxis.VERTICAL, 0, widgetPanelItem).isSuccess,
        )
        assertTrue(
            "写入手势槽位失败",
            repository.setSlotConfig(
                side = PanelSide.LEFT,
                trigger = GestureTriggerType.SHORT_SWIPE_IN,
                action = GestureAction.WidgetPopupOverlay(removedPageId.toString()),
                triggerMode = GestureTriggerMode.DEFAULT,
            ).isSuccess,
        )
        assertTrue(
            "写入启动面板条目失败",
            repository.updateQuickLauncherPanelItems(quickLauncherPanelId, listOf(widgetPanelItem)).isSuccess,
        )

        // 真实删除路径是「把剩下的页原样写回」，这里保持同样语义。
        val survivingPages = repository.settings.first().widgetPanelPages
            .filterNot { it.id == removedPageId }
        val removeWrite = repository.setWidgetPanelPages(survivingPages)
        assertTrue("删除页失败: $removeWrite", removeWrite.isSuccess)

        val expected = GestureAction.WidgetPopupOverlay(keptPageId.toString())
        val settings = repository.settings.first()

        assertEquals(
            expected,
            settings
                .fvRingLauncherFor(FvRingLauncherAxis.VERTICAL)
                .slots[0]
                ?.let { QuickLauncherItemCodec.parseActionPayload(it.payload) },
        )
        assertEquals(expected, settings.slotAction(PanelSide.LEFT, GestureTriggerType.SHORT_SWIPE_IN))
        assertEquals(
            expected,
            settings.quickLauncherPanels
                .first { it.id == quickLauncherPanelId }
                .items
                .first()
                .payload
                .let(QuickLauncherItemCodec::parseActionPayload),
        )
        assertEquals(listOf(keptPageId), settings.widgetPanelPages.map { it.id })
    }

    @Test
    fun writingPagesWithoutRemovalKeepsBindings() = runBlocking {
        val pageId = 1L
        assertTrue(
            repository.setQuickLauncherPanels(listOf(QuickLauncherPanel(id = "ql", name = "QL"))).isSuccess,
        )
        assertTrue(
            repository.setWidgetPanelPages(listOf(WidgetPanelPage(id = pageId, name = "Only"))).isSuccess,
        )
        val action = GestureAction.WidgetPopupOverlay(pageId.toString())
        assertTrue(
            repository.setSlotConfig(
                side = PanelSide.LEFT,
                trigger = GestureTriggerType.SHORT_SWIPE_IN,
                action = action,
                triggerMode = GestureTriggerMode.DEFAULT,
            ).isSuccess,
        )

        // 拖滑块、改列数也会走这个入口；没有删页时引用必须原样保留。
        assertTrue(
            repository.setWidgetPanelPages(
                listOf(WidgetPanelPage(id = pageId, name = "Only", cellWidthDp = 80)),
            ).isSuccess,
        )

        assertEquals(
            action,
            repository.settings.first().slotAction(PanelSide.LEFT, GestureTriggerType.SHORT_SWIPE_IN),
        )
    }
}
