package com.ai.assistance.operit.services.core

import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.ChatMessageDisplayMode
import com.ai.assistance.operit.data.model.InputProcessingState
import com.ai.assistance.operit.core.chat.SpeechContentMetadata
import com.ai.assistance.operit.util.ConversationContentVisibility
import com.ai.assistance.operit.util.stream.streamOf
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantResponseContentTest {
    @Test
    fun toolProgressSurvivesToolLifecycleAndOtherActiveChats() {
        assertFalse(
            shouldClearToolProgress(
                state = InputProcessingState.ToolProgress("tool", 0.5f),
                hasOtherActiveChat = false,
                hasPendingSummary = false,
            )
        )
        assertFalse(
            shouldClearToolProgress(
                state = InputProcessingState.Receiving("reply"),
                hasOtherActiveChat = true,
                hasPendingSummary = false,
            )
        )
        assertTrue(
            shouldClearToolProgress(
                state = InputProcessingState.Completed,
                hasOtherActiveChat = false,
                hasPendingSummary = false,
            )
        )
    }

    @Test
    fun cancellationNormalizesOnlyPendingUserDispatches() {
        assertTrue(
            isPendingUserDispatch(
                ChatMessage(
                    sender = "user",
                    content = "pending",
                    displayMode = ChatMessageDisplayMode.PENDING_DISPATCH,
                )
            )
        )
        assertFalse(
            isPendingUserDispatch(
                ChatMessage(sender = "user", content = "sent")
            )
        )
        assertFalse(
            isPendingUserDispatch(
                ChatMessage(
                    sender = "ai",
                    content = "pending",
                    displayMode = ChatMessageDisplayMode.PENDING_DISPATCH,
                )
            )
        )
    }

    @Test
    fun blankStreamAndBlankSegmentsAreEmpty() {
        assertFalse(hasAssistantResponseContent("  \n", listOf("", "  ")))
    }

    @Test
    fun rawStreamContentCountsAsResponse() {
        assertTrue(hasAssistantResponseContent("reply", emptyList()))
    }

    @Test
    fun internalMarkupDoesNotCountAsVisibleAssistantContent() {
        listOf(
            "<think>internal reasoning</think>",
            "<tool name=\"lookup\"></tool>",
            "<speech emotion=\"neutral\"></speech>",
            "<attachment id=\"file-1\" filename=\"note.txt\" type=\"text/plain\">content</attachment>",
            "<workspace_attachment path=\"/tmp/note.txt\">content</workspace_attachment>",
        ).forEach { raw ->
            val prepared = SpeechContentMetadata.prepare(raw)
            assertFalse(ConversationContentVisibility.hasRenderableAssistantContent(prepared.visibleText))
            assertFalse(hasAssistantResponseContent(raw, emptyList()))
        }
    }

    @Test
    fun visibleTextStillSurvivesInternalMarkupFiltering() {
        val raw = "<think>internal reasoning</think>实际回复"
        val prepared = SpeechContentMetadata.prepare(raw)

        assertTrue(ConversationContentVisibility.hasRenderableAssistantContent(prepared.visibleText))
        assertTrue(hasAssistantResponseContent(raw, emptyList()))
    }

    @Test
    fun waifuSegmentCountsAsResponse() {
        assertTrue(hasAssistantResponseContent("", listOf("reply")))
    }

    @Test
    fun cancelledWaifuResponseRespectsPartialPersistenceIntent() {
        assertTrue(
            shouldPersistCancelledWaifuResponse(
                isWaifuModeEnabled = true,
                preservePartialResponse = true,
                partialContent = "<tool name=\"start_app\"></tool>",
            )
        )
        assertFalse(
            shouldPersistCancelledWaifuResponse(
                isWaifuModeEnabled = true,
                preservePartialResponse = false,
                partialContent = "partial",
            )
        )
        assertFalse(
            shouldPersistCancelledWaifuResponse(
                isWaifuModeEnabled = false,
                preservePartialResponse = true,
                partialContent = "partial",
            )
        )
        assertFalse(
            shouldPersistCancelledWaifuResponse(
                isWaifuModeEnabled = true,
                preservePartialResponse = true,
                partialContent = "  ",
            )
        )
    }

    @Test
    fun cancellationSnapshotFindsStreamingOrMatchingImmersiveResponse() {
        val streaming = ChatMessage(sender = "ai", contentStream = streamOf("partial"))
        val immersive = ChatMessage(sender = "ai", content = "partial", sentAt = 42L)
        val stale = ChatMessage(sender = "ai", content = "old", sentAt = 41L)
        val user = ChatMessage(sender = "user", content = "hello", sentAt = 42L)

        assertTrue(isCancellationResponseCandidate(streaming, snapshotSentAt = null))
        assertTrue(isCancellationResponseCandidate(immersive, snapshotSentAt = 42L))
        assertFalse(isCancellationResponseCandidate(stale, snapshotSentAt = 42L))
        assertFalse(isCancellationResponseCandidate(user, snapshotSentAt = 42L))
    }

    @Test
    fun revisableResponsesDelayIrreversibleSpeechAndImmersiveSegments() {
        assertFalse(shouldStreamProvisionalAssistantOutput(hasRevisionEvents = true))
        assertTrue(shouldStreamProvisionalAssistantOutput(hasRevisionEvents = false))
    }
}
