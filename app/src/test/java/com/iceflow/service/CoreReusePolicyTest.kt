package com.iceflow.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreReusePolicyTest {
    @Test
    fun `reuse only when core running with same node and identical config`() {
        val cfg = "config-json"
        assertTrue(CoreReusePolicy.shouldReuse(coreRunning = true, hasTunnel = true, appliedConfig = cfg, newConfig = cfg))
    }

    @Test
    fun `config change forces core restart`() {
        // 公共 SOCKS 端口/认证等设置变化后不得沿用旧配置
        assertFalse(CoreReusePolicy.shouldReuse(coreRunning = true, hasTunnel = true, appliedConfig = "old", newConfig = "new"))
    }

    @Test
    fun `missing applied config never reuses`() {
        assertFalse(CoreReusePolicy.shouldReuse(coreRunning = true, hasTunnel = true, appliedConfig = null, newConfig = "cfg"))
    }

    @Test
    fun `stopped core or missing tunnel never reuses`() {
        assertFalse(CoreReusePolicy.shouldReuse(coreRunning = false, hasTunnel = true, appliedConfig = "cfg", newConfig = "cfg"))
        assertFalse(CoreReusePolicy.shouldReuse(coreRunning = true, hasTunnel = false, appliedConfig = "cfg", newConfig = "cfg"))
    }
}
