package com.slideindex.app.overlay.share

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * 分享键"点一下直发"的 Intent 形状：必须补 component、保留 ACTION_SEND / 类型 / EXTRA，
 * 并且在没设默认目标时返回 false（调用方据此退回分享面板）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class ShareSheetLauncherDefaultTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun noDefaultTargetReportsNotShared() {
        // 默认目标表是空的：直发必须"没发出去"，让调用方回退到弹面板。
        assertFalse(ShareSheetLauncher.shareToDefault(context, ShareSheetPayload.Text("hello")))
        assertFalse(
            ShareSheetLauncher.shareToDefault(
                context,
                ShareSheetPayload.Image(android.net.Uri.parse("content://test/1"), "image/png"),
            ),
        )
    }

    @Test
    fun textTargetBecomesExplicitSendIntent() {
        val target = ShareDefaultTarget("com.example.target", "com.example.target.ShareActivity")
        var dismissed = false

        val shared = ShareSheetLauncher.shareToTarget(
            context = context,
            payload = ShareSheetPayload.Text("hello"),
            target = target,
            dismissSource = { dismissed = true },
        )

        assertTrue(shared)
        assertTrue("发送前必须先收起来源浮窗", dismissed)
        val intent = shadowOf(context as Application).nextStartedActivity
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("text/plain", intent.type)
        assertEquals("hello", intent.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals(ComponentName(target.packageName, target.activityClassName), intent.component)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun imageTargetKeepsStreamAndReadGrant() {
        val uri = android.net.Uri.parse("content://com.example.fileprovider/share/1.png")
        val target = ShareDefaultTarget("com.example.target", "com.example.target.ImageActivity")

        val shared = ShareSheetLauncher.shareToTarget(
            context = context,
            payload = ShareSheetPayload.Image(uri = uri, mimeType = "image/png"),
            target = target,
        )

        assertTrue(shared)
        val intent = shadowOf(context as Application).nextStartedActivity
        assertEquals("image/png", intent.type)
        assertEquals(uri, intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM))
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }
}
