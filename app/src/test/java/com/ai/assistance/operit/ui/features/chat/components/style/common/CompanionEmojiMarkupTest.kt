package com.ai.assistance.operit.ui.features.chat.components.style.common

import com.ai.assistance.operit.core.chat.CompanionEmojiMarkup
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CompanionEmojiMarkupTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun parsesStandaloneCustomEmoji() {
        val content =
            CompanionEmojiMarkup.parseStandalone(
                "![happy](file:///data/user/0/com.example/files/custom_emoji/card/happy/a.gif)"
            )

        assertNotNull(content)
        assertEquals("happy", content?.emotion)
        assertEquals(
            "file:///data/user/0/com.example/files/custom_emoji/card/happy/a.gif",
            content?.imageUrl,
        )
    }

    @Test
    fun rejectsRegularMarkdownImage() {
        assertNull(CompanionEmojiMarkup.parseStandalone("![photo](https://example.com/photo.png)"))
    }

    @Test
    fun rejectsUnsupportedLocalFileType() {
        assertNull(
            CompanionEmojiMarkup.parseStandalone(
                "![happy](file:///data/user/0/com.example/files/custom_emoji/card/happy/a.txt)"
            )
        )
    }

    @Test
    fun rejectsFileUriWithRemoteAuthority() {
        assertNull(
            CompanionEmojiMarkup.parseStandalone(
                "![happy](file://example.com/custom_emoji/card/happy/a.png)"
            )
        )
    }

    @Test
    fun rejectsImageMixedWithText() {
        assertNull(
            CompanionEmojiMarkup.parseStandalone(
                "hello ![happy](file:///data/user/0/com.example/files/custom_emoji/card/happy/a.png)"
            )
        )
    }

    @Test
    fun rejectsTraversalEscapingCustomEmojiDirectory() {
        assertNull(
            CompanionEmojiMarkup.parseStandalone(
                "![happy](file:///data/user/0/com.example/files/custom_emoji/card/../../secrets.png)"
            )
        )
    }

    @Test
    fun rejectsFileOutsideProvidedAppEmojiRoot() {
        val appFiles = temporaryFolder.newFolder("files")
        val otherEmoji = File(temporaryFolder.root, "other/custom_emoji/happy/a.png")

        assertNull(
            CompanionEmojiMarkup.parseStandalone(
                content = "![happy](${otherEmoji.toURI()})",
                customEmojiRoot = File(appFiles, "custom_emoji"),
            )
        )
    }

    @Test
    fun acceptsFileInsideProvidedAppEmojiRoot() {
        val emojiRoot = temporaryFolder.newFolder("files", "custom_emoji")
        val emojiFile = File(emojiRoot, "character_card_zero/happy/a.png")

        val parsed =
            CompanionEmojiMarkup.parseStandalone(
                content = "![happy](${emojiFile.toURI()})",
                customEmojiRoot = emojiRoot,
            )

        assertNotNull(parsed)
    }

    @Test
    fun projectsAssistantEmojiToCompactModelSemantics() {
        val projected =
            CompanionEmojiMarkup.projectForModel(
                PromptTurn(
                    kind = PromptTurnKind.ASSISTANT,
                    content =
                        "![Happy](file:///data/user/0/com.example/files/custom_emoji/card/happy/a.webp)",
                )
            )

        assertEquals("[sent sticker: happy]", projected.content)
    }

    @Test
    fun doesNotProjectUserContent() {
        val content =
            "![happy](file:///data/user/0/com.example/files/custom_emoji/card/happy/a.png)"
        val projected =
            CompanionEmojiMarkup.projectForModel(
                PromptTurn(kind = PromptTurnKind.USER, content = content)
            )

        assertEquals(content, projected.content)
    }
}
