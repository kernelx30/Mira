# 05 Mira companion product direction

## Decision

Mira is the product mainline. It is a companion-first Android agent, not a separate romance-chat shell and not a model playground with companion styling.

Product name: Mira

Positioning:

> An Android companion agent with a stable persona, visible relationship memory, voice, proactive moments, and Operit's full ability to act.

## Primary navigation

1. Companion: character presence, chat, voice, today's state, and unfinished promises.
2. Persona: character cards, relationship framing, voice, appearance, world book, and advanced bindings.
3. Memory: relationship, events, preferences, and world memories that users can inspect and correct.
4. Capabilities: reminders, schedules, workflows, packages, Skill, MCP, Shizuku, terminal, and automation.
5. Me: models, backup, privacy, theme, updates, and developer settings.

## Interaction rules

- The chat surface must prioritize the character, current relationship context, and the conversation.
- Model selection, raw prompts, permission matrices, and tool logs stay out of the default emotional path.
- Tool calls render as a compact activity timeline and expand only on demand.
- Permissions are requested when a feature is used, not as a first-launch wall.
- Active behavior is opt-in by category, time window, frequency, and character.
- Long-term memory is proposed and sourceable. Users can correct, ignore, or delete it.
- High-risk capabilities remain available but are authorized per role and per tool.

## Runtime ownership

- Operit remains the Android host for chat, character cards, memory graph, TTS/STT, local models, notifications, floating UI, tools, and workflows.
- Mira does not operate an application backend. Chat stays in Room, long-term memory stays in ObjectBox, preferences stay in DataStore/Keystore, and provider requests go directly to the endpoint configured by the user.
- Proactive delivery is an on-device chain: AlarmManager for the target time, an opt-in foreground service for resident companionship, and WorkManager only for recovery and network return.
- OpenSquilla patterns guide context projection, compaction, memory writes, and token budgets.
- RikkaHub is a density and Material 3 reference only; it does not replace the Operit runtime.

## Delivery order

### MVP

- Companion, Persona, and Memory product surfaces.
- Single-character chat, editable character cards, visible memory, voice, reminders, import/export, and local backup.

### Enhanced

- Group chat, world-book saves, relationship timeline, floating companionship, proactive moments, routing, and offline fallback.

### Local platform

- Role-scoped Skill/MCP and character-triggered workflows.
- Encrypted local export, restore, and user-managed file backup without a Mira account or server.

## Current implementation slice

- Five primary destinations are present.
- Companion chat uses the Mate identity and collapses tool execution.
- Model selection is being removed from the default composer hierarchy.
- Persona is being changed from avatar-only configuration to a role-first surface.
- Memory is being changed from graph-first tooling to a readable archive with the graph retained as an advanced view.
- Active reminders now use local alarms, local policy gates, direct provider calls, Room persistence, and notification delivery without an intermediary service.
