package com.iceflow.data

import com.iceflow.data.model.SubscriptionUrl
import java.net.URLDecoder

/**
 * 外部深链接与分享导入的解析结果。
 *
 * 只做「把 Intent 数据归一成待确认的导入请求」，不依赖任何 Android 类：
 * 便于 JVM 单测，也保证在用户确认前不会产生写入或连接等副作用。
 */
sealed interface DeepLinkRequest {
    /** 订阅链接（仅 HTTPS），[name] 来自链接中的 name/remark 参数。 */
    data class AddSubscription(val url: String, val name: String? = null) : DeepLinkRequest

    /** 单节点分享链接或整份配置文本，交给现有文本导入路径解析。 */
    data class ImportText(val text: String, val sourceName: String? = null) : DeepLinkRequest

    /** 外部应用用 content:// 打开的配置文件，由调用方按上限读取后再导入。 */
    data class ImportFile(val uri: String, val displayName: String? = null) : DeepLinkRequest

    /** 无法导入的链接，[reason] 决定界面提示文案。 */
    data class Invalid(val reason: DeepLinkError) : DeepLinkRequest
}

/** 深链接导入失败的原因，每个原因对应一条本地化文案。 */
enum class DeepLinkError(val stringKey: String) {
    EMPTY("deeplink.error.empty"),
    UNSUPPORTED("deeplink.error.unsupported"),
    INSECURE_URL("deeplink.error.insecureUrl"),
    MALFORMED("deeplink.error.malformed"),
    TOO_LONG("deeplink.error.tooLong"),
    UNSUPPORTED_FILE("deeplink.error.unsupportedFile"),
    CORE_UNSUPPORTED("deeplink.error.coreUnsupported"),
    READ_FAILED("deeplink.error.readFailed"),
}

/**
 * 把外部 Intent 的三要素（action、data、文本）归一成导入请求。
 *
 * 支持的入口：
 * - clash://install-config?url=…、iceflow://install-config|import-config?url=…&name=…
 * - v2rayn://install-sub?url=…、sing-box://import-remote-profile?url=…
 * - 单节点分享链接（vmess/vless/trojan/ss/ssr/hysteria2/tuic/wireguard/anytls/mieru 等）
 * - ACTION_SEND 分享的文本，以及 content:// 打开的 .yaml/.yml/.json/.txt 配置
 */
object DeepLinkParser {

    /** 订阅地址上限，避免外部链接塞入超长垃圾参数。 */
    const val MAX_URL_LENGTH = 8 * 1024

    /** 单条深链接上限（vmess Base64 链接可能较长）。 */
    const val MAX_LINK_LENGTH = 64 * 1024

    /** 分享文本上限，与 [BoundedTextReader.MAX_IMPORT_BYTES] 保持一致的 4 MiB。 */
    const val MAX_TEXT_LENGTH = 4 * 1024 * 1024

    const val ACTION_VIEW = "android.intent.action.VIEW"
    const val ACTION_SEND = "android.intent.action.SEND"

    /** 订阅导入 scheme：clash / iceflow / v2rayn / sing-box。 */
    val subscriptionSchemes: Set<String> = setOf("clash", "iceflow", "v2rayn", "sing-box")

    private val subscriptionCommands = setOf(
        "install-config", "import-config",
        "install-sub", "import-sub", "install-subscription", "import-subscription",
        "import-remote-profile", "import-profile",
    )

    /**
     * 单节点分享链接 scheme。http/https 与普通网页冲突，故意不作为深链接注册。
     * 与 [ShareLinkParser] / [ExtendedShareLinks] / [MieruShareLinks] 实际支持的能力保持一致。
     */
    val nodeSchemes: Set<String> = setOf(
        "vmess", "vless", "trojan", "ss", "ssr",
        "hysteria", "hy", "hysteria2", "hy2", "tuic",
        "wireguard", "wg", "socks", "socks5", "socks5h", "anytls",
        "mieru", "mierus",
    )

    private val fileExtensions = setOf("yaml", "yml", "json", "txt")

    private val fileMimeTypes = setOf(
        "text/plain", "text/yaml", "text/x-yaml",
        "application/json", "application/x-yaml", "application/yaml",
    )

    /** Intent 中与导入相关的字段，由 Android 层提取后传入。 */
    data class Payload(
        val action: String?,
        val uri: String? = null,
        val mimeType: String? = null,
        val text: String? = null,
        val displayName: String? = null,
    )

    /**
     * @param supportsMieruProtobuf 当前内核能否解码 mieru:// 完整 Protobuf 链接（仅双核变体具备）。
     * 不能解码时明确返回 [DeepLinkError.CORE_UNSUPPORTED]，不静默忽略。
     */
    fun parse(payload: Payload, supportsMieruProtobuf: Boolean = true): DeepLinkRequest {
        val uri = payload.uri?.trim().orEmpty()
        return when {
            payload.action == ACTION_SEND ->
                if (payload.text.isNullOrBlank() && uri.isNotEmpty()) fromUri(uri, payload.mimeType, payload.displayName, supportsMieruProtobuf)
                else fromText(payload.text)
            payload.action == ACTION_VIEW -> fromUri(uri, payload.mimeType, payload.displayName, supportsMieruProtobuf)
            // 少数应用发送 ACTION_MAIN 之外的自定义 action，此时仍按 data 兜底识别。
            uri.isNotEmpty() -> fromUri(uri, payload.mimeType, payload.displayName, supportsMieruProtobuf)
            else -> fromText(payload.text)
        }
    }

