package com.ai.assistance.operit.ui.features.settings.sections

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.settings.components.ColorSelectionItem
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class BubbleThemePreset(
    val id: String,
    @StringRes val titleRes: Int,
    val userBubbleColor: Int,
    val aiBubbleColor: Int,
    val userTextColor: Int,
    val aiTextColor: Int,
    val userCornerRadius: Float,
    val aiCornerRadius: Float,
    val userMaxWidthRatio: Float,
    val aiMaxWidthRatio: Float,
    val messageVerticalPadding: Float,
    val shadowElevation: Float,
    val showAvatar: Boolean = true,
    val wideLayout: Boolean = false,
)

private val bubbleThemePresets =
    listOf(
        BubbleThemePreset(
            id = "mira_blue",
            titleRes = R.string.bubble_preset_mira_blue,
            userBubbleColor = 0xFF1565C0.toInt(),
            aiBubbleColor = 0xFFF1F4F8.toInt(),
            userTextColor = 0xFFFFFFFF.toInt(),
            aiTextColor = 0xFF1C1C1E.toInt(),
            userCornerRadius = 18f,
            aiCornerRadius = 18f,
            userMaxWidthRatio = 0.80f,
            aiMaxWidthRatio = 0.84f,
            messageVerticalPadding = 4f,
            shadowElevation = 1f,
        ),
        BubbleThemePreset(
            id = "wechat_green",
            titleRes = R.string.bubble_preset_wechat_green,
            userBubbleColor = 0xFF95EC69.toInt(),
            aiBubbleColor = 0xFFFFFFFF.toInt(),
            userTextColor = 0xFF111111.toInt(),
            aiTextColor = 0xFF111111.toInt(),
            userCornerRadius = 7f,
            aiCornerRadius = 7f,
            userMaxWidthRatio = 0.78f,
            aiMaxWidthRatio = 0.84f,
            messageVerticalPadding = 4f,
            shadowElevation = 0f,
        ),
        BubbleThemePreset(
            id = "sakura",
            titleRes = R.string.bubble_preset_sakura,
            userBubbleColor = 0xFFF7CADA.toInt(),
            aiBubbleColor = 0xFFFFF4F7.toInt(),
            userTextColor = 0xFF4E2634.toInt(),
            aiTextColor = 0xFF4B2D38.toInt(),
            userCornerRadius = 22f,
            aiCornerRadius = 22f,
            userMaxWidthRatio = 0.82f,
            aiMaxWidthRatio = 0.86f,
            messageVerticalPadding = 5f,
            shadowElevation = 2f,
        ),
        BubbleThemePreset(
            id = "mint",
            titleRes = R.string.bubble_preset_mint,
            userBubbleColor = 0xFFC9EEE3.toInt(),
            aiBubbleColor = 0xFFF1FAF7.toInt(),
            userTextColor = 0xFF173A31.toInt(),
            aiTextColor = 0xFF173D34.toInt(),
            userCornerRadius = 18f,
            aiCornerRadius = 18f,
            userMaxWidthRatio = 0.78f,
            aiMaxWidthRatio = 0.84f,
            messageVerticalPadding = 4f,
            shadowElevation = 1f,
        ),
        BubbleThemePreset(
            id = "mist_purple",
            titleRes = R.string.bubble_preset_mist_purple,
            userBubbleColor = 0xFFDDD4F4.toInt(),
            aiBubbleColor = 0xFFF7F4FC.toInt(),
            userTextColor = 0xFF342C4B.toInt(),
            aiTextColor = 0xFF342E49.toInt(),
            userCornerRadius = 20f,
            aiCornerRadius = 20f,
            userMaxWidthRatio = 0.82f,
            aiMaxWidthRatio = 0.86f,
            messageVerticalPadding = 5f,
            shadowElevation = 2f,
        ),
        BubbleThemePreset(
            id = "monochrome",
            titleRes = R.string.bubble_preset_monochrome,
            userBubbleColor = 0xFF242426.toInt(),
            aiBubbleColor = 0xFFF2F2F3.toInt(),
            userTextColor = 0xFFFFFFFF.toInt(),
            aiTextColor = 0xFF202124.toInt(),
            userCornerRadius = 12f,
            aiCornerRadius = 12f,
            userMaxWidthRatio = 0.76f,
            aiMaxWidthRatio = 0.82f,
            messageVerticalPadding = 3f,
            shadowElevation = 0f,
            showAvatar = false,
        ),
    )

