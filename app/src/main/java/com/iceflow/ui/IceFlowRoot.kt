package com.iceflow.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iceflow.data.model.AppSettings
import com.iceflow.data.model.LocalGroup
import com.iceflow.data.model.GroupConfiguration
import com.iceflow.data.model.GroupSort
import com.iceflow.data.model.ServerProfile
import com.iceflow.data.model.ServerStore
import com.iceflow.data.ShareLinkParser
import com.iceflow.service.AppLog
import com.iceflow.service.TrafficSnapshot
import com.iceflow.service.VpnController
import com.iceflow.service.VpnState
import com.iceflow.core.XrayBridge
import com.iceflow.ui.component.ShareNodeDialog
import com.iceflow.ui.component.latencyColor
import com.iceflow.ui.component.latencyText
import com.iceflow.ui.screen.AppSelectorScreen
import com.iceflow.ui.screen.SettingsScreen
import com.iceflow.ui.theme.IceRadius
import com.iceflow.util.formatSpeed
import com.iceflow.util.formatDuration
import com.iceflow.util.formatBytes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Tab(val title: String, val icon: ImageVector) {
    HOME("节点", Icons.Filled.Home), SUBS("分组", Icons.Filled.Refresh), ROUTES("路由", Icons.Filled.List), SETTINGS("设置", Icons.Filled.Settings)
}

