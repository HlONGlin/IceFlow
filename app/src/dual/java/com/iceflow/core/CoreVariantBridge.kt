package com.iceflow.core

import com.iceflow.BuildConfig
import com.iceflow.data.XrayConfigBuilder
import com.iceflow.data.XrayStats
import com.iceflow.data.model.AppSettings
import com.iceflow.data.model.ServerProfile
import com.iceflow.data.model.ServerStore
import com.iceflow.data.model.AppLanguage
import com.iceflow.service.VpnErrorType

internal object CoreVariantBridge {
    val variantName: String get() = BuildConfig.CORE_VARIANT
    /** 用户可见的核心名称，与实际运行的核心保持一致。 */
    val coreDisplayName: String get() = "xray-core"
    val coreVersionLabel: String? get() = XrayBridge.version()
    /** TUN 转发层标签，用于诊断日志。 */
    val tunLabel: String get() = "Rust"
    val isReady: Boolean get() = XrayBridge.isAvailable && RustBridge.isAvailable
    val stopFailureName: String get() = "Xray"

    fun stop() = XrayBridge.stop()

    fun prepareAssets(dir: String) {
        XrayBridge.setAssetDir(dir)
        XrayBridge.setMphCacheDir(dir)
    }

    fun buildConfig(
        profile: ServerProfile,
        settings: AppSettings,
        includeInbound: Boolean = true,
        store: ServerStore? = null,
        tunnelPort: Int? = null,
        tunnelAuth: Pair<String, String>? = null,
    ): String = XrayConfigBuilder.build(profile, settings, includeInbound, store, tunnelPort, tunnelAuth)

    /** Reuse checks only Xray, just as the service did before delegation. */
    fun isCoreRunning(): Boolean = XrayBridge.isRunning()

    /** First startup phase, before the service establishes TUN. Exceptions propagate unchanged. */
    fun start(config: String) = XrayBridge.start(config)

    /**
     * Second phase, after the service establishes TUN. Borrow [fd]; never adopt/detach/close it.
     * Rust uses close_fd_on_drop(false). The service owns the original ParcelFileDescriptor,
     * closes its predecessor after replacement, and closes the current fd on terminal cleanup.
     * A failed start may retain that original for retry, as before; fail/onDestroy is the fallback.
     * No local cleanup here: the service's existing failure path stops Rust then Xray, without
     * replacing the primary start failure with a cleanup exception.
     */
    fun attachTun(fd: Int, mtu: Int, proxyUrl: String, settings: AppSettings) {
        val dnsMode = when {
            !settings.dnsProxyEnabled -> RustBridge.DNS_DIRECT
            settings.fakeDns -> RustBridge.DNS_VIRTUAL
            else -> RustBridge.DNS_OVER_TCP
        }
        val code = RustBridge.startTun(
            fd = fd,
            mtu = mtu,
            proxyUrl = proxyUrl,
            dnsMode = dnsMode,
            dnsAddr = settings.remoteDns,
        )
        check(code == 0) { RustBridge.describe(code) }
    }

    /** Stops forwarding only. Xray stop and fd close remain separately guarded by the service. */
    fun stopTun() = RustBridge.stopTun()

    /** Preserve Rust-first short circuit and propagation of native/reflection failures. */
    fun isHealthy(): Boolean = RustBridge.isRunning() && XrayBridge.isRunning()

    /** Session cumulative [uplink bytes, downlink bytes], never rates/deltas or reset-on-read. */
    fun stats(): LongArray = RustBridge.stats()

    /** Cumulative counters by inbound/outbound tag; caller retains its existing runCatching. */
    fun queryStats(): XrayStats = XrayStats.parse(XrayBridge.queryStats())

    fun measureDelay(profile: ServerProfile, settings: AppSettings, store: ServerStore?, url: String, timeoutMs: Long): Int =
        XrayBridge.measureDelay(buildConfig(profile, settings, includeInbound = false, store = store), url, timeoutMs).coerceAtLeast(1).toInt()

    fun failureMessage(type: VpnErrorType, language: AppLanguage): String? = null

    fun shortLabel(type: VpnErrorType, language: AppLanguage): String? = null
}
