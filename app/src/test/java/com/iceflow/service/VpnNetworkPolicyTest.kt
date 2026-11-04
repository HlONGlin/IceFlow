package com.iceflow.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class VpnNetworkPolicyTest {
    @Test
    fun startupWithUnderlyingNetworkPublishesConnectedState() {
        val connected = VpnState.Connected("test", since = 1L)

        assertSame(connected, VpnNetworkPolicy.stateAfterStartup(connected, hasUnderlyingNetwork = true))
    }

    @Test
    fun startupWithoutUnderlyingNetworkWaitsForRecovery() {
        val connected = VpnState.Connected("test", since = 1L)

        assertEquals(VpnState.WaitingNetwork, VpnNetworkPolicy.stateAfterStartup(connected, hasUnderlyingNetwork = false))
    }
}
