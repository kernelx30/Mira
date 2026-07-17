package com.ai.assistance.operit.ui.features.memory.screens.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.CompanionMemoryOwnership
import com.ai.assistance.operit.data.model.CompanionMemoryScope
import com.ai.assistance.operit.data.model.CompanionMemoryTarget
import com.ai.assistance.operit.data.model.Memory
import com.ai.assistance.operit.data.model.companionOwnership

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditMemoryDialog(
    memory: Memory?,
    allFolderPaths: List<String>,
    showAdvancedFields: Boolean = true,
    defaultCategoryTag: String = "Relationship",
    defaultScope: CompanionMemoryScope = CompanionMemoryScope.RELATIONSHIP,
    profileId: String = "default",
    activeTarget: CompanionMemoryTarget = CompanionMemoryTarget(),
    onDismiss: () -> Unit,
    onSave: (
        memory: Memory?,
        title: String,
        content: String,
        contentType: String,
        source: String,
        credibility: Float,
        importance: Float,
        folderPath: String,
        tags: List<String>,
        ownership: CompanionMemoryOwnership?,
    ) -> Unit
) {
    val defaultFolder = stringResource(R.string.memory_uncategorized)
    val scrollState = rememberScrollState()
    var title by remember { mutableStateOf(memory?.title ?: "") }
    var content by remember { mutableStateOf(memory?.content ?: "") }
    var contentType by remember { mutableStateOf(memory?.contentType ?: "text/plain") }
    var source by remember { mutableStateOf(memory?.source ?: "user_input") }
    var credibility by remember { mutableStateOf(memory?.credibility ?: 0.8f) }
    var importance by remember { mutableStateOf(memory?.importance ?: 0.5f) }
    var folderPath by remember { mutableStateOf(memory?.folderPath ?: defaultFolder) }
    var selectedCategoryTag by remember { mutableStateOf(defaultCategoryTag) }
    val existingOwnership = remember(memory?.uuid) { memory?.companionOwnership() }
    var selectedScope by remember { mutableStateOf(existingOwnership?.scope ?: defaultScope) }
    var recallEnabled by remember { mutableStateOf(existingOwnership?.recallEnabled ?: true) }
    val tags = remember { mutableStateListOf<String>() }
    
    LaunchedEffect(memory, defaultCategoryTag, defaultScope) {
        tags.clear()
        memory?.tags?.let {
            tags.addAll(it.map { tag -> tag.name })
        }
        selectedCategoryTag = memory?.let { inferMemoryCategoryTag(it, tags) } ?: defaultCategoryTag
        selectedScope = existingOwnership?.scope ?: defaultScope
        recallEnabled = existingOwnership?.recallEnabled ?: true
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .fillMaxHeight(if (showAdvancedFields) 0.9f else 0.78f)
                    .imePadding(),
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = if (memory == null) {
                            stringResource(R.string.memory_create_new)
                        } else {
                            stringResource(R.string.memory_edit_memory)
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 16.dp)
                    )
                    HorizontalDivider()

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(scrollState)
                            .padding(24.dp)
                    ) {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text(stringResource(R.string.memory_title)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = content,
                            onValueChange = { content = it },
                            label = { Text(stringResource(R.string.memory_content)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 100.dp, max = 200.dp),
                            enabled = memory?.isDocumentNode != true
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        if (showAdvancedFields) {
                            FolderSelector(
                                allFolderPaths = allFolderPaths,
                                selectedPath = folderPath,
                                onPathSelected = { folderPath = it }
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            TagsEditor(tags = tags, onTagsChanged = { tags.clear(); tags.addAll(it) })
                            Spacer(modifier = Modifier.height(16.dp))

                            OutlinedTextField(
                                value = source,
                                onValueChange = { source = it },
                                label = { Text(stringResource(R.string.memory_source)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            Text("${stringResource(R.string.memory_credibility)}: ${String.format("%.2f", credibility)}")
                            Slider(
                                value = credibility,
                                onValueChange = { credibility = it },
                                valueRange = 0f..1f
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Text("${stringResource(R.string.memory_importance)}: ${String.format("%.2f", importance)}")
                            Slider(
                                value = importance,
                                onValueChange = { importance = it },
                                valueRange = 0f..1f
                            )
                        } else {
                            MemoryScopeSelector(
                                selectedScope = selectedScope,
                                activeTarget = activeTarget,
                                onSelected = { scope ->
                                    selectedScope = scope
                                    selectedCategoryTag = defaultCategoryForScope(scope)
                                },
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.mate_memory_recall_enabled),
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    Text(
                                        text = stringResource(R.string.mate_memory_recall_enabled_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = recallEnabled,
                                    onCheckedChange = { recallEnabled = it },
                                )
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                            MemoryCategorySelector(
                                selectedTag = selectedCategoryTag,
                                onSelected = { selectedCategoryTag = it },
                            )
                        }
                    }

                    HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.memory_cancel))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val tagsToSave =
                                    if (showAdvancedFields) {
                                        tags.toList()
                                    } else {
                                        tags.filterNot { it in companionMemoryCategoryTags } + selectedCategoryTag
                                    }
                                val ownershipToSave =
                                    if (showAdvancedFields) {
                                        existingOwnership
                                    } else {
                                        val target =
                                            existingOwnership
                                                ?.takeIf {
                                                    it.scope == selectedScope &&
                                                        selectedScope != CompanionMemoryScope.USER
                                                }
                                                ?.toTarget()
                                                ?: activeTarget
                                        CompanionMemoryOwnership.manual(
                                            scope = selectedScope,
                                            profileId = profileId,
                                            target = target,
                                            recallEnabled = recallEnabled,
                                        )
                                    }
                                onSave(
                                    memory,
                                    title,
                                    content,
                                    contentType,
                                    source,
                                    credibility,
                                    importance,
                                    folderPath,
                                    tagsToSave,
                                    ownershipToSave,
                                )
                                onDismiss()
                            },
                            enabled = title.isNotBlank() && content.isNotBlank(),
                        ) {
                            Text(stringResource(R.string.memory_save))
                        }
                    }
                }
            }
        }
    }
}

