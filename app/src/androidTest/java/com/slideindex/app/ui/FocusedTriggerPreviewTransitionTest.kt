package com.slideindex.app.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.Choreographer
import android.view.ViewTreeObserver
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inspector.WindowInspector
import androidx.activity.compose.setContent
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.slideindex.app.MainActivity
import com.slideindex.app.R
import com.slideindex.app.gesture.TriggerDesignKind
import com.slideindex.app.gesture.TriggerHandle
import com.slideindex.app.overlay.PanelSide
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.EdgeTriggerSettings
import com.slideindex.app.settings.FreeWindowMode
import com.slideindex.app.settings.FreeWindowSettings
import com.slideindex.app.settings.triggerCollectionEntries
import com.slideindex.app.ui.navigation.AppNavKey
import com.slideindex.app.ui.navigation.focusedTriggerPreviewKey
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FocusedTriggerPreviewTransitionTest {
    @Test
    fun ordinarySideTriggerKeepsItsRangeInTheActualLayoutEditor() =
        verifyEditorPreview(R.string.trigger_appearance_title, R.string.handle_width)

    @Test
    fun ordinarySideTriggerKeepsItsRangeInTheActualDesignEditor() =
        verifyEditorPreview(R.string.trigger_design_title, R.string.trigger_design_section)

    @Test
    fun touchingTheEdgeKeepsTheDesignPreviewVisible() =
        verifyEditorPreview(R.string.trigger_design_title, R.string.trigger_design_section, touchEdge = true)

    private fun verifyEditorPreview(
        editorTitle: Int,
        editorContent: Int,
        touchEdge: Boolean = false,
    ) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
            automation.executeShellCommand(command),
        ).bufferedReader().use { it.readText() }
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        val launch = shell("am start -W -n ${instrumentation.targetContext.packageName}/${MainActivity::class.java.name}")
        val startedActivity = instrumentation.waitForMonitorWithTimeout(monitor, 5_000)
        assertNotNull(launch, startedActivity)
        val activity = startedActivity as MainActivity
        val repository = activity.deps.settingsRepository
        val settings = repository.readSnapshot()
        try {
            val entries = settings.triggerCollectionEntries()
            val index = entries.indexOfFirst { entry -> entry.right?.let { it.enabled && !it.passthrough } == true }
            assertTrue("An enabled ordinary right-edge trigger is required", index >= 0)
            val sideLabel = activity.getString(R.string.trigger_side_right_item)
            val row = if (entries.size == 1) sideLabel else
                "$sideLabel · ${activity.getString(R.string.trigger_pair_index, index + 1)}"

            fun find(label: String, scroll: Boolean = false): AccessibilityNodeInfo {
                var visible = emptyList<Pair<String, String>>()
                repeat(25) {
                    val root = automation.rootInActiveWindow
                    val nodes = buildList {
                        fun visit(node: AccessibilityNodeInfo) {
                            if (node.isVisibleToUser) add(node)
                            for (index in 0 until node.childCount) node.getChild(index)?.let(::visit)
                        }
                        root?.let(::visit)
                    }
                    fun matchesLabel(node: AccessibilityNodeInfo): Boolean =
                        listOf(node.text, node.contentDescription).any { value ->
                            value?.toString()?.lineSequence()?.any { it.trim() == label } == true
                        }
                    nodes.asReversed().firstOrNull(::matchesLabel)?.let { return it }
                    visible = nodes.mapNotNull { node ->
                        val value = node.text ?: node.contentDescription ?: return@mapNotNull null
                        node.packageName.toString() to value.toString()
                    }
                    if (scroll && it % 3 == 2) {
                        val decor = activity.window.decorView
                        val middle = decor.width / 2
                        shell("input swipe $middle ${decor.height * 4 / 5} $middle ${decor.height / 4} 240")
                    }
                    SystemClock.sleep(200)
                }
                error("Cannot find $label. Visible labels: ${visible.filter { it.first == activity.packageName }.take(20)}")
            }

            fun open(label: String, scroll: Boolean = false) {
                val bounds = android.graphics.Rect()
                find(label, scroll).getBoundsInScreen(bounds)
                shell("input tap ${bounds.centerX()} ${bounds.centerY()}")
            }

            val decor = activity.window.decorView
            fun renderedRanges(): List<Float> {
                val canvas = PreviewRecordingCanvas(decor.width.toFloat())
                decor.draw(canvas)
                return canvas.rangeWidths
            }

            val collection = activity.getString(R.string.trigger_collection_title)
            open(collection, scroll = true)
            open(row, scroll = true)
            val appearance = activity.getString(editorTitle)
            find(appearance, scroll = true)
            if (touchEdge) {
                open(appearance)
                find(activity.getString(editorContent))
                val widths = mutableListOf<List<Float>>()
                var start = emptyList<Float>()
                fun allRanges(): List<Float> = WindowInspector.getGlobalWindowViews().flatMap { window ->
                    val canvas = PreviewRecordingCanvas(window.width.toFloat(), includeNativeStyle = true)
                    window.draw(canvas)
                    canvas.rangeWidths
                }.sorted()
                val callback = object : Choreographer.FrameCallback {
                    override fun doFrame(frameTimeNanos: Long) {
                        widths.add(allRanges())
                        Choreographer.getInstance().postFrameCallback(this)
                    }
                }
                instrumentation.runOnMainSync {
                    start = allRanges()
                    assertTrue("Design preview must be visible before the edge touch", start.isNotEmpty())
                    Choreographer.getInstance().postFrameCallback(callback)
                }
                try {
                    val trigger = checkNotNull(entries[index].right)
                    val edge = decor.width - 2
                    val position = (decor.height * (trigger.topFraction + trigger.heightFraction / 2f)).toInt()
                    shell("input tap $edge $position")
                    shell("input swipe $edge $position $edge $position 450")
                    find(activity.getString(editorContent))
                } finally {
                    instrumentation.runOnMainSync { Choreographer.getInstance().removeFrameCallback(callback) }
                }
                assertTrue("The edge touch produced no frames", widths.isNotEmpty())
                assertEquals("Same ranges while touching the edge", setOf(start), widths.toSet())
                return
            }
            var initial = emptyList<Float>()
            instrumentation.runOnMainSync { initial = renderedRanges() }
            assertEquals("Trigger settings", 1, initial.size)
            val frames = mutableListOf<List<Float>>()
            val sample = ViewTreeObserver.OnPreDrawListener {
                frames.add(renderedRanges())
                true
            }
            instrumentation.runOnMainSync { decor.viewTreeObserver.addOnPreDrawListener(sample) }
            try {
                open(appearance)
                find(activity.getString(editorContent))
                instrumentation.runOnMainSync {
                    assertEquals("Same range in the actual editor", initial, renderedRanges())
                }
                shell("input keyevent KEYCODE_BACK")
                find(appearance, scroll = true)
                instrumentation.runOnMainSync {
                    assertEquals("Same range after returning", initial, renderedRanges())
                    assertTrue("The transition produced no frames", frames.isNotEmpty())
                    assertEquals("Same range in every transition frame", setOf(initial), frames.toSet())
                }
            } finally {
                instrumentation.runOnMainSync { decor.viewTreeObserver.removeOnPreDrawListener(sample) }
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    @Test
    fun layoutPreviewStaysVisibleWhileOpeningAndLeavingLayoutEditor() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        val component = "${instrumentation.targetContext.packageName}/${MainActivity::class.java.name}"
        val launch = ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand("am start -W -n $component"),
        ).bufferedReader().use { it.readText() }
        val startedActivity = instrumentation.waitForMonitorWithTimeout(monitor, 5_000)
        assertNotNull(launch, startedActivity)
        val activity = startedActivity as MainActivity
        try {
            val handle = TriggerHandle.default().copy(
                id = "reserved",
                passthrough = true,
                edgeWidthDp = 20f,
                alignOppositeSide = false,
                alignOppositeDesign = true,
            )
            val opposite = handle.copy(edgeWidthDp = 28f)
            val settings = AppSettings(
                freeWindow = FreeWindowSettings(freeWindowModeId = FreeWindowMode.STANDARD.id),
                edgeTrigger = EdgeTriggerSettings(
                    leftTriggerHandles = listOf(opposite),
                    rightTriggerHandles = listOf(handle),
                ),
            )
            val details = AppNavKey.HomeSideGestures("RIGHT", handle.id)
            val layout = AppNavKey.HomeSideGesturesAppearance(details.side, details.handleId)
            val designEditor = AppNavKey.HomeSideGesturesDesign(details.side, details.handleId)
            val previewColor = Color.MAGENTA
            val design = handle.design.copy(
                kind = TriggerDesignKind.CONFIGURABLE_RECTANGLE,
                backgroundColor = previewColor,
                sizeDp = 12f,
            )
            val frame = mutableStateOf(PreviewFrame(details, details))
            var applied = CountDownLatch(1)
            instrumentation.runOnMainSync {
                activity.setContent {
                    val current = frame.value
                    FocusedSideTriggerLayoutPreviewHost(current.destination.focusedTriggerPreviewKey()) {
                        current.page?.let { page ->
                            key(page) {
                                FocusedSideTriggerLayoutPreview(
                                    if (current.aligned) settings else settings.copy(
                                        edgeTrigger = settings.edgeTrigger.copy(
                                            rightTriggerHandles = listOf(handle.copy(alignOppositeDesign = false)),
                                        ),
                                    ),
                                    PanelSide.RIGHT,
                                    handle.id,
                                    draftWidth = current.width,
                                    designDraft = design.takeIf { page == designEditor },
                                    showRange = current.showRange,
                                )
                            }
                        }
                    }
                    SideEffect { applied.countDown() }
                }
            }

            fun assertRange(
                expectedWidth: Float?,
                label: String,
                expectedDesignWidth: Float? = null,
                expectedLeftWidth: Float? = null,
            ) {
                assertTrue("Composition did not apply: $label", applied.await(5, TimeUnit.SECONDS))
                val actual = mutableListOf<Float>()
                var density = 0f
                instrumentation.runOnMainSync {
                    val decor = activity.window.decorView
                    assertTrue(decor.width > 0)
                    density = decor.resources.displayMetrics.density
                    val canvas = PreviewRecordingCanvas(decor.width.toFloat())
                    decor.draw(canvas)
                    actual.addAll(canvas.rangeWidths)
                    assertEquals(label, if (expectedLeftWidth == null) 0 else 1, canvas.leftRangeWidths.size)
                    if (expectedLeftWidth != null) {
                        assertEquals(label, expectedLeftWidth, canvas.leftRangeWidths.single() / density, 0.01f)
                    }
                    val designWidth = canvas.pathWidths[previewColor]
                    if (expectedDesignWidth == null) assertEquals(label, null, designWidth)
                    else assertEquals(label, expectedDesignWidth, checkNotNull(designWidth) / density, 0.5f)
                }
                assertEquals(label, if (expectedWidth == null) 0 else 1, actual.size)
                if (expectedWidth != null) assertEquals(label, expectedWidth, actual.single() / density, 0.01f)
            }

            fun show(
                destination: AppNavKey,
                page: AppNavKey?,
                width: Float? = null,
                aligned: Boolean = true,
                showRange: Boolean = true,
            ) {
                instrumentation.runOnMainSync {
                    applied = CountDownLatch(1)
                    frame.value = PreviewFrame(destination, page, width, aligned, showRange)
                }
            }

            assertRange(20f, "trigger settings")
            show(layout, null)
            assertRange(20f, "detail disposed before the layout editor renders")
            show(layout, layout, 30f)
            assertRange(30f, "layout editor width draft")
            show(details, null)
            assertRange(30f, "layout editor disposed before trigger settings render")
            show(details, details)
            assertRange(20f, "trigger settings restored")
            show(designEditor, null)
            assertRange(20f, "settings disposed before the design editor renders")
            show(designEditor, designEditor)
            assertRange(20f, "aligned design shows the range on both edges", 12f, expectedLeftWidth = 28f)
            show(designEditor, designEditor, aligned = false)
            assertRange(20f, "unaligned design shows only the selected range", 12f)
            show(designEditor, designEditor, showRange = false)
            assertRange(null, "collection preview owns both ranges", 12f)
            show(details, details)
            assertRange(20f, "design preview cleared after returning")
            show(AppNavKey.HomeTriggerCollection, null)
            assertRange(null, "left the trigger settings")
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private data class PreviewFrame(
        val destination: AppNavKey,
        val page: AppNavKey?,
        val width: Float? = null,
        val aligned: Boolean = true,
        val showRange: Boolean = true,
    )

    private class PreviewRecordingCanvas(
        private val rightEdge: Float,
        private val includeNativeStyle: Boolean = false,
    ) : Canvas() {
        val rangeWidths = mutableListOf<Float>()
        val leftRangeWidths = mutableListOf<Float>()
        val pathWidths = mutableMapOf<Int, Float>()

        override fun drawPath(path: Path, paint: Paint) {
            val bounds = RectF()
            path.computeBounds(bounds, true)
            pathWidths[paint.color] = bounds.width()
        }

        override fun drawRoundRect(rect: RectF, radiusX: Float, radiusY: Float, paint: Paint) {
            val previewFill = paint.alpha == 72 || (includeNativeStyle && paint.color == Color.argb(36, 33, 150, 243))
            if (paint.style == Paint.Style.FILL && previewFill) {
                if (rect.right == rightEdge) rangeWidths.add(rect.width())
                if (rect.left == 0f) leftRangeWidths.add(rect.width())
            }
        }
    }
}
