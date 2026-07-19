package com.ai.assistance.operit.data.backup

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal object AtomicRestoreFileOps {
    fun copyStreamWithLimit(input: InputStream, output: OutputStream, maxBytes: Long): Long {
        require(maxBytes >= 0L) { "maxBytes must not be negative" }
        val buffer = ByteArray(64 * 1024)
        var copiedBytes = 0L
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            if (read.toLong() > maxBytes - copiedBytes) {
                throw IllegalArgumentException("Backup archive is too large")
            }
            output.write(buffer, 0, read)
            copiedBytes += read
        }
        return copiedBytes
    }

    fun copyReplacing(source: File, target: File) {
        require(source.isFile) { "Restore source is not a file: ${source.absolutePath}" }
        val parent = target.parentFile ?: throw IllegalArgumentException("Restore target has no parent")
        parent.mkdirs()
        val staged = File.createTempFile(".mira_restore_", ".tmp", parent)
        try {
            source.inputStream().use { input ->
                staged.outputStream().use { output -> input.copyTo(output) }
            }
            moveReplacing(staged, target)
        } finally {
            staged.delete()
        }
    }

    fun moveReplacing(source: File, target: File) {
        require(source.isFile) { "Restore source is not a file: ${source.absolutePath}" }
        target.parentFile?.mkdirs()
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }
}

internal class RestoreExtractionBudget(
    private val maxEntries: Int,
    private val maxTotalBytes: Long,
    private val maxEntryBytes: Long,
) {
    private var entryCount = 0
    private var totalBytes = 0L
    private var currentEntryBytes = 0L

    fun beginEntry(entryName: String, declaredSize: Long) {
        if (entryCount >= maxEntries) {
            throw IllegalArgumentException("Backup contains too many files")
        }
        if (declaredSize > maxEntryBytes || declaredSize > maxTotalBytes - totalBytes) {
            throw IllegalArgumentException("Backup entry is too large: $entryName")
        }
        entryCount++
        currentEntryBytes = 0L
    }

    fun recordBytes(entryName: String, byteCount: Int) {
        require(byteCount >= 0) { "byteCount must not be negative" }
        if (byteCount.toLong() > maxEntryBytes - currentEntryBytes ||
            byteCount.toLong() > maxTotalBytes - totalBytes
        ) {
            throw IllegalArgumentException("Backup extraction limit exceeded at: $entryName")
        }
        currentEntryBytes += byteCount
        totalBytes += byteCount
    }
}
