package com.iceflow.data

/** Exclave format: bypass flag on the first line, then one package name per line. */
object AppRoutingClipboard {
    data class Selection(val proxyMode: Boolean, val packages: List<String>)

    private val packageName = Regex("[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)*")

    fun encode(proxyMode: Boolean, packages: List<String>): String =
        "${!proxyMode}\n${packages.distinct().sorted().joinToString("\n")}"

    fun decode(text: String): Selection {
        val lines = text.removePrefix("\uFEFF").lineSequence().map(String::trim).toList()
        val bypass = lines.first().toBooleanStrictOrNull()
        require(bypass != null) { "Missing bypass flag" }
        val packages = lines.drop(1).filter(String::isNotEmpty).distinct()
        require(packages.all(packageName::matches)) { "Invalid package name" }
        return Selection(proxyMode = !bypass, packages = packages)
    }
}
