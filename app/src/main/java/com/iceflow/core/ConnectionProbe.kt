package com.iceflow.core

import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.net.HttpURLConnection
import java.io.IOException

/** Tests the running outbound, never the system's direct connection or a new core. */
object ConnectionProbe {

    fun publicIp(socksPort: Int): String {
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort))
        val connection = URL("https://api.ipify.org").openConnection(proxy) as HttpURLConnection
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        connection.instanceFollowRedirects = false
        return try {
            check(connection.responseCode == 200) { "IP 查询失败" }
            val value = connection.inputStream.use { input ->
                val bytes = ByteArray(65)
                var count = 0
                while (count < bytes.size) {
                    val read = input.read(bytes, count, bytes.size - count)
                    if (read < 0) break
                    count += read
                }
                check(count <= 64) { "IP 查询响应过大" }
                String(bytes, 0, count, Charsets.US_ASCII).trim()
            }
            check(value.isNotEmpty() && value.matches(Regex("[0-9a-fA-F:.]+"))) { "IP 查询响应无效" }
            value
        } finally { connection.disconnect() }
    }

    fun measure(socksPort: Int, url: String = "https://www.google.com/generate_204", timeoutMs: Int = 5000): Int {
        return try {
            XrayBridge.measureSocksDelay(socksPort, url, timeoutMs).coerceAtLeast(1).toInt()
        } catch (error: Exception) {
            throw IOException(error.message ?: "测速失败", error)
        }
    }
}
