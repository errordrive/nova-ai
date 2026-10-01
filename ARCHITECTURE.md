# Nova — AI Chat & Agent Workspace: Architecture

## Overview

Nova is a single-activity Android app (minSdk 26) that combines a multi-provider
AI chat client with an on-device agent workspace: file tools, a permission
engine, an audit log, and (later) a terminal/Termux bridge. UI is 100% Jetpack
Compose (Material 3). DI is Hilt, persistence is Room + DataStore Preferences,
API keys are encrypted at rest via Jetpack Security Crypto.

## Package structure

```
com.nova.ai
├── NovaApplication.kt
├── MainActivity.kt
├── di/                     # Hilt modules
│   ├── DatabaseModule.kt   # Room database + DAOs
│   ├── NetworkModule.kt    # OkHttp clients (streaming + plain), logging
│   ├── CryptoModule.kt     # EncryptedSharedPreferences provider
│   └── ProviderModule.kt   # Provider registry bindings
├── data/
│   ├── local/              # Room: entities, DAOs, NovaDatabase
│   ├── datastore/          # SettingsStore, AppSettings (theme, etc.)
│   ├── security/           # KeyStore helpers, encrypted key storage
│   └── repository/         # Repository implementations over DAO + provider
├── domain/
│   └── repository/         # Repository interfaces (provider, chat, agent, files)
├── provider/               # AIProvider interface + implementations
│   ├── OpenAICompatProvider.kt   # OpenAI, DeepSeek, Groq, OpenRouter, etc.
│   ├── AnthropicProvider.kt
│   ├── GeminiProvider.kt
│   └── LocalOllamaProvider.kt    # plain-http LAN endpoint
├── agent/
│   ├── AgentOrchestrator.kt
│   ├── tools/              # AgentTool implementations (file read/write, shell...)
│   ├── permissions/        # PermissionEngine, rules, levels
│   ├── mcp/                # MCP client (later)
│   └── terminal/           # Termux bridge (later)
└── ui/
    ├── theme/              # NovaTheme, ThemeMode, colors, typography
    ├── navigation/         # MainScreen, NavGraph, routes
    ├── components/         # Shared: LoadingState, ErrorBanner, ConfirmDialog...
    ├── chat/               # ChatScreen, ConversationList, MessageBubble (markdown)
    ├── agent/              # AgentScreen, ToolCallCard, DiffView, session controls
    ├── files/              # Workspace browser (SAF)
    ├── providers/          # Provider list, ProviderEdit screen
    └── settings/           # Theme, permissions, audit log, agent profiles
```

## Database schema (Room)

| Table | Columns |
|---|---|
| `providers` | `id` TEXT PK, `name`, `type` (openai_compat/anthropic/gemini/ollama), `base_url`, `api_key_ref` (opaque ref into EncryptedSharedPreferences — never the key itself), `default_model`, `created_at`, `updated_at` |
| `models_cache` | `provider_id` FK, `model_id` composite PK, `display_name`, `cached_at` |
| `conversations` | `id` TEXT PK, `title`, `provider_id` FK, `model`, `created_at`, `updated_at` |
| `messages` | `id` TEXT PK, `conversation_id` FK (index), `role` (user/assistant/system/tool), `content`, `tool_calls_json`, `created_at` |
| `workspaces` | `id` TEXT PK, `name`, `tree_uri` (SAF persisted URI), `created_at` |
| `permission_rules` | `id` INTEGER PK, `category`, `pattern` (glob/regex), `level`, `created_at` |
| `agent_sessions` | `id` TEXT PK, `profile_id` FK, `workspace_id` FK nullable, `status`, `started_at`, `ended_at` |
| `tool_calls` | `id` TEXT PK, `session_id` FK (index), `tool_name`, `arguments_json`, `decision` (auto/allowed/denied/asked), `result_summary`, `created_at` |
| `audit_records` | `id` INTEGER PK, `timestamp`, `actor`, `action`, `detail_json` |
| `agent_profiles` | `id` TEXT PK, `name`, `system_prompt`, `default_provider_id` FK nullable, `default_model`, `max_steps`, `created_at`, `updated_at` |

Foreign keys with `onDelete = CASCADE` from conversations → messages and
agent_sessions → tool_calls.

## AIProvider interface

