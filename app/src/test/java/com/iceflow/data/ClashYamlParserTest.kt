package com.iceflow.data

import com.iceflow.data.model.AppSettings
import com.iceflow.data.model.Protocol
import com.iceflow.data.model.ServerProfile
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class ClashYamlParserTest {
    @Test fun fullConfigKeepsNestedTransportsAndIgnoresRoutingGroups() {
        val yaml = """
            mixed-port: 7890
            proxies:
              - name: '香港 01 # WS'
                type: vmess
                server: proxy.example.com
                port: 443
                uuid: b831381d-6324-4d53-ad4f-8cda48b30811
                alterId: 0
                cipher: auto
                tls: true
                network: ws
                ws-opts:
                  path: /ws?ed=2048
                  headers: {Host: cdn.example.com}
                  max-early-data: 2048
                smux: {enabled: true, protocol: h2mux, max-connections: 4}
            proxy-groups:
              - {name: 自动选择, type: select, proxies: ['香港 01 # WS']}
            rules: ['MATCH,DIRECT']
        """.trimIndent()
        val p = ShareLinkParser.parseMany(yaml).single()
        assertEquals("香港 01 # WS", p.name); assertEquals(Protocol.VMESS, p.protocol)
        assertEquals("/ws?ed=2048", p.path); assertEquals("cdn.example.com", p.host)
        val config = Json.parseToJsonElement(XrayConfigBuilder.build(p, AppSettings(), false)).jsonObject
        val adapter = config.getValue("iceflowAdapter").jsonObject
        assertEquals(2048, adapter.getValue("ws-opts").jsonObject.getValue("max-early-data").jsonPrimitive.int)
        assertEquals(true, adapter.getValue("smux").jsonObject.getValue("enabled").jsonPrimitive.boolean)
        assertEquals("blackhole", config.getValue("outbounds").jsonArray[0].jsonObject.getValue("protocol").jsonPrimitive.content)
    }

    @Test fun mapsAllTwelveSupportedProtocolTypesWithoutLosingOptions() {
        val types = listOf("vmess", "vless", "trojan", "ss", "ssr", "hysteria", "hysteria2", "tuic", "wireguard", "http", "socks5", "mieru")
        val yaml = "proxies:\n" + types.joinToString("\n") {
            "  - {name: $it, type: $it, server: '::1', port: 443, password: secret, username: user, custom-option: {enabled: true}}"
        }
        val nodes = ShareLinkParser.parseMany(yaml)
        assertEquals(Protocol.entries.toSet(), nodes.map { it.protocol }.toSet())
        for (p in nodes) {
            val restored = Json.decodeFromString<ServerProfile>(Json.encodeToString(p))
            assertEquals(p, restored)
            assertEquals(p.clashConfig, ShareLinkParser.parseMany(ShareLinkParser.encode(p)).single().clashConfig)
        }
    }

    @Test fun supportsProviderPayloadAndYamlMergeAnchors() {
        val nodes = ShareLinkParser.parseMany("""
            defaults: &defaults
              type: socks5
              server: 127.0.0.1
              port: 1080
              udp: true
            proxies:
              - <<: *defaults
                name: 一号
              - <<: *defaults
                name: 二号
                port: 1081
        """.trimIndent())
        assertEquals(listOf(1080, 1081), nodes.map { it.port })
        assertEquals(listOf("一号", "二号"), nodes.map { it.name })
        assertEquals(2, ShareLinkParser.parseMany(Base64.getEncoder().encodeToString("proxies: [{name: a, type: http, server: localhost, port: 80}, {name: b, type: socks5, server: localhost, port: 1080}]".toByteArray())).size)
    }

    @Test fun listFormBomQuotedSecretsAndRenamesRoundTrip() {
        val p = ShareLinkParser.parseMany("\uFEFF- {name: 测试, type: http, server: localhost, port: '8080', username: '001', password: 'true:# + 密码'}").single()
        assertEquals("true:# + 密码", p.password); assertEquals("001", p.username)
        val renamed = p.copy(name = "修改后的名称")
        val restored = ShareLinkParser.parseMany(ShareLinkParser.encode(renamed)).single()
        assertEquals(renamed.name, restored.name); assertEquals(p.password, restored.password)
    }

    @Test fun rejectsMalformedUnsafeAndChainedConfigurations() {
        val invalid = listOf(
            "proxies: [",
            "proxies: [{type: http, server: localhost, port: 0}]",
            "proxies: [{type: http, server: localhost, port: 80, port: 90}]",
            "proxies: [!!java.net.URL ['http://example.com']]",
            "proxies: [{type: http, server: localhost, port: 80, dialer-proxy: other}]",
            "proxy-providers: {remote: {type: http, url: 'https://example.com'}}",
        )
        invalid.forEach { assertTrue(it, runCatching { ShareLinkParser.parseMany(it) }.isFailure) }
    }

    @Test fun unsupportedTypesDoNotBecomeFakeNodesAndLinksStillImport() {
        val nodes = ShareLinkParser.parseMany("proxies: [{name: reject, type: reject}, {name: unsupported, type: unknown}, {name: ok, type: http, server: localhost, port: 80}]")
        assertEquals(listOf("ok"), nodes.map { it.name })
        val mixed = ShareLinkParser.encode(nodes.single()) + "\n---\nhttp://user:secret@localhost:8080#link"
        assertEquals(listOf("ok", "link"), ShareLinkParser.parseMany(mixed).map { it.name })
    }
}
