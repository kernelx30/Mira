package com.ai.assistance.operit.ui.features.settings.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.ManageHistory
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import com.ai.assistance.operit.core.tools.defaultTool.standard.CookiePrivacyManager
import com.ai.assistance.operit.data.preferences.GitHubAuthPreferences
import com.ai.assistance.operit.ui.common.MiraLogo
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.launch

private val SettingsScreenScrollPosition = mutableStateOf(0)

private data class MateSettingsItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

private data class MateSettingsSection(
    val title: String,
    val icon: ImageVector,
    val items: List<MateSettingsItem>,
)

@Composable
fun SettingsScreen(
    onNavigateToUserPreferences: () -> Unit,
    navigateToGitHubAccount: () -> Unit,
    navigateToToolPermissions: () -> Unit,
    navigateToModelConfig: () -> Unit,
    navigateToThemeSettings: () -> Unit,
    navigateToGlobalDisplaySettings: () -> Unit,
    navigateToModelPrompts: () -> Unit,
    navigateToFunctionalConfig: () -> Unit,
    navigateToChatHistorySettings: () -> Unit,
    navigateToChatBackupSettings: () -> Unit,
    navigateToLanguageSettings: () -> Unit,
    navigateToSpeechServicesSettings: () -> Unit,
    navigateToExternalHttpChatSettings: () -> Unit,
    navigateToPersonaCardGeneration: () -> Unit,
    navigateToWaifuModeSettings: () -> Unit,
    navigateToTokenUsageStatistics: () -> Unit,
    navigateToContextSummarySettings: () -> Unit,
    navigateToLayoutAdjustmentSettings: () -> Unit,
    navigateToCapabilities: () -> Unit,
    navigateToPackageManager: () -> Unit,
    navigateToTerminalSetup: () -> Unit,
    navigateToCompanionSettings: () -> Unit,
    navigateToCompanionPresence: () -> Unit,
) {
    val context = LocalContext.current
    val githubAuth = remember { GitHubAuthPreferences.getInstance(context) }
    val scope = rememberCoroutineScope()
    var showClearCookieConfirm by remember { mutableStateOf(false) }
    val isGitHubLoggedIn by githubAuth.isLoggedInFlow.collectAsState(initial = false)
    val gitHubUser by githubAuth.userInfoFlow.collectAsState(initial = null)
    val scrollState = rememberScrollState(SettingsScreenScrollPosition.value)

    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value }.collect { SettingsScreenScrollPosition.value = it }
    }

    val sections =
        listOf(
            MateSettingsSection(
                title = stringResource(R.string.settings_section_character_voice),
                icon = Icons.Filled.Face,
                items =
                    listOf(
                        MateSettingsItem(
                            stringResource(R.string.nav_assistant_config),
                            stringResource(R.string.mate_persona_behavior),
                            Icons.Filled.Face,
                            navigateToCompanionSettings,
                        ),
                        MateSettingsItem(
                            stringResource(R.string.persona_card_generation),
                            stringResource(R.string.persona_card_generation_desc),
                            Icons.Filled.Face,
                            navigateToPersonaCardGeneration,
                        ),
                        MateSettingsItem(
                            stringResource(R.string.waifu_mode_settings),
                            stringResource(R.string.waifu_mode_settings_desc),
                            Icons.Filled.EmojiEmotions,
                            navigateToWaifuModeSettings,
                        ),
                        MateSettingsItem(
                            stringResource(R.string.settings_speech_services),
                            stringResource(R.string.settings_speech_services_subtitle),
                            Icons.Filled.RecordVoiceOver,
                            navigateToSpeechServicesSettings,
                        ),
                    ),
            ),
            MateSettingsSection(
                title = stringResource(R.string.settings_section_chat_memory),
                icon = Icons.Filled.AutoStories,
                items =
                    listOf(
                        MateSettingsItem(
                            stringResource(R.string.settings_user_preferences),
                            stringResource(R.string.settings_user_preferences_subtitle),
                            Icons.Filled.Face,
                            onNavigateToUserPreferences,
                        ),
                        MateSettingsItem(
                            stringResource(R.string.settings_section_context_summary),
                            stringResource(R.string.settings_context_summary_subtitle),
                            Icons.Filled.Memory,
                            navigateToContextSummarySettings,
                        ),
                        MateSettingsItem(
                            stringResource(R.string.settings_chat_history_management),
                            stringResource(R.string.settings_chat_history_management_subtitle),
                            Icons.Filled.ManageHistory,
                            navigateToChatHistorySettings,
                        ),
                        MateSettingsItem(
                            stringResource(R.string.settings_data_backup),
                            stringResource(R.string.settings_data_backup_desc),
                            Icons.Filled.CloudUpload,
                            navigateToChatBackupSettings,
                        ),
                    ),
            ),
            MateSettingsSection(
                title = stringResource(R.string.settings_section_proactive_companion),
                icon = Icons.Filled.Favorite,
                items =
                    listOf(
                        MateSettingsItem(
                            stringResource(R.string.companion_presence_title),
                            stringResource(R.string.companion_presence_keep_alive_desc),
                            Icons.Filled.Favorite,
                            navigateToCompanionPresence,
                        ),
                    ),
            ),
            MateSettingsSection(
                title = stringResource(R.string.settings_section_models_api),
                icon = Icons.Filled.Api,
                items =
                    listOf(
                        MateSettingsItem(
                            stringResource(R.string.settings_model_parameters),
                            stringResource(R.string.settings_model_params_subtitle),
                            Icons.Filled.Api,
                            navigateToModelConfig,
                        ),
                        MateSettingsItem(
                            stringResource(R.string.settings_functional_model),
                            stringResource(R.string.settings_functional_model_subtitle),
                            Icons.Filled.Tune,
                            navigateToFunctionalConfig,
                        ),
                        MateSettingsItem(
                            stringResource(R.string.settings_prompt_title),
                            stringResource(R.string.settings_system_prompts_subtitle),
                            Icons.Filled.ChatBubble,
                            navigateToModelPrompts,
                        ),
                    ),
            ),
            MateSettingsSection(
                title = stringResource(R.string.settings_section_privacy_advanced),
                icon = Icons.Filled.Security,
                items =
                    listOf(
                        MateSettingsItem(
                            stringResource(R.string.settings_theme_appearance),
                            stringResource(R.string.settings_theme_subtitle),
                            Icons.Filled.Palette,
                            navigateToThemeSettings,
                        ),
                        MateSettingsItem(
                            stringResource(R.string.settings_global_display),
                            stringResource(R.string.settings_global_display_subtitle),
                            Icons.Filled.Visibility,
                            navigateToGlobalDisplaySettings,
                        ),
                        MateSettingsItem(
                            stringResource(R.string.language_settings),
                            stringResource(R.string.settings_language_subtitle),
                            Icons.Filled.Language,
                            navigateToLanguageSettings,
                        ),
                        MateSettingsItem(
                            stringResource(R.string.github_account),
                            gitHubUser?.login?.let { "@$it" }
                                ?: stringResource(R.string.github_account_not_logged_in),
                            Icons.Filled.Person,
                            navigateToGitHubAccount,
                        ),
                        MateSettingsItem(
                            stringResource(R.string.settings_token_usage_stats),
                            stringResource(R.string.settings_token_usage_subtitle),
                            Icons.Filled.Analytics,
                            navigateToTokenUsageStatistics,
                        ),
                        MateSettingsItem(
                            stringResource(R.string.settings_external_http_chat),
                            stringResource(R.string.settings_external_http_chat_subtitle),
                            Icons.Filled.SettingsEthernet,
                            navigateToExternalHttpChatSettings,
                        ),
                        MateSettingsItem(
                            stringResource(R.string.settings_clear_cookies),
                            stringResource(R.string.settings_clear_cookies_subtitle),
                            Icons.Filled.DeleteSweep,
                        ) { showClearCookieConfirm = true },
                        MateSettingsItem(
                            stringResource(R.string.mira_advanced_features),
                            stringResource(R.string.settings_capability_center_subtitle),
                            Icons.Filled.Extension,
                            navigateToCapabilities,
                        ),
                    ),
            ),
        )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wideLayout = maxWidth >= 840.dp
        Column(
            modifier =
                Modifier.align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .widthIn(max = 1180.dp)
                    .verticalScroll(scrollState)
                    .padding(horizontal = if (wideLayout) 28.dp else 16.dp, vertical = 20.dp),
        ) {
            MateSettingsHeader(isGitHubLoggedIn = isGitHubLoggedIn, username = gitHubUser?.login)
            Spacer(Modifier.height(24.dp))

            if (wideLayout) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        sections.take(3).forEach { MateSettingsSectionCard(it) }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        sections.drop(3).forEach { MateSettingsSectionCard(it) }
                    }
                }
            } else {
                sections.forEach { MateSettingsSectionCard(it) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showClearCookieConfirm) {
        AlertDialog(
            onDismissRequest = { showClearCookieConfirm = false },
            title = { Text(stringResource(R.string.clear_cookies_dialog_title)) },
            text = { Text(stringResource(R.string.clear_cookies_dialog_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearCookieConfirm = false
                        scope.launch {
                            runCatching { CookiePrivacyManager.clearAllCookies() }
                                .onSuccess {
                                    Toast.makeText(
                                            context,
                                            context.getString(R.string.clear_cookies_success),
                                            Toast.LENGTH_SHORT,
                                        )
                                        .show()
                                }
                                .onFailure { error ->
                                    AppLogger.e("SettingsScreen", "Failed to clear cookies", error)
                                    Toast.makeText(
                                            context,
                                            context.getString(R.string.clear_cookies_failed),
                                            Toast.LENGTH_SHORT,
                                        )
                                        .show()
                                }
                        }
                    },
                ) { Text(stringResource(R.string.clear_cookies_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearCookieConfirm = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun MateSettingsHeader(isGitHubLoggedIn: Boolean, username: String?) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(64.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
        ) {
            MiraLogo(modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text =
                    if (isGitHubLoggedIn && !username.isNullOrBlank()) "@$username"
                    else stringResource(R.string.mate_profile_local),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ColumnScope.MateSettingsSectionCard(section: MateSettingsSection) {
    Row(
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = section.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = section.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        section.items.forEachIndexed { index, item ->
            MateSettingsListItem(item)
            if (index != section.items.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 56.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                )
            }
        }
    }
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun MateSettingsListItem(item: MateSettingsItem) {
    ListItem(
        modifier = Modifier.fillMaxWidth().clickable(onClick = item.onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Icon(
                    item.icon,
                    contentDescription = null,
                    modifier = Modifier.size(21.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        trailingContent = {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        },
    )
}
