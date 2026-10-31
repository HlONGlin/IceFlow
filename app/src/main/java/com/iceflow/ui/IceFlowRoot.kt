package com.iceflow.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iceflow.data.model.AppSettings
import com.iceflow.data.model.LocalGroup
import com.iceflow.data.model.NodeGroup
import com.iceflow.data.model.GroupConfiguration
import com.iceflow.data.model.GroupSort
import com.iceflow.data.model.ServerProfile
import com.iceflow.data.model.ServerStore
import com.iceflow.data.model.Protocol
import com.iceflow.data.model.ProxyComposition
import com.iceflow.data.model.ProxyCompositionKind
import com.iceflow.data.model.LoadBalanceStrategy
import com.iceflow.data.ShareLinkParser
import com.iceflow.data.BoundedTextReader
import com.iceflow.data.UpdateInfo
import com.iceflow.service.AppLog
import com.iceflow.service.TrafficSnapshot
import com.iceflow.service.VpnController
import com.iceflow.service.VpnState
import com.iceflow.service.VpnErrorAction
import com.iceflow.service.VpnErrorPresentation
import com.iceflow.service.VpnEntryPoint
import com.iceflow.core.XrayBridge
import com.iceflow.ui.component.ShareNodeDialog
import com.iceflow.ui.component.latencyColor
import com.iceflow.ui.component.latencyText
import com.iceflow.ui.screen.AppSelectorScreen
import com.iceflow.ui.screen.SettingsScreen
import com.iceflow.ui.theme.IceRadius
import com.iceflow.data.model.AppLanguage
import com.iceflow.i18n.T
import com.iceflow.i18n.stringsOf
import com.iceflow.util.formatSpeed
import com.iceflow.util.formatDuration
import com.iceflow.util.formatBytes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Tab(val labelKey: String, val icon: ImageVector) {
    HOME("tab.home", Icons.Filled.Home), SUBS("tab.groups", Icons.Filled.Refresh), ROUTES("tab.routes", Icons.Filled.List), SETTINGS("tab.settings", Icons.Filled.Settings)
}

/** 未分组节点在工作台中的虚拟分组，不参与分组管理与排序持久化。 */
private val UngroupedGroup: NodeGroup @Composable get() = NodeGroup("builtin:ungrouped", null, T("groups.ungrouped"))

