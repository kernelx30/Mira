package com.ai.assistance.operit.data.backup

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.db.ObjectBoxManager
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.OperitPaths
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object RawSnapshotBackupManager {

    private const val TAG = "RawSnapshotBackup"
    private const val FORMAT_VERSION = 1

    private const val ZIP_PREFIX = "operit_raw_snapshot_"

    private const val ENTRY_MANIFEST = "manifest.json"
    private const val ENTRY_PAYLOAD_PREFIX = "payload/"

    private const val ENTRY_FILES = "payload/files/"
    private const val ENTRY_EXTERNAL_FILES = "payload/external_files/"
    private const val ENTRY_SHARED_PREFS = "payload/shared_prefs/"
    private const val ENTRY_DATASTORE = "payload/datastore/"
    private const val ENTRY_DATABASES = "payload/databases/"

    private const val MAX_EXTRACTED_PAYLOAD_FILES = 100_000
    private const val MAX_EXTRACTED_ENTRY_BYTES = 4L * 1024 * 1024 * 1024
    private const val MAX_EXTRACTED_TOTAL_BYTES = 32L * 1024 * 1024 * 1024
    private const val RESTORE_FREE_SPACE_RESERVE_BYTES = 256L * 1024 * 1024

    private val terminalTopLevelDirNames = setOf("usr", "tmp", "bin")

    private val mutex = Mutex()
    private val mainHandler by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        Handler(Looper.getMainLooper())
    }

    @Serializable
    data class Manifest(
        val formatVersion: Int,
        val packageName: String,
        val createdAt: Long,
        val includes: List<String>,
        val includeTerminalData: Boolean = true
    )

    data class SnapshotOptions(
        val includeTerminalData: Boolean = false
    )

    enum class ExportProgress {
        PREPARING,
        SCANNING_FILES,
        ZIPPING_FILES,
        ZIPPING_EXTERNAL_FILES,
        ZIPPING_SHARED_PREFS,
        ZIPPING_DATASTORE,
        ZIPPING_DATABASES,
        FINALIZING
    }

    data class ExportProgressInfo(
        val stage: ExportProgress,
        val percent: Int? = null,
        val scannedFiles: Int? = null
    )

    enum class RestoreProgress {
        PREPARING,
        READING_ZIP,
        EXTRACTING,
        REPLACING_FILES,
        REPLACING_EXTERNAL_FILES,
        REPLACING_SHARED_PREFS,
        REPLACING_DATASTORE,
        REPLACING_DATABASES,
        FINALIZING
    }

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun exportToBackupDir(
        context: Context,
        options: SnapshotOptions = SnapshotOptions(),
        onProgress: ((ExportProgressInfo) -> Unit)? = null
    ): File = withContext(Dispatchers.IO) {
        mutex.withLock {
            AppLogger.i(TAG, "export start (includeTerminalData=${options.includeTerminalData})")
            withContext(Dispatchers.Main) { onProgress?.invoke(ExportProgressInfo(ExportProgress.PREPARING)) }
            val exportDir = OperitBackupDirs.rawSnapshotDir()
            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
            val outFile = File(exportDir, "$ZIP_PREFIX$timestamp.zip")
            val tmpFile = File(exportDir, "${outFile.name}.tmp")

            if (tmpFile.exists()) {
                tmpFile.delete()
            }

            val dataDir = context.dataDir
            val externalFilesDir = requireNotNull(context.getExternalFilesDir(null)) {
                "External files dir is unavailable"
            }
            val sharedPrefsDir = File(dataDir, "shared_prefs")
            val datastoreDir = File(dataDir, "datastore")
            val databasesDir = File(dataDir, "databases")

            try {
                val sqliteDb = AppDatabase.getDatabase(context).openHelper.writableDatabase
                sqliteDb.query("PRAGMA wal_checkpoint(FULL)").close()
            } catch (e: Exception) {
                AppLogger.w(TAG, "wal_checkpoint failed", e)
            }

            val includes = listOf(
                ENTRY_FILES,
                ENTRY_EXTERNAL_FILES,
                ENTRY_SHARED_PREFS,
                ENTRY_DATASTORE,
                ENTRY_DATABASES
            )
            val manifest = Manifest(
                formatVersion = FORMAT_VERSION,
                packageName = context.packageName,
                createdAt = System.currentTimeMillis(),
                includes = includes,
                includeTerminalData = options.includeTerminalData
            )

            ZipOutputStream(BufferedOutputStream(FileOutputStream(tmpFile))).use { zos ->
                zos.putNextEntry(ZipEntry(ENTRY_MANIFEST))
                zos.write(json.encodeToString(manifest).toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                val alwaysExcluded = OperitPaths.rawSnapshotExcludedFilesTopLevelDirNames()
                val excludedNames = if (options.includeTerminalData) {
                    alwaysExcluded
                } else {
                    alwaysExcluded + terminalTopLevelDirNames
                }
                withContext(Dispatchers.Main) {
                    onProgress?.invoke(ExportProgressInfo(stage = ExportProgress.SCANNING_FILES, scannedFiles = 0))
                }
                val filesTotalCount = totalFilesForZip(
                    dir = context.filesDir,
                    entryPrefix = ENTRY_FILES,
                    excludedTopLevelDirNames = excludedNames,
                    onScannedCountChanged = { scanned ->
                        if (onProgress != null) {
                            mainHandler.post {
                                onProgress.invoke(
                                    ExportProgressInfo(stage = ExportProgress.SCANNING_FILES, scannedFiles = scanned)
                                )
                            }
                        }
                    }
                )
                withContext(Dispatchers.Main) {
                    onProgress?.invoke(
                        ExportProgressInfo(stage = ExportProgress.SCANNING_FILES, scannedFiles = filesTotalCount)
                    )
                }
                withContext(Dispatchers.Main) { onProgress?.invoke(ExportProgressInfo(ExportProgress.ZIPPING_FILES, 0)) }
                val filesMs = measureTimeMillis {
                    addDirToZip(
                        zos = zos,
                        dir = context.filesDir,
                        entryPrefix = ENTRY_FILES,
                        excludedTopLevelDirNames = excludedNames,
                        totalFiles = filesTotalCount,
                        onPercentChanged = { percent ->
                            if (onProgress != null) {
                                mainHandler.post {
                                    onProgress.invoke(ExportProgressInfo(ExportProgress.ZIPPING_FILES, percent))
                                }
                            }
                        }
                    )
                }
                withContext(Dispatchers.Main) { onProgress?.invoke(ExportProgressInfo(ExportProgress.ZIPPING_FILES, 100)) }
                AppLogger.i(TAG, "export add files done in ${filesMs}ms (excludedTopLevel=${excludedNames.size})")

                val externalFilesTotalCount = totalFilesForZip(
                    dir = externalFilesDir,
                    entryPrefix = ENTRY_EXTERNAL_FILES,
                    excludedTopLevelDirNames = emptySet()
                )
                withContext(Dispatchers.Main) {
                    onProgress?.invoke(ExportProgressInfo(ExportProgress.ZIPPING_EXTERNAL_FILES, 0))
                }
                val externalFilesMs = measureTimeMillis {
                    addDirToZip(
                        zos = zos,
                        dir = externalFilesDir,
                        entryPrefix = ENTRY_EXTERNAL_FILES,
                        totalFiles = externalFilesTotalCount,
                        onPercentChanged = { percent ->
                            if (onProgress != null) {
                                mainHandler.post {
                                    onProgress.invoke(
                                        ExportProgressInfo(ExportProgress.ZIPPING_EXTERNAL_FILES, percent)
                                    )
                                }
                            }
                        }
                    )
                }
                withContext(Dispatchers.Main) {
                    onProgress?.invoke(ExportProgressInfo(ExportProgress.ZIPPING_EXTERNAL_FILES, 100))
                }
                AppLogger.i(TAG, "export add external_files done in ${externalFilesMs}ms")

                withContext(Dispatchers.Main) { onProgress?.invoke(ExportProgressInfo(ExportProgress.ZIPPING_SHARED_PREFS)) }
                val sharedPrefsMs = measureTimeMillis { addDirToZip(zos, sharedPrefsDir, ENTRY_SHARED_PREFS) }
                AppLogger.i(TAG, "export add shared_prefs done in ${sharedPrefsMs}ms")

                withContext(Dispatchers.Main) { onProgress?.invoke(ExportProgressInfo(ExportProgress.ZIPPING_DATASTORE)) }
                val datastoreMs = measureTimeMillis { addDirToZip(zos, datastoreDir, ENTRY_DATASTORE) }
                AppLogger.i(TAG, "export add datastore done in ${datastoreMs}ms")

                withContext(Dispatchers.Main) { onProgress?.invoke(ExportProgressInfo(ExportProgress.ZIPPING_DATABASES)) }
                val databasesMs = measureTimeMillis { addDirToZip(zos, databasesDir, ENTRY_DATABASES) }
                AppLogger.i(TAG, "export add databases done in ${databasesMs}ms")
            }

            withContext(Dispatchers.Main) { onProgress?.invoke(ExportProgressInfo(ExportProgress.FINALIZING)) }
            if (outFile.exists()) {
                outFile.delete()
            }

            if (!tmpFile.renameTo(outFile)) {
                tmpFile.copyTo(outFile, overwrite = true)
                tmpFile.delete()
            }

            AppLogger.i(TAG, "export done: ${outFile.absolutePath} (${outFile.length()} bytes)")
            outFile
        }
    }

    suspend fun restoreFromBackupUri(
        context: Context,
        uri: Uri,
        onProgress: ((RestoreProgress) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val cacheZip = File.createTempFile("raw_snapshot_restore_", ".zip", context.cacheDir)
            val workDir =
                File(
                    context.cacheDir,
                    "raw_snapshot_restore_work_${System.currentTimeMillis()}_${UUID.randomUUID()}",
                ).apply {
                    check(mkdirs()) { "Failed to create restore work directory" }
                }
            var restoreTransaction: SnapshotRestoreTransaction? = null
            var preserveWorkDir = false

            try {
                AppLogger.i(TAG, "restore start uri=$uri")
                withContext(Dispatchers.Main) { onProgress?.invoke(RestoreProgress.PREPARING) }
                withContext(Dispatchers.Main) { onProgress?.invoke(RestoreProgress.READING_ZIP) }
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(cacheZip).use { output ->
                        AtomicRestoreFileOps.copyStreamWithLimit(
                            input = input,
                            output = output,
                            maxBytes =
                                minOf(
                                    MAX_EXTRACTED_TOTAL_BYTES,
                                    (context.cacheDir.usableSpace - RESTORE_FREE_SPACE_RESERVE_BYTES)
                                        .coerceAtLeast(0L),
                                ),
                        )
                    }
                } ?: throw IllegalStateException("Failed to open uri")

                AppLogger.i(TAG, "restore cached zip: ${cacheZip.absolutePath} (${cacheZip.length()} bytes)")

                withContext(Dispatchers.Main) { onProgress?.invoke(RestoreProgress.EXTRACTING) }
                val manifest = extractZipToWorkDir(cacheZip, workDir, expectedPackageName = context.packageName)

                val payloadDir = File(workDir, "payload")
                val externalFilesPayloadDir = File(payloadDir, "external_files")

                val alwaysExcluded = OperitPaths.rawSnapshotExcludedFilesTopLevelDirNames()

                val preserveTerminal = !manifest.includeTerminalData
                val preservedTerminalNames = if (preserveTerminal) terminalTopLevelDirNames else emptySet()
                val preservedAlwaysExcludedNames = alwaysExcluded.filterNot { dirName ->
                    File(payloadDir, "files/$dirName").exists()
                }.toSet()
                val preservedNames = preservedTerminalNames + preservedAlwaysExcludedNames

                AppLogger.i(
                    TAG,
                    "restore manifest ok (formatVersion=${manifest.formatVersion}, includeTerminalData=${manifest.includeTerminalData})"
                )

                AppDatabase.closeDatabase()
                ObjectBoxManager.closeAll()
                AppLogger.i(TAG, "restore closed databases (room + objectbox)")

                AppLogger.i(TAG, "restore replace dirs (preserveTerminalTopLevel=${preservedNames.isNotEmpty()})")
                val transaction =
                    SnapshotRestoreTransaction(File(workDir, ".rollback")).also {
                        restoreTransaction = it
                    }

                withContext(Dispatchers.Main) { onProgress?.invoke(RestoreProgress.REPLACING_FILES) }
                replaceDirContents(
                    File(payloadDir, "files"),
                    context.filesDir,
                    preservedTopLevelDirNames = preservedNames,
                    transaction = transaction,
                )
                if (externalFilesPayloadDir.exists()) {
                    val externalFilesDir = requireNotNull(context.getExternalFilesDir(null)) {
                        "External files dir is unavailable"
                    }
                    withContext(Dispatchers.Main) { onProgress?.invoke(RestoreProgress.REPLACING_EXTERNAL_FILES) }
                    replaceDirContents(externalFilesPayloadDir, externalFilesDir, transaction = transaction)
                }
                withContext(Dispatchers.Main) { onProgress?.invoke(RestoreProgress.REPLACING_SHARED_PREFS) }
                replaceDirContents(
                    File(payloadDir, "shared_prefs"),
                    File(context.dataDir, "shared_prefs"),
                    transaction = transaction,
                )
                withContext(Dispatchers.Main) { onProgress?.invoke(RestoreProgress.REPLACING_DATASTORE) }
                replaceDirContents(
                    File(payloadDir, "datastore"),
                    File(context.dataDir, "datastore"),
                    transaction = transaction,
                )
                withContext(Dispatchers.Main) { onProgress?.invoke(RestoreProgress.REPLACING_DATABASES) }
                removeStaleSqliteSidecars(
                    File(payloadDir, "databases"),
                    File(context.dataDir, "databases"),
                    transaction,
                )
                replaceDirContents(
                    File(payloadDir, "databases"),
                    File(context.dataDir, "databases"),
                    transaction = transaction,
                )

                withContext(Dispatchers.Main) { onProgress?.invoke(RestoreProgress.FINALIZING) }
                transaction.commit()
                AppLogger.i(TAG, "restore done: ${manifest.packageName}")
            } catch (e: Exception) {
                val rollbackFailures = restoreTransaction?.rollback().orEmpty()
                rollbackFailures.forEach(e::addSuppressed)
                preserveWorkDir = rollbackFailures.isNotEmpty()
                if (preserveWorkDir) {
                    AppLogger.e(
                        TAG,
                        "restore rollback incomplete; preserved recovery files at ${workDir.absolutePath}",
                    )
                }
                AppLogger.e(TAG, "restore failed", e)
                throw e
            } finally {
                try {
                    cacheZip.delete()
                } catch (_: Exception) {
                }
                if (!preserveWorkDir) {
                    try {
                        workDir.deleteRecursively()
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    private fun extractZipToWorkDir(zipFile: File, workDir: File, expectedPackageName: String): Manifest {
        val payloadRoot = File(workDir, "payload")
        payloadRoot.mkdirs()

        var manifestText: String? = null
        var extractedPayloadFiles = 0
        val maxExtractedBytes =
            minOf(
                MAX_EXTRACTED_TOTAL_BYTES,
                (workDir.usableSpace - RESTORE_FREE_SPACE_RESERVE_BYTES).coerceAtLeast(0L),
            )
        val extractionBudget =
            RestoreExtractionBudget(
                maxEntries = MAX_EXTRACTED_PAYLOAD_FILES,
                maxTotalBytes = maxExtractedBytes,
                maxEntryBytes = MAX_EXTRACTED_ENTRY_BYTES,
            )
        val extractedEntryNames = HashSet<String>()

        val buffer = ByteArray(64 * 1024)
        val extractMs = measureTimeMillis {
            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                while (true) {
                    val entry = zis.nextEntry ?: break
                    val name = entry.name

                    if (entry.isDirectory) {
                        zis.closeEntry()
                        continue
                    }

                    if (name == ENTRY_MANIFEST) {
                        val bytes = zis.readBytesSafely(maxBytes = 512 * 1024)
                        manifestText = bytes.toString(Charsets.UTF_8)
                        zis.closeEntry()
                        continue
                    }

                    if (!name.startsWith(ENTRY_PAYLOAD_PREFIX)) {
                        zis.closeEntry()
                        continue
                    }

                    if (!extractedEntryNames.add(name)) {
                        zis.closeEntry()
                        throw IllegalArgumentException("Duplicate zip entry: $name")
                    }

                    extractionBudget.beginEntry(name, entry.size)

                    val target = File(workDir, name)
                    val workCanonical = workDir.canonicalFile
                    val targetCanonical = target.canonicalFile
                    if (!targetCanonical.path.startsWith(workCanonical.path + File.separator)) {
                        zis.closeEntry()
                        throw IllegalArgumentException("Invalid zip entry path: $name")
                    }

                    target.parentFile?.mkdirs()
                    BufferedOutputStream(FileOutputStream(target)).use { output ->
                        while (true) {
                            val read = zis.read(buffer)
                            if (read <= 0) break
                            extractionBudget.recordBytes(name, read)
                            output.write(buffer, 0, read)
                        }
                    }

                    extractedPayloadFiles++

                    zis.closeEntry()
                }
            }
        }

        AppLogger.i(TAG, "restore extract done in ${extractMs}ms (payloadFiles=$extractedPayloadFiles)")

        val manifest = manifestText?.let { json.decodeFromString(Manifest.serializer(), it) }
            ?: throw IllegalArgumentException("Invalid backup zip: missing $ENTRY_MANIFEST")

        if (manifest.formatVersion != FORMAT_VERSION) {
            throw IllegalArgumentException("Unsupported backup version: ${manifest.formatVersion}")
        }

        if (manifest.packageName != expectedPackageName) {
            throw IllegalArgumentException("Backup package mismatch: ${manifest.packageName}")
        }

        return manifest
    }

    private fun addDirToZip(
        zos: ZipOutputStream,
        dir: File,
        entryPrefix: String,
        excludedTopLevelDirNames: Set<String> = emptySet(),
        totalFiles: Int = 0,
        onPercentChanged: ((Int) -> Unit)? = null
    ) {
        if (!dir.exists() || !dir.isDirectory) return

        val baseCanonical = dir.canonicalFile
        val buffer = ByteArray(64 * 1024)
        val writtenEntryNames = HashSet<String>()

        var processedFiles = 0
        var lastPercent = -1

        dir.walkTopDown().onEnter { currentDir ->
            !shouldPruneDirForZip(currentDir, dir, entryPrefix, excludedTopLevelDirNames)
        }.forEach { f ->
            if (!f.isFile) return@forEach

            val canonical = f.canonicalFile
            if (shouldSkipForZip(canonical, baseCanonical, entryPrefix, excludedTopLevelDirNames)) {
                if (canonical.name == "lock.mdb" && canonical.parentFile?.name?.startsWith("objectbox") == true) {
                    AppLogger.w(TAG, "export skip objectbox lock file: ${canonical.absolutePath}")
                }
                return@forEach
            }

            val rel = canonical.path.substring(baseCanonical.path.length + 1)
            val entryName = entryPrefix + rel.replace(File.separatorChar, '/')

            if (!writtenEntryNames.add(entryName)) {
                AppLogger.w(TAG, "export skip duplicate entry: $entryName")
                return@forEach
            }

            zos.putNextEntry(ZipEntry(entryName))
            BufferedInputStream(FileInputStream(canonical)).use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    zos.write(buffer, 0, read)
                }
            }
            zos.closeEntry()

            if (totalFiles > 0 && onPercentChanged != null) {
                processedFiles++
                val percent = ((processedFiles * 100) / totalFiles).coerceIn(0, 100)
                if (percent != lastPercent) {
                    lastPercent = percent
                    onPercentChanged(percent)
                }
            }
        }
    }

    private fun shouldPruneDirForZip(
        currentDir: File,
        baseDir: File,
        entryPrefix: String,
        excludedTopLevelDirNames: Set<String>
    ): Boolean {
        if (currentDir == baseDir) return false
        val parent = currentDir.parentFile ?: return false
        if (parent != baseDir) return false

        val name = currentDir.name
        if (excludedTopLevelDirNames.contains(name)) return true

        if (entryPrefix == ENTRY_FILES) {
            if (name.startsWith("sherpa-ncnn-")) return true
        }

        return false
    }

    private fun shouldSkipForZip(
        canonical: File,
        baseCanonical: File,
        entryPrefix: String,
        excludedTopLevelDirNames: Set<String>
    ): Boolean {
        if (!canonical.path.startsWith(baseCanonical.path + File.separator)) return true

        if (canonical.name == "lock.mdb" && canonical.parentFile?.name?.startsWith("objectbox") == true) {
            return true
        }

        val rel = canonical.path.substring(baseCanonical.path.length + 1)
        val relNormalized = rel.replace(File.separatorChar, '/')
        val top = relNormalized.substringBefore('/', missingDelimiterValue = relNormalized)
        if (excludedTopLevelDirNames.isNotEmpty() && excludedTopLevelDirNames.contains(top)) {
            return true
        }

        if (entryPrefix == ENTRY_FILES) {
            if (top.startsWith("sherpa-ncnn-")) {
                return true
            }

            // Exclude Ubuntu rootfs package (very large). Stored as a top-level file in filesDir.
            if (!relNormalized.contains('/')) {
                val name = relNormalized
                if (name.startsWith("ubuntu-", ignoreCase = true) && name.endsWith(".tar.xz", ignoreCase = true)) {
                    return true
                }
            }

            if (!relNormalized.contains('/')) {
                if (relNormalized.startsWith("memory_hnsw_") && relNormalized.endsWith(".idx")) {
                    return true
                }
                if (relNormalized.startsWith("doc_index_") && relNormalized.endsWith(".hnsw")) {
                    return true
                }
            }
        }

        return false
    }

    private fun totalFilesForZip(
        dir: File,
        entryPrefix: String,
        excludedTopLevelDirNames: Set<String>,
        onScannedCountChanged: ((Int) -> Unit)? = null
    ): Int {
        if (!dir.exists() || !dir.isDirectory) return 0
        val baseCanonical = dir.canonicalFile
        var total = 0

        var lastReported = 0
        var lastReportAtMs = 0L
        dir.walkTopDown().onEnter { currentDir ->
            !shouldPruneDirForZip(currentDir, dir, entryPrefix, excludedTopLevelDirNames)
        }.forEach { f ->
            if (!f.isFile) return@forEach
            val canonical = f.canonicalFile
            if (shouldSkipForZip(canonical, baseCanonical, entryPrefix, excludedTopLevelDirNames)) return@forEach
            total++

            if (onScannedCountChanged != null) {
                val now = System.currentTimeMillis()
                if (total == 1 || total - lastReported >= 200 || now - lastReportAtMs >= 250L) {
                    lastReported = total
                    lastReportAtMs = now
                    onScannedCountChanged(total)
                }
            }
        }
        return total
    }

    private fun replaceDirContents(
        fromDir: File,
        toDir: File,
        preservedTopLevelDirNames: Set<String> = emptySet(),
        transaction: SnapshotRestoreTransaction,
    ) {
        if (!toDir.exists()) {
            toDir.mkdirs()
        }

        if (!fromDir.exists() || !fromDir.isDirectory) return

        // Non-destructive restore: only overwrite files present in the backup.
        // Files not present in the backup are preserved.
        copyDir(fromDir, toDir, preservedTopLevelDirNames, transaction)
    }

    internal fun removeStaleSqliteSidecars(
        fromDir: File,
        toDir: File,
        transaction: SnapshotRestoreTransaction,
    ) {
        if (!fromDir.isDirectory || !toDir.isDirectory) return
        fromDir.listFiles().orEmpty()
            .filter { source ->
                source.isFile &&
                    !source.name.endsWith("-wal") &&
                    !source.name.endsWith("-shm")
            }
            .forEach { sourceDatabase ->
                listOf("-wal", "-shm").forEach { suffix ->
                    val sourceSidecar = File(fromDir, sourceDatabase.name + suffix)
                    if (!sourceSidecar.isFile) {
                        transaction.delete(File(toDir, sourceDatabase.name + suffix))
                    }
                }
            }
    }

    private fun copyDir(
        fromDir: File,
        toDir: File,
        preservedTopLevelDirNames: Set<String>,
        transaction: SnapshotRestoreTransaction,
    ) {
        val baseCanonical = fromDir.canonicalFile
        fromDir.walkTopDown().forEach { f ->
            val canonical = f.canonicalFile
            if (!canonical.path.startsWith(baseCanonical.path + File.separator) && canonical != baseCanonical) {
                return@forEach
            }

            if (canonical == baseCanonical) return@forEach

            val rel = canonical.path.substring(baseCanonical.path.length + 1)
            if (preservedTopLevelDirNames.isNotEmpty()) {
                val relNormalized = rel.replace(File.separatorChar, '/')
                val top = relNormalized.substringBefore('/', missingDelimiterValue = relNormalized)
                if (preservedTopLevelDirNames.contains(top)) {
                    return@forEach
                }
            }
            val target = File(toDir, rel)

            if (canonical.isDirectory) {
                target.mkdirs()
            } else if (canonical.isFile) {
                target.parentFile?.mkdirs()
                transaction.replace(canonical, target)
            }
        }
    }

    private fun ZipInputStream.readBytesSafely(maxBytes: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val read = read(buffer)
            if (read <= 0) break
            if (out.size() + read > maxBytes) {
                throw IllegalArgumentException("Zip entry too large")
            }
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }
}
