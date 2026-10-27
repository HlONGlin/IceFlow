package com.iceflow.data

import java.io.ByteArrayOutputStream
import java.io.InputStream

object BoundedTextReader {
    const val MAX_IMPORT_BYTES = 4 * 1024 * 1024

    fun readUtf8(input: InputStream, maxBytes: Int = MAX_IMPORT_BYTES): String {
        require(maxBytes in 0 until Int.MAX_VALUE) { "读取上限无效" }
        val output = ByteArrayOutputStream(minOf(maxBytes + 1, 8192))
        val buffer = ByteArray(8192)
        while (output.size() <= maxBytes) {
            val count = input.read(buffer, 0, minOf(buffer.size, maxBytes + 1 - output.size()))
            if (count < 0) break
            output.write(buffer, 0, count)
        }
        require(output.size() <= maxBytes) { "导入内容超过允许上限（${maxBytes} 字节）" }
        return output.toByteArray().toString(Charsets.UTF_8)
    }
}
