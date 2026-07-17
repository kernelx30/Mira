package com.ai.assistance.operit.data.repository

import com.ai.assistance.operit.data.model.ActivePrompt
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CustomEmojiStoragePolicyTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun keepsExistingSafeTargetIdsReadable() {
        assertEquals(
            "character_card_123e4567-e89b-12d3-a456-426614174000",
            CustomEmojiStoragePolicy.targetScopeDirName(
                ActivePrompt.CharacterCard("123e4567-e89b-12d3-a456-426614174000")
            ),
        )
    }

    @Test
    fun hashesTargetIdsThatCouldEscapeStorageRoot() {
        val first =
            CustomEmojiStoragePolicy.targetScopeDirName(
                ActivePrompt.CharacterGroup("../../outside")
            )
        val second =
            CustomEmojiStoragePolicy.targetScopeDirName(
                ActivePrompt.CharacterGroup("../../outside")
            )

        assertEquals(first, second)
        assertTrue(first.startsWith("character_group_sha256_"))
        assertEquals("character_group_sha256_".length + 64, first.length)
        assertFalse(first.contains(".."))
        assertFalse(first.contains('/'))
        assertFalse(first.contains('\\'))
    }

    @Test
    fun distinctUnsafeTargetIdsHaveDistinctStorageKeys() {
        assertNotEquals(
            CustomEmojiStoragePolicy.targetScopeDirName(ActivePrompt.CharacterCard("../one")),
            CustomEmojiStoragePolicy.targetScopeDirName(ActivePrompt.CharacterCard("../two")),
        )
    }

    @Test
    fun rejectsInvalidCategoryAndFileNames() {
        assertFalse(CustomEmojiStoragePolicy.isValidCategoryName("../happy"))
        assertFalse(CustomEmojiStoragePolicy.isValidCategoryName("Happy"))
        assertTrue(CustomEmojiStoragePolicy.isValidCategoryName("miss_you"))
        assertFalse(
            CustomEmojiStoragePolicy.isValidFileName(
                "../secret.png",
                setOf("png", "webp"),
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun categoryResolutionCannotEscapeTargetDirectory() {
        val targetDir = temporaryFolder.newFolder("custom_emoji", "character_card_zero")
        CustomEmojiStoragePolicy.resolveCategoryDir(targetDir, "../outside")
    }

    @Test
    fun resolvesValidEmojiFileDirectlyUnderCategory() {
        val categoryDir = temporaryFolder.newFolder("custom_emoji", "character_card_zero", "happy")
        val file =
            CustomEmojiStoragePolicy.resolveEmojiFile(
                categoryDir = categoryDir,
                fileName = "123e4567-e89b-12d3-a456-426614174000.webp",
                supportedExtensions = setOf("png", "webp"),
            )

        assertEquals(File(categoryDir, file.name).canonicalFile, file)
    }
}
