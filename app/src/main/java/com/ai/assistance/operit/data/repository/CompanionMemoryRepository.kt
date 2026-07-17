package com.ai.assistance.operit.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.model.CompanionMemoryEpisodeEntity
import com.ai.assistance.operit.data.model.CompanionMemoryEdgeEntity
import com.ai.assistance.operit.data.model.CompanionMemoryEdgeStatus
import com.ai.assistance.operit.data.model.CompanionMemoryEdgeType
import com.ai.assistance.operit.data.model.CompanionMemoryEvidenceEntity
import com.ai.assistance.operit.data.model.CompanionMemoryProposal
import com.ai.assistance.operit.data.model.CompanionMemoryProposalAction
import com.ai.assistance.operit.data.model.CompanionMemoryPredicate
import com.ai.assistance.operit.data.model.CompanionMemoryRecordEntity
import com.ai.assistance.operit.data.model.CompanionMemorySourceKind
import com.ai.assistance.operit.data.model.CompanionMemoryType
import com.ai.assistance.operit.data.model.CompanionRecordScope
import com.ai.assistance.operit.data.model.CompanionRecordStatus
import com.ai.assistance.operit.util.AppLogger
import java.util.UUID
import java.util.Locale
import java.nio.charset.StandardCharsets
import kotlin.math.exp
import kotlin.math.max
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

internal const val COMPANION_MEMORY_FTS_QUERY =
    "SELECT `recordId` FROM `companion_memory_fts` WHERE companion_memory_fts MATCH ? LIMIT 64"

data class CompanionMemoryRecallResult(
    val records: List<CompanionMemoryRecordEntity>,
    val episodes: List<CompanionMemoryEpisodeEntity>,
)

data class CompanionMemoryRelation(
    val edge: CompanionMemoryEdgeEntity,
    val relatedRecord: CompanionMemoryRecordEntity,
)

data class CompanionMemoryGraphSnapshot(
    val records: List<CompanionMemoryRecordEntity>,
    val edges: List<CompanionMemoryEdgeEntity>,
)

class CompanionMemoryRepository(context: Context) {
    private val database = AppDatabase.getDatabase(context.applicationContext)
    private val dao = database.companionMemoryDao()

