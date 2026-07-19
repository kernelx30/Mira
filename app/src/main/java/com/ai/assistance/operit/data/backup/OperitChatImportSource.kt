package com.ai.assistance.operit.data.backup

import java.io.File

/** Locates native chat backups produced by current and legacy Operit versions. */
object OperitChatImportSource {
    private const val BACKUP_PREFIX = "chat_backup_"

    fun findLatestBackup(operitRootDir: File): File? {
        return candidateDirectories(operitRootDir)
            .asSequence()
            .flatMap { directory -> directory.listFiles()?.asSequence() ?: emptySequence() }
            .filter(::isChatBackup)
            .maxWithOrNull(compareBy<File> { it.lastModified() }.thenBy { it.name })
    }

    internal fun isChatBackup(file: File): Boolean {
        return file.isFile &&
            file.name.startsWith(BACKUP_PREFIX, ignoreCase = true) &&
            file.extension.equals("json", ignoreCase = true)
    }

    private fun candidateDirectories(operitRootDir: File): List<File> {
        return listOf(
            operitRootDir,
            File(operitRootDir, "backup/chat"),
            File(operitRootDir, "chat"),
        ).distinctBy { it.absolutePath }
    }
}
