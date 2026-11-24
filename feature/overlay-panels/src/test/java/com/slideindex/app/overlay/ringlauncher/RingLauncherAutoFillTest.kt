package com.slideindex.app.overlay.ringlauncher

import com.slideindex.app.launcher.QuickLauncherItem
import com.slideindex.app.launcher.QuickLauncherItemType
import com.slideindex.app.settings.FvRingLauncherSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RingLauncherAutoFillTest {
    @Test
    fun explicitNoneDoesNotConsumeTheNextDefaultApp() {
        val none = QuickLauncherItem(QuickLauncherItemType.APP, "", "")
        val settings = FvRingLauncherSettings(slots = mapOf(0 to none))
        val defaults = ArrayDeque(listOf("first", "second"))
        fun select(index: Int, explicit: String? = null, enabled: Boolean = true) =
            selectRingLauncherSlot(settings, index, explicit, enabled) { defaults.removeFirstOrNull() }

        assertNull(select(0))
        assertEquals("first", select(1))
        assertEquals("pinned", select(2, explicit = "pinned"))
        assertNull(select(3, enabled = false))
        assertEquals("second", select(4))
    }
}
