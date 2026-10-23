package com.iceflow.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.iceflow.service.VpnState
import com.iceflow.ui.theme.IceFlowMotion

/** 大圆形连接按钮：连接中显示呼吸光环，已连接时为冰蓝渐变。 */
@Composable
fun ConnectButton(
    state: VpnState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
) {
    val haptic = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    val connected = state is VpnState.Connected
    val busy = state == VpnState.Connecting || state == VpnState.Stopping

    val label = when (state) {
        is VpnState.Connected -> "断开连接"
        VpnState.Connecting -> "连接中..."
        VpnState.Stopping -> "断开中..."
        else -> "点击连接"
    }

    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(1200, easing = IceFlowMotion.EaseInOutCubic),
            RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )
    val ringColor by animateColorAsState(
        targetValue = when {
            connected -> colors.primary
            busy -> colors.secondary
            else -> colors.onSurfaceVariant.copy(alpha = 0.4f)
        },
        animationSpec = tween(IceFlowMotion.MEDIUM),
        label = "ringColor",
    )

    Box(modifier.size(size + 40.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val base = this.size.minDimension / 2 - 4.dp.toPx()
            val r = if (busy) base * pulse else base
            drawCircle(ringColor.copy(alpha = 0.18f), radius = r)
            drawCircle(ringColor, radius = r, style = Stroke(width = 3.dp.toPx()))
        }
        Surface(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
            enabled = state != VpnState.Stopping,
            shape = CircleShape,
            color = colors.surfaceVariant,
            modifier = Modifier
                .size(size)
                .testTag("ConnectButton")
                .semantics { stateDescription = if (connected) "已连接" else "未连接" },
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .then(
                        if (connected) {
                            Modifier.drawGradient(colors.primary, colors.secondary)
                        } else {
                            Modifier
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (connected) colors.onPrimary else colors.onSurface,
                )
            }
        }
    }
}

private fun Modifier.drawGradient(a: Color, b: Color): Modifier =
    this.background(Brush.linearGradient(listOf(a, b)))
