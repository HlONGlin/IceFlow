package com.iceflow

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.iceflow.service.VpnController
import com.iceflow.service.VpnState
import com.iceflow.service.AppLog
import com.iceflow.ui.IceFlowRoot
import com.iceflow.ui.MainViewModel
import com.iceflow.ui.theme.IceFlowTheme

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_CONNECT_FROM_TILE = "com.iceflow.extra.CONNECT_FROM_TILE"
    }

    private val vm: MainViewModel by viewModels()

    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) VpnController.start(this)
    }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 拒绝也不影响 VPN 功能 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.write(this, "INFO", "应用启动")
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            IceFlowTheme(theme = settings.theme, nightMode = settings.nightMode) {
                IceFlowRoot(vm = vm, onToggleVpn = ::toggleVpn)
            }
        }
        if (savedInstanceState == null) handleTileIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleTileIntent(intent)
    }

    private fun handleTileIntent(intent: Intent) {
        if (!intent.getBooleanExtra(EXTRA_CONNECT_FROM_TILE, false)) return
        intent.removeExtra(EXTRA_CONNECT_FROM_TILE)
        when (VpnController.state.value) {
            VpnState.Idle, is VpnState.Error -> toggleVpn()
            else -> Unit
        }
    }

    private fun toggleVpn() {
        when (VpnController.state.value) {
            is VpnState.Connected, VpnState.Connecting -> VpnController.stop(this)
            VpnState.Stopping -> Unit
            else -> {
                if (!vm.canConnect()) return
                val intent = VpnService.prepare(this)
                if (intent != null) vpnPermission.launch(intent) else VpnController.start(this)
            }
        }
    }
}
