package com.ai.assistance.operit.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ai.assistance.operit.data.model.CompanionMemoryEpisodeEntity
import com.ai.assistance.operit.data.model.CompanionMemoryEdgeEntity
import com.ai.assistance.operit.data.model.CompanionMemoryEvidenceEntity
import com.ai.assistance.operit.data.model.CompanionMemoryRecordEntity
import com.ai.assistance.operit.data.model.MemoryGrantEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CompanionMemoryDao {
    @Query(
        """
        UPDATE companion_memory_records
        SET companionId = :canonicalId,
            ownerCompanionId = :canonicalId,
            ownerScope = 'RELATIONSHIP_PAIR',
            visibility = 'PRIVATE',
            needsReview = 0,
            reviewAt = NULL
        WHERE conversationId = :conversationId
          AND scope = 'USER'
          AND ownerCompanionId = ''
          AND (needsReview = 1 OR ownerScope = 'RELATIONSHIP_PAIR')
        """,
    )
    suspend fun bindUnresolvedConversationMemories(
        conversationId: String,
        canonicalId: String,
    ): Int

    @Query(
        """
        UPDATE companion_memory_episodes
        SET companionId = :canonicalId
        WHERE conversationId = :conversationId AND companionId = ''
        """,
    )
    suspend fun bindUnresolvedConversationEpisodes(
        conversationId: String,
        canonicalId: String,
    ): Int

    @Query(
        """
        UPDATE companion_memory_records
        SET companionId = CASE WHEN companionId = :aliasId THEN :canonicalId ELSE companionId END,
            ownerCompanionId = CASE WHEN ownerCompanionId = :aliasId THEN :canonicalId ELSE ownerCompanionId END
        WHERE companionId = :aliasId OR ownerCompanionId = :aliasId
        """,
    )
    suspend fun migrateCompanionAliasInRecords(aliasId: String, canonicalId: String): Int

    @Query(
        "UPDATE companion_memory_episodes SET companionId = :canonicalId WHERE companionId = :aliasId",
    )
    suspend fun migrateCompanionAliasInEpisodes(aliasId: String, canonicalId: String): Int

    @Query(
        """
        DELETE FROM companion_memory_grants
        WHERE granteeCompanionId = :aliasId
          AND EXISTS (
            SELECT 1 FROM companion_memory_grants canonical
            WHERE canonical.memoryId = companion_memory_grants.memoryId
              AND canonical.granteeCompanionId = :canonicalId
          )
        """,
    )
    suspend fun deleteDuplicateCompanionAliasGrants(aliasId: String, canonicalId: String): Int

    @Query(
        "UPDATE companion_memory_grants SET granteeCompanionId = :canonicalId WHERE granteeCompanionId = :aliasId",
    )
    suspend fun migrateCompanionAliasInGrants(aliasId: String, canonicalId: String): Int

    @Query(
        """
        SELECT COUNT(*) FROM companion_memory_records
        WHERE profileId = :profileId
          AND status NOT IN ('DELETED', 'RETRACTED')
        """,
    )
    suspend fun countRecordsForExport(profileId: String): Int

    @Query(
        """
        SELECT COUNT(*) FROM companion_memory_evidence evidence
        INNER JOIN companion_memory_records record ON record.id = evidence.memoryId
        WHERE record.profileId = :profileId
          AND record.status NOT IN ('DELETED', 'RETRACTED')
        """,
    )
    suspend fun countEvidenceForExport(profileId: String): Int

    @Query("SELECT COUNT(*) FROM companion_memory_episodes WHERE profileId = :profileId")
    suspend fun countEpisodesForExport(profileId: String): Int

    @Query(
        """
        SELECT COUNT(*) FROM companion_memory_edges edge
        INNER JOIN companion_memory_records source ON source.id = edge.fromMemoryId
        INNER JOIN companion_memory_records target ON target.id = edge.toMemoryId
        WHERE source.profileId = :profileId AND target.profileId = :profileId
          AND source.status NOT IN ('DELETED', 'RETRACTED')
          AND target.status NOT IN ('DELETED', 'RETRACTED')
        """,
    )
    suspend fun countEdgesForExport(profileId: String): Int

    @Query(
        """
        SELECT COUNT(*) FROM companion_memory_grants grant
        INNER JOIN companion_memory_records record ON record.id = grant.memoryId
        WHERE record.profileId = :profileId
          AND record.status NOT IN ('DELETED', 'RETRACTED')
        """,
    )
    suspend fun countGrantsForExport(profileId: String): Int

    @Query(
        """
        SELECT * FROM companion_memory_records
        WHERE profileId = :profileId
          AND status NOT IN ('DELETED', 'RETRACTED')
        ORDER BY createdAt ASC, id ASC
        """,
    )
    suspend fun getAllRecordsForExport(profileId: String): List<CompanionMemoryRecordEntity>

    @Query("SELECT * FROM companion_memory_evidence WHERE memoryId IN (:memoryIds) ORDER BY memoryId ASC, timestamp ASC, id ASC")
    suspend fun getAllEvidenceForExport(memoryIds: List<String>): List<CompanionMemoryEvidenceEntity>

    @Query(
        """
        SELECT edge.* FROM companion_memory_edges edge
        INNER JOIN companion_memory_records source ON source.id = edge.fromMemoryId
        INNER JOIN companion_memory_records target ON target.id = edge.toMemoryId
        WHERE source.profileId = :profileId AND target.profileId = :profileId
          AND source.status NOT IN ('DELETED', 'RETRACTED')
          AND target.status NOT IN ('DELETED', 'RETRACTED')
        ORDER BY edge.createdAt ASC, edge.id ASC
        """,
    )
    suspend fun getAllEdgesForExport(profileId: String): List<CompanionMemoryEdgeEntity>

    @Query("SELECT * FROM companion_memory_episodes WHERE profileId = :profileId ORDER BY createdAt ASC, id ASC")
    suspend fun getAllEpisodesForExport(profileId: String): List<CompanionMemoryEpisodeEntity>

    @Query(
        """
        SELECT grant.* FROM companion_memory_grants grant
        INNER JOIN companion_memory_records record ON record.id = grant.memoryId
        WHERE record.profileId = :profileId
          AND record.status NOT IN ('DELETED', 'RETRACTED')
        ORDER BY grant.grantedAt ASC, grant.id ASC
        """,
    )
    suspend fun getAllGrantsForExport(profileId: String): List<MemoryGrantEntity>

    @Query(
        """
        SELECT * FROM companion_memory_records
        WHERE profileId = :profileId
          AND companionId = :companionId
          AND conversationId = :conversationId
          AND scope = :scope
          AND type = :type
          AND subjectKey = :subjectKey
          AND predicate = :predicate
          AND normalizedValue = :normalizedValue
          AND createdAt = :createdAt
        LIMIT 1
        """,
    )
    suspend fun findRecordForImport(
        profileId: String,
        companionId: String,
        conversationId: String,
        scope: String,
        type: String,
        subjectKey: String,
        predicate: String,
        normalizedValue: String,
        createdAt: Long,
    ): CompanionMemoryRecordEntity?

    @Query(
        """
        SELECT * FROM companion_memory_edges
        WHERE fromMemoryId = :fromMemoryId
          AND toMemoryId = :toMemoryId
          AND type = :type
          AND status = :status
        LIMIT 1
        """,
    )
    suspend fun findEdgeForImport(
        fromMemoryId: String,
        toMemoryId: String,
        type: String,
        status: String,
    ): CompanionMemoryEdgeEntity?

    @Query(
        """
        SELECT * FROM companion_memory_episodes
        WHERE profileId = :profileId
          AND companionId = :companionId
          AND conversationId = :conversationId
          AND startMessageTimestamp = :startMessageTimestamp
          AND endMessageTimestamp = :endMessageTimestamp
          AND createdAt = :createdAt
        LIMIT 1
        """,
    )
    suspend fun findEpisodeForImport(
        profileId: String,
        companionId: String,
        conversationId: String,
        startMessageTimestamp: Long,
        endMessageTimestamp: Long,
        createdAt: Long,
    ): CompanionMemoryEpisodeEntity?

    @Query("SELECT * FROM companion_memory_grants WHERE memoryId = :memoryId AND granteeCompanionId = :granteeCompanionId LIMIT 1")
    suspend fun findGrantForImport(memoryId: String, granteeCompanionId: String): MemoryGrantEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGrant(grant: MemoryGrantEntity)

    @Query("UPDATE companion_memory_grants SET revokedAt = :revokedAt WHERE memoryId = :memoryId AND granteeCompanionId = :granteeCompanionId AND revokedAt IS NULL")
    suspend fun revokeGrant(memoryId: String, granteeCompanionId: String, revokedAt: Long): Int

    @Query("SELECT * FROM companion_memory_grants WHERE memoryId = :memoryId AND granteeCompanionId = :granteeCompanionId AND revokedAt IS NULL LIMIT 1")
    suspend fun getActiveGrant(memoryId: String, granteeCompanionId: String): MemoryGrantEntity?

    @Query("SELECT * FROM companion_memory_grants WHERE memoryId = :memoryId AND revokedAt IS NULL")
    suspend fun getActiveGrants(memoryId: String): List<MemoryGrantEntity>

    @Query(
        """
        SELECT * FROM companion_memory_records
        WHERE profileId = :profileId AND status = 'ACTIVE'
          AND (:includeReview = 1 OR reviewAt IS NULL)
          AND (validFrom IS NULL OR validFrom <= :nowMs)
          AND (validUntil IS NULL OR validUntil > :nowMs)
          AND (
            (ownerScope = 'GLOBAL_USER' AND visibility = 'ALL_COMPANIONS')
            OR (ownerScope = 'COMPANION_PRIVATE' AND ownerCompanionId = :companionId)
            OR (ownerScope = 'RELATIONSHIP_PAIR' AND ownerCompanionId = :companionId)
            OR (ownerScope = 'CONVERSATION' AND conversationId = :conversationId)
            OR (visibility = 'PARTICIPANTS' AND conversationId = :conversationId)
            OR EXISTS (
                SELECT 1 FROM companion_memory_grants g
                WHERE g.granteeCompanionId = :companionId
                  AND g.permission = 'READ' AND g.revokedAt IS NULL
                  AND (
                    g.memoryId = companion_memory_records.id
                    OR (
                      g.includeSuccessors = 1
                      AND COALESCE(companion_memory_records.versionOfId, companion_memory_records.id) =
                        (SELECT COALESCE(source.versionOfId, source.id) FROM companion_memory_records source WHERE source.id = g.memoryId)
                    )
                  )
            )
          )
        ORDER BY importance DESC, lastConfirmedAt DESC
        LIMIT :limit
        """,
    )
    suspend fun getAccessibleRecords(
        profileId: String,
        companionId: String,
        conversationId: String,
        nowMs: Long,
        limit: Int,
        includeReview: Boolean,
    ): List<CompanionMemoryRecordEntity>

    @Query(
        """
        SELECT * FROM companion_memory_records
        WHERE profileId = :profileId AND status = 'ACTIVE' AND reviewAt IS NULL
          AND (validFrom IS NULL OR validFrom <= :nowMs)
          AND (validUntil IS NULL OR validUntil > :nowMs)
          AND (
            (ownerScope = 'GLOBAL_USER' AND visibility = 'ALL_COMPANIONS')
            OR (ownerScope IN ('COMPANION_PRIVATE','RELATIONSHIP_PAIR') AND ownerCompanionId = :companionId)
            OR (ownerScope = 'CONVERSATION' AND conversationId = :conversationId)
            OR (visibility = 'PARTICIPANTS' AND conversationId = :conversationId)
            OR EXISTS (
              SELECT 1 FROM companion_memory_grants g
              WHERE g.granteeCompanionId = :companionId AND g.permission = 'READ' AND g.revokedAt IS NULL
                AND (g.memoryId = companion_memory_records.id OR (
                  g.includeSuccessors = 1 AND COALESCE(companion_memory_records.versionOfId, companion_memory_records.id) =
                    (SELECT COALESCE(source.versionOfId, source.id) FROM companion_memory_records source WHERE source.id = g.memoryId)
                ))
            )
          )
        ORDER BY importance DESC, lastConfirmedAt DESC
        """,
    )
    fun observeAccessibleRecords(
        profileId: String,
        companionId: String,
        conversationId: String,
        nowMs: Long,
    ): Flow<List<CompanionMemoryRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRecord(record: CompanionMemoryRecordEntity)

    @Update
    suspend fun updateRecord(record: CompanionMemoryRecordEntity)

    @Query("SELECT * FROM companion_memory_records WHERE id = :recordId LIMIT 1")
    suspend fun getRecordById(recordId: String): CompanionMemoryRecordEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvidence(evidence: CompanionMemoryEvidenceEntity): Long

    @Update
    suspend fun updateEvidence(evidence: CompanionMemoryEvidenceEntity)

    @Query(
        """
        SELECT * FROM companion_memory_evidence
        WHERE memoryId = :memoryId
          AND conversationId = :conversationId
          AND messageTimestamp = :messageTimestamp
          AND speaker = :speaker
        LIMIT 1
        """,
    )
    suspend fun findEvidenceForImport(
        memoryId: String,
        conversationId: String,
        messageTimestamp: Long,
        speaker: String,
    ): CompanionMemoryEvidenceEntity?

    @Query(
        """
        UPDATE companion_memory_evidence
        SET messageId = (
            SELECT message.messageId
            FROM messages message
            WHERE message.chatId = companion_memory_evidence.conversationId
              AND message.timestamp = companion_memory_evidence.messageTimestamp
            LIMIT 1
        )
        WHERE messageId IS NULL
          AND conversationId <> ''
          AND EXISTS (
            SELECT 1
            FROM messages message
            WHERE message.chatId = companion_memory_evidence.conversationId
              AND message.timestamp = companion_memory_evidence.messageTimestamp
          )
        """,
    )
    suspend fun rebindUnresolvedEvidenceMessages(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisode(episode: CompanionMemoryEpisodeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEdge(edge: CompanionMemoryEdgeEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEdge(edge: CompanionMemoryEdgeEntity)

    @Update
    suspend fun updateEdge(edge: CompanionMemoryEdgeEntity)

    @Query(
        "SELECT * FROM companion_memory_edges WHERE fromMemoryId = :fromMemoryId AND toMemoryId = :toMemoryId AND type = :type AND status = 'ACTIVE' LIMIT 1",
    )
    suspend fun findActiveEdge(
        fromMemoryId: String,
        toMemoryId: String,
        type: String,
    ): CompanionMemoryEdgeEntity?

    @Query(
        "SELECT * FROM companion_memory_edges WHERE (fromMemoryId = :memoryId OR toMemoryId = :memoryId) AND status = 'ACTIVE' AND (validUntil IS NULL OR validUntil > :nowMs) ORDER BY strength DESC, confidence DESC LIMIT :limit",
    )
    suspend fun getActiveEdgesForMemory(
        memoryId: String,
        nowMs: Long,
        limit: Int,
    ): List<CompanionMemoryEdgeEntity>

    @Query(
        "SELECT * FROM companion_memory_edges WHERE (fromMemoryId IN (:memoryIds) OR toMemoryId IN (:memoryIds)) AND type IN (:types) AND status = 'ACTIVE' AND (validUntil IS NULL OR validUntil > :nowMs) ORDER BY strength DESC, confidence DESC LIMIT :limit",
    )
    suspend fun getRecallEdges(
        memoryIds: List<String>,
        types: List<String>,
        nowMs: Long,
        limit: Int,
    ): List<CompanionMemoryEdgeEntity>

    @Query(
        """
        SELECT edge.* FROM companion_memory_edges AS edge
        INNER JOIN companion_memory_records AS source ON source.id = edge.fromMemoryId
        INNER JOIN companion_memory_records AS target ON target.id = edge.toMemoryId
        WHERE source.profileId = :profileId
          AND target.profileId = :profileId
          AND source.status = 'ACTIVE'
          AND target.status = 'ACTIVE'
          AND edge.status = 'ACTIVE'
          AND (edge.validUntil IS NULL OR edge.validUntil > :nowMs)
        ORDER BY edge.strength DESC, edge.confidence DESC
        LIMIT :limit
        """,
    )
    suspend fun getActiveGraphEdges(
        profileId: String,
        nowMs: Long,
        limit: Int,
    ): List<CompanionMemoryEdgeEntity>

    @Query("SELECT * FROM companion_memory_records WHERE id IN (:recordIds)")
    suspend fun getRecordsByIds(recordIds: List<String>): List<CompanionMemoryRecordEntity>

    @Query("UPDATE companion_memory_edges SET status = 'RETRACTED' WHERE id = :edgeId")
    suspend fun retractEdge(edgeId: String): Int

    @Query("DELETE FROM companion_memory_episodes WHERE id = :episodeId")
    suspend fun deleteEpisode(episodeId: String)

    @Query(
        """
        SELECT * FROM companion_memory_records
        WHERE profileId = :profileId
          AND scope = :scope
          AND type = :type
          AND subjectKey = :subjectKey
          AND predicate = :predicate
          AND status = 'ACTIVE'
          AND (
            (:scope = 'USER' AND (companionId = :companionId OR companionId = ''))
            OR companionId = :companionId
          )
        ORDER BY lastConfirmedAt DESC
        LIMIT 1
        """,
    )
    suspend fun findActiveByIdentity(
        profileId: String,
        companionId: String,
        scope: String,
        type: String,
        subjectKey: String,
        predicate: String,
    ): CompanionMemoryRecordEntity?

    @Query(
        """
        SELECT * FROM companion_memory_records
        WHERE profileId = :profileId
          AND scope = :scope
          AND normalizedValue = :normalizedValue
          AND status = 'DELETED'
          AND (
            (:scope = 'USER' AND (companionId = :companionId OR companionId = ''))
            OR (:scope = 'CONVERSATION' AND conversationId = :conversationId)
            OR (:scope != 'USER' AND :scope != 'CONVERSATION' AND companionId = :companionId)
          )
        ORDER BY updatedAt DESC
        LIMIT 1
        """,
    )
    suspend fun findLatestDeletionForValue(
        profileId: String,
        companionId: String,
        conversationId: String,
        scope: String,
        normalizedValue: String,
    ): CompanionMemoryRecordEntity?

    @Query(
        """
        SELECT * FROM companion_memory_records
        WHERE profileId = :profileId
          AND scope = :scope
          AND subjectKey = :subjectKey
          AND predicate = :predicate
          AND status = 'DELETED'
          AND (
            (:scope = 'USER' AND (companionId = :companionId OR companionId = ''))
            OR (:scope = 'CONVERSATION' AND conversationId = :conversationId)
            OR (:scope != 'USER' AND :scope != 'CONVERSATION' AND companionId = :companionId)
          )
        ORDER BY updatedAt DESC
        LIMIT 1
        """,
    )
    suspend fun findLatestDeletionByIdentity(
        profileId: String,
        companionId: String,
        conversationId: String,
        scope: String,
        subjectKey: String,
        predicate: String,
    ): CompanionMemoryRecordEntity?

    @Query(
        """
        SELECT * FROM companion_memory_records
        WHERE profileId = :profileId
          AND companionId = :companionId
          AND scope = :scope
          AND type = :type
          AND subjectKey = :subjectKey
          AND predicate = :predicate
          AND status = 'ACTIVE'
        ORDER BY lastConfirmedAt DESC
        """,
    )
    suspend fun getActiveByIdentity(
        profileId: String,
        companionId: String,
        scope: String,
        type: String,
        subjectKey: String,
        predicate: String,
    ): List<CompanionMemoryRecordEntity>

    @Query(
        """
        SELECT * FROM companion_memory_records
        WHERE profileId = :profileId
          AND companionId = :companionId
          AND scope = :scope
          AND subjectKey = :subjectKey
          AND status = 'ACTIVE'
        ORDER BY lastConfirmedAt DESC
        """,
    )
    suspend fun getActiveBySubject(
        profileId: String,
        companionId: String,
        scope: String,
        subjectKey: String,
    ): List<CompanionMemoryRecordEntity>

    @Query(
        """
        SELECT * FROM companion_memory_records
        WHERE profileId = :profileId
          AND status = 'ACTIVE'
          AND reviewAt IS NULL
          AND (validFrom IS NULL OR validFrom <= :nowMs)
          AND (validUntil IS NULL OR validUntil > :nowMs)
          AND (
            scope = 'USER'
            OR (scope = 'COMPANION' AND companionId = :companionId)
            OR (scope = 'RELATIONSHIP' AND companionId = :companionId)
            OR (scope = 'CONVERSATION' AND conversationId = :conversationId)
          )
        ORDER BY importance DESC, lastConfirmedAt DESC
        LIMIT :limit
        """,
    )
    suspend fun getActiveCandidates(
        profileId: String,
        companionId: String,
        conversationId: String,
        nowMs: Long,
        limit: Int,
    ): List<CompanionMemoryRecordEntity>

    @Query(
        """
        SELECT * FROM companion_memory_records
        WHERE profileId = :profileId
          AND status = 'ACTIVE'
        ORDER BY updatedAt DESC
        """,
    )
    fun observeActiveRecords(profileId: String): Flow<List<CompanionMemoryRecordEntity>>

    @Query(
        """
        SELECT * FROM companion_memory_records
        WHERE profileId = :profileId
          AND status = 'ACTIVE'
          AND (validFrom IS NULL OR validFrom <= :nowMs)
          AND (validUntil IS NULL OR validUntil > :nowMs)
        ORDER BY importance DESC, updatedAt DESC
        """,
    )
    suspend fun getActiveGraphRecords(
        profileId: String,
        nowMs: Long,
    ): List<CompanionMemoryRecordEntity>

    @Query(
        """
        SELECT record.* FROM companion_memory_records record
        WHERE record.id IN (:recordIds)
          AND record.profileId = :profileId AND record.status = 'ACTIVE'
          AND (
            (record.ownerScope = 'GLOBAL_USER' AND record.visibility = 'ALL_COMPANIONS')
            OR (record.ownerScope IN ('COMPANION_PRIVATE','RELATIONSHIP_PAIR') AND record.ownerCompanionId = :companionId)
            OR (record.ownerScope = 'CONVERSATION' AND record.conversationId = :conversationId)
            OR EXISTS (
              SELECT 1 FROM companion_memory_grants g
              WHERE g.granteeCompanionId = :companionId AND g.permission = 'READ' AND g.revokedAt IS NULL
                AND (g.memoryId = record.id OR (
                  g.includeSuccessors = 1 AND COALESCE(record.versionOfId, record.id) =
                    (SELECT COALESCE(source.versionOfId, source.id) FROM companion_memory_records source WHERE source.id = g.memoryId)
                ))
            )
          )
        """,
    )
    suspend fun getAccessibleRecordsByIds(
        recordIds: List<String>,
        profileId: String,
        companionId: String,
        conversationId: String,
    ): List<CompanionMemoryRecordEntity>

    @Query("SELECT * FROM companion_memory_evidence WHERE memoryId = :memoryId ORDER BY timestamp DESC")
    suspend fun getEvidence(memoryId: String): List<CompanionMemoryEvidenceEntity>

    @Query("DELETE FROM companion_memory_evidence WHERE memoryId = :memoryId")
    suspend fun deleteEvidenceForMemory(memoryId: String)

    @Query("DELETE FROM companion_memory_edges WHERE fromMemoryId = :memoryId OR toMemoryId = :memoryId")
    suspend fun deleteEdgesForMemory(memoryId: String)

    @Query(
        "SELECT * FROM companion_memory_records WHERE id = :rootId OR versionOfId = :rootId ORDER BY createdAt DESC",
    )
    suspend fun getVersionHistory(rootId: String): List<CompanionMemoryRecordEntity>

    @Query(
        "SELECT * FROM companion_memory_records WHERE profileId = :profileId AND companionId = :companionId AND status = 'ACTIVE' AND (scope = 'RELATIONSHIP' OR (scope = 'COMPANION' AND type IN ('RELATIONSHIP', 'COMMITMENT'))) ORDER BY importance DESC, updatedAt DESC",
    )
    fun observeRelationshipRecords(
        profileId: String,
        companionId: String,
    ): Flow<List<CompanionMemoryRecordEntity>>

    @Query(
        """
        SELECT * FROM companion_memory_episodes
        WHERE profileId = :profileId
          AND (companionId = '' OR companionId = :companionId)
          AND (validUntil IS NULL OR validUntil > :nowMs)
        ORDER BY createdAt DESC
        LIMIT :limit
        """,
    )
    suspend fun getRecentEpisodes(
        profileId: String,
        companionId: String,
        nowMs: Long,
        limit: Int,
    ): List<CompanionMemoryEpisodeEntity>

    @Query(
        "SELECT * FROM companion_memory_episodes WHERE profileId = :profileId AND companionId = :companionId AND conversationId = :conversationId ORDER BY startMessageTimestamp DESC LIMIT :limit",
    )
    suspend fun getConversationEpisodes(
        profileId: String,
        companionId: String,
        conversationId: String,
        limit: Int,
    ): List<CompanionMemoryEpisodeEntity>

    @Query("UPDATE companion_memory_records SET lastAccessedAt = :accessedAt WHERE id IN (:recordIds)")
    suspend fun markAccessed(recordIds: List<String>, accessedAt: Long)

    @Query("SELECT * FROM companion_memory_edges WHERE pendingEvidenceReferencesJson IS NOT NULL AND pendingEvidenceReferencesJson <> ''")
    suspend fun getEdgesWithPendingEvidenceReferences(): List<CompanionMemoryEdgeEntity>

}
