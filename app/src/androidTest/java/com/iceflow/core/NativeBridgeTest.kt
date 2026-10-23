package com.iceflow.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iceflow.data.XrayConfigBuilder
import com.iceflow.data.ShareLinkParser
import com.iceflow.data.model.AppSettings
import com.iceflow.data.model.Protocol
import com.iceflow.data.model.ServerProfile
import com.iceflow.data.model.ServerStore
import com.iceflow.data.model.LocalGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.io.FileDescriptor
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * 在真机/模拟器上验证原生层：libiceflow_core.so（Rust）与 libgojni.so（xray-core）。
 * 不建立 VPN、不连接任何外部服务器。
 */
@RunWith(AndroidJUnit4::class)
class NativeBridgeTest {

    private val uuid = "b831381d-6324-4d53-ad4f-8cda48b30811"

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

    @Test fun chinaDomainAndIpDatabasesActuallySelectDirectOutbound() {
        com.iceflow.data.repository.RoutingResources(InstrumentationRegistry.getInstrumentation().targetContext).prepare()
        ServerSocket(0, 2, InetAddress.getByName("127.0.0.1")).use { proxy ->
            proxy.soTimeout = 8000
            val seen = java.util.Collections.synchronizedList(mutableListOf<String>())
            val failure = AtomicReference<Throwable?>()
            val worker = thread { try { repeat(2) { proxy.accept().use { socket ->
                socket.soTimeout = 5000
                fun header(): String {
                    val bytes = java.io.ByteArrayOutputStream()
                    while (bytes.size() < 8192) { val b = socket.getInputStream().read(); check(b >= 0); bytes.write(b); if (bytes.toString("UTF-8").endsWith("\r\n\r\n")) return bytes.toString("UTF-8") }
                    error("Oversized header")
                }
                seen.add(header().lineSequence().first().split(' ')[1])
                socket.getOutputStream().write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
                assertTrue(header().startsWith("GET /route-check"))
                socket.getOutputStream().write("HTTP/1.1 204 No Content\r\nConnection: close\r\n\r\n".toByteArray())
            } } } catch (error: Throwable) { failure.set(error) } }
            val port = freePort()
            // Only the outbound selected by a CN rule can reach this local fixture.
            val config = """{"inbounds":[{"listen":"127.0.0.1","port":$port,"protocol":"socks","settings":{"auth":"noauth"}}],"outbounds":[{"tag":"proxy","protocol":"blackhole"},{"tag":"direct","protocol":"http","settings":{"servers":[{"address":"127.0.0.1","port":${proxy.localPort}}]}}],"routing":{"domainStrategy":"IPIfNonMatch","rules":[{"type":"field","domain":["geosite:cn"],"outboundTag":"direct"},{"type":"field","ip":["geoip:cn"],"outboundTag":"direct"}]}}"""
            try {
                XrayBridge.start(config)
                assertTrue(ConnectionProbe.measure(port, "http://baidu.com/route-check", 3000) > 0)
                assertTrue(ConnectionProbe.measure(port, "http://223.5.5.5/route-check", 3000) > 0)
                worker.join(6000); failure.get()?.let { throw AssertionError(it) }
                assertEquals(listOf("baidu.com:80", "223.5.5.5:80"), seen.toList())
                assertFalse(worker.isAlive)
            } finally { XrayBridge.stop(); proxy.close(); worker.join(2000) }
        }
    }

