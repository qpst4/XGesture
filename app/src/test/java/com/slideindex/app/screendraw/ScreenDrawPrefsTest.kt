package com.slideindex.app.screendraw

import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class ScreenDrawPrefsTest {

    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    @Before
    fun clear() {
        context.getSharedPreferences(ScreenDrawPrefs.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @After
    fun tearDown() {
        context.getSharedPreferences(ScreenDrawPrefs.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun defaultOrientationIsHorizontal() {
        assertEquals(
            ScreenDrawOrientation.HORIZONTAL,
            ScreenDrawPrefs.resolveInitialOrientation(context),
        )
    }

    @Test
    fun fixedVerticalWins() {
        ScreenDrawPrefs.writeInitialOrientation(
            context,
            ScreenDrawPrefs.InitialOrientation.VERTICAL,
        )
        ScreenDrawPrefs.writeLastOrientation(context, ScreenDrawOrientation.HORIZONTAL)
        assertEquals(
            ScreenDrawOrientation.VERTICAL,
            ScreenDrawPrefs.resolveInitialOrientation(context),
        )
    }

    @Test
    fun memoryUsesLastOrientation() {
        ScreenDrawPrefs.writeInitialOrientation(
            context,
            ScreenDrawPrefs.InitialOrientation.MEMORY,
        )
        ScreenDrawPrefs.writeLastOrientation(context, ScreenDrawOrientation.VERTICAL)
        assertEquals(
            ScreenDrawOrientation.VERTICAL,
            ScreenDrawPrefs.resolveInitialOrientation(context),
        )
    }
}
