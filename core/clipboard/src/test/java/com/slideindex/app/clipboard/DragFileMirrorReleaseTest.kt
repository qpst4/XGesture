package com.slideindex.app.clipboard

import android.app.Application
import android.content.Context
import android.net.Uri
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class DragFileMirrorReleaseTest {

    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    private val prefs
        get() = context.getSharedPreferences(DragFileMirror.PREFS_NAME, Context.MODE_PRIVATE)

    @Test
    fun releaseNowDropsMirrorRecord() {
        val uri = mirrorUri(1)
        record(uri, createdAt = System.currentTimeMillis())

        DragMirrorSession(context, listOf(uri)).releaseNow()

        assertFalse(prefs.contains(uri.toString()))
    }

    @Test
    fun releaseAfterZeroGraceDropsMirrorRecord() {
        val uri = mirrorUri(2)
        record(uri, createdAt = System.currentTimeMillis())

        DragMirrorSession(context, listOf(uri)).releaseAfter(graceMs = 0L)

        assertFalse(prefs.contains(uri.toString()))
    }

    @Test
    fun orphanSweepDropsStaleRecords() {
        val stale = mirrorUri(3)
        record(stale, createdAt = System.currentTimeMillis() - 11 * 60 * 1000L)

        DragFileMirror.purgeOrphans(context)

        assertFalse(prefs.contains(stale.toString()))
    }

    /** 正在拖拽的镜像不能被兜底清理误删：年龄门槛必须长于任何一次拖拽。 */
    @Test
    fun orphanSweepKeepsFreshRecords() {
        val fresh = mirrorUri(4)
        record(fresh, createdAt = System.currentTimeMillis())

        DragFileMirror.purgeOrphans(context)

        assertTrue(prefs.contains(fresh.toString()))
    }

    private fun mirrorUri(id: Long): Uri =
        Uri.parse("content://media/external_primary/images/media/$id")

    private fun record(uri: Uri, createdAt: Long) {
        prefs.edit().putLong(uri.toString(), createdAt).commit()
    }
}
