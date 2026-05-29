package com.mahi.assistant.ai

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * JARVIS-LEVEL IntentClassifier — AI-PRIMARY Classification.
 *
 * Strategy:
 * 1. Ultra-fast path: Regex for OBVIOUS instant commands (flashlight, battery, time) — no API call
 * 2. AI-first path: Gemini returns structured JSON for EVERYTHING else
 * 3. Keyword fallback: If AI fails, basic keyword matching as last resort
 *
 * This means MAHI understands "kisi bhi tarah bolo" — ANY phrasing in ANY language!
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
        SMS_READ,
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
        NOTE_SAVE,
        NOTE_READ,
        CONTACT_SEARCH,
        TIMER,
        TRANSLATE,
        CALCULATE,
        CONTINUOUS_MODE,
        CAMERA,
        FILE_OPEN,
        GENERAL_CHAT
    }

    data class IntentResult(
        val type: IntentType,
        val action: String,
        val params: Map<String, String> = emptyMap(),
        val response: String? = null
    )

    // ──────────────────────────────────────────────────────────────────────────
    // Ultra-fast patterns — INSTANT, no AI call needed
    // Only the most obvious, unambiguous commands go here
    // ──────────────────────────────────────────────────────────────────────────

    private data class QuickPattern(
        val type: IntentType,
        val action: String,
        val pattern: Regex,
        val paramExtractor: ((MatchResult, String) -> Map<String, String>)? = null
    )

    private val ultraFastPatterns: List<QuickPattern> = listOf(

        // ── FLASHLIGHT — ultra obvious ────────────────────────────────────
        QuickPattern(IntentType.DEVICE_CONTROL, "flashlight_on",
            Regex("(?i)\\b(?:turn on|switch on|enable|on karo|jala)\\s+(?:the\\s+)?(?:flashlight|torch|flash|light)\\b")),
        QuickPattern(IntentType.DEVICE_CONTROL, "flashlight_off",
            Regex("(?i)\\b(?:turn off|switch off|disable|off karo|bujha)\\s+(?:the\\s+)?(?:flashlight|torch|flash|light)\\b")),

        // ── BATTERY — ultra obvious ───────────────────────────────────────
        QuickPattern(IntentType.BATTERY, "battery_status",
            Regex("(?i)\\b(?:battery|charge|charging|battery kitni|charge kitna)\\s*(?:level|status|percentage|info|check|hai|kitni|kitna)?\\s*\\b")),

        // ── TIME/DATE — ultra obvious ─────────────────────────────────────
        QuickPattern(IntentType.TIME_DATE, "get_time",
            Regex("(?i)\\b(?:what'?s\\s+)?(?:the\\s+)?(?:time|clock|samay|baje|kitne baje)\\b")),
        QuickPattern(IntentType.TIME_DATE, "get_date",
            Regex("(?i)\\b(?:what'?s\\s+)?(?:the\\s+)?(?:date|day|today|tarikh|din|aaj)\\b")),

        // ── FIND PHONE — ultra obvious ────────────────────────────────────
        QuickPattern(IntentType.FIND_PHONE, "find_phone",
            Regex("(?i)\\b(?:find|locate|ring|track)\\s+(?:my\\s+)?(?:phone|device|mobile)\\b")),

        // ── CAMERA — ultra obvious ────────────────────────────────────────
        QuickPattern(IntentType.CAMERA, "open_camera",
            Regex("(?i)\\b(?:open|launch)\\s+(?:the\\s+)?(?:camera)\\b")),
        QuickPattern(IntentType.CAMERA, "take_photo",
            Regex("(?i)\\b(?:take|click|snap|capture|shoot)\\s+(?:a\\s+)?(?:photo|picture|pic|selfie)\\b")),
        QuickPattern(IntentType.CAMERA, "take_photo",
            Regex("(?i)\\b(?:photo|picture|pic|selfie)\\s+(?:kheencho|lo|lena)\\b")),
        QuickPattern(IntentType.CAMERA, "take_photo",
            Regex("(?i)\\bphoto\\s+kheencho\\b")),

        // ── CONTINUOUS MODE ───────────────────────────────────────────────
        QuickPattern(IntentType.CONTINUOUS_MODE, "enable_continuous",
            Regex("(?i)\\b(?:enable|turn on|start)\\s+(?:the\\s+)?(?:continuous|always\\s*listening|call\\s*type)\\s*(?:mode)?\\b")),
        QuickPattern(IntentType.CONTINUOUS_MODE, "disable_continuous",
            Regex("(?i)\\b(?:disable|turn off|stop)\\s+(?:the\\s+)?(?:continuous|always\\s*listening|call\\s*type)\\s*(?:mode)?\\b")),
    )

    // ──────────────────────────────────────────────────────────────────────────
    // AI Classification Prompt — THE BRAIN
    // ──────────────────────────────────────────────────────────────────────────

    private val CLASSIFICATION_PROMPT = """
You are MAHI's intent classifier. Classify the user input into EXACTLY ONE intent type and extract parameters.
Respond ONLY with valid JSON, nothing else. No markdown, no explanation, JUST JSON.

Available types:
- DEVICE_CONTROL: Toggle flashlight, wifi, bluetooth, brightness, volume, DND, etc.
- WEATHER: Any weather/temperature/mausam question
- NEWS: Any news/headlines/breaking news/khabar request
- YOUTUBE: Play/search/watch something on YouTube
- CALL: Make a phone call to someone
- SMS: Send a text message (regular SMS)
- SMS_READ: Read SMS inbox messages
- WHATSAPP: Send a WhatsApp message or open WhatsApp chat
- ALARM: Set an alarm
- REMINDER: Set a reminder
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
- NOTE_SAVE: Save a note/memory/reminder to remember something
- NOTE_READ: Recall/read saved notes/memories
- CONTACT_SEARCH: Find/search contact details by name
- TIMER: Set a timer/stopwatch
- TRANSLATE: Translate text from one language to another
- CALCULATE: Math calculation or expression evaluation
- CONTINUOUS_MODE: Toggle always-listening/continuous conversation mode
- CAMERA: Open camera or take a photo/selfie
- FILE_OPEN: Open files/downloads folder/file manager
- GENERAL_CHAT: General conversation that doesn't fit above

Examples:
- "play carryminati latest video on youtube" → {"type":"YOUTUBE","action":"search_youtube","params":{"query":"carryminati latest video"}}
- "call ayush from sim 1" → {"type":"CALL","action":"make_call","params":{"contact":"ayush","sim":"1"}}
- "text ayush in whatsapp that he needs to call me" → {"type":"WHATSAPP","action":"send_whatsapp","params":{"contact":"ayush","message":"he needs to call me"}}
- "aaj ka mausam kaisa hai" → {"type":"WEATHER","action":"get_weather","params":{}}
- "top 10 bihar breaking news" → {"type":"NEWS","action":"get_news","params":{"topic":"bihar","count":"10"}}
- "yaad rakhna kal exam hai" → {"type":"NOTE_SAVE","action":"save_note","params":{"note":"kal exam hai"}}
- "kya note save hai" → {"type":"NOTE_READ","action":"read_notes","params":{}}
- "ayush ka number batao" → {"type":"CONTACT_SEARCH","action":"find_contact","params":{"contact":"ayush"}}
- "5 minute timer lagao" → {"type":"TIMER","action":"set_timer","params":{"duration":"5 minutes"}}
- "hello kaise ho" → {"type":"GENERAL_CHAT","action":"chat","params":{}}
- "2 + 2 kitna hota hai" → {"type":"CALCULATE","action":"calculate","params":{"expression":"2 + 2"}}
- "photo kheencho" → {"type":"CAMERA","action":"open_camera","params":{}}
- "translate hello to hindi" → {"type":"TRANSLATE","action":"translate","params":{"text":"hello","target_lang":"hindi"}}
- "kisi bhi tarah bolo whatsapp pe message karo" → {"type":"WHATSAPP","action":"open_whatsapp_chat","params":{"contact":""}}
- "continuous mode chalu karo" → {"type":"CONTINUOUS_MODE","action":"enable_continuous","params":{}}
- "file manager kholo" → {"type":"FILE_OPEN","action":"open_files","params":{}}
- "sms padho" → {"type":"SMS_READ","action":"read_sms","params":{}}
- "mujhe apne messages dikhao" → {"type":"SMS_READ","action":"read_sms","params":{}}

IMPORTANT: If the user is just chatting/greeting/asking questions, use GENERAL_CHAT.
If they want to save/remember something, use NOTE_SAVE.
If they want to recall what they saved, use NOTE_READ.

User input: """.trimIndent()

    // ──────────────────────────────────────────────────────────────────────────
    // Classification Entry Points
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Classify user input.
     * 1. Ultra-fast regex for OBVIOUS commands (instant, offline)
     * 2. Gemini AI for EVERYTHING else (max intelligence)
     * 3. Keyword fallback if AI fails
     */
    suspend fun classify(input: String): IntentResult {
        val trimmedInput = input.trim()
        if (trimmedInput.isBlank()) {
            return IntentResult(IntentType.GENERAL_CHAT, "empty_input",
                response = "I didn't catch that. Could you please repeat?")
        }

        // Step 1: Ultra-fast regex match for obvious commands
        for (qp in ultraFastPatterns) {
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

        // Step 3: Fallback
        return keywordFallback(trimmedInput)
    }

    /**
     * Synchronous version — regex only, no AI fallback.
     */
    fun classifySync(input: String): IntentResult {
        val trimmedInput = input.trim()
        if (trimmedInput.isBlank()) {
            return IntentResult(IntentType.GENERAL_CHAT, "empty_input")
        }

        for (qp in ultraFastPatterns) {
            val match = qp.pattern.find(trimmedInput)
            if (match != null) {
                val params = qp.paramExtractor?.invoke(match, trimmedInput) ?: emptyMap()
                return IntentResult(type = qp.type, action = qp.action, params = params)
            }
        }

        return keywordFallback(trimmedInput)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Gemini AI Classification — AI-FIRST BRAIN
    // ──────────────────────────────────────────────────────────────────────────

    private suspend fun classifyWithAi(input: String): IntentResult {
        val prompt = CLASSIFICATION_PROMPT + input

        return try {
            val aiResponse = aiEngine!!.classifyIntent(prompt)
            parseAiClassification(aiResponse, input)
        } catch (_: Exception) {
            // AI failed — try keyword matching as last resort
            keywordFallback(input)
        }
    }

    private fun parseAiClassification(response: String, originalInput: String): IntentResult {
        return try {
            // Extract JSON from response — handle markdown code blocks too
            var cleaned = response.trim()
            // Remove markdown code block if present
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replace(Regex("^```(?:json)?\\s*"), "").replace(Regex("\\s*```$"), "")
            }

            val jsonMatch = Regex("\\{[^{}]*(?:\\{[^{}]*\\}[^{}]*)*\\}").find(cleaned)
                ?: Regex("\\{[^{}]*\\}").find(cleaned)
                ?: return keywordFallback(originalInput)
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
     * Expanded with new intent types and better Hindi/Hinglish support.
     */
    private fun keywordFallback(input: String): IntentResult {
        val lower = input.lowercase()

        return when {
            // YouTube
            lower.contains("youtube") || lower.contains("yt") || lower.contains("video") ->
                IntentResult(IntentType.YOUTUBE, "search_youtube", mapOf("query" to extractTopic(lower, listOf("youtube", "yt", "video", "play", "watch", "chalao"))))

            // WhatsApp
            lower.contains("whatsapp") || lower.contains("wa ") || lower.contains("watsapp") ->
                IntentResult(IntentType.WHATSAPP, "send_whatsapp", mapOf("contact" to extractContact(lower), "message" to ""))

            // Call
            lower.contains("call") || lower.contains("phone") || lower.contains("ring") || lower.contains("dial") || lower.contains("karo call") ->
                IntentResult(IntentType.CALL, "make_call", mapOf("contact" to extractContact(lower)))

            // Weather
            lower.contains("weather") || lower.contains("mausam") || lower.contains("temperature") || lower.contains("garmi") || lower.contains("thand") || lower.contains("barish") || lower.contains("rain") ->
                IntentResult(IntentType.WEATHER, "get_weather")

            // News
            lower.contains("news") || lower.contains("khabar") || lower.contains("headline") || lower.contains("breaking") ->
                IntentResult(IntentType.NEWS, "get_news", mapOf("topic" to extractTopic(lower, listOf("news", "khabar", "headline", "breaking", "latest", "top"))))

            // Note save
            lower.contains("yaad") || lower.contains("note save") || lower.contains("remember") || lower.contains("save note") || lower.contains("yaad rakh") ->
                IntentResult(IntentType.NOTE_SAVE, "save_note", mapOf("note" to input))

            // Note read
            lower.contains("note padho") || lower.contains("notes dikhao") || lower.contains("saved notes") || lower.contains("kya yaad hai") || lower.contains("kya note save hai") ->
                IntentResult(IntentType.NOTE_READ, "read_notes")

            // SMS Read
            lower.contains("sms padho") || lower.contains("message padho") || lower.contains("read sms") || lower.contains("messages dikhao") || lower.contains("inbox") ->
                IntentResult(IntentType.SMS_READ, "read_sms")

            // Contact search
            lower.contains("number batao") || lower.contains("contact search") || lower.contains("find contact") || lower.contains("ka number") ->
                IntentResult(IntentType.CONTACT_SEARCH, "find_contact", mapOf("contact" to extractContact(lower)))

            // Timer
            lower.contains("timer") || lower.contains("stopwatch") || lower.contains("timer lagao") ->
                IntentResult(IntentType.TIMER, "set_timer", mapOf("duration" to extractTopic(lower, listOf("timer", "lagao", "set", "start"))))

            // Translation
            lower.contains("translate") || lower.contains("anuvad") || lower.contains("bhasha") ->
                IntentResult(IntentType.TRANSLATE, "translate", mapOf("text" to input))

            // Calculation
            lower.contains("calculate") || lower.contains("hisab") || lower.contains("kitna hota") || Regex("\\d+\\s*[+\\-*/]\\s*\\d+").containsMatchIn(lower) ->
                IntentResult(IntentType.CALCULATE, "calculate", mapOf("expression" to input))

            // Camera
            lower.contains("camera") || lower.contains("photo") || lower.contains("selfie") || lower.contains("picture") || lower.contains("pic") ->
                IntentResult(IntentType.CAMERA, "open_camera")

            // Continuous mode
            lower.contains("continuous mode") || lower.contains("always listening") || lower.contains("call type") ->
                IntentResult(IntentType.CONTINUOUS_MODE, "toggle_continuous")

            // File manager
            lower.contains("file manager") || lower.contains("files open") || lower.contains("downloads") || lower.contains("folder kholo") ->
                IntentResult(IntentType.FILE_OPEN, "open_files")

            // Music/Media
            lower.contains("play") && (lower.contains("music") || lower.contains("song") || lower.contains("gana")) ->
                IntentResult(IntentType.MEDIA_CONTROL, "play", mapOf("action" to "play"))

            // Alarm
            lower.contains("alarm") || lower.contains("wake") ->
                IntentResult(IntentType.ALARM, "set_alarm")

            // Reminder
            lower.contains("remind") || lower.contains("reminder") ->
                IntentResult(IntentType.REMINDER, "set_reminder")

            // Flashlight
            lower.contains("flashlight") || lower.contains("torch") || lower.contains("flash") ->
                IntentResult(IntentType.DEVICE_CONTROL, "flashlight_on")

            // Time
            lower.contains("time") || lower.contains("samay") || lower.contains("baje") ->
                IntentResult(IntentType.TIME_DATE, "get_time")

            // SMS Send
            lower.contains("sms") || lower.contains("text message") || lower.contains("message bhejo") ->
                IntentResult(IntentType.SMS, "send_sms", mapOf("contact" to extractContact(lower)))

            else -> IntentResult(IntentType.GENERAL_CHAT, "general_conversation")
        }
    }

    private fun extractContact(input: String): String {
        val cleaned = input.replace(Regex("(?i)\\b(?:call|phone|ring|dial|text|message|send|whatsapp|wa|from|sim\\s*\\d|on|to|that|the|please|karo|bhejo|ka|number|batao|se|ko)\\b"), "").trim()
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
