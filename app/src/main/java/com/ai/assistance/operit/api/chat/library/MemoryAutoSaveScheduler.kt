package com.ai.assistance.operit.api.chat.library

import android.content.Context
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.api.chat.llmprovider.AIService
import com.ai.assistance.operit.core.chat.CompanionMemoryProposalExtractor
import com.ai.assistance.operit.core.chat.CompanionMemorySaveEvidencePolicy
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.MemoryAutoSaveCandidate
import com.ai.assistance.operit.data.model.MemoryTriggerKind
import com.ai.assistance.operit.data.preferences.MemorySearchSettingsPreferences
import com.ai.assistance.operit.data.preferences.preferencesManager
import com.ai.assistance.operit.data.repository.MemoryAutoSaveCandidateRepository
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException

internal enum class MemoryAutoSaveBatchKind(
    val triggerKind: MemoryTriggerKind,
    val usesExactUserMessages: Boolean,
    val autoConfirmHighImportance: Boolean,
) {
    EXPLICIT(MemoryTriggerKind.EXPLICIT_REQUEST, true, false),
    SELECTED(MemoryTriggerKind.USER_SELECTED, true, false),
    HIGH_VALUE(MemoryTriggerKind.AUTO_EXTRACT, true, true),
    AUTO(MemoryTriggerKind.AUTO_EXTRACT, false, true),
}

internal data class ScopedMemoryAutoSaveBatch(
    val companionId: String,
    val kind: MemoryAutoSaveBatchKind,
    val candidates: List<MemoryAutoSaveCandidate>,
)

internal fun buildScopedMemoryAutoSaveBatches(
    orderedCandidates: List<MemoryAutoSaveCandidate>,
    maxCandidatesPerCompanion: Int,
): List<ScopedMemoryAutoSaveBatch> {
    require(maxCandidatesPerCompanion > 0)

    return orderedCandidates
        .asSequence()
        .filter { it.companionId.isNotBlank() }
        .groupBy { it.companionId }
        .flatMap { (companionId, scopedCandidates) ->
            scopedCandidates
                .take(maxCandidatesPerCompanion)
                .groupBy { candidate ->
                    when {
                        MemoryAutoSaveCandidate.isExplicitUserRequestSource(candidate.sourceType) ->
                            MemoryAutoSaveBatchKind.EXPLICIT
                        MemoryAutoSaveCandidate.isUserSelectedSource(candidate.sourceType) ->
                            MemoryAutoSaveBatchKind.SELECTED
                        MemoryAutoSaveCandidate.isHighValueAutoSource(candidate.sourceType) ->
                            MemoryAutoSaveBatchKind.HIGH_VALUE
                        else -> MemoryAutoSaveBatchKind.AUTO
                    }
                }
                .map { (kind, candidates) ->
                    ScopedMemoryAutoSaveBatch(
                        companionId = companionId,
                        kind = kind,
                        candidates = candidates,
                    )
                }
        }
}

