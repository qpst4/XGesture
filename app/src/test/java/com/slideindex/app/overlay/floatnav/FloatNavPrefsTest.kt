package com.slideindex.app.overlay.floatnav

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
class FloatNavPrefsTest {

    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    @Before
    fun clear() {
        context.getSharedPreferences(FloatNavPrefs.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @After
    fun tearDown() {
        context.getSharedPreferences(FloatNavPrefs.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun defaultIsHorizontal() {
        assertEquals(FloatNavFormSetting.HORIZONTAL, FloatNavPrefs.readInitialForm(context))
        assertEquals(FloatNavForm.HORIZONTAL, FloatNavPrefs.resolveInitialForm(context))
    }

    @Test
    fun fixedVerticalWins() {
        FloatNavPrefs.writeInitialForm(context, FloatNavFormSetting.VERTICAL)
        FloatNavPrefs.writeLastForm(context, FloatNavForm.HORIZONTAL)
        assertEquals(FloatNavForm.VERTICAL, FloatNavPrefs.resolveInitialForm(context))
    }

    @Test
    fun memoryUsesLastForm() {
        FloatNavPrefs.writeInitialForm(context, FloatNavFormSetting.MEMORY)
        FloatNavPrefs.writeLastForm(context, FloatNavForm.VERTICAL)
        assertEquals(FloatNavForm.VERTICAL, FloatNavPrefs.resolveInitialForm(context))
    }
}