@Composable
fun IceFlowRoot(vm: MainViewModel, onToggleVpn: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(vm) {
        vm.recoveryMessage?.let { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show() }
    }
    val state by vm.vpnState.collectAsStateWithLifecycle()
    val traffic by vm.traffic.collectAsStateWithLifecycle()
    val store by vm.store.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val testing by vm.testing.collectAsStateWithLifecycle()
    val refreshing by vm.refreshingSubscriptions.collectAsStateWithLifecycle()
    val refreshingGroups by vm.refreshingGroupIds.collectAsStateWithLifecycle()
    val subscriptionLoad by vm.subscriptionLoad.collectAsStateWithLifecycle()
    val connectionTest by vm.connectionTest.collectAsStateWithLifecycle()
    val nodeTest by vm.nodeTest.collectAsStateWithLifecycle()
    val bulkTest by vm.bulkTest.collectAsStateWithLifecycle()
    val dismissBulkTest = vm::dismissBulkTest
    LaunchedEffect(vm) { vm.messages.collect { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show() } }
    val publicIp by vm.publicIp.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var groupSettingsOpen by remember { mutableStateOf(false) }
    var settingsDetailOpen by remember { mutableStateOf(false) }
    var logsOpen by rememberSaveable { mutableStateOf(false) }
    var backupOpen by rememberSaveable { mutableStateOf(false) }
    var appsOpen by rememberSaveable { mutableStateOf(false) }
    var shareNodeId by remember { mutableStateOf<String?>(null) }
    val nodeToShare = remember(shareNodeId) { store.servers.firstOrNull { it.id == shareNodeId } }
    var nodeEditorOpen by rememberSaveable { mutableStateOf(false) }
    var nodeEditorId by rememberSaveable { mutableStateOf<String?>(null) }
    var groupId by rememberSaveable { mutableStateOf<String?>(store.selectedGroupId) }
    LaunchedEffect(store.selectedGroupId) { if (groupId != store.selectedGroupId) groupId = store.selectedGroupId }
    var toast by remember { mutableStateOf<String?>(null) }
    var previousState by remember { mutableStateOf<VpnState>(VpnState.Idle) }
    LaunchedEffect(state) {
        if (state is VpnState.Connected && previousState !is VpnState.Connected) toast = "已连接"
        if (state is VpnState.Idle && previousState is VpnState.Connected) toast = "已断开连接"
        previousState = state
    }
    // UI 侧感知耗时：Compose 真正观察到「已连接」的时刻（含主线程排队与重组延迟）
    LaunchedEffect(state) {
        if (state is VpnState.Connected) {
            val clickedAt = VpnController.clickAt
            if (clickedAt > 0) {
                val total = android.os.SystemClock.elapsedRealtime() - clickedAt
                android.util.Log.i("IceFlowUi", "[TIMING] UI 观察到已连接：$total ms")
                AppLog.write(context, "INFO", "[计时] UI 观察到已连接：$total ms")
            }
        }
    }
    LaunchedEffect(toast) { if (toast != null) { kotlinx.coroutines.delay(2000); toast = null } }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            if (nodeEditorOpen) {
                val initialJson = rememberSaveable(nodeEditorId) {
                    val node = store.servers.firstOrNull { it.id == nodeEditorId } ?: com.iceflow.data.NodeEditing.blank().copy(groupId = groupId?.takeIf { id -> store.localGroups.any { it.id == id } })
                    com.iceflow.data.NodeEditing.json.encodeToString(ServerProfile.serializer(), node)
                }
                val initial = remember(initialJson) { com.iceflow.data.NodeEditing.json.decodeFromString(ServerProfile.serializer(), initialJson) }
                NodeEditorPage(initial, nodeEditorId == null, { nodeEditorOpen = false }, vm::saveServer)
            } else if (backupOpen) {
                BackupPage(vm::exportBackup, vm::restoreBackup, { backupOpen = false })
            } else if (logsOpen) {
                RuntimeLogPage(
                    readLogs = vm::readLogs,
                    onBack = { logsOpen = false },
                    logRetentionDays = settings.logRetentionDays,
                    modifier = Modifier.weight(1f)
                )
            } else if (appsOpen) {
                AppSelectorScreen(
                    proxyMode = settings.appProxyMode,
                    selectedApps = settings.bypassApps,
                    onSelectionChange = { apps -> vm.updateSettings { it.copy(bypassApps = apps) } },
                    onDismiss = { appsOpen = false }
                )
            } else {
            AnimatedContent(
                targetState = Tab.entries[tab],
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                transitionSpec = { fadeIn(tween(360)) togetherWith fadeOut(tween(300)) },
                label = "tab",
            ) { current ->
                Box(Modifier.fillMaxSize()) {
                    when (current) {
                        Tab.HOME -> WorkbenchPage(state, traffic, store, groupId, publicIp, testing, settings.showTrafficStats, onToggleVpn, vm::select, vm::testAll, vm::testOne, nodeTest, vm::delete, vm::updateServer, vm::moveServerToGroup, { shareNodeId = it.id }, { vm.importText(readClipboard(context)) }, vm::importText, vm::refreshSubscriptions, vm::setGroupSort, vm::sortGroupByLatency, vm::dedupeServers, toast, connectionTest, vm::testRunningConnection, { selected -> groupId = selected; vm.selectGroup(selected) }, { nodeEditorId = null; nodeEditorOpen = true }, { server -> nodeEditorId = server.id; nodeEditorOpen = true }, bulkTest, vm::dismissBulkTest, vm::reorderServers, settings.showNodeLatency)
                        Tab.SUBS -> GroupsPage(store, groupId, { selected -> groupId = selected; vm.selectGroup(selected) }, refreshing, vm::refreshSubscriptions, vm::refreshSubscription, refreshingGroups, vm::saveGroup, vm::deleteLocalGroup, vm::deleteSubscription, { groupSettingsOpen = it }, vm::reorderGroups, subscriptionLoad, vm::previewSubscription, vm::commitSubscriptionPreview, vm::dismissSubscriptionPreview)
                        Tab.SETTINGS -> SettingsScreen(
                            settings = settings,
                            xrayVersion = XrayBridge.version(),
                            coreVersion = if (com.iceflow.core.RustBridge.isAvailable) "已集成" else null,
                            appVersion = vm.appVersion,
                            onUpdate = vm::updateSettings,
                            onOpenLogs = { vm.cleanOldLogs(); logsOpen = true },
                            onOpenBackup = { backupOpen = true },
                            onOpenAppSelector = { appsOpen = true },
                            onDetailOpenChange = { settingsDetailOpen = it },
                        )
                        Tab.ROUTES -> RoutingPage(settings, vm::updateSettings, vm.routingResources, { groupSettingsOpen = it })
                    }
                }
            }
            }
            if (!groupSettingsOpen && !settingsDetailOpen && !logsOpen && !backupOpen && !nodeEditorOpen && !appsOpen) Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).navigationBarsPadding().padding(top = 8.dp, bottom = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Tab.entries.forEachIndexed { index, item ->
                    val moduleInteraction = remember { MutableInteractionSource() }
                    Column(Modifier.weight(1f).clickable(
                        interactionSource = moduleInteraction,
                        indication = null,
                        onClick = { tab = index },
                    ), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(item.icon, item.title, tint = if (tab == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(23.dp))
                        Text(item.title, style = MaterialTheme.typography.labelSmall, color = if (tab == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
        Box(Modifier.fillMaxSize()) {
            if (bulkTest.testing || bulkTest.completed > 0) BulkTestOverlay(bulkTest, dismissBulkTest)
            if (!bulkTest.testing && bulkTest.completed == 0 && nodeTest.serverId != null) Box(Modifier.align(Alignment.TopCenter).padding(top = 80.dp)) { NodeLatencyToast(nodeTest) }
        }

        // 分享节点对话框
        if (nodeToShare != null) {
            ShareNodeDialog(
                node = nodeToShare,
                onDismiss = { shareNodeId = null }
            )
        }

    LaunchedEffect(state) {
        if (state is VpnState.Connected) {
            vm.refreshPublicIp()
            // VPN 连接时自动清理旧日志
            vm.cleanOldLogs()
        }
    }
}

@Composable private fun PageTitle(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick = { onAction?.invoke() }, contentPadding = PaddingValues(0.dp)) { Text(action, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun EmptyStateMessage(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
        )
    }
}

@Composable private fun WorkbenchPage(state: VpnState, traffic: TrafficSnapshot, store: ServerStore, groupId: String?, publicIp: String?, testing: Boolean, showTrafficStats: Boolean, onToggle: () -> Unit, onSelect: (String) -> Unit, onTest: () -> Unit, onTestOne: (ServerProfile) -> Unit, nodeTest: NodeTestState, onDelete: (String) -> Unit, onUpdate: (ServerProfile) -> Unit, onMove: (String, String?) -> Unit, onShare: (ServerProfile) -> Unit, onImportClipboard: () -> Unit, onImportText: (String) -> Unit, onRefreshSubs: () -> Unit, onSetGroupSort: (String?, GroupSort) -> Unit, onSortGroupByLatency: (String?) -> Unit, onDedupe: () -> Unit, toast: String?, connectionTest: ConnectionTestState, onTestConnection: () -> Unit, onSelectGroup: (String?) -> Unit, onAddNode: () -> Unit, onEditNode: (ServerProfile) -> Unit, bulkTest: BulkTestState, dismissBulkTest: () -> Unit, onReorderNodes: (String?, List<String>) -> Unit, showNodeLatency: Boolean = false) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    val panelVisibility = remember { MutableTransitionState(false) }
    var lastRunningState by remember { mutableStateOf<VpnState>(VpnState.Connecting) }
    val context = LocalContext.current
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var addMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var sortingKey by remember { mutableStateOf<String?>(null) }
    if (sortingKey != null) BackHandler { sortingKey = null }
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result -> result.contents?.let(onImportText) }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()?.let(onImportText)
    }
    LaunchedEffect(state) { while (state is VpnState.Connected) { now = System.currentTimeMillis(); kotlinx.coroutines.delay(1000) } }
    val selected = store.selected
    val running = state is VpnState.Connected || state is VpnState.Connecting || state is VpnState.Stopping || state is VpnState.WaitingNetwork
    LaunchedEffect(running) { panelVisibility.targetState = running }
    LaunchedEffect(state) { if (running) lastRunningState = state }
    fun visibleNodes(id: String?, filter: String = query) = store.servers.filter { server ->
        if (id == null) server.subscriptionId == null && server.groupId == null
        else server.groupId == id || server.subscriptionId == id
    }.let { servers ->
        (if (id == null) store.localSort else store.localGroups.firstOrNull { it.id == id }?.sort
            ?: store.subscriptions.firstOrNull { it.id == id }?.sort
            ?: GroupSort.ORIGINAL).apply(servers)
    }.filter { filter.isBlank() || it.name.contains(filter, true) || it.address.contains(filter, true) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (searchOpen) "搜索" else "IceFlow", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                IconButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) query = "" }) { Icon(if (searchOpen) Icons.Filled.Close else Icons.Filled.Search, "搜索节点", tint = MaterialTheme.colorScheme.primary) }
                Box {
                    IconButton(onClick = { addMenu = true }) { Icon(Icons.Filled.Add, "添加节点", tint = MaterialTheme.colorScheme.primary) }
                    IceDropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                        IceMenuItem("从剪贴板导入", Icons.Filled.List) { addMenu = false; onImportClipboard() }
                        IceMenuItem("手动输入", Icons.Filled.Add) { addMenu = false; onAddNode() }
                        IceMenuItem("从文件导入", Icons.Filled.Home) { addMenu = false; fileLauncher.launch("*/*") }
                        IceMenuItem("扫描二维码", Icons.Filled.Search) { addMenu = false; scanLauncher.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setBeepEnabled(false)) }
                    }
                }
                Box {
                    IconButton(onClick = { moreMenu = true }) { Icon(Icons.Filled.MoreVert, "更多操作", tint = MaterialTheme.colorScheme.primary) }
                    IceDropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                        IceMenuItem("更新订阅", Icons.Filled.Refresh) { moreMenu = false; onRefreshSubs() }
                        IceMenuItem(if (testing) "测速中…" else "测试连接速度", Icons.Filled.Send, enabled = !testing) { moreMenu = false; onTest() }
                        HorizontalDivider(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                        IceMenuItem("排序", Icons.Filled.List) { moreMenu = false; sortMenu = true }
                        IceMenuItem("节点去重", Icons.Filled.Check) { moreMenu = false; onDedupe() }
                    }
                    IceDropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        GroupSort.entries.forEach { sort ->
                            IceMenuItem(sort.label, Icons.Filled.List, enabled = !testing) {
                                sortMenu = false
                                if (sort == GroupSort.LATENCY) onSortGroupByLatency(groupId) else onSetGroupSort(groupId, sort)
                            }
                        }
                    }
                }
            }
            if (searchOpen) OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), placeholder = { Text("搜索节点名称或地址") }, singleLine = true, shape = RoundedCornerShape(12.dp))
            val visibleGroups = store.orderedGroups
            if (visibleGroups.isEmpty()) {
                val ungroupedNodes = visibleNodes(null)
                if (ungroupedNodes.isEmpty()) {
                    EmptyStateMessage("暂无分组或节点。可在分组页点 + 创建分组，或在节点页点 + 导入节点。", Modifier.weight(1f))
                } else LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = if (running) 112.dp else 80.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    item { Text("未分组节点", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp)) }
                    items(ungroupedNodes, key = { it.id }) { server ->
                        NodeRow(server, store.localGroups, server.id == store.selectedId, { onSelect(server.id) }, { if (!testing) onTestOne(server) }, { onDelete(server.id) }, { onMove(server.id, it) }, { onShare(server) }, { onUpdate(server.copy(name = it)) }, { onEditNode(server) }, showTrafficStats = showTrafficStats, showNodeLatency = showNodeLatency)
                    }
                }
            } else GroupPager(visibleGroups, groupId?.takeIf { id -> visibleGroups.any { it.key == id } }, onSelectGroup, Modifier.weight(1f)) { group ->
                val sorting = sortingKey == group.key
                val nodes = if (sorting) visibleNodes(group.id, "") else visibleNodes(group.id)
                if (sorting) Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("拖动右侧图标调整顺序", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        TextButton(onClick = { sortingKey = null }) { Text("完成", color = MaterialTheme.colorScheme.primary) }
                    }
                    ReorderableNodeList(nodes, { onReorderNodes(group.id, it) }, Modifier.weight(1f), PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = if (running) 112.dp else 80.dp)) { server, handle ->
                        NodeRow(server, store.localGroups, server.id == store.selectedId, {}, {}, {}, {}, {}, {}, dragHandle = handle, showNodeLatency = showNodeLatency)
                    }
                } else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = if (running) 112.dp else 80.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    if (nodes.isEmpty()) item { Text(if (query.isBlank()) "此分组暂无节点" else "没有匹配的节点", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(24.dp)) }
                    items(nodes, key = { it.id }) { server -> NodeRow(server, store.localGroups, server.id == store.selectedId, { onSelect(server.id) }, { if (!testing) onTestOne(server) }, { onDelete(server.id) }, { onMove(server.id, it) }, { onShare(server) }, { onUpdate(server.copy(name = it)) }, { onEditNode(server) }, if (query.isBlank()) ({ sortingKey = group.key }) else null, showTrafficStats = showTrafficStats, showNodeLatency = showNodeLatency) }
                }
            }
        }
        AnimatedVisibility(
            visibleState = panelVisibility,
            // Keep the floating connect control visually separate from the expanded bar.
             modifier = Modifier.align(Alignment.BottomCenter),
            enter = expandVertically(expandFrom = Alignment.Bottom, animationSpec = tween(260)) + fadeIn(tween(220)),
            exit = shrinkVertically(shrinkTowards = Alignment.Bottom, animationSpec = tween(220)),
        ) {
            val panelState = if (!running) lastRunningState else state
            ConnectionPanel(panelState, traffic, now, connectionTest, onTestConnection)
        }
        // The control floats over the panel edge instead of leaving an empty gap.
        Box(Modifier.align(Alignment.BottomCenter).offset(y = (-28).dp)) { ConnectOrb(state, onToggle, 54.dp) }
        if (toast != null) Text(toast, color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f), CircleShape).padding(horizontal = 18.dp, vertical = 9.dp))
    }
}

