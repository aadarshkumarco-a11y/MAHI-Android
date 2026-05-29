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
 * Gemini 1.5 Flash API. It handles:
 * - API key retrieval from SettingsManager (SharedPreferences)
 * - Conversation history management
 * - System prompt configuration (MAHI persona)
 * - Error handling and retry logic
 * - Loading state exposure via StateFlow
 */
class AiConversationEngine(
    private val settingsManager: SettingsManager
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/"

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

            // Make the API call
            val response = apiService.generateContent(
                apiKey = apiKey,
                request = request
            )

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

        } catch (e: HttpException) {
            val errorMsg = when (e.code()) {
                400 -> "Bad request. Please check your input."
                401 -> "Invalid API key. Please update your Gemini API key in Settings."
                403 -> "Access forbidden. Check your API key permissions."
                429 -> "Rate limit exceeded. Please wait and try again."
                500 -> "Gemini server error. Please try again later."
                503 -> "Gemini service unavailable. Please try again later."
                else -> "Network error (${e.code()}): ${e.message()}"
            }
            _lastError.value = errorMsg
            _isLoading.value = false
            return errorMsg

        } catch (e: java.net.UnknownHostException) {
            val errorMsg = "No internet connection. Please check your network."
            _lastError.value = errorMsg
            _isLoading.value = false
            return errorMsg

        } catch (e: java.net.SocketTimeoutException) {
            val errorMsg = "Request timed out. Please try again."
            _lastError.value = errorMsg
            _isLoading.value = false
            return errorMsg

        } catch (e: Exception) {
            val errorMsg = "Error: ${e.message ?: "Unknown error"}"
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

            val response = apiService.generateContent(
                apiKey = apiKey,
                request = request
            )

            _isLoading.value = false
            response.extractText() ?: "No response generated."

        } catch (e: Exception) {
            _isLoading.value = false
            "Error: ${e.message}"
        }
    }
}
