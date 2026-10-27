package com.iceflow.service

/** Cleanup can add a failure, but must never replace the failure that caused it. */
object VpnFailurePolicy {
    fun afterCleanup(primaryError: VpnErrorType?, cleanupSucceeded: Boolean): VpnErrorType? =
        primaryError ?: if (cleanupSucceeded) null else VpnErrorType.RESOURCE_CLEANUP_FAILED
}
