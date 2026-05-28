package com.mahi.assistant.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mahi.assistant.ui.theme.NeonCyan
import com.mahi.assistant.ui.theme.NeonGreen

/**
 * Animated audio waveform bars — used when the assistant
 * is listening or speaking.
 *
 * @param barCount Number of bars (default 5)
 * @param barWidth Width of each bar
 * @param maxHeight Maximum height of bars
 * @param color Color of the bars
 * @param isAnimating Whether the bars should animate
 */
@Composable
fun VoiceWaveform(
    modifier: Modifier = Modifier,
    barCount: Int = 5,
    barWidth: Dp = 4.dp,
    maxHeight: Dp = 32.dp,
    color: Color = NeonCyan,
    isAnimating: Boolean = true,
) {
    val barHeights = remember { List(barCount) { Animatable(0.3f) } }

    // Different speed/delay per bar for organic feel
    val durations = remember { List(barCount) { (300 + it * 80) } }

    LaunchedEffect(isAnimating) {
        if (isAnimating) {
            barHeights.forEachIndexed { index, animatable ->
                animatable.animateTo(
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(
                            durationMillis = durations[index],
                            delayMillis = index * 60,
                            easing = EaseInOutSine
                        ),
                        repeatMode = RepeatMode.Reverse
                    )
                )
            }
        } else {
            barHeights.forEachIndexed { _, animatable ->
                animatable.snapTo(0.15f)
            }
        }
    }

    val density = androidx.compose.ui.platform.LocalDensity.current
    val barWidthPx = with(density) { barWidth.toPx() }
    val maxHeightPx = with(density) { maxHeight.toPx() }
    val gapPx = with(density) { 3.dp.toPx() }

    Canvas(modifier = modifier.height(maxHeight)) {
        val totalWidth = barCount * barWidthPx + (barCount - 1) * gapPx
        var startX = (size.width - totalWidth) / 2f

        for (i in 0 until barCount) {
            val barHeight = maxHeightPx * barHeights[i].value
            val yOffset = (size.height - barHeight) / 2f

            drawRoundRect(
                color = color,
                topLeft = Offset(startX, yOffset),
                size = Size(barWidthPx, barHeight),
                cornerRadius = CornerRadius(barWidthPx / 2f),
            )
            startX += barWidthPx + gapPx
        }
    }
}
