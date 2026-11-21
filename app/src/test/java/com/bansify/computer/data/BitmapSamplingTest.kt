package com.bansify.computer.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BitmapSamplingTest {
    @Test fun boundsLargeImagesToPowerOfTwoSamples() {
        assertEquals(4, BitmapSampling.sampleSize(8000, 4000, 2000, 2000))
        assertEquals(1, BitmapSampling.sampleSize(300, 200, 1920, 1080))
        assertEquals(8, BitmapSampling.sampleSize(500, 8000, 1000, 1000))
    }
    @Test fun invalidDimensionsDoNotLoop() {
        assertEquals(1, BitmapSampling.sampleSize(0, 0, 100, 100))
        assertEquals(1, BitmapSampling.sampleSize(400, 400, 0, 0))
    }
}
