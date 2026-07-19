package com.ai.assistance.operit.data.backup

import android.content.Context
import android.net.Uri
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.util.AppLogger
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object RoomDatabaseRestoreManager {

    private const val TAG = "RoomDbRestore"
    private const val DB_NAME = "app_database"

    private const val AUTO_BACKUP_FILE_PREFIX = "room_db_backup_"
    private const val MANUAL_BACKUP_FILE_PREFIX = "room_db_manual_backup_"
    private const val MAX_DATABASE_BACKUP_ENTRIES = 3
    private const val MAX_DATABASE_ENTRY_BYTES = 4L * 1024 * 1024 * 1024
    private const val MAX_DATABASE_TOTAL_BYTES = 8L * 1024 * 1024 * 1024
    private const val RESTORE_FREE_SPACE_RESERVE_BYTES = 128L * 1024 * 1024

    fun listRecentAutoBackups(context: Context, limit: Int = 3): List<File> {
        val newDir = OperitBackupDirs.roomDbDir()
        val legacyDir = OperitBackupDirs.operitRootDir()

        val backups = sequenceOf(newDir, legacyDir)
            .flatMap { dir ->
                (dir.listFiles { f ->
                    f.isFile && f.name.startsWith(AUTO_BACKUP_FILE_PREFIX) && f.name.endsWith(".zip")
                }?.asSequence() ?: emptySequence())
            }
            .distinctBy { it.name }
            .toList()

        return backups.sortedByDescending { it.name }.take(limit)
    }

    fun listRecentBackups(context: Context, limit: Int = 3): List<File> {
        val newDir = OperitBackupDirs.roomDbDir()
        val legacyDir = OperitBackupDirs.operitRootDir()

        val backups = sequenceOf(newDir, legacyDir)
            .flatMap { dir ->
                (dir.listFiles { f ->
                    f.isFile && isRoomDatabaseBackupFile(f.name)
                }?.asSequence() ?: emptySequence())
            }
            .distinctBy { it.name }
            .toList()

        return backups
            .sortedWith(compareByDescending<File> { it.lastModified() }.thenByDescending { it.name })
            .take(limit)
    }

    fun isRoomDatabaseBackupFile(name: String): Boolean {
        return (name.startsWith(AUTO_BACKUP_FILE_PREFIX) || name.startsWith(MANUAL_BACKUP_FILE_PREFIX)) &&
            name.endsWith(".zip")
    }

    suspend fun restoreFromBackupUri(context: Context, uri: Uri) {
        withContext(Dispatchers.IO) {
            RoomDatabaseBackupRestoreLock.mutex.withLock {
                val cacheFile = File.createTempFile("room_db_restore_", ".zip", context.cacheDir)
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(cacheFile).use { output ->
                            AtomicRestoreFileOps.copyStreamWithLimit(
                                input = input,
                                output = output,
                                maxBytes =
                                    minOf(
                                        MAX_DATABASE_TOTAL_BYTES,
                                        (context.cacheDir.usableSpace - RESTORE_FREE_SPACE_RESERVE_BYTES)
                                            .coerceAtLeast(0L),
                                    ),
                            )
                        }
                    } ?: throw IllegalStateException("Failed to open uri")

                    restoreFromBackupFileInternal(context, cacheFile)
                } finally {
                    cacheFile.delete()
                }
            }
        }
    }

    suspend fun restoreFromBackupFile(context: Context, zipFile: File) {
        withContext(Dispatchers.IO) {
            RoomDatabaseBackupRestoreLock.mutex.withLock {
                restoreFromBackupFileInternal(context, zipFile)
            }
        }
    }

    private fun restoreFromBackupFileInternal(context: Context, zipFile: File) {
        if (!zipFile.exists() || !zipFile.isFile) {
            throw IllegalArgumentException("Backup file not found: ${zipFile.absolutePath}")
        }

        val targetDb = context.getDatabasePath(DB_NAME)
        val targetWal = File(targetDb.absolutePath + "-wal")
        val targetShm = File(targetDb.absolutePath + "-shm")

        val dir = targetDb.parentFile ?: throw IllegalStateException("Database dir not found")

        val tmpDb = File(dir, "${DB_NAME}.restore.tmp")
        val tmpWal = File(dir, "${DB_NAME}-wal.restore.tmp")
        val tmpShm = File(dir, "${DB_NAME}-shm.restore.tmp")

        tmpDb.delete()
        tmpWal.delete()
        tmpShm.delete()

        var extractedDb = false
        var extractedWal = false
        var extractedShm = false
        val extractionBudget =
            RestoreExtractionBudget(
                maxEntries = MAX_DATABASE_BACKUP_ENTRIES,
                maxTotalBytes =
                    minOf(
                        MAX_DATABASE_TOTAL_BYTES,
                        (dir.usableSpace - RESTORE_FREE_SPACE_RESERVE_BYTES).coerceAtLeast(0L),
                    ),
                maxEntryBytes = MAX_DATABASE_ENTRY_BYTES,
            )
        val extractedEntryNames = HashSet<String>()

        try {
            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                while (true) {
                    val entry = zis.nextEntry ?: break
                    val name = entry.name

                    when (name) {
                        DB_NAME -> {
                            requireNewDatabaseEntry(extractedEntryNames, extractionBudget, name, entry.size)
                            writeStreamToFile(zis, tmpDb, extractionBudget, name)
                            extractedDb = true
                        }
                        "${DB_NAME}-wal" -> {
                            requireNewDatabaseEntry(extractedEntryNames, extractionBudget, name, entry.size)
                            writeStreamToFile(zis, tmpWal, extractionBudget, name)
                            extractedWal = true
                        }
                        "${DB_NAME}-shm" -> {
                            requireNewDatabaseEntry(extractedEntryNames, extractionBudget, name, entry.size)
                            writeStreamToFile(zis, tmpShm, extractionBudget, name)
                            extractedShm = true
                        }
                    }

                    zis.closeEntry()
                }
            }

            if (!extractedDb) {
                throw IllegalArgumentException("Invalid backup zip: missing $DB_NAME")
            }
            if (!hasValidSqliteHeader(tmpDb)) {
                throw IllegalArgumentException("Invalid backup zip: $DB_NAME is not a SQLite database")
            }

            try {
                AppDatabase.closeDatabase()
            } catch (e: Exception) {
                AppLogger.w(TAG, "closeDatabase failed", e)
            }

            replaceDatabaseFilesWithRollback(
                listOf(
                    DatabaseRestoreFilePlan(staged = tmpDb, target = targetDb),
                    DatabaseRestoreFilePlan(staged = tmpWal.takeIf { extractedWal }, target = targetWal),
                    DatabaseRestoreFilePlan(staged = tmpShm.takeIf { extractedShm }, target = targetShm),
                )
            )
        } catch (e: Exception) {
            tmpDb.delete()
            tmpWal.delete()
            tmpShm.delete()
            throw e
        }
    }

    private fun requireNewDatabaseEntry(
        extractedEntryNames: MutableSet<String>,
        extractionBudget: RestoreExtractionBudget,
        entryName: String,
        declaredSize: Long,
    ) {
        if (!extractedEntryNames.add(entryName)) {
            throw IllegalArgumentException("Duplicate database backup entry: $entryName")
        }
        extractionBudget.beginEntry(entryName, declaredSize)
    }

    private fun writeStreamToFile(
        input: ZipInputStream,
        target: File,
        extractionBudget: RestoreExtractionBudget,
        entryName: String,
    ) {
        val buffer = ByteArray(64 * 1024)
        BufferedOutputStream(FileOutputStream(target)).use { output ->
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                extractionBudget.recordBytes(entryName, read)
                output.write(buffer, 0, read)
            }
        }
    }

    internal fun hasValidSqliteHeader(file: File): Boolean {
        if (!file.isFile || file.length() < SQLITE_HEADER.size) return false
        val header = ByteArray(SQLITE_HEADER.size)
        FileInputStream(file).use { input ->
            var offset = 0
            while (offset < header.size) {
                val read = input.read(header, offset, header.size - offset)
                if (read <= 0) return false
                offset += read
            }
        }
        return header.contentEquals(SQLITE_HEADER)
    }

    internal fun replaceFile(from: File, to: File) {
        AtomicRestoreFileOps.moveReplacing(from, to)
    }

    internal data class DatabaseRestoreFilePlan(
        val staged: File?,
        val target: File,
    )

    internal fun replaceDatabaseFilesWithRollback(
        plans: List<DatabaseRestoreFilePlan>,
        moveFile: (File, File) -> Unit = { source, target ->
            AtomicRestoreFileOps.moveReplacing(source, target)
        },
    ) {
        require(plans.map { it.target.absolutePath }.distinct().size == plans.size) {
            "Restore plan contains duplicate targets"
        }
        plans.mapNotNull { it.staged }.forEach { staged ->
            require(staged.isFile) { "Restore source is not a file: ${staged.absolutePath}" }
        }

        val backups = LinkedHashMap<File, File>()
        val installedTargets = LinkedHashSet<File>()
        var committed = false
        try {
            plans.forEach { plan ->
                if (plan.target.exists()) {
                    val parent = plan.target.parentFile
                        ?: throw IllegalArgumentException("Restore target has no parent")
                    val backup = File.createTempFile(".${plan.target.name}.mira_restore_", ".bak", parent)
                    check(backup.delete()) { "Failed to prepare restore backup: ${backup.absolutePath}" }
                    moveFile(plan.target, backup)
                    backups[plan.target] = backup
                }
            }

            plans.forEach { plan ->
                plan.staged?.let { staged ->
                    moveFile(staged, plan.target)
                    installedTargets += plan.target
                }
            }
            committed = true
        } catch (failure: Exception) {
            installedTargets
                .filterNot(backups::containsKey)
                .forEach { target ->
                    if (target.exists() && !target.delete()) {
                        failure.addSuppressed(
                            IllegalStateException("Failed to remove partial restore target: ${target.absolutePath}")
                        )
                    }
                }
            backups.entries.toList().asReversed().forEach { (target, backup) ->
                if (backup.exists()) {
                    try {
                        moveFile(backup, target)
                    } catch (rollbackFailure: Exception) {
                        failure.addSuppressed(
                            IllegalStateException(
                                "Failed to roll back ${target.absolutePath}; backup remains at ${backup.absolutePath}",
                                rollbackFailure,
                            )
                        )
                    }
                }
            }
            throw failure
        } finally {
            if (committed) {
                backups.values.forEach { backup -> backup.delete() }
            }
        }
    }

    private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
}
