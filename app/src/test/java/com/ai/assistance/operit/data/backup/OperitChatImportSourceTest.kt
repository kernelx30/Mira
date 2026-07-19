package com.ai.assistance.operit.data.backup

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OperitChatImportSourceTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun findsNewestBackupAcrossLegacyAndCurrentDirectories() {
        val root = temporaryFolder.newFolder("Operit")
        File(root, "chat_backup_2025-01-01.json").apply {
            writeText("{}")
            setLastModified(1_000L)
        }
        val currentDir = File(root, "backup/chat").apply { mkdirs() }
        val current = File(currentDir, "chat_backup_2026-01-01.json").apply {
            writeText("{}")
            setLastModified(2_000L)
        }

        assertEquals(current.canonicalFile, OperitChatImportSource.findLatestBackup(root)?.canonicalFile)
    }

    @Test
    fun ignoresNonNativeChatExports() {
        val root = temporaryFolder.newFolder("Operit")
        File(root, "chat_backup_2026-01-01.md").writeText("text")
        File(root, "model_config_backup_2026-01-01.json").writeText("[]")

        assertNull(OperitChatImportSource.findLatestBackup(root))
    }
}
