package com.iceflow.service

import com.iceflow.data.model.AppLanguage

enum class VpnTileState { INACTIVE, ACTIVE }
enum class VpnTileIcon { DEFAULT, CONNECTING, WAITING_NETWORK, ACTIVE, STOPPING, ERROR }

data class VpnTileLabels(
    val disconnected: String,
    val connecting: String,
    val connected: String,
    val waitingNetwork: String,
    val stopping: String,
)

data class VpnTilePresentation(
    val state: VpnTileState,
    val subtitle: String,
    val icon: VpnTileIcon,
    val showNodeName: Boolean,
)

/** Pure Quick Settings rendering policy; command meaning remains owned by [VpnEntryPolicy]. */
object VpnTilePolicy {
    fun presentation(
        state: VpnState,
        recentError: VpnErrorType?,
        language: AppLanguage,
        labels: VpnTileLabels,
    ): VpnTilePresentation {
        val errorType = (state as? VpnState.Error)?.let { recentError ?: it.presentation.type }
        if (errorType != null) {
            return VpnTilePresentation(
                state = VpnTileState.INACTIVE,
                subtitle = VpnErrorCatalog.shortLabel(errorType, language),
                icon = VpnTileIcon.ERROR,
                showNodeName = false,
            )
        }

        return when (state) {
            is VpnState.Connected -> VpnTilePresentation(VpnTileState.ACTIVE, labels.connected, VpnTileIcon.ACTIVE, true)
            VpnState.Connecting -> VpnTilePresentation(VpnTileState.INACTIVE, labels.connecting, VpnTileIcon.CONNECTING, true)
            VpnState.WaitingNetwork -> VpnTilePresentation(VpnTileState.INACTIVE, labels.waitingNetwork, VpnTileIcon.WAITING_NETWORK, true)
            VpnState.Stopping -> VpnTilePresentation(VpnTileState.INACTIVE, labels.stopping, VpnTileIcon.STOPPING, false)
            VpnState.Idle, is VpnState.Error -> VpnTilePresentation(VpnTileState.INACTIVE, "", VpnTileIcon.DEFAULT, false)
        }
    }
}
