package com.iceflow.service

import android.content.Context
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLog {
    private const val FILE_NAME = "iceflow.log"
    private const val MAX_BYTES = 256 * 1024
    private const val PREVIOUS = "iceflow.previous.log"

    @Synchronized
    fun write(context: Context, level: String, message: String) {
        runCatching {
            val file = File(context.filesDir, FILE_NAME)
            if (file.length() >= MAX_BYTES) {
                val previous = File(context.filesDir, PREVIOUS)
                previous.delete()
                file.renameTo(previous)
            }
            val line = "${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())} [$level] $message\n"
            file.appendText(line)
        }
    }

    @Synchronized
    fun read(context: Context): String = runCatching {
        listOf(PREVIOUS, FILE_NAME).map { File(context.filesDir, it) }.filter { it.exists() }.joinToString("") { file ->
            RandomAccessFile(file, "r").use { reader ->
                val start = (reader.length() - MAX_BYTES).coerceAtLeast(0)
                reader.seek(start)
                val bytes = ByteArray((reader.length() - start).toInt())
                reader.readFully(bytes)
                val text = bytes.toString(Charsets.UTF_8)
                if (start > 0) text.substringAfter('\n', "") else text
            }
        }.ifBlank { "暂无日志" }
    }.getOrDefault("无法读取日志")
}
