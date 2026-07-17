# Chat shell hardening

## Previous behavior

- The chat header reused one microphone permission result callback for both floating-window and fullscreen voice modes. A first-time voice-call permission grant could therefore open the window mode.
- Workspace and AI Computer covered the chat header but did not expose an application-level close control.
- Scroll observers captured the initial auto-scroll and drag values for the lifetime of the list state.
- Streaming text repeatedly restarted animated scroll operations.
- Jumping to an older message could disable auto-scroll without exposing a way back to the latest message.
- The chat page still accepted the app-wide drawer swipe, competing with horizontal message content.
- Several retained editor, attachment, queue, workspace, and navigation controls exposed touch targets below 48dp.
- Selection, edit, reply, rollback, resend, export, queue, and scroll state could survive a conversation switch and act on the wrong chat.

## Intended behavior

- Each microphone permission request resumes the exact mode that initiated it.
- Every fullscreen capability has a visible 48dp back control and exits on the system back action.
- User drag state and auto-scroll callbacks remain current without restarting the observer coroutine.
- New messages animate into view once; streaming growth stays pinned with throttled, non-animated movement.
- A disabled follow state or newer history below the active window always reveals the return-to-latest control.
- Chat owns its local gestures and composer Insets; fullscreen capabilities have one explicit IME owner.
- Interactive controls in the audited chat, workspace, and navigation paths expose at least 48dp targets.
- Conversation-scoped UI state and LazyColumn identities are keyed by chat ID; switching chats clears stale action targets and resumes following the newest message.

## Implementation scope

- `ChatScreenHeader.kt` and `ChatViewModel.kt`: distinct post-permission routes for window and fullscreen voice modes. [DONE]
- `WorkspaceScreen.kt`, `ComputerScreen.kt`, and `AIChatScreen.kt`: shared fullscreen capability header and back handling. [DONE]
- `ScrollToBottomButton.kt`, `ChatArea.kt`, and `ChatScrollExtensions.kt`: current-state observation and stream-aware end scrolling. [DONE]
- `AIChatScreen.kt`, `ChatScreenContent.kt`, `WorkspaceManager.kt`, and `AppContent.kt`: remove dead gesture state, use size-only measurement, and narrow IME observation. [DONE]
- `PhoneLayout.kt`, chat attachment/editor components, workspace controls, and drawer components: prevent chat drawer gesture conflicts and normalize touch targets. [DONE]
- `AIChatScreen.kt`, `ChatScreenContent.kt`, `ChatArea.kt`, `ChatScrollNavigator.kt`, and `FloatingChatWindowScreen.kt`: isolate transient UI state, navigation state, and message keys by conversation. [DONE]
- Narrow-screen, font-scale, IME, attachment-sheet, and long-history device validation. Pending while ADB is disabled.

## Verification record

- Static whitespace check: passed on 2026-07-15.
- Mira regression suite: `67/67` tests passed across 16 suites with zero failures, errors, or skips.
- The broader repository unit suite is not green: `391` tests completed with `12` failures in `ConditionEvaluator`, `ChatMarkupRegex`, `ChatUtils`, and `StreamingJsonXmlConverter`; none are part of the Mira regression set.
- `:app:compileDebugKotlin` and `:app:assembleDebug`: passed on 2026-07-15.
- APK: `com.ai.assistance.mira`, `1.12.0+4` (`44`), `arm64-v8a`, APK Signature Scheme v2 verified.
- APK size: `233,913,587` bytes.
- APK SHA-256: `EEC397739CFA16E0A3AFC83C02115AE21F7926FD45DA6711A7398D9BC097D957`.
- Device validation: intentionally deferred until ADB is enabled by the user.
