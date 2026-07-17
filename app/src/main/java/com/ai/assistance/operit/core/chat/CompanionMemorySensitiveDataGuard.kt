package com.ai.assistance.operit.core.chat

object CompanionMemorySensitiveDataGuard {
    private val sensitivePatterns =
        listOf(
            Regex("(?:密码|口令|验证码|支付密码|取款密码|银行卡密码|私钥|助记词|密钥)\\s*(?:是|为|[:：])", RegexOption.IGNORE_CASE),
            Regex("(?:验证码|otp)\\s*[:：]?\\s*\\d{4,8}\\b", RegexOption.IGNORE_CASE),
            Regex("(?:password|passcode|verification\\s*code|otp|api[_ -]?key|access[_ -]?token|refresh[_ -]?token|private[_ -]?key|seed[_ -]?phrase)\\s*(?:is|是|为|=|[:：])", RegexOption.IGNORE_CASE),
            Regex("-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----", RegexOption.IGNORE_CASE),
            Regex("\\b(?:sk|pk|rk)-[A-Za-z0-9_-]{12,}\\b", RegexOption.IGNORE_CASE),
            Regex("(?:银行卡号|信用卡号|借记卡号|card\\s*(?:number|no\\.?))\\s*(?:是|为|is|=|[:：])?\\s*\\d{12,19}", RegexOption.IGNORE_CASE),
            Regex("(?:cvv|cvc|安全码)\\s*(?:是|为|is|=|[:：])?\\s*\\d{3,4}", RegexOption.IGNORE_CASE),
        )

    fun containsSensitiveData(value: String): Boolean {
        if (value.isBlank()) return false
        return sensitivePatterns.any { it.containsMatchIn(value) }
    }
}
