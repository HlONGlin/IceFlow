package com.iceflow.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iceflow.data.model.*
import com.iceflow.data.repository.RoutingResources
import com.iceflow.ui.theme.IceBlue
import com.iceflow.ui.theme.IceCyan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable internal fun RoutingPage(settings: AppSettings, update: ((AppSettings) -> AppSettings) -> Unit, resources: RoutingResources, editing: (Boolean) -> Unit) {
    val context = LocalContext.current
    val clipboard = remember { context.getSystemService(ClipboardManager::class.java) }
    var menu by remember { mutableStateOf(false) }
    var editor by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var resourcePage by rememberSaveable { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var incoming by remember { mutableStateOf<List<RoutingRule>?>(null) }
    var deleting by remember { mutableStateOf<RoutingRule?>(null) }
    DisposableEffect(editor, resourcePage) { editing(editor || resourcePage); onDispose { editing(false) } }
    fun commit(rules: List<RoutingRule>) { update { it.copy(routingRules = rules, bypassLan = rules.firstOrNull { r -> r.id == "builtin:private" }?.enabled ?: true) } }
    if (resourcePage) { RoutingResourcePage(resources) { resourcePage = false }; return }
    if (editor) {
        val initial = remember(editingId) { settings.routingRules.firstOrNull { it.id == editingId } ?: RoutingRule(action = RouteAction.PROXY) }
        RuleEditor(initial, { editor = false }) { rule ->
            val list = settings.routingRules.toMutableList(); val index = list.indexOfFirst { it.id == rule.id }
            if (index >= 0) list[index] = rule else list.add(0, rule)
            commit(list); editor = false
        }; return
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            IconButton({ editingId = null; editor = true }) { Icon(Icons.Default.Add, "添加路由规则", tint = IceBlue) }
            Box {
                IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, "路由操作", tint = IceBlue) }
                IceDropdownMenu(menu, { menu = false }) {
                    IceMenuItem("禁用所有规则", Icons.Default.Close) { menu = false; commit(settings.routingRules.map { it.copy(enabled = false) }) }
                    IceMenuItem("启用所有规则", Icons.Default.Check) { menu = false; commit(settings.routingRules.map { it.copy(enabled = true) }) }
                    HorizontalDivider(Modifier.padding(horizontal = 12.dp), color = IceCyan)
                    IceMenuItem("从剪贴板导入 JSON 规则", Icons.Default.Add) {
                        menu = false
                        runCatching { RoutingRules.decode(clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()) }.onSuccess { incoming = it }.onFailure { notice = it.message ?: "导入失败" }
                    }
                    IceMenuItem("导出到剪贴板", Icons.Default.Send) { menu = false; clipboard?.setPrimaryClip(ClipData.newPlainText("IceFlow 路由规则", RoutingRules.encode(settings.routingRules))); notice = "已复制 ${settings.routingRules.size} 条规则" }
                    IceMenuItem("管理路由资源", Icons.Default.Settings) { menu = false; resourcePage = true }
                }
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("规则从上到下优先匹配，未匹配流量使用当前节点。修改后重新连接生效。", color = Color(0xFF8E8E93), style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().background(IceCyan, RoundedCornerShape(12.dp)).padding(14.dp)) }
            if (settings.routingRules.isEmpty()) item { Text("暂无规则，点击右上角添加。", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp)) }
            itemsIndexed(settings.routingRules, key = { _, rule -> rule.id }) { index, rule ->
                Surface(shape = RoundedCornerShape(14.dp), color = Color.White, shadowElevation = 1.dp, border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEAF0F6))) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(rule.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            IconButton({ editingId = rule.id; editor = true }, Modifier.size(36.dp)) { Icon(Icons.Default.Settings, "编辑${rule.name}", tint = IceBlue, modifier = Modifier.size(20.dp)) }
                        }
                        Text(rule.summary, style = MaterialTheme.typography.bodySmall, color = IceBlue, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = 8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(rule.action.label, color = if (rule.action == RouteAction.BLOCK) Color(0xFFFF3B30) else IceBlue, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            TextButton(enabled = index > 0, onClick = { val list = settings.routingRules.toMutableList(); java.util.Collections.swap(list, index, index - 1); commit(list) }, contentPadding = PaddingValues(4.dp)) { Text("上移", style = MaterialTheme.typography.labelSmall) }
                            TextButton(enabled = index < settings.routingRules.lastIndex, onClick = { val list = settings.routingRules.toMutableList(); java.util.Collections.swap(list, index, index + 1); commit(list) }, contentPadding = PaddingValues(4.dp)) { Text("下移", style = MaterialTheme.typography.labelSmall) }
                            IconButton({ deleting = rule }, Modifier.size(32.dp)) { Icon(Icons.Default.Delete, "删除${rule.name}", tint = Color(0xFF8E8E93), modifier = Modifier.size(17.dp)) }
                            Switch(rule.enabled, { enabled -> commit(settings.routingRules.map { if (it.id == rule.id) it.copy(enabled = enabled) else it }) })
                        }
                    }
                }
            }
        }
    }
    notice?.let { message -> AlertDialog(onDismissRequest = { notice = null }, title = { Text("路由") }, text = { Text(message) }, confirmButton = { TextButton({ notice = null }) { Text("确定") } }) }
    incoming?.let { rules -> AlertDialog(onDismissRequest = { incoming = null }, title = { Text("导入 ${rules.size} 条规则") }, text = { Text("追加将放在现有规则之前；替换将移除现有规则。") }, confirmButton = { TextButton({ commit(rules + settings.routingRules); incoming = null }) { Text("追加") } }, dismissButton = { Row { TextButton({ incoming = null }) { Text("取消") }; TextButton({ commit(rules); incoming = null }) { Text("替换") } } }) }
    deleting?.let { rule -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("删除规则？") }, text = { Text(rule.name) }, confirmButton = { TextButton({ commit(settings.routingRules.filterNot { it.id == rule.id }); deleting = null }) { Text("删除") } }, dismissButton = { TextButton({ deleting = null }) { Text("取消") } }) }
}

