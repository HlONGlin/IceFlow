package com.iceflow.service

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

data class ResourceReleaseStatus(
    val lastRequestedId: Long = 0,
    val lastCompletedId: Long = 0,
    val lastResult: ResourceReleaseResult? = null,
)

data class ResourceReleaseResult(val requestId: Long, val failures: List<String>) {
    val succeeded: Boolean get() = failures.isEmpty()
}

/** Queryable release acknowledgement; completion remains available to late waiters. */
class ResourceReleaseTracker {
    private val _status = MutableStateFlow(ResourceReleaseStatus())
    val status: StateFlow<ResourceReleaseStatus> = _status.asStateFlow()

    @Synchronized
    fun begin(): Long {
        val id = _status.value.lastRequestedId + 1
        _status.value = _status.value.copy(lastRequestedId = id)
        return id
    }

    @Synchronized
    fun complete(requestId: Long, failures: List<String>) {
        require(requestId in 1.._status.value.lastRequestedId)
        require(requestId > _status.value.lastCompletedId) { "Release IDs must complete in order" }
        _status.value = _status.value.copy(
            lastCompletedId = requestId,
            lastResult = ResourceReleaseResult(requestId, failures.toList()),
        )
    }

    suspend fun await(requestId: Long): ResourceReleaseResult =
        status.first { it.lastCompletedId >= requestId }.lastResult
            ?.takeIf { it.requestId >= requestId }
            ?: error("Release completion record is missing")
}

sealed interface VpnState {
    data object Idle : VpnState
    data object Connecting : VpnState

    /**
     * 底层网络不可用，正在等待网络恢复。
     * 与 [Connecting] 区分：此时并不是"正在连接"，用户等待多久都无济于事，
     * 界面应提示"等待网络恢复"而不是一直转圈，避免看起来像卡死。
     */
    data object WaitingNetwork : VpnState

    data class Connected(
        val serverName: String,
        val since: Long,
        val socksPort: Int = 10808,
        /** 内部 TUN 入站的随机凭据，measureSocksDelay 需要用到 */
        val tunnelUser: String = "",
        val tunnelPass: String = "",
    ) : VpnState
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

    private val releases = ResourceReleaseTracker()
    val resourceReleaseStatus: StateFlow<ResourceReleaseStatus> = releases.status

    internal fun beginResourceRelease(): Long = releases.begin()

    internal fun completeResourceRelease(requestId: Long, failures: List<String>) = releases.complete(requestId, failures)

    suspend fun awaitResourcesReleased(requestId: Long): ResourceReleaseResult = releases.await(requestId)

    private val _state = MutableStateFlow<VpnState>(VpnState.Idle)
    val state: StateFlow<VpnState> = _state.asStateFlow()

    private val _traffic = MutableStateFlow(TrafficSnapshot())
    val traffic: StateFlow<TrafficSnapshot> = _traffic.asStateFlow()

    /**
     * 用户点击「连接」的时刻（elapsedRealtime）。用于统计端到端感知耗时：
     * 从点击到界面真正显示「已连接」，包含调度、xray 启动与 Compose 渲染。
     */
    @Volatile
    var clickAt: Long = 0L

    /** 记录一次连接点击，并返回距今毫秒数。 */
    fun markClick(): Long {
        val now = android.os.SystemClock.elapsedRealtime()
        clickAt = now
        return now
    }

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

    /** 在"等待网络恢复"状态下由用户手动触发的重试。 */
    fun retry(context: Context) {
        val intent = Intent(context, IceFlowVpnService::class.java).setAction(IceFlowVpnService.ACTION_RETRY)
        runCatching { context.startService(intent) }
            .onFailure { Log.w("VpnController", "retry failed: $it") }
    }

    fun switch(context: Context) {
        val intent = Intent(context, IceFlowVpnService::class.java).setAction(IceFlowVpnService.ACTION_SWITCH)
        runCatching { context.startService(intent) }
            .onFailure { Log.w("VpnController", "switch failed: $it") }
    }
}
