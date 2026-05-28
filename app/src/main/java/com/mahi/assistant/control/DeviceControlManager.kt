package com.mahi.assistant.control

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.mahi.assistant.data.local.DeviceStateDao
import com.mahi.assistant.data.local.DeviceStateEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Represents the on/off state and optional numeric value of a controllable device feature.
 * Internal state model used by [DeviceControlManager]. Not to be confused with
 * [com.mahi.assistant.data.model.DeviceState] (the UI model) or
 * [DeviceStateEntity] (the Room entity).
 */
data class DeviceFeatureState(
    val isOn: Boolean = false,
    val value: Int = 0
)

/**
 * Central manager for controlling phone hardware features and system settings.
 *
 * Every public method returns [Result] so callers can handle permission denials,
 * missing hardware, or API failures gracefully. A [StateFlow] of all current
 * device feature states is published so UI layers can react in real-time.
 *
 * State changes are also persisted to Room via [DeviceStateDao] so they survive
 * process restarts.
 */
@Singleton
class DeviceControlManager @Inject constructor(
    private val context: Context,
    private val deviceStateDao: DeviceStateDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // ──────────────────────────────────────────────
    // System service handles
    // ──────────────────────────────────────────────
    private val cameraManager: CameraManager by lazy {
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }
    private val audioManager: AudioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    private val wifiManager: WifiManager by lazy {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }
    private val bluetoothManager: BluetoothManager by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        bluetoothManager.adapter
    }
    private val connectivityManager: ConnectivityManager by lazy {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }
    private val powerManager: PowerManager by lazy {
        context.getSystemService(Context.POWER_SERVICE) as PowerManager
    }
    private val notificationManager: android.app.NotificationManager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
    }

    // ──────────────────────────────────────────────
    // Device states
    // ──────────────────────────────────────────────
    private val _deviceStates = MutableStateFlow<Map<String, DeviceFeatureState>>(emptyMap())
    val deviceStates: StateFlow<Map<String, DeviceFeatureState>> = _deviceStates.asStateFlow()

    private var flashCameraId: String? = null

    init {
        refreshAllStates()
        discoverFlashCamera()
    }

    // ──────────────────────────────────────────────
    // Public toggle entry-point
    // ──────────────────────────────────────────────

    /**
     * Toggle a named device feature. Supported names (case-insensitive):
     * flashlight, wifi, bluetooth, brightness, ringer, volume,
     * mobile_data, auto_rotate, hotspot, screen_timeout, battery_saver, dnd
     *
     * For features that require a numeric value (brightness, volume, screen_timeout),
     * pass it via [value]; otherwise it defaults to a sensible mid-point.
     */
    fun toggle(deviceName: String, value: Int? = null): Result<Boolean> {
        return when (deviceName.lowercase().trim()) {
            "flashlight", "torch" -> toggleFlashlight()
            "wifi" -> toggleWifi()
            "bluetooth", "bt" -> toggleBluetooth()
            "brightness" -> setBrightness(value ?: 128)
            "ringer" -> cycleRingerMode()
            "volume" -> setVolume(value ?: 7)
            "mobile_data", "data" -> toggleMobileData()
            "auto_rotate", "autorotate", "rotation" -> toggleAutoRotate()
            "hotspot" -> toggleHotspot()
            "screen_timeout", "timeout" -> setScreenTimeout(value ?: 30000)
            "battery_saver", "saver" -> toggleBatterySaver()
            "dnd", "do_not_disturb" -> toggleDnd()
            else -> Result.failure(IllegalArgumentException("Unknown device: $deviceName"))
        }
    }

    // ──────────────────────────────────────────────
    // 1. Flashlight
    // ──────────────────────────────────────────────

    private fun discoverFlashCamera() {
        try {
            flashCameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (_: Exception) {
            flashCameraId = null
        }
    }

    fun toggleFlashlight(): Result<Boolean> {
        return try {
            val cameraId = flashCameraId ?: run {
                discoverFlashCamera()
                flashCameraId
            }
            if (cameraId == null) {
                return Result.failure(IllegalStateException("No camera with flash available"))
            }
            val currentState = _deviceStates.value["flashlight"]?.isOn ?: false
            val newState = !currentState
            cameraManager.setTorchMode(cameraId, newState)
            updateState("flashlight", DeviceFeatureState(isOn = newState))
            Result.success(newState)
        } catch (e: SecurityException) {
            Result.failure(SecurityException("Camera permission required: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun setFlashlight(on: Boolean): Result<Boolean> {
        return try {
            val cameraId = flashCameraId ?: run {
                discoverFlashCamera()
                flashCameraId
            }
            if (cameraId == null) {
                return Result.failure(IllegalStateException("No camera with flash available"))
            }
            cameraManager.setTorchMode(cameraId, on)
            updateState("flashlight", DeviceFeatureState(isOn = on))
            Result.success(on)
        } catch (e: SecurityException) {
            Result.failure(SecurityException("Camera permission required: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ──────────────────────────────────────────────
    // 2. WiFi
    // ──────────────────────────────────────────────

    fun toggleWifi(): Result<Boolean> {
        return try {
            val currentState = wifiManager.isWifiEnabled
            val newState = !currentState
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // On Android 10+ we cannot toggle WiFi programmatically.
                // Open the WiFi settings panel instead.
                val intent = Intent(Settings.Panel.ACTION_WIFI).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Result.success(true)
            } else {
                @Suppress("DEPRECATION")
                wifiManager.isWifiEnabled = newState
                updateState("wifi", DeviceFeatureState(isOn = newState))
                Result.success(newState)
            }
        } catch (e: SecurityException) {
            Result.failure(SecurityException("WiFi permission required: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun setWifi(enabled: Boolean): Result<Boolean> {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val intent = Intent(Settings.Panel.ACTION_WIFI).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Result.success(true)
            } else {
                @Suppress("DEPRECATION")
                wifiManager.isWifiEnabled = enabled
                updateState("wifi", DeviceFeatureState(isOn = enabled))
                Result.success(enabled)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ──────────────────────────────────────────────
    // 3. Bluetooth
    // ──────────────────────────────────────────────

    fun toggleBluetooth(): Result<Boolean> {
        return try {
            val adapter = bluetoothAdapter ?: return Result.failure(
                IllegalStateException("Bluetooth not available on this device")
            )
            val newState = !adapter.isEnabled
            if (newState) {
                adapter.enable()
            } else {
                adapter.disable()
            }
            updateState("bluetooth", DeviceFeatureState(isOn = newState))
            Result.success(newState)
        } catch (e: SecurityException) {
            Result.failure(SecurityException("Bluetooth permission required: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun setBluetooth(enabled: Boolean): Result<Boolean> {
        return try {
            val adapter = bluetoothAdapter ?: return Result.failure(
                IllegalStateException("Bluetooth not available on this device")
            )
            if (enabled) adapter.enable() else adapter.disable()
            updateState("bluetooth", DeviceFeatureState(isOn = enabled))
            Result.success(enabled)
        } catch (e: SecurityException) {
            Result.failure(SecurityException("Bluetooth permission required: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ──────────────────────────────────────────────
    // 4. Brightness
    // ──────────────────────────────────────────────

    fun setBrightness(level: Int): Result<Boolean> {
        return try {
            val clamped = level.coerceIn(0, 255)
            if (!Settings.System.canWrite(context)) {
                return Result.failure(SecurityException("WRITE_SETTINGS permission required to change brightness"))
            }
            // Ensure auto-brightness is off so manual value takes effect
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                clamped
            )
            updateState("brightness", DeviceFeatureState(isOn = true, value = clamped))
            Result.success(true)
        } catch (e: SecurityException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getBrightness(): Int {
        return try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        } catch (_: Exception) {
            128
        }
    }

    // ──────────────────────────────────────────────
    // 5. Ringer Mode
    // ──────────────────────────────────────────────

    fun setRingerMode(mode: Int): Result<Boolean> {
        return try {
            if (mode !in listOf(
                    AudioManager.RINGER_MODE_SILENT,
                    AudioManager.RINGER_MODE_VIBRATE,
                    AudioManager.RINGER_MODE_NORMAL
                )
            ) {
                return Result.failure(IllegalArgumentException("Invalid ringer mode: $mode"))
            }
            audioManager.ringerMode = mode
            val modeName = when (mode) {
                AudioManager.RINGER_MODE_SILENT -> "silent"
                AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
                else -> "normal"
            }
            updateState("ringer", DeviceFeatureState(isOn = mode != AudioManager.RINGER_MODE_SILENT, value = mode))
            Result.success(true)
        } catch (e: SecurityException) {
            Result.failure(SecurityException("Notification policy access required: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun cycleRingerMode(): Result<Boolean> {
        val current = audioManager.ringerMode
        val next = when (current) {
            AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
            AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
            else -> AudioManager.RINGER_MODE_NORMAL
        }
        return setRingerMode(next)
    }

    fun setSilentMode(): Result<Boolean> = setRingerMode(AudioManager.RINGER_MODE_SILENT)
    fun setVibrateMode(): Result<Boolean> = setRingerMode(AudioManager.RINGER_MODE_VIBRATE)
    fun setNormalMode(): Result<Boolean> = setRingerMode(AudioManager.RINGER_MODE_NORMAL)

    // ──────────────────────────────────────────────
    // 6. Volume
    // ──────────────────────────────────────────────

    fun setVolume(level: Int, streamType: Int = AudioManager.STREAM_MUSIC): Result<Boolean> {
        return try {
            val maxVolume = audioManager.getStreamMaxVolume(streamType)
            val clamped = level.coerceIn(0, maxVolume)
            audioManager.setStreamVolume(streamType, clamped, 0)
            updateState("volume", DeviceFeatureState(isOn = clamped > 0, value = clamped))
            Result.success(true)
        } catch (e: SecurityException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getVolume(streamType: Int = AudioManager.STREAM_MUSIC): Int {
        return audioManager.getStreamVolume(streamType)
    }

    fun getMaxVolume(streamType: Int = AudioManager.STREAM_MUSIC): Int {
        return audioManager.getStreamMaxVolume(streamType)
    }

    // ──────────────────────────────────────────────
    // 7. Mobile Data
    // ──────────────────────────────────────────────

    fun toggleMobileData(): Result<Boolean> {
        return try {
            val currentState = isMobileDataEnabled()
            val newState = !currentState

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val intent = Intent(Settings.ACTION_DATA_USAGE_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Result.success(true)
            } else {
                @Suppress("DEPRECATION")
                val method = ConnectivityManager::class.java.getDeclaredMethod(
                    "setMobileDataEnabled", Boolean::class.javaPrimitiveType
                )
                method.isAccessible = true
                method.invoke(connectivityManager, newState)
                updateState("mobile_data", DeviceFeatureState(isOn = newState))
                Result.success(newState)
            }
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_DATA_ROAMING_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Result.success(true)
            } catch (e2: Exception) {
                Result.failure(e2)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun isMobileDataEnabled(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                connectivityManager.activeNetworkInfo?.type == ConnectivityManager.TYPE_MOBILE
            } else {
                val method = ConnectivityManager::class.java.getDeclaredMethod("getMobileDataEnabled")
                method.isAccessible = true
                method.invoke(connectivityManager) as? Boolean ?: false
            }
        } catch (_: Exception) {
            false
        }
    }

    // ──────────────────────────────────────────────
    // 8. Auto-Rotate
    // ──────────────────────────────────────────────

    fun toggleAutoRotate(): Result<Boolean> {
        return try {
            if (!Settings.System.canWrite(context)) {
                return Result.failure(SecurityException("WRITE_SETTINGS permission required"))
            }
            val current = Settings.System.getInt(
                context.contentResolver,
                Settings.System.ACCELEROMETER_ROTATION,
                0
            )
            val newState = current == 0
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.ACCELEROMETER_ROTATION,
                if (newState) 1 else 0
            )
            updateState("auto_rotate", DeviceFeatureState(isOn = newState))
            Result.success(newState)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun setAutoRotate(enabled: Boolean): Result<Boolean> {
        return try {
            if (!Settings.System.canWrite(context)) {
                return Result.failure(SecurityException("WRITE_SETTINGS permission required"))
            }
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.ACCELEROMETER_ROTATION,
                if (enabled) 1 else 0
            )
            updateState("auto_rotate", DeviceFeatureState(isOn = enabled))
            Result.success(enabled)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ──────────────────────────────────────────────
    // 9. Hotspot (Wi-Fi Tethering)
    // ──────────────────────────────────────────────

    fun toggleHotspot(): Result<Boolean> {
        return try {
            val currentState = isHotspotEnabled()
            val newState = !currentState
            val method = WifiManager::class.java.getDeclaredMethod(
                "setWifiApEnabled",
                android.net.wifi.WifiConfiguration::class.java,
                Boolean::class.javaPrimitiveType
            )
            method.isAccessible = true
            method.invoke(wifiManager, null, newState)
            updateState("hotspot", DeviceFeatureState(isOn = newState))
            Result.success(newState)
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Result.success(true)
            } catch (e2: Exception) {
                Result.failure(e2)
            }
        }
    }

    private fun isHotspotEnabled(): Boolean {
        return try {
            val method = WifiManager::class.java.getDeclaredMethod("isWifiApEnabled")
            method.isAccessible = true
            method.invoke(wifiManager) as? Boolean ?: false
        } catch (_: Exception) {
            false
        }
    }

    // ──────────────────────────────────────────────
    // 10. Screen Timeout
    // ──────────────────────────────────────────────

    fun setScreenTimeout(timeoutMs: Int): Result<Boolean> {
        return try {
            if (!Settings.System.canWrite(context)) {
                return Result.failure(SecurityException("WRITE_SETTINGS permission required"))
            }
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_OFF_TIMEOUT,
                timeoutMs
            )
            updateState("screen_timeout", DeviceFeatureState(isOn = true, value = timeoutMs))
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getScreenTimeout(): Int {
        return try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT)
        } catch (_: Exception) {
            30000
        }
    }

    // ──────────────────────────────────────────────
    // 11. Battery Saver
    // ──────────────────────────────────────────────

    fun toggleBatterySaver(): Result<Boolean> {
        return try {
            val currentState = powerManager.isPowerSaveMode
            val newState = !currentState
            powerManager.isPowerSaveMode = newState
            updateState("battery_saver", DeviceFeatureState(isOn = newState))
            Result.success(newState)
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Result.success(true)
            } catch (e2: Exception) {
                Result.failure(e2)
            }
        }
    }

    fun setBatterySaver(enabled: Boolean): Result<Boolean> {
        return try {
            powerManager.isPowerSaveMode = enabled
            updateState("battery_saver", DeviceFeatureState(isOn = enabled))
            Result.success(enabled)
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Result.success(true)
            } catch (e2: Exception) {
                Result.failure(e2)
            }
        }
    }

    // ──────────────────────────────────────────────
    // 12. Do Not Disturb
    // ──────────────────────────────────────────────

    fun toggleDnd(): Result<Boolean> {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!notificationManager.isNotificationPolicyAccessGranted) {
                    return Result.failure(SecurityException("Notification policy access not granted"))
                }
                val currentFilter = notificationManager.currentInterruptionFilter
                val newFilter = if (currentFilter == android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
                    android.app.NotificationManager.INTERRUPTION_FILTER_ALL
                } else {
                    android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY
                }
                notificationManager.setInterruptionFilter(newFilter)
                val isDnd = newFilter == android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY
                updateState("dnd", DeviceFeatureState(isOn = isDnd))
                Result.success(isDnd)
            } else {
                Result.failure(IllegalStateException("DND requires Android 6.0+"))
            }
        } catch (e: SecurityException) {
            Result.failure(SecurityException("Notification policy access required: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun setDnd(enabled: Boolean): Result<Boolean> {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!notificationManager.isNotificationPolicyAccessGranted) {
                    return Result.failure(SecurityException("Notification policy access not granted"))
                }
                val filter = if (enabled) {
                    android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY
                } else {
                    android.app.NotificationManager.INTERRUPTION_FILTER_ALL
                }
                notificationManager.setInterruptionFilter(filter)
                updateState("dnd", DeviceFeatureState(isOn = enabled))
                Result.success(enabled)
            } else {
                Result.failure(IllegalStateException("DND requires Android 6.0+"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ──────────────────────────────────────────────
    // State helpers
    // ──────────────────────────────────────────────

    private fun updateState(device: String, state: DeviceFeatureState) {
        scope.launch {
            val current = _deviceStates.value.toMutableMap()
            current[device] = state
            _deviceStates.value = current

            // Persist to Room for crash-survival
            try {
                deviceStateDao.toggleDevice(device, state.isOn)
            } catch (_: Exception) {
                // Silently ignore DB errors; the StateFlow is the primary source
            }
        }
    }

    /**
     * Reads all current device states from the system and publishes them.
     */
    fun refreshAllStates() {
        scope.launch {
            val states = mutableMapOf<String, DeviceFeatureState>()

            // Flashlight — Android has no public API to query; track from our own state
            states["flashlight"] = _deviceStates.value["flashlight"] ?: DeviceFeatureState(isOn = false)

            // WiFi
            states["wifi"] = DeviceFeatureState(isOn = wifiManager.isWifiEnabled)

            // Bluetooth
            states["bluetooth"] = DeviceFeatureState(isOn = bluetoothAdapter?.isEnabled == true)

            // Brightness
            states["brightness"] = DeviceFeatureState(
                isOn = true,
                value = getBrightness()
            )

            // Ringer
            states["ringer"] = DeviceFeatureState(
                isOn = audioManager.ringerMode != AudioManager.RINGER_MODE_SILENT,
                value = audioManager.ringerMode
            )

            // Volume
            states["volume"] = DeviceFeatureState(
                isOn = getVolume() > 0,
                value = getVolume()
            )

            // Mobile data
            states["mobile_data"] = DeviceFeatureState(isOn = isMobileDataEnabled())

            // Auto-rotate
            val rotationEnabled = try {
                Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
            } catch (_: Exception) {
                false
            }
            states["auto_rotate"] = DeviceFeatureState(isOn = rotationEnabled)

            // Hotspot
            states["hotspot"] = DeviceFeatureState(isOn = isHotspotEnabled())

            // Screen timeout
            states["screen_timeout"] = DeviceFeatureState(isOn = true, value = getScreenTimeout())

            // Battery saver
            states["battery_saver"] = DeviceFeatureState(isOn = powerManager.isPowerSaveMode)

            // DND
            val dndOn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                notificationManager.currentInterruptionFilter != android.app.NotificationManager.INTERRUPTION_FILTER_ALL
            } else false
            states["dnd"] = DeviceFeatureState(isOn = dndOn)

            _deviceStates.value = states
        }
    }

    /**
     * Returns a human-readable description of the current ringer mode.
     */
    fun getRingerModeName(): String {
        return when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> "Silent"
            AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"
            AudioManager.RINGER_MODE_NORMAL -> "Normal"
            else -> "Unknown"
        }
    }
}
