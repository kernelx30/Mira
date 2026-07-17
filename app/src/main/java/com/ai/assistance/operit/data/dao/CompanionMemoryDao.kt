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
import kotlinx.coroutines.flow.Flow

@Dao
interface CompanionMemoryDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRecord(record: CompanionMemoryRecordEntity)

    @Update
    suspend fun updateRecord(record: CompanionMemoryRecordEntity)

    @Query("SELECT * FROM companion_memory_records WHERE id = :recordId LIMIT 1")
    suspend fun getRecordById(recordId: String): CompanionMemoryRecordEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvidence(evidence: CompanionMemoryEvidenceEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisode(episode: CompanionMemoryEpisodeEntity)

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
            (:scope = 'USER' AND companionId = '')
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

}
