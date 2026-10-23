package com.iceflow.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.iceflow.data.model.AppTheme

/**
 * IceFlow 扩展语义色令牌
 * 用于补充 Material3 ColorScheme 中未包含的语义色
 */
@Immutable
data class IceFlowColors(
    val success: Color,
    val warning: Color,
    val error: Color,
    val info: Color,
)

/**
 * 提供全局访问扩展语义色的 CompositionLocal
 */
val LocalIceFlowColors = staticCompositionLocalOf {
    IceFlowColors(
        success = Color.Unspecified,
        warning = Color.Unspecified,
        error = Color.Unspecified,
        info = Color.Unspecified,
    )
}

/**
 * 浅蓝主题配色方案（默认）
 */
private fun lightBlueColorScheme() = lightColorScheme(
    background = LightBlueColors.background,
    surface = LightBlueColors.surface,
    surfaceVariant = LightBlueColors.surface2,
    primary = LightBlueColors.primary,
    primaryContainer = LightBlueColors.primarySoft,
    onPrimary = Color.White,
    onPrimaryContainer = LightBlueColors.primary,
    onBackground = LightBlueColors.textPrimary,
    onSurface = LightBlueColors.textPrimary,
    onSurfaceVariant = LightBlueColors.textSecondary,
    outline = LightBlueColors.border,
    outlineVariant = LightBlueColors.border.copy(alpha = 0.5f),
    surfaceTint = LightBlueColors.primary,
)

private fun lightBlueExtColors() = IceFlowColors(
    success = LightBlueColors.success,
    warning = LightBlueColors.warning,
    error = LightBlueColors.error,
    info = LightBlueColors.info,
)

/**
 * 薄荷绿主题配色方案
 */
private fun mintGreenColorScheme() = lightColorScheme(
    background = MintGreenColors.background,
    surface = MintGreenColors.surface,
    surfaceVariant = MintGreenColors.surface2,
    primary = MintGreenColors.primary,
    primaryContainer = MintGreenColors.primarySoft,
    onPrimary = Color.White,
    onPrimaryContainer = MintGreenColors.primary,
    onBackground = MintGreenColors.textPrimary,
    onSurface = MintGreenColors.textPrimary,
    onSurfaceVariant = MintGreenColors.textSecondary,
    outline = MintGreenColors.border,
    outlineVariant = MintGreenColors.border.copy(alpha = 0.5f),
    surfaceTint = MintGreenColors.primary,
)

private fun mintGreenExtColors() = IceFlowColors(
    success = MintGreenColors.success,
    warning = MintGreenColors.warning,
    error = MintGreenColors.error,
    info = MintGreenColors.info,
)

/**
 * 樱花粉主题配色方案
 */
private fun sakuraPinkColorScheme() = lightColorScheme(
    background = SakuraPinkColors.background,
    surface = SakuraPinkColors.surface,
    surfaceVariant = SakuraPinkColors.surface2,
    primary = SakuraPinkColors.primary,
    primaryContainer = SakuraPinkColors.primarySoft,
    onPrimary = Color.White,
    onPrimaryContainer = SakuraPinkColors.primary,
    onBackground = SakuraPinkColors.textPrimary,
    onSurface = SakuraPinkColors.textPrimary,
    onSurfaceVariant = SakuraPinkColors.textSecondary,
    outline = SakuraPinkColors.border,
    outlineVariant = SakuraPinkColors.border.copy(alpha = 0.5f),
    surfaceTint = SakuraPinkColors.primary,
)

private fun sakuraPinkExtColors() = IceFlowColors(
    success = SakuraPinkColors.success,
    warning = SakuraPinkColors.warning,
    error = SakuraPinkColors.error,
    info = SakuraPinkColors.info,
)

/**
 * 蜜桃橙主题配色方案
 */
private fun peachOrangeColorScheme() = lightColorScheme(
    background = PeachOrangeColors.background,
    surface = PeachOrangeColors.surface,
    surfaceVariant = PeachOrangeColors.surface2,
    primary = PeachOrangeColors.primary,
    primaryContainer = PeachOrangeColors.primarySoft,
    onPrimary = Color.White,
    onPrimaryContainer = PeachOrangeColors.primary,
    onBackground = PeachOrangeColors.textPrimary,
    onSurface = PeachOrangeColors.textPrimary,
    onSurfaceVariant = PeachOrangeColors.textSecondary,
    outline = PeachOrangeColors.border,
    outlineVariant = PeachOrangeColors.border.copy(alpha = 0.5f),
    surfaceTint = PeachOrangeColors.primary,
)

private fun peachOrangeExtColors() = IceFlowColors(
    success = PeachOrangeColors.success,
    warning = PeachOrangeColors.warning,
    error = PeachOrangeColors.error,
    info = PeachOrangeColors.info,
)

/**
 * 曜石黑主题配色方案（深色）
 */
private fun obsidianBlackColorScheme() = darkColorScheme(
    background = ObsidianBlackColors.background,
    surface = ObsidianBlackColors.surface,
    surfaceVariant = ObsidianBlackColors.surface2,
    primary = ObsidianBlackColors.primary,
    primaryContainer = ObsidianBlackColors.primarySoft,
    onPrimary = Color.White,
    onPrimaryContainer = ObsidianBlackColors.primary,
    onBackground = ObsidianBlackColors.textPrimary,
    onSurface = ObsidianBlackColors.textPrimary,
    onSurfaceVariant = ObsidianBlackColors.textSecondary,
    outline = ObsidianBlackColors.border,
    outlineVariant = ObsidianBlackColors.border.copy(alpha = 0.5f),
    surfaceTint = ObsidianBlackColors.primary,
)

private fun obsidianBlackExtColors() = IceFlowColors(
    success = ObsidianBlackColors.success,
    warning = ObsidianBlackColors.warning,
    error = ObsidianBlackColors.error,
    info = ObsidianBlackColors.info,
)

/**
 * IceFlow 主题组件
 * 
 * @param theme 主题类型，默认为浅蓝
 * @param dynamicColor 是否启用 Material You 动态取色（仅 Android 12+）
 * @param content 内容组件
 */
@Composable
fun IceFlowTheme(
    theme: AppTheme = AppTheme.LIGHT_BLUE,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    // 选择对应的主题配色方案
    val (colorScheme, extColors) = when {
        // Material You 动态取色（Android 12+）
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            val isDark = theme == AppTheme.OBSIDIAN_BLACK
            val scheme = if (isDark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
            // 动态取色时使用默认浅蓝的扩展色
            scheme to lightBlueExtColors()
        }
        // 根据主题选择配色方案
        else -> when (theme) {
            AppTheme.LIGHT_BLUE -> lightBlueColorScheme() to lightBlueExtColors()
            AppTheme.MINT_GREEN -> mintGreenColorScheme() to mintGreenExtColors()
            AppTheme.SAKURA_PINK -> sakuraPinkColorScheme() to sakuraPinkExtColors()
            AppTheme.PEACH_ORANGE -> peachOrangeColorScheme() to peachOrangeExtColors()
            AppTheme.OBSIDIAN_BLACK -> obsidianBlackColorScheme() to obsidianBlackExtColors()
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalIceFlowColors provides extColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = IceFlowTypography,
            content = content
        )
    }
}

/**
 * 便捷访问扩展语义色的对象
 * 使用方式：IceFlowTheme.extColors.success
 */
object IceFlowTheme {
    val extColors: IceFlowColors
        @Composable
        get() = LocalIceFlowColors.current
}
