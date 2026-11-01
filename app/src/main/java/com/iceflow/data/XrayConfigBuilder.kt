package com.iceflow.data

import com.iceflow.data.model.AppSettings
import com.iceflow.data.model.Protocol
import com.iceflow.data.model.RouteAction
import com.iceflow.data.model.ServerProfile
import com.iceflow.data.model.ServerStore
import com.iceflow.data.model.RoutingMode
import com.iceflow.data.model.LoadBalanceStrategy
import com.iceflow.data.model.RoutingOutboundTargets
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * 将 [ServerProfile] 转换为 xray-core JSON 配置。
 *
 * 数据流：TUN → tun2proxy (Rust) → SOCKS5 127.0.0.1:socksPort → xray-core → 远端服务器。
 */
object XrayConfigBuilder {

    const val PROXY_TAG = "proxy"

    /** DNS 服务器 tag：域匹配器缓存（MPH cache）以此为键，必须存在且唯一 */
    const val DOMESTIC_DNS_TAG = "dns-domestic"
    const val REMOTE_DNS_TAG = "dns-remote"

    /** 局域网/保留地址；使用 CIDR 而非 geoip:private，避免依赖 geoip.dat */
    private val PRIVATE_CIDRS = listOf(
        "10.0.0.0/8", "100.64.0.0/10", "127.0.0.0/8", "169.254.0.0/16",
        "172.16.0.0/12", "192.168.0.0/16", "224.0.0.0/4",
        "::1/128", "fc00::/7", "fe80::/10",
    )

