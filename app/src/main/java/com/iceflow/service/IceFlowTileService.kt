package com.iceflow.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.iceflow.IceFlowApp
import com.iceflow.MainActivity
import com.iceflow.R
import com.iceflow.data.model.AppLanguage
import com.iceflow.i18n.stringsOf
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class IceFlowTileService : TileService() {
    private var scope: CoroutineScope? = null
    private var selectedName: String? = null
    private var recentErrorType: VpnErrorType? = null
    private val fallbackScope = MainScope()
    private var flowFrameIndex = 0
    @Volatile private var flowActive = false
    private var flowJob: kotlinx.coroutines.Job? = null

    private val flowFrames = listOf(
        R.drawable.ic_tile_flow_00, R.drawable.ic_tile_flow_01, R.drawable.ic_tile_flow_02,
        R.drawable.ic_tile_flow_03, R.drawable.ic_tile_flow_04, R.drawable.ic_tile_flow_05,
        R.drawable.ic_tile_flow_06, R.drawable.ic_tile_flow_07, R.drawable.ic_tile_flow_08,
        R.drawable.ic_tile_flow_09, R.drawable.ic_tile_flow_10, R.drawable.ic_tile_flow_11,
    )

    override fun onTileAdded() {
        super.onTileAdded()
        refreshFromCurrentState()
    }

    override fun onStartListening() {
        super.onStartListening()
        scope?.cancel()
        refreshFromCurrentState()
        scope = MainScope().also { listenerScope ->
            listenerScope.launch(start = CoroutineStart.UNDISPATCHED) {
                combine(
                    VpnController.state,
                    repository()?.store ?: kotlinx.coroutines.flow.flowOf(null),
                ) { state, store -> state to store?.selected?.name }
                    .collect { (state, name) ->
                        selectedName = name
                        recentErrorType = (state as? VpnState.Error)?.presentation?.type
                        render(state)
                    }
            }
        }
    }

    override fun onStopListening() {
        scope?.cancel()
        scope = null
        stopFlowLoop()
        super.onStopListening()
    }

    override fun onDestroy() {
        scope?.cancel()
        scope = null
        fallbackScope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()
        val currentState = VpnController.state.value
        when (VpnEntryPolicy.primary(currentState, VpnEntryPoint.TILE)) {
            VpnUserOperation.CANCEL, VpnUserOperation.STOP -> {
                if (!VpnController.requestStop(this)) reportDispatchFailure()
            }
            VpnUserOperation.IGNORE -> Unit
            VpnUserOperation.RETRY -> {
                if (!VpnController.retry(this)) reportDispatchFailure()
            }
            VpnUserOperation.CONNECT -> {
                if (VpnService.prepare(this) != null) {
                    openApp()
                } else {
                    recentErrorType = null
                    if (!VpnController.start(this)) reportDispatchFailure()
                }
            }
        }
    }

    private fun reportDispatchFailure() {
        val language = repository()?.let {
            AppLanguage.fromTag((application as IceFlowApp).container.settings.settings.value.language)
        } ?: AppLanguage.SIMPLIFIED_CHINESE
        VpnController.reportError(this, VpnErrorType.CORE_START_FAILED, language)
        recentErrorType = VpnErrorType.CORE_START_FAILED
        render(VpnController.state.value)
    }

    private fun refreshFromCurrentState() {
        selectedName = repository()?.selected?.name
        val currentState = VpnController.state.value
        recentErrorType = (currentState as? VpnState.Error)?.presentation?.type
        render(currentState)
    }

    private fun tileLabels(language: AppLanguage): VpnTileLabels {
        val strings = stringsOf(language)
        return VpnTileLabels(
            disconnected = strings.get("state.idle"),
            connecting = strings.get("state.connecting"),
            connected = strings.get("state.connected"),
            waitingNetwork = strings.get("state.waitingNetwork"),
            stopping = strings.get("state.stopping"),
        )
    }

    private fun render(state: VpnState) {
        val tile = qsTile ?: return
        val language = AppLanguage.fromTag((application as? IceFlowApp)?.container?.settings?.settings?.value?.language)
        val labels = tileLabels(language)
        val presentation = VpnTilePolicy.presentation(state, recentErrorType, language, labels)
        if (state == VpnState.Connecting || state == VpnState.WaitingNetwork) {
            flowActive = true
            startFlowLoop()
        } else {
            stopFlowLoop()
        }

        val nodeName = if (presentation.showNodeName) {
            (state as? VpnState.Connected)?.serverName?.takeIf { it.isNotBlank() } ?: selectedName?.takeIf { it.isNotBlank() }
        } else null
        val shortNode = nodeName?.let { if (it.length > 12) it.take(11) + "..." else it }
        tile.state = when (presentation.state) {
            VpnTileState.ACTIVE -> Tile.STATE_ACTIVE
            VpnTileState.INACTIVE -> Tile.STATE_INACTIVE
        }
        tile.icon = Icon.createWithResource(this, iconFor(presentation.icon))
        tile.contentDescription = listOfNotNull("IceFlow", presentation.subtitle.ifBlank { labels.disconnected }, nodeName).joinToString(" · ")
        tile.label = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && state is VpnState.Error) {
            VpnErrorCatalog.shortLabel(state.presentation.type, language)
        } else shortNode ?: getString(R.string.app_name)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = presentation.subtitle
        tile.updateTile()
    }

    private fun iconFor(icon: VpnTileIcon): Int = when (icon) {
        VpnTileIcon.ACTIVE -> R.drawable.ic_tile_active
        VpnTileIcon.CONNECTING, VpnTileIcon.WAITING_NETWORK -> flowFrames[flowFrameIndex % flowFrames.size]
        VpnTileIcon.DEFAULT, VpnTileIcon.STOPPING, VpnTileIcon.ERROR -> R.drawable.ic_tile
    }

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

    private fun stopFlowLoop() {
        flowActive = false
        flowJob?.cancel()
        flowJob = null
    }

    private fun renderIconOnly() {
        val tile = qsTile ?: return
        tile.icon = Icon.createWithResource(this, flowFrames[flowFrameIndex % flowFrames.size])
        tile.updateTile()
    }

    private fun repository() = (applicationContext as? IceFlowApp)?.container?.servers

    @SuppressLint("StartActivityAndCollapseDeprecated")
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
