package com.mahi.assistant.data.model

data class DeviceState(
    val deviceName: String,
    val isOn: Boolean = false,
    val icon: String = "device"
)
