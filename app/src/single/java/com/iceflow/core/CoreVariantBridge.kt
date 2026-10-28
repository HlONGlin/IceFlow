package com.iceflow.core

import com.iceflow.BuildConfig
import com.iceflow.data.XrayStats
import com.iceflow.data.MihomoConfigBuilder
import com.iceflow.data.model.AppSettings
import com.iceflow.data.model.ServerProfile
import com.iceflow.data.model.ServerStore
import com.iceflow.data.model.AppLanguage
import com.iceflow.service.VpnErrorType

internal object CoreVariantBridge {
    val variantName: String get() = BuildConfig.CORE_VARIANT
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
