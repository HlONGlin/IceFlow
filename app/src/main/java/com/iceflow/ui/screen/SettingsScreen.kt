package com.iceflow.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.AltRoute
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Brightness4
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.SettingsEthernet
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.DialogProperties
import com.iceflow.data.model.AppSettings
import com.iceflow.data.model.RoutingMode
import com.iceflow.data.model.AppTheme
import com.iceflow.data.model.NightMode
import com.iceflow.data.model.TextSize
import com.iceflow.ui.component.SegmentedSelector
import com.iceflow.ui.theme.IceRadius

private val IPV4 = Regex("""^((25[0-5]|2[0-4]\d|1?\d?\d)\.){3}(25[0-5]|2[0-4]\d|1?\d?\d)$""")
internal fun isIpLiteral(s: String): Boolean = IPV4.matches(s) || (s.contains(':') && s.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it == ':' || it == '.' })

private val IconSize = 20.dp
private val IconSlot = 36.dp

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        letterSpacing = 0.8.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 18.dp, bottom = 5.dp)
    )
}

// 卡片：去除背景色，仅靠分割线分组（iOS 风格）
@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), content = content)
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 52.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
    )
}

@Composable
private fun SettingRow(
    icon: ImageVector? = null,
    title: String,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .defaultMinSize(minHeight = 52.dp)
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(IconSlot), contentAlignment = Alignment.Center) {
            if (icon != null) Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(IconSize))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing != null) { Spacer(Modifier.width(8.dp)); trailing() }
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector? = null, title: String, subtitle: String? = null,
    checked: Boolean, onChange: (Boolean) -> Unit
) {
    SettingRow(icon = icon, title = title, subtitle = subtitle,
        onClick = { onChange(!checked) },
        trailing = { Switch(checked = checked, onCheckedChange = null) }
    )
}

@Composable
private fun SettingAction(
    icon: ImageVector? = null, title: String, value: String, onClick: () -> Unit
) {
    SettingRow(icon = icon, title = title, onClick = onClick, trailing = {
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 140.dp))
        Spacer(Modifier.width(2.dp))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.size(16.dp))
    })
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// 端口+用户名+密码 的组合框（细描边、无背景色）
@Composable
private fun ComboGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium),
        content = content
    )
}

@Composable
private fun SettingsDetailPage(
    title: String,
    onBack: () -> Unit,
    actionLabel: String = "完成",
    onAction: () -> Unit = onBack,
    content: @Composable ColumnScope.() -> Unit,
) {
    // 复用全局主题配色，避免详情页自成一派
    val appColors = MaterialTheme.colorScheme
    Dialog(
        onDismissRequest = onBack,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val view = LocalView.current
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.setDimAmount(0.12f)
        }
        MaterialTheme(colorScheme = appColors, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
            Surface(
                modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp).padding(horizontal = 24.dp)
                    .heightIn(max = 680.dp),
                shape = RoundedCornerShape(IceRadius.panel),
                color = appColors.surface,
                shadowElevation = 12.dp,
            ) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 8.dp, end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onBack) { Icon(Icons.Filled.Close, "关闭", tint = MaterialTheme.colorScheme.primary) }
                        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                        TextButton(onClick = onAction) { Text(actionLabel, color = MaterialTheme.colorScheme.primary) }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(
                        Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp).padding(bottom = 16.dp),
                        content = content,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsDetailSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(IceRadius.row)).background(MaterialTheme.colorScheme.surface),
            content = content,
        )
    }
}

