package com.iceflow.util

import java.util.Locale

private val UNITS = arrayOf("KB", "MB", "GB", "TB")

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    var value = bytes / 1024.0
    var index = 0
    while (value >= 1024 && index < UNITS.lastIndex) {
        value /= 1024
        index++
    }
    return String.format(Locale.US, "%.1f %s", value, UNITS[index])
}

fun formatSpeed(bytesPerSecond: Long): String = "${formatBytes(bytesPerSecond)}/s"

fun formatDuration(millis: Long): String {
    val s = (millis / 1000).coerceAtLeast(0)
    return String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
}
