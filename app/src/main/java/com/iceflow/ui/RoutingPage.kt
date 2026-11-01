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
import com.iceflow.ui.theme.IceRadius
import com.iceflow.data.model.*
import com.iceflow.data.repository.RoutingResources
import com.iceflow.data.repository.RemoteRoutingRuleSetRepository
import com.iceflow.service.RemoteRuleSetScheduler
import com.iceflow.i18n.T
import com.iceflow.i18n.Tf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable internal fun RoutingPage(settings: AppSettings, store: ServerStore, update: ((AppSettings) -> AppSettings) -> Unit, resources: RoutingResources, ruleSets: RemoteRoutingRuleSetRepository, editing: (Boolean) -> Unit) {
    val context = LocalContext.current
    val clipboard = remember { context.getSystemService(ClipboardManager::class.java) }
    var menu by remember { mutableStateOf(false) }
    var editor by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var resourcePage by rememberSaveable { mutableStateOf(false) }
    var ruleSetPage by rememberSaveable { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var incoming by remember { mutableStateOf<List<RoutingRule>?>(null) }
    var deleting by remember { mutableStateOf<RoutingRule?>(null) }
    val addLabel = T("route.add")
    val actionsLabel = T("route.actions")
    val routeTitle = T("routes.rule")
    val importFailed = T("route.importFailed")
    val noImport = T("route.noImport")
    val copiedFormat = T("route.copied")
    val importText = T("route.importText")
    val description = T("route.description")
    val empty = T("route.empty")
    DisposableEffect(editor, resourcePage, ruleSetPage) { editing(editor || resourcePage || ruleSetPage); onDispose { editing(false) } }
    fun commit(rules: List<RoutingRule>) { update { it.copy(routingRules = rules, bypassLan = rules.firstOrNull { r -> r.id == "builtin:private" }?.enabled ?: true) } }
    if (resourcePage) { RoutingResourcePage(resources) { resourcePage = false }; return }
    if (ruleSetPage) { RoutingRuleSetPage(settings, update, ruleSets) { ruleSetPage = false }; return }
    if (ruleSetPage) { RoutingRuleSetPage(settings, update, ruleSets) { ruleSetPage = false }; return }
    if (editor) {
        val initial = remember(editingId) { settings.routingRules.firstOrNull { it.id == editingId } ?: RoutingRule(action = RouteAction.PROXY) }
        RuleEditor(initial, store, { editor = false }) { rule ->
            val list = settings.routingRules.toMutableList(); val index = list.indexOfFirst { it.id == rule.id }
            if (index >= 0) list[index] = rule else list.add(0, rule)
            commit(list); editor = false
        }; return
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().height(68.dp).background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
             IconButton({ editingId = null; editor = true }) { Icon(Icons.Default.Add, addLabel, tint = MaterialTheme.colorScheme.primary) }
            Box {
                  IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, actionsLabel, tint = MaterialTheme.colorScheme.primary) }
                IceDropdownMenu(menu, { menu = false }) {
                     IceMenuItem(T("route.disableAll"), Icons.Default.Close) { menu = false; commit(settings.routingRules.map { it.copy(enabled = false) }) }
                     IceMenuItem(T("route.enableAll"), Icons.Default.Check) { menu = false; commit(settings.routingRules.map { it.copy(enabled = true) }) }
                    HorizontalDivider(Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                     IceMenuItem(T("route.importJson"), Icons.Default.Add) {
                        menu = false
                         runCatching { RoutingRules.decode(clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()) }.onSuccess { incoming = it }.onFailure { notice = it.message ?: importFailed }
                    }
                     IceMenuItem(T("route.importClash"), Icons.Default.Add) {
                        menu = false
                        runCatching {
                            val text = clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                            com.iceflow.data.ClashYamlParser.parseRules(text)
                         }.onSuccess { rules ->
                             if (rules.isEmpty()) notice = noImport
                            else incoming = rules
                         }.onFailure { notice = it.message ?: importFailed }
                    }
                     IceMenuItem(T("route.export"), Icons.Default.Send) { menu = false; clipboard?.setPrimaryClip(ClipData.newPlainText(routeTitle, RoutingRules.encode(settings.routingRules))); notice = String.format(copiedFormat, settings.routingRules.size) }
                     IceMenuItem(T("route.resources"), Icons.Default.Settings) { menu = false; resourcePage = true }
                     IceMenuItem(T("route.ruleSets"), Icons.Default.List) { menu = false; ruleSetPage = true }
                     IceMenuItem(T("route.ruleSets"), Icons.Default.List) { menu = false; ruleSetPage = true }
                }
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
             item { Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(IceRadius.row)).padding(14.dp)) }
             if (settings.routingRules.isEmpty()) item { Text(empty, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp)) }
            itemsIndexed(settings.routingRules, key = { _, rule -> rule.id }) { index, rule ->
                 Surface(shape = RoundedCornerShape(IceRadius.row), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(rule.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                             IconButton({ editingId = rule.id; editor = true }, Modifier.size(36.dp)) { Icon(Icons.Default.Settings, "编辑${rule.name}", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) }
                        }
                         Text(rule.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = 8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                             val targetName = rule.outboundTargetId?.let { id ->
                                 when {
                                     id.startsWith("node:") -> store.servers.firstOrNull { it.id == id.removePrefix("node:") }?.name
                                     id.startsWith("group:") -> (store.localGroups.firstOrNull { it.id == id.removePrefix("group:") }?.name ?: store.subscriptions.firstOrNull { it.id == id.removePrefix("group:") }?.name)
                                     else -> null
                                 }
                             }
                             val missingTarget = rule.outboundTargetId != null && (targetName == null || RoutingOutboundTargets.resolve(rule.outboundTargetId, store) == null)
                             val actionLabel = T(when (rule.action) { RouteAction.PROXY -> "route.targetProxy"; RouteAction.DIRECT -> "route.targetDirect"; RouteAction.BLOCK -> "route.targetBlock" })
                             Text(if (missingTarget) T("route.targetMissing") else targetName ?: actionLabel, color = if (missingTarget || rule.action == RouteAction.BLOCK) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                             TextButton(enabled = index > 0, onClick = { val list = settings.routingRules.toMutableList(); java.util.Collections.swap(list, index, index - 1); commit(list) }, contentPadding = PaddingValues(4.dp)) { Text(T("route.moveUp"), style = MaterialTheme.typography.labelSmall) }
                             TextButton(enabled = index < settings.routingRules.lastIndex, onClick = { val list = settings.routingRules.toMutableList(); java.util.Collections.swap(list, index, index + 1); commit(list) }, contentPadding = PaddingValues(4.dp)) { Text(T("route.moveDown"), style = MaterialTheme.typography.labelSmall) }
                            IconButton({ deleting = rule }, Modifier.size(32.dp)) { Icon(Icons.Default.Delete, "删除${rule.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp)) }
                            Switch(rule.enabled, { enabled -> commit(settings.routingRules.map { if (it.id == rule.id) it.copy(enabled = enabled) else it }) })
                        }
                    }
                }
            }
        }
    }
    notice?.let { message -> AlertDialog(onDismissRequest = { notice = null }, title = { Text(routeTitle) }, text = { Text(message) }, confirmButton = { TextButton({ notice = null }) { Text(T("common.ok")) } }) }
    incoming?.let { rules -> AlertDialog(onDismissRequest = { incoming = null }, title = { Text(Tf("route.importTitle", rules.size)) }, text = { Text(importText) }, confirmButton = { TextButton({ commit(rules + settings.routingRules); incoming = null }) { Text(T("route.append")) } }, dismissButton = { Row { TextButton({ incoming = null }) { Text(T("common.cancel")) }; TextButton({ commit(rules); incoming = null }) { Text(T("route.replace")) } } }) }
    deleting?.let { rule -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text(T("route.deleteTitle")) }, text = { Text(rule.name) }, confirmButton = { TextButton({ commit(settings.routingRules.filterNot { it.id == rule.id }); deleting = null }) { Text(T("common.delete")) } }, dismissButton = { TextButton({ deleting = null }) { Text(T("common.cancel")) } }) }
}

