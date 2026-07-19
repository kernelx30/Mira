package com.ai.assistance.operit.core.chat

import android.content.Context
import androidx.room.withTransaction
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.preferences.CharacterCardManager

object CompanionMemoryTargetResolver {
    fun snapshotForTurn(characterGroupId: String?, roleCardId: String?): String {
        val groupId = characterGroupId?.trim().takeIf { !it.isNullOrBlank() }
        if (groupId != null) return "group:$groupId"
        val cardId = roleCardId?.trim().takeIf { !it.isNullOrBlank() }
        return cardId?.let { "character:$it" }.orEmpty()
    }

    suspend fun resolve(context: Context, chatId: String): String {
        if (chatId.isBlank()) return ""
        val database = AppDatabase.getDatabase(context.applicationContext)
        val chat = database.chatDao().getChatById(chatId)
            ?: return ""
        chat.characterGroupId?.trim()?.takeIf { it.isNotBlank() }?.let { groupId ->
            val canonicalId = "group:$groupId"
            database.withTransaction {
                val dao = database.companionMemoryDao()
                dao.bindUnresolvedConversationMemories(chatId, canonicalId)
                dao.bindUnresolvedConversationEpisodes(chatId, canonicalId)
            }
            return canonicalId
        }

        val cardName = chat.characterCardName?.trim()?.takeIf { it.isNotBlank() }
        val cardManager = CharacterCardManager.getInstance(context.applicationContext)
        val cardId =
            if (cardName == null) {
                CharacterCardManager.DEFAULT_CHARACTER_CARD_ID
            } else {
                cardManager.findCharacterCardByName(cardName)?.id
                    ?: return "character_name:$cardName"
            }
        val canonicalId = "character:$cardId"
        database.withTransaction {
            val dao = database.companionMemoryDao()
            dao.bindUnresolvedConversationMemories(chatId, canonicalId)
            dao.bindUnresolvedConversationEpisodes(chatId, canonicalId)
            if (cardName != null) {
                val aliasId = "character_name:$cardName"
                dao.migrateCompanionAliasInRecords(aliasId, canonicalId)
                dao.migrateCompanionAliasInEpisodes(aliasId, canonicalId)
                dao.deleteDuplicateCompanionAliasGrants(aliasId, canonicalId)
                dao.migrateCompanionAliasInGrants(aliasId, canonicalId)
            }
        }
        return canonicalId
    }
}
