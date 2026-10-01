# MCP Design (future)

How Model Context Protocol servers will plug into Nova's agent runtime without rewriting
`AgentOrchestrator`.

## Pieces

1. **Server registry** (`McpServerRegistry`, new): holds configured `McpServer` instances
   (name, transport, command/URL, env). Config stored in DataStore as JSON; edited from a
   future MCP settings screen.
2. **Tool adapter**: `McpToolAdapter(serverName, definition)` already implements `AgentTool`.
   - Name namespaced as `mcp_<server>_<tool>` to avoid collisions.
   - Permission category `NETWORK`, risk `HIGH` -> approval-gated by default.
3. **Registry hookup**: `ToolRegistry` gains `registerExternal(tool: AgentTool)` /
   `unregisterExternal(name)`; on server connect, each `listTools()` result becomes an
   `McpToolAdapter` registered under its namespaced name. `definitions()` then exposes them
   to the provider like any built-in tool.
4. **Execution**: a future `McpClient` implements the JSON-RPC transport (stdio/SSE),
   called from an `McpToolAdapter.execute()` override or a dedicated executor. Timeouts and
   cancellation mirror the agent loop's.

## Permission mapping

- Default: `NETWORK` / `HIGH` (always ask).
- Later: per-tool risk from server annotations, plus a per-server trust level setting that can
  downgrade to `ALLOW_SESSION`.

## Why the orchestrator does not change

The orchestrator only sees `AgentTool` via `ToolRegistry.get()` and `definitions*()`; MCP tools
flow through the same permission check, approval dialog, audit logging and TOOL-message
feedback as built-in tools.
