package com.ai.assistance.operit.ui.features.chat.components

import android.os.SystemClock
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.AiReference
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.ChatMessageDisplayMode
import com.ai.assistance.operit.data.model.ChatMessageLocatorPreview
import com.ai.assistance.operit.data.preferences.UserPreferencesManager

import androidx.compose.ui.window.PopupProperties

import androidx.compose.material.icons.filled.AutoFixHigh

import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.ui.draw.alpha
import com.ai.assistance.operit.api.chat.llmprovider.MediaLinkParser
import com.ai.assistance.operit.ui.features.chat.components.style.cursor.CursorStyleChatMessage
import com.ai.assistance.operit.ui.features.chat.components.style.bubble.BubbleImageStyleConfig
import com.ai.assistance.operit.ui.features.chat.components.style.bubble.BubbleStyleChatMessage
import com.ai.assistance.operit.util.ChatMarkupRegex
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * 清理消息中的XML标签，保留Markdown格式和纯文本内容
 */
private fun cleanXmlTags(content: String): String {
    return content
        // 移除状态标签
        .replace(ChatMarkupRegex.statusTag, "")
        .replace(ChatMarkupRegex.statusSelfClosingTag, "")
        // 移除思考标签（包括 <think> 和 <thinking>）
        .replace(ChatMarkupRegex.thinkTag, "")
        .replace(ChatMarkupRegex.thinkSelfClosingTag, "")
        // 移除搜索来源标签
        .replace(ChatMarkupRegex.searchTag, "")
        .replace(ChatMarkupRegex.searchSelfClosingTag, "")
        // 移除工具标签
        .replace(ChatMarkupRegex.toolTag, "")
        .replace(ChatMarkupRegex.toolSelfClosingTag, "")
        // 移除工具结果标签
        .replace(ChatMarkupRegex.toolResultTag, "")
        .replace(ChatMarkupRegex.toolResultSelfClosingTag, "")
        // 移除emotion标签
        .replace(ChatMarkupRegex.emotionTag, "")
        // 移除附件与工作区上下文
        .replace(ChatMarkupRegex.workspaceAttachmentTag, "")
        .replace(ChatMarkupRegex.attachmentTag, "")
        .replace(ChatMarkupRegex.attachmentSelfClosingTag, "")
        // 移除多媒体链接标签
        .let(MediaLinkParser::removeImageLinks)
        .let(MediaLinkParser::removeMediaLinks)
        .trim()
}

private fun isHiddenUserPlaceholder(message: ChatMessage): Boolean {
    return message.sender == "user" &&
        message.displayMode == ChatMessageDisplayMode.HIDDEN_PLACEHOLDER
}

internal fun shouldShowStarterActions(messages: List<ChatMessage>): Boolean {
    var assistantCount = 0
    for (message in messages) {
        when (message.sender) {
            "user" -> return false
            "ai" -> {
                assistantCount += 1
                if (assistantCount > 1) return false
            }
        }
    }
    return assistantCount == 1
}

internal fun shouldRenderChatTimelineMessage(
    message: ChatMessage,
    hideAsLoadingPlaceholder: Boolean,
): Boolean =
    !hideAsLoadingPlaceholder &&
        !(message.sender == "ai" && !ChatTimelinePolicy.isTimelineVisible(message))

enum class ChatStyle {
    CURSOR,
    BUBBLE
}

