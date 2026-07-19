package com.ai.assistance.operit.data.db

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDatabaseMigrationContractTest {
    @Test
    fun latestMigrationPersistsSpeechAndPendingEdgeEvidenceColumns() {
        val source = locateProjectFile(
            "app/src/main/java/com/ai/assistance/operit/data/db/AppDatabase.kt"
        ).readText(Charsets.UTF_8)

        assertTrue(source.contains("version = 28"))
        assertTrue(source.contains("MIGRATION_25_26"))
        assertTrue(source.contains("UPDATE companion_memory_records SET subjectScope = scope"))
        assertTrue(source.contains("THEN 'group:' || TRIM(chat.characterGroupId)"))
        assertTrue(source.contains("THEN 'character_name:' || TRIM(chat.characterCardName)"))
        assertTrue(source.contains("SET needsReview = 1, reviewAt = updatedAt"))
        assertTrue(!source.contains("runCatching { db.execSQL(\"ALTER TABLE companion_memory"))
        assertTrue(source.contains("MIGRATION_26_27"))
        assertTrue(source.contains("ALTER TABLE messages ADD COLUMN `speechDirectionJson` TEXT"))
        assertTrue(source.contains("ALTER TABLE message_variants ADD COLUMN `speechDirectionJson` TEXT"))
        assertTrue(source.contains("MIGRATION_27_28"))
        assertTrue(
            source.contains(
                "ALTER TABLE companion_memory_edges ADD COLUMN `pendingEvidenceReferencesJson` TEXT"
            )
        )

        val variantDao = locateProjectFile(
            "app/src/main/java/com/ai/assistance/operit/data/dao/MessageVariantDao.kt"
        ).readText(Charsets.UTF_8).replace("\r\n", "\n")
        assertTrue(variantDao.contains("completedAt,\n            speechDirectionJson"))
    }

    private fun locateProjectFile(relativePath: String): File =
        generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .map { File(it, relativePath) }
            .firstOrNull(File::isFile)
            ?: error("Missing project file: $relativePath")
}