@Composable
fun IceFlowRoot(vm: MainViewModel, onToggleVpn: () -> Unit, openLogsRequested: Boolean = false, onLogsOpened: () -> Unit = {}) {
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
    val updateState by vm.updateState.collectAsStateWithLifecycle()
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
    var manualAddOpen by rememberSaveable { mutableStateOf(false) }
    var manualAddPage by rememberSaveable { mutableStateOf(ManualAddPage.CHAIN_EDITOR) }
    var newNodeProtocol by rememberSaveable { mutableStateOf(Protocol.VLESS) }
    var pendingGroupSettingsId by remember { mutableStateOf<String?>(null) }
    var groupId by rememberSaveable { mutableStateOf<String?>(store.selectedGroupId) }
    LaunchedEffect(openLogsRequested) {
        if (openLogsRequested) {
            tab = Tab.HOME.ordinal
            vm.cleanOldLogs()
            logsOpen = true
            onLogsOpened()
        }
    }
    LaunchedEffect(store.selectedGroupId) { if (groupId != store.selectedGroupId) groupId = store.selectedGroupId }
    var toast by remember { mutableStateOf<String?>(null) }
    var previousState by remember { mutableStateOf<VpnState>(VpnState.Idle) }
    val connectedToast = T("home.toast.connected")
    val disconnectedToast = T("home.toast.disconnected")
    LaunchedEffect(state) {
        if (state is VpnState.Connected && previousState !is VpnState.Connected) toast = connectedToast
        if (state is VpnState.Idle && previousState is VpnState.Connected) toast = disconnectedToast
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
            AnimatedContent(
                targetState = when { nodeEditorOpen -> "node"; manualAddOpen -> "composition"; backupOpen -> "backup"; logsOpen -> "logs"; appsOpen -> "apps"; else -> "main" },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                transitionSpec = {
                    val direction = if (targetState == "main") -1 else 1
                    fadeIn(tween(240)) + slideInHorizontally(tween(240)) { direction * it / 8 } togetherWith
                        fadeOut(tween(160)) + slideOutHorizontally(tween(240)) { -direction * it / 8 }
                }, label = "workbench-page",
            ) { screen ->
            Column(Modifier.fillMaxSize()) {
            if (screen == "node") {
                val initialJson = rememberSaveable(nodeEditorId) {
                    val node = store.servers.firstOrNull { it.id == nodeEditorId } ?: com.iceflow.data.NodeEditing.blank(newNodeProtocol).copy(groupId = groupId?.takeIf { id -> store.localGroups.any { it.id == id } })
                    com.iceflow.data.NodeEditing.json.encodeToString(ServerProfile.serializer(), node)
                }
                val initial = remember(initialJson) { com.iceflow.data.NodeEditing.json.decodeFromString(ServerProfile.serializer(), initialJson) }
                if (initial.composition != null) ManualAddScreen(initial, store.servers, com.iceflow.core.CoreVariantBridge.variantName, { nodeEditorOpen = false }, vm::saveServer)
                else NodeEditorPage(initial, nodeEditorId == null, { nodeEditorOpen = false }, vm::saveServer)
            } else if (screen == "composition") {
                val initialJson = rememberSaveable(manualAddPage) {
                    val kind = if (manualAddPage == ManualAddPage.CHAIN_EDITOR) ProxyCompositionKind.CHAIN else ProxyCompositionKind.BALANCE
                    val initial = com.iceflow.data.NodeEditing.blank(Protocol.SOCKS).copy(
                        groupId = groupId?.takeIf { id -> store.localGroups.any { it.id == id } },
                        composition = ProxyComposition(kind = kind, strategy = if (com.iceflow.core.CoreVariantBridge.variantName == "dual") LoadBalanceStrategy.RANDOM else LoadBalanceStrategy.ROUND_ROBIN),
                    )
                    com.iceflow.data.NodeEditing.json.encodeToString(ServerProfile.serializer(), initial)
                }
                val initial = remember(initialJson) { com.iceflow.data.NodeEditing.json.decodeFromString(ServerProfile.serializer(), initialJson) }
                ManualAddScreen(initial, store.servers, com.iceflow.core.CoreVariantBridge.variantName, { manualAddOpen = false }, vm::saveServer)
            } else if (screen == "backup") {
                BackupPage(vm::exportBackup, vm::restoreBackup, { backupOpen = false })
            } else if (screen == "logs") {
                RuntimeLogPage(
                    readLogs = vm::readLogs,
                    onBack = { logsOpen = false },
                    logRetentionDays = settings.logRetentionDays,
                    modifier = Modifier.weight(1f)
                )
            } else if (screen == "apps") {
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
                        Tab.HOME -> WorkbenchPage(state, traffic, store, groupId, publicIp, settings.showPublicIp, testing, settings.showTrafficStats, onToggleVpn, vm::select, vm::testAll, vm::testOne, nodeTest, vm::delete, vm::updateServer, vm::moveServerToGroup, { shareNodeId = it.id }, { vm.importText(readClipboard(context), groupId) }, { text -> vm.importText(text, groupId) }, vm::refreshSubscriptions, vm::setGroupSort, vm::sortGroupByLatency, vm::dedupeServers, toast, connectionTest, vm::testRunningConnection, { selected -> groupId = selected; vm.selectGroup(selected) }, { protocol, page -> if (protocol != null) { newNodeProtocol = protocol; nodeEditorId = null; nodeEditorOpen = true } else { manualAddPage = requireNotNull(page); manualAddOpen = true } }, { server -> nodeEditorId = server.id; nodeEditorOpen = true }, bulkTest, vm::dismissBulkTest, vm::reorderServers, settings.showNodeLatency) { action ->
                            when (action) {
                                VpnErrorAction.REAUTHORIZE, VpnErrorAction.CONNECT -> onToggleVpn()
                                VpnErrorAction.CHECK_NODE -> store.selected?.let { nodeEditorId = it.id; nodeEditorOpen = true }
                                    ?: run { tab = Tab.SUBS.ordinal }
                                VpnErrorAction.OPEN_LOGS -> { vm.cleanOldLogs(); logsOpen = true }
                            }
                        }
                        Tab.SUBS -> GroupsPage(store, groupId, { selected -> groupId = selected; vm.selectGroup(selected) }, refreshing, vm::refreshSubscriptions, vm::refreshSubscription, refreshingGroups, vm::saveGroup, vm::deleteLocalGroup, vm::deleteSubscription, { groupSettingsOpen = it }, vm::reorderGroups, vm::createGroupFromUngrouped, vm::deleteUngroupedServers, subscriptionLoad, vm::previewSubscription, vm::commitSubscriptionPreview, vm::dismissSubscriptionPreview, pendingGroupSettingsId, { pendingGroupSettingsId = null })
                        Tab.SETTINGS -> SettingsScreen(
                            settings = settings,
                            xrayVersion = XrayBridge.version(),
                            coreVersion = if (com.iceflow.core.RustBridge.isAvailable) "已集成" else null,
                            coreVariant = com.iceflow.core.CoreVariantBridge.variantName,
                            mihomoVersion = com.iceflow.core.CoreVariantBridge.coreVersionLabel,
                            appVersion = vm.appVersion,
                            onUpdate = vm::updateSettings,
                            onOpenLogs = { vm.cleanOldLogs(); logsOpen = true },
                            onOpenBackup = { backupOpen = true },
                            onOpenAppSelector = { appsOpen = true },
                            onCheckUpdate = { vm.checkForUpdate(manual = true) },
                            onDetailOpenChange = { settingsDetailOpen = it },
                        )
                        Tab.ROUTES -> RoutingPage(settings, vm::updateSettings, vm.routingResources, { groupSettingsOpen = it })
                    }
                }
            }
            }
            }
            }
            if (!groupSettingsOpen && !settingsDetailOpen && !logsOpen && !backupOpen && !nodeEditorOpen && !manualAddOpen && !appsOpen) Row(
                Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()
                    .padding(top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Tab.entries.forEachIndexed { index, item ->
                    val moduleInteraction = remember { MutableInteractionSource() }
                    Column(
                        Modifier.weight(1f)
                            .clickable(
                                interactionSource = moduleInteraction,
                                indication = null,
                                onClick = { tab = index },
                            )
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            item.icon,
                            T(item.labelKey),
                            tint = if (tab == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(23.dp)
                        )
                        Text(
                            T(item.labelKey),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (tab == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
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

        UpdateDialog(
            state = updateState,
            onDismiss = vm::dismissUpdate,
            onRetry = { token -> vm.setUpdateToken(token); vm.checkForUpdate(manual = true) },
            onDontRemind = { vm.setAutoRemind(false) },
            onDownload = { info ->
                val target = info.downloadUrl?.takeIf { it.isNotBlank() } ?: info.pageUrl
                if (target.isNotBlank()) runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
                }
                vm.dismissUpdate()
            },
        )

    LaunchedEffect(state) {
        if (state is VpnState.Connected) {
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

@Composable private fun WorkbenchPage(state: VpnState, traffic: TrafficSnapshot, store: ServerStore, groupId: String?, publicIp: PublicIpState, showPublicIp: Boolean, testing: Boolean, showTrafficStats: Boolean, onToggle: () -> Unit, onSelect: (String) -> Unit, onTest: () -> Unit, onTestOne: (ServerProfile) -> Unit, nodeTest: NodeTestState, onDelete: (String) -> Unit, onUpdate: (ServerProfile) -> Unit, onMove: (String, String?) -> Unit, onShare: (ServerProfile) -> Unit, onImportClipboard: () -> Unit, onImportText: (String) -> Unit, onRefreshSubs: () -> Unit, onSetGroupSort: (String?, GroupSort) -> Unit, onSortGroupByLatency: (String?) -> Unit, onDedupe: () -> Unit, toast: String?, connectionTest: ConnectionTestState, onTestConnection: () -> Unit, onSelectGroup: (String?) -> Unit, onAddNode: (Protocol?, ManualAddPage?) -> Unit, onEditNode: (ServerProfile) -> Unit, bulkTest: BulkTestState, dismissBulkTest: () -> Unit, onReorderNodes: (String?, List<String>) -> Unit, showNodeLatency: Boolean = false, onErrorAction: (VpnErrorAction) -> Unit) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    val panelVisibility = remember { MutableTransitionState(false) }
    var lastRunningState by remember { mutableStateOf<VpnState>(VpnState.Connecting) }
    val context = LocalContext.current
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var addMenu by remember { mutableStateOf(false) }
    var manualMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var sortingKey by remember { mutableStateOf<String?>(null) }
    if (sortingKey != null) BackHandler { sortingKey = null }
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result -> result.contents?.let(onImportText) }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            runCatching {
                val input = context.contentResolver.openInputStream(uri) ?: error("无法读取所选文件")
                input.use { BoundedTextReader.readUtf8(it) }
            }.onSuccess(onImportText).onFailure {
                android.widget.Toast.makeText(context, it.message ?: "读取导入文件失败", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
    LaunchedEffect(state) { while (state is VpnState.Connected) { now = System.currentTimeMillis(); kotlinx.coroutines.delay(1000) } }
    val selected = store.selected
    val running = state is VpnState.Connected || state is VpnState.Connecting || state is VpnState.WaitingNetwork
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
                Text(if (searchOpen) T("home.search") else "IceFlow", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                IconButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) query = "" }) { Icon(if (searchOpen) Icons.Filled.Close else Icons.Filled.Search, T("home.searchHint"), tint = MaterialTheme.colorScheme.primary) }
                Box {
                    IconButton(onClick = { manualMenu = false; addMenu = true }) { Icon(Icons.Filled.Add, T("home.import.menu"), tint = MaterialTheme.colorScheme.primary) }
                    IceDropdownMenu(expanded = addMenu, onDismissRequest = { if (manualMenu) manualMenu = false else addMenu = false }) {
                        AnimatedContent(targetState = manualMenu, transitionSpec = {
                            val direction = if (targetState) 1 else -1
                            fadeIn(tween(220)) + slideInHorizontally(tween(220)) { direction * it / 4 } togetherWith
                                fadeOut(tween(140)) + slideOutHorizontally(tween(220)) { -direction * it / 4 }
                        }, label = "add-server-menu") { manual ->
                            Column(Modifier.width(240.dp).heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                                if (manual) {
                                    IceMenuItem(T("home.import.manual"), Icons.AutoMirrored.Filled.ArrowBack) { manualMenu = false }
                                    HorizontalDivider(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                                    IceMenuItem(T("groups.chain"), Icons.Filled.Link) { addMenu = false; onAddNode(null, ManualAddPage.CHAIN_EDITOR) }
                                    IceMenuItem(T("groups.loadBalance"), Icons.Filled.Balance) { addMenu = false; onAddNode(null, ManualAddPage.BALANCE_EDITOR) }
                                    HorizontalDivider(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                                    Protocol.entries.forEach { protocol ->
                                        IceMenuItem(protocol.label, Icons.Filled.Add) { addMenu = false; onAddNode(protocol, null) }
                                    }
                                } else {
                                    IceMenuItem(T("home.import.clipboard"), Icons.Filled.List) { addMenu = false; onImportClipboard() }
                                    IceMenuItem(T("home.import.manual"), Icons.Filled.Add, submenu = true) { manualMenu = true }
                                    IceMenuItem(T("home.import.file"), Icons.Filled.Home) { addMenu = false; fileLauncher.launch("*/*") }
                                    IceMenuItem(T("home.import.qr"), Icons.Filled.Search) { addMenu = false; scanLauncher.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setBeepEnabled(false)) }
                                }
                            }
                        }
                    }
                }
                Box {
                    IconButton(onClick = { moreMenu = true }) { Icon(Icons.Filled.MoreVert, T("common.more"), tint = MaterialTheme.colorScheme.primary) }
                    IceDropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                        IceMenuItem(T("sub.refreshAll"), Icons.Filled.Refresh) { moreMenu = false; onRefreshSubs() }
                        IceMenuItem(if (testing) T("node.test.testing") else T("home.testAll"), Icons.Filled.Send, enabled = !testing) { moreMenu = false; onTest() }
                        HorizontalDivider(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                        IceMenuItem(T("home.sort"), Icons.Filled.List) { moreMenu = false; sortMenu = true }
                        IceMenuItem(T("home.dedupe"), Icons.Filled.Check) { moreMenu = false; onDedupe() }
                    }
                    IceDropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        GroupSort.entries.forEach { sort ->
                            IceMenuItem(T(sort.stringKey), Icons.Filled.List, enabled = !testing) {
                                sortMenu = false
                                if (sort == GroupSort.LATENCY) onSortGroupByLatency(groupId) else onSetGroupSort(groupId, sort)
                            }
                        }
                    }
                }
            }
            if (showPublicIp && state is VpnState.Connected) {
                val ipText = when (val result = publicIp) {
                    PublicIpState.Idle -> T("publicIp.unavailable")
                    PublicIpState.Loading -> T("publicIp.loading")
                    is PublicIpState.Success -> result.address
                    PublicIpState.Failed -> T("publicIp.failed")
                }
                Text(
                    T("publicIp.label") + ": " + ipText,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            (state as? VpnState.Error)?.let { error ->
                VpnErrorBanner(error.presentation, { onErrorAction(error.presentation.action) }, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            if (searchOpen) OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), placeholder = { Text(T("home.searchPlaceholder")) }, singleLine = true, shape = RoundedCornerShape(12.dp))
            val visibleGroups = if (visibleNodes(null, "").isEmpty()) store.orderedGroups else listOf(UngroupedGroup) + store.orderedGroups
            if (visibleGroups.isEmpty()) EmptyStateMessage(T("home.empty"), Modifier.weight(1f))
            else GroupPager(visibleGroups, groupId?.takeIf { id -> visibleGroups.any { it.key == id } }, onSelectGroup, Modifier.weight(1f)) { group ->
                val sorting = sortingKey == group.key
                val nodes = if (sorting) visibleNodes(group.id, "") else visibleNodes(group.id)
                if (sorting) Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(T("home.dragHint"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        TextButton(onClick = { sortingKey = null }) { Text(T("common.done"), color = MaterialTheme.colorScheme.primary) }
                    }
                    ReorderableNodeList(nodes, { onReorderNodes(group.id, it) }, Modifier.weight(1f), PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = if (running) 112.dp else 80.dp)) { server, handle ->
                        NodeRow(server, store.localGroups, server.id == store.selectedId, {}, {}, {}, {}, {}, {}, dragHandle = handle, showNodeLatency = showNodeLatency)
                    }
                } else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = if (running) 112.dp else 80.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    if (nodes.isEmpty()) item { Text(if (query.isBlank()) T("home.groupEmpty") else T("home.noMatch"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(24.dp)) }
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
        Row(Modifier.align(Alignment.BottomCenter).offset(y = (-28).dp), verticalAlignment = Alignment.CenterVertically) {
            ConnectOrb(state, onToggle, 54.dp) {
                VpnController.dispatchEntryAction(context, VpnEntryPoint.HOME, secondary = true)
            }
            if (state == VpnState.Stopping) Text(T("state.stopping"), modifier = Modifier.padding(start = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        }
        if (toast != null) Text(toast, color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f), CircleShape).padding(horizontal = 18.dp, vertical = 9.dp))
    }
}

@Composable
private fun VpnErrorBanner(presentation: VpnErrorPresentation, onAction: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().widthIn(max = 600.dp)
            .shadow(5.dp, RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(presentation.message, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(presentation.nextAction, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp)) {
            Text(presentation.actionLabel, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@Composable internal fun ConnectOrb(state: VpnState, onClick: () -> Unit, size: Dp = 54.dp, onStopWaiting: () -> Unit = {}) {
    val connected = state is VpnState.Connected
    val waitingNetwork = state == VpnState.WaitingNetwork
    // 等待网络恢复时不是"正在连接"，用不同的图标，避免无限转圈造成的误解
    // 断开连接时不显示loading，因为断开应该是即时的
    val busy = state == VpnState.Connecting || waitingNetwork
    val stopping = state == VpnState.Stopping
    val stoppingDescription = if (stopping) T("state.stopping") else ""
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
            .clickable(enabled = !stopping, interactionSource = interaction, indication = androidx.compose.material3.ripple(), onClick = onClick)
            .semantics { if (stopping) contentDescription = stoppingDescription }, contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = when {
                    waitingNetwork -> 2   // 等待网络
                    stopping -> 3        // 断开中
                    busy -> 1            // 连接中
                    else -> 0            // 空闲
                },
                // 连接成功后立即隐藏转圈指示器：退出动画压到 120ms，
                // 否则后端已就绪但转圈仍会残留数百毫秒，用户观感上像"连接很慢"。
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
                label = "connectStatus",
            ) { loading ->
                when (loading) {
                    2 -> Icon(Icons.Filled.WifiOff, T("state.waitingNetwork"), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(size * .40f))
                    3 -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(size * .42f), strokeWidth = 2.5.dp)
                    1 -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(size * .42f), strokeWidth = 2.5.dp)
                    else -> Icon(Icons.Filled.Send, if (connected) T("connect.disconnect") else T("connect.title"), tint = iconColor, modifier = Modifier.size(size * .44f).graphicsLayer { rotationZ = if (connected) -12f else 0f })
                }
            }
        }
    }
    if (waitingNetwork) {
        TextButton(onClick = onStopWaiting) { Text(T("notification_disconnect")) }
    }
}

@Composable internal fun ConnectionPanel(state: VpnState, traffic: TrafficSnapshot, now: Long, test: ConnectionTestState, onTest: () -> Unit, modifier: Modifier = Modifier) {
    val connected = state is VpnState.Connected
    // 断开时不显示busy状态，让UI立即响应
    val busy = state == VpnState.Connecting || state == VpnState.WaitingNetwork
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
                        VpnState.WaitingNetwork -> T("state.waitingNetwork") + "…"
                        else -> T("state.connecting") + "…"
                    }
                    test.testing -> T("connect.testing")
                    test.latencyMs == null -> T("node.test.tap")
                    test.latencyMs < 0 -> T("latency.timeout") + " · " + T("common.retry")
                    else -> "${test.latencyMs} ms"
                }, color = textColor, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 1)
            }
            Text(if (connected) formatDuration(now - (state as VpnState.Connected).since) else T("common.pleaseWait"), color = secondaryColor, style = MaterialTheme.typography.labelSmall)
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
            Box(dragHandle, contentAlignment = Alignment.Center) { Icon(Icons.Filled.Menu, T("common.drag"), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) }
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
                Text(ServerTypeLabel(server), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                if (showTrafficStats) {
                    Text("↑ ${formatBytes(server.uploadBytes)}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    Text("↓ ${formatBytes(server.downloadBytes)}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        if (dragHandle == null) {
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(40.dp)) { Icon(Icons.Filled.MoreVert, T("node.actions"), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(19.dp)) }
                IceDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    IceMenuItem(T("node.test.one"), Icons.Filled.Info) { menu = false; onTest() }
                    IceMenuItem(T("common.edit"), Icons.Filled.Settings) { menu = false; if (onEdit != null) onEdit() else edit = true }
                    if (server.composition == null) IceMenuItem(T("node.share.title"), Icons.Filled.Share) { menu = false; onShare() }
                    if (onSort != null) IceMenuItem(T("node.sort"), Icons.Filled.List) { menu = false; onSort() }
                    HorizontalDivider(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                    IceMenuItem(T("common.delete"), Icons.Filled.Delete, destructive = true) { menu = false; onDelete() }
                }
            }
        }
    }
    if (edit) AlertDialog(onDismissRequest = { edit = false }, title = { Text(T("node.editTitle")) }, text = { OutlinedTextField(name, { name = it }, label = { Text(T("node.name")) }, singleLine = true) }, confirmButton = { TextButton(onClick = { edit = false; onRename(name.trim()) }, enabled = name.isNotBlank()) { Text(T("common.save")) } }, dismissButton = { TextButton(onClick = { edit = false }) { Text(T("common.cancel")) } })
}

@Composable private fun GroupsPage(store: ServerStore, selectedId: String?, onSelect: (String?) -> Unit, refreshing: Boolean, refresh: () -> Unit, refreshGroup: (String) -> Unit, refreshingGroups: Set<String>, save: (GroupConfiguration) -> String?, deleteLocal: (String) -> Unit, deleteSubscription: (String) -> Unit, onEditingChange: (Boolean) -> Unit, onReorder: (List<String>) -> Unit, createGroupFromUngrouped: (String) -> Unit = {}, deleteUngrouped: () -> Unit = {}, subscriptionLoad: SubscriptionLoadState = SubscriptionLoadState.Idle, onPreviewSubscription: (url: String, name: String) -> Unit = { _, _ -> }, onCommitPreview: (groupId: String) -> Unit = {}, onDismissPreview: () -> Unit = {}, requestedSettingsId: String? = null, onRequestedSettingsHandled: () -> Unit = {}) {
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingConfigId by remember { mutableStateOf<String?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    var ungroupedEditOpen by rememberSaveable { mutableStateOf(false) }
    var ungroupedDeleteOpen by rememberSaveable { mutableStateOf(false) }
    val ungroupedDefaultName = T("groups.ungrouped.defaultName")
    var ungroupedName by rememberSaveable { mutableStateOf(ungroupedDefaultName) }
    DisposableEffect(settingsOpen) {
        onEditingChange(settingsOpen)
        onDispose { onEditingChange(false) }
    }
    fun edit(id: String?) { editingId = id; settingsOpen = true }
    LaunchedEffect(requestedSettingsId) {
        requestedSettingsId?.let { id -> edit(id); onRequestedSettingsHandled() }
    }
    if (settingsOpen) {
        val local = store.localGroups.firstOrNull { it.id == editingId }
        val sub = store.subscriptions.firstOrNull { it.id == editingId }
        val initial = remember(editingId) {
            when {
                sub != null -> GroupConfiguration(sub.id, sub.name, true, sub.url, sub.updateIntervalHours, sub.sort, sub.frontNodeId, sub.landingNodeId)
                local != null -> GroupConfiguration(id = local.id, name = local.name, sort = local.sort, frontNodeId = local.frontNodeId, landingNodeId = local.landingNodeId, balanceStrategy = local.balanceStrategy, balanceNodeIds = local.balanceNodeIds, balanceTestUrl = local.balanceTestUrl, balanceIntervalSeconds = local.balanceIntervalSeconds)
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
                    else Icon(Icons.Filled.Refresh, T("sub.refreshAll"), tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { edit(null) }) { Icon(Icons.Filled.Add, T("groups.newGroup"), tint = MaterialTheme.colorScheme.primary) }
            }
            if (store.orderedGroups.isEmpty() && store.servers.none { it.subscriptionId == null && it.groupId == null }) {
                EmptyStateMessage(T("groups.empty"), Modifier.weight(1f))
            } else {
                Column(Modifier.weight(1f)) {
                    val ungroupedCount = store.servers.count { it.subscriptionId == null && it.groupId == null }
                    if (ungroupedCount > 0) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            GroupCard(
                                title = UngroupedGroup.name,
                                tags = listOf(String.format(T("groups.nodes"), ungroupedCount), T("groups.ungroupedTags")),
                                selected = selectedId == null,
                                onEdit = { ungroupedName = ungroupedDefaultName; ungroupedEditOpen = true },
                                onDelete = { ungroupedDeleteOpen = true },
                                handle = Modifier,
                                showHandle = false,
                            ) { onSelect(null) }
                        }
                    }
                    if (store.orderedGroups.isNotEmpty()) {
                        ReorderableGroupList(store.orderedGroups, onReorder, Modifier.weight(1f)) { group, handle ->
                            val sub = store.subscriptions.firstOrNull { it.id == group.id }
                            val tags = when {
                                sub != null -> buildList {
                                    add(String.format(T("groups.nodes"), store.servers.count { it.subscriptionId == sub.id }))
                                    add(dateText(sub.updatedAt))
                                    add(if (sub.updateIntervalHours <= 0) T("groups.noAutoUpdate") else String.format(T("groups.everyHours"), sub.updateIntervalHours))
                                }
                                group.id != null -> listOf(String.format(T("groups.nodes"), store.servers.count { it.groupId == group.id }), T("groups.local"))
                                else -> listOf(String.format(T("groups.nodes"), store.servers.count { it.subscriptionId == null && it.groupId == null }), T("groups.ungrouped"))
                            }
                            GroupCard(group.name, tags, false, if (group.id == null) null else ({ edit(group.id) }), if (group.id == null) null else ({ deletingId = group.id }), handle, onRefresh = if (sub != null) ({ refreshGroup(group.id!!) }) else null, refreshing = group.id in refreshingGroups) { onSelect(group.id) }
                        }
                    }
                }
            }
        }
    }

    // 订阅预览对话框
    val currentLoad = subscriptionLoad
    if (currentLoad != SubscriptionLoadState.Idle) {
        AlertDialog(
            onDismissRequest = { if (currentLoad !is SubscriptionLoadState.Loading) onDismissPreview() },
            title = { Text(when (currentLoad) {
                is SubscriptionLoadState.Loading -> T("sub.loading")
                is SubscriptionLoadState.Preview -> T("sub.loaded")
                is SubscriptionLoadState.Failure -> T("sub.loadFailed")
                else -> ""
            }) },
            text = {
                when (currentLoad) {
                    is SubscriptionLoadState.Loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                        Text(T("sub.fetchingNodes"), style = MaterialTheme.typography.bodyMedium)
                    }
                    is SubscriptionLoadState.Preview -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(String.format(T("sub.foundNodes"), currentLoad.servers.size), style = MaterialTheme.typography.bodyMedium)
                        val preview = currentLoad.servers.take(5)
                        preview.forEach { server ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape))
                                Text("${server.name}  (${server.protocol.label})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
                        }
                        if (currentLoad.servers.size > 5) {
                            Text(String.format(T("sub.moreNodes"), currentLoad.servers.size - 5), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    is SubscriptionLoadState.Failure -> Text(currentLoad.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    else -> {}
                }
            },
            confirmButton = {
                when (currentLoad) {
                    is SubscriptionLoadState.Preview -> TextButton(onClick = { pendingConfigId?.let { onCommitPreview(it); pendingConfigId = null } }) { Text(T("sub.previewConfirm")) }
                    is SubscriptionLoadState.Failure -> TextButton(onClick = onDismissPreview) { Text(T("common.close")) }
                    else -> {}
                }
            },
            dismissButton = {
                if (currentLoad is SubscriptionLoadState.Preview) TextButton(onClick = onDismissPreview) { Text(T("common.cancel")) }
            }
        )
    }

    deletingId?.let { id ->
        val subscription = store.subscriptions.firstOrNull { it.id == id }
        val name = subscription?.name ?: store.localGroups.firstOrNull { it.id == id }?.name.orEmpty()
        AlertDialog(onDismissRequest = { deletingId = null }, title = { Text(T("groups.deleteTitle")) }, text = {
            Text(if (subscription != null) String.format(T("groups.delete.subText"), name) else String.format(T("groups.delete.localText"), name))
        }, confirmButton = { TextButton(onClick = {
            if (subscription != null) deleteSubscription(id) else deleteLocal(id)
            if (editingId == id) settingsOpen = false
            deletingId = null
        }) { Text(T("common.delete"), color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(onClick = { deletingId = null }) { Text(T("common.cancel"), color = MaterialTheme.colorScheme.primary) } })
    }

    if (ungroupedEditOpen) {
        AlertDialog(
            onDismissRequest = { ungroupedEditOpen = false },
            title = { Text(T("groups.ungrouped.arrange")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(T("groups.ungrouped.arrangeText"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(ungroupedName, { ungroupedName = it }, label = { Text(T("groups.name")) }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { createGroupFromUngrouped(ungroupedName.trim()); ungroupedEditOpen = false },
                    enabled = ungroupedName.isNotBlank(),
                ) { Text(T("common.create"), color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = { TextButton(onClick = { ungroupedEditOpen = false }) { Text(T("common.cancel"), color = MaterialTheme.colorScheme.primary) } },
        )
    }

    if (ungroupedDeleteOpen) {
        AlertDialog(
            onDismissRequest = { ungroupedDeleteOpen = false },
            title = { Text(T("groups.ungrouped.deleteTitle")) },
            text = { Text(T("groups.ungrouped.deleteText"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = { deleteUngrouped(); ungroupedDeleteOpen = false }) {
                    Text(T("common.delete"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { ungroupedDeleteOpen = false }) { Text(T("common.cancel"), color = MaterialTheme.colorScheme.primary) } },
        )
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
    var balanceStrategy by rememberSaveable { mutableStateOf(initial.balanceStrategy) }
    var nodePicker by remember { mutableStateOf<String?>(null) }
    var chooser by remember { mutableStateOf<String?>(null) }
    var saveError by remember { mutableStateOf<String?>(null) }
    val noLinkText = T("groups.noLinkInClipboard")
    val subLabel = T("groups.subscription")
    var discard by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val config = GroupConfiguration(id, name.trim(), subscription, url.trim(), hours.toIntOrNull() ?: 0, GroupSort.valueOf(order), frontNodeId, landingNodeId, balanceStrategy, initial.balanceNodeIds, initial.balanceTestUrl, initial.balanceIntervalSeconds)
    val dirty = config != initial
    val back = { if (dirty) discard = true else onBack() }
    BackHandler(onBack = back)
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = back) { Icon(Icons.Filled.Close, T("common.cancel"), tint = MaterialTheme.colorScheme.primary) }
            Text(T("groups.settings"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f).padding(start = 8.dp))
            TextButton(onClick = { saveError = config.validationError() ?: onConfirm(config) }) { Text(T("common.save"), color = MaterialTheme.colorScheme.primary) }
        }
        LazyColumn(contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)) {
            item { SettingGroup(T("groups.basic")) {
                GroupSettingText(T("groups.name"), name, T("groups.nameHint")) { name = it; saveError = null }
                GroupSettingChoice(T("groups.type"), if (subscription) T("groups.subscription") else T("groups.basicType"), Icons.Filled.List) { chooser = "type" }
                GroupSettingChoice(T("groups.sort"), T(config.sort.stringKey), Icons.Filled.Refresh) { chooser = "sort" }
            } }
            item { SettingGroup(T("groups.chain")) {
                GroupSettingChoice(T("groups.frontNode"), frontNodeId?.let { selected -> servers.firstOrNull { it.id == selected }?.name ?: T("groups.nodeInvalid") } ?: T("groups.disabled"), Icons.Filled.Send) { nodePicker = "front" }
                GroupSettingChoice(T("groups.landingNode"), landingNodeId?.let { selected -> servers.firstOrNull { it.id == selected }?.name ?: T("groups.nodeInvalid") } ?: T("groups.disabled"), Icons.Filled.Home) { nodePicker = "landing" }
                Text(T("groups.chainHint"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            } }
            if (subscription) item { SettingGroup(T("groups.subSettings")) {
                GroupSettingText(T("groups.subUrl"), url, "https://example.com/subscription") { url = it; saveError = null }
                TextButton(onClick = {
                    val text = readClipboard(context)
                    if (text.isNullOrBlank()) saveError = noLinkText else { url = text.trim(); saveError = null }
                }, modifier = Modifier.padding(start = 48.dp)) { Text(T("groups.pasteClipboard"), color = MaterialTheme.colorScheme.primary) }
                GroupSettingText(T("groups.interval"), hours, T("groups.intervalHint"), numeric = true) { hours = it; saveError = null }
                Text(T("groups.intervalHelp"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
                if (editing && !initial.subscription) Text(T("groups.toSubHint"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            } }
            if (!subscription && initial.subscription) item { Text(T("groups.toBasicHint"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)) }
            item { Text(T("groups.sortHint"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)) }
            saveError?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp)) } }
            if (editing) item {
                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(T("groups.delete"), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    nodePicker?.let { title ->
        GroupNodePicker(title, servers.filter { it.groupId != id && it.subscriptionId != id }, if (title == "front") frontNodeId else landingNodeId, { nodePicker = null }) { selected ->
            if (title == "front") frontNodeId = selected else landingNodeId = selected
            saveError = null
            nodePicker = null
        }
    }
    chooser?.let { title ->
        val typeOptions = listOf(T("groups.basicType"), T("groups.subscription"))
        val sortOptions = GroupSort.entries.map { it to T(it.stringKey) }
        val options = if (title == "type") typeOptions else sortOptions.map { it.second }
        val dialogTitle = if (title == "type") T("groups.type") else T("groups.sort")
        val selectedType = if (subscription) T("groups.subscription") else T("groups.basicType")
        val selectedSort = T(config.sort.stringKey)
        AlertDialog(onDismissRequest = { chooser = null }, title = { Text(dialogTitle) }, text = {
            Column { options.forEach { option ->
                TextButton(onClick = {
                    if (title == "type") subscription = option == subLabel else order = sortOptions.first { it.second == option }.first.name
                    saveError = null; chooser = null
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(option, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    if (option == if (title == "type") selectedType else selectedSort) Icon(Icons.Filled.Check, T("common.selected"), tint = MaterialTheme.colorScheme.primary)
                }
            } }
        }, confirmButton = { TextButton(onClick = { chooser = null }) { Text(T("common.cancel"), color = MaterialTheme.colorScheme.primary) } })
    }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text(T("groups.discardTitle")) }, text = { Text(T("groups.discardText")) }, confirmButton = { TextButton(onClick = onBack) { Text(T("groups.discard"), color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(onClick = { discard = false }) { Text(T("groups.keepEditing"), color = MaterialTheme.colorScheme.primary) } })
}

@Composable private fun GroupNodePicker(title: String, servers: List<ServerProfile>, selected: String?, onDismiss: () -> Unit, onSelect: (String?) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(String.format(T("groups.pickerTitle"), title)) }, text = {
        Column {
            OutlinedTextField(query, { query = it }, placeholder = { Text(T("groups.pickerSearch"), style = MaterialTheme.typography.bodySmall) }, singleLine = true, textStyle = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth())
            LazyColumn(Modifier.height(300.dp)) {
                item { TextButton(onClick = { onSelect(null) }, modifier = Modifier.fillMaxWidth()) { Text(T("groups.disabled"), color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f)); if (selected == null) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary) } }
                items(servers.filter { query.isBlank() || it.name.contains(query, true) || it.address.contains(query, true) }, key = { it.id }) { node ->
                    TextButton(onClick = { onSelect(node.id) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) { Text(node.name, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall); Text(node.protocol.label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall) }
                        if (selected == node.id) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                if (servers.isEmpty()) item { Text(T("groups.pickerEmpty"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp)) }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text(T("common.cancel")) } })
}

@Composable private fun GroupSettingText(title: String, value: String, placeholder: String, numeric: Boolean = false, onValueChange: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (title == T("groups.subUrl")) Icons.Filled.Refresh else Icons.Filled.Settings, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(21.dp))
        Spacer(Modifier.size(14.dp))
        OutlinedTextField(value, onValueChange, label = { Text(title, style = MaterialTheme.typography.bodySmall) }, placeholder = { Text(placeholder, style = MaterialTheme.typography.bodySmall) }, textStyle = MaterialTheme.typography.bodySmall, singleLine = true, shape = RoundedCornerShape(12.dp),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = if (numeric) androidx.compose.ui.text.input.KeyboardType.Number else if (title == T("groups.subUrl")) androidx.compose.ui.text.input.KeyboardType.Uri else androidx.compose.ui.text.input.KeyboardType.Text),
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
    AlertDialog(onDismissRequest = onDismiss, title = { Text(T("node.moveTitle")) }, text = {
        Column { TextButton(onClick = { onSelect(null) }, modifier = Modifier.fillMaxWidth()) { Text(T("node.local"), color = MaterialTheme.colorScheme.primary) }; groups.forEach { group -> TextButton(onClick = { onSelect(group.id) }, modifier = Modifier.fillMaxWidth()) { Text(group.name, color = MaterialTheme.colorScheme.primary) } } }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text(T("common.cancel")) } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun GroupCard(
    title: String,
    tags: List<String>,
    selected: Boolean,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    handle: Modifier,
    showHandle: Boolean = true,
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
                if (showHandle) {
                    Box(handle, contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.List, String.format(T("groups.dragHint"), title),
                            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.size(10.dp))
                }
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
                    Icon(Icons.Filled.Check, T("common.selected"), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
                if (onRefresh != null) {
                    IconButton(onClick = onRefresh, enabled = !refreshing, modifier = Modifier.size(38.dp)) {
                        if (refreshing) CircularProgressIndicator(Modifier.size(17.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                        else Icon(Icons.Filled.Refresh, String.format(T("groups.refreshTitle"), title), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
                    }
                }
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Filled.MoreVert, T("common.more"), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(19.dp))
                    }
                    IceDropdownMenu(menu, { menu = false }) {
                        if (onEdit != null) IceMenuItem(T("groups.edit"), Icons.Filled.Settings) { menu = false; onEdit() }
                        if (onDelete != null) IceMenuItem(T("groups.delete"), Icons.Filled.Delete, destructive = true) { menu = false; onDelete() }
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

@Composable internal fun IceMenuItem(title: String, icon: ImageVector, enabled: Boolean = true, destructive: Boolean = false, submenu: Boolean = false, onClick: () -> Unit) {
    val tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    DropdownMenuItem(
        text = { Text(title, style = MaterialTheme.typography.bodyMedium, color = if (!enabled) MaterialTheme.colorScheme.onSurfaceVariant else if (destructive) tint else MaterialTheme.colorScheme.onSurface) },
        leadingIcon = {
            Box(Modifier.size(30.dp).background(tint.copy(alpha = if (enabled) .08f else .04f), RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = if (enabled) tint else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        },
        trailingIcon = if (submenu) ({ Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)) }) else null,
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
    } }, confirmButton = { TextButton({ runCatching { com.iceflow.data.model.RoutingRules.validateTestUrl(url) }.onSuccess { valid -> update { it.copy(testUrl = valid) }; urlOpen = false }.onFailure { error = it.message } }) { Text(T("common.save")) } }, dismissButton = { TextButton({ urlOpen = false }) { Text(T("common.cancel")) } })
    if (dnsOpen) AlertDialog(onDismissRequest = { dnsOpen = false }, title = { Text("DNS 设置") }, text = { EditorInput("每行一个 DNS 地址", dns, { dns = it }, multiline = true) }, confirmButton = { TextButton({ val values = dns.lines().map { it.trim() }.filter { it.isNotEmpty() }; if (values.isNotEmpty()) { update { it.copy(dnsServers = values, remoteDns = values.first()) }; dnsOpen = false } }) { Text(T("common.save")) } }, dismissButton = { TextButton({ dnsOpen = false }) { Text(T("common.cancel")) } })
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
                T("settings.themeChoice"),
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

@Composable
private fun themeLabel(theme: com.iceflow.data.model.AppTheme) = when (theme) {
    com.iceflow.data.model.AppTheme.LIGHT_BLUE -> T("theme.lightBlue")
    com.iceflow.data.model.AppTheme.MINT_GREEN -> T("theme.mintGreen")
    com.iceflow.data.model.AppTheme.SAKURA_PINK -> T("theme.sakuraPink")
    com.iceflow.data.model.AppTheme.PEACH_ORANGE -> T("theme.peachOrange")
    com.iceflow.data.model.AppTheme.OBSIDIAN_BLACK -> T("theme.obsidianBlack")
}

@Composable private fun AppSelectionPage(selected: List<String>, update: (List<String>) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val packageManager = context.packageManager
    val apps = remember { packageManager.getInstalledApplications(0).sortedBy { it.loadLabel(packageManager).toString() } }
    Column(modifier.statusBarsPadding().fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().height(68.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, T("common.back"), tint = MaterialTheme.colorScheme.primary) }
            Text(T("apps.bypassTitle"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
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
@Composable private fun SettingAction(title: String, icon: ImageVector, onClick: () -> Unit) { Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(21.dp)); Spacer(Modifier.size(14.dp)); Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f)); Text(T("common.view"), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) } }
@Composable private fun SettingSwitch(title: String, checked: Boolean, icon: ImageVector, onClick: () -> Unit) { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(21.dp)); Spacer(Modifier.size(14.dp)); Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f)); Switch(checked, { onClick() }) } }

@Composable private fun AddSubscriptionDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) { var url by rememberSaveable { mutableStateOf("") }; AlertDialog(onDismissRequest = onDismiss, title = { Text(T("sub.add")) }, text = { OutlinedTextField(url, { url = it }, label = { Text(T("sub.url")) }, singleLine = true) }, confirmButton = { TextButton(onClick = { onConfirm(url.trim()) }, enabled = url.startsWith("http")) { Text(T("common.add")) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(T("common.cancel")) } }) }

private fun readClipboard(context: Context): String? { val cm = context.getSystemService(ClipboardManager::class.java) ?: return null; return cm.primaryClip?.let { if (it.itemCount > 0) it.getItemAt(0).coerceToText(context).toString() else null } }

@Composable private fun UpdateDialog(
    state: UpdateState,
    onDismiss: () -> Unit,
    onRetry: (String) -> Unit,
    onDontRemind: () -> Unit,
    onDownload: (UpdateInfo) -> Unit,
) {
    if (state is UpdateState.Idle) return
    var dontRemind by remember(state) { mutableStateOf(false) }
    var token by remember(state) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(when (state) {
                UpdateState.Checking -> T("update.title")
                is UpdateState.Available -> String.format(T("update.available"), state.info.version)
                is UpdateState.UpToDate -> T("update.upToDate")
                is UpdateState.Failed -> T("update.failed")
                else -> ""
            })
        },
        text = {
            when (state) {
                UpdateState.Checking -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Text(T("update.checkingText"), style = MaterialTheme.typography.bodySmall)
                }
                is UpdateState.Available -> Column {
                    Text(
                        state.info.notes.ifBlank { T("update.notesFallback") },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth().clickable { dontRemind = !dontRemind }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(dontRemind, { dontRemind = it })
                        Text(T("update.dontRemind"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                is UpdateState.UpToDate -> Text(String.format(T("update.upToDateText"), state.version), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                is UpdateState.Failed -> Column {
                    Text(state.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(token, { token = it }, label = { Text(T("update.token")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                else -> {}
            }
        },
        confirmButton = {
            when (state) {
                is UpdateState.Available -> TextButton(onClick = { if (dontRemind) onDontRemind(); onDownload(state.info) }) { Text(T("update.download"), color = MaterialTheme.colorScheme.primary) }
                is UpdateState.Failed -> TextButton(onClick = { onRetry(token) }) { Text(T("common.retry"), color = MaterialTheme.colorScheme.primary) }
                UpdateState.Checking -> {}
                else -> TextButton(onClick = onDismiss) { Text(T("common.ok"), color = MaterialTheme.colorScheme.primary) }
            }
        },
        dismissButton = {
            if (state is UpdateState.Available) TextButton(onClick = { if (dontRemind) onDontRemind(); onDismiss() }) { Text(T("update.later"), color = MaterialTheme.colorScheme.primary) }
            else if (state is UpdateState.Failed) TextButton(onClick = onDismiss) { Text(T("common.cancel"), color = MaterialTheme.colorScheme.primary) }
        },
    )
}

private fun shareNode(context: Context, server: ServerProfile, title: String) {
    shareText(context, title, ShareLinkParser.encodeV2rayN(server))
}

private fun shareText(context: Context, title: String, text: String) {
    if (text.isBlank()) return
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, title))
}
