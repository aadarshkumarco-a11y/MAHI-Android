package com.mahi.assistant.di

import android.content.Context
import com.mahi.assistant.ai.AiConversationEngine
import com.mahi.assistant.ai.IntentClassifier
import com.mahi.assistant.data.local.SettingsManager
import com.mahi.assistant.voice.TextToSpeechEngine
import com.mahi.assistant.voice.VoiceRecognitionEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object VoiceModule {

    @Provides
    @Singleton
    fun provideAiConversationEngine(
        settingsManager: SettingsManager
    ): AiConversationEngine {
        return AiConversationEngine(settingsManager)
    }

    @Provides
    @Singleton
    fun provideIntentClassifier(
        aiEngine: AiConversationEngine
    ): IntentClassifier {
        return IntentClassifier(aiEngine)
    }

    @Provides
    @Singleton
    fun provideVoiceRecognitionEngine(
        @ApplicationContext context: Context
    ): VoiceRecognitionEngine {
        return VoiceRecognitionEngine(context)
    }

    @Provides
    @Singleton
    fun provideTextToSpeechEngine(
        @ApplicationContext context: Context
    ): TextToSpeechEngine {
        return TextToSpeechEngine(context)
    }
}
