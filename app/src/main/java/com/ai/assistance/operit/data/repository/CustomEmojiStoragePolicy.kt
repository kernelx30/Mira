package com.ai.assistance.operit.data.repository

import com.ai.assistance.operit.data.model.ActivePrompt
import java.io.File
import java.security.MessageDigest
import java.util.Locale

internal object CustomEmojiStoragePolicy {
    private val categoryRegex = Regex("^[a-z0-9_]{1,64}$")
    private val safeTargetIdRegex = Regex("^[A-Za-z0-9_-]{1,128}$")
    private val safeFileNameRegex = Regex("^[A-Za-z0-9._-]{1,255}$")

    fun isValidCategoryName(categoryName: String): Boolean =
        categoryRegex.matches(categoryName)

    fun targetScopeDirName(target: ActivePrompt): String {
        val prefix =
            when (target) {
                is ActivePrompt.CharacterCard -> "character_card_"
                is ActivePrompt.CharacterGroup -> "character_group_"
            }
        val targetId =
            when (target) {
                is ActivePrompt.CharacterCard -> target.id
                is ActivePrompt.CharacterGroup -> target.id
            }
        return prefix + safeTargetStorageKey(targetId)
    }

    fun resolveCategoryDir(targetBaseDir: File, category: String): File {
        require(isValidCategoryName(category)) { "Invalid emoji category: $category" }
        return resolveDescendant(targetBaseDir, category)
    }

    fun resolveEmojiFile(
        categoryDir: File,
        fileName: String,
        supportedExtensions: Set<String>,
    ): File {
        require(isValidFileName(fileName, supportedExtensions)) {
            "Invalid emoji file name: $fileName"
        }
        return resolveDescendant(categoryDir, fileName)
    }

    fun isValidFileName(fileName: String, supportedExtensions: Set<String>): Boolean {
        if (
            fileName == "." ||
            fileName == ".." ||
            !safeFileNameRegex.matches(fileName)
        ) {
            return false
        }
        return fileName.substringAfterLast('.', "")
            .lowercase(Locale.ROOT) in supportedExtensions
    }

    private fun safeTargetStorageKey(targetId: String): String {
        if (safeTargetIdRegex.matches(targetId)) {
            return targetId
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(targetId.toByteArray(Charsets.UTF_8))
        return "sha256_" + digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private fun resolveDescendant(parent: File, childName: String): File {
        val canonicalParent = parent.canonicalFile
        val candidate = File(canonicalParent, childName).canonicalFile
        require(candidate.parentFile == canonicalParent) {
            "Emoji storage path escaped its parent directory"
        }
        return candidate
    }
}
