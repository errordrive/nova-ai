# Nova — AI Chat & Agent Workspace

Nova is an Android app that combines a **multi-provider AI chat client** with an
**on-device agent workspace**: file tools, a granular permission engine, an
audit log, and (later) a terminal/Termux bridge. One app, many models, full
control over what the agent is allowed to do.

## Features

### Phase 1 — Provider & chat foundation
- Add AI providers (OpenAI-compatible, Anthropic, Gemini, local Ollama/LiteLLM)
- API keys stored encrypted (Keystore-backed), never in plain text
- Streaming chat with markdown rendering, conversation history (Room)
- Light / dark / system theme

### Phase 2 — Agent orchestrator + file tools
- Tool-using agent loop with session history
- File read/write/list tools scoped to a workspace
- Tool-call cards with status and output preview

### Phase 3 — Permissions, workspace, diff, audit
- Granular permission rules per tool category (allow / ask / deny)
- Always-ask confirmation for dangerous operations
- SAF workspace sandboxing with path-traversal validation
- Before/after diff view for agent file edits, full audit log

### Phase 4 — Terminal / Termux
- Shell tool via Termux bridge with destructive-command guardrails

### Later
- Git integration, web fetch/search tools, SSH, MCP client support

## Building

Requirements:

- **Android Studio Ladybug** (2024.2.1) or newer
- **JDK 17** (bundled with recent Android Studio is fine)
- Android SDK with API 34 platform + build-tools

Steps:

1. Open `~/workspace/nova-ai` in Android Studio.
2. Let Gradle sync (first sync downloads AGP 8.6.1, Kotlin 2.0.21, and
   dependencies from Google / Maven Central).
3. Select the `app` run configuration and press **Run** (debug build installs
   directly; minSdk 26 = Android 8.0+).

### Release APK

1. `Build > Generate Signed Bundle / APK > APK`, create or choose a keystore.
2. Build the `release` variant — **R8 is enabled** (`isMinifyEnabled = true`
   with `proguard-android-optimize.txt` + `app/proguard-rules.pro`).
3. The signed APK is produced under `app/release/`.

Command line (after first successful Studio sync):

```bash
./gradlew assembleRelease   # needs gradle wrapper jar; or run from Studio
```

## Security notes

- **API keys:** stored in Keystore-backed `EncryptedSharedPreferences`
  (`androidx.security:security-crypto`); the database keeps only an opaque key
  reference, never the key itself. Keys are never logged or written to plain
  text.
- **Workspace sandboxing:** file tools operate inside a user-chosen SAF
  workspace root; every path is validated against traversal (`..`) before any
  read/write.
- **Permission model:** every agent tool call passes through the permission
  engine — allow / ask / deny rules per category, always-ask for destructive
  operations, and every decision is recorded in the audit log.
- **Local endpoints:** cleartext HTTP is permitted only so local Ollama /
  LiteLLM instances on the LAN work; remote providers should always use HTTPS.
- **No secrets in the repo:** no API keys, tokens, or keystores are committed.
  `local.properties` and keystores are git-ignored.

## Project layout

See [ARCHITECTURE.md](ARCHITECTURE.md) for the full package structure,
database schema, provider/tool interfaces, permission engine design,
navigation routes, and the phase plan.