    suspend fun applyProposal(
        profileId: String,
        companionId: String,
        proposal: CompanionMemoryProposal,
    ): String? {
        if (profileId.isBlank() || proposal.action == CompanionMemoryProposalAction.IGNORE) return null
        if (proposal.action == CompanionMemoryProposalAction.LINK) {
            val fromMemoryId = proposal.memoryId ?: return null
            val toMemoryId = proposal.relatedMemoryId ?: return null
            val edgeType = proposal.edgeType ?: return null
            val edgeId =
                linkMemories(
                    fromMemoryId = fromMemoryId,
                    toMemoryId = toMemoryId,
                    type = edgeType,
                    confidence = proposal.confidence,
                    strength = proposal.edgeStrength,
                    evidenceMessageIds =
                        (proposal.evidenceMessageIds + listOfNotNull(proposal.messageId)).distinct(),
                    validUntil = proposal.validUntil,
                )
            return fromMemoryId.takeIf { edgeId != null }
        }
        val normalizedCompanionId =
            if (proposal.scope == CompanionRecordScope.USER) "" else companionId.trim()
        if (proposal.scope != CompanionRecordScope.USER && normalizedCompanionId.isBlank()) return null
        val canonicalPredicate = CompanionMemoryPredicate.canonicalize(proposal.predicate)
        if (canonicalPredicate.isBlank()) return null
        val normalizedValue =
            proposal.normalizedValue.ifBlank { normalizeValue(proposal.value) }
        if (normalizedValue.isBlank()) return null

        return database.withTransaction {
            val activeSubjectRecords =
                (
                    dao.getActiveBySubject(
                        profileId = profileId,
                        companionId = normalizedCompanionId,
                        scope = proposal.scope.name,
                        subjectKey = proposal.subjectKey,
                    ) +
                        if (proposal.scope == CompanionRecordScope.RELATIONSHIP) {
                            dao.getActiveBySubject(
                                profileId = profileId,
                                companionId = normalizedCompanionId,
                                scope = CompanionRecordScope.COMPANION.name,
                                subjectKey = proposal.subjectKey,
                            )
                        } else {
                            emptyList()
                        }
                ).distinctBy { it.id }
            val identityRecords =
                activeSubjectRecords.filter { record ->
                    record.type == proposal.type.name &&
                        CompanionMemoryPredicate.canonicalize(record.predicate) == canonicalPredicate
                }
            val exactExisting = identityRecords.firstOrNull { it.normalizedValue == normalizedValue }
            val existing = exactExisting ?: identityRecords.firstOrNull()
            val now = System.currentTimeMillis()
            val encodedValue =
                encodeCompanionMemoryValue(
                    value = proposal.value,
                    displayLabel = proposal.displayLabel ?: existing?.decodedLabel(),
                )
            val record =
                if (exactExisting != null) {
                    val incomingIsConfirmed =
                        proposal.sourceKind == CompanionMemorySourceKind.USER_EXPLICIT &&
                            proposal.reviewAt == null
                    val existingIsConfirmed = exactExisting.reviewAt == null
                    val preferredSourceKind =
                        if (sourcePriority(proposal.sourceKind.name) >= sourcePriority(exactExisting.sourceKind)) {
                            proposal.sourceKind.name
                        } else {
                            exactExisting.sourceKind
                        }
                    var updated = exactExisting.copy(
                        predicate = canonicalPredicate,
                        valueJson = encodedValue,
                        confidence = max(exactExisting.confidence, proposal.confidence.coerceIn(0.0, 1.0)),
                        importance = max(exactExisting.importance, proposal.importance.coerceIn(0.0, 1.0)),
                        updatedAt = now,
                        lastConfirmedAt = now,
                        sourceKind = preferredSourceKind,
                        validFrom = proposal.validFrom ?: exactExisting.validFrom,
                        validUntil = proposal.validUntil ?: exactExisting.validUntil,
                        reviewAt =
                            when {
                                incomingIsConfirmed -> null
                                existingIsConfirmed -> null
                                else -> proposal.reviewAt ?: exactExisting.reviewAt
                            },
                    )
                    if (incomingIsConfirmed) {
                        updated = promoteConfirmedRecord(updated, now)
                    }
                    dao.updateRecord(updated)
                    updated
                } else {
                    val deferSupersedeUntilReview = proposal.reviewAt != null
                    val conflictingRecords =
                        when {
                            deferSupersedeUntilReview -> emptyList()
                            CompanionMemoryPredicate.isSingleton(canonicalPredicate) -> identityRecords
                            CompanionMemoryPredicate.isPreference(canonicalPredicate) ->
                                activeSubjectRecords.filter { record ->
                                    CompanionMemoryPredicate.isOppositePreference(
                                        canonicalPredicate,
                                        record.predicate,
                                    ) && preferenceConflictKey(record.decodedValue()) ==
                                        preferenceConflictKey(proposal.value)
                                }
                            proposal.action in
                                setOf(
                                    CompanionMemoryProposalAction.UPDATE,
                                    CompanionMemoryProposalAction.SUPERSEDE,
                                ) -> listOfNotNull(existing)
                            else -> emptyList()
                        }
                    val previousConfirmed =
                        conflictingRecords.firstOrNull { it.reviewAt == null }
                            ?: conflictingRecords.firstOrNull()
                            ?: existing.takeIf {
                                CompanionMemoryPredicate.isSingleton(canonicalPredicate) ||
                                    proposal.action != CompanionMemoryProposalAction.CREATE
                            }
                    conflictingRecords.forEach { conflict ->
                        dao.updateRecord(
                            conflict.copy(
                                status = CompanionRecordStatus.SUPERSEDED.name,
                                updatedAt = now,
                            ),
                        )
                    }
                    CompanionMemoryRecordEntity(
                        profileId = profileId,
                        companionId = normalizedCompanionId,
                        conversationId = proposal.conversationId,
                        scope = proposal.scope.name,
                        type = proposal.type.name,
                        subjectKey = proposal.subjectKey,
                        predicate = canonicalPredicate,
                        valueJson = encodedValue,
                        normalizedValue = normalizedValue,
                        confidence = proposal.confidence.coerceIn(0.0, 1.0),
                        importance = proposal.importance.coerceIn(0.0, 1.0),
                        validFrom = proposal.validFrom,
                        validUntil = proposal.validUntil,
                        createdAt = now,
                        updatedAt = now,
                        lastConfirmedAt = now,
                        sourceKind = proposal.sourceKind.name,
                        versionOfId = previousConfirmed?.versionOfId ?: previousConfirmed?.id,
                        supersedesId = previousConfirmed?.id,
                        reviewAt = proposal.reviewAt,
                    ).also { dao.insertRecord(it) }
                }

            record.supersedesId?.let { previousId ->
                if (dao.findActiveEdge(record.id, previousId, CompanionMemoryEdgeType.SUPERSEDES.name) == null) {
                    dao.insertEdge(
                        CompanionMemoryEdgeEntity(
                            fromMemoryId = record.id,
                            toMemoryId = previousId,
                            type = CompanionMemoryEdgeType.SUPERSEDES.name,
                            confidence = record.confidence,
                            strength = 1.0,
                            evidenceMessageIds = encodeEvidenceMessageIds(listOfNotNull(proposal.messageId)),
                            createdAt = now,
                        ),
                    )
                }
            }
            dao.insertEvidence(
                CompanionMemoryEvidenceEntity(
                    memoryId = record.id,
                    conversationId = proposal.conversationId,
                    messageId = proposal.messageId,
                    messageTimestamp = proposal.messageTimestamp,
                    quote = proposal.evidenceQuote.trim().take(1_000),
                    speaker = proposal.evidenceSpeaker,
                    timestamp = proposal.messageTimestamp,
                ),
            )
            record.id
        }
    }

