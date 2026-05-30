package com.mahi.assistant.di

import android.content.Context
import androidx.room.Room
import com.mahi.assistant.data.local.DeviceStateDao
import com.mahi.assistant.data.local.ExpenseDao
import com.mahi.assistant.data.local.MahiDatabase
import com.mahi.assistant.data.local.MessageDao
import com.mahi.assistant.data.local.RoutineDao
import com.mahi.assistant.data.local.UserMemoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideMahiDatabase(
        @ApplicationContext context: Context
    ): MahiDatabase {
        return Room.databaseBuilder(
            context,
            MahiDatabase::class.java,
            MahiDatabase.DATABASE_NAME
        )
            .addMigrations(MahiDatabase.MIGRATION_1_2)
            .build()
    }

    @Provides
    fun provideMessageDao(database: MahiDatabase): MessageDao {
        return database.messageDao()
    }

    @Provides
    fun provideRoutineDao(database: MahiDatabase): RoutineDao {
        return database.routineDao()
    }

    @Provides
    fun provideDeviceStateDao(database: MahiDatabase): DeviceStateDao {
        return database.deviceStateDao()
    }

    @Provides
    fun provideUserMemoryDao(database: MahiDatabase): UserMemoryDao {
        return database.userMemoryDao()
    }

    @Provides
    fun provideExpenseDao(database: MahiDatabase): ExpenseDao {
        return database.expenseDao()
    }
}
