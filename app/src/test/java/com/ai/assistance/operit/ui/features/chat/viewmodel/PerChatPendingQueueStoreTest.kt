package com.ai.assistance.operit.ui.features.chat.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PerChatPendingQueueStoreTest {
    @Test
    fun keepsQueuesSeparatedWhenChatsSwitch() {
        val store = PerChatPendingQueueStore()
        val firstA = store.enqueue("chat-a", "a1")
        val firstB = store.enqueue("chat-b", "b1")
        val secondA = store.enqueue("chat-a", "a2")

        assertEquals(listOf(firstA, secondA), store.itemsByChatId.value["chat-a"])
        assertEquals(listOf(firstB), store.itemsByChatId.value["chat-b"])
    }

    @Test
    fun restoresRejectedItemToItsOriginalChat() {
        val store = PerChatPendingQueueStore()
        val item = store.enqueue("chat-a", "queued")

        assertEquals(item, store.remove("chat-a", item.id))
        assertNull(store.itemsByChatId.value["chat-a"])

        store.restore(item)
        store.restore(item)
        assertEquals(listOf(item), store.itemsByChatId.value["chat-a"])
        assertNull(store.itemsByChatId.value["chat-b"])
    }
}
