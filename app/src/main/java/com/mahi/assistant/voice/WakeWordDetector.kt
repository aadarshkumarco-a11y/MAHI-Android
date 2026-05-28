package com.mahi.assistant.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineException

/**
 * WakeWordDetector uses the Picovoice Porcupine engine (v2.x) to continuously
 * listen for a configurable wake word from the device microphone.
 *
 * Audio is captured at 16 kHz, 16-bit mono — the format required by Porcupine.
 * Detection events are exposed via a StateFlow and a callback interface.
 */
class WakeWordDetector(
    private val context: Context,
    private val accessKey: String,
    private val keyword: String = "porcupine"
) {

    enum class BuiltInKeyword(val keywordName: String) {
        PICOVOICE("porcupine"),
        BUMBLEBEE("bumblebee"),
        GRASSHOPPER("grasshopper"),
        HEY_GOOGLE("hey google"),
        ALEXA("alexa"),
        AMERICANO("americano"),
        BLUEBERRY("blueberry"),
        COMPUTER("computer"),
        GRAPEFRUIT("grapefruit"),
        GRASSHOPPER_V2("grasshopper"),
        HEY_SIRI("hey siri"),
        JARVIS("jarvis"),
        OK_GOOGLE("ok google"),
        PICOVOICE_V2("picovoice"),
        PORCUPINE("porcupine"),
        TERMINATOR("terminator")
    }

    interface Callback {
        fun onWakeWordDetected() {}
    }

    private val _isDetected = MutableStateFlow(false)
    val isDetected: StateFlow<Boolean> = _isDetected.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    var callback: Callback? = null

    private var porcupine: Porcupine? = null
    private var audioRecord: AudioRecord? = null
    private var detectionJob: Job? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val COOLDOWN_MS = 1500L
    }

    /**
     * Initialize the Porcupine engine. Must be called before start().
     * In Porcupine v2.x, we use Porcupine.Builder with setBuiltInKeyword.
     */
    @Throws(PorcupineException::class)
    fun initialize() {
        releasePorcupine()
        val builder = Porcupine.Builder()
            .setAccessKey(accessKey)

        // Set the built-in keyword
        when (keyword.lowercase()) {
            "jarvis" -> builder.setBuiltInKeyword(Porcupine.BuiltInKeyword.JARVIS)
            "hey google" -> builder.setBuiltInKeyword(Porcupine.BuiltInKeyword.HEY_GOOGLE)
            "alexa" -> builder.setBuiltInKeyword(Porcupine.BuiltInKeyword.ALEXA)
            "hey siri" -> builder.setBuiltInKeyword(Porcupine.BuiltInKeyword.HEY_SIRI)
            "ok google" -> builder.setBuiltInKeyword(Porcupine.BuiltInKeyword.OK_GOOGLE)
            "computer" -> builder.setBuiltInKeyword(Porcupine.BuiltInKeyword.COMPUTER)
            "terminator" -> builder.setBuiltInKeyword(Porcupine.BuiltInKeyword.TERMINATOR)
            "grapefruit" -> builder.setBuiltInKeyword(Porcupine.BuiltInKeyword.GRAPEFRUIT)
            "grasshopper" -> builder.setBuiltInKeyword(Porcupine.BuiltInKeyword.GRASSHOPPER)
            "bumblebee" -> builder.setBuiltInKeyword(Porcupine.BuiltInKeyword.BUMBLEBEE)
            "americano" -> builder.setBuiltInKeyword(Porcupine.BuiltInKeyword.AMERICANO)
            "blueberry" -> builder.setBuiltInKeyword(Porcupine.BuiltInKeyword.BLUEBERRY)
            "picovoice" -> builder.setBuiltInKeyword(Porcupine.BuiltInKeyword.PICOVOICE)
            "porcupine" -> builder.setBuiltInKeyword(Porcupine.BuiltInKeyword.PORCUPINE)
            else -> builder.setBuiltInKeyword(Porcupine.BuiltInKeyword.PORCUPINE)
        }

        porcupine = builder.build(context.applicationContext)
    }

    fun start() {
        if (_isRunning.value) return

        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            throw SecurityException("RECORD_AUDIO permission is required for wake word detection")
        }

        if (porcupine == null) {
            initialize()
        }

        val porcupineInstance = this.porcupine ?: throw IllegalStateException("Porcupine not initialized")

        val frameLength = porcupineInstance.frameLength
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)

        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            throw IllegalStateException("Unable to get minimum AudioRecord buffer size")
        }

        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            throw IllegalStateException("AudioRecord failed to initialize")
        }

        audioRecord = record
        record.startRecording()
        _isRunning.value = true

        detectionJob = scope.launch {
            val audioBuffer = ShortArray(frameLength)
            var lastDetectionTime = 0L

            while (isActive && _isRunning.value) {
                val readCount = record.read(audioBuffer, 0, frameLength)
                if (readCount < 0) break

                try {
                    val keywordIndex = porcupineInstance.process(audioBuffer)
                    if (keywordIndex >= 0) {
                        val now = System.currentTimeMillis()
                        if (now - lastDetectionTime > COOLDOWN_MS) {
                            lastDetectionTime = now
                            _isDetected.value = true
                            withContext(Dispatchers.Main) {
                                callback?.onWakeWordDetected()
                            }
                            delay(COOLDOWN_MS)
                            _isDetected.value = false
                        }
                    }
                } catch (e: PorcupineException) {
                    break
                }
            }
        }
    }

    fun stop() {
        _isRunning.value = false
        _isDetected.value = false
        detectionJob?.cancel()
        detectionJob = null
        try { audioRecord?.stop() } catch (_: IllegalStateException) {}
        try { audioRecord?.release() } catch (_: IllegalStateException) {}
        audioRecord = null
    }

    fun destroy() {
        stop()
        releasePorcupine()
        callback = null
    }

    private fun releasePorcupine() {
        try { porcupine?.delete() } catch (_: Exception) {}
        porcupine = null
    }
}
