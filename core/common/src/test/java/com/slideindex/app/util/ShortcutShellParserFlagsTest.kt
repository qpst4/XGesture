package com.slideindex.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class ShortcutShellParserFlagsTest {

    @Test
    fun mapsPersistedShortcutXmlFlagsToKinds() {
        val xml = """
            <shortcuts>
              <package name="com.example.xml">
                <shortcut id="manifest" flags="4" title="Manifest" />
                <shortcut id="dynamic" flags="1" title="Dynamic" />
                <shortcut id="pinned" flags="2" title="Pinned" />
                <shortcut id="both" flags="3" title="Both" />
              </package>
            </shortcuts>
        """.trimIndent()

        val parsed = ShortcutSystemXmlParser.parseShortcutsXml(xml)

        assertEquals(
            setOf(ShortcutKind.STATIC),
            parsed.getValue("com.example.xml").first { it.id == "manifest" }.kinds,
        )
        assertEquals(
            setOf(ShortcutKind.DYNAMIC),
            parsed.getValue("com.example.xml").first { it.id == "dynamic" }.kinds,
        )
        assertEquals(
            setOf(ShortcutKind.PINNED),
            parsed.getValue("com.example.xml").first { it.id == "pinned" }.kinds,
        )
        assertEquals(
            setOf(ShortcutKind.DYNAMIC, ShortcutKind.PINNED),
            parsed.getValue("com.example.xml").first { it.id == "both" }.kinds,
        )
    }

    @Test
    fun mapsAndroidShortcutInfoFlagsToKinds() {
        val parsed = ShortcutShellParser.parseAllPackagesEntries(
            listOf(
                block("manifest", "0x1a4", "com.example.manifest", "Manifest"),
                block("dynamic", "0x1", "com.example.dynamic", "Dynamic"),
                block("pinned", "0x2", "com.example.pinned", "Pinned"),
                block("both", "0x3", "com.example.both", "Both"),
            ).joinToString("\n"),
        )

        assertEquals(
            setOf(ShortcutKind.STATIC),
            parsed.getValue("com.example.manifest").single().kinds,
        )
        assertEquals(
            setOf(ShortcutKind.DYNAMIC),
            parsed.getValue("com.example.dynamic").single().kinds,
        )
        assertEquals(
            setOf(ShortcutKind.PINNED),
            parsed.getValue("com.example.pinned").single().kinds,
        )
        assertEquals(
            setOf(ShortcutKind.DYNAMIC, ShortcutKind.PINNED),
            parsed.getValue("com.example.both").single().kinds,
        )
        assertEquals(
            ".MainActivity",
            parsed.getValue("com.example.manifest").single().targetComponent,
        )
        assertTrue(parsed.containsKey("com.example.pinned"))
    }

    private fun block(id: String, flags: String, packageName: String, label: String): String =
        """
        ShortcutInfo {id=$id, flags=$flags [ImManIc-rStr]
          packageName=$packageName
          activity=ComponentInfo{$packageName/.MainActivity}
          shortLabel=$label, resId=0[null]
          longLabel=$label, resId=0[null]
          disabledMessage=null, resId=0[null]
          disabledReason=[Not disabled]
          categories=null
          persons=null
          icon=null
          rank=0, timestamp=0
          intents=[Intent { act=android.intent.action.VIEW }/null]
          extras=null
          iconRes=0[null], bitmapPath=null, iconUri=null}
        """.trimIndent()
}
