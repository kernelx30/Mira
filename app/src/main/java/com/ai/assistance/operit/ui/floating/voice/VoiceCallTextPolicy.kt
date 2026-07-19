package com.ai.assistance.operit.ui.floating.voice

object VoiceCallTextPolicy {
    fun silenceDelayMs(
        text: String,
        isFinal: Boolean,
        configuredMs: Long,
    ): Long {
        val base = configuredMs.coerceIn(700L, 4_000L)
        val normalized = text.trim()
        return when {
            isFinal && normalized.lastOrNull() in TERMINAL_PUNCTUATION -> (base * 0.58f).toLong()
            normalized.length <= 3 -> (base * 1.35f).toLong()
            normalized.lastOrNull() in CONTINUATION_PUNCTUATION -> (base * 1.28f).toLong()
            normalized.length >= 40 -> (base * 1.15f).toLong()
            else -> base
        }.coerceIn(650L, 5_000L)
    }

    fun isLikelyPlaybackEcho(recognizedText: String, spokenText: String): Boolean {
        val recognized = normalize(recognizedText)
        val spoken = normalize(spokenText)
        if (recognized.length < 4 || spoken.length < 4) return false
        if (spoken.contains(recognized)) return true
        if (recognized.contains(spoken) && spoken.length >= 6) return true

        val prefixLength = minOf(recognized.length, spoken.length, 16)
        if (prefixLength < 6) return false
        val matchingPrefix =
            (0 until prefixLength).count { index -> recognized[index] == spoken[index] }
        return matchingPrefix.toFloat() / prefixLength >= 0.78f
    }

    private fun normalize(text: String): String =
        text.lowercase().filter { it.isLetterOrDigit() }

    private val TERMINAL_PUNCTUATION = setOf('。', '！', '？', '.', '!', '?')
    private val CONTINUATION_PUNCTUATION = setOf('，', '、', ',', '；', ';', '：', ':')
}
