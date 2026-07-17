package com.ai.assistance.operit.ui.features.memory.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoveToInbox
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.CompanionEventKind
import com.ai.assistance.operit.data.model.CompanionEventStatus
import com.ai.assistance.operit.data.model.CompanionMemoryOwnership
import com.ai.assistance.operit.data.model.CompanionMemoryScope
import com.ai.assistance.operit.data.model.CompanionMemoryTarget
import com.ai.assistance.operit.data.model.CompanionMemoryMetadata
import com.ai.assistance.operit.data.model.CompanionMemoryEvidenceEntity
import com.ai.assistance.operit.data.model.CompanionMemoryEdgeType
import com.ai.assistance.operit.data.model.CompanionMemoryRecordEntity
import com.ai.assistance.operit.data.model.CompanionMemoryType
import com.ai.assistance.operit.data.model.CompanionRecordScope
import com.ai.assistance.operit.data.model.CompanionRecordStatus
import com.ai.assistance.operit.data.model.Memory
import com.ai.assistance.operit.data.model.companionMetadata
import com.ai.assistance.operit.data.model.companionOwnership
import com.ai.assistance.operit.data.repository.CompanionMemoryRepository
import com.ai.assistance.operit.data.repository.CompanionMemoryRelation
import com.ai.assistance.operit.data.repository.decodedLabel
import com.ai.assistance.operit.data.repository.decodedValue
import com.ai.assistance.operit.ui.features.memory.screens.dialogs.CompanionMemoryEditorDialog
import com.ai.assistance.operit.util.AppLogger
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private enum class CompanionMemoryCategory(
    @StringRes val titleRes: Int,
    val icon: ImageVector,
    val tag: String,
) {
    TIMELINE(R.string.mate_memory_category_timeline, Icons.Default.History, "Timeline"),
    RELATIONSHIP(R.string.mate_memory_category_relationship, Icons.Default.Favorite, "Relationship"),
    EVENT(R.string.mate_memory_category_event, Icons.Default.Event, "Event"),
    PREFERENCE(R.string.mate_memory_category_preference, Icons.Default.Tune, "Preference"),
    WORLD(R.string.mate_memory_category_world, Icons.Default.Public, "World"),
}

@Composable
private fun memoryViewTitle(view: CompanionMemoryView, targetName: String): String =
    when (view) {
        CompanionMemoryView.USER ->
            stringResource(R.string.mira_memory_remembered_you, targetName)
        CompanionMemoryView.RECENT -> stringResource(R.string.mira_memory_recent_us)
    }

private fun memoryMatchesView(
    memory: Memory,
    view: CompanionMemoryView,
    activeProfileId: String,
    activeTarget: CompanionMemoryTarget,
): Boolean {
    val ownership = memory.companionOwnership()
    val metadata = memory.companionMetadata()
    val category = classifyMemory(memory)
    val isLegacy = ownership == null && metadata == null
    val belongsToProfile = ownership?.belongsToProfile(activeProfileId) == true
    val belongsToTarget = ownership?.belongsToTarget(activeTarget) == true

    return when (view) {
        CompanionMemoryView.USER ->
            (ownership?.scope == CompanionMemoryScope.USER && belongsToProfile) ||
                (isLegacy && category == CompanionMemoryCategory.PREFERENCE)
        CompanionMemoryView.RECENT -> {
            val relationshipMemory =
                (ownership?.scope == CompanionMemoryScope.RELATIONSHIP &&
                    belongsToProfile &&
                    belongsToTarget) ||
                    (isLegacy && category == CompanionMemoryCategory.RELATIONSHIP)
            val ownedEvent =
                ownership != null &&
                    belongsToProfile &&
                    category == CompanionMemoryCategory.EVENT &&
                    (ownership.scope == CompanionMemoryScope.USER ||
                        (ownership.scope == CompanionMemoryScope.RELATIONSHIP && belongsToTarget))
            val companionEvent =
                metadata != null && metadataBelongsToTarget(metadata, activeTarget)
            relationshipMemory || ownedEvent || companionEvent ||
                (isLegacy && category == CompanionMemoryCategory.EVENT)
        }
    }
}

private fun metadataBelongsToTarget(
    metadata: CompanionMemoryMetadata,
    activeTarget: CompanionMemoryTarget,
): Boolean {
    val isBound = metadata.characterId.isNotBlank() || metadata.characterGroupId.isNotBlank()
    return !isBound || activeTarget.matches(metadata.characterId, metadata.characterGroupId)
}

