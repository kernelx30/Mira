package com.ai.assistance.operit.ui.floating.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.ai.assistance.operit.data.model.InputProcessingState
import com.ai.assistance.operit.data.model.PromptFunctionType
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.floating.FloatContext
import com.ai.assistance.operit.ui.floating.FloatingMode
import com.ai.assistance.operit.ui.main.MainActivity
import com.ai.assistance.operit.services.floating.FloatingTranscriptSource
import com.ai.assistance.operit.services.floating.MiraFloatingLayoutPolicy
import com.ai.assistance.operit.services.floating.MiraFloatingTranscriptPolicy
import com.ai.assistance.operit.util.ConversationContentVisibility
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private data class FloatingCompanionIdentity(
    val name: String,
    val avatarUri: String?,
)

@Composable
private fun rememberFloatingCompanionIdentity(floatContext: FloatContext): FloatingCompanionIdentity {
    val service = floatContext.chatService
        ?: return FloatingCompanionIdentity(name = "Mira", avatarUri = null)
    val chatCore = remember(service) { service.getChatCore() }
    val histories by chatCore.chatHistories.collectAsState(initial = emptyList())
    val currentChatId by chatCore.currentChatId.collectAsState(initial = null)
    val characterName =
        histories.firstOrNull { it.id == currentChatId }
            ?.characterCardName
            ?.takeIf { it.isNotBlank() }
            ?: "Mira"
    var avatarUri by remember(characterName) { mutableStateOf<String?>(null) }
    val characterCardManager = remember(service) { CharacterCardManager.getInstance(service) }
    val preferences = remember(service) { UserPreferencesManager.getInstance(service) }

    LaunchedEffect(characterName) {
        avatarUri =
            characterCardManager.findCharacterCardByName(characterName)
                ?.id
                ?.let { preferences.getAiAvatarForCharacterCardFlow(it).first() }
    }

    return FloatingCompanionIdentity(name = characterName, avatarUri = avatarUri)
}

@Composable
private fun MiraCompanionAvatar(
    identity: FloatingCompanionIdentity,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (!identity.avatarUri.isNullOrBlank()) {
            Image(
                painter = rememberAsyncImagePainter(Uri.parse(identity.avatarUri)),
                contentDescription = identity.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = identity.name,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(size * 0.52f),
            )
        }
    }
}

@Composable
fun MiraCompanionBubble(floatContext: FloatContext) {
    val identity = rememberFloatingCompanionIdentity(floatContext)
    val processingState by floatContext.inputProcessingState
    val isProcessing = processingState.isActive()

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            floatContext.snapToEdge(true)
                            floatContext.saveWindowState?.invoke()
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            floatContext.onMove(amount.x, amount.y, 1f)
                        },
                    )
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    floatContext.onModeChange(FloatingMode.WINDOW)
                },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.size(52.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            shadowElevation = 3.dp,
        ) {
            MiraCompanionAvatar(
                identity = identity,
                size = 48.dp,
                modifier = Modifier.padding(2.dp),
            )
        }

        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(2.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        if (isProcessing) MaterialTheme.colorScheme.tertiary
                        else Color(0xFF2E9D67)
                    )
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
        )
    }
}

