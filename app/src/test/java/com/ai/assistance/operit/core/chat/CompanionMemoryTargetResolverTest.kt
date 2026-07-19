package com.ai.assistance.operit.core.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class CompanionMemoryTargetResolverTest {
    @Test
    fun groupSnapshotTakesPriorityOverMemberCard() {
        assertEquals(
            "group:friends",
            CompanionMemoryTargetResolver.snapshotForTurn("friends", "member-a"),
        )
    }

    @Test
    fun directChatSnapshotUsesExactRoleCardId() {
        assertEquals(
            "character:zero",
            CompanionMemoryTargetResolver.snapshotForTurn(null, "zero"),
        )
    }

    @Test
    fun missingBindingDoesNotInventATarget() {
        assertEquals("", CompanionMemoryTargetResolver.snapshotForTurn(null, null))
    }
}
