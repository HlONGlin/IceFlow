package com.iceflow.data

import com.iceflow.data.model.*
import com.iceflow.data.repository.ServerRepository
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ProxyCompositionsTest {
    private val first = ServerProfile(id = "a", name = "first", protocol = Protocol.TROJAN, address = "first.example", port = 443, password = "secret", tls = "tls", groupId = "local")
    private val second = first.copy(id = "b", name = "second", address = "second.example", groupId = null, subscriptionId = "sub")
    private fun profile(kind: ProxyCompositionKind) = NodeEditing.blank(Protocol.SOCKS).copy(
        id = "composition", name = "configuration", composition = ProxyComposition(kind, listOf(second.id, first.id)),
    )
    private fun store(p: ServerProfile) = ServerStore(
        servers = listOf(first, second, p), selectedId = p.id,
        localGroups = listOf(LocalGroup(id = "local", name = "original")),
        subscriptions = listOf(Subscription(id = "sub", name = "subscription", url = "https://example.com/sub")),
    )

    @Test fun chainUsesSelectedOrderWithoutApplyingSourceGroupSettings() {
        val p = profile(ProxyCompositionKind.CHAIN)
        val s = store(p).copy(localGroups = listOf(LocalGroup(id = "local", name = "original", frontNodeId = "missing")))
        val cfg = Json.parseToJsonElement(XrayConfigBuilder.build(p, AppSettings(), store = s)).jsonObject
        val out = cfg.getValue("outbounds").jsonArray.map { it.jsonObject }
        val firstOut = out.first { it["tag"] == JsonPrimitive("chain-0") }
        val lastOut = out.first { it["tag"] == JsonPrimitive("proxy") }
        assertEquals("second.example", firstOut["settings"]!!.jsonObject["servers"]!!.jsonArray[0].jsonObject["address"]!!.jsonPrimitive.content)
        assertEquals("first.example", lastOut["settings"]!!.jsonObject["servers"]!!.jsonArray[0].jsonObject["address"]!!.jsonPrimitive.content)
        assertEquals("chain-0", lastOut["streamSettings"]!!.jsonObject["sockopt"]!!.jsonObject["dialerProxy"]!!.jsonPrimitive.content)
    }

    @Test fun balanceReferencesServersAcrossGroupsAndPersistsInBackup() {
        val p = profile(ProxyCompositionKind.BALANCE).let { it.copy(composition = it.composition!!.copy(strategy = LoadBalanceStrategy.LEAST_PING, testUrl = "https://example.com/check", intervalSeconds = 60)) }
        val s = store(p)
        val cfg = Json.parseToJsonElement(XrayConfigBuilder.build(p, AppSettings(), store = s)).jsonObject
        assertEquals(2, cfg["outbounds"]!!.jsonArray.count { it.jsonObject["tag"]?.jsonPrimitive?.content?.startsWith("lb-") == true })
        assertEquals("https://example.com/check", cfg["observatory"]!!.jsonObject["probeURL"]!!.jsonPrimitive.content)
        assertEquals(s, BackupCodec.decode(BackupCodec.encode(s, AppSettings())).store)
        val dir = java.nio.file.Files.createTempDirectory("composition").toFile()
        try {
            val file = File(dir, "servers.json")
            ServerRepository(file).addServers(s.servers)
            assertEquals(s.servers, ServerRepository(file).store.value.servers)
            assertEquals("local", s.servers[0].groupId)
            assertEquals("sub", s.servers[1].subscriptionId)
        } finally { dir.deleteRecursively() }
    }

    @Test fun invalidReferencesAndTooFewServersAreRejected() {
        val p = profile(ProxyCompositionKind.CHAIN)
        for (ids in listOf(emptyList(), listOf("a"), listOf("a", "a"), listOf("a", "missing"), listOf("a", p.id))) {
            val invalid = p.copy(composition = p.composition!!.copy(serverIds = ids))
            assertTrue(runCatching { NodeEditing.validate(invalid, store(invalid)) }.isFailure)
            assertTrue(runCatching { XrayConfigBuilder.build(invalid, AppSettings(), store = store(invalid)) }.isFailure)
        }
        val nested = p.copy(id = "nested")
        val invalid = p.copy(composition = p.composition!!.copy(serverIds = listOf("a", nested.id)))
        assertTrue(runCatching { ProxyCompositions.resolve(invalid, store(invalid).copy(servers = listOf(first, nested, invalid))) }.isFailure)
    }
}