private fun BubbleThemePreset.matches(
    userBubbleColor: Int,
    aiBubbleColor: Int,
    userTextColor: Int,
    aiTextColor: Int,
    userCornerRadius: Float,
    aiCornerRadius: Float,
    userMaxWidthRatio: Float,
    aiMaxWidthRatio: Float,
    messageVerticalPadding: Float,
    shadowElevation: Float,
    showAvatar: Boolean,
    wideLayout: Boolean,
    usesCustomSurface: Boolean,
): Boolean =
    !usesCustomSurface &&
        this.userBubbleColor == userBubbleColor &&
        this.aiBubbleColor == aiBubbleColor &&
        this.userTextColor == userTextColor &&
        this.aiTextColor == aiTextColor &&
        abs(this.userCornerRadius - userCornerRadius) < 0.05f &&
        abs(this.aiCornerRadius - aiCornerRadius) < 0.05f &&
        abs(this.userMaxWidthRatio - userMaxWidthRatio) < 0.005f &&
        abs(this.aiMaxWidthRatio - aiMaxWidthRatio) < 0.005f &&
        abs(this.messageVerticalPadding - messageVerticalPadding) < 0.05f &&
        abs(this.shadowElevation - shadowElevation) < 0.05f &&
        this.showAvatar == showAvatar &&
        this.wideLayout == wideLayout

