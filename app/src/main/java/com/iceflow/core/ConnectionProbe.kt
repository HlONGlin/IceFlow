package com.iceflow.core

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.util.Base64
import javax.net.ssl.SSLParameters
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/** Tests the running outbound through a local proxy inbound. */
object ConnectionProbe {
    fun publicIp(socksPort: Int, username: String = "", password: String = ""): String {
        return withSocksSocket(socksPort, username, password, "api.ipify.org", 443, 5000) { socket, _, _ ->
            val tls = (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(socket, "api.ipify.org", 443, true) as SSLSocket
            tls.use { secure ->
                secure.soTimeout = 5000; configureTlsSocket(secure, "api.ipify.org"); secure.startHandshake()
                outputRequest(secure.outputStream, "GET / HTTP/1.1\r\nHost: api.ipify.org\r\nAccept: text/plain\r\nConnection: close\r\n\r\n")
                val input = BufferedInputStream(secure.inputStream); val status = input.readAsciiLine()
                check(status.startsWith("HTTP/1.") && status.substringAfter(' ').trim().take(3) == "200") { "Public IP lookup failed" }
                var chunked = false
                while (true) {
                    val header = input.readAsciiLine()
                    if (header.isEmpty()) break
                    if (header.substringBefore(':').equals("Transfer-Encoding", true)) chunked = header.substringAfter(':').contains("chunked", true)
                }
                String(if (chunked) input.readChunkedBody(64) else input.readBoundedBody(64), Charsets.US_ASCII).trim()
                    .also { check(it.matches(Regex("[0-9a-fA-F:.]+"))) { "Public IP response invalid" } }
            }
        }
    }

    internal fun configureTlsSocket(secure: SSLSocket, host: String) {
        secure.sslParameters = SSLParameters().apply { endpointIdentificationAlgorithm = "HTTPS"; serverNames = listOf(SNIHostName(host)) }
        secure.enabledProtocols = arrayOf("TLSv1.2")
    }

    internal fun negotiateSocks5(input: InputStream, output: OutputStream, username: String, password: String, host: String, port: Int) {
        val authenticated = username.isNotEmpty()
        require(!authenticated || username.toByteArray(Charsets.UTF_8).size <= 255 && password.toByteArray(Charsets.UTF_8).size <= 255) { "SOCKS credentials too long" }
        output.write(if (authenticated) byteArrayOf(5, 1, 2) else byteArrayOf(5, 1, 0)); output.flush()
        check(input.read() == 5 && input.read() == if (authenticated) 2 else 0) { "SOCKS authentication method rejected" }
        if (authenticated) {
            val user = username.toByteArray(Charsets.UTF_8); val pass = password.toByteArray(Charsets.UTF_8)
            output.write(byteArrayOf(1, user.size.toByte(), *user, pass.size.toByte(), *pass)); output.flush()
            check(input.read() == 1 && input.read() == 0) { "SOCKS authentication failed" }
        }
        val hostBytes = host.toByteArray(Charsets.US_ASCII); require(hostBytes.size <= 255)
        output.write(byteArrayOf(5, 1, 0, 3, hostBytes.size.toByte(), *hostBytes, (port shr 8).toByte(), port.toByte())); output.flush()
        check(input.read() == 5 && input.read() == 0) { "SOCKS CONNECT failed" }; check(input.read() == 0)
        val addressLength = when (val type = input.read()) { 1 -> 4; 3 -> input.read().also { check(it >= 0) }; 4 -> 16; else -> error("Invalid SOCKS address type $type") }
        readExactly(input, addressLength + 2)
    }

    internal fun readExactly(input: InputStream, size: Int): ByteArray {
        require(size >= 0); val bytes = ByteArray(size); var offset = 0
        while (offset < size) { val count = input.read(bytes, offset, size - offset); if (count < 0) throw EOFException("Unexpected end of proxy response"); if (count == 0) { val value = input.read(); if (value < 0) throw EOFException("Unexpected end of proxy response"); bytes[offset++] = value.toByte() } else offset += count }
        return bytes
    }

    fun measure(socksPort: Int, username: String = "", password: String = "", url: String = "https://www.google.com/generate_204", timeoutMs: Int = 5000): Int = measureSocks5(socksPort, username, password, url, timeoutMs)

    fun measureSocks5(socksPort: Int, username: String = "", password: String = "", url: String, timeoutMs: Int = 5000): Int {
        val target = parseHttpTarget(url)
        return timed(timeoutMs) { withSocksSocket(socksPort, username, password, target.host, target.port, timeoutMs) { socket, input, output ->
            val start = System.nanoTime()
            if (target.https) {
                val tls = (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(socket, target.host, target.port, true) as SSLSocket
                tls.use { secure -> configureTlsSocket(secure, target.host); secure.soTimeout = timeoutMs; secure.startHandshake(); requestAndCheck(secure.inputStream, secure.outputStream, target) }
            } else requestAndCheck(input, output, target)
            elapsedMs(start)
        } }
    }

    fun measureHttpProxy(proxyPort: Int, username: String = "", password: String = "", url: String, timeoutMs: Int = 5000): Int {
        val target = parseHttpTarget(url)
        return timed(timeoutMs) { Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", proxyPort), timeoutMs); socket.soTimeout = timeoutMs
            val input = BufferedInputStream(socket.getInputStream()); val output = BufferedOutputStream(socket.getOutputStream())
            val auth = if (username.isNotEmpty()) "\r\nProxy-Authorization: Basic " + Base64.getEncoder().encodeToString("$username:$password".toByteArray(Charsets.ISO_8859_1)) else ""
            val start = System.nanoTime()
            if (target.https) {
                outputRequest(output, "CONNECT ${target.host}:${target.port} HTTP/1.1\r\nHost: ${target.host}:${target.port}$auth\r\nConnection: close\r\n\r\n")
                check(input.readAsciiLine().substringAfter(' ').trim().take(3) == "200") { "HTTP proxy CONNECT failed" }; while (input.readAsciiLine().isNotEmpty()) Unit
                val tls = (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(socket, target.host, target.port, true) as SSLSocket
                tls.use { secure -> configureTlsSocket(secure, target.host); secure.soTimeout = timeoutMs; secure.startHandshake(); requestAndCheck(secure.inputStream, secure.outputStream, target) }
            } else requestHttpProxyAndCheck(input, output, target, auth)
            elapsedMs(start)
        } }
    }

    private data class HttpTarget(val host: String, val port: Int, val https: Boolean, val path: String)
    private fun parseHttpTarget(url: String): HttpTarget {
        val uri = URI(url); val https = uri.scheme.equals("https", true); require(uri.scheme.equals("http", true) || https) { "Proxy test only supports HTTP and HTTPS URLs" }
        val host = requireNotNull(uri.host) { "Proxy test URL has no host" }
        return HttpTarget(host, uri.port.takeIf { it > 0 } ?: if (https) 443 else 80, https, (uri.rawPath.takeIf { it.isNotEmpty() } ?: "/") + (uri.rawQuery?.let { "?$it" } ?: ""))
    }
    private fun requestAndCheck(input: InputStream, output: OutputStream, target: HttpTarget) {
        outputRequest(output, "GET ${target.path} HTTP/1.1\r\nHost: ${target.host}\r\nAccept: */*\r\nConnection: close\r\n\r\n"); val status = input.readAsciiLine()
        check(status.startsWith("HTTP/1.") && status.substringAfter(' ').trim().take(1) in "23") { "Proxy test returned an invalid HTTP status" }
    }
    private fun requestHttpProxyAndCheck(input: InputStream, output: OutputStream, target: HttpTarget, auth: String) {
        outputRequest(output, "GET http://${target.host}:${target.port}${target.path} HTTP/1.1\r\nHost: ${target.host}\r\nAccept: */*${auth}\r\nConnection: close\r\n\r\n")
        val status = input.readAsciiLine()
        check(status.startsWith("HTTP/1.") && status.substringAfter(' ').trim().take(1) in "23") { "HTTP proxy returned an invalid status" }
    }
    private fun outputRequest(output: OutputStream, request: String) { output.write(request.toByteArray(Charsets.US_ASCII)); output.flush() }
    private fun elapsedMs(start: Long) = ((System.nanoTime() - start) / 1_000_000).toInt().coerceAtLeast(1)
    private fun <T> timed(timeoutMs: Int, block: () -> T): T { require(timeoutMs > 0); return try { block() } catch (e: Exception) { throw IOException(e.message ?: "Proxy test failed", e) } }
    private fun <T> withSocksSocket(port: Int, username: String, password: String, host: String, targetPort: Int, timeoutMs: Int, block: (Socket, InputStream, OutputStream) -> T): T = Socket().use { socket ->
        socket.connect(InetSocketAddress("127.0.0.1", port), timeoutMs); socket.soTimeout = timeoutMs; val input = BufferedInputStream(socket.getInputStream()); val output = BufferedOutputStream(socket.getOutputStream()); negotiateSocks5(input, output, username, password, host, targetPort); block(socket, input, output)
    }
    private fun InputStream.readAsciiLine(): String { val bytes = ByteArrayOutputStream(); while (bytes.size() <= 8192) { val value = read(); if (value < 0) error("Incomplete HTTP response"); if (value == '\n'.code) return bytes.toByteArray().toString(Charsets.US_ASCII).trimEnd('\r'); bytes.write(value) }; error("HTTP response line too long") }
    private fun InputStream.readBoundedBody(limit: Int): ByteArray { val out = ByteArrayOutputStream(); val buf = ByteArray(64); while (out.size() < limit) { val n = read(buf, 0, minOf(buf.size, limit - out.size())); if (n < 0) break; out.write(buf, 0, n) }; return out.toByteArray() }
    private fun InputStream.readChunkedBody(limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        while (out.size() < limit) {
            val size = readAsciiLine().substringBefore(';').trim().toIntOrNull(16) ?: error("Invalid chunked response")
            if (size == 0) { readAsciiLine(); break }
            val chunk = readExactly(this, size); out.write(chunk, 0, minOf(size, limit - out.size()))
            check(read() == '\r'.code && read() == '\n'.code) { "Invalid chunked response" }
        }
        return out.toByteArray()
    }
}
