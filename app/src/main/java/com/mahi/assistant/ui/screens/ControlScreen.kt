package com.mahi.assistant.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mahi.assistant.ui.components.DeviceToggleCard
import com.mahi.assistant.ui.theme.*
import com.mahi.assistant.ui.viewmodel.MahiViewModel

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
 * Now scrollable and connected to ViewModel for real device state.
 */
@Composable
fun ControlScreen(
    viewModel: MahiViewModel,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val deviceState by viewModel.deviceState.collectAsState()

    // Build toggles from actual device state
    val toggles = listOf(
        // Connectivity
        DeviceToggle("flashlight", "Flashlight", Icons.Filled.FlashlightOn, deviceState.flashlight, DeviceSection.CONNECTIVITY),
        DeviceToggle("wifi", "WiFi", Icons.Filled.Wifi, deviceState.wifi, DeviceSection.CONNECTIVITY),
        DeviceToggle("bluetooth", "Bluetooth", Icons.Filled.Bluetooth, deviceState.bluetooth, DeviceSection.CONNECTIVITY),
        DeviceToggle("hotspot", "Hotspot", Icons.Filled.WifiTethering, deviceState.hotspot, DeviceSection.CONNECTIVITY),
        DeviceToggle("mobile_data", "Mobile Data", Icons.Filled.CellTower, deviceState.mobileData, DeviceSection.CONNECTIVITY),
        // Display
        DeviceToggle("auto_rotate", "Auto-Rotate", Icons.Filled.ScreenRotation, deviceState.autoRotate, DeviceSection.DISPLAY),
        // Sound
        DeviceToggle("dnd", "DND", Icons.Filled.DoNotDisturbOn, deviceState.dnd, DeviceSection.SOUND),
        // System
        DeviceToggle("battery_saver", "Battery Saver", Icons.Filled.BatterySaver, deviceState.batterySaver, DeviceSection.SYSTEM),
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),  // NOW SCROLLABLE!
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

        // ── Sections with FlowRow for proper layout ─────────
        DeviceSection.entries.forEach { section ->
            val sectionToggles = toggles.filter { it.section == section }

            if (sectionToggles.isNotEmpty()) {
                // Section header
                Text(
                    text = section.label.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = ElectricPurple,
                )
                Spacer(modifier = Modifier.height(8.dp))

                // 2-column grid using FlowRow
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    sectionToggles.forEach { toggle ->
                        Box(modifier = Modifier.weight(1f, fill = true)) {
                            DeviceToggleCard(
                                name = toggle.name,
                                icon = toggle.icon,
                                isOn = toggle.isOn,
                                onToggle = {
                                    viewModel.toggleDevice(toggle.id)
                                },
                            )
                        }
                    }
                    // Fill remaining space for odd items
                    if (sectionToggles.size % 2 != 0) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Bottom spacing
        Spacer(modifier = Modifier.height(24.dp))
    }
}
