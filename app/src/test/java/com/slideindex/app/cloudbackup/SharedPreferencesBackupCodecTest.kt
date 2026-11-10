package com.slideindex.app.cloudbackup

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class SharedPreferencesBackupCodecTest {

    @Test
    fun exportImport_roundTrip_preservesSupportedTypes() {
        val context = RuntimeEnvironment.getApplication()
        val name = "backup_codec_test"
        context.getSharedPreferences(name, Context.MODE_PRIVATE).edit()
            .putBoolean("b", true)
            .putInt("i", 42)
            .putLong("l", 123L)
            .putFloat("f", 1.25f)
            .putString("s", "文本")
            .putStringSet("set", setOf("a", "b"))
            .commit()

        val raw = SharedPreferencesBackupCodec.export(context, name)
        context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        SharedPreferencesBackupCodec.import(context, name, raw)

        val restored = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        assertTrue(restored.getBoolean("b", false))
        assertEquals(42, restored.getInt("i", 0))
        assertEquals(123L, restored.getLong("l", 0L))
        assertEquals(1.25f, restored.getFloat("f", 0f), 0.0001f)
        assertEquals("文本", restored.getString("s", null))
        assertEquals(setOf("a", "b"), restored.getStringSet("set", null))
    }
}
