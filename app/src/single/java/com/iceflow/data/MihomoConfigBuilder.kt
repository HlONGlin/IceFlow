package com.iceflow.data

import com.iceflow.data.model.*
import com.iceflow.service.VpnErrorType
import kotlinx.serialization.json.*

/** No raw config or credentials are included in diagnostics. */
internal class MihomoMappingException(val field: String, reason: String) :
    IllegalArgumentException("CONFIGURATION_OR_SUBSCRIPTION: $field：$reason") {
    val errorType = VpnErrorType.CONFIGURATION_OR_SUBSCRIPTION
}

/** Pure single-only mapper; runtime bridge validates the resulting app configuration. */
internal object MihomoConfigBuilder {
    const val PROXY = "proxy"
    private val native = setOf(Protocol.VMESS, Protocol.VLESS, Protocol.TROJAN, Protocol.SHADOWSOCKS)
    private val privateCidrs = listOf("10.0.0.0/8", "100.64.0.0/10", "127.0.0.0/8", "169.254.0.0/16",
        "172.16.0.0/12", "192.168.0.0/16", "224.0.0.0/4", "::1/128", "fc00::/7", "fe80::/10")

    /** Mapping remains callable in isolation; runtime readiness and state transitions are unchanged. */
    fun build(profile: ServerProfile, settings: AppSettings, includeInbound: Boolean = true,
              store: ServerStore? = null, tunnelPort: Int? = null, tunnelAuth: Pair<String, String>? = null): String {
        try {
            settings.validate()
            require(tunnelPort == null || (tunnelPort in 1..65535 && (!settings.socksEnabled || tunnelPort != settings.socksPort)))
            val chain = chain(profile, store)
            val names = chain.indices.map { if (it == chain.lastIndex) PROXY else "chain-$it" }
            val proxies = chain.mapIndexed { index, p ->
                JsonObject(outbound(p, names[index]) + if (index == 0) emptyMap() else mapOf("dialer-proxy" to JsonPrimitive(names[index - 1])))
            }
            return buildJsonObject {
                put("mode", "rule"); put("log-level", "warning"); put("find-process-mode", "off")
                put("ipv6", settings.ipv6Enabled); put("geodata-mode", true)
                // Every emitted configuration explicitly leaves Android route ownership with VpnService.
                putJsonObject("tun") {
                    put("enable", false); put("stack", "gvisor"); put("mtu", 1500)
                    put("auto-route", false); put("auto-detect-interface", false); put("auto-redirect", false)
                    put("dns-hijack", JsonArray(listOf(JsonPrimitive("any:53"))))
                    put("inet6-address", JsonArray(if (settings.ipv6Enabled) listOf(JsonPrimitive("fdfe:dcba:9876::1/126")) else emptyList()))
                }
                put("proxies", JsonArray(proxies))
                // Wrapper-only metadata: session persistence belongs to the selected node.
                put("iceflow-selected-outbound", names[chain.indexOfFirst { it.id == profile.id }])
                put("rules", JsonArray(rules(settings).map(::JsonPrimitive)))
                put("dns", dns(settings))
                if (includeInbound) put("listeners", JsonArray(listeners(settings, tunnelPort, tunnelAuth)))
            }.toString()
        } catch (e: MihomoMappingException) { throw e }
        catch (e: IllegalArgumentException) { throw MihomoMappingException("configuration", "配置校验失败；请核对字段格式") }
        catch (e: IllegalStateException) { throw MihomoMappingException("configuration", "配置生成失败；请核对协议参数") }
    }

    internal fun chain(profile: ServerProfile, store: ServerStore?): List<ServerProfile> {
        val group = store?.localGroups?.firstOrNull { it.id == profile.groupId }
        val subscription = store?.subscriptions?.firstOrNull { it.id == profile.subscriptionId }
        fun resolve(id: String?, field: String) = id?.let { selected ->
            store?.servers?.firstOrNull { it.id == selected } ?: throw MihomoMappingException(field, "链路节点已失效")
        }
        val nodes = listOfNotNull(resolve(subscription?.frontNodeId ?: group?.frontNodeId, "frontNodeId"), profile,
            resolve(subscription?.landingNodeId ?: group?.landingNodeId, "landingNodeId"))
        if (nodes.map { it.id }.distinct().size != nodes.size) fail("chain", "代理链包含重复节点")
        return nodes
    }

    internal fun outbound(p: ServerProfile, name: String = PROXY): JsonObject {
        if (p.address.isBlank() || p.port !in 1..65535) fail("address/port", "服务器地址或端口无效")
        spider(p.spiderX)
        checkProfileFields(p)
        val node = if (p.clashConfig != null) {
            if (p.protocolOptions.isNotEmpty()) fail("protocolOptions", "与原始 Clash 配置并存的选项消费路径待核定")
            ClashYamlParser.config(p).toMutableMap()
        } else if (p.protocol in native) native(p).toMutableMap() else extended(p).toMutableMap()
        node["name"] = JsonPrimitive(name)
        if (node["type"] != JsonPrimitive(type(p.protocol))) fail("type", "协议与配置不一致")
        if (node["dialer-proxy"] != null) fail("dialer-proxy", "原始节点外部链路引用消费边界待核定")
        normalizeTransport(node, p)
        val result = JsonObject(node)
        checkGaps(result, p)
        MihomoOptionSchema.validate(result)
        return result
    }

