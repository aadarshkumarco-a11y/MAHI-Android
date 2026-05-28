package com.mahi.assistant.di

import android.content.Context
import androidx.room.Room
import com.mahi.assistant.data.local.DeviceStateDao
import com.mahi.assistant.data.local.MahiDatabase
import com.mahi.assistant.data.local.MessageDao
import com.mahi.assistant.data.local.RoutineDao
import com.mahi.assistant.data.local.converter.Converters
import com.google.gson.Gson
import javax.inject.Named
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
        @ApplicationContext context: Context,
        gson: Gson
    ): MahiDatabase {
        return Room.databaseBuilder(
            context,
            MahiDatabase::class.java,
            MahiDatabase.DATABASE_NAME
        )
            .addTypeConverter(Converters())
            .fallbackToDestructiveMigration()
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
}