@Composable
private fun memoryOwnershipLabel(
    ownership: CompanionMemoryOwnership?,
    metadata: CompanionMemoryMetadata?,
    activeTarget: CompanionMemoryTarget,
): String {
    val activeTargetName =
        activeTarget.displayName.ifBlank {
            stringResource(R.string.mate_memory_current_character)
        }
    val ownerLabel = when (ownership?.scope) {
        CompanionMemoryScope.USER -> stringResource(R.string.mate_memory_scope_badge_user)
        CompanionMemoryScope.RELATIONSHIP -> {
            val ownerName =
                ownership.characterGroupName.ifBlank {
                    ownership.characterName.ifBlank { activeTargetName }
                }
            stringResource(R.string.mate_memory_scope_badge_relationship, ownerName)
        }
        CompanionMemoryScope.CHARACTER_WORLD -> {
            val ownerName =
                ownership.characterGroupName.ifBlank {
                    ownership.characterName.ifBlank { activeTargetName }
                }
            stringResource(R.string.mate_memory_scope_badge_world, ownerName)
        }
        null -> {
            val metadataName = metadata?.characterName.orEmpty()
            if (metadata != null &&
                (metadata.characterId.isNotBlank() || metadata.characterGroupId.isNotBlank())) {
                stringResource(
                    R.string.mate_memory_scope_badge_relationship,
                    metadataName.ifBlank { activeTargetName },
                )
            } else {
                stringResource(R.string.mate_memory_scope_badge_legacy)
            }
        }
    }
    return if (ownership?.recallEnabled == false) {
        stringResource(R.string.mate_memory_recall_paused_value, ownerLabel)
    } else {
        ownerLabel
    }
}

private enum class CompanionMemoryView(
    val icon: ImageVector,
    val defaultScope: CompanionMemoryScope,
    val defaultCategoryTag: String,
) {
    USER(Icons.Default.Person, CompanionMemoryScope.USER, "Preference"),
    RECENT(Icons.Default.History, CompanionMemoryScope.RELATIONSHIP, "Event"),
}

private data class StructuredMemoryEditorRequest(
    val record: CompanionMemoryRecordEntity? = null,
    val scope: CompanionRecordScope,
    val defaultType: CompanionMemoryType,
    val allowedTypes: List<CompanionMemoryType>,
)

private fun structuredEditorRequest(view: CompanionMemoryView): StructuredMemoryEditorRequest? =
    when (view) {
        CompanionMemoryView.USER ->
            StructuredMemoryEditorRequest(
                scope = CompanionRecordScope.USER,
                defaultType = CompanionMemoryType.PREFERENCE,
                allowedTypes =
                    listOf(
                        CompanionMemoryType.IDENTITY,
                        CompanionMemoryType.PREFERENCE,
                        CompanionMemoryType.FACT,
                        CompanionMemoryType.ROUTINE,
                        CompanionMemoryType.BOUNDARY,
                    ),
            )
        CompanionMemoryView.RECENT ->
            StructuredMemoryEditorRequest(
                scope = CompanionRecordScope.RELATIONSHIP,
                defaultType = CompanionMemoryType.EVENT,
                allowedTypes =
                    listOf(
                        CompanionMemoryType.EVENT,
                        CompanionMemoryType.COMMITMENT,
                        CompanionMemoryType.RELATIONSHIP,
                        CompanionMemoryType.PREFERENCE,
                        CompanionMemoryType.BOUNDARY,
                    ),
            )
    }

private fun structuredEditorRequest(record: CompanionMemoryRecordEntity): StructuredMemoryEditorRequest {
    val scope =
        CompanionRecordScope.entries.firstOrNull { it.name == record.scope }
            ?: CompanionRecordScope.USER
    val type =
        CompanionMemoryType.entries.firstOrNull { it.name == record.type }
            ?: CompanionMemoryType.FACT
    return StructuredMemoryEditorRequest(
        record = record,
        scope = scope,
        defaultType = type,
        allowedTypes = listOf(type),
    )
}

private fun CompanionMemoryTarget.structuredCompanionId(): String =
    characterGroupId.takeIf { it.isNotBlank() }?.let { "group:$it" }
        ?: characterId.takeIf { it.isNotBlank() }?.let { "character:$it" }
        ?: characterName.takeIf { it.isNotBlank() }?.let { "character_name:$it" }
        .orEmpty()

