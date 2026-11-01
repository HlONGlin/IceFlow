package com.iceflow.data

import com.iceflow.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 单核变体（仅 mihomo）里的深链接行为：常规订阅与分享链接可用，
 * 依赖 xray 解码的 mieru 完整链接必须明确报错，不能静默忽略。
 */
class DeepLinkSingleCoreTest {

    @Test fun singleVariantRejectsMieruFullLink() {
        assertEquals("single", BuildConfig.CORE_VARIANT)
        val request = DeepLinkParser.parse(
            DeepLinkParser.Payload(action = DeepLinkParser.ACTION_VIEW, uri = "mieru://CgR0ZXN0"),
            supportsMieruProtobuf = false,
        )
        assertEquals(DeepLinkError.CORE_UNSUPPORTED, (request as DeepLinkRequest.Invalid).reason)
    }

    @Test fun singleVariantStillAcceptsSubscriptionsAndShareLinks() {
        val subscription = DeepLinkParser.parse(
            DeepLinkParser.Payload(action = DeepLinkParser.ACTION_VIEW, uri = "clash://install-config?url=https://sub.example.com/s"),
            supportsMieruProtobuf = false,
        )
        assertEquals("https://sub.example.com/s", (subscription as DeepLinkRequest.AddSubscription).url)
        val node = DeepLinkParser.parse(
            DeepLinkParser.Payload(action = DeepLinkParser.ACTION_VIEW, uri = "hysteria2://pass@example.com:443#hy2"),
            supportsMieruProtobuf = false,
        )
        assertTrue(node is DeepLinkRequest.ImportText)
    }

    @Test fun singleVariantImportsSimpleMieruLink() {
        val link = "mierus://user:pass@example.com:8964?protocol=TCP&profile=mieru-test"
        val request = DeepLinkParser.parse(
            DeepLinkParser.Payload(action = DeepLinkParser.ACTION_VIEW, uri = link),
            supportsMieruProtobuf = false,
        )
        assertTrue(request is DeepLinkRequest.ImportText)
    }
}
