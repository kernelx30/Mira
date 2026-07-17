package com.ai.assistance.operit.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionMemoryPredicateTest {
    @Test
    fun canonicalizesCommonModelAliases() {
        assertEquals("occupation", CompanionMemoryPredicate.canonicalize("work_role"))
        assertEquals("location", CompanionMemoryPredicate.canonicalize("Residence"))
        assertEquals("dislikes", CompanionMemoryPredicate.canonicalize("food.dislikes"))
        assertEquals("health.allergy", CompanionMemoryPredicate.canonicalize("allergies"))
    }

    @Test
    fun distinguishesSingletonAndSetPredicates() {
        assertTrue(CompanionMemoryPredicate.isSingleton("career"))
        assertTrue(CompanionMemoryPredicate.isSingleton("family.mother.name"))
        assertFalse(CompanionMemoryPredicate.isSingleton("likes"))
        assertTrue(CompanionMemoryPredicate.isOppositePreference("likes", "food.dislikes"))
    }
}