    private fun fromText(text: String?): DeepLinkRequest {
        val trimmed = text.orEmpty().trim().removePrefix("\uFEFF")
        if (trimmed.isBlank()) return DeepLinkRequest.Invalid(DeepLinkError.EMPTY)
        if (trimmed.length > MAX_TEXT_LENGTH) return DeepLinkRequest.Invalid(DeepLinkError.TOO_LONG)
        // 分享一条纯订阅地址时直接走订阅导入，其余按分享链接/配置内容处理。
        if ('\n' !in trimmed && trimmed.none(Char::isWhitespace) && isHttpsSubscription(trimmed)) {
            return DeepLinkRequest.AddSubscription(trimmed)
        }
        return DeepLinkRequest.ImportText(trimmed)
    }

    private fun fromUri(uri: String, mimeType: String?, displayName: String?, supportsMieruProtobuf: Boolean): DeepLinkRequest {
        if (uri.isBlank()) return DeepLinkRequest.Invalid(DeepLinkError.EMPTY)
        if (uri.length > MAX_LINK_LENGTH) return DeepLinkRequest.Invalid(DeepLinkError.TOO_LONG)
        val scheme = schemeOf(uri) ?: return DeepLinkRequest.Invalid(DeepLinkError.MALFORMED)
        return when {
            scheme == "content" || scheme == "file" -> fileRequest(uri, mimeType, displayName)
            scheme in subscriptionSchemes -> subscriptionRequest(uri)
            scheme in nodeSchemes -> nodeRequest(uri, scheme, supportsMieruProtobuf)
            else -> DeepLinkRequest.Invalid(DeepLinkError.UNSUPPORTED)
        }
    }

    private fun fileRequest(uri: String, mimeType: String?, displayName: String?): DeepLinkRequest {
        val type = mimeType?.trim()?.lowercase().orEmpty()
        val name = displayName?.trim()?.takeIf { it.isNotEmpty() }
        val extension = (name ?: uri.substringBefore('?')).substringAfterLast('.', "").lowercase()
        // extension 优先：很多文件管理器给 .yaml 报 octet-stream，但文件名仍然可靠。
        if (extension !in fileExtensions && type !in fileMimeTypes) {
            return DeepLinkRequest.Invalid(DeepLinkError.UNSUPPORTED_FILE)
        }
        return DeepLinkRequest.ImportFile(uri, name)
    }

    private fun subscriptionRequest(link: String): DeepLinkRequest {
        val body = link.substringAfter("://").substringBefore('#')
        val command = body.substringBefore('?').trim('/').lowercase()
        if (command !in subscriptionCommands) return DeepLinkRequest.Invalid(DeepLinkError.UNSUPPORTED)
        val query = parseQuery(if ('?' in body) body.substringAfter('?') else "")
        val raw = query["url"]?.takeIf { it.isNotBlank() } ?: return DeepLinkRequest.Invalid(DeepLinkError.MALFORMED)
        val url = decodeSubscriptionUrl(raw)
        if (url.length > MAX_URL_LENGTH) return DeepLinkRequest.Invalid(DeepLinkError.TOO_LONG)
        if (!isHttpsSubscription(url)) return DeepLinkRequest.Invalid(DeepLinkError.INSECURE_URL)
        val name = (query["name"] ?: query["remark"] ?: query["title"])
            ?.trim()?.take(64)?.takeIf { it.isNotEmpty() }
        return DeepLinkRequest.AddSubscription(url, name)
    }

    private fun nodeRequest(link: String, scheme: String, supportsMieruProtobuf: Boolean): DeepLinkRequest {
        // mieru:// 完整链接依赖 xray 侧解码，单核变体必须明确提示而不是静默失败。
        if (scheme == "mieru" && !supportsMieruProtobuf) return DeepLinkRequest.Invalid(DeepLinkError.CORE_UNSUPPORTED)
        val nodes = runCatching { ShareLinkParser.parseMany(link) }.getOrDefault(emptyList())
        if (nodes.isEmpty()) return DeepLinkRequest.Invalid(DeepLinkError.MALFORMED)
        return DeepLinkRequest.ImportText(link)
    }

    private fun schemeOf(uri: String): String? {
        if ("://" !in uri) return null
        val scheme = uri.substringBefore("://").lowercase()
        return scheme.takeIf { it.isNotEmpty() && it.all { c -> c.isLetterOrDigit() || c == '+' || c == '-' || c == '.' } }
    }

    /** 与订阅导入一致的 https 校验，拒绝 http、缺主机或带 userInfo 的地址。 */
    private fun isHttpsSubscription(value: String): Boolean =
        value.startsWith("https://", ignoreCase = true) &&
            runCatching { SubscriptionUrl.requireHttpsUri(value) }.isSuccess

    /** 订阅地址可能被百分号编码（clash://install-config?url=https%3A%2F%2F…）。 */
    private fun decodeSubscriptionUrl(value: String): String =
        if ("://" in value) value
        else runCatching { URLDecoder.decode(value.replace("+", "%2B"), "UTF-8") }.getOrDefault(value)

    private fun parseQuery(query: String): Map<String, String> =
        query.split('&')
            .filter { it.isNotEmpty() }
            .associate { decode(it.substringBefore('=')).lowercase() to decode(it.substringAfter('=', "")) }

    private fun decode(value: String): String = try {
        // 链接中的 '+' 通常是字面量，不当作空格
        URLDecoder.decode(value.replace("+", "%2B"), "UTF-8")
    } catch (_: IllegalArgumentException) {
        value
    }
}
