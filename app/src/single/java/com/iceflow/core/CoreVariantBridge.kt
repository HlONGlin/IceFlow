package com.iceflow.core

import com.iceflow.BuildConfig
import com.iceflow.data.XrayStats
import com.iceflow.data.model.AppSettings
import com.iceflow.data.model.ServerProfile
import com.iceflow.data.model.ServerStore
import com.iceflow.data.model.AppLanguage
import com.iceflow.service.VpnErrorType

internal object CoreVariantBridge {
    val variantName: String get() = BuildConfig.CORE_VARIANT
    val isReady: Boolean get() = false
    val stopFailureName: String get() = "Core"

    fun stop() = Unit

    fun prepareAssets(dir: String): Unit = unavailable()

    fun buildConfig(
        profile: ServerProfile,
        settings: AppSettings,
        includeInbound: Boolean = true,
        store: ServerStore? = null,
        tunnelPort: Int? = null,
        tunnelAuth: Pair<String, String>? = null,
    ): String = unavailable()

    fun isCoreRunning(): Boolean = false

    fun start(config: String): Unit = unavailable()

    /**
     * The service owns/establishes/closes the original TUN ParcelFileDescriptor.
     * The placeholder never adopts or closes [fd], including failure paths.
     * A future mihomo implementation must duplicate any fd it adopts and close its own duplicate
     * on failed start and stop; terminal service cleanup closes the original, as in dual.
     */
    fun attachTun(fd: Int, mtu: Int, proxyUrl: String, settings: AppSettings): Unit = unavailable()

    fun stopTun() = Unit

    fun isHealthy(): Boolean = false

    /** Future core must normalize to cumulative session [uplink bytes, downlink bytes]. */
    fun stats(): LongArray = unavailable()

    /** Future core must normalize tag counters to this shape; no fabricated zero counters. */
    fun queryStats(): XrayStats = unavailable()

    private fun unavailable(): Nothing = throw IllegalStateException("Single-core build is not ready.")

    fun failureMessage(type: VpnErrorType, language: AppLanguage): String? =
        if (type != VpnErrorType.CORE_START_FAILED) null else when (language) {
            AppLanguage.SIMPLIFIED_CHINESE -> "单核未就绪"
            AppLanguage.TRADITIONAL_CHINESE -> "單核尚未就緒"
            AppLanguage.ENGLISH -> "Single-core build is not ready."
        }

    fun shortLabel(type: VpnErrorType, language: AppLanguage): String? =
        if (type != VpnErrorType.CORE_START_FAILED) null else when (language) {
            AppLanguage.SIMPLIFIED_CHINESE -> "单核未就绪"
            AppLanguage.TRADITIONAL_CHINESE -> "單核未就緒"
            AppLanguage.ENGLISH -> "Single core not ready"
        }
}