    suspend fun saveManualRecord(
        profileId: String,
        companionId: String,
        scope: CompanionRecordScope,
        type: CompanionMemoryType,
        displayLabel: String,
        value: String,
        existingRecord: CompanionMemoryRecordEntity? = null,
        conversationId: String = "",
        nowMs: Long = System.currentTimeMillis(),
    ): String? {
        val cleanLabel = displayLabel.trim()
        val cleanValue = value.trim()
        if (profileId.isBlank() || cleanValue.isBlank()) return null
        if (existingRecord == null && cleanLabel.isBlank()) return null

        val resolvedScope =
            existingRecord?.scope
                ?.let { stored -> CompanionRecordScope.entries.firstOrNull { it.name == stored } }
                ?: scope
        val resolvedType =
            existingRecord?.type
                ?.let { stored -> CompanionMemoryType.entries.firstOrNull { it.name == stored } }
                ?: type
        val resolvedLabel = cleanLabel.ifBlank { existingRecord?.decodedLabel().orEmpty() }
        val resolvedPredicate =
            existingRecord?.predicate
                ?: manualPredicate(resolvedLabel).ifBlank { "manual:${UUID.randomUUID()}" }
        val resolvedSubject =
            existingRecord?.subjectKey
                ?: when (resolvedScope) {
                    CompanionRecordScope.USER -> "user"
                    CompanionRecordScope.COMPANION -> "companion"
                    CompanionRecordScope.RELATIONSHIP -> "relationship"
                    CompanionRecordScope.CONVERSATION -> "conversation:${conversationId.ifBlank { "manual" }}"
                }

        val savedId = applyProposal(
            profileId = profileId,
            companionId = companionId,
            proposal =
                CompanionMemoryProposal(
                    action =
                        if (existingRecord == null) {
                            CompanionMemoryProposalAction.CREATE
                        } else {
                            CompanionMemoryProposalAction.SUPERSEDE
                        },
                    scope = resolvedScope,
                    type = resolvedType,
                    subjectKey = resolvedSubject,
                    predicate = resolvedPredicate,
                    value = cleanValue,
                    displayLabel = resolvedLabel.takeIf { it.isNotBlank() },
                    normalizedValue = normalizeValue(cleanValue),
                    confidence = 1.0,
                    importance = existingRecord?.importance ?: defaultImportance(resolvedType),
                    sourceKind = CompanionMemorySourceKind.USER_EXPLICIT,
                    conversationId = conversationId,
                    messageId = null,
                    messageTimestamp = nowMs,
                    evidenceQuote = cleanValue,
                    evidenceSpeaker = "user_manual",
                    validFrom = existingRecord?.validFrom,
                    validUntil = existingRecord?.validUntil,
                    reviewAt = existingRecord?.reviewAt,
                ),
        )
        if (savedId != null) refreshEpisodeForRecord(savedId)
        return savedId
    }

    suspend fun importLegacyRecord(
        profileId: String,
        companionId: String,
        legacyId: String,
        scope: CompanionRecordScope,
        type: CompanionMemoryType,
        displayLabel: String,
        value: String,
        conversationId: String = "",
        sourceTimestamp: Long = System.currentTimeMillis(),
        validFrom: Long? = null,
    ): String? {
        val cleanLegacyId = legacyId.trim()
        val cleanValue = value.trim()
        if (profileId.isBlank() || cleanLegacyId.isBlank() || cleanValue.isBlank()) return null
        return applyProposal(
            profileId = profileId,
            companionId = companionId,
            proposal =
                CompanionMemoryProposal(
                    action = CompanionMemoryProposalAction.CREATE,
                    scope = scope,
                    type = type,
                    subjectKey =
                        when (scope) {
                            CompanionRecordScope.USER -> "user"
                            CompanionRecordScope.COMPANION -> "companion"
                            CompanionRecordScope.RELATIONSHIP -> "relationship"
                            CompanionRecordScope.CONVERSATION -> "conversation:$conversationId"
                        },
                    predicate = "imported:$cleanLegacyId",
                    value = cleanValue,
                    displayLabel = displayLabel.trim().takeIf { it.isNotBlank() },
                    normalizedValue = normalizeValue(cleanValue),
                    confidence = 0.6,
                    importance = defaultImportance(type),
                    sourceKind = CompanionMemorySourceKind.IMPORTED,
                    conversationId = conversationId,
                    messageId = null,
                    messageTimestamp = sourceTimestamp,
                    evidenceQuote = cleanValue,
                    evidenceSpeaker = "imported",
                    validFrom = validFrom,
                    reviewAt = System.currentTimeMillis(),
                ),
        )
    }

    suspend fun deleteRecord(recordId: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (recordId.isBlank()) return false
        return database.withTransaction {
            val record = dao.getRecordById(recordId) ?: return@withTransaction false
            if (record.status != CompanionRecordStatus.ACTIVE.name) return@withTransaction false
            dao.updateRecord(
                record.copy(
                    status = CompanionRecordStatus.DELETED.name,
                    updatedAt = nowMs,
                ),
            )
            dao.deleteEvidenceForMemory(recordId)
            dao.deleteEdgesForMemory(recordId)
            dao.deleteEpisode(episodeIdFor(record))
            true
        }
    }

    suspend fun archiveRecord(recordId: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (recordId.isBlank()) return false
        return database.withTransaction {
            val record = dao.getRecordById(recordId) ?: return@withTransaction false
            if (record.status != CompanionRecordStatus.ACTIVE.name) return@withTransaction false
            dao.updateRecord(
                record.copy(
                    status = CompanionRecordStatus.ARCHIVED.name,
                    updatedAt = nowMs,
                ),
            )
            dao.deleteEpisode(episodeIdFor(record))
            true
        }
    }

    suspend fun retractRecord(recordId: String, nowMs: Long = System.currentTimeMillis()): Boolean =
        deleteRecord(recordId, nowMs)

