package com.ai.assistance.operit.data.model

data class ChatTurnOptions(
    val persistTurn: Boolean = true,
    val notifyReply: Boolean? = null,
    val hideUserMessage: Boolean = false,
    val disableWarning: Boolean = false,
    val autoReadOverride: Boolean? = null,
    val memoryAutoUpdateOverride: Boolean? = null,
    val toolsEnabledOverride: Boolean? = null,
    val requireToolExecution: Boolean = false,
    val consumeUserDraftAfterPersist: Boolean = false,
)
