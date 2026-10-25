package com.iceflow.data.repository

import android.content.Context
import com.iceflow.core.XrayBridge
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class RoutingResources(private val context: Context) {
    private val prefs = context.getSharedPreferences("routing_resources", Context.MODE_PRIVATE)
    val names = listOf("geosite.dat", "geoip.dat")

    /** 进程内标记：本进程是否已确认 geo 数据库存在于 filesDir。 */
    private var installed = false

    /**
     * 确保 geo 数据库已就位，并设置 xray 的资源目录。
     *
     * 解压 28MB 资产在真机上可能耗时数秒。App 启动与连接流程都会调用本方法，
     * 若各调用一次会重复解压；此处用 @Synchronized 串行化，并把结果记入
     * 进程内标记，使同一进程内只真正解压一次，后续调用只做目录设置（毫秒级）。
     */
    @Synchronized fun prepare() {
        if (!installed) {
            names.forEach { name ->
                val file = File(context.filesDir, name)
                if (!file.isFile) context.assets.open("routing/$name").use { input ->
                    val tmp = File(context.filesDir, "$name.tmp")
                    tmp.outputStream().use { input.copyTo(it) }
                    check(tmp.renameTo(file)) { "安装路由资源失败：$name" }
                }
            }
            installed = true
        }
        if (XrayBridge.isAvailable) {
            XrayBridge.setAssetDir(context.filesDir.absolutePath)
            // 域匹配器缓存目录：与 geo 数据库同目录，保证 geosite 更新时缓存自动失效
            XrayBridge.setMphCacheDir(context.filesDir.absolutePath)
        }
    }

    fun url(name: String): String { require(name in names); return prefs.getString(name, null) ?: "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/$name" }
    fun description(name: String): String {
        require(name in names)
        val file = File(context.filesDir, name)
        return "${file.length() / 1024} KiB · " + if (prefs.contains("updated:$name")) java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(prefs.getLong("updated:$name", 0))) else "内置 202610212206"
    }
    @Synchronized fun update(name: String, source: String) {
        require(name in names)
        prepare()
        val uri = java.net.URI(source.trim())
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null) { "资源地址须为 HTTPS .dat 下载链接" }
        val tmp = File(context.filesDir, "$name.download")
        try {
            val connection = URL(source.trim()).openConnection() as HttpURLConnection
            connection.connectTimeout = 15000; connection.readTimeout = 30000
            try {
                check(connection.responseCode in 200..299) { "下载失败：HTTP ${connection.responseCode}" }
                connection.inputStream.use { input -> tmp.outputStream().use { out ->
                    val buffer = ByteArray(32768); var size = 0L
                    while (true) { val n = input.read(buffer); if (n < 0) break; size += n; check(size <= 64L * 1024 * 1024) { "资源不能超过 64 MiB" }; out.write(buffer, 0, n) }
                } }
            } finally { connection.disconnect() }
            XrayBridge.validateRoutingAsset(tmp.absolutePath, name)
            check(tmp.renameTo(File(context.filesDir, name))) { "替换路由资源失败" }
            prefs.edit().putString(name, source.trim()).putLong("updated:$name", System.currentTimeMillis()).apply()
        } finally { tmp.delete() }
    }
}
