package com.iceflow.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesiredRunningControllerTest {
    @Test
    fun explicitConnectSetsDesiredRunningAndResetsRetries() {
        val controller = DesiredRunningController()
        controller.nextRetryDelayIndex()

        controller.userConnect()

        assertTrue(controller.desiredRunning)
        assertEquals(0, controller.retryAttempt)
    }

    @Test
    fun userStopClearsDesiredRunningAndInvalidatesQueuedRetry() {
        val controller = DesiredRunningController()
        controller.userConnect()
        val queuedRetry = controller.issueRetryToken()

        controller.userStop()

        assertFalse(controller.desiredRunning)
        assertFalse(controller.acceptsRetryToken(queuedRetry))
    }

    @Test
    fun exhaustedRetriesClearDesiredBeforeCleanupAndPublishErrorAfterCleanup() {
        val controller = DesiredRunningController()
        controller.userConnect()
        repeat(5) { controller.nextRetryDelayIndex() }
        assertTrue(controller.retryExhausted(5))
        val order = mutableListOf<String>()

        controller.terminateWithCleanup(
            cleanup = {
                assertFalse(controller.desiredRunning)
                order += "cleanup"
                "clean"
            },
            publishError = {
                order += "error"
                assertEquals("clean", it)
            },
        )

        assertEquals(listOf("cleanup", "error"), order)
        assertFalse(controller.desiredRunning)
    }

    @Test
    fun configurationChangesAfterExhaustionDoNotRequestReconnect() {
        val controller = DesiredRunningController()
        controller.userConnect()
        repeat(5) { controller.nextRetryDelayIndex() }
        controller.terminateWithCleanup(cleanup = {}, publishError = {})

        assertFalse(controller.shouldReconnectForConfigurationChange(VpnState.Error(VpnErrorCatalog.presentation(VpnErrorType.RETRIES_EXHAUSTED, com.iceflow.data.model.AppLanguage.ENGLISH))))
        assertFalse(controller.desiredRunning)
    }

    @Test
    fun manualRetryFromConnectingSetsDesiredAndResetsRetryCount() {
        assertManualRetryResets(VpnState.Connecting)
    }

    @Test
    fun manualRetryFromWaitingNetworkSetsDesiredAndResetsRetryCount() {
        assertManualRetryResets(VpnState.WaitingNetwork)
    }

    private fun assertManualRetryResets(state: VpnState) {
        val controller = DesiredRunningController()
        controller.nextRetryDelayIndex()
        controller.userStop()

        assertTrue(controller.manualRetry(state))

        assertTrue(controller.desiredRunning)
        assertEquals(0, controller.retryAttempt)
    }
}
