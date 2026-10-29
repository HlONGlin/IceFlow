package com.iceflow.data

import com.iceflow.data.model.Protocol
import com.iceflow.data.model.ServerProfile
import kotlinx.serialization.json.*
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Base64

/** URI codecs for the extended native adapters. Unknown options survive editing/sharing. */
internal object ExtendedShareLinks {
    val protocols = setOf(Protocol.HYSTERIA, Protocol.HYSTERIA2, Protocol.TUIC, Protocol.WIREGUARD, Protocol.SHADOWSOCKSR, Protocol.HTTP, Protocol.SOCKS, Protocol.MIERU, Protocol.ANYTLS, Protocol.SUDOKU)
    val schemes = setOf("hysteria", "hy", "hysteria2", "hy2", "tuic", "wireguard", "wg", "ssr", "http", "https", "socks", "socks5", "socks5h", "anytls")
    private val anyTlsFingerprintKeys = listOf("fp", "client-fingerprint", "clientFingerprint")
    private fun decode(s: String) = URLDecoder.decode(s.replace("+", "%2B"), "UTF-8")
    private fun escape(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    private fun b64(s: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(s.toByteArray(Charsets.UTF_8))
    private fun unb64(s: String) = if (s.isEmpty()) "" else ShareLinkParser.decodeBase64(s) ?: error("Invalid Base64")
    private fun query(s: String) = s.split('&').filter { it.isNotEmpty() }.associate { decode(it.substringBefore('=')) to decode(it.substringAfter('=', "")) }
    private fun host(s: String) = if (':' in s && !s.startsWith('[')) "[$s]" else s
    private fun endpoint(s: String, defaultPort: Int): Pair<String, Int> {
        val text = s.trimEnd('/')
        val address: String
        val port: String
        if (text.startsWith('[')) {
            val end = text.indexOf(']'); require(end > 1)
            val suffix = text.substring(end + 1)
            require(suffix.isEmpty() || suffix.startsWith(':'))
            address = text.substring(1, end); port = suffix.removePrefix(":")
        } else {
            require(text.count { it == ':' } <= 1) { "IPv6 requires brackets" }
            address = text.substringBefore(':'); port = text.substringAfter(':', "")
        }
        require(address.isNotBlank() && address.none { it.isWhitespace() || it == '/' })
        val number = if (port.isEmpty()) defaultPort else port.substringBefore(',').substringBefore('-').toInt()
        require(number in 1..65535)
        return address to number
    }

    fun parse(link: String): ServerProfile {
        val scheme = link.substringBefore("://").lowercase()
        val body = link.substringAfter("://")
        if (scheme == "ssr") return parseSsr(body)
        val protocol = when (scheme) {
            "hysteria", "hy" -> Protocol.HYSTERIA
            "hysteria2", "hy2" -> Protocol.HYSTERIA2
            "tuic" -> Protocol.TUIC
            "wg", "wireguard" -> Protocol.WIREGUARD
            "http", "https" -> Protocol.HTTP
            "anytls" -> Protocol.ANYTLS
            else -> Protocol.SOCKS
        }
        val options = query(body.substringBefore('#').substringAfter('?', "")).toMutableMap()
        if (protocol == Protocol.ANYTLS) {
            // AnyTLS is intrinsically TLS. A number of share-link producers still
            // emit generic security=tls/tls=true fields; they describe the model
            // field below and must not survive as unknown mihomo options.
            val security = options["security"]?.trim()?.lowercase()
            require(security.isNullOrEmpty() || security == "tls") { "AnyTLS only supports TLS" }
            listOf("security", "tls").forEach(options::remove)
        }
        val main = body.substringBefore('#').substringBefore('?').trimEnd('/')
        val rawAuth = if ('@' in main) main.substringBeforeLast('@') else ""
        val authority = main.substringAfterLast('@')
        val (address, port) = endpoint(authority, when(protocol) { Protocol.HTTP -> if (scheme == "https") 443 else 80; Protocol.SOCKS -> 1080; Protocol.WIREGUARD -> 51820; else -> 443 })
        if (protocol in setOf(Protocol.HYSTERIA, Protocol.HYSTERIA2)) {
            val ports = authority.substringAfterLast(']').substringAfter(':', "")
            if (',' in ports || '-' in ports) options["mport"] = ports
        }
        val auth = decode(rawAuth)
        val userPass = if (protocol in setOf(Protocol.HTTP, Protocol.SOCKS) && ':' !in rawAuth && rawAuth.isNotEmpty()) {
            ShareLinkParser.decodeBase64(rawAuth)?.takeIf { ':' in it } ?: auth
        } else auth
        val uuid = if (protocol == Protocol.TUIC && ':' in rawAuth) decode(rawAuth.substringBefore(':')) else ""
        val username = if (protocol in setOf(Protocol.HTTP, Protocol.SOCKS)) userPass.substringBefore(':') else ""
        val password = when(protocol) {
            Protocol.HYSTERIA -> options["auth"].orEmpty().ifEmpty { auth }
            Protocol.TUIC -> if (':' in rawAuth) decode(rawAuth.substringAfter(':')) else auth
            Protocol.HTTP, Protocol.SOCKS -> userPass.substringAfter(':', "")
            Protocol.WIREGUARD -> ""
            else -> auth
        }
        if (protocol == Protocol.TUIC && uuid.isEmpty()) options["token"] = auth
        if (protocol == Protocol.WIREGUARD) options["private-key"] = auth.ifEmpty { options["private-key"].orEmpty() }
        val fingerprint = if (protocol == Protocol.ANYTLS) {
            anyTlsFingerprintKeys.firstNotNullOfOrNull { options[it]?.takeIf(String::isNotEmpty) }.orEmpty()
                .also { anyTlsFingerprintKeys.forEach { key -> options.remove(key) } }
        } else ""
        require(protocol !in setOf(Protocol.HYSTERIA2, Protocol.TUIC, Protocol.ANYTLS) || password.isNotEmpty())
        val allowInsecure = listOf("insecure", "allowInsecure", "skip-cert-verify").any { options[it] in listOf("1", "true") }
        if (protocol == Protocol.ANYTLS) listOf("allowInsecure", "skip-cert-verify").forEach(options::remove)
        return ServerProfile(
            name = decode(body.substringAfter('#', "")).ifEmpty { "$address:$port" }, protocol = protocol, address = address, port = port,
            username = username, password = password, uuid = uuid,
            tls = if (scheme == "https" || protocol in setOf(Protocol.HYSTERIA, Protocol.HYSTERIA2, Protocol.TUIC, Protocol.ANYTLS) || options["tls"] == "true") "tls" else "none",
            sni = options["sni"].orEmpty().ifEmpty { options["peer"].orEmpty() }, alpn = options["alpn"].orEmpty(),
            fingerprint = fingerprint,
            publicKey = options["publickey"].orEmpty().ifEmpty { options["public-key"].orEmpty() },
            allowInsecure = allowInsecure,
            protocolOptions = options,
        )
    }

    /** Only convert imported AnyTLS when the URI rebuild retains every original option. */
    fun anyTlsUriProfile(profile: ServerProfile): ServerProfile? {
        val node = profile.clashConfig ?: return profile
        // Mihomo defaults imported AnyTLS to UDP off; URI profiles enable it by default.
        if (node["udp"] != JsonPrimitive(true)) return null
        val common = setOf("name", "type", "server", "port", "password", "sni", "alpn", "client-fingerprint", "skip-cert-verify", "udp")
        val options = node.filterKeys { it !in common }.mapValues { (_, value) ->
            if (value is JsonPrimitive) value.content else value.toString()
        }
        val candidate = profile.copy(clashConfig = null, protocolOptions = options)
        val rebuilt = runCatching { ExtendedConfigBuilder.build(candidate) }.getOrNull() ?: return null
        return candidate.takeIf { node.all { (key, value) -> key == "name" || rebuilt[key] == value } }
    }

    private fun parseSsr(body: String): ServerProfile {
        val decoded = unb64(body.substringBefore('#'))
        val fields = decoded.substringBefore("/?").split(':')
        require(fields.size >= 6)
        val address = fields.dropLast(5).joinToString(":").removeSurrounding("[", "]")
        val tail = fields.takeLast(5)
        val port = tail[0].toInt(); require(port in 1..65535 && address.isNotBlank())
        val q = query(decoded.substringAfter("/?", ""))
        val options = mutableMapOf("protocol" to tail[1], "obfs" to tail[3])
        q["protoparam"]?.let { options["protocol-param"] = unb64(it) }
        q["obfsparam"]?.let { options["obfs-param"] = unb64(it) }
        return ServerProfile(name = q["remarks"]?.let(::unb64).orEmpty().ifEmpty { "$address:$port" },
            protocol = Protocol.SHADOWSOCKSR, address = address, port = port, security = tail[2],
            password = unb64(tail[4]), protocolOptions = options)
    }

    fun encode(p: ServerProfile): String {
        require(p.protocol != Protocol.SUDOKU) { "Sudoku nodes are shared as Clash YAML/JSON" }
        if (p.protocol == Protocol.SHADOWSOCKSR) {
            val q = linkedMapOf("remarks" to b64(p.name), "protoparam" to b64(p.protocolOptions["protocol-param"].orEmpty()), "obfsparam" to b64(p.protocolOptions["obfs-param"].orEmpty()))
            val raw = "${host(p.address)}:${p.port}:${p.protocolOptions["protocol"] ?: "origin"}:${p.security}:${p.protocolOptions["obfs"] ?: "plain"}:${b64(p.password)}/?" + q.entries.joinToString("&") { "${it.key}=${it.value}" }
            return "ssr://${b64(raw)}"
        }
        val q = p.protocolOptions.toMutableMap()
        fun add(k: String, v: String) { if (v.isNotEmpty()) q[k] = v else q.remove(k) }
        add("sni", p.sni); add("alpn", p.alpn)
        if (p.protocol == Protocol.ANYTLS) {
            val fingerprint = p.fingerprint.ifEmpty {
                anyTlsFingerprintKeys.firstNotNullOfOrNull { q[it]?.takeIf(String::isNotEmpty) }.orEmpty()
            }
            anyTlsFingerprintKeys.forEach { q.remove(it) }
            q.remove("security")
            q.remove("tls")
            add("fp", fingerprint)
        }
        q.remove("peer"); q.remove("allowInsecure"); q.remove("skip-cert-verify")
        if (p.allowInsecure) q["insecure"] = "1" else q.remove("insecure")
        val scheme = when(p.protocol) {
            Protocol.HYSTERIA -> "hysteria"
            Protocol.HYSTERIA2 -> "hysteria2"
            Protocol.TUIC -> "tuic"
            Protocol.WIREGUARD -> "wireguard"
            Protocol.HTTP -> if (p.tls == "tls") "https" else "http"
            Protocol.ANYTLS -> "anytls"
            else -> "socks5"
        }
        val auth = when(p.protocol) {
            Protocol.TUIC -> if (p.uuid.isNotEmpty()) "${escape(p.uuid)}:${escape(p.password)}" else escape(p.password)
            Protocol.WIREGUARD -> { add("publickey", p.publicKey); escape(q.remove("private-key").orEmpty()) }
            Protocol.HTTP, Protocol.SOCKS -> if (p.username.isNotEmpty()) "${escape(p.username)}:${escape(p.password)}" else ""
            else -> escape(p.password)
        }
        if (p.protocol == Protocol.SOCKS && p.tls == "tls") q["tls"] = "true" else q.remove("tls")
        if (p.protocol == Protocol.HYSTERIA) q["auth"] = p.password
        if (p.protocol == Protocol.TUIC) q.remove("token")
        val query = q.entries.joinToString("&") { "${escape(it.key)}=${escape(it.value)}" }
        return "$scheme://${if (auth.isNotEmpty()) "$auth@" else ""}${host(p.address)}:${p.port}/?$query#${escape(p.name)}"
    }
}
