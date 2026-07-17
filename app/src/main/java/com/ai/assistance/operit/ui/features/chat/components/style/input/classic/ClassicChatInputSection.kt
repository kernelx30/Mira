package com.ai.assistance.operit.ui.features.chat.components.style.input.classic

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.ai.assistance.operit.data.model.AttachmentInfo
import com.ai.assistance.operit.data.model.InputProcessingState
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.core.tools.ToolProgressBus
import com.ai.assistance.operit.ui.common.animations.SimpleAnimatedVisibility
import com.ai.assistance.operit.ui.features.chat.components.AttachmentChip
import com.ai.assistance.operit.ui.features.chat.components.MateAttachmentActionSheet
import com.ai.assistance.operit.ui.features.chat.components.style.input.common.rememberMentionVisualTransformation
import com.ai.assistance.operit.ui.features.chat.components.style.input.common.PendingMessageQueuePanel
import com.ai.assistance.operit.data.model.PendingQueueMessageItem
import com.ai.assistance.operit.ui.features.chat.components.style.input.common.MiraComposerControlBar
import com.ai.assistance.operit.ui.features.chat.viewmodel.ChatViewModel
import com.ai.assistance.operit.ui.floating.FloatingMode
import com.ai.assistance.operit.ui.theme.isLiquidGlassSupported
import com.ai.assistance.operit.ui.theme.isWaterGlassSupported
import com.ai.assistance.operit.ui.theme.liquidGlass
import com.ai.assistance.operit.ui.theme.waterGlass
import com.ai.assistance.operit.util.ChatUtils
import androidx.compose.ui.res.stringResource
import android.net.Uri
import kotlinx.coroutines.flow.collect

