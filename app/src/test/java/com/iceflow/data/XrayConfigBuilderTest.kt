package com.iceflow.data

import com.iceflow.data.model.AppSettings
import com.iceflow.data.model.Protocol
import com.iceflow.data.model.ServerProfile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
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
    fun `socks inbound uses configured port`() {
        val cfg = parse(XrayConfigBuilder.build(vless, AppSettings(socksPort = 12345)))
        val inbound = cfg.getValue("inbounds").jsonArray[0].jsonObject
        assertEquals("socks", inbound.str("protocol"))
        assertEquals("127.0.0.1", inbound.str("listen"))
        assertEquals(12345, inbound.getValue("port").jsonPrimitive.int)
    }

    @Test
    fun `measurement config has no inbound`() {
        val cfg = parse(XrayConfigBuilder.build(vless, AppSettings(), includeInbound = false))
        assertFalse(cfg.containsKey("inbounds"))
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
}
