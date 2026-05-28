package com.mahi.assistant.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.dp
import com.mahi.assistant.ui.components.*
import com.mahi.assistant.ui.theme.*

/**
 * Data model for a chat message.
 */
data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
)

/**
 * The state of the AI assistant in the chat.
 */
enum class AssistantState {
    IDLE, THINKING, SPEAKING
}

/**
 * Chat conversation screen — full conversation with the AI assistant.
 */
@Composable
fun ChatScreen(
    messages: List<ChatMessage> = emptyList(),
    assistantState: AssistantState = AssistantState.IDLE,
    onSendMessage: (String) -> Unit = {},
    onVoiceInput: () -> Unit = {},
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var textInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // ── Top Bar ─────────────────────────────────────────────
        ChatTopBar(onBack = onBack, assistantState = assistantState)

        // ── Messages ────────────────────────────────────────────
        LazyColumn(
            modifier = Modifier.weight(1f),
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(messages, key = { it.id }) { message ->
                MessageBubble(message = message)
            }

            // Assistant state indicator
            if (assistantState == AssistantState.THINKING) {
                item {
                    ThinkingIndicator()
                }
            }

            if (assistantState == AssistantState.SPEAKING) {
                item {
                    SpeakingIndicator()
                }
            }
        }

        // ── Input Bar ───────────────────────────────────────────
        ChatInputBar(
            value = textInput,
            onValueChange = { textInput = it },
            onSubmit = {
                if (textInput.isNotBlank()) {
                    onSendMessage(textInput)
                    textInput = ""
                }
            },
            onMicClick = onVoiceInput,
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// Sub-composables
// ═══════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    onBack: () -> Unit,
    assistantState: AssistantState,
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "MAHI",
                    style = MaterialTheme.typography.titleMedium,
                    color = NeonCyan,
                )
                Spacer(modifier = Modifier.width(8.dp))
                val stateText = when (assistantState) {
                    AssistantState.IDLE -> ""
                    AssistantState.THINKING -> "thinking..."
                    AssistantState.SPEAKING -> "speaking..."
                }
                val stateColor = when (assistantState) {
                    AssistantState.IDLE -> TextTertiary
                    AssistantState.THINKING -> ElectricPurple
                    AssistantState.SPEAKING -> NeonGreen
                }
                if (stateText.isNotEmpty()) {
                    Text(
                        text = stateText,
                        style = MaterialTheme.typography.labelSmall,
                        color = stateColor,
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = NeonCyan,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = DeepSpaceBlack,
            titleContentColor = TextPrimary,
        ),
    )
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val alignment = if (message.isFromUser) Alignment.End else Alignment.Start
    val bubbleColor = if (message.isFromUser) {
        ElectricPurple.copy(alpha = 0.15f)
    } else {
        NeonCyan.copy(alpha = 0.08f)
    }
    val borderColor = if (message.isFromUser) {
        ElectricPurple.copy(alpha = 0.3f)
    } else {
        NeonCyan.copy(alpha = 0.3f)
    }
    val textColor = if (message.isFromUser) TextPrimary else TextPrimary

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment,
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .then(
                    if (!message.isFromUser) {
                        Modifier.drawBehind {
                            val blurPx = 4.dp.toPx()
                            drawIntoCanvas { canvas ->
                                val paint = Paint().apply {
                                    color = NeonCyan.copy(alpha = 0.15f)
                                    asFrameworkPaint().maskFilter =
                                        android.graphics.BlurMaskFilter(
                                            blurPx,
                                            android.graphics.BlurMaskFilter.Blur.NORMAL
                                        )
                                }
                                canvas.nativeCanvas.drawRoundRect(
                                    android.graphics.RectF(0f, 0f, size.width, size.height),
                                    12.dp.toPx(), 12.dp.toPx(),
                                    paint.asFrameworkPaint()
                                )
                            }
                        }
                    } else Modifier
                ),
            shape = RoundedCornerShape(
                topStart = 12.dp,
                topEnd = 12.dp,
                bottomStart = if (message.isFromUser) 12.dp else 4.dp,
                bottomEnd = if (message.isFromUser) 4.dp else 12.dp,
            ),
            color = bubbleColor,
            border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Sender label
                Text(
                    text = if (message.isFromUser) "YOU" else "MAHI",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (message.isFromUser) ElectricPurple else NeonCyan,
                )
                Spacer(modifier = Modifier.height(4.dp))
                // Message content
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                )
            }
        }
    }
}

@Composable
private fun ThinkingIndicator() {
    val dotAlpha = remember { Animatable(0.3f) }
    LaunchedEffect(Unit) {
        dotAlpha.animateTo(
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse
            )
        )
    }

    Row(
        modifier = Modifier.padding(start = 16.dp, top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "MAHI",
            style = MaterialTheme.typography.labelSmall,
            color = ElectricPurple,
        )
        Spacer(modifier = Modifier.width(8.dp))
        repeat(3) { i ->
            val delay = i * 150L
            val animatedAlpha = remember { Animatable(0.2f) }
            LaunchedEffect(Unit) {
                animatedAlpha.animateTo(
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600, delayMillis = delay.toInt(), easing = EaseInOutSine),
                        repeatMode = RepeatMode.Reverse
                    )
                )
            }
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .padding(1.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = ElectricPurple.copy(alpha = animatedAlpha.value),
                ) {}
            }
        }
    }
}

@Composable
private fun SpeakingIndicator() {
    Row(
        modifier = Modifier.padding(start = 16.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "MAHI",
            style = MaterialTheme.typography.labelSmall,
            color = NeonGreen,
        )
        Spacer(modifier = Modifier.width(8.dp))
        VoiceWaveform(
            isAnimating = true,
            color = NeonGreen,
            maxHeight = 20.dp,
        )
    }
}

@Composable
private fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onMicClick: () -> Unit,
) {
    Surface(
        color = DarkPanel,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .navigationBarsPadding(),
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
                        text = "Type a message...",
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
