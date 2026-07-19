package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.content.Context
import com.ai.assistance.operit.api.chat.enhance.ToolExecutionManager
import com.ai.assistance.operit.core.chat.CompanionMemoryImportanceDetector
import com.ai.assistance.operit.core.chat.CompanionMemoryEvidenceValidator
import com.ai.assistance.operit.core.chat.CompanionMemoryReceiptBus
import com.ai.assistance.operit.core.chat.CompanionMemorySaveEvidencePolicy
import com.ai.assistance.operit.core.chat.CompanionMemorySensitiveDataGuard
import com.ai.assistance.operit.core.chat.CompanionMemoryTargetResolver
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.ToolExecutor
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.CharacterCardMemoryProfileBindingMode
import com.ai.assistance.operit.data.model.ChatMessageDisplayMode
import com.ai.assistance.operit.data.model.CompanionMemoryPredicate
import com.ai.assistance.operit.data.model.CompanionMemoryProposal
import com.ai.assistance.operit.data.model.CompanionMemoryProposalAction
import com.ai.assistance.operit.data.model.CompanionMemorySourceKind
import com.ai.assistance.operit.data.model.CompanionMemoryType
import com.ai.assistance.operit.data.model.CompanionRecordScope
import com.ai.assistance.operit.data.model.MemoryEvidenceKind
import com.ai.assistance.operit.data.model.MemoryTriggerKind
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.model.ToolValidationResult
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.preferencesManager
import com.ai.assistance.operit.data.repository.CompanionMemoryRepository
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** Writes user facts immediately when the latest message explicitly requests a memory save. */
class CompanionMemoryToolExecutor(private val context: Context) : ToolExecutor {
    override fun invoke(tool: AITool): ToolResult =
        runBlocking {
            val runtime = ToolExecutionManager.currentToolRuntimeContext()
            val chatId = runtime?.callerChatId.orEmpty()
            if (chatId.isBlank()) return@runBlocking error(tool, "Missing active chat context")

            val recentUserMessages =
                AppDatabase.getDatabase(context.applicationContext)
                    .messageDao()
                    .getMessagesForChatDesc(chatId, 16)
                    .filter {
                        it.sender == "user" &&
                            it.content.isNotBlank() &&
                            it.displayMode != ChatMessageDisplayMode.HIDDEN_PLACEHOLDER.name
                    }
            val latestUserMessage =
                recentUserMessages.firstOrNull()
                    ?: return@runBlocking error(tool, "No user message is available as memory evidence")

            // Ordinary durable facts are handled by the silent background extractor. A model-side
            // miscall here is ignored so it cannot show a false failure after that separate write.
            if (!CompanionMemoryImportanceDetector.isExplicitSaveRequest(latestUserMessage.content)) {
                return@runBlocking ignored(tool, "latest_user_message_is_not_an_explicit_save_request")
            }

            val value = parameter(tool, "value").take(MAX_MEMORY_TEXT_LENGTH)
            val requestedEvidenceQuote =
                parameter(tool, "evidence_quote").take(MAX_MEMORY_TEXT_LENGTH)
            if (value.isBlank() || requestedEvidenceQuote.isBlank()) {
                return@runBlocking error(tool, "value and evidence_quote are required")
            }
            if (value.trim(' ', '。', '.', '！', '!', '？', '?') in INVALID_DISCOURSE_VALUES) {
                return@runBlocking error(tool, "Conversational filler is not a valid memory value")
            }

            val evidenceSelection =
                CompanionMemorySaveEvidencePolicy.resolveEvidence(
                    recentUserMessagesDesc = recentUserMessages,
                    evidenceQuote = requestedEvidenceQuote,
                )
                    ?: return@runBlocking error(
                        tool,
                        "evidence_quote must come from the save request or the user message it refers to",
                    )
            val evidenceMessage = evidenceSelection.message
            val evidenceQuote = evidenceSelection.quote.take(MAX_MEMORY_TEXT_LENGTH)
            if (!CompanionMemoryEvidenceValidator.supportsValue(evidenceQuote, value)) {
                return@runBlocking error(
                    tool,
                    "value must be directly supported by evidence_quote without changing its polarity",
                )
            }
            if (
                CompanionMemorySensitiveDataGuard.containsSensitiveData(value) ||
                    CompanionMemorySensitiveDataGuard.containsSensitiveData(evidenceQuote)
            ) {
                return@runBlocking error(tool, "Sensitive credentials are excluded from companion memory")
            }

            val scope = enumParameter<CompanionRecordScope>(tool, "scope") ?: CompanionRecordScope.USER
            val type = enumParameter<CompanionMemoryType>(tool, "type") ?: CompanionMemoryType.FACT
            if (!typeAllowedInScope(type, scope)) {
                return@runBlocking error(tool, "Memory type $type is invalid for scope $scope")
            }
            val action =
                enumParameter<CompanionMemoryProposalAction>(tool, "action")
                    ?.takeIf { it in ALLOWED_ACTIONS }
                    ?: CompanionMemoryProposalAction.CREATE
            val predicate =
                CompanionMemoryPredicate.canonicalize(parameter(tool, "predicate").ifBlank { "explicit_memory" })
            if (!PREDICATE_PATTERN.matches(predicate)) {
                return@runBlocking error(tool, "predicate must be a stable snake_case key")
            }

            val profileId = resolveProfileId(runtime?.callerCardId)
            val companionId = CompanionMemoryTargetResolver.resolve(context.applicationContext, chatId)
            if (companionId.isBlank()) {
                return@runBlocking error(tool, "The active companion could not be resolved")
            }

            val proposal =
                CompanionMemoryProposal(
                    action = action,
                    scope = scope,
                    type = type,
                    subjectKey =
                        when (scope) {
                            CompanionRecordScope.USER -> "user"
                            CompanionRecordScope.COMPANION -> "companion"
                            CompanionRecordScope.RELATIONSHIP -> "relationship"
                            CompanionRecordScope.CONVERSATION -> "conversation:$chatId"
                        },
                    predicate = predicate,
                    value = value,
                    displayLabel = parameter(tool, "label").take(48).takeIf { it.isNotBlank() },
                    normalizedValue = CompanionMemoryRepository.normalizeValue(value),
                    confidence = numberParameter(tool, "confidence", 1.0),
                    importance = numberParameter(tool, "importance", 0.9),
                    sourceKind = CompanionMemorySourceKind.USER_EXPLICIT,
                    conversationId = chatId,
                    messageId = evidenceMessage.messageId,
                    messageTimestamp = evidenceMessage.timestamp,
                    evidenceQuote = evidenceQuote,
                    evidenceSpeaker = "user",
                    reviewAt = null,
                    evidenceKind = MemoryEvidenceKind.USER_DIRECT,
                    triggerKind = MemoryTriggerKind.EXPLICIT_REQUEST,
                )
            val recordId =
                CompanionMemoryRepository(context.applicationContext)
                    .applyProposal(profileId, companionId, proposal)
                    ?: return@runBlocking error(tool, "The structured memory record was not saved")
            CompanionMemoryReceiptBus.publishConfirmed(recordId, proposal)

            ToolResult(
                toolName = tool.name,
                success = true,
                result =
                    StringResultData(
                        "status=saved\nSaved companion memory: ${proposal.displayLabel ?: proposal.value} (record_id=$recordId)",
                    ),
            )
        }