@Composable internal fun EditorHeader(title: String, onBack: () -> Unit, onSave: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onBack) { Icon(Icons.Default.Close, "关闭", tint = IceBlue) }
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (onSave != null) IconButton(onSave) { Icon(Icons.Default.Check, "保存", tint = IceBlue) }
    }
}

@Composable internal fun EditorInput(label: String, value: String, change: (String) -> Unit, hint: String = "", multiline: Boolean = false) {
    OutlinedTextField(value, change, modifier = Modifier.fillMaxWidth(), label = { Text(label, style = MaterialTheme.typography.bodySmall) }, placeholder = { Text(hint, style = MaterialTheme.typography.bodySmall) }, textStyle = MaterialTheme.typography.bodySmall, singleLine = !multiline, minLines = if (multiline) 3 else 1, maxLines = if (multiline) 8 else 1, shape = RoundedCornerShape(12.dp))
}

@Composable internal fun EditorChoice(label: String, value: String, choices: List<String>, select: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(Modifier.fillMaxWidth().clickable { expanded = true }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f)); Text(value, color = IceBlue, style = MaterialTheme.typography.bodySmall); Icon(Icons.Default.MoreVert, null, tint = IceBlue, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded, { expanded = false }, containerColor = Color.White) { choices.forEachIndexed { index, option -> DropdownMenuItem(text = { Text(option, style = MaterialTheme.typography.bodySmall) }, onClick = { expanded = false; select(index) }) } }
    }
}

