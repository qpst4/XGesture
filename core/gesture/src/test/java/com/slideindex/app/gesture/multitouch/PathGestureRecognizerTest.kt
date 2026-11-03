package com.slideindex.app.gesture.multitouch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PathGestureRecognizerTest {

    private fun twoFingerFrames(fromX: Float, toX: Float, steps: Int): List<List<TouchPointer>> {
        val frames = ArrayList<List<TouchPointer>>()
        for (step in 0..steps) {
            val x = fromX + (toX - fromX) * step / steps
            frames += listOf(
                TouchPointer(0, x, 0.45f),
                TouchPointer(1, x, 0.55f),
            )
        }
        return frames
    }

    private fun feed(
        collector: PathGestureCollector,
        frames: List<List<TouchPointer>>,
    ): CollectedPath? {
        for (frame in frames) {
            collector.onFrame(frame)
        }
        return collector.onFrame(emptyList())
    }

    private fun line(fromX: Float, toX: Float, y: Float, steps: Int = 20): PathStroke =
        PathStroke((0..steps).map { step ->
            PathPoint(fromX + (toX - fromX) * step / steps, y)
        })

    @Test
    fun `two finger strokes are collected`() {
        val collector = PathGestureCollector()
        val result = feed(collector, twoFingerFrames(0.2f, 0.8f, 20))
        assertNotNull(result)
        assertEquals(2, result?.strokes?.size)
        assertEquals(2, result?.fingerCount)
        assertTrue((result?.strokes?.first()?.points?.size ?: 0) >= 2)
    }

    @Test
    fun `single stroke is collected`() {
        val collector = PathGestureCollector()
        val frames = (0..20).map { step ->
            listOf(TouchPointer(0, 0.2f + step * 0.03f, 0.5f))
        }
        val result = feed(collector, frames)
        assertNotNull(result)
        assertEquals(1, result?.strokes?.size)
        assertEquals(1, result?.fingerCount)
    }

    @Test
    fun `cancelled path is not returned`() {
        val collector = PathGestureCollector()
        for (frame in twoFingerFrames(0.2f, 0.8f, 20)) {
            collector.onFrame(frame)
        }
        collector.cancel()
        assertNull(collector.onFrame(emptyList()))
    }

    @Test
    fun `same two strokes at another position match`() {
        val template = PathGestureTemplate(
            id = "t",
            name = "双横",
            strokes = listOf(line(0.2f, 0.8f, 0.45f), line(0.2f, 0.8f, 0.55f)),
        )
        val live = listOf(line(0.1f, 0.9f, 0.4f), line(0.1f, 0.9f, 0.6f))
        val match = PathGestureMatcher.bestMatch(live, listOf(template))
        assertEquals("t", match?.id)
    }

    @Test
    fun `different stroke count does not match`() {
        val template = PathGestureTemplate(
            id = "t",
            name = "双横",
            strokes = listOf(line(0.2f, 0.8f, 0.45f), line(0.2f, 0.8f, 0.55f)),
        )
        val live = listOf(line(0.2f, 0.8f, 0.5f))
        assertNull(PathGestureMatcher.bestMatch(live, listOf(template)))
    }

    @Test
    fun `best match picks the closest template`() {
        val horizontal = PathGestureTemplate(
            id = "h",
            name = "横线",
            strokes = listOf(line(0.2f, 0.8f, 0.45f), line(0.2f, 0.8f, 0.55f)),
        )
        val vertical = PathGestureTemplate(
            id = "v",
            name = "竖线",
            strokes = listOf(
                PathStroke((0..20).map { step -> PathPoint(0.45f, 0.2f + step * 0.03f) }),
                PathStroke((0..20).map { step -> PathPoint(0.55f, 0.2f + step * 0.03f) }),
            ),
        )
        val live = listOf(line(0.15f, 0.85f, 0.42f), line(0.15f, 0.85f, 0.58f))
        assertEquals("h", PathGestureMatcher.bestMatch(live, listOf(horizontal, vertical))?.id)
    }
}
