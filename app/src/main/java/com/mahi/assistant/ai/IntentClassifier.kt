package com.mahi.assistant.ai

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * JARVIS-LEVEL IntentClassifier — Offline-First Classification.
 *
 * Strategy:
 * 1. Ultra-fast path: Regex for OBVIOUS instant commands — covers ~90% of common use, NO API key needed
 * 2. Keyword fallback: Enhanced keyword matching for common patterns — also works OFFLINE
 * 3. AI path: Gemini returns structured JSON ONLY for truly ambiguous inputs — requires valid API key
 *
 * This means MAHI works OFFLINE for: calls, SMS, WhatsApp, YouTube, weather, news,
 * time, battery, flashlight, alarms, reminders, media control, app launch, and more.
 * Only general conversation and truly ambiguous queries require a valid Gemini API key.
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
        EMERGENCY_SOS,
        EXPENSE_TRACK,
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
    // Covers ~90% of common commands — all work OFFLINE without any API key
    // ──────────────────────────────────────────────────────────────────────────

    private data class QuickPattern(
        val type: IntentType,
        val action: String,
        val pattern: Regex,
        val paramExtractor: ((MatchResult, String) -> Map<String, String>)? = null
    )

    private val ultraFastPatterns: List<QuickPattern> = listOf(

        // ═══════════════ EMERGENCY SOS — works OFFLINE ═══════════════
        QuickPattern(IntentType.EMERGENCY_SOS, "emergency_sos",
            Regex("(?i)\\b(?:emergency|sos|help\\s+help|madad|bahut\\s+mushkil|danger|bachao|save\\s+me)\\b")),
        QuickPattern(IntentType.EMERGENCY_SOS, "emergency_sos",
            Regex("(?i)\\b(?:emergency\\s+sos|call\\s+emergency|emergency\\s+call|112\\s+call|police\\s+call)\\b")),

        // ═══════════════ EXPENSE TRACK — works OFFLINE ═══════════════
        QuickPattern(IntentType.EXPENSE_TRACK, "add_expense",
            Regex("(?i)\\b(?:expense\\s+add|add\\s+expense|kharcha\\s+add|kharcha\\s+karo|kharcha\\s+kiya|spending\\s+add|track\\s+expense)\\b"),
            paramExtractor = { match, input -> extractExpenseParams(input) }),
        QuickPattern(IntentType.EXPENSE_TRACK, "add_expense",
            Regex("(?i)\\b\\d+\\s*(?:rupee|rs|₹|dollar|\\$)\\s+\\w+\\s*(?:kharcha|expense|spending)\\b"),
            paramExtractor = { match, input -> extractExpenseParams(input) }),
        QuickPattern(IntentType.EXPENSE_TRACK, "read_expenses",
            Regex("(?i)\\b(?:kitna\\s+kharcha|kharcha\\s+kitna|total\\s+expense|expense\\s+total|spending\\s+total|aaj\\s+ka\\s+kharcha|today'?s?\\s+expense|week\\s+expense|expenses?\\s+(?:dikhao|show|read|check))\\b")),
        QuickPattern(IntentType.EXPENSE_TRACK, "add_expense",
            Regex("(?i)\\b(?:kharcha|expense|spending)\\s+(?:kiya|kita|hua|hua\\s+hai|add|save)\\b"),
            paramExtractor = { match, input -> extractExpenseParams(input) }),

        // ═══════════════ FLASHLIGHT — works OFFLINE ═══════════════
        QuickPattern(IntentType.DEVICE_CONTROL, "flashlight_on",
            Regex("(?i)\\b(?:turn on|switch on|enable|on karo|jala)\\s+(?:the\\s+)?(?:flashlight|torch|flash|light)\\b")),
        QuickPattern(IntentType.DEVICE_CONTROL, "flashlight_off",
            Regex("(?i)\\b(?:turn off|switch off|disable|off karo|bujha)\\s+(?:the\\s+)?(?:flashlight|torch|flash|light)\\b")),
        QuickPattern(IntentType.DEVICE_CONTROL, "flashlight_on",
            Regex("(?i)\\b(?:flashlight|torch)\\s+(?:on|jala|chalu)\\b")),
        QuickPattern(IntentType.DEVICE_CONTROL, "flashlight_off",
            Regex("(?i)\\b(?:flashlight|torch)\\s+(?:off|bujha|band)\\b")),
        QuickPattern(IntentType.DEVICE_CONTROL, "flashlight_toggle",
            Regex("(?i)\\b(?:flashlight|torch)\\b")),

        // ═══════════════ BATTERY — works OFFLINE ═══════════════
        QuickPattern(IntentType.BATTERY, "battery_status",
            Regex("(?i)\\b(?:battery|charge|charging)\\s*(?:level|status|percentage|info|check|hai|kitni|kitna|kaisa)?\\s*")),
        QuickPattern(IntentType.BATTERY, "battery_status",
            Regex("(?i)\\b(?:battery\\s+kitni|charge\\s+kitna|kitni\\s+battery|kitna\\s+charge)\\b")),

        // ═══════════════ TIME/DATE — works OFFLINE ═══════════════
        QuickPattern(IntentType.TIME_DATE, "get_time",
            Regex("(?i)\\b(?:what'?s\\s+)?(?:the\\s+)?(?:time|clock|samay|baje|kitne\\s+baje)\\b")),
        QuickPattern(IntentType.TIME_DATE, "get_time",
            Regex("(?i)\\b(?:kitne\\s+baje|time\\s+batao|time\\s+kya|samay\\s+kya|samay\\s+batao)\\b")),
        QuickPattern(IntentType.TIME_DATE, "get_date",
            Regex("(?i)\\b(?:what'?s\\s+)?(?:the\\s+)?(?:date|day|today|tarikh|din|aaj)\\b")),
        QuickPattern(IntentType.TIME_DATE, "get_date",
            Regex("(?i)\\b(?:aaj\\s+tarikh|aaj\\s+din|tarikh\\s+batao|date\\s+kya)\\b")),

        // ═══════════════ CALL — works OFFLINE ═══════════════
        QuickPattern(IntentType.CALL, "make_call",
            Regex("(?i)\\b(?:call|phone|ring|dial)\\s+\\w+"),
            paramExtractor = { match, input -> mapOf("contact" to extractContactFromInput(input, "call")) }),
        QuickPattern(IntentType.CALL, "make_call",
            Regex("(?i)\\b\\w+\\s+ko\\s+(?:call|phone|ring)\\s*(?:karo)?\\b"),
            paramExtractor = { match, input -> mapOf("contact" to extractContactFromInput(input, "call")) }),
        QuickPattern(IntentType.CALL, "make_call",
            Regex("(?i)\\b(?:call|phone|ring|dial)\\s+(?:karo|kar)\\b")),
        QuickPattern(IntentType.CALL, "make_call",
            Regex("(?i)\\b(?:make\\s+a\\s+call|place\\s+a\\s+call|phone\\s+call)\\b")),

        // ═══════════════ YOUTUBE — works OFFLINE ═══════════════
        QuickPattern(IntentType.YOUTUBE, "search_youtube",
            Regex("(?i)\\b(?:play|watch|search)\\s+.+\\s+(?:on\\s+)?(?:youtube|yt)\\b"),
            paramExtractor = { match, input -> mapOf("query" to extractTopicFromInput(input, listOf("play", "watch", "search", "on", "youtube", "yt", "chalao", "pe"))) }),
        QuickPattern(IntentType.YOUTUBE, "search_youtube",
            Regex("(?i)\\b(?:youtube|yt)\\s+pe\\s+.+"),
            paramExtractor = { match, input -> mapOf("query" to extractTopicFromInput(input, listOf("youtube", "yt", "pe", "chalao", "play"))) }),
        QuickPattern(IntentType.YOUTUBE, "open_youtube",
            Regex("(?i)\\b(?:youtube|yt)\\s+(?:pe\\s+)?(?:chalao|play|search|kholo)\\b"),
            paramExtractor = { match, input -> mapOf("query" to "") }),
        QuickPattern(IntentType.YOUTUBE, "search_youtube",
            Regex("(?i)\\b(?:youtube|yt)\\s+(?:search|pe)\\s+\\w+"),
            paramExtractor = { match, input -> mapOf("query" to extractTopicFromInput(input, listOf("youtube", "yt", "search", "pe"))) }),

        // ═══════════════ WHATSAPP — SMART extraction (Bug Fix #1) ═══════════════
        // Hinglish: "whatsapp pe ayush ko message bhejo ki kal exam hai"
        QuickPattern(IntentType.WHATSAPP, "send_whatsapp",
            Regex("(?i)\\b(?:whatsapp|wa)\\s+pe\\s+(\\w+)\\s+ko\\s+(?:message|msg)\\s+(?:bhejo|karo|send)?\\s*(?:ki|ke|ki\\s+ki)?\\s*(.*)"),
            paramExtractor = { match, input -> mapOf(
                "contact" to (match.groupValues.getOrNull(1)?.trim()?.ifBlank { "unknown" } ?: "unknown"),
                "message" to (match.groupValues.getOrNull(2)?.trim()?.ifBlank { "" } ?: "")
            )}),
        // "ayush ko whatsapp pe message bhejo ki kal exam hai"
        QuickPattern(IntentType.WHATSAPP, "send_whatsapp",
            Regex("(?i)\\b(\\w+)\\s+ko\\s+(?:whatsapp|wa)\\s+pe\\s+(?:message|msg)\\s+(?:bhejo|karo|send)?\\s*(?:ki|ke)?\\s*(.*)"),
            paramExtractor = { match, input -> mapOf(
                "contact" to (match.groupValues.getOrNull(1)?.trim()?.ifBlank { "unknown" } ?: "unknown"),
                "message" to (match.groupValues.getOrNull(2)?.trim()?.ifBlank { "" } ?: "")
            )}),
        // English: "send hello to ayush on whatsapp"
        QuickPattern(IntentType.WHATSAPP, "send_whatsapp",
            Regex("(?i)\\b(?:send|bhejo)\\s+(.+?)\\s+(?:to|ko)\\s+(\\w+)\\s+(?:on|pe)\\s+(?:whatsapp|wa)\\b"),
            paramExtractor = { match, input -> mapOf(
                "message" to (match.groupValues.getOrNull(1)?.trim()?.ifBlank { "" } ?: ""),
                "contact" to (match.groupValues.getOrNull(2)?.trim()?.ifBlank { "unknown" } ?: "unknown")
            )}),
        // "whatsapp pe mom ko bhejo ki I'll be late"
        QuickPattern(IntentType.WHATSAPP, "send_whatsapp",
            Regex("(?i)\\b(?:whatsapp|wa)\\s+pe\\s+(\\w+)\\s+ko\\s+(?:bhejo|send)\\s*(?:ki|ke)?\\s*(.*)"),
            paramExtractor = { match, input -> mapOf(
                "contact" to (match.groupValues.getOrNull(1)?.trim()?.ifBlank { "unknown" } ?: "unknown"),
                "message" to (match.groupValues.getOrNull(2)?.trim()?.ifBlank { "" } ?: "")
            )}),
        // Generic WhatsApp send patterns (fallback, no specific contact/message extraction)
        QuickPattern(IntentType.WHATSAPP, "send_whatsapp",
            Regex("(?i)\\b(?:whatsapp|wa)\\s+(?:pe\\s+)?(?:message|msg|send|bhejo)\\b"),
            paramExtractor = { match, input -> extractWhatsAppParams(input) }),
        QuickPattern(IntentType.WHATSAPP, "send_whatsapp",
            Regex("(?i)\\b\\w+\\s+ko\\s+(?:whatsapp|wa)\\s+pe\\s+(?:message|msg)\\b"),
            paramExtractor = { match, input -> extractWhatsAppParams(input) }),
        QuickPattern(IntentType.WHATSAPP, "open_whatsapp",
            Regex("(?i)\\b(?:open|launch|start)\\s+(?:whatsapp|wa)\\b")),
        QuickPattern(IntentType.WHATSAPP, "send_whatsapp",
            Regex("(?i)\\b(?:whatsapp|wa)\\s+(?:message|msg)\\s+(?:karo|bhejo|send)"),
            paramExtractor = { match, input -> extractWhatsAppParams(input) }),
        QuickPattern(IntentType.WHATSAPP, "send_whatsapp",
            Regex("(?i)\\b(?:send|bhejo)\\s+.+\\s+(?:on\\s+)?(?:whatsapp|wa)\\b"),
            paramExtractor = { match, input -> extractWhatsAppParams(input) }),

        // ═══════════════ WEATHER — uses free Open-Meteo API ═══════════════
        QuickPattern(IntentType.WEATHER, "get_weather",
            Regex("(?i)\\b(?:weather|mausam|temperature|garmi|thand|barish|rain)\\b")),
        QuickPattern(IntentType.WEATHER, "get_weather",
            Regex("(?i)\\b(?:aaj\\s+ka\\s+mausam|mausam\\s+kaisa|mausam\\s+kya|weather\\s+kya|weather\\s+check)\\b")),
        QuickPattern(IntentType.WEATHER, "get_weather",
            Regex("(?i)\\b(?:kitni\\s+garmi|kitni\\s+thand|barish\\s+hogi|rain\\s+hoga)\\b")),
        QuickPattern(IntentType.WEATHER, "get_weather",
            Regex("(?i)\\b(?:weather\\s+(?:in|of|for)\\s+\\w+|\\w+\\s+(?:ka|ki|me)\\s+mausam)\\b")),

        // ═══════════════ NEWS — uses free RSS ═══════════════
        QuickPattern(IntentType.NEWS, "get_news",
            Regex("(?i)\\b(?:news|khabar|headline|breaking\\s*news|latest\\s*news|top\\s*news)\\b")),
        QuickPattern(IntentType.NEWS, "get_news",
            Regex("(?i)\\b(?:news\\s+dikhao|khabar\\s+dikhao|news\\s+suna|khabar\\s+suna|news\\s+chalu)\\b")),
        QuickPattern(IntentType.NEWS, "get_news",
            Regex("(?i)\\b(?:aaj\\s+ki\\s+khabar|aaj\\s+ka\\s+news|latest\\s+headline)\\b")),

        // ═══════════════ APP_LAUNCH — works OFFLINE ═══════════════
        QuickPattern(IntentType.APP_LAUNCH, "launch_app",
            Regex("(?i)\\b(?:open|launch|start)\\s+(?:the\\s+)?(?:app\\s+)?\\w+"),
            paramExtractor = { match, input -> mapOf("app" to extractAppFromInput(input)) }),
        QuickPattern(IntentType.APP_LAUNCH, "launch_app",
            Regex("(?i)\\b\\w+\\s+(?:kholo|chalao|shuru)\\b"),
            paramExtractor = { match, input -> mapOf("app" to extractAppFromInput(input)) }),

        // ═══════════════ SMS SEND — works OFFLINE ═══════════════
        QuickPattern(IntentType.SMS, "send_sms",
            Regex("(?i)\\b(?:send|write)\\s+(?:a\\s+)?(?:sms|text|text\\s+message)\\b")),
        QuickPattern(IntentType.SMS, "send_sms",
            Regex("(?i)\\b(?:sms|text)\\s+(?:\\w+\\s+)?(?:karo|bhejo|send)\\b"),
            paramExtractor = { match, input -> mapOf("contact" to extractContactFromInput(input, "sms")) }),
        QuickPattern(IntentType.SMS, "send_sms",
            Regex("(?i)\\b(?:message|msg)\\s+bhejo\\b"),
            paramExtractor = { match, input -> mapOf("contact" to extractContactFromInput(input, "sms")) }),

        // ═══════════════ SMS READ — works OFFLINE ═══════════════
        QuickPattern(IntentType.SMS_READ, "read_sms",
            Regex("(?i)\\b(?:read|show|check)\\s+(?:my\\s+)?(?:sms|text\\s+messages|messages|inbox)\\b")),
        QuickPattern(IntentType.SMS_READ, "read_sms",
            Regex("(?i)\\b(?:sms|message)\\s+(?:padho|dikhao|read|check)\\b")),
        QuickPattern(IntentType.SMS_READ, "read_sms",
            Regex("(?i)\\b(?:messages?\\s+dikhao|inbox\\s+dikhao|sms\\s+padho)\\b")),

        // ═══════════════ ALARM — works OFFLINE ═══════════════
        QuickPattern(IntentType.ALARM, "set_alarm",
            Regex("(?i)\\b(?:set|create|make)\\s+(?:an?\\s+)?(?:alarm|wake\\s*up)\\b")),
        QuickPattern(IntentType.ALARM, "set_alarm",
            Regex("(?i)\\b(?:alarm)\\s+(?:lagao|set|karo|chalu)\\b")),
        QuickPattern(IntentType.ALARM, "set_alarm",
            Regex("(?i)\\balarm\\b")),

        // ═══════════════ REMINDER — works OFFLINE ═══════════════
        QuickPattern(IntentType.REMINDER, "set_reminder",
            Regex("(?i)\\b(?:set|create|make)\\s+(?:a\\s+)?(?:reminder|remind)\\b")),
        QuickPattern(IntentType.REMINDER, "set_reminder",
            Regex("(?i)\\b(?:remind|reminder|yaad\\s+dilana)\\b")),

        // ═══════════════ MEDIA CONTROL — works OFFLINE ═══════════════
        QuickPattern(IntentType.MEDIA_CONTROL, "play",
            Regex("(?i)\\b(?:play\\s+)?(?:music|song|gana)\\s*(?:play|chalao|baja)?\\b")),
        QuickPattern(IntentType.MEDIA_CONTROL, "play",
            Regex("(?i)\\b(?:play\\s+music|play\\s+song|music\\s+chalao|gana\\s+chalao|gana\\s+baja)\\b")),
        QuickPattern(IntentType.MEDIA_CONTROL, "pause",
            Regex("(?i)\\b(?:pause|stop\\s+music|ruk|ruko)\\s*(?:music|song|gana)?\\b")),
        QuickPattern(IntentType.MEDIA_CONTROL, "next",
            Regex("(?i)\\b(?:next|aage|next\\s+song|next\\s+track|agla\\s+gana)\\b")),
        QuickPattern(IntentType.MEDIA_CONTROL, "previous",
            Regex("(?i)\\b(?:previous|peeche|prev|last\\s+song|pichla\\s+gana)\\b")),

        // ═══════════════ BRIGHTNESS — works OFFLINE ═══════════════
        QuickPattern(IntentType.DEVICE_CONTROL, "brightness_up",
            Regex("(?i)\\b(?:brightness|screen\\s+brightness|roshni)\\s+(?:up|increase|badhao|tez)\\b")),
        QuickPattern(IntentType.DEVICE_CONTROL, "brightness_down",
            Regex("(?i)\\b(?:brightness|screen\\s+brightness|roshni)\\s+(?:down|decrease|kam|dhima)\\b")),
        QuickPattern(IntentType.DEVICE_CONTROL, "brightness",
            Regex("(?i)\\b(?:brightness|roshni)\\b")),

        // ═══════════════ VOLUME — works OFFLINE ═══════════════
        QuickPattern(IntentType.DEVICE_CONTROL, "volume_up",
            Regex("(?i)\\b(?:volume|sound|awaaz)\\s+(?:up|increase|badhao|tez|loud)\\b")),
        QuickPattern(IntentType.DEVICE_CONTROL, "volume_down",
            Regex("(?i)\\b(?:volume|sound|awaaz)\\s+(?:down|decrease|kam|dhima|low|quiet)\\b")),
        QuickPattern(IntentType.DEVICE_CONTROL, "volume_mute",
            Regex("(?i)\\b(?:mute|silent|khamosh)\\s*(?:volume|sound|phone)?\\b")),
        QuickPattern(IntentType.DEVICE_CONTROL, "volume",
            Regex("(?i)\\b(?:volume|sound\\s+level|awaaz)\\b")),

        // ═══════════════ WEB SEARCH — works OFFLINE (launches browser) ═══════════════
        QuickPattern(IntentType.WEB_SEARCH, "web_search",
            Regex("(?i)\\b(?:search|google|lookup|find\\s+info)\\s+(?:for\\s+)?(.+)"),
            paramExtractor = { match, input -> mapOf("query" to extractTopicFromInput(input, listOf("search", "for", "google", "lookup", "find", "info"))) }),
        QuickPattern(IntentType.WEB_SEARCH, "web_search",
            Regex("(?i)\\b(?:search\\s+karo|google\\s+karo|khojo)\\b")),

        // ═══════════════ NOTE SAVE — works OFFLINE ═══════════════
        QuickPattern(IntentType.NOTE_SAVE, "save_note",
            Regex("(?i)\\b(?:remember|note|save|yaad)\\s+(?:this|that|note|karo|rakhna|rakh)\\b")),
        QuickPattern(IntentType.NOTE_SAVE, "save_note",
            Regex("(?i)\\b(?:yaad\\s+rakhna|note\\s+save|save\\s+note|note\\s+karo|remember\\s+this)\\b")),
        QuickPattern(IntentType.NOTE_SAVE, "save_note",
            Regex("(?i)\\b(?:save\\s+this|remember\\s+that|yaad\\s+rakh)\\b")),

        // ═══════════════ NOTE READ — works OFFLINE ═══════════════
        QuickPattern(IntentType.NOTE_READ, "read_notes",
            Regex("(?i)\\b(?:show|read|check|what\\s+(?:are|is))\\s+(?:my\\s+)?(?:notes?|memories|saved)\\b")),
        QuickPattern(IntentType.NOTE_READ, "read_notes",
            Regex("(?i)\\b(?:notes?\\s+dikhao|notes?\\s+padho|kya\\s+yaad\\s+hai|kya\\s+note\\s+save\\s+hai)\\b")),
        QuickPattern(IntentType.NOTE_READ, "read_notes",
            Regex("(?i)\\b(?:yaad\\s+kya\\s+hai|saved\\s+notes|mujhe\\s+meri\\s+notes)\\b")),

        // ═══════════════ CONTACT SEARCH — works OFFLINE ═══════════════
        QuickPattern(IntentType.CONTACT_SEARCH, "find_contact",
            Regex("(?i)\\b(?:find|search|look\\s+up)\\s+(?:contact|number)\\b")),
        QuickPattern(IntentType.CONTACT_SEARCH, "find_contact",
            Regex("(?i)\\b\\w+\\s+(?:ka\\s+number|ka\\s+contact|ka\\s+phone)\\b"),
            paramExtractor = { match, input -> mapOf("contact" to extractContactFromInput(input, "contact_search")) }),
        QuickPattern(IntentType.CONTACT_SEARCH, "find_contact",
            Regex("(?i)\\b(?:number\\s+batao|contact\\s+search|contact\\s+dhoond)\\b"),
            paramExtractor = { match, input -> mapOf("contact" to extractContactFromInput(input, "contact_search")) }),

        // ═══════════════ TIMER — works OFFLINE ═══════════════
        QuickPattern(IntentType.TIMER, "set_timer",
            Regex("(?i)\\b(?:set|start)\\s+(?:a\\s+)?(?:timer|stopwatch|countdown)\\b")),
        QuickPattern(IntentType.TIMER, "set_timer",
            Regex("(?i)\\b(?:timer|stopwatch)\\s+(?:lagao|set|start|chalu)\\b")),
        QuickPattern(IntentType.TIMER, "set_timer",
            Regex("(?i)\\b\\d+\\s*(?:minute|min|second|sec)\\s*(?:timer|ka\\s+timer)\\b")),

        // ═══════════════ TRANSLATE — can launch translate app ═══════════════
        QuickPattern(IntentType.TRANSLATE, "translate",
            Regex("(?i)\\b(?:translate|anuvad|translation)\\b")),

        // ═══════════════ CALCULATE — can do basic math ═══════════════
        QuickPattern(IntentType.CALCULATE, "calculate",
            Regex("(?i)\\b(?:calculate|compute|kitna\\s+hota|solve)\\b")),
        QuickPattern(IntentType.CALCULATE, "calculate",
            Regex("\\d+\\s*[+\\-*/×÷]\\s*\\d+")),

        // ═══════════════ FIND PHONE — works OFFLINE ═══════════════
        QuickPattern(IntentType.FIND_PHONE, "find_phone",
            Regex("(?i)\\b(?:find|locate|ring|track)\\s+(?:my\\s+)?(?:phone|device|mobile)\\b")),
        QuickPattern(IntentType.FIND_PHONE, "find_phone",
            Regex("(?i)\\b(?:phone\\s+dhoondo|mobile\\s+kahan|phone\\s+ring\\s+karo)\\b")),

        // ═══════════════ CAMERA — works OFFLINE ═══════════════
        QuickPattern(IntentType.CAMERA, "open_camera",
            Regex("(?i)\\b(?:open|launch)\\s+(?:the\\s+)?(?:camera)\\b")),
        QuickPattern(IntentType.CAMERA, "take_photo",
            Regex("(?i)\\b(?:take|click|snap|capture|shoot)\\s+(?:a\\s+)?(?:photo|picture|pic|selfie)\\b")),
        QuickPattern(IntentType.CAMERA, "take_photo",
            Regex("(?i)\\b(?:photo|picture|pic|selfie)\\s+(?:kheencho|lo|lena)\\b")),
        QuickPattern(IntentType.CAMERA, "take_photo",
            Regex("(?i)\\bphoto\\s+kheencho\\b")),

        // ═══════════════ CONTINUOUS MODE — works OFFLINE ═══════════════
        QuickPattern(IntentType.CONTINUOUS_MODE, "enable_continuous",
            Regex("(?i)\\b(?:enable|turn on|start)\\s+(?:the\\s+)?(?:continuous|always\\s*listening|call\\s*type)\\s*(?:mode)?\\b")),
        QuickPattern(IntentType.CONTINUOUS_MODE, "disable_continuous",
            Regex("(?i)\\b(?:disable|turn off|stop)\\s+(?:the\\s+)?(?:continuous|always\\s*listening|call\\s*type)\\s*(?:mode)?\\b")),

        // ═══════════════ LOCATION — works OFFLINE (GPS) ═══════════════
        QuickPattern(IntentType.LOCATION, "get_location",
            Regex("(?i)\\b(?:where\\s+am\\s+i|my\\s+location|mera\\s+location|location\\s+dikhao|kahan\\s+hun)\\b")),
        QuickPattern(IntentType.LOCATION, "get_location",
            Regex("(?i)\\b(?:find\\s+my\\s+location|show\\s+my\\s+location|location\\s+kya\\s+hai)\\b")),

        // ═══════════════ CALL LOG — works OFFLINE ═══════════════
        QuickPattern(IntentType.CALL_LOG, "call_log",
            Regex("(?i)\\b(?:call\\s*log|call\\s+history|recent\\s+calls|call\\s+record)\\b")),
        QuickPattern(IntentType.CALL_LOG, "call_log",
            Regex("(?i)\\b(?:call\\s+log\\s+dikhao|recent\\s+call|call\\s+details)\\b")),

        // ═══════════════ NOTIFICATION — works OFFLINE ═══════════════
        QuickPattern(IntentType.NOTIFICATION, "read_notifications",
            Regex("(?i)\\b(?:read|show|check)\\s+(?:my\\s+)?(?:notifications?|notifs?)\\b")),
        QuickPattern(IntentType.NOTIFICATION, "read_notifications",
            Regex("(?i)\\b(?:notification|notifs?)\\s+(?:dikhao|padho|check)\\b")),

        // ═══════════════ WIFI/BLUETOOTH/DND — works OFFLINE ═══════════════
        QuickPattern(IntentType.DEVICE_CONTROL, "wifi_on",
            Regex("(?i)\\b(?:turn\\s+on|enable|on\\s+karo)\\s+(?:the\\s+)?(?:wifi|wi-?fi)\\b")),
        QuickPattern(IntentType.DEVICE_CONTROL, "wifi_off",
            Regex("(?i)\\b(?:turn\\s+off|disable|off\\s+karo)\\s+(?:the\\s+)?(?:wifi|wi-?fi)\\b")),
        QuickPattern(IntentType.DEVICE_CONTROL, "bluetooth_on",
            Regex("(?i)\\b(?:turn\\s+on|enable|on\\s+karo)\\s+(?:the\\s+)?bluetooth\\b")),
        QuickPattern(IntentType.DEVICE_CONTROL, "bluetooth_off",
            Regex("(?i)\\b(?:turn\\s+off|disable|off\\s+karo)\\s+(?:the\\s+)?bluetooth\\b")),
        QuickPattern(IntentType.DEVICE_CONTROL, "dnd_on",
            Regex("(?i)\\b(?:turn\\s+on|enable|on\\s+karo)\\s+(?:the\\s+)?(?:dnd|do\\s+not\\s+disturb)\\b")),
        QuickPattern(IntentType.DEVICE_CONTROL, "dnd_off",
            Regex("(?i)\\b(?:turn\\s+off|disable|off\\s+karo)\\s+(?:the\\s+)?(?:dnd|do\\s+not\\s+disturb)\\b")),

        // ═══════════════ FILE MANAGER — works OFFLINE ═══════════════
        QuickPattern(IntentType.FILE_OPEN, "open_files",
            Regex("(?i)\\b(?:open|launch)\\s+(?:the\\s+)?(?:file\\s*manager|files|downloads|folder)\\b")),
        QuickPattern(IntentType.FILE_OPEN, "open_files",
            Regex("(?i)\\b(?:file\\s*manager|downloads|folder)\\s+(?:kholo|chalao|open)\\b")),
    )

    // ──────────────────────────────────────────────────────────────────────────
    // AI Classification Prompt — THE BRAIN (only used for ambiguous inputs)
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
- EMERGENCY_SOS: Emergency, SOS, help help, danger, bachao, madad
- EXPENSE_TRACK: Track expenses, add expense, kharcha, spending, kitna kharcha
- GENERAL_CHAT: General conversation that doesn't fit above

