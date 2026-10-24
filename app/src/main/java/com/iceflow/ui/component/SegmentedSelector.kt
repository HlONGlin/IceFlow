package com.iceflow.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 分段选择器。用于互斥的多选项，比多个独立按钮更紧凑，也不会在选项变多时撑破一行。
 *
 * @param options 选项文案
 * @param selectedIndex 当前选中项下标
 * @param onSelect 选中项变化回调
 */
@Composable
fun SegmentedSelector(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceVariant)
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(10.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEachIndexed { index, label ->
            SegmentedOption(
                label = label,
                selected = index == selectedIndex,
                enabled = enabled,
                onClick = { onSelect(index) },
            )
        }
    }
}

@Composable
private fun RowScope.SegmentedOption(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) colors.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .heightIn(min = 38.dp)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = when {
                !enabled -> colors.onSurfaceVariant.copy(alpha = 0.5f)
                selected -> colors.onPrimaryContainer
                else -> colors.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
