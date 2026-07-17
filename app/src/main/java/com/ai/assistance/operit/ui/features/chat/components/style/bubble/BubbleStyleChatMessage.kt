
package com.ai.assistance.operit.ui.features.chat.components.style.bubble

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.ChatMessageDisplayMode
import com.ai.assistance.operit.ui.features.chat.components.ChatMessageHeightMemory
import com.ai.assistance.operit.ui.features.chat.components.ImmersiveMessagePresentation
import com.ai.assistance.operit.ui.features.chat.components.style.cursor.SummaryMessageComposable
import com.ai.assistance.operit.util.WaifuMessageProcessor

/**
 * A composable function that renders chat messages in a bubble chat style.
 * Delegates to specialized composables based on message type.
 */
@Composable
fun BubbleStyleChatMessage(
    message: ChatMessage,
    userMessageColor: Color,
    aiMessageColor: Color,
    userTextColor: Color,
    aiTextColor: Color,
    systemMessageColor: Color,
    systemTextColor: Color,
    userMessageLiquidGlassEnabled: Boolean = false,
    userMessageWaterGlassEnabled: Boolean = false,
    aiMessageLiquidGlassEnabled: Boolean = false,
    aiMessageWaterGlassEnabled: Boolean = false,
    userBubbleImageStyle: BubbleImageStyleConfig? = null,
    aiBubbleImageStyle: BubbleImageStyleConfig? = null,
    bubbleUserRoundedCornersEnabled: Boolean = true,
    bubbleAiRoundedCornersEnabled: Boolean = true,
    bubbleUserContentPaddingLeft: Float = 12f,
    bubbleUserContentPaddingRight: Float = 12f,
    bubbleAiContentPaddingLeft: Float = 12f,
    bubbleAiContentPaddingRight: Float = 12f,
    isVisualGroupStart: Boolean = true,
    isVisualGroupEnd: Boolean = true,
    isCurrentlySpoken: Boolean = false,
    currentSpeechSegment: String? = null,
    initialThinkingExpanded: Boolean = false,
    allowExpandedThinkingFullHeight: Boolean = false,
    expandThinkToolsGroups: Boolean = false,
    forceShowThinkingProcess: Boolean = false,
    isHidden: Boolean = false,
    heightMemory: ChatMessageHeightMemory? = null,
    onDeleteMessage: ((Int) -> Unit)? = null,
    index: Int = -1,
    enableDialogs: Boolean = true,  // 新增参数：是否启用弹窗功能，默认启用
    onRoleAvatarLongPress: ((String) -> Unit)? = null,
    onEditSummary: ((ChatMessage) -> Unit)? = null,
) {
    when (message.sender) {
        "user" -> {
            BubbleUserMessageComposable(
                message = message,
                backgroundColor = userMessageColor,
                textColor = userTextColor,
                enableLiquidGlass = userMessageLiquidGlassEnabled,
                enableWaterGlass = userMessageWaterGlassEnabled,
                bubbleImageStyle = userBubbleImageStyle,
                bubbleRoundedCornersEnabled = bubbleUserRoundedCornersEnabled,
                bubbleContentPaddingLeft = bubbleUserContentPaddingLeft,
                bubbleContentPaddingRight = bubbleUserContentPaddingRight,
                enableDialogs = enableDialogs,
                isVisualGroupStart = isVisualGroupStart,
                isVisualGroupEnd = isVisualGroupEnd,
            )
        }
        "ai" -> {
            val fragments =
                remember(message.content, message.displayMode) {
                    if (message.displayMode == ChatMessageDisplayMode.IMMERSIVE_TURN) {
                        ImmersiveMessagePresentation.fragments(message.content)
                    } else {
                        listOf(message.content)
                    }
                }
            val renderBubble:
                @Composable (ChatMessage, Boolean, Boolean, Boolean, ChatMessageHeightMemory?) -> Unit =
                { fragmentMessage, fragmentStart, fragmentEnd, fragmentSpoken, fragmentHeightMemory ->
                    BubbleAiMessageComposable(
                        message = fragmentMessage,
                        backgroundColor = aiMessageColor,
                        textColor = aiTextColor,
                        enableLiquidGlass = aiMessageLiquidGlassEnabled,
                        enableWaterGlass = aiMessageWaterGlassEnabled,
                        bubbleImageStyle = aiBubbleImageStyle,
                        bubbleRoundedCornersEnabled = bubbleAiRoundedCornersEnabled,
                        bubbleContentPaddingLeft = bubbleAiContentPaddingLeft,
                        bubbleContentPaddingRight = bubbleAiContentPaddingRight,
                        initialThinkingExpanded = initialThinkingExpanded,
                        allowExpandedThinkingFullHeight = allowExpandedThinkingFullHeight,
                        expandThinkToolsGroups = expandThinkToolsGroups,
                        forceShowThinkingProcess = forceShowThinkingProcess,
                        isHidden = isHidden,
                        heightMemory = fragmentHeightMemory,
                        enableDialogs = enableDialogs,
                        onAvatarLongPressMention = onRoleAvatarLongPress,
                        isVisualGroupStart = fragmentStart,
                        isVisualGroupEnd = fragmentEnd,
                        isCurrentlySpoken = fragmentSpoken,
                    )
                }

            if (fragments.size <= 1) {
                renderBubble(
                    message,
                    isVisualGroupStart,
                    isVisualGroupEnd,
                    isCurrentlySpoken,
                    heightMemory,
                )
            } else {
                Column {
                    fragments.forEachIndexed { fragmentIndex, fragment ->
                        if (fragmentIndex > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        val fragmentSpoken =
                            !currentSpeechSegment.isNullOrBlank() &&
                                WaifuMessageProcessor.cleanContentForWaifu(fragment)
                                    .contains(currentSpeechSegment)
                        renderBubble(
                            message.copy(content = fragment, contentStream = null),
                            isVisualGroupStart && fragmentIndex == 0,
                            isVisualGroupEnd && fragmentIndex == fragments.lastIndex,
                            fragmentSpoken,
                            null,
                        )
                    }
                }
            }
        }
        "summary" -> {
            SummaryMessageComposable(
                message = message,
                backgroundColor = systemMessageColor.copy(alpha = 0.7f),
                textColor = systemTextColor,
                onDelete = {
                    if (index != -1) {
                        onDeleteMessage?.invoke(index)
                    }
                },
                enableDialog = enableDialogs,  // 传递弹窗启用状态
                onEdit = { editedMessage ->
                    onEditSummary?.invoke(editedMessage)
                }
            )
        }
    }
}
