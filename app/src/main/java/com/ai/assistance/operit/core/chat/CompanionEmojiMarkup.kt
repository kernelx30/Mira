package com.ai.assistance.operit.core.chat

import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import java.io.File
import java.net.URI
import java.util.Locale

data class CompanionEmojiContent(
    val emotion: String,
    val imageUrl: String,
)

object CompanionEmojiMarkup {
    private const val CUSTOM_EMOJI_DIRECTORY = "custom_emoji"
    private val supportedExtensions = setOf("jpg", "jpeg", "png", "gif", "webp")
    private val standaloneImageRegex =
        Regex(
            pattern = """^\s*!\[([a-z0-9_]+)]\((file:[^)]+)\)\s*$""",
            option = RegexOption.IGNORE_CASE,
        )

    fun parseStandalone(
        content: String,
        customEmojiRoot: File? = null,
    ): CompanionEmojiContent? {
        val match = standaloneImageRegex.matchEntire(content) ?: return null
        val imageUrl = match.groupValues[2]
        val imageFile = resolveCanonicalFile(imageUrl) ?: return null
        if (imageFile.extension.lowercase(Locale.ROOT) !in supportedExtensions) {
            return null
        }

        val isTrustedPath =
            if (customEmojiRoot != null) {
                val canonicalRoot = runCatching { customEmojiRoot.canonicalFile }.getOrNull()
                    ?: return null
                imageFile.isDescendantOf(canonicalRoot)
            } else {
                imageFile.hasAncestorNamed(CUSTOM_EMOJI_DIRECTORY)
            }
        if (!isTrustedPath) {
            return null
        }

        return CompanionEmojiContent(
            emotion = match.groupValues[1].lowercase(Locale.ROOT),
            imageUrl = imageUrl,
        )
    }

    private fun resolveCanonicalFile(imageUrl: String): File? {
        return runCatching {
            val uri = URI(imageUrl)
            if (!uri.scheme.equals("file", ignoreCase = true) || !uri.authority.isNullOrEmpty()) {
                return null
            }
            File(uri).canonicalFile
        }.getOrNull()
    }

    private fun File.isDescendantOf(root: File): Boolean {
        var current = parentFile
        while (current != null) {
            if (current == root) {
                return true
            }
            current = current.parentFile
        }
        return false
    }

    private fun File.hasAncestorNamed(directoryName: String): Boolean {
        var current = parentFile
        while (current != null) {
            if (current.name.equals(directoryName, ignoreCase = true)) {
                return true
            }
            current = current.parentFile
        }
        return false
    }

    fun projectForModel(turn: PromptTurn): PromptTurn {
        if (turn.kind != PromptTurnKind.ASSISTANT) {
            return turn
        }

        val emoji = parseStandalone(turn.content) ?: return turn
        return turn.copy(content = "[sent sticker: ${emoji.emotion}]")
    }
}
