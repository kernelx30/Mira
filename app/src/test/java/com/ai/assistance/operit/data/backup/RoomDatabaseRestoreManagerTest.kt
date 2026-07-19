package com.ai.assistance.operit.data.backup

import java.io.File
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RoomDatabaseRestoreManagerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun acceptsSQLiteDatabaseHeader() {
        val database = temporaryFolder.newFile("app_database")
        database.writeBytes(
            "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII) + ByteArray(128)
        )

        assertTrue(RoomDatabaseRestoreManager.hasValidSqliteHeader(database))
    }

    @Test
    fun rejectsArbitraryFileWithDatabaseEntryName() {
        val database = temporaryFolder.newFile("app_database")
        database.writeText("not a database")

        assertFalse(RoomDatabaseRestoreManager.hasValidSqliteHeader(database))
    }

    @Test
    fun rejectsMissingFile() {
        val missing = File(temporaryFolder.root, "missing")

        assertFalse(RoomDatabaseRestoreManager.hasValidSqliteHeader(missing))
    }

    @Test
    fun atomicallyReplacesExistingDatabaseFile() {
        val source = temporaryFolder.newFile("app_database.restore.tmp").apply {
            writeText("new database")
        }
        val target = temporaryFolder.newFile("app_database").apply {
            writeText("old database")
        }

        RoomDatabaseRestoreManager.replaceFile(source, target)

        assertTrue(!source.exists())
        assertTrue(target.readText() == "new database")
    }

    @Test
    fun failedReplacementDoesNotDeleteExistingDatabaseFile() {
        val missingSource = File(temporaryFolder.root, "missing.restore.tmp")
        val target = temporaryFolder.newFile("app_database").apply {
            writeText("old database")
        }

        assertThrows(IllegalArgumentException::class.java) {
            RoomDatabaseRestoreManager.replaceFile(missingSource, target)
        }

        assertEquals("old database", target.readText())
    }

    @Test
    fun databaseFamilyRollsBackWhenSecondFileReplacementFails() {
        val targetDb = temporaryFolder.newFile("app_database").apply { writeText("old db") }
        val targetWal = temporaryFolder.newFile("app_database-wal").apply { writeText("old wal") }
        val stagedDb = temporaryFolder.newFile("app_database.restore.tmp").apply { writeText("new db") }
        val stagedWal = temporaryFolder.newFile("app_database-wal.restore.tmp").apply { writeText("new wal") }

        assertThrows(IOException::class.java) {
            RoomDatabaseRestoreManager.replaceDatabaseFilesWithRollback(
                plans =
                    listOf(
                        RoomDatabaseRestoreManager.DatabaseRestoreFilePlan(stagedDb, targetDb),
                        RoomDatabaseRestoreManager.DatabaseRestoreFilePlan(stagedWal, targetWal),
                    ),
                moveFile = { source, target ->
                    if (source == stagedWal && target == targetWal) {
                        throw IOException("injected WAL replacement failure")
                    }
                    AtomicRestoreFileOps.moveReplacing(source, target)
                },
            )
        }

        assertEquals("old db", targetDb.readText())
        assertEquals("old wal", targetWal.readText())
        assertTrue(temporaryFolder.root.listFiles().orEmpty().none { it.name.endsWith(".bak") })
    }

    @Test
    fun databaseFamilyRemovesStaleSidecarsWhenBackupOmitsThem() {
        val targetDb = temporaryFolder.newFile("app_database").apply { writeText("old db") }
        val targetWal = temporaryFolder.newFile("app_database-wal").apply { writeText("old wal") }
        val targetShm = temporaryFolder.newFile("app_database-shm").apply { writeText("old shm") }
        val stagedDb = temporaryFolder.newFile("app_database.restore.tmp").apply { writeText("new db") }

        RoomDatabaseRestoreManager.replaceDatabaseFilesWithRollback(
            listOf(
                RoomDatabaseRestoreManager.DatabaseRestoreFilePlan(stagedDb, targetDb),
                RoomDatabaseRestoreManager.DatabaseRestoreFilePlan(null, targetWal),
                RoomDatabaseRestoreManager.DatabaseRestoreFilePlan(null, targetShm),
            ),
        )

        assertEquals("new db", targetDb.readText())
        assertFalse(targetWal.exists())
        assertFalse(targetShm.exists())
    }
}
