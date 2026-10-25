package com.iceflow.service

import android.app.PendingIntent
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.iceflow.IceFlowApp
import com.iceflow.MainActivity
import com.iceflow.R
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** 下拉快捷开关。未授权 VPN 时跳转到应用内完成授权。 */
class IceFlowTileService : TileService() {

    private var scope: CoroutineScope? = null

    /** 当前选中的节点名称，用于磁贴副标题。 */
    private var selectedName: String? = null

    override fun onStartListening() {
        super.onStartListening()
        scope?.cancel()
        selectedName = repository()?.selected?.name
        render(VpnController.state.value)
        scope = MainScope().also { s ->
            s.launch(start = CoroutineStart.UNDISPATCHED) {
                // 同时订阅连接状态与节点列表：连接中显示正在使用的节点，
                // 未连接时显示已选节点，用户在磁贴上就能看到会连到哪台。
                combine(VpnController.state, repository()?.store ?: kotlinx.coroutines.flow.flowOf(null)) { state, store ->
                    state to store?.selected?.name
                }.collect { (state, name) ->
                    selectedName = name
                    render(state)
                }
            }
        }
    }

    override fun onStopListening() {
        scope?.cancel()
        scope = null
        super.onStopListening()
    }

    override fun onDestroy() {
        scope?.cancel()
        scope = null
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()
        when (VpnController.state.value) {
            is VpnState.Connected, VpnState.Connecting -> {
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
                    render(VpnState.Connecting)
                    runCatching { VpnController.start(this) }.onFailure {
                        render(VpnController.state.value)
                        openApp()
                    }
                }
            }
        }
    }

    private fun render(state: VpnState) {
        val tile = qsTile ?: return
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

    /** 按状态挑选磁贴图标：已连接实心、连接中虚线、未连接描边。 */
    private fun iconFor(state: VpnState): Int = when (state) {
        is VpnState.Connected -> R.drawable.ic_tile
        VpnState.Connecting, VpnState.WaitingNetwork -> R.drawable.ic_tile_connecting
        else -> R.drawable.ic_tile_off
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
