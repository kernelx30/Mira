package com.ai.assistance.operit.api.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DoubaoSeedTts2VoicesTest {
    @Test
    fun officialVoiceListContainsExpectedCompanionVoicesWithoutDuplicateIds() {
        val voices = DoubaoSeedTts2Voices.all

        assertEquals(292, voices.size)
        assertEquals(voices.size, voices.map { it.id }.distinct().size)
        assertTrue(voices.any { it.name == "娇喘女声 2.0" })
        assertTrue(voices.any { it.name == "撒娇学妹 2.0" })
    }
}
