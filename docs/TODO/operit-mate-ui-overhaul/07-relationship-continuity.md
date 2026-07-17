---
fork: https://github.com/AAswordman/Operit
branch: companion-material
status: implemented-awaiting-device-validation
---

# Mira relationship continuity

## Scope

This pass implements the first companion product loop:

`conversation commitment -> structured relationship memory -> timeline -> character reminder -> next conversation follow-up`

It keeps the existing Operit memory graph, WorkManager runtime, role cards, profile binding, notifications, skills, MCP, Shizuku, workflows, terminal, and local model support.

## Structured event metadata [DONE]

Companion events use existing `MemoryProperty` relations and do not change the ObjectBox entity schema.

- `companion.kind`: `promise`, `event`, `anniversary`, or `reminder`
- `companion.eventAt`: epoch milliseconds
- `companion.status`: `pending`, `done`, or `cancelled`
- `companion.reminderText`: one short notification sentence in character voice
- `companion.characterId` and `companion.characterGroupId`: role ownership
- `companion.characterName`: notification identity
- `companion.chatId`: source conversation
- `companion.notifiedAt`: latest delivered reminder

Memory extraction accepts confirmed future commitments while continuing to reject tentative plans and generic TODO suggestions. Relative dates are resolved against an explicit local RFC 3339 timestamp.

## Immediate extraction [DONE]

Normal memory candidates remain batched. Messages containing an explicit reminder or a time reference combined with a commitment action request immediate processing, so short reminders do not wait for the five-candidate batch threshold.

The language model may propose event metadata, but local code owns role binding, profile binding, notification permission, quiet hours, daily limits, scheduling, and status checks.

## Timeline [DONE]

The relationship archive now opens on a real timeline tab. Structured events use their event time and legacy event memories continue to appear using their update time.

Users can mark a pending event done or reopen it. Completing or cancelling an event removes its scheduled WorkManager job.

## Role-scoped presence controls [DONE]

`Persona -> Presence` stores settings independently for each character or group.

- Master reminder switch
- `Explicit only`, `Occasional`, and `Daily` intensity modes
- Quiet-hours start and end
- Daily notification limit from one to eight

The default is `Explicit only`. Inferred promises do not notify until the user chooses a more active mode.

## Notification and return path [DONE]

One-time WorkManager jobs reload the memory before delivery and refuse to notify completed, cancelled, timeless, disabled, over-budget, or quiet-hours events. Quiet-hours and budget decisions defer to the next allowed time.

Notification taps return to the source chat when the chat still exists.

Android 13 notification permission is requested at the point of use, not during startup. Mira requests it when the user sends an explicit reminder instruction, enables role reminders, or enables reply notifications. Granting it resynchronizes stored companion events.

## Conversation continuity and token budget [DONE]

At most six relevant events are injected into the next turn. Selection prioritizes pending items, nearby event times, recently delivered reminders, and recently completed items for the current character or group.

The prompt tells the role to continue naturally without reciting the list or claiming pending work is complete.

Chat-local UI state is also isolated by conversation. Switching chats clears edit and reply targets, restores follow-latest behavior, rebuilds return-to-latest state, and prevents selection, delete, share, rollback, resend, export, queue, and message-list identity from leaking into another conversation.

## Backup compatibility [DONE]

Memory export version `1.1` includes property values. Imports remain compatible with version `1.0`, where `propertyValues` is absent. Imported reminders are rescheduled after import.

## Verification

- The current Mira regression suite passed `67/67` tests across 16 suites with zero failures, errors, or skips. It covers reminder classification and policy, sender-specific persisted timestamps, role auto-read overrides, prompt recovery, history compaction, custom emoji storage, chat time separators, and return-to-latest visibility.
- `:app:assembleDebug` passed
- APK package: `com.ai.assistance.mira`
- APK label: `Mira`
- APK version: `1.12.0+4` / `44`
- APK architecture: `arm64-v8a`
- APK size: `233,913,587` bytes
- APK SHA-256: `EEC397739CFA16E0A3AFC83C02115AE21F7926FD45DA6711A7398D9BC097D957`
- `git diff --check` passed
- Device install, notification timing, and visual screenshots remain pending because ADB was intentionally disabled
