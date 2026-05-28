package com.mahi.assistant.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mahi.assistant.ui.theme.*

/**
 * The central MAHI AI orb — the visual heart of the assistant.
 *
 * States:
 *  - IDLE      → slow, calm pulse (cyan)
 *  - LISTENING → fast pulse with outer ring (cyan)
 *  - THINKING  → rotating ring segments (purple)
 *  - SPEAKING  → wave / breathing effect (green)
 */
enum class OrbState {
    IDLE, LISTENING, THINKING, SPEAKING
}

@Composable
fun MahiOrb(
    state: OrbState = OrbState.IDLE,
    size: Dp = 200.dp,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val orbSizePx = with(androidx.compose.ui.platform.LocalDensity.current) { size.toPx() }
    val centerOffset = orbSizePx / 2f

    // ── Pulse animations ────────────────────────────────────────
    val pulseAlpha = remember { Animatable(0.5f) }
    val glowScale = remember { Animatable(1f) }
    val ringRotation = remember { Animatable(0f) }
    val waveOffset = remember { Animatable(0f) }

    // Choose animation specs based on state
    val pulseDuration = when (state) {
        OrbState.IDLE -> 2500
        OrbState.LISTENING -> 600
        OrbState.THINKING -> 1200
        OrbState.SPEAKING -> 800
    }

    val orbColor = when (state) {
        OrbState.IDLE -> NeonCyan
        OrbState.LISTENING -> NeonCyan
        OrbState.THINKING -> ElectricPurple
        OrbState.SPEAKING -> NeonGreen
    }

    val glowColor = when (state) {
        OrbState.IDLE -> GlowMaskCyan
        OrbState.LISTENING -> GlowMaskCyan
        OrbState.THINKING -> GlowMaskPurple
        OrbState.SPEAKING -> GlowMaskGreen
    }

    val orbColorDim = when (state) {
        OrbState.IDLE -> NeonCyanDim
        OrbState.LISTENING -> NeonCyanDim
        OrbState.THINKING -> ElectricPurpleDim
        OrbState.SPEAKING -> NeonGreenDim
    }

    // Pulse alpha animation
    LaunchedEffect(state) {
        pulseAlpha.animateTo(
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = pulseDuration, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse
            )
        )
    }

    // Glow scale animation
    LaunchedEffect(state) {
        glowScale.snapTo(1f)
        glowScale.animateTo(
            targetValue = 1.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = pulseDuration, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse
            )
        )
    }

    // Ring rotation (thinking state)
    LaunchedEffect(state) {
        if (state == OrbState.THINKING) {
            ringRotation.snapTo(0f)
            ringRotation.animateTo(
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 2000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
        }
    }

    // Wave offset (speaking state)
    LaunchedEffect(state) {
        if (state == OrbState.SPEAKING) {
            waveOffset.snapTo(0f)
            waveOffset.animateTo(
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1500, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(centerOffset, centerOffset)
            val baseRadius = orbSizePx * 0.3f

            // ── Outer glow ──────────────────────────────────────
            val glowRadius = baseRadius * glowScale.value * 1.5f
            drawCircle(
                brush = RadialGradient(
                    colors = listOf(glowColor, Color.Transparent),
                    center = center,
                    radius = glowRadius
                ),
                radius = glowRadius,
                center = center,
                alpha = pulseAlpha.value * 0.6f
            )

            // ── Rotating ring (THINKING) ────────────────────────
            if (state == OrbState.THINKING) {
                rotate(ringRotation.value, center) {
                    val ringRadius = baseRadius * 1.35f
                    val strokeWidth = 3.dp.toPx()
                    // Draw segmented ring
                    for (i in 0 until 4) {
                        val startAngle = i * 90f
                        val sweep = 55f
                        drawArc(
                            color = ElectricPurple,
                            startAngle = startAngle,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = Offset(center.x - ringRadius, center.y - ringRadius),
                            size = Size(ringRadius * 2, ringRadius * 2),
                            style = Stroke(width = strokeWidth),
                            alpha = 0.9f
                        )
                    }
                }
            }

            // ── Wave rings (SPEAKING) ───────────────────────────
            if (state == OrbState.SPEAKING) {
                val numWaves = 3
                for (i in 0 until numWaves) {
                    val phase = (waveOffset.value + i * 120f) % 360f
                    val progress = phase / 360f
                    val waveRadius = baseRadius * (1.3f + progress * 0.5f)
                    val waveAlpha = 1f - progress
                    drawCircle(
                        color = NeonGreen,
                        radius = waveRadius,
                        center = center,
                        style = Stroke(width = 2.dp.toPx()),
                        alpha = waveAlpha * 0.5f
                    )
                }
            }

            // ── Outer ring (always visible) ─────────────────────
            val outerRingRadius = baseRadius * 1.2f
            drawCircle(
                color = orbColor,
                radius = outerRingRadius,
                center = center,
                style = Stroke(width = 1.5.dp.toPx()),
                alpha = pulseAlpha.value * 0.6f
            )

            // ── Main orb body ───────────────────────────────────
            drawCircle(
                brush = RadialGradient(
                    colors = listOf(
                        orbColor,
                        orbColorDim,
                        Color(0xFF0A0E17)
                    ),
                    center = Offset(center.x, center.y - baseRadius * 0.2f),
                    radius = baseRadius
                ),
                radius = baseRadius,
                center = center,
                alpha = 0.9f
            )

            // ── Inner highlight / specular ──────────────────────
            val highlightRadius = baseRadius * 0.35f
            drawCircle(
                brush = RadialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.3f),
                        Color.Transparent
                    ),
                    center = Offset(center.x - baseRadius * 0.2f, center.y - baseRadius * 0.2f),
                    radius = highlightRadius
                ),
                radius = highlightRadius,
                center = Offset(center.x - baseRadius * 0.2f, center.y - baseRadius * 0.2f),
            )

            // ── Listening pulse ring ────────────────────────────
            if (state == OrbState.LISTENING) {
                val listenRingRadius = baseRadius * (1.2f + (glowScale.value - 1f) * 2f)
                drawCircle(
                    color = NeonCyan,
                    radius = listenRingRadius,
                    center = center,
                    style = Stroke(width = 2.dp.toPx()),
                    alpha = (1f - (glowScale.value - 1f) / 0.15f).coerceIn(0f, 0.8f)
                )
            }
        }
    }
}

/**
 * Helper to create a radial gradient shader brush.
 */
private fun RadialGradient(
    colors: List<Color>,
    center: Offset,
    radius: Float
): ShaderBrush {
    return object : ShaderBrush() {
        override fun createShader(size: Size): Shader {
            return android.graphics.RadialGradient(
                center.x, center.y, radius,
                colors.map { it.toArgb() }.toIntArray(),
                null,
                Shader.TileMode.Clamp
            )
        }
    }
}
