package com.iceflow.ui

import com.iceflow.data.model.ServerProfile
import com.iceflow.service.VpnState
import java.net.SocketTimeoutException

/** Shared rules for node-menu, bulk and single-core session reuse. */
internal object NodeTestingPolicy {
    private const val STANDARD_TIMEOUT_MS = 5_000L
    private const val DUAL_HTTPS_MANUAL_TIMEOUT_MS = 12_000L

    fun timeoutMs(variant: String, url: String, manual: Boolean): Long =
        if (manual && variant == "dual" && url.startsWith("https://", ignoreCase = true)) {
            DUAL_HTTPS_MANUAL_TIMEOUT_MS
        } else {
            STANDARD_TIMEOUT_MS
        }

    fun matchesConnectedServer(session: VpnState.Connected, server: ServerProfile): Boolean =
        session.serverId == server.id || (session.serverId == null && session.serverName == server.name)

    fun canTestAllWithConnectedSingleCore(session: VpnState.Connected, nodes: List<ServerProfile>): Boolean =
        nodes.all { matchesConnectedServer(session, it) }
}

internal enum class NodeTestFailure(val labelKey: String, val storedLatency: Int = -1) {
    DIAL_TIMEOUT("node.test.dialTimeout"),
    TLS_TIMEOUT("node.test.tlsTimeout"),
    RESPONSE_TIMEOUT("node.test.responseTimeout"),
    TIMEOUT("latency.timeout"),
    FAILED("node.test.failed", -2);

    companion object {
        // gomobile preserves the Go error's message, not its type.
        fun from(error: Exception): NodeTestFailure = when (error.message?.substringBefore(": ")) {
            "iceflow_probe_dial_timeout" -> DIAL_TIMEOUT
            "iceflow_probe_tls_timeout" -> TLS_TIMEOUT
            "iceflow_probe_response_timeout" -> RESPONSE_TIMEOUT
            else -> if (error is SocketTimeoutException || error.cause is SocketTimeoutException) TIMEOUT else FAILED
        }
    }
}
