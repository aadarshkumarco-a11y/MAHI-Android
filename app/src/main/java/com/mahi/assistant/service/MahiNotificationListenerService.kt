package com.mahi.assistant.service

import android.app.Notification
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import android.util.Log
import com.mahi.assistant.data.model.NotificationItem
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * Notification listener service that captures all incoming notifications,
 * stores them in a [StateFlow], and provides methods to read them aloud
 * via TTS, reply to them, or dismiss them.
 *
 * Must be enabled by the user in Settings → Apps → Special access →
 * Notification access.
 */
@AndroidEntryPoint
class MahiNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "MahiNotifListener"
        private const val MAX_NOTIFICATIONS = 50
        private const val MAHI_PACKAGE = "com.mahi.assistant"

        /** Shared instance so other components can access the flow easily. */
        @Volatile
        var instance: MahiNotificationListenerService? = null
            private set
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _notifications = MutableStateFlow<List<NotificationItem>>(emptyList())
    val notifications: StateFlow<List<NotificationItem>> = _notifications.asStateFlow()

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    // ──────────────────────────────────────────────
    // Service lifecycle
    // ──────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        instance = this
        initTts()
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
        instance = null
        super.onDestroy()
    }

    override fun onListenerConnected() {
        Log.d(TAG, "Notification listener connected")
        instance = this
        // Seed the list with currently active notifications
        try {
            val active = activeNotifications
            if (active != null) {
                val items = active
                    .filter { it.packageName != MAHI_PACKAGE }
                    .mapNotNull { sbn -> mapToNotificationItem(sbn) }
                    .sortedByDescending { it.timestamp }
                    .take(MAX_NOTIFICATIONS)
                _notifications.value = items
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error seeding active notifications", e)
        }
    }

    override fun onListenerDisconnected() {
        Log.d(TAG, "Notification listener disconnected")
        instance = null
    }

    // ──────────────────────────────────────────────
    // Notification callbacks
    // ──────────────────────────────────────────────

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (sbn.packageName == MAHI_PACKAGE) return // filter own notifications

        val item = mapToNotificationItem(sbn) ?: return

        scope.launch {
            val current = _notifications.value.toMutableList()
            // De-duplicate by package + title + timestamp proximity
            current.removeAll {
                it.packageName == item.packageName &&
                        it.title == item.title &&
                        Math.abs(it.timestamp - item.timestamp) < 2000L
            }
            current.add(0, item)
            // Trim to max
            if (current.size > MAX_NOTIFICATIONS) {
                val trimmed = current.take(MAX_NOTIFICATIONS)
                _notifications.value = trimmed
            } else {
                _notifications.value = current
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val key = sbn.key

        scope.launch {
            _notifications.value = _notifications.value.filterNot { it.id == key }
        }
    }

    // ──────────────────────────────────────────────
    // Public methods
    // ──────────────────────────────────────────────

    /**
     * Reads the [count] most recent notifications aloud via TTS.
     * Returns the list of items that were read.
     */
    fun readAloud(count: Int = 5): List<NotificationItem> {
        val items = _notifications.value.take(count)
        if (items.isEmpty()) {
            speak("You have no notifications.")
            return emptyList()
        }

        val text = buildString {
            append("You have ${items.size} notification${if (items.size > 1) "s" else ""}. ")
            items.forEachIndexed { index, item ->
                append("${index + 1}. From ${item.appName}. ${item.title}. ")
                if (item.text.isNotBlank()) {
                    append("${item.text}. ")
                }
            }
        }
        speak(text)

        // Mark as read
        scope.launch {
            val current = _notifications.value.toMutableList()
            items.forEach { readItem ->
                val idx = current.indexOfFirst { it.id == readItem.id }
                if (idx >= 0) {
                    current[idx] = current[idx].copy(isRead = true)
                }
            }
            _notifications.value = current
        }

        return items
    }

    /**
     * Replies to a notification that has a direct-reply action.
     *
     * @param key     The notification item id (maps to StatusBarNotification.key)
     * @param message The text to send as a reply
     * @return true if reply was sent successfully
     */
    fun replyToNotification(key: String, message: String): Boolean {
        val sbn = try {
            activeNotifications.firstOrNull { it.key == key }
        } catch (_: Exception) {
            null
        }

        if (sbn == null) {
            Log.w(TAG, "Notification with key $key not found for reply")
            return false
        }

        val notification = sbn.notification
        val replyAction = notification.actions?.firstOrNull { action ->
            action.remoteInputs?.isNotEmpty() == true
        }

        if (replyAction == null) {
            Log.w(TAG, "No reply action available for notification $key")
            return false
        }

        return try {
            val remoteInput = replyAction.remoteInputs?.firstOrNull()
            if (remoteInput == null) return false

            val intent = Intent()
            val bundle = Bundle()
            bundle.putCharSequence(remoteInput.resultKey, message)
            android.app.RemoteInput.addResultsToIntent(replyAction.remoteInputs, intent, bundle)

            replyAction.actionIntent.send(applicationContext, 0, intent)
            Log.d(TAG, "Reply sent to $key: $message")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reply to notification $key", e)
            false
        }
    }

    /**
     * Dismisses a notification by its key.
     */
    fun dismissNotification(key: String) {
        try {
            cancelNotification(key)
            scope.launch {
                _notifications.value = _notifications.value.filterNot { it.id == key }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dismiss notification $key", e)
        }
    }

    /**
     * Clears all notifications.
     */
    fun clearAll() {
        try {
            cancelAllNotifications()
            scope.launch {
                _notifications.value = emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear all notifications", e)
        }
    }

    /**
     * Returns notifications from a specific app.
     */
    fun getNotificationsByPackage(packageName: String): List<NotificationItem> {
        return _notifications.value.filter { it.packageName == packageName }
    }

    /**
     * Returns unread notifications.
     */
    fun getUnreadNotifications(): List<NotificationItem> {
        return _notifications.value.filter { !it.isRead }
    }

    // ──────────────────────────────────────────────
    // Internal helpers
    // ──────────────────────────────────────────────

    private fun mapToNotificationItem(sbn: StatusBarNotification): NotificationItem? {
        return try {
            val notification = sbn.notification
            val extras = notification.extras

            val title = extras.getString(Notification.EXTRA_TITLE)?.trim() ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""
            val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim() ?: ""

            val appName = getAppName(sbn.packageName)
            val category = notification.category
            val hasReply = notification.actions?.any { it.remoteInputs?.isNotEmpty() == true } == true

            NotificationItem(
                id = sbn.key,
                packageName = sbn.packageName,
                appName = appName,
                title = title,
                text = if (text.isNotBlank()) text else subText,
                timestamp = sbn.postTime,
                category = category,
                isRead = false,
                hasReplyAction = hasReply
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to map notification from ${sbn.packageName}", e)
            null
        }
    }

    private fun getAppName(packageName: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    // ──────────────────────────────────────────────
    // TTS
    // ──────────────────────────────────────────────

    private fun initTts() {
        tts = TextToSpeech(applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                tts?.language = java.util.Locale.getDefault()
                Log.d(TAG, "TTS initialized successfully")
            } else {
                ttsReady = false
                Log.e(TAG, "TTS initialization failed with status: $status")
            }
        }
    }

    private fun speak(text: String) {
        if (ttsReady && tts != null) {
            tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "mahi_notif_${System.currentTimeMillis()}")
        }
    }
}
