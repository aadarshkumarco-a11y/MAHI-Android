package com.mahi.assistant.ai

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * MAX INTELLIGENCE IntentClassifier.
 *
 * Strategy:
 * 1. Fast path: Regex patterns for common commands (instant, no API call)
 * 2. Smart path: Gemini AI classifies the intent for ANY natural language input
 *
 * This means MAHI understands "play carryminati latest video on youtube",
 * "text ayush in WhatsApp that he needs to call me", "aaj ka mausam kaisa hai",
 * "top 10 bihar breaking news" — ANY phrasing!
 */
class IntentClassifier(
    private val aiEngine: AiConversationEngine? = null
) {

    enum class IntentType {
        DEVICE_CONTROL,
        WEATHER,
        NEWS,
        YOUTUBE,
        CALL,
        SMS,
        WHATSAPP,
        ALARM,
        REMINDER,
        ROUTINE,
        CALENDAR,
        NOTIFICATION,
        APP_LAUNCH,
        WEB_SEARCH,
        MEDIA_CONTROL,
        LOCATION,
        BATTERY,
        CALL_LOG,
        TIME_DATE,
        FIND_PHONE,
        GENERAL_CHAT
    }

    data class IntentResult(
        val type: IntentType,
        val action: String,
        val params: Map<String, String> = emptyMap(),
        val response: String? = null
    )

    // ──────────────────────────────────────────────────────────────────────────
    // Quick-match patterns (FAST PATH — no AI call needed)
    // These are the most common, unambiguous commands.
    // ──────────────────────────────────────────────────────────────────────────

    private data class QuickPattern(
        val type: IntentType,
        val action: String,
        val pattern: Regex,
        val paramExtractor: ((MatchResult, String) -> Map<String, String>)? = null
    )

    private val quickPatterns: List<QuickPattern> = listOf(

        // ── YOUTUBE (specific search) ──────────────────────────────────────
        QuickPattern(IntentType.YOUTUBE, "search_youtube",
            Regex("(?i)\\b(?:play|watch|search|find|open)\\s+(.+?)\\s+(?:on\\s+)?(?:youtube|yt)\\b"),
            { m, _ -> mapOf("query" to m.groupValues[1].trim()) }
        ),
        QuickPattern(IntentType.YOUTUBE, "search_youtube",
            Regex("(?i)\\byoutube\\s+(?:pe\\s+)?(?:play|search|find|watch)\\s+(.+)\\b"),
            { m, _ -> mapOf("query" to m.groupValues[1].trim()) }
        ),
        QuickPattern(IntentType.YOUTUBE, "open_youtube",
            Regex("(?i)\\b(?:open|launch|start)\\s+(?:the\\s+)?youtube\\b"),
            { _, _ -> mapOf("query" to "") }
        ),

        // ── WHATSAPP ──────────────────────────────────────────────────────
        QuickPattern(IntentType.WHATSAPP, "send_whatsapp",
            Regex("(?i)(?:text|message|send|msg|whatsapp|wa)\\s+(.+?)\\s+(?:on\\s+)?(?:whatsapp|wa|wt)\\s+(?:that\\s+)?(.+)"),
            { m, _ -> mapOf("contact" to m.groupValues[1].trim(), "message" to m.groupValues[2].trim()) }
        ),
        QuickPattern(IntentType.WHATSAPP, "send_whatsapp",
            Regex("(?i)(?:whatsapp|wa|wt)\\s+(.+?)\\s+(?:that\\s+)?(.+)"),
            { m, _ -> mapOf("contact" to m.groupValues[1].trim(), "message" to m.groupValues[2].trim()) }
        ),
        QuickPattern(IntentType.WHATSAPP, "open_whatsapp_chat",
            Regex("(?i)(?:open|chat|message|text)\\s+(.+?)\\s+(?:on\\s+)?(?:whatsapp|wa|wt)\\b"),
            { m, _ -> mapOf("contact" to m.groupValues[1].trim()) }
        ),
        QuickPattern(IntentType.WHATSAPP, "open_whatsapp_chat",
            Regex("(?i)(?:whatsapp|wa)\\s+(?:pe\\s+)?(?:message|text|chat|msg)\\s+(.+)\\b"),
            { m, _ -> mapOf("contact" to m.groupValues[1].trim()) }
        ),

        // ── CALL with SIM ─────────────────────────────────────────────────
        QuickPattern(IntentType.CALL, "make_call",
            Regex("(?i)\\b(?:call|phone|ring|dial)\\s+(.+?)\\s+(?:from|using|on)\\s+(?:sim|sim\\s*card)\\s*(\\d)\\b"),
            { m, _ -> mapOf("contact" to m.groupValues[1].trim(), "sim" to m.groupValues[2].trim()) }
        ),
        QuickPattern(IntentType.CALL, "make_call",
            Regex("(?i)\\b(?:call|phone|ring|dial)\\s+(.+)\\b"),
            { m, _ -> mapOf("contact" to m.groupValues[1].trim()) }
        ),

        // ── MEDIA CONTROL ─────────────────────────────────────────────────
        QuickPattern(IntentType.MEDIA_CONTROL, "play",
            Regex("(?i)\\b(?:play|resume)\\s+(?:music|song|audio|video)?\\s*$"),
            { _, _ -> mapOf("action" to "play") }
        ),
        QuickPattern(IntentType.MEDIA_CONTROL, "pause",
            Regex("(?i)\\b(?:pause|stop)\\s+(?:the\\s+)?(?:music|song|audio|video|playback)?\\s*$"),
            { _, _ -> mapOf("action" to "pause") }
        ),
        QuickPattern(IntentType.MEDIA_CONTROL, "next",
            Regex("(?i)\\b(?:next|skip|forward)\\s+(?:song|track|music)?\\s*$"),
            { _, _ -> mapOf("action" to "next") }
        ),
        QuickPattern(IntentType.MEDIA_CONTROL, "previous",
            Regex("(?i)\\b(?:previous|prev|back|rewind)\\s+(?:song|track|music)?\\s*$"),
            { _, _ -> mapOf("action" to "previous") }
        ),

        // ── FLASHLIGHT ────────────────────────────────────────────────────
        QuickPattern(IntentType.DEVICE_CONTROL, "flashlight_on",
            Regex("(?i)\\b(?:turn on|switch on|enable)\\s+(?:the\\s+)?(?:flashlight|torch)\\b")),
        QuickPattern(IntentType.DEVICE_CONTROL, "flashlight_off",
            Regex("(?i)\\b(?:turn off|switch off|disable)\\s+(?:the\\s+)?(?:flashlight|torch)\\b")),

        // ── WEATHER ───────────────────────────────────────────────────────
        QuickPattern(IntentType.WEATHER, "get_weather",
            Regex("(?i)\\bweather\\s+in\\s+(.+)\\b"),
            { m, _ -> mapOf("city" to m.groupValues[1].trim()) }
        ),

        // ── NEWS ──────────────────────────────────────────────────────────
        QuickPattern(IntentType.NEWS, "get_news",
            Regex("(?i)\\bnews\\s+(?:about|on|for)\\s+(.+)\\b"),
            { m, _ -> mapOf("topic" to m.groupValues[1].trim()) }
        ),

        // ── REMINDER ──────────────────────────────────────────────────────
        QuickPattern(IntentType.REMINDER, "set_reminder",
            Regex("(?i)\\bremind\\s+(?:me\\s+)?(?:to\\s+)?(.+?)\\s+(?:at|by|in|on)\\s+(.+)\\b"),
            { m, _ -> mapOf("task" to m.groupValues[1].trim(), "time" to m.groupValues[2].trim()) }
        ),

        // ── FIND PHONE ────────────────────────────────────────────────────
        QuickPattern(IntentType.FIND_PHONE, "find_phone",
            Regex("(?i)\\b(?:find|locate|ring|track)\\s+(?:my\\s+)?(?:phone|device|mobile)\\b")),

        // ── BATTERY ───────────────────────────────────────────────────────
        QuickPattern(IntentType.BATTERY, "battery_status",
            Regex("(?i)\\b(?:battery|charge|charging)\\s+(?:level|status|percentage|info|check)?\\b")),

        // ── CALL LOG ──────────────────────────────────────────────────────
        QuickPattern(IntentType.CALL_LOG, "read_calls",
            Regex("(?i)\\b(?:call|phone)\\s+(?:history|log|records?|list)\\b")),

        // ── TIME/DATE ─────────────────────────────────────────────────────
        QuickPattern(IntentType.TIME_DATE, "get_time",
            Regex("(?i)\\b(?:what'?s\\s+)?(?:the\\s+)?(?:time|clock)\\b")),
        QuickPattern(IntentType.TIME_DATE, "get_date",
            Regex("(?i)\\b(?:what'?s\\s+)?(?:the\\s+)?(?:date|day|today)\\b")),

        // ── ALARM ─────────────────────────────────────────────────────────
        QuickPattern(IntentType.ALARM, "set_alarm",
            Regex("(?i)\\bset\\s+(?:an?\\s+)?alarm\\s+(?:for\\s+)?(.+)\\b"),
            { m, _ -> mapOf("time" to m.groupValues[1].trim()) }
        ),

        // ── APP LAUNCH ────────────────────────────────────────────────────
        QuickPattern(IntentType.APP_LAUNCH, "open_app",
            Regex("(?i)\\b(?:open|launch|start)\\s+(.+)\\b"),
            { m, _ -> mapOf("app" to m.groupValues[1].trim().lowercase()) }
        ),
    )

    // ──────────────────────────────────────────────────────────────────────────
    // Classification
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Classify user input.
     * 1. Try quick regex patterns first (instant, offline)
     * 2. If no match, use Gemini AI to understand (max intelligence)
     */
    suspend fun classify(input: String): IntentResult {
        val trimmedInput = input.trim()
        if (trimmedInput.isBlank()) {
            return IntentResult(IntentType.GENERAL_CHAT, "empty_input",
                response = "I didn't catch that. Could you please repeat?")
        }

        // Step 1: Quick regex match
        for (qp in quickPatterns) {
            val match = qp.pattern.find(trimmedInput)
            if (match != null) {
                val params = qp.paramExtractor?.invoke(match, trimmedInput) ?: emptyMap()
                return IntentResult(type = qp.type, action = qp.action, params = params)
            }
        }

        // Step 2: Gemini AI classification for MAX intelligence
        if (aiEngine != null) {
            return classifyWithAi(trimmedInput)
        }

        // Fallback
        return IntentResult(IntentType.GENERAL_CHAT, "general_conversation")
    }

    /**
     * Synchronous version — regex only, no AI fallback.
     */
    fun classifySync(input: String): IntentResult {
        val trimmedInput = input.trim()
        if (trimmedInput.isBlank()) {
            return IntentResult(IntentType.GENERAL_CHAT, "empty_input")
        }

        for (qp in quickPatterns) {
            val match = qp.pattern.find(trimmedInput)
            if (match != null) {
                val params = qp.paramExtractor?.invoke(match, trimmedInput) ?: emptyMap()
                return IntentResult(type = qp.type, action = qp.action, params = params)
            }
        }

        return IntentResult(IntentType.GENERAL_CHAT, "general_conversation")
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Gemini AI Classification — THE BRAIN
    // ──────────────────────────────────────────────────────────────────────────

    private suspend fun classifyWithAi(input: String): IntentResult {
        val prompt = """
You are the intent classifier for MAHI, a voice assistant. Classify the user's input into EXACTLY ONE of these intent types:

- DEVICE_CONTROL: Toggle flashlight, wifi, bluetooth, brightness, volume, DND, etc.
- WEATHER: Any weather/temperature/mausam question
- NEWS: Any news/headlines/breaking news request
- YOUTUBE: Play/search/watch something on YouTube
- CALL: Make a phone call to someone
- SMS: Send a text message (regular SMS)
- WHATSAPP: Send a WhatsApp message or open WhatsApp chat
- ALARM: Set an alarm
- REMINDER: Set a reminder (different from alarm - reminders have a task description)
- ROUTINE: Morning/night routine
- CALENDAR: Calendar/schedule related
- NOTIFICATION: Read/check notifications
- APP_LAUNCH: Open/launch an app
- WEB_SEARCH: Search the web for information
- MEDIA_CONTROL: Play/pause/next/previous music or media
- LOCATION: Where am I / nearby places / directions
- BATTERY: Battery level/status
- CALL_LOG: Call history / recent calls
- TIME_DATE: What time/date is it
- FIND_PHONE: Find/locate/ring my phone
- GENERAL_CHAT: General conversation that doesn't fit above

Extract relevant parameters. For example:
- "play carryminati latest video on youtube" → type:YOUTUBE, query:"carryminati latest video"
- "call ayush from sim 1" → type:CALL, contact:"ayush", sim:"1"
- "text ayush in whatsapp that he needs to call me" → type:WHATSAPP, contact:"ayush", message:"he needs to call me"
- "aaj ka mausam kaisa hai" → type:WEATHER, city:"" (use default)
- "top 10 bihar breaking news" → type:NEWS, topic:"bihar", count:"10"
- "remind me to buy milk at 5pm" → type:REMINDER, task:"buy milk", time:"5pm"
- "battery kitni hai" → type:BATTERY
- "kal ayush ne call kiya tha" → type:CALL_LOG, contact:"ayush"

Respond ONLY with valid JSON, nothing else:
{"type":"INTENT_TYPE","action":"action_name","params":{"key":"value"}}

User input: $input
        """.trimIndent()

        return try {
            val aiResponse = aiEngine.queryOnce(prompt)
            parseAiClassification(aiResponse, input)
        } catch (_: Exception) {
            // AI failed — try basic keyword matching as last resort
            keywordFallback(input)
        }
    }

    private fun parseAiClassification(response: String, originalInput: String): IntentResult {
        return try {
            // Extract JSON from response
            val jsonMatch = Regex("\\{[^{}]*\\}").find(response) ?: return keywordFallback(originalInput)
            val json = jsonMatch.value

            val gson = Gson()
            val parsed = gson.fromJson(json, AiClassificationResult::class.java)

            val intentType = try {
                IntentType.valueOf(parsed.type ?: "GENERAL_CHAT")
            } catch (_: IllegalArgumentException) {
                IntentType.GENERAL_CHAT
            }

            IntentResult(
                type = intentType,
                action = parsed.action ?: "ai_classified",
                params = parsed.params ?: emptyMap()
            )
        } catch (_: Exception) {
            keywordFallback(originalInput)
        }
    }

    /**
     * Last-resort keyword matching when AI is unavailable.
     */
    private fun keywordFallback(input: String): IntentResult {
        val lower = input.lowercase()

        // Check for keywords
        return when {
            // YouTube
            lower.contains("youtube") || lower.contains("yt") || lower.contains("video") ->
                IntentResult(IntentType.YOUTUBE, "search_youtube", mapOf("query" to extractTopic(lower, listOf("youtube", "yt", "video", "play", "watch"))))

            // WhatsApp
            lower.contains("whatsapp") || lower.contains("wa ") || lower.contains("watsapp") ->
                IntentResult(IntentType.WHATSAPP, "open_whatsapp_chat", mapOf("contact" to extractContact(lower)))

            // Call
            lower.contains("call") || lower.contains("phone") || lower.contains("ring") || lower.contains("dial") ->
                IntentResult(IntentType.CALL, "make_call", mapOf("contact" to extractContact(lower)))

            // Weather
            lower.contains("weather") || lower.contains("mausam") || lower.contains("temperature") || lower.contains("garmi") || lower.contains("thand") || lower.contains("barish") || lower.contains("rain") ->
                IntentResult(IntentType.WEATHER, "get_weather")

            // News
            lower.contains("news") || lower.contains("khabar") || lower.contains("headline") || lower.contains("breaking") ->
                IntentResult(IntentType.NEWS, "get_news", mapOf("topic" to extractTopic(lower, listOf("news", "khabar", "headline", "breaking", "latest", "top"))))

            // Music/Media
            lower.contains("play") && (lower.contains("music") || lower.contains("song") || lower.contains("gana")) ->
                IntentResult(IntentType.MEDIA_CONTROL, "play", mapOf("action" to "play"))

            // Alarm
            lower.contains("alarm") || lower.contains("wake") ->
                IntentResult(IntentType.ALARM, "set_alarm")

            // Reminder
            lower.contains("remind") || lower.contains("yaad") ->
                IntentResult(IntentType.REMINDER, "set_reminder")

            // Battery
            lower.contains("battery") || lower.contains("charge") ->
                IntentResult(IntentType.BATTERY, "battery_status")

            // Flashlight
            lower.contains("flashlight") || lower.contains("torch") || lower.contains("flash") ->
                IntentResult(IntentType.DEVICE_CONTROL, "flashlight_on")

            // Time
            lower.contains("time") || lower.contains("samay") || lower.contains("baje") ->
                IntentResult(IntentType.TIME_DATE, "get_time")

            else -> IntentResult(IntentType.GENERAL_CHAT, "general_conversation")
        }
    }

    private fun extractContact(input: String): String {
        // Remove common keywords and try to extract the name
        val cleaned = input.replace(Regex("(?i)\\b(?:call|phone|ring|dial|text|message|send|whatsapp|wa|from|sim\\s*\\d|on|to|that|the|please)\\b"), "").trim()
        return cleaned.ifBlank { "unknown" }
    }

    private fun extractTopic(input: String, removeWords: List<String>): String {
        var cleaned = input
        for (word in removeWords) {
            cleaned = cleaned.replace(Regex("(?i)\\b$word\\b"), "")
        }
        return cleaned.trim().ifBlank { "" }
    }

    // ── Gson helper class ────────────────────────────────────────────────────

    private data class AiClassificationResult(
        val type: String? = null,
        val action: String? = null,
        val params: Map<String, String>? = null
    )
}
