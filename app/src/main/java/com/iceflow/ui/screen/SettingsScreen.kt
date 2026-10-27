package com.iceflow.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import com.iceflow.data.model.AppLanguage
import com.iceflow.i18n.T
import com.iceflow.i18n.stringsOf
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
    actionLabel: String? = null,
    onAction: () -> Unit = onBack,
    content: @Composable ColumnScope.() -> Unit,
) {
    // 复用全局主题配色，避免详情页自成一派
    val appColors = MaterialTheme.colorScheme
    val resolvedActionLabel = actionLabel ?: T("common.done")
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
                        IconButton(onClick = onBack) { Icon(Icons.Filled.Close, T("common.close"), tint = MaterialTheme.colorScheme.primary) }
                        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                        TextButton(onClick = onAction) { Text(resolvedActionLabel, color = MaterialTheme.colorScheme.primary) }
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
        if (trailing != null) trailing() else if (selected) Icon(Icons.Filled.Check, T("common.selected"), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SettingsDetailDivider() {
    HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
}

@Composable
private fun ThemeSettingsPage(current: AppTheme, onBack: () -> Unit, onSelect: (AppTheme) -> Unit) {
    SettingsDetailPage(T("settings.theme"), onBack) {
        SettingsDetailSection(T("settings.themeChoice")) {
            AppTheme.entries.forEachIndexed { index, theme ->
                val (primary, soft) = com.iceflow.ui.theme.themePreviewColors(theme)
                SettingsOptionRow(themeLabel(theme), theme == current, { onSelect(theme) }, trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(28.dp).clip(CircleShape).background(soft))
                        Box(Modifier.size(20.dp).clip(CircleShape).background(primary))
                        if (theme == current) Icon(Icons.Filled.Check, T("common.selected"), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
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
        SettingsDetailSection(T("settings.choosePrefix") + title) {
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
        SettingsDetailSection(T("settings.socksAuth")) {
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
    val nightHint = T("settings.nightAutoHint")
    val foreverLabel = T("settings.forever")
    val daysFormat = T("settings.days")
    val dnsRequired = T("settings.dnsRequired")
    val dnsPlaceholder = T("settings.dnsPlaceholder")
    val nightOptions = NightMode.entries.map { it to T(it.stringKey) }
    val textSizeOptions = TextSize.entries.map { it to T(it.stringKey) }
    val routingOptions = RoutingMode.entries.map { it to T(it.stringKey) }
    var port by rememberSaveable { mutableStateOf(settings.socksPort.toString()) }
    var detail by rememberSaveable { mutableStateOf<String?>(null) }
    val portValid = port.toIntOrNull()?.let { it in 1024..65535 } == true

    LaunchedEffect(detail) { onDetailOpenChange(detail != null) }
    DisposableEffect(Unit) { onDispose { onDetailOpenChange(false) } }

    if (detail != null) {
        when (detail) {
            "language" -> ChoiceSettingsPage(
                T("settings.languageChoice"),
                AppLanguage.entries.map { stringsOf(it).get("lang.${it.tag}") },
                stringsOf(AppLanguage.fromTag(settings.language)).get("lang.${AppLanguage.fromTag(settings.language).tag}"),
                { detail = null },
                { label -> AppLanguage.entries.firstOrNull { stringsOf(it).get("lang.${it.tag}") == label }
                    ?.let { lang -> onUpdate { it.copy(language = lang.tag) } } },
            )
            "theme" -> ThemeSettingsPage(settings.theme, { detail = null }) { theme -> onUpdate { it.copy(theme = theme) } }
            "nightMode" -> ChoiceSettingsPage(
                T("settings.nightMode"),
                nightOptions.map { it.second },
                nightOptions.first { it.first == settings.nightMode }.second,
                { detail = null },
                { label -> onUpdate { it.copy(nightMode = nightOptions.first { it.second == label }.first) } },
                { label -> if (label == nightOptions.first { it.first == NightMode.AUTO }.second) nightHint else null },
            )
            "textSize" -> ChoiceSettingsPage(
                T("settings.textSize"),
                textSizeOptions.map { it.second },
                textSizeOptions.first { it.first == settings.textSize }.second,
                { detail = null },
                { label ->
                    onUpdate { it.copy(textSize = textSizeOptions.first { it.second == label }.first) }
                    detail = null
                },
            )
            "routing" -> ChoiceSettingsPage(
                T("settings.routing"), routingOptions.map { it.second }, routingOptions.first { it.first == settings.routingMode }.second,
                onBack = { detail = null },
                onSelect = { label ->
                    routingOptions.first { it.second == label }.first.let { mode -> onUpdate { it.copy(routingMode = mode) } }
                },
            )
            "logRetention" -> ChoiceSettingsPage(
                T("settings.logRetention"), listOf(foreverLabel, 1, 3, 7, 30).map { if (it is String) it else String.format(daysFormat, it) },
                if (settings.logRetentionDays == 0) foreverLabel else String.format(daysFormat, settings.logRetentionDays),
                onBack = { detail = null },
                onSelect = { label ->
                    val days = if (label == foreverLabel) 0 else label.filter(Char::isDigit).toInt()
                    onUpdate { it.copy(logRetentionDays = days) }
                },
            )
            "dns" -> TextSettingsPage(T("settings.remoteDns"), dnsPlaceholder, settings.dnsServers.joinToString("\n"), multiline = true, onBack = { detail = null }, onSave = { text ->
                val values = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
                runCatching {
                    require(values.isNotEmpty()) { dnsRequired }
                    settings.copy(dnsServers = values, remoteDns = values.first()).validate()
                    onUpdate { it.copy(dnsServers = values, remoteDns = values.first()) }
                    detail = null
                }.exceptionOrNull()?.message
            })
            "testUrl" -> TextSettingsPage(T("settings.testUrl"), T("settings.testUrlPlaceholder"), settings.testUrl, onBack = { detail = null }, onSave = { text ->
                runCatching { com.iceflow.data.model.RoutingRules.validateTestUrl(text) }
                    .onSuccess { valid -> onUpdate { it.copy(testUrl = valid) }; detail = null }
                    .exceptionOrNull()?.message
            }, help = T("settings.testUrlHelp"))
            "username" -> AuthSettingsPage(T("settings.username"), settings.socksUsername, false, { detail = null }) { value -> onUpdate { it.copy(socksUsername = value) }; detail = null }
            "password" -> AuthSettingsPage(T("settings.password"), settings.socksPassword, true, { detail = null }) { value -> onUpdate { it.copy(socksPassword = value) }; detail = null }
            else -> detail = null
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Text(T("settings.title"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp))

        // ── 外观 ──────────────────────────────────────────────────────────────────
        SectionHeader(T("settings.appearance"))
        SettingsCard {
            SettingAction(Icons.Outlined.Palette, T("settings.theme"), themeLabel(settings.theme)) { detail = "theme" }
            RowDivider()
            SettingAction(Icons.Outlined.Brightness4, T("settings.nightMode"), T(settings.nightMode.stringKey)) { detail = "nightMode" }
            RowDivider()
            SettingAction(Icons.Outlined.FormatSize, T("settings.textSize"), T(settings.textSize.stringKey)) { detail = "textSize" }
            RowDivider()
            SettingAction(
                icon = Icons.Outlined.Language,
                title = T("settings.language"),
                value = stringsOf(AppLanguage.fromTag(settings.language)).get("lang.${AppLanguage.fromTag(settings.language).tag}"),
            ) { detail = "language" }
        }

        // ── 代理 ──────────────────────────────────────────────────────────────────
        SectionHeader(T("settings.proxy"))
        SettingsCard {
            SettingAction(Icons.AutoMirrored.Outlined.AltRoute, T("settings.routing"), T(settings.routingMode.stringKey)) { detail = "routing" }
            RowDivider()
            ToggleRow(Icons.Outlined.Hub, T("settings.socks"), T("settings.socksHint"), settings.socksEnabled) { v -> onUpdate { it.copy(socksEnabled = v) } }
            // 端口 + 用户名 + 密码 组合
            ComboGroup {
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter(Char::isDigit).take(5); port.toIntOrNull()?.takeIf { p -> p in 1024..65535 }?.let { p -> onUpdate { s -> s.copy(socksPort = p) } } },
                    label = { Text(T("settings.socksPort"), style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = { Icon(Icons.Outlined.SettingsEthernet, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(IconSize)) },
                    textStyle = MaterialTheme.typography.bodySmall, isError = !portValid,
                    supportingText = { if (!portValid) Text(T("settings.portRange"), style = MaterialTheme.typography.labelSmall) },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                )
                RowDivider()
                SettingAction(Icons.Outlined.Person, T("settings.username"), settings.socksUsername.ifBlank { T("common.noValue") }) { detail = "username" }
                RowDivider()
                SettingAction(Icons.Outlined.Lock, T("settings.password"), if (settings.socksPassword.isBlank()) T("common.noValue") else T("common.set")) { detail = "password" }
            }
            // UDP 放最底部
            ToggleRow(Icons.Outlined.SwapHoriz, T("settings.udp"), T("settings.udpHint"), settings.socksUdp) { v -> onUpdate { it.copy(socksUdp = v) } }
        }

        // ── DNS 与网络 ────────────────────────────────────────────────────────────
        SectionHeader(T("settings.dnsSection"))
        SettingsCard {
            ToggleRow(Icons.Outlined.Lan, T("settings.bypassLan"), T("settings.bypassLanHint"), settings.bypassLan) { v -> onUpdate { it.copy(bypassLan = v) } }
            RowDivider()
            SettingAction(Icons.Outlined.Dns, T("settings.remoteDns"), settings.remoteDns.ifBlank { T("common.noValue") }) { detail = "dns" }
            RowDivider()
            ToggleRow(Icons.Outlined.VpnKey, T("settings.dnsProxy"), T("settings.dnsProxyHint"), settings.dnsProxyEnabled) { v -> onUpdate { it.copy(dnsProxyEnabled = v) } }
            RowDivider()
            ToggleRow(Icons.Outlined.FilterAlt, T("settings.fakeDns"), T("settings.fakeDnsHint"), settings.fakeDns) { v -> onUpdate { it.copy(fakeDns = v) } }
            RowDivider()
            ToggleRow(Icons.Outlined.Public, T("settings.ipv6"), T("settings.ipv6Hint"), settings.ipv6Enabled) { v -> onUpdate { it.copy(ipv6Enabled = v) } }
        }

        // ── 应用路由 ──────────────────────────────────────────────────────────────
        SectionHeader(T("settings.appRouting"))
        SettingsCard {
            ToggleRow(Icons.Outlined.Apps, T("settings.appRouting"), T("settings.appRoutingHint"), settings.appRoutingEnabled) { v -> onUpdate { it.copy(appRoutingEnabled = v) } }
            RowDivider()
            // 两个模式：分应用代理 / 允许应用绕过代理
            SegmentedSelector(
                options = listOf(T("settings.proxyMode"), T("settings.bypassMode")),
                selectedIndex = if (settings.appProxyMode) 0 else 1,
                onSelect = { index ->
                    val proxyOnly = index == 0
                    onUpdate { it.copy(appProxyMode = proxyOnly, proxyAppsOnly = proxyOnly) }
                },
                modifier = Modifier.padding(start = 52.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            )
            RowDivider()
            SettingRow(icon = Icons.Outlined.Checklist, title = T("settings.pickApps"), onClick = onOpenAppSelector, trailing = {
                Text(String.format(T("settings.appCount"), settings.bypassApps.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(2.dp))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.size(16.dp))
            })
        }

        // ── 核心与日志 ────────────────────────────────────────────────────────────
        SectionHeader(T("settings.coreSection"))
        SettingsCard {
            SettingAction(Icons.Outlined.Speed, T("settings.testUrl"), settings.testUrl) { detail = "testUrl" }
            RowDivider()
            ToggleRow(Icons.Outlined.BarChart, T("settings.showTraffic"), T("settings.showTrafficHint"), settings.showTrafficStats) { v -> onUpdate { it.copy(showTrafficStats = v) } }
            RowDivider()
            ToggleRow(Icons.Outlined.Speed, T("settings.showLatency"), T("settings.showLatencyHint"), settings.showNodeLatency) { v -> onUpdate { it.copy(showNodeLatency = v) } }
            RowDivider()
            ToggleRow(Icons.Outlined.Public, T("settings.showPublicIp"), T("settings.showPublicIpHint"), settings.showPublicIp) { v -> onUpdate { it.copy(showPublicIp = v) } }
            RowDivider()
            SettingAction(Icons.Outlined.History, T("settings.logRetention"), if (settings.logRetentionDays == 0) T("settings.forever") else String.format(T("settings.days"), settings.logRetentionDays)) { detail = "logRetention" }
            RowDivider()
            SettingRow(icon = Icons.AutoMirrored.Outlined.Article, title = T("settings.viewLogs"), onClick = onOpenLogs, trailing = {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.size(16.dp))
            })
        }

        // ── 关于 ──────────────────────────────────────────────────────────────────
        SectionHeader(T("settings.dataSection"))
        SettingsCard {
            SettingAction(Icons.Outlined.Save, T("settings.backup"), T("settings.backupHint")) { onOpenBackup() }
        }

        SectionHeader(T("settings.aboutSection"))
        SettingsCard {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onCheckUpdate).defaultMinSize(minHeight = 44.dp).padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("IceFlow", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    Text(T("settings.checkUpdate"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(appVersion, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RowDivider()
            InfoRow("xray-core", xrayVersion ?: T("settings.notIntegrated"))
            RowDivider()
            InfoRow(T("settings.rustCore"), coreVersion ?: T("settings.notIntegrated"))
        }
    }

}

@Composable
private fun themeLabel(theme: AppTheme): String = when (theme) {
    AppTheme.LIGHT_BLUE -> T("theme.lightBlue")
    AppTheme.MINT_GREEN -> T("theme.mintGreen")
    AppTheme.SAKURA_PINK -> T("theme.sakuraPink")
    AppTheme.PEACH_ORANGE -> T("theme.peachOrange")
    AppTheme.OBSIDIAN_BLACK -> T("theme.obsidianBlack")
}
