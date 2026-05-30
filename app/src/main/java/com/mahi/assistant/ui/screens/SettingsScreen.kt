package com.mahi.assistant.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.mahi.assistant.ui.components.GlowCard
import com.mahi.assistant.ui.components.NeonButton
import com.mahi.assistant.ui.theme.*
import com.mahi.assistant.ui.viewmodel.MahiViewModel
import com.mahi.assistant.ui.viewmodel.SettingsUiState

/**
 * Settings screen — system configuration for MAHI.
 * Now properly connected to ViewModel for API key persistence.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MahiViewModel,
    onBack: () -> Unit = {},
    onRequestNotificationAccess: () -> Unit = {},
    onRequestAccessibility: () -> Unit = {},
    onRequestOverlayPermission: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val settingsState by viewModel.settingsState.collectAsState()
    val context = LocalContext.current

    // Local state for editing - initialized from persisted settings
    var geminiKey by remember(settingsState.geminiKey) { mutableStateOf(settingsState.geminiKey) }
    var grokKey by remember(settingsState.grokKey) { mutableStateOf(settingsState.grokKey) }
    var porcupineKey by remember(settingsState.porcupineKey) { mutableStateOf(settingsState.porcupineKey) }
    var weatherKey by remember(settingsState.weatherKey) { mutableStateOf(settingsState.weatherKey) }
    var newsKey by remember(settingsState.newsKey) { mutableStateOf(settingsState.newsKey) }
    var showGeminiKey by remember { mutableStateOf(false) }
    var showGrokKey by remember { mutableStateOf(false) }
    var showPorcupineKey by remember { mutableStateOf(false) }
    var showWeatherKey by remember { mutableStateOf(false) }
    var showNewsKey by remember { mutableStateOf(false) }

    // Voice settings - from persisted state
    var voiceSpeed by remember(settingsState.voiceSpeed) { mutableStateOf(settingsState.voiceSpeed) }
    var voicePitch by remember(settingsState.voicePitch) { mutableStateOf(settingsState.voicePitch) }
    var selectedWakeWord by remember(settingsState.wakeWord) { mutableStateOf(settingsState.wakeWord) }
    val wakeWords = listOf("Hey Mahi", "Jarvis", "Computer", "Hey Assistant")

    // Toggles - from persisted state
    var autoStartOnBoot by remember(settingsState.autoStartOnBoot) { mutableStateOf(settingsState.autoStartOnBoot) }
    var floatingAssistant by remember(settingsState.floatingAssistant) { mutableStateOf(settingsState.floatingAssistant) }
    var continuousMode by remember(settingsState.continuousMode) { mutableStateOf(settingsState.continuousMode) }
    var defaultCity by remember(settingsState.defaultCity) { mutableStateOf(settingsState.defaultCity) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // ── Header ──────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = NeonCyan,
                )
            }
            Text(
                text = "SYSTEM CONFIG",
                style = MaterialTheme.typography.headlineSmall,
                color = NeonCyan,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── API Keys Section ────────────────────────────────
            item {
                SectionHeader(title = "API KEYS")
            }

            item {
                GlowCard(glowColor = ElectricPurple, borderAlpha = 0.2f) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        ApiKeyField(
                            label = "Gemini API Key",
                            value = geminiKey,
                            onValueChange = {
                                geminiKey = it
                                viewModel.updateGeminiKey(it)
                            },
                            showKey = showGeminiKey,
                            onToggleVisibility = { showGeminiKey = !showGeminiKey },
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        ApiKeyField(
                            label = "Grok (xAI) API Key",
                            value = grokKey,
                            onValueChange = {
                                grokKey = it
                                viewModel.updateGrokKey(it)
                            },
                            showKey = showGrokKey,
                            onToggleVisibility = { showGrokKey = !showGrokKey },
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Grok is used as automatic fallback when Gemini is unavailable",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        ApiKeyField(
                            label = "Porcupine Access Key",
                            value = porcupineKey,
                            onValueChange = {
                                porcupineKey = it
                                viewModel.updatePorcupineKey(it)
                            },
                            showKey = showPorcupineKey,
                            onToggleVisibility = { showPorcupineKey = !showPorcupineKey },
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        ApiKeyField(
                            label = "OpenWeatherMap API Key",
                            value = weatherKey,
                            onValueChange = {
                                weatherKey = it
                                viewModel.updateWeatherKey(it)
                            },
                            showKey = showWeatherKey,
                            onToggleVisibility = { showWeatherKey = !showWeatherKey },
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        ApiKeyField(
                            label = "GNews API Key",
                            value = newsKey,
                            onValueChange = {
                                newsKey = it
                                viewModel.updateNewsKey(it)
                            },
                            showKey = showNewsKey,
                            onToggleVisibility = { showNewsKey = !showNewsKey },
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Save button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            NeonButton(
                                text = "SAVE ALL",
                                onClick = {
                                    viewModel.saveAllSettings()
                                    Toast.makeText(context, "Settings saved!", Toast.LENGTH_SHORT).show()
                                },
                                glowColor = NeonGreen,
                            )
                        }

                        // Status indicator
                        val missingKeys = buildList {
                            if (geminiKey.isBlank() && grokKey.isBlank()) add("Gemini/Grok (need at least one)")
                            if (weatherKey.isBlank()) add("Weather (optional)")
                            if (newsKey.isBlank()) add("News (optional)")
                        }
                        if (missingKeys.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Missing keys: ${missingKeys.joinToString(", ")}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Amber,
                            )
                        } else {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = NeonGreen,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "All API keys configured",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NeonGreen,
                                )
                            }
                        }
                    }
                }
            }

            // ── Voice Settings Section ──────────────────────────
            item {
                SectionHeader(title = "VOICE SETTINGS")
            }

            item {
                GlowCard(glowColor = NeonCyan, borderAlpha = 0.2f) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Wake word
                        Text(
                            text = "WAKE WORD",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            wakeWords.forEach { word ->
                                FilterChip(
                                    selected = selectedWakeWord == word,
                                    onClick = {
                                        selectedWakeWord = word
                                        viewModel.updateWakeWord(word)
                                    },
                                    label = {
                                        Text(
                                            text = word,
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = NeonCyan.copy(alpha = 0.15f),
                                        selectedLabelColor = NeonCyan,
                                        containerColor = DarkPanelLight,
                                        labelColor = TextTertiary,
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        borderColor = DarkPanelBorder,
                                        selectedBorderColor = NeonCyan.copy(alpha = 0.5f),
                                        borderWidth = 1.dp,
                                    ),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Voice speed
                        Text(
                            text = "VOICE SPEED: ${String.format("%.1f", voiceSpeed)}x",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary,
                        )
                        Slider(
                            value = voiceSpeed,
                            onValueChange = {
                                voiceSpeed = it
                                viewModel.updateVoiceSpeed(it)
                            },
                            valueRange = 0.5f..2.0f,
                            steps = 5,
                            colors = SliderDefaults.colors(
                                thumbColor = NeonCyan,
                                activeTrackColor = NeonCyan,
                                inactiveTrackColor = DarkPanelBorder,
                            ),
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Voice pitch
                        Text(
                            text = "VOICE PITCH: ${String.format("%.1f", voicePitch)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary,
                        )
                        Slider(
                            value = voicePitch,
                            onValueChange = {
                                voicePitch = it
                                viewModel.updateVoicePitch(it)
                            },
                            valueRange = 0.5f..2.0f,
                            steps = 5,
                            colors = SliderDefaults.colors(
                                thumbColor = ElectricPurple,
                                activeTrackColor = ElectricPurple,
                                inactiveTrackColor = DarkPanelBorder,
                            ),
                        )
                    }
                }
            }

            // ── General Settings Section ────────────────────────
            item {
                SectionHeader(title = "GENERAL")
            }

            item {
                GlowCard(glowColor = NeonGreen, borderAlpha = 0.2f) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Auto-start on boot
                        SettingToggle(
                            title = "Auto-start on Boot",
                            description = "Launch MAHI when device boots",
                            isOn = autoStartOnBoot,
                            onToggle = {
                                autoStartOnBoot = it
                                viewModel.updateAutoStartOnBoot(it)
                            },
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Floating assistant
                        SettingToggle(
                            title = "Floating Assistant",
                            description = "Show floating bubble overlay",
                            isOn = floatingAssistant,
                            onToggle = {
                                floatingAssistant = it
                                viewModel.updateFloatingAssistant(it)
                            },
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Continuous Mode
                        SettingToggle(
                            title = "Continuous Mode",
                            description = "Keep listening after each response",
                            isOn = continuousMode,
                            onToggle = {
                                continuousMode = it
                                viewModel.updateContinuousMode(it)
                            },
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Default City
                        Text(
                            text = "DEFAULT CITY FOR WEATHER",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextTertiary,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = defaultCity,
                            onValueChange = {
                                defaultCity = it
                                viewModel.updateDefaultCity(it)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                Text(
                                    text = "Enter city name...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextTertiary,
                                )
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan.copy(alpha = 0.5f),
                                unfocusedBorderColor = DarkPanelBorder,
                                focusedContainerColor = DeepSpaceBlack,
                                unfocusedContainerColor = DeepSpaceBlack,
                                cursorColor = NeonCyan,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                            ),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                            textStyle = MaterialTheme.typography.labelMedium,
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Gemini API key hint
                        Text(
                            text = "Gemini is primary AI. Grok is automatic fallback when Gemini fails. At least one key required.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Amber,
                        )
                    }
                }
            }

            // ── Permissions Section ─────────────────────────────
            item {
                SectionHeader(title = "PERMISSIONS")
            }

            item {
                GlowCard(glowColor = Amber, borderAlpha = 0.2f) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        PermissionButton(
                            title = "Notification Listener",
                            description = "Required to read notifications",
                            onClick = onRequestNotificationAccess,
                        )
                        PermissionButton(
                            title = "Accessibility Service",
                            description = "Required for app automation",
                            onClick = onRequestAccessibility,
                        )
                        PermissionButton(
                            title = "Overlay Permission",
                            description = "Required for floating assistant",
                            onClick = onRequestOverlayPermission,
                        )
                    }
                }
            }

            // ── About ──────────────────────────────────────────
            item {
                SectionHeader(title = "ABOUT")
            }

            item {
                GlowCard(glowColor = TextTertiary, borderAlpha = 0.1f) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "MAHI AI Assistant",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                            )
                            Text(
                                text = "v2.0.0",
                                style = MaterialTheme.typography.labelMedium,
                                color = NeonCyan,
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Mobile Artificial Human Intelligence — Powered by Gemini",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary,
                        )
                    }
                }
            }

            // Bottom spacing
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// Sub-composables
// ═══════════════════════════════════════════════════════════════

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = ElectricPurple,
    )
}

@Composable
private fun ApiKeyField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    showKey: Boolean,
    onToggleVisibility: () -> Unit,
) {
    Column {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = onToggleVisibility) {
                    Icon(
                        imageVector = if (showKey) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = if (showKey) "Hide" else "Show",
                        tint = TextTertiary,
                    )
                }
            },
            placeholder = {
                Text(
                    text = "Enter key...",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                )
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NeonCyan.copy(alpha = 0.5f),
                unfocusedBorderColor = DarkPanelBorder,
                focusedContainerColor = DeepSpaceBlack,
                unfocusedContainerColor = DeepSpaceBlack,
                cursorColor = NeonCyan,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
            ),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            textStyle = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun SettingToggle(
    title: String,
    description: String,
    isOn: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
            )
        }
        Switch(
            checked = isOn,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedTrackColor = NeonCyan.copy(alpha = 0.3f),
                checkedThumbColor = NeonCyan,
                checkedBorderColor = NeonCyan.copy(alpha = 0.5f),
                uncheckedTrackColor = DarkPanelBorder,
                uncheckedThumbColor = TextTertiary,
                uncheckedBorderColor = DarkPanelBorder,
            ),
        )
    }
}

@Composable
private fun PermissionButton(
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
            )
        }
        NeonButton(
            text = "SETUP",
            onClick = onClick,
            glowColor = Amber,
        )
    }
}
