package com.iceflow.service

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface VpnState {
    data object Idle : VpnState
    data object Connecting : VpnState
    data class Connected(val serverName: String, val since: Long, val socksPort: Int = 10808) : VpnState
    data object Stopping : VpnState
    data class Error(val message: String) : VpnState
}

data class TrafficPoint(val up: Long, val down: Long)

data class TrafficSnapshot(
    val upTotal: Long = 0,
    val downTotal: Long = 0,
    val upSpeed: Long = 0,
    val downSpeed: Long = 0,
    val history: List<TrafficPoint> = emptyList(),
    /** 按出站标签分类的流量（proxy / direct / block 等）*/
    val byOutbound: Map<String, Pair<Long, Long>> = emptyMap(),
)

/** 进程内 VPN 状态中心，UI / 快捷开关 / 服务共享。 */
object VpnController {

    private val _state = MutableStateFlow<VpnState>(VpnState.Idle)
    val state: StateFlow<VpnState> = _state.asStateFlow()

    private val _traffic = MutableStateFlow(TrafficSnapshot())
    val traffic: StateFlow<TrafficSnapshot> = _traffic.asStateFlow()

    internal fun setState(state: VpnState) {
        _state.value = state
    }

    internal fun setTraffic(snapshot: TrafficSnapshot) {
        _traffic.value = snapshot
    }

    /** 调用前需确保 VpnService.prepare() 已授权。 */
    fun start(context: Context) {
        val intent = Intent(context, IceFlowVpnService::class.java).setAction(IceFlowVpnService.ACTION_START)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
        val intent = Intent(context, IceFlowVpnService::class.java).setAction(IceFlowVpnService.ACTION_STOP)
        runCatching { context.startService(intent) }
            .onFailure { Log.w("VpnController", "stop failed: $it") }
    }

    fun switch(context: Context) {
        val intent = Intent(context, IceFlowVpnService::class.java).setAction(IceFlowVpnService.ACTION_SWITCH)
        runCatching { context.startService(intent) }
            .onFailure { Log.w("VpnController", "switch failed: $it") }
    }
}
