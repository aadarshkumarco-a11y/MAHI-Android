package com.mahi.assistant.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mahi.assistant.ai.AiConversationEngine
import com.mahi.assistant.ai.IntentClassifier
import com.mahi.assistant.api.NewsService
import com.mahi.assistant.api.WeatherService
import com.mahi.assistant.automation.RoutineEngine
import com.mahi.assistant.control.DeviceControlManager
import com.mahi.assistant.data.local.MessageDao
import com.mahi.assistant.data.local.MessageEntity
import com.mahi.assistant.data.model.AssistantState
import com.mahi.assistant.data.model.ChatMessage
import com.mahi.assistant.data.model.IntentResult
import com.mahi.assistant.data.model.IntentResult.IntentType
import com.mahi.assistant.data.model.NotificationItem
import com.mahi.assistant.data.model.Routine
import com.mahi.assistant.service.MahiNotificationListenerService
import com.mahi.assistant.voice.TextToSpeechEngine
import com.mahi.assistant.voice.VoiceRecognitionEngine
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val articles: List<NewsService.Article> = emptyList(),
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

@HiltViewModel
class MahiViewModel @Inject constructor(
    private val aiEngine: AiConversationEngine,
    private val intentClassifier: IntentClassifier,
    private val deviceControlManager: DeviceControlManager,
    private val routineEngine: RoutineEngine,
    private val messageDao: MessageDao,
    private val voiceRecognition: VoiceRecognitionEngine,
    private val ttsEngine: TextToSpeechEngine
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

    private var processingJob: Job? = null

    init {
        viewModelScope.launch {
            messageDao.getRecent(50).collect { entities ->
                _messages.value = entities.map { entity ->
                    ChatMessage(
                        id = entity.id,
                        role = if (entity.role == "USER") ChatMessage.Role.USER else ChatMessage.Role.ASSISTANT,
                        content = entity.content,
                        timestamp = entity.timestamp
                    )
                }
            }
        }

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

        ttsEngine.callback = object : TextToSpeechEngine.Callback {
            override fun onSpeakStart() { _assistantState.value = AssistantState.SPEAKING }
            override fun onSpeakEnd() { _assistantState.value = AssistantState.IDLE }
            override fun onError() { _assistantState.value = AssistantState.IDLE }
        }

        viewModelScope.launch {
            routineEngine.availableRoutines.collect { _routines.value = it }
        }

        viewModelScope.launch {
            MahiNotificationListenerService.instance?.notifications?.collect { _notifications.value = it }
        }
    }

    fun navigateTo(route: String) { _currentRoute.value = route }

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

            val userMessage = ChatMessage(role = ChatMessage.Role.USER, content = input)
            _messages.value = _messages.value + userMessage
            messageDao.insert(MessageEntity(role = "USER", content = input, timestamp = System.currentTimeMillis()))

            val intent = intentClassifier.classifySync(input)
            val response = handleIntent(intent, input)

            val assistantMessage = ChatMessage(role = ChatMessage.Role.ASSISTANT, content = response)
            _messages.value = _messages.value + assistantMessage
            messageDao.insert(MessageEntity(role = "ASSISTANT", content = response, timestamp = System.currentTimeMillis()))

            val speechText = response.replace(Regex("\\[\\w+\\]\\s*"), "").replace(Regex("[\\*#_]"), "").take(500)
            ttsEngine.speak(speechText, "response_${System.currentTimeMillis()}")
        }
    }

    private suspend fun handleIntent(intent: IntentResult, originalInput: String): String {
        return when (intent.type) {
            IntentType.DEVICE_CONTROL -> handleDeviceControl(intent)
            IntentType.WEATHER -> fetchWeather(intent.params["city"] ?: "New Delhi")
            IntentType.NEWS -> fetchNews(intent.params["category"] ?: "general")
            IntentType.YOUTUBE -> "Launching YouTube search for ${intent.params["query"] ?: originalInput}."
            IntentType.CALL -> "Initiating call to ${intent.params["contact"] ?: "unknown"}. Please confirm."
            IntentType.SMS -> "Opening SMS app for confirmation."
            IntentType.ALARM -> "Setting alarm for ${intent.params["time"] ?: "requested time"}."
            IntentType.ROUTINE -> executeRoutine(intent.action ?: "unknown")
            IntentType.NOTIFICATION -> readNotifications()
            IntentType.CALENDAR -> "Opening calendar app."
            else -> fetchAiResponse(originalInput)
        }
    }

    private suspend fun handleDeviceControl(intent: IntentResult): String {
        val action = intent.action ?: return "I couldn't understand which device to control."
        val parts = action.split("_")
        if (parts.size < 2) return "Invalid device command."
        val command = parts[0]
        val device = parts.drop(1).joinToString("_")
        val turnOn = command == "turn" && parts.getOrNull(1) == "on"
        val result = deviceControlManager.toggle(device, turnOn)
        refreshDeviceState()
        return if (result.isSuccess) {
            intent.response ?: "${device.replace("_", " ").replaceFirstChar { it.uppercase() }} ${if (turnOn) "enabled" else "disabled"}."
        } else {
            "Couldn't ${if (turnOn) "enable" else "disable"} ${device.replace("_", " ")}. Check permissions."
        }
    }

    private suspend fun fetchWeather(city: String): String {
        _weatherState.value = _weatherState.value.copy(isLoading = true)
        return try {
            val weather = WeatherService.WeatherClient.instance.getCurrentWeather(city, "YOUR_OPENWEATHERMAP_API_KEY", "metric")
            _weatherState.value = WeatherUiState(
                city = weather.name, temperature = weather.main.temp,
                description = weather.weather.firstOrNull()?.description ?: "",
                humidity = weather.main.humidity, windSpeed = weather.wind.speed,
                pressure = weather.main.pressure, feelsLike = weather.main.feelsLike,
                icon = weather.weather.firstOrNull()?.icon ?: "", isLoading = false
            )
            navigateTo("weather")
            "Weather in ${weather.name}: ${weather.main.temp.toInt()} degrees, ${weather.weather.firstOrNull()?.description ?: "clear"}. Humidity ${weather.main.humidity}%. Feels like ${weather.main.feelsLike.toInt()} degrees."
        } catch (e: Exception) {
            _weatherState.value = _weatherState.value.copy(isLoading = false, error = e.message)
            "Couldn't fetch weather for $city. Check your API key."
        }
    }

    private suspend fun fetchNews(category: String): String {
        _newsState.value = _newsState.value.copy(isLoading = true, selectedCategory = category)
        return try {
            val news = NewsService.NewsClient.instance.getTopHeadlines(category, "en", "YOUR_GNEWS_API_KEY")
            _newsState.value = NewsUiState(articles = news.articles, isLoading = false, selectedCategory = category)
            navigateTo("news")
            if (news.articles.isEmpty()) "No news articles found."
            else "Top headlines: ${news.articles.take(3).mapIndexed { i, a -> "${i + 1}. ${a.title}" }.joinToString(". ")}"
        } catch (e: Exception) {
            _newsState.value = _newsState.value.copy(isLoading = false, error = e.message)
            "Couldn't fetch news. Check your API key."
        }
    }

    private suspend fun executeRoutine(name: String): String {
        return try {
            routineEngine.executeRoutine(name)
            navigateTo("routines")
            "${name.replace("_", " ").replaceFirstChar { it.uppercase() }} routine activated."
        } catch (e: Exception) { "Couldn't execute $name routine. ${e.message}" }
    }

    private fun readNotifications(): String {
        val notifs = _notifications.value.take(5)
        return if (notifs.isEmpty()) "No recent notifications."
        else "Recent notifications: ${notifs.mapIndexed { i, n -> "${i + 1}. ${n.appName}: ${n.title}" }.joinToString(". ")}"
    }

    private suspend fun fetchAiResponse(input: String): String {
        navigateTo("chat")
        return try {
            val history = _messages.value.takeLast(10).map {
                com.mahi.assistant.ai.GeminiModels.ChatMessage(role = it.role.name.lowercase(), content = it.content)
            }
            aiEngine.sendMessage(input, history)
        } catch (e: Exception) { "Technical difficulty. Please try again." }
    }

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
            deviceControlManager.toggle(deviceName, !current)
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
