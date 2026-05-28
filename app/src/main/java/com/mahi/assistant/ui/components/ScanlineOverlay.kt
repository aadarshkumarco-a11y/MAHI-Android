package com.mahi.assistant.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mahi.assistant.ui.theme.ScanlineColor

/**
 * Full-screen scanline overlay for the holographic JARVIS feel.
 *
 * Draws faint horizontal lines across the entire screen with
 * a slow vertical drift animation.
 */
@Composable
fun ScanlineOverlay(
    modifier: Modifier = Modifier,
    lineSpacing: Float = 4f,      // dp between scanlines
    lineAlpha: Float = 0.04f,      // base alpha per line
    driftEnabled: Boolean = true,  // slow vertical movement
) {
    val driftOffset = remember { Animatable(0f) }

    LaunchedEffect(driftEnabled) {
        if (driftEnabled) {
            driftOffset.animateTo(
                targetValue = lineSpacing,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 3000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
        }
    }

    val density = androidx.compose.ui.platform.LocalDensity.current
    val spacingPx = with(density) { lineSpacing.dp.toPx() }

    Canvas(modifier = modifier.fillMaxSize()) {
        val offsetY = driftOffset.value
        var y = -spacingPx + offsetY

        while (y < size.height) {
            drawLine(
                color = Color.White.copy(alpha = lineAlpha),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f,
            )
            y += spacingPx
        }
    }
}
