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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iceflow.data.model.ServerProfile
import com.iceflow.i18n.T
import com.iceflow.ui.theme.IceRadius

@Composable internal fun BulkTestOverlay(state: BulkTestState, dismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
        Surface(
            Modifier.fillMaxWidth().padding(horizontal = 28.dp).heightIn(min = 180.dp, max = 620.dp),
            shape = RoundedCornerShape(IceRadius.panel),
            color = colors.surface,
            shadowElevation = 16.dp,
        ) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (state.testing) T("node.test.testing") else T("node.test.done"), style = MaterialTheme.typography.titleMedium)
                        Text("${state.completed} / ${state.nodes.size}", style = MaterialTheme.typography.bodySmall, color = colors.primary)
                    }
                    if (!state.testing) IconButton(dismiss) { Icon(Icons.Default.Close, T("common.close"), tint = colors.primary) }
                }
                if (state.testing) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { if (state.nodes.isEmpty()) 0f else state.completed.toFloat() / state.nodes.size },
                        modifier = Modifier.fillMaxWidth(),
                        color = colors.primary,
                    )
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
    val colors = MaterialTheme.colorScheme
    val result = state.results[node.id]
    val current = state.currentId == node.id && state.testing
    Row(
        Modifier.fillMaxWidth()
            .background(if (current) colors.primary.copy(alpha = 0.08f) else Color.Transparent, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(node.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(node.protocol.label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
        when {
            current -> CircularProgressIndicator(Modifier.size(17.dp), color = colors.primary, strokeWidth = 2.dp)
            result == null -> Text(T("node.test.waiting"), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            result < 0 -> Text(T("latency.timeout"), color = colors.error, style = MaterialTheme.typography.bodySmall)
            else -> Text("${result} ms", color = colors.primary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable internal fun NodeLatencyToast(state: NodeTestState) {
    Row(
        Modifier.background(MaterialTheme.colorScheme.inverseSurface, RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val onInverse = MaterialTheme.colorScheme.inverseOnSurface
        if (state.testing) CircularProgressIndicator(Modifier.size(15.dp), color = onInverse, strokeWidth = 2.dp)
        Text(
            if (state.testing) "${state.name}: ${T("latency.testing")}"
            else if ((state.latencyMs ?: -1) < 0) "${state.name}: ${T("latency.timeout")}"
            else "${state.name}: ${state.latencyMs} ms",
            style = MaterialTheme.typography.bodySmall,
            color = onInverse,
            maxLines = 1,
        )
    }
}
