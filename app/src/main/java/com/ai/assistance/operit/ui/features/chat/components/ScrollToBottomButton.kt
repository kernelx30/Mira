package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.lazy.LazyListState as ComposeLazyListState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.chat.components.lazy.LazyListState as ChatLazyListState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * 滚动到底部按钮组件
 * 
 * @param scrollState 滚动状态
 * @param coroutineScope 协程作用域
 * @param autoScrollToBottom 是否自动滚动到底部
 * @param hasNewerDisplayHistory 当前窗口下方是否还有可切换到的更新内容
 * @param onAutoScrollToBottomChange 自动滚动状态变化回调
 * @param modifier 修饰符
 */
@Composable
fun ScrollToBottomButton(
    scrollState: ScrollState,
    coroutineScope: CoroutineScope,
    autoScrollToBottom: Boolean,
    hasNewerDisplayHistory: Boolean = false,
    onRequestLatestMessages: (() -> Unit)? = null,
    onAutoScrollToBottomChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var showScrollButton by remember { mutableStateOf(false) }
    val isDragged = scrollState.interactionSource.collectIsDraggedAsState()
    val currentAutoScrollToBottom by rememberUpdatedState(autoScrollToBottom)
    val currentHasNewerDisplayHistory by rememberUpdatedState(hasNewerDisplayHistory)
    val currentOnAutoScrollToBottomChange by rememberUpdatedState(onAutoScrollToBottomChange)

    LaunchedEffect(scrollState) {
        var lastPosition = scrollState.value
        snapshotFlow { scrollState.value }
            .distinctUntilChanged()
            .collect { currentPosition ->
                if (scrollState.isScrollInProgress) {
                    val scrolledUp = currentPosition < lastPosition
                    if (scrolledUp) {
                        if (currentAutoScrollToBottom && isDragged.value) {
                            currentOnAutoScrollToBottomChange(false)
                            showScrollButton = true
                        }
                    } else {
                        val isAtBottom =
                            scrollState.value >= scrollState.maxValue &&
                                !currentHasNewerDisplayHistory
                        if (isAtBottom && !currentAutoScrollToBottom) {
                            currentOnAutoScrollToBottomChange(true)
                            showScrollButton = false
                        }
                    }
                }
                lastPosition = currentPosition
            }
    }

    ScrollToBottomButtonContent(
        visible =
            shouldShowScrollToBottomButton(
                autoScrollToBottom = autoScrollToBottom,
                gestureRequestedButton = showScrollButton,
                hasNewerDisplayHistory = hasNewerDisplayHistory,
            ),
        modifier = modifier,
        onClick = {
            coroutineScope.launch {
                if (hasNewerDisplayHistory) {
                    onRequestLatestMessages?.invoke()
                }
                scrollState.animateScrollTo(scrollState.maxValue)
            }
            onAutoScrollToBottomChange(true)
            showScrollButton = false
        },
    )
}

@Composable
fun ScrollToBottomButton(
    scrollState: ChatLazyListState,
    coroutineScope: CoroutineScope,
    autoScrollToBottom: Boolean,
    onAutoScrollToBottomChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var showScrollButton by remember { mutableStateOf(false) }
    val isDragged = scrollState.interactionSource.collectIsDraggedAsState()
    val currentAutoScrollToBottom by rememberUpdatedState(autoScrollToBottom)
    val currentOnAutoScrollToBottomChange by rememberUpdatedState(onAutoScrollToBottomChange)

    // Effect 生命周期只绑定滚动状态，动态参数通过 rememberUpdatedState 保持实时。
    LaunchedEffect(scrollState) {
        var lastIndex = scrollState.firstVisibleItemIndex
        var lastOffset = scrollState.firstVisibleItemScrollOffset
        snapshotFlow {
            Triple(
                scrollState.firstVisibleItemIndex,
                scrollState.firstVisibleItemScrollOffset,
                scrollState.canScrollForward,
            )
        }
            .distinctUntilChanged()
            .collect { (currentIndex, currentOffset, _) ->
                if (scrollState.isScrollInProgress) {
                    val scrolledUp =
                        currentIndex < lastIndex ||
                            (currentIndex == lastIndex && currentOffset < lastOffset)
                    if (scrolledUp) {
                        if (currentAutoScrollToBottom && isDragged.value) {
                            currentOnAutoScrollToBottomChange(false)
                            showScrollButton = true
                        }
                    } else {
                        val isAtBottom = scrollState.isAtBottom()
                        if (isAtBottom && !currentAutoScrollToBottom) {
                            currentOnAutoScrollToBottomChange(true)
                            showScrollButton = false
                        }
                    }
                }
                lastIndex = currentIndex
                lastOffset = currentOffset
            }
    }

    ScrollToBottomButtonContent(
        visible =
            shouldShowScrollToBottomButton(
                autoScrollToBottom = autoScrollToBottom,
                gestureRequestedButton = showScrollButton,
            ),
        modifier = modifier,
        onClick = {
            coroutineScope.launch {
                scrollState.animateScrollToEnd()
            }
            onAutoScrollToBottomChange(true)
            showScrollButton = false
        },
    )
}