    suspend fun confirmRecord(recordId: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (recordId.isBlank()) return false
        return database.withTransaction {
            val record = dao.getRecordById(recordId) ?: return@withTransaction false
            if (record.status != CompanionRecordStatus.ACTIVE.name || record.reviewAt == null) {
                return@withTransaction false
            }
            val canonicalPredicate = CompanionMemoryPredicate.canonicalize(record.predicate)
            val activeSubjectRecords =
                dao.getActiveBySubject(
                    profileId = record.profileId,
                    companionId = record.companionId,
                    scope = record.scope,
                    subjectKey = record.subjectKey,
                ).filterNot { it.id == record.id }
            val conflicts =
                when {
                    CompanionMemoryPredicate.isSingleton(canonicalPredicate) ->
                        activeSubjectRecords.filter { candidate ->
                            candidate.type == record.type &&
                                CompanionMemoryPredicate.canonicalize(candidate.predicate) == canonicalPredicate
                        }
                    CompanionMemoryPredicate.isPreference(canonicalPredicate) ->
                        activeSubjectRecords.filter { candidate ->
                            CompanionMemoryPredicate.isOppositePreference(
                                canonicalPredicate,
                                candidate.predicate,
                            ) && preferenceConflictKey(candidate.decodedValue()) ==
                                preferenceConflictKey(record.decodedValue())
                        }
                    else -> emptyList()
                }
            val previous = conflicts.firstOrNull { it.reviewAt == null } ?: conflicts.firstOrNull()
            conflicts.forEach { conflict ->
                dao.updateRecord(
                    conflict.copy(
                        status = CompanionRecordStatus.SUPERSEDED.name,
                        updatedAt = nowMs,
                    ),
                )
            }
            val confirmed =
                promoteConfirmedRecord(record, nowMs).copy(
                    predicate = canonicalPredicate,
                    versionOfId = record.versionOfId ?: previous?.versionOfId ?: previous?.id,
                    supersedesId = record.supersedesId ?: previous?.id,
                )
            dao.updateRecord(confirmed)
            confirmed.supersedesId?.let { previousId ->
                if (dao.findActiveEdge(confirmed.id, previousId, CompanionMemoryEdgeType.SUPERSEDES.name) == null) {
                    dao.insertEdge(
                        CompanionMemoryEdgeEntity(
                            fromMemoryId = confirmed.id,
                            toMemoryId = previousId,
                            type = CompanionMemoryEdgeType.SUPERSEDES.name,
                            confidence = confirmed.confidence,
                            strength = 1.0,
                            createdAt = nowMs,
                        ),
                    )
                }
            }
            upsertEpisodeForRecord(confirmed)
            true
        }
    }

    suspend fun refreshEpisodeForRecord(recordId: String): Boolean {
        if (recordId.isBlank()) return false
        return database.withTransaction {
            val record = dao.getRecordById(recordId) ?: return@withTransaction false
            upsertEpisodeForRecord(record)
        }
    }

    suspend fun recall(
        profileId: String,
        companionId: String,
        conversationId: String,
        query: String,
        maxRecords: Int = 6,
        maxEpisodes: Int = 2,
        nowMs: Long = System.currentTimeMillis(),
    ): CompanionMemoryRecallResult {
        if (profileId.isBlank()) return CompanionMemoryRecallResult(emptyList(), emptyList())
        val candidates =
            dao.getActiveCandidates(
                profileId = profileId,
                companionId = companionId,
                conversationId = conversationId,
                nowMs = nowMs,
                limit = 200,
            )
        val ftsMatches = querySearchIndex(query)
        val queryTerms = tokenize(query)
        val scored =
            candidates
                .asSequence()
                .map { record -> record to score(record, query, queryTerms, ftsMatches, nowMs) }
                .toList()
        val selected = selectPriorityCandidates(scored, maxRecords.coerceAtLeast(0))
        val expanded = expandRecallRelations(
            selected = selected,
            profileId = profileId,
            companionId = companionId,
            conversationId = conversationId,
            maxRecords = maxRecords.coerceAtLeast(0),
            nowMs = nowMs,
        )
        if (expanded.isNotEmpty()) {
            dao.markAccessed(expanded.map { it.id }, nowMs)
        }
        return CompanionMemoryRecallResult(
            records = expanded,
            episodes =
                dao.getRecentEpisodes(
                    profileId = profileId,
                    companionId = companionId,
                    nowMs = nowMs,
                    limit = maxEpisodes.coerceAtLeast(0),
                ),
        )
    }

    fun observeActiveRecords(profileId: String) = dao.observeActiveRecords(profileId)

    suspend fun getRecordById(recordId: String): CompanionMemoryRecordEntity? =
        recordId.takeIf { it.isNotBlank() }?.let { dao.getRecordById(it) }

    suspend fun getGraphSnapshot(
        profileId: String,
        nowMs: Long = System.currentTimeMillis(),
    ): CompanionMemoryGraphSnapshot {
        if (profileId.isBlank()) return CompanionMemoryGraphSnapshot(emptyList(), emptyList())
        val records = dao.getActiveGraphRecords(profileId, nowMs)
        if (records.isEmpty()) return CompanionMemoryGraphSnapshot(emptyList(), emptyList())
        val activeRecordIds = records.mapTo(hashSetOf()) { it.id }
        val edges =
            dao.getActiveGraphEdges(
                profileId = profileId,
                nowMs = nowMs,
                limit = 4_000,
            ).filter { edge ->
                edge.fromMemoryId in activeRecordIds && edge.toMemoryId in activeRecordIds
            }
        return CompanionMemoryGraphSnapshot(records = records, edges = edges)
    }

    fun observeRelationshipRecords(profileId: String, companionId: String) =
        dao.observeRelationshipRecords(profileId, companionId)

