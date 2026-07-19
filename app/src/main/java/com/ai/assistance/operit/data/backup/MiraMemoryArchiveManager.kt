package com.ai.assistance.operit.data.backup

import android.content.Context
import androidx.room.withTransaction
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.model.CompanionMemoryEdgeEntity
import com.ai.assistance.operit.data.model.CompanionMemoryEpisodeEntity
import com.ai.assistance.operit.data.model.CompanionMemoryEvidenceEntity
import com.ai.assistance.operit.data.model.CompanionMemoryOwnershipKeys
import com.ai.assistance.operit.data.model.CompanionMemoryRecordEntity
import com.ai.assistance.operit.data.model.ImportStrategy
import com.ai.assistance.operit.data.model.MemoryExportData
import com.ai.assistance.operit.data.model.MemoryGrantEntity
import com.ai.assistance.operit.data.model.MemoryImportResult
import com.ai.assistance.operit.data.repository.MemoryRepository
import com.ai.assistance.operit.util.AppLogger
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/**
 * 可移植的 Mira 记忆包。
 *
 * 旧 ObjectBox 记忆仍承担通用知识库能力；角色私有记忆、证据、事件、关系边和授权位于 Room。
 * 用户看到的“记忆导出”必须同时包含两者，否则恢复后角色关系视角会残缺。
 */
object MiraMemoryArchiveManager {
    private const val FORMAT = "mira_memory_archive"
    private const val FORMAT_VERSION = 2
    private val archiveMutex = Mutex()

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Serializable
    data class PortableMessageReference(
        val conversationId: String,
        val messageTimestamp: Long,
    )

    @Serializable
    data class PortableEdgeEvidence(
        val edgeId: String,
        val messages: List<PortableMessageReference> = emptyList(),
    )

    @Serializable
    data class StructuredMemoryPayload(
        val records: List<CompanionMemoryRecordEntity> = emptyList(),
        val evidence: List<CompanionMemoryEvidenceEntity> = emptyList(),
        val episodes: List<CompanionMemoryEpisodeEntity> = emptyList(),
        val edges: List<CompanionMemoryEdgeEntity> = emptyList(),
        val edgeEvidence: List<PortableEdgeEvidence> = emptyList(),
        val grants: List<MemoryGrantEntity> = emptyList(),
    )

    @Serializable
    data class MiraMemoryArchive(
        val format: String = FORMAT,
        val version: Int = FORMAT_VERSION,
        val exportedAt: Long,
        val sourceProfileId: String,
        val legacyMemory: MemoryExportData,
        val structuredMemory: StructuredMemoryPayload = StructuredMemoryPayload(),
    )

    data class ArchiveStats(
        val legacyMemories: Int,
        val legacyLinks: Int,
        val structuredRecords: Int,
        val evidence: Int,
        val episodes: Int,
        val structuredEdges: Int,
        val edgeEvidenceReferences: Int,
        val grants: Int,
    ) {
        val totalMemories: Int
            get() = legacyMemories + structuredRecords

        val totalLinks: Int
            get() = legacyLinks + structuredEdges

        val totalEvidence: Int
            get() = evidence + edgeEvidenceReferences
    }

    data class ExportResult(
        val json: String,
        val stats: ArchiveStats,
    )

    data class FileExportResult(
        val file: File,
        val stats: ArchiveStats,
    )

    internal fun reserveExportFile(directory: File, nowMs: Long): File {
        require(directory.isDirectory || directory.mkdirs()) {
            "Memory backup directory is unavailable: ${directory.absolutePath}"
        }
        val timestamp =
            SimpleDateFormat("yyyy-MM-dd_HH-mm-ss_SSS", Locale.US).format(Date(nowMs))
        return File.createTempFile("memory_backup_mira_${timestamp}_", ".json", directory)
    }

    suspend fun exportToBackupDir(context: Context, profileId: String): FileExportResult =
        withContext(Dispatchers.IO) {
            val result = export(context, profileId)
            val file = reserveExportFile(OperitBackupDirs.memoryDir(), System.currentTimeMillis())
            val staged = File.createTempFile(".mira-memory-export-", ".tmp", file.parentFile)
            var completed = false
            try {
                staged.writeText(result.json, Charsets.UTF_8)
                AtomicRestoreFileOps.moveReplacing(staged, file)
                completed = true
            } finally {
                staged.delete()
                if (!completed) file.delete()
            }
            FileExportResult(file = file, stats = result.stats)
        }

