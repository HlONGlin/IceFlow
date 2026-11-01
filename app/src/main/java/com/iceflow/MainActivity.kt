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
import com.iceflow.core.XrayBridge
import com.iceflow.data.DeepLinkParser
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
        private const val EXTRA_DEEP_LINK_CONSUMED = "com.iceflow.extra.DEEP_LINK_CONSUMED"
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
        if (savedInstanceState == null) {
            handleTileIntent(intent)
            handleDeepLinkIntent(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleTileIntent(intent)
        handleDeepLinkIntent(intent)
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

    /**
     * 深链接 / 分享导入入口：只把解析结果交给 ViewModel 弹出确认框，
     * 不写入配置、不自动连接。处理完立刻清空 data、文本与 clipData，
     * 并打上消费标记，旋转、重建或重复 onNewIntent 都不会重复消费同一个 Intent。
     */
    private fun handleDeepLinkIntent(intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_VIEW && action != Intent.ACTION_SEND) return
        if (intent.getBooleanExtra(EXTRA_DEEP_LINK_CONSUMED, false)) return
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
        val payload = DeepLinkParser.Payload(
            action = action,
            uri = intent.dataString,
            mimeType = intent.type,
            text = text,
            displayName = intent.data?.takeIf { it.scheme == "content" }?.let(::queryDisplayName),
        )
        intent.putExtra(EXTRA_DEEP_LINK_CONSUMED, true)
        intent.removeExtra(Intent.EXTRA_TEXT)
        intent.clipData = null
        intent.data = null
        vm.submitDeepLink(DeepLinkParser.parse(payload, supportsMieruProtobuf = XrayBridge.isAvailable))
    }

    /** content:// 的显示名，用于校验配置文件扩展名。 */
    private fun queryDisplayName(uri: android.net.Uri): String? = runCatching {
        contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

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
