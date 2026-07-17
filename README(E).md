# Mira

<div align="center">
  <strong>A local-first AI companion for Android</strong><br>
  Conversation, long-term memory, voice companionship, and full agent tooling
</div>

<div align="center">
  <a href="README.md">中文</a> ·
  <a href="https://github.com/kernelx30/Mira/releases">Releases</a> ·
  <a href="https://github.com/kernelx30/Mira/issues">Issues</a> ·
  <a href="https://github.com/AAswordman/Operit">Upstream Operit</a>
</div>

<div align="center">
  <img src="https://img.shields.io/github/license/kernelx30/Mira" alt="License">
  <img src="https://img.shields.io/github/last-commit/kernelx30/Mira" alt="Last commit">
  <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84" alt="Android 8.0+">
  <img src="https://img.shields.io/badge/Kotlin-Compose-7F52FF" alt="Kotlin and Compose">
</div>

## What Mira Is

Mira is an Android AI companion built through a substantial redesign of [Operit](https://github.com/AAswordman/Operit). Its product flow is centered on four ideas:

```text
Conversation -> Memory -> Companionship -> Control
```

Mira puts character relationships, long-term memory, voice interaction, and proactive companionship in the foreground while retaining Operit's tool calling, MCP, Skill, script, tool package, terminal, workflow, and device-assistance capabilities.

The application ID is `com.ai.assistance.mira`, so Mira can be installed alongside upstream Operit.

## Screenshots

<div align="center">
  <img src="docs/assets/README_examples/mira/chat-home.png" width="42%" alt="Mira chat screen">
  <img src="docs/assets/README_examples/mira/settings.png" width="42%" alt="Mira settings screen">
</div>

## Current Capabilities

### Conversation

- Character-based chat, history, search, archive, and export
- Streaming output, immersive multi-bubble replies, and response recovery
- Per-conversation model, reasoning, context-window, and memory controls
- Attachments, voice input, automatic reading, and voice-session features
- Tool execution traces, failure recovery, and conversation-switch protection

### Relationship and Memory

- User, companion, relationship, and conversation memory scopes
- Facts, preferences, events, boundaries, commitments, and relationship state
- Evidence, versions, links, correction, and manual memory saving
- Local Room/SQLite persistence and full-text retrieval
- Automatic memory context assembly before each response

### Voice and Presence

- Multiple configurable TTS and STT providers
- Doubao, MiniMax, MiMo, OpenAI, and generic HTTP TTS support
- Emotion, pacing, pitch, and segmented playback direction
- Spoken-text highlighting, playback queues, and interruption on new messages
- Proactive reminders, notifications, a companion bubble, and quick replies

### Agent and Extensions

- Tool calling remains available by default
- MCP, Skill, script, and tool package installation
- Shizuku, terminal, files, workspaces, and device assistance
- MNN and llama.cpp local-model modules
- Compatibility with existing Operit plugins and market resources

## Plugin Compatibility

Mira currently continues to read the Operit-compatible market. Plugins are downloaded from their respective authors' repositories, so existing Skills, MCP servers, scripts, and tool packages remain usable.

The following internal identifiers are intentionally retained for compatibility:

- The `com.ai.assistance.operit` Kotlin/Java namespace
- `.operit/market.json` installation markers
- Existing JS Bridge, tool package, and Skill protocol fields

These identifiers form a compatibility layer. Mira's source, Issues, Releases, and update entry points belong to this repository.

## Design Principles

- Content first, with less tool-dashboard clutter
- Only controls needed for the current interaction stay on the chat screen
- Shared adaptive rules for phones, foldables, tablets, and desktop windows
- Consistent status-bar, navigation-bar, keyboard, and overlay inset handling
- Restrained solid surfaces by default; complex visual effects remain optional

## Build

### Requirements

- Android Studio
- JDK 17
- Android SDK 36
- Android NDK and CMake
- Git submodules

### Clone

```bash
git clone --recursive https://github.com/kernelx30/Mira.git
cd Mira
```

For an existing clone without submodules:

```bash
git submodule update --init --recursive
```

### Local Configuration

Copy the required fields from `local.properties.example` into your local `local.properties`. API keys, signing files, and OAuth secrets must remain outside Git.

### Build a Debug APK

Windows:

```powershell
.\gradlew.bat :app:assembleDebug
```

Linux/macOS:

```bash
./gradlew :app:assembleDebug
```

Output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Repository Remotes

The recommended remote layout is:

```text
origin    https://github.com/kernelx30/Mira.git
upstream  https://github.com/AAswordman/Operit.git
```

Use `origin` for Mira development and releases. Use `upstream` only to track reusable Operit fixes.

## Data and Privacy

- Chat history and companion memory are stored locally by default
- Sensitive configuration belongs in app-private storage or local properties
- Model exports, logs, backups, and screenshots should be reviewed before publishing
- External model and voice providers apply their own data-processing terms

## Project Status

Mira is under active development. Conversation, voice, memory, floating companion, and large-screen behavior are still being refined, and database or interaction details may change.

Published builds are available on [Releases](https://github.com/kernelx30/Mira/releases). Report problems through [Issues](https://github.com/kernelx30/Mira/issues).

## License and Credits

Mira is based on Operit and retains its LGPLv3 license and original copyright notices. Thanks to the Operit project and its contributors for the agent, tool, plugin, and local-runtime foundations.

- Mira: [kernelx30/Mira](https://github.com/kernelx30/Mira)
- Upstream: [AAswordman/Operit](https://github.com/AAswordman/Operit)
- License: [LICENSE](LICENSE)