@Composable internal fun RuleEditor(initial: RoutingRule, close: () -> Unit, save: (RoutingRule) -> Unit) {
    var name by rememberSaveable(initial.id) { mutableStateOf(initial.name) }
    var action by rememberSaveable(initial.id) { mutableStateOf(initial.action.name) }
    var domains by rememberSaveable(initial.id) { mutableStateOf(initial.domains.joinToString(10.toChar().toString())) }
    var ips by rememberSaveable(initial.id) { mutableStateOf(initial.ips.joinToString(10.toChar().toString())) }
    var port by rememberSaveable(initial.id) { mutableStateOf(initial.port) }
    var source by rememberSaveable(initial.id) { mutableStateOf(initial.source.joinToString(10.toChar().toString())) }
    var sourcePort by rememberSaveable(initial.id) { mutableStateOf(initial.sourcePort) }
    var network by rememberSaveable(initial.id) { mutableStateOf(initial.network) }
    var protocol by rememberSaveable(initial.id) { mutableStateOf(initial.protocols.joinToString(",")) }
    var error by remember { mutableStateOf<String?>(null) }
    var discard by remember { mutableStateOf(false) }
    fun lines(value: String) = value.lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    fun current() = initial.copy(name = name.trim(), action = RouteAction.valueOf(action), domains = lines(domains), ips = lines(ips), port = port.trim(), source = lines(source), sourcePort = sourcePort.trim(), network = network.trim(), protocols = protocol.split(',').map { it.trim() }.filter { it.isNotEmpty() })
    fun back() { if (current() == initial) close() else discard = true }
    BackHandler { back() }
    Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
        EditorHeader(if (initial.name.isEmpty()) "添加路由规则" else "编辑路由规则", ::back) { runCatching { current().also { it.validate() } }.onSuccess(save).onFailure { error = it.message } }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            EditorInput("规则名称", name, { name = it })
            EditorChoice("路由目标", RouteAction.valueOf(action).label, RouteAction.entries.map { it.label }) { action = RouteAction.entries[it].name }
            Text("不同条件之间是「且」，同一条件内多项是「或」。域名 / IP 每行一项。", style = MaterialTheme.typography.labelSmall, color = Color(0xFF8E8E93))
            EditorInput("域名", domains, { domains = it }, "domain:example.com / full:www.example.com / geosite:cn / regexp:…", true)
            EditorInput("IP / CIDR", ips, { ips = it }, "192.168.0.0/16 / geoip:cn", true)
            EditorInput("目标端口", port, { port = it }, "80,443,1000-2000")
            EditorChoice("网络", network.ifBlank { "全部" }, listOf("全部", "TCP", "UDP", "TCP + UDP")) { network = listOf("", "tcp", "udp", "tcp,udp")[it] }
            EditorInput("来源 IP / CIDR", source, { source = it }, multiline = true)
            EditorInput("来源端口", sourcePort, { sourcePort = it }, "1000-65535")
            EditorInput("嗅探协议", protocol, { protocol = it }, "http,tls,bittorrent,quic")
        }
    }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("放弃修改？") }, confirmButton = { TextButton(close) { Text("放弃") } }, dismissButton = { TextButton({ discard = false }) { Text("继续编辑") } })
}

@Composable private fun RoutingResourcePage(resources: RoutingResources, back: () -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    BackHandler { if (busy == null) back() }
    Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
        EditorHeader("路由资源", { if (busy == null) back() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("已内置中国域名与 IP 资源，可离线使用。更新成功后重新连接生效；校验失败保留原资源。", style = MaterialTheme.typography.bodySmall, color = Color(0xFF8E8E93))
            resources.names.forEach { name ->
                var url by rememberSaveable(name) { mutableStateOf(resources.url(name)) }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (name == "geosite.dat") "域名数据库 · geosite.dat" else "IP 数据库 · geoip.dat", style = MaterialTheme.typography.bodySmall)
                    Text(remember(refresh) { resources.description(name) }, style = MaterialTheme.typography.labelSmall, color = IceBlue)
                    EditorInput("下载地址", url, { url = it })
                    TextButton(enabled = busy == null, onClick = { busy = name; scope.launch { message = withContext(Dispatchers.IO) { runCatching { resources.update(name, url) }.fold({ "$name 更新成功，请重新连接" }, { "更新失败：${it.message}" }) }; refresh++; busy = null } }) {
                        if (busy == name) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(if (busy == name) "更新中…" else "更新资源")
                    }
                }
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = IceBlue) }
        }
    }
}
