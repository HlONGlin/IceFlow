package com.iceflow.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.*
import java.net.URI
import java.util.UUID

@Serializable
enum class RouteAction(val label: String, val tag: String) { PROXY("代理", "proxy"), DIRECT("绕过", "direct"), BLOCK("阻止", "block") }

@Serializable
data class RoutingRule(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val enabled: Boolean = true,
    val action: RouteAction = RouteAction.DIRECT,
    val domains: List<String> = emptyList(),
    val ips: List<String> = emptyList(),
    val port: String = "",
    val source: List<String> = emptyList(),
    val sourcePort: String = "",
    val network: String = "",
    val protocols: List<String> = emptyList(),
) {
    fun validate() {
        require(id.isNotBlank() && id.length <= 200) { "无效规则 ID" }
        require(name.isNotBlank() && name.length <= 200) { "请输入规则名称（最多 200 字）" }
        require(domains.isNotEmpty() || ips.isNotEmpty() || port.isNotBlank() || source.isNotEmpty() || sourcePort.isNotBlank() || network.isNotBlank() || protocols.isNotEmpty()) { "请至少设置一个匹配条件" }
        require((domains + ips + source).size <= 10000) { "单条规则条件过多" }
        require((domains + ips + source).all { it.isNotBlank() && it.length <= 2048 }) { "匹配条件不能为空或过长" }
        require(network in listOf("", "tcp", "udp", "tcp,udp")) { "网络类型须为 tcp、udp 或 tcp,udp" }
        require(protocols.all { it in listOf("http", "tls", "bittorrent", "quic") }) { "不支持的嗅探协议" }
        fun ports(s: String) {
            if (s.isBlank()) return
            require(s.split(',').all { part ->
                val pair = part.trim().split('-').map { it.toIntOrNull() }
                pair.size in 1..2 && pair.all { it != null && it in 1..65535 } && (pair.size == 1 || pair[0]!! <= pair[1]!!)
            }) { "端口格式例如 80,443,1000-2000" }
        }
        ports(port); ports(sourcePort)
        fun cidr(value: String) {
            if (value.startsWith("geoip:") || value.startsWith("ext:")) return
            val parts = value.split('/')
            val ip = parts.first()
            require(parts.size <= 2 && ((ip.contains(':') && ip.matches(Regex("[0-9a-fA-F:.]+")) && runCatching { java.net.InetAddress.getByName(ip) }.isSuccess) || (ip.split('.').size == 4 && ip.split('.').all { it.toIntOrNull() in 0..255 }))) { "无效 IP / CIDR：$value" }
            if (parts.size == 2) require(parts[1].toIntOrNull() in 0..(if (ip.contains(':')) 128 else 32)) { "无效 CIDR 前缀：$value" }
        }
        ips.forEach(::cidr); source.forEach(::cidr)
        domains.filter { it.startsWith("regexp:") }.forEach { Regex(it.removePrefix("regexp:")) }
    }
    fun toXray(): JsonObject = buildJsonObject {
        put("type", "field"); put("outboundTag", action.tag)
        fun strings(key: String, values: List<String>) { if (values.isNotEmpty()) put(key, JsonArray(values.map(::JsonPrimitive))) }
        strings("domain", domains); strings("ip", ips); strings("source", source); strings("protocol", protocols)
        if (port.isNotBlank()) put("port", port)
        if (sourcePort.isNotBlank()) put("sourcePort", sourcePort)
        if (network.isNotBlank()) put("network", network)
    }
    val summary: String get() = (domains + ips + listOfNotNull(port.takeIf { it.isNotBlank() }?.let { "端口 $it" }, network.takeIf { it.isNotBlank() }) + source.map { "来源 $it" } + protocols).joinToString(" · ")
}

object RoutingRules {
    private val json = Json { prettyPrint = true }
    fun defaults(privateEnabled: Boolean = true) = listOf(
        RoutingRule("builtin:google-cn", "中国 Play 商店规则", false, RouteAction.PROXY, domains = listOf("domain:googleapis.cn")),
        RoutingRule("builtin:cn-domain", "中国域名规则", domains = listOf("geosite:cn")),
        RoutingRule("builtin:cn-ip", "中国 IP 规则", ips = listOf("geoip:cn")),
        RoutingRule("builtin:private", "绕过私人地址", privateEnabled, ips = listOf("geoip:private")),
    )
    fun encode(rules: List<RoutingRule>): String {
        require(rules.size <= 2000) { "最多保存 2000 条规则" }
        require(rules.map { it.id }.distinct().size == rules.size) { "规则 ID 不能重复" }
        rules.forEach { it.validate() }
        return json.encodeToString(ListSerializer(RoutingRule.serializer()), rules).also { require(it.length <= 4 * 1024 * 1024) { "规则文件不能超过 4 MiB" } }
    }
    fun decode(text: String, preserveIds: Boolean = false): List<RoutingRule> {
        require(text.length <= 4 * 1024 * 1024) { "规则文件不能超过 4 MiB" }
        val root = json.parseToJsonElement(text)
        val array = root as? JsonArray ?: (root as? JsonObject)?.let { (it["rules"] ?: it["routing"]?.jsonObject?.get("rules")) as? JsonArray } ?: error("需要规则数组或含 rules 的 JSON 对象")
        require(array.size <= 2000) { "最多导入 2000 条规则" }
        return array.mapIndexed { index, value ->
            val obj = value.jsonObject
            if ("action" in obj || "domains" in obj || ("id" in obj && "outboundTag" !in obj)) json.decodeFromJsonElement(RoutingRule.serializer(), obj).let { if (preserveIds) it else it.copy(id = UUID.randomUUID().toString()) }.also { it.validate() }
            else {
                val supported = setOf("type", "name", "remarks", "locked", "enabled", "outboundTag", "domain", "ip", "port", "source", "sourcePort", "network", "protocol")
                require(obj.keys.all { it in supported }) { "第 ${index + 1} 条含不支持的条件：${obj.keys - supported}" }
                require(obj["type"]?.jsonPrimitive?.content.orEmpty() in listOf("", "field")) { "仅支持 field 规则" }
                fun s(key: String) = obj[key]?.jsonPrimitive?.content.orEmpty()
                fun list(key: String): List<String> = when (val v = obj[key]) { null -> emptyList(); is JsonArray -> v.map { it.jsonPrimitive.content }; else -> listOf(v.jsonPrimitive.content) }
                val action = RouteAction.entries.firstOrNull { it.tag == s("outboundTag") } ?: error("路由目标须为 proxy / direct / block")
                RoutingRule(name = s("name").ifBlank { s("remarks") }.ifBlank { "导入规则 ${index + 1}" }, enabled = obj["enabled"]?.jsonPrimitive?.boolean ?: true, action = action, domains = list("domain"), ips = list("ip"), port = s("port"), source = list("source"), sourcePort = s("sourcePort"), network = s("network"), protocols = list("protocol")).also { it.validate() }
            }
        }
    }
    fun validateTestUrl(value: String): String {
        val uri = runCatching { URI(value.trim()) }.getOrNull()
        require(uri != null && uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null && (uri.port == -1 || uri.port in 1..65535)) { "请输入有效的 HTTP / HTTPS 测速链接" }
        return value.trim()
    }
}
