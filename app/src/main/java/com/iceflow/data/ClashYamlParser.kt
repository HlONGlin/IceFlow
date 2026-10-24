package com.iceflow.data

import com.iceflow.data.model.Protocol
import com.iceflow.data.model.ServerProfile
import kotlinx.serialization.json.*
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

/** Imports nodes only; application routing remains controlled by IceFlow settings. */
internal object ClashYamlParser {
    private val protocols = mapOf(
        "vmess" to Protocol.VMESS, "vless" to Protocol.VLESS, "trojan" to Protocol.TROJAN,
        "ss" to Protocol.SHADOWSOCKS, "ssr" to Protocol.SHADOWSOCKSR,
        "hysteria" to Protocol.HYSTERIA, "hysteria2" to Protocol.HYSTERIA2,
        "tuic" to Protocol.TUIC, "wireguard" to Protocol.WIREGUARD,
        "http" to Protocol.HTTP, "socks5" to Protocol.SOCKS, "mieru" to Protocol.MIERU,
    )

    fun looksLike(text: String): Boolean = text.startsWith('{') || text.startsWith('[') ||
        Regex("(?m)^\\s*(?:proxies\\s*:|proxy-providers\\s*:|-\\s+(?:\\{|(?:name|type)\\s*:))").containsMatchIn(text)

    fun parse(text: String): List<ServerProfile> {
        require(text.length <= 4 * 1024 * 1024) { "Clash 配置超过 4 MiB" }
        val options = LoaderOptions().apply {
            isAllowDuplicateKeys = false
            maxAliasesForCollections = 30
            nestingDepthLimit = 40
            codePointLimit = 4 * 1024 * 1024
        }
        val root = Yaml(SafeConstructor(options)).load<Any?>(text)
        val nodes = when (root) {
            is Map<*, *> -> root["proxies"] as? List<*>
                ?: error("Clash 配置没有 proxies 节点列表（不自动拉取 proxy-providers）")
            is List<*> -> root
            else -> error("Clash 配置格式无效")
        }
        require(nodes.size <= 10000) { "Clash 节点数量超过限制" }
        val budget = intArrayOf(200000)
        return nodes.mapNotNull { value ->
            val map = value as? Map<*, *> ?: error("Clash 节点必须是对象")
            val node = element(map, 0, budget).jsonObject
            val type = node.text("type").lowercase()
            val protocol = protocols[type] ?: return@mapNotNull null
            val name = node.text("name").ifBlank { node.text("server") }
            require(node.text("dialer-proxy").isEmpty()) { "节点 $name 使用暂不支持的 dialer-proxy 链式代理" }
            val server = node.text("server").removeSurrounding("[", "]")
            val port = node.text("port").toIntOrNull()
                ?: node.text("port-range").substringBefore('-').toIntOrNull()
            require(server.isNotBlank() && port != null && port in 1..65535) { "节点 $name 地址或端口无效" }
            val canonical = JsonObject(node + ("type" to JsonPrimitive(type)))
            val network = node.text("network").ifBlank { "tcp" }
            val ws = node["ws-opts"] as? JsonObject
            val grpc = node["grpc-opts"] as? JsonObject
            val reality = node["reality-opts"] as? JsonObject
            ServerProfile(
                name = name, protocol = protocol, address = server, port = port,
                uuid = node.text("uuid"), username = node.text("username"),
                password = node.text("password").ifEmpty { node.text("auth-str").ifEmpty { node.text("token") } },
                alterId = node.text("alterId").toIntOrNull() ?: 0,
                security = node.text("cipher").ifEmpty { "auto" }, flow = node.text("flow"),
                network = network, path = ws?.text("path").orEmpty(),
                host = (ws?.get("headers") as? JsonObject)?.text("Host").orEmpty(),
                serviceName = grpc?.text("grpc-service-name").orEmpty(),
                tls = when { reality != null -> "reality"; node.text("tls") == "true" || type in setOf("trojan", "hysteria", "hysteria2", "tuic") -> "tls"; else -> "none" },
                sni = node.text("servername").ifEmpty { node.text("sni") },
                alpn = (node["alpn"] as? JsonArray)?.joinToString(",") { it.jsonPrimitive.content }.orEmpty(),
                fingerprint = node.text("client-fingerprint"),
                publicKey = reality?.text("public-key").orEmpty().ifEmpty { node.text("public-key") },
                shortId = reality?.text("short-id").orEmpty(),
                allowInsecure = node.text("skip-cert-verify") == "true",
                clashConfig = canonical,
            )
        }
    }

