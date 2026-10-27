package com.iceflow.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
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
import kotlinx.coroutines.channels.Channel
import kotlin.math.min
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.combine
import java.net.InetAddress
import java.net.ServerSocket

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
        const val ACTION_SWITCH = "com.iceflow.action.SWITCH"
        const val ACTION_RETRY = "com.iceflow.action.RETRY"
        const val CHANNEL_ID = "vpn"

        private const val TAG = "IceFlowVpnService"
        private const val NOTIFICATION_ID = 1
        private const val MTU = 1500
        private const val TUN_ADDRESS_V4 = "172.19.0.1"
        private const val TUN_ADDRESS_V6 = "fdfe:dcba:9876::1"
    }

    private sealed interface Command {
        data object Start : Command
        data class Stop(val startId: Int? = null) : Command
        data object Switch : Command
        data object CoreFailed : Command
        /** 用户在"等待网络恢复"时手动点击重试 */
        data object RetryNow : Command
        data class Retry(val generation: Long) : Command
        data class Available(val network: Network) : Command
        data class Lost(val network: Network) : Command
    }

    // A single consumer also serializes commands across retry delays.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val commands = Channel<Command>(Channel.UNLIMITED)
    private val operationLock = Any()
    private var tun: ParcelFileDescriptor? = null
    private var statsJob: Job? = null
    private var retryJob: Job? = null
    private var generation = 0L
    private var retryAttempt = 0
    private var desiredRunning = false
    private var activeServerId: String? = null
    // 缓存当前连接的 tunnel 配置，用于复用 xray 时保持一致
    private var activeTunnelPort: Int = 0
    private var activeTunnelUser: String = ""
    private var activeTunnelPass: String = ""
    private var persistedUp = 0L
    private var persistedDown = 0L
    private var persistedAt = 0L
    @Volatile private var destroyed = false
    private val networks = mutableSetOf<Network>()
    private var networkCallbackRegistered = false
    private val connectivity by lazy { getSystemService(ConnectivityManager::class.java) }
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { commands.trySend(Command.Available(network)) }
        override fun onLost(network: Network) { commands.trySend(Command.Lost(network)) }
    }

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "[TIMING] service.onCreate")
        AppLog.write(this, "INFO", "[计时] service.onCreate")
        scope.launch {
            for (command in commands) synchronized(operationLock) {
                if (!destroyed) handle(command)
            }
        }
        scope.launch {
            val container = (application as IceFlowApp).container
            combine(container.settings.settings, container.servers.store) { settings, store ->
                settings to runCatching {
                    store.selected?.let { XrayConfigBuilder.build(it, settings, includeInbound = false, store = store) }
                }.fold({ it }, { "invalid:${it.message}" })
            }
                .distinctUntilChanged { old, new -> old.first.hasSameConnection(new.first) && old.second == new.second }
                .drop(1)
                // 配置变更时快速重连：debounce 从 400ms 降至 150ms，减少等待时间
                .debounce(150)
                .collect { commands.send(Command.Switch) }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        runCatching { connectivity.registerNetworkCallback(request, networkCallback) }
            .onSuccess { networkCallbackRegistered = true }
            .onFailure { AppLog.write(this, "WARN", "无法监听网络变化：${it.message}") }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "[TIMING] service.onStartCommand action=${intent?.action}")
        AppLog.write(this, "INFO", "[计时] service.onStartCommand action=${intent?.action}")
        val currentState = VpnController.state.value
        val requestedAction = intent?.action
        val command = when {
            requestedAction == ACTION_STOP && currentState == VpnState.Stopping -> return START_NOT_STICKY
            requestedAction == ACTION_STOP -> Command.Stop(startId)
            requestedAction == ACTION_START -> when (VpnEntryPolicy.primary(currentState, VpnEntryPoint.NOTIFICATION)) {
                VpnUserOperation.CONNECT -> Command.Start
                VpnUserOperation.CANCEL, VpnUserOperation.STOP -> Command.Stop(startId)
                VpnUserOperation.RETRY -> Command.RetryNow
                VpnUserOperation.IGNORE -> return START_NOT_STICKY
            }
            requestedAction == ACTION_SWITCH -> Command.Switch
            requestedAction == ACTION_RETRY -> Command.RetryNow
            else -> Command.Start
        }
        if (command !is Command.Stop && !VpnController.canStartConnection()) {
            return START_NOT_STICKY
        }
        if (command !is Command.Stop) {
            val connected = VpnController.state.value as? VpnState.Connected
            val text = if (command == Command.Start && connected != null) getString(R.string.notification_connected, connected.serverName)
                else getString(R.string.notification_connecting)
            startForegroundCompat(buildNotification(text))
        }
        commands.trySend(command)
        return if (command is Command.Stop) START_NOT_STICKY else START_STICKY
    }

    override fun onRevoke() {
        commands.trySend(Command.Stop())
    }

    override fun onDestroy() {
        destroyed = true
        if (networkCallbackRegistered) runCatching { connectivity.unregisterNetworkCallback(networkCallback) }
        commands.close()
        scope.cancel()
        synchronized(operationLock) {
            desiredRunning = false
            val release = releaseResources(closeTun = true, stopXray = true)
            if (release.succeeded && VpnController.state.value !is VpnState.Error) {
                VpnController.setState(VpnState.Idle)
            } else if (!release.succeeded) {
                VpnController.setState(VpnState.Error("VPN resource cleanup failed: ${release.failures.joinToString()}"))
            }
        }
        super.onDestroy()
    }

    private fun handle(command: Command) {
        if (command !is Command.Stop && !VpnController.canStartConnection()) return
        Log.i(TAG, "[CMD] $command  desiredRunning=$desiredRunning state=${VpnController.state.value}")
        AppLog.write(this, "INFO", "[CMD] $command desiredRunning=$desiredRunning")
        when (command) {
            is Command.Stop -> stopVpn(command.startId)
            Command.Start -> {
                if (desiredRunning && VpnController.state.value is VpnState.Connected) return
                desiredRunning = true
                resetRetry()
                startVpn()
            }
            Command.Switch -> if (desiredRunning) {
                resetRetry()
                val container = (application as IceFlowApp).container
                val newProfile = container.servers.selected
                val sameNode = newProfile?.id == activeServerId
                // 同节点快速重连：保留 xray-core 进程，只重启 tun2proxy
                releaseResources(closeTun = false, stopXray = !sameNode)
                startVpn()
            }
            Command.RetryNow -> if (desiredRunning) {
                // 用户手动重试：立即尝试，不再等待退避计时器
                retryJob?.cancel()
                resetRetry()
                val container = (application as IceFlowApp).container
                val sameNode = container.servers.selected?.id == activeServerId
                releaseResources(closeTun = false, stopXray = !sameNode)
                startVpn()
            }
            is Command.Retry -> if (desiredRunning && command.generation == generation) {
                // 自动重试时尝试复用 xray-core（节点未变的情况）
                val container = (application as IceFlowApp).container
                val sameNode = container.servers.selected?.id == activeServerId
                releaseResources(closeTun = false, stopXray = !sameNode)
                startVpn()
            }
            Command.CoreFailed -> if (desiredRunning && VpnController.state.value is VpnState.Connected) {
                AppLog.write(this, "ERROR", "流量核心已退出，正在恢复连接")
                // 核心崩溃时必须完全清理，不能复用
                releaseResources(closeTun = false, stopXray = true)
                scheduleRetry()
            }
            is Command.Available -> if (networks.add(command.network) && desiredRunning) {
                resetRetry()
                // 网络恢复时快速重连，尽量复用 xray-core
                val container = (application as IceFlowApp).container
                val sameNode = container.servers.selected?.id == activeServerId
                releaseResources(closeTun = false, stopXray = !sameNode)
                // 网络恢复时立即重连，不再延迟
                scheduleRetry(100)
            }
            is Command.Lost -> if (networks.remove(command.network) && desiredRunning) {
                resetRetry()
                val container = (application as IceFlowApp).container
                val sameNode = container.servers.selected?.id == activeServerId
                releaseResources(closeTun = false, stopXray = !sameNode)
                // 有其他网络可用时快速切换，延迟从 500ms 降至 100ms
                if (networks.isNotEmpty()) scheduleRetry(100) else {
                    // 底层网络完全不可用：等待网络恢复，而不是盲目重试。
                    // 界面显示"等待网络恢复"，避免用户对着转圈干等几十秒。
                    VpnController.setState(VpnState.WaitingNetwork)
                    updateNotification("等待网络恢复")
                }
            }
        }
    }

    private fun resetRetry() {
        generation++
        retryJob?.cancel()
        retryJob = null
        retryAttempt = 0
    }

    private fun scheduleRetry(delayMs: Long? = null) {
        if (!desiredRunning || destroyed) return
        if (delayMs == null && retryAttempt >= 5) {
            VpnController.setState(VpnState.Error("连接恢复失败，请检查节点或网络后重试"))
            updateNotification("连接恢复失败，VPN 仍接管流量")
            return
        }
        // 优化重试策略：200ms / 500ms / 1s / 2s / 3s，总计不超过 7 秒
        // 早期失败往往是瞬时问题（网络切换、DNS 波动），快速重试可以大幅降低感知耗时
        val waitMs = delayMs ?: when (retryAttempt++) {
            0 -> 200L   // 第一次重试立即进行
            1 -> 500L   // 第二次稍作等待
            2 -> 1000L  // 第三次 1 秒
            3 -> 2000L  // 第四次 2 秒
            else -> 3000L // 最后一次 3 秒
        }
        val expectedGeneration = ++generation
        retryJob?.cancel()
        // 底层网络还没回来时，明确告诉界面"在等网络"，而不是显示"正在连接"。
        if (networks.isEmpty()) {
            VpnController.setState(VpnState.WaitingNetwork)
            updateNotification("等待网络恢复")
        } else {
            VpnController.setState(VpnState.Connecting)
            updateNotification("正在恢复连接")
        }
        retryJob = scope.launch {
            delay(waitMs)
            commands.send(Command.Retry(expectedGeneration))
        }
    }

    private fun startVpn() {
        if (!desiredRunning || destroyed) return
        val t0 = SystemClock.elapsedRealtime()
        fun mark(stage: String) {
            val dt = SystemClock.elapsedRealtime() - t0
            AppLog.write(this, "INFO", "[计时] $stage: ${dt}ms")
            Log.i(TAG, "[TIMING] $stage: ${dt}ms")
        }
        startForegroundCompat(buildNotification(getString(R.string.notification_connecting)))
        VpnController.setState(VpnState.Connecting)
        AppLog.write(this, "INFO", "开始连接 VPN")
        mark("foreground")

        val container = (application as IceFlowApp).container
        val profile = container.servers.selected ?: return fail("请先选择节点")
        val settings = container.settings.settings.value
        if (!XrayBridge.isAvailable) return fail("未集成 xray-core（缺少 app/libs/xray.aar）")
        if (!RustBridge.isAvailable) return fail("未集成 Rust 核心（缺少 libiceflow_core.so）")

        try {
            settings.validate()
            AppLog.write(this, "INFO", "准备连接节点：${profile.name} [${profile.protocol.label}] ${profile.address}:${profile.port}")
            container.routingResources.prepare()
            mark("routingResources.prepare")
            val appProxyMode = if (settings.appRoutingEnabled) settings.appProxyMode else settings.proxyAppsOnly
            AppLog.write(this, "INFO", "代理方案：Xray 核心 + TUN 转发，DNS=${settings.dnsServers.joinToString(",")}, IPv6=${settings.ipv6Enabled}, 分应用代理=$appProxyMode")
            if (settings.bypassApps.isNotEmpty()) AppLog.write(this, "INFO", "应用策略包数量：${settings.bypassApps.size}")
            XrayBridge.setAssetDir(filesDir.absolutePath)
            // 域匹配器缓存：避免每次连接节点时重新解析 geosite 数据
            XrayBridge.setMphCacheDir(filesDir.absolutePath)
            mark("validate+readSettings")

            // 快速重连优化：如果 xray 已在运行且节点未变，复用之前的配置
            val xrayAlreadyRunning = XrayBridge.isRunning() && profile.id == activeServerId
            val tunnelPort: Int
            val tunnelUser: String
            val tunnelPass: String

            if (xrayAlreadyRunning && activeTunnelPort > 0) {
                // 复用已有配置
                tunnelPort = activeTunnelPort
                tunnelUser = activeTunnelUser
                tunnelPass = activeTunnelPass
                mark("config.reuse")
            } else {
                // 生成新配置
                tunnelPort = generateSequence {
                    ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }
                }.first { !settings.socksEnabled || it != settings.socksPort }
                tunnelUser = java.util.UUID.randomUUID().toString().take(16)
                tunnelPass = java.util.UUID.randomUUID().toString()
                mark("config.generate")
            }

            val tunnelAuth = tunnelUser to tunnelPass

            val cfg = XrayConfigBuilder.build(
                profile, settings,
                store = container.servers.store.value,
                tunnelPort = tunnelPort,
                tunnelAuth = tunnelAuth
            )
            mark("buildConfig")

            if (!xrayAlreadyRunning) {
                XrayBridge.start(cfg)
                mark("xray.start")
                // 保存配置供后续复用
                activeTunnelPort = tunnelPort
                activeTunnelUser = tunnelUser
                activeTunnelPass = tunnelPass
            } else {
                mark("xray.reuse (already running)")
                AppLog.write(this, "INFO", "复用已运行的 xray-core 进程")
            }

            val builder = Builder()
                .setSession(profile.name)
                .setMtu(MTU)
                .addAddress(TUN_ADDRESS_V4, 30)
                .addRoute("0.0.0.0", 0)
                // 同时接管 IPv6，防止 IPv6 流量绕过隧道泄漏
            settings.dnsServers.forEach { builder.addDnsServer(it) }
            if (settings.ipv6Enabled) {
                builder.addAddress(TUN_ADDRESS_V6, 126).addRoute("::", 0)
            }
            if ((settings.appRoutingEnabled || settings.proxyAppsOnly) && appProxyMode) {
                val installed = packageManager.getInstalledApplications(0).map { it.packageName }.toSet()
                val allowed = settings.bypassApps.filter { it != packageName && it in installed }
                require(allowed.isNotEmpty()) { "分应用代理模式下请至少选择一个已安装的应用" }
                allowed.forEach { builder.addAllowedApplication(it) }
            } else {
                builder.addDisallowedApplication(packageName)
                if (settings.appRoutingEnabled) settings.bypassApps.filter { it != packageName }.forEach {
                    runCatching { builder.addDisallowedApplication(it) }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)

            mark("vpnBuilder.prepare")
            val pfd = builder.establish() ?: throw IllegalStateException("VPN 权限未授予")
            mark("vpn.establish")
            val previousTun = tun
            tun = pfd
            previousTun?.close()

            val dnsMode = when {
                !settings.dnsProxyEnabled -> RustBridge.DNS_DIRECT
                settings.fakeDns -> RustBridge.DNS_VIRTUAL
                else -> RustBridge.DNS_OVER_TCP
            }
            val code = RustBridge.startTun(
                fd = pfd.fd,
                mtu = MTU,
                proxyUrl = "socks5://$tunnelUser:$tunnelPass@127.0.0.1:$tunnelPort",
                dnsMode = dnsMode,
                dnsAddr = settings.remoteDns,
            )
            check(code == 0) { RustBridge.describe(code) }
            mark("rust.startTun")

            if (destroyed) { releaseResources(closeTun = true, stopXray = true); return }
            activeServerId = profile.id
            persistedUp = 0
            persistedDown = 0
            persistedAt = SystemClock.elapsedRealtime()
            VpnController.setState(VpnState.Connected(profile.name, System.currentTimeMillis(), tunnelPort, tunnelUser, tunnelPass))
            getSystemService(NotificationManager::class.java).notify(
                NOTIFICATION_ID,
                buildNotification(getString(R.string.notification_connected, profile.name)),
            )
            statsJob = scope.launch { statsLoop() }
            Log.i(TAG, "VPN started: ${profile.name}")
            AppLog.write(this, "INFO", "VPN 已连接: ${profile.name}")
            // 端到端感知耗时：从用户点击「连接」到此刻（含调度、xray 启动、TUN 建立）
            val clickedAt = VpnController.clickAt
            if (clickedAt > 0) {
                val total = SystemClock.elapsedRealtime() - clickedAt
                Log.i(TAG, "[TIMING] end-to-end from click: ${total}ms")
                AppLog.write(this, "INFO", "[计时] 点击→已连接：$total ms")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "start failed", t)
            AppLog.write(this, "ERROR", "VPN 连接失败：${t.message}")
            releaseResources(closeTun = false, stopXray = true)
            if (tun == null || t is IllegalArgumentException) fail(t.message ?: t.toString()) else scheduleRetry()
        }
    }

    private fun stopVpn(startId: Int? = null) {
        desiredRunning = false
        resetRetry()
        VpnController.setState(VpnState.Stopping)
        updateNotification("正在断开…")
        val release = releaseResources(closeTun = true, stopXray = true)
        if (release.succeeded) VpnController.setState(VpnState.Idle)
        else VpnController.setState(VpnState.Error("VPN resource cleanup failed: ${release.failures.joinToString()}"))
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        if (startId == null) stopSelf() else stopSelf(startId)
    }

    private fun fail(message: String) {
        desiredRunning = false
        resetRetry()
        val release = releaseResources(closeTun = true, stopXray = true)
        val detail = release.failures.takeIf { it.isNotEmpty() }?.joinToString(prefix = "; cleanup failed: ") ?: ""
        VpnController.setState(VpnState.Error(message + detail))
        AppLog.write(this, "ERROR", "VPN 失败: $message")
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** 释放顺序：统计 → tun2proxy（不持有 fd）→ xray-core → 关闭 TUN fd。
     *  @param closeTun 是否关闭 TUN，重连场景保持 TUN 可减少延迟
     *  @param stopXray 是否停止 xray-core，重连时如果节点未变可以复用
     */
    private fun releaseResources(closeTun: Boolean = true, stopXray: Boolean = true): ResourceReleaseResult {
        VpnController.setState(VpnState.Stopping)
        val requestId = VpnController.beginResourceRelease()
        val failures = mutableListOf<String>()
        statsJob?.cancel()
        statsJob = null
        runCatching { persistTraffic(force = true) }
            .onFailure { failures += "Persist traffic: ${it.message}"; Log.w(TAG, "persist traffic: $it") }
        if (!stopXray) {
            runCatching { RustBridge.stopTun() }.onFailure { failures += "Rust stopTun: ${it.message}"; Log.w(TAG, "stopTun: $it") }
        } else {
            activeServerId = null
            activeTunnelPort = 0
            activeTunnelUser = ""
            activeTunnelPass = ""
            runCatching { RustBridge.stopTun() }.onFailure { failures += "Rust stopTun: ${it.message}"; Log.w(TAG, "stopTun: $it") }
            runCatching { XrayBridge.stop() }.onFailure { failures += "Xray stop: ${it.message}"; Log.w(TAG, "xray stop: $it") }
        }
        if (closeTun) {
            runCatching { tun?.close() }.onFailure { failures += "TUN close: ${it.message}" }
            tun = null
        }
        VpnController.setTraffic(TrafficSnapshot())
        val result = ResourceReleaseResult(requestId, failures.toList())
        VpnController.completeResourceRelease(requestId, result.failures)
        return result
    }
    private suspend fun statsLoop() {
        val initial = RustBridge.stats()
        val sampler = TrafficSampler(initial[0], initial[1], SystemClock.elapsedRealtime())
        while (true) {
            delay(1000)
            synchronized(operationLock) {
                if (!RustBridge.isRunning() || !XrayBridge.isRunning()) {
                    commands.trySend(Command.CoreFailed)
                    return
                }
                val stats = RustBridge.stats()

                // 查询 xray 详细统计
                val xrayStats = runCatching {
                    com.iceflow.data.XrayStats.parse(XrayBridge.queryStats())
                }.getOrNull()

                val byOutbound = xrayStats?.let { xs ->
                    buildMap {
                        xs.outboundUplink.keys.union(xs.outboundDownlink.keys).forEach { tag ->
                            val up = xs.outboundUplink[tag] ?: 0
                            val down = xs.outboundDownlink[tag] ?: 0
                            put(tag, up to down)
                        }
                    }
                } ?: emptyMap()

                VpnController.setTraffic(
                    sampler.sample(stats[0], stats[1], SystemClock.elapsedRealtime())
                        .copy(byOutbound = byOutbound)
                )
                persistTraffic()
                val session = VpnController.state.value as? VpnState.Connected
                if (session != null && System.currentTimeMillis() - session.since >= 30_000) retryAttempt = 0
            }
        }
    }

    private fun persistTraffic(force: Boolean = false) {
        val id = activeServerId ?: return
        if (!force && SystemClock.elapsedRealtime() - persistedAt < 30_000) return
        runCatching {
            val stats = RustBridge.stats()
            val up = (stats[0] - persistedUp).coerceAtLeast(0)
            val down = (stats[1] - persistedDown).coerceAtLeast(0)
            if (up > 0 || down > 0) (application as IceFlowApp).container.servers.addTraffic(id, up, down)
            persistedUp = stats[0]
            persistedDown = stats[1]
            persistedAt = SystemClock.elapsedRealtime()
        }.onFailure { AppLog.write(this, "WARN", "流量统计保存失败：${it.message}") }
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val entryPoint = VpnEntryPoint.NOTIFICATION
        val state = VpnController.state.value
        val primary = VpnEntryPolicy.primary(state, entryPoint)
        val secondary = VpnEntryPolicy.secondary(state, entryPoint)
        fun pendingAction(operation: VpnUserOperation, requestCode: Int): PendingIntent = PendingIntent.getService(
            this,
            requestCode,
            Intent(this, IceFlowVpnService::class.java).setAction(
                when (operation) {
                    VpnUserOperation.CONNECT -> ACTION_START
                    VpnUserOperation.CANCEL, VpnUserOperation.STOP -> ACTION_STOP
                    VpnUserOperation.RETRY -> ACTION_RETRY
                    VpnUserOperation.IGNORE -> ACTION_STOP
                },
            ),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        if (primary != VpnUserOperation.IGNORE) {
            val label = when (primary) {
                VpnUserOperation.CONNECT -> getString(R.string.notification_connect)
                VpnUserOperation.CANCEL -> getString(R.string.notification_cancel)
                VpnUserOperation.RETRY -> getString(R.string.notification_retry)
                VpnUserOperation.STOP -> getString(R.string.notification_disconnect)
                VpnUserOperation.IGNORE -> ""
            }
            builder.addAction(0, label, pendingAction(primary, 1))
        }
        if (secondary != null) builder.addAction(0, getString(R.string.notification_disconnect), pendingAction(secondary, 2))
        return builder.build()
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
