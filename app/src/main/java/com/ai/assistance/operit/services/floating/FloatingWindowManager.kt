package com.ai.assistance.operit.services.floating

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.ai.assistance.operit.util.AppLogger
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.WindowInsets
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.AIForegroundService
import com.ai.assistance.operit.data.model.AttachmentInfo
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.InputProcessingState
import com.ai.assistance.operit.data.model.PromptFunctionType
import com.ai.assistance.operit.services.FloatingChatService
import com.ai.assistance.operit.ui.common.displays.RainbowBorderOverlay
import com.ai.assistance.operit.ui.floating.FloatingChatWindow
import com.ai.assistance.operit.ui.floating.FloatingMode
import com.ai.assistance.operit.ui.floating.FloatingWindowTheme

enum class StatusIndicatorStyle {
    FULLSCREEN_RAINBOW,
    TOP_BAR
}

interface FloatingWindowCallback {
    fun onClose()
    fun onModeWillChange(newMode: FloatingMode) {}
    fun onSendMessage(message: String, promptType: PromptFunctionType = PromptFunctionType.CHAT)
    fun onSendMessageWithResult(
        message: String,
        promptType: PromptFunctionType = PromptFunctionType.CHAT,
        onResolved: (Boolean) -> Unit,
    ): Boolean {
        onSendMessage(message, promptType)
        onResolved(true)
        return true
    }
    fun onCancelMessage()
    fun onAttachmentRequest(request: String)
    fun onRemoveAttachment(filePath: String)
    fun getMessages(): List<ChatMessage>
    fun getAttachments(): List<AttachmentInfo>
    fun saveState()
    fun getColorScheme(): ColorScheme?
    fun getTypography(): Typography?
    fun getInputProcessingState(): State<InputProcessingState>
    fun getStatusIndicatorStyle(): StatusIndicatorStyle
}

