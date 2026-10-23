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

/** 节点与订阅的持久化（JSON 文件，原子写入）。 */
class ServerRepository(private val file: File) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val lock = Any()
    private val _store = MutableStateFlow(load())
    val store: StateFlow<ServerStore> = _store.asStateFlow()

    val selected: ServerProfile? get() = _store.value.selected

    fun addServers(servers: List<ServerProfile>) = mutate { s ->
        s.copy(servers = s.servers + servers, selectedId = s.selectedId ?: servers.firstOrNull()?.id)
    }

    fun updateServer(server: ServerProfile) = mutate { s -> s.copy(servers = s.servers.map { if (it.id == server.id) server else it }) }

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

    fun saveGroup(config: GroupConfiguration) = mutate { s ->
        require(config.validationError() == null) { config.validationError().orEmpty() }
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
            if (s.localGroups.any { it.id == config.id }) s.localGroups.map { if (it.id == config.id) it.copy(name = config.name.trim(), sort = config.sort, frontNodeId = config.frontNodeId, landingNodeId = config.landingNodeId) else it }
            else s.localGroups + LocalGroup(config.id, config.name.trim(), config.sort, config.frontNodeId, config.landingNodeId)
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

    /** 设置指定分组（本地分组或订阅）的排序方式；groupId 为 null 时表示未分组节点，写入 ungroupedSort。 */
    fun setGroupSort(groupId: String?, sort: com.iceflow.data.model.GroupSort) = mutate { s ->
        if (groupId == null) return@mutate s.copy(ungroupedSort = sort)
        s.copy(
            localGroups = s.localGroups.map { if (it.id == groupId) it.copy(sort = sort) else it },
            subscriptions = s.subscriptions.map { if (it.id == groupId) it.copy(sort = sort) else it },
        )
    }

    /** 手动调序：将 orderedIds 按出现顺序写回 sortOrder，保证后续"原始顺序"排序遵循该次序。 */
    fun reorderServers(orderedIds: List<String>) = mutate { s ->
        val indexOf = orderedIds.withIndex().associate { (index, id) -> id to index.toLong() }
        s.copy(servers = s.servers.map { server -> indexOf[server.id]?.let { server.copy(sortOrder = it) } ?: server })
    }

    /** 在当前分组内将某节点与其上一个（up=true）或下一个（up=false）节点交换 sortOrder，实现原地上移/下移。 */
    fun moveServerWithinGroup(serverId: String, groupId: String?, up: Boolean) = mutate { s ->
        val siblings = s.servers.filter { server ->
            if (groupId == null) server.subscriptionId == null && server.groupId == null
            else server.groupId == groupId || server.subscriptionId == groupId
        }.sortedBy { it.sortOrder }
        val index = siblings.indexOfFirst { it.id == serverId }
        val targetIndex = if (up) index - 1 else index + 1
        if (index < 0 || targetIndex < 0 || targetIndex >= siblings.size) return@mutate s
        val current = siblings[index]
        val target = siblings[targetIndex]
        val currentOrder = current.sortOrder
        val targetOrder = target.sortOrder
        s.copy(servers = s.servers.map { server ->
            when (server.id) {
                current.id -> server.copy(sortOrder = targetOrder)
                target.id -> server.copy(sortOrder = currentOrder)
                else -> server
            }
        })
    }

    fun selectGroup(groupId: String?) = mutate { it.copy(selectedGroupId = groupId) }

    fun setGroupSort(groupId: String?, sort: GroupSort) = mutate { s ->
        if (groupId == null) s.copy(localSort = sort)
        else s.copy(
            localGroups = s.localGroups.map { if (it.id == groupId) it.copy(sort = sort) else it },
            subscriptions = s.subscriptions.map { if (it.id == groupId) it.copy(sort = sort) else it },
        )
    }

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
    fun upsertSubscription(url: String, name: String, servers: List<ServerProfile>, expectedId: String? = null) = mutate { s ->
        val existing = s.subscriptions.firstOrNull { it.url == url && (expectedId == null || it.id == expectedId) }
        if (expectedId != null && existing == null) return@mutate s
        val sub = (existing ?: Subscription(name = name, url = url)).copy(
            updatedAt = System.currentTimeMillis(),
            uploadBytes = parseTrafficHeader(SubscriptionFetcher.lastUserInfo, "upload"),
            downloadBytes = parseTrafficHeader(SubscriptionFetcher.lastUserInfo, "download"),
            downloadedConfigBytes = SubscriptionFetcher.lastDownloadedBytes,
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

    private fun load(): ServerStore = runCatching {
        if (file.exists()) json.decodeFromString<ServerStore>(file.readText()) else ServerStore()
    }.getOrDefault(ServerStore()).let { store ->
        if (store.selectedGroupId == null && store.localGroups.isNotEmpty()) store.copy(selectedGroupId = store.localGroups.first().id) else store
    }

    private fun mutate(block: (ServerStore) -> ServerStore) {
        synchronized(lock) {
            val changed = block(_store.value)
            val next = changed.copy(groupOrder = changed.orderedGroups.map { it.key })
            _store.value = next
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(json.encodeToString(ServerStore.serializer(), next))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }
    }
}
