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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    this.drawBehind {
        // Simulate glow with layered semi-transparent rounded rects
        val glowColor = color.copy(alpha = alpha.coerceIn(0f, 1f) * 0.3f)
        drawRoundRect(
            color = glowColor,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx(), 12.dp.toPx()),
            topLeft = androidx.compose.ui.geometry.Offset(-blurRadius.toPx(), -blurRadius.toPx()),
            size = androidx.compose.ui.geometry.Size(size.width + blurRadius.toPx() * 2, size.height + blurRadius.toPx() * 2)
        )
    }
}
