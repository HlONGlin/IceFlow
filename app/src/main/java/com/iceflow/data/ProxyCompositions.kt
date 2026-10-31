package com.iceflow.data

import com.iceflow.data.model.*
import java.net.URI

object ProxyCompositions {
    fun resolve(profile: ServerProfile, store: ServerStore?): List<ServerProfile> {
        val config = requireNotNull(profile.composition)
        require(profile.name.isNotBlank()) { "请输入配置名称" }
        require(config.serverIds.size >= 2) { "请至少添加两个服务器配置" }
        require(config.serverIds.distinct().size == config.serverIds.size) { "不能重复添加同一个服务器配置" }
        require(profile.id !in config.serverIds) { "配置不能引用自身" }
        if (config.kind == ProxyCompositionKind.BALANCE) {
            require(config.intervalSeconds in 10..86400) { "检查间隔应为 10–86400 秒" }
            require(config.testUrl.isBlank() || runCatching {
                URI(config.testUrl.trim()).let { it.scheme in setOf("http", "https") && !it.host.isNullOrBlank() }
            }.getOrDefault(false)) { "请输入有效的 HTTP / HTTPS 检查地址" }
        }
        return config.serverIds.map { id ->
            val server = requireNotNull(store?.servers?.firstOrNull { it.id == id }) { "引用的服务器已删除，请重新添加" }
            require(server.composition == null) { "请选择普通服务器配置，避免嵌套或循环引用" }
            server
        }
    }
}
