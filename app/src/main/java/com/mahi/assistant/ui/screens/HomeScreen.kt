package com.mahi.assistant.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mahi.assistant.data.model.AssistantState
import com.mahi.assistant.ui.components.*
import com.mahi.assistant.ui.theme.*
import com.mahi.assistant.ui.viewmodel.MahiViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * The main Home Screen — the JARVIS command center.
 * Connected to shared ViewModel for AI interaction.
 */
@Composable
fun HomeScreen(
    viewModel: MahiViewModel,
    onNavigateToChat: () -> Unit = {},
    onNavigateToControls: () -> Unit = {},
    onNavigateToWeather: () -> Unit = {},
    onNavigateToNews: () -> Unit = {},
    onNavigateToRoutines: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val assistantState by viewModel.assistantState.collectAsState()
    val settingsState by viewModel.settingsState.collectAsState()
    var textInput by remember { mutableStateOf("") }

    // Time-based greeting
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 5..11 -> "Good Morning"
        in 12..16 -> "Good Afternoon"
        in 17..20 -> "Good Evening"
        else -> "Good Night"
    }

    val currentTime = remember {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }

    // Map AssistantState to OrbState
    val orbState = when (assistantState) {
        AssistantState.IDLE -> OrbState.IDLE
        AssistantState.LISTENING -> OrbState.LISTENING
        AssistantState.THINKING -> OrbState.THINKING
        AssistantState.SPEAKING -> OrbState.SPEAKING
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Scanline overlay
        ScanlineOverlay(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ── Top Bar ─────────────────────────────────────────
            TopBar(currentTime = currentTime, viewModel = viewModel)

            Spacer(modifier = Modifier.height(24.dp))

            // ── Greeting ────────────────────────────────────────
            Text(
                text = greeting,
                style = MaterialTheme.typography.headlineSmall,
                color = TextCyan,
            )
            Text(
                text = "How can I help you?",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )

            // ── Info Banner — Web search always available ────
            if (!settingsState.isGeminiKeyValid && settingsState.grokKey.isBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    color = NeonGreen.copy(alpha = 0.08f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonGreen.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Web Search Available",
                            tint = NeonGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Web Search Active",
                                style = MaterialTheme.typography.labelMedium,
                                color = NeonGreen,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Wikipedia + DuckDuckGo always work. Add Gemini key in Settings for smarter AI.",
                                style = MaterialTheme.typography.bodySmall,
                                color = NeonGreen.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // ── Central Orb ─────────────────────────────────────
            MahiOrb(
                state = orbState,
                size = 200.dp,
                onClick = {
                    viewModel.startListening()
                },
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── Orb state label ─────────────────────────────────
            val stateLabel = when (orbState) {
                OrbState.IDLE -> "TAP TO SPEAK"
                OrbState.LISTENING -> "LISTENING..."
                OrbState.THINKING -> "PROCESSING..."
                OrbState.SPEAKING -> "SPEAKING..."
            }
            Text(
                text = stateLabel,
                style = MaterialTheme.typography.labelMedium,
                color = when (orbState) {
                    OrbState.IDLE -> TextTertiary
                    OrbState.LISTENING -> NeonCyan
                    OrbState.THINKING -> ElectricPurple
                    OrbState.SPEAKING -> NeonGreen
                },
            )

            Spacer(modifier = Modifier.weight(1f))

            // ── Voice Waveform (when listening/speaking) ────────
            if (orbState == OrbState.LISTENING || orbState == OrbState.SPEAKING) {
                VoiceWaveform(
                    isAnimating = true,
                    color = when (orbState) {
                        OrbState.LISTENING -> NeonCyan
                        OrbState.SPEAKING -> NeonGreen
                        else -> NeonCyan
                    },
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // ── Quick Action Buttons ────────────────────────────
            QuickActionsRow(
                onWeather = onNavigateToWeather,
                onNews = onNavigateToNews,
                onControls = onNavigateToControls,
                onRoutines = onNavigateToRoutines,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── Text Input Bar ──────────────────────────────────
            TextInputBar(
                value = textInput,
                onValueChange = { textInput = it },
                onSubmit = {
                    if (textInput.isNotBlank()) {
                        viewModel.processInput(textInput)
                        textInput = ""
                        onNavigateToChat()
                    }
                },
                onMicClick = {
                    viewModel.startListening()
                },
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── Status Indicators ───────────────────────────────
            StatusIndicators(isAiConfigured = settingsState.isGeminiKeyValid)

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// Sub-composables
// ═══════════════════════════════════════════════════════════════

@Composable
private fun TopBar(currentTime: String, viewModel: MahiViewModel) {
    val currentLanguage by viewModel.currentLanguage.collectAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // MAHI logo
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "MAHI",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = NeonCyan,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "AI",
                style = MaterialTheme.typography.titleSmall,
                color = ElectricPurple,
            )
        }

        // Time
        Text(
            text = currentTime,
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
        )

        // Language toggle + Status dot
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Language toggle button
            TextButton(onClick = { viewModel.toggleLanguage() }) {
                Text(
                    text = if (currentLanguage == "en") "HI" else "EN",
                    color = NeonCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Box(
                modifier = Modifier
                    .size(8.dp)
                    .padding(1.dp)
            ) {
                val pulseAlpha = remember { Animatable(0.5f) }
                LaunchedEffect(Unit) {
                    pulseAlpha.animateTo(
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1500, easing = EaseInOutSine),
                            repeatMode = RepeatMode.Reverse
                        )
                    )
                }
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = NeonGreen.copy(alpha = pulseAlpha.value),
                ) {}
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "ONLINE",
                style = MaterialTheme.typography.labelSmall,
                color = NeonGreen,
            )
        }
    }
}

@Composable
private fun QuickActionsRow(
    onWeather: () -> Unit,
    onNews: () -> Unit,
    onControls: () -> Unit,
    onRoutines: () -> Unit,
) {
    val actions = listOf(
        "Weather" to Icons.Filled.WbSunny,
        "News" to Icons.Filled.Article,
        "Controls" to Icons.Filled.ToggleOn,
        "Routines" to Icons.Filled.AutoMode,
    )
    val callbacks = listOf(onWeather, onNews, onControls, onRoutines)

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        items(actions.indices.toList()) { index ->
            NeonButton(
                text = actions[index].first,
                onClick = callbacks[index],
                glowColor = NeonCyan,
            )
        }
    }
}

@Composable
private fun TextInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onMicClick: () -> Unit,
) {
    GlowCard(
        modifier = Modifier.fillMaxWidth(),
        glowColor = NeonCyan,
        borderAlpha = 0.2f,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Mic button
            IconButton(onClick = onMicClick) {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = "Voice Input",
                    tint = NeonCyan,
                )
            }

            // Text field
            TextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = {
                    Text(
                        text = "Ask MAHI anything...",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                    )
                },
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = NeonCyan,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                ),
                textStyle = MaterialTheme.typography.bodyMedium,
                singleLine = true,
            )

            // Send button
            IconButton(
                onClick = onSubmit,
                enabled = value.isNotBlank(),
            ) {
                Icon(
                    imageVector = Icons.Filled.Send,
                    contentDescription = "Send",
                    tint = if (value.isNotBlank()) ElectricPurple else TextTertiary,
                )
            }
        }
    }
}

@Composable
private fun StatusIndicators(isAiConfigured: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        StatusDot(label = "Voice Ready", color = NeonGreen)
        StatusDot(label = if (isAiConfigured) "AI Online" else "Web Search", color = if (isAiConfigured) NeonCyan else NeonGreen)
        StatusDot(label = if (isAiConfigured) "Gemini+Grok" else "Wikipedia+DDG", color = NeonGreen)
        StatusDot(label = "Device Control", color = ElectricPurple)
    }
}

@Composable
private fun StatusDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .padding(0.5.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = androidx.compose.foundation.shape.CircleShape,
                color = color,
            ) {}
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color.copy(alpha = 0.7f),
        )
    }
}

// Needed for Color import inside StatusDot
private val Color = androidx.compose.ui.graphics.Color