    suspend fun getStats(context: Context, profileId: String): ArchiveStats =
        withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            val (legacyMemories, legacyLinks) =
                MemoryRepository(appContext, profileId).getPortableExportCounts()
            val database = AppDatabase.getDatabase(appContext)
            val dao = database.companionMemoryDao()
            val messageDao = database.messageDao()
            val records = dao.getAllRecordsForExport(profileId)
            val evidence =
                records
                    .map { it.id }
                    .chunked(SQLITE_BIND_CHUNK_SIZE)
                    .flatMap { ids -> dao.getAllEvidenceForExport(ids) }
            val edges = dao.getAllEdgesForExport(profileId)
            val edgeEvidence = buildPortableEdgeEvidence(edges, messageDao, evidence)
            ArchiveStats(
                legacyMemories = legacyMemories,
                legacyLinks = legacyLinks,
                structuredRecords = dao.countRecordsForExport(profileId),
                evidence = dao.countEvidenceForExport(profileId),
                episodes = dao.countEpisodesForExport(profileId),
                structuredEdges = edges.size,
                edgeEvidenceReferences = edgeEvidence.sumOf { it.messages.size },
                grants = dao.countGrantsForExport(profileId),
            )
        }

    suspend fun export(context: Context, profileId: String): ExportResult =
        archiveMutex.withLock {
            withContext(Dispatchers.IO) {
                val archive = loadArchive(context.applicationContext, profileId)
                validateArchive(archive, profileId)
                ExportResult(
                    json = json.encodeToString(archive),
                    stats = archive.stats(),
                )
            }
        }

    private suspend fun loadArchive(context: Context, profileId: String): MiraMemoryArchive {
        val legacyJson = MemoryRepository(context, profileId).exportMemoriesToJson()
        val legacy = json.decodeFromString<MemoryExportData>(legacyJson)
        val database = AppDatabase.getDatabase(context)
        val dao = database.companionMemoryDao()
        val messageDao = database.messageDao()
        val records = normalizeRecordsForExport(dao.getAllRecordsForExport(profileId))
        val evidence =
            records
                .map { it.id }
                .chunked(SQLITE_BIND_CHUNK_SIZE)
                .flatMap { ids -> dao.getAllEvidenceForExport(ids) }
        val edges = dao.getAllEdgesForExport(profileId)
        return MiraMemoryArchive(
            exportedAt = System.currentTimeMillis(),
            sourceProfileId = profileId,
            legacyMemory = legacy,
            structuredMemory =
                StructuredMemoryPayload(
                    records = records,
                    evidence = evidence.map { it.copy(id = 0L, messageId = null) },
                    episodes = dao.getAllEpisodesForExport(profileId),
                    edges =
                        edges.map {
                            it.copy(
                                evidenceMessageIds = "[]",
                                pendingEvidenceReferencesJson = null,
                            )
                        },
                    edgeEvidence = buildPortableEdgeEvidence(edges, messageDao, evidence),
                    grants = dao.getAllGrantsForExport(profileId),
                ),
        )
    }

    suspend fun importFromJson(
        context: Context,
        targetProfileId: String,
        jsonString: String,
        strategy: ImportStrategy,
    ): MemoryImportResult = archiveMutex.withLock {
        withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            val root = json.parseToJsonElement(jsonString).jsonObject
            val declaredFormat = root["format"]?.jsonPrimitive?.contentOrNull
            require(declaredFormat == null || declaredFormat == FORMAT) {
                "Unsupported memory archive format: $declaredFormat"
            }
            val archive =
                if (declaredFormat == FORMAT) {
                    json.decodeFromString<MiraMemoryArchive>(jsonString)
                } else {
                    // 旧版 memory_backup_*.json 仍可直接导入。
                    MiraMemoryArchive(
                        exportedAt = System.currentTimeMillis(),
                        sourceProfileId = targetProfileId,
                        legacyMemory = json.decodeFromString<MemoryExportData>(jsonString),
                    )
                }

            require(archive.version in 1..FORMAT_VERSION) {
                "Unsupported Mira memory archive version: ${archive.version}"
            }
            validateArchive(archive, targetProfileId)

            val remappedLegacy = archive.legacyMemory.remapProfile(targetProfileId)
            val legacyRepository = MemoryRepository(appContext, targetProfileId)
            val legacySnapshot =
                legacyRepository.capturePortableImportSnapshot(
                    remappedLegacy.memories.mapTo(hashSetOf()) { it.uuid },
                )
            val createdLegacyMemoryIds = linkedSetOf<Long>()
            val legacyResult: MemoryImportResult
            val structuredResult: MemoryImportResult
            try {
                legacyResult =
                    legacyRepository.importMemoriesFromJson(
                        jsonString = json.encodeToString(remappedLegacy),
                        strategy = strategy,
                        onMemoryCreated = { id ->
                            createdLegacyMemoryIds.add(id)
                            Unit
                        },
                    )
                structuredResult =
                    importStructured(
                        context = appContext,
                        targetProfileId = targetProfileId,
                        payload = archive.structuredMemory,
                        strategy = strategy,
                        archiveVersion = archive.version,
                    )
            } catch (error: Exception) {
                try {
                    legacyRepository.restorePortableImportSnapshot(
                        snapshot = legacySnapshot,
                        createdMemoryIds = createdLegacyMemoryIds,
                    )
                } catch (rollbackError: Exception) {
                    error.addSuppressed(rollbackError)
                }
                throw error
            }

            MemoryImportResult(
                newMemories = legacyResult.newMemories + structuredResult.newMemories,
                updatedMemories = legacyResult.updatedMemories + structuredResult.updatedMemories,
                skippedMemories = legacyResult.skippedMemories + structuredResult.skippedMemories,
                newLinks = legacyResult.newLinks + structuredResult.newLinks,
                importedEvidence = structuredResult.importedEvidence,
                importedEpisodes = structuredResult.importedEpisodes,
                importedGrants = structuredResult.importedGrants,
                includesStructuredMemory = archive.structuredMemory.hasContent(),
            )
        }
    }

    /**
     * Export queries omit soft-deleted records. Remove version links that pointed at an omitted
     * record so the portable archive never contains dangling references.
     */
    internal fun normalizeRecordsForExport(
        records: List<CompanionMemoryRecordEntity>,
    ): List<CompanionMemoryRecordEntity> {
        val exportedIds = records.mapTo(hashSetOf()) { it.id }
        return records.map { record ->
            record.copy(
                versionOfId = record.versionOfId?.takeIf(exportedIds::contains),
                supersedesId = record.supersedesId?.takeIf(exportedIds::contains),
            )
        }
    }

    private suspend fun importStructured(
        context: Context,
        targetProfileId: String,
        payload: StructuredMemoryPayload,
        strategy: ImportStrategy,
        archiveVersion: Int,
    ): MemoryImportResult {
        if (!payload.hasContent()) return MemoryImportResult()

        val database = AppDatabase.getDatabase(context)
        val dao = database.companionMemoryDao()
        val messageDao = database.messageDao()
        return database.withTransaction {
            val idMap = mutableMapOf<String, String>()
            val recordsToFinalize = mutableListOf<Pair<CompanionMemoryRecordEntity, String>>()
            var createdRecords = 0
            var updatedRecords = 0
            var skippedRecords = 0

            payload.records.sortedWith(compareBy({ it.createdAt }, { it.id })).forEach { source ->
                val existingById = dao.getRecordById(source.id)?.takeIf { it.profileId == targetProfileId }
                val existing =
                    existingById
                        ?: dao.findRecordForImport(
                            profileId = targetProfileId,
                            companionId = source.companionId,
                            conversationId = source.conversationId,
                            scope = source.scope,
                            type = source.type,
                            subjectKey = source.subjectKey,
                            predicate = source.predicate,
                            normalizedValue = source.normalizedValue,
                            createdAt = source.createdAt,
                        )

                when {
                    existing != null && strategy == ImportStrategy.SKIP -> {
                        idMap[source.id] = existing.id
                        skippedRecords++
                    }

                    existing != null && strategy == ImportStrategy.UPDATE -> {
                        idMap[source.id] = existing.id
                        recordsToFinalize += source to existing.id
                        updatedRecords++
                    }

                    else -> {
                        val requestedId =
                            if (strategy == ImportStrategy.CREATE_NEW) UUID.randomUUID().toString()
                            else source.id
                        val targetId =
                            if (dao.getRecordById(requestedId) == null) requestedId
                            else UUID.randomUUID().toString()
                        dao.insertRecord(
                            source.copy(
                                id = targetId,
                                profileId = targetProfileId,
                                versionOfId = null,
                                supersedesId = null,
                            )
                        )
                        idMap[source.id] = targetId
                        recordsToFinalize += source to targetId
                        createdRecords++
                    }
                }
            }

            recordsToFinalize.forEach { (source, targetId) ->
                val versionOfId =
                    source.versionOfId?.let { referencedId ->
                        idMap[referencedId]
                            ?: dao.getRecordById(referencedId)
                                ?.takeIf { it.profileId == targetProfileId }
                                ?.id
                    }
                val supersedesId =
                    source.supersedesId?.let { referencedId ->
                        idMap[referencedId]
                            ?: dao.getRecordById(referencedId)
                                ?.takeIf { it.profileId == targetProfileId }
                                ?.id
                    }
                dao.updateRecord(
                    source.copy(
                        id = targetId,
                        profileId = targetProfileId,
                        versionOfId = versionOfId,
                        supersedesId = supersedesId,
                    )
                )
            }

            val importedMessageIds = mutableMapOf<Long, Long?>()
            val portableReferencesByLegacyMessageId =
                payload.evidence
                    .mapNotNull { item ->
                        item.messageId?.let { messageId ->
                            messageId to
                                PortableMessageReference(
                                    conversationId = item.conversationId,
                                    messageTimestamp = item.messageTimestamp,
                                )
                        }
                    }
                    .toMap()
            var evidenceCount = 0
            payload.evidence.forEach { source ->
                val targetMemoryId = idMap[source.memoryId] ?: return@forEach
                val resolvedMessageId =
                    messageDao
                        .getMessageByTimestamp(
                            chatId = source.conversationId,
                            timestamp = source.messageTimestamp,
                        )
                        ?.messageId
                source.messageId?.let { oldMessageId ->
                    importedMessageIds[oldMessageId] = resolvedMessageId
                }
                val existing =
                    dao.findEvidenceForImport(
                        memoryId = targetMemoryId,
                        conversationId = source.conversationId,
                        messageTimestamp = source.messageTimestamp,
                        speaker = source.speaker,
                    )
                when {
                    existing == null -> {
                        val inserted =
                            dao.insertEvidence(
                                source.copy(
                                    id = 0L,
                                    memoryId = targetMemoryId,
                                    messageId = resolvedMessageId,
                                )
                            )
                        if (inserted > 0L) evidenceCount++
                    }

                    strategy == ImportStrategy.UPDATE -> {
                        dao.updateEvidence(
                            source.copy(
                                id = existing.id,
                                memoryId = targetMemoryId,
                                messageId = resolvedMessageId,
                            )
                        )
                        evidenceCount++
                    }
                }
            }

            var edgeCount = 0
            var edgeEvidenceCount = 0
            val portableEdgeEvidence = payload.edgeEvidence.associateBy { it.edgeId }
            payload.edges.forEach { source ->
                val fromId = idMap[source.fromMemoryId] ?: return@forEach
                val toId = idMap[source.toMemoryId] ?: return@forEach
                val legacyMessageIds = decodeMessageIds(source.evidenceMessageIds)
                val messageReferences =
                    if (archiveVersion >= 2) {
                        portableEdgeEvidence[source.id]?.messages.orEmpty()
                    } else {
                        legacyMessageIds.mapNotNull(portableReferencesByLegacyMessageId::get)
                    }
                val resolvedEdgeEvidence =
                    if (archiveVersion >= 2) {
                        resolvePortableMessageReferences(messageReferences, messageDao)
                    } else {
                        val resolvedReferences =
                            resolvePortableMessageReferences(messageReferences, messageDao)
                        ResolvedMessageReferences(
                            messageIds =
                                (remapEvidenceMessageIds(
                                    source.evidenceMessageIds,
                                    importedMessageIds,
                                ) + resolvedReferences.messageIds).distinct(),
                            unresolved = resolvedReferences.unresolved,
                        )
                    }
                val existing = dao.findEdgeForImport(fromId, toId, source.type, source.status)
                when {
                    existing == null -> {
                        dao.upsertEdge(
                            source.copy(
                                id = UUID.randomUUID().toString(),
                                fromMemoryId = fromId,
                                toMemoryId = toId,
                                evidenceMessageIds = encodeMessageIds(resolvedEdgeEvidence.messageIds),
                                pendingEvidenceReferencesJson =
                                    encodePortableMessageReferences(messageReferences),
                            )
                        )
                        edgeCount++
                        edgeEvidenceCount +=
                            resolvedEdgeEvidence.messageIds.size + resolvedEdgeEvidence.unresolved.size
                    }

                    strategy == ImportStrategy.UPDATE -> {
                        dao.upsertEdge(
                            source.copy(
                                id = existing.id,
                                fromMemoryId = fromId,
                                toMemoryId = toId,
                                evidenceMessageIds = encodeMessageIds(resolvedEdgeEvidence.messageIds),
                                pendingEvidenceReferencesJson =
                                    encodePortableMessageReferences(messageReferences),
                            )
                        )
                        edgeEvidenceCount +=
                            resolvedEdgeEvidence.messageIds.size + resolvedEdgeEvidence.unresolved.size
                    }
                }
            }

            var episodeCount = 0
            payload.episodes.forEach { source ->
                val existing =
                    dao.findEpisodeForImport(
                        profileId = targetProfileId,
                        companionId = source.companionId,
                        conversationId = source.conversationId,
                        startMessageTimestamp = source.startMessageTimestamp,
                        endMessageTimestamp = source.endMessageTimestamp,
                        createdAt = source.createdAt,
                    )
                when {
                    existing == null || strategy == ImportStrategy.CREATE_NEW -> {
                        dao.insertEpisode(
                            source.copy(
                                id = UUID.randomUUID().toString(),
                                profileId = targetProfileId,
                            )
                        )
                        episodeCount++
                    }

                    strategy == ImportStrategy.UPDATE -> {
                        dao.insertEpisode(source.copy(id = existing.id, profileId = targetProfileId))
                        episodeCount++
                    }
                }
            }

            var grantCount = 0
            payload.grants.forEach { source ->
                val memoryId = idMap[source.memoryId] ?: return@forEach
                val existing = dao.findGrantForImport(memoryId, source.granteeCompanionId)
                when {
                    existing == null -> {
                        dao.upsertGrant(
                            source.copy(
                                id = UUID.randomUUID().toString(),
                                memoryId = memoryId,
                            )
                        )
                        grantCount++
                    }

                    strategy == ImportStrategy.UPDATE -> {
                        dao.upsertGrant(source.copy(id = existing.id, memoryId = memoryId))
                        grantCount++
                    }
                }
            }

            MemoryImportResult(
                newMemories = createdRecords,
                updatedMemories = updatedRecords,
                skippedMemories = skippedRecords,
                newLinks = edgeCount,
                importedEvidence = evidenceCount + edgeEvidenceCount,
                importedEpisodes = episodeCount,
                importedGrants = grantCount,
                includesStructuredMemory = true,
            )
        }
    }

    private fun MemoryExportData.remapProfile(targetProfileId: String): MemoryExportData =
        copy(
            memories =
                memories.map { memory ->
                    if (CompanionMemoryOwnershipKeys.PROFILE_ID !in memory.propertyValues) {
                        memory
                    } else {
                        memory.copy(
                            propertyValues =
                                memory.propertyValues +
                                    (CompanionMemoryOwnershipKeys.PROFILE_ID to targetProfileId),
                        )
                    }
                }
        )

    private fun MiraMemoryArchive.stats(): ArchiveStats =
        ArchiveStats(
            legacyMemories = legacyMemory.memories.size,
            legacyLinks = legacyMemory.links.size,
            structuredRecords = structuredMemory.records.size,
            evidence = structuredMemory.evidence.size,
            episodes = structuredMemory.episodes.size,
            structuredEdges = structuredMemory.edges.size,
            edgeEvidenceReferences = structuredMemory.edgeEvidence.sumOf { it.messages.size },
            grants = structuredMemory.grants.size,
        )

    suspend fun rebindUnresolvedReferences(context: Context): Int =
        withContext(Dispatchers.IO) {
            val database = AppDatabase.getDatabase(context.applicationContext)
            database.withTransaction {
                val dao = database.companionMemoryDao()
                val messageDao = database.messageDao()
                val evidenceCount = dao.rebindUnresolvedEvidenceMessages()
                var edgeCount = 0
                dao.getEdgesWithPendingEvidenceReferences().forEach { edge ->
                    val pending = decodePortableMessageReferences(edge.pendingEvidenceReferencesJson)
                    val resolved = resolvePortableMessageReferences(pending, messageDao)
                    val existingIds = decodeMessageIds(edge.evidenceMessageIds)
                    val liveExistingIds =
                        existingIds
                            .chunked(SQLITE_BIND_CHUNK_SIZE)
                            .flatMap { messageDao.getMessagesByIds(it) }
                            .map { it.messageId }
                    val mergedMessageIds =
                        (liveExistingIds + resolved.messageIds).distinct()
                    if (mergedMessageIds != existingIds) {
                        dao.updateEdge(
                            edge.copy(
                                evidenceMessageIds = encodeMessageIds(mergedMessageIds),
                            )
                        )
                        edgeCount++
                    }
                }
                evidenceCount + edgeCount
            }
        }

    private suspend fun buildPortableEdgeEvidence(
        edges: List<CompanionMemoryEdgeEntity>,
        messageDao: com.ai.assistance.operit.data.dao.MessageDao,
        evidence: List<CompanionMemoryEvidenceEntity>,
    ): List<PortableEdgeEvidence> {
        val messageIds = edges.flatMap { decodeMessageIds(it.evidenceMessageIds) }.distinct()
        val messagesById =
            messageIds
                .chunked(SQLITE_BIND_CHUNK_SIZE)
                .flatMap { messageDao.getMessagesByIds(it) }
                .associateBy { it.messageId }
        val evidenceReferencesByMessageId =
            evidence
                .mapNotNull { item ->
                    item.messageId?.let { messageId ->
                        messageId to
                            PortableMessageReference(
                                conversationId = item.conversationId,
                                messageTimestamp = item.messageTimestamp,
                            )
                    }
                }
                .toMap()
        return edges.mapNotNull { edge ->
            val databaseMessageIds = decodeMessageIds(edge.evidenceMessageIds)
            val references =
                (databaseMessageIds.mapNotNull { messageId ->
                    messagesById[messageId]?.let { message ->
                        PortableMessageReference(message.chatId, message.timestamp)
                    } ?: evidenceReferencesByMessageId[messageId]
                } + decodePortableMessageReferences(edge.pendingEvidenceReferencesJson))
                    .distinct()
            if (databaseMessageIds.isNotEmpty() && references.isEmpty()) {
                AppLogger.w(
                    "MiraMemoryArchive",
                    "关系 ${edge.id} 的旧本机证据消息已不存在，导出时保留关系但无法恢复原始出处",
                )
            }
            references.takeIf { it.isNotEmpty() }?.let {
                PortableEdgeEvidence(edgeId = edge.id, messages = it)
            }
        }
    }

    private suspend fun resolvePortableMessageReferences(
        references: List<PortableMessageReference>,
        messageDao: com.ai.assistance.operit.data.dao.MessageDao,
    ): ResolvedMessageReferences {
        val resolved = mutableListOf<Long>()
        val unresolved = mutableListOf<PortableMessageReference>()
        references.distinct().forEach { reference ->
            val message =
                messageDao.getMessageByTimestamp(
                    chatId = reference.conversationId,
                    timestamp = reference.messageTimestamp,
                )
            if (message == null) unresolved += reference else resolved += message.messageId
        }
        return ResolvedMessageReferences(resolved.distinct(), unresolved.distinct())
    }

    internal fun validateArchive(archive: MiraMemoryArchive, targetProfileId: String) {
        require(targetProfileId.isNotBlank()) { "Target profile ID must not be blank" }
        require(archive.sourceProfileId.isNotBlank()) { "Source profile ID must not be blank" }
        require(archive.legacyMemory.version == "1.0" || archive.legacyMemory.version == "1.1") {
            "Unsupported legacy memory format version: ${archive.legacyMemory.version}"
        }

        val legacyIds = archive.legacyMemory.memories.map { it.uuid }
        require(legacyIds.all { it.isNotBlank() }) { "Legacy memory ID must not be blank" }
        require(legacyIds.distinct().size == legacyIds.size) { "Duplicate legacy memory IDs" }
        val legacyIdSet = legacyIds.toSet()
        require(archive.legacyMemory.links.all { it.sourceUuid in legacyIdSet && it.targetUuid in legacyIdSet }) {
            "Legacy memory link references a missing memory"
        }

        val payload = archive.structuredMemory
        val recordIds = payload.records.map { it.id }
        require(recordIds.all { it.isNotBlank() }) { "Structured memory ID must not be blank" }
        require(recordIds.distinct().size == recordIds.size) { "Duplicate structured memory IDs" }
        val recordIdSet = recordIds.toSet()
        require(payload.records.all {
            it.profileId == archive.sourceProfileId &&
            (it.versionOfId == null || it.versionOfId in recordIdSet) &&
                (it.supersedesId == null || it.supersedesId in recordIdSet)
        }) { "Structured memory profile or version chain is invalid" }
        require(payload.evidence.all { it.memoryId in recordIdSet }) {
            "Memory evidence references a missing record"
        }
        if (archive.version >= 2) {
            require(payload.evidence.all { it.id == 0L && it.messageId == null }) {
                "Portable memory evidence must not contain database-local IDs"
            }
        } else {
            val evidenceMessageIds = payload.evidence.mapNotNullTo(hashSetOf()) { it.messageId }
            require(
                payload.edges
                    .flatMap { decodeMessageIds(it.evidenceMessageIds) }
                    .all { it in evidenceMessageIds }
            ) { "Legacy edge evidence references a missing evidence message" }
        }
        require(payload.edges.all { it.fromMemoryId in recordIdSet && it.toMemoryId in recordIdSet }) {
            "Memory edge references a missing record"
        }
        require(payload.grants.all { it.memoryId in recordIdSet }) {
            "Memory grant references a missing record"
        }
        require(payload.grants.map { it.id }.all { it.isNotBlank() }) {
            "Memory grant ID must not be blank"
        }
        require(payload.grants.map { it.id }.distinct().size == payload.grants.size) {
            "Duplicate memory grant IDs"
        }
        require(
            payload.grants
                .map { it.memoryId to it.granteeCompanionId }
                .distinct()
                .size == payload.grants.size
        ) { "Duplicate memory grant recipients" }

        require(payload.edges.map { it.id }.distinct().size == payload.edges.size) {
            "Duplicate memory edge IDs"
        }
        require(payload.edges.all { it.id.isNotBlank() }) { "Memory edge ID must not be blank" }
        if (archive.version >= 2) {
            require(payload.edges.all { decodeMessageIds(it.evidenceMessageIds).isEmpty() }) {
                "Portable memory edges must not contain database-local message IDs"
            }
        }
        val edgeIds = payload.edges.mapTo(hashSetOf()) { it.id }
        require(payload.edgeEvidence.map { it.edgeId }.distinct().size == payload.edgeEvidence.size) {
            "Duplicate edge evidence entries"
        }
        require(payload.edgeEvidence.all { it.edgeId in edgeIds }) {
            "Edge evidence references a missing edge"
        }
        require(payload.edgeEvidence.flatMap { it.messages }.all {
            it.conversationId.isNotBlank() && it.messageTimestamp > 0L
        }) { "Edge evidence contains an invalid message reference" }
    }

    private fun remapEvidenceMessageIds(
        encoded: String,
        importedMessageIds: Map<Long, Long?>,
    ): List<Long> {
        val oldIds = decodeMessageIds(encoded)
        return oldIds.mapNotNull(importedMessageIds::get).distinct()
    }

    private fun decodeMessageIds(encoded: String): List<Long> {
        if (encoded.isBlank()) return emptyList()
        val elements = json.parseToJsonElement(encoded).jsonArray
        val ids = elements.map { element ->
            requireNotNull(element.jsonPrimitive.longOrNull) {
                "Memory edge evidence contains a non-integer message ID"
            }
        }
        return ids.distinct()
    }

    private fun encodeMessageIds(ids: List<Long>): String =
        buildJsonArray { ids.distinct().forEach { add(JsonPrimitive(it)) } }.toString()

    private fun decodePortableMessageReferences(encoded: String?): List<PortableMessageReference> {
        if (encoded.isNullOrBlank()) return emptyList()
        return json.decodeFromString<List<PortableMessageReference>>(encoded).distinct()
    }

    private fun encodePortableMessageReferences(
        references: List<PortableMessageReference>,
    ): String? =
        references.distinct().takeIf { it.isNotEmpty() }?.let { json.encodeToString(it) }

    private data class ResolvedMessageReferences(
        val messageIds: List<Long>,
        val unresolved: List<PortableMessageReference>,
    )

    private fun StructuredMemoryPayload.hasContent(): Boolean =
        records.isNotEmpty() ||
            evidence.isNotEmpty() ||
            episodes.isNotEmpty() ||
            edges.isNotEmpty() ||
            edgeEvidence.isNotEmpty() ||
            grants.isNotEmpty()

    private const val SQLITE_BIND_CHUNK_SIZE = 500
}
