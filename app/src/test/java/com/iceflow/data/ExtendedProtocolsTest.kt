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
            "anytls://pa%3Ass@server.example:443?sni=cdn.example&alpn=h2&insecure=1#AnyTLS",
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
        val anytls = config("anytls://pa%3Ass@host:443?sni=cdn.example&alpn=h2&insecure=1")
        assertEquals("anytls", anytls["type"]?.jsonPrimitive?.content)
        assertEquals("pa:ss", anytls["password"]?.jsonPrimitive?.content)
        assertEquals("cdn.example", anytls["sni"]?.jsonPrimitive?.content)
        assertEquals(listOf("h2"), anytls["alpn"]?.jsonArray?.map { it.jsonPrimitive.content })
        assertEquals(true, anytls["skip-cert-verify"]?.jsonPrimitive?.boolean)
    }

    @Test fun anyTlsGenericSecurityAliasesAreConsumed() {
        listOf(
            "security=tls&allowInsecure=1" to true,
            "security=tls&skip-cert-verify=true" to true,
            "security=tls&tls=true" to false,
        ).forEach { (suffix, insecure) ->
            val parsed = requireNotNull(ShareLinkParser.parse("anytls://pass@host:443?$suffix"))
            assertEquals("tls", parsed.tls)
            assertEquals(insecure, parsed.allowInsecure)
            assertTrue(parsed.protocolOptions.keys.none { it in setOf("security", "tls", "allowInsecure", "skip-cert-verify") })
        }
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

    @Test fun anyTlsFingerprintAliasesAndCertificatePinsRemainDistinct() {
        listOf("fp", "client-fingerprint", "clientFingerprint").forEach { alias ->
            val parsed = requireNotNull(ShareLinkParser.parse("anytls://pass@host?$alias=chrome&fingerprint=certificate-pin"))
            val restored = requireNotNull(ShareLinkParser.parse(ShareLinkParser.encodeV2rayN(parsed)))
            assertEquals("chrome", parsed.fingerprint)
            assertEquals("chrome", restored.fingerprint)
            val options = ExtendedConfigBuilder.build(restored)
            assertEquals("chrome", options.getValue("client-fingerprint").jsonPrimitive.content)
            assertEquals("certificate-pin", options.getValue("fingerprint").jsonPrimitive.content)

            val cleared = parsed.copy(fingerprint = "")
            assertFalse(ExtendedConfigBuilder.build(cleared).containsKey("client-fingerprint"))
            assertEquals("", requireNotNull(ShareLinkParser.parse(ShareLinkParser.encodeV2rayN(cleared))).fingerprint)
        }
    }

    @Test fun anyTlsManualAndEditedFingerprintsSurviveSharing() {
        val manual = NodeEditing.blank(Protocol.ANYTLS).copy(name = "AnyTLS", address = "host", password = "pass", fingerprint = "firefox")
        val edited = requireNotNull(ShareLinkParser.parse("anytls://pass@host?fp=chrome")).copy(
            fingerprint = "firefox", protocolOptions = mapOf("client-fingerprint" to "chrome")
        )
        listOf(manual, edited).forEach { node ->
            val shared = ShareLinkParser.encodeV2rayN(node)
            val restored = requireNotNull(ShareLinkParser.parse(shared))
            assertTrue(shared.contains("fp=firefox"))
            assertEquals("firefox", restored.fingerprint)
            assertEquals("firefox", ExtendedConfigBuilder.build(node).getValue("client-fingerprint").jsonPrimitive.content)
            assertEquals(ExtendedConfigBuilder.build(node), ExtendedConfigBuilder.build(restored))
        }
    }

    @Test fun importedAnyTlsSharesSessionAndSecurityOptions() {
        val original = ShareLinkParser.parseMany("""proxies: [{name: AnyTLS, type: anytls, server: proxy.example, port: 443, password: secret, sni: cdn.example, alpn: [h2], udp: true, skip-cert-verify: false, client-fingerprint: chrome, fingerprint: certificate-pin, client-metadata: iceflow, idle-session-check-interval: 15, idle-session-timeout: 30, min-idle-session: 2, disable-reuse: true, ech-opts: {enable: true, config: test-config}}]""").single()
        val shared = ShareLinkParser.encodeV2rayN(original)
        assertTrue(shared.startsWith("anytls://"))
        val restored = requireNotNull(ShareLinkParser.parse(shared))
        assertEquals(original.name, restored.name)
        assertEquals("chrome", restored.fingerprint)
        assertEquals(
            JsonObject(original.clashConfig!! + ("name" to JsonPrimitive("iceflow-proxy"))),
            ExtendedConfigBuilder.build(restored),
        )
    }

    @Test fun importedAnyTlsFallsBackToClashWhenUriWouldLoseOptions() {
        listOf("certificate: custom-certificate", "udp: false", "custom-option: {nested: value}").forEach { option ->
            val original = ShareLinkParser.parseMany("""proxies: [{name: AnyTLS, type: anytls, server: proxy.example, port: 443, password: secret, fingerprint: certificate-pin, $option}]""").single()
            val shared = ShareLinkParser.encodeV2rayN(original)
            val restored = ShareLinkParser.parseMany(shared).single()
            assertEquals(original.clashConfig, restored.clashConfig)
            assertEquals("", restored.fingerprint)
        }
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
        assertTrue(runCatching { NodeEditing.validate(NodeEditing.blank(Protocol.ANYTLS).copy(name = "AnyTLS", address = "host")) }.isFailure)
        assertTrue(runCatching { NodeEditing.validate(NodeEditing.blank(Protocol.SUDOKU).copy(name = "Sudoku", address = "host")) }.isFailure)
    }

    @Test fun sudokuUsesClashJsonAndBuildsNativeOptions() {
        val node = NodeEditing.blank(Protocol.SUDOKU).copy(
            name = "Sudoku",
            address = "proxy.example",
            port = 443,
            password = key,
            protocolOptions = mapOf(
                "aead-method" to "chacha20-poly1305",
                "padding-min" to "4",
                "padding-max" to "12",
                "table-type" to "prefer_ascii",
                "http-mask" to "true",
                "custom-tables" to "xpxvvpvv,vpxpvvpx",
            ),
        )
        val options = ExtendedConfigBuilder.build(NodeEditing.validate(node))
        assertEquals("sudoku", options["type"]?.jsonPrimitive?.content)
        assertEquals(key, options["key"]?.jsonPrimitive?.content)
        assertEquals(4, options["padding-min"]?.jsonPrimitive?.int)
        assertEquals(12, options["padding-max"]?.jsonPrimitive?.int)
        assertEquals(true, options["http-mask"]?.jsonPrimitive?.boolean)
        assertEquals(listOf("xpxvvpvv", "vpxpvvpx"), options["custom-tables"]?.jsonArray?.map { it.jsonPrimitive.content })

        val restored = ShareLinkParser.parseMany(ShareLinkParser.encode(node)).single()
        assertEquals(Protocol.SUDOKU, restored.protocol)
        assertEquals(node.password, restored.password)
        assertEquals(node.name, restored.name)
        assertEquals(JsonObject(options + ("name" to JsonPrimitive(node.name))), ClashYamlParser.config(restored))
    }

    @Test fun importedSudokuKeyEditsReachSharingAndRuntime() {
        val original = ShareLinkParser.parseMany("""proxies: [{name: Sudoku, type: sudoku, server: proxy.example, port: 443, key: old-key, httpmask: {mode: stream, host: cdn.example}}]""").single()
        val edited = NodeEditing.validate(NodeEditing.syncClash(original, original.copy(password = uuid)))
        val restored = ShareLinkParser.parseMany(ShareLinkParser.encode(edited)).single()
        val runtime = Json.parseToJsonElement(XrayConfigBuilder.build(edited, AppSettings())).jsonObject
            .getValue("iceflowAdapter").jsonObject

        assertEquals(uuid, edited.password)
        assertEquals(uuid, restored.password)
        assertEquals(uuid, runtime.getValue("key").jsonPrimitive.content)
        assertEquals(original.clashConfig!!.getValue("httpmask"), runtime.getValue("httpmask"))
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