    /**
     * @param includeInbound 测速时传 false，避免与正在运行的 SOCKS 端口冲突
     * @param tunnelAuth 可选的 TUN 入站鉴权凭据 (username, password)；为 null 时不鉴权
     */
    fun build(
        profile: ServerProfile,
        settings: AppSettings,
        includeInbound: Boolean = true,
        store: ServerStore? = null,
        tunnelPort: Int? = null,
        tunnelAuth: Pair<String, String>? = null
    ): String {
        settings.validate()
        require(tunnelPort == null || (tunnelPort in 1..65535 && (!settings.socksEnabled || tunnelPort != settings.socksPort) && (!settings.httpEnabled || tunnelPort != settings.httpPort)))
        val composition = profile.composition
        val local = store?.localGroups?.firstOrNull { composition == null && it.id == profile.groupId }
        val subscription = store?.subscriptions?.firstOrNull { composition == null && it.id == profile.subscriptionId }
        val frontId = subscription?.frontNodeId ?: local?.frontNodeId
        val landingId = subscription?.landingNodeId ?: local?.landingNodeId
        val balanceStrategy = composition?.takeIf { it.kind == com.iceflow.data.model.ProxyCompositionKind.BALANCE }?.strategy ?: local?.balanceStrategy
        fun resolve(id: String?, label: String): ServerProfile? = id?.let { selected ->
            requireNotNull(store?.servers?.firstOrNull { it.id == selected }) { "${label}已失效，请在分组设置中重新选择" }
        }
        val chain = if (composition != null) ProxyCompositions.resolve(profile, store)
        else if (balanceStrategy == null) listOfNotNull(resolve(frontId, "前置节点"), profile, resolve(landingId, "落地节点")) else {
            require(frontId == null && landingId == null) { "chaining and load balancing cannot be enabled together" }
            val group = requireNotNull(local)
            val groupMembers = requireNotNull(store).servers.filter { it.groupId == group.id && it.composition == null }
            val members = if (group.balanceNodeIds.isEmpty()) groupMembers else groupMembers.filter { it.id in group.balanceNodeIds }
            require(members.size >= 2) { "load balancing requires at least two nodes" }
            members
        }
        require(chain.map { it.id }.distinct().size == chain.size) { "代理链包含重复节点，请调整分组设置" }
        val tags = chain.indices.map { if (balanceStrategy != null) "lb-$it" else if (it == chain.lastIndex) PROXY_TAG else "chain-$it" }
        val adapters = chain.mapIndexedNotNull { index, node ->
            val options = if (node.clashConfig != null) ClashYamlParser.config(node) else if (node.protocol in ExtendedShareLinks.protocols) ExtendedConfigBuilder.build(node) else null
            options?.let { buildJsonObject { put("tag", tags[index]); if (balanceStrategy == null && index > 0) put("upstream", tags[index - 1]); put("options", it) } }
        }
        val ruleTargets = RoutingOutboundTargets.referenced(settings, store)
        val targetRouteTags = mutableMapOf<String, Pair<String, Boolean>>()
        val targetOutbounds = mutableListOf<JsonObject>()
        val targetAdapters = mutableListOf<JsonObject>()
        ruleTargets.forEach { target ->
            val finals = target.members.mapIndexed { memberIndex, member ->
                val memberTags = member.indices.map { hopIndex -> target.tag + "-" + memberIndex + "-" + hopIndex }
                member.indices.reversed().forEach { hopIndex ->
                    val node = member[hopIndex]
                    val base = outbound(node)
                    targetOutbounds += buildJsonObject {
                        base.forEach { (key, value) -> put(key, value) }
                        put("tag", memberTags[hopIndex])
                        if (hopIndex > 0 && (node.clashConfig == null && node.protocol !in ExtendedShareLinks.protocols)) {
                            put("streamSettings", buildJsonObject {
                                base["streamSettings"]?.jsonObject?.forEach { (key, value) -> put(key, value) }
                                putJsonObject("sockopt") { put("dialerProxy", memberTags[hopIndex - 1]) }
                            })
                        }
                    }
                    val options = if (node.clashConfig != null) ClashYamlParser.config(node) else if (node.protocol in ExtendedShareLinks.protocols) ExtendedConfigBuilder.build(node) else null
                    options?.let { targetAdapters += buildJsonObject { put("tag", memberTags[hopIndex]); if (hopIndex > 0) put("upstream", memberTags[hopIndex - 1]); put("options", it) } }
                }
                memberTags.last()
            }
            targetRouteTags[target.id] = if (target.group && finals.size > 1) target.tag to true else finals.first() to false
        }
        val allAdapters = adapters + targetAdapters
        val probingTargets = ruleTargets.filter { it.group && it.strategy in setOf(LoadBalanceStrategy.LEAST_PING, LoadBalanceStrategy.LEAST_LOAD) }
        val selectedProbeUrl = (composition?.testUrl ?: local?.balanceTestUrl)?.takeIf { it.isNotBlank() } ?: settings.testUrl
        val selectedProbeInterval = composition?.intervalSeconds ?: local?.balanceIntervalSeconds ?: 300
        val probeUrls = (if (balanceStrategy != null) listOf(selectedProbeUrl) else emptyList()) + probingTargets.map { it.testUrl.takeIf(String::isNotBlank) ?: settings.testUrl }
        val probeIntervals = (if (balanceStrategy != null) listOf(selectedProbeInterval) else emptyList()) + probingTargets.map { it.intervalSeconds }
        require(probeUrls.distinct().size <= 1 && probeIntervals.distinct().size <= 1) { "Xray 规则出口的最少延迟/负载策略必须共用探测地址和间隔 / Xray least-ping/load targets must share a probe URL and interval" }
        return buildJsonObject {
            if (chain.size == 1 && allAdapters.size == 1 && targetAdapters.isEmpty()) put("iceflowAdapter", adapters.single()["options"]!!)
            else if (allAdapters.isNotEmpty()) put("iceflowAdapters", JsonArray(allAdapters))
            if (balanceStrategy != null || probingTargets.isNotEmpty()) {
                putJsonObject("observatory") {
                    putJsonArray("subjectSelector") {
                        if (balanceStrategy != null) add("lb-")
                        probingTargets.forEach { add(it.tag + "-") }
                    }
                    put("probeURL", probeUrls.first())
                    put("probeInterval", probeIntervals.first().toString() + "s")
                }
            }
            putJsonObject("log") { put("loglevel", "warning") }
            putJsonObject("stats") {}
            putJsonObject("policy") {
                putJsonObject("system") {
                    put("statsInboundUplink", true)
                    put("statsInboundDownlink", true)
                    put("statsOutboundUplink", true)
                    put("statsOutboundDownlink", true)
                }
            }
            if (includeInbound && (settings.socksEnabled || settings.httpEnabled || tunnelPort != null)) {
                putJsonArray("inbounds") {
                    if (tunnelPort != null) addJsonObject {
                        put("tag", "tun-in")
                        put("listen", "127.0.0.1")
                        put("port", tunnelPort)
                        put("protocol", "socks")
                        putJsonObject("settings") {
                            if (tunnelAuth != null) {
                                put("auth", "password")
                                putJsonArray("accounts") {
                                    addJsonObject {
                                        put("user", tunnelAuth.first)
                                        put("pass", tunnelAuth.second)
                                    }
                                }
                            } else {
                                put("auth", "noauth")
                            }
                            put("udp", true)
                        }
                        putJsonObject("sniffing") {
                            put("enabled", true)
                            putJsonArray("destOverride") { add("http"); add("tls"); add("quic") }
                            put("routeOnly", true)
                        }
                    }
                    if (settings.socksEnabled) addJsonObject {
                        put("tag", "socks-in")
                        put("listen", if (settings.allowLan) "0.0.0.0" else "127.0.0.1")
                        put("port", settings.socksPort)
                        put("protocol", "socks")
                        putJsonObject("settings") {
                            val hasCredentials = settings.socksUsername.isNotBlank() && settings.socksPassword.isNotBlank()
                            put("auth", if (hasCredentials) "password" else "noauth")
                            if (hasCredentials) {
                                putJsonArray("accounts") {
                                    addJsonObject {
                                        put("user", settings.socksUsername)
                                        put("pass", settings.socksPassword)
                                    }
                                }
                            }
                            put("udp", settings.socksUdp)
                        }
                        putJsonObject("sniffing") {
                            put("enabled", true)
                            putJsonArray("destOverride") { add("http"); add("tls"); add("quic") }
                            put("routeOnly", true)
                        }
                    }
                    if (settings.httpEnabled) addJsonObject {
                        put("tag", "http-in")
                        put("listen", if (settings.allowLan) "0.0.0.0" else "127.0.0.1")
                        put("port", settings.httpPort)
                        put("protocol", "http")
                        putJsonObject("settings") {
                            if (settings.socksUsername.isNotBlank() && settings.socksPassword.isNotBlank()) putJsonArray("accounts") {
                                addJsonObject { put("user", settings.socksUsername); put("pass", settings.socksPassword) }
                            }
                        }
                    }
                }
            }
            putJsonArray("outbounds") {
                // Keep the logical proxy tag available to forced latency and connection probes.
                if (balanceStrategy != null) addJsonObject {
                    put("tag", PROXY_TAG); put("protocol", "loopback")
                    putJsonObject("settings") { put("inboundTag", "balance-in") }
                }
                // The first outbound remains the final hop for chains.
                (if (balanceStrategy == null) chain.indices.reversed() else chain.indices).forEach { index ->
                    val base = outbound(chain[index])
                    add(buildJsonObject {
                        base.forEach { (key, value) -> put(key, value) }
                        put("tag", tags[index])
                        if (balanceStrategy == null && index > 0 && adapters.none { it["tag"] == JsonPrimitive(tags[index]) }) {
                            put("streamSettings", buildJsonObject {
                                base["streamSettings"]?.jsonObject?.forEach { (key, value) -> put(key, value) }
                                putJsonObject("sockopt") { put("dialerProxy", tags[index - 1]) }
                            })
                        }
                    })
                }
                targetOutbounds.forEach { add(it) }
                addJsonObject { put("tag", "direct"); put("protocol", "freedom") }
                addJsonObject { put("tag", "block"); put("protocol", "blackhole") }
            }
            putJsonObject("dns") {
                putJsonArray("servers") {
                    // 国内 DNS：用于直连域名，避免 CDN 污染
                    val directDomains = settings.effectiveRoutingRules
                        .filter { it.enabled && it.outboundTargetId == null && it.action == RouteAction.DIRECT && it.domains.isNotEmpty() }
                        .flatMap { it.domains }
                    if (directDomains.isNotEmpty() || settings.routingMode == RoutingMode.RULE) {
                        addJsonObject {
                            put("address", xrayDnsAddress(settings.domesticDns))
                            // tag 用于域匹配器缓存（MPH cache）的键，必须存在且唯一
                            put("tag", DOMESTIC_DNS_TAG)
                            putJsonArray("domains") {
                                directDomains.forEach { domain -> add(domain) }
                                // 添加常见国内域名 geosite 分类
                                if (settings.routingMode == RoutingMode.RULE) {
                                    add("geosite:cn")
                                }
                            }
                        }
                    }
                    // 远端 DNS：默认，用于代理域名
                    addJsonObject {
                        put("address", xrayDnsAddress(settings.remoteDns))
                        put("tag", REMOTE_DNS_TAG)
                    }
                }
                // 优先使用 IP，减少 DNS 泄漏
                put("queryStrategy", if (settings.ipv6Enabled) "UseIP" else "UseIPv4")
            }
            putJsonObject("routing") {
                put("domainStrategy", "IPIfNonMatch")
                if (balanceStrategy != null || ruleTargets.any { it.group && it.members.size > 1 }) putJsonArray("balancers") {
                    if (balanceStrategy != null) addJsonObject {
                        put("tag", PROXY_TAG)
                        putJsonArray("selector") { add("lb-") }
                        put("fallbackTag", tags.first())
                        putJsonObject("strategy") {
                            put("type", when (balanceStrategy) {
                                LoadBalanceStrategy.RANDOM -> "random"
                                LoadBalanceStrategy.ROUND_ROBIN -> "roundRobin"
                                LoadBalanceStrategy.LEAST_PING -> "leastPing"
                                LoadBalanceStrategy.LEAST_LOAD -> "leastLoad"
                                else -> error("Strategy is not supported by Xray")
                            })
                        }
                    }
                    ruleTargets.filter { it.group && it.members.size > 1 }.forEach { target -> addJsonObject {
                        put("tag", target.tag)
                        putJsonArray("selector") { add(target.tag + "-") }
                        put("fallbackTag", target.tag + "-0-" + (target.members.first().lastIndex))
                        putJsonObject("strategy") { put("type", when (target.strategy) {
                            LoadBalanceStrategy.RANDOM -> "random"
                            LoadBalanceStrategy.ROUND_ROBIN -> "roundRobin"
                            LoadBalanceStrategy.LEAST_PING -> "leastPing"
                            LoadBalanceStrategy.LEAST_LOAD -> "leastLoad"
                            else -> error("Xray 不支持该规则分组策略 / Xray does not support this routing group strategy")
                        }) }
                    } }
                }
                putJsonArray("rules") {
                    if (balanceStrategy != null) addJsonObject {
                        put("type", "field"); putJsonArray("inboundTag") { add("balance-in") }
                        put("balancerTag", PROXY_TAG)
                    }
                    if (settings.routingMode == RoutingMode.GLOBAL) {
                        add(buildJsonObject { put("type", "field"); put("network", "tcp,udp"); if (balanceStrategy == null) put("outboundTag", PROXY_TAG) else put("balancerTag", PROXY_TAG) })
                    } else if (settings.routingMode == RoutingMode.DIRECT) {
                        add(buildJsonObject { put("type", "field"); put("network", "tcp,udp"); put("outboundTag", "direct") })
                    } else settings.effectiveRoutingRules.filter { it.enabled && (it.id != "builtin:private" || settings.bypassLan) }.forEach { rule ->
                        rule.validate()
                        val requested = rule.outboundTargetId?.let(targetRouteTags::get)
                        val resolvedRule = if (rule.outboundTargetId != null) rule.toXray(requested?.first ?: PROXY_TAG) else rule.toXray()
                        val routedRule = if (requested?.second == true) JsonObject(resolvedRule.toMutableMap().apply {
                            remove("outboundTag"); put("balancerTag", JsonPrimitive(requested.first))
                        }) else routeToBalancer(resolvedRule, balanceStrategy != null)
                        if (rule.id == "builtin:private" && rule.ips == listOf("geoip:private")) add(buildJsonObject {
                            routedRule.forEach { (k, v) -> put(k, v) }
                            put("ip", JsonArray(PRIVATE_CIDRS.map(::JsonPrimitive)))
                        }) else add(routedRule)
                    }
                    if (balanceStrategy != null && settings.routingMode == RoutingMode.RULE) addJsonObject {
                        put("type", "field"); put("network", "tcp,udp"); put("balancerTag", PROXY_TAG)
                    }
                }
            }
        }.toString()
    }

