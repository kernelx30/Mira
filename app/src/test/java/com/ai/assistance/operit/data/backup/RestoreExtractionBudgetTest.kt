package com.ai.assistance.operit.data.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RestoreExtractionBudgetTest {
    @Test
    fun `bounded stream copy accepts exact limit and rejects overflow`() {
        val acceptedOutput = ByteArrayOutputStream()
        AtomicRestoreFileOps.copyStreamWithLimit(
            input = ByteArrayInputStream(byteArrayOf(1, 2, 3)),
            output = acceptedOutput,
            maxBytes = 3,
        )
        assertArrayEquals(byteArrayOf(1, 2, 3), acceptedOutput.toByteArray())

        assertThrows(IllegalArgumentException::class.java) {
            AtomicRestoreFileOps.copyStreamWithLimit(
                input = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)),
                output = ByteArrayOutputStream(),
                maxBytes = 3,
            )
        }
    }

    @Test
    fun `rejects an entry that grows beyond its declared-independent limit`() {
        val budget = RestoreExtractionBudget(maxEntries = 2, maxTotalBytes = 20, maxEntryBytes = 10)

        budget.beginEntry("payload/files/a", declaredSize = -1)
        budget.recordBytes("payload/files/a", 8)

        assertThrows(IllegalArgumentException::class.java) {
            budget.recordBytes("payload/files/a", 3)
        }
    }

    @Test
    fun `rejects cumulative extraction beyond total limit`() {
        val budget = RestoreExtractionBudget(maxEntries = 2, maxTotalBytes = 10, maxEntryBytes = 10)

        budget.beginEntry("payload/files/a", declaredSize = 6)
        budget.recordBytes("payload/files/a", 6)
        budget.beginEntry("payload/files/b", declaredSize = -1)

        assertThrows(IllegalArgumentException::class.java) {
            budget.recordBytes("payload/files/b", 5)
        }
    }

    @Test
    fun `rejects payload file count overflow`() {
        val budget = RestoreExtractionBudget(maxEntries = 1, maxTotalBytes = 10, maxEntryBytes = 10)

        budget.beginEntry("payload/files/a", declaredSize = 1)

        assertThrows(IllegalArgumentException::class.java) {
            budget.beginEntry("payload/files/b", declaredSize = 1)
        }
    }
}
