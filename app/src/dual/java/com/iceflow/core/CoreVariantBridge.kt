package com.iceflow.core

import com.iceflow.BuildConfig
import com.iceflow.data.model.AppLanguage
import com.iceflow.service.VpnErrorType

internal object CoreVariantBridge {
    val variantName: String get() = BuildConfig.CORE_VARIANT
    val isReady: Boolean get() = XrayBridge.isAvailable && RustBridge.isAvailable

    fun stop() = XrayBridge.stop()

    fun failureMessage(type: VpnErrorType, language: AppLanguage): String? = null

    fun shortLabel(type: VpnErrorType, language: AppLanguage): String? = null
}
