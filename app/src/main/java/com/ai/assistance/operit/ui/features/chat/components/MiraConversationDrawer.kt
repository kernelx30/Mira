package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MiraConversationDrawer(
    chatHistories: List<ChatHistory>,
    currentId: String?,
    activeStreamingChatIds: Set<String>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onNewChat: () -> Unit,
    onSelectChat: (String) -> Unit,
    onPinChat: (String, Boolean) -> Unit,
    onRenameChat: (String, String) -> Unit,
    onArchiveChat: (String, Boolean) -> Unit,
    onExportChat: (String) -> Unit,
    onDeleteChat: (String) -> Unit,
    onOpenMemory: () -> Unit,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val historyManager = remember { ChatHistoryManager.getInstance(context) }
    var matchedChatIdsByContent by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isSearching by remember { mutableStateOf(false) }
    var actionTarget by remember { mutableStateOf<ChatHistory?>(null) }
    var renameTarget by remember { mutableStateOf<ChatHistory?>(null) }
    var deleteTarget by remember { mutableStateOf<ChatHistory?>(null) }
    var renameText by remember { mutableStateOf("") }

    LaunchedEffect(searchQuery) {
        val query = searchQuery.trim()
        if (query.isEmpty()) {
            matchedChatIdsByContent = emptySet()
            isSearching = false
        } else {
            isSearching = true
            delay(180)
            matchedChatIdsByContent =
                withContext(Dispatchers.IO) { historyManager.searchChatIdsByContent(query) }
            isSearching = false
        }
    }

    val filteredHistories =
        remember(chatHistories, searchQuery, matchedChatIdsByContent) {
            val query = searchQuery.trim()
            chatHistories
                .asSequence()
                .filter { history ->
                    query.isEmpty() ||
                        history.id in matchedChatIdsByContent ||
                        history.title.contains(query, ignoreCase = true) ||
                        history.characterCardName.orEmpty().contains(query, ignoreCase = true)
                }
                .sortedByDescending { it.updatedAt }
                .toList()
        }
    val pinnedChats = filteredHistories.filter { it.pinned && !it.archived }
    val recentChats = filteredHistories.filter { !it.pinned && !it.archived }
    val archivedChats = filteredHistories.filter { it.archived }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.mira_conversations),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
        }

        Button(
            onClick = onNewChat,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).heightIn(min = 48.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            Icon(Icons.Default.AddComment, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.new_chat))
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            singleLine = true,
            leadingIcon = {
                if (isSearching) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Search, contentDescription = null)
                }
            },
            placeholder = { Text(stringResource(R.string.search_chat_history_hint)) },
            shape = MaterialTheme.shapes.medium,
        )

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (pinnedChats.isNotEmpty()) {
                item(key = "pinned_header") { DrawerSectionLabel(R.string.mira_pinned_chats) }
                items(pinnedChats, key = { "pinned:${it.id}" }) { history ->
                    MiraConversationRow(
                        history = history,
                        selected = history.id == currentId,
                        streaming = history.id in activeStreamingChatIds,
                        onClick = { onSelectChat(history.id) },
                        onActions = { actionTarget = history },
                    )
                }
            }

            item(key = "recent_header") { DrawerSectionLabel(R.string.mira_recent_chats) }
            if (recentChats.isEmpty()) {
                item(key = "recent_empty") { DrawerEmptyLabel(R.string.mira_no_recent_chats) }
            } else {
                items(recentChats, key = { "recent:${it.id}" }) { history ->
                    MiraConversationRow(
                        history = history,
                        selected = history.id == currentId,
                        streaming = history.id in activeStreamingChatIds,
                        onClick = { onSelectChat(history.id) },
                        onActions = { actionTarget = history },
                    )
                }
            }

            item(key = "archive_header") { DrawerSectionLabel(R.string.mira_archived_chats) }
            if (archivedChats.isEmpty()) {
                item(key = "archive_empty") { DrawerEmptyLabel(R.string.mira_no_archived_chats) }
            } else {
                items(archivedChats, key = { "archive:${it.id}" }) { history ->
                    MiraConversationRow(
                        history = history,
                        selected = history.id == currentId,
                        streaming = history.id in activeStreamingChatIds,
                        onClick = { onSelectChat(history.id) },
                        onActions = { actionTarget = history },
                    )
                }
            }
            item(key = "drawer_bottom_space") { Spacer(Modifier.height(12.dp)) }
        }

        HorizontalDivider()
        DrawerDestinationRow(
            icon = Icons.AutoMirrored.Filled.MenuBook,
            label = stringResource(R.string.mate_nav_memory),
            onClick = onOpenMemory,
        )
        DrawerDestinationRow(
            icon = Icons.Default.Settings,
            label = stringResource(R.string.nav_settings),
            onClick = onOpenSettings,
        )
    }

    actionTarget?.let { history ->
        ModalBottomSheet(onDismissRequest = { actionTarget = null }) {
            Text(
                text = history.title,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!history.archived) {
                DrawerActionItem(
                    icon = Icons.Default.PushPin,
                    label = stringResource(if (history.pinned) R.string.unpin_chat else R.string.pin_chat),
                ) {
                    actionTarget = null
                    onPinChat(history.id, !history.pinned)
                }
            }
            DrawerActionItem(Icons.Default.Edit, stringResource(R.string.mira_rename_chat)) {
                actionTarget = null
                renameTarget = history
                renameText = history.title
            }
            DrawerActionItem(
                icon = if (history.archived) Icons.Default.Unarchive else Icons.Default.Archive,
                label = stringResource(if (history.archived) R.string.mira_unarchive_chat else R.string.mira_archive_chat),
            ) {
                actionTarget = null
                onArchiveChat(history.id, !history.archived)
            }
            DrawerActionItem(Icons.Default.Share, stringResource(R.string.mira_export_chat)) {
                actionTarget = null
                onExportChat(history.id)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            DrawerActionItem(
                icon = Icons.Default.Delete,
                label = stringResource(R.string.mira_delete_chat),
                destructive = true,
            ) {
                actionTarget = null
                deleteTarget = history
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    renameTarget?.let { history ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.mira_rename_chat)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = renameText.isNotBlank(),
                    onClick = {
                        onRenameChat(history.id, renameText.trim())
                        renameTarget = null
                    },
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    deleteTarget?.let { history ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.confirm_delete_chat)) },
            text = { Text(stringResource(R.string.delete_chat_confirmation, history.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteChat(history.id)
                        deleteTarget = null
                    },
                ) {
                    Text(stringResource(R.string.confirm_delete_action), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun DrawerSectionLabel(labelRes: Int) {
    Text(
        text = stringResource(labelRes),
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 6.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DrawerEmptyLabel(labelRes: Int) {
    Text(
        text = stringResource(labelRes),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MiraConversationRow(
    history: ChatHistory,
    selected: Boolean,
    streaming: Boolean,
    onClick: () -> Unit,
    onActions: () -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .combinedClickable(onClick = onClick, onLongClick = onActions),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (streaming) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = history.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle =
                    when {
                        history.isTemporary -> stringResource(R.string.mira_temporary_chat)
                        !history.characterCardName.isNullOrBlank() -> history.characterCardName
                        else -> null
                    }
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            IconButton(onClick = onActions, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more))
            }
        }
    }
}

@Composable
private fun DrawerDestinationRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(21.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun DrawerActionItem(
    icon: ImageVector,
    label: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = label,
                color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