@Composable internal fun ConnectOrb(state: VpnState, onClick: () -> Unit, size: Dp = 54.dp) {
    val connected = state is VpnState.Connected
    val waitingNetwork = state == VpnState.WaitingNetwork
    // 等待网络恢复时不是"正在连接"，用不同的图标，避免无限转圈造成的误解
    val busy = state == VpnState.Connecting || state == VpnState.Stopping || waitingNetwork
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .91f else 1f, spring(dampingRatio = .6f, stiffness = 480f), label = "connectPress")
    val orbColor by animateColorAsState(if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface, tween(420), label = "connectColor")
    val iconColor by animateColorAsState(if (connected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary, tween(380), label = "connectIcon")
    val rimColor by animateColorAsState(androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.surface, orbColor, .22f), tween(420), label = "connectRim")
    Box(Modifier.size(size).graphicsLayer { scaleX = scale; scaleY = scale }
        .shadow(5.dp, CircleShape, ambientColor = Color.Black.copy(alpha = .10f), spotColor = Color.Black.copy(alpha = .12f))
        .background(Brush.radialGradient(listOf(orbColor, rimColor)), CircleShape)
        .border(1.dp, rimColor, CircleShape)
        .padding(2.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize()
            .background(Brush.verticalGradient(if (connected) listOf(MaterialTheme.colorScheme.primary.copy(alpha = .84f), orbColor) else listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surfaceVariant)), CircleShape)
            .clip(CircleShape)
            .clickable(enabled = !busy || waitingNetwork, interactionSource = interaction, indication = androidx.compose.material3.ripple(), onClick = onClick), contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = when {
                    waitingNetwork -> 2   // 等待网络
                    busy -> 1            // 连接中/断开中
                    else -> 0            // 空闲
                },
                // 连接成功后立即隐藏转圈指示器：退出动画压到 120ms，
                // 否则后端已就绪但转圈仍会残留数百毫秒，用户观感上像"连接很慢"。
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
                label = "connectStatus",
            ) { loading ->
                when (loading) {
                    2 -> Icon(Icons.Filled.WifiOff, "等待网络恢复", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(size * .40f))
                    1 -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(size * .42f), strokeWidth = 2.5.dp)
                    else -> Icon(Icons.Filled.Send, if (connected) "断开连接" else "连接", tint = iconColor, modifier = Modifier.size(size * .44f).graphicsLayer { rotationZ = if (connected) -12f else 0f })
                }
            }
        }
    }
}