    internal fun spider(value: String) {
        // Approved initial-path approximation only; NOT preservation of Xray spider lifecycle.
        if (value != "" && value != "/") fail("spiderX", "单核内核不支持该字段的非根路径或查询参数；仅空值和无参 / 可作初始路径近似")
    }

    private fun native(p: ServerProfile): JsonObject = buildJsonObject {
        if (p.protocolOptions.isNotEmpty()) fail("protocolOptions", "手工原生节点的扩展字段消费入口待核定；请保留原始配置")
        put("type", type(p.protocol)); put("name", PROXY); put("server", p.address); put("port", p.port); put("udp", true)
        when (p.protocol) {
            Protocol.VMESS -> { put("uuid", p.uuid); put("alterId", p.alterId); put("cipher", p.security.ifEmpty { "auto" }) }
            Protocol.VLESS -> { put("uuid", p.uuid); if (p.flow.isNotEmpty()) put("flow", p.flow); put("encryption", p.encryption.ifEmpty { "none" }) }
            Protocol.TROJAN -> put("password", p.password)
            Protocol.SHADOWSOCKS -> { put("password", p.password); put("cipher", p.security) }
            else -> error("unreachable")
        }
        if (p.protocol == Protocol.SHADOWSOCKS) {
            if (p.network !in setOf("", "tcp", "raw") || p.tls !in setOf("", "none") || p.headerType !in setOf("", "none"))
                fail("network/tls/headerType", "SS 手工节点只支持原有 TCP/none；插件须来自原始 Clash 配置")
            if (listOf(p.host, p.path, p.serviceName, p.alpn, p.sni, p.fingerprint, p.shortId, p.publicKey).any { it.isNotEmpty() } || p.allowInsecure)
                fail("transport", "SS 手工节点携带无消费入口的传输/TLS 字段，已进入批量缺口清单")
        } else {
            if (p.tls !in setOf("", "none", "tls", "reality")) fail("tls", "未知安全层")
            if (p.protocol == Protocol.TROJAN && p.tls != "tls" && p.tls != "reality") fail("tls", "Trojan 明文与单核固定 TLS 语义不一致，待处置")
            if (p.protocol != Protocol.TROJAN) put("tls", p.tls in setOf("tls", "reality"))
            val sni = p.sni.ifEmpty { p.host.ifEmpty { p.address } }
            if (p.tls in setOf("tls", "reality") || p.protocol == Protocol.TROJAN) {
                put(if (p.protocol == Protocol.TROJAN) "sni" else "servername", sni)
                put("skip-cert-verify", p.allowInsecure)
            } else if (p.sni.isNotEmpty()) fail("sni", "关闭 TLS 时没有 SNI 消费入口")
            if (p.alpn.isNotBlank()) put("alpn", strings(p.alpn))
            if (p.fingerprint.isNotEmpty() || p.tls == "reality") put("client-fingerprint", p.fingerprint.ifEmpty { "chrome" })
            if (p.tls == "reality") putJsonObject("reality-opts") { put("public-key", p.publicKey); put("short-id", p.shortId) }
            else if (p.publicKey.isNotEmpty() || p.shortId.isNotEmpty()) fail("publicKey/shortId", "非 Reality 配置的字段无消费入口")
            if (p.protocol != Protocol.VLESS && p.flow.isNotEmpty()) fail("flow", "该协议无 flow 消费入口")
            if (p.protocol != Protocol.VLESS && p.encryption !in setOf("", "none")) fail("encryption", "该协议无 encryption 消费入口")
            val network = p.network.ifEmpty { "tcp" }
            if (p.headerType !in setOf("", "none")) fail("headerType", "TCP HTTP 头伪装等价性未完成，不降为普通 TCP")
            put("network", if (network == "raw") "tcp" else network)
            when (network) {
                "ws", "httpupgrade" -> putJsonObject("ws-opts") {
                    put("path", p.path.ifEmpty { "/" }); if (p.host.isNotEmpty()) putJsonObject("headers") { put("Host", p.host) }
                }
                "grpc" -> putJsonObject("grpc-opts") { put("grpc-service-name", p.serviceName) }
                "xhttp" -> putJsonObject("xhttp-opts") { put("path", p.path.ifEmpty { "/" }); if (p.host.isNotEmpty()) put("host", p.host) }
                "tcp", "raw" -> if (p.path.isNotEmpty() || (p.host.isNotEmpty() && (p.sni.isNotEmpty() || p.tls !in setOf("tls", "reality"))) || p.serviceName.isNotEmpty()) fail("path/host/serviceName", "普通 TCP 的附加传输字段没有消费入口")
                else -> fail("network", "未知传输方式")
            }
            if (network != "grpc" && p.serviceName.isNotEmpty()) fail("serviceName", "非 gRPC 传输没有此字段消费入口")
            if (network == "grpc" && (p.path.isNotEmpty() || (p.host.isNotEmpty() && (p.sni.isNotEmpty() || p.tls !in setOf("tls", "reality")))))
                fail("path/host", "gRPC 不消费此 path 或被 SNI 覆盖的 host")
        }
    }

