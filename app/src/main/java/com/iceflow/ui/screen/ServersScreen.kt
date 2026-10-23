package com.iceflow.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.iceflow.data.model.ServerStore
import com.iceflow.ui.component.ServerItem

@Composable
fun ServersScreen(
    store: ServerStore,
    testing: Boolean,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onTestAll: () -> Unit,
    onImportClipboard: () -> Unit,
    onAddSubscription: (String) -> Unit,
    onRefreshSubscriptions: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var subDialog by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("节点 (${store.servers.size})", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (testing) {
                Box(Modifier.padding(12.dp)) { CircularProgressIndicator(Modifier.padding(2.dp), strokeWidth = 2.dp) }
            } else {
                IconButton(onClick = onTestAll, enabled = store.servers.isNotEmpty()) {
                    Icon(Icons.Filled.Refresh, contentDescription = "测试全部延迟")
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "添加节点")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("从剪贴板导入") }, onClick = { menuOpen = false; onImportClipboard() })
                    DropdownMenuItem(text = { Text("添加订阅") }, onClick = { menuOpen = false; subDialog = true })
                    DropdownMenuItem(
                        text = { Text("更新全部订阅") },
                        enabled = store.subscriptions.isNotEmpty(),
                        onClick = { menuOpen = false; onRefreshSubscriptions() },
                    )
                }
            }
        }

        if (store.servers.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "还没有节点\n点击右上角 + 从剪贴板导入分享链接或添加订阅\n支持 vmess / vless / trojan / ss",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(store.servers, key = { it.id }) { server ->
                    ServerItem(
                        server = server,
                        selected = server.id == store.selectedId,
                        onClick = { onSelect(server.id) },
                        onDelete = { onDelete(server.id) },
                    )
                }
            }
        }
    }

    if (subDialog) {
        SubscriptionDialog(
            onDismiss = { subDialog = false },
            onConfirm = { subDialog = false; onAddSubscription(it) },
        )
    }
}

@Composable
private fun SubscriptionDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var url by rememberSaveable { mutableStateOf("") }
    val valid = url.trim().startsWith("https://")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加订阅") },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("订阅地址 (https)") },
                singleLine = true,
                isError = url.isNotBlank() && !valid,
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(url.trim()) }, enabled = valid) { Text("添加") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
