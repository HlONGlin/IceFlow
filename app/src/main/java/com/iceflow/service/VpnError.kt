package com.iceflow.service

import com.iceflow.data.model.AppLanguage

enum class VpnErrorType {
    VPN_AUTHORIZATION_DENIED,
    CORE_START_FAILED,
    CONFIGURATION_OR_SUBSCRIPTION,
    NETWORK_UNAVAILABLE,
    RETRIES_EXHAUSTED,
    RESOURCE_CLEANUP_FAILED,
}

data class VpnErrorPresentation(
    val type: VpnErrorType,
    val message: String,
    val nextAction: String,
    val actionLabel: String,
    val action: VpnErrorAction,
)

enum class VpnErrorAction { REAUTHORIZE, CONNECT, CHECK_NODE, OPEN_LOGS }

/** One pure, localized mapping from VPN failure type to user-facing feedback. */
object VpnErrorCatalog {
    fun shortLabel(type: VpnErrorType, language: AppLanguage): String = when (language) {
        AppLanguage.SIMPLIFIED_CHINESE -> when (type) {
            VpnErrorType.VPN_AUTHORIZATION_DENIED -> "授权被拒"
            VpnErrorType.CORE_START_FAILED -> "核心启动失败"
            VpnErrorType.CONFIGURATION_OR_SUBSCRIPTION -> "配置错误"
            VpnErrorType.NETWORK_UNAVAILABLE -> "网络不可用"
            VpnErrorType.RETRIES_EXHAUSTED -> "重试耗尽"
            VpnErrorType.RESOURCE_CLEANUP_FAILED -> "清理失败"
        }
        AppLanguage.TRADITIONAL_CHINESE -> when (type) {
            VpnErrorType.VPN_AUTHORIZATION_DENIED -> "授權遭拒"
            VpnErrorType.CORE_START_FAILED -> "核心啟動失敗"
            VpnErrorType.CONFIGURATION_OR_SUBSCRIPTION -> "設定錯誤"
            VpnErrorType.NETWORK_UNAVAILABLE -> "網路無法使用"
            VpnErrorType.RETRIES_EXHAUSTED -> "重試耗盡"
            VpnErrorType.RESOURCE_CLEANUP_FAILED -> "清理失敗"
        }
        AppLanguage.ENGLISH -> when (type) {
            VpnErrorType.VPN_AUTHORIZATION_DENIED -> "Permission denied"
            VpnErrorType.CORE_START_FAILED -> "Core start failed"
            VpnErrorType.CONFIGURATION_OR_SUBSCRIPTION -> "Invalid config"
            VpnErrorType.NETWORK_UNAVAILABLE -> "Network unavailable"
            VpnErrorType.RETRIES_EXHAUSTED -> "Retries exhausted"
            VpnErrorType.RESOURCE_CLEANUP_FAILED -> "Cleanup failed"
        }
    }

    fun presentation(type: VpnErrorType, language: AppLanguage): VpnErrorPresentation {
        val (message, nextAction, actionLabel, action) = when (language) {
            AppLanguage.SIMPLIFIED_CHINESE -> simplifiedChinese(type)
            AppLanguage.TRADITIONAL_CHINESE -> traditionalChinese(type)
            AppLanguage.ENGLISH -> english(type)
        }
        return VpnErrorPresentation(type, message, nextAction, actionLabel, action)
    }

