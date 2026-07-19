package com.ai.assistance.operit.services.core

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.core.chat.AIMessageManager
import com.ai.assistance.operit.core.chat.CompanionMemoryTargetResolver
import com.ai.assistance.operit.core.chat.HistoryCompactionPlan
import com.ai.assistance.operit.core.chat.HistoryCompactionPlanner
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.core.config.FunctionalPrompts
import com.ai.assistance.operit.api.chat.enhance.MultiServiceManager
import com.ai.assistance.operit.api.chat.llmprovider.AIService
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.CharacterCard
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.PromptFunctionType
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.ChatComposerSnapshot
import com.ai.assistance.operit.data.model.ChatMessageDisplayMode
import com.ai.assistance.operit.data.model.ChatTurnOptions
import com.ai.assistance.operit.data.model.InputProcessingState
import com.ai.assistance.operit.data.model.CharacterCardChatModelBindingMode
import com.ai.assistance.operit.data.model.CharacterCardMemoryProfileBindingMode
import com.ai.assistance.operit.data.model.ActivePrompt
import com.ai.assistance.operit.core.tools.ToolProgressBus
import com.ai.assistance.operit.ui.features.chat.viewmodel.UiStateDelegate
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.CharacterGroupCardManager
import com.ai.assistance.operit.data.preferences.ActivePromptManager
import com.ai.assistance.operit.data.preferences.DisplayPreferencesManager
import com.ai.assistance.operit.data.preferences.preferencesManager
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.data.preferences.FunctionalConfigManager
import com.ai.assistance.operit.data.preferences.FunctionConfigMapping
import com.ai.assistance.operit.services.ChatServiceUiBridge
import com.ai.assistance.operit.util.ChatMarkupRegex
import com.ai.assistance.operit.util.ChatUtils
import com.ai.assistance.operit.util.LocaleUtils
import com.ai.assistance.operit.data.repository.MemoryAutoSaveCandidateRepository
import com.ai.assistance.operit.api.chat.library.MemoryAutoSaveScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.coroutineContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

internal fun List<ChatMessage>.validUserMessagesForMemory(): List<ChatMessage> =
    asSequence()
        .filter { it.sender == "user" }
        .filter { it.content.isNotBlank() }
        .distinctBy { it.timestamp }
        .sortedBy { it.timestamp }
        .toList()

internal fun canAcceptMessageDispatch(
    isChatLoading: Boolean,
    reservationAcquired: Boolean,
): Boolean = !isChatLoading && reservationAcquired

internal fun boundDispatchChatId(
    explicitChatId: String?,
    chatIdAtClick: String?,
): String? = explicitChatId?.takeIf { it.isNotBlank() } ?: chatIdAtClick

internal fun permitsGroupOrchestrationDispatch(
    hasComposerSnapshot: Boolean,
    messageTextOverride: String?,
    chatIdOverride: String?,
    allowBoundUserText: Boolean = false,
): Boolean =
    hasComposerSnapshot ||
        allowBoundUserText ||
        (messageTextOverride.isNullOrBlank() && chatIdOverride.isNullOrBlank())

internal fun hasPlannedGroupSpeaker(roundSpeakerFlags: List<List<Boolean>>): Boolean =
    roundSpeakerFlags.any { round -> round.any { it } }

/**
 * 消息协调委托类
 * 负责消息发送、自动总结、附件清理等核心协调逻辑
 */
