package com.slideindex.app.overlay

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inspector.WindowInspector
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.slideindex.app.SlideIndexApp
import com.slideindex.app.gesture.TriggerHandle
import com.slideindex.app.inspire.PickPrefetchCache
import com.slideindex.app.overlay.FloatBallCursorPreviewView.HintMode
import com.slideindex.app.service.AccessibilityTextExtractor
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.EdgeTriggerSettings
import com.slideindex.app.settings.FloatBallSettings
import com.slideindex.app.settings.FloatBallSide
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
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

    @Test
    fun pausedCursorShowsTextHintAfterTextArrives() = exerciseLatePreview(listOf("selected text"))

    @Test
    fun pausedCursorShowsScreenshotHintForEmptySelection() =
        exerciseLatePreview(emptyList(), expectedHint = HintMode.SCREENSHOT)

    @Test
    fun pausedCursorShowsHintWithFirstFrameOfKnownTextSelection() =
        exerciseLatePreview(listOf("selected text"), hasNodeText = true)

    @Test
    fun selectedNativeImageWithDescriptionShowsTextHint() =
        exerciseLatePreview(
            listOf("Photo description"),
            hasNodeText = true,
            imageClass = "android.widget.ImageView",
            imageDescription = "Photo description",
        )

    @Test
    fun weChatImageWithSingleCharacterDescriptionShowsTextHint() =
        exerciseLatePreview(
            listOf("无"),
            hasNodeText = true,
            imageClass = "android.widget.Image",
            imageDescription = "无",
        )

    @Test
    fun selectedWebImageShowsScreenshotHintWhenScanReturnsSurroundingText() =
        exerciseLatePreview(
            listOf("Nearby caption"),
            imageClass = "android.widget.Image",
            expectedHint = HintMode.SCREENSHOT,
        )

    private fun exerciseLatePreview(
        words: List<String>,
        hasNodeText: Boolean = false,
        imageClass: String? = null,
        imageDescription: String? = null,
        expectedHint: HintMode = HintMode.TEXT,
    ) {
        val overlayHost = OverlayHostRuntime.accessibilityHostContext
        val accessibilityEnabled = OverlayHostRuntime.isAccessibilityServiceEnabled
        val serviceField = SlideIndexAccessibilityService::class.java.getDeclaredField("instance").apply { isAccessible = true }
        val context = instrumentation.targetContext.applicationContext as SlideIndexApp
        val settings = AppSettings(floatBall = FloatBallSettings(floatBallHoverPauseDelayMs = 50))
        val service = serviceField.get(null)
        val result = CompletableDeferred<List<String>>()
        val activeColor = AtomicReference<Int>()
        try {
            instrumentation.runOnMainSync {
                // Control accessibility results independently of the app in the foreground.
                serviceField.set(null, null)
                OverlayHostRuntime.accessibilityHostContext = { context }
                OverlayHostRuntime.isAccessibilityServiceEnabled = { false }
                RegionalPickOverlay.launchFromEdge(
                    context, settings, gestureStartRawY = 350f, edgeSide = FloatBallSide.LEFT,
                    rawX = 120f, rawY = 350f,
                )
                activeColor.set(chrome().crosshairColor)
            }
            SystemClock.sleep(90)
            instrumentation.runOnMainSync {
                val waiting = chrome()
                assertFalse("the text hint waits for text", waiting.hintDrawn)
                assertTrue("the crosshair changes color when the cursor pauses", activeColor.get() != waiting.crosshairColor)
                assertFalse("the selection waits for accessibility bounds", waiting.selectionDrawn)
                val target = Rect(
                    waiting.crosshairX.toInt() - 20, waiting.crosshairY.toInt() - 10,
                    waiting.crosshairX.toInt() + 20, waiting.crosshairY.toInt() + 10,
                )
                val generation = RegionalPickOverlay::class.java.getDeclaredField("boundsLookupGeneration")
                    .apply { isAccessible = true }.getInt(RegionalPickOverlay) + 1
                seedTextPrefetch(target, generation, result)
                serviceField.set(null, SlideIndexAccessibilityService())
                try {
                    val preview = if (imageClass == null) {
                        AccessibilityTextExtractor.PreviewBoundsEntry(target, 0, hasNodeText)
                    } else {
                        imagePreview(target, imageClass, imageDescription)
                    }
                    publishPreviewBounds(preview)
                } finally {
                    serviceField.set(null, null)
                }
                assertEquals("the first drag draws the selection without moving or lifting",
                    RectF(target), chrome().selection)
                val firstHint = when {
                    hasNodeText -> HintMode.TEXT
                    imageClass != null -> HintMode.SCREENSHOT
                    else -> HintMode.HIDDEN
                }
                assertEquals("the hint reflects what is known when the box appears", firstHint, chrome().hintMode)
                val replacement = Rect(target).apply { inset(-10, -5) }
                publishPreviewBounds(AccessibilityTextExtractor.PreviewBoundsEntry(replacement, 0))
                assertEquals("a later scan keeps the selected bounds locked", RectF(target), chrome().selection)
            }
            result.complete(words)
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val resolved = chrome()
                assertEquals("the hint reflects the selected content", expectedHint, resolved.hintMode)
                assertTrue("the badge is drawn after the selection is resolved", resolved.hintDrawn)
                assertTrue(send(MotionEvent.ACTION_MOVE, 200f, 420f))
                assertTrue("moving from the paused cursor still starts regional selection", chrome().selectionDrawn)
                assertEquals("regional selection keeps its screenshot hint", HintMode.SCREENSHOT, chrome().hintMode)
            }
        } finally {
            instrumentation.runOnMainSync {
                RegionalPickOverlay.dismiss()
                FloatBallPreviewBoundsCache.invalidate()
                serviceField.set(null, service)
                OverlayHostRuntime.accessibilityHostContext = overlayHost
                OverlayHostRuntime.isAccessibilityServiceEnabled = accessibilityEnabled
            }
        }
    }

    private fun seedTextPrefetch(bounds: Rect, generation: Int, result: CompletableDeferred<List<String>>) {
        val cacheClass = PickPrefetchCache::class.java
        mapOf("prefetchRect" to bounds, "prefetchGeneration" to generation, "prefetchDeferred" to result)
            .forEach { (name, value) ->
                cacheClass.getDeclaredField(name).apply { isAccessible = true }.set(PickPrefetchCache, value)
            }
    }

    private fun imagePreview(bounds: Rect, imageClass: String, description: String?): AccessibilityTextExtractor.PreviewBoundsEntry {
        val node = AccessibilityNodeInfo().apply {
            className = imageClass
            if (imageClass == "android.widget.Image") packageName = "com.tencent.mm"
            contentDescription = description
            isVisibleToUser = true
            isClickable = true
        }
        return AccessibilityTextExtractor::class.java.getDeclaredMethod(
            "previewBoundsEntry", AccessibilityNodeInfo::class.java, Rect::class.java, Int::class.javaPrimitiveType,
        ).apply { isAccessible = true }
            .invoke(AccessibilityTextExtractor, node, bounds, bounds.width() * bounds.height()) as
            AccessibilityTextExtractor.PreviewBoundsEntry
    }

    private fun publishPreviewBounds(preview: AccessibilityTextExtractor.PreviewBoundsEntry) {
        // Deliver the result of the asynchronous scan while the finger is still held.
        val field = FloatBallPreviewBoundsCache::class.java.getDeclaredField("entriesRef").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val entries = field.get(null) as AtomicReference<List<AccessibilityTextExtractor.PreviewBoundsEntry>>
        entries.set(listOf(preview))
        RegionalPickOverlay::class.java.getDeclaredMethod("applyPreviewBoundsFromCache")
            .apply { isAccessible = true }.invoke(RegionalPickOverlay)
    }

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
        val hintMode = FloatBallCursorPreviewView::class.java.getDeclaredField("hintMode")
            .apply { isAccessible = true }.get(cursor) as HintMode
        return CursorCanvas(hintMode).also(cursor::draw)
    }

    private class CursorCanvas(val hintMode: HintMode) : Canvas() {
        var selection: RectF? = null
        val selectionDrawn: Boolean get() = selection != null
        var hintDrawn = false
        var crosshairX = Float.NaN
        var crosshairY = Float.NaN
        var crosshairColor = 0

        override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
            if (right > left && bottom > top) selection = RectF(left, top, right, bottom)
        }

        override fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) {
            hintDrawn = true
        }

        override fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint) {
            if (startY == stopY) {
                crosshairX = (startX + stopX) / 2f
                crosshairY = startY
                crosshairColor = paint.color
            }
        }
    }
}
