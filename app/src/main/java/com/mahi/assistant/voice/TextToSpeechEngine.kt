package com.mahi.assistant.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * TextToSpeechEngine wraps Android's TextToSpeech API.
 *
 * CRITICAL FIX: TTS is now created with applicationContext and wrapped
 * in comprehensive try/catch blocks to prevent crash on devices where
 * TTS is unavailable or fails to initialize.
 */
class TextToSpeechEngine(
    private val context: Context
) {

    interface Callback {
        fun onSpeakStart(utteranceId: String) {}
        fun onSpeakEnd(utteranceId: String) {}
        fun onError(utteranceId: String) {}
    }

    private var tts: TextToSpeech? = null

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    var callback: Callback? = null

    private var speechRate: Float = 1.0f
    private var pitch: Float = 1.0f

    init {
        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                try {
                    if (status == TextToSpeech.SUCCESS) {
                        val result = tts?.setLanguage(Locale.US)
                        if (result == TextToSpeech.LANG_MISSING_DATA ||
                            result == TextToSpeech.LANG_NOT_SUPPORTED
                        ) {
                            try { tts?.language = Locale.getDefault() } catch (_: Exception) {}
                        }
                        setupUtteranceListener()
                        _isReady.value = true
                    } else {
                        Log.w("TTS", "TTS initialization failed with status: $status")
                        _isReady.value = false
                    }
                } catch (e: Exception) {
                    Log.e("TTS", "Error in TTS init callback", e)
                    _isReady.value = false
                }
            }
        } catch (e: Exception) {
            Log.e("TTS", "Failed to create TextToSpeech instance", e)
            tts = null
            _isReady.value = false
        }
    }

    private fun setupUtteranceListener() {
        try {
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String) {
                    _isSpeaking.value = true
                    callback?.onSpeakStart(utteranceId)
                }

                override fun onDone(utteranceId: String) {
                    _isSpeaking.value = false
                    callback?.onSpeakEnd(utteranceId)
                }

                override fun onError(utteranceId: String) {
                    _isSpeaking.value = false
                    callback?.onError(utteranceId)
                }
            })
        } catch (e: Exception) {
            Log.e("TTS", "Failed to set UtteranceProgressListener", e)
        }
    }

    fun speak(text: String, utteranceId: String = "mahi_utterance_${System.currentTimeMillis()}") {
        try {
            val engine = tts ?: return
            if (!_isReady.value) return

            engine.setSpeechRate(speechRate)
            engine.setPitch(pitch)

            val params = android.os.Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        } catch (e: Exception) {
            Log.e("TTS", "speak() failed", e)
        }
    }

    fun speakQueue(text: String, utteranceId: String = "mahi_utterance_${System.currentTimeMillis()}") {
        try {
            val engine = tts ?: return
            if (!_isReady.value) return

            engine.setSpeechRate(speechRate)
            engine.setPitch(pitch)

            val params = android.os.Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }
            engine.speak(text, TextToSpeech.QUEUE_ADD, params, utteranceId)
        } catch (e: Exception) {
            Log.e("TTS", "speakQueue() failed", e)
        }
    }

    fun stop() {
        try { tts?.stop() } catch (_: Exception) {}
        _isSpeaking.value = false
    }

    fun setSpeechRate(rate: Float) {
        speechRate = rate.coerceIn(0.5f, 2.0f)
    }

    fun setPitch(pitch: Float) {
        this.pitch = pitch.coerceIn(0.5f, 2.0f)
    }

    fun isLanguageAvailable(locale: Locale): Int {
        return try { tts?.isLanguageAvailable(locale) ?: TextToSpeech.LANG_NOT_SUPPORTED }
        catch (_: Exception) { TextToSpeech.LANG_NOT_SUPPORTED }
    }

    fun setLanguage(locale: Locale): Int {
        return try { tts?.setLanguage(locale) ?: TextToSpeech.LANG_NOT_SUPPORTED }
        catch (_: Exception) { TextToSpeech.LANG_NOT_SUPPORTED }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {}
        tts = null
        _isSpeaking.value = false
        _isReady.value = false
        callback = null
    }
}
