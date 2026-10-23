package com.iceflow.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.iceflow.service.TrafficPoint

private const val MAX_POINTS = 60

/** 实时流量曲线：下行为主色填充曲线，上行为次色描边曲线。 */
@Composable
fun FlowChart(
    points: List<TrafficPoint>,
    modifier: Modifier = Modifier,
    height: Dp = 140.dp,
) {
    val down = MaterialTheme.colorScheme.primary
    val up = MaterialTheme.colorScheme.secondary
    val grid = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = "最近 60 秒流量曲线" },
    ) {
        for (i in 1..3) {
            val y = size.height * i / 4f
            drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }
        if (points.size < 2) return@Canvas
        val max = points.maxOf { maxOf(it.up, it.down) }.coerceAtLeast(1024).toFloat()

        val downPath = smoothPath(points.map { it.down }, max)
        val fill = Path().apply {
            addPath(downPath)
            lineTo(xAt(points.size - 1), size.height)
            lineTo(xAt(0), size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(down.copy(alpha = 0.35f), Color.Transparent)))
        drawPath(downPath, down, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        drawPath(
            smoothPath(points.map { it.up }, max),
            up,
            style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

private fun DrawScope.xAt(index: Int): Float = size.width * index / (MAX_POINTS - 1).toFloat()

private fun DrawScope.smoothPath(values: List<Long>, max: Float): Path {
    // 右对齐：最新点在最右侧
    val offset = MAX_POINTS - values.size
    val pts = values.mapIndexed { i, v ->
        Offset(xAt(i + offset), size.height - (v / max) * size.height * 0.9f)
    }
    return Path().apply {
        moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) {
            val p0 = pts[i - 1]
            val p1 = pts[i]
            val cx = (p0.x + p1.x) / 2
            cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
        }
    }
}
