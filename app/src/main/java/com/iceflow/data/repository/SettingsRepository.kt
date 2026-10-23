package com.iceflow.data.repository

import android.content.Context
import androidx.core.content.edit
import com.iceflow.data.model.AppSettings
import com.iceflow.data.model.RoutingRules
import com.iceflow.data.model.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        RoutingRules.validateTestUrl(next.testUrl)
        val encodedRules = RoutingRules.encode(next.routingRules)
        prefs.edit {
            putInt(KEY_SOCKS_PORT, next.socksPort)
            putString(KEY_REMOTE_DNS, next.remoteDns)
            putBoolean(KEY_BYPASS_LAN, next.bypassLan)
            putBoolean(KEY_FAKE_DNS, next.fakeDns)
            putString("test_url", next.testUrl)
            putString("routing_rules", encodedRules)
            putStringSet("bypass_apps", next.bypassApps.toSet())
            putBoolean("proxy_apps_only", next.proxyAppsOnly)
            putBoolean("ipv6_enabled", next.ipv6Enabled)
            putString("dns_servers", next.dnsServers.joinToString("\n"))
            putBoolean("socks_enabled", next.socksEnabled)
            putBoolean("socks_udp", next.socksUdp)
            putString("socks_username", next.socksUsername)
            putString("socks_password", next.socksPassword)
            putBoolean("dns_proxy_enabled", next.dnsProxyEnabled)
            putString("theme", next.theme.name)
            putString("routing_mode", next.routingMode.name)
            putBoolean("show_traffic_stats", next.showTrafficStats)
            putInt("log_retention_days", next.logRetentionDays)
            putBoolean("app_proxy_mode", next.appProxyMode)
            putBoolean("app_routing_enabled", next.appRoutingEnabled)
        }
        _settings.value = next
    }

    private fun load(): AppSettings {
        val d = AppSettings()
        val bypassApps = prefs.getStringSet("bypass_apps", emptySet())?.toList().orEmpty()
        val legacyProxyAppsOnly = prefs.getBoolean("proxy_apps_only", false)
        return AppSettings(
            socksPort = prefs.getInt(KEY_SOCKS_PORT, d.socksPort),
            remoteDns = prefs.getString(KEY_REMOTE_DNS, d.remoteDns) ?: d.remoteDns,
            bypassLan = prefs.getBoolean(KEY_BYPASS_LAN, d.bypassLan),
            fakeDns = prefs.getBoolean(KEY_FAKE_DNS, d.fakeDns),
            testUrl = prefs.getString("test_url", d.testUrl) ?: d.testUrl,
            routingRules = prefs.getString("routing_rules", null)?.let { runCatching { RoutingRules.decode(it, preserveIds = true) }.getOrNull() } ?: RoutingRules.defaults(prefs.getBoolean(KEY_BYPASS_LAN, true)),
            bypassApps = bypassApps,
            proxyAppsOnly = legacyProxyAppsOnly,
            ipv6Enabled = prefs.getBoolean("ipv6_enabled", true),
            dnsServers = prefs.getString("dns_servers", d.dnsServers.joinToString("\n"))!!.lines().map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { d.dnsServers },
            socksEnabled = prefs.getBoolean("socks_enabled", true),
            socksUdp = prefs.getBoolean("socks_udp", true),
            socksUsername = prefs.getString("socks_username", "") ?: "",
            socksPassword = prefs.getString("socks_password", "") ?: "",
            dnsProxyEnabled = prefs.getBoolean("dns_proxy_enabled", true),
            theme = runCatching { AppTheme.valueOf(prefs.getString("theme", d.theme.name)!!) }.getOrDefault(d.theme),
            routingMode = runCatching { com.iceflow.data.model.RoutingMode.valueOf(prefs.getString("routing_mode", d.routingMode.name)!!) }.getOrDefault(d.routingMode),
            showTrafficStats = prefs.getBoolean("show_traffic_stats", false),
            logRetentionDays = prefs.getInt("log_retention_days", 3),
            appProxyMode = prefs.getBoolean("app_proxy_mode", legacyProxyAppsOnly),
            appRoutingEnabled = prefs.getBoolean("app_routing_enabled", legacyProxyAppsOnly || bypassApps.isNotEmpty()),
        )
    }

    private companion object {
        const val KEY_SOCKS_PORT = "socks_port"
        const val KEY_REMOTE_DNS = "remote_dns"
        const val KEY_BYPASS_LAN = "bypass_lan"
        const val KEY_FAKE_DNS = "fake_dns"
    }
}
