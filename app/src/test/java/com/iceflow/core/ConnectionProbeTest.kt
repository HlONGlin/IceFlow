package com.iceflow.core

import org.junit.Assert.assertArrayEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream

class ConnectionProbeTest {

    @Test fun socks5UsesPerSocketUsernamePasswordHandshake() {
        val input = ByteArrayInputStream(byteArrayOf(
            5, 2, // select username/password
            1, 0, // authentication accepted
            5, 0, 0, 1, 127, 0, 0, 1, 0, 0, // CONNECT accepted
        ))
        val output = ByteArrayOutputStream()

        ConnectionProbe.negotiateSocks5(input, output, "user", "pass", "api.ipify.org", 443)

        assertArrayEquals(
            byteArrayOf(
                5, 1, 2, // offer username/password only
                1, 4, 117, 115, 101, 114, 4, 112, 97, 115, 115, // RFC 1929 credentials
                5, 1, 0, 3, 13, 97, 112, 105, 46, 105, 112, 105, 102, 121, 46, 111, 114, 103, 1, -69, // CONNECT
            ),
            output.toByteArray(),
        )
    }

    @Test fun socks5WithoutCredentialsOffersNoAuthentication() {
        val input = ByteArrayInputStream(byteArrayOf(5, 0, 5, 0, 0, 1, 127, 0, 0, 1, 0, 0))
        val output = ByteArrayOutputStream()

        ConnectionProbe.negotiateSocks5(input, output, "", "", "api.ipify.org", 443)

        assertArrayEquals(byteArrayOf(5, 1, 0, 5, 1, 0, 3, 13, 97, 112, 105, 46, 105, 112, 105, 102, 121, 46, 111, 114, 103, 1, -69), output.toByteArray())
    }
    @Test fun readExactlyAssemblesFragmentedBytesInOrder() {
        val input = FragmentedInputStream(byteArrayOf(1, 2, 3, 4, 5), 1)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5), ConnectionProbe.readExactly(input, 5))
    }

    @Test fun readExactlyThrowsWhenStreamEndsEarly() {
        val input = ByteArrayInputStream(byteArrayOf(1, 2))
        try {
            ConnectionProbe.readExactly(input, 3)
            org.junit.Assert.fail("expected EOFException")
        } catch (_: EOFException) {
            // expected
        }
    }

    @Test fun readExactlyPreservesSingleReadBehavior() {
        val input = ByteArrayInputStream(byteArrayOf(9, 8, 7))
        assertArrayEquals(byteArrayOf(9, 8, 7), ConnectionProbe.readExactly(input, 3))
    }

    private class FragmentedInputStream(
        private val bytes: ByteArray,
        private val maxChunk: Int,
    ) : InputStream() {
        private var offset = 0
        override fun read(): Int = if (offset == bytes.size) -1 else bytes[offset++].toInt() and 0xff
        override fun read(target: ByteArray, start: Int, length: Int): Int {
            if (offset == bytes.size) return -1
            val count = minOf(length, maxChunk, bytes.size - offset)
            bytes.copyInto(target, start, offset, offset + count)
            offset += count
            return count
        }
    }

}
