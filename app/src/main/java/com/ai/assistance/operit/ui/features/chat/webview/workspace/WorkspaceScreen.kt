package com.ai.assistance.operit.ui.features.chat.webview.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.ui.features.chat.viewmodel.ChatViewModel
import com.ai.assistance.operit.ui.features.chat.webview.CapabilityOverlayHeader
import java.io.File

/**
 * 主工作区屏幕组件
 * 根据聊天状态显示不同的工作区界面
 */
@Composable
fun WorkspaceScreen(
    actualViewModel: ChatViewModel,
    currentChat: ChatHistory?,
    isVisible: Boolean,
    onExportClick: (workDir: File) -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
    ) {
        CapabilityOverlayHeader(
            title = stringResource(R.string.chat_workspace_status),
            onClose = onClose,
        )
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (currentChat?.workspace != null) {
                val workspacePath = currentChat.workspace
                WorkspaceManager(
                    actualViewModel = actualViewModel,
                    currentChat = currentChat,
                    workspacePath = workspacePath,
                    workspaceEnv = currentChat.workspaceEnv,
                    isVisible = isVisible,
                    onExportClick = onExportClick,
                )
            } else if (currentChat != null) {
                WorkspaceSetup(
                    chatId = currentChat.id,
                    onBindWorkspace = { workspacePath, workspaceEnv ->
                        actualViewModel.bindChatToWorkspace(currentChat.id, workspacePath, workspaceEnv)
                    },
                )
            } else {
                val context = LocalContext.current
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = context.getString(R.string.please_select_or_create_conversation),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
            }
        }
    }
}
