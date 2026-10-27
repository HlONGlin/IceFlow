package com.iceflow.service

import com.iceflow.data.model.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnErrorCatalogTest {
    @Test
    fun everyFailureTypeHasClearSimplifiedChineseMessageAndNextAction() {
        val expected = mapOf(
            VpnErrorType.VPN_AUTHORIZATION_DENIED to ("VPN 授权未完成。" to ("点击连接并在系统提示中允许 VPN 授权。" to VpnErrorAction.REAUTHORIZE)),
            VpnErrorType.CORE_START_FAILED to ("VPN 核心启动失败。" to ("检查所选节点和核心组件后，点击连接重试。" to VpnErrorAction.CONNECT)),
            VpnErrorType.CONFIGURATION_OR_SUBSCRIPTION to ("节点或订阅配置无效。" to ("检查节点参数或刷新订阅，修正后再点击连接。" to VpnErrorAction.CHECK_NODE)),
            VpnErrorType.NETWORK_UNAVAILABLE to ("当前网络不可用，暂时无法建立连接。" to ("恢复 Wi-Fi 或移动网络后，点击连接重试。" to VpnErrorAction.CONNECT)),
            VpnErrorType.RETRIES_EXHAUSTED to ("多次尝试后仍未连接。" to ("检查网络和所选节点，然后点击连接重试。" to VpnErrorAction.CONNECT)),
            VpnErrorType.RESOURCE_CLEANUP_FAILED to ("VPN 资源未能正常清理。" to ("打开应用日志查看清理错误，然后重新连接。" to VpnErrorAction.OPEN_LOGS)),
        )

        expected.forEach { (type, copy) ->
            val result = VpnErrorCatalog.presentation(type, AppLanguage.SIMPLIFIED_CHINESE)
            assertEquals(type, result.type)
            assertEquals(copy.first, result.message)
            assertEquals(copy.second.first, result.nextAction)
            assertEquals(copy.second.second, result.action)
            assertFalse(result.actionLabel.isBlank())
        }
    }

    @Test
    fun everyFailureTypeHasLocalizedFeedbackForAllSupportedLanguages() {
        VpnErrorType.entries.forEach { type ->
            val translations = AppLanguage.entries.map { VpnErrorCatalog.presentation(type, it) }
            translations.forEach { result ->
                assertEquals(type, result.type)
                assertTrue(result.message.isNotBlank())
                assertTrue(result.nextAction.isNotBlank())
                assertTrue(result.actionLabel.isNotBlank())
            }
            assertEquals(3, translations.map { it.message }.distinct().size)
        }
    }

    @Test
    fun eachFailureTypeHasOnlyItsConfiguredAction() {
        val actions = VpnErrorType.entries.map { type ->
            type to VpnErrorCatalog.presentation(type, AppLanguage.ENGLISH).action
        }.toMap()

        assertEquals(VpnErrorAction.REAUTHORIZE, actions[VpnErrorType.VPN_AUTHORIZATION_DENIED])
        assertEquals(VpnErrorAction.CONNECT, actions[VpnErrorType.CORE_START_FAILED])
        assertEquals(VpnErrorAction.CHECK_NODE, actions[VpnErrorType.CONFIGURATION_OR_SUBSCRIPTION])
        assertEquals(VpnErrorAction.CONNECT, actions[VpnErrorType.NETWORK_UNAVAILABLE])
        assertEquals(VpnErrorAction.CONNECT, actions[VpnErrorType.RETRIES_EXHAUSTED])
        assertEquals(VpnErrorAction.OPEN_LOGS, actions[VpnErrorType.RESOURCE_CLEANUP_FAILED])
        assertNotEquals(actions[VpnErrorType.VPN_AUTHORIZATION_DENIED], actions[VpnErrorType.CONFIGURATION_OR_SUBSCRIPTION])
    }
}
