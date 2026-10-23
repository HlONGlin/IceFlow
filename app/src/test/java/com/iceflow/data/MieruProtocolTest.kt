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

class MieruProtocolTest {
    @Test fun fragmentRemarkOverridesDefaultProfileName() {
        // Same format as subscription providers: profile is not the display remark.
        val base = "mierus://test-user:test-password@proxy.example.com?handshake-mode=HANDSHAKE_NO_WAIT&mtu=1400&multiplexing=MULTIPLEXING_OFF&port=40980&profile=default&protocol=TCP&traffic-pattern=CCo%3D"
        for (remark in listOf("香港1(go)", "%E9%A6%99%E6%B8%AF1%28go%29")) {
            val p = requireNotNull(ShareLinkParser.parse("$base#$remark"))
            assertEquals("香港1(go)", p.name)
            assertEquals(Protocol.MIERU, p.protocol)
            assertEquals("CCo=", p.protocolOptions["traffic-pattern"])
            assertEquals(40980, p.port)
            assertEquals("香港1(go)", ShareLinkParser.parseMany("$base#$remark").single().name)
            val encoded = ShareLinkParser.encode(p)
            assertTrue(encoded.contains("#%E9%A6%99%E6%B8%AF1%28go%29"))
            val restored = requireNotNull(ShareLinkParser.parse(encoded))
            assertEquals(p.copy(id = restored.id), restored)
        }
        assertEquals("default", ShareLinkParser.parse(base)?.name)
        assertEquals("default", ShareLinkParser.parse("$base#")?.name)
        assertEquals("香港+1", ShareLinkParser.parse("$base#香港+1")?.name)
    }

    private val link = "mierus://user%2Bname:p%40ss%3Aword@[2001:db8::1]?profile=mieru+%E4%B8%AD%E6%96%87&port=12345-12347&protocol=UDP&multiplexing=MULTIPLEXING_HIGH&handshake-mode=HANDSHAKE_NO_WAIT&mtu=1500"

    @Test fun officialSimpleLinkRoundTripAndPersistence() {
        val p = requireNotNull(ShareLinkParser.parse(link))
        assertEquals(Protocol.MIERU, p.protocol); assertEquals("mieru 中文", p.name)
        assertEquals("user+name", p.username); assertEquals("p@ss:word", p.password)
        assertEquals("2001:db8::1", p.address); assertEquals(12345, p.port)
        val restored = requireNotNull(ShareLinkParser.parse(ShareLinkParser.encode(p)))
        assertEquals(p.copy(id = restored.id), restored)
        assertEquals(p, Json.decodeFromString<ServerProfile>(Json.encodeToString(p)))
    }

    @Test fun nativeConfigRetainsTransportAndProfileOptions() {
        val p = requireNotNull(ShareLinkParser.parse(link))
        val config = Json.parseToJsonElement(XrayConfigBuilder.build(p, AppSettings(), includeInbound = false)).jsonObject
        val adapter = config.getValue("iceflowAdapter").jsonObject
        assertEquals("mieru", adapter.getValue("type").jsonPrimitive.content)
        assertEquals("UDP", adapter.getValue("transport").jsonPrimitive.content)
        assertEquals("12345-12347", adapter.getValue("port-range").jsonPrimitive.content)
        assertFalse(adapter.containsKey("port"))
        assertEquals(1500, adapter.getValue("mtu").jsonPrimitive.int)
        assertEquals("HANDSHAKE_NO_WAIT", adapter.getValue("handshake-mode").jsonPrimitive.content)
        assertEquals("MULTIPLEXING_HIGH", adapter.getValue("multiplexing").jsonPrimitive.content)
    }

    @Test fun multipleBindingsAndBase64SubscriptionsImportAllNodes() {
        val multi = "mierus://user:secret@proxy.example.com?profile=Multi&port=12345&protocol=TCP&port=20000-20010&protocol=UDP"
        val text = multi + "\n" + "ss://YWVzLTEyOC1nY206c2VjcmV0@127.0.0.1:8388#SS"
        val nodes = ShareLinkParser.parseMany(Base64.getEncoder().encodeToString(text.toByteArray()))
        assertEquals(3, nodes.size); assertEquals(setOf("TCP", "UDP"), nodes.take(2).map { it.protocolOptions["transport"] }.toSet())
        assertEquals(listOf(12345, 20000), nodes.take(2).map { it.port })
        assertEquals("20000-20010", nodes[1].protocolOptions["port-range"])
        nodes.forEach { p -> assertNotNull(ShareLinkParser.parse(ShareLinkParser.encode(p))) }
    }

    @Test fun rejectsMalformedLinksAndInvalidNativeOptions() {
        for (bad in listOf(
            "mierus://user@host?profile=X&port=1234&protocol=TCP",
            "mierus://user:@host?profile=X&port=1234&protocol=TCP",
            "mierus://user:pass@host?port=1234&protocol=TCP",
            "mierus://user:pass@host?profile=X&port=1234",
            "mierus://user:pass@host?profile=X&port=0&protocol=TCP",
            "mierus://user:pass@host?profile=X&port=2000-1000&protocol=UDP",
            "mierus://user:pass@host?profile=X&port=1234&protocol=QUIC",
            "mierus://user:pass@host?profile=X&port=1234&protocol=TCP&port=5678",
            "mierus://user:pass@host?profile=X&port=1234&protocol=TCP&mtu=9999",
            "mierus://user:pass@host?profile=X&port=1234&protocol=TCP&multiplexing=INVALID",
            "mierus://user:pass@host?profile=X&port=1234&protocol=TCP&handshake-mode=INVALID"
        )) assertNull(bad, ShareLinkParser.parse(bad))
        val p = requireNotNull(ShareLinkParser.parse(link))
        for (options in listOf(mapOf("multiplexing" to "invalid"), mapOf("handshake-mode" to "invalid"), mapOf("mtu" to "2000"), mapOf("transport" to "QUIC"))) {
            assertThrows(IllegalArgumentException::class.java) { ExtendedConfigBuilder.build(p.copy(protocolOptions = options)) }
        }
        assertThrows(IllegalArgumentException::class.java) { ExtendedConfigBuilder.build(p.copy(username = "")) }
    }
}
