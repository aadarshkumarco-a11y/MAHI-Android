package com.mahi.assistant.ui.viewmodel

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Settings
import android.telephony.SmsManager
import android.text.format.DateUtils
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mahi.assistant.ai.AiConversationEngine
import com.mahi.assistant.ai.IntentClassifier
import com.mahi.assistant.api.NewsClient
import com.mahi.assistant.api.NewsService
import com.mahi.assistant.automation.RoutineEngine
import com.mahi.assistant.control.DeviceControlManager
import com.mahi.assistant.data.local.ExpenseDao
import com.mahi.assistant.data.local.ExpenseEntity
import com.mahi.assistant.data.local.MessageDao
import com.mahi.assistant.data.local.MessageEntity
import com.mahi.assistant.data.local.SettingsManager
import com.mahi.assistant.data.local.UserMemoryDao
import com.mahi.assistant.data.local.UserMemoryEntity
import com.mahi.assistant.receiver.ReminderReceiver
import com.mahi.assistant.data.model.AssistantState
import com.mahi.assistant.data.model.ChatMessage
import com.mahi.assistant.data.model.MessageRole
import com.mahi.assistant.data.model.NotificationItem
import com.mahi.assistant.data.model.Routine
import com.mahi.assistant.service.MahiNotificationListenerService
import com.mahi.assistant.voice.TextToSpeechEngine
import com.mahi.assistant.voice.VoiceRecognitionEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class WeatherUiState(
    val city: String = "",
    val temperature: Double = 0.0,
    val description: String = "",
    val humidity: Int = 0,
    val windSpeed: Double = 0.0,
    val pressure: Int = 0,
    val feelsLike: Double = 0.0,
    val icon: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

data class NewsUiState(
    val articles: List<com.mahi.assistant.api.Article> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedCategory: String = "general"
)

data class DeviceUiState(
    val flashlight: Boolean = false,
    val wifi: Boolean = false,
    val bluetooth: Boolean = false,
    val brightness: Int = 50,
    val volume: Int = 50,
    val ringerMode: Int = 2,
    val autoRotate: Boolean = false,
    val hotspot: Boolean = false,
    val mobileData: Boolean = true,
    val dnd: Boolean = false,
    val batterySaver: Boolean = false,
    val screenTimeout: Int = 30000
)

data class SettingsUiState(
    val geminiKey: String = "",
    val isGeminiKeyValid: Boolean = false,
    val grokKey: String = "",
    val porcupineKey: String = "",
    val weatherKey: String = "",
    val newsKey: String = "",
    val voiceSpeed: Float = 1.0f,
    val voicePitch: Float = 1.0f,
    val wakeWord: String = "Hey Mahi",
    val autoStartOnBoot: Boolean = true,
    val floatingAssistant: Boolean = false,
    val isSaved: Boolean = false,
    val defaultCity: String = "New Delhi",
    val continuousMode: Boolean = false,
    val language: String = "en"
)

@HiltViewModel
class MahiViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val aiEngine: AiConversationEngine,
    private val intentClassifier: IntentClassifier,
    private val deviceControlManager: DeviceControlManager,
    private val routineEngine: RoutineEngine,
    private val messageDao: MessageDao,
    private val userMemoryDao: UserMemoryDao,
    private val expenseDao: ExpenseDao,
    private val voiceRecognition: VoiceRecognitionEngine,
    private val ttsEngine: TextToSpeechEngine,
    private val settingsManager: SettingsManager
) : ViewModel() {

    private val _assistantState = MutableStateFlow(AssistantState.IDLE)
    val assistantState: StateFlow<AssistantState> = _assistantState.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _currentInput = MutableStateFlow("")
    val currentInput: StateFlow<String> = _currentInput.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _weatherState = MutableStateFlow(WeatherUiState())
    val weatherState: StateFlow<WeatherUiState> = _weatherState.asStateFlow()

    private val _newsState = MutableStateFlow(NewsUiState())
    val newsState: StateFlow<NewsUiState> = _newsState.asStateFlow()

    private val _deviceState = MutableStateFlow(DeviceUiState())
    val deviceState: StateFlow<DeviceUiState> = _deviceState.asStateFlow()

    private val _notifications = MutableStateFlow<List<NotificationItem>>(emptyList())
    val notifications: StateFlow<List<NotificationItem>> = _notifications.asStateFlow()

    private val _routines = MutableStateFlow<List<Routine>>(emptyList())
    val routines: StateFlow<List<Routine>> = _routines.asStateFlow()

    private val _currentRoute = MutableStateFlow("home")
    val currentRoute: StateFlow<String> = _currentRoute.asStateFlow()

    private val _settingsState = MutableStateFlow(SettingsUiState())
    val settingsState: StateFlow<SettingsUiState> = _settingsState.asStateFlow()

    private val _welcomeMessage = MutableStateFlow<String?>(null)
    val welcomeMessage: StateFlow<String?> = _welcomeMessage.asStateFlow()

    // Continuous mode state
    private val _continuousMode = MutableStateFlow(false)
    val continuousMode: StateFlow<Boolean> = _continuousMode.asStateFlow()

    // Language state
    private val _currentLanguage = MutableStateFlow(settingsManager.getLanguage())
    val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()

    private var processingJob: Job? = null

    // WhatsApp confirmation state machine
    private val _whatsappPendingContact = MutableStateFlow<String?>(null)
    val whatsappPendingContact: StateFlow<String?> = _whatsappPendingContact.asStateFlow()
    private var _whatsappPendingMessage: String? = null
    private var _whatsappPendingNumber: String? = null
    private var _whatsappStep: Int = 0 // 0=idle, 1=awaiting confirmation, 2=awaiting message, 3=awaiting send confirmation

    // Voice session tracking for auto-enabling continuous mode
    private var _isVoiceSession = false
    private var _voiceInputCount = 0

    // ── App package map ──────────────────────────────────────────────────────

    private val appPackageMap = mapOf(
        "youtube" to "com.google.android.youtube",
        "whatsapp" to "com.whatsapp",
        "instagram" to "com.instagram.android",
        "facebook" to "com.facebook.katana",
        "twitter" to "com.twitter.android",
        "x" to "com.twitter.android",
        "telegram" to "org.telegram.messenger",
        "snapchat" to "com.snapchat.android",
        "tiktok" to "com.zhiliaoapp.musically",
        "spotify" to "com.spotify.music",
        "gmail" to "com.google.android.gm",
        "google" to "com.google.android.googlequicksearchbox",
        "chrome" to "com.android.chrome",
        "maps" to "com.google.android.apps.maps",
        "google maps" to "com.google.android.apps.maps",
        "camera" to "com.android.camera",
        "phone" to "com.android.dialer",
        "dialer" to "com.android.dialer",
        "contacts" to "com.android.contacts",
        "settings" to "com.android.settings",
        "calculator" to "com.android.calculator2",
        "clock" to "com.android.deskclock",
        "calendar" to "com.android.calendar",
        "play store" to "com.android.vending",
        "netflix" to "com.netflix.mediaclient",
        "amazon" to "com.amazon.mShop.android.shopping",
        "flipkart" to "com.flipkart.android",
        "swiggy" to "in.swiggy.android",
        "zomato" to "com.application.zomato",
        "paytm" to "net.one97.paytm",
        "gpay" to "com.google.android.apps.nbu.paisa.user",
        "google pay" to "com.google.android.apps.nbu.paisa.user",
        "phonepe" to "com.phonepe.app",
        "gallery" to "com.android.gallery3d",
        "photos" to "com.google.android.apps.photos",
        "notes" to "com.google.android.keep",
        "keep" to "com.google.android.keep",
        "meet" to "com.google.android.apps.meetings",
        "zoom" to "us.zoom.videomeetings",
        "linkedin" to "com.linkedin.android",
        "reddit" to "com.reddit.frontpage",
        "pinterest" to "com.pinterest",
        "discord" to "com.discord",
        "files" to "com.google.android.apps.nbu.files",
        "file manager" to "com.google.android.apps.nbu.files",
        "sharechat" to "in.mohalla.sharechat",
        "jio" to "com.jio.myjio",
        "jiotv" to "com.jio.jiotv",
        "hotstar" to "in.startv.hotstar",
        "disney hotstar" to "in.startv.hotstar",
        "mx player" to "com.mxtech.videoplayer.ad",
        "vlc" to "org.videolan.vlc",
        "truecaller" to "com.truecaller",
        "uber" to "com.ubercab",
        "ola" to "com.olacabs.customer",
        "rapido" to "com.rapido.passenger"
    )

    init {
        android.util.Log.d("MahiViewModel", "ViewModel init starting...")

        // Load conversation history from Room and pass to AI engine as context
        viewModelScope.launch {
            try {
                val entities = messageDao.getRecent(50)
                val chatMessages = entities.map { entity: MessageEntity ->
                    com.mahi.assistant.ai.ChatMessage(
                        role = if (entity.role == "USER") com.mahi.assistant.ai.ChatMessage.ROLE_USER else com.mahi.assistant.ai.ChatMessage.ROLE_MODEL,
                        content = entity.content,
                        timestamp = entity.timestamp
                    )
                }
                aiEngine.loadContext(chatMessages)

                _messages.value = entities.map { entity: MessageEntity ->
                    ChatMessage(
                        id = entity.id.toString(),
                        role = if (entity.role == "USER") MessageRole.USER else MessageRole.ASSISTANT,
                        content = entity.content,
                        timestamp = entity.timestamp
                    )
                }
                android.util.Log.d("MahiViewModel", "Loaded ${entities.size} messages from DB")
            } catch (e: Exception) {
                android.util.Log.e("MahiViewModel", "Failed to load messages", e)
            }
        }

        // Load continuous mode state
        try { _continuousMode.value = settingsManager.isContinuousMode() } catch (_: Exception) { }

        try { loadSettings() } catch (e: Exception) {
            android.util.Log.e("MahiViewModel", "Failed to load settings", e)
        }

        // CRITICAL FIX: Apply saved language to TTS/STT on startup
        try { applyLanguageToEngines(_currentLanguage.value) } catch (e: Exception) {
            android.util.Log.e("MahiViewModel", "Failed to apply language on startup", e)
        }

        try {
            voiceRecognition.callback = object : VoiceRecognitionEngine.Callback {
                override fun onResult(text: String) {
                    _isListening.value = false
                    if (text.isNotBlank()) {
                        processInput(text, fromVoice = true)
                    } else if (_continuousMode.value) {
                        // Empty result in continuous mode — restart listening after brief pause
                        viewModelScope.launch {
                            try {
                                kotlinx.coroutines.delay(800) // Brief pause before re-listening
                                if (_continuousMode.value && _assistantState.value != AssistantState.SPEAKING) {
                                    startListening()
                                }
                            } catch (_: Exception) {}
                        }
                    } else {
                        _assistantState.value = AssistantState.IDLE
                    }
                }
                override fun onPartial(text: String) {
                    try { _partialTranscript.value = text } catch (_: Exception) {}
                }
                override fun onError(code: Int) {
                    _isListening.value = false
                    if (_continuousMode.value) {
                        // Error in continuous mode — auto-restart after delay
                        val shouldRetry = code != android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
                        if (shouldRetry) {
                            viewModelScope.launch {
                                try {
                                    kotlinx.coroutines.delay(1500) // Wait before retry
                                    if (_continuousMode.value && _assistantState.value != AssistantState.SPEAKING) {
                                        startListening()
                                    }
                                } catch (_: Exception) {}
                            }
                        } else {
                            _assistantState.value = AssistantState.IDLE
                        }
                    } else {
                        _assistantState.value = AssistantState.IDLE
                    }
                }
                override fun onReady() {
                    _isListening.value = true
                    _assistantState.value = AssistantState.LISTENING
                }
                override fun onEnd() {
                    _isListening.value = false
                    if (_assistantState.value == AssistantState.LISTENING) {
                        // Speech ended naturally — in continuous mode, don't go IDLE
                        // The onResult or onError will handle next steps
                        if (!_continuousMode.value) {
                            _assistantState.value = AssistantState.IDLE
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MahiViewModel", "Failed to set voiceRecognition callback", e)
        }

        try {
            ttsEngine.callback = object : TextToSpeechEngine.Callback {
                override fun onSpeakStart(utteranceId: String) {
                    _assistantState.value = AssistantState.SPEAKING
                }
                override fun onSpeakEnd(utteranceId: String) {
                    if (_continuousMode.value) {
                        // CONTINUOUS CONVERSATION: Auto-restart listening after TTS ends
                        // This creates a natural back-and-forth like ChatGPT Voice
                        _assistantState.value = AssistantState.IDLE
                        viewModelScope.launch {
                            try {
                                kotlinx.coroutines.delay(600) // Brief natural pause
                                if (_continuousMode.value) {
                                    startListening()
                                }
                            } catch (_: Exception) {}
                        }
                    } else {
                        _assistantState.value = AssistantState.IDLE
                    }
                }
                override fun onError(utteranceId: String) {
                    _assistantState.value = AssistantState.IDLE
                    // Even on TTS error, restart listening in continuous mode
                    if (_continuousMode.value) {
                        viewModelScope.launch {
                            try {
                                kotlinx.coroutines.delay(1000)
                                if (_continuousMode.value) {
                                    startListening()
                                }
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MahiViewModel", "Failed to set ttsEngine callback", e)
        }

        viewModelScope.launch {
            try { routineEngine.availableRoutines.collect { _routines.value = it } } catch (_: Exception) { }
        }
        viewModelScope.launch {
            try { MahiNotificationListenerService.instance?.notifications?.collect { _notifications.value = it } } catch (_: Exception) { }
        }

        android.util.Log.d("MahiViewModel", "ViewModel init completed")
    }

    // ── Settings ──────────────────────────────────────────────────────────

    fun loadSettings() {
        _settingsState.value = SettingsUiState(
            geminiKey = settingsManager.getGeminiApiKey(),
            isGeminiKeyValid = settingsManager.isGeminiKeyValid(),
            grokKey = settingsManager.getGrokApiKey(),
            porcupineKey = settingsManager.getPorcupineKey(),
            weatherKey = settingsManager.getWeatherApiKey(),
            newsKey = settingsManager.getNewsApiKey(),
            voiceSpeed = settingsManager.getVoiceSpeed(),
            voicePitch = settingsManager.getVoicePitch(),
            wakeWord = settingsManager.getWakeWord(),
            autoStartOnBoot = settingsManager.getAutoStartOnBoot(),
            floatingAssistant = settingsManager.getFloatingAssistant(),
            defaultCity = settingsManager.getDefaultCity(),
            continuousMode = settingsManager.isContinuousMode(),
            language = settingsManager.getLanguage()
        )
    }

    fun updateGeminiKey(key: String) { settingsManager.setGeminiApiKey(key); _settingsState.value = _settingsState.value.copy(geminiKey = key, isGeminiKeyValid = key.startsWith("AIza") && key.length >= 30, isSaved = false) }
    fun updateGrokKey(key: String) { settingsManager.setGrokApiKey(key); _settingsState.value = _settingsState.value.copy(grokKey = key, isSaved = false) }
    fun updatePorcupineKey(key: String) { settingsManager.setPorcupineKey(key); _settingsState.value = _settingsState.value.copy(porcupineKey = key, isSaved = false) }
    fun updateWeatherKey(key: String) { settingsManager.setWeatherApiKey(key); _settingsState.value = _settingsState.value.copy(weatherKey = key, isSaved = false) }
    fun updateNewsKey(key: String) { settingsManager.setNewsApiKey(key); _settingsState.value = _settingsState.value.copy(newsKey = key, isSaved = false) }
    fun updateVoiceSpeed(speed: Float) { settingsManager.setVoiceSpeed(speed); _settingsState.value = _settingsState.value.copy(voiceSpeed = speed) }
    fun updateVoicePitch(pitch: Float) { settingsManager.setVoicePitch(pitch); _settingsState.value = _settingsState.value.copy(voicePitch = pitch) }
    fun updateWakeWord(word: String) { settingsManager.setWakeWord(word); _settingsState.value = _settingsState.value.copy(wakeWord = word) }
    fun updateAutoStartOnBoot(enabled: Boolean) { settingsManager.setAutoStartOnBoot(enabled); _settingsState.value = _settingsState.value.copy(autoStartOnBoot = enabled) }
    fun updateFloatingAssistant(enabled: Boolean) { settingsManager.setFloatingAssistant(enabled); _settingsState.value = _settingsState.value.copy(floatingAssistant = enabled) }
    fun updateDefaultCity(city: String) { settingsManager.setDefaultCity(city); _settingsState.value = _settingsState.value.copy(defaultCity = city) }
    fun updateContinuousMode(enabled: Boolean) { settingsManager.setContinuousMode(enabled); _continuousMode.value = enabled; _settingsState.value = _settingsState.value.copy(continuousMode = enabled) }
    fun updateLanguage(lang: String) {
        settingsManager.setLanguage(lang)
        _currentLanguage.value = lang
        _settingsState.value = _settingsState.value.copy(language = lang)
        // CRITICAL FIX: Also update TTS and STT languages (was missing before!)
        applyLanguageToEngines(lang)
    }
    fun toggleLanguage() {
        val newLang = if (_currentLanguage.value == "en") "hi" else "en"
        _currentLanguage.value = newLang
        settingsManager.setLanguage(newLang)
        _settingsState.value = _settingsState.value.copy(language = newLang)
        applyLanguageToEngines(newLang)
    }

    /**
     * CRITICAL FIX: Apply language to both TTS and SpeechRecognizer.
     * English mode = en-US ONLY for STT (so English speech is written in English!)
     * Hindi mode = hi-IN ONLY for STT (so Hindi speech is recognized properly)
     * Both modes use appropriate TTS language.
     */
    private fun applyLanguageToEngines(lang: String) {
        try {
            if (lang == "hi") {
                // Hindi mode: TTS speaks Hindi, STT listens for Hindi
                val hindiLocale = java.util.Locale("hi", "IN")
                val result = ttsEngine.setLanguage(hindiLocale)
                if (result == android.speech.tts.TextToSpeech.LANG_MISSING_DATA || result == android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED) {
                    // Hindi TTS not available — use Indian English as fallback
                    ttsEngine.setLanguage(java.util.Locale("en", "IN"))
                }
                // STT: Hindi primary — this ensures Hindi speech is written as Hindi text
                voiceRecognition.recognitionLanguage = "hi-IN"
            } else {
                // English mode: TTS speaks English, STT listens for English
                ttsEngine.setLanguage(java.util.Locale.US)
                // STT: English ONLY — this ensures English speech is written as English text!
                // NOT "en-US,hi-IN" because that causes English to be written in Hindi!
                voiceRecognition.recognitionLanguage = "en-US"
            }
        } catch (e: Exception) {
            android.util.Log.e("MahiViewModel", "Failed to apply language to engines", e)
        }
    }
    fun saveAllSettings() { _settingsState.value = _settingsState.value.copy(isSaved = true) }

    fun navigateTo(route: String) { _currentRoute.value = route }
    fun startListening() { _partialTranscript.value = ""; voiceRecognition.startListening() }
    fun stopListening() { voiceRecognition.stopListening(); _isListening.value = false; _assistantState.value = AssistantState.IDLE }
    fun updateInput(text: String) { _currentInput.value = text }

    fun submitInput() {
        val input = _currentInput.value.trim()
        if (input.isNotBlank()) { _currentInput.value = ""; processInput(input) }
    }

    // ══════════════════════════════════════════════════════════════════════
    // MAIN BRAIN - Process any input with MAX intelligence
    // ══════════════════════════════════════════════════════════════════════

    fun processInput(input: String, fromVoice: Boolean = false) {
        processingJob?.cancel()
        processingJob = viewModelScope.launch {
            _assistantState.value = AssistantState.THINKING

            // Auto-enable continuous mode after 2nd voice input in a session
            if (fromVoice) {
                _isVoiceSession = true
                _voiceInputCount++
                if (_voiceInputCount >= 2 && !_continuousMode.value) {
                    updateContinuousMode(true)
                }
            }

            // Check WhatsApp confirmation flow first
            if (_whatsappStep > 0) {
                val waResponse = handleWhatsAppConfirmation(input)
                val assistantMessage = ChatMessage(role = MessageRole.ASSISTANT, content = waResponse)
                _messages.value = _messages.value + ChatMessage(role = MessageRole.USER, content = input) + assistantMessage
                try { messageDao.insert(MessageEntity(role = "USER", content = input, timestamp = System.currentTimeMillis())) } catch (_: Exception) { }
                try { messageDao.insert(MessageEntity(role = "ASSISTANT", content = waResponse, timestamp = System.currentTimeMillis())) } catch (_: Exception) { }
                val speechText = waResponse.replace(Regex("""\[\w+\]\s*"""), "").replace(Regex("""[*#_]"""), "").take(500)
                ttsEngine.speak(speechText, "response_${System.currentTimeMillis()}")
                return@launch
            }

            // Save user message
            val userMessage = ChatMessage(role = MessageRole.USER, content = input)
            _messages.value = _messages.value + userMessage
            try { messageDao.insert(MessageEntity(role = "USER", content = input, timestamp = System.currentTimeMillis())) } catch (_: Exception) { }

            // Greeting detection — respond with personality
            val greetingResponse = detectGreeting(input)
            if (greetingResponse != null) {
                val assistantMessage = ChatMessage(role = MessageRole.ASSISTANT, content = greetingResponse)
                _messages.value = _messages.value + assistantMessage
                try { messageDao.insert(MessageEntity(role = "ASSISTANT", content = greetingResponse, timestamp = System.currentTimeMillis())) } catch (_: Exception) { }
                val speechText = greetingResponse.replace(Regex("""[*#_]"""), "").take(500)
                ttsEngine.speak(speechText, "response_${System.currentTimeMillis()}")
                return@launch
            }

            // Classify using AI-powered classifier (regex first, then Gemini)
            val intent = intentClassifier.classify(input)
            var response = handleIntent(intent, input)

            // Handle FALLBACK_TO_SEARCH from AI engine — never show to user
            if (response == "FALLBACK_TO_SEARCH") {
                response = try {
                    val searchResult = com.mahi.assistant.api.WebSearchService.search(input)
                    if (searchResult.isNotBlank() && searchResult.length > 20) {
                        val isHindi = com.mahi.assistant.api.WebSearchService.isHindiText(input)
                        if (isHindi) "Mujhe ye mila: ${searchResult.take(500)}"
                        else "Here's what I found: ${searchResult.take(500)}"
                    } else {
                        "I'm having trouble connecting right now, Boss. Let me try again in a moment."
                    }
                } catch (e: Exception) {
                    "I'm having a moment — my AI services are warming up. Try again in a sec!"
                }
            }

            // Save assistant message
            val assistantMessage = ChatMessage(role = MessageRole.ASSISTANT, content = response)
            _messages.value = _messages.value + assistantMessage
            try { messageDao.insert(MessageEntity(role = "ASSISTANT", content = response, timestamp = System.currentTimeMillis())) } catch (_: Exception) { }

            // Update AI engine context with new messages
            try {
                val recentMessages = _messages.value.takeLast(50).map {
                    com.mahi.assistant.ai.ChatMessage(
                        role = if (it.role == MessageRole.USER) com.mahi.assistant.ai.ChatMessage.ROLE_USER else com.mahi.assistant.ai.ChatMessage.ROLE_MODEL,
                        content = it.content,
                        timestamp = it.timestamp
                    )
                }
                aiEngine.loadContext(recentMessages)
            } catch (_: Exception) { }

            // Proactively save important user memories from this exchange
            try { saveUserMemory(input) } catch (_: Exception) { }

            // Speak the response
            val speechText = response.replace(Regex("""\[\w+\]\s*"""), "").replace(Regex("""[*#_]"""), "").take(500)
            ttsEngine.speak(speechText, "response_${System.currentTimeMillis()}")
        }
    }

    /**
     * Detect greeting patterns and return a time-based, personality-rich response.
     * Returns null if input is not a greeting.
     */
    private suspend fun detectGreeting(input: String): String? {
        val lower = input.lowercase().trim()
        val greetingPatterns = listOf(
            """^(hey|hi|hello|yo|sup|what'?s up|hola)\s*(mahi)?$""".toRegex(RegexOption.IGNORE_CASE),
            """^(kaise\s+ho|kya\s+hal|kya\s+haal|namaste|namaskar)""".toRegex(RegexOption.IGNORE_CASE),
            """^(good\s+(morning|afternoon|evening|night))\s*(mahi)?$""".toRegex(RegexOption.IGNORE_CASE),
            """^(hey\s+mahi|hello\s+mahi|hi\s+mahi)""".toRegex(RegexOption.IGNORE_CASE)
        )

        val isGreeting = greetingPatterns.any { it.containsMatchIn(lower) }
        if (!isGreeting) return null

        // Also check it's short enough to be a greeting (not "hello how is the weather")
        if (lower.split("\\s+".toRegex()).size > 5) return null

        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val isHindi = _currentLanguage.value == "hi"

        // Try to use user's name from memories
        val userName = try {
            val memories = readUserMemories()
            val nameMatch = Regex("""name[:\s]+(\w+)""", RegexOption.IGNORE_CASE).find(memories)
            nameMatch?.groupValues?.get(1)
        } catch (_: Exception) { null }

        val nameGreet = userName ?: "Boss"

        return when {
            hour in 5..11 -> if (isHindi) "Good morning, $nameGreet! Aaj ka din shubh ho. Batao kya madad karun?"
                             else "Good morning, $nameGreet! Ready to take on the day. What can I do for you?"
            hour in 12..16 -> if (isHindi) "Good afternoon, $nameGreet! Din kaisa ja raha hai? Kuch help chahiye?"
                              else "Good afternoon, $nameGreet! How's the day going? Need anything?"
            hour in 17..20 -> if (isHindi) "Good evening, $nameGreet! Kya haal hai? Batao kya chahiye!"
                              else "Good evening, $nameGreet! Winding down or still grinding? I'm here either way."
            else -> if (isHindi) "Hey $nameGreet! Itni raat jaag rahe ho? Kya help chahiye?"
                    else "Hey $nameGreet! Burning the midnight oil? What do you need?"
        }
    }

    /**
     * WhatsApp confirmation state machine.
     * Step 0: idle (normal flow)
     * Step 1: "I found [Name]. Should I open WhatsApp for them?"
     * Step 2: "What message should I send?"
     * Step 3: "Ready to send '[msg]' to [name]. Confirm?"
     */
    private fun handleWhatsAppConfirmation(input: String): String {
        val lower = input.lowercase().trim()
        val isYes = lower.matches(Regex("""(?i)^(yes|yeah|yep|haan|ha|ok|okay|sure|confirm|send|bhejo|karo|thik|correct|right)$"""))
        val isNo = lower.matches(Regex("""(?i)^(no|nahi|nope|cancel|stop|mat)"""))

        return when (_whatsappStep) {
            1 -> {
                // Awaiting confirmation to proceed with WhatsApp
                if (isYes) {
                    if (_whatsappPendingMessage.isNullOrBlank()) {
                        _whatsappStep = 2
                        "What message should I send to ${_whatsappPendingContact.value}?"
                    } else {
                        _whatsappStep = 3
                        "Ready to send '${_whatsappPendingMessage}' to ${_whatsappPendingContact.value}. Confirm?"
                    }
                } else if (isNo) {
                    resetWhatsAppState()
                    "Okay, cancelled. No message will be sent."
                } else {
                    "Please say yes to confirm or no to cancel."
                }
            }
            2 -> {
                // Awaiting message content
                _whatsappPendingMessage = input
                _whatsappStep = 3
                "Ready to send '$input' to ${_whatsappPendingContact.value}. Confirm?"
            }
            3 -> {
                // Awaiting final send confirmation
                if (isYes) {
                    val contact = _whatsappPendingContact.value ?: ""
                    val message = _whatsappPendingMessage ?: ""
                    val number = _whatsappPendingNumber
                    resetWhatsAppState()
                    // Actually send the WhatsApp message now
                    executeWhatsAppSend(contact, number, message)
                } else if (isNo) {
                    resetWhatsAppState()
                    "Okay, cancelled. No message will be sent."
                } else {
                    "Please say yes to send or no to cancel."
                }
            }
            else -> {
                resetWhatsAppState()
                "Something went wrong. Please try again."
            }
        }
    }

    private fun resetWhatsAppState() {
        _whatsappStep = 0
        _whatsappPendingContact.value = null
        _whatsappPendingMessage = null
        _whatsappPendingNumber = null
    }

    /**
     * Actually execute the WhatsApp send after confirmation.
     */
    private fun executeWhatsAppSend(contact: String, phoneNumber: String?, message: String?): String {
        return try {
            if (phoneNumber != null) {
                val waNumber = phoneNumber.replace(Regex("""[^\d]"""), "")
                if (message != null && message.isNotBlank()) {
                    val waIntent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("https://wa.me/$waNumber?text=${URLEncoder.encode(message, "UTF-8")}")
                        setPackage("com.whatsapp")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    appContext.startActivity(waIntent)
                    "Opening WhatsApp chat with $contact. Message ready to send!"
                } else {
                    val waIntent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("https://wa.me/$waNumber")
                        setPackage("com.whatsapp")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    appContext.startActivity(waIntent)
                    "Opening WhatsApp chat with $contact."
                }
            } else {
                if (message != null && message.isNotBlank()) {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, message)
                        setPackage("com.whatsapp")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    appContext.startActivity(intent)
                    "Opening WhatsApp with your message. Select $contact from your contacts."
                } else {
                    val launchIntent = appContext.packageManager.getLaunchIntentForPackage("com.whatsapp")
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        appContext.startActivity(launchIntent)
                        "Opening WhatsApp."
                    } else {
                        "WhatsApp is not installed on your device."
                    }
                }
            }
        } catch (e: Exception) {
            "Couldn't open WhatsApp. ${e.message}"
        }
    }

    private suspend fun handleIntent(intent: IntentClassifier.IntentResult, originalInput: String): String {
        return when (intent.type) {
            IntentClassifier.IntentType.DEVICE_CONTROL -> handleDeviceControl(intent)
            IntentClassifier.IntentType.WEATHER -> fetchWeather(intent.params["city"] ?: "")
            IntentClassifier.IntentType.NEWS -> fetchNews(intent.params["topic"] ?: intent.params["category"] ?: "", intent.params["count"] ?: "5")
            IntentClassifier.IntentType.YOUTUBE -> launchYouTube(intent.params["query"] ?: "")
            IntentClassifier.IntentType.CALL -> launchCall(intent.params["contact"] ?: "", intent.params["sim"] ?: "")
            IntentClassifier.IntentType.SMS -> launchSms(intent.params["contact"] ?: "", intent.params["message"])
            IntentClassifier.IntentType.SMS_READ -> readSms()
            IntentClassifier.IntentType.WHATSAPP -> handleWhatsApp(intent.params["contact"] ?: "", intent.params["message"])
            IntentClassifier.IntentType.ALARM -> launchAlarm(intent.params["time"])
            IntentClassifier.IntentType.REMINDER -> setReminder(intent.params["task"] ?: "", intent.params["time"] ?: "")
            IntentClassifier.IntentType.ROUTINE -> executeRoutine(intent.action)
            IntentClassifier.IntentType.NOTIFICATION -> readNotifications()
            IntentClassifier.IntentType.CALENDAR -> {
                if (intent.action.contains("create") || intent.action.contains("add") || intent.action.contains("event"))
                    createCalendarEvent(intent.params)
                else
                    launchCalendar()
            }
            IntentClassifier.IntentType.EMERGENCY_SOS -> handleEmergencySos()
            IntentClassifier.IntentType.EXPENSE_TRACK -> handleExpense(intent.action, intent.params)
            IntentClassifier.IntentType.APP_LAUNCH -> launchApp(intent.params["app"] ?: "")
            IntentClassifier.IntentType.WEB_SEARCH -> launchWebSearch(intent.params["query"] ?: originalInput)
            IntentClassifier.IntentType.MEDIA_CONTROL -> handleMediaControl(intent.params["action"] ?: "play")
            IntentClassifier.IntentType.LOCATION -> handleLocation()
            IntentClassifier.IntentType.BATTERY -> getBatteryStatus()
            IntentClassifier.IntentType.CALL_LOG -> getCallLog(intent.params["contact"] ?: "")
            IntentClassifier.IntentType.TIME_DATE -> getTimeDate()
            IntentClassifier.IntentType.FIND_PHONE -> findPhone()
            IntentClassifier.IntentType.NOTE_SAVE -> saveNote(intent.params["note"] ?: originalInput)
            IntentClassifier.IntentType.NOTE_READ -> readNotes()
            IntentClassifier.IntentType.CONTACT_SEARCH -> searchContact(intent.params["contact"] ?: "")
            IntentClassifier.IntentType.TIMER -> setTimer(intent.params["duration"] ?: "")
            IntentClassifier.IntentType.TRANSLATE -> handleTranslation(intent.params["text"] ?: originalInput, intent.params["target_lang"] ?: "")
            IntentClassifier.IntentType.CALCULATE -> handleCalculation(intent.params["expression"] ?: originalInput)
            IntentClassifier.IntentType.CONTINUOUS_MODE -> toggleContinuousMode(intent.action)
            IntentClassifier.IntentType.CAMERA -> openCamera()
            IntentClassifier.IntentType.FILE_OPEN -> openFileManager()
            else -> fetchAiResponse(originalInput)
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // YOUTUBE — Smart Search + Play
    // ══════════════════════════════════════════════════════════════════════

    private fun launchYouTube(query: String): String {
        return try {
            if (query.isNotBlank()) {
                // Try to play directly in YouTube app
                try {
                    val searchIntent = Intent(Intent.ACTION_SEARCH).apply {
                        component = ComponentName("com.google.android.youtube", "com.google.android.youtube.SearchActivity")
                        putExtra("query", query)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    appContext.startActivity(searchIntent)
                    return "Playing $query on YouTube."
                } catch (_: ActivityNotFoundException) { }

                // Fallback: open YouTube with search results
                try {
                    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${URLEncoder.encode(query, "UTF-8")}")).apply {
                        setPackage("com.google.android.youtube")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    appContext.startActivity(webIntent)
                    "Playing $query on YouTube."
                } catch (_: Exception) {
                    // Browser fallback
                    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${URLEncoder.encode(query, "UTF-8")}")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    appContext.startActivity(webIntent)
                    "Searching YouTube for $query."
                }
            } else {
                val launchIntent = appContext.packageManager.getLaunchIntentForPackage("com.google.android.youtube")
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    appContext.startActivity(launchIntent)
                    "Opening YouTube."
                } else {
                    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    appContext.startActivity(webIntent)
                    "Opening YouTube in browser."
                }
            }
        } catch (e: Exception) {
            "Couldn't open YouTube. ${e.message}"
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // WHATSAPP — FIXED: Uses ACTION_SEND + setPackage for reliability
    // ══════════════════════════════════════════════════════════════════════

    private fun handleWhatsApp(contact: String, message: String?): String {
        if (contact.isBlank() && message.isNullOrBlank()) {
            // Just open WhatsApp
            return try {
                val launchIntent = appContext.packageManager.getLaunchIntentForPackage("com.whatsapp")
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    appContext.startActivity(launchIntent)
                    "Opening WhatsApp."
                } else {
                    "WhatsApp is not installed on your device."
                }
            } catch (e: Exception) {
                "Couldn't open WhatsApp. ${e.message}"
            }
        }

        // SAFETY: Contact must be verified before sending. Start confirmation flow.
        return try {
            val phoneNumber = if (contact.isNotBlank() && contact != "unknown") lookupContactPhoneNumber(contact) else null

            if (phoneNumber != null || (contact.isNotBlank() && contact != "unknown")) {
                // Found a contact (or at least have a name) — enter confirmation flow
                _whatsappPendingContact.value = contact
                _whatsappPendingMessage = message
                _whatsappPendingNumber = phoneNumber
                _whatsappStep = 1

                if (message != null && message.isNotBlank()) {
                    "I found $contact. Should I send a message to them on WhatsApp?"
                } else {
                    "I found $contact. Should I open WhatsApp chat with them?"
                }
            } else {
                // No contact found at all — just open WhatsApp
                val launchIntent = appContext.packageManager.getLaunchIntentForPackage("com.whatsapp")
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    appContext.startActivity(launchIntent)
                    "Opening WhatsApp. I couldn't find $contact in your contacts."
                } else {
                    "WhatsApp is not installed on your device."
                }
            }
        } catch (e: Exception) {
            "Couldn't process WhatsApp request. ${e.message}"
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // CALL — with SIM Selection + Fallback
    // ══════════════════════════════════════════════════════════════════════

    private fun launchCall(contact: String, simSlot: String): String {
        if (contact.isBlank() || contact == "unknown") return "Who would you like me to call? Say a name or number."

        return try {
            // First try to resolve contact name to phone number
            val phoneNumber = lookupContactPhoneNumber(contact)

            if (phoneNumber != null) {
                // We have the phone number — make the call
                val uri = Uri.parse("tel:$phoneNumber")
                val callIntent = Intent(Intent.ACTION_CALL).apply {
                    data = uri
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    // SIM selection support
                    if (simSlot.isNotBlank()) {
                        val slotIndex = simSlot.toIntOrNull()?.minus(1) ?: 0
                        putExtra("com.android.phone.extra.slot", slotIndex)
                        putExtra("slotId", slotIndex)
                        putExtra("simId", slotIndex)
                    }
                }
                appContext.startActivity(callIntent)
                val simInfo = if (simSlot.isNotBlank()) " from SIM $simSlot" else ""
                "Calling $contact$simInfo."
            } else {
                // Check if the input is already a phone number
                val isPhoneNumber = contact.matches(Regex("^[+]?[0-9\\s\\-()]{7,15}$"))
                if (isPhoneNumber) {
                    val uri = Uri.parse("tel:${contact.replace(Regex("[^+\\d]"), "")}")
                    val callIntent = Intent(Intent.ACTION_CALL).apply {
                        data = uri
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    appContext.startActivity(callIntent)
                    "Calling $contact."
                } else {
                    // Not a number and not in contacts — open dialer
                    val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                        data = Uri.parse("tel:${Uri.encode(contact)}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    appContext.startActivity(dialIntent)
                    "I couldn't find $contact in your contacts. Opening dialer."
                }
            }
        } catch (e: SecurityException) {
            // No CALL_PHONE permission — fall back to dialer
            try {
                val number = lookupContactPhoneNumber(contact) ?: contact
                val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:${Uri.encode(number)}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(dialIntent)
                "Opening dialer for $contact. Grant phone permission for direct calling."
            } catch (e2: Exception) {
                "I need phone permission to make calls. Please grant it in Settings."
            }
        } catch (e: Exception) {
            // Last resort — just open the dialer
            try {
                val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:${Uri.encode(contact)}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(dialIntent)
                "Opening dialer for $contact."
            } catch (e2: Exception) {
                "Couldn't call $contact. ${e.message}"
            }
        }
    }

    private suspend fun fetchWeather(city: String): String {
        _weatherState.value = _weatherState.value.copy(isLoading = true)
        val cityName = city.ifBlank { settingsManager.getDefaultCity() }

        return try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                // Use Open-Meteo Geocoding API instead of Android Geocoder (works on ALL devices!)
                val geocodingUrl = "https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(cityName, "UTF-8")}&count=1&language=en"
                val geocodingClient = okhttp3.OkHttpClient.Builder().connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS).build()
                val geocodingRequest = okhttp3.Request.Builder().url(geocodingUrl).build()
                val geocodingResponse = geocodingClient.newCall(geocodingRequest).execute()
                val geocodingBody = geocodingResponse.body?.string()

                if (geocodingBody.isNullOrBlank()) {
                    _weatherState.value = _weatherState.value.copy(isLoading = false)
                    return@withContext "I couldn't find $cityName. Try a different city name."
                }

                val gson = com.google.gson.Gson()
                val geoResult = gson.fromJson(geocodingBody, GeocodingResult::class.java)
                val geoData = geoResult.results?.firstOrNull()

                if (geoData == null) {
                    _weatherState.value = _weatherState.value.copy(isLoading = false)
                    return@withContext "I couldn't find $cityName. Try a different city name."
                }

                val lat = geoData.latitude
                val lon = geoData.longitude
                val resolvedCity = geoData.name ?: cityName

                // Call Open-Meteo API (completely free!)
                val openMeteoUrl = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m&timezone=auto"
                val weatherRequest = okhttp3.Request.Builder().url(openMeteoUrl).build()
                val weatherResponse = geocodingClient.newCall(weatherRequest).execute()
                val body = weatherResponse.body?.string()

                if (body != null) {
                    val weatherData = gson.fromJson(body, OpenMeteoResponse::class.java)
                    val current = weatherData.current

                    if (current != null) {
                        val temp = current.temperature_2m?.toInt() ?: 0
                        val feelsLike = current.apparent_temperature?.toInt() ?: temp
                        val humidity = current.relative_humidity_2m?.toInt() ?: 0
                        val windSpeed = current.wind_speed_10m ?: 0.0
                        val desc = weatherCodeToText(current.weather_code ?: 0)

                        _weatherState.value = WeatherUiState(
                            city = resolvedCity,
                            temperature = temp.toDouble(),
                            description = desc,
                            humidity = humidity,
                            windSpeed = windSpeed,
                            feelsLike = feelsLike.toDouble(),
                            isLoading = false
                        )
                        navigateTo("weather")
                        "$resolvedCity ka mausam: $temp degree, $desc. Feels like $feelsLike degree. Humidity $humidity%. Wind speed ${String.format("%.1f", windSpeed)} km/h."
                    } else {
                        fallbackWeather(cityName)
                    }
                } else {
                    fallbackWeather(cityName)
                }
            }
        } catch (e: Exception) {
            fallbackWeather(cityName)
        }
    }

    private suspend fun fallbackWeather(city: String): String {
        return try {
            val weatherApiKey = settingsManager.getWeatherApiKey()
            if (weatherApiKey.isBlank()) {
                _weatherState.value = _weatherState.value.copy(isLoading = false)
                return "I couldn't fetch weather data. Please check your internet connection."
            }
            val weather = com.mahi.assistant.api.WeatherClient.instance.getCurrentWeather(city, weatherApiKey, "metric")
            _weatherState.value = WeatherUiState(
                city = weather.name ?: city,
                temperature = weather.main?.temp ?: 0.0,
                description = weather.weather?.firstOrNull()?.description ?: "",
                humidity = weather.main?.humidity ?: 0,
                windSpeed = weather.wind?.speed ?: 0.0,
                feelsLike = weather.main?.feelsLike ?: 0.0,
                isLoading = false
            )
            navigateTo("weather")
            "Weather in ${weather.name ?: city}: ${(weather.main?.temp ?: 0.0).toInt()} degrees, ${weather.weather?.firstOrNull()?.description ?: "clear"}. Feels like ${(weather.main?.feelsLike ?: 0.0).toInt()} degrees."
        } catch (e: Exception) {
            _weatherState.value = _weatherState.value.copy(isLoading = false, error = e.message)
            "Couldn't fetch weather for $city. Please check your internet connection."
        }
    }

    private fun weatherCodeToText(code: Int): String {
        return when (code) {
            0 -> "clear sky"
            1, 2, 3 -> "partly cloudy"
            45, 48 -> "foggy"
            51, 53, 55 -> "drizzle"
            56, 57 -> "freezing drizzle"
            61, 63, 65 -> "rainy"
            66, 67 -> "freezing rain"
            71, 73, 75 -> "snowy"
            77 -> "snow grains"
            80, 81, 82 -> "rain showers"
            85, 86 -> "snow showers"
            95 -> "thunderstorm"
            96, 99 -> "thunderstorm with hail"
            else -> "variable weather"
        }
    }

    // Open-Meteo response model
    private data class OpenMeteoResponse(val current: OpenMeteoCurrent? = null)
    private data class OpenMeteoCurrent(
        val temperature_2m: Double? = null,
        val relative_humidity_2m: Double? = null,
        val apparent_temperature: Double? = null,
        val weather_code: Int? = null,
        val wind_speed_10m: Double? = null
    )

    // Open-Meteo Geocoding response model
    private data class GeocodingResult(val results: List<GeocodingItem>? = null)
    private data class GeocodingItem(
        val name: String? = null,
        val latitude: Double = 0.0,
        val longitude: Double = 0.0,
        val country: String? = null
    )

    // ══════════════════════════════════════════════════════════════════════
    // NEWS — FIXED: RSS fallback (FREE, no API key!) + GNews API
    // ══════════════════════════════════════════════════════════════════════

    private suspend fun fetchNews(topic: String, count: String): String {
        _newsState.value = _newsState.value.copy(isLoading = true)
        val maxArticles = count.toIntOrNull()?.coerceIn(1, 10) ?: 5

        // Strategy: RSS first (FREE, no API key, reliable), then GNews API as fallback
        val rssResult = fetchNewsRss(topic, maxArticles)
        if (rssResult != "No news found for ${topic.ifBlank { "top headlines" }}." &&
            rssResult != "Couldn't fetch news. Please check your internet connection.") {
            return rssResult
        }

        // RSS failed — try GNews API as fallback
        val apiResult = fetchNewsFromApi(topic, maxArticles)
        if (apiResult != null) return apiResult

        // Final fallback: web search
        return try {
            val searchResult = com.mahi.assistant.api.WebSearchService.search("latest news $topic")
            if (searchResult.isNotBlank() && !searchResult.contains("couldn't find")) {
                _newsState.value = _newsState.value.copy(isLoading = false)
                searchResult
            } else {
                "I couldn't fetch news right now. Please check your internet connection."
            }
        } catch (e: Exception) {
            "I couldn't fetch news right now. Please try again later."
        }
    }

    /**
     * Try GNews API. Returns null if it fails (so we can fallback to RSS).
     */
    private suspend fun fetchNewsFromApi(topic: String, maxArticles: Int): String? {
        return try {
            val newsApiKey = settingsManager.getNewsApiKey()
            if (newsApiKey.isBlank()) return null // Fall through to RSS

            val news = if (topic.isNotBlank()) {
                NewsClient.instance.searchNews(query = topic, lang = "en", max = maxArticles, token = newsApiKey)
            } else {
                NewsClient.instance.getTopHeadlines(category = "general", lang = "en", token = newsApiKey)
            }

            _newsState.value = NewsUiState(articles = news.articles ?: emptyList(), isLoading = false, selectedCategory = topic)
            navigateTo("news")

            if (news.articles.isNullOrEmpty()) null // Fall through to RSS
            else {
                val topicLabel = if (topic.isNotBlank()) "about $topic" else ""
                "Top $topicLabel headlines: ${news.articles.take(maxArticles).mapIndexed { i, a -> "${i + 1}. ${a.title ?: "Untitled"}" }.joinToString(". ")}"
            }
        } catch (e: retrofit2.HttpException) {
            // API key expired or invalid — fall through to RSS
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * FREE RSS News Fallback using Google News RSS.
     * No API key needed! Unlimited requests!
     */
    private suspend fun fetchNewsRss(topic: String, maxArticles: Int): String {
        return try {
            val query = if (topic.isNotBlank()) URLEncoder.encode(topic, "UTF-8") else ""
            val rssUrl = if (topic.isNotBlank())
                "https://news.google.com/rss/search?q=$query&hl=en-IN&gl=IN&ceid=IN:en"
            else
                "https://news.google.com/rss?hl=en-IN&gl=IN&ceid=IN:en"

            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
            val request = okhttp3.Request.Builder().url(rssUrl).build()
            val response = client.newCall(request).execute()
            val xml = response.body?.string() ?: return "Couldn't fetch news. Please check your internet connection."

            // Parse RSS XML — extract titles from <item><title> tags
            val titles = Regex("<item>.*?</item>", RegexOption.DOT_MATCHES_ALL)
                .findAll(xml)
                .take(maxArticles)
                .mapNotNull { itemMatch ->
                    Regex("<title>(.*?)</title>").find(itemMatch.value)?.groupValues?.get(1)
                }
                .toList()

            if (titles.isEmpty()) {
                _newsState.value = _newsState.value.copy(isLoading = false)
                return "No news found for ${topic.ifBlank { "top headlines" }}."
            }

            _newsState.value = _newsState.value.copy(isLoading = false, selectedCategory = topic)
            navigateTo("news")

            val topicLabel = if (topic.isNotBlank()) "about $topic" else ""
            "Top $topicLabel headlines: ${titles.mapIndexed { i, t -> "${i + 1}. $t" }.joinToString(". ")}"
        } catch (e: Exception) {
            _newsState.value = _newsState.value.copy(isLoading = false, error = e.message)
            "Couldn't fetch news. Please check your internet connection."
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // MEDIA CONTROL — Play/Pause/Next/Previous
    // ══════════════════════════════════════════════════════════════════════

    private fun handleMediaControl(action: String): String {
        return try {
            when (action.lowercase()) {
                "play", "resume" -> {
                    val intent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                        putExtra(Intent.EXTRA_KEY_EVENT, android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PLAY))
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    appContext.sendBroadcast(intent)
                    "Playing music."
                }
                "pause", "stop" -> {
                    val intent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                        putExtra(Intent.EXTRA_KEY_EVENT, android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PAUSE))
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    appContext.sendBroadcast(intent)
                    "Music paused."
                }
                "next", "skip", "forward" -> {
                    val intent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                        putExtra(Intent.EXTRA_KEY_EVENT, android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_NEXT))
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    appContext.sendBroadcast(intent)
                    "Playing next song."
                }
                "previous", "prev", "back", "rewind" -> {
                    val intent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                        putExtra(Intent.EXTRA_KEY_EVENT, android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS))
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    appContext.sendBroadcast(intent)
                    "Playing previous song."
                }
                else -> "Unknown media action."
            }
        } catch (e: Exception) {
            "Couldn't control media. ${e.message}"
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // BATTERY STATUS
    // ══════════════════════════════════════════════════════════════════════

    private fun getBatteryStatus(): String {
        return try {
            val bm = appContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val isCharging = bm.isCharging

            val chargingStatus = if (isCharging) "and is charging" else "and is not charging"
            val healthAdvice = when {
                level <= 10 -> "Battery is very low! Please charge your phone immediately."
                level <= 20 -> "Battery is low. Consider charging soon."
                level <= 50 -> "Battery is moderate. You have enough charge for now."
                level >= 80 && isCharging -> "Battery is almost full! You can unplug the charger."
                else -> "Battery level is good."
            }
            "Battery is at $level% $chargingStatus. $healthAdvice"
        } catch (e: Exception) {
            "Couldn't check battery status. ${e.message}"
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // CALL LOG
    // ══════════════════════════════════════════════════════════════════════

    private fun getCallLog(contact: String): String {
        return try {
            val calls = mutableListOf<String>()
            val uri = CallLog.Calls.CONTENT_URI
            val projection = arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION)
            val selection = if (contact.isNotBlank()) "${CallLog.Calls.CACHED_NAME} LIKE ?" else null
            val selectionArgs = if (contact.isNotBlank()) arrayOf("%$contact%") else null

            appContext.contentResolver.query(uri, projection, selection, selectionArgs, "${CallLog.Calls.DATE} DESC")?.use { cursor ->
                var count = 0
                while (cursor.moveToNext() && count < 5) {
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)) ?: "Unknown"
                    val type = when (cursor.getInt(cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE))) {
                        CallLog.Calls.INCOMING_TYPE -> "Incoming"
                        CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
                        CallLog.Calls.MISSED_TYPE -> "Missed"
                        else -> "Unknown"
                    }
                    val duration = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)) ?: "0"
                    val date = cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls.DATE))
                    val timeAgo = DateUtils.getRelativeTimeSpanString(date, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
                    calls.add("$type call ${if (contact.isNotBlank()) "" else "from $name "}$timeAgo, duration ${duration}s")
                    count++
                }
            }

            if (calls.isEmpty()) {
                if (contact.isNotBlank()) "No calls found with $contact."
                else "No recent calls found."
            } else {
                val prefix = if (contact.isNotBlank()) "Recent calls with $contact: " else "Recent calls: "
                prefix + calls.joinToString(". ")
            }
        } catch (e: SecurityException) {
            "I need call log permission to read your call history. Please grant it in Settings."
        } catch (e: Exception) {
            "Couldn't read call log. ${e.message}"
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // TIME & DATE
    // ══════════════════════════════════════════════════════════════════════

    private fun getTimeDate(): String {
        val now = Date()
        val time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(now)
        val date = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault()).format(now)
        return "It's $time on $date."
    }

    // ══════════════════════════════════════════════════════════════════════
    // FIND PHONE
    // ══════════════════════════════════════════════════════════════════════

    private fun findPhone(): String {
        return try {
            val am = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVolume = am.getStreamMaxVolume(AudioManager.STREAM_RING)
            am.setStreamVolume(AudioManager.STREAM_RING, maxVolume, 0)

            val ringtoneUri = Settings.System.DEFAULT_RINGTONE_URI
            val ringtone = android.media.RingtoneManager.getRingtone(appContext, ringtoneUri)
            ringtone.play()
            "Ringing your phone at maximum volume! Find it!"
        } catch (e: Exception) {
            "I couldn't ring your phone. ${e.message}"
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // SMS READ — NEW: Read SMS inbox
    // ══════════════════════════════════════════════════════════════════════

    private fun readSms(): String {
        return try {
            val uri = android.net.Uri.parse("content://sms/inbox")
            val projection = arrayOf("_id", "address", "body", "date")
            val messages = mutableListOf<String>()

            appContext.contentResolver.query(uri, projection, null, null, "date DESC")?.use { cursor ->
                var count = 0
                while (cursor.moveToNext() && count < 5) {
                    val sender = cursor.getString(1) ?: "Unknown"
                    val body = cursor.getString(2) ?: ""
                    // Try to resolve sender name from contacts
                    val senderName = resolveContactName(sender) ?: sender
                    messages.add("From $senderName: $body")
                    count++
                }
            }

            if (messages.isEmpty()) "No messages found in your inbox."
            else "Recent messages: ${messages.joinToString(". ")}"
        } catch (e: SecurityException) {
            "I need SMS permission to read your messages. Please grant it in Settings."
        } catch (e: Exception) {
            "Couldn't read messages. ${e.message}"
        }
    }

    /**
     * Resolve a phone number to a contact name.
     */
    private fun resolveContactName(phoneNumber: String): String? {
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)

            appContext.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME))
                } else null
            }
        } catch (_: Exception) { null }
    }

    // ══════════════════════════════════════════════════════════════════════
    // NOTES — NEW: Save/Read notes using SharedPreferences
    // ══════════════════════════════════════════════════════════════════════

    private fun saveNote(note: String): String {
        if (note.isBlank()) return "What would you like me to remember?"
        val notes = settingsManager.getNotes().toMutableList()
        notes.add(note)
        settingsManager.saveNotes(notes)
        return "Note saved: $note. I'll remember this for you."
    }

    private fun readNotes(): String {
        val notes = settingsManager.getNotes()
        return if (notes.isEmpty()) "You haven't saved any notes yet. Tell me to remember something!"
        else "Your notes: ${notes.mapIndexed { i, n -> "${i + 1}. $n" }.joinToString(". ")}"
    }

    // ══════════════════════════════════════════════════════════════════════
    // CONTACT SEARCH — NEW: Find contact by name
    // ══════════════════════════════════════════════════════════════════════

    private fun searchContact(name: String): String {
        if (name.isBlank()) return "Which contact are you looking for?"

        return try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("%$name%")

            val results = mutableListOf<String>()
            appContext.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                while (cursor.moveToNext() && results.size < 3) {
                    val contactName = cursor.getString(0) ?: ""
                    val phone = cursor.getString(1) ?: ""
                    results.add("$contactName: $phone")
                }
            }

            // Also try email lookup
            try {
                val emailUri = ContactsContract.CommonDataKinds.Email.CONTENT_URI
                val emailProjection = arrayOf(
                    ContactsContract.CommonDataKinds.Email.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Email.DATA
                )
                val emailSelection = "${ContactsContract.CommonDataKinds.Email.DISPLAY_NAME} LIKE ?"

                appContext.contentResolver.query(emailUri, emailProjection, emailSelection, selectionArgs, null)?.use { cursor ->
                    while (cursor.moveToNext() && results.size < 5) {
                        val contactName = cursor.getString(0) ?: ""
                        val email = cursor.getString(1) ?: ""
                        val entry = "$contactName: $email (email)"
                        if (results.none { it.startsWith(contactName) && it.contains(email) }) {
                            results.add(entry)
                        }
                    }
                }
            } catch (_: Exception) { }

            if (results.isEmpty()) "No contact found for $name."
            else results.joinToString(". ")
        } catch (e: SecurityException) {
            "I need contacts permission to search. Please grant it in Settings."
        } catch (e: Exception) {
            "Couldn't search contacts. ${e.message}"
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // TIMER — NEW: Set timer using clock app
    // ══════════════════════════════════════════════════════════════════════

    private fun setTimer(duration: String): String {
        return try {
            val durationSeconds = parseDurationToSeconds(duration)

            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                if (durationSeconds > 0) {
                    putExtra(AlarmClock.EXTRA_LENGTH, durationSeconds)
                }
                putExtra(AlarmClock.EXTRA_MESSAGE, "MAHI Timer")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // Check if there's an app that can handle this
            if (intent.resolveActivity(appContext.packageManager) != null) {
                appContext.startActivity(intent)
                val durationText = if (durationSeconds > 0) formatDurationText(durationSeconds) else duration
                "Timer set for $durationText."
            } else {
                // Fallback: just open the clock app
                val clockIntent = appContext.packageManager.getLaunchIntentForPackage("com.android.deskclock")
                if (clockIntent != null) {
                    clockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    appContext.startActivity(clockIntent)
                    "Opening clock app. Please set the timer manually."
                } else {
                    "Couldn't find a clock app to set the timer."
                }
            }
        } catch (e: Exception) {
            "Couldn't set timer. ${e.message}"
        }
    }

    private fun parseDurationToSeconds(duration: String): Int {
        val lower = duration.lowercase().trim()

        // "X minutes" or "X min" or "X minute"
        val minutes = Regex("(\\d+)\\s*(?:min|minute|minutes)").find(lower)
        if (minutes != null) return (minutes.groupValues[1].toIntOrNull() ?: 0) * 60

        // "X hours" or "X hr"
        val hours = Regex("(\\d+)\\s*(?:hour|hours|hr)").find(lower)
        if (hours != null) return (hours.groupValues[1].toIntOrNull() ?: 0) * 3600

        // "X seconds" or "X sec"
        val seconds = Regex("(\\d+)\\s*(?:sec|second|seconds)").find(lower)
        if (seconds != null) return seconds.groupValues[1].toIntOrNull() ?: 0

        // Just a number — assume minutes
        val justNumber = Regex("^(\\d+)$").find(lower)
        if (justNumber != null) return (justNumber.groupValues[1].toIntOrNull() ?: 0) * 60

        return 0
    }

    private fun formatDurationText(seconds: Int): String {
        return when {
            seconds >= 3600 -> "${seconds / 3600} hour${if (seconds / 3600 > 1) "s" else ""}"
            seconds >= 60 -> "${seconds / 60} minute${if (seconds / 60 > 1) "s" else ""}"
            else -> "$seconds second${if (seconds > 1) "s" else ""}"
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // TRANSLATION — NEW: Use Gemini for translation
    // ══════════════════════════════════════════════════════════════════════

    private suspend fun handleTranslation(text: String, targetLang: String): String {
        if (text.isBlank()) return "What would you like me to translate?"
        return try {
            val langSpec = if (targetLang.isNotBlank()) " to $targetLang" else ""
            aiEngine.queryOnce("Translate the following$langSpec. Give ONLY the translation, nothing else: $text")
        } catch (e: Exception) {
            "Couldn't translate. ${e.message}"
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // CALCULATION — NEW: Use Gemini for math
    // ══════════════════════════════════════════════════════════════════════

    private suspend fun handleCalculation(expression: String): String {
        if (expression.isBlank()) return "What would you like me to calculate?"
        return try {
            // Try basic arithmetic first (instant, no API)
            val basicResult = tryBasicMath(expression)
            if (basicResult != null) return "The answer is $basicResult."

            // Fallback to Gemini for complex calculations
            aiEngine.queryOnce("Calculate this and give ONLY the answer: $expression")
        } catch (e: Exception) {
            "Couldn't calculate. ${e.message}"
        }
    }

    private fun tryBasicMath(expression: String): Double? {
        return try {
            // Simple regex-based calculator for basic operations
            val cleaned = expression.lowercase()
                .replace(Regex("[^0-9+\\-*/.() ]"), "")
                .trim()
            if (cleaned.isBlank()) return null

            // Evaluate simple expressions
            val addMatch = Regex("(\\d+(?:\\.\\d+)?)\\s*\\+\\s*(\\d+(?:\\.\\d+)?)").find(cleaned)
            val subMatch = Regex("(\\d+(?:\\.\\d+)?)\\s*-\\s*(\\d+(?:\\.\\d+)?)").find(cleaned)
            val mulMatch = Regex("(\\d+(?:\\.\\d+)?)\\s*\\*\\s*(\\d+(?:\\.\\d+)?)").find(cleaned)
            val divMatch = Regex("(\\d+(?:\\.\\d+)?)\\s*/\\s*(\\d+(?:\\.\\d+)?)").find(cleaned)

            when {
                addMatch != null -> addMatch.groupValues[1].toDouble() + addMatch.groupValues[2].toDouble()
                subMatch != null -> subMatch.groupValues[1].toDouble() - subMatch.groupValues[2].toDouble()
                mulMatch != null -> mulMatch.groupValues[1].toDouble() * mulMatch.groupValues[2].toDouble()
                divMatch != null -> {
                    val divisor = divMatch.groupValues[2].toDouble()
                    if (divisor == 0.0) return null
                    divMatch.groupValues[1].toDouble() / divisor
                }
                else -> null
            }
        } catch (_: Exception) { null }
    }

    // ══════════════════════════════════════════════════════════════════════
    // CONTINUOUS MODE — NEW: Toggle always-listening mode
    // ══════════════════════════════════════════════════════════════════════

    private fun toggleContinuousMode(action: String): String {
        val lowerAction = action.lowercase()
        val enable = when {
            lowerAction.contains("enable") || lowerAction.contains("on") || lowerAction.contains("start") -> true
            lowerAction.contains("disable") || lowerAction.contains("off") || lowerAction.contains("stop") -> false
            lowerAction == "toggle_continuous" -> !_continuousMode.value  // toggle
            else -> !_continuousMode.value  // default: toggle
        }
        _continuousMode.value = enable
        settingsManager.setContinuousMode(enable)
        _settingsState.value = _settingsState.value.copy(continuousMode = enable)

        return if (enable) {
            "Continuous mode enabled. I'll keep listening after each response. Say 'stop continuous mode' to disable."
        } else {
            "Continuous mode disabled. I'll wait for you to tap the mic button."
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // CAMERA — NEW: Open camera app or take photo
    // ══════════════════════════════════════════════════════════════════════

    private fun openCamera(): String {
        return try {
            val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(appContext.packageManager) != null) {
                appContext.startActivity(intent)
                "Opening camera."
            } else {
                // Fallback: launch camera app by package name
                val cameraIntent = appContext.packageManager.getLaunchIntentForPackage("com.android.camera")
                if (cameraIntent != null) {
                    cameraIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    appContext.startActivity(cameraIntent)
                    "Opening camera."
                } else {
                    "Couldn't find a camera app on your device."
                }
            }
        } catch (e: Exception) {
            "Couldn't open camera. ${e.message}"
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // FILE MANAGER — NEW: Open files/downloads
    // ══════════════════════════════════════════════════════════════════════

    private fun openFileManager(): String {
        return try {
            // Try opening the default file manager
            val filesIntent = appContext.packageManager.getLaunchIntentForPackage("com.google.android.apps.nbu.files")
            if (filesIntent != null) {
                filesIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                appContext.startActivity(filesIntent)
                return "Opening file manager."
            }

            // Try generic files intent
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = android.provider.MediaStore.Files.getContentUri("external")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(intent)
                "Opening files."
            } catch (e: Exception) {
                // Try opening downloads folder
                try {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("content://downloads/my_downloads")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    appContext.startActivity(intent)
                    "Opening downloads."
                } catch (e2: Exception) {
                    "Couldn't open file manager. ${e2.message}"
                }
            }
        } catch (e: Exception) {
            "Couldn't open file manager. ${e.message}"
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // LOCATION
    // ══════════════════════════════════════════════════════════════════════

    private fun handleLocation(): String {
        return try {
            val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

            var locationText = "I couldn't determine your location. Please enable location services."

            for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                try {
                    val loc = lm.getLastKnownLocation(provider)
                    if (loc != null) {
                        // Try Open-Meteo reverse geocoding (works on ALL devices, no Geocoder needed)
                        try {
                            val reverseUrl = "https://geocoding-api.open-meteo.com/v1/search?name=${String.format("%.2f", loc.latitude)},${String.format("%.2f", loc.longitude)}&count=1"
                            val client = okhttp3.OkHttpClient.Builder().connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS).build()
                            val request = okhttp3.Request.Builder().url(reverseUrl).build()
                            val response = client.newCall(request).execute()
                            if (response.isSuccessful) {
                                locationText = "Your location: ${String.format("%.4f", loc.latitude)}, ${String.format("%.4f", loc.longitude)}."
                            }
                        } catch (_: Exception) { }

                        // Fallback: just show coordinates
                        if (locationText == "I couldn't determine your location. Please enable location services.") {
                            locationText = "Your location: ${String.format("%.4f", loc.latitude)}, ${String.format("%.4f", loc.longitude)}."
                        }
                        break
                    }
                } catch (_: Exception) { continue }
            }
            locationText
        } catch (e: SecurityException) {
            "I need location permission to know where you are. Please grant it in Settings."
        } catch (e: Exception) {
            "Couldn't determine your location. ${e.message}"
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // SMART REMINDERS
    // ══════════════════════════════════════════════════════════════════════

    private fun setReminder(task: String, time: String): String {
        if (task.isBlank()) return "What should I remind you about?"

        return try {
            val intent = Intent(appContext, ReminderReceiver::class.java).apply {
                putExtra("task", task)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                appContext, (System.currentTimeMillis() % 100000).toInt(),
                intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            if (time.isNotBlank()) {
                val delayMs = parseTimeToMillis(time)
                if (delayMs > 0) {
                    alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        SystemClock.elapsedRealtime() + delayMs, pendingIntent)
                    val timeDesc = formatDelay(delayMs)
                    "Reminder set: $task in $timeDesc."
                } else {
                    launchAlarm(time)
                    return "I set an alarm for $time. For reminders, say the time like 'in 5 minutes' or 'at 3pm'."
                }
            } else {
                alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + 60000, pendingIntent)
                "Reminder set: $task in 1 minute."
            }
        } catch (e: Exception) {
            "Couldn't set reminder. ${e.message}"
        }
    }

    private fun parseTimeToMillis(time: String): Long {
        val lower = time.lowercase().trim()

        val inMinutes = Regex("(?i)(\\d+)\\s*min").find(lower)
        if (inMinutes != null) return inMinutes.groupValues[1].toLongOrNull()?.times(60000L) ?: 0L

        val inHours = Regex("(?i)(\\d+)\\s*hour").find(lower)
        if (inHours != null) return inHours.groupValues[1].toLongOrNull()?.times(3600000L) ?: 0L

        val inSeconds = Regex("(?i)(\\d+)\\s*sec").find(lower)
        if (inSeconds != null) return inSeconds.groupValues[1].toLongOrNull()?.times(1000L) ?: 0L

        val atTime = Regex("(?i)(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?").find(lower)
        if (atTime != null) {
            var hour = atTime.groupValues[1].toIntOrNull() ?: return 0L
            val minute = atTime.groupValues[2].toIntOrNull() ?: 0
            val ampm = atTime.groupValues[3].lowercase()

            if (ampm == "pm" && hour < 12) hour += 12
            if (ampm == "am" && hour == 12) hour = 0

            val calendar = java.util.Calendar.getInstance()
            calendar.set(java.util.Calendar.HOUR_OF_DAY, hour)
            calendar.set(java.util.Calendar.MINUTE, minute)
            calendar.set(java.util.Calendar.SECOND, 0)

            if (calendar.timeInMillis <= System.currentTimeMillis()) {
                calendar.add(java.util.Calendar.DAY_OF_MONTH, 1)
            }

            return calendar.timeInMillis - System.currentTimeMillis()
        }

        return 0L
    }

    private fun formatDelay(ms: Long): String {
        val minutes = ms / 60000
        val hours = minutes / 60
        return when {
            hours > 0 -> "${hours}h ${minutes % 60}m"
            minutes > 0 -> "${minutes}m"
            else -> "${ms / 1000}s"
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // APP LAUNCH
    // ══════════════════════════════════════════════════════════════════════

    private fun launchApp(appName: String): String {
        if (appName.isBlank()) return "Which app would you like me to open?"
        val packageName = appPackageMap[appName.lowercase().trim()]

        return try {
            if (packageName != null) {
                val launchIntent = appContext.packageManager.getLaunchIntentForPackage(packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    appContext.startActivity(launchIntent)
                    "Opening ${appName.replaceFirstChar { it.uppercase() }}."
                } else {
                    openPlayStore(packageName)
                    "${appName.replaceFirstChar { it.uppercase() }} is not installed. Opening Play Store."
                }
            } else {
                val foundPackage = findPackageByAppName(appName)
                if (foundPackage != null) {
                    val launchIntent = appContext.packageManager.getLaunchIntentForPackage(foundPackage)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        appContext.startActivity(launchIntent)
                        "Opening ${appName.replaceFirstChar { it.uppercase() }}."
                    } else {
                        openPlayStore(foundPackage)
                        "Opening ${appName.replaceFirstChar { it.uppercase() }}."
                    }
                } else {
                    openPlayStoreSearch(appName)
                    "Couldn't find ${appName.replaceFirstChar { it.uppercase() }} on your device. Searching Play Store."
                }
            }
        } catch (e: Exception) {
            "Couldn't open ${appName.replaceFirstChar { it.uppercase() }}. ${e.message}"
        }
    }

    private fun findPackageByAppName(appName: String): String? {
        return try {
            val query = appName.lowercase().trim()
            appContext.packageManager.getInstalledApplications(0)
                .firstOrNull { appInfo ->
                    val label = appContext.packageManager.getApplicationLabel(appInfo).toString().lowercase()
                    label.contains(query) || query.contains(label)
                }?.packageName
        } catch (_: Exception) { null }
    }

    private fun openPlayStore(packageName: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            appContext.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            appContext.startActivity(intent)
        }
    }

    private fun openPlayStoreSearch(query: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=${Uri.encode(query)}")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            appContext.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=${Uri.encode(query)}")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            appContext.startActivity(intent)
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // SMS / ALARM / CALENDAR / WEB SEARCH / CONTACTS
    // ══════════════════════════════════════════════════════════════════════

    private fun launchSms(contact: String, message: String?): String {
        if (contact.isBlank()) return "Who would you like to message?"
        return try {
            // Try to resolve contact to phone number
            val phoneNumber = lookupContactPhoneNumber(contact)
            val number = phoneNumber ?: contact

            val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:${Uri.encode(number)}")
                if (message != null) putExtra("sms_body", message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(smsIntent)
            "Opening message for $contact."
        } catch (e: Exception) { "Couldn't open messages for $contact. ${e.message}" }
    }

    private fun launchAlarm(time: String?): String {
        return try {
            if (time != null) {
                val alarmIntent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_MESSAGE, "MAHI Alarm")
                    val match = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?", RegexOption.IGNORE_CASE).find(time)
                    if (match != null) {
                        var hour = match.groupValues[1].toIntOrNull() ?: 7
                        val minute = match.groupValues[2].toIntOrNull() ?: 0
                        val ampm = match.groupValues[3].lowercase()
                        if (ampm == "pm" && hour < 12) hour += 12
                        if (ampm == "am" && hour == 12) hour = 0
                        putExtra(AlarmClock.EXTRA_HOUR, hour)
                        putExtra(AlarmClock.EXTRA_MINUTES, minute)
                    }
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(alarmIntent)
                "Setting alarm for $time."
            } else {
                val alarmIntent = Intent(AlarmClock.ACTION_SET_ALARM).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                appContext.startActivity(alarmIntent)
                "Opening alarm settings."
            }
        } catch (e: Exception) { "Couldn't set alarm. ${e.message}" }
    }

    private fun launchCalendar(): String {
        return try {
            val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_APP_CALENDAR); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            appContext.startActivity(intent)
            "Opening calendar."
        } catch (e: Exception) { "Couldn't open calendar. ${e.message}" }
    }

    // ══════════════════════════════════════════════════════════════════════
    // CALENDAR EVENT — Create a new calendar event
    // ══════════════════════════════════════════════════════════════════════

    private fun createCalendarEvent(params: Map<String, String>): String {
        return try {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, System.currentTimeMillis())
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, System.currentTimeMillis() + 3600000) // 1 hour default
                params["title"]?.let { putExtra(CalendarContract.Events.TITLE, it) }
                params["description"]?.let { putExtra(CalendarContract.Events.DESCRIPTION, it) }
                params["location"]?.let { putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
            "Creating calendar event."
        } catch (e: Exception) {
            "Couldn't create calendar event. ${e.message}"
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // EMERGENCY SOS — Send SMS with location + Call emergency
    // ══════════════════════════════════════════════════════════════════════

    private fun handleEmergencySos(): String {
        val results = mutableListOf<String>()

        // Try to get current location
        var locationText = "Location unavailable"
        try {
            val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val lastKnown = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (lastKnown != null) {
                locationText = "https://maps.google.com/?q=${lastKnown.latitude},${lastKnown.longitude}"
            }
        } catch (_: Exception) { }

        // Try to send SMS to first contact
        try {
            val firstContactNumber = getFirstContactNumber()
            if (firstContactNumber != null) {
                val smsManager = SmsManager.getDefault()
                smsManager.sendTextMessage(
                    firstContactNumber,
                    null,
                    "EMERGENCY! I need help! My location: $locationText — Sent via MAHI Assistant",
                    null,
                    null
                )
                results.add("Emergency SMS sent with your location")
            } else {
                results.add("No contact found for emergency SMS")
            }
        } catch (e: SecurityException) {
            results.add("SMS permission needed for emergency")
        } catch (_: Exception) {
            results.add("Could not send emergency SMS")
        }

        // Try to call emergency number (112 in India)
        try {
            val emergencyIntent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:112")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(emergencyIntent)
            results.add("Calling emergency number 112")
        } catch (e: SecurityException) {
            // No CALL_PHONE permission — open dialer
            try {
                val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:112")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(dialIntent)
                results.add("Opening emergency dialer")
            } catch (_: Exception) {
                results.add("Could not open emergency dialer")
            }
        } catch (_: Exception) {
            results.add("Could not call emergency number")
        }

        return "Emergency SOS activated! ${results.joinToString(". ")}."
    }

    private fun getFirstContactNumber(): String? {
        return try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
            appContext.contentResolver.query(uri, projection, null, null, "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC")?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)
                } else null
            }
        } catch (_: Exception) { null }
    }

    // ══════════════════════════════════════════════════════════════════════
    // EXPENSE TRACKER — Add and read expenses
    // ══════════════════════════════════════════════════════════════════════

    private suspend fun handleExpense(action: String, params: Map<String, String>): String {
        return when {
            action.contains("read") || action.contains("total") || action.contains("show") || action.contains("dikhao") ->
                readExpenses(params)
            else -> addExpense(params)
        }
    }

    private suspend fun addExpense(params: Map<String, String>): String {
        return try {
            val amount = params["amount"]?.toDoubleOrNull() ?: 0.0
            if (amount <= 0) return "Please tell me the expense amount. For example: 'expense add 500 rupees food'"

            val category = params["category"] ?: "other"
            val description = params["description"] ?: params["note"] ?: ""

            val expense = ExpenseEntity(
                amount = amount,
                category = category,
                description = description,
                timestamp = System.currentTimeMillis()
            )
            expenseDao.insert(expense)
            "Expense added: ₹$amount for $category${if (description.isNotBlank()) " ($description)" else ""}."
        } catch (e: Exception) {
            "Couldn't add expense. ${e.message}"
        }
    }

    private suspend fun readExpenses(params: Map<String, String>): String {
        return try {
            val now = System.currentTimeMillis()
            val oneDayMs = 86400000L
            val oneWeekMs = 7 * oneDayMs

            // Determine time range
            val fromTimestamp = when {
                params.containsKey("week") -> now - oneWeekMs
                params.containsKey("month") -> now - 30 * oneDayMs
                else -> now - oneDayMs // Default: today
            }

            val expenses = expenseDao.getSince(fromTimestamp)
            val total = expenseDao.getTotalSince(fromTimestamp) ?: 0.0
            val categoryTotals = expenseDao.getTotalByCategorySince(fromTimestamp)

            if (expenses.isEmpty()) {
                return "No expenses recorded yet. Tell me something like 'expense add 200 rupees food'."
            }

            val categoryBreakdown = if (categoryTotals.isNotEmpty()) {
                categoryTotals.mapIndexed { i, ct -> "${i + 1}. ${ct.category}: ₹${String.format("%.0f", ct.total)}" }.joinToString(". ")
            } else ""

            val recentExpenses = expenses.take(5).mapIndexed { i, e ->
                "${i + 1}. ₹${String.format("%.0f", e.amount)} - ${e.category}${if (e.description.isNotBlank()) " (${e.description})" else ""}"
            }.joinToString(". ")

            "Total expenses: ₹${String.format("%.0f", total)}. Breakdown: $categoryBreakdown. Recent: $recentExpenses"
        } catch (e: Exception) {
            "Couldn't read expenses. ${e.message}"
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // USER MEMORY — Long-term memory for personal info
    // ══════════════════════════════════════════════════════════════════════

    private suspend fun saveUserMemory(input: String) {
        try {
            val lower = input.lowercase()

            // Detect personal info patterns and save to memory
            // Name: "mera naam Aadarsh hai", "my name is Aadarsh"
            val namePatterns = listOf(
                Regex("""(?i)mera\s+naam\s+(\w+)\s+hai"""),
                Regex("""(?i)my\s+name\s+is\s+(\w+)"""),
                Regex("""(?i)i'?m\s+(\w+)(?:\s+and)?""")
            )
            for (pattern in namePatterns) {
                val match = pattern.find(lower)
                if (match != null) {
                    val name = match.groupValues[1].trim().replaceFirstChar { it.uppercase() }
                    if (name.length > 1 && name !in listOf("not", "very", "so", "also", "just", "fine", "good", "okay", "doing", "from")) {
                        val existing = userMemoryDao.getByKey("user_name")
                        if (existing == null || existing.value != name) {
                            userMemoryDao.insert(UserMemoryEntity(category = "name", key = "user_name", value = name))
                        }
                    }
                    break
                }
            }

            // Location: "I live in Patna", "mai Patna me rehta hun"
            val locationPatterns = listOf(
                Regex("""(?i)(?:i\s+live\s+in|mai\s+)(\w+)(?:\s+me\s+reht|hun)?"""),
                Regex("""(?i)mera\s+(?:ghar|city|shehar)\s+(\w+)\s+(?:hai|me)?""")
            )
            for (pattern in locationPatterns) {
                val match = pattern.find(lower)
                if (match != null) {
                    val city = match.groupValues[1].trim().replaceFirstChar { it.uppercase() }
                    if (city.length > 1) {
                        userMemoryDao.insert(UserMemoryEntity(category = "location", key = "home_city", value = city))
                    }
                    break
                }
            }

            // General memory: "yaad rakhna ...", "remember that ..."
            val memoryPatterns = listOf(
                Regex("""(?i)(?:yaad\s+rakhna?|remember\s+(?:that|this))?\s*(.+)""")
            )
            for (pattern in memoryPatterns) {
                val match = pattern.find(input)
                if (match != null && (lower.contains("yaad") || lower.contains("remember"))) {
                    val fact = match.groupValues[1].trim()
                    if (fact.length > 2) {
                        userMemoryDao.insert(UserMemoryEntity(category = "fact", key = "memory_${System.currentTimeMillis()}", value = fact))
                    }
                    break
                }
            }

            // Preference: "mujhe ... pasand hai", "I like ..."
            val likePatterns = listOf(
                Regex("""(?i)mujhe\s+(.+?)\s+pasand\s+hai"""),
                Regex("""(?i)i\s+(?:like|love|prefer)\s+(.+?)(?:\.|$)""")
            )
            for (pattern in likePatterns) {
                val match = pattern.find(lower)
                if (match != null) {
                    val preference = match.groupValues[1].trim()
                    if (preference.length > 1) {
                        userMemoryDao.insert(UserMemoryEntity(category = "preference", key = "pref_${System.currentTimeMillis()}", value = preference))
                    }
                    break
                }
            }
        } catch (_: Exception) { }
    }

    private suspend fun readUserMemories(): String {
        return try {
            val memories = userMemoryDao.getAll()
            if (memories.isEmpty()) return ""
            memories.map { "${it.key}: ${it.value}" }.joinToString(". ")
        } catch (_: Exception) { "" }
    }

    private fun launchWebSearch(query: String): String {
        if (query.isBlank()) return "What would you like to search for?"
        return try {
            val intent = Intent(Intent.ACTION_WEB_SEARCH).apply { putExtra("query", query); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            appContext.startActivity(intent)
            "Searching for $query."
        } catch (e: Exception) {
            try {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                appContext.startActivity(webIntent)
                "Searching Google for $query."
            } catch (e2: Exception) { "Couldn't search for that. ${e2.message}" }
        }
    }

    private fun lookupContactPhoneNumber(name: String): String? {
        return try {
            var phoneNumber: String? = null
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("%$name%")
            appContext.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    phoneNumber = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
                }
            }
            phoneNumber
        } catch (_: Exception) { null }
    }

    // ══════════════════════════════════════════════════════════════════════
    // DEVICE CONTROL / ROUTINE / NOTIFICATION
    // ══════════════════════════════════════════════════════════════════════

    private suspend fun handleDeviceControl(intent: IntentClassifier.IntentResult): String {
        val action = intent.action ?: return "I couldn't understand which device to control."
        val lowerAction = action.lowercase()

        // Parse device and state from action string
        // Formats: "flashlight_on", "wifi_off", "brightness_70", "volume_50", "toggle_flashlight", etc.
        val device: String
        val stateValue: Int  // 1 = on, 0 = off, -1 = toggle

        when {
            // Flashlight / Torch
            lowerAction.contains("flashlight") || lowerAction.contains("torch") -> {
                device = "flashlight"
                stateValue = when {
                    lowerAction.contains("on") || lowerAction.contains("jala") -> 1
                    lowerAction.contains("off") || lowerAction.contains("bujha") -> 0
                    else -> -1
                }
            }
            // WiFi
            lowerAction.contains("wifi") -> {
                device = "wifi"
                stateValue = when {
                    lowerAction.contains("on") -> 1
                    lowerAction.contains("off") -> 0
                    else -> -1
                }
            }
            // Bluetooth
            lowerAction.contains("bluetooth") -> {
                device = "bluetooth"
                stateValue = when {
                    lowerAction.contains("on") -> 1
                    lowerAction.contains("off") -> 0
                    else -> -1
                }
            }
            // Brightness
            lowerAction.contains("brightness") -> {
                device = "brightness"
                val level = lowerAction.filter { it.isDigit() }.toIntOrNull()?.coerceIn(0, 255) ?: 128
                val result = deviceControlManager.setBrightness(level)
                refreshDeviceState()
                return if (result.isSuccess) "Brightness set to $level." else "Couldn't set brightness. Check permissions."
            }
            // Volume
            lowerAction.contains("volume") -> {
                device = "volume"
                val level = lowerAction.filter { it.isDigit() }.toIntOrNull()?.coerceIn(0, 15) ?: 7
                val result = deviceControlManager.setVolume(level)
                refreshDeviceState()
                return if (result.isSuccess) "Volume set to $level." else "Couldn't set volume. Check permissions."
            }
            // Ringer Mode
            lowerAction.contains("silent") || lowerAction.contains("vibrate") || lowerAction.contains("normal") && lowerAction.contains("mode") -> {
                device = "ringer_mode"
                val mode = when {
                    lowerAction.contains("silent") -> 0
                    lowerAction.contains("vibrate") -> 1
                    else -> 2
                }
                val result = deviceControlManager.setRingerMode(mode)
                refreshDeviceState()
                return if (result.isSuccess) "Ringer mode set to ${if (mode == 0) "silent" else if (mode == 1) "vibrate" else "normal"}." else "Couldn't change ringer mode."
            }
            // DND
            lowerAction.contains("dnd") || lowerAction.contains("do_not_disturb") -> {
                device = "dnd"
                stateValue = when {
                    lowerAction.contains("on") -> 1
                    lowerAction.contains("off") -> 0
                    else -> -1
                }
            }
            // Hotspot
            lowerAction.contains("hotspot") -> {
                device = "hotspot"
                stateValue = when {
                    lowerAction.contains("on") -> 1
                    lowerAction.contains("off") -> 0
                    else -> -1
                }
            }
            // Auto-rotate
            lowerAction.contains("rotate") || lowerAction.contains("auto_rotate") -> {
                device = "auto_rotate"
                stateValue = when {
                    lowerAction.contains("on") -> 1
                    lowerAction.contains("off") -> 0
                    else -> -1
                }
            }
            // Mobile Data
            lowerAction.contains("mobile_data") || lowerAction.contains("mobiledata") -> {
                device = "mobile_data"
                stateValue = when {
                    lowerAction.contains("on") -> 1
                    lowerAction.contains("off") -> 0
                    else -> -1
                }
            }
            // Generic toggle patterns
            lowerAction.startsWith("toggle_") -> {
                device = lowerAction.removePrefix("toggle_")
                stateValue = -1  // toggle
            }
            // Generic on/off patterns
            lowerAction.endsWith("_on") -> {
                device = lowerAction.removeSuffix("_on")
                stateValue = 1
            }
            lowerAction.endsWith("_off") -> {
                device = lowerAction.removeSuffix("_off")
                stateValue = 0
            }
            else -> {
                device = lowerAction
                stateValue = -1  // default to toggle
            }
        }

        val result = if (stateValue == -1) {
            deviceControlManager.toggle(device, 1)  // toggle
        } else {
            deviceControlManager.toggle(device, stateValue)
        }
        refreshDeviceState()

        return if (result.isSuccess) {
            val deviceDisplay = device.replace("_", " ").replaceFirstChar { it.uppercase() }
            when (stateValue) {
                1 -> "$deviceDisplay enabled."
                0 -> "$deviceDisplay disabled."
                else -> "$deviceDisplay toggled."
            }
        } else {
            "Couldn't control ${device.replace("_", " ")}. Check permissions."
        }
    }

    private suspend fun executeRoutine(name: String?): String {
        if (name == null) return "No routine specified."
        return try {
            routineEngine.executeRoutine(name)
            navigateTo("routines")
            "${name.replace("_", " ").replaceFirstChar { it.uppercase() }} routine activated."
        } catch (e: Exception) { "Couldn't execute routine. ${e.message}" }
    }

    private fun readNotifications(): String {
        val notifs = _notifications.value.take(5)
        return if (notifs.isEmpty()) "No recent notifications."
        else "Recent notifications: ${notifs.mapIndexed { i, n -> "${i + 1}. ${n.appName}: ${n.title}" }.joinToString(". ")}"
    }

    // ══════════════════════════════════════════════════════════════════════
    // AI FALLBACK — General Chat with MEMORY
    // ══════════════════════════════════════════════════════════════════════

    private suspend fun fetchAiResponse(input: String): String {
        navigateTo("chat")

        // Detect language for better response — PRIORITY: user's language toggle setting
        val isHindiText = com.mahi.assistant.api.WebSearchService.isHindiText(input)
        val currentLang = _currentLanguage.value
        val responseLang = when {
            isHindiText -> "hi" // Hindi script detected → Hinglish response
            currentLang == "hi" -> "hi" // Hindi mode → Hinglish response
            else -> "en" // English mode → English response
        }

        // Try to extract and save user memory from this input
        try { saveUserMemory(input) } catch (_: Exception) { }

        // ── STEP 1: Try AI with full context + memory ──────────────────
        if (aiEngine.isConfigured()) {
            try {
                // Build conversation context with last 50 messages for STRONG memory
                val history = _messages.value.takeLast(50).map {
                    com.mahi.assistant.ai.ChatMessage(
                        role = if (it.role == MessageRole.USER) com.mahi.assistant.ai.ChatMessage.ROLE_USER else com.mahi.assistant.ai.ChatMessage.ROLE_MODEL,
                        content = it.content,
                        timestamp = it.timestamp
                    )
                }

                // Include user memories in context for personalization
                val memories = readUserMemories()
                val contextPrefix = if (memories.isNotBlank()) {
                    com.mahi.assistant.ai.ChatMessage(
                        role = com.mahi.assistant.ai.ChatMessage.ROLE_MODEL,
                        content = "[System: User memories - $memories]",
                        timestamp = 0L
                    )
                } else null

                // CRITICAL: Language instruction based on toggle
                val langInstruction = when (responseLang) {
                    "hi" -> "[CRITICAL INSTRUCTION: You MUST respond in Hinglish (Hindi written in English/Roman script). " +
                            "DO NOT use Devanagari script. DO NOT respond in pure English. " +
                            "Write Hindi words in English letters. Example: 'Haan main aapki madad kar sakta hoon!' " +
                            "'Aaj ka mausam accha hai.' 'Kya aap mujhe aur bata sakte hain?']"
                    else -> "[CRITICAL INSTRUCTION: You MUST respond in English. " +
                            "Do NOT use Hindi, Hinglish, or Devanagari script. " +
                            "Respond in clear, natural English.]"
                }

                // RESEARCH INSTRUCTION: Tell AI to use web research results if provided
                val researchInstruction = "[INSTRUCTION: If you see web research results above, use them to answer. " +
                        "Synthesize information naturally. Cite sources when relevant. " +
                        "If research results contradict your knowledge, trust the research. " +
                        "If no research results provided, answer from your knowledge.]"

                // Check if this query needs web research
                val needsWebResearch = com.mahi.assistant.api.WebSearchService.needsResearch(input)

                // If research needed, do web search FIRST, then pass results to AI for summarization
                var researchResults = ""
                if (needsWebResearch) {
                    try {
                        researchResults = com.mahi.assistant.api.WebSearchService.deepResearch(input)
                    } catch (_: Exception) {}
                }

                val researchContext = if (researchResults.isNotBlank() && researchResults.length > 30) {
                    com.mahi.assistant.ai.ChatMessage(
                        role = com.mahi.assistant.ai.ChatMessage.ROLE_MODEL,
                        content = "[Web Research Results for '$input':\n$researchResults]",
                        timestamp = 0L
                    )
                } else null

                val langHint = com.mahi.assistant.ai.ChatMessage(
                    role = com.mahi.assistant.ai.ChatMessage.ROLE_MODEL,
                    content = langInstruction,
                    timestamp = 0L
                )
                val researchHint = com.mahi.assistant.ai.ChatMessage(
                    role = com.mahi.assistant.ai.ChatMessage.ROLE_MODEL,
                    content = researchInstruction,
                    timestamp = 0L
                )

                val fullHistory = listOfNotNull(langHint, researchHint, researchContext, contextPrefix) + history
                val response = aiEngine.chatWithMemory(input, fullHistory)

                // If the response looks like an error, fall through to web search
                val isErrorResponse = response == "FALLBACK_TO_SEARCH" ||
                    response.contains("trouble connecting") || response.contains("check your internet") ||
                    response.contains("API key") || response.contains("having trouble") ||
                    response.contains("No AI backend") || response.contains("KEY_INVALID") ||
                    response.contains("ACCESS_DENIED") || response.contains("timed out") ||
                    response.contains("internet connection") || response.contains("server error") ||
                    response.contains("unavailable") || response.contains("rate limit")
                if (isErrorResponse) {
                    // AI failed — use research results directly if available
                    if (researchResults.isNotBlank() && researchResults.length > 30) {
                        return summarizeResearch(input, researchResults, responseLang)
                    }
                    // Fall through to web search
                } else if (!response.contains("error", ignoreCase = true) || response.length > 100) {
                    // Good AI response — return it
                    return response
                }
            } catch (e: Exception) {
                // AI failed — try research
            }
        }

        // ── STEP 2: AI not configured or failed — Deep research pipeline ──
        return try {
            val researchResults = com.mahi.assistant.api.WebSearchService.deepResearch(input)
            if (researchResults.isNotBlank() && researchResults.length > 30) {
                // If AI is available, use it to summarize the research
                if (aiEngine.isConfigured()) {
                    try {
                        val summaryPrompt = buildString {
                            append("Based on the following web research, answer the user's question naturally and conversationally.\n\n")
                            append("Question: $input\n\n")
                            append("Research Results:\n${researchResults.take(1000)}\n\n")
                            if (responseLang == "hi") {
                                append("Answer in Hinglish (Hindi in English script). Do NOT use Devanagari.")
                            } else {
                                append("Answer in English.")
                            }
                        }
                        val summary = aiEngine.queryOnce(summaryPrompt)
                        if (summary.isNotBlank() && !summary.contains("error", ignoreCase = true)) {
                            return summary
                        }
                    } catch (_: Exception) {}
                }
                // AI summarization failed — return research directly
                summarizeResearch(input, researchResults, responseLang)
            } else {
                // Even research failed — provide helpful response
                tryWebSearch(input, responseLang)
            }
        } catch (e: Exception) {
            tryWebSearch(input, responseLang)
        }
    }

    /**
     * Summarize web research results into a natural, conversational response.
     * Never returns "I don't have info" — always makes the best of what we have.
     */
    private fun summarizeResearch(query: String, research: String, language: String): String {
        val isHindi = language == "hi"
        val q = query.lowercase()

        // Quick answers for common queries
        when {
            q.contains("time") || q.contains("samay") || q.contains("waqt") -> {
                val time = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
                return if (isHindi) "Abhi ka time $time hai." else "Current time is $time."
            }
            q.contains("date") || q.contains("tarikh") -> {
                val date = java.text.SimpleDateFormat("dd MMMM yyyy, EEEE", java.util.Locale.getDefault()).format(java.util.Date())
                return if (isHindi) "Aaj ki date $date hai." else "Today's date is $date."
            }
        }

        // Clean up research text for natural response
        val cleanResearch = research
            .replace(Regex("\\[\\w+\\]\\s*"), "")
            .replace(Regex("Source:\\s*"), "")
            .trim()
            .take(600)

        // Format as natural conversational response
        return if (isHindi) {
            "Mujhe ye mila: $cleanResearch"
        } else {
            "Here's what I found: $cleanResearch"
        }
    }

    /**
     * ULTIMATE FALLBACK: Multi-source web search when AI APIs fail.
     * NEVER returns 'I'm having trouble' — always provides something useful.
     * Uses: Wikipedia → DuckDuckGo → Contextual help
     */
    private suspend fun tryWebSearch(query: String, language: String = "en"): String {
        val isHindi = language == "hi" || com.mahi.assistant.api.WebSearchService.isHindiText(query)

        return try {
            // Try deep research first (4 sources: Wikipedia + DuckDuckGo + Brave + News)
            val searchResult = com.mahi.assistant.api.WebSearchService.deepResearch(query)
            if (searchResult.isNotBlank() && searchResult.length > 20 &&
                !searchResult.contains("couldn't find") && !searchResult.contains("couldn't search")) {
                return summarizeResearch(query, searchResult, language)
            }

            // Research failed - provide contextual response (NEVER "I don't have info")
            val q = query.lowercase()
            when {
                q.contains("weather") || q.contains("mausam") -> {
                    val city = settingsManager.getDefaultCity()
                    if (isHindi) "$city ka mausam jaanne ke liye 'mausam batao' bolo." else "Say 'weather' to check the weather for $city."
                }
                q.contains("news") || q.contains("khabar") -> {
                    if (isHindi) "Latest khabar jaanne ke liye 'aaj ki khabar' bolo." else "Say 'news' to get the latest headlines."
                }
                q.contains("time") || q.contains("samay") -> {
                    val time = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
                    if (isHindi) "Abhi ka time $time hai." else "Current time is $time."
                }
                q.contains("date") -> {
                    val date = java.text.SimpleDateFormat("dd MMMM yyyy, EEEE", java.util.Locale.getDefault()).format(java.util.Date())
                    if (isHindi) "Aaj ki date $date hai." else "Today's date is $date."
                }
                else -> {
                    if (isHindi) "Main search kar raha hoon lekin abhi exact answer nahi mil raha. Dobara puch sakte hain ya kuch aur poochein!"
                    else "I searched but couldn't find a specific answer right now. Try rephrasing or ask me something else - I'll keep trying!"
                }
            }
        } catch (e: Exception) {
            if (isHindi) "Internet connection check karo aur dobara try karo. Device commands jaise call, SMS, flashlight abhi bhi kaam karte hain!"
            else "Please check your internet connection and try again. Device commands like calls, SMS, and flashlight still work!"
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // DEVICE STATE UI
    // ══════════════════════════════════════════════════════════════════════

    fun toggleDevice(deviceName: String) {
        viewModelScope.launch {
            val current = when (deviceName) {
                "flashlight" -> _deviceState.value.flashlight
                "wifi" -> _deviceState.value.wifi
                "bluetooth" -> _deviceState.value.bluetooth
                "auto_rotate" -> _deviceState.value.autoRotate
                "hotspot" -> _deviceState.value.hotspot
                "mobile_data" -> _deviceState.value.mobileData
                "dnd" -> _deviceState.value.dnd
                "battery_saver" -> _deviceState.value.batterySaver
                else -> false
            }
            deviceControlManager.toggle(deviceName, if (!current) 1 else 0)
            refreshDeviceState()
        }
    }

    private fun refreshDeviceState() {
        viewModelScope.launch {
            val s = deviceControlManager.deviceStates.value
            _deviceState.value = DeviceUiState(
                flashlight = s["flashlight"]?.isOn ?: false,
                wifi = s["wifi"]?.isOn ?: false,
                bluetooth = s["bluetooth"]?.isOn ?: false,
                brightness = s["brightness"]?.value ?: 50,
                volume = s["volume"]?.value ?: 50,
                ringerMode = s["ringer_mode"]?.value ?: 2,
                autoRotate = s["auto_rotate"]?.isOn ?: false,
                hotspot = s["hotspot"]?.isOn ?: false,
                mobileData = s["mobile_data"]?.isOn ?: true,
                dnd = s["dnd"]?.isOn ?: false,
                batterySaver = s["battery_saver"]?.isOn ?: false,
                screenTimeout = s["screen_timeout"]?.value ?: 30000
            )
        }
    }

    fun stopSpeaking() {
        ttsEngine.stop()
        if (_assistantState.value == AssistantState.SPEAKING) _assistantState.value = AssistantState.IDLE
    }

    override fun onCleared() {
        super.onCleared()
        voiceRecognition.destroy()
        ttsEngine.shutdown()
        processingJob?.cancel()
    }
}
