package com.iceflow.core

import com.iceflow.data.XrayStats
import kotlinx.serialization.json.*
import libmihomo.Libmihomo

/** Direct references keep the gomobile API reachable in minified release builds. */
internal interface MihomoNative {
    fun available(): Boolean
    fun home(dir: String)
    fun validate(config: String)
    fun prepare(config: String)
    fun attach(fd: Int, mtu: Int)
    fun stopTun()
    fun stop()
    fun coreRunning(): Boolean
    fun healthy(): Boolean
    fun stats(): String
    fun queryStats(): String
}

internal object GomobileMihomo : MihomoNative {
    private val loaded by lazy { runCatching { Libmihomo.version() == "v1.19.32" }.getOrDefault(false) }
    override fun available() = loaded
    override fun home(dir: String) = Libmihomo.setHomeDir(dir)
    override fun validate(config: String) = Libmihomo.validate(config)
    override fun prepare(config: String) = Libmihomo.prepare(config)
    override fun attach(fd: Int, mtu: Int) = Libmihomo.attachTun(fd.toLong(), mtu.toLong())
    override fun stopTun() = Libmihomo.stopTun()
    override fun stop() = Libmihomo.stop()
    override fun coreRunning() = Libmihomo.coreRunning()
    override fun healthy() = Libmihomo.isRunning()
    override fun stats() = Libmihomo.statsJSON()
    override fun queryStats() = Libmihomo.queryStats()
}

internal class MihomoRuntime(private val native: MihomoNative = GomobileMihomo) {
    val isReady: Boolean get() = native.available()
    fun prepareAssets(dir: String) { if (!isCoreRunning()) native.home(dir) }
    fun validateConfig(config: String) = native.validate(config)
    fun start(config: String) { check(isReady) { "Single-core build is not ready." }; native.prepare(config) }
    fun attachTun(fd: Int, mtu: Int) = native.attach(fd, mtu)
    fun stopTun() { if (isReady) native.stopTun() }
    fun stop() { if (isReady) native.stop() }
    fun isCoreRunning() = isReady && native.coreRunning()
    fun isHealthy() = isReady && native.healthy()
    fun stats(): LongArray {
        if (!isReady) return longArrayOf(0, 0) // missing core has never started a session
        val values = Json.parseToJsonElement(native.stats()).jsonObject
        val up = values.getValue("upload").jsonPrimitive.long
        val down = values.getValue("download").jsonPrimitive.long
        check(up >= 0 && down >= 0) { "Invalid mihomo cumulative statistics" }
        return longArrayOf(up, down)
    }
    fun queryStats(): XrayStats = XrayStats.parse(native.queryStats())
}