class MessageCoordinationDelegate(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val chatHistoryDelegate: ChatHistoryDelegate,
    private val messageProcessingDelegate: MessageProcessingDelegate,
    private val tokenStatsDelegate: TokenStatisticsDelegate,
    private val apiConfigDelegate: ApiConfigDelegate,
    private val attachmentDelegate: AttachmentDelegate,
    private val uiStateDelegate: UiStateDelegate,
    private val getEnhancedAiService: () -> EnhancedAIService?,
    private var uiBridge: ChatServiceUiBridge
) {
    companion object {
        private const val TAG = "MessageCoordinationDelegate"
    }

    // 总结状态（使用 summarizeHistory 时）
    private val _isSummarizing = MutableStateFlow(false)
    val isSummarizing: StateFlow<Boolean> = _isSummarizing.asStateFlow()

    private val _isUpdatingMemory = MutableStateFlow(false)

    private val _summarizingChatId = MutableStateFlow<String?>(null)
    val summarizingChatId: StateFlow<String?> = _summarizingChatId.asStateFlow()

    // 发送消息触发的异步总结状态（使用 launchAsyncSummaryForSend 时）
    private val _isSendTriggeredSummarizing = MutableStateFlow(false)
    val isSendTriggeredSummarizing: StateFlow<Boolean> = _isSendTriggeredSummarizing.asStateFlow()

    private val _sendTriggeredSummarizingChatId = MutableStateFlow<String?>(null)
    val sendTriggeredSummarizingChatId: StateFlow<String?> = _sendTriggeredSummarizingChatId.asStateFlow()

    // 保存总结任务的 Job 引用，用于取消
    private var summaryJob: Job? = null
    private var sendTriggeredSummaryJob: Job? = null
    private val sendTriggeredSummaryLock = Any()

    // 保存当前的 promptFunctionType，用于自动继续时保持提示词一致性
    private var currentPromptFunctionType: PromptFunctionType = PromptFunctionType.CHAT
    private var currentChatModelConfigIdOverride: String? = null
    private var currentChatModelIndexOverride: Int? = null
    private var currentPreferenceProfileIdOverride: String? = null

    private var nonFatalErrorCollectorJob: Job? = null
    private val characterCardManager = CharacterCardManager.getInstance(context)
    private val characterGroupCardManager = CharacterGroupCardManager.getInstance(context)
    private val activePromptManager = ActivePromptManager.getInstance(context)
    private val displayPreferencesManager = DisplayPreferencesManager.getInstance(context)
    private val plannerServiceManager = MultiServiceManager(context)
    private data class PendingAutoContinuationRequest(
        val chatId: String,
        val promptFunctionType: PromptFunctionType,
        val chatModelConfigIdOverride: String?,
        val chatModelIndexOverride: Int?,
        val preferenceProfileIdOverride: String?,
        val roleCardIdOverride: String?,
        val isGroupOrchestrationTurn: Boolean,
        val groupParticipantNamesText: String?,
        var waitJob: Job? = null
    )

    private data class CompanionMemoryEnqueueTarget(
        val companionId: String,
        val roleCardId: String?,
    )

    private val pendingAutoContinuationByChatId =
        ConcurrentHashMap<String, PendingAutoContinuationRequest>()
    private val pendingMessageDispatchChatIds = ConcurrentHashMap.newKeySet<String>()
    private val pendingInitialChatDispatch = AtomicBoolean(false)

    init {
        ensureNonFatalErrorCollectorStarted()
    }

    private fun ensureNonFatalErrorCollectorStarted() {
        if (nonFatalErrorCollectorJob?.isActive == true) return
        nonFatalErrorCollectorJob = coroutineScope.launch {
            messageProcessingDelegate.nonFatalErrorEvent.collect { errorMessage ->
                uiStateDelegate.showToast(errorMessage)
            }
        }
    }

    private fun reportDispatchFailure(chatId: String?, error: Throwable) {
        AppLogger.e(
            TAG,
            "Message dispatch failed before the response pipeline started: chatId=$chatId",
            error,
        )
        val message = context.getString(R.string.message_send_failed, error.message)
        if (chatId != null && !messageProcessingDelegate.isChatLoading(chatId)) {
            messageProcessingDelegate.setInputProcessingStateForChat(
                chatId,
                InputProcessingState.Error(message),
            )
        }
        uiStateDelegate.showErrorMessage(message)
    }



    private fun fallbackConversationTitle(userText: String, attachments: List<com.ai.assistance.operit.data.model.AttachmentInfo>): String {
        return attachments.firstOrNull()?.fileName?.trim()?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.new_conversation)
    }

    private fun launchConversationTitleGeneration(
        chatId: String,
        userText: String,
        attachments: List<com.ai.assistance.operit.data.model.AttachmentInfo>,
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
                val currentTitle = chatHistoryDelegate.chatHistories.value.firstOrNull { it.id == chatId }?.title
                if (generatedTitle.isNotBlank() && currentTitle == fallbackTitle) {
                    chatHistoryDelegate.updateChatTitle(chatId, generatedTitle)
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "生成群组对话标题失败", e)
            }
        }
    }

    private suspend fun recalculateStableWindowSize(
        service: EnhancedAIService,
        chatId: String?,
        roleCardId: String?,
        promptFunctionType: PromptFunctionType = PromptFunctionType.CHAT,
        groupOrchestrationMode: Boolean = false,
        groupParticipantNamesText: String? = null,
        chatModelConfigIdOverride: String? = null,
        chatModelIndexOverride: Int? = null,
        preferenceProfileIdOverride: String? = null
    ): Int {
        val currentChat = chatHistoryDelegate.chatHistories.value.firstOrNull { it.id == chatId }
        val currentRoleName =
            roleCardId?.let {
                runCatching { characterCardManager.getCharacterCardFlow(it).first().name }
                    .getOrNull()
            }
        return AIMessageManager.calculateStableContextWindow(
            enhancedAiService = service,
            chatId = chatId,
            chatHistory = chatId?.let { chatHistoryDelegate.getRuntimeChatHistory(it) }.orEmpty(),
            workspacePath = currentChat?.workspace,
            workspaceEnv = currentChat?.workspaceEnv,
            promptFunctionType = promptFunctionType,
            roleCardId = roleCardId,
            currentRoleName = currentRoleName,
            splitHistoryByRole = true,
            groupOrchestrationMode = groupOrchestrationMode,
            groupParticipantNamesText = groupParticipantNamesText,
            chatModelConfigIdOverride = chatModelConfigIdOverride,
            chatModelIndexOverride = chatModelIndexOverride,
            preferenceProfileIdOverride = preferenceProfileIdOverride,
            publishEstimate = true
        )
    }

    private suspend fun resolveBoundRoleCardId(chatId: String?): String? {
        if (chatId.isNullOrBlank()) {
            return null
        }
        val chatMeta = chatHistoryDelegate.chatHistories.value.firstOrNull { it.id == chatId } ?: return null
        if (!chatMeta.characterGroupId.isNullOrBlank()) {
            return null
        }
        val characterCardName = chatMeta.characterCardName?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { characterCardManager.findCharacterCardByName(characterCardName)?.id }.getOrNull()
    }

    private suspend fun resolveWindowEstimateRoleCardId(
        chatId: String?,
        roleCardId: String?
    ): String? {
        return roleCardId
            ?: resolveBoundRoleCardId(chatId)
            ?: runCatching { activePromptManager.resolveActiveCardIdForSend() }.getOrNull()
    }

    private suspend fun resolveRegenerationRoleCardId(
        chatId: String,
        message: ChatMessage,
    ): String {
        val roleName = message.roleName.trim()
        if (roleName.isNotEmpty()) {
            val card = characterCardManager.findCharacterCardByName(roleName)
            if (card != null) {
                return card.id
            }
        }
        return resolveWindowEstimateRoleCardId(chatId, null)
            ?: CharacterCardManager.DEFAULT_CHARACTER_CARD_ID
    }

    private fun isGroupChatSession(chatId: String?): Boolean {
        if (chatId.isNullOrBlank()) return false
        return chatHistoryDelegate.chatHistories.value
            .firstOrNull { it.id == chatId }
            ?.characterGroupId
            ?.isNotBlank() == true
    }

    private fun resolveWindowEstimateService(chatId: String?): EnhancedAIService? {
        return if (chatId.isNullOrBlank()) {
            getEnhancedAiService()
        } else {
            EnhancedAIService.getChatInstance(context, chatId)
        }
    }

    suspend fun refreshStableContextWindow(
        chatId: String? = null,
        roleCardId: String? = null,
        promptFunctionType: PromptFunctionType? = null,
        groupOrchestrationMode: Boolean = false,
        groupParticipantNamesText: String? = null,
        chatModelConfigIdOverride: String? = null,
        chatModelIndexOverride: Int? = null,
        preferenceProfileIdOverride: String? = null
    ): Int? {
        val targetChatId = chatId ?: chatHistoryDelegate.currentChatId.value ?: return null
        val service = resolveWindowEstimateService(targetChatId) ?: return null
        val effectiveRoleCardId = resolveWindowEstimateRoleCardId(targetChatId, roleCardId)
        val effectivePromptFunctionType = promptFunctionType ?: currentPromptFunctionType
        val effectiveChatModelConfigIdOverride =
            chatModelConfigIdOverride ?: currentChatModelConfigIdOverride
        val effectiveChatModelIndexOverride =
            chatModelIndexOverride ?: currentChatModelIndexOverride
        val effectivePreferenceProfileIdOverride =
            preferenceProfileIdOverride
                ?: currentPreferenceProfileIdOverride
                ?: effectiveRoleCardId?.let { resolveRoleCardMemoryProfileOverride(it) }

        val newWindowSize =
            recalculateStableWindowSize(
                service = service,
                chatId = targetChatId,
                roleCardId = effectiveRoleCardId,
                promptFunctionType = effectivePromptFunctionType,
                groupOrchestrationMode = groupOrchestrationMode,
                groupParticipantNamesText = groupParticipantNamesText,
                chatModelConfigIdOverride = effectiveChatModelConfigIdOverride,
                chatModelIndexOverride = effectiveChatModelIndexOverride,
                preferenceProfileIdOverride = effectivePreferenceProfileIdOverride
            )
        val (inputTokens, outputTokens) = tokenStatsDelegate.getCumulativeTokenCounts(targetChatId)
        chatHistoryDelegate.saveCurrentChat(
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            actualContextWindowSize = newWindowSize.toLong(),
            chatIdOverride = targetChatId
        )
        withContext(Dispatchers.Main) {
            tokenStatsDelegate.setTokenCounts(
                targetChatId,
                inputTokens,
                outputTokens,
                newWindowSize.toLong()
            )
        }
        AppLogger.d(
            TAG,
            "上下文窗口已刷新: chatId=$targetChatId, window=$newWindowSize, " +
                "input=$inputTokens, output=$outputTokens, service=${service.javaClass.simpleName}, " +
                "promptType=$effectivePromptFunctionType"
        )
        return newWindowSize
    }

    /**
     * 发送用户消息
     * 检查是否有当前对话，如果没有则自动创建新对话
     */
    fun sendUserMessage(
        promptFunctionType: PromptFunctionType = PromptFunctionType.CHAT,
        roleCardIdOverride: String? = null,
        chatIdOverride: String? = null,
        messageTextOverride: String? = null,
        proxySenderNameOverride: String? = null,
        chatModelConfigIdOverride: String? = null,
        chatModelIndexOverride: Int? = null,
        composerSnapshot: ChatComposerSnapshot? = null,
        turnOptions: ChatTurnOptions = ChatTurnOptions(),
        suppressUserMessageInHistory: Boolean = false,
        historyMessageTimestampsToExclude: Set<Long> = emptySet(),
        enableGroupOrchestrationForBoundText: Boolean = false,
        reportBusyRejection: Boolean = true,
        onDispatchResolved: ((Boolean) -> Unit)? = null,
    ): Boolean {
        val dispatchResolutionReported = AtomicBoolean(false)
        val reportDispatchResolved: (Boolean) -> Unit = { accepted ->
            if (dispatchResolutionReported.compareAndSet(false, true)) {
                onDispatchResolved?.invoke(accepted)
            }
        }
        val effectiveMessageTextOverride = composerSnapshot?.text ?: messageTextOverride
        val initialTargetChatId = chatIdOverride ?: chatHistoryDelegate.currentChatId.value
        val requiresChatCreation = chatIdOverride.isNullOrBlank() && initialTargetChatId == null
        if (requiresChatCreation && !pendingInitialChatDispatch.compareAndSet(false, true)) {
            if (reportBusyRejection) {
                uiStateDelegate.showErrorMessage(context.getString(R.string.chat_regenerate_busy))
            }
            AppLogger.w(TAG, "Duplicate initial-chat send dispatch ignored")
            reportDispatchResolved(false)
            return false
        }
        if (initialTargetChatId != null) {
            val isChatLoading = messageProcessingDelegate.isChatLoading(initialTargetChatId)
            val reservationAcquired =
                !isChatLoading && pendingMessageDispatchChatIds.add(initialTargetChatId)
            if (!canAcceptMessageDispatch(isChatLoading, reservationAcquired)) {
                if (reportBusyRejection) {
                    uiStateDelegate.showErrorMessage(context.getString(R.string.chat_regenerate_busy))
                }
                AppLogger.w(
                    TAG,
                    "Message dispatch rejected: chatId=$initialTargetChatId, " +
                        "isLoading=$isChatLoading, dispatchPending=${!reservationAcquired && !isChatLoading}"
                )
                reportDispatchResolved(false)
                return false
            }
        }

        // 仅在没有指定 chatId 的情况下，才需要确保有当前对话
        if (requiresChatCreation) {
            AppLogger.d(TAG, "当前没有活跃对话，自动创建新对话")

            // 使用 coroutineScope 启动协程
            coroutineScope.launch {
                var reservedChatId = initialTargetChatId
                try {
                    val createdChat = CompletableDeferred<ChatHistory>()
                    chatHistoryDelegate.createNewChat(
                        onCreated = { newChat -> createdChat.complete(newChat) }
                    )
                    val createdChatId =
                        withTimeoutOrNull(10_000L) { createdChat.await().id }
                    if (createdChatId == null) {
                        AppLogger.e(TAG, "创建新对话超时，无法发送消息")
                        uiStateDelegate.showErrorMessage(context.getString(R.string.chat_cannot_create_new))
                        reportDispatchResolved(false)
                        return@launch
                    }
                    reservedChatId = createdChatId
                    if (!pendingMessageDispatchChatIds.add(createdChatId)) {
                        if (reportBusyRejection) {
                            uiStateDelegate.showErrorMessage(context.getString(R.string.chat_regenerate_busy))
                        }
                        reportDispatchResolved(false)
                        return@launch
                    }

                    AppLogger.d(
                        TAG,
                        "新对话创建完成，ID: $createdChatId，现在发送消息"
                    )

                    // 对话创建完成后，发送消息
                    sendMessageInternal(
                        promptFunctionType,
                        roleCardIdOverride = roleCardIdOverride,
                        chatIdOverride = createdChatId,
                        messageTextOverride = effectiveMessageTextOverride,
                        composerSnapshot = composerSnapshot,
                        proxySenderNameOverride = proxySenderNameOverride,
                        chatModelConfigIdOverride = chatModelConfigIdOverride,
                        chatModelIndexOverride = chatModelIndexOverride,
                        turnOptions = turnOptions,
                        suppressUserMessageInHistory = suppressUserMessageInHistory,
                        historyMessageTimestampsToExclude = historyMessageTimestampsToExclude,
                        enableGroupOrchestrationForBoundText = enableGroupOrchestrationForBoundText,
                        onDispatchResolved = reportDispatchResolved,
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    reportDispatchResolved(false)
                    reportDispatchFailure(reservedChatId, error)
                } finally {
                    reservedChatId?.let(pendingMessageDispatchChatIds::remove)
                    pendingInitialChatDispatch.set(false)
                }
            }
        } else {
            // 已有对话，直接发送消息
            coroutineScope.launch {
                try {
                    sendMessageInternal(
                        promptFunctionType,
                        roleCardIdOverride = roleCardIdOverride,
                        chatIdOverride = boundDispatchChatId(chatIdOverride, initialTargetChatId),
                        messageTextOverride = effectiveMessageTextOverride,
                        composerSnapshot = composerSnapshot,
                        proxySenderNameOverride = proxySenderNameOverride,
                        chatModelConfigIdOverride = chatModelConfigIdOverride,
                        chatModelIndexOverride = chatModelIndexOverride,
                        turnOptions = turnOptions,
                        suppressUserMessageInHistory = suppressUserMessageInHistory,
                        historyMessageTimestampsToExclude = historyMessageTimestampsToExclude,
                        enableGroupOrchestrationForBoundText = enableGroupOrchestrationForBoundText,
                        onDispatchResolved = reportDispatchResolved,
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    reportDispatchResolved(false)
                    reportDispatchFailure(initialTargetChatId, error)
                } finally {
                    initialTargetChatId?.let(pendingMessageDispatchChatIds::remove)
                }
            }
        }
        return true
    }

    suspend fun regenerateSingleAiMessage(index: Int) {
        val chatId =
            chatHistoryDelegate.currentChatId.value
                ?: throw IllegalStateException(context.getString(R.string.chat_no_active_conversation))
        if (messageProcessingDelegate.isChatLoading(chatId)) {
            throw IllegalStateException(context.getString(R.string.chat_regenerate_busy))
        }

        val currentHistory = chatHistoryDelegate.chatHistory.value
        val targetMessage =
            currentHistory.getOrNull(index)
                ?: throw IndexOutOfBoundsException(context.getString(R.string.chat_invalid_message_index))
        if (targetMessage.sender != "ai") {
            throw IllegalArgumentException(context.getString(R.string.chat_only_ai_message_allowed))
        }
        val runtimeHistory =
            chatHistoryDelegate.getRuntimeChatHistoryUpTo(
                chatId = chatId,
                upToTimestampInclusive = targetMessage.timestamp
            )
        val targetRuntimeIndex = runtimeHistory.indexOfFirst { it.timestamp == targetMessage.timestamp }
        if (targetRuntimeIndex < 0) {
            throw IndexOutOfBoundsException(context.getString(R.string.chat_invalid_message_index))
        }
        val requestHistory = runtimeHistory.take(targetRuntimeIndex)
        val requestMessageContent =
            requestHistory.lastOrNull()
                ?.takeIf { it.sender == "user" }
                ?.content
                .orEmpty()

        val currentChat = chatHistoryDelegate.chatHistories.value.firstOrNull { it.id == chatId }
        val groupParticipantNamesText = buildBoundGroupParticipantNamesText(chatId)
        val groupOrchestrationMode = groupParticipantNamesText != null
        val workspacePath = currentChat?.workspace
        val roleCardId = resolveRegenerationRoleCardId(chatId, targetMessage)
        val currentRoleName =
            runCatching { characterCardManager.getCharacterCardFlow(roleCardId).first().name }
                .getOrDefault(targetMessage.roleName)
                .ifBlank { targetMessage.roleName }

        val (resolvedChatModelConfigIdOverride, resolvedChatModelIndexOverride) =
            resolveRoleCardChatModelOverrides(roleCardId)
        val resolvedPreferenceProfileIdOverride =
            resolveRoleCardMemoryProfileOverride(roleCardId)
        val chatContextSettings =
            resolveChatContextSettingsForRequest(resolvedChatModelConfigIdOverride)

        try {
            messageProcessingDelegate.regenerateAiMessageVariant(
                chatId = chatId,
                targetMessageTimestamp = targetMessage.timestamp,
                requestMessageContent = requestMessageContent,
                requestHistory = requestHistory,
                workspacePath = workspacePath,
                promptFunctionType = currentPromptFunctionType,
                roleCardId = roleCardId,
                currentRoleName = currentRoleName,
                enableThinking = apiConfigDelegate.enableThinkingMode.value,
                enableMemoryAutoUpdate = apiConfigDelegate.enableMemoryAutoUpdate.value,
                maxTokens =
                    (chatContextSettings.effectiveContextLength * 1024)
                        .toLong()
                        .coerceIn(0L, Int.MAX_VALUE.toLong())
                        .toInt(),
                tokenUsageThreshold = chatContextSettings.summaryTokenThreshold.toDouble(),
                chatModelConfigIdOverride = resolvedChatModelConfigIdOverride,
                chatModelIndexOverride = resolvedChatModelIndexOverride,
                preferenceProfileIdOverride = resolvedPreferenceProfileIdOverride,
                groupOrchestrationMode = groupOrchestrationMode,
                groupParticipantNamesText = groupParticipantNamesText,
                onVariantPreviewStarted = { previewMessage ->
                    chatHistoryDelegate.addMessageToChat(
                        previewMessage.copy(
                            content = "",
                            selectedVariantIndex = targetMessage.variantCount,
                            variantCount = targetMessage.variantCount + 1,
                            isVariantPreview = true,
                        ),
                        chatIdOverride = chatId,
                    )
                },
                onVariantReady = { variantMessage ->
                    chatHistoryDelegate.addMessageVariant(
                        timestamp = targetMessage.timestamp,
                        message = variantMessage,
                        chatIdOverride = chatId,
                    )
                    runCatching {
                        refreshStableContextWindow(
                            chatId = chatId,
                            roleCardId = roleCardId,
                            groupOrchestrationMode = groupOrchestrationMode,
                            groupParticipantNamesText = groupParticipantNamesText,
                            chatModelConfigIdOverride = resolvedChatModelConfigIdOverride,
                            chatModelIndexOverride = resolvedChatModelIndexOverride,
                            preferenceProfileIdOverride = resolvedPreferenceProfileIdOverride
                        )
                    }.onFailure {
                        AppLogger.w(TAG, "单条重新生成后刷新上下文窗口失败", it)
                    }
                },
            )
        } catch (e: Exception) {
            if (chatHistoryDelegate.currentChatId.value == chatId) {
                runCatching {
                    chatHistoryDelegate.addMessageToChat(
                        targetMessage.copy(
                            contentStream = null,
                            isVariantPreview = true,
                        ),
                        chatIdOverride = chatId,
                    )
                }.onFailure {
                    AppLogger.w(TAG, "单条重新生成失败后恢复聊天显示失败", it)
                }
            }
            throw e
        }
    }

    /**
     * 内部发送消息的逻辑
     */
    private suspend fun sendMessageInternal(
        promptFunctionType: PromptFunctionType,
        isContinuation: Boolean = false,
        skipSummaryCheck: Boolean = false,
        isAutoContinuation: Boolean = false,
        roleCardIdOverride: String? = null,
        chatIdOverride: String? = null,
        messageTextOverride: String? = null,
        proxySenderNameOverride: String? = null,
        chatModelConfigIdOverride: String? = null,
        chatModelIndexOverride: Int? = null,
        preferenceProfileIdOverride: String? = null,
        composerSnapshot: ChatComposerSnapshot? = null,
        suppressUserMessageInHistory: Boolean = false,
        historyMessageTimestampsToExclude: Set<Long> = emptySet(),
        forceDisableSummary: Boolean = false,
        enableGroupOrchestration: Boolean = true,
        enableGroupOrchestrationForBoundText: Boolean = false,
        isGroupOrchestrationTurn: Boolean = false,
        groupParticipantNamesText: String? = null,
        turnOptions: ChatTurnOptions = ChatTurnOptions(),
        onDispatchResolved: ((Boolean) -> Unit)? = null,
    ) {
        // 如果不是自动续写，更新当前的 promptFunctionType
        if (!isAutoContinuation) {
            currentPromptFunctionType = promptFunctionType
        }
        val isBackgroundSend =
            !chatIdOverride.isNullOrBlank() && chatIdOverride != chatHistoryDelegate.currentChatId.value
        // 获取当前聊天ID和工作区路径
        val chatId = chatIdOverride ?: chatHistoryDelegate.currentChatId.value
        if (chatId == null) {
            uiStateDelegate.showErrorMessage(context.getString(R.string.chat_no_active_conversation))
            onDispatchResolved?.invoke(false)
            return
        }
        if (!isAutoContinuation) {
            cancelPendingAutoContinuation(chatId, restoreIdleIfPendingState = false)
        }
        if (
            turnOptions.persistTurn &&
            enableGroupOrchestration &&
            shouldRunGroupOrchestration(
                promptFunctionType = promptFunctionType,
                isContinuation = isContinuation,
                isAutoContinuation = isAutoContinuation,
                skipSummaryCheck = skipSummaryCheck,
                roleCardIdOverride = roleCardIdOverride,
                proxySenderNameOverride = proxySenderNameOverride,
                messageTextOverride = messageTextOverride,
                chatIdOverride = chatIdOverride,
                composerSnapshot = composerSnapshot,
                allowBoundUserText = enableGroupOrchestrationForBoundText,
            )
        ) {
            val handled = try {
                orchestrateGroupConversation(
                    chatId = chatId,
                    promptFunctionType = promptFunctionType,
                    turnOptions = turnOptions,
                    composerSnapshot = composerSnapshot,
                    messageTextOverride = messageTextOverride,
                    userMessageAlreadyPersisted = suppressUserMessageInHistory,
                    historyMessageTimestampsToExclude = historyMessageTimestampsToExclude,
                    onFirstDispatchResolved = onDispatchResolved,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (throwable: Throwable) {
                AppLogger.e(TAG, "群组编排失败，回退普通发送", throwable)
                false
            }
            if (!handled) {
                sendMessageInternal(
                    promptFunctionType = promptFunctionType,
                    isContinuation = isContinuation,
                    skipSummaryCheck = skipSummaryCheck,
                    isAutoContinuation = isAutoContinuation,
                    roleCardIdOverride = roleCardIdOverride,
                    chatIdOverride = chatIdOverride,
                    messageTextOverride = messageTextOverride,
                    proxySenderNameOverride = proxySenderNameOverride,
                    chatModelConfigIdOverride = chatModelConfigIdOverride,
                    chatModelIndexOverride = chatModelIndexOverride,
                    composerSnapshot = composerSnapshot,
                    suppressUserMessageInHistory = suppressUserMessageInHistory,
                    historyMessageTimestampsToExclude = historyMessageTimestampsToExclude,
                    forceDisableSummary = forceDisableSummary,
                    enableGroupOrchestration = false,
                    enableGroupOrchestrationForBoundText = false,
                    turnOptions = turnOptions,
                    onDispatchResolved = onDispatchResolved,
                )
            }
            return
        }
        val currentChat = chatHistoryDelegate.chatHistories.value.find { it.id == chatId }
        val workspacePath = currentChat?.workspace
        val workspaceEnv = currentChat?.workspaceEnv

        if (!isBackgroundSend) {
            // 更新本地Web服务器的聊天ID
            uiBridge.updateWebServerForCurrentChat(chatId)
        }

        // 获取当前附件列表
        val currentAttachments =
            composerSnapshot?.attachments
                ?: if (isBackgroundSend) emptyList() else attachmentDelegate.attachments.value
        val currentReplyToMessage =
            if (composerSnapshot != null) {
                composerSnapshot.replyToMessage
            } else if (isBackgroundSend) {
                null
            } else {
                uiBridge.getReplyToMessage()
            }
        // 角色卡和群组地位相等，都可以为 null，优先使用 override，否则使用当前活跃的角色卡（可能为 null）
        val roleCardId = roleCardIdOverride?.takeIf { it.isNotBlank() }
            ?: activePromptManager.resolveActiveCardIdForSend()
        val resolvedOverrides = try {
            if (promptFunctionType == PromptFunctionType.CHAT) {
                val (resolvedChatModelConfigIdOverride, resolvedChatModelIndexOverride) =
                    when {
                        !chatModelConfigIdOverride.isNullOrBlank() -> {
                            Pair(chatModelConfigIdOverride, (chatModelIndexOverride ?: 0).coerceAtLeast(0))
                        }
                        isAutoContinuation -> {
                            Pair(currentChatModelConfigIdOverride, currentChatModelIndexOverride)
                        }
                        else -> {
                            resolveRoleCardChatModelOverrides(roleCardId)
                        }
                    }
                val resolvedPreferenceProfileIdOverride =
                    when {
                        !preferenceProfileIdOverride.isNullOrBlank() -> preferenceProfileIdOverride
                        isAutoContinuation -> currentPreferenceProfileIdOverride
                        else -> roleCardId?.let { resolveRoleCardMemoryProfileOverride(it) }
                    }
                Triple(
                    resolvedChatModelConfigIdOverride,
                    resolvedChatModelIndexOverride,
                    resolvedPreferenceProfileIdOverride
                )
            } else {
                Triple(null, null, null)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "解析角色卡对话模型绑定失败", e)
            uiStateDelegate.showErrorMessage(
                e.message ?: context.getString(R.string.role_card_chat_model_binding_parse_failed)
            )
            onDispatchResolved?.invoke(false)
            return
        }
        val resolvedChatModelConfigIdOverride = resolvedOverrides.first
        val resolvedChatModelIndexOverride = resolvedOverrides.second
        val resolvedPreferenceProfileIdOverride = resolvedOverrides.third
        val chatContextSettings =
            resolveChatContextSettingsForRequest(resolvedChatModelConfigIdOverride)

        if (!isAutoContinuation) {
            currentChatModelConfigIdOverride = resolvedChatModelConfigIdOverride
            currentChatModelIndexOverride = resolvedChatModelIndexOverride
            currentPreferenceProfileIdOverride = resolvedPreferenceProfileIdOverride
        }

        // 当前请求使用的Token使用率阈值，默认使用配置值
        var tokenUsageThresholdForSend = chatContextSettings.summaryTokenThreshold.toDouble()
        val maxTokensForSend =
            (chatContextSettings.effectiveContextLength * 1024)
                .toLong()
                .coerceIn(0L, Int.MAX_VALUE.toLong())
                .toInt()

        // 如果不是续写，检查是否需要总结
        if (turnOptions.persistTurn && !isBackgroundSend && !isContinuation && !skipSummaryCheck) {
            val currentMessages = chatHistoryDelegate.getCurrentRuntimeChatHistorySnapshot()
            val currentTokens = tokenStatsDelegate.currentWindowSizeFlow.value

            val isShouldGenerateSummary = AIMessageManager.shouldGenerateSummary(
                messages = currentMessages,
                currentTokens = currentTokens.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                maxTokens = maxTokensForSend,
                tokenUsageThreshold = tokenUsageThresholdForSend,
                enableSummary = chatContextSettings.enableSummary,
                enableSummaryByMessageCount = chatContextSettings.enableSummaryByMessageCount,
                summaryMessageCountThreshold = chatContextSettings.summaryMessageCountThreshold
            )

            if (isShouldGenerateSummary) {
                val snapshotMessages = currentMessages.toList()
                val compactionPlan = HistoryCompactionPlanner.plan(snapshotMessages)
                if (compactionPlan == null) {
                    AppLogger.d(TAG, "历史仍在受保护的近期窗口内，跳过本次异步总结")
                } else {
                    // 异步生成总结，不阻塞当前消息发送
                    launchAsyncSummaryForSend(
                        compactionPlan = compactionPlan,
                        originalChatId = chatId,
                        roleCardId = roleCardId,
                        chatModelConfigIdOverride = resolvedChatModelConfigIdOverride,
                        chatModelIndexOverride = resolvedChatModelIndexOverride,
                        preferenceProfileIdOverride = resolvedPreferenceProfileIdOverride
                    )

                    // 本次请求的Token阈值在原基础上增加 0.5
                    tokenUsageThresholdForSend += 0.5
                }
            }
        }

        val proxySenderName = proxySenderNameOverride?.takeIf { it.isNotBlank() }

        // 如果是proxy sender，视为关闭记忆自动更新
        val shouldEnableMemoryAutoUpdate =
            turnOptions.memoryAutoUpdateOverride
                ?: if (proxySenderName.isNullOrBlank()) {
                    apiConfigDelegate.enableMemoryAutoUpdate.value
                } else {
                    false
                }

        // 调用messageProcessingDelegate发送消息，并传递附件信息和工作区路径
        val processingAccepted = messageProcessingDelegate.sendUserMessage(
            attachments = currentAttachments,
            chatId = chatId,
            messageTextOverride = messageTextOverride,
            proxySenderNameOverride = proxySenderName,
            workspacePath = workspacePath,
            workspaceEnv = workspaceEnv,
            promptFunctionType = promptFunctionType,
            roleCardId = roleCardId,
            enableThinking = apiConfigDelegate.enableThinkingMode.value,
            enableMemoryAutoUpdate = shouldEnableMemoryAutoUpdate,
            maxTokens = maxTokensForSend,
            tokenUsageThreshold = tokenUsageThresholdForSend,
            replyToMessage = currentReplyToMessage,
            isAutoContinuation = isAutoContinuation,
            enableSummary = !forceDisableSummary && !isBackgroundSend && chatContextSettings.enableSummary,
            chatModelConfigIdOverride = resolvedChatModelConfigIdOverride,
            chatModelIndexOverride = resolvedChatModelIndexOverride,
            preferenceProfileIdOverride = resolvedPreferenceProfileIdOverride,
            suppressUserMessageInHistory = suppressUserMessageInHistory,
            historyMessageTimestampsToExclude = historyMessageTimestampsToExclude,
            isGroupOrchestrationTurn = isGroupOrchestrationTurn,
            groupParticipantNamesText = groupParticipantNamesText,
            onUserMessagePersisted =
                if (!isContinuation && composerSnapshot != null) {
                    {
                        consumeComposerAfterDispatch(
                            chatId = chatId,
                            composerSnapshot = composerSnapshot,
                            attachments = currentAttachments,
                            replyToMessage = currentReplyToMessage,
                            isBackgroundSend = chatHistoryDelegate.currentChatId.value != chatId,
                        )
                    }
                } else {
                    null
                },
            turnOptions = turnOptions
        )

        if (!processingAccepted) {
            AppLogger.w(
                TAG,
                "Response pipeline rejected dispatch after coordination: chatId=$chatId",
            )
            onDispatchResolved?.invoke(false)
            return
        }
        onDispatchResolved?.invoke(true)

        // 只有在非续写（即用户主动发送）时才清空附件和UI状态
        if (!isContinuation && composerSnapshot == null) {
            consumeComposerAfterDispatch(
                chatId = chatId,
                composerSnapshot = composerSnapshot,
                attachments = currentAttachments,
                replyToMessage = currentReplyToMessage,
                isBackgroundSend = isBackgroundSend,
            )
        }
    }

    private fun consumeComposerAfterDispatch(
        chatId: String,
        composerSnapshot: ChatComposerSnapshot?,
        attachments: List<com.ai.assistance.operit.data.model.AttachmentInfo>,
        replyToMessage: ChatMessage?,
        isBackgroundSend: Boolean,
    ) {
        if (composerSnapshot != null) {
            uiBridge.consumeComposerSnapshot(chatId, composerSnapshot)
            if (!isBackgroundSend) {
                attachmentDelegate.consumeAttachments(attachments)
                replyToMessage?.let(uiBridge::clearReplyToMessageIfMatches)
                uiBridge.resetAttachmentPanelState()
            }
            return
        }

        if (!isBackgroundSend) {
            attachmentDelegate.clearAttachments()
            uiBridge.resetAttachmentPanelState()
            uiBridge.clearReplyToMessage()
        }
    }

    private suspend fun shouldRunGroupOrchestration(
        promptFunctionType: PromptFunctionType,
        isContinuation: Boolean,
        isAutoContinuation: Boolean,
        skipSummaryCheck: Boolean,
        roleCardIdOverride: String?,
        proxySenderNameOverride: String?,
        messageTextOverride: String?,
        chatIdOverride: String?,
        composerSnapshot: ChatComposerSnapshot?,
        allowBoundUserText: Boolean,
    ): Boolean {
        if (promptFunctionType != PromptFunctionType.CHAT) return false
        if (isContinuation || isAutoContinuation || skipSummaryCheck) return false
        if (!roleCardIdOverride.isNullOrBlank()) return false
        if (!proxySenderNameOverride.isNullOrBlank()) return false
        if (
            !permitsGroupOrchestrationDispatch(
                hasComposerSnapshot = composerSnapshot != null,
                messageTextOverride = messageTextOverride,
                chatIdOverride = chatIdOverride,
                allowBoundUserText = allowBoundUserText,
            )
        ) return false
        val activePrompt = activePromptManager.getActivePrompt()
        if (activePrompt !is ActivePrompt.CharacterGroup) return false
        return true
    }

    private suspend fun orchestrateGroupConversation(
        chatId: String,
        promptFunctionType: PromptFunctionType,
        turnOptions: ChatTurnOptions,
        composerSnapshot: ChatComposerSnapshot? = null,
        messageTextOverride: String? = null,
        userMessageAlreadyPersisted: Boolean = false,
        historyMessageTimestampsToExclude: Set<Long> = emptySet(),
        onFirstDispatchResolved: ((Boolean) -> Unit)? = null,
    ): Boolean {
        val group = resolveTargetGroupForChat(chatId) ?: return false

        val orderedMembers = group.members
            .sortedBy { it.orderIndex }
            .filter { it.characterCardId.isNotBlank() }
        AppLogger.d(
            TAG,
            "回答规划: plan=${group.id}, members=${group.members.size}, activeMembers=${orderedMembers.size}"
        )
        if (orderedMembers.isEmpty()) {
            return false
        }

        val existingBinding = chatHistoryDelegate.chatHistories.value
            .firstOrNull { it.id == chatId }
            ?.characterGroupId
        if (existingBinding != group.id) {
            chatHistoryDelegate.updateChatCharacterBinding(chatId, null, group.id)
        }

        val originalUserText =
            (
                composerSnapshot?.text
                    ?: messageTextOverride
                    ?: messageProcessingDelegate.userMessage.value.text
            ).trim()
        val attachments = composerSnapshot?.attachments ?: attachmentDelegate.attachments.value
        val replyToMessage =
            if (composerSnapshot != null) composerSnapshot.replyToMessage else uiBridge.getReplyToMessage()
        val hasAttachments = attachments.isNotEmpty()
        AppLogger.d(
            TAG,
            "群组编排输入: chatId=$chatId, userTextLength=${originalUserText.length}, hasAttachments=$hasAttachments"
        )
        if (originalUserText.isBlank() && !hasAttachments) {
            AppLogger.d(TAG, "群组编排终止: 输入为空且无附件")
            return false
        }
        messageProcessingDelegate.setInputProcessingStateForChat(
            chatId,
            InputProcessingState.Processing(context.getString(R.string.role_response_planner_planning))
        )

        val timeline = mutableListOf<Pair<String, String>>()
        if (originalUserText.isNotBlank()) {
            timeline.add(context.getString(R.string.message_role_user) to originalUserText)
        }

        val currentChat = chatHistoryDelegate.chatHistories.value.firstOrNull { it.id == chatId }
        val workspacePath = currentChat?.workspace
        val workspaceEnv = currentChat?.workspaceEnv
        val isFirstMessage = !chatHistoryDelegate.hasUserMessage(chatId)
        val titleFallback = if (isFirstMessage && !userMessageAlreadyPersisted) {
            fallbackConversationTitle(originalUserText, attachments).also { fallbackTitle ->
                chatHistoryDelegate.updateChatTitle(chatId, fallbackTitle)
            }
        } else {
            null
        }

        val finalUserMessageContent =
            messageProcessingDelegate.buildUserMessageContentForGroupOrchestration(
                messageText = originalUserText,
                attachments = attachments,
                workspacePath = workspacePath,
                workspaceEnv = workspaceEnv,
                replyToMessage = replyToMessage,
                chatId = chatId
            )
        val userMessage = ChatMessage(
            sender = "user",
            content = finalUserMessageContent,
            roleName = context.getString(R.string.message_role_user),
            displayMode =
                if (turnOptions.hideUserMessage) {
                    ChatMessageDisplayMode.HIDDEN_PLACEHOLDER
                } else {
                    ChatMessageDisplayMode.NORMAL
                }
        )
        if (!userMessageAlreadyPersisted) {
            chatHistoryDelegate.addMessageToChat(userMessage, chatId)
            if (turnOptions.consumeUserDraftAfterPersist && originalUserText.isNotBlank()) {
                messageProcessingDelegate.consumeUserMessageDraft(chatId, originalUserText)
            }
            consumeComposerAfterDispatch(
                chatId = chatId,
                composerSnapshot = composerSnapshot,
                attachments = attachments,
                replyToMessage = replyToMessage,
                isBackgroundSend = chatHistoryDelegate.currentChatId.value != chatId,
            )
        }
        titleFallback?.let { fallbackTitle ->
            launchConversationTitleGeneration(
                chatId = chatId,
                userText = originalUserText,
                attachments = attachments,
                fallbackTitle = fallbackTitle
            )
        }

        var userMessageInsertedForCurrentUserTurn = true
        val memberCardsById = orderedMembers
            .associate { member ->
                member.characterCardId to runCatching { characterCardManager.getCharacterCard(member.characterCardId) }.getOrNull()
            }
            .filterValues { it != null }
            .mapValues { it.value!! }
        val groupParticipantNamesText = buildGroupParticipantNamesText(
            members = orderedMembers,
            memberCardsById = memberCardsById
        )

        val plannedRounds = planResponseOrder(
            userText = originalUserText,
            members = orderedMembers,
            memberCardsById = memberCardsById
        ) ?: run {
            AppLogger.w(TAG, "回答规划失败，终止本轮群组编排")
            val message = context.getString(R.string.role_response_planner_failed)
            uiStateDelegate.showErrorMessage(message)
            messageProcessingDelegate.setInputProcessingStateForChat(
                chatId,
                InputProcessingState.Error(message)
            )
            onFirstDispatchResolved?.invoke(false)
            return true
        }

        val effectiveRounds =
            if (hasPlannedGroupSpeaker(plannedRounds.rounds.map { round -> round.map { it.speak } })) {
                plannedRounds.rounds
            } else {
                val fallbackMemberId =
                    orderedMembers.firstOrNull { member ->
                        memberCardsById.containsKey(member.characterCardId)
                    }?.characterCardId
                if (fallbackMemberId == null) {
                    val message = context.getString(R.string.role_response_planner_failed)
                    AppLogger.w(TAG, "回答规划无可用角色，终止本轮群组编排")
                    uiStateDelegate.showErrorMessage(message)
                    messageProcessingDelegate.setInputProcessingStateForChat(
                        chatId,
                        InputProcessingState.Error(message)
                    )
                    onFirstDispatchResolved?.invoke(false)
                    return true
                }
                AppLogger.w(TAG, "回答规划本轮全部跳过，回退到首个可用角色: member=$fallbackMemberId")
                listOf(listOf(PlannedMember(id = fallbackMemberId, speak = true)))
            }

        AppLogger.d(TAG, "回答规划完成: 共 ${effectiveRounds.size} 轮对话")

        // 执行多轮对话
        var hasDispatchedMember = false
        effectiveRounds.forEachIndexed { roundIndex, roundMembers ->
            AppLogger.d(TAG, "开始执行第 ${roundIndex + 1} 轮，成员数: ${roundMembers.size}")

            roundMembers.forEach { plannedMember ->
                if (!plannedMember.speak) {
                    AppLogger.d(TAG, "跳过成员: member=${plannedMember.id}")
                    return@forEach
                }

                val member = orderedMembers.firstOrNull { it.characterCardId == plannedMember.id }
                    ?: return@forEach
                val memberCard = runCatching { characterCardManager.getCharacterCard(member.characterCardId) }.getOrNull()
                    ?: return@forEach
                val memberName = memberCard.name

                messageProcessingDelegate.setInputProcessingStateForChat(
                    chatId,
                    InputProcessingState.Processing(
                        context.getString(R.string.role_response_planner_member_replying, memberName)
                    )
                )

                val beforeLastAiTimestamp =
                    chatHistoryDelegate.getRuntimeChatHistory(chatId)
                        .lastOrNull { it.sender == "ai" }
                        ?.timestamp
                        ?: Long.MIN_VALUE
                val targetTurnCounter = messageProcessingDelegate.getTurnCompleteCounter(chatId) + 1L

                // 第一轮第一个成员使用原始用户消息，其他使用空消息（不添加"继续"）
                val isFirstDispatchedMember = !hasDispatchedMember
                val memberMessage = if (isFirstDispatchedMember) {
                    originalUserText
                } else {
                    ""
                }

                AppLogger.d(
                    TAG,
                    "回答规划成员发送: round=${roundIndex + 1}, member=$memberName, targetTurnCounter=$targetTurnCounter, firstDispatch=$isFirstDispatchedMember, suppressUserMessage=$userMessageInsertedForCurrentUserTurn"
                )

                sendMessageInternal(
                    promptFunctionType = promptFunctionType,
                    isContinuation = !isFirstDispatchedMember,
                    skipSummaryCheck = true,
                    isAutoContinuation = false,
                    roleCardIdOverride = member.characterCardId,
                    chatIdOverride = chatId,
                    messageTextOverride = memberMessage,
                    proxySenderNameOverride = null,
                    chatModelConfigIdOverride = null,
                    chatModelIndexOverride = null,
                    suppressUserMessageInHistory = userMessageInsertedForCurrentUserTurn,
                    historyMessageTimestampsToExclude =
                        if (isFirstDispatchedMember) {
                            historyMessageTimestampsToExclude
                        } else {
                            emptySet()
                        },
                    forceDisableSummary = true,
                    enableGroupOrchestration = false,
                    isGroupOrchestrationTurn = true,
                    groupParticipantNamesText = groupParticipantNamesText,
                    turnOptions = turnOptions,
                    onDispatchResolved = onFirstDispatchResolved,
                )
                if (!messageProcessingDelegate.isChatLoading(chatId)) {
                    AppLogger.w(TAG, "Group member dispatch was not accepted: chatId=$chatId, member=$memberName")
                    onFirstDispatchResolved?.invoke(false)
                    return false
                }
                hasDispatchedMember = true
                userMessageInsertedForCurrentUserTurn = true

                val completed = awaitTurnComplete(chatId, targetTurnCounter)
                if (!completed) {
                    val currentCounter = messageProcessingDelegate.getTurnCompleteCounter(chatId)
                    AppLogger.w(
                        TAG,
                        "回答规划成员等待超时: member=$memberName, targetTurnCounter=$targetTurnCounter, currentTurnCounter=$currentCounter"
                    )
                    return@forEach
                }

                val newAiMessage = chatHistoryDelegate.getRuntimeChatHistory(chatId)
                    .asReversed()
                    .firstOrNull { it.sender == "ai" && it.timestamp > beforeLastAiTimestamp }
                if (newAiMessage != null && newAiMessage.content.isNotBlank()) {
                    val rawContent = newAiMessage.content
                    val effectiveSpeech = extractEffectiveSpeechContent(rawContent)
                    if (effectiveSpeech.isNotBlank()) {
                        timeline.add("AI($memberName)" to shrinkForMemberPrompt(effectiveSpeech))
                    } else {
                        AppLogger.w(TAG, "回答规划成员完成但消息为空: member=$memberName")
                    }
                } else {
                    AppLogger.w(TAG, "回答规划成员完成但未捕获到新AI消息: member=$memberName")
                }
            }
        }

        AppLogger.d(TAG, "群组编排结束: chatId=$chatId, timelineSize=${timeline.size}")
        maybeSummarizeAfterGroupRound(chatId, promptFunctionType)
        return true
    }

    private data class PlannedMember(
        val id: String,
        val speak: Boolean
    )

    private data class PlannedRounds(
        val rounds: List<List<PlannedMember>>
    )

    private suspend fun planResponseOrder(
        userText: String,
        members: List<com.ai.assistance.operit.data.model.GroupMemberConfig>,
        memberCardsById: Map<String, CharacterCard>
    ): PlannedRounds? {
        val service = getEnhancedAiService() ?: return null
        val plannerService = runCatching {
            service.getAIServiceForFunction(FunctionType.ROLE_RESPONSE_PLANNER)
        }.getOrNull() ?: return null

        val modelParameters = runCatching {
            plannerServiceManager.getModelParametersForFunction(FunctionType.ROLE_RESPONSE_PLANNER)
        }.getOrElse { emptyList<ModelParameter<*>>() }

        val memberLines = members.mapNotNull { member ->
            val card = memberCardsById[member.characterCardId] ?: return@mapNotNull null
            "- id: ${member.characterCardId}, name: ${card.name}"
        }.joinToString("\n")

        val prompt = FunctionalPrompts.buildGroupRoleResponsePlannerPrompt(
            memberLines = memberLines,
            userText = userText,
            useEnglish = false
        )

        val contentBuilder = StringBuilder()
        runCatching {
            val stream = plannerService.sendMessage(
                context = context,
                chatHistory = listOf(PromptTurn(kind = PromptTurnKind.USER, content = prompt)),
                modelParameters = modelParameters,
                enableThinking = false,
                stream = false,
                preserveThinkInHistory = false
            )
            stream.collect { chunk -> contentBuilder.append(chunk) }
        }.onFailure {
            AppLogger.e(TAG, "回答规划模型调用失败: ${it.message}", it)
            return null
        }

        val rawContent = ChatUtils.removeThinkingContent(contentBuilder.toString()).trim()
        return parsePlannedRounds(
            rawContent = rawContent,
            memberIds = memberCardsById.keys,
            memberNameToId = memberCardsById.values.associate { it.name.trim() to it.id }
        )
    }

    private fun parsePlannedRounds(
        rawContent: String,
        memberIds: Set<String>,
        memberNameToId: Map<String, String>
    ): PlannedRounds? {
        if (rawContent.isBlank()) return null
        val trimmed = rawContent.trim()
        val jsonText = when {
            trimmed.startsWith("{") && trimmed.endsWith("}") -> trimmed
            trimmed.contains("{") && trimmed.contains("}") -> {
                val start = trimmed.indexOf("{")
                val end = trimmed.lastIndexOf("}")
                if (start >= 0 && end > start) trimmed.substring(start, end + 1) else trimmed
            }
            else -> trimmed
        }

        fun resolveId(value: String?): String? {
            val trimmedValue = value?.trim().orEmpty()
            if (trimmedValue.isBlank()) return null
            if (memberIds.contains(trimmedValue)) return trimmedValue
            return memberNameToId[trimmedValue]
        }

        fun parseMemberFromJson(item: Any): PlannedMember? {
            return when (item) {
                is String -> {
                    val id = resolveId(item) ?: return null
                    PlannedMember(id, true)
                }
                is JSONObject -> {
                    val id = resolveId(
                        item.optString("id")
                            .ifBlank { item.optString("memberId") }
                            .ifBlank { item.optString("roleId") }
                            .ifBlank { item.optString("name") }
                    ) ?: return null
                    val skip = item.optBoolean("skip", false)
                    val speak = item.optBoolean("speak", !skip)
                    PlannedMember(id, speak)
                }
                else -> null
            }
        }

        return runCatching {
            val obj = JSONObject(jsonText)

            // 尝试解析新格式：{"rounds":[[...],[...]]}
            val roundsArray = obj.optJSONArray("rounds")
            if (roundsArray != null) {
                val rounds = mutableListOf<List<PlannedMember>>()
                for (i in 0 until roundsArray.length()) {
                    val roundArray = roundsArray.optJSONArray(i) ?: continue
                    val roundMembers = mutableListOf<PlannedMember>()
                    val seen = mutableSetOf<String>()

                    for (j in 0 until roundArray.length()) {
                        val member = parseMemberFromJson(roundArray.get(j)) ?: continue
                        if (seen.add(member.id)) {
                            roundMembers.add(member)
                        }
                    }

                    if (roundMembers.isNotEmpty()) {
                        rounds.add(roundMembers)
                    }
                }

                return@runCatching PlannedRounds(rounds)
            }

            // 兼容旧格式：{"order":[...]}
            val orderArray = obj.optJSONArray("order")
                ?: obj.optJSONArray("plan")
                ?: obj.optJSONArray("members")

            if (orderArray != null) {
                val members = mutableListOf<PlannedMember>()
                val seen = mutableSetOf<String>()

                for (i in 0 until orderArray.length()) {
                    val member = parseMemberFromJson(orderArray.get(i)) ?: continue
                    if (seen.add(member.id)) {
                        members.add(member)
                    }
                }

                return@runCatching PlannedRounds(listOf(members))
            }

            null
        }.getOrNull()
    }

    private suspend fun buildGroupParticipantNamesText(
        members: List<com.ai.assistance.operit.data.model.GroupMemberConfig>,
        memberCardsById: Map<String, CharacterCard>
    ): String {
        val useEnglish = LocaleUtils.getCurrentLanguage(context).lowercase().startsWith("en")
        val userName = displayPreferencesManager.globalUserName.first()?.trim().orEmpty()
        val formattedUserName = if (userName.isNotBlank()) {
            "$userName（用户）"
        } else {
            "用户（用户）"
        }
        val participantNames = members
            .sortedBy { it.orderIndex }
            .mapNotNull { member -> memberCardsById[member.characterCardId]?.name?.trim()?.takeIf { it.isNotBlank() } }
            .distinct() + formattedUserName
        return if (useEnglish) participantNames.joinToString(", ") else participantNames.joinToString("、")
    }

    private suspend fun buildBoundGroupParticipantNamesText(chatId: String): String? {
        val groupId = chatHistoryDelegate.chatHistories.value
            .firstOrNull { it.id == chatId }
            ?.characterGroupId
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val group = characterGroupCardManager.getCharacterGroupCard(groupId) ?: return null
        val memberCardsById = group.members
            .associate { member ->
                member.characterCardId to characterCardManager.getCharacterCard(member.characterCardId)
            }
        return buildGroupParticipantNamesText(
            members = group.members,
            memberCardsById = memberCardsById
        )
    }

    private suspend fun resolveTargetGroupForChat(chatId: String): com.ai.assistance.operit.data.model.CharacterGroupCard? {
        val activePrompt = activePromptManager.getActivePrompt()
        val activeGroupId = (activePrompt as? ActivePrompt.CharacterGroup)
            ?.id
            ?.takeIf { it.isNotBlank() }
        if (!activeGroupId.isNullOrBlank()) {
            return characterGroupCardManager.getCharacterGroupCard(activeGroupId)
        }

        val boundGroupId = chatHistoryDelegate.chatHistories.value
            .firstOrNull { it.id == chatId }
            ?.characterGroupId
            ?.takeIf { it.isNotBlank() }
        if (!boundGroupId.isNullOrBlank()) {
            AppLogger.d(
                TAG,
                "发送判定按当前选择执行，忽略会话绑定群组: chatId=$chatId, boundGroupId=$boundGroupId"
            )
        }
        return null
    }

    private fun extractEffectiveSpeechContent(content: String): String {
        val withoutThinking = ChatUtils.removeThinkingContent(content)
        val withoutStatus = ChatMarkupRegex.statusTag.replace(withoutThinking, " ")
        return ChatMarkupRegex.statusSelfClosingTag.replace(withoutStatus, " ").trim()
    }

    private fun shrinkForMemberPrompt(content: String, maxLength: Int = 220): String {
        val normalized = content.replace("\n", " ").trim()
        return if (normalized.length <= maxLength) normalized else normalized.take(maxLength) + "..."
    }

    private suspend fun awaitTurnComplete(
        chatId: String,
        targetCounter: Long,
        timeoutMs: Long = 180_000L
    ): Boolean {
        val start = System.currentTimeMillis()
        AppLogger.d(TAG, "等待回合完成: chatId=$chatId, targetCounter=$targetCounter, timeoutMs=$timeoutMs")
        val completed = withTimeoutOrNull(timeoutMs) {
            messageProcessingDelegate.turnCompleteCounterByChatId.first { counters ->
                (counters[chatId] ?: 0L) >= targetCounter
            }
            true
        } ?: false
        val elapsed = System.currentTimeMillis() - start
        AppLogger.d(
            TAG,
            "等待回合完成结果: chatId=$chatId, targetCounter=$targetCounter, completed=$completed, elapsedMs=$elapsed"
        )
        return completed
    }

    private fun isSamePendingAutoContinuation(
        chatId: String,
        request: PendingAutoContinuationRequest
    ): Boolean {
        return pendingAutoContinuationByChatId[chatId] === request
    }

    private fun restoreIdleIfCurrentlySummarizing(chatId: String) {
        val currentState = messageProcessingDelegate.inputProcessingStateByChatId.value[chatId]
        if (currentState is InputProcessingState.Summarizing) {
            messageProcessingDelegate.setInputProcessingStateForChat(chatId, InputProcessingState.Idle)
        }
    }

    private fun removePendingAutoContinuation(chatId: String): PendingAutoContinuationRequest? {
        return pendingAutoContinuationByChatId.remove(chatId)
    }

    private fun cancelPendingAutoContinuation(
        chatId: String,
        restoreIdleIfPendingState: Boolean
    ) {
        val removed = removePendingAutoContinuation(chatId) ?: return
        removed.waitJob?.cancel()
        messageProcessingDelegate.setSuppressIdleCompletedStateForChat(chatId, false)
        if (restoreIdleIfPendingState) {
            restoreIdleIfCurrentlySummarizing(chatId)
        }
        AppLogger.d(TAG, "已取消待派发的自动续聊: chatId=$chatId")
    }

    private fun queuePendingAutoContinuation(
        chatId: String,
        promptFunctionType: PromptFunctionType,
        chatModelConfigIdOverride: String?,
        chatModelIndexOverride: Int?,
        preferenceProfileIdOverride: String?,
        roleCardIdOverride: String? = null,
        isGroupOrchestrationTurn: Boolean = false,
        groupParticipantNamesText: String? = null
    ) {
        cancelPendingAutoContinuation(chatId, restoreIdleIfPendingState = false)
        messageProcessingDelegate.setSuppressIdleCompletedStateForChat(chatId, true)
        val request =
            PendingAutoContinuationRequest(
                chatId = chatId,
                promptFunctionType = promptFunctionType,
                chatModelConfigIdOverride = chatModelConfigIdOverride,
                chatModelIndexOverride = chatModelIndexOverride,
                preferenceProfileIdOverride = preferenceProfileIdOverride,
                roleCardIdOverride = roleCardIdOverride,
                isGroupOrchestrationTurn = isGroupOrchestrationTurn,
                groupParticipantNamesText = groupParticipantNamesText
            )
        pendingAutoContinuationByChatId[chatId] = request
        request.waitJob =
            coroutineScope.launch {
                try {
                    while (isSamePendingAutoContinuation(chatId, request)) {
                        if (!messageProcessingDelegate.isChatLoading(chatId)) {
                            break
                        }
                        val targetCounter = messageProcessingDelegate.getTurnCompleteCounter(chatId) + 1L
                        val completed = awaitTurnComplete(chatId, targetCounter)
                        if (!completed) {
                            AppLogger.w(TAG, "等待上一轮完成超时，取消自动续聊: chatId=$chatId")
                            if (isSamePendingAutoContinuation(chatId, request)) {
                                removePendingAutoContinuation(chatId)
                                messageProcessingDelegate.setSuppressIdleCompletedStateForChat(chatId, false)
                                restoreIdleIfCurrentlySummarizing(chatId)
                            }
                            return@launch
                        }
                    }
                    if (!isSamePendingAutoContinuation(chatId, request)) {
                        return@launch
                    }
                    AppLogger.d(TAG, "上一轮已完成，开始派发自动续聊: chatId=$chatId")
                    sendMessageInternal(
                        promptFunctionType = request.promptFunctionType,
                        isContinuation = true,
                        isAutoContinuation = true,
                        roleCardIdOverride = request.roleCardIdOverride,
                        chatIdOverride = chatId,
                        chatModelConfigIdOverride = request.chatModelConfigIdOverride,
                        chatModelIndexOverride = request.chatModelIndexOverride,
                        preferenceProfileIdOverride = request.preferenceProfileIdOverride,
                        isGroupOrchestrationTurn = request.isGroupOrchestrationTurn,
                        groupParticipantNamesText = request.groupParticipantNamesText
                    )
                    val started = messageProcessingDelegate.isChatLoading(chatId)
                    if (isSamePendingAutoContinuation(chatId, request)) {
                        removePendingAutoContinuation(chatId)
                        messageProcessingDelegate.setSuppressIdleCompletedStateForChat(chatId, false)
                    }
                    if (!started) {
                        AppLogger.w(TAG, "自动续聊派发后未启动发送，恢复Idle: chatId=$chatId")
                        restoreIdleIfCurrentlySummarizing(chatId)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.e(TAG, "派发自动续聊时出错: ${e.message}", e)
                    if (isSamePendingAutoContinuation(chatId, request)) {
                        removePendingAutoContinuation(chatId)
                        messageProcessingDelegate.setSuppressIdleCompletedStateForChat(chatId, false)
                        restoreIdleIfCurrentlySummarizing(chatId)
                    }
                }
            }
        AppLogger.d(TAG, "已排队自动续聊，等待当前回合完全结束: chatId=$chatId")
    }

    private suspend fun maybeSummarizeAfterGroupRound(
        chatId: String,
        promptFunctionType: PromptFunctionType
    ) {
        val chatContextSettings =
            resolveChatContextSettingsForRequest(currentChatModelConfigIdOverride)
        if (!chatContextSettings.enableSummary) return

        val currentMessages = chatHistoryDelegate.getRuntimeChatHistory(chatId)
        val currentTokens = tokenStatsDelegate.getLastCurrentWindowSize(chatId)
        val maxTokens =
            (chatContextSettings.effectiveContextLength * 1024)
                .toLong()
                .coerceIn(0L, Int.MAX_VALUE.toLong())
                .toInt()
        val shouldSummarize = AIMessageManager.shouldGenerateSummary(
            messages = currentMessages,
            currentTokens = currentTokens.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            maxTokens = maxTokens,
            tokenUsageThreshold = chatContextSettings.summaryTokenThreshold.toDouble(),
            enableSummary = chatContextSettings.enableSummary,
            enableSummaryByMessageCount = chatContextSettings.enableSummaryByMessageCount,
            summaryMessageCountThreshold = chatContextSettings.summaryMessageCountThreshold
        )
        if (shouldSummarize) {
            // 群组编排后的总结，标记为群聊模式
            summarizeHistory(
                autoContinue = false,
                promptFunctionType = promptFunctionType,
                chatIdOverride = chatId,
                chatModelConfigIdOverride = currentChatModelConfigIdOverride,
                chatModelIndexOverride = currentChatModelIndexOverride,
                isGroupChat = true
            )
        }
    }

    private suspend fun resolveRoleCardChatModelOverrides(roleCardId: String): Pair<String?, Int?> {
        val roleCard = characterCardManager.getCharacterCardFlow(roleCardId).first()
        val bindingMode = CharacterCardChatModelBindingMode.normalize(roleCard.chatModelBindingMode)
        return if (
            bindingMode == CharacterCardChatModelBindingMode.FIXED_CONFIG &&
            !roleCard.chatModelConfigId.isNullOrBlank()
        ) {
            Pair(roleCard.chatModelConfigId, roleCard.chatModelIndex.coerceAtLeast(0))
        } else {
            Pair(null, null)
        }
    }

    private suspend fun resolveRoleCardMemoryProfileOverride(roleCardId: String): String? {
        val roleCard = characterCardManager.getCharacterCardFlow(roleCardId).first()
        val bindingMode =
            CharacterCardMemoryProfileBindingMode.normalize(roleCard.memoryProfileBindingMode)
        return if (
            bindingMode == CharacterCardMemoryProfileBindingMode.FIXED_PROFILE &&
            !roleCard.memoryProfileId.isNullOrBlank()
        ) {
            roleCard.memoryProfileId
        } else {
            null
        }
    }

    private suspend fun resolveCompanionMemoryEnqueueTarget(
        chatId: String,
        characterGroupId: String?,
        characterCardName: String?,
    ): CompanionMemoryEnqueueTarget? {
        // Only the chat binding captured by the user action may own these candidates;
        // consulting the later active prompt here could move old evidence to another persona.
        val roleCardId =
            if (characterGroupId.isNullOrBlank()) {
                characterCardName
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { characterCardManager.findCharacterCardByName(it)?.id }
            } else {
                null
            }
        val companionId =
            CompanionMemoryTargetResolver.snapshotForTurn(
                characterGroupId = characterGroupId,
                roleCardId = roleCardId,
            )
        if (companionId.isBlank()) {
            AppLogger.w(
                TAG,
                "Skipping companion-memory enqueue because the selected chat has no " +
                    "resolvable target: chatId=$chatId",
            )
            return null
        }
        return CompanionMemoryEnqueueTarget(
            companionId = companionId,
            roleCardId = roleCardId,
        )
    }

    private suspend fun resolveChatContextSettingsForRequest(
        chatModelConfigIdOverride: String?
    ): ChatContextSettings {
        return apiConfigDelegate.resolveChatContextSettings(chatModelConfigIdOverride)
    }

    /**
     * 手动更新记忆
     */
    fun manuallyUpdateMemory() {
        if (_isUpdatingMemory.value) {
            uiStateDelegate.showToast(context.getString(R.string.chat_summarizing_memory))
            return
        }
        // Freeze the selected chat before launching so a navigation change cannot retarget memory.
        val currentChatId = chatHistoryDelegate.currentChatId.value
        val currentChatSnapshot =
            currentChatId?.let { chatId ->
                chatHistoryDelegate.chatHistories.value.firstOrNull { it.id == chatId }
            }
        coroutineScope.launch {
            val runtimeHistory =
                currentChatId?.let { chatHistoryDelegate.getRuntimeChatHistory(it) }.orEmpty()
            saveMessagesToMemory(
                sourceMessages = runtimeHistory,
                currentChatId = currentChatId,
                emptyToastMessage = context.getString(R.string.chat_history_empty_no_update),
                characterGroupIdSnapshot = currentChatSnapshot?.characterGroupId,
                characterCardNameSnapshot = currentChatSnapshot?.characterCardName,
            )
        }
    }

    fun enqueueSelectedMessagesForMemoryAutoSave(selectedMessages: List<ChatMessage>) {
        // Selection belongs to the chat visible at click time, even if the coroutine runs later.
        val currentChatId = chatHistoryDelegate.currentChatId.value
        val currentChatSnapshot =
            currentChatId?.let { chatId ->
                chatHistoryDelegate.chatHistories.value.firstOrNull { it.id == chatId }
            }
        coroutineScope.launch {
            val userMessages = selectedMessages.validUserMessagesForMemory()

            if (currentChatId.isNullOrBlank()) {
                uiStateDelegate.showToast(context.getString(R.string.chat_history_empty_no_update))
                return@launch
            }
            if (userMessages.isEmpty()) {
                uiStateDelegate.showToast(
                    context.getString(R.string.chat_selected_messages_no_user_for_memory)
                )
                return@launch
            }

            try {
                val target =
                    resolveCompanionMemoryEnqueueTarget(
                        chatId = currentChatId,
                        characterGroupId = currentChatSnapshot?.characterGroupId,
                        characterCardName = currentChatSnapshot?.characterCardName,
                    ) ?: return@launch
                val profileId =
                    target.roleCardId?.let { resolveRoleCardMemoryProfileOverride(it) }
                        ?: preferencesManager.activeProfileIdFlow.first()
                MemoryAutoSaveCandidateRepository(context, profileId)
                    .enqueueSelectedUserMessages(
                        chatId = currentChatId,
                        companionId = target.companionId,
                        triggerMessageTimestamps = userMessages.map { it.timestamp }
                    )
                MemoryAutoSaveScheduler.getInstance()
                    ?.requestImmediateProcessing(profileId, currentChatId)
                uiStateDelegate.showToast(
                    context.getString(
                        R.string.chat_selected_messages_added_to_memory_queue,
                        userMessages.size
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(TAG, "选中消息加入自动记忆队列失败", e)
                uiStateDelegate.showToast(
                    context.getString(
                        R.string.chat_selected_messages_add_to_memory_queue_failed,
                        e.message ?: ""
                    )
                )
            }
        }
    }

    private suspend fun saveMessagesToMemory(
        sourceMessages: List<ChatMessage>,
        currentChatId: String?,
        emptyToastMessage: String,
        lastContentOverride: String? = null,
        characterGroupIdSnapshot: String? = null,
        characterCardNameSnapshot: String? = null,
    ) {
        val enhancedAiService = getEnhancedAiService()
        if (enhancedAiService == null) {
            uiStateDelegate.showToast(context.getString(R.string.chat_ai_service_unavailable_memory))
            return
        }
        if (sourceMessages.isEmpty()) {
            uiStateDelegate.showToast(emptyToastMessage)
            return
        }

        _isUpdatingMemory.value = true
        uiStateDelegate.showToast(context.getString(R.string.chat_summarizing_memory))

        try {
            val history = sourceMessages.map { it.sender to it.content }
            val lastMessageContent =
                lastContentOverride?.takeIf { it.isNotBlank() }
                    ?: sourceMessages.lastOrNull()?.content
                    ?: ""
            val target =
                currentChatId?.let { chatId ->
                    resolveCompanionMemoryEnqueueTarget(
                        chatId = chatId,
                        characterGroupId = characterGroupIdSnapshot,
                        characterCardName = characterCardNameSnapshot,
                    )
                }
            val roleCardId = target?.roleCardId
            val preferenceProfileIdOverride =
                roleCardId?.let { resolveRoleCardMemoryProfileOverride(it) }

            if (!currentChatId.isNullOrBlank() && target != null) {
                val profileId =
                    preferenceProfileIdOverride
                        ?: preferencesManager.activeProfileIdFlow.first()
                val queuedCount =
                    MemoryAutoSaveCandidateRepository(context, profileId)
                        .enqueueSelectedUserMessages(
                            chatId = currentChatId,
                            companionId = target.companionId,
                            triggerMessageTimestamps =
                                sourceMessages.validUserMessagesForMemory().map { it.timestamp }
                        )
                if (queuedCount > 0) {
                    AppLogger.d(
                        TAG,
                        "Manual memory save queued $queuedCount companion-memory candidates " +
                            "for chatId=$currentChatId, profileId=$profileId"
                    )
                }
                MemoryAutoSaveScheduler.getInstance()
                    ?.requestImmediateProcessing(profileId, currentChatId)
            }

            enhancedAiService.saveConversationToMemoryAsync(
                conversationHistory = history,
                lastContent = lastMessageContent,
                preferenceProfileIdOverride = preferenceProfileIdOverride,
                chatId = currentChatId,
                onSuccess = {
                    uiStateDelegate.showToast(context.getString(R.string.chat_memory_manually_updated))
                    _isUpdatingMemory.value = false
                },
                onError = { e ->
                    AppLogger.e(TAG, "手动更新记忆失败", e)
                    uiStateDelegate.showToast(
                        context.getString(
                            R.string.chat_manual_update_memory_failed,
                            e.message ?: ""
                        )
                    )
                    _isUpdatingMemory.value = false
                }
            )
        } catch (e: CancellationException) {
            _isUpdatingMemory.value = false
            throw e
        } catch (e: Exception) {
            _isUpdatingMemory.value = false
            AppLogger.e(TAG, "手动更新记忆失败", e)
            uiStateDelegate.showToast(
                context.getString(
                    R.string.chat_manual_update_memory_failed,
                    e.message ?: ""
                )
            )
        }
    }

    /**
     * 手动触发对话总结
     */
    fun manuallySummarizeConversation() {
        if (_isSummarizing.value) {
            uiStateDelegate.showToast(context.getString(R.string.chat_summarizing_please_wait))
            return
        }
        coroutineScope.launch {
            val currentChatId = chatHistoryDelegate.currentChatId.value
            val success =
                summarizeHistory(
                    autoContinue = false,
                    chatIdOverride = currentChatId,
                    isGroupChat = isGroupChatSession(currentChatId)
                )
            if (success) {
                uiStateDelegate.showToast(context.getString(R.string.chat_conversation_summary_generated))
            }
        }
    }

    /**
     * 处理Token超限的情况，触发一次历史总结并继续。
     */
    fun handleTokenLimitExceeded(
        chatId: String?,
        roleCardId: String?,
        isGroupOrchestrationTurn: Boolean,
        groupParticipantNamesText: String?
    ) {
        AppLogger.d(TAG, "接收到Token超限信号，开始执行总结并继续...")
        summaryJob = coroutineScope.launch {
            summarizeHistory(
                autoContinue = true,
                chatIdOverride = chatId,
                roleCardIdOverride = roleCardId,
                isGroupChat = isGroupChatSession(chatId),
                isGroupOrchestrationTurn = isGroupOrchestrationTurn,
                groupParticipantNamesText = groupParticipantNamesText
            )
            summaryJob = null
        }
    }

    private suspend fun cancelSummaryStreamingInternal() {
        runCatching {
            getEnhancedAiService()
                ?.getAIServiceForFunction(FunctionType.SUMMARY)
                ?.cancelStreaming()
        }.onFailure { throwable ->
            AppLogger.w(TAG, "取消 SUMMARY 流失败: ${throwable.message}")
        }
        ToolProgressBus.clear()
    }

    /**
     * 取消正在进行的总结操作
     */
    private suspend fun cancelSummaryInternal(targetChatId: String? = null) {
        val currentChatId = targetChatId ?: chatHistoryDelegate.currentChatId.value
        val shouldCancelSummary =
            _isSummarizing.value &&
                (targetChatId == null || _summarizingChatId.value == targetChatId)
        val shouldCancelAsyncSummary =
            _isSendTriggeredSummarizing.value &&
                (targetChatId == null || _sendTriggeredSummarizingChatId.value == targetChatId)
        val shouldCancelPendingAutoContinuation =
            if (targetChatId != null) {
                pendingAutoContinuationByChatId.containsKey(targetChatId)
            } else {
                currentChatId != null && pendingAutoContinuationByChatId.containsKey(currentChatId)
            }
        val shouldCancelCurrentSummarizingUi =
            currentChatId != null &&
                messageProcessingDelegate.inputProcessingStateByChatId.value[currentChatId] is InputProcessingState.Summarizing

        if (!shouldCancelSummary &&
            !shouldCancelAsyncSummary &&
            !shouldCancelPendingAutoContinuation &&
            !shouldCancelCurrentSummarizingUi
        ) {
            if (targetChatId == null) {
                // 兜住尚未被协调层标记，但底层 SUMMARY 请求仍在执行的场景。
                cancelSummaryStreamingInternal()
            }
            return
        }

        AppLogger.d(TAG, "取消正在进行的总结操作: chatId=$targetChatId")

        val affectedChatIds = linkedSetOf<String>()
        val jobsToCancel = linkedSetOf<Job>()
        var cancelledSendTriggeredSummaryJob: Job? = null

        if (shouldCancelSummary) {
            _summarizingChatId.value?.let { affectedChatIds.add(it) }
            summaryJob?.let { jobsToCancel.add(it) }
            summaryJob = null
            _isSummarizing.value = false
            _summarizingChatId.value = null
        }

        if (shouldCancelAsyncSummary) {
            _sendTriggeredSummarizingChatId.value?.let { affectedChatIds.add(it) }
            cancelledSendTriggeredSummaryJob =
                synchronized(sendTriggeredSummaryLock) { sendTriggeredSummaryJob }
            cancelledSendTriggeredSummaryJob?.let { jobsToCancel.add(it) }
            _isSendTriggeredSummarizing.value = false
            _sendTriggeredSummarizingChatId.value = null
        }

        if (shouldCancelPendingAutoContinuation) {
            val pendingChatId = targetChatId ?: currentChatId
            if (!pendingChatId.isNullOrBlank()) {
                affectedChatIds.add(pendingChatId)
                pendingAutoContinuationByChatId[pendingChatId]?.waitJob?.let { jobsToCancel.add(it) }
                removePendingAutoContinuation(pendingChatId)
            }
        }

        if (shouldCancelCurrentSummarizingUi) {
            currentChatId?.let { affectedChatIds.add(it) }
        }

        // 先真正取消 SUMMARY 模型请求，再取消协程/清理 UI，避免进度继续推进。
        cancelSummaryStreamingInternal()

        jobsToCancel.forEach { job -> job.cancel() }
        jobsToCancel.forEach { job ->
            try {
                job.join()
            } catch (_: CancellationException) {
            }
        }

        messageProcessingDelegate.refreshGlobalLoadingState()
        affectedChatIds.forEach { chatId ->
            messageProcessingDelegate.setPendingAsyncSummaryUiForChat(chatId, false)
            messageProcessingDelegate.setSuppressIdleCompletedStateForChat(chatId, false)
            messageProcessingDelegate.setInputProcessingStateForChat(
                chatId,
                InputProcessingState.Idle
            )
        }
        cancelledSendTriggeredSummaryJob?.let { cancelledJob ->
            synchronized(sendTriggeredSummaryLock) {
                if (sendTriggeredSummaryJob === cancelledJob) {
                    sendTriggeredSummaryJob = null
                }
            }
        }
    }

    fun cancelSummary() {
        coroutineScope.launch {
            cancelSummaryInternal()
        }
    }

    fun cancelSummaryForChat(chatId: String) {
        coroutineScope.launch {
            cancelSummaryInternal(chatId)
        }
    }

    suspend fun cancelSummaryForDestructiveMutation(chatId: String) {
        cancelSummaryInternal(chatId)
    }

    private fun launchAsyncSummaryForSend(
        compactionPlan: HistoryCompactionPlan,
        originalChatId: String?,
        roleCardId: String?,
        chatModelConfigIdOverride: String? = null,
        chatModelIndexOverride: Int? = null,
        preferenceProfileIdOverride: String? = null
    ) {
        if (compactionPlan.summaryInputMessages.isEmpty() || originalChatId == null) {
            return
        }

        val asyncSummaryJob =
            synchronized(sendTriggeredSummaryLock) {
                if (sendTriggeredSummaryJob != null) {
                    AppLogger.d(
                        TAG,
                        "Async summary already running; skipped duplicate request for $originalChatId"
                    )
                    return
                }

                _isSendTriggeredSummarizing.value = true
                _sendTriggeredSummarizingChatId.value = originalChatId
                messageProcessingDelegate.setPendingAsyncSummaryUiForChat(originalChatId, true)
                messageProcessingDelegate.setSuppressIdleCompletedStateForChat(originalChatId, true)
                messageProcessingDelegate.setInputProcessingStateForChat(
                    originalChatId,
                    InputProcessingState.Summarizing(context.getString(R.string.chat_compressing_history))
                )

                coroutineScope.launch(start = CoroutineStart.LAZY) {
            try {
                val service = getEnhancedAiService() ?: return@launch

                // 检查是否是群聊
                val currentChat = chatHistoryDelegate.chatHistories.value.firstOrNull { it.id == originalChatId }
                val isGroupChat = currentChat?.characterGroupId != null

                flushMemoryBeforeCompaction(
                    chatId = originalChatId,
                    preferenceProfileIdOverride = preferenceProfileIdOverride
                )
                val summaryCustomRules = readSummaryCustomRules()
                val summaryMessage = AIMessageManager.summarizeMemory(
                    enhancedAiService = service,
                    messages = compactionPlan.summaryInputMessages,
                    autoContinue = false,
                    isGroupChat = isGroupChat,
                    summaryCustomRules = summaryCustomRules
                ) ?: return@launch

                val currentChatId = chatHistoryDelegate.currentChatId.value
                if (currentChatId != originalChatId) {
                    AppLogger.d(
                        TAG,
                        "Async summary skipped: chat switched from $originalChatId to $currentChatId"
                    )
                    return@launch
                }

                chatHistoryDelegate.addSummaryMessage(
                    summaryMessage = summaryMessage,
                    beforeTimestamp = compactionPlan.beforeTimestamp,
                    afterTimestamp = compactionPlan.afterTimestamp,
                    chatIdOverride = originalChatId,
                )
                AppLogger.d(
                    TAG,
                    "异步历史压缩完成: compacted=${compactionPlan.compactedMessageCount}, protected=${compactionPlan.protectedMessageCount}"
                )

                refreshStableContextWindow(
                    chatId = originalChatId,
                    roleCardId = roleCardId,
                    chatModelConfigIdOverride = chatModelConfigIdOverride,
                    chatModelIndexOverride = chatModelIndexOverride,
                    preferenceProfileIdOverride = preferenceProfileIdOverride
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(TAG, "Async summary during send failed: ${e.message}", e)
            } finally {
                val currentJob = coroutineContext[Job]
                val ownsSlot =
                    synchronized(sendTriggeredSummaryLock) {
                        currentJob != null &&
                            sendTriggeredSummaryJob === currentJob &&
                            _isSendTriggeredSummarizing.value
                    }
                if (ownsSlot) {
                    _isSendTriggeredSummarizing.value = false

                    if (_sendTriggeredSummarizingChatId.value == originalChatId) {
                        _sendTriggeredSummarizingChatId.value = null
                    }

                    messageProcessingDelegate.setPendingAsyncSummaryUiForChat(originalChatId, false)
                    messageProcessingDelegate.setSuppressIdleCompletedStateForChat(originalChatId, false)

                    val currentState =
                        messageProcessingDelegate.inputProcessingStateByChatId.value[originalChatId]
                    if (currentState is InputProcessingState.Summarizing) {
                        messageProcessingDelegate.setInputProcessingStateForChat(
                            originalChatId,
                            InputProcessingState.Idle
                        )
                    }
                    synchronized(sendTriggeredSummaryLock) {
                        if (sendTriggeredSummaryJob === currentJob) {
                            sendTriggeredSummaryJob = null
                        }
                    }
                }
            }
        }
                    .also { sendTriggeredSummaryJob = it }
            }
        asyncSummaryJob.start()
    }

    private suspend fun flushMemoryBeforeCompaction(
        chatId: String,
        preferenceProfileIdOverride: String?
    ) {
        val scheduler = MemoryAutoSaveScheduler.getInstance()
        if (scheduler == null) {
            AppLogger.d(TAG, "总结前记忆冲刷器尚未启动，原聊天记录继续作为 raw fallback")
            return
        }
        val profileId =
            preferenceProfileIdOverride?.takeIf { it.isNotBlank() }
                ?: preferencesManager.activeProfileIdFlow.first()
        val flushed =
            withTimeoutOrNull(15_000L) {
                scheduler.flushBeforeCompaction(profileId = profileId, chatId = chatId)
            }
        when (flushed) {
            true -> AppLogger.d(TAG, "总结前长期记忆冲刷完成: chatId=$chatId, profileId=$profileId")
            false -> AppLogger.w(TAG, "总结前长期记忆仍有待处理项，将由修复队列重试")
            null -> AppLogger.w(TAG, "总结前长期记忆冲刷超时，原聊天记录已保留")
        }
    }

    /**
     * 执行历史总结并自动继续对话的核心逻辑
     */
    private suspend fun summarizeHistory(
        autoContinue: Boolean = true,
        promptFunctionType: PromptFunctionType? = null,
        chatIdOverride: String? = null,
        chatModelConfigIdOverride: String? = null,
        chatModelIndexOverride: Int? = null,
        preferenceProfileIdOverride: String? = null,
        roleCardIdOverride: String? = null,
        isGroupChat: Boolean = false,
        isGroupOrchestrationTurn: Boolean = false,
        groupParticipantNamesText: String? = null
    ): Boolean {
        if (_isSummarizing.value) {
            AppLogger.d(TAG, "已在总结中，忽略本次请求")
            return false
        }
        _isSummarizing.value = true
        val currentChatId = chatIdOverride ?: chatHistoryDelegate.currentChatId.value
        _summarizingChatId.value = currentChatId
        if (currentChatId != null) {
            messageProcessingDelegate.setSuppressIdleCompletedStateForChat(currentChatId, true)
            messageProcessingDelegate.setInputProcessingStateForChat(
                currentChatId,
                InputProcessingState.Summarizing(context.getString(R.string.chat_compressing_history))
            )
        }
        val effectiveChatModelConfigIdOverride =
            chatModelConfigIdOverride ?: currentChatModelConfigIdOverride
        val effectiveChatModelIndexOverride =
            chatModelIndexOverride ?: currentChatModelIndexOverride
        val effectivePreferenceProfileIdOverride =
            preferenceProfileIdOverride
                ?: currentPreferenceProfileIdOverride
                ?: roleCardIdOverride?.let { resolveRoleCardMemoryProfileOverride(it) }
        val effectiveIsGroupChat = isGroupChat || isGroupChatSession(currentChatId)

        var summarySuccess = false
        try {
            val service = getEnhancedAiService()
            if (service == null) {
                uiStateDelegate.showErrorMessage(context.getString(R.string.chat_ai_service_unavailable_summarize))
                return false
            }

            val currentMessages =
                currentChatId?.let { chatHistoryDelegate.getRuntimeChatHistory(it) }.orEmpty()
            if (currentMessages.isEmpty()) {
                AppLogger.d(TAG, "历史记录为空，无需总结")
                return false
            }

            val compactionPlan = HistoryCompactionPlanner.plan(currentMessages)
            if (compactionPlan == null) {
                AppLogger.d(TAG, "历史仍在受保护的近期窗口内，无需总结")
                return false
            }
            if (currentChatId != null) {
                flushMemoryBeforeCompaction(
                    chatId = currentChatId,
                    preferenceProfileIdOverride = effectivePreferenceProfileIdOverride
                )
            }
            val summaryCustomRules = readSummaryCustomRules()
            val summaryMessage =
                AIMessageManager.summarizeMemory(
                    service,
                    compactionPlan.summaryInputMessages,
                    autoContinue,
                    effectiveIsGroupChat,
                    summaryCustomRules
                )

            if (summaryMessage != null) {
                chatHistoryDelegate.addSummaryMessage(
                    summaryMessage = summaryMessage,
                    beforeTimestamp = compactionPlan.beforeTimestamp,
                    afterTimestamp = compactionPlan.afterTimestamp,
                    chatIdOverride = currentChatId,
                )
                AppLogger.d(
                    TAG,
                    "历史压缩完成: compacted=${compactionPlan.compactedMessageCount}, protected=${compactionPlan.protectedMessageCount}"
                )

                refreshStableContextWindow(
                    chatId = currentChatId,
                    roleCardId = roleCardIdOverride,
                    groupOrchestrationMode = isGroupOrchestrationTurn,
                    groupParticipantNamesText = groupParticipantNamesText,
                    chatModelConfigIdOverride = effectiveChatModelConfigIdOverride,
                    chatModelIndexOverride = effectiveChatModelIndexOverride,
                    preferenceProfileIdOverride = effectivePreferenceProfileIdOverride
                )
                summarySuccess = true
            } else {
                AppLogger.w(TAG, "总结失败或无需总结")
                uiStateDelegate.showErrorMessage(context.getString(R.string.chat_summarize_failed_no_valid_summary))
            }
        } catch (e: CancellationException) {
            // 总结被取消，这是正常流程
            AppLogger.d(TAG, "总结操作被取消")
            throw e // 重新抛出取消异常，让协程正确取消
        } catch (e: Exception) {
            AppLogger.e(TAG, "生成总结时出错: ${e.message}", e)
            uiStateDelegate.showErrorMessage(
                context.getString(
                    R.string.chat_summarize_generation_failed,
                    e.message ?: ""
                )
            )
        } finally {
            _isSummarizing.value = false
            if (_summarizingChatId.value == currentChatId) {
                _summarizingChatId.value = null
            }
            val wasSummarizing =
                currentChatId != null &&
                    messageProcessingDelegate.inputProcessingStateByChatId.value[currentChatId] is InputProcessingState.Summarizing

            // 刷新聚合加载状态；这里只更新派生值，不会直接解除当前 chat 的加载锁
            messageProcessingDelegate.refreshGlobalLoadingState()

            if (summarySuccess) {
                if (autoContinue) {
                    if (currentChatId != null) {
                        val continuationPromptType = promptFunctionType ?: currentPromptFunctionType
                        if (messageProcessingDelegate.isChatLoading(currentChatId)) {
                            AppLogger.d(TAG, "总结成功，但上一轮仍在处理中，转为排队自动续聊...")
                            queuePendingAutoContinuation(
                                chatId = currentChatId,
                                promptFunctionType = continuationPromptType,
                                chatModelConfigIdOverride = effectiveChatModelConfigIdOverride,
                                chatModelIndexOverride = effectiveChatModelIndexOverride,
                                preferenceProfileIdOverride = effectivePreferenceProfileIdOverride,
                                roleCardIdOverride = roleCardIdOverride,
                                isGroupOrchestrationTurn = isGroupOrchestrationTurn,
                                groupParticipantNamesText = groupParticipantNamesText
                            )
                        } else {
                            messageProcessingDelegate.setSuppressIdleCompletedStateForChat(currentChatId, false)
                            AppLogger.d(TAG, "总结成功，自动继续对话...")
                            sendMessageInternal(
                                promptFunctionType = continuationPromptType,
                                isContinuation = true,
                                isAutoContinuation = true,
                                roleCardIdOverride = roleCardIdOverride,
                                chatIdOverride = currentChatId,
                                chatModelConfigIdOverride = effectiveChatModelConfigIdOverride,
                                chatModelIndexOverride = effectiveChatModelIndexOverride,
                                preferenceProfileIdOverride = effectivePreferenceProfileIdOverride,
                                isGroupOrchestrationTurn = isGroupOrchestrationTurn,
                                groupParticipantNamesText = groupParticipantNamesText
                            )
                            if (!messageProcessingDelegate.isChatLoading(currentChatId)) {
                                AppLogger.w(TAG, "自动续聊未能启动，恢复Idle: chatId=$currentChatId")
                                restoreIdleIfCurrentlySummarizing(currentChatId)
                            }
                        }
                    }
                } else if (wasSummarizing) {
                    // 总结成功且不自动续写时，主动恢复到Idle
                    if (currentChatId != null) {
                        messageProcessingDelegate.setSuppressIdleCompletedStateForChat(currentChatId, false)
                        messageProcessingDelegate.setInputProcessingStateForChat(currentChatId, InputProcessingState.Idle)
                    }
                }
            } else if (wasSummarizing) {
                // 总结未成功时也恢复到Idle，避免卡在Summarizing状态
                if (currentChatId != null) {
                    messageProcessingDelegate.setSuppressIdleCompletedStateForChat(currentChatId, false)
                    messageProcessingDelegate.setInputProcessingStateForChat(currentChatId, InputProcessingState.Idle)
                }
            } else if (currentChatId != null) {
                messageProcessingDelegate.setSuppressIdleCompletedStateForChat(currentChatId, false)
            }
        }
        return summarySuccess
    }

    fun setUiBridge(uiBridge: ChatServiceUiBridge) {
        this.uiBridge = uiBridge
    }

    /** 从当前聊天绑定的模型配置中读取自定义总结规则 */
    suspend fun readSummaryCustomRules(): String? {
        return try {
            val functionalConfigManager = FunctionalConfigManager(context)
            val modelConfigManager = ModelConfigManager(context)
            functionalConfigManager.initializeIfNeeded()
            modelConfigManager.initializeIfNeeded()
            val functionMappings = functionalConfigManager.functionConfigMappingWithIndexFlow.first()
            val chatMapping = functionMappings[FunctionType.CHAT] ?: FunctionConfigMapping()
            if (chatMapping.configId.isNotBlank()) {
                val config = modelConfigManager.getModelConfigFlow(chatMapping.configId).first()
                config.summaryCustomRules.takeIf { it.isNotBlank() }
            } else {
                null
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "读取自定义总结规则失败", e)
            null
        }
    }
}