private fun structuredMemoryMatchesView(
    record: CompanionMemoryRecordEntity,
    view: CompanionMemoryView,
    activeTarget: CompanionMemoryTarget,
): Boolean {
    val targetId = activeTarget.structuredCompanionId()
    val belongsToTarget =
        record.scope == CompanionRecordScope.USER.name ||
            record.companionId.isBlank() ||
            record.companionId == targetId
    if (!belongsToTarget) return false
    return when (view) {
        CompanionMemoryView.USER ->
            record.scope == CompanionRecordScope.USER.name &&
                record.type !in setOf(
                    CompanionMemoryType.EVENT.name,
                    CompanionMemoryType.COMMITMENT.name,
                    CompanionMemoryType.SUMMARY.name,
                )
        CompanionMemoryView.RECENT ->
            record.scope in
                setOf(
                    CompanionRecordScope.RELATIONSHIP.name,
                    CompanionRecordScope.CONVERSATION.name,
                ) ||
                (record.scope == CompanionRecordScope.COMPANION.name &&
                    record.type in
                        setOf(
                            CompanionMemoryType.RELATIONSHIP.name,
                            CompanionMemoryType.EVENT.name,
                            CompanionMemoryType.COMMITMENT.name,
                        ))
    }
}

@Composable
fun CompanionMemoryArchive(
    memories: List<Memory>,
    activeProfileId: String,
    activeTarget: CompanionMemoryTarget,
    searchQuery: String,
    onMemoryClick: (Memory) -> Unit,
    onAddMemory: (CompanionMemoryScope, String) -> Unit,
    onCompanionStatusChange: (Memory, CompanionEventStatus) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val structuredRepository = remember(context) { CompanionMemoryRepository(context) }
    val coroutineScope = rememberCoroutineScope()
    val structuredRecords by
        remember(structuredRepository, activeProfileId) {
            structuredRepository.observeActiveRecords(activeProfileId)
        }.collectAsState(initial = emptyList())
    var selectedViewIndex by rememberSaveable { mutableIntStateOf(0) }
    var editorRequest by remember { mutableStateOf<StructuredMemoryEditorRequest?>(null) }
    var pendingRetraction by remember { mutableStateOf<CompanionMemoryRecordEntity?>(null) }
    var relationFocus by remember { mutableStateOf<CompanionMemoryRecordEntity?>(null) }
    var isSavingStructuredMemory by remember { mutableStateOf(false) }
    var editorErrorMessage by remember { mutableStateOf<String?>(null) }
    val views = remember { CompanionMemoryView.entries }
    val targetName =
        activeTarget.displayName.ifBlank {
            stringResource(R.string.mate_memory_current_character)
        }
    val activeCompanionId = remember(activeTarget) { activeTarget.structuredCompanionId() }

    fun openAddMemory(view: CompanionMemoryView) {
        val request = structuredEditorRequest(view)
        if (request == null) {
            onAddMemory(view.defaultScope, view.defaultCategoryTag)
        } else {
            editorErrorMessage = null
            editorRequest = request
        }
    }
    fun importLegacyMemory(memory: Memory, view: CompanionMemoryView) {
        val scope =
            when (view) {
                CompanionMemoryView.USER -> CompanionRecordScope.USER
                CompanionMemoryView.RECENT -> CompanionRecordScope.RELATIONSHIP
            }
        val metadata = memory.companionMetadata()
        val type =
            when (view) {
                CompanionMemoryView.USER ->
                    if (classifyMemory(memory) == CompanionMemoryCategory.PREFERENCE) {
                        CompanionMemoryType.PREFERENCE
                    } else {
                        CompanionMemoryType.FACT
                    }
                CompanionMemoryView.RECENT ->
                    if (classifyMemory(memory) == CompanionMemoryCategory.RELATIONSHIP) {
                        CompanionMemoryType.RELATIONSHIP
                    } else if (metadata?.kind == CompanionEventKind.PROMISE) {
                        CompanionMemoryType.COMMITMENT
                    } else {
                        CompanionMemoryType.EVENT
                    }
            }
        coroutineScope.launch {
            try {
                structuredRepository.importLegacyRecord(
                    profileId = activeProfileId,
                    companionId = activeCompanionId,
                    legacyId = memory.uuid,
                    scope = scope,
                    type = type,
                    displayLabel = memory.title,
                    value = memory.content,
                    conversationId = metadata?.chatId.orEmpty(),
                    sourceTimestamp = memory.updatedAt.time,
                    validFrom = metadata?.eventAtMs,
                )
            } catch (error: Exception) {
                AppLogger.e("CompanionMemoryArchive", "Failed to import legacy memory", error)
            }
        }
    }
    val importedLegacyIds =
        remember(structuredRecords) {
            structuredRecords
                .asSequence()
                .mapNotNull { record ->
                    record.predicate.takeIf { it.startsWith("imported:") }
                        ?.removePrefix("imported:")
                }
                .toSet()
        }
    val visibleMemories =
        remember(memories, selectedViewIndex, activeProfileId, activeTarget, importedLegacyIds) {
            val selectedView = views[selectedViewIndex]
            memories
                .asSequence()
                .filterNot { it.title == ".folder_placeholder" }
                .filterNot { it.uuid in importedLegacyIds }
                .filter { memory ->
                    memoryMatchesView(
                        memory = memory,
                        view = selectedView,
                        activeProfileId = activeProfileId,
                        activeTarget = activeTarget,
                    )
                }
                .sortedByDescending { memory ->
                    if (selectedView == CompanionMemoryView.RECENT) {
                        timelineTimestamp(memory)
                    } else {
                        memory.updatedAt.time
                    }
                }
                .toList()
        }
    val visibleStructuredRecords =
        remember(structuredRecords, selectedViewIndex, activeTarget, searchQuery) {
            val normalizedQuery = searchQuery.trim().lowercase(Locale.ROOT)
            structuredRecords
                .filter { structuredMemoryMatchesView(it, views[selectedViewIndex], activeTarget) }
                .filter { record ->
                    normalizedQuery.isBlank() ||
                        record.decodedLabel().orEmpty().lowercase(Locale.ROOT).contains(normalizedQuery) ||
                        record.decodedValue().lowercase(Locale.ROOT).contains(normalizedQuery) ||
                        record.predicate.lowercase(Locale.ROOT).contains(normalizedQuery)
                }
                .sortedByDescending { it.updatedAt }
        }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.mate_memory_archive_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text =
                        stringResource(
                            R.string.mate_memory_archive_subtitle_with_count,
                            visibleMemories.size + visibleStructuredRecords.size,
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            FilledTonalButton(
                onClick = {
                    val view = views[selectedViewIndex]
                    openAddMemory(view)
                },
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.mate_memory_add))
            }
        }

        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            views.forEachIndexed { index, view ->
                MemoryViewSelector(
                    view = view,
                    title = memoryViewTitle(view, targetName),
                    selected = selectedViewIndex == index,
                    onClick = { selectedViewIndex = index },
                )
                if (index != views.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 48.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (visibleMemories.isEmpty() && visibleStructuredRecords.isEmpty()) {
            val view = views[selectedViewIndex]
            EmptyMemoryCategory(
                onAddMemory = { openAddMemory(view) },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            if (views[selectedViewIndex] == CompanionMemoryView.RECENT) {
                CompanionTimeline(
                    memories = visibleMemories,
                    structuredMemories = visibleStructuredRecords,
                    structuredRepository = structuredRepository,
                    activeTarget = activeTarget,
                    onMemoryClick = onMemoryClick,
                    onStatusChange = onCompanionStatusChange,
                    onEditStructured = { record ->
                        editorErrorMessage = null
                        editorRequest = structuredEditorRequest(record)
                    },
                    onRetractStructured = { pendingRetraction = it },
                    onConfirmStructured = { record ->
                        coroutineScope.launch { structuredRepository.confirmRecord(record.id) }
                    },
                    onOpenRelations = { relationFocus = it },
                    onImportLegacy = { memory ->
                        importLegacyMemory(memory, CompanionMemoryView.RECENT)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(visibleStructuredRecords, key = { "structured-${it.id}" }) { record ->
                        StructuredMemoryArchiveItem(
                            record = record,
                            repository = structuredRepository,
                            onEdit = {
                                editorErrorMessage = null
                                editorRequest = structuredEditorRequest(record)
                            },
                            onRetract = { pendingRetraction = record },
                            onConfirm = {
                                coroutineScope.launch { structuredRepository.confirmRecord(record.id) }
                            },
                            onOpenRelations = { relationFocus = record },
                        )
                    }
                    items(visibleMemories, key = { it.uuid }) { memory ->
                        MemoryArchiveItem(
                            memory = memory,
                            category = classifyMemory(memory),
                            activeTarget = activeTarget,
                            onClick = { onMemoryClick(memory) },
                            onImport = { importLegacyMemory(memory, views[selectedViewIndex]) },
                        )
                    }
                }
            }
        }
    }

    editorRequest?.let { request ->
        CompanionMemoryEditorDialog(
            record = request.record,
            scope = request.scope,
            defaultType = request.defaultType,
            allowedTypes = request.allowedTypes,
            activeTargetName = targetName,
            isSaving = isSavingStructuredMemory,
            errorMessage = editorErrorMessage,
            onDismiss = {
                if (!isSavingStructuredMemory) {
                    editorRequest = null
                    editorErrorMessage = null
                }
            },
            onSave = { type, displayLabel, value ->
                coroutineScope.launch {
                    isSavingStructuredMemory = true
                    editorErrorMessage = null
                    try {
                        val savedId =
                            structuredRepository.saveManualRecord(
                                profileId = activeProfileId,
                                companionId =
                                    request.record?.companionId
                                        ?.takeIf { it.isNotBlank() }
                                        ?: activeCompanionId,
                                scope = request.scope,
                                type = type,
                                displayLabel = displayLabel,
                                value = value,
                                existingRecord = request.record,
                                conversationId = request.record?.conversationId.orEmpty(),
                            )
                        if (savedId == null) {
                            editorErrorMessage =
                                context.getString(R.string.mate_memory_manual_save_failed)
                        } else {
                            editorRequest = null
                        }
                    } catch (error: Exception) {
                        AppLogger.e("CompanionMemoryArchive", "Failed to save companion memory", error)
                        editorErrorMessage = context.getString(R.string.mate_memory_manual_save_failed)
                    } finally {
                        isSavingStructuredMemory = false
                    }
                }
            },
        )
    }

    pendingRetraction?.let { record ->
        AlertDialog(
            onDismissRequest = { pendingRetraction = null },
            title = { Text(stringResource(R.string.mate_memory_manual_delete_title)) },
            text = { Text(stringResource(R.string.mate_memory_manual_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRetraction = null
                        coroutineScope.launch {
                            structuredRepository.retractRecord(record.id)
                        }
                    },
                ) {
                    Text(stringResource(R.string.mate_memory_manual_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRetraction = null }) {
                    Text(stringResource(R.string.mate_memory_manual_cancel))
                }
            },
        )
    }

    relationFocus?.let { focus ->
        CompanionMemoryRelationsSheet(
            focus = focus,
            candidates = structuredRecords.filter { it.id != focus.id },
            repository = structuredRepository,
            onDismiss = { relationFocus = null },
        )
    }
}

@Composable
private fun CompanionTimeline(
    memories: List<Memory>,
    structuredMemories: List<CompanionMemoryRecordEntity>,
    structuredRepository: CompanionMemoryRepository,
    activeTarget: CompanionMemoryTarget,
    onMemoryClick: (Memory) -> Unit,
    onStatusChange: (Memory, CompanionEventStatus) -> Unit,
    onEditStructured: (CompanionMemoryRecordEntity) -> Unit,
    onRetractStructured: (CompanionMemoryRecordEntity) -> Unit,
    onConfirmStructured: (CompanionMemoryRecordEntity) -> Unit,
    onOpenRelations: (CompanionMemoryRecordEntity) -> Unit,
    onImportLegacy: (Memory) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dayFormatter = remember { DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()) }
    val groupedMemories =
        remember(memories) {
            memories.groupBy { memory -> dayFormatter.format(Date(timelineTimestamp(memory))) }
        }

    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(structuredMemories, key = { "structured-timeline-${it.id}" }) { record ->
            StructuredMemoryArchiveItem(
                record = record,
                repository = structuredRepository,
                onEdit = { onEditStructured(record) },
                onRetract = { onRetractStructured(record) },
                onConfirm = { onConfirmStructured(record) },
                onOpenRelations = { onOpenRelations(record) },
            )
        }
        groupedMemories.forEach { (dayLabel, dayMemories) ->
            item(key = "timeline-header-$dayLabel") {
                Text(
                    text = dayLabel,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            items(dayMemories, key = { "timeline-${it.uuid}" }) { memory ->
                MemoryArchiveItem(
                    memory = memory,
                    category = CompanionMemoryCategory.TIMELINE,
                    activeTarget = activeTarget,
                    onClick = { onMemoryClick(memory) },
                    onStatusChange = { status -> onStatusChange(memory, status) },
                    onImport = { onImportLegacy(memory) },
                )
            }
        }
    }
}

@Composable
private fun StructuredMemoryArchiveItem(
    record: CompanionMemoryRecordEntity,
    repository: CompanionMemoryRepository,
    onEdit: () -> Unit,
    onRetract: () -> Unit,
    onConfirm: () -> Unit,
    onOpenRelations: () -> Unit,
) {
    var menuExpanded by remember(record.id) { mutableStateOf(false) }
    val evidence by
        produceState(initialValue = emptyList<CompanionMemoryEvidenceEntity>(), record.id) {
            value = repository.getEvidence(record.id)
        }
    val dateFormatter = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()) }
    val latestEvidence = evidence.firstOrNull()
    val isManualEvidence =
        latestEvidence?.messageId == null &&
            latestEvidence?.conversationId.isNullOrBlank() &&
            latestEvidence?.speaker == "user_manual"
    val sourceLabel =
        if (record.reviewAt != null) {
            stringResource(R.string.mate_memory_structured_source_review)
        } else if (isManualEvidence) {
            stringResource(R.string.mate_memory_structured_source_manual)
        } else if (record.sourceKind == "USER_EXPLICIT") {
            stringResource(R.string.mate_memory_structured_source_explicit)
        } else {
            stringResource(R.string.mate_memory_structured_source_review)
        }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                modifier = Modifier.size(38.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = structuredMemoryIcon(record.type),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.decodedLabel() ?: structuredMemoryTypeLabel(record.type),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                record.decodedLabel()?.let {
                    Text(
                        text = structuredMemoryTypeLabel(record.type),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = record.decodedValue(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                latestEvidence?.quote
                    ?.takeIf { it.isNotBlank() && !isManualEvidence }
                    ?.let { quote ->
                    Spacer(modifier = Modifier.height(7.dp))
                    Text(
                        text = stringResource(R.string.mate_memory_structured_evidence, quote),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text =
                        stringResource(
                            R.string.mate_memory_structured_meta,
                            sourceLabel,
                            dateFormatter.format(Date(record.updatedAt)),
                            record.confidence * 100.0,
                        ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (record.supersedesId != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.mate_memory_structured_corrected),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = null,
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    if (record.reviewAt != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.mate_memory_manual_confirm_action)) },
                            leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onConfirm()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.mate_memory_manual_edit_action)) },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.mate_memory_relations_open)) },
                        leadingIcon = { Icon(Icons.Default.AccountTree, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onOpenRelations()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.mate_memory_manual_delete_action)) },
                        leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onRetract()
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompanionMemoryRelationsSheet(
    focus: CompanionMemoryRecordEntity,
    candidates: List<CompanionMemoryRecordEntity>,
    repository: CompanionMemoryRepository,
    onDismiss: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    var refreshKey by remember(focus.id) { mutableIntStateOf(0) }
    var addingRelation by remember(focus.id) { mutableStateOf(false) }
    var typeMenuExpanded by remember(focus.id) { mutableStateOf(false) }
    var selectedType by remember(focus.id) { mutableStateOf(CompanionMemoryEdgeType.RELATES_TO) }
    val relations by
        produceState(initialValue = emptyList<CompanionMemoryRelation>(), focus.id, refreshKey) {
            value = repository.getRelations(focus.id, limit = 20)
        }
    val linkCandidates =
        remember(focus.id, candidates) {
            candidates
                .filter {
                    it.profileId == focus.profileId &&
                        it.status == CompanionRecordStatus.ACTIVE.name
                }
                .sortedByDescending { it.updatedAt }
        }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AccountTree,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.mate_memory_relations_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = focus.decodedLabel() ?: focus.decodedValue(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (relations.isEmpty()) {
                Text(
                    text = stringResource(R.string.mate_memory_relations_empty),
                    modifier = Modifier.padding(vertical = 20.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).padding(top = 12.dp),
                ) {
                    items(relations, key = { it.edge.id }) { relation ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = null,
                                modifier = Modifier.size(19.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = memoryEdgeTypeLabel(relation.edge.type),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text =
                                        relation.relatedRecord.decodedLabel()
                                            ?: relation.relatedRecord.decodedValue(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        repository.retractRelation(relation.edge.id)
                                        refreshKey += 1
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = stringResource(R.string.mate_memory_relations_delete),
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }

            FilledTonalButton(
                onClick = { addingRelation = !addingRelation },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 8.dp),
            ) {
                Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.mate_memory_relations_add))
            }

            if (addingRelation) {
                Text(
                    text = stringResource(R.string.mate_memory_relations_type),
                    modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
                Box {
                    Surface(
                        onClick = { typeMenuExpanded = true },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(memoryEdgeTypeLabel(selectedType.name), modifier = Modifier.weight(1f))
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
                        }
                    }
                    DropdownMenu(
                        expanded = typeMenuExpanded,
                        onDismissRequest = { typeMenuExpanded = false },
                    ) {
                        CompanionMemoryEdgeType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(memoryEdgeTypeLabel(type.name)) },
                                onClick = {
                                    selectedType = type
                                    typeMenuExpanded = false
                                },
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.mate_memory_relations_choose),
                    modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp)) {
                    items(linkCandidates, key = { "relation-candidate-${it.id}" }) { candidate ->
                        Surface(
                            onClick = {
                                coroutineScope.launch {
                                    repository.linkMemories(
                                        fromMemoryId = focus.id,
                                        toMemoryId = candidate.id,
                                        type = selectedType,
                                        confidence = 1.0,
                                        strength = 0.8,
                                    )
                                    addingRelation = false
                                    refreshKey += 1
                                }
                            },
                            color = MaterialTheme.colorScheme.surface,
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                                Text(
                                    text = candidate.decodedLabel() ?: structuredMemoryTypeLabel(candidate.type),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    text = candidate.decodedValue(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun memoryEdgeTypeLabel(type: String): String =
    stringResource(
        when (type) {
            CompanionMemoryEdgeType.ABOUT.name -> R.string.mate_memory_edge_about
            CompanionMemoryEdgeType.SUPPORTS.name -> R.string.mate_memory_edge_supports
            CompanionMemoryEdgeType.CONTRADICTS.name -> R.string.mate_memory_edge_contradicts
            CompanionMemoryEdgeType.SUPERSEDES.name -> R.string.mate_memory_edge_supersedes
            CompanionMemoryEdgeType.FOLLOWED_BY.name -> R.string.mate_memory_edge_followed_by
            CompanionMemoryEdgeType.OCCURRED_DURING.name -> R.string.mate_memory_edge_occurred_during
            CompanionMemoryEdgeType.CAUSED_BY.name -> R.string.mate_memory_edge_caused_by
            CompanionMemoryEdgeType.PROMISES.name -> R.string.mate_memory_edge_promises
            CompanionMemoryEdgeType.PREFERS.name -> R.string.mate_memory_edge_prefers
            CompanionMemoryEdgeType.AVOIDS.name -> R.string.mate_memory_edge_avoids
            else -> R.string.mate_memory_edge_relates_to
        },
    )

@Composable
private fun structuredMemoryTypeLabel(type: String): String =
    stringResource(
        when (type) {
            CompanionMemoryType.IDENTITY.name -> R.string.mate_memory_type_identity
            CompanionMemoryType.PREFERENCE.name -> R.string.mate_memory_type_preference
            CompanionMemoryType.EVENT.name -> R.string.mate_memory_type_event
            CompanionMemoryType.ROUTINE.name -> R.string.mate_memory_type_routine
            CompanionMemoryType.BOUNDARY.name -> R.string.mate_memory_type_boundary
            CompanionMemoryType.COMMITMENT.name -> R.string.mate_memory_type_commitment
            CompanionMemoryType.RELATIONSHIP.name -> R.string.mate_memory_type_relationship
            CompanionMemoryType.SUMMARY.name -> R.string.mate_memory_type_summary
            else -> R.string.mate_memory_type_fact
        },
    )

private fun structuredMemoryIcon(type: String): ImageVector =
    when (type) {
        CompanionMemoryType.IDENTITY.name -> Icons.Default.Person
        CompanionMemoryType.PREFERENCE.name,
        CompanionMemoryType.ROUTINE.name,
        CompanionMemoryType.BOUNDARY.name -> Icons.Default.Tune
        CompanionMemoryType.EVENT.name,
        CompanionMemoryType.COMMITMENT.name,
        CompanionMemoryType.SUMMARY.name -> Icons.Default.Event
        CompanionMemoryType.RELATIONSHIP.name -> Icons.Default.Favorite
        else -> Icons.Default.Memory
    }

@Composable
private fun MemoryArchiveItem(
    memory: Memory,
    category: CompanionMemoryCategory,
    activeTarget: CompanionMemoryTarget,
    onClick: () -> Unit,
    onStatusChange: ((CompanionEventStatus) -> Unit)? = null,
    onImport: (() -> Unit)? = null,
) {
    val dateFormatter = remember { SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()) }
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val sourceRes = memorySourceRes(memory)
    val (containerColor, contentColor) = memoryCategoryColors(category)
    val companionMetadata = memory.companionMetadata()
    val ownership = memory.companionOwnership()

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                modifier = Modifier.size(38.dp),
                shape = RoundedCornerShape(8.dp),
                color = containerColor,
                contentColor = contentColor,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(category.icon, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = memory.title.ifBlank { stringResource(category.titleRes) },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = memory.content.ifBlank { stringResource(R.string.mate_memory_content_empty) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(sourceRes),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text =
                            if (category == CompanionMemoryCategory.TIMELINE) {
                                timeFormatter.format(Date(timelineTimestamp(memory)))
                            } else {
                                dateFormatter.format(memory.updatedAt)
                            },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = memoryOwnershipLabel(ownership, companionMetadata, activeTarget),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (
                    (category == CompanionMemoryCategory.TIMELINE && companionMetadata != null) ||
                        memory.importance >= 0.75f
                ) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (category == CompanionMemoryCategory.TIMELINE && companionMetadata != null) {
                        Text(
                            text = stringResource(companionKindLabel(companionMetadata.kind)),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(companionStatusLabel(companionMetadata.status)),
                            style = MaterialTheme.typography.labelMedium,
                            color =
                                if (companionMetadata.status == CompanionEventStatus.DONE) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                        }
                        if (memory.importance >= 0.75f) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.tertiary,
                            )
                            Text(
                                text = stringResource(R.string.mate_memory_important),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                onImport?.let { importAction ->
                    IconButton(onClick = importAction) {
                        Icon(
                            imageVector = Icons.Default.MoveToInbox,
                            contentDescription =
                                stringResource(R.string.mate_memory_import_legacy_action),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (
                    category == CompanionMemoryCategory.TIMELINE &&
                        companionMetadata != null &&
                        companionMetadata.status != CompanionEventStatus.CANCELLED &&
                        onStatusChange != null
                ) {
                    val isDone = companionMetadata.status == CompanionEventStatus.DONE
                    IconButton(
                        onClick = {
                            onStatusChange(
                                if (isDone) CompanionEventStatus.PENDING else CompanionEventStatus.DONE
                            )
                        },
                    ) {
                        Icon(
                            imageVector = if (isDone) Icons.Default.Replay else Icons.Default.CheckCircle,
                            contentDescription =
                                stringResource(
                                    if (isDone) {
                                        R.string.mate_memory_reopen_event
                                    } else {
                                        R.string.mate_memory_mark_done
                                    }
                                ),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.padding(top = 4.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MemoryViewSelector(
    view: CompanionMemoryView,
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = view.icon,
                contentDescription = null,
                modifier = Modifier.size(21.dp),
                tint =
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            imageVector = if (selected) Icons.Default.CheckCircle else Icons.Default.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint =
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyMemoryCategory(
    onAddMemory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Memory,
                contentDescription = null,
                modifier = Modifier.size(42.dp),
                tint = MaterialTheme.colorScheme.outline,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.mate_memory_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.mate_memory_empty_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            FilledTonalButton(
                onClick = onAddMemory,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.mate_memory_add))
            }
        }
    }
}

@Composable
private fun memoryCategoryColors(category: CompanionMemoryCategory): Pair<Color, Color> =
    when (category) {
        CompanionMemoryCategory.TIMELINE ->
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        CompanionMemoryCategory.RELATIONSHIP ->
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        CompanionMemoryCategory.EVENT ->
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        CompanionMemoryCategory.PREFERENCE ->
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        CompanionMemoryCategory.WORLD ->
            MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

private fun companionKindLabel(kind: CompanionEventKind): Int =
    when (kind) {
        CompanionEventKind.PROMISE -> R.string.mate_memory_kind_promise
        CompanionEventKind.EVENT -> R.string.mate_memory_kind_event
        CompanionEventKind.ANNIVERSARY -> R.string.mate_memory_kind_anniversary
        CompanionEventKind.REMINDER -> R.string.mate_memory_kind_reminder
    }

private fun companionStatusLabel(status: CompanionEventStatus): Int =
    when (status) {
        CompanionEventStatus.PENDING -> R.string.mate_memory_status_pending
        CompanionEventStatus.DONE -> R.string.mate_memory_status_done
        CompanionEventStatus.CANCELLED -> R.string.mate_memory_status_cancelled
    }

private fun timelineTimestamp(memory: Memory): Long =
    memory.companionMetadata()?.eventAtMs ?: memory.updatedAt.time

private fun memorySourceRes(memory: Memory): Int {
    if (memory.isDocumentNode) return R.string.mate_memory_source_document
    val source = memory.source.lowercase(Locale.ROOT)
    return when {
        "chat" in source || "conversation" in source || "summary" in source -> R.string.mate_memory_source_chat
        "user" in source || "manual" in source -> R.string.mate_memory_source_manual
        "import" in source || "file" in source || "web" in source -> R.string.mate_memory_source_import
        else -> R.string.mate_memory_source_organized
    }
}

private fun classifyMemory(memory: Memory): CompanionMemoryCategory {
    if (memory.isDocumentNode) return CompanionMemoryCategory.WORLD

    val tags = runCatching { memory.tags.joinToString(" ") { it.name } }.getOrDefault("")
    val searchable =
        listOf(memory.title, memory.source, memory.folderPath.orEmpty(), tags)
            .joinToString(" ")
            .lowercase(Locale.ROOT)

    return when {
        containsAny(
            searchable,
            "preference", "habit", "taste", "favorite", "偏好", "喜欢", "讨厌", "习惯", "口味",
        ) -> CompanionMemoryCategory.PREFERENCE
        containsAny(
            searchable,
            "world", "lore", "setting", "location", "place", "concept", "story", "世界", "设定", "地点", "剧情", "背景", "知识",
        ) -> CompanionMemoryCategory.WORLD
        containsAny(
            searchable,
            "event", "timeline", "history", "schedule", "reminder", "trip", "事件", "经历", "时间线", "回忆", "旅行", "争执", "约定",
        ) -> CompanionMemoryCategory.EVENT
        containsAny(
            searchable,
            "relation", "person", "family", "friend", "partner", "companion", "关系", "人物", "家人", "朋友", "伴侣", "称呼", "纪念日",
        ) -> CompanionMemoryCategory.RELATIONSHIP
        "chat" in searchable || "conversation" in searchable || "summary" in searchable ->
            CompanionMemoryCategory.EVENT
        else -> CompanionMemoryCategory.RELATIONSHIP
    }
}

private fun containsAny(value: String, vararg candidates: String): Boolean =
    candidates.any(value::contains)
