package com.mahi.assistant.data.model

data class IntentResult(
    val type: IntentType,
    val action: String,
    val params: Map<String, String> = emptyMap(),
    val response: String? = null,
    val confidence: Float = 0.0f
)

enum class IntentType {
    // Device Control
    WIFI_TOGGLE,
    BLUETOOTH_TOGGLE,
    FLASHLIGHT_TOGGLE,
    VOLUME_CONTROL,
    BRIGHTNESS_CONTROL,

    // Communication
    MAKE_CALL,
    SEND_SMS,
    READ_SMS,

    // Information
    WEATHER_QUERY,
    NEWS_QUERY,
    TIME_QUERY,
    DATE_QUERY,
    WEB_SEARCH,

    // Productivity
    SET_ALARM,
    SET_TIMER,
    SET_REMINDER,
    CALENDAR_QUERY,
    CALENDAR_EVENT,

    // System
    OPEN_APP,
    TAKE_PHOTO,
    READ_NOTIFICATIONS,

    // AI
    GENERAL_QUESTION,
    CONVERSATION,

    // Automation
    CREATE_ROUTINE,
    EXECUTE_ROUTINE,
    LIST_ROUTINES,

    // Unknown
    UNKNOWN
}
