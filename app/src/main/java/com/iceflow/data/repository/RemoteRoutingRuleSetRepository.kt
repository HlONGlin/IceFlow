package com.iceflow.data.repository

import android.content.Context
import com.iceflow.data.model.RemoteRoutingRuleSet
import com.iceflow.data.model.RemoteRoutingRuleSetParser
import com.iceflow.data.model.RoutingRule
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class RemoteRoutingRuleSetRepository(private val context: Context) {
    data class Status(val updatedAt: Long, val count: Int, val error: String?)

    private val directory = File(context.filesDir, "routing-rule-sets").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("routing_rule_set_status", Context.MODE_PRIVATE)

    fun status(id: String): Status = Status(prefs.getLong("updated:${id}", 0), prefs.getInt("count:${id}", 0), prefs.getString("error:${id}", null))

    fun validateEnabledTotal(sets: List<RemoteRoutingRuleSet>) {
        val count = sets.asSequence().filter { it.enabled }.sumOf { status(it.id).count }
        require(count <= MAX_TOTAL_ENTRIES) {
            "启用的远程规则集总计不能超过 50,000 条 / Enabled remote rule sets may contain at most 50,000 entries in total"
        }
    }

    @Synchronized
    fun update(set: RemoteRoutingRuleSet, allSets: List<RemoteRoutingRuleSet> = listOf(set)): Int {
        set.validate()
        val tmp = File(directory, set.id + ".download")
        try {
            val body = download(set.url)
            val text = body.toString(Charsets.UTF_8)
            val rules = RemoteRoutingRuleSetParser.parse(set, text)
            val entryCount = rules.sumOf { it.domains.size + it.ips.size }
            val otherEnabledCount = allSets.filter { it.enabled && it.id != set.id }.sumOf { status(it.id).count }
            check(!set.enabled || otherEnabledCount + entryCount <= MAX_TOTAL_ENTRIES) {
                "启用的远程规则集总计不能超过 50,000 条 / Enabled remote rule sets may contain at most 50,000 entries in total"
            }
            tmp.writeBytes(body)
            val totalAfterReplace = directory.listFiles().orEmpty().filter { it.extension == "txt" && it.name != set.id + ".txt" }.sumOf { it.length() } + tmp.length()
            check(totalAfterReplace <= MAX_TOTAL_CACHE_BYTES) { "规则集缓存总量不能超过 64 MiB / Total rule set cache must not exceed 64 MiB" }
            Files.move(tmp.toPath(), cache(set.id).toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            prefs.edit().putLong("updated:${set.id}", System.currentTimeMillis()).putInt("count:${set.id}", entryCount).remove("error:${set.id}").apply()
            return entryCount
        } catch (error: Exception) {
            prefs.edit().putString("error:${set.id}", error.message?.take(500) ?: "更新失败 / Update failed").apply()
            throw error
        } finally {
            tmp.delete()
        }
    }

    fun loadEnabled(sets: List<RemoteRoutingRuleSet>): List<RoutingRule> = sets.asSequence().filter { it.enabled }
        .flatMap { set ->
            val file = cache(set.id)
            if (!file.isFile) emptySequence() else runCatching {
                RemoteRoutingRuleSetParser.parse(set, file.readText(Charsets.UTF_8)).asSequence()
            }.getOrElse { emptySequence() }
        }.toList().also { rules ->
            require(rules.sumOf { it.domains.size + it.ips.size } <= MAX_TOTAL_ENTRIES) {
                "启用的远程规则集总计不能超过 50,000 条 / Enabled remote rule sets may contain at most 50,000 entries in total"
            }
        }

    fun delete(id: String) {
        cache(id).delete()
        prefs.edit().remove("updated:${id}").remove("count:${id}").remove("error:${id}").apply()
    }

    private fun cache(id: String) = File(directory, id + ".txt")

    private fun download(source: String): ByteArray {
        var url = URL(source)
        repeat(6) { redirect ->
            require(url.protocol.equals("https", true) && !url.host.isNullOrBlank() && URI(url.toString()).userInfo == null) { "规则集下载只允许 HTTPS / Rule set downloads require HTTPS" }
            val connection = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15000
                readTimeout = 30000
            }
            try {
                val code = connection.responseCode
                if (code in 300..399) {
                    check(redirect < 5) { "规则集重定向次数过多 / Too many rule set redirects" }
                    url = URL(url, connection.getHeaderField("Location") ?: error("规则集重定向缺少地址 / Redirect response has no location"))
                    return@repeat
                }
                check(code in 200..299) { "规则集下载失败 / Rule set download failed: HTTP $code" }
                return connection.inputStream.use { input ->
                    val out = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(16384)
                    var size = 0
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        size += read
                        check(size <= RemoteRoutingRuleSetParser.MAX_BYTES) { "规则集内容不能超过 4 MiB / Rule set content must not exceed 4 MiB" }
                        out.write(buffer, 0, read)
                    }
                    out.toByteArray()
                }
            } finally {
                connection.disconnect()
            }
        }
        error("规则集重定向次数过多 / Too many rule set redirects")
    }

    private companion object {
        const val MAX_TOTAL_CACHE_BYTES = 64L * 1024 * 1024
        const val MAX_TOTAL_ENTRIES = 50_000
    }
}
