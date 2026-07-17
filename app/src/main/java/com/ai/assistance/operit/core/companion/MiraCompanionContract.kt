package com.ai.assistance.operit.core.companion

object MiraCompanionContract {
    const val ACTION_DELIVER = "com.ai.assistance.mira.action.DELIVER_COMPANION_EVENT"
    const val ACTION_OPEN_REMINDER = "com.ai.assistance.mira.action.OPEN_COMPANION_REMINDER"
    const val ACTION_SYNC = "com.ai.assistance.mira.action.SYNC_COMPANION_EVENTS"
    const val ACTION_DISABLE_KEEP_ALIVE =
        "com.ai.assistance.mira.action.DISABLE_COMPANION_KEEP_ALIVE"

    const val EXTRA_PROFILE_ID = "companion_profile_id"
    const val EXTRA_MEMORY_UUID = "companion_memory_uuid"
    const val EXTRA_CHAT_ID = "companion_reminder_chat_id"

    const val PROMPT_MARKER_PREFIX = "[MIRA_PROACTIVE_EVENT:"

    fun promptMarker(memoryUuid: String): String = "$PROMPT_MARKER_PREFIX$memoryUuid]"
}
