package com.iceflow.service

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreCoordinatorTest {
    @Test
    fun connectionIsRejectedWhileRestoreIsReplacingConfiguration() = runBlocking {
        val coordinator = RestoreCoordinator()
        var replaced = false
        val result = coordinator.restore(
            timeoutMs = 1_000,
            requestStop = { 0L },
            awaitRelease = { ResourceReleaseResult(1, emptyList()) },
            replaceConfiguration = {
                assertFalse(coordinator.canStartConnection())
                replaced = true
                "ok"
            },
        )
        assertEquals("ok", result)
        assertTrue(replaced)
        assertTrue(coordinator.canStartConnection())
    }

    @Test
    fun timeoutDoesNotImportAndLeavesConfigurationUntouched() = runBlocking {
        val coordinator = RestoreCoordinator()
        var config = "original"
        var imported = false
        try {
            coordinator.restore(
                timeoutMs = 20,
                requestStop = { 0L },
                awaitRelease = { withTimeout(100) { kotlinx.coroutines.awaitCancellation() } },
                replaceConfiguration = { imported = true; config = "backup" },
            )
        } catch (failure: IllegalStateException) {
            assertTrue(failure.message.orEmpty().contains("Timed out"))
        }
        assertFalse(imported)
        assertEquals("original", config)
        assertTrue(coordinator.canStartConnection())
    }

    @Test
    fun cleanupFailureDoesNotImportAndExposesReason() = runBlocking {
        val coordinator = RestoreCoordinator()
        var imported = false
        var error = ""
        try {
            coordinator.restore(
                timeoutMs = 1_000,
                requestStop = { 0L },
                awaitRelease = { ResourceReleaseResult(1, listOf("Xray stop failed")) },
                replaceConfiguration = { imported = true },
            )
        } catch (failure: IllegalStateException) {
            error = failure.message.orEmpty()
        }
        assertFalse(imported)
        assertTrue(error.contains("Xray stop failed"))
        assertTrue(coordinator.canStartConnection())
    }
}