@Composable
fun ChatArea(
    chatHistory: List<ChatMessage>,
    currentChatId: String,
    onStarterPromptSelected: (String) -> Unit = {},
    scrollState: LazyListState,
    aiReferences: List<AiReference> = emptyList(),
    isLoading: Boolean,
    enableDialogs: Boolean = true,
    userMessageColor: Color,
    aiMessageColor: Color,
    userTextColor: Color,
    aiTextColor: Color,
    systemMessageColor: Color,
    systemTextColor: Color,
    thinkingBackgroundColor: Color,
    thinkingTextColor: Color,
    hasBackgroundImage: Boolean = false,
    modifier: Modifier = Modifier,
    onSelectMessageToEdit: ((Int, ChatMessage, String) -> Unit)? = null,
    onCopyMessage: ((ChatMessage) -> Unit)? = null,
    onDeleteMessage: ((Int) -> Unit)? = null,
    onDeleteCurrentMessageVariant: ((Int) -> Unit)? = null,
    onDeleteMessagesFrom: ((Int) -> Unit)? = null,
    onRollbackToMessage: ((Int) -> Unit)? = null, // 回滚到指定消息的回调
    onRegenerateMessage: ((Int) -> Unit)? = null,
    onSwitchMessageVariant: ((Int, Int) -> Unit)? = null,
    onSpeakMessage: ((ChatMessage) -> Unit)? = null, // 添加朗读回调参数
    currentSpeechSegment: String? = null,
    onAutoReadMessage: ((String) -> Unit)? = null, // 添加自动朗读回调参数
    onReplyToMessage: ((ChatMessage) -> Unit)? = null, // 添加回复回调参数
    onToggleFavoriteMessage: ((Long, Boolean) -> Unit)? = null,
    onCreateBranch: ((Long) -> Unit)? = null, // 添加创建分支回调参数
    onInsertSummary: ((ChatMessage) -> Unit)? = null, // 添加插入总结回调参数
    onMentionRoleFromAvatar: ((String) -> Unit)? = null, // 长按角色头像提及
    autoScrollToBottom: Boolean = true,
    onAutoScrollToBottomChange: ((Boolean) -> Unit)? = null,
    hasOlderDisplayHistory: Boolean = false,
    hasNewerDisplayHistory: Boolean = false,
    isLoadingDisplayWindow: Boolean = false,
    onLoadOlderDisplayWindow: (() -> Unit)? = null,
    onLoadNewerDisplayWindow: (() -> Unit)? = null,
    onShowLatestDisplayWindow: (() -> Unit)? = null,
    loadMessageLocatorEntries: (suspend (String, String) -> List<ChatMessageLocatorPreview>)? = null,
    onRevealMessageForLocator: (suspend (Long) -> Boolean)? = null,
    topPadding: Dp = 0.dp,
    bottomPadding: Dp = 0.dp,
    chatStyle: ChatStyle = ChatStyle.CURSOR, // 新增参数，默认为CURSOR风格
    cursorUserBubbleLiquidGlass: Boolean = false,
    cursorUserBubbleWaterGlass: Boolean = false,
    bubbleUserBubbleLiquidGlass: Boolean = false,
    bubbleUserBubbleWaterGlass: Boolean = false,
    bubbleAiBubbleLiquidGlass: Boolean = false,
    bubbleAiBubbleWaterGlass: Boolean = false,
    isMultiSelectMode: Boolean = false, // 是否处于多选模式
    selectedMessageIndices: Set<Int> = emptySet(), // 已选中的消息索引集合
    onToggleMultiSelectMode: ((Int?) -> Unit)? = null, // 切换多选模式的回调，可传入要初始选中的消息索引
    onToggleMessageSelection: ((Int) -> Unit)? = null, // 切换消息选中状态的回调
    horizontalPadding: Dp = 16.dp, // 水平内边距，可自定义
    bubbleUserImageStyle: BubbleImageStyleConfig? = null,
    bubbleAiImageStyle: BubbleImageStyleConfig? = null,
    bubbleUserRoundedCornersEnabled: Boolean = true,
    bubbleAiRoundedCornersEnabled: Boolean = true,
    bubbleUserContentPaddingLeft: Float = 12f,
    bubbleUserContentPaddingRight: Float = 12f,
    bubbleAiContentPaddingLeft: Float = 12f,
    bubbleAiContentPaddingRight: Float = 12f,
    showChatFloatingDotsAnimation: Boolean = true,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val preferencesManager = remember { UserPreferencesManager.getInstance(context) }
    val showMessageTokenStats by
        preferencesManager.showMessageTokenStats.collectAsState(initial = false)
    val showMessageTimingStats by
        preferencesManager.showMessageTimingStats.collectAsState(initial = false)
    val showMessageTimestamp by
        preferencesManager.showMessageTimestamp.collectAsState(initial = true)
    val density = LocalDensity.current
    val isImeVisible = WindowInsets.ime.getBottom(density) > 0
    var pendingJumpToMessageTimestamp by remember(currentChatId) { mutableStateOf<Long?>(null) }
    val lastMessage = chatHistory.lastOrNull()
    var hasLastAiMessageStartedStreaming by remember(currentChatId, lastMessage?.timestamp) {
        mutableStateOf(lastMessage?.run { sender == "ai" && content.isNotBlank() } == true)
    }
    val currentAutoScrollToBottom by rememberUpdatedState(autoScrollToBottom)
    val currentHasNewerDisplayHistory by rememberUpdatedState(hasNewerDisplayHistory)

    val messagesCount = chatHistory.size
    LaunchedEffect(currentChatId, chatHistory.isEmpty()) {
        if (chatHistory.isEmpty()) {
            pendingJumpToMessageTimestamp = null
        }
    }

    LaunchedEffect(
        currentChatId,
        lastMessage?.timestamp,
        lastMessage?.content,
        lastMessage?.contentStream,
    ) {
        val lastAiMessageHasStaticContent =
            lastMessage?.let {
                it.sender == "ai" && ChatTimelinePolicy.isTimelineVisible(it)
            } == true
        hasLastAiMessageStartedStreaming = lastAiMessageHasStaticContent

        val shouldAwaitFirstChunk =
            lastMessage?.let {
                it.sender == "ai" &&
                    !ChatTimelinePolicy.isTimelineVisible(it) &&
                    it.contentStream != null
            } == true
        val stream = lastMessage?.contentStream

        if (!lastAiMessageHasStaticContent && shouldAwaitFirstChunk && stream != null) {
            var lastStreamScrollAt = 0L
            stream.collect { chunk ->
                val now = SystemClock.uptimeMillis()
                if (
                    chunk.isNotEmpty() &&
                        currentAutoScrollToBottom &&
                        !currentHasNewerDisplayHistory &&
                        now - lastStreamScrollAt >= 100L
                ) {
                    scrollState.scrollToEnd()
                    lastStreamScrollAt = now
                }
            }
        }
    }

    val isLatestMessageVisible = messagesCount > 0 && !hasNewerDisplayHistory
    val showLoadingIndicator =
        isLatestMessageVisible &&
            isLoading &&
            (
                lastMessage?.sender == "user" ||
                    lastMessage?.let {
                        it.sender == "ai" &&
                            !ChatTimelinePolicy.isTimelineVisible(it) &&
                            !hasLastAiMessageStartedStreaming
                    } == true
            )
    val shouldHideLastAiMessage =
        isLatestMessageVisible &&
            showLoadingIndicator &&
            chatStyle == ChatStyle.BUBBLE &&
            lastMessage?.sender == "ai"
    val showStarterActions =
        !isLoading &&
            !isImeVisible &&
            !hasOlderDisplayHistory &&
            !hasNewerDisplayHistory &&
            shouldShowStarterActions(chatHistory)
    val showEmptyStarterActions = chatHistory.isEmpty() && !isLoading && !isImeVisible
    // The edge spacers reserve resting room for floating chrome, but scroll away with the
    // timeline so messages can pass behind the header and composer edge fades.
    val leadingItemCount = 1 + if (hasOlderDisplayHistory) 1 else 0
    val timelineItemCount =
        leadingItemCount +
            chatHistory.size +
            (if (hasNewerDisplayHistory) 1 else 0) +
            (if (showLoadingIndicator) 1 else 0) +
            1

    // 新消息和加载行采用一次平滑滚动；流式内容增长走无动画定位，避免每个 token 重启动画。
    LaunchedEffect(
        currentChatId,
        autoScrollToBottom,
        messagesCount,
        lastMessage?.timestamp,
        showLoadingIndicator,
        hasNewerDisplayHistory,
        isLoadingDisplayWindow,
        timelineItemCount,
    ) {
        if (!autoScrollToBottom) return@LaunchedEffect
        if (
            hasNewerDisplayHistory &&
                !isLoadingDisplayWindow &&
                onShowLatestDisplayWindow != null
        ) {
            onShowLatestDisplayWindow.invoke()
            return@LaunchedEffect
        }
        if (timelineItemCount > 0) {
            scrollState.animateScrollToEnd()
        }
    }

    var observedLastMessageLength by
        remember(currentChatId, lastMessage?.timestamp) {
            mutableStateOf(lastMessage?.content?.length ?: 0)
        }
    LaunchedEffect(lastMessage?.content, autoScrollToBottom, hasNewerDisplayHistory) {
        val currentLength = lastMessage?.content?.length ?: 0
        val contentChanged = currentLength != observedLastMessageLength
        observedLastMessageLength = currentLength
        if (
            contentChanged &&
                autoScrollToBottom &&
                !hasNewerDisplayHistory &&
                timelineItemCount > 0
        ) {
            scrollState.scrollToEnd()
        }
    }

    LaunchedEffect(
        pendingJumpToMessageTimestamp,
        messagesCount,
        chatHistory.firstOrNull()?.timestamp,
        chatHistory.lastOrNull()?.timestamp,
        leadingItemCount,
        timelineItemCount,
    ) {
        val targetTimestamp = pendingJumpToMessageTimestamp ?: return@LaunchedEffect
        val targetIndex = chatHistory.indexOfFirst { it.timestamp == targetTimestamp }
        if (targetIndex < 0) return@LaunchedEffect

        val isActualLatestMessage = targetIndex == messagesCount - 1 && !hasNewerDisplayHistory
        onAutoScrollToBottomChange?.invoke(isActualLatestMessage)
        if (isActualLatestMessage) {
            scrollState.animateScrollToEnd()
        } else {
            scrollState.animateScrollToItem(leadingItemCount + targetIndex)
        }
        pendingJumpToMessageTimestamp = null
    }

    Box(
        modifier = modifier.background(Color.Transparent),
    ) {
        if (showEmptyStarterActions) {
            MateEmptyConversation(
                onStarterPromptSelected = onStarterPromptSelected,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(
                            start = horizontalPadding,
                            end = horizontalPadding,
                            top = topPadding + 12.dp,
                            bottom = bottomPadding + 12.dp,
                        ),
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().background(Color.Transparent),
            state = scrollState,
            contentPadding =
                PaddingValues(
                    start = horizontalPadding,
                    end = horizontalPadding,
                ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item(key = "chat:$currentChatId:timeline-start") {
                Spacer(modifier = Modifier.height(topPadding + 12.dp))
            }

            if (hasOlderDisplayHistory) {
                item(key = "chat:$currentChatId:load-older-history") {
                    Text(
                        text = stringResource(id = R.string.load_more_history),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onAutoScrollToBottomChange?.invoke(false)
                                    if (!isLoadingDisplayWindow) {
                                        onLoadOlderDisplayWindow?.invoke()
                                    }
                                }
                                .padding(vertical = 16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            itemsIndexed(
                items = chatHistory,
                key = { _, message ->
                    "chat:$currentChatId:message:${message.timestamp}"
                },
            ) { actualIndex, message ->
                val startsTimeBlock =
                    showMessageTimestamp &&
                        ChatTimelinePolicy.shouldShowSeparatorAt(chatHistory, actualIndex)
                val nextStartsTimeBlock =
                    showMessageTimestamp &&
                        ChatTimelinePolicy.shouldShowSeparatorAt(chatHistory, actualIndex + 1)
                val localSeparatorIndices =
                    buildSet {
                        if (startsTimeBlock) add(actualIndex)
                        if (nextStartsTimeBlock) add(actualIndex + 1)
                    }
                val visualGroupPosition =
                    ChatMessageVisualGrouping.position(
                        messages = chatHistory,
                        index = actualIndex,
                        separatorIndices = localSeparatorIndices,
                    )
                val isLastAiMessage = actualIndex == messagesCount - 1 && message.sender == "ai"
                val hideAsLoadingPlaceholder = shouldHideLastAiMessage && isLastAiMessage
                if (shouldRenderChatTimelineMessage(message, hideAsLoadingPlaceholder)) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    top =
                                        if (
                                            visualGroupPosition.isStart &&
                                                !startsTimeBlock
                                        ) {
                                            6.dp
                                        } else {
                                            0.dp
                                        },
                                ),
                    ) {
                        if (startsTimeBlock) {
                            ChatTimeSeparator(
                                timestamp = ChatTimelinePolicy.activityAt(message),
                            )
                        }
                        MessageItem(
                            index = actualIndex,
                            message = message,
                            enableDialogs = enableDialogs,
                            userMessageColor = userMessageColor,
                            aiMessageColor = aiMessageColor,
                            userTextColor = userTextColor,
                            aiTextColor = aiTextColor,
                            systemMessageColor = systemMessageColor,
                            systemTextColor = systemTextColor,
                            thinkingBackgroundColor = thinkingBackgroundColor,
                            thinkingTextColor = thinkingTextColor,
                            onSelectMessageToEdit = onSelectMessageToEdit,
                            onCopyMessage = onCopyMessage,
                            onDeleteMessage = onDeleteMessage,
                            onDeleteCurrentMessageVariant = onDeleteCurrentMessageVariant,
                            onDeleteMessagesFrom = onDeleteMessagesFrom,
                            onRollbackToMessage = onRollbackToMessage,
                            onRegenerateMessage = onRegenerateMessage,
                            onSwitchMessageVariant = onSwitchMessageVariant,
                            onSpeakMessage = onSpeakMessage,
                            currentSpeechSegment = currentSpeechSegment,
                            onReplyToMessage = onReplyToMessage,
                            onToggleFavoriteMessage = onToggleFavoriteMessage,
                            onCreateBranch = onCreateBranch,
                            onInsertSummary = onInsertSummary,
                            onMentionRoleFromAvatar = onMentionRoleFromAvatar,
                            chatStyle = chatStyle,
                            showMessageTokenStats = showMessageTokenStats,
                            showMessageTimingStats = showMessageTimingStats,
                            cursorUserBubbleLiquidGlass = cursorUserBubbleLiquidGlass,
                            cursorUserBubbleWaterGlass = cursorUserBubbleWaterGlass,
                            bubbleUserBubbleLiquidGlass = bubbleUserBubbleLiquidGlass,
                            bubbleUserBubbleWaterGlass = bubbleUserBubbleWaterGlass,
                            bubbleAiBubbleLiquidGlass = bubbleAiBubbleLiquidGlass,
                            bubbleAiBubbleWaterGlass = bubbleAiBubbleWaterGlass,
                            isHidden = false,
                            isMultiSelectMode = isMultiSelectMode,
                            isSelected = selectedMessageIndices.contains(actualIndex),
                            onToggleSelection = { onToggleMessageSelection?.invoke(actualIndex) },
                            onToggleMultiSelectMode = onToggleMultiSelectMode,
                            messageIndex = actualIndex,
                            bubbleUserImageStyle = bubbleUserImageStyle,
                            bubbleAiImageStyle = bubbleAiImageStyle,
                            bubbleUserRoundedCornersEnabled = bubbleUserRoundedCornersEnabled,
                            bubbleAiRoundedCornersEnabled = bubbleAiRoundedCornersEnabled,
                            bubbleUserContentPaddingLeft = bubbleUserContentPaddingLeft,
                            bubbleUserContentPaddingRight = bubbleUserContentPaddingRight,
                            bubbleAiContentPaddingLeft = bubbleAiContentPaddingLeft,
                            bubbleAiContentPaddingRight = bubbleAiContentPaddingRight,
                            isVisualGroupStart = visualGroupPosition.isStart,
                            isVisualGroupEnd = visualGroupPosition.isEnd,
                        )
                    }
                }
            }

            if (hasNewerDisplayHistory) {
                item(key = "chat:$currentChatId:load-newer-history") {
                    Text(
                        text = stringResource(id = R.string.load_newer_history),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (!isLoadingDisplayWindow) {
                                        onLoadNewerDisplayWindow?.invoke()
                                    }
                                }
                                .padding(vertical = 16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            if (showLoadingIndicator) {
                item(key = "chat:$currentChatId:companion-loading") {
                    if (chatStyle == ChatStyle.BUBBLE) {
                        MateLoadingMessageRow(
                            aiMessageColor = aiMessageColor,
                            aiTextColor = aiTextColor,
                            animateDots = showChatFloatingDotsAnimation,
                        )
                    } else {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp)
                                    .padding(vertical = 8.dp),
                        ) {
                            LoadingDotsIndicator(aiTextColor)
                        }
                    }
                }
            }

            if (showStarterActions) {
                item(key = "chat:$currentChatId:starter-actions") {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp, bottom = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        MateStarterActions(
                            onStarterPromptSelected = onStarterPromptSelected,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 420.dp),
                        )
                    }
                }
            }

            item(key = "chat:$currentChatId:timeline-end") {
                Spacer(modifier = Modifier.height(bottomPadding + 12.dp))
            }
        }

        ChatScrollNavigator(
            chatHistory = chatHistory,
            currentChatId = currentChatId,
            scrollState = scrollState,
            messageItemStartIndex = leadingItemCount,
            visibleMessageCount = chatHistory.size,
            loadLocatorEntries = loadMessageLocatorEntries,
            onToggleFavoriteMessage = onToggleFavoriteMessage,
            onJumpToMessageTimestamp = { targetTimestamp ->
                pendingJumpToMessageTimestamp = targetTimestamp
                val targetIndex = chatHistory.indexOfFirst { it.timestamp == targetTimestamp }
                if (targetIndex >= 0) {
                    val isActualLatestMessage =
                        targetIndex == messagesCount - 1 && !hasNewerDisplayHistory
                    onAutoScrollToBottomChange?.invoke(isActualLatestMessage)
                } else if (onRevealMessageForLocator != null) {
                    onAutoScrollToBottomChange?.invoke(false)
                    coroutineScope.launch {
                        val didReveal = onRevealMessageForLocator.invoke(targetTimestamp)
                        if (
                            !didReveal &&
                            pendingJumpToMessageTimestamp == targetTimestamp &&
                            chatHistory.none { it.timestamp == targetTimestamp }
                        ) {
                            pendingJumpToMessageTimestamp = null
                        }
                    }
                } else {
                    pendingJumpToMessageTimestamp = null
                }
            },
            onJumpToMessage = { targetIndex ->
                chatHistory.getOrNull(targetIndex)?.let { targetMessage ->
                    val isActualLatestMessage =
                        targetIndex == messagesCount - 1 && !hasNewerDisplayHistory
                    onAutoScrollToBottomChange?.invoke(isActualLatestMessage)
                    pendingJumpToMessageTimestamp = targetMessage.timestamp
                }
            },
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 10.dp),
        )

        key(currentChatId) {
            ScrollToBottomButton(
                scrollState = scrollState,
                coroutineScope = coroutineScope,
                autoScrollToBottom = autoScrollToBottom,
                hasNewerDisplayHistory = hasNewerDisplayHistory,
                onRequestLatestMessages = onShowLatestDisplayWindow,
                onAutoScrollToBottomChange = { enabled ->
                    onAutoScrollToBottomChange?.invoke(enabled)
                },
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = bottomPadding + 12.dp),
            )
        }
    }
}

