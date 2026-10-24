package com.iceflow.data.repository

import com.iceflow.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URI

data class SubscriptionDownload(val body: String, val userInfo: String?, val downloadedBytes: Long)

object SubscriptionFetcher {

    private const val MAX_BYTES = 4 * 1024 * 1024
    private const val MAX_REDIRECTS = 5
    suspend fun fetch(url: String): SubscriptionDownload = withContext(Dispatchers.IO) {
        var current = requireHttpsUri(url.trim())
        repeat(MAX_REDIRECTS + 1) { redirects ->
            val conn = URL(current.toString()).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", "IceFlow/${BuildConfig.VERSION_NAME}")
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    check(redirects < MAX_REDIRECTS) { "订阅重定向次数过多" }
                    val location = conn.getHeaderField("Location")
                    check(!location.isNullOrBlank()) { "订阅重定向缺少目标地址" }
                    current = requireHttpsUri(current.resolve(location.trim()).toString())
                } else {
                    check(code in 200..299) { "HTTP $code" }
                    val userInfo = conn.getHeaderField("subscription-userinfo")
                    conn.inputStream.use { input ->
                        val bytes = input.readNBytesCompat(MAX_BYTES + 1)
                        check(bytes.size <= MAX_BYTES) { "订阅内容过大" }
                        return@withContext SubscriptionDownload(String(bytes, Charsets.UTF_8), userInfo, bytes.size.toLong())
                    }
                }
            } finally {
                conn.disconnect()
            }
        }
        error("订阅重定向次数过多")
    }

    internal fun requireHttpsUri(value: String): URI {
        val uri = URI(value)
        require(uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() && uri.userInfo == null) {
            "订阅地址及重定向目标必须是有效的 HTTPS 地址"
        }
        return uri
    }

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (out.size() < limit) {
            val n = read(buf, 0, minOf(buf.size, limit - out.size()))
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}
