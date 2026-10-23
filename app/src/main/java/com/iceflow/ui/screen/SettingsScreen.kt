package com.iceflow.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.iceflow.data.model.AppSettings
import com.iceflow.data.model.RoutingMode
import com.iceflow.data.model.AppTheme

private val IPV4 = Regex("""^((25[0-5]|2[0-4]\d|1?\d?\d)\.){3}(25[0-5]|2[0-4]\d|1?\d?\d)$""")
internal fun isIpLiteral(s: String): Boolean = IPV4.matches(s) || (s.contains(':') && s.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it == ':' || it == '.' })

@Composable
fun SettingsScreen(settings: AppSettings, xrayVersion: String?, coreVersion: String?, appVersion: String, onUpdate: ((AppSettings) -> AppSettings) -> Unit, onOpenLogs: () -> Unit = {}, onOpenAppSelector: () -> Unit = {}) {
    var port by rememberSaveable { mutableStateOf(settings.socksPort.toString()) }
    var dns by rememberSaveable { mutableStateOf(settings.remoteDns) }
    var testUrl by rememberSaveable { mutableStateOf(settings.testUrl) }
    var authDialog by remember { mutableStateOf<String?>(null) }
    val portValid = port.toIntOrNull()?.let { it in 1024..65535 } == true
    val dnsValid = isIpLiteral(dns.trim())
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("设置", style = MaterialTheme.typography.titleLarge)
        SettingsSection {
            Text("通用", style = MaterialTheme.typography.titleMedium)
            Text("主题", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                AppTheme.entries.forEach { theme ->
                    FilterChip(selected = settings.theme == theme, onClick = { onUpdate { it.copy(theme = theme) } }, label = { Text(themeLabel(theme)) })
                }
            }
            SettingAction("语言", if (settings.language == "zh-CN") "简体中文" else settings.language) { onUpdate { it.copy(language = "zh-CN") } }
        }
        SettingsSection {
            Text("代理与路由", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(port, { port = it.filter(Char::isDigit).take(5); port.toIntOrNull()?.takeIf { p -> p in 1024..65535 }?.let { p -> onUpdate { s -> s.copy(socksPort = p) } } }, label = { Text("本地 SOCKS5 端口") }, isError = !portValid, supportingText = { if (!portValid) Text("范围 1024–65535") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            ToggleRow("启用 SOCKS5 路由", "允许其他应用使用本地代理", settings.socksEnabled) { value -> onUpdate { it.copy(socksEnabled = value) } }
            ToggleRow("SOCKS5 UDP", "允许 UDP 转发", settings.socksUdp) { value -> onUpdate { it.copy(socksUdp = value) } }
            SettingAction("SOCKS5 用户名", settings.socksUsername.ifBlank { "未设置" }) { authDialog = "username" }
            SettingAction("SOCKS5 密码", if (settings.socksPassword.isBlank()) "未设置" else "已设置") { authDialog = "password" }
            Text("路由模式", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { RoutingMode.entries.forEach { mode -> FilterChip(selected = settings.routingMode == mode, onClick = { onUpdate { it.copy(routingMode = mode) } }, label = { Text(mode.label) }) } }
        }
        SettingsSection {
            Text("DNS 与网络", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(dns, { dns = it; if (isIpLiteral(it.trim())) onUpdate { s -> s.copy(remoteDns = it.trim(), dnsServers = listOf(it.trim())) } }, label = { Text("远程 DNS") }, isError = !dnsValid, supportingText = { if (!dnsValid) Text("请输入 IP 地址") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
            ToggleRow("绕过局域网", "局域网与保留地址直连", settings.bypassLan) { value -> onUpdate { it.copy(bypassLan = value) } }
            ToggleRow("虚拟 DNS (Fake-IP)", "关闭后使用 DNS over TCP", settings.fakeDns) { value -> onUpdate { it.copy(fakeDns = value) } }
            ToggleRow("DNS 代理", "DNS 请求经代理转发", settings.dnsProxyEnabled) { value -> onUpdate { it.copy(dnsProxyEnabled = value) } }
            ToggleRow("启用 IPv6", "允许 IPv6 流量通过代理", settings.ipv6Enabled) { value -> onUpdate { it.copy(ipv6Enabled = value) } }
        }
        SettingsSection {
            Text("应用与测速", style = MaterialTheme.typography.titleMedium)
            ToggleRow("应用路由", "启用所选应用的绕过或代理策略", settings.appRoutingEnabled) { value -> onUpdate { it.copy(appRoutingEnabled = value) } }
            ToggleRow("分应用代理", "仅所选应用通过代理", settings.appProxyMode) { value -> onUpdate { it.copy(appRoutingEnabled = value, appProxyMode = value, proxyAppsOnly = value) } }
            OutlinedButton(onClick = onOpenAppSelector, modifier = Modifier.fillMaxWidth()) { Text("选择应用 · ${settings.bypassApps.size}") }
            OutlinedTextField(testUrl, { testUrl = it; runCatching { com.iceflow.data.model.RoutingRules.validateTestUrl(it) }.onSuccess { valid -> onUpdate { s -> s.copy(testUrl = valid) } } }, label = { Text("测速 URL") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
            ToggleRow("显示流量统计", "在节点卡片显示累计流量", settings.showTrafficStats) { value -> onUpdate { it.copy(showTrafficStats = value) } }
        }
        SettingsSection {
            Text("运行日志", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(0, 1, 3, 7, 30).forEach { days -> FilterChip(selected = settings.logRetentionDays == days, onClick = { onUpdate { it.copy(logRetentionDays = days) } }, label = { Text(if (days == 0) "永久" else "${days}天") }) } }
            Button(onClick = onOpenLogs, modifier = Modifier.fillMaxWidth()) { Text("查看运行日志") }
        }
        SettingsSection { Text("关于", style = MaterialTheme.typography.titleMedium); Text("IceFlow $appVersion"); Text("xray-core: ${xrayVersion ?: "未集成"}"); Text("Rust 核心: ${coreVersion ?: "未集成"}") }
    }
    authDialog?.let { kind ->
        val title = if (kind == "username") "SOCKS5 用户名" else "SOCKS5 密码"
        var value by rememberSaveable(kind) { mutableStateOf(if (kind == "username") settings.socksUsername else settings.socksPassword) }
        AlertDialog(onDismissRequest = { authDialog = null }, title = { Text(title) }, text = { OutlinedTextField(value, { value = it }, singleLine = true, visualTransformation = if (kind == "password") PasswordVisualTransformation() else VisualTransformation.None, modifier = Modifier.fillMaxWidth()) }, confirmButton = { TextButton(onClick = { onUpdate { s -> if (kind == "username") s.copy(socksUsername = value) else s.copy(socksPassword = value) }; authDialog = null }) { Text("保存") } }, dismissButton = { TextButton(onClick = { authDialog = null }) { Text("取消") } })
    }
}

@Composable private fun SettingAction(title: String, value: String, onClick: () -> Unit) { TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 8.dp)) { Text(title, modifier = Modifier.weight(1f)); Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
@Composable private fun SettingsSection(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp), content = content)
}
private fun themeLabel(theme: AppTheme): String = when (theme) {
    AppTheme.LIGHT_BLUE -> "浅蓝"
    AppTheme.MINT_GREEN -> "薄荷绿"
    AppTheme.SAKURA_PINK -> "樱花粉"
    AppTheme.PEACH_ORANGE -> "蜜桃橙"
    AppTheme.OBSIDIAN_BLACK -> "曜石黑"
}
@Composable private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.bodyMedium); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Switch(checked, onChange) } }