@Composable
private fun MateLoadingMessageRow(
    aiMessageColor: Color,
    aiTextColor: Color,
    animateDots: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            modifier = Modifier.size(32.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.AutoFixHigh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(0.84f).heightIn(min = 40.dp),
            shape = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
            color = aiMessageColor,
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (animateDots) {
                    LoadingDotsIndicator(aiTextColor)
                } else {
                    Text(
                        text = "...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = aiTextColor,
                    )
                }
            }
        }
    }
}

@Composable
private fun MateEmptyConversation(
    onStarterPromptSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.BottomCenter,
    ) {
        MateStarterActions(
            onStarterPromptSelected = onStarterPromptSelected,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .padding(bottom = 24.dp),
        )
    }
}

@Composable
private fun MateStarterActions(
    onStarterPromptSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val starterItems =
        listOf(
            Triple(
                Icons.AutoMirrored.Filled.Chat,
                stringResource(R.string.mate_chat_starter_today),
                stringResource(R.string.mate_chat_starter_today_prompt),
            ),
            Triple(
                Icons.Default.Favorite,
                stringResource(R.string.mate_chat_starter_relax),
                stringResource(R.string.mate_chat_starter_relax_prompt),
            ),
            Triple(
                Icons.Default.Lightbulb,
                stringResource(R.string.mate_chat_starter_plan),
                stringResource(R.string.mate_chat_starter_plan_prompt),
            ),
        )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        starterItems.forEach { (icon, label, prompt) ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onStarterPromptSelected(prompt) }
                        .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** 单个消息项组件 将消息渲染逻辑提取到单独的组件，减少重组范围 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageItem(
    index: Int,
    message: ChatMessage,
    enableDialogs: Boolean,
    userMessageColor: Color,
    aiMessageColor: Color,
    userTextColor: Color,
    aiTextColor: Color,
    systemMessageColor: Color,
    systemTextColor: Color,
    thinkingBackgroundColor: Color,
    thinkingTextColor: Color,
    onSelectMessageToEdit: ((Int, ChatMessage, String) -> Unit)?,
    onCopyMessage: ((ChatMessage) -> Unit)?,
    onDeleteMessage: ((Int) -> Unit)?,
    onDeleteCurrentMessageVariant: ((Int) -> Unit)?,
    onDeleteMessagesFrom: ((Int) -> Unit)?,
    onRollbackToMessage: ((Int) -> Unit)? = null, // 回滚到指定消息的回调
    onRegenerateMessage: ((Int) -> Unit)? = null,
    onSwitchMessageVariant: ((Int, Int) -> Unit)? = null,
    onSpeakMessage: ((ChatMessage) -> Unit)? = null, // 添加朗读回调
    currentSpeechSegment: String? = null,
    onReplyToMessage: ((ChatMessage) -> Unit)? = null, // 添加回复回调
    onToggleFavoriteMessage: ((Long, Boolean) -> Unit)? = null,
    onCreateBranch: ((Long) -> Unit)? = null, // 添加创建分支回调
    onInsertSummary: ((ChatMessage) -> Unit)? = null, // 添加插入总结回调
    onMentionRoleFromAvatar: ((String) -> Unit)? = null, // 长按角色头像提及
    chatStyle: ChatStyle, // 新增参数
    showMessageTokenStats: Boolean = false,
    showMessageTimingStats: Boolean = false,
    cursorUserBubbleLiquidGlass: Boolean = false,
    cursorUserBubbleWaterGlass: Boolean = false,
    bubbleUserBubbleLiquidGlass: Boolean = false,
    bubbleUserBubbleWaterGlass: Boolean = false,
    bubbleAiBubbleLiquidGlass: Boolean = false,
    bubbleAiBubbleWaterGlass: Boolean = false,
    isHidden: Boolean = false, // 新增参数控制隐藏
    isMultiSelectMode: Boolean = false, // 是否处于多选模式
    isSelected: Boolean = false, // 是否被选中
    onToggleSelection: (() -> Unit)? = null, // 切换选中状态的回调
    onToggleMultiSelectMode: ((Int?) -> Unit)? = null, // 切换多选模式的回调，可传入要初始选中的消息索引
    messageIndex: Int, // 消息索引，用于进入多选时自动选中
    bubbleUserImageStyle: BubbleImageStyleConfig? = null,
    bubbleAiImageStyle: BubbleImageStyleConfig? = null,
    bubbleUserRoundedCornersEnabled: Boolean = true,
    bubbleAiRoundedCornersEnabled: Boolean = true,
    bubbleUserContentPaddingLeft: Float = 12f,
    bubbleUserContentPaddingRight: Float = 12f,
    bubbleAiContentPaddingLeft: Float = 12f,
    bubbleAiContentPaddingRight: Float = 12f,
    isVisualGroupStart: Boolean = true,
    isVisualGroupEnd: Boolean = true,
) {
    var showContextMenu by remember { mutableStateOf(false) }
    var showMessageInfoDialog by remember { mutableStateOf(false) }
    var showHiddenUserMessageDialog by remember { mutableStateOf(false) }
    var showDeleteMessageConfirmDialog by remember { mutableStateOf(false) }
    var copyPreviewText by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val messageInteractionSource = remember { MutableInteractionSource() }

    // 只有用户和AI的消息才能被操作
    val isActionable = message.sender == "user" || message.sender == "ai"
    val isHiddenUserMessage = isHiddenUserPlaceholder(message)
    val isCurrentlySpoken =
        message.sender == "ai" &&
            !currentSpeechSegment.isNullOrBlank() &&
            cleanXmlTags(message.content).contains(currentSpeechSegment)

    Box(
        modifier =
        Modifier
            .alpha(if (isHidden) 0f else 1f)
            .then(
                if (isSelected) {
                    Modifier.background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp)
                    )
                } else Modifier
            )
            .combinedClickable(
                interactionSource = messageInteractionSource,
                indication = null,
                onClick = {
                    if (isMultiSelectMode && isActionable) {
                        onToggleSelection?.invoke()
                    } else if (!isMultiSelectMode && enableDialogs && isHiddenUserMessage) {
                        showHiddenUserMessageDialog = true
                    }
                },
                onLongClick = { 
                    if (!isMultiSelectMode && isActionable) {
                        showContextMenu = true
                    }
                },
            ),
    ) {
        Column {
            when (chatStyle) {
                ChatStyle.CURSOR -> {
                    CursorStyleChatMessage(
                        message = message,
                        userMessageColor = userMessageColor,
                        userMessageLiquidGlassEnabled = cursorUserBubbleLiquidGlass,
                        userMessageWaterGlassEnabled = cursorUserBubbleWaterGlass,
                        aiMessageColor = aiMessageColor,
                        userTextColor = userTextColor,
                        aiTextColor = aiTextColor,
                        systemMessageColor = systemMessageColor,
                        systemTextColor = systemTextColor,
                        thinkingBackgroundColor = thinkingBackgroundColor,
                        thinkingTextColor = thinkingTextColor,
                        supportToolMarkup = true,
                        initialThinkingExpanded = false,
                        onDeleteMessage = onDeleteMessage,
                        index = index,
                        enableDialogs = enableDialogs,
                        onEditSummary = { summaryMessage ->
                            onSelectMessageToEdit?.invoke(index, summaryMessage, "summary")
                        }
                    )
                }

                ChatStyle.BUBBLE -> {
                    BubbleStyleChatMessage(
                        message = message,
                        userMessageColor = userMessageColor,
                        aiMessageColor = aiMessageColor,
                        userTextColor = userTextColor,
                        aiTextColor = aiTextColor,
                        systemMessageColor = systemMessageColor,
                        systemTextColor = systemTextColor,
                        userMessageLiquidGlassEnabled = bubbleUserBubbleLiquidGlass,
                        userMessageWaterGlassEnabled = bubbleUserBubbleWaterGlass,
                        aiMessageLiquidGlassEnabled = bubbleAiBubbleLiquidGlass,
                        aiMessageWaterGlassEnabled = bubbleAiBubbleWaterGlass,
                        userBubbleImageStyle = bubbleUserImageStyle,
                        aiBubbleImageStyle = bubbleAiImageStyle,
                        bubbleUserRoundedCornersEnabled = bubbleUserRoundedCornersEnabled,
                        bubbleAiRoundedCornersEnabled = bubbleAiRoundedCornersEnabled,
                        bubbleUserContentPaddingLeft = bubbleUserContentPaddingLeft,
                        bubbleUserContentPaddingRight = bubbleUserContentPaddingRight,
                        bubbleAiContentPaddingLeft = bubbleAiContentPaddingLeft,
                        bubbleAiContentPaddingRight = bubbleAiContentPaddingRight,
                        isVisualGroupStart = isVisualGroupStart,
                        isVisualGroupEnd = isVisualGroupEnd,
                        isCurrentlySpoken = isCurrentlySpoken,
                        currentSpeechSegment = currentSpeechSegment,
                        isHidden = isHidden,
                        onDeleteMessage = onDeleteMessage,
                        index = index,
                        enableDialogs = enableDialogs,
                        onRoleAvatarLongPress = onMentionRoleFromAvatar,
                        onEditSummary = { summaryMessage ->
                            onSelectMessageToEdit?.invoke(index, summaryMessage, "summary")
                        }
                    )
                }
            }

            if (message.sender == "ai" &&
                isVisualGroupEnd &&
                (
                    message.variantCount > 1 ||
                        (showMessageTokenStats && hasDisplayableTokenStats(message)) ||
                        (showMessageTimingStats && hasDisplayableTimingStats(message))
                )
            ) {
                MessageFooterBar(
                    message = message,
                    showMessageTokenStats = showMessageTokenStats,
                    showMessageTimingStats = showMessageTimingStats,
                    onSelectVariant = { targetVariantIndex ->
                        onSwitchMessageVariant?.invoke(index, targetVariantIndex)
                    },
                )
            }
        }

        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false },
            modifier = Modifier
                .width(180.dp)
                .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(6.dp)),
            properties = PopupProperties(
                focusable = true,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            if (!isHiddenUserMessage) {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(id = R.string.copy_message),
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 13.sp
                        )
                    },
                    onClick = {
                        val cleanContent = cleanXmlTags(message.content)
                        copyPreviewText = cleanContent
                        onCopyMessage?.invoke(message)
                        showContextMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = stringResource(id = R.string.copy_message),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                )

                // 朗读消息选项
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(R.string.read_message),
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 13.sp
                        )
                    },
                    onClick = {
                        onSpeakMessage?.invoke(message)
                        showContextMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                            contentDescription = stringResource(R.string.read_message),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                )
            }

            // 根据消息发送者显示不同的操作
            if (message.sender == "user") {
                if (!isHiddenUserMessage) {
                    // 编辑并重发选项
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(id = R.string.edit_and_resend),
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = 13.sp
                            )
                        },
                        onClick = {
                            onSelectMessageToEdit?.invoke(index, message, "user")
                            showContextMenu = false
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = stringResource(id = R.string.edit_and_resend),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                }
                // 回滚到此处
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(id = R.string.rollback_to_here),
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 13.sp
                        )
                    },
                    onClick = {
                        onRollbackToMessage?.invoke(index)
                        showContextMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = stringResource(id = R.string.rollback_to_here),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                )
            } else if (message.sender == "ai") {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(id = R.string.chat_regenerate_single),
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 13.sp
                        )
                    },
                    onClick = {
                        onRegenerateMessage?.invoke(index)
                        showContextMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(id = R.string.chat_regenerate_single),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                )
                // 修改记忆选项
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(id = R.string.modify_memory),
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 13.sp
                        )
                    },
                    onClick = {
                        onSelectMessageToEdit?.invoke(index, message, "ai")
                        showContextMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.AutoFixHigh,
                            contentDescription = stringResource(id = R.string.modify_memory),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                )
            }

            if (message.sender == "ai" && message.variantCount > 1) {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(id = R.string.chat_delete_single_variant),
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 13.sp,
                        )
                    },
                    onClick = {
                        onDeleteCurrentMessageVariant?.invoke(index)
                        showContextMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(id = R.string.chat_delete_single_variant),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }

            // 删除
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(id = R.string.delete),
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 13.sp
                    )
                },
                onClick = {
                    showContextMenu = false
                    showDeleteMessageConfirmDialog = true
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(id = R.string.delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                },
                modifier = Modifier.heightIn(min = 48.dp)
            )

            // 回复选项
            if (message.sender == "ai") {
                DropdownMenuItem(
                text = {
                        Text(
                            stringResource(R.string.reply_message),
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 13.sp
                       )
                },
                onClick = {
                        onReplyToMessage?.invoke(message)
                        showContextMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Reply,
                            contentDescription = stringResource(R.string.reply_message),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                )
            }

            if (message.sender == "user" || message.sender == "ai") {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(id = R.string.insert_summary),
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 13.sp
                        )
                    },
                    onClick = {
                        onInsertSummary?.invoke(message)
                        showContextMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Summarize,
                            contentDescription = stringResource(id = R.string.insert_summary),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                )
            }

            // 创建分支
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(id = R.string.create_branch),
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 13.sp
                    )
                },
                onClick = {
                    onCreateBranch?.invoke(message.timestamp)
                    showContextMenu = false
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.AccountTree,
                        contentDescription = stringResource(id = R.string.create_branch),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                },
                modifier = Modifier.heightIn(min = 48.dp)
            )

            // 信息
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(id = R.string.info),
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 13.sp
                    )
                },
                onClick = {
                    showContextMenu = false
                    showMessageInfoDialog = true
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = stringResource(id = R.string.info),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                },
                modifier = Modifier.heightIn(min = 48.dp)
            )

            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(id = R.string.multi_select),
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 13.sp
                    )
                },
                onClick = {
                    onToggleMultiSelectMode?.invoke(messageIndex) // 传入消息索引
                    showContextMenu = false
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = stringResource(id = R.string.multi_select),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                },
                modifier = Modifier.heightIn(min = 48.dp)
            )
        }

        if (enableDialogs && isHiddenUserMessage && showHiddenUserMessageDialog) {
            AlertDialog(
                onDismissRequest = { showHiddenUserMessageDialog = false },
                title = { Text(text = stringResource(R.string.chat_hidden_user_message_badge)) },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showHiddenUserMessageDialog = false }) {
                        Text(text = stringResource(R.string.floating_close))
                    }
                },
            )
        }

        if (showMessageInfoDialog) {
            MessageInfoDialog(
                message = message,
                onDismiss = { showMessageInfoDialog = false }
            )
        }

        if (showDeleteMessageConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteMessageConfirmDialog = false },
                title = { Text(text = stringResource(R.string.confirm_delete)) },
                text = { Text(text = stringResource(R.string.chat_delete_message_confirm_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDeleteMessage?.invoke(index)
                            showDeleteMessageConfirmDialog = false
                        }
                    ) {
                        Text(text = stringResource(R.string.confirm_delete_action))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteMessageConfirmDialog = false }) {
                        Text(text = stringResource(R.string.cancel))
                    }
                },
            )
        }

        copyPreviewText?.let { previewText ->
            MessageCopyPreviewBottomSheet(
                text = previewText,
                onDismiss = { copyPreviewText = null }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageCopyPreviewBottomSheet(
    text: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val textScrollState = rememberScrollState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
        ) {
            Text(
                text = stringResource(id = R.string.copy_message),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            SelectionContainer(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(textScrollState)
                    .padding(bottom = 12.dp)
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(text))
                        Toast.makeText(
                            context,
                            context.getString(R.string.message_copied_to_clipboard),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                ) {
                    Text(text = stringResource(id = R.string.copy_message))
                }
            }
        }
    }
}

