package com.ai.assistance.operit.data.backup

import com.ai.assistance.operit.data.model.CompanionMemoryEdgeEntity
import com.ai.assistance.operit.data.model.CompanionMemoryEpisodeEntity
import com.ai.assistance.operit.data.model.CompanionMemoryEvidenceEntity
import com.ai.assistance.operit.data.model.CompanionMemoryRecordEntity
import com.ai.assistance.operit.data.model.MemoryExportData
import com.ai.assistance.operit.data.model.MemoryGrantEntity
import java.util.Date
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class MiraMemoryArchiveModelTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun archiveRoundTripKeepsStructuredMemoryReferences() {
        val record =
            CompanionMemoryRecordEntity(
                id = "memory-1",
                profileId = "profile-a",
                companionId = "character:mira",
                conversationId = "chat-1",
                scope = "RELATIONSHIP",
                type = "PREFERENCE",
                subjectKey = "user",
                predicate = "likes",
                valueJson = "{\"value\":\"Genshin Impact\"}",
                normalizedValue = "genshin impact",
                sourceKind = "USER_EXPLICIT",
                ownerScope = "RELATIONSHIP_PAIR",
                ownerCompanionId = "character:mira",
            )
        val payload =
            MiraMemoryArchiveManager.StructuredMemoryPayload(
                records = listOf(record),
                evidence =
                    listOf(
                        CompanionMemoryEvidenceEntity(
                            id = 0L,
                            memoryId = record.id,
                            conversationId = "chat-1",
                            messageId = null,
                            messageTimestamp = 100L,
                            quote = "我喜欢玩原神",
                            speaker = "user",
                            timestamp = 100L,
                        )
                    ),
                episodes =
                    listOf(
                        CompanionMemoryEpisodeEntity(
                            id = "episode-1",
                            profileId = "profile-a",
                            companionId = "character:mira",
                            conversationId = "chat-1",
                            startMessageTimestamp = 100L,
                            endMessageTimestamp = 200L,
                            summary = "聊过游戏偏好",
                        )
                    ),
                edges =
                    listOf(
                        CompanionMemoryEdgeEntity(
                            id = "edge-1",
                            fromMemoryId = record.id,
                            toMemoryId = record.id,
                            type = "RELATES_TO",
                            evidenceMessageIds = "[]",
                        )
                    ),
                edgeEvidence =
                    listOf(
                        MiraMemoryArchiveManager.PortableEdgeEvidence(
                            edgeId = "edge-1",
                            messages =
                                listOf(
                                    MiraMemoryArchiveManager.PortableMessageReference(
                                        conversationId = "chat-1",
                                        messageTimestamp = 100L,
                                    )
                                ),
                        )
                    ),
                grants =
                    listOf(
                        MemoryGrantEntity(
                            id = "grant-1",
                            memoryId = record.id,
                            granteeCompanionId = "character:luna",
                        )
                    ),
            )
        val archive =
            MiraMemoryArchiveManager.MiraMemoryArchive(
                exportedAt = 123L,
                sourceProfileId = "profile-a",
                legacyMemory =
                    MemoryExportData(
                        memories = emptyList(),
                        links = emptyList(),
                        exportDate = Date(123L),
                    ),
                structuredMemory = payload,
            )

        val decoded =
            json.decodeFromString<MiraMemoryArchiveManager.MiraMemoryArchive>(
                json.encodeToString(archive)
            )

        assertEquals(record.id, decoded.structuredMemory.evidence.single().memoryId)
        assertEquals(record.id, decoded.structuredMemory.edges.single().fromMemoryId)
        assertEquals(
            100L,
            decoded.structuredMemory.edgeEvidence.single().messages.single().messageTimestamp,
        )
        assertEquals(record.id, decoded.structuredMemory.grants.single().memoryId)
        assertEquals("profile-a", decoded.structuredMemory.episodes.single().profileId)
    }

    @Test
    fun exportNormalizationRemovesOnlyDanglingVersionReferences() {
        val root = memoryRecord(id = "memory-root")
        val child =
            memoryRecord(
                id = "memory-child",
                versionOfId = root.id,
                supersedesId = "deleted-memory",
            )

        val normalized = MiraMemoryArchiveManager.normalizeRecordsForExport(listOf(root, child))

        assertEquals(root, normalized[0])
        assertEquals(root.id, normalized[1].versionOfId)
        assertNull(normalized[1].supersedesId)
    }

    @Test
    fun legacyMemoryWithoutLastAccessedTimestampStillDecodes() {
        val legacyJson =
            """
            {
              "uuid": "legacy-1",
              "title": "旧记忆",
              "content": "内容",
              "contentType": "text/plain",
              "source": "user_input",
              "credibility": 1.0,
              "importance": 0.8,
              "folderPath": null,
              "createdAt": 100,
              "updatedAt": 200,
              "tagNames": [],
              "propertyValues": {}
            }
            """.trimIndent()

        val memory = json.decodeFromString<com.ai.assistance.operit.data.model.SerializableMemory>(legacyJson)

        assertNull(memory.lastAccessedAtMs)
    }

    @Test
    fun validationRejectsEdgeEvidenceForMissingEdge() {
        val archive =
            MiraMemoryArchiveManager.MiraMemoryArchive(
                exportedAt = 123L,
                sourceProfileId = "profile-a",
                legacyMemory =
                    MemoryExportData(
                        memories = emptyList(),
                        links = emptyList(),
                        exportDate = Date(123L),
                    ),
                structuredMemory =
                    MiraMemoryArchiveManager.StructuredMemoryPayload(
                        edgeEvidence =
                            listOf(
                                MiraMemoryArchiveManager.PortableEdgeEvidence(
                                    edgeId = "missing-edge",
                                    messages =
                                        listOf(
                                            MiraMemoryArchiveManager.PortableMessageReference(
                                                conversationId = "chat-1",
                                                messageTimestamp = 100L,
                                            )
                                        ),
                                )
                            )
                    ),
            )

        assertThrows(IllegalArgumentException::class.java) {
            MiraMemoryArchiveManager.validateArchive(archive, "profile-b")
        }
    }

    private fun memoryRecord(
        id: String,
        versionOfId: String? = null,
        supersedesId: String? = null,
    ) = CompanionMemoryRecordEntity(
        id = id,
        profileId = "profile-a",
        companionId = "character:mira",
        conversationId = "chat-1",
        scope = "RELATIONSHIP",
        type = "PREFERENCE",
        subjectKey = "user",
        predicate = "likes",
        valueJson = "{\"value\":\"test\"}",
        normalizedValue = "test",
        sourceKind = "USER_EXPLICIT",
        versionOfId = versionOfId,
        supersedesId = supersedesId,
    )
}