```kotlin
interface AIProvider {
    val providerId: String
    val displayName: String
    val providerType: ProviderType
    val supportsStreaming: Boolean
    val supportsTools: Boolean

    /** Fetch available model IDs (may hit network; results cached in models_cache). */
    suspend fun listModels(): List<ModelInfo>

    /** Non-streaming completion. */
    suspend fun chat(request: ChatRequest): ChatResponse

    /** Streaming completion via SSE; emits deltas until [DONE]. */
    fun chatStream(request: ChatRequest): Flow<ChatStreamEvent>

    /** Validate credentials/base URL; returns a human-readable error or null on success. */
    suspend fun validate(): String?
}
```

Supporting types: `ProviderType` enum, `ModelInfo(id, displayName)`,
`ChatRequest(messages, model, temperature, maxTokens, tools)`,
`ChatResponse(message, usage)`, `ChatStreamEvent` sealed (Delta / ToolCall / Done / Error),
`ChatMessage(role, content, toolCalls)`.

## AgentTool interface

```kotlin
interface AgentTool {
    /** Stable machine name, e.g. "file_read", "file_write", "shell_exec". */
    val name: String
    /** Human-readable description shown in permission prompts. */
    val description: String
    /** JSON schema of the arguments object. */
    val parametersSchema: String
    /** Permission category this tool belongs to (see permission engine). */
    val category: ToolCategory
    /** Executes the tool; throws ToolException on failure. */
    suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult
}
```

`ToolContext` carries the active workspace (SAF root), the session id, and a
cancellation hook. `ToolResult` carries `success`, `output` (truncated), and an
optional `diff` for file edits.

## Permission engine

### Categories

- `FILESYSTEM` — read/write/delete/list within a workspace
- `NETWORK` — outbound HTTP (fetch, provider calls outside chat)
- `SHELL` — local shell / Termux commands
- `SYSTEM` — clipboard, notifications, other device capabilities

### Levels

`ALLOW` — always permit · `ASK` — prompt the user every time ·
`DENY` — never permit. Rules are stored in `permission_rules` and matched by
(category, glob pattern on tool name / path / host).

### Decision flow

1. Tool call arrives → engine resolves (category, target).
2. Most specific matching rule wins; no rule → default per category
   (FILESYSTEM read: ALLOW inside workspace, ASK outside; write: ASK;
   SHELL: ASK; NETWORK: ASK for non-provider hosts).
3. **Always-ask dangerous ops** — `rm -rf`-style recursive deletes,
   writes outside the workspace root, shell commands matching a blocklist
   (e.g. `mkfs`, `dd`, fork bombs), and any SYSTEM action always surface a
   confirmation dialog, regardless of rules.
4. Every decision is written to `tool_calls` and summarized in `audit_records`.

## Navigation routes

| Route | Screen |
|---|---|
| `chat` | Conversation list → ChatScreen |
| `agent` | Agent sessions → AgentScreen |
| `files` | Workspace browser (SAF picker on first run) |
| `providers` | Provider list |
| `settings` | App settings (theme, permissions shortcut, audit log shortcut) |
| `provider_edit/{id}` | Add/edit provider (`id = "new"` for creation) |
| `audit_log` | Audit records list |
| `permissions` | Permission rules editor |
| `agent_profiles` | Agent profile list |
| `profile_edit/{id}` | Add/edit agent profile |

Bottom bar: Chat · Agent · Files · Providers · Settings. Detail routes are pushed
on the NavHost back stack.

## UI component inventory

- `NovaScaffold` — top app bar + bottom navigation wrapper
- `LoadingState` / `ErrorBanner` / `EmptyState` — shared state surfaces
- `ConfirmDialog` — permission / destructive-action confirmation
- `MarkdownText` — mikepenz multiplatform-markdown-renderer-m3 wrapper
- `MessageBubble` / `ChatInputBar` / `ModelPicker` — chat
- `ToolCallCard` — expandable tool call with status chip and output preview
- `DiffView` — before/after file diff for agent edits
- `ProviderCard` / `ProviderForm` — provider list and editor
- `PermissionRuleRow` — rule list item with level selector
- `AuditRecordRow` — audit log item

## Phase plan

- **Phase 1 — provider/chat foundation:** provider CRUD + encrypted keys,
  model listing, streaming chat UI, conversation persistence, markdown rendering.
- **Phase 2 — agent orchestrator + file tools:** AgentOrchestrator loop,
  `file_read`/`file_write`/`file_list` tools, ToolCallCard UI, session history.
- **Phase 3 — permissions/workspace/diff/audit:** permission engine + rules UI,
  SAF workspace sandboxing + path traversal validation, DiffView, audit log UI.
- **Phase 4 — terminal/Termux:** shell tool via Termux bridge, terminal view,
  always-ask guardrails for destructive commands.
- **Later:** Git integration, web fetch/search tools, SSH, MCP client support.
