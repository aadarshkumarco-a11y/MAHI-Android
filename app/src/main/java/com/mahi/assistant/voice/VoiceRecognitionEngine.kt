package com.mahi.assistant.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * VoiceRecognitionEngine wraps Android's SpeechRecognizer API.
 *
 * CRITICAL FIX: SpeechRecognizer is NOT created in the init block anymore.
 * It is lazily created on first startListening() call on the main thread.
 * This prevents crashes when Hilt creates this singleton on a background thread.
 */
class VoiceRecognitionEngine(
    private val context: android.content.Context
) {

    interface Callback {
        fun onResult(text: String) {}
        fun onPartial(text: String) {}
        fun onError(code: Int) {}
        fun onReady() {}
        fun onEnd() {}
    }

    // SpeechRecognizer is now LAZILY created — NOT in init block
    private var speechRecognizer: SpeechRecognizer? = null
    private var isRecognizerInitialized = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _partialResult = MutableStateFlow<String?>(null)
    val partialResult: StateFlow<String?> = _partialResult.asStateFlow()

    private val _finalResult = MutableStateFlow<String?>(null)
    val finalResult: StateFlow<String?> = _finalResult.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    var callback: Callback? = null

    private val recognitionListener = object : RecognitionListener {

        override fun onReadyForSpeech(params: Bundle?) {
            _isListening.value = true
            callback?.onReady()
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            _isListening.value = false
            callback?.onEnd()
        }

        override fun onError(error: Int) {
            _isListening.value = false
            _partialResult.value = null
            callback?.onError(error)
        }

        override fun onResults(results: Bundle?) {
            _isListening.value = false
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull() ?: ""
            if (text.isNotEmpty()) {
                _finalResult.value = text
                _partialResult.value = null
                callback?.onResult(text)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull()
            if (text != null) {
                _partialResult.value = text
                callback?.onPartial(text)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    // NO SpeechRecognizer creation in init — prevents crash on non-main thread
    init {
        Log.d("VoiceRecognition", "VoiceRecognitionEngine created (SpeechRecognizer will be lazy)")
    }

    /**
     * Lazily create SpeechRecognizer on the main thread.
     * Must be called from the main thread.
     */
    private fun ensureRecognizerCreated(): Boolean {
        if (isRecognizerInitialized && speechRecognizer != null) return true

        return try {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                Log.w("VoiceRecognition", "Speech recognition not available on this device")
                return false
            }
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
            speechRecognizer?.setRecognitionListener(recognitionListener)
            isRecognizerInitialized = true
            Log.d("VoiceRecognition", "SpeechRecognizer created successfully")
            true
        } catch (e: Exception) {
            Log.e("VoiceRecognition", "Failed to create SpeechRecognizer", e)
            speechRecognizer = null
            isRecognizerInitialized = false
            false
        }
    }

    /**
     * Start listening for speech input.
     */
    fun startListening() {
        if (!ensureRecognizerCreated()) {
            callback?.onError(SpeechRecognizer.ERROR_CLIENT)
            return
        }

        val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN,en-US")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        _partialResult.value = null
        _finalResult.value = null

        try {
            speechRecognizer?.startListening(recognizerIntent)
        } catch (e: Exception) {
            Log.e("VoiceRecognition", "startListening failed", e)
            // Try recreating the recognizer
            try {
                speechRecognizer?.destroy()
            } catch (_: Exception) {}
            speechRecognizer = null
            isRecognizerInitialized = false
            callback?.onError(SpeechRecognizer.ERROR_CLIENT)
        }
    }

    /**
     * Stop listening and finalize any in-progress recognition.
     */
    fun stopListening() {
        if (_isListening.value) {
            try {
                speechRecognizer?.stopListening()
            } catch (_: Exception) {}
        }
    }

    /**
     * Cancel the current recognition without producing a result.
     */
    fun cancel() {
        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {}
        _isListening.value = false
        _partialResult.value = null
    }

    /**
     * Release all resources held by the SpeechRecognizer.
     */
    fun destroy() {
        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
        isRecognizerInitialized = false
        _isListening.value = false
        _partialResult.value = null
        _finalResult.value = null
        callback = null
    }

    companion object {
        fun translateErrorCode(errorCode: Int): String = when (errorCode) {
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_CLIENT -> "Client error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
            SpeechRecognizer.ERROR_NO_MATCH -> "No recognition match found"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            else -> "Unknown error (code $errorCode)"
        }
    }
}
