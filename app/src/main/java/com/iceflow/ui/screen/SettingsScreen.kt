package com.iceflow.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.iceflow.data.model.AppSettings
import com.iceflow.ui.component.GlassCard

private val IPV4 = Regex("""^((25[0-5]|2[0-4]\d|1?\d?\d)\.){3}(25[0-5]|2[0-4]\d|1?\d?\d)$""")

/** 仅接受 IP 字面量（VpnService.addDnsServer 不接受域名）。 */
internal fun isIpLiteral(s: String): Boolean =
    IPV4.matches(s) || (s.contains(':') && s.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it == ':' || it == '.' })

@Composable
fun SettingsScreen(
    settings: AppSettings,
    xrayVersion: String?,
    coreVersion: String?,
    appVersion: String,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
) {
    var port by rememberSaveable { mutableStateOf(settings.socksPort.toString()) }
    var dns by rememberSaveable { mutableStateOf(settings.remoteDns) }
    val portValid = port.toIntOrNull()?.let { it in 1024..65535 } == true
    val dnsValid = isIpLiteral(dns.trim())

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("设置", style = MaterialTheme.typography.titleLarge)
        GlassCard(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = port,
                onValueChange = { v ->
                    port = v.filter(Char::isDigit).take(5)
                    port.toIntOrNull()?.takeIf { it in 1024..65535 }?.let { p -> onUpdate { it.copy(socksPort = p) } }
                },
                label = { Text("本地 SOCKS 端口") },
                isError = !portValid,
                supportingText = { if (!portValid) Text("范围 1024–65535") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = dns,
                onValueChange = { v ->
                    dns = v
                    if (isIpLiteral(v.trim())) onUpdate { it.copy(remoteDns = v.trim()) }
                },
                label = { Text("远程 DNS") },
                isError = !dnsValid,
                supportingText = { if (!dnsValid) Text("请输入 IP 地址") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            ToggleRow("绕过局域网", "局域网与保留地址直连", settings.bypassLan) { v -> onUpdate { it.copy(bypassLan = v) } }
            ToggleRow("虚拟 DNS (Fake-IP)", "关闭后改为 DNS over TCP 经代理", settings.fakeDns) { v -> onUpdate { it.copy(fakeDns = v) } }
            Text(
                "设置在下次连接时生效",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        GlassCard(Modifier.fillMaxWidth()) {
            Text("关于", style = MaterialTheme.typography.titleMedium)
            Text("IceFlow $appVersion", style = MaterialTheme.typography.bodyMedium)
            Text("xray-core: ${xrayVersion ?: "未集成"}", style = MaterialTheme.typography.bodyMedium)
            Text("Rust 核心: ${coreVersion ?: "未集成"}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
