package com.mahi.assistant.ui.viewmodel

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mahi.assistant.ai.AiConversationEngine
import com.mahi.assistant.ai.IntentClassifier
import com.mahi.assistant.api.NewsService
import com.mahi.assistant.api.WeatherService
import com.mahi.assistant.api.WeatherClient
import com.mahi.assistant.api.NewsClient
import com.mahi.assistant.automation.RoutineEngine
import com.mahi.assistant.control.DeviceControlManager
import com.mahi.assistant.data.local.MessageDao
import com.mahi.assistant.data.local.MessageEntity
import com.mahi.assistant.data.local.SettingsManager
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
    val porcupineKey: String = "",
    val weatherKey: String = "",
    val newsKey: String = "",
    val voiceSpeed: Float = 1.0f,
    val voicePitch: Float = 1.0f,
    val wakeWord: String = "Hey Mahi",
    val autoStartOnBoot: Boolean = true,
    val floatingAssistant: Boolean = false,
    val isSaved: Boolean = false
)

@HiltViewModel
class MahiViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val aiEngine: AiConversationEngine,
    private val intentClassifier: IntentClassifier,
    private val deviceControlManager: DeviceControlManager,
    private val routineEngine: RoutineEngine,
    private val messageDao: MessageDao,
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

    private var processingJob: Job? = null

    // ── Known app package names for APP_LAUNCH ───────────────────────────────

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
        "play music" to "com.google.android.music",
        "netflix" to "com.netflix.mediaclient",
        "amazon" to "com.amazon.mShop.android.shopping",
        "flipkart" to "com.flipkart.android",
        "swiggy" to "in.swiggy.android",
        "zomato" to "com.application.zomato",
        "paytm" to "net.one97.paytm",
        "gpay" to "com.google.android.apps.nbu.paisa.user",
        "google pay" to "com.google.android.apps.nbu.paisa.user",
        "phonepe" to "com.phonepe.app",
        "files" to "com.google.android.apps.nbu.files",
        "gallery" to "com.android.gallery3d",
        "photos" to "com.google.android.apps.photos",
        "notes" to "com.google.android.keep",
        "keep" to "com.google.android.keep",
        "meet" to "com.google.android.apps.meetings",
        "zoom" to "us.zoom.videomeetings",
        "linkedin" to "com.linkedin.android",
        "reddit" to "com.reddit.frontpage",
        "pinterest" to "com.pinterest",
        "discord" to "com.discord"
    )

    init {
        // Load saved messages (defensive — Room might not be ready)
        viewModelScope.launch {
            try {
                val entities = messageDao.getRecent(50)
                _messages.value = entities.map { entity: MessageEntity ->
                    ChatMessage(
                        id = entity.id.toString(),
                        role = if (entity.role == "USER") MessageRole.USER else MessageRole.ASSISTANT,
                        content = entity.content,
                        timestamp = entity.timestamp
                    )
                }
            } catch (e: Exception) {
                // Room not ready or corrupted — start with empty messages
            }
        }

        // Load settings
        try { loadSettings() } catch (_: Exception) { /* Settings not available */ }

        // Voice recognition callback
        try {
            voiceRecognition.callback = object : VoiceRecognitionEngine.Callback {
                override fun onResult(text: String) {
                    _isListening.value = false
                    _assistantState.value = AssistantState.IDLE
                    if (text.isNotBlank()) processInput(text)
                }
                override fun onPartial(text: String) { _partialTranscript.value = text }
                override fun onError(code: Int) {
                    _isListening.value = false
                    _assistantState.value = AssistantState.IDLE
                }
                override fun onReady() {
                    _isListening.value = true
                    _assistantState.value = AssistantState.LISTENING
                }
                override fun onEnd() {
                    _isListening.value = false
                    if (_assistantState.value == AssistantState.LISTENING) {
                        _assistantState.value = AssistantState.IDLE
                    }
                }
            }
        } catch (_: Exception) { /* Voice recognition not available */ }

        // TTS callback
        try {
            ttsEngine.callback = object : TextToSpeechEngine.Callback {
                override fun onSpeakStart(utteranceId: String) { _assistantState.value = AssistantState.SPEAKING }
                override fun onSpeakEnd(utteranceId: String) { _assistantState.value = AssistantState.IDLE }
                override fun onError(utteranceId: String) { _assistantState.value = AssistantState.IDLE }
            }
        } catch (_: Exception) { /* TTS not available */ }

        // Routines
        viewModelScope.launch {
            try {
                routineEngine.availableRoutines.collect { _routines.value = it }
            } catch (_: Exception) { /* Routine engine not available */ }
        }

        // Notifications
        viewModelScope.launch {
            try {
                MahiNotificationListenerService.instance?.notifications?.collect { _notifications.value = it }
            } catch (_: Exception) { /* Notification listener not available */ }
        }
    }

    // ── Settings Management ─────────────────────────────────────────

    fun loadSettings() {
        _settingsState.value = SettingsUiState(
            geminiKey = settingsManager.getGeminiApiKey(),
            porcupineKey = settingsManager.getPorcupineKey(),
            weatherKey = settingsManager.getWeatherApiKey(),
            newsKey = settingsManager.getNewsApiKey(),
            voiceSpeed = settingsManager.getVoiceSpeed(),
            voicePitch = settingsManager.getVoicePitch(),
            wakeWord = settingsManager.getWakeWord(),
            autoStartOnBoot = settingsManager.getAutoStartOnBoot(),
            floatingAssistant = settingsManager.getFloatingAssistant()
        )
    }

    fun updateGeminiKey(key: String) {
        settingsManager.setGeminiApiKey(key)
        _settingsState.value = _settingsState.value.copy(geminiKey = key, isSaved = false)
    }

    fun updatePorcupineKey(key: String) {
        settingsManager.setPorcupineKey(key)
        _settingsState.value = _settingsState.value.copy(porcupineKey = key, isSaved = false)
    }

    fun updateWeatherKey(key: String) {
        settingsManager.setWeatherApiKey(key)
        _settingsState.value = _settingsState.value.copy(weatherKey = key, isSaved = false)
    }

    fun updateNewsKey(key: String) {
        settingsManager.setNewsApiKey(key)
        _settingsState.value = _settingsState.value.copy(newsKey = key, isSaved = false)
    }

    fun updateVoiceSpeed(speed: Float) {
        settingsManager.setVoiceSpeed(speed)
        _settingsState.value = _settingsState.value.copy(voiceSpeed = speed)
    }

    fun updateVoicePitch(pitch: Float) {
        settingsManager.setVoicePitch(pitch)
        _settingsState.value = _settingsState.value.copy(voicePitch = pitch)
    }

    fun updateWakeWord(word: String) {
        settingsManager.setWakeWord(word)
        _settingsState.value = _settingsState.value.copy(wakeWord = word)
    }

    fun updateAutoStartOnBoot(enabled: Boolean) {
        settingsManager.setAutoStartOnBoot(enabled)
        _settingsState.value = _settingsState.value.copy(autoStartOnBoot = enabled)
    }

    fun updateFloatingAssistant(enabled: Boolean) {
        settingsManager.setFloatingAssistant(enabled)
        _settingsState.value = _settingsState.value.copy(floatingAssistant = enabled)
    }

    fun saveAllSettings() {
        _settingsState.value = _settingsState.value.copy(isSaved = true)
    }

    // ── Navigation ──────────────────────────────────────────────────

    fun navigateTo(route: String) { _currentRoute.value = route }

    // ── Voice ───────────────────────────────────────────────────────

    fun startListening() {
        _partialTranscript.value = ""
        voiceRecognition.startListening()
    }

    fun stopListening() {
        voiceRecognition.stopListening()
        _isListening.value = false
        _assistantState.value = AssistantState.IDLE
    }

    fun updateInput(text: String) { _currentInput.value = text }

    // ── Chat ────────────────────────────────────────────────────────

    fun submitInput() {
        val input = _currentInput.value.trim()
        if (input.isNotBlank()) {
            _currentInput.value = ""
            processInput(input)
        }
    }

    fun processInput(input: String) {
        processingJob?.cancel()
        processingJob = viewModelScope.launch {
            _assistantState.value = AssistantState.THINKING

            // Save user message
            val userMessage = ChatMessage(role = MessageRole.USER, content = input)
            _messages.value = _messages.value + userMessage
            try {
                messageDao.insert(MessageEntity(role = "USER", content = input, timestamp = System.currentTimeMillis()))
            } catch (_: Exception) { }

            // Classify intent and handle
            val intent = intentClassifier.classifySync(input)
            val response = handleIntent(intent, input)

            // Save assistant message
            val assistantMessage = ChatMessage(role = MessageRole.ASSISTANT, content = response)
            _messages.value = _messages.value + assistantMessage
            try {
                messageDao.insert(MessageEntity(role = "ASSISTANT", content = response, timestamp = System.currentTimeMillis()))
            } catch (_: Exception) { }

            // Speak the response
            val speechText = response.replace(Regex("\\[\\w+\\]\\s*"), "").replace(Regex("[\\*#_]"), "").take(500)
            ttsEngine.speak(speechText, "response_${System.currentTimeMillis()}")
        }
    }

    private suspend fun handleIntent(intent: IntentClassifier.IntentResult, originalInput: String): String {
        return when (intent.type) {
            IntentClassifier.IntentType.DEVICE_CONTROL -> handleDeviceControl(intent)
            IntentClassifier.IntentType.WEATHER -> fetchWeather(intent.params["city"] ?: "New Delhi")
            IntentClassifier.IntentType.NEWS -> fetchNews(intent.params["category"] ?: "general")
            IntentClassifier.IntentType.YOUTUBE -> launchYouTube(intent.params["query"] ?: "")
            IntentClassifier.IntentType.CALL -> launchCall(intent.params["contact"] ?: "")
            IntentClassifier.IntentType.SMS -> launchSms(intent.params["contact"] ?: "", intent.params["message"])
            IntentClassifier.IntentType.ALARM -> launchAlarm(intent.params["time"])
            IntentClassifier.IntentType.ROUTINE -> executeRoutine(intent.action)
            IntentClassifier.IntentType.NOTIFICATION -> readNotifications()
            IntentClassifier.IntentType.CALENDAR -> launchCalendar()
            IntentClassifier.IntentType.APP_LAUNCH -> launchApp(intent.params["app"] ?: "")
            IntentClassifier.IntentType.WEB_SEARCH -> launchWebSearch(intent.params["query"] ?: originalInput)
            else -> fetchAiResponse(originalInput)
        }
    }

    // ── App Launch ──────────────────────────────────────────────────────

    private fun launchApp(appName: String): String {
        if (appName.isBlank()) return "Which app would you like me to open?"

        val packageName = appPackageMap[appName.lowercase().trim()]

        return try {
            if (packageName != null) {
                // Known app — launch directly
                val launchIntent = appContext.packageManager.getLaunchIntentForPackage(packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    appContext.startActivity(launchIntent)
                    "Opening ${appName.replaceFirstChar { it.uppercase() }}."
                } else {
                    // App installed but no launch intent — open Play Store
                    openPlayStore(packageName)
                    "${appName.replaceFirstChar { it.uppercase() }} can't be opened directly. Opening Play Store."
                }
            } else {
                // Unknown app name — try to find it by searching package manager
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
                    // Not found — search Play Store
                    openPlayStoreSearch(appName)
                    "I couldn't find ${appName.replaceFirstChar { it.uppercase() }} on your device. Searching Play Store."
                }
            }
        } catch (e: ActivityNotFoundException) {
            openPlayStoreSearch(appName)
            "Opening Play Store to find ${appName.replaceFirstChar { it.uppercase() }}."
        } catch (e: Exception) {
            "I couldn't open ${appName.replaceFirstChar { it.uppercase() }}. ${e.message}"
        }
    }

    private fun findPackageByAppName(appName: String): String? {
        return try {
            val query = appName.lowercase().trim()
            val packages = appContext.packageManager.getInstalledApplications(0)
            packages.firstOrNull { appInfo ->
                val label = appContext.packageManager.getApplicationLabel(appInfo).toString().lowercase()
                label.contains(query) || query.contains(label)
            }?.packageName
        } catch (_: Exception) {
            null
        }
    }

    private fun openPlayStore(packageName: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
        }
    }

    private fun openPlayStoreSearch(query: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=${Uri.encode(query)}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=${Uri.encode(query)}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
        }
    }

    // ── YouTube ─────────────────────────────────────────────────────────

    private fun launchYouTube(query: String): String {
        return try {
            if (query.isNotBlank()) {
                // Search YouTube for the query
                val intent = Intent(Intent.ACTION_SEARCH).apply {
                    component = ComponentName("com.google.android.youtube", "com.google.android.youtube.SearchActivity")
                    putExtra("query", query)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    appContext.startActivity(intent)
                    "Searching YouTube for $query."
                } catch (e: ActivityNotFoundException) {
                    // Fallback: open YouTube via web URL
                    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    appContext.startActivity(webIntent)
                    "Searching YouTube for $query."
                }
            } else {
                // Just open the YouTube app
                val launchIntent = appContext.packageManager.getLaunchIntentForPackage("com.google.android.youtube")
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    appContext.startActivity(launchIntent)
                    "Opening YouTube."
                } else {
                    // YouTube not installed — open web version
                    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    appContext.startActivity(webIntent)
                    "Opening YouTube in browser."
                }
            }
        } catch (e: Exception) {
            "I couldn't open YouTube. ${e.message}"
        }
    }

    // ── Call ────────────────────────────────────────────────────────────

    private fun launchCall(contactName: String): String {
        if (contactName.isBlank()) return "Who would you like me to call?"

        return try {
            // First try to find the contact in the phone's contacts
            val phoneNumber = lookupContactPhoneNumber(contactName)
            if (phoneNumber != null) {
                val callIntent = Intent(Intent.ACTION_CALL).apply {
                    data = Uri.parse("tel:$phoneNumber")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(callIntent)
                "Calling $contactName."
            } else {
                // Contact not found — open dialer with the name (Android will try to match)
                val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:${Uri.encode(contactName)}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(dialIntent)
                "I couldn't find $contactName in your contacts. Opening dialer."
            }
        } catch (e: SecurityException) {
            // No CALL_PHONE permission — fall back to dialer
            try {
                val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:${Uri.encode(contactName)}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(dialIntent)
                "Opening dialer for $contactName. Please grant phone permission to call directly."
            } catch (e2: Exception) {
                "I need phone permission to make calls. Please grant the permission in Settings."
            }
        } catch (e: Exception) {
            "I couldn't call $contactName. ${e.message}"
        }
    }

    private fun lookupContactPhoneNumber(name: String): String? {
        return try {
            var phoneNumber: String? = null
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            )
            val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("%$name%")

            appContext.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    phoneNumber = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
                }
            }
            phoneNumber
        } catch (_: Exception) {
            null
        }
    }

    // ── SMS ──────────────────────────────────────────────────────────────

    private fun launchSms(contactName: String, message: String?): String {
        if (contactName.isBlank()) return "Who would you like to message?"

        return try {
            val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:${Uri.encode(contactName)}")
                if (message != null) {
                    putExtra("sms_body", message)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(smsIntent)
            "Opening message for $contactName."
        } catch (e: Exception) {
            "I couldn't open messages for $contactName. ${e.message}"
        }
    }

    // ── Alarm ────────────────────────────────────────────────────────────

    private fun launchAlarm(time: String?): String {
        return try {
            if (time != null) {
                val alarmIntent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_MESSAGE, "MAHI Alarm")
                    putExtra(AlarmClock.EXTRA_IS_PM, false)
                    // Try to parse the time
                    val timeRegex = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?", RegexOption.IGNORE_CASE)
                    val match = timeRegex.find(time)
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
                // Just open the alarm app
                val alarmIntent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(alarmIntent)
                "Opening alarm settings."
            }
        } catch (e: Exception) {
            "I couldn't set an alarm. ${e.message}"
        }
    }

    // ── Calendar ─────────────────────────────────────────────────────────

    private fun launchCalendar(): String {
        return try {
            val calendarIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_CALENDAR)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(calendarIntent)
            "Opening calendar."
        } catch (e: Exception) {
            "I couldn't open the calendar. ${e.message}"
        }
    }

    // ── Web Search ───────────────────────────────────────────────────────

    private fun launchWebSearch(query: String): String {
        if (query.isBlank()) return "What would you like me to search for?"

        return try {
            val searchIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra("query", query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(searchIntent)
            "Searching for $query."
        } catch (e: Exception) {
            // Fallback to opening in browser
            try {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(webIntent)
                "Searching Google for $query."
            } catch (e2: Exception) {
                "I couldn't search for that. ${e2.message}"
            }
        }
    }

    // ── Device Control ──────────────────────────────────────────────────

    private suspend fun handleDeviceControl(intent: IntentClassifier.IntentResult): String {
        val action = intent.action ?: return "I couldn't understand which device to control."
        val parts = action.split("_")
        if (parts.size < 2) return "Invalid device command."
        val command = parts[0]
        val device = parts.drop(1).joinToString("_")
        val turnOn = command == "turn" && parts.getOrNull(1) == "on"
        val result = deviceControlManager.toggle(device, if (turnOn) 1 else 0)
        refreshDeviceState()
        return if (result.isSuccess) {
            intent.response ?: "${device.replace("_", " ").replaceFirstChar { it.uppercase() }} ${if (turnOn) "enabled" else "disabled"}."
        } else {
            "Couldn't ${if (turnOn) "enable" else "disable"} ${device.replace("_", " ")}. Check permissions."
        }
    }

    // ── Weather ──────────────────────────────────────────────────────────

    private suspend fun fetchWeather(city: String): String {
        _weatherState.value = _weatherState.value.copy(isLoading = true)
        return try {
            val weatherApiKey = settingsManager.getWeatherApiKey()
            if (weatherApiKey.isBlank()) {
                _weatherState.value = _weatherState.value.copy(isLoading = false)
                return "Weather API key not set. Please add your OpenWeatherMap API key in Settings."
            }
            val weather = WeatherClient.instance.getCurrentWeather(city, weatherApiKey, "metric")
            _weatherState.value = WeatherUiState(
                city = weather.name ?: city,
                temperature = weather.main?.temp ?: 0.0,
                description = weather.weather?.firstOrNull()?.description ?: "",
                humidity = weather.main?.humidity ?: 0,
                windSpeed = weather.wind?.speed ?: 0.0,
                pressure = weather.main?.pressure ?: 0,
                feelsLike = weather.main?.feelsLike ?: 0.0,
                icon = weather.weather?.firstOrNull()?.icon ?: "",
                isLoading = false
            )
            navigateTo("weather")
            "Weather in ${weather.name ?: city}: ${(weather.main?.temp ?: 0.0).toInt()} degrees, ${weather.weather?.firstOrNull()?.description ?: "clear"}. Humidity ${weather.main?.humidity ?: 0}%. Feels like ${(weather.main?.feelsLike ?: 0.0).toInt()} degrees."
        } catch (e: retrofit2.HttpException) {
            _weatherState.value = _weatherState.value.copy(isLoading = false, error = e.message)
            when (e.code()) {
                401 -> "Weather API key seems invalid. Please check your OpenWeatherMap API key in Settings."
                404 -> "City not found. Try specifying a different city name."
                else -> "Couldn't fetch weather for $city. Error: ${e.code()}"
            }
        } catch (e: Exception) {
            _weatherState.value = _weatherState.value.copy(isLoading = false, error = e.message)
            "Couldn't fetch weather for $city. Please check your internet connection."
        }
    }

    // ── News ──────────────────────────────────────────────────────────────

    private suspend fun fetchNews(category: String): String {
        _newsState.value = _newsState.value.copy(isLoading = true, selectedCategory = category)
        return try {
            val newsApiKey = settingsManager.getNewsApiKey()
            if (newsApiKey.isBlank()) {
                _newsState.value = _newsState.value.copy(isLoading = false)
                return "News API key not set. Please add your GNews API key in Settings."
            }
            val news = NewsClient.instance.getTopHeadlines(category = category, lang = "en", token = newsApiKey)
            _newsState.value = NewsUiState(articles = news.articles ?: emptyList(), isLoading = false, selectedCategory = category)
            navigateTo("news")
            if (news.articles.isNullOrEmpty()) "No news articles found at the moment."
            else "Top headlines: ${news.articles.take(3).mapIndexed { i, a -> "${i + 1}. ${a.title ?: "Untitled"}" }.joinToString(". ")}"
        } catch (e: retrofit2.HttpException) {
            _newsState.value = _newsState.value.copy(isLoading = false, error = e.message)
            when (e.code()) {
                401 -> "News API key seems invalid. Please check your GNews API key in Settings."
                403 -> "News API access denied. Your API key may have expired."
                else -> "Couldn't fetch news. Error: ${e.code()}"
            }
        } catch (e: Exception) {
            _newsState.value = _newsState.value.copy(isLoading = false, error = e.message)
            "Couldn't fetch news. Please check your internet connection."
        }
    }

    // ── Routine ──────────────────────────────────────────────────────────

    private suspend fun executeRoutine(name: String?): String {
        if (name == null) return "No routine specified."
        return try {
            routineEngine.executeRoutine(name)
            navigateTo("routines")
            "${name.replace("_", " ").replaceFirstChar { it.uppercase() }} routine activated."
        } catch (e: Exception) { "Couldn't execute $name routine. ${e.message}" }
    }

    // ── Notifications ────────────────────────────────────────────────────

    private fun readNotifications(): String {
        val notifs = _notifications.value.take(5)
        return if (notifs.isEmpty()) "No recent notifications."
        else "Recent notifications: ${notifs.mapIndexed { i, n -> "${i + 1}. ${n.appName}: ${n.title}" }.joinToString(". ")}"
    }

    // ── AI Response (fallback for general chat) ──────────────────────────

    private suspend fun fetchAiResponse(input: String): String {
        navigateTo("chat")
        return try {
            val history = _messages.value.takeLast(10).map {
                com.mahi.assistant.ai.ChatMessage(role = it.role.name.lowercase(), content = it.content)
            }
            aiEngine.sendMessage(input, history)
        } catch (e: Exception) {
            "I'm having trouble connecting right now. Please check your internet connection and try again."
        }
    }

    // ── Device Control UI ────────────────────────────────────────────────

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
