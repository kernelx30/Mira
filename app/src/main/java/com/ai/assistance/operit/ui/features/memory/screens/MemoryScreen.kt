package com.ai.assistance.operit.ui.features.memory.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ai.assistance.operit.ui.components.CustomScaffold
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ActivePrompt
import com.ai.assistance.operit.data.model.CharacterCardMemoryProfileBindingMode
import com.ai.assistance.operit.data.model.CompanionMemoryScope
import com.ai.assistance.operit.data.model.CompanionMemoryRecordEntity
import com.ai.assistance.operit.data.model.CompanionMemoryTarget
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.data.model.structuredCompanionId
import com.ai.assistance.operit.data.preferences.ActivePromptManager
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.CharacterGroupCardManager
import com.ai.assistance.operit.data.preferences.preferencesManager
import com.ai.assistance.operit.ui.features.memory.screens.dialogs.BatchDeleteConfirmDialog
import com.ai.assistance.operit.ui.features.memory.screens.dialogs.DocumentViewDialog
import com.ai.assistance.operit.ui.features.memory.screens.dialogs.EditMemoryDialog
import com.ai.assistance.operit.ui.features.memory.screens.dialogs.LinkMemoryDialog
import com.ai.assistance.operit.ui.features.memory.screens.dialogs.MemoryInfoDialog
import com.ai.assistance.operit.ui.features.memory.screens.dialogs.EdgeInfoDialog
import com.ai.assistance.operit.ui.features.memory.screens.dialogs.EditEdgeDialog
import com.ai.assistance.operit.ui.features.memory.viewmodel.MemoryViewModel
import com.ai.assistance.operit.ui.features.memory.viewmodel.MemoryViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import android.provider.OpenableColumns
import android.widget.Toast
import com.ai.assistance.operit.util.AppLogger
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.memory.screens.dialogs.MemorySearchSettingsDialog
import com.ai.assistance.operit.ui.features.memory.screens.dialogs.MemorySearchSimulationDialog
import com.ai.assistance.operit.ui.features.memory.screens.graph.model.Edge
import com.ai.assistance.operit.ui.main.components.LocalIsCurrentScreen
import com.ai.assistance.operit.data.repository.decodedLabel
import com.ai.assistance.operit.data.repository.decodedValue

