package com.ai.assistance.operit.services.core

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.api.chat.library.MemoryAutoSaveWorkScheduler
import com.ai.assistance.operit.api.chat.library.MemoryAutoSaveScheduler
import com.ai.assistance.operit.core.chat.AIMessageManager
import com.ai.assistance.operit.core.chat.CompanionMemoryImportanceDetector
import com.ai.assistance.operit.core.chat.CompanionMemoryTargetResolver
import com.ai.assistance.operit.core.chat.SpeechContentMetadata
import com.ai.assistance.operit.core.chat.logMessageTiming
import com.ai.assistance.operit.core.chat.messageTimingNow
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.agent.PhoneAgentJobRegistry
import com.ai.assistance.operit.data.model.*
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.repository.MemoryAutoSaveCandidateRepository
import com.ai.assistance.operit.data.model.InputProcessingState as EnhancedInputProcessingState
import com.ai.assistance.operit.data.model.PromptFunctionType
import com.ai.assistance.operit.data.skill.ChatSkillActivationStore
import com.ai.assistance.operit.util.stream.SharedStream
import com.ai.assistance.operit.util.stream.TextStreamEventCarrier
import com.ai.assistance.operit.util.stream.TextStreamEventType
import com.ai.assistance.operit.util.stream.TextStreamRevisionTracker
import com.ai.assistance.operit.util.TtsSegmenter
import com.ai.assistance.operit.util.TtsNaturalBlockBuffer
import com.ai.assistance.operit.util.WaifuMessageProcessor
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.WaifuPreferences
import com.ai.assistance.operit.data.preferences.FunctionalConfigManager
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.features.chat.webview.workspace.WorkspaceBackupManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import com.ai.assistance.operit.core.tools.ToolProgressBus
import com.ai.assistance.operit.util.ConversationContentVisibility
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

internal fun hasAssistantResponseContent(
    rawContent: String,
    emittedSegments: List<String>,
): Boolean =
    ConversationContentVisibility.hasRenderableAssistantContent(rawContent) ||
        emittedSegments.any(ConversationContentVisibility::hasRenderableAssistantContent)

internal fun chatHistoryForDispatch(
    history: List<ChatMessage>,
    excludedMessageTimestamps: Set<Long>,
): List<ChatMessage> =
    if (excludedMessageTimestamps.isEmpty()) {
        history
    } else {
        history.filterNot { message -> message.timestamp in excludedMessageTimestamps }
    }

internal fun shouldConsumeUserDraft(
    currentText: String?,
    dispatchedText: String,
): Boolean = currentText == dispatchedText

internal fun shouldClearCompletedSendJob(
    runtimeGeneration: Long,
    completedGeneration: Long,
    isSameJob: Boolean,
): Boolean = runtimeGeneration == completedGeneration && isSameJob

internal fun isChatRuntimeBusy(
    isLoading: Boolean,
    isCancelling: Boolean,
): Boolean = isLoading || isCancelling

internal suspend fun isolateMemoryEnqueueFailure(
    block: suspend () -> Unit,
): Exception? =
    try {
        block()
        null
    } catch (error: kotlinx.coroutines.CancellationException) {
        throw error
    } catch (error: Exception) {
        error
    }

internal class ChatCancellationGate {
    private val mutex = Mutex()

    suspend fun run(block: suspend () -> Unit) {
        mutex.withLock { block() }
    }
}

internal suspend fun cancelAndJoinJobs(jobs: Collection<Job>) {
    jobs.forEach { job -> job.cancel() }
    // Once cancellation starts, destructive history work must not pass the gate until every
    // writer has stopped, even when the caller itself is cancelled while waiting.
    withContext(NonCancellable) {
        jobs.forEach { job -> job.join() }
    }
}

internal fun shouldPersistCancelledWaifuResponse(
    isWaifuModeEnabled: Boolean,
    preservePartialResponse: Boolean,
    partialContent: String?,
): Boolean =
    isWaifuModeEnabled &&
        preservePartialResponse &&
        !partialContent.isNullOrBlank()

internal fun isCancellationResponseCandidate(
    message: ChatMessage,
    snapshotSentAt: Long?,
): Boolean =
    message.sender == "ai" &&
        (
            message.contentStream != null ||
                (snapshotSentAt != null && snapshotSentAt > 0L && message.sentAt == snapshotSentAt)
        )

internal fun isPendingUserDispatch(message: ChatMessage): Boolean =
    message.sender == "user" &&
        message.displayMode == ChatMessageDisplayMode.PENDING_DISPATCH

internal fun shouldStreamProvisionalAssistantOutput(hasRevisionEvents: Boolean): Boolean =
    !hasRevisionEvents

internal fun shouldClearToolProgress(
    state: EnhancedInputProcessingState,
    hasOtherActiveChat: Boolean,
    hasPendingSummary: Boolean,
): Boolean {
    val keepsToolProgress =
        state is EnhancedInputProcessingState.ExecutingTool ||
            state is EnhancedInputProcessingState.ToolProgress ||
            state is EnhancedInputProcessingState.ProcessingToolResult ||
            state is EnhancedInputProcessingState.Summarizing
    return !keepsToolProgress && !hasOtherActiveChat && !hasPendingSummary
}

