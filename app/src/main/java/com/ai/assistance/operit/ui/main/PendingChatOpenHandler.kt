package com.ai.assistance.operit.ui.main

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object PendingChatOpenHandler {
    private val _pendingChatId = MutableStateFlow<String?>(null)
    val pendingChatId: StateFlow<String?> = _pendingChatId

    fun request(chatId: String) {
        _pendingChatId.value = chatId.trim().takeIf { it.isNotEmpty() }
    }

    fun clear() {
        _pendingChatId.value = null
    }
}
