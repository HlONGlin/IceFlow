package com.iceflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.iceflow.data.model.ServerProfile
import com.iceflow.ui.theme.IceBlue

@Composable internal fun BulkTestOverlay(state: BulkTestState, dismiss: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0x55000000)), contentAlignment = Alignment.Center) {
        Surface(Modifier.fillMaxWidth().padding(horizontal = 28.dp).heightIn(min = 180.dp, max = 620.dp), shape = RoundedCornerShape(22.dp), color = Color.White, shadowElevation = 16.dp) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (state.testing) "正在逐个测试节点" else "全部测速完成", style = MaterialTheme.typography.titleMedium)
                        Text("${state.completed} / ${state.nodes.size}", style = MaterialTheme.typography.bodySmall, color = IceBlue)
                    }
                    if (!state.testing) IconButton(dismiss) { Icon(Icons.Default.Close, "关闭测速结果", tint = IceBlue) }
                }
                if (state.testing) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { if (state.nodes.isEmpty()) 0f else state.completed.toFloat() / state.nodes.size }, modifier = Modifier.fillMaxWidth(), color = IceBlue)
                }
                Spacer(Modifier.height(10.dp))
                LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(state.nodes, key = { it.id }) { node -> BulkResultRow(node, state) }
                }
            }
        }
    }
}

@Composable private fun BulkResultRow(node: ServerProfile, state: BulkTestState) {
    val result = state.results[node.id]
    val current = state.currentId == node.id && state.testing
    Row(Modifier.fillMaxWidth().background(if (current) Color(0xFFEDF5FF) else Color.Transparent, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(node.name, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            Text(node.protocol.label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF8E8E93))
        }
        when {
            current -> CircularProgressIndicator(Modifier.size(17.dp), color = IceBlue, strokeWidth = 2.dp)
            result == null -> Text("等待", color = Color(0xFF8E8E93), style = MaterialTheme.typography.bodySmall)
            result < 0 -> Text("超时", color = Color(0xFFFF3B30), style = MaterialTheme.typography.bodySmall)
            else -> Text("${result} ms", color = IceBlue, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable internal fun NodeLatencyToast(state: NodeTestState) {
    Row(Modifier.background(Color(0xE6333A45), RoundedCornerShape(24.dp)).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.testing) CircularProgressIndicator(Modifier.size(15.dp), color = Color.White, strokeWidth = 2.dp)
        Text(if (state.testing) "${state.name}：测速中" else if ((state.latencyMs ?: -1) < 0) "${state.name}：超时" else "${state.latencyMs} ms", style = MaterialTheme.typography.bodySmall, color = Color.White, maxLines = 1)
    }
}
