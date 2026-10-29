package com.iceflow.core

import com.iceflow.BuildConfig
import com.iceflow.data.XrayStats
import com.iceflow.data.MihomoConfigBuilder
import com.iceflow.data.model.AppSettings
import com.iceflow.data.model.ServerProfile
import com.iceflow.data.model.ServerStore
import com.iceflow.data.model.AppLanguage
import com.iceflow.service.VpnErrorType
import java.net.ServerSocket
import java.net.Socket

internal object CoreVariantBridge {
    val variantName: String get() = BuildConfig.CORE_VARIANT
    /** 用户可见的核心名称：single 仅使用 mihomo，无 Xray/Rust 双核。 */
    val coreDisplayName: String get() = "mihomo"
    val coreVersionLabel: String? get() = runtime.version()
    /** TUN 转发层标签，用于诊断日志。 */
    val tunLabel: String get() = "mihomo"
    private val runtime = MihomoRuntime()
    val isReady: Boolean get() = runtime.isReady
    val stopFailureName: String get() = "Core"

    fun stop() = runtime.stop()

    fun prepareAssets(dir: String) = runtime.prepareAssets(dir)

    fun buildConfig(
        profile: ServerProfile,
        settings: AppSettings,
        includeInbound: Boolean = true,
        store: ServerStore? = null,
        tunnelPort: Int? = null,
        tunnelAuth: Pair<String, String>? = null,
    ): String = MihomoConfigBuilder.build(profile, settings, includeInbound, store, tunnelPort, tunnelAuth)
        .also { if (includeInbound) runtime.validateConfig(it) } // service still classifies CONFIGURATION here

    fun isCoreRunning(): Boolean = runtime.isCoreRunning()

    fun start(config: String) = runtime.start(config)

    /**
     * The service owns/establishes/closes the original TUN ParcelFileDescriptor.
     * Mihomo duplicates [fd] and closes only its duplicate on failed attach, stopTun and stop.
     * The service alone closes the original, including terminal failure/onDestroy fallback.
     */
    fun attachTun(fd: Int, mtu: Int, proxyUrl: String, settings: AppSettings) = runtime.attachTun(fd, mtu)

    fun stopTun() = runtime.stopTun()

    fun isHealthy(): Boolean = runtime.isHealthy()

    /** Selected node cumulative session [uplink bytes, downlink bytes], not chain sum/rates. */
    fun stats(): LongArray = runtime.stats()

    /** Per-node cumulative counters, excluding direct/block; reads never accumulate again. */
    fun queryStats(): XrayStats = runtime.queryStats()

    /** Starts the real single-core outbound with a temporary SOCKS listener, then probes it. */
    fun measureDelay(profile: ServerProfile, settings: AppSettings, store: ServerStore?, url: String, timeoutMs: Long): Int {
        check(!runtime.isCoreRunning()) { "Single-core is already running; use its connected-session probe" }
        val port = ServerSocket(0).use { it.localPort }
        val probeSettings = settings.copy(socksEnabled = false)
        val config = buildConfig(profile, probeSettings, includeInbound = true, store = store, tunnelPort = port, tunnelAuth = null)
        runtime.start(config)
        try {
            val deadline = System.nanoTime() + timeoutMs * 1_000_000
            while (System.nanoTime() < deadline && !canConnect(port)) Thread.sleep(25)
            check(canConnect(port)) { "Single-core SOCKS listener did not start" }
            return ConnectionProbe.measureSocks5(port, url = url, timeoutMs = timeoutMs.toInt())
        } finally {
            runtime.stop()
        }
    }

    private fun canConnect(port: Int): Boolean = runCatching {
        Socket().use { it.connect(java.net.InetSocketAddress("127.0.0.1", port), 100) }
        true
    }.getOrDefault(false)

    fun failureMessage(type: VpnErrorType, language: AppLanguage): String? =
        if (type != VpnErrorType.CORE_START_FAILED || isReady) null else when (language) {
            AppLanguage.SIMPLIFIED_CHINESE -> "单核未就绪"
            AppLanguage.TRADITIONAL_CHINESE -> "單核尚未就緒"
            AppLanguage.ENGLISH -> "Single-core build is not ready."
        }

    fun shortLabel(type: VpnErrorType, language: AppLanguage): String? =
        if (type != VpnErrorType.CORE_START_FAILED || isReady) null else when (language) {
            AppLanguage.SIMPLIFIED_CHINESE -> "单核未就绪"
            AppLanguage.TRADITIONAL_CHINESE -> "單核未就緒"
            AppLanguage.ENGLISH -> "Single core not ready"
        }
}
