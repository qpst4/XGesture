package com.slideindex.app.activity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityShortcutParamsTest {

    private fun component(label: String = "Scanner") = ActivityShortcut.component(
        label = label,
        packageName = "com.example",
        activityClassName = "com.example.ScannerActivity",
    )

    @Test
    fun splitComponent_acceptsDumpsysStyleComponent() {
        assertEquals(
            "com.example" to "com.example.ScannerActivity",
            ActivityShortcutParams.splitComponent("com.example/com.example.ScannerActivity"),
        )
    }

    @Test
    fun splitComponent_rejectsPlainPackageAndMalformedInput() {
        assertNull(ActivityShortcutParams.splitComponent("com.example"))
        assertNull(ActivityShortcutParams.splitComponent("com.example/"))
        assertNull(ActivityShortcutParams.splitComponent("/com.example.ScannerActivity"))
        assertNull(ActivityShortcutParams.splitComponent("com.example/com.example.ScannerActivity/Extra"))
        assertNull(ActivityShortcutParams.splitComponent("com.example/com.example ScannerActivity"))
    }

    @Test
    fun normalizeClassName_expandsRelativeNameOnly() {
        assertEquals(
            "com.example.ScannerActivity",
            ActivityShortcutParams.normalizeClassName("com.example", ".ScannerActivity"),
        )
        assertEquals(
            "com.example.ScannerActivity",
            ActivityShortcutParams.normalizeClassName("com.example", " com.example.ScannerActivity "),
        )
    }

    @Test
    fun stripControlChars_removesCodecSeparators() {
        assertEquals("abcde", ActivityShortcutParams.stripControlChars("a\u001Cb\u001Dc\u001Ed\u001Fe"))
        assertEquals("ab", ActivityShortcutParams.stripControlChars("a\u001Eb"))
    }

    @Test
    fun parseIntentUris_trimsDropsBlanksAndDuplicates() {
        assertEquals(
            listOf("intent:#Intent;end", "https://example.com"),
            ActivityShortcutParams.parseIntentUris(
                " intent:#Intent;end \n\n https://example.com \n intent:#Intent;end ",
            ),
        )
    }

    @Test
    fun build_componentClearsOtherKindFields() {
        val updated = ActivityShortcutParams.build(
            base = component(),
            label = " Scan ",
            packageName = " com.example ",
            activityClassName = ".ScannerActivity",
            shortcutId = "stale",
            shellCommand = "stale",
            intentUris = listOf("stale"),
            isShell = false,
        )

        assertEquals("Scan", updated.label)
        assertEquals("com.example", updated.packageName)
        assertEquals("com.example.ScannerActivity", updated.activityClassName)
        assertEquals("", updated.shortcutId)
        assertEquals(emptyList<String>(), updated.intentUris)
        assertEquals(ActivityShortcutKind.COMPONENT, updated.kind)
    }

    @Test
    fun build_dynamicClearsStaleComponentFields() {
        val base = ActivityShortcut(
            label = "Pay",
            packageName = "com.example",
            activityClassName = "com.example.StaleActivity",
            shortcutId = "pay",
        )

        val updated = ActivityShortcutParams.build(
            base = base,
            label = "Pay code",
            packageName = "com.example",
            activityClassName = "ignored",
            shortcutId = "pay_code",
            shellCommand = "ignored",
            intentUris = listOf("ignored"),
            isShell = false,
        )

        assertEquals("Pay code", updated.label)
        assertEquals("", updated.activityClassName)
        assertEquals("pay_code", updated.shortcutId)
        assertEquals(emptyList<String>(), updated.intentUris)
        assertEquals(ActivityShortcutKind.DYNAMIC, updated.kind)
    }

    @Test
    fun build_shellRewritesUriAndKeepsCommandId() {
        val base = ActivityShortcut(
            label = "Battery",
            packageName = ActivityShortcutShellSupport.HOST_PACKAGE,
            shortcutId = "cmd-1",
            intentUris = listOf("cebianshell:dumpsys battery"),
        )

        val updated = ActivityShortcutParams.build(
            base = base,
            label = "Battery level",
            packageName = "ignored",
            activityClassName = "ignored",
            shortcutId = "ignored",
            shellCommand = " dumpsys battery | grep level ",
            intentUris = emptyList(),
            isShell = true,
        )

        assertEquals("Battery level", updated.label)
        assertEquals(ActivityShortcutShellSupport.HOST_PACKAGE, updated.packageName)
        assertEquals("cmd-1", updated.shortcutId)
        assertEquals("dumpsys battery | grep level", ActivityShortcutShellSupport.decodeCommand(updated.intentUris.single()))
        assertEquals(ActivityShortcutKind.INTENT, updated.kind)
    }

    @Test
    fun appShortcutBuild_keepsIntentFallbackWhenIdUnchanged() {
        val base = ActivityShortcut(
            label = "Pay",
            packageName = "com.example",
            shortcutId = "pay_code",
            intentUris = listOf("intent:#Intent;component=com.example/.PayActivity;end"),
        )
        assertTrue(base.isAppShortcut)

        val updated = ActivityShortcutParams.build(
            base = base,
            label = "Pay code",
            packageName = "com.example",
            activityClassName = "",
            shortcutId = "pay_code",
            shellCommand = "",
            intentUris = emptyList(),
            isShell = false,
        )

        assertEquals(base.intentUris, updated.intentUris)
        assertEquals("pay_code", updated.shortcutId)
        assertEquals(ActivityShortcutKind.INTENT, updated.kind)
        assertTrue(updated.isAppShortcut)
    }

    @Test
    fun appShortcutBuild_changedIdDropsStaleIntentAndBecomesDynamic() {
        val base = ActivityShortcut(
            label = "Pay",
            packageName = "com.example",
            shortcutId = "pay_code",
            intentUris = listOf("intent:#Intent;component=com.example/.PayActivity;end"),
        )

        val updated = ActivityShortcutParams.build(
            base = base,
            label = "Pay",
            packageName = "com.example",
            activityClassName = "",
            shortcutId = "pay_code_v2",
            shellCommand = "",
            intentUris = emptyList(),
            isShell = false,
        )

        assertEquals(emptyList<String>(), updated.intentUris)
        assertEquals("pay_code_v2", updated.shortcutId)
        assertEquals(ActivityShortcutKind.DYNAMIC, updated.kind)
        assertTrue(updated.isAppShortcut)
    }

    @Test
    fun isAppShortcut_excludesShellEntries() {
        val shell = ActivityShortcut(
            label = "Battery",
            packageName = ActivityShortcutShellSupport.HOST_PACKAGE,
            shortcutId = "cmd-1",
            intentUris = listOf("cebianshell:dumpsys battery"),
        )
        assertFalse(shell.isAppShortcut)
    }

    @Test
    fun build_intentClearsStaleComponentAndCommandId() {
        val base = ActivityShortcut(
            label = "Settings",
            packageName = "com.android.settings",
            activityClassName = "com.android.settings.StaleActivity",
            intentUris = listOf("intent:#Intent;action=android.settings.SETTINGS;end"),
        )

        val updated = ActivityShortcutParams.build(
            base = base,
            label = "Settings",
            packageName = "com.android.settings",
            activityClassName = "ignored",
            shortcutId = "ignored",
            shellCommand = "ignored",
            intentUris = listOf(" intent:#Intent;action=android.settings.SETTINGS;end ", ""),
            isShell = false,
        )

        assertEquals("", updated.activityClassName)
        assertEquals("", updated.shortcutId)
        assertEquals(
            listOf("intent:#Intent;action=android.settings.SETTINGS;end"),
            updated.intentUris,
        )
        assertEquals(ActivityShortcutKind.INTENT, updated.kind)
    }

    @Test
    fun duplicateOf_ignoresSelfAndMatchesIdentity() {
        val first = component()
        val shortcuts = listOf(first)

        assertSame(first, ActivityShortcutParams.duplicateOf(shortcuts, first.copy(id = "new")))
        assertNull(ActivityShortcutParams.duplicateOf(shortcuts, first))
    }

    @Test
    fun merge_inheritsIconOnlyWhenKeptHasNone() {
        val kept = component().copy(iconPath = null)
        val dropped = component().copy(iconPath = "shortcut_icons/custom-1.png")

        assertEquals("shortcut_icons/custom-1.png", ActivityShortcutParams.merge(kept, dropped).iconPath)

        val keptWithIcon = kept.copy(iconPath = "shortcut_icons/custom-2.png")
        assertEquals(
            "shortcut_icons/custom-2.png",
            ActivityShortcutParams.merge(keptWithIcon, dropped).iconPath,
        )
    }

    @Test
    fun replace_swapsOnlyMatchingId() {
        val first = component()
        val second = ActivityShortcut.dynamic("Pay", "com.example", "pay")
        val updated = first.copy(label = "New label")

        val result = ActivityShortcutParams.replace(listOf(first, second), updated)

        assertEquals("New label", result[0].label)
        assertSame(second, result[1])
    }
}
