package com.mahi.assistant.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.mahi.assistant.service.FloatingAssistantService
import com.mahi.assistant.service.WakeWordService

/**
 * BroadcastReceiver that listens for [Intent.ACTION_BOOT_COMPLETED] and
 * conditionally starts the [WakeWordService] and/or [FloatingAssistantService]
 * if the user has enabled auto-start in SharedPreferences.
 *
 * Must be declared in the manifest with:
 * ```
 * <receiver android:name=".receiver.BootReceiver"
 *     android:exported="true">
 *     <intent-filter>
 *         <action android:name="android.intent.action.BOOT_COMPLETED" />
 *     </intent-filter>
 * </receiver>
 * ```
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
        private const val PREFS_NAME = "mahi_prefs"
        private const val KEY_AUTOSTART_WAKEWORD = "autostart_wakeword"
        private const val KEY_AUTOSTART_FLOATING = "autostart_floating"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            Log.w(TAG, "Ignoring non-boot action: ${intent.action}")
            return
        }

        Log.i(TAG, "Boot completed received — checking auto-start preferences")

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val autostartWakeword = prefs.getBoolean(KEY_AUTOSTART_WAKEWORD, false)
        val autostartFloating = prefs.getBoolean(KEY_AUTOSTART_FLOATING, false)

        if (autostartWakeword) {
            Log.i(TAG, "Auto-starting WakeWordService")
            startWakeWordService(context)
        }

        if (autostartFloating) {
            Log.i(TAG, "Auto-starting FloatingAssistantService")
            startFloatingService(context)
        }

        if (!autostartWakeword && !autostartFloating) {
            Log.i(TAG, "No auto-start services enabled")
        }
    }

    // ──────────────────────────────────────────────
    // Service starters
    // ──────────────────────────────────────────────

    private fun startWakeWordService(context: Context) {
        try {
            val serviceIntent = Intent(context, WakeWordService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start WakeWordService on boot", e)
        }
    }

    private fun startFloatingService(context: Context) {
        try {
            val serviceIntent = Intent(context, FloatingAssistantService::class.java).apply {
                action = FloatingAssistantService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start FloatingAssistantService on boot", e)
        }
    }
}