Examples:
- "play carryminati latest video on youtube" → {"type":"YOUTUBE","action":"search_youtube","params":{"query":"carryminati latest video"}}
- "call ayush from sim 1" → {"type":"CALL","action":"make_call","params":{"contact":"ayush","sim":"1"}}
- "text ayush in whatsapp that he needs to call me" → {"type":"WHATSAPP","action":"send_whatsapp","params":{"contact":"ayush","message":"he needs to call me"}}
- "whatsapp pe ayush ko message bhejo ki kal exam hai" → {"type":"WHATSAPP","action":"send_whatsapp","params":{"contact":"ayush","message":"kal exam hai"}}
- "send hello to ayush on whatsapp" → {"type":"WHATSAPP","action":"send_whatsapp","params":{"contact":"ayush","message":"hello"}}
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
- "emergency help" → {"type":"EMERGENCY_SOS","action":"emergency_sos","params":{}}
- "bachao madad" → {"type":"EMERGENCY_SOS","action":"emergency_sos","params":{}}
- "expense add 500 rupees food" → {"type":"EXPENSE_TRACK","action":"add_expense","params":{"amount":"500","category":"food"}}
- "aaj ka kharcha kitna hua" → {"type":"EXPENSE_TRACK","action":"read_expenses","params":{}}
- "200 rs ka kharcha transport ka" → {"type":"EXPENSE_TRACK","action":"add_expense","params":{"amount":"200","category":"transport","description":"transport"}}

