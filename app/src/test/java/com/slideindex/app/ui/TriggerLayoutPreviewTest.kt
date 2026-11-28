package com.slideindex.app.ui

import com.slideindex.app.gesture.TriggerHandle
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.EdgeTriggerSettings
import com.slideindex.app.settings.FreeWindowMode
import com.slideindex.app.settings.FreeWindowSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TriggerLayoutPreviewTest {
    private val settings = AppSettings(
        freeWindow = FreeWindowSettings(freeWindowModeId = FreeWindowMode.STANDARD.id),
        edgeTrigger = EdgeTriggerSettings(
            leftEdgeEnabled = true,
            rightEdgeEnabled = true,
            leftTriggerHandles = listOf(
                TriggerHandle("first", 0.25f, 0.5f, edgeWidthDp = 12f),
                TriggerHandle("second", 0.5f, 0.5f, edgeWidthDp = 20f),
            ),
            rightTriggerHandles = listOf(TriggerHandle("first", 0.25f, 0.5f, edgeWidthDp = 12f)),
            topTriggerHandles = listOf(TriggerHandle("top", 0.25f, 0.5f, edgeWidthDp = 8f)),
            bottomTriggerHandles = listOf(TriggerHandle("bottom", 0.5f, 0.25f, edgeWidthDp = 10f)),
        ),
    )

    @Test
    fun collectionPreviewShowsAllFourEdgesAndPreservesOverlap() {
        val bands = triggerLayoutPreviewBands(settings, width = 400, height = 800, density = 2f)
        assertEquals(setOf("first", "second", "top", "bottom"), bands.map { it.handleId }.toSet())
        val left = bands.filter { it.side == PanelSide.LEFT }
        assertEquals(2, left.size)
        assertEquals(0f, left.first().left, 0f)
        assertEquals(24f, left.first().right, 0f)
        assertEquals(200f, left.first().top, 0f)
        assertTrue(left.first().bottom > left.last().top)
        val right = bands.single { it.side == PanelSide.RIGHT }
        assertEquals(400f, right.right, 0f)
        assertEquals(left.first().colorIndex, right.colorIndex)
        val top = bands.single { it.side == PanelSide.TOP }
        assertEquals(0f, top.top, 0f)
        assertEquals(100f, top.left, 0f)
        val bottom = bands.single { it.side == PanelSide.BOTTOM }
        assertEquals(800f, bottom.bottom, 0f)
    }

    @Test
    fun previewNumbersMatchConfiguredOrderWhenColorsRepeatAndAGroupIsDisabled() {
        val handles = (1..4).map { number ->
            TriggerHandle("group-$number", 0.25f, 0.5f, enabled = number != 2)
        }
        val numbered = settings.copy(
            edgeTrigger = settings.edgeTrigger.copy(leftTriggerHandles = handles, rightTriggerHandles = handles),
        )
        val bands = triggerLayoutPreviewBands(numbered, 400, 800, 2f)
        for (side in listOf(PanelSide.LEFT, PanelSide.RIGHT)) {
            assertEquals(listOf(1, 3, 4), bands.filter { it.side == side }.map { it.configurationNumber })
        }
        assertEquals(1, bands.single { it.side == PanelSide.TOP }.configurationNumber)
        assertEquals(1, bands.single { it.side == PanelSide.BOTTOM }.configurationNumber)
    }

    @Test
    fun previewOmitsDisabledHandlesAndDisabledSides() {
        val disabled = settings.copy(
            edgeTrigger = settings.edgeTrigger.copy(
                leftEdgeEnabled = false,
                topTriggerHandles = listOf(TriggerHandle.topDefault()),
                bottomTriggerHandles = listOf(TriggerHandle.bottomDefault()),
            ),
        )
        val bands = triggerLayoutPreviewBands(disabled, 400, 800, 2f)
        assertEquals(listOf(PanelSide.RIGHT), bands.map { it.side })
        assertEquals(0, bands.single().colorIndex)
    }

    @Test
    fun draggingUpdatesBothAlignedEdgesWithoutMovingOtherHandles() {
        val before = triggerLayoutPreviewBands(settings, 400, 800, 2f)
        val after = triggerLayoutPreviewBands(
            settings, 400, 800, 2f,
            draft = TriggerLayoutPreviewDraft(PanelSide.LEFT, "first", 16f, 0.125f..0.25f),
        )
        val edited = after.filter { it.handleId == "first" }
        assertEquals(2, edited.size)
        edited.forEach {
            assertEquals(100f, it.top, 0f)
            assertEquals(200f, it.bottom, 0f)
            assertEquals(32f, it.right - it.left, 0f)
        }
        assertEquals(before.filterNot { it.handleId == "first" }, after.filterNot { it.handleId == "first" })
    }
}