    private fun extended(p: ServerProfile): JsonObject {
        checkOptionKeys(p)
        if (p.network !in setOf("", "tcp", "raw") || p.headerType !in setOf("", "none") || p.host.isNotEmpty() || p.path.isNotEmpty() || p.serviceName.isNotEmpty())
            fail("transport", "该扩展协议的通用传输字段消费入口待核定")
        if (p.tls == "reality" || p.shortId.isNotEmpty() || (p.protocol != Protocol.WIREGUARD && p.publicKey.isNotEmpty()))
            fail("reality", "该扩展协议无通用 Reality 消费入口")
        val generated = ExtendedConfigBuilder.build(p).toMutableMap()
        if (p.protocol in setOf(Protocol.WIREGUARD, Protocol.SHADOWSOCKSR, Protocol.MIERU, Protocol.SUDOKU) && p.tls == "tls")
            fail("tls", "该协议没有独立通用 TLS 开关消费入口")
        // These are synthesized convenience flags, not user option fields: the corresponding
        // transports have intrinsic UDP support rather than a configurable udp option.
        if (p.protocol in setOf(Protocol.HYSTERIA, Protocol.HYSTERIA2, Protocol.TUIC, Protocol.SUDOKU, Protocol.HTTP)) generated.remove("udp")
        if (p.protocol in setOf(Protocol.WIREGUARD, Protocol.SHADOWSOCKSR, Protocol.MIERU, Protocol.SUDOKU)) {
            if (p.allowInsecure || p.sni.isNotEmpty() || p.alpn.isNotEmpty()) fail("tls", "该协议的通用 TLS 字段消费入口待核定")
            generated.remove("skip-cert-verify")
        }
        return JsonObject(generated)
    }

    private fun checkProfileFields(p: ServerProfile) {
        if (p.clashConfig != null) {
            if (p.spiderX !in setOf("", "/")) spider(p.spiderX)
            if (p.headerType !in setOf("", "none")) fail("headerType", "原始 Clash 与通用头伪装字段并存，消费路径待核定")
            return // Raw JSON is authoritative, as in dual; mirrored fields are import/edit projections.
        }
        // G04: reject before the shared builder substitutes certificate verification for SNI.
        if (p.protocol == Protocol.SOCKS && p.sni.isNotEmpty())
            fail("sni", "单核内核不支持该字段；name-cert-verify 不是 SNI，禁止顶替")
        if (p.protocol !in setOf(Protocol.VMESS, Protocol.VLESS, Protocol.TUIC) && p.uuid.isNotEmpty()) fail("uuid", "该协议无 UUID 消费入口")
        if (p.protocol !in setOf(Protocol.VMESS, Protocol.SHADOWSOCKS, Protocol.SHADOWSOCKSR) && p.security !in setOf("", "auto")) fail("security", "该协议无 cipher 消费入口")
        if (p.protocol != Protocol.VMESS && p.alterId != 0) fail("alterId", "该协议无 alterId 消费入口")
        if (p.protocol != Protocol.VLESS && p.flow.isNotEmpty()) fail("flow", "该协议无 flow 消费入口")
        if (p.protocol != Protocol.VLESS && p.encryption !in setOf("", "none")) fail("encryption", "该协议无 encryption 消费入口")
        if (p.protocol in setOf(Protocol.VMESS, Protocol.VLESS, Protocol.WIREGUARD) && p.password.isNotEmpty()) fail("password", "该协议无该通用密码消费入口")
        if (p.protocol !in setOf(Protocol.HTTP, Protocol.SOCKS, Protocol.MIERU) && p.username.isNotEmpty()) fail("username", "该协议无用户名消费入口")
        if (p.protocol !in native && p.protocol != Protocol.ANYTLS && p.fingerprint.isNotEmpty()) fail("fingerprint", "该协议未找到 client fingerprint 消费入口")
        if (p.protocol !in native && p.tls !in setOf("", "none", "tls")) fail("tls", "扩展协议安全层不支持此值")
    }

