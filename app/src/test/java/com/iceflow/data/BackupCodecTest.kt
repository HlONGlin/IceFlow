package com.iceflow.data

import com.iceflow.data.model.*
import org.junit.Assert.*
import org.junit.Test

class BackupCodecTest {
    @Test fun fullBackupRoundTripsCredentialsGroupsRulesAndSettings() {
        val node = ServerProfile(name = "node", protocol = Protocol.SOCKS, address = "localhost", port = 1080, username = "user", password = "secret", groupId = "local")
        val store = ServerStore(servers = listOf(node), selectedId = node.id, localGroups = listOf(LocalGroup(id = "local", name = "Local")), selectedGroupId = "local", groupOrder = listOf("local"))
        val settings = AppSettings(socksEnabled = false, dnsProxyEnabled = false, nightMode = NightMode.ENABLED, socksPassword = "local-secret")
        val restored = BackupCodec.decode(BackupCodec.encode(store, settings))
        assertEquals(store, restored.store)
        assertEquals(settings, restored.settings)
    }

    @Test fun rejectsFutureFormatsInvalidDnsAndDuplicateNodesBeforeRestore() {
        val settings = AppSettings()
        val text = BackupCodec.encode(ServerStore(), settings)
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(text.replace("\"version\": 1", "\"version\": 9")) }
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(BackupCodec.encode(ServerStore(), settings.copy(remoteDns = "bad.example"))) }
        val node = ServerProfile(name = "node", protocol = Protocol.SOCKS, address = "localhost", port = 1080)
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(BackupCodec.encode(ServerStore(servers = listOf(node, node)), settings)) }
    }

    @Test fun onlyConnectionChangesRequireRestartAndDnsMustBeNumeric() {
        val settings = AppSettings()
        assertTrue(settings.hasSameConnection(settings.copy(theme = AppTheme.MINT_GREEN, testUrl = "https://example.org/", showTrafficStats = true)))
        assertFalse(settings.hasSameConnection(settings.copy(dnsProxyEnabled = false)))
        assertFalse(settings.hasSameConnection(settings.copy(socksPassword = "changed")))
        assertThrows(IllegalArgumentException::class.java) { settings.copy(dnsServers = listOf("256.1.1.1")).validate() }
        assertThrows(IllegalArgumentException::class.java) { settings.copy(remoteDns = "::::").validate() }
        settings.copy(dnsServers = listOf("2606:4700:4700::1111")).validate()
    }

    @Test fun deletedChainNodesRemainRestorableAndCannotSilentlyBypassTheChain() {
        val node = ServerProfile(name = "node", protocol = Protocol.SOCKS, address = "localhost", port = 1080, groupId = "local")
        val store = ServerStore(servers = listOf(node), selectedId = node.id,
            localGroups = listOf(LocalGroup(id = "local", name = "Local", frontNodeId = "removed")))
        val restored = BackupCodec.decode(BackupCodec.encode(store, AppSettings())).store
        assertEquals(store, restored)
        assertThrows(IllegalArgumentException::class.java) {
            XrayConfigBuilder.build(restored.selected!!, AppSettings(), store = restored)
        }
    }
}
