package com.slideindex.app.overlay.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 默认分享目标的键解析与容错：坏数据（旧版本 / 手改的备份）不能把面板打不开。
 */
class ShareDefaultTargetTest {

    @Test
    fun fromKeyParsesPackageAndActivity() {
        val target = ShareDefaultTarget.fromKey("com.tencent.mobileqq/com.tencent.mobileqq.activity.JumpActivity")

        assertEquals("com.tencent.mobileqq", target?.packageName)
        assertEquals("com.tencent.mobileqq.activity.JumpActivity", target?.activityClassName)
        assertEquals("com.tencent.mobileqq/com.tencent.mobileqq.activity.JumpActivity", target?.key)
    }

    @Test
    fun fromKeyRejectsMalformedValues() {
        assertNull(ShareDefaultTarget.fromKey(null))
        assertNull(ShareDefaultTarget.fromKey(""))
        assertNull(ShareDefaultTarget.fromKey("   "))
        assertNull(ShareDefaultTarget.fromKey("com.example.app"))
        assertNull(ShareDefaultTarget.fromKey("/com.example.app.Main"))
        assertNull(ShareDefaultTarget.fromKey("com.example.app/"))
        assertNull(ShareDefaultTarget.fromKey("/"))
    }

    @Test
    fun onlyTextAndImageSupportDefaults() {
        assertTrue(ShareSheetDefaults.supports(ShareSheetKind.TEXT))
        assertTrue(ShareSheetDefaults.supports(ShareSheetKind.IMAGE))
        // 文件分享没有角标入口，不允许设默认（否则会存一个永远用不到的值）。
        assertFalse(ShareSheetDefaults.supports(ShareSheetKind.FILE))
    }

    @Test
    fun decodeAcceptsStoredMapAndRejectsGarbage() {
        assertEquals(
            mapOf("TEXT" to "com.a/.A", "IMAGE" to "com.b/.B"),
            ShareSheetDefaults.decode("""{"TEXT":"com.a/.A","IMAGE":"com.b/.B"}"""),
        )
        assertEquals(emptyMap<String, String>(), ShareSheetDefaults.decode("{}"))
        assertNull(ShareSheetDefaults.decode(null))
        assertNull(ShareSheetDefaults.decode(""))
        assertNull(ShareSheetDefaults.decode("not json"))
        // 旧格式（钉住列表的 JSON 数组）不是默认目标，必须当坏数据处理。
        assertNull(ShareSheetDefaults.decode("""["com.a/.A"]"""))
    }
}
