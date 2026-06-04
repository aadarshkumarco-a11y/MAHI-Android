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
 * This means MAHI works OFFLINE for: calls, SMS, WhatsApp, WhatsApp calls, YouTube, weather, news,
 * time, battery, flashlight, alarms, reminders, media control, app launch, gestures, accessibility,
 * clipboard, screenshots, contacts management, navigation, device info, and more.
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
        WHATSAPP_CALL,
        WHATSAPP_VIDEO_CALL,
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
        CONTACT_SAVE,
        CONTACT_DELETE,
        CONTACTS_SHOW,
        TIMER,
        TRANSLATE,
        CALCULATE,
        CONTINUOUS_MODE,
        CAMERA,
        FILE_OPEN,
        EMERGENCY_SOS,
        EXPENSE_TRACK,
        GESTURE,
        ACCESSIBILITY,
        CLIPBOARD,
        SCREENSHOT,
        DEVICE_INFO,
        NAVIGATION,
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
    // ORDER MATTERS: More specific patterns MUST come before generic ones!
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
            Regex("""(?i)\b(?:emergency|sos|help\s+help|madad|bahut\s+mushkil|danger|bachao|save\s+me)\b""")),
        QuickPattern(IntentType.EMERGENCY_SOS, "emergency_sos",
            Regex("""(?i)\b(?:emergency\s+sos|call\s+emergency|emergency\s+call|112\s+call|police\s+call)\b""")),

        // ═══════════════ EXPENSE TRACK — works OFFLINE ═══════════════
        QuickPattern(IntentType.EXPENSE_TRACK, "add_expense",
            Regex("""(?i)\b(?:expense\s+add|add\s+expense|kharcha\s+add|kharcha\s+karo|kharcha\s+kiya|spending\s+add|track\s+expense)\b"""),
            paramExtractor = { match, input -> extractExpenseParams(input) }),
        QuickPattern(IntentType.EXPENSE_TRACK, "add_expense",
            Regex("""(?i)\b\d+\s*(?:rupee|rs|₹|dollar|\$)\s+\w+\s*(?:kharcha|expense|spending)\b"""),
            paramExtractor = { match, input -> extractExpenseParams(input) }),
        QuickPattern(IntentType.EXPENSE_TRACK, "read_expenses",
            Regex("""(?i)\b(?:kitna\s+kharcha|kharcha\s+kitna|total\s+expense|expense\s+total|spending\s+total|aaj\s+ka\s+kharcha|today'?s?\s+expense|week\s+expense|expenses?\s+(?:dikhao|show|read|check))\b""")),
        QuickPattern(IntentType.EXPENSE_TRACK, "add_expense",
            Regex("""(?i)\b(?:kharcha|expense|spending)\s+(?:kiya|kita|hua|hua\s+hai|add|save)\b"""),
            paramExtractor = { match, input -> extractExpenseParams(input) }),

        // ═══════════════ WHATSAPP CALL — MUST come before generic WhatsApp! ═══════════════
        QuickPattern(IntentType.WHATSAPP_CALL, "whatsapp_call",
            Regex("""(?i)\b(?:whatsapp|wa)\s+(?:pe\s+)?(?:call|phone)\s+(?:karo|lagao)?\s*(\w+)"""),
            paramExtractor = { match, input -> mapOf("contact" to (match.groupValues.getOrNull(1)?.trim()?.ifBlank { "unknown" } ?: "unknown")) }),
        QuickPattern(IntentType.WHATSAPP_CALL, "whatsapp_call",
            Regex("""(?i)\b(\w+)\s+ko\s+(?:whatsapp|wa)\s+(?:pe\s+)?(?:call|phone)\s*(?:karo|lagao)?"""),
            paramExtractor = { match, input -> mapOf("contact" to (match.groupValues.getOrNull(1)?.trim()?.ifBlank { "unknown" } ?: "unknown")) }),
        QuickPattern(IntentType.WHATSAPP_CALL, "whatsapp_call",
            Regex("""(?i)\b(?:whatsapp|wa)\s+call\s+(\w+)"""),
            paramExtractor = { match, input -> mapOf("contact" to (match.groupValues.getOrNull(1)?.trim()?.ifBlank { "unknown" } ?: "unknown")) }),
        QuickPattern(IntentType.WHATSAPP_CALL, "whatsapp_call",
            Regex("""(?i)\b(?:whatsapp|wa)\s+(?:pe\s+)?call\s*(?:karo|lagao)?"""),
            paramExtractor = { match, input -> mapOf("contact" to extractContactFromInput(input, "whatsapp_call")) }),
        QuickPattern(IntentType.WHATSAPP_CALL, "whatsapp_call",
            Regex("""(?i)\b\w+\s+ka\s+(?:whatsapp|wa)\s+call"""),
            paramExtractor = { match, input -> mapOf("contact" to extractContactFromInput(input, "whatsapp_call")) }),
        QuickPattern(IntentType.WHATSAPP_CALL, "whatsapp_call",
            Regex("""(?i)\b(?:whatsapp|wa)\s+(?:pe\s+)?phone\s*(?:karo)?"""),
            paramExtractor = { match, input -> mapOf("contact" to extractContactFromInput(input, "whatsapp_call")) }),

        // ═══════════════ WHATSAPP VIDEO CALL — MUST come before generic WhatsApp! ═══════════════
        QuickPattern(IntentType.WHATSAPP_VIDEO_CALL, "whatsapp_video_call",
            Regex("""(?i)\b(?:whatsapp|wa)\s+(?:pe\s+)?video\s+call\s*(?:karo)?\s*(\w+)?"""),
            paramExtractor = { match, input -> mapOf("contact" to (match.groupValues.getOrNull(1)?.trim()?.ifBlank { "unknown" } ?: "unknown")) }),
        QuickPattern(IntentType.WHATSAPP_VIDEO_CALL, "whatsapp_video_call",
            Regex("""(?i)\b(\w+)\s+ko\s+(?:whatsapp|wa)\s+(?:pe\s+)?video\s+call\s*(?:karo)?"""),
            paramExtractor = { match, input -> mapOf("contact" to (match.groupValues.getOrNull(1)?.trim()?.ifBlank { "unknown" } ?: "unknown")) }),
        QuickPattern(IntentType.WHATSAPP_VIDEO_CALL, "whatsapp_video_call",
            Regex("""(?i)\bvideo\s+call\s+(?:karo\s+)?(\w+)"""),
            paramExtractor = { match, input -> mapOf("contact" to (match.groupValues.getOrNull(1)?.trim()?.ifBlank { "unknown" } ?: "unknown")) }),
        QuickPattern(IntentType.WHATSAPP_VIDEO_CALL, "whatsapp_video_call",
            Regex("""(?i)\b(\w+)\s+ko\s+video\s+call\s*(?:karo)?"""),
            paramExtractor = { match, input -> mapOf("contact" to (match.groupValues.getOrNull(1)?.trim()?.ifBlank { "unknown" } ?: "unknown")) }),
        QuickPattern(IntentType.WHATSAPP_VIDEO_CALL, "whatsapp_video_call",
            Regex("""(?i)\b(?:whatsapp|wa)\s+video\s*(\w+)?"""),
            paramExtractor = { match, input -> mapOf("contact" to (match.groupValues.getOrNull(1)?.trim()?.ifBlank { "unknown" } ?: "unknown")) }),
        QuickPattern(IntentType.WHATSAPP_VIDEO_CALL, "whatsapp_video_call",
            Regex("""(?i)\b(\w+)\s+ka\s+video\s+call"""),
            paramExtractor = { match, input -> mapOf("contact" to (match.groupValues.getOrNull(1)?.trim()?.ifBlank { "unknown" } ?: "unknown")) }),

        // ═══════════════ WHATSAPP SEARCH — MUST come before generic WhatsApp! ═══════════════
        QuickPattern(IntentType.WHATSAPP, "whatsapp_search",
            Regex("""(?i)\b(?:whatsapp|wa)\s+(?:mein|pe|par)\s+(\w+)\s+(?:dhoondo|search|find|khojo|dhundo|talash)"""),
            paramExtractor = { match, input -> mapOf("contact" to (match.groupValues.getOrNull(1)?.trim()?.ifBlank { "unknown" } ?: "unknown")) }),
        QuickPattern(IntentType.WHATSAPP, "whatsapp_search",
            Regex("""(?i)\b(\w+)\s+ko\s+(?:whatsapp|wa)\s+(?:mein|pe)\s+(?:dhoondo|search|find)"""),
            paramExtractor = { match, input -> mapOf("contact" to (match.groupValues.getOrNull(1)?.trim()?.ifBlank { "unknown" } ?: "unknown")) }),

        // ═══════════════ CONTACT SAVE — MUST come before CONTACT_SEARCH! ═══════════════
        QuickPattern(IntentType.CONTACT_SAVE, "save_contact",
            Regex("""(?i)\b(?:save|add)\s+(?:contact|number)\b"""),
            paramExtractor = { match, input -> extractContactSaveParams(input) }),
        QuickPattern(IntentType.CONTACT_SAVE, "save_contact",
            Regex("""(?i)\b(?:contact|number)\s+(?:save|add)\s*(?:karo)?\b"""),
            paramExtractor = { match, input -> extractContactSaveParams(input) }),
        QuickPattern(IntentType.CONTACT_SAVE, "save_contact",
            Regex("""(?i)\b\w+\s+(?:ka\s+)?(?:number|contact)\s+\d+\s*(?:save|add)"""),
            paramExtractor = { match, input -> extractContactSaveParams(input) }),
        QuickPattern(IntentType.CONTACT_SAVE, "save_contact",
            Regex("""(?i)\b(?:naya\s+)?contact\s+(?:save|add)\s*karo\b"""),
            paramExtractor = { match, input -> extractContactSaveParams(input) }),
        QuickPattern(IntentType.CONTACT_SAVE, "save_contact",
            Regex("""(?i)\b\w+\s+ko\s+(?:contacts?\s+)?(?:mein\s+)?(?:add|save)\s*karo\b"""),
            paramExtractor = { match, input -> extractContactSaveParams(input) }),
        QuickPattern(IntentType.CONTACT_SAVE, "save_contact",
            Regex("""(?i)\b\w+\s+\d{7,15}\s*(?:save|yad|rakh)\b"""),
            paramExtractor = { match, input -> extractContactSaveParams(input) }),

        // ═══════════════ CONTACT DELETE — MUST come before CONTACT_SEARCH! ═══════════════
        QuickPattern(IntentType.CONTACT_DELETE, "delete_contact",
            Regex("""(?i)\b(?:delete|remove|erase|mitao|hatao)\s+(?:contact|number)\b"""),
            paramExtractor = { match, input -> mapOf("contact" to extractContactFromInput(input, "contact_delete")) }),
        QuickPattern(IntentType.CONTACT_DELETE, "delete_contact",
            Regex("""(?i)\b(?:contact|number)\s+(?:delete|remove|erase|mitao)\b"""),
            paramExtractor = { match, input -> mapOf("contact" to extractContactFromInput(input, "contact_delete")) }),
        QuickPattern(IntentType.CONTACT_DELETE, "delete_contact",
            Regex("""(?i)\b(\w+)\s+ko\s+(?:contacts?\s+)?(?:se\s+)?(?:delete|remove|hatao|mitao|erase)\b"""),
            paramExtractor = { match, input -> mapOf("contact" to (match.groupValues.getOrNull(1)?.trim()?.ifBlank { "unknown" } ?: "unknown")) }),
        QuickPattern(IntentType.CONTACT_DELETE, "delete_contact",
            Regex("""(?i)\b(\w+)\s+ka\s+(?:contact|number)\s+(?:delete|remove|mitao)\b"""),
            paramExtractor = { match, input -> mapOf("contact" to (match.groupValues.getOrNull(1)?.trim()?.ifBlank { "unknown" } ?: "unknown")) }),

        // ═══════════════ CONTACTS SHOW — MUST come before CONTACT_SEARCH! ═══════════════
        QuickPattern(IntentType.CONTACTS_SHOW, "show_contacts",
            Regex("""(?i)\b(?:contacts?|phone\s*book)\s+(?:dikhao|show|kholo|list)\b""")),
        QuickPattern(IntentType.CONTACTS_SHOW, "show_contacts",
            Regex("""(?i)\b(?:show|display|open)\s+(?:my\s+)?(?:contacts?|phone\s*book)\b""")),
        QuickPattern(IntentType.CONTACTS_SHOW, "show_contacts",
            Regex("""(?i)\b(?:all\s+)?contacts?\s+(?:list|dikhao|kholo)\b""")),
        QuickPattern(IntentType.CONTACTS_SHOW, "show_contacts",
            Regex("""(?i)\b(?:mere|meri)\s+contacts?\b""")),

        // ═══════════════ SCREENSHOT — works OFFLINE ═══════════════
        QuickPattern(IntentType.SCREENSHOT, "take_screenshot",
            Regex("""(?i)\b(?:screenshot|screen\s*shot|screen\s+capture|capture\s+screen)\s*(?:lo|karo|le|le\s+lo)?\b""")),
        QuickPattern(IntentType.SCREENSHOT, "take_screenshot",
            Regex("""(?i)\bcapture\s*(?:karo)?\b""")),
        QuickPattern(IntentType.SCREENSHOT, "take_screenshot",
            Regex("""(?i)\bscreen\s+ka\s+(?:photo|picture)\b""")),

        // ═══════════════ DEVICE INFO — works OFFLINE ═══════════════
        QuickPattern(IntentType.DEVICE_INFO, "device_info",
            Regex("""(?i)\b(?:device|phone|mobile)\s+info\b""")),
        QuickPattern(IntentType.DEVICE_INFO, "device_info",
            Regex("""(?i)\b(?:phone|mobile|device)\s+ka\s+(?:info|information|details|detail)\b""")),

        // ═══════════════ GESTURE — MUST come before APP_LAUNCH! ═══════════════
        // Lock screen
        QuickPattern(IntentType.GESTURE, "lock_screen",
            Regex("""(?i)\b(?:lock|screen\s+lock|phone\s+lock|mobile\s+lock)\s*(?:karo|kar\s*do|screen)?\b""")),
        QuickPattern(IntentType.GESTURE, "lock_screen",
            Regex("""(?i)\bscreen\s*(?:band|off)\s*(?:karo)?\b""")),
        QuickPattern(IntentType.GESTURE, "lock_screen",
            Regex("""(?i)\bphone\s+band\s*karo\b""")),

        // Scroll
        QuickPattern(IntentType.GESTURE, "scroll_down",
            Regex("""(?i)\bscroll\s+(?:down|neeche|aage)\b""")),
        QuickPattern(IntentType.GESTURE, "scroll_down",
            Regex("""(?i)\bpage\s+(?:down|neeche)\b""")),
        QuickPattern(IntentType.GESTURE, "scroll_up",
            Regex("""(?i)\bscroll\s+(?:up|upar|piche)\b""")),
        QuickPattern(IntentType.GESTURE, "scroll_up",
            Regex("""(?i)\bpage\s+(?:up|upar)\b""")),

        // Swipe
        QuickPattern(IntentType.GESTURE, "swipe_left",
            Regex("""(?i)\bswipe\s+(?:left|bayein)\b""")),
        QuickPattern(IntentType.GESTURE, "swipe_left",
            Regex("""(?i)\b(?:left|bayein)\s+(?:jao|swipe|scroll)\b""")),
        QuickPattern(IntentType.GESTURE, "swipe_left",
            Regex("""(?i)\bpichla\s+page\b""")),
        QuickPattern(IntentType.GESTURE, "swipe_right",
            Regex("""(?i)\bswipe\s+(?:right|dahine)\b""")),
        QuickPattern(IntentType.GESTURE, "swipe_right",
            Regex("""(?i)\b(?:right|dahine)\s+(?:jao|swipe|scroll)\b""")),
        QuickPattern(IntentType.GESTURE, "swipe_right",
            Regex("""(?i)\bagla\s+page\b""")),

        // Back
        QuickPattern(IntentType.GESTURE, "go_back",
            Regex("""(?i)\bback\s*(?:jao|karo|button)?\b""")),
        QuickPattern(IntentType.GESTURE, "go_back",
            Regex("""(?i)\bpiche\s*(?:jao|hato)?\b""")),
        QuickPattern(IntentType.GESTURE, "go_back",
            Regex("""(?i)\bwapis\s+jao\b""")),
        QuickPattern(IntentType.GESTURE, "go_back",
            Regex("""(?i)\bback\s+button\s+dabao\b""")),

        // Home
        QuickPattern(IntentType.GESTURE, "go_home",
            Regex("""(?i)\bhome\s*(?:jao|karo|screen|button)?\b""")),
        QuickPattern(IntentType.GESTURE, "go_home",
            Regex("""(?i)\bbahar\s+jao\b""")),
        QuickPattern(IntentType.GESTURE, "go_home",
            Regex("""(?i)\bghar\s+jao\b""")),
        QuickPattern(IntentType.GESTURE, "go_home",
            Regex("""(?i)\bhome\s+screen\s*(?:jao|par\s+jao)?\b""")),
        QuickPattern(IntentType.GESTURE, "go_home",
            Regex("""(?i)\bmain\s+screen\s+par\s+jao\b""")),
        QuickPattern(IntentType.GESTURE, "go_home",
            Regex("""(?i)\bdesktop\s+jao\b""")),
        QuickPattern(IntentType.GESTURE, "go_home",
            Regex("""(?i)\bclose\s+(?:all|app)\b""")),
        QuickPattern(IntentType.GESTURE, "go_home",
            Regex("""(?i)\bsab\s+band\s+karo\b""")),
        QuickPattern(IntentType.GESTURE, "go_home",
            Regex("""(?i)\bexit\s+karo\b""")),
        QuickPattern(IntentType.GESTURE, "go_home",
            Regex("""(?i)\bstop\s+all\b""")),

        // Recent apps
        QuickPattern(IntentType.GESTURE, "recent_apps",
            Regex("""(?i)\brecent(?:s)?\s*(?:kholo|dikhao|jao|apps|mein|screen)?\b""")),
        QuickPattern(IntentType.GESTURE, "recent_apps",
            Regex("""(?i)\bapp\s+switcher\s+kholo\b""")),

        // Notifications panel
        QuickPattern(IntentType.GESTURE, "open_notifications",
            Regex("""(?i)\bnotifications?\s+(?:kholo|dikhao|check|bar|panel)\b""")),
        QuickPattern(IntentType.GESTURE, "open_notifications",
            Regex("""(?i)\bnotification\s+(?:dekh|khol\s+do)\b""")),

        // Quick settings
        QuickPattern(IntentType.GESTURE, "quick_settings",
            Regex("""(?i)\bquick\s+(?:settings|panel|toggles)\s*(?:kholo|dikhao)?\b""")),
        QuickPattern(IntentType.GESTURE, "quick_settings",
            Regex("""(?i)\bsettings\s+(?:panel|shortcut)\s*(?:kholo|dikhao)?\b""")),

        // ═══════════════ ACCESSIBILITY — works OFFLINE ═══════════════
        QuickPattern(IntentType.ACCESSIBILITY, "click_text",
            Regex("""(?i)\b(?:click|tap|press|dabao)\s+(?:(?:on|par|ko)\s+)?(\w+)"""),
            paramExtractor = { match, input -> mapOf("text" to (match.groupValues.getOrNull(1)?.trim()?.ifBlank { "" } ?: "")) }),
        QuickPattern(IntentType.ACCESSIBILITY, "click_text",
            Regex("""(?i)\b(\w+)\s+(?:par|pe|ko)\s+(?:click|tap|press|dabao)\b"""),
            paramExtractor = { match, input -> mapOf("text" to (match.groupValues.getOrNull(1)?.trim()?.ifBlank { "" } ?: "")) }),
        QuickPattern(IntentType.ACCESSIBILITY, "click_button",
            Regex("""(?i)\bbutton\s+(?:click|dabao|press|tap)\b""")),
        QuickPattern(IntentType.ACCESSIBILITY, "click_button",
            Regex("""(?i)\b(?:click|tap|press)\s+button\b""")),
        QuickPattern(IntentType.ACCESSIBILITY, "click_button",
            Regex("""(?i)\bjo\s+button\s+(?:dikh\s+raha\s+hai|hai)\b""")),
        QuickPattern(IntentType.ACCESSIBILITY, "type_text",
            Regex("""(?i)\btype\s+(.+)"""),
            paramExtractor = { match, input -> mapOf("text" to (match.groupValues.getOrNull(1)?.trim()?.ifBlank { "" } ?: "")) }),
        QuickPattern(IntentType.ACCESSIBILITY, "type_text",
            Regex("""(?i)\bwrite\s+(.+)"""),
            paramExtractor = { match, input -> mapOf("text" to (match.groupValues.getOrNull(1)?.trim()?.ifBlank { "" } ?: "")) }),
        QuickPattern(IntentType.ACCESSIBILITY, "type_text",
            Regex("""(?i)\blikh\s*(?:do|o)?\s+(.+)"""),
            paramExtractor = { match, input -> mapOf("text" to (match.groupValues.getOrNull(1)?.trim()?.ifBlank { "" } ?: "")) }),
        QuickPattern(IntentType.ACCESSIBILITY, "type_text",
            Regex("""(?i)\benter\s+(.+)"""),
            paramExtractor = { match, input -> mapOf("text" to (match.groupValues.getOrNull(1)?.trim()?.ifBlank { "" } ?: "")) }),
        QuickPattern(IntentType.ACCESSIBILITY, "type_text",
            Regex("""(?i)\binput\s+(.+)"""),
            paramExtractor = { match, input -> mapOf("text" to (match.groupValues.getOrNull(1)?.trim()?.ifBlank { "" } ?: "")) }),
        QuickPattern(IntentType.ACCESSIBILITY, "screen_read",
            Regex("""(?i)\bscreen\s+(?:read|padho|par\s+kya\s+hai|content|mein\s+kya)\b""")),
        QuickPattern(IntentType.ACCESSIBILITY, "screen_read",
            Regex("""(?i)\byeh\s+(?:kya\s+hai|page\s+kya)\b""")),
        QuickPattern(IntentType.ACCESSIBILITY, "screen_read",
            Regex("""(?i)\bpage\s+read\b""")),

        // ═══════════════ CLIPBOARD — works OFFLINE ═══════════════
        QuickPattern(IntentType.CLIPBOARD, "copy",
            Regex("""(?i)\b(?:copy|clipboard)\s*(?:karo|kar\s+lo|mein|text)?\b""")),
        QuickPattern(IntentType.CLIPBOARD, "copy",
            Regex("""(?i)\bsave\s+to\s+clipboard\b""")),
        QuickPattern(IntentType.CLIPBOARD, "copy",
            Regex("""(?i)\btext\s+copy\b""")),
        QuickPattern(IntentType.CLIPBOARD, "paste",
            Regex("""(?i)\bpaste\s*(?:karo|kar\s+do|yahan)?\b""")),
        QuickPattern(IntentType.CLIPBOARD, "paste",
            Regex("""(?i)\bclipboard\s+(?:se\s+)?paste\b""")),
        QuickPattern(IntentType.CLIPBOARD, "paste",
            Regex("""(?i)\bjo\s+copy\s+kiya\s+tha\s+paste\b""")),
        QuickPattern(IntentType.CLIPBOARD, "read_clipboard",
            Regex("""(?i)\bclipboard\s+(?:mein\s+)?kya\s+hai\b""")),
        QuickPattern(IntentType.CLIPBOARD, "read_clipboard",
            Regex("""(?i)\bclipboard\s+(?:batao|read|check|content)\b""")),

        // ═══════════════ NAVIGATION — works OFFLINE ═══════════════
        QuickPattern(IntentType.NAVIGATION, "navigate_to",
            Regex("""(?i)\b(?:navigate|navigation)\s+(?:to\s+)?(.+)"""),
            paramExtractor = { match, input -> mapOf("location" to (match.groupValues.getOrNull(1)?.trim()?.ifBlank { "" } ?: "")) }),
        QuickPattern(IntentType.NAVIGATION, "navigate_to",
            Regex("""(?i)\b\w+\s+ka\s+(?:raasta|rasta|direction|map)\b"""),
            paramExtractor = { match, input -> extractNavigationLocation(input) }),
        QuickPattern(IntentType.NAVIGATION, "open_maps",
            Regex("""(?i)\b(?:maps?|navigation)\s+(?:kholo|open|chalao|launch)\b""")),
        QuickPattern(IntentType.NAVIGATION, "open_maps",
            Regex("""(?i)\b(?:google\s+)?maps?\s+kholo\b""")),

        // ═══════════════ AIRPLANE MODE — works OFFLINE ═══════════════
        QuickPattern(IntentType.DEVICE_CONTROL, "airplane_on",
            Regex("""(?i)\b(?:airplane|flight)\s+mode\s+(?:on|chalu|active|enable|lagao|chal)\b""")),
        QuickPattern(IntentType.DEVICE_CONTROL, "airplane_on",
            Regex("""(?i)\b(?:airplane|flight)\s+(?:on|lagao|chal)\b""")),
        QuickPattern(IntentType.DEVICE_CONTROL, "airplane_off",
            Regex("""(?i)\b(?:airplane|flight)\s+mode\s+(?:off|band|disable|hatao|stop)\b""")),
        QuickPattern(IntentType.DEVICE_CONTROL, "airplane_off",
            Regex("""(?i)\b(?:airplane|flight)\s+(?:off|hatao|band)\b""")),

        // ═══════════════ SILENT/VIBRATE/NORMAL MODE — works OFFLINE ═══════════════
        QuickPattern(IntentType.DEVICE_CONTROL, "silent_mode",
            Regex("""(?i)\b(?:silent|khamosh)\s*(?:mode|karo|kar\s*do)?\b""")),
        QuickPattern(IntentType.DEVICE_CONTROL, "silent_mode",
            Regex("""(?i)\bphone\s+(?:chup|silent)\b""")),
        QuickPattern(IntentType.DEVICE_CONTROL, "silent_mode",
            Regex("""(?i)\bno\s+sound\b""")),
        QuickPattern(IntentType.DEVICE_CONTROL, "vibrate_mode",
            Regex("""(?i)\bvibrat(?:e|ion|ing)\s*(?:mode|karo|kar\s*do|on)?\b""")),
        QuickPattern(IntentType.DEVICE_CONTROL, "vibrate_mode",
            Regex("""(?i)\bphone\s+vibrat\w+\b""")),
        QuickPattern(IntentType.DEVICE_CONTROL, "normal_mode",
            Regex("""(?i)\bnormal\s*(?:mode|karo|kar\s*do)?\b""")),
        QuickPattern(IntentType.DEVICE_CONTROL, "normal_mode",
            Regex("""(?i)\bgeneral\s+mode\b""")),
        QuickPattern(IntentType.DEVICE_CONTROL, "normal_mode",
            Regex("""(?i)\bsound\s+(?:on|wapas|wapas\s+lao)\b""")),

        // ═══════════════ SET VOLUME (specific level) — works OFFLINE ═══════════════
        QuickPattern(IntentType.DEVICE_CONTROL, "set_volume",
            Regex("""(?i)\b(?:volume|sound|awaz|awaaz)\s+\d+\s*(?:set|karo|par)?\b"""),
            paramExtractor = { match, input -> extractVolumeLevel(input) }),
        QuickPattern(IntentType.DEVICE_CONTROL, "set_volume",
            Regex("""(?i)\b(?:volume|sound)\s+\d+\s*\b"""),
            paramExtractor = { match, input -> extractVolumeLevel(input) }),

        // ═══════════════ DND VARIATIONS — works OFFLINE ═══════════════
        QuickPattern(IntentType.DEVICE_CONTROL, "dnd_on",
            Regex("""(?i)\bdnd\s+(?:on|chalu|active|lagao|chal)\b""")),
        QuickPattern(IntentType.DEVICE_CONTROL, "dnd_on",
            Regex("""(?i)\bdo\s+not\s+disturb\s+(?:on|chalu|enable|active)\b""")),
        QuickPattern(IntentType.DEVICE_CONTROL, "dnd_on",
            Regex("""(?i)\bdisturb\s+mat\s+karo\b""")),
        QuickPattern(IntentType.DEVICE_CONTROL, "dnd_off",
            Regex("""(?i)\bdnd\s+(?:off|band|disable|hatao|stop)\b""")),
        QuickPattern(IntentType.DEVICE_CONTROL, "dnd_off",
            Regex("""(?i)\bdo\s+not\s+disturb\s+(?:off|band|disable|hatao)\b""")),
        QuickPattern(IntentType.DEVICE_CONTROL, "dnd_off",
            Regex("""(?i)\bdisturb\s+kar\s+sakte\s+ho\b""")),

        // ═══════════════ WIFI/BLUETOOTH HINGLISH — works OFFLINE ═══════════════
        QuickPattern(IntentType.DEVICE_CONTROL, "wifi_on",
            Regex("""(?i)\b(?:wifi|wi-?fi)\s+(?:on|chalu|start|khol|chal|active|connect)\b""")),
        QuickPattern(IntentType.DEVICE_CONTROL, "wifi_off",
            Regex("""(?i)\b(?:wifi|wi-?fi)\s+(?:off|band|stop|hata|shut\s+down|inactive|disconnect)\b""")),
        QuickPattern(IntentType.DEVICE_CONTROL, "wifi_on",
            Regex("""(?i)\b(?:wifi|wi-?fi)\s+ko\s+on\s+karo\b""")),
        QuickPattern(IntentType.DEVICE_CONTROL, "wifi_off",
            Regex("""(?i)\b(?:wifi|wi-?fi)\s+ko\s+off\s+karo\b""")),
        QuickPattern(IntentType.DEVICE_CONTROL, "bluetooth_on",
            Regex("""(?i)\bbluetooth\s+(?:on|chalu|start|khol|chal|active)\b""")),
        QuickPattern(IntentType.DEVICE_CONTROL, "bluetooth_on",
            Regex("""(?i)\bblue\s+on\s+karo\b""")),
        QuickPattern(IntentType.DEVICE_CONTROL, "bluetooth_off",
            Regex("""(?i)\bbluetooth\s+(?:off|band|stop|hata|shut\s+down|inactive)\b""")),
        QuickPattern(IntentType.DEVICE_CONTROL, "bluetooth_off",
            Regex("""(?i)\bblue\s+off\s+karo\b""")),

        // ═══════════════ FLASHLIGHT HINGLISH — works OFFLINE ═══════════════
        QuickPattern(IntentType.DEVICE_CONTROL, "flashlight_on",
            Regex("""(?i)\b(?:turn\s+on|switch\s+on|enable|on\s+karo|jala)\s+(?:the\s+)?(?:flashlight|torch|flash|light)\b""")),
        QuickPattern(IntentType.DEVICE_CONTROL, "flashlight_off",
            Regex("""(?i)\b(?:turn\s+off|switch\s+off|disable|off\s+karo|bujha)\s+(?:the\s+)?(?:flashlight|torch|flash|light)\b""")),
        QuickPattern(IntentType.DEVICE_CONTROL, "flashlight_on",
            Regex("""(?i)\b(?:flashlight|torch)\s+(?:on|jala|chalu)\b""")),
        QuickPattern(IntentType.DEVICE_CONTROL, "flashlight_off",
            Regex("""(?i)\b(?:flashlight|torch)\s+(?:off|bujha|band)\b""")),
        QuickPattern(IntentType.DEVICE_CONTROL, "flashlight_on",
            Regex("""(?i)\b(?:flashlight|torch|flash)\s+(?:chalu|enable)\b""")),
        QuickPattern(IntentType.DEVICE_CONTROL, "flashlight_off",
            Regex("""(?i)\b(?:flashlight|torch|flash)\s+band\b""")),
        QuickPattern(IntentType.DEVICE_CONTROL, "flashlight_on",
            Regex("""(?i)\blight\s+(?:on|khol|chalu)\s*(?:karo|do)?\b""")),
        QuickPattern(IntentType.DEVICE_CONTROL, "flashlight_off",
            Regex("""(?i)\blight\s+(?:off|band)\s*(?:karo)?\b""")),
        QuickPattern(IntentType.DEVICE_CONTROL, "flashlight_on",
            Regex("""(?i)\broshni\s+(?:khol|badhao|tej)\b""")),
        QuickPattern(IntentType.DEVICE_CONTROL, "flashlight_off",
            Regex("""(?i)\broshni\s+(?:band|kam)\b""")),
        QuickPattern(IntentType.DEVICE_CONTROL, "flashlight_toggle",
            Regex("""(?i)\b(?:flashlight|torch)\b""")),

        // ═══════════════ VOLUME HINGLISH — works OFFLINE ═══════════════
        QuickPattern(IntentType.DEVICE_CONTROL, "volume_up",
            Regex("""(?i)\b(?:volume|sound|awaaz)\s+(?:up|increase|badhao|tez|loud)\b""")),
        QuickPattern(IntentType.DEVICE_CONTROL, "volume_up",
            Regex("""(?i)\bawaz\s+(?:badhao|tej|barhao|up)\b""")),
        QuickPattern(IntentType.DEVICE_CONTROL, "volume_up",
            Regex("""(?i)\b(?:sound|awaz|awaaz)\s+(?:tej|badhao|up)\b""")),
        QuickPattern(IntentType.DEVICE_CONTROL, "volume_down",
            Regex("""(?i)\b(?:volume|sound|awaaz)\s+(?:down|decrease|kam|dhima|low|quiet)\b""")),
        QuickPattern(IntentType.DEVICE_CONTROL, "volume_down",
            Regex("""(?i)\bawaz\s+(?:kam|ghatao|down)\b""")),
        QuickPattern(IntentType.DEVICE_CONTROL, "volume_down",
            Regex("""(?i)\b(?:sound|awaz|awaaz)\s+(?:kam|ghatao|down)\b""")),
        QuickPattern(IntentType.DEVICE_CONTROL, "volume_mute",
            Regex("""(?i)\b(?:mute|silent|khamosh)\s*(?:volume|sound|phone)?\b""")),
        QuickPattern(IntentType.DEVICE_CONTROL, "volume_mute",
            Regex("""(?i)\b(?:chup\s+(?:karo|ho\s+jao)|awaz\s+band|sound\s+band)\b""")),
        QuickPattern(IntentType.DEVICE_CONTROL, "volume_mute",
            Regex("""(?i)\bawaz\s+(?:band|hatao)\b""")),
        QuickPattern(IntentType.DEVICE_CONTROL, "volume",
            Regex("""(?i)\b(?:volume|sound\s+level|awaaz)\b""")),

        // ═══════════════ BRIGHTNESS HINGLISH — works OFFLINE ═══════════════
        QuickPattern(IntentType.DEVICE_CONTROL, "brightness_up",
            Regex("""(?i)\b(?:brightness|screen\s+brightness|roshni|screen\s+roshni)\s+(?:up|increase|badhao|tez)\b""")),
        QuickPattern(IntentType.DEVICE_CONTROL, "brightness_up",
            Regex("""(?i)\b(?:screen|display)\s+(?:bright|tej)\b""")),
        QuickPattern(IntentType.DEVICE_CONTROL, "brightness_down",
            Regex("""(?i)\b(?:brightness|screen\s+brightness|roshni|screen\s+roshni)\s+(?:down|decrease|kam)\b""")),
        QuickPattern(IntentType.DEVICE_CONTROL, "brightness_down",
            Regex("""(?i)\b(?:screen|display)\s+(?:dim|kam)\b""")),
        QuickPattern(IntentType.DEVICE_CONTROL, "brightness",
            Regex("""(?i)\b(?:brightness|roshni)\b""")),

        // ═══════════════ SMART ROUTINES — works OFFLINE ═══════════════
        QuickPattern(IntentType.ROUTINE, "good_night",
            Regex("""(?i)\b(?:good\s+)?night\s*(?:mode|karo|time)?\b""")),
        QuickPattern(IntentType.ROUTINE, "good_night",
            Regex("""(?i)\b(?:so\s+(?:ja|jao)|raat\s+(?:ho\s+gai|ka\s+mode)|sleep\s+mode)\b""")),
        QuickPattern(IntentType.ROUTINE, "good_morning",
            Regex("""(?i)\b(?:good\s+)?morning\s*(?:mode|karo|time)?\b""")),
        QuickPattern(IntentType.ROUTINE, "good_morning",
            Regex("""(?i)\b(?:subah\s*(?:ho\s+gai|ka\s+mode)?|din\s+shuru)\b""")),
        QuickPattern(IntentType.ROUTINE, "work_mode",
            Regex("""(?i)\b(?:work|kaam|office|productive|focus)\s+mode\b""")),
        QuickPattern(IntentType.ROUTINE, "work_mode",
            Regex("""(?i)\bkaam\s+shuru\s+karo\b""")),
        QuickPattern(IntentType.ROUTINE, "driving_mode",
            Regex("""(?i)\b(?:driving|drive|gaadi|car|road|travel|journey)\s*(?:mode|chal\s+raha\s+hoon)?\b""")),
        QuickPattern(IntentType.ROUTINE, "meeting_mode",
            Regex("""(?i)\bmeeting\s*(?:mode|mein|chalu|laga\s+do|time|karo|silent)?\b""")),

        // ═══════════════ SETTINGS (open specific settings) — works OFFLINE ═══════════════
        QuickPattern(IntentType.APP_LAUNCH, "launch_app",
            Regex("""(?i)\b(?:wifi|wi-?fi)\s+settings\s*(?:kholo|mein\s+jao|dikhao|open)?\b"""),
            paramExtractor = { match, input -> mapOf("app" to "wifi_settings") }),
        QuickPattern(IntentType.APP_LAUNCH, "launch_app",
            Regex("""(?i)\bbluetooth\s+settings\s*(?:kholo|mein\s+jao|dikhao|open)?\b"""),
            paramExtractor = { match, input -> mapOf("app" to "bluetooth_settings") }),
        QuickPattern(IntentType.APP_LAUNCH, "launch_app",
            Regex("""(?i)\b(?:sound|audio|awaz)\s+settings\s*(?:kholo|mein\s+jao|dikhao|open)?\b"""),
            paramExtractor = { match, input -> mapOf("app" to "sound_settings") }),
        QuickPattern(IntentType.APP_LAUNCH, "launch_app",
            Regex("""(?i)\b(?:display|screen)\s+settings\s*(?:kholo|mein\s+jao|dikhao|open)?\b"""),
            paramExtractor = { match, input -> mapOf("app" to "display_settings") }),
        QuickPattern(IntentType.APP_LAUNCH, "launch_app",
            Regex("""(?i)\bbattery\s+settings\s*(?:kholo|mein\s+jao|dikhao|open)?\b"""),
            paramExtractor = { match, input -> mapOf("app" to "battery_settings") }),

        // ═══════════════ VIDEO CAMERA — works OFFLINE ═══════════════
        QuickPattern(IntentType.CAMERA, "video_camera",
            Regex("""(?i)\b(?:video\s+)?camera\s+(?:video\s+)?mode\s*(?:kholo|chalao|open)?\b""")),
        QuickPattern(IntentType.CAMERA, "video_camera",
            Regex("""(?i)\bvideo\s+(?:camera|record|mode|cam)\s*(?:kholo|chalao|start|open)?\b""")),
        QuickPattern(IntentType.CAMERA, "video_camera",
            Regex("""(?i)\brecording\s+start\b""")),

        // ═══════════════ FLASHLIGHT (original English patterns) — works OFFLINE ═══════════════
        // NOTE: Hinglish flashlight patterns are above, these are the original English patterns

        // ═══════════════ BATTERY — works OFFLINE ═══════════════
        QuickPattern(IntentType.BATTERY, "battery_status",
            Regex("""(?i)\b(?:battery|charge|charging)\s*(?:level|status|percentage|info|check|hai|kitni|kitna|kaisa)?\s*""")),
        QuickPattern(IntentType.BATTERY, "battery_status",
            Regex("""(?i)\b(?:battery\s+kitni|charge\s+kitna|kitni\s+battery|kitna\s+charge)\b""")),

        // ═══════════════ TIME/DATE — works OFFLINE ═══════════════
        QuickPattern(IntentType.TIME_DATE, "get_time",
            Regex("""(?i)\b(?:what'?s\s+)?(?:the\s+)?(?:time|clock|samay|baje|kitne\s+baje)\b""")),
        QuickPattern(IntentType.TIME_DATE, "get_time",
            Regex("""(?i)\b(?:kitne\s+baje|time\s+batao|time\s+kya|samay\s+kya|samay\s+batao)\b""")),
        QuickPattern(IntentType.TIME_DATE, "get_date",
            Regex("""(?i)\b(?:what'?s\s+)?(?:the\s+)?(?:date|day|today|tarikh|din|aaj)\b""")),
        QuickPattern(IntentType.TIME_DATE, "get_date",
            Regex("""(?i)\b(?:aaj\s+tarikh|aaj\s+din|tarikh\s+batao|date\s+kya)\b""")),

        // ═══════════════ CALL — works OFFLINE ═══════════════
        QuickPattern(IntentType.CALL, "make_call",
            Regex("""(?i)\b(?:call|phone|ring|dial)\s+\w+"""),
            paramExtractor = { match, input -> mapOf("contact" to extractContactFromInput(input, "call")) }),
        QuickPattern(IntentType.CALL, "make_call",
            Regex("""(?i)\b\w+\s+ko\s+(?:call|phone|ring)\s*(?:karo)?\b"""),
            paramExtractor = { match, input -> mapOf("contact" to extractContactFromInput(input, "call")) }),
        QuickPattern(IntentType.CALL, "make_call",
            Regex("""(?i)\b(?:call|phone|ring|dial)\s+(?:karo|kar)\b""")),
        QuickPattern(IntentType.CALL, "make_call",
            Regex("""(?i)\b(?:make\s+a\s+call|place\s+a\s+call|phone\s+call)\b""")),

        // ═══════════════ YOUTUBE — works OFFLINE ═══════════════
        QuickPattern(IntentType.YOUTUBE, "search_youtube",
            Regex("""(?i)\b(?:play|watch|search)\s+.+\s+(?:on\s+)?(?:youtube|yt)\b"""),
            paramExtractor = { match, input -> mapOf("query" to extractTopicFromInput(input, listOf("play", "watch", "search", "on", "youtube", "yt", "chalao", "pe"))) }),
        QuickPattern(IntentType.YOUTUBE, "search_youtube",
            Regex("""(?i)\b(?:youtube|yt)\s+pe\s+.+"""),
            paramExtractor = { match, input -> mapOf("query" to extractTopicFromInput(input, listOf("youtube", "yt", "pe", "chalao", "play"))) }),
        QuickPattern(IntentType.YOUTUBE, "open_youtube",
            Regex("""(?i)\b(?:youtube|yt)\s+(?:pe\s+)?(?:chalao|play|search|kholo)\b"""),
            paramExtractor = { match, input -> mapOf("query" to "") }),
        QuickPattern(IntentType.YOUTUBE, "search_youtube",
            Regex("""(?i)\b(?:youtube|yt)\s+(?:search|pe)\s+\w+"""),
            paramExtractor = { match, input -> mapOf("query" to extractTopicFromInput(input, listOf("youtube", "yt", "search", "pe"))) }),

        // ═══════════════ WHATSAPP — SMART extraction (Bug Fix #1) ═══════════════
        // NOTE: WhatsApp CALL and VIDEO CALL patterns are ABOVE — they match first!
        // Hinglish: "whatsapp pe ayush ko message bhejo ki kal exam hai"
        QuickPattern(IntentType.WHATSAPP, "send_whatsapp",
            Regex("""(?i)\b(?:whatsapp|wa)\s+pe\s+(\w+)\s+ko\s+(?:message|msg)\s+(?:bhejo|karo|send)?\s*(?:ki|ke|ki\s+ki)?\s*(.*)"""),
            paramExtractor = { match, input -> mapOf(
                "contact" to (match.groupValues.getOrNull(1)?.trim()?.ifBlank { "unknown" } ?: "unknown"),
                "message" to (match.groupValues.getOrNull(2)?.trim()?.ifBlank { "" } ?: "")
            )}),
        // "ayush ko whatsapp pe message bhejo ki kal exam hai"
        QuickPattern(IntentType.WHATSAPP, "send_whatsapp",
            Regex("""(?i)\b(\w+)\s+ko\s+(?:whatsapp|wa)\s+pe\s+(?:message|msg)\s+(?:bhejo|karo|send)?\s*(?:ki|ke)?\s*(.*)"""),
            paramExtractor = { match, input -> mapOf(
                "contact" to (match.groupValues.getOrNull(1)?.trim()?.ifBlank { "unknown" } ?: "unknown"),
                "message" to (match.groupValues.getOrNull(2)?.trim()?.ifBlank { "" } ?: "")
            )}),
        // English: "send hello to ayush on whatsapp"
        QuickPattern(IntentType.WHATSAPP, "send_whatsapp",
            Regex("""(?i)\b(?:send|bhejo)\s+(.+?)\s+(?:to|ko)\s+(\w+)\s+(?:on|pe)\s+(?:whatsapp|wa)\b"""),
            paramExtractor = { match, input -> mapOf(
                "message" to (match.groupValues.getOrNull(1)?.trim()?.ifBlank { "" } ?: ""),
                "contact" to (match.groupValues.getOrNull(2)?.trim()?.ifBlank { "unknown" } ?: "unknown")
            )}),
        // "whatsapp pe mom ko bhejo ki I'll be late"
        QuickPattern(IntentType.WHATSAPP, "send_whatsapp",
            Regex("""(?i)\b(?:whatsapp|wa)\s+pe\s+(\w+)\s+ko\s+(?:bhejo|send)\s*(?:ki|ke)?\s*(.*)"""),
            paramExtractor = { match, input -> mapOf(
                "contact" to (match.groupValues.getOrNull(1)?.trim()?.ifBlank { "unknown" } ?: "unknown"),
                "message" to (match.groupValues.getOrNull(2)?.trim()?.ifBlank { "" } ?: "")
            )}),
        // Generic WhatsApp send patterns (fallback, no specific contact/message extraction)
        QuickPattern(IntentType.WHATSAPP, "send_whatsapp",
            Regex("""(?i)\b(?:whatsapp|wa)\s+(?:pe\s+)?(?:message|msg|send|bhejo)\b"""),
            paramExtractor = { match, input -> extractWhatsAppParams(input) }),
        QuickPattern(IntentType.WHATSAPP, "send_whatsapp",
            Regex("""(?i)\b\w+\s+ko\s+(?:whatsapp|wa)\s+pe\s+(?:message|msg)\b"""),
            paramExtractor = { match, input -> extractWhatsAppParams(input) }),
        QuickPattern(IntentType.WHATSAPP, "open_whatsapp",
            Regex("""(?i)\b(?:open|launch|start)\s+(?:whatsapp|wa)\b""")),
        QuickPattern(IntentType.WHATSAPP, "send_whatsapp",
            Regex("""(?i)\b(?:whatsapp|wa)\s+(?:message|msg)\s+(?:karo|bhejo|send)"""),
            paramExtractor = { match, input -> extractWhatsAppParams(input) }),
        QuickPattern(IntentType.WHATSAPP, "send_whatsapp",
            Regex("""(?i)\b(?:send|bhejo)\s+.+\s+(?:on\s+)?(?:whatsapp|wa)\b"""),
            paramExtractor = { match, input -> extractWhatsAppParams(input) }),

        // ═══════════════ WEATHER — uses free Open-Meteo API ═══════════════
        QuickPattern(IntentType.WEATHER, "get_weather",
            Regex("""(?i)\b(?:weather|mausam|temperature|garmi|thand|barish|rain)\b""")),
        QuickPattern(IntentType.WEATHER, "get_weather",
            Regex("""(?i)\b(?:aaj\s+ka\s+mausam|mausam\s+kaisa|mausam\s+kya|weather\s+kya|weather\s+check)\b""")),
        QuickPattern(IntentType.WEATHER, "get_weather",
            Regex("""(?i)\b(?:kitni\s+garmi|kitni\s+thand|barish\s+hogi|rain\s+hoga)\b""")),
        QuickPattern(IntentType.WEATHER, "get_weather",
            Regex("""(?i)\b(?:weather\s+(?:in|of|for)\s+\w+|\w+\s+(?:ka|ki|me)\s+mausam)\b""")),

        // ═══════════════ NEWS — uses free RSS ═══════════════
        QuickPattern(IntentType.NEWS, "get_news",
            Regex("""(?i)\b(?:news|khabar|headline|breaking\s*news|latest\s*news|top\s*news)\b""")),
        QuickPattern(IntentType.NEWS, "get_news",
            Regex("""(?i)\b(?:news\s+dikhao|khabar\s+dikhao|news\s+suna|khabar\s+suna|news\s+chalu)\b""")),
        QuickPattern(IntentType.NEWS, "get_news",
            Regex("""(?i)\b(?:aaj\s+ki\s+khabar|aaj\s+ka\s+news|latest\s+headline)\b""")),

        // ═══════════════ APP_LAUNCH — works OFFLINE ═══════════════
        QuickPattern(IntentType.APP_LAUNCH, "launch_app",
            Regex("""(?i)\b(?:open|launch|start)\s+(?:the\s+)?(?:app\s+)?\w+"""),
            paramExtractor = { match, input -> mapOf("app" to extractAppFromInput(input)) }),
        QuickPattern(IntentType.APP_LAUNCH, "launch_app",
            Regex("""(?i)\b\w+\s+(?:kholo|chalao|shuru)\b"""),
            paramExtractor = { match, input -> mapOf("app" to extractAppFromInput(input)) }),

        // ═══════════════ SMS SEND — works OFFLINE ═══════════════
        QuickPattern(IntentType.SMS, "send_sms",
            Regex("""(?i)\b(?:send|write)\s+(?:a\s+)?(?:sms|text|text\s+message)\b""")),
        QuickPattern(IntentType.SMS, "send_sms",
            Regex("""(?i)\b(?:sms|text)\s+(?:\w+\s+)?(?:karo|bhejo|send)\b"""),
            paramExtractor = { match, input -> mapOf("contact" to extractContactFromInput(input, "sms")) }),
        QuickPattern(IntentType.SMS, "send_sms",
            Regex("""(?i)\b(?:message|msg)\s+bhejo\b"""),
            paramExtractor = { match, input -> mapOf("contact" to extractContactFromInput(input, "sms")) }),

        // ═══════════════ SMS READ — works OFFLINE ═══════════════
        QuickPattern(IntentType.SMS_READ, "read_sms",
            Regex("""(?i)\b(?:read|show|check)\s+(?:my\s+)?(?:sms|text\s+messages|messages|inbox)\b""")),
        QuickPattern(IntentType.SMS_READ, "read_sms",
            Regex("""(?i)\b(?:sms|message)\s+(?:padho|dikhao|read|check)\b""")),
        QuickPattern(IntentType.SMS_READ, "read_sms",
            Regex("""(?i)\b(?:messages?\s+dikhao|inbox\s+dikhao|sms\s+padho)\b""")),

        // ═══════════════ ALARM — works OFFLINE ═══════════════
        QuickPattern(IntentType.ALARM, "set_alarm",
            Regex("""(?i)\b(?:set|create|make)\s+(?:an?\s+)?(?:alarm|wake\s*up)\b""")),
        QuickPattern(IntentType.ALARM, "set_alarm",
            Regex("""(?i)\b(?:alarm)\s+(?:lagao|set|karo|chalu)\b""")),
        QuickPattern(IntentType.ALARM, "set_alarm",
            Regex("""(?i)\balarm\b""")),

        // ═══════════════ REMINDER — works OFFLINE ═══════════════
        QuickPattern(IntentType.REMINDER, "set_reminder",
            Regex("""(?i)\b(?:set|create|make)\s+(?:a\s+)?(?:reminder|remind)\b""")),
        QuickPattern(IntentType.REMINDER, "set_reminder",
            Regex("""(?i)\b(?:remind|reminder|yaad\s+dilana)\b""")),

        // ═══════════════ MEDIA CONTROL — works OFFLINE ═══════════════
        QuickPattern(IntentType.MEDIA_CONTROL, "play",
            Regex("""(?i)\b(?:play\s+)?(?:music|song|gana)\s*(?:play|chalao|baja)?\b""")),
        QuickPattern(IntentType.MEDIA_CONTROL, "play",
            Regex("""(?i)\b(?:play\s+music|play\s+song|music\s+chalao|gana\s+chalao|gana\s+baja)\b""")),
        QuickPattern(IntentType.MEDIA_CONTROL, "pause",
            Regex("""(?i)\b(?:pause|stop\s+music|ruk|ruko)\s*(?:music|song|gana)?\b""")),
        QuickPattern(IntentType.MEDIA_CONTROL, "next",
            Regex("""(?i)\b(?:next|aage|next\s+song|next\s+track|agla\s+gana)\b""")),
        QuickPattern(IntentType.MEDIA_CONTROL, "previous",
            Regex("""(?i)\b(?:previous|peeche|prev|last\s+song|pichla\s+gana)\b""")),

        // ═══════════════ WEB SEARCH — works OFFLINE (launches browser) ═══════════════
        QuickPattern(IntentType.WEB_SEARCH, "web_search",
            Regex("""(?i)\b(?:search|google|lookup|find\s+info)\s+(?:for\s+)?(.+)"""),
            paramExtractor = { match, input -> mapOf("query" to extractTopicFromInput(input, listOf("search", "for", "google", "lookup", "find", "info"))) }),
        QuickPattern(IntentType.WEB_SEARCH, "web_search",
            Regex("""(?i)\b(?:search\s+karo|google\s+karo|khojo)\b""")),

        // ═══════════════ NOTE SAVE — works OFFLINE ═══════════════
        QuickPattern(IntentType.NOTE_SAVE, "save_note",
            Regex("""(?i)\b(?:remember|note|save|yaad)\s+(?:this|that|note|karo|rakhna|rakh)\b""")),
        QuickPattern(IntentType.NOTE_SAVE, "save_note",
            Regex("""(?i)\b(?:yaad\s+rakhna|note\s+save|save\s+note|note\s+karo|remember\s+this)\b""")),
        QuickPattern(IntentType.NOTE_SAVE, "save_note",
            Regex("""(?i)\b(?:save\s+this|remember\s+that|yaad\s+rakh)\b""")),

        // ═══════════════ NOTE READ — works OFFLINE ═══════════════
        QuickPattern(IntentType.NOTE_READ, "read_notes",
            Regex("""(?i)\b(?:show|read|check|what\s+(?:are|is))\s+(?:my\s+)?(?:notes?|memories|saved)\b""")),
        QuickPattern(IntentType.NOTE_READ, "read_notes",
            Regex("""(?i)\b(?:notes?\s+dikhao|notes?\s+padho|kya\s+yaad\s+hai|kya\s+note\s+save\s+hai)\b""")),
        QuickPattern(IntentType.NOTE_READ, "read_notes",
            Regex("""(?i)\b(?:yaad\s+kya\s+hai|saved\s+notes|mujhe\s+meri\s+notes)\b""")),

        // ═══════════════ CONTACT SEARCH — works OFFLINE ═══════════════
        QuickPattern(IntentType.CONTACT_SEARCH, "find_contact",
            Regex("""(?i)\b(?:find|search|look\s+up)\s+(?:contact|number)\b""")),
        QuickPattern(IntentType.CONTACT_SEARCH, "find_contact",
            Regex("""(?i)\b\w+\s+(?:ka\s+number|ka\s+contact|ka\s+phone)\b"""),
            paramExtractor = { match, input -> mapOf("contact" to extractContactFromInput(input, "contact_search")) }),
        QuickPattern(IntentType.CONTACT_SEARCH, "find_contact",
            Regex("""(?i)\b(?:number\s+batao|contact\s+search|contact\s+dhoond)\b"""),
            paramExtractor = { match, input -> mapOf("contact" to extractContactFromInput(input, "contact_search")) }),

        // ═══════════════ TIMER — works OFFLINE ═══════════════
        QuickPattern(IntentType.TIMER, "set_timer",
            Regex("""(?i)\b(?:set|start)\s+(?:a\s+)?(?:timer|stopwatch|countdown)\b""")),
        QuickPattern(IntentType.TIMER, "set_timer",
            Regex("""(?i)\b(?:timer|stopwatch)\s+(?:lagao|set|start|chalu)\b""")),
        QuickPattern(IntentType.TIMER, "set_timer",
            Regex("""(?i)\b\d+\s*(?:minute|min|second|sec)\s*(?:timer|ka\s+timer)\b""")),

        // ═══════════════ TRANSLATE — can launch translate app ═══════════════
        QuickPattern(IntentType.TRANSLATE, "translate",
            Regex("""(?i)\b(?:translate|anuvad|translation)\b""")),

        // ═══════════════ CALCULATE — can do basic math ═══════════════
        QuickPattern(IntentType.CALCULATE, "calculate",
            Regex("""(?i)\b(?:calculate|compute|kitna\s+hota|solve)\b""")),
        QuickPattern(IntentType.CALCULATE, "calculate",
            Regex("""\d+\s*[+\-*/×÷]\s*\d+""")),

        // ═══════════════ FIND PHONE — works OFFLINE ═══════════════
        QuickPattern(IntentType.FIND_PHONE, "find_phone",
            Regex("""(?i)\b(?:find|locate|ring|track)\s+(?:my\s+)?(?:phone|device|mobile)\b""")),
        QuickPattern(IntentType.FIND_PHONE, "find_phone",
            Regex("""(?i)\b(?:phone\s+dhoondo|mobile\s+kahan|phone\s+ring\s+karo)\b""")),

        // ═══════════════ CAMERA — works OFFLINE ═══════════════
        QuickPattern(IntentType.CAMERA, "open_camera",
            Regex("""(?i)\b(?:open|launch)\s+(?:the\s+)?(?:camera)\b""")),
        QuickPattern(IntentType.CAMERA, "take_photo",
            Regex("""(?i)\b(?:take|click|snap|capture|shoot)\s+(?:a\s+)?(?:photo|picture|pic|selfie)\b""")),
        QuickPattern(IntentType.CAMERA, "take_photo",
            Regex("""(?i)\b(?:photo|picture|pic|selfie)\s+(?:kheencho|lo|lena)\b""")),
        QuickPattern(IntentType.CAMERA, "take_photo",
            Regex("""(?i)\bphoto\s+kheencho\b""")),

        // ═══════════════ CONTINUOUS MODE — works OFFLINE ═══════════════
        QuickPattern(IntentType.CONTINUOUS_MODE, "enable_continuous",
            Regex("""(?i)\b(?:enable|turn\s+on|start)\s+(?:the\s+)?(?:continuous|always\s*listening|call\s*type)\s*(?:mode)?\b""")),
        QuickPattern(IntentType.CONTINUOUS_MODE, "disable_continuous",
            Regex("""(?i)\b(?:disable|turn\s+off|stop)\s+(?:the\s+)?(?:continuous|always\s*listening|call\s*type)\s*(?:mode)?\b""")),

        // ═══════════════ LOCATION — works OFFLINE (GPS) ═══════════════
        QuickPattern(IntentType.LOCATION, "get_location",
            Regex("""(?i)\b(?:where\s+am\s+i|my\s+location|mera\s+location|location\s+dikhao|kahan\s+hun)\b""")),
        QuickPattern(IntentType.LOCATION, "get_location",
            Regex("""(?i)\b(?:find\s+my\s+location|show\s+my\s+location|location\s+kya\s+hai)\b""")),

        // ═══════════════ CALL LOG — works OFFLINE ═══════════════
        QuickPattern(IntentType.CALL_LOG, "call_log",
            Regex("""(?i)\b(?:call\s*log|call\s+history|recent\s+calls|call\s+record)\b""")),
        QuickPattern(IntentType.CALL_LOG, "call_log",
            Regex("""(?i)\b(?:call\s+log\s+dikhao|recent\s+call|call\s+details)\b""")),

        // ═══════════════ NOTIFICATION — works OFFLINE ═══════════════
        QuickPattern(IntentType.NOTIFICATION, "read_notifications",
            Regex("""(?i)\b(?:read|show|check)\s+(?:my\s+)?(?:notifications?|notifs?)\b""")),
        QuickPattern(IntentType.NOTIFICATION, "read_notifications",
            Regex("""(?i)\b(?:notification|notifs?)\s+(?:dikhao|padho|check)\b""")),

        // ═══════════════ WIFI/BLUETOOTH/DND (original English patterns) — works OFFLINE ═══════════════
        QuickPattern(IntentType.DEVICE_CONTROL, "wifi_on",
            Regex("""(?i)\b(?:turn\s+on|enable|on\s+karo)\s+(?:the\s+)?(?:wifi|wi-?fi)\b""")),
        QuickPattern(IntentType.DEVICE_CONTROL, "wifi_off",
            Regex("""(?i)\b(?:turn\s+off|disable|off\s+karo)\s+(?:the\s+)?(?:wifi|wi-?fi)\b""")),
        QuickPattern(IntentType.DEVICE_CONTROL, "bluetooth_on",
            Regex("""(?i)\b(?:turn\s+on|enable|on\s+karo)\s+(?:the\s+)?bluetooth\b""")),
        QuickPattern(IntentType.DEVICE_CONTROL, "bluetooth_off",
            Regex("""(?i)\b(?:turn\s+off|disable|off\s+karo)\s+(?:the\s+)?bluetooth\b""")),
        QuickPattern(IntentType.DEVICE_CONTROL, "dnd_on",
            Regex("""(?i)\b(?:turn\s+on|enable|on\s+karo)\s+(?:the\s+)?(?:dnd|do\s+not\s+disturb)\b""")),
        QuickPattern(IntentType.DEVICE_CONTROL, "dnd_off",
            Regex("""(?i)\b(?:turn\s+off|disable|off\s+karo)\s+(?:the\s+)?(?:dnd|do\s+not\s+disturb)\b""")),

        // ═══════════════ FILE MANAGER — works OFFLINE ═══════════════
        QuickPattern(IntentType.FILE_OPEN, "open_files",
            Regex("""(?i)\b(?:open|launch)\s+(?:the\s+)?(?:file\s*manager|files|downloads|folder)\b""")),
        QuickPattern(IntentType.FILE_OPEN, "open_files",
            Regex("""(?i)\b(?:file\s*manager|downloads|folder)\s+(?:kholo|chalao|open)\b""")),
    )

    // ──────────────────────────────────────────────────────────────────────────
    // AI Classification Prompt — THE BRAIN (only used for ambiguous inputs)
    // ──────────────────────────────────────────────────────────────────────────

    private val CLASSIFICATION_PROMPT = """
You are MAHI's intent classifier. Classify the user input into EXACTLY ONE intent type and extract parameters.
Respond ONLY with valid JSON, nothing else. No markdown, no explanation, JUST JSON.

Available types:
- DEVICE_CONTROL: Toggle flashlight, wifi, bluetooth, brightness, volume, DND, airplane mode, silent/vibrate/normal mode, etc.
- WEATHER: Any weather/temperature/mausam question
- NEWS: Any news/headlines/breaking news/khabar request
- YOUTUBE: Play/search/watch something on YouTube
- CALL: Make a phone call to someone
- SMS: Send a text message (regular SMS)
- SMS_READ: Read SMS inbox messages
- WHATSAPP: Send a WhatsApp message or open WhatsApp chat
- WHATSAPP_CALL: Make a WhatsApp voice call
- WHATSAPP_VIDEO_CALL: Make a WhatsApp video call
- ALARM: Set an alarm
- REMINDER: Set a reminder
- ROUTINE: Morning/night/work/driving/meeting routine
- CALENDAR: Calendar/schedule related
- NOTIFICATION: Read/check notifications
- APP_LAUNCH: Open/launch an app
- WEB_SEARCH: Search the web for information
- MEDIA_CONTROL: Play/pause/next/previous music or media
- LOCATION: Where am I / nearby places
- BATTERY: Battery level/status
- CALL_LOG: Call history / recent calls
- TIME_DATE: What time/date is it
- FIND_PHONE: Find/locate/ring my phone
- NOTE_SAVE: Save a note/memory/reminder to remember something
- NOTE_READ: Recall/read saved notes/memories
- CONTACT_SEARCH: Find/search contact details by name
- CONTACT_SAVE: Save a new contact with name and number
- CONTACT_DELETE: Delete/remove a saved contact
- CONTACTS_SHOW: Show/display contacts list
- TIMER: Set a timer/stopwatch
- TRANSLATE: Translate text from one language to another
- CALCULATE: Math calculation or expression evaluation
- CONTINUOUS_MODE: Toggle always-listening/continuous conversation mode
- CAMERA: Open camera or take a photo/selfie
- FILE_OPEN: Open files/downloads folder/file manager
- EMERGENCY_SOS: Emergency, SOS, help help, danger, bachao, madad
- EXPENSE_TRACK: Track expenses, add expense, kharcha, spending, kitna kharcha
- GESTURE: Scroll, swipe, back, home, recent apps, notifications panel, quick settings, lock screen
- ACCESSIBILITY: Click text, click button, type text, read screen
- CLIPBOARD: Copy, paste, read clipboard
- SCREENSHOT: Take a screenshot/screen capture
- DEVICE_INFO: Show device/phone info
- NAVIGATION: Navigate to a specific location, open maps
- GENERAL_CHAT: General conversation that doesn't fit above

Examples:
- "play carryminati latest video on youtube" → {"type":"YOUTUBE","action":"search_youtube","params":{"query":"carryminati latest video"}}
- "call ayush from sim 1" → {"type":"CALL","action":"make_call","params":{"contact":"ayush","sim":"1"}}
- "text ayush in whatsapp that he needs to call me" → {"type":"WHATSAPP","action":"send_whatsapp","params":{"contact":"ayush","message":"he needs to call me"}}
- "whatsapp pe ayush ko message bhejo ki kal exam hai" → {"type":"WHATSAPP","action":"send_whatsapp","params":{"contact":"ayush","message":"kal exam hai"}}
- "send hello to ayush on whatsapp" → {"type":"WHATSAPP","action":"send_whatsapp","params":{"contact":"ayush","message":"hello"}}
- "whatsapp call karo Ali" → {"type":"WHATSAPP_CALL","action":"whatsapp_call","params":{"contact":"ali"}}
- "Ali ko video call karo" → {"type":"WHATSAPP_VIDEO_CALL","action":"whatsapp_video_call","params":{"contact":"ali"}}
- "Sara ka number 1234567890 save karo" → {"type":"CONTACT_SAVE","action":"save_contact","params":{"contact":"sara","number":"1234567890"}}
- "Ali ko delete karo" → {"type":"CONTACT_DELETE","action":"delete_contact","params":{"contact":"ali"}}
- "contacts dikhao" → {"type":"CONTACTS_SHOW","action":"show_contacts","params":{}}
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
- "scroll down karo" → {"type":"GESTURE","action":"scroll_down","params":{}}
- "back jao" → {"type":"GESTURE","action":"go_back","params":{}}
- "home jao" → {"type":"GESTURE","action":"go_home","params":{}}
- "screenshot lo" → {"type":"SCREENSHOT","action":"take_screenshot","params":{}}
- "Send par click karo" → {"type":"ACCESSIBILITY","action":"click_text","params":{"text":"Send"}}
- "type Hello World" → {"type":"ACCESSIBILITY","action":"type_text","params":{"text":"Hello World"}}
- "screen read karo" → {"type":"ACCESSIBILITY","action":"screen_read","params":{}}
- "copy karo" → {"type":"CLIPBOARD","action":"copy","params":{}}
- "paste karo" → {"type":"CLIPBOARD","action":"paste","params":{}}
- "clipboard mein kya hai" → {"type":"CLIPBOARD","action":"read_clipboard","params":{}}
- "Lahore ka raasta batao" → {"type":"NAVIGATION","action":"navigate_to","params":{"location":"Lahore"}}
- "good night" → {"type":"ROUTINE","action":"good_night","params":{}}
- "work mode" → {"type":"ROUTINE","action":"work_mode","params":{}}
- "driving mode" → {"type":"ROUTINE","action":"driving_mode","params":{}}
- "meeting mode" → {"type":"ROUTINE","action":"meeting_mode","params":{}}
- "airplane mode on karo" → {"type":"DEVICE_CONTROL","action":"airplane_on","params":{}}
- "silent mode karo" → {"type":"DEVICE_CONTROL","action":"silent_mode","params":{}}
- "vibrate mode" → {"type":"DEVICE_CONTROL","action":"vibrate_mode","params":{}}
- "volume 50 set karo" → {"type":"DEVICE_CONTROL","action":"set_volume","params":{"level":"50"}}
- "phone lock karo" → {"type":"GESTURE","action":"lock_screen","params":{}}
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
If they mention whatsapp call or video call, use WHATSAPP_CALL or WHATSAPP_VIDEO_CALL, NOT WHATSAPP.
If they want to save a contact, use CONTACT_SAVE.
If they want to delete a contact, use CONTACT_DELETE.
If they want to show contacts list, use CONTACTS_SHOW.
If they mention scroll, swipe, back, home, lock screen, use GESTURE.
If they mention screenshot, use SCREENSHOT.
If they mention click, tap, type text, screen read, use ACCESSIBILITY.
If they mention copy, paste, clipboard, use CLIPBOARD.
If they want to navigate to a location, use NAVIGATION.
If they want device info, use DEVICE_INFO.
If they mention good night, good morning, work mode, driving mode, meeting mode, use ROUTINE.

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
                cleaned = cleaned.replace(Regex("""^```(?:json)?\s*"""), "").replace(Regex("""\s*```$"""), "")
            }

            val jsonMatch = Regex("""\{[^{}]*(?:\{[^{}]*\}[^{}]*)*\}""").find(cleaned)
                ?: Regex("""\{[^{}]*\}""").find(cleaned)
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

            // ── NEW INTENT KEYWORD PATTERNS ──

            // Screenshot
            lower.contains("screenshot") || lower.contains("screen shot") || lower.contains("screen capture") ->
                IntentResult(IntentType.SCREENSHOT, "take_screenshot")

            // Clipboard
            lower.contains("clipboard") || (lower.contains("copy") && !lower.contains("right")) || lower.contains("paste karo") ->
                IntentResult(IntentType.CLIPBOARD, if (lower.contains("paste")) "paste" else if (lower.contains("kya hai") || lower.contains("read") || lower.contains("check")) "read_clipboard" else "copy")

            // Navigation
            lower.contains("navigate") || lower.contains("navigation") || lower.contains("ka raasta") || lower.contains("ka rasta") || lower.contains("maps kholo") ->
                IntentResult(IntentType.NAVIGATION, "navigate_to", extractNavigationLocation(input))

            // Device info
            lower.contains("device info") || lower.contains("phone info") || lower.contains("mobile info") || lower.contains("phone ka info") ->
                IntentResult(IntentType.DEVICE_INFO, "device_info")

            // Gestures
            lower.contains("scroll") || lower.contains("swipe") || lower.contains("back jao") || lower.contains("wapis jao") ||
            lower.contains("home jao") || lower.contains("ghar jao") || lower.contains("recent apps") || lower.contains("lock screen") ||
            lower.contains("phone lock") || lower.contains("screen lock") || lower.contains("quick settings") ->
                IntentResult(IntentType.GESTURE, "gesture_action")

            // Accessibility
            lower.contains("click karo") || lower.contains("tap karo") || lower.contains("screen read") || lower.contains("type karo") ->
                IntentResult(IntentType.ACCESSIBILITY, "accessibility_action")

            // WhatsApp Call
            (lower.contains("whatsapp") || lower.contains("wa ")) && (lower.contains("call") || lower.contains("phone")) && !lower.contains("video") ->
                IntentResult(IntentType.WHATSAPP_CALL, "whatsapp_call", mapOf("contact" to extractContact(lower)))

            // WhatsApp Video Call
            (lower.contains("whatsapp") || lower.contains("wa ")) && lower.contains("video") ->
                IntentResult(IntentType.WHATSAPP_VIDEO_CALL, "whatsapp_video_call", mapOf("contact" to extractContact(lower)))

            // Contact Save
            lower.contains("save contact") || lower.contains("add contact") || lower.contains("contact save") || lower.contains("number save") ->
                IntentResult(IntentType.CONTACT_SAVE, "save_contact", extractContactSaveParams(input))

            // Contact Delete
            lower.contains("delete contact") || lower.contains("remove contact") || lower.contains("contact delete") || lower.contains("hatao contact") ->
                IntentResult(IntentType.CONTACT_DELETE, "delete_contact", mapOf("contact" to extractContact(lower)))

            // Contacts Show
            lower.contains("contacts dikhao") || lower.contains("show contacts") || lower.contains("contacts list") || lower.contains("mere contacts") || lower.contains("phone book") ->
                IntentResult(IntentType.CONTACTS_SHOW, "show_contacts")

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
            lower.contains("calculate") || lower.contains("hisab") || lower.contains("kitna hota") || Regex("""\d+\s*[+\-*/]\s*\d+""").containsMatchIn(lower) ->
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
        val koPattern = Regex("""(?i)\b(\w+)\s+ko\b""")
        val koMatch = koPattern.find(lower)
        if (koMatch != null) {
            val name = koMatch.groupValues[1].trim()
            // Filter out common non-name words
            if (name !in listOf("kya", "kaise", "kab", "kahan", "kyu", "mujhe", "tumhe", "unko")) {
                return name
            }
        }

        // English patterns: "call ayush", "send message to ayush"
        val toPattern = Regex("""(?i)\bto\s+(\w+)""")
        val toMatch = toPattern.find(lower)
        if (toMatch != null) {
            val name = toMatch.groupValues[1].trim()
            if (name !in listOf("the", "a", "an", "my", "me", "him", "her")) {
                return name
            }
        }

        // "call ayush" pattern — word after the verb
        val verbPattern = Regex("""(?i)\b(?:call|phone|ring|dial|text|message)\s+(\w+)""")
        val verbMatch = verbPattern.find(lower)
        if (verbMatch != null) {
            val name = verbMatch.groupValues[1].trim()
            if (name !in listOf("a", "an", "the", "my", "me", "him", "her", "karo", "please")) {
                return name
            }
        }

        // "ayush ka number" pattern
        val kaPattern = Regex("""(?i)\b(\w+)\s+(?:ka|ki)\s+(?:number|contact|phone)\b""")
        val kaMatch = kaPattern.find(lower)
        if (kaMatch != null) {
            return kaMatch.groupValues[1].trim()
        }

        // Fallback: remove common filler words and return what's left
        val cleaned = input.replace(Regex("""(?i)\b(?:call|phone|ring|dial|text|message|send|whatsapp|wa|from|sim\s*\d|on|to|that|the|please|karo|bhejo|ka|number|batao|se|ko|pe|a|an|the|my|me|i|want|need|can|you|will|would|should|could|must|shall|msg|pe|karo|bhejo)\b"""), "").trim()
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
        val kiPattern = Regex("""(?i)\bki\s+(.+?)$""")
        val kiMatch = kiPattern.find(lower)
        if (kiMatch != null) {
            return kiMatch.groupValues[1].trim().ifBlank { "" }
        }

        // English: "that I'll be late" → message is after "that"
        val thatPattern = Regex("""(?i)\bthat\s+(.+?)$""")
        val thatMatch = thatPattern.find(lower)
        if (thatMatch != null) {
            return thatMatch.groupValues[1].trim().ifBlank { "" }
        }

        // "send hello on whatsapp" → message is between "send" and "on whatsapp"
        val sendPattern = Regex("""(?i)\b(?:send|bhejo)\s+(.+?)\s+(?:on|pe)\s+(?:whatsapp|wa)\b""")
        val sendMatch = sendPattern.find(lower)
        if (sendMatch != null) {
            return sendMatch.groupValues[1].trim().ifBlank { "" }
        }

        // Quoted text: "whatsapp pe 'hello' bhejo" or "whatsapp pe "hello" bhejo"
        val quotePattern = Regex("""["'](.+?)["']""")
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
        val amountPattern = Regex("""(\d+(?:\.\d+)?)\s*(?:rupee|rs|₹|dollar|\$|rupaye)?""", RegexOption.IGNORE_CASE)
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
            .replace(Regex("""(?i)\b(?:expense|kharcha|spending|add|save|karo|kiya|hua|hai|track|ki|ka|ke)\b"""), "")
            .replace(Regex("""\d+(?:\.\d+)?\s*(?:rupee|rs|₹|dollar|\$|rupaye)?"""), "")
            .trim()
        if (descClean.isNotBlank()) {
            params["description"] = descClean
        }

        return params
    }

    /**
     * Extract contact save parameters — name and phone number.
     * Handles: "Sara ka number 1234567890 save karo", "save contact Ali 9876543210"
     */
    private fun extractContactSaveParams(input: String): Map<String, String> {
        val lower = input.lowercase()
        val params = mutableMapOf<String, String>()

        // Extract phone number (7-15 digits)
        val numberPattern = Regex("""(\d{7,15})""")
        val numberMatch = numberPattern.find(input)
        if (numberMatch != null) {
            params["number"] = numberMatch.groupValues[1]
        }

        // Extract name — try "X ka number" pattern first
        val kaNumberPattern = Regex("""(?i)\b(\w+)\s+(?:ka\s+)?(?:number|contact)""")
        val kaNumberMatch = kaNumberPattern.find(lower)
        if (kaNumberMatch != null) {
            val name = kaNumberMatch.groupValues[1].trim()
            if (name !in listOf("save", "add", "naya", "the", "a", "my", "contact", "number") && name.isNotBlank()) {
                params["contact"] = name
            }
        }

        // Try "X ko contacts mein add/save karo" pattern
        if (!params.containsKey("contact")) {
            val koPattern = Regex("""(?i)\b(\w+)\s+ko\s+(?:contacts?\s+)?(?:mein\s+)?(?:add|save)""")
            val koMatch = koPattern.find(lower)
            if (koMatch != null) {
                val name = koMatch.groupValues[1].trim()
                if (name !in listOf("save", "add", "the", "a", "my") && name.isNotBlank()) {
                    params["contact"] = name
                }
            }
        }

        // Fallback: try extracting name before the number
        if (!params.containsKey("contact")) {
            val nameBeforeNumberPattern = Regex("""(?i)\b(\w+)\s+\d{7,15}""")
            val nameBeforeNumberMatch = nameBeforeNumberPattern.find(lower)
            if (nameBeforeNumberMatch != null) {
                val name = nameBeforeNumberMatch.groupValues[1].trim()
                if (name !in listOf("save", "add", "number", "contact", "naya") && name.isNotBlank()) {
                    params["contact"] = name
                }
            }
        }

        if (!params.containsKey("contact")) {
            params["contact"] = "unknown"
        }

        return params
    }

    /**
     * Extract volume level from input.
     * Handles: "volume 50 set karo", "volume 70", "awaz 30 par"
     */
    private fun extractVolumeLevel(input: String): Map<String, String> {
        val numberPattern = Regex("""(\d+)""")
        val numberMatch = numberPattern.find(input)
        return if (numberMatch != null) {
            mapOf("level" to numberMatch.groupValues[1])
        } else {
            emptyMap()
        }
    }

    /**
     * Extract navigation location from input.
     * Handles: "navigate to Lahore", "Lahore ka raasta batao"
     */
    private fun extractNavigationLocation(input: String): Map<String, String> {
        val lower = input.lowercase()

        // "navigate to X"
        val navigatePattern = Regex("""(?i)\b(?:navigate|navigation)\s+(?:to\s+)?(.+)""")
        val navigateMatch = navigatePattern.find(lower)
        if (navigateMatch != null) {
            val location = navigateMatch.groupValues[1].trim()
                .replace(Regex("""(?i)\b(karo|batao|dikhao|please)\b"""), "")
                .trim()
            if (location.isNotBlank()) {
                return mapOf("location" to location)
            }
        }

        // "X ka raasta/rasta/direction/map"
        val raastaPattern = Regex("""(?i)\b(\w+)\s+ka\s+(?:raasta|rasta|direction|map)""")
        val raastaMatch = raastaPattern.find(lower)
        if (raastaMatch != null) {
            return mapOf("location" to raastaMatch.groupValues[1].trim())
        }

        return mapOf("location" to "")
    }

    /**
     * Extract a topic/query from a natural language input string.
     * Used by ultra-fast patterns to populate the "query" param.
     */
    private fun extractTopicFromInput(input: String, removeWords: List<String>): String {
        var cleaned = input
        for (word in removeWords) {
            cleaned = cleaned.replace(Regex("""(?i)\b${Regex.escape(word)}\b"""), "")
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
            Regex("""(?i)\b(?:open|launch|start|kholo|chalao)\s+(?:the\s+)?(?:app\s+)?(.+?)(?:\s+(?:app|application|karo|please))?$"""),
            Regex("""(?i)\b(.+?)\s+(?:kholo|chalao)\b""")
        )
        for (pattern in patterns) {
            val match = pattern.find(input)
            if (match != null && match.groupValues.size > 1) {
                val app = match.groupValues[1].trim()
                if (app.isNotBlank()) return app
            }
        }
        // Fallback: just remove common verbs and return what's left
        return input.replace(Regex("""(?i)\b(?:open|launch|start|kholo|chalao|the|app|application|karo|please)\b"""), "").trim().ifBlank { "" }
    }

    // Keep old methods for backward compatibility with keywordFallback
    private fun extractContact(input: String): String {
        // Use the smart extraction
        return extractContactFromInput(input, "fallback")
    }

    private fun extractTopic(input: String, removeWords: List<String>): String {
        return extractTopicFromInput(input, removeWords)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // AI Response Parsing Models
    // ──────────────────────────────────────────────────────────────────────────

    private data class AiClassificationResult(
        val type: String? = null,
        val action: String? = null,
        val params: Map<String, String>? = null
    )
}
