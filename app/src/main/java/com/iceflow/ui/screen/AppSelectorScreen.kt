package com.iceflow.ui.screen

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: Bitmap?,
    val isSystemApp: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectorScreen(
    proxyMode: Boolean,
    selectedApps: List<String>,
    onSelectionChange: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var showSystemApps by remember { mutableStateOf(false) }
    var selectedOnly by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { it.packageName != context.packageName } // 排除自己
                .map { appInfo ->
                    AppInfo(
                        packageName = appInfo.packageName,
                        label = pm.getApplicationLabel(appInfo).toString(),
                        icon = runCatching { pm.getApplicationIcon(appInfo).toBitmap(96, 96) }.getOrNull(),
                        isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    )
                }
                .sortedBy { it.label.lowercase() }

            withContext(Dispatchers.Main) {
                apps = installedApps
                loading = false
            }
        }
    }

    val selected = remember(selectedApps) { selectedApps.toSet() }
    val filteredApps = remember(apps, searchQuery, showSystemApps, selectedOnly, selected) {
        apps.filter { app ->
            (showSystemApps || !app.isSystemApp) &&
            (!selectedOnly || app.packageName in selected) &&
            (searchQuery.isEmpty() ||
             app.label.contains(searchQuery, ignoreCase = true) ||
             app.packageName.contains(searchQuery, ignoreCase = true))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (proxyMode) "选择代理应用" else "选择绕过应用") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "关闭")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("搜索应用名称或包名") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, "清除")
                            }
                        }
                    },
                    singleLine = true
                )

            // 过滤选项
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${if (proxyMode) "代理" else "绕过"} ${selected.size} 个应用 · 当前显示 ${filteredApps.size} 个",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("显示系统应用", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = showSystemApps,
                        onCheckedChange = { showSystemApps = it }
                    )
                }
            }

            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = !selectedOnly, onClick = { selectedOnly = false }, label = { Text("全部") })
                FilterChip(selected = selectedOnly, onClick = { selectedOnly = true }, label = { Text("仅已选") })
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    val visible = filteredApps.map { it.packageName }.toSet()
                    onSelectionChange((selected - visible + visible).toList())
                }, enabled = filteredApps.isNotEmpty()) { Text("全选") }
                TextButton(onClick = {
                    val visible = filteredApps.map { it.packageName }.toSet()
                    onSelectionChange(selectedApps.filterNot { it in visible })
                }, enabled = filteredApps.any { it.packageName in selected }) { Text("清除") }
            }
            HorizontalDivider()

            // 应用列表
            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    if (filteredApps.isEmpty()) {
                        item {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "未找到应用",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(filteredApps, key = { it.packageName }) { app ->
                            AppItem(
                                app = app,
                                isSelected = app.packageName in selected,
                                onClick = {
                                    val newSelection = if (app.packageName in selected) {
                                        selectedApps - app.packageName
                                    } else {
                                        selectedApps + app.packageName
                                    }
                                    onSelectionChange(newSelection)
                                }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppItem(app: AppInfo, isSelected: Boolean, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable(onClick = onClick), color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 应用图标
            app.icon?.let { icon ->
                Image(
                    bitmap = icon.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp)
                )
            } ?: Surface(Modifier.size(48.dp), shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant) { }

            Spacer(Modifier.width(12.dp))

            // 应用信息
            Column(Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (app.isSystemApp) {
                    Text(
                        text = "系统应用",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            // 选择框
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClick() }
            )
        }
    }
}
