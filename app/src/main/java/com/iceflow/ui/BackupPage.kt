package com.iceflow.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.iceflow.data.BackupCodec
import com.iceflow.data.BackupDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BackupPage(export: suspend () -> String, restore: suspend (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<Pair<String, BackupDocument>?>(null) }
    BackHandler { if (!busy) onBack() }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            message = runCatching {
                val text = export()
                withContext(Dispatchers.IO) {
                    checkNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "无法写入备份文件" }
                        .bufferedWriter(Charsets.UTF_8).use { it.write(text) }
                }
                "备份已保存"
            }.getOrElse { "备份失败：${it.message}" }
            busy = false
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            runCatching {
                withContext(Dispatchers.IO) {
                    val text = checkNotNull(context.contentResolver.openInputStream(uri)) { "无法读取备份文件" }.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            require(output.size() + count <= BackupCodec.MAX_BYTES) { "备份不能超过 16 MiB" }
                            output.write(buffer, 0, count)
                        }
                        output.toString("UTF-8")
                    }
                    text to BackupCodec.decode(text)
                }
            }.onSuccess { pending = it }.onFailure { message = "读取失败：${it.message}" }
            busy = false
        }
    }
    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, enabled = !busy) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
            Text("备份与恢复", style = MaterialTheme.typography.titleMedium)
        }
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("备份包含节点密码、订阅链接、分组、路由规则和设置，请妥善保存。", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { save.launch("IceFlow-backup.json") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.FileDownload, null); Spacer(Modifier.width(8.dp)); Text("导出备份")
            }
            OutlinedButton(onClick = { open.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.FileUpload, null); Spacer(Modifier.width(8.dp)); Text("从文件恢复")
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
    pending?.let { (text, backup) ->
        AlertDialog(
            onDismissRequest = { if (!busy) pending = null },
            title = { Text("恢复备份？") },
            text = { Text("将恢复 ${backup.store.servers.size} 个节点、${backup.store.subscriptions.size} 个订阅，替换当前数据及设置，并断开当前 VPN。") },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                scope.launch {
                    busy = true
                    message = runCatching { restore(text); "备份已恢复" }.getOrElse { "恢复失败：${it.message}" }
                    pending = null
                    busy = false
                }
            }) { Text("恢复") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { pending = null }) { Text("取消") } },
        )
    }
}
