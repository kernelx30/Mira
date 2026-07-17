package com.ai.assistance.operit.core.chat

import com.ai.assistance.operit.data.model.CompanionMemoryRecordEntity
import com.ai.assistance.operit.data.model.CompanionMemorySourceKind
import com.ai.assistance.operit.data.model.CompanionMemoryType
import com.ai.assistance.operit.data.model.CompanionRecordScope
import com.ai.assistance.operit.data.repository.CompanionMemoryRecallResult
import com.ai.assistance.operit.data.repository.CompanionMemoryRepository
import com.ai.assistance.operit.data.repository.encodeCompanionMemoryValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionMemoryContextBuilderTest {
    @Test
    fun `recall failure degrades to an empty memory note`() = runBlocking {
        val failure = IllegalStateException("index broken")
        var reported: Throwable? = null

        val result =
            CompanionMemoryContextBuilder.buildPromptNoteOrEmpty(
                useEnglish = false,
                loadRecall = { throw failure },
                onFailure = { reported = it },
            )

        assertEquals("", result)
        assertSame(failure, reported)
    }

    @Test
    fun `empty recall remains an empty memory note`() = runBlocking {
        val result =
            CompanionMemoryContextBuilder.buildPromptNoteOrEmpty(
                useEnglish = true,
                loadRecall = {
                    CompanionMemoryRecallResult(
                        records = emptyList(),
                        episodes = emptyList(),
                    )
                },
            )

        assertEquals("", result)
    }

    @Test(expected = CancellationException::class)
    fun `recall cancellation is propagated`() {
        runBlocking {
            CompanionMemoryContextBuilder.buildPromptNoteOrEmpty(
                useEnglish = false,
                loadRecall = { throw CancellationException("cancelled") },
            )
        }
    }

    @Test
    fun `configured user name filters conflicting recalled identity`() {
        val result =
            CompanionMemoryContextBuilder.buildPromptNote(
                recall =
                    CompanionMemoryRecallResult(
                        records =
                            listOf(
                                userNameRecord("小杨"),
                                userNameRecord("hackerx30"),
                            ),
                        episodes = emptyList(),
                    ),
                useEnglish = false,
                authoritativeUserName = "hackerx30",
            )

        assertFalse(result.contains("小杨"))
        assertTrue(result.contains("hackerx30"))
    }

    private fun userNameRecord(value: String): CompanionMemoryRecordEntity =
        CompanionMemoryRecordEntity(
            profileId = "default",
            scope = CompanionRecordScope.USER.name,
            type = CompanionMemoryType.IDENTITY.name,
            subjectKey = "user",
            predicate = "name",
            valueJson = encodeCompanionMemoryValue(value),
            normalizedValue = CompanionMemoryRepository.normalizeValue(value),
            sourceKind = CompanionMemorySourceKind.USER_EXPLICIT.name,
        )
}
