package com.iceflow.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test
    fun bytes() {
        assertEquals("512 B", formatBytes(512))
        assertEquals("1.0 KB", formatBytes(1024))
        assertEquals("1.5 MB", formatBytes(1536L * 1024))
        assertEquals("2.0 GB", formatBytes(2L * 1024 * 1024 * 1024))
    }

    @Test
    fun speedAndDuration() {
        assertEquals("1.0 KB/s", formatSpeed(1024))
        assertEquals("01:01:01", formatDuration(3_661_000))
        assertEquals("00:00:00", formatDuration(-5))
    }
}
