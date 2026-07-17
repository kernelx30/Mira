package com.ai.assistance.operit.ui.features.chat.viewmodel

import com.ai.assistance.operit.data.model.PendingQueueMessageItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class PerChatPendingQueueStore {
    private val _itemsByChatId =
        MutableStateFlow<Map<String, List<PendingQueueMessageItem>>>(emptyMap())
    val itemsByChatId: StateFlow<Map<String, List<PendingQueueMessageItem>>> =
        _itemsByChatId.asStateFlow()

    private var nextId = 1L

    @Synchronized
    fun enqueue(chatId: String, text: String): PendingQueueMessageItem {
        require(chatId.isNotBlank())
        val item = PendingQueueMessageItem(id = nextId++, chatId = chatId, text = text)
        val updated = _itemsByChatId.value.toMutableMap()
        updated[chatId] = updated[chatId].orEmpty() + item
        _itemsByChatId.value = updated
        return item
    }

    @Synchronized
    fun remove(chatId: String, id: Long): PendingQueueMessageItem? {
        val currentItems = _itemsByChatId.value[chatId].orEmpty()
        val item = currentItems.firstOrNull { it.id == id } ?: return null
        val remaining = currentItems.filterNot { it.id == id }
        val updated = _itemsByChatId.value.toMutableMap()
        if (remaining.isEmpty()) {
            updated.remove(chatId)
        } else {
            updated[chatId] = remaining
        }
        _itemsByChatId.value = updated
        return item
    }

    @Synchronized
    fun restore(item: PendingQueueMessageItem) {
        val currentItems = _itemsByChatId.value[item.chatId].orEmpty()
        if (currentItems.any { it.id == item.id }) return
        val updated = _itemsByChatId.value.toMutableMap()
        updated[item.chatId] = listOf(item) + currentItems
        _itemsByChatId.value = updated
    }
}