@Composable internal fun ConnectionPanel(state: VpnState, traffic: TrafficSnapshot, now: Long, test: ConnectionTestState, onTest: () -> Unit, modifier: Modifier = Modifier) {
    val connected = state is VpnState.Connected
    val busy = state == VpnState.Connecting || state == VpnState.Stopping || state == VpnState.WaitingNetwork
    val panelColor by animateColorAsState(if (connected) MaterialTheme.colorScheme.primary else if (busy) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surface, tween(420), label = "panelColor")
    val textColor = if (connected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val secondaryColor = if (connected) MaterialTheme.colorScheme.onPrimary.copy(alpha = .78f) else MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier.fillMaxWidth().padding(top = 8.dp).shadow(8.dp).background(panelColor)
        .clickable(enabled = connected && !test.testing, onClick = {
            android.util.Log.i("ConnectionPanel", "点击测速: connected=$connected, testing=${test.testing}")
            onTest()
        })
        .height(64.dp).padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("↑ ${formatSpeed(traffic.upSpeed)}", color = textColor, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            Text("↓ ${formatSpeed(traffic.downSpeed)}", color = textColor, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
        Spacer(Modifier.size(width = 76.dp, height = 1.dp))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (test.testing) CircularProgressIndicator(Modifier.size(12.dp), color = textColor, strokeWidth = 1.5.dp)
                Text(when {
                    !connected -> when (state) {
                        VpnState.Stopping -> "正在断开…"
                        VpnState.WaitingNetwork -> "等待网络恢复…"
                        else -> "正在连接…"
                    }
                    test.testing -> "测速中…"
                    test.latencyMs == null -> "点击测速"
                    test.latencyMs < 0 -> "超时 · 重试"
                    else -> "${test.latencyMs} ms"
                }, color = textColor, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 1)
            }
            Text(if (connected) formatDuration(now - (state as VpnState.Connected).since) else "请稍候", color = secondaryColor, style = MaterialTheme.typography.labelSmall)
        }
    }
}


