package com.iceflow.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface PublicIpState {
    data object Idle : PublicIpState
    data object Loading : PublicIpState
    data class Success(val address: String) : PublicIpState
    data object Failed : PublicIpState
}

/** Owned by the ViewModel's serial VPN-state collector; stale lookups cannot republish an IP. */
class PublicIpLookup(private val logFailure: (Throwable) -> Unit) {
    private val mutableState = MutableStateFlow<PublicIpState>(PublicIpState.Idle)
    val state: StateFlow<PublicIpState> = mutableState.asStateFlow()
    private var generation = 0L

    fun reset() {
        generation++
        mutableState.value = PublicIpState.Idle
    }

    suspend fun query(lookup: suspend () -> String) {
        val token = ++generation
        mutableState.value = PublicIpState.Loading
        try {
            val address = lookup()
            if (token == generation) mutableState.value = PublicIpState.Success(address)
        } catch (cancelled: CancellationException) {
            if (token == generation) mutableState.value = PublicIpState.Idle
            throw cancelled
        } catch (failure: Throwable) {
            if (token == generation) mutableState.value = PublicIpState.Failed
            logFailure(failure)
        }
    }
}