@Composable
fun ScrollToBottomButton(
    scrollState: ComposeLazyListState,
    coroutineScope: CoroutineScope,
    autoScrollToBottom: Boolean,
    hasNewerDisplayHistory: Boolean = false,
    onRequestLatestMessages: (() -> Unit)? = null,
    reverseLayout: Boolean = false,
    onAutoScrollToBottomChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var showScrollButton by remember { mutableStateOf(false) }
    val isDragged = scrollState.interactionSource.collectIsDraggedAsState()
    val currentAutoScrollToBottom by rememberUpdatedState(autoScrollToBottom)
    val currentHasNewerDisplayHistory by rememberUpdatedState(hasNewerDisplayHistory)
    val currentReverseLayout by rememberUpdatedState(reverseLayout)
    val currentOnAutoScrollToBottomChange by rememberUpdatedState(onAutoScrollToBottomChange)

    LaunchedEffect(scrollState) {
        var lastIndex = scrollState.firstVisibleItemIndex
        var lastOffset = scrollState.firstVisibleItemScrollOffset
        snapshotFlow {
            Triple(
                scrollState.firstVisibleItemIndex,
                scrollState.firstVisibleItemScrollOffset,
                scrollState.canScrollForward,
            )
        }
            .distinctUntilChanged()
            .collect { (currentIndex, currentOffset, _) ->
                if (scrollState.isScrollInProgress) {
                    val movedAwayFromBottom =
                        if (currentReverseLayout) {
                            currentIndex > lastIndex ||
                                (currentIndex == lastIndex && currentOffset > lastOffset)
                        } else {
                            currentIndex < lastIndex ||
                                (currentIndex == lastIndex && currentOffset < lastOffset)
                        }
                    if (movedAwayFromBottom) {
                        if (currentAutoScrollToBottom && isDragged.value) {
                            currentOnAutoScrollToBottomChange(false)
                            showScrollButton = true
                        }
                    } else {
                        val isAtBottom =
                            scrollState.isAtBottom(reverseLayout = currentReverseLayout) &&
                                !currentHasNewerDisplayHistory
                        if (isAtBottom && !currentAutoScrollToBottom) {
                            currentOnAutoScrollToBottomChange(true)
                            showScrollButton = false
                        }
                    }
                }
                lastIndex = currentIndex
                lastOffset = currentOffset
            }
    }

    ScrollToBottomButtonContent(
        visible =
            shouldShowScrollToBottomButton(
                autoScrollToBottom = autoScrollToBottom,
                gestureRequestedButton = showScrollButton,
                hasNewerDisplayHistory = hasNewerDisplayHistory,
            ),
        modifier = modifier,
        onClick = {
            coroutineScope.launch {
                if (hasNewerDisplayHistory) {
                    onRequestLatestMessages?.invoke()
                }
                if (reverseLayout) {
                    scrollState.animateScrollToItem(0)
                } else {
                    scrollState.animateScrollToEnd()
                }
            }
            onAutoScrollToBottomChange(true)
            showScrollButton = false
        },
    )
}

@Composable
private fun ScrollToBottomButtonContent(
    visible: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(50)
                )
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(R.string.history_scroll_to_bottom),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

internal fun shouldShowScrollToBottomButton(
    autoScrollToBottom: Boolean,
    gestureRequestedButton: Boolean,
    hasNewerDisplayHistory: Boolean = false,
): Boolean = gestureRequestedButton || !autoScrollToBottom || hasNewerDisplayHistory

private fun ChatLazyListState.isAtBottom(): Boolean {
    val layoutInfo = layoutInfo
    if (layoutInfo.totalItemsCount == 0) {
        return true
    }
    val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return false
    return !canScrollForward && lastVisibleItemIndex >= layoutInfo.totalItemsCount - 1
}

private fun ComposeLazyListState.isAtBottom(reverseLayout: Boolean = false): Boolean {
    val layoutInfo = layoutInfo
    if (layoutInfo.totalItemsCount == 0) {
        return true
    }
    return if (reverseLayout) {
        val firstVisibleItemIndex = layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: return false
        !canScrollBackward && firstVisibleItemIndex <= 0
    } else {
        val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return false
        !canScrollForward && lastVisibleItemIndex >= layoutInfo.totalItemsCount - 1
    }
}
