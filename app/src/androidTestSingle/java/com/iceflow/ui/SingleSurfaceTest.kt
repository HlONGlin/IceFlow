package com.iceflow.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iceflow.IceFlowApp
import com.iceflow.core.CoreVariantBridge
import com.iceflow.data.model.AppLanguage
import com.iceflow.data.model.AppSettings
import com.iceflow.i18n.stringsOf
import com.iceflow.service.TrafficSnapshot
import com.iceflow.service.VpnController
import com.iceflow.service.VpnState
import com.iceflow.service.VpnErrorCatalog
import com.iceflow.service.VpnErrorType
import com.iceflow.ui.screen.SettingsScreen
import com.iceflow.ui.theme.IceFlowTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Asserts the production single UI and ViewModel, without depending on public IP providers. */
@RunWith(AndroidJUnit4::class)
class SingleSurfaceTest {
    @get:Rule val rule = createComposeRule()
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as IceFlowApp
    private lateinit var originalSettings: AppSettings
    private val viewModels = ViewModelStore()

    @Before fun prepare() {
        assertEquals("single", CoreVariantBridge.variantName)
        assertEquals(VpnState.Idle, VpnController.state.value)
        originalSettings = app.container.settings.settings.value
    }

    @After fun restore() {
        rule.runOnIdle {
            viewModels.clear()
            VpnController.setState(VpnState.Idle)
            VpnController.setTraffic(TrafficSnapshot())
            if (::originalSettings.isInitialized) app.container.settings.update { originalSettings }
        }
    }

    @Test fun aboutSectionShowsActualSingleCoreAndNoDualCoreRows() {
        rule.setContent {
            IceFlowTheme(language = AppLanguage.ENGLISH) {
                SettingsScreen(
                    settings = AppSettings(language = "en"),
                    // Supply conspicuous dual versions so a leaked row cannot quietly pass.
                    xrayVersion = "dual-xray-sentinel", coreVersion = "dual-rust-sentinel",
                    coreVariant = CoreVariantBridge.variantName, mihomoVersion = CoreVariantBridge.coreVersionLabel,
                    appVersion = "test", onUpdate = {},
                )
            }
        }
        rule.onNodeWithText("mihomo").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("xray-core").assertDoesNotExist()
        rule.onNodeWithText("Rust core").assertDoesNotExist()
        rule.onNodeWithText("dual-xray-sentinel").assertDoesNotExist()
        rule.onNodeWithText("dual-rust-sentinel").assertDoesNotExist()
    }

    @Test fun failedPublicIpLookupKeepsConnectedUiAndLiveTraffic() {
        lateinit var vm: MainViewModel
        rule.runOnIdle {
            app.container.settings.update { it.copy(language = "en", showPublicIp = true, showTrafficStats = true) }
            vm = MainViewModel(app)
            viewModels.put("surface", vm)
        }
        rule.setContent { IceFlowTheme(language = AppLanguage.ENGLISH) { IceFlowRoot(vm = vm, onToggleVpn = {}) } }
        val connected = VpnState.Connected("Single surface node", System.currentTimeMillis(), socksPort = 1)
        rule.runOnIdle {
            VpnController.setTraffic(TrafficSnapshot(upSpeed = 2048, downSpeed = 4096))
            VpnController.setState(connected)
        }
        // Closed local SOCKS port produces a deterministic transport failure; no internet dependency.
        rule.waitUntil(20_000) { vm.publicIp.value == PublicIpState.Failed }
        val failureText = stringsOf(AppLanguage.ENGLISH).get("publicIp.failed")
        rule.onNodeWithText(failureText, substring = true).assertIsDisplayed()
        rule.onNodeWithText("↑ 2.0 KB/s", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("↓ 4.0 KB/s", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(connected, vm.vpnState.value)
        rule.runOnIdle { VpnController.setState(VpnState.Idle) }
        rule.waitUntil { vm.publicIp.value == PublicIpState.Idle }
        rule.onNodeWithText(failureText, substring = true).assertDoesNotExist()
    }

    @Test fun coreFailureBannerReconnectsAndCleanupFailureOpensRuntimeLogs() {
        lateinit var vm: MainViewModel
        var reconnects = 0
        rule.runOnIdle {
            app.container.settings.update { it.copy(language = "en") }
            vm = MainViewModel(app)
            viewModels.put("surface", vm)
        }
        rule.setContent {
            IceFlowTheme(language = AppLanguage.ENGLISH) { IceFlowRoot(vm = vm, onToggleVpn = { reconnects++ }) }
        }
        val coreFailure = VpnErrorCatalog.presentation(VpnErrorType.CORE_START_FAILED, AppLanguage.ENGLISH)
        rule.runOnIdle { VpnController.setState(VpnState.Error(coreFailure)) }
        rule.onNodeWithText(coreFailure.message).assertIsDisplayed()
        rule.onNodeWithText(coreFailure.nextAction).assertIsDisplayed()
        rule.onNodeWithText(coreFailure.actionLabel).performClick()
        assertEquals(1, reconnects)

        val cleanupFailure = VpnErrorCatalog.presentation(VpnErrorType.RESOURCE_CLEANUP_FAILED, AppLanguage.ENGLISH)
        rule.runOnIdle { VpnController.setState(VpnState.Error(cleanupFailure)) }
        rule.onNodeWithText(cleanupFailure.message).assertIsDisplayed()
        rule.onNodeWithText(cleanupFailure.actionLabel).performClick()
        rule.onNodeWithText(stringsOf(AppLanguage.ENGLISH).get("logs.title")).assertIsDisplayed()
    }
}
