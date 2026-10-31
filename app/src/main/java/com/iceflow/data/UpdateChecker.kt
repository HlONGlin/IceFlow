package com.iceflow.data

import android.os.Build
import com.iceflow.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** 一条可用的新版本信息。 */
data class UpdateInfo(
    val version: String,
    val tag: String,
    val title: String,
    val notes: String,
    val pageUrl: String,
    val downloadUrl: String?,
)

data class UpdateResult(val info: UpdateInfo? = null, val error: String? = null)

/**
 * 通过 GitHub Releases 检查更新。
 * 仓库为私有时需要只读令牌，否则匿名请求会返回 404。
 */
object UpdateChecker {

    private const val API = "https://api.github.com/repos/HlONGlin/IceFlow/releases/latest"

    suspend fun check(currentVersion: String, token: String): UpdateResult = withContext(Dispatchers.IO) {
        val connection = runCatching { URL(API).openConnection() as HttpURLConnection }.getOrElse {
            return@withContext UpdateResult(error = "无法创建网络连接：${it.message}")
        }
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "IceFlow/$currentVersion")
            if (token.isNotBlank()) connection.setRequestProperty("Authorization", "Bearer ${token.trim()}")
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            when {
                code == 401 -> return@withContext UpdateResult(error = "GitHub 令牌无效或已过期，请重新填写")
                code == 403 -> return@withContext UpdateResult(error = "GitHub 访问受限（可能触发频率限制），请稍后再试")
                code == 404 -> return@withContext UpdateResult(
                    error = if (token.isBlank())
                        "读取不到发布信息：仓库为私有时需要填写 GitHub 只读令牌，或将仓库设为公开"
                    else "读取不到发布信息：请确认令牌具有该仓库的读取权限",
                )
                code !in 200..299 -> return@withContext UpdateResult(error = "检查更新失败（HTTP $code）")
            }
            val json = runCatching { JSONObject(body) }.getOrElse {
                return@withContext UpdateResult(error = "发布信息解析失败")
            }
            val tag = json.optString("tag_name").ifBlank { return@withContext UpdateResult(error = "发布信息缺少版本号") }
            val version = tag.removePrefix("v")
            if (!isNewer(version, currentVersion)) {
                return@withContext UpdateResult(UpdateInfo(version, tag, "", "", json.optString("html_url"), null))
            }
            val assets = json.optJSONArray("assets")
            val apkNames = buildList {
                if (assets != null) for (index in 0 until assets.length()) {
                    val asset = assets.optJSONObject(index) ?: continue
                    add(asset.optString("name") to asset.optString("browser_download_url"))
                }
            }
            val downloadUrl = selectDownloadUrl(apkNames, Build.SUPPORTED_ABIS.toList())
                ?: return@withContext UpdateResult(error = "新版本暂未提供适用于当前${if (BuildConfig.CORE_VARIANT == "single") "单核版" else "双核版"}和设备架构的安装包")
            UpdateResult(
                UpdateInfo(
                    version = version,
                    tag = tag,
                    title = json.optString("name").ifBlank { tag },
                    notes = json.optString("body").trim(),
                    pageUrl = json.optString("html_url"),
                    downloadUrl = downloadUrl,
                )
            )
        } catch (error: Exception) {
            UpdateResult(error = "检查更新失败：${error.message ?: "网络错误"}")
        } finally {
            connection.disconnect()
        }
    }

    /** Keep the installed core variant; use a compatible ABI when its universal APK is absent. */
    internal fun selectDownloadUrl(
        assets: List<Pair<String, String>>,
        supportedAbis: List<String>,
        coreVariant: String = BuildConfig.CORE_VARIANT,
    ): String? {
        if (coreVariant !in setOf("single", "dual")) return null
        val installable = assets.filter { (name, url) ->
            name.endsWith(".apk", ignoreCase = true) &&
                !name.contains("unsigned", ignoreCase = true) && url.isNotBlank()
        }
        fun find(abi: String): String? = installable.firstOrNull { (name, _) ->
            name.endsWith("-$coreVariant-$abi-release.apk", ignoreCase = true)
        }?.second
        return find("universal") ?: supportedAbis.firstNotNullOfOrNull(::find)
    }

    /** 比较版本号：按数字段逐级比较，忽略前缀 v 与预发布后缀。 */
    fun isNewer(latest: String, current: String): Boolean {
        fun parts(value: String) = value.trim().removePrefix("v").substringBefore('-').substringBefore('+')
            .split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val a = parts(latest)
        val b = parts(current)
        for (index in 0 until maxOf(a.size, b.size)) {
            val left = a.getOrElse(index) { 0 }
            val right = b.getOrElse(index) { 0 }
            if (left != right) return left > right
        }
        return false
    }
}
