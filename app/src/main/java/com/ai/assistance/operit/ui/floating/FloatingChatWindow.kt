package com.ai.assistance.operit.ui.floating

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.AttachmentInfo
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.InputProcessingState
import com.ai.assistance.operit.data.model.PromptFunctionType
import com.ai.assistance.operit.services.FloatingChatService
import com.ai.assistance.operit.services.floating.MiraFloatingLayoutPolicy
import com.ai.assistance.operit.services.floating.FloatingWindowState
import com.ai.assistance.operit.ui.features.chat.components.ChatToastHost
import com.ai.assistance.operit.ui.floating.ui.ball.FloatingChatBallMode
import com.ai.assistance.operit.ui.floating.ui.ball.FloatingResultDisplay
import com.ai.assistance.operit.ui.floating.ui.ball.FloatingVoiceBallMode
import com.ai.assistance.operit.ui.floating.ui.fullscreen.FloatingFullscreenMode
import com.ai.assistance.operit.ui.floating.ui.screenocr.FloatingScreenOcrMode
import com.ai.assistance.operit.ui.floating.ui.MiraQuickReplyCard

/**
 * 悬浮聊天窗口的主要UI组件 - 重构版
 *
 * @param messages 要显示的聊天消息列表
 * @param width 窗口宽度
 * @param height 窗口高度
 * @param onClose 关闭窗口的回调
 * @param onResize 调整窗口大小的回调
 * @param ballSize 球的大小
 * @param windowScale 窗口缩放比例
 * @param onScaleChange 缩放比例变化的回调
 * @param currentMode 当前的显示模式 (窗口或球)
 * @param previousMode 上一个显示模式，用于回退
 * @param onModeChange 模式切换的回调
 * @param onMove 悬浮窗移动的回调，传递相对移动距离和当前缩放比例
 * @param snapToEdge 靠边收起的回调
 * @param isAtEdge 是否处于屏幕边缘
 * @param screenWidth 屏幕宽度参数，用于边界检测
 * @param screenHeight 屏幕高度参数，用于边界检测
 * @param currentX 当前窗口X坐标
 * @param currentY 当前窗口Y坐标
 * @param saveWindowState 保存窗口状态的回调
 * @param onSendMessage 发送消息的回调
 * @param onCancelMessage 取消消息的回调
 * @param onAttachmentRequest 附件请求回调
 * @param attachments 当前附件列表
 * @param onRemoveAttachment 删除附件回调
 * @param onInputFocusRequest 请求输入焦点的回调，参数为true时请求获取焦点，false时释放焦点
 * @param chatService 聊天服务实例，用于访问音频焦点管理器
 * @param windowState 窗口状态
 */
