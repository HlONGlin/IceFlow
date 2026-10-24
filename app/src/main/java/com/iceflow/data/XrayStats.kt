package com.iceflow.data

/**
 * xray-core 统计数据，按出站/入站分组。
 *
 * 原始格式：每行 "outbound>>>tag,value" 或 "inbound>>>tag,value"，
 * 其中 tag 可以是 "proxy" / "direct" / "block" 等。
 */
data class XrayStats(
    val outboundUplink: Map<String, Long> = emptyMap(),
    val outboundDownlink: Map<String, Long> = emptyMap(),
    val inboundUplink: Map<String, Long> = emptyMap(),
    val inboundDownlink: Map<String, Long> = emptyMap(),
) {
    companion object {
        /**
         * 解析 xray queryStats 返回的文本。
         * 格式：每行 "outbound>>>tag>>>traffic>>>uplink,12345"
         */
        fun parse(raw: String): XrayStats {
            val outUp = mutableMapOf<String, Long>()
            val outDown = mutableMapOf<String, Long>()
            val inUp = mutableMapOf<String, Long>()
            val inDown = mutableMapOf<String, Long>()

            raw.lineSequence().forEach { line ->
                val parts = line.split(',')
                if (parts.size != 2) return@forEach
                val name = parts[0]
                val value = parts[1].toLongOrNull() ?: return@forEach

                // name 格式: "outbound>>>proxy>>>traffic>>>uplink"
                val segments = name.split(">>>")
                if (segments.size < 4) return@forEach
                val direction = segments[0]  // "outbound" or "inbound"
                val tag = segments[1]        // "proxy" / "direct" / etc
                val metric = segments[3]     // "uplink" or "downlink"

                when (direction) {
                    "outbound" -> when (metric) {
                        "uplink" -> outUp[tag] = value
                        "downlink" -> outDown[tag] = value
                    }
                    "inbound" -> when (metric) {
                        "uplink" -> inUp[tag] = value
                        "downlink" -> inDown[tag] = value
                    }
                }
            }

            return XrayStats(
                outboundUplink = outUp,
                outboundDownlink = outDown,
                inboundUplink = inUp,
                inboundDownlink = inDown,
            )
        }
    }

    /** 代理出站的总流量（上行 + 下行）*/
    val proxyTotal: Long
        get() = (outboundUplink["proxy"] ?: 0) + (outboundDownlink["proxy"] ?: 0)

    /** 直连出站的总流量 */
    val directTotal: Long
        get() = (outboundUplink["direct"] ?: 0) + (outboundDownlink["direct"] ?: 0)

    /** 所有出站的总上行 */
    val totalUplink: Long
        get() = outboundUplink.values.sum()

    /** 所有出站的总下行 */
    val totalDownlink: Long
        get() = outboundDownlink.values.sum()
}
