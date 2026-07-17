package com.ai.assistance.operit.ui.features.assistant.screens

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.companion.CompanionReminderScheduler
import com.ai.assistance.operit.core.companion.MiraCompanionService
import com.ai.assistance.operit.data.model.ActivePrompt
import com.ai.assistance.operit.data.preferences.ActivePromptManager
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.CharacterGroupCardManager
import com.ai.assistance.operit.data.preferences.CompanionReminderIntensity
import com.ai.assistance.operit.data.preferences.CompanionReminderPreferences
import com.ai.assistance.operit.data.preferences.CompanionReminderSettings
import com.ai.assistance.operit.data.preferences.CompanionReminderTarget
import com.ai.assistance.operit.data.preferences.CompanionReminderTargetType
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun CompanionPresenceScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activePromptManager = remember(context) { ActivePromptManager.getInstance(context) }
    val characterCardManager = remember(context) { CharacterCardManager.getInstance(context) }
    val groupManager = remember(context) { CharacterGroupCardManager.getInstance(context) }
    val preferences = remember(context) { CompanionReminderPreferences.getInstance(context) }
    val scheduler = remember(context) { CompanionReminderScheduler.getInstance(context) }
    val activePrompt by
        activePromptManager.activePromptFlow.collectAsState(
            initial = ActivePrompt.CharacterCard(CharacterCardManager.DEFAULT_CHARACTER_CARD_ID)
        )
    val target = remember(activePrompt) { activePrompt.toReminderTarget() }
    val settings by
        remember(target) { preferences.settingsFlow(target) }
            .collectAsState(initial = CompanionReminderSettings())
    val keepAliveEnabled by
        preferences.keepAliveEnabledFlow.collectAsState(initial = false)
    var targetName by remember { mutableStateOf("") }
    var dailyLimitDraft by remember { mutableIntStateOf(settings.dailyLimit) }
    var notificationPermissionGranted by
        remember {
            mutableStateOf(
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) == PackageManager.PERMISSION_GRANTED
            )
        }
    var exactAlarmGranted by remember { mutableStateOf(scheduler.canScheduleExactAlarms()) }
    val remindersOperational = settings.enabled && notificationPermissionGranted

    LaunchedEffect(activePrompt) {
        targetName =
            when (val prompt = activePrompt) {
                is ActivePrompt.CharacterCard -> characterCardManager.getCharacterCard(prompt.id).name
                is ActivePrompt.CharacterGroup -> groupManager.getCharacterGroupCard(prompt.id)?.name.orEmpty()
            }
    }
    LaunchedEffect(settings.dailyLimit) {
        dailyLimitDraft = settings.dailyLimit
    }

    fun saveAndReschedule(action: suspend () -> Unit) {
        scope.launch {
            action()
            scheduler.syncAllProfiles()
        }
    }

    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            notificationPermissionGranted = isGranted
            if (isGranted) {
                saveAndReschedule { preferences.saveEnabled(target, true) }
            } else {
                Toast.makeText(
                    context,
                    context.getString(R.string.notification_permission_denied),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }

    val keepAlivePermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            notificationPermissionGranted = isGranted
            if (isGranted) {
                scope.launch {
                    preferences.saveKeepAliveEnabled(true)
                    if (!MiraCompanionService.startKeepAlive(context)) {
                        preferences.saveKeepAliveEnabled(false)
                        Toast.makeText(
                            context,
                            context.getString(R.string.mira_companion_service_start_failed),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            } else {
                Toast.makeText(
                    context,
                    context.getString(R.string.notification_permission_denied),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }

    val exactAlarmLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            exactAlarmGranted = scheduler.canScheduleExactAlarms()
            scope.launch { scheduler.syncAllProfiles() }
        }

    fun updateRemindersEnabled(enabled: Boolean) {
        if (!enabled) {
            saveAndReschedule { preferences.saveEnabled(target, false) }
            return
        }
        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionGranted = true
            saveAndReschedule { preferences.saveEnabled(target, true) }
        } else {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun updateKeepAliveEnabled(enabled: Boolean) {
        if (!enabled) {
            scope.launch {
                preferences.saveKeepAliveEnabled(false)
                MiraCompanionService.stopKeepAlive(context)
            }
            return
        }
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
        ) {
            keepAlivePermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        scope.launch {
            preferences.saveKeepAliveEnabled(true)
            if (!MiraCompanionService.startKeepAlive(context)) {
                preferences.saveKeepAliveEnabled(false)
                Toast.makeText(
                    context,
                    context.getString(R.string.mira_companion_service_start_failed),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || exactAlarmGranted) return
        exactAlarmLauncher.launch(
            Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:${context.packageName}"),
            )
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.companion_presence_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = targetName.ifBlank { stringResource(R.string.mate_default_companion) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                ReminderSwitchRow(
                    icon = Icons.Default.PhoneAndroid,
                    title = stringResource(R.string.companion_presence_keep_alive),
                    supportingText = stringResource(R.string.companion_presence_keep_alive_desc),
                    checked = keepAliveEnabled,
                    onCheckedChange = ::updateKeepAliveEnabled,
                )
            }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                ReminderActionRow(
                    icon = Icons.Default.Schedule,
                    title = stringResource(R.string.companion_presence_exact_alarm),
                    supportingText =
                        stringResource(
                            if (exactAlarmGranted) {
                                R.string.companion_presence_exact_alarm_granted
                            } else {
                                R.string.companion_presence_exact_alarm_fallback
                            }
                        ),
                    actionLabel =
                        if (exactAlarmGranted || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                            null
                        } else {
                            stringResource(R.string.companion_presence_exact_alarm_grant)
                        },
                    onAction = ::requestExactAlarmPermission,
                )
            }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column {
                    ReminderSwitchRow(
                        icon = Icons.Default.NotificationsActive,
                        title = stringResource(R.string.companion_presence_reminders),
                        checked = remindersOperational,
                        onCheckedChange = ::updateRemindersEnabled,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.size(10.dp))
                            Text(
                                text = stringResource(R.string.companion_presence_intensity),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        ReminderIntensityControl(
                            selected = settings.intensity,
                            enabled = remindersOperational,
                            onSelect = { intensity ->
                                saveAndReschedule { preferences.saveIntensity(target, intensity) }
                            },
                        )
                    }
                }
            }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column {
                    ReminderSwitchRow(
                        icon = Icons.Default.Bedtime,
                        title = stringResource(R.string.companion_presence_quiet_hours),
                        checked = settings.quietHoursEnabled,
                        enabled = remindersOperational,
                        onCheckedChange = { enabled ->
                            saveAndReschedule { preferences.saveQuietHoursEnabled(target, enabled) }
                        },
                    )
                    if (settings.quietHoursEnabled) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            ReminderTimeButton(
                                label = stringResource(R.string.companion_presence_quiet_start),
                                minutes = settings.quietStartMinutes,
                                enabled = remindersOperational,
                                modifier = Modifier.weight(1f),
                                onTimeSelected = { minutes ->
                                    saveAndReschedule {
                                        preferences.saveQuietHours(target, minutes, settings.quietEndMinutes)
                                    }
                                },
                            )
                            ReminderTimeButton(
                                label = stringResource(R.string.companion_presence_quiet_end),
                                minutes = settings.quietEndMinutes,
                                enabled = remindersOperational,
                                modifier = Modifier.weight(1f),
                                onTimeSelected = { minutes ->
                                    saveAndReschedule {
                                        preferences.saveQuietHours(target, settings.quietStartMinutes, minutes)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.companion_presence_daily_limit),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = dailyLimitDraft.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Slider(
                        value = dailyLimitDraft.toFloat(),
                        onValueChange = { dailyLimitDraft = it.roundToInt().coerceIn(1, 8) },
                        onValueChangeFinished = {
                            saveAndReschedule { preferences.saveDailyLimit(target, dailyLimitDraft) }
                        },
                        enabled = remindersOperational,
                        valueRange = 1f..8f,
                        steps = 6,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReminderSwitchRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    supportingText: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.size(10.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            supportingText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun ReminderActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    supportingText: String,
    actionLabel: String?,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.size(10.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        actionLabel?.let {
            TextButton(onClick = onAction) { Text(it) }
        }
    }
}

@Composable
private fun ReminderIntensityControl(
    selected: CompanionReminderIntensity,
    enabled: Boolean,
    onSelect: (CompanionReminderIntensity) -> Unit,
) {
    val options =
        listOf(
            CompanionReminderIntensity.EXPLICIT_ONLY to R.string.companion_presence_intensity_explicit,
            CompanionReminderIntensity.OCCASIONAL to R.string.companion_presence_intensity_occasional,
            CompanionReminderIntensity.DAILY to R.string.companion_presence_intensity_daily,
        )
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        options.forEachIndexed { index, (value, labelRes) ->
            val selectedOption = selected == value
            Surface(
                onClick = { onSelect(value) },
                modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp),
                enabled = enabled,
                shape =
                    RoundedCornerShape(
                        topStart = if (index == 0) 8.dp else 0.dp,
                        bottomStart = if (index == 0) 8.dp else 0.dp,
                        topEnd = if (index == options.lastIndex) 8.dp else 0.dp,
                        bottomEnd = if (index == options.lastIndex) 8.dp else 0.dp,
                    ),
                color =
                    if (selectedOption) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                contentColor =
                    if (selectedOption) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(labelRes),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReminderTimeButton(
    label: String,
    minutes: Int,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onTimeSelected: (Int) -> Unit,
) {
    val context = LocalContext.current
    Surface(
        onClick = {
            TimePickerDialog(
                context,
                { _, hour, minute -> onTimeSelected(hour * 60 + minute) },
                minutes / 60,
                minutes % 60,
                true,
            ).show()
        },
        modifier = modifier.heightIn(min = 56.dp),
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.size(8.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall)
                Text(
                    String.format(Locale.getDefault(), "%02d:%02d", minutes / 60, minutes % 60),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

private fun ActivePrompt.toReminderTarget(): CompanionReminderTarget =
    when (this) {
        is ActivePrompt.CharacterCard ->
            CompanionReminderTarget(CompanionReminderTargetType.CHARACTER, id)
        is ActivePrompt.CharacterGroup ->
            CompanionReminderTarget(CompanionReminderTargetType.GROUP, id)
    }
