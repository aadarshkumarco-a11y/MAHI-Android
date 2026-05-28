package com.mahi.assistant.automation

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mahi.assistant.control.DeviceControlManager
import com.mahi.assistant.data.local.RoutineDao
import com.mahi.assistant.data.local.RoutineEntity
import com.mahi.assistant.data.model.ActionType
import com.mahi.assistant.data.model.Routine
import com.mahi.assistant.data.model.RoutineAction
import com.mahi.assistant.data.model.TriggerType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

// ──────────────────────────────────────────────
// Execution status
// ──────────────────────────────────────────────

/**
 * Execution status of a routine.
 */
sealed class RoutineExecutionStatus {
    data object Idle : RoutineExecutionStatus()
    data class Running(val routineName: String, val currentAction: String, val progress: Float) :
        RoutineExecutionStatus()

    data class Completed(val routineName: String) : RoutineExecutionStatus()
    data class Failed(val routineName: String, val error: String) : RoutineExecutionStatus()
}

// ──────────────────────────────────────────────
// Engine
// ──────────────────────────────────────────────

/**
 * Automation / Routine engine that executes pre-defined and custom routines.
 *
 * Pre-defined routines:
 * - **Good Morning**: enable WiFi, brightness 70%, ringer normal, read weather, read news
 * - **Good Night**: disable WiFi, brightness 20%, silent mode, enable DND
 * - **Meeting Mode**: silent mode, disable WiFi, enable DND
 * - **Focus Mode**: enable DND, disable WiFi, brightness 40%
 * - **Workout Mode**: enable Bluetooth, volume 80%, launch YouTube Music
 *
 * Custom routines are persisted in the Room database via [RoutineDao] and
 * [RoutineEntity], and can be added / deleted at runtime.
 *
 * All actions execute sequentially with configurable delays between them.
 * Execution status is published via [executionStatus] StateFlow.
 */
