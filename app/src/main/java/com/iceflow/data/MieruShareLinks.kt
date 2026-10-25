package com.iceflow.data

import com.iceflow.core.XrayBridge
import com.iceflow.data.model.Protocol
import com.iceflow.data.model.ServerProfile
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/** Official mieru protobuf URLs and mierus (mieru simple) profile URLs. */
internal object MieruShareLinks {
    val schemes = setOf("mieru", "mierus")
    private fun escape(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    private fun authDecode(value: String) = URLDecoder.decode(value.replace("+", "%2B"), "UTF-8")

    fun parseMany(link: String): List<ServerProfile> {
        if (link.startsWith("mieru://", ignoreCase = true)) {
            // Full links contain protobuf, not JSON or a user@host URI. Use the upstream codec.
            val normalized = XrayBridge.normalizeMieruLink("mieru://" + link.substringAfter("://"))
            return normalized.lineSequence().filter(String::isNotBlank).flatMap { parseSimple(it).asSequence() }.toList()
        }
        return parseSimple(link)
    }

    private fun parseSimple(link: String): List<ServerProfile> {
        val uri = URI(link)
        require(uri.scheme.equals("mierus", ignoreCase = true))
        val rawAuth = requireNotNull(uri.rawUserInfo) { "mieru 需要用户名和密码" }
        require(':' in rawAuth) { "mieru 需要用户名和密码" }
        val username = authDecode(rawAuth.substringBefore(':'))
        val password = authDecode(rawAuth.substringAfter(':'))
        require(username.isNotEmpty() && password.isNotEmpty()) { "mieru 需要用户名和密码" }
        val address = requireNotNull(uri.host).removeSurrounding("[", "]")
        require(address.isNotBlank() && (uri.rawPath.isNullOrEmpty() || uri.rawPath == "/"))
        val query = uri.rawQuery.orEmpty().split('&').filter(String::isNotEmpty).map {
            // Official query is application/x-www-form-urlencoded (profile names use '+').
            URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('=', ""), "UTF-8")
        }.groupBy({ it.first }, { it.second })
        fun option(key: String) = query[key]?.firstOrNull().orEmpty()
        // Subscription generators often use profile=default and put the actual node name in #remark.
        val name = uri.rawFragment?.let(::authDecode).orEmpty().takeIf { it.isNotBlank() }
            ?: option("profile")
        require(name.isNotBlank()) { "mieru 链接缺少 profile 名称" }
        val ports = query["port"] ?: if (uri.port != -1) listOf(uri.port.toString()) else emptyList()
        val transports = query["protocol"].orEmpty()
        require(ports.isNotEmpty() && ports.size == transports.size) { "mieru 的 port 和 protocol 必须一一对应" }
        require(ports.size <= 1024) { "mieru 端口配置过多" }
        val common = query.filterKeys { it !in setOf("profile", "port", "protocol") }.mapValues { it.value.first() }
        return ports.mapIndexed { index, binding ->
            val range = binding.split('-')
            require(range.size in 1..2) { "mieru 端口格式无效" }
            val begin = range.first().toInt(); val end = range.last().toInt()
            require(begin in 1..65535 && end in begin..65535) { "mieru 端口范围无效" }
            val transport = transports[index].uppercase()
            require(transport in setOf("TCP", "UDP")) { "mieru 传输方式必须是 TCP 或 UDP" }
            val options = common +
                query["profile"]?.firstOrNull()?.takeUnless { it == "default" }?.let { mapOf("profile" to it) }.orEmpty() +
                ("transport" to transport) +
                if (range.size == 2) mapOf("port-range" to "$begin-$end") else emptyMap()
            ServerProfile(name = if (ports.size > 1) "$name · $transport $binding" else name,
                protocol = Protocol.MIERU, address = address, port = begin,
                username = username, password = password, protocolOptions = options).also {
                // Reject malformed profile options at import time instead of saving unusable nodes.
                ExtendedConfigBuilder.build(it)
            }
        }
    }

    fun encode(p: ServerProfile): String {
        val host = if (':' in p.address) "[${p.address}]" else p.address
        val query = p.protocolOptions.filterKeys { it !in setOf("transport", "port-range", "profile", "port", "protocol") }.toMutableMap()
        query["profile"] = p.protocolOptions["profile"] ?: "default"
        query["port"] = p.protocolOptions["port-range"] ?: p.port.toString()
        query["protocol"] = p.protocolOptions["transport"] ?: "TCP"
        return "mierus://${escape(p.username)}:${escape(p.password)}@$host?" +
            query.entries.joinToString("&") { "${escape(it.key)}=${escape(it.value)}" } +
            "#${escape(p.name)}"
    }
}
