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
            putBoolean("http_proxy_enabled", next.httpProxyEnabled)
            putBoolean("dns_proxy_enabled", next.dnsProxyEnabled)
            putString("theme", next.theme.name)
        }
        _settings.value = next
    }

    private fun load(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            socksPort = prefs.getInt(KEY_SOCKS_PORT, d.socksPort),
            remoteDns = prefs.getString(KEY_REMOTE_DNS, d.remoteDns) ?: d.remoteDns,
            bypassLan = prefs.getBoolean(KEY_BYPASS_LAN, d.bypassLan),
            fakeDns = prefs.getBoolean(KEY_FAKE_DNS, d.fakeDns),
            testUrl = prefs.getString("test_url", d.testUrl) ?: d.testUrl,
            routingRules = prefs.getString("routing_rules", null)?.let { runCatching { RoutingRules.decode(it, preserveIds = true) }.getOrNull() } ?: RoutingRules.defaults(prefs.getBoolean(KEY_BYPASS_LAN, true)),
            bypassApps = prefs.getStringSet("bypass_apps", emptySet())?.toList().orEmpty(),
            proxyAppsOnly = prefs.getBoolean("proxy_apps_only", false),
            ipv6Enabled = prefs.getBoolean("ipv6_enabled", true),
            dnsServers = prefs.getString("dns_servers", d.dnsServers.joinToString("\n"))!!.lines().map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { d.dnsServers },
            socksEnabled = prefs.getBoolean("socks_enabled", true),
            socksUdp = prefs.getBoolean("socks_udp", true),
            httpProxyEnabled = prefs.getBoolean("http_proxy_enabled", false),
            dnsProxyEnabled = prefs.getBoolean("dns_proxy_enabled", true),
            theme = runCatching { AppTheme.valueOf(prefs.getString("theme", d.theme.name)!!) }.getOrDefault(d.theme),
        )
    }

    private companion object {
        const val KEY_SOCKS_PORT = "socks_port"
        const val KEY_REMOTE_DNS = "remote_dns"
        const val KEY_BYPASS_LAN = "bypass_lan"
        const val KEY_FAKE_DNS = "fake_dns"
    }
}
