package com.ai.assistance.operit.util.stream

import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HotStreamShareTest {
    @Test
    fun onBeforeCloseFinishesBeforeCollectorCanComplete() = runBlocking {
        val beforeCloseStarted = CompletableDeferred<Unit>()
        val allowClose = CompletableDeferred<Unit>()
        val valueCollected = CompletableDeferred<Unit>()
        val collectedValues = mutableListOf<String>()
        val shared =
            finiteStream("value").share(
                scope = this,
                replay = 1,
                onBeforeClose = {
                    beforeCloseStarted.complete(Unit)
                    allowClose.await()
                }
            )
        val collector =
            async {
                shared.collect { value ->
                    collectedValues += value
                    valueCollected.complete(Unit)
                }
            }

        try {
            withTimeout(1_000) { beforeCloseStarted.await() }
            withTimeout(1_000) { valueCollected.await() }

            assertFalse(collector.isCompleted)
            assertEquals(listOf("value"), collectedValues)
        } finally {
            allowClose.complete(Unit)
        }

        withTimeout(1_000) { collector.await() }
        assertTrue(collector.isCompleted)
    }

    @Test
    fun onBeforeCloseFailureStillClosesStreamAndRunsOnComplete() = runBlocking {
        val expectedFailure = IllegalStateException("before-close failed")
        val reportedFailure = CompletableDeferred<Throwable>()
        val completionCalled = CompletableDeferred<Unit>()
        val sharingJob = SupervisorJob(coroutineContext[Job])
        val sharingScope =
            CoroutineScope(
                coroutineContext +
                    sharingJob +
                    CoroutineExceptionHandler { _, throwable ->
                        reportedFailure.complete(throwable)
                    }
            )

        try {
            val shared =
                finiteStream<String>().share(
                    scope = sharingScope,
                    onComplete = { completionCalled.complete(Unit) },
                    onBeforeClose = { throw expectedFailure }
                )
            val collector = async { shared.collect {} }

            withTimeout(1_000) { completionCalled.await() }
            withTimeout(1_000) { collector.await() }
            val reported = withTimeout(1_000) { reportedFailure.await() }
            assertTrue(reported is IllegalStateException)
            assertEquals(expectedFailure.message, reported.message)
            assertTrue(collector.isCompleted)
        } finally {
            sharingJob.cancelAndJoin()
        }
    }

    @Test
    fun revisableSharingKeepsOriginalSharedEventChannel() = runBlocking {
        val eventChannel = MutableSharedStream<TextStreamEvent>(replay = 1)
        val source = revisableFiniteStream(eventChannel, "value")
        val sharingJob = SupervisorJob(coroutineContext[Job])
        val sharingScope = CoroutineScope(coroutineContext + sharingJob)

        try {
            val shared = source.shareRevisable(scope = sharingScope, replay = 1)

            assertTrue(shared is TextStreamEventCarrier)
            assertSame(eventChannel, (shared as TextStreamEventCarrier).eventChannel)
        } finally {
            sharingJob.cancelAndJoin()
        }
    }

    @Test
    fun sharingInCancelledScopeStillClosesAndRunsCleanup() = runBlocking {
        val cancelledParent = Job().apply { cancel() }
        val sharingScope = CoroutineScope(coroutineContext + cancelledParent)
        val beforeCloseCalled = CompletableDeferred<Unit>()
        val completionCalled = CompletableDeferred<Unit>()
        var upstreamCollected = false
        val source =
            object : Stream<String> {
                override val isLocked: Boolean = false
                override val bufferedCount: Int = 0

                override suspend fun lock() = Unit

                override suspend fun unlock() = Unit

                override fun clearBuffer() = Unit

                override suspend fun collect(collector: StreamCollector<String>) {
                    upstreamCollected = true
                    collector.emit("unexpected")
                }
            }

        val shared =
            source.share(
                scope = sharingScope,
                onBeforeClose = {
                    delay(1)
                    beforeCloseCalled.complete(Unit)
                },
                onComplete = { completionCalled.complete(Unit) },
            )
        val collector = async { shared.collect {} }

        withTimeout(1_000) { beforeCloseCalled.await() }
        withTimeout(1_000) { completionCalled.await() }
        withTimeout(1_000) { collector.await() }

        assertFalse(upstreamCollected)
        assertTrue(collector.isCompleted)
    }

    private fun <T> finiteStream(vararg values: T): Stream<T> =
        object : Stream<T> {
            override val isLocked: Boolean = false
            override val bufferedCount: Int = 0

            override suspend fun lock() = Unit

            override suspend fun unlock() = Unit

            override fun clearBuffer() = Unit

            override suspend fun collect(collector: StreamCollector<T>) {
                values.forEach { value -> collector.emit(value) }
            }
        }

    private fun revisableFiniteStream(
        eventChannel: SharedStream<TextStreamEvent>,
        vararg values: String
    ): RevisableTextStream =
        object : RevisableTextStream {
            override val eventChannel: SharedStream<TextStreamEvent> = eventChannel
            override val isLocked: Boolean = false
            override val bufferedCount: Int = 0

            override suspend fun lock() = Unit

            override suspend fun unlock() = Unit

            override fun clearBuffer() = Unit

            override suspend fun collect(collector: StreamCollector<String>) {
                values.forEach { value -> collector.emit(value) }
            }
        }
}
