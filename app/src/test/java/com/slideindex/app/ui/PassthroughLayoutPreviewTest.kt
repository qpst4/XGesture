package com.slideindex.app.ui

import com.slideindex.app.gesture.TriggerDesignKind
import com.slideindex.app.gesture.TriggerHandle
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.EdgeTriggerSettings
import com.slideindex.app.settings.FreeWindowMode
import com.slideindex.app.settings.FreeWindowSettings
import com.slideindex.app.ui.navigation.AppNavKey
import com.slideindex.app.ui.navigation.focusedTriggerPreviewKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PassthroughLayoutPreviewTest {
    private val handle = TriggerHandle.default().copy(
        id = "reserved",
        passthrough = true,
        topFraction = 0.25f,
        heightFraction = 0.5f,
        edgeWidthDp = 20f,
    )

    @Test
    fun `toggling passthrough keeps the entire focused preview identical`() {
        val visible = handle.copy(design = handle.design.copy(kind = TriggerDesignKind.CONFIGURABLE_RECTANGLE, sizeDp = 8f))
        val frames = listOf(false, true, false).map { passthrough ->
            val working = settings.copy(
                edgeTrigger = settings.edgeTrigger.copy(rightTriggerHandles = listOf(visible.copy(passthrough = passthrough))),
            )
            focusedSideTriggerBounds(working, PanelSide.RIGHT, handle.id, 400, 800, 2f)
        }

        assertEquals(List(3) { TriggerPreviewBounds(360f, 200f, 400f, 600f) }, frames)
    }

    @Test
    fun `opening the layout and design editors retains the same trigger preview`() {
        val details = AppNavKey.HomeSideGestures("RIGHT", handle.id)
        val layout = AppNavKey.HomeSideGesturesAppearance(details.side, details.handleId)
        val design = AppNavKey.HomeSideGesturesDesign(details.side, details.handleId)

        assertEquals(List(5) { details }, listOf(details, layout, details, design, details).map { it.focusedTriggerPreviewKey() })
    }

    @Test
    fun `leaving the trigger or editing another configuration ends its preview`() {
        val details = AppNavKey.HomeSideGestures("RIGHT", handle.id)
        val anotherSide = AppNavKey.HomeSideGesturesAppearance("LEFT", handle.id)
        val anotherProfile = AppNavKey.HomeSideGesturesAppearance(details.side, handle.id, "other")

        assertEquals(details.copy(side = "LEFT"), anotherSide.focusedTriggerPreviewKey())
        assertEquals(details.copy(profileId = "other"), anotherProfile.focusedTriggerPreviewKey())
        assertNull(AppNavKey.HomeTriggerCollection.focusedTriggerPreviewKey())
    }
    private val settings = AppSettings(
        freeWindow = FreeWindowSettings(freeWindowModeId = FreeWindowMode.STANDARD.id),
        edgeTrigger = EdgeTriggerSettings(
            leftTriggerHandles = listOf(handle),
            rightTriggerHandles = listOf(handle),
        ),
    )

    @Test
    fun `the selected passthrough handle shows its configured range on either edge`() {
        assertEquals(
            TriggerPreviewBounds(0f, 200f, 40f, 600f),
            focusedSideTriggerBounds(settings, PanelSide.LEFT, handle.id, 400, 800, 2f),
        )
        assertEquals(
            TriggerPreviewBounds(360f, 200f, 400f, 600f),
            focusedSideTriggerBounds(settings, PanelSide.RIGHT, handle.id, 400, 800, 2f),
        )
    }

    @Test
    fun `missing handles have no focused preview`() {
        assertNull(focusedSideTriggerBounds(settings, PanelSide.LEFT, "missing", 400, 800, 2f))
        assertNull(focusedSideTriggerBounds(settings, PanelSide.TOP, handle.id, 400, 800, 2f))
    }

    @Test
    fun `the design editor can preview the top edge`() {
        val top = settings.copy(edgeTrigger = settings.edgeTrigger.copy(topTriggerHandles = listOf(handle)))

        assertEquals(
            TriggerPreviewBounds(100f, 0f, 300f, 40f),
            focusedSideTriggerBounds(top, PanelSide.TOP, handle.id, 400, 800, 2f),
        )
    }

    @Test
    fun `an in-progress edit previews the new width and length before saving`() {
        assertEquals(
            TriggerPreviewBounds(340f, 100f, 400f, 400f),
            focusedSideTriggerBounds(settings, PanelSide.RIGHT, handle.id, 400, 800, 2f, 30f, 0.125f..0.5f),
        )
    }
}
