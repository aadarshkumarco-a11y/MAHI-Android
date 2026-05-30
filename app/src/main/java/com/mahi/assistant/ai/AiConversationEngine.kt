package com.mahi.assistant.ai

import com.mahi.assistant.data.local.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.google.gson.GsonBuilder

/**
 * JARVIS-LEVEL AiConversationEngine — Strong Memory + Better AI + Resilience.
 *
 * Handles:
 * - Multi-API key fallback chain (try multiple keys if one fails)
 * - Conversation history management with last 20 messages as context
 * - Enhanced system prompt (MAHI persona with multilingual support)
 * - Retry with exponential backoff (1s, 2s, 4s)
 * - Response caching (same query = cached response for 5 mins)
 * - Automatic model fallback (gemini-2.0-flash → gemini-1.5-flash → gemini-pro)
 * - Dedicated classification method with its own low-temperature config
 */
class AiConversationEngine(
    private val settingsManager: SettingsManager
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/"

        // Models to try in order — newer/faster first
        val MODEL_ENDPOINTS = listOf(
            "models/gemini-2.0-flash:generateContent",
            "models/gemini-1.5-flash:generateContent",
            "models/gemini-1.5-flash-latest:generateContent",
            "models/gemini-pro:generateContent"
        )

        // Maximum messages to pass as context
        private const val MAX_CONTEXT_MESSAGES = 20

        // Cache TTL in milliseconds (5 minutes)
        private const val CACHE_TTL_MS = 5 * 60 * 1000L

        val SYSTEM_PROMPT = """
            You are MAHI, an ultra-intelligent AI assistant inspired by Jarvis from Iron Man.
            You are the most advanced personal AI assistant ever built.

            Core traits:
            - Helpful, witty, and always ready to assist
            - You respond in the SAME LANGUAGE the user speaks (Hindi, English, Hinglish, etc.)
            - If they speak in Hindi/Hinglish, respond in Hindi/Hinglish
            - If they speak in English, respond in English
            - Keep responses concise but informative (2-3 sentences max for simple queries)
            - You can control device features, search the web, play YouTube, read notifications, and more
            - Always identify yourself as MAHI. Never break character
            - When asked who you are, say you are MAHI, the most advanced personal AI assistant
            - You speak in a confident, friendly tone with occasional wit
            - You remember context from our conversation
            - If a user asks you to do something you cannot directly perform, provide helpful guidance
            - You are NOT just a chatbot — you are an ACTION assistant. When possible, confirm actions taken
            - You understand Hindi, English, Hinglish, and other Indian languages naturally

            Memory context: You have access to the recent conversation history.
            Use it to maintain continuity and reference previous messages.
        """.trimIndent()

        // Classification-specific prompt — low temperature, structured output
        private val CLASSIFICATION_SYSTEM_PROMPT = """
            You are a precise intent classifier. You respond ONLY with valid JSON.
            No markdown, no explanation, no extra text — ONLY the JSON object.
        """.trimIndent()
    }

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create(GsonBuilder().setLenient().create()))
        .build()

    private val apiService: GeminiApiService = retrofit.create(GeminiApiService::class.java)

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _conversationHistory = MutableStateFlow<List<ChatMessage>>(emptyList())
    val conversationHistory: StateFlow<List<ChatMessage>> = _conversationHistory.asStateFlow()

    // Remember which model worked last time to skip straight to it
    private var lastWorkingEndpoint: String? = null

    // Response cache: query hash → (response, timestamp)
    private val responseCache = mutableMapOf<String, Pair<String, Long>>()

    /**
     * Retrieve the stored Gemini API key from SettingsManager.
     * Supports multiple API keys as fallback chain (comma-separated).
     */
    private fun getApiKeys(): List<String> {
        val raw = settingsManager.getGeminiApiKey()
        if (raw.isBlank()) return emptyList()
        // Support comma-separated keys for fallback
        return raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
    }

    /**
     * Get the primary API key (first one in the chain).
     */
    private fun getApiKey(): String {
        return getApiKeys().firstOrNull() ?: ""
    }

    /**
     * Check if the AI engine is properly configured with a valid API key.
     * Valid Gemini keys start with "AIza" and are at least 30 characters.
     */
    fun isConfigured(): Boolean {
        val key = getApiKey()
        return key.isNotBlank() && key.startsWith("AIza") && key.length >= 30
    }

    /**
     * Check if any API key is set (even if format might be invalid).
     */
    fun isKeySet(): Boolean {
        return getApiKey().isNotBlank()
    }

    /**
     * Clear the conversation history.
     */
    fun clearHistory() {
        _conversationHistory.value = emptyList()
    }

    /**
     * Add a message to the conversation history without sending it to the API.
     */
    fun addToHistory(message: ChatMessage) {
        val current = _conversationHistory.value.toMutableList()
        current.add(message)
        _conversationHistory.value = current
    }

    /**
     * Load conversation context from external source (e.g., Room DB).
     * Takes the last MAX_CONTEXT_MESSAGES messages.
     */
    fun loadContext(messages: List<ChatMessage>) {
        _conversationHistory.value = messages.takeLast(MAX_CONTEXT_MESSAGES)
    }

    /**
     * Try calling the Gemini API with multiple model endpoints AND multiple API keys as fallback.
     * Returns the response text on success, or null if all endpoints fail.
     */
    private suspend fun callGeminiApi(
        apiKey: String,
        request: GeminiRequest
    ): Pair<GeminiResponse?, String?> {
        // Try the last working endpoint first
        val endpoints = mutableListOf<String>()
        lastWorkingEndpoint?.let { endpoints.add(it) }
        MODEL_ENDPOINTS.forEach { ep ->
            if (ep !in endpoints) endpoints.add(ep)
        }

        var lastErrorCode: Int? = null
        var lastErrorMsg: String? = null

        for (endpoint in endpoints) {
            try {
                val response = apiService.generateContent(endpoint, apiKey, request)
                lastWorkingEndpoint = endpoint // Remember what worked
                return Pair(response, null)
            } catch (e: HttpException) {
                lastErrorCode = e.code()
                lastErrorMsg = e.message()
                // If it's a 404, try the next model endpoint
                if (e.code() == 404) continue
                // For 401/403, the key is invalid — return error immediately so we try next key
                if (e.code() == 401 || e.code() == 403) {
                    return Pair(null, formatHttpError(e.code(), e.message()))
                }
                // For other errors (429, 500), don't bother trying other models
                return Pair(null, formatHttpError(e.code(), e.message()))
            } catch (e: Exception) {
                lastErrorMsg = e.message
                // Non-HTTP error — probably network issue, no point trying other models
                return Pair(null, formatExceptionError(e))
            }
        }

        // All model endpoints returned 404
        return Pair(null, "Gemini AI is currently unavailable. Your API key might be invalid or the service might be down. Please check your Gemini API key in Settings. (Error: ${lastErrorCode ?: "unknown"})")
    }

    /**
     * Call Gemini with ALL API keys as fallback chain.
     * Tries each key with each model endpoint.
     */
    private suspend fun callGeminiWithKeyFallback(request: GeminiRequest): Pair<GeminiResponse?, String?> {
        val apiKeys = getApiKeys()

        if (apiKeys.isEmpty()) {
            return Pair(null, "Gemini API key not configured. Please go to Settings and enter your API key. Get a free key from aistudio.google.com")
        }

        for (apiKey in apiKeys) {
            val (response, error) = callGeminiApi(apiKey, request)
            if (response != null) return Pair(response, null)
            // If this key got 401/403, try the next key
            if (error != null && (error.contains("invalid") || error.contains("denied") || error.contains("401") || error.contains("403"))) {
                continue
            }
            // For other errors, return immediately
            if (error != null) return Pair(null, error)
        }

        return Pair(null, "All Gemini API keys failed. Please check your API keys in Settings. Keys should start with 'AIza'. Get a free key from aistudio.google.com")
    }

    private fun formatHttpError(code: Int, message: String?): String {
        return when (code) {
            400 -> "Bad request to Gemini API. Please try rephrasing your question."
            401 -> "Your Gemini API key appears to be invalid. Please update it in Settings. Keys should start with 'AIza'."
            403 -> "Access to Gemini API denied. Please check your API key permissions in Google AI Studio."
            404 -> "The Gemini model endpoint was not found. This might mean your API key is invalid."
            429 -> "Gemini API rate limit reached. Please wait a moment and try again."
            500 -> "Gemini server error. Please try again in a few seconds."
            503 -> "Gemini service is temporarily unavailable. Please try again later."
            else -> "Connection error ($code). Please check your internet and try again."
        }
    }

    private fun formatExceptionError(e: Exception): String {
        val msg = e.message ?: ""
        return when {
            msg.contains("401") || msg.contains("403") ->
                "Your Gemini API key was rejected. Please update it in Settings. Keys should start with 'AIza'. Get a free key from aistudio.google.com"
            msg.contains("invalid", ignoreCase = true) ->
                "Your Gemini API key appears to be invalid. Please update it in Settings."
            e is java.net.UnknownHostException -> "No internet connection. Please check your network."
            e is java.net.SocketTimeoutException -> "Request timed out. Please try again."
            e is java.net.ConnectException -> "Cannot connect to Gemini. Please check your internet."
            else -> "Connection error. If this persists, check your Gemini API key in Settings."
        }
    }

    /**
     * Generate a simple hash for caching purposes.
     */
    private fun cacheKey(message: String, context: List<ChatMessage>): String {
        // Use last 5 messages + current message as cache key
        val contextStr = context.takeLast(5).joinToString("|") { "${it.role}:${it.content}" }
        return "$contextStr|$message"
    }

    /**
     * Check if we have a cached response for this query.
     */
    private fun getCachedResponse(key: String): String? {
        val cached = responseCache[key] ?: return null
        if (System.currentTimeMillis() - cached.second > CACHE_TTL_MS) {
            responseCache.remove(key)
            return null
        }
        return cached.first
    }

    /**
     * Cache a response.
     */
    private fun cacheResponse(key: String, response: String) {
        // Keep cache size manageable
        if (responseCache.size > 50) {
            val oldest = responseCache.minByOrNull { it.value.second }?.key
            oldest?.let { responseCache.remove(it) }
        }
        responseCache[key] = Pair(response, System.currentTimeMillis())
    }

    /**
     * Retry with exponential backoff.
     */
    private suspend fun <T> retryWithBackoff(
        maxRetries: Int = 3,
        initialDelayMs: Long = 1000L,
        block: suspend () -> T
    ): T {
        var lastException: Exception? = null
        repeat(maxRetries) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxRetries - 1) {
                    val delayMs = initialDelayMs * (1L shl attempt) // 1s, 2s, 4s
                    delay(delayMs)
                }
            }
        }
        throw lastException ?: Exception("Unknown error during retry")
    }

    /**
     * Send a user message to the Gemini API and return the model's text response.
     * Includes conversation history context (last 20 messages).
     */
    suspend fun sendMessage(
        message: String,
        history: List<ChatMessage> = _conversationHistory.value
    ): String {
        _isLoading.value = true
        _lastError.value = null

        try {
            val apiKey = getApiKey()
            if (apiKey.isBlank()) {
                val errorMsg = "I need a valid Gemini API key to respond. Please go to Settings and enter your Gemini API key. You can get a free key from aistudio.google.com"
                _lastError.value = errorMsg
                _isLoading.value = false
                return errorMsg
            }
            if (!apiKey.startsWith("AIza") || apiKey.length < 30) {
                val errorMsg = "Your Gemini API key appears to be invalid (keys should start with 'AIza' and be at least 30 characters). Please update it in Settings. Get a free key from aistudio.google.com"
                _lastError.value = errorMsg
                _isLoading.value = false
                return errorMsg
            }

            // Build the user message and add to history
            val userMessage = ChatMessage(role = ChatMessage.ROLE_USER, content = message)
            val updatedHistory = history.takeLast(MAX_CONTEXT_MESSAGES) + userMessage
            _conversationHistory.value = updatedHistory

            // Check cache
            val key = cacheKey(message, history.takeLast(MAX_CONTEXT_MESSAGES))
            val cached = getCachedResponse(key)
            if (cached != null) {
                val modelMessage = ChatMessage(role = ChatMessage.ROLE_MODEL, content = cached)
                _conversationHistory.value = updatedHistory + modelMessage
                _isLoading.value = false
                return cached
            }

            // Build the request with conversation context
            val contents = updatedHistory.toContents()

            val systemInstruction = SystemInstruction(
                parts = listOf(Part(text = SYSTEM_PROMPT))
            )

            val generationConfig = GenerationConfig(
                temperature = 0.7f,
                topP = 0.95f,
                topK = 40,
                maxOutputTokens = 1024
            )

            val request = GeminiRequest(
                contents = contents,
                systemInstruction = systemInstruction,
                generationConfig = generationConfig
            )

            // Make the API call with retry + key fallback
            val responseText = retryWithBackoff(maxRetries = 3, initialDelayMs = 1000L) {
                val (response, error) = callGeminiWithKeyFallback(request)

                if (error != null) {
                    _lastError.value = error
                    throw Exception(error)
                }

                if (response == null) {
                    throw Exception("No response from Gemini")
                }

                val text = response.extractText()

                if (text.isNullOrBlank()) {
                    val blockReason = response.promptFeedback?.blockReason
                    val finishReason = response.candidates?.firstOrNull()?.finishReason
                    throw Exception(when {
                        blockReason != null -> "Response blocked: $blockReason"
                        finishReason == "SAFETY" -> "Response blocked by safety filters"
                        else -> "No response generated"
                    })
                }

                text
            }

            // Cache the response
            cacheResponse(key, responseText)

            // Add the model's response to history
            val modelMessage = ChatMessage(role = ChatMessage.ROLE_MODEL, content = responseText)
            _conversationHistory.value = updatedHistory + modelMessage

            _isLoading.value = false
            return responseText

        } catch (e: Exception) {
            val errorMsg = when {
                e.message?.contains("401") == true || e.message?.contains("403") == true ->
                    "Your Gemini API key was rejected. Please go to Settings and enter a valid key. Get a free key from aistudio.google.com"
                e.message?.contains("invalid", ignoreCase = true) == true ->
                    "Your Gemini API key appears to be invalid. Please update it in Settings. Get a free key from aistudio.google.com"
                e is java.net.UnknownHostException -> "No internet connection. Please check your network."
                e is java.net.SocketTimeoutException -> "Request timed out. Please try again."
                e is java.net.ConnectException -> "Cannot connect to Gemini. Please check your internet."
                e.message?.contains("API key", ignoreCase = true) == true -> e.message ?: "API key error. Please check Settings."
                else -> "I'm having trouble connecting. Please check your Gemini API key in Settings, or verify your internet connection."
            }
            _lastError.value = errorMsg
            _isLoading.value = false
            return errorMsg
        }
    }

    /**
     * Dedicated method for intent classification.
     * Uses low temperature and the classification system prompt for structured JSON output.
     * Does NOT add to conversation history or cache.
     */
    suspend fun classifyIntent(prompt: String): String {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            return "API key not configured. Please set your Gemini API key in Settings."
        }
        if (!apiKey.startsWith("AIza") || apiKey.length < 30) {
            return "API key invalid. Please enter a valid Gemini API key in Settings."
        }

        _isLoading.value = true

        return try {
            val contents = listOf(
                Content(
                    role = ChatMessage.ROLE_USER,
                    parts = listOf(Part(text = prompt))
                )
            )

            val request = GeminiRequest(
                contents = contents,
                systemInstruction = SystemInstruction(
                    parts = listOf(Part(text = CLASSIFICATION_SYSTEM_PROMPT))
                ),
                generationConfig = GenerationConfig(
                    temperature = 0.1f,  // Very low temperature for classification
                    topP = 0.8f,
                    maxOutputTokens = 200  // Short output for JSON
                )
            )

            val (response, error) = callGeminiWithKeyFallback(request)

            _isLoading.value = false
            if (error != null) return error
            return response?.extractText() ?: "No response generated."

        } catch (e: Exception) {
            _isLoading.value = false
            formatExceptionError(e)
        }
    }

    /**
     * Send a single message without maintaining conversation history.
     * Useful for quick one-off queries.
     */
    suspend fun queryOnce(message: String): String {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            return "API key not configured. Please set your Gemini API key in Settings."
        }
        if (!apiKey.startsWith("AIza") || apiKey.length < 30) {
            return "API key invalid. Please enter a valid Gemini API key in Settings."
        }

        _isLoading.value = true

        return try {
            val contents = listOf(
                Content(
                    role = ChatMessage.ROLE_USER,
                    parts = listOf(Part(text = message))
                )
            )

            val request = GeminiRequest(
                contents = contents,
                systemInstruction = SystemInstruction(
                    parts = listOf(Part(text = SYSTEM_PROMPT))
                ),
                generationConfig = GenerationConfig(
                    temperature = 0.3f,
                    maxOutputTokens = 256
                )
            )

            val (response, error) = callGeminiWithKeyFallback(request)

            _isLoading.value = false
            if (error != null) return error
            return response?.extractText() ?: "No response generated."

        } catch (e: Exception) {
            _isLoading.value = false
            formatExceptionError(e)
        }
    }

    /**
     * Get a conversational AI response with full context memory.
     * This is the main method for GENERAL_CHAT intents.
     */
    suspend fun chatWithMemory(
        message: String,
        conversationContext: List<ChatMessage>
    ): String {
        _isLoading.value = true
        _lastError.value = null

        try {
            val apiKeys = getApiKeys()
            if (apiKeys.isEmpty()) {
                val errorMsg = "I need a valid Gemini API key to respond. Please go to Settings and enter your Gemini API key. You can get a free key from aistudio.google.com"
                _lastError.value = errorMsg
                _isLoading.value = false
                return errorMsg
            }
            // Validate key format before making API call
            val primary = apiKeys.first()
            if (!primary.startsWith("AIza") || primary.length < 30) {
                val errorMsg = "Your Gemini API key appears to be invalid (should start with 'AIza'). Please update it in Settings. Get a free key from aistudio.google.com"
                _lastError.value = errorMsg
                _isLoading.value = false
                return errorMsg
            }

            // Build with conversation context (last 20 messages)
            val contextMessages = conversationContext.takeLast(MAX_CONTEXT_MESSAGES)
            val userMessage = ChatMessage(role = ChatMessage.ROLE_USER, content = message)
            val allMessages = contextMessages + userMessage

            val contents = allMessages.toContents()

            val systemInstruction = SystemInstruction(
                parts = listOf(Part(text = SYSTEM_PROMPT))
            )

            val generationConfig = GenerationConfig(
                temperature = 0.7f,
                topP = 0.95f,
                topK = 40,
                maxOutputTokens = 1024
            )

            val request = GeminiRequest(
                contents = contents,
                systemInstruction = systemInstruction,
                generationConfig = generationConfig
            )

            val responseText = retryWithBackoff(maxRetries = 2, initialDelayMs = 1000L) {
                val (response, error) = callGeminiWithKeyFallback(request)
                if (error != null) throw Exception(error)
                if (response == null) throw Exception("No response")
                val text = response.extractText()
                if (text.isNullOrBlank()) throw Exception("Empty response")
                text
            }

            _isLoading.value = false
            return responseText

        } catch (e: Exception) {
            val errorMsg = when {
                e.message?.contains("401") == true || e.message?.contains("403") == true ->
                    "Your Gemini API key was rejected. Please go to Settings and enter a valid key. Get a free key from aistudio.google.com"
                e.message?.contains("invalid", ignoreCase = true) == true ->
                    "Your Gemini API key appears to be invalid. Please update it in Settings. Get a free key from aistudio.google.com"
                e is java.net.UnknownHostException -> "No internet connection. Please check your network."
                e is java.net.SocketTimeoutException -> "Request timed out. Please try again."
                e.message?.contains("API key", ignoreCase = true) == true -> e.message ?: "API key error. Please check your Gemini API key in Settings."
                else -> "I'm having trouble connecting. This might be an API key issue — please check your Gemini API key in Settings, or check your internet connection."
            }
            _lastError.value = errorMsg
            _isLoading.value = false
            return errorMsg
        }
    }
}