@Composable
private fun SettingsOptionRow(title: String, selected: Boolean, onClick: () -> Unit, subtitle: String? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            subtitle?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (trailing != null) trailing() else if (selected) Icon(Icons.Filled.Check, "已选择", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SettingsDetailDivider() {
    HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
}

@Composable
private fun ThemeSettingsPage(current: AppTheme, onBack: () -> Unit, onSelect: (AppTheme) -> Unit) {
    SettingsDetailPage("主题", onBack) {
        SettingsDetailSection("选择主题") {
            AppTheme.entries.forEachIndexed { index, theme ->
                val (primary, soft) = com.iceflow.ui.theme.themePreviewColors(theme)
                SettingsOptionRow(themeLabel(theme), theme == current, { onSelect(theme) }, trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(28.dp).clip(CircleShape).background(soft))
                        Box(Modifier.size(20.dp).clip(CircleShape).background(primary))
                        if (theme == current) Icon(Icons.Filled.Check, "已选择", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                })
                if (index != AppTheme.entries.lastIndex) SettingsDetailDivider()
            }
        }
    }
}

@Composable
private fun ChoiceSettingsPage(title: String, options: List<String>, selected: String, onBack: () -> Unit, onSelect: (String) -> Unit, subtitleFor: (String) -> String? = { null }) {
    SettingsDetailPage(title, onBack) {
        SettingsDetailSection("选择$title") {
            options.forEachIndexed { index, option ->
                SettingsOptionRow(option, option == selected, { onSelect(option) }, subtitleFor(option))
                if (index != options.lastIndex) SettingsDetailDivider()
            }
        }
    }
}

@Composable
private fun TextSettingsPage(
    title: String,
    label: String,
    initialValue: String,
    multiline: Boolean = false,
    onBack: () -> Unit,
    onSave: (String) -> String?,
    help: String? = null,
) {
    var value by rememberSaveable { mutableStateOf(initialValue) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    SettingsDetailPage(title, onBack, onAction = { error = onSave(value) }) {
        SettingsDetailSection(title) {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it; error = null },
                label = { Text(label) },
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                singleLine = !multiline,
                minLines = if (multiline) 3 else 1,
                maxLines = if (multiline) 8 else 1,
                keyboardOptions = KeyboardOptions(keyboardType = if (title.contains("URL")) KeyboardType.Uri else KeyboardType.Text),
                isError = error != null,
                supportingText = { error?.let { Text(it, color = MaterialTheme.colorScheme.error) } },
            )
        }
        if (help != null) Text(help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp))
    }
}

@Composable
private fun AuthSettingsPage(title: String, initialValue: String, password: Boolean, onBack: () -> Unit, onSave: (String) -> Unit) {
    var value by rememberSaveable { mutableStateOf(initialValue) }
    SettingsDetailPage(title, onBack, onAction = { onSave(value) }) {
        SettingsDetailSection("SOCKS5 认证") {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(title) },
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                singleLine = true,
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            )
        }
    }
}

