package com.mahi.assistant.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mahi.assistant.ui.components.DeviceToggleCard
import com.mahi.assistant.ui.components.GlowCard
import com.mahi.assistant.ui.theme.*

/**
 * Data model for a device toggle.
 */
data class DeviceToggle(
    val id: String,
    val name: String,
    val icon: ImageVector,
    val isOn: Boolean = false,
    val section: DeviceSection,
)

enum class DeviceSection(val label: String) {
    CONNECTIVITY("Connectivity"),
    DISPLAY("Display"),
    SOUND("Sound"),
    SYSTEM("System"),
}

/**
 * Device control panel — JARVIS-style device management.
 */
@Composable
fun ControlScreen(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Toggle states
    var toggles by remember {
        mutableStateOf(
            listOf(
                // Connectivity
                DeviceToggle("flashlight", "Flashlight", Icons.Filled.FlashlightOn, false, DeviceSection.CONNECTIVITY),
                DeviceToggle("wifi", "WiFi", Icons.Filled.Wifi, true, DeviceSection.CONNECTIVITY),
                DeviceToggle("bluetooth", "Bluetooth", Icons.Filled.Bluetooth, false, DeviceSection.CONNECTIVITY),
                DeviceToggle("hotspot", "Hotspot", Icons.Filled.WifiTethering, false, DeviceSection.CONNECTIVITY),
                DeviceToggle("mobile_data", "Mobile Data", Icons.Filled.CellTower, true, DeviceSection.CONNECTIVITY),
                // Display
                DeviceToggle("brightness", "Brightness", Icons.Filled.BrightnessHigh, true, DeviceSection.DISPLAY),
                DeviceToggle("auto_rotate", "Auto-Rotate", Icons.Filled.ScreenRotation, false, DeviceSection.DISPLAY),
                DeviceToggle("screen_timeout", "Screen Timeout", Icons.Filled.ScreenLockPortrait, true, DeviceSection.DISPLAY),
                // Sound
                DeviceToggle("volume", "Volume", Icons.Filled.VolumeUp, true, DeviceSection.SOUND),
                DeviceToggle("ringer", "Ringer", Icons.Filled.Notifications, true, DeviceSection.SOUND),
                DeviceToggle("dnd", "DND", Icons.Filled.DoNotDisturbOn, false, DeviceSection.SOUND),
                // System
                DeviceToggle("battery_saver", "Battery Saver", Icons.Filled.BatterySaver, false, DeviceSection.SYSTEM),
            )
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        // ── Header ──────────────────────────────────────────────
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "DEVICE CONTROLS",
                style = MaterialTheme.typography.headlineSmall,
                color = NeonCyan,
            )
            Text(
                text = "${toggles.count { it.isOn }} ACTIVE",
                style = MaterialTheme.typography.labelMedium,
                color = NeonGreen,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Sections with Grid ──────────────────────────────────
        DeviceSection.values().forEach { section ->
            val sectionToggles = toggles.filter { it.section == section }

            if (sectionToggles.isNotEmpty()) {
                // Section header
                Text(
                    text = section.label.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = ElectricPurple,
                )
                Spacer(modifier = Modifier.height(8.dp))

                // 2-column grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.height(((sectionToggles.size / 2 + sectionToggles.size % 2) * 130).dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    userScrollEnabled = false,
                ) {
                    items(sectionToggles, key = { it.id }) { toggle ->
                        DeviceToggleCard(
                            name = toggle.name,
                            icon = toggle.icon,
                            isOn = toggle.isOn,
                            onToggle = {
                                toggles = toggles.map {
                                    if (it.id == toggle.id) it.copy(isOn = !it.isOn) else it
                                }
                            },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
