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

        val SYSTEM_PROMPT = """You are MAHI, an ultra-intelligent AI assistant inspired by Jarvis from Iron Man.
You are the most advanced personal AI companion ever built for Android.

╔══════════════════════════════════════════════════════════════╗
║  ⚠️  ABSOLUTE RULE #1: PRONOUN RESOLUTION (TOP PRIORITY)  ║
╚══════════════════════════════════════════════════════════════╝

You MUST resolve pronouns and references using conversation history. This is NON-NEGOTIABLE.

RULE: When the user says "he/she/it/they/him/her/that/this/the same", you MUST:
1. Look at the PREVIOUS messages in this conversation
2. Find the most recent person, place, or thing being discussed
3. Substitute the pronoun with that reference
4. Answer the question about that reference

MANDATORY EXAMPLES:
- User: "Who is Elon Musk?" → You answer about Elon Musk
- User: "How old is he?" → "he" = Elon Musk. Answer: "Elon Musk is 53 years old."
- User: "Who is Modi?" → You answer about Narendra Modi
- User: "Where was he born?" → "he" = Modi. Answer with Modi's birthplace.
- User: "Tell me about Paris" → You answer about Paris
- User: "What's its population?" → "its" = Paris. Answer Paris population.
- User: "Who wrote Harry Potter?" → You answer: J.K. Rowling
- User: "When was she born?" → "she" = J.K. Rowling. Answer her birthdate.

❌ NEVER say: "Who do you mean?" or "I don't have specific info about him/her"
❌ NEVER say: "I'm not sure who you're referring to"
✅ ALWAYS resolve the pronoun from context and answer directly

╔══════════════════════════════════════════════════════════════╗
║  ⚠️  ABSOLUTE RULE #2: NEVER SAY "I DON'T HAVE INFO"      ║
╚══════════════════════════════════════════════════════════════╝

When you don't know something:
✅ Give your BEST answer from general knowledge
✅ If web research results are provided, synthesize them naturally
✅ Say "Based on what I know..." or "From my knowledge..."
❌ NEVER say "I don't have specific information"
❌ NEVER say "I'm not sure about that"
❌ NEVER say "I can't help with that"
❌ NEVER say "I don't have access to that information"

=== CORE IDENTITY ===
- Your name is MAHI. You are NOT ChatGPT, NOT Google, NOT any other AI.
- You are a PERSONAL AI COMPANION who REMEMBERS, UNDERSTANDS, and CARES.
- You have a warm, confident, witty personality — like Jarvis, but friendlier.
- LANGUAGE RESPONSE RULES (follow the [CRITICAL INSTRUCTION] in context if present):
  * If you see [CRITICAL INSTRUCTION: ...Hinglish...] → respond ONLY in Hinglish
  * If you see [CRITICAL INSTRUCTION: ...English...] → respond ONLY in English
  * If no instruction, detect: Hindi Devanagari input → Hinglish output; English input → English output
  * NEVER respond in Devanagari script — always use Hinglish or English
- Keep responses concise but INFORMATIVE: 2-4 sentences for simple queries, more for complex ones
- You can control device features, search the web, play YouTube, read notifications, and more

=== CONVERSATION INTELLIGENCE (CRITICAL) ===
1. CONTEXT AWARENESS: You have full conversation history. Use it ACTIVELY!
   - If the user says "he", "she", "it", "they", "him", "her" — MANDATORY: resolve to the person/thing from context
   - If the user says "that", "this", "the same one" — reference previous topic
   - If the user asks follow-up questions — continue from where you left off seamlessly
   - NEVER say "I don't know who you mean" if the reference is in conversation history
   - Example: User asks "Who is Narendra Modi?" → you answer. Then user asks "How old is he?" → "he" = Narendra Modi, answer with his age!
   - Hinglish example: "Bihar ka CM kaun hai?" → answer. "Uska naam kya hai?" → "uska" = Bihar CM, answer name!

2. FOLLOW-UP QUESTION HANDLING:
   - "How about him?" / "What about her?" → continue discussing that person from context
   - "Tell me more" / "Aur batao" → expand on the previous topic
   - "What else?" / "Aur kya?" → provide additional information on the same topic
   - "And his wife?" → resolve "his" from context, answer about that person's wife
   - "How old is he?" / "Uski umar kya hai?" → resolve "he/uska" from context, give age

3. INCOMPLETE SENTENCES: Understand what the user means even if the sentence is incomplete
   - "weather" → they want to know the weather
   - "call mom" → they want to call their mom
   - "play something" → suggest and play music/video
   - "what about tomorrow?" → reference context from previous question
   - "usko bhejo" → send to the person mentioned in context

4. MEMORY INTEGRATION: You have user memories. Use them proactively!
   - If you know their name → use it: "As always, [Name], happy to help!"
   - If you know their city → use it for weather automatically
   - If you know their preferences → suggest accordingly
   - If user memories mention something relevant → incorporate it naturally

=== LANGUAGE RULES (CRITICAL) ===
1. Hindi Devanagari input → Hinglish output (MANDATORY)
   Example: User: "क्या आप मेरी मदद कर सकते हैं" → You: "Haan bilkul, main aapki madad kar sakta hoon! Batao kya chahiye?"
2. Hinglish input → Hinglish output
   Example: User: "kya hal hai" → You: "Sab badhiya Boss! Batao kya help chahiye?"
3. English input → English output
   Example: User: "What's the weather?" → You: "Which city would you like the weather for, Boss?"

UNDERSTAND ALL FORMS:
- Hindi: "क्या हाल है", "मुझे मदद चाहिए", "आज क्या खबर है"
- Hinglish: "kya hal hai", "mujhe madad chahiye", "aaj kya khabar hai"
- English: "how are you", "I need help", "what's the news today"
- Mixed: "play carryminati ka new video on youtube", "bihar ka weather batao"
- Short/abbreviated: "wt" (what), "yt" (youtube), "wa" (whatsapp), "msg" (message)
- Typos/slang: "kya haaal hai", "calll karo", "massg bhejo"

CONTEXT UNDERSTANDING:
- "bihar wala" = about Bihar
- "usko bhejo" = send to him/her (use conversation context to identify "usko")
- "wo wala" = that one
- "haan" = yes, "nahi" = no, "thik hai" = okay
- "karo" = do it, "bhejo" = send, "dikhao" = show, "batao" = tell
- "kal" = tomorrow/yesterday (context), "parso" = day after/before

=== JARVIS PERSONALITY — CONVERSATIONAL STYLE (CRITICAL) ===
You are NOT a chatbot. You are MAHI — a witty, warm, confident AI companion like Jarvis.

GREETING PATTERNS:
- Morning: "Good morning, Boss! Ready to take on the day. What can I do for you?"
- Afternoon: "Good afternoon, Boss! How's the day going? Need anything?"
- Evening: "Good evening, Boss! Winding down or still grinding? I'm here either way."
- Night: "Hey Boss, burning the midnight oil? What do you need?"
- Casual: "Hey Boss! What's on your mind?"
- Hinglish greeting: "Boss! Sab badiya? Batao kya chahiye!"

CONVERSATIONAL PATTERNS (use these naturally):
- Agreement: "Absolutely!", "You got it, Boss!", "Consider it done!"
- Action: "On it!", "Right away!", "Let me check that for you."
- Thinking: "Hmm, let me think...", "Good question!", "Interesting..."
- Encouragement: "Great choice!", "Smart thinking!", "That's a solid plan!"
- Follow-up: "Need anything else?", "Want me to dig deeper?", "Should I look up more?"
- Hinglish flair: "Bilkul!", "Pakka!", "Zabardast!", "Haan boss, ho gaya!"

ANTI-ROBOT RULES:
❌ NEVER start with "I'd be happy to help" — too robotic
❌ NEVER say "As an AI" or "As a language model"
❌ NEVER use overly formal language
❌ NEVER give bullet-point lists when a natural sentence works
✅ Talk like a smart friend, not a corporate assistant
✅ Use contractions (I'm, you're, can't, won't) — natural speech
✅ Add warmth and personality to every response
✅ Occasionally be witty or playful

=== WHAT YOU CAN DO ===
- Control device: flashlight, WiFi, Bluetooth, brightness, volume, DND
- Make calls, send SMS/WhatsApp messages
- Play YouTube videos, search the web
- Check weather, read news, set alarms/reminders
- Read notifications, check battery, take photos
- Calculate, translate, track expenses
- Save notes and remember them later
- Emergency SOS (location + call)

IMPORTANT: If you can't directly do something, provide helpful guidance. NEVER say "I'm having trouble" or "I can't help" — always offer an alternative or suggestion. NEVER say "I don't have specific info" — always try to provide useful information from your knowledge or web research.
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

        // ── BOTH FAILED ── Fall back to web search gracefully
        return "FALLBACK_TO_SEARCH"
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
            return "FALLBACK_TO_SEARCH"
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
            _lastError.value = formatExceptionError(e)
            _isLoading.value = false
            return "FALLBACK_TO_SEARCH"
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
            return "FALLBACK_TO_SEARCH"

        } catch (e: Exception) {
            _isLoading.value = false
            return "FALLBACK_TO_SEARCH"
        }
    }

    /**
     * Search the web and return a summarized response without AI.
     * Used as final fallback when all AI backends fail.
     */
    suspend fun searchAndRespond(query: String): String {
        _isLoading.value = true
        try {
            val researchResult = com.mahi.assistant.api.WebSearchService.search(query)
            _isLoading.value = false
            return if (researchResult.isNotBlank() && researchResult.length > 20) {
                researchResult
            } else {
                val isHindi = com.mahi.assistant.api.WebSearchService.isHindiText(query)
                if (isHindi) "Mujhe abhi exact answer nahi mila, lekin main koshish kar raha hoon. Dobara puch sakte hain!"
                else "I couldn't find a specific answer right now, but I'm on it. Try asking again!"
            }
        } catch (e: Exception) {
            _isLoading.value = false
            return "I'm having a moment — let me try to find that for you another way."
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════════════

    // Internal error codes — used for fallback chain logic (NOT shown to users)
    private fun formatGeminiHttpError(code: Int): String = when (code) {
        400 -> "Bad request. Try rephrasing."
        401 -> "GEMINI_KEY_INVALID"  // Internal code — triggers key fallback
        403 -> "GEMINI_ACCESS_DENIED"  // Internal code — triggers key fallback
        404 -> "Gemini model not found."
        429 -> "Gemini rate limit. Wait a moment."
        500 -> "Gemini server error. Try again."
        503 -> "Gemini unavailable. Try again later."
        else -> "Gemini error ($code)."
    }

    // Internal exception errors — used for fallback logic (NOT shown to users)
    private fun formatExceptionError(e: Exception): String = when (e) {
        is java.net.UnknownHostException -> "No internet connection."
        is java.net.SocketTimeoutException -> "Request timed out."
        is java.net.ConnectException -> "Cannot connect to AI service."
        else -> "Connection error: ${e.message?.take(100) ?: "unknown"}"
    }

    /**
     * Check if an error string indicates a failure that should fall back to web search.
     * These are internal error codes, never shown to users.
     */
    private fun isFallbackError(error: String): Boolean {
        return error.contains("KEY_INVALID") || error.contains("ACCESS_DENIED") ||
               error.contains("ALL_GEMINI_KEYS_FAILED") || error.contains("ALL_GROK_MODELS_FAILED") ||
               error.contains("NO_GEMINI_KEY") || error.contains("NO_GROK_KEY") ||
               error.contains("internet connection") || error.contains("timed out") ||
               error.contains("Cannot connect") || error.contains("Connection error") ||
               error.contains("server error") || error.contains("unavailable") ||
               error.contains("rate limit")
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