private val companionMemoryCategoryTags =
    setOf("Relationship", "Event", "Preference", "World")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemoryScopeSelector(
    selectedScope: CompanionMemoryScope,
    activeTarget: CompanionMemoryTarget,
    onSelected: (CompanionMemoryScope) -> Unit,
) {
    val targetName =
        activeTarget.displayName.ifBlank {
            stringResource(R.string.mate_memory_current_character)
        }
    val scopes =
        listOf(
            Triple(CompanionMemoryScope.USER, R.string.mate_memory_scope_user, Icons.Default.Person),
            Triple(
                CompanionMemoryScope.RELATIONSHIP,
                R.string.mate_memory_scope_relationship,
                Icons.Default.Favorite,
            ),
            Triple(
                CompanionMemoryScope.CHARACTER_WORLD,
                R.string.mate_memory_scope_character_world,
                Icons.Default.Public,
            ),
        )

    Column {
        Text(
            text = stringResource(R.string.mate_memory_scope_label),
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            scopes.forEach { (scope, titleRes, icon) ->
                FilterChip(
                    selected = selectedScope == scope,
                    onClick = { onSelected(scope) },
                    label = {
                        Text(
                            if (scope == CompanionMemoryScope.RELATIONSHIP) {
                                stringResource(titleRes, targetName)
                            } else {
                                stringResource(titleRes)
                            }
                        )
                    },
                    leadingIcon = {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text =
                when (selectedScope) {
                    CompanionMemoryScope.USER ->
                        stringResource(R.string.mate_memory_scope_user_desc)
                    CompanionMemoryScope.RELATIONSHIP ->
                        stringResource(R.string.mate_memory_scope_relationship_desc, targetName)
                    CompanionMemoryScope.CHARACTER_WORLD ->
                        stringResource(R.string.mate_memory_scope_character_world_desc, targetName)
                },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemoryCategorySelector(
    selectedTag: String,
    onSelected: (String) -> Unit,
) {
    val categories =
        listOf(
            Triple("Relationship", R.string.mate_memory_category_relationship, Icons.Default.Favorite),
            Triple("Event", R.string.mate_memory_category_event, Icons.Default.Event),
            Triple("Preference", R.string.mate_memory_category_preference, Icons.Default.Tune),
            Triple("World", R.string.mate_memory_category_world, Icons.Default.Public),
        )

    Column {
        Text(
            text = stringResource(R.string.mate_memory_type_label),
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            categories.forEach { (tag, titleRes, icon) ->
                FilterChip(
                    selected = selectedTag == tag,
                    onClick = { onSelected(tag) },
                    label = { Text(stringResource(titleRes)) },
                    leadingIcon = {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                )
            }
        }
    }
}

private fun defaultCategoryForScope(scope: CompanionMemoryScope): String =
    when (scope) {
        CompanionMemoryScope.USER -> "Preference"
        CompanionMemoryScope.RELATIONSHIP -> "Relationship"
        CompanionMemoryScope.CHARACTER_WORLD -> "World"
    }

private fun CompanionMemoryOwnership.toTarget(): CompanionMemoryTarget =
    CompanionMemoryTarget(
        characterId = characterId,
        characterName = characterName,
        characterGroupId = characterGroupId,
        characterGroupName = characterGroupName,
    )

private fun inferMemoryCategoryTag(memory: Memory, tags: List<String>): String {
    tags.firstOrNull { it in companionMemoryCategoryTags }?.let { return it }
    if (memory.isDocumentNode) return "World"

    val searchable =
        listOf(memory.title, memory.source, memory.folderPath.orEmpty(), tags.joinToString(" "))
            .joinToString(" ")
            .lowercase()
    return when {
        listOf("preference", "habit", "偏好", "喜欢", "习惯").any(searchable::contains) -> "Preference"
        listOf("world", "lore", "setting", "世界", "设定", "剧情").any(searchable::contains) -> "World"
        listOf("event", "timeline", "history", "事件", "经历", "回忆", "约定").any(searchable::contains) -> "Event"
        else -> "Relationship"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderSelector(
    allFolderPaths: List<String>,
    selectedPath: String,
    onPathSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedPath,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.memory_folder_label2)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            allFolderPaths.forEach { path ->
                DropdownMenuItem(
                    text = { Text(path) },
                    onClick = {
                        onPathSelected(path)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsEditor(
    tags: List<String>,
    onTagsChanged: (List<String>) -> Unit
) {
    var newTagText by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    Column {
        Text(stringResource(R.string.memory_tags), style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tags.forEach { tag ->
                InputChip(
                    selected = false,
                    onClick = { /* Not used */ },
                    label = { Text(tag) },
                    trailingIcon = {
                        IconButton(
                            onClick = { onTagsChanged(tags - tag) },
                            modifier = Modifier.size(18.dp)
                        ) {
                            Icon(Icons.Default.Cancel, contentDescription = "Remove tag")
                        }
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = newTagText,
            onValueChange = { newTagText = it },
            placeholder = { Text(stringResource(R.string.memory_add_tag_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                if (newTagText.isNotBlank() && newTagText !in tags) {
                    onTagsChanged(tags + newTagText)
                    newTagText = ""
                }
                keyboardController?.hide()
            }),
            trailingIcon = {
                IconButton(onClick = {
                    if (newTagText.isNotBlank() && newTagText !in tags) {
                        onTagsChanged(tags + newTagText)
                        newTagText = ""
                    }
                }) {
                    Icon(Icons.Default.Add, contentDescription = "Add tag")
                }
            }
        )
    }
}
