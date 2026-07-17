package com.ai.assistance.operit.ui.features.memory.screens.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.Memory
import com.ai.assistance.operit.ui.features.memory.screens.graph.model.Edge
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemoryInfoDialog(
        memory: Memory,
        showAdvancedFields: Boolean = true,
        onDismiss: () -> Unit,
        onEdit: () -> Unit,
        onDelete: () -> Unit
) {
    val scrollState = rememberScrollState()
    val dateFormat =
        remember(showAdvancedFields) {
            SimpleDateFormat(
                if (showAdvancedFields) "yyyy-MM-dd HH:mm:ss" else "yyyy/MM/dd HH:mm",
                Locale.getDefault(),
            )
        }

    AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text =
                        if (showAdvancedFields) {
                            stringResource(R.string.memory_details_title)
                        } else {
                            memory.title
                        },
                )
            },
            text = {
                Column(
                        modifier = Modifier.verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (showAdvancedFields) {
                        Text("${stringResource(R.string.memory_title)}: ${memory.title}", style = MaterialTheme.typography.titleMedium)
                        HorizontalDivider()
                        Text(stringResource(R.string.memory_content) + ":", style = MaterialTheme.typography.titleSmall)
                        Text(memory.content)
                        HorizontalDivider()
                        Text("${stringResource(R.string.memory_folder)}: ${memory.folderPath?.ifEmpty { stringResource(R.string.memory_uncategorized) }}", style = MaterialTheme.typography.bodySmall)
                        Text("${stringResource(R.string.memory_uuid)}: ${memory.uuid}", style = MaterialTheme.typography.bodySmall)
                        Text("${stringResource(R.string.memory_source)}: ${memory.source}", style = MaterialTheme.typography.bodySmall)
                        Text(
                                "${stringResource(R.string.memory_importance)}: ${String.format("%.2f", memory.importance)}",
                                style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                                "${stringResource(R.string.memory_credibility)}: ${String.format("%.2f", memory.credibility)}",
                                style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                                "${stringResource(R.string.memory_created_at)}: ${dateFormat.format(memory.createdAt)}",
                                style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                                "${stringResource(R.string.memory_updated_at)}: ${dateFormat.format(memory.updatedAt)}",
                                style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Text(
                            text = memory.content.ifBlank { stringResource(R.string.mate_memory_content_empty) },
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        HorizontalDivider()
                        Text(
                            text = stringResource(friendlyMemorySourceRes(memory)),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(R.string.mate_memory_updated_value, dateFormat.format(memory.updatedAt)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text =
                                stringResource(
                                    R.string.mate_memory_confidence_value,
                                    (memory.credibility * 100).toInt(),
                                ) + " · " +
                                    stringResource(
                                        R.string.mate_memory_importance_value,
                                        (memory.importance * 100).toInt(),
                                    ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalArrangement = Arrangement.Center
                ) {
                    Button(onClick = onEdit) { Text(stringResource(R.string.memory_edit)) }
                    Button(
                            onClick = onDelete,
                            colors =
                                    ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error
                                    )
                    ) { Text(stringResource(R.string.memory_delete)) }
                    OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.memory_close)) }
                }
            }
    )
}

private fun friendlyMemorySourceRes(memory: Memory): Int {
    if (memory.isDocumentNode) return R.string.mate_memory_source_document
    val source = memory.source.lowercase(Locale.ROOT)
    return when {
        "chat" in source || "conversation" in source || "summary" in source -> R.string.mate_memory_source_chat
        "user" in source || "manual" in source -> R.string.mate_memory_source_manual
        "import" in source || "file" in source || "web" in source -> R.string.mate_memory_source_import
        else -> R.string.mate_memory_source_organized
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EdgeInfoDialog(
    edge: Edge,
    graph: com.ai.assistance.operit.ui.features.memory.screens.graph.model.Graph,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val sourceNode = graph.nodes.find { it.id == edge.sourceId }
    val targetNode = graph.nodes.find { it.id == edge.targetId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.memory_link_details)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${stringResource(R.string.memory_from)}: ${sourceNode?.label ?: stringResource(R.string.memory_uncategorized)}")
                Text("${stringResource(R.string.memory_to)}: ${targetNode?.label ?: stringResource(R.string.memory_uncategorized)}")
                HorizontalDivider()
                Text("${stringResource(R.string.memory_type)}: ${edge.label}")
                Text("${stringResource(R.string.memory_weight)}: ${edge.weight}")
            }
        },
        confirmButton = {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.Center
            ) {
                Button(onClick = onEdit) { Text(stringResource(R.string.memory_edit)) }
                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.memory_delete)) }
                OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.memory_close)) }
            }
        }
    )
}

@Composable
fun EditEdgeDialog(
    edge: Edge,
    onDismiss: () -> Unit,
    onSave: (type: String, weight: Float, description: String) -> Unit
) {
    var type by remember { mutableStateOf(edge.label ?: "related") }
    var weight by remember { mutableStateOf(edge.weight.toString()) }
    var description by remember { mutableStateOf("") } // 假设需要编辑description

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.memory_edit_link)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = type, onValueChange = { type = it }, label = { Text(stringResource(R.string.memory_type)) })
                OutlinedTextField(value = weight, onValueChange = { weight = it }, label = { Text(stringResource(R.string.memory_weight)) })
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text(stringResource(R.string.memory_description)) })
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(type, weight.toFloatOrNull() ?: 1.0f, description)
            }) { Text(stringResource(R.string.memory_save)) }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.memory_cancel)) } }
    )
}

@Composable
fun LinkMemoryDialog(
    sourceNodeLabel: String,
    targetNodeLabel: String,
    onDismiss: () -> Unit,
    onLink: (type: String, weight: Float, description: String) -> Unit
) {
    var type by remember { mutableStateOf("related") }
    var weight by remember { mutableStateOf("1.0") }
    var description by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.memory_link_nodes, sourceNodeLabel, targetNodeLabel)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = type,
                    onValueChange = { type = it },
                    label = { Text(stringResource(R.string.memory_type)) }
                )
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it },
                    label = { Text(stringResource(R.string.memory_weight)) }
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.memory_description)) }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val w = weight.toFloatOrNull() ?: 1.0f
                    onLink(type, w, description)
                }
            ) { Text(stringResource(R.string.memory_create_link)) }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.memory_cancel)) } }
    )
}

@Composable
fun BatchDeleteConfirmDialog(
    selectedCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.confirm_delete)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.memory_delete_confirmation, selectedCount),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = stringResource(R.string.memory_delete_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(R.string.confirm_delete))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
