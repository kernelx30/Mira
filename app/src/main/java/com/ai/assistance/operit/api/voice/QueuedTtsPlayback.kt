package com.ai.assistance.operit.api.voice

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.PlaybackParams
import com.ai.assistance.operit.util.AppLogger
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class QueuedTtsPlayback(
    private val tag: String,
    private val prepareAudioFile: suspend (Request) -> File?,
) {
    companion object {
        const val EXTRA_APPLY_LOCAL_PLAYBACK_PARAMS = "operit_apply_local_playback_params"
    }

    data class Request(
        val text: String,
        val rate: Float?,
        val pitch: Float?,
        val extraParams: Map<String, String>,
        val generation: Long,
        val completion: CompletableDeferred<Boolean>,
    )

    private data class PreparedRequest(
        val request: Request,
        val audioFile: File,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val speakQueue = Channel<Request>(Channel.UNLIMITED)
    private val playbackQueue = Channel<PreparedRequest>(capacity = 1)
    private val stopGeneration = AtomicLong(0)
    private val isPaused = AtomicBoolean(false)
    private val _isSpeaking = MutableStateFlow(false)
    private var mediaPlayer: MediaPlayer? = null
    @Volatile private var activePlaybackCompletion: CompletableDeferred<Boolean>? = null

    val speakingStateFlow: Flow<Boolean> = _isSpeaking.asStateFlow()
    val isSpeaking: Boolean
        get() = _isSpeaking.value

    init {
        scope.launch {
            for (request in speakQueue) {
                try {
                    val audioFile = prepareAudioFile(request)
                    if (audioFile == null) {
                        request.completion.complete(false)
                    } else {
                        var queued = false
                        try {
                            playbackQueue.send(PreparedRequest(request, audioFile))
                            queued = true
                        } finally {
                            if (!queued) {
                                runCatching { audioFile.delete() }
                            }
                        }
                    }
                } catch (e: Exception) {
                    request.completion.completeExceptionally(e)
                }
            }
        }

        scope.launch {
            for (prepared in playbackQueue) {
                try {
                    val result = playPreparedRequest(prepared)
                    prepared.request.completion.complete(result)
                } catch (e: Exception) {
                    prepared.request.completion.completeExceptionally(e)
                } finally {
                    runCatching { prepared.audioFile.delete() }
                }
            }
        }
    }

    suspend fun speak(
        text: String,
        interrupt: Boolean,
        rate: Float?,
        pitch: Float?,
        extraParams: Map<String, String>,
    ): Boolean = withContext(Dispatchers.IO) {
        if (interrupt) {
            clearForInterrupt()
        }

        val completion = CompletableDeferred<Boolean>()
        val request = Request(
            text = text,
            rate = rate,
            pitch = pitch,
            extraParams = extraParams,
            generation = stopGeneration.get(),
            completion = completion,
        )
        speakQueue.send(request)
        completion.await()
    }

    suspend fun stop(): Boolean = withContext(Dispatchers.IO) {
        stopGeneration.incrementAndGet()
        isPaused.set(false)
        clearPendingRequests()
        clearPendingPlayback()
        stopPlaybackOnly()
    }

    suspend fun pause(): Boolean = withContext(Dispatchers.IO) {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.pause()
                    isPaused.set(true)
                    _isSpeaking.value = false
                    return@withContext true
                }
            }
            false
        } catch (e: Exception) {
            AppLogger.e(tag, "暂停TTS播放失败", e)
            false
        }
    }

    suspend fun resume(): Boolean = withContext(Dispatchers.IO) {
        try {
            mediaPlayer?.let {
                if (!it.isPlaying) {
                    it.start()
                    isPaused.set(false)
                    _isSpeaking.value = true
                    return@withContext true
                }
            }
            false
        } catch (e: Exception) {
            AppLogger.e(tag, "恢复TTS播放失败", e)
            false
        }
    }

    fun shutdown() {
        stopGeneration.incrementAndGet()
        isPaused.set(false)
        clearPendingRequests()
        clearPendingPlayback()
        stopPlaybackOnly()
        try {
            mediaPlayer?.release()
        } catch (e: Exception) {
            AppLogger.w(tag, "释放TTS播放器失败", e)
        }
        mediaPlayer = null
        scope.cancel()
        speakQueue.close()
        playbackQueue.close()
    }

    fun isCurrent(request: Request): Boolean {
        return request.generation == stopGeneration.get()
    }

    private fun clearPendingRequests() {
        while (true) {
            val request = speakQueue.tryReceive().getOrNull() ?: break
            request.completion.complete(false)
        }
    }

    private fun clearPendingPlayback() {
        while (true) {
            val prepared = playbackQueue.tryReceive().getOrNull() ?: break
            runCatching { prepared.audioFile.delete() }
            prepared.request.completion.complete(false)
        }
    }

    private fun clearForInterrupt() {
        stopGeneration.incrementAndGet()
        isPaused.set(false)
        clearPendingRequests()
        clearPendingPlayback()
        stopPlaybackOnly()
    }

    private fun stopPlaybackOnly(): Boolean {
        return try {
            isPaused.set(false)
            activePlaybackCompletion?.complete(false)
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.reset()
                _isSpeaking.value = false
                true
            } ?: false
        } catch (e: Exception) {
            AppLogger.e(tag, "停止TTS播放失败", e)
            false
        }
    }

    private suspend fun playPreparedRequest(prepared: PreparedRequest): Boolean {
        if (!isCurrent(prepared.request)) {
            return false
        }
        return playAudioFile(prepared.audioFile, prepared.request)
    }

    private suspend fun playAudioFile(audioFile: File, request: Request): Boolean {
        if (!audioFile.exists() || audioFile.length() == 0L) {
            AppLogger.e(tag, "Audio file is invalid: ${audioFile.absolutePath}")
            return false
        }

        var playbackCompletion: CompletableDeferred<Boolean>? = null
        return try {
            withContext(Dispatchers.IO) {
                isPaused.set(false)
                FileInputStream(audioFile).use { fis ->
                    val completion = CompletableDeferred<Boolean>()
                    playbackCompletion = completion
                    val mp = MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .build()
                        )
                        setDataSource(fis.fd)
                        prepare()
                        setOnCompletionListener {
                            completion.complete(true)
                        }
                        setOnErrorListener { _, what, extra ->
                            AppLogger.e(tag, "MediaPlayer error: what=$what, extra=$extra")
                            completion.complete(false)
                            true
                        }
                        if (
                            request.extraParams[EXTRA_APPLY_LOCAL_PLAYBACK_PARAMS]
                                ?.equals("true", ignoreCase = true) == true
                        ) {
                            val playbackRate = (request.rate ?: 1f).coerceIn(0.5f, 2f)
                            val playbackPitch = (request.pitch ?: 1f).coerceIn(0.5f, 2f)
                            playbackParams =
                                PlaybackParams()
                                    .allowDefaults()
                                    .setSpeed(playbackRate)
                                    .setPitch(playbackPitch)
                            AppLogger.d(
                                tag,
                                "apply local TTS playback rate=$playbackRate pitch=$playbackPitch"
                            )
                        }
                        start()
                    }

                    mediaPlayer?.release()
                    mediaPlayer = mp
                    activePlaybackCompletion = completion
                    _isSpeaking.value = true
                }
            }
            playbackCompletion?.await() ?: false
        } catch (e: Exception) {
            AppLogger.e(tag, "播放TTS音频失败", e)
            false
        } finally {
            _isSpeaking.value = false
            isPaused.set(false)
            if (activePlaybackCompletion === playbackCompletion) {
                activePlaybackCompletion = null
            }
            mediaPlayer?.apply {
                try {
                    if (isPlaying) {
                        stop()
                    }
                } catch (_: Exception) {
                }
                try {
                    release()
                } catch (_: Exception) {
                }
            }
            mediaPlayer = null
        }
    }
}
