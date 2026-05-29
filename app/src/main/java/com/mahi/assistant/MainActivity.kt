package com.mahi.assistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.mahi.assistant.ui.viewmodel.MahiViewModel
import com.mahi.assistant.ui.MahiApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MahiViewModel by viewModels()

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.mahi.assistant.VOICE_COMMAND" -> {
                    viewModel.startListening()
                }
                "com.mahi.assistant.TEXT_COMMAND" -> {
                    val text = intent.getStringExtra("text") ?: ""
                    if (text.isNotBlank()) {
                        viewModel.updateInput(text)
                        viewModel.submitInput()
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Register broadcast receiver for floating assistant commands
        val filter = IntentFilter().apply {
            addAction("com.mahi.assistant.VOICE_COMMAND")
            addAction("com.mahi.assistant.TEXT_COMMAND")
        }
        registerReceiver(commandReceiver, filter)

        setContent {
            MahiApp()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(commandReceiver) } catch (_: Exception) {}
    }
}
