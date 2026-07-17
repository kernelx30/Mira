package com.ai.assistance.operit.services.core

import com.ai.assistance.operit.data.model.AttachmentInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageDispatchAdmissionTest {
    @Test
    fun groupPlanRequiresAtLeastOneSpeakingMember() {
        assertFalse(hasPlannedGroupSpeaker(emptyList()))
        assertFalse(hasPlannedGroupSpeaker(listOf(emptyList(), listOf(false, false))))
        assertTrue(hasPlannedGroupSpeaker(listOf(listOf(false), listOf(false, true))))
    }

    @Test
    fun acceptsIdleChatAfterReservation() {
        assertTrue(
            canAcceptMessageDispatch(
                isChatLoading = false,
                reservationAcquired = true,
            )
        )
    }

    @Test
    fun rejectsLoadingChatEvenWhenNoDispatchIsPending() {
        assertFalse(
            canAcceptMessageDispatch(
                isChatLoading = true,
                reservationAcquired = false,
            )
        )
    }

    @Test
    fun rejectsDuplicateDispatchBeforeTheTurnStarts() {
        assertFalse(
            canAcceptMessageDispatch(
                isChatLoading = false,
                reservationAcquired = false,
            )
        )
    }

    @Test
    fun bindsDispatchToTheChatThatWasActiveAtClickTime() {
        assertTrue(boundDispatchChatId(explicitChatId = null, chatIdAtClick = "chat-a") == "chat-a")
        assertTrue(boundDispatchChatId(explicitChatId = "chat-b", chatIdAtClick = "chat-a") == "chat-b")
    }

    @Test
    fun consumesOnlyTheDraftThatWasActuallyDispatched() {
        assertTrue(shouldConsumeUserDraft(currentText = "hello", dispatchedText = "hello"))
        assertFalse(shouldConsumeUserDraft(currentText = "hello again", dispatchedText = "hello"))
    }

    @Test
    fun composerSnapshotKeepsGroupOrchestrationEligibleAfterChatBinding() {
        assertTrue(
            permitsGroupOrchestrationDispatch(
                hasComposerSnapshot = true,
                messageTextOverride = "hello group",
                chatIdOverride = "chat-a",
            )
        )
        assertFalse(
            permitsGroupOrchestrationDispatch(
                hasComposerSnapshot = false,
                messageTextOverride = "background prompt",
                chatIdOverride = "chat-a",
            )
        )
        assertTrue(
            permitsGroupOrchestrationDispatch(
                hasComposerSnapshot = false,
                messageTextOverride = "merged visible user text",
                chatIdOverride = "chat-a",
                allowBoundUserText = true,
            )
        )
    }

    @Test
    fun consumesOnlyAttachmentsCapturedByTheDispatchedTurn() {
        fun attachment(path: String, content: String = "") =
            AttachmentInfo(
                filePath = path,
                fileName = path.substringAfterLast('/'),
                mimeType = "text/plain",
                fileSize = content.length.toLong(),
                content = content,
            )

        val dispatched = attachment("/old.txt", "old")
        val replacementAtSamePath = attachment("/old.txt", "new")
        val newlyAdded = attachment("/new.txt", "new")

        assertEquals(
            listOf(replacementAtSamePath, newlyAdded),
            remainingAttachmentsAfterConsume(
                current = listOf(replacementAtSamePath, newlyAdded),
                consumed = listOf(dispatched),
            )
        )
        assertEquals(
            listOf(newlyAdded),
            remainingAttachmentsAfterConsume(
                current = listOf(dispatched, newlyAdded),
                consumed = listOf(dispatched),
            )
        )
    }
}
