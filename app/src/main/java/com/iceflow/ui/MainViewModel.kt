package com.iceflow.ui

import android.app.Application
import com.iceflow.BuildConfig
import com.iceflow.IceFlowApp
import com.iceflow.core.XrayBridge
import com.iceflow.core.RustBridge
import com.iceflow.data.XrayConfigBuilder
import com.iceflow.service.VpnState
import com.iceflow.data.ShareLinkParser
import com.iceflow.data.model.AppSettings
import com.iceflow.data.model.GroupConfiguration
import com.iceflow.data.model.GroupSort
import com.iceflow.data.model.ServerProfile
import com.iceflow.data.repository.SubscriptionFetcher
import com.iceflow.service.VpnController
import com.iceflow.service.SubscriptionScheduler
import com.iceflow.service.AppLog
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import java.net.URL
import java.net.InetSocketAddress
import java.net.Socket

data class ConnectionTestState(val testing: Boolean = false, val latencyMs: Int? = null)
data class NodeTestState(val serverId: String? = null, val name: String = "", val testing: Boolean = false, val latencyMs: Int? = null)
data class BulkTestState(val testing: Boolean = false, val nodes: List<ServerProfile> = emptyList(), val currentId: String? = null, val completed: Int = 0, val results: Map<String, Int> = emptyMap())

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as IceFlowApp).container

    val vpnState = VpnController.state
    val traffic = VpnController.traffic
    val store = container.servers.store
    val settings: StateFlow<AppSettings> = container.settings.settings
    val routingResources get() = container.routingResources

    private val _testing = MutableStateFlow(false)
    val testing: StateFlow<Boolean> = _testing.asStateFlow()
    private val _nodeTest = MutableStateFlow(NodeTestState())
    val nodeTest: StateFlow<NodeTestState> = _nodeTest.asStateFlow()
    private val _bulkTest = MutableStateFlow(BulkTestState())
    val bulkTest: StateFlow<BulkTestState> = _bulkTest.asStateFlow()
    private val _refreshingSubscriptions = MutableStateFlow(false)
    val refreshingSubscriptions = _refreshingSubscriptions.asStateFlow()

    private val _publicIp = MutableStateFlow<String?>(null)
    val publicIp: StateFlow<String?> = _publicIp.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    val appVersion: String = BuildConfig.VERSION_NAME
    val xrayVersion: String? by lazy { XrayBridge.version() }
    val coreVersion: String? by lazy { RustBridge.version() }
    private val _connectionTest = MutableStateFlow(ConnectionTestState())
    val connectionTest = _connectionTest.asStateFlow()
    private var connectionTestJob: Job? = null

    init {
        viewModelScope.launch {
            while (true) {
                withContext(Dispatchers.IO) {
                    AppLog.cleanOldLogs(getApplication(), settings.value.logRetentionDays)
                }
                kotlinx.coroutines.delay(24 * 60 * 60 * 1000L)
            }
        }
        viewModelScope.launch {
            var activeServerId: String? = null
            var lastUp = 0L
            var lastDown = 0L
            traffic.collect { snapshot ->
                val connected = vpnState.value as? VpnState.Connected
                if (connected == null) {
                    activeServerId = null
                    lastUp = snapshot.upTotal
                    lastDown = snapshot.downTotal
                } else {
                    val selectedId = container.servers.selected?.id
                    if (selectedId != activeServerId) {
                        activeServerId = selectedId
                        lastUp = snapshot.upTotal
                        lastDown = snapshot.downTotal
                    } else if (selectedId != null) {
                        val uploadDelta = (snapshot.upTotal - lastUp).coerceAtLeast(0)
                        val downloadDelta = (snapshot.downTotal - lastDown).coerceAtLeast(0)
                        if (uploadDelta > 0 || downloadDelta > 0) {
                            container.servers.store.value.servers.firstOrNull { it.id == selectedId }?.let { server ->
                                container.servers.updateServer(server.copy(
                                    uploadBytes = server.uploadBytes + uploadDelta,
                                    downloadBytes = server.downloadBytes + downloadDelta,
                                ))
                            }
                        }
                        lastUp = snapshot.upTotal
                        lastDown = snapshot.downTotal
                    }
                }
            }
        }
        viewModelScope.launch {
            vpnState.collectLatest {
                connectionTestJob?.cancel()
                _connectionTest.value = ConnectionTestState()
            }
        }
    }

    fun testRunningConnection() {
        val session = vpnState.value as? VpnState.Connected ?: run {
            android.util.Log.w("MainViewModel", "testRunningConnection: VPN未连接")
            return
        }
        if (_connectionTest.value.testing) {
            android.util.Log.w("MainViewModel", "testRunningConnection: 测速进行中，跳过")
            return
        }

        val profile = container.servers.selected ?: run {
            android.util.Log.w("MainViewModel", "testRunningConnection: 未选择节点")
            return
        }

        android.util.Log.i("MainViewModel", "testRunningConnection: 开始测速节点 ${profile.name}, testUrl=${settings.value.testUrl}")
        _connectionTest.value = ConnectionTestState(testing = true)
        connectionTestJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            val latency = withContext(Dispatchers.IO) {
                runCatching {
                    // 使用 XrayBridge.measureDelay 直接测试代理节点，而不是通过已连接的 SOCKS 端口
                    // 这样能真正测量节点到目标的延迟，而不受 VPN 排除自身应用的影响
                    val config = XrayConfigBuilder.build(profile, settings.value, store = container.servers.store.value)
                    XrayBridge.measureDelay(config, settings.value.testUrl, 5000L).toInt()
                }.onSuccess { result ->
                    android.util.Log.i("MainViewModel", "测速成功: ${result}ms")
                }.onFailure { error ->
                    android.util.Log.e("MainViewModel", "测速失败: ${error.message}", error)
                }.getOrDefault(-1)
            }
            val elapsed = System.currentTimeMillis() - startTime
            android.util.Log.i("MainViewModel", "testRunningConnection: 测速完成, latency=${latency}ms, 耗时=${elapsed}ms")
            if (vpnState.value == session) {
                _connectionTest.value = ConnectionTestState(latencyMs = latency)
                withContext(Dispatchers.IO) {
                    AppLog.write(getApplication(), "INFO", if (latency >= 0) "当前连接测速：$latency ms" else "当前连接测速失败或超时")
                }
            } else {
                android.util.Log.w("MainViewModel", "testRunningConnection: VPN会话已变更，丢弃测速结果")
            }
        }
    }

    fun canConnect(): Boolean {
        if (container.servers.selected == null) {
            _messages.tryEmit("请先添加并选择节点")
            return false
        }
        return true
    }

    fun select(id: String) {
        container.servers.select(id)
        if (vpnState.value is VpnState.Connected) VpnController.switch(getApplication())
    }
    fun delete(id: String) = container.servers.delete(id)
    fun updateServer(server: ServerProfile) = container.servers.updateServer(server)
    fun saveServer(server: ServerProfile): String? = runCatching {
        val validated = com.iceflow.data.NodeEditing.validate(server)
        if (store.value.servers.any { it.id == validated.id }) container.servers.updateServer(validated)
        else {
            container.servers.addServers(listOf(validated))
            container.servers.selectGroup(validated.subscriptionId ?: validated.groupId)
        }
        null
    }.getOrElse { it.message ?: "保存节点失败" }
    fun addLocalGroup(name: String) = container.servers.addLocalGroup(name)
    fun renameLocalGroup(id: String, name: String) = container.servers.renameLocalGroup(id, name)
    fun deleteLocalGroup(id: String) = container.servers.deleteLocalGroup(id)
    fun moveServerToGroup(serverId: String, groupId: String?) = container.servers.moveServerToLocalGroup(serverId, groupId)
    fun selectGroup(groupId: String?) = container.servers.selectGroup(groupId)
    fun reorderGroups(keys: List<String>) = container.servers.reorderGroups(keys)

    fun reorderServers(groupId: String?, ids: List<String>) = container.servers.reorderServers(groupId, ids)
    fun updateSubscription(id: String, name: String, intervalHours: Int) {
        container.servers.updateSubscription(id, name, intervalHours)
        container.servers.store.value.subscriptions.firstOrNull { it.id == id }?.let { SubscriptionScheduler.schedule(getApplication(), it) }
    }
    fun deleteSubscription(id: String) {
        SubscriptionScheduler.cancel(getApplication(), id)
        container.servers.deleteSubscription(id)
    }

    fun saveGroup(config: GroupConfiguration): String? {
        val previous = store.value.subscriptions.firstOrNull { it.id == config.id }
        return runCatching {
            container.servers.saveGroup(config)
            if (config.subscription) {
                store.value.subscriptions.first { it.id == config.id }.let { SubscriptionScheduler.schedule(getApplication(), it) }
                if (previous?.url != config.url.trim()) viewModelScope.launch { refreshOne(config.url.trim(), config.name.trim(), config.id) }
            } else SubscriptionScheduler.cancel(getApplication(), config.id)
            null
        }.getOrElse { it.message ?: "保存分组失败" }
    }
    fun updateSettings(transform: (AppSettings) -> AppSettings) = container.settings.update(transform)

    fun setGroupSort(groupId: String?, sort: GroupSort) = container.servers.setGroupSort(groupId, sort)

    fun sortGroupByLatency(groupId: String?) {
        if (_testing.value || _nodeTest.value.testing) return
        val nodes = store.value.servers.filter { server ->
            if (groupId == null) server.subscriptionId == null && server.groupId == null
            else server.groupId == groupId || server.subscriptionId == groupId
        }
        if (nodes.isEmpty()) {
            container.servers.setGroupSort(groupId, GroupSort.LATENCY)
            return
        }
        _testing.value = true
        _bulkTest.value = BulkTestState(testing = true, nodes = nodes)
        viewModelScope.launch {
            val results = linkedMapOf<String, Int>()
            try {
                nodes.forEachIndexed { index, server ->
                    _bulkTest.value = BulkTestState(true, nodes, server.id, index, results.toMap())
                    val latency = measure(server)
                    results[server.id] = latency
                    container.servers.updateLatency(server.id, latency)
                    _bulkTest.value = BulkTestState(true, nodes, if (index + 1 < nodes.size) nodes[index + 1].id else null, index + 1, results.toMap())
                }
                container.servers.setGroupSort(groupId, GroupSort.LATENCY)
            } finally {
                _testing.value = false
                _bulkTest.value = BulkTestState(false, nodes, null, nodes.size, results.toMap())
            }
        }
    }

    /** 设置当前分组的排序方式（原始/名称/延迟），分组内节点列表据此自动排序，与分组设置页的"排序"选项保持同步。 */
    fun setGroupSort(groupId: String?, sort: com.iceflow.data.model.GroupSort) = container.servers.setGroupSort(groupId, sort)

    /** 手动调序：按用户拖动后的最终顺序写回节点的 sortOrder 字段。 */
    fun reorderServers(orderedIds: List<String>) = container.servers.reorderServers(orderedIds)

    /** 在当前分组内将节点上移或下移一位，与"排序"功能完全独立。 */
    fun moveServerWithinGroup(serverId: String, groupId: String?, up: Boolean) = container.servers.moveServerWithinGroup(serverId, groupId, up)

    fun dedupeServers() {
        val removed = container.servers.dedupe()
        _messages.tryEmit(if (removed > 0) "已移除 $removed 个重复节点" else "没有发现重复节点")
    }

    fun importText(text: String?) {
        val servers = runCatching { ShareLinkParser.parseMany(text.orEmpty()) }.getOrElse {
            _messages.tryEmit("导入失败：${it.message}")
            return
        }
        if (servers.isEmpty()) {
            _messages.tryEmit("内容中没有可识别的分享链接或 Clash 节点")
        } else {
            container.servers.addServers(servers)
            _messages.tryEmit("已导入 ${servers.size} 个节点")
        }
    }

    fun addSubscription(url: String) = viewModelScope.launch { refreshOne(url, url.substringAfter("://").substringBefore('/')) }

    fun refreshSubscriptions() = viewModelScope.launch {
        if (_refreshingSubscriptions.value) return@launch
        _refreshingSubscriptions.value = true
        try {
            val subscriptions = store.value.subscriptions
            if (subscriptions.isEmpty()) _messages.emit("没有需要刷新的订阅分组")
            subscriptions.forEach { refreshOne(it.url, it.name, it.id) }
        } finally { _refreshingSubscriptions.value = false }
    }

    private suspend fun refreshOne(url: String, name: String, expectedId: String? = null) {
        runCatching {
            val servers = ShareLinkParser.parseMany(SubscriptionFetcher.fetch(url))
            check(servers.isNotEmpty()) { "订阅中没有可识别的节点" }
            container.servers.upsertSubscription(url, name, servers, expectedId)
            container.servers.store.value.subscriptions.firstOrNull { it.url == url }?.let { SubscriptionScheduler.schedule(getApplication(), it) }
            servers.size
        }.onSuccess { _messages.emit("订阅 $name：$it 个节点") }
            .onFailure { _messages.emit("订阅 $name 更新失败：${it.message}") }
    }

    fun testAll() {
        if (_testing.value || _nodeTest.value.testing) return
        val nodes = store.value.servers.toList()
        if (nodes.isEmpty()) { _messages.tryEmit("没有可测速的节点"); return }
        _testing.value = true
        _bulkTest.value = BulkTestState(testing = true, nodes = nodes)
        viewModelScope.launch {
            val results = linkedMapOf<String, Int>()
            nodes.forEachIndexed { index, server ->
                _bulkTest.value = BulkTestState(true, nodes, server.id, index, results.toMap())
                val latency = measure(server)
                results[server.id] = latency
                container.servers.updateLatency(server.id, latency)
                _bulkTest.value = BulkTestState(true, nodes, if (index + 1 < nodes.size) nodes[index + 1].id else null, index + 1, results.toMap())
            }
            _testing.value = false
            _bulkTest.value = BulkTestState(false, nodes, null, nodes.size, results.toMap())
        }
    }

    fun testOne(server: ServerProfile) {
        if (_nodeTest.value.testing || _testing.value) return
        _nodeTest.value = NodeTestState(server.id, server.name, testing = true)
        viewModelScope.launch {
            val latency = measure(server)
            container.servers.updateLatency(server.id, latency)
            _nodeTest.value = NodeTestState(server.id, server.name, latencyMs = latency)
            kotlinx.coroutines.delay(3500)
            if (_nodeTest.value.serverId == server.id) _nodeTest.value = NodeTestState()
        }
    }

    fun dismissBulkTest() {
        if (_bulkTest.value.testing) return
        _bulkTest.value = BulkTestState()
    }

    fun refreshPublicIp() {
        viewModelScope.launch(Dispatchers.IO) {
            _publicIp.value = runCatching {
                URL("https://api.ipify.org").openStream().bufferedReader().use { it.readText().trim() }
            }.getOrNull()
        }
    }

    suspend fun readLogs(): String = withContext(Dispatchers.IO) { AppLog.read(getApplication()) }

