package com.iceflow.service

import com.iceflow.data.model.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnTilePolicyTest {
    private val labels = VpnTileLabels(
        disconnected = "Disconnected",
        connecting = "Connecting",
        connected = "Connected",
        waitingNetwork = "Waiting for network",
        stopping = "Disconnecting",
    )

    @Test
    fun connectingRemainsInactiveUntilTheServiceReportsConnected() {
        val result = render(VpnState.Connecting)

        assertEquals(VpnTileState.INACTIVE, result.state)
        assertEquals("Connecting", result.subtitle)
        assertEquals(VpnTileIcon.CONNECTING, result.icon)
        assertTrue(result.showNodeName)
    }

    @Test
    fun connectedStateActivatesTile() {
        val result = render(VpnState.Connected("node", 0))

        assertEquals(VpnTileState.ACTIVE, result.state)
        assertEquals("Connected", result.subtitle)
        assertEquals(VpnTileIcon.ACTIVE, result.icon)
        assertTrue(result.showNodeName)
    }

    @Test
    fun successfulStopClearsFailureSubtitleAndReturnsToInactive() {
        val result = render(VpnState.Idle, VpnErrorType.CORE_START_FAILED)

        assertEquals(VpnTileState.INACTIVE, result.state)
        assertEquals("", result.subtitle)
        assertEquals(VpnTileIcon.DEFAULT, result.icon)
        assertFalse(result.showNodeName)
    }

    @Test
    fun eachClassifiedFailureShowsItsCatalogShortLabelWithoutActivatingTile() {
        val failures = listOf(
            VpnErrorType.VPN_AUTHORIZATION_DENIED,
            VpnErrorType.CORE_START_FAILED,
            VpnErrorType.CONFIGURATION_OR_SUBSCRIPTION,
            VpnErrorType.NETWORK_UNAVAILABLE,
            VpnErrorType.RETRIES_EXHAUSTED,
            VpnErrorType.RESOURCE_CLEANUP_FAILED,
        )

        failures.forEach { type ->
            val error = VpnErrorCatalog.presentation(type, AppLanguage.SIMPLIFIED_CHINESE)
            val result = render(VpnState.Error(error))
            assertEquals(type.name, VpnTileState.INACTIVE, result.state)
            assertEquals(type.name, VpnErrorCatalog.shortLabel(type, AppLanguage.SIMPLIFIED_CHINESE), result.subtitle)
            assertEquals(type.name, VpnTileIcon.ERROR, result.icon)
            assertFalse(type.name, result.showNodeName)
        }
    }

    @Test
    fun manualRetryAndSuccessfulConnectClearThePreviousFailureSubtitle() {
        val previousFailure = VpnErrorType.RETRIES_EXHAUSTED
        val retrying = render(VpnState.Connecting, previousFailure)
        val connected = render(VpnState.Connected("node", 0), previousFailure)

        assertEquals("Connecting", retrying.subtitle)
        assertEquals(VpnTileState.INACTIVE, retrying.state)
        assertEquals("Connected", connected.subtitle)
        assertEquals(VpnTileState.ACTIVE, connected.state)
    }

    @Test
    fun stoppingAndWaitingNetworkRemainInactiveAndUseTheirOwnIcons() {
        val stopping = render(VpnState.Stopping)
        val waiting = render(VpnState.WaitingNetwork)

        assertEquals(VpnTileState.INACTIVE, stopping.state)
        assertEquals("Disconnecting", stopping.subtitle)
        assertEquals(VpnTileIcon.STOPPING, stopping.icon)
        assertEquals(VpnTileState.INACTIVE, waiting.state)
        assertEquals("Waiting for network", waiting.subtitle)
        assertEquals(VpnTileIcon.WAITING_NETWORK, waiting.icon)
    }

    private fun render(state: VpnState, recentError: VpnErrorType? = null): VpnTilePresentation =
        VpnTilePolicy.presentation(state, recentError, AppLanguage.SIMPLIFIED_CHINESE, labels)
}
