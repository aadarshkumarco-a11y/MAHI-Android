package com.mahi.assistant.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mahi.assistant.ui.theme.*

/**
 * A card for device toggles (WiFi, Bluetooth, Flashlight, etc.)
 * Neon cyan glow when ON, dim when OFF.
 *
 * @param name       Device label
 * @param icon       Material icon for the device
 * @param isOn       Current toggle state
 * @param onToggle   Callback when toggled
 */
@Composable
fun DeviceToggleCard(
    name: String,
    icon: ImageVector,
    isOn: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glowAlpha = remember { Animatable(if (isOn) 0.4f else 0f) }

    LaunchedEffect(isOn) {
        if (isOn) {
            glowAlpha.animateTo(
                targetValue = 0.6f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 2000, easing = EaseInOutSine),
                    repeatMode = RepeatMode.Reverse
                )
            )
        } else {
            glowAlpha.snapTo(0f)
        }
    }

    val currentGlowAlpha by glowAlpha.asState()
    val accentColor = if (isOn) NeonCyan else TextTertiary
    val bgColor = if (isOn) NeonCyan.copy(alpha = 0.06f) else DarkPanel

    Card(
        modifier = modifier.drawBehind {
            if (isOn) {
                val blurPx = 6.dp.toPx()
                drawIntoCanvas { canvas ->
                    val paint = Paint().apply {
                        color = NeonCyan.copy(alpha = currentGlowAlpha.coerceIn(0f, 1f))
                        asFrameworkPaint().maskFilter = android.graphics.BlurMaskFilter(
                            blurPx,
                            android.graphics.BlurMaskFilter.Blur.NORMAL
                        )
                    }
                    canvas.nativeCanvas.drawRoundRect(
                        android.graphics.RectF(0f, 0f, size.width, size.height),
                        12.dp.toPx(), 12.dp.toPx(),
                        paint.asFrameworkPaint()
                    )
                }
            }
        },
        colors = CardDefaults.cardColors(
            containerColor = bgColor,
            contentColor = TextPrimary,
        ),
        border = BorderStroke(
            width = 1.dp,
            color = accentColor.copy(alpha = if (isOn) 0.5f else 0.15f)
        ),
        shape = RoundedCornerShape(12.dp),
        onClick = onToggle,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = name,
                tint = accentColor,
                modifier = Modifier.size(28.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                color = accentColor,
            )
            Spacer(modifier = Modifier.height(8.dp))
            // Toggle indicator
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(18.dp)
            ) {
                // Track
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(9.dp),
                    color = if (isOn) NeonCyan.copy(alpha = 0.3f) else DarkPanelBorder,
                ) {}
                // Thumb
                Box(
                    modifier = Modifier
                        .align(if (isOn) Alignment.CenterEnd else Alignment.CenterStart)
                        .offset(x = if (isOn) (-2).dp else 2.dp)
                        .size(14.dp),
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(7.dp),
                        color = accentColor,
                    ) {}
                }
            }
        }
    }
}
