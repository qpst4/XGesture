package com.slideindex.app.overlay

import android.content.ComponentName
import android.content.Context
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.FloatBallSide
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowWindowManagerImpl
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RegionalPickOverlayWindowTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val windowManager: WindowManager
        get() = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var initialHostGeneration = 0

    @Before
    fun enableOverlayHost() {
        val serviceClass = SlideIndexAccessibilityService::class.java
        initialHostGeneration = SlideIndexAccessibilityService.overlayHostGeneration()
        OverlayHostRuntime.accessibilityServiceClass = serviceClass
        OverlayHostRuntime.accessibilityHostContext = { context }
        OverlayHostRuntime.isAccessibilityServiceEnabled = { true }
        Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ComponentName(context, serviceClass).flattenToString(),
        )
    }

    @After
    fun closeOverlay() {
        RegionalPickOverlay.dismiss()
        cursorWindows().forEach(windowManager::removeViewImmediate)
        shadowOf(Looper.getMainLooper()).idle()
        OverlayHostRuntime.accessibilityServiceClass = null
        OverlayHostRuntime.accessibilityHostContext = null
        OverlayHostRuntime.isAccessibilityServiceEnabled = null
        setHostGeneration(initialHostGeneration)
    }

    @Test
    fun nextEdgeDragRestoresCursorAfterWindowIsRemoved() {
        startEdgeDrag()
        val original = visibleCursorWindow()

        RegionalPickOverlay.dismiss()
        assertTrue("结束拖动后应保留隐藏的窗口", original in cursorWindows())
        windowManager.removeViewImmediate(original)
        assertFalse(original.isAttachedToWindow)
        assertTrue(cursorWindows().isEmpty())

        startEdgeDrag()

        visibleCursorWindow()
    }

    @Test
    fun reconnectRestoresCursorEvenIfObsoleteViewStillReportsAttached() {
        startEdgeDrag()
        val original = visibleCursorWindow()
        RegionalPickOverlay.dismiss()
        setHostGeneration(initialHostGeneration + 1)
        assertTrue("旧 View 在宿主重连后仍可能被标记为已挂载", original.isAttachedToWindow)

        startEdgeDrag()

        assertNotSame(original, visibleCursorWindow())
        assertFalse("旧宿主的 View 应从窗口管理器卸载", original.isAttachedToWindow)
    }

    @Test
    fun consecutiveDragsReuseHealthyWindow() {
        startEdgeDrag()
        val original = visibleCursorWindow()
        RegionalPickOverlay.dismiss()

        startEdgeDrag()

        assertSame(original, visibleCursorWindow())
    }

    private fun visibleCursorWindow(): View {
        assertTrue("拖动应进入区域截图／取词会话", RegionalPickOverlay.isActive)
        val window = requireNotNull(cursorWindows().singleOrNull()) { "拖动应挂载一个光标浮层" }
        assertTrue("光标应可见", window.isAttachedToWindow && cursorIn(window)?.isShown == true)
        return window
    }

    private fun setHostGeneration(generation: Int) =
        ReflectionHelpers.setStaticField(SlideIndexAccessibilityService::class.java, "instanceGeneration", generation)

    private fun startEdgeDrag() {
        RegionalPickOverlay.launchFromEdge(
            context = context,
            appSettings = AppSettings(),
            gestureStartRawY = 350f,
            edgeSide = FloatBallSide.LEFT,
            rawX = 120f,
            rawY = 240f,
        )
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
    }

    private fun cursorWindows(): List<View> =
        Shadow.extract<ShadowWindowManagerImpl>(windowManager).views.filter { cursorIn(it) != null }

    private fun cursorIn(window: View): FloatBallCursorPreviewView? =
        (window as? FrameLayout)?.getChildAt(0) as? FloatBallCursorPreviewView
}
