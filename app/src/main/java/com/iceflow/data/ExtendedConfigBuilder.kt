package com.iceflow.data

import com.iceflow.data.model.Protocol
import com.iceflow.data.model.ServerProfile
import kotlinx.serialization.json.*

/** Pinned Mihomo adapter options; the surrounding Xray instance owns local routing. */
internal object ExtendedConfigBuilder {
    fun build(p: ServerProfile): JsonObject = buildJsonObject {
        require(p.address.isNotBlank() && p.port in 1..65535) { "服务器地址或端口无效" }
        val o = p.protocolOptions
        fun option(key: String, vararg aliases: String): String = (listOf(key) + aliases).firstNotNullOfOrNull { o[it]?.takeIf(String::isNotEmpty) }.orEmpty()
        fun text(key: String, value: String) { if (value.isNotEmpty()) put(key, value) }
        fun textOption(key: String, vararg aliases: String) = text(key, option(key, *aliases))
        fun number(key: String, vararg aliases: String) { val value = option(key, *aliases); if (value.isNotEmpty()) put(key, value.toIntOrNull() ?: error("$key 必须是整数")) }
        fun boolean(key: String, vararg aliases: String) { val v = option(key, *aliases); if (v.isNotEmpty()) { require(v.lowercase() in listOf("1", "0", "true", "false")) { "$key 必须是 true/false" }; put(key, v.lowercase() in listOf("1", "true")) } }
        fun list(key: String, value: String) { if (value.isNotBlank()) putJsonArray(key) { value.split(',').map(String::trim).filter(String::isNotEmpty).forEach { add(it) } } }
        put("name", "iceflow-proxy"); put("server", p.address)
        if (p.protocol != Protocol.MIERU || option("port-range").isEmpty()) put("port", p.port)
        put("udp", p.protocol != Protocol.HTTP)
        text("sni", p.sni); put("skip-cert-verify", p.allowInsecure); list("alpn", p.alpn)
        text("fingerprint", option("pinSHA256", "fingerprint"))
        when (p.protocol) {
            Protocol.MIERU -> {
                require(p.username.isNotEmpty() && p.password.isNotEmpty()) { "mieru 需要用户名和密码" }
                put("type", "mieru"); put("username", p.username); put("password", p.password)
                val transport = option("transport").ifEmpty { "TCP" }.uppercase()
                require(transport in setOf("TCP", "UDP")) { "mieru 传输方式必须是 TCP 或 UDP" }
                put("transport", transport)
                val range = option("port-range")
                if (range.isNotEmpty()) {
                    val ports = range.split('-').map(String::toInt)
                    require(ports.size == 2 && ports[0] in 1..65535 && ports[1] in ports[0]..65535) { "mieru 端口范围无效" }
                    put("port-range", range)
                }
                val multiplexing = option("multiplexing")
                require(multiplexing.isEmpty() || multiplexing in setOf("MULTIPLEXING_DEFAULT", "MULTIPLEXING_OFF", "MULTIPLEXING_LOW", "MULTIPLEXING_MIDDLE", "MULTIPLEXING_HIGH")) { "mieru 复用等级无效" }
                text("multiplexing", multiplexing)
                val handshake = option("handshake-mode")
                require(handshake.isEmpty() || handshake in setOf("HANDSHAKE_DEFAULT", "HANDSHAKE_STANDARD", "HANDSHAKE_NO_WAIT")) { "mieru 握手模式无效" }
                text("handshake-mode", handshake); textOption("traffic-pattern")
                // The native shim applies MTU to the client profile (not exposed by Mihomo options).
                val mtu = option("mtu")
                if (mtu.isNotEmpty()) {
                    val number = mtu.toInt(); require(number == 0 || number in 1280..1500) { "mieru MTU 必须为 0（自动）或 1280–1500" }
                    put("mtu", number)
                }
            }
            Protocol.HYSTERIA -> {
                put("type", "hysteria"); text("auth-str", p.password)
                val up = option("up", "upmbps"); val down = option("down", "downmbps")
                require(up.isNotEmpty() && down.isNotEmpty()) { "Hysteria 需要设置上传和下载带宽（Mbps）" }
                put("up", up); put("down", down)
                textOption("ports", "mport"); textOption("protocol"); number("hop-interval", "hopInterval")
                val obfs = option("obfs")
                if (obfs == "xplus") text("obfs", option("obfsParam", "obfs-password")) else text("obfs", obfs)
            }
            Protocol.HYSTERIA2 -> {
                require(p.password.isNotEmpty()) { "请输入 Hysteria2 密码" }
                put("type", "hysteria2"); put("password", p.password)
                textOption("up", "upmbps"); textOption("down", "downmbps")
                textOption("ports", "mport"); textOption("hop-interval", "hopInterval")
                textOption("obfs"); textOption("obfs-password", "obfsParam")
                option("ech").takeIf { it.isNotEmpty() }?.let { putJsonObject("ech-opts") { put("enable", true); put("config", it) } }
            }
            Protocol.TUIC -> {
                put("type", "tuic")
                if (p.uuid.isNotEmpty()) { put("uuid", p.uuid); put("password", p.password) }
                else { val token = p.password.ifEmpty { option("token") }; require(token.isNotEmpty()) { "请输入 TUIC UUID/密码或 v4 Token" }; put("token", token) }
                textOption("congestion-controller", "congestion_control", "congestion-controller")
                textOption("udp-relay-mode", "udp_relay_mode")
                boolean("disable-sni", "disable_sni"); boolean("reduce-rtt", "reduce_rtt"); boolean("fast-open", "fast_open")
                number("heartbeat-interval", "heartbeat"); number("request-timeout"); number("max-udp-relay-packet-size")
            }
            Protocol.WIREGUARD -> {
                put("type", "wireguard")
                val privateKey = option("private-key"); val publicKey = p.publicKey.ifEmpty { option("publickey", "public-key") }
                require(privateKey.isNotEmpty() && publicKey.isNotEmpty()) { "WireGuard 需要私钥和对端公钥" }
                put("private-key", privateKey); put("public-key", publicKey)
                val addresses = option("address", "local-address", "ip").split(',').map(String::trim).filter(String::isNotEmpty)
                val ipv4 = addresses.firstOrNull { ':' !in it }.orEmpty()
                val ipv6 = addresses.firstOrNull { ':' in it }.orEmpty().ifEmpty { option("ipv6") }
                require(ipv4.isNotEmpty() || ipv6.isNotEmpty()) { "请输入 WireGuard 本地隧道地址" }
                text("ip", ipv4.substringBefore('/')); text("ipv6", ipv6.substringBefore('/'))
                textOption("pre-shared-key", "presharedkey", "preshared-key")
                number("mtu"); number("persistent-keepalive", "keepalive")
                val reserved = option("reserved").removeSurrounding("[", "]")
                if (reserved.isNotBlank()) {
                    val bytes = reserved.split(',').map { it.trim().toInt() }; require(bytes.size == 3 && bytes.all { it in 0..255 }) { "Reserved 应是 3 个 0–255 的整数" }
                    putJsonArray("reserved") { bytes.forEach { add(it) } }
                }
                list("allowed-ips", option("allowed-ips", "allowedips")); list("dns", option("dns"))
                if (option("dns").isNotEmpty()) put("remote-dns-resolve", true)
            }
            Protocol.SHADOWSOCKSR -> {
                require(p.password.isNotEmpty()) { "请输入 SSR 密码" }
                put("type", "ssr"); put("cipher", p.security); put("password", p.password)
                put("protocol", option("protocol").ifEmpty { "origin" }); put("obfs", option("obfs").ifEmpty { "plain" })
                textOption("protocol-param", "protocol-param", "protoparam"); textOption("obfs-param", "obfs-param", "obfsparam")
            }
            Protocol.ANYTLS -> {
                require(p.password.isNotEmpty()) { "AnyTLS requires a password" }
                put("type", "anytls"); put("password", p.password)
                text("client-fingerprint", p.fingerprint.ifEmpty { option("fp", "client-fingerprint", "clientFingerprint") })
                textOption("client-metadata", "clientMetadata")
                number("idle-session-check-interval", "idleSessionCheckInterval")
                number("idle-session-timeout", "idleSessionTimeout")
                number("min-idle-session", "minIdleSession")
                boolean("disable-reuse", "disableReuse")
                for (key in listOf("ech-opts", "shadow-tls-opts", "restls-opts", "jls-opts")) {
                    option(key).takeIf(String::isNotEmpty)?.let { put(key, Json.parseToJsonElement(it)) }
                }
            }
            Protocol.SUDOKU -> {
                require(p.password.isNotEmpty()) { "Sudoku requires a key" }
                put("type", "sudoku"); put("key", p.password)
                textOption("aead-method", "aeadMethod")
                textOption("table-type", "tableType")
                textOption("multiplex")
                textOption("http-mask-mode", "httpMaskMode")
                textOption("http-mask-host", "httpMaskHost")
                textOption("path-root", "pathRoot")
                textOption("custom-table", "customTable")
                number("padding-min", "paddingMin")
                number("padding-max", "paddingMax")
                boolean("enable-pure-downlink", "enablePureDownlink")
                boolean("http-mask", "httpMask")
                boolean("http-mask-tls", "httpMaskTls")
                textOption("http-mask-multiplex", "httpMaskMultiplex")
                list("custom-tables", option("custom-tables", "customTables"))
                option("httpmask").takeIf(String::isNotEmpty)?.let { put("httpmask", Json.parseToJsonElement(it)) }
            }
            Protocol.HTTP, Protocol.SOCKS -> {
                put("type", if (p.protocol == Protocol.HTTP) "http" else "socks5")
                put("tls", p.tls == "tls"); text("username", p.username); text("password", p.password)
                if (p.protocol == Protocol.SOCKS) text("name-cert-verify", p.sni)
            }
            else -> error("Unsupported extended protocol")
        }
    }
}