    private fun xrayDnsAddress(value: String): String = when {
        value.startsWith("tls://", ignoreCase = true) -> error("Xray core does not support DoT tls:// DNS endpoints")
        value.startsWith("quic://", ignoreCase = true) -> "quic+local://${value.removePrefix("quic://").removePrefix("QUIC://")}"
        value.startsWith("h3://", ignoreCase = true) -> "https://${value.removePrefix("h3://").removePrefix("H3://")}"
        else -> value
    }

    private fun routeToBalancer(rule: JsonObject, enabled: Boolean): JsonObject {
        if (!enabled || rule["outboundTag"] != JsonPrimitive(PROXY_TAG)) return rule
        return JsonObject(rule.toMutableMap().apply {
            remove("outboundTag")
            put("balancerTag", JsonPrimitive(PROXY_TAG))
        })
    }

    private fun outbound(p: ServerProfile): JsonObject = buildJsonObject {
        put("tag", PROXY_TAG)
        if (p.clashConfig != null) {
            put("protocol", "blackhole")
            return@buildJsonObject
        }
        when (p.protocol) {
            Protocol.VMESS -> {
                put("protocol", "vmess")
                putJsonObject("settings") {
                    putJsonArray("vnext") {
                        addJsonObject {
                            put("address", p.address)
                            put("port", p.port)
                            putJsonArray("users") {
                                addJsonObject {
                                    put("id", p.uuid)
                                    put("alterId", p.alterId)
                                    put("security", p.security.ifEmpty { "auto" })
                                }
                            }
                        }
                    }
                }
            }
            Protocol.VLESS -> {
                put("protocol", "vless")
                putJsonObject("settings") {
                    putJsonArray("vnext") {
                        addJsonObject {
                            put("address", p.address)
                            put("port", p.port)
                            putJsonArray("users") {
                                addJsonObject {
                                    put("id", p.uuid)
                                    put("encryption", p.encryption.ifEmpty { "none" })
                                    if (p.flow.isNotEmpty()) put("flow", p.flow)
                                }
                            }
                        }
                    }
                }
            }
            Protocol.TROJAN -> {
                put("protocol", "trojan")
                putJsonObject("settings") {
                    putJsonArray("servers") {
                        addJsonObject {
                            put("address", p.address)
                            put("port", p.port)
                            put("password", p.password)
                        }
                    }
                }
            }
            Protocol.SHADOWSOCKS -> {
                put("protocol", "shadowsocks")
                putJsonObject("settings") {
                    putJsonArray("servers") {
                        addJsonObject {
                            put("address", p.address)
                            put("port", p.port)
                            put("method", p.security)
                            put("password", p.password)
                        }
                    }
                }
            }
            else -> {
                // Replaced by the native adapter before this instance starts.
                // Blackhole fails closed when an older AAR is used accidentally.
                put("protocol", "blackhole")
            }
        }
        if (p.protocol !in ExtendedShareLinks.protocols) put("streamSettings", streamSettings(p))
    }

