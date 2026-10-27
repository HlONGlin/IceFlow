package com.iceflow.service

enum class VpnEntryPoint { HOME, ACTIVITY, TILE, NOTIFICATION }

enum class VpnUserOperation { CONNECT, CANCEL, RETRY, STOP, IGNORE }

/** The primary action is shared across entry points; WaitingNetwork also exposes an explicit stop action. */
object VpnEntryPolicy {
    fun primary(state: VpnState, @Suppress("UNUSED_PARAMETER") entryPoint: VpnEntryPoint): VpnUserOperation = when (state) {
        VpnState.Idle -> VpnUserOperation.CONNECT
        VpnState.Connecting -> VpnUserOperation.CANCEL
        is VpnState.Connected -> VpnUserOperation.STOP
        VpnState.WaitingNetwork -> VpnUserOperation.RETRY
        VpnState.Stopping -> VpnUserOperation.IGNORE
        is VpnState.Error -> VpnUserOperation.CONNECT
    }

    fun secondary(state: VpnState, @Suppress("UNUSED_PARAMETER") entryPoint: VpnEntryPoint): VpnUserOperation? =
        VpnUserOperation.STOP.takeIf { state == VpnState.WaitingNetwork }
}
