package com.iceflow.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// ==================== 浅蓝主题（默认）====================
object LightBlueColors {
    val background = Color(0xFFFFFFFF)
    val surface = Color(0xFFFFFFFF)
    val surface2 = Color(0xFFFFFFFF)
    val primary = Color(0xFF007AFF)
    val primarySoft = Color(0xFFE5F3FF)
    val secondary = Color(0xFF5856D6)
    val secondarySoft = Color(0xFFEDEDFF)
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
    val secondary = Color(0xFF007AFF)
    val secondarySoft = Color(0xFFE5F3FF)
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
    val secondary = Color(0xFFAF52DE)
    val secondarySoft = Color(0xFFF6EBFF)
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
    val secondary = Color(0xFF5E5CE6)
    val secondarySoft = Color(0xFFEDEDFF)
    val textPrimary = Color(0xFF1F1C1A)
    val textSecondary = Color(0xFF6B645F)
    val textTertiary = Color(0xFFA59E9A)
    val border = Color(0xFFE5DDD1)
    val success = Color(0xFF30C268)
    val warning = Color(0xFFFF9F5A)
    val error = Color(0xFFFF4444)
    val info = Color(0xFF4A9AFF)
}

// ==================== 曜石黑主题（黑白）====================
object ObsidianBlackColors {
    val background = Color(0xFF000000)
    val surface = Color(0xFF111111)
    val surface2 = Color(0xFF1F1F1F)
    val primary = Color(0xFFA6A6A6)
    val primarySoft = Color(0xFF2A2A2A)
    val secondary = Color(0xFF8E8E93)
    val secondarySoft = Color(0xFF26262A)
    val textPrimary = Color(0xFFFFFFFF)
    val textSecondary = Color(0xFFB3B3B3)
    val textTertiary = Color(0xFF808080)
    val border = Color(0xFF404040)
    val success = Color(0xFF32D74B)
    val warning = Color(0xFFFF9F0A)
    val error = Color(0xFFFF453A)
    val info = Color(0xFF9E9E9E)
}

/**
 * 卡片圆角令牌。全局统一，避免各页面各写一个数值。
 */
object IceRadius {
    /** 列表项、行卡片 */
    val row = 14.dp
    /** 独立卡片、分组卡片 */
    val card = 16.dp
    /** 强调面板、浮层 */
    val panel = 20.dp
    /** 小标签、胶囊 */
    val chip = 6.dp
}

/**
 * 主题预览色。设置页与主题卡片共用，避免两处各写一份常量。
 */
fun themePreviewColors(theme: com.iceflow.data.model.AppTheme): Pair<Color, Color> = when (theme) {
    com.iceflow.data.model.AppTheme.LIGHT_BLUE -> LightBlueColors.primary to LightBlueColors.primarySoft
    com.iceflow.data.model.AppTheme.MINT_GREEN -> MintGreenColors.primary to MintGreenColors.primarySoft
    com.iceflow.data.model.AppTheme.SAKURA_PINK -> SakuraPinkColors.primary to SakuraPinkColors.primarySoft
    com.iceflow.data.model.AppTheme.PEACH_ORANGE -> PeachOrangeColors.primary to PeachOrangeColors.primarySoft
    com.iceflow.data.model.AppTheme.OBSIDIAN_BLACK -> ObsidianBlackColors.primary to ObsidianBlackColors.primarySoft
}

// 延迟颜色请使用 com.iceflow.ui.component.latencyColor，它读取主题语义色
