package com.ai.assistance.operit.data.repository

import android.content.Context
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.model.CompanionMemoryEpisodeEntity
import java.util.UUID

class ChapterRepository(context: Context) {
    private val dao = AppDatabase.getDatabase(context.applicationContext).companionMemoryDao()

    suspend fun save(
        profileId: String,
        companionId: String,
        conversationId: String,
        startMessageTimestamp: Long,
        endMessageTimestamp: Long,
        summary: String,
        topicTags: String = "",
        emotionState: String = "",
        outcome: String = "",
        nowMs: Long = System.currentTimeMillis(),
    ): String? {
        val cleanSummary = summary.trim()
        if (
            profileId.isBlank() ||
                companionId.isBlank() ||
                conversationId.isBlank() ||
                cleanSummary.isBlank()
        ) {
            return null
        }
        val chapter =
            CompanionMemoryEpisodeEntity(
                id = UUID.randomUUID().toString(),
                profileId = profileId,
                companionId = companionId,
                conversationId = conversationId,
                startMessageTimestamp = minOf(startMessageTimestamp, endMessageTimestamp),
                endMessageTimestamp = maxOf(startMessageTimestamp, endMessageTimestamp),
                summary = cleanSummary.take(2_000),
                topicTags = topicTags.trim(),
                emotionState = emotionState.trim(),
                outcome = outcome.trim(),
                createdAt = nowMs,
            )
        dao.insertEpisode(chapter)
        return chapter.id
    }

    suspend fun getRecent(
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
}
