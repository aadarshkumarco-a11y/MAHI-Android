package com.mahi.assistant.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mahi.assistant.ui.components.GlowCard
import com.mahi.assistant.ui.components.NeonButton
import com.mahi.assistant.ui.theme.*

/**
 * Data model for a routine.
 */
data class Routine(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val description: String,
    val icon: ImageVector,
    val actions: List<String>,
    val isActive: Boolean = false,
)

/**
 * Routines panel — JARVIS automated sequences.
 */
@Composable
fun RoutinesScreen(
    onBack: () -> Unit = {},
    onActivateRoutine: (String) -> Unit = {},
    onCreateCustom: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var routines by remember {
        mutableStateOf(
            listOf(
                Routine(
                    name = "Good Morning",
                    description = "Start your day with weather, news, and device setup",
                    icon = Icons.Filled.WbSunny,
                    actions = listOf("Turn off DND", "Read weather", "Read headlines", "Set brightness to 70%"),
                ),
                Routine(
                    name = "Good Night",
                    description = "Wind down with minimal distractions",
                    icon = Icons.Filled.Nightlight,
                    actions = listOf("Enable DND", "Set brightness to 10%", "Turn off Bluetooth", "Set alarm"),
                ),
                Routine(
                    name = "Work Mode",
                    description = "Focus settings for productivity",
                    icon = Icons.Filled.Work,
                    actions = listOf("Enable DND", "Turn on WiFi", "Set ringer to vibrate", "Close social apps"),
                ),
                Routine(
                    name = "Drive Mode",
                    description = "Hands-free driving assistant",
                    icon = Icons.Filled.DirectionsCar,
                    actions = listOf("Turn on Bluetooth", "Enable auto-rotate", "Launch maps", "Read messages aloud"),
                ),
                Routine(
                    name = "Movie Time",
                    description = "Optimal media experience",
                    icon = Icons.Filled.Movie,
                    actions = listOf("Set brightness to 40%", "Turn on DND", "Enable auto-rotate", "Set volume to 80%"),
                ),
                Routine(
                    name = "Battery Saver",
                    description = "Maximize battery life",
                    icon = Icons.Filled.BatterySaver,
                    actions = listOf("Enable battery saver", "Lower brightness", "Turn off Bluetooth", "Disable auto-rotate"),
                ),
            )
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // ── Header ──────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = NeonCyan,
                )
            }
            Text(
                text = "ROUTINES",
                style = MaterialTheme.typography.headlineSmall,
                color = NeonCyan,
            )
            Spacer(modifier = Modifier.weight(1f))

            // Create custom routine button
            NeonButton(
                text = "+ CUSTOM",
                onClick = onCreateCustom,
                glowColor = ElectricPurple,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Active Routine Indicator ────────────────────────────
        val activeRoutine = routines.find { it.isActive }
        if (activeRoutine != null) {
            GlowCard(
                modifier = Modifier.fillMaxWidth(),
                glowColor = NeonGreen,
                animateGlow = true,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayCircle,
                        contentDescription = null,
                        tint = NeonGreen,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "ACTIVE",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonGreen,
                        )
                        Text(
                            text = activeRoutine.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = {
                        routines = routines.map {
                            it.copy(isActive = false)
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Stop,
                            contentDescription = "Stop",
                            tint = ErrorRed,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // ── Routine Cards ───────────────────────────────────────
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(routines, key = { it.id }) { routine ->
                RoutineCard(
                    routine = routine,
                    onActivate = {
                        routines = routines.map {
                            it.copy(isActive = if (it.id == routine.id) !it.isActive else false)
                        }
                        onActivateRoutine(routine.id)
                    },
                )
            }

            // Bottom spacing
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun RoutineCard(
    routine: Routine,
    onActivate: () -> Unit,
) {
    GlowCard(
        modifier = Modifier.fillMaxWidth(),
        glowColor = if (routine.isActive) NeonGreen else NeonCyan,
        animateGlow = routine.isActive,
        borderAlpha = if (routine.isActive) 0.5f else 0.2f,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            // Icon + Name row
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = routine.icon,
                    contentDescription = routine.name,
                    tint = if (routine.isActive) NeonGreen else NeonCyan,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = routine.name.uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (routine.isActive) NeonGreen else TextPrimary,
                    )
                    Text(
                        text = routine.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action list
            routine.actions.forEach { action ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 8.dp, bottom = 2.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = if (routine.isActive) NeonGreen else ElectricPurple,
                        ) {}
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = action,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Activate button
            NeonButton(
                text = if (routine.isActive) "DEACTIVATE" else "ACTIVATE",
                onClick = onActivate,
                glowColor = if (routine.isActive) ErrorRed else NeonCyan,
            )
        }
    }
}
