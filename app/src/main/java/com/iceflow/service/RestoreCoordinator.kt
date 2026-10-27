package com.iceflow.service

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/** Serializes backup replacement against commands that could start a new VPN connection. */
class RestoreCoordinator {
    private val lock = Any()
    private var restoring = false

    val isRestoring: Boolean
        get() = synchronized(lock) { restoring }

    fun canStartConnection(): Boolean = !isRestoring

    suspend fun <T> restore(
        timeoutMs: Long,
        requestStop: () -> Long,
        awaitRelease: suspend (Long) -> ResourceReleaseResult,
        replaceConfiguration: suspend () -> T,
    ): T {
        synchronized(lock) {
            check(!restoring) { "Backup restore is already in progress" }
            restoring = true
        }
        try {
            val previousId = requestStop()
            val result = try {
                withTimeout(timeoutMs) { awaitRelease(previousId) }
            } catch (timeout: TimeoutCancellationException) {
                throw IllegalStateException("Timed out waiting for VPN resources to be released", timeout)
            }
            check(result.succeeded) {
                "Cannot restore because VPN resource cleanup failed: ${result.failures.joinToString()}"
            }
            return replaceConfiguration()
        } finally {
            synchronized(lock) { restoring = false }
        }
    }
}
