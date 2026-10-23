package com.iceflow.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

/**
 * 玻璃态卡片：半透明渐变 + 细描边。
 * 不对内容做 blur（会让文字模糊），真实背景模糊需 RenderEffect，留待后续。
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        colors.surfaceVariant.copy(alpha = 0.55f),
                        colors.surface.copy(alpha = 0.25f),
                    ),
                ),
            )
            .border(BorderStroke(1.dp, colors.primary.copy(alpha = 0.18f)), shape),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}
