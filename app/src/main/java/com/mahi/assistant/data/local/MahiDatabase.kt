package com.mahi.assistant.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.mahi.assistant.data.local.converter.Converters

@Database(
    entities = [
        MessageEntity::class,
        RoutineEntity::class,
        DeviceStateEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class MahiDatabase : RoomDatabase() {

    abstract fun messageDao(): MessageDao
    abstract fun routineDao(): RoutineDao
    abstract fun deviceStateDao(): DeviceStateDao

    companion object {
        const val DATABASE_NAME = "mahi_database"
    }
}
