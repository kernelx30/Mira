package com.ai.assistance.operit.core.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionMemorySensitiveDataGuardTest {
    @Test
    fun detectsCredentialsAndFinancialSecrets() {
        assertTrue(CompanionMemorySensitiveDataGuard.containsSensitiveData("密码是 123456"))
        assertTrue(CompanionMemorySensitiveDataGuard.containsSensitiveData("验证码 482913"))
        assertTrue(CompanionMemorySensitiveDataGuard.containsSensitiveData("api_key: sk-example-123456789"))
        assertTrue(CompanionMemorySensitiveDataGuard.containsSensitiveData("银行卡号 6222021234567890"))
    }

    @Test
    fun keepsOrdinaryDurableFacts() {
        assertFalse(CompanionMemorySensitiveDataGuard.containsSensitiveData("我对花生过敏"))
        assertFalse(CompanionMemorySensitiveDataGuard.containsSensitiveData("我最喜欢蓝色"))
    }
}