    fun config(profile: ServerProfile): JsonObject {
        val node = requireNotNull(profile.clashConfig)
        require(protocols[node.text("type")] == profile.protocol) { "Clash 协议与节点不匹配" }
        return JsonObject(node + ("name" to JsonPrimitive(profile.name)))
    }

    fun encode(profile: ServerProfile): String = buildJsonObject {
        put("proxies", JsonArray(listOf(config(profile))))
    }.toString() // JSON is also valid YAML; no nested option is lost.

    private fun JsonObject.text(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

    private fun element(value: Any?, depth: Int, budget: IntArray): JsonElement {
        require(--budget[0] >= 0) { "Clash 配置展开后的字段数量超过限制" }
        require(depth <= 40) { "Clash 配置嵌套过深" }
        return when (value) {
            null -> JsonNull
            is String -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Map<*, *> -> JsonObject(value.entries.associate {
                require(it.key is String) { "Clash 字段名必须是文本" }
                it.key as String to element(it.value, depth + 1, budget)
            })
            is List<*> -> JsonArray(value.map { element(it, depth + 1, budget) })
            else -> error("Clash 字段类型不支持")
        }
    }

    /**
     * 解析 Clash 规则列表为 IceFlow RoutingRule。
     * Clash 规则格式：DOMAIN,example.com,DIRECT 或 IP-CIDR,1.1.1.1/24,PROXY
     * 返回可导入的规则列表。
     */
    fun parseRules(text: String): List<com.iceflow.data.model.RoutingRule> {
        require(text.length <= 4 * 1024 * 1024) { "配置超过 4 MiB" }
        val options = LoaderOptions().apply {
            isAllowDuplicateKeys = false
            maxAliasesForCollections = 30
            nestingDepthLimit = 40
            codePointLimit = 4 * 1024 * 1024
        }
        val root = Yaml(SafeConstructor(options)).load<Any?>(text) as? Map<*, *>
            ?: return emptyList()
        val rules = root["rules"] as? List<*> ?: return emptyList()
        require(rules.size <= 2000) { "规则数量超过限制" }

        val actionMap = mapOf(
            "PROXY" to com.iceflow.data.model.RouteAction.PROXY,
            "DIRECT" to com.iceflow.data.model.RouteAction.DIRECT,
            "REJECT" to com.iceflow.data.model.RouteAction.BLOCK,
        )

        return rules.mapIndexedNotNull { index, rule ->
            val ruleStr = rule as? String ?: return@mapIndexedNotNull null
            val parts = ruleStr.split(',')
            if (parts.size < 3) return@mapIndexedNotNull null

            val type = parts[0].trim().uppercase()
            val value = parts[1].trim()
            val policy = parts[2].trim().uppercase()
            val action = actionMap[policy] ?: return@mapIndexedNotNull null

            when (type) {
                "DOMAIN" -> com.iceflow.data.model.RoutingRule(
                    name = "Clash: $value",
                    action = action,
                    domains = listOf("full:$value")
                )
                "DOMAIN-SUFFIX" -> com.iceflow.data.model.RoutingRule(
                    name = "Clash: *.$value",
                    action = action,
                    domains = listOf("domain:$value")
                )
                "DOMAIN-KEYWORD" -> com.iceflow.data.model.RoutingRule(
                    name = "Clash: *$value*",
                    action = action,
                    domains = listOf("keyword:$value")
                )
                "GEOSITE" -> com.iceflow.data.model.RoutingRule(
                    name = "Clash: geosite:$value",
                    action = action,
                    domains = listOf("geosite:${value.lowercase()}")
                )
                "IP-CIDR", "IP-CIDR6" -> com.iceflow.data.model.RoutingRule(
                    name = "Clash: $value",
                    action = action,
                    ips = listOf(value)
                )
                "GEOIP" -> com.iceflow.data.model.RoutingRule(
                    name = "Clash: geoip:$value",
                    action = action,
                    ips = listOf("geoip:${value.lowercase()}")
                )
                else -> null
            }
        }.filter { runCatching { it.validate() }.isSuccess }
    }
}
