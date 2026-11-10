package com.slideindex.app.ui.picker

import com.slideindex.app.data.AppInfo
import com.slideindex.app.overlay.TaskSwitcherMenuItem
import com.slideindex.app.overlay.TaskSwitcherMenuItemType
import com.slideindex.app.util.AppShortcutLoader
import com.slideindex.app.util.ShortcutKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutCatalogRedactionTest {

    @Test
    fun plan_keepsCatalogUntouchedWithoutRedaction() {
        val groups = listOf(
            group("org.breezyweather", shortcut("weather", "Weather")),
            group("com.samsung.android.calendar", shortcut("new_event", "New event")),
        )

        val plan = ShortcutCatalogRedaction.plan(groups)

        assertEquals(0, plan.redactedAppCount)
        assertFalse(plan.useStatic)
        assertEquals(groups, plan.usableGroups)
        assertSame(groups[0], plan.usableGroups[0])
    }

    @Test
    fun plan_dropsRedactedEntriesPerEntry() {
        val groups = listOf(
            group("com.example.mixed", shortcut("***"), shortcut("real", "Real")),
        )

        val plan = ShortcutCatalogRedaction.plan(groups)

        assertEquals(1, plan.usableGroups.size)
        assertEquals(listOf("real"), plan.usableGroups[0].shortcuts.map { it.shortcutId })
        // 还有可用条目的 App 不算「整组脱敏」
        assertEquals(0, plan.redactedAppCount)
        assertFalse(plan.useStatic)
    }

    @Test
    fun plan_twoRedactedAppsDoNotSwitchToStatic() {
        val groups = listOf(
            group("com.example.a", shortcut("***")),
            group("com.example.b", shortcut("***")),
            group("com.example.c", shortcut("real", "Real")),
        )

        val plan = ShortcutCatalogRedaction.plan(groups)

        assertEquals(2, plan.redactedAppCount)
        assertFalse(plan.useStatic)
        assertEquals(1, plan.usableGroups.size)
    }

    @Test
    fun plan_threeRedactedAppsSwitchToStatic() {
        val groups = listOf(
            group("com.example.a", shortcut("***")),
            group("com.example.b", shortcut("***")),
            group("com.example.c", shortcut("***")),
            group("com.example.d", shortcut("real", "Real")),
        )

        val plan = ShortcutCatalogRedaction.plan(groups)

        assertEquals(3, plan.redactedAppCount)
        assertTrue(plan.useStatic)
        assertEquals(
            listOf("com.example.d"),
            plan.usableGroups.map { it.app.packageName },
        )
    }

    @Test
    fun plan_everyGroupRedactedSwitchesEvenBelowThreshold() {
        val groups = listOf(
            group("com.example.a", shortcut("***")),
            group("com.example.b", shortcut("***")),
        )

        val plan = ShortcutCatalogRedaction.plan(groups)

        assertEquals(2, plan.redactedAppCount)
        assertTrue(plan.useStatic)
        assertTrue(plan.usableGroups.isEmpty())
    }

    @Test
    fun plan_emptyCatalogDoesNotSwitch() {
        val plan = ShortcutCatalogRedaction.plan(emptyList())

        assertEquals(0, plan.redactedAppCount)
        assertFalse(plan.useStatic)
        assertTrue(plan.usableGroups.isEmpty())
    }

    @Test
    fun isRedactedId_onlyMatchesFrameworkMarkerPrefix() {
        assertTrue(ShortcutCatalogRedaction.isRedactedId("***"))
        assertTrue(ShortcutCatalogRedaction.isRedactedId("***123"))
        assertTrue(ShortcutCatalogRedaction.isRedactedId("  ***  "))
        assertFalse(ShortcutCatalogRedaction.isRedactedId("a***"))
        assertFalse(ShortcutCatalogRedaction.isRedactedId("weather"))
        assertFalse(ShortcutCatalogRedaction.isRedactedId(""))
        assertFalse(ShortcutCatalogRedaction.isRedactedId(null))
    }

    private fun app(packageName: String): AppInfo = AppInfo(
        packageName = packageName,
        label = packageName.substringAfterLast('.'),
        letter = 'x',
        pinyinKey = packageName,
    )

    private fun shortcut(id: String?, label: String = id.orEmpty()): TaskSwitcherMenuItem =
        TaskSwitcherMenuItem(
            label = label,
            type = TaskSwitcherMenuItemType.SHORTCUT,
            shortcutId = id,
            kind = ShortcutKind.DYNAMIC,
            isServiceShortcut = true,
        )

    private fun group(packageName: String, vararg shortcuts: TaskSwitcherMenuItem): AppShortcutLoader.AppShortcutGroup =
        AppShortcutLoader.AppShortcutGroup(app(packageName), shortcuts.toList())
}
