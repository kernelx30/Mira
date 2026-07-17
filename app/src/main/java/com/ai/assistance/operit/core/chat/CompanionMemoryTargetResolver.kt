package com.ai.assistance.operit.core.chat

import android.content.Context
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.preferences.CharacterCardManager

object CompanionMemoryTargetResolver {
    suspend fun resolve(context: Context, chatId: String): String {
        if (chatId.isBlank()) return ""
        val chat = AppDatabase.getDatabase(context.applicationContext).chatDao().getChatById(chatId)
            ?: return ""
        chat.characterGroupId?.takeIf { it.isNotBlank() }?.let { return "group:$it" }

        val cardName = chat.characterCardName?.takeIf { it.isNotBlank() } ?: return ""
        val cardId =
            CharacterCardManager.getInstance(context.applicationContext)
                .findCharacterCardByName(cardName)
                ?.id
        return cardId?.takeIf { it.isNotBlank() }?.let { "character:$it" }
            ?: "character_name:$cardName"
    }
}
