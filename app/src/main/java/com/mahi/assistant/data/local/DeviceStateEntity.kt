package com.mahi.assistant.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "device_states",
    indices = [Index(value = ["deviceName"], unique = true)]
)
data class DeviceStateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val deviceName: String,
    val isOn: Boolean = false,
    val lastToggled: Long = System.currentTimeMillis()
)