@Composable
internal fun ThemeSettingsBubblePersonalizationSection(
    cardColors: CardColors,
    userBubbleColor: Int,
    aiBubbleColor: Int,
    userTextColor: Int,
    aiTextColor: Int,
    userCornerRadius: Float,
    onUserCornerRadiusChange: (Float) -> Unit,
    onUserCornerRadiusChangeFinished: (Float) -> Unit,
    aiCornerRadius: Float,
    onAiCornerRadiusChange: (Float) -> Unit,
    onAiCornerRadiusChangeFinished: (Float) -> Unit,
    userMaxWidthRatio: Float,
    onUserMaxWidthRatioChange: (Float) -> Unit,
    onUserMaxWidthRatioChangeFinished: (Float) -> Unit,
    aiMaxWidthRatio: Float,
    onAiMaxWidthRatioChange: (Float) -> Unit,
    onAiMaxWidthRatioChangeFinished: (Float) -> Unit,
    messageVerticalPadding: Float,
    onMessageVerticalPaddingChange: (Float) -> Unit,
    onMessageVerticalPaddingChangeFinished: (Float) -> Unit,
    shadowElevation: Float,
    onShadowElevationChange: (Float) -> Unit,
    onShadowElevationChangeFinished: (Float) -> Unit,
    showAvatar: Boolean,
    wideLayout: Boolean,
    usesCustomSurface: Boolean,
    onShowColorPicker: (String) -> Unit,
    onApplyPreset: (BubbleThemePreset) -> Unit,
) {
    val activePreset =
        bubbleThemePresets.firstOrNull {
            it.matches(
                userBubbleColor = userBubbleColor,
                aiBubbleColor = aiBubbleColor,
                userTextColor = userTextColor,
                aiTextColor = aiTextColor,
                userCornerRadius = userCornerRadius,
                aiCornerRadius = aiCornerRadius,
                userMaxWidthRatio = userMaxWidthRatio,
                aiMaxWidthRatio = aiMaxWidthRatio,
                messageVerticalPadding = messageVerticalPadding,
                shadowElevation = shadowElevation,
                showAvatar = showAvatar,
                wideLayout = wideLayout,
                usesCustomSurface = usesCustomSurface,
            )
        }
    var advancedExpanded by rememberSaveable { mutableStateOf(false) }

    ThemeSettingsSectionTitle(
        title = stringResource(id = R.string.bubble_personalization_title),
        icon = Icons.Default.Palette,
    )

    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        colors = cardColors,
    ) {
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            Text(
                text = stringResource(id = R.string.bubble_personalization_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            ) {
                items(bubbleThemePresets, key = { it.id }) { preset ->
                    BubblePresetCard(
                        preset = preset,
                        selected = preset.id == activePreset?.id,
                        onClick = { onApplyPreset(preset) },
                    )
                }
            }

            Text(
                text =
                    activePreset?.let {
                        stringResource(
                            id = R.string.bubble_personalization_current_preset,
                            stringResource(id = it.titleRes),
                        )
                    } ?: stringResource(id = R.string.bubble_personalization_custom),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )

            HorizontalDivider()

            Text(
                text = stringResource(id = R.string.bubble_personalization_custom_colors),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
            )
            Text(
                text = stringResource(id = R.string.bubble_personalization_custom_colors_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ColorSelectionItem(
                    title = stringResource(id = R.string.chat_style_bubble_user_color),
                    color = Color(userBubbleColor),
                    modifier = Modifier.weight(1f),
                    onClick = { onShowColorPicker("bubbleUserBubble") },
                )
                ColorSelectionItem(
                    title = stringResource(id = R.string.chat_style_bubble_ai_color),
                    color = Color(aiBubbleColor),
                    modifier = Modifier.weight(1f),
                    onClick = { onShowColorPicker("bubbleAiBubble") },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ColorSelectionItem(
                    title = stringResource(id = R.string.chat_style_bubble_user_text_color),
                    color = Color(userTextColor),
                    modifier = Modifier.weight(1f),
                    onClick = { onShowColorPicker("bubbleUserText") },
                )
                ColorSelectionItem(
                    title = stringResource(id = R.string.chat_style_bubble_ai_text_color),
                    color = Color(aiTextColor),
                    modifier = Modifier.weight(1f),
                    onClick = { onShowColorPicker("bubbleAiText") },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(top = 12.dp))

            TextButton(
                onClick = { advancedExpanded = !advancedExpanded },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(id = R.string.bubble_personalization_advanced),
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (advancedExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                )
            }

            AnimatedVisibility(visible = advancedExpanded) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    BubbleAppearanceSlider(
                        title = stringResource(id = R.string.bubble_user_corner_radius),
                        valueLabel = stringResource(id = R.string.bubble_value_dp, userCornerRadius.roundToInt()),
                        value = userCornerRadius,
                        valueRange = 4f..28f,
                        steps = 23,
                        onValueChange = onUserCornerRadiusChange,
                        onValueChangeFinished = { onUserCornerRadiusChangeFinished(userCornerRadius) },
                    )
                    BubbleAppearanceSlider(
                        title = stringResource(id = R.string.bubble_ai_corner_radius),
                        valueLabel = stringResource(id = R.string.bubble_value_dp, aiCornerRadius.roundToInt()),
                        value = aiCornerRadius,
                        valueRange = 4f..28f,
                        steps = 23,
                        onValueChange = onAiCornerRadiusChange,
                        onValueChangeFinished = { onAiCornerRadiusChangeFinished(aiCornerRadius) },
                    )
                    BubbleAppearanceSlider(
                        title = stringResource(id = R.string.bubble_user_max_width),
                        valueLabel = stringResource(id = R.string.bubble_value_percent, (userMaxWidthRatio * 100).roundToInt()),
                        value = userMaxWidthRatio,
                        valueRange = 0.60f..0.96f,
                        steps = 17,
                        onValueChange = onUserMaxWidthRatioChange,
                        onValueChangeFinished = { onUserMaxWidthRatioChangeFinished(userMaxWidthRatio) },
                    )
                    BubbleAppearanceSlider(
                        title = stringResource(id = R.string.bubble_ai_max_width),
                        valueLabel = stringResource(id = R.string.bubble_value_percent, (aiMaxWidthRatio * 100).roundToInt()),
                        value = aiMaxWidthRatio,
                        valueRange = 0.60f..0.96f,
                        steps = 17,
                        onValueChange = onAiMaxWidthRatioChange,
                        onValueChangeFinished = { onAiMaxWidthRatioChangeFinished(aiMaxWidthRatio) },
                    )
                    BubbleAppearanceSlider(
                        title = stringResource(id = R.string.bubble_message_spacing),
                        valueLabel = stringResource(id = R.string.bubble_value_dp, messageVerticalPadding.roundToInt()),
                        value = messageVerticalPadding,
                        valueRange = 0f..10f,
                        steps = 9,
                        onValueChange = onMessageVerticalPaddingChange,
                        onValueChangeFinished = { onMessageVerticalPaddingChangeFinished(messageVerticalPadding) },
                    )
                    BubbleAppearanceSlider(
                        title = stringResource(id = R.string.bubble_shadow_strength),
                        valueLabel = stringResource(id = R.string.bubble_value_dp, shadowElevation.roundToInt()),
                        value = shadowElevation,
                        valueRange = 0f..8f,
                        steps = 7,
                        onValueChange = onShadowElevationChange,
                        onValueChangeFinished = { onShadowElevationChangeFinished(shadowElevation) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BubblePresetCard(
    preset: BubbleThemePreset,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.width(112.dp).height(96.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (selected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
                    },
            ),
        border =
            BorderStroke(
                width = if (selected) 1.5.dp else 1.dp,
                color =
                    if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
            ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Card(
                        modifier = Modifier.width(58.dp).height(19.dp).align(Alignment.End),
                        shape = RoundedCornerShape(preset.userCornerRadius.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(preset.userBubbleColor)),
                    ) {}
                    Card(
                        modifier = Modifier.width(72.dp).height(19.dp),
                        shape = RoundedCornerShape(preset.aiCornerRadius.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(preset.aiBubbleColor)),
                        border = BorderStroke(0.5.dp, Color.Black.copy(alpha = 0.06f)),
                    ) {}
                }
                if (selected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Text(
                text = stringResource(id = preset.titleRes),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun BubbleAppearanceSlider(
    title: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            steps = steps,
        )
    }
}
