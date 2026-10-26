package com.iceflow.ui.screen

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.iceflow.ui.component.SegmentedSelector
import com.iceflow.ui.theme.IceRadius
import com.iceflow.i18n.T
import com.iceflow.i18n.Tf
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: Bitmap?,
    val isSystemApp: Boolean
)

/** 应用列表的筛选条件。 */
enum class AppFilter { ALL, SELECTED, UNSELECTED }

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
    var filter by remember { mutableStateOf(AppFilter.ALL) }
    val title = if (proxyMode) T("apps.proxyTitle") else T("apps.bypassTitle")
    val backLabel = T("common.back")
    val doneLabel = T("common.done")
    val searchHint = T("apps.searchHint")
    val clearLabel = T("apps.clear")
    val systemLabel = T("apps.system")
    val filterLabels = listOf(T("apps.all"), T("apps.selected"), T("apps.unselected"))
    val selectAllLabel = T("apps.selectAll")
    val notFoundLabel = T("apps.notFound")

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
    val filteredApps = remember(apps, searchQuery, showSystemApps, filter, selected) {
        apps.filter { app ->
            (showSystemApps || !app.isSystemApp) &&
            when (filter) {
                AppFilter.ALL -> true
                AppFilter.SELECTED -> app.packageName in selected
                AppFilter.UNSELECTED -> app.packageName !in selected
            } &&
            (searchQuery.isEmpty() ||
             app.label.contains(searchQuery, ignoreCase = true) ||
             app.packageName.contains(searchQuery, ignoreCase = true))
        }
    }
    val selectedCount = Tf("apps.selectedCount", selectedApps.size, filteredApps.size)

    Scaffold(
        topBar = {
            Row(
                Modifier.fillMaxWidth().height(64.dp).background(MaterialTheme.colorScheme.background).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, backLabel, tint = MaterialTheme.colorScheme.primary) }
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(doneLabel, color = MaterialTheme.colorScheme.primary) }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(IceRadius.row))
            ) {
            OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    placeholder = { Text(searchHint) },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, clearLabel)
                            }
                        }
                    },
                    singleLine = true
                )

            // 过滤选项
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    selectedCount,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
                    Text(systemLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Switch(
                        checked = showSystemApps,
                        onCheckedChange = { showSystemApps = it }
                    )
                }
            }

            // 筛选：全部 / 已选 / 未选
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SegmentedSelector(
                    options = filterLabels,
                    selectedIndex = filter.ordinal,
                    onSelect = { filter = AppFilter.entries[it] },
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    val visible = filteredApps.map { it.packageName }.toSet()
                    onSelectionChange((selected + visible).toList())
                }, enabled = filteredApps.isNotEmpty()) { Text(selectAllLabel) }
                TextButton(onClick = {
                    val visible = filteredApps.map { it.packageName }.toSet()
                    onSelectionChange(selectedApps.filterNot { it in visible })
                }, enabled = filteredApps.any { it.packageName in selected }) { Text(clearLabel) }
            }
            }

            // 应用列表
            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
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
                                    notFoundLabel,
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
            }

            // 选择框
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClick() }
            )
        }
    }
}
