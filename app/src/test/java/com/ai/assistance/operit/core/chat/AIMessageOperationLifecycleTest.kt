package com.ai.assistance.operit.core.chat

import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AIMessageOperationLifecycleTest {
    @Test
    fun lateCompletionCannotRemoveReplacementOperation() {
        val registry = ActiveOperationRegistry<Any>()
        val first = Any()
        val replacement = Any()

        assertTrue(registry.register("chat", first))
        assertTrue(registry.removeIfCurrent("chat", first))
        assertTrue(registry.register("chat", replacement))
        assertFalse(registry.removeIfCurrent("chat", first))
        assertSame(replacement, registry.current("chat"))
        assertTrue(registry.removeIfCurrent("chat", replacement))
    }

    @Test
    fun duplicateOperationRegistrationIsRejected() {
        val registry = ActiveOperationRegistry<Any>()

        assertTrue(registry.register("chat", Any()))
        assertFalse(registry.register("chat", Any()))
        assertEquals(setOf("chat"), registry.keys())
    }

    @Test
    fun cancellationHandleWaitsForProducerAndProviderCleanup() = runBlocking {
        val completion = CompletableDeferred<Unit>()
        val handle = OperationCancellationHandle(completion)
        val waiter = async { handle.awaitCompletion() }
        yield()

        assertFalse(waiter.isCompleted)
        completion.complete(Unit)
        waiter.await()
        assertTrue(waiter.isCompleted)
    }

    @Test
    fun cancellationOwnsSlotUntilItsBarrierFinishes() = runBlocking {
        val registry = ActiveOperationRegistry<Any>()
        val lifecycle = OperationLifecycle()
        val first = Any()
        val replacement = Any()

        assertTrue(registry.register("chat", first))

        assertEquals(OperationCancellationStart.STARTED, lifecycle.beginCancellation())
        assertFalse(lifecycle.beginCompletion())
        assertEquals(
            OperationCancellationStart.ALREADY_CANCELLING,
            lifecycle.beginCancellation(),
        )
        assertFalse(lifecycle.cancellationCompletion().isCompleted)

        val replacementAdmission = async {
            lifecycle.cancellationCompletion().await()
            while (!registry.register("chat", replacement)) {
                yield()
            }
        }
        yield()

        assertFalse(replacementAdmission.isCompleted)
        assertSame(first, registry.current("chat"))
        lifecycle.finishCancellation()
        assertTrue(registry.removeIfCurrent("chat", first))
        replacementAdmission.await()

        assertTrue(lifecycle.cancellationCompletion().isCompleted)
        assertSame(replacement, registry.current("chat"))
    }

    @Test
    fun cancellingOperationKeepsControllerForCancellationOwner() {
        val lifecycle = OperationLifecycle()
        val controller = Any()
        val controllerReference = AtomicReference<Any?>(controller)

        assertEquals(OperationCancellationStart.STARTED, lifecycle.beginCancellation())

        assertFalse(clearControllerAfterNaturalCompletion(lifecycle, controllerReference))
        assertSame(controller, controllerReference.get())
    }

    @Test
    fun naturalCompletionPreventsLateCancellationFromStarting() {
        val lifecycle = OperationLifecycle()
        val controllerReference = AtomicReference<Any?>(Any())

        assertTrue(lifecycle.beginCompletion())
        assertTrue(clearControllerAfterNaturalCompletion(lifecycle, controllerReference))
        assertEquals(null, controllerReference.get())
        assertEquals(
            OperationCancellationStart.ALREADY_COMPLETING,
            lifecycle.beginCancellation(),
        )
    }

    @Test
    fun expectedControllerHasExactlyOneCancellationOwner() {
        val controller = Any()
        val controllerReference = AtomicReference<Any?>(controller)

        assertSame(controller, takeExpectedController(controllerReference, controller))
        assertEquals(null, takeExpectedController(controllerReference, controller))
        assertEquals(null, takeCurrentController(controllerReference))
    }

    @Test
    fun cancellationOwnerPreventsStartupPathFromTakingControllerAgain() {
        val controller = Any()
        val controllerReference = AtomicReference<Any?>(controller)

        assertSame(controller, takeCurrentController(controllerReference))
        assertEquals(null, takeExpectedController(controllerReference, controller))
    }
}
