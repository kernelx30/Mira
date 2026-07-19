package com.ai.assistance.operit.core.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionMemoryEvidenceValidatorTest {
    @Test
    fun acceptsAtomicValueCopiedFromDirectEvidence() {
        assertTrue(CompanionMemoryEvidenceValidator.supportsValue("我平时不吃香菜", "不吃香菜"))
        assertTrue(CompanionMemoryEvidenceValidator.supportsValue("我喜欢玩原神", "原神"))
        assertTrue(CompanionMemoryEvidenceValidator.supportsValue("我有点怕生，熟悉后会放松", "用户有点怕生"))
        assertTrue(CompanionMemoryEvidenceValidator.supportsValue("我是武陟的", "用户来自武陟"))
        assertTrue(CompanionMemoryEvidenceValidator.supportsValue("我家在上海", "来自上海"))
    }

    @Test
    fun rejectsValueThatDropsOrReversesEvidenceNegation() {
        assertFalse(CompanionMemoryEvidenceValidator.supportsValue("我不吃香菜", "吃香菜"))
        assertFalse(CompanionMemoryEvidenceValidator.supportsValue("我没有养猫", "养猫"))
        assertFalse(CompanionMemoryEvidenceValidator.supportsValue("I don't like cilantro", "like cilantro"))
        assertFalse(CompanionMemoryEvidenceValidator.supportsValue("我不吃香菜", "我喜欢香菜"))
        assertFalse(CompanionMemoryEvidenceValidator.supportsValue("我不是不吃香菜", "不吃香菜"))
        assertFalse(CompanionMemoryEvidenceValidator.supportsValue("我并非喜欢香菜", "喜欢香菜"))
        assertFalse(CompanionMemoryEvidenceValidator.supportsValue("我从未养猫", "养猫"))
        assertFalse(CompanionMemoryEvidenceValidator.supportsValue("我不来自武陟", "来自武陟"))
    }
}
