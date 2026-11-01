package com.iceflow.data.model

import com.iceflow.data.ProxyCompositions
import java.security.MessageDigest

data class RoutingOutboundTarget(
    val id: String,
    val tag: String,
    val group: Boolean,
    val members: List<List<ServerProfile>>,
    val strategy: LoadBalanceStrategy? = null,
    val testUrl: String = "",
    val intervalSeconds: Int = 300,
)

object RoutingOutboundTargets {
    fun resolve(id: String, store: ServerStore?): RoutingOutboundTarget? {
        val source = store ?: return null
        val nodes = source.servers.associateBy { it.id }
        fun path(node: ServerProfile): List<ServerProfile>? = runCatching {
            if (node.composition != null) return@runCatching ProxyCompositions.resolve(node, source)
            val local = source.localGroups.firstOrNull { it.id == node.groupId }
            val subscription = source.subscriptions.firstOrNull { it.id == node.subscriptionId }
            listOfNotNull(local?.frontNodeId ?: subscription?.frontNodeId, node.id, local?.landingNodeId ?: subscription?.landingNodeId)
                .map { nodes[it] ?: error("引用的节点已失效 / Referenced node is missing") }
                .also { require(it.map(ServerProfile::id).distinct().size == it.size) }
        }.getOrNull()

        val isNode = id.startsWith("node:")
        val rawId = when {
            isNode -> id.removePrefix("node:")
            id.startsWith("group:") -> id.removePrefix("group:")
            else -> return null
        }
        val tag = "rule-target-" + MessageDigest.getInstance("SHA-256").digest(id.toByteArray())
            .take(16).joinToString("") { "%02x".format(it) }
        if (isNode) {
            val selected = nodes[rawId] ?: return null
            val composition = selected.composition
            if (composition?.kind == ProxyCompositionKind.BALANCE) {
                val members = ProxyCompositions.resolve(selected, source).map { path(it) ?: return null }
                return RoutingOutboundTarget(id, tag, true, members, composition.strategy, composition.testUrl, composition.intervalSeconds)
                    .takeIf { it.members.size >= 2 }
            }
            return RoutingOutboundTarget(id, tag, false, listOfNotNull(path(selected)))
                .takeIf { it.members.isNotEmpty() }
        }

        val local = source.localGroups.firstOrNull { it.id == rawId }
        val subscription = source.subscriptions.firstOrNull { it.id == rawId }
        if (local == null && subscription == null) return null
        val groupNodes = source.servers.filter { if (local != null) it.groupId == rawId else it.subscriptionId == rawId }
        if (local != null && local.balanceNodeIds.any { selected -> groupNodes.none { it.id == selected } }) return null
        val selectedNodes = if (local?.balanceNodeIds.isNullOrEmpty()) groupNodes else groupNodes.filter { it.id in local!!.balanceNodeIds }
        val members = selectedNodes.map { path(it) ?: return null }
        if (members.isEmpty()) return null
        if (local?.balanceStrategy != null && members.size < 2) return null
        return RoutingOutboundTarget(
            id, tag, true, members, local?.balanceStrategy ?: LoadBalanceStrategy.ROUND_ROBIN,
            local?.balanceTestUrl.orEmpty(), local?.balanceIntervalSeconds ?: 300,
        )
    }

    fun referenced(settings: AppSettings, store: ServerStore?): List<RoutingOutboundTarget> =
        settings.effectiveRoutingRules.asSequence().filter { it.enabled }.mapNotNull { it.outboundTargetId }
            .distinct().mapNotNull { resolve(it, store) }.toList()
}
