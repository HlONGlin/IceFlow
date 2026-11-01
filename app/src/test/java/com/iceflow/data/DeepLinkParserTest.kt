package com.iceflow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class DeepLinkParserTest {

    private fun b64(s: String) = Base64.getEncoder().encodeToString(s.toByteArray())

    private fun view(uri: String, mime: String? = null, name: String? = null) = DeepLinkParser.parse(
        DeepLinkParser.Payload(action = DeepLinkParser.ACTION_VIEW, uri = uri, mimeType = mime, displayName = name),
    )

    private fun send(text: String) = DeepLinkParser.parse(
        DeepLinkParser.Payload(action = DeepLinkParser.ACTION_SEND, mimeType = "text/plain", text = text),
    )

    private fun subscription(request: DeepLinkRequest) = request as DeepLinkRequest.AddSubscription

    private fun invalid(request: DeepLinkRequest) = (request as DeepLinkRequest.Invalid).reason

    @Test fun clashInstallConfigIsSubscription() {
        val request = subscription(view("clash://install-config?url=https%3A%2F%2Fsub.example.com%2Fapi%3Ftoken%3Dabc"))
        assertEquals("https://sub.example.com/api?token=abc", request.url)
        assertEquals(null, request.name)
    }

    @Test fun iceflowInstallConfigKeepsName() {
        val request = subscription(view("iceflow://install-config?url=https%3A%2F%2Fsub.example.com%2Fsub&name=My%20Sub"))
        assertEquals("https://sub.example.com/sub", request.url)
        assertEquals("My Sub", request.name)
    }

    @Test fun iceflowImportConfigIsAccepted() {
        val request = subscription(view("iceflow://import-config?url=https://sub.example.com/s"))
        assertEquals("https://sub.example.com/s", request.url)
    }

    @Test fun v2rayNInstallSubIsAccepted() {
        val request = subscription(view("v2rayn://install-sub?url=https://sub.example.com/v2&remark=v2rayN"))
        assertEquals("v2rayN", request.name)
    }

    @Test fun singBoxRemoteProfileIsAccepted() {
        val request = subscription(view("sing-box://import-remote-profile?url=https%3A%2F%2Fsub.example.com%2Fsing"))
        assertEquals("https://sub.example.com/sing", request.url)
    }

    @Test fun trailingSlashCommandStillMatches() {
        val request = subscription(view("clash://install-config/?url=https://sub.example.com/"))
        assertEquals("https://sub.example.com/", request.url)
    }

    @Test fun plainHttpsUrlParamIsNotDoubleDecoded() {
        val request = subscription(view("clash://install-config?url=https://sub.example.com/p%2Fq"))
        assertEquals("https://sub.example.com/p/q", request.url)
    }

    @Test fun httpSubscriptionIsRejected() {
        assertEquals(DeepLinkError.INSECURE_URL, invalid(view("clash://install-config?url=http://sub.example.com/s")))
    }

    @Test fun subscriptionWithoutUrlIsMalformed() {
        assertEquals(DeepLinkError.MALFORMED, invalid(view("clash://install-config?name=x")))
    }

    @Test fun unknownCommandIsUnsupported() {
        assertEquals(DeepLinkError.UNSUPPORTED, invalid(view("iceflow://open-settings?url=https://a.com")))
    }

    @Test fun unknownSchemeIsUnsupported() {
        assertEquals(DeepLinkError.UNSUPPORTED, invalid(view("foobar://install-config?url=https://a.com")))
    }

    @Test fun ordinaryWebLinkIsNotImported() {
        assertEquals(DeepLinkError.UNSUPPORTED, invalid(view("https://example.com/share")))
    }

    @Test fun vlessLinkBecomesTextImport() {
        val text = "vless://11111111-2222-3333-4444-555555555555@example.com:443?encryption=none&security=tls#node"
        assertTrue(view(text) is DeepLinkRequest.ImportText)
    }

    @Test fun hysteria2LinkBecomesTextImport() {
        assertTrue(view("hysteria2://pass@example.com:443?sni=example.com#hy2") is DeepLinkRequest.ImportText)
    }

    @Test fun vmessLinkBecomesTextImport() {
        val body = """{"v":"2","ps":"n","add":"1.2.3.4","port":"443","id":"11111111-2222-3333-4444-555555555555"}"""
        assertTrue(view("vmess://" + b64(body)) is DeepLinkRequest.ImportText)
    }

    @Test fun brokenNodeLinkIsMalformed() {
        assertEquals(DeepLinkError.MALFORMED, invalid(view("vless://not-a-link")))
    }

    @Test fun mieruFullLinkIsRejectedWhenCoreCannotDecode() {
        val request = DeepLinkParser.parse(
            DeepLinkParser.Payload(action = DeepLinkParser.ACTION_VIEW, uri = "mieru://CgR0ZXN0"),
            supportsMieruProtobuf = false,
        )
        assertEquals(DeepLinkError.CORE_UNSUPPORTED, invalid(request))
    }

    @Test fun sharedTextWithLinksIsImported() {
        val request = send("vmess://" + b64("""{"add":"1.2.3.4","port":"443","id":"u"}""") + "\nvless://u@example.com:443#n")
        assertTrue(request is DeepLinkRequest.ImportText)
    }

    @Test fun sharedBareSubscriptionUrlIsSubscription() {
        val request = subscription(send("https://sub.example.com/api?token=1"))
        assertEquals("https://sub.example.com/api?token=1", request.url)
    }

    @Test fun sharedEmptyTextIsInvalid() {
        assertEquals(DeepLinkError.EMPTY, invalid(send("   ")))
    }

    @Test fun oversizedSharedTextIsRejected() {
        val text = "x".repeat(DeepLinkParser.MAX_TEXT_LENGTH + 1)
        assertEquals(DeepLinkError.TOO_LONG, invalid(send(text)))
    }
}