    @Test fun routingRulesActuallyBlockBypassAndHonorDisabledState() {
        val rule = com.iceflow.data.model.RoutingRule(name = "Local route", action = com.iceflow.data.model.RouteAction.DIRECT, ips = listOf("127.0.0.1/32"))
        val unreachable = ServerProfile(name = "Unreachable", protocol = Protocol.HTTP, address = "127.0.0.1", port = freePort())
        val socksPort = freePort()
        ServerSocket(0, 2, InetAddress.getByName("127.0.0.1")).use { target ->
            target.soTimeout = 7000
            val failure = AtomicReference<Throwable?>()
            val worker = thread {
                try { target.accept().use { socket ->
                    socket.soTimeout = 5000
                    val reader = socket.getInputStream().bufferedReader()
                    assertTrue(reader.readLine().startsWith("GET /custom-check"))
                    while (!reader.readLine().isNullOrEmpty()) {}
                    socket.getOutputStream().write("HTTP/1.1 204 No Content\r\nConnection: close\r\n\r\n".toByteArray())
                } } catch (error: Throwable) { failure.set(error) }
            }
            val url = "http://127.0.0.1:${target.localPort}/custom-check"
            fun start(rules: List<com.iceflow.data.model.RoutingRule>) = XrayBridge.start(XrayConfigBuilder.build(unreachable, AppSettings(socksPort = socksPort, bypassLan = false, routingRules = rules)))
            try {
                val block = rule.copy(id = "block", action = com.iceflow.data.model.RouteAction.BLOCK)
                start(listOf(block, rule))
                assertTrue(runCatching { ConnectionProbe.measure(socksPort, url, 1500) }.isFailure)
                start(listOf(block.copy(enabled = false), rule))
                assertTrue(ConnectionProbe.measure(socksPort, url, 3000) > 0)
                worker.join(5000); failure.get()?.let { throw AssertionError(it) }; assertFalse(worker.isAlive)
                // Removing the direct rule must expose the unavailable proxy, never direct fallback.
                start(emptyList())
                assertTrue(runCatching { ConnectionProbe.measure(socksPort, url, 1500) }.isFailure)
            } finally { XrayBridge.stop(); target.close(); worker.join(2000) }
        }
    }

