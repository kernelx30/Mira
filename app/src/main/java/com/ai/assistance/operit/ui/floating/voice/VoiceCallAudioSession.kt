package com.ai.assistance.operit.ui.floating.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import com.ai.assistance.operit.util.AppLogger

class VoiceCallAudioSession(context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var previousMode = AudioManager.MODE_NORMAL
    private var focusRequest: AudioFocusRequest? = null
    private var focusHeld = false
    private var onFocusLost: ((Boolean) -> Unit)? = null
    private var onFocusGained: (() -> Unit)? = null

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> onFocusGained?.invoke()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
            -> onFocusLost?.invoke(true)
            AudioManager.AUDIOFOCUS_LOSS -> onFocusLost?.invoke(false)
        }
    }

    fun start(
        onFocusLost: (transient: Boolean) -> Unit,
        onFocusGained: () -> Unit,
    ): Boolean {
        this.onFocusLost = onFocusLost
        this.onFocusGained = onFocusGained
        if (focusHeld) return true

        previousMode = audioManager.mode
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        val result =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val request =
                    AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                        .setAcceptsDelayedFocusGain(false)
                        .setOnAudioFocusChangeListener(focusChangeListener)
                        .build()
                focusRequest = request
                audioManager.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    focusChangeListener,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
                )
            }
        focusHeld = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!focusHeld) {
            restoreAudioMode()
            AppLogger.w(TAG, "Voice call audio focus request was denied")
        }
        return focusHeld
    }

    fun stop() {
        if (focusHeld) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let(audioManager::abandonAudioFocusRequest)
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(focusChangeListener)
            }
        }
        focusHeld = false
        focusRequest = null
        onFocusLost = null
        onFocusGained = null
        restoreAudioMode()
    }

    private fun restoreAudioMode() {
        if (audioManager.mode == AudioManager.MODE_IN_COMMUNICATION) {
            audioManager.mode = previousMode
        }
    }

    private companion object {
        const val TAG = "VoiceCallAudioSession"
    }
}
