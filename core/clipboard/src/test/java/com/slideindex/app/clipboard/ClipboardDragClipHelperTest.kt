package com.slideindex.app.clipboard

import android.app.Application
import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.net.Uri
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ClipboardDragClipHelperTest {

    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    @Test
    fun nonMediaStoreHostKeepsClipUnchanged() {
        val clip = ClipData.newPlainText("clipboard", "hello")

        val result = ClipboardDragClipHelper.remapForHostIfNeeded(
            context = context,
            clipData = clip,
            hostPackage = "com.example.chat",
        )

        assertSame(clip, result?.clipData)
        assertTrue(result?.mirrorSession?.isEmpty ?: false)
    }

    @Test
    fun blankHostKeepsClipUnchanged() {
        val clip = ClipData.newPlainText("clipboard", "hello")

        val result = ClipboardDragClipHelper.remapForHostIfNeeded(
            context = context,
            clipData = clip,
            hostPackage = null,
        )

        assertSame(clip, result?.clipData)
    }

    @Test
    fun hostWithoutUriItemsKeepsClipUnchanged() {
        val clip = ClipData.newPlainText("clipboard", "hello")

        val result = ClipboardDragClipHelper.remapForHostIfNeeded(
            context = context,
            clipData = clip,
            hostPackage = "com.example.chat",
        )

        assertSame(clip, result?.clipData)
        assertTrue(result?.mirrorSession?.isEmpty ?: false)
    }

    /** 系统媒体库图片宿主自己能读，直接透传，不再镜像一份到相册。 */
    @Test
    fun systemMediaUriIsPassedThroughWithoutMirror() {
        val clip = uriClip("content://media/external/images/media/42", "image/png")

        val result = ClipboardDragClipHelper.remapForHostIfNeeded(
            context = context,
            clipData = clip,
            hostPackage = "com.example.chat",
        )

        assertSame(clip, result?.clipData)
        assertTrue(result?.mirrorSession?.isEmpty ?: false)
    }

    @Test
    fun privateUrisNeedSystemMirror() {
        assertTrue(
            ClipboardDragClipHelper.needsSystemMirror(
                Uri.parse("content://com.slideindex.app.fileprovider/clip_files/1.png")
            )
        )
        assertTrue(ClipboardDragClipHelper.needsSystemMirror(Uri.parse("file:///sdcard/a.png")))
        assertFalse(
            ClipboardDragClipHelper.needsSystemMirror(
                Uri.parse("content://media/external/images/media/42")
            )
        )
    }

    @Test
    fun plainTextClipHasShareableContent() {
        assertTrue(
            ClipboardDragShareFallback.hasShareableContent(
                ClipData.newPlainText("clipboard", "hello")
            )
        )
    }

    @Test
    fun htmlClipHasShareableContent() {
        assertTrue(
            ClipboardDragShareFallback.hasShareableContent(
                ClipData.newHtmlText("clipboard", "hello", "<b>hello</b>")
            )
        )
    }

    @Test
    fun blankClipHasNoShareableContent() {
        assertFalse(
            ClipboardDragShareFallback.hasShareableContent(
                ClipData.newPlainText("clipboard", "")
            )
        )
    }

    /** 与 [ClipboardWriter] 的 uriClip 一致：显式声明 MIME，不依赖 resolver 反查。 */
    private fun uriClip(uri: String, mime: String): ClipData =
        ClipData(
            ClipDescription("clipboard", arrayOf(mime)),
            ClipData.Item(Uri.parse(uri)),
        )
}
