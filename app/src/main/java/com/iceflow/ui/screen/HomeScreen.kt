package com.iceflow.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.iceflow.data.model.ServerProfile
import com.iceflow.service.TrafficSnapshot
import com.iceflow.service.VpnState
import com.iceflow.ui.component.ConnectButton
import com.iceflow.ui.component.FlowChart
import com.iceflow.ui.component.GlassCard
import com.iceflow.ui.component.latencyColor
import com.iceflow.ui.component.latencyText
import com.iceflow.util.formatSpeed

@Composable
fun HomeScreen(
    state: VpnState,
    traffic: TrafficSnapshot,
    selected: ServerProfile?,
    onToggle: () -> Unit,
    onOpenServers: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("❄ IceFlow", style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))

        ConnectButton(state = state, onClick = onToggle)
        Spacer(Modifier.height(12.dp))
        Text(
            text = when (state) {
                is VpnState.Connected -> "已连接"
                VpnState.Connecting -> "正在连接"
                VpnState.Stopping -> "正在断开"
                is VpnState.Error -> "连接失败"
                VpnState.Idle -> "未连接"
            },
            style = MaterialTheme.typography.titleLarge,
            color = if (state is VpnState.Connected) colors.primary else colors.onSurfaceVariant,
        )
        if (state is VpnState.Error) {
            Text(state.message, style = MaterialTheme.typography.bodySmall, color = colors.error)
        }
        Spacer(Modifier.height(24.dp))

        GlassCard(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenServers),
        ) {
            if (selected == null) {
                Text("未选择节点，点击前往添加", style = MaterialTheme.typography.bodyLarge)
            } else {
                Text("🌐 ${selected.name}", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        "📶 ${latencyText(selected.latencyMs)}",
                        color = latencyColor(selected.latencyMs, colors.onSurfaceVariant),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text("↑ ${formatSpeed(traffic.upSpeed)}", style = MaterialTheme.typography.bodyMedium)
                    Text("↓ ${formatSpeed(traffic.downSpeed)}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Text("实时流量", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            FlowChart(traffic.history)
        }
    }
}
