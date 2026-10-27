package com.iceflow.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class Protocol(val label: String) {
    VMESS("VMess"),
    VLESS("VLESS"),
    TROJAN("Trojan"),
    SHADOWSOCKS("SS"),
    HYSTERIA("Hysteria"),
    HYSTERIA2("Hysteria2"),
    TUIC("TUIC"),
    WIREGUARD("WireGuard"),
    SHADOWSOCKSR("SSR"),
    HTTP("HTTP"),
    SOCKS("SOCKS5"),
    MIERU("mieru"),
    ANYTLS("AnyTLS"),
    SUDOKU("Sudoku"),
}

/**
 * 单个节点配置。通用连接字段与各协议扩展参数分别持久化，
 * 由 [com.iceflow.data.XrayConfigBuilder] 生成 Xray 路由及原生适配器配置。
 */
@Serializable
data class ServerProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val protocol: Protocol,
    val address: String,
    val port: Int,
    /** VMess / VLESS / TUIC v5 用户 ID */
    val uuid: String = "",
    /** 协议密码、Hysteria 认证内容或 TUIC v4 Token */
    val password: String = "",
    val alterId: Int = 0,
    /** VMess 加密方式或 Shadowsocks method */
    val security: String = "auto",
    val flow: String = "",
    val encryption: String = "none",
    /** 传输方式：tcp / ws / grpc / httpupgrade / xhttp */
    val network: String = "tcp",
    val headerType: String = "none",
    val host: String = "",
    val path: String = "",
    val serviceName: String = "",
    /** 传输层安全：none / tls / reality */
    val tls: String = "none",
    val sni: String = "",
    val alpn: String = "",
    val fingerprint: String = "",
    val publicKey: String = "",
    val shortId: String = "",
    val spiderX: String = "",
    val allowInsecure: Boolean = false,
    val username: String = "",
    /** Protocol-specific options, persisted independently of Xray transport fields. */
    val protocolOptions: Map<String, String> = emptyMap(),
    /** Original Clash node options, including nested transport/plugin settings. */
    val clashConfig: kotlinx.serialization.json.JsonObject? = null,
    val subscriptionId: String? = null,
    /** 本地分组 ID；订阅节点使用 subscriptionId。 */
    val groupId: String? = null,
    /** 延迟（毫秒）；null 表示未测试，-1 表示超时/失败 */
    val latencyMs: Int? = null,
    val uploadBytes: Long = 0,
    val downloadBytes: Long = 0,
)

@Serializable
data class Subscription(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val url: String,
    val updatedAt: Long = 0,
    val uploadBytes: Long = 0,
    val downloadBytes: Long = 0,
    val updateIntervalHours: Int = 24,
    val downloadedConfigBytes: Long = 0,
    val sort: GroupSort = GroupSort.ORIGINAL,
    val frontNodeId: String? = null,
    val landingNodeId: String? = null,
)

@Serializable
data class LocalGroup(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val sort: GroupSort = GroupSort.ORIGINAL,
    val frontNodeId: String? = null,
    val landingNodeId: String? = null,
)

@Serializable
enum class GroupSort(val label: String, val stringKey: String) {
    ORIGINAL("原始", "groups.sort.original"),
    NAME("按名称", "groups.sort.name"),
    LATENCY("按延迟", "groups.sort.latency");

    fun apply(servers: List<ServerProfile>): List<ServerProfile> = when (this) {
        ORIGINAL -> servers
        NAME -> servers.sortedBy { it.name.lowercase() }
        LATENCY -> servers.sortedWith(compareBy(
            { when { it.latencyMs == null -> 2; it.latencyMs < 0 -> 1; else -> 0 } },
            { it.latencyMs ?: 0 },
        ))
    }
}

data class GroupConfiguration(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val subscription: Boolean = false,
    val url: String = "",
    val intervalHours: Int = 24,
    val sort: GroupSort = GroupSort.ORIGINAL,
    val frontNodeId: String? = null,
    val landingNodeId: String? = null,
) {
    fun validationError(): String? = when {
        name.isBlank() -> "请输入分组名称"
        frontNodeId != null && frontNodeId == landingNodeId -> "前置节点和落地节点不能相同"
        subscription && !runCatching { SubscriptionUrl.requireHttpsUri(url.trim()) }.isSuccess -> "请输入有效的 HTTPS 订阅链接"
        subscription && intervalHours !in 0..720 -> "更新间隔应为 0–720 小时（0 为不自动更新）"
        else -> null
    }
}

