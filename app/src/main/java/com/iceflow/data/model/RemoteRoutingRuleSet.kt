package com.iceflow.data.model

import kotlinx.serialization.Serializable
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.net.URI
import java.security.MessageDigest
import java.util.UUID

@Serializable
enum class RemoteRuleSetFormat { DOMAIN_LIST, IP_LIST, CLASH_CLASSICAL, CLASH_DOMAIN, CLASH_IPCIDR }

@Serializable
data class RemoteRoutingRuleSet(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val url: String,
    val format: RemoteRuleSetFormat,
    val action: RouteAction = RouteAction.PROXY,
    val updateIntervalHours: Int = 24,
    val enabled: Boolean = true,
) {
    fun validate() {
        require(id.matches(Regex("[A-Za-z0-9_-]{1,200}"))) { "无效的规则集 ID / Invalid rule set ID" }
        require(name.isNotBlank() && name.length <= 200) { "规则集名称不能为空或超过 200 字 / Name must be 1–200 characters" }
        val uri = runCatching { URI(url.trim()) }.getOrElse { throw IllegalArgumentException("请输入有效的 HTTPS 规则集地址 / Enter a valid HTTPS rule set URL", it) }
        require(uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null && (uri.port == -1 || uri.port in 1..65535)) { "规则集地址必须是有效的 HTTPS URL / Rule set URL must be a valid HTTPS URL" }
        require(updateIntervalHours in 0..720) { "规则集更新间隔必须为 0–720 小时 / Update interval must be 0–720 hours" }
    }
}

object RemoteRoutingRuleSetParser {
    const val MAX_ENTRIES = 50_000
    const val MAX_BYTES = 4 * 1024 * 1024
    private const val CHUNK_SIZE = 10_000

    fun parse(set: RemoteRoutingRuleSet, text: String): List<RoutingRule> {
        set.validate()
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "规则集内容不能超过 4 MiB / Rule set content must not exceed 4 MiB" }
        val domains = mutableListOf<String>()
        val ips = mutableListOf<String>()
        when (set.format) {
            RemoteRuleSetFormat.DOMAIN_LIST -> text.lines().forEach { line ->
                val value = clean(line) ?: return@forEach
                domains += domain(value)
            }
            RemoteRuleSetFormat.IP_LIST -> text.lines().forEach { line ->
                val value = clean(line) ?: return@forEach
                ips += value
            }
            RemoteRuleSetFormat.CLASH_CLASSICAL -> clashPayload(text).forEach { entry ->
                val parts = entry.split(',').map(String::trim)
                require(parts.size >= 2) { "无效的 Clash classical 规则 / Invalid Clash classical rule: " + entry }
                when (parts[0].uppercase()) {
                    "DOMAIN" -> domains += "full:" + parts[1]
                    "DOMAIN-SUFFIX" -> domains += "domain:" + parts[1]
                    "DOMAIN-KEYWORD" -> domains += "keyword:" + parts[1]
                    "DOMAIN-REGEX" -> domains += "regexp:" + parts[1]
                    "GEOSITE" -> domains += "geosite:" + parts[1].lowercase()
                    "IP-CIDR", "IP-CIDR6" -> ips += parts[1]
                    "GEOIP" -> ips += "geoip:" + parts[1].lowercase()
                    else -> error("Clash classical 格式不支持 " + parts[0] + " 规则 / Unsupported Clash classical rule type: " + parts[0])
                }
            }
            RemoteRuleSetFormat.CLASH_DOMAIN, RemoteRuleSetFormat.CLASH_IPCIDR -> clashPayload(text).forEach { value ->
                if (set.format == RemoteRuleSetFormat.CLASH_DOMAIN) domains += domain(value) else ips += value
            }
        }
        require(domains.size + ips.size <= MAX_ENTRIES) { "单个规则集最多支持 50,000 条规则 / A rule set may contain at most 50,000 entries" }
        domains.forEach { RoutingRule(name = "validation", domains = listOf(it)).validate() }
        ips.forEach { RoutingRule(name = "validation", ips = listOf(it)).validate() }
        val idToken = MessageDigest.getInstance("SHA-256").digest(set.id.toByteArray()).take(12).joinToString("") { "%02x".format(it) }
        val domainRules = domains.distinct().chunked(CHUNK_SIZE).mapIndexed { index, chunk ->
            RoutingRule(id = "remote:" + idToken + ":domain:" + index, name = set.name.take(180) + " domains " + (index + 1), action = set.action, domains = chunk)
        }
        val ipRules = ips.distinct().chunked(CHUNK_SIZE).mapIndexed { index, chunk ->
            RoutingRule(id = "remote:" + idToken + ":ip:" + index, name = set.name.take(180) + " IP " + (index + 1), action = set.action, ips = chunk)
        }
        return domainRules + ipRules
    }

    private fun clean(line: String): String? = line.removePrefix("\uFEFF").substringBefore("#").trim().takeIf(String::isNotEmpty)

    private fun domain(value: String): String {
        val prefix = listOf("domain:", "full:", "keyword:").firstOrNull(value::startsWith).orEmpty()
        val raw = value.removePrefix(prefix).removePrefix("+.").removePrefix("*.").removePrefix(".")
        if (prefix == "keyword:") {
            require(raw.isNotBlank() && raw.none(Char::isWhitespace)) { "无效的域名关键字 / Invalid domain keyword: " + value }
            return prefix + raw
        }
        require(raw.matches(Regex("(?i)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)*[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?"))) { "无效的域名 / Invalid domain: " + value }
        return (prefix.ifEmpty { "domain:" }) + raw
    }

    private fun clashPayload(text: String): List<String> {
        val options = LoaderOptions().apply {
            isAllowDuplicateKeys = false
            maxAliasesForCollections = 0
            nestingDepthLimit = 20
            codePointLimit = MAX_BYTES
        }
        val root = runCatching { Yaml(SafeConstructor(options)).load<Any?>(text) }
            .getOrElse { throw IllegalArgumentException("Clash YAML 解析失败 / Invalid Clash YAML", it) }
        val payload = when (root) {
            is List<*> -> root
            is Map<*, *> -> root["payload"] as? List<*> ?: root["rules"] as? List<*> ?: error("YAML 规则集需要 payload 或 rules 列表 / YAML rule set must contain a payload or rules list")
            else -> error("YAML 规则集需要一个列表 / YAML rule set must be a list")
        }
        require(payload.size <= MAX_ENTRIES) { "单个规则集最多支持 50,000 条规则 / A rule set may contain at most 50,000 entries" }
        return payload.map { it as? String ?: error("规则集条目必须是文本 / Rule set entries must be strings") }.map(String::trim).filter(String::isNotEmpty)
    }
}
