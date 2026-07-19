package com.ai.assistance.operit.ui.floating.ui.fullscreen.viewmodel

import android.content.Context
import androidx.compose.runtime.*
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.avatar.common.state.AvatarEmotion
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.InputProcessingState
import com.ai.assistance.operit.data.model.PromptFunctionType
import com.ai.assistance.operit.data.preferences.WakeWordPreferences
import com.ai.assistance.operit.ui.floating.FloatContext
import com.ai.assistance.operit.ui.floating.ui.fullscreen.XmlTextProcessor
import com.ai.assistance.operit.ui.floating.ui.pet.AvatarEmotionManager
import com.ai.assistance.operit.ui.floating.voice.SpeechInteractionManager
import com.ai.assistance.operit.ui.floating.voice.VoiceCallSessionState
import com.ai.assistance.operit.ui.floating.voice.VoiceCallTextPolicy
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.TtsSegmenter
import com.ai.assistance.operit.util.stream.Stream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "FloatingFullscreenViewModel"
private const val FULLSCREEN_TTS_CAPTURE_SUPPRESS_MS = 1200L
private const val VOICE_SEND_ACCEPT_TIMEOUT_MS = 15_000L
private const val VOICE_TURN_START_TIMEOUT_MS = 8_000L
private const val VOICE_TURN_COMPLETE_TIMEOUT_MS = 180_000L
private const val MAX_RECOGNITION_RECOVERY_ATTEMPTS = 3

internal fun shouldSpeakFullscreenResponse(
    voiceCallActive: Boolean,
    locallyMuted: Boolean,
    effectiveAutoReadEnabled: Boolean,
): Boolean =
    voiceCallActive || (!locallyMuted && effectiveAutoReadEnabled)

data class VoiceAvatarMotionRequest(
    val emotion: AvatarEmotion = AvatarEmotion.IDLE,
    val triggerName: String? = null,
    val playOnce: Boolean = false,
    val sequence: Long = 0L
)

