package com.iceflow.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iceflow.data.model.ServerProfile
import com.iceflow.ui.theme.LatencyBad
import com.iceflow.ui.theme.LatencyGood
import com.iceflow.ui.theme.LatencyMedium

fun latencyColor(ms: Int?, fallback: Color): Color = when {
    ms == null -> fallback
    ms < 0 -> LatencyBad
    ms <= 50 -> LatencyGood
    ms <= 150 -> LatencyMedium
    else -> LatencyBad
}

fun latencyText(ms: Int?): String = when {
    ms == null -> "未测试"
    ms < 0 -> "超时"
    else -> "${ms}ms"
}

@Composable
fun ServerItem(
    server: ServerProfile,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) colors.primary.copy(alpha = 0.14f) else colors.surfaceVariant.copy(alpha = 0.5f),
        border = if (selected) BorderStroke(1.5.dp, colors.primary) else null,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = RoundedCornerShape(6.dp), color = colors.primary.copy(alpha = 0.2f)) {
                Text(
                    server.protocol.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.primary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(server.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${server.address}:${server.port} · ${server.network}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                latencyText(server.latencyMs),
                style = MaterialTheme.typography.labelLarge,
                color = latencyColor(server.latencyMs, colors.onSurfaceVariant),
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "删除 ${server.name}", tint = colors.onSurfaceVariant)
            }
        }
    }
}