    suspend fun getEvidence(memoryId: String) = dao.getEvidence(memoryId)

    suspend fun getVersionHistory(memoryId: String): List<CompanionMemoryRecordEntity> {
        val record = dao.getRecordById(memoryId) ?: return emptyList()
        return dao.getVersionHistory(record.versionOfId ?: record.id)
    }

    suspend fun getConversationChapters(
        profileId: String,
        companionId: String,
        conversationId: String,
        limit: Int = 40,
    ): List<CompanionMemoryEpisodeEntity> =
        if (profileId.isBlank() || companionId.isBlank() || conversationId.isBlank() || limit <= 0) {
            emptyList()
        } else {
            dao.getConversationEpisodes(profileId, companionId, conversationId, limit)
        }

    suspend fun linkMemories(
        fromMemoryId: String,
        toMemoryId: String,
        type: CompanionMemoryEdgeType,
        confidence: Double = 1.0,
        strength: Double = 0.5,
        evidenceMessageIds: List<Long> = emptyList(),
        validUntil: Long? = null,
        nowMs: Long = System.currentTimeMillis(),
    ): String? {
        if (fromMemoryId.isBlank() || toMemoryId.isBlank() || fromMemoryId == toMemoryId) return null
        return database.withTransaction {
            val from = dao.getRecordById(fromMemoryId) ?: return@withTransaction null
            val to = dao.getRecordById(toMemoryId) ?: return@withTransaction null
            if (from.profileId != to.profileId) return@withTransaction null
            if (
                from.companionId.isNotBlank() &&
                    to.companionId.isNotBlank() &&
                    from.companionId != to.companionId
            ) {
                return@withTransaction null
            }
            val encodedEvidence = encodeEvidenceMessageIds(evidenceMessageIds)
            val existing = dao.findActiveEdge(fromMemoryId, toMemoryId, type.name)
            if (existing != null) {
                dao.updateEdge(
                    existing.copy(
                        confidence = max(existing.confidence, confidence.coerceIn(0.0, 1.0)),
                        strength = max(existing.strength, strength.coerceIn(0.0, 1.0)),
                        evidenceMessageIds = mergeEvidenceIds(existing.evidenceMessageIds, encodedEvidence),
                        validUntil = validUntil ?: existing.validUntil,
                    ),
                )
                existing.id
            } else {
                val edge =
                    CompanionMemoryEdgeEntity(
                        fromMemoryId = fromMemoryId,
                        toMemoryId = toMemoryId,
                        type = type.name,
                        confidence = confidence.coerceIn(0.0, 1.0),
                        strength = strength.coerceIn(0.0, 1.0),
                        evidenceMessageIds = encodedEvidence,
                        createdAt = nowMs,
                        validUntil = validUntil,
                    )
                dao.insertEdge(edge)
                edge.id
            }
        }
    }

    suspend fun getRelations(
        memoryId: String,
        limit: Int = 20,
        nowMs: Long = System.currentTimeMillis(),
    ): List<CompanionMemoryRelation> {
        if (memoryId.isBlank() || limit <= 0) return emptyList()
        val edges = dao.getActiveEdgesForMemory(memoryId, nowMs, limit)
        val relatedIds =
            edges.map { edge ->
                if (edge.fromMemoryId == memoryId) edge.toMemoryId else edge.fromMemoryId
            }
        val recordsById = dao.getRecordsByIds(relatedIds.distinct()).associateBy { it.id }
        return edges.mapNotNull { edge ->
            val relatedId = if (edge.fromMemoryId == memoryId) edge.toMemoryId else edge.fromMemoryId
            recordsById[relatedId]?.let { CompanionMemoryRelation(edge, it) }
        }
    }

    suspend fun retractRelation(edgeId: String): Boolean =
        edgeId.isNotBlank() && dao.retractEdge(edgeId) > 0

    private suspend fun expandRecallRelations(
        selected: List<CompanionMemoryRecordEntity>,
        profileId: String,
        companionId: String,
        conversationId: String,
        maxRecords: Int,
        nowMs: Long,
    ): List<CompanionMemoryRecordEntity> {
        if (selected.isEmpty() || maxRecords <= 1) return selected.take(maxRecords)
        val recallTypes =
            listOf(
                CompanionMemoryEdgeType.PREFERS,
                CompanionMemoryEdgeType.AVOIDS,
                CompanionMemoryEdgeType.PROMISES,
                CompanionMemoryEdgeType.SUPERSEDES,
                CompanionMemoryEdgeType.FOLLOWED_BY,
            ).map { it.name }
        val edges =
            dao.getRecallEdges(
                memoryIds = selected.map { it.id },
                types = recallTypes,
                nowMs = nowMs,
                limit = 16,
            )
        if (edges.isEmpty()) return selected.take(maxRecords)
        val selectedIds = selected.mapTo(linkedSetOf()) { it.id }
        val neighborIds =
            edges.mapNotNull { edge ->
                when {
                    edge.fromMemoryId in selectedIds && edge.toMemoryId !in selectedIds -> edge.toMemoryId
                    edge.toMemoryId in selectedIds && edge.fromMemoryId !in selectedIds -> edge.fromMemoryId
                    else -> null
                }
            }.distinct()
        val relatedById = dao.getRecordsByIds(neighborIds).associateBy { it.id }
        val linked =
            neighborIds.mapNotNull(relatedById::get).filter { record ->
                record.profileId == profileId &&
                    record.status == CompanionRecordStatus.ACTIVE.name &&
                    record.reviewAt == null &&
                    (record.validFrom == null || record.validFrom <= nowMs) &&
                    (record.validUntil == null || record.validUntil > nowMs) &&
                    when (record.scope) {
                        CompanionRecordScope.USER.name -> true
                        CompanionRecordScope.COMPANION.name -> record.companionId == companionId
                        CompanionRecordScope.RELATIONSHIP.name -> record.companionId == companionId
                        CompanionRecordScope.CONVERSATION.name -> record.conversationId == conversationId
                        else -> false
                    }
            }.take(2)
        if (linked.isEmpty()) return selected.take(maxRecords)
        val keepSelected = (maxRecords - linked.size).coerceAtLeast(0)
        return (selected.take(keepSelected) + linked).distinctBy { it.id }.take(maxRecords)
    }

