package com.iceflow.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iceflow.service.VpnState
import com.iceflow.data.model.AppLanguage
import com.iceflow.i18n.stringsOf
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
        rule.onNodeWithText(stringsOf(AppLanguage.SIMPLIFIED_CHINESE).get("state.connecting")).assertIsDisplayed()
    }

    @Test fun accessibilityAnnouncesConnectingWaitingAndStoppingInsteadOfDisconnected() {
        val state = mutableStateOf<VpnState>(VpnState.Connecting)
        rule.setContent { IceFlowTheme(language = AppLanguage.ENGLISH) { ConnectButton(state.value, {}) } }
        rule.onNodeWithTag("ConnectButton").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, stringsOf(AppLanguage.ENGLISH).get("state.connecting")))
        rule.runOnIdle { state.value = VpnState.WaitingNetwork }
        rule.onNodeWithTag("ConnectButton").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, stringsOf(AppLanguage.ENGLISH).get("state.waitingNetwork")))
        rule.runOnIdle { state.value = VpnState.Stopping }
        rule.onNodeWithTag("ConnectButton").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, stringsOf(AppLanguage.ENGLISH).get("state.stopping")))
    }
}
