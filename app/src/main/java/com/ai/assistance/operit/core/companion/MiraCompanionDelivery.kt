package com.ai.assistance.operit.core.companion

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.ai.assistance.operit.api.chat.ChatRuntimeHolder
import com.ai.assistance.operit.api.chat.ChatRuntimeSlot
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.api.voice.VoiceServiceFactory
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.CharacterCardChatModelBindingMode
import com.ai.assistance.operit.data.model.ChatMessageDisplayMode
import com.ai.assistance.operit.data.model.ChatTurnOptions
import com.ai.assistance.operit.data.model.CompanionEventKind
import com.ai.assistance.operit.data.model.CompanionEventStatus
import com.ai.assistance.operit.data.model.CompanionMemoryKeys
import com.ai.assistance.operit.data.model.CompanionMemoryMetadata
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.InputProcessingState
import com.ai.assistance.operit.data.model.PromptFunctionType
import com.ai.assistance.operit.data.model.companionMetadata
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.AutoReadOverride
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.CharacterGroupCardManager
import com.ai.assistance.operit.data.preferences.CompanionReminderPreferences
import com.ai.assistance.operit.data.preferences.CompanionReminderTarget
import com.ai.assistance.operit.data.preferences.CompanionReminderTargetType
import com.ai.assistance.operit.data.preferences.WaifuPreferences
import com.ai.assistance.operit.data.preferences.resolveAutoReadEnabled
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import com.ai.assistance.operit.data.repository.MemoryRepository
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.TtsSegmenter
import com.ai.assistance.operit.util.WaifuMessageProcessor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

sealed interface MiraCompanionDeliveryResult {
    data object Delivered : MiraCompanionDeliveryResult
    data object AlreadyDelivered : MiraCompanionDeliveryResult
    data object Deferred : MiraCompanionDeliveryResult
    data object Paused : MiraCompanionDeliveryResult
    data object WaitingForNetwork : MiraCompanionDeliveryResult
    data object Retry : MiraCompanionDeliveryResult
}

object MiraCompanionDelivery {
    private val deliveryMutex = Mutex()

    suspend fun deliver(
        context: Context,
        profileId: String,
        memoryUuid: String,
        nowMs: Long = System.currentTimeMillis(),
    ): MiraCompanionDeliveryResult =
        deliveryMutex.withLock {
            deliverLocked(context.applicationContext, profileId, memoryUuid, nowMs)
        }

