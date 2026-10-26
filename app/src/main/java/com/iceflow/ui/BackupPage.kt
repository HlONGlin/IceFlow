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
import com.iceflow.i18n.T
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
    val cannotWrite = T("backup.cannotWrite")
    val exportDone = T("backup.exportDone")
    val exportFailed = T("backup.exportFailed")
    val cannotRead = T("backup.cannotRead")
    val tooLarge = T("backup.tooLarge")
    val readFailed = T("backup.readFailed")
    val importDone = T("backup.importDone")
    val importFailed = T("backup.importFailed")
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
                    checkNotNull(context.contentResolver.openOutputStream(uri, "wt")) { cannotWrite }
                        .bufferedWriter(Charsets.UTF_8).use { it.write(text) }
                }
                exportDone
            }.getOrElse { String.format(exportFailed, it.message ?: "") }
            busy = false
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            runCatching {
                withContext(Dispatchers.IO) {
                    val text = checkNotNull(context.contentResolver.openInputStream(uri)) { cannotRead }.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            require(output.size() + count <= BackupCodec.MAX_BYTES) { tooLarge }
                            output.write(buffer, 0, count)
                        }
                        output.toString("UTF-8")
                    }
                    text to BackupCodec.decode(text)
                }
            }.onSuccess { pending = it }.onFailure { message = String.format(readFailed, it.message ?: "") }
            busy = false
        }
    }
    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, enabled = !busy) { Icon(Icons.AutoMirrored.Filled.ArrowBack, T("common.back")) }
            Text(T("backup.title"), style = MaterialTheme.typography.titleMedium)
        }
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(T("backup.description"), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { save.launch("IceFlow-backup.json") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.FileDownload, null); Spacer(Modifier.width(8.dp)); Text(T("backup.export"))
            }
            OutlinedButton(onClick = { open.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.FileUpload, null); Spacer(Modifier.width(8.dp)); Text(T("backup.import"))
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
    pending?.let { (text, backup) ->
        AlertDialog(
            onDismissRequest = { if (!busy) pending = null },
            title = { Text(T("backup.importTitle")) },
            text = { Text(String.format(T("backup.importText"), backup.store.servers.size, backup.store.subscriptions.size, backup.settings.bypassApps.size)) },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                scope.launch {
                    busy = true
                    message = runCatching { restore(text); importDone }.getOrElse { String.format(importFailed, it.message ?: "") }
                    pending = null
                    busy = false
                }
            }) { Text(T("backup.restore")) } },
            dismissButton = { TextButton(enabled = !busy, onClick = { pending = null }) { Text(T("common.cancel")) } },
        )
    }
}