@Composable
fun MiraVoiceOrb(floatContext: FloatContext) {
    val identity = rememberFloatingCompanionIdentity(floatContext)
    val processingState by floatContext.inputProcessingState

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            floatContext.snapToEdge(true)
                            floatContext.saveWindowState?.invoke()
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            floatContext.onMove(amount.x, amount.y, 1f)
                        },
                    )
                }
                .clickable { floatContext.onModeChange(FloatingMode.WINDOW) },
        contentAlignment = Alignment.Center,
    ) {
        if (processingState.isActive()) {
            CircularProgressIndicator(
                // Keep the indicator inside the 52dp overlay window; 58dp was visibly clipped.
                modifier = Modifier.size(48.dp),
                strokeWidth = 2.dp,
            )
        }
        MiraCompanionAvatar(identity = identity, size = 50.dp)
        Surface(
            modifier = Modifier.align(Alignment.BottomEnd).size(20.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "语音会话",
                modifier = Modifier.padding(4.dp),
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
fun MiraQuickReplyCard(floatContext: FloatContext) {
    val identity = rememberFloatingCompanionIdentity(floatContext)
    val focusRequester = remember { FocusRequester() }
    val processingState by floatContext.inputProcessingState
    val isProcessing = processingState.isActive()
    var showMenu by remember { mutableStateOf(false) }
    val transcript =
        MiraFloatingTranscriptPolicy.latestConversation(
            floatContext.messages.map { message ->
                FloatingTranscriptSource(
                    sender = message.sender,
                    text =
                        if (message.sender == "ai") {
                            ConversationContentVisibility.extractAssistantConversationContent(
                                message.content
                            )
                        } else {
                            message.content
                        },
                )
            }
        )
    val cardWidth =
        MiraFloatingLayoutPolicy.cardWidthDp(floatContext.screenWidth.value.toInt()).dp

    LaunchedEffect(floatContext.showInputDialog) {
        if (floatContext.showInputDialog) focusRequester.requestFocus()
    }

    Surface(
        modifier =
            Modifier
                .width(cardWidth)
                .wrapContentHeight()
                .heightIn(max = 260.dp)
                .padding(2.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 4.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragEnd = { floatContext.saveWindowState?.invoke() },
                                onDrag = { change, amount ->
                                    change.consume()
                                    floatContext.onMove(amount.x, amount.y, 1f)
                                },
                            )
                        }
                        .padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MiraCompanionAvatar(identity = identity, size = 30.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = identity.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(
                    onClick = {
                        floatContext.showInputDialog = false
                        floatContext.onModeChange(FloatingMode.BALL)
                    },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "收起")
                }
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("打开完整聊天") },
                            leadingIcon = { Icon(Icons.Default.OpenInFull, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                floatContext.openMiraMainChat()
                            },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("关闭悬浮") },
                            leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                floatContext.onClose()
                            },
                        )
                    }
                }
            }

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp, max = 88.dp)
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (transcript.isEmpty()) {
                    Text(
                        text = if (isProcessing) "正在整理回复..." else "还没有对话，先跟我说点什么。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    transcript.forEach { line ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(
                                text = if (line.sender == "user") "你" else identity.name,
                                modifier = Modifier.width(44.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color =
                                    if (line.sender == "user") {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = line.text,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp, max = 112.dp)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(
                    onClick = { floatContext.openMiraMainChat() },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = "添加照片、文件或记忆")
                }

                TextField(
                    value = floatContext.userMessage,
                    onValueChange = { floatContext.userMessage = it },
                    modifier =
                        Modifier
                            .weight(1f)
                            .heightIn(min = 52.dp, max = 112.dp)
                            .focusRequester(focusRequester)
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitFirstDown(pass = PointerEventPass.Initial)
                                    floatContext.showInputDialog = true
                                }
                            },
                    placeholder = { Text("和 ${identity.name} 说点什么……", maxLines = 1) },
                    maxLines = 2,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { floatContext.sendQuickReply() }),
                    shape = RoundedCornerShape(8.dp),
                    colors =
                        TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                )

                IconButton(
                    onClick = {
                        when {
                            isProcessing -> floatContext.onCancelMessage?.invoke()
                            floatContext.userMessage.isNotBlank() -> floatContext.sendQuickReply()
                            else -> floatContext.openMiraMainChat()
                        }
                    },
                    modifier = Modifier.size(48.dp),
                ) {
                    val icon =
                        when {
                            isProcessing -> Icons.Default.Stop
                            floatContext.userMessage.isNotBlank() -> Icons.AutoMirrored.Filled.Send
                            else -> Icons.Default.Mic
                        }
                    Icon(
                        imageVector = icon,
                        contentDescription =
                            when {
                                isProcessing -> "停止生成"
                                floatContext.userMessage.isNotBlank() -> "发送"
                                else -> "打开语音"
                            },
                        tint =
                            if (isProcessing || floatContext.userMessage.isNotBlank()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }
            }

        }
    }
}

private fun FloatContext.sendQuickReply() {
    val message = userMessage.trim()
    if (message.isBlank() && attachments.isEmpty()) return
    onSendMessage?.invoke(message, PromptFunctionType.CHAT)
    userMessage = ""
    chatService?.getChatCore()?.updateUserMessage("")
}

private fun FloatContext.openMiraMainChat() {
    val context = chatService ?: return
    val draft = userMessage
    coroutineScope.launch {
        runCatching {
            context.getChatCore().updateUserMessage(draft)
            context.getChatCore().syncCurrentChatIdToGlobal()
        }
        context.startActivity(
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
        )
        showInputDialog = false
        onModeChange(FloatingMode.BALL)
    }
}

private fun InputProcessingState.isActive(): Boolean =
    this !is InputProcessingState.Idle &&
        this !is InputProcessingState.Completed &&
        this !is InputProcessingState.Error