/** 委托类，负责处理消息处理相关功能 */
class MessageProcessingDelegate(
        private val context: Context,
        private val coroutineScope: CoroutineScope,
        private val getEnhancedAiService: () -> EnhancedAIService?,
        private val getFullChatHistory: suspend (String) -> List<ChatMessage>,
        private val getRuntimeChatHistory: suspend (String) -> List<ChatMessage>,
        private val hasUserMessage: suspend (String) -> Boolean,
        private val addMessageToChat: suspend (String, ChatMessage) -> Unit,
        private val saveCurrentChat: suspend () -> Unit,
        private val showErrorMessage: (String) -> Unit,
        private val updateChatTitle: (chatId: String, title: String) -> Unit,
        private val getChatTitle: (chatId: String) -> String?,
        private val onTurnComplete:
            suspend (chatId: String?, service: EnhancedAIService, nextWindowSize: Int?, turnOptions: ChatTurnOptions) -> Unit,
        private val onTokenLimitExceeded: suspend (
            chatId: String?,
            roleCardId: String?,
            isGroupOrchestrationTurn: Boolean,
            groupParticipantNamesText: String?
        ) -> Unit,
        // 添加自动朗读相关的回调
        private val getIsAutoReadEnabled: () -> Boolean,
        private var speakMessageHandler: suspend (String, Boolean) -> Unit
) {
    companion object {
        private const val TAG = "MessageProcessingDelegate"
        private const val STREAM_SCROLL_THROTTLE_MS = 200L
        private const val STREAM_PERSIST_INTERVAL_MS = 1000L
        private const val FIRST_RESPONSE_TIMEOUT_MS = 120_000L
        private const val TEXT_PUBLISH_BEFORE_SPEECH_MS = 16L
        private const val AUTO_READ_PREVIEW_MAX = 48
    }



    private fun fallbackConversationTitle(userText: String, attachments: List<AttachmentInfo>): String {
        return attachments.firstOrNull()?.fileName?.trim()?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.new_conversation)
    }

    private fun launchConversationTitleGeneration(
        chatId: String,
        userText: String,
        attachments: List<AttachmentInfo>,
        fallbackTitle: String
    ) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val generatedTitle = EnhancedAIService.getChatInstance(context, chatId)
                    .generateConversationTitle(
                        userText = userText,
                        attachmentFileNames = attachments.map { it.fileName }
                    )
                    .trim()
                if (generatedTitle.isNotBlank() && getChatTitle(chatId) == fallbackTitle) {
                    updateChatTitle(chatId, generatedTitle)
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "生成对话标题失败", e)
            }
        }
    }

    private fun speechPreview(text: String): String {
        return text.replace("\n", "\\n").take(AUTO_READ_PREVIEW_MAX)
    }

    // 角色卡管理器
    private val characterCardManager = CharacterCardManager.getInstance(context)
    
    // 模型配置管理器
    private val modelConfigManager = ModelConfigManager(context)
    
    // 功能配置管理器，用于获取正确的模型配置ID
    private val functionalConfigManager = FunctionalConfigManager(context)

    private val _userMessage = MutableStateFlow(TextFieldValue(""))
    val userMessage: StateFlow<TextFieldValue> = _userMessage.asStateFlow()
    private val userMessageDraftsByChatId = ConcurrentHashMap<String, TextFieldValue>()
    @Volatile
    private var activeDraftChatId: String? = null

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _activeStreamingChatIds = MutableStateFlow<Set<String>>(emptySet())
    val activeStreamingChatIds: StateFlow<Set<String>> = _activeStreamingChatIds.asStateFlow()

    private val _inputProcessingStateByChatId =
        MutableStateFlow<Map<String, EnhancedInputProcessingState>>(emptyMap())
    val inputProcessingStateByChatId: StateFlow<Map<String, EnhancedInputProcessingState>> =
        _inputProcessingStateByChatId.asStateFlow()

    private val _scrollToBottomEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val scrollToBottomEvent = _scrollToBottomEvent.asSharedFlow()

    private val _nonFatalErrorEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val nonFatalErrorEvent = _nonFatalErrorEvent.asSharedFlow()

    private val _turnCompleteCounterByChatId = MutableStateFlow<Map<String, Long>>(emptyMap())
    val turnCompleteCounterByChatId: StateFlow<Map<String, Long>> =
        _turnCompleteCounterByChatId.asStateFlow()
    private val _currentTurnToolInvocationCountByChatId =
        MutableStateFlow<Map<String, Int>>(emptyMap())
    val currentTurnToolInvocationCountByChatId: StateFlow<Map<String, Int>> =
        _currentTurnToolInvocationCountByChatId.asStateFlow()

    // 当前活跃的AI响应流
    private data class ChatRuntime(
        var sendJob: Job? = null,
        var responseStream: SharedStream<String>? = null,
        var streamCollectionJob: Job? = null,
        var stateCollectionJob: Job? = null,
        var autoReadJob: Job? = null,
        var currentTurnOptions: ChatTurnOptions = ChatTurnOptions(),
        var requestSentAt: Long = 0L,
        var requestStartElapsed: Long = 0L,
        var firstResponseElapsed: Long? = null,
        var preservePartialResponseOnCancellation: Boolean = true,
        @Volatile var isCancelling: Boolean = false,
        val cancellationGate: ChatCancellationGate = ChatCancellationGate(),
        val sendGeneration: AtomicLong = AtomicLong(0L),
        val isLoading: MutableStateFlow<Boolean> = MutableStateFlow(false)
    )

    private val chatRuntimes = ConcurrentHashMap<String, ChatRuntime>()
    private val lastScrollEmitMsByChatKey = ConcurrentHashMap<String, AtomicLong>()
    private val suppressIdleCompletedStateByChatId = ConcurrentHashMap<String, Boolean>()
    private val pendingAsyncSummaryUiByChatId = ConcurrentHashMap<String, Boolean>()

    private fun chatKey(chatId: String?): String = chatId ?: "__DEFAULT_CHAT__"

    private fun tryEmitScrollToBottomThrottled(chatId: String?) {
        val key = chatKey(chatId)
        val now = System.currentTimeMillis()
        val last = lastScrollEmitMsByChatKey.getOrPut(key) { AtomicLong(0L) }
        val prev = last.get()
        if (now - prev >= STREAM_SCROLL_THROTTLE_MS && last.compareAndSet(prev, now)) {
            _scrollToBottomEvent.tryEmit(Unit)
        }
    }

    private fun forceEmitScrollToBottom(chatId: String?) {
        val key = chatKey(chatId)
        lastScrollEmitMsByChatKey.getOrPut(key) { AtomicLong(0L) }.set(System.currentTimeMillis())
        _scrollToBottomEvent.tryEmit(Unit)
    }

    private fun runtimeFor(chatId: String?): ChatRuntime {
        val key = chatKey(chatId)
        return chatRuntimes.computeIfAbsent(key) { ChatRuntime() }
    }

    private fun updateGlobalLoadingState() {
        val anyLoading = chatRuntimes.values.any { runtime ->
            isChatRuntimeBusy(runtime.isLoading.value, runtime.isCancelling)
        }
        val activeChatIds = chatRuntimes
            .filter { (_, runtime) ->
                isChatRuntimeBusy(runtime.isLoading.value, runtime.isCancelling)
            }
            .keys
            .filter { it != "__DEFAULT_CHAT__" }
            .toSet()

        _activeStreamingChatIds.value = activeChatIds
        _isLoading.value = anyLoading
    }

    private fun isTerminalInputState(state: EnhancedInputProcessingState): Boolean {
        return state is EnhancedInputProcessingState.Idle ||
            state is EnhancedInputProcessingState.Completed
    }

    private fun setChatInputProcessingState(chatId: String?, state: EnhancedInputProcessingState) {
        val chatRuntime = chatId?.let(::runtimeFor)
        if (chatRuntime != null &&
            isChatRuntimeBusy(
                chatRuntime.isLoading.value,
                chatRuntime.isCancelling,
            ) &&
            isTerminalInputState(state)
        ) {
            return
        }
        if (chatId != null && suppressIdleCompletedStateByChatId.containsKey(chatId)) {
            if (isTerminalInputState(state)) {
                return
            }
        }
        val currentKey = chatKey(chatId)
        val hasOtherActiveChat =
            chatRuntimes.any { (key, runtime) ->
                key != currentKey && isChatRuntimeBusy(runtime.isLoading.value, runtime.isCancelling)
            }
        val hasPendingSummary = pendingAsyncSummaryUiByChatId.isNotEmpty()
        if (shouldClearToolProgress(state, hasOtherActiveChat, hasPendingSummary)) {
            ToolProgressBus.clear()
        }
        _inputProcessingStateByChatId.update { current ->
            current.toMutableMap().apply { this[currentKey] = state }
        }
    }

    fun setSuppressIdleCompletedStateForChat(chatId: String, suppress: Boolean) {
        if (suppress) {
            suppressIdleCompletedStateByChatId[chatId] = true
        } else {
            suppressIdleCompletedStateByChatId.remove(chatId)
        }
    }

    fun setPendingAsyncSummaryUiForChat(chatId: String, pending: Boolean) {
        if (pending) {
            pendingAsyncSummaryUiByChatId[chatId] = true
        } else {
            pendingAsyncSummaryUiByChatId.remove(chatId)
        }
    }

    fun setInputProcessingStateForChat(chatId: String, state: EnhancedInputProcessingState) {
        setChatInputProcessingState(chatId, state)
    }

    suspend fun buildUserMessageContentForGroupOrchestration(
        messageText: String,
        attachments: List<AttachmentInfo>,
        workspacePath: String?,
        workspaceEnv: String?,
        replyToMessage: ChatMessage?,
        chatId: String? = null
    ): String = withContext(Dispatchers.IO) {
        val totalStartTime = messageTimingNow()
        val configId = functionalConfigManager.getConfigIdForFunction(FunctionType.CHAT)
        val currentModelConfig = modelConfigManager.getModelConfigFlow(configId).first()
        val enableDirectImageProcessing = currentModelConfig.enableDirectImageProcessing
        val enableDirectAudioProcessing = currentModelConfig.enableDirectAudioProcessing
        val enableDirectVideoProcessing = currentModelConfig.enableDirectVideoProcessing

        val finalMessageContent = AIMessageManager.buildUserMessageContent(
            context = context,
            messageText = messageText,
            attachments = attachments,
            workspacePath = workspacePath,
            workspaceEnv = workspaceEnv,
            replyToMessage = replyToMessage,
            enableDirectImageProcessing = enableDirectImageProcessing,
            enableDirectAudioProcessing = enableDirectAudioProcessing,
            enableDirectVideoProcessing = enableDirectVideoProcessing,
            chatId = chatId
        )
        logMessageTiming(
            stage = "delegate.groupOrchestration.buildUserMessageContent",
            startTimeMs = totalStartTime,
            details = "attachments=${attachments.size}, configId=$configId, finalLength=${finalMessageContent.length}"
        )
        finalMessageContent
    }

    fun getResponseStream(chatId: String): SharedStream<String>? {
        return chatRuntimes[chatKey(chatId)]?.responseStream
    }

    private fun resolveFinalContent(aiMessage: ChatMessage): String {
        val sharedStream = aiMessage.contentStream as? SharedStream<String>
        val replayChunks = sharedStream?.replayCache
        val eventCarrier = aiMessage.contentStream as? TextStreamEventCarrier

        return if (eventCarrier?.eventChannel?.replayCache?.isNotEmpty() == true) {
            aiMessage.content
        } else if (!replayChunks.isNullOrEmpty()) {
            replayChunks.joinToString(separator = "")
        } else {
            aiMessage.content
        }
    }

    private fun ChatMessage.withTurnMetrics(
        inputTokens: Int,
        outputTokens: Int,
        cachedInputTokens: Int,
        sentAt: Long,
        outputDurationMs: Long,
        waitDurationMs: Long
    ): ChatMessage {
        return copy(
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            cachedInputTokens = cachedInputTokens,
            sentAt = sentAt,
            outputDurationMs = outputDurationMs,
            waitDurationMs = waitDurationMs
        )
    }

    private data class TurnCancellationSnapshot(
        val inputTokens: Int,
        val outputTokens: Int,
        val cachedInputTokens: Int,
        val sentAt: Long,
        val outputDurationMs: Long,
        val waitDurationMs: Long,
    )

    private fun readCurrentTurnCancellationSnapshot(chatId: String): TurnCancellationSnapshot? {
        val service =
            EnhancedAIService.getChatInstance(context, chatId)
                ?: getEnhancedAiService()
                ?: return null
        val runtime = runtimeFor(chatId)
        return runCatching {
            val snapshot = service.captureCurrentTurnTokenSnapshot()
            val sentAt = runtime.requestSentAt
            val firstResponseElapsed = runtime.firstResponseElapsed
            val waitDurationMs =
                if (runtime.requestStartElapsed > 0L && firstResponseElapsed != null) {
                    (firstResponseElapsed - runtime.requestStartElapsed).coerceAtLeast(0L)
                } else {
                    0L
                }
            val outputDurationMs =
                if (firstResponseElapsed != null) {
                    (messageTimingNow() - firstResponseElapsed).coerceAtLeast(0L)
                } else {
                    0L
                }
            TurnCancellationSnapshot(
                inputTokens = snapshot.inputTokens,
                outputTokens = snapshot.outputTokens,
                cachedInputTokens = snapshot.cachedInputTokens,
                sentAt = sentAt,
                outputDurationMs = outputDurationMs,
                waitDurationMs = waitDurationMs,
            )
        }.onFailure {
            AppLogger.w(TAG, "读取取消请求的统计快照失败", it)
        }.getOrNull()
    }

    private suspend fun detachStreamingAiMessage(
        chatId: String,
        snapshot: TurnCancellationSnapshot? = null,
    ) {
        val messages = getRuntimeChatHistory(chatId)
        val snapshotSentAt = snapshot?.sentAt?.takeIf { it > 0L }
        val streamingMessage =
            messages.lastOrNull { message ->
                isCancellationResponseCandidate(message, snapshotSentAt)
            }
                ?: return
        val finalContent = resolveFinalContent(streamingMessage)
        val preparedSpeech = SpeechContentMetadata.prepare(finalContent)
        if (!ConversationContentVisibility.hasRenderableAssistantContent(preparedSpeech.visibleText)) {
            AppLogger.w(
                TAG,
                "Skipping blank assistant message after cancellation: chatId=$chatId rawLen=${finalContent.length}",
            )
            return
        }
        streamingMessage.content = preparedSpeech.visibleText
        val completedAt = System.currentTimeMillis()
        val finalMessage =
            snapshot?.let { stats ->
                streamingMessage.withTurnMetrics(
                    inputTokens = stats.inputTokens,
                    outputTokens = stats.outputTokens,
                    cachedInputTokens = stats.cachedInputTokens,
                    sentAt = stats.sentAt.takeIf { it > 0L } ?: streamingMessage.sentAt,
                    outputDurationMs = stats.outputDurationMs,
                    waitDurationMs = stats.waitDurationMs,
                )
            }?.copy(
                content = preparedSpeech.visibleText,
                contentStream = null,
                completedAt = completedAt,
                speechDirectionJson = preparedSpeech.speechDirectionJson,
            )
                ?: streamingMessage.copy(
                    content = preparedSpeech.visibleText,
                    contentStream = null,
                    completedAt = completedAt,
                    speechDirectionJson = preparedSpeech.speechDirectionJson,
                )
        withContext(Dispatchers.Main) {
            snapshot?.let { stats ->
                val matchingUserMessage =
                    messages.lastOrNull { message ->
                        message.sender == "user" &&
                            message.sentAt == (stats.sentAt.takeIf { it > 0L } ?: streamingMessage.sentAt)
                    }
                if (matchingUserMessage != null) {
                    addMessageToChat(
                        chatId,
                        matchingUserMessage.withTurnMetrics(
                            inputTokens = stats.inputTokens,
                            outputTokens = stats.outputTokens,
                            cachedInputTokens = stats.cachedInputTokens,
                            sentAt = stats.sentAt.takeIf { it > 0L } ?: matchingUserMessage.sentAt,
                            outputDurationMs = stats.outputDurationMs,
                            waitDurationMs = stats.waitDurationMs,
                        ),
                    )
                }
            }
            addMessageToChat(chatId, finalMessage)
        }
    }

    private suspend fun cancelMessageInternal(chatId: String, keepPartialResponse: Boolean) {
        val chatRuntime = runtimeFor(chatId)
        chatRuntime.cancellationGate.run {
            cancelMessageLocked(chatId, chatRuntime, keepPartialResponse)
        }
    }

    private suspend fun cancelMessageLocked(
        chatId: String,
        chatRuntime: ChatRuntime,
        keepPartialResponse: Boolean,
    ) {
        val cancellationWork =
            synchronized(chatRuntime) {
                chatRuntime.isCancelling = true
                chatRuntime.preservePartialResponseOnCancellation = keepPartialResponse
                chatRuntime.currentTurnOptions to
                    linkedSetOf<Job>().apply {
                        chatRuntime.sendJob?.let { add(it) }
                        chatRuntime.stateCollectionJob?.let { add(it) }
                        chatRuntime.streamCollectionJob?.let { add(it) }
                        chatRuntime.autoReadJob?.let { add(it) }
                    }
            }
        val (currentTurnOptions, jobsToCancel) = cancellationWork
        updateGlobalLoadingState()

        try {
            val cancellationSnapshot =
                if (keepPartialResponse) readCurrentTurnCancellationSnapshot(chatId) else null

            clearCurrentTurnToolInvocationCount(chatId)
            val operationCancellation = AIMessageManager.cancelOperation(chatId)

            cancelAndJoinJobs(jobsToCancel)
            withContext(NonCancellable) {
                operationCancellation.awaitCompletion()
            }

            if (keepPartialResponse) {
                detachStreamingAiMessage(chatId, snapshot = cancellationSnapshot)
            }

            getRuntimeChatHistory(chatId)
                .filter(::isPendingUserDispatch)
                .forEach { pendingMessage ->
                    addMessageToChat(
                        chatId,
                        pendingMessage.copy(displayMode = ChatMessageDisplayMode.NORMAL),
                    )
                }

            if (currentTurnOptions.persistTurn) {
                withContext(Dispatchers.IO) { saveCurrentChat() }
            }
        } finally {
            synchronized(chatRuntime) {
                chatRuntime.sendJob = null
                chatRuntime.stateCollectionJob = null
                chatRuntime.streamCollectionJob = null
                chatRuntime.autoReadJob = null
                chatRuntime.responseStream = null
                chatRuntime.isLoading.value = false
                chatRuntime.currentTurnOptions = ChatTurnOptions()
                chatRuntime.requestSentAt = 0L
                chatRuntime.requestStartElapsed = 0L
                chatRuntime.firstResponseElapsed = null
                chatRuntime.preservePartialResponseOnCancellation = true
                chatRuntime.isCancelling = false
            }
            updateGlobalLoadingState()
            setChatInputProcessingState(chatId, EnhancedInputProcessingState.Idle)
        }
    }

    fun cancelMessage(chatId: String) {
        coroutineScope.launch(Dispatchers.IO) {
            cancelMessageInternal(chatId, keepPartialResponse = true)
        }
    }

    fun cancelAutoRead(chatId: String?) {
        if (chatId == null) return
        val runtime = chatRuntimes[chatKey(chatId)] ?: return
        runtime.autoReadJob?.cancel()
        runtime.autoReadJob = null
    }

    suspend fun cancelMessageForDestructiveMutation(chatId: String) {
        cancelMessageInternal(chatId, keepPartialResponse = false)
    }

    init {
        AppLogger.d(TAG, "MessageProcessingDelegate初始化: 创建滚动事件流")
    }

    fun setActiveDraftChat(chatId: String?) {
        val previousChatId = activeDraftChatId
        if (previousChatId == chatId) {
            return
        }

        val currentValue = _userMessage.value
        if (previousChatId != null) {
            saveUserMessageDraft(previousChatId, currentValue)
        }

        activeDraftChatId = chatId
        if (chatId == null) {
            _userMessage.value = TextFieldValue("")
            return
        }

        val savedDraft = userMessageDraftsByChatId[chatId]
        if (savedDraft != null) {
            _userMessage.value = savedDraft
            return
        }

        if (previousChatId == null && currentValue.text.isNotEmpty()) {
            saveUserMessageDraft(chatId, currentValue)
            _userMessage.value = currentValue
            return
        }

        _userMessage.value = TextFieldValue("")
    }

    fun updateUserMessage(message: String) {
        setUserMessageDraft(TextFieldValue(message))
    }

    fun updateUserMessage(value: TextFieldValue) {
        setUserMessageDraft(value)
    }

    private fun setUserMessageDraft(value: TextFieldValue) {
        _userMessage.value = value
        val chatId = activeDraftChatId
        if (chatId != null) {
            saveUserMessageDraft(chatId, value)
        }
    }

    private fun saveUserMessageDraft(chatId: String, value: TextFieldValue) {
        if (value.text.isEmpty()) {
            userMessageDraftsByChatId.remove(chatId)
            return
        }

        userMessageDraftsByChatId[chatId] = value
    }

    private fun clearUserMessageDraft(chatId: String, expectedText: String? = null) {
        val currentDraft =
            if (activeDraftChatId == chatId || activeDraftChatId == null) {
                _userMessage.value
            } else {
                userMessageDraftsByChatId[chatId]
            }
        if (expectedText != null && !shouldConsumeUserDraft(currentDraft?.text, expectedText)) {
            return
        }
        userMessageDraftsByChatId.remove(chatId)
        if (activeDraftChatId == chatId || activeDraftChatId == null) {
            _userMessage.value = TextFieldValue("")
        }
    }

    fun consumeUserMessageDraft(chatId: String, expectedText: String) {
        clearUserMessageDraft(chatId = chatId, expectedText = expectedText)
    }

    fun scrollToBottom() {
        _scrollToBottomEvent.tryEmit(Unit)
    }

    fun getTurnCompleteCounter(chatId: String): Long {
        return _turnCompleteCounterByChatId.value[chatId] ?: 0L
    }

    fun isChatLoading(chatId: String): Boolean {
        val runtime = runtimeFor(chatId)
        return isChatRuntimeBusy(runtime.isLoading.value, runtime.isCancelling)
    }

    fun setSpeakMessageHandler(handler: suspend (String, Boolean) -> Unit) {
        speakMessageHandler = handler
    }

    private fun resetCurrentTurnToolInvocationCount(chatId: String) {
        _currentTurnToolInvocationCountByChatId.update { current ->
            current.toMutableMap().apply { this[chatId] = 0 }
        }
    }

    private fun incrementCurrentTurnToolInvocationCount(chatId: String) {
        _currentTurnToolInvocationCountByChatId.update { current ->
            current.toMutableMap().apply { this[chatId] = (this[chatId] ?: 0) + 1 }
        }
    }

    private fun clearCurrentTurnToolInvocationCount(chatId: String) {
        _currentTurnToolInvocationCountByChatId.update { current ->
            current.toMutableMap().apply { remove(chatId) }
        }
    }

    fun sendUserMessage(
            attachments: List<AttachmentInfo> = emptyList(),
            chatId: String,
            messageTextOverride: String? = null,
            proxySenderNameOverride: String? = null,
            workspacePath: String? = null,
            workspaceEnv: String? = null,
            promptFunctionType: PromptFunctionType = PromptFunctionType.CHAT,
            roleCardId: String,
            enableThinking: Boolean = false,
            enableMemoryAutoUpdate: Boolean = true,
            maxTokens: Int,
            tokenUsageThreshold: Double,
            replyToMessage: ChatMessage? = null, // 新增回复消息参数
            isAutoContinuation: Boolean = false, // 标识是否为自动续写
            enableSummary: Boolean = true,
            chatModelConfigIdOverride: String? = null,
            chatModelIndexOverride: Int? = null,
            preferenceProfileIdOverride: String? = null,
            suppressUserMessageInHistory: Boolean = false,
            historyMessageTimestampsToExclude: Set<Long> = emptySet(),
            isGroupOrchestrationTurn: Boolean = false,
            groupParticipantNamesText: String? = null,
            onUserMessagePersisted: (() -> Unit)? = null,
            turnOptions: ChatTurnOptions = ChatTurnOptions()
    ): Boolean {
        val rawMessageText = messageTextOverride ?: _userMessage.value.text
        // 群组编排模式下，允许空消息（后续成员不需要用户消息）
        if (rawMessageText.isBlank() && attachments.isEmpty() && !isAutoContinuation && !isGroupOrchestrationTurn) {
            AppLogger.d(
                TAG,
                "sendUserMessage忽略: 空消息且无附件, chatId=$chatId, autoContinuation=$isAutoContinuation"
            )
            return false
        }
        val chatRuntime = runtimeFor(chatId)

        // This is part of dispatch acceptance, so keep it synchronous. Callers use the return value
        // to decide whether drafts, attachments, and delayed immersive batches may be consumed.
        val chatScopedService = EnhancedAIService.getChatInstance(context, chatId)
        val service = chatScopedService ?: getEnhancedAiService()
        if (service == null) {
            showErrorMessage(context.getString(R.string.message_ai_service_not_initialized))
            AppLogger.w(TAG, "sendUserMessage rejected: AI service unavailable, chatId=$chatId")
            return false
        }

        val originalMessageText = rawMessageText.trim()
        var messageText = originalMessageText
        
        val consumeUserDraftAfterPersist =
            turnOptions.consumeUserDraftAfterPersist || messageTextOverride == null
        val sendGeneration =
            synchronized(chatRuntime) {
                if (isChatRuntimeBusy(chatRuntime.isLoading.value, chatRuntime.isCancelling)) {
                    null
                } else {
                    chatRuntime.autoReadJob?.cancel()
                    chatRuntime.autoReadJob = null
                    chatRuntime.responseStream = null
                    chatRuntime.isLoading.value = true
                    chatRuntime.currentTurnOptions = turnOptions
                    chatRuntime.preservePartialResponseOnCancellation = true
                    chatRuntime.sendGeneration.incrementAndGet()
                }
            }
        if (sendGeneration == null) {
            showErrorMessage(context.getString(R.string.chat_regenerate_busy))
            AppLogger.w(
                TAG,
                "sendUserMessage忽略: chat正在处理中, chatId=$chatId, roleCardId=$roleCardId, override=${!messageTextOverride.isNullOrBlank()}, suppressUserMessageInHistory=$suppressUserMessageInHistory"
            )
            return false
        }
        ChatSkillActivationStore.getInstance(context).clear(chatId)
        resetCurrentTurnToolInvocationCount(chatId)
        updateGlobalLoadingState()
        setChatInputProcessingState(chatId, EnhancedInputProcessingState.Processing(context.getString(R.string.message_processing)))

        val sendJob =
            coroutineScope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
            try {
            val sendUserMessageStartTime = messageTimingNow()
            val effectivePersistTurn = turnOptions.persistTurn
            val effectiveHideUserMessage = effectivePersistTurn && turnOptions.hideUserMessage
            // Freeze ownership before persistence and network work so a role switch cannot retarget this turn.
            val memoryCompanionId =
                CompanionMemoryTargetResolver.snapshotForTurn(
                    characterGroupId =
                        AppDatabase.getDatabase(context.applicationContext)
                            .chatDao()
                            .getChatById(chatId)
                            ?.characterGroupId,
                    roleCardId = roleCardId,
                )

            val acquireServiceStartTime = messageTimingNow()
            logMessageTiming(
                stage = "delegate.acquireService",
                startTimeMs = acquireServiceStartTime,
                details = "chatId=$chatId, reusedChatInstance=${chatScopedService != null}"
            )

            // 检查这是否是聊天中的第一条用户消息（忽略AI的开场白）
            val isFirstMessage = !hasUserMessage(chatId)
            val titleFallback = if (
                effectivePersistTurn &&
                    !effectiveHideUserMessage &&
                    isFirstMessage &&
                    chatId != null
            ) {
                fallbackConversationTitle(originalMessageText, attachments).also { fallbackTitle ->
                    updateChatTitle(chatId, fallbackTitle)
                }
            } else {
                null
            }

            AppLogger.d(TAG, "开始处理用户消息：附件数量=${attachments.size}")

            // 获取当前模型配置以检查是否启用直接图片处理
            val configId = chatModelConfigIdOverride?.takeIf { it.isNotBlank() }
                ?: functionalConfigManager.getConfigIdForFunction(FunctionType.CHAT)
            val loadModelConfigStartTime = messageTimingNow()
            val currentModelConfig = modelConfigManager.getModelConfigFlow(configId).first()
            val enableDirectImageProcessing = currentModelConfig.enableDirectImageProcessing
            val enableDirectAudioProcessing = currentModelConfig.enableDirectAudioProcessing
            val enableDirectVideoProcessing = currentModelConfig.enableDirectVideoProcessing
            AppLogger.d(TAG, "直接图片处理状态: $enableDirectImageProcessing (配置ID: $configId)")
            logMessageTiming(
                stage = "delegate.loadModelConfig",
                startTimeMs = loadModelConfigStartTime,
                details = "chatId=$chatId, configId=$configId"
            )

            // 1. 使用 AIMessageManager 构建最终消息
            val buildUserMessageStartTime = messageTimingNow()
            val finalMessageContent = AIMessageManager.buildUserMessageContent(
                context = context,
                messageText = messageText,
                proxySenderName = proxySenderNameOverride,
                attachments = attachments,
                workspacePath = workspacePath,
                workspaceEnv = workspaceEnv,
                replyToMessage = replyToMessage,
                enableDirectImageProcessing = enableDirectImageProcessing,
                enableDirectAudioProcessing = enableDirectAudioProcessing,
                enableDirectVideoProcessing = enableDirectVideoProcessing,
                chatId = chatId,
                roleCardId = roleCardId
            )
            logMessageTiming(
                stage = "delegate.buildUserMessageContent",
                startTimeMs = buildUserMessageStartTime,
                details = "chatId=$chatId, attachments=${attachments.size}, finalLength=${finalMessageContent.length}"
            )

            // 自动继续且原本消息为空时，不添加到聊天历史（虽然会发送"继续"给AI）
            // 群组编排模式下，空消息也不添加到聊天历史
            val shouldAddUserMessageToChat =
                effectivePersistTurn &&
                !suppressUserMessageInHistory &&
                !(isAutoContinuation &&
                        originalMessageText.isBlank() &&
                        attachments.isEmpty()) &&
                !(isGroupOrchestrationTurn &&
                        originalMessageText.isBlank() &&
                        attachments.isEmpty())
            var userMessageAdded = false
            var userMessage = ChatMessage(
                sender = "user",
                content = finalMessageContent,
                roleName = context.getString(R.string.message_role_user), // 用户消息的角色名固定为"用户"
                displayMode =
                    if (effectiveHideUserMessage) {
                        ChatMessageDisplayMode.HIDDEN_PLACEHOLDER
                    } else {
                        ChatMessageDisplayMode.NORMAL
                    }
            )

            val toolHandler = AIToolHandler.getInstance(context)
            var workspaceToolHookSession: WorkspaceBackupManager.WorkspaceToolHookSession? = null

            // 在消息发送期间临时挂载 workspace hook，结束后卸载
            if (!workspacePath.isNullOrBlank()) {
                val attachWorkspaceHookStartTime = messageTimingNow()
                try {
                    val session =
                        WorkspaceBackupManager.getInstance(context)
                            .createWorkspaceToolHookSession(
                                workspacePath = workspacePath,
                                workspaceEnv = workspaceEnv,
                                messageTimestamp = userMessage.timestamp,
                                chatId = chatId
                            )
                    workspaceToolHookSession = session
                    toolHandler.addToolHook(session)
                    AppLogger.d(
                        TAG,
                        "Workspace hook attached for timestamp=${userMessage.timestamp}, path=$workspacePath"
                    )
                    logMessageTiming(
                        stage = "delegate.attachWorkspaceHook",
                        startTimeMs = attachWorkspaceHookStartTime,
                        details = "chatId=$chatId, workspacePath=$workspacePath"
                    )
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Failed to attach workspace hook", e)
                    _nonFatalErrorEvent.emit(context.getString(R.string.message_workspace_sync_failed, e.message))
                }
            }

            if (shouldAddUserMessageToChat && chatId != null) {
                // 等待消息添加到聊天历史完成，确保getChatHistory()包含新消息
                val addUserMessageStartTime = messageTimingNow()
                addMessageToChat(chatId, userMessage)
                userMessageAdded = true
                val isExplicitMemoryRequest =
                    CompanionMemoryImportanceDetector.isExplicitSaveRequest(originalMessageText)
                if (
                    isExplicitMemoryRequest ||
                        (enableMemoryAutoUpdate && CompanionMemoryImportanceDetector.isHighValue(originalMessageText))
                ) {
                    val memoryEnqueueError =
                        isolateMemoryEnqueueFailure {
                            val profileId =
                                preferenceProfileIdOverride?.takeIf { it.isNotBlank() }
                                    ?: UserPreferencesManager.getInstance(context).activeProfileIdFlow.first()
                            MemoryAutoSaveCandidateRepository(context, profileId).enqueue(
                                chatId = chatId,
                                companionId = memoryCompanionId,
                                triggerMessageTimestamp = userMessage.timestamp,
                                sourceType =
                                    if (isExplicitMemoryRequest) {
                                        MemoryAutoSaveCandidate.SOURCE_TYPE_EXPLICIT_USER
                                    } else {
                                        MemoryAutoSaveCandidate.SOURCE_TYPE_HIGH_VALUE_AUTO
                                    },
                            )
                            val memoryScheduler = MemoryAutoSaveScheduler.getInstance()
                            if (memoryScheduler != null) {
                                memoryScheduler.requestImmediateProcessing(profileId, chatId)
                            } else {
                                MemoryAutoSaveWorkScheduler.requestImmediate(context, profileId, chatId)
                            }
                        }
                    if (memoryEnqueueError != null) {
                        AppLogger.e(
                            TAG,
                            "Memory candidate enqueue failed without interrupting chat dispatch: chatId=$chatId",
                            memoryEnqueueError,
                        )
                    }
                }
                if (consumeUserDraftAfterPersist) {
                    clearUserMessageDraft(chatId, expectedText = rawMessageText)
                }
                onUserMessagePersisted?.let { callback ->
                    runCatching(callback).onFailure { error ->
                        AppLogger.w(TAG, "Composer cleanup failed after persistence: chatId=$chatId", error)
                    }
                }
                logMessageTiming(
                    stage = "delegate.addUserMessageToChat",
                    startTimeMs = addUserMessageStartTime,
                    details = "chatId=$chatId, contentLength=${userMessage.content.length}"
                )
                titleFallback?.let { fallbackTitle ->
                    launchConversationTitleGeneration(
                        chatId = chatId,
                        userText = originalMessageText,
                        attachments = attachments,
                        fallbackTitle = fallbackTitle
                    )
                }
            }

            lateinit var aiMessage: ChatMessage
            val activeChatId = chatId
            var serviceForTurnComplete: EnhancedAIService? = null
            var shouldNotifyTurnComplete = false
            var finalInputStateAfterSend: EnhancedInputProcessingState? = null
            var isWaifuModeEnabled = false
            var didStreamAutoRead = false
            val effectiveRoleCardId = roleCardId
            val waifuEmittedSegments = mutableListOf<String>()
            var waifuCombinedMessage: ChatMessage? = null
            val waifuAutoReadBuffer = TtsNaturalBlockBuffer()
            var isFirstWaifuAutoReadBlock = true
            var syncWaifuMessageMetricsHandler: (suspend (ChatMessage) -> Unit)? = null
            var requestSentAt = 0L
            var requestStartElapsed = 0L
            var firstResponseElapsed: Long? = null
            var turnInputTokens = 0
            var turnOutputTokens = 0
            var turnCachedInputTokens = 0
            var calculateNextWindowSize: (suspend () -> Int?)? = null
            var cancellationToPropagate: kotlinx.coroutines.CancellationException? = null
            try {
                // if (!NetworkUtils.isNetworkAvailable(context)) {
                //     withContext(Dispatchers.Main) { showErrorMessage("网络连接不可用") }
                //     _isLoading.value = false
                //     setChatInputProcessingState(activeChatId, EnhancedInputProcessingState.Idle)
                //     return@launch
                // }

                serviceForTurnComplete = service

                // 清除上一次可能残留的 Error 状态，避免 StateFlow 重放导致新一轮发送立即再次触发弹窗
                service.setInputProcessingState(EnhancedInputProcessingState.Processing(context.getString(R.string.message_processing)))

                // 监听此 chat 对应的 EnhancedAIService 状态，映射到 per-chat state
                chatRuntime.stateCollectionJob?.cancel()
                chatRuntime.stateCollectionJob =
                    coroutineScope.launch {
                        var lastErrorMessage: String? = null
                        service.inputProcessingState.collect { state ->
                            setChatInputProcessingState(activeChatId, state)

                            if (state is EnhancedInputProcessingState.Error) {
                                val msg = state.message
                                if (msg != lastErrorMessage) {
                                    lastErrorMessage = msg
                                    withContext(Dispatchers.Main) {
                                        showErrorMessage(msg)
                                    }
                                }
                            } else {
                                lastErrorMessage = null
                            }
                        }
                    }

                val responseStartTime = messageTimingNow()

                val userPreferencesManager = UserPreferencesManager.getInstance(context)

                // 获取角色信息用于通知
                val loadRoleInfoStartTime = messageTimingNow()
                val (characterName, avatarUri) = try {
                    val roleCard = characterCardManager.getCharacterCardFlow(effectiveRoleCardId).first()
                    val avatar =
                        userPreferencesManager.getAiAvatarForCharacterCardFlow(roleCard.id).first()
                    Pair(roleCard.name, avatar)
                } catch (e: Exception) {
                    AppLogger.e(TAG, "获取角色信息失败: ${e.message}", e)
                    Pair(null, null)
                }
                val currentRoleName = characterName ?: context.getString(R.string.app_name)
                logMessageTiming(
                    stage = "delegate.loadRoleInfo",
                    startTimeMs = loadRoleInfoStartTime,
                    details = "chatId=$activeChatId, roleCardId=$effectiveRoleCardId, roleName=$currentRoleName"
                )
                calculateNextWindowSize = {
                    runCatching {
                        AIMessageManager.calculateStableContextWindow(
                            enhancedAiService = service,
                            chatId = activeChatId,
                            messageContent = "",
                            chatHistory = getRuntimeChatHistory(activeChatId),
                            workspacePath = workspacePath,
                            workspaceEnv = workspaceEnv,
                            promptFunctionType = promptFunctionType,
                            roleCardId = effectiveRoleCardId,
                            currentRoleName = currentRoleName,
                            splitHistoryByRole = true,
                            groupOrchestrationMode = isGroupOrchestrationTurn,
                            groupParticipantNamesText = groupParticipantNamesText,
                            chatModelConfigIdOverride = chatModelConfigIdOverride,
                            chatModelIndexOverride = chatModelIndexOverride,
                            preferenceProfileIdOverride = preferenceProfileIdOverride,
                            publishEstimate = false
                        )
                    }.onFailure {
                        AppLogger.w(TAG, "回合结束后重算上下文窗口失败", it)
                    }.getOrNull()
                }

                val loadChatHistoryStartTime = messageTimingNow()
                val fullChatHistory = getRuntimeChatHistory(activeChatId)
                val chatHistory =
                    chatHistoryForDispatch(
                        history = fullChatHistory,
                        excludedMessageTimestamps = historyMessageTimestampsToExclude,
                    )
                logMessageTiming(
                    stage = "delegate.loadChatHistory",
                    startTimeMs = loadChatHistoryStartTime,
                    details =
                        "chatId=$activeChatId, size=${chatHistory.size}, " +
                            "excluded=${fullChatHistory.size - chatHistory.size}"
                )

                // 关闭总结时仍保留真实 limits，避免下游插件收到 0/Infinity 这类无效 JSON 值。
                val effectiveMaxTokens = maxTokens
                val effectiveEnableSummary = enableSummary && effectivePersistTurn
                val effectiveTokenUsageThreshold =
                    if (effectiveEnableSummary) tokenUsageThreshold else Double.MAX_VALUE
                val effectiveOnTokenLimitExceeded = if (effectiveEnableSummary) {
                    suspend {
                        onTokenLimitExceeded(
                            activeChatId,
                            effectiveRoleCardId,
                            isGroupOrchestrationTurn,
                            groupParticipantNamesText
                        )
                    }
                } else {
                    null
                }

                // 2. 使用 AIMessageManager 发送消息
                // 群组编排模式下，只有当消息内容不为空时才添加 [From user] 前缀
                val roleScopedRequestMessageContent =
                    if (isGroupOrchestrationTurn &&
                        finalMessageContent.trimStart().isNotEmpty() &&
                        !finalMessageContent.trimStart().startsWith("[From user]")
                    ) {
                        "[From user]\n$finalMessageContent"
                    } else {
                        finalMessageContent
                    }
                val requestMessageContent =
                    if (turnOptions.requireToolExecution) {
                        """<device_action_requirement>
Execute the requested device action with an available tool. Do not claim success unless a tool result confirms it. If no suitable tool is available or execution fails, state that the operation was not completed.
</device_action_requirement>
$roleScopedRequestMessageContent"""
                    } else {
                        roleScopedRequestMessageContent
                    }

                requestSentAt = System.currentTimeMillis()
                requestStartElapsed = messageTimingNow()
                chatRuntime.requestSentAt = requestSentAt
                chatRuntime.requestStartElapsed = requestStartElapsed
                chatRuntime.firstResponseElapsed = null
                if (userMessageAdded && chatId != null) {
                    userMessage = userMessage.copy(sentAt = requestSentAt)
                    addMessageToChat(chatId, userMessage)
                }

                val prepareResponseStreamStartTime = messageTimingNow()
                val responseStream = AIMessageManager.sendMessage(
                    enhancedAiService = service,
                    chatId = activeChatId,
                    messageContent = requestMessageContent,
                    currentRequestTimestamp = userMessage.timestamp,
                    // 仅在群组编排中去掉当前用户消息，避免重复拼接。
                    chatHistory = if (isGroupOrchestrationTurn && userMessageAdded && chatHistory.isNotEmpty()) {
                        chatHistory.subList(0, chatHistory.size - 1)
                    } else {
                        chatHistory
                    },
                    workspacePath = workspacePath,
                    promptFunctionType = promptFunctionType,
                    enableThinking = enableThinking,
                    enableMemoryAutoUpdate = enableMemoryAutoUpdate,
                    maxTokens = effectiveMaxTokens,
                    tokenUsageThreshold = effectiveTokenUsageThreshold,
                    onNonFatalError = { error ->
                        _nonFatalErrorEvent.emit(error)
                    },
                    onTokenLimitExceeded = effectiveOnTokenLimitExceeded,
                    characterName = characterName,
                    avatarUri = avatarUri,
                    roleCardId = effectiveRoleCardId,
                    memoryCompanionId = memoryCompanionId,
                    currentRoleName = currentRoleName,
                    splitHistoryByRole = true,
                    groupOrchestrationMode = isGroupOrchestrationTurn,
                    groupParticipantNamesText = groupParticipantNamesText,
                    proxySenderName = proxySenderNameOverride,
                    onToolInvocation = {
                        incrementCurrentTurnToolInvocationCount(chatId)
                    },
                    notifyReplyOverride = turnOptions.notifyReply,
                    chatModelConfigIdOverride = chatModelConfigIdOverride,
                    chatModelIndexOverride = chatModelIndexOverride,
                    preferenceProfileIdOverride = preferenceProfileIdOverride,
                    disableWarning = turnOptions.disableWarning,
                    toolsEnabledOverride = turnOptions.toolsEnabledOverride,
                )
                logMessageTiming(
                    stage = "delegate.prepareResponseStream",
                    startTimeMs = prepareResponseStreamStartTime,
                    details = "chatId=$activeChatId, requestLength=${requestMessageContent.length}, history=${chatHistory.size}"
                )

                // AIMessageManager 已返回可重放的共享流，这里直接复用，避免在 viewModelScope 上再包一层。
                val sharedCharStream = responseStream

                // 更新当前响应流，使其可以被其他组件（如悬浮窗）访问
                chatRuntime.responseStream = sharedCharStream

                // 获取当前使用的provider和model信息
                val loadProviderModelStartTime = messageTimingNow()
                val (provider, modelName) = try {
                    service.getDisplayProviderAndModelForFunction(
                        functionType = com.ai.assistance.operit.data.model.FunctionType.CHAT,
                        chatModelConfigIdOverride = chatModelConfigIdOverride,
                        chatModelIndexOverride = chatModelIndexOverride
                    )
                } catch (e: Exception) {
                    AppLogger.e(TAG, "获取provider和model信息失败: ${e.message}", e)
                    Pair("", "")
                }
                logMessageTiming(
                    stage = "delegate.loadProviderModel",
                    startTimeMs = loadProviderModelStartTime,
                    details = "chatId=$activeChatId, provider=$provider, model=$modelName"
                )

                aiMessage = ChatMessage(
                    sender = "ai", 
                    contentStream = sharedCharStream,
                    timestamp = ChatMessageTimestampAllocator.next(),
                    roleName = currentRoleName,
                    provider = provider,
                    modelName = modelName,
                    sentAt = requestSentAt
                )
                AppLogger.d(
                    TAG,
                    "创建带流的AI消息, stream is null: ${aiMessage.contentStream == null}, timestamp: ${aiMessage.timestamp}"
                )

                // 检查是否启用waifu模式来决定是否显示流式过程
                val waifuPreferences = WaifuPreferences.getInstance(context)
                isWaifuModeEnabled = waifuPreferences.enableWaifuModeFlow.first()
                val waifuCharDelay = waifuPreferences.waifuCharDelayFlow.first()
                val waifuRemovePunctuation =
                    if (isWaifuModeEnabled) {
                        waifuPreferences.waifuRemovePunctuationFlow.first()
                    } else {
                        false
                    }

                suspend fun emitWaifuSegment(segment: String) {
                    if (!ConversationContentVisibility.hasRenderableAssistantContent(segment)) return

                    val speechBlocks = mutableListOf<Pair<String, Boolean>>()
                    withContext(Dispatchers.Main) {
                        waifuEmittedSegments += segment
                        val previous = waifuCombinedMessage
                        val combined =
                            (previous
                                ?: ChatMessage(
                                    sender = "ai",
                                    content = "",
                                    contentStream = null,
                                    timestamp = ChatMessageTimestampAllocator.next(),
                                    roleName = currentRoleName,
                                    provider = provider,
                                    modelName = modelName,
                                    sentAt = requestSentAt,
                                    displayMode = ChatMessageDisplayMode.IMMERSIVE_TURN,
                                )).copy(
                                content =
                                    WaifuMessageProcessor.appendSegmentToReply(
                                        current = previous?.content.orEmpty(),
                                        segment = segment,
                                    ),
                                contentStream = null,
                            )
                        waifuCombinedMessage = combined
                        if (effectivePersistTurn && chatId != null) {
                            addMessageToChat(chatId, combined)
                        }
                        if (isAutoReadEnabledFor(turnOptions)) {
                            waifuAutoReadBuffer.append(segment).forEach { block ->
                                didStreamAutoRead = true
                                val interrupt = isFirstWaifuAutoReadBlock
                                AppLogger.d(
                                    TAG,
                                    "autoRead[waifuBlock] interrupt=$interrupt len=${block.length} preview=\"${speechPreview(block)}\""
                                )
                                speechBlocks += block to interrupt
                                isFirstWaifuAutoReadBlock = false
                            }
                        } else {
                            waifuAutoReadBuffer.clear()
                        }
                        tryEmitScrollToBottomThrottled(chatId)
                    }

                    // Publish the text before waiting for its matching audio. Keeping the
                    // playback wait inside the UI update made immersive replies appear only
                    // after TTS had completed on some devices.
                    if (speechBlocks.isNotEmpty()) {
                        delay(TEXT_PUBLISH_BEFORE_SPEECH_MS)
                    }
                    speechBlocks.forEach { (block, interrupt) ->
                        speakMessageHandler(block, interrupt)
                    }
                }

                suspend fun syncWaifuMessageMetrics(sourceMessage: ChatMessage) {
                    if (!effectivePersistTurn || chatId == null) return

                    // Immersive mode may split visible prose for pacing, but the persisted
                    // message must retain tool calls/results and status blocks for inspection.
                    val preparedContent = SpeechContentMetadata.prepare(sourceMessage.content)
                    withContext(Dispatchers.Main) {
                        val current = waifuCombinedMessage
                        if (
                            current == null &&
                                !ConversationContentVisibility.hasRenderableAssistantContent(
                                    preparedContent.visibleText,
                                )
                        ) {
                            AppLogger.w(
                                TAG,
                                "Skipping blank immersive assistant message: chatId=$chatId rawLen=${sourceMessage.content.length}",
                            )
                            return@withContext
                        }
                        val baseMessage =
                            current
                                ?: sourceMessage.copy(
                                    content = "",
                                    contentStream = null,
                                    displayMode = ChatMessageDisplayMode.IMMERSIVE_TURN,
                                )
                        val updatedMessage =
                            baseMessage.copy(
                                content =
                                    preparedContent.visibleText.takeIf {
                                        ConversationContentVisibility.hasRenderableAssistantContent(it)
                                    } ?: baseMessage.content,
                                inputTokens = sourceMessage.inputTokens,
                                outputTokens = sourceMessage.outputTokens,
                                cachedInputTokens = sourceMessage.cachedInputTokens,
                                sentAt = sourceMessage.sentAt,
                                outputDurationMs = sourceMessage.outputDurationMs,
                                waitDurationMs = sourceMessage.waitDurationMs,
                                completedAt = sourceMessage.completedAt,
                                speechDirectionJson =
                                    sourceMessage.speechDirectionJson
                                        ?: preparedContent.speechDirectionJson,
                                contentStream = null,
                            )
                        waifuCombinedMessage = updatedMessage
                        addMessageToChat(chatId, updatedMessage)
                    }
                }
                syncWaifuMessageMetricsHandler = { sourceMessage ->
                    syncWaifuMessageMetrics(sourceMessage)
                }
                
                // 启动一个独立的协程来收集流内容并持续更新数据库
                val streamCollectionResult = CompletableDeferred<Throwable?>()
                val firstResponseSignal = CompletableDeferred<Boolean>()
                chatRuntime.streamCollectionJob =
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            var hasLoggedFirstChunk = false
                            var lastStreamingPersistAt = 0L
                            val revisionTracker = TextStreamRevisionTracker()
                            val revisionMutex = Mutex()
                            val autoReadBuffer = StringBuilder()
                            var isFirstAutoReadSegment = true
                            val revisableStream = sharedCharStream as? TextStreamEventCarrier
                            // Provider retries can retract text, but audio and immersive segments
                            // are irreversible. Defer those side effects until revised text is final.
                            val canStreamProvisionalOutput =
                                shouldStreamProvisionalAssistantOutput(
                                    hasRevisionEvents = revisableStream != null,
                                )
                            val autoReadStream =
                                if (
                                    !isWaifuModeEnabled &&
                                        isAutoReadEnabledFor(turnOptions) &&
                                        canStreamProvisionalOutput
                                ) {
                                    WaifuMessageProcessor.streamTtsText(sharedCharStream)
                                } else {
                                    null
                                }

                            suspend fun flushAutoReadSegment(segment: String, interrupt: Boolean) {
                                val trimmed = segment.trim()
                                if (trimmed.isNotEmpty()) {
                                    AppLogger.d(
                                        TAG,
                                        "autoRead[flush] interrupt=$interrupt len=${trimmed.length} preview=\"${speechPreview(trimmed)}\""
                                    )
                                    delay(TEXT_PUBLISH_BEFORE_SPEECH_MS)
                                    speakMessageHandler(trimmed, interrupt)
                                    didStreamAutoRead = true
                                } else if (segment.isNotEmpty()) {
                                    AppLogger.d(
                                        TAG,
                                        "autoRead[flush.skipBlank] rawLen=${segment.length}"
                                    )
                                }
                            }

                            suspend fun tryFlushAutoRead() {
                                if (!isAutoReadEnabledFor(turnOptions)) return
                                if (isWaifuModeEnabled) return
                                while (true) {
                                    val bufferBefore = autoReadBuffer.length
                                    val cutIdx = TtsSegmenter.nextSegmentEnd(autoReadBuffer)
                                    if (cutIdx < 0) return

                                    val seg = autoReadBuffer.substring(0, cutIdx)
                                    autoReadBuffer.delete(0, cutIdx)
                                    AppLogger.d(
                                        TAG,
                                        "autoRead[cut] cutIdx=$cutIdx bufferBefore=$bufferBefore bufferAfter=${autoReadBuffer.length} firstSegment=$isFirstAutoReadSegment rawLen=${seg.length} preview=\"${speechPreview(seg)}\""
                                    )

                                    flushAutoReadSegment(seg, interrupt = isFirstAutoReadSegment)
                                    isFirstAutoReadSegment = false
                                }
                            }

                            suspend fun persistStreamingSnapshot(
                                contentSnapshot: String,
                                force: Boolean = false
                            ) {
                                if (!effectivePersistTurn || isWaifuModeEnabled || chatId == null) return
                                if (contentSnapshot.isEmpty()) return
                                if (!ConversationContentVisibility.hasRenderableAssistantContent(contentSnapshot)) {
                                    AppLogger.d(
                                        TAG,
                                        "Skipping non-renderable streaming snapshot: chatId=$chatId rawLen=${contentSnapshot.length}",
                                    )
                                    return
                                }
                                val now = messageTimingNow()
                                if (!force && now - lastStreamingPersistAt < STREAM_PERSIST_INTERVAL_MS) {
                                    return
                                }

                                addMessageToChat(chatId, aiMessage.copy(content = contentSnapshot))
                                lastStreamingPersistAt = now
                            }

                            autoReadStream?.let { stream ->
                                // TTS consumes the replayable response independently. Message
                                // finalization must never wait for audio playback to complete.
                                val job =
                                    coroutineScope.launch(
                                        Dispatchers.IO,
                                        start = CoroutineStart.LAZY,
                                    ) {
                                        val currentJob = coroutineContext[Job]
                                        try {
                                            stream.collect { char ->
                                                autoReadBuffer.append(char)
                                                tryFlushAutoRead()
                                            }
                                            val remaining = autoReadBuffer.toString()
                                            autoReadBuffer.clear()
                                            AppLogger.d(
                                                TAG,
                                                "autoRead[remaining] firstSegment=$isFirstAutoReadSegment rawLen=${remaining.length} trimmedLen=${remaining.trim().length} preview=\"${speechPreview(remaining)}\""
                                            )
                                            if (isAutoReadEnabledFor(turnOptions)) {
                                                flushAutoReadSegment(
                                                    remaining,
                                                    interrupt = isFirstAutoReadSegment,
                                                )
                                            }
                                        } finally {
                                            if (chatRuntime.autoReadJob === currentJob) {
                                                chatRuntime.autoReadJob = null
                                            }
                                        }
                                    }
                                chatRuntime.autoReadJob = job
                                job.start()
                            }
                            val waifuSegmentsJob =
                                if (isWaifuModeEnabled && canStreamProvisionalOutput) {
                                    launch {
                                        WaifuMessageProcessor.streamSegmentsWithTypingQueue(
                                            sourceStream = sharedCharStream,
                                            removePunctuation = waifuRemovePunctuation,
                                            charDelayMs = waifuCharDelay,
                                            synchronizeWithSpeech = isAutoReadEnabledFor(turnOptions),
                                        ).collect { segment ->
                                            emitWaifuSegment(segment)
                                        }
                                    }
                                } else {
                                    null
                                }

                            val revisionJob =
                                revisableStream?.let { carrier ->
                                    launch {
                                        carrier.eventChannel.collect { event ->
                                            when (event.eventType) {
                                                TextStreamEventType.SAVEPOINT -> {
                                                    revisionMutex.withLock {
                                                        revisionTracker.savepoint(event.id)
                                                    }
                                                }

                                                TextStreamEventType.ROLLBACK -> {
                                                    val snapshot =
                                                        revisionMutex.withLock {
                                                            revisionTracker.rollback(event.id)
                                                        } ?: return@collect

                                                    aiMessage.content = snapshot

                                                    if (!isWaifuModeEnabled) {
                                                        persistStreamingSnapshot(snapshot)
                                                        tryEmitScrollToBottomThrottled(chatId)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                            sharedCharStream.collect { chunk ->
                                if (chunk.isNotEmpty() && !firstResponseSignal.isCompleted) {
                                    firstResponseSignal.complete(true)
                                }
                                if (!hasLoggedFirstChunk) {
                                    hasLoggedFirstChunk = true
                                    if (firstResponseElapsed == null) {
                                        firstResponseElapsed = messageTimingNow()
                                        chatRuntime.firstResponseElapsed = firstResponseElapsed
                                    }
                                    logMessageTiming(
                                        stage = "delegate.firstResponseChunk",
                                        startTimeMs = responseStartTime,
                                        details = "chatId=$activeChatId, firstChunkLength=${chunk.length}"
                                    )
                                }
                                val content =
                                    revisionMutex.withLock {
                                        revisionTracker.append(chunk)
                                    }
                                // 防止后续读取不到
                                aiMessage.content = content
                                
                                // 流式内容由 contentStream 实时渲染，这里仅按固定间隔同步快照，避免碎片 chunk 导致高频持久化。
                                persistStreamingSnapshot(content)
                                if (!isWaifuModeEnabled) {
                                    tryEmitScrollToBottomThrottled(chatId)
                                }
                            }

                            revisionJob?.cancelAndJoin()
                            waifuSegmentsJob?.join()

                            if (isWaifuModeEnabled && aiMessage.content.isNotBlank()) {
                                val emittedSnapshot =
                                    withContext(Dispatchers.Main) {
                                        waifuEmittedSegments.toList()
                                    }
                                val missingSegments =
                                    WaifuMessageProcessor.findMissingFinalSegments(
                                        finalContent = aiMessage.content,
                                        emittedSegments = emittedSnapshot,
                                        removePunctuation = waifuRemovePunctuation,
                                    )
                                if (missingSegments.isNotEmpty()) {
                                    AppLogger.w(
                                        TAG,
                                        "waifu stream reconciliation appended ${missingSegments.size} missing segment(s)"
                                    )
                                    missingSegments.forEach { segment -> emitWaifuSegment(segment) }
                                }
                            }

                            if (isAutoReadEnabledFor(turnOptions) && isWaifuModeEnabled) {
                                waifuAutoReadBuffer.flush()?.let { block ->
                                    didStreamAutoRead = true
                                    AppLogger.d(
                                        TAG,
                                        "autoRead[waifuRemaining] interrupt=$isFirstWaifuAutoReadBlock len=${block.length} preview=\"${speechPreview(block)}\""
                                    )
                                    speakMessageHandler(block, isFirstWaifuAutoReadBlock)
                                    isFirstWaifuAutoReadBlock = false
                                }
                            }

                        } catch (t: Throwable) {
                            if (!streamCollectionResult.isCompleted) {
                                streamCollectionResult.complete(t)
                            }
                            // This collector is launched from the shared chat scope rather than
                            // the send job. Re-throwing here cancels that scope and takes down the
                            // whole app before sendUserMessage can render a recoverable error.
                            if (t !is kotlinx.coroutines.CancellationException) {
                                AppLogger.e(TAG, "响应流收集失败，交由发送协程处理", t)
                            }
                        } finally {
                            if (!firstResponseSignal.isCompleted) {
                                firstResponseSignal.complete(false)
                            }
                            if (!streamCollectionResult.isCompleted) {
                                streamCollectionResult.complete(null)
                            }
                        }
                    }

                val firstResponseObserved =
                    withTimeoutOrNull(FIRST_RESPONSE_TIMEOUT_MS) {
                        firstResponseSignal.await()
                    }
                if (firstResponseObserved == null) {
                    AppLogger.w(
                        TAG,
                        "First assistant response timed out: chatId=$activeChatId, timeoutMs=$FIRST_RESPONSE_TIMEOUT_MS"
                    )
                    // Stop the producer as well as the collector; otherwise a timed-out turn can
                    // keep the chat-scoped service busy and block the user's next message.
                    val operationCancellation = AIMessageManager.cancelOperation(activeChatId)
                    chatRuntime.streamCollectionJob?.let { job -> cancelAndJoinJobs(listOf(job)) }
                    operationCancellation.awaitCompletion()
                    throw IllegalStateException(context.getString(R.string.mira_first_response_timeout))
                }

                if (firstResponseObserved && historyMessageTimestampsToExclude.isNotEmpty() && chatId != null) {
                    fullChatHistory
                        .asSequence()
                        .filter { message ->
                            message.timestamp in historyMessageTimestampsToExclude &&
                                message.sender == "user" &&
                                message.displayMode == ChatMessageDisplayMode.PENDING_DISPATCH
                        }
                        .forEach { pendingMessage ->
                            addMessageToChat(
                                chatId,
                                pendingMessage.copy(
                                    sentAt = requestSentAt,
                                    displayMode = ChatMessageDisplayMode.NORMAL,
                                ),
                            )
                        }
                }

                val streamCollectionError = streamCollectionResult.await()
                if (streamCollectionError != null) {
                    throw streamCollectionError
                }
                logMessageTiming(
                    stage = "delegate.sharedStreamComplete",
                    startTimeMs = responseStartTime,
                    details = "chatId=$activeChatId"
                )

                if (
                    turnOptions.requireToolExecution &&
                        (_currentTurnToolInvocationCountByChatId.value[chatId] ?: 0) == 0
                ) {
                    val verificationMessage = context.getString(R.string.mira_device_action_not_executed)
                    AppLogger.w(TAG, "Device-assist turn completed without a tool invocation: chatId=$chatId")
                    if (isWaifuModeEnabled) {
                        aiMessage.content =
                            listOf(aiMessage.content.trim(), verificationMessage)
                                .filter { it.isNotBlank() }
                                .joinToString("\n\n")
                        emitWaifuSegment(verificationMessage)
                    } else {
                        aiMessage.content =
                            listOf(aiMessage.content.trim(), verificationMessage)
                                .filter { it.isNotBlank() }
                                .joinToString("\n\n")
                    }
                }

                if (
                    !hasAssistantResponseContent(
                        rawContent = aiMessage.content,
                        emittedSegments = waifuEmittedSegments,
                    )
                ) {
                    throw IllegalStateException(context.getString(R.string.provider_error_response_empty))
                }

                runCatching {
                    turnInputTokens = service.getCurrentInputTokenCount()
                    turnOutputTokens = service.getCurrentOutputTokenCount()
                    turnCachedInputTokens = service.getCurrentCachedInputTokenCount()
                }.onFailure {
                    AppLogger.w(TAG, "读取本轮 token 统计失败", it)
                }

                val waitDurationMs =
                    if (requestStartElapsed > 0L && firstResponseElapsed != null) {
                        (firstResponseElapsed!! - requestStartElapsed).coerceAtLeast(0L)
                    } else {
                        0L
                    }
                val outputDurationMs =
                    if (firstResponseElapsed != null) {
                        (messageTimingNow() - firstResponseElapsed!!).coerceAtLeast(0L)
                    } else {
                        0L
                    }

                if (requestSentAt > 0L) {
                    if (userMessageAdded && chatId != null) {
                        userMessage =
                            userMessage.withTurnMetrics(
                                inputTokens = turnInputTokens,
                                outputTokens = turnOutputTokens,
                                cachedInputTokens = turnCachedInputTokens,
                                sentAt = requestSentAt,
                                outputDurationMs = outputDurationMs,
                                waitDurationMs = waitDurationMs
                            )
                        addMessageToChat(chatId, userMessage)
                    }

                    aiMessage =
                        aiMessage.withTurnMetrics(
                            inputTokens = turnInputTokens,
                            outputTokens = turnOutputTokens,
                            cachedInputTokens = turnCachedInputTokens,
                            sentAt = requestSentAt,
                            outputDurationMs = outputDurationMs,
                            waitDurationMs = waitDurationMs
                        )
                }
                val preparedSpeech = SpeechContentMetadata.prepare(aiMessage.content)
                aiMessage =
                    aiMessage.copy(
                        completedAt = System.currentTimeMillis(),
                        speechDirectionJson = preparedSpeech.speechDirectionJson,
                    )

                if (isWaifuModeEnabled) {
                    syncWaifuMessageMetricsHandler?.invoke(aiMessage)
                }

                val stateAfterStream =
                    _inputProcessingStateByChatId.value[chatKey(chatId)]
                if (stateAfterStream !is EnhancedInputProcessingState.Error) {
                    shouldNotifyTurnComplete = true
                    finalInputStateAfterSend = EnhancedInputProcessingState.Completed
                }

                if (pendingAsyncSummaryUiByChatId.containsKey(chatId)) {
                    setSuppressIdleCompletedStateForChat(chatId, true)
                    finalInputStateAfterSend =
                        EnhancedInputProcessingState.Summarizing(
                            context.getString(R.string.message_summarizing)
                        )
                }

                logMessageTiming(
                    stage = "delegate.responseProcessingComplete",
                    startTimeMs = responseStartTime,
                    details = "chatId=$activeChatId, waifu=$isWaifuModeEnabled, autoRead=$didStreamAutoRead"
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    AppLogger.d(TAG, "消息发送被取消")
                    val partialMessage = runCatching { aiMessage }.getOrNull()
                    if (
                        shouldPersistCancelledWaifuResponse(
                            isWaifuModeEnabled = isWaifuModeEnabled,
                            preservePartialResponse =
                                chatRuntime.preservePartialResponseOnCancellation,
                            partialContent = partialMessage?.content,
                        )
                    ) {
                        withContext(NonCancellable) {
                            syncWaifuMessageMetricsHandler?.invoke(
                                requireNotNull(partialMessage).copy(
                                    completedAt = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                    finalInputStateAfterSend = EnhancedInputProcessingState.Idle
                    shouldNotifyTurnComplete = false
                    cancellationToPropagate = e
                } else {
                    AppLogger.e(TAG, "发送消息时出错", e)
                    setChatInputProcessingState(
                        chatId,
                        EnhancedInputProcessingState.Error(context.getString(R.string.message_send_failed, e.message))
                    )
                    withContext(Dispatchers.Main) { showErrorMessage(context.getString(R.string.message_send_failed, e.message)) }
                }
            } finally {
                val finalizeMessageStartTime = messageTimingNow()
                val deferTurnCompleteToAsyncJob =
                    if (cancellationToPropagate == null) {
                        finalizeMessageAndNotify(
                            chatId = chatId,
                            activeChatId = activeChatId,
                            aiMessageProvider = { aiMessage },
                            isWaifuModeEnabled = isWaifuModeEnabled,
                            skipFinalAutoRead = didStreamAutoRead && !isWaifuModeEnabled,
                            syncWaifuMessageMetrics = { sourceMessage ->
                                syncWaifuMessageMetricsHandler?.invoke(sourceMessage)
                            },
                            calculateNextWindowSize = calculateNextWindowSize,
                            turnOptions = turnOptions
                        )
                    } else {
                        AppLogger.d(TAG, "取消回合不执行消息收尾: chatId=$activeChatId")
                        false
                    }
                logMessageTiming(
                    stage = "delegate.finalizeMessage",
                    startTimeMs = finalizeMessageStartTime,
                    details = "chatId=$activeChatId, notifyTurnComplete=$shouldNotifyTurnComplete"
                )

                workspaceToolHookSession?.let { session ->
                    val cleanupWorkspaceHookStartTime = messageTimingNow()
                    runCatching { toolHandler.removeToolHook(session) }
                        .onFailure { AppLogger.w(TAG, "Failed to remove workspace hook", it) }
                    runCatching { session.close() }
                        .onFailure { AppLogger.w(TAG, "Failed to close workspace hook session", it) }
                    logMessageTiming(
                        stage = "delegate.cleanupWorkspaceHook",
                        startTimeMs = cleanupWorkspaceHookStartTime,
                        details = "chatId=$activeChatId"
                    )
                }

                val cleanupRuntimeStartTime = messageTimingNow()
                cleanupRuntimeAfterSend(chatId, chatRuntime)
                logMessageTiming(
                    stage = "delegate.cleanupRuntime",
                    startTimeMs = cleanupRuntimeStartTime,
                    details = "chatId=$activeChatId"
                )

                // Terminal states are intentionally ignored while the chat runtime is loading.
                // Publish them only after cleanup flips isLoading to false and cancels the
                // service-state collector, otherwise a completed tool turn remains stuck in
                // the last Receiving/ProcessingToolResult state.
                if (!deferTurnCompleteToAsyncJob) {
                    finalInputStateAfterSend?.let { terminalState ->
                        setChatInputProcessingState(chatId, terminalState)
                    }
                }

                if (shouldNotifyTurnComplete && !deferTurnCompleteToAsyncJob) {
                    val service = serviceForTurnComplete
                    if (service != null) {
                        notifyTurnComplete(
                            chatId,
                            activeChatId,
                            service,
                            calculateNextWindowSize,
                            turnOptions
                        )
                    }
                }

                logMessageTiming(
                    stage = "delegate.sendUserMessage.total",
                    startTimeMs = sendUserMessageStartTime,
                    details = "chatId=$activeChatId, addedUserMessage=$userMessageAdded, enableSummary=$enableSummary, persistTurn=${turnOptions.persistTurn}"
                )
            }
            cancellationToPropagate?.let { throw it }
            } catch (error: kotlinx.coroutines.CancellationException) {
                if (chatRuntime.isLoading.value) {
                    cleanupRuntimeAfterSend(chatId, chatRuntime)
                    setChatInputProcessingState(chatId, EnhancedInputProcessingState.Idle)
                }
                throw error
            } catch (error: Exception) {
                AppLogger.e(TAG, "Uncaught send pipeline failure before normal finalization: chatId=$chatId", error)
                if (chatRuntime.isLoading.value) {
                    val message = context.getString(R.string.message_send_failed, error.message)
                    setChatInputProcessingState(chatId, EnhancedInputProcessingState.Error(message))
                    withContext(Dispatchers.Main) { showErrorMessage(message) }
                    cleanupRuntimeAfterSend(chatId, chatRuntime)
                }
            } finally {
                val completedJob = coroutineContext[Job]
                synchronized(chatRuntime) {
                    if (
                        shouldClearCompletedSendJob(
                            runtimeGeneration = chatRuntime.sendGeneration.get(),
                            completedGeneration = sendGeneration,
                            isSameJob = chatRuntime.sendJob === completedJob,
                        )
                    ) {
                        chatRuntime.sendJob = null
                    }
                }
            }
        }
        val attachedToRuntime =
            synchronized(chatRuntime) {
                if (
                    !isChatRuntimeBusy(chatRuntime.isLoading.value, chatRuntime.isCancelling) ||
                        chatRuntime.sendGeneration.get() != sendGeneration
                ) {
                    false
                } else {
                    chatRuntime.sendJob = sendJob
                    true
                }
            }
        if (!attachedToRuntime) {
            sendJob.cancel()
            return false
        }
        sendJob.start()
        return true
    }

    suspend fun regenerateAiMessageVariant(
        chatId: String,
        targetMessageTimestamp: Long,
        requestMessageContent: String,
        requestHistory: List<ChatMessage>,
        workspacePath: String?,
        promptFunctionType: PromptFunctionType,
        roleCardId: String,
        currentRoleName: String,
        enableThinking: Boolean,
        enableMemoryAutoUpdate: Boolean,
        maxTokens: Int,
        tokenUsageThreshold: Double,
        chatModelConfigIdOverride: String?,
        chatModelIndexOverride: Int?,
        preferenceProfileIdOverride: String?,
        groupOrchestrationMode: Boolean,
        groupParticipantNamesText: String?,
        onVariantPreviewStarted: suspend (ChatMessage) -> Unit,
        onVariantReady: suspend (ChatMessage) -> Unit,
    ) {
        val chatRuntime = runtimeFor(chatId)
        val currentJob = coroutineContext[Job] ?: throw IllegalStateException("Missing coroutine job")
        var serviceForTerminalCleanup: EnhancedAIService? = null
        var shouldResetInputStateToIdle = false
        val regenerateGeneration =
            synchronized(chatRuntime) {
                if (isChatRuntimeBusy(chatRuntime.isLoading.value, chatRuntime.isCancelling)) {
                    null
                } else {
                    val generation = chatRuntime.sendGeneration.incrementAndGet()
                    chatRuntime.sendJob = currentJob
                    chatRuntime.isLoading.value = true
                    generation
                }
            } ?: throw IllegalStateException(context.getString(R.string.chat_regenerate_busy))
        resetCurrentTurnToolInvocationCount(chatId)
        updateGlobalLoadingState()
        setChatInputProcessingState(
            chatId,
            EnhancedInputProcessingState.Processing(context.getString(R.string.message_processing)),
        )
        var terminalState: EnhancedInputProcessingState? = null
        var exceptionToPropagate: Exception? = null

        try {
            // Regeneration is a separate turn and must keep its own immutable memory owner.
            val memoryCompanionId =
                CompanionMemoryTargetResolver.snapshotForTurn(
                    characterGroupId =
                        AppDatabase.getDatabase(context.applicationContext)
                            .chatDao()
                            .getChatById(chatId)
                            ?.characterGroupId,
                    roleCardId = roleCardId,
                )
            val service =
                EnhancedAIService.getChatInstance(context, chatId)
                    ?: getEnhancedAiService()
                    ?: throw IllegalStateException(context.getString(R.string.message_ai_service_not_initialized))
            serviceForTerminalCleanup = service
            service.setInputProcessingState(
                EnhancedInputProcessingState.Processing(context.getString(R.string.message_processing))
            )

            chatRuntime.stateCollectionJob?.cancel()
            chatRuntime.stateCollectionJob =
                coroutineScope.launch {
                    var lastErrorMessage: String? = null
                    service.inputProcessingState.collect { state ->
                        setChatInputProcessingState(chatId, state)

                        if (state is EnhancedInputProcessingState.Error) {
                            val msg = state.message
                            if (msg != lastErrorMessage) {
                                lastErrorMessage = msg
                                withContext(Dispatchers.Main) {
                                    showErrorMessage(msg)
                                }
                            }
                        } else {
                            lastErrorMessage = null
                        }
                    }
                }

            val (provider, modelName) =
                service.getDisplayProviderAndModelForFunction(
                    functionType = FunctionType.CHAT,
                    chatModelConfigIdOverride = chatModelConfigIdOverride,
                    chatModelIndexOverride = chatModelIndexOverride,
                )

            var firstResponseElapsed: Long? = null
            val requestSentAt = System.currentTimeMillis()
            val requestStartElapsed = messageTimingNow()
            val effectiveRequestMessageContent =
                if (groupOrchestrationMode &&
                    requestMessageContent.trimStart().isNotEmpty() &&
                    !requestMessageContent.trimStart().startsWith("[From user]")
                ) {
                    "[From user]\n$requestMessageContent"
                } else {
                    requestMessageContent
                }

            val responseStream =
                AIMessageManager.sendMessage(
                    enhancedAiService = service,
                    chatId = chatId,
                    messageContent = effectiveRequestMessageContent,
                    chatHistory = requestHistory,
                    workspacePath = workspacePath,
                    promptFunctionType = promptFunctionType,
                    enableThinking = enableThinking,
                    enableMemoryAutoUpdate = enableMemoryAutoUpdate,
                    maxTokens = maxTokens,
                    tokenUsageThreshold = tokenUsageThreshold,
                    onNonFatalError = { error -> _nonFatalErrorEvent.emit(error) },
                    characterName = currentRoleName,
                    roleCardId = roleCardId,
                    memoryCompanionId = memoryCompanionId,
                    currentRoleName = currentRoleName,
                    splitHistoryByRole = true,
                    groupOrchestrationMode = groupOrchestrationMode,
                    groupParticipantNamesText = groupParticipantNamesText,
                    onToolInvocation = { incrementCurrentTurnToolInvocationCount(chatId) },
                    chatModelConfigIdOverride = chatModelConfigIdOverride,
                    chatModelIndexOverride = chatModelIndexOverride,
                    preferenceProfileIdOverride = preferenceProfileIdOverride,
                )

            val sharedResponseStream = responseStream
            chatRuntime.responseStream = sharedResponseStream

            val aiMessage =
                ChatMessage(
                    sender = "ai",
                    contentStream = sharedResponseStream,
                    timestamp = targetMessageTimestamp,
                    roleName = currentRoleName,
                    provider = provider,
                    modelName = modelName,
                    sentAt = requestSentAt,
                )
            onVariantPreviewStarted(aiMessage)

            coroutineScope {
                val revisableStream = sharedResponseStream as? TextStreamEventCarrier
                val revisionTracker = TextStreamRevisionTracker()
                val revisionMutex = Mutex()

                val revisionJob =
                    revisableStream?.let { carrier ->
                        launch {
                            carrier.eventChannel.collect { event ->
                                when (event.eventType) {
                                    TextStreamEventType.SAVEPOINT -> {
                                        revisionMutex.withLock {
                                            revisionTracker.savepoint(event.id)
                                        }
                                    }

                                    TextStreamEventType.ROLLBACK -> {
                                        val snapshot =
                                            revisionMutex.withLock {
                                                revisionTracker.rollback(event.id)
                                            } ?: return@collect
                                        aiMessage.content = snapshot
                                    }
                                }
                            }
                        }
                    }

                sharedResponseStream.collect { chunk ->
                    if (firstResponseElapsed == null) {
                        firstResponseElapsed = messageTimingNow()
                    }
                    aiMessage.content =
                        revisionMutex.withLock {
                            revisionTracker.append(chunk)
                        }
                }

                revisionJob?.cancelAndJoin()
            }

            val finalContent = resolveFinalContent(aiMessage)
            var turnInputTokens = 0
            var turnOutputTokens = 0
            var turnCachedInputTokens = 0
            runCatching {
                turnInputTokens = service.getCurrentInputTokenCount()
                turnOutputTokens = service.getCurrentOutputTokenCount()
                turnCachedInputTokens = service.getCurrentCachedInputTokenCount()
            }.onFailure {
                AppLogger.w(TAG, "读取重新生成 token 统计失败", it)
            }

            val waitDurationMs =
                if (firstResponseElapsed != null) {
                    (firstResponseElapsed!! - requestStartElapsed).coerceAtLeast(0L)
                } else {
                    0L
                }
            val outputDurationMs =
                if (firstResponseElapsed != null) {
                    (messageTimingNow() - firstResponseElapsed!!).coerceAtLeast(0L)
                } else {
                    0L
                }

            val completedAt = System.currentTimeMillis()
            val preparedSpeech = SpeechContentMetadata.prepare(finalContent)
            if (!ConversationContentVisibility.hasRenderableAssistantContent(preparedSpeech.visibleText)) {
                AppLogger.w(
                    TAG,
                    "Skipping blank regenerated variant: chatId=$chatId rawLen=${finalContent.length}",
                )
                throw IllegalStateException(context.getString(R.string.provider_error_response_empty))
            }
            onVariantReady(
                aiMessage.withTurnMetrics(
                    inputTokens = turnInputTokens,
                    outputTokens = turnOutputTokens,
                    cachedInputTokens = turnCachedInputTokens,
                    sentAt = requestSentAt,
                    outputDurationMs = outputDurationMs,
                    waitDurationMs = waitDurationMs,
                ).copy(
                    content = preparedSpeech.visibleText,
                    contentStream = null,
                    completedAt = completedAt,
                    speechDirectionJson = preparedSpeech.speechDirectionJson,
                )
            )
            terminalState = EnhancedInputProcessingState.Completed
            shouldResetInputStateToIdle = true
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) {
                terminalState = EnhancedInputProcessingState.Idle
            } else {
                AppLogger.e(TAG, "单条重新生成失败", e)
                setChatInputProcessingState(
                    chatId,
                    EnhancedInputProcessingState.Error(
                        context.getString(R.string.chat_regenerate_single_failed, e.message ?: "")
                    ),
                )
            }
            exceptionToPropagate = e
        } finally {
            clearCurrentTurnToolInvocationCount(chatId)
            val releasedRuntime =
                synchronized(chatRuntime) {
                    if (
                        !shouldClearCompletedSendJob(
                            runtimeGeneration = chatRuntime.sendGeneration.get(),
                            completedGeneration = regenerateGeneration,
                            isSameJob = chatRuntime.sendJob === currentJob,
                        )
                    ) {
                        false
                    } else {
                        chatRuntime.stateCollectionJob?.cancel()
                        chatRuntime.stateCollectionJob = null
                        chatRuntime.responseStream = null
                        chatRuntime.sendJob = null
                        chatRuntime.isLoading.value = false
                        true
                    }
                }
            if (releasedRuntime) {
                updateGlobalLoadingState()
                terminalState?.let { state ->
                    setChatInputProcessingState(chatId, state)
                }
                if (shouldResetInputStateToIdle) {
                    serviceForTerminalCleanup?.setInputProcessingState(
                        EnhancedInputProcessingState.Idle
                    )
                    setChatInputProcessingState(chatId, EnhancedInputProcessingState.Idle)
                }
            }
        }
        exceptionToPropagate?.let { throw it }
    }

    private suspend fun notifyTurnComplete(
        chatId: String?,
        activeChatId: String?,
        service: EnhancedAIService,
        calculateNextWindowSize: (suspend () -> Int?)? = null,
        turnOptions: ChatTurnOptions = ChatTurnOptions()
    ) {
        if (!chatId.isNullOrBlank()) {
            _turnCompleteCounterByChatId.update { current ->
                current.toMutableMap().apply { this[chatId] = (this[chatId] ?: 0L) + 1L }
            }
        }
        val nextWindowSize = calculateNextWindowSize?.invoke()
        AppLogger.d(
            TAG,
            "回合完成: chatId=$activeChatId, nextWindow=$nextWindowSize, service=${service.javaClass.simpleName}"
        )
        onTurnComplete(activeChatId, service, nextWindowSize, turnOptions)
    }

    private suspend fun finalizeMessageAndNotify(
        chatId: String?,
        activeChatId: String?,
        aiMessageProvider: () -> ChatMessage,
        isWaifuModeEnabled: Boolean,
        skipFinalAutoRead: Boolean,
        syncWaifuMessageMetrics: suspend (ChatMessage) -> Unit,
        calculateNextWindowSize: (suspend () -> Int?)? = null,
        turnOptions: ChatTurnOptions = ChatTurnOptions()
    ): Boolean {
        try {
            val aiMessage = aiMessageProvider()
            // 优先使用共享流的全量重放缓存重建最终文本，避免完成信号早于收集协程处理尾部字符时丢字。
            val finalContent = resolveFinalContent(aiMessage)
            if (finalContent.isBlank()) {
                AppLogger.w(TAG, "Skipping empty assistant message during finalization: chatId=$activeChatId")
                return false
            }
            aiMessage.content = finalContent
            val completedAt = System.currentTimeMillis()
            val preparedSpeech = SpeechContentMetadata.prepare(finalContent)
            if (!ConversationContentVisibility.hasRenderableAssistantContent(preparedSpeech.visibleText)) {
                AppLogger.w(
                    TAG,
                    "Skipping non-renderable assistant message during finalization: chatId=$activeChatId rawLen=${finalContent.length}",
                )
                return false
            }

            withContext(Dispatchers.IO) {
                if (isWaifuModeEnabled) {
                    syncWaifuMessageMetrics(
                        aiMessage.copy(
                            completedAt = completedAt,
                            speechDirectionJson = preparedSpeech.speechDirectionJson,
                        )
                    )
                    forceEmitScrollToBottom(chatId)
                } else {
                    // 普通模式，直接清理流
                    val finalMessage =
                        aiMessage.copy(
                            content = preparedSpeech.visibleText,
                            contentStream = null,
                            completedAt = completedAt,
                            speechDirectionJson = preparedSpeech.speechDirectionJson,
                        )
                    val shouldAutoRead =
                        isAutoReadEnabledFor(turnOptions) && !skipFinalAutoRead
                    withContext(Dispatchers.Main) {
                        if (turnOptions.persistTurn && chatId != null) {
                            addMessageToChat(chatId, finalMessage)
                        }
                        AppLogger.d(
                            TAG,
                            "autoRead[final] enabled=${isAutoReadEnabledFor(turnOptions)} skipFinalAutoRead=$skipFinalAutoRead len=${finalContent.length} preview=\"${speechPreview(finalContent)}\""
                        )
                        forceEmitScrollToBottom(chatId)
                    }
                    if (shouldAutoRead) {
                        delay(TEXT_PUBLISH_BEFORE_SPEECH_MS)
                        speakMessageHandler(finalContent, true)
                    }
                }
            }
        } catch (e: UninitializedPropertyAccessException) {
            AppLogger.d(TAG, "AI消息未初始化，跳过流清理步骤")
        } catch (e: kotlinx.coroutines.CancellationException) {
            AppLogger.d(TAG, "消息收尾阶段被取消，跳过waifu收尾处理")
            throw e
        } catch (e: Exception) {
            AppLogger.e(TAG, "处理waifu模式时出错", e)
            try {
                val aiMessage = aiMessageProvider()
                val finalContent = aiMessage.content
                val preparedSpeech = SpeechContentMetadata.prepare(finalContent)
                if (!ConversationContentVisibility.hasRenderableAssistantContent(preparedSpeech.visibleText)) {
                    AppLogger.w(
                        TAG,
                        "Skipping non-renderable assistant message during finalization fallback: chatId=$activeChatId rawLen=${finalContent.length}",
                    )
                    return false
                }
                val finalMessage =
                    aiMessage.copy(
                        content = preparedSpeech.visibleText,
                        contentStream = null,
                        completedAt = System.currentTimeMillis(),
                        speechDirectionJson = preparedSpeech.speechDirectionJson,
                    )
                withContext(Dispatchers.Main) {
                    if (turnOptions.persistTurn && chatId != null) {
                        addMessageToChat(chatId, finalMessage)
                    }
                }
            } catch (ex: Exception) {
                AppLogger.e(TAG, "回退到普通模式也失败", ex)
            }
        }
        return false
    }

    private fun cleanupRuntimeAfterSend(chatId: String, chatRuntime: ChatRuntime) {
        clearCurrentTurnToolInvocationCount(chatId)
        synchronized(chatRuntime) {
            chatRuntime.streamCollectionJob = null
            chatRuntime.stateCollectionJob?.cancel()
            chatRuntime.stateCollectionJob = null
            chatRuntime.responseStream = null
            chatRuntime.currentTurnOptions = ChatTurnOptions()
            chatRuntime.requestSentAt = 0L
            chatRuntime.requestStartElapsed = 0L
            chatRuntime.firstResponseElapsed = null
            chatRuntime.preservePartialResponseOnCancellation = true
            chatRuntime.isLoading.value = false
        }

        updateGlobalLoadingState()
    }

    /**
     * 刷新聚合后的加载状态。
     * 仅重新计算全局/按会话的加载派生值，不会直接改写具体 chat 的 isLoading。
     */
    fun refreshGlobalLoadingState() {
        updateGlobalLoadingState()
    }

    private fun isAutoReadEnabledFor(turnOptions: ChatTurnOptions): Boolean =
        turnOptions.autoReadOverride ?: getIsAutoReadEnabled()
}
