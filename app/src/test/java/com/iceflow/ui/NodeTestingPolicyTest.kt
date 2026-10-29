package com.iceflow.ui

import com.iceflow.data.model.Protocol
import com.iceflow.data.model.ServerProfile
import com.iceflow.service.VpnState
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
}