    @Test fun packagedRoutingResourcesValidateAndMalformedUpdatesAreRejected() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        com.iceflow.data.repository.RoutingResources(context).prepare()
        for (name in listOf("geosite.dat", "geoip.dat")) {
            XrayBridge.validateRoutingAsset(java.io.File(context.filesDir, name).absolutePath, name)
            val invalid = java.io.File(context.cacheDir, "invalid-$name")
            try {
                invalid.writeText("<html>not a database</html>")
                assertTrue(runCatching { XrayBridge.validateRoutingAsset(invalid.absolutePath, name) }.isFailure)
            } finally { invalid.delete() }
        }
    }

    @Test fun groupChainForwardsThroughFrontMiddleAndLandingOnAndroid() {
        val listeners = List(3) { ServerSocket(0, 4, InetAddress.getByName("127.0.0.1")).apply { soTimeout = 6000 } }
        val seen = java.util.Collections.synchronizedList(mutableListOf<String>())
        val failure = AtomicReference<Throwable?>()
        val workers = listeners.mapIndexed { index, listener -> thread {
            try {
                listener.accept().use { socket ->
                    socket.soTimeout = 6000
                    // Read only the header bytes; buffering here could swallow the next hop's handshake.
                    fun header(): String {
                        val data = java.io.ByteArrayOutputStream()
                        while (data.size() < 8192) {
                            val value = socket.getInputStream().read(); check(value >= 0); data.write(value)
                            if (data.size() >= 4 && data.toString("UTF-8").endsWith("\r\n\r\n")) return data.toString("UTF-8")
                        }
                        error("Oversized CONNECT header")
                    }
                    val connect = header().lineSequence().first()
                    assertTrue(connect.startsWith("CONNECT "))
                    val destination = connect.split(' ')[1]
                    seen.add("$index:$destination")
                    socket.getOutputStream().write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
                    if (index == 2) {
                        assertTrue(header().startsWith("GET /generate_204"))
                        socket.getOutputStream().write("HTTP/1.1 204 No Content\r\nConnection: close\r\n\r\n".toByteArray())
                    } else {
                        Socket("127.0.0.1", listeners[index + 1].localPort).use { next ->
                            next.soTimeout = 6000
                            val outbound = thread(isDaemon = true) { runCatching { socket.getInputStream().copyTo(next.getOutputStream()); next.shutdownOutput() } }
                            next.getInputStream().copyTo(socket.getOutputStream())
                            socket.shutdownOutput()
                            outbound.join(1000)
                        }
                    }
                }
            } catch (error: Throwable) { failure.compareAndSet(null, error) }
        } }
        try {
            val nodes = listeners.mapIndexed { index, listener -> ServerProfile(id = "hop-$index", name = "Hop $index", protocol = Protocol.HTTP, address = "127.0.0.1", port = listener.localPort, groupId = if (index == 1) "chain-group" else null) }
            val store = ServerStore(servers = nodes, localGroups = listOf(LocalGroup(id = "chain-group", name = "Chain", frontNodeId = nodes[0].id, landingNodeId = nodes[2].id)))
            val config = XrayConfigBuilder.build(nodes[1], AppSettings(), includeInbound = false, store = store)
            assertTrue(XrayBridge.measureDelay(config, "http://chain.invalid/generate_204", 5000) > 0)
            workers.forEach { it.join(7000) }
            failure.get()?.let { throw AssertionError("Group chain failed", it) }
            assertEquals(listOf("0:127.0.0.1:${listeners[1].localPort}", "1:127.0.0.1:${listeners[2].localPort}", "2:chain.invalid:80"), seen.toList())
            listeners[0].close()
            try { XrayBridge.measureDelay(config, "http://chain.invalid/generate_204", 500); fail("Unavailable front node must fail closed") } catch (_: Exception) {}
        } finally { listeners.forEach { it.close() }; XrayBridge.stop() }
    }

    @Test
    fun tunPacketCountersIncludeShortBurstsInBothDirectionsAndReset() {
        val tun = FileDescriptor()
        val peer = FileDescriptor()
        Os.socketpair(OsConstants.AF_UNIX, OsConstants.SOCK_DGRAM, 0, tun, peer)
        try {
            repeat(2) {
                ParcelFileDescriptor.dup(tun).use { descriptor ->
                    assertEquals(0, RustBridge.startTun(descriptor.fd, 1500, "socks5://127.0.0.1:10808", RustBridge.DNS_VIRTUAL, "1.1.1.1"))
                    try {
                        assertEquals(listOf(0L, 0L), RustBridge.stats().toList())
                        val dns = "123401000001000000000000076578616d706c6503636f6d0000010001"
                            .chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                        val packet = ByteArray(28 + dns.size)
                        fun word(offset: Int, value: Int) { packet[offset] = (value shr 8).toByte(); packet[offset + 1] = value.toByte() }
                        packet[0] = 0x45; word(2, packet.size); packet[8] = 64; packet[9] = 17
                        byteArrayOf(172.toByte(), 19, 0, 2, 1, 1, 1, 1).copyInto(packet, 12)
                        word(20, 12345); word(22, 53); word(24, 8 + dns.size)
                        dns.copyInto(packet, 28)
                        var checksum = 0
                        for (i in 0 until 20 step 2) checksum += ((packet[i].toInt() and 255) shl 8) or (packet[i + 1].toInt() and 255)
                        while (checksum shr 16 != 0) checksum = (checksum and 65535) + (checksum shr 16)
                        word(10, checksum.inv() and 65535)
                        Os.write(peer, packet, 0, packet.size)
                        val poll = StructPollfd().apply { fd = peer; events = OsConstants.POLLIN.toShort() }
                        assertEquals("Virtual DNS did not respond through TUN", 1, Os.poll(arrayOf(poll), 5000))
                        val response = ByteArray(1500)
                        val count = Os.read(peer, response, 0, response.size)
                        val stats = RustBridge.stats()
                        assertEquals("Upload must be counted immediately, including short bursts", packet.size.toLong(), stats[0])
                        assertEquals("Download must match actual TUN bytes", count.toLong(), stats[1])
                        assertTrue(count > packet.size)
                        SystemClock.sleep(200)
                        assertEquals(stats.toList(), RustBridge.stats().toList())
                    } finally { RustBridge.stopTun() }
                }
                assertEquals(listOf(0L, 0L), RustBridge.stats().toList())
            }
        } finally {
            RustBridge.stopTun()
            Os.close(tun)
            Os.close(peer)
        }
    }

    @Test
    fun runningConnectionProbeUsesConfiguredSocksInbound() {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { target ->
            val failure = AtomicReference<Throwable?>()
            val worker = thread {
                try {
                    target.accept().use { socket ->
                        socket.soTimeout = 5000
                        val reader = socket.getInputStream().bufferedReader()
                        assertTrue(reader.readLine().startsWith("GET /generate_204"))
                        while (!reader.readLine().isNullOrEmpty()) { }
                        socket.getOutputStream().write("HTTP/1.1 204 No Content\r\nConnection: close\r\n\r\n".toByteArray())
                    }
                } catch (error: Throwable) { failure.set(error) }
            }
            target.soTimeout = 5000
            val port = freePort()
            try {
                XrayBridge.start("""{"inbounds":[{"listen":"127.0.0.1","port":$port,"protocol":"socks","settings":{"auth":"noauth","udp":true}}],"outbounds":[{"protocol":"freedom"}]}""")
                assertTrue(ConnectionProbe.measure(port, "http://127.0.0.1:${target.localPort}/generate_204") > 0)
                worker.join(6000)
                failure.get()?.let { throw AssertionError("Probe target failed", it) }
                try {
                    ConnectionProbe.measure(freePort(), "http://127.0.0.1:${target.localPort}/generate_204", 500)
                    fail("Probe must not fall back to direct traffic when SOCKS is unavailable")
                } catch (_: java.io.IOException) { }
            } finally { XrayBridge.stop() }
        }
    }

    @Test
    fun rustCoreLoadsAndValidatesInput() {
        assertTrue("libiceflow_core.so 未加载", RustBridge.isAvailable)
        assertTrue(RustBridge.version().orEmpty().startsWith("iceflow_core"))
        assertFalse(RustBridge.isRunning())

        val proxy = "socks5://127.0.0.1:10808"
        // 只走参数校验分支，不会真正启动隧道
        assertEquals(-2, RustBridge.startTun(-1, 1500, proxy, RustBridge.DNS_VIRTUAL, "1.1.1.1"))
        assertEquals(-5, RustBridge.startTun(0, 100, proxy, RustBridge.DNS_VIRTUAL, "1.1.1.1"))
        assertEquals(-3, RustBridge.startTun(0, 1500, "bogus", RustBridge.DNS_VIRTUAL, "1.1.1.1"))
        assertEquals(-4, RustBridge.startTun(0, 1500, proxy, 9, "1.1.1.1"))
        assertEquals(-4, RustBridge.startTun(0, 1500, proxy, RustBridge.DNS_OVER_TCP, "dns.google"))

        assertEquals(2, RustBridge.stats().size)
        RustBridge.stopTun() // 未启动时调用应安全
        assertFalse(RustBridge.isRunning())
    }

    @Test
    fun xrayAcceptsAllGeneratedConfigs() {
        assertTrue("xray-core 未加载", XrayBridge.isAvailable)
        assertFalse(XrayBridge.version().isNullOrEmpty())
        XrayBridge.setAssetDir(InstrumentationRegistry.getInstrumentation().targetContext.filesDir.absolutePath)

        val profiles = listOf(
            ServerProfile(
                name = "vmess-ws-tls", protocol = Protocol.VMESS, address = "example.com", port = 443,
                uuid = uuid, network = "ws", host = "cdn.example.com", path = "/ws", tls = "tls", alpn = "h2,http/1.1",
            ),
            ServerProfile(
                name = "vmess-tcp-http", protocol = Protocol.VMESS, address = "example.com", port = 80,
                uuid = uuid, network = "tcp", headerType = "http", host = "example.com", path = "/",
            ),
            ServerProfile(
                name = "vless-reality", protocol = Protocol.VLESS, address = "192.0.2.1", port = 443,
                uuid = uuid, flow = "xtls-rprx-vision", tls = "reality", sni = "www.microsoft.com",
                fingerprint = "chrome", publicKey = "A".repeat(43), shortId = "ab12",
            ),
            ServerProfile(
                name = "vless-grpc", protocol = Protocol.VLESS, address = "example.com", port = 443,
                uuid = uuid, network = "grpc", serviceName = "svc", tls = "tls",
            ),
            ServerProfile(
                name = "vless-xhttp", protocol = Protocol.VLESS, address = "example.com", port = 443,
                uuid = uuid, network = "xhttp", path = "/x", tls = "tls",
            ),
            ServerProfile(
                name = "vless-httpupgrade", protocol = Protocol.VLESS, address = "example.com", port = 80,
                uuid = uuid, network = "httpupgrade", path = "/u",
            ),
            ServerProfile(
                name = "trojan", protocol = Protocol.TROJAN, address = "example.com", port = 443,
                password = "secret", tls = "tls", sni = "example.com",
            ),
            ServerProfile(
                name = "ss-aead", protocol = Protocol.SHADOWSOCKS, address = "192.0.2.2", port = 8388,
                password = "secret", security = "aes-128-gcm",
            ),
            ServerProfile(
                name = "ss-2022", protocol = Protocol.SHADOWSOCKS, address = "192.0.2.3", port = 8388,
                password = "AAAAAAAAAAAAAAAAAAAAAA==", security = "2022-blake3-aes-128-gcm",
            ),
            ServerProfile(name = "hysteria", protocol = Protocol.HYSTERIA, address = "127.0.0.1", port = 443,
                password = "secret", protocolOptions = mapOf("up" to "10", "down" to "100")),
            ServerProfile(name = "hysteria2", protocol = Protocol.HYSTERIA2, address = "127.0.0.1", port = 443,
                password = "secret", protocolOptions = mapOf("obfs" to "salamander", "obfs-password" to "obfs")),
            ServerProfile(name = "tuic-v5", protocol = Protocol.TUIC, address = "127.0.0.1", port = 443, uuid = uuid, password = "secret", alpn = "h3"),
            ServerProfile(name = "tuic-v4", protocol = Protocol.TUIC, address = "127.0.0.1", port = 443, password = "token", alpn = "h3"),
            ServerProfile(name = "wireguard", protocol = Protocol.WIREGUARD, address = "127.0.0.1", port = 51820,
                publicKey = "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=",
                protocolOptions = mapOf("private-key" to "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=", "address" to "10.0.0.2/32")),
            ServerProfile(name = "ssr", protocol = Protocol.SHADOWSOCKSR, address = "127.0.0.1", port = 8388,
                password = "secret", security = "aes-128-cfb", protocolOptions = mapOf("protocol" to "auth_aes128_md5", "obfs" to "tls1.2_ticket_auth")),
            ServerProfile(name = "http-proxy", protocol = Protocol.HTTP, address = "127.0.0.1", port = 8080, username = "user", password = "secret"),
            ServerProfile(name = "socks-proxy", protocol = Protocol.SOCKS, address = "127.0.0.1", port = 1080, username = "user", password = "secret"),
            ServerProfile(name = "mieru-tcp", protocol = Protocol.MIERU, address = "127.0.0.1", port = 12345,
                username = "user", password = "secret", protocolOptions = mapOf("transport" to "TCP", "multiplexing" to "MULTIPLEXING_HIGH")),
            ServerProfile(name = "mieru-udp-mtu", protocol = Protocol.MIERU, address = "127.0.0.1", port = 12345,
                username = "user", password = "secret", protocolOptions = mapOf("transport" to "UDP", "mtu" to "1400", "port-range" to "12345-12347", "handshake-mode" to "HANDSHAKE_NO_WAIT")),
        )

        for (p in profiles) {
            try {
                XrayBridge.start(XrayConfigBuilder.build(p, AppSettings(socksPort = freePort())))
            } catch (e: Exception) {
                fail("${p.name} 配置被 xray-core 拒绝: ${e.message}")
            } finally {
                XrayBridge.stop()
            }
        }
    }

    @Test(expected = Exception::class)
    fun xrayRejectsInvalidConfig() {
        XrayBridge.start("{not json")
    }

    @Test
    fun clashYamlNodesStartWithNestedOptionsOnAndroid() {
        val yaml = """
            proxies:
              - {name: vmess, type: vmess, server: 127.0.0.1, port: 443, uuid: $uuid, alterId: 0, cipher: auto, tls: true, network: ws, ws-opts: {path: /ws, headers: {Host: cdn.example.com}}}
              - {name: vless, type: vless, server: 127.0.0.1, port: 443, uuid: $uuid, tls: true, network: grpc, grpc-opts: {grpc-service-name: svc}}
              - {name: trojan, type: trojan, server: 127.0.0.1, port: 443, password: secret, sni: example.com}
              - {name: ss, type: ss, server: 127.0.0.1, port: 8388, cipher: aes-128-gcm, password: secret, udp: true}
              - {name: ssr, type: ssr, server: 127.0.0.1, port: 8388, cipher: aes-128-cfb, password: secret, protocol: auth_aes128_md5, obfs: tls1.2_ticket_auth}
              - {name: hy, type: hysteria, server: 127.0.0.1, port: 443, auth-str: secret, up: 10, down: 100}
              - {name: hy2, type: hysteria2, server: 127.0.0.1, port: 443, password: secret, obfs: salamander, obfs-password: obfs}
              - {name: tuic, type: tuic, server: 127.0.0.1, port: 443, uuid: $uuid, password: secret, alpn: [h3]}
              - {name: wg, type: wireguard, server: 127.0.0.1, port: 51820, ip: 10.0.0.2, private-key: 'AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=', public-key: 'AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE='}
              - {name: http, type: http, server: 127.0.0.1, port: 8080, username: user, password: secret}
              - {name: socks, type: socks5, server: 127.0.0.1, port: 1080, udp: true}
              - {name: mieru, type: mieru, server: 127.0.0.1, port-range: 12345-12347, username: user, password: secret, transport: TCP, mtu: 1400, handshake-mode: HANDSHAKE_NO_WAIT}
        """.trimIndent()
        val profiles = ShareLinkParser.parseMany(yaml)
        assertEquals(12, profiles.size)
        for (profile in profiles) {
            try { XrayBridge.start(XrayConfigBuilder.build(profile, AppSettings(socksPort = freePort()))) }
            catch (e: Exception) { throw AssertionError("Clash ${profile.name} rejected", e) }
            finally { XrayBridge.stop() }
        }
    }

    @Test
    fun officialMieruProtobufLinkImportsEveryServerAndBinding() {
        // Generated by upstream appctl.ClientConfigToURL in the Go regression suite.
        val fixture = "mieru://CmUKDm1pZXJ1ICsg5Lit5paHEhYKCXVzZXIrbmFtZRIJcEBzczp3b3JkGhoSEXByb3h5LmV4YW1wbGUuY29tGgUIuWAQAhoWCgM6OjEaDxABGgsxMjM0NS0xMjM0NyD4CioCCAQwAgplCg5taWVydSArIOS4reaWhxIWCgl1c2VyK25hbWUSCXBAc3M6d29yZBoaEhFwcm94eS5leGFtcGxlLmNvbRoFCLlgEAIaFgoDOjoxGg8QARoLMTIzNDUtMTIzNDcg+AoqAggEMAI="
        val profiles = ShareLinkParser.parseMany(fixture)
        assertEquals(4, profiles.size)
        assertEquals(setOf("proxy.example.com", "::1"), profiles.map { it.address }.toSet())
        for (p in profiles) {
            assertEquals(Protocol.MIERU, p.protocol); assertEquals("mieru + 中文", p.name)
            assertEquals("user+name", p.username); assertEquals("p@ss:word", p.password)
            assertEquals("1400", p.protocolOptions["mtu"])
            assertEquals("MULTIPLEXING_HIGH", p.protocolOptions["multiplexing"])
            val restored = requireNotNull(ShareLinkParser.parse(ShareLinkParser.encode(p)))
            assertEquals(p.copy(id = restored.id), restored)
            try { XrayBridge.start(XrayConfigBuilder.build(p, AppSettings(socksPort = freePort()))) }
            finally { XrayBridge.stop() }
        }
    }

    @Test
    fun extendedHttpAdapterForwardsAuthenticatedRequestOnAndroid() {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 5_000
            val failure = AtomicReference<Throwable?>(null)
            val worker = thread {
                try {
                    server.accept().use { c ->
                        c.soTimeout = 5_000
                        fun headers(): String {
                            val text = StringBuilder()
                            while (!text.endsWith("\r\n\r\n")) {
                                val b = c.getInputStream().read()
                                check(b >= 0 && text.length < 16_384)
                                text.append(b.toChar())
                            }
                            return text.toString()
                        }
                        val connect = headers()
                        assertTrue(connect.startsWith("CONNECT iceflow.invalid:80 "))
                        assertTrue(connect.contains("Proxy-Authorization: Basic dXNlcjpzZWNyZXQ=", ignoreCase = true))
                        c.getOutputStream().write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
                        c.getOutputStream().flush()
                        assertTrue(headers().startsWith("GET /generate_204 "))
                        c.getOutputStream().write("HTTP/1.1 204 No Content\r\nConnection: close\r\n\r\n".toByteArray())
                        c.getOutputStream().flush()
                    }
                } catch (e: Throwable) { failure.set(e) }
            }
            val profile = ShareLinkParser.parseMany("proxies: [{name: http-test, type: http, server: 127.0.0.1, port: ${server.localPort}, username: user, password: secret}]").single()
            try {
                val config = XrayConfigBuilder.build(profile, AppSettings(bypassLan = false), includeInbound = false)
                assertTrue(XrayBridge.measureDelay(config, "http://iceflow.invalid/generate_204", 5_000) >= 0)
            } finally {
                worker.join(6_000)
            }
            failure.get()?.let { throw AssertionError("Android HTTP adapter forwarding failed", it) }
            assertFalse(worker.isAlive)
        }
    }

    /** 本地 SOCKS 入站 + 局域网直连规则：经 xray 访问本机回环端口应直连成功。 */
    @Test
    fun socksInboundRoutesLanDirect() {
        val socksPort = freePort()
        val unreachable = ServerProfile(
            name = "lan-test", protocol = Protocol.VLESS, address = "192.0.2.1", port = 443, uuid = uuid,
        )
        XrayBridge.start(XrayConfigBuilder.build(unreachable, AppSettings(socksPort = socksPort, bypassLan = true)))
        try {
            ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
                val echo = thread {
                    server.accept().use { c ->
                        val b = c.getInputStream().read()
                        c.getOutputStream().write(b + 1)
                        c.getOutputStream().flush()
                    }
                }
                Socket(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort))).use { s ->
                    s.soTimeout = 5_000
                    s.connect(InetSocketAddress("127.0.0.1", server.localPort), 5_000)
                    s.getOutputStream().write(41)
                    s.getOutputStream().flush()
                    assertEquals(42, s.getInputStream().read())
                }
                echo.join(5_000)
            }
        } finally {
            XrayBridge.stop()
        }
    }
}
