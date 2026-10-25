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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.iceflow.data.model.AppTheme
import com.iceflow.data.model.NightMode
import com.iceflow.data.model.TextSize
import kotlinx.coroutines.delay
import java.time.LocalTime

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
private fun themedColorScheme(accent: Color, secondary: Color, dark: Boolean): ColorScheme = if (dark) darkColorScheme(
    background = Color.Black,
    surface = Color(0xFF111111),
    surfaceVariant = Color(0xFF1F1F1F),
    primary = accent,
    primaryContainer = when (accent) {
        LightBlueColors.primary -> LightBlueColors.primarySoft
        MintGreenColors.primary -> Color(0xFF123C31)
        SakuraPinkColors.primary -> Color(0xFF472333)
        PeachOrangeColors.primary -> Color(0xFF4A2D1A)
        else -> ObsidianBlackColors.primarySoft
    },
    secondary = secondary,
    onPrimary = Color.White,
    onPrimaryContainer = Color.White,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFB3B3B3),
    outline = Color(0xFF707070),
    outlineVariant = Color(0xFF404040),
    surfaceTint = accent,
) else lightColorScheme(
    background = Color.White,
    surface = Color.White,
    surfaceVariant = Color(0xFFF5F5F7),
    primary = accent,
    primaryContainer = when (accent) {
        LightBlueColors.primary -> LightBlueColors.primarySoft
        MintGreenColors.primary -> MintGreenColors.primarySoft
        SakuraPinkColors.primary -> SakuraPinkColors.primarySoft
        PeachOrangeColors.primary -> PeachOrangeColors.primarySoft
        else -> Color(0xFFE5F3FF)
    },
    secondary = secondary,
    onPrimary = Color.White,
    onPrimaryContainer = accent,
    onSecondary = Color.White,
    onBackground = Color(0xFF1C1C1E),
    onSurface = Color(0xFF1C1C1E),
    onSurfaceVariant = Color(0xFF6B6B6F),
    outline = Color(0xFFD1D1D6),
    outlineVariant = Color(0xFFD1D1D6),
    surfaceTint = accent,
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
private fun mintGreenExtColors() = IceFlowColors(
    success = MintGreenColors.success,
    warning = MintGreenColors.warning,
    error = MintGreenColors.error,
    info = MintGreenColors.info,
)

/**
 * 樱花粉主题配色方案
 */
private fun sakuraPinkExtColors() = IceFlowColors(
    success = SakuraPinkColors.success,
    warning = SakuraPinkColors.warning,
    error = SakuraPinkColors.error,
    info = SakuraPinkColors.info,
)

/**
 * 蜜桃橙主题配色方案
 */
private fun peachOrangeExtColors() = IceFlowColors(
    success = PeachOrangeColors.success,
    warning = PeachOrangeColors.warning,
    error = PeachOrangeColors.error,
    info = PeachOrangeColors.info,
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
    nightMode: NightMode = NightMode.FOLLOW_SYSTEM,
    textSize: TextSize = TextSize.MEDIUM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val autoDark by produceState(initialValue = isAutoNightTime(), nightMode) {
        if (nightMode == NightMode.AUTO) {
            while (true) {
                value = isAutoNightTime()
                delay(60_000L)
            }
        }
    }
    val isDark = when (nightMode) {
        NightMode.FOLLOW_SYSTEM -> systemDark
        NightMode.ENABLED -> true
        NightMode.DISABLED -> false
        NightMode.AUTO -> autoDark
    }
    val accent = when (theme) {
        AppTheme.LIGHT_BLUE -> LightBlueColors.primary
        AppTheme.MINT_GREEN -> MintGreenColors.primary
        AppTheme.SAKURA_PINK -> SakuraPinkColors.primary
        AppTheme.PEACH_ORANGE -> PeachOrangeColors.primary
        AppTheme.OBSIDIAN_BLACK -> ObsidianBlackColors.primary
    }
    val accentSecondary = when (theme) {
        AppTheme.LIGHT_BLUE -> LightBlueColors.secondary
        AppTheme.MINT_GREEN -> MintGreenColors.secondary
        AppTheme.SAKURA_PINK -> SakuraPinkColors.secondary
        AppTheme.PEACH_ORANGE -> PeachOrangeColors.secondary
        AppTheme.OBSIDIAN_BLACK -> ObsidianBlackColors.secondary
    }
    val extColors = when (theme) {
        AppTheme.LIGHT_BLUE -> lightBlueExtColors()
        AppTheme.MINT_GREEN -> mintGreenExtColors()
        AppTheme.SAKURA_PINK -> sakuraPinkExtColors()
        AppTheme.PEACH_ORANGE -> peachOrangeExtColors()
        AppTheme.OBSIDIAN_BLACK -> obsidianBlackExtColors()
    }
    // 选择对应的主题配色方案
    val colorScheme = when {
        // Material You 动态取色（Android 12+）
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (isDark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        else -> themedColorScheme(accent, accentSecondary, isDark)
    }

    val density = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.runtime.CompositionLocalProvider(
        LocalIceFlowColors provides extColors,
        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(
            density = density.density,
            fontScale = density.fontScale * textSize.scale,
        ),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = IceFlowTypography,
            content = content
        )
    }
}

private fun isAutoNightTime(): Boolean {
    val hour = LocalTime.now().hour
    return hour >= 22 || hour < 6
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