    private fun streamSettings(p: ServerProfile): JsonObject = buildJsonObject {
        val network = if (p.protocol == Protocol.SHADOWSOCKS) "tcp" else p.network.ifEmpty { "tcp" }
        val security = if (p.protocol == Protocol.SHADOWSOCKS) "none" else p.tls.ifEmpty { "none" }
        val serverName = p.sni.ifEmpty { p.host.ifEmpty { p.address } }
        put("network", network)
        put("security", security)

        when (security) {
            "tls" -> putJsonObject("tlsSettings") {
                put("serverName", serverName)
                put("allowInsecure", p.allowInsecure)
                val alpn = p.alpn.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                if (alpn.isNotEmpty()) putJsonArray("alpn") { alpn.forEach { add(it) } }
                if (p.fingerprint.isNotEmpty()) put("fingerprint", p.fingerprint)
            }
            "reality" -> putJsonObject("realitySettings") {
                put("serverName", serverName)
                put("fingerprint", p.fingerprint.ifEmpty { "chrome" })
                put("publicKey", p.publicKey)
                put("shortId", p.shortId)
                put("spiderX", p.spiderX)
            }
        }

        when (network) {
            "ws" -> putJsonObject("wsSettings") {
                put("path", p.path.ifEmpty { "/" })
                if (p.host.isNotEmpty()) put("host", p.host)
            }
            "httpupgrade" -> putJsonObject("httpupgradeSettings") {
                put("path", p.path.ifEmpty { "/" })
                if (p.host.isNotEmpty()) put("host", p.host)
            }
            "xhttp" -> putJsonObject("xhttpSettings") {
                put("path", p.path.ifEmpty { "/" })
                if (p.host.isNotEmpty()) put("host", p.host)
            }
            "grpc" -> putJsonObject("grpcSettings") {
                put("serviceName", p.serviceName)
            }
            "tcp", "raw" -> if (p.headerType == "http") {
                putJsonObject("tcpSettings") {
                    putJsonObject("header") {
                        put("type", "http")
                        putJsonObject("request") {
                            putJsonArray("path") { add(p.path.ifEmpty { "/" }) }
                            putJsonObject("headers") {
                                putJsonArray("Host") { add(p.host.ifEmpty { p.address }) }
                            }
                        }
                    }
                }
            }
        }
    }
}
