package com.ai.assistance.operit.ui.features.memory.screens.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.CompanionMemoryRecordEntity
import com.ai.assistance.operit.data.model.CompanionMemoryType
import com.ai.assistance.operit.data.model.CompanionRecordScope
import com.ai.assistance.operit.data.repository.decodedLabel
import com.ai.assistance.operit.data.repository.decodedValue

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompanionMemoryEditorDialog(
    record: CompanionMemoryRecordEntity?,
    scope: CompanionRecordScope,
    defaultType: CompanionMemoryType,
    allowedTypes: List<CompanionMemoryType>,
    activeTargetName: String,
    isSaving: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSave: (CompanionMemoryType, String, String) -> Unit,
) {
    var selectedType by remember(record?.id, defaultType) {
        mutableStateOf(record?.type?.toMemoryType() ?: defaultType)
    }
    var displayLabel by remember(record?.id) { mutableStateOf(record?.decodedLabel().orEmpty()) }
    var value by remember(record?.id) { mutableStateOf(record?.decodedValue().orEmpty()) }
    val isCorrection = record != null
    val canSave =
        value.isNotBlank() &&
            (isCorrection || displayLabel.isNotBlank()) &&
            !isSaving

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = 560.dp)
                        .fillMaxHeight(0.82f)
                        .imePadding(),
                shape = MaterialTheme.shapes.large,
                tonalElevation = 6.dp,
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text =
                            stringResource(
                                if (isCorrection) {
                                    R.string.mate_memory_manual_correct_title
                                } else {
                                    R.string.mate_memory_manual_create_title
                                },
                            ),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier =
                            Modifier.padding(
                                start = 24.dp,
                                top = 24.dp,
                                end = 24.dp,
                                bottom = 16.dp,
                            ),
                    )
                    HorizontalDivider()

                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(24.dp),
                    ) {
                        Text(
                            text = scopeLabel(scope, activeTargetName),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(R.string.mate_memory_manual_scope_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = stringResource(R.string.mate_memory_type_label),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            allowedTypes.forEach { type ->
                                FilterChip(
                                    selected = selectedType == type,
                                    onClick = { if (!isCorrection) selectedType = type },
                                    enabled = !isCorrection,
                                    label = { Text(companionMemoryTypeLabel(type)) },
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))
                        OutlinedTextField(
                            value = displayLabel,
                            onValueChange = { displayLabel = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.mate_memory_manual_topic)) },
                            supportingText = {
                                Text(
                                    stringResource(
                                        if (isCorrection) {
                                            R.string.mate_memory_manual_topic_optional
                                        } else {
                                            R.string.mate_memory_manual_topic_required
                                        },
                                    ),
                                )
                            },
                            singleLine = true,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = value,
                            onValueChange = { value = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.mate_memory_manual_content)) },
                            minLines = 4,
                            maxLines = 10,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text =
                                stringResource(
                                    if (isCorrection) {
                                        R.string.mate_memory_manual_correct_desc
                                    } else {
                                        R.string.mate_memory_manual_create_desc
                                    },
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        errorMessage?.takeIf { it.isNotBlank() }?.let { message ->
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }

                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onDismiss, enabled = !isSaving) {
                            Text(stringResource(R.string.mate_memory_manual_cancel))
                        }
                        Button(
                            onClick = { onSave(selectedType, displayLabel.trim(), value.trim()) },
                            enabled = canSave,
                        ) {
                            Text(
                                stringResource(
                                    if (isCorrection) {
                                        R.string.mate_memory_manual_correct_action
                                    } else {
                                        R.string.mate_memory_manual_save_action
                                    },
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun companionMemoryTypeLabel(type: CompanionMemoryType): String =
    stringResource(
        when (type) {
            CompanionMemoryType.IDENTITY -> R.string.mate_memory_type_identity
            CompanionMemoryType.PREFERENCE -> R.string.mate_memory_type_preference
            CompanionMemoryType.FACT -> R.string.mate_memory_type_fact
            CompanionMemoryType.EVENT -> R.string.mate_memory_type_event
            CompanionMemoryType.ROUTINE -> R.string.mate_memory_type_routine
            CompanionMemoryType.BOUNDARY -> R.string.mate_memory_type_boundary
            CompanionMemoryType.COMMITMENT -> R.string.mate_memory_type_commitment
            CompanionMemoryType.RELATIONSHIP -> R.string.mate_memory_type_relationship
            CompanionMemoryType.SUMMARY -> R.string.mate_memory_type_summary
        },
    )

@Composable
private fun scopeLabel(scope: CompanionRecordScope, activeTargetName: String): String =
    when (scope) {
        CompanionRecordScope.USER -> stringResource(R.string.mate_memory_scope_user)
        CompanionRecordScope.COMPANION ->
            stringResource(R.string.mate_memory_scope_companion, activeTargetName)
        CompanionRecordScope.RELATIONSHIP ->
            stringResource(R.string.mate_memory_scope_relationship, activeTargetName)
        CompanionRecordScope.CONVERSATION -> stringResource(R.string.mate_memory_scope_conversation)
    }

private fun String.toMemoryType(): CompanionMemoryType =
    CompanionMemoryType.entries.firstOrNull { it.name == this } ?: CompanionMemoryType.FACT
