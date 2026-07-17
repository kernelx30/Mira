---
fork: https://github.com/AAswordman/Operit
branch: companion-material
status: active
---

# Mira UI and companion runtime overhaul

## Current state

The first companion pass only changed branding, default chat preferences, colors, and settings order. The application shell, onboarding narrative, navigation density, settings geometry, and capability grid still read as the original Operit product.

## Intent

Turn the fork into a companion-first product while keeping Operit's agent runtime and every advanced capability intact. Mira remains local-first with no application backend: scheduled outreach is driven on-device and model requests go directly to the provider configured by the user. The UI should use a quiet Material 3 hierarchy inspired by KernelSU Style UI Kit and the chat density of RikkaHub.

## Target result

- A phone chat shell without persistent bottom navigation; conversations, memory, persona, settings, and advanced capabilities remain reachable from the header and conversation drawer
- Brand-owned rose, teal, and gold color roles instead of device dynamic blue
- Neutral top bars and responsive phone/tablet navigation
- Companion-first onboarding and permission explanation
- Grouped settings and capability surfaces that remain readable on large landscape screens
- Existing Skill, MCP, Shizuku, terminal, workflow, map, automation, and plugin routes remain reachable
- Build, install, launch, and screenshot validation on the connected Android device

## Work areas

- `ui/theme/`
- `ui/main/layout/` and `ui/main/components/`
- `ui/features/agreement/` and `ui/features/permission/`
- `ui/features/chat/`
- `ui/features/settings/`
- `ui/features/toolbox/`
- Android string resources and visible Operit branding
- `api/chat/` context projection and pre-compaction memory flush

## Design notes

- [09 Conversation drawer and delivery reliability](09-conversation-drawer-and-delivery-reliability.md)
- [08 Chat shell hardening](08-chat-shell-hardening.md)
- [07 Relationship continuity](07-relationship-continuity.md)
- [06 Product requirements document](06-product-requirements-document.md)
- [05 Companion product direction](05-companion-product-direction.md)
- [04 OpenSquilla memory and token budget](04-memory-and-token-budget.md)
