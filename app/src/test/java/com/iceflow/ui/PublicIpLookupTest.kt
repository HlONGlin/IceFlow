package com.iceflow.ui

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicIpLookupTest {
    @Test
    fun successfulQueryPublishesIp() = runBlocking {
        val lookup = PublicIpLookup { throw AssertionError(it) }
        lookup.query { "203.0.113.7" }
        assertEquals(PublicIpState.Success("203.0.113.7"), lookup.state.value)
    }

    @Test
    fun failedQueryClearsOldIpAndLogsOriginalThrowable() = runBlocking {
        val failures = mutableListOf<Throwable>()
        val lookup = PublicIpLookup { failures += it }
        lookup.query { "203.0.113.7" }
        val failure = IOException("lookup timeout")
        lookup.query { throw failure }
        assertEquals(PublicIpState.Failed, lookup.state.value)
        assertSame(failure, failures.single())
    }

    @Test
    fun newConnectionResetsPreviousIp() = runBlocking {
        val lookup = PublicIpLookup { throw AssertionError(it) }
        lookup.query { "203.0.113.7" }
        lookup.reset()
        assertEquals(PublicIpState.Idle, lookup.state.value)
        lookup.query {
            assertEquals(PublicIpState.Loading, lookup.state.value)
            "203.0.113.8"
        }
        assertEquals(PublicIpState.Success("203.0.113.8"), lookup.state.value)
    }

    @Test
    fun disconnectRejectsLateLookupResult() = runBlocking {
        val lookup = PublicIpLookup { throw AssertionError(it) }
        val result = CompletableDeferred<String>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { lookup.query { result.await() } }
        assertEquals(PublicIpState.Loading, lookup.state.value)
        lookup.reset()
        result.complete("203.0.113.7")
        job.join()
        assertEquals(PublicIpState.Idle, lookup.state.value)
    }

    @Test
    fun oldSessionCannotOverwriteNewSessionIp() = runBlocking {
        val lookup = PublicIpLookup { throw AssertionError(it) }
        val oldResult = CompletableDeferred<String>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { lookup.query { oldResult.await() } }
        lookup.reset()
        lookup.query { "203.0.113.8" }
        oldResult.complete("203.0.113.7")
        job.join()
        assertEquals(PublicIpState.Success("203.0.113.8"), lookup.state.value)
    }

    @Test
    fun linkageFailureAlsoLeavesFailureStateAndOriginalLog() = runBlocking {
        val failures = mutableListOf<Throwable>()
        val lookup = PublicIpLookup { failures += it }
        val failure = NoSuchMethodError("runtime API unavailable")
        lookup.query { throw failure }
        assertEquals(PublicIpState.Failed, lookup.state.value)
        assertSame(failure, failures.single())
    }

    @Test
    fun cancellationDoesNotRemainLoadingOrReportLookupFailure() = runBlocking {
        val failures = mutableListOf<Throwable>()
        val lookup = PublicIpLookup { failures += it }
        var cancelled = false
        try {
            lookup.query { throw CancellationException("session ended") }
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
        assertEquals(PublicIpState.Idle, lookup.state.value)
        assertTrue(failures.isEmpty())
    }
}