class MemoryAutoSaveScheduler(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "MemoryAutoSaveScheduler"
        private const val LOOP_TICK_MS = 60 * 1000L
        const val DEFAULT_POLL_INTERVAL_MS =
            MemorySearchSettingsPreferences.DEFAULT_AUTO_SAVE_INTERVAL_MINUTES * 60 * 1000L
        private const val MAX_MESSAGES_PER_BATCH = 48
        private const val MAX_CANDIDATES_PER_RUN_PER_COMPANION = 20
        private const val REFERENTIAL_CONTEXT_SCAN_LIMIT = 8

        @Volatile
        private var instance: MemoryAutoSaveScheduler? = null

        fun getInstance(): MemoryAutoSaveScheduler? = instance
    }

    private val isRunning = AtomicBoolean(false)
    @Volatile
    private var loopJob: Job? = null
    private val nextRunAtMsByProfileId = ConcurrentHashMap<String, Long>()
    private val immediateRequests = ConcurrentHashMap.newKeySet<String>()

    fun start() {
        if (loopJob?.isActive == true) return
        instance = this
        loopJob =
            scope.launch(Dispatchers.IO) {
                AppLogger.d(TAG, "长期记忆自动保存轮询器已启动")
                while (isActive) {
                    delay(LOOP_TICK_MS)
                    try {
                        runOnce()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        AppLogger.e(TAG, "长期记忆自动保存轮询失败，将在下个周期重试", error)
                    }
                }
            }
    }

    fun getMinutesUntilNextRun(
        profileId: String,
        nowMs: Long = System.currentTimeMillis()
    ): Long {
        val target = getOrInitNextRunAtMs(profileId, nowMs)
        val remainingMs = (target - nowMs).coerceAtLeast(0L)
        return ((remainingMs + 60_000L - 1L) / 60_000L).coerceAtLeast(0L)
    }

    suspend fun runOnce() {
        if (!isRunning.compareAndSet(false, true)) {
            AppLogger.d(TAG, "上一轮长期记忆自动保存仍在运行，跳过本轮")
            return
        }
        try {
            scanAndProcessCandidates()
        } finally {
            isRunning.set(false)
        }
    }

    fun requestImmediateProcessing(profileId: String, chatId: String) {
        if (profileId.isBlank() || chatId.isBlank()) return
        val requestKey = "$profileId::$chatId"
        if (!immediateRequests.add(requestKey)) return

        scope.launch(Dispatchers.IO) {
            try {
                while (isActive) {
                    if (isRunning.get()) {
                        delay(500L)
                        continue
                    }
                    val completed = flushBeforeCompaction(profileId, chatId)
                    if (!completed) {
                        // A flush intentionally handles a bounded batch. Let WorkManager drain
                        // the remainder with backoff instead of either dropping it or spinning.
                        MemoryAutoSaveWorkScheduler.requestImmediate(context, profileId, chatId)
                    }
                    break
                }
            } finally {
                immediateRequests.remove(requestKey)
            }
        }
    }

    /**
     * Best-effort pre-compaction flush. Unlike the periodic pass, this bypasses the interval so
     * durable facts are extracted before old context is summarized.
     */
    suspend fun flushBeforeCompaction(profileId: String, chatId: String): Boolean {
        if (profileId.isBlank() || chatId.isBlank()) return true
        if (!isRunning.compareAndSet(false, true)) {
            AppLogger.d(TAG, "Pre-compaction memory flush skipped because another pass is active")
            return false
        }

        return try {
            val repository = MemoryAutoSaveCandidateRepository(context, profileId)
            val candidates =
                repository.getProcessableCandidates()
                    .filter { it.chatId == chatId }
                    .sortedWith(
                        compareBy<MemoryAutoSaveCandidate> { it.triggerMessageTimestamp }
                            .thenBy { it.createdAt.time }
                    )
            if (candidates.isEmpty()) {
                return repository.getPendingAndFailedCandidates().none { it.chatId == chatId }
            }

            val unscopedCount = candidates.count { it.companionId.isBlank() }
            if (unscopedCount > 0) {
                AppLogger.w(
                    TAG,
                    "Pre-compaction memory flush skipped $unscopedCount unscoped candidates: " +
                        "profileId=$profileId, chatId=$chatId",
                )
            }
            val batches =
                buildScopedMemoryAutoSaveBatches(
                    orderedCandidates = candidates,
                    maxCandidatesPerCompanion = MAX_CANDIDATES_PER_RUN_PER_COMPANION,
                )
            if (batches.isEmpty()) {
                return repository.getPendingAndFailedCandidates().none { it.chatId == chatId }
            }

            val memoryService =
                EnhancedAIService.getAIServiceForFunction(context, FunctionType.MEMORY)
            val messageDao = AppDatabase.getDatabase(context).messageDao()
            batches.forEach { batch ->
                processChatCandidateGroup(
                    profileId = profileId,
                    chatId = chatId,
                    batch = batch,
                    repository = repository,
                    messageDao = messageDao,
                    memoryService = memoryService,
                )
            }

            val remaining =
                repository.getPendingAndFailedCandidates().count { it.chatId == chatId }
            AppLogger.d(
                TAG,
                "Pre-compaction memory flush finished: profileId=$profileId, chatId=$chatId, remaining=$remaining"
            )
            remaining == 0
        } finally {
            isRunning.set(false)
        }
    }

    private suspend fun scanAndProcessCandidates() {
        val profileIds = preferencesManager.profileListFlow.first()
        if (profileIds.isEmpty()) return

        val messageDao = AppDatabase.getDatabase(context).messageDao()
        val nowMs = System.currentTimeMillis()
        var memoryService: AIService? = null

        for (profileId in profileIds) {
            val intervalMs = intervalMsForProfile(profileId)
            val nextRunAtMs = getOrInitNextRunAtMs(profileId, nowMs)
            if (nowMs < nextRunAtMs) {
                continue
            }

            val repository = MemoryAutoSaveCandidateRepository(context, profileId)
            val allCandidates = repository.getProcessableCandidates()
            val groupedCandidates =
                allCandidates
                    .groupBy { it.chatId }
                    .filterKeys { it.isNotBlank() }

            if (groupedCandidates.isEmpty()) {
                scheduleNextRun(
                    profileId,
                    resolveNextRunAtMs(repository, System.currentTimeMillis(), intervalMs)
                )
                continue
            }

            AppLogger.d(
                TAG,
                "开始处理长期记忆候选: profileId=$profileId, chats=${groupedCandidates.size}"
            )
            val activeMemoryService =
                memoryService
                    ?: EnhancedAIService.getAIServiceForFunction(context, FunctionType.MEMORY)
                        .also { memoryService = it }

            for ((chatId, candidates) in groupedCandidates) {
                val orderedCandidates =
                    candidates.sortedWith(
                        compareBy<MemoryAutoSaveCandidate> { it.triggerMessageTimestamp }
                            .thenBy { it.createdAt.time }
                    )
                val unscopedCount = orderedCandidates.count { it.companionId.isBlank() }
                if (unscopedCount > 0) {
                    AppLogger.w(
                        TAG,
                        "Periodic memory pass skipped $unscopedCount unscoped candidates: " +
                            "profileId=$profileId, chatId=$chatId",
                    )
                }
                val batches =
                    buildScopedMemoryAutoSaveBatches(
                        orderedCandidates = orderedCandidates,
                        maxCandidatesPerCompanion = MAX_CANDIDATES_PER_RUN_PER_COMPANION,
                    )
                batches.forEach { batch ->
                    processChatCandidateGroup(
                        profileId = profileId,
                        chatId = chatId,
                        batch = batch,
                        repository = repository,
                        messageDao = messageDao,
                        memoryService = activeMemoryService,
                    )
                }
            }
            scheduleNextRun(
                profileId,
                resolveNextRunAtMs(repository, System.currentTimeMillis(), intervalMs)
            )
        }
    }

    private fun resolveNextRunAtMs(
        repository: MemoryAutoSaveCandidateRepository,
        nowMs: Long,
        intervalMs: Long
    ): Long {
        if (repository.getProcessableCandidates(nowMs).isNotEmpty()) {
            return nowMs + LOOP_TICK_MS
        }
        val earliestDeferredRetry =
            repository.getPendingAndFailedCandidates()
                .asSequence()
                .map { it.nextAttemptAtMs }
                .filter { it > nowMs }
                .minOrNull()
        return minOf(nowMs + intervalMs, earliestDeferredRetry ?: Long.MAX_VALUE)
    }

    private fun intervalMsForProfile(profileId: String): Long {
        val minutes = MemorySearchSettingsPreferences(context, profileId).loadAutoSaveIntervalMinutes()
        return minutes * 60_000L
    }

    private fun getOrInitNextRunAtMs(
        profileId: String,
        nowMs: Long = System.currentTimeMillis()
    ): Long {
        nextRunAtMsByProfileId[profileId]?.takeIf { it > 0L }?.let { return it }
        val preferences = MemorySearchSettingsPreferences(context, profileId)
        val persisted = preferences.loadNextAutoSaveRunAtMs().takeIf { it > 0L }
        val target = persisted ?: (nowMs + intervalMsForProfile(profileId))
        scheduleNextRun(profileId, target)
        return target
    }

    private fun scheduleNextRun(profileId: String, nextRunAtMs: Long) {
        val normalized = nextRunAtMs.coerceAtLeast(0L)
        nextRunAtMsByProfileId[profileId] = normalized
        MemorySearchSettingsPreferences(context, profileId).saveNextAutoSaveRunAtMs(normalized)
    }

    private suspend fun processChatCandidateGroup(
        profileId: String,
        chatId: String,
        batch: ScopedMemoryAutoSaveBatch,
        repository: MemoryAutoSaveCandidateRepository,
        messageDao: com.ai.assistance.operit.data.dao.MessageDao,
        memoryService: com.ai.assistance.operit.api.chat.llmprovider.AIService
    ) {
        val candidates = batch.candidates
        if (candidates.isEmpty()) return
        if (batch.companionId.isBlank()) {
            AppLogger.w(
                TAG,
                "Memory candidate batch has no companion target; skipping: " +
                    "profileId=$profileId, chatId=$chatId",
            )
            return
        }

        val triggerKind = batch.kind.triggerKind
        val isDirectUserSelection = batch.kind.usesExactUserMessages
        val candidateIds = candidates.map { it.id }
        repository.markProcessing(candidateIds)

        try {
            val messages =
                if (isDirectUserSelection) {
                    val selectedMessages =
                        withContext(Dispatchers.IO) {
                            candidates
                                .mapNotNull { candidate ->
                                    messageDao.getMessageByTimestamp(
                                        chatId = chatId,
                                        timestamp = candidate.triggerMessageTimestamp
                                    )
                                }
                        }
                    val referencedMessages =
                        if (batch.kind == MemoryAutoSaveBatchKind.EXPLICIT) {
                            withContext(Dispatchers.IO) {
                                selectedMessages
                                    .filter { message ->
                                        CompanionMemorySaveEvidencePolicy.isReferentialSaveRequest(
                                            message.content,
                                        )
                                    }
                                    .mapNotNull { message ->
                                        messageDao
                                            .getMessagesForChatBeforeTimestampDesc(
                                                chatId = chatId,
                                                maxTimestamp = message.timestamp - 1L,
                                                limit = REFERENTIAL_CONTEXT_SCAN_LIMIT,
                                            )
                                            .firstOrNull { previous ->
                                                previous.sender == "user" &&
                                                    previous.content.isNotBlank() &&
                                                    previous.displayMode !=
                                                        com.ai.assistance.operit.data.model.ChatMessageDisplayMode.HIDDEN_PLACEHOLDER.name
                                            }
                                    }
                            }
                        } else {
                            emptyList()
                        }
                    (selectedMessages + referencedMessages)
                        .distinctBy { message -> message.messageId }
                        .filter { it.sender == "user" && it.content.isNotBlank() }
                        .sortedBy { it.timestamp }
                } else {
                    val latestTriggerTimestamp = candidates.maxOf { it.triggerMessageTimestamp }
                    withContext(Dispatchers.IO) {
                        messageDao.getMessagesForChatBeforeTimestampDesc(
                            chatId = chatId,
                            maxTimestamp = latestTriggerTimestamp,
                            limit = MAX_MESSAGES_PER_BATCH
                        )
                    }.asReversed()
                }

            if (messages.isEmpty()) {
                AppLogger.w(TAG, "未找到候选对应消息，直接清理候选: profileId=$profileId, chatId=$chatId")
                repository.deleteCandidates(candidateIds)
                return
            }

            if (messages.none { it.sender == "user" && it.content.isNotBlank() }) {
                AppLogger.w(TAG, "候选消息缺少有效用户上下文，直接清理候选: profileId=$profileId, chatId=$chatId")
                repository.deleteCandidates(candidateIds)
                return
            }

            // The target is captured when the user turn is queued. Re-resolving here would move
            // an in-flight memory to a different role if the chat binding changes before this pass.
            // Automatic batches may contain ordinary turns, but direct high-confidence
            // durable facts should still become usable memory without a manual confirmation.
            // The extractor keeps ambiguous or weak proposals in review.
            val autoConfirmHighImportance = batch.kind.autoConfirmHighImportance
            val appliedCount = CompanionMemoryProposalExtractor.extractAndApply(
                context = context,
                profileId = profileId,
                companionId = batch.companionId,
                conversationId = chatId,
                messages = messages,
                aiService = memoryService,
                requireReview = !isDirectUserSelection,
                autoConfirmHighImportance = autoConfirmHighImportance,
                triggerKind = triggerKind,
            )
            repository.deleteCandidates(candidateIds)
            AppLogger.d(
                TAG,
                "伴侣记忆候选处理成功: profileId=$profileId, chatId=$chatId, candidates=${candidateIds.size}, proposals=$appliedCount"
            )
        } catch (e: CancellationException) {
            repository.markPending(candidateIds)
            throw e
        } catch (e: Exception) {
            AppLogger.e(TAG, "长期记忆候选处理失败: profileId=$profileId, chatId=$chatId", e)
            repository.markFailed(candidateIds, e.message ?: e.javaClass.simpleName)
        }
    }
}
