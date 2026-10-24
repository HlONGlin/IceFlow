package com.iceflow.data

import com.iceflow.data.model.AppSettings
import com.iceflow.data.model.ServerStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BackupDocument(
    val format: String = "IceFlow",
    val version: Int = 1,
    val store: ServerStore,
    val settings: AppSettings,
)

object BackupCodec {
    const val MAX_BYTES = 16 * 1024 * 1024
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true; prettyPrint = true }

    fun encode(store: ServerStore, settings: AppSettings): String =
        json.encodeToString(BackupDocument.serializer(), BackupDocument(store = store, settings = settings))
            .also { require(it.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "备份不能超过 16 MiB" } }

    fun decode(text: String): BackupDocument {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "备份不能超过 16 MiB" }
        val backup = json.decodeFromString<BackupDocument>(text)
        require(backup.format == "IceFlow" && backup.version == 1) { "不支持的备份格式或版本" }
        backup.settings.validate()
        val store = backup.store
        val ids = store.servers.map { it.id }
        require(ids.all { it.isNotBlank() } && ids.distinct().size == ids.size) { "节点 ID 无效或重复" }
        val groups = store.localGroups.map { it.id } + store.subscriptions.map { it.id }
        require(groups.all { it.isNotBlank() } && groups.distinct().size == groups.size) { "分组 ID 无效或重复" }
        require(store.selectedId == null || store.selectedId in ids) { "所选节点不存在" }
        require(store.selectedGroupId == null || store.selectedGroupId in groups) { "所选分组不存在" }
        store.servers.forEach { node ->
            NodeEditing.validate(node)
            require(node.groupId == null || store.localGroups.any { it.id == node.groupId }) { "节点所属分组不存在" }
            require(node.subscriptionId == null || store.subscriptions.any { it.id == node.subscriptionId }) { "节点所属订阅不存在" }
        }
        store.subscriptions.forEach {
            com.iceflow.data.repository.SubscriptionFetcher.requireHttpsUri(it.url)
            require(it.updateIntervalHours in 0..720) { "订阅更新间隔无效" }
        }
        // Deleted subscription nodes may leave chain references. Preserve them so
        // connection validation still asks the user to repair the original chain.
        return backup
    }
}
