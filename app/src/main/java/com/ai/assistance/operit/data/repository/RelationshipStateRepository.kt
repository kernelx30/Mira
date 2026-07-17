package com.ai.assistance.operit.data.repository

import android.content.Context
import com.ai.assistance.operit.data.model.CompanionMemoryRecordEntity
import com.ai.assistance.operit.data.model.CompanionMemoryType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class RelationshipState(
    val profileId: String,
    val companionId: String,
    val preferredAddress: String? = null,
    val relationshipFacts: List<CompanionMemoryRecordEntity> = emptyList(),
    val commitments: List<CompanionMemoryRecordEntity> = emptyList(),
    val boundaries: List<CompanionMemoryRecordEntity> = emptyList(),
    val recentEvents: List<CompanionMemoryRecordEntity> = emptyList(),
)

class RelationshipStateRepository(context: Context) {
    private val memoryRepository = CompanionMemoryRepository(context.applicationContext)

    fun observe(profileId: String, companionId: String): Flow<RelationshipState> =
        memoryRepository.observeRelationshipRecords(profileId, companionId).map { records ->
            buildRelationshipState(profileId, companionId, records)
        }
}

internal fun buildRelationshipState(
    profileId: String,
    companionId: String,
    records: List<CompanionMemoryRecordEntity>,
): RelationshipState {
    val sorted = records.sortedByDescending { it.updatedAt }
    return RelationshipState(
        profileId = profileId,
        companionId = companionId,
        preferredAddress =
            sorted.firstOrNull { it.predicate == "preferred_address" }?.decodedValue(),
        relationshipFacts =
            sorted.filter { it.type == CompanionMemoryType.RELATIONSHIP.name },
        commitments =
            sorted.filter { it.type == CompanionMemoryType.COMMITMENT.name },
        boundaries =
            sorted.filter { it.type == CompanionMemoryType.BOUNDARY.name },
        recentEvents =
            sorted.filter { it.type == CompanionMemoryType.EVENT.name },
    )
}
