package com.iceflow.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.iceflow.ui.theme.IceFlowTheme
import com.iceflow.i18n.T

/** 延迟标签文案。null 表示尚未测速，负数表示超时。 */
@Composable
fun latencyText(ms: Int?): String = when {
    ms == null -> T("common.noValue")
    ms < 0 -> T("latency.timeout")
    else -> "${ms}ms"
}

/** 延迟颜色：读取当前主题的语义色，切换主题后随之变化。 */
@Composable
fun latencyColor(ms: Int?, fallback: Color): Color = when {
    ms == null -> fallback
    ms < 0 -> IceFlowTheme.extColors.error
    ms <= 50 -> IceFlowTheme.extColors.success
    ms <= 150 -> IceFlowTheme.extColors.warning
    else -> IceFlowTheme.extColors.error
}
