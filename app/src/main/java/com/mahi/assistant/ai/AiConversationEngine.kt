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
 * JARVIS-LEVEL AiConversationEngine — DUAL AI Backend + Strong Memory.
 *
 * AI Fallback Chain:
 * 1. Gemini (primary) — try all model endpoints + all keys
 * 2. Grok (xAI) — AUTOMATIC fallback when Gemini fails
 *
 * Memory System:
 * - 50 messages context (up from 20)
 * - Enhanced system prompt with memory awareness
 * - Conversation history persisted in Room DB
 * - Response caching (5 min TTL)
 *
 * Resilience:
 * - Retry with exponential backoff (1s, 2s, 4s)
 * - Multi-key fallback for Gemini
 * - Automatic Grok fallback when ALL Gemini keys fail
 */
class AiConversationEngine(
    private val settingsManager: SettingsManager
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/"

        // Gemini model endpoints to try in order
        val MODEL_ENDPOINTS = listOf(
            "models/gemini-2.0-flash:generateContent",
            "models/gemini-1.5-flash:generateContent",
            "models/gemini-1.5-flash-latest:generateContent",
            "models/gemini-pro:generateContent"
        )

        // Grok models to try in order
        val GROK_MODELS = listOf(
            "grok-3-mini",
            "grok-3",
            "grok-2"
        )

        // Maximum messages to pass as context — INCREASED for stronger memory
        private const val MAX_CONTEXT_MESSAGES = 50

        // Cache TTL in milliseconds (5 minutes)
        private const val CACHE_TTL_MS = 5 * 60 * 1000L

        val SYSTEM_PROMPT = """
You are MAHI, an ultra-intelligent AI assistant inspired by Jarvis from Iron Man.
You are the most advanced personal AI assistant ever built.

CORE TRAITS:
- Helpful, witty, and always ready to assist
- You respond in the SAME LANGUAGE the user speaks (Hindi, English, Hinglish, etc.)
- If they speak in Hindi/Hinglish, respond in Hindi/Hinglish
- If they speak in English, respond in English
- Keep responses concise but informative (2-3 sentences for simple queries, more for complex ones)
- You can control device features, search the web, play YouTube, read notifications, and more
- Always identify yourself as MAHI. Never break character
- When asked who you are, say you are MAHI, the most advanced personal AI assistant
- You speak in a confident, friendly tone with occasional wit
- You are NOT just a chatbot — you are an ACTION assistant

MEMORY SYSTEM — CRITICAL:
- You have access to the FULL conversation history below
- REMEMBER everything the user tells you — their name, preferences, past requests
- If the user says "yaad rakhna" or "remember this", STORE it in your response
- If the user asks about something discussed earlier, REFERENCE it accurately
- Track the user's name, location, contacts, preferences across the conversation
- When they say "mujhe apna naam batao" tell them their name if you know it
- When they say "kya maine kal kuch kaha tha?" reference previous messages
- NEVER say "I don't remember" if the information is in the conversation history
- Build a mental model of the user across conversations

LANGUAGE UNDERSTANDING:
- You understand Hindi, English, Hinglish, and other Indian languages naturally
- "kya hal hai" = "how are you", "aaj ka mausam" = "today's weather"
- "call karo" = "make a call", "message bhejo" = "send message"
- Understand partial words, slang, abbreviations, typos
- "whatsapp pe msg karo" = send WhatsApp message
- "yt pe video chalao" = play YouTube video
- "flash on karo" = turn on flashlight
- Always respond in the SAME language/style the user uses

IMPORTANT: If a user asks you to do something you cannot directly perform, provide helpful guidance.
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
    private val grokService: GrokApiService = GrokClient.apiService

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _conversationHistory = MutableStateFlow<List<ChatMessage>>(emptyList())
    val conversationHistory: StateFlow<List<ChatMessage>> = _conversationHistory.asStateFlow()

    // Remember which model worked last time to skip straight to it
    private var lastWorkingEndpoint: String? = null
    private var lastWorkingGrokModel: String? = null

    // Response cache: query hash → (response, timestamp)
    private val responseCache = mutableMapOf<String, Pair<String, Long>>()

    /**
     * Retrieve the stored Gemini API key from SettingsManager.
     */
    private fun getApiKeys(): List<String> {
        val raw = settingsManager.getGeminiApiKey()
        if (raw.isBlank()) return emptyList()
        return raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
    }

    private fun getApiKey(): String = getApiKeys().firstOrNull() ?: ""

    private fun getGrokApiKey(): String = settingsManager.getGrokApiKey()

    /**
     * Check if ANY AI backend is configured (Gemini OR Grok).
     */
    fun isConfigured(): Boolean = isGeminiConfigured() || isGrokConfigured()

    fun isGeminiConfigured(): Boolean {
        val key = getApiKey()
        return key.isNotBlank() && key.startsWith("AIza") && key.length >= 30
    }

    fun isGrokConfigured(): Boolean = getGrokApiKey().isNotBlank()

    fun isKeySet(): Boolean = getApiKey().isNotBlank() || getGrokApiKey().isNotBlank()

    fun clearHistory() { _conversationHistory.value = emptyList() }

    fun addToHistory(message: ChatMessage) {
        val current = _conversationHistory.value.toMutableList()
        current.add(message)
        _conversationHistory.value = current
    }

    fun loadContext(messages: List<ChatMessage>) {
        _conversationHistory.value = messages.takeLast(MAX_CONTEXT_MESSAGES)
    }

    // ══════════════════════════════════════════════════════════════════════
    // GEMINI API — Primary Backend
    // ══════════════════════════════════════════════════════════════════════

    private suspend fun callGeminiApi(
        apiKey: String,
        request: GeminiRequest
    ): Pair<GeminiResponse?, String?> {
        val endpoints = mutableListOf<String>()
        lastWorkingEndpoint?.let { endpoints.add(it) }
        MODEL_ENDPOINTS.forEach { ep -> if (ep !in endpoints) endpoints.add(ep) }

        var lastErrorCode: Int? = null

        for (endpoint in endpoints) {
            try {
                val response = apiService.generateContent(endpoint, apiKey, request)
                lastWorkingEndpoint = endpoint
                return Pair(response, null)
            } catch (e: HttpException) {
                lastErrorCode = e.code()
                if (e.code() == 404) continue
                if (e.code() == 401 || e.code() == 403) {
                    return Pair(null, formatGeminiHttpError(e.code()))
                }
                return Pair(null, formatGeminiHttpError(e.code()))
            } catch (e: Exception) {
                return Pair(null, formatExceptionError(e))
            }
        }
        return Pair(null, "Gemini unavailable (Error: ${lastErrorCode ?: "unknown"})")
    }

    private suspend fun callGeminiWithKeyFallback(request: GeminiRequest): Pair<GeminiResponse?, String?> {
        val apiKeys = getApiKeys()
        if (apiKeys.isEmpty()) return Pair(null, "NO_GEMINI_KEY")

        for (apiKey in apiKeys) {
            val (response, error) = callGeminiApi(apiKey, request)
            if (response != null) return Pair(response, null)
            if (error != null && (error.contains("invalid") || error.contains("denied") || error.contains("401") || error.contains("403"))) {
                continue // Try next key
            }
            if (error != null) return Pair(null, error)
        }
        return Pair(null, "ALL_GEMINI_KEYS_FAILED")
    }

    // ══════════════════════════════════════════════════════════════════════
    // GROK (xAI) — AUTOMATIC FALLBACK Backend
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Call Grok API as fallback when Gemini fails.
     * Uses OpenAI-compatible chat completions format.
     */
    private suspend fun callGrokApi(
        messages: List<GrokMessage>,
        model: String = "grok-3-mini",
        temperature: Double = 0.7,
        maxTokens: Int = 1024
    ): Pair<String?, String?> {
        val grokKey = getGrokApiKey()
        if (grokKey.isBlank()) return Pair(null, "NO_GROK_KEY")

        // Try models in order, starting with last working
        val models = mutableListOf<String>()
        lastWorkingGrokModel?.let { models.add(it) }
        GROK_MODELS.forEach { m -> if (m !in models) models.add(m) }

        for (tryModel in models) {
            try {
                val request = GrokRequest(
                    model = tryModel,
                    messages = messages,
                    temperature = temperature,
                    max_tokens = maxTokens
                )
                val response = grokService.chatCompletions("Bearer $grokKey", request)
                lastWorkingGrokModel = tryModel
                val text = response.extractText()
                return if (text.isNullOrBlank()) Pair(null, "Grok returned empty response") else Pair(text, null)
            } catch (e: HttpException) {
                if (e.code() == 404) continue // Try next model
                if (e.code() == 401 || e.code() == 403) {
                    return Pair(null, "Grok API key rejected (${e.code()})")
                }
                return Pair(null, "Grok error (${e.code()})")
            } catch (e: Exception) {
                return Pair(null, formatExceptionError(e))
            }
        }
        return Pair(null, "ALL_GROK_MODELS_FAILED")
    }

    /**
     * Convert ChatMessage list to Grok message format.
     */
    private fun List<ChatMessage>.toGrokMessages(systemPrompt: String): List<GrokMessage> {
        val result = mutableListOf(GrokMessage(role = "system", content = systemPrompt))
        for (msg in this) {
            val role = when (msg.role) {
                ChatMessage.ROLE_USER -> "user"
                ChatMessage.ROLE_MODEL -> "assistant"
                else -> "user"
            }
            result.add(GrokMessage(role = role, content = msg.content))
        }
        return result
    }

    // ══════════════════════════════════════════════════════════════════════
    // DUAL-BACKEND CALL — Gemini → Grok Auto-Fallback
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Try Gemini first, then automatically fall back to Grok if Gemini fails.
     * This is the CORE method that ensures MAHI always responds.
     */
    private suspend fun callWithDualFallback(
        geminiRequest: GeminiRequest,
        chatMessages: List<ChatMessage>,
        systemPrompt: String = SYSTEM_PROMPT,
        temperature: Double = 0.7,
        maxTokens: Int = 1024
    ): String {
        // ── TRY GEMINI FIRST ──────────────────────────────────
        if (isGeminiConfigured()) {
            val (response, geminiError) = callGeminiWithKeyFallback(geminiRequest)
            if (response != null) {
                val text = response.extractText()
                if (!text.isNullOrBlank()) return text
            }
            // Gemini failed — log and try Grok
            _lastError.value = "Gemini failed: $geminiError — trying Grok fallback..."
        }

        // ── GROK FALLBACK ─────────────────────────────────────
        if (isGrokConfigured()) {
            val grokMessages = chatMessages.toGrokMessages(systemPrompt)
            val (grokText, grokError) = callGrokApi(
                messages = grokMessages,
                temperature = temperature,
                maxTokens = maxTokens
            )
            if (grokText != null) return grokText

            _lastError.value = "Both AI backends failed. Gemini error + Grok: $grokError"
        }

        // ── BOTH FAILED ───────────────────────────────────────
        return if (!isGeminiConfigured() && !isGrokConfigured()) {
            "No AI backend configured. Please set your Gemini API key or Grok API key in Settings."
        } else {
            "I'm having trouble connecting to my AI backends. Please check your internet connection or API keys in Settings."
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // PUBLIC METHODS
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Send a user message with full conversation memory.
     * Uses Gemini first, then Grok as automatic fallback.
     */
    suspend fun sendMessage(
        message: String,
        history: List<ChatMessage> = _conversationHistory.value
    ): String {
        _isLoading.value = true
        _lastError.value = null

        try {
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

            // Build Gemini request with conversation context
            val contents = updatedHistory.toContents()
            val systemInstruction = SystemInstruction(parts = listOf(Part(text = SYSTEM_PROMPT)))
            val generationConfig = GenerationConfig(temperature = 0.7f, topP = 0.95f, topK = 40, maxOutputTokens = 1024)
            val request = GeminiRequest(contents = contents, systemInstruction = systemInstruction, generationConfig = generationConfig)

            // Try Gemini → Grok fallback
            val responseText = retryWithBackoff(maxRetries = 2, initialDelayMs = 1000L) {
                callWithDualFallback(request, updatedHistory)
            }

            // Cache and store
            cacheResponse(key, responseText)
            val modelMessage = ChatMessage(role = ChatMessage.ROLE_MODEL, content = responseText)
            _conversationHistory.value = updatedHistory + modelMessage

            _isLoading.value = false
            return responseText

        } catch (e: Exception) {
            _lastError.value = formatExceptionError(e)
            _isLoading.value = false
            return formatExceptionError(e)
        }
    }

    /**
     * Classify intent using AI — Gemini first, then Grok fallback.
     */
    suspend fun classifyIntent(prompt: String): String {
        _isLoading.value = true

        // Try Gemini first
        if (isGeminiConfigured()) {
            try {
                val contents = listOf(Content(role = ChatMessage.ROLE_USER, parts = listOf(Part(text = prompt))))
                val request = GeminiRequest(
                    contents = contents,
                    systemInstruction = SystemInstruction(parts = listOf(Part(text = CLASSIFICATION_SYSTEM_PROMPT))),
                    generationConfig = GenerationConfig(temperature = 0.1f, topP = 0.8f, maxOutputTokens = 200)
                )
                val (response, error) = callGeminiWithKeyFallback(request)
                if (response != null) {
                    val text = response.extractText()
                    if (!text.isNullOrBlank()) {
                        _isLoading.value = false
                        return text
                    }
                }
            } catch (_: Exception) { }
        }

        // Grok fallback for classification
        if (isGrokConfigured()) {
            try {
                val grokMessages = listOf(
                    GrokMessage(role = "system", content = CLASSIFICATION_SYSTEM_PROMPT),
                    GrokMessage(role = "user", content = prompt)
                )
                val (text, _) = callGrokApi(messages = grokMessages, temperature = 0.1, maxTokens = 200)
                if (text != null) {
                    _isLoading.value = false
                    return text
                }
            } catch (_: Exception) { }
        }

        _isLoading.value = false
        return "CLASSIFICATION_FAILED"
    }

    /**
     * Chat with full memory context — Gemini → Grok fallback.
     */
    suspend fun chatWithMemory(
        message: String,
        conversationContext: List<ChatMessage>
    ): String {
        _isLoading.value = true
        _lastError.value = null

        try {
            val contextMessages = conversationContext.takeLast(MAX_CONTEXT_MESSAGES)
            val userMessage = ChatMessage(role = ChatMessage.ROLE_USER, content = message)
            val allMessages = contextMessages + userMessage

            // Build Gemini request
            val contents = allMessages.toContents()
            val systemInstruction = SystemInstruction(parts = listOf(Part(text = SYSTEM_PROMPT)))
            val generationConfig = GenerationConfig(temperature = 0.7f, topP = 0.95f, topK = 40, maxOutputTokens = 1024)
            val request = GeminiRequest(contents = contents, systemInstruction = systemInstruction, generationConfig = generationConfig)

            // Try Gemini → Grok fallback with retry
            val responseText = retryWithBackoff(maxRetries = 2, initialDelayMs = 1000L) {
                callWithDualFallback(request, allMessages)
            }

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
     * Quick one-off query — no history maintained.
     */
    suspend fun queryOnce(message: String): String {
        _isLoading.value = true

        try {
            // Gemini attempt
            if (isGeminiConfigured()) {
                val contents = listOf(Content(role = ChatMessage.ROLE_USER, parts = listOf(Part(text = message))))
                val request = GeminiRequest(
                    contents = contents,
                    systemInstruction = SystemInstruction(parts = listOf(Part(text = SYSTEM_PROMPT))),
                    generationConfig = GenerationConfig(temperature = 0.3f, maxOutputTokens = 256)
                )
                val (response, _) = callGeminiWithKeyFallback(request)
                if (response != null) {
                    val text = response.extractText()
                    if (!text.isNullOrBlank()) { _isLoading.value = false; return text }
                }
            }

            // Grok fallback
            if (isGrokConfigured()) {
                val grokMessages = listOf(
                    GrokMessage(role = "system", content = SYSTEM_PROMPT),
                    GrokMessage(role = "user", content = message)
                )
                val (text, _) = callGrokApi(messages = grokMessages, temperature = 0.3, maxTokens = 256)
                if (text != null) { _isLoading.value = false; return text }
            }

            _isLoading.value = false
            return "No AI backend available. Please configure an API key in Settings."

        } catch (e: Exception) {
            _isLoading.value = false
            return formatExceptionError(e)
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════════════

    private fun formatGeminiHttpError(code: Int): String = when (code) {
        400 -> "Bad request. Try rephrasing."
        401 -> "GEMINI_KEY_INVALID"
        403 -> "GEMINI_ACCESS_DENIED"
        404 -> "Gemini model not found."
        429 -> "Gemini rate limit. Wait a moment."
        500 -> "Gemini server error. Try again."
        503 -> "Gemini unavailable. Try again later."
        else -> "Gemini error ($code)."
    }

    private fun formatExceptionError(e: Exception): String = when (e) {
        is java.net.UnknownHostException -> "No internet connection."
        is java.net.SocketTimeoutException -> "Request timed out."
        is java.net.ConnectException -> "Cannot connect to AI service."
        else -> "Connection error: ${e.message?.take(100) ?: "unknown"}"
    }

    private fun cacheKey(message: String, context: List<ChatMessage>): String {
        val contextStr = context.takeLast(5).joinToString("|") { "${it.role}:${it.content}" }
        return "$contextStr|$message"
    }

    private fun getCachedResponse(key: String): String? {
        val cached = responseCache[key] ?: return null
        if (System.currentTimeMillis() - cached.second > CACHE_TTL_MS) {
            responseCache.remove(key)
            return null
        }
        return cached.first
    }

    private fun cacheResponse(key: String, response: String) {
        if (responseCache.size > 50) {
            val oldest = responseCache.minByOrNull { it.value.second }?.key
            oldest?.let { responseCache.remove(it) }
        }
        responseCache[key] = Pair(response, System.currentTimeMillis())
    }

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
                    delay(initialDelayMs * (1L shl attempt))
                }
            }
        }
        throw lastException ?: Exception("Unknown error during retry")
    }
}
