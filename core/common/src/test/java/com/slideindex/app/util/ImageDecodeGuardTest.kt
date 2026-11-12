package com.slideindex.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageDecodeGuardTest {

    @Test
    fun sampleSize_keepsSmallImageAtOriginalSize() {
        assertEquals(1, ImageDecodeGuard.sampleSize(512, 512, 512))
        assertEquals(1, ImageDecodeGuard.sampleSize(320, 800, 1024))
    }

    @Test
    fun sampleSize_halvesUntilWithinBudget() {
        assertEquals(2, ImageDecodeGuard.sampleSize(1024, 1024, 512))
        assertEquals(16, ImageDecodeGuard.sampleSize(7168, 7168, 512))
        assertEquals(2, ImageDecodeGuard.sampleSize(7168, 7168, 4096))
    }

    @Test
    fun sampleSize_handlesNonSquareAndInvalidDimensions() {
        assertEquals(16, ImageDecodeGuard.sampleSize(8000, 1000, 512))
        assertEquals(1, ImageDecodeGuard.sampleSize(0, 1000, 512))
        assertEquals(1, ImageDecodeGuard.sampleSize(1000, 1000, 0))
    }
}
