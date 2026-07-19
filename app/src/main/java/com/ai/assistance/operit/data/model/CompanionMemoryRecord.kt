package com.ai.assistance.operit.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import java.util.UUID
import kotlinx.serialization.Serializable

enum class CompanionRecordScope {
    USER,
    COMPANION,
    RELATIONSHIP,
    CONVERSATION,
}

enum class MemorySubjectScope { USER, COMPANION, RELATIONSHIP, CONVERSATION }

enum class MemoryOwnerScope { GLOBAL_USER, COMPANION_PRIVATE, RELATIONSHIP_PAIR, CONVERSATION }

enum class MemoryVisibility { PRIVATE, ALL_COMPANIONS, GRANTED, PARTICIPANTS }

enum class MemoryPerspective { SELF_EXPERIENCED, USER_DISCLOSED, USER_SHARED, IMPORTED }

enum class MemoryEvidenceKind { USER_DIRECT, USER_INFERRED, IMPORTED }

enum class MemoryTriggerKind { EXPLICIT_REQUEST, AUTO_EXTRACT, USER_SELECTED }

enum class MemoryGrantPermission { READ }

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
@Serializable
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
    @ColumnInfo(defaultValue = "'USER'")
    val subjectScope: String = MemorySubjectScope.USER.name,
    @ColumnInfo(defaultValue = "'GLOBAL_USER'")
    val ownerScope: String = MemoryOwnerScope.GLOBAL_USER.name,
    @ColumnInfo(defaultValue = "''")
    val ownerCompanionId: String = "",
    @ColumnInfo(defaultValue = "'PRIVATE'")
    val visibility: String = MemoryVisibility.PRIVATE.name,
    @ColumnInfo(defaultValue = "'USER_DISCLOSED'")
    val perspective: String = MemoryPerspective.USER_DISCLOSED.name,
    @ColumnInfo(defaultValue = "'USER_DIRECT'")
    val evidenceKind: String = MemoryEvidenceKind.USER_DIRECT.name,
    @ColumnInfo(defaultValue = "'AUTO_EXTRACT'")
    val triggerKind: String = MemoryTriggerKind.AUTO_EXTRACT.name,
    @ColumnInfo(defaultValue = "0")
    val needsReview: Boolean = false,
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
@Serializable
data class CompanionMemoryEvidenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val memoryId: String,
    val conversationId: String,
    val messageId: Long? = null,
    val messageTimestamp: Long,
    val quote: String,
    val speaker: String,
    val timestamp: Long,
    @ColumnInfo(defaultValue = "'USER_DIRECT'")
    val evidenceKind: String = MemoryEvidenceKind.USER_DIRECT.name,
)

@Entity(
    tableName = "companion_memory_grants",
    foreignKeys = arrayOf(
        ForeignKey(
            entity = CompanionMemoryRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["memoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ),
    indices = [
        Index(value = ["memoryId"]),
        Index(value = ["granteeCompanionId", "revokedAt"]),
        Index(value = ["memoryId", "granteeCompanionId"], unique = true),
    ],
)
@Serializable
data class MemoryGrantEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val memoryId: String,
    val granteeCompanionId: String,
    val permission: String = MemoryGrantPermission.READ.name,
    val includeSuccessors: Boolean = true,
    val grantedBy: String = "USER",
    val grantedAt: Long = System.currentTimeMillis(),
    val revokedAt: Long? = null,
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
@Serializable
data class CompanionMemoryEdgeEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val fromMemoryId: String,
    val toMemoryId: String,
    val type: String,
    val confidence: Double = 1.0,
    val strength: Double = 0.5,
    val evidenceMessageIds: String = "",
    /**
     * 关系证据的可移植引用。
     *
     * `evidenceMessageIds` 是当前数据库的自增主键，跨设备不可复用。这里长期保存
     * `conversationId + messageTimestamp`。导入时即使目标聊天尚未恢复，也会保留引用；
     * 完成本机消息 ID 绑定后仍不删除，供下一次跨设备导出继续使用。
     */
    val pendingEvidenceReferencesJson: String? = null,
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
@Serializable
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
    val ownerScope: MemoryOwnerScope? = null,
    val visibility: MemoryVisibility? = null,
    val perspective: MemoryPerspective? = null,
    val evidenceKind: MemoryEvidenceKind? = null,
    val triggerKind: MemoryTriggerKind? = null,
)
