package com.slideindex.app.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExcludedAppScopesCodecTest {
    @Test
    fun roundTripsScopesMap() {
        val input = mapOf(
            "com.example.one" to ExcludedAppScopes(true, false, true, false, true),
            "com.example.two" to ExcludedAppScopes(false, true, false, true, false),
        )
        val encoded = ExcludedAppScopesCodec.encode(input)
        assertEquals(input, ExcludedAppScopesCodec.decode(encoded))
    }

    @Test
    fun ignoresInvalidEntries() {
        val decoded = ExcludedAppScopesCodec.decode(
            setOf(
                "com.valid|1,0,1",
                "invalid",
                "|1,0,1",
            ),
        )
        assertEquals(1, decoded.size)
        assertTrue(decoded.containsKey("com.valid"))
    }
}
