package com.iceflow.data.model

import java.net.URI

/** Shared subscription URL validation for configuration, backups, and downloads. */
object SubscriptionUrl {
    fun requireHttpsUri(value: String): URI {
        val uri = URI(value)
        require(uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() && uri.userInfo == null) {
            "订阅地址及重定向目标必须是有效的 HTTPS 地址"
        }
        return uri
    }
}
