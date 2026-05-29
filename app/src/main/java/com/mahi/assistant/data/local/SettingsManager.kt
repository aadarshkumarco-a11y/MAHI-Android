package com.mahi.assistant.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages persistent storage of API keys and user preferences
 * using SharedPreferences for simplicity and reliability.
 *
 * Uses commit() instead of apply() for IMMEDIATE synchronous saves,
 * ensuring API keys are persisted before the app navigates away.
 */
@Singleton
class SettingsManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("mahi_settings", Context.MODE_PRIVATE)

    companion object {
        const val KEY_GEMINI_API = "gemini_api_key"
        const val KEY_PORCUPINE_KEY = "porcupine_access_key"
        const val KEY_WEATHER_API = "weather_api_key"
        const val KEY_NEWS_API = "news_api_key"
        const val KEY_VOICE_SPEED = "voice_speed"
        const val KEY_VOICE_PITCH = "voice_pitch"
        const val KEY_WAKE_WORD = "wake_word"
        const val KEY_AUTO_START_BOOT = "auto_start_boot"
        const val KEY_FLOATING_ASSISTANT = "floating_assistant"
    }

    // ── API Keys ────────────────────────────────────────────────

    fun getGeminiApiKey(): String = prefs.getString(KEY_GEMINI_API, "") ?: ""
    fun setGeminiApiKey(key: String) = prefs.edit().putString(KEY_GEMINI_API, key).commit()

    fun getPorcupineKey(): String = prefs.getString(KEY_PORCUPINE_KEY, "") ?: ""
    fun setPorcupineKey(key: String) = prefs.edit().putString(KEY_PORCUPINE_KEY, key).commit()

    fun getWeatherApiKey(): String = prefs.getString(KEY_WEATHER_API, "") ?: ""
    fun setWeatherApiKey(key: String) = prefs.edit().putString(KEY_WEATHER_API, key).commit()

    fun getNewsApiKey(): String = prefs.getString(KEY_NEWS_API, "") ?: ""
    fun setNewsApiKey(key: String) = prefs.edit().putString(KEY_NEWS_API, key).commit()

    // ── Voice Settings ──────────────────────────────────────────

    fun getVoiceSpeed(): Float = prefs.getFloat(KEY_VOICE_SPEED, 1.0f)
    fun setVoiceSpeed(speed: Float) = prefs.edit().putFloat(KEY_VOICE_SPEED, speed).commit()

    fun getVoicePitch(): Float = prefs.getFloat(KEY_VOICE_PITCH, 1.0f)
    fun setVoicePitch(pitch: Float) = prefs.edit().putFloat(KEY_VOICE_PITCH, pitch).commit()

    fun getWakeWord(): String = prefs.getString(KEY_WAKE_WORD, "Hey Mahi") ?: "Hey Mahi"
    fun setWakeWord(word: String) = prefs.edit().putString(KEY_WAKE_WORD, word).commit()

    // ── General Settings ────────────────────────────────────────

    fun getAutoStartOnBoot(): Boolean = prefs.getBoolean(KEY_AUTO_START_BOOT, true)
    fun setAutoStartOnBoot(enabled: Boolean) = prefs.edit().putBoolean(KEY_AUTO_START_BOOT, enabled).commit()

    fun getFloatingAssistant(): Boolean = prefs.getBoolean(KEY_FLOATING_ASSISTANT, false)
    fun setFloatingAssistant(enabled: Boolean) = prefs.edit().putBoolean(KEY_FLOATING_ASSISTANT, enabled).commit()

    // ── Helpers ─────────────────────────────────────────────────

    fun areApiKeysConfigured(): Boolean {
        return getGeminiApiKey().isNotBlank()
    }

    fun getMissingApiKeys(): List<String> {
        val missing = mutableListOf<String>()
        if (getGeminiApiKey().isBlank()) missing.add("Gemini")
        if (getWeatherApiKey().isBlank()) missing.add("OpenWeatherMap")
        if (getNewsApiKey().isBlank()) missing.add("GNews")
        if (getPorcupineKey().isBlank()) missing.add("Porcupine")
        return missing
    }
}
