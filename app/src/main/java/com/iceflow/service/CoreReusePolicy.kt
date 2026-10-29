package com.iceflow.service

/**
 * 决定重连时是否复用运行中的核心。
 *
 * 只有当核心仍在运行、节点未变且新构建的配置与当前核心实际应用的配置完全一致时
 * 才允许复用；否则必须停止核心并按新配置重启，保证公共 SOCKS 端口/认证等
 * 设置变更能立即生效。
 */
internal object CoreReusePolicy {
    fun shouldReuse(coreRunning: Boolean, hasTunnel: Boolean, appliedConfig: String?, newConfig: String): Boolean =
        coreRunning && hasTunnel && appliedConfig != null && appliedConfig == newConfig
}