class FloatingWindowManager(
        private val context: Context,
        private val state: FloatingWindowState,
        private val lifecycleOwner: LifecycleOwner,
        private val viewModelStoreOwner: ViewModelStoreOwner,
        private val savedStateRegistryOwner: SavedStateRegistryOwner,
        private val callback: FloatingWindowCallback
) {
    private val TAG = "FloatingWindowManager"
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var composeView: ComposeView? = null
    private var statusIndicatorView: ComposeView? = null
    private var focusDismissView: View? = null
    private var isViewAdded = false
    private var isIndicatorAdded = false
    private var sizeAnimator: ValueAnimator? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingImeFocusRunnable: Runnable? = null
    private var focusDismissOverlayRequested: Boolean = false
    private var windowDisplayEnabled: Boolean = true
    private var windowPersistentHidden: Boolean = false
    private var indicatorDisplayEnabled: Boolean = true
    private var indicatorPersistentEnabled: Boolean = false

    private fun cancelFocusBeforeExit() {
        val view = composeView ?: run {
            state.isTransitioning = false
            return
        }
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        try {
            view.clearFocus()
        } catch (_: Exception) {
        }
        try {
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        } catch (_: Exception) {
        }
        updateViewLayout { params ->
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED
        }
        pendingImeFocusRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingImeFocusRunnable = null
        focusDismissOverlayRequested = false
        setFocusDismissOverlayEnabled(false)
    }

    fun prepareForExit() {
        cancelFocusBeforeExit()
    }

    companion object {
        // Private flag to disable window move animations
        private const val PRIVATE_FLAG_NO_MOVE_ANIMATION = 0x00000040
        private const val FULLSCREEN_BLUR_RADIUS_DP = 48
        private const val IME_FOCUS_DELAY_MS = 200L
        private const val IME_FOCUS_RETRY_DELAY_MS = 50L
        private const val MAX_IME_FOCUS_RETRIES = 4
        private const val BALL_SAFE_MARGIN_DP = 8
        private const val QUICK_REPLY_SAFE_MARGIN_DP = 12
        private const val QUICK_REPLY_GAP_DP = 8
    }

    private data class SafeInsets(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    )

    private fun systemSafeInsets(): SafeInsets {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val insets =
                windowManager.currentWindowMetrics.windowInsets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
                )
            return SafeInsets(insets.left, insets.top, insets.right, insets.bottom)
        }
        val statusBarId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        val navigationBarId = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return SafeInsets(
            left = 0,
            top = if (statusBarId > 0) context.resources.getDimensionPixelSize(statusBarId) else 0,
            right = 0,
            bottom = if (navigationBarId > 0) context.resources.getDimensionPixelSize(navigationBarId) else 0,
        )
    }

    private fun quickReplyCardSizePx(): Pair<Int, Int> {
        val metrics = context.resources.displayMetrics
        val screenWidthDp = (metrics.widthPixels / metrics.density).toInt()
        return Pair(
            (MiraFloatingLayoutPolicy.cardWidthDp(screenWidthDp) * metrics.density).toInt(),
            (MiraFloatingLayoutPolicy.CARD_MAX_HEIGHT_DP * metrics.density).toInt(),
        )
    }

    private fun anchoredQuickReplyPosition(
        ballX: Int,
        ballY: Int,
        ballSize: Int,
        cardWidth: Int,
        cardHeight: Int,
    ): FloatingCardPosition {
        val metrics = context.resources.displayMetrics
        val density = metrics.density
        val systemInsets = systemSafeInsets()
        val margin = (QUICK_REPLY_SAFE_MARGIN_DP * density).toInt()
        return MiraFloatingLayoutPolicy.anchoredCardPosition(
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels,
            ballX = ballX,
            ballY = ballY,
            ballSize = ballSize,
            cardWidth = cardWidth,
            cardHeight = cardHeight,
            safeLeft = systemInsets.left + margin,
            safeTop = systemInsets.top + margin,
            safeRight = systemInsets.right + margin,
            safeBottom = systemInsets.bottom + margin,
            gap = (QUICK_REPLY_GAP_DP * density).toInt(),
        )
    }

    private fun clampBallPosition(x: Int, y: Int, ballSize: Int): FloatingBallPosition {
        val metrics = context.resources.displayMetrics
        val insets = systemSafeInsets()
        return MiraFloatingLayoutPolicy.clampBallPosition(
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels,
            x = x,
            y = y,
            ballSize = ballSize,
            safeLeft = insets.left,
            safeTop = insets.top,
            safeRight = insets.right,
            safeBottom = insets.bottom,
            margin = (BALL_SAFE_MARGIN_DP * metrics.density).toInt(),
        )
    }

    private fun snapBallToEdge(enabled: Boolean) {
        if (!enabled) return
        if (
            state.currentMode.value != FloatingMode.BALL &&
                state.currentMode.value != FloatingMode.VOICE_BALL
        ) return

        updateViewLayout { params ->
            val metrics = context.resources.displayMetrics
            val insets = systemSafeInsets()
            val ballSize = (state.ballSize.value.value * metrics.density).toInt()
            val target = MiraFloatingLayoutPolicy.nearestHorizontalEdgePosition(
                screenWidth = metrics.widthPixels,
                screenHeight = metrics.heightPixels,
                x = params.x,
                y = params.y,
                ballSize = ballSize,
                safeLeft = insets.left,
                safeTop = insets.top,
                safeRight = insets.right,
                safeBottom = insets.bottom,
                margin = (BALL_SAFE_MARGIN_DP * metrics.density).toInt(),
            )
            params.x = target.x
            params.y = target.y
            state.x = target.x
            state.y = target.y
            state.lastBallPositionX = target.x
            state.lastBallPositionY = target.y
            state.hasSavedBallPosition = true
            state.isAtEdge.value = true
        }
        callback.saveState()
    }

    private fun resolveSoftInputModeForMode(mode: FloatingMode): Int {
        return when (mode) {
            FloatingMode.FULLSCREEN -> WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            else -> WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun show(): Boolean {
        if (isViewAdded) {
            AppLogger.d(TAG, "Floating view already added")
            return true
        }

        try {
            ensureFocusDismissView()

            composeView =
                    ComposeView(context).apply {
                        setViewTreeLifecycleOwner(lifecycleOwner)
                        setViewTreeViewModelStoreOwner(viewModelStoreOwner)
                        setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)

                        setContent {
                            FloatingWindowTheme(
                                    colorScheme = callback.getColorScheme(),
                                    typography = callback.getTypography()
                            ) { FloatingChatUi() }
                        }
                        setOnTouchListener { _, event ->
                            if (
                                event.action == MotionEvent.ACTION_OUTSIDE &&
                                    state.currentMode.value == FloatingMode.WINDOW
                            ) {
                                switchMode(FloatingMode.BALL)
                                true
                            } else {
                                false
                            }
                        }
                    }

            val params = createLayoutParams()
            windowManager.addView(composeView, params)
            isViewAdded = true
            AppLogger.d(TAG, "Floating view added at (${params.x}, ${params.y})")
            return true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error creating floating view", e)
            return false
        }
    }

    fun destroy() {
        sizeAnimator?.cancel()
        sizeAnimator = null
        mainHandler.removeCallbacksAndMessages(null)
        pendingImeFocusRunnable = null
        state.isTransitioning = false
        state.ballExploding.value = false
        hideStatusIndicator()
        if (isViewAdded) {
            composeView?.let {
                cancelFocusBeforeExit()
                try {
                    windowManager.removeView(it)
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error removing view", e)
                }
                composeView = null
                isViewAdded = false
            }
        }

        focusDismissView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error removing focus dismiss view", e)
            }
        }
        focusDismissView = null
    }

    private fun ensureFocusDismissView() {
        if (focusDismissView != null) return

        val dismissView = View(context).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            visibility = View.GONE
            isClickable = true
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    AppLogger.d(
                        TAG,
                        "Focus dismiss overlay tapped: x=${event.rawX}, y=${event.rawY}, mode=${state.currentMode.value}"
                    )
                    this@FloatingWindowManager.setFocusable(false)
                    if (state.currentMode.value == FloatingMode.WINDOW) {
                        switchMode(FloatingMode.BALL)
                    }
                }
                true
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        try {
            windowManager.addView(dismissView, params)
            focusDismissView = dismissView
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error creating focus dismiss view", e)
        }
    }

    private fun setFocusDismissOverlayEnabled(enabled: Boolean) {
        val view = focusDismissView ?: return
        val canShow =
            enabled &&
                state.currentMode.value == FloatingMode.WINDOW &&
                !windowPersistentHidden &&
                windowDisplayEnabled
        view.visibility = if (canShow) View.VISIBLE else View.GONE
    }

    @Composable
    private fun FloatingChatUi() {
        FloatingChatWindow(
                messages = callback.getMessages(),
                width = state.windowWidth.value,
                height = state.windowHeight.value,
                windowScale = state.windowScale.value,
                onScaleChange = { newScale ->
                    state.windowScale.value = newScale.coerceIn(0.5f, 1.0f)
                    updateWindowSizeInLayoutParams()
                    callback.saveState()
                },
                onClose = {
                    cancelFocusBeforeExit()
                    callback.onClose()
                },
                onResize = { newWidth, newHeight ->
                    state.windowWidth.value = newWidth
                    state.windowHeight.value = newHeight
                    updateWindowSizeInLayoutParams()
                    callback.saveState()
                },
                currentMode = state.currentMode.value,
                previousMode = state.previousMode,
                ballSize = state.ballSize.value,
                onModeChange = { newMode -> switchMode(newMode) },
                onMove = { dx, dy, scale -> onMove(dx, dy, scale) },
                snapToEdge = { enabled -> snapBallToEdge(enabled) },
                isAtEdge = state.isAtEdge.value,
                saveWindowState = { callback.saveState() },
                 onSendMessage = { message, promptType ->
                     callback.onSendMessage(message, promptType)
                 },
                 onSendMessageWithResult = { message, promptType, onResolved ->
                     callback.onSendMessageWithResult(message, promptType, onResolved)
                 },
                onCancelMessage = { callback.onCancelMessage() },
                onInputFocusRequest = { setFocusable(it) },
                attachments = callback.getAttachments(),
                onAttachmentRequest = { callback.onAttachmentRequest(it) },
                onRemoveAttachment = { callback.onRemoveAttachment(it) },
                chatService = context as? FloatingChatService,
                windowState = state,
                inputProcessingState = callback.getInputProcessingState(),
                screenWidth =
                    (context.resources.displayMetrics.widthPixels /
                        context.resources.displayMetrics.density).dp,
                screenHeight =
                    (context.resources.displayMetrics.heightPixels /
                        context.resources.displayMetrics.density).dp,
                currentX = state.x.toFloat(),
                currentY = state.y.toFloat(),
        )
    }

    fun setFloatingWindowVisible(visible: Boolean) {
        windowDisplayEnabled = visible
        refreshWindowAndIndicatorVisibility()
        AppLogger.d(TAG, "Floating window visibility set to: $visible.")
    }

    fun setFloatingWindowPersistentHidden(hidden: Boolean) {
        windowPersistentHidden = hidden
        refreshWindowAndIndicatorVisibility()
        AppLogger.d(TAG, "Floating window persistent hidden set to: $hidden.")
    }

    fun setStatusIndicatorVisible(visible: Boolean) {
        indicatorDisplayEnabled = visible
        refreshWindowAndIndicatorVisibility()
        AppLogger.d(TAG, "Status indicator visibility set to: $visible.")
    }

    fun setStatusIndicatorPersistentVisible(visible: Boolean) {
        indicatorPersistentEnabled = visible
        refreshWindowAndIndicatorVisibility()
        AppLogger.d(TAG, "Status indicator persistent visibility set to: $visible.")
    }

    private fun refreshWindowAndIndicatorVisibility() {
        val currentMode = state.currentMode.value
        val view = composeView

        val windowVisible = !windowPersistentHidden && windowDisplayEnabled

        view?.let { v ->
            v.visibility = if (windowVisible) View.VISIBLE else View.GONE
            if (windowVisible) {
                updateViewLayout { params ->
                    params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                }
            }
        }

        setFocusDismissOverlayEnabled(focusDismissOverlayRequested)

        val indicatorShouldShow = when {
            !indicatorDisplayEnabled && !indicatorPersistentEnabled -> false
            indicatorPersistentEnabled -> true
            else -> !windowVisible &&
                    (currentMode == FloatingMode.FULLSCREEN || currentMode == FloatingMode.WINDOW)
        }

        if (indicatorShouldShow) {
            showStatusIndicator()
        } else {
            hideStatusIndicator()
        }
    }

    @Composable
    private fun TopBarStatusIndicator() {
        Card(
            modifier = Modifier
                .padding(16.dp)
                .wrapContentSize(),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f)
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    strokeWidth = 2.5.dp
                )
                Text(
                    text = context.getString(R.string.ui_automation_in_progress),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }

    private fun showStatusIndicator() {
        if (isIndicatorAdded) return
        val style = callback.getStatusIndicatorStyle()
        statusIndicatorView = ComposeView(context).apply {
            // Set the necessary owners for the ComposeView to work correctly.
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(viewModelStoreOwner)
            setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)

            setContent {
                FloatingWindowTheme(
                    colorScheme = callback.getColorScheme(),
                    typography = callback.getTypography()
                ) {
                    when (style) {
                        StatusIndicatorStyle.FULLSCREEN_RAINBOW -> FullscreenRainbowStatusIndicator()
                        StatusIndicatorStyle.TOP_BAR -> TopBarStatusIndicator()
                    }
                }
            }
        }
        val params = when (style) {
            StatusIndicatorStyle.FULLSCREEN_RAINBOW -> WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        @Suppress("DEPRECATION")
                        WindowManager.LayoutParams.FLAG_FULLSCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                applyFullscreenOverlayWindowPolicy(this, true)
            }
            StatusIndicatorStyle.TOP_BAR -> WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = (context.resources.displayMetrics.density * 16).toInt()
            }
        }
        windowManager.addView(statusIndicatorView, params)
        isIndicatorAdded = true
        AppLogger.d(TAG, "Status indicator shown.")
    }

    private fun hideStatusIndicator() {
        if (isIndicatorAdded) {
            statusIndicatorView?.let {
                try {
                    windowManager.removeView(it)
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error removing status indicator view", e)
                }
            }
            statusIndicatorView = null
            isIndicatorAdded = false
            AppLogger.d(TAG, "Status indicator hidden.")
        }
    }

    fun setStatusIndicatorAlpha(alpha: Float) {
        val view = statusIndicatorView ?: return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            view.alpha = alpha
        } else {
            val latch = java.util.concurrent.CountDownLatch(1)
            Handler(Looper.getMainLooper()).post {
                statusIndicatorView?.alpha = alpha
                latch.countDown()
            }
            try {
                latch.await(200, java.util.concurrent.TimeUnit.MILLISECONDS)
            } catch (e: InterruptedException) {
                AppLogger.e(TAG, "setStatusIndicatorAlpha interrupted", e)
                Thread.currentThread().interrupt()
            }
        }
    }

    @Composable
    private fun FullscreenRainbowStatusIndicator() {
        RainbowBorderOverlay()
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val density = displayMetrics.density

        val params =
                WindowManager.LayoutParams(
                        0, // width
                        0, // height
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        0, // flags
                        PixelFormat.TRANSLUCENT
                )
        params.gravity = Gravity.TOP or Gravity.START
        // Overlay startup should be immediate; the platform window animation made the ball visibly jump in.
        params.windowAnimations = 0

        // Disable system move animations to allow custom animations to take full control
        setPrivateFlag(params, PRIVATE_FLAG_NO_MOVE_ANIMATION)

        when (state.currentMode.value) {
            FloatingMode.FULLSCREEN, FloatingMode.SCREEN_OCR -> {
                params.width = WindowManager.LayoutParams.MATCH_PARENT
                params.height = WindowManager.LayoutParams.MATCH_PARENT
                params.flags =
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                state.x = 0
                state.y = 0
            }
            FloatingMode.BALL, FloatingMode.VOICE_BALL -> {
                val ballSizeInPx = (state.ballSize.value.value * density).toInt()
                params.width = ballSizeInPx
                params.height = ballSizeInPx
                params.flags =
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

                val restored = clampBallPosition(
                    x = if (state.hasSavedBallPosition) state.lastBallPositionX else state.x,
                    y = if (state.hasSavedBallPosition) state.lastBallPositionY else state.y,
                    ballSize = ballSizeInPx,
                )
                state.x = restored.x
                state.y = restored.y
                state.lastBallPositionX = restored.x
                state.lastBallPositionY = restored.y
                state.hasSavedBallPosition = true
            }
            FloatingMode.WINDOW -> {
                val (cardWidth, cardHeight) = quickReplyCardSizePx()
                params.width = WindowManager.LayoutParams.WRAP_CONTENT
                params.height = WindowManager.LayoutParams.WRAP_CONTENT
                params.flags =
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                val position = anchoredQuickReplyPosition(
                    ballX = if (state.hasSavedBallPosition) state.lastBallPositionX else state.x,
                    ballY = if (state.hasSavedBallPosition) state.lastBallPositionY else state.y,
                    ballSize = (state.ballSize.value.value * density).toInt(),
                    cardWidth = cardWidth,
                    cardHeight = cardHeight,
                )
                state.x = position.x
                state.y = position.y
            }
            FloatingMode.RESULT_DISPLAY -> {
                params.width = WindowManager.LayoutParams.WRAP_CONTENT
                params.height = WindowManager.LayoutParams.WRAP_CONTENT
                params.flags =
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                
                // 保持位置逻辑与球体类似，确保可见
                val ballSizeInPx = (state.ballSize.value.value * density).toInt()
                val minVisible = ballSizeInPx / 2
                state.x = state.x.coerceIn(-ballSizeInPx + minVisible, screenWidth - minVisible)
                state.y = state.y.coerceIn(0, screenHeight - minVisible)
            }
        }

        params.softInputMode = resolveSoftInputModeForMode(state.currentMode.value)
        params.x = state.x
        params.y = state.y
        applyFullscreenOverlayWindowPolicy(
            params,
            state.currentMode.value == FloatingMode.FULLSCREEN ||
                state.currentMode.value == FloatingMode.SCREEN_OCR
        )

        applyFullscreenBlur(params, state.currentMode.value == FloatingMode.FULLSCREEN)

        state.isAtEdge.value = params.width > 0 && isAtEdge(params.x, params.width)

        return params
    }

    private fun applyFullscreenOverlayWindowPolicy(
        params: WindowManager.LayoutParams,
        enabled: Boolean
    ) {
        if (!enabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private fun setPrivateFlag(params: WindowManager.LayoutParams, flags: Int) {
        try {
            val field = params.javaClass.getField("privateFlags")
            field.setInt(params, field.getInt(params) or flags)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to set privateFlags", e)
        }
    }

    private fun applyFullscreenBlur(params: WindowManager.LayoutParams, enabled: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            AppLogger.d(TAG, "Fullscreen blur skipped: API < 31")
            state.fullscreenSystemBlurActive.value = false
            return
        }
        val crossWindowBlurEnabled = windowManager.isCrossWindowBlurEnabled
        if (enabled) {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            val density = context.resources.displayMetrics.density
            val blurRadiusPx = (FULLSCREEN_BLUR_RADIUS_DP * density).toInt()
            params.setBlurBehindRadius(blurRadiusPx)
            AppLogger.d(
                TAG,
                "Fullscreen blur enabled: radiusPx=$blurRadiusPx, crossWindowBlurEnabled=$crossWindowBlurEnabled"
            )
        } else {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
            params.setBlurBehindRadius(0)
            AppLogger.d(
                TAG,
                "Fullscreen blur disabled: crossWindowBlurEnabled=$crossWindowBlurEnabled"
            )
        }
        state.fullscreenSystemBlurActive.value = enabled && crossWindowBlurEnabled
    }

    private fun isAtEdge(x: Int, width: Int): Boolean {
        val screenWidth = context.resources.displayMetrics.widthPixels
        // A small tolerance to account for rounding errors or slight offsets
        val tolerance = 5 
        return x <= tolerance || x >= screenWidth - width - tolerance
    }

    private fun updateWindowSizeInLayoutParams() {
        updateViewLayout { params ->
            if (state.currentMode.value == FloatingMode.WINDOW) {
                params.width = WindowManager.LayoutParams.WRAP_CONTENT
                params.height = WindowManager.LayoutParams.WRAP_CONTENT
                return@updateViewLayout
            }
            val density = context.resources.displayMetrics.density
            val scale = state.windowScale.value
            val widthDp = state.windowWidth.value
            val heightDp = state.windowHeight.value
            params.width = (widthDp.value * density * scale).toInt()
            params.height = (heightDp.value * density * scale).toInt()
        }
    }

    private fun updateViewLayout(configure: (WindowManager.LayoutParams) -> Unit = {}) {
        composeView?.let { view ->
            val params = view.layoutParams as WindowManager.LayoutParams
            configure(params)
            windowManager.updateViewLayout(view, params)
        }
    }

    private fun calculateCenteredPosition(
            fromX: Int,
            fromY: Int,
            fromWidth: Int,
            fromHeight: Int,
            toWidth: Int,
            toHeight: Int
    ): Pair<Int, Int> {
        val centerX = fromX + fromWidth / 2
        val centerY = fromY + fromHeight / 2
        val newX = centerX - toWidth / 2
        val newY = centerY - toHeight / 2
        return Pair(newX, newY)
    }

    private fun switchMode(newMode: FloatingMode) {
        if (state.isTransitioning || state.currentMode.value == newMode) return
        state.isTransitioning = true

        if (newMode == FloatingMode.BALL || newMode == FloatingMode.VOICE_BALL) {
            cancelFocusBeforeExit()
        }

        val wasFullscreen =
            state.currentMode.value == FloatingMode.FULLSCREEN ||
                state.currentMode.value == FloatingMode.SCREEN_OCR
        val willFullscreen = newMode == FloatingMode.FULLSCREEN || newMode == FloatingMode.SCREEN_OCR

        // 取消之前的动画
        sizeAnimator?.cancel()

        val view = composeView ?: return
        val currentParams = view.layoutParams as WindowManager.LayoutParams

        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val density = displayMetrics.density

        val startWidth = currentParams.width
        val startHeight = currentParams.height
        val startX = currentParams.x
        val startY = currentParams.y
        
        com.ai.assistance.operit.util.AppLogger.d("FloatingWindowManager", 
            "switchMode: from=${state.currentMode.value} to=$newMode, " +
            "startPos=($startX,$startY), startSize=($startWidth,$startHeight), " +
            "screenSize=($screenWidth,$screenHeight)")

        // Logic for leaving a mode
        state.previousMode = state.currentMode.value
        when (state.currentMode.value) {
            FloatingMode.BALL, FloatingMode.VOICE_BALL -> {
                state.lastBallPositionX = currentParams.x
                state.lastBallPositionY = currentParams.y
                state.hasSavedBallPosition = true
            }
            FloatingMode.WINDOW -> {
                state.lastWindowPositionX = currentParams.x
                state.lastWindowPositionY = currentParams.y
                state.lastWindowScale = state.windowScale.value
            }
            FloatingMode.FULLSCREEN -> {
                // Leaving fullscreen, no special state to save
            }
            FloatingMode.SCREEN_OCR -> {
                // Leaving screen ocr, no special state to save
            }
            FloatingMode.RESULT_DISPLAY -> {
                // Leaving result display, no special state to save
            }
        }

        callback.onModeWillChange(newMode)
        state.currentMode.value = newMode
        if (newMode != FloatingMode.WINDOW) {
            pendingImeFocusRunnable?.let { mainHandler.removeCallbacks(it) }
            pendingImeFocusRunnable = null
            focusDismissOverlayRequested = false
            setFocusDismissOverlayEnabled(false)
        }
        callback.saveState()

        if (wasFullscreen != willFullscreen) {
            try {
                AIForegroundService.setWakeListeningSuspendedForFloatingFullscreen(
                    context.applicationContext,
                    willFullscreen
                )
            } catch (_: Exception) {
            }
        }

        // 计算目标尺寸和位置
        data class TargetParams(
            val width: Int,
            val height: Int,
            val x: Int,
            val y: Int,
            val flags: Int,
            val gravity: Int = Gravity.TOP or Gravity.START,
            val blurEnabled: Boolean = false
        )

        val target = when (newMode) {
                FloatingMode.BALL, FloatingMode.VOICE_BALL -> {
                val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                    val ballSizeInPx = (state.ballSize.value.value * density).toInt()
                
                val (newX, newY) = if (state.hasSavedBallPosition) {
                    Pair(state.lastBallPositionX, state.lastBallPositionY)
                } else {
                    val actualStartWidth = when {
                        startWidth == WindowManager.LayoutParams.MATCH_PARENT -> screenWidth
                        startWidth <= 0 -> view.width.coerceAtLeast(ballSizeInPx)
                        else -> startWidth
                    }
                    val actualStartHeight = when {
                        startHeight == WindowManager.LayoutParams.MATCH_PARENT -> screenHeight
                        startHeight <= 0 -> view.height.coerceAtLeast(ballSizeInPx)
                        else -> startHeight
                    }
                    
                    calculateCenteredPosition(
                        startX, startY, actualStartWidth, actualStartHeight,
                        ballSizeInPx, ballSizeInPx
                    )
                }

                val finalPosition = clampBallPosition(newX, newY, ballSizeInPx)
                state.lastBallPositionX = finalPosition.x
                state.lastBallPositionY = finalPosition.y
                state.hasSavedBallPosition = true
                AppLogger.d(
                    TAG,
                    "Ball target restored: (${finalPosition.x},${finalPosition.y}), size=$ballSizeInPx"
                )
                TargetParams(
                    ballSizeInPx,
                    ballSizeInPx,
                    finalPosition.x,
                    finalPosition.y,
                    flags,
                )
                }
                FloatingMode.WINDOW -> {
                val flags =
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                val (width, height) = quickReplyCardSizePx()
                
                val isFromBall = state.previousMode == FloatingMode.BALL || 
                                state.previousMode == FloatingMode.VOICE_BALL

                val position =
                    if (isFromBall) {
                        anchoredQuickReplyPosition(
                            ballX = startX,
                            ballY = startY,
                            ballSize = (state.ballSize.value.value * density).toInt(),
                            cardWidth = width,
                            cardHeight = height,
                        )
                    } else {
                        anchoredQuickReplyPosition(
                            ballX = state.lastBallPositionX,
                            ballY = state.lastBallPositionY,
                            ballSize = (state.ballSize.value.value * density).toInt(),
                            cardWidth = width,
                            cardHeight = height,
                        )
                    }
                state.windowScale.value = 1f
                TargetParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    position.x,
                    position.y,
                    flags,
                )
            }
            FloatingMode.FULLSCREEN, FloatingMode.SCREEN_OCR -> {
                val flags =
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                TargetParams(
                    screenWidth,
                    screenHeight,
                    0,
                    0,
                    flags,
                    blurEnabled = newMode == FloatingMode.FULLSCREEN
                )
            }
            FloatingMode.RESULT_DISPLAY -> {
                val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                
                val ballSizeInPx = (state.ballSize.value.value * density).toInt()
                val ballCenter = startX + ballSizeInPx / 2
                
                val finalGravity: Int
                val finalX: Int
                
                if (ballCenter > screenWidth / 2) {
                    // 球在右半屏，结果显示在球左侧（右对齐）
                    finalGravity = Gravity.TOP or Gravity.END
                    // x 是距离右边的距离
                    finalX = screenWidth - (startX + ballSizeInPx)
                } else {
                    // 球在左半屏，结果显示在球右侧（左对齐）
                    finalGravity = Gravity.TOP or Gravity.START
                    finalX = startX
                }

                TargetParams(
                    WindowManager.LayoutParams.WRAP_CONTENT, 
                    WindowManager.LayoutParams.WRAP_CONTENT, 
                    finalX, 
                    startY, 
                    flags,
                    finalGravity
                )
            }
        }

        if (newMode == FloatingMode.BALL || newMode == FloatingMode.VOICE_BALL) {
            callback.saveState()
        }

        // 判断是否在球模式和其他模式之间切换
        val isBallTransition = (state.previousMode == FloatingMode.BALL || 
                                state.previousMode == FloatingMode.VOICE_BALL) ||
                               (newMode == FloatingMode.BALL || newMode == FloatingMode.VOICE_BALL)
        
        if (isBallTransition) {
            // 球模式切换：需要与 Compose AnimatedContent 动画同步
            val isToBall = newMode == FloatingMode.BALL || newMode == FloatingMode.VOICE_BALL
            val isFromBall = state.previousMode == FloatingMode.BALL || state.previousMode == FloatingMode.VOICE_BALL
            val isBallToWindow = isFromBall && newMode == FloatingMode.WINDOW
            
            if (isToBall && !isFromBall) {
                // 其他模式 -> 球模式
                // AnimatedContent: 旧内容在 150ms 内 fadeOut + scaleOut，新内容延迟 150ms 后用 350ms fadeIn + scaleIn
                // 策略：延迟 150ms 后再改变窗口物理尺寸，这样旧内容先消失，然后窗口变小，球再出现
                mainHandler.postDelayed({
                    updateViewLayout { params ->
                        params.width = target.width
                        params.height = target.height
                        params.x = target.x
                        params.y = target.y
                        params.flags = target.flags
                        params.gravity = target.gravity
                        params.softInputMode = resolveSoftInputModeForMode(newMode)
                        applyFullscreenOverlayWindowPolicy(params, willFullscreen)
                        applyFullscreenBlur(params, target.blurEnabled)
                        
                        // Sync state with params
                        state.x = params.x
                        state.y = params.y
                    }
                }, 150) // 与 fadeOut/scaleOut 的时长匹配
                
            } else if (isBallToWindow) {
                state.ballExploding.value = false
                updateViewLayout { params ->
                    params.width = target.width
                    params.height = target.height
                    params.x = target.x
                    params.y = target.y
                    params.flags = target.flags
                    params.gravity = target.gravity
                    params.softInputMode = resolveSoftInputModeForMode(newMode)
                    applyFullscreenOverlayWindowPolicy(params, willFullscreen)
                    applyFullscreenBlur(params, target.blurEnabled)
                    state.x = params.x
                    state.y = params.y
                }
                state.isTransitioning = false
            } else if (isFromBall && !isToBall) {
                // 球模式 -> 其他模式：触发淡出动画，球平滑消失
                // 1. 触发淡出动画（100ms）
                state.ballExploding.value = true
                
                // 2. 延迟 100ms 后改变窗口尺寸（此时球已经淡出消失）
                mainHandler.postDelayed({
                    updateViewLayout { params ->
                        params.width = target.width
                        params.height = target.height
                        params.x = target.x
                        params.y = target.y
                        params.flags = target.flags
                        params.gravity = target.gravity
                        params.softInputMode = resolveSoftInputModeForMode(newMode)
                        applyFullscreenOverlayWindowPolicy(params, willFullscreen)
                        applyFullscreenBlur(params, target.blurEnabled)
                        
                        // Sync state with params
                        state.x = params.x
                        state.y = params.y
                    }
                    
                    // 重置淡出状态
                    state.ballExploding.value = false
                }, 100) // 与淡出动画时长匹配
            } else {
                // 球模式之间切换：立即更新窗口尺寸
                updateViewLayout { params ->
                    params.width = target.width
                    params.height = target.height
                    params.x = target.x
                    params.y = target.y
                    params.flags = target.flags
                    params.gravity = target.gravity
                    params.softInputMode = resolveSoftInputModeForMode(newMode)
                    applyFullscreenOverlayWindowPolicy(params, willFullscreen)
                    applyFullscreenBlur(params, target.blurEnabled)
                    
                    // Sync state with params
                    state.x = params.x
                    state.y = params.y
                }
            }
            
            // 延迟标记过渡完成，与 AnimatedContent 动画时长匹配
            if (!isBallToWindow) {
                mainHandler.postDelayed({
                    state.isTransitioning = false
                }, 500) // 匹配 AnimatedContent 的最长动画时长
            }
        } else {
            // 非球模式切换（如窗口↔全屏）：立即改变窗口尺寸
            updateViewLayout { params ->
                params.width = target.width
                params.height = target.height
                params.x = target.x
                params.y = target.y
                params.flags = target.flags
                params.gravity = target.gravity
                params.softInputMode = resolveSoftInputModeForMode(newMode)
                applyFullscreenOverlayWindowPolicy(params, willFullscreen)
                applyFullscreenBlur(params, target.blurEnabled)

                // Sync state with params
                state.x = params.x
                state.y = params.y
            }

            // 立即标记过渡完成
            state.isTransitioning = false
        }
    }

    private fun onMove(dx: Float, dy: Float, scale: Float) {
        if (state.currentMode.value == FloatingMode.FULLSCREEN) return // Disable move in fullscreen

        updateViewLayout { params ->
            val displayMetrics = context.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            val density = displayMetrics.density

            state.windowScale.value = scale

            val sensitivity =
                    if (state.currentMode.value == FloatingMode.BALL ||
                                    state.currentMode.value == FloatingMode.VOICE_BALL
                    )
                            1.0f
                    else scale
            params.x += (dx * sensitivity).toInt()
            params.y += (dy * sensitivity).toInt()

            if (state.currentMode.value == FloatingMode.BALL ||
                            state.currentMode.value == FloatingMode.VOICE_BALL
            ) {
                val ballSize = (state.ballSize.value.value * density).toInt()
                val position = clampBallPosition(params.x, params.y, ballSize)
                params.x = position.x
                params.y = position.y
                state.lastBallPositionX = position.x
                state.lastBallPositionY = position.y
                state.hasSavedBallPosition = true
                state.isAtEdge.value = false
            } else if (state.currentMode.value == FloatingMode.WINDOW) {
                val cardWidth = composeView?.width?.takeIf { it > 0 } ?: quickReplyCardSizePx().first
                val cardHeight = composeView?.height?.takeIf { it > 0 } ?: quickReplyCardSizePx().second
                val insets = systemSafeInsets()
                val margin = (QUICK_REPLY_SAFE_MARGIN_DP * density).toInt()
                val minX = insets.left + margin
                val maxX = (screenWidth - insets.right - margin - cardWidth).coerceAtLeast(minX)
                val minY = insets.top + margin
                val maxY = (screenHeight - insets.bottom - margin - cardHeight).coerceAtLeast(minY)
                params.x = params.x.coerceIn(minX, maxX)
                params.y = params.y.coerceIn(minY, maxY)
            } else {
                val windowWidth = (state.windowWidth.value.value * density * scale).toInt()
                val windowHeight = (state.windowHeight.value.value * density * scale).toInt()
                val minVisibleWidth = (windowWidth * 2 / 3)
                val minVisibleHeight = (windowHeight * 2 / 3)
                params.x =
                        params.x.coerceIn(
                                -(windowWidth - minVisibleWidth),
                                screenWidth - minVisibleWidth / 2
                        )
                params.y = params.y.coerceIn(0, screenHeight - minVisibleHeight)
            }
            state.x = params.x
            state.y = params.y
        }
    }

    private fun setFocusable(needsFocus: Boolean) {
        val view = composeView ?: return
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        AppLogger.d(TAG, "setFocusable(needsFocus=$needsFocus, mode=${state.currentMode.value})")

        if (needsFocus) {
            pendingImeFocusRunnable?.let { mainHandler.removeCallbacks(it) }
            pendingImeFocusRunnable = null
            focusDismissOverlayRequested = true
            setFocusDismissOverlayEnabled(true)

            // Step 1: 更新窗口参数使其可获取焦点
            updateViewLayout { params ->
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()

                // Keep background tappable while IME is active.
                if (state.currentMode.value == FloatingMode.WINDOW) {
                    params.flags =
                            params.flags or
                                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                }

                @Suppress("DEPRECATION")
                params.softInputMode = resolveSoftInputModeForMode(state.currentMode.value)
            }

            // Step 2: 等待Compose真正建立输入焦点后再显示键盘
            // 这里不能直接依赖固定延迟，否则在焦点宿主尚未准备好时会触发IMM空指针
            scheduleImeShow(view, imm)
        } else {
            pendingImeFocusRunnable?.let { mainHandler.removeCallbacks(it) }
            pendingImeFocusRunnable = null
            focusDismissOverlayRequested = false
            setFocusDismissOverlayEnabled(false)

            // Step 1: 立即清理悬浮窗焦点并隐藏键盘，避免阻塞外部输入框抢焦点
            try {
                view.findFocus()?.clearFocus()
            } catch (_: Exception) {
            }
            try {
                view.clearFocus()
            } catch (_: Exception) {
            }
            imm.hideSoftInputFromWindow(view.windowToken, 0)

            // Step 2: 立即恢复窗口不可聚焦状态（全屏模式除外）
            updateViewLayout { params ->
                if (state.currentMode.value != FloatingMode.FULLSCREEN && state.currentMode.value != FloatingMode.SCREEN_OCR) {
                    params.flags =
                            params.flags or
                                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    if (state.currentMode.value == FloatingMode.WINDOW) {
                        params.flags =
                            params.flags or
                                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                    } else {
                        params.flags =
                            params.flags and
                                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL.inv()
                        params.flags =
                            params.flags and
                                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH.inv()
                    }
                }
                params.softInputMode = resolveSoftInputModeForMode(state.currentMode.value)
            }
            val lp = view.layoutParams as? WindowManager.LayoutParams
            AppLogger.d(
                TAG,
                "setFocusable(false) applied: hasFocus=${view.hasFocus()}, findFocus=${view.findFocus() != null}, flags=${lp?.flags}"
            )
        }
    }

    private fun scheduleImeShow(
        rootView: View,
        imm: InputMethodManager,
        retryCount: Int = 0,
        delayMillis: Long = IME_FOCUS_DELAY_MS
    ) {
        lateinit var imeRunnable: Runnable
        imeRunnable = Runnable {
            if (pendingImeFocusRunnable !== imeRunnable) return@Runnable

            if (composeView !== rootView || !isViewAdded) {
                pendingImeFocusRunnable = null
                AppLogger.d(TAG, "Skip IME request: floating view is no longer active.")
                return@Runnable
            }

            if (!rootView.isAttachedToWindow || rootView.windowToken == null) {
                if (retryCount >= MAX_IME_FOCUS_RETRIES) {
                    pendingImeFocusRunnable = null
                    AppLogger.w(
                        TAG,
                        "Skip IME request: floating view is still not attached after $MAX_IME_FOCUS_RETRIES retries."
                    )
                    return@Runnable
                }

                AppLogger.d(
                    TAG,
                    "Floating view not attached yet, retry=${retryCount + 1}/$MAX_IME_FOCUS_RETRIES"
                )
                scheduleImeShow(
                    rootView = rootView,
                    imm = imm,
                    retryCount = retryCount + 1,
                    delayMillis = IME_FOCUS_RETRY_DELAY_MS
                )
                return@Runnable
            }

            rootView.requestFocus()

            val imeHost =
                rootView.findFocus()?.takeIf {
                    it.isAttachedToWindow && it.windowToken != null && it.onCheckIsTextEditor()
                }

            if (imeHost == null) {
                if (retryCount >= MAX_IME_FOCUS_RETRIES) {
                    pendingImeFocusRunnable = null
                    AppLogger.w(
                        TAG,
                        "Skip IME request: no focused host after $MAX_IME_FOCUS_RETRIES retries."
                    )
                    return@Runnable
                }

                AppLogger.d(
                    TAG,
                    "IME host not ready, retry=${retryCount + 1}/$MAX_IME_FOCUS_RETRIES"
                )
                scheduleImeShow(
                    rootView = rootView,
                    imm = imm,
                    retryCount = retryCount + 1,
                    delayMillis = IME_FOCUS_RETRY_DELAY_MS
                )
                return@Runnable
            }

            pendingImeFocusRunnable = null
            imm.showSoftInput(imeHost, InputMethodManager.SHOW_IMPLICIT)
        }

        pendingImeFocusRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingImeFocusRunnable = imeRunnable
        mainHandler.postDelayed(imeRunnable, delayMillis)
    }

    /**
     * 获取当前使用的ComposeView实例
     * @return View? 当前的ComposeView实例，如果未创建则返回null
     */
    fun getComposeView(): View? {
        return composeView
    }
}
