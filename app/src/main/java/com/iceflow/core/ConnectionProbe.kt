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
    fun measure(socksPort: Int, url: String = "https://www.gstatic.com/generate_204", timeoutMs: Int = 5000): Int {
        val target = URL(url)
        require(target.protocol == "https" || target.protocol == "http")
        val port = if (target.port >= 0) target.port else target.defaultPort
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort))
        val start = System.nanoTime()
        fun remaining(): Int {
            val elapsed = (System.nanoTime() - start) / 1_000_000
            if (elapsed >= timeoutMs) throw SocketTimeoutException("测速超时")
            return (timeoutMs - elapsed).toInt().coerceAtLeast(1)
        }
        Socket(proxy).use { socks ->
            socks.soTimeout = remaining()
            socks.connect(InetSocketAddress.createUnresolved(target.host, port), remaining())
            val connection = if (target.protocol == "https") {
                (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(socks, target.host, port, true).let { it as SSLSocket }.apply {
                    soTimeout = remaining()
                    sslParameters = sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
                    startHandshake()
                }
            } else socks
            connection.use { socket ->
                socket.soTimeout = remaining()
                val path = target.file.ifEmpty { "/" }
                val host = if (':' in target.host && !target.host.startsWith('[')) "[${target.host}]" else target.host
                val authority = if (port == target.defaultPort) host else "$host:$port"
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
                val code = status.toString().split(' ').getOrNull(1)?.toIntOrNull()
                check(status.startsWith("HTTP/") && code != null && code in 200..299) { "测速请求失败：$status" }
            }
            return ((System.nanoTime() - start) / 1_000_000).coerceAtLeast(1).toInt()
        }
    }
}
