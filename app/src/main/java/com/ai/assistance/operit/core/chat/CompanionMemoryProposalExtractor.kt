package com.ai.assistance.operit.core.chat

import android.content.Context
import com.ai.assistance.operit.api.chat.llmprovider.AIService
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.data.model.CompanionMemoryProposal
import com.ai.assistance.operit.data.model.CompanionMemoryProposalAction
import com.ai.assistance.operit.data.model.ChatMessageDisplayMode
import com.ai.assistance.operit.data.model.CompanionMemoryPredicate
import com.ai.assistance.operit.data.model.CompanionMemoryEdgeType
import com.ai.assistance.operit.data.model.CompanionMemorySourceKind
import com.ai.assistance.operit.data.model.CompanionMemoryType
import com.ai.assistance.operit.data.model.CompanionRecordScope
import com.ai.assistance.operit.data.model.MessageEntity
import com.ai.assistance.operit.data.model.MemoryTriggerKind
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.repository.CompanionMemoryRepository
import com.ai.assistance.operit.util.ChatUtils
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

object CompanionMemoryProposalExtractor {
    private const val MAX_SOURCE_MESSAGES = 24
    private const val MAX_PROPOSALS = 8
    private val predicatePattern = Regex("[a-z][a-z0-9_.:-]{0,63}")

    suspend fun extractAndApply(
        context: Context,
        profileId: String,
        companionId: String,
        conversationId: String,
        messages: List<MessageEntity>,
        aiService: AIService,
        requireReview: Boolean,
        autoConfirmHighImportance: Boolean = false,
        triggerKind: MemoryTriggerKind,
        nowMs: Long = System.currentTimeMillis(),
    ): Int {
        val sourceMessages =
            messages
                .asSequence()
                .filter { it.sender == "user" || it.sender == "ai" }
                .filter { it.content.isNotBlank() }
                .filter { it.displayMode != ChatMessageDisplayMode.HIDDEN_PLACEHOLDER.name }
                .toList()
                .takeLast(MAX_SOURCE_MESSAGES)
        if (sourceMessages.none { it.sender == "user" && it.messageId > 0L }) return 0

        val sourceJson =
            buildJsonArray {
                sourceMessages.forEach { message ->
                    add(
                        buildJsonObject {
                            put("message_id", message.messageId)
                            put("speaker", if (message.sender == "user") "user" else "assistant")
                            put("timestamp_ms", message.timestamp)
                            put("content", message.content.take(2_000))
                        },
                    )
                }
            }
        val prompt =
            listOf(
                PromptTurn(
                    kind = PromptTurnKind.SYSTEM,
                    content = SYSTEM_PROMPT,
                ),
                PromptTurn(
                    kind = PromptTurnKind.USER,
                    content = sourceJson.toString(),
                ),
            )
        val response = StringBuilder()
        withContext(Dispatchers.IO) {
            aiService.sendMessage(
                context = context,
                chatHistory = prompt,
                stream = false,
                availableTools = emptyList(),
            ).collect { response.append(it) }
        }

        ApiPreferences.getInstance(context).apply {
            updateTokensForProviderModel(
                aiService.providerModel,
                aiService.inputTokenCount,
                aiService.outputTokenCount,
                aiService.cachedInputTokenCount,
            )
            incrementRequestCountForProviderModel(aiService.providerModel)
        }

        val proposals =
            parseValidatedProposals(
                response = ChatUtils.removeThinkingContent(response.toString()),
                conversationId = conversationId,
                messages = sourceMessages,
                requireReview = requireReview,
                autoConfirmHighImportance = autoConfirmHighImportance,
                triggerKind = triggerKind,
                nowMs = nowMs,
            )
        val repository = CompanionMemoryRepository(context)
        var applied = 0
        proposals.forEach { proposal ->
            val recordId = repository.applyProposal(profileId, companionId, proposal)
            if (recordId != null) {
                applied += 1
                // Background extraction is deliberately silent. Only the explicit save tool owns
                // the receipt, otherwise two independent writers race to notify the same turn.
                if (proposal.reviewAt == null) {
                    repository.refreshEpisodeForRecord(recordId)
                }
            }
        }
        return applied
    }

