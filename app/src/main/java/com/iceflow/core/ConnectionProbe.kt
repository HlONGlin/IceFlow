package com.iceflow.core

import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.URL
import java.net.SocketTimeoutException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/** Tests the running outbound, never the system's direct connection or a new core. */
object ConnectionProbe {

    private const val TAG = "ConnectionProbe"

    fun measure(socksPort: Int, url: String = "https://www.google.com/generate_204", timeoutMs: Int = 5000): Int {
        // Add a cache-busting query to the URL that is actually sent through SOCKS.
        val testUrl = if ('?' in url) "$url&_t=${System.currentTimeMillis()}" else "$url?_t=${System.currentTimeMillis()}"
        val target = URL(testUrl)
        require(target.protocol == "https" || target.protocol == "http")
        val port = if (target.port >= 0) target.port else target.defaultPort
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort))
        val start = System.nanoTime()

        android.util.Log.d(TAG, "开始测速: url=$testUrl, socksPort=$socksPort, target=${target.host}:$port")

        fun remaining(): Int {
            val elapsed = (System.nanoTime() - start) / 1_000_000
            if (elapsed >= timeoutMs) throw SocketTimeoutException("测速超时")
            return (timeoutMs - elapsed).toInt().coerceAtLeast(1)
        }

        try {
            Socket(proxy).use { socks ->
                socks.soTimeout = remaining()
                val connectStart = System.nanoTime()
                socks.connect(InetSocketAddress.createUnresolved(target.host, port), remaining())
                val connectTime = (System.nanoTime() - connectStart) / 1_000_000
                android.util.Log.d(TAG, "SOCKS连接成功: ${connectTime}ms")

                val connection = if (target.protocol == "https") {
                    val sslStart = System.nanoTime()
                    val ssl = (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(socks, target.host, port, true).let { it as SSLSocket }.apply {
                        soTimeout = remaining()
                        sslParameters = sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
                        startHandshake()
                    }
                    val sslTime = (System.nanoTime() - sslStart) / 1_000_000
                    android.util.Log.d(TAG, "SSL握手成功: ${sslTime}ms")
                    ssl
                } else socks

                connection.use { socket ->
                    socket.soTimeout = remaining()
                    val path = target.file.ifEmpty { "/" }
                    val host = if (':' in target.host && !target.host.startsWith('[')) "[${target.host}]" else target.host
                    val authority = if (port == target.defaultPort) host else "$host:$port"
                    val requestStart = System.nanoTime()
                    socket.getOutputStream().write("GET $path HTTP/1.1\r\nHost: $authority\r\nConnection: close\r\nCache-Control: no-cache\r\n\r\n".toByteArray(Charsets.US_ASCII))
                    val input = socket.getInputStream()
                    val status = StringBuilder()
                    while (status.length < 1024) {
                        socket.soTimeout = remaining()
                        val next = input.read()
                        check(next >= 0) { "测速响应不完整" }
                        if (next == 10) break
                        status.append(next.toChar())
                    }
                    val requestTime = (System.nanoTime() - requestStart) / 1_000_000
                    android.util.Log.d(TAG, "HTTP请求完成: ${requestTime}ms, status=${status.toString().trim()}")

                    val code = status.toString().split(' ').getOrNull(1)?.toIntOrNull()
                    check(status.startsWith("HTTP/") && code != null && code in 200..299) { "测速请求失败：$status" }
                }
                val totalTime = ((System.nanoTime() - start) / 1_000_000).coerceAtLeast(1).toInt()
                android.util.Log.i(TAG, "测速完成: ${totalTime}ms")
                return totalTime
            }
        } catch (e: Exception) {
            val elapsed = ((System.nanoTime() - start) / 1_000_000).toInt()
            android.util.Log.e(TAG, "测速失败 (${elapsed}ms): ${e.message}", e)
            throw e
        }
    }
}
