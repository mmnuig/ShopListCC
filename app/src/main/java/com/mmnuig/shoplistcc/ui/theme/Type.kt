package com.mmnuig.shoplistcc.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.unit.sp

/** Standard Material typography, optionally scaled up for supermarket use. */
fun appTypography(large: Boolean): Typography {
    val base = Typography()
    if (!large) return base
    return Typography(
        bodyLarge = base.bodyLarge.copy(fontSize = 20.sp, lineHeight = 28.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 17.sp, lineHeight = 24.sp),
        titleLarge = base.titleLarge.copy(fontSize = 26.sp, lineHeight = 32.sp),
        titleMedium = base.titleMedium.copy(fontSize = 19.sp, lineHeight = 26.sp),
        labelLarge = base.labelLarge.copy(fontSize = 17.sp, lineHeight = 24.sp)
    )
}