    private fun encodeEvidenceMessageIds(ids: List<Long>): String =
        buildJsonArray {
            ids.distinct().forEach { add(it) }
        }.toString()

    private fun mergeEvidenceIds(existing: String, incoming: String): String {
        fun decode(value: String): List<Long> =
            runCatching {
                Json.parseToJsonElement(value).jsonArray.mapNotNull { it.jsonPrimitive.longOrNull }
            }.getOrDefault(emptyList())
        return encodeEvidenceMessageIds(decode(existing) + decode(incoming))
    }

    private suspend fun promoteConfirmedRecord(
        record: CompanionMemoryRecordEntity,
        nowMs: Long,
    ): CompanionMemoryRecordEntity {
        val conflictingRecords =
            getActiveByIdentity(
                profileId = record.profileId,
                companionId = record.companionId,
                scope =
                    CompanionRecordScope.entries.firstOrNull { it.name == record.scope }
                        ?: CompanionRecordScope.USER,
                type = record.type,
                subjectKey = record.subjectKey,
                predicate = record.predicate,
            ).filterNot { it.id == record.id }
        val previousConfirmed =
            conflictingRecords.firstOrNull { it.reviewAt == null }
                ?: conflictingRecords.firstOrNull()
        conflictingRecords.forEach { conflict ->
            dao.updateRecord(
                conflict.copy(
                    status = CompanionRecordStatus.SUPERSEDED.name,
                    updatedAt = nowMs,
                ),
            )
        }
        return record.copy(
            sourceKind = CompanionMemorySourceKind.USER_EXPLICIT.name,
            confidence = 1.0,
            reviewAt = null,
            updatedAt = nowMs,
            lastConfirmedAt = nowMs,
            versionOfId =
                record.versionOfId
                    ?: previousConfirmed?.versionOfId
                    ?: previousConfirmed?.id,
            supersedesId = previousConfirmed?.id ?: record.supersedesId,
        )
    }

    private suspend fun upsertEpisodeForRecord(record: CompanionMemoryRecordEntity): Boolean {
        if (record.reviewAt != null || record.status != CompanionRecordStatus.ACTIVE.name) return false
        if (
            record.type != CompanionMemoryType.EVENT.name &&
                record.type != CompanionMemoryType.SUMMARY.name
        ) {
            return false
        }
        val evidence = dao.getEvidence(record.id)
        val startTimestamp = evidence.minOfOrNull { it.messageTimestamp } ?: record.createdAt
        val endTimestamp = evidence.maxOfOrNull { it.messageTimestamp } ?: record.updatedAt
        dao.insertEpisode(
            CompanionMemoryEpisodeEntity(
                id = episodeIdFor(record),
                profileId = record.profileId,
                companionId = record.companionId,
                conversationId = record.conversationId,
                startMessageTimestamp = startTimestamp,
                endMessageTimestamp = endTimestamp,
                summary = record.decodedValue().take(1_000),
                topicTags = record.type,
                createdAt = record.updatedAt,
                validUntil = record.validUntil,
            ),
        )
        return true
    }

    private suspend fun findActiveByIdentity(
        profileId: String,
        companionId: String,
        scope: CompanionRecordScope,
        type: String,
        subjectKey: String,
        predicate: String,
    ): CompanionMemoryRecordEntity? =
        dao.findActiveByIdentity(
            profileId = profileId,
            companionId = companionId,
            scope = scope.name,
            type = type,
            subjectKey = subjectKey,
            predicate = predicate,
        ) ?: if (scope == CompanionRecordScope.RELATIONSHIP) {
            dao.findActiveByIdentity(
                profileId = profileId,
                companionId = companionId,
                scope = CompanionRecordScope.COMPANION.name,
                type = type,
                subjectKey = subjectKey,
                predicate = predicate,
            )
        } else {
            null
        }

    private suspend fun getActiveByIdentity(
        profileId: String,
        companionId: String,
        scope: CompanionRecordScope,
        type: String,
        subjectKey: String,
        predicate: String,
    ): List<CompanionMemoryRecordEntity> {
        val primary =
            dao.getActiveByIdentity(
                profileId = profileId,
                companionId = companionId,
                scope = scope.name,
                type = type,
                subjectKey = subjectKey,
                predicate = predicate,
            )
        if (scope != CompanionRecordScope.RELATIONSHIP) return primary
        return (
            primary +
                dao.getActiveByIdentity(
                    profileId = profileId,
                    companionId = companionId,
                    scope = CompanionRecordScope.COMPANION.name,
                    type = type,
                    subjectKey = subjectKey,
                    predicate = predicate,
                )
        ).distinctBy { it.id }
    }

