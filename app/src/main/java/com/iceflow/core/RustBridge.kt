package com.iceflow.core

import android.util.Log

/**
 * Rust 流量层（rust-core，基于 tun2proxy）的 JNI 入口。
 * 负责 TUN fd → SOCKS5 (xray-core) 的转发与流量统计。
 *
 * 若 libiceflow_core.so 未打包（未执行 rust-core/build-android.*），[isAvailable] 为 false。
 */
object RustBridge {

    private const val TAG = "RustBridge"

    const val DNS_VIRTUAL = 0
    const val DNS_OVER_TCP = 1
    const val DNS_DIRECT = 2

    private const val ERR_NOT_LOADED = -100

    val isAvailable: Boolean by lazy {
        try {
            System.loadLibrary("iceflow_core")
            true
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "libiceflow_core.so not found: ${e.message}")
            false
        }
    }

    /** 启动 TUN 转发，返回 0 表示成功，负数为错误码（见 [describe]）。 */
    fun startTun(fd: Int, mtu: Int, proxyUrl: String, dnsMode: Int, dnsAddr: String): Int {
        if (!isAvailable) return ERR_NOT_LOADED
        return nativeStartTun(fd, mtu, proxyUrl, dnsMode, dnsAddr)
    }

    fun stopTun() {
        if (isAvailable) nativeStopTun()
    }

    /** 本次会话累计 [上行字节, 下行字节]。 */
    fun stats(): LongArray = (if (isAvailable) nativeStats() else null) ?: LongArray(2)

    fun isRunning(): Boolean = isAvailable && nativeIsRunning()

    fun version(): String? = if (isAvailable) nativeVersion() else null

    fun describe(code: Int): String = when (code) {
        0 -> "OK"
        -1 -> "流量层已在运行"
        -2 -> "无效的 TUN 文件描述符"
        -3 -> "无效的代理地址"
        -4 -> "无效的 DNS 配置"
        -5 -> "无效的 MTU"
        -6 -> "无法创建工作线程"
        -7 -> "当前平台不支持"
        -8 -> "Rust 核心内部错误"
        ERR_NOT_LOADED -> "Rust 核心未加载"
        else -> "未知错误 ($code)"
    }

    @JvmStatic
    private external fun nativeStartTun(fd: Int, mtu: Int, proxyUrl: String, dnsMode: Int, dnsAddr: String): Int

    @JvmStatic
    private external fun nativeStopTun()

    @JvmStatic
    private external fun nativeStats(): LongArray?

    @JvmStatic
    private external fun nativeIsRunning(): Boolean

    @JvmStatic
    private external fun nativeVersion(): String?
}
