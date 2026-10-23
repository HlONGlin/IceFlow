package com.iceflow.ui.screen

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.iceflow.service.TrafficSnapshot
import com.iceflow.service.VpnState
import com.iceflow.ui.component.FlowChart
import com.iceflow.ui.component.GlassCard
import com.iceflow.util.formatBytes
import com.iceflow.util.formatDuration
import com.iceflow.util.formatSpeed
import kotlinx.coroutines.delay

@Composable
fun StatsScreen(state: VpnState, traffic: TrafficSnapshot) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state) {
        while (state is VpnState.Connected) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val duration = if (state is VpnState.Connected) now - state.since else 0L

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("流量统计", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("上传速度", formatSpeed(traffic.upSpeed), Modifier.weight(1f))
            StatTile("下载速度", formatSpeed(traffic.downSpeed), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("上传总量", formatBytes(traffic.upTotal), Modifier.weight(1f))
            StatTile("下载总量", formatBytes(traffic.downTotal), Modifier.weight(1f))
        }
        StatTile("连接时长", formatDuration(duration), Modifier.fillMaxWidth())
        GlassCard(Modifier.fillMaxWidth()) {
            Text("最近 60 秒", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            FlowChart(traffic.history, height = 200.dp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("━ 下行", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                Text("━ 上行", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    GlassCard(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleLarge)
    }
}
