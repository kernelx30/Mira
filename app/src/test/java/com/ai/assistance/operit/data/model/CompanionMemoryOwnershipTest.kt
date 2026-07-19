package com.ai.assistance.operit.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionMemoryOwnershipTest {
    @Test
    fun `structured companion id prefers group then character then character name`() {
        assertEquals(
            "group:friends",
            CompanionMemoryTarget(
                characterGroupId = "friends",
                characterId = "mira",
                characterName = "Mira",
            ).structuredCompanionId(),
        )
        assertEquals(
            "character:mira",
            CompanionMemoryTarget(characterId = "mira", characterName = "Mira")
                .structuredCompanionId(),
        )
        assertEquals(
            "character_name:Mira",
            CompanionMemoryTarget(characterName = "Mira").structuredCompanionId(),
        )
        assertEquals("", CompanionMemoryTarget().structuredCompanionId())
    }

    @Test
    fun `property values round trip preserves relationship owner`() {
        val ownership =
            CompanionMemoryOwnership.manual(
                scope = CompanionMemoryScope.RELATIONSHIP,
                profileId = "personal",
                target =
                    CompanionMemoryTarget(
                        characterId = "zero",
                        characterName = "Zero",
                    ),
            )

        assertEquals(
            ownership,
            CompanionMemoryOwnership.fromPropertyValues(ownership.toPropertyValues()),
        )
    }

    @Test
    fun `user memory is shared across character targets in the same profile`() {
        val ownership =
            CompanionMemoryOwnership.manual(
                scope = CompanionMemoryScope.USER,
                profileId = "personal",
                target = CompanionMemoryTarget(characterId = "zero", characterName = "Zero"),
            )

        assertTrue(ownership.belongsToProfile("personal"))
        assertTrue(ownership.belongsToTarget(CompanionMemoryTarget(characterId = "mira")))
        assertEquals("", ownership.characterId)
    }

    @Test
    fun `relationship memory only matches its bound character`() {
        val ownership =
            CompanionMemoryOwnership.manual(
                scope = CompanionMemoryScope.RELATIONSHIP,
                profileId = "personal",
                target = CompanionMemoryTarget(characterId = "zero", characterName = "Zero"),
            )

        assertTrue(ownership.belongsToTarget(CompanionMemoryTarget(characterId = "zero")))
        assertFalse(ownership.belongsToTarget(CompanionMemoryTarget(characterId = "mira")))
        assertTrue(ownership.canRecall("personal", CompanionMemoryTarget(characterId = "zero")))
        assertFalse(ownership.canRecall("work", CompanionMemoryTarget(characterId = "zero")))
        assertFalse(ownership.canRecall("personal", CompanionMemoryTarget(characterId = "mira")))
    }
}
