package com.iceflow.service

/** Rates are bytes/second using monotonic timestamps, not wall time or assumed delays. */
internal class TrafficSampler(up: Long, down: Long, nowMs: Long) {
    private var lastUp = up
    private var lastDown = down
    private var lastTime = nowMs
    private val history = ArrayDeque<TrafficPoint>()

    fun sample(up: Long, down: Long, nowMs: Long): TrafficSnapshot {
        val elapsed = nowMs - lastTime
        if (elapsed <= 0) return TrafficSnapshot(up, down, history = history.toList())
        val upSpeed = ((up - lastUp).coerceAtLeast(0).toDouble() * 1000 / elapsed).toLong()
        val downSpeed = ((down - lastDown).coerceAtLeast(0).toDouble() * 1000 / elapsed).toLong()
        lastUp = up
        lastDown = down
        lastTime = nowMs
        history.addLast(TrafficPoint(upSpeed, downSpeed))
        while (history.size > 60) history.removeFirst()
        return TrafficSnapshot(up, down, upSpeed, downSpeed, history.toList())
    }
}
