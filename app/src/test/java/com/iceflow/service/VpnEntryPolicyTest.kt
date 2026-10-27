package com.iceflow.service

import org.junit.Assert.assertEquals
import org.junit.Test

class VpnEntryPolicyTest {
    private val entries = VpnEntryPoint.entries

    @Test
    fun everyEntryUsesSamePrimaryOperationForEachState() {
        val cases = listOf(
            VpnState.Idle to VpnUserOperation.CONNECT,
            VpnState.Connecting to VpnUserOperation.CANCEL,
            VpnState.Connected("node", 0) to VpnUserOperation.STOP,
            VpnState.WaitingNetwork to VpnUserOperation.RETRY,
            VpnState.Stopping to VpnUserOperation.IGNORE,
            VpnState.Error("failed") to VpnUserOperation.CONNECT,
        )
        cases.forEach { (state, expected) ->
            entries.forEach { entry ->
                assertEquals("$entry with $state", expected, VpnEntryPolicy.primary(state, entry))
            }
        }
    }

    @Test
    fun waitingNetworkExposesExplicitStopForEveryEntry() {
        entries.forEach { entry ->
            assertEquals(VpnUserOperation.STOP, VpnEntryPolicy.secondary(VpnState.WaitingNetwork, entry))
        }
        listOf(VpnState.Idle, VpnState.Connecting, VpnState.Connected("node", 0), VpnState.Stopping, VpnState.Error("failed"))
            .forEach { state -> entries.forEach { entry -> assertEquals(null, VpnEntryPolicy.secondary(state, entry)) } }
    }
}