    private fun episodeIdFor(record: CompanionMemoryRecordEntity): String {
        val rootVersionId = record.versionOfId ?: record.id
        return UUID.nameUUIDFromBytes(
            "${record.profileId}|${record.companionId}|${record.conversationId}|$rootVersionId"
                .toByteArray(StandardCharsets.UTF_8),
        ).toString()
    }

    private fun score(
        record: CompanionMemoryRecordEntity,
        rawQuery: String,
        queryTerms: Set<String>,
        ftsMatches: Map<String, Double>,
        nowMs: Long,
    ): Double {
        val haystack =
            "${record.subjectKey} ${record.predicate} ${record.decodedLabel().orEmpty()} ${record.normalizedValue} ${record.decodedValue()}"
                .lowercase(Locale.ROOT)
        val lexical =
            if (queryTerms.isEmpty()) {
                0.0
            } else {
                queryTerms.count { haystack.contains(it) }.toDouble() / queryTerms.size.toDouble()
            }
        val exact =
            rawQuery.trim().lowercase(Locale.ROOT).takeIf { it.length >= 2 && haystack.contains(it) }
                ?.let { 0.35 }
                ?: 0.0
        val ftsBoost = ftsMatches[record.id]?.times(0.35) ?: 0.0
        val typeBoost =
            when (record.type) {
                CompanionMemoryType.BOUNDARY.name -> 0.35
                CompanionMemoryType.COMMITMENT.name -> 0.25
                CompanionMemoryType.PREFERENCE.name,
                CompanionMemoryType.IDENTITY.name -> 0.20
                CompanionMemoryType.RELATIONSHIP.name -> 0.15
                else -> 0.0
            }
        val explicitBoost = if (record.sourceKind == "USER_EXPLICIT") 0.25 else 0.0
        val ageDays = ((nowMs - record.lastConfirmedAt).coerceAtLeast(0L) / 86_400_000.0)
        val recency =
            if (
                record.type == CompanionMemoryType.BOUNDARY.name ||
                    record.type == CompanionMemoryType.PREFERENCE.name ||
                    record.type == CompanionMemoryType.COMMITMENT.name
            ) {
                1.0
            } else {
                exp(-ageDays / 180.0)
            }
        return lexical * 1.5 + exact + ftsBoost + typeBoost + explicitBoost +
            record.importance * 0.45 + record.confidence * 0.35 + recency * 0.2
    }

