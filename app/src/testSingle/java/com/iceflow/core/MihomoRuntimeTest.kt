package com.iceflow.core

import org.junit.Assert.*
import org.junit.Test

class MihomoRuntimeTest {
    private class Native : MihomoNative {
        var ready = true
        var running = false
        var tun = false
        var failure: RuntimeException? = null
        val events = mutableListOf<String>()
        var rawStats = """{"upload":12,"download":34}"""
        override fun available() = ready
        override fun home(dir: String) { events += "home" }
        override fun validate(config: String) { events += "validate"; failure?.let { throw it } }
        override fun prepare(config: String) { events += "prepare"; failure?.let { throw it }; running = true }
        override fun attach(fd: Int, mtu: Int) { events += "attach:$fd:$mtu"; failure?.let { throw it }; tun = true }
        override fun stopTun() { events += "stopTun"; tun = false }
        override fun stop() { events += "stop"; running = false }
        override fun coreRunning() = running
        override fun healthy() = running && tun
        override fun stats() = rawStats
        override fun queryStats() = "outbound>>>proxy>>>traffic>>>uplink,12\noutbound>>>proxy>>>traffic>>>downlink,34"
        override fun version() = "v1.19.32"
    }
    @Test fun unavailableCoreNeverAdvertisesReadinessOrHealth() {
        val n = Native().apply { ready = false }; val r = MihomoRuntime(n)
        assertFalse(r.isReady); assertFalse(r.isCoreRunning()); assertFalse(r.isHealthy())
        try { r.start("{}"); fail("start accepted") } catch (_: IllegalStateException) {}
        r.stopTun(); r.stop(); assertTrue(n.events.isEmpty())
    }
    @Test fun twoPhaseHandoffAndStopDelegateInOrder() {
        val n = Native(); val r = MihomoRuntime(n)
        r.prepareAssets("assets"); r.start("{}"); assertTrue(r.isCoreRunning()); assertFalse(r.isHealthy())
        r.attachTun(42, 1500); assertTrue(r.isHealthy())
        r.prepareAssets("assets"); r.stopTun(); assertTrue(r.isCoreRunning()); assertFalse(r.isHealthy())
        r.stop(); assertFalse(r.isCoreRunning())
        assertEquals(listOf("home", "prepare", "attach:42:1500", "stopTun", "stop"), n.events)
    }
    @Test fun primaryNativeFailurePropagatesUnchanged() {
        val original = IllegalStateException("CORE_START_FAILED: primary; cleanup: secondary")
        val n = Native().apply { failure = original }; val r = MihomoRuntime(n)
        try { r.start("{}"); fail("failure lost") } catch (e: IllegalStateException) { assertSame(original, e) }
        assertFalse(r.isHealthy()); assertFalse(r.isCoreRunning())
    }
    @Test fun nativeValidationPropagatesConfigurationFailureBeforeStart() {
        val original = IllegalArgumentException("CONFIGURATION_OR_SUBSCRIPTION: listeners")
        val n = Native().apply { failure = original }; val r = MihomoRuntime(n)
        try { r.validateConfig("{}"); fail("invalid accepted") } catch (e: IllegalArgumentException) { assertSame(original, e) }
        assertEquals(listOf("validate"), n.events); assertFalse(r.isCoreRunning())
    }
    @Test fun cumulativeStatisticsDoNotResetOrAddOnRead() {
        val r = MihomoRuntime(Native())
        repeat(3) { assertArrayEquals(longArrayOf(12, 34), r.stats()) }
        assertEquals(12L, r.queryStats().outboundUplink["proxy"])
        assertEquals(34L, r.queryStats().outboundDownlink["proxy"])
    }
    @Test fun malformedNativeCountersFailInsteadOfFabricatingZeros() {
        val n = Native().apply { rawStats = """{"upload":-1,"download":2}""" }
        try { MihomoRuntime(n).stats(); fail("negative accepted") } catch (_: IllegalStateException) {}
        n.rawStats = "{}"
        try { MihomoRuntime(n).stats(); fail("missing accepted") } catch (_: NoSuchElementException) {}
    }
}
