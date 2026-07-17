package com.ai.assistance.operit.services.floating

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.floating.FloatingMode

private const val MIRA_FLOATING_LAYOUT_VERSION = 3

class FloatingWindowState(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("floating_chat_prefs", Context.MODE_PRIVATE)
    private val screenWidthDp: Dp
    private val screenHeightDp: Dp

    // Window position
    var x: Int = 200
    var y: Int = 200

    // Window size
    val windowWidth = mutableStateOf(336.dp)
    val windowHeight = mutableStateOf(228.dp)
    val windowScale = mutableStateOf(1f)
    var lastWindowScale: Float = 1f

    // Mode state
    val currentMode = mutableStateOf(FloatingMode.BALL)
    var previousMode: FloatingMode = FloatingMode.BALL
    val ballSize = mutableStateOf(52.dp)
    val isAtEdge = mutableStateOf(false)

    // DragonBones pet mode lock state
    var isPetModeLocked = mutableStateOf(false)

    // Transition state
    var lastWindowPositionX: Int = 0
    var lastWindowPositionY: Int = 0
    var lastBallPositionX: Int = 0
    var lastBallPositionY: Int = 0
    var hasSavedBallPosition: Boolean = false
    var isTransitioning = false
    val transitionDebounceTime = 500L // 防抖时间
    
    // Ball explosion animation state
    val ballExploding = mutableStateOf(false)

    // Whether system-level cross-window blur is actually active for fullscreen
    val fullscreenSystemBlurActive = mutableStateOf(false)

    init {
        val displayMetrics = context.resources.displayMetrics
        screenWidthDp = (displayMetrics.widthPixels / displayMetrics.density).dp
        screenHeightDp = (displayMetrics.heightPixels / displayMetrics.density).dp
        restoreState()
    }

    fun saveState() {
        prefs.edit().apply {
            putInt("window_x", x)
            putInt("window_y", y)
            putFloat(
                "window_width",
                windowWidth.value.value.coerceIn(
                    minOf(200f, (screenWidthDp.value - 32f).coerceAtLeast(160f)),
                    (screenWidthDp.value - 32f).coerceAtLeast(160f),
                )
            )
            putFloat(
                "window_height",
                windowHeight.value.value.coerceIn(
                    minOf(200f, (screenHeightDp.value * 0.8f).coerceAtLeast(160f)),
                    (screenHeightDp.value * 0.8f).coerceAtLeast(160f),
                )
            )
            putString("current_mode", currentMode.value.name)
            putString("previous_mode", previousMode.name)
            putFloat("window_scale", windowScale.value.coerceIn(0.3f, 1.0f))
            putFloat("last_window_scale", lastWindowScale.coerceIn(0.3f, 1.0f))
            putInt("last_window_x", lastWindowPositionX)
            putInt("last_window_y", lastWindowPositionY)
            putInt("last_ball_x", lastBallPositionX)
            putInt("last_ball_y", lastBallPositionY)
            putBoolean("has_saved_ball_position", hasSavedBallPosition)
            putInt("mira_layout_version", MIRA_FLOATING_LAYOUT_VERSION)
            apply()
        }
    }

    fun restoreState() {
        val defaultX = 200
        val defaultY = 200
        x = prefs.getInt("window_x", defaultX)
        y = prefs.getInt("window_y", defaultY)

        val maxWindowWidth = (screenWidthDp.value - 32f).coerceAtLeast(160f)
        val minWindowWidth = minOf(280f, maxWindowWidth)
        val maxWindowHeight = (screenHeightDp.value * 0.8f).coerceAtLeast(160f)
        val minWindowHeight = minOf(200f, maxWindowHeight)
        val defaultWidth = minOf(360f, maxWindowWidth).coerceAtLeast(minWindowWidth)
        val defaultHeight = minOf(228f, maxWindowHeight).coerceAtLeast(minWindowHeight)
        val isLegacyLayout =
            prefs.getInt("mira_layout_version", 0) < MIRA_FLOATING_LAYOUT_VERSION
        val storedWidth = prefs.getFloat("window_width", defaultWidth)
        val storedHeight = prefs.getFloat("window_height", defaultHeight)
        windowWidth.value =
            (if (isLegacyLayout) defaultWidth else storedWidth)
                .coerceIn(minWindowWidth, maxWindowWidth)
                .dp
        windowHeight.value =
            (if (isLegacyLayout) defaultHeight else storedHeight)
                .coerceIn(minWindowHeight, maxWindowHeight)
                .dp

        val modeName = prefs.getString("current_mode", FloatingMode.BALL.name)
        currentMode.value = if (isLegacyLayout) {
            FloatingMode.BALL
        } else try {
            FloatingMode.valueOf(modeName ?: FloatingMode.BALL.name)
        } catch (_: Exception) {
            FloatingMode.BALL
        }

        val prevModeName = prefs.getString("previous_mode", FloatingMode.BALL.name)
        previousMode = if (isLegacyLayout) {
            FloatingMode.BALL
        } else try {
            FloatingMode.valueOf(prevModeName ?: FloatingMode.BALL.name)
        } catch (_: Exception) {
            FloatingMode.BALL
        }

        val storedScale = prefs.getFloat("window_scale", 1f)
        val storedLastScale = prefs.getFloat("last_window_scale", 1f)
        windowScale.value = if (isLegacyLayout) 1f else storedScale.coerceIn(0.3f, 1.0f)
        lastWindowScale = if (isLegacyLayout) 1f else storedLastScale.coerceIn(0.3f, 1.0f)

        lastWindowPositionX = prefs.getInt("last_window_x", 0)
        lastWindowPositionY = prefs.getInt("last_window_y", 0)
        val hasStoredBallCoordinates = prefs.contains("last_ball_x") && prefs.contains("last_ball_y")
        lastBallPositionX = prefs.getInt("last_ball_x", x)
        lastBallPositionY = prefs.getInt("last_ball_y", y)
        hasSavedBallPosition =
            prefs.getBoolean("has_saved_ball_position", hasStoredBallCoordinates) ||
                (!hasStoredBallCoordinates &&
                    (currentMode.value == FloatingMode.BALL ||
                        currentMode.value == FloatingMode.VOICE_BALL))

        if (isLegacyLayout) saveState()
    }
}
