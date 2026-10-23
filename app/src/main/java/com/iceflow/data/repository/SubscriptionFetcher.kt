package com.iceflow.data.repository

import com.iceflow.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object SubscriptionFetcher {

    private const val MAX_BYTES = 4 * 1024 * 1024
    @Volatile var lastUserInfo: String? = null
        private set
    @Volatile var lastDownloadedBytes: Long = 0
        private set

    suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        val u = URL(url.trim())
        require(u.protocol == "https") { "仅支持 https 订阅地址" }
        val conn = u.openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("User-Agent", "IceFlow/${BuildConfig.VERSION_NAME}")
        try {
            val code = conn.responseCode
            check(code in 200..299) { "HTTP $code" }
            lastUserInfo = conn.getHeaderField("subscription-userinfo")
            conn.inputStream.use { input ->
                val bytes = input.readNBytesCompat(MAX_BYTES + 1)
                check(bytes.size <= MAX_BYTES) { "订阅内容过大" }
                lastDownloadedBytes = bytes.size.toLong()
                String(bytes, Charsets.UTF_8)
            }
        } finally {
            conn.disconnect()
        }
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
