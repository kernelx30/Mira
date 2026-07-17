package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.theme.LocalLiquidGlassBackdrop
import com.ai.assistance.operit.ui.theme.isLiquidGlassSupported
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur

internal enum class MiraChatFadeEdge {
    TOP,
    BOTTOM,
}

@Composable
internal fun MiraChatEdgeFade(
    edge: MiraChatFadeEdge,
    height: Dp,
    hasBackgroundImage: Boolean,
    modifier: Modifier = Modifier,
) {
    val backdrop =
        if (isLiquidGlassSupported()) LocalLiquidGlassBackdrop.current else null
    val base = MaterialTheme.colorScheme.background
    // Keep the timeline legible behind the floating chrome. The blur softens moving text;
    // this tint only fades it instead of turning the edge into an opaque surface.
    val strongAlpha = if (hasBackgroundImage) 0.10f else 0.14f
    val weakAlpha = if (hasBackgroundImage) 0.01f else 0.03f
    val topColors =
        listOf(
            base.copy(alpha = strongAlpha),
            base.copy(alpha = strongAlpha * 0.72f),
            base.copy(alpha = weakAlpha),
            Color.Transparent,
        )
    val gradient =
        Brush.verticalGradient(
            colors = if (edge == MiraChatFadeEdge.TOP) topColors else topColors.reversed(),
        )
    val alphaMask =
        Brush.verticalGradient(
            colors =
                if (edge == MiraChatFadeEdge.TOP) {
                    listOf(
                        Color.Black.copy(alpha = 0.62f),
                        Color.Black.copy(alpha = 0.52f),
                        Color.Black.copy(alpha = 0.22f),
                        Color.Transparent,
                    )
                } else {
                    listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.22f),
                        Color.Black.copy(alpha = 0.52f),
                        Color.Black.copy(alpha = 0.62f),
                    )
                },
        )
    val fadeModifier =
        if (backdrop != null) {
            Modifier.drawBackdrop(
                backdrop = backdrop,
                shape = { RectangleShape },
                effects = { blur(4.dp.toPx()) },
                onDrawSurface = { drawRect(brush = gradient) },
            )
        } else {
            Modifier.background(gradient)
        }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(height)
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    drawContent()
                    drawRect(brush = alphaMask, blendMode = BlendMode.DstIn)
                }
                .then(fadeModifier),
    )
}
