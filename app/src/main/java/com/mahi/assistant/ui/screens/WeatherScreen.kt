package com.mahi.assistant.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mahi.assistant.ui.components.GlowCard
import com.mahi.assistant.ui.theme.*
import com.mahi.assistant.ui.viewmodel.MahiViewModel

/**
 * Weather display screen — holographic weather station.
 * Now connected to ViewModel for live weather data.
 */
@Composable
fun WeatherScreen(
    viewModel: MahiViewModel,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val weatherState by viewModel.weatherState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
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
                text = "WEATHER STATION",
                style = MaterialTheme.typography.headlineSmall,
                color = NeonCyan,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (weatherState.isLoading) {
            // Loading state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = NeonCyan)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Fetching weather data...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
            }
        } else if (weatherState.error != null) {
            // Error state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.CloudOff,
                        contentDescription = null,
                        tint = Amber,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Could not load weather",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = weatherState.error ?: "Unknown error",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Set your OpenWeatherMap API key in Settings",
                        style = MaterialTheme.typography.bodySmall,
                        color = Amber,
                    )
                }
            }
        } else if (weatherState.city.isNotEmpty()) {
            // ── Main Weather Card ───────────────────────────────────
            GlowCard(
                modifier = Modifier.fillMaxWidth(),
                glowColor = NeonCyan,
                animateGlow = true,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // City name
                    Text(
                        text = weatherState.city.uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Temperature
                    Text(
                        text = "${weatherState.temperature.toInt()}°",
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = NeonCyan,
                    )

                    // Description
                    Text(
                        text = weatherState.description.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Detail Cards Row ────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                WeatherDetailCard(
                    label = "HUMIDITY",
                    value = "${weatherState.humidity}%",
                    icon = Icons.Filled.WaterDrop,
                    modifier = Modifier.weight(1f),
                    color = NeonCyan,
                )
                WeatherDetailCard(
                    label = "WIND",
                    value = "${weatherState.windSpeed} km/h",
                    icon = Icons.Filled.Air,
                    modifier = Modifier.weight(1f),
                    color = ElectricPurple,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                WeatherDetailCard(
                    label = "PRESSURE",
                    value = "${weatherState.pressure} hPa",
                    icon = Icons.Filled.Speed,
                    modifier = Modifier.weight(1f),
                    color = NeonGreen,
                )
                WeatherDetailCard(
                    label = "FEELS LIKE",
                    value = "${weatherState.feelsLike.toInt()}°",
                    icon = Icons.Filled.Thermostat,
                    modifier = Modifier.weight(1f),
                    color = Amber,
                )
            }
        } else {
            // No data yet - prompt user to ask
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.WbSunny,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Ask MAHI for weather",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Say \"What's the weather?\" to get started",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun WeatherDetailCard(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = NeonCyan,
) {
    GlowCard(
        modifier = modifier,
        glowColor = color,
        borderAlpha = 0.25f,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                color = color,
            )
        }
    }
}
