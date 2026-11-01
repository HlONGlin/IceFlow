package com.iceflow.data

import com.iceflow.data.model.AppSettings
import com.iceflow.data.model.Protocol
import com.iceflow.data.model.ServerProfile
import com.iceflow.data.model.LocalGroup
import com.iceflow.data.model.ServerStore
import com.iceflow.data.model.LoadBalanceStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XrayConfigBuilderTest {

    private fun parse(s: String) = Json.parseToJsonElement(s).jsonObject
    private fun JsonObject.obj(k: String) = getValue(k).jsonObject
    private fun JsonObject.str(k: String) = getValue(k).jsonPrimitive.content

    private val vless = ServerProfile(
        name = "r", protocol = Protocol.VLESS, address = "1.2.3.4", port = 443,
        uuid = "uuid", flow = "xtls-rprx-vision", tls = "reality",
        sni = "www.microsoft.com", publicKey = "PBK", shortId = "ab",
    )

    @Test
    fun `vless reality outbound`() {
        val cfg = parse(XrayConfigBuilder.build(vless, AppSettings()))
        val out = cfg.getValue("outbounds").jsonArray[0].jsonObject
        assertEquals("proxy", out.str("tag"))
        assertEquals("vless", out.str("protocol"))
        val user = out.obj("settings").getValue("vnext").jsonArray[0].jsonObject
            .getValue("users").jsonArray[0].jsonObject
        assertEquals("xtls-rprx-vision", user.str("flow"))
        val stream = out.obj("streamSettings")
        assertEquals("reality", stream.str("security"))
        assertEquals("PBK", stream.obj("realitySettings").str("publicKey"))
        assertEquals("www.microsoft.com", stream.obj("realitySettings").str("serverName"))
    }

    @Test
    fun `least ping group emits balancer and observatory`() {
        val group = LocalGroup(id = "g", name = "Balanced", balanceStrategy = LoadBalanceStrategy.LEAST_PING, balanceTestUrl = "https://probe.example/check", balanceIntervalSeconds = 45)
        val members = listOf(vless.copy(id = "a", groupId = "g"), vless.copy(id = "b", groupId = "g"))
        val cfg = parse(XrayConfigBuilder.build(members.first(), AppSettings(), store = ServerStore(servers = members, localGroups = listOf(group))))
        val balancer = cfg.getValue("routing").jsonObject.getValue("balancers").jsonArray.single().jsonObject
        assertEquals("proxy", balancer.str("tag"))
        assertEquals("leastPing", balancer.obj("strategy").str("type"))
        assertEquals("45s", cfg.getValue("observatory").jsonObject.str("probeInterval"))
        assertEquals("https://probe.example/check", cfg.getValue("observatory").jsonObject.str("probeURL"))
        assertTrue(cfg.getValue("routing").jsonObject.getValue("rules").jsonArray.any { it.jsonObject["balancerTag"]?.jsonPrimitive?.content == "proxy" })
    }

    @Test
    fun `socks inbound uses configured port`() {
        val cfg = parse(XrayConfigBuilder.build(vless, AppSettings(socksPort = 12345)))
        val inbound = cfg.getValue("inbounds").jsonArray[0].jsonObject
        assertEquals("socks", inbound.str("protocol"))
        assertEquals("127.0.0.1", inbound.str("listen"))
        assertEquals(12345, inbound.getValue("port").jsonPrimitive.int)
        assertTrue(inbound.obj("settings").getValue("udp").jsonPrimitive.boolean)
    }

    @Test fun `http inbound and lan binding use credentials`() {
        val cfg = parse(XrayConfigBuilder.build(vless, AppSettings(httpEnabled = true, allowLan = true, socksUsername = "u", socksPassword = "p")))
        val inbound = cfg.getValue("inbounds").jsonArray.first { it.jsonObject.str("tag") == "http-in" }.jsonObject
        assertEquals("0.0.0.0", inbound.str("listen")); assertEquals(10809, inbound.getValue("port").jsonPrimitive.int)
        assertEquals("u", inbound.obj("settings").getValue("accounts").jsonArray.single().jsonObject.str("user"))
    }

    @Test
    fun `socks routing and udp are enabled by default`() {
        val settings = AppSettings()
        assertTrue(settings.socksEnabled)
        assertTrue(settings.socksUdp)

        val cfg = parse(XrayConfigBuilder.build(vless, settings))
        val inbound = cfg.getValue("inbounds").jsonArray[0].jsonObject
        assertTrue(inbound.obj("settings").getValue("udp").jsonPrimitive.boolean)
    }

    @Test
    fun `disabled socks routing omits inbound`() {
        val cfg = parse(XrayConfigBuilder.build(vless, AppSettings(socksEnabled = false)))
        assertFalse(cfg.containsKey("inbounds"))
    }

    @Test
    fun `measurement config has no inbound`() {
        val cfg = parse(XrayConfigBuilder.build(vless, AppSettings(), includeInbound = false))
        assertFalse(cfg.containsKey("inbounds"))
    }

    @Test fun `vpn forwarding survives disabling the public socks listener`() {
        val cfg = parse(XrayConfigBuilder.build(vless, AppSettings(socksEnabled = false, socksUdp = false), tunnelPort = 23456))
        val inbound = cfg.getValue("inbounds").jsonArray.single().jsonObject
        assertEquals("tun-in", inbound.str("tag"))
        assertEquals(23456, inbound.getValue("port").jsonPrimitive.int)
        assertEquals("noauth", inbound.obj("settings").str("auth"))
        assertTrue(inbound.obj("settings").getValue("udp").jsonPrimitive.boolean)
    }

    @Test fun `public socks credentials do not apply to vpn forwarding`() {
        val cfg = parse(XrayConfigBuilder.build(vless, AppSettings(socksUsername = "user", socksPassword = "secret"), tunnelPort = 23456))
        val inbounds = cfg.getValue("inbounds").jsonArray.map { it.jsonObject }
        assertEquals("noauth", inbounds.first { it.str("tag") == "tun-in" }.obj("settings").str("auth"))
        assertEquals("password", inbounds.first { it.str("tag") == "socks-in" }.obj("settings").str("auth"))
    }

    @Test
    fun `bypass lan toggles direct rule`() {
        val on = parse(XrayConfigBuilder.build(vless, AppSettings(bypassLan = true)))
        val off = parse(XrayConfigBuilder.build(vless, AppSettings(bypassLan = false)))
        assertEquals(3, on.obj("routing").getValue("rules").jsonArray.size)
        assertEquals(2, off.obj("routing").getValue("rules").jsonArray.size)
    }

    @Test
    fun `shadowsocks forces plain tcp`() {
        val ss = ServerProfile(
            name = "s", protocol = Protocol.SHADOWSOCKS, address = "h", port = 1,
            password = "p", security = "aes-128-gcm", network = "ws", tls = "tls",
        )
        val out = parse(XrayConfigBuilder.build(ss, AppSettings()))
            .getValue("outbounds").jsonArray[0].jsonObject
        val server = out.obj("settings").getValue("servers").jsonArray[0].jsonObject
        assertEquals("aes-128-gcm", server.str("method"))
        assertEquals("tcp", out.obj("streamSettings").str("network"))
        assertEquals("none", out.obj("streamSettings").str("security"))
    }

    @Test
    fun `vmess ws tls stream`() {
        val vm = ServerProfile(
            name = "v", protocol = Protocol.VMESS, address = "a.com", port = 443, uuid = "u",
            network = "ws", host = "cdn.com", path = "/ws", tls = "tls", alpn = "h2, http/1.1",
        )
        val stream = parse(XrayConfigBuilder.build(vm, AppSettings()))
            .getValue("outbounds").jsonArray[0].jsonObject.obj("streamSettings")
        assertEquals("/ws", stream.obj("wsSettings").str("path"))
        assertEquals("cdn.com", stream.obj("tlsSettings").str("serverName"))
        assertEquals(2, stream.obj("tlsSettings").getValue("alpn").jsonArray.size)
    }

    // 域匹配器缓存（MPH cache）要求：带域的规则必须有唯一的 ruleTag，
    // DNS 服务器必须有唯一的 tag，否则 xray-core 会因找不到匹配器而启动失败。
    @Test
    fun `domain rules carry a unique ruleTag`() {
        val cfg = parse(XrayConfigBuilder.build(vless, AppSettings()))
        val rules = cfg.obj("routing").getValue("rules").jsonArray.map { it.jsonObject }
        val tagged = rules.filter { it.containsKey("domain") }
        assertTrue(tagged.isNotEmpty())

        val tags = tagged.map { it.str("ruleTag") }
        assertTrue("带域的规则必须包含 ruleTag，实际为 $tags", tags.none { it.isBlank() })
        assertEquals("ruleTag 必须唯一，实际为 $tags", tags.size, tags.distinct().size)
    }

    @Test
    fun `dns servers carry a unique tag`() {
        val cfg = parse(XrayConfigBuilder.build(vless, AppSettings()))
        val servers = cfg.obj("dns").getValue("servers").jsonArray.map { it.jsonObject }
        val tags = servers.map { it.str("tag") }
        assertTrue("DNS 服务器必须包含 tag，实际为 $tags", tags.none { it.isBlank() })
        assertEquals("DNS tag 必须唯一，实际为 $tags", tags.size, tags.distinct().size)
    }

    @Test
    fun `ruleTag does not collide with dns tags`() {
        val cfg = parse(XrayConfigBuilder.build(vless, AppSettings()))
        val ruleTags = cfg.obj("routing").getValue("rules").jsonArray.map { it.jsonObject }
            .filter { it.containsKey("domain") }.map { it.str("ruleTag") }
        val dnsTags = cfg.obj("dns").getValue("servers").jsonArray.map { it.jsonObject.str("tag") }
        assertTrue(
            "ruleTag 与 DNS tag 冲突：${ruleTags.intersect(dnsTags.toSet())}",
            ruleTags.none { it in dnsTags },
        )
    }

    @Test
    fun `rules without domains do not need a ruleTag`() {
        val cfg = parse(XrayConfigBuilder.build(vless, AppSettings()))
        val rules = cfg.obj("routing").getValue("rules").jsonArray.map { it.jsonObject }
        assertTrue(rules.filter { !it.containsKey("domain") }.none { it.containsKey("ruleTag") })
    }
}
