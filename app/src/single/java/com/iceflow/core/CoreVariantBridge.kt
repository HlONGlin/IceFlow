package com.iceflow.core

import com.iceflow.BuildConfig
import com.iceflow.data.model.AppLanguage
import com.iceflow.service.VpnErrorType

internal object CoreVariantBridge {
    val variantName: String get() = BuildConfig.CORE_VARIANT
    val isReady: Boolean get() = false

    fun stop() = Unit

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
