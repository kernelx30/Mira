package com.ai.assistance.operit.data.backup

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RawSnapshotBackupManagerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun stagedCopyReplacesTargetWithoutConsumingSource() {
        val source = temporaryFolder.newFile("source").apply { writeText("new value") }
        val target = temporaryFolder.newFile("target").apply { writeText("old value") }

        AtomicRestoreFileOps.copyReplacing(source, target)

        assertTrue(source.exists())
        assertEquals("new value", target.readText())
    }

    @Test
    fun restoreTransactionRollsBackOverwrittenAndNewFilesAfterFailure() {
        val sourceOne = temporaryFolder.newFile("source-one").apply { writeText("new one") }
        val sourceTwo = temporaryFolder.newFile("source-two").apply { writeText("new two") }
        val sourceThree = temporaryFolder.newFile("source-three").apply { writeText("new three") }
        val targetOne = temporaryFolder.newFile("target-one").apply { writeText("old one") }
        val targetTwo = temporaryFolder.newFile("target-two").apply { writeText("old two") }
        val targetThree = File(temporaryFolder.root, "target-three")
        val rollbackRoot = File(temporaryFolder.root, "rollback")
        var copyCount = 0
        val transaction =
            SnapshotRestoreTransaction(rollbackRoot) { source, target ->
                copyCount++
                if (copyCount == 5) {
                    throw IllegalStateException("injected install failure")
                }
                AtomicRestoreFileOps.copyReplacing(source, target)
            }

        transaction.replace(sourceOne, targetOne)
        transaction.replace(sourceThree, targetThree)
        assertThrows(IllegalStateException::class.java) {
            transaction.replace(sourceTwo, targetTwo)
        }

        val rollbackFailures = transaction.rollback()

        assertTrue(rollbackFailures.isEmpty())
        assertEquals("old one", targetOne.readText())
        assertEquals("old two", targetTwo.readText())
        assertFalse(targetThree.exists())
        assertFalse(rollbackRoot.exists())
    }

    @Test
    fun staleWalAndShmAreDeletedAndRestoredOnRollback() {
        val payload = temporaryFolder.newFolder("payload")
        val target = temporaryFolder.newFolder("target")
        File(payload, "app_database").writeText("new db")
        val oldWal = File(target, "app_database-wal").apply { writeText("old wal") }
        val oldShm = File(target, "app_database-shm").apply { writeText("old shm") }
        val transaction = SnapshotRestoreTransaction(File(temporaryFolder.root, "rollback-sidecars"))

        RawSnapshotBackupManager.removeStaleSqliteSidecars(payload, target, transaction)

        assertFalse(oldWal.exists())
        assertFalse(oldShm.exists())
        assertTrue(transaction.rollback().isEmpty())
        assertEquals("old wal", oldWal.readText())
        assertEquals("old shm", oldShm.readText())
    }
}
