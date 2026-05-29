package com.mahi.assistant.di

import com.mahi.assistant.data.repository.AiRepository
import com.mahi.assistant.data.repository.AiRepositoryImpl
import com.mahi.assistant.ai.AiConversationEngine
import com.mahi.assistant.data.local.SettingsManager
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
        aiEngine: AiConversationEngine,
        settingsManager: SettingsManager
    ): AiRepository {
        return AiRepositoryImpl(aiEngine, settingsManager)
    }
}
