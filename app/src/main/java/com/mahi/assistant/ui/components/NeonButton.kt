package com.mahi.assistant.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.mahi.assistant.ui.theme.*

/**
 * A button with a neon glow border — perfect for the JARVIS UI.
 *
 * @param text       Button label
 * @param onClick    Click callback
 * @param glowColor  Neon color for the glow (default NeonCyan)
 * @param enabled    Whether the button is active
 */
@Composable
fun NeonButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glowColor: Color = NeonCyan,
    enabled: Boolean = true,
    textStyle: TextStyle = MaterialTheme.typography.labelLarge,
) {
    val glowAlpha = remember { Animatable(0.3f) }

    // Pulse glow when enabled
    LaunchedEffect(enabled) {
        if (enabled) {
            glowAlpha.animateTo(
                targetValue = 0.7f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1800, easing = EaseInOutSine),
                    repeatMode = RepeatMode.Reverse
                )
            )
        } else {
            glowAlpha.snapTo(0.15f)
        }
    }

    val currentAlpha by glowAlpha.asState()

    OutlinedButton(
        onClick = onClick,
        modifier = modifier.drawBehind {
            val blurPx = 6.dp.toPx()
            drawIntoCanvas { canvas ->
                val paint = Paint().apply {
                    color = glowColor.copy(alpha = currentAlpha.coerceIn(0f, 1f))
                    asFrameworkPaint().maskFilter = android.graphics.BlurMaskFilter(
                        blurPx,
                        android.graphics.BlurMaskFilter.Blur.NORMAL
                    )
                }
                canvas.nativeCanvas.drawRoundRect(
                    android.graphics.RectF(0f, 0f, size.width, size.height),
                    8.dp.toPx(), 8.dp.toPx(),
                    paint.asFrameworkPaint()
                )
            }
        },
        enabled = enabled,
        border = BorderStroke(
            width = 1.5.dp,
            color = glowColor.copy(alpha = if (enabled) 0.8f else 0.2f)
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = glowColor.copy(alpha = 0.08f),
            contentColor = glowColor,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = TextTertiary,
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Text(
            text = text,
            style = textStyle,
            color = if (enabled) glowColor else TextTertiary,
        )
    }
}
