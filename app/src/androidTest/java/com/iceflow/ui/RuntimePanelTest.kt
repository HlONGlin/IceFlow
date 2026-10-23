package com.iceflow.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.GeneralClickAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Tap
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import android.content.ClipboardManager
import android.content.Context
import android.widget.TextView
import com.iceflow.service.TrafficSnapshot
import com.iceflow.service.VpnState
import com.iceflow.ui.theme.IceFlowTheme
import com.iceflow.data.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RuntimePanelTest {
    @get:Rule val rule = createComposeRule()

    @Test fun connectButtonBlocksRepeatActionsWhileConnecting() {
        val state = mutableStateOf<VpnState>(VpnState.Idle)
        var clicks = 0
        rule.setContent { IceFlowTheme { ConnectOrb(state.value, { clicks++ }) } }
        rule.onNodeWithContentDescription("连接").performClick()
        assertEquals(1, clicks)
        rule.runOnIdle { state.value = VpnState.Connecting }
        rule.onAllNodes(hasClickAction()).fetchSemanticsNodes().forEach { node ->
            assertTrue(node.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled))
        }
        rule.runOnIdle { state.value = VpnState.Connected("Node", 1000) }
        rule.onNodeWithContentDescription("断开连接").performClick()
        assertEquals(2, clicks)
    }

    @Test fun groupNodePickersSaveAndDisableFrontAndLandingNodes() {
        val front = ServerProfile(id = "front", name = "Front test node", protocol = Protocol.SOCKS, address = "example.com", port = 1080)
        val landing = front.copy(id = "landing", name = "Landing test node")
        val config = GroupConfiguration(name = "QA group")
        var saved: GroupConfiguration? = null
        rule.setContent { IceFlowTheme { GroupSettingsPage(config, true, listOf(front, landing), {}, { saved = it; null }, {}) } }
        rule.onNodeWithText("前置节点").performClick()
        rule.onNodeWithText(front.name).performClick()
        rule.onNodeWithText("落地节点").performClick()
        rule.onNodeWithText(landing.name).performClick()
        rule.onNodeWithText("保存").performClick()
        assertEquals(front.id, saved?.frontNodeId)
        assertEquals(landing.id, saved?.landingNodeId)
        rule.onNodeWithText("前置节点").performClick()
        rule.onNodeWithText("禁用").performClick()
        rule.onNodeWithText("保存").performClick()
        assertEquals(null, saved?.frontNodeId)
        assertEquals(landing.id, saved?.landingNodeId)
    }

    @Test fun connectionPanelTestsOnTapAndShowsResultWithoutNodeName() {
        val test = mutableStateOf(ConnectionTestState())
        var clicks = 0
        rule.setContent {
            IceFlowTheme {
                ConnectionPanel(VpnState.Connected("Hidden node name", 1000), TrafficSnapshot(upSpeed = 2048, downSpeed = 4096),
                    6000, test.value, { clicks++; test.value = ConnectionTestState(testing = true) })
            }
        }
        rule.onNodeWithText("Hidden node name", substring = true).assertDoesNotExist()
        rule.onNodeWithText("已连接", substring = true).assertDoesNotExist()
        rule.onNodeWithText("点击测速").performClick()
        rule.onNodeWithText("测速中…").assertIsDisplayed()
        assertEquals(1, clicks)
        rule.runOnIdle { test.value = ConnectionTestState(latencyMs = 123) }
        rule.onNodeWithText("123 ms").assertIsDisplayed()
        rule.runOnIdle { test.value = ConnectionTestState(latencyMs = -1) }
        rule.onNodeWithText("超时 · 重试").assertIsDisplayed()
    }

    @Test fun logPageRefreshesAndCloses() {
        var source = "2026-10-22 [INFO] first log line\n2026-10-22 [ERROR] second log line"
        var back = 0
        rule.setContent { IceFlowTheme { RuntimeLogPage({ source }, { back++ }) } }
        rule.onNodeWithText("运行日志").assertIsDisplayed()
        rule.waitForIdle()
        onView(withText(source)).check(matches(isDisplayed()))
        rule.onNodeWithText("长按文字，拖动选区后复制").assertIsDisplayed()
        rule.onNodeWithText("暂停刷新").performClick()
        rule.runOnIdle { source = "2026-10-22 [INFO] refreshed log" }
        rule.onNodeWithContentDescription("刷新日志").performClick()
        rule.waitForIdle()
        onView(withText(source)).check(matches(isDisplayed()))
        rule.onNodeWithContentDescription("返回").performClick()
        assertEquals(1, back)
    }

    @Test fun logTextSupportsLongPressPartialCopy() {
        val log = "INFO running log selection sample\nERROR another line"
        rule.setContent { IceFlowTheme { RuntimeLogPage({ log }, {}) } }
        rule.waitForIdle()
        onView(withText(log)).perform(GeneralClickAction(Tap.LONG, { view ->
            val text = view as TextView
            val screen = IntArray(2)
            text.getLocationOnScreen(screen)
            floatArrayOf(screen[0] + text.totalPaddingLeft + text.layout.getPrimaryHorizontal(2),
                screen[1] + text.totalPaddingTop + text.layout.getLineBottom(0) / 2f)
        }, Press.FINGER))
        rule.onNodeWithText("实时刷新").assertIsDisplayed()
        rule.onNodeWithText("复制选中").assertIsDisplayed().performClick()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var copied = ""
        rule.runOnIdle {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            copied = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
        }
        assertTrue(copied.isNotEmpty())
        assertTrue(log.contains(copied))
        assertTrue("Long press should select part of the log", copied.length < log.length)
    }
}