class FloatingFullscreenModeViewModel(
    private val context: Context,
    private val floatContext: FloatContext,
    private val coroutineScope: CoroutineScope,
    initialWaveActive: Boolean
) {
    // ===== 状态定义 =====
    var aiMessage by mutableStateOf(context.getString(R.string.floating_hold_microphone_to_speak))
    
    // UI状态
    var isWaveActive by mutableStateOf(initialWaveActive)
    var showBottomControls by mutableStateOf(true)
    var isEditMode by mutableStateOf(false)
    var editableText by mutableStateOf("")
    var inputText by mutableStateOf("")
    var showDragHints by mutableStateOf(false)

    var attachScreenContent by mutableStateOf(false)
    var attachNotifications by mutableStateOf(false)
    var attachLocation by mutableStateOf(false)
    var hasOcrSelection by mutableStateOf(false)
    var isStreamingTtsMuted by mutableStateOf(false)
    var voiceAvatarMotionRequest by mutableStateOf(VoiceAvatarMotionRequest())
        private set
    var sessionState by mutableStateOf<VoiceCallSessionState>(VoiceCallSessionState.Idle)
        private set
    
    val isInitialLoad = mutableStateOf(true)

     private var aiStreamJob: Job? = null
     private var activeAiStreamIdentity: Int? = null
     private var activeAiMessageTimestamp: Long? = null
     private var ttsSpeakJob: Job? = null
     private var lastSpokenStaticMessageKey: String? = null

    private val wakePrefs by lazy { WakeWordPreferences(context.applicationContext) }
    private var inactivityTimeoutSeconds: Int = WakeWordPreferences.DEFAULT_VOICE_CALL_INACTIVITY_TIMEOUT_SECONDS
    private var bargeInEnabled: Boolean = WakeWordPreferences.DEFAULT_VOICE_CALL_BARGE_IN_ENABLED
    private var silenceTimeoutMs: Long = WakeWordPreferences.DEFAULT_VOICE_CALL_SILENCE_TIMEOUT_MS.toLong()
    private var prefsJob: Job? = null
    private var inactivityJob: Job? = null
    private var lastVoiceActivityAtMs: Long = 0L
    private var recognitionRecoveryAttempts: Int = 0
    private var recognitionRecoveryJob: Job? = null

    private var wakeEnterJob: Job? = null
    private var resumeVoiceCaptureJob: Job? = null
    private var shouldResumeVoiceCaptureAfterAiTurn: Boolean = false
    private var suppressRecognitionUntilMs: Long = 0L
    private var waveModeAutoTimeoutEnabled: Boolean = false
    var isVoiceCapturePausedForAi by mutableStateOf(false)
    private var voiceAvatarSequence: Long = 0L
    private var lastHandledVoiceAvatarMessageKey: String? = null
    private var hasInitializedVoiceAvatarFromSnapshot: Boolean = false
    
    // ===== 语音交互管理器 =====
    val speechManager = SpeechInteractionManager(
        context = context,
        coroutineScope = coroutineScope,
        onSpeechResult = { text, _ ->
            dispatchVoiceMessage(text)
        },
        onStateChange = { msg -> aiMessage = msg },
        onSessionStateChange = { state ->
            sessionState = state
            if (
                state is VoiceCallSessionState.Speaking &&
                    bargeInEnabled &&
                    isWaveActive &&
                    !isRecording
            ) {
                startVoiceCapture(cancelAiIfWorking = false)
            }
        },
        onAudioFocusGained = {
            if (isWaveActive && !isVoiceCapturePausedForAi && !isRecording) {
                startVoiceCapture()
            }
        },
    )
    
    // 代理属性，方便 UI 访问
    val isRecording: Boolean get() = speechManager.isRecording
    val isProcessingSpeech: Boolean get() = speechManager.isProcessingSpeech
    val userMessage: String get() = speechManager.userMessage
    val hasFocus: Boolean get() = speechManager.hasFocus
    // UI 用到 volumeFlow 和 recognitionResultFlow
    val speechService get() = speechManager.speechService
    val volumeLevelFlow get() = speechManager.volumeLevelFlow
    val recognitionResultFlow get() = speechManager.recognitionResultFlow
    val recognitionErrorFlow get() = speechManager.recognitionErrorFlow

    // ===== 业务逻辑 =====

    fun toggleStreamingTtsMuted() {
        isStreamingTtsMuted = !isStreamingTtsMuted
        if (isStreamingTtsMuted) {
            stopCurrentTtsPlayback()
        }
    }

    private fun stopCurrentTtsPlayback() {
        ttsSpeakJob?.cancel()
        ttsSpeakJob = null
        coroutineScope.launch { speechManager.voiceService.stop() }
    }

    private fun isAiBusyOrSpeaking(): Boolean {
        return isAiBusy() || speechManager.voiceService.isSpeaking
    }

    private fun shouldInterceptCenterAvatarClick(): Boolean {
        return isVoiceCapturePausedForAi || isAiBusyOrSpeaking()
    }

    private fun prepareVoiceCaptureForAiTurn() {
        if (!isWaveActive) return
        shouldResumeVoiceCaptureAfterAiTurn = true
        isVoiceCapturePausedForAi = true
        resumeVoiceCaptureJob?.cancel()
        if (speechManager.isRecording || speechManager.isProcessingSpeech) {
            stopVoiceCapture(isCancel = true, notifyIdle = false)
        }
    }

    private fun dispatchVoiceMessage(
        rawText: String,
        beforeSend: suspend () -> Unit = { maybeAutoAttachByKeyword(rawText.trim()) },
        allowEmpty: Boolean = false,
    ) {
        val text = rawText.trim()
        if (text.isEmpty() && !allowEmpty && floatContext.attachments.isEmpty()) return

        val turnBaselineTimestamp = floatContext.messages.lastOrNull()?.timestamp ?: Long.MIN_VALUE
        aiMessage = context.getString(R.string.floating_thinking)
        sessionState = VoiceCallSessionState.Sending
        startVoiceAvatarThinking()
        prepareVoiceCaptureForAiTurn()

        coroutineScope.launch {
            runCatching { beforeSend() }
                .onFailure { AppLogger.w(TAG, "Voice attachment preparation failed", it) }

            val accepted = awaitVoiceMessageAccepted(text)
            if (!accepted) {
                recoverVoiceCallAfterFailure(context.getString(R.string.mira_voice_send_failed))
                return@launch
            }

            sessionState = VoiceCallSessionState.Thinking
            awaitAiTurnAndResumeVoiceCapture(turnBaselineTimestamp)
        }
    }

    private suspend fun awaitVoiceMessageAccepted(text: String): Boolean {
        val resolved = CompletableDeferred<Boolean>()
        val callback = floatContext.onSendMessageWithResult
        if (callback == null) {
            val legacyCallback = floatContext.onSendMessage ?: return false
            legacyCallback(text, PromptFunctionType.VOICE)
            return true
        }

        val reserved = callback(text, PromptFunctionType.VOICE) { accepted ->
            if (!resolved.isCompleted) resolved.complete(accepted)
        }
        if (!reserved) return false
        return withTimeoutOrNull(VOICE_SEND_ACCEPT_TIMEOUT_MS) { resolved.await() } == true
    }

    private fun awaitAiTurnAndResumeVoiceCapture(turnBaselineTimestamp: Long) {
        if (!isWaveActive || !shouldResumeVoiceCaptureAfterAiTurn) return
        resumeVoiceCaptureJob?.cancel()
        resumeVoiceCaptureJob = coroutineScope.launch {
            val turnStarted = withTimeoutOrNull(VOICE_TURN_START_TIMEOUT_MS) {
                while (isActive && isWaveActive && shouldResumeVoiceCaptureAfterAiTurn) {
                    val latestAiTimestamp =
                        floatContext.messages.lastOrNull { it.sender == "ai" }?.timestamp
                            ?: Long.MIN_VALUE
                    if (isAiBusyOrSpeaking() || latestAiTimestamp > turnBaselineTimestamp) {
                        return@withTimeoutOrNull true
                    }
                    delay(100L)
                }
                false
            } == true

            if (!turnStarted) {
                recoverVoiceCallAfterFailure(context.getString(R.string.mira_voice_response_start_timeout))
                return@launch
            }

            val completed = withTimeoutOrNull(VOICE_TURN_COMPLETE_TIMEOUT_MS) {
                do {
                    delay(120L)
                } while (
                    isActive &&
                        isWaveActive &&
                        shouldResumeVoiceCaptureAfterAiTurn &&
                        (isAiBusyOrSpeaking() || ttsSpeakJob?.isActive == true)
                )
                isActive && isWaveActive && shouldResumeVoiceCaptureAfterAiTurn
            } == true

            if (!completed) {
                floatContext.onCancelMessage?.invoke()
                stopCurrentTtsPlayback()
                recoverVoiceCallAfterFailure(context.getString(R.string.mira_voice_response_timeout))
                return@launch
            }

            resumeVoiceCaptureAfterTurn()
        }
    }

    private fun recoverVoiceCallAfterFailure(message: String) {
        sessionState = VoiceCallSessionState.Error(message, recoverable = true)
        aiMessage = message
        resumeVoiceCaptureAfterTurn()
    }

    private fun resumeVoiceCaptureAfterTurn() {
        shouldResumeVoiceCaptureAfterAiTurn = false
        isVoiceCapturePausedForAi = false
        lastVoiceActivityAtMs = System.currentTimeMillis()
        if (isWaveActive && !speechManager.isRecording && !speechManager.isProcessingSpeech) {
            startVoiceCapture()
        } else if (!isWaveActive) {
            sessionState = VoiceCallSessionState.Idle
        }
    }

    private fun cancelPendingVoiceCaptureResume() {
        shouldResumeVoiceCaptureAfterAiTurn = false
        isVoiceCapturePausedForAi = false
        resumeVoiceCaptureJob?.cancel()
        resumeVoiceCaptureJob = null
    }

    fun processAndSpeakAiMessage(
        lastMessage: ChatMessage?,
        ttsCleanerRegexs: List<String>,
        effectiveAutoReadEnabled: Boolean,
    ) {
        val message = lastMessage ?: return

         // If we are switching to a new message, stop any previous stream collector.
         // This avoids duplicate collectors (and duplicated replay) when the upstream SharedStream replays history.
         val messageChanged = activeAiMessageTimestamp != message.timestamp
         if (activeAiMessageTimestamp != null && messageChanged) {
             aiStreamJob?.cancel()
             aiStreamJob = null
             activeAiStreamIdentity = null
         }
         activeAiMessageTimestamp = message.timestamp
        
        if (isInitialLoad.value) {
            isInitialLoad.value = false
            if (message.sender == "ai") aiMessage = stripVoiceAvatarTags(message.content)
            return
        }
        
        if (messageChanged) {
            stopCurrentTtsPlayback()
        }
        
        when (message.sender) {
            "think" -> {
                aiStreamJob?.cancel()
                aiStreamJob = null
                activeAiStreamIdentity = null
                aiMessage = context.getString(R.string.floating_thinking)
                sessionState = VoiceCallSessionState.Thinking
            }
            "ai" -> {
                val stream = message.contentStream
                if (stream != null) {
                    val streamIdentity = System.identityHashCode(stream)
                    if (aiStreamJob?.isActive == true && activeAiStreamIdentity == streamIdentity) {
                        return
                    }
                    aiStreamJob?.cancel()
                    aiStreamJob = null
                    activeAiStreamIdentity = streamIdentity

                    // 不要立即清空，等待流内容到达
                    aiStreamJob = coroutineScope.launch {
                        handleStreamResponse(
                            stream = stream,
                            cleaners = ttsCleanerRegexs,
                            effectiveAutoReadEnabled = effectiveAutoReadEnabled,
                        )
                    }
                } else {
                    aiStreamJob?.cancel()
                    aiStreamJob = null
                    activeAiStreamIdentity = null
                    val messageKey = buildVoiceAvatarMessageKey(message)
                    if (lastSpokenStaticMessageKey != messageKey) {
                        lastSpokenStaticMessageKey = messageKey
                        handleStaticResponse(
                            content = message.content,
                            cleaners = ttsCleanerRegexs,
                            effectiveAutoReadEnabled = effectiveAutoReadEnabled,
                        )
                    }
                }
            }
        }
    }

    private suspend fun handleStreamResponse(
        stream: Stream<String>,
        cleaners: List<String>,
        effectiveAutoReadEnabled: Boolean,
    ) {
        val sb = StringBuilder()
        var isFirstSentence = true
        var isFirstChar = true
        XmlTextProcessor.processStreamToText(stream).collect { char ->
            if (isFirstChar) {
                aiMessage = "" // 收到第一个字符时才清空等待提示
                isFirstChar = false
            }
            aiMessage += char
            sb.append(char)
            
            val cutIdx = TtsSegmenter.nextSegmentEnd(sb)
            if (cutIdx >= 0) {
                val segment = sb.substring(0, cutIdx)
                if (
                    trySpeak(
                        text = segment,
                        interrupt = isFirstSentence,
                        cleaners = cleaners,
                        effectiveAutoReadEnabled = effectiveAutoReadEnabled,
                        armMicSuppression = isFirstSentence,
                    )
                ) {
                    isFirstSentence = false
                    sb.delete(0, cutIdx)
                }
            }
        }
        trySpeak(
            text = sb.toString(),
            interrupt = isFirstSentence,
            cleaners = cleaners,
            effectiveAutoReadEnabled = effectiveAutoReadEnabled,
            armMicSuppression = isFirstSentence,
        )
    }

    private fun handleStaticResponse(
        content: String,
        cleaners: List<String>,
        effectiveAutoReadEnabled: Boolean,
    ) {
        val plainContent = stripVoiceAvatarTags(content)
        aiMessage = plainContent
        trySpeak(
            text = plainContent,
            interrupt = true,
            cleaners = cleaners,
            effectiveAutoReadEnabled = effectiveAutoReadEnabled,
            armMicSuppression = true,
        )
    }

    private fun trySpeak(
        text: String,
        interrupt: Boolean,
        cleaners: List<String>,
        effectiveAutoReadEnabled: Boolean,
        armMicSuppression: Boolean = false
    ): Boolean {
        val cleanText = speechManager.cleanTextForTts(text.trim(), cleaners)
        if (cleanText.isNotEmpty()) {
            if (
                !shouldSpeakFullscreenResponse(
                    voiceCallActive = isWaveActive,
                    locallyMuted = isStreamingTtsMuted,
                    effectiveAutoReadEnabled = effectiveAutoReadEnabled,
                )
            ) {
                return true
            }
            if (armMicSuppression && isWaveActive) {
                suppressRecognitionUntilMs = System.currentTimeMillis() + FULLSCREEN_TTS_CAPTURE_SUPPRESS_MS
            }
            enqueueSpeak(cleanText, interrupt)
            return true
        }
        return false
    }

    private fun enqueueSpeak(text: String, interrupt: Boolean) {
        val previousJob = if (interrupt) {
            ttsSpeakJob?.cancel()
            null
        } else {
            ttsSpeakJob
        }

        ttsSpeakJob =
            coroutineScope.launch {
                try {
                    previousJob?.join()
                    speechManager.speakExpressively(text, interrupt)
                } catch (_: kotlinx.coroutines.CancellationException) {
                } catch (e: Exception) {
                    AppLogger.e(TAG, "TTS playback failed", e)
                }
            }
    }

    // ===== 语音交互 =====

    fun startVoiceCapture(cancelAiIfWorking: Boolean = true) {
        // 如果AI正在生成，尝试取消
        val lastMessage = floatContext.messages.lastOrNull()
        val isAiWorking = lastMessage?.sender == "think" || 
                          (lastMessage?.sender == "ai" && lastMessage.contentStream != null)
        
        if (isAiWorking && cancelAiIfWorking) {
            floatContext.onCancelMessage?.invoke()
        }
        
        speechManager.startListening { errorMsg ->
            aiMessage = errorMsg
        }
    }

    fun stopVoiceCapture(isCancel: Boolean, notifyIdle: Boolean = true) {
        speechManager.stopListening(isCancel, notifyIdle)
    }

    fun enterWaveMode(
        wakeLaunched: Boolean = false,
        enableAutoTimeout: Boolean = false
    ) {
        wakeEnterJob?.cancel()
        wakeEnterJob = coroutineScope.launch {
            // 语音态 UI 先切换出来（唤醒场景更符合预期）
            isWaveActive = true
            waveModeAutoTimeoutEnabled = enableAutoTimeout
            inactivityJob?.cancel()
            inactivityJob = null

            playWakeGreetingIfNeeded(wakeLaunched)

            startVoiceCapture()
            if (speechManager.isRecording && waveModeAutoTimeoutEnabled) {
                lastVoiceActivityAtMs = System.currentTimeMillis()
                startInactivityMonitor()
            } else {
                if (!speechManager.isRecording) {
                    isWaveActive = false
                    showBottomControls = true
                }
            }
        }
    }
    
    fun exitWaveMode() {
        wakeEnterJob?.cancel()
        wakeEnterJob = null
        cancelPendingVoiceCaptureResume()
        suppressRecognitionUntilMs = 0L
        waveModeAutoTimeoutEnabled = false
        stopVoiceCapture(true)
        coroutineScope.launch { speechManager.voiceService.stop() }
        isWaveActive = false
        showBottomControls = true
        inactivityJob?.cancel()
        inactivityJob = null
        recognitionRecoveryJob?.cancel()
        recognitionRecoveryJob = null
        resetVoiceAvatarToIdle()
    }

    private suspend fun playWakeGreetingIfNeeded(wakeLaunched: Boolean) {
        if (!wakeLaunched) return

        val enabled = wakePrefs.wakeGreetingEnabledFlow.first()
        if (!enabled) return

        val text =
            wakePrefs.wakeGreetingTextFlow.first().trim().ifBlank {
                WakeWordPreferences.DEFAULT_WAKE_GREETING_TEXT
            }
        if (text.isBlank()) return

        // 唤醒问候语与录音并行执行（全双工）
        if (isWaveActive) {
            suppressRecognitionUntilMs = System.currentTimeMillis() + FULLSCREEN_TTS_CAPTURE_SUPPRESS_MS
        }
        speechManager.speak(text, interrupt = true)
    }

    fun onCenterAvatarClick() {
        if (isWaveActive && shouldInterceptCenterAvatarClick()) {
            val shouldCancelAiTurn = shouldResumeVoiceCaptureAfterAiTurn || isAiBusy()
            cancelPendingVoiceCaptureResume()
            if (shouldCancelAiTurn) {
                floatContext.onCancelMessage?.invoke()
            }
            coroutineScope.launch {
                speechManager.voiceService.stop()
                if (!speechManager.isRecording && !speechManager.isProcessingSpeech) {
                    startVoiceCapture()
                }
            }
            return
        }

        if (isWaveActive) {
            exitWaveMode()
        } else {
            enterWaveMode(enableAutoTimeout = false)
        }
    }

    fun handleRecognitionResult(resultText: String, isFinal: Boolean) {
        val speaking = speechManager.voiceService.isSpeaking
        if (
            isWaveActive &&
                System.currentTimeMillis() < suppressRecognitionUntilMs &&
                (!bargeInEnabled || !speaking || isLikelyPlaybackEcho(resultText))
        ) {
            return
        }
        if (isWaveActive && resultText.isNotBlank()) {
            lastVoiceActivityAtMs = System.currentTimeMillis()
            recognitionRecoveryAttempts = 0
            recognitionRecoveryJob?.cancel()
            recognitionRecoveryJob = null
        }
        // 委托给 Manager 处理，波浪模式下启用自动静默发送
        if (isWaveActive && speechManager.voiceService.isSpeaking) {
            if (isLikelyPlaybackEcho(resultText)) return
            if (resultText.isNotBlank()) {
                sessionState = VoiceCallSessionState.Interrupted
                stopCurrentTtsPlayback()
                floatContext.onCancelMessage?.invoke()
                suppressRecognitionUntilMs = 0L
            }
        }
        speechManager.handleRecognitionResult(
            resultText = resultText,
            isFinal = isFinal,
            autoSendSilence = isWaveActive,
            silenceTimeoutMs = silenceTimeoutMs,
        )
    }

    private fun isLikelyPlaybackEcho(recognizedText: String): Boolean {
        return VoiceCallTextPolicy.isLikelyPlaybackEcho(
            recognizedText = recognizedText,
            spokenText = speechManager.currentSpokenText,
        )
    }

    fun handleRecognitionError(code: Int, message: String) {
        if (code == 0 || message.isBlank()) return
        recognitionRecoveryAttempts += 1
        val canRetry = isWaveActive && recognitionRecoveryAttempts <= MAX_RECOGNITION_RECOVERY_ATTEMPTS
        sessionState = VoiceCallSessionState.Error(message, recoverable = canRetry)
        aiMessage = message
        stopVoiceCapture(isCancel = true, notifyIdle = false)
        recognitionRecoveryJob?.cancel()
        if (canRetry) {
            recognitionRecoveryJob = coroutineScope.launch {
                delay(350L * recognitionRecoveryAttempts)
                if (isWaveActive && !isVoiceCapturePausedForAi) {
                    startVoiceCapture(cancelAiIfWorking = false)
                }
            }
        }
    }

    // ===== 初始化与清理 =====

     suspend fun initialize(autoEnterVoiceChat: Boolean = false, wakeLaunched: Boolean = false) {
         val speechReady = speechManager.initialize()
         cancelPendingVoiceCaptureResume()
         prefsJob?.cancel()
         prefsJob = coroutineScope.launch {
             combine(
                 wakePrefs.voiceCallInactivityTimeoutSecondsFlow,
                 wakePrefs.voiceCallBargeInEnabledFlow,
                 wakePrefs.voiceCallSilenceTimeoutMsFlow,
             ) { inactivitySeconds, bargeIn, silenceMs ->
                 Triple(inactivitySeconds, bargeIn, silenceMs)
             }.collectLatest { (inactivitySeconds, bargeIn, silenceMs) ->
                 inactivityTimeoutSeconds = inactivitySeconds.coerceIn(1, 600)
                 bargeInEnabled = bargeIn
                 silenceTimeoutMs = silenceMs.toLong().coerceIn(700L, 4_000L)
             }
         }
         isInitialLoad.value = true
         isWaveActive = autoEnterVoiceChat && speechReady
         showBottomControls = true
         hasInitializedVoiceAvatarFromSnapshot = false
         lastHandledVoiceAvatarMessageKey = null
         resetVoiceAvatarToIdle()
         exitEditMode()

        // 获取焦点
        val view = floatContext.chatService?.getComposeView()
         if (!speechReady) {
             aiMessage = context.getString(R.string.speech_error_init_failed)
         } else if (!speechManager.requestFocus(view)) {
             aiMessage = context.getString(R.string.floating_cannot_get_input_service)
         } else {
             aiMessage = context.getString(R.string.floating_hold_microphone_to_speak)
         }

         if (autoEnterVoiceChat && speechReady) {
             enterWaveMode(wakeLaunched = wakeLaunched, enableAutoTimeout = true)
         }
     }

     fun cleanup() {
        val view = floatContext.chatService?.getComposeView()
        speechManager.releaseFocus(view)
        speechManager.cleanup()
        ttsSpeakJob?.cancel()
        ttsSpeakJob = null
        cancelPendingVoiceCaptureResume()

        prefsJob?.cancel()
        prefsJob = null
        inactivityJob?.cancel()
        inactivityJob = null
        recognitionRecoveryJob?.cancel()
        recognitionRecoveryJob = null

         aiStreamJob?.cancel()
         aiStreamJob = null
        activeAiStreamIdentity = null
        lastSpokenStaticMessageKey = null

        wakeEnterJob?.cancel()
        wakeEnterJob = null
        hasInitializedVoiceAvatarFromSnapshot = false
        lastHandledVoiceAvatarMessageKey = null
        resetVoiceAvatarToIdle()
    }

    private fun startInactivityMonitor() {
        inactivityJob?.cancel()
        inactivityJob = coroutineScope.launch {
            while (isActive && isWaveActive) {
                val timeoutMs = inactivityTimeoutSeconds.toLong() * 1000L
                val elapsed = System.currentTimeMillis() - lastVoiceActivityAtMs
                val remaining = timeoutMs - elapsed
                if (remaining <= 0L) {
                    // 如果 AI 正在朗读，不要在朗读过程中退出/关闭。
                    // 等朗读结束后再重新评估 remaining。
                    val voiceService = speechManager.voiceService
                    if (voiceService.isSpeaking) {
                        withTimeoutOrNull(20_000L) {
                            voiceService.speakingStateFlow.filter { speaking -> !speaking }.first()
                        }
                        // 朗读结束后重置计时，给用户一个完整的超时窗口
                        lastVoiceActivityAtMs = System.currentTimeMillis()
                        continue
                    }

                    // AI 在工具调用/处理/生成过程中，也不要自动关闭。
                    // 否则会出现“工具调用耗时较长 -> 超时 -> 窗口自动关闭”。
                    if (isAiBusy()) {
                        while (isActive && isWaveActive && isAiBusy()) {
                            delay(250L)
                        }
                        // AI 忙完后重置计时，避免立刻触发关闭
                        lastVoiceActivityAtMs = System.currentTimeMillis()
                        continue
                    }

                    exitWaveMode()
                    if (floatContext.chatService?.isWakeLaunched() == true) {
                        floatContext.onClose()
                    }
                    return@launch
                }
                delay(minOf(remaining, 500L))
            }
        }
    }

    private fun isAiBusy(): Boolean {
        val state = floatContext.inputProcessingState.value
        val stateBusy =
            state !is InputProcessingState.Idle &&
                state !is InputProcessingState.Completed &&
                state !is InputProcessingState.Error

        val lastMessage = floatContext.messages.lastOrNull()
        val streamBusy =
            lastMessage?.sender == "think" ||
                (lastMessage?.sender == "ai" && lastMessage.contentStream != null)

        return stateBusy || streamBusy
    }

    // ===== 编辑模式 =====

    fun enterEditMode(text: String) {
        coroutineScope.launch { speechManager.stopListening(isCancel = true) }
        editableText = text
        isEditMode = true
        aiMessage = context.getString(R.string.floating_edit_your_message)
    }
    
    fun exitEditMode() {
        isEditMode = false
        editableText = ""
        aiMessage = context.getString(R.string.floating_hold_microphone_to_speak)
    }
    
    fun sendEditedMessage() {
        if (editableText.isNotBlank()) {
            dispatchVoiceMessage(editableText)
            isEditMode = false
            editableText = ""
        }
    }
    
    fun sendInputMessage() {
        val text = inputText.trim()
        if (text.isEmpty() && !attachScreenContent && !attachNotifications && !attachLocation && !hasOcrSelection) return

        // 立即清理UI状态，不等待协程
        val shouldCaptureScreen = attachScreenContent
        val shouldCaptureNotifications = attachNotifications
        val shouldCaptureLocation = attachLocation
        
        inputText = ""
        attachScreenContent = false
        attachNotifications = false
        attachLocation = false
        hasOcrSelection = false
        aiMessage = context.getString(R.string.floating_thinking)

        dispatchVoiceMessage(
            rawText = text,
            allowEmpty = true,
            beforeSend = {
                maybeAutoAttachByKeyword(text)
                val attachmentDelegate = floatContext.chatService?.getChatCore()?.getAttachmentDelegate()
                if (shouldCaptureScreen) {
                    attachmentDelegate?.captureScreenContent()
                }
                if (shouldCaptureNotifications) {
                    attachmentDelegate?.captureNotifications()
                }
                if (shouldCaptureLocation) {
                    attachmentDelegate?.captureLocation()
                }
            },
        )
    }

    private suspend fun maybeAutoAttachByKeyword(text: String) {
        if (text.isBlank()) return

        wakePrefs.migrateVoiceAutoAttachItemsIfNeeded()

        val enabled = wakePrefs.voiceAutoAttachEnabledFlow.first()
        if (!enabled) return

        val attachmentDelegate = floatContext.chatService?.getChatCore()?.getAttachmentDelegate() ?: return

        val items = wakePrefs.voiceAutoAttachItemsFlow.first()
        items
            .asSequence()
            .filter { it.enabled }
            .forEach { item ->
                val keywordConfig = item.keywords.trim()
                if (keywordConfig.isBlank()) return@forEach
                if (!matchesAnyKeyword(text, keywordConfig)) return@forEach

                when (item.type) {
                    WakeWordPreferences.VoiceAutoAttachType.SCREEN_OCR -> {
                        attachmentDelegate.captureScreenContent()
                    }
                    WakeWordPreferences.VoiceAutoAttachType.NOTIFICATIONS -> {
                        attachmentDelegate.captureNotifications()
                    }
                    WakeWordPreferences.VoiceAutoAttachType.LOCATION -> {
                        attachmentDelegate.captureLocation()
                    }
                    WakeWordPreferences.VoiceAutoAttachType.TIME -> {
                        attachmentDelegate.captureCurrentTime()
                    }
                }
            }
    }

    private fun matchesAnyKeyword(text: String, keywordConfig: String): Boolean {
        val keywords =
            keywordConfig
                .split('|', ',', '，', ';', '；', '\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        if (keywords.isEmpty()) return false
        return keywords.any { k -> text.contains(k, ignoreCase = true) }
    }

    fun handleVoiceAvatarMessage(message: ChatMessage?) {
        if (!hasInitializedVoiceAvatarFromSnapshot) {
            hasInitializedVoiceAvatarFromSnapshot = true
            if (message?.sender == "ai" && message.contentStream == null) {
                lastHandledVoiceAvatarMessageKey = buildVoiceAvatarMessageKey(message)
                return
            }
        }

        when (message?.sender) {
            "think" -> startVoiceAvatarThinking()
            "ai" -> {
                if (message.contentStream != null) {
                    startVoiceAvatarThinking()
                    return
                }

                val messageKey = buildVoiceAvatarMessageKey(message)
                if (lastHandledVoiceAvatarMessageKey == messageKey) {
                    return
                }
                lastHandledVoiceAvatarMessageKey = messageKey

                val triggerName = AvatarEmotionManager.extractMoodTagValue(message.content)
                if (!triggerName.isNullOrBlank()) {
                    pushVoiceAvatarMotion(
                        emotion = AvatarEmotionManager.analyzeEmotion(message.content),
                        triggerName = triggerName,
                        playOnce = true
                    )
                    return
                }

                val emotion = AvatarEmotionManager.analyzeEmotion(message.content)
                if (emotion == AvatarEmotion.IDLE) {
                    resetVoiceAvatarToIdle()
                } else {
                    pushVoiceAvatarMotion(emotion = emotion, playOnce = true)
                }
            }
        }
    }

    fun syncVoiceAvatarWithProcessingState(
        state: InputProcessingState,
        latestMessage: ChatMessage?
    ) {
        val shouldResetThinking =
            (state is InputProcessingState.Idle || state is InputProcessingState.Error) &&
                voiceAvatarMotionRequest.triggerName.isNullOrBlank() &&
                voiceAvatarMotionRequest.emotion == AvatarEmotion.THINKING
        if (!shouldResetThinking) {
            return
        }

        val hasCompletedAiMessage =
            latestMessage?.sender == "ai" && latestMessage.contentStream == null
        if (!hasCompletedAiMessage) {
            resetVoiceAvatarToIdle()
        }
    }

    private fun buildVoiceAvatarMessageKey(message: ChatMessage): String {
        return "${message.sender}:${message.timestamp}:${message.content.hashCode()}:${message.contentStream == null}"
    }

    private fun pushVoiceAvatarMotion(
        emotion: AvatarEmotion,
        triggerName: String? = null,
        playOnce: Boolean
    ) {
        voiceAvatarSequence += 1
        voiceAvatarMotionRequest = VoiceAvatarMotionRequest(
            emotion = emotion,
            triggerName = triggerName,
            playOnce = playOnce,
            sequence = voiceAvatarSequence
        )
    }

    private fun startVoiceAvatarThinking() {
        pushVoiceAvatarMotion(emotion = AvatarEmotion.THINKING, playOnce = false)
    }

    private fun resetVoiceAvatarToIdle() {
        pushVoiceAvatarMotion(emotion = AvatarEmotion.IDLE, playOnce = false)
    }

    private fun stripVoiceAvatarTags(content: String): String {
        return AvatarEmotionManager.stripXmlLikeTags(content)
    }
}

@Composable
 fun rememberFloatingFullscreenModeViewModel(
     context: Context,
     floatContext: FloatContext,
     coroutineScope: CoroutineScope,
     initialWaveActive: Boolean
 ) = remember(context, initialWaveActive) {
     FloatingFullscreenModeViewModel(context, floatContext, coroutineScope, initialWaveActive)
 }
