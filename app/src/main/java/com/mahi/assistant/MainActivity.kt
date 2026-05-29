package com.mahi.assistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.mahi.assistant.ui.viewmodel.MahiViewModel
import com.mahi.assistant.ui.MahiApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private val viewModel: MahiViewModel by viewModels()

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            try {
                when (intent?.action) {
                    "com.mahi.assistant.VOICE_COMMAND" -> {
                        viewModel.startListening()
                    }
                    "com.mahi.assistant.TEXT_COMMAND" -> {
                        val text = intent.getStringExtra("text") ?: intent.getStringExtra("command") ?: ""
                        if (text.isNotBlank()) {
                            viewModel.updateInput(text)
                            viewModel.submitInput()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in commandReceiver", e)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            // Register broadcast receiver for floating assistant commands
            // Android 14+ (API 34) REQUIRES RECEIVER_EXPORTED or RECEIVER_NOT_EXPORTED flag
            val filter = IntentFilter().apply {
                addAction("com.mahi.assistant.VOICE_COMMAND")
                addAction("com.mahi.assistant.TEXT_COMMAND")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(commandReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(commandReceiver, filter)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register commandReceiver", e)
        }

        setContent {
            MahiApp()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(commandReceiver) } catch (_: Exception) {}
    }
}
