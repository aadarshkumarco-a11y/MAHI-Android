package com.mahi.assistant.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.ScaleAnimation
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.mahi.assistant.MainActivity
import com.mahi.assistant.R

/**
 * Foreground service that displays a floating assistant orb overlay.
 *
 * The orb:
 *  - Is a translucent neon-cyan (#37DCF2) circle with a pulse/glow animation
 *  - Can be dragged anywhere on screen
 *  - On tap expands to a mini command panel (voice button + text input)
 *  - Auto-collapses after 5 seconds of inactivity
 *  - Runs as a foreground service with a persistent notification
 */
class FloatingAssistantService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    companion object {
        const val CHANNEL_ID = "mahi_floating_assistant"
        const val NOTIFICATION_ID = 2001
        const val ACTION_START = "com.mahi.assistant.action.START_FLOATING"
        const val ACTION_STOP = "com.mahi.assistant.action.STOP_FLOATING"
        const val COLLAPSE_DELAY_MS = 5000L

        val NEON_CYAN_COLOR = Color(0xFF37DCF2)
    }

    // ──────────────────────────────────────────────
    // Lifecycle plumbing for ComposeView in Service
    // ──────────────────────────────────────────────
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = androidx.savedstate.SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    // ──────────────────────────────────────────────
    // Service state
    // ──────────────────────────────────────────────
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var isExpanded = false
    private val handler = Handler(Looper.getMainLooper())

    // Drag tracking
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    private val collapseRunnable = Runnable {
        if (isExpanded) {
            isExpanded = false
            rebuildOverlay()
        }
    }

    // ──────────────────────────────────────────────
    // Service lifecycle
    // ──────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        showOverlay()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        removeOverlay()
        handler.removeCallbacks(collapseRunnable)
        super.onDestroy()
    }

    // ──────────────────────────────────────────────
    // Notification channel & foreground notification
    // ──────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Floating Assistant",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the floating assistant running"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    @Suppress("DEPRECATION")
    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPi = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, FloatingAssistantService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPi = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("MAHI Assistant")
            .setContentText("Floating assistant is active")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openPi)
            .setOngoing(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    addAction(Notification.Action.Builder(null, "Stop", stopPi).build())
                }
            }
            .build()
    }

    // ──────────────────────────────────────────────
    // Overlay management
    // ──────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun showOverlay() {
        if (overlayView != null) return
        rebuildOverlay()
    }

    private fun rebuildOverlay() {
        removeOverlay()
        overlayView = if (isExpanded) createExpandedView() else createOrbView()
        val params = if (isExpanded) expandedLayoutParams() else orbLayoutParams()
        try {
            windowManager?.addView(overlayView, params)
        } catch (_: Exception) {
            // SYSTEM_ALERT_WINDOW may not be granted
        }
    }

    private fun removeOverlay() {
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {
            }
        }
        overlayView = null
    }

    // ──────────────────────────────────────────────
    // Orb view (collapsed state)
    // ──────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun createOrbView(): View {
        val container = FrameLayout(this).apply {
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }

        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                OrbContent(
                    onClick = {
                        isExpanded = true
                        handler.removeCallbacks(collapseRunnable)
                        rebuildOverlay()
                    }
                )
            }
        }

        composeView.setViewTreeLifecycleOwner(this)
        composeView.setViewTreeSavedStateRegistryOwner(this)

        container.addView(composeView)
        setupDrag(container, isOrb = true)
        startPulseAnimation(container)
        return container
    }

    @Composable
    private fun OrbContent(onClick: () -> Unit) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .shadow(12.dp, CircleShape, ambientColor = NEON_CYAN_COLOR, spotColor = NEON_CYAN_COLOR)
                .clip(CircleShape)
                .background(NEON_CYAN_COLOR.copy(alpha = 0.85f))
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            // Inner glow ring
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f))
            )
            // Center dot
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.9f))
            )
        }
    }

    // ──────────────────────────────────────────────
    // Expanded panel view
    // ──────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun createExpandedView(): View {
        val container = FrameLayout(this).apply {
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }

        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ExpandedPanelContent(
                    onVoiceClick = { handleVoiceCommand() },
                    onSendClick = { text -> handleTextCommand(text) },
                    onCloseClick = {
                        isExpanded = false
                        handler.removeCallbacks(collapseRunnable)
                        rebuildOverlay()
                    }
                )
            }
        }

        composeView.setViewTreeLifecycleOwner(this)
        composeView.setViewTreeSavedStateRegistryOwner(this)

        container.addView(composeView)
        setupDrag(container, isOrb = false)

        // Auto-collapse after inactivity
        handler.removeCallbacks(collapseRunnable)
        handler.postDelayed(collapseRunnable, COLLAPSE_DELAY_MS)

        return container
    }

    @Composable
    private fun ExpandedPanelContent(
        onVoiceClick: () -> Unit,
        onSendClick: (String) -> Unit,
        onCloseClick: () -> Unit
    ) {
        var textInput by remember { mutableStateOf("") }

        Card(
            modifier = Modifier
                .width(280.dp)
                .wrapContentHeight()
                .shadow(24.dp, RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1A1A2E).copy(alpha = 0.95f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "MAHI",
                        color = NEON_CYAN_COLOR,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onCloseClick, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Voice button
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .shadow(16.dp, CircleShape, ambientColor = NEON_CYAN_COLOR, spotColor = NEON_CYAN_COLOR)
                        .clip(CircleShape)
                        .background(NEON_CYAN_COLOR)
                        .clickable { onVoiceClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = "Voice",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Tap to speak",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Text input row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 4.dp),
                        placeholder = {
                            Text(
                                "Type a command…",
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 14.sp
                            )
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            cursorColor = NEON_CYAN_COLOR
                        )
                    )
                    IconButton(
                        onClick = {
                            if (textInput.isNotBlank()) {
                                onSendClick(textInput.trim())
                                textInput = ""
                            }
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = "Send",
                            tint = NEON_CYAN_COLOR,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }

    // ──────────────────────────────────────────────
    // Command handling
    // ──────────────────────────────────────────────

    private fun handleVoiceCommand() {
        val intent = Intent("com.mahi.assistant.action.VOICE_COMMAND")
        intent.setPackage(packageName)
        sendBroadcast(intent)
        resetCollapseTimer()
    }

    private fun handleTextCommand(text: String) {
        val intent = Intent("com.mahi.assistant.action.TEXT_COMMAND").apply {
            putExtra("command", text)
            setPackage(packageName)
        }
        sendBroadcast(intent)
        resetCollapseTimer()
    }

    private fun resetCollapseTimer() {
        handler.removeCallbacks(collapseRunnable)
        handler.postDelayed(collapseRunnable, COLLAPSE_DELAY_MS)
    }

    // ──────────────────────────────────────────────
    // Drag handling
    // ──────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDrag(view: View, isOrb: Boolean) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = viewParamsX(v)
                    initialY = viewParamsY(v)
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (!isDragging && (kotlin.math.abs(dx) > 10 || kotlin.math.abs(dy) > 10)) {
                        isDragging = true
                    }
                    if (isDragging) {
                        val params = v.layoutParams as? WindowManager.LayoutParams
                        params?.let {
                            it.x = initialX + dx.toInt()
                            it.y = initialY + dy.toInt()
                            windowManager?.updateViewLayout(v, it)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        v.performClick()
                    }
                    if (isOrb) {
                        snapToEdge(v)
                    }
                    isDragging = false
                    true
                }
                else -> false
            }
        }
    }

    private fun viewParamsX(v: View): Int {
        return (v.layoutParams as? WindowManager.LayoutParams)?.x ?: 0
    }

    private fun viewParamsY(v: View): Int {
        return (v.layoutParams as? WindowManager.LayoutParams)?.y ?: 0
    }

    private fun snapToEdge(view: View) {
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val centerX = params.x + 28 // orb width ~ 56dp / 2
        params.x = if (centerX < screenWidth / 2) 0 else screenWidth - 56
        params.y = params.y.coerceIn(0, displayMetrics.heightPixels - 200)
        try {
            windowManager?.updateViewLayout(view, params)
        } catch (_: Exception) {
        }
    }

    // ──────────────────────────────────────────────
    // Pulse animation for the orb
    // ──────────────────────────────────────────────

    private fun startPulseAnimation(view: View) {
        val pulse = ScaleAnimation(
            1f, 1.08f, 1f, 1.08f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 1200
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }

        val glow = AlphaAnimation(0.7f, 1.0f).apply {
            duration = 1200
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }

        val set = AnimationSet(true).apply {
            addAnimation(pulse)
            addAnimation(glow)
        }

        view.startAnimation(set)
    }

    // ──────────────────────────────────────────────
    // Layout params helpers
    // ──────────────────────────────────────────────

    @SuppressLint("RtlHardcoded")
    private fun orbLayoutParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = 0
            y = 200
        }
    }

    @SuppressLint("RtlHardcoded")
    private fun expandedLayoutParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            x = 0
            y = 0
        }
    }
}
