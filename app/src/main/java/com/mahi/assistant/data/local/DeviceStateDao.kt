package com.mahi.assistant.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface DeviceStateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(deviceState: DeviceStateEntity): Long

    @Update
    suspend fun update(deviceState: DeviceStateEntity)

    @Delete
    suspend fun delete(deviceState: DeviceStateEntity)

    @Query("SELECT * FROM device_states ORDER BY deviceName ASC")
    suspend fun getAll(): List<DeviceStateEntity>

    @Query("SELECT * FROM device_states WHERE deviceName = :deviceName")
    suspend fun getByName(deviceName: String): DeviceStateEntity?

    @Query("SELECT * FROM device_states WHERE isOn = 1")
    suspend fun getActiveDevices(): List<DeviceStateEntity>

    @Query("UPDATE device_states SET isOn = :isOn, lastToggled = :timestamp WHERE deviceName = :deviceName")
    suspend fun toggleDevice(deviceName: String, isOn: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM device_states WHERE deviceName = :deviceName")
    suspend fun deleteByName(deviceName: String)

    @Query("DELETE FROM device_states")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM device_states")
    suspend fun count(): Int
}