@Composable
fun SettingsScreen(
    settings: AppSettings,
    xrayVersion: String?,
    coreVersion: String?,
    appVersion: String,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onOpenLogs: () -> Unit = {},
    onOpenBackup: () -> Unit = {},
    onOpenAppSelector: () -> Unit = {},
    onCheckUpdate: () -> Unit = {},
    onDetailOpenChange: (Boolean) -> Unit = {},
) {
    var port by rememberSaveable { mutableStateOf(settings.socksPort.toString()) }
    var detail by rememberSaveable { mutableStateOf<String?>(null) }
    val portValid = port.toIntOrNull()?.let { it in 1024..65535 } == true

    LaunchedEffect(detail) { onDetailOpenChange(detail != null) }
    DisposableEffect(Unit) { onDispose { onDetailOpenChange(false) } }

    if (detail != null) {
        when (detail) {
            "theme" -> ThemeSettingsPage(settings.theme, { detail = null }) { theme -> onUpdate { it.copy(theme = theme) } }
            "nightMode" -> ChoiceSettingsPage(
                "夜间模式",
                NightMode.entries.map { it.label },
                settings.nightMode.label,
                { detail = null },
                { label -> onUpdate { it.copy(nightMode = NightMode.entries.first { mode -> mode.label == label }) } },
                { label -> if (label == NightMode.AUTO.label) "每天 22:00 至次日 06:00 自动切换" else null },
            )
            "textSize" -> ChoiceSettingsPage(
                "文字大小",
                TextSize.entries.map { it.label },
                settings.textSize.label,
                { detail = null },
                { label ->
                    onUpdate { it.copy(textSize = TextSize.entries.first { size -> size.label == label }) }
                    detail = null
                },
            )
            "routing" -> ChoiceSettingsPage(
                "路由模式", RoutingMode.entries.map { it.label }, settings.routingMode.label,
                onBack = { detail = null },
                onSelect = { label ->
                    RoutingMode.entries.first { it.label == label }.let { mode -> onUpdate { it.copy(routingMode = mode) } }
                },
            )
            "logRetention" -> ChoiceSettingsPage(
                "日志保留天数", listOf("永久", "1 天", "3 天", "7 天", "30 天"),
                if (settings.logRetentionDays == 0) "永久" else "${settings.logRetentionDays} 天",
                onBack = { detail = null },
                onSelect = { label ->
                    val days = if (label == "永久") 0 else label.removeSuffix(" 天").toInt()
                    onUpdate { it.copy(logRetentionDays = days) }
                },
            )
            "dns" -> TextSettingsPage("远程 DNS", "每行一个 DNS 地址", settings.dnsServers.joinToString("\n"), multiline = true, onBack = { detail = null }, onSave = { text ->
                val values = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
                runCatching {
                    require(values.isNotEmpty()) { "至少输入一个 DNS 地址" }
                    settings.copy(dnsServers = values, remoteDns = values.first()).validate()
                    onUpdate { it.copy(dnsServers = values, remoteDns = values.first()) }
                    detail = null
                }.exceptionOrNull()?.message
            })
            "testUrl" -> TextSettingsPage("测速 URL", "HTTP / HTTPS 链接", settings.testUrl, onBack = { detail = null }, onSave = { text ->
                runCatching { com.iceflow.data.model.RoutingRules.validateTestUrl(text) }
                    .onSuccess { valid -> onUpdate { it.copy(testUrl = valid) }; detail = null }
                    .exceptionOrNull()?.message
            }, help = "用于单节点、批量和启动栏测速，建议选择返回 204 的轻量页面。")
            "username" -> AuthSettingsPage("用户名", settings.socksUsername, false, { detail = null }) { value -> onUpdate { it.copy(socksUsername = value) }; detail = null }
            "password" -> AuthSettingsPage("密码", settings.socksPassword, true, { detail = null }) { value -> onUpdate { it.copy(socksPassword = value) }; detail = null }
            else -> detail = null
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Text("设置", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp))

        // ── 外观 ──────────────────────────────────────────────────────────────────
        SectionHeader("外观")
        SettingsCard {
            SettingAction(Icons.Outlined.Palette, "主题", themeLabel(settings.theme)) { detail = "theme" }
            RowDivider()
            SettingAction(Icons.Outlined.Brightness4, "夜间模式", settings.nightMode.label) { detail = "nightMode" }
            RowDivider()
            SettingAction(Icons.Outlined.FormatSize, "文字大小", settings.textSize.label) { detail = "textSize" }
            RowDivider()
            SettingRow(icon = Icons.Outlined.Language, title = "语言", trailing = {
                Text(if (settings.language == "zh-CN") "简体中文" else settings.language, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            })
        }

        // ── 代理 ──────────────────────────────────────────────────────────────────
        SectionHeader("代理")
        SettingsCard {
            SettingAction(Icons.AutoMirrored.Outlined.AltRoute, "路由模式", settings.routingMode.label) { detail = "routing" }
            RowDivider()
            ToggleRow(Icons.Outlined.Hub, "启用 SOCKS5 路由", "允许其他应用使用本地代理", settings.socksEnabled) { v -> onUpdate { it.copy(socksEnabled = v) } }
            // 端口 + 用户名 + 密码 组合
            ComboGroup {
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter(Char::isDigit).take(5); port.toIntOrNull()?.takeIf { p -> p in 1024..65535 }?.let { p -> onUpdate { s -> s.copy(socksPort = p) } } },
                    label = { Text("本地 SOCKS5 端口", style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = { Icon(Icons.Outlined.SettingsEthernet, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(IconSize)) },
                    textStyle = MaterialTheme.typography.bodySmall, isError = !portValid,
                    supportingText = { if (!portValid) Text("范围 1024–65535", style = MaterialTheme.typography.labelSmall) },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                )
                RowDivider()
                SettingAction(Icons.Outlined.Person, "用户名", settings.socksUsername.ifBlank { "未设置" }) { detail = "username" }
                RowDivider()
                SettingAction(Icons.Outlined.Lock, "密码", if (settings.socksPassword.isBlank()) "未设置" else "已设置") { detail = "password" }
            }
            // UDP 放最底部
            ToggleRow(Icons.Outlined.SwapHoriz, "UDP", "允许 UDP 转发", settings.socksUdp) { v -> onUpdate { it.copy(socksUdp = v) } }
        }

                // ── DNS 与网络 ────────────────────────────────────────────────────────────
        SectionHeader("DNS 与网络")
        SettingsCard {
            ToggleRow(Icons.Outlined.Lan, "绕过局域网", "局域网与保留地址直连", settings.bypassLan) { v -> onUpdate { it.copy(bypassLan = v) } }
            RowDivider()
            SettingAction(Icons.Outlined.Dns, "远程 DNS", settings.remoteDns.ifBlank { "未设置" }) { detail = "dns" }
            RowDivider()
            ToggleRow(Icons.Outlined.VpnKey, "DNS 代理", "关闭后 DNS 请求按普通 UDP 跟随路由规则", settings.dnsProxyEnabled) { v -> onUpdate { it.copy(dnsProxyEnabled = v) } }
            RowDivider()
            ToggleRow(Icons.Outlined.FilterAlt, "虚拟 DNS（Fake-IP）", "关闭后使用 DNS over TCP", settings.fakeDns) { v -> onUpdate { it.copy(fakeDns = v) } }
            RowDivider()
            ToggleRow(Icons.Outlined.Public, "启用 IPv6", "允许 IPv6 流量通过代理", settings.ipv6Enabled) { v -> onUpdate { it.copy(ipv6Enabled = v) } }
        }

        // ── 应用路由 ──────────────────────────────────────────────────────────────
        SectionHeader("应用路由")
        SettingsCard {
            ToggleRow(Icons.Outlined.Apps, "应用路由", "启用所选应用的绕过或代理策略", settings.appRoutingEnabled) { v -> onUpdate { it.copy(appRoutingEnabled = v) } }
            RowDivider()
            // 两个模式：分应用代理 / 允许应用绕过代理
            SegmentedSelector(
                options = listOf("分应用代理", "允许应用绕过"),
                selectedIndex = if (settings.appProxyMode) 0 else 1,
                onSelect = { index ->
                    val proxyOnly = index == 0
                    onUpdate { it.copy(appProxyMode = proxyOnly, proxyAppsOnly = proxyOnly) }
                },
                modifier = Modifier.padding(start = 52.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            )
            RowDivider()
            SettingRow(icon = Icons.Outlined.Checklist, title = "选择应用", onClick = onOpenAppSelector, trailing = {
                Text("${settings.bypassApps.size} 个应用", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(2.dp))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.size(16.dp))
            })
        }

        // ── 核心与日志 ────────────────────────────────────────────────────────────
        SectionHeader("核心与日志")
        SettingsCard {
            SettingAction(Icons.Outlined.Speed, "测速 URL", settings.testUrl) { detail = "testUrl" }
            RowDivider()
            ToggleRow(Icons.Outlined.BarChart, "显示流量统计", "在节点卡片显示累计流量", settings.showTrafficStats) { v -> onUpdate { it.copy(showTrafficStats = v) } }
            RowDivider()
            ToggleRow(Icons.Outlined.Speed, "显示节点延迟", "在节点卡片显示延迟；未测速的节点不显示", settings.showNodeLatency) { v -> onUpdate { it.copy(showNodeLatency = v) } }
            RowDivider()
            SettingAction(Icons.Outlined.History, "日志保留天数", if (settings.logRetentionDays == 0) "永久" else "${settings.logRetentionDays} 天") { detail = "logRetention" }
            RowDivider()
            SettingRow(icon = Icons.AutoMirrored.Outlined.Article, title = "查看运行日志", onClick = onOpenLogs, trailing = {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.size(16.dp))
            })
        }

        // ── 关于 ──────────────────────────────────────────────────────────────────
        SectionHeader("数据")
        SettingsCard {
            SettingAction(Icons.Outlined.Save, "备份与恢复", "节点、订阅、路由规则和设置") { onOpenBackup() }
        }

        SectionHeader("关于")
        SettingsCard {
            SettingRow(title = "IceFlow", subtitle = "点按检查更新", onClick = onCheckUpdate, trailing = {
                Text(appVersion, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            })
            RowDivider()
            InfoRow("xray-core", xrayVersion ?: "未集成")
            RowDivider()
            InfoRow("Rust 核心", coreVersion ?: "未集成")
        }
    }

}

private fun themeLabel(theme: AppTheme): String = when (theme) {
    AppTheme.LIGHT_BLUE -> "浅蓝"
    AppTheme.MINT_GREEN -> "薄荷绿"
    AppTheme.SAKURA_PINK -> "樱花粉"
    AppTheme.PEACH_ORANGE -> "蜜桃橙"
    AppTheme.OBSIDIAN_BLACK -> "曜石黑"
}