@Composable
fun FloatingChatWindow(
        messages: List<ChatMessage>,
        width: Dp,
        height: Dp,
        onClose: () -> Unit,
        onResize: (Dp, Dp) -> Unit,
        ballSize: Dp = 48.dp,
        windowScale: Float = 1.0f,
        onScaleChange: (Float) -> Unit = {},
        currentMode: FloatingMode = FloatingMode.WINDOW,
        previousMode: FloatingMode = FloatingMode.WINDOW,
        onModeChange: (FloatingMode) -> Unit = {},
        onMove: (Float, Float, Float) -> Unit = { _, _, _ -> },
        snapToEdge: (Boolean) -> Unit = { _ -> },
        isAtEdge: Boolean = false,
        screenWidth: Dp = 1080.dp,
        screenHeight: Dp = 2340.dp,
        currentX: Float = 0f,
        currentY: Float = 0f,
        saveWindowState: (() -> Unit)? = null,
        onSendMessage: ((String, PromptFunctionType) -> Unit)? = null,
        onSendMessageWithResult: ((String, PromptFunctionType, (Boolean) -> Unit) -> Boolean)? = null,
        onCancelMessage: (() -> Unit)? = null,
        onAttachmentRequest: ((String) -> Unit)? = null,
        attachments: List<AttachmentInfo> = emptyList(),
        onRemoveAttachment: ((String) -> Unit)? = null,
        onInputFocusRequest: ((Boolean) -> Unit)? = null,
        chatService: FloatingChatService? = null,
        windowState: FloatingWindowState? = null,
        inputProcessingState: State<InputProcessingState> = mutableStateOf(InputProcessingState.Idle)
) {
    val floatContext =
            rememberFloatContext(
                    messages = messages,
                    width = width,
                    height = height,
                    onClose = onClose,
                    onResize = onResize,
                    ballSize = ballSize,
                    windowScale = windowScale,
                    onScaleChange = onScaleChange,
                    currentMode = currentMode,
                    previousMode = previousMode,
                    onModeChange = onModeChange,
                    onMove = onMove,
                    snapToEdge = snapToEdge,
                    isAtEdge = isAtEdge,
                    screenWidth = screenWidth,
                    screenHeight = screenHeight,
                    currentX = currentX,
                    currentY = currentY,
                    saveWindowState = saveWindowState,
                    onSendMessage = onSendMessage,
                    onSendMessageWithResult = onSendMessageWithResult,
                    onCancelMessage = onCancelMessage,
                    onAttachmentRequest = onAttachmentRequest,
                    attachments = attachments,
                    onRemoveAttachment = onRemoveAttachment,
                    onInputFocusRequest = onInputFocusRequest,
                    chatService = chatService,
                    windowState = windowState,
                    inputProcessingState = inputProcessingState
            )
    val chatCore = remember(chatService) { chatService?.getChatCore() }
    val toastEventState =
        chatCore?.getUiStateDelegate()?.toastEvent?.collectAsState(initial = null)
            ?: remember { mutableStateOf<String?>(null) }
    val toastEvent by toastEventState
    val quickReplyWidth =
        MiraFloatingLayoutPolicy.cardWidthDp(screenWidth.value.toInt()).dp

    // 将窗口缩放限制在合理范围内 - 已通过回调和状态源头处理，不再需要
    // LaunchedEffect(initialWindowScale) {
    //     floatContext.windowScale = initialWindowScale.coerceIn(0.5f, 1.0f)
    // }

    // 监听输入状态变化
    LaunchedEffect(floatContext.showInputDialog) {
        // 通知服务需要切换焦点模式
        floatContext.onInputFocusRequest?.invoke(floatContext.showInputDialog)

        // 如果隐藏输入框，清空消息
        // Keep the floating draft so the full chat can receive it.
    }

    // 模式切换保持瞬时完成，悬浮窗不做展开、缩放或淡入淡出动画。
    Box {
        when (currentMode) {
            FloatingMode.WINDOW -> MiraQuickReplyCard(floatContext = floatContext)
            FloatingMode.BALL -> {
                when (previousMode) {
                    FloatingMode.VOICE_BALL -> FloatingVoiceBallMode(floatContext = floatContext)
                    else -> FloatingChatBallMode(floatContext = floatContext)
                }
            }
            FloatingMode.VOICE_BALL -> FloatingVoiceBallMode(floatContext = floatContext)
            FloatingMode.FULLSCREEN -> FloatingFullscreenMode(floatContext = floatContext)
            FloatingMode.RESULT_DISPLAY -> FloatingResultDisplay(floatContext = floatContext)
            FloatingMode.SCREEN_OCR -> FloatingScreenOcrMode(floatContext = floatContext)
        }

        ChatToastHost(
            message = toastEvent,
            onDismiss = { chatCore?.getUiStateDelegate()?.clearToastEvent() },
            modifier =
                if (currentMode == FloatingMode.WINDOW) {
                    Modifier
                        .width(quickReplyWidth)
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                },
            maxWidth = 360.dp,
            maxHeight = 200.dp
        )
    }
}