@Composable private fun NodeRow(server: ServerProfile, groups: List<com.iceflow.data.model.LocalGroup>, selected: Boolean, onSelect: () -> Unit, onTest: () -> Unit, onDelete: () -> Unit, onMove: (String?) -> Unit, onShare: () -> Unit, onRename: (String) -> Unit, onEdit: (() -> Unit)? = null, onSort: (() -> Unit)? = null, dragHandle: Modifier? = null, showTrafficStats: Boolean = false, showNodeLatency: Boolean = false) {
    var menu by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf(false) }
    var name by remember(server.name) { mutableStateOf(server.name) }
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, RoundedCornerShape(IceRadius.row))
            .clickable(onClick = onSelect)
            .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dragHandle != null) {
            Box(dragHandle, contentAlignment = Alignment.Center) { Icon(Icons.Filled.Menu, "拖动排序", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.size(10.dp))
        } else {
            Box(Modifier.size(8.dp).background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, androidx.compose.foundation.shape.CircleShape))
            Spacer(Modifier.size(12.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    server.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                // 只有开启了延迟显示、且确实测过速时才展示延迟
                if (showNodeLatency && server.latencyMs != null) {
                    Spacer(Modifier.size(8.dp))
                    Text(
                        latencyText(server.latencyMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = latencyColor(server.latencyMs, MaterialTheme.colorScheme.onSurfaceVariant),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(server.protocol.label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                if (showTrafficStats) {
                    Text("↑ ${formatBytes(server.uploadBytes)}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    Text("↓ ${formatBytes(server.downloadBytes)}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        if (dragHandle == null) {
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(40.dp)) { Icon(Icons.Filled.MoreVert, "节点操作", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(19.dp)) }
                IceDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    IceMenuItem("测速", Icons.Filled.Info) { menu = false; onTest() }
                    IceMenuItem("编辑", Icons.Filled.Settings) { menu = false; if (onEdit != null) onEdit() else edit = true }
                    IceMenuItem("分享节点", Icons.Filled.Share) { menu = false; onShare() }
                    if (onSort != null) IceMenuItem("单独排序", Icons.Filled.List) { menu = false; onSort() }
                    HorizontalDivider(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                    IceMenuItem("删除", Icons.Filled.Delete, destructive = true) { menu = false; onDelete() }
                }
            }
        }
    }
    if (edit) AlertDialog(onDismissRequest = { edit = false }, title = { Text("编辑节点") }, text = { OutlinedTextField(name, { name = it }, label = { Text("节点名称") }, singleLine = true) }, confirmButton = { TextButton(onClick = { edit = false; onRename(name.trim()) }, enabled = name.isNotBlank()) { Text("保存") } }, dismissButton = { TextButton(onClick = { edit = false }) { Text("取消") } })
}

@Composable private fun GroupsPage(store: ServerStore, selectedId: String?, onSelect: (String?) -> Unit, refreshing: Boolean, refresh: () -> Unit, refreshGroup: (String) -> Unit, refreshingGroups: Set<String>, save: (GroupConfiguration) -> String?, deleteLocal: (String) -> Unit, deleteSubscription: (String) -> Unit, onEditingChange: (Boolean) -> Unit, onReorder: (List<String>) -> Unit, subscriptionLoad: SubscriptionLoadState = SubscriptionLoadState.Idle, onPreviewSubscription: (url: String, name: String) -> Unit = { _, _ -> }, onCommitPreview: (groupId: String) -> Unit = {}, onDismissPreview: () -> Unit = {}) {
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingConfigId by remember { mutableStateOf<String?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    DisposableEffect(settingsOpen) {
        onEditingChange(settingsOpen)
        onDispose { onEditingChange(false) }
    }
    fun edit(id: String?) { editingId = id; settingsOpen = true }
    if (settingsOpen) {
        val local = store.localGroups.firstOrNull { it.id == editingId }
        val sub = store.subscriptions.firstOrNull { it.id == editingId }
        val initial = remember(editingId) {
            when {
                sub != null -> GroupConfiguration(sub.id, sub.name, true, sub.url, sub.updateIntervalHours, sub.sort, sub.frontNodeId, sub.landingNodeId)
                local != null -> GroupConfiguration(id = local.id, name = local.name, sort = local.sort, frontNodeId = local.frontNodeId, landingNodeId = local.landingNodeId)
                else -> GroupConfiguration()
            }
        }
        GroupSettingsPage(initial, editingId != null, store.servers, { settingsOpen = false }, { config ->
            // 新增订阅分组时，先保存分组元信息，再进入预览流程
            val isNewSubscription = config.subscription && editingId == null
            val error = save(config)
            if (error != null) return@GroupSettingsPage error
            if (isNewSubscription) {
                pendingConfigId = config.id
                onPreviewSubscription(config.url, config.name)
            }
            settingsOpen = false
            null
        }, { deletingId = editingId })
    } else {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                IconButton(onClick = refresh, enabled = !refreshing) {
                    if (refreshing) CircularProgressIndicator(Modifier.size(22.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                    else Icon(Icons.Filled.Refresh, "刷新全部订阅", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { edit(null) }) { Icon(Icons.Filled.Add, "新增分组", tint = MaterialTheme.colorScheme.primary) }
            }
            if (store.orderedGroups.isEmpty()) {
                EmptyStateMessage("暂无分组。点右上角 + 创建分组；也可到节点页点 + 导入节点。", Modifier.weight(1f))
            } else ReorderableGroupList(store.orderedGroups, onReorder, Modifier.weight(1f)) { group, handle ->
                val sub = store.subscriptions.firstOrNull { it.id == group.id }
                val tags = when {
                    sub != null -> buildList {
                        add("${store.servers.count { it.subscriptionId == sub.id }} 节点")
                        add(dateText(sub.updatedAt))
                        add(if (sub.updateIntervalHours <= 0) "不自动更新" else "每 ${sub.updateIntervalHours} 小时")
                    }
                    group.id != null -> listOf("${store.servers.count { it.groupId == group.id }} 节点", "本地分组")
                    else -> listOf("${store.servers.count { it.subscriptionId == null && it.groupId == null }} 节点", "未分组")
                }
                GroupCard(group.name, tags, false, if (group.id == null) null else ({ edit(group.id) }), if (group.id == null) null else ({ deletingId = group.id }), handle, onRefresh = if (sub != null) ({ refreshGroup(group.id!!) }) else null, refreshing = group.id in refreshingGroups) { onSelect(group.id) }
            }
        }
    }

    // 订阅预览对话框
    val currentLoad = subscriptionLoad
    if (currentLoad != SubscriptionLoadState.Idle) {
        AlertDialog(
            onDismissRequest = { if (currentLoad !is SubscriptionLoadState.Loading) onDismissPreview() },
            title = { Text(when (currentLoad) {
                is SubscriptionLoadState.Loading -> "正在加载订阅…"
                is SubscriptionLoadState.Preview -> "加载完成"
                is SubscriptionLoadState.Failure -> "加载失败"
                else -> ""
            }) },
            text = {
                when (currentLoad) {
                    is SubscriptionLoadState.Loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                        Text("正在获取节点，请稍候…", style = MaterialTheme.typography.bodyMedium)
                    }
                    is SubscriptionLoadState.Preview -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("共发现 ${currentLoad.servers.size} 个节点：", style = MaterialTheme.typography.bodyMedium)
                        val preview = currentLoad.servers.take(5)
                        preview.forEach { server ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape))
                                Text("${server.name}  (${server.protocol.label})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
                        }
                        if (currentLoad.servers.size > 5) {
                            Text("… 还有 ${currentLoad.servers.size - 5} 个节点", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    is SubscriptionLoadState.Failure -> Text(currentLoad.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    else -> {}
                }
            },
            confirmButton = {
                when (currentLoad) {
                    is SubscriptionLoadState.Preview -> TextButton(onClick = { pendingConfigId?.let { onCommitPreview(it); pendingConfigId = null } }) { Text("确认导入") }
                    is SubscriptionLoadState.Failure -> TextButton(onClick = onDismissPreview) { Text("关闭") }
                    else -> {}
                }
            },
            dismissButton = {
                if (currentLoad is SubscriptionLoadState.Preview) TextButton(onClick = onDismissPreview) { Text("取消") }
            }
        )
    }

    deletingId?.let { id ->
        val subscription = store.subscriptions.firstOrNull { it.id == id }
        val name = subscription?.name ?: store.localGroups.firstOrNull { it.id == id }?.name.orEmpty()
        AlertDialog(onDismissRequest = { deletingId = null }, title = { Text("删除分组？") }, text = {
            Text(if (subscription != null) "删除“$name”后，会同时移除该订阅下的节点并停止自动更新。" else "删除“$name”后，其中的节点将移回本地节点。")
        }, confirmButton = { TextButton(onClick = {
            if (subscription != null) deleteSubscription(id) else deleteLocal(id)
            if (editingId == id) settingsOpen = false
            deletingId = null
        }) { Text("删除", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(onClick = { deletingId = null }) { Text("取消", color = MaterialTheme.colorScheme.primary) } })
    }
}

@Composable internal fun GroupSettingsPage(initial: GroupConfiguration, editing: Boolean, servers: List<ServerProfile>, onBack: () -> Unit, onConfirm: (GroupConfiguration) -> String?, onDelete: () -> Unit) {
    var id by rememberSaveable { mutableStateOf(initial.id) }
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var subscription by rememberSaveable { mutableStateOf(initial.subscription) }
    var url by rememberSaveable { mutableStateOf(initial.url) }
    var hours by rememberSaveable { mutableStateOf(initial.intervalHours.toString()) }
    var order by rememberSaveable { mutableStateOf(initial.sort.name) }
    var frontNodeId by rememberSaveable { mutableStateOf(initial.frontNodeId) }
    var landingNodeId by rememberSaveable { mutableStateOf(initial.landingNodeId) }
    var nodePicker by remember { mutableStateOf<String?>(null) }
    var chooser by remember { mutableStateOf<String?>(null) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var discard by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val config = GroupConfiguration(id, name.trim(), subscription, url.trim(), hours.toIntOrNull() ?: 0, GroupSort.valueOf(order), frontNodeId, landingNodeId)
    val dirty = config != initial
    val back = { if (dirty) discard = true else onBack() }
    BackHandler(onBack = back)
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = back) { Icon(Icons.Filled.Close, "取消", tint = MaterialTheme.colorScheme.primary) }
            Text("分组设置", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f).padding(start = 8.dp))
            TextButton(onClick = { saveError = config.validationError() ?: onConfirm(config) }) { Text("保存", color = MaterialTheme.colorScheme.primary) }
        }
        LazyColumn(contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)) {
            item { SettingGroup("基本设置") {
                GroupSettingText("分组名称", name, "请输入分组名称") { name = it; saveError = null }
                GroupSettingChoice("分组类型", if (subscription) "订阅" else "基本", Icons.Filled.List) { chooser = "分组类型" }
                GroupSettingChoice("排序", config.sort.label, Icons.Filled.Refresh) { chooser = "排序" }
            } }
            item { SettingGroup("链式代理") {
                GroupSettingChoice("前置节点", frontNodeId?.let { selected -> servers.firstOrNull { it.id == selected }?.name ?: "节点已失效，请重选" } ?: "禁用", Icons.Filled.Send) { nodePicker = "前置节点" }
                GroupSettingChoice("落地节点", landingNodeId?.let { selected -> servers.firstOrNull { it.id == selected }?.name ?: "节点已失效，请重选" } ?: "禁用", Icons.Filled.Home) { nodePicker = "落地节点" }
                Text("连接顺序：本机 → 前置节点 → 本分组节点 → 落地节点 → 目标。请选择其他分组的节点，保存后下次连接生效。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            } }
            if (subscription) item { SettingGroup("订阅设置") {
                GroupSettingText("订阅链接", url, "https://example.com/subscription") { url = it; saveError = null }
                TextButton(onClick = {
                    val text = readClipboard(context)
                    if (text.isNullOrBlank()) saveError = "剪贴板中没有链接" else { url = text.trim(); saveError = null }
                }, modifier = Modifier.padding(start = 48.dp)) { Text("从剪贴板粘贴", color = MaterialTheme.colorScheme.primary) }
                GroupSettingText("更新间隔（小时）", hours, "0–720，0 为不自动更新", numeric = true) { hours = it; saveError = null }
                Text("保存后自动更新订阅；间隔填 0 则不自动更新。更新失败时保留原有节点，可在分组页重试。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
                if (editing && !initial.subscription) Text("转为订阅后，现有节点归入该订阅；首次更新成功时会替换为链接中的节点。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            } }
            if (!subscription && initial.subscription) item { Text("转为基本分组后保留现有节点，并停止订阅自动更新。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)) }
            item { Text("排序设置仅作用于本分组，未测速的节点排在最后。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)) }
            saveError?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp)) } }
            if (editing) item {
                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("删除分组", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    nodePicker?.let { title ->
        GroupNodePicker(title, servers.filter { it.groupId != id && it.subscriptionId != id }, if (title == "前置节点") frontNodeId else landingNodeId, { nodePicker = null }) { selected ->
            if (title == "前置节点") frontNodeId = selected else landingNodeId = selected
            saveError = null
            nodePicker = null
        }
    }
    chooser?.let { title ->
        val options = if (title == "分组类型") listOf("基本", "订阅") else GroupSort.entries.map { it.label }
        AlertDialog(onDismissRequest = { chooser = null }, title = { Text(title) }, text = {
            Column { options.forEach { option ->
                TextButton(onClick = {
                    if (title == "分组类型") subscription = option == "订阅" else order = GroupSort.entries.first { it.label == option }.name
                    saveError = null; chooser = null
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(option, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    if (option == if (title == "分组类型") { if (subscription) "订阅" else "基本" } else config.sort.label) Icon(Icons.Filled.Check, "当前选项", tint = MaterialTheme.colorScheme.primary)
                }
            } }
        }, confirmButton = { TextButton(onClick = { chooser = null }) { Text("取消", color = MaterialTheme.colorScheme.primary) } })
    }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("放弃修改？") }, text = { Text("当前修改尚未保存。") }, confirmButton = { TextButton(onClick = onBack) { Text("放弃", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(onClick = { discard = false }) { Text("继续编辑", color = MaterialTheme.colorScheme.primary) } })
}

@Composable private fun GroupNodePicker(title: String, servers: List<ServerProfile>, selected: String?, onDismiss: () -> Unit, onSelect: (String?) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("设置$title") }, text = {
        Column {
            OutlinedTextField(query, { query = it }, placeholder = { Text("搜索节点", style = MaterialTheme.typography.bodySmall) }, singleLine = true, textStyle = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth())
            LazyColumn(Modifier.height(300.dp)) {
                item { TextButton(onClick = { onSelect(null) }, modifier = Modifier.fillMaxWidth()) { Text("禁用", color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f)); if (selected == null) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary) } }
                items(servers.filter { query.isBlank() || it.name.contains(query, true) || it.address.contains(query, true) }, key = { it.id }) { node ->
                    TextButton(onClick = { onSelect(node.id) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) { Text(node.name, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall); Text(node.protocol.label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall) }
                        if (selected == node.id) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                if (servers.isEmpty()) item { Text("暂无可选节点，请先导入其他分组的节点。", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp)) }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable private fun GroupSettingText(title: String, value: String, placeholder: String, numeric: Boolean = false, onValueChange: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (title == "订阅链接") Icons.Filled.Refresh else Icons.Filled.Settings, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(21.dp))
        Spacer(Modifier.size(14.dp))
        OutlinedTextField(value, onValueChange, label = { Text(title, style = MaterialTheme.typography.bodySmall) }, placeholder = { Text(placeholder, style = MaterialTheme.typography.bodySmall) }, textStyle = MaterialTheme.typography.bodySmall, singleLine = true, shape = RoundedCornerShape(12.dp),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = if (numeric) androidx.compose.ui.text.input.KeyboardType.Number else if (title == "订阅链接") androidx.compose.ui.text.input.KeyboardType.Uri else androidx.compose.ui.text.input.KeyboardType.Text),
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, focusedLabelColor = MaterialTheme.colorScheme.primary, cursorColor = MaterialTheme.colorScheme.primary), modifier = Modifier.weight(1f))
    }
}

@Composable private fun GroupSettingChoice(title: String, value: String, icon: ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(21.dp))
        Spacer(Modifier.size(14.dp))
        Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp))
        Text(" ›", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable private fun MoveNodeDialog(groups: List<com.iceflow.data.model.LocalGroup>, onDismiss: () -> Unit, onSelect: (String?) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("移动到分组") }, text = {
        Column { TextButton(onClick = { onSelect(null) }, modifier = Modifier.fillMaxWidth()) { Text("本地节点", color = MaterialTheme.colorScheme.primary) }; groups.forEach { group -> TextButton(onClick = { onSelect(group.id) }, modifier = Modifier.fillMaxWidth()) { Text(group.name, color = MaterialTheme.colorScheme.primary) } } }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun GroupCard(
    title: String,
    tags: List<String>,
    selected: Boolean,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    handle: Modifier,
    onRefresh: (() -> Unit)? = null,
    refreshing: Boolean = false,
    onClick: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box(
        Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(IceRadius.card))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        if (selected) Box(
            Modifier.fillMaxWidth().height(3.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topStart = IceRadius.card, topEnd = IceRadius.card))
        )
        Column(Modifier.padding(start = 16.dp, top = 14.dp, end = 8.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(handle, contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.List, "长按拖动$title 排序",
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.size(10.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (selected) {
                    Spacer(Modifier.size(6.dp))
                    Icon(Icons.Filled.Check, "已选", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
                if (onRefresh != null) {
                    IconButton(onClick = onRefresh, enabled = !refreshing, modifier = Modifier.size(38.dp)) {
                        if (refreshing) CircularProgressIndicator(Modifier.size(17.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                        else Icon(Icons.Filled.Refresh, "刷新$title", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
                    }
                }
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Filled.MoreVert, "更多操作", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(19.dp))
                    }
                    IceDropdownMenu(menu, { menu = false }) {
                        if (onEdit != null) IceMenuItem("编辑分组", Icons.Filled.Settings) { menu = false; onEdit() }
                        if (onDelete != null) IceMenuItem("删除分组", Icons.Filled.Delete, destructive = true) { menu = false; onDelete() }
                    }
                }
            }
            if (tags.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    tags.forEach { tag ->
                        Text(
                            tag,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable internal fun IceDropdownMenu(expanded: Boolean, onDismissRequest: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = Modifier.widthIn(min = 208.dp, max = 264.dp),
        shape = RoundedCornerShape(18.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
        content = content,
    )
}

@Composable internal fun IceMenuItem(title: String, icon: ImageVector, enabled: Boolean = true, destructive: Boolean = false, onClick: () -> Unit) {
    val tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    DropdownMenuItem(
        text = { Text(title, style = MaterialTheme.typography.bodyMedium, color = if (!enabled) MaterialTheme.colorScheme.onSurfaceVariant else if (destructive) tint else MaterialTheme.colorScheme.onSurface) },
        leadingIcon = {
            Box(Modifier.size(30.dp).background(tint.copy(alpha = if (enabled) .08f else .04f), RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = if (enabled) tint else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        },
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 16.dp),
        onClick = onClick,
    )
}

private fun dateText(value: Long): String = if (value == 0L) "尚未更新" else SimpleDateFormat("MM月dd日 HH:mm", Locale.getDefault()).format(Date(value))

@Composable internal fun SettingsPage(settings: AppSettings, update: ((AppSettings) -> AppSettings) -> Unit, version: String, viewLogs: () -> Unit, openApps: () -> Unit = {}) {
    var urlOpen by rememberSaveable { mutableStateOf(false) }
    var dnsOpen by rememberSaveable { mutableStateOf(false) }
    var themeOpen by rememberSaveable { mutableStateOf(false) }
    var url by rememberSaveable(settings.testUrl) { mutableStateOf(settings.testUrl) }
    var dns by rememberSaveable(settings.dnsServers) { mutableStateOf(settings.dnsServers.joinToString("\n")) }
    var error by remember { mutableStateOf<String?>(null) }
    LazyColumn(contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)) {
        item { SettingGroup("通用") { SettingAction("主题", Icons.Filled.Settings, { themeOpen = true }); SettingRow("语言", "简体中文", Icons.Filled.Info) } }
        item { SettingGroup("代理") {
            SettingAction("绕过应用", Icons.Filled.Settings, openApps)
            SettingSwitch("分应用代理", settings.proxyAppsOnly, Icons.Filled.Settings) { update { it.copy(proxyAppsOnly = !it.proxyAppsOnly) } }
            SettingSwitch("IPv6 路由", settings.ipv6Enabled, Icons.Filled.Settings) { update { it.copy(ipv6Enabled = !it.ipv6Enabled) } }
            SettingAction("DNS 设置", Icons.Filled.Info, { dns = settings.dnsServers.joinToString("\n"); dnsOpen = true })
            SettingSwitch("启用 SOCKS5 路由", settings.socksEnabled, Icons.Filled.Settings) { update { it.copy(socksEnabled = !it.socksEnabled) } }
            SettingSwitch("SOCKS5 UDP", settings.socksUdp, Icons.Filled.Settings) { update { it.copy(socksUdp = !it.socksUdp) } }
            SettingSwitch("DNS 路由", settings.dnsProxyEnabled, Icons.Filled.Settings) { update { it.copy(dnsProxyEnabled = !it.dnsProxyEnabled) } }
            Column(Modifier.fillMaxWidth().clickable { url = settings.testUrl; error = null; urlOpen = true }) {
                SettingRow("测试 URL", "修改", Icons.Filled.Send)
                Text(settings.testUrl, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 51.dp, end = 16.dp, bottom = 12.dp))
            }
        } }
        item { SettingGroup("高级") { SettingAction("查看运行日志", Icons.Filled.List, viewLogs) } }
        item { SettingGroup("关于") { SettingRow("版本", version, Icons.Filled.Info); SettingRow("开源地址", "GitHub", Icons.Filled.Info) } }
    }
    if (urlOpen) AlertDialog(onDismissRequest = { urlOpen = false }, title = { Text("测试 URL") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        EditorInput("HTTP / HTTPS 链接", url, { url = it })
        Text("用于单节点、批量和启动栏测速，建议选择返回 204 的轻量页面。", style = MaterialTheme.typography.bodySmall)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    } }, confirmButton = { TextButton({ runCatching { com.iceflow.data.model.RoutingRules.validateTestUrl(url) }.onSuccess { valid -> update { it.copy(testUrl = valid) }; urlOpen = false }.onFailure { error = it.message } }) { Text("保存") } }, dismissButton = { TextButton({ urlOpen = false }) { Text("取消") } })
    if (dnsOpen) AlertDialog(onDismissRequest = { dnsOpen = false }, title = { Text("DNS 设置") }, text = { EditorInput("每行一个 DNS 地址", dns, { dns = it }, multiline = true) }, confirmButton = { TextButton({ val values = dns.lines().map { it.trim() }.filter { it.isNotEmpty() }; if (values.isNotEmpty()) { update { it.copy(dnsServers = values, remoteDns = values.first()) }; dnsOpen = false } }) { Text("保存") } }, dismissButton = { TextButton({ dnsOpen = false }) { Text("取消") } })
    if (themeOpen) ThemeSelectionDialog(
        currentTheme = settings.theme,
        onDismiss = { themeOpen = false },
        onThemeSelected = { theme ->
            update { it.copy(theme = theme) }
            themeOpen = false
        }
    )
}

@Composable
private fun ThemeSelectionDialog(
    currentTheme: com.iceflow.data.model.AppTheme,
    onDismiss: () -> Unit,
    onThemeSelected: (com.iceflow.data.model.AppTheme) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { 
            Text(
                "选择主题",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            ) 
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                com.iceflow.data.model.AppTheme.values().forEach { theme ->
                    ThemePreviewCard(
                        theme = theme,
                        isSelected = theme == currentTheme,
                        onClick = { onThemeSelected(theme) }
                    )
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun ThemePreviewCard(
    theme: com.iceflow.data.model.AppTheme,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val (primaryColor, secondaryColor) = com.iceflow.ui.theme.themePreviewColors(theme)
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(IceRadius.card))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 配色预览
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(primaryColor)
            )
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(secondaryColor)
            )
        }
        
        // 主题名称
        Text(
            text = themeLabel(theme),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        
        // 选中标记
        if (isSelected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "已选中",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

private fun themeLabel(theme: com.iceflow.data.model.AppTheme) = when (theme) {
    com.iceflow.data.model.AppTheme.LIGHT_BLUE -> "浅蓝"
    com.iceflow.data.model.AppTheme.MINT_GREEN -> "薄荷绿"
    com.iceflow.data.model.AppTheme.SAKURA_PINK -> "樱花粉"
    com.iceflow.data.model.AppTheme.PEACH_ORANGE -> "蜜桃橙"
    com.iceflow.data.model.AppTheme.OBSIDIAN_BLACK -> "曜石黑"
}

@Composable private fun AppSelectionPage(selected: List<String>, update: (List<String>) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val packageManager = context.packageManager
    val apps = remember { packageManager.getInstalledApplications(0).sortedBy { it.loadLabel(packageManager).toString() } }
    Column(modifier.statusBarsPadding().fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().height(68.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = MaterialTheme.colorScheme.primary) }
            Text("选择绕过应用", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        }
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            items(apps, key = { it.packageName }) { app ->
                val checked = app.packageName in selected
                Row(Modifier.fillMaxWidth().clickable { update(if (checked) selected - app.packageName else selected + app.packageName) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(app.loadLabel(packageManager).toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface); Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Checkbox(checked, null)
                }
            }
        }
    }
}

@Composable private fun SettingGroup(title: String, content: @Composable () -> Unit) { Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) { Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)); Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))) { content() } } }
@Composable private fun SettingRow(title: String, value: String, icon: ImageVector) { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(21.dp)); Spacer(Modifier.size(14.dp)); Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f)); Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) } }
@Composable private fun SettingAction(title: String, icon: ImageVector, onClick: () -> Unit) { Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(21.dp)); Spacer(Modifier.size(14.dp)); Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f)); Text("查看", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) } }
@Composable private fun SettingSwitch(title: String, checked: Boolean, icon: ImageVector, onClick: () -> Unit) { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(21.dp)); Spacer(Modifier.size(14.dp)); Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f)); Switch(checked, { onClick() }) } }

@Composable private fun AddSubscriptionDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) { var url by rememberSaveable { mutableStateOf("") }; AlertDialog(onDismissRequest = onDismiss, title = { Text("添加订阅") }, text = { OutlinedTextField(url, { url = it }, label = { Text("订阅地址") }, singleLine = true) }, confirmButton = { TextButton(onClick = { onConfirm(url.trim()) }, enabled = url.startsWith("http")) { Text("添加") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }) }

private fun readClipboard(context: Context): String? { val cm = context.getSystemService(ClipboardManager::class.java) ?: return null; return cm.primaryClip?.let { if (it.itemCount > 0) it.getItemAt(0).coerceToText(context).toString() else null } }

private fun shareNode(context: Context, server: ServerProfile) {
    shareText(context, "分享节点", ShareLinkParser.encodeV2rayN(server))
}

private fun shareText(context: Context, title: String, text: String) {
    if (text.isBlank()) return
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, title))
}
