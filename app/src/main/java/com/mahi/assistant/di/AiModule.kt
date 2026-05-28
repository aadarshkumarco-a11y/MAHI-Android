package com.mahi.assistant.di

import com.mahi.assistant.data.remote.GeminiApiService
import com.mahi.assistant.data.repository.AiRepository
import com.mahi.assistant.data.repository.AiRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AiModule {

    @Provides
    @Singleton
    fun provideAiRepository(
        geminiApiService: GeminiApiService
    ): AiRepository {
        return AiRepositoryImpl(geminiApiService)
    }
}
