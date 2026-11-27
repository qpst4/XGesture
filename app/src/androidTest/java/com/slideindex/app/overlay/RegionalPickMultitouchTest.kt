package com.slideindex.app.overlay

import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inspector.WindowInspector
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.slideindex.app.SlideIndexApp
import com.slideindex.app.gesture.TriggerHandle
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.EdgeTriggerSettings
import com.slideindex.app.settings.FloatBallSettings
import com.slideindex.app.settings.FloatBallSide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RegionalPickMultitouchTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val downTime = SystemClock.uptimeMillis()
    private var capture: EdgeGestureOverlayView? = null

    @Test
    fun secondFingerClearsSelectionAndFirstFingerCanSelectAgain() = exerciseSelection(throughEdgeCapture = false)

    @Test
    fun edgeCaptureForwardsSecondaryPointerChangesDuringAContinuedPick() = exerciseSelection(throughEdgeCapture = true)

    private fun exerciseSelection(throughEdgeCapture: Boolean) {
        val overlayHost = OverlayHostRuntime.accessibilityHostContext
        val accessibilityEnabled = OverlayHostRuntime.isAccessibilityServiceEnabled
        val context = instrumentation.targetContext.applicationContext as SlideIndexApp
        val scope = CoroutineScope(Dispatchers.Main)
        var controller: SideOverlayController? = null
        val settings = AppSettings(
            serviceEnabled = true,
            edgeTrigger = EdgeTriggerSettings(leftTriggerHandles = listOf(TriggerHandle.default(0f, 1f))),
            floatBall = FloatBallSettings(floatBallHoverPauseDelayMs = 50),
        )
        try {
            instrumentation.runOnMainSync {
                OverlayHostRuntime.accessibilityHostContext = { context }
                OverlayHostRuntime.isAccessibilityServiceEnabled = { false }
                if (throughEdgeCapture) {
                    controller = SideOverlayController(
                        context, PanelSide.LEFT, context.getSystemService(WindowManager::class.java),
                        context.deps.appRepository, scope,
                    ).apply { showEdge(sourceSettings = settings) }
                    capture = checkNotNull(controller?.overlayPresentation)
                    assertTrue(send(MotionEvent.ACTION_DOWN, 3f, 350f))
                }
                RegionalPickOverlay.launchFromEdge(
                    context = context,
                    appSettings = settings,
                    gestureStartRawY = 350f,
                    edgeSide = FloatBallSide.LEFT,
                    rawX = 120f,
                    rawY = 350f,
                )
                assertTrue("regional pick starts", RegionalPickOverlay.isActive)
            }
            SystemClock.sleep(90)
            instrumentation.runOnMainSync {
                assertTrue(send(MotionEvent.ACTION_MOVE, 200f, 420f))
                val selected = chrome()
                assertTrue("the drag has drawn a selection", selected.selectionDrawn)
                assertTrue("the second finger is handled", send(MotionEvent.ACTION_POINTER_DOWN, 200f, 420f, 760f, index = 1))
                val cleared = chrome()
                assertFalse("the selected region is cleared", cleared.selectionDrawn)
                assertTrue("the crosshair stays visible", cleared.crosshairY.isFinite())
                assertEquals(selected.crosshairY, cleared.crosshairY, 0.01f)
                assertTrue(RegionalPickOverlay.isActive)
                assertTrue(send(MotionEvent.ACTION_POINTER_UP, 200f, 420f, 760f, index = 1))
            }
            SystemClock.sleep(120)
            instrumentation.runOnMainSync {
                assertFalse("the old selection cannot return while stationary", chrome().selectionDrawn)
                val beforeMove = chrome().crosshairY
                assertTrue(send(MotionEvent.ACTION_MOVE, 280f, 520f))
                assertTrue("the first finger still moves the crosshair", chrome().crosshairY > beforeMove)
            }
            SystemClock.sleep(90)
            instrumentation.runOnMainSync {
                assertTrue(send(MotionEvent.ACTION_MOVE, 360f, 610f))
                assertTrue("the first finger can start a fresh selection", chrome().selectionDrawn)
                assertTrue(send(MotionEvent.ACTION_POINTER_DOWN, 360f, 610f, 760f, index = 1))
                assertTrue(send(MotionEvent.ACTION_POINTER_UP, 360f, 610f, 760f, index = 0))
                assertFalse("lifting the first finger ends the drag", RegionalPickOverlay.isActive)
            }
        } finally {
            instrumentation.runOnMainSync {
                RegionalPickOverlay.dismiss()
                capture = null
                controller?.destroy()
                scope.cancel()
                OverlayHostRuntime.accessibilityHostContext = overlayHost
                OverlayHostRuntime.isAccessibilityServiceEnabled = accessibilityEnabled
            }
        }
    }

    private fun send(action: Int, x: Float, y: Float, secondX: Float? = null, index: Int = 0): Boolean {
        val xs = listOfNotNull(x, secondX)
        val event = MotionEvent.obtain(
            downTime, SystemClock.uptimeMillis(), action or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), xs.size,
            Array(xs.size) { pointer ->
                MotionEvent.PointerProperties().apply {
                    id = pointer + 3
                    toolType = MotionEvent.TOOL_TYPE_FINGER
                }
            },
            Array(xs.size) { pointer ->
                MotionEvent.PointerCoords().apply {
                    this.x = xs[pointer]
                    this.y = y + pointer * 80f
                    pressure = 1f
                    size = 1f
                }
            },
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0,
        )
        return try {
            capture?.handleCaptureStripTouch(event, 0) ?: RegionalPickOverlay.forwardContinuedTouch(event)
        } finally {
            event.recycle()
        }
    }

    private fun chrome(): CursorCanvas {
        val cursor = WindowInspector.getGlobalWindowViews().asSequence()
            .filterIsInstance<ViewGroup>()
            .flatMap { window -> (0 until window.childCount).asSequence().map(window::getChildAt) }
            .filterIsInstance<FloatBallCursorPreviewView>()
            .first { it.isShown }
        return CursorCanvas().also(cursor::draw)
    }

    private class CursorCanvas : Canvas() {
        var selectionDrawn = false
        var crosshairY = Float.NaN

        override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
            if (right > left && bottom > top) selectionDrawn = true
        }

        override fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint) {
            if (startY == stopY) crosshairY = startY
        }
    }
}
