# MAHI - Ultra-Futuristic Android AI Assistant

> Inspired by Jarvis from Iron Man | Built with Kotlin + Jetpack Compose

---

## 🚀 Quick Start - How to Build the APK

### Prerequisites
1. **Android Studio** (latest version): https://developer.android.com/studio
2. **JDK 17** (included with Android Studio)
3. **Internet connection** (for Gradle dependency downloads)

### Build Steps

#### Method 1: Android Studio (Recommended)
1. Download and install **Android Studio** from https://developer.android.com/studio
2. Open Android Studio → **Open an Existing Project**
3. Navigate to this `MAHI-Android` folder and select it
4. Wait for Gradle sync to complete (first time may take 5-10 minutes)
5. Click **Build** → **Build Bundle(s) / APK(s)** → **Build APK(s)**
6. The APK will be at: `app/build/outputs/apk/debug/app-debug.apk`
7. Transfer the APK to your Android phone and install it

#### Method 2: Command Line
```bash
# If you have Gradle installed:
gradle wrapper
./gradlew assembleDebug

# APK location:
# app/build/outputs/apk/debug/app-debug.apk
```

---

## 🔑 Required API Keys (All FREE!)

Before using MAHI's full features, add your free API keys in the **Settings** screen:

| Service | Get Free Key | Purpose |
|---------|-------------|---------|
| **Gemini** | https://aistudio.google.com/apikey | AI Chat & Conversation |
| **OpenWeatherMap** | https://openweathermap.org/api | Weather Forecasts |
| **GNews** | https://gnews.io | News Headlines |
| **Porcupine** | https://picovoice.ai | Wake Word Detection |

> All APIs have generous free tiers. No paid subscriptions needed!

---

## ✨ Features

### 🎙️ Voice Assistant
- Wake word detection ("Hey Mahi!")
- Speech-to-text recognition
- Text-to-speech responses
- Hands-free operation

### 📱 Phone Control
- Flashlight toggle
- WiFi on/off
- Bluetooth on/off
- Brightness & volume control
- Ringer mode (silent/vibrate/normal)
- Auto-rotate toggle
- Mobile data & hotspot
- Do Not Disturb mode
- Battery saver

### 💬 Smart AI Chat
- Gemini 1.5 Flash (free tier)
- Context-aware conversations
- Intent classification
- Natural language device control

### 🌤️ Weather & 📰 News
- Real-time weather from OpenWeatherMap
- Multi-category news from GNews
- Voice-read weather & news briefings

### 📺 YouTube Integration
- "Play [song] on YouTube"
- "Search YouTube for [topic]"

### 🤖 Automation Routines
- Good Morning / Good Night routines
- Meeting Mode / Focus Mode / Workout Mode
- Custom routine creation

### 🔔 Notification Reader
- Reads incoming notifications aloud
- Reply to messages via voice

### 🔮 Floating Assistant
- Always-on-top floating orb
- Quick voice/text commands from any app
- Draggable & collapsible

---

## 🏗️ Project Architecture

```
com.mahi.assistant/
├── MahiApplication.kt         # Hilt Application
├── MainActivity.kt            # Entry point
├── ai/                        # AI Engine
│   ├── AiConversationEngine.kt
│   ├── GeminiApiService.kt
│   ├── GeminiModels.kt
│   └── IntentClassifier.kt
├── api/                       # External APIs
│   ├── WeatherService.kt
│   └── NewsService.kt
├── automation/                # Routines
│   └── RoutineEngine.kt
├── control/                   # Device Control
│   └── DeviceControlManager.kt
├── data/
│   ├── local/                 # Room DB
│   │   ├── MahiDatabase.kt
│   │   ├── MessageDao.kt / MessageEntity.kt
│   │   ├── RoutineDao.kt / RoutineEntity.kt
│   │   └── DeviceStateDao.kt / DeviceStateEntity.kt
│   ├── model/                 # Data Models
│   └── repository/            # Repositories
├── di/                        # Hilt Modules
│   ├── DatabaseModule.kt
│   ├── NetworkModule.kt
│   ├── VoiceModule.kt
│   └── AiModule.kt
├── receiver/                  # Broadcast Receivers
│   └── BootReceiver.kt
├── service/                   # Android Services
│   ├── WakeWordService.kt
│   ├── FloatingAssistantService.kt
│   ├── MahiNotificationListenerService.kt
│   └── MahiAccessibilityService.kt
├── voice/                     # Voice Engine
│   ├── VoiceRecognitionEngine.kt
│   ├── TextToSpeechEngine.kt
│   └── WakeWordDetector.kt
└── ui/                        # Jetpack Compose UI
    ├── MahiApp.kt
    ├── theme/                 # Iron Man/Jarvis Theme
    ├── components/            # Reusable Components
    ├── screens/               # All Screens
    ├── navigation/            # NavHost
    └── viewmodel/             # ViewModels
```

---

## 🎨 UI Theme - Iron Man Jarvis Style

- **Primary**: Neon Cyan `#37DCF2`
- **Background**: Deep Space `#0A0E17`
- **Surface**: Dark Panel `#111827`
- **Accent**: Electric Purple `#8B5CF6`
- **Glow effects**, scanlines, holographic overlays
- **Animated orb** with state-based visual changes

---

## ⚠️ Required Permissions

MAHI needs these permissions (all requested at runtime):

| Permission | Purpose |
|-----------|---------|
| RECORD_AUDIO | Voice recognition |
| INTERNET | API calls |
| CAMERA | Flashlight control |
| ACCESS_WIFI_STATE / CHANGE_WIFI_STATE | WiFi toggle |
| BLUETOOTH_CONNECT | Bluetooth toggle |
| READ_CONTACTS / CALL_PHONE | Phone calls |
| SEND_SMS / READ_SMS | SMS features |
| SYSTEM_ALERT_WINDOW | Floating assistant |
| FOREGROUND_SERVICE | Background services |
| ACCESS_NOTIFICATION_POLICY | Notification reader |

---

## 🛠️ Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM |
| DI | Hilt |
| Database | Room |
| Networking | Retrofit + OkHttp |
| AI | Gemini 1.5 Flash (Free) |
| Speech | Android SpeechRecognizer + TTS |
| Wake Word | Porcupine (Free Tier) |
| Animations | Lottie + Compose Animations |

---

**Made with ❤️ by MAHI Team**
