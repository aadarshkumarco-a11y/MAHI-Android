package com.mahi.assistant.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * TextToSpeechEngine wraps Android's TextToSpeech API to provide
 * speech synthesis with StateFlow-based state management and
 * a callback interface for utterance lifecycle events.
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
        // TextToSpeech constructor can throw on some devices/ROMs
        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    try {
                        val result = tts?.setLanguage(Locale.US)
                        if (result == TextToSpeech.LANG_MISSING_DATA ||
                            result == TextToSpeech.LANG_NOT_SUPPORTED
                        ) {
                            tts?.language = Locale.getDefault()
                        }
                    } catch (_: Exception) {
                        // Language setting failed, TTS will still work with default
                    }
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

                            @Deprecated("Deprecated in API 21; onError(String) is used instead")
                            override fun onError(utteranceId: String) {
                                _isSpeaking.value = false
                                callback?.onError(utteranceId)
                            }
                        })
                    } catch (_: Exception) { /* Listener setup failed */ }
                    _isReady.value = true
                } else {
                    _isReady.value = false
                }
            }
        } catch (e: Exception) {
            // TTS not available on this device
            tts = null
            _isReady.value = false
        }
    }

    /**
     * Speak the given text aloud.
     *
     * @param text The text to synthesize.
     * @param utteranceId A unique identifier for this utterance, used in callbacks.
     */
    fun speak(text: String, utteranceId: String = "mahi_utterance_${System.currentTimeMillis()}") {
        val engine = tts ?: return
        if (!_isReady.value) return

        engine.setSpeechRate(speechRate)
        engine.setPitch(pitch)

        val params = android.os.Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    /**
     * Queue additional text to be spoken after the current utterance finishes.
     *
     * @param text The text to synthesize.
     * @param utteranceId A unique identifier for this utterance.
     */
    fun speakQueue(text: String, utteranceId: String = "mahi_utterance_${System.currentTimeMillis()}") {
        val engine = tts ?: return
        if (!_isReady.value) return

        engine.setSpeechRate(speechRate)
        engine.setPitch(pitch)

        val params = android.os.Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        engine.speak(text, TextToSpeech.QUEUE_ADD, params, utteranceId)
    }

    /**
     * Stop all current and queued speech immediately.
     */
    fun stop() {
        tts?.stop()
        _isSpeaking.value = false
    }

    /**
     * Set the speech rate.
     *
     * @param rate Speech rate from 0.5 (half speed) to 2.0 (double speed). Default is 1.0.
     */
    fun setSpeechRate(rate: Float) {
        speechRate = rate.coerceIn(0.5f, 2.0f)
    }

    /**
     * Set the pitch of the synthesized voice.
     *
     * @param pitch Pitch from 0.5 (low) to 2.0 (high). Default is 1.0.
     */
    fun setPitch(pitch: Float) {
        this.pitch = pitch.coerceIn(0.5f, 2.0f)
    }

    /**
     * Check if a specific language is available for TTS.
     *
     * @param locale The locale to check.
     * @return One of TextToSpeech.LANG_AVAILABLE, LANG_COUNTRY_AVAILABLE,
     *         LANG_COUNTRY_VAR_AVAILABLE, LANG_MISSING_DATA, or LANG_NOT_SUPPORTED.
     */
    fun isLanguageAvailable(locale: Locale): Int {
        return tts?.isLanguageAvailable(locale) ?: TextToSpeech.LANG_NOT_SUPPORTED
    }

    /**
     * Set the language for TTS output.
     *
     * @param locale The desired locale.
     * @return Result code from TextToSpeech.
     */
    fun setLanguage(locale: Locale): Int {
        return tts?.setLanguage(locale) ?: TextToSpeech.LANG_NOT_SUPPORTED
    }

    /**
     * Release all resources held by the TextToSpeech engine.
     * Must be called when this engine is no longer needed.
     */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        _isSpeaking.value = false
        _isReady.value = false
        callback = null
    }
}
