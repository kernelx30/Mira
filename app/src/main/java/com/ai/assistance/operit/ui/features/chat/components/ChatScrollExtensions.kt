package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState as ComposeLazyListState
import com.ai.assistance.operit.ui.features.chat.components.lazy.LazyListState

/**
 * Scrolls to the actual end of the list so the trailing edge of the final item stays aligned with
 * the viewport's content end instead of its start.
 */
suspend fun LazyListState.animateScrollToEnd() {
    val lastIndex = layoutInfo.totalItemsCount - 1
    if (lastIndex < 0) {
        return
    }

    animateScrollToItem(lastIndex)

    val updatedLayoutInfo = layoutInfo
    val targetItemInfo =
        updatedLayoutInfo.visibleItemsInfo.lastOrNull { it.index == lastIndex } ?: return
    val contentViewportEnd =
        updatedLayoutInfo.viewportEndOffset - updatedLayoutInfo.afterContentPadding
    val targetItemEnd = targetItemInfo.offset + targetItemInfo.size
    val deltaToViewportEnd = (targetItemEnd - contentViewportEnd).toFloat()
    if (deltaToViewportEnd < 0f && !canScrollBackward) {
        return
    }

    if (kotlin.math.abs(deltaToViewportEnd) > 0.5f) {
        animateScrollBy(deltaToViewportEnd)
    }
}

suspend fun ComposeLazyListState.animateScrollToEnd() {
    val lastIndex = layoutInfo.totalItemsCount - 1
    if (lastIndex < 0) {
        return
    }

    animateScrollToItem(lastIndex)

    val updatedLayoutInfo = layoutInfo
    val targetItemInfo =
        updatedLayoutInfo.visibleItemsInfo.lastOrNull { it.index == lastIndex } ?: return
    val contentViewportEnd =
        updatedLayoutInfo.viewportEndOffset - updatedLayoutInfo.afterContentPadding
    val targetItemEnd = targetItemInfo.offset + targetItemInfo.size
    val deltaToViewportEnd = (targetItemEnd - contentViewportEnd).toFloat()
    if (deltaToViewportEnd < 0f && !canScrollBackward) {
        return
    }

    if (kotlin.math.abs(deltaToViewportEnd) > 0.5f) {
        animateScrollBy(deltaToViewportEnd)
    }
}

/** Keeps streaming content pinned to the end without restarting an animation for every chunk. */
suspend fun ComposeLazyListState.scrollToEnd() {
    val lastIndex = layoutInfo.totalItemsCount - 1
    if (lastIndex < 0) {
        return
    }

    scrollToItem(lastIndex)

    val updatedLayoutInfo = layoutInfo
    val targetItemInfo =
        updatedLayoutInfo.visibleItemsInfo.lastOrNull { it.index == lastIndex } ?: return
    val contentViewportEnd =
        updatedLayoutInfo.viewportEndOffset - updatedLayoutInfo.afterContentPadding
    val targetItemEnd = targetItemInfo.offset + targetItemInfo.size
    val deltaToViewportEnd = (targetItemEnd - contentViewportEnd).toFloat()
    if (deltaToViewportEnd < 0f && !canScrollBackward) {
        return
    }

    if (kotlin.math.abs(deltaToViewportEnd) > 0.5f) {
        scrollBy(deltaToViewportEnd)
    }
}
