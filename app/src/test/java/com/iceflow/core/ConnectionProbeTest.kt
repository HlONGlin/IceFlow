package com.iceflow.core

import org.junit.Assert.assertArrayEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

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
}
