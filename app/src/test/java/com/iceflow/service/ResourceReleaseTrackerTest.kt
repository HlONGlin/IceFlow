package com.iceflow.service

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourceReleaseTrackerTest {
    @Test
    fun lateWaiterReadsAlreadyPublishedCompletion() = runBlocking {
        val tracker = ResourceReleaseTracker()
        val id = tracker.begin()
        tracker.complete(id, emptyList())

        assertEquals(ResourceReleaseResult(id, emptyList()), tracker.await(id))
    }

    @Test
    fun eachReleaseGetsIncreasingIdAndPublishesResult() = runBlocking {
        val tracker = ResourceReleaseTracker()
        val first = tracker.begin()
        tracker.complete(first, emptyList())
        val second = tracker.begin()
        tracker.complete(second, listOf("TUN close failed"))

        assertEquals(1L, first)
        assertEquals(2L, second)
        assertEquals(second, tracker.status.value.lastCompletedId)
        assertEquals(second, tracker.await(second).requestId)
        assertFalse(tracker.await(second).succeeded)
        assertEquals(listOf("TUN close failed"), tracker.await(first).failures)
    }
}
