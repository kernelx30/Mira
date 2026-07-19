package com.ai.assistance.operit.services.core

import com.ai.assistance.operit.data.model.AttachmentInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

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
    fun completedTurnCannotClearAReplacementSendJob() {
        assertTrue(
            shouldClearCompletedSendJob(
                runtimeGeneration = 4L,
                completedGeneration = 4L,
                isSameJob = true,
            )
        )
        assertFalse(
            shouldClearCompletedSendJob(
                runtimeGeneration = 5L,
                completedGeneration = 4L,
                isSameJob = true,
            )
        )
        assertFalse(
            shouldClearCompletedSendJob(
                runtimeGeneration = 4L,
                completedGeneration = 4L,
                isSameJob = false,
            )
        )
    }

    @Test
    fun cancellingChatRemainsBusyAfterItsSendJobStops() {
        assertTrue(isChatRuntimeBusy(isLoading = true, isCancelling = false))
        assertTrue(isChatRuntimeBusy(isLoading = false, isCancelling = true))
        assertFalse(isChatRuntimeBusy(isLoading = false, isCancelling = false))
    }

    @Test
    fun destructiveCancellationWaitsForAnEarlierCancellationToFinish() = runBlocking {
        val gate = ChatCancellationGate()
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()

        val first = async {
            gate.run {
                order += "first-start"
                firstEntered.complete(Unit)
                releaseFirst.await()
                order += "first-end"
            }
        }
        firstEntered.await()
        val destructive = async {
            gate.run { order += "destructive" }
        }
        yield()

        assertEquals(listOf("first-start"), order)
        releaseFirst.complete(Unit)
        first.await()
        destructive.await()
        assertEquals(listOf("first-start", "first-end", "destructive"), order)
    }

    @Test
    fun callerCancellationDoesNotReleaseGateBeforeWritersStop() = runBlocking {
        val gate = ChatCancellationGate()
        val writerStopping = CompletableDeferred<Unit>()
        val releaseWriter = CompletableDeferred<Unit>()
        val destructiveEntered = CompletableDeferred<Unit>()
        val writer = launch {
            try {
                CompletableDeferred<Unit>().await()
            } finally {
                withContext(NonCancellable) {
                    writerStopping.complete(Unit)
                    releaseWriter.await()
                }
            }
        }
        val firstCancellation = launch {
            gate.run { cancelAndJoinJobs(listOf(writer)) }
        }
        writerStopping.await()
        firstCancellation.cancel()
        val destructive = launch {
            gate.run { destructiveEntered.complete(Unit) }
        }
        yield()

        assertFalse(destructiveEntered.isCompleted)
        releaseWriter.complete(Unit)
        firstCancellation.join()
        destructive.join()
        assertTrue(destructiveEntered.isCompleted)
    }

    @Test
    fun memoryEnqueueFailureDoesNotAbortChatDispatch() = runBlocking {
        val failure = isolateMemoryEnqueueFailure { error("ObjectBox unavailable") }

        assertEquals("ObjectBox unavailable", failure?.message)
    }

    @Test(expected = CancellationException::class)
    fun memoryEnqueueCancellationStillCancelsChatDispatch() {
        runBlocking {
            isolateMemoryEnqueueFailure { throw CancellationException("turn cancelled") }
        }
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
