package com.iceflow.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iceflow.service.VpnState
import com.iceflow.ui.component.ConnectButton
import com.iceflow.ui.theme.IceFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConnectButtonTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun labelsFollowState_andClickInvokesCallback() {
        var clicks = 0
        rule.setContent {
            IceFlowTheme { ConnectButton(state = VpnState.Idle, onClick = { clicks++ }) }
        }
        rule.onNodeWithText("连接").assertIsDisplayed()
        rule.onNodeWithTag("ConnectButton").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun connectingStateShowsProgressLabel() {
        rule.setContent { IceFlowTheme { ConnectButton(state = VpnState.Connecting, onClick = {}) } }
        rule.onNodeWithText("连接中").assertIsDisplayed()
    }
}
