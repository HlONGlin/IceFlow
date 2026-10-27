package com.iceflow.service

/** Pure desired-state and retry-token rules shared by the service and unit tests. */
class DesiredRunningController {
    var desiredRunning: Boolean = false
        private set
    var generation: Long = 0
        private set
    var retryAttempt: Int = 0
        private set

    fun userConnect() {
        desiredRunning = true
        resetRetries()
    }

    fun userStop() {
        desiredRunning = false
        resetRetries()
    }

    fun terminateAfterFailure() {
        desiredRunning = false
        resetRetries()
    }

    fun <T> terminateWithCleanup(cleanup: () -> T, publishError: (T) -> Unit): T {
        terminateAfterFailure()
        val result = cleanup()
        publishError(result)
        return result
    }

    fun manualRetry(state: VpnState): Boolean {
        if (state != VpnState.Connecting && state != VpnState.WaitingNetwork) return false
        desiredRunning = true
        resetRetries()
        return true
    }

    fun shouldReconnectForConfigurationChange(state: VpnState): Boolean =
        desiredRunning && state !is VpnState.Error

    fun retryExhausted(maxRetries: Int): Boolean = retryAttempt >= maxRetries

    fun nextRetryDelayIndex(): Int = retryAttempt++

    fun issueRetryToken(): Long = ++generation

    fun invalidateRetries() = resetRetries()

    fun markConnected() {
        retryAttempt = 0
    }

    fun acceptsRetryToken(token: Long): Boolean = desiredRunning && token == generation

    private fun resetRetries() {
        generation++
        retryAttempt = 0
    }
}
