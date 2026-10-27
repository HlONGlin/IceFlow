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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.iceflow.service.VpnController
import com.iceflow.service.VpnEntryPoint
import com.iceflow.service.AppLog
import com.iceflow.service.VpnErrorType
import com.iceflow.ui.IceFlowRoot
import com.iceflow.ui.MainViewModel
import com.iceflow.ui.theme.IceFlowTheme
import com.iceflow.data.model.AppLanguage

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_CONNECT_FROM_TILE = "com.iceflow.extra.CONNECT_FROM_TILE"
        const val EXTRA_OPEN_LOGS = "com.iceflow.extra.OPEN_LOGS"
    }

    private val vm: MainViewModel by viewModels()
    private var openLogsRequested by mutableStateOf(false)

    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) VpnController.start(this)
        else VpnController.reportError(this, VpnErrorType.VPN_AUTHORIZATION_DENIED, AppLanguage.fromTag(vm.settings.value.language))
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
            IceFlowTheme(
                theme = settings.theme,
                nightMode = settings.nightMode,
                textSize = settings.textSize,
                language = com.iceflow.data.model.AppLanguage.fromTag(settings.language),
            ) {
                IceFlowRoot(vm = vm, onToggleVpn = ::toggleVpn, openLogsRequested = openLogsRequested) {
                    openLogsRequested = false
                }
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
        if (intent.getBooleanExtra(EXTRA_OPEN_LOGS, false)) {
            intent.removeExtra(EXTRA_OPEN_LOGS)
            openLogsRequested = true
        }
        if (!intent.getBooleanExtra(EXTRA_CONNECT_FROM_TILE, false)) return
        intent.removeExtra(EXTRA_CONNECT_FROM_TILE)
        toggleVpn(VpnEntryPoint.ACTIVITY)
    }

    private fun toggleVpn(entryPoint: VpnEntryPoint = VpnEntryPoint.ACTIVITY) {
        VpnController.dispatchEntryAction(this, entryPoint, connect = {
            if (vm.canConnect()) {
                VpnController.markClick()
                val intent = VpnService.prepare(this)
                if (intent != null) vpnPermission.launch(intent) else VpnController.start(this)
            }
        })
    }
}
