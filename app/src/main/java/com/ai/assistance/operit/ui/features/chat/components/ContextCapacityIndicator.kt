package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ContextWindowUsage
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ContextCapacityIndicator(
    currentTokens: Long,
    maxTokens: Long,
    projectedTokens: Long,
    usage: ContextWindowUsage,
    onRequestRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val safeCurrent = currentTokens.coerceAtLeast(0L)
    val safeMaximum = maxTokens.coerceAtLeast(0L)
    val progress =
        if (safeMaximum > 0L) {
            (safeCurrent.toFloat() / safeMaximum.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
    val progressColor =
        when {
            progress >= 0.9f -> MaterialTheme.colorScheme.error
            progress >= 0.7f -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.primary
        }
    val ringTrackColor = MaterialTheme.colorScheme.outlineVariant

    Box(modifier = modifier.size(48.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier =
                Modifier
                    .size(48.dp)
                    .semantics {
                        contentDescription = context.getString(R.string.context_capacity)
                    }
                    .clickable {
                        expanded = true
                        onRequestRefresh()
                    },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(30.dp)) {
                val strokeWidth = 3.dp.toPx()
                val inset = strokeWidth / 2f
                val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                drawArc(
                    color = ringTrackColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = strokeWidth),
                )
                if (progress > 0f) {
                    drawArc(
                        color = progressColor,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                }
                drawCircle(
                    color = progressColor,
                    radius = 4.dp.toPx(),
                    center = center,
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 280.dp, max = 320.dp),
            offset = DpOffset((-8).dp, (-4).dp),
            properties = PopupProperties(focusable = true),
            shape = RoundedCornerShape(8.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 2.dp,
            shadowElevation = 8.dp,
        ) {
            ContextCapacityPopupContent(
                currentTokens = safeCurrent,
                maxTokens = safeMaximum,
                projectedTokens = projectedTokens.coerceAtLeast(safeCurrent),
                usage = usage,
                progress = progress,
                progressColor = progressColor,
            )
        }
    }
}

@Composable
fun ContextCapacityDialog(
    currentTokens: Long,
    maxTokens: Long,
    usage: ContextWindowUsage,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val safeCurrent = currentTokens.coerceAtLeast(0L)
    val safeMaximum = maxTokens.coerceAtLeast(0L)
    val progress =
        if (safeMaximum > 0L) {
            (safeCurrent.toFloat() / safeMaximum.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
    val progressColor =
        when {
            progress >= 0.9f -> MaterialTheme.colorScheme.error
            progress >= 0.7f -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.primary
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(context.getString(android.R.string.ok))
            }
        },
        text = {
            ContextCapacityPopupContent(
                currentTokens = safeCurrent,
                maxTokens = safeMaximum,
                projectedTokens = safeCurrent,
                usage = usage,
                progress = progress,
                progressColor = progressColor,
            )
        },
        shape = RoundedCornerShape(8.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

@Composable
private fun ContextCapacityPopupContent(
    currentTokens: Long,
    maxTokens: Long,
    projectedTokens: Long,
    usage: ContextWindowUsage,
    progress: Float,
    progressColor: Color,
) {
    val context = LocalContext.current
    val hasMatchingBreakdown = usage.hasBreakdown && usage.totalTokens == currentTokens
    val percentage = if (maxTokens > 0L) progress * 100f else 0f

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = context.getString(R.string.context_capacity),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text =
                    if (maxTokens > 0L) {
                        context.getString(
                            R.string.context_capacity_summary,
                            formatCompactTokenCount(currentTokens),
                            formatCompactTokenCount(maxTokens),
                            percentage,
                        )
                    } else {
                        formatCompactTokenCount(currentTokens)
                    },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = progressColor,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            strokeCap = StrokeCap.Round,
        )

        if (projectedTokens > currentTokens) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text =
                    context.getString(
                        R.string.context_capacity_projected,
                        formatCompactTokenCount(projectedTokens),
                    ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
        if (hasMatchingBreakdown) {
            val rows =
                listOf(
                    Triple(
                        R.string.context_capacity_messages,
                        usage.messageTokens,
                        MaterialTheme.colorScheme.primary,
                    ),
                    Triple(
                        R.string.context_capacity_system_tools,
                        usage.systemToolTokens,
                        MaterialTheme.colorScheme.tertiary,
                    ),
                    Triple(
                        R.string.context_capacity_skills,
                        usage.skillTokens,
                        if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
                            Color(0xFFF3C55D)
                        } else {
                            Color(0xFF9A6A00)
                        },
                    ),
                    Triple(
                        R.string.context_capacity_system_prompt,
                        usage.systemPromptTokens,
                        MaterialTheme.colorScheme.secondary,
                    ),
                    Triple(
                        R.string.context_capacity_other,
                        usage.otherTokens,
                        MaterialTheme.colorScheme.outline,
                    ),
                )
            rows.forEach { (labelRes, tokens, color) ->
                ContextUsageRow(
                    label = context.getString(labelRes),
                    tokens = tokens,
                    totalTokens = usage.totalTokens,
                    color = color,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = context.getString(R.string.context_capacity_breakdown_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = context.getString(R.string.context_capacity_calculating),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ContextUsageRow(
    label: String,
    tokens: Long,
    totalTokens: Long,
    color: Color,
) {
    val percent =
        if (totalTokens > 0L) {
            tokens.toDouble() * 100.0 / totalTokens.toDouble()
        } else {
            0.0
        }
    Row(
        modifier = Modifier.fillMaxWidth().height(36.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(9.dp).background(color, CircleShape))
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = String.format(Locale.getDefault(), "%.1f%%", percent),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

internal fun formatCompactTokenCount(tokens: Long): String {
    val safeTokens = tokens.coerceAtLeast(0L)
    val locale = Locale.getDefault()
    return when {
        locale.language == Locale.CHINESE.language && safeTokens >= 10_000L ->
            String.format(locale, "%.1f万", safeTokens / 10_000.0)
        safeTokens >= 1_000_000L -> String.format(locale, "%.1fM", safeTokens / 1_000_000.0)
        safeTokens >= 1_000L -> String.format(locale, "%.1fK", safeTokens / 1_000.0)
        else -> NumberFormat.getIntegerInstance(locale).format(safeTokens)
    }
}
