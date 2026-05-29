package com.mahi.assistant.ai

import com.mahi.assistant.data.local.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.google.gson.GsonBuilder

/**
 * AiConversationEngine manages the full conversation lifecycle with the
 * Gemini API. It handles:
 * - API key retrieval from SettingsManager (SharedPreferences)
 * - Conversation history management
 * - System prompt configuration (MAHI persona)
 * - Automatic model fallback (tries gemini-2.0-flash → gemini-1.5-flash → gemini-pro)
 * - Error handling with user-friendly messages
 * - Loading state exposure via StateFlow
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

        val SYSTEM_PROMPT = """
            You are MAHI, an ultra-intelligent AI assistant inspired by Jarvis from Iron Man.
            You are helpful, witty, and always ready. Keep responses concise but informative.
            You can control device features, search the web, play YouTube, read notifications, and more.
            Always identify yourself as MAHI. Never break character.
            When asked who you are, say you are MAHI, the most advanced personal AI assistant.
            You speak in a confident, friendly tone with occasional wit.
            If a user asks you to do something you cannot directly perform (like calling someone),
            provide helpful guidance on how they can do it or indicate you are passing the command
            to the appropriate handler.
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

    /**
     * Retrieve the stored Gemini API key from SettingsManager.
     */
    private fun getApiKey(): String {
        return settingsManager.getGeminiApiKey()
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
     * Try calling the Gemini API with multiple model endpoints as fallback.
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
                // For other errors (401, 403, 429), don't bother trying other models
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
        return when (e) {
            is java.net.UnknownHostException -> "No internet connection. Please check your network."
            is java.net.SocketTimeoutException -> "Request timed out. Please try again."
            is java.net.ConnectException -> "Cannot connect to Gemini. Please check your internet."
            else -> "Connection error. Please check your internet and try again."
        }
    }

    /**
     * Send a user message to the Gemini API and return the model's text response.
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
                val errorMsg = "API key not configured. Please set your Gemini API key in Settings."
                _lastError.value = errorMsg
                _isLoading.value = false
                return errorMsg
            }

            // Build the user message and add to history
            val userMessage = ChatMessage(role = ChatMessage.ROLE_USER, content = message)
            val updatedHistory = history + userMessage
            _conversationHistory.value = updatedHistory

            // Build the request
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

            // Make the API call with model fallback
            val (response, error) = callGeminiApi(apiKey, request)

            if (error != null) {
                _lastError.value = error
                _isLoading.value = false
                return error
            }

            if (response == null) {
                val errorMsg = "No response from Gemini. Please try again."
                _lastError.value = errorMsg
                _isLoading.value = false
                return errorMsg
            }

            // Extract the response text
            val responseText = response.extractText()

            if (responseText.isNullOrBlank()) {
                val blockReason = response.promptFeedback?.blockReason
                val finishReason = response.candidates?.firstOrNull()?.finishReason
                val errorMsg = when {
                    blockReason != null -> "Response blocked: $blockReason"
                    finishReason == "SAFETY" -> "Response blocked by safety filters"
                    else -> "No response generated. Please try again."
                }
                _lastError.value = errorMsg
                _isLoading.value = false
                return errorMsg
            }

            // Add the model's response to history
            val modelMessage = ChatMessage(role = ChatMessage.ROLE_MODEL, content = responseText)
            _conversationHistory.value = updatedHistory + modelMessage

            _isLoading.value = false
            return responseText

        } catch (e: Exception) {
            val errorMsg = formatExceptionError(e)
            _lastError.value = errorMsg
            _isLoading.value = false
            return errorMsg
        }
    }

    /**
     * Send a single message without maintaining conversation history.
     * Useful for quick one-off queries or intent classification.
     */
    suspend fun queryOnce(message: String): String {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            return "API key not configured."
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

            val (response, error) = callGeminiApi(apiKey, request)

            _isLoading.value = false
            if (error != null) return error
            return response?.extractText() ?: "No response generated."

        } catch (e: Exception) {
            _isLoading.value = false
            formatExceptionError(e)
        }
    }
}
