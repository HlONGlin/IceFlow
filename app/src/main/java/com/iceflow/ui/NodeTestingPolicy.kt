package com.iceflow.ui

import com.iceflow.data.model.ServerProfile
import com.iceflow.service.VpnState

/** Decides whether a node can reuse the currently running single-core session. */
internal object NodeTestingPolicy {
    fun matchesConnectedServer(session: VpnState.Connected, server: ServerProfile): Boolean =
        session.serverId == server.id || (session.serverId == null && session.serverName == server.name)

    fun canTestAllWithConnectedSingleCore(session: VpnState.Connected, nodes: List<ServerProfile>): Boolean =
        nodes.all { matchesConnectedServer(session, it) }
}
