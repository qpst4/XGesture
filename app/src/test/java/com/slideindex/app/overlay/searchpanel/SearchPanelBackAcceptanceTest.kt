package com.slideindex.app.overlay.searchpanel

import android.content.Context
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.window.OnBackInvokedCallback
import com.slideindex.app.util.PredictiveBackHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowWindowOnBackInvokedDispatcher
import java.time.Duration

/** 验收真实搜索浮窗的系统返回链；仅省去搜索结果内容及入口权限检查。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SearchPanelBackAcceptanceTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @After
    fun closeWindow() {
        SearchPanelSessionState.onBackPressed = null
        SearchPanelSessionState.persistBeforeDismiss = null
        SearchPanelSessionState.onPanelClosed = null
        SearchPanelOverlayWindow.releaseWarmUp()
        idle()
    }

    @Test
    fun systemBack_closesSearchPanel_andPersistsQueryOnce() {
        val frame = openPanel()
        var persisted = 0
        var closed = 0
        SearchPanelSessionState.persistBeforeDismiss = { persisted++ }
        SearchPanelSessionState.onPanelClosed = { closed++ }

        backCallback(frame).onBackInvoked()
        idle()

        assertFalse(SearchPanelOverlayWindow.isShowing)
        assertNull(SearchPanelOverlayWindow.dragHostView)
        assertEquals(1, persisted)
        assertEquals(1, closed)
    }

    @Test
    fun systemBack_closesPreviewFirst_thenClosesPanel() {
        val frame = openPanel()
        var previewShowing = true
        SearchPanelSessionState.onBackPressed = {
            if (previewShowing) { previewShowing = false; true } else false
        }

        backCallback(frame).onBackInvoked()
        idle()
        assertFalse(previewShowing)
        assertTrue(SearchPanelOverlayWindow.isShowing)

        backCallback(frame).onBackInvoked()
        idle()
        assertFalse(SearchPanelOverlayWindow.isShowing)
    }

    @Test
    fun reopeningPanel_backClosesTheNewWindow() {
        backCallback(openPanel()).onBackInvoked()
        idle()
        assertFalse(SearchPanelOverlayWindow.isShowing)

        backCallback(openPanel()).onBackInvoked()
        idle()
        assertFalse(SearchPanelOverlayWindow.isShowing)
    }

    @Test
    @Config(sdk = [31])
    fun legacyBackKey_stillClosesPanel() {
        val frame = openPanel()
        assertTrue(frame.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)))
        assertTrue(frame.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK)))
        idle()
        assertFalse(SearchPanelOverlayWindow.isShowing)
    }

    @Test
    @Config(sdk = [31])
    fun legacyBackKey_stillClosesPanel_whenFocusedContentConsumesKeys() {
        val frame = openPanel() as ViewGroup
        val input = View(context).apply {
            isFocusableInTouchMode = true
            setOnKeyListener { _, _, _ -> true }
        }
        frame.addView(input)
        input.layout(0, 0, 100, 100)
        assertTrue(input.requestFocus())

        frame.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
        frame.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
        idle()

        assertFalse("输入控件消费按键时，搜索面板的返回仍应生效", SearchPanelOverlayWindow.isShowing)
    }

    @Test
    fun predictiveBackDisabled_usesLegacyKeyRoute() {
        val frame = openPanel(predictive = false)
        assertEquals(PredictiveBackHelper.AppBackDispatch.INJECTED_KEY, PredictiveBackHelper.resolveAppBackDispatch(context))
        frame.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
        frame.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
        idle()
        assertFalse(SearchPanelOverlayWindow.isShowing)
    }

    @Test
    @Config(sdk = [31])
    fun ordinaryKeys_stillReachFocusedContent() {
        val frame = openPanel() as ViewGroup
        var keysReceived = 0
        val input = View(context).apply {
            isFocusableInTouchMode = true
            setOnKeyListener { _, _, _ -> keysReceived++; true }
        }
        frame.addView(input)
        input.layout(0, 0, 100, 100)
        assertTrue(input.requestFocus())

        frame.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A))

        assertEquals(1, keysReceived)
        assertTrue(SearchPanelOverlayWindow.isShowing)
    }

    @Test
    fun back_closesKeyboard_thenPreview_thenPanel() {
        val frame = openPanel()
        val imm = context.getSystemService(InputMethodManager::class.java)
        // 浮窗 Insets 不报 IME 时，可见区域明显小于根窗口是键盘弹出的兼容判据。
        val width = context.resources.displayMetrics.widthPixels
        val height = context.resources.displayMetrics.heightPixels
        frame.layout(0, 0, width, height * 2)
        imm.showSoftInput(frame, 0)
        assertTrue(shadowOf(imm).isSoftInputVisible)
        var previewShowing = true
        SearchPanelSessionState.onBackPressed = {
            if (previewShowing) { previewShowing = false; true } else false
        }

        backCallback(frame).onBackInvoked()
        idle()
        assertFalse(shadowOf(imm).isSoftInputVisible)
        assertTrue(previewShowing)
        assertTrue(SearchPanelOverlayWindow.isShowing)

        frame.layout(0, 0, width, height)
        backCallback(frame).onBackInvoked()
        idle()
        assertFalse(previewShowing)
        assertTrue(SearchPanelOverlayWindow.isShowing)

        backCallback(frame).onBackInvoked()
        idle()
        assertFalse(SearchPanelOverlayWindow.isShowing)
    }

    private fun openPanel(predictive: Boolean = true): View {
        PredictiveBackHelper.applyEnabled(context.applicationInfo, predictive)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            ShadowWindowOnBackInvokedDispatcher::class.java.getDeclaredMethod("setEnablePredictiveBack", Boolean::class.javaPrimitiveType)
                .apply { isAccessible = true }.invoke(null, true)
        }
        SearchPanelOverlayWindow::class.java.getDeclaredMethod("ensureWindow", Context::class.java)
            .apply { isAccessible = true }.invoke(SearchPanelOverlayWindow, context)
        SearchPanelOverlayWindow::class.java.getDeclaredMethod("applyPanelShellActive")
            .apply { isAccessible = true }.invoke(SearchPanelOverlayWindow)
        idle()
        assertTrue("验收前搜索浮窗必须显示", SearchPanelOverlayWindow.isShowing)
        return SearchPanelOverlayWindow.dragHostView!!
    }

    private fun backCallback(frame: View): OnBackInvokedCallback {
        val dispatcher = frame.findOnBackInvokedDispatcher()
        assertNotNull("窗口应具有系统返回分发器", dispatcher)
        val callback = dispatcher!!.javaClass.getDeclaredMethod("getTopCallback").apply { isAccessible = true }
            .invoke(dispatcher) as? OnBackInvokedCallback
        assertNotNull("搜索浮窗必须认领系统返回回调", callback)
        return callback!!
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(180))
}
