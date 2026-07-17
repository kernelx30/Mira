package com.ai.assistance.operit.ui.features.assistant.screens

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ActivePrompt
import com.ai.assistance.operit.data.model.CharacterCard
import com.ai.assistance.operit.data.model.CharacterCardMemoryProfileBindingMode
import com.ai.assistance.operit.data.preferences.ActivePromptManager
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.features.settings.screens.ModelPromptsSettingsScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@Composable
fun PersonaScreen(
    onNavigateToMarket: () -> Unit = {},
    onNavigateToPersonaGeneration: () -> Unit = {},
    onNavigateToChatManagement: () -> Unit = {},
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs =
        listOf(
            R.string.mate_persona_overview,
            R.string.character_cards,
            R.string.mate_persona_proactive,
            R.string.mate_persona_presence,
        )

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 8.dp) {
            tabs.forEachIndexed { index, titleRes ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(stringResource(titleRes)) },
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when (selectedTab) {
                0 ->
                    PersonaOverview(
                        onManageCharacters = { selectedTab = 1 },
                        onOpenPresence = { selectedTab = 3 },
                    )
                1 ->
                    ModelPromptsSettingsScreen(
                        onBackPressed = { selectedTab = 0 },
                        onNavigateToMarket = onNavigateToMarket,
                        onNavigateToPersonaGeneration = onNavigateToPersonaGeneration,
                        onNavigateToChatManagement = onNavigateToChatManagement,
                    )
                2 -> CompanionPresenceScreen()
                else -> AssistantConfigScreen()
            }
        }
    }
}

@Composable
private fun PersonaOverview(
    onManageCharacters: () -> Unit,
    onOpenPresence: () -> Unit,
) {
    val context = LocalContext.current
    val characterCardManager = remember(context) { CharacterCardManager.getInstance(context) }
    val activePromptManager = remember(context) { ActivePromptManager.getInstance(context) }
    val preferencesManager = remember(context) { UserPreferencesManager.getInstance(context) }
    val activePrompt by
        activePromptManager.activePromptFlow.collectAsState(
            initial = ActivePrompt.CharacterCard(CharacterCardManager.DEFAULT_CHARACTER_CARD_ID),
        )
    val activeCardFlow: Flow<CharacterCard?> =
        remember(activePrompt) {
            when (val prompt = activePrompt) {
                is ActivePrompt.CharacterCard -> characterCardManager.getCharacterCardFlow(prompt.id)
                is ActivePrompt.CharacterGroup -> flowOf(null)
            }
        }
    val activeCard by activeCardFlow.collectAsState(initial = null)
    val avatarFlow: Flow<String?> =
        remember(activeCard?.id) {
            activeCard?.id?.let(preferencesManager::getAiAvatarForCharacterCardFlow) ?: flowOf(null)
        }
    val avatarUri by avatarFlow.collectAsState(initial = null)

    LaunchedEffect(characterCardManager) {
        characterCardManager.initializeIfNeeded()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(72.dp).clip(CircleShape),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            if (!avatarUri.isNullOrBlank()) {
                                AsyncImage(
                                    model = Uri.parse(avatarUri),
                                    contentDescription = stringResource(R.string.character_avatar),
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.TheaterComedy,
                                        contentDescription = stringResource(R.string.character_avatar),
                                        modifier = Modifier.size(34.dp),
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.size(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = activeCard?.name ?: stringResource(R.string.mate_persona_group_active),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text =
                                    activeCard?.description?.takeIf { it.isNotBlank() }
                                        ?: stringResource(R.string.mate_persona_description_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onManageCharacters, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(stringResource(R.string.mate_persona_manage))
                        }
                        OutlinedButton(onClick = onOpenPresence, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(stringResource(R.string.mate_persona_voice_appearance))
                        }
                    }
                }
            }
        }

        item {
            PersonaDetailRow(
                icon = Icons.Default.Favorite,
                title = stringResource(R.string.mate_persona_relationship),
                value =
                    activeCard?.marks?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.mate_persona_relationship_empty),
            )
        }
        item {
            PersonaDetailRow(
                icon = Icons.Default.TheaterComedy,
                title = stringResource(R.string.mate_persona_behavior),
                value =
                    activeCard?.characterSetting?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.mate_persona_behavior_empty),
            )
        }
        item {
            PersonaDetailRow(
                icon = Icons.Default.AutoStories,
                title = stringResource(R.string.mate_persona_opening),
                value =
                    activeCard?.openingStatement?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.mate_persona_opening_empty),
            )
        }
        item {
            val memoryValue =
                if (activeCard?.memoryProfileBindingMode == CharacterCardMemoryProfileBindingMode.FIXED_PROFILE) {
                    stringResource(R.string.mate_persona_memory_fixed)
                } else {
                    stringResource(R.string.mate_persona_memory_global)
                }
            PersonaDetailRow(
                icon = Icons.Default.Security,
                title = stringResource(R.string.mate_persona_bindings),
                value =
                    "$memoryValue · " +
                        if (activeCard?.toolAccessConfig?.enabled == true) {
                            stringResource(R.string.mate_persona_tools_custom)
                        } else {
                            stringResource(R.string.mate_persona_tools_global)
                        },
            )
        }
    }
}

@Composable
private fun PersonaDetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
