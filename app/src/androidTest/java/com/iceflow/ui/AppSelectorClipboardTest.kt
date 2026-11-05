package com.iceflow.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iceflow.data.AppRoutingClipboard
import com.iceflow.data.model.AppSettings
import com.iceflow.data.model.AppTheme
import com.iceflow.data.model.NightMode
import com.iceflow.data.repository.SettingsRepository
import com.iceflow.ui.screen.AppSelectorScreen
import com.iceflow.ui.theme.IceFlowTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppSelectorClipboardTest {
    @get:Rule val rule = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val clipboard get() = context.getSystemService(ClipboardManager::class.java)
    private val state = mutableStateOf(AppSettings(appProxyMode = true, bypassApps = listOf("com.android.settings")))
    private val theme = mutableStateOf(AppTheme.LIGHT_BLUE)
    private val nightMode = mutableStateOf(NightMode.DISABLED)

    private fun show(onImport: (Boolean, List<String>) -> Unit = { mode, apps ->
        state.value = state.value.copy(appProxyMode = mode, proxyAppsOnly = mode, bypassApps = apps)
    }) {
        rule.setContent {
            IceFlowTheme(theme = theme.value, nightMode = nightMode.value) {
                AppSelectorScreen(
                    proxyMode = state.value.appProxyMode,
                    selectedApps = state.value.bypassApps,
                    onSelectionChange = { state.value = state.value.copy(bypassApps = it) },
                    onImport = onImport,
                    onDismiss = {},
                )
            }
        }
    }

    private fun action(label: String) {
        rule.onNodeWithContentDescription("应用列表操作").performClick()
        rule.waitUntil(10_000) {
            rule.onAllNodes(hasText(label) and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(label).performClick()
    }

    private fun paste(text: String) {
        rule.runOnIdle { clipboard.setPrimaryClip(ClipData.newPlainText("app-routing-test", text)) }
        action("从剪贴板导入")
    }

    @Test fun exportIncludesHiddenAndFilteredSelectionsAndMode() {
        state.value = state.value.copy(bypassApps = listOf("com.android.settings", "com.android.shell", "com.example.uninstalled"))
        show()
        rule.onNode(hasSetTextAction()).performTextInput("does-not-match-any-app")
        action("导出到剪贴板")
        rule.runOnIdle {
            val exported = clipboard.primaryClip!!.getItemAt(0).text.toString()
            assertEquals("false\ncom.android.settings\ncom.android.shell\ncom.example.uninstalled", exported)
            assertEquals(3, state.value.bypassApps.size)
        }
    }

    @Test fun exclaveImportReplacesSelectionSwitchesBothModesAndPersists() {
        val isolated = object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String, mode: Int) =
                super.getSharedPreferences("app-clipboard-qa-$name", mode)
        }
        val prefs = isolated.getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        try {
            val repository = SettingsRepository(isolated)
            repository.update { state.value }
            show { mode, apps ->
                repository.update { it.copy(appProxyMode = mode, proxyAppsOnly = mode, bypassApps = apps) }
                state.value = repository.settings.value
            }
            paste("true\r\ncom.android.shell\r\ncom.android.shell\r\n${context.packageName}\r\ncom.example.uninstalled\r\n")
            rule.runOnIdle {
                assertFalse(state.value.appProxyMode)
                assertFalse(state.value.proxyAppsOnly)
                assertEquals(listOf("com.android.shell", "com.example.uninstalled"), state.value.bypassApps)
                val saved = SettingsRepository(isolated).settings.value
                assertFalse(saved.appProxyMode)
                assertEquals(state.value.bypassApps.toSet(), saved.bypassApps.toSet())
            }
            rule.onNodeWithText("选择绕过应用").assertIsDisplayed()
            paste("false\ncom.android.settings")
            rule.runOnIdle {
                val saved = SettingsRepository(isolated).settings.value
                assertTrue(saved.appProxyMode)
                assertTrue(saved.proxyAppsOnly)
                assertEquals(listOf("com.android.settings"), saved.bypassApps)
            }
            rule.onNodeWithText("选择代理应用").assertIsDisplayed()
        } finally {
            prefs.edit().clear().commit()
        }
    }

    @Test fun emptyAndMalformedClipboardLeaveSettingsUntouched() {
        show()
        val before = state.value
        paste("")
        rule.onNodeWithText("剪贴板为空，未更改应用选择").assertIsDisplayed()
        rule.runOnIdle { assertEquals(before, state.value) }
        paste("false\nhttps://not-an-app.example")
        rule.onNodeWithText("导入失败：首行应为 true（绕过）或 false（代理），之后每行一个应用包名").assertIsDisplayed()
        rule.runOnIdle { assertEquals(before, state.value) }
    }

    @Test fun failedSaveIsReportedWithoutClosingTheScreen() {
        show { _, _ -> throw IllegalArgumentException("Invalid existing settings") }
        val before = state.value
        paste("true\ncom.android.shell")
        rule.onNodeWithText("无法保存导入的应用设置，请检查当前配置后重试").assertIsDisplayed()
        rule.onNodeWithText("选择代理应用").assertIsDisplayed()
        rule.runOnIdle { assertEquals(before, state.value) }
    }

    @Test fun intentionallyEmptySelectionCanBeExportedAndRestored() {
        state.value = state.value.copy(appProxyMode = false, bypassApps = emptyList())
        show()
        action("导出到剪贴板")
        var exported = ""
        rule.runOnIdle {
            exported = clipboard.primaryClip!!.getItemAt(0).text.toString()
            assertEquals("true\n", exported)
            state.value = state.value.copy(appProxyMode = true, bypassApps = listOf("com.android.settings"))
        }
        paste(exported)
        rule.runOnIdle {
            assertFalse(state.value.appProxyMode)
            assertTrue(state.value.bypassApps.isEmpty())
        }
    }

    @Test fun clearMenuRemovesHiddenFilteredAndUninstalledSelections() {
        state.value = state.value.copy(bypassApps = listOf("com.android.settings", "com.example.uninstalled"))
        show()
        rule.onNode(hasSetTextAction()).performTextInput("does-not-match-any-app")
        action("清除全部选择")
        rule.runOnIdle {
            assertTrue(state.value.bypassApps.isEmpty())
            assertTrue(state.value.appProxyMode)
        }
        rule.onNodeWithContentDescription("应用列表操作").performClick()
        rule.onNodeWithText("清除全部选择").assertIsNotEnabled()
    }

    @Test fun invertMenuTogglesAllInstalledAppsDespiteFiltersAndRoundTrips() {
        val installed = context.packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .map { it.packageName }.filterNot { it == context.packageName }.toSet()
        val original = setOf("com.android.settings", "com.example.uninstalled")
        assertTrue("The emulator must have Settings installed", "com.android.settings" in installed)
        state.value = state.value.copy(bypassApps = original.toList())
        show()
        rule.onNodeWithText("已选").performClick()
        rule.onNode(hasSetTextAction()).performTextInput("does-not-match-any-app")
        action("反选全部应用")
        rule.runOnIdle {
            assertEquals((installed - original) + (original - installed), state.value.bypassApps.toSet())
            assertFalse(context.packageName in state.value.bypassApps)
            assertTrue(state.value.appProxyMode)
        }
        action("反选全部应用")
        rule.runOnIdle { assertEquals(original, state.value.bypassApps.toSet()) }
    }

    @Test fun clipboardMenuWorksAcrossAllThemesInLightAndDarkModes() {
        show()
        val directory = context.getExternalFilesDir("clipboard-qa")!!
        directory.mkdirs()
        for (style in AppTheme.entries) {
            for (night in listOf(NightMode.DISABLED, NightMode.ENABLED)) {
                rule.runOnIdle { theme.value = style; nightMode.value = night }
                rule.onNodeWithContentDescription("应用列表操作").performClick()
                rule.onNodeWithText("导出到剪贴板").assertIsDisplayed()
                rule.onNodeWithText("从剪贴板导入").assertIsDisplayed()
                rule.onNodeWithText("清除全部选择").assertIsDisplayed()
                rule.onNodeWithText("反选全部应用").assertIsDisplayed()
                rule.waitForIdle()
                val screenshot = instrumentation.uiAutomation.takeScreenshot()
                File(directory, "${style.name}-${night.name}.png").outputStream().use {
                    screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                screenshot.recycle()
                rule.onNodeWithText("导出到剪贴板").performClick()
                rule.runOnIdle {
                    assertEquals(state.value.appProxyMode, AppRoutingClipboard.decode(clipboard.primaryClip!!.getItemAt(0).text.toString()).proxyMode)
                }
            }
        }
    }
}
