package com.iceflow.data

import com.iceflow.data.model.AppSettings
import com.iceflow.data.model.Protocol
import com.iceflow.data.repository.ServerRepository
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.util.Base64

class ExtendedProtocolsTest {
    @get:Rule val temp = TemporaryFolder()
    private val key = "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="
    private val uuid = "00000000-0000-4000-8000-000000000001"
    private fun config(link: String): JsonObject = Json.parseToJsonElement(
        XrayConfigBuilder.build(requireNotNull(ShareLinkParser.parse(link)), AppSettings())
    ).jsonObject.getValue("iceflowAdapter").jsonObject

    @Test fun extendedLinksRoundTripConnectionSettings() {
        val ssr = "ssr://" + Base64.getUrlEncoder().withoutPadding().encodeToString(
            "server.example:8388:auth_aes128_md5:aes-128-cfb:tls1.2_ticket_auth:cGErc3M/?remarks=U1NS".toByteArray())
        val links = listOf(
            "hysteria://server.example:443?auth=pa%2Bss&upmbps=10&downmbps=100&obfs=secret#HY%20one",
            "hy2://pa%3Ass@[2001:db8::1]:443/?obfs=salamander&obfs-password=abc%2B123&sni=cdn.example&insecure=1#HY%202",
            "tuic://$uuid:pa%3Ass@server.example:443?congestion_control=bbr&udp_relay_mode=native&alpn=h3#TUIC",
            "tuic://legacy%2Btoken@server.example:443?alpn=h3#v4",
            "wireguard://$key@server.example:51820?publickey=$key&address=10.0.0.2%2F32%2Cfd00%3A%3A2%2F128&reserved=1%2C2%2C3#WG",
            ssr,
            "https://us%3Aer:pa%3Ass%2Bword@server.example:8443?sni=cdn.example#HTTP%20TLS",
            "socks5://user:pa%3Ass@server.example:1080?tls=true#SOCKS",
        )
        links.forEach { link ->
            val original = requireNotNull(ShareLinkParser.parse(link)) { link }
            val reparsed = requireNotNull(ShareLinkParser.parse(ShareLinkParser.encode(original)))
            assertEquals(original.name, reparsed.name)
            assertEquals(original.protocol, reparsed.protocol)
            assertEquals(original.address, reparsed.address)
            assertEquals(original.port, reparsed.port)
            assertEquals(original.username, reparsed.username)
            assertEquals(original.password, reparsed.password)
            assertEquals(original.uuid, reparsed.uuid)
            assertEquals(ExtendedConfigBuilder.build(original), ExtendedConfigBuilder.build(reparsed))
        }
    }

    @Test fun mapsProtocolSpecificOptionsToNativeTypes() {
        val hy = config("hy2://pass@host:443,8443?obfs=salamander&obfs-password=obfs&insecure=1")
        assertEquals("443,8443", hy["ports"]?.jsonPrimitive?.content)
        assertEquals(true, hy["skip-cert-verify"]?.jsonPrimitive?.boolean)
        assertEquals("obfs", hy["obfs-password"]?.jsonPrimitive?.content)
        val tuic = config("tuic://$uuid:pass@host?congestion_control=bbr&udp_relay_mode=quic&reduce_rtt=1&heartbeat=10000")
        assertEquals("bbr", tuic["congestion-controller"]?.jsonPrimitive?.content)
        assertEquals(true, tuic["reduce-rtt"]?.jsonPrimitive?.boolean)
        assertEquals(10000, tuic["heartbeat-interval"]?.jsonPrimitive?.int)
        val wg = config("wg://$key@[2001:db8::1]:51820?publickey=$key&address=10.0.0.2/32,fd00::2/128&reserved=1,2,3&keepalive=25")
        assertEquals("10.0.0.2", wg["ip"]?.jsonPrimitive?.content)
        assertEquals("fd00::2", wg["ipv6"]?.jsonPrimitive?.content)
        assertEquals(listOf(1, 2, 3), wg["reserved"]?.jsonArray?.map { it.jsonPrimitive.int })
        assertEquals(25, wg["persistent-keepalive"]?.jsonPrimitive?.int)
    }

    @Test fun acceptsHttpAndSocksDefaultPortsAndBase64Auth() {
        assertEquals(80, ShareLinkParser.parse("http://host")?.port)
        assertEquals(443, ShareLinkParser.parse("https://host")?.port)
        val auth = Base64.getEncoder().encodeToString("user:pa+ss".toByteArray())
        val p = requireNotNull(ShareLinkParser.parse("socks://$auth@host#SOCKS"))
        assertEquals(1080, p.port)
        assertEquals("user", p.username)
        assertEquals("pa+ss", p.password)
    }

    @Test fun invalidExtendedLinksAreRejected() {
        listOf("hy2://host", "tuic://host", "http://host:0", "socks5://host:65536", "wg://key@[::1]oops", "ssr://invalid").forEach {
            assertNull(it, ShareLinkParser.parse(it))
        }
    }

    @Test fun missingRequiredConnectionOptionsFailClearly() {
        listOf("hy://pass@host", "wg://key@host").forEach { link ->
            assertTrue(runCatching { config(link) }.exceptionOrNull() is IllegalArgumentException)
        }
    }

    @Test fun subscriptionListsIncludeAllNewSchemes() {
        val text = "hy2://pass@host#HY2\ntuic://token@host#TUIC\nhttp://host:8080#HTTP\nsocks5://host:1080#SOCKS"
        assertEquals(listOf(Protocol.HYSTERIA2, Protocol.TUIC, Protocol.HTTP, Protocol.SOCKS), ShareLinkParser.parseMany(text).map { it.protocol })
        assertEquals(4, ShareLinkParser.parseMany(Base64.getEncoder().encodeToString(text.toByteArray())).size)
    }

    @Test fun persistenceAndDeduplicationKeepDistinctCredentialsAndProtocolOptions() {
        val file = temp.newFile()
        val repository = ServerRepository(file)
        val p = requireNotNull(ShareLinkParser.parse("socks5://alice:secret@host:1080?tls=true&sni=proxy.example"))
        repository.addServers(listOf(p, p.copy(id = "other-user", username = "bob"),
            p.copy(id = "other-options", protocolOptions = mapOf("custom" to "value")),
            p.copy(id = "duplicate", name = "renamed", latencyMs = 20)))
        assertEquals(1, repository.dedupe())
        val reloaded = ServerRepository(file).store.value.servers
        assertEquals(3, reloaded.size)
        assertEquals(p, reloaded.first())
        assertEquals("bob", reloaded[1].username)
        assertEquals("value", reloaded[2].protocolOptions["custom"])
        assertEquals("proxy.example", ExtendedConfigBuilder.build(p)["name-cert-verify"]?.jsonPrimitive?.content)
    }
}