@Composable
fun MemorySearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSettingsClick: () -> Unit,
    onMenuClick: () -> Unit,
    isGraphView: Boolean,
    onImportClick: () -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (isGraphView) {
            IconButton(onClick = onMenuClick) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = "Toggle Folders",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.memory_search_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                keyboardController?.hide()
                onSearch()
            })
        )
        if (!isGraphView) {
            IconButton(onClick = onImportClick) {
                Icon(
                    Icons.Default.UploadFile,
                    contentDescription = stringResource(R.string.mate_memory_import),
                    tint = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
        if (isGraphView) {
            IconButton(onClick = onSettingsClick) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = stringResource(R.string.memory_search_settings_title),
                    tint = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

@Composable
private fun MemoryViewModeTabs(
    showGraphView: Boolean,
    onGraphViewChange: (Boolean) -> Unit,
) {
    TabRow(selectedTabIndex = if (showGraphView) 1 else 0) {
        Tab(
            selected = !showGraphView,
            onClick = { onGraphViewChange(false) },
            text = { Text(stringResource(R.string.mate_memory_archive_title)) },
        )
        Tab(
            selected = showGraphView,
            onClick = { onGraphViewChange(true) },
            text = { Text(stringResource(R.string.mate_memory_graph_title)) },
        )
    }
}

@Composable
private fun CompanionGraphMemoryDialog(
    record: CompanionMemoryRecordEntity,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                record.decodedLabel()
                    ?: stringResource(R.string.mate_memory_graph_node_title),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(record.decodedValue(), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text =
                        stringResource(
                            R.string.mate_memory_graph_node_meta,
                            record.scope,
                            record.type,
                            (record.confidence * 100).toInt(),
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (record.reviewAt != null) {
                    Text(
                        stringResource(R.string.mate_memory_status_review),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
    )
}

@Composable
private fun CompanionGraphEdgeDialog(
    edge: Edge,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mate_memory_graph_edge_title)) },
        text = {
            Text(
                edge.label ?: stringResource(R.string.mate_memory_edge_relates_to),
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MemoryScreen() {
    val context = LocalContext.current
    val profileList by preferencesManager.profileListFlow.collectAsState(initial = emptyList())
    val activeProfileId by
    preferencesManager.activeProfileIdFlow.collectAsState(initial = "default")
    val activePromptManager = remember(context) { ActivePromptManager.getInstance(context) }
    val characterCardManager = remember(context) { CharacterCardManager.getInstance(context) }
    val characterGroupManager = remember(context) { CharacterGroupCardManager.getInstance(context) }
    val activePrompt by
        activePromptManager.activePromptFlow.collectAsState(
            initial = ActivePrompt.CharacterCard(CharacterCardManager.DEFAULT_CHARACTER_CARD_ID),
        )

    // 获取所有配置文件的名称映射(id -> name)
    val profileNameMap = remember { mutableStateMapOf<String, String>() }

    // 加载所有配置文件名称
    LaunchedEffect(profileList) {
        profileList.forEach { profileId ->
            val profile = preferencesManager.getUserPreferencesFlow(profileId).first()
            profileNameMap[profileId] = profile.name
        }
    }

    var selectedProfileId by remember { mutableStateOf(activeProfileId) }
    var showFolderNavigator by remember { mutableStateOf(false) }
    var showGraphView by rememberSaveable { mutableStateOf(false) }
    var defaultMemoryCategoryTag by rememberSaveable { mutableStateOf("Relationship") }
    var defaultMemoryScope by remember { mutableStateOf(CompanionMemoryScope.RELATIONSHIP) }
    var activeMemoryTarget by remember {
        mutableStateOf(
            CompanionMemoryTarget(characterId = CharacterCardManager.DEFAULT_CHARACTER_CARD_ID)
        )
    }

    LaunchedEffect(activePrompt, activeProfileId) {
        when (val prompt = activePrompt) {
                is ActivePrompt.CharacterCard -> {
                    val card = characterCardManager.getCharacterCard(prompt.id)
                    activeMemoryTarget = CompanionMemoryTarget(
                        characterId = card.id,
                        characterName = card.name,
                    )
                    val bindingMode =
                        CharacterCardMemoryProfileBindingMode.normalize(card.memoryProfileBindingMode)
                    val boundProfileId = card.memoryProfileId?.takeIf { it.isNotBlank() }
                    selectedProfileId =
                        if (
                            bindingMode == CharacterCardMemoryProfileBindingMode.FIXED_PROFILE &&
                                boundProfileId != null
                        ) {
                            boundProfileId
                        } else {
                            activeProfileId
                        }
                }
                is ActivePrompt.CharacterGroup -> {
                    val group = characterGroupManager.getCharacterGroupCard(prompt.id)
                    activeMemoryTarget = CompanionMemoryTarget(
                        characterGroupId = prompt.id,
                        characterGroupName = group?.name.orEmpty(),
                    )
                    selectedProfileId = activeProfileId
                }
        }
    }

    val activeCompanionId = remember(activeMemoryTarget) { activeMemoryTarget.structuredCompanionId() }
    // Structured graph visibility depends on the active role, so role switches need a new owner context.
    val viewModel: MemoryViewModel =
        viewModel(
            key = "$selectedProfileId|$activeCompanionId",
            factory = MemoryViewModelFactory(context, selectedProfileId, activeCompanionId)
        )
    val uiState by viewModel.uiState.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val isCurrentScreen = LocalIsCurrentScreen.current

    LaunchedEffect(isCurrentScreen, selectedProfileId, activeCompanionId) {
        if (isCurrentScreen) {
            viewModel.loadMemoryGraph()
            viewModel.loadFolderPaths()
        }
    }

    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.clearMessage()
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let { fileUri ->
                scope.launch {
                    var tempFile: File? = null
                    try {
                        // More robust file name extraction
                        val (fileName, mimeType) = withContext(Dispatchers.IO) {
                            // Execute ContentResolver operations on IO thread
                            var extractedFileName = "Untitled"
                            context.contentResolver.query(fileUri, null, null, null, null)
                                ?.use { cursor ->
                                    if (cursor.moveToFirst()) {
                                        val displayNameIndex =
                                            cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                        if (displayNameIndex != -1) {
                                            extractedFileName = cursor.getString(displayNameIndex)
                                        }
                                    }
                                }

                            val extractedMimeType = context.contentResolver.getType(fileUri)
                            Pair(extractedFileName, extractedMimeType)
                        }

                        if (mimeType != null && mimeType.startsWith("text")) {
                            val content = withContext(Dispatchers.IO) {
                                val inputStream = context.contentResolver.openInputStream(fileUri)
                                val reader = BufferedReader(InputStreamReader(inputStream))
                                reader.readText()
                            }
                            viewModel.importDocument(fileName, fileUri.toString(), content)
                        } else {
                            // For binary files, use the tool
                            tempFile = File(context.cacheDir, fileName)
                            withContext(Dispatchers.IO) {
                                val inputStream = context.contentResolver.openInputStream(fileUri)
                                val outputStream = FileOutputStream(tempFile)
                                inputStream?.use { input ->
                                    outputStream.use { output ->
                                        input.copyTo(output)
                                    }
                                }
                            }

                            val result = withContext(Dispatchers.IO) {
                                val toolHandler = AIToolHandler.getInstance(context)
                                val tool = AITool(
                                    name = "read_file_full",
                                    parameters = listOf(ToolParameter("path", tempFile.absolutePath))
                                )
                                toolHandler.executeTool(tool)
                            }

                            if (result.success) {
                                // Assuming result.result can be cast to StringResultData
                                val resultData = result.result
                                val content = if (resultData is StringResultData) {
                                    resultData.value
                                } else {
                                    resultData.toString()
                                }
                                viewModel.importDocument(fileName, fileUri.toString(), content)
                            } else {
                                AppLogger.e("MemoryScreen", "Tool execution failed: ${result.error}")
                            }
                        }
                    } catch (e: Exception) {
                        AppLogger.e("MemoryScreen", "Error processing file: $fileUri", e)
                    } finally {
                        tempFile?.delete()
                    }
                }
            }
        }
    )

    CustomScaffold(
        floatingActionButton = {
            if (showGraphView) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                // 只有在框选模式下才显示"确认删除"按钮
                if (uiState.isBoxSelectionMode) {
                    FloatingActionButton(
                        onClick = { viewModel.showBatchDeleteConfirm() },
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Selected")
                    }
                }

                // 框选模式切换按钮
                FloatingActionButton(
                    onClick = {
                        com.ai.assistance.operit.util.AppLogger.d(
                            "MemoryScreen",
                            "Box selection button clicked. Current mode: ${uiState.isBoxSelectionMode}, toggling to ${!uiState.isBoxSelectionMode}"
                        )
                        viewModel.toggleBoxSelectionMode(!uiState.isBoxSelectionMode)
                    },
                    containerColor = if (uiState.isBoxSelectionMode) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.SelectAll, contentDescription = "Toggle Box Selection Mode")
                }

                FloatingActionButton(
                    onClick = {
                        com.ai.assistance.operit.util.AppLogger.d(
                            "MemoryScreen",
                            "Linking button clicked. Current mode: ${uiState.isLinkingMode}, toggling to ${!uiState.isLinkingMode}"
                        )
                        viewModel.toggleLinkingMode(!uiState.isLinkingMode)
                    },
                    containerColor = if (uiState.isLinkingMode) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Link, contentDescription = "Toggle Linking Mode")
                }
                FloatingActionButton(
                    onClick = {
                        filePickerLauncher.launch(
                            arrayOf(
                                "text/*",
                                "application/pdf",
                                "application/msword",
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                            )
                        )
                    },
                    modifier = Modifier.size(48.dp),
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = "Import Document")
                }
                FloatingActionButton(
                    onClick = { viewModel.startEditing(null) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Create Memory")
                }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {

            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                MemorySearchBar(
                    query = uiState.searchQuery,
                    onQueryChange = { viewModel.onSearchQueryChange(it) },
                    onSearch = {
                        keyboardController?.hide()
                        viewModel.searchMemories()
                    },
                    onSettingsClick = { viewModel.showSearchSettingsDialog(true) },
                    onMenuClick = { showFolderNavigator = !showFolderNavigator },
                    isGraphView = showGraphView,
                    onImportClick = {
                        filePickerLauncher.launch(
                            arrayOf(
                                "text/*",
                                "application/pdf",
                                "application/msword",
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            ),
                        )
                    },
                )
                MemoryViewModeTabs(
                    showGraphView = showGraphView,
                    onGraphViewChange = { nextGraphView ->
                        showGraphView = nextGraphView
                        showFolderNavigator = false
                        viewModel.toggleLinkingMode(false)
                        viewModel.toggleBoxSelectionMode(false)
                        viewModel.clearSelection()
                        if (nextGraphView) viewModel.loadMemoryGraph()
                    },
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                ) {
                    if (showGraphView) {
                        if (uiState.graph.nodes.isEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Icon(
                                    Icons.Default.AccountTree,
                                    contentDescription = null,
                                    modifier = Modifier.size(42.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = stringResource(R.string.mate_memory_graph_empty_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(top = 12.dp),
                                )
                                Text(
                                    text = stringResource(R.string.mate_memory_graph_empty_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 6.dp),
                                )
                            }
                        } else {
                            GraphVisualizer(
                                graph = uiState.graph,
                                modifier = Modifier.fillMaxSize(),
                                selectedNodeId = uiState.selectedNodeId,
                                boxSelectedNodeIds = uiState.boxSelectedNodeIds,
                                isBoxSelectionMode = uiState.isBoxSelectionMode,
                                linkingNodeIds = uiState.linkingNodeIds,
                                selectedEdgeId = uiState.selectedEdge?.id ?: uiState.selectedCompanionEdge?.id,
                                onNodeClick = { node -> viewModel.selectNode(node) },
                                onEdgeClick = { edge -> viewModel.selectEdge(edge) },
                                onNodesSelected = { nodeIds -> viewModel.addNodesToSelection(nodeIds) },
                            )
                        }
                    } else {
                        CompanionMemoryArchive(
                            memories = uiState.memories,
                            activeProfileId = selectedProfileId,
                            activeTarget = activeMemoryTarget,
                            searchQuery = uiState.searchQuery,
                            onMemoryClick = viewModel::selectMemory,
                            onAddMemory = { scope, categoryTag ->
                                defaultMemoryScope = scope
                                defaultMemoryCategoryTag = categoryTag
                                viewModel.startEditing(null)
                            },
                            onCompanionStatusChange = viewModel::updateCompanionStatus,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    if (uiState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                }
            }
            // 左侧文件夹导航 (Overlay)
            AnimatedVisibility(
                visible = showGraphView && showFolderNavigator,
                enter = slideInHorizontally(initialOffsetX = { -it }),
                exit = slideOutHorizontally(targetOffsetX = { -it })
            ) {
                FolderNavigator(
                    folderPaths = uiState.folderPaths,
                    selectedFolderPath = uiState.selectedFolderPath,
                    onFolderSelected = { folderPath -> viewModel.selectFolder(folderPath) },
                    onFolderRename = { oldPath, newPath ->
                        viewModel.renameFolder(
                            oldPath,
                            newPath
                        )
                    },
                    onFolderDelete = { folderPath -> viewModel.deleteFolder(folderPath) },
                    onFolderCreate = { folderPath -> viewModel.createFolder(folderPath) },
                    onRefresh = { viewModel.refreshFolderList() },
                    profileList = profileList,
                    profileNameMap = profileNameMap,
                    selectedProfileId = selectedProfileId,
                    onProfileSelected = { selectedProfileId = it },
                    onDismissRequest = { showFolderNavigator = false }
                )
            }

            // 对话框层
            if (uiState.isSearchSettingsDialogVisible) {
                MemorySearchSettingsDialog(
                    currentConfig = uiState.searchConfig,
                    autoSaveIntervalMinutes = uiState.autoSaveIntervalMinutes,
                    cloudConfig = uiState.cloudEmbeddingConfig,
                    dimensionUsage = uiState.embeddingDimensionUsage,
                    rebuildProgress = uiState.embeddingRebuildProgress,
                    error = uiState.error,
                    isRebuilding = uiState.isEmbeddingRebuildRunning,
                    onDismiss = { viewModel.showSearchSettingsDialog(false) },
                    onSave = { config, cloudConfig, autoSaveIntervalMinutes ->
                        viewModel.saveSearchSettings(config, cloudConfig, autoSaveIntervalMinutes)
                        viewModel.searchMemories()
                    },
                    onRebuild = { viewModel.rebuildVectorIndex() },
                    onSimulateSearch = { viewModel.openSearchSimulationDialog() }
                )
            }

            if (uiState.isSearchSimulationDialogVisible) {
                MemorySearchSimulationDialog(
                    query = uiState.searchSimulationQuery,
                    isRunning = uiState.isSearchSimulationRunning,
                    result = uiState.searchSimulationResult,
                    error = uiState.searchSimulationError,
                    onQueryChange = { viewModel.onSearchSimulationQueryChange(it) },
                    onRun = { viewModel.runSearchSimulation() },
                    onDismiss = { viewModel.showSearchSimulationDialog(false) }
                )
            }

            if (uiState.isDocumentViewOpen && uiState.selectedMemory != null) {
                var memoryTitle by remember { mutableStateOf(uiState.selectedMemory!!.title) }
                val chunkStates = remember {
                    mutableStateMapOf<Long, String>().apply {
                        uiState.selectedDocumentChunks.forEach { put(it.id, it.content) }
                    }
                }
                // 当chunks列表变化时，同步状态
                LaunchedEffect(uiState.selectedDocumentChunks) {
                    chunkStates.clear()
                    uiState.selectedDocumentChunks.forEach { chunk ->
                        chunkStates[chunk.id] = chunk.content
                    }
                }

                DocumentViewDialog(
                    memoryTitle = memoryTitle,
                    onTitleChange = { memoryTitle = it },
                    chunks = uiState.selectedDocumentChunks,
                    chunkStates = chunkStates,
                    onChunkChange = { id, content -> chunkStates[id] = content },
                    searchQuery = uiState.documentSearchQuery,
                    onSearchQueryChange = { viewModel.onDocumentSearchQueryChange(it) },
                    onPerformSearch = { viewModel.performSearchInDocument() },
                    onDismiss = { viewModel.closeDocumentView() },
                    onSave = {
                        // 保存标题
                        if (memoryTitle != uiState.selectedMemory!!.title) {
                            viewModel.updateMemory(
                                memory = uiState.selectedMemory!!,
                                newTitle = memoryTitle,
                                newContent = uiState.selectedMemory!!.content,
                                newContentType = uiState.selectedMemory!!.contentType,
                                newSource = uiState.selectedMemory!!.source,
                                newCredibility = uiState.selectedMemory!!.credibility,
                                newImportance = uiState.selectedMemory!!.importance,
                                newFolderPath = uiState.selectedMemory!!.folderPath ?: "",
                                newTags = uiState.selectedMemory!!.tags.map { it.name }
                            )
                        }
                        // 保存有变动的chunks
                        chunkStates.forEach { (id, content) ->
                            val originalContent =
                                uiState.selectedDocumentChunks.find { it.id == id }?.content
                            if (content != originalContent) {
                                viewModel.updateChunkContent(id, content)
                            }
                        }
                        viewModel.closeDocumentView()
                    },
                    onDelete = { viewModel.deleteMemory(uiState.selectedMemory!!.id) },
                    folderPath = uiState.selectedMemory?.folderPath ?: ""
                )
            } else if (uiState.selectedMemory != null) {
                MemoryInfoDialog(
                    memory = uiState.selectedMemory!!,
                    showAdvancedFields = showGraphView,
                    onDismiss = { viewModel.clearSelection() },
                    onEdit = {
                        viewModel.startEditing(uiState.selectedMemory)
                        viewModel.clearSelection() // 关闭当前对话框
                    },
                    onDelete = { viewModel.deleteMemory(uiState.selectedMemory!!.id) }
                )
            }

            uiState.selectedCompanionMemory?.let { record ->
                CompanionGraphMemoryDialog(
                    record = record,
                    onDismiss = { viewModel.clearSelection() },
                )
            }

            val selectedEdge = uiState.selectedEdge
            if (selectedEdge != null) {
                EdgeInfoDialog(
                    edge = selectedEdge,
                    graph = uiState.graph,
                    onDismiss = { viewModel.clearSelection() },
                    onEdit = {
                        viewModel.startEditingEdge(selectedEdge)
                        viewModel.clearSelection() // 同样, 点击编辑后关闭
                    },
                    onDelete = { viewModel.deleteEdge(selectedEdge.id) }
                )
            }

            uiState.selectedCompanionEdge?.let { edge ->
                CompanionGraphEdgeDialog(
                    edge = edge,
                    onDismiss = { viewModel.clearSelection() },
                )
            }

            if (uiState.linkingNodeIds.size == 2) {
                val sourceNode = uiState.graph.nodes.find { it.id == uiState.linkingNodeIds[0] }
                val targetNode = uiState.graph.nodes.find { it.id == uiState.linkingNodeIds[1] }
                if (sourceNode != null && targetNode != null) {
                    LinkMemoryDialog(
                        sourceNodeLabel = sourceNode.label,
                        targetNodeLabel = targetNode.label,
                        onDismiss = { viewModel.toggleLinkingMode(false) },
                        onLink = { type, weight, description ->
                            viewModel.linkMemories(
                                sourceNode.id,
                                targetNode.id,
                                type,
                                weight,
                                description
                            )
                        }
                    )
                }
            }

            if (uiState.isEditing) {
                EditMemoryDialog(
                    memory = uiState.editingMemory,
                    allFolderPaths = uiState.folderPaths,
                    showAdvancedFields = showGraphView,
                    defaultCategoryTag = defaultMemoryCategoryTag,
                    defaultScope = defaultMemoryScope,
                    profileId = selectedProfileId,
                    activeTarget = activeMemoryTarget,
                    onDismiss = { viewModel.cancelEditing() },
                    onSave = { memory, title, content, contentType, source, credibility, importance, folderPath, tags, ownership ->
                        if (memory == null) {
                            viewModel.createMemory(
                                title = title,
                                content = content,
                                contentType = contentType,
                                source = source,
                                credibility = credibility,
                                importance = importance,
                                folderPath = folderPath,
                                tags = tags,
                                ownership = ownership,
                            )
                        } else {
                            viewModel.updateMemory(
                                memory = memory,
                                newTitle = title,
                                newContent = content,
                                newContentType = contentType,
                                newSource = source,
                                newCredibility = credibility,
                                newImportance = importance,
                                newFolderPath = folderPath,
                                newTags = tags,
                                ownership = ownership,
                            )
                        }
                    }
                )
            }

            val editingEdge = uiState.editingEdge
            if (uiState.isEditingEdge && editingEdge != null) {
                EditEdgeDialog(
                    edge = editingEdge,
                    onDismiss = { viewModel.cancelEditingEdge() },
                    onSave = { type, weight, description ->
                        viewModel.updateEdge(editingEdge, type, weight, description)
                    }
                )
            }

            if (uiState.showBatchDeleteConfirm) {
                BatchDeleteConfirmDialog(
                    selectedCount = uiState.boxSelectedNodeIds.size,
                    onDismiss = { viewModel.dismissBatchDeleteConfirm() },
                    onConfirm = { viewModel.deleteSelectedNodes() }
                )
            }
        }
    }}
