package com.ai.assistance.operit.ui.floating.voice

import android.content.Intent
import android.content.Context
import android.content.Context.INPUT_METHOD_SERVICE
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.R
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ai.assistance.operit.api.chat.AIForegroundService
import com.ai.assistance.operit.api.speech.SpeechPrerollStore
import com.ai.assistance.operit.api.speech.WakePhraseSnapshot
import com.ai.assistance.operit.api.speech.SpeechServiceFactory
import com.ai.assistance.operit.api.voice.VoiceServiceFactory
import com.ai.assistance.operit.api.voice.ExpressiveTtsDirector
import com.ai.assistance.operit.api.voice.VoiceCapabilities
import com.ai.assistance.operit.data.model.SpeechDelivery
import com.ai.assistance.operit.data.preferences.SpeechServicesPreferences
import com.ai.assistance.operit.util.TtsCleaner
import com.ai.assistance.operit.util.WaifuMessageProcessor
import com.ai.assistance.operit.util.LocaleUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "SpeechInteractionManager"

/**
 * 通用语音交互管理器
 * 封装了：语音识别、TTS、焦点获取、文本累积、静默检测等逻辑
 */
class SpeechInteractionManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val onSpeechResult: (String, Boolean) -> Unit, // (text, isFinal)
    private val onStateChange: (String) -> Unit, // 更新状态提示文本
    private val onSessionStateChange: (VoiceCallSessionState) -> Unit = {},
    private val onAudioFocusGained: () -> Unit = {},
) {
    // ===== 状态 =====
    var isRecording by mutableStateOf(false)
        private set
    var isProcessingSpeech by mutableStateOf(false)
        private set
    var hasFocus by mutableStateOf(false)
        private set

    // 文本累积
    var userMessage by mutableStateOf("")
        private set
    var currentSpokenText by mutableStateOf("")
        private set
    private var accumulatedText = ""
    private var latestPartialText = ""

    private var wakePhraseSnapshot: WakePhraseSnapshot? = null

    // 任务控制
    private var timeoutJob: Job? = null
    private var silenceTimeoutJob: Job? = null
    private var startListeningJob: Job? = null
    private var utteranceGeneration = 0L
    private var finalizedGeneration = -1L
    private var initialized = false

    // ===== 服务 =====
    val speechService = SpeechServiceFactory.getInstance(context)
    val voiceService
        get() = VoiceServiceFactory.getInstance(context)
    private val inputMethodManager = context.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
    private val audioSession = VoiceCallAudioSession(context.applicationContext)
    private val speechPreferences = SpeechServicesPreferences(context.applicationContext)

    // 暴露 Flow 给外部使用
    val volumeLevelFlow get() = speechService.volumeLevelFlow
    val recognitionResultFlow get() = speechService.recognitionResultFlow

    // ===== 初始化与清理 =====
    suspend fun initialize(): Boolean {
        resetState()
        onSessionStateChange(VoiceCallSessionState.Initializing)
        try {
            val speechReady = speechService.initialize()
            val voiceReady = voiceService.initialize()
            val focusReady =
                audioSession.start(
                    onFocusLost = { _ ->
                        stopListening(isCancel = true)
                        coroutineScope.launch { voiceService.stop() }
                        onSessionStateChange(VoiceCallSessionState.Suspended)
                    },
                    onFocusGained = {
                        onSessionStateChange(VoiceCallSessionState.Idle)
                        onAudioFocusGained()
                    },
                )
            initialized = speechReady && voiceReady && focusReady
            if (initialized) {
                onSessionStateChange(VoiceCallSessionState.Idle)
            } else {
                onSessionStateChange(
                    VoiceCallSessionState.Error(
                        context.getString(R.string.speech_error_init_failed),
                        recoverable = true,
                    )
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to initialize speech services", e)
            initialized = false
            onSessionStateChange(
                VoiceCallSessionState.Error(
                    e.message ?: context.getString(R.string.speech_error_init_failed),
                    recoverable = true,
                )
            )
        }
        return initialized
    }

    fun cleanup() {
        stopListening(isCancel = true)
        startListeningJob?.cancel()
        coroutineScope.launch {
            speechService.cancelRecognition()
            voiceService.stop()
        }
        timeoutJob?.cancel()
        silenceTimeoutJob?.cancel()
        audioSession.stop()
        currentSpokenText = ""
        initialized = false
        onSessionStateChange(VoiceCallSessionState.Ended)
    }

    private fun resetState() {
        isRecording = false
        isProcessingSpeech = false
        userMessage = ""
        accumulatedText = ""
        latestPartialText = ""
        currentSpokenText = ""
        wakePhraseSnapshot = null
        utteranceGeneration += 1L
        finalizedGeneration = -1L
        startListeningJob?.cancel()
        timeoutJob?.cancel()
        silenceTimeoutJob?.cancel()
    }

    // ===== 焦点管理 =====
    fun requestFocus(view: View?): Boolean {
        if (view == null) {
            hasFocus = false
            return false
        }
        view.requestFocus()
        inputMethodManager.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
        inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
        hasFocus = true
        return true
    }

    fun releaseFocus(view: View?) {
        view?.let {
            inputMethodManager.hideSoftInputFromWindow(it.windowToken, 0)
        }
        hasFocus = false // 逻辑上释放，虽然物理上可能还在
    }

    // ===== 语音识别流程 =====

    fun startListening(onStartFailure: ((String) -> Unit)? = null) {
        if (!initialized) {
            val message = context.getString(R.string.speech_error_init_failed)
            onSessionStateChange(VoiceCallSessionState.Error(message, recoverable = true))
            onStartFailure?.invoke(message)
            return
        }
        if (!hasFocus) {
            onStartFailure?.invoke(context.getString(R.string.floating_cannot_get_focus))
            return
        }

        // 重置超时
        timeoutJob?.cancel()
        
        // 重置文本状态
        isRecording = true
        userMessage = ""
        accumulatedText = ""
        latestPartialText = ""
        utteranceGeneration += 1L
        finalizedGeneration = -1L
        wakePhraseSnapshot = SpeechPrerollStore.consumePendingWakePhrase()
        onStateChange(context.getString(R.string.floating_listening))
        onSessionStateChange(VoiceCallSessionState.Listening)

        // 启动监听
        startListeningJob?.cancel()
        startListeningJob = coroutineScope.launch {
            try {
                try {
                    AIForegroundService.ensureMicrophoneForeground(context, forceStart = true)
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Failed to request microphone foreground", e)
                }

                try {
                    context.startService(Intent(context, AIForegroundService::class.java).apply {
                        action = AIForegroundService.ACTION_PREPARE_WAKE_HANDOFF
                    })
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Failed to request wake handoff prepare", e)
                }

                // Give AIForegroundService a moment to stop wake listening and release microphone
                delay(180)

                var ok = false
                var attempt = 0
                while (!ok && attempt < 12) {
                    if (attempt > 0) {
                        onSessionStateChange(VoiceCallSessionState.Reconnecting(attempt))
                        delay(160)
                    }
                    ok = speechService.startRecognition(
                        languageCode = resolveRecognitionLanguageCode(),
                        continuousMode = true,
                        partialResults = true
                    )
                    attempt++
                }

                if (!ok) {
                    isRecording = false
                    isProcessingSpeech = false
                    onStateChange(context.getString(R.string.floating_hold_microphone))
                    onSessionStateChange(
                        VoiceCallSessionState.Error(
                            context.getString(R.string.floating_start_recording_failed),
                            recoverable = true,
                        )
                    )
                    onStartFailure?.invoke(context.getString(R.string.floating_start_recording_failed))
                } else {
                    onSessionStateChange(VoiceCallSessionState.Listening)
                }
            } catch (e: Exception) {
                isRecording = false
                isProcessingSpeech = false
                onStateChange(context.getString(R.string.floating_hold_microphone))
                onSessionStateChange(
                    VoiceCallSessionState.Error(
                        e.message ?: context.getString(R.string.floating_start_recording_failed),
                        recoverable = true,
                    )
                )
                onStartFailure?.invoke(e.message ?: context.getString(R.string.floating_start_recording_failed))
            }
        }

        // 监听结果流 (假设这是 ViewModel 或者外部调用者通过 collectLatest 连接到 handleRecognitionResult)
        // 注意：SpeechService 的结果是通过 Flow 发出的，这里我们需要一个桥接方式，
        // 或者让 ViewModel 监听 Flow 并调用这里的 handleRecognitionResult。
        // 为了简化，这里我们假设 ViewModel 负责监听 SpeechService 的 Flow 并调用本类的 handleRecognitionResult
    }

    fun stopListening(isCancel: Boolean) {
        if (!isRecording) return
        
        isRecording = false
        silenceTimeoutJob?.cancel()

        coroutineScope.launch {
            if (isCancel) {
                speechService.cancelRecognition()
                isProcessingSpeech = false
                resetState()
                onStateChange(context.getString(R.string.floating_hold_microphone))
                onSessionStateChange(VoiceCallSessionState.Idle)
            } else {
                isProcessingSpeech = true
                onStateChange(context.getString(R.string.floating_recognizing))
                onSessionStateChange(VoiceCallSessionState.FinalizingSpeech)
                speechService.stopRecognition()
                startFallbackTimeout()
            }
        }
    }

    // 处理识别结果
    fun handleRecognitionResult(
        resultText: String,
        isFinal: Boolean,
        autoSendSilence: Boolean = false,
        silenceTimeoutMs: Long = DEFAULT_SILENCE_TIMEOUT_MS,
    ) {
        val effectiveText = stripWakePhrasePrefixIfNeeded(resultText)
        if (isRecording) {
            if (effectiveText.isNotBlank()) {
                // 处理增量
                if (latestPartialText.isNotEmpty() && !effectiveText.startsWith(latestPartialText)) {
                    accumulatedText += (if (accumulatedText.isNotEmpty()) "。" else "") + latestPartialText
                }
                latestPartialText = effectiveText

                // 静默检测
                if (autoSendSilence) {
                    silenceTimeoutJob?.cancel()
                    val generation = utteranceGeneration
                    val delayMs = dynamicSilenceDelayMs(effectiveText, isFinal, silenceTimeoutMs)
                    silenceTimeoutJob = coroutineScope.launch {
                        delay(delayMs)
                        if (generation != utteranceGeneration || finalizedGeneration == generation) {
                            return@launch
                        }
                        AppLogger.d(TAG, "Silence timeout, sending...")
                        finalizeSpeechInput()
                    }
                }
            }
            userMessage = accumulatedText + (if (accumulatedText.isNotEmpty() || latestPartialText.isNotEmpty()) latestPartialText else "")
            
        } else if (isProcessingSpeech && isFinal) {
            timeoutJob?.cancel()
            accumulatedText += (if (accumulatedText.isNotEmpty() && effectiveText.isNotBlank()) "。" else "") + effectiveText
            finalizeSpeechInput()
        }
    }

    private fun stripWakePhrasePrefixIfNeeded(text: String): String {
        val snapshot = wakePhraseSnapshot ?: return text
        if (text.isBlank()) return text

        val leadingRegex = Regex("^[\\s\\p{Punct}，。！？；：、“”‘’【】（）()\\[\\]{}<>《》]+")
        val leadingMatch = leadingRegex.find(text)
        val startIdx = leadingMatch?.value?.length ?: 0
        val trimmed = text.substring(startIdx)

        if (trimmed.isBlank()) return text

        if (snapshot.regexEnabled) {
            return try {
                val m = Regex(snapshot.phrase).find(trimmed)
                if (m != null && m.range.first == 0) {
                    val remainder = trimmed.substring(m.value.length)
                    remainder.replaceFirst(leadingRegex, "")
                } else {
                    text
                }
            } catch (_: Exception) {
                text
            }
        }

        val phrase = snapshot.phrase
        if (phrase.isBlank()) return text
        return if (trimmed.startsWith(phrase)) {
            val remainder = trimmed.substring(phrase.length)
            remainder.replaceFirst(leadingRegex, "")
        } else {
            text
        }
    }

    private fun startFallbackTimeout() {
        timeoutJob = coroutineScope.launch {
            delay(3000)
            if (isProcessingSpeech) {
                AppLogger.w(TAG, "Fallback timeout")
                finalizeSpeechInput()
            }
        }
    }

    private fun finalizeSpeechInput() {
        val generation = utteranceGeneration
        if (finalizedGeneration == generation) return
        finalizedGeneration = generation
        silenceTimeoutJob?.cancel()
        timeoutJob?.cancel()
        isRecording = false
        isProcessingSpeech = false
        val text = userMessage.ifBlank { accumulatedText }
        onSessionStateChange(VoiceCallSessionState.FinalizingSpeech)
        coroutineScope.launch {
            runCatching { speechService.cancelRecognition() }
            if (text.isNotBlank()) {
                onSpeechResult(text, true)
                onStateChange(context.getString(R.string.floating_thinking_2))
            } else {
                onStateChange(context.getString(R.string.floating_didnt_hear_clearly))
                onSessionStateChange(VoiceCallSessionState.Idle)
            }
            userMessage = ""
            accumulatedText = ""
            latestPartialText = ""
        }
    }
    
    // ===== TTS 辅助 =====
    
    fun speak(text: String, interrupt: Boolean = true) {
        if (text.isBlank()) return
        coroutineScope.launch {
            speakExpressively(text, interrupt)
        }
    }

    suspend fun speakExpressively(text: String, interrupt: Boolean = true): Boolean {
        if (text.isBlank()) return true
        return try {
            val baseRate = speechPreferences.ttsSpeechRateFlow.first()
            val basePitch = speechPreferences.ttsPitchFlow.first()
            val expressiveEnabled = speechPreferences.expressiveTtsEnabledFlow.first()
            val expressionStrength = speechPreferences.expressiveTtsStrengthFlow.first()
            val requests =
                ExpressiveTtsDirector.plan(
                    content = text,
                    capabilities = if (expressiveEnabled) voiceService.capabilities else VoiceCapabilities.PLAIN,
                    baseRate = baseRate,
                    basePitch = basePitch,
                    delivery = SpeechDelivery.CALL,
                    expressionScale = expressionStrength.scale,
                )

            if (requests.isEmpty()) return true
            onSessionStateChange(VoiceCallSessionState.Speaking)
            var firstSegment = true
            for (request in requests) {
                currentSpokenText = request.text
                val accepted =
                    voiceService.speak(
                        text = request.text,
                        interrupt = if (firstSegment) interrupt else false,
                        rate = request.rate,
                        pitch = request.pitch,
                        extraParams = request.extraParams,
                    )
                if (!accepted) {
                    currentSpokenText = ""
                    return false
                }
                val playbackCompleted = withTimeoutOrNull(MAX_TTS_SEGMENT_DURATION_MS) {
                    while (voiceService.isSpeaking) delay(50L)
                    true
                } == true
                if (!playbackCompleted) {
                    voiceService.stop()
                    currentSpokenText = ""
                    return false
                }
                firstSegment = false
            }
            currentSpokenText = ""
            onSessionStateChange(VoiceCallSessionState.Idle)
            true
        } catch (e: Exception) {
            currentSpokenText = ""
            AppLogger.e(TAG, "TTS Error", e)
            onSessionStateChange(
                VoiceCallSessionState.Error(
                    e.message ?: context.getString(R.string.chat_speak_failed),
                    recoverable = true,
                )
            )
            false
        }
    }
    
    fun cleanTextForTts(text: String, regexs: List<String>): String {
        return WaifuMessageProcessor.cleanContentForWaifu(TtsCleaner.clean(text, regexs))
    }

    private fun resolveRecognitionLanguageCode(): String {
        val language = LocaleUtils.getCurrentLanguage(context)
        return when (language.substringBefore('-').lowercase()) {
            "zh" -> "zh-CN"
            "en" -> "en-US"
            "ko" -> "ko-KR"
            "es" -> "es-ES"
            "ms" -> "ms-MY"
            "id" -> "id-ID"
            "pt" -> "pt-BR"
            else -> LocaleUtils.getLocaleForLanguageCode(language, context).toLanguageTag()
        }
    }

    private fun dynamicSilenceDelayMs(
        text: String,
        isFinal: Boolean,
        configuredMs: Long,
    ): Long {
        val base = configuredMs.coerceIn(700L, 4_000L)
        val normalized = text.trim()
        return when {
            isFinal && normalized.lastOrNull() in TERMINAL_PUNCTUATION -> (base * 0.58f).toLong()
            normalized.length <= 3 -> (base * 1.35f).toLong()
            normalized.lastOrNull() in CONTINUATION_PUNCTUATION -> (base * 1.28f).toLong()
            normalized.length >= 40 -> (base * 1.15f).toLong()
            else -> base
        }.coerceIn(650L, 5_000L)
    }

    private companion object {
        const val DEFAULT_SILENCE_TIMEOUT_MS = 1_800L
        const val MAX_TTS_SEGMENT_DURATION_MS = 120_000L
        val TERMINAL_PUNCTUATION = setOf('。', '！', '？', '.', '!', '?')
        val CONTINUATION_PUNCTUATION = setOf('，', '、', ',', '；', ';', '：', ':')
    }
}