    internal fun parseValidatedProposals(
        response: String,
        conversationId: String,
        messages: List<MessageEntity>,
        requireReview: Boolean = true,
        autoConfirmHighImportance: Boolean = false,
        triggerKind: MemoryTriggerKind = MemoryTriggerKind.AUTO_EXTRACT,
        nowMs: Long,
    ): List<CompanionMemoryProposal> {
        val evidenceMessages =
            messages
                .asSequence()
                .filter { it.sender == "user" && it.messageId > 0L }
                .filter { it.displayMode != ChatMessageDisplayMode.HIDDEN_PLACEHOLDER.name }
                .associateBy { it.messageId }
        if (evidenceMessages.isEmpty()) return emptyList()

        val cleanJson = ChatUtils.extractJsonArray(response)
        if (cleanJson.isBlank()) return emptyList()
        val array =
            runCatching { Json.parseToJsonElement(cleanJson) as? JsonArray }
                .getOrNull()
                ?: return emptyList()
        return buildList {
            for (itemElement in array.take(MAX_PROPOSALS)) {
                val item = itemElement as? JsonObject ?: continue
                val action = item.stringValue("action").toProposalAction() ?: continue
                if (action !in ALLOWED_MODEL_ACTIONS) continue

                val evidenceMessageId = item.longValue("evidence_message_id") ?: continue
                val evidence = evidenceMessages[evidenceMessageId] ?: continue
                val evidenceQuote = item.stringValue("evidence_quote").trim().take(500)
                if (evidenceQuote.isBlank() || !evidence.content.contains(evidenceQuote)) continue
                if (CompanionMemorySensitiveDataGuard.containsSensitiveData(evidenceQuote)) continue

                if (action == CompanionMemoryProposalAction.LINK) {
                    val memoryId = item.stringValue("memory_id").trim().takeIf { it.isNotBlank() }
                    val relatedMemoryId =
                        item.stringValue("related_memory_id").trim().takeIf { it.isNotBlank() }
                    val edgeType = item.stringValue("edge_type").toEdgeType()
                    if (memoryId == null || relatedMemoryId == null || edgeType == null) continue
                    add(
                        CompanionMemoryProposal(
                            action = action,
                            scope = CompanionRecordScope.RELATIONSHIP,
                            type = CompanionMemoryType.RELATIONSHIP,
                            subjectKey = "relationship",
                            predicate = "memory_link",
                            value = evidenceQuote,
                            normalizedValue = CompanionMemoryRepository.normalizeValue(evidenceQuote),
                            confidence = item.doubleValue("confidence")?.coerceIn(0.4, 0.9) ?: 0.65,
                            importance = item.doubleValue("importance")?.coerceIn(0.3, 0.95) ?: 0.6,
                            sourceKind = CompanionMemorySourceKind.USER_EXPLICIT,
                            conversationId = conversationId,
                            messageId = evidence.messageId,
                            messageTimestamp = evidence.timestamp,
                            evidenceQuote = evidenceQuote,
                            evidenceSpeaker = "user",
                            memoryId = memoryId,
                            relatedMemoryId = relatedMemoryId,
                            edgeType = edgeType,
                            edgeStrength =
                                item.doubleValue("edge_strength")?.coerceIn(0.0, 1.0) ?: 0.5,
                            evidenceMessageIds = listOf(evidence.messageId),
                            triggerKind = triggerKind,
                        ),
                    )
                    continue
                }

                val scope = item.stringValue("scope").toRecordScope() ?: continue
                val type = item.stringValue("type").toMemoryType() ?: continue
                if (!typeAllowedInScope(type, scope)) continue

                val predicate =
                    CompanionMemoryPredicate.canonicalize(item.stringValue("predicate"))
                if (!predicatePattern.matches(predicate)) continue
                val value = normalizeCandidateValue(item.stringValue("value")).take(500)
                if (value.isBlank()) continue
                if (CompanionMemorySensitiveDataGuard.containsSensitiveData(value)) continue

                val memoryId = item.stringValue("memory_id").trim().takeIf { it.isNotBlank() }
                val relatedMemoryId = item.stringValue("related_memory_id").trim().takeIf { it.isNotBlank() }
                val edgeType = item.stringValue("edge_type").toEdgeType()

                val displayLabel = item.stringValue("label").trim().take(48).takeIf { it.isNotBlank() }
                val confidence = item.doubleValue("confidence")?.coerceIn(0.4, 0.9) ?: 0.65
                val importance = item.doubleValue("importance")?.coerceIn(0.3, 0.95) ?: 0.6
                val evidenceSupportsValue =
                    CompanionMemoryEvidenceValidator.supportsValue(evidenceQuote, value)
                val autoConfirmed =
                    evidenceSupportsValue &&
                        autoConfirmHighImportance &&
                        confidence >= 0.8 &&
                        (importance >= 0.85 ||
                            (type in
                                setOf(
                                    CompanionMemoryType.PREFERENCE,
                                    CompanionMemoryType.ROUTINE,
                                ) && importance >= 0.75)) &&
                        type in AUTO_CONFIRM_TYPES
                // A direct/user-selected trigger does not make a model-transformed value true.
                val needsReview = !evidenceSupportsValue || (requireReview && !autoConfirmed)
                add(
                    CompanionMemoryProposal(
                        action = action,
                        scope = scope,
                        type = type,
                        subjectKey =
                            when (scope) {
                                CompanionRecordScope.USER -> "user"
                                CompanionRecordScope.COMPANION -> "companion"
                                CompanionRecordScope.RELATIONSHIP -> "relationship"
                                CompanionRecordScope.CONVERSATION -> "conversation:$conversationId"
                            },
                        predicate = predicate,
                        value = value,
                        displayLabel = displayLabel,
                        normalizedValue = CompanionMemoryRepository.normalizeValue(value),
                        confidence = confidence,
                        importance = importance,
                        sourceKind =
                            if (needsReview) {
                                CompanionMemorySourceKind.USER_IMPLIED
                            } else {
                                CompanionMemorySourceKind.USER_EXPLICIT
                            },
                        conversationId = conversationId,
                        messageId = evidence.messageId,
                        messageTimestamp = evidence.timestamp,
                        evidenceQuote = evidenceQuote,
                        evidenceSpeaker = "user",
                        reviewAt = nowMs.takeIf { needsReview },
                        memoryId = memoryId,
                        relatedMemoryId = relatedMemoryId,
                        edgeType = edgeType,
                        edgeStrength = item.doubleValue("edge_strength")?.coerceIn(0.0, 1.0) ?: 0.5,
                        evidenceMessageIds = listOf(evidence.messageId),
                        triggerKind = triggerKind,
                    ),
                )
            }
        }.distinctBy { proposal ->
            if (proposal.action == CompanionMemoryProposalAction.LINK) {
                "LINK|${proposal.memoryId}|${proposal.relatedMemoryId}|${proposal.edgeType}"
            } else {
                "${proposal.scope}|${proposal.type}|${proposal.predicate}|${proposal.normalizedValue}"
            }
        }
    }

