package com.iceflow.ui.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.iceflow.service.VpnState
import com.iceflow.ui.theme.IceFlowMotion

/**
 * 极简风格连接按钮
 * 设计灵感：iOS App Store 下载按钮、Telegram 语音通话按钮
 */
@Composable
fun ConnectButton(
    state: VpnState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 180.dp,
) {
    val haptic = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    val connected = state is VpnState.Connected
    val busy = state == VpnState.Connecting || state == VpnState.Stopping

    val label = when (state) {
        is VpnState.Connected -> "断开"
        VpnState.Connecting -> "连接中"
        VpnState.Stopping -> "断开中"
        else -> "连接"
    }

    // 按下缩放动画
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(200),
        label = "scale"
    )

    // 呼吸动画（仅连接中）
    val breathe by rememberInfiniteTransition(label = "breathe").animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            tween(1500, easing = IceFlowMotion.EaseInOutCubic),
            RepeatMode.Reverse,
        ),
        label = "breatheScale",
    )

    Box(
        modifier = modifier.size(size + 40.dp),
        contentAlignment = Alignment.Center
    ) {
        // 背景模糊光晕（仅连接时）
        if (connected || busy) {
            Box(
                modifier = Modifier
                    .size(size * 0.9f)
                    .scale(if (busy) breathe else 1f)
                    .alpha(if (connected) 0.4f else 0.3f)
                    .blur(40.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                colors.primary.copy(alpha = 0.8f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )
        }

        // 主按钮
        Surface(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
            enabled = state != VpnState.Stopping,
            shape = CircleShape,
            color = Color.Transparent,
            shadowElevation = 0.dp,
            modifier = Modifier
                .size(size)
                .scale(scale * if (busy) breathe else 1f)
                .testTag("ConnectButton")
                .semantics { stateDescription = if (connected) "已连接" else "未连接" },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = if (connected) {
                            // 已连接：鲜艳的渐变
                            Brush.radialGradient(
                                colors = listOf(
                                    colors.primary,
                                    colors.primary.copy(alpha = 0.9f)
                                )
                            )
                        } else if (busy) {
                            // 连接中：柔和的主题色
                            Brush.radialGradient(
                                colors = listOf(
                                    colors.primary.copy(alpha = 0.6f),
                                    colors.primary.copy(alpha = 0.5f)
                                )
                            )
                        } else {
                            // 未连接：简洁的浅色
                            Brush.radialGradient(
                                colors = listOf(
                                    colors.surfaceVariant,
                                    colors.surfaceVariant
                                )
                            )
                        },
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.headlineMedium,
                    color = when {
                        connected -> Color.White
                        busy -> Color.White.copy(alpha = 0.9f)
                        else -> colors.onSurface
                    }
                )
            }
        }
    }
}
