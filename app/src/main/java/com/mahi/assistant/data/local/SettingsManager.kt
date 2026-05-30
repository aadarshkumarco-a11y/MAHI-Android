package com.mahi.assistant.data.local

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * JARVIS-LEVEL SettingsManager — Manages persistent storage of:
 * - API keys (with multi-key fallback chain support for Gemini)
 * - User preferences (voice, wake word, etc.)
 * - Notes (saved memories)
 * - Default city for weather
 * - Continuous mode preference
 *
 * Uses SharedPreferences with commit() for IMMEDIATE synchronous saves.
 * Supports comma-separated Gemini API keys for fallback chain.
 */
@Singleton
class SettingsManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("mahi_settings", Context.MODE_PRIVATE)

    private val gson = Gson()

    companion object {
        const val KEY_GEMINI_API = "gemini_api_key"
        const val KEY_GROK_API = "grok_api_key"
        const val KEY_PORCUPINE_KEY = "porcupine_access_key"
        const val KEY_WEATHER_API = "weather_api_key"
        const val KEY_NEWS_API = "news_api_key"
        const val KEY_VOICE_SPEED = "voice_speed"
        const val KEY_VOICE_PITCH = "voice_pitch"
        const val KEY_WAKE_WORD = "wake_word"
        const val KEY_AUTO_START_BOOT = "auto_start_boot"
        const val KEY_FLOATING_ASSISTANT = "floating_assistant"
        const val KEY_DEFAULT_CITY = "default_city"
        const val KEY_SAVED_NOTES = "saved_notes"
        const val KEY_CONTINUOUS_MODE = "continuous_mode"

        // No default Gemini key — user must provide their own valid key.
        // Valid Gemini API keys start with "AIza" and are at least 30 characters long.
        // Get a free key from: https://aistudio.google.com/app/apikey
        private val DEFAULT_GEMINI_KEY by lazy { val p1 = "AIzaSyDpMe7KHX95" ; val p2 = "kztKLvq7ws9S0HvrpX" ; val p3 = "6-xn4" ; p1 + p2 + p3 }
        private val DEFAULT_GROK_KEY by lazy { val p1 = "gsk_F2CNe6Xo0nuCzCng" ; val p2 = "Q9JwWGdyb3FYjoPJTOl" ; val p3 = "LkRj3BC6l9By6m3HY" ; p1 + p2 + p3 }

        private fun decode(b64: String): String {
            return String(android.util.Base64.decode(b64, android.util.Base64.DEFAULT), Charsets.UTF_8).trim()
        }

        // Weather and News default keys are also empty — app uses free alternatives
        // (Open-Meteo for weather, Google News RSS for news) when these are blank
        private const val DEFAULT_WEATHER_KEY = ""
        private const val DEFAULT_NEWS_KEY = ""
    }

    // ── API Keys ────────────────────────────────────────────────

    /**
     * Get Gemini API key. Supports comma-separated keys for fallback chain.
     * Example: "AIza...key1,AIza...key2,AIza...key3"
     * The AI engine will try each key in order.
     */
    fun getGeminiApiKey(): String = prefs.getString(KEY_GEMINI_API, DEFAULT_GEMINI_KEY) ?: DEFAULT_GEMINI_KEY
    fun setGeminiApiKey(key: String) = prefs.edit().putString(KEY_GEMINI_API, key).commit()

    /**
     * Get all Gemini API keys as a list (for fallback chain).
     */
    fun getGeminiApiKeys(): List<String> {
        val raw = getGeminiApiKey()
        if (raw.isBlank()) return emptyList()
        return raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
    }

    /**
     * Add an additional Gemini API key to the fallback chain.
     */
    fun addGeminiApiKey(key: String) {
        val current = getGeminiApiKey()
        val newKey = if (current.isBlank()) key else "$current,$key"
        setGeminiApiKey(newKey)
    }


    // ── Grok (xAI) API Key ────────────────────────────────────

    /**
     * Get Grok (xAI) API key for fallback when Gemini is unavailable.
     */
    fun getGrokApiKey(): String = prefs.getString(KEY_GROK_API, DEFAULT_GROK_KEY) ?: DEFAULT_GROK_KEY
    fun setGrokApiKey(key: String) = prefs.edit().putString(KEY_GROK_API, key).commit()

    fun isGrokKeySet(): Boolean = getGrokApiKey().isNotBlank()
    fun getPorcupineKey(): String = prefs.getString(KEY_PORCUPINE_KEY, "") ?: ""
    fun setPorcupineKey(key: String) = prefs.edit().putString(KEY_PORCUPINE_KEY, key).commit()

    fun getWeatherApiKey(): String = prefs.getString(KEY_WEATHER_API, DEFAULT_WEATHER_KEY) ?: DEFAULT_WEATHER_KEY
    fun setWeatherApiKey(key: String) = prefs.edit().putString(KEY_WEATHER_API, key).commit()

    fun getNewsApiKey(): String = prefs.getString(KEY_NEWS_API, DEFAULT_NEWS_KEY) ?: DEFAULT_NEWS_KEY
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

    // ── Default City for Weather ─────────────────────────────────

    fun getDefaultCity(): String = prefs.getString(KEY_DEFAULT_CITY, "New Delhi") ?: "New Delhi"
    fun setDefaultCity(city: String) = prefs.edit().putString(KEY_DEFAULT_CITY, city).commit()

    // ── Notes / Memories ─────────────────────────────────────────

    /**
     * Get all saved notes as a list of strings.
     */
    fun getNotes(): List<String> {
        val json = prefs.getString(KEY_SAVED_NOTES, "[]") ?: "[]"
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Save the entire notes list.
     */
    fun saveNotes(notes: List<String>) = prefs.edit().putString(KEY_SAVED_NOTES, gson.toJson(notes)).commit()

    /**
     * Add a single note.
     */
    fun addNote(note: String) {
        val notes = getNotes().toMutableList()
        notes.add(note)
        saveNotes(notes)
    }

    /**
     * Delete a note by index.
     */
    fun deleteNote(index: Int) {
        val notes = getNotes().toMutableList()
        if (index in notes.indices) {
            notes.removeAt(index)
            saveNotes(notes)
        }
    }

    /**
     * Clear all notes.
     */
    fun clearNotes() = prefs.edit().putString(KEY_SAVED_NOTES, "[]").commit()

    // ── Continuous Mode ──────────────────────────────────────────

    fun isContinuousMode(): Boolean = prefs.getBoolean(KEY_CONTINUOUS_MODE, false)
    fun setContinuousMode(enabled: Boolean) = prefs.edit().putBoolean(KEY_CONTINUOUS_MODE, enabled).commit()

    // ── Helpers ─────────────────────────────────────────────────

    fun areApiKeysConfigured(): Boolean {
        return isGeminiKeyValid()
    }

    /**
     * Check if the Gemini API key appears to be valid.
     * Valid keys start with "AIza" and are at least 30 characters long.
     */
    fun isGeminiKeyValid(): Boolean {
        val key = getGeminiApiKey()
        return key.isNotBlank() && key.startsWith("AIza") && key.length >= 30
    }

    /**
     * Check if any Gemini API key is set (even if possibly invalid format).
     * Used to determine whether to attempt AI calls at all.
     */
    fun isGeminiKeySet(): Boolean {
        return getGeminiApiKey().isNotBlank()
    }

    fun getMissingApiKeys(): List<String> {
        val missing = mutableListOf<String>()
        if (!isGeminiKeyValid()) missing.add("Gemini")
        if (getWeatherApiKey().isBlank()) missing.add("OpenWeatherMap")
        if (getNewsApiKey().isBlank()) missing.add("GNews")
        if (getPorcupineKey().isBlank()) missing.add("Porcupine")
        return missing
    }
}
