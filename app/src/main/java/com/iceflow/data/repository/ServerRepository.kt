package com.iceflow.data.repository

import com.iceflow.data.model.ServerProfile
import com.iceflow.data.model.ServerStore
import com.iceflow.data.model.Subscription
import com.iceflow.data.model.LocalGroup
import com.iceflow.data.model.GroupConfiguration
import com.iceflow.data.model.GroupSort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException

/** 节点与订阅的持久化（JSON 文件，原子写入）。 */
class ServerRepository(private val file: File) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val lock = Any()
    private val backup = File(file.path + ".bak")
    var recoveryMessage: String? = null
        private set
    private var unreadable = false
    private val _store = MutableStateFlow(load())
    val store: StateFlow<ServerStore> = _store.asStateFlow()

    val selected: ServerProfile? get() = _store.value.selected

    fun addServers(servers: List<ServerProfile>) = mutate { s ->
        s.copy(servers = s.servers + servers, selectedId = s.selectedId ?: servers.firstOrNull()?.id)
    }

    fun updateServer(server: ServerProfile) = mutate { s -> s.copy(servers = s.servers.map { if (it.id == server.id) server else it }) }

    fun addTraffic(id: String, upload: Long, download: Long) = mutate { s ->
        s.copy(servers = s.servers.map { if (it.id == id) it.copy(uploadBytes = it.uploadBytes + upload, downloadBytes = it.downloadBytes + download) else it })
    }

    fun select(id: String) = mutate { it.copy(selectedId = id) }

    fun delete(id: String) = mutate { s ->
        val remaining = s.servers.filterNot { it.id == id }
        s.copy(
            servers = remaining,
            selectedId = if (s.selectedId == id) remaining.firstOrNull()?.id else s.selectedId,
        )
    }

    fun updateLatency(id: String, latencyMs: Int) = mutate { s ->
        s.copy(servers = s.servers.map { if (it.id == id) it.copy(latencyMs = latencyMs) else it })
    }

    /** 按延迟升序排序：正常测速结果在前，失败（-1）次之，未测速最后。 */
    fun sortByLatency() = mutate { s ->
        s.copy(servers = s.servers.sortedWith(compareBy(
            { when { it.latencyMs == null -> 2; it.latencyMs < 0 -> 1; else -> 0 } },
            { it.latencyMs ?: 0 },
        )))
    }

    /** 按完整连接配置去重，忽略显示名、所属分组和测速结果。 */
    fun dedupe(): Int {
        val seen = HashSet<ServerProfile>()
        val kept = _store.value.servers.filter { seen.add(it.copy(id = "", name = "", subscriptionId = null, groupId = null, latencyMs = null)) }
        val removed = _store.value.servers.size - kept.size
        if (removed > 0) mutate { s -> s.copy(servers = kept) }
        return removed
     }

    fun addLocalGroup(name: String) = mutate { s -> s.copy(localGroups = s.localGroups + LocalGroup(name = name.trim())) }

    /** 导入节点到指定本地分组；分组缺失（或传入的是订阅分组）时自动新建并选中。 */
    fun importServers(servers: List<ServerProfile>, preferredGroupId: String?): LocalGroup {
        var targetId: String? = null
        mutate { s ->
            val existing = s.localGroups.firstOrNull { it.id == preferredGroupId }
            val group = existing ?: LocalGroup(name = nextGroupName(s))
            targetId = group.id
            s.copy(
                localGroups = if (existing == null) s.localGroups + group else s.localGroups,
                servers = s.servers + servers.map { it.copy(groupId = group.id, subscriptionId = null) },
                selectedId = s.selectedId ?: servers.firstOrNull()?.id,
                selectedGroupId = group.id,
            )
        }
        return store.value.localGroups.first { it.id == targetId }
    }

    /** 把未分组节点整理成一个新的本地分组。 */
    fun createGroupFromUngrouped(name: String): LocalGroup {
        var targetId: String? = null
        mutate { s ->
            val group = LocalGroup(name = name.trim())
            targetId = group.id
            s.copy(
                localGroups = s.localGroups + group,
                servers = s.servers.map {
                    if (it.subscriptionId == null && it.groupId == null) it.copy(groupId = group.id) else it
                },
                selectedGroupId = group.id,
            )
        }
        return store.value.localGroups.first { it.id == targetId }
    }

    /** 清空未分组节点。 */
    fun deleteUngroupedServers() = mutate { s ->
        val remaining = s.servers.filterNot { it.subscriptionId == null && it.groupId == null }
        s.copy(
            servers = remaining,
            selectedId = s.selectedId?.takeIf { id -> remaining.any { it.id == id } } ?: remaining.firstOrNull()?.id,
        )
    }

    private fun nextGroupName(store: ServerStore): String {
        val used = (store.localGroups.map { it.name } + store.subscriptions.map { it.name }).toSet()
        if ("新分组" !in used) return "新分组"
        var index = 2
        while ("新分组 $index" in used) index++
        return "新分组 $index"
    }

    fun saveGroup(config: GroupConfiguration) = mutate { s ->
        require(config.validationError() == null) { config.validationError().orEmpty() }
        require(config.balanceStrategy == null || !config.subscription) { "Load balancing is supported on local groups only" }
        val groupNodes = s.servers.filter { it.groupId == config.id && it.composition == null }
        val balanceNodes = if (config.balanceNodeIds.isEmpty()) groupNodes else groupNodes.filter { it.id in config.balanceNodeIds }
        require(config.balanceStrategy == null || balanceNodes.size >= 2) { "Load balancing requires at least two selected nodes in this group" }
        require(config.balanceNodeIds.distinct().size == config.balanceNodeIds.size && config.balanceNodeIds.all { id -> groupNodes.any { it.id == id } }) { "Load balancing nodes must belong to this group" }
        require(config.balanceTestUrl.isBlank() || runCatching { java.net.URI(config.balanceTestUrl.trim()).let { it.scheme in setOf("http", "https") && !it.host.isNullOrBlank() } }.getOrDefault(false)) { "Invalid load-balancing test URL" }
        require(!config.subscription || s.subscriptions.none { it.id != config.id && it.url == config.url.trim() }) { "该订阅链接已存在" }
        listOfNotNull(config.frontNodeId, config.landingNodeId).forEach { nodeId ->
            val node = s.servers.firstOrNull { it.id == nodeId }
            require(node != null) { "所选代理节点已删除，请重新选择" }
            require(node.subscriptionId != config.id && node.groupId != config.id) { "请选择本分组以外的前置或落地节点" }
        }
        val old = s.subscriptions.firstOrNull { it.id == config.id }
        val subscription = if (config.subscription) (old ?: Subscription(id = config.id, name = config.name.trim(), url = config.url.trim())).copy(
            name = config.name.trim(), url = config.url.trim(), updateIntervalHours = config.intervalHours, sort = config.sort,
            frontNodeId = config.frontNodeId, landingNodeId = config.landingNodeId,
            updatedAt = if (old?.url == config.url.trim()) old.updatedAt else 0,
        ) else null
        val locals = if (subscription == null) {
            if (s.localGroups.any { it.id == config.id }) s.localGroups.map { if (it.id == config.id) it.copy(name = config.name.trim(), sort = config.sort, frontNodeId = config.frontNodeId, landingNodeId = config.landingNodeId, balanceStrategy = config.balanceStrategy, balanceNodeIds = config.balanceNodeIds, balanceTestUrl = config.balanceTestUrl.trim(), balanceIntervalSeconds = config.balanceIntervalSeconds) else it }
            else s.localGroups + LocalGroup(config.id, config.name.trim(), config.sort, config.frontNodeId, config.landingNodeId, config.balanceStrategy, config.balanceNodeIds, config.balanceTestUrl.trim(), config.balanceIntervalSeconds)
        } else s.localGroups.filterNot { it.id == config.id }
        val subscriptions = if (subscription != null) {
            if (old != null) s.subscriptions.map { if (it.id == config.id) subscription else it } else s.subscriptions + subscription
        } else s.subscriptions.filterNot { it.id == config.id }
        s.copy(localGroups = locals, subscriptions = subscriptions, servers = s.servers.map { server ->
            when {
                subscription != null && server.groupId == config.id -> server.copy(groupId = null, subscriptionId = config.id)
                subscription == null && server.subscriptionId == config.id -> server.copy(subscriptionId = null, groupId = config.id)
                else -> server
            }
        })
    }

    fun renameLocalGroup(id: String, name: String) = mutate { s -> s.copy(localGroups = s.localGroups.map { if (it.id == id) it.copy(name = name.trim()) else it }) }

    fun deleteLocalGroup(id: String) = mutate { s ->
        val fallback: String? = null
        s.copy(localGroups = s.localGroups.filterNot { it.id == id }, selectedGroupId = if (s.selectedGroupId == id) fallback else s.selectedGroupId, servers = s.servers.map { if (it.groupId == id) it.copy(groupId = fallback) else it })
    }

    fun updateSubscription(id: String, name: String, updateIntervalHours: Int) = mutate { s ->
        s.copy(subscriptions = s.subscriptions.map { if (it.id == id) it.copy(name = name.trim(), updateIntervalHours = updateIntervalHours.coerceIn(0, 720)) else it })
    }

    fun deleteSubscription(id: String) = mutate { s ->
        val remaining = s.servers.filterNot { it.subscriptionId == id }
        s.copy(subscriptions = s.subscriptions.filterNot { it.id == id }, servers = remaining, selectedGroupId = s.selectedGroupId?.takeUnless { it == id }, selectedId = s.selectedId?.takeIf { selected -> remaining.any { it.id == selected } } ?: remaining.firstOrNull()?.id)
    }

    fun moveServerToLocalGroup(serverId: String, groupId: String?) = mutate { s -> s.copy(servers = s.servers.map { if (it.id == serverId) it.copy(groupId = groupId) else it }) }

    /** 设置指定分组（本地分组或订阅）的排序方式；groupId 为 null 时表示未分组节点。 */
    fun setGroupSort(groupId: String?, sort: GroupSort) = mutate { s ->
        if (groupId == null) s.copy(localSort = sort)
        else s.copy(
            localGroups = s.localGroups.map { if (it.id == groupId) it.copy(sort = sort) else it },
            subscriptions = s.subscriptions.map { if (it.id == groupId) it.copy(sort = sort) else it },
        )
    }

    fun selectGroup(groupId: String?) = mutate { it.copy(selectedGroupId = groupId) }

    /** 按 [ids] 的顺序重排分组内节点，并把分组排序切回“原始”，使手动顺序生效。 */
    fun reorderServers(groupId: String?, ids: List<String>) = mutate { s ->
        fun inGroup(server: ServerProfile) =
            if (groupId == null) server.subscriptionId == null && server.groupId == null
            else server.groupId == groupId || server.subscriptionId == groupId
        val members = s.servers.filter(::inGroup).associateBy { it.id }
        val ordered = (ids.filter { it in members } + members.keys).distinct().map { members.getValue(it) }.iterator()
        s.copy(
            servers = s.servers.map { if (inGroup(it)) ordered.next() else it },
            localSort = if (groupId == null) GroupSort.ORIGINAL else s.localSort,
            localGroups = s.localGroups.map { if (it.id == groupId) it.copy(sort = GroupSort.ORIGINAL) else it },
            subscriptions = s.subscriptions.map { if (it.id == groupId) it.copy(sort = GroupSort.ORIGINAL) else it },
        )
    }

    fun reorderGroups(keys: List<String>) = mutate { s ->
        val valid = s.orderedGroups.map { it.key }
        s.copy(groupOrder = (keys + valid).distinct().filter { it in valid })
    }

    /** 新增或更新订阅，并用新节点替换该订阅下的旧节点。 */
    fun upsertSubscription(url: String, name: String, servers: List<ServerProfile>, expectedId: String? = null, userInfo: String? = null, downloadedBytes: Long = 0) = mutate { s ->
        val existing = s.subscriptions.firstOrNull { it.url == url && (expectedId == null || it.id == expectedId) }
        if (expectedId != null && existing == null) return@mutate s
        val sub = (existing ?: Subscription(name = name, url = url)).copy(
            updatedAt = System.currentTimeMillis(),
            uploadBytes = parseTrafficHeader(userInfo, "upload"),
            downloadBytes = parseTrafficHeader(userInfo, "download"),
            downloadedConfigBytes = downloadedBytes,
        )
        val previous = s.servers.filter { it.subscriptionId == sub.id }
        val available = previous.toMutableList()
        // Keep stable IDs for unambiguous nodes referenced by other groups. Never
        // silently substitute an unrelated node when the provider removes it.
        val tagged = servers.map { incoming ->
            fun ServerProfile.identity() = copy(id = "", name = "", subscriptionId = null, groupId = null, latencyMs = null)
            val exact = available.filter { it.identity() == incoming.identity() }
            val match = exact.singleOrNull() ?: available.filter { it.name == incoming.name && it.protocol == incoming.protocol && it.address == incoming.address && it.port == incoming.port }.singleOrNull()
            if (match != null) available.remove(match)
            incoming.copy(id = match?.id ?: incoming.id, subscriptionId = sub.id, groupId = null)
        }
        val others = s.servers.filterNot { it.subscriptionId == sub.id }
        val all = others + tagged
        s.copy(
            servers = all,
            subscriptions = if (existing == null) s.subscriptions + sub else s.subscriptions.map { if (it.id == sub.id) sub else it },
            selectedId = s.selectedId?.takeIf { id -> all.any { it.id == id } } ?: all.firstOrNull()?.id,
        )
    }

    private fun parseTrafficHeader(value: String?, key: String): Long {
        return value.orEmpty().split(';').mapNotNull { part ->
            val pair = part.trim().split('=', limit = 2)
            if (pair.size == 2 && pair[0].equals(key, true)) pair[1].toLongOrNull() else null
        }.firstOrNull() ?: 0L
    }

    private fun load(): ServerStore {
        val primary = runCatching { json.decodeFromString<ServerStore>(file.readText()) }.getOrNull()
        val recovered = if (primary == null) runCatching { json.decodeFromString<ServerStore>(backup.readText()) }.getOrNull() else null
        if (recovered != null) recoveryMessage = "节点文件读取失败，已从上一次有效副本恢复"
        if (primary == null && recovered == null && (file.length() > 0 || backup.exists())) {
            unreadable = true
            recoveryMessage = "节点文件及恢复副本读取失败，已保护原文件，请从备份恢复"
        }
        val decoded = primary ?: recovered ?: ServerStore()
        val migrated = migrateLegacyAnyTls(decoded)
        if (primary != null && migrated != decoded) {
            // Commit the compatibility migration while the source file is known to be valid.
            // A failed migration write must not prevent the app from opening the old data.
            runCatching {
                val content = json.encodeToString(ServerStore.serializer(), migrated)
                writeAtomic(backup, content)
                writeAtomic(file, content)
            }
        }
        return if (migrated.selectedGroupId == null && migrated.localGroups.isNotEmpty()) migrated.copy(selectedGroupId = migrated.localGroups.first().id) else migrated
    }

    private fun migrateLegacyAnyTls(store: ServerStore): ServerStore {
        var changed = false
        val servers = store.servers.map { server ->
            if (server.protocol != com.iceflow.data.model.Protocol.ANYTLS) return@map server
            val options = server.protocolOptions.toMutableMap()
            val legacyAllowInsecure = listOf("insecure", "allowInsecure", "skip-cert-verify")
                .any { options[it]?.lowercase() in setOf("1", "true") }
            listOf("security", "tls", "type", "headerType", "insecure", "allowInsecure", "skip-cert-verify")
                .forEach(options::remove)
            val migrated = server.copy(
                allowInsecure = server.allowInsecure || legacyAllowInsecure,
                protocolOptions = options,
            )
            if (migrated != server) changed = true
            migrated
        }
        return if (changed) store.copy(servers = servers) else store
    }

    fun replace(store: ServerStore) {
        synchronized(lock) {
            val wasUnreadable = unreadable
            if (wasUnreadable) {
                val suffix = ".corrupt-${System.currentTimeMillis()}"
                listOf(file, backup).filter { it.exists() }.forEach {
                    Files.copy(it.toPath(), File(it.path + suffix).toPath())
                }
            }
            unreadable = false
            try {
                mutate(force = true) { store }
                recoveryMessage = null
            } catch (error: Exception) {
                unreadable = wasUnreadable
                throw error
            }
        }
    }

    private fun writeAtomic(target: File, content: String) {
        val tmp = File(target.path + ".tmp")
        FileOutputStream(tmp).use { output ->
            output.write(content.toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        try {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun mutate(force: Boolean = false, block: (ServerStore) -> ServerStore) {
        synchronized(lock) {
            check(!unreadable) { recoveryMessage.orEmpty() }
            val changed = block(_store.value)
            val next = changed.copy(groupOrder = changed.orderedGroups.map { it.key })
            if (!force && next == _store.value && file.exists()) return
            file.parentFile?.mkdirs()
            // Back up the decoded state, never a damaged primary file.
            writeAtomic(backup, json.encodeToString(ServerStore.serializer(), _store.value))
            writeAtomic(file, json.encodeToString(ServerStore.serializer(), next))
            _store.value = next
        }
    }
}
