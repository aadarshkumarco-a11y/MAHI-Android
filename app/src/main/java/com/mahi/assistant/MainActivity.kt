package com.mahi.assistant

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import com.mahi.assistant.ui.theme.MAHITheme
import com.mahi.assistant.ui.MahiApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    var hasAllPermissions by mutableStateOf(false)
        private set

    private val requiredPermissions = arrayOf(
        android.Manifest.permission.RECORD_AUDIO,
        android.Manifest.permission.CAMERA,
        android.Manifest.permission.READ_CONTACTS,
        android.Manifest.permission.CALL_PHONE,
        android.Manifest.permission.READ_CALL_LOG,
        android.Manifest.permission.SEND_SMS,
        android.Manifest.permission.READ_SMS,
        android.Manifest.permission.READ_CALENDAR,
        android.Manifest.permission.WRITE_CALENDAR,
        android.Manifest.permission.POST_NOTIFICATIONS,
        android.Manifest.permission.READ_PHONE_STATE,
        android.Manifest.permission.BLUETOOTH_CONNECT,
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        hasAllPermissions = allGranted
        if (!allGranted) {
            val denied = permissions.filter { !it.value }.keys
            val shouldShowRationale = denied.any { perm ->
                ActivityCompat.shouldShowRequestPermissionRationale(this, perm)
            }
            if (!shouldShowRationale) {
                Toast.makeText(
                    this,
                    "Please grant permissions in Settings for full functionality",
                    Toast.LENGTH_LONG
                ).show()
                openAppSettings()
            }
        }
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        checkOverlayPermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make status bar and nav bar transparent (manual edge-to-edge for older activity)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        requestRequiredPermissions()
        checkOverlayPermission()

        setContent {
            MAHITheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MahiApp(
                        hasAllPermissions = hasAllPermissions,
                        onRequestPermissions = { requestRequiredPermissions() }
                    )
                }
            }
        }

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_VOICE_COMMAND -> {
                // Triggered from voice command shortcut
            }
            Intent.ACTION_MAIN -> {
                // Standard launch
            }
        }
    }

    private fun requestRequiredPermissions() {
        val ungranted = requiredPermissions.filter { perm ->
            ActivityCompat.checkSelfPermission(this, perm) !=
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (ungranted.isEmpty()) {
            hasAllPermissions = true
        } else {
            permissionLauncher.launch(ungranted.toTypedArray())
        }
    }

    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }
}
