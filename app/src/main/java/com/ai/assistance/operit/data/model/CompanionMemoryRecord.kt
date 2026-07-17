package com.ai.assistance.operit.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

enum class CompanionRecordScope {
    USER,
    COMPANION,
    RELATIONSHIP,
    CONVERSATION,
}

enum class CompanionMemoryType {
    IDENTITY,
    PREFERENCE,
    FACT,
    EVENT,
    ROUTINE,
    BOUNDARY,
    COMMITMENT,
    RELATIONSHIP,
    SUMMARY,
}

enum class CompanionRecordStatus {
    ACTIVE,
    ARCHIVED,
    SUPERSEDED,
    DELETED,
    ;

    companion object {
        fun fromStorageValue(value: String): CompanionRecordStatus =
            when (value) {
                "RETRACTED" -> DELETED
                "EXPIRED" -> ARCHIVED
                else -> entries.firstOrNull { it.name == value } ?: ARCHIVED
            }
    }
}

enum class CompanionMemorySourceKind {
    USER_EXPLICIT,
    USER_IMPLIED,
    ASSISTANT,
    IMPORTED,
}

enum class CompanionMemoryProposalAction {
    CREATE,
    UPDATE,
    SUPERSEDE,
    LINK,
    IGNORE,
}

enum class CompanionMemoryEdgeType {
    ABOUT,
    SUPPORTS,
    CONTRADICTS,
    SUPERSEDES,
    FOLLOWED_BY,
    OCCURRED_DURING,
    CAUSED_BY,
    PROMISES,
    PREFERS,
    AVOIDS,
    RELATES_TO,
}

enum class CompanionMemoryEdgeStatus {
    ACTIVE,
    RETRACTED,
    EXPIRED,
}

@Entity(
    tableName = "companion_memory_records",
    indices = [
        Index(value = ["profileId", "status"]),
        Index(value = ["profileId", "companionId", "scope", "status"]),
        Index(value = ["profileId", "scope", "type", "subjectKey", "predicate", "status"]),
        Index(value = ["conversationId"]),
        Index(value = ["normalizedValue"]),
        Index(value = ["updatedAt"]),
    ],
)
data class CompanionMemoryRecordEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val profileId: String,
    val companionId: String = "",
    val conversationId: String = "",
    val scope: String,
    val type: String,
    val subjectKey: String,
    val predicate: String,
    val valueJson: String,
    val normalizedValue: String,
    val status: String = CompanionRecordStatus.ACTIVE.name,
    val confidence: Double = 1.0,
    val importance: Double = 0.5,
    val validFrom: Long? = null,
    val validUntil: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastConfirmedAt: Long = System.currentTimeMillis(),
    val lastAccessedAt: Long = 0L,
    val sourceKind: String,
    val versionOfId: String? = null,
    val supersedesId: String? = null,
    val reviewAt: Long? = null,
)

@Entity(
    tableName = "companion_memory_evidence",
    foreignKeys = [
        ForeignKey(
            entity = CompanionMemoryRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["memoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["memoryId"]),
        Index(value = ["conversationId", "messageTimestamp"]),
        Index(
            value = ["memoryId", "conversationId", "messageTimestamp", "speaker"],
            unique = true,
        ),
    ],
)
data class CompanionMemoryEvidenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val memoryId: String,
    val conversationId: String,
    val messageId: Long? = null,
    val messageTimestamp: Long,
    val quote: String,
    val speaker: String,
    val timestamp: Long,
)

@Entity(
    tableName = "companion_memory_edges",
    foreignKeys = [
        ForeignKey(
            entity = CompanionMemoryRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["fromMemoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CompanionMemoryRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["toMemoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["fromMemoryId", "status"]),
        Index(value = ["toMemoryId", "status"]),
        Index(value = ["type", "status"]),
        Index(value = ["fromMemoryId", "toMemoryId", "type", "status"], unique = true),
    ],
)
data class CompanionMemoryEdgeEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val fromMemoryId: String,
    val toMemoryId: String,
    val type: String,
    val confidence: Double = 1.0,
    val strength: Double = 0.5,
    val evidenceMessageIds: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val validUntil: Long? = null,
    val status: String = CompanionMemoryEdgeStatus.ACTIVE.name,
)

@Entity(
    tableName = "companion_memory_episodes",
    indices = [
        Index(value = ["profileId", "companionId", "createdAt"]),
        Index(value = ["conversationId", "endMessageTimestamp"]),
    ],
)
data class CompanionMemoryEpisodeEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val profileId: String,
    val companionId: String = "",
    val conversationId: String,
    val startMessageTimestamp: Long,
    val endMessageTimestamp: Long,
    val summary: String,
    val topicTags: String = "",
    val emotionState: String = "",
    val outcome: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val validUntil: Long? = null,
)

data class CompanionMemoryProposal(
    val action: CompanionMemoryProposalAction = CompanionMemoryProposalAction.CREATE,
    val scope: CompanionRecordScope,
    val type: CompanionMemoryType,
    val subjectKey: String,
    val predicate: String,
    val value: String,
    val displayLabel: String? = null,
    val normalizedValue: String,
    val confidence: Double,
    val importance: Double,
    val sourceKind: CompanionMemorySourceKind,
    val conversationId: String,
    val messageId: Long? = null,
    val messageTimestamp: Long,
    val evidenceQuote: String,
    val evidenceSpeaker: String = "user",
    val validFrom: Long? = null,
    val validUntil: Long? = null,
    val reviewAt: Long? = null,
    val memoryId: String? = null,
    val relatedMemoryId: String? = null,
    val edgeType: CompanionMemoryEdgeType? = null,
    val edgeStrength: Double = 0.5,
    val evidenceMessageIds: List<Long> = emptyList(),
)