    private suspend fun deliverLocked(
        context: Context,
        profileId: String,
        memoryUuid: String,
        nowMs: Long,
    ): MiraCompanionDeliveryResult {
        val repository = MemoryRepository(context, profileId)
        val memory = repository.findMemoryByUuid(memoryUuid) ?: return MiraCompanionDeliveryResult.Paused
        val metadata = memory.companionMetadata() ?: return MiraCompanionDeliveryResult.Paused
        if (metadata.status != CompanionEventStatus.PENDING || metadata.eventAtMs == null) {
            return MiraCompanionDeliveryResult.Paused
        }
        if (metadata.notifiedAtMs != null) return MiraCompanionDeliveryResult.AlreadyDelivered

        val scheduler = CompanionReminderScheduler.getInstance(context)
        val dueAtMs = maxOf(metadata.eventAtMs, metadata.nextAttemptAtMs ?: 0L)
        if (dueAtMs > nowMs + CLOCK_SKEW_TOLERANCE_MS) {
            scheduler.scheduleAt(profileId, memoryUuid, dueAtMs)
            return MiraCompanionDeliveryResult.Deferred
        }

        val chatId = metadata.chatId.takeIf { it.isNotBlank() } ?: return MiraCompanionDeliveryResult.Paused
        val chatHistoryManager = ChatHistoryManager.getInstance(context)
        if (!chatHistoryManager.chatExists(chatId)) return MiraCompanionDeliveryResult.Paused

        recoverGeneratedReply(chatHistoryManager, chatId, memoryUuid)?.let { recovered ->
            completeDelivery(
                context = context,
                profileId = profileId,
                repository = repository,
                memoryUuid = memoryUuid,
                metadata = metadata,
                reply = recovered,
                triggerTimestamp = recovered.triggerTimestamp,
                nowMs = nowMs,
            )
            return MiraCompanionDeliveryResult.Delivered
        }

        val target = metadata.toReminderTarget()
        val preferences = CompanionReminderPreferences.getInstance(context)
        val settings = preferences.getSettings(target)
        val sentToday = preferences.getNotificationsSentToday(target, nowMs)
        val cooldownUntilMs =
            if (metadata.kind == CompanionEventKind.REMINDER) {
                null
            } else {
                resolveCooldownUntil(chatHistoryManager, chatId, nowMs)
            }
        when (
            val decision =
                CompanionReminderPolicy.decide(
                    kind = metadata.kind,
                    settings = settings,
                    sentToday = sentToday,
                    nowMs = nowMs,
                    cooldownUntilMs = cooldownUntilMs,
                )
        ) {
            CompanionReminderDecision.Skip -> return MiraCompanionDeliveryResult.Paused
            is CompanionReminderDecision.Defer -> {
                val updated =
                    repository.setMemoryProperties(
                        memory = memory,
                        values = metadata.copy(nextAttemptAtMs = decision.untilMs).toPropertyValues(),
                        removeKeys = CompanionMemoryKeys.ALL,
                    )
                scheduler.sync(profileId, updated)
                return MiraCompanionDeliveryResult.Deferred
            }
            CompanionReminderDecision.Send -> Unit
        }

        if (!MiraCompanionNotifier.canNotify(context)) return MiraCompanionDeliveryResult.Paused
        val roleCardId = resolveRoleCardId(context, metadata)
        if (requiresNetwork(context, chatId, roleCardId) && !hasInternetCapability(context)) {
            return MiraCompanionDeliveryResult.WaitingForNetwork
        }

        val core = ChatRuntimeHolder.getInstance(context).getCore(ChatRuntimeSlot.MAIN)
        if (chatId in core.activeStreamingChatIds.value) return MiraCompanionDeliveryResult.Retry
        cleanupFailedTrigger(
            chatHistoryManager = chatHistoryManager,
            core = core,
            chatId = chatId,
            memoryUuid = memoryUuid,
            cancelActive = false,
        )

        val prompt = MiraCompanionPromptBuilder.build(memory, metadata, nowMs)
        val accepted = core.sendUserMessage(
            promptFunctionType = PromptFunctionType.CHAT,
            roleCardIdOverride = roleCardId,
            chatIdOverride = chatId,
            messageTextOverride = prompt,
            turnOptions =
                ChatTurnOptions(
                    persistTurn = true,
                    notifyReply = false,
                    hideUserMessage = true,
                    disableWarning = true,
                    autoReadOverride = false,
                    memoryAutoUpdateOverride = false,
                    toolsEnabledOverride = false,
                ),
        )

        if (!accepted) {
            return MiraCompanionDeliveryResult.Retry
        }

        if (!awaitTurnStart(core, chatId)) {
            cleanupFailedTrigger(chatHistoryManager, core, chatId, memoryUuid, cancelActive = true)
            return MiraCompanionDeliveryResult.Retry
        }
        val terminalState = awaitTurnCompletion(core, chatId)
        if (terminalState !is InputProcessingState.Error) {
            recoverGeneratedReply(chatHistoryManager, chatId, memoryUuid)?.let { generated ->
                completeDelivery(
                    context = context,
                    profileId = profileId,
                    repository = repository,
                    memoryUuid = memoryUuid,
                    metadata = metadata,
                    reply = generated,
                    triggerTimestamp = generated.triggerTimestamp,
                    nowMs = System.currentTimeMillis(),
                )
                return MiraCompanionDeliveryResult.Delivered
            }
        }

        val failureReason =
            when (terminalState) {
                is InputProcessingState.Error -> terminalState.message
                null -> "turn completion timed out or the terminal state was unavailable"
                else -> "turn completed without a persisted reply"
            }
        AppLogger.w(TAG, "Local proactive generation failed: $failureReason")
        cleanupFailedTrigger(
            chatHistoryManager = chatHistoryManager,
            core = core,
            chatId = chatId,
            memoryUuid = memoryUuid,
            cancelActive = terminalState == null,
        )
        return MiraCompanionDeliveryResult.Retry
    }

