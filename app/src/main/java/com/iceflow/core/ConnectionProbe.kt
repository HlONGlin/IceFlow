package com.iceflow.core

import java.io.IOException
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/** Tests the running outbound, never the system's direct connection or a new core. */
object ConnectionProbe {

    fun publicIp(socksPort: Int, username: String = "", password: String = ""): String {
        return Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", socksPort), 5000)
            socket.soTimeout = 5000
            val input = BufferedInputStream(socket.getInputStream())
            val output = BufferedOutputStream(socket.getOutputStream())
            negotiateSocks5(input, output, username, password, "api.ipify.org", 443)
            val tls = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                .createSocket(socket, "api.ipify.org", 443, true) as SSLSocket
            tls.use { secure ->
                secure.soTimeout = 5000
                secure.sslParameters = SSLParameters().apply { endpointIdentificationAlgorithm = "HTTPS" }
                secure.startHandshake()
                val request = "GET / HTTP/1.1\r\nHost: api.ipify.org\r\nAccept: text/plain\r\nConnection: close\r\n\r\n"
                secure.outputStream.write(request.toByteArray(Charsets.US_ASCII))
                val response = BufferedInputStream(secure.inputStream)
                val status = response.readAsciiLine()
                check(status.startsWith("HTTP/1.") && status.substringAfter(' ').take(3) == "200") { "IP 查询失败" }
                var chunked = false
                while (true) {
                    val header = response.readAsciiLine()
                    if (header.isEmpty()) break
                    if (header.substringBefore(':').equals("Transfer-Encoding", ignoreCase = true)) {
                        chunked = header.substringAfter(':').contains("chunked", ignoreCase = true)
                    }
                }
                val body = if (chunked) response.readChunkedBody(64) else response.readBoundedBody(64)
                val value = String(body, Charsets.US_ASCII).trim()
                check(value.isNotEmpty() && value.matches(Regex("[0-9a-fA-F:.]+"))) { "IP 查询响应无效" }
                value
            }
        }
    }

    internal fun negotiateSocks5(input: java.io.InputStream, output: java.io.OutputStream, username: String, password: String, host: String, port: Int) {
        val authenticated = username.isNotEmpty()
        require(!authenticated || username.toByteArray(Charsets.UTF_8).size <= 255 && password.toByteArray(Charsets.UTF_8).size <= 255) {
            "SOCKS 用户名或密码过长"
        }
        output.write(if (authenticated) byteArrayOf(5, 1, 2) else byteArrayOf(5, 1, 0))
        output.flush()
        check(input.read() == 5 && input.read() == if (authenticated) 2 else 0) { "SOCKS 认证方式不受支持" }
        if (authenticated) {
            val user = username.toByteArray(Charsets.UTF_8)
            val pass = password.toByteArray(Charsets.UTF_8)
            output.write(byteArrayOf(1, user.size.toByte(), *user, pass.size.toByte(), *pass))
            output.flush()
            check(input.read() == 1 && input.read() == 0) { "SOCKS 认证失败" }
        }
        val hostBytes = host.toByteArray(Charsets.US_ASCII)
        output.write(byteArrayOf(5, 1, 0, 3, hostBytes.size.toByte(), *hostBytes, (port shr 8).toByte(), port.toByte()))
        output.flush()
        check(input.read() == 5 && input.read() == 0) { "SOCKS 连接失败" }
        input.read() // reserved
        val addressType = input.read()
        val addressLength = when (addressType) {
            1 -> 4
            3 -> input.read().also { check(it >= 0) }
            4 -> 16
            else -> error("SOCKS 响应地址类型无效")
        }
        check(input.readNBytes(addressLength + 2).size == addressLength + 2) { "SOCKS 响应不完整" }
    }

    private fun java.io.InputStream.readAsciiLine(): String {
        val bytes = ByteArrayOutputStream()
        while (bytes.size() <= 8192) {
            val value = read()
            if (value < 0) error("HTTP 响应不完整")
            if (value == '\n'.code) return bytes.toByteArray().toString(Charsets.US_ASCII).trimEnd('\r')
            bytes.write(value)
        }
        error("HTTP 响应头过长")
    }

    private fun java.io.InputStream.readBoundedBody(maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(maxBytes + 1)
        while (output.size() <= maxBytes) {
            val count = read(buffer, 0, minOf(buffer.size, maxBytes + 1 - output.size()))
            if (count < 0) break
            output.write(buffer, 0, count)
        }
        check(output.size() <= maxBytes) { "IP 查询响应过大" }
        return output.toByteArray()
    }

    private fun java.io.InputStream.readChunkedBody(maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        while (true) {
            val chunkSize = readAsciiLine().substringBefore(';').toIntOrNull(16) ?: error("HTTP 分块响应无效")
            if (chunkSize == 0) break
            check(output.size() + chunkSize <= maxBytes) { "IP 查询响应过大" }
            output.write(readNBytes(chunkSize).also { check(it.size == chunkSize) { "HTTP 分块响应不完整" } })
            check(readAsciiLine().isEmpty()) { "HTTP 分块响应无效" }
        }
        return output.toByteArray()
    }

    fun measure(socksPort: Int, username: String = "", password: String = "", url: String = "https://www.google.com/generate_204", timeoutMs: Int = 5000): Int {
        return try {
            XrayBridge.measureSocksDelay(socksPort, username, password, url, timeoutMs).coerceAtLeast(1).toInt()
        } catch (error: Exception) {
            throw IOException(error.message ?: "测速失败", error)
        }
    }
}
