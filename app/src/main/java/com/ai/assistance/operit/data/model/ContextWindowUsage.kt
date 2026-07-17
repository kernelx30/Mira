package com.ai.assistance.operit.data.model

import kotlin.math.floor

/** Token allocation for the exact request shape used by the current context window. */
data class ContextWindowUsage(
    val totalTokens: Long = 0L,
    val messageTokens: Long = 0L,
    val systemToolTokens: Long = 0L,
    val skillTokens: Long = 0L,
    val systemPromptTokens: Long = 0L,
    val otherTokens: Long = 0L,
    val updatedAtMillis: Long = 0L,
) {
    val classifiedTokens: Long
        get() =
            messageTokens +
                systemToolTokens +
                skillTokens +
                systemPromptTokens +
                otherTokens

    val hasBreakdown: Boolean
        get() = totalTokens > 0L && classifiedTokens == totalTokens

    companion object {
        val Empty = ContextWindowUsage()

        /**
         * Fits component estimates to the provider's complete request estimate. The component
         * weights come from the final assembled prompt; any unassigned framing cost stays in
         * [otherTokens].
         */
        fun fromRawEstimates(
            totalTokens: Long,
            messageTokens: Long,
            systemToolTokens: Long,
            skillTokens: Long,
            systemPromptTokens: Long,
            otherTokens: Long = 0L,
            updatedAtMillis: Long = 0L,
        ): ContextWindowUsage {
            val safeTotal = totalTokens.coerceAtLeast(0L)
            if (safeTotal == 0L) return Empty.copy(updatedAtMillis = updatedAtMillis)

            val raw =
                longArrayOf(
                    messageTokens.coerceAtLeast(0L),
                    systemToolTokens.coerceAtLeast(0L),
                    skillTokens.coerceAtLeast(0L),
                    systemPromptTokens.coerceAtLeast(0L),
                    otherTokens.coerceAtLeast(0L),
                )
            val rawTotal = raw.sum()
            if (rawTotal == 0L) {
                return ContextWindowUsage(
                    totalTokens = safeTotal,
                    otherTokens = safeTotal,
                    updatedAtMillis = updatedAtMillis,
                )
            }

            val allocated =
                if (rawTotal <= safeTotal) {
                    raw.copyOf().also { values ->
                        values[4] += safeTotal - rawTotal
                    }
                } else {
                    val exact = raw.map { it.toDouble() * safeTotal.toDouble() / rawTotal.toDouble() }
                    val values = LongArray(raw.size) { index -> floor(exact[index]).toLong() }
                    var remaining = safeTotal - values.sum()
                    exact.indices
                        .sortedWith(
                            compareByDescending<Int> { exact[it] - values[it].toDouble() }
                                .thenBy { it },
                        )
                        .forEach { index ->
                            if (remaining > 0L) {
                                values[index] += 1L
                                remaining -= 1L
                            }
                        }
                    values
                }

            return ContextWindowUsage(
                totalTokens = safeTotal,
                messageTokens = allocated[0],
                systemToolTokens = allocated[1],
                skillTokens = allocated[2],
                systemPromptTokens = allocated[3],
                otherTokens = allocated[4],
                updatedAtMillis = updatedAtMillis,
            )
        }
    }
}
