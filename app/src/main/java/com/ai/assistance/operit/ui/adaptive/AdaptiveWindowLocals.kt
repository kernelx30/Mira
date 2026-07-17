package com.ai.assistance.operit.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity

val LocalAdaptiveWindowMetrics = staticCompositionLocalOf {
    AdaptiveWindowMetrics(widthDp = 360, heightDp = 640, fontScale = 1f)
}

@Composable
fun rememberAdaptiveWindowMetrics(): AdaptiveWindowMetrics {
    val configuration = LocalConfiguration.current
    val fontScale = LocalDensity.current.fontScale
    return AdaptiveWindowMetrics(
        widthDp = configuration.screenWidthDp,
        heightDp = configuration.screenHeightDp,
        fontScale = fontScale,
    )
}