IMPORTANT: If the user is just chatting/greeting/asking questions, use GENERAL_CHAT.
If they want to save/remember something, use NOTE_SAVE.
If they want to recall what they saved, use NOTE_READ.
If they say emergency, SOS, help help, madad, bachao, use EMERGENCY_SOS.
If they mention expense, kharcha, spending, use EXPENSE_TRACK.

User input: """.trimIndent()

    // ──────────────────────────────────────────────────────────────────────────
    // Classification Entry Points
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Classify user input.
     * 1. Ultra-fast regex for OBVIOUS commands (instant, offline) — covers ~90% of common use
     * 2. Enhanced keyword fallback for common patterns (also offline)
     * 3. Gemini AI classification ONLY for truly ambiguous inputs (requires valid API key)
     */
    suspend fun classify(input: String): IntentResult {
        val trimmedInput = input.trim()
        if (trimmedInput.isBlank()) {
            return IntentResult(IntentType.GENERAL_CHAT, "empty_input",
                response = "I didn't catch that. Could you please repeat?")
        }

        // Step 1: Ultra-fast regex match for obvious commands (covers ~90% of common use)
        for (qp in ultraFastPatterns) {
            val match = qp.pattern.find(trimmedInput)
            if (match != null) {
                val params = qp.paramExtractor?.invoke(match, trimmedInput) ?: emptyMap()
                return IntentResult(type = qp.type, action = qp.action, params = params)
            }
        }

        // Step 2: Enhanced keyword fallback FIRST (works offline, no API key needed)
        val keywordResult = keywordFallback(trimmedInput)
        if (keywordResult.type != IntentType.GENERAL_CHAT) {
            // Keyword matched a specific intent — no need for AI
            return keywordResult
        }

        // Step 3: Only use AI for truly ambiguous inputs that keywords couldn't classify
        // AND only if the AI engine is properly configured with a valid API key
        if (aiEngine != null && aiEngine.isConfigured()) {
            return classifyWithAi(trimmedInput)
        }

        // Step 4: No AI available — return the keyword result (even if GENERAL_CHAT)
        return keywordResult
    }

    /**
     * Synchronous version — regex + keyword only, no AI fallback.
     * Use this when you can't use coroutines (e.g., from a service).
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
    // Gemini AI Classification — Only for truly ambiguous inputs
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
     * Keyword fallback for when AI is unavailable or not configured.
     * Expanded with new intent types and better Hindi/Hinglish support.
     * Works OFFLINE — no API key needed.
     */
    private fun keywordFallback(input: String): IntentResult {
        val lower = input.lowercase()

        // Check for Hindi/Devanagari text
        val hasDevanagari = input.any { it in '\u0900'..'\u097F' }

        return when {
            // ── HINDI DEVANAGARI PATTERNS (speech recognition output) ──
            // These handle the case where Android SpeechRecognizer outputs Hindi in Devanagari script

            // Emergency - Hindi
            hasDevanagari && (input.contains("मदद") || input.contains("बचाओ") || input.contains("खतरा") || input.contains("संकट") || input.contains("मदद कर")) ->
                IntentResult(IntentType.EMERGENCY_SOS, "emergency_sos")

            // Weather - Hindi
            hasDevanagari && (input.contains("मौसम") || input.contains("गर्मी") || input.contains("ठंड") || input.contains("बारिश") || input.contains("तापमान")) ->
                IntentResult(IntentType.WEATHER, "get_weather")

            // News - Hindi
            hasDevanagari && (input.contains("खबर") || input.contains("समाचार") || input.contains("हेडलाइन") || input.contains("ब्रेकिंग")) ->
                IntentResult(IntentType.NEWS, "get_news", mapOf("topic" to input))

            // YouTube - Hindi
            hasDevanagari && (input.contains("यूट्यूब") || input.contains("वीडियो") || input.contains("चलाओ")) ->
                IntentResult(IntentType.YOUTUBE, "search_youtube", mapOf("query" to input))

            // WhatsApp - Hindi
            hasDevanagari && (input.contains("व्हाट्सएप") || input.contains("व्हाट्सअप") || input.contains("मैसेज भेज")) ->
                IntentResult(IntentType.WHATSAPP, "send_whatsapp", mapOf("contact" to "", "message" to ""))

            // Call - Hindi
            hasDevanagari && (input.contains("कॉल") || input.contains("फोन") || input.contains("डायल")) ->
                IntentResult(IntentType.CALL, "make_call", mapOf("contact" to input))

            // SMS - Hindi
            hasDevanagari && (input.contains("संदेश") || input.contains("एसएमएस") || input.contains("मैसेज")) ->
                IntentResult(IntentType.SMS, "send_sms", mapOf("contact" to input))

            // Alarm - Hindi
            hasDevanagari && (input.contains("अलार्म") || input.contains("जगाओ") || input.contains("टाइम")) ->
                IntentResult(IntentType.ALARM, "set_alarm")

            // Time - Hindi
            hasDevanagari && (input.contains("समय") || input.contains("बजे") || input.contains("कितने बजे")) ->
                IntentResult(IntentType.TIME_DATE, "get_time")

            // Date - Hindi
            hasDevanagari && (input.contains("तारीख") || input.contains("दिन") || input.contains("आज")) ->
                IntentResult(IntentType.TIME_DATE, "get_date")

            // Camera/Photo - Hindi
            hasDevanagari && (input.contains("कैमरा") || input.contains("फोटो") || input.contains("तस्वीर") || input.contains("सेल्फी")) ->
                IntentResult(IntentType.CAMERA, "open_camera")

            // Note/Memory - Hindi
            hasDevanagari && (input.contains("याद") || input.contains("नोट") || input.contains("सहेज")) ->
                IntentResult(IntentType.NOTE_SAVE, "save_note", mapOf("note" to input))

            // Battery - Hindi
            hasDevanagari && (input.contains("बैटरी") || input.contains("चार्ज")) ->
                IntentResult(IntentType.BATTERY, "battery_status")

            // Flashlight - Hindi
            hasDevanagari && (input.contains("टॉर्च") || input.contains("रोशनी") || input.contains("जलाओ") || input.contains("बुझाओ")) ->
                IntentResult(IntentType.DEVICE_CONTROL, "flashlight_on")

            // Location - Hindi
            hasDevanagari && (input.contains("कहां") || input.contains("लोकेशन") || input.contains("पता")) ->
                IntentResult(IntentType.LOCATION, "get_location")

            // Music - Hindi
            hasDevanagari && (input.contains("गाना") || input.contains("संगीत") || input.contains("बजाओ")) ->
                IntentResult(IntentType.MEDIA_CONTROL, "play", mapOf("action" to "play"))

            // Expense - Hindi
            hasDevanagari && (input.contains("खर्च") || input.contains("खर्चा")) ->
                IntentResult(IntentType.EXPENSE_TRACK, if (input.contains("कितना") || input.contains("दिखाओ")) "read_expenses" else "add_expense",
                    mapOf("description" to input))

            // General Hindi greeting or question → treat as general chat (not error)
            hasDevanagari && (input.contains("क्या") || input.contains("कैसे") || input.contains("कहां") || input.contains("कब") || input.contains("क्यों") || input.contains("कौन") || input.contains("है") || input.contains("हूं") || input.contains("हो") || input.contains("बताओ") || input.contains("करो") || input.contains("दिखाओ") || input.contains("भेजो") || input.contains("चाहिए")) ->
                IntentResult(IntentType.GENERAL_CHAT, "hindi_general_chat")

            // ── FOLLOW-UP QUESTION PATTERNS (pronouns → GENERAL_CHAT so AI uses context) ──
            lower.matches(Regex("""(?i).*how old is (he|she|it|they|him|her).*""")) ||
            lower.matches(Regex("""(?i).*where (was|is|did) (he|she|it|they|him|her).*""")) ||
            lower.matches(Regex("""(?i).*when (was|is|did) (he|she|it|they|him|her).*""")) ||
            lower.matches(Regex("""(?i).*what (did|does|is) (he|she|it|they|him|her).*""")) ||
            lower.matches(Regex("""(?i).*who is (he|she|it|they|him|her).*""")) ||
            lower.matches(Regex("""(?i).*how about (him|her|it|them).*""")) ||
            lower.matches(Regex("""(?i).*what about (him|her|it|them).*""")) ||
            lower.matches(Regex("""(?i).*tell me more (about )?.*""")) ||
            lower.contains("aur batao") || lower.contains("aur kya") || lower.contains("aur bata") ->
                IntentResult(IntentType.GENERAL_CHAT, "follow_up_question")

            // ── GREETING PATTERNS → GENERAL_CHAT (AI handles with personality) ──
            lower.matches(Regex("""(?i)^(hey|hi|hello|yo|sup|what'?s up|hola)\s*(mahi)?$""")) ||
            lower.matches(Regex("""(?i)^(kaise ho|kya hal|kya haal|namaste|namaskar).*""")) ||
            lower.matches(Regex("""(?i)^(good (morning|afternoon|evening|night)).*""")) ||
            lower.matches(Regex("""(?i)^(hey mahi|hello mahi|hi mahi|sup mahi).*""")) ||
            lower.matches(Regex("""(?i)^(how are you|how r u|kya hal hai|kaise ho).*""")) ->
                IntentResult(IntentType.GENERAL_CHAT, "greeting")

            // ── EXISTING HINGLISH/ENGLISH PATTERNS ──

            // Emergency SOS
            lower.contains("emergency") || lower.contains("sos") || lower.contains("help help") || lower.contains("madad") || lower.contains("bachao") || lower.contains("danger") ->
                IntentResult(IntentType.EMERGENCY_SOS, "emergency_sos")

            // Expense tracking
            lower.contains("kharcha") || lower.contains("expense") || lower.contains("spending") || lower.contains("kitna kharcha") ->
                IntentResult(IntentType.EXPENSE_TRACK, if (lower.contains("kitna") || lower.contains("total") || lower.contains("dikhao") || lower.contains("show")) "read_expenses" else "add_expense",
                    mapOf("description" to input))

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

            // Location
            lower.contains("location") || lower.contains("kahan") || lower.contains("where am") ->
                IntentResult(IntentType.LOCATION, "get_location")

            // Call log
            lower.contains("call log") || lower.contains("call history") || lower.contains("recent call") ->
                IntentResult(IntentType.CALL_LOG, "call_log")

            // Notification
            lower.contains("notification") || lower.contains("notif") ->
                IntentResult(IntentType.NOTIFICATION, "read_notifications")

            // Brightness
            lower.contains("brightness") || lower.contains("roshni") ->
                IntentResult(IntentType.DEVICE_CONTROL, "brightness")

            // Volume
            lower.contains("volume") || lower.contains("sound level") || lower.contains("awaaz") ->
                IntentResult(IntentType.DEVICE_CONTROL, "volume")

            else -> IntentResult(IntentType.GENERAL_CHAT, "general_conversation")
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helper functions for extracting params from natural language input
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * SMART contact extraction from natural language input.
     * Uses positional/contextual extraction instead of brute-force word removal.
     * Handles both English and Hinglish patterns.
     */
    private fun extractContactFromInput(input: String, intentType: String = "general"): String {
        val lower = input.lowercase()

        // Hinglish patterns: "ayush ko call karo", "ayush ko whatsapp pe message bhejo"
        val koPattern = Regex("(?i)\\b(\\w+)\\s+ko\\b")
        val koMatch = koPattern.find(lower)
        if (koMatch != null) {
            val name = koMatch.groupValues[1].trim()
            // Filter out common non-name words
            if (name !in listOf("kya", "kaise", "kab", "kahan", "kyu", "mujhe", "tumhe", "unko")) {
                return name
            }
        }

        // English patterns: "call ayush", "send message to ayush"
        val toPattern = Regex("(?i)\\bto\\s+(\\w+)")
        val toMatch = toPattern.find(lower)
        if (toMatch != null) {
            val name = toMatch.groupValues[1].trim()
            if (name !in listOf("the", "a", "an", "my", "me", "him", "her")) {
                return name
            }
        }

        // "call ayush" pattern — word after the verb
        val verbPattern = Regex("(?i)\\b(?:call|phone|ring|dial|text|message)\\s+(\\w+)")
        val verbMatch = verbPattern.find(lower)
        if (verbMatch != null) {
            val name = verbMatch.groupValues[1].trim()
            if (name !in listOf("a", "an", "the", "my", "me", "him", "her", "karo", "please")) {
                return name
            }
        }

        // "ayush ka number" pattern
        val kaPattern = Regex("(?i)\\b(\\w+)\\s+(?:ka|ki)\\s+(?:number|contact|phone)\\b")
        val kaMatch = kaPattern.find(lower)
        if (kaMatch != null) {
            return kaMatch.groupValues[1].trim()
        }

        // Fallback: remove common filler words and return what's left
        val cleaned = input.replace(Regex("(?i)\\b(?:call|phone|ring|dial|text|message|send|whatsapp|wa|from|sim\\s*\\d|on|to|that|the|please|karo|bhejo|ka|number|batao|se|ko|pe|a|an|the|my|me|i|want|need|can|you|will|would|should|could|must|shall|msg|pe|karo|bhejo)\\b"), "").trim()
        return cleaned.ifBlank { "unknown" }
    }

    /**
     * SMART WhatsApp parameter extraction.
     * Extracts both contact and message from WhatsApp commands.
     */
    private fun extractWhatsAppParams(input: String): Map<String, String> {
        val contact = extractContactFromInput(input, "whatsapp")
        val message = extractWhatsAppMessage(input)
        return mapOf("contact" to contact, "message" to message)
    }

    /**
     * Extract the message portion from a WhatsApp command.
     * Handles both English and Hinglish patterns.
     */
    private fun extractWhatsAppMessage(input: String): String {
        val lower = input.lowercase()

        // Hinglish: "ki kal exam hai" → message is after "ki"
        val kiPattern = Regex("(?i)\\bki\\s+(.+?)$")
        val kiMatch = kiPattern.find(lower)
        if (kiMatch != null) {
            return kiMatch.groupValues[1].trim().ifBlank { "" }
        }

        // English: "that I'll be late" → message is after "that"
        val thatPattern = Regex("(?i)\\bthat\\s+(.+?)$")
        val thatMatch = thatPattern.find(lower)
        if (thatMatch != null) {
            return thatMatch.groupValues[1].trim().ifBlank { "" }
        }

        // "send hello on whatsapp" → message is between "send" and "on whatsapp"
        val sendPattern = Regex("(?i)\\b(?:send|bhejo)\\s+(.+?)\\s+(?:on|pe)\\s+(?:whatsapp|wa)\\b")
        val sendMatch = sendPattern.find(lower)
        if (sendMatch != null) {
            return sendMatch.groupValues[1].trim().ifBlank { "" }
        }

        // Quoted text: "whatsapp pe 'hello' bhejo" or "whatsapp pe "hello" bhejo"
        val quotePattern = Regex("[\"'](.+?)[\"']")
        val quoteMatch = quotePattern.find(input)
        if (quoteMatch != null) {
            return quoteMatch.groupValues[1].trim()
        }

        return ""
    }

    /**
     * Extract expense parameters from natural language input.
     * Handles: "500 rupees food", "200 rs ka kharcha transport ka", "expense add 100 food"
     */
    private fun extractExpenseParams(input: String): Map<String, String> {
        val lower = input.lowercase()
        val params = mutableMapOf<String, String>()

        // Extract amount
        val amountPattern = Regex("(\\d+(?:\\.\\d+)?)\\s*(?:rupee|rs|₹|dollar|\\$|rupaye)?", RegexOption.IGNORE_CASE)
        val amountMatch = amountPattern.find(lower)
        if (amountMatch != null) {
            params["amount"] = amountMatch.groupValues[1]
        }

        // Extract category
        val categories = listOf("food", "transport", "shopping", "bills", "entertainment", "khana", "travel", "medical", "education", "rent", "other")
        val foundCategory = categories.firstOrNull { lower.contains(it) }
        params["category"] = foundCategory ?: "other"

        // Extract description (everything after amount/category keywords)
        val descClean = lower
            .replace(Regex("(?i)\\b(?:expense|kharcha|spending|add|save|karo|kiya|hua|hai|track|ki|ka|ke)\\b"), "")
            .replace(Regex("\\d+(?:\\.\\d+)?\\s*(?:rupee|rs|₹|dollar|\\$|rupaye)?"), "")
            .trim()
        if (descClean.isNotBlank()) {
            params["description"] = descClean
        }

        return params
    }

    /**
     * Extract a topic/query from a natural language input string.
     * Used by ultra-fast patterns to populate the "query" param.
     */
    private fun extractTopicFromInput(input: String, removeWords: List<String>): String {
        var cleaned = input
        for (word in removeWords) {
            cleaned = cleaned.replace(Regex("(?i)\\b${Regex.escape(word)}\\b"), "")
        }
        return cleaned.trim().ifBlank { "" }
    }

    /**
     * Extract an app name from a natural language "open/launch X" input.
     * Used by ultra-fast patterns to populate the "app" param.
     */
    private fun extractAppFromInput(input: String): String {
        // Try to extract the word(s) after "open", "launch", "start", "kholo", "chalao"
        val patterns = listOf(
            Regex("(?i)\\b(?:open|launch|start|kholo|chalao)\\s+(?:the\\s+)?(?:app\\s+)?(.+?)(?:\\s+(?:app|application|karo|please))?$"),
            Regex("(?i)\\b(.+?)\\s+(?:kholo|chalao)\\b")
        )
        for (pattern in patterns) {
            val match = pattern.find(input)
            if (match != null && match.groupValues.size > 1) {
                val app = match.groupValues[1].trim()
                if (app.isNotBlank()) return app
            }
        }
        // Fallback: just remove common verbs and return what's left
        return input.replace(Regex("(?i)\\b(?:open|launch|start|kholo|chalao|the|app|application|karo|please)\\b"), "").trim().ifBlank { "" }
    }

    // Keep old methods for backward compatibility with keywordFallback
    private fun extractContact(input: String): String {
        // Use the smart extraction
        return extractContactFromInput(input, "general")
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
