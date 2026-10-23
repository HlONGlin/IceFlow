package com.iceflow.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SubscriptionFetcherTest {

    @Test
    fun acceptsHttpsRedirectTargets() {
        assertEquals("https://example.com/path", SubscriptionFetcher.requireHttpsUri("https://example.com/path").toString())
    }

    @Test
    fun rejectsInsecureOrCredentialBearingRedirectTargets() {
        assertThrows(IllegalArgumentException::class.java) {
            SubscriptionFetcher.requireHttpsUri("http://example.com/subscription")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SubscriptionFetcher.requireHttpsUri("https://user:password@example.com/subscription")
        }
    }
}