    override fun validateParameters(tool: AITool): ToolValidationResult {
        // invoke() checks intent first, then reports malformed parameters only for a real request.
        return ToolValidationResult(valid = true)
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

    private fun parameter(tool: AITool, name: String): String =
        tool.parameters.firstOrNull { it.name == name }?.value?.trim().orEmpty()

    private inline fun <reified T : Enum<T>> enumParameter(tool: AITool, name: String): T? =
        enumValues<T>().firstOrNull { it.name == parameter(tool, name).uppercase(Locale.ROOT) }

    private fun numberParameter(tool: AITool, name: String, default: Double): Double =
        parameter(tool, name).toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: default

    private fun error(tool: AITool, message: String): ToolResult =
        ToolResult(
            toolName = tool.name,
            success = false,
            result = StringResultData(""),
            error = message,
        )

    private fun ignored(tool: AITool, reason: String): ToolResult =
        ToolResult(
            toolName = tool.name,
            success = true,
            result =
                StringResultData(
                    "status=ignored\nreason=$reason\n" +
                        "No memory was written by this tool. Do not claim that this tool saved anything; continue the conversation normally.",
                ),
        )

    private fun typeAllowedInScope(type: CompanionMemoryType, scope: CompanionRecordScope): Boolean =
        when (scope) {
            CompanionRecordScope.USER -> type !in setOf(CompanionMemoryType.RELATIONSHIP, CompanionMemoryType.SUMMARY)
            CompanionRecordScope.COMPANION ->
                type in setOf(
                    CompanionMemoryType.IDENTITY,
                    CompanionMemoryType.FACT,
                    CompanionMemoryType.PREFERENCE,
                    CompanionMemoryType.ROUTINE,
                    CompanionMemoryType.BOUNDARY,
                )
            CompanionRecordScope.RELATIONSHIP ->
                type in setOf(
                    CompanionMemoryType.EVENT,
                    CompanionMemoryType.PREFERENCE,
                    CompanionMemoryType.BOUNDARY,
                    CompanionMemoryType.COMMITMENT,
                    CompanionMemoryType.RELATIONSHIP,
                )
            CompanionRecordScope.CONVERSATION ->
                type in setOf(
                    CompanionMemoryType.EVENT,
                    CompanionMemoryType.COMMITMENT,
                    CompanionMemoryType.SUMMARY,
                )
        }

    private companion object {
        const val MAX_MEMORY_TEXT_LENGTH = 500
        val PREDICATE_PATTERN = Regex("[a-z][a-z0-9_.:-]{0,63}")
        val ALLOWED_ACTIONS =
            setOf(
                CompanionMemoryProposalAction.CREATE,
                CompanionMemoryProposalAction.UPDATE,
                CompanionMemoryProposalAction.SUPERSEDE,
            )
        val INVALID_DISCOURSE_VALUES = setOf("了吗", "吗", "了", "吧", "哦", "呀")
    }
}
