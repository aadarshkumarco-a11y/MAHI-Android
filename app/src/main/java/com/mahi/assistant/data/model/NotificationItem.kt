package com.mahi.assistant.data.model

/**
 * Represents a captured notification from any app on the device.
 * Kept in memory via StateFlow in the notification listener service.
 *
 * @property id           Unique identifier (StatusBarNotification.key)
 * @property packageName  The package that posted the notification (e.g. "com.whatsapp")
 * @property appName      Human-readable app name resolved from PackageManager
 * @property title        Notification title / heading text
 * @property text         Notification body text
 * @property timestamp    Epoch millis when the notification was posted
 * @property category     Notification category (e.g. "msg", "call", "email")
 * @property isRead       Whether the user has already seen / read this notification
 * @property hasReplyAction Whether the notification carries a direct-reply RemoteInput
 */
data class NotificationItem(
    val id: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val timestamp: Long,
    val category: String?,
    val isRead: Boolean = false,
    val hasReplyAction: Boolean = false
)
