package com.iceflow.data

import com.iceflow.data.model.Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class ShareLinkParserTest {

    private fun b64(s: String) = Base64.getEncoder().encodeToString(s.toByteArray())
    private fun b64Url(s: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(s.toByteArray())

    @Test
    fun `parse vmess ws tls`() {
        val body = """{"v":"2","ps":"HK 01","add":"hk.example.com","port":"443","id":"11111111-2222-3333-4444-555555555555","aid":"0","scy":"auto","net":"ws","type":"none","host":"cdn.example.com","path":"/ws","tls":"tls","sni":"hk.example.com"}"""
        val p = ShareLinkParser.parse("vmess://" + b64(body))
        assertNotNull(p)
        p!!
        assertEquals(Protocol.VMESS, p.protocol)
        assertEquals("HK 01", p.name)
        assertEquals("hk.example.com", p.address)
        assertEquals(443, p.port)
        assertEquals("ws", p.network)
        assertEquals("/ws", p.path)
        assertEquals("tls", p.tls)
    }

    @Test
    fun `parse vmess grpc maps path to serviceName`() {
        val body = """{"ps":"g","add":"a.com","port":443,"id":"u","net":"grpc","path":"svc","tls":"tls"}"""
        val p = ShareLinkParser.parse("vmess://" + b64(body))!!
        assertEquals("svc", p.serviceName)
        assertEquals("", p.path)
    }

    @Test
    fun `parse vless reality`() {
        val link = "vless://11111111-2222-3333-4444-555555555555@1.2.3.4:443" +
            "?encryption=none&flow=xtls-rprx-vision&security=reality&sni=www.microsoft.com" +
            "&fp=chrome&pbk=PUBKEY&sid=ab12&type=tcp#JP%20Reality"
        val p = ShareLinkParser.parse(link)!!
        assertEquals(Protocol.VLESS, p.protocol)
        assertEquals("JP Reality", p.name)
        assertEquals("1.2.3.4", p.address)
        assertEquals("xtls-rprx-vision", p.flow)
        assertEquals("reality", p.tls)
        assertEquals("PUBKEY", p.publicKey)
        assertEquals("ab12", p.shortId)
    }

    @Test
    fun `parse vless ipv6 host`() {
        val p = ShareLinkParser.parse("vless://uuid@[2001:db8::1]:8443?type=ws&path=%2Fx#v6")!!
        assertEquals("2001:db8::1", p.address)
        assertEquals(8443, p.port)
        assertEquals("/x", p.path)
    }

    @Test
    fun `parse trojan defaults to tls and keeps plus sign`() {
        val p = ShareLinkParser.parse("trojan://pa+ss@t.example.com:443?sni=t.example.com#TR")!!
        assertEquals(Protocol.TROJAN, p.protocol)
        assertEquals("pa+ss", p.password)
        assertEquals("tls", p.tls)
    }

    @Test
    fun `parse ss sip002`() {
        val link = "ss://" + b64Url("aes-256-gcm:secret") + "@5.6.7.8:8388#SS%20Node"
        val p = ShareLinkParser.parse(link)!!
        assertEquals(Protocol.SHADOWSOCKS, p.protocol)
        assertEquals("aes-256-gcm", p.security)
        assertEquals("secret", p.password)
        assertEquals(8388, p.port)
        assertEquals("SS Node", p.name)
    }

    @Test
    fun `parse ss 2022 plaintext userinfo`() {
        val p = ShareLinkParser.parse("ss://2022-blake3-aes-128-gcm:a%2Bb%3D@h.com:443#n")!!
        assertEquals("2022-blake3-aes-128-gcm", p.security)
        assertEquals("a+b=", p.password)
    }

    @Test
    fun `parse ss legacy full base64`() {
        val p = ShareLinkParser.parse("ss://" + b64("chacha20-ietf-poly1305:pw@9.9.9.9:1234") + "#old")!!
        assertEquals("chacha20-ietf-poly1305", p.security)
        assertEquals("pw", p.password)
        assertEquals("9.9.9.9", p.address)
    }

    @Test
    fun `invalid links return null`() {
        assertNull(ShareLinkParser.parse("unsupported://example.com"))
        assertNull(ShareLinkParser.parse("vless://nohost"))
        assertNull(ShareLinkParser.parse("vless://u@host:99999"))
        assertNull(ShareLinkParser.parse("vmess://!!!notbase64"))
    }

    @Test
    fun `parseMany handles base64 subscription`() {
        val lines = listOf(
            "trojan://p@a.com:443#A",
            "garbage line",
            "vless://u@b.com:443#B",
        ).joinToString("\n")
        val result = ShareLinkParser.parseMany(b64(lines))
        assertEquals(listOf("A", "B"), result.map { it.name })
        assertTrue(ShareLinkParser.parseMany(lines).size == 2)
    }


    @Test
    fun `long v2rayN reality link preserves encoded remark in clipboard import`() {
        val link = "vless://11111111-2222-3333-4444-555555555555@192.0.2.60:57620" +
            "?encryption=none&security=reality&sni=www.amazon.com&fp=chrome" +
            "&pbk=public-key&sid=d1e776a8&spx=%2F&pqv=" + "Ab9_-".repeat(700) +
            "&type=tcp&headerType=none#%E9%A6%99%E6%B8%AF1%28le%29"
        for (input in listOf(link, b64(link))) {
            val p = ShareLinkParser.parseMany(input).single()
            assertEquals("香港1(le)", p.name)
            assertEquals("www.amazon.com", p.sni)
            assertEquals("192.0.2.60", p.address)
            assertEquals("reality", p.tls)
            assertEquals("public-key", p.publicKey)
            assertEquals("/", p.spiderX)
        }
    }

    @Test
    fun `clipboard import keeps remark on continuation line`() {
        val link = "vless://u@192.0.2.60:57620?security=reality&sni=www.amazon.com" +
            "\r\n&pbk=public-key&sid=d1e776a8\r\n#%E9%A6%99%E6%B8%AF1%28le%29"
        for (input in listOf(link, b64(link))) {
            val p = ShareLinkParser.parseMany(input).single()
            assertEquals("香港1(le)", p.name)
            assertEquals("public-key", p.publicKey)
            assertEquals("www.amazon.com", p.sni)
        }
    }

    @Test
    fun `continuation handling preserves independent links and ignores unrelated lines`() {
        val text = "vless://u@192.0.2.1:443?sni=tls.example.com\n#First\n" +
            "trojan://p@192.0.2.2:443?peer=peer.example.com#Second\n" +
            "# unrelated line\n&unused=value\ngarbage line\n" +
            "vless://u@192.0.2.3:443?sni=third.example.com#Third%2BNode"
        val profiles = ShareLinkParser.parseMany(text)
        assertEquals(listOf("First", "Second", "Third+Node"), profiles.map { it.name })
        assertEquals(listOf("tls.example.com", "peer.example.com", "third.example.com"), profiles.map { it.sni })
    }

    @Test
    fun `unnamed vmess never uses tls or transport host as name`() {
        for (remark in listOf("", "   ")) {
            val body = """{"ps":"$remark","add":"192.0.2.1","port":443,"id":"u","host":"cdn.example.com","sni":"tls.example.com"}"""
            val p = ShareLinkParser.parse("vmess://" + b64(body))!!
            assertEquals("vmess-192.0.2.1:443", p.name)
            assertEquals("cdn.example.com", p.host)
            assertEquals("tls.example.com", p.sni)
        }
    }

    @Test
    fun `unnamed standard links keep name independent from sni peer and host`() {
        for (scheme in listOf("vless", "trojan")) {
            for (tlsName in listOf("sni", "peer")) {
                for (remark in listOf("", "#%20%20")) {
                    val p = ShareLinkParser.parse("$scheme://u@[2001:db8::1]:443?$tlsName=tls.example.com&host=cdn.example.com$remark")!!
                    assertEquals("$scheme-[2001:db8::1]:443", p.name)
                    assertEquals("tls.example.com", p.sni)
                    assertEquals("cdn.example.com", p.host)
                }
            }
            val p = ShareLinkParser.parse("$scheme://u@node.example.com:443?sni=tls.example.com")!!
            assertEquals("node.example.com", p.name)
        }
    }

    @Test
    fun `encode round trips supported protocols`() {
        val profiles = listOf(
            ShareLinkParser.parse("vmess://${b64("""{"ps":"vm","add":"vm.example.com","port":"443","id":"uuid","net":"ws","path":"/x","tls":"tls"}""")}")!!,
            ShareLinkParser.parse("vless://user@vless.example.com:443?type=ws&path=%2Fx&security=reality&pbk=pub&sid=12#vless")!!,
            ShareLinkParser.parse("trojan://pass@trojan.example.com:443?sni=trojan.example.com#trojan")!!,
            ShareLinkParser.parse("ss://${b64Url("aes-256-gcm:secret")}@ss.example.com:8388#ss")!!,
        )

        profiles.forEach { original ->
            val parsed = ShareLinkParser.parse(ShareLinkParser.encode(original))
            assertNotNull(parsed)
            assertEquals(original.protocol, parsed?.protocol)
            assertEquals(original.name, parsed?.name)
            assertEquals(original.address, parsed?.address)
            assertEquals(original.port, parsed?.port)
            assertEquals(original.uuid, parsed?.uuid)
            assertEquals(original.password, parsed?.password)
        }
    }
}
