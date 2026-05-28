package com.mahi.assistant.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mahi.assistant.ui.theme.*

/**
 * A Card with a neon-glow border effect — the standard container
 * throughout the MAHI UI.
 *
 * @param glowColor   Border glow tint (default NeonCyan)
 * @param animateGlow Whether the glow should pulse
 * @param borderAlpha Base alpha for the border stroke
 */
@Composable
fun GlowCard(
    modifier: Modifier = Modifier,
    glowColor: Color = NeonCyan,
    animateGlow: Boolean = false,
    borderAlpha: Float = 0.4f,
    content: @Composable ColumnScope.() -> Unit,
) {
    val glowAlpha = remember { Animatable(borderAlpha) }

    LaunchedEffect(animateGlow) {
        if (animateGlow) {
            glowAlpha.animateTo(
                targetValue = borderAlpha * 1.6f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 2000, easing = EaseInOutSine),
                    repeatMode = RepeatMode.Reverse
                )
            )
        } else {
            glowAlpha.snapTo(borderAlpha)
        }
    }

    val currentAlpha by glowAlpha.asState()

    Card(
        modifier = modifier.neonGlow(glowColor, currentAlpha, 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = DarkPanel,
            contentColor = TextPrimary,
        ),
        border = BorderStroke(
            width = 1.dp,
            color = glowColor.copy(alpha = currentAlpha.coerceIn(0f, 1f))
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        content = content,
    )
}

/**
 * Modifier that draws a soft glow behind the composable.
 */
private fun Modifier.neonGlow(
    color: Color,
    alpha: Float,
    blurRadius: Dp,
): Modifier = composed {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val blurPx = with(density) { blurRadius.toPx() }

    this.drawBehind {
        drawIntoCanvas { canvas ->
            val paint = Paint().apply {
                this.color = color.copy(alpha = alpha.coerceIn(0f, 1f))
                this.asFrameworkPaint().apply {
                    maskFilter = android.graphics.BlurMaskFilter(
                        blurPx,
                        android.graphics.BlurMaskFilter.Blur.NORMAL
                    )
                }
            }
            val stroke = 2.dp.toPx()
            // Draw glow around the edges
            val rect = android.graphics.RectF(
                stroke, stroke,
                size.width - stroke, size.height - stroke
            )
            canvas.nativeCanvas.drawRoundRect(
                rect, 12.dp.toPx(), 12.dp.toPx(), paint.asFrameworkPaint()
            )
        }
    }
}