@Composable
fun ClassicChatInputSection(
    actualViewModel: ChatViewModel,
    userMessage: TextFieldValue,
    onUserMessageChange: (TextFieldValue) -> Unit,
    enableEnterToSend: Boolean = false,
    onSendMessage: () -> Unit,
    onSendSuggestedMessage: (String) -> Unit,
    onQueueMessage: () -> Unit,
    onCancelMessage: () -> Unit,
    isLoading: Boolean,
    inputState: InputProcessingState = InputProcessingState.Idle,
    allowTextInputWhileProcessing: Boolean = false,
    onAttachmentRequest: (String) -> Unit = {},
    attachments: List<AttachmentInfo> = emptyList(),
    onRemoveAttachment: (String) -> Unit = {},
    onInsertAttachment: (AttachmentInfo) -> Unit = {},
    onAttachScreenContent: () -> Unit = {},
    onAttachNotifications: () -> Unit = {},
    onAttachLocation: () -> Unit = {},
    onAttachMemory: () -> Unit = {},
    onAttachPackage: (String) -> Unit = {},
    onTakePhoto: (Uri) -> Unit,
    hasBackgroundImage: Boolean = false,
    chatInputTransparent: Boolean = false,
    chatInputFloating: Boolean = true,
    chatInputLiquidGlass: Boolean = false,
    chatInputWaterGlass: Boolean = false,
    modifier: Modifier = Modifier,
    externalAttachmentPanelState: Boolean? = null,
    onAttachmentPanelStateChange: ((Boolean) -> Unit)? = null,
    showInputProcessingStatus: Boolean = true,
    enableTools: Boolean = true,
    onToggleTools: () -> Unit = {},
    onNavigateToModelConfig: () -> Unit = {},
    replyToMessage: ChatMessage? = null, // 回复目标消息
    onClearReply: (() -> Unit)? = null, // 清除回复状态的回调
    isWorkspaceOpen: Boolean = false,
    pendingQueueMessages: List<PendingQueueMessageItem> = emptyList(),
    isPendingQueueExpanded: Boolean = true,
    onPendingQueueExpandedChange: (Boolean) -> Unit = {},
    onDeletePendingQueueMessage: (Long) -> Unit = {},
    onEditPendingQueueMessage: (Long) -> Unit = {},
    onSendPendingQueueMessage: (Long) -> Unit = {}
) {
    val showTokenLimitDialog = remember { mutableStateOf(false) }
    val context = LocalContext.current
    val suggestedUserMessage by actualViewModel.suggestedUserMessage.collectAsState()
    val sendableSuggestedMessage =
        suggestedUserMessage?.trim()?.takeIf { userMessage.text.isBlank() && it.isNotEmpty() }
    val composerFocusRequester = remember { FocusRequester() }
    val softwareKeyboardController = LocalSoftwareKeyboardController.current
    val inputHeightScale = LocalDensity.current.fontScale.coerceIn(1f, 2f)
    val textFieldMaxHeight =
        120.dp * inputHeightScale + 4.dp * (inputHeightScale - 1f)
    val composerMaxHeight = textFieldMaxHeight + 8.dp
    val isProcessing =
        isLoading ||
            inputState is InputProcessingState.Connecting ||
            inputState is InputProcessingState.ExecutingTool ||
            inputState is InputProcessingState.ToolProgress ||
            inputState is InputProcessingState.Processing ||
            inputState is InputProcessingState.ProcessingToolResult ||
            inputState is InputProcessingState.Summarizing ||
            inputState is InputProcessingState.Receiving

    LaunchedEffect(actualViewModel, composerFocusRequester) {
        actualViewModel.composerFocusRequests.collect {
            composerFocusRequester.requestFocus()
            softwareKeyboardController?.show()
        }
    }

    if (showTokenLimitDialog.value) {
        AlertDialog(
            onDismissRequest = {
                showTokenLimitDialog.value = false
            },
            title = { Text(context.getString(R.string.token_limit_warning)) },
            text = { Text(context.getString(R.string.token_limit_warning_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showTokenLimitDialog.value = false
                        sendableSuggestedMessage?.let(onSendSuggestedMessage) ?: onSendMessage()
                    }
                ) { Text(context.getString(R.string.continue_send)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showTokenLimitDialog.value = false
                    },
                ) {
                    Text(context.getString(R.string.cancel))
                }
            }
        )
    }
    val modernTextStyle = TextStyle(fontSize = 16.sp, lineHeight = 22.sp)
    val mentionVisualTransformation = rememberMentionVisualTransformation(modernTextStyle)
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

    val toolProgressEvent by ToolProgressBus.progress.collectAsState()

    // Token limit calculation
    val currentWindowSize by actualViewModel.currentWindowSize.collectAsState()
    val maxWindowSizeInK by actualViewModel.maxWindowSizeInK.collectAsState()
    val maxTokens = (maxWindowSizeInK * 1024).toLong().coerceAtLeast(0L)
    val sendableText = userMessage.text.ifBlank { sendableSuggestedMessage.orEmpty() }
    val userMessageTokens = remember(sendableText) { ChatUtils.estimateTokenCount(sendableText) }
    val projectedTokens = userMessageTokens.toLong() + currentWindowSize

    val isOverTokenLimit =
        if (maxTokens > 0) {
            projectedTokens > maxTokens
        } else {
            false
        }

    val hasDraftText = userMessage.text.isNotBlank()
    val canSendMessage =
        hasDraftText || sendableSuggestedMessage != null || attachments.isNotEmpty()
    val showQueueAction = isProcessing && hasDraftText
    val showCancelAction = isProcessing && !showQueueAction
    val sendButtonEnabled =
        when {
            isProcessing -> true // Queue / Cancel button
            canSendMessage -> true // Send button is always enabled if there's content
            else -> true // Mic button
        }

    val voicePermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                actualViewModel.launchFullscreenVoiceModeAfterMicPermissionGranted(
                    colorScheme = colorScheme,
                    typography = typography,
                )
            } else {
                actualViewModel.showToast(context.getString(R.string.microphone_permission_denied_toast))
            }
        }

    // 控制附件面板的展开状态 - 使用外部状态或本地状态
    val (showAttachmentPanel, setShowAttachmentPanel) =
        androidx.compose.runtime.remember {
            androidx.compose.runtime.mutableStateOf(
                externalAttachmentPanelState ?: false
            )
        }

    // 当外部状态变化时更新本地状态
    androidx.compose.runtime.LaunchedEffect(externalAttachmentPanelState) {
        externalAttachmentPanelState?.let { setShowAttachmentPanel(it) }
    }

    // 当本地状态改变时通知外部
    androidx.compose.runtime.LaunchedEffect(showAttachmentPanel) {
        onAttachmentPanelStateChange?.invoke(showAttachmentPanel)
    }
    fun handleEnterSendAction() {
        if (!canSendMessage) return
        if (showQueueAction) {
            onQueueMessage()
            setShowAttachmentPanel(false)
            return
        }
        if (isOverTokenLimit) {
            showTokenLimitDialog.value = true
            return
        }
        sendableSuggestedMessage?.let(onSendSuggestedMessage) ?: onSendMessage()
        setShowAttachmentPanel(false)
    }

    val surfaceColor = when {
        chatInputTransparent -> MaterialTheme.colorScheme.surface.copy(alpha = 0f)
        hasBackgroundImage -> MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
        chatInputFloating -> MaterialTheme.colorScheme.surfaceContainerLow
        else -> MaterialTheme.colorScheme.surface
    }
    val queueContainerColor = when {
        chatInputTransparent -> MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
        else -> surfaceColor
    }
    val queueItemColor = when {
        chatInputTransparent -> MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        hasBackgroundImage -> MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        else -> MaterialTheme.colorScheme.surface
    }
    val inputLiquidGlassEnabled =
        chatInputTransparent && chatInputLiquidGlass && !chatInputWaterGlass && isLiquidGlassSupported()
    val inputWaterGlassEnabled =
        chatInputTransparent && chatInputWaterGlass && isWaterGlassSupported()
    val containerShape = if (chatInputFloating) RoundedCornerShape(24.dp) else RoundedCornerShape(0.dp)
    val containerModifier =
        if (chatInputFloating) {
            modifier.padding(horizontal = 12.dp)
        } else {
            modifier
        }

    Column(modifier = containerModifier) {
        SimpleAnimatedVisibility(
            visible =
                showInputProcessingStatus &&
                    inputState !is InputProcessingState.Idle &&
                    inputState !is InputProcessingState.Completed &&
                    inputState !is InputProcessingState.Error
        ) {
            val (progressColor, baseMessage) = when (inputState) {
                is InputProcessingState.Connecting -> MaterialTheme.colorScheme.tertiary to inputState.message
                is InputProcessingState.ExecutingTool -> MaterialTheme.colorScheme.secondary to context.getString(R.string.executing_tool, inputState.toolName)
                is InputProcessingState.ToolProgress -> MaterialTheme.colorScheme.secondary to inputState.message
                is InputProcessingState.Processing -> MaterialTheme.colorScheme.primary to inputState.message
                is InputProcessingState.ProcessingToolResult -> MaterialTheme.colorScheme.tertiary.copy(
                    alpha = 0.8f
                ) to context.getString(R.string.processing_tool_result, inputState.toolName)
                is InputProcessingState.Summarizing -> MaterialTheme.colorScheme.tertiary to inputState.message
                is InputProcessingState.Receiving -> MaterialTheme.colorScheme.secondary to inputState.message
                else -> MaterialTheme.colorScheme.primary to ""
            }

            var message = baseMessage
            var progressValue = when (inputState) {
                is InputProcessingState.Processing -> 0.3f
                is InputProcessingState.Connecting -> 0.6f
                is InputProcessingState.Summarizing -> 0.05f
                is InputProcessingState.ToolProgress -> inputState.progress
                else -> 1f
            }

            if (inputState is InputProcessingState.ExecutingTool) {
                val event = toolProgressEvent
                if (event != null && inputState.toolName.contains(event.toolName)) {
                    progressValue = event.progress
                    if (event.message.isNotBlank()) message = event.message
                }
            }

            if (inputState is InputProcessingState.Summarizing) {
                val event = toolProgressEvent
                if (event != null && event.toolName == ToolProgressBus.SUMMARY_PROGRESS_TOOL_NAME) {
                    progressValue = event.progress
                    if (event.message.isNotBlank()) message = event.message
                }
            }

            if (message.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        progress = { progressValue.coerceIn(0f, 1f) },
                        modifier = Modifier.size(14.dp),
                        color = progressColor,
                        trackColor = progressColor.copy(alpha = 0.18f),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                .waterGlass(
                    enabled = inputWaterGlassEnabled,
                    shape = containerShape,
                    containerColor = MaterialTheme.colorScheme.surface,
                    shadowElevation = if (chatInputFloating) 10.dp else 14.dp,
                    borderWidth = 0.7.dp,
                    overlayAlphaBoost = if (chatInputFloating) 0.04f else 0.08f,
                )
                .liquidGlass(
                    enabled = inputLiquidGlassEnabled,
                    shape = containerShape,
                    containerColor = MaterialTheme.colorScheme.surface,
                    shadowElevation = if (chatInputFloating) 10.dp else 14.dp,
                    borderWidth = 0.42.dp,
                    blurRadius = if (chatInputFloating) 16.dp else 20.dp,
                    overlayAlphaBoost = if (chatInputFloating) 0.06f else 0.10f,
                )
                .then(
                    if (chatInputFloating && !inputLiquidGlassEnabled && !inputWaterGlassEnabled) {
                        Modifier.border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = containerShape,
                        )
                    } else {
                        Modifier
                    },
                )
                .clip(containerShape)
                .background(
                    if (inputLiquidGlassEnabled || inputWaterGlassEnabled) {
                        Color.Transparent
                    } else {
                        surfaceColor
                    }
                ),
    ) {
        Column {
            // Reply preview section
            replyToMessage?.let { message ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(8.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Reply,
                            contentDescription = context.getString(R.string.reply_message),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        val previewText = message.content
                            .replace(Regex("<[^>]*>"), "") // 移除XML标签
                            .trim()
                            .let { if (it.length > 50) it.take(50) + "..." else it }
                        Text(
                            text = "${stringResource(R.string.reply_message)}: $previewText",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        
                        IconButton(
                            onClick = { onClearReply?.invoke() },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = context.getString(R.string.cancel_reply),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            PendingMessageQueuePanel(
                queuedMessages = pendingQueueMessages,
                expanded = isPendingQueueExpanded,
                onExpandedChange = onPendingQueueExpandedChange,
                onDeleteMessage = onDeletePendingQueueMessage,
                onEditMessage = onEditPendingQueueMessage,
                onSendMessage = onSendPendingQueueMessage,
                containerColor = queueContainerColor,
                itemColor = queueItemColor,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            // Attachment chips row - only show if there are attachments
            if (attachments.isNotEmpty()) {
                LazyRow(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                start = 16.dp,
                                end = 16.dp,
                                top = 0.dp,
                                bottom = 6.dp
                            ),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    items(attachments) { attachment ->
                        AttachmentChip(
                            attachmentInfo = attachment,
                            onRemove = {
                                onRemoveAttachment(
                                    attachment.filePath
                                )
                            },
                            onInsert = {
                                onInsertAttachment(attachment)
                            }
                        )
                    }
                }
            }

            MateAttachmentActionSheet(
                visible = showAttachmentPanel,
                onAttachImage = { filePath -> onAttachmentRequest(filePath) },
                onAttachFile = { filePath -> onAttachmentRequest(filePath) },
                onAttachScreenContent = onAttachScreenContent,
                onAttachNotifications = onAttachNotifications,
                onAttachLocation = onAttachLocation,
                onAttachMemory = onAttachMemory,
                onAttachPackage = onAttachPackage,
                onTakePhoto = onTakePhoto,
                onDismiss = { setShowAttachmentPanel(false) },
            )

            Row(
                modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp, max = composerMaxHeight)
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                // Input field (保持原有高度)

                val classicInputEnabled = !isProcessing || allowTextInputWhileProcessing

                BasicTextField(
                    value = userMessage,
                    onValueChange = { value ->
                        actualViewModel.dismissSuggestedUserMessage()
                        onUserMessageChange(value)
                    },
                    modifier =
                        Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp, max = textFieldMaxHeight)
                            .focusRequester(composerFocusRequester)
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    actualViewModel.dismissSuggestedUserMessage()
                                }
                            }
                            .pointerInput(suggestedUserMessage) {
                                if (suggestedUserMessage != null) {
                                    awaitPointerEventScope {
                                        awaitPointerEvent(PointerEventPass.Initial)
                                        actualViewModel.dismissSuggestedUserMessage()
                                    }
                                }
                            }
                            .onPreviewKeyEvent { keyEvent ->
                                if (!enableEnterToSend) {
                                    false
                                } else if (
                                    keyEvent.type == KeyEventType.KeyDown &&
                                        keyEvent.key == Key.Enter &&
                                        !keyEvent.isShiftPressed
                                ) {
                                    handleEnterSendAction()
                                    true
                                } else {
                                    false
                                }
                            },
                    textStyle = modernTextStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    visualTransformation = mentionVisualTransformation,
                    maxLines = 5,
                    minLines = 1,
                    keyboardOptions =
                    KeyboardOptions(imeAction = if (enableEnterToSend) ImeAction.Send else ImeAction.Default),
                    keyboardActions =
                    if (enableEnterToSend) {
                        KeyboardActions(onSend = { handleEnterSendAction() })
                    } else {
                        KeyboardActions()
                    },
                    enabled = classicInputEnabled,
                    decorationBox = { innerTextField ->
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (userMessage.text.isEmpty()) {
                                Text(
                                    text = suggestedUserMessage ?: if (isWorkspaceOpen) {
                                        context.getString(R.string.input_question_with_workspace)
                                    } else {
                                        context.getString(R.string.input_question_hint)
                                    },
                                    style = modernTextStyle,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            innerTextField()
                        }
                    },
                )

                Spacer(modifier = Modifier.width(4.dp))

                // Send button (发送按钮) - 确保圆形
                Box(
                    modifier =
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(
                            enabled = sendButtonEnabled,
                            onClick = {
                                when {
                                    showCancelAction ->
                                        onCancelMessage()
                                    showQueueAction -> {
                                        onQueueMessage()
                                        setShowAttachmentPanel(false)
                                    }

                                    canSendMessage -> {
                                        if (isOverTokenLimit) {
                                            showTokenLimitDialog.value = true
                                        } else {
                                            sendableSuggestedMessage?.let(onSendSuggestedMessage)
                                                ?: onSendMessage()
                                            setShowAttachmentPanel(false)
                                        }
                                    }

                                    else -> {
                                        actualViewModel.onFloatingButtonClick(
                                            FloatingMode.FULLSCREEN,
                                            voicePermissionLauncher,
                                            colorScheme,
                                            typography
                                        )
                                    }
                                }
                            }
                        )
                        .padding(4.dp)
                        .background(
                            when {
                                showCancelAction ->
                                    MaterialTheme.colorScheme.inverseSurface
                                showQueueAction ->
                                    MaterialTheme.colorScheme.tertiary

                                canSendMessage ->
                                    if (isOverTokenLimit)
                                        MaterialTheme.colorScheme.secondary // Warning color
                                    else
                                        MaterialTheme.colorScheme.primary

                                else ->
                                    MaterialTheme
                                        .colorScheme
                                        .primary
                            },
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val iconTint =
                        when {
                            showCancelAction -> MaterialTheme.colorScheme.inverseOnSurface
                            showQueueAction -> MaterialTheme.colorScheme.onTertiary
                            canSendMessage ->
                                if (isOverTokenLimit)
                                    MaterialTheme.colorScheme.onSecondary
                                else
                                    MaterialTheme.colorScheme.onPrimary

                            else -> MaterialTheme.colorScheme.onPrimary
                        }
                    Icon(
                        imageVector =
                        when {
                            showCancelAction -> Icons.Default.Stop
                            showQueueAction -> Icons.Default.Add
                            canSendMessage -> Icons.AutoMirrored.Filled.Send
                            else -> Icons.Default.Mic
                        },
                        contentDescription =
                        when {
                            showCancelAction -> context.getString(R.string.cancel)
                            showQueueAction -> context.getString(R.string.chat_queue_add_message)
                            canSendMessage -> context.getString(R.string.send)
                            else -> context.getString(R.string.voice_input)
                        },
                        tint = iconTint,
                        modifier = Modifier.size(if (showCancelAction) 18.dp else 21.dp)
                    )
                }

            }

            MiraComposerControlBar(
                actualViewModel = actualViewModel,
                onOpenAttachments = {
                    if (showAttachmentPanel) {
                        setShowAttachmentPanel(false)
                    } else {
                        softwareKeyboardController?.hide()
                        setShowAttachmentPanel(true)
                    }
                },
                onNavigateToModelConfig = onNavigateToModelConfig,
            )



            // Token limit warning
            if (isOverTokenLimit && canSendMessage && !showQueueAction) {
                Text(
                    text =
                    context.getString(R.string.token_limit_exceeded_message, projectedTokens, maxTokens),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 4.dp)
                )
            }

            }
        }
    }
}
