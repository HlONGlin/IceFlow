package com.iceflow.data

import com.iceflow.data.model.*
import com.iceflow.service.VpnErrorType
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class MihomoConfigBuilderTest {
    @Test fun standaloneCompositionsUseOrderedReferencesAcrossGroups() {
        val a = ServerProfile(id = "a", name = "a", protocol = Protocol.VLESS, address = "a.example", port = 443, uuid = "00112233-4455-6677-8899-aabbccddeeff", groupId = "different")
        val b = a.copy(id = "b", address = "b.example", groupId = null, subscriptionId = "sub")
        for (kind in ProxyCompositionKind.entries) {
            val p = NodeEditing.blank(Protocol.SOCKS).copy(name = "composition", composition = ProxyComposition(kind, listOf(b.id, a.id), testUrl = "https://example.com/check", intervalSeconds = 60))
            val s = ServerStore(servers = listOf(a, b, p))
            val cfg = config(p = p, store = s)
            val proxies = cfg["proxies"]!!.jsonArray.map { it.jsonObject }
            assertEquals(listOf("b.example", "a.example"), proxies.map { it.text("server") })
            if (kind == ProxyCompositionKind.CHAIN) {
                assertEquals("chain-0", proxies.last().text("dialer-proxy"))
                assertFalse(proxies.first().containsKey("dialer-proxy"))
                assertEquals("proxy", proxies.last().text("name"))
            } else {
                val group = cfg["proxy-groups"]!!.jsonArray.single().jsonObject
                assertEquals("proxy", cfg.text("iceflow-selected-outbound"))
                assertEquals("load-balance", group.text("type"))
                assertEquals("round-robin", group.text("strategy"))
                assertEquals(listOf("lb-0", "lb-1"), group["proxies"]!!.jsonArray.map { it.jsonPrimitive.content })
                assertEquals("https://example.com/check", group.text("url"))
                assertEquals("60", group.text("interval"))
            }
        }
    }
    private val base = ServerProfile(id = "selected", name = "node", protocol = Protocol.VLESS,
        address = "example.com", port = 443, uuid = "00112233-4455-6677-8899-aabbccddeeff")
    private fun config(p: ServerProfile = base, s: AppSettings = AppSettings(), include: Boolean = true,
                       store: ServerStore? = null, port: Int? = null, auth: Pair<String, String>? = null) =
        Json.parseToJsonElement(MihomoConfigBuilder.build(p, s, include, store, port, auth)).jsonObject
    private fun out(p: ServerProfile = base) = MihomoConfigBuilder.outbound(p)
    private fun raw(type: String = "vless", extra: String = ""): ServerProfile = ClashYamlParser.parse(
        """{"proxies":[{"name":"raw","type":"$type","server":"example.com","port":443,"uuid":"00112233-4455-6677-8899-aabbccddeeff"$extra}]}""").single()
    private fun rejected(field: String, body: () -> Unit): MihomoMappingException {
        val error = try { body(); throw AssertionError("expected rejection: $field") } catch (e: MihomoMappingException) { e }
        assertEquals(VpnErrorType.CONFIGURATION_OR_SUBSCRIPTION, error.errorType)
        assertTrue(error.message.orEmpty(), error.message.orEmpty().contains(field))
        return error
    }
    private fun JsonObject.text(key: String) = getValue(key).jsonPrimitive.content

    @Test fun explicitAndroidRouteOwnership() {
        for (s in listOf(AppSettings(), AppSettings(fakeDns = false), AppSettings(ipv6Enabled = false), AppSettings(dnsProxyEnabled = false))) {
            for (include in listOf(true, false)) {
                val tun = config(s = s, include = include).getValue("tun").jsonObject
                for (key in listOf("auto-route", "auto-detect-interface", "auto-redirect")) {
                    assertTrue(tun.containsKey(key)); assertEquals(JsonPrimitive(false), tun[key])
                }
            }
        }
    }
    @Test fun spiderEmptyIsInitialPathApproximation() { assertEquals("vless", out(base.copy(tls = "reality", publicKey = "key")).text("type")) }
    @Test fun spiderRootIsInitialPathApproximation() { assertEquals("vless", out(base.copy(tls = "reality", spiderX = "/", publicKey = "key")).text("type")) }
    @Test fun spiderQueryIsRejectedWithoutMutation() {
        val p = base.copy(spiderX = "/?p=1&c=2")
        assertTrue(rejected("spiderX") { out(p) }.message!!.contains("单核内核不支持该字段"))
        assertEquals("/?p=1&c=2", p.spiderX)
    }
    @Test fun spiderNonRootIsRejected() { rejected("spiderX") { out(base.copy(spiderX = "/news")) } }
    @Test fun httpAlpnMatchesExistingDualOutput() {
        val p = base.copy(protocol = Protocol.HTTP, uuid = "", username = "u", password = "p", tls = "tls", alpn = "h2,http/1.1")
        assertEquals(ExtendedConfigBuilder.build(p)["alpn"], out(p)["alpn"])
    }
    @Test fun socksAlpnMatchesExistingDualOutput() {
        val p = base.copy(protocol = Protocol.SOCKS, uuid = "", username = "u", password = "p", tls = "tls", alpn = "h2")
        assertEquals(ExtendedConfigBuilder.build(p)["alpn"], out(p)["alpn"])
    }
    @Test fun vmessUpgradeConversion() { upgrade(Protocol.VMESS) }
    @Test fun vlessUpgradeConversion() { upgrade(Protocol.VLESS) }
    @Test fun trojanUpgradeConversion() { upgrade(Protocol.TROJAN) }
    private fun upgrade(protocol: Protocol) {
        val p = base.copy(protocol = protocol, uuid = if (protocol == Protocol.TROJAN) "" else base.uuid,
            password = if (protocol == Protocol.TROJAN) "secret" else "", network = "httpupgrade", tls = "tls", path = "/upgrade", host = "cdn.example")
        val n = out(p); assertEquals("ws", n.text("network"))
        val ws = n.getValue("ws-opts").jsonObject
        assertEquals("/upgrade", ws.text("path")); assertEquals("cdn.example", ws.getValue("headers").jsonObject.text("Host"))
        assertEquals(JsonPrimitive(true), ws["v2ray-http-upgrade"]); assertFalse(MihomoConfigBuilder.upgradeUsesEarlyConn(n))
    }
    @Test fun earlyConnRequiresBothFlags() {
        for (upgrade in listOf(false, true)) for (fast in listOf(false, true)) {
            val n = out(raw(extra = """, "network":"ws", "ws-opts":{"path":"/", "v2ray-http-upgrade":$upgrade,"v2ray-http-upgrade-fast-open":$fast}"""))
            assertEquals(upgrade && fast, MihomoConfigBuilder.upgradeUsesEarlyConn(n))
            assertEquals(JsonPrimitive(fast), n.getValue("ws-opts").jsonObject["v2ray-http-upgrade-fast-open"])
        }
    }
    @Test fun ssUpgradeOnlyThroughV2rayPlugin() {
        val node = buildJsonObject {
            put("name", "ss"); put("type", "ss"); put("server", "example.com"); put("port", 443)
            put("cipher", "aes-128-gcm"); put("password", "secret"); put("plugin", "v2ray-plugin")
            putJsonObject("plugin-opts") { put("mode", "websocket"); put("path", "/"); put("v2ray-http-upgrade", true); put("v2ray-http-upgrade-fast-open", true) }
        }
        val p = base.copy(protocol = Protocol.SHADOWSOCKS, clashConfig = node)
        assertEquals(node["plugin-opts"], out(p)["plugin-opts"])
    }
    @Test fun grpcAllOptionsPreserved() {
        val p = raw(extra = """, "network":"grpc", "grpc-opts":{"grpc-service-name":"svc","grpc-user-agent":"agent","ping-interval":12,"max-connections":3,"min-streams":2,"max-streams":9}""")
        assertEquals(p.clashConfig!!["grpc-opts"], out(p)["grpc-opts"])
    }
    @Test fun xhttpNestedOptionsPreserved() {
        val p = raw(extra = """, "network":"xhttp", "xhttp-opts":{"path":"/x","host":"h","mode":"auto","headers":{"X-Test":"v"},"reuse-settings":{"max-connections":"2-3","h-keep-alive-period":30},"download-settings":{"server":"down.example","port":8443,"path":"/down","tls":true,"alpn":["h2"]}}""")
        assertEquals(p.clashConfig!!["xhttp-opts"], out(p)["xhttp-opts"])
    }
    @Test fun ordinaryWsHeadersPreserved() {
        val p = raw(extra = """, "network":"ws", "ws-opts":{"path":"/ws","headers":{"Host":"cdn","X-A":"b"},"max-early-data":100,"early-data-header-name":"Sec-WebSocket-Protocol"}""")
        assertEquals(p.clashConfig!!["ws-opts"], out(p)["ws-opts"])
    }
    @Test fun rawTcpNormalized() { assertEquals("tcp", out(base.copy(network = "raw")).text("network")) }
    @Test fun tlsFieldsHaveDistinctDestinations() {
        val n = out(base.copy(tls = "tls", sni = "sni.example", alpn = "h2, http/1.1", fingerprint = "chrome", allowInsecure = true))
        assertEquals("sni.example", n.text("servername")); assertEquals("chrome", n.text("client-fingerprint"))
        assertEquals(JsonPrimitive(true), n["skip-cert-verify"]); assertEquals(2, n.getValue("alpn").jsonArray.size)
    }
    @Test fun realityKeysAndVisionPreserved() {
        val n = out(base.copy(tls = "reality", publicKey = "key", shortId = "ab", flow = "xtls-rprx-vision"))
        assertEquals("key", n.getValue("reality-opts").jsonObject.text("public-key")); assertEquals("xtls-rprx-vision", n.text("flow"))
    }
    @Test fun unsupportedAlterIdRejected() { rejected("alterId") { out(base.copy(protocol = Protocol.VMESS, alterId = 1)) } }
    @Test fun nonVlessXhttpRejected() { rejected("network") { out(base.copy(protocol = Protocol.VMESS, network = "xhttp")) } }
    @Test fun tcpHttpHeaderRejected() { rejected("headerType") { out(base.copy(headerType = "http")) } }
    @Test fun vlessHostWsHeadersConverted() {
        val node = out(raw(extra = """, "network":"ws", "ws-headers":{"Host":"x"}"""))
        assertEquals("x", node.getValue("ws-opts").jsonObject.getValue("headers").jsonObject.text("Host"))
        assertFalse(node.containsKey("ws-headers"))
    }
    @Test fun vlessCustomWsHeadersRejected() {
        rejected("ws-headers") { out(raw(extra = """, "network":"ws", "ws-headers":{"Host":"x","X-Custom":"y"}""")) }
    }
    @Test fun fixedWsAlpnRejected() { rejected("alpn") { out(base.copy(network = "ws", tls = "tls", alpn = "h2")) } }
    @Test fun wsRealityRejected() { rejected("reality-opts") { out(base.copy(network = "ws", tls = "reality", publicKey = "key")) } }
    @Test fun inactiveNestedTransportRejected() { rejected("ws-opts") { out(raw(extra = """, "ws-opts":{"path":"/x"}""")) } }
    @Test fun unknownTopOptionRejectedWithoutSecretLeak() {
        val p = raw(extra = """, "secret-new-option":"do-not-log-this"""")
        assertFalse(rejected("secret-new-option") { out(p) }.message!!.contains("do-not-log-this"))
    }
    @Test fun unknownNestedOptionRejected() { rejected("unknown") { out(raw(extra = """, "network":"grpc", "grpc-opts":{"unknown":1}""")) } }
    @Test fun pluginUnknownOptionRejected() {
        val p = base.copy(protocol = Protocol.SHADOWSOCKS, clashConfig = Json.parseToJsonElement("""{"type":"ss","server":"e","port":1,"plugin":"v2ray-plugin","plugin-opts":{"mode":"websocket","unknown":1}}""").jsonObject)
        rejected("plugin-opts") { out(p) }
    }
    @Test fun nestedTypeMismatchRejected() { rejected("max-connections") { out(raw(extra = """, "network":"grpc", "grpc-opts":{"max-connections":"many"}""")) } }
    @Test fun ignoredProtocolOptionMatchesDual() {
        val p = base.copy(protocol = Protocol.HTTP, uuid = "", protocolOptions = mapOf("sni" to "x"))
        assertEquals(ExtendedConfigBuilder.build(p)["alpn"], out(p)["alpn"])
        assertFalse(out(p).containsKey("protocolOptions"))
    }
    @Test fun duplicateBandwidthAliasesFollowDualPrecedence() {
        val n = out(base.copy(protocol = Protocol.HYSTERIA, uuid = "", password = "p",
            protocolOptions = mapOf("up" to "10", "upmbps" to "20", "down" to "10")))
        assertEquals("10", n.text("up")); assertEquals("10", n.text("down"))
    }
    @Test fun wgAddressPrefixMatchesDual() {
        val n = out(base.copy(protocol = Protocol.WIREGUARD, uuid = "", publicKey = "key",
            protocolOptions = mapOf("private-key" to "key", "address" to "10.0.0.1/24")))
        assertEquals("10.0.0.1", n.text("ip"))
    }
    @Test fun mieruMtuUsesOfficialClientShim() {
        val node = out(base.copy(protocol = Protocol.MIERU, uuid = "", username = "u", password = "p", protocolOptions = mapOf("mtu" to "1400")))
        assertEquals(JsonPrimitive(1400), node["mtu"])
    }
    @Test fun mieruInvalidMtuRejected() { rejected("configuration") { config(base.copy(protocol = Protocol.MIERU, uuid = "", username = "u", password = "p", protocolOptions = mapOf("mtu" to "1279"))) } }
    @Test fun authAndUdpListenersIndependent() {
        val list = config(s = AppSettings(socksUsername = "public", socksPassword = "pw", socksUdp = false), port = 12345, auth = "private" to "tp").getValue("listeners").jsonArray
        assertEquals(2, list.size)
        assertEquals("private", list[0].jsonObject.getValue("users").jsonArray.single().jsonObject.text("username"))
        assertEquals(JsonPrimitive(true), list[0].jsonObject["udp"])
        assertEquals("public", list[1].jsonObject.getValue("users").jsonArray.single().jsonObject.text("username"))
        assertEquals(JsonPrimitive(false), list[1].jsonObject["udp"])
    }
    @Test fun disabledPublicSocksKeepsPrivateListener() { assertEquals(1, config(s = AppSettings(socksEnabled = false), port = 12345).getValue("listeners").jsonArray.size) }
    @Test fun measurementOmitsListeners() { assertFalse(config(include = false).containsKey("listeners")) }
    @Test fun dnsFakeIpMode() { assertEquals("fake-ip", config().getValue("dns").jsonObject.text("enhanced-mode")) }
    @Test fun dnsRealIpStillUsesProxyTcp() {
        val dns = config(s = AppSettings(fakeDns = false)).getValue("dns").jsonObject
        assertEquals("redir-host", dns.text("enhanced-mode")); assertEquals("tcp://1.1.1.1#proxy", dns.getValue("nameserver").jsonArray.single().jsonPrimitive.content)
    }
    @Test fun dnsDisabledUsesDirectDeviceServers() {
        val dns = config(s = AppSettings(dnsProxyEnabled = false, dnsServers = listOf("8.8.8.8"))).getValue("dns").jsonObject
        assertEquals("udp://8.8.8.8#DIRECT", dns.getValue("nameserver").jsonArray.single().jsonPrimitive.content)
    }
    @Test fun ipv6DnsAddressBracketed() { assertEquals("tcp://[2606:4700:4700::1111]#proxy", config(s = AppSettings(remoteDns = "2606:4700:4700::1111")).getValue("dns").jsonObject.getValue("nameserver").jsonArray.single().jsonPrimitive.content) }
    @Test fun ipv6SwitchControlsDnsAndTun() {
        val n = config(s = AppSettings(ipv6Enabled = false)); assertEquals(JsonPrimitive(false), n["ipv6"])
        assertEquals(JsonPrimitive(false), n.getValue("dns").jsonObject["ipv6"]); assertTrue(n.getValue("tun").jsonObject.getValue("inet6-address").jsonArray.isEmpty())
    }
    @Test fun ruleFieldsOrWithinAndAcross() {
        val r = RoutingRule(name = "r", domains = listOf("full:a.com", "domain:b.com"), ips = listOf("1.2.3.4", "2001:db8::1"), source = listOf("10.0.0.1"), port = "80,443", sourcePort = "1000-2000", network = "tcp,udp", action = RouteAction.BLOCK)
        val text = MihomoConfigBuilder.rule(r)
        assertTrue(text.startsWith("AND,")); assertTrue(text.contains("OR,((DOMAIN,a.com),(DOMAIN-SUFFIX,b.com))"))
        assertTrue(text.contains("IP-CIDR6,2001:db8::1/128")); assertTrue(text.contains("SRC-IP-CIDR,10.0.0.1/32")); assertTrue(text.endsWith(",REJECT"))
    }
    @Test fun geoAndDomainPrefixesMapped() {
        val r = RoutingRule(name = "r", domains = listOf("keyword:ad", "regexp:^a", "geosite:cn"), ips = listOf("geoip:cn"), source = listOf("geoip:us"))
        val text = MihomoConfigBuilder.rule(r); assertTrue(text.contains("DOMAIN-KEYWORD,ad")); assertTrue(text.contains("DOMAIN-REGEX,^a")); assertTrue(text.contains("GEOSITE,cn")); assertTrue(text.contains("SRC-GEOIP,us"))
    }
    @Test fun privateCidrListExpanded() { assertTrue(MihomoConfigBuilder.rule(RoutingRule(id = "builtin:private", name = "r", ips = listOf("geoip:private"))).contains("IP-CIDR,100.64.0.0/10")) }
    @Test fun lanToggleAndRuleOrderPreserved() {
        val on = MihomoConfigBuilder.rules(AppSettings()); val off = MihomoConfigBuilder.rules(AppSettings(bypassLan = false))
        assertEquals(on.size - 1, off.size); assertEquals("MATCH,proxy", on.last())
    }
    @Test fun globalAndDirectModes() {
        assertEquals(listOf("MATCH,proxy"), MihomoConfigBuilder.rules(AppSettings(routingMode = RoutingMode.GLOBAL)))
        assertEquals(listOf("MATCH,DIRECT"), MihomoConfigBuilder.rules(AppSettings(routingMode = RoutingMode.DIRECT)))
    }
    @Test fun protocolRuleIsRejectedRatherThanDropped() { rejected("protocols") { MihomoConfigBuilder.rule(RoutingRule(name = "r", protocols = listOf("bittorrent"))) } }
    @Test fun externalGeoRuleIsRejected() { rejected("ext") { MihomoConfigBuilder.rule(RoutingRule(name = "r", domains = listOf("ext:other.dat:x"))) } }
    @Test fun unprefixedDomainIsPending() { rejected("domains") { MihomoConfigBuilder.rule(RoutingRule(name = "r", domains = listOf("example"))) } }
    @Test fun chainOrderAndDialersPreserved() {
        val front = base.copy(id = "front"); val landing = base.copy(id = "landing"); val p = base.copy(groupId = "group")
        val store = ServerStore(servers = listOf(front, p, landing), localGroups = listOf(LocalGroup(id = "group", name = "g", frontNodeId = "front", landingNodeId = "landing")))
        val nodes = config(p, store = store).getValue("proxies").jsonArray.map { it.jsonObject }
        assertEquals(listOf("chain-0", "chain-1", "proxy"), nodes.map { it.text("name") })
        assertFalse(nodes[0].containsKey("dialer-proxy")); assertEquals("chain-0", nodes[1].text("dialer-proxy")); assertEquals("chain-1", nodes[2].text("dialer-proxy"))
    }
    @Test fun missingChainNodeRejected() { rejected("frontNodeId") { config(base.copy(groupId = "g"), store = ServerStore(localGroups = listOf(LocalGroup(id = "g", name = "g", frontNodeId = "missing")))) } }
    @Test fun duplicateChainNodeRejected() { rejected("chain") { config(base.copy(groupId = "g"), store = ServerStore(servers = listOf(base), localGroups = listOf(LocalGroup(id = "g", name = "g", frontNodeId = base.id)))) } }
    @Test fun loadBalanceGroupUsesSelectedMihomoStrategyAndAllMembers() {
        val nodes = listOf(base.copy(id = "a", groupId = "g"), base.copy(id = "b", groupId = "g"), base.copy(id = "c", groupId = "g"))
        val group = LocalGroup(id = "g", name = "g", balanceStrategy = LoadBalanceStrategy.CONSISTENT_HASHING, balanceNodeIds = listOf("a", "b"), balanceTestUrl = "https://probe.example/check", balanceIntervalSeconds = 45)
        val cfg = config(nodes.first(), store = ServerStore(servers = nodes, localGroups = listOf(group)))
        val lb = cfg.getValue("proxy-groups").jsonArray.single().jsonObject
        assertEquals("load-balance", lb.text("type"))
        assertEquals("consistent-hashing", lb.text("strategy"))
        assertEquals(listOf("lb-0", "lb-1"), lb.getValue("proxies").jsonArray.map { it.jsonPrimitive.content })
        assertEquals("https://probe.example/check", lb.text("url"))
        assertEquals(45, lb.getValue("interval").jsonPrimitive.int)
        assertEquals("MATCH,proxy", cfg.getValue("rules").jsonArray.last().jsonPrimitive.content)
    }
    @Test fun metadataDoesNotMutatePersistedProfile() {
        val p = base.copy(name = "original", latencyMs = 42, uploadBytes = 123, downloadBytes = 456)
        config(p); assertEquals("original", p.name); assertEquals(123L, p.uploadBytes); assertEquals(456L, p.downloadBytes)
    }
    @Test fun allFourteenProtocolEndpointsMap() {
        for (protocol in Protocol.entries) {
            val options = when (protocol) {
                Protocol.HYSTERIA -> mapOf("up" to "10", "down" to "20")
                Protocol.WIREGUARD -> mapOf("private-key" to "key", "address" to "10.0.0.1", "reserved" to "1,2,3")
                else -> emptyMap()
            }
            val p = base.copy(protocol = protocol, uuid = if (protocol in setOf(Protocol.VMESS, Protocol.VLESS, Protocol.TUIC)) base.uuid else "",
                password = if (protocol in setOf(Protocol.VMESS, Protocol.VLESS, Protocol.WIREGUARD)) "" else "secret",
                security = if (protocol in setOf(Protocol.SHADOWSOCKS, Protocol.SHADOWSOCKSR)) "aes-128-gcm" else "auto",
                tls = if (protocol == Protocol.TROJAN) "tls" else "none", username = if (protocol == Protocol.MIERU) "user" else "",
                publicKey = if (protocol == Protocol.WIREGUARD) "key" else "", protocolOptions = options)
            val n = out(p)
            assertEquals(protocol.name, "example.com", n.text("server"))
            assertEquals(protocol.name, 443, n.getValue("port").jsonPrimitive.int)
        }
    }
    @Test fun wgReservedHasConsumer() {
        val p = base.copy(protocol = Protocol.WIREGUARD, uuid = "", publicKey = "key", protocolOptions = mapOf("private-key" to "key", "address" to "10.0.0.1", "reserved" to "1,2,3"))
        assertEquals(JsonArray(listOf(1, 2, 3).map(::JsonPrimitive)), out(p)["reserved"])
    }
    @Test fun wgAllowedIpsSinglePeerConverted() {
        val p = base.copy(protocol = Protocol.WIREGUARD, uuid = "", publicKey = "key", protocolOptions = mapOf("private-key" to "key", "address" to "10.0.0.1", "allowed-ips" to "10.0.0.0/8"))
        val peer = out(p).getValue("peers").jsonArray.single().jsonObject
        assertEquals("key", peer.text("public-key"))
        assertEquals("10.0.0.0/8", peer.getValue("allowed-ips").jsonArray.single().jsonPrimitive.content)
    }
    @Test fun tuicFastOpenFollowsDualOutput() {
        val n = out(base.copy(protocol = Protocol.TUIC, password = "secret",
            protocolOptions = mapOf("fast-open" to "true", "request-timeout" to "5000")))
        assertEquals(JsonPrimitive(true), n["fast-open"])
        assertEquals(5000, n.getValue("request-timeout").jsonPrimitive.int)
    }
    @Test fun tuicV4TokenAndFastOpenMapped() {
        val n = out(base.copy(protocol = Protocol.TUIC, uuid = "", password = "token", protocolOptions = mapOf("fast-open" to "true", "request-timeout" to "5000")))
        assertEquals("token", n.text("token")); assertEquals(JsonPrimitive(true), n["fast-open"])
    }
    // R3: pinned mihomo TuicOption has no version field; v4/v5 is token-presence derived
    // (tuic.go:183,271). Rule ② applies only to a non-default tuic.version value in the
    // raw path; the default path (no version key) must never be rejected for version.
    @Test fun rawTuicVersionNonDefaultRejectedNamingField() {
        val p = base.copy(protocol = Protocol.TUIC, uuid = "", clashConfig = Json.parseToJsonElement(
            """{"name":"t","type":"tuic","server":"e","port":443,"token":"tok","version":5}""").jsonObject)
        val error = rejected("tuic.version") { out(p) }
        assertTrue(error.message!!.contains("tuic.version"))
    }
    @Test fun tuicWithoutVersionMapsBothFlavors() {
        val v5 = out(base.copy(protocol = Protocol.TUIC, uuid = "00112233-4455-6677-8899-aabbccddeeff", password = "pass"))
        assertEquals("00112233-4455-6677-8899-aabbccddeeff", v5.text("uuid"))
        val v4 = out(base.copy(protocol = Protocol.TUIC, uuid = "", password = "token"))
        assertEquals("token", v4.text("token"))
    }
    @Test fun hysteriaAliasesAndObfsConvert() {
        val n = out(base.copy(protocol = Protocol.HYSTERIA, uuid = "", password = "auth", protocolOptions = mapOf("upmbps" to "10", "downmbps" to "20", "obfs" to "xplus", "obfsParam" to "obfs-key", "hopInterval" to "12")))
        assertEquals("obfs-key", n.text("obfs")); assertEquals(12, n.getValue("hop-interval").jsonPrimitive.int)
    }
    @Test fun anytlsAliasesAndNestedEchMap() {
        val p = base.copy(protocol = Protocol.ANYTLS, uuid = "", password = "p", protocolOptions = mapOf("clientFingerprint" to "chrome", "idleSessionTimeout" to "30", "ech-opts" to """{"enable":true,"config":"AA=="}"""))
        assertEquals("chrome", out(p).text("client-fingerprint")); assertEquals(30, out(p).getValue("idle-session-timeout").jsonPrimitive.int)
    }
    @Test fun anyTlsFingerprintFollowsDualPrecedence() {
        val n = out(base.copy(protocol = Protocol.ANYTLS, uuid = "", password = "p", fingerprint = "chrome",
            protocolOptions = mapOf("fp" to "firefox")))
        assertEquals("chrome", n.text("client-fingerprint"))
    }
    @Test fun anyTlsGenericSecurityAliasDoesNotBlockMihomo() {
        val n = out(base.copy(protocol = Protocol.ANYTLS, uuid = "", password = "p",
            protocolOptions = mapOf("security" to "tls", "type" to "tcp", "headerType" to "none", "insecure" to "1")))
        assertEquals("anytls", n.text("type"))
        assertEquals(JsonPrimitive(true), n["skip-cert-verify"])
        assertFalse(n.containsKey("security"))
        assertFalse(n.containsKey("allowInsecure"))
    }
    @Test fun socksSniGapRejected() { rejected("sni") { out(base.copy(protocol = Protocol.SOCKS, uuid = "", tls = "tls", sni = "custom.example")) } }
    @Test fun trojanGrpcFixedAlpnIsEquivalent() {
        val n = out(base.copy(protocol = Protocol.TROJAN, uuid = "", password = "p", tls = "tls", network = "grpc", alpn = "h2"))
        assertEquals(listOf("h2"), n.getValue("alpn").jsonArray.map { it.jsonPrimitive.content })
    }
    @Test fun trojanGrpcDifferentAlpnRejected() {
        rejected("alpn") { out(base.copy(protocol = Protocol.TROJAN, uuid = "", password = "p", tls = "tls", network = "grpc", alpn = "h3")) }
    }
    @Test fun realityAlpnAndAllowInsecureKeepDualIgnoredBehavior() {
        val n = out(base.copy(tls = "reality", publicKey = "key", alpn = "h3", allowInsecure = true))
        assertEquals(listOf("h3"), n.getValue("alpn").jsonArray.map { it.jsonPrimitive.content })
        assertEquals(JsonPrimitive(true), n["skip-cert-verify"])
    }
    @Test fun tlsDisabledAlpnKeepsDualIgnoredBehavior() {
        val n = out(base.copy(tls = "none", alpn = "h3"))
        assertEquals(listOf("h3"), n.getValue("alpn").jsonArray.map { it.jsonPrimitive.content })
    }
    @Test fun tlsDisabledClientFingerprintKeepsDualIgnoredBehavior() {
        val n = out(base.copy(protocol = Protocol.VMESS, tls = "none", fingerprint = "chrome"))
        assertEquals("chrome", n.text("client-fingerprint"))
    }
    @Test fun wsFingerprintIsConsumed() {
        val n = out(base.copy(tls = "tls", network = "ws", fingerprint = "chrome"))
        assertEquals("chrome", n.text("client-fingerprint"))
    }
    @Test fun dnsDirectDomainPolicyConverted() {
        val rules = listOf(RoutingRule(name = "r", domains = listOf("full:a.example", "domain:b.example", "geosite:cn")))
        val p = config(s = AppSettings(routingRules = rules)).getValue("dns").jsonObject.getValue("nameserver-policy").jsonObject
        assertTrue(p.containsKey("a.example")); assertTrue(p.containsKey("+.b.example")); assertTrue(p.containsKey("geosite:cn"))
    }
    @Test fun customPrivateGeoRuleKeepsGeoSemantics() { assertEquals("GEOIP,private,DIRECT", MihomoConfigBuilder.rule(RoutingRule(name = "r", ips = listOf("geoip:private")))) }
    @Test fun echDisabledPayloadRejected() { rejected("ech-opts") { out(raw(extra = """, "tls":true,"ech-opts":{"enable":false,"config":"AA=="}""")) } }
    @Test fun echFixedConfigAndQueryConflictRejected() { rejected("ech-opts") { out(raw(extra = """, "tls":true,"ech-opts":{"enable":true,"config":"AA==","query-server-name":"x"}""")) } }
    @Test fun gRpcPathNotSilentlyDropped() { rejected("path/host") { out(base.copy(network = "grpc", path = "/unused")) } }
    @Test fun packetEncodingConflictFollowsDualPrecedence() {
        val n = out(raw(extra = """, "packet-encoding":"xudp","packet-addr":true"""))
        assertEquals("xudp", n.text("packet-encoding")); assertEquals(JsonPrimitive(true), n["packet-addr"])
    }
    @Test fun ignoredFlowNotAccepted() { rejected("flow") { out(base.copy(flow = "vision-typo")) } }
    @Test fun rawHy1AuthConflictFollowsDualPrecedence() {
        val p = base.copy(protocol = Protocol.HYSTERIA, uuid = "", clashConfig = Json.parseToJsonElement("""{"name":"h","type":"hysteria","server":"e","port":443,"auth":"AA==","auth-str":"a","up":"10","down":"20"}""").jsonObject)
        val n = out(p); assertEquals("AA==", n.text("auth")); assertEquals("a", n.text("auth-str"))
    }
    @Test fun rawHy2InactiveObfsSizeFollowsDualPrecedence() {
        val p = base.copy(protocol = Protocol.HYSTERIA2, uuid = "", clashConfig = Json.parseToJsonElement("""{"name":"h","type":"hysteria2","server":"e","port":443,"password":"p","obfs":"salamander","obfs-password":"p","obfs-min-packet-size":100}""").jsonObject)
        assertEquals(JsonPrimitive(100), out(p)["obfs-min-packet-size"])
    }
    @Test fun sudokuNestedAndFlatMaskConflictFollowsDualPrecedence() {
        val p = base.copy(protocol = Protocol.SUDOKU, uuid = "", clashConfig = Json.parseToJsonElement("""{"name":"s","type":"sudoku","server":"e","port":443,"key":"p","http-mask-mode":"legacy","httpmask":{"mode":"stream"}}""").jsonObject)
        val n = out(p); assertEquals("legacy", n.text("http-mask-mode")); assertTrue(n.containsKey("httpmask"))
    }
    @Test fun rawWgPeersAndTopKeyConflictFollowsDualPrecedence() {
        val p = base.copy(protocol = Protocol.WIREGUARD, uuid = "", clashConfig = Json.parseToJsonElement("""{"name":"w","type":"wireguard","server":"e","port":443,"public-key":"top","peers":[{"server":"p","port":443,"public-key":"peer","allowed-ips":["0.0.0.0/0"]}]}""").jsonObject)
        assertEquals("peer", out(p).getValue("peers").jsonArray.single().jsonObject.text("public-key"))
    }
    @Test fun xhttpDisabledDownloadTlsFieldsRejected() {
        rejected("download-settings.tls") { out(raw(extra = """, "network":"xhttp","xhttp-opts":{"download-settings":{"tls":false,"alpn":["h2"]}}""")) }
    }
    @Test fun customListenerConfigNotClaimedWrapperCompatible() {
        // Configuration-only evidence: wrapper parse currently rejects cfg.Listeners (audit G10).
        assertTrue(config().getValue("listeners").jsonArray.isNotEmpty())
    }
    @Test fun basicOptionsNotForwardedMatchesDual() {
        val p = base.copy(protocol = Protocol.HYSTERIA2, uuid = "", clashConfig = Json.parseToJsonElement("""{"name":"h","type":"hysteria2","server":"e","port":443,"password":"p","mptcp":true}""").jsonObject)
        assertEquals(JsonPrimitive(true), out(p)["mptcp"])
    }
    @Test fun omittedTlsAlpnRetainsDualOutput() {
        val node = out(raw(extra = """, "alpn":["h2"]"""))
        assertEquals(listOf("h2"), node.getValue("alpn").jsonArray.map { it.jsonPrimitive.content })
    }
    @Test fun httpSocksAlpnParityAlsoWithoutTls() {
        for (protocol in listOf(Protocol.HTTP, Protocol.SOCKS)) {
            val p = base.copy(protocol = protocol, uuid = "", alpn = "h2", tls = "none")
            assertEquals(ExtendedConfigBuilder.build(p)["alpn"], out(p)["alpn"])
        }
    }
}