<<<<<<< Updated upstream
    fun cleanOldLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            AppLog.cleanOldLogs(getApplication(), settings.value.logRetentionDays)
        }
    }

    /** 优先用 xray 真实请求测速；未集成 xray 时退化为 TCP 握手耗时。 */
=======
    /** 测速策略说明：
     * - 若该节点正是当前已连接并选中的节点，使用真实隧道（ConnectionProbe）测速，
     *   以保证结果与"点击测速"面板显示的当前连接延迟一致。
     * - 其余节点直接对节点自身的地址:端口做 TCP 握手计时。这个值才能真实反映
     *   手机到该服务器的网络距离（例如美国节点应明显高于本地节点）。
     *   之前版本改用 xray 发起一次到 generate_204（Google Anycast/CDN）的完整请求，
     *   但 Anycast 会把请求路由到"离代理服务器最近"的 Google 边缘节点，而不是固定地址，
     *   导致几乎所有服务器测出的结果都趋近同一个值（代理到最近 CDN 节点的距离），
     *   而不是"手机到代理服务器"的距离，这正是之前所有节点延迟都在 200ms 左右的根因。
     *   直接测 TCP 握手耗时没有这个问题，且更快、更贴近用户对"延迟"的直觉认知。 */
>>>>>>> Stashed changes
    private suspend fun measure(server: ServerProfile): Int = withContext(Dispatchers.IO) {
        runCatching {
            val session = vpnState.value as? VpnState.Connected
            if (session != null && store.value.selectedId == server.id) {
                ConnectionProbe.measure(session.socksPort, settings.value.testUrl, TIMEOUT_MS.toInt())
            } else {
                val start = System.nanoTime()
                Socket().use { it.connect(InetSocketAddress(server.address, server.port), TIMEOUT_MS.toInt()) }
                ((System.nanoTime() - start) / 1_000_000).coerceAtLeast(1).toInt()
            }
        }.getOrDefault(-1)
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
