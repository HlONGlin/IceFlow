package com.iceflow.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iceflow.data.NodeEditing
import com.iceflow.data.model.*
import com.iceflow.data.repository.RoutingResources
import com.iceflow.data.repository.SettingsRepository
import com.iceflow.ui.theme.IceFlowTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoutingEditorTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun field(label: String) = rule.onNode(hasText(label) and hasSetTextAction())

    @Test fun routingBulkClipboardAndResourcesWork() {
        val state = mutableStateOf(AppSettings())
        val resources = RoutingResources(context).also { it.prepare() }
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        rule.setContent { IceFlowTheme { RoutingPage(state.value, { change -> state.value = change(state.value) }, resources, {}) } }
        rule.onNodeWithContentDescription("路由操作").performClick()
        rule.onNodeWithText("禁用所有规则").performClick()
        rule.runOnIdle { assertTrue(state.value.routingRules.none { it.enabled }); assertFalse(state.value.bypassLan) }
        rule.onNodeWithContentDescription("路由操作").performClick()
        rule.onNodeWithText("启用所有规则").performClick()
        rule.runOnIdle { assertTrue(state.value.routingRules.all { it.enabled }); assertTrue(state.value.bypassLan) }
        rule.onNodeWithContentDescription("路由操作").performClick()
        rule.onNodeWithText("导出到剪贴板").performClick()
        rule.runOnIdle { assertEquals(4, RoutingRules.decode(clipboard.primaryClip!!.getItemAt(0).text.toString()).size) }
        rule.onNodeWithText("确定").performClick()
        rule.runOnIdle { clipboard.setPrimaryClip(ClipData.newPlainText("test", """[{"name":"Imported block","outboundTag":"block","domain":["full:blocked.example"]}]""")) }
        rule.onNodeWithContentDescription("路由操作").performClick()
        rule.onNodeWithText("从剪贴板导入 JSON 规则").performClick()
        rule.onNodeWithText("追加").performClick()
        rule.runOnIdle { assertEquals("Imported block", state.value.routingRules.first().name); assertEquals(RouteAction.BLOCK, state.value.routingRules.first().action) }
        rule.onNodeWithContentDescription("编辑Imported block").performClick()
        field("规则名称").performTextReplacement("Edited block")
        rule.onNodeWithContentDescription("保存").performClick()
        rule.onNodeWithText("Edited block").assertIsDisplayed()
        rule.onNodeWithContentDescription("路由操作").performClick()
        rule.onNodeWithText("管理路由资源").performClick()
        rule.onNodeWithText("路由资源").assertIsDisplayed()
        rule.onNodeWithText("域名数据库 · geosite.dat").assertIsDisplayed()
        rule.onNodeWithText("IP 数据库 · geoip.dat").performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("关闭").performClick()
        rule.onNodeWithContentDescription("添加路由规则").performClick()
        field("规则名称").performTextReplacement("New direct")
        field("域名").performTextReplacement("domain:example.org")
        rule.onNodeWithText("路由目标").performClick()
        rule.onNodeWithText("绕过").performClick()
        rule.onNodeWithContentDescription("保存").performClick()
        rule.runOnIdle { assertEquals("New direct", state.value.routingRules.first().name); assertEquals(RouteAction.DIRECT, state.value.routingRules.first().action) }
    }

    @Test fun manualNodeAndEditUseTheSameCompleteForm() {
        val initial = NodeEditing.blank()
        var saved: ServerProfile? = null
        var closed = 0
        rule.setContent { IceFlowTheme { NodeEditorPage(initial, true, { closed++ }, { saved = it; null }) } }
        field("节点名称").performTextReplacement("Manual VLESS")
        field("服务器地址").performTextReplacement("proxy.example.org")
        field("服务器端口").performTextReplacement("8443")
        field("UUID").performTextReplacement("b831381d-6324-4d53-ad4f-8cda48b30811")
        rule.onNodeWithContentDescription("保存").performClick()
        assertEquals(1, closed); assertEquals(8443, saved!!.port); assertEquals(Protocol.VLESS, saved!!.protocol)
        field("服务器地址").performTextReplacement("edited.example.org")
        rule.onNodeWithContentDescription("保存").performClick()
        assertEquals(initial.id, saved!!.id); assertEquals("edited.example.org", saved!!.address)
    }

    @Test fun measurementUrlAndRoutingOrderPersistWithoutChangingUserSettings() {
        val isolated = object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("routing-qa-$name", mode)
        }
        isolated.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
        try {
            val repository = SettingsRepository(isolated)
            val state = mutableStateOf(repository.settings.value)
            rule.setContent { IceFlowTheme { SettingsPage(state.value, { change -> repository.update(change); state.value = repository.settings.value }, "test", {}) } }
            rule.onNodeWithText("测试 URL").performScrollTo().performClick()
            rule.onAllNodes(hasSetTextAction()).onFirst().performTextReplacement("http://127.0.0.1:8080/check")
            rule.onNodeWithText("保存").performClick()
            rule.runOnIdle {
                repository.update { it.copy(routingRules = it.routingRules.reversed()) }
                val reloaded = SettingsRepository(isolated).settings.value
                assertEquals("http://127.0.0.1:8080/check", reloaded.testUrl)
                assertEquals(repository.settings.value.routingRules, reloaded.routingRules)
                assertTrue(runCatching { repository.update { it.copy(testUrl = "bad URL") } }.isFailure)
                assertEquals(reloaded.testUrl, repository.settings.value.testUrl)
            }
        } finally { isolated.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit() }
    }
}