    private fun String.toProposalAction(): CompanionMemoryProposalAction? =
        CompanionMemoryProposalAction.entries.firstOrNull { it.name == trim().uppercase(Locale.ROOT) }

    private fun String.toRecordScope(): CompanionRecordScope? =
        CompanionRecordScope.entries.firstOrNull { it.name == trim().uppercase(Locale.ROOT) }

    private fun String.toMemoryType(): CompanionMemoryType? =
        CompanionMemoryType.entries.firstOrNull { it.name == trim().uppercase(Locale.ROOT) }

    private fun String.toEdgeType(): CompanionMemoryEdgeType? =
        CompanionMemoryEdgeType.entries.firstOrNull { it.name == trim().uppercase(Locale.ROOT) }

    private fun JsonObject.stringValue(key: String): String =
        (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()

    private fun JsonObject.longValue(key: String): Long? =
        (get(key) as? JsonPrimitive)?.longOrNull

    private fun JsonObject.doubleValue(key: String): Double? =
        (get(key) as? JsonPrimitive)?.doubleOrNull

    private fun normalizeCandidateValue(rawValue: String): String {
        val value = rawValue.trim()
        if (value.isBlank()) return ""
        val withoutQuestionTail =
            value.replace(
                Regex("[，,;；\\s]*(?:记住了吗|明白了吗|懂了吗|对吧|是不是这样)[？?。.!！]*$"),
                "",
            ).trim()
        if (withoutQuestionTail in INVALID_DISCOURSE_VALUES) return ""
        return withoutQuestionTail
    }

    private val INVALID_DISCOURSE_VALUES = setOf("了吗", "吗", "了", "吧", "哦", "呀")

    private fun typeAllowedInScope(type: CompanionMemoryType, scope: CompanionRecordScope): Boolean =
        when (scope) {
            CompanionRecordScope.USER ->
                type !in setOf(CompanionMemoryType.RELATIONSHIP, CompanionMemoryType.SUMMARY)
            CompanionRecordScope.COMPANION ->
                type in
                    setOf(
                        CompanionMemoryType.IDENTITY,
                        CompanionMemoryType.FACT,
                        CompanionMemoryType.PREFERENCE,
                        CompanionMemoryType.ROUTINE,
                        CompanionMemoryType.BOUNDARY,
                    )
            CompanionRecordScope.RELATIONSHIP ->
                type in
                    setOf(
                        CompanionMemoryType.EVENT,
                        CompanionMemoryType.PREFERENCE,
                        CompanionMemoryType.BOUNDARY,
                        CompanionMemoryType.COMMITMENT,
                        CompanionMemoryType.RELATIONSHIP,
                    )
            CompanionRecordScope.CONVERSATION ->
                type in
                    setOf(
                        CompanionMemoryType.EVENT,
                        CompanionMemoryType.COMMITMENT,
                        CompanionMemoryType.SUMMARY,
                    )
        }

    private val ALLOWED_MODEL_ACTIONS =
        setOf(
            CompanionMemoryProposalAction.CREATE,
            CompanionMemoryProposalAction.UPDATE,
            CompanionMemoryProposalAction.SUPERSEDE,
            CompanionMemoryProposalAction.LINK,
        )

    private val AUTO_CONFIRM_TYPES =
        setOf(
            CompanionMemoryType.IDENTITY,
            CompanionMemoryType.PREFERENCE,
            CompanionMemoryType.ROUTINE,
            CompanionMemoryType.BOUNDARY,
            CompanionMemoryType.COMMITMENT,
            CompanionMemoryType.RELATIONSHIP,
            CompanionMemoryType.EVENT,
            CompanionMemoryType.FACT,
        )

    private val SYSTEM_PROMPT =
        """
        Extract only durable companion memories grounded in the supplied messages.
        Return only a JSON array with at most $MAX_PROPOSALS objects. Return [] when nothing is durable.

        Memory schema for CREATE, UPDATE, or SUPERSEDE:
        {"action":"CREATE|UPDATE|SUPERSEDE","scope":"USER|COMPANION|RELATIONSHIP|CONVERSATION","type":"IDENTITY|PREFERENCE|FACT|EVENT|ROUTINE|BOUNDARY|COMMITMENT|RELATIONSHIP|SUMMARY","predicate":"stable_snake_case_key","label":"short user-language label","value":"concise fact","confidence":0.0,"importance":0.0,"evidence_message_id":123,"evidence_quote":"exact substring from that user message"}

        Link schema:
        {"action":"LINK","memory_id":"existing id","related_memory_id":"existing id","edge_type":"ABOUT|PREFERS|AVOIDS|PROMISES|FOLLOWED_BY|CAUSED_BY|SUPPORTS|CONTRADICTS|SUPERSEDES|RELATES_TO","edge_strength":0.0,"confidence":0.0,"importance":0.0,"evidence_message_id":123,"evidence_quote":"exact substring from that user message"}

        Rules:
        - Evidence must reference a real user message_id and quote an exact substring from it.
        - Never use assistant text as evidence and never invent shared history.
        - Save durable identity, preference, routine, boundary, event, commitment, or relationship facts only.
        - Prioritize facts that materially change future companionship: identity and preferred address; family and close relationships; health, allergies, medication and food restrictions; occupation and education; long-term goals; stable routines; firm boundaries; promises; major life events; stable hobbies, favorite games, media, genres, and recurring likes or dislikes.
        - Build the user's profile across these categories when the evidence is direct: identity (name, age, location, background), role (occupation, school, skills), relationships (family, partner, friends, pets), health and food restrictions, interests and hobbies, likes and dislikes, routines and habits, goals and ongoing projects, communication preferences, personality traits, boundaries, and recurring quirks.
        - Store each independently useful fact as an atomic record. Do not wait for the user to use a memory command when the wording clearly describes an enduring characteristic.
        - The schema is extensible: when a direct user trait does not fit a predefined label, keep it as USER/FACT or USER/PREFERENCE with a concise custom predicate such as personality.trait, quirk, communication_style, or custom_note. Never discard a concrete durable fact merely because its label is new.
        - A direct statement such as "我喜欢玩原神" / "I enjoy playing Genshin" is a USER/PREFERENCE memory (for example predicate "favorite_game" or "hobby"), even when the user does not say "记住". A direct trait such as "我有点怕生" is a USER/FACT memory with a custom personality predicate. Treat a one-off activity report as an EVENT instead of a stable preference.
        - Explicit phrases such as "remember this", "this is important", "from now on", "I always", or their Chinese equivalents are strong durability signals when the quoted fact is concrete.
        - When the latest user message asks to remember "this", "that", "the previous message", "这个", "刚才那条", or "上面那个", resolve the referenced fact from preceding user messages and cite that earlier message as evidence.
        - Treat question tails and conversational checks such as "记住了吗", "明白了吗", "对吧", "懂了吗" as discourse, never as the memory value. If a preceding clause contains a concrete durable fact, quote that fact; otherwise return [].
        - Never turn a question, filler phrase, or acknowledgement into a standalone memory. Values like "了吗", "吗", "了", "吧", "哦", and "呀" are always invalid.
        - Assign importance >= 0.85 to identity, health or allergy facts, firm boundaries, close relationships, long-term goals, promises, and major life events. Stable user preferences and routines stated directly can use importance >= 0.75. Use confidence >= 0.8 only when the user's wording directly states the fact.
        - Prefer multiple atomic memories over one vague summary when a message contains several independently useful facts.
        - Ignore greetings, transient wording, tool logs, role-play narration without a durable fact, and guesses.
        - Never store passwords, API keys, verification codes, access tokens, private keys, or financial credentials.
        - Use the same predicate for later corrections of the same fact.
        - Use RELATIONSHIP only for facts shared by this user and companion; use COMPANION for the companion's own profile.
        - LINK is valid only when both supplied memory ids already exist. Never invent ids.
        """.trimIndent()
}
