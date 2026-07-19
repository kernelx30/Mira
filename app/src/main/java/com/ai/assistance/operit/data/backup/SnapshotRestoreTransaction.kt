package com.ai.assistance.operit.data.backup

import java.io.File

internal class SnapshotRestoreTransaction(
    private val rollbackRoot: File,
    private val copyReplacing: (File, File) -> Unit = AtomicRestoreFileOps::copyReplacing,
) {
    private data class RestoreEntry(
        val target: File,
        val backup: File?,
    )

    private val entriesByTarget = LinkedHashMap<String, RestoreEntry>()
    private var finished = false

    fun replace(source: File, target: File) {
        check(!finished) { "Restore transaction is already finished" }
        require(source.isFile) { "Restore source is not a file: ${source.absolutePath}" }

        registerTarget(target)
        copyReplacing(source, target)
    }

    fun delete(target: File) {
        check(!finished) { "Restore transaction is already finished" }
        if (!target.exists()) return
        registerTarget(target)
        if (!target.delete()) {
            throw IllegalStateException("Failed to remove stale restore target: ${target.absolutePath}")
        }
    }

    private fun registerTarget(target: File) {
        val key = target.canonicalPath
        if (!entriesByTarget.containsKey(key)) {
            val backup =
                if (target.exists()) {
                    require(target.isFile) {
                        "Restore target is not a file: ${target.absolutePath}"
                    }
                    rollbackRoot.mkdirs()
                    File(rollbackRoot, "${entriesByTarget.size}.bak").also { backupFile ->
                        copyReplacing(target, backupFile)
                    }
                } else {
                    null
                }
            entriesByTarget[key] = RestoreEntry(target = target, backup = backup)
        }
    }

    fun commit() {
        check(!finished) { "Restore transaction is already finished" }
        finished = true
        rollbackRoot.deleteRecursively()
    }

    fun rollback(): List<Throwable> {
        if (finished) return emptyList()

        val failures = mutableListOf<Throwable>()
        entriesByTarget.values.toList().asReversed().forEach { entry ->
            try {
                val backup = entry.backup
                if (backup != null) {
                    if (!backup.isFile) {
                        throw IllegalStateException(
                            "Restore rollback file is missing: ${backup.absolutePath}"
                        )
                    }
                    copyReplacing(backup, entry.target)
                } else if (entry.target.exists() && !entry.target.delete()) {
                    throw IllegalStateException(
                        "Failed to remove restored file: ${entry.target.absolutePath}"
                    )
                }
            } catch (error: Throwable) {
                failures += error
            }
        }

        finished = true
        if (failures.isEmpty()) {
            rollbackRoot.deleteRecursively()
        }
        return failures
    }
}
