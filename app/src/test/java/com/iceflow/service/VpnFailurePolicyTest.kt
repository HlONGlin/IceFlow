package com.iceflow.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VpnFailurePolicyTest {
    @Test
    fun startupFailureAndFailedCleanupKeepStartupClassification() {
        assertEquals(VpnErrorType.CORE_START_FAILED,
            VpnFailurePolicy.afterCleanup(VpnErrorType.CORE_START_FAILED, false))
    }

    @Test
    fun startupFailureAndSuccessfulCleanupKeepStartupClassification() {
        assertEquals(VpnErrorType.CORE_START_FAILED,
            VpnFailurePolicy.afterCleanup(VpnErrorType.CORE_START_FAILED, true))
    }

    @Test
    fun stopCleanupFailureReportsCleanupClassification() {
        assertEquals(VpnErrorType.RESOURCE_CLEANUP_FAILED,
            VpnFailurePolicy.afterCleanup(null, false))
    }

    @Test
    fun successfulStopHasNoError() {
        assertNull(VpnFailurePolicy.afterCleanup(null, true))
    }

    @Test
    fun everyPrimaryClassificationSurvivesSubsequentCleanup() {
        VpnErrorType.entries.forEach { type ->
            assertEquals(type, VpnFailurePolicy.afterCleanup(type, false))
            assertEquals(type, VpnFailurePolicy.afterCleanup(type, true))
        }
    }

    @Test
    fun repeatedDestroyCleanupKeepsPrimaryAndPublishesReleaseResults() {
        val tracker = ResourceReleaseTracker()
        val first = tracker.begin()
        tracker.complete(first, listOf("startup cleanup failure"))
        val primary = VpnFailurePolicy.afterCleanup(VpnErrorType.CORE_START_FAILED, false)
        val second = tracker.begin()
        tracker.complete(second, emptyList())

        assertEquals(VpnErrorType.CORE_START_FAILED,
            VpnFailurePolicy.afterCleanup(primary, true))
        assertEquals(first + 1, tracker.status.value.lastCompletedId)
        assertEquals(ResourceReleaseResult(second, emptyList()), tracker.status.value.lastResult)
    }
}
