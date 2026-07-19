package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.content.Context
import com.ai.assistance.operit.api.chat.enhance.ToolExecutionManager
import com.ai.assistance.operit.core.chat.CompanionMemoryBulkDeleteConfirmationStore
import com.ai.assistance.operit.core.chat.CompanionMemoryDeletionIntentDetector
import com.ai.assistance.operit.core.chat.CompanionMemoryReceiptBus
import com.ai.assistance.operit.core.chat.CompanionMemoryTargetResolver
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.ToolExecutor
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.CharacterCardMemoryProfileBindingMode
import com.ai.assistance.operit.data.model.CompanionMemoryRecordEntity
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.model.ToolValidationResult
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.preferencesManager
import com.ai.assistance.operit.data.repository.CompanionMemoryRepository
import com.ai.assistance.operit.data.repository.decodedLabel
import com.ai.assistance.operit.data.repository.decodedValue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** Deletes matching memories directly; only a complete memory clear requires a second confirmation. */
class CompanionMemoryDeleteToolExecutor(private val context: Context) : ToolExecutor {
    override fun invoke(tool: AITool): ToolResult =
        runBlocking {
            val runtime = ToolExecutionManager.currentToolRuntimeContext()
            val chatId = runtime?.callerChatId.orEmpty()
            if (chatId.isBlank()) return@runBlocking error(tool, "Missing active chat context")

            val recentMessages =
                AppDatabase.getDatabase(context.applicationContext)
                    .messageDao()
                    .getMessagesForChatDesc(chatId, RECENT_MESSAGE_LIMIT)
            val latestUserMessage =
                recentMessages
                    .firstOrNull { it.sender == "user" && it.content.isNotBlank() }
                    ?: return@runBlocking error(tool, "No user message is available as deletion evidence")
            val request = latestUserMessage.content.trim()

            val profileId = resolveProfileId(runtime?.callerCardId)
            val companionId = CompanionMemoryTargetResolver.resolve(context.applicationContext, chatId)
            val repository = CompanionMemoryRepository(context.applicationContext)
            val confirmationKey = "$profileId|$companionId|$chatId"

            when {
                CompanionMemoryDeletionIntentDetector.isBulkDeletionConfirmation(request) -> {
                    val pendingIds =
                        CompanionMemoryBulkDeleteConfirmationStore.consume(confirmationKey)
                            ?: return@runBlocking ignored(tool, "no_pending_bulk_delete_confirmation")
                    val records =
                        pendingIds.mapNotNull { recordId ->
                            repository.getAccessibleRecordById(
                                recordId = recordId,
                                profileId = profileId,
                                companionId = companionId,
                                conversationId = chatId,
                            )
                        }
                    return@runBlocking deletedResult(
                        tool = tool,
                        records = deleteRecords(repository, records),
                        request = request,
                        bulk = true,
                    )
                }

                CompanionMemoryDeletionIntentDetector.isBulkDeletionRequest(request) -> {
                    val recordIds =
                        repository.accessibleRecords(
                            profileId = profileId,
                            companionId = companionId,
                            conversationId = chatId,
                            limit = BULK_DELETE_RECORD_LIMIT,
                            includeReview = true,
                        ).map { it.id }
                    if (recordIds.isEmpty()) {
                        return@runBlocking result(
                            tool,
                            "status=already_empty\nThere are no accessible companion memories to clear.",
                        )
                    }
                    CompanionMemoryBulkDeleteConfirmationStore.request(confirmationKey, recordIds)
                    return@runBlocking result(
                        tool,
                        "status=needs_bulk_confirmation\ncount=${recordIds.size}\n" +
                            "Ask the user to send exactly: 确认清空全部记忆. Do not delete anything until that second confirmation arrives.",
                    )
                }

                !CompanionMemoryDeletionIntentDetector.isExplicitDeletionRequest(request) -> {
                    return@runBlocking ignored(tool, "latest_user_message_is_not_a_deletion_request")
                }
            }

            // A new direct deletion request supersedes an unfinished bulk-clear confirmation.
            CompanionMemoryBulkDeleteConfirmationStore.clear(confirmationKey)
            val matches =
                repository.findManagementMatches(
                    profileId = profileId,
                    companionId = companionId,
                    conversationId = chatId,
                    query = request,
                    contextQueries =
                        if (CompanionMemoryDeletionIntentDetector.isReferentialDeletionRequest(request)) {
                            recentMessages
                                .asSequence()
                                .filter { message -> message != latestUserMessage }
                                .filter { message -> message.sender == "user" || message.sender == "ai" }
                                .filter { message ->
                                    message.displayMode !=
                                        com.ai.assistance.operit.data.model.ChatMessageDisplayMode.HIDDEN_PLACEHOLDER.name
                                }
                                .flatMap { message ->
                                    message.content
                                        .split(Regex("[。！？!?\\n]"))
                                        .asSequence()
                                }
                                .map(String::trim)
                                .filter { sentence -> sentence.length >= MIN_CONTEXT_QUERY_LENGTH }
                                .take(MAX_CONTEXT_QUERY_COUNT)
                                .toList()
                        } else {
                            emptyList()
                        },
                    limit = DIRECT_DELETE_RECORD_LIMIT,
                ).map { it.record }
            if (matches.isEmpty()) {
                return@runBlocking result(
                    tool,
                    "status=not_found\nNo active companion memory matches the user's deletion request.",
                )
            }
            deletedResult(
                tool = tool,
                records = deleteRecords(repository, matches),
                request = request,
                bulk = false,
            )
        }

