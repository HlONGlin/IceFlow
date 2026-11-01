package com.iceflow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepLinkParserFileTest {

    private fun view(uri: String, mime: String? = null, name: String? = null) = DeepLinkParser.parse(
        DeepLinkParser.Payload(action = DeepLinkParser.ACTION_VIEW, uri = uri, mimeType = mime, displayName = name),
    )

    @Test fun contentUriWithYamlNameIsFileImport() {
        val request = view("content://com.android.providers.downloads.documents/document/1234", "application/octet-stream", "config.yaml")
        request as DeepLinkRequest.ImportFile
        assertEquals("config.yaml", request.displayName)
    }

    @Test fun contentUriWithTextMimeIsFileImport() {
        assertTrue(view("content://media/external/file/42", "text/plain") is DeepLinkRequest.ImportFile)
    }

    @Test fun contentUriWithJsonMimeIsFileImport() {
        assertTrue(view("content://x/y", "application/json") is DeepLinkRequest.ImportFile)
    }

    @Test fun contentUriWithUnsupportedTypeIsRejected() {
        val request = view("content://com.example.app/image/9", "image/png")
        assertEquals(DeepLinkError.UNSUPPORTED_FILE, (request as DeepLinkRequest.Invalid).reason)
    }

    @Test fun fileUriWithTxtExtensionIsFileImport() {
        assertTrue(view("file:///storage/emulated/0/nodes.txt", null, "nodes.txt") is DeepLinkRequest.ImportFile)
    }

    @Test fun malformedUriWithoutSchemeIsRejected() {
        assertEquals(DeepLinkError.MALFORMED, (view("not-a-uri") as DeepLinkRequest.Invalid).reason)
    }

    @Test fun oversizedLinkIsRejected() {
        val uri = "vless://" + "a".repeat(DeepLinkParser.MAX_LINK_LENGTH)
        assertEquals(DeepLinkError.TOO_LONG, (view(uri) as DeepLinkRequest.Invalid).reason)
    }

    @Test fun nodeSchemesCoverEveryShareLinkFamily() {
        val required = listOf("vmess", "vless", "trojan", "ss", "ssr", "hysteria2", "hy2", "tuic", "wireguard", "anytls", "mieru", "mierus")
        required.forEach { assertTrue("missing $it", it in DeepLinkParser.nodeSchemes) }
        // http/https 与普通网页冲突，不能注册成深链接
        assertTrue("https" !in DeepLinkParser.nodeSchemes)
    }
}
