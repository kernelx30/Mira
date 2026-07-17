package com.ai.assistance.operit.ui.features.chat.webview.computer

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.R
import com.ai.assistance.operit.terminal.TerminalManager
import com.ai.assistance.operit.terminal.rememberTerminalEnv
import com.ai.assistance.operit.terminal.main.TerminalScreen
import com.ai.assistance.operit.ui.features.chat.webview.CapabilityOverlayHeader

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun ComputerScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    
    // Create a TerminalManager and TerminalEnv instance for the terminal
    val terminalManager = remember { TerminalManager.getInstance(context) }
    val terminalEnv = rememberTerminalEnv(terminalManager)
    
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
    ) {
        CapabilityOverlayHeader(
            title = stringResource(R.string.ai_computer),
            onClose = onClose,
        )
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxSize()
                    // 拦截空白区域触摸，避免事件穿透到下层聊天界面。
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {},
                            onDoubleTap = {},
                            onLongPress = {},
                            onPress = {},
                        )
                    },
        ) {
            TerminalScreen(
                env = terminalEnv,
                useLocalImeHandling = false,
                checkUpdatesOnEnter = false,
            )
        }
    }
}
