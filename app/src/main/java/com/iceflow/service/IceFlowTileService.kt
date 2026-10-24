package com.iceflow.service

import android.app.PendingIntent
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.iceflow.MainActivity
import com.iceflow.R
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** 下拉快捷开关。未授权 VPN 时跳转到应用内完成授权。 */
class IceFlowTileService : TileService() {

    private var scope: CoroutineScope? = null

    override fun onStartListening() {
        super.onStartListening()
        scope?.cancel()
        render(VpnController.state.value)
        scope = MainScope().also { s ->
            s.launch(start = CoroutineStart.UNDISPATCHED) { VpnController.state.collect { render(it) } }
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
            VpnState.Stopping -> "断开中"
            else -> "已断开"
        }
        tile.state = if (connected) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.icon = Icon.createWithResource(this, if (connected) R.drawable.ic_tile else R.drawable.ic_tile_off)
        tile.contentDescription = "IceFlow · $status"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = status
        }
        tile.updateTile()
    }

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