    private suspend fun completeDelivery(
        context: Context,
        profileId: String,
        repository: MemoryRepository,
        memoryUuid: String,
        metadata: CompanionMemoryMetadata,
        reply: MiraRecoveredReply,
        triggerTimestamp: Long?,
        nowMs: Long,
    ) {
        val memory = repository.findMemoryByUuid(memoryUuid) ?: return
        val latestMetadata = memory.companionMetadata() ?: metadata
        if (latestMetadata.notifiedAtMs != null) return
        val notificationBody =
            cleanReply(reply.content).ifBlank {
                latestMetadata.reminderText.ifBlank { memory.content.ifBlank { memory.title } }
            }
        MiraCompanionNotifier.show(
            context = context,
            memoryUuid = memoryUuid,
            chatId = latestMetadata.chatId,
            roleName = latestMetadata.characterName,
            body = notificationBody,
        )
        repository.setMemoryProperties(
            memory = memory,
            values =
                latestMetadata.copy(
                    notifiedAtMs = nowMs,
                    nextAttemptAtMs = null,
                ).toPropertyValues(),
            removeKeys = CompanionMemoryKeys.ALL,
        )
        val target = latestMetadata.toReminderTarget()
        if (latestMetadata.kind != CompanionEventKind.REMINDER) {
            CompanionReminderPreferences.getInstance(context).recordNotificationSent(target, nowMs)
        }
        autoReadIfEnabled(context, latestMetadata, notificationBody)

        triggerTimestamp?.let { timestamp ->
            runCatching {
                ChatHistoryManager.getInstance(context).deleteMessage(latestMetadata.chatId, timestamp)
                ChatRuntimeHolder.getInstance(context)
                    .getCore(ChatRuntimeSlot.MAIN)
                    .reloadChatMessagesSmart(latestMetadata.chatId)
            }.onFailure { error ->
                AppLogger.w(TAG, "Failed to remove completed proactive trigger", error)
            }
        }
        CompanionReminderScheduler.getInstance(context).syncProfile(profileId)
    }

    private suspend fun recoverGeneratedReply(
        chatHistoryManager: ChatHistoryManager,
        chatId: String,
        memoryUuid: String,
    ): MiraRecoveredReply? =
        MiraCompanionReplyRecovery.find(
            messages = chatHistoryManager.loadChatMessagesDesc(chatId, RECOVERY_MESSAGE_LIMIT).asReversed(),
            memoryUuid = memoryUuid,
        )

    private suspend fun cleanupFailedTrigger(
        chatHistoryManager: ChatHistoryManager,
        core: com.ai.assistance.operit.services.ChatServiceCore,
        chatId: String,
        memoryUuid: String,
        cancelActive: Boolean,
    ) {
        if (cancelActive) {
            core.cancelMessage(chatId)
            delay(CANCELLATION_SETTLE_MS)
        }
        val marker = MiraCompanionContract.promptMarker(memoryUuid)
        val messages = chatHistoryManager.loadChatMessagesDesc(chatId, RECOVERY_MESSAGE_LIMIT).asReversed()
        val triggerIndex =
            messages.indexOfLast {
                it.sender == "user" &&
                    it.displayMode == ChatMessageDisplayMode.HIDDEN_PLACEHOLDER &&
                    marker in it.content
            }
        if (triggerIndex < 0) return
        val staleMessages = messages.drop(triggerIndex)
        if (
            staleMessages.drop(1).any {
                it.sender == "user" && it.displayMode == ChatMessageDisplayMode.NORMAL
            }
        ) {
            return
        }
        staleMessages.forEach { message ->
            runCatching { chatHistoryManager.deleteMessage(chatId, message.timestamp) }
        }
        runCatching { core.reloadChatMessagesSmart(chatId) }
    }

    private suspend fun awaitTurnStart(
        core: com.ai.assistance.operit.services.ChatServiceCore,
        chatId: String,
    ): Boolean =
        withTimeoutOrNull(TURN_START_TIMEOUT_MS) {
            while (true) {
                val state = core.inputProcessingStateByChatId.value[chatId]
                if (chatId in core.activeStreamingChatIds.value || state !is InputProcessingState.Idle && state != null) {
                    return@withTimeoutOrNull true
                }
                delay(POLL_INTERVAL_MS)
            }
            @Suppress("UNREACHABLE_CODE")
            false
        } ?: false

    private suspend fun awaitTurnCompletion(
        core: com.ai.assistance.operit.services.ChatServiceCore,
        chatId: String,
    ): InputProcessingState? =
        withTimeoutOrNull(TURN_COMPLETION_TIMEOUT_MS) {
            while (true) {
                val state = core.inputProcessingStateByChatId.value[chatId]
                if (
                    chatId !in core.activeStreamingChatIds.value &&
                        (state == null ||
                            state is InputProcessingState.Idle ||
                            state is InputProcessingState.Completed ||
                            state is InputProcessingState.Error)
                ) {
                    return@withTimeoutOrNull state
                }
                delay(POLL_INTERVAL_MS)
            }
            @Suppress("UNREACHABLE_CODE")
            null
        }

    private suspend fun resolveCooldownUntil(
        chatHistoryManager: ChatHistoryManager,
        chatId: String,
        nowMs: Long,
    ): Long? {
        val latestVisible =
            chatHistoryManager.loadChatMessagesDesc(chatId, 20)
                .firstOrNull { it.displayMode == ChatMessageDisplayMode.NORMAL && it.sender in setOf("user", "ai") }
                ?: return null
        if (latestVisible.sender != "ai") return null
        val latestAt = latestVisible.completedAt.takeIf { it > 0L } ?: latestVisible.sentAt.takeIf { it > 0L }
            ?: latestVisible.timestamp
        return (latestAt + UNANSWERED_COOLDOWN_MS).takeIf { it > nowMs }
    }

