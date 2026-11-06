package com.iceflow.data

import org.junit.Assert.*
import org.junit.Test

class AppRoutingClipboardTest {
    @Test fun exportsExclaveBypassFlagAndAllPackagesInStableOrder() {
        val packages = listOf("com.example.z", "android", "com.example.a", "com.example.z")
        assertEquals("false\nandroid\ncom.example.a\ncom.example.z", AppRoutingClipboard.encode(true, packages))
        assertEquals("true\ncom.example.a", AppRoutingClipboard.encode(false, listOf("com.example.a")))
    }

    @Test fun importsExclaveBothModesWithoutInvertingRouting() {
        assertEquals(AppRoutingClipboard.Selection(false, listOf("com.example.app")), AppRoutingClipboard.decode("true\ncom.example.app"))
        assertEquals(AppRoutingClipboard.Selection(true, listOf("com.example.app")), AppRoutingClipboard.decode("false\ncom.example.app"))
    }

    @Test fun handlesWindowsNewlinesWhitespaceBomAndDuplicatePackages() {
        assertEquals(
            AppRoutingClipboard.Selection(true, listOf("com.example.app", "android")),
            AppRoutingClipboard.decode("\uFEFFfalse\r\n com.example.app \r\n\r\nandroid\r\ncom.example.app\r\n")
        )
    }

    @Test fun emptySelectionRoundTripsInBothModes() {
        listOf(true, false).forEach { mode ->
            assertEquals(AppRoutingClipboard.Selection(mode, emptyList()), AppRoutingClipboard.decode(AppRoutingClipboard.encode(mode, emptyList())))
        }
        assertEquals(AppRoutingClipboard.Selection(false, emptyList()), AppRoutingClipboard.decode("true"))
    }

    @Test fun rejectsEmptyClipboardMissingModeAndMalformedPackages() {
        listOf("", " ", "com.example.app", "yes\ncom.example.app", "false\nhttps://example.org", "true\ncom..app", "true\ncom.example app").forEach { text ->
            assertThrows(IllegalArgumentException::class.java) { AppRoutingClipboard.decode(text) }
        }
    }
}
