package com.iceflow.service

internal object VpnNetworkPolicy {
    fun stateAfterStartup(
        connectedState: VpnState.Connected,
        hasUnderlyingNetwork: Boolean,
    ): VpnState = if (hasUnderlyingNetwork) connectedState else VpnState.WaitingNetwork
}
