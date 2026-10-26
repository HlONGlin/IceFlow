package com.iceflow.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.iceflow.data.NodeEditing
import com.iceflow.data.model.*
import com.iceflow.i18n.T
import kotlinx.serialization.json.*

@Composable internal fun NodeEditorPage(initial: ServerProfile, isNew: Boolean, close: () -> Unit, save: (ServerProfile) -> String?) {
    var encoded by rememberSaveable(initial.id) { mutableStateOf(NodeEditing.json.encodeToString(ServerProfile.serializer(), initial)) }
    val profile = remember(encoded) { NodeEditing.json.decodeFromString(ServerProfile.serializer(), encoded) }
    var baseline by rememberSaveable(initial.id) { mutableStateOf(encoded) }
    fun edit(transform: (ServerProfile) -> ServerProfile) { encoded = NodeEditing.json.encodeToString(ServerProfile.serializer(), transform(profile)) }
    var port by rememberSaveable(initial.id) { mutableStateOf(initial.port.toString()) }
    var alterId by rememberSaveable(initial.id) { mutableStateOf(initial.alterId.toString()) }
    var options by rememberSaveable(initial.id) { mutableStateOf(JsonObject(initial.protocolOptions.mapValues { JsonPrimitive(it.value) }).toString()) }
    var raw by rememberSaveable(initial.id) { mutableStateOf(initial.clashConfig?.let { NodeEditing.json.encodeToString(JsonObject.serializer(), it) }.orEmpty()) }
    var rawMode by rememberSaveable(initial.id) { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var discard by remember { mutableStateOf(false) }
    val invalidPort = T("editor.invalidPort")
    val invalidAlterId = T("editor.invalidAlterId")
    val invalidConfig = T("editor.invalidConfig")
    val dirty = profile != initial || port != initial.port.toString() || alterId != initial.alterId.toString() || options != JsonObject(initial.protocolOptions.mapValues { JsonPrimitive(it.value) }).toString() || raw != initial.clashConfig?.let { NodeEditing.json.encodeToString(JsonObject.serializer(), it) }.orEmpty()
    fun back() { if (dirty) discard = true else close() }
    BackHandler { back() }
    fun current(): ServerProfile {
        if (rawMode) return NodeEditing.validate(NodeEditing.clash(raw, initial))
        val next = profile.copy(port = port.toIntOrNull() ?: error(invalidPort), alterId = alterId.toIntOrNull() ?: error(invalidAlterId), protocolOptions = NodeEditing.options(options), latencyMs = null)
        return NodeEditing.validate(NodeEditing.syncClash(NodeEditing.json.decodeFromString(ServerProfile.serializer(), baseline), next))
    }
    val standard = profile.protocol in listOf(Protocol.VMESS, Protocol.VLESS, Protocol.TROJAN)
    val extended = profile.protocol in listOf(Protocol.HYSTERIA, Protocol.HYSTERIA2, Protocol.TUIC, Protocol.WIREGUARD, Protocol.SHADOWSOCKSR, Protocol.MIERU, Protocol.ANYTLS, Protocol.SUDOKU)
    val tlsCapable = standard || profile.protocol in listOf(Protocol.HTTP, Protocol.SOCKS)
    Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
        EditorHeader(if (isNew) T("editor.title.new") else T("editor.title.edit"), ::back) {
            runCatching { current() }.onSuccess { node -> error = save(node); if (error == null) close() }.onFailure { error = it.message ?: invalidConfig }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (initial.clashConfig != null) {
                EditorChoice(T("editor.form"), if (rawMode) T("editor.rawJson") else T("editor.form"), listOf(T("editor.form"), T("editor.rawJson"))) { mode ->
                    runCatching {
                        if (mode == 1 && !rawMode) raw = NodeEditing.json.encodeToString(JsonObject.serializer(), current().clashConfig!!)
                        if (mode == 0 && rawMode) { val parsed = current(); encoded = NodeEditing.json.encodeToString(ServerProfile.serializer(), parsed); baseline = encoded; port = parsed.port.toString(); alterId = parsed.alterId.toString() }
                        rawMode = mode == 1
                    }.onFailure { error = it.message }
                }
            }
            if (rawMode) {
                Text(T("editor.rawHint"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                EditorInput("Clash 节点 JSON", raw, { raw = it }, multiline = true)
            } else {
                EditorInput(T("editor.name"), profile.name, { value -> edit { it.copy(name = value) } })
                if (initial.clashConfig == null) EditorChoice(T("editor.protocol"), profile.protocol.label, Protocol.entries.map { it.label }) { index ->
                    val type = Protocol.entries[index]
                    edit { NodeEditing.blank(type).copy(id = it.id, name = it.name, address = it.address, port = it.port, groupId = it.groupId, subscriptionId = it.subscriptionId) }; options = "{}"; alterId = "0"
                } else Text("协议：${profile.protocol.label} · 高级嵌套参数可在原始 JSON 编辑", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                EditorInput(T("editor.address"), profile.address, { value -> edit { it.copy(address = value) } }, "域名 / IPv4 / IPv6")
                EditorInput(T("editor.port"), port, { port = it })
                if (profile.protocol in listOf(Protocol.VMESS, Protocol.VLESS, Protocol.TUIC)) EditorInput("UUID", profile.uuid, { value -> edit { it.copy(uuid = value) } })
                if (profile.protocol !in listOf(Protocol.VMESS, Protocol.VLESS, Protocol.WIREGUARD)) EditorInput(T("editor.password"), profile.password, { value -> edit { it.copy(password = value) } })
                if (profile.protocol in listOf(Protocol.HTTP, Protocol.SOCKS, Protocol.MIERU)) EditorInput(T("editor.username"), profile.username, { value -> edit { it.copy(username = value) } })
                if (profile.protocol == Protocol.VMESS) EditorInput("Alter ID", alterId, { alterId = it })
                if (profile.protocol in listOf(Protocol.VMESS, Protocol.SHADOWSOCKS, Protocol.SHADOWSOCKSR)) EditorInput(T("editor.security"), profile.security, { value -> edit { it.copy(security = value) } }, "auto / aes-128-gcm / chacha20-ietf-poly1305")
                if (profile.protocol == Protocol.VLESS) {
                    EditorInput("Flow", profile.flow, { value -> edit { it.copy(flow = value) } }, "xtls-rprx-vision")
                    EditorInput("加密方式", profile.encryption, { value -> edit { it.copy(encryption = value) } }, "none")
                }
                if (standard) {
                    EditorChoice(T("editor.transport"), profile.network, listOf("tcp", "ws", "grpc", "httpupgrade", "xhttp")) { index -> edit { it.copy(network = listOf("tcp", "ws", "grpc", "httpupgrade", "xhttp")[index]) } }
                    if (profile.network == "grpc") EditorInput("gRPC Service Name", profile.serviceName, { value -> edit { it.copy(serviceName = value) } }) else {
                        EditorInput("Host", profile.host, { value -> edit { it.copy(host = value) } }); EditorInput(T("editor.path"), profile.path, { value -> edit { it.copy(path = value) } }, "/")
                    }
                    if (profile.network in listOf("tcp", "raw")) EditorChoice("伪装类型", profile.headerType, listOf("none", "http")) { index -> edit { it.copy(headerType = listOf("none", "http")[index]) } }
                }
                if (tlsCapable) EditorChoice(T("editor.tls"), profile.tls, if (profile.protocol == Protocol.VLESS) listOf("none", "tls", "reality") else listOf("none", "tls")) { index -> edit { it.copy(tls = listOf("none", "tls", "reality")[index]) } }
                if (profile.tls != "none" || profile.protocol == Protocol.ANYTLS) {
                    EditorInput("SNI", profile.sni, { value -> edit { it.copy(sni = value) } })
                    EditorInput("ALPN", profile.alpn, { value -> edit { it.copy(alpn = value) } }, "h2,http/1.1")
                    EditorInput("浏览器指纹", profile.fingerprint, { value -> edit { it.copy(fingerprint = value) } }, "chrome")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(T("editor.insecure"), style = MaterialTheme.typography.bodySmall); Switch(profile.allowInsecure, { value -> edit { it.copy(allowInsecure = value) } }) }
                    if (profile.tls == "reality") {
                        EditorInput("Reality 公钥", profile.publicKey, { value -> edit { it.copy(publicKey = value) } })
                        EditorInput("Short ID", profile.shortId, { value -> edit { it.copy(shortId = value) } })
                        EditorInput("Spider X", profile.spiderX, { value -> edit { it.copy(spiderX = value) } })
                    }
                }
                if (extended && initial.clashConfig == null) {
                    Text("协议参数", style = MaterialTheme.typography.bodySmall)
                    val keys = when (profile.protocol) {
                        Protocol.HYSTERIA -> listOf("up" to "上行带宽 Mbps（必填）", "down" to "下行带宽 Mbps（必填）", "obfs" to "混淆")
                        Protocol.HYSTERIA2 -> listOf("obfs" to "混淆类型（salamander）", "obfs-password" to "混淆密码", "up" to "上行带宽 Mbps", "down" to "下行带宽 Mbps")
                        Protocol.TUIC -> listOf("congestion-controller" to "拥塞控制", "udp-relay-mode" to "UDP 转发模式")
                        Protocol.WIREGUARD -> listOf("private-key" to "私钥（必填）", "public-key" to "对端公钥（必填）", "ip" to "本地地址（必填，逗号分隔）", "preshared-key" to "预共享密钥", "mtu" to "MTU", "reserved" to "Reserved（三个字节）")
                        Protocol.SHADOWSOCKSR -> listOf("protocol" to "SSR 协议", "protocol-param" to "协议参数", "obfs" to "混淆", "obfs-param" to "混淆参数")
                        Protocol.MIERU -> listOf("transport" to "传输（TCP / UDP）", "port-range" to "端口范围", "mtu" to "MTU", "multiplexing" to "多路复用", "handshake-mode" to "握手模式", "traffic-pattern" to "流量模式")
                        Protocol.ANYTLS -> listOf("client-metadata" to "客户端元数据", "idle-session-check-interval" to "空闲会话检查间隔（秒）", "idle-session-timeout" to "空闲会话超时（秒）", "min-idle-session" to "最小空闲会话数", "disable-reuse" to "禁用会话复用")
                        Protocol.SUDOKU -> listOf("aead-method" to "AEAD 方法", "padding-min" to "最小填充", "padding-max" to "最大填充", "table-type" to "表类型", "enable-pure-downlink" to "纯下行模式", "http-mask" to "HTTP Mask", "http-mask-mode" to "HTTP Mask 模式", "http-mask-tls" to "HTTP Mask TLS", "http-mask-host" to "HTTP Mask Host", "path-root" to "路径前缀", "multiplex" to "多路复用", "custom-table" to "自定义表")
                        else -> emptyList()
                    }
                    val map = runCatching { NodeEditing.options(options) }.getOrDefault(emptyMap())
                    keys.forEach { (key, label) -> EditorInput(label, if (profile.protocol == Protocol.WIREGUARD && key == "public-key") profile.publicKey.ifEmpty { map[key].orEmpty() } else map[key].orEmpty(), { value -> val next = map.toMutableMap(); if (value.isEmpty()) next.remove(key) else next[key] = value; options = JsonObject(next.mapValues { JsonPrimitive(it.value) }).toString(); if (profile.protocol == Protocol.WIREGUARD && key == "public-key") edit { it.copy(publicKey = value) } }) }
                    Text("其他参数（JSON 字符串键值）", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    EditorInput("扩展参数 JSON", options, { options = it }, multiline = true)
                }
                if (initial.subscriptionId != null) Text(T("editor.subscriptionHint"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text(T("editor.discardTitle")) }, confirmButton = { TextButton(close) { Text(T("editor.discard")) } }, dismissButton = { TextButton({ discard = false }) { Text(T("editor.continue")) } })
}