private fun hasDisplayableTokenStats(message: ChatMessage): Boolean {
    return message.inputTokens > 0 || message.cachedInputTokens > 0 || message.outputTokens > 0
}

private fun hasDisplayableTimingStats(message: ChatMessage): Boolean {
    return message.waitDurationMs > 0L || message.outputDurationMs > 0L
}

private fun formatCompactDuration(durationMs: Long): String {
    if (durationMs <= 0L) return "0ms"
    return if (durationMs >= 1000L) {
        if (durationMs >= 10_000L) {
            String.format(Locale.getDefault(), "%.0fs", durationMs / 1000f)
        } else {
            String.format(Locale.getDefault(), "%.1fs", durationMs / 1000f)
        }
    } else {
        "${durationMs}ms"
    }
}

@Composable
private fun MessageFooterBar(
    message: ChatMessage,
    showMessageTokenStats: Boolean,
    showMessageTimingStats: Boolean,
    onSelectVariant: (Int) -> Unit,
) {
    val hasPrevious = message.selectedVariantIndex > 0
    val hasNext = message.selectedVariantIndex < message.variantCount - 1
    val context = LocalContext.current
    val tokenSummary =
        remember(message.inputTokens, message.cachedInputTokens, message.outputTokens) {
            val totalTokens = message.inputTokens + message.outputTokens
            context.getString(
                R.string.chat_message_token_stats_compact,
                totalTokens,
                message.cachedInputTokens,
                message.inputTokens,
                message.outputTokens,
            )
        }
    val timeSummary =
        remember(message.waitDurationMs, message.outputDurationMs) {
            val totalDuration = (message.waitDurationMs + message.outputDurationMs).coerceAtLeast(0L)
            context.getString(
                R.string.chat_message_timing_stats_compact,
                formatCompactDuration(totalDuration),
                formatCompactDuration(message.waitDurationMs),
                formatCompactDuration(message.outputDurationMs),
            )
        }
    val statsTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f)

    Column(
        modifier = Modifier.padding(start = 16.dp, top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (message.variantCount > 1) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(
                    onClick = { onSelectVariant(message.selectedVariantIndex - 1) },
                    enabled = hasPrevious,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.chat_previous_variant),
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    text =
                        stringResource(
                            R.string.chat_message_variant_counter,
                            message.selectedVariantIndex + 1,
                            message.variantCount,
                        ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(
                    onClick = { onSelectVariant(message.selectedVariantIndex + 1) },
                    enabled = hasNext,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = stringResource(R.string.chat_next_variant),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        if (showMessageTokenStats && hasDisplayableTokenStats(message)) {
            Text(
                text = tokenSummary,
                style = MaterialTheme.typography.labelSmall,
                color = statsTextColor,
            )
        }

        if (showMessageTimingStats && hasDisplayableTimingStats(message)) {
            Text(
                text = timeSummary,
                style = MaterialTheme.typography.labelSmall,
                color = statsTextColor,
            )
        }
    }
}

@Composable
private fun LoadingDotsIndicator(textColor: Color) {
    val infiniteTransition = rememberInfiniteTransition()

    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val jumpHeight = -5f
        val animationDelay = 160

        (0..2).forEach { index ->
            val offsetY by
            infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = jumpHeight,
                animationSpec =
                infiniteRepeatable(
                    animation =
                    keyframes {
                        durationMillis = 600
                        0f at 0
                        jumpHeight * 0.4f at 100
                        jumpHeight * 0.8f at 200
                        jumpHeight at 300
                        jumpHeight * 0.8f at 400
                        jumpHeight * 0.4f at 500
                        0f at 600
                    },
                    repeatMode = RepeatMode.Restart,
                    initialStartOffset = StartOffset(index * animationDelay),
                ),
                label = "",
            )

            Box(
                modifier =
                Modifier
                    .size(6.dp)
                    .offset { IntOffset(x = 0, y = offsetY.dp.roundToPx()) }
                    .background(
                        color = textColor.copy(alpha = 0.6f),
                        shape = CircleShape,
                    ),
            )
        }
    }
}
