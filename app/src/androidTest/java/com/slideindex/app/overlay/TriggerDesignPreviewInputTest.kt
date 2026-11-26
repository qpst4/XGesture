package com.slideindex.app.overlay

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.slideindex.app.SlideIndexApp
import com.slideindex.app.gesture.TriggerHandle
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.EdgeTriggerSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TriggerDesignPreviewInputTest {
    @Test
    fun designPreviewStaysVisibleThroughAnAcceptedEdgeTap() {
        val (before, during, after) = tapEdge(showPairedGroup = true)
        assertTrue(before.isNotEmpty())
        assertEquals(before, during)
        assertEquals(before, after)
    }

    @Test
    fun ordinaryPreviewYieldsToGestureTrackingAndReturnsAfterRelease() {
        val (before, during, after) = tapEdge(showPairedGroup = false)
        assertTrue(before.isNotEmpty())
        assertTrue(during.isEmpty())
        assertEquals(before, after)
    }

    private fun tapEdge(showPairedGroup: Boolean): Triple<List<Int>, List<Int>, List<Int>> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as SlideIndexApp
        val scope = CoroutineScope(Dispatchers.Main)
        val trigger = TriggerHandle.default(0.25f, 0.5f)
        val settings = AppSettings(
            serviceEnabled = true,
            edgeTrigger = EdgeTriggerSettings(rightTriggerHandles = listOf(trigger)),
        )
        var result: Triple<List<Int>, List<Int>, List<Int>>? = null
        instrumentation.runOnMainSync {
            val controller = SideOverlayController(
                app, PanelSide.RIGHT, app.getSystemService(WindowManager::class.java), app.deps.appRepository, scope,
            )
            try {
                controller.showEdge(sourceSettings = settings)
                controller.setPreviewMode(true, focus = LayoutPreviewFocus(PanelSide.RIGHT, trigger.id, showPairedGroup = showPairedGroup))
                val view = checkNotNull(controller.overlayPresentation)
                val (width, height) = controller.authoritativeScreenSizePx()
                if (view.width == 0 || view.height == 0) view.layout(0, 0, width, height)
                val before = rangeWidths(view)
                val x = width - 2f
                val y = height * (trigger.topFraction + trigger.heightFraction / 2f)
                val time = SystemClock.uptimeMillis()
                val down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, x, y, 0)
                val up = MotionEvent.obtain(time, time + 40, MotionEvent.ACTION_UP, x, y, 0)
                try {
                    assertTrue("The capture strip must accept the press", view.handleCaptureStripTouch(down, 0))
                    val during = rangeWidths(view)
                    view.handleCaptureStripTouch(up, 0)
                    result = Triple(before, during, rangeWidths(view))
                } finally {
                    down.recycle()
                    up.recycle()
                }
            } finally {
                controller.destroy()
                scope.cancel()
            }
        }
        return checkNotNull(result)
    }

    private fun rangeWidths(view: EdgeGestureOverlayView): List<Int> = buildList {
        view.draw(object : Canvas() {
            override fun drawRoundRect(rect: RectF, radiusX: Float, radiusY: Float, paint: Paint) {
                val range = paint.color == Color.argb(36, 33, 150, 243) ||
                    paint.color == Color.argb(72, 255, 152, 0)
                if (range && paint.style == Paint.Style.FILL && rect.left > view.width / 2f) {
                    add(rect.width().toInt())
                }
            }
        })
    }
}
