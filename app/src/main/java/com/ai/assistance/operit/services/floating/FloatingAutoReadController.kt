package com.ai.assistance.operit.services.floating

import android.content.Context
import com.ai.assistance.operit.api.voice.ExpressiveTtsDirector
import com.ai.assistance.operit.api.voice.VoiceCapabilities
import com.ai.assistance.operit.api.voice.VoiceService
import com.ai.assistance.operit.api.voice.VoiceServiceFactory
import com.ai.assistance.operit.core.chat.SpeechMarkupParser
import com.ai.assistance.operit.data.model.SpeechDelivery
import com.ai.assistance.operit.data.model.SpeechSegment
import com.ai.assistance.operit.data.preferences.SpeechServicesPreferences
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.TtsCleaner
import com.ai.assistance.operit.util.WaifuMessageProcessor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 悬浮窗普通聊天的自动朗读出口。
 *
 * FLOATING ChatServiceCore 与主聊天使用不同实例，主界面绑定的 ChatViewModel TTS handler
 * 不会覆盖悬浮窗实例。这里必须把悬浮窗的 handler 明确接到 VoiceService，
 * 否则自动朗读流程只会执行到 ChatServiceCore 的日志占位。
 */
internal class FloatingAutoReadController(
    context: Context,
    private val coroutineScope: CoroutineScope,
    private val shouldSpeak: () -> Boolean,
) {
    private val appContext = context.applicationContext
    private val speechPreferences = SpeechServicesPreferences(appContext)
    private val playbackLock = Any()
    private val parentContext = coroutineScope.coroutineContext
    private val parentJob = parentContext[Job]
    private var playbackSessionJob: Job = SupervisorJob(parentJob)
    private var playbackScope =
        CoroutineScope(parentContext.minusKey(Job) + playbackSessionJob)
    private var playbackJob: Job? = null
    private var stopJob: Job? = null

    suspend fun speak(text: String, interrupt: Boolean) {
        val allowed = shouldSpeak()
        if (text.isBlank() || !allowed) {
            AppLogger.d(TAG, "skip floating auto read: allowed=$allowed len=${text.length}")
            return
        }

        val scheduledJob =
            synchronized(playbackLock) {
                val interruptedSession =
                    if (interrupt) resetPlaybackSessionLocked() else null
                val pendingStopJob = stopJob
                val previousJob = playbackJob
                val job =
                    playbackScope.launch(start = CoroutineStart.LAZY) {
                        try {
                            interruptedSession?.join()
                            pendingStopJob?.join()
                            if (interrupt) {
                                runCatching { VoiceServiceFactory.getInstance(appContext).stop() }
                                    .onFailure {
                                        AppLogger.w(TAG, "failed to interrupt floating auto read", it)
                                    }
                            } else {
                                previousJob?.join()
                            }
                            currentCoroutineContext().ensureActive()
                            play(text = text, interrupt = interrupt)
                        } catch (_: CancellationException) {
                        } catch (error: Exception) {
                            AppLogger.e(TAG, "floating auto read failed", error)
                        }
                    }
                playbackJob = job
                job.invokeOnCompletion {
                    synchronized(playbackLock) {
                        if (playbackJob === job) {
                            playbackJob = null
                        }
                    }
                }
                job
            }

        scheduledJob.start()
    }

    fun stop() {
        val scheduledStop =
            synchronized(playbackLock) {
                val interruptedSession = resetPlaybackSessionLocked()
                val previousStopJob = stopJob
                val job =
                    coroutineScope.launch(start = CoroutineStart.LAZY) {
                        interruptedSession.join()
                        previousStopJob?.join()
                        runCatching { VoiceServiceFactory.getInstance(appContext).stop() }
                            .onFailure { AppLogger.w(TAG, "failed to stop floating auto read", it) }
                    }
                stopJob = job
                job.invokeOnCompletion {
                    synchronized(playbackLock) {
                        if (stopJob === job) {
                            stopJob = null
                        }
                    }
                }
                job
            }
        scheduledStop.start()
    }

    /**
     * 一次回复会被拆成多个自动朗读任务。只取消队尾会让前面的任务在 stop 后继续唤醒后续队列，
     * 因此中断时直接作废整代播放作用域，再为下一次回复创建新作用域。返回旧作用域，
     * 让新朗读先等旧 Provider 调用完全退出，避免迟到的 stop 误伤下一段语音。
     */
    private fun resetPlaybackSessionLocked(): Job {
        val interruptedSession = playbackSessionJob
        interruptedSession.cancel()
        playbackSessionJob = SupervisorJob(parentJob)
        playbackScope = CoroutineScope(parentContext.minusKey(Job) + playbackSessionJob)
        playbackJob = null
        return interruptedSession
    }

    private suspend fun play(text: String, interrupt: Boolean) {
        currentCoroutineContext().ensureActive()
        if (!shouldSpeak()) return

        val voiceService = VoiceServiceFactory.getInstance(appContext)
        if (!ensureInitialized(voiceService)) {
            AppLogger.e(TAG, "floating TTS provider initialization failed")
            return
        }

        val cleanerRegexs = speechPreferences.ttsCleanerRegexsFlow.first()
        val parsedSpeech = SpeechMarkupParser.parse(text)
        val cleanedSegments =
            parsedSpeech.segments.mapNotNull { segment ->
                val cleaned =
                    WaifuMessageProcessor.cleanContentForWaifu(
                        TtsCleaner.clean(segment.text, cleanerRegexs),
                    )
                cleaned.takeIf { it.isNotBlank() }?.let {
                    SpeechSegment(text = it, direction = segment.direction)
                }
            }
        if (cleanedSegments.isEmpty()) {
            AppLogger.d(TAG, "skip blank floating speech after cleaning")
            return
        }

        val baseRate = speechPreferences.ttsSpeechRateFlow.first()
        val basePitch = speechPreferences.ttsPitchFlow.first()
        val expressiveEnabled = speechPreferences.expressiveTtsEnabledFlow.first()
        val expressionStrength = speechPreferences.expressiveTtsStrengthFlow.first()
        val requests =
            ExpressiveTtsDirector.planSegments(
                segments = cleanedSegments,
                capabilities =
                    if (expressiveEnabled) voiceService.capabilities else VoiceCapabilities.PLAIN,
                baseRate = baseRate,
                basePitch = basePitch,
                delivery = SpeechDelivery.CONVERSATION,
                expressionScale = expressionStrength.scale,
            )

        var firstSegment = true
        for (request in requests) {
            currentCoroutineContext().ensureActive()
            if (!shouldSpeak()) {
                voiceService.stop()
                return
            }
            val accepted =
                voiceService.speak(
                    text = request.text,
                    interrupt = if (firstSegment) interrupt else false,
                    rate = request.rate,
                    pitch = request.pitch,
                    extraParams = request.extraParams,
                )
            if (!accepted) {
                AppLogger.e(TAG, "floating TTS provider rejected speech request")
                return
            }
            while (voiceService.isSpeaking && shouldSpeak()) {
                delay(50L)
            }
            if (!shouldSpeak()) {
                voiceService.stop()
                return
            }
            firstSegment = false
        }
    }

    private suspend fun ensureInitialized(service: VoiceService): Boolean {
        return service.isInitialized || service.initialize()
    }

    private companion object {
        const val TAG = "FloatingAutoRead"
    }
}

internal fun shouldUseFloatingCoreAutoRead(mode: com.ai.assistance.operit.ui.floating.FloatingMode): Boolean =
    mode != com.ai.assistance.operit.ui.floating.FloatingMode.FULLSCREEN &&
        mode != com.ai.assistance.operit.ui.floating.FloatingMode.SCREEN_OCR

internal fun resolveFloatingCoreAutoReadOverride(
    mode: com.ai.assistance.operit.ui.floating.FloatingMode,
    conversationOverride: Boolean?,
): Boolean? =
    if (shouldUseFloatingCoreAutoRead(mode)) conversationOverride else false
