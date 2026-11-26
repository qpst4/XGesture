package com.slideindex.app.overlay.floatnav

import android.view.View
import com.slideindex.app.R
import com.slideindex.app.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class FloatNavViewCollapseTest {

    private val listener = object : FloatNavView.Listener {
        override fun onKey(key: FloatNavKey) = Unit
        override fun onClose() = Unit
        override fun onGripClick() = Unit
        override fun onDragStart() = Unit
        override fun onDragBy(dx: Float, dy: Float) = Unit
        override fun onDragEnd() = Unit
    }

    @Test
    fun collapseHidesNavigationKeysAndShrinksToHandle() {
        val view = FloatNavView(
            context = RuntimeEnvironment.getApplication(),
            appSettings = AppSettings(),
            expandRight = true,
            listener = listener,
        )
        val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.measure(unspecified, unspecified)
        val expandedWidth = view.measuredWidth
        assertEquals(View.VISIBLE, view.getChildAt(1).visibility)

        view.setCollapsed(true)
        view.measure(unspecified, unspecified)

        assertTrue(view.isCollapsed)
        assertTrue(view.measuredWidth < expandedWidth)
        assertEquals(View.VISIBLE, view.getChildAt(0).visibility)
        assertEquals(View.GONE, view.getChildAt(1).visibility)

        view.setCollapsed(false)
        view.measure(unspecified, unspecified)

        assertFalse(view.isCollapsed)
        assertEquals(expandedWidth, view.measuredWidth)
        assertEquals(View.VISIBLE, view.getChildAt(1).visibility)
    }

    @Test
    fun expandLeft_putsGripAtRightEnd() {
        val context = RuntimeEnvironment.getApplication()
        val view = FloatNavView(
            context = context,
            appSettings = AppSettings(),
            expandRight = false,
            listener = listener,
        )
        val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.measure(unspecified, unspecified)

        assertEquals(context.getString(R.string.float_nav_close), view.getChildAt(0).contentDescription)
        assertEquals(
            context.getString(R.string.float_nav_drag),
            view.getChildAt(view.childCount - 1).contentDescription,
        )
    }

    @Test
    fun setExpandRight_reordersLiveView() {
        val context = RuntimeEnvironment.getApplication()
        val view = FloatNavView(
            context = context,
            appSettings = AppSettings(),
            expandRight = true,
            listener = listener,
        )
        val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.measure(unspecified, unspecified)
        assertEquals(context.getString(R.string.float_nav_drag), view.getChildAt(0).contentDescription)

        view.setExpandRight(false)
        view.measure(unspecified, unspecified)

        assertEquals(context.getString(R.string.float_nav_close), view.getChildAt(0).contentDescription)
        assertEquals(
            context.getString(R.string.float_nav_drag),
            view.getChildAt(view.childCount - 1).contentDescription,
        )
    }
}
