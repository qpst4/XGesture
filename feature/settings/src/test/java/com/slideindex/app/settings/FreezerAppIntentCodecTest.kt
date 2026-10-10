package com.slideindex.app.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FreezerAppIntentCodecTest {
    @Test
    fun `round trips intents`() {
        val intents = mapOf(
            "com.example.a" to FreezerAppIntent.FROZEN,
            "com.example.b" to FreezerAppIntent.PAUSE,
        )
        assertEquals(intents, FreezerAppIntentCodec.decode(FreezerAppIntentCodec.encode(intents)))
    }

    @Test
    fun `encode is stable regardless of map order`() {
        val first = mapOf(
            "com.example.b" to FreezerAppIntent.PAUSE,
            "com.example.a" to FreezerAppIntent.FROZEN,
        )
        val second = mapOf(
            "com.example.a" to FreezerAppIntent.FROZEN,
            "com.example.b" to FreezerAppIntent.PAUSE,
        )
        assertEquals(FreezerAppIntentCodec.encode(first), FreezerAppIntentCodec.encode(second))
    }

    @Test
    fun `decoding tolerates missing blank and malformed entries`() {
        assertTrue(FreezerAppIntentCodec.decode(null).isEmpty())
        assertTrue(FreezerAppIntentCodec.decode("").isEmpty())
        assertTrue(FreezerAppIntentCodec.decode("no-separator").isEmpty())
        assertTrue(FreezerAppIntentCodec.decode("com.example.a\u0001x").isEmpty())

        assertEquals(
            mapOf("com.example.a" to FreezerAppIntent.FROZEN),
            FreezerAppIntentCodec.decode("com.example.a\u0001f\ncom.example.b\u0001zzz"),
        )
    }
}
