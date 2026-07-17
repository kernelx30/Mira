# 04 OpenSquilla memory and token budget

Reference reviewed: `opensquilla/opensquilla` at local checkout `927dd13`.

## Useful model

OpenSquilla separates three concerns that must not be collapsed into one feature:

- Durable memory stores reusable facts across sessions.
- Compaction summarizes only an old conversation prefix and keeps a recent tail verbatim.
- Tool projection keeps the complete runtime output outside the provider prompt and supplies a bounded preview plus a retrieval handle.

## Implemented in Operit Mate

- Tool outputs over 12,000 characters are archived under the app's `files/tool_results` directory.
- Provider prompts receive a head/tail preview, `tool_result_handle`, exact path, source length, and SHA-256.
- The existing `read_file_part` tool can retrieve a narrow range from the archived output.
- Chat rendering still receives the normal bounded tool result instead of the provider projection.
- History compaction summarizes a completed old prefix and protects the latest eight user/assistant messages.
- Summary input uses condensed tool calls/results rather than replaying full XML payloads.
- Locally persisted companion sticker URIs stay available to chat rendering but become compact semantic markers in provider and summary history.
- A summary is rejected when its estimated token count is not at least 15 percent smaller.
- Pending long-term-memory candidates get a best-effort 15-second flush before compaction.
- Flush cancellation returns candidates to the pending queue; failures remain retryable.
- The Room chat transcript is the raw fallback and is not deleted by compaction.
- Logs record approximate before/after token counts and tool projection savings.

## Deliberately not copied

- Python storage and plugin code are not embedded in the Android runtime.
- OpenSquilla Dream is not enabled yet. Operit's ObjectBox memory graph needs a phone-friendly idle/charging policy, merge receipts, and a quarantine surface before background consolidation is safe.
- Prompt-cache placement is provider-specific. The stable system prompt remains unchanged; volatile memory and projected tool results stay near the request tail.

## Validation

- `:app:compileDebugKotlin`
- `HistoryCompactionPlannerTest`
- `CompanionEmojiMarkupTest`
- Device test with a tool result larger than 12,000 characters
- Confirm archive creation, projected prompt diagnostics, ranged retrieval, recent-tail retention, and failed-flush retry
