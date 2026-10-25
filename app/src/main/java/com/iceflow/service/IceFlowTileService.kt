package com.iceflow.service

import android.app.PendingIntent
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.VpnService
import android.os.Build
import android.os.SystemClock
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.iceflow.IceFlowApp
import com.iceflow.MainActivity
import com.iceflow.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive

/** 下拉快捷开关。未授权 VPN 时跳转到应用内完成授权。 */
class IceFlowTileService : TileService() {

    private var scope: CoroutineScope? = null

    /** 当前选中的节点名称，用于磁贴副标题。 */
    private var selectedName: String? = null

    /**
     * 用户刚点了「连接」的截止时刻（elapsedRealtime）。
     * 点击后状态往往还没变成 Connecting，这段窗口期内磁贴显示水流动画，
     * 保证「点一下就有反馈」，而不是干等到连接真正开始。
     * 用时间戳而不是布尔量：磁贴被重新列举（onStartListening）时不会把它抹掉。
     */
    private var optimisticUntil: Long = 0L

    /** 乐观显示窗口长度。 */
    private val optimisticWindowMs = 10_000L

    /**
     * 水流动画帧。磁贴图标由 SystemUI 进程绘制，animated-vector 在那边不会真的跑起来，
     * 所以改成由本进程按帧率切换图标并主动 updateTile()，SystemUI 才会持续重绘。
     */
    private val flowFrames = listOf(
        R.drawable.ic_tile_flow_00, R.drawable.ic_tile_flow_01, R.drawable.ic_tile_flow_02,
        R.drawable.ic_tile_flow_03, R.drawable.ic_tile_flow_04, R.drawable.ic_tile_flow_05,
        R.drawable.ic_tile_flow_06, R.drawable.ic_tile_flow_07, R.drawable.ic_tile_flow_08,
        R.drawable.ic_tile_flow_09, R.drawable.ic_tile_flow_10, R.drawable.ic_tile_flow_11,
    )

    /** 当前显示的动画帧序号。 */
    private var flowFrameIndex = 0

    /** 是否正在播放水流动画。由 render() 根据状态维护。 */
    @Volatile
    private var flowActive = false

    /** 驱动水流帧切换的循环任务，仅在「连接中」时运行。 */
    private var flowJob: Job? = null

