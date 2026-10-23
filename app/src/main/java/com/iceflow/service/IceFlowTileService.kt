package com.iceflow.service

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.iceflow.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** 下拉快捷开关。未授权 VPN 时跳转到应用内完成授权。 */
class IceFlowTileService : TileService() {

    private var scope: CoroutineScope? = null

    override fun onStartListening() {
        super.onStartListening()
        scope = MainScope().also { s ->
            s.launch { VpnController.state.collect { render(it) } }
        }
    }

    override fun onStopListening() {
        scope?.cancel()
        scope = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        when (VpnController.state.value) {
            is VpnState.Connected, VpnState.Connecting -> VpnController.stop(this)
            VpnState.Stopping -> Unit
            else -> {
                if (VpnService.prepare(this) != null) {
                    openApp()
                } else {
                    runCatching { VpnController.start(this) }.onFailure { openApp() }
                }
            }
        }
    }

    private fun render(state: VpnState) {
        val tile = qsTile ?: return
        tile.state = if (state is VpnState.Connected) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when (state) {
                is VpnState.Connected -> state.serverName
                VpnState.Connecting -> "连接中"
                else -> null
            }
        }
        tile.updateTile()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
