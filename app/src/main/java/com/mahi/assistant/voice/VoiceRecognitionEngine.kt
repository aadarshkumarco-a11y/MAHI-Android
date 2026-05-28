package com.mahi.assistant.voice

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * VoiceRecognitionEngine wraps Android's SpeechRecognizer API to provide
 * speech-to-text capabilities with StateFlow-based state management and
 * a callback interface for lifecycle events.
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

    private val speechRecognizer: SpeechRecognizer =
        SpeechRecognizer.createSpeechRecognizer(context)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _partialResult = MutableStateFlow<String?>(null)
    val partialResult: StateFlow<String?> = _partialResult.asStateFlow()

    private val _finalResult = MutableStateFlow<String?>(null)
    val finalResult: StateFlow<String?> = _finalResult.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    var callback: Callback? = null

    private val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }

    private val recognitionListener = object : RecognitionListener {

        override fun onReadyForSpeech(params: Bundle?) {
            _isListening.value = true
            callback?.onReady()
        }

        override fun onBeginningOfSpeech() {
            // Speech input has begun; no action required
        }

        override fun onRmsChanged(rmsdB: Float) {
            // Audio volume change; could be exposed for VU meter UI
        }

        override fun onBufferReceived(buffer: ByteArray?) {
            // Raw audio buffer received; not needed for standard STT
        }

        override fun onEndOfSpeech() {
            _isListening.value = false
            callback?.onEnd()
        }

        override fun onError(error: Int) {
            _isListening.value = false
            val errorMessage = translateErrorCode(error)
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

        override fun onEvent(eventType: Int, params: Bundle?) {
            // Reserved for future use by Android framework
        }
    }

    init {
        speechRecognizer.setRecognitionListener(recognitionListener)
    }

    /**
     * Start listening for speech input.
     */
    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            callback?.onError(SpeechRecognizer.ERROR_CLIENT)
            return
        }
        _partialResult.value = null
        _finalResult.value = null
        speechRecognizer.startListening(recognizerIntent)
    }

    /**
     * Stop listening and finalize any in-progress recognition.
     */
    fun stopListening() {
        if (_isListening.value) {
            speechRecognizer.stopListening()
        }
    }

    /**
     * Cancel the current recognition without producing a result.
     */
    fun cancel() {
        speechRecognizer.cancel()
        _isListening.value = false
        _partialResult.value = null
    }

    /**
     * Release all resources held by the SpeechRecognizer.
     * Must be called when this engine is no longer needed.
     */
    fun destroy() {
        speechRecognizer.destroy()
        _isListening.value = false
        _partialResult.value = null
        _finalResult.value = null
        callback = null
    }

    companion object {
        /**
         * Translate SpeechRecognizer error codes to human-readable messages.
         */
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
