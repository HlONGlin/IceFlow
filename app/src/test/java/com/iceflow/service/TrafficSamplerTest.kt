package com.iceflow.service

import org.junit.Assert.assertEquals
import org.junit.Test

class TrafficSamplerTest {
    @Test fun ratesUseActualElapsedTimeAndReturnToZeroWhenIdle() {
        val sampler = TrafficSampler(100, 200, 1000)
        val active = sampler.sample(2100, 8200, 3000)
        assertEquals(1000L, active.upSpeed)
        assertEquals(4000L, active.downSpeed)
        assertEquals(8200L, active.downTotal)
        val idle = sampler.sample(2100, 8200, 4000)
        assertEquals(0L, idle.upSpeed)
        assertEquals(0L, idle.downSpeed)
        assertEquals(2, idle.history.size)
    }

    @Test fun resetsAndInvalidIntervalsNeverProduceNegativeRates() {
        val sampler = TrafficSampler(1000, 2000, 100)
        assertEquals(0L, sampler.sample(0, 0, 100).upSpeed)
        val reset = sampler.sample(0, 0, 1100)
        assertEquals(0L, reset.upSpeed)
        assertEquals(0L, reset.downSpeed)
        repeat(65) { sampler.sample(100L * it, 200L * it, 2100L + it * 1000) }
        assertEquals(60, sampler.sample(7000, 14000, 70000).history.size)
    }
}
