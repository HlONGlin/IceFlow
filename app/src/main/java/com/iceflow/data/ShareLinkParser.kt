package com.iceflow.data

import com.iceflow.data.model.Protocol
import com.iceflow.data.model.ServerProfile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonPrimitive
import java.net.URLDecoder
import java.util.Base64

/**
 * 解析分享链接：VMess、VLESS、Trojan、SS，以及 ExtendedShareLinks 中的扩展协议。
 * 解析失败返回 null，不抛异常。
 */
object ShareLinkParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(link: String): ServerProfile? = runCatching {
        val s = link.trim()
        when {
            s.startsWith("vmess://", ignoreCase = true) -> parseVmess(s.substring(8))
            s.startsWith("vless://", ignoreCase = true) -> parseStandard(s.substring(8), Protocol.VLESS)
            s.startsWith("trojan://", ignoreCase = true) -> parseStandard(s.substring(9), Protocol.TROJAN)
            s.startsWith("ss://", ignoreCase = true) -> parseShadowsocks(s.substring(5))
            s.substringBefore("://").lowercase() in MieruShareLinks.schemes -> MieruShareLinks.parseMany(s).firstOrNull()
            s.substringBefore("://").lowercase() in ExtendedShareLinks.schemes -> ExtendedShareLinks.parse(s)
            else -> null
        }
    }.getOrNull()

    /** 分享链接、Clash YAML/JSON 和 Base64 订阅使用同一导入路径。 */
    fun parseMany(text: String): List<ServerProfile> {
        val trimmed = text.trim().removePrefix("\uFEFF")
        if (Regex("(?m)^---\\s*$").containsMatchIn(trimmed)) {
            return trimmed.split(Regex("(?m)^---\\s*$")).filter(String::isNotBlank).flatMap(::parseMany)
        }
        if (ClashYamlParser.looksLike(trimmed)) return ClashYamlParser.parse(trimmed)
        val content = if (trimmed.contains("://")) trimmed else decodeBase64(trimmed) ?: trimmed
        if (content != trimmed && ClashYamlParser.looksLike(content.trim())) return parseMany(content)
        return content.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .flatMap { line ->
                if (line.substringBefore("://").lowercase() in MieruShareLinks.schemes) {
                    runCatching { MieruShareLinks.parseMany(line) }.getOrDefault(emptyList()).asSequence()
                } else listOfNotNull(parse(line)).asSequence()
            }
            .toList()
    }

    /** Prefers a protocol URI; retains Clash JSON when AnyTLS options cannot survive conversion. */
    fun encodeV2rayN(profile: ServerProfile): String {
        val export = if (profile.protocol == Protocol.ANYTLS && profile.clashConfig != null) {
            ExtendedShareLinks.anyTlsUriProfile(profile) ?: return ClashYamlParser.encode(profile)
        } else profile
        return encode(export, includeClashConfig = false)
    }

    fun encode(profile: ServerProfile): String = encode(profile, includeClashConfig = true)

    private fun encode(profile: ServerProfile, includeClashConfig: Boolean): String {
        if (includeClashConfig && profile.clashConfig != null) return ClashYamlParser.encode(profile)
        if (profile.protocol == Protocol.MIERU) return MieruShareLinks.encode(profile)
        if (profile.protocol == Protocol.SUDOKU) return ClashYamlParser.encode(profile)
        if (profile.protocol in ExtendedShareLinks.protocols) return ExtendedShareLinks.encode(profile)
        val fragment = java.net.URLEncoder.encode(profile.name, "UTF-8").replace("+", "%20")
        val query = linkedMapOf<String, String>()
        fun add(key: String, value: String) { if (value.isNotEmpty()) query[key] = value }
        when (profile.protocol) {
            Protocol.VMESS -> {
                val payload = kotlinx.serialization.json.buildJsonObject {
                    put("v", JsonPrimitive("2")); put("ps", JsonPrimitive(profile.name)); put("add", JsonPrimitive(profile.address)); put("port", JsonPrimitive(profile.port.toString())); put("id", JsonPrimitive(profile.uuid))
                    put("aid", JsonPrimitive(profile.alterId.toString())); put("scy", JsonPrimitive(profile.security)); put("net", JsonPrimitive(profile.network)); put("type", JsonPrimitive(profile.headerType)); put("host", JsonPrimitive(profile.host)); put("path", JsonPrimitive(if (profile.network == "grpc") profile.serviceName else profile.path)); put("tls", JsonPrimitive(if (profile.tls == "tls") "tls" else "")); put("sni", JsonPrimitive(profile.sni)); put("alpn", JsonPrimitive(profile.alpn)); put("fp", JsonPrimitive(profile.fingerprint))
                }.toString().toByteArray(Charsets.UTF_8)
                return "vmess://${Base64.getEncoder().encodeToString(payload)}"
            }
            Protocol.VLESS -> {
                add("encryption", profile.encryption); add("flow", profile.flow)
            }
            Protocol.TROJAN -> Unit
            Protocol.SHADOWSOCKS -> {
                val user = Base64.getEncoder().withoutPadding().encodeToString("${profile.security}:${profile.password}".toByteArray())
                return "ss://${user}@${formatHost(profile.address)}:${profile.port}#$fragment"
            }
            else -> error("Unsupported share protocol")
        }
        add("type", profile.network); add("headerType", profile.headerType); add("host", profile.host); add("path", profile.path); add("serviceName", profile.serviceName); add("security", profile.tls); add("sni", profile.sni); add("alpn", profile.alpn); add("fp", profile.fingerprint); add("pbk", profile.publicKey); add("sid", profile.shortId); add("spx", profile.spiderX); if (profile.allowInsecure) add("allowInsecure", "1")
        val auth = if (profile.protocol == Protocol.VLESS) profile.uuid else profile.password
        val scheme = if (profile.protocol == Protocol.VLESS) "vless" else "trojan"
        val queryText = query.entries.joinToString("&") { "${it.key}=${java.net.URLEncoder.encode(it.value, "UTF-8").replace("+", "%20")}" }
        return "$scheme://${java.net.URLEncoder.encode(auth, "UTF-8")}@${formatHost(profile.address)}:${profile.port}?$queryText#$fragment"
    }

    private fun formatHost(host: String): String = if (host.contains(':') && !host.startsWith('[')) "[$host]" else host

    private fun parseVmess(body: String): ServerProfile? {
        val decoded = decodeBase64(body.substringBefore('#')) ?: return null
        val obj = json.parseToJsonElement(decoded).jsonObject
        fun str(key: String) = obj[key]?.jsonPrimitive?.contentOrNull.orEmpty()

        val address = str("add")
        val port = str("port").toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        val uuid = str("id")
        if (address.isEmpty() || uuid.isEmpty()) return null
        val network = str("net").ifEmpty { "tcp" }
        return ServerProfile(
            name = str("ps").ifEmpty { "$address:$port" },
            protocol = Protocol.VMESS,
            address = address,
            port = port,
            uuid = uuid,
            alterId = str("aid").toIntOrNull() ?: 0,
            security = str("scy").ifEmpty { "auto" },
            network = network,
            headerType = str("type").ifEmpty { "none" },
            host = str("host"),
            path = if (network == "grpc") "" else str("path"),
            serviceName = if (network == "grpc") str("path") else "",
            tls = if (str("tls") == "tls") "tls" else "none",
            sni = str("sni"),
            alpn = str("alpn"),
            fingerprint = str("fp"),
        )
    }

    /** vless:// 与 trojan:// 共享格式：user@host:port?query#name */
    private fun parseStandard(body: String, protocol: Protocol): ServerProfile? {
        val fragment = body.substringAfter('#', "")
        val noFragment = body.substringBefore('#')
        val query = parseQuery(noFragment.substringAfter('?', ""))
        val main = noFragment.substringBefore('?')

        val at = main.lastIndexOf('@')
        if (at <= 0) return null
        val user = urlDecode(main.substring(0, at))
        val (host, port) = splitHostPort(main.substring(at + 1)) ?: return null

        fun q(key: String) = query[key].orEmpty()
        val defaultSecurity = if (protocol == Protocol.TROJAN) "tls" else "none"
        return ServerProfile(
            name = urlDecode(fragment).ifEmpty { "$host:$port" },
            protocol = protocol,
            address = host,
            port = port,
            uuid = if (protocol == Protocol.VLESS) user else "",
            password = if (protocol == Protocol.TROJAN) user else "",
            flow = q("flow"),
            encryption = q("encryption").ifEmpty { "none" },
            network = q("type").ifEmpty { "tcp" },
            headerType = q("headerType").ifEmpty { "none" },
            host = q("host"),
            path = q("path"),
            serviceName = q("serviceName"),
            tls = q("security").ifEmpty { defaultSecurity },
            sni = q("sni").ifEmpty { q("peer") },
            alpn = q("alpn"),
            fingerprint = q("fp"),
            publicKey = q("pbk"),
            shortId = q("sid"),
            spiderX = q("spx"),
            allowInsecure = q("allowInsecure") == "1" || q("insecure") == "1",
        )
    }

    private fun parseShadowsocks(body: String): ServerProfile? {
        val name = urlDecode(body.substringAfter('#', ""))
        // 插件参数（?plugin=）暂不支持，直接忽略
        var main = body.substringBefore('#').substringBefore('?').trimEnd('/')
        if (!main.contains('@')) {
            // 旧格式：整段 Base64(method:password@host:port)
            main = decodeBase64(main) ?: return null
        }
        val at = main.lastIndexOf('@')
        if (at <= 0) return null
        val userRaw = main.substring(0, at)
        // SIP002 用 Base64 编码 userinfo；SS2022 允许百分号编码的明文
        val user = decodeBase64(userRaw)?.takeIf { it.contains(':') } ?: urlDecode(userRaw)
        val method = user.substringBefore(':')
        val password = user.substringAfter(':', "")
        if (method.isEmpty() || password.isEmpty()) return null
        val (host, port) = splitHostPort(main.substring(at + 1)) ?: return null
        return ServerProfile(
            name = name.ifEmpty { "$host:$port" },
            protocol = Protocol.SHADOWSOCKS,
            address = host,
            port = port,
            password = password,
            security = method,
        )
    }

    private fun splitHostPort(value: String): Pair<String, Int>? {
        val s = value.trimEnd('/')
        val host: String
        val portText: String
        if (s.startsWith("[")) {
            val end = s.indexOf(']')
            if (end < 0) return null
            host = s.substring(1, end)
            portText = s.substring(end + 1).removePrefix(":")
        } else {
            val idx = s.lastIndexOf(':')
            if (idx <= 0) return null
            host = s.substring(0, idx)
            portText = s.substring(idx + 1)
        }
        val port = portText.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        if (host.isEmpty()) return null
        return host to port
    }

    private fun parseQuery(query: String): Map<String, String> =
        query.split('&')
            .filter { it.isNotEmpty() }
            .associate { urlDecode(it.substringBefore('=')) to urlDecode(it.substringAfter('=', "")) }

    private fun urlDecode(value: String): String = try {
        // 分享链接中的 '+' 通常是字面量，不当作空格
        URLDecoder.decode(value.replace("+", "%2B"), "UTF-8")
    } catch (_: IllegalArgumentException) {
        value
    }

    internal fun decodeBase64(input: String): String? {
        val clean = input.filterNot { it.isWhitespace() }.trimEnd('=')
        if (clean.isEmpty()) return null
        val padded = clean + "=".repeat((4 - clean.length % 4) % 4)
        for (decoder in listOf(Base64.getDecoder(), Base64.getUrlDecoder())) {
            try {
                return String(decoder.decode(padded), Charsets.UTF_8)
            } catch (_: IllegalArgumentException) {
                // 尝试下一种字母表
            }
        }
        return null
    }
}
