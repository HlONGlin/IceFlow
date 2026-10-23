package com.iceflow.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.iceflow.IceFlowApp
import com.iceflow.MainActivity
import com.iceflow.R
import com.iceflow.core.RustBridge
import com.iceflow.core.XrayBridge
import com.iceflow.data.XrayConfigBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * VPN 前台服务。
 *
 * 启动顺序：xray-core (SOCKS5 入站) → 建立 TUN → Rust tun2proxy 将 TUN 流量转发到 SOCKS5。
 * 本应用自身被排除在 VPN 之外，因此 xray-core 的出站连接直接走物理网络，不会回环。
 */
class IceFlowVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.iceflow.action.START"
        const val ACTION_STOP = "com.iceflow.action.STOP"
        const val CHANNEL_ID = "vpn"

        private const val TAG = "IceFlowVpnService"
        private const val NOTIFICATION_ID = 1
        private const val MTU = 1500
        private const val TUN_ADDRESS_V4 = "172.19.0.1"
        private const val TUN_ADDRESS_V6 = "fdfe:dcba:9876::1"
    }

    // 启停操作串行执行，避免竞态
    @OptIn(ExperimentalCoroutinesApi::class)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private var tun: ParcelFileDescriptor? = null
    private var statsJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            scope.launch { stopVpn() }
            return START_NOT_STICKY
        }
        // startForegroundService 后必须尽快调用 startForeground
        startForegroundCompat(buildNotification(getString(R.string.notification_connecting)))
        scope.launch { startVpn() }
        return START_STICKY
    }

    override fun onRevoke() {
        // 其他 VPN 应用接管或用户在系统设置中断开
        scope.launch { stopVpn() }
    }

    override fun onDestroy() {
        scope.cancel()
        if (tun != null) {
            Thread({
                cleanup()
                VpnController.setState(VpnState.Idle)
            }, "iceflow-cleanup").start()
        }
        super.onDestroy()
    }

    private fun startVpn() {
        if (tun != null) return
        VpnController.setState(VpnState.Connecting)
        AppLog.write(this, "INFO", "开始连接 VPN")

        val container = (application as IceFlowApp).container
        val profile = container.servers.selected ?: return fail("请先选择节点")
        val settings = container.settings.settings.value
        if (!XrayBridge.isAvailable) return fail("未集成 xray-core（缺少 app/libs/xray.aar）")
        if (!RustBridge.isAvailable) return fail("未集成 Rust 核心（缺少 libiceflow_core.so）")

        try {
            AppLog.write(this, "INFO", "准备连接节点：${profile.name} [${profile.protocol.label}] ${profile.address}:${profile.port}")
            AppLog.write(this, "INFO", "代理方案：Xray 核心 + TUN 转发，DNS=${settings.dnsServers.joinToString(",")}, IPv6=${settings.ipv6Enabled}, 分应用代理=${settings.proxyAppsOnly}")
            if (settings.bypassApps.isNotEmpty()) AppLog.write(this, "INFO", "应用策略包数量：${settings.bypassApps.size}")
            XrayBridge.setAssetDir(filesDir.absolutePath)
            XrayBridge.start(XrayConfigBuilder.build(profile, settings, store = container.servers.store.value))

            val builder = Builder()
                .setSession(profile.name)
                .setMtu(MTU)
                .addAddress(TUN_ADDRESS_V4, 30)
                .addRoute("0.0.0.0", 0)
                // 同时接管 IPv6，防止 IPv6 流量绕过隧道泄漏
                .addDnsServer(settings.dnsServers.firstOrNull() ?: settings.remoteDns)
                .addDisallowedApplication(packageName)
            if (settings.ipv6Enabled) {
                builder.addAddress(TUN_ADDRESS_V6, 126).addRoute("::", 0)
            }
            val installed = packageManager.getInstalledApplications(0).map { it.packageName }.filter { it != packageName }
            if (settings.proxyAppsOnly) {
                installed.filterNot { it in settings.bypassApps }.forEach { runCatching { builder.addDisallowedApplication(it) } }
            } else {
                settings.bypassApps.forEach { runCatching { builder.addDisallowedApplication(it) } }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)

            val pfd = builder.establish() ?: throw IllegalStateException("VPN 权限未授予")
            tun = pfd

            val dnsMode = if (settings.fakeDns) RustBridge.DNS_VIRTUAL else RustBridge.DNS_OVER_TCP
            val code = RustBridge.startTun(
                fd = pfd.fd,
                mtu = MTU,
                proxyUrl = "socks5://127.0.0.1:${settings.socksPort}",
                dnsMode = dnsMode,
                dnsAddr = settings.remoteDns,
            )
            check(code == 0) { RustBridge.describe(code) }

            VpnController.setState(VpnState.Connected(profile.name, System.currentTimeMillis(), settings.socksPort))
            getSystemService(NotificationManager::class.java).notify(
                NOTIFICATION_ID,
                buildNotification(getString(R.string.notification_connected, profile.name)),
            )
            statsJob = scope.launch(Dispatchers.Default) { statsLoop() }
            Log.i(TAG, "VPN started: ${profile.name}")
            AppLog.write(this, "INFO", "VPN 已连接: ${profile.name}")
        } catch (t: Throwable) {
            Log.e(TAG, "start failed", t)
            fail(t.message ?: t.toString())
        }
    }

    private fun stopVpn() {
        VpnController.setState(VpnState.Stopping)
        cleanup()
        VpnController.setState(VpnState.Idle)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun fail(message: String) {
        cleanup()
        VpnController.setState(VpnState.Error(message))
        AppLog.write(this, "ERROR", "VPN 失败: $message")
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** 释放顺序：统计 → tun2proxy（不持有 fd）→ xray-core → 关闭 TUN fd。 */
    @Synchronized
    private fun cleanup() {
        statsJob?.cancel()
        statsJob = null
        runCatching { RustBridge.stopTun() }.onFailure { Log.w(TAG, "stopTun: $it") }
        runCatching { XrayBridge.stop() }.onFailure { Log.w(TAG, "xray stop: $it") }
        runCatching { tun?.close() }
        tun = null
        VpnController.setTraffic(TrafficSnapshot())
    }

    private suspend fun statsLoop() {
        val initial = RustBridge.stats()
        val sampler = TrafficSampler(initial[0], initial[1], SystemClock.elapsedRealtime())
        while (true) {
            delay(1000)
            val stats = RustBridge.stats()
            VpnController.setTraffic(sampler.sample(stats[0], stats[1], SystemClock.elapsedRealtime()))
        }
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, IceFlowVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, getString(R.string.notification_disconnect), stop)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startForegroundCompat(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }
}