    override fun validateParameters(tool: AITool): ToolValidationResult = ToolValidationResult(valid = true)

    private suspend fun deleteRecords(
        repository: CompanionMemoryRepository,
        records: List<CompanionMemoryRecordEntity>,
    ): List<CompanionMemoryRecordEntity> {
        val deleted = mutableListOf<CompanionMemoryRecordEntity>()
        records.forEach { record ->
            if (repository.deleteRecord(record.id)) deleted += record
        }
        return deleted
    }

    private fun deletedResult(
        tool: AITool,
        records: List<CompanionMemoryRecordEntity>,
        request: String,
        bulk: Boolean,
    ): ToolResult {
        if (records.isEmpty()) {
            return result(tool, "status=no_change\nNo active companion memory was changed.")
        }
        if (records.size == 1) {
            val record = records.single()
            CompanionMemoryReceiptBus.publishDeleted(
                record = record,
                conversationId = ToolExecutionManager.currentToolRuntimeContext()?.callerChatId.orEmpty(),
                evidenceQuote = request,
            )
        }
        val operation = if (bulk) "Cleared" else "Deleted"
        return result(
            tool,
            "status=deleted\ncount=${records.size}\n$operation companion memories: " +
                records.joinToString(separator = " | ") { displayText(it) }.take(MAX_RESULT_TEXT_LENGTH),
        )
    }

    private suspend fun resolveProfileId(callerCardId: String?): String {
        val cardId = callerCardId?.trim().takeIf { !it.isNullOrBlank() }
        if (cardId != null) {
            val card = CharacterCardManager.getInstance(context).getCharacterCard(cardId)
            val fixedProfileId = card.memoryProfileId?.trim().takeIf { !it.isNullOrBlank() }
            if (
                CharacterCardMemoryProfileBindingMode.normalize(card.memoryProfileBindingMode) ==
                    CharacterCardMemoryProfileBindingMode.FIXED_PROFILE && fixedProfileId != null
            ) {
                return fixedProfileId
            }
        }
        return preferencesManager.activeProfileIdFlow.first()
    }

    private fun displayText(record: CompanionMemoryRecordEntity): String =
        (record.decodedLabel()?.let { "$it: " }.orEmpty() + record.decodedValue())
            .replace('\n', ' ')
            .take(180)

    private fun result(tool: AITool, message: String): ToolResult =
        ToolResult(
            toolName = tool.name,
            success = true,
            result = StringResultData(message),
        )

    private fun error(tool: AITool, message: String): ToolResult =
        ToolResult(
            toolName = tool.name,
            success = false,
            result = StringResultData(""),
            error = message,
        )

    private fun ignored(tool: AITool, reason: String): ToolResult =
        result(
            tool,
            "status=ignored\nreason=$reason\nNo memory was deleted. Do not claim that anything was deleted; continue the conversation normally.",
        )

    private companion object {
        const val RECENT_MESSAGE_LIMIT = 24
        const val DIRECT_DELETE_RECORD_LIMIT = 100
        const val BULK_DELETE_RECORD_LIMIT = 4_000
        const val MAX_RESULT_TEXT_LENGTH = 1_500
        const val MIN_CONTEXT_QUERY_LENGTH = 2
        const val MAX_CONTEXT_QUERY_COUNT = 12
    }
}
