package com.ai.assistance.operit.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ActivePrompt
import com.ai.assistance.operit.data.model.CustomEmoji
import com.ai.assistance.operit.data.preferences.ActivePromptManager
import com.ai.assistance.operit.data.preferences.CustomEmojiPreferences
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * 自定义表情 Repository
 *
 * 管理按角色卡/角色组隔离的表情文件和数据持久化。
 * - 文件存储在 filesDir/custom_emoji/<target_scope>/{category}/{uuid}.{ext}
 * - 元数据通过 CustomEmojiPreferences 按 target 独立存储在 DataStore
 */
class CustomEmojiRepository private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: CustomEmojiRepository? = null

        fun getInstance(context: Context): CustomEmojiRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CustomEmojiRepository(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }

        private const val TAG = "CustomEmojiRepository"
        private const val EMOJI_DIR = "custom_emoji"
        private val SUPPORTED_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp")
    }

    private val preferences = CustomEmojiPreferences.getInstance(context)
    private val activePromptManager by lazy { ActivePromptManager.getInstance(context) }
    @Volatile
    private var legacyStoragePurged = false

    suspend fun initializeBuiltinEmojis() = withContext(Dispatchers.IO) {
        initializeBuiltinEmojis(activePromptManager.getActivePrompt())
    }

    suspend fun initializeBuiltinEmojis(target: ActivePrompt) = withContext(Dispatchers.IO) {
        purgeLegacyGlobalStorage()
        if (preferences.isBuiltinEmojisInitialized(target).first()) {
            return@withContext
        }

        copyBuiltinEmojisFromAssets(target)
        preferences.setBuiltinEmojisInitialized(target, true)
        AppLogger.d(TAG, "Built-in emojis initialized successfully for target: $target")
    }

    suspend fun resetToDefault() = withContext(Dispatchers.IO) {
        resetToDefault(activePromptManager.getActivePrompt())
    }

    /**
     * 重置指定目标的表情库为默认表情（重新从 assets 复制）
     */
    suspend fun resetToDefault(target: ActivePrompt) = withContext(Dispatchers.IO) {
        try {
            preferences.clearAllEmojis(target)
            getTargetBaseDir(target).deleteRecursively()

            copyBuiltinEmojisFromAssets(target)
            preferences.setBuiltinEmojisInitialized(target, true)

            AppLogger.d(TAG, "Reset emojis to default successfully for target: $target")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error resetting emojis for target: $target", e)
            throw e
        }
    }

    suspend fun addCustomEmoji(category: String, sourceUri: Uri): Result<CustomEmoji> = withContext(Dispatchers.IO) {
        addCustomEmoji(activePromptManager.getActivePrompt(), category, sourceUri)
    }

    /**
     * 为指定目标添加自定义表情
     */
    suspend fun addCustomEmoji(
        target: ActivePrompt,
        category: String,
        sourceUri: Uri
    ): Result<CustomEmoji> = withContext(Dispatchers.IO) {
        try {
            if (!isValidCategoryName(category)) {
                return@withContext Result.failure(
                    IllegalArgumentException(context.getString(R.string.invalid_category_name))
                )
            }
            initializeBuiltinEmojis(target)

            val extension = getFileExtension(sourceUri) ?: return@withContext Result.failure(
                IllegalArgumentException(context.getString(R.string.emoji_cannot_determine_extension))
            )

            if (extension.lowercase() !in SUPPORTED_EXTENSIONS) {
                return@withContext Result.failure(
                    IllegalArgumentException(context.getString(R.string.emoji_unsupported_image_format, extension))
                )
            }

            val fileName = "${UUID.randomUUID()}.$extension"
            val categoryDir = getCategoryDir(target, category)
            if (!categoryDir.exists()) {
                categoryDir.mkdirs()
            }

            val targetFile = File(categoryDir, fileName)
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(
                IllegalStateException(context.getString(R.string.emoji_cannot_read_source))
            )

            val emoji = CustomEmoji(
                emotionCategory = category,
                fileName = fileName,
                isBuiltInCategory = category in CustomEmojiPreferences.BUILTIN_EMOTIONS
            )

            preferences.addCategory(target, category)
            preferences.addCustomEmoji(target, emoji)

            AppLogger.d(TAG, "Successfully added emoji: $fileName to target: $target category: $category")
            Result.success(emoji)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error adding custom emoji for target: $target", e)
            Result.failure(e)
        }
    }

    suspend fun deleteCustomEmoji(emojiId: String): Result<Unit> = withContext(Dispatchers.IO) {
        deleteCustomEmoji(activePromptManager.getActivePrompt(), emojiId)
    }

    suspend fun deleteCustomEmoji(target: ActivePrompt, emojiId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val emoji = preferences.getCustomEmojisFlow(target).first()
                .find { it.id == emojiId }
                ?: return@withContext Result.failure(
                    IllegalArgumentException(context.getString(R.string.emoji_not_exist, emojiId))
                )

            runCatching { getEmojiFile(target, emoji) }
                .getOrNull()
                ?.takeIf { it.exists() }
                ?.let { file ->
                    file.delete()
                    AppLogger.d(TAG, "Deleted file: ${file.absolutePath}")
                }

            preferences.deleteCustomEmoji(target, emojiId)

            AppLogger.d(TAG, "Successfully deleted emoji: $emojiId from target: $target")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error deleting custom emoji for target: $target", e)
            Result.failure(e)
        }
    }

    suspend fun deleteCategory(category: String): Result<Unit> = withContext(Dispatchers.IO) {
        deleteCategory(activePromptManager.getActivePrompt(), category)
    }

    suspend fun deleteCategory(target: ActivePrompt, category: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!isValidCategoryName(category)) {
                return@withContext Result.failure(
                    IllegalArgumentException(context.getString(R.string.invalid_category_name))
                )
            }
            val emojis = preferences.getEmojisForCategory(target, category).first()
            emojis.forEach { emoji ->
                runCatching { getEmojiFile(target, emoji) }
                    .getOrNull()
                    ?.takeIf { it.exists() }
                    ?.delete()
            }

            val categoryDir = getCategoryDir(target, category)
            if (categoryDir.exists()) {
                categoryDir.deleteRecursively()
            }

            preferences.deleteCategory(target, category)

            AppLogger.d(TAG, "Successfully deleted category: $category from target: $target")
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error deleting category for target: $target", e)
            Result.failure(e)
        }
    }

    fun getEmojiFile(target: ActivePrompt, emoji: CustomEmoji): File {
        val categoryDir = getCategoryDir(target, emoji.emotionCategory)
        return CustomEmojiStoragePolicy.resolveEmojiFile(
            categoryDir = categoryDir,
            fileName = emoji.fileName,
            supportedExtensions = SUPPORTED_EXTENSIONS,
        )
    }

    fun getEmojiUri(target: ActivePrompt, emoji: CustomEmoji): Uri {
        return Uri.fromFile(getEmojiFile(target, emoji))
    }

    fun getEmojisForCategory(target: ActivePrompt, category: String): Flow<List<CustomEmoji>> {
        if (!isValidCategoryName(category)) {
            return kotlinx.coroutines.flow.flowOf(emptyList())
        }
        return preferences.getEmojisForCategory(target, category).map { emojis ->
            emojis.filter(::isValidEmojiMetadata)
        }
    }

    fun getAllCategories(target: ActivePrompt): Flow<List<String>> {
        return preferences.getAllCategories(target).map { categories ->
            categories.filter(::isValidCategoryName)
        }
    }

    fun getAllEmojis(target: ActivePrompt): Flow<List<CustomEmoji>> {
        return preferences.getCustomEmojisFlow(target).map { emojis ->
            emojis.filter(::isValidEmojiMetadata)
        }
    }

    suspend fun addCategory(target: ActivePrompt, categoryName: String) {
        require(isValidCategoryName(categoryName)) { "Invalid emoji category: $categoryName" }
        initializeBuiltinEmojis(target)
        preferences.addCategory(target, categoryName)
    }

    suspend fun categoryExists(target: ActivePrompt, categoryName: String): Boolean {
        require(isValidCategoryName(categoryName)) { "Invalid emoji category: $categoryName" }
        initializeBuiltinEmojis(target)
        return getAllCategories(target).first().contains(categoryName)
    }

    suspend fun cloneEmojiSet(source: ActivePrompt, target: ActivePrompt) = withContext(Dispatchers.IO) {
        if (source == target) {
            initializeBuiltinEmojis(target)
            return@withContext
        }

        initializeBuiltinEmojis(source)
        deleteTarget(target)

        val categories =
            preferences.getAllCategories(source).first().filter(::isValidCategoryName)
        preferences.setAllCategories(target, categories.toSet())

        val emojis = preferences.getCustomEmojisFlow(source).first().filter(::isValidEmojiMetadata)
        val copiedEmojis = mutableListOf<CustomEmoji>()
        emojis.forEach { emoji ->
            val sourceFile = getEmojiFile(source, emoji)
            if (!sourceFile.exists()) return@forEach

            val targetDir = getCategoryDir(target, emoji.emotionCategory)
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }

            val targetFile = File(targetDir, emoji.fileName)
            sourceFile.copyTo(targetFile, overwrite = true)
            copiedEmojis.add(emoji)
        }

        preferences.setCustomEmojis(target, copiedEmojis)
        preferences.setBuiltinEmojisInitialized(target, true)
    }

    suspend fun deleteTarget(target: ActivePrompt) = withContext(Dispatchers.IO) {
        preferences.deleteTarget(target)
        getTargetBaseDir(target).deleteRecursively()
    }

    suspend fun cloneEmojisBetweenCharacterCards(sourceCharacterCardId: String, targetCharacterCardId: String) {
        cloneEmojiSet(
            ActivePrompt.CharacterCard(sourceCharacterCardId),
            ActivePrompt.CharacterCard(targetCharacterCardId)
        )
    }

    suspend fun deleteCharacterCardEmojis(characterCardId: String) {
        deleteTarget(ActivePrompt.CharacterCard(characterCardId))
    }

    suspend fun cloneEmojisBetweenCharacterGroups(sourceGroupId: String, targetGroupId: String) {
        cloneEmojiSet(
            ActivePrompt.CharacterGroup(sourceGroupId),
            ActivePrompt.CharacterGroup(targetGroupId)
        )
    }

    suspend fun deleteCharacterGroupEmojis(characterGroupId: String) {
        deleteTarget(ActivePrompt.CharacterGroup(characterGroupId))
    }

    fun isValidCategoryName(categoryName: String): Boolean {
        return CustomEmojiStoragePolicy.isValidCategoryName(categoryName)
    }

    private suspend fun copyBuiltinEmojisFromAssets(target: ActivePrompt) {
        try {
            val emojiAssetsDir = "emoji"
            val categories = context.assets.list(emojiAssetsDir)

            if (categories.isNullOrEmpty()) {
                AppLogger.w(TAG, "No built-in emoji categories found in assets.")
                return
            }

            preferences.addCategories(target, categories.toList())

            categories.forEach { category ->
                val files = context.assets.list("$emojiAssetsDir/$category")
                files?.forEach { fileName ->
                    val extension = fileName.substringAfterLast('.', "")
                    if (extension.lowercase() !in SUPPORTED_EXTENSIONS) {
                        return@forEach
                    }

                    val targetDir = getCategoryDir(target, category)
                    if (!targetDir.exists()) {
                        targetDir.mkdirs()
                    }

                    val targetFileName = "${UUID.randomUUID()}.$extension"
                    val targetFile = File(targetDir, targetFileName)

                    context.assets.open("$emojiAssetsDir/$category/$fileName").use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    }

                    val emoji = CustomEmoji(
                        emotionCategory = category,
                        fileName = targetFileName,
                        isBuiltInCategory = true
                    )
                    preferences.addCustomEmoji(target, emoji)
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error copying built-in emojis for target: $target", e)
            throw e
        }
    }

    private fun getTargetScopeDirName(target: ActivePrompt): String {
        return CustomEmojiStoragePolicy.targetScopeDirName(target)
    }

    private suspend fun purgeLegacyGlobalStorage() {
        if (legacyStoragePurged) return

        preferences.clearLegacyGlobalStorage()

        val emojiRootDir = File(context.filesDir, EMOJI_DIR)
        emojiRootDir.listFiles()?.forEach { child ->
            val isCurrentScopeDir = child.isDirectory &&
                (child.name.startsWith("character_card_") || child.name.startsWith("character_group_"))
            if (!isCurrentScopeDir) {
                child.deleteRecursively()
            }
        }

        legacyStoragePurged = true
    }

    private fun getTargetBaseDir(target: ActivePrompt): File {
        val emojiRoot = File(context.filesDir, EMOJI_DIR)
        return File(emojiRoot, getTargetScopeDirName(target)).canonicalFile.also { targetDir ->
            require(targetDir.parentFile == emojiRoot.canonicalFile) {
                "Emoji target path escaped the emoji root"
            }
        }
    }

    private fun getCategoryDir(target: ActivePrompt, category: String): File {
        return CustomEmojiStoragePolicy.resolveCategoryDir(getTargetBaseDir(target), category)
    }

    private fun isValidEmojiMetadata(emoji: CustomEmoji): Boolean =
        isValidCategoryName(emoji.emotionCategory) &&
            CustomEmojiStoragePolicy.isValidFileName(emoji.fileName, SUPPORTED_EXTENSIONS)

    private fun getFileExtension(uri: Uri): String? {
        return try {
            val extensionFromMimeType =
                if ("content" == uri.scheme) {
                    val mimeType = context.contentResolver.getType(uri)
                    MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
                } else {
                    null
                }

            extensionFromMimeType
                ?.takeIf { it.isNotBlank() }
                ?.lowercase()
                ?: getFileNameFromUri(uri)
                    ?.substringAfterLast('.', "")
                    ?.takeIf { it.isNotEmpty() }
                    ?.lowercase()
                ?: uri.path
                    ?.substringAfterLast('.', "")
                    ?.takeIf { it.isNotEmpty() }
                    ?.lowercase()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error getting file extension", e)
            null
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        if ("content" != uri.scheme) {
            return uri.lastPathSegment
        }

        return context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (columnIndex >= 0 && cursor.moveToFirst()) {
                cursor.getString(columnIndex)
            } else {
                null
            }
        }
    }
}