    private fun querySearchIndex(query: String): Map<String, Double> {
        val terms = tokenize(query).filter { it.length >= 2 }.take(8)
        if (terms.isEmpty()) return emptyMap()
        val matchExpression = terms.joinToString(" OR ") { "\"${it.replace("\"", "\"\"")}\"" }
        return runCatching {
            database.openHelper.readableDatabase
                .query(
                    COMPANION_MEMORY_FTS_QUERY,
                    arrayOf(matchExpression),
                ).use { cursor ->
                    buildMap {
                        while (cursor.moveToNext()) {
                            put(cursor.getString(0), 1.0)
                        }
                    }
                }
        }.onFailure { error ->
            AppLogger.e(
                "CompanionMemoryRepository",
                "Companion memory search index failed; using structured lexical recall",
                error,
            )
        }.getOrDefault(emptyMap())
    }

    companion object {
        private fun sourcePriority(sourceKind: String): Int =
            when (sourceKind) {
                CompanionMemorySourceKind.USER_EXPLICIT.name -> 3
                CompanionMemorySourceKind.USER_IMPLIED.name -> 2
                CompanionMemorySourceKind.IMPORTED.name -> 1
                CompanionMemorySourceKind.ASSISTANT.name -> 0
                else -> 0
            }

        internal fun selectDiverseCandidates(
            scoredCandidates: List<Pair<CompanionMemoryRecordEntity, Double>>,
            limit: Int,
        ): List<CompanionMemoryRecordEntity> {
            if (limit <= 0 || scoredCandidates.isEmpty()) return emptyList()
            val remaining =
                scoredCandidates
                    .distinctBy { (record, _) -> recallIdentityKey(record) }
                    .toMutableList()
            val selected = mutableListOf<CompanionMemoryRecordEntity>()
            val termCache = mutableMapOf<String, Set<String>>()

            while (remaining.isNotEmpty() && selected.size < limit) {
                val next =
                    remaining.maxByOrNull { (candidate, relevance) ->
                        val candidateTerms =
                            termCache.getOrPut(candidate.id) { tokenize(candidate.searchableText()) }
                        val maximumSimilarity =
                            selected.maxOfOrNull { selectedRecord ->
                                jaccardSimilarity(
                                    candidateTerms,
                                    termCache.getOrPut(selectedRecord.id) {
                                        tokenize(selectedRecord.searchableText())
                                    },
                                )
                            } ?: 0.0
                        relevance * 0.75 - maximumSimilarity * 0.25
                    } ?: break
                selected += next.first
                remaining.remove(next)
            }
            return selected
        }

        internal fun selectPriorityCandidates(
            scoredCandidates: List<Pair<CompanionMemoryRecordEntity, Double>>,
            limit: Int,
        ): List<CompanionMemoryRecordEntity> {
            if (limit <= 0 || scoredCandidates.isEmpty()) return emptyList()
            if (limit < 4) return selectDiverseCandidates(scoredCandidates, limit)

            val unique =
                scoredCandidates.distinctBy { (record, _) -> recallIdentityKey(record) }
            val selected = mutableListOf<CompanionMemoryRecordEntity>()

            fun reserve(predicate: (CompanionMemoryRecordEntity) -> Boolean) {
                val candidate =
                    unique
                        .asSequence()
                        .filter { (record, _) -> record.id !in selected.map { it.id } }
                        .filter { (record, _) -> predicate(record) }
                        .maxByOrNull { (record, relevance) ->
                            relevance + record.importance * 0.35 +
                                if (record.sourceKind == CompanionMemorySourceKind.USER_EXPLICIT.name) 0.1 else 0.0
                        }
                        ?.first
                if (candidate != null && selected.size < limit) selected += candidate
            }

            reserve { record ->
                record.type == CompanionMemoryType.IDENTITY.name ||
                    CompanionMemoryPredicate.canonicalize(record.predicate) == "preferred_address"
            }
            reserve { record ->
                record.type == CompanionMemoryType.BOUNDARY.name ||
                    CompanionMemoryPredicate.canonicalize(record.predicate).startsWith("health.")
            }
            reserve { record ->
                record.type in
                    setOf(
                        CompanionMemoryType.RELATIONSHIP.name,
                        CompanionMemoryType.COMMITMENT.name,
                    ) || record.scope == CompanionRecordScope.RELATIONSHIP.name
            }
            reserve { record ->
                record.type == CompanionMemoryType.EVENT.name && record.importance >= 0.8
            }

            val selectedIds = selected.mapTo(hashSetOf()) { it.id }
            val remaining = unique.filterNot { (record, _) -> record.id in selectedIds }
            selected += selectDiverseCandidates(remaining, limit - selected.size)
            return selected.take(limit)
        }

        private fun CompanionMemoryRecordEntity.searchableText(): String =
            "$subjectKey $predicate $normalizedValue ${decodedValue()}"

        private fun recallIdentityKey(record: CompanionMemoryRecordEntity): String =
            "${record.scope}|${record.subjectKey}|" +
                "${CompanionMemoryPredicate.canonicalize(record.predicate)}|${record.normalizedValue}"

        private fun jaccardSimilarity(left: Set<String>, right: Set<String>): Double {
            if (left.isEmpty() || right.isEmpty()) return 0.0
            val unionSize = (left union right).size
            if (unionSize == 0) return 0.0
            return (left intersect right).size.toDouble() / unionSize.toDouble()
        }

        fun manualPredicate(displayLabel: String): String {
            val normalized = normalizeValue(displayLabel)
            return normalized.takeIf { it.isNotBlank() }?.let { "manual:$it" }.orEmpty()
        }

        fun defaultImportance(type: CompanionMemoryType): Double =
            when (type) {
                CompanionMemoryType.IDENTITY,
                CompanionMemoryType.BOUNDARY,
                CompanionMemoryType.COMMITMENT -> 0.95
                CompanionMemoryType.PREFERENCE,
                CompanionMemoryType.RELATIONSHIP,
                CompanionMemoryType.ROUTINE -> 0.8
                CompanionMemoryType.FACT,
                CompanionMemoryType.EVENT -> 0.7
                CompanionMemoryType.SUMMARY -> 0.55
            }

        fun normalizeValue(value: String): String =
            value
                .trim()
                .lowercase(Locale.ROOT)
                .replace(Regex("[\\s，。！？、,.!?;；:：'\"]+"), "")

        internal fun preferenceConflictKey(value: String): String =
            normalizeValue(value)
                .replace(
                    Regex(
                        "^(?:我)?(?:现在|已经|以后|从今以后)?(?:不再|不)?(?:很|最|特别)?(?:喜欢|爱|讨厌|受不了|吃|喝|用|玩|看|听)+",
                    ),
                    "",
                )
                .trimEnd('了')

        fun tokenize(value: String): Set<String> =
            Regex("[\\p{L}\\p{N}_-]{2,}")
                .findAll(value.lowercase(Locale.ROOT))
                .map { it.value }
                .flatMap { token ->
                    if (token.any { it.code in 0x3400..0x9FFF } && token.length > 2) {
                        sequenceOf(token) + token.windowed(2).asSequence()
                    } else {
                        sequenceOf(token)
                    }
                }
                .take(24)
                .toSet()
    }
}

fun encodeCompanionMemoryValue(value: String, displayLabel: String? = null): String {
    val cleanValue = value.trim()
    val cleanLabel = displayLabel?.trim().orEmpty()
    return if (cleanLabel.isBlank()) {
        JsonPrimitive(cleanValue).toString()
    } else {
        buildJsonObject {
            put("value", cleanValue)
            put("label", cleanLabel)
        }.toString()
    }
}

fun CompanionMemoryRecordEntity.decodedValue(): String =
    runCatching {
        when (val element = Json.parseToJsonElement(valueJson)) {
            is JsonPrimitive -> element.content
            is JsonObject -> element["value"]?.jsonPrimitive?.content.orEmpty()
            else -> ""
        }
    }.getOrDefault(normalizedValue).ifBlank { normalizedValue }

fun CompanionMemoryRecordEntity.decodedLabel(): String? =
    runCatching {
        Json.parseToJsonElement(valueJson)
            .jsonObject["label"]
            ?.jsonPrimitive
            ?.content
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }.getOrNull()
