package com.ai.assistance.operit.data.skill

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class ChatSkillActivationStore private constructor(context: Context) {
    companion object {
        private const val PREFS_NAME = "chat_skill_activations"
        private const val KEY_PREFIX = "active_skills:"

        @Volatile
        private var INSTANCE: ChatSkillActivationStore? = null

        fun getInstance(context: Context): ChatSkillActivationStore {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ChatSkillActivationStore(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }

    private val preferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val revision = MutableStateFlow(0L)

    fun observeActiveSkills(chatId: String?): Flow<List<String>> {
        val normalizedChatId = chatId?.trim().orEmpty()
        if (normalizedChatId.isBlank()) return flowOf(emptyList())

        return revision
            .map {
                preferences
                    .getStringSet(keyForChat(normalizedChatId), emptySet())
                    .orEmpty()
                    .toList()
                    .sortedWith(String.CASE_INSENSITIVE_ORDER)
            }
            .distinctUntilChanged()
    }

    fun markActive(chatId: String?, skillName: String?) {
        val normalizedChatId = chatId?.trim().orEmpty()
        val normalizedSkillName = skillName?.trim().orEmpty()
        if (normalizedChatId.isBlank() || normalizedSkillName.isBlank()) return

        val key = keyForChat(normalizedChatId)
        val updated = preferences.getStringSet(key, emptySet()).orEmpty().toMutableSet()
        updated.removeAll { it.equals(normalizedSkillName, ignoreCase = true) }
        updated += normalizedSkillName
        preferences.edit().putStringSet(key, updated).apply()
        revision.value += 1
    }

    fun clear(chatId: String?) {
        val normalizedChatId = chatId?.trim().orEmpty()
        if (normalizedChatId.isBlank()) return
        val key = keyForChat(normalizedChatId)
        if (!preferences.contains(key)) return
        preferences.edit().remove(key).apply()
        revision.value += 1
    }

    private fun keyForChat(chatId: String): String = "$KEY_PREFIX$chatId"
}