    private fun normalizeTransport(node: MutableMap<String, JsonElement>, p: ServerProfile) {
        val network = node.text("network")
        if (network == "raw") node["network"] = JsonPrimitive("tcp")
        if (network == "httpupgrade") {
            if (p.protocol !in setOf(Protocol.VMESS, Protocol.VLESS, Protocol.TROJAN)) fail("network", "此协议无通用 HTTP Upgrade 消费入口")
            node["network"] = JsonPrimitive("ws")
            val ws = (node["ws-opts"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
            ws["v2ray-http-upgrade"] = JsonPrimitive(true); node["ws-opts"] = JsonObject(ws)
        }
        // Keep both flags verbatim for raw Clash WS/plugin configs. The upstream EarlyConn
        // condition is conjunction; an isolated fast-open flag is not promoted into Upgrade.
    }

    private fun checkGaps(n: JsonObject, p: ServerProfile) {
        val net = n.text("network"); val kind = type(p.protocol)
        if (p.protocol == Protocol.VMESS && (n["alterId"]?.jsonPrimitive?.intOrNull ?: 0) != 0) fail("alterId", "单核暂不支持 VMess alterId > 0；兼容性补丁未完成")
        if (net == "xhttp" && p.protocol != Protocol.VLESS) fail("network", "单核不支持非 VLESS 的 xhttp")
        if (net !in setOf("", "tcp", "ws", "grpc", "xhttp")) fail("network", "此传输的等价语义待核定")
        if (n["http-opts"] != null || n["h2-opts"] != null) fail("http-opts", "HTTP 头伪装/HTTP2 等价性待核定")
        if (n["ws-headers"] != null) fail("ws-headers", "pin 版仅声明该字段，未找到消费入口；保留待处置")
        if (net in setOf("ws", "grpc") && n["alpn"] != null && p.protocol in setOf(Protocol.VMESS, Protocol.VLESS))
            fail("alpn", "该传输固定 ALPN，与用户指定列表的语义不一致，待处置")
        if (net == "ws" && n["reality-opts"] != null && p.protocol in setOf(Protocol.VMESS, Protocol.VLESS))
            fail("reality-opts", "WS 路径未消费 Reality；不得退为普通 TLS，待处置")
        if (p.protocol == Protocol.WIREGUARD && n["allowed-ips"] != null && (n["peers"] as? JsonArray).isNullOrEmpty())
            fail("allowed-ips", "顶层单 peer 路径未消费此字段，peer 转换方案待处置")
        if (p.protocol == Protocol.TUIC) {
            if (!n.text("token").isEmpty() && (n["uuid"] != null || n["password"] != null)) fail("token", "TUIC v4 token 分支不消费 UUID/password")
            if (n.text("token").isEmpty() && (n["fast-open"] != null || n["request-timeout"] != null)) fail("fast-open/request-timeout", "TUIC v5 分支没有对应消费入口，待批量处置")
        }
        if (p.protocol == Protocol.MIERU && n["mtu"] != null) {
            val mtu = n["mtu"]?.jsonPrimitive?.intOrNull
            if (mtu == null || (mtu != 0 && mtu !in 1280..1500)) fail("mtu", "mieru MTU 必须为 0 或 1280–1500；由 single 官方客户端 shim 消费")
        }
        if (n["plugin"] != null || n["plugin-opts"] != null) checkPlugin(n)
        val active = mapOf("ws-opts" to "ws", "grpc-opts" to "grpc", "xhttp-opts" to "xhttp", "mkcp-opts" to "mkcp", "mekya-opts" to "mekya")
        active.forEach { (key, transport) -> if (n[key] != null && net != transport) fail(key, "当前 network 不消费该传输对象") }
        if (net == "grpc" && p.protocol == Protocol.TROJAN && n["alpn"] != null)
            fail("alpn", "Trojan gRPC 固定 h2，不消费用户 ALPN，待批量处置")
        if (net == "ws" && n["client-fingerprint"] != null)
            fail("client-fingerprint", "普通 WS TLS 分支未消费客户端指纹；专用 TLS 包装分支待批量处置")
        if (net == "ws" && p.protocol == Protocol.TROJAN && n["reality-opts"] != null)
            fail("reality-opts", "Trojan WS 分支不消费 Reality，待批量处置")
        if (p.protocol in setOf(Protocol.VMESS, Protocol.VLESS, Protocol.HTTP, Protocol.SOCKS) && n["tls"] != JsonPrimitive(true) && listOf("servername", "sni", "reality-opts", "ech-opts", "certificate", "private-key", "fingerprint", "name-cert-verify", "shadow-tls-opts", "restls-opts", "jls-opts", "tlsmirror-opts").any { n[it] != null })
            fail("tls", "TLS 关闭时附加 TLS 字段没有消费入口")
        if (p.protocol in setOf(Protocol.VMESS, Protocol.VLESS) && n["tls"] != JsonPrimitive(true) && (n["alpn"] != null || n["client-fingerprint"] != null || n["skip-cert-verify"] == JsonPrimitive(true)))
            fail("tls", "TLS 缺省关闭时附加字段没有消费入口")
        checkConditionalConsumers(n, p)
        if (n["reality-opts"] != null && n["alpn"] != null) fail("alpn", "Reality 客户端固定 h2/http1.1，与用户指定列表语义待核定")
        if (n["reality-opts"] != null && n["skip-cert-verify"] == JsonPrimitive(true)) fail("allowInsecure", "Reality 独立证书校验逻辑不消费此开关，待处置")
        if (n["tls"] == JsonPrimitive(false) && ((p.protocol !in setOf(Protocol.HTTP, Protocol.SOCKS) && n["alpn"] != null) || n["client-fingerprint"] != null || n["skip-cert-verify"] == JsonPrimitive(true))) fail("tls", "关闭 TLS 时附加 TLS 字段无消费入口")
        if (kind == "http" && n["udp"] != null) fail("udp", "HTTP outbound 无 UDP；未向无消费入口输出该字段")
    }

    /** A declaration alone is insufficient when a sibling option switches its consumer off. */
    private fun checkConditionalConsumers(n: JsonObject, p: ServerProfile) {
        fun present(vararg keys: String) = keys.filter { n[it] != null }
        fun exclusive(vararg keys: String) {
            if (present(*keys).size > 1) fail(keys.joinToString("/"), "并存选项存在覆盖或互斥，消费语义待批量处置")
        }
        if (p.protocol in setOf(Protocol.HYSTERIA, Protocol.HYSTERIA2, Protocol.TUIC, Protocol.WIREGUARD) && present("tfo", "mptcp").isNotEmpty())
            fail("tfo/mptcp", "此协议的 BasicOption 未转发这两个字段，待批量处置")
        if (p.protocol in setOf(Protocol.VMESS, Protocol.VLESS)) {
            exclusive("packet-encoding", "packet-addr", "xudp")
            if (p.protocol == Protocol.VLESS && n.text("flow") !in setOf("", "xtls-rprx-vision"))
                fail("flow", "pin 内核可能截断或忽略该 flow，等价语义待核定")
        }
        if (p.protocol == Protocol.HYSTERIA) {
            exclusive("auth", "auth-str"); exclusive("up", "up-speed"); exclusive("down", "down-speed"); exclusive("protocol", "obfs-protocol")
        }
        if (p.protocol == Protocol.HYSTERIA2) {
            if (n.text("obfs").isEmpty() && present("obfs-password", "obfs-min-packet-size", "obfs-max-packet-size").isNotEmpty())
                fail("obfs", "未启用 obfs 时不消费附加参数")
            if (n.text("obfs") != "gecko" && present("obfs-min-packet-size", "obfs-max-packet-size").isNotEmpty())
                fail("obfs", "非 gecko 分支不消费包大小参数")
        }
        if (p.protocol == Protocol.WIREGUARD) {
            if (!(n["peers"] as? JsonArray).isNullOrEmpty() && present("public-key", "pre-shared-key", "allowed-ips").isNotEmpty())
                fail("peers", "显式 peers 不消费顶层 peer 密钥或 allowed-ips")
            if (n["dns"] != null && n["remote-dns-resolve"] != JsonPrimitive(true)) fail("dns", "未启用 remote-dns-resolve 时不消费 dns")
        }
        if (p.protocol == Protocol.SUDOKU) {
            if (n["httpmask"] != null && present("http-mask", "http-mask-mode", "http-mask-tls", "http-mask-host", "path-root", "multiplex", "http-mask-multiplex").isNotEmpty())
                fail("httpmask", "嵌套与扁平 HTTP mask 参数存在覆盖，待批量处置")
            exclusive("multiplex", "http-mask-multiplex")
        }
        val security = present("reality-opts", "shadow-tls-opts", "restls-opts", "jls-opts", "tlsmirror-opts")
        if (security.size > 1) fail("tls", "安全包装配置并存的消费边界待批量处置")
        val xhttp = n["xhttp-opts"] as? JsonObject
        val download = xhttp?.get("download-settings") as? JsonObject
        if (download != null) {
            if (xhttp.text("mode") == "stream-one") fail("download-settings", "stream-one 不支持独立下载配置")
            if (download["tls"] == JsonPrimitive(false) && download.keys.any { it in setOf("alpn", "ech-opts", "reality-opts", "shadow-tls-opts", "restls-opts", "jls-opts", "servername", "client-fingerprint", "fingerprint", "skip-cert-verify", "name-cert-verify", "certificate", "private-key") })
                fail("download-settings.tls", "下载 TLS 关闭时不消费附加 TLS 参数")
            if (download["reality-opts"] != null && (download["alpn"] != null || download["skip-cert-verify"] == JsonPrimitive(true)))
                fail("download-settings.reality-opts", "Reality 下载分支不消费用户 ALPN/skip-cert-verify")
        }
    }

    private fun checkPlugin(n: JsonObject) {
        val opts = n["plugin-opts"] as? JsonObject ?: fail("plugin-opts", "插件选项应为对象")
        if (n.text("plugin") != "v2ray-plugin") fail("plugin", "其他插件的动态字段消费入口待批量处置")
        val keys = setOf("mode", "host", "path", "tls", "ech-opts", "fingerprint", "certificate", "private-key", "headers", "skip-cert-verify", "name-cert-verify", "mux", "v2ray-http-upgrade", "v2ray-http-upgrade-fast-open")
        if ((opts.keys - keys).isNotEmpty()) fail("plugin-opts", "插件含未审定的消费字段")
        MihomoOptionSchema.validatePlugin(opts)
        if (opts["tls"] != JsonPrimitive(true) && listOf("ech-opts", "fingerprint", "certificate", "private-key", "skip-cert-verify", "name-cert-verify").any { opts[it] != null })
            fail("plugin-opts.tls", "插件 TLS 关闭时附加字段没有消费入口")
        if (opts.text("mode") != "websocket") fail("plugin-opts.mode", "HTTP Upgrade 插件边界仅审定 websocket 模式")
    }

    internal fun upgradeUsesEarlyConn(node: JsonObject): Boolean {
        val ws = node["ws-opts"] as? JsonObject ?: return false
        return ws["v2ray-http-upgrade"] == JsonPrimitive(true) && ws["v2ray-http-upgrade-fast-open"] == JsonPrimitive(true)
    }

    private fun listeners(s: AppSettings, tunnelPort: Int?, auth: Pair<String, String>?): List<JsonObject> = buildList {
        fun listener(name: String, port: Int, udp: Boolean, credentials: Pair<String, String>?): JsonObject = buildJsonObject {
            put("type", "socks"); put("name", name); put("listen", "127.0.0.1"); put("port", port); put("udp", udp)
            put("users", JsonArray(if (credentials == null) emptyList() else listOf(buildJsonObject { put("username", credentials.first); put("password", credentials.second) })))
        }
        if (tunnelPort != null) add(listener("tun-in", tunnelPort, true, auth))
        if (s.socksEnabled) add(listener("socks-in", s.socksPort, s.socksUdp,
            if (s.socksUsername.isNotBlank() && s.socksPassword.isNotBlank()) s.socksUsername to s.socksPassword else null))
    }

    private fun dns(s: AppSettings): JsonObject = buildJsonObject {
        fun server(ip: String, proxy: Boolean) = (if (proxy) "tcp://" else "udp://") + (if (':' in ip) "[$ip]" else ip) + (if (proxy) "#proxy" else "#DIRECT")
        put("enable", true); put("ipv6", s.ipv6Enabled)
        put("enhanced-mode", if (s.dnsProxyEnabled && s.fakeDns) "fake-ip" else "redir-host")
        put("fake-ip-range", "198.18.0.1/16"); put("fake-ip-range6", "fc00::/18")
        put("nameserver", JsonArray((if (s.dnsProxyEnabled) listOf(server(s.remoteDns, true)) else s.dnsServers.map { server(it, false) }).map(::JsonPrimitive)))
        put("proxy-server-nameserver", strings(s.domesticDns)); put("direct-nameserver", strings(s.domesticDns))
        if (s.dnsProxyEnabled) putJsonObject("nameserver-policy") {
            if (s.routingMode == RoutingMode.RULE) put("geosite:cn", JsonArray(listOf(JsonPrimitive(server(s.domesticDns, false)))))
            s.routingRules.filter { it.enabled && it.action == RouteAction.DIRECT }.flatMap { it.domains }.forEach { domain ->
                val key = when { domain.startsWith("full:") -> domain.removePrefix("full:"); domain.startsWith("domain:") -> "+." + domain.removePrefix("domain:"); domain.startsWith("geosite:") -> domain; else -> fail("dns.domains", "该域名条件的 DNS policy 等价性待核定") }
                put(key, JsonArray(listOf(JsonPrimitive(server(s.domesticDns, false)))))
            }
        }
    }

    internal fun rules(s: AppSettings): List<String> = when (s.routingMode) {
        RoutingMode.GLOBAL -> listOf("MATCH,proxy")
        RoutingMode.DIRECT -> listOf("MATCH,DIRECT")
        RoutingMode.RULE -> s.routingRules.filter { it.enabled && (it.id != "builtin:private" || s.bypassLan) }.map(::rule) + "MATCH,proxy"
    }

    internal fun rule(r: RoutingRule): String {
        r.validate()
        if (r.protocols.isNotEmpty()) fail("protocols", "不支持嗅探协议条件：等待 shim 等价验证；未改动现行 bittorrent/destOverride")
        fun atom(type: String, payload: String): String {
            if (payload.any { it in "()," }) fail("rules", "逻辑规则载荷含未验证的分隔符")
            return "$type,$payload"
        }
        fun any(parts: List<String>) = if (parts.size == 1) parts.single() else "OR,(" + parts.joinToString(",") { "($it)" } + ")"
        fun ip(v: String, source: Boolean): String = when {
            v.startsWith("ext:") -> fail("ext", "外部 geo 资源消费格式待核定")
            v == "geoip:private" && !source && r.id == "builtin:private" && r.ips == listOf("geoip:private") -> any(privateCidrs.map { ip(it, source) })
            v.startsWith("geoip:") -> atom(if (source) "SRC-GEOIP" else "GEOIP", v.removePrefix("geoip:"))
            else -> atom(if (source) "SRC-IP-CIDR" else if (':' in v) "IP-CIDR6" else "IP-CIDR", v + if ('/' in v) "" else if (':' in v) "/128" else "/32")
        }
        val fields = buildList {
            if (r.domains.isNotEmpty()) add(any(r.domains.map { d -> when {
                d.startsWith("full:") -> atom("DOMAIN", d.removePrefix("full:"))
                d.startsWith("domain:") -> atom("DOMAIN-SUFFIX", d.removePrefix("domain:"))
                d.startsWith("keyword:") -> atom("DOMAIN-KEYWORD", d.removePrefix("keyword:"))
                d.startsWith("regexp:") -> atom("DOMAIN-REGEX", d.removePrefix("regexp:"))
                d.startsWith("geosite:") -> atom("GEOSITE", d.removePrefix("geosite:"))
                d.startsWith("ext:") -> fail("ext", "外部域名资源格式待核定")
                else -> fail("domains", "无前缀 Xray 子串语义与 DOMAIN-KEYWORD 待核定")
            } }))
            if (r.ips.isNotEmpty()) add(any(r.ips.map { ip(it, false) }))
            if (r.source.isNotEmpty()) add(any(r.source.map { ip(it, true) }))
            if (r.port.isNotBlank()) add(any(r.port.split(',').map { atom("DST-PORT", it.trim()) }))
            if (r.sourcePort.isNotBlank()) add(any(r.sourcePort.split(',').map { atom("SRC-PORT", it.trim()) }))
            if (r.network.isNotBlank()) add(any(r.network.split(',').map { atom("NETWORK", it.uppercase()) }))
        }
        val condition = if (fields.size == 1) fields.single() else "AND,(" + fields.joinToString(",") { "($it)" } + ")"
        val target = when (r.action) { RouteAction.PROXY -> "proxy"; RouteAction.DIRECT -> "DIRECT"; RouteAction.BLOCK -> "REJECT" }
        return "$condition,$target"
    }

    private fun checkOptionKeys(p: ServerProfile) {
        val common = setOf("pinSHA256", "fingerprint") // Other common aliases are not read by the existing builder.
        val protocol = when (p.protocol) {
            Protocol.HYSTERIA -> setOf("auth", "up", "upmbps", "down", "downmbps", "ports", "mport", "protocol", "hop-interval", "hopInterval", "obfs", "obfsParam", "obfs-password")
            Protocol.HYSTERIA2 -> setOf("up", "upmbps", "down", "downmbps", "ports", "mport", "hop-interval", "hopInterval", "obfs", "obfs-password", "obfsParam", "ech")
            Protocol.TUIC -> setOf("token", "congestion-controller", "congestion_control", "udp-relay-mode", "udp_relay_mode", "disable-sni", "disable_sni", "reduce-rtt", "reduce_rtt", "fast-open", "fast_open", "heartbeat-interval", "heartbeat", "request-timeout", "max-udp-relay-packet-size")
            Protocol.WIREGUARD -> setOf("private-key", "public-key", "publickey", "address", "local-address", "ip", "ipv6", "pre-shared-key", "presharedkey", "preshared-key", "mtu", "persistent-keepalive", "keepalive", "reserved", "allowed-ips", "allowedips", "dns")
            Protocol.SHADOWSOCKSR -> setOf("protocol", "protocol-param", "protoparam", "obfs", "obfs-param", "obfsparam")
            Protocol.ANYTLS -> setOf("fp", "client-fingerprint", "clientFingerprint", "client-metadata", "clientMetadata", "idle-session-check-interval", "idleSessionCheckInterval", "idle-session-timeout", "idleSessionTimeout", "min-idle-session", "minIdleSession", "disable-reuse", "disableReuse", "ech-opts", "shadow-tls-opts", "restls-opts", "jls-opts")
            Protocol.SUDOKU -> setOf("aead-method", "aeadMethod", "table-type", "tableType", "multiplex", "http-mask-mode", "httpMaskMode", "http-mask-host", "httpMaskHost", "path-root", "pathRoot", "custom-table", "customTable", "padding-min", "paddingMin", "padding-max", "paddingMax", "enable-pure-downlink", "enablePureDownlink", "http-mask", "httpMask", "http-mask-tls", "httpMaskTls", "http-mask-multiplex", "httpMaskMultiplex", "custom-tables", "customTables", "httpmask")
            Protocol.MIERU -> setOf("transport", "port-range", "multiplexing", "handshake-mode", "traffic-pattern", "mtu")
            Protocol.HTTP, Protocol.SOCKS -> emptySet()
            else -> emptySet()
        }
        if ((p.protocolOptions.keys - common - protocol).isNotEmpty()) fail("protocolOptions", "包含未知或未审定消费入口的字段；原数据保留")
        val aliasGroups = listOf(listOf("up", "upmbps"), listOf("down", "downmbps"), listOf("ports", "mport"),
            listOf("hop-interval", "hopInterval"), listOf("publickey", "public-key"), listOf("address", "local-address", "ip"),
            listOf("persistent-keepalive", "keepalive"), listOf("allowed-ips", "allowedips"), listOf("congestion-controller", "congestion_control"),
            listOf("udp-relay-mode", "udp_relay_mode"), listOf("fast-open", "fast_open"), listOf("disable-sni", "disable_sni"),
            listOf("reduce-rtt", "reduce_rtt"), listOf("heartbeat-interval", "heartbeat"), listOf("pinSHA256", "fingerprint"),
            listOf("pre-shared-key", "presharedkey", "preshared-key"), listOf("protocol-param", "protoparam"), listOf("obfs-param", "obfsparam"),
            listOf("fp", "client-fingerprint", "clientFingerprint"), listOf("obfs-password", "obfsParam"),
            listOf("client-metadata", "clientMetadata"), listOf("idle-session-check-interval", "idleSessionCheckInterval"),
            listOf("idle-session-timeout", "idleSessionTimeout"), listOf("min-idle-session", "minIdleSession"), listOf("disable-reuse", "disableReuse"),
            listOf("aead-method", "aeadMethod"), listOf("table-type", "tableType"), listOf("http-mask-mode", "httpMaskMode"),
            listOf("http-mask-host", "httpMaskHost"), listOf("path-root", "pathRoot"), listOf("custom-table", "customTable"),
            listOf("padding-min", "paddingMin"), listOf("padding-max", "paddingMax"), listOf("enable-pure-downlink", "enablePureDownlink"),
            listOf("http-mask", "httpMask"), listOf("http-mask-tls", "httpMaskTls"), listOf("http-mask-multiplex", "httpMaskMultiplex"),
            listOf("custom-tables", "customTables"))
        aliasGroups.forEach { keys -> if (keys.mapNotNull { p.protocolOptions[it] }.distinct().size > 1) fail(keys.first(), "重复别名值冲突") }
        if (p.protocol == Protocol.HYSTERIA) {
            if (p.protocolOptions["auth"] != null) fail("auth", "此别名未被既有生成器读取，待批量处置")
            if (p.protocolOptions["obfs"] != "xplus" && listOf("obfsParam", "obfs-password").any { p.protocolOptions[it] != null })
                fail("obfsParam", "非 xplus 分支不消费此字段")
        }
        if (p.protocol == Protocol.HYSTERIA2 && p.protocolOptions["obfs"].isNullOrEmpty() && listOf("obfs-password", "obfsParam").any { p.protocolOptions[it] != null })
            fail("obfs-password", "obfs 未启用时不消费此字段，待批量处置")
        if (p.protocol == Protocol.TUIC && p.protocolOptions["token"] != null && (p.uuid.isNotEmpty() || p.password.isNotEmpty()))
            fail("token", "TUIC UUID/password 与 token 分支冲突，禁止丢弃 token")
        if (p.protocol == Protocol.WIREGUARD) {
            if (p.publicKey.isNotEmpty() && listOf("publickey", "public-key").any { p.protocolOptions[it]?.let { v -> v != p.publicKey } == true })
                fail("publicKey", "模型与协议选项冲突")
            val addresses = listOf("address", "local-address", "ip").firstNotNullOfOrNull { p.protocolOptions[it]?.takeIf(String::isNotEmpty) }
                .orEmpty().split(',').map(String::trim).filter(String::isNotEmpty)
            if (addresses.count { ':' !in it } > 1 || addresses.count { ':' in it } > 1 || addresses.any { '/' in it } || p.protocolOptions["ipv6"]?.contains('/') == true)
                fail("address", "WireGuard 多地址或前缀与现有生成器语义待批量处置，禁止丢弃")
            if (addresses.any { ':' in it } && !p.protocolOptions["ipv6"].isNullOrEmpty()) fail("ipv6", "重复 IPv6 地址来源待处置")
        }
        if (p.protocol == Protocol.ANYTLS && p.fingerprint.isNotEmpty() && listOf("fp", "client-fingerprint", "clientFingerprint").any { p.protocolOptions[it]?.let { v -> v != p.fingerprint } == true })
            fail("fingerprint", "模型与协议选项冲突")
        // Aliases already represented by the model must agree rather than silently overwrite.
        p.protocolOptions["alpn"]?.let { if (it != p.alpn) fail("alpn", "模型与扩展选项冲突") }
        listOf("sni", "peer").forEach { key -> p.protocolOptions[key]?.let { if (it != p.sni) fail(key, "模型与扩展选项冲突") } }
    }

    private fun type(p: Protocol): String = when (p) {
        Protocol.VMESS -> "vmess"; Protocol.VLESS -> "vless"; Protocol.TROJAN -> "trojan"; Protocol.SHADOWSOCKS -> "ss"
        Protocol.HYSTERIA -> "hysteria"; Protocol.HYSTERIA2 -> "hysteria2"; Protocol.TUIC -> "tuic"; Protocol.WIREGUARD -> "wireguard"
        Protocol.SHADOWSOCKSR -> "ssr"; Protocol.HTTP -> "http"; Protocol.SOCKS -> "socks5"; Protocol.MIERU -> "mieru"; Protocol.ANYTLS -> "anytls"; Protocol.SUDOKU -> "sudoku"
    }
    private fun strings(value: String) = JsonArray(value.split(',').map(String::trim).filter(String::isNotEmpty).map(::JsonPrimitive))
    private fun JsonObject.text(key: String) = (this[key] as? JsonPrimitive)?.content.orEmpty()
    private fun Map<String, JsonElement>.text(key: String) = (this[key] as? JsonPrimitive)?.content.orEmpty()
    private fun fail(field: String, reason: String): Nothing = throw MihomoMappingException(field, reason)
}