    private fun simplifiedChinese(type: VpnErrorType): ErrorCopy = when (type) {
        VpnErrorType.VPN_AUTHORIZATION_DENIED -> ErrorCopy("VPN 授权未完成。", "点击连接并在系统提示中允许 VPN 授权。", "重新授权", VpnErrorAction.REAUTHORIZE)
        VpnErrorType.CORE_START_FAILED -> ErrorCopy("VPN 核心启动失败。", "检查所选节点和核心组件后，点击连接重试。", "重新连接", VpnErrorAction.CONNECT)
        VpnErrorType.CONFIGURATION_OR_SUBSCRIPTION -> ErrorCopy("节点或订阅配置无效。", "检查节点参数或刷新订阅，修正后再点击连接。", "查看节点", VpnErrorAction.CHECK_NODE)
        VpnErrorType.NETWORK_UNAVAILABLE -> ErrorCopy("当前网络不可用，暂时无法建立连接。", "恢复 Wi-Fi 或移动网络后，点击连接重试。", "重试连接", VpnErrorAction.CONNECT)
        VpnErrorType.RETRIES_EXHAUSTED -> ErrorCopy("多次尝试后仍未连接。", "检查网络和所选节点，然后点击连接重试。", "重试连接", VpnErrorAction.CONNECT)
        VpnErrorType.RESOURCE_CLEANUP_FAILED -> ErrorCopy("VPN 资源未能正常清理。", "打开应用日志查看清理错误，然后重新连接。", "查看日志", VpnErrorAction.OPEN_LOGS)
    }

    private fun traditionalChinese(type: VpnErrorType): ErrorCopy = when (type) {
        VpnErrorType.VPN_AUTHORIZATION_DENIED -> ErrorCopy("VPN 授權尚未完成。", "點擊連線，並在系統提示中允許 VPN 授權。", "重新授權", VpnErrorAction.REAUTHORIZE)
        VpnErrorType.CORE_START_FAILED -> ErrorCopy("VPN 核心啟動失敗。", "檢查所選節點和核心元件後，點擊連線重試。", "重新連線", VpnErrorAction.CONNECT)
        VpnErrorType.CONFIGURATION_OR_SUBSCRIPTION -> ErrorCopy("節點或訂閱設定無效。", "檢查節點參數或重新整理訂閱，修正後再點擊連線。", "查看節點", VpnErrorAction.CHECK_NODE)
        VpnErrorType.NETWORK_UNAVAILABLE -> ErrorCopy("目前網路無法使用，暫時無法建立連線。", "恢復 Wi-Fi 或行動網路後，點擊連線重試。", "重試連線", VpnErrorAction.CONNECT)
        VpnErrorType.RETRIES_EXHAUSTED -> ErrorCopy("多次嘗試後仍未連線。", "檢查網路和所選節點，然後點擊連線重試。", "重試連線", VpnErrorAction.CONNECT)
        VpnErrorType.RESOURCE_CLEANUP_FAILED -> ErrorCopy("VPN 資源未能正常清理。", "開啟應用程式記錄查看清理錯誤，然後重新連線。", "查看記錄", VpnErrorAction.OPEN_LOGS)
    }

    private fun english(type: VpnErrorType): ErrorCopy = when (type) {
        VpnErrorType.VPN_AUTHORIZATION_DENIED -> ErrorCopy("VPN permission was not granted.", "Tap Connect and allow the VPN request in the system prompt.", "Authorize VPN", VpnErrorAction.REAUTHORIZE)
        VpnErrorType.CORE_START_FAILED -> ErrorCopy("The VPN core failed to start.", "Check the selected node and core components, then tap Connect to retry.", "Reconnect", VpnErrorAction.CONNECT)
        VpnErrorType.CONFIGURATION_OR_SUBSCRIPTION -> ErrorCopy("The node or subscription configuration is invalid.", "Check the node settings or refresh the subscription, then tap Connect.", "View nodes", VpnErrorAction.CHECK_NODE)
        VpnErrorType.NETWORK_UNAVAILABLE -> ErrorCopy("The network is unavailable, so a connection cannot be established.", "Restore Wi-Fi or mobile data, then tap Connect to retry.", "Retry connection", VpnErrorAction.CONNECT)
        VpnErrorType.RETRIES_EXHAUSTED -> ErrorCopy("The connection still failed after several attempts.", "Check the network and selected node, then tap Connect to retry.", "Retry connection", VpnErrorAction.CONNECT)
        VpnErrorType.RESOURCE_CLEANUP_FAILED -> ErrorCopy("VPN resources could not be cleaned up normally.", "Open the app log to review the cleanup error, then reconnect.", "View log", VpnErrorAction.OPEN_LOGS)
    }

    private data class ErrorCopy(
        val message: String,
        val nextAction: String,
        val actionLabel: String,
        val action: VpnErrorAction,
    )
}