@Composable internal fun EditorHeader(title: String, onBack: () -> Unit, onSave: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onBack) { Icon(Icons.Default.Close, T("common.close"), tint = MaterialTheme.colorScheme.primary) }
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (onSave != null) IconButton(onSave) { Icon(Icons.Default.Check, T("common.save"), tint = MaterialTheme.colorScheme.primary) }
    }
}

@Composable internal fun EditorInput(label: String, value: String, change: (String) -> Unit, hint: String = "", multiline: Boolean = false) {
    OutlinedTextField(value, change, modifier = Modifier.fillMaxWidth(), label = { Text(label, style = MaterialTheme.typography.bodySmall) }, placeholder = { Text(hint, style = MaterialTheme.typography.bodySmall) }, textStyle = MaterialTheme.typography.bodySmall, singleLine = !multiline, minLines = if (multiline) 3 else 1, maxLines = if (multiline) 8 else 1, shape = RoundedCornerShape(12.dp))
}

@Composable internal fun EditorChoice(label: String, value: String, choices: List<String>, select: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(Modifier.fillMaxWidth().clickable { expanded = true }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f)); Text(value, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall); Icon(Icons.Default.MoreVert, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded, { expanded = false }, containerColor = MaterialTheme.colorScheme.surface) { choices.forEachIndexed { index, option -> DropdownMenuItem(text = { Text(option, style = MaterialTheme.typography.bodySmall) }, onClick = { expanded = false; select(index) }) } }
    }
}

