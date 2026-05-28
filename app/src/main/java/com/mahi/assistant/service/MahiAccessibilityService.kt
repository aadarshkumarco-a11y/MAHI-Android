package com.mahi.assistant.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Accessibility service that provides screen interaction capabilities:
 * - Global actions (back, home, recents, notifications, quick settings)
 * - Click at specific screen coordinates
 * - Scroll up / down
 * - Read all visible on-screen text
 *
 * Must be enabled by the user in Settings → Accessibility → MAHI Assistant.
 *
 * The service configuration is set programmatically in [onServiceConnected]
 * so no separate XML configuration file is strictly required, though one
 * can be used for more fine-grained control.
 */
class MahiAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "MahiAccessibility"

        /** Shared singleton so other components can access the service. */
        @Volatile
        var instance: MahiAccessibilityService? = null
            private set

        // Action name constants
        const val ACTION_BACK = "back"
        const val ACTION_HOME = "home"
        const val ACTION_RECENTS = "recents"
        const val ACTION_NOTIFICATIONS = "notifications"
        const val ACTION_QUICK_SETTINGS = "quick_settings"
        const val ACTION_POWER_DIALOG = "power_dialog"
        const val ACTION_LOCK_SCREEN = "lock_screen"
        const val ACTION_TAKE_SCREENSHOT = "screenshot"
        const val ACTION_SCROLL_UP = "scroll_up"
        const val ACTION_SCROLL_DOWN = "scroll_down"
        const val ACTION_CLICK = "click"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _lastScreenText = MutableStateFlow("")
    val lastScreenText: StateFlow<String> = _lastScreenText.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    // ──────────────────────────────────────────────
    // Service lifecycle
    // ──────────────────────────────────────────────

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isConnected.value = true

        // Configure the service programmatically
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.DEFAULT or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 100
        }

        Log.i(TAG, "Accessibility service connected and configured")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We don't need to react to every event for our use-case.
        // Screen text reading is done on-demand via getScreenText().
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        instance = null
        _isConnected.value = false
        super.onDestroy()
    }

    // ──────────────────────────────────────────────
    // Public action API
    // ──────────────────────────────────────────────

    /**
     * Performs a named action. Supported values:
     * - "back", "home", "recents", "notifications", "quick_settings"
     * - "power_dialog", "lock_screen", "screenshot"
     * - "scroll_up", "scroll_down"
     * - "click x y"  (e.g. "click 540 1200")
     *
     * @return true if the action was dispatched successfully
     */
    fun performAction(action: String): Boolean {
        val trimmed = action.lowercase().trim()

        return when {
            // Global actions
            trimmed == ACTION_BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
            trimmed == ACTION_HOME -> performGlobalAction(GLOBAL_ACTION_HOME)
            trimmed == ACTION_RECENTS -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            trimmed == ACTION_NOTIFICATIONS -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            trimmed == ACTION_QUICK_SETTINGS -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
            trimmed == ACTION_POWER_DIALOG -> performGlobalActionSafe(GLOBAL_ACTION_POWER_DIALOG)
            trimmed == ACTION_LOCK_SCREEN -> performGlobalActionSafe(GLOBAL_ACTION_LOCK_SCREEN)
            trimmed == ACTION_TAKE_SCREENSHOT -> performGlobalActionSafe(GLOBAL_ACTION_TAKE_SCREENSHOT)

            // Scroll
            trimmed == ACTION_SCROLL_UP -> scrollUp()
            trimmed == ACTION_SCROLL_DOWN -> scrollDown()

            // Click at coordinates: "click 540 1200"
            trimmed.startsWith(ACTION_CLICK) -> {
                val parts = trimmed.split("\\s+".toRegex())
                if (parts.size >= 3) {
                    val x = parts[1].toIntOrNull()
                    val y = parts[2].toIntOrNull()
                    if (x != null && y != null) {
                        clickAt(x, y)
                    } else {
                        Log.w(TAG, "Invalid click coordinates: $action")
                        false
                    }
                } else {
                    Log.w(TAG, "Click requires x and y: $action")
                    false
                }
            }

            else -> {
                Log.w(TAG, "Unknown accessibility action: $action")
                false
            }
        }
    }

    // ──────────────────────────────────────────────
    // Global action helpers
    // ──────────────────────────────────────────────

    /**
     * Wrapper that safely calls global actions that may not exist on all API levels.
     */
    private fun performGlobalActionSafe(action: Int): Boolean {
        return try {
            performGlobalAction(action)
        } catch (e: Exception) {
            Log.e(TAG, "Global action $action not supported on this device", e)
            false
        }
    }

    // ──────────────────────────────────────────────
    // Click at coordinates (gesture)
    // ──────────────────────────────────────────────

    /**
     * Performs a tap gesture at the specified screen coordinates.
     */
    fun clickAt(x: Int, y: Int): Boolean {
        return try {
            val path = Path().apply {
                moveTo(x.toFloat(), y.toFloat())
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0L, 100L))
                .build()

            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.d(TAG, "Click gesture completed at ($x, $y)")
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "Click gesture cancelled at ($x, $y)")
                }
            }, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to click at ($x, $y)", e)
            false
        }
    }

    /**
     * Performs a long-press gesture at the specified screen coordinates.
     */
    fun longPressAt(x: Int, y: Int, durationMs: Long = 500L): Boolean {
        return try {
            val path = Path().apply {
                moveTo(x.toFloat(), y.toFloat())
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0L, durationMs))
                .build()

            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.d(TAG, "Long-press gesture completed at ($x, $y)")
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "Long-press gesture cancelled at ($x, $y)")
                }
            }, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to long-press at ($x, $y)", e)
            false
        }
    }

    // ──────────────────────────────────────────────
    // Scroll
    // ──────────────────────────────────────────────

    /**
     * Scrolls up by performing an upward swipe gesture in the center of the screen.
     */
    fun scrollUp(): Boolean {
        val bounds = getScreenBounds()
        val centerX = bounds.centerX()
        val startY = (bounds.bottom * 0.7).toInt()
        val endY = (bounds.bottom * 0.3).toInt()

        return performSwipe(centerX, startY, centerX, endY)
    }

    /**
     * Scrolls down by performing a downward swipe gesture in the center of the screen.
     */
    fun scrollDown(): Boolean {
        val bounds = getScreenBounds()
        val centerX = bounds.centerX()
        val startY = (bounds.bottom * 0.3).toInt()
        val endY = (bounds.bottom * 0.7).toInt()

        return performSwipe(centerX, startY, centerX, endY)
    }

    /**
     * Scrolls a specific node up or down.
     */
    fun scrollNode(action: Int, node: AccessibilityNodeInfo): Boolean {
        return node.performAction(action)
    }

    /**
     * Finds a scrollable node and scrolls it.
     */
    fun findAndScroll(direction: Int): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val scrollable = findScrollableNode(rootNode)
        if (scrollable != null) {
            val result = scrollable.performAction(direction)
            scrollable.recycle()
            return result
        }
        return false
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return AccessibilityNodeInfo.obtain(node)

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findScrollableNode(child)
            if (result != null) {
                child.recycle()
                return result
            }
            child.recycle()
        }
        return null
    }

    // ──────────────────────────────────────────────
    // Swipe gesture helper
    // ──────────────────────────────────────────────

    private fun performSwipe(
        startX: Int, startY: Int,
        endX: Int, endY: Int,
        durationMs: Long = 300L
    ): Boolean {
        return try {
            val path = Path().apply {
                moveTo(startX.toFloat(), startY.toFloat())
                lineTo(endX.toFloat(), endY.toFloat())
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0L, durationMs))
                .build()

            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.d(TAG, "Swipe gesture completed")
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "Swipe gesture cancelled")
                }
            }, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to perform swipe", e)
            false
        }
    }

    // ──────────────────────────────────────────────
    // Screen text reading
    // ──────────────────────────────────────────────

    /**
     * Reads all visible text on the current screen by traversing the
     * accessibility node tree. The result is also published to
     * [lastScreenText] StateFlow.
     *
     * @return All visible text concatenated with newlines
     */
    fun getScreenText(): String {
        val rootNode = rootInActiveWindow
        if (rootNode == null) {
            Log.w(TAG, "No active window root node available")
            return ""
        }

        val textBuilder = StringBuilder()
        collectTextFromNode(rootNode, textBuilder)
        rootNode.recycle()

        val result = textBuilder.toString().trim()
        scope.launch {
            _lastScreenText.value = result
        }
        return result
    }

    /**
     * Finds a node containing the given text and clicks it.
     *
     * @return true if a matching node was found and clicked
     */
    fun clickOnText(text: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val found = findNodeByText(rootNode, text)
        if (found != null) {
            val result = found.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            found.recycle()
            return result
        }
        return false
    }

    /**
     * Finds a node by content description and clicks it.
     */
    fun clickOnContentDescription(desc: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val found = findNodeByContentDescription(rootNode, desc)
        if (found != null) {
            val result = found.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            found.recycle()
            return result
        }
        return false
    }

    /**
     * Returns a list of all clickable nodes with their bounds and text.
     * Useful for building a UI map of the current screen.
     */
    fun getClickableElements(): List<ClickableElement> {
        val rootNode = rootInActiveWindow ?: return emptyList()
        val elements = mutableListOf<ClickableElement>()
        collectClickableNodes(rootNode, elements)
        rootNode.recycle()
        return elements
    }

    // ──────────────────────────────────────────────
    // Node tree traversal helpers
    // ──────────────────────────────────────────────

    private fun collectTextFromNode(node: AccessibilityNodeInfo, builder: StringBuilder) {
        // Collect text from the node itself
        val text = node.text
        if (text != null && text.isNotBlank()) {
            if (builder.isNotEmpty()) builder.append("\n")
            builder.append(text)
        }

        // Also check content description
        val contentDesc = node.contentDescription
        if (contentDesc != null && contentDesc.isNotBlank() && contentDesc != text?.toString()) {
            if (builder.isNotEmpty()) builder.append("\n")
            builder.append(contentDesc)
        }

        // Check hint text
        val hint = node.hintText
        if (hint != null && hint.isNotBlank()) {
            // Only add hints if they're meaningful
            if (builder.isNotEmpty()) builder.append("\n")
            builder.append("[hint: $hint]")
        }

        // Recurse into children
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectTextFromNode(child, builder)
            child.recycle()
        }
    }

    private fun findNodeByText(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        // Check this node
        val nodeText = node.text?.toString()
        if (nodeText != null && nodeText.contains(text, ignoreCase = true)) {
            return AccessibilityNodeInfo.obtain(node)
        }

        val nodeDesc = node.contentDescription?.toString()
        if (nodeDesc != null && nodeDesc.contains(text, ignoreCase = true)) {
            return AccessibilityNodeInfo.obtain(node)
        }

        // Recurse into children
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeByText(child, text)
            if (found != null) {
                child.recycle()
                return found
            }
            child.recycle()
        }
        return null
    }

    private fun findNodeByContentDescription(node: AccessibilityNodeInfo, desc: String): AccessibilityNodeInfo? {
        val nodeDesc = node.contentDescription?.toString()
        if (nodeDesc != null && nodeDesc.contains(desc, ignoreCase = true)) {
            return AccessibilityNodeInfo.obtain(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeByContentDescription(child, desc)
            if (found != null) {
                child.recycle()
                return found
            }
            child.recycle()
        }
        return null
    }

    private fun collectClickableNodes(
        node: AccessibilityNodeInfo,
        elements: MutableList<ClickableElement>
    ) {
        if (node.isClickable && node.isVisibleToUser) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            val text = node.text?.toString() ?: node.contentDescription?.toString() ?: ""
            if (text.isNotBlank()) {
                elements.add(
                    ClickableElement(
                        text = text,
                        bounds = bounds,
                        className = node.className?.toString() ?: "",
                        viewId = node.viewIdResourceName ?: ""
                    )
                )
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectClickableNodes(child, elements)
            child.recycle()
        }
    }

    // ──────────────────────────────────────────────
    // Utility
    // ──────────────────────────────────────────────

    private fun getScreenBounds(): Rect {
        val rootNode = rootInActiveWindow
        val bounds = Rect()
        rootNode?.getBoundsInScreen(bounds)
        rootNode?.recycle()
        if (bounds.isEmpty) {
            // Fallback to display metrics
            val metrics = resources.displayMetrics
            bounds.set(0, 0, metrics.widthPixels, metrics.heightPixels)
        }
        return bounds
    }
}

/**
 * Represents a clickable element on screen with its text, bounds, and identifiers.
 */
data class ClickableElement(
    val text: String,
    val bounds: Rect,
    val className: String,
    val viewId: String
)
