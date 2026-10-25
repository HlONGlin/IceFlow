package com.iceflow.data

import com.iceflow.data.model.*
import kotlinx.serialization.json.*
import java.util.UUID

object NodeEditing {
    val json = Json { encodeDefaults = true; prettyPrint = true }
    fun blank(protocol: Protocol = Protocol.VLESS) = ServerProfile(name = "", protocol = protocol, address = "", port = 443, tls = when (protocol) { Protocol.TROJAN, Protocol.HYSTERIA, Protocol.HYSTERIA2, Protocol.TUIC, Protocol.ANYTLS -> "tls"; else -> "none" }, security = when (protocol) { Protocol.SHADOWSOCKS -> "aes-128-gcm"; Protocol.SHADOWSOCKSR -> "aes-128-cfb"; else -> "auto" })
    fun validate(p: ServerProfile): ServerProfile {
        require(p.name.isNotBlank()) { "请输入节点名称" }
        require(p.address.isNotBlank() && p.address.none { it.isWhitespace() || it == '/' || it == '@' }) { "请输入有效的服务器地址（不含协议和端口）" }
        require(p.port in 1..65535) { "端口必须在 1–65535 之间" }
        if (p.clashConfig == null) {
            if (p.protocol in listOf(Protocol.VMESS, Protocol.VLESS)) require(runCatching { UUID.fromString(p.uuid) }.isSuccess) { "请输入有效 UUID" }
            if (p.protocol in listOf(Protocol.TROJAN, Protocol.SHADOWSOCKS, Protocol.SHADOWSOCKSR, Protocol.HYSTERIA2, Protocol.TUIC, Protocol.MIERU, Protocol.ANYTLS, Protocol.SUDOKU)) require(p.password.isNotBlank()) { "请输入密码 / Token / Key" }
            require(p.alterId >= 0) { "Alter ID 不能为负数" }
            if (p.tls == "reality") require(p.publicKey.isNotBlank() && p.sni.isNotBlank()) { "Reality 需要 SNI 和公钥" }
            if (p.protocol in ExtendedShareLinks.protocols) ExtendedConfigBuilder.build(p)
            else XrayConfigBuilder.build(p, AppSettings(routingRules = emptyList()), includeInbound = false)
        } else ClashYamlParser.config(p)
        return p.copy(name = p.name.trim(), address = p.address.trim())
    }
    fun options(text: String): Map<String, String> {
        val obj = json.parseToJsonElement(text).jsonObject
        return obj.mapValues { (_, v) -> require(v is JsonPrimitive && v.isString) { "扩展参数必须是字符串键值；嵌套 Clash 参数请使用原始 JSON 编辑" }; v.content }
    }
    fun clash(text: String, original: ServerProfile): ServerProfile {
        val config = json.parseToJsonElement(text).jsonObject
        return ClashYamlParser.parse(buildJsonObject { put("proxies", JsonArray(listOf(config))) }.toString()).single().copy(id = original.id, groupId = original.groupId, subscriptionId = original.subscriptionId, latencyMs = null)
    }
    /** Common form changes only replace changed keys; nested plugin/smux/transport data survives. */
    fun syncClash(original: ServerProfile, changed: ServerProfile): ServerProfile {
        val source = original.clashConfig ?: return changed
        require(original.protocol == changed.protocol) { "Clash 节点请在原始 JSON 中修改协议" }
        val map = source.toMutableMap()
        fun text(key: String, a: String, b: String) { if (a != b) { if (b.isEmpty()) map.remove(key) else map[key] = JsonPrimitive(b) } }
        map["name"] = JsonPrimitive(changed.name); map["server"] = JsonPrimitive(changed.address)
        if (original.port != changed.port || "port-range" !in map) { map.remove("port-range"); map["port"] = JsonPrimitive(changed.port) }
        val passwordKey = if (changed.protocol == Protocol.SUDOKU) "key" else "password"
        text("uuid", original.uuid, changed.uuid); text(passwordKey, original.password, changed.password); text("username", original.username, changed.username)
        text("cipher", original.security, changed.security); text("flow", original.flow, changed.flow)
        val sniKey = if ("servername" in map) "servername" else "sni"
        text(sniKey, original.sni, changed.sni); text("client-fingerprint", original.fingerprint, changed.fingerprint)
        if (original.alterId != changed.alterId) map["alterId"] = JsonPrimitive(changed.alterId)
        if (original.allowInsecure != changed.allowInsecure) map["skip-cert-verify"] = JsonPrimitive(changed.allowInsecure)
        if (original.alpn != changed.alpn) map["alpn"] = JsonArray(changed.alpn.split(',').filter { it.isNotBlank() }.map { JsonPrimitive(it.trim()) })
        if (original.tls != changed.tls) {
            map["tls"] = JsonPrimitive(changed.tls != "none")
            if (changed.tls != "reality") map.remove("reality-opts")
        }
        if (original.publicKey != changed.publicKey || original.shortId != changed.shortId) {
            val reality = (map["reality-opts"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
            reality["public-key"] = JsonPrimitive(changed.publicKey); reality["short-id"] = JsonPrimitive(changed.shortId)
            map["reality-opts"] = JsonObject(reality)
        }
        if (original.network != changed.network) map["network"] = JsonPrimitive(changed.network)
        if (original.path != changed.path || original.host != changed.host || original.serviceName != changed.serviceName) {
            val key = when (changed.network) { "grpc" -> "grpc-opts"; "httpupgrade" -> "http-upgrade-opts"; "xhttp" -> "xhttp-opts"; else -> "ws-opts" }
            val transport = (map[key] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
            if (changed.network == "grpc") transport["grpc-service-name"] = JsonPrimitive(changed.serviceName) else {
                transport["path"] = JsonPrimitive(changed.path)
                val headers = (transport["headers"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
                if (changed.host.isNotEmpty()) headers["Host"] = JsonPrimitive(changed.host) else headers.remove("Host")
                transport["headers"] = JsonObject(headers)
            }; map[key] = JsonObject(transport)
        }
        return clash(JsonObject(map).toString(), changed)
    }
}
