---
fork: https://github.com/kernelx30/Mira
status: in-progress
---

# Chat operation lifecycle

## Existing failure

Cancellation is split across the UI collector, `AIMessageManager` shared-stream producer,
`EnhancedAIService` provider calls, and tool jobs. A cancelled turn can finish cleanup after a
new turn starts, remove the new active entry, or cancel the new provider request.

## Intended result

- Every chat turn owns one identity-tokened operation and one producer job.
- Completion removes only the operation that completed.
- Cancellation waits for producer, provider, collector, and registered tool jobs before the chat
  accepts another turn.
- Plugin-controller ownership is transferred atomically so each execution is cancelled once.
- An eagerly shared response closes even when its producer scope is cancelled before dispatch.
- Tool jobs are registered before they can start.

## Scope

- `AIMessageManager.kt`
- `EnhancedAIService.kt`
- `MessageProcessingDelegate.kt`
- focused JVM policy and concurrency tests

No UI behavior, persistence format, model prompt, or public plugin interface changes are included.

## Verified invariants

- A naturally completed operation leaves the registry before its shared stream closes, and a late
  completion callback can only remove the operation instance that created it.
- Cancelling a producer scope still enters shared-stream cleanup, closes collectors, and waits for
  provider and registered tool cancellation before another turn is admitted.
- Provider retry streams retain their original revision event channel. Text may remain streaming,
  while irreversible speech and immersive segments wait for the authoritative post-rollback text.

Focused JVM coverage lives in `AIMessageOperationLifecycleTest`, `HotStreamShareTest`,
`MessageDispatchAdmissionTest`, and `AssistantResponseContentTest`.
