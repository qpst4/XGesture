package com.slideindex.app.gesture.multitouch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PathGestureRecognizerTest {

    private fun centroidFrames(
        fromX: Float,
        toX: Float,
        y: Float,
        steps: Int,
        fingers: Int = 3,
    ): List<List<TouchPointer>> {
        val frames = ArrayList<List<TouchPointer>>()
        for (step in 0..steps) {
            val x = fromX + (toX - fromX) * step / steps
            frames += (0 until fingers).map { index ->
                TouchPointer(
                    id = index,
                    x = x + (index - 1) * 0.02f,
                    y = y + (index - 1) * 0.015f,
                )
            }
        }
        return frames
    }

    private fun feedCollector(
        collector: PathGestureCollector,
        frames: List<List<TouchPointer>>,
    ): CollectedPath? {
        var result: CollectedPath? = null
        for (frame in frames) {
            result = collector.onFrame(frame) ?: result
        }
        return collector.onFrame(emptyList()) ?: result
    }

    @Test
    fun `three finger path is collected and returned on lift`() {
        val collector = PathGestureCollector()
        val result = feedCollector(
            collector,
            centroidFrames(0.2f, 0.8f, 0.5f, steps = 30),
        )
        assertNotNull(result)
        assertEquals(3, result?.fingerCount)
        assertTrue((result?.points?.size ?: 0) >= 8)
    }

    @Test
    fun `short path is discarded`() {
        val collector = PathGestureCollector()
        val result = feedCollector(
            collector,
            centroidFrames(0.5f, 0.52f, 0.5f, steps = 3),
        )
        assertNull(result)
    }

    @Test
    fun `two finger path is ignored`() {
        val collector = PathGestureCollector(minFingerCount = 3)
        val result = feedCollector(
            collector,
            centroidFrames(0.2f, 0.8f, 0.5f, steps = 30, fingers = 2),
        )
        assertNull(result)
    }

    @Test
    fun `cancelled path is not returned`() {
        val collector = PathGestureCollector()
        for (frame in centroidFrames(0.2f, 0.8f, 0.5f, steps = 30)) {
            collector.onFrame(frame)
        }
        collector.cancel()
        assertNull(collector.onFrame(emptyList()))
    }

    @Test
    fun `same path at different position and scale matches`() {
        val line = PathGestureMatcher.prepare(
            (0..30).map { step -> PathPoint(0.2f + step * 0.02f, 0.5f) }
        )
        val moved = PathGestureMatcher.prepare(
            (0..30).map { step -> PathPoint(0.5f + step * 0.03f, 0.8f) }
        )
        val distance = PathGestureMatcher.distance(line, moved)
        assertTrue("distance=$distance", distance < 0.05f)
    }

    @Test
    fun `opposite path does not match`() {
        val forward = PathGestureMatcher.prepare(
            (0..30).map { step -> PathPoint(0.2f + step * 0.02f, 0.5f) }
        )
        val backward = PathGestureMatcher.prepare(
            (0..30).map { step -> PathPoint(0.8f - step * 0.02f, 0.5f) }
        )
        assertTrue(PathGestureMatcher.distance(forward, backward) > PathGestureMatcher.MATCH_THRESHOLD)
    }

    @Test
    fun `best match picks the closest template`() {
        val line = (0..30).map { step -> PathPoint(0.2f + step * 0.02f, 0.5f) }
        val circle = (0..30).map { step ->
            val angle = step * 2.0 * Math.PI / 30.0
            PathPoint(
                x = (0.5 + 0.2 * Math.cos(angle)).toFloat(),
                y = (0.5 + 0.2 * Math.sin(angle)).toFloat(),
            )
        }
        val templates = listOf(
            PathGestureTemplate("circle", "圆圈", 3, circle),
            PathGestureTemplate("line", "横线", 3, line),
        )
        val match = PathGestureMatcher.bestMatch(
            line.map { PathPoint(it.x + 0.05f, it.y - 0.03f) },
            templates,
        )
        assertEquals("line", match?.id)
    }
}
