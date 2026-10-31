package com.iceflow.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.Alignment
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.iceflow.data.NodeEditing
import com.iceflow.data.model.*
import com.iceflow.i18n.T
import kotlinx.serialization.json.*

internal enum class ManualAddPage { CHAIN_EDITOR, BALANCE_EDITOR }

@Composable internal fun ServerTypeLabel(server: ServerProfile): String = when (server.composition?.kind) {
    ProxyCompositionKind.CHAIN -> T("groups.chain")
    ProxyCompositionKind.BALANCE -> T("groups.loadBalance")
    null -> server.protocol.label
}

@Composable internal fun ManualAddScreen(
    initial: ServerProfile,
    servers: List<ServerProfile>,
    coreVariant: String,
    onDismiss: () -> Unit,
    onSave: (ServerProfile) -> String?,
) {
    val original = requireNotNull(initial.composition)
    val focusManager = LocalFocusManager.current
    val balancing = original.kind == ProxyCompositionKind.BALANCE
    var configName by rememberSaveable(initial.id) { mutableStateOf(initial.name) }
    var serverIds by rememberSaveable(initial.id) { mutableStateOf(original.serverIds) }
    var strategy by rememberSaveable(initial.id) { mutableStateOf(original.strategy) }
    var testUrl by rememberSaveable(initial.id) { mutableStateOf(original.testUrl) }
    var intervalText by rememberSaveable(initial.id) { mutableStateOf(original.intervalSeconds.toString()) }
    var picker by rememberSaveable(initial.id) { mutableStateOf(false) }
    var query by rememberSaveable(initial.id) { mutableStateOf("") }
    var strategyMenu by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var discard by remember { mutableStateOf(false) }
    val strategies = if (coreVariant == "single") listOf(LoadBalanceStrategy.ROUND_ROBIN, LoadBalanceStrategy.CONSISTENT_HASHING, LoadBalanceStrategy.STICKY_SESSIONS)
        else listOf(LoadBalanceStrategy.RANDOM, LoadBalanceStrategy.ROUND_ROBIN, LoadBalanceStrategy.LEAST_PING, LoadBalanceStrategy.LEAST_LOAD)
    val dirty = configName != initial.name || serverIds != original.serverIds || strategy != original.strategy || testUrl != original.testUrl || intervalText != original.intervalSeconds.toString()
    fun back() { if (picker) picker = false else if (dirty) discard = true else onDismiss() }
    BackHandler(onBack = ::back)
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).background(MaterialTheme.colorScheme.surface).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = ::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, T("common.cancel")) }
            Text(if (picker) T("groups.balance.addServer") else if (balancing) T("groups.loadBalance") else T("groups.chain"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (picker) {
                IconButton(onClick = { picker = false }) { Icon(Icons.Filled.Check, T("common.done")) }
            } else {
                IconButton(onClick = {
                    focusManager.clearFocus()
                    val interval = intervalText.toIntOrNull()
                    if (balancing && interval == null) { error = "请输入有效的检查间隔" }
                    else {
                        val next = initial.copy(name = configName, latencyMs = null, composition = original.copy(serverIds = serverIds, strategy = strategy, testUrl = testUrl.trim(), intervalSeconds = interval ?: 300))
                        error = onSave(next)
                        if (error == null) onDismiss()
                    }
                }) { Icon(Icons.Filled.Check, T("common.save"), tint = MaterialTheme.colorScheme.primary) }
            }
        }
        AnimatedContent(targetState = picker, modifier = Modifier.weight(1f).fillMaxWidth(), transitionSpec = {
            val direction = if (targetState) 1 else -1
            fadeIn(tween(220)) + slideInHorizontally(tween(220)) { direction * it / 8 } togetherWith
                fadeOut(tween(160)) + slideOutHorizontally(tween(220)) { -direction * it / 8 }
        }, label = "configuration-servers") { selecting ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
                if (selecting) {
                    OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(bottom = 8.dp), label = { Text(T("home.searchHint")) }, singleLine = true)
                    val candidates = servers.filter { it.id != initial.id && it.composition == null && (query.isBlank() || it.name.contains(query, true) || it.address.contains(query, true)) }
                    if (candidates.isEmpty()) Text("暂无可添加的服务器配置", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                    candidates.forEach { server ->
                        Row(Modifier.fillMaxWidth().clickable {
                            serverIds = if (server.id in serverIds) serverIds - server.id else serverIds + server.id
                        }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(server.name, style = MaterialTheme.typography.bodyMedium)
                                Text(server.protocol.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Checkbox(server.id in serverIds, onCheckedChange = { selected -> serverIds = if (selected) serverIds + server.id else serverIds - server.id })
                        }
                    }
                } else {
                    OutlinedTextField(configName, { configName = it }, Modifier.fillMaxWidth().padding(vertical = 8.dp), label = { Text("配置名称") }, singleLine = true)
                    if (balancing) {
                        Box {
                            ManualSettingRow(T("groups.balance.strategy"), T(strategy.stringKey)) { strategyMenu = true }
                            IceDropdownMenu(strategyMenu, { strategyMenu = false }) {
                                strategies.forEach { item -> IceMenuItem(T(item.stringKey), Icons.Filled.Balance) { strategy = item; strategyMenu = false } }
                            }
                        }
                        OutlinedTextField(testUrl, { testUrl = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text(T("groups.balance.testUrl")) }, placeholder = { Text(T("common.notSet")) }, singleLine = true)
                        OutlinedTextField(intervalText, { intervalText = it.filter(Char::isDigit) }, Modifier.fillMaxWidth().padding(vertical = 8.dp), label = { Text(T("groups.balance.interval")) }, singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                    } else {
                        Text("按列表顺序依次连接，最后一个服务器为出口。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
                    }
                    OutlinedButton({ focusManager.clearFocus(); query = ""; picker = true }, Modifier.fillMaxWidth().padding(vertical = 12.dp).heightIn(min = 48.dp), shape = RoundedCornerShape(8.dp)) {
                        Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                        Text(T("groups.balance.addServer"), modifier = Modifier.padding(start = 8.dp))
                    }
                    serverIds.forEachIndexed { index, id ->
                        val server = servers.firstOrNull { it.id == id }
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${index + 1}. ${server?.name ?: "服务器已删除"}", style = MaterialTheme.typography.bodyMedium)
                                server?.let { Text(it.protocol.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                            if (!balancing) {
                                IconButton(onClick = {
                                    serverIds = serverIds.toMutableList().apply { add(index - 1, removeAt(index)) }
                                }, enabled = index > 0) { Icon(Icons.Filled.KeyboardArrowUp, "上移") }
                                IconButton(onClick = {
                                    serverIds = serverIds.toMutableList().apply { add(index + 1, removeAt(index)) }
                                }, enabled = index < serverIds.lastIndex) { Icon(Icons.Filled.KeyboardArrowDown, "下移") }
                            }
                            IconButton(onClick = { serverIds = serverIds - id }) { Icon(Icons.Filled.Delete, T("common.delete")) }
                        }
                    }
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
    }
    if (discard) AlertDialog(
        onDismissRequest = { discard = false },
        title = { Text("放弃修改？") }, text = { Text("尚未保存的配置将丢失。") },
        confirmButton = { TextButton(onClick = { discard = false; onDismiss() }) { Text("放弃") } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text(T("common.cancel")) } },
    )
}

@Composable private fun ManualSettingRow(title: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

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
