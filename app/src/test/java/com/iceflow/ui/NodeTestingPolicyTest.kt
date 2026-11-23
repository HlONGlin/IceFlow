package com.iceflow.ui

import com.iceflow.data.model.Protocol
import com.iceflow.data.model.ServerProfile
import com.iceflow.service.VpnState
import java.io.IOException
import java.net.SocketTimeoutException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeTestingPolicyTest {
    private fun node(id: String, name: String = id) = ServerProfile(
        id = id,
        name = name,
        protocol = Protocol.SOCKS,
        address = "127.0.0.1",
        port = 1080,
    )

    @Test
    fun connectedNodeMatchesStableIdEvenWhenNameChanges() {
        val session = VpnState.Connected("old name", 0, serverId = "node-1")
        assertTrue(NodeTestingPolicy.matchesConnectedServer(session, node("node-1", "new name")))
        assertFalse(NodeTestingPolicy.matchesConnectedServer(session, node("node-2", "old name")))
    }

    @Test
    fun legacySessionFallsBackToName() {
        val session = VpnState.Connected("node", 0)
        assertTrue(NodeTestingPolicy.matchesConnectedServer(session, node("node-1", "node")))
        assertFalse(NodeTestingPolicy.matchesConnectedServer(session, node("node-2", "other")))
    }

    @Test
    fun singleCoreBatchRequiresOnlyTheConnectedNode() {
        val session = VpnState.Connected("node", 0, serverId = "node-1")
        assertTrue(NodeTestingPolicy.canTestAllWithConnectedSingleCore(session, listOf(node("node-1"))))
        assertFalse(NodeTestingPolicy.canTestAllWithConnectedSingleCore(session, listOf(node("node-1"), node("node-2"))))
    }

    @Test
    fun onlyManualDualHttpsGetsTheExtendedBudget() {
        val url = "https://probe.invalid/generate_204"
        assertEquals(12_000L, NodeTestingPolicy.timeoutMs("dual", url, manual = true))
        assertEquals(12_000L, NodeTestingPolicy.timeoutMs("dual", "HTTPS://probe.invalid", manual = true))
        assertEquals(5_000L, NodeTestingPolicy.timeoutMs("dual", url, manual = false))
        assertEquals(5_000L, NodeTestingPolicy.timeoutMs("dual", "http://probe.invalid", manual = true))
        assertEquals(5_000L, NodeTestingPolicy.timeoutMs("single", url, manual = true))
        assertEquals(5_000L, NodeTestingPolicy.timeoutMs("single", url, manual = false))
    }

    @Test
    fun mapsNativeTimeoutCodesWithoutGuessingFromErrorText() {
        assertEquals(NodeTestFailure.DIAL_TIMEOUT, NodeTestFailure.from(Exception("iceflow_probe_dial_timeout: deadline")))
        assertEquals(NodeTestFailure.TLS_TIMEOUT, NodeTestFailure.from(Exception("iceflow_probe_tls_timeout: deadline")))
        assertEquals(NodeTestFailure.RESPONSE_TIMEOUT, NodeTestFailure.from(Exception("iceflow_probe_response_timeout: deadline")))
        assertEquals(NodeTestFailure.TIMEOUT, NodeTestFailure.from(SocketTimeoutException()))
        assertEquals(NodeTestFailure.TIMEOUT, NodeTestFailure.from(IOException(SocketTimeoutException())))
        assertEquals(NodeTestFailure.FAILED, NodeTestFailure.from(Exception("HTTP 503")))
        assertEquals(NodeTestFailure.FAILED, NodeTestFailure.from(Exception("config: iceflow_probe_tls_timeout: bad config")))
    }
}
