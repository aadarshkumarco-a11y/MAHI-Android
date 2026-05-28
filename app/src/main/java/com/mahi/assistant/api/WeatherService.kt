package com.mahi.assistant.api

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// ──────────────────────────────────────────────────────────────────────────────
// Retrofit Service Interface
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Retrofit interface for the OpenWeatherMap Current Weather Data API (free tier).
 *
 * Base URL: https://api.openweathermap.org/data/2.5/
 *
 * Usage:
 * ```
 * val retrofit = Retrofit.Builder()
 *     .baseUrl("https://api.openweathermap.org/data/2.5/")
 *     .addConverterFactory(GsonConverterFactory.create())
 *     .build()
 * val service = retrofit.create(WeatherService::class.java)
 * val weather = service.getCurrentWeather("London", "YOUR_API_KEY", "metric")
 * ```
 */
interface WeatherService {

    @GET("weather")
    suspend fun getCurrentWeather(
        @Query("q") city: String,
        @Query("appid") appId: String,
        @Query("units") units: String = "metric"
    ): WeatherData
}

// ──────────────────────────────────────────────────────────────────────────────
// Response Data Classes
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Top-level response from the OpenWeatherMap current weather endpoint.
 */
data class WeatherData(
    val coord: Coord? = null,
    val weather: List<Weather>? = null,
    val main: Main? = null,
    val visibility: Int? = null,
    val wind: Wind? = null,
    val clouds: Clouds? = null,
    val rain: Rain? = null,
    val snow: Snow? = null,
    val dt: Long? = null,
    val sys: Sys? = null,
    val timezone: Int? = null,
    val id: Int? = null,
    val name: String? = null,
    val cod: Int? = null
)

data class Coord(
    val lon: Double? = null,
    val lat: Double? = null
)

data class Weather(
    val id: Int? = null,
    val main: String? = null,
    val description: String? = null,
    val icon: String? = null
)

data class Main(
    val temp: Double? = null,
    @SerializedName("feels_like")
    val feelsLike: Double? = null,
    @SerializedName("temp_min")
    val tempMin: Double? = null,
    @SerializedName("temp_max")
    val tempMax: Double? = null,
    val pressure: Int? = null,
    val humidity: Int? = null,
    @SerializedName("sea_level")
    val seaLevel: Int? = null,
    @SerializedName("grnd_level")
    val grndLevel: Int? = null
)

data class Wind(
    val speed: Double? = null,
    val deg: Int? = null,
    val gust: Double? = null
)

data class Clouds(
    val all: Int? = null
)

data class Rain(
    @SerializedName("1h")
    val oneHour: Double? = null,
    @SerializedName("3h")
    val threeHour: Double? = null
)

data class Snow(
    @SerializedName("1h")
    val oneHour: Double? = null,
    @SerializedName("3h")
    val threeHour: Double? = null
)

data class Sys(
    val type: Int? = null,
    val id: Int? = null,
    val country: String? = null,
    val sunrise: Long? = null,
    val sunset: Long? = null
)

// ──────────────────────────────────────────────────────────────────────────────
// Formatting Helper
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Format weather data as speech-friendly text suitable for TTS output.
 * Produces a natural-sounding weather summary.
 */
fun WeatherData.toSpeechText(): String {
    val cityName = this.name ?: "your location"
    val mainInfo = this.main
    val weatherInfo = this.weather?.firstOrNull()
    val windInfo = this.wind

    if (mainInfo == null || weatherInfo == null) {
        return "I couldn't retrieve the weather information. Please try again."
    }

    val description = weatherInfo.description ?: "unknown conditions"
    val temp = mainInfo.temp
    val feelsLike = mainInfo.feelsLike
    val humidity = mainInfo.humidity
    val windSpeed = windInfo?.speed

    val parts = mutableListOf<String>()

    // Main summary
    val tempStr = if (temp != null) String.format("%.0f", temp) else "unknown"
    parts.add("Currently in $cityName, it's $tempStr degrees with $description.")

    // Feels like
    if (feelsLike != null && temp != null) {
        val feelsDiff = kotlin.math.abs(feelsLike - temp)
        if (feelsDiff > 2.0) {
            val feelsStr = String.format("%.0f", feelsLike)
            parts.add("It feels like $feelsStr degrees.")
        }
    }

    // Humidity
    if (humidity != null) {
        val humidityDesc = when {
            humidity < 30 -> "low"
            humidity < 60 -> "moderate"
            else -> "high"
        }
        parts.add("Humidity is $humidity percent, which is $humidityDesc.")
    }

    // Wind
    if (windSpeed != null) {
        val windDesc = when {
            windSpeed < 2.0 -> "calm"
            windSpeed < 5.0 -> "a light breeze"
            windSpeed < 10.0 -> "moderate wind"
            windSpeed < 15.0 -> "strong wind"
            else -> "very strong wind"
        }
        parts.add("Wind is $windDesc at ${String.format("%.1f", windSpeed)} meters per second.")
    }

    // Rain
    this.rain?.oneHour?.let { rainMm ->
        if (rainMm > 0) {
            parts.add("It's raining with ${String.format("%.1f", rainMm)} millimeters in the last hour.")
        }
    }

    // Snow
    this.snow?.oneHour?.let { snowMm ->
        if (snowMm > 0) {
            parts.add("It's snowing with ${String.format("%.1f", snowMm)} millimeters in the last hour.")
        }
    }

    return parts.joinToString(" ")
}

/**
 * Format weather data as a concise text string for UI display.
 */
fun WeatherData.toDisplayText(): String {
    val cityName = this.name ?: "Unknown"
    val temp = this.main?.temp?.let { String.format("%.0f°C", it) } ?: "N/A"
    val description = this.weather?.firstOrNull()?.description?.capitalize() ?: "N/A"
    val humidity = this.main?.humidity?.let { "$it%" } ?: "N/A"

    return "$cityName: $temp, $description, Humidity: $humidity"
}

// ──────────────────────────────────────────────────────────────────────────────
// Weather Client Helper
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Convenience helper to create a WeatherService instance with the
 * OpenWeatherMap base URL pre-configured.
 */
object WeatherClient {

    private const val BASE_URL = "https://api.openweathermap.org/data/2.5/"

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(com.google.gson.GsonConverterFactory.create())
            .build()
    }

    val instance: WeatherService by lazy {
        retrofit.create(WeatherService::class.java)
    }
}
