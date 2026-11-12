package com.slideindex.app.gesture

import org.junit.Assert.assertEquals
import org.junit.Test

class TriggerHandleDesignCodecTest {

    @Test
    fun decode_customImageFallsBackToHide() {
        // 旧版本下拉框允许选 CUSTOM_IMAGE，但没有选图/渲染实现；读回时不能再让条保持不可见。
        val encoded = TriggerHandleDesignCodec.encode(
            TriggerHandleDesign(
                kind = TriggerDesignKind.CUSTOM_IMAGE,
                customImageUri = "file:///tmp/handle.png",
            ),
        )

        val decoded = TriggerHandleDesignCodec.decode(encoded)

        assertEquals(TriggerDesignKind.HIDE, decoded.kind)
        assertEquals("file:///tmp/handle.png", decoded.customImageUri)
    }

    @Test
    fun decode_rectangleRoundTrips() {
        val design = TriggerHandleDesign(
            kind = TriggerDesignKind.CONFIGURABLE_RECTANGLE,
            sizeDp = 6f,
            cornerRadiusDp = 3f,
            haloSizeDp = 10f,
        )

        assertEquals(design, TriggerHandleDesignCodec.decode(TriggerHandleDesignCodec.encode(design)))
    }
}