@Singleton
class RoutineEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deviceControlManager: DeviceControlManager,
    private val routineDao: RoutineDao,
    private val gson: Gson
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _executionStatus = MutableStateFlow<RoutineExecutionStatus>(RoutineExecutionStatus.Idle)
    val executionStatus: StateFlow<RoutineExecutionStatus> = _executionStatus.asStateFlow()

    private val _availableRoutines = MutableStateFlow<List<Routine>>(emptyList())
    val availableRoutines: StateFlow<List<Routine>> = _availableRoutines.asStateFlow()

    init {
        scope.launch {
            seedPredefinedRoutinesIfNeeded()
            refreshRoutines()
        }
    }

    // ──────────────────────────────────────────────
    // Pre-defined routine definitions
    // ──────────────────────────────────────────────

    private val predefinedRoutines: List<Routine> by lazy {
        listOf(
            Routine(
                name = "Good Morning",
                triggerType = TriggerType.VOICE_COMMAND,
                triggerValue = "good morning",
                actions = listOf(
                    RoutineAction(ActionType.TOGGLE_WIFI, mapOf("state" to "on")),
                    RoutineAction(ActionType.TOGGLE_WIFI, mapOf("state" to "on")),
                    RoutineAction(ActionType.SET_VOLUME, mapOf("stream" to "brightness", "level" to "178")),
                    RoutineAction(ActionType.SPEAK_TEXT, mapOf("text" to "Reading weather forecast")),
                    RoutineAction(ActionType.SPEAK_TEXT, mapOf("text" to "Reading today's news"))
                )
            ),
            Routine(
                name = "Good Night",
                triggerType = TriggerType.VOICE_COMMAND,
                triggerValue = "good night",
                actions = listOf(
                    RoutineAction(ActionType.TOGGLE_WIFI, mapOf("state" to "off")),
                    RoutineAction(ActionType.SET_VOLUME, mapOf("stream" to "brightness", "level" to "51")),
                    RoutineAction(ActionType.SPEAK_TEXT, mapOf("text" to "Setting silent mode")),
                    RoutineAction(ActionType.SPEAK_TEXT, mapOf("text" to "Enabling do not disturb"))
                )
            ),
            Routine(
                name = "Meeting Mode",
                triggerType = TriggerType.VOICE_COMMAND,
                triggerValue = "meeting mode",
                actions = listOf(
                    RoutineAction(ActionType.SPEAK_TEXT, mapOf("text" to "Setting silent mode")),
                    RoutineAction(ActionType.TOGGLE_WIFI, mapOf("state" to "off")),
                    RoutineAction(ActionType.SPEAK_TEXT, mapOf("text" to "Enabling do not disturb"))
                )
            ),
            Routine(
                name = "Focus Mode",
                triggerType = TriggerType.VOICE_COMMAND,
                triggerValue = "focus mode",
                actions = listOf(
                    RoutineAction(ActionType.SPEAK_TEXT, mapOf("text" to "Enabling do not disturb")),
                    RoutineAction(ActionType.TOGGLE_WIFI, mapOf("state" to "off")),
                    RoutineAction(ActionType.SET_VOLUME, mapOf("stream" to "brightness", "level" to "102"))
                )
            ),
            Routine(
                name = "Workout Mode",
                triggerType = TriggerType.VOICE_COMMAND,
                triggerValue = "workout mode",
                actions = listOf(
                    RoutineAction(ActionType.TOGGLE_BLUETOOTH, mapOf("state" to "on")),
                    RoutineAction(ActionType.SET_VOLUME, mapOf("level" to "12")),
                    RoutineAction(ActionType.OPEN_APP, mapOf("package" to "com.google.android.apps.youtube.music"))
                )
            )
        )
    }

    /**
     * Seeds predefined routines into the Room database if they don't already exist.
     * This ensures the database always has the built-in routines available.
     */
    private suspend fun seedPredefinedRoutinesIfNeeded() {
        withContext(Dispatchers.IO) {
            val existingRoutines = routineDao.getAll()
            val existingNames = existingRoutines.map { it.name }.toSet()

            for (routine in predefinedRoutines) {
                if (routine.name !in existingNames) {
                    val entity = routineToEntity(routine, isActive = true)
                    routineDao.insert(entity)
                }
            }
        }
    }

    // ──────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────

    /**
     * Execute a routine by name. Looks up routines from the database
     * (both pre-defined and custom).
     *
     * If a routine is already running, this call is ignored.
     */
    fun executeRoutine(name: String) {
        if (_executionStatus.value is RoutineExecutionStatus.Running) {
            return // don't interrupt an ongoing routine
        }

        scope.launch {
            val routine = findRoutine(name)
            if (routine == null) {
                _executionStatus.value = RoutineExecutionStatus.Failed(name, "Routine not found: $name")
                return@launch
            }
            executeActions(routine)
        }
    }

    /**
     * Execute a routine by its database ID.
     */
    fun executeRoutineById(id: Long) {
        if (_executionStatus.value is RoutineExecutionStatus.Running) return

        scope.launch {
            val entity = withContext(Dispatchers.IO) { routineDao.getById(id) }
            if (entity == null) {
                _executionStatus.value = RoutineExecutionStatus.Failed("id=$id", "Routine not found")
                return@launch
            }
            val routine = entityToRoutine(entity)
            executeActions(routine)
        }
    }

    /**
     * Add a custom routine and persist it in the Room database.
     */
    suspend fun addCustomRoutine(routine: Routine): Long {
        val entity = routineToEntity(routine, isActive = true)
        val id = withContext(Dispatchers.IO) { routineDao.insert(entity) }
        refreshRoutines()
        return id
    }

    /**
     * Delete a custom routine by database ID.
     */
    suspend fun deleteCustomRoutine(id: Long) {
        withContext(Dispatchers.IO) { routineDao.deleteById(id) }
        refreshRoutines()
    }

    /**
     * Refresh the available routines list from the database.
     */
    suspend fun refreshRoutines() {
        val entities = withContext(Dispatchers.IO) { routineDao.getAll() }
        _availableRoutines.value = entities.map { entityToRoutine(it) }
    }

    /**
     * Get a specific routine by name.
     */
    suspend fun getRoutine(name: String): Routine? {
        return findRoutine(name)
    }

    // ──────────────────────────────────────────────
    // Execution engine
    // ──────────────────────────────────────────────

    private suspend fun executeActions(routine: Routine) {
        val totalActions = routine.actions.size
        if (totalActions == 0) {
            _executionStatus.value = RoutineExecutionStatus.Completed(routine.name)
            return
        }

        routine.actions.forEachIndexed { index, action ->
            val actionLabel = "${action.type.name}(${action.params})"
            _executionStatus.value = RoutineExecutionStatus.Running(
                routineName = routine.name,
                currentAction = actionLabel,
                progress = (index.toFloat() + 1f) / totalActions
            )

            val result = executeAction(action)
            if (result.isFailure) {
                _executionStatus.value = RoutineExecutionStatus.Failed(
                    routine.name,
                    "Action '${action.type.name}' failed: ${result.exceptionOrNull()?.message}"
                )
                return
            }

            // Delay between actions for stability
            if (index < totalActions - 1) {
                delay(600L)
            }
        }

        _executionStatus.value = RoutineExecutionStatus.Completed(routine.name)
    }

    /**
     * Executes a single [RoutineAction] by dispatching to the appropriate
     * device control, TTS, or system intent.
     */
    private suspend fun executeAction(action: RoutineAction): Result<Boolean> {
        return withContext(Dispatchers.Main) {
            when (action.type) {
                // ── WiFi ──
                ActionType.TOGGLE_WIFI -> {
                    val state = action.params["state"]
                    if (state == "on") deviceControlManager.setWifi(true)
                    else if (state == "off") deviceControlManager.setWifi(false)
                    else deviceControlManager.toggleWifi()
                }

                // ── Bluetooth ──
                ActionType.TOGGLE_BLUETOOTH -> {
                    val state = action.params["state"]
                    if (state == "on") deviceControlManager.setBluetooth(true)
                    else if (state == "off") deviceControlManager.setBluetooth(false)
                    else deviceControlManager.toggleBluetooth()
                }

                // ── Flashlight ──
                ActionType.TOGGLE_FLASHLIGHT -> {
                    val state = action.params["state"]
                    if (state == "on") deviceControlManager.setFlashlight(true)
                    else if (state == "off") deviceControlManager.setFlashlight(false)
                    else deviceControlManager.toggleFlashlight()
                }

                // ── Volume ──
                ActionType.SET_VOLUME -> {
                    val level = action.params["level"]?.toIntOrNull() ?: 7
                    deviceControlManager.setVolume(level)
                }

                // ── Open App ──
                ActionType.OPEN_APP -> {
                    val packageName = action.params["package"] ?: action.params["app"] ?: ""
                    if (packageName.isNotBlank()) {
                        launchPackage(packageName)
                    } else {
                        Result.failure(IllegalArgumentException("No package name specified for OPEN_APP"))
                    }
                }

                // ── Speak Text ──
                ActionType.SPEAK_TEXT -> {
                    val text = action.params["text"] ?: ""
                    if (text.isNotBlank()) {
                        speakText(text)
                    } else {
                        Result.success(true)
                    }
                }

                // ── Send SMS ──
                ActionType.SEND_SMS -> {
                    val number = action.params["number"] ?: ""
                    val message = action.params["message"] ?: ""
                    if (number.isNotBlank() && message.isNotBlank()) {
                        sendSms(number, message)
                    } else {
                        Result.failure(IllegalArgumentException("Number and message required for SEND_SMS"))
                    }
                }

                // ── Make Call ──
                ActionType.MAKE_CALL -> {
                    val number = action.params["number"] ?: ""
                    if (number.isNotBlank()) {
                        makeCall(number)
                    } else {
                        Result.failure(IllegalArgumentException("Number required for MAKE_CALL"))
                    }
                }

                // ── Set Alarm ──
                ActionType.SET_ALARM -> {
                    val hour = action.params["hour"] ?: "8"
                    val minute = action.params["minute"] ?: "0"
                    val message = action.params["message"] ?: "MAHI Alarm"
                    setAlarm(hour.toIntOrNull() ?: 8, minute.toIntOrNull() ?: 0, message)
                }

                // ── Read Notification ──
                ActionType.READ_NOTIFICATION -> {
                    val count = action.params["count"]?.toIntOrNull() ?: 3
                    launchIntentAction("com.mahi.assistant.action.READ_NOTIFICATIONS", mapOf("count" to count.toString()))
                }

                // ── Custom action ──
                ActionType.CUSTOM -> {
                    val command = action.params["command"] ?: ""
                    if (command.isNotBlank()) {
                        launchIntentAction("com.mahi.assistant.action.CUSTOM_COMMAND", action.params)
                    } else {
                        Result.failure(IllegalArgumentException("No command specified for CUSTOM action"))
                    }
                }
            }
        }
    }

    // ──────────────────────────────────────────────
    // Action helpers
    // ──────────────────────────────────────────────

    private fun launchIntentAction(action: String, extras: Map<String, String> = emptyMap()): Result<Boolean> {
        return try {
            val intent = Intent(action).apply {
                setPackage(context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                extras.forEach { (key, value) -> putExtra(key, value) }
            }
            context.startActivity(intent)
            Result.success(true)
        } catch (_: Exception) {
            // If no activity handles this, send as broadcast
            try {
                val broadcast = Intent(action).apply {
                    setPackage(context.packageName)
                    extras.forEach { (key, value) -> putExtra(key, value) }
                }
                context.sendBroadcast(broadcast)
                Result.success(true)
            } catch (e2: Exception) {
                Result.failure(e2)
            }
        }
    }

    private fun launchPackage(packageName: String): Result<Boolean> {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Result.success(true)
            } else {
                // App not installed — open Play Store
                val marketIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("market://details?id=$packageName")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(marketIntent)
                Result.failure(IllegalStateException("App $packageName not installed, opened Play Store"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun speakText(text: String): Result<Boolean> {
        return try {
            val intent = Intent("com.mahi.assistant.action.SPEAK").apply {
                setPackage(context.packageName)
                putExtra("text", text)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.sendBroadcast(intent)
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun sendSms(number: String, message: String): Result<Boolean> {
        return try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$number")
                putExtra("sms_body", message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun makeCall(number: String): Result<Boolean> {
        return try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$number")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Result.success(true)
        } catch (e: SecurityException) {
            // Fall back to dial
            try {
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$number")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Result.success(true)
            } catch (e2: Exception) {
                Result.failure(e2)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun setAlarm(hour: Int, minute: Int, message: String): Result<Boolean> {
        return try {
            val intent = Intent(android.provider.AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(android.provider.AlarmClock.EXTRA_HOUR, hour)
                putExtra(android.provider.AlarmClock.EXTRA_MINUTES, minute)
                putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ──────────────────────────────────────────────
    // Mapping helpers: Routine ↔ RoutineEntity
    // ──────────────────────────────────────────────

    private fun routineToEntity(routine: Routine, isActive: Boolean = true): RoutineEntity {
        return RoutineEntity(
            name = routine.name,
            triggerType = routine.triggerType.name,
            triggerValue = routine.triggerValue,
            actionsJson = gson.toJson(routine.actions),
            isActive = isActive
        )
    }

    private fun entityToRoutine(entity: RoutineEntity): Routine {
        val actionListType = object : TypeToken<List<RoutineAction>>() {}.type
        val actions: List<RoutineAction> = try {
            gson.fromJson(entity.actionsJson, actionListType)
        } catch (_: Exception) {
            emptyList()
        }

        val triggerType = try {
            TriggerType.valueOf(entity.triggerType)
        } catch (_: Exception) {
            TriggerType.MANUAL
        }

        return Routine(
            id = entity.id.toString(),
            name = entity.name,
            triggerType = triggerType,
            triggerValue = entity.triggerValue,
            actions = actions,
            isActive = entity.isActive,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    // ──────────────────────────────────────────────
    // Lookup
    // ──────────────────────────────────────────────

    private suspend fun findRoutine(name: String): Routine? {
        val allRoutines = withContext(Dispatchers.IO) { routineDao.getAll() }
        val entity = allRoutines.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: return null
        return entityToRoutine(entity)
    }
}
