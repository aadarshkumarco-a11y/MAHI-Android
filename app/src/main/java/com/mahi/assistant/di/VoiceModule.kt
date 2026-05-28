package com.mahi.assistant.di

import android.content.Context
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.Locale
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object VoiceModule {

    @Provides
    @Singleton
    fun provideSpeechRecognizer(
        @ApplicationContext context: Context
    ): SpeechRecognizer {
        return if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            throw IllegalStateException("Speech recognition is not available on this device")
        }
    }

    @Provides
    @Singleton
    fun provideTextToSpeech(
        @ApplicationContext context: Context
    ): TextToSpeech {
        var tts: TextToSpeech? = null
        val initListener = TextToSpeech.OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
            }
        }
        tts = TextToSpeech(context, initListener)
        return tts
    }
}