    private suspend fun requiresNetwork(
        context: Context,
        chatId: String,
        roleCardId: String,
    ): Boolean =
        runCatching {
            val card = CharacterCardManager.getInstance(context).getCharacterCard(roleCardId)
            val bindingMode = CharacterCardChatModelBindingMode.normalize(card.chatModelBindingMode)
            val configId =
                card.chatModelConfigId?.takeIf {
                    bindingMode == CharacterCardChatModelBindingMode.FIXED_CONFIG && it.isNotBlank()
                }
            val modelIndex = if (configId != null) card.chatModelIndex else null
            val config =
                EnhancedAIService.getChatInstance(context, chatId)
                    .getModelConfigForFunction(FunctionType.CHAT, configId, modelIndex)
            val provider = ApiProviderType.fromProviderTypeId(config.apiProviderTypeId) ?: config.apiProviderType
            provider !in LOCAL_PROVIDERS
        }.getOrElse { true }

    private suspend fun resolveRoleCardId(
        context: Context,
        metadata: CompanionMemoryMetadata,
    ): String {
        metadata.characterId.takeIf { it.isNotBlank() }?.let { return it }
        if (metadata.characterGroupId.isNotBlank()) {
            val group =
                CharacterGroupCardManager.getInstance(context)
                    .getCharacterGroupCard(metadata.characterGroupId)
            group?.members
                ?.sortedBy { it.orderIndex }
                ?.firstOrNull { it.characterCardId.isNotBlank() }
                ?.characterCardId
                ?.let { return it }
        }
        return CharacterCardManager.DEFAULT_CHARACTER_CARD_ID
    }

    private fun hasInternetCapability(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private suspend fun autoReadIfEnabled(
        context: Context,
        metadata: CompanionMemoryMetadata,
        text: String,
    ) {
        val apiPreferences = ApiPreferences.getInstance(context)
        val globalEnabled = apiPreferences.enableAutoReadFlow.first()
        val waifuPreferences = WaifuPreferences.getInstance(context)
        val roleOverride =
            when {
                metadata.characterGroupId.isNotBlank() ->
                    waifuPreferences.getAutoReadOverrideForCharacterGroup(metadata.characterGroupId)
                metadata.characterId.isNotBlank() ->
                    waifuPreferences.getAutoReadOverrideForCharacterCard(metadata.characterId)
                else -> AutoReadOverride.INHERIT
            }
        if (!resolveAutoReadEnabled(globalEnabled, roleOverride)) return

        runCatching {
            val service = VoiceServiceFactory.getInstance(context)
            if (!service.isInitialized && !service.initialize()) return
            TtsSegmenter.splitNaturalBlocks(text).forEachIndexed { index, segment ->
                service.speak(segment, interrupt = index == 0)
            }
        }.onFailure { error ->
            AppLogger.w(TAG, "Companion auto-read failed", error)
        }
    }

    private fun cleanReply(content: String): String =
        WaifuMessageProcessor.cleanContentForWaifu(content)
            .replace(Regex("\\[MIRA_PROACTIVE_EVENT:[^]]+]"), "")
            .trim()

    private fun CompanionMemoryMetadata.toReminderTarget(): CompanionReminderTarget =
        if (characterGroupId.isNotBlank()) {
            CompanionReminderTarget(CompanionReminderTargetType.GROUP, characterGroupId)
        } else {
            CompanionReminderTarget(
                CompanionReminderTargetType.CHARACTER,
                characterId.ifBlank { CharacterCardManager.DEFAULT_CHARACTER_CARD_ID },
            )
        }

    private val LOCAL_PROVIDERS =
        setOf(
            ApiProviderType.MNN,
            ApiProviderType.LLAMA_CPP,
            ApiProviderType.LMSTUDIO,
            ApiProviderType.OLLAMA,
            ApiProviderType.OPENAI_LOCAL,
        )

    private const val TAG = "MiraCompanionDelivery"
    private const val CLOCK_SKEW_TOLERANCE_MS = 1_000L
    private const val TURN_START_TIMEOUT_MS = 20_000L
    private const val TURN_COMPLETION_TIMEOUT_MS = 5 * 60 * 1_000L
    private const val POLL_INTERVAL_MS = 75L
    private const val CANCELLATION_SETTLE_MS = 350L
    private const val RECOVERY_MESSAGE_LIMIT = 120
    private const val UNANSWERED_COOLDOWN_MS = 6 * 60 * 60 * 1_000L
}
