package com.iceflow.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val IceFlowTypography = Typography().let { base ->
    base.copy(
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        headlineLarge = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold),
        bodyLarge = TextStyle(fontSize = 17.sp),
        bodyMedium = TextStyle(fontSize = 15.sp),
        labelSmall = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
    )
}
