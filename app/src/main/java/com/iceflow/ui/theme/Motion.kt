package com.iceflow.ui.theme

import androidx.compose.animation.core.CubicBezierEasing

/** 全局动画规范（毫秒）。 */
object IceFlowMotion {
    const val FAST = 200
    const val MEDIUM = 400
    const val SLOW = 600
    val EaseInOutCubic = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
}
