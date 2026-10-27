package com.iceflow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream

class BoundedTextReaderTest {

    @Test fun readsUtf8WhenByteCountIsExactlyAtLimit() {
        assertEquals("冰", BoundedTextReader.readUtf8(ByteArrayInputStream("冰".toByteArray()), 3))
    }

    @Test fun rejectsInputThatExceedsByteLimit() {
        assertThrows(IllegalArgumentException::class.java) {
            BoundedTextReader.readUtf8(ByteArrayInputStream("abcd".toByteArray()), 3)
        }
    }

    @Test fun acceptsEmptyInputAtZeroLimit() {
        assertEquals("", BoundedTextReader.readUtf8(ByteArrayInputStream(byteArrayOf()), 0))
    }
}
