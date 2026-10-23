package com.iceflow.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.iceflow.data.model.AppTheme
import androidx.compose.ui.platform.LocalContext

private val DarkColors = darkColorScheme(
    primary = IceBlue,
    onPrimary = DeepBlue,
    secondary = IceCyan,
    onSecondary = DeepBlue,
    background = Midnight,
    onBackground = FrostWhite,
    surface = Midnight,
    onSurface = FrostWhite,
    surfaceVariant = DeepBlue,
    onSurfaceVariant = GlacierGray,
)

private val LightColors = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF007AFF),
    onPrimary = FrostWhite,
    secondary = androidx.compose.ui.graphics.Color(0xFF34C759),
    background = FrostWhite,
    onBackground = androidx.compose.ui.graphics.Color(0xFF000000),
    surface = FrostWhite,
    onSurface = androidx.compose.ui.graphics.Color(0xFF000000),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFF2F2F7),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF3C3C43),
    surfaceTint = IceBlue,
    surfaceContainerHigh = FrostWhite,
    surfaceContainer = androidx.compose.ui.graphics.Color(0xFFF2F6FC),
)

/**
 * 默认使用冰蓝品牌色；dynamicColor=true 时在 Android 12+ 使用 Material You 动态取色。
 */
@Composable
fun IceFlowTheme(
    theme: AppTheme = AppTheme.ICE,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val themedLight = when (theme) {
        AppTheme.LAVENDER -> LightColors.copy(primary = Color(0xFF7C6FF2), secondary = Color(0xFFE9E5FF), surfaceTint = Color(0xFF7C6FF2))
        AppTheme.MINT -> LightColors.copy(primary = Color(0xFF159A80), secondary = Color(0xFFDDF7EF), surfaceTint = Color(0xFF159A80))
        AppTheme.SUNSET -> LightColors.copy(primary = Color(0xFFE56B35), secondary = Color(0xFFFFE5D8), surfaceTint = Color(0xFFE56B35))
        AppTheme.DARK -> LightColors.copy(primary = Color(0xFF6BA8FF), secondary = Color(0xFF24344F), surfaceTint = Color(0xFF6BA8FF))
        AppTheme.ICE -> LightColors
    }
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkColors
        else -> themedLight
    }
    MaterialTheme(colorScheme = colorScheme, typography = IceFlowTypography, content = content)
}
