package com.iceflow.core

import android.util.Log
import java.lang.reflect.InvocationTargetException

/**
 * xray-core（gomobile 生成的 libxray.Libxray）的封装。
 *
 * 通过反射调用，使 app 在未放入 xray.aar 时仍能编译运行（此时 [isAvailable] 为 false）。
 * Go 侧签名见 xray-wrapper/libxray.go。
 */
object XrayBridge {

    private const val TAG = "XrayBridge"
    private const val CLASS_NAME = "libxray.Libxray"

    private val clazz: Class<*>? by lazy {
        try {
            Class.forName(CLASS_NAME)
        } catch (t: Throwable) {
            // ClassNotFoundException（未集成）或 UnsatisfiedLinkError（gojni 缺失）
            Log.w(TAG, "xray-core unavailable: $t")
            null
        }
    }

    val isAvailable: Boolean get() = clazz != null

    fun setAssetDir(dir: String) {
        call("setAssetDir", arrayOf(String::class.java), dir)
    }
    fun validateRoutingAsset(path: String, kind: String) {
        call("validateRoutingAsset", arrayOf(String::class.java, String::class.java), path, kind)
    }

    /** 启动 xray-core；配置错误时抛出异常（Go error）。 */
    fun start(configJson: String) {
        call("start", arrayOf(String::class.java), configJson)
    }

    fun stop() {
        call("stop", emptyArray())
    }

    fun isRunning(): Boolean = call("isRunning", emptyArray()) as Boolean

    fun version(): String? = runCatching { call("version", emptyArray()) as String }.getOrNull()

    /** 官方 mieru:// Protobuf 配置可包含多个配置及服务器，统一转换为 mierus:// 列表。 */
    fun normalizeMieruLink(link: String): String =
        call("normalizeMieruLink", arrayOf(String::class.java), link) as String

    /** 使用临时实例测量经代理访问 [url] 的耗时（毫秒），失败抛出异常。 */
    fun measureDelay(configJson: String, url: String, timeoutMs: Long): Long =
        call(
            "measureDelay",
            arrayOf(String::class.java, String::class.java, Long::class.javaPrimitiveType!!),
            configJson, url, timeoutMs,
        ) as Long

    fun measureSocksDelay(socksPort: Int, username: String, password: String, url: String, timeoutMs: Int): Long =
        call(
            "measureSocksDelay",
            arrayOf(Long::class.javaPrimitiveType!!, String::class.java, String::class.java, String::class.java, Long::class.javaPrimitiveType!!),
            socksPort.toLong(), username, password, url, timeoutMs.toLong(),
        ) as Long

    /** 查询 xray-core 统计信息，返回格式：每行 "name,value"。失败抛出异常。 */
    fun queryStats(): String =
        call("queryStats", emptyArray()) as String

    private fun call(name: String, types: Array<Class<*>>, vararg args: Any?): Any? {
        val c = clazz ?: throw IllegalStateException("未集成 xray-core（缺少 app/libs/xray.aar）")
        val method = c.getMethod(name, *types)
        try {
            return method.invoke(null, *args)
        } catch (e: InvocationTargetException) {
            throw e.targetException ?: e
        }
    }
}
