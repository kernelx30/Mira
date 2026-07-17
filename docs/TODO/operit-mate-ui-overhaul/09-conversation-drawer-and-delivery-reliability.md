# Conversation drawer and delivery reliability

## Previous behavior

- The phone drawer still rendered the legacy grouping editor, display-mode controls, gesture tutorial, and new-group action.
- Conversations had a pinned flag but no independent archive state. Reusing the group field would destroy the user's existing organization.
- A first-response timeout stopped only the UI collector. The provider, plugin takeover, or ToolPkg producer could continue occupying the conversation.
- Companion notification delivery checked permission before posting, but did not handle permission changing between the check and the platform call.
- Floating-service broadcasts used the platform registration split and silently swallowed registration failures.

## Implemented behavior

- The phone drawer now exposes one full-width new-conversation action, search, pinned/recent/archived sections, and fixed Memory and Settings destinations.
- Long press and the row action button open the same bottom action sheet for pin, rename, archive, export, and delete.
- Archive state is a dedicated `ChatHistory` and Room field. Migration `23 -> 24` adds the column with an active default and preserves groups, pins, messages, and existing chat selection data.
- Migration `24 -> 25` repairs the legacy plain `companion_memory_fts` table into an Android-compatible FTS4 index, rebuilds its content from structured records, and preserves evidence, versions, and edges.
- Archiving the active conversation selects the newest active conversation or creates a clean one. A streaming conversation must finish before it can be archived.
- The header more menu exposes archive and restore actions for the current conversation.
- A first-response timeout now cancels the chat-scoped producer through `AIMessageManager` before joining the UI collector.
- The intermittent no-reply failure found in the device log came from treating the legacy plain search table as FTS5 and invoking `bm25()` before the provider request. Android's bundled SQLite surface does not consistently expose FTS5, so Mira now uses the portable FTS4 `MATCH` contract and scores relevance in Kotlin.
- Search-index failure falls back to structured lexical recall, and any wider companion-memory recall failure degrades to an empty memory note. Neither failure can abort a chat request; coroutine cancellation is still propagated.
- Message admission now rejects an already-running or already-reserved conversation before reporting the send as accepted. The composer therefore keeps its draft instead of clearing text that never entered the turn pipeline.
- Composer drafts are consumed only after the user message has been persisted. If model configuration, attachment preparation, or message construction fails first, the original draft remains available; text changed after dispatch is not cleared by the older request.
- Every foreground send is bound to the conversation that was active when the user tapped send. Switching conversations while dispatch starts no longer redirects the request into the newly selected conversation.
- Foreground sends now capture one immutable composer snapshot containing text, attachments, and the reply target. A backgrounded turn uses that snapshot and consumes only matching inputs, so switching conversations cannot drop the old attachment/reply or erase newly added composer state.
- Text drafts, attachments, and reply targets now each have a per-conversation composer slot. Switching away preserves unfinished input, switching back restores it, and a completed background dispatch consumes only its source conversation's matching state.
- Snapshot attachments and reply targets are consumed only after the user message is persisted. Attachment preparation or local persistence failure leaves the complete composer available for retry; group turns consume immediately after their manual user-message insert instead of after every role finishes.
- The main composer snapshot remains eligible for character-group orchestration even after the target conversation ID and text have been bound. Programmatic background prompts still bypass group planning.
- The response pipeline reports whether it actually accepted a coordinated dispatch. A late busy or empty-input rejection preserves composer state instead of clearing attachments after a turn that never started.
- `ChatServiceCore` propagates dispatch admission to companion delivery, Web SSE, floating replies, and chat-manager tools. Callers now retry or report the busy state immediately instead of waiting for a response stream that was never created.
- Main-composer and queued sends emit submitted events only after admission. Queue dispatch is bound to the checked conversation and restores the removed queue item when a late race rejects it.
- Pending-send queues now live in the view model as per-conversation lists. Switching away no longer discards queued text, and cancellation or delayed dispatch cannot redirect an item into the conversation that became active later.
- Immersive merge-send now combines every settled user bubble in sequence instead of submitting only the final line. The already-visible source bubbles are excluded from the request history for that turn, preventing the merged content from being injected twice.
- The complete send coroutine has an outer terminal guard. Failures before the former response-stream `try/finally` now surface an error, clear the per-conversation loading flag, release the send job, and leave the conversation ready for retry.
- The new-conversation text action supports a bounded two-line layout, and reply-mode choices wrap in the model sheet, preserving the explicit controls at narrow widths and enlarged font scales.
- The model control sheet exposes current context usage and its detailed breakdown without restoring a technical meter to the main chat header.
- The chat page remains the sole owner of IME/navigation-bar padding for its composer. Other phone surfaces once again receive the shared navigation-bar inset, and the drawer uses a non-bouncy transition.
- The phone drawer overlays a stationary chat surface with a restrained scrim instead of pushing the entire conversation off screen.
- The chat timeline no longer rebuilds a complete wrapper list and time-separator set for every streamed chunk. Lazy rows resolve their own separator state, and the multi-select index set is built only while selection mode is active.
- Notification delivery handles the runtime permission race, and floating-service broadcasts are registered as not exported through `ContextCompat`.
- The ANR monitor uses uptime instead of wall time, reports one warning after one second, captures one full dump only after five seconds, and keeps at most one main-thread probe queued.

## Verification

- `:app:compileDebugKotlin`: passed on 2026-07-16.
- `:app:testDebugUnitTest`: 445 tests across 89 suites, zero failures, errors, or skips.
- Dispatch admission, conversation binding, draft/attachment consumption, and group-orchestration eligibility regression suite: 7 tests, zero failures or errors after the final send-pipeline changes.
- Per-conversation composer restoration/consumption: 2 tests; lazy timeline separator parity and starter-state behavior: 2 tests.
- Archive entity and export round trips: 2 tests passed.
- Exported-device database rehearsal: version `23 -> 25`, 2 structured records retained, 2 FTS rows rebuilt, `MATCH` returned both expected records, and archive state defaulted to active.
- `:app:assembleDebug`: passed on 2026-07-16.
- APK package: `com.ai.assistance.mira`, version `1.12.0+4` (`44`).
- APK size: `240,774,492` bytes.
- APK SHA-256: `D19FF92AB1991F92288A77FB97A24AF746C0CB979E89C7A3FECD40BCFA385A03`.
- A full Android Lint scan was attempted but produced no task output for eight minutes and was stopped; the last completed report was stale. Its two non-Compose findings in the Mira manifest were resolved by removing the invalid protected-permission request and annotating the manifest-removal placeholder.
- Device installation and visual validation remain pending because ADB currently reports no connected device.

[DONE]
