package com.mahi.assistant.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mahi.assistant.data.local.converter.Converters

@Database(
    entities = [
        MessageEntity::class,
        RoutineEntity::class,
        DeviceStateEntity::class,
        UserMemoryEntity::class,
        ExpenseEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class MahiDatabase : RoomDatabase() {

    abstract fun messageDao(): MessageDao
    abstract fun routineDao(): RoutineDao
    abstract fun deviceStateDao(): DeviceStateDao
    abstract fun userMemoryDao(): UserMemoryDao
    abstract fun expenseDao(): ExpenseDao

    companion object {
        const val DATABASE_NAME = "mahi_database"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `user_memories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `category` TEXT NOT NULL, `key` TEXT NOT NULL, `value` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `expenses` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `amount` REAL NOT NULL, `category` TEXT NOT NULL, `description` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)")
            }
        }
    }
}
