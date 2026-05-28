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

/**
 * Data models for weather.
 */
data class WeatherData(
    val cityName: String = "San Francisco",
    val temp: Int = 22,
    val description: String = "Partly Cloudy",
    val icon: String = "⛅",
    val humidity: Int = 65,
    val windSpeed: Float = 12.5f,
    val pressure: Int = 1013,
    val feelsLike: Int = 20,
    val forecasts: List<DailyForecast> = emptyList(),
)

data class DailyForecast(
    val day: String,
    val icon: String,
    val high: Int,
    val low: Int,
)

/**
 * Weather display screen — holographic weather station.
 */
@Composable
fun WeatherScreen(
    weather: WeatherData = WeatherData(
        forecasts = listOf(
            DailyForecast("Mon", "☀️", 24, 16),
            DailyForecast("Tue", "⛅", 22, 15),
            DailyForecast("Wed", "🌧️", 18, 12),
            DailyForecast("Thu", "🌦️", 20, 14),
            DailyForecast("Fri", "☀️", 25, 17),
        )
    ),
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
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
                    text = weather.cityName.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary,
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Weather icon
                Text(
                    text = weather.icon,
                    style = MaterialTheme.typography.displayLarge,
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Temperature
                Text(
                    text = "${weather.temp}°",
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = NeonCyan,
                )

                // Description
                Text(
                    text = weather.description,
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
                value = "${weather.humidity}%",
                icon = Icons.Filled.WaterDrop,
                modifier = Modifier.weight(1f),
                color = NeonCyan,
            )
            WeatherDetailCard(
                label = "WIND",
                value = "${weather.windSpeed} km/h",
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
                value = "${weather.pressure} hPa",
                icon = Icons.Filled.Speed,
                modifier = Modifier.weight(1f),
                color = NeonGreen,
            )
            WeatherDetailCard(
                label = "FEELS LIKE",
                value = "${weather.feelsLike}°",
                icon = Icons.Filled.Thermostat,
                modifier = Modifier.weight(1f),
                color = Amber,
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── 5-Day Forecast ─────────────────────────────────────
        Text(
            text = "5-DAY FORECAST",
            style = MaterialTheme.typography.labelLarge,
            color = ElectricPurple,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            weather.forecasts.forEach { forecast ->
                ForecastDayCard(forecast = forecast)
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

@Composable
private fun ForecastDayCard(forecast: DailyForecast) {
    GlowCard(
        glowColor = NeonCyan,
        borderAlpha = 0.15f,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = forecast.day,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = forecast.icon,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${forecast.high}°",
                style = MaterialTheme.typography.labelMedium,
                color = TextPrimary,
            )
            Text(
                text = "${forecast.low}°",
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
            )
        }
    }
}


