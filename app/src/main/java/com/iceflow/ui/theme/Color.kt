package com.iceflow.ui.theme

import androidx.compose.ui.graphics.Color

// ==================== 浅蓝主题（默认）====================
object LightBlueColors {
    val background = Color(0xFFFFFFFF)
    val surface = Color(0xFFFFFFFF)
    val surface2 = Color(0xFFFFFFFF)
    val primary = Color(0xFF007AFF)
    val primarySoft = Color(0xFFE5F3FF)
    val textPrimary = Color(0xFF1C1C1E)
    val textSecondary = Color(0xFF6B6B6F)
    val textTertiary = Color(0xFFA0A0A5)
    val border = Color(0xFFD1D1D6)
    val success = Color(0xFF34C759)
    val warning = Color(0xFFFF9500)
    val error = Color(0xFFFF3B30)
    val info = Color(0xFF007AFF)
}

// ==================== 薄荷绿主题 ====================
object MintGreenColors {
    val background = Color(0xFFFFFFFF)
    val surface = Color(0xFFFFFFFF)
    val surface2 = Color(0xFFFFFFFF)
    val primary = Color(0xFF00C896)
    val primarySoft = Color(0xFFE5F9F1)
    val textPrimary = Color(0xFF1A1F1E)
    val textSecondary = Color(0xFF5F6B68)
    val textTertiary = Color(0xFF9AA59F)
    val border = Color(0xFFD1E5DD)
    val success = Color(0xFF00C896)
    val warning = Color(0xFFFFA03A)
    val error = Color(0xFFFF4757)
    val info = Color(0xFF00A8E8)
}

// ==================== 樱花粉主题 ====================
object SakuraPinkColors {
    val background = Color(0xFFFFFFFF)
    val surface = Color(0xFFFFFFFF)
    val surface2 = Color(0xFFFFFFFF)
    val primary = Color(0xFFFF6B9D)
    val primarySoft = Color(0xFFFFE5EE)
    val textPrimary = Color(0xFF1E1A1C)
    val textSecondary = Color(0xFF6B5F66)
    val textTertiary = Color(0xFFA59AA1)
    val border = Color(0xFFE5D1DC)
    val success = Color(0xFF2DB87D)
    val warning = Color(0xFFFF8F3A)
    val error = Color(0xFFFF385C)
    val info = Color(0xFF5B9AFF)
}

// ==================== 蜜桃橙主题 ====================
object PeachOrangeColors {
    val background = Color(0xFFFFFFFF)
    val surface = Color(0xFFFFFFFF)
    val surface2 = Color(0xFFFFFFFF)
    val primary = Color(0xFFFF9F5A)
    val primarySoft = Color(0xFFFFEDD9)
    val textPrimary = Color(0xFF1F1C1A)
    val textSecondary = Color(0xFF6B645F)
    val textTertiary = Color(0xFFA59E9A)
    val border = Color(0xFFE5DDD1)
    val success = Color(0xFF30C268)
    val warning = Color(0xFFFF9F5A)
    val error = Color(0xFFFF4444)
    val info = Color(0xFF4A9AFF)
}

// ==================== 曜石黑主题（深色）====================
object ObsidianBlackColors {
    val background = Color(0xFF000000)
    val surface = Color(0xFF111111)
    val surface2 = Color(0xFF1F1F1F)
    val primary = Color(0xFF3A9DFF)
    val primarySoft = Color(0xFF1A3A52)
    val textPrimary = Color(0xFFFFFFFF)
    val textSecondary = Color(0xFFB3B3B3)
    val textTertiary = Color(0xFF808080)
    val border = Color(0xFF404040)
    val success = Color(0xFF32D74B)
    val warning = Color(0xFFFF9F0A)
    val error = Color(0xFFFF453A)
    val info = Color(0xFF64D2FF)
}

// ==================== 遗留颜色（用于延迟显示等特殊场景）====================
val LatencyGood = Color(0xFF34C759)
val LatencyMedium = Color(0xFFFF9500)
val LatencyBad = Color(0xFF8E8E93)

// ==================== 向后兼容的颜色别名（逐步迁移中）====================
// 这些颜色应该逐步替换为 MaterialTheme.colorScheme 中的对应颜色
val IceBlue = LightBlueColors.primary
val IceCyan = LightBlueColors.primarySoft
val FrostWhite = Color(0xFFFFFFFF)
val DeepBlue = Color(0xFF000000)
val Midnight = LightBlueColors.surface
val GlacierGray = LightBlueColors.textSecondary