@Serializable
data class ServerStore(
    val servers: List<ServerProfile> = emptyList(),
    val selectedId: String? = null,
    val subscriptions: List<Subscription> = emptyList(),
    val localGroups: List<LocalGroup> = emptyList(),
    val selectedGroupId: String? = null,
    val groupOrder: List<String> = emptyList(),
    /** 未分组节点（不属于任何本地分组或订阅）的排序方式，因为它没有对应的分组实体可以挂载 sort 字段。 */
    val localSort: GroupSort = GroupSort.ORIGINAL,
) {
    val selected: ServerProfile?
        get() = servers.firstOrNull { it.id == selectedId }

    val orderedGroups: List<NodeGroup>
        get() {
            val groups = localGroups.map { NodeGroup(it.id, it.id, it.name) } +
                subscriptions.map { NodeGroup(it.id, it.id, it.name) }
            val byKey = groups.associateBy { it.key }
            return (groupOrder + groups.map { it.key }).distinct().mapNotNull(byKey::get)
        }
}
@Serializable
enum class AppTheme { LIGHT_BLUE, MINT_GREEN, SAKURA_PINK, PEACH_ORANGE, OBSIDIAN_BLACK }
@Serializable
enum class NightMode(val label: String, val stringKey: String) {
    FOLLOW_SYSTEM("跟随系统", "settings.night.followSystem"),
    ENABLED("启用", "settings.night.enabled"),
    DISABLED("禁用", "settings.night.disabled"),
    AUTO("自动", "settings.night.auto")
}
@Serializable
enum class TextSize(val label: String, val scale: Float, val stringKey: String) {
    SMALL("小", 1.0f, "settings.text.small"),
    MEDIUM("中", 1.15f, "settings.text.medium"),
    LARGE("大", 1.3f, "settings.text.large"),
}
@Serializable
enum class RoutingMode(val label: String, val stringKey: String) {
    RULE("规则", "routes.rule"),
    GLOBAL("全局", "routes.global"),
    DIRECT("直连", "routes.direct")
}

/** 界面语言；持久化为 [tag]，旧版本仅有的 zh-CN 仍然兼容。 */
@Serializable
enum class AppLanguage(val tag: String) {
    SIMPLIFIED_CHINESE("zh-CN"),
    TRADITIONAL_CHINESE("zh-TW"),
    ENGLISH("en"),
    ;

    companion object {
        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag.equals(tag, ignoreCase = true) } ?: SIMPLIFIED_CHINESE
    }
}
data class NodeGroup(val key: String, val id: String?, val name: String)

@Serializable
data class AppSettings(
    val socksPort: Int = 10808,
    val remoteDns: String = "1.1.1.1",
    val domesticDns: String = "223.5.5.5",
    val bypassLan: Boolean = true,
    /** true：tun2proxy 虚拟 DNS（Fake-IP）；false：DNS over TCP 经代理转发 */
    val fakeDns: Boolean = true,
    val testUrl: String = "https://www.google.com/generate_204",
    val routingRules: List<RoutingRule> = RoutingRules.defaults(),
    val routingMode: RoutingMode = RoutingMode.RULE,
    val bypassApps: List<String> = emptyList(),
    val proxyAppsOnly: Boolean = false,
    val ipv6Enabled: Boolean = true,
    val dnsServers: List<String> = listOf("1.1.1.1"),
    val socksEnabled: Boolean = true,
    val socksUdp: Boolean = true,
    val socksUsername: String = "",
    val socksPassword: String = "",
    val dnsProxyEnabled: Boolean = true,
    val theme: AppTheme = AppTheme.LIGHT_BLUE,
    val nightMode: NightMode = NightMode.FOLLOW_SYSTEM,
    val textSize: TextSize = TextSize.MEDIUM,
    val showTrafficStats: Boolean = false,
    /** 是否在节点卡片显示延迟；关闭时不显示延迟与未测速提示 */
    val showNodeLatency: Boolean = false,
    /** 是否在主页显示公网 IP；默认关闭 */
    val showPublicIp: Boolean = false,
    val logRetentionDays: Int = 3,
    val appRoutingEnabled: Boolean = false,
    val appProxyMode: Boolean = false,
    val language: String = "zh-CN",
) {
    fun hasSameConnection(other: AppSettings): Boolean = copy(
        theme = other.theme, nightMode = other.nightMode, language = other.language,
        showTrafficStats = other.showTrafficStats, logRetentionDays = other.logRetentionDays,
        showNodeLatency = other.showNodeLatency, showPublicIp = other.showPublicIp,
        testUrl = other.testUrl,
    ) == other

    fun validate() {
        require(socksPort in 1024..65535) { "SOCKS 端口应为 1024–65535" }
        require(dnsServers.isNotEmpty()) { "至少输入一个 DNS 地址" }
        (dnsServers + remoteDns + domesticDns).forEach { value ->
            val v4 = value.split('.').let { parts -> parts.size == 4 && parts.all { part -> part.isNotEmpty() && part.all(Char::isDigit) && part.toIntOrNull() in 0..255 } }
            val v6 = ':' in value && value.matches(Regex("[0-9a-fA-F:.]+")) && runCatching { java.net.InetAddress.getByName(value) }.isSuccess
            require(v4 || v6) { "DNS 必须是 IPv4 或 IPv6 地址：$value" }
        }
        RoutingRules.validateTestUrl(testUrl)
        RoutingRules.encode(routingRules)
        require(logRetentionDays in 0..365) { "日志保留天数应为 0–365" }
    }
}