    /**
     * 乐观显示的兜底任务：状态迟迟不变时收回动画，避免磁贴永远显示「连接中」。
     * 用独立于磁贴监听生命周期的 scope：磁贴被重新列举时监听 scope 会被重建，
     * 但这个计时器必须继续跑，否则动画会一直挂着。
     */
    private val fallbackScope = MainScope()
    private var optimisticJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        scope?.cancel()
        selectedName = repository()?.selected?.name
        // 用 displayState：磁贴重新列举时也要保留乐观窗口内的「连接中」动画，
        // 否则点击后磁贴一被列举就会退回默认图标。
        render(displayState(VpnController.state.value))
        scope = MainScope().also { s ->
            s.launch(start = CoroutineStart.UNDISPATCHED) {
                // 同时订阅连接状态与节点列表：连接中显示正在使用的节点，
                // 未连接时显示已选节点，用户在磁贴上就能看到会连到哪台。
                combine(VpnController.state, repository()?.store ?: kotlinx.coroutines.flow.flowOf(null)) { state, store ->
                    state to store?.selected?.name
                }.collect { (state, name) ->
                    selectedName = name
                    // 状态真的开始连接了（或已经结束），解除乐观显示。
                    if (state !is VpnState.Idle) {
                        optimisticUntil = 0L
                        optimisticJob?.cancel()
                        optimisticJob = null
                    }
                    render(displayState(state))
                }
            }
        }
    }

    override fun onStopListening() {
        scope?.cancel()
        scope = null
        // 磁贴不可见时不必继续刷帧，省电也避免无谓的 IPC。
        stopFlowLoop()
        super.onStopListening()
    }

    override fun onDestroy() {
        scope?.cancel()
        scope = null
        optimisticJob?.cancel()
        optimisticJob = null
        fallbackScope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()
        // 用 displayState 而不是真实状态：乐观窗口内磁贴已经显示「连接中」，
        // 此时再点一次应该理解为「取消连接」，而不是再发一次启动指令。
        when (displayState(VpnController.state.value)) {
            is VpnState.Connected, VpnState.Connecting -> {
                // 收回乐观显示：真实状态已经接管，避免兜底任务又把磁贴改回「连接中」。
                optimisticUntil = 0L
                optimisticJob?.cancel()
                optimisticJob = null
                render(VpnState.Stopping)
                VpnController.stop(this)
            }
            VpnState.Stopping -> render(VpnState.Stopping)
            VpnState.WaitingNetwork -> {
                // 等待网络恢复时，点击磁贴立即重试一次
                render(VpnState.Connecting)
                runCatching { VpnController.retry(this) }.onFailure { render(VpnController.state.value) }
            }
            else -> {
                if (VpnService.prepare(this) != null) {
                    render(VpnController.state.value)
                    openApp()
                } else {
                    // 点击瞬间先切到「连接中」图标（水流弧开始旋转），
                    // 让用户立刻看到流动反馈，而不是等连接真正建立才有变化。
                    optimisticUntil = SystemClock.elapsedRealtime() + optimisticWindowMs
                    render(VpnState.Connecting)
                    // 兜底：10 秒内状态仍未变化（服务没起来或没有可用节点），
                    // 就把乐观显示收回，磁贴回到真实状态。
                    optimisticJob = fallbackScope.launch {
                        delay(optimisticWindowMs)
                        if (optimisticUntil > 0) {
                            optimisticUntil = 0L
                            render(VpnController.state.value)
                        }
                    }
                    runCatching { VpnController.start(this) }.onFailure {
                        optimisticUntil = 0L
                        render(VpnController.state.value)
                        openApp()
                    }
                }
            }
        }
    }

    /**
     * 实际用于渲染的状态：点击后的乐观窗口内，即使真实状态还是 Idle，
     * 也按「连接中」渲染，让水流动画立刻转起来。
     */
    private fun displayState(state: VpnState): VpnState =
        if (state is VpnState.Idle && optimisticUntil > SystemClock.elapsedRealtime()) VpnState.Connecting else state

    private fun render(state: VpnState) {
        val tile = qsTile ?: return
        // 流动态：启动帧循环；其他状态：停掉。
        if (state is VpnState.Connecting || state is VpnState.WaitingNetwork) {
            flowActive = true
            startFlowLoop()
        } else {
            stopFlowLoop()
        }
        val connected = state is VpnState.Connected
        val status = when (state) {
            is VpnState.Connected -> "已连接"
            VpnState.Connecting -> "连接中"
            VpnState.WaitingNetwork -> "等待网络"
            VpnState.Stopping -> "断开中"
            else -> "已断开"
        }
        // 已连接时用实际在用的节点名（连接过程中切换过节点也能对上），
        // 未连接时用当前选中的节点，用户在磁贴上就能看到会连到哪台。
        val nodeName = (state as? VpnState.Connected)?.serverName?.takeIf { it.isNotBlank() }
            ?: selectedName?.takeIf { it.isNotBlank() }
        tile.state = if (connected) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.icon = Icon.createWithResource(this, iconFor(state))
        tile.contentDescription = if (nodeName != null) "IceFlow · $status · $nodeName" else "IceFlow · $status"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // 副标题优先显示节点名，节点名过长时截断，避免磁贴排版被撑破。
            tile.subtitle = nodeName?.let { if (it.length > 18) it.take(17) + "…" else it } ?: status
        } else {
            // Android 9 及以下没有 subtitle，改用 label 承载节点名，否则用户看不到。
            tile.label = nodeName?.let { if (it.length > 14) it.take(13) + "…" else it } ?: getString(R.string.app_name)
        }
        tile.updateTile()
    }

    /**
     * 按状态挑选磁贴图标：已连接实心、连接中流水动画、未连接描边。
     * 连接中取当前动画帧（由 startFlowLoop 每 70ms 推进一帧），
     * 一颗高光珠沿水流弧滑动，明确表达「正在流动」。
     */
    private fun iconFor(state: VpnState): Int = when (state) {
        // 已连接：实心雪花，与默认态明确区分
        is VpnState.Connected -> R.drawable.ic_tile_active
        // 连接中/等待网络：水流动画帧，点击后立刻开始转，形成流动感
        is VpnState.Connecting, is VpnState.WaitingNetwork -> flowFrames[flowFrameIndex % flowFrames.size]
        // 未连接：与应用图标同款
        else -> R.drawable.ic_tile
    }

    /** 启动水流帧循环：每 70ms 换一帧并刷新磁贴。 */
    private fun startFlowLoop() {
        if (flowJob?.isActive == true) return
        flowJob = fallbackScope.launch {
            while (isActive && flowActive) {
                delay(70)
                if (!flowActive) break
                flowFrameIndex = (flowFrameIndex + 1) % flowFrames.size
                renderIconOnly()
            }
        }
    }

    /** 停止水流帧循环。 */
    private fun stopFlowLoop() {
        flowActive = false
        flowJob?.cancel()
        flowJob = null
    }

    /**
     * 只刷新图标，不动其它状态。
     * 走 updateTile() 全量更新会重置副标题等，这里保持内容描述不变。
     */
    private fun renderIconOnly() {
        val tile = qsTile ?: return
        tile.icon = Icon.createWithResource(this, flowFrames[flowFrameIndex % flowFrames.size])
        tile.updateTile()
    }

    /** 取到节点仓库；Application 尚未初始化时返回 null。 */
    private fun repository() = (applicationContext as? IceFlowApp)?.container?.servers

    @SuppressLint("StartActivityAndCollapseDeprecated") // Intent overload is only used below API 34.
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(MainActivity.EXTRA_CONNECT_FROM_TILE, true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
