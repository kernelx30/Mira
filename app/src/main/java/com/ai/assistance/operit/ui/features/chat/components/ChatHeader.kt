package com.ai.assistance.operit.ui.features.chat.components

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsVoice
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material.icons.filled.Token
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ContextWindowUsage
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.ui.adaptive.LocalAdaptiveWindowMetrics
import kotlin.math.roundToInt

private const val CHAT_HEADER_CHARACTER_NAME_MAX_LENGTH = 24

data class SessionModelChoice(
    val configId: String,
    val configName: String,
    val modelIndex: Int,
    val modelName: String,
)

private fun String.toChatHeaderName(maxLength: Int = CHAT_HEADER_CHARACTER_NAME_MAX_LENGTH): String =
    if (length <= maxLength) this else take(maxLength) + "..."

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ChatHeader(
    showChatHistorySelector: Boolean,
    onToggleChatHistorySelector: () -> Unit,
    modifier: Modifier = Modifier,
    onLaunchFloatingWindow: () -> Unit = {},
    isFloatingMode: Boolean = false,
    activeCharacterName: String,
    activeCharacterAvatarUri: String?,
    onCharacterClick: () -> Unit,
    onCreateNewChat: () -> Unit,
    onCreateTemporaryChat: () -> Unit,
    onExportChat: () -> Unit,
    onArchiveChat: () -> Unit,
    onDeleteChat: () -> Unit,
    isArchived: Boolean,
    hasDraft: Boolean,
    autoReadEnabled: Boolean,
    onAutoReadEnabledChange: (Boolean) -> Unit,
    currentContextTokens: Long,
    maxContextTokens: Long,
    contextWindowUsage: ContextWindowUsage,
    onRequestContextRefresh: () -> Unit,
    onManualMemoryUpdate: () -> Unit,
    onOpenVoiceSettings: () -> Unit = {},
    onOpenCapabilities: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    var showMoreMenu by remember { mutableStateOf(false) }
    var showContextDetails by remember { mutableStateOf(false) }
    var showDraftConfirmation by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    val displayCharacterName =
        activeCharacterName.ifBlank { stringResource(R.string.mate_default_companion) }
            .toChatHeaderName()
    val adaptiveMetrics = LocalAdaptiveWindowMetrics.current
    val compactHeader = adaptiveMetrics.shouldCompactChatHeader

    val requestNewChat = {
        if (hasDraft) {
            showDraftConfirmation = true
        } else {
            onCreateNewChat()
        }
    }

    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            onClick = onToggleChatHistorySelector,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            shadowElevation = 2.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = stringResource(R.string.mira_session_list),
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Surface(
            modifier =
                Modifier
                    .weight(1f, fill = compactHeader)
                    .widthIn(max = if (compactHeader) 240.dp else 360.dp)
                    .heightIn(min = 48.dp),
            onClick = onCharacterClick,
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            shadowElevation = 2.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CharacterAvatar(activeCharacterAvatarUri)
                Text(
                    text = displayCharacterName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.mira_switch_character),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Box {
            Surface(
                modifier = Modifier.width(104.dp).height(48.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                shadowElevation = 2.dp,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .clickable(onClick = requestNewChat),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddComment,
                            contentDescription = stringResource(R.string.new_chat),
                            modifier = Modifier.size(21.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Box(
                        modifier =
                            Modifier
                                .width(1.dp)
                                .height(20.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                    )
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .clickable { showMoreMenu = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.more),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
            DropdownMenu(
                expanded = showMoreMenu,
                onDismissRequest = { showMoreMenu = false },
                modifier = Modifier.widthIn(min = 220.dp, max = 288.dp),
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                MiraMenuItem(Icons.Default.SettingsVoice, R.string.screen_title_speech_services_settings) {
                    showMoreMenu = false
                    onOpenVoiceSettings()
                }
                MiraMenuItem(
                    if (autoReadEnabled) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Outlined.VolumeOff,
                    if (autoReadEnabled) R.string.auto_read_active else R.string.auto_read_message,
                ) {
                    showMoreMenu = false
                    onAutoReadEnabledChange(!autoReadEnabled)
                }
                MiraMenuItem(Icons.Default.Psychology, R.string.mira_save_memory_now) {
                    showMoreMenu = false
                    onManualMemoryUpdate()
                }
                MiraMenuItem(Icons.Default.Token, R.string.context_capacity) {
                    showMoreMenu = false
                    onRequestContextRefresh()
                    showContextDetails = true
                }
                HorizontalDivider()
                MiraMenuItem(Icons.Default.AddComment, R.string.mira_temporary_chat) {
                    showMoreMenu = false
                    onCreateTemporaryChat()
                }
                MiraMenuItem(Icons.Default.Share, R.string.mira_export_chat) {
                    showMoreMenu = false
                    onExportChat()
                }
                MiraMenuItem(
                    if (isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                    if (isArchived) R.string.mira_unarchive_chat else R.string.mira_archive_chat,
                ) {
                    showMoreMenu = false
                    onArchiveChat()
                }
                MiraMenuItem(Icons.Default.Delete, R.string.mira_delete_chat) {
                    showMoreMenu = false
                    showDeleteConfirmation = true
                }
                HorizontalDivider()
                MiraMenuItem(Icons.Default.Extension, R.string.mira_advanced_features) {
                    showMoreMenu = false
                    onOpenCapabilities()
                }
                MiraMenuItem(Icons.Default.Settings, R.string.nav_settings) {
                    showMoreMenu = false
                    onOpenSettings()
                }
                MiraMenuItem(Icons.Default.PictureInPicture, if (isFloatingMode) R.string.close_floating_window else R.string.open_floating_window) {
                    showMoreMenu = false
                    onLaunchFloatingWindow()
                }
            }
        }
    }

    if (showDraftConfirmation) {
        AlertDialog(
            onDismissRequest = { showDraftConfirmation = false },
            title = { Text(stringResource(R.string.mira_new_chat_draft_title)) },
            text = { Text(stringResource(R.string.mira_new_chat_draft_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDraftConfirmation = false
                        onCreateNewChat()
                    },
                ) {
                    Text(stringResource(R.string.mira_save_draft_and_new))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDraftConfirmation = false }) {
                    Text(stringResource(R.string.mira_stay_current_chat))
                }
            },
        )
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(stringResource(R.string.confirm_delete_chat)) },
            text = { Text(stringResource(R.string.mira_delete_chat_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        onDeleteChat()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.confirm_delete_action),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showContextDetails) {
        ContextCapacityDialog(
            currentTokens = currentContextTokens,
            maxTokens = maxContextTokens,
            usage = contextWindowUsage,
            onDismiss = { showContextDetails = false },
        )
    }
}

@Composable
private fun CharacterAvatar(avatarUri: String?) {
    Box(modifier = Modifier.size(34.dp)) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            if (!avatarUri.isNullOrBlank()) {
                Image(
                    painter = rememberAsyncImagePainter(Uri.parse(avatarUri)),
                    contentDescription = stringResource(R.string.character_avatar),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = stringResource(R.string.character_avatar),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
        }
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF3F8F6B)),
        )
    }
}

@Composable
private fun MiraMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    labelRes: Int,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(stringResource(labelRes)) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
private fun SessionControlsContent(
    currentModelName: String,
    memoryEnabled: Boolean,
    onMemoryEnabledChange: (Boolean) -> Unit,
    autoReadEnabled: Boolean,
    onAutoReadEnabledChange: (Boolean) -> Unit,
    thinkingEnabled: Boolean,
    thinkingQualityLevel: Int,
    onThinkingEnabledChange: (Boolean) -> Unit,
    onThinkingQualityLevelChange: (Int) -> Unit,
    maxContextMode: Boolean,
    maxContextTokens: Long,
    onOpenModelPicker: () -> Unit,
    onOpenContext: () -> Unit,
    onOpenModelSettings: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.mira_session_controls),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        SessionActionRow(
            label = stringResource(R.string.mira_session_model),
            value = currentModelName,
            onClick = onOpenModelPicker,
        )
        SessionSwitchRow(
            label = stringResource(R.string.mira_session_memory),
            checked = memoryEnabled,
            onCheckedChange = onMemoryEnabledChange,
        )
        SessionSwitchRow(
            label = stringResource(R.string.mira_session_auto_read),
            checked = autoReadEnabled,
            onCheckedChange = onAutoReadEnabledChange,
        )
        SessionSwitchRow(
            label = stringResource(R.string.mira_session_thinking),
            checked = thinkingEnabled,
            onCheckedChange = onThinkingEnabledChange,
        )
        if (thinkingEnabled) {
            Text(
                text = stringResource(R.string.mira_thinking_level_value, thinkingQualityLevel),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = thinkingQualityLevel.toFloat(),
                onValueChange = { onThinkingQualityLevelChange(it.roundToInt()) },
                valueRange =
                    ApiPreferences.MIN_THINKING_QUALITY_LEVEL.toFloat()..
                        ApiPreferences.MAX_THINKING_QUALITY_LEVEL.toFloat(),
                steps = 3,
            )
        }
        SessionActionRow(
            label = stringResource(R.string.mira_session_context),
            value =
                stringResource(
                    if (maxContextMode) R.string.mira_context_max else R.string.mira_context_standard,
                    (maxContextTokens / 1024L).coerceAtLeast(0L),
                ),
            onClick = onOpenContext,
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        TextButton(onClick = onOpenModelSettings, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.mira_manage_models_api))
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun ModelPickerContent(
    choices: List<SessionModelChoice>,
    selectedConfigId: String?,
    selectedModelIndex: Int,
    onBack: () -> Unit,
    onSelect: (String?, Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            Text(
                text = stringResource(R.string.mira_select_model),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
            item(key = "inherit") {
                ModelChoiceRow(
                    title = stringResource(R.string.mira_follow_character_model),
                    subtitle = stringResource(R.string.mira_follow_character_model_desc),
                    selected = selectedConfigId == null,
                    onClick = { onSelect(null, 0) },
                )
            }
            items(choices, key = { "${it.configId}:${it.modelIndex}" }) { choice ->
                ModelChoiceRow(
                    title = choice.modelName,
                    subtitle = choice.configName,
                    selected = selectedConfigId == choice.configId && selectedModelIndex == choice.modelIndex,
                    onClick = { onSelect(choice.configId, choice.modelIndex) },
                )
            }
        }
    }
}

@Composable
private fun ModelChoiceRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun NewChatSheetContent(
    characterName: String,
    modelName: String,
    memoryEnabled: Boolean,
    onCreate: () -> Unit,
    onCreateTemporary: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.new_chat),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(characterName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
        Text(
            text = stringResource(R.string.mira_new_chat_model, modelName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(if (memoryEnabled) R.string.mira_new_chat_memory_inherit else R.string.mira_new_chat_memory_off),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Button(onClick = onCreate, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Text(stringResource(R.string.mira_create_chat))
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))
        Text(
            text = stringResource(R.string.mira_temporary_chat),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.mira_temporary_chat_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        TextButton(onClick = onCreateTemporary, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text(stringResource(R.string.mira_start_temporary_chat))
        }
    }
}

@Composable
private fun SessionActionRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(
            value,
            modifier = Modifier.widthIn(max = 220.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SessionSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