@Composable internal fun RuleEditor(initial: RoutingRule, store: ServerStore, close: () -> Unit, save: (RoutingRule) -> Unit) {
    var name by rememberSaveable(initial.id) { mutableStateOf(initial.name) }
    var target by rememberSaveable(initial.id) { mutableStateOf(initial.outboundTargetId ?: ("action:" + initial.action.name)) }
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
    val targetChoices = buildList {
        add("action:PROXY" to T("route.targetProxy")); add("action:DIRECT" to T("route.targetDirect")); add("action:BLOCK" to T("route.targetBlock"))
        store.servers.forEach { node ->
            val id = "node:" + node.id
            if (RoutingOutboundTargets.resolve(id, store) != null) add(id to (T("route.targetNode") + ": " + node.name))
        }
        (store.localGroups.map { it.id to it.name } + store.subscriptions.map { it.id to it.name }).forEach { (id, label) ->
            val targetId = "group:" + id
            if (RoutingOutboundTargets.resolve(targetId, store) != null) add(targetId to (T("route.targetGroup") + ": " + label))
        }
        val exists = initial.outboundTargetId?.let { RoutingOutboundTargets.resolve(it, store) != null } ?: true
        if (!exists) add(initial.outboundTargetId!! to (T("route.targetMissing") + ": " + initial.outboundTargetId))
    }
    fun current(): RoutingRule {
        val selectedAction = target.takeIf { it.startsWith("action:") }?.removePrefix("action:")?.let(RouteAction::valueOf) ?: RouteAction.PROXY
        return initial.copy(name = name.trim(), action = selectedAction, outboundTargetId = target.takeUnless { it.startsWith("action:") }, domains = lines(domains), ips = lines(ips), port = port.trim(), source = lines(source), sourcePort = sourcePort.trim(), network = network.trim(), protocols = protocol.split(',').map { it.trim() }.filter { it.isNotEmpty() })
    }
    fun back() { if (current() == initial) close() else discard = true }
    BackHandler { back() }
    Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
        EditorHeader(if (initial.name.isEmpty()) "添加路由规则" else "编辑路由规则", ::back) { runCatching { current().also { it.validate() } }.onSuccess(save).onFailure { error = it.message } }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            EditorInput("规则名称", name, { name = it })
            EditorChoice(T("route.target"), targetChoices.firstOrNull { it.first == target }?.second ?: T("route.targetMissing"), targetChoices.map { it.second }) { target = targetChoices[it].first }
            Text("不同条件之间是「且」，同一条件内多项是「或」。域名 / IP 每行一项。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Text("已内置中国域名与 IP 资源，可离线使用。更新成功后重新连接生效；校验失败保留原资源。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            resources.names.forEach { name ->
                var url by rememberSaveable(name) { mutableStateOf(resources.url(name)) }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (name == "geosite.dat") "域名数据库 · geosite.dat" else "IP 数据库 · geoip.dat", style = MaterialTheme.typography.bodySmall)
                    Text(remember(refresh) { resources.description(name) }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    EditorInput("下载地址", url, { url = it })
                    TextButton(enabled = busy == null, onClick = { busy = name; scope.launch { message = withContext(Dispatchers.IO) { runCatching { resources.update(name, url) }.fold({ "$name 更新成功，请重新连接" }, { "更新失败：${it.message}" }) }; refresh++; busy = null } }) {
                        if (busy == name) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(if (busy == name) "更新中…" else "更新资源")
                    }
                }
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        }
    }
}
