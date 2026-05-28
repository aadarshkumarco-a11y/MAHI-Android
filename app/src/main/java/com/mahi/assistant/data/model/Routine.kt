package com.mahi.assistant.data.model

import com.google.gson.annotations.SerializedName
import java.util.UUID

data class Routine(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val triggerType: TriggerType,
    val triggerValue: String,
    val actions: List<RoutineAction>,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class TriggerType {
    @SerializedName("voice_command")
    VOICE_COMMAND,

    @SerializedName("time")
    TIME,

    @SerializedName("location")
    LOCATION,

    @SerializedName("device_event")
    DEVICE_EVENT,

    @SerializedName("notification")
    NOTIFICATION,

    @SerializedName("manual")
    MANUAL
}

data class RoutineAction(
    val type: ActionType,
    val params: Map<String, String> = emptyMap()
)

enum class ActionType {
    @SerializedName("toggle_wifi")
    TOGGLE_WIFI,

    @SerializedName("toggle_bluetooth")
    TOGGLE_BLUETOOTH,

    @SerializedName("toggle_flashlight")
    TOGGLE_FLASHLIGHT,

    @SerializedName("set_volume")
    SET_VOLUME,

    @SerializedName("send_sms")
    SEND_SMS,

    @SerializedName("make_call")
    MAKE_CALL,

    @SerializedName("open_app")
    OPEN_APP,

    @SerializedName("set_alarm")
    SET_ALARM,

    @SerializedName("read_notification")
    READ_NOTIFICATION,

    @SerializedName("speak_text")
    SPEAK_TEXT,

    @SerializedName("custom")
    CUSTOM
}
