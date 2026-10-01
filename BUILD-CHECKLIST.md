# Nova — AI Chat & Agent Workspace: Build Checklist

## 1. Prerequisites

- **Android Studio** Ladybug (2024.2.1) or newer
- **JDK 17** (bundled with recent Android Studio; or set `org.gradle.java.home` in
  `~/.gradle/gradle.properties`)
- **Android SDK**: compileSdk 34, build-tools 34.x (installed via SDK Manager)
- Internet access on first sync (Gradle plugin + dependency download)

## 2. Open and sync

1. Android Studio → **Open** → select the `nova-ai` folder (the one containing
   `settings.gradle.kts`).
2. Let Gradle sync finish. The wrapper uses Gradle 8.9 (`gradle/wrapper/`).
3. If sync fails on a dependency version, check the version catalog at
   `gradle/libs.versions.toml` — pinned versions are listed in §5.

## 3. Run

- Select the `app` run configuration and press **Run** (or `./gradlew installDebug`
  from the project root, then launch "Nova" on the device/emulator).
- minSdk is 26 (Android 8.0); target/compile is 34.

## 4. Build a release APK

- Android Studio: **Build → Generate Signed Bundle / APK → APK**, create/use your
  keystore. R8 is enabled (`isMinifyEnabled = true`); rules live in
  `app/proguard-rules.pro` (Room, Hilt, kotlinx.serialization, OkHttp keep rules).
- CLI: `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk`
  (unsigned until you configure `signingConfigs`).

## 5. Pinned dependency versions (gradle/libs.versions.toml)

| Component | Version |
|---|---|
| Kotlin | 2.0.21 |
| AGP | 8.6.1 |
| KSP | 2.0.21-1.0.25 |
| Hilt | 2.51.1 |
| Room | 2.6.1 |
| Coroutines | 1.9.0 |
| kotlinx.serialization | 1.7.3 |
| OkHttp | 4.12.0 |
| DataStore Preferences | 1.1.1 |
| security-crypto | 1.1.0-alpha06 |
| Compose BOM | 2024.09.00 |
| activity-compose | 1.9.3 |
| navigation-compose | 2.7.7 |
| lifecycle | 2.8.6 |
| mikepenz markdown-m3 | 0.32.0 |

## 6. First-run smoke test (no code needed)

1. Open the app → **Providers** tab → **+** → add a provider:
   - *OpenAI-compatible*: name it, pick type (e.g. OpenRouter), paste API key,
     set model id (e.g. `openai/gpt-4o-mini`), **Test Connection**.
   - *Local*: type Ollama, base URL `http://10.0.2.2:11434/v1` on the emulator
     (or your machine's LAN IP on a physical device), no key needed.
2. **Chat** tab → send a message; streaming reply should appear.
3. **Agent** tab → pick "Coding Agent" → ask it to list the workspace files.
   Approve/deny the tool call in the dialog to exercise the permission engine.
4. **Settings → Audit log** → every tool attempt is recorded there.

## 7. Known TODOs / phase status

- **Phase 1 (provider/chat foundation)** — done: 9 providers, streaming, chat UI,
  markdown, attachments, model picker, encrypted credential storage.
- **Phase 2 (agent orchestrator + file tools)** — done: `AgentOrchestrator`,
  8 file tools, `ToolRegistry`, 5 default agent profiles.
- **Phase 3 (permissions/workspace/diff/audit)** — done: `PermissionEngine`
  (Deny/Ask/Allow-session/Always-allow + always-ask dangerous ops),
  `PathValidator` sandboxing, diff previews, audit log UI, SAF + internal
  workspaces. SAF workspaces are browsable but agent file tools operate on the
  internal workspace (external roots report a clear "not supported" instead of
  failing silently).
- **Phase 4 (terminal/Termux)** — **stub only**: `run_terminal` tool returns
  "not available yet"; design note in `agent/terminal/TERMUX_PLAN.md`.
- **Later (Git, web tools, SSH, MCP)** — `McpToolAdapter` + `MCP_DESIGN.md`
  sketch the integration; no network/Git tools are registered yet.

## 8. Things NOT yet verified (no Android SDK/JDK in the build VM)

- The project has **never been compiled** — Gradle sync/build must be run in
  Android Studio. The assembly pass statically verified: package names,
  imports, Room entities vs DAOs, Hilt modules vs `@Inject` constructors,
  nav routes, repository interfaces vs implementations, and provider/agent
  API contracts. Expect possible minor compile nits (e.g. an unused import or a
  Compose API nuance) on first build.
- Room schema, DataStore, and EncryptedSharedPreferences behavior are
  untested at runtime.
- Provider integrations are untested against live APIs (no keys in the repo,
  by design).

## 9. Security notes

- API keys live **only** in `EncryptedSharedPreferences` (MasterKey AES256_GCM,
  Android Keystore). They are never written to Room, logs, or the audit trail
  (audit stores tool *names/args*, never keys).
- `AndroidManifest.xml` sets `usesCleartextTraffic="true"` so local
  Ollama/LiteLLM over plain HTTP on the LAN works. For a Play Store release,
  prefer a `network_security_config.xml` that allows cleartext only to local
  hosts.
- Agent file tools resolve every path through `PathValidator` against granted
  workspace roots; `..` escapes, absolute paths, and symlink escapes are
  rejected with `SecurityException`.
